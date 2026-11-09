#!/usr/bin/env python3
"""What CI runs (.github/workflows/ci.yml), and where the runs and checks of the manifests apply (ci/runs.tsv,
ci/checks.tsv).

A branch holds one platform's version folders, mc/<platform>/<version> with a gradle.properties (main NeoForge's, forge
and forge-legacy theirs). A path belongs to a platform when it is in mc/<platform>/, loader/<platform>/ or ci/<platform>/,
or is mc/platform-<platform>.gradle; mc/forge-shared, loader/forge-shared, ci/forge-shared, mc/forge-mixinextras.gradle
and mc/platform-forge.gradle (ForgeGradle's build, which forge-legacy's 1.21.1 applies too) belong to both Forge
platforms. Documentation, release.yml and the CurseForge upload touch no version. Anything else is shared and touches
every version the tree holds.

The versions a change touches: per touched platform, the versions whose own folders changed when nothing else of the
platform did, else every version. The fast job boots one: the version whose folder alone changed, else the newest.

A manifest row says where it applies in one column per platform: empty (not on that platform), * (every version),
versions, and !<version> to leave one out of *. Its why_not column gives the reasons, "; "-separated, each
"<platform>[ <version>]: <reason>", or "when: <reason>" for a row whose precondition doesn't hold.

Usage:
  plan.py ci                                  the workflow's plan from its environment (EVENT, REF, BEFORE,
                                              EXTENDED, COVERED: an open pull request runs the full matrix, CALLED_REF:
                                              called by another workflow), to $GITHUB_OUTPUT
  plan.py target <platform> <minecraft>       a version's pins, as shell assignments
  plan.py runs <platform> <minecraft> <part> [--extended] [<run>...]
                                              the part's runs (all, or the named ones), one per line: run, side,
                                              gates, needs, mods, env and why it is skipped without what it needs,
                                              separated by the unit separator (0x1f: a shell's read keeps empty
                                              fields), the version's pins filled in, comma-separated
"""
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

PLATFORMS = ("neoforge", "forge", "forge-legacy")
BASE = {"neoforge": "main", "forge": "forge", "forge-legacy": "forge-legacy"}
BASE_REFS = tuple(f"refs/heads/{branch}" for branch in BASE.values())
LOADER_PROPERTY = {"neoforge": "neoforge_version", "forge": "forge_version", "forge-legacy": "forge_version"}
FORGE_SHARED = ("mc/forge-shared/", "loader/forge-shared/", "ci/forge-shared/", "mc/forge-mixinextras.gradle", "mc/platform-forge.gradle")
NO_TARGET = re.compile(r"^(docs/|.*\.md$|\.github/workflows/release\.yml$|ci/curseforge)")
FABRIC_API_PLAN = "src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json"
# Values a plan hands to a shell or a cache key: a branch's files decide them, so they are checked.
SAFE = {"minecraft": r"\d+(\.\d+)+", "loader": r"[0-9A-Za-z.+-]+", "java": r"\d+", "fabric_api": r"[0-9A-Za-z.+-]*",
        "plugin_hosts": r"[0-9A-Za-z@ ._-]*", "renderers": r"[0-9A-Za-z@ ._-]*", "fabric_renderers": r"[0-9A-Za-z@ ._-]*"}
HERE = Path(__file__).resolve().parent


