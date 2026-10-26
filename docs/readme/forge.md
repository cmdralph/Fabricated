# Fabricated for Minecraft Forge

> **This is the `forge` branch:** Fabricated for **Minecraft Forge 61 to 66** (Minecraft 1.21.11, 26.1.2, 26.2 and
> 26.3). It is an **experimental preview for dedicated servers**. NeoForge is Fabricated's supported platform: see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main). For Forge on Minecraft 1.20.1, see
> [`forge-legacy`](https://github.com/cmdralph/Fabricated/tree/forge-legacy).

Fabricated loads Fabric mods and libraries next to Forge mods. Every branch holds the same code, with one folder per
platform. This branch builds and tests the Forge targets, and its releases' `fabricated-forge-*` jars for Forge 61 to
66 come from it.

## Status

| Minecraft | Forge | Java | Fabric mods use | CI on this branch |
|---|---|---|---|---|
| 1.21.11 | 61.2.4 | 21 | Intermediary names (remapped at load) | Builds; server runs informational |
| 26.1.2 | 64.1.5 | 25 | Mojang names (no remapping) | Builds; server runs informational |
| 26.2 | 65.1.5 | 25 | Mojang names (no remapping) | Builds; server runs informational |
| 26.3 | 66.1.0 | 25 | Mojang names (no remapping) | Builds; server runs informational |

### What CI checks

CI starts a dedicated server on each version twice: once with the test mod, and once more with the official Fabric
API jar.

On each run, the server checks that:
- a translated Fabric mod is discovered and loads;
- its main and server entrypoints run in Forge's registry events, with the registries open;
- it registers an item that stays in the frozen registry;
- its mixins are applied, its injected interfaces are added, and its other side's members are stripped;
- Forge's loading warnings name a Fabric mod that wasn't loaded;
- the report names Forge as the loader.

**With the official Fabric API jar**, the server starts on all four versions and no Fabric API module is turned off.
Some modules load without the mixins that Forge's code can't take.

**Native on Forge:**
- `fabric-api-base`.
- `fabric-loot-api-v3`: Fabric's loot table events run from Forge's loot loading, or from a hook into the registry
  decode on Forge 66.

**Bridged to Forge's own systems:**
- **Tag files:** their `fabric:remove` entries go into Forge's remove list.
- **Default attributes:** changes made through `FabricDefaultAttributeRegistry.MODIFY` reach the game, on Forge 64 to 66.
- **Advancements:** Fabric's advancement events run, on Forge 66.
- **Fluids:** Fabric mods' fluids get Forge fluid types. An entity moves through a Fabric fluid that isn't water as
  through air, as on Fabric.

## Not supported yet

- **Clients.** Fabric mods' client entrypoints don't run on Forge, and CI starts no Forge client.
- **Native modules.** Fabric API modules that need a native port on Forge's events, such as entity and interaction
  events, pass through from the Fabric API jar, without the hooks Forge's code can't take.
- **Gates.** The server runs are informational: a failing check is reported but doesn't fail CI.

## Installing (for testing)

1. Install Minecraft Forge for your exact Minecraft version, with the official installer.
2. Download `fabricated-forge-<version>+mc<minecraft>.jar` for that version from the
   [releases](https://github.com/cmdralph/Fabricated/releases).
3. Check the jar against the release's `SHA256SUMS.txt`.
4. Put the jar in the `mods` folder with your Fabric mods. If they need Fabric API, add the official Fabric API jar
   for your version.

On 1.21.11, Fabricated remaps Fabric mods from Intermediary names, using the Mojang mappings that Forge's installer
leaves. Fabricated never downloads anything and never modifies your mod jars. `logs/fabric-bridge-report.txt` says
what loaded and why anything didn't.

## Building

```bash
./gradlew -Pfabricated.coreOnly=true build                    # unit tests, no Minecraft needed
./gradlew -Pfabricated.platforms=forge --configure-on-demand :mc:forge-26.2:fabricatedJar :mc:forge-26.2:testmodJar
# -> build/libs/fabricated-forge-<version>+mc26.2.jar
```

The Forge code is in:
- `loader/forge`: Forge's discovery services, which are a mod locator, a dependency locator, a language provider and
  a transformation service.
- `mc/forge/<version>` and `mc/forge/common*`: the in-game mod and the test mod.

CI is `.github/workflows/build-forge.yml` with `ci/forge/smoke-server.sh`. The project's layout and rules are in
[`CONTRIBUTING.md`](/CONTRIBUTING.md).

## Documentation

- [`docs/PORTING.md`](/docs/PORTING.md) §4 and §7: the Forge port's design and its steps (F1 to F4).
- [`docs/roadmap.md`](/docs/roadmap.md): where the `forge` track stands and what comes next.
- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): what works on NeoForge, per version and per Fabric API module.
- [`README.md`](/README.md): Fabricated's main README (NeoForge).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
