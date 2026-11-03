#!/usr/bin/env python3
"""Writes stand-ins for the mods Fabricated shares a Forge game with (the provider policy, PROVIDER-1), for a server
smoke run on Forge 47: built from Fabricated's own jar, so nothing is downloaded and the test mods keep working with
them. The forge-legacy branch's counterpart of ci/neoforge/standins.py.

- standin-fabric-api-base.jar: a Forge mod with Fabric API's fabric-api-base module (its id, its packages, the classes
  of Fabricated's copy), as Forgified Fabric API's module jars are, placed straight in the mods folder.
- standin-forgified-fabric-api.jar: a Forge mod nesting, with Forge's jar-in-jar, Fabric API's fabric-dimensions-v1 (its
  packages, with its mixin config and the refmap naming its targets in SRG's names, named in the jar's manifest as Forge
  reads them), as Forgified Fabric API for 1.20.1 nests its modules. Fabricated's copy stands down; the stand-in's is
  Fabricated's own, so the test mod's teleport check must still pass, through the stand-in's classes.
- standin-lib-a.jar and standin-lib-b.jar: Fabric mods nesting two versions of the same library (standin_lib 1.0.0
  and 1.1.0): only the newer one loads.
- standin-forgeconfigapiport.jar and standin-modmenu.jar: Fabric mods without code under Forge Config API Port's and
  Mod Menu's ids: on Forge only Mod Menu's is left out, Forge's mod list standing in for it, where NeoForge leaves out
  both (REPL-1, PLAT-6).

Each copy of a package is one the game would otherwise load twice; the run passes if the server starts, the report
says who provides what, and the test mods' checks pass.

With own-loader, it writes only standin-own-loader.jar, for the server run that must refuse it (PROVIDER-2): a Forge
mod nesting, with Forge's jar-in-jar, a language provider with its own copy of Fabric Loader's API (the classes of
Fabricated's), as Forgified Fabric API for 1.20.1 nests Forgified Fabric Loader.

Usage: ci/forge-legacy/standins.py <fabricated-jar> <out-dir> [own-loader]
"""
import io
import json
import sys
import zipfile

STAMP = (1980, 2, 1, 0, 0, 0)
# The module the Forgified Fabric API stand-in nests.
NESTED = "fabric-dimensions-v1"


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
    """Forge's jar-in-jar metadata for {path: (artifact, version)}."""
    return json.dumps({"jars": [{"identifier": {"group": "standin", "artifact": artifact},
                                 "version": {"range": f"[{version},)", "artifactVersion": version},
                                 "path": path, "isObfuscated": False} for path, (artifact, version) in nested.items()]}).encode()


def module_files(game_mod, packages):
    return {name: game_mod.read(name) for name in game_mod.namelist()
            if not name.endswith("/") and "/" in name and name.rsplit("/", 1)[0] in packages}


def module_jar(game_mod, index, plan_versions, module, name, configs=()):
    """A Forge mod with a module's files from the in-game mod, and the mixin configs (with their refmaps) it owns."""
    files = module_files(game_mod, set(index[module]))
    if not files:
        raise SystemExit(f"Fabricated's in-game mod has no classes of {module}")
    attributes = {"Implementation_Version": "1.0.0"}
    names = []
    for config in configs:
        data = json.loads(game_mod.read(config))
        if data.get("package", "").replace(".", "/") not in index[module]:
            raise SystemExit(f"{config} isn't in a package of {module}")
        renamed = "standin-" + config
        refmap = data.get("refmap")
        if refmap:
            files["standin-" + refmap] = game_mod.read(refmap)
            data["refmap"] = "standin-" + refmap
        files[renamed] = json.dumps(data, indent=2).encode()
        names.append(renamed)
    if names:
        attributes["MixinConfigs"] = ",".join(names)
    files["META-INF/MANIFEST.MF"] = manifest(**attributes)
    files["META-INF/mods.toml"] = mods_toml(module.replace("-", "_"), plan_versions.get(module, "1.0.0") + "+standin", name)
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