def properties(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def versions(root, platform):
    """The platform's Minecraft versions in the tree, oldest first."""
    found = [p.parent.name for p in (root / "mc" / platform).glob("*/gradle.properties")]
    for version in found:
        if not re.fullmatch(SAFE["minecraft"], version):
            raise SystemExit(f"mc/{platform}/{version}: not a Minecraft version")
    return sorted(found, key=lambda v: tuple(int(n) for n in v.split(".")))


def held(root):
    return [platform for platform in PLATFORMS if versions(root, platform)]


def platforms_of(path):
    """The platforms a path belongs to alone, () for a path that touches no version, None for shared code."""
    if NO_TARGET.match(path):
        return ()
    if path.startswith(FORGE_SHARED):
        return ("forge", "forge-legacy")
    for platform in PLATFORMS:
        if path.startswith((f"mc/{platform}/", f"loader/{platform}/", f"ci/{platform}/")) or path == f"mc/platform-{platform}.gradle":
            return (platform,)
    return None


def touched(root, paths):
    """platform -> the version folders changed under mc/<platform>/ (None: some other file of the platform)."""
    holds = held(root)
    result = {}
    for path in paths:
        owners = platforms_of(path)
        parts = path.split("/")
        for platform in holds if owners is None else owners:
            if platform not in holds:
                continue
            own = len(parts) > 3 and parts[0] == "mc" and parts[1] == platform and parts[2] in versions(root, platform)
            result.setdefault(platform, set()).add(parts[2] if own else None)
    return result


def target(root, platform, version):
    folder = root / "mc" / platform / version
    props = properties(folder / "gradle.properties")
    plan = folder / FABRIC_API_PLAN
    fabric_api = json.loads(plan.read_text(encoding="utf-8")).get("fabricApiVersion", "") if plan.is_file() else ""
    result = {"platform": platform, "minecraft": version, "loader": props[LOADER_PROPERTY[platform]],
              "java": props["java_version"], "fabric_api": fabric_api, "plugin_hosts": props.get("ci_plugin_hosts", ""),
              "renderers": props.get("ci_renderers", "sodium iris"),
              "fabric_renderers": props.get("ci_fabric_renderers", "sodium iris")}
    for key, pattern in SAFE.items():
        if not re.fullmatch(pattern, result[key]):
            raise SystemExit(f"mc/{platform}/{version}: {key} {result[key]!r} is not {pattern}")
    # What the downloads cache holds for the version: the pinned builds, so a new pin makes a new entry.
    pins = "|".join(result[k] for k in ("fabric_api", "plugin_hosts", "renderers", "fabric_renderers"))
    result["pins"] = hashlib.sha256(pins.encode()).hexdigest()[:16]
    return result


def targets(root, paths, quick=False):
    """The versions the paths touch (every version for paths=None), or with quick the one the fast job boots."""
    changed = {p: {None} for p in held(root)} if paths is None else touched(root, paths)
    result = []
    for platform in PLATFORMS:
        if platform not in changed:
            continue
        known, folders = versions(root, platform), changed[platform]
        if quick:
            picked = [next(iter(folders))] if len(folders) == 1 and None not in folders else [known[-1]]
        else:
            picked = known if None in folders else [v for v in known if v in folders]
        result.extend(target(root, platform, version) for version in picked)
    return result[:1] if quick else result


def read_table(path):
    """A manifest's rows as dicts by its header, empty trailing cells left out or not; blank lines and lines starting
    with # are left out."""
    lines = [line for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip() and not line.startswith("#")]
    header = lines[0].split("\t")
    rows = []
    for number, line in enumerate(lines[1:], 2):
        cells = line.split("\t")
        if len(cells) > len(header):
            raise SystemExit(f"{path}: row {number} ({cells[0]}) has {len(cells)} cells, more than its {len(header)} columns")
        rows.append(dict(zip(header, cells + [""] * (len(header) - len(cells)))))
    return rows


def applies(row, platform, version):
    words = row[platform].split()
    return ("*" in words or version in words) and f"!{version}" not in words


def why_not(row, platform, version=None):
    """The row's reason for not applying to the platform (and version), or for its precondition (platform "when")."""
    reasons = {}
    for entry in filter(None, (e.strip() for e in row.get("why_not", "").split("; "))):
        key, _, text = entry.partition(": ")
        reasons[key] = text
    found = reasons.get(f"{platform} {version}") or reasons.get(platform)
    if found:
        return found
    if platform == "when":
        return "its precondition doesn't hold"
    return f"not on {version}" if row[platform].strip() else f"{platform}'s test mods have no such check"


def runs(root, platform, version, part, extended, names=()):
    """The part's runs for the version, in the manifest's order, with the version's pins filled into their mods."""
    pins = target(root, platform, version)

    def fill(match):
        words = pins[match.group(1)].split()
        if match.group(2):
            words = [w for w in words if w.split("@")[0] == match.group(2)]
        return ",".join(words)

    result = []
    for row in read_table(HERE / "runs.tsv"):
        if row["part"] != part or not applies(row, platform, version) or (row["extended"] == "yes" and not extended):
            continue
        if names and row["run"] not in names:
            continue
        row["mods"] = re.sub(r"\{(\w+)(?:\.([\w-]+))?\}", fill, row["mods"])
        result.append(row)
    return result


def skip_reason(row):
    """Why a run is skipped when what it needs isn't there: its why_not's "when:" reason."""
    return why_not(row, "when") if "when: " in row.get("why_not", "") else "nothing to run without it"


def parts(root, platform, version, extended):
    """The parts the version's runs make up, each with the sides it starts ("server", "client" or both)."""
    found = {}
    for row in read_table(HERE / "runs.tsv"):
        if applies(row, platform, version) and (row["extended"] != "yes" or extended):
            found.setdefault(row["part"], set()).add(row["side"])
    return {part: " ".join(sorted(sides, reverse=True)) for part, sides in found.items()}


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True).returncode == 0


