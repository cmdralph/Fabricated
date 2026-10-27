# Fabricated for Minecraft Forge

> **This is the `forge` branch:** Fabricated for **Minecraft Forge 61 to 66** (Minecraft 1.21.11, 26.1.2, 26.2 and
> 26.3). It is an **experimental preview**: dedicated servers, and clients that run Fabric mods' client entrypoints.
> NeoForge is Fabricated's supported platform: see [`main`](https://github.com/cmdralph/Fabricated/tree/main). For
> Forge on Minecraft 1.20.1, see [`forge-legacy`](https://github.com/cmdralph/Fabricated/tree/forge-legacy).

Fabricated loads Fabric mods and libraries next to Forge mods. Every branch holds the same code, with one folder per
platform. This branch builds and tests the Forge targets, and its releases' `fabricated-forge-*` jars for Forge 61 to
66 come from it.

## Status

| Minecraft | Forge | Java | Fabric mods use | CI on this branch |
|---|---|---|---|---|
| 1.21.11 | 61.2.4 | 21 | Intermediary names (remapped at load) | Builds; both server runs and the client run must pass |
| 26.1.2 | 64.1.5 | 25 | Mojang names (no remapping) | Builds; both server runs and the client run must pass |
| 26.2 | 65.1.5 | 25 | Mojang names (no remapping) | Builds; both server runs and the client run must pass |
| 26.3 | 66.1.0 | 25 | Mojang names (no remapping) | Builds; both server runs and the client run must pass |

### What CI checks

CI starts a dedicated server on each version twice: once with the test mod, and once more with the official Fabric
API jar. Each run is a gate: the server must start and every check that applies to the run must pass (a few checks
need Fabric API or a 26.x version). Three lines read from Forge's debug log are only reported.

On each run, the server checks that:
- a translated Fabric mod is discovered and loads;
- its main and server entrypoints run in Forge's registry events, with the registries open;
- it registers an item that stays in the frozen registry;
- content it builds during the game's bootstrap, before any mod is constructed, waits for its main entrypoint to
  register it, as on Fabric (CORPUS-31);
- the built-in registries' freeze, which Fabric mods mix into, runs after its main entrypoint, as on Fabric (CORPUS-32);
- two Fabric mods that depend on each other load, with one ordering of their cycle left out (ORDER-1);
- its mixins are applied, its injected interfaces are added, and its other side's members are stripped;
- Forge's loading warnings name a Fabric mod that wasn't loaded;
- an access widener leaves private a field Forge's coremod reads through a getter, and the report says so;
- the report names Forge as the loader.

CI then starts a client on each version with the test mod, headlessly, with HeadlessMC and mc-runtime-test. This run
is a gate too: the client must join a singleplayer world and quit normally, and it checks that:
- the test mod's main entrypoint and then its client entrypoint run in Forge's registry events, with the registries
  open, as Fabric runs them;
- Fabric Loader's game instance is the `Minecraft` instance when the client entrypoints run;
- the item, the content built during the bootstrap (CORPUS-31) and the built-in registries' freeze (CORPUS-32) behave
  as on the server.

**With the official Fabric API jar**, the server starts on all four versions and no Fabric API module is turned off. A
client started with it reaches a world on all four too; that run is only reported for now.
Some modules load without the mixins that Forge's code can't take. Fabric's use and attack callbacks need no port: a
Fabric mod refusing a player's use or attack of an item, a block or an entity is heard, as CI checks.

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
- **Block colours:** on 1.21.11, Fabric's colour provider registry reads Forge's own map of block colours.
- **Block dust:** on 1.21.11, Fabric's block dust tint event is asked where Forge decides whether a block's breaking
  particles are tinted.
- **Commands:** Fabric mods' commands, registered through Fabric API's event, reach the server.
- **Block breaking:** Fabric's block break events fire where Forge breaks a player's block, and a Fabric mod that
  refuses a break keeps the block.
- **Beds:** Fabric's bed occupation and sleeping direction events run where Forge handles beds, and a block a Fabric
  mod admits as a bed stays that block while an entity sleeps on it.
- **Storages:** a Fabric mod's item and fluid storages are the item and fluid handlers of its block entity, which Forge
  mods' hoppers and pipes reach, and a Forge mod's handlers are storages to Fabric mods. A Forge handler moves items
  only once Fabric's transaction commits. Tank items cross both ways too. A fluid carrying data doesn't cross.

## Not supported yet

- **Item storages in items.** A Fabric mod's item storage in an item (a backpack's) and Forge's item handler on a stack
  don't see each other yet; fluids in items do.
- **Native modules.** Fabric API modules that need a native port on Forge's events, such as entity events, pass
  through from the Fabric API jar, without the hooks Forge's code can't take: on Forge 64, the one behind Fabric's
  after-damage event.

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
- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): what works, per version and per Fabric API module, with a table for the Forge builds.
- [`README.md`](/README.md): Fabricated's main README (NeoForge).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
