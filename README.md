# Fabricated for Minecraft Forge

> **This is the `forge` branch:** Fabricated for **Minecraft Forge 61 to 66** (Minecraft 1.21.11, 26.1.2, 26.2 and
> 26.3), on dedicated servers and clients. From 0.6.0-beta Forge is held to the same bar as NeoForge: no launch stop
> caused by Fabricated and no open P0 or P1 finding (the open ones are in the [roadmap](/docs/roadmap.md#findings)).
> Coverage differs, and what Forge lacks is listed in [`docs/FORGE.md`](/docs/FORGE.md). For NeoForge, see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge on Minecraft 1.20.1, see
> [`forge-legacy`](https://github.com/cmdralph/Fabricated/tree/forge-legacy).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the Forge 61 to
66 platform (`main` holds NeoForge's, `forge-legacy` Forge 47's), with the engine merged in from `main`. It builds and
tests the Forge targets, and its releases' `fabricated-forge-*` jars for Forge 61 to 66 come from it.

## Status

| Minecraft | Forge | Java | Fabric mods use | CI on this branch |
|---|---|---|---|---|
| 1.21.11 | 61.2.4 | 21 | Intermediary names (remapped at load) | Builds; both server runs and both client runs must pass |
| 26.1.2 | 64.1.5 | 25 | Mojang names (no remapping) | Builds; both server runs and both client runs must pass |
| 26.2 | 65.1.5 | 25 | Mojang names (no remapping) | Builds; both server runs and both client runs must pass |
| 26.3 | 66.1.0 | 25 | Mojang names (no remapping) | Builds; both server runs and both client runs must pass |

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

CI then starts a client on each version twice, the same two ways, headlessly, with HeadlessMC and mc-runtime-test.
Both runs are gates too: the client must join a singleplayer world and quit normally, and it checks that:
- the test mod's main entrypoint and then its client entrypoint run in Forge's registry events, with the registries
  open, as Fabric runs them;
- Fabric Loader's game instance is the `Minecraft` instance when the client entrypoints run;
- the item, the content built during the bootstrap (CORPUS-31) and the built-in registries' freeze (CORPUS-32) behave
  as on the server;
- a key mapping the test mod registers from its client entrypoint is in the game's options, with the key the options
  file saves for it (KEYMAP-3), and a world holding its registry entries opens without asking to confirm experimental
  settings (PACK-5).

**With the official Fabric API jar**, the server starts and the client reaches a world on all four versions, and no
Fabric API module is turned off. Some modules load without the mixins that Forge's code can't take; `docs/FORGE.md`
says which, and what Fabricated does in their place. Fabric's use and attack callbacks need no port: a Fabric mod
refusing a player's use or attack of an item, a block or an entity is heard, as CI checks.

**Native on Forge:**
- `fabric-api-base`.
- `fabric-loot-api-v3`: Fabric's loot table events run from Forge's loot loading, or from a hook into the registry
  decode on Forge 66.
- `fabric-key-mapping-api-v1` (`fabric-key-binding-api-v1` on 1.21.11): Fabric mods' key mappings are registered on
  Forge's key mapping event. Forge creates the game's options before it loads mods, which upstream's module refuses
  key mappings after (KEYMAP-3).

**Bridged to Forge's own systems:**
- **Tag files:** their `fabric:remove` entries go into Forge's remove list.
- **Registry data:** a Fabric mod's own pack keeps Fabric's known-pack identity, so its worldgen and damage types are
  stable and a world holding them opens without asking to confirm experimental settings (PACK-5).
- **Load conditions:** Fabric's `fabric:load_conditions` decide whether a data file or a registry entry loads, through
  Forge's own conditional decoding on Forge 61.
- **Biomes:** features Fabric's biome modifications add reach the biomes, in the list type Forge uses.
- **Default attributes:** changes made through `FabricDefaultAttributeRegistry.MODIFY` reach the game, on Forge 64 to 66.
- **Advancements:** Fabric's advancement events run, on Forge 66.
- **Fluids:** Fabric mods' fluids get Forge fluid types. An entity moves through a Fabric fluid that isn't water as
  through air, as on Fabric.
- **Block colours:** on 1.21.11, Fabric's colour provider registry reads Forge's own map of block colours.
- **Block dust:** on 1.21.11, Fabric's block dust tint event is asked where Forge decides whether a block's breaking
  particles are tinted.
- **Commands:** Fabric mods' commands, registered through Fabric API's event, reach the server.
- **Screens:** Fabric's screen drawing events fire from Forge's screen rendering, and its key and mouse events from
  Forge's screen input hooks, where a Fabric mod's refusal holds.
- **HUD:** Fabric HUD elements attached around a vanilla element, or replacing or removing it, are drawn from Forge's
  own HUD layers, which take the place of vanilla's HUD method (HUD-2). A Fabric mod's own hooks at that method's end,
  or at a call there that one of Forge's layers makes, run where Forge's HUD does that work, as Fabric API's
  `HudRenderCallback` does on 1.21.11 (HUD-4).
- **Block breaking:** Fabric's block break events fire where Forge breaks a player's block, and a Fabric mod that
  refuses a break keeps the block.
- **Beds:** Fabric's bed occupation and sleeping direction events run where Forge handles beds, and a block a Fabric
  mod admits as a bed stays that block while an entity sleeps on it.
- **Damage:** Fabric's after-damage event reports the damage an entity was dealt and took, on Forge 61 and 64 too,
  whose code drops what Fabric API's hook reads.
- **Storages:** a Fabric mod's item and fluid storages are the item and fluid handlers of its block entity, which Forge
  mods' hoppers and pipes reach, and a Forge mod's handlers are storages to Fabric mods. A Forge handler moves items
  only once Fabric's transaction commits. Tank items and bags cross both ways too. A fluid carrying data doesn't cross.
- **Fabric's renderer API:** Fabric block models are drawn in the world, as moving blocks, with the breaking overlay
  and as an entity's block, and Fabric item models wherever items are drawn; on 1.21.11 Fabricated sends the three
  that Forge's code moved to Fabric's renderer itself. A Fabric model's level-aware particle sprite shows in the screen
  effect inside its block, in block markers and in block particles, where Forge asks models for theirs with its model
  data. A Fabric model that throws while drawn fails alone: it is drawn from its parts and named once in the log and
  the report (FRAPI rule 8).

## Not supported on Forge

[`docs/FORGE.md`](/docs/FORGE.md) lists what Fabric mods can't rely on here, under *Unsupported on Forge*, with the
task that takes each on. The main ones:

- **Other hooks inside the HUD method:** a mixin there that wraps or redirects one of vanilla's calls, or hooks in
  anywhere but the method's start, its end or a call Forge's layers make, applies but never runs, as Forge draws the
  HUD from its layers (HUD-4).
- **Forgified Fabric API** is refused, as is any jar carrying its own copy of Fabric Loader; task 5 tries letting both
  run.

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
- `loader/forge`: Forge's discovery services, a mod locator and a dependency locator, with the language provider and
  the transformation service in `loader/forge-shared`, which Forge 47 shares.
- `mc/forge/<version>` and `mc/forge/common*`: the in-game mod and the test mod, with the code Forge 47 compiles as it
  is in `mc/forge-shared`.

CI is `.github/workflows/build-forge.yml` with `ci/forge/smoke-server.sh` and `ci/forge/smoke-client.sh`. The project's layout and rules are in
[`CONTRIBUTING.md`](/CONTRIBUTING.md).

## Documentation

- [`docs/PORTING.md`](/docs/PORTING.md) §4 and §7: the Forge port's design and its steps (F1 to F4).
- [`docs/roadmap.md`](/docs/roadmap.md): where the `forge` track stands and what comes next.
- [`docs/FORGE.md`](/docs/FORGE.md): what works on the Forge builds, per version and per Fabric API module.
- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): the same for NeoForge, and the engine's shared behaviour.
- [`README.md`](/README.md): Fabricated's main README (NeoForge).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
