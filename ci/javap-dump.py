#!/usr/bin/env python3
"""Prints classes' bytecode for findings that turn on it (CI only): a game class as NeoForge's patched jars have it,
a mod's class as its jar (or a jar nested in it) has it and as Fabricated's translation left it, or a text file from a
mod's jar. Reads jars in memory with size caps and hands javap one class file at a time; never runs mod code.

Usage: ci/javap-dump.py <client or server work directory> <spec>...
  game:<class>[#member]   the class (or its members of that name) in the game's patched jars
  mod:<class>[#member]    the class in the mods' jars and Fabricated's translations; <class> may be *Name, any
                          class of that simple name
  file:<path>             a text file at that path in the mods' jars (a mixin config, say)
  mixins:<class>          the mixins in the mods' jars (as they ship) that target the class (internal name)
  refs:<text>             the methods of the mods' classes (as they ship) whose code names the text (a class's
                          internal name, say), up to 8 classes
A member is a name, a name's start followed by * (lambda$charTyped$*), "class" (the class's header and attributes:
its inner classes, its @Mixin) or * (every member's declaration and descriptor, without code). A class or file that is
the same as one already printed is named, not printed again (the same translation in several cache jars).
"""
import glob
import hashlib
import io
import os
import re
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


def javap(data, verbose=True):
    """javap -v -p (or -c -p) of one class file, without its constant pool."""
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "Dumped.class")
        with open(path, "wb") as f:
            f.write(data)
        try:
            out = subprocess.run(["javap", "-v" if verbose else "-c", "-p", path], capture_output=True, text=True, timeout=60).stdout
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


def member_name(declaration):
    """The name a javap declaration line declares: a method's (a constructor's simple class name) or a field's."""
    line = declaration.strip()
    method = re.search(r"([\w$.<>]+)\(", line)
    if method:
        return method.group(1).rsplit(".", 1)[-1]
    field = re.search(r"([\w$]+);$", line)
    return field.group(1) if field else ""


def wanted(name, pattern):
    return name.startswith(pattern[:-1]) if pattern.endswith("*") else name == pattern


def members(listing, pattern):
    """The members of a javap listing whose names match `pattern` (methods and fields), each up to the blank line ending
    it; with "*", every member's declaration and descriptor only."""
    found, current, inside = [], None, False
    for line in listing.splitlines():
        if not inside:
            inside = line == "{"
            continue
        if current is None:
            if line == "}":
                break
            declaration = line.startswith("  ") and not line.startswith("   ")
            if declaration and (pattern == "*" or wanted(member_name(line), pattern)):
                current = [line]
        elif line.strip() and not line.startswith("}"):
            if pattern != "*" or line.strip().startswith("descriptor:"):
                current.append(line)
        else:
            found.append(current)
            current = None
            if line.startswith("}"):
                break
    if current:
        found.append(current)
    return ["\n".join(block) for block in found]


def members_code(listing):
    """The members of a javap -c listing, each with its code: a block per declaration at the listing's member indent."""
    blocks, current, inside = [], None, False
    for line in listing.splitlines():
        if not inside:
            inside = line.rstrip().endswith("{")
            continue
        if line.startswith("}"):
            break
        if line.startswith("  ") and not line.startswith("   ") and line.strip():
            if current:
                blocks.append("\n".join(current))
            current = [line]
        elif current is not None and line.strip():
            current.append(line)
    if current:
        blocks.append("\n".join(current))
    return blocks


def class_part(listing):
    """A javap listing without its members: the class's header and its attributes."""
    lines = listing.splitlines()
    try:
        start = lines.index("{")
        end = len(lines) - 1 - lines[::-1].index("}")
    except ValueError:
        return listing
    return "\n".join(lines[:start] + lines[end + 1:])


def show(title, data, member):
    listing = javap(data)
    print(f"-- {title}")
    if member == "class":
        text = class_part(listing)
    elif member:
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


def mod_classes(mods, needle):
    """(where, entry name, bytes) of the classes in the mods' jars (and the jars nested in them) holding the bytes."""
    for jar in mods:
        data = read_jar(jar)
        for where, entry, read in entries(os.path.basename(jar), data) if data else ():
            if entry.endswith(".class"):
                data_ = read()
                if needle in data_:
                    yield where, entry, data_


def refs(mods, what, limit):
    """Prints the methods of the mods' classes whose code names `what`, from up to `limit` classes; returns how many."""
    found = 0
    for where, entry, data_ in mod_classes(mods, what.encode()):
        if found >= limit:
            break
        if entry == what + ".class":
            continue
        blocks = [b for b in members_code(javap(data_, verbose=False)) if what in b or what.replace("/", ".") in b]
        if not blocks:
            continue
        found += 1
        print(f"-- {entry[:-6]} in {where}: its code naming {what}")
        for block in blocks[:6]:
            lines = block.splitlines()
            for line in lines[:60]:
                print("  " + line.rstrip()[:300])
            if len(lines) > 60:
                print(f"  ... {len(lines) - 60} more line(s)")
    if not found:
        print(f"-- no class in the mods' jars names {what} in its code")
    return found


def without_code(listing):
    """A javap -v listing without its members' code (their declarations, descriptors, flags and annotations)."""
    out, code = [], False
    for line in listing.splitlines():
        indent = len(line) - len(line.lstrip(" "))
        if code and (indent > 4 or not line.strip()):
            if not line.strip():
                code = False
                out.append(line)
            continue
        code = line.strip() == "Code:" and indent == 4
        if not code:
            out.append(line)
    return "\n".join(out)


def printed_before(title, data, printed):
    """Whether the same bytes were printed for this spec already; names the earlier one if so."""
    digest = hashlib.sha256(data).hexdigest()
    if digest in printed:
        print(f"-- {title}: the same as {printed[digest]}")
        return True
    printed[digest] = title
    return False


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
        printed = {}
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
                        title = f"{entry[:-6]} in {where}{label}" + (f", member {member}" if member else "")
                        data = read()
                        if not printed_before(title, data, printed):
                            show(title, data, member)
                        found += 1
                    elif kind == "file" and entry == what:
                        title = f"{what} in {where}{label}"
                        data = read()
                        if not printed_before(title, data, printed):
                            print(f"-- {title}")
                            for line in data.decode("utf-8", "replace").splitlines()[:LINES]:
                                print("  " + line.rstrip()[:300])
                        found += 1
        elif kind == "mixins":
            accessors = []
            for where, entry, data_ in mod_classes(mods, what.encode()):
                if found >= 8:
                    break
                if b"org/spongepowered/asm/mixin/Mixin" not in data_:
                    continue
                listing = javap(data_)
                if what not in class_part(listing):
                    continue
                found += 1
                print(f"-- {entry[:-6]} in {where}, a mixin of {what} (without code)")
                text = without_code(listing)
                for line in text.splitlines()[:LINES]:
                    print("  " + line.rstrip()[:300])
                if "ACC_INTERFACE" in class_part(listing):
                    accessors.append(entry[:-6])
            # An accessor interface's callers: what the mod does with what it reaches.
            for accessor in accessors[:4]:
                refs(mods, accessor, 4)
        elif kind == "refs":
            refs(mods, what, 8)
            found = 1  # refs says so itself
        if not found:
            print(f"-- {spec}: not found in {len(game) if kind == 'game' else len(mods) + len(translated)} jar(s)")


if __name__ == "__main__":
    main()
