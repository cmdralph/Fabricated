#!/usr/bin/env python3
"""Prints classes' bytecode for findings that turn on it (CI only): a game class as NeoForge's patched jars have it,
a mod's class as its jar (or a jar nested in it) has it and as Fabricated's translation left it, or a text file from a
mod's jar. Reads jars in memory with size caps and hands javap one class file at a time; never runs mod code.

Usage: ci/javap-dump.py <client or server work directory> <spec>...
  game:<class>[#member]   the class (or its members of that name) in the game's patched jars
  mod:<class>[#member]    the class in the mods' jars and Fabricated's translations; <class> may be *Name, any
                          class of that simple name
  file:<path>             a text file at that path in the mods' jars (a mixin config, say)
"""
import glob
import io
import os
import subprocess
import sys
import tempfile
import zipfile

MAX_ENTRY = 64 << 20
MAX_DEPTH = 3
LINES = 250
# The jars a NeoForge install loads the game from, the patched ones first: 26.x's minecraft-client-patched jar holds
# the whole game; 21.1's neoforge client jar holds the classes NeoForge patched over the remapped game jar.
GAME_JARS = ("**/minecraft-client-patched-*.jar", "**/minecraft-server-patched-*.jar", "**/neoforge-*-client.jar",
             "**/neoforge-*-server.jar", "**/client-*-srg.jar", "**/server-*-srg.jar")


def javap(data):
    """javap -v -p of one class file, without its constant pool."""
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "Dumped.class")
        with open(path, "wb") as f:
            f.write(data)
        try:
            out = subprocess.run(["javap", "-v", "-p", path], capture_output=True, text=True, timeout=60).stdout
        except (OSError, subprocess.SubprocessError) as e:
            return f"(javap failed: {e})"
    lines, pool = [], False
    for line in out.splitlines():
        if line.startswith("Constant pool:"):
            pool = True
            continue
        if pool:
            if line.startswith("{"):
                pool = False
            else:
                continue
        lines.append(line)
    return "\n".join(lines)


def members(listing, name):
    """The members of a javap listing named `name` (methods and fields), each up to the blank line ending it."""
    found, current = [], None
    for line in listing.splitlines():
        if current is None:
            declaration = line.startswith("  ") and not line.startswith("   ")
            if declaration and (f" {name}(" in line or line.rstrip().endswith(f" {name};")):
                current = [line]
        elif line.strip() and not line.startswith("}"):
            current.append(line)
        else:
            found.append(current)
            current = None
    if current:
        found.append(current)
    return ["\n".join(block) for block in found]


def show(title, data, member):
    listing = javap(data)
    print(f"-- {title}")
    if member:
        blocks = members(listing, member)
        if not blocks:
            print(f"  (no member {member})")
        text = "\n".join(blocks)
    else:
        text = listing
    lines = text.splitlines()
    for line in lines[:LINES]:
        print("  " + line.rstrip()[:300])
    if len(lines) > LINES:
        print(f"  ... {len(lines) - LINES} more line(s)")


def entries(name, data, depth=0):
    """(where, entry name, reader) for every entry of a jar and of the jars nested in it."""
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return
    for info in archive.infolist():
        if info.file_size > MAX_ENTRY:
            continue
        if info.filename.endswith(".jar") and depth < MAX_DEPTH:
            yield from entries(name + "!/" + info.filename, archive.read(info), depth + 1)
        else:
            yield name, info.filename, (lambda i=info: archive.read(i))


def read_jar(path):
    try:
        if os.path.getsize(path) > MAX_ENTRY * 4:
            return None
        with open(path, "rb") as f:
            return f.read()
    except OSError:
        return None


def matches(entry, cls):
    if not entry.endswith(".class"):
        return False
    if cls.startswith("*"):
        return entry.rsplit("/", 1)[-1] == cls[1:] + ".class"
    return entry == cls.replace(".", "/") + ".class"


def main():
    work, specs = sys.argv[1], sys.argv[2:]
    game = []
    for pattern in GAME_JARS:
        game += sorted(glob.glob(os.path.join(work, "minecraft", "libraries", pattern), recursive=True))
        game += sorted(glob.glob(os.path.join(work, "libraries", pattern), recursive=True))
    mods = sorted(glob.glob(os.path.join(work, "run", "mods", "*.jar"))) + sorted(glob.glob(os.path.join(work, "mods", "*.jar")))
    translated = []
    for cache in (os.path.join(work, "run", ".fabricated", "cache"), os.path.join(work, ".fabricated", "cache")):
        translated += sorted(glob.glob(os.path.join(cache, "**", "*.jar"), recursive=True))
    for spec in specs:
        kind, _, what = spec.partition(":")
        cls, _, member = what.partition("#")
        found = 0
        if kind == "game":
            for jar in game:
                data = read_jar(jar)
                for where, entry, read in entries(os.path.basename(jar), data) if data else ():
                    if matches(entry, cls):
                        show(f"{cls} in the game's {where}" + (f", member {member}" if member else ""), read(), member)
                        found += 1
                        break
                if found:
                    break  # the first jar that has it is the one the game loads it from
        elif kind in ("mod", "file"):
            for jar, label in [(j, "") for j in mods] + [(j, " (Fabricated's translation)") for j in translated]:
                data = read_jar(jar)
                for where, entry, read in entries(os.path.basename(jar), data) if data else ():
                    if kind == "mod" and matches(entry, cls):
                        show(f"{entry[:-6]} in {where}{label}" + (f", member {member}" if member else ""), read(), member)
                        found += 1
                    elif kind == "file" and entry == what:
                        print(f"-- {what} in {where}{label}")
                        text = read().decode("utf-8", "replace").splitlines()
                        for line in text[:LINES]:
                            print("  " + line.rstrip()[:300])
                        found += 1
        if not found:
            print(f"-- {spec}: not found in {len(game) if kind == 'game' else len(mods) + len(translated)} jar(s)")


if __name__ == "__main__":
    main()
