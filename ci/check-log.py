#!/usr/bin/env python3
"""Checks one game run against the manifest (ci/checks.tsv; its header says how a row reads): prints PASS, FAIL or SKIP
for each check that applies to the run's platform, appends the run's results to a JSON summary, and exits 1 when a
check failed that gates the run (its gates in ci/runs.tsv: all, own or none).

Usage: check-log.py <platform> <minecraft> <run> <work-dir> [--loader <version>] [--extra] [--no-testmods]
                    [--summary <file>]
       check-log.py <platform> <minecraft> <run> (--skip | --error) <reason> [--summary <file>]
       check-log.py --entries <summary> <run>     how many entries the summary has for the run
       check-log.py --report <summary>            each run's result, and the gating checks that failed, also to
                                                  $GITHUB_STEP_SUMMARY
<work-dir> is the harness's (ci/smoke-server.sh, ci/smoke-client.sh); --extra says other mods joined the test mods,
--no-testmods that the test mods were left out.
"""
import glob
import json
import os
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plan  # noqa: E402

HERE = Path(__file__).resolve().parent
CHECKS, RUNS = HERE / "checks.tsv", HERE / "runs.tsv"
CONDITION = re.compile(r"^(?P<negate>!?)(?:#(?P<count>\d+)(?P<more>\+?) )?(?:<(?P<files>[^>]+)>)?(?P<regex>.*)$")
SECTION_END = re.compile(r"^(== |$)")


class Run:
    """A run's files, read once each."""

    def __init__(self, side, work):
        self.work = Path(work)
        self.game = self.work / "run" if side == "client" else self.work
        self.console = self.work / ("client.out" if side == "client" else "server.out")
        self.cache = {}

    def paths(self, name):
        game = self.game
        log = game / "logs" / "latest.log"
        return {"out": [self.console], "log": [log if log.is_file() else self.console],
                "report": [game / "logs" / "fabric-bridge-report.txt"], "debug": [game / "logs" / "debug.log"],
                "crash": [Path(p) for p in sorted(glob.glob(str(game / "crash-reports" / "*.txt")))],
                "status": [self.work / "status.txt"], "failures": [game / ".fabricated" / "mixin-failures.json"],
                "warm": [self.work / "server-warm.out"], "warm-report": [self.work / "fabric-bridge-report-warm.txt"],
                "mods": [self.work / "mods.txt"],
                "linkage": [self.work / "standin-linkage.txt"]}[name]

    def lines(self, spec):
        """The lines of the files a spec names ("log,out", "report#Summary")."""
        if spec not in self.cache:
            found = []
            for name in spec.split(","):
                name, _, section = name.strip().partition("#")
                for path in self.paths(name):
                    try:
                        text = path.read_text(encoding="utf-8", errors="replace").splitlines()
                    except OSError:
                        continue
                    found.extend(lines_of_section(text, section) if section else text)
            self.cache[spec] = found
        return self.cache[spec]


def lines_of_section(lines, title):
    """The lines of a report section, from its "== <title>" header to the next header or blank line."""
    result, inside = [], False
    for line in lines:
        if inside and SECTION_END.match(line):
            break
        if line.startswith(f"== {title}"):
            inside = True
        elif inside:
            result.append(line)
    return result


def holds(condition, run, default_files, values):
    """Whether one condition holds, and what was seen, for the report of a failure."""
    m = CONDITION.match(condition)
    files = m.group("files") or default_files
    regex = re.compile(fill(m.group("regex"), values))
    matched = [line for line in run.lines(files) if regex.search(line)]
    if m.group("count"):
        wanted = int(m.group("count"))
        ok = len(matched) >= wanted if m.group("more") else len(matched) == wanted
        seen = f"{len(matched)} line(s) of '{regex.pattern}' in {files}, not {wanted}{m.group('more')}"
    else:
        ok = bool(matched)
        seen = f"missing '{regex.pattern}' in {files}"
    if m.group("negate"):
        return not ok, f"{files} has '{matched[0][:300]}'" if matched else f"'{regex.pattern}' in {files}"
    return ok, seen


def evaluate(expression, run, default_files, values):
    """Conditions joined by && and ||, && binding first: whether it holds, and the first failure seen."""
    seen = ""
    for alternative in expression.split(" || "):
        ok = True
        for condition in alternative.split(" && "):
            held, why = holds(condition, run, default_files, values)
            if not held:
                ok, seen = False, seen or why
                break
        if ok:
            return True, ""
    return False, seen


def fill(text, values):
    return re.sub(r"\{(minecraft|platform|loader)\}", lambda m: re.escape(values[m.group(1)]), text)


def run_row(platform, version, name):
    rows = [r for r in plan.read_table(RUNS) if r["run"] == name and plan.applies(r, platform, version)]
    if not rows:
        raise SystemExit(f"ci/runs.tsv has no run {name} for {platform} {version}")
    return rows[0]


def selects(row, name, testmods, extra):
    """Whether a check row is made in the run, and whether it names the run (which the run's gates "own" ask)."""
    tokens = row["runs"].split()
    if f"!{name}" in tokens:
        return False, False
    named = name in tokens
    return named or ("*" in tokens and testmods) or ("extra" in tokens and testmods and extra), named


