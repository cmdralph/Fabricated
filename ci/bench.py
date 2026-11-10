#!/usr/bin/env python3
"""The startup and memory benchmark: reads a server run's measurements (ci/smoke-server.sh with SMOKE_BENCH and
SMOKE_WARM_LAUNCH), compares them with the version's baselines (ci/bench.tsv; its header says how a row reads), prints a
table, also to $GITHUB_STEP_SUMMARY, and exits 1 when a metric is more than 10% over its baseline, when a recorded one
wasn't measured, or when a baseline changed without a new reason.

Usage: bench.py <platform> <minecraft> <run-dir> [--base <bench.tsv>]
--base: the baselines the change started from (a pull request's base branch's), which a changed baseline must give a
new reason against.
"""
import os
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plan  # noqa: E402

BASELINES = Path(__file__).resolve().parent / "bench.tsv"
LIMIT = 0.10
DISCOVERY = re.compile(r"Fabricated discovery finished in (\d+) ms, (\d+) MB allocated")
HEAP = re.compile(r"^Total\s+\d+\s+(\d+)\s*$")


def first(path, pattern):
    """The first line of a file that matches, as a match, or None."""
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return None
    return next((m for m in map(pattern.search, lines) if m), None)


def measure(run):
    """What the run measured, by metric: the allocations and the heap in MB, the wall times (ms), which are only reported."""
    run, found = Path(run), {}
    log = run / "logs" / "latest.log"
    for console, suffix in ((log if log.is_file() else run / "server.out", ""), (run / "server-warm.out", "-cached")):
        m = first(console, DISCOVERY)
        if m:
            found["discovery-ms" + suffix], found["discovery-alloc" + suffix] = int(m.group(1)), int(m.group(2))
    m = first(run / "heap-histogram.txt", HEAP)
    if m:
        found["heap"] = int(m.group(1)) // 1_048_576
    return found


def rows(path, platform, version):
    """The version's baselines, by metric: (value or None where not recorded yet, why)."""
    found = {}
    for row in plan.read_table(path):
        if row["platform"] == platform and row["minecraft"] == version:
            value = None if row["baseline"] == "-" else float(row["baseline"])
            if value is not None and not row["why"].strip():
                raise SystemExit(f"{path}: {platform} {version} {row['metric']}: a baseline without its reason")
            found[row["metric"]] = (value, row["why"])
    return found


def compare(baselines, measured, base=None):
    """Each metric's line (metric, baseline, measured, change, result) and whether one fails."""
    lines, failed = [], False
    for metric in [*baselines, *(m for m in measured if m not in baselines)]:
        value, why = baselines.get(metric, (None, ""))
        got = measured.get(metric)
        change = f"{(got - value) / value:+.1%}" if got is not None and value else ""
        if metric not in baselines:
            result = "reported only"
        elif base and metric in base and base[metric][0] != value and base[metric][1] == why:
            result, failed = "FAIL: the baseline changed without a new reason in ci/bench.tsv", True
        elif value is None:
            result = "not recorded yet: reported only"
        elif got is None:
            result, failed = "FAIL: not measured", True
        elif got > value * (1 + LIMIT):
            result, failed = f"FAIL: more than {LIMIT:.0%} over the baseline; if expected, record the new value in ci/bench.tsv with why", True
        elif got < value * (1 - LIMIT):
            result = f"pass, more than {LIMIT:.0%} under: record the new value in ci/bench.tsv"
        else:
            result = "pass"
        unit = " ms" if "-ms" in metric else " MB"
        lines.append((metric, "-" if value is None else f"{value:g}{unit}", "-" if got is None else f"{got:g}{unit}", change, result))
    return lines, failed


def main(args):
    base = None
    if "--base" in args:
        i = args.index("--base")
        base = args[i + 1]
        del args[i:i + 2]
    if len(args) != 3:
        raise SystemExit(__doc__)
    platform, version, run = args
    lines, failed = compare(rows(BASELINES, platform, version), measure(run),
                            rows(base, platform, version) if base else None)
    table = [f"Benchmark, {platform} {version} ({Path(run).name}):", "", "| Metric | Baseline | Measured | Change | Result |",
             "|---|---|---|---|---|", *(f"| {' | '.join(line)} |" for line in lines)]
    print("\n".join(table))
    for metric, _, _, _, result in lines:
        if result.startswith("FAIL"):
            print(f"::error title=Benchmark {platform} {version} {metric}::{result}")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
            out.write("\n".join(table) + "\n")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
