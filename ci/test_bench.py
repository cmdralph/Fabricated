"""Tests of ci/bench.py and its baselines: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import contextlib
import importlib.util
import io
import os
import tempfile
import unittest
from pathlib import Path

import plan

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("bench", HERE / "bench.py")
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)

HEADER = "platform\tminecraft\tmetric\tbaseline\twhy\n"
# Every platform's versions, which ci/bench.tsv must hold a row of each gated metric for.
MATRIX = {"neoforge": ("1.21.1", "1.21.11", "26.1.2", "26.2", "26.3"), "forge": ("1.21.11", "26.1.2", "26.2", "26.3"),
          "forge-legacy": ("1.20.1", "1.21.1")}
GATED = ("discovery-alloc", "discovery-alloc-cached", "heap")


def baselines(*rows):
    path = Path(tempfile.mkdtemp()) / "bench.tsv"
    path.write_text(HEADER + "".join("\t".join(row) + "\n" for row in rows))
    return path


def server_run(cold=True, warm=True, heap=True):
    """A server run's folder, as ci/smoke-server.sh leaves it."""
    run = Path(tempfile.mkdtemp())
    (run / "logs").mkdir()
    line = "[main/INFO] [Fabricated/]: Fabricated discovery finished in {} ({})\n"
    (run / "logs" / "latest.log").write_text(line.format("2879 ms, 899 MB allocated", "in-game mod 66 ms") if cold else "")
    (run / "server.out").write_text("Done (1.330s)!\n")
    if warm:
        (run / "server-warm.out").write_text(line.format("627 ms, 96 MB allocated", "in-game mod 24 ms"))
    if heap:
        (run / "heap-histogram.txt").write_text(" num     #instances         #bytes  class name (module)\n"
                                                "   1:        933529       63425992  [B (java.base@21)\nTotal       6177634      241920520\n")
    return run


class MeasureTest(unittest.TestCase):
    def test_both_launches_and_the_heap(self):
        self.assertEqual(bench.measure(server_run()), {"discovery-ms": 2879, "discovery-alloc": 899, "discovery-ms-cached": 627,
                                                      "discovery-alloc-cached": 96, "heap": 230})

    def test_what_a_run_did_not_measure_is_missing(self):
        self.assertEqual(bench.measure(server_run(cold=False, warm=False, heap=False)), {})


class CompareTest(unittest.TestCase):
    BASELINES = {"discovery-alloc": (900.0, "measured"), "heap": (230.0, "measured"), "discovery-alloc-cached": (None, "not recorded yet")}

    def results(self, measured, base=None, baselines=None):
        lines, failed = bench.compare(baselines or self.BASELINES, measured, base)
        return {line[0]: line[4].split(":")[0].split(",")[0] for line in lines}, failed

    def test_up_to_10_percent_over_passes(self):
        results, failed = self.results({"discovery-alloc": 990, "heap": 207, "discovery-alloc-cached": 96, "discovery-ms": 2879})
        self.assertEqual(results, {"discovery-alloc": "pass", "heap": "pass", "discovery-alloc-cached": "not recorded yet",
                                   "discovery-ms": "reported only"})
        self.assertFalse(failed)

    def test_more_than_10_percent_over_or_not_measured_fails(self):
        results, failed = self.results({"discovery-alloc": 991})
        self.assertEqual(results["discovery-alloc"], "FAIL")
        self.assertEqual(results["heap"], "FAIL")
        self.assertTrue(failed)
        self.assertFalse(self.results({"discovery-alloc": 800, "heap": 230})[1])

    def test_a_changed_baseline_needs_a_new_reason(self):
        base = {"discovery-alloc": (800.0, "measured"), "heap": (230.0, "measured")}
        results, failed = self.results({"discovery-alloc": 899, "heap": 230}, base)
        self.assertEqual(results["discovery-alloc"], "FAIL")
        self.assertTrue(failed)
        moved = dict(self.BASELINES, **{"discovery-alloc": (900.0, "PERF-9: the mapping table read twice")})
        self.assertFalse(self.results({"discovery-alloc": 899, "heap": 230}, base, moved)[1])


class MainTest(unittest.TestCase):
    def test_the_table_and_the_exit_status(self):
        saved = bench.BASELINES
        summary = Path(tempfile.mkdtemp()) / "summary.md"
        os.environ["GITHUB_STEP_SUMMARY"] = str(summary)
        try:
            for heap, status in (("230", 0), ("200", 1)):
                bench.BASELINES = baselines(("neoforge", "1.21.1", "heap", heap, "measured"),
                                            ("neoforge", "1.21.1", "discovery-alloc", "-", "not recorded yet"))
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(bench.main(["neoforge", "1.21.1", str(server_run())]), status)
        finally:
            bench.BASELINES = saved
            del os.environ["GITHUB_STEP_SUMMARY"]
        text = summary.read_text()
        self.assertIn("| heap | 230 MB | 230 MB | +0.0% | pass |", text)
        self.assertIn("| heap | 200 MB | 230 MB | +15.0% | FAIL", text)
        self.assertIn("| discovery-ms-cached | - | 627 ms |  | reported only |", text)

    def test_a_baseline_needs_its_reason(self):
        with self.assertRaises(SystemExit):
            bench.rows(baselines(("neoforge", "1.21.1", "heap", "230", "")), "neoforge", "1.21.1")


class BaselinesTest(unittest.TestCase):
    def test_every_version_has_each_metric_once(self):
        rows = plan.read_table(bench.BASELINES)
        keys = [(r["platform"], r["minecraft"], r["metric"]) for r in rows]
        self.assertEqual(len(keys), len(set(keys)))
        self.assertEqual(set(keys), {(p, v, m) for p, versions in MATRIX.items() for v in versions for m in GATED})
        for r in rows:
            self.assertRegex(r["baseline"], r"^(-|\d+)$", r)
            self.assertTrue(r["why"].strip(), r)
            bench.rows(bench.BASELINES, r["platform"], r["minecraft"])


if __name__ == "__main__":
    unittest.main()