def check(platform, version, name, work, loader="", extra=False, testmods=True):
    """The run's results: (whether a gating check failed, the summary entry)."""
    spec = run_row(platform, version, name)
    run = Run(spec["side"], work)
    values = {"minecraft": version, "platform": platform, "loader": loader}
    gates, _, gate_when = spec["gates"].partition(" when ")
    if gate_when and not evaluate(gate_when, run, "log", values)[0]:
        print(f"NOTE: {name} is informational here: {gate_when} doesn't hold")
        gates = "none"
    results, failed = [], False
    for row in plan.read_table(CHECKS):
        if row["side"] != spec["side"] or not row[platform].strip():
            continue
        selected, named = selects(row, name, testmods, extra)
        if not selected:
            continue
        gating = row["gate"] == "yes" and (gates == "all" or (gates == "own" and named))
        if not plan.applies(row, platform, version):
            result, detail = "SKIP", plan.why_not(row, platform, version)
        elif row["when"] and not evaluate(row["when"], run, row["file"], values)[0]:
            result, detail = "SKIP", plan.why_not(row, "when")
        else:
            ok, detail = evaluate(row["pattern"], run, row["file"], values)
            result = "PASS" if ok else "FAIL"
            single = CONDITION.match(row["pattern"])
            if not ok and row["pattern"].endswith(" OK") and not single.group("negate") and not single.group("count") \
                    and not re.search(r" (&&|\|\|) ", row["pattern"]):
                # Another mod's module answers the check: the test mod logs "<check> SKIPPED: <why>" in its place.
                skipped = re.compile(fill(single.group("regex")[:-3], values) + " SKIPPED.*")
                lines = run.lines(single.group("files") or row["file"])
                found = next((m.group(0) for line in lines for m in [skipped.search(line)] if m), None)
                if found:
                    result, detail = "SKIP", found[:300]
        failed |= result == "FAIL" and gating
        label = "" if gating or result != "FAIL" else " (informational)"
        print(f"{result}: {row['description']}{label}" + (f" ({detail})" if result != "PASS" else ""))
        results.append({"id": row["id"], "result": result.lower(), "gate": gating, "description": row["description"],
                        "detail": detail if result != "PASS" else ""})
    if gates == "none":
        print(f"NOTE: {name} is informational: its failures gate nothing")
    return failed, {"platform": platform, "minecraft": version, "run": name, "gates": gates,
                    "result": "fail" if failed else "pass", "checks": results}


def append(summary, entry):
    """Adds a run's entry to the summary file, a JSON list of every run of the job."""
    if not summary:
        return
    path = Path(summary)
    entries = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []
    entries.append(entry)
    path.write_text(json.dumps(entries, indent=1) + "\n", encoding="utf-8")


def report(summary):
    """Prints each run's result and the gating checks that failed; 1 when a run that gates failed."""
    path = Path(summary)
    entries = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []
    lines, failed = ["| Run | Result | Gating checks that failed |", "|---|---|---|"], False
    for entry in entries:
        name = f"{entry['platform']} {entry['minecraft']} {entry['run']}"
        broken = [c["description"] for c in entry.get("checks", []) if c["result"] == "fail" and c["gate"]]
        # An informational run passes whatever its checks say, so the result says how many failed (CI-11).
        missed = [c["description"] for c in entry.get("checks", []) if c["result"] == "fail" and not c["gate"]]
        result = entry["result"] + (f" (informational, {len(missed)} check(s) failed)" if entry.get("gates") == "none" and missed
                                    else " (informational)" if entry.get("gates") == "none" else "")
        print(f"{name}: {result}" + (f" ({entry['detail']})" if entry.get("detail") else ""))
        for description in broken:
            print(f"  FAIL: {description}")
        for description in missed:
            print(f"  FAIL (informational): {description}")
        if entry["result"] in ("fail", "error") and entry.get("gates") != "none":
            failed = True
            print(f"::error title={name}::" + ("; ".join(broken) or entry.get("detail", ""))[:900])
        lines.append(f"| {name} | {result} | {'; '.join(broken) or entry.get('detail', '')} |")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n")
    return 1 if failed else 0


def main(args):
    def option(name, has_value=True):
        if name not in args:
            return None if has_value else False
        i = args.index(name)
        value = args[i + 1] if has_value else True
        del args[i:i + 1 + has_value]
        return value

    if args[:1] == ["--entries"] and len(args) == 3:
        path = Path(args[1])
        print(sum(e["run"] == args[2] for e in json.loads(path.read_text(encoding="utf-8"))) if path.is_file() else 0)
        return 0
    if args[:1] == ["--report"] and len(args) == 2:
        return report(args[1])
    summary, loader = option("--summary"), option("--loader") or ""
    skip, error = option("--skip"), option("--error")
    extra, testmods = option("--extra", False), not option("--no-testmods", False)
    if (skip is not None or error is not None) and len(args) == 3:
        gates = run_row(*args)["gates"].partition(" when ")[0]
        print(f"{'SKIP' if error is None else 'FAIL'}: run {args[2]}: {skip if error is None else error}")
        append(summary, {"platform": args[0], "minecraft": args[1], "run": args[2], "gates": gates,
                         "result": "skipped" if error is None else "error", "detail": skip if error is None else error})
        return 0
    if len(args) != 4:
        raise SystemExit(__doc__)
    failed, entry = check(*args, loader=loader, extra=extra, testmods=testmods)
    append(summary, entry)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
