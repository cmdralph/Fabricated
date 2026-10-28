#!/usr/bin/env python3
"""Writes stand-ins for the mods Fabricated shares a game with (PROVIDER-1, PROVIDER-2), for a server smoke run: built
from Fabricated's own jar, so nothing is downloaded and the test mods keep working with them.

- standin-fabric-api-base.jar: a NeoForge mod with Fabric API's fabric-api-base module (its id, its packages, the
  classes of Fabricated's copy), as Forgified Fabric API's module jars are, placed straight in the mods folder.
- standin-forgified-fabric-api.jar: a NeoForge mod nesting, with NeoForge's jar-in-jar, a module that has a Fabricated
  mixin standing down with it (fabric-screen-handler-api-v1, or fabric-menu-api-v1 where it was renamed) and a copy of
  Fabric Loader's API as Forgified Fabric Loader ships it: a plugin library, with a language loader service naming one
  of its classes and a package of its own.
- standin-lib-a.jar and standin-lib-b.jar: Fabric mods nesting two versions of the same library (standin_lib 1.0.0
  and 1.1.0): only the newer one loads.

Each copy of a package is one the game would otherwise load twice; the run passes if the server starts, the report
says who provides what, and the test mods' checks pass.

Usage: ci/neoforge/standins.py <fabricated-jar> <out-dir>
"""
import io
import json
import sys
import zipfile

LOADER_PACKAGES = ("net/fabricmc/loader/", "net/fabricmc/api/")
STAMP = (1980, 2, 1, 0, 0, 0)


def jar(files):
    """A jar of {path: bytes}, deterministic, with a manifest first."""
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in ["META-INF/MANIFEST.MF"] + sorted(p for p in files if p != "META-INF/MANIFEST.MF"):
            if path in files:
                z.writestr(zipfile.ZipInfo(path, STAMP), files[path])
    return out.getvalue()


def manifest(**attributes):
    lines = ["Manifest-Version: 1.0"] + [f"{k.replace('_', '-')}: {v}" for k, v in attributes.items()]
    return ("\r\n".join(lines) + "\r\n\r\n").encode()


def mods_toml(mod_id, version, name):
    return (f'modLoader = "lowcodefml"\nloaderVersion = "[1,)"\nlicense = "GPL-3.0-only"\n[[mods]]\nmodId = "{mod_id}"\n'
            f'version = "{version}"\ndisplayName = "{name}"\n').encode()


def jar_in_jar(nested):
    """NeoForge's jar-in-jar metadata for {path: (artifact, version)}."""
    return json.dumps({"jars": [{"identifier": {"group": "standin", "artifact": artifact},
                                 "version": {"range": f"[{version},)", "artifactVersion": version},
                                 "path": path, "isObfuscated": False} for path, (artifact, version) in nested.items()]}).encode()


def module_classes(game_mod, packages):
    return {name: game_mod.read(name) for name in game_mod.namelist()
            if not name.endswith("/") and name.rsplit("/", 1)[0] in packages}


def module_jar(game_mod, index, plan_versions, module, name):
    files = module_classes(game_mod, set(index[module]))
    if not files:
        raise SystemExit(f"Fabricated's in-game mod has no classes of {module}")
    files["META-INF/MANIFEST.MF"] = manifest(Implementation_Version="1.0.0")
    files["META-INF/neoforge.mods.toml"] = mods_toml(module.replace("-", "_"), plan_versions.get(module, "1.0.0") + "+standin", name)
    return jar(files)


def fabric_mod(mod_id, version, nested=None):
    """A Fabric mod without code; nested is {path: bytes} of jars it nests."""
    meta = {"schemaVersion": 1, "id": mod_id, "version": version, "name": mod_id, "environment": "*"}
    files = {"META-INF/MANIFEST.MF": manifest()}
    if nested:
        meta["jars"] = [{"file": path} for path in nested]
        files.update(nested)
    files["fabric.mod.json"] = json.dumps(meta).encode()
    return jar(files)


def main(fabricated, out):
    with zipfile.ZipFile(fabricated) as outer:
        index = json.loads(outer.read("META-INF/fabricated/native-modules.json"))["modules"]
        plan = json.loads(outer.read("META-INF/fabricated/fabric-api-plan.json"))
        loader_api = {name: outer.read(name) for name in outer.namelist()
                      if name.startswith(LOADER_PACKAGES[0] + "api/") and name.count("/") == 4 and name.endswith(".class")}
        game_mod = zipfile.ZipFile(io.BytesIO(outer.read("META-INF/fabricated/game-mod.jar")))
    plan_versions = {module: entry.get("version", "1.0.0") for module, entry in plan["modules"].items()}

    written = {}
    written["standin-fabric-api-base.jar"] = module_jar(game_mod, index, plan_versions, "fabric-api-base", "Stand-in Fabric API Base")

    menu = "fabric-screen-handler-api-v1" if "fabric-screen-handler-api-v1" in index else "fabric-menu-api-v1"
    loader = dict(loader_api)
    loader["META-INF/MANIFEST.MF"] = manifest(FMLModType="LIBRARY", Automatic_Module_Name="net.fabricmc.loader", Implementation_Version="1.0.0")
    # A language loader whose class goes with the copy: if its service file stayed, FML's service loader would fail.
    loader["META-INF/services/net.neoforged.neoforgespi.language.IModLanguageLoader"] = b"net.fabricmc.loader.impl.standin.StandInLanguageLoader\n"
    loader["standin/loader/README.txt"] = b"A package of the stand-in's own, which stays.\n"
    nested = {"META-INF/jarjar/standin-module.jar": ("module", "1.0.0"), "META-INF/jarjar/standin-loader.jar": ("loader", "1.0.0")}
    written["standin-forgified-fabric-api.jar"] = jar({
        "META-INF/MANIFEST.MF": manifest(Implementation_Version="1.0.0"),
        "META-INF/neoforge.mods.toml": mods_toml("standin_forgified_fabric_api", "1.0.0", "Stand-in Forgified Fabric API"),
        "META-INF/jarjar/metadata.json": jar_in_jar(nested),
        "META-INF/jarjar/standin-module.jar": module_jar(game_mod, index, plan_versions, menu, "Stand-in " + menu),
        "META-INF/jarjar/standin-loader.jar": jar(loader),
    })

    for mod, version in (("a", "1.0.0"), ("b", "1.1.0")):
        library = fabric_mod("standin_lib", version)
        written[f"standin-lib-{mod}.jar"] = fabric_mod(f"standin_lib_user_{mod}", "1.0.0", {f"META-INF/jars/standin-lib-{version}.jar": library})

    for name, data in written.items():
        with open(f"{out}/{name}", "wb") as f:
            f.write(data)
        print(f"{name}: {len(data)} bytes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
