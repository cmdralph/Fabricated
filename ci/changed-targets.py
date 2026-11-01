#!/usr/bin/env python3
"""Which platforms and Minecraft versions CI builds and tests, from the paths a change touched (one per line on stdin).

A branch holds one platform's folders (main NeoForge's, forge and forge-legacy theirs). A path belongs to a platform
when it is in mc/<platform>/, loader/<platform>/ or ci/<platform>/, or is mc/platform-<platform>.gradle or the
platform's workflow; mc/forge-shared, loader/forge-shared and mc/forge-mixinextras.gradle are both Forge platforms'.
Anything else but documentation is shared code (core, api, loader/common, the build, ci/): it touches every platform
the branch holds. A platform the branch doesn't hold (its files deleted) builds nothing, nor does documentation.

Three modes:
  (default)  the quick check (check.yml): per touched platform one version, the version whose folder alone changed
             (mc/<platform>/<version>/), else its newest.
  --full     the full matrix of a pull request (build.yml, build-forge.yml): per touched platform the versions whose own
             folders changed, when nothing else of the platform did, else every version.
  --all      every version of every platform the branch holds (release tags, runs by hand); stdin is not read.

Prints a JSON list of {"platform", "minecraft", "loader", "java", "fabric_api", "plugin_hosts"} for a matrix, read from
each version's gradle.properties (the loader version, java_version, ci_plugin_hosts) and its Fabric API plan
(fabricApiVersion). Usage: git diff --name-only A B | ci/changed-targets.py [--full | --all] [repository root]
"""
import json
import sys
from pathlib import Path

PLATFORMS = ("neoforge", "forge", "forge-legacy")
LOADER_PROPERTY = {"neoforge": "neoforge_version", "forge": "forge_version", "forge-legacy": "forge_version"}
WORKFLOW = {"neoforge": "build.yml", "forge": "build-forge.yml", "forge-legacy": "build-forge-legacy.yml"}
FABRIC_API_PLAN = "src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json"


def properties(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def versions(root, platform):
    """The platform's Minecraft versions (folders with a gradle.properties), oldest first."""
    folders = [p.parent.name for p in (root / "mc" / platform).glob("*/gradle.properties")]
    return sorted(folders, key=lambda v: tuple(int(n) for n in v.split(".")))


def platforms_of(path):
    """The platforms a path belongs to alone (both Forge platforms for the forge-shared folders), or None for shared
    code."""
    if path.startswith(("mc/forge-shared/", "loader/forge-shared/")) or path == "mc/forge-mixinextras.gradle":
        return ("forge", "forge-legacy")
    for platform in PLATFORMS:
        if (path.startswith((f"mc/{platform}/", f"loader/{platform}/", f"ci/{platform}/"))
                or path in (f"mc/platform-{platform}.gradle", f".github/workflows/{WORKFLOW[platform]}")):
            return (platform,)
    return None


def is_docs(path):
    return path.startswith("docs/") or path.endswith(".md")


def touched(root, paths):
    """platform -> the version folders changed under mc/<platform>/ (None: some other file of the platform)."""
    held = [platform for platform in PLATFORMS if versions(root, platform)]
    result = {}
    for path in paths:
        if is_docs(path):
            continue
        parts = path.split("/")
        for platform in platforms_of(path) or held:
            if platform not in held:
                continue
            known = versions(root, platform)
            version = parts[2] if len(parts) > 3 and parts[0] == "mc" and parts[1] == platform and parts[2] in known else None
            result.setdefault(platform, set()).add(version)
    return result


def target(root, platform, version):
    folder = root / "mc" / platform / version
    props = properties(folder / "gradle.properties")
    plan = folder / FABRIC_API_PLAN
    fabric_api = json.loads(plan.read_text(encoding="utf-8")).get("fabricApiVersion", "") if plan.is_file() else ""
    return {"platform": platform, "minecraft": version, "loader": props[LOADER_PROPERTY[platform]],
            "java": props["java_version"], "fabric_api": fabric_api, "plugin_hosts": props.get("ci_plugin_hosts", "")}


def targets(root, paths, mode="quick"):
    if mode == "all":
        changed = {platform: {None} for platform in PLATFORMS if versions(root, platform)}
    else:
        changed = touched(root, paths)
    result = []
    for platform in PLATFORMS:
        if platform not in changed:
            continue
        known = versions(root, platform)
        folders = changed[platform]
        if mode == "quick":
            picked = [next(iter(folders))] if len(folders) == 1 and None not in folders else [known[-1]]
        else:
            picked = known if None in folders else [v for v in known if v in folders]
        result.extend(target(root, platform, version) for version in picked)
    return result


if __name__ == "__main__":
    args = sys.argv[1:]
    mode = "quick"
    if args and args[0] in ("--full", "--all"):
        mode = args.pop(0)[2:]
    repo = Path(args[0] if args else ".")
    paths = [] if mode == "all" else [line.strip() for line in sys.stdin if line.strip()]
    print(json.dumps(targets(repo, paths, mode)))
