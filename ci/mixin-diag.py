#!/usr/bin/env python3
"""For a crash where Mixin could not apply an injector that pre-flight passed (CI only): the handler's annotations, as
the mod's jar has them and as Fabricated's translation left them, the code of the target methods in the game's
patched jar, the other mixins naming the target class and those methods, and what Fabricated's report says of the mixin
and of its mod, so what Mixin searched is in the log (CORPUS-18).

Usage: ci/mixin-diag.py <crash report> <client work directory (run/mods, run/.fabricated, minecraft/libraries)>
"""
import glob
import json
import re
import subprocess
import sys
import zipfile

LINES = 60
MIXIN = b"Lorg/spongepowered/asm/mixin/Mixin;"


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


def handler_lines(listing, handler):
    return [line for block in blocks(listing, handler) for line in block
            if not re.match(r"\s+(\d+: |stack=|line \d+|LocalVariableTable|Start +Length|LineNumberTable)", line)]


def print_handler(title, listing, handler):
    print(title)
    lines = handler_lines(listing, handler)
    for line in lines[:LINES]:
        print("  " + line.rstrip()[:250])
    return lines


def others(jars, own, target, methods):
    """Mixin classes in `jars`, but `own`, whose bytes name the target class and one of the methods."""
    needle = target.encode()
    names = [m.encode() for m in methods]
    found = []
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as z:
                for entry in z.namelist():
                    if not entry.endswith(".class") or entry == own:
                        continue
                    data = z.read(entry)
                    if MIXIN in data and needle in data and any(n in data for n in names):
                        found.append(f"{jar.rsplit('/', 1)[-1]}: {entry[:-6]}")
        except (OSError, zipfile.BadZipFile):
            continue
    return found


def main():
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        crash = f.read()
    work = sys.argv[2]
    m = re.search(r"method ([A-Za-z0-9_$]+)\(.*? in ([A-Za-z0-9_.-]+\.json):([A-Za-z0-9_.$]+) from mod ([A-Za-z0-9_.-]+)", crash)
    if not m:
        return
    handler, config, mixin, mod = m.groups()
    overlays = glob.glob(f"{work}/run/.fabricated/cache/**/overlay.jar", recursive=True)
    for jar in glob.glob(f"{work}/run/mods/*.jar"):
        try:
            with zipfile.ZipFile(jar) as z:
                if config not in z.namelist():
                    continue
                package = json.loads(z.read(config)).get("package", "")
        except (OSError, zipfile.BadZipFile, ValueError):
            continue
        name = f"{package}.{mixin}"
        entry = name.replace(".", "/") + ".class"
        listing = javap("-v", "-p", "-cp", jar, name)
        lines = print_handler(f"-- {mixin} in {jar.rsplit('/', 1)[-1]}: {handler}, as the jar has it", listing, handler)
        for overlay in overlays:
            try:
                with zipfile.ZipFile(overlay) as z:
                    if entry not in z.namelist():
                        continue
            except (OSError, zipfile.BadZipFile):
                continue
            translated = javap("-v", "-p", "-cp", overlay, name)
            lines = print_handler(f"-- {mixin}: {handler}, as Fabricated's translation has it", translated, handler) or lines
            listing = translated
        # javap prints a class-valued annotation element as `class Lpkg/Name;`.
        targets = re.findall(r"class L?(net/minecraft/[A-Za-z0-9_/$]+);?", listing.split("RuntimeInvisibleAnnotations")[-1])
        methods = {name.split("(")[0] for name in re.findall(r'"([A-Za-z0-9_$<>]+(?:\([^"]*)?)"', " ".join(
            re.findall(r"method=\[([^\]]*)\]", "\n".join(lines))))}
        patched = glob.glob(f"{work}/minecraft/libraries/**/minecraft-client-patched-*.jar", recursive=True)
        print(f"-- targets {targets}, methods {sorted(methods)}, patched jar {'found' if patched else 'not found'}, {len(overlays)} translation overlay(s)")
        # What the report says of the mixin, and of its mod (its row, notes and findings), in case none names the mixin.
        simple = mixin.rsplit(".", 1)[-1]
        for report in glob.glob(f"{work}/run/logs/fabric-bridge-report*.txt"):
            with open(report, encoding="utf-8", errors="replace") as f:
                lines_of_report = f.read().splitlines()
            for i, line in enumerate(lines_of_report):
                words = line.split()
                if simple in line or words and words[0] in (mod, mod + ":"):
                    print("-- report: " + line.rstrip()[:300])
                    # A mod's row continues on the indented lines below it.
                    for detail in lines_of_report[i + 1:i + 4]:
                        if not detail.startswith("      "):
                            break
                        print("-- report:   " + detail.strip()[:300])
        if not targets or not methods:
            return
        target = targets[0]
        for other in others(glob.glob(f"{work}/run/mods/*.jar") + overlays, entry, target, methods):
            print(f"-- another mixin naming {target} and {sorted(methods)}: {other}")
        if not patched:
            return
        code = javap("-c", "-p", "-cp", patched[0], target.replace("/", "."))
        for method in sorted(methods):
            for block in blocks(code, method)[:3]:
                print(f"-- {target}: {block[0].strip()[:200]}, as the game has it")
                for line in block[1:LINES]:
                    print("  " + line.rstrip()[:200])
        return


if __name__ == "__main__":
    main()
