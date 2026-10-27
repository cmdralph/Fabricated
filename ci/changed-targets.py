#!/usr/bin/env python3
"""The quick check's targets (.github/workflows/check.yml): which platform and Minecraft version to build and boot for
a push, from the paths it changed (one per line on stdin).

A platform whose files changed gets one version: the version whose folder alone changed (mc/<platform>/<version>/),
else its newest; mc/forge-shared is both Forge platforms'. A change to shared code (core, api, loader/common, the
build) builds NeoForge's newest, the supported platform; the full matrix on the pull request covers the rest.
Documentation builds nothing.

Prints a JSON list of {"platform", "minecraft", "loader", "java"} for a matrix's include, read from each version's
gradle.properties. Usage: git diff --name-only A B | ci/changed-targets.py [repository root]
"""
import json
import sys
from pathlib import Path

PLATFORMS = ("neoforge", "forge", "forge-legacy")
LOADER_PROPERTY = {"neoforge": "neoforge_version", "forge": "forge_version", "forge-legacy": "forge_version"}
WORKFLOW = {"neoforge": "build.yml", "forge": "build-forge.yml", "forge-legacy": "build-forge-legacy.yml"}


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
    """The platforms a path belongs to alone (both Forge platforms for mc/forge-shared), or None for shared code."""
    if path.startswith("mc/forge-shared/"):
        return ("forge", "forge-legacy")
    for platform in PLATFORMS:
        if (path.startswith((f"mc/{platform}/", f"loader/{platform}/", f"ci/{platform}/"))
                or path in (f"mc/platform-{platform}.gradle", f".github/workflows/{WORKFLOW[platform]}")):
            return (platform,)
    return None


def is_docs(path):
    return path.startswith("docs/") or path.endswith(".md")


def targets(root, paths):
    touched = {}  # platform -> the version folders changed under mc/<platform>/ (None: some other file of it)
    for path in paths:
        if is_docs(path):
            continue
        parts = path.split("/")
        for platform in platforms_of(path) or ("neoforge",):
            known = versions(root, platform)
            version = parts[2] if len(parts) > 3 and parts[0] == "mc" and parts[1] == platform and parts[2] in known else None
            touched.setdefault(platform, set()).add(version)
    result = []
    for platform in PLATFORMS:
        if platform not in touched:
            continue
        changed = touched[platform]
        known = versions(root, platform)
        version = next(iter(changed)) if len(changed) == 1 and None not in changed else known[-1]
        props = properties(root / "mc" / platform / version / "gradle.properties")
        result.append({"platform": platform, "minecraft": version, "loader": props[LOADER_PROPERTY[platform]],
                       "java": props["java_version"]})
    return result


if __name__ == "__main__":
    repo = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    print(json.dumps(targets(repo, [line.strip() for line in sys.stdin if line.strip()])))
