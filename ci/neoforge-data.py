#!/usr/bin/env python3
"""CI diagnostic: lists the NeoForge biome and structure modifiers that Fabric mod jars (nested jars too) carry in
their data. NeoForge applies such a file in any jar, while Fabric ignores it, so a mod that adds a feature both ways
adds it twice under Fabricated (the 26.1.2 advanced client's "Feature order cycle" names one biome). Reads jars in
memory with size caps; never extracts or runs them.

Usage: neoforge-data.py <jar>...
"""
import io
import re
import sys
import zipfile
from pathlib import Path

MAX_ENTRY = 64 * 1024 * 1024
MAX_DEPTH = 4
MODIFIER = re.compile(r"^data/[^/]+/neoforge/(biome_modifier|structure_modifier)/.+\.json$")


def scan(name, data, depth, hits):
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return
    if "fabric.mod.json" not in archive.namelist():
        return
    for info in archive.infolist():
        if info.file_size > MAX_ENTRY:
            continue
        if info.filename.endswith(".jar") and depth < MAX_DEPTH:
            scan(name + "!/" + info.filename, archive.read(info), depth + 1, hits)
        elif MODIFIER.match(info.filename):
            hits.append(f"{name}: {info.filename}")


def main():
    hits = []
    for path in sys.argv[1:]:
        if Path(path).stat().st_size <= MAX_ENTRY:
            scan(Path(path).name, Path(path).read_bytes(), 0, hits)
    print("\n".join(hits) if hits else "(no Fabric jar carries NeoForge biome or structure modifiers)")


if __name__ == "__main__":
    main()
