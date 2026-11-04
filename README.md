# Fabricated for Forge 1.20.1

> **This is the `forge-legacy` branch:** Fabricated for **Minecraft Forge 47.4** (Minecraft 1.20.1), on dedicated
> servers and clients. From 0.6.0-beta Forge is held to the same bar as NeoForge: no launch stop caused by Fabricated
> and no open P0 or P1 finding (the open ones are in the [roadmap](/docs/roadmap.md#findings)). Coverage differs, and
> what Forge lacks is listed in [`docs/FORGE-LEGACY.md`](/docs/FORGE-LEGACY.md). For NeoForge, see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge on Minecraft 1.21.11 and 26.x, see
> [`forge`](https://github.com/cmdralph/Fabricated/tree/forge).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the Forge 47
platform (`main` holds NeoForge's, `forge` Forge 61 to 66's), with the engine merged in from `main`. It builds and
tests the Forge 1.20.1 target, and its releases' `fabricated-forge-*+mc1.20.1.jar` comes from it. NeoForge has no 1.20.1 line to support, so this is the only legacy branch.

## Status

| Minecraft | Forge | Java | Fabric mods use | CI on this branch |
|---|---|---|---|---|
| 1.20.1 | 47.4.28 | 17 | Intermediary names, remapped to Mojang's class names and SRG's member names | Builds; both server runs and both client runs must pass |

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

CI then starts a client twice, the same two ways, headlessly, with HeadlessMC and mc-runtime-test. Both runs are gates
too: the client must join a singleplayer world and quit normally, and they check that:
- the test mod's main entrypoint and then its client entrypoint run in Forge's registry events, with the registries
  open, as Fabric runs them;
- Fabric Loader's game instance is the `Minecraft` instance when the client entrypoints run;
- the item, the content built during the bootstrap (CORPUS-31) and the built-in registries' freeze (CORPUS-32) behave
  as on the server;
- the particle factory it registers from its client entrypoint is found by the particle engine, and its sprite is in
  the particle atlas (PART-1).

**With the official Fabric API jar,** the server starts and every module of Fabric API 0.92.12 loads, and so does the
client, which reaches a world. Some modules load without the mixins that Forge's code can't take;
`docs/FORGE-LEGACY.md` says which, and what Fabricated does in their place. Fabric's networking needs no port: Fabric
API's module carries a Fabric mod's packets through Forge 47's network stack, as CI checks. Nor do Fabric's use and
attack callbacks: a Fabric mod refusing a player's use or attack of an item, a block or an entity is heard, as CI
checks.

**Native on Forge 47:**
- `fabric-api-base`.
- `fabric-biome-api-v1`: Fabric's biome changes are applied through Forge's biome modifiers.
- `fabric-loot-api-v2` and `fabric-loot-tables-v1`: Fabric's loot events run on Forge's list of loot pools.
- `fabric-dimensions-v1`: a Fabric teleport goes through Forge's teleporter, with no portal built.
- `fabric-particles-v1`: Fabric's particle factories and their sprites go into Forge's particle engine, which keeps
  them by the particle type's id.

**Bridged to Forge's own systems:**
- **Tag files:** their `fabric:remove` entries are read into Forge's list.
- **Fluids:** Fabric mods' fluids get Forge fluid types. An entity moves through a Fabric fluid that isn't water as
  through air, as on Fabric.
- **Block and item colours:** Fabric's colour provider registry reads Forge's own maps of block and item colours.
- **Hooks into constructors:** Forge 47's Mixin takes a hook into a constructor only at its end, so Fabricated turns a
  Fabric mod's hook at a call there into a wrapper of that call: Fabric mods' commands register, and Fabric's model
  loading runs.
- **Block breaking:** Fabric's block break events fire where Forge breaks a player's block, and a Fabric mod that
  refuses a break keeps the block.
- **Beds:** Fabric's bed occupation and sleeping direction events run where Forge handles beds, and a block a Fabric
  mod admits as a bed stays that block while an entity sleeps on it and as it wakes up. Fabric's sleep time event
  decides, where Forge asks its own, whether a player may sleep at that hour.
- **Storages:** a Fabric mod's item and fluid storages are the item and fluid handlers of its block entity, which Forge
  mods' hoppers and pipes reach, and a Forge mod's handlers are storages to Fabric mods. A Forge handler moves items
  only once Fabric's transaction commits. Tank items cross both ways too: a Forge mod's machine fills a Fabric mod's,
  and a Fabric mod's machine a Forge mod's.
- **Fuels and brewing:** an item Fabric's fuel registry makes a fuel burns in Forge's furnace (FUEL-1), and Fabric's
  brewing recipes are built the way Forge builds vanilla's, where upstream's call failed the mod registering one
  (BREW-1).
- **Screens:** a click and a key press on a screen, and their releases, ask Fabric's screen input events, through
  Forge's patched input code.
- **HUD:** `HudRenderCallback`, 1.20.1's HUD API, fires where Forge's GUI ends its HUD drawing, which takes the place
  of vanilla's HUD method (HUD-2).
- **Model baking:** a model loading plugin's before-bake and after-bake modifiers see the models baked, in the bake
  method Forge 47 added.
- **Fabric's renderer API:** Fabric block models are drawn in the world, as falling blocks, as blocks a piston moves
  and with the breaking overlay, and Fabric item models wherever items are drawn. Upstream's hooks miss Forge 47's
  chunk section compiler and block renderers, so Fabricated has Fabric's renderer draw those blocks itself. A Fabric
  model that throws while drawn fails alone: Forge's renderer draws it from the quads it gives, and it is named once in
  the log and the report (FRAPI rule 8). Blocks drawn as entities keep their vanilla quads, as on Fabric for 1.20.1.

## Not supported on Forge

[`docs/FORGE-LEGACY.md`](/docs/FORGE-LEGACY.md) lists what Fabric mods can't rely on here, under *Unsupported on
Forge*, with the task that takes each on. The main ones:

- **Item storages in items** (task 5): a Fabric mod's item storage in an item (a backpack's) and Forge's item handler on
  a stack don't see each other; fluids in items do.
- **Fabric mods' own HUD mixins** (task 5): a mixin into vanilla's HUD method applies but never runs, as Forge's GUI
  draws the HUD without it; Fabric API's `HudRenderCallback` is bridged (above).
- **Injectors in interface mixins:** Forge 47's Mixin (0.8.5) applies none. Fabricated applies an `@Inject` at `HEAD`,
  `RETURN` or `TAIL` there itself (INTERFACE-1), so `TooltipComponentCallback` and the oxidizable blocks registry work;
  a Fabric item's stack-aware recipe remainder, whose injectors are others, is lost.
- **Forgified Fabric API** is refused, as is any jar carrying its own copy of Fabric Loader; task 5 tries letting both
  run.

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
- `loader/forge-legacy`: Forge 47's discovery services, with the language provider and the transformation service in
  `loader/forge-shared`, which Forge 61 to 66 share.
- `mc/forge-legacy/1.20.1`: the in-game mod, reobfuscated to SRG's names, and the test mod in Intermediary's names,
  with the code Forge 61 to 66 compile as it is in `mc/forge-shared`.

The build uses ModDevGradle's `legacyforge` plugin (`mc/platform-forge-legacy.gradle`). CI is
`.github/workflows/build-forge-legacy.yml` with `ci/forge-legacy/smoke-server.sh` and `ci/forge-legacy/smoke-client.sh`. The project's layout and rules are in
[`CONTRIBUTING.md`](/CONTRIBUTING.md).

## Documentation

- [`docs/PORTING.md`](/docs/PORTING.md) §3 and §7: the 1.20.1 port's design and its steps (L1 to L4).
- [`docs/roadmap.md`](/docs/roadmap.md): where the `forge-legacy` track stands and what comes next.
- [`docs/FORGE-LEGACY.md`](/docs/FORGE-LEGACY.md): what works on the Forge builds, per version and per Fabric API module.
- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): the same for NeoForge, and the engine's shared behaviour.
- [`README.md`](/README.md): Fabricated's main README (NeoForge).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
