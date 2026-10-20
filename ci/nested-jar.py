#!/usr/bin/env python3
"""Copies a Fabric mod nested in other mods' jars (META-INF/jars) out of them (CI only): for a library with no build
of its own on Modrinth that mods ship inside theirs. Takes the first nested jar whose fabric.mod.json has the id and
prints where it came from; prints nothing when no jar has it.

Usage: ci/nested-jar.py <mod-id> <out-dir> <jar>...
"""
import io
import json
import os
import sys
import zipfile

MAX_BYTES = 64 << 20


def mod_id(data):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            return json.loads(jar.read("fabric.mod.json"), strict=False).get("id")
    except (KeyError, ValueError, zipfile.BadZipFile):
        return None


def main():
    wanted, out, jars = sys.argv[1], sys.argv[2], sys.argv[3:]
    os.makedirs(out, exist_ok=True)
    for path in jars:
        try:
            with zipfile.ZipFile(path) as jar:
                for info in jar.infolist():
                    if not (info.filename.startswith("META-INF/jars/") and info.filename.endswith(".jar")) or info.file_size > MAX_BYTES:
                        continue
                    data = jar.read(info)
                    if mod_id(data) == wanted:
                        name = os.path.basename(info.filename)
                        with open(os.path.join(out, name), "wb") as f:
                            f.write(data)
                        print(f"{name} (nested in {os.path.basename(path)})")
                        return
        except (OSError, zipfile.BadZipFile) as e:
            print(f"{os.path.basename(path)}: not read: {e}", file=sys.stderr)


if __name__ == "__main__":
    main()