def own_loader(outer):
    """standin-own-loader.jar (see the module's comment), from Fabricated's jar."""
    api = {name: outer.read(name) for name in outer.namelist()
           if name.startswith(("net/fabricmc/loader/", "net/fabricmc/api/")) and not name.endswith("/")}
    if not api:
        raise SystemExit("Fabricated's jar has no Fabric Loader API")
    loader = jar({"META-INF/MANIFEST.MF": manifest(FMLModType="LANGPROVIDER", Automatic_Module_Name="standin.fabric.loader",
                                                   Implementation_Version="1.0.0"), **api})
    return jar({
        "META-INF/MANIFEST.MF": manifest(Implementation_Version="1.0.0"),
        "META-INF/mods.toml": mods_toml("standin_own_loader", "1.0.0", "Stand-in Forgified Fabric API with its Fabric Loader"),
        "META-INF/jarjar/metadata.json": jar_in_jar({"META-INF/jarjar/standin-fabric-loader.jar": ("fabric-loader", "1.0.0")}),
        "META-INF/jarjar/standin-fabric-loader.jar": loader,
    })


def main(fabricated, out, only=None):
    if only == "own-loader":
        with zipfile.ZipFile(fabricated) as outer:
            data = own_loader(outer)
        with open(f"{out}/standin-own-loader.jar", "wb") as f:
            f.write(data)
        print(f"standin-own-loader.jar: {len(data)} bytes")
        return
    if only is not None:
        raise SystemExit(__doc__)
    with zipfile.ZipFile(fabricated) as outer:
        index = json.loads(outer.read("META-INF/fabricated/native-modules.json"))["modules"]
        plan = json.loads(outer.read("META-INF/fabricated/fabric-api-plan.json"))
        game_mod = zipfile.ZipFile(io.BytesIO(outer.read("META-INF/fabricated/game-mod.jar")))
    plan_versions = {module: entry.get("version", "1.0.0") for module, entry in plan["modules"].items()}
    # The nested module's own mixin configs: those in its packages.
    nested_packages = set(index[NESTED])
    nested_configs = [name for name in game_mod.namelist() if "/" not in name and name.endswith(".mixins.json")
                      and json.loads(game_mod.read(name)).get("package", "").replace(".", "/") in nested_packages]
    if not nested_configs:
        raise SystemExit(f"Fabricated's in-game mod has no mixin config in {NESTED}'s packages")

    written = {}
    written["standin-fabric-api-base.jar"] = module_jar(game_mod, index, plan_versions, "fabric-api-base", "Stand-in Fabric API Base")
    nested = {"META-INF/jarjar/standin-module.jar": ("module", "1.0.0")}
    written["standin-forgified-fabric-api.jar"] = jar({
        "META-INF/MANIFEST.MF": manifest(Implementation_Version="1.0.0"),
        "META-INF/mods.toml": mods_toml("standin_forgified_fabric_api", "1.0.0", "Stand-in Forgified Fabric API"),
        "META-INF/jarjar/metadata.json": jar_in_jar(nested),
        "META-INF/jarjar/standin-module.jar": module_jar(game_mod, index, plan_versions, NESTED, "Stand-in " + NESTED, nested_configs),
    })

    for mod, version in (("a", "1.0.0"), ("b", "1.1.0")):
        library = fabric_mod("standin_lib", version)
        written[f"standin-lib-{mod}.jar"] = fabric_mod(f"standin_lib_user_{mod}", "1.0.0", {f"META-INF/jars/standin-lib-{version}.jar": library})

    written["standin-forgeconfigapiport.jar"] = fabric_mod("forgeconfigapiport", "8.0.0")
    written["standin-modmenu.jar"] = fabric_mod("modmenu", "7.2.2")

    for name, data in written.items():
        with open(f"{out}/{name}", "wb") as f:
            f.write(data)
        print(f"{name}: {len(data)} bytes")


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
