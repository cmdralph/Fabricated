#!/usr/bin/env python3
"""CI diagnostic: for each class a game log says it failed to find, list the mod jars (nested jars too) that contain
it, or a mixin config or class naming its package. Reads jars in memory with size caps; never extracts or runs them.

Usage: find-classes.py <game log> <mods dir>
"""
import io
import re
import sys
import zipfile
from pathlib import Path

MAX_ENTRY = 64 * 1024 * 1024
MAX_DEPTH = 4


def scan(name, data, wanted, depth, hits):
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return
    for info in archive.infolist():
        if info.file_size > MAX_ENTRY:
            continue
        entry = info.filename
        if entry.endswith(".jar") and depth < MAX_DEPTH:
            scan(name + "!/" + entry, archive.read(info), wanted, depth + 1, hits)
            continue
        for cls in wanted:
            if entry == cls + ".class":
                hits.append(f"{name}!/{entry}: the class itself")
            elif entry.endswith((".json", ".class")):
                content = archive.read(info)
                package = cls.rsplit("/", 1)[0]
                # Fabric API keeps a module's mixins next to its implementation: impl/x -> mixin/x.
                for pkg in (package, package.replace("/impl/", "/mixin/")):
                    if pkg.encode() in content or pkg.replace("/", ".").encode() in content:
                        hits.append(f"{name}!/{entry}: names {pkg}")
                        break


def main():
    log, mods = Path(sys.argv[1]), Path(sys.argv[2])
    text = log.read_text(errors="replace") if log.is_file() else ""
    wanted = sorted(set(re.findall(r"Failed to find class ([\w/$]+)", text)) | set(
        c.replace(".", "/") for c in re.findall(r"ClassNotFoundException: ([\w.$]+)", text)))
    wanted = [w for w in wanted if w.startswith("net/fabricmc/fabric/")][:5]
    if not wanted:
        return
    for jar in sorted(mods.glob("*.jar")):
        hits = []
        scan(jar.name, jar.read_bytes(), wanted, 0, hits)
        for hit in hits[:20]:
            print(hit)


if __name__ == "__main__":
    main()
