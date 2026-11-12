"""Tests of ci/check-log.py and of the manifests it reads: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import contextlib
import importlib.util
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

import plan

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("check_log", HERE / "check-log.py")
check_log = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check_log)

PLATFORMS = ("neoforge", "forge", "forge-legacy")
RUNS = ("run\tside\tpart\textended\tgates\tneoforge\tforge\tforge-legacy\tneeds\tmods\tenv\tdescription\twhy_not\n"
        "server\tserver\tserver\tno\tall\t*\t*\t\t\t\t\tA server\n"
        "server-fapi\tserver\tserver\tno\town\t*\t\t\t\tfabric-api\t\tWith Fabric API\n"
        "quiet\tserver\tcorpus\tyes\tnone\t*\t\t\t\tcorpus\t\tInformational\n"
        "when-run\tserver\tserver\tno\tall when <report#Provided>renderer\t*\t\t\t\t\t\tGates when provided\n"
        "client\tclient\tclient\tno\tall\t*\t\t\t\t\t\tA client\n")
HEADER = "id\tside\truns\tgate\tneoforge\tforge\tforge-legacy\tfile\tpattern\twhen\tsame\tdescription\twhy_not\n"


def manifest(*rows):
    return HEADER + "".join("\t".join(row) + "\n" for row in rows)


def row(id, pattern, runs="*", gate="yes", n="*", f="", file="out", when="", why="", side="server"):
    return (id, side, runs, gate, n, f, "", file, pattern, when, "", id, why)


class EvaluatorTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "logs").mkdir()
        (self.dir / "server.out").write_text(
            "FABRICATED-TEST main entrypoint OK\nFABRICATED-TEST loot SKIPPED: fabric-loot-api-v3 is another mod's\n"
            "Fabric block models of class a$BrokenModel (x) threw while drawn in a chunk\n"
            "Fabric block models of class a$BrokenModel (x) threw while drawn in a chunk\nversion 26.1.2 ready\n")
        (self.dir / "logs" / "fabric-bridge-report.txt").write_text(
            "== Summary ==\n  Disabled:            0\n\n== Loaded Fabric mods ==\n  sodium 0.6  [sodium.jar]\n"
            "      translated in 3 ms\n== Provided ==\n  renderer\n")
        (self.dir / "status.txt").write_text("started\n")
        manifests = Path(tempfile.mkdtemp())
        (manifests / "runs.tsv").write_text(RUNS)
        self.checks = manifests / "checks.tsv"
        self.saved = check_log.CHECKS, check_log.RUNS
        check_log.CHECKS, check_log.RUNS = self.checks, manifests / "runs.tsv"

    def tearDown(self):
        check_log.CHECKS, check_log.RUNS = self.saved

    def run_checks(self, *rows, run="server", version="26.1.2", **options):
        self.checks.write_text(manifest(*rows))
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            failed, entry = check_log.check("neoforge", version, run, self.dir, loader="26.1.2.116", **options)
        return failed, {c["id"]: c["result"] for c in entry["checks"]}, out.getvalue()

    def test_a_line_present_absent_or_counted(self):
        failed, results, _ = self.run_checks(
            row("present", "FABRICATED-TEST main entrypoint OK"), row("absent", "!FABRICATED-TEST .*FAILED"),
            row("twice", r"#2 Fabric block models of class [^ ]*\$BrokenModel \(.*\) threw"),
            row("once", r"#1 Fabric block models of class [^ ]*\$BrokenModel \(.*\) threw"),
            row("at-least", "#1+ BrokenModel"))
        self.assertEqual(results, {"present": "pass", "absent": "pass", "twice": "pass", "once": "fail", "at-least": "pass"})
        self.assertTrue(failed)

    def test_files_sections_and_conditions(self):
        _, results, _ = self.run_checks(
            row("status", "^started$", file="status"),
            row("section", r"^  sodium\s", file="report#Loaded Fabric mods"),
            row("not-in-section", r"^  renderer", file="report#Loaded Fabric mods"),
            row("disabled", "^  Disabled: +0$", file="report#Summary"),
            row("and", "<status>^started$ && main entrypoint OK"),
            row("and-fails", "main entrypoint OK && !<report>sodium"),
            row("or", "nothing like this || main entrypoint OK && version {minecraft} ready"),
            row("missing-file", "!anything", file="crash"))
        self.assertEqual(results, {"status": "pass", "section": "pass", "not-in-section": "fail", "disabled": "pass",
                                   "and": "pass", "and-fails": "fail", "or": "pass", "missing-file": "pass"})

    def test_a_check_another_mods_module_answers_is_skipped(self):
        _, results, out = self.run_checks(row("loot", "FABRICATED-TEST loot OK"), row("other", "FABRICATED-TEST other OK"))
        self.assertEqual(results, {"loot": "skip", "other": "fail"})
        self.assertIn("SKIP: loot (FABRICATED-TEST loot SKIPPED: fabric-loot-api-v3 is another mod's)", out)

    def test_a_check_whose_ok_line_says_more_takes_no_skipped_line(self):
        # A check logging more than "<check> OK" can't be skipped: its SKIPPED line fails it (vanilla-break, which only
        # its row for the run where another mod may keep the class takes as a skip).
        (self.dir / "server.out").write_text("FABRICATED-TEST vanilla block breaking SKIPPED: kept\n")
        _, results, _ = self.run_checks(row("more", r"FABRICATED-TEST vanilla block breaking OK \(with a block\)"),
                                        row("plain", "FABRICATED-TEST vanilla block breaking OK"))
        self.assertEqual(results, {"more": "fail", "plain": "skip"})

    def test_where_and_when_a_row_applies(self):
        _, results, out = self.run_checks(
            row("other-version", "nothing", n="1.21.1", why="neoforge: 1.21.1's alone"),
            row("forge-only", "nothing", n="", f="*"),
            row("unless-provided", "nothing", when="!<report>renderer", why="when: the renderer is another mod's"),
            row("client-side", "nothing", side="client"))
        self.assertEqual(results, {"other-version": "skip", "unless-provided": "skip"})
        self.assertIn("(1.21.1's alone)", out)
        self.assertIn("(the renderer is another mod's)", out)

    def test_runs_named_and_the_test_mods(self):
        rows = (row("every", "main entrypoint OK"), row("named", "nothing", runs="server-fapi"),
                row("not-fapi", "main entrypoint OK", runs="* !server-fapi"), row("with-extra", "main entrypoint OK", runs="extra"))
        self.assertEqual(self.run_checks(*rows)[1], {"every": "pass", "not-fapi": "pass"})
        self.assertEqual(self.run_checks(*rows, extra=True)[1], {"every": "pass", "not-fapi": "pass", "with-extra": "pass"})
        self.assertEqual(self.run_checks(*rows, run="server-fapi", extra=True)[1], {"every": "pass", "named": "fail", "with-extra": "pass"})
        self.assertEqual(self.run_checks(*rows, testmods=False)[1], {})

    def test_the_runs_gates(self):
        rows = (row("every", "nothing"), row("named", "nothing", runs="* server-fapi"), row("quiet", "nothing", gate="no"))
        self.assertTrue(self.run_checks(*rows)[0])
        failed, _, out = self.run_checks(*rows, run="server-fapi")
        self.assertTrue(failed)
        self.assertIn("FAIL: every (informational)", out)
        self.assertIn("FAIL: named (", out)
        self.assertFalse(self.run_checks(row("every", "nothing"), run="server-fapi")[0])
        self.assertFalse(self.run_checks(*rows, run="quiet")[0])
        self.assertFalse(self.run_checks(row("quiet", "nothing", gate="no"))[0])

    def test_gates_that_hold_only_when_a_condition_does(self):
        self.assertTrue(self.run_checks(row("every", "nothing"), run="when-run")[0])
        (self.dir / "logs" / "fabric-bridge-report.txt").write_text("== Provided ==\n  something else\n")
        failed, _, out = self.run_checks(row("every", "nothing"), run="when-run")
        self.assertFalse(failed)
        self.assertIn("is informational here", out)

    def test_the_client_reads_its_game_directory(self):
        (self.dir / "run" / "logs").mkdir(parents=True)
        (self.dir / "run" / "logs" / "latest.log").write_text("Successfully finished.\n")
        (self.dir / "client.out").write_text("console only\n")
        _, results, _ = self.run_checks(row("joined", r"Successfully finished\.", file="log", side="client"),
                                        row("console", "console only", file="log,out", side="client"), run="client")
        self.assertEqual(results, {"joined": "pass", "console": "pass"})

    def test_the_summary_and_its_report(self):
        summary = self.dir / "checks.json"
        self.checks.write_text(manifest(row("every", "nothing")))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(check_log.main(["neoforge", "26.1.2", "server", str(self.dir), "--summary", str(summary)]), 1)
            check_log.main(["neoforge", "26.1.2", "quiet", "--skip", "nothing to run", "--summary", str(summary)])
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(check_log.report(summary), 1)
        entries = json.loads(summary.read_text())
        self.assertEqual([(e["run"], e["result"]) for e in entries], [("server", "fail"), ("quiet", "skipped")])
        self.assertIn("  FAIL: every", out.getvalue())
        self.assertIn("::error title=neoforge 26.1.2 server::every", out.getvalue())

    def test_an_informational_runs_failed_checks_are_counted(self):
        summary = self.dir / "checks.json"
        summary.write_text(json.dumps([{"platform": "forge", "minecraft": "26.3", "run": "client-corpus", "gates": "none", "result": "pass",
                                        "checks": [{"id": "joined", "result": "fail", "gate": False, "description": "client joined"},
                                                   {"id": "ran", "result": "pass", "gate": False, "description": "ran"}]}]))
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(check_log.report(summary), 0)
        self.assertIn("forge 26.3 client-corpus: pass (informational, 1 check(s) failed)\n", out.getvalue())
        self.assertIn("  FAIL (informational): client joined\n", out.getvalue())
        self.assertNotIn("::error", out.getvalue())


class ManifestTest(unittest.TestCase):
    """The repository's manifests: every row well formed, so a mistake fails here rather than in a game run."""
    runs = plan.read_table(HERE / "runs.tsv")
    checks = plan.read_table(HERE / "checks.tsv")

    def cells(self, rows):
        for r in rows:
            for platform in PLATFORMS:
                words = r[platform].split()
                self.assertTrue(all(w == "*" or re.fullmatch(r"!?\d+(\.\d+)+", w) for w in words), (r, platform))
                if words and words != ["*"]:
                    self.assertTrue(re.search(rf"(^|; ){platform}( [\d.]+)?: ", r["why_not"]),
                                    f"{r.get('id') or r['run']}: no reason for where it doesn't apply on {platform}")

    def test_runs(self):
        self.cells(self.runs)
        for r in self.runs:
            self.assertIn(r["side"], ("server", "client"), r)
            self.assertIn(r["part"], ("server", "client", "mods", "corpus"), r)
            self.assertIn(r["extended"], ("yes", "no"), r)
            gates, _, condition = r["gates"].partition(" when ")
            self.assertIn(gates, ("all", "own", "none"), r)
            for token in r["mods"].split():
                self.assertRegex(token, r"^(-testmods|fabric-api|testmod:\w+|modrinth:(fabric|neoforge|forge):[\w@.,{}-]+|corpus(:advanced)?"
                                        r"|drop:\S+|standins(:own-loader)?|connector|fabricated-copy|energy|unreadable-pack|seed:\S+|reuse:\S+)$", r["run"])
        for platform in PLATFORMS:
            for version in ("1.20.1", "1.21.1", "1.21.11", "26.1.2", "26.2", "26.3"):
                names = [r["run"] for r in self.runs if plan.applies(r, platform, version)]
                self.assertEqual(len(names), len(set(names)), f"a run twice on {platform} {version}")

    def test_checks(self):
        self.cells(self.checks)
        ids = [r["id"] for r in self.checks]
        self.assertEqual(len(ids), len(set(ids)))
        files = {"out", "log", "report", "debug", "crash", "status", "failures", "warm", "warm-report", "mods", "linkage"}
        same = {}
        for r in self.checks:
            self.assertIn(r["side"], ("server", "client"), r["id"])
            self.assertIn(r["gate"], ("yes", "no"), r["id"])
            for expression in (r["pattern"], r["when"]):
                for alternative in filter(None, expression.split(" || ")):
                    for condition in alternative.split(" && "):
                        m = check_log.CONDITION.match(condition)
                        for name in (m.group("files") or r["file"]).split(","):
                            self.assertIn(name.partition("#")[0], files, r["id"])
                        re.compile(check_log.fill(m.group("regex"), {"minecraft": "1.21.1", "platform": "neoforge", "loader": "1"}))
            if r["when"]:
                self.assertIn("when: ", r["why_not"], r["id"])
            for token in r["runs"].split():
                name = token.lstrip("!")
                if name in ("*", "extra"):
                    continue
                matching = [run for run in self.runs if run["run"] == name and run["side"] == r["side"]
                            and any(run[p].strip() and r[p].strip() for p in PLATFORMS)]
                self.assertTrue(matching, f"{r['id']}: no {r['side']} run {name} on its platforms")
            if r["same"]:
                same.setdefault(r["same"], []).append(r["id"])
        self.assertEqual([k for k, v in same.items() if len(v) < 2], [])


if __name__ == "__main__":
    unittest.main()
