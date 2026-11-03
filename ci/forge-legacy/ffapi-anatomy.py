#!/usr/bin/env python3
"""What coexisting with Forgified Fabric API on Forge 47 needs (task 5, PROVIDER-2), read off its jar (CI only).

Prints the jar's jar-in-jar metadata, each jar it nests (its mod id, whether it carries Fabric Loader's packages and
how many of their classes), the packages of the nested copy of Fabric Loader, and the classes of Fabric Loader outside
its API (net.fabricmc.loader.api, net.fabricmc.api) that the other nested jars name, with how many of their classes
name each. The jar is read as data and never run; nested jars are read in memory, two levels deep, within limits.

Usage: python3 -I ci/forge-legacy/ffapi-anatomy.py <jar>
"""
import collections
import io
import re
import sys
import zipfile

MAX_ENTRIES = 100_000
MAX_NESTED_BYTES = 64 << 20
MAX_CLASS_BYTES = 4 << 20
MAX_TEXT = 4096
LOADER_PREFIXES = ("net/fabricmc/loader/", "net/fabricmc/api/")
API_PREFIXES = ("net/fabricmc/loader/api/", "net/fabricmc/api/")
LOADER_REF = re.compile(rb"net/fabricmc/loader/[A-Za-z0-9_/$]+")
MOD_ID = re.compile(r'modId\s*=\s*"([^"]+)"')


def text(zf, name):
    info = zf.getinfo(name)
    if info.file_size > MAX_TEXT * 16:
        return "(too large)"
    return zf.read(name)[:MAX_TEXT].decode("utf-8", "replace")


def nested(zf):
    for info in zf.infolist()[:MAX_ENTRIES]:
        if info.filename.startswith("META-INF/jarjar/") and info.filename.endswith(".jar") and info.file_size <= MAX_NESTED_BYTES:
            try:
                yield info.filename, zipfile.ZipFile(io.BytesIO(zf.read(info)))
            except zipfile.BadZipFile:
                print(f"  {info.filename}: not a readable jar")


def describe(path, zf, uses, loader_packages, depth):
    names = [i.filename for i in zf.infolist()[:MAX_ENTRIES]]
    own = [n for n in names if n.startswith(LOADER_PREFIXES) and n.endswith(".class")]
    mod_id = None
    if "META-INF/mods.toml" in names:
        found = MOD_ID.search(text(zf, "META-INF/mods.toml"))
        mod_id = found.group(1) if found else None
    print(f"{'  ' * depth}{path}: mod id {mod_id or '-'}, {len(names)} entries, {len(own)} classes in Fabric Loader's packages")
    if "META-INF/jarjar/metadata.json" in names:
        print(f"{'  ' * depth}  jarjar metadata: {text(zf, 'META-INF/jarjar/metadata.json')[:1500]}")
    if own:
        for package, count in collections.Counter("/".join(n.split("/")[:4]) for n in own).most_common(25):
            loader_packages[package] += count
    else:
        for info in zf.infolist()[:MAX_ENTRIES]:
            if not info.filename.endswith(".class") or info.file_size > MAX_CLASS_BYTES:
                continue
            refs = {m.decode("ascii", "replace") for m in LOADER_REF.findall(zf.read(info))}
            for ref in refs:
                if not ref.startswith(API_PREFIXES):
                    uses[ref].add(f"{path}!{info.filename}")
    if depth < 2:
        for name, inner in nested(zf):
            with inner:
                describe(name, inner, uses, loader_packages, depth + 1)


def main():
    uses = collections.defaultdict(set)
    loader_packages = collections.Counter()
    with zipfile.ZipFile(sys.argv[1]) as zf:
        describe(sys.argv[1].rsplit("/", 1)[-1], zf, uses, loader_packages, 0)
    print("== the nested copy of Fabric Loader, by package (classes)")
    for package, count in loader_packages.most_common(30):
        print(f"  {package}: {count}")
    print(f"== Fabric Loader's classes outside its API that the other jars name ({len(uses)}), by how many of their classes name each")
    for ref, users in sorted(uses.items(), key=lambda e: -len(e[1]))[:60]:
        print(f"  {ref}: {len(users)} (e.g. {sorted(users)[0]})")


if __name__ == "__main__":
    main()
