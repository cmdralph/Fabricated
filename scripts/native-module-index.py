#!/usr/bin/env python3
"""Writes a version's native-modules.json: the packages of each Fabric API module Fabricated ships natively.

Fabricated leaves out its own copy of a native module when another mod already brings that module (Forgified Fabric
API, Sodium's bundled modules): it then needs to know which of its in-game mod's packages belong to which module. Each
Fabric API module's packages are read from the official Fabric API release of the version's plan: its jar nests one jar
per module. Only the packages Fabricated's in-game mod has are kept.

Usage: scripts/native-module-index.py <fabric-api-jar> <fabric-api-plan.json> <game-mod.jar> <output.json> [<package>=<module>...]
<package>=<module> assigns a package to one module: a package of Fabricated's own that no module of that release has (a
stand-in for a later release's class), or one that several native modules share on Fabric's single class path (Fabric
API's net/fabricmc/fabric/api/util is fabric-api-base's and fabric-content-registries-v0's), which a module layer allows
in one module only: the one Forgified Fabric API puts it in, as the mods that provide the module are built from it. The
script refuses a shared package without one.
Re-run it with the plan's Fabric API release whenever a version's native modules or their sources change; review the
diff before committing. The in-game mod's build checks that each of its Fabric API packages is listed.
"""
import io
import json
import sys
import zipfile


def packages(archive: zipfile.ZipFile) -> set[str]:
    found = set()
    for name in archive.namelist():
        if name.endswith(".class") and not name.startswith("META-INF/") and "/" in name:
            found.add(name.rsplit("/", 1)[0])
    return found


def module_id(nested: zipfile.ZipFile) -> str | None:
    try:
        return json.loads(nested.read("fabric.mod.json")).get("id")
    except (KeyError, ValueError):
        return None


def main() -> int:
    if len(sys.argv) < 5:
        print(__doc__, file=sys.stderr)
        return 2
    fabric_api, plan_path, game_mod, output = sys.argv[1:5]
    assigned = dict(arg.split("=", 1) for arg in sys.argv[5:])
    with open(plan_path, encoding="utf-8") as f:
        plan = json.load(f)
    native = sorted(m for m, spec in plan["modules"].items() if spec.get("plan") == "native")
    with zipfile.ZipFile(game_mod) as jar:
        ours = {p for p in packages(jar) if p.startswith("net/fabricmc/fabric/")}

    by_module: dict[str, set[str]] = {}
    with zipfile.ZipFile(fabric_api) as umbrella:
        for name in umbrella.namelist():
            if not (name.startswith("META-INF/jars/") and name.endswith(".jar")):
                continue
            with zipfile.ZipFile(io.BytesIO(umbrella.read(name))) as nested:
                mod = module_id(nested)
                if mod in native:
                    by_module[mod] = packages(nested) & ours

    for package, module in assigned.items():
        if module not in by_module or package not in ours:
            print(f"can't assign {package} to {module}", file=sys.stderr)
            return 1
        for packages_of in by_module.values():
            packages_of.discard(package)
        by_module[module].add(package)
    shared = sorted(p for p in ours if sum(p in packages_of for packages_of in by_module.values()) > 1)
    if shared:
        print(f"packages several native modules share, to assign to one: {shared}", file=sys.stderr)
        return 1

    missing = [m for m in native if m not in by_module]
    if missing:
        print(f"not in {fabric_api}: {missing}", file=sys.stderr)
        return 1
    claimed = set().union(*by_module.values())
    unclaimed = sorted(ours - claimed)
    if unclaimed:
        print(f"packages of the in-game mod no native module has: {unclaimed}", file=sys.stderr)
        return 1

    index = {
        "fabricApiVersion": plan["fabricApiVersion"],
        "modules": {m: sorted(by_module[m]) for m in native},
    }
    with open(output, "w", encoding="utf-8") as f:
        json.dump(index, f, indent=2)
        f.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