def changed_paths(root, event, ref, before):
    """The paths the event changed, or None for every version (a tag, a run by hand, a diff that can't be made)."""
    if event == "pull_request":
        base = "HEAD^1"
    elif event == "push" and ref.startswith("refs/heads/"):
        if before and set(before) != {"0"} and git("fetch", "-q", "--depth=1", "origin", before):
            base = before
        else:
            # A new branch: against the base branch of the platform the tree holds.
            platforms = held(root)
            branch = BASE[platforms[0]] if len(platforms) == 1 else "main"
            if not git("fetch", "-q", "--depth=1", "origin", branch):
                return None
            base = "FETCH_HEAD"
    else:
        return None
    diff = subprocess.run(["git", "diff", "--name-only", base, "HEAD"], capture_output=True, text=True)
    return diff.stdout.split() if diff.returncode == 0 else None


def plan(root, event, ref, paths, extended=False, covered=False, called=False):
    """The jobs' inputs: fast (the fast job, booting boot's version), core, targets (each built in a job of its own),
    tests (each target's parts, a job each, game when there are any) and save (caches written: a push to a base
    branch). A call from another workflow (the release) runs every version, whatever event started its caller."""
    result = {"fast": False, "boot": {}, "core": False, "targets": [], "tests": [], "game": False, "save": False}
    branch_push = event == "push" and ref.startswith("refs/heads/") and not called
    if branch_push and ref not in BASE_REFS:
        # A working branch: the unit tests, and one touched version booted, unless an open pull request into a base
        # branch runs its full matrix on this push anyway.
        if not covered:
            boot = targets(root, paths, quick=True)
            result.update(core=True, fast=bool(boot), boot=boot[0] if boot else {})
        return result
    if branch_push:
        # A merge into a base branch, whose pull request passed the full matrix: what it changed is built, so the base
        # branch's caches, which pull requests read, stay current.
        result.update(core=paths is None or any(platforms_of(p) != () for p in paths), targets=targets(root, paths), save=True)
        return result
    found = targets(root, paths if event == "pull_request" and not called else None)
    tests = [dict(t, part=part, sides=sides) for t in found
             for part, sides in sorted(parts(root, t["platform"], t["minecraft"], extended).items())]
    result.update(core=True, targets=found, tests=tests, game=bool(found))
    return result


def main(args):
    root = Path.cwd()
    if args[:1] == ["ci"]:
        env = os.environ
        event, ref = env["EVENT"], env["REF"]
        extended, called = env.get("EXTENDED") == "true", bool(env.get("CALLED_REF"))
        paths = None if called else changed_paths(root, event, ref, env.get("BEFORE", ""))
        result = plan(root, event, ref, paths, extended, env.get("COVERED") == "true", called)
        boot = result["boot"]
        result["boot_key"] = f"{boot['platform']}-{boot['minecraft']}" if boot else "core"
        result["extended"] = extended
        # For names that must differ between the release's calls, one per branch.
        result["platforms"] = "-".join(held(root))
        with open(env["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
            for key, value in result.items():
                out.write(f"{key}={json.dumps(value) if isinstance(value, (dict, list, bool)) else value}\n")
        names = " ".join(f"{t['platform']} {t['minecraft']}" for t in result["targets"]) or "none"
        print(f"Event {event}, paths {'all' if paths is None else len(paths)}: built {names}; "
              f"{len(result['tests'])} other part(s); fast job: {result['fast']} ({result['boot_key']})")
    elif args[:1] == ["target"] and len(args) == 3:
        for key, value in target(root, args[1], args[2]).items():
            print(f"{key}='{value}'")
    elif args[:1] == ["runs"] and len(args) >= 4:
        extended = "--extended" in args
        names = [a for a in args[4:] if a != "--extended"]
        for row in runs(root, args[1], args[2], args[3], extended, names):
            print("\x1f".join([*(row[k] for k in ("run", "side", "gates", "needs", "mods", "env")), skip_reason(row)]))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
