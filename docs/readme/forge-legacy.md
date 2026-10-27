# Fabricated for Forge 1.20.1

> **This is the `forge-legacy` branch:** Fabricated for **Minecraft Forge 47.4** (Minecraft 1.20.1). It is an
> **experimental preview**: dedicated servers, and clients that run Fabric mods' client entrypoints. NeoForge is
> Fabricated's supported platform: see [`main`](https://github.com/cmdralph/Fabricated/tree/main). For Forge on
> Minecraft 1.21.11 and 26.x, see [`forge`](https://github.com/cmdralph/Fabricated/tree/forge).

Fabricated loads Fabric mods and libraries next to Forge mods. Every branch holds the same code, with one folder per
platform. This branch builds and tests the Forge 1.20.1 target, and its releases' `fabricated-forge-*+mc1.20.1.jar`
comes from it. NeoForge has no 1.20.1 line to support, so this is the only legacy branch.

## Status

| Minecraft | Forge | Java | Fabric mods use | CI on this branch |
|---|---|---|---|---|
| 1.20.1 | 47.4.28 | 17 | Intermediary names, remapped to Mojang's class names and SRG's member names | Builds; both server runs and the client run must pass |

### What CI checks

CI starts a dedicated server twice: once with the test mod, and once more with the official Fabric API 0.92.12 jar.
Each run is a gate: the server must start and every check must pass. Two lines read from Forge's debug log are only
reported.

On each run, the server checks that:
- a translated Fabric mod is discovered, remapped to the names Forge runs the game with, and loads. Those names are
  composed from the files Forge's installer leaves, so nothing is downloaded.
- its main and server entrypoints run in Forge's registry events, with the registries open;
- it registers an item by Intermediary names;
- content it builds during the game's bootstrap, before any mod is constructed, waits for its main entrypoint to
  register it, as on Fabric (CORPUS-31);
- the built-in registries' freeze, which Fabric mods mix into, runs after its main entrypoint, as on Fabric (CORPUS-32);
- two Fabric mods that depend on each other load, with one ordering of their cycle left out (ORDER-1);
- it reads a private field its access widener opens;
- an access widener leaves private a field Forge's coremod reads through a getter, and the report says so;
- its mixins are applied, including one through MixinExtras 0.5.5, which Fabricated ships for Forge 47 because Forge
  47 has none;
- its injected interfaces are added, and its other side's members are stripped.

CI then starts a client with the test mod, headlessly, with HeadlessMC and mc-runtime-test. This run is a gate too: the
client must join a singleplayer world and quit normally, and it checks that:
- the test mod's main entrypoint and then its client entrypoint run in Forge's registry events, with the registries
  open, as Fabric runs them;
- Fabric Loader's game instance is the `Minecraft` instance when the client entrypoints run;
- the item, the content built during the bootstrap (CORPUS-31) and the built-in registries' freeze (CORPUS-32) behave
  as on the server.

**With the official Fabric API jar,** the server starts and every module of Fabric API 0.92.12 loads. A client started
with it reaches a world too; that run is only reported for now.

**Native on Forge 47:**
- `fabric-api-base`.
- `fabric-biome-api-v1`: Fabric's biome changes are applied through Forge's biome modifiers.
- `fabric-loot-api-v2` and `fabric-loot-tables-v1`: Fabric's loot events run on Forge's list of loot pools.
- `fabric-dimensions-v1`: a Fabric teleport goes through Forge's teleporter, with no portal built.

**Bridged to Forge's own systems:**
- **Tag files:** their `fabric:remove` entries are read into Forge's list.
- **Fluids:** Fabric mods' fluids get Forge fluid types. An entity moves through a Fabric fluid that isn't water as
  through air, as on Fabric.
- **Block and item colours:** Fabric's colour provider registry reads Forge's own maps of block and item colours.
- **Hooks into constructors:** Forge 47's Mixin takes a hook into a constructor only at its end, so Fabricated turns a
  Fabric mod's hook at a call there into a wrapper of that call: Fabric mods' commands register, and Fabric's model
  loading runs.

## Not supported yet

- **Fabric's particles module.** With the Fabric API jar, `fabric-particles-v1` is turned off on the client: its
  accessor to the particle providers' map expects vanilla's type, which Forge changed.
- **Capabilities.** Fabric's storages on Forge 47's old capability system are next on the
  [roadmap](/docs/roadmap.md). Fabric's networking needs no port: Fabric API's module carries a Fabric mod's packets
  through Forge 47's network stack, as CI checks.

## Installing (for testing)

1. Install Forge 47.4 for Minecraft 1.20.1, with the official installer.
2. Download `fabricated-forge-<version>+mc1.20.1.jar` from the
   [releases](https://github.com/cmdralph/Fabricated/releases).
3. Check the jar against the release's `SHA256SUMS.txt`.
4. Put the jar in the `mods` folder with your Fabric mods for 1.20.1. If they need Fabric API, add the official
   Fabric API jar for 1.20.1.

Fabricated never downloads anything and never modifies your mod jars. `logs/fabric-bridge-report.txt` says what loaded
and why anything didn't.

## Building

```bash
./gradlew -Pfabricated.coreOnly=true build                    # unit tests, no Minecraft needed
./gradlew -Pfabricated.platforms=forge-legacy --configure-on-demand :mc:forge-legacy-1.20.1:fabricatedJar
# -> build/libs/fabricated-forge-<version>+mc1.20.1.jar
```

The Forge 47 code is in:
- `loader/forge-legacy`: Forge 47's discovery services.
- `mc/forge-legacy/1.20.1`: the in-game mod, reobfuscated to SRG's names, and the test mod in Intermediary's names.

The build uses ModDevGradle's `legacyforge` plugin (`mc/platform-forge-legacy.gradle`). CI is
`.github/workflows/build-forge-legacy.yml` with `ci/forge-legacy/smoke-server.sh`. The project's layout and rules are in
[`CONTRIBUTING.md`](/CONTRIBUTING.md).

## Documentation

- [`docs/PORTING.md`](/docs/PORTING.md) §3 and §7: the 1.20.1 port's design and its steps (L1 to L4).
- [`docs/roadmap.md`](/docs/roadmap.md): where the `forge-legacy` track stands and what comes next.
- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): what works, per version and per Fabric API module, with a table for the Forge builds.
- [`README.md`](/README.md): Fabricated's main README (NeoForge).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
