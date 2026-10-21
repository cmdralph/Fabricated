#!/usr/bin/env python3
"""For a crash where Mixin could not apply an injector that pre-flight passed (CI only): the handler's annotations, as
the mod's jar has them, and the code of the target methods in the game's patched jar, so what Mixin searched is in the
log (CORPUS-18).

Usage: ci/mixin-diag.py <crash report> <client work directory (run/mods, minecraft/libraries)>
"""
import glob
import json
import re
import subprocess
import sys
import zipfile

LINES = 60


def javap(*args):
    try:
        return subprocess.run(["javap", *args], capture_output=True, text=True, timeout=60).stdout
    except (OSError, subprocess.SubprocessError):
        return ""


def blocks(listing, name):
    """The members of a javap listing whose declaration names `name(`, each up to the blank line ending it."""
    found, current = [], None
    for line in listing.splitlines():
        if current is None:
            if line.startswith("  ") and not line.startswith("   ") and f" {name}(" in line:
                current = [line]
        elif line.strip():
            current.append(line)
        else:
            found.append(current)
            current = None
    if current:
        found.append(current)
    return found


def main():
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        crash = f.read()
    work = sys.argv[2]
    m = re.search(r"method ([A-Za-z0-9_$]+)\(.*? in ([A-Za-z0-9_.-]+\.json):([A-Za-z0-9_.$]+) from mod", crash)
    if not m:
        return
    handler, config, mixin = m.groups()
    for jar in glob.glob(f"{work}/run/mods/*.jar"):
        try:
            with zipfile.ZipFile(jar) as z:
                if config not in z.namelist():
                    continue
                package = json.loads(z.read(config)).get("package", "")
        except (OSError, zipfile.BadZipFile, ValueError):
            continue
        listing = javap("-v", "-p", "-cp", jar, f"{package}.{mixin}")
        print(f"-- {mixin} in {jar.rsplit('/', 1)[-1]}: {handler}, as the jar has it")
        handler_lines = [line for block in blocks(listing, handler) for line in block
                         if not re.match(r"\s+(\d+: |stack=|line \d+|LocalVariableTable|Start +Length|LineNumberTable)", line)]
        for line in handler_lines[:LINES]:
            print("  " + line.rstrip()[:250])
        targets = re.findall(r"class (net/minecraft/[A-Za-z0-9_/$]+)", listing.split("RuntimeInvisibleAnnotations")[-1])
        methods = {name.split("(")[0] for name in re.findall(r'"([A-Za-z0-9_$<>]+(?:\([^"]*)?)"', " ".join(
            re.findall(r"method=\[([^\]]*)\]", "\n".join(handler_lines))))}
        patched = glob.glob(f"{work}/minecraft/libraries/**/minecraft-client-patched-*.jar", recursive=True)
        if not targets or not methods or not patched:
            return
        target = targets[0].replace("/", ".")
        code = javap("-c", "-p", "-cp", patched[0], target)
        for name in sorted(methods):
            for block in blocks(code, name)[:3]:
                print(f"-- {target}: {block[0].strip()[:200]}, as the game has it")
                for line in block[1:LINES]:
                    print("  " + line.rstrip()[:200])
        return


if __name__ == "__main__":
    main()
