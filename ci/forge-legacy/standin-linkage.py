#!/usr/bin/env python3
"""Whether Fabricated's linkage check counted the classes of the modules other mods provide (PROVIDER-1), for the server
smoke run with the stand-ins standins.py writes: no warning in the report's linkage section may name a class in one of
the stand-ins' packages, their nested jars' included. The run has no Fabric API, so the test mod's warnings for the
modules neither Fabricated nor the stand-ins provide (its block break, command, networking and transfer checks, which run
only with Fabric API) are expected, and left alone.

The checker reports at most 25 warnings per mod, and the test mod has about that many here: a warning past those isn't
seen by this check.

Usage: standin-linkage.py <stand-ins-dir> <fabric-bridge-report.txt>
Exit status: 0 if no warning names a stand-in's package; 1, naming each that does, otherwise.
"""
import io
import pathlib
import re
import sys
import zipfile

WARNING = re.compile(r" -> missing (class|field|method) ([\w.$]+)")


def packages(archive, found):
    """The packages of a jar's classes, its nested jars' included."""
    for name in archive.namelist():
        if name.endswith(".class") and "/" in name and not name.startswith("META-INF/"):
            found.add(name.rsplit("/", 1)[0].replace("/", "."))
        elif name.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                packages(nested, found)
    return found


def package_of(kind, reference):
    """The package of a warning's class: a class's name, or a member's (its class, then the member's name)."""
    owner = reference if kind == "class" else reference.rsplit(".", 1)[0]
    return owner.rsplit(".", 1)[0] if "." in owner else ""


def main(standins, report):
    provided = set()
    for path in sorted(pathlib.Path(standins).glob("*.jar")):
        with zipfile.ZipFile(path) as archive:
            packages(archive, provided)
    if not provided:
        raise SystemExit(f"no stand-in classes in {standins}")
    wrong, section = [], False
    for line in pathlib.Path(report).read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith("== "):
            section = line.startswith("== Linkage warnings")
            continue
        match = WARNING.search(line) if section else None
        if match and package_of(match.group(1), match.group(2).split("(", 1)[0]) in provided:
            wrong.append(line.strip())
    for line in wrong:
        print(f"linkage warning for a stand-in's class: {line}")
    return 1 if wrong else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
