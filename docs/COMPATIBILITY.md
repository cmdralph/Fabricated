# Compatibility

What Fabricated supports, on every loader and Minecraft version. The support matrix below is the source of truth;
the sections after it give the detail per platform, from CI runs on real dedicated servers and clients. A cell that
isn't supported says why, in the notes under the matrix.

## Support matrix

✅ supported, checked by a CI gate · ◐ supported with a gap (note) · ❌ unsupported (note gives the reason) ·
⚪ supported, not yet checked in CI · ❔ not checked in CI, and whether it works is unknown (the note says what is
missing) · — doesn't exist there

| | NeoForge 1.21.1 | NeoForge 1.21.11 | NeoForge 26.1.2 | NeoForge 26.2 | NeoForge 26.3 | Forge 1.20.1 | Forge 1.21.1 | Forge 1.21.11 | Forge 26.1.2 | Forge 26.2 | Forge 26.3 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Loader build CI pins | 21.1.265 | 21.11.45 | 26.1.2.116 | 26.2.0.90 | 26.3.0.95 | 47.4.28 | 52.1.21 | 61.2.4 | 64.1.5 | 65.1.5 | 66.1.0 |
| Fabric API CI pins | 0.116.17 | 0.141.6 | 0.155.3 | 0.161.0 | 0.164.0 | 0.92.12 | 0.116.17 | 0.141.6 | 0.155.3 | 0.161.0 | 0.164.0 |
| Loader versions the jar requires (its `mods.toml`) | `[21.1,21.2)` | `[21.11,21.12)` | `[26.1.2,26.1.3)` | `[26.2,26.3)` | `[26.3,26.4)` | `[47,)` | `[52,)` | `[61,)` | `[64,)` | `[65,)` | `[66,)` |
| Minecraft versions the jar requires | `[1.21.1,1.21.2)` | `[1.21.11,1.21.12)` | `[26.1,26.2)` | `[26.2,26.3)` | `[26.3,26.4)` | `[1.20.1,1.20.2)` | `[1.21.1,1.21.2)` | `[1.21.11,1.21.12)` | `[26.1,26.2)` | `[26.2,26.3)` | `[26.3,26.4)` |
| Fabric mods' names | Intermediary, remapped | Intermediary, remapped | Mojang | Mojang | Mojang | Intermediary, remapped to SRG | Intermediary, remapped | Intermediary, remapped | Mojang | Mojang | Mojang |
| **Loading** | | | | | | | | | | | |
| Discovery, translation, the cache, the report | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `main`, `client`, `server` and custom entrypoints | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `preLaunch` | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 | ◐ 1 |
| Fabric Loader's public API | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Dependencies, nested jars, packages shared by mods | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mixins: pre-flight, its repairs, MixinExtras | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Access wideners, class tweakers, injected interfaces, `@Environment` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `config/fabricated.json` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| The `/fabricated` command | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Refusals with an error naming the jars (Sinytra Connector, two Fabricated jars) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Fabric API** | | | | | | | | | | | |
| Every module of the official jar loads, server and client | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 | ✅ 2 |
| Networking: play and configuration payloads | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Networking: login queries | ❌ 3 | ❌ 3 | ❌ 3 | ❌ 3 | ❌ 3 | ❔ 3 | ❔ 3 | ❔ 3 | ❔ 3 | ❔ 3 | ❔ 3 |
| Registries, registry sync, resource and data packs, load conditions | ✅ | ✅ | ✅ | ✅ | ✅ | ◐ 4 | ◐ 4 | ◐ 4 | ◐ 4 | ◐ 4 | ◐ 4 |
| Item, block, entity, interaction and lifecycle events | ✅ | ✅ | ✅ | ✅ | ✅ | ◐ 4 | ◐ 4 | ◐ 4 | ◐ 4 | ◐ 4 | ◐ 4 |
| Fabric fluids as the platform's fluid types | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Biome modifications: features, spawns, climate | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Transfer API and the platform's item and fluid handlers, on blocks and in items | ✅ | ✅ | ✅ | ✅ | ✅ | ◐ 5 | ✅ | ✅ | ✅ | ✅ | ✅ |
| Team Reborn Energy and the platform's energy | ⚪ 6 | ⚪ 6 | ⚪ 6 | ⚪ 6 | ⚪ 6 | ❌ 6 | ❌ 6 | ❌ 6 | ❌ 6 | ❌ 6 | ❌ 6 |
| Client modules: key mappings, HUD, screens, client commands | ✅ | ◐ 7 | ✅ | ✅ | ✅ | ◐ 7 | ◐ 7 | ◐ 7 | ✅ | ✅ | ✅ |
| Fabric mods' hooks inside the HUD method | ✅ | ✅ | ✅ | ✅ | ✅ | ◐ 8 | ✅ | ◐ 8 | ◐ 8 | ◐ 8 | ◐ 8 |
| Fabric mods' layers added to vanilla's HUD layer stacks in `Gui`'s constructor | ✅ 20 | — | — | — | — | — | ✅ | — | — | — | — |
| Mod Menu config screens in the platform's mod list | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fabric mods' plugins for JEI, REI, Jade, WTHIT and Simple Voice Chat | ✅ | ✅ | ✅ | ✅ | ✅ 9 | ✅ | ✅ 17 | ✅ 17 | ✅ 17 | ✅ 17 | ✅ 17 |
| Platform mods' properties as Fabric custom values, and the reverse | ✅ | ✅ | ✅ | ✅ | ✅ | ⚪ | ⚪ | ⚪ | ⚪ | ⚪ | ⚪ |
| **Fabric's renderer API and models** | | | | | | | | | | | |
| Model loading: plugins, modifiers, Fabric model formats | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fabric block models in chunk sections, and nested in vanilla models | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Moving blocks and the breaking overlay | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fabric item models | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Blocks drawn as entities (a minecart's block) | ◐ 10 | ✅ | ✅ | ✅ | ✅ | ◐ 10 | ⚪ | ✅ | ✅ | ✅ | ✅ |
| A model that throws is drawn by the platform from then on | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Level-aware particle sprites | ⚪ | ⚪ | ◐ 11 | ◐ 11 | ◐ 11 | ⚪ | ⚪ | ✅ | ✅ | ✅ | ✅ |
| Another registered renderer (Sodium's, Forgified Fabric API's) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Other mods** | | | | | | | | | | | |
| Forgified Fabric API beside Fabricated | ✅ 12 | — | ✅ 12 | ✅ 12 | — | ✅ 12 | ❌ 13 | ❌ 13 | ❌ 13 | ❌ 13 | ❌ 13 |
| Sodium and Iris for the platform | ✅ | ✅ | ✅ | ✅ | ✅ | — 14 | — 14 | — 14 | — 14 | — 14 | — 14 |
| The Fabric builds of Sodium and Iris, with a shader pack | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 19 | ✅ 19 | ✅ 19 | ✅ 19 | ✅ 19 | ✅ 19 |
| Essential's Fabric build | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| malilib, MiniHUD, Tweakeroo, Litematica | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Flashback | ✅ | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| OneConfig and PolyPlus | ✅ | ✅ | ✅ | ✅ | ✅ | ❔ 18 | ❔ 18 | ❔ 18 | ❔ 18 | ❔ 18 | ❔ 18 |
| Continuity (connected textures through the renderer API) | ✅ | ✅ | ✅ | ✅ | ✅ | ❔ 18 | ❔ 18 | ❔ 18 | ❔ 18 | ❔ 18 | ❔ 18 |
| Popular Fabric mods (the corpora) | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 | ◐ 15 |
| **Tests** | | | | | | | | | | | |
| One test-mod suite for every loader with this Minecraft version | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 16 | ✅ 16 | ✅ 16 | ✅ 16 | ✅ 16 | ✅ 16 |

Notes:

1. `preLaunch` runs right before `main`, not before the game's classes load as on Fabric: running it earlier would load
   mods' classes before the platform builds their containers. Mods doing class loader work there are unsupported.
2. Fabric's game tests (`fabric-gametest-api-v1`, `client-gametest`) are development tools and aren't loaded. Data
   generation's classes load, and it generates nothing unless `fabric-api.datagen` is set. A module's hooks that the
   platform's code can't take are left out and named in the report (*Mixins NeoForge's code can't take* below, and
   *Unsupported on Forge*).
3. NeoForge has no hook in the login phase, and its configuration phase can't stand in for it: the receivers of
   `ServerLoginNetworking` and `ClientLoginNetworking` are never called. On Forge, upstream's module loads, but no CI
   run sends a login query, so whether its receivers are called there is unknown.
4. The hooks listed under *Unsupported on Forge* (item API, content registries, lifecycle, object builder, registry
   sync, fluid rendering, sound).
5. Blocks and fluids in items cross to Forge's handlers; Fabric API for 1.20.1 has no lookup for item storages in
   items, so there is nothing to cross.
6. Fabricated bridges Team Reborn's energy library, when a Fabric mod brings it, to NeoForge's energy capability on
   blocks and items, both ways; CI found no build of the library for these versions to check it with. On Forge it has
   no energy bridge (`ci/checks.tsv`).
7. NeoForge 1.21.11's test mod compiles against stand-ins without these modules, so it doesn't check them; on Forge
   1.20.1, 1.21.1 and 1.21.11 the checks cover the HUD callback, key bindings and screen input only.
8. A Fabric mod's mixin inside vanilla's HUD method that wraps or redirects one of its calls applies but never runs:
   Forge's HUD layers skip that code (on Forge 1.20.1 only hooks at the method's start and end run).
9. WTHIT has no build for 26.3; the other plugin hosts are checked.
10. A minecart's block comes from vanilla's quads, as upstream draws it on these versions, so a model with only Fabric
    quads is invisible there, as on Fabric.
11. Seen from inside a block, the screen effect takes the model's plain particle sprite, not Fabric's level-aware one
    (PART-5).
12. Forgified Fabric API's modules are used as they are, and Fabricated's copies of them stand down. What its modules do
    differently is theirs (*Mods that bring Fabric API or Fabric Loader*). It has no build for NeoForge 1.21.11 or 26.3.
13. Forgified Fabric API's builds for these versions are NeoForge's, and a jar carrying its own copy of Fabric Loader
    is refused.
14. Sodium and Iris have no Forge builds for these versions.
15. CI runs two corpora of popular Fabric mods, the main one and an advanced one of mods with deep hooks, on a server
    and a client of every version, as informational runs (`ci/runs.tsv`). On NeoForge every corpus server starts, and
    every client reaches its world but where a mod's own behaviour holds it (CORPUS-66). On Forge the stops the
    advanced corpus found are fixed, some checked on local runs only (`roadmap.md`, *Compatibility baseline*), and one
    mod, Wover Story, fails on Forge 66 (REG-13, a known limit). Per-mod results are in *Fabric mods tested*.
16. The shared suite runs, with Forge's half of it; each shared check Forge doesn't pass yet is listed in `ci/checks.tsv`
    with its reason, and is a cell of this matrix.
17. Modrinth has Forge builds of WTHIT and Simple Voice Chat for 1.21.11 and 26.x, and of JEI, WTHIT and Simple Voice
    Chat for 1.21.1, which CI checks; the other hosts have none there, so their plugins are converted the same way but
    unchecked (on Forge 47 CI checks all five).
18. CI doesn't run OneConfig and PolyPlus, or Continuity, on Forge (`ci/runs.tsv`), so whether they work there is
    unknown.
19. On Forge CI runs the Fabric builds of Sodium and Iris without a shader pack (Sodium 0.5 with Indium on 1.20.1): the
    run with CI's shader pack is NeoForge's alone (`ci/runs.tsv`).
20. NeoForge builds the HUD from layers of its own: a layer a Fabric mod adds to one of vanilla's stacks there (as Apoli
    adds its resource bars) is a layer of NeoForge's, drawn where vanilla draws it among vanilla's layers and under its
    stack's condition (the GUI shown), and NeoForge mods' layers order against NeoForge's as before. A vanilla layer a
    mod takes out of a stack there, or replaces, isn't drawn: NeoForge's layers for it stay, under their names, drawing
    nothing, and a replacement is drawn where it was. One a mod moves stays where NeoForge draws it. Vanilla has had no
    such stacks since 1.21.6, which the other versions are past (their mods add HUD elements through Fabric API, above),
    and 1.20.1 has none yet.

## How CI tests it

Every loader and Minecraft version runs the same harness (`ci/smoke-server.sh`, `ci/smoke-client.sh`, with the
platform's install and launch in `ci/<platform>/platform.sh`), on real dedicated servers and clients, from
`.github/workflows/ci.yml`. The runs are `ci/runs.tsv` and their checks `ci/checks.tsv`: each check's row says where it
gates and, where it doesn't apply, why. On NeoForge these three runs are the base of the tables below:

1. **Test mods only**: Fabricated plus the Fabric test mods in `mc/shared` (with their NeoForge half in
   `mc/neoforge`). This is a pass/fail gate.
2. **With the official Fabric API jar** added: the server must start (a gate), and the energy bridge check must
   pass where CI finds Team Reborn's energy library for the version; the other checks are informational. The
   resulting `fabric-bridge-report.txt` is kept as a CI artifact
   (`smoke-fabric-api-<version>`), and the tables below are taken from it.
3. **Client**: a real NeoForge client, started headlessly, joins a singleplayer world with Fabricated and the test
   mod (client entrypoint, client lifecycle, a client resource listener, a networking round trip with the
   integrated server). This is a pass/fail gate.

The module tables were first taken from CI run [38491176425](https://github.com/cmdralph/Fabricated/actions/runs/38491176425)
(Fabricated 0.1.0 development build) and have been edited since; a newer run's `smoke-fabric-api-<version>` report
is the measured state. Forge's tables are in [*Forge*](#forge), below.

## NeoForge: Minecraft versions

| Minecraft | NeoForge | Fabric API tested | Test mods | Server boots with Fabric API |
|---|---|---|---|---|
| 1.21.1 | 21.1.265 | 0.116.17+1.21.1 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 1.21.11 | 21.11.45 | 0.141.6+1.21.11 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 26.1.2 | 26.1.2.116 | 0.155.3+26.1.2 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 26.2 | 26.2.0.90 | 0.161.0+26.2 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 26.3 | 26.3.0.95 | 0.164.0+26.3 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |

"Boots with Fabric API" means the server starts with the full official Fabric API jar installed. Modules
that can't work on NeoForge are disabled before any code runs (see below); one that can do without a mixin NeoForge's
code can't take loads without it (LEAVE-1); the rest load alongside Fabricated's test mods.

The server checks: the server starts; a `main` entrypoint runs; the Fabric Loader API works; a custom
entrypoint resolves; content registers directly into vanilla registries; `fabric-api-base` events fire; an
access widener applies; a `server` entrypoint runs; a mixin applies; a mod with a missing dependency is
reported, naming the dependency; a mod with an impossible mixin is caught by pre-flight and loads without it; a mod whose
entrypoint throws is isolated while the server keeps running; Fabricated's public API answers and its
after-initializers callback runs; a client-only interface listed in `@EnvironmentInterface` is stripped on the
server; and no test mod check reports a failure. The others cover the native Fabric API modules (described in
their sections below) and fixes the roadmap lists, each named in `ci/checks.tsv` by its finding. The client checks: the client starts and joins a singleplayer world, the `main` and
`client` entrypoints run, the client-side native modules work, including a networking round trip and a
configuration task with the integrated server, an extended menu opens with its data, a Mod Menu config screen
factory is registered with NeoForge, and no test mod check reports a failure.

CI also starts the client with the full Fabric API jar, a gate since v0.6.0-beta: it joins its world and passes every
client check on every version. The checks CI then makes in its log are gates too: Fabric's renderer API, model formats
and client modules on 26.x, model loading and the renderer API on 1.21.11, the renderer API and client modules on
1.21.1. On 26.x every Fabric API module loads on the client, the renderer API, Indigo and model loading included, and on
1.21.11 too. That run checks on 1.21.11 that a model file of a Fabric type is read by its Fabric deserializer and that
Fabric block models are drawn in chunk sections. On the dedicated server no Fabric API module is disabled.

NeoForge's 26.3 line left beta (its `26.3.0-stable` tag), so its job is a gate like the others; until then it was
marked experimental, and passed every check on `26.3.0.69-beta`. Its client opens its window with SDL3 over EGL, so CI installs Mesa's EGL library for it.

## NeoForge: Fabric API modules

How to read the table:

- **native**: Fabricated provides the module itself.
- **loads**: the module from the official jar passes mixin pre-flight and loads.
- **disabled**: the module can't load on NeoForge yet (reason below), and mods that require it are
  disabled with a message.
- **client**: client-only module, so not loaded on a dedicated server; its client status is in the rows below.
- **unsupported**: deliberately not loaded.
- **—**: the module doesn't exist in that Fabric API version.

| Module | 1.21.1 | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|---|---|
| fabric-api-base | native | native | native | native | native |
| fabric-crash-report-info-v1 | native | native | native | native | native |
| fabric-biome-api-v1 | native | native (see below) | native | native | native |
| fabric-block-getter-api-v2 / block-view-api-v2 | loads | loads | loads | loads | loads |
| fabric-message-api-v1 | loads | loads | loads | loads | loads |
| fabric-rendering-fluids-v1 | loads | loads | loads | loads | loads |
| fabric-serialization-api-v1 | — | loads | loads | loads | loads |
| fabric-transitive-access-wideners-v1 | loads | loads | loads | loads | loads |
| fabric-game-rule-api-v1 | loads | loads | loads | loads | loads |
| fabric-permission-api-v1 | — | — | loads | loads | loads |
| fabric-lifecycle-events-v1 | native | native | native | native | native |
| fabric-networking-api-v1 | native | native | native | native | native |
| fabric-registry-sync-v0 | native | native | native | native | native |
| fabric-resource-loader-v1 | — | native | native | native | native |
| fabric-resource-loader-v0 | native | loads | loads | loads | loads |
| fabric-item-api-v1 | native | native | native | native | native |
| fabric-block-api-v1 | native | native | native | native | native |
| fabric-transfer-api-v1 | native | native | native | native | native |
| fabric-content-registries-v0 | native | native | native | native | native |
| fabric-object-builder-api-v1 | native | native | native | native | native |
| fabric-entity-events-v1 | native | native | native | native | native |
| fabric-events-interaction-v0 | native | native | native | native | native |
| fabric-dimensions-v1 | loads | loads | loads | loads | loads |
| fabric-loot-api-v3 | native (v2 loads too) | native (v2 loads too) | native | native | native |
| fabric-resource-conditions-api-v1 | loads | loads | loads | loads | loads |
| fabric-tag-api-v1 | loads | loads | loads | loads | loads |
| fabric-advancement-api-v1 | — | — | — | loads | loads |
| fabric-menu-api-v1 / screen-handler-api-v1 | native | native | native | native | native |
| fabric-api-lookup-api-v1 | native | native | native | native | native |
| fabric-command-api-v2 | loads | loads | loads | loads | loads |
| fabric-convention-tags-v2 (and v1) | loads | loads | loads | loads | loads |
| fabric-data-attachment-api-v1 | loads | loads | loads | loads | loads |
| fabric-particles-v1 | native | loads | loads | loads | loads |
| fabric-recipe-api-v1 | loads (see below) | loads | loads | loads | loads |
| fabric-creative-tab-api-v1 / item-group-api-v1 | loads | loads | loads | loads | loads |
| fabric-debug-api-v1 | — | — | loads | loads | loads |
| fabric-key-binding-api-v1 | native (see below) | client | — | — | — |
| fabric-key-mapping-api-v1 / key-binding-api-v1, screen-api-v1, rendering-v1, sound-api-v1, model-loading-api-v1 | client | client | client | client | client |
| fabric-rendering-v1 on the client | loads (see below) | loads (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-screen-api-v1, sound-api-v1 on the client | loads (see below) | loads (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-model-loading-api-v1 on the client | loads (see below) | loads (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-renderer-api-v1, fabric-renderer-indigo | client (see below) | client (see below) | client (see below) | client (see below) | client (see below) |
| fabric-data-generation-api-v1 (its classes; it generates nothing unless `fabric-api.datagen` is set; its tag appender mixins, and on 26.3 its loot sub-provider mixins, are left out) | loads | loads | loads | loads | loads |
| fabric-gametest-api-v1, client-gametest | unsupported | unsupported | unsupported | unsupported | unsupported |
| 0.116's deprecated modules: fabric-command-api-v1, fabric-commands-v0, fabric-rendering-data-attachment-v1 | loads | — | — | — | — |
| 0.116's client-only modules: fabric-client-tags-api-v1 and the deprecated fabric-blockrenderlayer-v1, fabric-keybindings-v0, fabric-renderer-registries-v1, fabric-rendering-v0 | client; load on the client | — | — | — | — |

### Minecraft 1.21.1

1.21.1 runs Fabric API 0.116.17 on NeoForge 21.1, whose FML still runs on ModLauncher (Fabricated's `modlauncher`
loader variant). Fabric mods use Intermediary names, remapped at load as on 1.21.11. The 19 native modules are the
ones that exist in 0.116 (`fabric-resource-loader-v0` stands for v1, which 0.116 doesn't have),
`fabric-particles-v1` and `fabric-key-binding-api-v1`, with these differences from later versions:

- **Storages and energy**: NeoForge 21.1 has the item, fluid and energy handler capabilities of before its transfer
  API, so the bridge is the Forge one, ported (`ItemHandler`, `FluidHandler` and `EnergyStorage` capabilities, both
  ways on blocks and in items: a NeoForge mod's code puts items in a Fabric mod's bag through `ItemHandler.ITEM`, and
  a Fabric mod's code in a NeoForge mod's through `ItemStorage.ITEM`). Fluid data components cross. NeoForge's item
  handler of a stack can't hand back another stack, as a fluid handler's container can, so a move a Fabric storage in
  an item would make by leaving another item, or a different count of it, in the item's place doesn't happen through
  it: NeoForge's caller, holding the item, would lose what moved.
- **Fuel**: `FuelRegistry.INSTANCE` (0.116 has no fuel events) answers through NeoForge's furnace fuel data map and
  its burn time event; tags first, then items, and vanilla's non-flammable wood exclusion applies.
- **Biomes**: NeoForge 21.1's biome info holds the special effects, so its own check sends a biome a Fabric
  modification changed to clients.
- **Client entrypoints**: NeoForge 21.1 loads mods inside `Minecraft`'s constructor, so Fabric's main and client
  entrypoints run right after it has (REG-7), where later versions run them at the game thread.
- **HUD layers added in `Gui`'s constructor**: vanilla builds its two HUD layer stacks there and Fabric mods add their
  layers to one at the constructor's read of its root stack (Apoli's resource bars); NeoForge 21.1 builds each stack as
  a layer manager of its own and never makes that read. Pre-flight points such a hook at Fabricated's seam for the
  read, which holds vanilla's stacks as the constructor does there, and each layer the hook adds becomes one of
  NeoForge's, drawn where vanilla draws it under its stack's condition (HUD-7, note 20 above). A hook elsewhere in the
  constructor that captures the stacks stays left out, with the report's reason. Where NeoForge's constructor doesn't
  add its stacks as Fabricated knows them (another 21.1 build), the hooks still run, and the report names the layers
  they added, which aren't drawn then.
- **Particles** (native): NeoForge 21.1 keys the particle engine's providers by id, as Forge 47 does, so Fabricated
  ships the module (PART-3): factories and their sprite sets are registered through the engine's own methods, and
  the block dust tint event is asked where NeoForge decides the tint.
- **Colours and shaders**: NeoForge 21.1 keeps block and item colours in maps, so Fabric's colour lookups read them
  (COLOR-2), and it resolves the namespace of a core shader program, its stages and imports itself, so upstream's
  two shader hooks are left out (SHADER-2).
- **Key bindings** (native): Fabric API's key binding module refuses a key binding once the game's options exist, which
  on Fabric is after the client entrypoints; NeoForge 21.1 creates them before it loads mods. Fabricated ships the
  module (KEYMAP-2): upstream's API, its key bindings kept until NeoForge's `RegisterKeyMappingsEvent` and registered
  there, each with the key the player saved. As upstream, a key binding is refused twice and once the options exist,
  and its category joins the controls screen's order after the game's. `fabric-keybindings-v0` passes through and
  calls it.
- **Fabric API's internal pack class**: mods that tell Fabric mods' packs apart by
  `net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack` (Moonlight) find a stand-in no pack is, as NeoForge
  builds every mod's packs itself (CORPUS-44).
- **Fabric's renderer API**: it loads, with Indigo, and a Fabric model that isn't a vanilla adapter (its own
  `emitBlockQuads`), alone or among the parts of a vanilla multipart or weighted model, is drawn by Indigo where NeoForge
  21.1 draws blocks: in chunk sections, as a falling block or one a piston moves, with the breaking overlay and through
  the block renderer's `renderBatched`, once per block at its first render type, as upstream draws it on Fabric
  (FRAPI-5). Indigo's own hooks there miss NeoForge's code and are left out. A block a minecart carries comes from
  vanilla's quads, as upstream draws it on Fabric too, so a model with only Fabric quads isn't drawn there. Fabric item
  models are drawn in the hotbar, in hand, dropped and in item frames. CI checks each of these, and that a model that
  throws fails alone (see the renderer API's section below).
- **Model loading's modifiers**: `modifyModelBeforeBake` and `modifyModelAfterBake` run for top-level models (a block
  state's, an item's) but not for a model baked as another's part or parent, as upstream's hook is on an overload
  NeoForge 21.1 doesn't call there (MODEL-4).
- **A model's `loader` key**: NeoForge 21.1 reads it as the name of one of its geometry loaders and fails a model naming
  none; on Fabric the game ignores it, and Fabric mods read it in their own hook into vanilla's model deserializer, as
  Moonlight does for Supplementaries' models. A `loader` NeoForge doesn't know whose namespace is a Fabric mod's id is
  left to that mod; one NeoForge knows is NeoForge's, and any other unknown loader still fails the model (CORPUS-62).
- **Entity data serializers**: 0.116 has no registry for them, so Fabric mods register through vanilla's
  `EntityDataSerializers.registerSerializer`, which NeoForge 21.1 refuses for mods. Fabricated registers such a
  serializer in NeoForge's synced registry instead, named after the mod and the order it registers in (CORPUS-41).
- **0.116's object builder**: `FabricBlockSettings` has both `create()` and `of()` (CORPUS-39), and vanilla's block
  entity type builder, which exists only on 1.21.1, has Fabric's `build()` (CORPUS-40).
- **Libraries**: NeoForge 21.1's launcher ships libraries later versions don't, checker-qual among them; a Fabric mod's
  copy of one of their packages is left out for the platform's, as for the other platform libraries (CORPUS-43).
- **Left out by pre-flight** (with Fabric API installed, the module loads without it): `fabric-recipe-api-v1`'s
  hook on the ingredient network codec, whose call NeoForge replaced with its own custom ingredient codec, so clients
  receive a Fabric custom ingredient as the items it matches (UNVERIFIED in a client run); `fabric-data-generation-api-v1`'s
  tag provider hook, as on later versions; and on the client, where NeoForge changed the code they hook: Indigo's chunk
  section and block renderer hooks (Fabricated draws through Indigo instead, FRAPI-5), `fabric-model-loading-api-v1`'s
  hook on baking a model another model needs (MODEL-4),
  `fabric-rendering-fluids-v1`'s same-fluid, overlay and block checks, `fabric-sound-api-v1`'s audio stream hook (as
  on every version) and `fabric-block-view-api-v2`'s render data in chunk sections.
- The plan's other skips (tag files' `fabric:remove`, the dimensions module's DataFixerUpper mixins, resource
  conditions' registry loader) are 1.21.11's. NeoForge 21.1 leaves vanilla's JSON data loaders as they are, so
  upstream's load conditions hook applies there, and a data file whose `fabric:load_conditions` fail isn't loaded, as
  on Fabric (CORPUS-49). Fabricated decides them there before other mods' hooks and takes them off the files that load,
  so a NeoForge mod reading them again as NeoForge's own conditions finds none: Moonlight's NeoForge build (3.8.3) does,
  asking NeoForge's mod list, which names a Fabric mod by its id with underscores and has none of the Fabric API modules
  Fabricated provides, so with it installed a Fabric mod's recipe, advancement or other JSON data whose
  `fabric:all_mods_loaded` named such a mod wasn't loaded (COND-1). A file whose conditions Fabric API can't parse is
  left as it is, as Fabric API keeps it without deciding, so a mod that can read them still decides.

### Why modules are disabled

Pre-flight reads NeoForge's patched Minecraft bytecode and checks each Fabric API mixin against it. The
failures fall into a few groups:

- **NeoForge changed the code the mixin targets.** This is the most common cause. Examples from the
  reports:
  - `fabric-block-api-v1` (native now): NeoForge replaces `BlockState.isAir()` calls in chunk sections.
  - `fabric-networking-api-v1`: NeoForge replaces the custom-payload codec setup in
    `ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket`, because NeoForge has its own payload
    system.
- **NeoForge recompiles Minecraft, so names of synthetic members differ** (1.21.11): lambda methods such as
  `lambda$loadResources$1` and captured fields such as `val$tags` have different names in NeoForge's build.
- **The mixin targets a library class**: `fabric-dimensions-v1` patches DataFixerUpper's `TaggedChoice`,
  which NeoForge loads without mixin support. Fabricated's Fabric API plan
  (`fabric-api-plan.json`) leaves out those mixins and the mixin that depends on them, so the rest of the
  module loads; only the safety net for worlds that reference a removed modded dimension is missing. A Fabric
  mod's mixin into such a class that only gives it one of the mod's interfaces (Carpet's into Brigadier's command
  nodes, to remove a script's commands) is replaced by a generated class serving the interface (LIB-1); one giving it
  another library's interfaces with the class's public methods (adventure-platform-fabric's into authlib's
  `GameProfile`) by an adapter the mod's casts wrap the object in (LIB-2).
- **NeoForge changed a field's type**: an `@Accessor` must read a field of exactly its type. Upstream's
  `fabric-object-builder-api-v1` (26.x) read `AttributeSupplier.Builder.builder` as an `ImmutableMap.Builder`,
  which NeoForge replaced; it is native now (see below).
- **NeoForge's build gives a lambda different parameters**: an `@Inject` handler must take the target
  method's parameters in order. `fabric-loot-api-v3` (26.1.2, 26.2; native now) and `fabric-resource-conditions-api-v1`
  inject into synthetic lambdas whose captured values NeoForge's build passes in another order or not at all.
  NeoForge rewrote that code around its own conditions, so Fabricated evaluates `fabric:load_conditions` inside
  NeoForge's conditional codecs instead (with upstream's conditions), and the module loads.
- **A mixin adds a method NeoForge already added**: `fabric-recipe-api-v1` generates an accessor
  `getConnection()` on `ServerCommonPacketListenerImpl` (NeoForge adds a public one), on 26.1.2 and 1.21.11 its
  `IngredientMixin` merges a `hashCode()` that NeoForge's `Ingredient` already has, and `fabric-tag-api-v1`
  merges a `remove()` into `TagFile`, which NeoForge already declares. Mixin rejects all three. Fabricated now
  turns an accessor whose method the target already declares publicly into a shadow of that method (for any
  mod, reported as a warning), and the plans leave out the other two methods: NeoForge's `hashCode` stays, and
  NeoForge's own tag `remove` list, which NeoForge applies, also receives `fabric:remove` entries. recipe-api
  and tag-api load on all versions.
- **NeoForge's build names a captured variable differently**: an anonymous class keeps each local variable it
  captures in a synthetic field named after it (`val$tags`). NeoForge's 1.21.11 build is recompiled from
  decompiled code, so the variable, and with it the field, has another name. `fabric-tag-api-v1` reads and writes
  `val$tags` in `MappedRegistry$TagSet$2` with an accessor. When an `@Accessor` names a missing `val$` field and
  the target has exactly one captured field of the same type, Fabricated points the accessor at that field (for
  any mod, reported as a warning).
- **NeoForge calls a different constructor**: `fabric-particles-v1` modifies `new BlockParticleOption(type,
  state)` in `Entity.spawnSprintParticle`; NeoForge passes the block position as a third argument. The plan
  leaves out that mixin and the entity one next to it (they only make sprint and fall particles block-aware),
  so the module loads. NeoForge also re-keyed the particle provider map the module registers into;
  Fabricated adds vanilla's map back next to NeoForge's, forwarding to it. On 1.21.11 the module also reads a
  networking-internal interface of upstream's networking module, which Fabricated provides as upstream does (the
  module then always sends the particle encoding every client reads).
- **NeoForge calls a different overload**: on 26.3 NeoForge loads the reloadable registries through its own
  `RegistryDataLoader.load` overload (with the updated tags), so `fabric-advancement-api-v1`'s reload mixin finds
  nothing to wrap. Fabricated wraps NeoForge's call instead and runs upstream's reload hooks around it
  (advancement modifications see the registries being loaded; `AdvancementEvents.ALL_LOADED` fires).
- **NeoForge moved a method's code into a new overload**: NeoForge often keeps vanilla's signature as a method that
  calls a new overload of the same name holding the code (`SimpleModelWrapper.bake`, `ModelManager`'s model
  discovery). A selector without a descriptor picks the first method of that name, as Mixin does, so an injection
  point inside the code is missing there. When the points all occur in the one overload that method calls, and the
  handler doesn't depend on the method's parameters (an `@Inject` taking only the callback info, a `@WrapOperation`,
  `@ModifyExpressionValue`, `@Redirect` and so on), Fabricated points the injector at the overload (for any mod,
  reported as a warning).
- **A mixin uses an unsupported module**: Mixin fails on a class it can't find as it applies a mixin, so a mod
  whose mixin uses a class only an unsupported Fabric API module has (Continuity's used the renderer API while that
  was unsupported on 1.21.11; today only the game test modules are) is disabled before launch with the reason.
  When the mixin's config has a plugin, which may skip it at runtime, or isn't required, only that mixin is left out.
- **Dependency cascade**: a module that requires a disabled module is disabled too. In the current CI runs no
  Fabric API module is disabled only for that reason; `fabric-data-attachment-api-v1`, which needed
  object-builder, loads now.

Each of these crashed the server at some point during development: they are the reason the pre-flight checks
exist. Now each is a clear line in the report, and the server starts with the complete Fabric API jar on
every version; on 26.x no Fabric API module is disabled any more.

### Native `fabric-lifecycle-events-v1`

Upstream's mixins don't apply on NeoForge (for example, NeoForge moved the `blockEntities.remove` call in
`LevelChunk.getBlockEntity`). Fabricated therefore ships the module's unchanged public API and fires its
events from NeoForge's events. CI checks `SERVER_STARTED`, `END_SERVER_TICK` and `SERVER_STOPPING` on a
real server for every version.

| Fabric events | Fired from (NeoForge) | Notes |
|---|---|---|
| Server starting/started/stopping/stopped | `ServerStartingEvent` … `ServerStoppedEvent` | |
| Server and level ticks (start/end) | `ServerTickEvent`, `LevelTickEvent` | |
| Level load/unload | `LevelEvent.Load/Unload` | |
| Chunk load/generate/unload | `ChunkEvent.Load/Unload` | `CHUNK_GENERATE` fires for newly generated chunks |
| Full chunk status / chunk level type change | `ChunkTicketLevelUpdatedEvent` | fired when a loaded chunk's ticket level changes |
| Entity load/unload, `ALLOW_LOAD` (26.x) | `EntityJoinLevelEvent`, `EntityLeaveLevelEvent` | `ALLOW_LOAD` cancels the join |
| `EntityLoadData` (26.x) | `EntityJoinLevelEvent`, `FinalizeSpawnEvent` | spawn reason is known for mobs and null for other entities, which the API allows |
| Equipment change | `LivingEquipmentChangeEvent` | |
| Block entity load/unload | `LevelChunk` (Fabricated mixin, at upstream's points) and `ChunkEvent.Unload` | as on Fabric: when a block entity enters or leaves its chunk (loading with it, placed, broken or replaced later), and for those a chunk holds when it unloads. On the client also when the server resends a chunk, and when the client drops its level (disconnect, dimension change) |
| Sync data pack contents | `OnDatapackSyncEvent` | |
| Tags loaded | `TagsUpdatedEvent` | |
| Client: ticks, chunks, entities, block entities | `ClientTickEvent`, `LevelTickEvent`, `ChunkEvent`, `EntityJoin/LeaveLevelEvent` | chunks the client drops without NeoForge's event (a smaller view distance, a reused slot) unload too, as upstream |
| Client started / stopping | first client tick / `GameShuttingDownEvent` | |
| After client level change | `ClientPlayerNetworkEvent.LoggingIn` and `.Clone` | fires on login and on respawn into another level |
| **Not fired**: start/end data pack reload, before/after save | none | NeoForge has no matching hook |

### Native `fabric-networking-api-v1`

Fabric payload types are registered with NeoForge as optional payloads when NeoForge locks its network registry
(after every mod initialiser), so they travel through NeoForge's own networking, including its large-payload
splitting. Play handlers run on the main thread and configuration handlers on the network thread, as in Fabric.
A packet carrying a Fabric payload is encoded once, as on Fabric: NeoForge's splitter encodes each packet to measure
it before vanilla's encoder encodes it again, and a codec that empties what it writes, as Cardinal Components API's
sync payloads' does, sent no data the second time; vanilla's encoder now writes the bytes the splitter measured
(NET-10). CI's clients check such a payload arrives whole.

| Fabric | NeoForge |
|---|---|
| `PayloadTypeRegistry` (play and configuration, both directions) | `RegisterPayloadHandlersEvent` registrar (`optional()`) |
| Global and per-connection receivers, `PacketContext` (26.x API) | payload handlers, dispatched by connection |
| `canSend` / `getSendable` | NeoForge's channel negotiation (`hasChannel`) |
| `ServerPlayConnectionEvents`, channel `REGISTER` events | `PlayerLoggedIn/OutEvent` |
| `ServerConfigurationConnectionEvents.BEFORE_CONFIGURE/CONFIGURE`, `addTask`, `completeTask` | `RegisterConfigurationTasksEvent`, `finishCurrentTask` |
| `EntityTrackingEvents` | `PlayerEvent.StartTracking/StopTracking` |
| `ServerPlayNetworking.reconfigure` | vanilla `switchToConfig()` |
| Client play/configuration connection events | `ClientPlayerNetworkEvent`, configuration listener creation |

Not supported: **login queries** (`ServerLoginNetworking`, `ClientLoginNetworking`): NeoForge exposes no
login-phase hooks. The API links, so mods load, but login receivers are never called; mods should use the
configuration phase instead (Fabric's own recommendation since 1.20.2). Configuration-phase `DISCONNECT` events
fire when a connection closes during configuration, from the configuration listener's `onDisconnect` on the server or
client thread (Fabric API may fire them from the network thread instead, as the channel closes). A payload id can use a different codec in each direction, as on Fabric
(NeoForge keeps one codec per id, so Fabricated supplies the clientbound one when NeoForge looks it up).
CI checks that a Fabric payload and receiver register and reach NeoForge on a dedicated server, that a
configuration listener closed there fires `DISCONNECT` once, and, on the client, a play-phase round trip and a
configuration task with the integrated server. The client's `DISCONNECT` has no check: it would end the client's own
connection.

### Native `fabric-resource-loader-v1`

Upstream patches the reload-listener and pack-repository code that NeoForge has already replaced with its own
sorted reload-listener events. Fabricated ships the module's unchanged public API on top of those events:

| Fabric | NeoForge |
|---|---|
| Server data listeners, listener factories (`DataResourceLoader`) | `AddServerReloadListenersEvent` |
| Client resource listeners | `AddClientReloadListenersEvent` |
| A listener registered the vanilla way on the client's resource manager before that event (from a mixin into `Minecraft`'s constructor) | held back and added through the event, after vanilla's: NeoForge's event refuses listeners it can't name |
| `addListenerOrdering` / `addReloaderOrdering` | `addDependency`; Fabric's vanilla ids (`minecraft:recipes`, `minecraft:languages`, …) are translated to NeoForge's (`minecraft:recipe_manager`, `minecraft:language_manager`, …) |
| `fabric:before_vanilla`, `fabric:after_vanilla` phases | empty listeners before the first and after the last vanilla listener |
| Shared reload state: registries, feature flags, data store (and on 1.21.11, 26.1.2 and 26.2 the recipe and advancement managers) | a setup listener that runs before all others |
| `MinecraftServer` as `DataResourceStore`, `Resource#getFabricPackSource` | interfaces added by mixins that only add methods |
| `registerBuiltinPack` | `AddPackFindersEvent` |

A listener registered the vanilla way (`ReloadableResourceManager.registerReloadListener`, as OneConfig does from a
mixin into `Minecraft`'s constructor) after NeoForge has sorted the client's listeners is added last, as vanilla adds
it, and runs from the next reload; NeoForge itself would throw there.

Mod resources themselves need nothing: NeoForge already loads the `assets/` and `data/` of every mod, Fabric mods
included. Resources from those mod packs report Fabric's mod pack source, as on Fabric. What only NeoForge reads in a
Fabric jar built for both loaders is left out, as Fabric ignores it: the `neoforge:` sections of its `pack.mcmeta`,
and the `neoforge:conditions` of its data files, which name its NeoForge build's own condition types (Tectonic's,
DATA-1); the Fabric build's `fabric:load_conditions` still decide. A built-in pack (`resourcepacks/<name>`, Fabric API's
`registerBuiltinPack`) is read with those changes too (PACK-2), and has Fabric API's known-pack identity, so the
registry entries it adds are stable and a world with them opens without an experimental-settings warning (PACK-3).
A mod that adds a pack source the vanilla way, replacing the repository's sources with a new immutable set
(FrozenLib's resource pack API), keeps it: the set is made mutable again before NeoForge adds its own pack finders
(PACK-1).

With Fabric API, a pack's conditional overlays (`fabric:overlays`) apply where vanilla reads a pack's metadata, as
upstream applies them, but not to a pack that declares NeoForge's own (`neoforge:overlays`): such a pack is built for
both loaders, its Fabric overlays gated by conditions only its Fabric build registers (the NeoForge builds of Terralith
and Tectonic), and NeoForge's overlays alone apply to it, as without Fabricated. A Fabric jar never declares them, as
its `neoforge:` sections are left out (above). A `fabric:overlays` section that can't be read (a condition nothing
registers, the wrong shape) is left out with one warning naming the pack, which loads with its other overlays. Upstream's
hook made such packs unreadable, and a mod passing the missing pack on stopped the server (PACK-12). On 26.x, a Fabric
mod whose own `pack.mcmeta` can't be read loses its pack, with NeoForge's error naming the file, where NeoForge's second
read of the file stopped loading every pack; NeoForge stops there for a native mod's, which Fabricated leaves as it is.

`DEFAULT_ENABLED` built-in **resource** packs are switched on as upstream switches them on: the first time the client
is offered each, and only then, so a pack the player turned off stays off, and a pack whose mod was removed is
switched on again when it comes back (PACK-6). Which packs were offered before is kept in upstream's own file,
`data/fabricDefaultResourcePacks.dat` in the game directory, so an instance moved between Fabric and Fabricated keeps
the player's choices. NeoForge itself has no such default.
Upstream adds the packs as the options load; Fabricated adds them as the client applies the options to its pack
repository, which by then has found every pack.

Differences from upstream:

- An ordering that names a listener nobody registered is skipped (logged at debug level). An ordering that would
  create a cycle is skipped with a warning in the report, where NeoForge would otherwise stop the game.
- `DEFAULT_ENABLED` built-in **data** packs are enabled in new worlds. `ALWAYS_ENABLED` packs are required on both
  sides.
- Fabric's `/datapack` protections for its internal packs don't apply; NeoForge's own rules do.

CI checks, on every version: listener ordering (including an ordering against a vanilla listener), the
shared reload state, a listener factory, the data store read back through `MinecraftServer`, a `DEFAULT_ENABLED`
built-in data pack being enabled and loaded, a `DEFAULT_ENABLED` built-in resource pack being switched on in a new
game directory and its resources loaded, and the listener running again after `/reload`. Core's
`DefaultPackTrackerTest` checks upstream's rule and file format. Client listeners
are checked by the client CI run (a client resource listener runs while the client starts).

### Native `fabric-registry-sync-v0`

NeoForge already syncs registry ids to clients and checks that both sides have the same entries, so Fabric's own
sync protocol isn't needed (and isn't sent). Fabricated provides the module's unchanged public API on NeoForge's
registries:

| Fabric | NeoForge |
|---|---|
| `FabricRegistryBuilder` (new static registries) | registered into the root registry while NeoForge's registration events run, when Fabric initializers run; NeoForge freezes, syncs and snapshots them with the rest |
| `RegistryAttribute.SYNCED` | NeoForge's sync flag; registries NeoForge syncs also report `SYNCED` |
| `RegistryEntryAddedCallback` | NeoForge's registry add callback |
| `RegistryIdRemapCallback` | NeoForge's clear and bake callbacks around applying a registry snapshot (for example the server's ids on joining it) |
| `DynamicRegistries.register` / `registerSynced` (and `registerReloadable` on 26.3) | NeoForge's data-pack registry lists |
| `FabricRegistry#addAlias` | NeoForge's registry aliases |

Differences from upstream:

- `DynamicRegistrySetupCallback` fires once the dynamic registries are loaded (on the server before it starts,
  on the client when joining a server), not before they are filled. Entry-added callbacks registered on its view
  are called once for every entry already present. Entries can't be added to the registries from the callback.
- A registry must be created while registration is open, which is when Fabric initializers run. Creating one
  later fails, as it does on NeoForge.
- `SKIP_WHEN_EMPTY` has no effect; NeoForge decides how to sync an empty registry. The `MODDED`, `OPTIONAL` and
  `SAVE_DATA_VALIDATED` attributes are recorded for mods that read them but change nothing.
- NeoForge has no public API for the sync flag and the data-pack registry lists, so Fabricated reaches them by
  reflection. If a NeoForge build lacks them, the affected call fails with a clear message instead of the game
  failing to start.

CI checks, on every version: a new synced registry created and filled from a Fabric initializer (with the
entry-added callback and an alias), and a synced data-pack registry loaded from a data pack and seen by a
`DynamicRegistrySetupCallback`. Id remapping on joining a server needs a client and isn't tested in CI yet.

### Native `fabric-entity-events-v1`

Upstream's mixins don't apply on NeoForge, so Fabricated ships the module's unchanged public API and fires its
events from NeoForge's entity events. All of them are server-side, as upstream.

| Fabric events | Fired from (NeoForge) | Notes |
|---|---|---|
| `ServerLivingEntityEvents.ALLOW_DAMAGE` | `LivingIncomingDamageEvent` | returning false cancels the hit |
| `AFTER_DAMAGE` | `LivingDamageEvent.Post` | not fired for the hit that kills, as upstream; "damage taken" is after shields, before armor |
| `ALLOW_DEATH` (living and `ServerPlayerEvents`) | `LivingDeathEvent` (highest priority) | returning false cancels the death; the amount is the killing hit's damage |
| `AFTER_DEATH`, `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` | `LivingDeathEvent` (lowest priority, not cancelled) | |
| `MOB_CONVERSION` | `LivingConversionEvent.Post` | NeoForge doesn't pass the conversion parameters; vanilla's usual ones are given |
| `ServerPlayerEvents.COPY_FROM`, `AFTER_RESPAWN`, `JOIN`, `LEAVE` | `PlayerEvent.Clone`, `PlayerRespawnEvent`, `PlayerLoggedIn/OutEvent` | |
| Level change (player and other entities) | `PlayerChangedDimensionEvent`; `EntityTravelToDimensionEvent` then the copy joining the destination | |
| `ServerMobEffectEvents` and `FabricMobEffect` hooks | `MobEffectEvent.Applicable`, `Added`, `Remove`, `Expired` | `AFTER_REMOVE` runs as the server's next task, once the effect is gone. The context names the command an effect comes from, set where vanilla's command execution runs a command (upstream's hook is into Brigadier, which NeoForge loads without mixins; EFFECT-1); a command a mod runs through Brigadier's own `CommandDispatcher.execute` gives none |
| `EntitySleepEvents.ALLOW_SLEEPING`, `STOP_SLEEPING` (players), `ALLOW_SETTING_SPAWN` | `CanPlayerSleepEvent`, `PlayerWakeUpEvent`, `PlayerSetSpawnEvent` | |
| **Not fired**: `EntityElytraEvents`, the other sleep events | none | NeoForge has no matching hook |

CI checks, on every version: damage and death events for a mob, in order; mob effect add and remove on 1.21.11 and
26.x, with an effect the effect command gives coming from that command and one from code from none (EFFECT-1), and on
1.21.1, whose Fabric API (0.116) has no mob effect events, mob conversion instead.

### Native `fabric-object-builder-api-v1`

Upstream's mixins don't fit NeoForge: NeoForge changed the attribute builder's field type, the villager-trade
constructor and the entity data serializer table. Fabricated therefore ships the unchanged public API and
implements it on NeoForge's registration hooks. Those hooks run after every registry is filled, so they also see
what Fabric initializers registered.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `FabricDefaultAttributeRegistry.register` | `EntityAttributeCreationEvent` | for a type that already has attributes (vanilla's or a NeoForge mod's), applied as a modification instead |
| `FabricDefaultAttributeRegistry.MODIFY` (26.x; not in the 1.21.11 API) | `EntityAttributeModificationEvent` | runs once, after registration, as upstream; attribute registrations made later are ignored with a warning |
| `FabricEntityType.Builder` (`createLiving`, `createMob`, `alwaysUpdateVelocity`, `canPotentiallyExecuteCommands`) | NeoForge's own `EntityType.Builder` methods | default attributes and spawn placements are taken when the type is built |
| Spawn placements | `RegisterSpawnPlacementsEvent` (replace) | |
| `FabricEntityDataRegistry` (`FabricTrackedDataRegistry` on 1.21.11); on 1.21.1, which has neither, vanilla's `EntityDataSerializers.registerSerializer` | NeoForge's `ENTITY_DATA_SERIALIZERS` registry | NeoForge keeps the ids in step between server and client; register from an initializer. On 1.21.1 a mod's serializer is named after the mod and the order it registers in (CORPUS-41) |
| `FabricBlockEntityTypeBuilder`, `FabricBlockEntityType.addValidBlock` (`addSupportedBlock` on 1.21.1 and 1.21.11), `FabricBlockEntityType.Builder.build()` on vanilla's builder (1.21.1) | NeoForge's op-only constructor; a larger copy of the valid-block set; vanilla's `build` without a data fixer type | |
| `WoodTypeBuilder`, `BlockSetTypeBuilder`, `PoiHelper` / `PointOfInterestHelper` | vanilla, unchanged | Fabricated's access transformer opens `PoiTypes.register` and `SpawnPlacements.register`, which the API calls. A type registered through `PoiTypes.register` (this helper, WorldWeaver) has its states mapped once, though NeoForge maps them as the type is registered and vanilla then again (POI-1) |
| `TradeOfferHelper` (1.21.11) | `VillagerTradesEvent`, `WandererTradesEvent` | NeoForge rebuilds the tables on every data load, so registrations are replayed each time. In rebalanced-trade worlds, additions for professions without their own rebalanced table use the normal ones |
| **Not used**: `MinecartComparatorLogicRegistry` | none | registrations are kept, but detector rails ignore them (NeoForge has no hook) |

Data saved with a null data-fix type, which upstream also handles, already works on NeoForge. The client-side
sign-editing mixins are not shipped: NeoForge resolves modded wood types' textures in the sign-editing screens
itself, except on 1.21.11, where the plain (not hanging) sign screen shows a missing texture for modded wood
types.

CI checks, on every version: a mob type built with `createMob` gets its default attributes, the `MODIFY`
change (26.x), its spawn placement and the velocity flag; an entity data serializer gets an id; a block entity type
gets the op-only flag and an added block. On 1.21.11, also a villager trade added through `TradeOfferHelper`.

### Native `fabric-block-api-v1`

Upstream's mixins redirect code NeoForge patched, so Fabricated ships the unchanged public API and answers it from
NeoForge's block hooks.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `FabricBlock.getAppearance`, `FabricBlockState.getAppearance` | NeoForge's `getAppearance` | the two have the same signature; Fabricated gives `Block` and `BlockState` their own, so a block overriding it overrides both |
| `FabricBlock.getProvidedEnchantmentPower` (26.2+) | `getEnchantPowerBonus` | for blocks in `minecraft:enchantment_power_provider`, as upstream |
| `BlockFunctionalityTags.CAN_CLIMB_TRAPDOOR_ABOVE` | `makesOpenTrapdoorAboveClimbable` | ladders keep NeoForge's facing rule |
| `FabricBlock.FabricProperties.blockId` (26.x) | the properties' id | |
| `FluidFlowEvents.ALLOW` (26.2+) | NeoForge's fluid interaction registry | asked when a neighbour of a liquid block changes, where vanilla makes obsidian or basalt; **not** when a liquid is placed or flows into a new position (NeoForge has no hook there) |
| **Not provided**: upstream's chunk-section air fix (MC-232360) | none | a modded block that reports itself as air may still be treated as air there, as on NeoForge |

A Fabric block class compiled against Fabricated's development setup (as Fabricated's own test mod is) must override
`getAppearance` itself, because the compiler sees both defaults; already-compiled Fabric mods are unaffected.

CI checks, on every version: the trapdoor tag, appearance through Fabric's interface, and on 26.x the properties
id; on 26.2 and 26.3 also enchanting power and the fluid flow event.

### Native `fabric-item-api-v1`

Upstream's mixins redirect furnace, brewing, crafting, enchanting, damage and tooltip code that NeoForge patched, so
Fabricated ships the unchanged public API and answers it from NeoForge's item hooks and events. A Fabric item's
overrides of `FabricItem` take effect wherever NeoForge asks.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `FabricItem.getCraftingRemainder` (`getRecipeRemainder` on 1.21.11) | `getCraftingRemainder` | furnaces, brewing stands, crafting |
| `canBeEnchantedWith`, `EnchantmentEvents.ALLOW_ENCHANTING` | `supportsEnchantment` (anvil, command, loot), `isPrimaryItemFor` (enchanting table) | enchanted books and books keep NeoForge's rule |
| `Properties.equipmentSlot` | `getEquipmentSlot` | NeoForge's hook doesn't say which entity asks, so the provider gets `null` for it |
| `Properties.customDamage` | `damageItem` | not asked for creative players, as upstream; the slot is the one holding the stack |
| `allowComponentsUpdateAnimation`, `allowContinuingBlockBreaking` (client) | `shouldCauseReequipAnimation`, `shouldCauseBlockBreakReset` | |
| `getCreatorNamespace` | `getCreatorModId` | when the item overrides it |
| `DefaultItemComponentEvents.MODIFY` | `DefaultDataComponentsBoundEvent` on 26.x (as upstream, once the items' default components are bound); `ModifyDefaultComponentsEvent` on 1.21.11 | as a component patch on 1.21.11 |
| `ItemComponentTooltipProviderRegistry` (26.x) | `RegisterTooltipAppendersEvent` | same placements (first, last, before, after) |
| `ComponentTooltipAppenderRegistry` (1.21.11) | `ItemTooltipEvent` | NeoForge 1.21.11 can't place component tooltips, so they come after the other lines; anchors aren't honoured |
| `ItemClickBehaviorCallback` (26.2+) | `ItemStackedOnOtherEvent` | |
| `ItemTooltipCallback` (client) | `ItemTooltipEvent` | |
| `FabricComponentMapBuilder`, `FabricTooltipFlag`, `Properties.itemId`/`modelId` | added by Fabricated's mixins | NeoForge already gives the builder `get` |
| `EnchantmentEvents.MODIFY` (and `MODIFY_WITH_LOOKUP` on 26.x), 26.3's `BlockTransformerEvents.MODIFY` and `BlockTransformerHelper` | Fabricated's hook where vanilla reads each data pack registry entry, as upstream | a changed entry is sent to clients in full |

CI checks, on every version: equipment slot, crafting remainder, the allow-enchanting event, custom damage
(through a player), a default component change, the creator mod and an enchantment modified as it loads; on 26.x
also the item id; on 26.3 also a registered block transform and one added by the modify event.

### Native `fabric-events-interaction-v0`

Upstream hooks the server's and client's interaction code with mixins at the same places NeoForge fires its
interaction events, and NeoForge's patches change that code. Fabricated ships the unchanged public API and runs the
callbacks from NeoForge's events, at the highest priority (upstream's hooks come first too). A result other than
`PASS` (or a non-null one for the `BlockEvents`/`ItemEvents` callbacks) cancels NeoForge's event with that result.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `UseBlockCallback` | `PlayerInteractEvent.RightClickBlock` | both sides; skipped for spectators on the client, as upstream |
| `UseItemCallback` | `PlayerInteractEvent.RightClickItem` | both sides |
| `UseEntityCallback` | `EntityInteract` (26.2+), `EntityInteractSpecific` (26.1.2, 1.21.11), plus `EntityInteract` without a hit result on the 1.21.11 server | as upstream, the hit result carries the clicked point |
| `AttackBlockCallback` | `PlayerInteractEvent.LeftClickBlock` when breaking starts | a cancelled attack resends the block (and its block entity) to the client, as upstream; `BlockAttackInteractionAware` is honoured |
| `AttackEntityCallback` | `AttackEntityEvent` | the hit result is always `null` |
| `BlockEvents.USE_ITEM_ON`, `ItemEvents.USE_ON` | `UseItemOnBlockEvent` (block and item phases) | at the head of `BlockState.useItemOn` and `ItemStack.useOn` |
| `BlockEvents.USE_WITHOUT_ITEM`, `ItemEvents.USE`, `PlayerPickItemEvents` | Fabricated's mixins, as upstream's | NeoForge has no event for these |
| `PlayerBlockBreakEvents.BEFORE`/`CANCELED` | `BreakBlockEvent` (26.x), `BlockEvent.BreakEvent` (1.21.11) | server side, as upstream; `CANCELED` resends the 3x3 area around the block |
| `PlayerBlockBreakEvents.AFTER`, `ClientPlayerBlockBreakEvents.AFTER` | a hook observing the block's removal | |
| `ClientPreAttackCallback` | asked once per client tick; cancels `InputEvent.InteractionKeyMappingTriggered` for attacks | no swing, and block breaking stops, as upstream |
| `ClientHotbarScrollEvents` (26.x) | a mixin around the hotbar slot change, as upstream's | scroll amounts from `InputEvent.MouseScrollingEvent` |
| `FakePlayer` | Fabricated's packet listener; the player reports `isFakePlayer()` | NeoForge then defaults it to survival and (26.2+) gives it a no-op advancement tracker; on 1.21.11 and 26.1.2 upstream's advancement guards apply |

On the client, NeoForge fires these events while it builds the interaction packet, so a client-side cancelled
interaction is still sent to the server, where the callbacks run again. Upstream sends it only when the client
result consumes the action.

CI checks, on every version, with a Fabric fake player on the server: every server-side callback above is
called and its result honoured (use item, use block, block use-item-on and use-without-item, item use and use-on,
attack block, attack entity, use entity, break before/canceled/after, pick item), and the fake player is a NeoForge
fake player. The client mixins are applied when the client starts.

### Native `fabric-loot-api-v3`

Upstream's loot builder, accessor and drop mixins pass pre-flight on every version, so Fabricated ships them
unchanged (with the public API). Only its hook into loot table loading conflicts with NeoForge, which rewrote that
code; Fabricated replaces it:

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `LootTableEvents.REPLACE`, `MODIFY` | `LootTableLoadEvent` (high priority) | once per table, with the source (vanilla, mod or data pack) of the file in use; the table is rebuilt from Fabric's builder, as upstream |
| `LootTableEvents.ALL_LOADED` | a hook on `ReloadableServerRegistries.reload` | once the reload's loot table registry is complete; the reload's result is passed on unchanged |
| `LootTableEvents.MODIFY_DROPS` | upstream's mixin | NeoForge's global loot modifiers run after it |
| `FabricLootTableBuilder`, `FabricLootPoolBuilder` | upstream's mixins | |

On 26.3, NeoForge's event offers the loading context only as a `HolderGetter.Provider`: the registry lookup Fabric's
events receive there can get entries and tags (loot tables included) but lists none.

CI checks, on every version: dirt's table is modified with source `VANILLA`, `REPLACE` and `ALL_LOADED` run, and
dirt's drops contain the added pool's diamond and the drop listener's emerald.

### Native `fabric-content-registries-v0`

Most of upstream's registries and mixins apply on NeoForge as they are and ship unchanged. Where NeoForge rewrote the
code they rely on, Fabricated bridges the gap:

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `CompostableRegistry` (`CompostingChanceRegistry` on 1.21.11), up to 26.2 | NeoForge's composter asks its data map; an item the data map leaves out gets the chance Fabric's registry stored | data packs win; villagers only compost data-map items |
| `VibrationFrequencyRegistry` (`SculkSensorFrequencyRegistry` on 1.21.11) | NeoForge's frequency lookup falls back to the table Fabric's registry writes | data packs win |
| `FuelValueEvents` (`FuelRegistryEvents` on 1.21.11), up to 26.2 | run on NeoForge's fuel builder, after its furnace fuel data map | `BUILD`, then `EXCLUSIONS`; vanilla's non-flammable wood exclusion is already in the data map |
| Flammable, strippable, tillable, flattenable, oxidizable and waxable blocks, path types, villager interactions, pot patterns, brewing | upstream's mixins | NeoForge falls back to the vanilla tables these write; on 26.3, where NeoForge reads oxidation and waxing from its data maps only, Fabricated adds that fallback |
| `EntityFluidInteractionRegistry` (26.x) | upstream's mixins, without three injectors | UNVERIFIED: what the interactions registered here do in a Fabric fluid is not tested. Until Fabric fluids had fluid types (see *Fabric mods' fluids* below), an entity touching one crashed the game; swimming, jumping out of it, drowning and boats floating follow those types now, which NeoForge's rewritten code asks |
| Oxidation and wax scraping (26.3) | added to the axe's block transformer, as upstream | see `BlockTransformerHelper` under `fabric-item-api-v1` |

On 26.3, composting, stripping and fuel are data-driven in vanilla, and Fabric API has no registries for them.

CI checks, on every version: a flammable block, a waxable pair and a custom game event's vibration frequency take
effect; up to 26.2 also a compostable item, a strippable block and a fuel value.

### Native `fabric-biome-api-v1`

NeoForge reads a biome's climate, effects, generation settings and mob spawns (on 26.3 its environment attributes, mob
spawns among them, instead of the spawn settings) from its own modifiable biome info, made when the biome is built,
and keeps the biome's fields for the climate and the effects (on 26.3 the attributes too) private for its coremod.
Upstream's module replaces those fields: on NeoForge that changes nothing NeoForge reads, and it fails when it runs: on
26.3 adding a spawn crashed world creation (`IllegalAccessError` on the attribute map, BIOME-1), and before 26.3 a
weather change reads the private climate field, as the 1.21.11 corpus server's attribute change did before BIOME-2
(the weather change itself not seen in a game; Fabricated's class tweaker conversion logs that the line stays private).
Fabricated ships the module on every version:

| Part | On NeoForge |
|---|---|
| `BiomeModifications` | applied by a NeoForge biome modifier (`fabricated:fabric_biome_api`, declared by Fabricated's data pack) on NeoForge's builders, as the server starts: additions in NeoForge's `ADD` phase, removals in `REMOVE`, replacements in `MODIFY`, post-processing in `AFTER_EVERYTHING`; selectors see the biome as it was built, as upstream's do |
| Mob spawns and spawn costs | NeoForge's spawn builder. On 26.3 NeoForge writes it into the biome's `NATURAL_MOB_SPAWNS` attribute as an overlay; a mod setting that attribute itself replaces the builder's spawns and costs. For a biome whose spawns aren't an overlay (which NeoForge then leaves alone), the spawns are read from the attribute and set on it, as upstream does |
| Environment attributes (1.21.11, 26.1.2, 26.2) | NeoForge's biome info doesn't hold them there: they are replaced on the biome, as upstream replaces them, and a biome whose attributes changed is then sent to clients rather than read from their own copy (NeoForge's own check compares its info, which doesn't hold them; upstream sends every biome it modifies) |
| `NetherBiomes`, `TheEndBiomes` | upstream's biome source mixins, unchanged |

CI checks, on every version: a mob spawn added to plains and plains' changed temperature are in the biome once the
server has started; before 26.3 also a sky colour set on the desert, its only change, which then has no known pack, so
clients are sent it.

### Native `fabric-api-lookup-api-v1` and `fabric-transfer-api-v1`

Both modules are upstream's code. The lookup module and most of transfer's mixins apply on NeoForge unchanged. Three
mixins needed changes:

| Upstream mixin | On NeoForge | Why |
|---|---|---|
| `HopperBlockEntityMixin` | not used | NeoForge's hoppers already move items through its capabilities |
| `SimpleContainerMixin`, `BaseContainerBlockEntityMixin` | rewritten | they defer `setChanged()` until a transaction commits by redirecting a call NeoForge's patches moved; Fabricated uses the `setItem(slot, stack, insideTransaction)` overload NeoForge added for the same purpose |

`ItemStorage.SIDED` and `FluidStorage.SIDED` find vanilla containers, cauldrons, composters, jukeboxes, shelves and
bundles as upstream does, and transactions behave as upstream's.

**Bridge to NeoForge's capabilities.** Fabric storages and NeoForge's item and fluid handlers on blocks see each
other: a NeoForge hopper or pipe moves items and fluids in and out of a Fabric mod's machine, and a Fabric mod's
pipe reaches a NeoForge mod's block. Storages in items do too (`ItemStorage.ITEM` and `FluidStorage.ITEM` with
`Capabilities.Item.ITEM` and `Capabilities.Fluid.ITEM`): a NeoForge machine fills a Fabric mod's tank item, and a
Fabric mod's machine a NeoForge mod's. The item's storage changes the item (an empty cell becomes a full one) through
the other side's access to where it is: NeoForge's `ItemAccess` as a Fabric `ContainerItemContext`, and the other way.

- Transactions are mirrored both ways: a simulation (an aborted transaction) on one side is aborted on the other, and
  nested transactions keep their meaning.
- Fluids convert at 81 droplets per millibucket. Only whole millibuckets move between the two: if a Fabric storage
  would take part of one, it is asked again for whole millibuckets.
- Each side answers for what its own mods add, told apart by namespace. NeoForge code asking about a Fabric mod's
  block or item gets the answer of Fabric's whole lookup, fallbacks included: Tech Reborn offers its machines' tanks
  and energy through one, and a Fabric mod's `Container` block entity gets Fabric's generic wrapper, as on Fabric.
  Fabric code asking about a NeoForge mod's block or item gets its NeoForge capability, before Fabric's generic
  `Container` wrapper, so the block's access rules hold. Neither side ever asks the other about the same thing.
- A NeoForge mod's own capability for a Fabric mod's block or item wins over the Fabric storage for NeoForge code;
  Fabric code doesn't see it. Content a Fabric mod registers under another mod's namespace counts as that mod's.
- Vanilla blocks and items keep each side's own wrappers. Every bucket item gets Fabric's own bucket storage from
  upstream's fallback, which comes before NeoForge's capability.
- A storage on one side that uses one on the other in the same transaction (an item's handler changing the item
  through the other side's access, a NeoForge handler passing items on to a Fabric storage) works: where a
  transaction already stands for one of the other side's, it is used instead of a new one nested in it.
- Entity storages are not bridged: Fabric API has no lookup for them.

**Energy, with Team Reborn's library.** Fabric API has no energy API; Fabric tech mods share Team Reborn's
(`team_reborn_energy`, nested in the mods that use it). When a Fabric mod brings it, its storages and NeoForge's
energy handlers see each other the same way, on blocks (its `SIDED` lookup and `Capabilities.Energy.BLOCK`) and in
items (its `ITEM` lookup and `Capabilities.Energy.ITEM`, batteries charged through either side). One unit is one
unit: neither API fixes an exchange rate. Without the library nothing of this loads.

On 1.21.11, `FluidVariantRendering`'s default sprites and colours come from NeoForge's fluid client extensions instead
of `fabric-rendering-fluids-v1`'s registry.

CI checks, on every version: a chest found through `ItemStorage.SIDED` keeps its items when a transaction is
aborted and changes when one commits; a full water cauldron empties when a bucket of water is extracted; a Fabric
storage used through NeoForge's capability and a NeoForge handler used through `ItemStorage.SIDED` follow aborts,
nested commits and commits; fluids move in whole millibuckets only; NeoForge code fills a Fabric mod's fluid cell
in a slot, and Fabric code a NeoForge mod's, each item swapped only when the transaction commits. On 1.21.1, NeoForge
code fills and empties a Fabric mod's bag, the bag's own stack showing each change, and Fabric code a NeoForge mod's
pouch in a slot, an aborted transaction leaving it unchanged; a Fabric mod's sack that takes an item by becoming
another is refused it by NeoForge code. With Team Reborn's library
(fetched for the server run with Fabric API where Modrinth has a build, of its own or nested in Tech Reborn's or
Modern Industrialization's jar): NeoForge code charges a Fabric mod's energy block and Fabric code a NeoForge mod's,
an aborted transaction leaving each unchanged; the Fabric block's energy comes from a lookup fallback, as Tech
Reborn's machines' does.

### Fabric mods' fluids

NeoForge asks every fluid for its fluid type: to move entities in it, to let it meet lava, to name it in a tank, and
on 1.21.11 to draw it. A mod's fluid without one throws (`Mod fluids must override getFluidType`), and no Fabric fluid
has one, so the first entity touching it, neighbour changing next to it or NeoForge tank naming it crashed the game.
Fabricated gives each Fabric fluid family (a source and its flowing fluid) one type, registered under the source's
id, which answers from Fabric's side when asked:

| NeoForge asks | Answer |
|---|---|
| name, light, temperature, viscosity, lighter than air, bucket sounds | Fabric's `FluidVariantAttributes` for the source (its handler, or Fabric's defaults: the fluid block's name and light) |
| water-like, pushes entities, swimming, drowning, putting out fire, hydrating farmland, boats | yes for a fluid tagged `minecraft:water`, no for any other, as vanilla decides by that tag |
| fall distance | reset in a water-tagged fluid, kept in any other |
| path types | vanilla's, which go by the same tags |

A fluid that isn't water to entities leaves them alone, as on Fabric: a living entity in it moves as through air
(NeoForge would move it with the fluid's own movement, which a type leaves to its mod: none on 26.2 and later, so the
entity would hang where it is, and lava's on 1.21.11), and an item falls through it.

- Not like vanilla: a fluid tagged `minecraft:lava` isn't lava to entities (NeoForge keys burning and lava's movement
  to its own lava type), and an entity jumping inside a fluid that isn't water rises a little, as NeoForge lets it in
  any mod fluid.
- On 1.21.11, NeoForge's renderer and `FluidVariantRendering` take a fluid's textures and tint from its type's client
  extensions; for a Fabric fluid those are its `fabric-rendering-fluids-v1` render handler's, and water's without one
  (as vanilla draws such a fluid). On 26.x vanilla draws fluids from fluid models, and the type plays no part.
- Fabric's `FluidFlowEvents.ALLOW` (26.2+) is asked for Fabric fluids too, now that they have types to hook.
- A fluid registered after the registry events (from a client initializer, say) gets an unregistered type when first
  asked, without client extensions.

CI checks, on every version: two test fluids, one tagged `minecraft:water`; a family's source and flowing fluid
share one type, registered under the source's id, with the block's light and name, water-like only for the tagged
one; a placed fluid block asks NeoForge's fluid interactions about itself without crashing; a pig falls through a
column of the untagged fluid as through air.

### Native `fabric-menu-api-v1` (`fabric-screen-handler-api-v1` on 1.21.11)

NeoForge replaced the `openMenu` upstream's mixins target with its own, which already lets a menu provider send
extra data to the client. Fabricated ships upstream's API and maps it onto that:

| Fabric API | NeoForge |
|---|---|
| `ExtendedMenuProvider.getScreenOpeningData` | written where NeoForge asks the provider for its client data (`writeClientSideData`), so the menu opens through NeoForge's extra-data packet |
| `ExtendedMenuType` creating the client menu from that data | NeoForge's `MenuType.create(id, inventory, data)` on the client |
| `FabricMenuProvider.shouldCloseCurrentScreen` | combined with NeoForge's `shouldTriggerClientSideContainerClosingOnOpen` |
| a `SimpleMenuProvider` around an `ExtendedMenuProvider` | opens the extended menu, as upstream |

Fabric mods' menus therefore open the same way NeoForge mods' menus do. The client CI run opens an extended
menu with opening data from the integrated server.

### `fabric-rendering-v1`, `fabric-screen-api-v1` and `fabric-sound-api-v1`

Upstream's module loads, without the mixins that target code NeoForge rewrote:

- **HUD** (`HudElementRegistry`): NeoForge draws the HUD as layers, so upstream's HUD mixin can't apply.
  Fabricated wraps each of NeoForge's vanilla layers with the Fabric element list of the same vanilla element, so
  elements attached before or after a vanilla element, replaced or removed, behave as on Fabric, but for one gap: an
  element attached to the experience level, the held item's name or the spectator tooltip is drawn even in game modes
  where vanilla doesn't draw that element (HUD-3). Its wrappers
  run after NeoForge mods registered their layers, so the Fabric elements surround whatever those mods did to the
  vanilla layers. On 1.21.11 the deprecated `HudRenderCallback` fires from NeoForge's `RenderGuiEvent.Post`, at the
  end of the HUD as upstream does.
- **Block colors** (1.21.11): NeoForge keeps block color providers in a map by block instead of vanilla's
  id-indexed one; `ColorProviderRegistry.BLOCK.get` reads NeoForge's map.
- **Picture-in-picture renderers** (`PictureInPictureRendererRegistry`): NeoForge rebuilt the GUI renderer around
  its own renderer pools. On 26.2 and 26.3, Fabricated registers the renderers Fabric mods registered with NeoForge,
  which creates and pools them as it does its own. On 1.21.11 and 26.1.2, Fabric's renderers need the game
  renderer's submit queue, which doesn't exist yet when NeoForge gathers its renderers, so they are not used.
- **Debug options screen**: keeps vanilla's sorting and search instead of upstream's namespace-aware ones.
- **Render pipeline snippets** (1.21.11, 26.1.2, 26.2): NeoForge added a field to them, so they don't carry Fabric's
  "use the pipeline's draw mode in GUIs" flag; pipelines still do.

The rest of the module (world rendering events, block and item colors, entity and block entity renderers, model
layers, tooltip components, render pipelines and so on) is upstream's code, unchanged.

`fabric-screen-api-v1` (all versions) loads without upstream's wrapper around drawing a screen: Fabricated
fires `ScreenEvents.beforeExtract` and `afterExtract` (`beforeRender` and `afterRender` on 1.21.11) from NeoForge's
`ScreenEvent.Render.Pre` and `Post`, which NeoForge posts around the same call. They run right around the screen (after NeoForge mods' `Pre` listeners, and not when
one of them cancels drawing the screen; before their `Post` listeners). The module's other hooks (screen init,
removal, ticks, mouse and keyboard events) are upstream's.

`fabric-rendering-fluids-v1` loads without the handler that changes which neighbouring blocks show a fluid's
overlay texture: NeoForge asks the block (`shouldDisplayFluidOverlay`) instead, so blocks marked transparent
through `FluidRenderingRegistry.setBlockTransparency` keep NeoForge's behavior. On 1.21.11, NeoForge also tints
fluids through their fluid type; a Fabric fluid render handler's `getFluidColor` replaces that tint's color (its
alpha stays NeoForge's). Custom fluid renderers and sprites work.

`fabric-sound-api-v1` (all versions) loads without upstream's redirect of the audio stream lookup, which NeoForge replaced with
its own `SoundInstance.getStream`. A sound instance whose class overrides `FabricSoundInstance.getAudioStream`
gets its stream from it there; other sounds keep NeoForge's path.

### `fabric-renderer-api-v1`, `fabric-renderer-indigo` and `fabric-model-loading-api-v1`

The renderer API loads with Indigo as its renderer, so meshes, quad emitters and Fabric's model types work. A block
whose model implements `emitQuads` itself (a Fabric model), or whose vanilla multipart or weighted model has a Fabric
model among its parts, is drawn by Fabric's renderer: in the world (into the chunk's buffers), as a moving block
(pushed by a piston, falling) and with the breaking overlay. Every other block keeps NeoForge's renderer, so NeoForge's
model data, tints, face culling and ambient occlusion per part still apply to it; upstream's hooks that send *every*
block through Fabric's renderer in those places are left out for that reason. A glowing moving block's outline is
drawn by Fabric's renderer too, into the outline's buffers (26.2 and 26.3; 26.1.2 draws no outline around moving
blocks). Other block outlines still come from a model's parts as NeoForge sees them. Where NeoForge asks whether a
block is translucent (the pass its selection outline is drawn in, and a moving block's), it gets the model's
context-free material flags, not Fabric's level-aware `materialFlags`, as upstream's redirects at vanilla's calls are
left out: a model translucent at some positions counts as translucent everywhere, which Fabric's contract allows. NeoForge already gives vanilla's models
`createGeometryKey` and `particleMaterial` with Fabric's signatures, so upstream's copies are left out, and
`BlockStateModel`, which then inherits both interfaces' defaults of each, declares NeoForge's (a default method
bridge, as for any added interface). On 26.1.2, where upstream's check of a mesh
model's sprites takes the parameters of vanilla's `SimpleModelWrapper.bake` (NeoForge moved the code into its own
overload), a mesh model using sprites from outside the block atlas is not rejected; on 26.2 and 26.3 the check
follows the code into NeoForge's overload. On 26.1.2, 26.2 and 26.3 the block overlay seen from inside a Fabric block
uses the block's plain particle sprite, not its level-aware one (PART-5).

A Fabric block model that throws while it is drawn (in a chunk section, as a moving block, with the breaking overlay,
as a block an entity carries, or while its parts are made from its quads) fails alone, on every version: that block is
drawn by NeoForge's renderer, from the parts the model gives it, as is every model of its class from then on, and the
failure is logged once and listed in the report under *Rendering*, naming the mod (FRAPI rule 8). The throw is put down
to the model whose code threw: a Fabric model wrapping another, as Continuity's wrap the models they are given, isn't
marked when the model it wraps throws, so its other blocks keep Fabric's renderer, and NeoForge's draws that block each
time it throws (FRAPI-7). CI checks a broken model alone, as a multipart model's part, inside a wrapper and, on 26.x, as
a minecart's block, on every version.

Model loading loads too: its plugins, model modifiers and extra models work, with the extra models added to
NeoForge's model discovery by Fabricated (NeoForge rewrote the code upstream hooks). Fabric's custom model formats
work next to NeoForge's: a model file with a `fabric:type` goes to the deserializer a Fabric mod registered with
`UnbakedModelDeserializer`, and a blockstate model with a `fabric:type` to the codec registered with
`CustomUnbakedBlockStateModel`, while NeoForge's own model loaders (`loader`) and blockstate model types (`type`, such as
`neoforge:composite`) keep working. Upstream's mixin that replaces the blockstate model codec is left out; Fabricated
adds Fabric's types to the codec NeoForge builds instead.

When Fabric's renderer draws a model that has no `emitQuads` of its own (a NeoForge model a Fabric model wraps, or
every model under a renderer that meshes the world through Fabric's renderer API), its parts come from NeoForge's
level-aware `collectParts`, so connected textures and other model-data models keep working, and quads keep NeoForge's
per-part and per-quad ambient occlusion (the defaults Forgified Fabric API has; upstream's ignore both).

On 1.21.11 the renderer API loads with Indigo too, and Fabric block models, alone or among the parts of a vanilla
multipart or weighted model, are drawn by Fabric's renderer in the chunk sections NeoForge compiles, as on 26.x; CI
checks both. Everywhere else NeoForge asks a model for its parts (its level-aware `collectParts`), a Fabric model gives
parts made from the quads it emits (since this pass): one part per render layer, each quad with the colours, normals,
light emission and ambient occlusion flag NeoForge's quads carry. So these see Fabric models:

- moving blocks (pushed by a piston, falling), the breaking overlay and blocks drawn as entities (`renderSingleBlock`:
  a minecart's contents, an enderman's block);
- NeoForge's translucent block outline pass, and other mods that draw blocks from their parts;
- renderer mods that mesh chunk sections themselves: Sodium for NeoForge (see *Renderer mods* below).

CI checks the parts (a NeoForge quad's baked colour kept through them), a falling block and the breaking overlay. A
model asked for its parts while it emits keeps the parts of its vanilla method, as does a caller passing air
(NeoForge's deprecated overloads). Also on 1.21.11:

- The renderer API's `emitQuads` defaults are NeoForge-aware, as on 26.x (since this pass): a NeoForge model inside a
  Fabric model gets its parts from NeoForge's level-aware `collectParts`, with NeoForge's ambient occlusion and its
  quads' baked colours and normals. CI checks the level and the colours.
- Upstream's mixins that NeoForge's 1.21.11 build conflicts with are left out (see the module's plan): vanilla models'
  `createGeometryKey` is NeoForge's, a mesh model using sprites from outside the block atlas isn't rejected, and the
  in-wall screen overlay uses NeoForge's level-aware particle icon. The model baker's sprite getter gets Fabric's sprite
  finders from Fabricated, as upstream's mixin reads them from fields NeoForge's build names differently.

Model loading works as on 26.x: plugins, model modifiers, extra models and Fabric's model formats. Upstream's hook that
hands the baked extra models to the model manager can't apply to NeoForge's 1.21.11 build (it looks for a local Mixin
doesn't find there), so Fabricated hands them over from NeoForge's bake event call.

### Renderer mods: Sodium and Iris

Both builds of Sodium and Iris load: their NeoForge builds, and on NeoForge their Fabric builds too (below). Where a
NeoForge build exists, prefer it: Sodium's Fabric build meshes NeoForge's models, fluids and lighting the Fabric way.
The mixed pairs are refused even with `loadUnsupported`: Iris's Fabric build next to Sodium for NeoForge, and Sodium's
Fabric build next to Iris for NeoForge, as each hooks classes only its own partner build has (their fluid renderers).

**Their NeoForge builds** (26.x). Sodium for NeoForge carries its own implementation of Fabric's renderer API and switches
it on whenever Fabric API's renderer module is loaded, which Fabricated with the Fabric API jar provides. It then meshes
every block through that renderer, so Fabric block models show in the world. Fabricated's part:

- It sees Sodium, whose mod is nested inside a service jar, and reads its mod properties, so Fabric API's own renderer
  (Indigo) stands down for Sodium's, and Fabric mods depending on `sodium` or on `indium` (which Sodium provides) load.
- Its renderer API defaults keep NeoForge models' level, model data and ambient occlusion when Sodium draws them
  through Fabric's renderer (connected textures and other model-data models keep working).
- Moving blocks and the breaking overlay of Fabric models go through the registered renderer, Sodium's.
- A Fabric mod's Sodium integration is given to Sodium for NeoForge in the form it reads: its page in Sodium's options
  (the `sodium:config_api_user` entrypoint, a plain class) and its overrides of Sodium's mixin options
  (`sodium:options`, see *Custom values* below).

CI runs Sodium and Iris for NeoForge (from Modrinth) with Fabric API and a Fabric model test mod on 26.1.2, 26.2 and
26.3, and checks each of these, as gates (the run's own smoke checks too since v0.6.0-beta); a second run with CI's
shader pack is a gate since v0.6.0-beta as well. A Fabric addon's mixins into Sodium are checked
against the installed Sodium for NeoForge before the game starts, like mixins into the game: one that needs Sodium's
Fabric-only classes, or members its NeoForge build lacks, is reported and the addon disabled instead of crashing.

On 1.21.11 Sodium for NeoForge meshes chunks itself and has no Fabric renderer, though its metadata says it contains
one (`fabric-renderer-api-v1:contains_renderer`, read in its 1.21.11 source), so Indigo stood down and Fabric's
renderer API had no renderer: Fabric block models stayed invisible and a Fabric mod building a mesh would fail. Once
the client initializers ran, Fabricated registers Indigo's renderer when none registered (on every version; Indigo's
own hooks stay off then), and Sodium draws a Fabric block model from the parts Fabricated makes from its quads.
CI's 1.21.11 run with Sodium for NeoForge, a gate since v0.6.0-beta, sees the test model and the multipart part with it
meshed in Sodium's own section level (first in run 374; run 372, without the fallback, had no parts).

**Their Fabric builds** (NeoForge and Forge, every version; on Forge, *Forge* below).
Sodium's Fabric build loads by default, and Iris's with it. CI runs them with Fabric API and the renderer test mod on
every version Modrinth has them for, as a gate: the client reaches a world, every renderer check passes under them,
and Sodium's renderer draws the Fabric block models. On NeoForge the same client with CI's minimal shader pack is a
gate too since v0.6.0-beta, as is the NeoForge builds' on 26.x: every smoke check passes and Iris uses the pack.
On 1.21.1 Iris's particle hook, a `@Redirect` of a read of `ParticleEngine.RENDER_ORDER` in `render`, would have
nothing to redirect: NeoForge 21.1's `render` goes over the keys of its particles' map instead, as Forge 47's does, so
pre-flight points the hook at that loop (IRIS-1).
What Fabricated changed for them: pre-flight moves Sodium's quad hook to the constructor NeoForge 21.1's baking calls
(CTOR-5), keeps Fabric API's fluid sprite hook where a sibling hook fails (STATE-1, which Sodium's water needs), pins
Sodium's hook to the overload it takes beside Indigo's overwrite (OVERLOAD-2), maps Iris's access widener entries for
`GlStateManager`'s nested classes (TOKEN-1) and lets Iris's accessor replace Sodium's own method, as on Fabric.

Their gaps, which Sodium for NeoForge's own platform code answers and its Fabric build answers the Fabric way (no patch
for one mod; the gaps are listed):

- Fluids are drawn through Fabric API's fluid render handlers, not NeoForge's fluid type extensions, so a NeoForge
  mod's custom fluid rendering is not used (Fabricated has no bridge; a candidate in `roadmap.md`, *Not in this pass*).
- Geometry NeoForge mods add to chunk sections (`AddSectionGeometryEvent`) isn't drawn.
- NeoForge's model data, position-aware light emission, `hidesNeighborFace` face culling and baked colours in
  immediate mode are not applied to what Sodium's Fabric build meshes.
- None of these is checked in CI with a NeoForge mod in view (UNVERIFIED how each looks).

### Plugins for recipe viewers, tooltip mods and Simple Voice Chat

A Fabric mod's plugins for JEI, REI, Jade, EMI and Simple Voice Chat are its entrypoints (`jei_mod_plugin`,
`rei_common`, `rei_client`, `rei_server`, `jade`, `emi`, `voicechat`); their NeoForge and Forge builds look for an
annotation on the plugin class instead. Fabricated adds that annotation to the mod's scan data for each such entrypoint
(loader/common's `PluginAnnotations`: on NeoForge as its language loader gets the mod, on Forge as Fabricated's container
for it is made, both before any mod is constructed), so those builds find the plugins. WTHIT needs nothing: on NeoForge
and Forge it reads the jar's `waila_plugins.json` / `wthit_plugins.json`, which stay in the translated mod. Jade's per-mod settings (the `jade` custom value) reach Jade for NeoForge as a mod property (see
*Custom values* below). Which entrypoint becomes which annotation, or which mod property, is data: core's
`interop.json`, one line per entrypoint key.

- The NeoForge builds create a plugin from a class with a public no-argument constructor, as Fabric's default
  language adapter does with a class name. A plugin given as a field or method reference (`Class::member`) or through
  another language adapter (Kotlin's `object`) is left out, with a line in the report.
- JEI and REI create plugins while NeoForge constructs mods, before Fabric `main` entrypoints run (on Fabric JEI does
  it after them); EMI and Jade create a plugin again on each reload.
- A plugin that calls a viewer's Fabric-only API (`mezz.jei.api.fabric`, EMI's `FabricEmiStack`) fails at that call.
- CI runs the NeoForge builds of JEI, REI, Jade, WTHIT and Simple Voice Chat, and on Forge 61 to 66 the Forge builds
  of WTHIT and Simple Voice Chat (from Modrinth, where one exists for the version: JEI, REI and Jade have no Forge build
  for these versions there), with the test mod's plugins, as a gate: each must create and call its plugin. EMI has no
  NeoForge or Forge build for these versions (UNVERIFIED), so only its mapping is unit-tested.

### Custom values

Libraries built for both loaders read a mod's settings from its `fabric.mod.json` custom values on Fabric, and from its
`mods.toml` on NeoForge, under the same name. Every custom value of a Fabric mod becomes a mod property of the
translated mod (`[modproperties.<id>]`, where Jade for NeoForge reads `jade`), and every namespaced one (`ns:key`) is
also a key of the file and of the mod's `[[mods]]` entry: Sodium for NeoForge reads `sodium:options` from the entry,
Lithium for NeoForge `lithium:options` from the file. No key of NeoForge's own has a colon, so these never clash with
it. Nulls are left out, as TOML has none. Unit tests read the generated file with night-config, the parser NeoForge
uses, and find each value where those mods look.

### Fabric builds Fabricated refuses, and those it lifted the refusal of

Some mods' Fabric builds are refused on a loader where they can't run, or aren't verified yet; these facts are data
(core's `compat-plan.json`). Such a build is disabled with the reason and what to do instead, and the mods that need it
with it; `loadUnsupported` loads it anyway, for testing. A refusal is lifted on a loader once CI's run of that mod is a
gate there: it is lifted for Sodium, Iris, Essential, malilib and Flashback,
on NeoForge and Forge.

A build for another Minecraft version that Fabricated can tell can't run is disabled too, with the build to install:
on 26.x, one whose mixins name intermediary classes (`net.minecraft.class_1234`), the names obfuscated versions (1.21.11
and older) run Fabric mods with, which 26.x doesn't have (OBF-1: Modrinth lists some 1.21.11 builds for 26.x too).

- **Sodium and Iris**: loaded on NeoForge and Forge, with the gaps in *Renderer mods* above.
- **Essential** (`essential-container`, and `essential-loader`, which partner mods nest): its Fabric build is a loader.
  As the game starts it downloads Essential into the game directory's `essential` folder and adds it to Fabric
  Loader's internal mod list, class loader and Mixin's processor (read in Essential Loader's source), none of which
  exist on NeoForge, and by then NeoForge takes no more mods. On NeoForge Fabricated loads the newest jar it
  downloaded, from the next launch on, through the same checks as the mods folder's (DOWNLOAD-1): the first launch
  runs without Essential, the second with it. Essential's loader then finds Essential among the mods and lets it load
  as a regular mod, so Essential's automatic updates don't run; a jar older than 30 days is reported with a loading
  warning (update it by deleting it, or with Essential's own build where one exists). Pre-flight reads Essential's
  relocated MixinExtras as MixinExtras' own (MIXEXT-4). Loaded on NeoForge and Forge the same way, CI gating both
  launches on every version of both.
- **malilib** (and with it Tweakeroo, MiniHUD and Litematica): it adds its picture-in-picture renderers through a map
  NeoForge replaced with renderer pools on every version, which Fabricated restores (PIP-1). Loaded on NeoForge and
  Forge, the four gated on every version of both.
- **WTHIT**: its Fabric build refuses NeoForge itself; its NeoForge build is the one to use. Refused everywhere.
- **Flashback**: its mixin into `MappedRegistry.freeze` starts Flashback on the first registry freeze, and its start-up
  registers a key binding through the client. NeoForge freezes the registries at bootstrap and again before the client
  exists, where Fabric API delays the freeze until after the initializers; Fabricated holds a Fabric mod's freeze
  handlers in every freeze before the main entrypoints (REG-8). Loaded on NeoForge and Forge (Forge 61 to 66: Modrinth
  has no Fabric build for 1.20.1), gated on every version.

### Mods that bring Fabric API or Fabric Loader

NeoForge loads each package from one module only, so a package two mods both carry stops the game before any mod runs
(a `ResolutionException`). Some NeoForge mods carry Fabric API's or Fabric Loader's packages: Forgified Fabric API
(Sinytra's port, one NeoForge mod per Fabric API module, with Forgified Fabric Loader nested), and mods that bundle
some of its modules (Sodium for NeoForge). Fabricated now shares the game with them, by one rule for each:

- **A Fabric API module another mod provides is that mod's.** A NeoForge mod provides a module when its id is the
  module's (`fabric_screen_handler_api_v1`), when its `mods.toml` lists the module under `provides`, or when its jar
  has one of the module's packages. For a native module, Fabricated's own copy stands down: its packages leave the
  in-game mod, and so do the mixins and mixin configs serving it (`META-INF/fabricated/mixin-modules.json`) and its
  NeoForge bridges, so no event fires twice. So do Fabricated's hooks for a module it passes through from Fabric API's
  jar (on Forge 47, the fuel and brewing hooks of fabric-content-registries-v0), and a module's mixins in a config it
  shares with a module the provider lacks stand down alone (PROVIDER-13). For a module loaded from Fabric API's jar, the jar's copy is left out
  (the platform's copy of a package is the one used, as for any Fabric jar). Fabric mods then use the provider's
  module, whose ids and versions satisfy their dependencies; the report lists it under *Fabric API modules provided by
  other mods*, the API as `PROVIDED_BY_MOD` (API 1.1.0), and the parts left out under *Fabric API providers*.
  (PROVIDER-1)
- **Fabric Loader's API is Fabricated's, always.** Another jar's copy of `net.fabricmc.loader` and `net.fabricmc.api`
  (Forgified Fabric Loader) is left out of it as NeoForge reads it, with the service files that name its classes; the
  rest of the jar loads as it would. Mods built against that copy use Fabric Loader's public API, which Fabricated
  implements, and see Fabricated's mod list, NeoForge mods included. On NeoForge 21.11 and 26.x, NeoForge is handed a
  copy of such a jar without those packages, under `.fabricated/loader-api/` (the original is never changed). The
  report lists each such jar under *Fabric Loader API*. (PROVIDER-2)
- **Kotlin:** with Kotlin for Forge installed, its Kotlin is the one loaded, and the copy Fabric Language Kotlin
  bundles is left out the same way, package by package (Kotlin for Forge's Kotlin is on the launcher's plugin layer on
  NeoForge 21.1, in FML's plugin loader on 21.11 and 26.x), so each Kotlin package has one provider; CI joins a world
  with both. A Fabric mod built
  against a newer Kotlin than Kotlin for Forge ships gets a linkage warning naming what it lacks.

Fabric API keeps `net.fabricmc.fabric.api.util` in two modules (`fabric-api-base` and
`fabric-content-registries-v0`); a module layer allows one, and Forgified Fabric API puts it in `fabric-api-base`, so
Fabricated counts it there too. A provider's module is used as it is: what it lacks, a Fabric mod's linkage warnings
name, and what it does differently is its own. Forgified Fabric API's networking refuses a payload in the `minecraft`
namespace, as NeoForge does, where Fabricated's own networking takes it (NET-4): with it installed, a Fabric mod
registering one fails its initializer and the report marks it failed (the test mod's payload on 1.21.1; BetterEnd's
ritual update UNVERIFIED, PROVIDER-5). On 26.x, its item API throws when NeoForge asks the equipment slot of an item
with Fabric's equipment slot provider, which stops the game (PROVIDER-7). On 1.21.1, read in its source (PROVIDER-8):
its block API's trapdoor climbing tag has no effect (its hook is in vanilla's `trapdoorUsableAsLadder`, which NeoForge's
ladder check no longer calls); an attack block callback returning `FAIL` stops the block's attack but not its
breaking, where upstream cancels the left click (a creative player still breaks the block); a Fabric mod's sculk
sensor frequency has no effect (it writes vanilla's table, NeoForge reads its data map); and a built-in resource pack a
Fabric mod enables by default is offered but not selected (selected as the options load, which NeoForge 21.1 does
before mods register their packs). On 26.1.2 and 26.2, read in its sources (PROVIDER-10), the trapdoor tag, the attack
block result and a vibration frequency do the same; its transfer bridge gives a Fabric fluid storage a NeoForge fluid
handler on block entities only, its combined item providers answer nothing while NeoForge asks an item for its fluid
handler (its own lock against recursion), and its view of a NeoForge item's fluid handler answers an insert in
NeoForge's millibuckets, not Fabric's droplets; and a Fabric biome modification changes the biome on the server but
leaves it marked as the known pack's, so clients read their own copy without the change (a sky colour, say). On 26.1.2 and 26.2
too (PROVIDER-17), its packet context holds nothing for a codec that reads it, as Polymer's do (its keys are null once
its context class is initialized first, the class initialization cycle NET-7 found in upstream's code), and none is
bound as NeoForge's splitter decodes a payload it reassembled; and its HUD registry draws an element a Fabric mod
attaches to a vanilla one as a layer of its own, whatever vanilla's layer checks inside (after chat with the GUI
hidden, after the experience level without experience to show). Of the bridges to NeoForge Fabricated's own modules make, it has fewer: NeoForge's
`supportsEnchantment` doesn't ask Fabric's enchanting event (the enchanting table, anvil, `/enchant` and loot do, as
upstream), NeoForge's `getCreatorModId` isn't answered from Fabric's `getCreatorNamespace`, and its transfer bridge
connects Fabric's and NeoForge's storages on block entities only, not a Fabric item's fluid storage to NeoForge, and
fills a NeoForge item's fluid handler without writing the filled item back to its slot. Its view of a NeoForge fluid
handler moves the fluid when a nested transaction commits, even if the outer one is then aborted. Mods read a Fabric mod's files through its
container as NeoForge reads them, with the NeoForge sections translation leaves out of its `pack.mcmeta` and data files
(PROVIDER-3). A Fabric mod's initializer creates its registries with Forgified Fabric API's `FabricRegistryBuilder` as
with Fabricated's: NeoForge's check that refuses a registry after its new-registry event, where Fabricated runs Fabric
initializers, lets it through while a Fabric entrypoint runs (PROVIDER-6). A data-pack registry a Fabric mod's
initializer makes with its `DynamicRegistries` on 1.21.1, which it hands NeoForge only in an event that fired before
Fabric initializers run, is handed to NeoForge once they have run, with the registries it named in that event
(PROVIDER-11). A command argument type a Fabric mod's initializer registers through its command API, which registers
them only in NeoForge's registration event for argument types, is registered on a client once the initializers have run
there, after that event, as its listener registers them (on a dedicated server they run before it; PROVIDER-12).
Likewise a default attribute modification a Fabric mod's initializer registers (`FabricDefaultAttributeRegistry.MODIFY`),
which its object builder applies once, in NeoForge's attribute modification event before a client's initializers run,
is applied on a client once they have run, with its own context (PROVIDER-18).
Forgified Fabric API keeps no registry attributes in `RegistryAttributeHolder`, and on 1.21.1 has no registry aliases. In a development workspace the in-game mod's own copies can't be left out; the report says so.

**Sinytra Connector beside Fabricated is refused**, as is a second Fabricated jar: the launch stops with an error naming
the jars, and Fabricated loads no Fabric mod (two loaders of Fabric mods can't share them; of two Fabricated jars, the
platform would run one by load order, DUP-1). The other Fabricated jars aren't loaded, so the error is what stops the
launch, not a module error (DUP-2). Remove one of them from the mods folder.

CI checks this on every version with stand-ins built from Fabricated's own jar (`ci/neoforge/standins.py`: a module
provided straight and nested, a copy of Fabric Loader's API with its language loader service, two versions of a nested
library; every check gates); with the newest Forgified Fabric API, Kotlin for Forge and Fabric Language Kotlin from
Modrinth on a server with the test mods (the server must start; with Forgified Fabric API's resource loader it must
take the test mod's built-in pack, and with its registry sync the test mod's initializer must make its registry); with
Forgified Fabric API and Sodium on a 1.21.1 client (a gate since v0.6.0-beta: it reaches its world, and no module layer
refuses a package the two share); and with a stand-in for Connector and a second
Fabricated jar, each of which must stop the launch with Fabricated's error and no module error. Every server and client
run also makes a registry the way Forgified Fabric API's builder does, in a Fabric initializer (PROVIDER-6).

### Fabric Loader

Fabricated reports itself as Fabric Loader 0.20.1, whose public API is 0.19.5's, unchanged (since this pass; it
reported 0.19.5, so mods declaring a newer loader were refused). The public `net.fabricmc.loader.api` is complete: entrypoints (including custom ones and language adapters such as
Kotlin's), mod containers and metadata, versions and predicates, the object share, and the mapping resolver
(intermediary to Mojang names on 1.21.11). As on Fabric, every declared language adapter is created before any Fabric
mod's mixin plugin loads (some mods declare one only to run code that early). `preLaunch` entrypoints run later than
on Fabric: first of the entrypoints, right before `main`, where NeoForge has built the mods' containers (Fabric runs
them before the game's classes load). That stays so for the beta: Sodium's driver checks there would come
after NeoForge's early window made its GL context either way.

Every mod has a root path, as on Fabric (mods take the first without checking): Minecraft's is the game's file, Java's
its home, the mods Fabricated provides (Fabric Loader, the native Fabric API modules) have Fabricated's in-game mod,
MixinExtras has NeoForge's jar, and a NeoForge mod nested in another jar has that jar.

- While Fabric entrypoints run, the thread's context class loader finds a Fabric mod's directories (a package's,
  as a `jar:file:` URL into its jar), as Fabric Loader's class loader does; NeoForge's finds only files. Mods that
  find their classes by scanning a package (Reflections, which YUNG's API uses for what it registers) found nothing
  without it (SCAN-1). A scan outside an entrypoint, on another thread or later, still finds nothing.
- A file read through one of the mod's classes (`Class.getResourceAsStream`, `Class.getResource`) is looked up in the
  mod's own jar first and then, as Fabric's unnamed modules do, on the whole class path: NeoForge's module for the mod
  has only its own files, so a mod reading Minecraft's `/version.json` that way (Lattice, while Mixin prepares its
  config) stopped the game (RES-1). The mod's calls go through Fabricated's `ClassResources`.
- `ResourceBundle.getBundle` with a `ResourceBundle.Control`, which Java refuses from a named module, makes the same
  lookup through Fabricated's `ResourceBundles` (LuckPerms' translations, LOADER-3). Bundles loaded so are not cached
  between calls, as the JDK's are.
- On a client, `main` and `client` entrypoints run where Fabric Loader runs them: in `Minecraft`'s constructor, once
  the instance exists, with the registries open (CORPUS-16). NeoForge registers content before `Minecraft` exists, so
  what it fills once its registration events end takes what Fabric mods registered there too: default attributes,
  spawn placements, fluid types, the creative tabs' order, Forge Config API Port configs (loaded as they are
  registered, as the port does on Fabric) and tooltip providers (placed before or after all component tooltips). A
  NeoForge mod that reads the registries during NeoForge's own registration events doesn't see Fabric mods' content
  on a client. On a dedicated server `main` and `server` entrypoints run while NeoForge registers content, as before.
- A hook after Fabric Loader's own `Hooks.startServer`, the call its launcher adds to the dedicated server's
  `Main.main` where mods' entrypoints run, is pointed at the call where the platform loads mods, where Fabricated runs
  them: NeoForge's `ServerModLoader.load` and, on every Forge version, Forge's (HOOK-1, HOOK-4). owo-lib's registry
  freeze runs once every mod's `main` and `server` entrypoints have, as on Fabric. Its client hook, after
  `Hooks.startClient`, isn't pointed anywhere: Fabricated runs the client entrypoints from a hook of its own, which a
  hook at the same call would come before.
- A Fabric payload type registered after NeoForge's payload registration (MiniHUD's, from the end of `Minecraft`'s
  constructor; Ledger's, as the server starts) is registered with NeoForge then, as upstream accepts it at any time
  (NET-3). An id a NeoForge mod already registered stays the NeoForge mod's.
- `@Environment` and `@EnvironmentInterface` are applied as Fabric Loader applies them: members and interfaces for
  the other side are stripped, so a common class implementing a client-only interface loads on a dedicated server.
  The stripping is done as the mod is translated for its side, with the classes' frames as they were (ENV-1): at
  class load, ModLauncher would compute them anew, which fails for a class naming an optional mod's that isn't
  installed.
- Of Fabric Loader's internals, `FabricLoaderImpl.INSTANCE` (its public API methods) and
  `FabricLauncherBase.getLauncher()` (environment, development flag, runtime namespace) are provided for the mods that
  use them. A jar a mod adds to the class path while running (`addToClassPath`, or the legacy launcher's `propose`,
  through which LuckPerms adds the libraries it downloads) is found for the classes no mod's module has, untransformed
  (LOADER-2), after the classes the game finds there without it: those of the packages the launcher's libraries don't
  export, such as Gson's `com.google.gson.internal`, through which vanilla reads a player's stats (GSON-1).
- A Fabric jar's copy of a package the platform already has is left out, and the platform's copy is used: NeoForge's
  and Minecraft's, the launcher's libraries' (Gson, Guava, checker-qual), every module layer the game reads (on
  NeoForge 21.1 the launcher's boot, service and plugin layers; on 21.11 and 26.x the JVM's boot layer), unexported
  packages included, and the libraries NeoForge mods nest, which FML 10 and later load in their plugin loader when a
  language provider brings them (PKG-5). The report lists each under *Package guard*. Mods can add entrypoints to Fabric Loader's internal storage as on Fabric
  (LOADER-4): `FabricLoaderImpl`'s `entrypointStorage` and `adapterMap` fields, which they read by reflection, work, and
  every container is Fabric Loader's `ModContainerImpl`, which mods test for first; what they add is returned by
  `FabricLoader.getEntrypoints` after the entrypoints the mods' metadata declares (FrozenLib's `frozenlib.json`
  entrypoints, among them the listeners of its events). `ModContainerImpl`'s own internal methods are not provided.
  Other internal classes are not provided. A mod's code that reaches for another one, or for a member of Fabric
  Loader's API that Fabricated lacks, is listed among the report's linkage warnings before the game starts.
- A registry a mod creates through vanilla's own code (`BuiltInRegistries`) before NeoForge's registration has ended
  joins the root registry, which NeoForge froze after its new-registry event, as Fabric API's `FabricRegistryBuilder`
  registries do; an entry a mod registers in a vanilla registry before NeoForge's registration events is accepted, as
  on Fabric (REG-3: Wover, BetterX's library).
- A mod whose required mixin (in a required config without a plugin) calls into a Fabric mod that isn't loading is
  disabled, with the reason: Mixin would stop the game looking for that mod's class while applying it. Such a mixin in
  another config (not required, or with a plugin) is left out instead, and the mod loads without it: Mixin would stop
  the game the same way unless the plugin skipped it (OneConfig's chat mixin, which needs adventure-platform-fabric,
  CORPUS-47). The report lists it under *Mixins left out (they need a Fabric mod that isn't loading)*.
- A mod one of whose classes extends or implements a class of a mod it nests that isn't loading is disabled, with the
  reason: the game can't define that class, and on Fabric the nested mod is always there (CORPUS-89: Modern
  Industrialization's items extend Magna's tools). It loads once the nested mod does. A mod nesting one that isn't
  loading without extending its classes loads, and so does one extending a class of a mod it doesn't nest (an optional
  integration, which only uses it when that mod is there).
- Mods detect Fabricated with `FabricLoader.getInstance().isModLoaded("fabricated")`; see [API.md](API.md).
- `mixinextras` is provided at the version NeoForge runs, as Fabric Loader provides its own, so mods requiring it
  load (copies nested in mod jars are dropped for NeoForge's).
- Forge Config API Port (a copy of NeoForge's config system for Fabric, which many mods use) is replaced by its
  Fabric API on NeoForge's own config system: `ConfigRegistry` registers with the mod's NeoForge container under the
  port's file names, `ModConfigEvents` fire from NeoForge's config events, and `ConfigScreenFactoryRegistry` screens
  open from NeoForge's config button. As on Fabric, a config other than the per-world one can be read as soon as it is
  registered (from its file, before NeoForge loads it). The port's jar is skipped (and provided at its version); its
  copy of Forge's own config API (`ForgeConfigSpec`) is not provided. On 26.3, whose FML renamed the config types `COMMON` and `SERVER` to
  `LOCAL` and `SYNCED` (the port keeps the old names), a mod's references to the old names are renamed. The port's `ConfigurationScreen` takes a mod id where NeoForge's takes the mod's container, so a mod creating one passes its container instead.
- Class tweakers v2 load: their `extend-enum` lines only declare the constants a mod's enum mixins add, which Mixin
  adds itself (0.17.1 or later, so on 26.x; not to NeoForge's own extensible enums).
- An enum mixin that adds constants to one of NeoForge's extensible enums (which NeoForge lets only its own enum
  extensions change: FML refuses an enum mixin into one) becomes an enum extension NeoForge's loader applies, when each
  constant's name starts with the
  mod's id (Farmer's Delight's recipe book type and categories). Its other methods, such as an injector into the enum,
  stay an ordinary mixin without the constants (CORPUS-65), when they use nothing of the enum mixin but its constants
  and its own methods. Constant arguments are written into the extension; arguments the
  mixin computes when the class loads (a registered object, a static array) come from a method Fabricated generates
  next to the mod's mixins, which runs the mixin's code for them when NeoForge adds the constant (ENUM-3). Where
  NeoForge reserves the mixin's constructor for one taking suppliers (`Raid.RaiderType`, which Friends and Foes extends
  with its raiders), the values are handed over in suppliers, of the type the constructor's signature names; an array
  where it takes a supplier of a list (`RecipeBookCategories`' item icons) as a list of its elements. Code that branches, uses local variables, or reads the
  mixin's or the enum's own members isn't moved, and its enum mixin disables the mod, with the reason, as other enum
  mixins into those enums do.
- An enum mixin into one of NeoForge's extensible enums that the enum extension file can't carry (a constant named
  otherwise than the mod's id and a colon, or with a body of its own) becomes an ordinary mixin into the enum's class
  initializer that makes the constants with the enum's constructor and appends them to its values, as mods' own NeoForge
  builds do (ENUM-5). A body overriding the method the enum forwards to a delegate (`GrassColorModifier`'s
  `ColorModifier`, which Wilder Wild's tundra grass colour overrides) becomes a delegate class running it. Code that
  branches or uses the mixin isn't moved; such a mixin still disables its mod. A mixin can't add the constants as
  fields of the enum, so the mod's classes that read them so read them from a holder class generated next to its
  mixins, which takes them from the enum's values by name (CORPUS-80: Farmer's Delight's recipe book type). Another
  mod reading them as fields still finds none. UNVERIFIED in the game.
- Where the running Mixin is older than 0.17.1, which applies enum mixins (NeoForge 21.1 and 21.11, and Forge), an
  enum mixin into any other enum becomes such an ordinary mixin too (ENUM-7: Friends and Foes' mob categories, read
  with `valueOf`); one that can't disables its mod, with the reason, where Mixin would stop the game on it. Into an
  enum Forge makes extensible (`MobCategory`), it makes the constants with the enum's own `create` factories, as
  Forge mods do, at the end of the enum's class initializer, once the map by name each factory registers its constant
  in is set. The enum's `init`, which each factory runs on the constant it makes, runs for the mod's constants only
  where it keeps to the enum, as `MobCategory`'s, which fills the map its codec reads. Fabric gives them no such hook,
  so the mixin skips it where it can't return (Forge 61 to 66's `RecipeBookType`, whose `init` throws "This is not
  implemented yet") or reaches another class (Forge 47's `RecipeBookType`, whose `init` runs `RecipeBookSettings`'
  class initializer early, before Farmer's Delight's mixin there can read its type; the mod adds its tags itself).
- Fabric ASM (Manningham Mills, mod id `mm`) adds enum constants that mods declare in their `mm:early_risers`, with
  `ClassTinkerers.enumBuilder(...).addEnum(...).build()`. Its Mixin plugin runs those early risers and serves the
  mixins it makes from Fabric's class loader. NeoForge's and Forge's class loaders take no added classes, so Mixin
  dropped the plugin's config, the constants were never made, and the mod failed where it looked them up
  (`ClassTinkerers.getEnum`). This happened to Farmer's Delight's recipe book types on Forge 47 (CORPUS-80).
  - **What Fabricated does instead:** it leaves Fabric ASM's config out and reads those calls from the early
    risers' code, without running it. It then adds the constants with enum mixins of its own, in a config it
    generates. Those mixins go the way the platform takes enum mixins above: NeoForge's enum extension file, or an
    ordinary mixin.
  - **What it reads:** strings, class literals, the primitive classes, boxed constants and arrays of them,
    `mapClassName` from intermediary names, string joins, and the side, whose branches it follows for this side.
  - **Arguments:** given as constants, or by a supplier. The supplier is one a static method of the mod returns, or
    a lambda capturing nothing. It runs as the enum is initialized, once per constant, as Fabric ASM's does, and an
    array of the wrong length fails with Fabric ASM's `IllegalArgumentException`.
  - **Not supported:** an early riser doing anything else adds nothing, and its mod gets a warning naming what
    wasn't read. That includes subclassed constants (`addEnumSubclass`), `ASM` `Type` parameters, and Fabric ASM's
    other tools: class definitions, transformations, replacements and added URLs. Its access transformer
    (`silky.at`) isn't applied either; a mod shipping one gets a warning.
  - UNVERIFIED in the game.
- An `@Intrinsic` method of a class mixin that only calls the superclass's method (Lithium's `setCachedState` of the
  chest, there for its `@Inject` to have a method to inject into) is dropped where the target already declares that
  method, as Forge 47's chest does: Mixin would reject the mixin, and the target's own method is what `@Intrinsic`
  keeps (CORPUS-69). Another `@Intrinsic` the target declares disables its mod, or leaves the mixin out where it can.
- Where the platform's Mixin looks up the class of every local variable in a mixin method's debug table and stops on
  one it can't find (SpongePowered's, which Forge runs, and Fabric's before 0.17.0, which NeoForge 21.1 and 21.11 run),
  a local of a class this side doesn't have leaves the table: owo's slot mixin keeps a client-only rectangle, which
  stopped the Forge 26.2 dedicated server. The table is debug information only (LVT-1).
- A mixin's `@Shadow` of a field another mod's mixin adds to the same class binds to that field on Fabric, whose Mixin
  finds the fields of the mixins it prepared before; SpongePowered's, which Forge runs, looks in the class's own fields
  only and stopped the game (MoreCulling's shadow of the mesh Fabric API's renderer mixin adds to
  `BlockModelRenderState`, on the Forge 26.x clients). There the shadow is declared as the other mixin declares the
  field, and Mixin merges the two into one field, which both mods' code uses, as on Fabric (CORPUS-74). One that can't
  be declared so (a static shadow of an instance field, or a writable shadow of a final one) disables its mod, or leaves
  the mixin out where it can.
- Two mixins that each add a `@Unique` field of the same name and type to one class keep a field each on Fabric, whose
  Mixin renames the later one. SpongePowered's Mixin, which Forge runs, merged the two into one field that both mixins'
  code used: SpruceUI's loading screen hook (in LambDynamicLights) cleared the screen Fabric API's hook was about to
  read, and the Forge 26.1.2 client stopped creating its world. There such a field (one that isn't public) is renamed in
  its mixin and the mixin's nested classes, so each mixin keeps its own, as on Fabric (CORPUS-95).
- A mixin's own field that its target class already declares is the target's once Mixin merges the mixin: Mixin adds
  a field only where the class has none of that name and type. Where the platform declares it final, the mixin's
  writes of it fail: Porting Lib adds `ItemTransform.rightRotation`, the field it ports from Forge, and its setter stopped
  the Forge 47 client loading its models, as Forge 47 declares the field final. Where the mixin writes the field outside
  its constructors, the field is declared as a writable shadow of the platform's, so Mixin makes that field writable and
  the mod's code works as on Fabric (CORPUS-101).
- Mixin finds a `@Shadow` method only among its target class's own methods, on every platform, and rejects the mixin
  otherwise. Where the platform moves a method vanilla declares into a supertype, a mixin's shadow of it is dropped and
  the mixin's calls of it reach the inherited method: Forge 47 moves `MultiPartBakedModel`'s `getQuads` into the default
  method of its `IDynamicBakedModel`, which MoreCulling shadows, and the Forge 47 client stopped baking its models
  (CORPUS-84). Only where every use of the shadow is a virtual call and the inherited method is an instance method that
  isn't private; otherwise the mod is disabled, or the mixin left out where it can.
- An `@Accessor` of a field the platform gave another type reads vanilla's value where Fabricated's in-game mod
  bridges the field: the accessor invokes a getter the in-game mod adds to the field's class, keeping its own name and
  descriptor, so the mod's callers are served as before. Forge 47 keeps holders in a brewing mix's `from` and `to`, and
  the particle engine's providers by their type's id, where Porting Lib's accessors read vanilla's potions and items
  and its map by raw id; the bridges give the holders' values, and a view by raw id of Forge's map (CORPUS-37). A
  retyped field Fabricated doesn't bridge still disables the accessor's mod, or leaves the mixin out where it can.
- Config screens a Fabric mod offers to Mod Menu (its `modmenu` entrypoint) open from the Config button of NeoForge's
  mod list. Mod Menu's API interfaces are provided, so such mods load without Mod Menu, which itself can't run on
  NeoForge (it would duplicate NeoForge's mod list): an installed Mod Menu jar is skipped and provided at its version.
- Where an interface a Fabric mod adds to a game class (with a mixin or a class tweaker) brings a default method the
  class already has from one of NeoForge's interfaces, with the same name and descriptor, the class declares that
  method, calling NeoForge's default, unless a mixin declares it (Fabricated's own do for some of Fabric API's). The JVM can't choose between two such defaults and throws
  (`IncompatibleClassChangeError: Conflicting default methods`, or on Java 21 `AbstractMethodError: Receiver class ...
  does not define or inherit an implementation`); Entity Culling's `getRenderBoundingBox` on block entity renderers
  was one, on 26.3 (1.11) and on 1.21.1, where Fabricated declares such methods since 0.6.0 (CORPUS-63). A default
  the class has through a superclass's interface counts too (since 0.6.0, DEFAULT-1): the method then calls the
  superclass's.
- A mixin's private method with the name and descriptor of a default method its target inherits from NeoForge's
  interfaces (`ILivingEntityExtension.self()` on `LivingEntity`) gets another name, with its calls in the mixin, and the
  report notes it (HIDE-1). Under its own name it hid the default from the target's subclasses, and NeoForge's calls of
  it on them threw (`AbstractMethodError`): Mixin renames such a `@Unique` method only where the target class declares
  the method itself. Fabric API's entity events module has one (`fabric-entity-events-v1`, native on NeoForge; passed
  through on the forge branch, where every mob in a fluid met it).
- `PlayerList.getPlayers()` follows the list in the field, as in vanilla. NeoForge returns an unmodifiable view made
  when the player list is constructed, so a mod whose mixin replaces the list (written for vanilla, where both are one
  object) would leave it on the old, empty list, and an integrated server finding no player stays paused. The view is
  still unmodifiable; Fabricated logs a warning once when it sees a replaced list.

### Mixins NeoForge's code can't take (LEAVE-1)

A Fabric mod's mixin can need something NeoForge's build of the game doesn't have: a call NeoForge replaced with its
own hook, a method whose signature NeoForge changed, a library class NeoForge loads without mixin support. Mixin would
stop the game on it, so pre-flight catches it before launch, and what happens next depends on whether the mod's own
code can do without that mixin:

- **A self-contained mixin** adds no interface to its target and has no accessor or invoker (an interface mixin, no
  abstract method either), so nothing outside it calls into it. The mod loads without the failing part: only the injectors that fail when the mixin adds no fields
  of its own (Balm's `LivingEntityMixin` keeps its damage, heal and effect events and loses the one hook into
  `removeAllEffects`, at a `Map.clear()` call NeoForge's code doesn't make there), or the whole mixin when it does, as its injectors may share those
  fields. The features the left-out part hooks in for don't work. The report lists each one under "Mixins left out
  (NeoForge's code differs where they hook in)", with what NeoForge's code lacks; the mod's row (and
  `/fabricated why <mod>`) gives the count, and the log names the mods at startup. A hook that is a known limit, which
  no update of the mod or of Fabricated changes, says so on its line, with what to do and its finding (SPAWN-1,
  SCREEN-2); an entrypoint failure that is one says so too, in the loading warnings as well (REG-13).
- **Any other mixin**: the mod's own code casts its target to the interface the mixin adds, or calls its accessors,
  so leaving the mixin out would only move the crash into the mod. The mod is disabled with the reason, as are the
  enum mixins NeoForge's extensible enums don't allow.
- In a config with a plugin, which may disable mixins itself, only the failing injector is left out. A mixin the
  plugin turns down itself, as Mixin asks it which to apply, as on Fabric, is listed apart under "Mixins their configs'
  plugins leave out (as on Fabric)" and not counted in the mod's row (LEAVE-4: Pehkui's version-specific mixins).
- Injectors of one mixin that hand state to each other through a `ThreadLocal` of the mod's, one storing into it and
  another using it, are left out together, as one left alone would act on a value the other never stored (ITEM-2:
  Fabric API's recipe remainder hooks on Forge 47 and 52). Injectors only reading one, or sharing a plain static field
  (a config's), stay apart.

A mod disabled this way can still be loaded without the mixin, by naming it in `skipMixins` in
`config/fabricated.json` (the report's suggestion gives the exact entry).

A hook at a private method of vanilla's that the platform replaced with one of its own, called in the place of
vanilla's one call of it, runs there (STRUCT-1): `StructureTemplate.placeEntities`, which NeoForge, Forge 47 and Forge
52 replaced with `addEntitiesToWorld(level, pos, settings)`, and Forge 61 to 66 with a `placeEntities` taking the
settings in the pivot's place, both running the structure processors on each entity. An `@Inject` at the method's call
moves to the platform's call, and one at its start to the platform's method (at its end too where the platform's
method returns where vanilla's does), its handler given vanilla's parameters as vanilla's call read them from the
settings: YUNG's API's and Friends and Foes' entity processing (their structures' armor stands and item frames) works
on every version. The same goes for methods a platform gave another parameter: on Forge 47
`HopperBlockEntity.ejectItems`, where Carpet's hopper counters and FrozenLib's ejection guard now hook its start, and
on Forge 61 to 66 `ServerExplosion.hurtEntities`, where Carpet's explosion event hooks after its call; a hook at their
end stays out where the platform's method returns otherwise (the hopper's returns first where Forge's insert hook took
the items). Other injectors there stay out, the report saying why: Porting Lib's port of the platform's own entity
processing, which the platform runs itself (Porting Lib's processors take the platform's `processEntity`), and Friends
and Foes' wrapper of a structure mob's spawn in the method's lambda on NeoForge and Forge 47, whose lambda takes the
settings where vanilla's takes what it reads from them: its spawn event doesn't fire for structure mobs there.

A mod's own call of a game constructor NeoForge replaced with one taking a name too, `LootPool`'s on every version
(BCLib's loot pool mixin makes its pools so), calls NeoForge's with no name, and its widener's line for vanilla's
constructor opens NeoForge's (LOOT-3). Any other call of a game constructor NeoForge lacks, a mixin's included, is
listed among the report's linkage warnings.

Vanilla's piston stickiness methods, `PistonStructureResolver.isSticky` and `canStickToEachOther`, which NeoForge 21.1,
21.11 and 26.1.2 replace with the block state's `isStickyBlock` and `canStickTo`, are put back where a Fabric mod's
mixin hooks one of them (an injector naming it, a shadow of it), answering as NeoForge's code does, so the mod's hooks
of them apply as on Fabric (SEAM-1: Carpet's, FrozenLib's and Moonlight's sticky blocks). A Fabric mod's mixin of the
resolver that hooks neither (Lithium's) leaves it as NeoForge has it. NeoForge's own sticky blocks
and slime and honey stick as before. NeoForge 26.2 and 26.3 keep both methods. The report notes each hook of one; on
26.1.2, where NeoForge's line check asks the first block twice, a resolver rewritten for such hooks asks both ways
there, as NeoForge 26.2 does, and one that isn't asks as NeoForge does. Carpet's hook keeping the line's positions finds
other locals in NeoForge 21.1 to 26.1.2, whose `addBlockLine` declares its old state before vanilla's position, so
Mixin skips it, and Carpet's check of the line, which reads only what it keeps, is left out (SEAM-2): its chains and
chests don't pull the blocks behind them along the push, and stick across it as its rules say.

Putting the methods back moves the resolver's `isStickyBlock` and `canStickTo` calls into them, so where a NeoForge
mod's mixin hooks those calls, or a place the move shifts in the methods holding them, they aren't put back: the
resolver stays as NeoForge has it, so the NeoForge mod's mixin applies, and the Fabric mods' hooks of the stickiness
methods find nothing, as without Fabricated's restoring them. Moonlight's NeoForge build (Supplementaries needs it) is one:
its `PistonStructureResolverMixin` wraps those calls. The report's environment then names the class and the NeoForge
mod's mixin ("Vanilla's methods not put back in PistonStructureResolver: NeoForge mod Moonlight Lib's mixin
PistonStructureResolverMixin hooks the platform's code that putting vanilla's method back would move"), and each Fabric
mod's hook of them has a line saying so. Carpet's and FrozenLib's sticky blocks then don't stick, as before. Where what a
NeoForge mod's mixin of the resolver hooks can't be told (a wildcard, a method name the class doesn't have), the
methods aren't put back either.

What this can't match of Fabric, seen in a reference pack (2026-10-28):

- **A field NeoForge retyped.** NeoForge makes `KeyMapping.MAP` its own `KeyMappingLookup`, not vanilla's `Map`, so an
  accessor to it as a `Map` is left out, and the mod's call to it throws `NoSuchMethodError`. OneConfig's keybind
  registry accessor (`oneconfig$map`) is one: OneConfig catches it, warns that its keybinds may shadow Minecraft's, and
  leaves the mirror of each of its keybinds that it adds to the Controls screen registered with Minecraft's keybinds,
  where on Fabric it takes them out again. They don't shadow Minecraft's own there: NeoForge's lookup keeps every
  binding of a key, so Minecraft's binding of a key a mirror shares still works (KEYMAP-1). Mixin's "Renaming @Accessor method ..." lines for OneConfig's other static accessors are Mixin's own
  naming of static interface accessors, as on Fabric.
- **Interfaces a mod adds to a library class.** adventure-platform-fabric gives authlib's `GameProfile` Adventure's
  `Identity` and Brigadier's `CommandSyntaxException` its `ComponentMessageThrowable`, which NeoForge loads without
  mixin support. Since LIB-2 a generated adapter stands in for each: the mod's casts of a profile to
  `Identity` (a player's identity) wrap it in an adapter that answers the mixin's methods and forwards the profile's
  own, so adventure-platform-fabric loads, and with it OneConfig's chat event. What stays short of Fabric: the profile
  itself still isn't an `Identity`, so code outside adventure-platform-fabric checking one against the interface sees
  it isn't, and Adventure's own check of a command's exception doesn't see a `ComponentMessageThrowable` (its message
  is shown as plain text); two casts of one profile give equal adapters, not the same object.
- **A jar a mod adds to the class path, in a package a mod's module has.** NeoForge's module class loader looks for a
  class in a package a module has only in that module, so OneConfig's Mod Menu API shim, in a package of OneConfig's
  own, doesn't load; Fabricated provides Mod Menu's API itself. Fabric Loader's `loadIntoTarget`, which OneConfig
  loads it with, is provided.

### What this means for Fabric mods

A Fabric mod works today if it needs only the Fabric Loader API and the **native** or **loads** modules
above (the item, block, entity and loot modules are native on every version), and its own mixins pass
pre-flight or can be left out (LEAVE-1 above).

### What isn't supported on NeoForge

For the beta, everything else in this document is supported. These are not, each with its reason and where it is
tracked:

| What | Versions | Why | Tracked |
|---|---|---|---|
| Fabric's game tests (`fabric-gametest-api-v1`, `client-gametest`) | all | deliberately not loaded: they are development-time tools, not used by mods at runtime | — |
| Data generation's tag appenders (and on 26.3 its loot sub-providers) | all | pre-flight leaves those hooks out where NeoForge's data providers differ; the module's classes load for mods' runtime code (DATAGEN-1), and nothing is generated unless `fabric-api.datagen` is set | — |
| A Fabric mod's accessor to a field NeoForge retyped (`KeyMapping.MAP`) | all | NeoForge's field has another type, so the accessor is left out and its callers throw `NoSuchMethodError` | KEYMAP-1 |
| A mixin adding interfaces to a library class NeoForge loads without mixin support, beyond LIB-1's and LIB-2's | all | Mixin can't reach such a class; one with state of its own, or injectors, disables its mod, or loads without the mixin by `skipMixins`. Where an adapter serves one (LIB-2), only the mod's own casts wrap the object: another mod's check against the interface doesn't see it | LEAVE-1, LIB-2 (above) |
| A failed mod's other entrypoints, with `"onEntrypointFailure": "disable"` | all | skipped, as the setting says: a server configuration task the failed mod registered can then leave a player waiting to join, its client side never registered | ENTRY-1 (decided) |
| Login queries (`ServerLoginNetworking`, `ClientLoginNetworking`) | all | NeoForge has no hook in the login phase; their receivers are never called (*Networking*) | decided |
| WTHIT's Fabric build, and the mixed renderer pairs | all | as listed in *Fabric builds Fabricated refuses* | — |
| What Sodium's Fabric build meshes the Fabric way: NeoForge's fluid renderers, section geometry from NeoForge mods, model data, position-aware light and culling | all | *Renderer mods* above; the gaps are listed rather than one mod patched | rendering-pipeline track |
| Essential's automatic updates (its Fabric build) | all | Essential loads from the launch after its loader's download, as a regular mod | DOWNLOAD-1 |
| A Fabric mod's hook into the game's options that needs its initializer (Bobby's larger render distance limit) | 1.21.1 | NeoForge 21.1 creates the options before it loads mods, where Fabric creates them after the entrypoints; such a hook that throws then is skipped for that call, with a warning and a report line (ENTRY-2) | — |
| A NeoForge model naming its loader in NeoForge's object form (`"loader": {"id": ...}`), with Moonlight's Fabric build installed | 1.21.1 | Moonlight's hook into vanilla's model deserializer, which NeoForge's runs first, reads `loader` as a string only and fails the model; not seen in the corpus | CORPUS-62 |
| What Forgified Fabric API's modules do differently from upstream, where it provides them | read on 1.21.1, 26.1.2 and 26.2 | a provider's module is used as it is; on 1.21.1 its trapdoor tag, attack block result, sculk frequencies and default-enabled packs don't take effect (on 26.1.2 and 26.2 the first three too, and a biome modification doesn't reach clients, PROVIDER-10; there its packet context holds nothing for codecs, and its HUD elements attached to vanilla ones ignore vanilla's layer checks, PROVIDER-17), and it makes fewer bridges to NeoForge (enchanting, creator mod, transfer), as listed in *Mods that bring Fabric API or Fabric Loader* above; the test mods skip those checks with it | PROVIDER-8, PROVIDER-10, PROVIDER-17 |
| Fabric mods' hooks of vanilla's spawn checks (Architectury's spawn check event for spawners and world generation, Friends and Foes' world generation spawn hook) | all | NeoForge makes `Mob.checkSpawnRules` and `checkSpawnObstruction` inside its own spawn position event's hook, so the hooks are left out, and spawners and world generation skip them; the report says so. Use the mod's NeoForge build | SPAWN-1 |
| Hooks pre-flight leaves out where NeoForge's code differs | per mod | each is in the report, with what NeoForge's code lacks and what is lost | LEAVE-1 (above) |

### Planned work, in order of impact

These modules are to be implemented natively on NeoForge's own events and APIs. Each one also unblocks the
modules that cascade from it.

1. ✅ `fabric-lifecycle-events-v1` (done; unblocked `fabric-api-lookup-api-v1` and, with a linkage-check fix
   for injected interfaces, `fabric-convention-tags-v2`/`v1`).
2. ✅ `fabric-networking-api-v1` on 26.x (done). Its dependants now load or fail on their own mixins
   (particles, recipe-api). Ported to 1.21.11 as well.
3. ✅ `fabric-resource-loader-v1` on every version (done). Unblocked the v0 API, creative-tab /
   item-group and, on 26.2, advancement-api. tag-api, loot-api and resource-conditions then failed on their own
   mixins (see above; all three load or are native now).
4. ✅ `fabric-registry-sync-v0` on every version (done). Unblocked debug-api on 26.x. object-builder
   then failed on its own mixins (native since step 6).
5. ✅ `fabric-entity-events-v1` on every version (done).
6. ✅ `fabric-object-builder-api-v1` on every version (done). Unblocked `fabric-data-attachment-api-v1`.
7. ✅ `fabric-item-api-v1` and `fabric-block-api-v1` on every version (done). The modules that build on them
   (transfer, content registries) then failed on their own mixins (native since steps 10 and 11).
8. ✅ `fabric-events-interaction-v0` on every version (done).
9. ✅ `fabric-loot-api-v3` on every version (done).
10. ✅ `fabric-content-registries-v0` on every version (done).
11. ✅ `fabric-transfer-api-v1` and `fabric-api-lookup-api-v1` on every version (done).
12. ✅ The bridge between Fabric storages and NeoForge's item and fluid capabilities (done).
13. ✅ `fabric-menu-api-v1` / `fabric-screen-handler-api-v1` on every version (done).

## Forge

Forge 61 to 66 (Minecraft 1.21.11 to 26.3) build from the `forge` branch, and Forge 47 (1.20.1) and Forge 52 (1.21.1)
from `forge-legacy`. All use the same engine as NeoForge; what Forge's code changes is below. Forge 52 runs the game in
Mojang's names, with the discovery services, the ForgeGradle build and most of the in-game mod Forge 61 to 66 have
(`mc/forge-shared`'s version groups), and Forge 47's EventBus 6 wiring; its column in the table below is its local
server and client runs with Fabric API's jar on Forge 52.1.21 (2026-11-09), and what it doesn't run or check is the
support matrix's Forge 1.21.1 column.

CI runs Forge with the harness every platform shares (*How CI tests it*), through Forge's adapter
(`ci/forge/platform.sh` on `forge`, `ci/forge-legacy/platform.sh` on `forge-legacy`); the test mods run with the
official Fabric API jar there, which they need on Forge. Which runs gate on which Forge version, and which only report,
is `ci/runs.tsv`. This table is the server's and the client's `fabric-bridge-report.txt` with Fabric API, from the
parity pull requests of 2026-11-02 (runs 41382942938 for Forge 61 to 66 and 41385466464 for Forge 47): Fabric API
0.141.6+1.21.11, 0.155.3+26.1.2, 0.161.0+26.2, 0.164.0+26.3 and 0.92.12+1.20.1, on Forge 61.2.4, 64.1.5, 65.1.5, 66.1.0
and 47.4.28. Each cell is the more limited of the two: a module with client hooks can load on the server and lose hooks,
or be turned off, on the client.

How to read it: as the NeoForge table, and

- **native**: Fabricated ships the module itself, on Forge's events; the official jar's copy isn't loaded.
- **loads, part**: the module loads without some of its hooks, ones its plan leaves out where Forge's code differs or
  ones pre-flight finds Forge's code can't take (LEAVE-1). The report names each; where Fabricated does the hook's
  work another way, the bullets below say so, and what is lost is under *Unsupported on Forge*.
- **disabled**: the client run turns it off (the game goes on without it); the server loads it where it isn't
  client-only. No module is disabled on any Forge version.

| Module | 1.21.11 (Forge 61) | 26.1.2 (Forge 64) | 26.2 (Forge 65) | 26.3 (Forge 66) | 1.20.1 (Forge 47) | 1.21.1 (Forge 52) |
|---|---|---|---|---|---|---|
| fabric-advancement-api-v1 | — | — | loads | loads, part | — | — |
| fabric-api-base | native | native | native | native | native | native |
| fabric-api-lookup-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-biome-api-v1 | loads | loads | loads | loads | native | loads |
| fabric-block-api-v1 | loads | loads | loads, part | loads, part | loads | loads |
| fabric-block-getter-api-v2 | — | loads | loads | loads | — | — |
| fabric-block-view-api-v2 | loads | — | — | — | loads | loads |
| fabric-blockrenderlayer-v1 | — | — | — | — | loads | loads |
| fabric-client-tags-api-v1 | — | — | — | — | loads | loads |
| fabric-command-api-v1 | — | — | — | — | loads | loads |
| fabric-command-api-v2 | loads | loads | loads | loads | loads | loads |
| fabric-commands-v0 | — | — | — | — | loads | loads |
| fabric-containers-v0 | — | — | — | — | loads | — |
| fabric-content-registries-v0 | loads | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-convention-tags-v1 | loads | — | — | — | loads | loads |
| fabric-convention-tags-v2 | loads | loads | loads | loads | — | loads |
| fabric-crash-report-info-v1 | loads | loads | loads | loads | loads | loads |
| fabric-creative-tab-api-v1 | — | loads | loads | loads | — | — |
| fabric-data-attachment-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-data-generation-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-debug-api-v1 | — | loads | loads | loads | — | — |
| fabric-dimensions-v1 | loads, part | loads, part | loads, part | loads, part | native | loads, part |
| fabric-entity-events-v1 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-events-interaction-v0 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-events-lifecycle-v0 | — | — | — | — | loads | — |
| fabric-game-rule-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-item-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-item-group-api-v1 | loads | — | — | — | loads | loads |
| fabric-key-binding-api-v1 | native | — | — | — | native | native |
| fabric-key-mapping-api-v1 | — | native | native | native | — | — |
| fabric-keybindings-v0 | — | — | — | — | loads | loads |
| fabric-lifecycle-events-v1 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-loot-api-v2 | loads | — | — | — | native | loads |
| fabric-loot-api-v3 | native | native | native | native | — | native |
| fabric-loot-tables-v1 | — | — | — | — | native | — |
| fabric-menu-api-v1 | — | loads | loads | loads | — | — |
| fabric-message-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-mining-level-api-v1 | — | — | — | — | loads | — |
| fabric-model-loading-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-models-v0 | — | — | — | — | loads | — |
| fabric-networking-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-networking-v0 | — | — | — | — | loads | — |
| fabric-object-builder-api-v1 | loads, part | loads, part | loads, part | loads, part | loads | loads, part |
| fabric-particles-v1 | loads, part | loads | loads | loads | native | native |
| fabric-permission-api-v1 | — | loads | loads | loads | — | — |
| fabric-recipe-api-v1 | loads | loads | loads | loads | loads | loads, part |
| fabric-registry-sync-v0 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-renderer-api-v1 | loads, part | loads, part | loads, part | loads, part | loads | loads |
| fabric-renderer-indigo | loads, part | loads | loads | loads | loads, part | loads, part |
| fabric-renderer-registries-v1 | — | — | — | — | loads | loads |
| fabric-rendering-data-attachment-v1 | — | — | — | — | loads | loads |
| fabric-rendering-fluids-v1 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-rendering-v0 | — | — | — | — | loads | loads |
| fabric-rendering-v1 | loads, part | loads | loads | loads | loads, part | loads, part |
| fabric-resource-conditions-api-v1 | loads, part | loads | loads | loads | loads | loads |
| fabric-resource-loader-v0 | loads | loads | loads | loads | loads, part | loads, part |
| fabric-resource-loader-v1 | loads, part | loads, part | loads, part | loads, part | — | — |
| fabric-screen-api-v1 | loads, part | loads, part | loads, part | loads, part | loads | loads, part |
| fabric-screen-handler-api-v1 | loads | — | — | — | loads | loads |
| fabric-serialization-api-v1 | loads | loads | loads | loads | — | — |
| fabric-sound-api-v1 | loads | loads | loads | loads | loads, part | loads, part |
| fabric-tag-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-transfer-api-v1 | loads | loads | loads | loads | loads | loads |
| fabric-transitive-access-wideners-v1 | loads | loads | loads | loads | loads | loads |


On top of the modules, the in-game mod bridges these to Forge, or makes Fabric API's hooks for them apply, each checked
by the server runs (the colours, screens, key mappings and config screens by the client runs):

- **Tags:** tag files' `fabric:remove` entries are read into Forge's own removal list, with and without Fabric API (all
  five).
- **Fluids:** Fabric mods' fluids get Forge fluid types, and an entity moves through one that isn't water as through
  air, as on Fabric (all five).
- **Registry data:** a Fabric mod's own pack takes Fabric API's known-pack identity, so its registry entries (worldgen,
  damage types) are stable, as on Fabric, and a world holding them opens without asking to confirm experimental
  settings (Forge 61 to 66, server and client; PACK-5). Forge gives mods' packs none, and Forge mods' packs keep that.
- **Built-in packs:** a built-in pack a Fabric mod's client entrypoint registers as enabled by default is switched on
  the first time the game offers it (all five, with Fabric API, client run). Fabric API keeps its file of the packs it
  offered, so a pack the player turns off stays off, as on Fabric. Upstream does this as the game's options load,
  which on Fabric is after the Fabric mods' entrypoints and on Forge before them. So the plans leave that hook out,
  and the in-game mod does its work once the entrypoints have run, before the game selects its packs from the
  options (CORPUS-104).
- **Conditional overlays:** a pack's `fabric:overlays` apply where their conditions hold, and a section that can't be
  read is left out with one warning naming the pack, which loads with its other overlays (Forge 52 and 61 to 66, with
  Fabric API; PACK-12). Forge reads every mod file's pack through vanilla's reading of a pack's metadata, where
  upstream's hook logged an error for such a section and, from Forge 64, made the pack unreadable, so Forge left the
  mod's resources out, native mods' included. Forge reads no `neoforge:overlays`.
- **Load conditions:** of two advancements (a JSON data loader's files) and two damage types (a data pack registry's
  entries) with `fabric:load_conditions`, the server loads the ones whose conditions hold and not the others (Forge 61
  to 66, with Fabric API). Forge 61 decodes registry entries through its own conditional decoder, which the in-game mod
  hands Fabric's conditions to, and Forge 61's and 66's JSON data loaders hand the data to lambdas taking more values
  than vanilla's, which pre-flight bridges upstream's hook into (LAMBDA-8). On Forge 47, a recipe's or an
  advancement's own `conditions`, which Forge reads first, take a type Forge has no serializer of from Fabric API's
  resource conditions, tested on the condition's JSON as Moonlight's bridge tests it on Fabric: Supplementaries' recipes
  name their own condition there (CORPUS-97). Of the test mod's two recipes naming its Fabric condition, the server
  loads the one whose condition holds, and neither fails to parse (with Fabric API).
- **Biomes:** a feature `BiomeModifications.addFeature` adds to the plains is among their features, and a spawn and a
  temperature added to the plains reach the biome Forge reads (Forge 61 to 66, with Fabric API). Upstream rebuilds each
  generator's feature list, a field Forge retypes, which the in-game mod writes with Forge's type (CORPUS-33). Its
  climate changes, which write a field Forge keeps private, go into that field and into Forge's biome info (BIOME-3).
  Forge 66 keeps a biome's environment attributes, its spawns among them, in that info too, and writes the info back
  into the biome once its biome modifiers ran: those start from the attributes Fabric API gave the biome (BIOME-4). On
  Forge 47 the module is native: Fabric's changes go through Forge's biome modifiers, and a spawn and a temperature
  added to the plains reach the biome Forge reads.
- **Default attributes:** `FabricDefaultAttributeRegistry.MODIFY` runs in Forge's attribute modification event, and a
  cow's armor set through it is what the game reads (Forge 64 to 66, with Fabric API).
- **Advancements:** a vanilla advancement's reward set through `AdvancementEvents.MODIFY` is what the server loads
  (Forge 65 and 66, with Fabric API; on Forge 66 through a hook on Forge's conditional decoding).
- **Colours:** Fabric's colour provider registry reads Forge's own map of block colours (Forge 61), and of block and
  item colours (Forge 47), by registry holder, where upstream's lookup reads the list by id Forge replaced (COLOR-1).
  It finds grass's provider and none for stone (Forge 61 and 47, with Fabric API). Forge 64 to 66 keep vanilla's list,
  which upstream's lookup reads as it is.
- **Networking:** a payload (Forge 61 to 66) or a channel's buffer (Forge 47) the client sends as it joins comes back
  from the integrated server with its answer, through Fabric API's module on Forge's network stack (all five, with
  Fabric API, client run).
- **Commands:** a command registered through `CommandRegistrationCallback` is in the server's dispatcher (all five, with
  Fabric API, server and client). Fabric API's hook into `Commands`' constructor applies on Forge 61 to 66 at a point
  pre-flight marks unsafe (CTOR-2), and on Forge 47, whose Mixin takes none, at Fabricated's own injection point, which
  finds what the hook's point finds and which Forge 47's Mixin takes anywhere after the constructor's `super()` call
  (CORPUS-103, which replaced CTOR-4's wrapper of the call). So does a hook shifted past a construction there, as Dripstone
  Fluid Lib's into `Minecraft`'s constructor is (the test mod's in `Commands`' sees the dispatcher just stored).
- **Block breaking:** `PlayerBlockBreakEvents` fire as a player breaks a block, and a `BEFORE` refusal keeps the block
  (all five, with Fabric API, server and client). Forge's `destroyBlock` makes the calls upstream's hooks look for in a
  `removeBlock` of its own, so the in-game mod fires the events there (BREAK-1).
- **Beds:** a block `EntitySleepEvents.ALLOW_BED` admits stays as it is while an entity sleeps on it, and the occupation
  and sleeping direction events run as it lies down and wakes up (all five, with Fabric API, server and client; on
  Forge 47 as it wakes up since LAMBDA-6). Forge sets a bed's occupation through its own `setBedOccupied`, on the red
  bed upstream's hook stands in for the block, so the in-game mod sets the block's own (SLEEP-1). On Forge 47 a player
  may sleep in the daytime `EntitySleepEvents.ALLOW_SLEEP_TIME` allows (with Fabric API, server and client): Forge 47
  checks its own sleep time event where upstream's hooks look for the day check, so the in-game mod answers it from
  Fabric's (SLEEP-2). Fabric API for 1.21.11 and later has no such event.
- **Damage:** an entity hurt through the game's own `hurtServer` asks `ServerLivingEntityEvents.ALLOW_DAMAGE`, whose
  refusal holds, and fires `AFTER_DAMAGE` with the damage it was dealt and the damage it took (Forge 61 to 66, with
  Fabric API, server and client; Fabric API 0.92.12 has no `AFTER_DAMAGE`). Forge 61's and 64's `hurtServer` drop the
  copy of the dealt damage upstream's hook reads, so the in-game mod fires the event there (DAMAGE-1).
- **Effects:** an effect's events learn the command that gave it, and an effect given by code comes from none (Forge
  61 to 66, with Fabric API, server and client; Fabric API 0.92.12 has no effect events). Upstream's hook is in
  Brigadier, a library Forge loads without mixins, so the in-game mod sets the same context where vanilla's command
  execution runs each command, a player's, the console's, a function's or a command block's (EFFECT-1). A command a mod
  runs through Brigadier's own dispatcher gives none.
- **Use and attack:** a player's uses of an item, a block and an entity, and its attacks on a block and an entity, ask
  `UseItemCallback`, `UseBlockCallback`, `UseEntityCallback`, `AttackBlockCallback` and `AttackEntityCallback` through
  Forge's own code, and a refusal holds (all five, with Fabric API, server and client). On Forge 61 to 66 the server
  asks `AttackBlockCallback` from Forge's `LeftClickBlock` as a block starts being broken and `UseEntityCallback` from
  its `EntityInteractSpecific`, which Forge's own code and its mods (a fake player's click) post, and
  `ItemEvents.USE_ON` where Forge's server calls an item's `useOn` (`ForgeHooks.onPlaceItemIntoWorld`); the plans leave
  out upstream's server hooks for the first two, in the packet handling before Forge's events, which would ask the
  callbacks twice (the shared suite's interaction check, server and client). The client keeps upstream's hooks, and an
  interaction with an entity without a clicked point (Forge 61's `EntityInteract`) keeps upstream's server hook.
  Upstream's other hooks apply as they are, pre-flight renaming the captured value the entity hook shadows.
- **Items:** Forge's item hooks answer from Fabric's item API (Forge 61 to 66, with Fabric API, server and client): an
  item's equipment slot from its Fabric equipment slot provider (asked without an entity, as Forge's hook names none;
  Forge's `LivingEntity.getEquipmentSlotForItem` asks upstream's hook first, with the entity), the enchanting table's
  check from `EnchantmentEvents.ALLOW_ENCHANTING` and `canBeEnchantedWith`, the creator mod from `getCreatorNamespace`
  where the item overrides it, and on Forge 61 a stack's crafting remainder from `getRecipeRemainder`, which its
  furnaces, brewing stands and crafting ask (Forge 64 to 66 share the method with Fabric's). On Forge 61 Fabric's
  `DefaultItemComponentEvents.MODIFY` reaches an item whose components were asked before the registries froze: Forge
  builds an item's components once, and builds them again once Fabric API changed them. On Forge 47 (with Fabric API,
  server and client) the equipment slot answers Forge's hook too, a stack's crafting remainder, which its crafting,
  furnaces and brewing stands ask through Forge's `getCraftingRemainingItem`, is `getRecipeRemainder`'s, and where
  Forge's item stack asks the item for its attribute modifiers and whether it is the tool for a block, Fabric's
  `getAttributeModifiers` and `isSuitableFor` answer for an item overriding them (Forge's answer stands otherwise, its
  tools' tiers sorted) and `ModifyItemAttributeModifiersCallback` changes the modifiers before Forge's own event, as
  upstream's hooks into vanilla's calls Forge replaced did; Fabric API 0.92.12 has no enchanting event or creator mod.
  An item overriding Forge's hooks (a Forge mod's) keeps its own answers.
- **Blocks:** a block in Fabric's `fabric:can_climb_trapdoor_above` tag makes the open trapdoor above it climbable
  through Forge's `makesOpenTrapdoorAboveClimbable`, whose ladder check never reaches vanilla's that upstream's hook
  extends (Forge 61 to 66, with Fabric API; Fabric API 0.92.12 has no such tag). On Forge 65 and 66 a block's
  enchanting power is Fabric's `getProvidedEnchantmentPower` through Forge's `getEnchantPowerBonus`, which Forge's
  enchanting table sums (the plans leave out upstream's table hooks, which would replace Forge's sum and lose a Forge
  mod's block's power), and `FluidFlowEvents.ALLOW` is asked where Forge asks its fluid interactions in place of
  vanilla's check, as a liquid is placed and as its neighbour changes (the shared suite's block check, server and
  client).
- **Screens:** a screen's initialization and drawing events fire, and a key press, a key release, a character typed, a
  mouse click and its release on a screen ask `ScreenKeyboardEvents` and `ScreenMouseEvents`, whose refusals hold (Forge
  64 to 66, with Fabric API, client run). Forge calls a screen's input handlers and draws it through hooks of its own,
  where upstream's wrappers look for vanilla's calls, so the in-game mod wraps the same calls inside Forge's hooks and
  fires the drawing events from Forge's `ScreenEvent.Render` (SCREEN-1). Forge 61 has the same bridges, which no check
  runs yet: 1.21.11 has no client test mod for Fabric API's client modules. On Forge 47 a click and a key press, and
  their releases, ask the same events (with Fabric API, client run): its patch made the mouse lambdas take `this` and
  reordered the key lambda's captured values, and pre-flight bridges upstream's hooks into both (LAMBDA-7).
- **HUD:** a Fabric HUD element attached after chat and one attached before the hotbar are drawn (Forge 61 to 66, with
  Fabric API, client run). Forge 61 to 66 draw the HUD as layers of their own, where upstream's hooks into vanilla's
  HUD method no longer run, so the in-game mod wraps each of Forge's layers with the Fabric elements attached to,
  replacing or removing the same vanilla element, vanilla's conditions around it checked first (HUD-2). Removing
  Fabric's `info_bar` element also keeps the bar from switching between the experience and the locator bar on Forge 61
  to 66, whose layer does both. A Fabric mod's own hooks into the HUD method run where Forge's HUD does its work
  (HUD-4; the test mod's, all five, client run). Forge 61 to 66's patch draws Forge's layers at the method's start and
  returns before vanilla's code, so pre-flight points a hook at its end (malilib's, which MiniHUD, Tweakeroo and
  Litematica draw from, on Forge 61 and 64) at its returns, and one at a call drawing a part of the HUD, which Forge's
  layer makes itself, at the start of the called method. Forge 47's GUI overrides the method and never calls it, so
  pre-flight points hooks at its start and end at Fabricated's seams, which run where Forge's GUI starts and ends its
  HUD drawing, a hook cancelling at the start skipping it. Fabric API's own hook fires the deprecated
  `HudRenderCallback` (1.20.1's HUD API on Forge 47) so, after Forge's last layer, outside the loading screen (Forge 61
  and 47, with Fabric API, client run).
- **Key mappings:** on all five Forge versions the key mapping module (the key binding module on 1.21.11 and 1.20.1) is
  native, with Fabric API's jar and without it (client runs). Upstream refuses a key mapping once the game's options
  exist, which Forge creates before it loads mods, so passed through, it failed every client entrypoint that registers
  one (KEYMAP-3; on Forge 47 found with Xaero's World Map). Fabricated keeps them until Forge's
  `RegisterKeyMappingsEvent`, where Forge mods register theirs; Forge then reads the saved keys again. A test key
  mapping is in the options, sorted after the game's, and bound to the key CI's options file saves for it; a second
  registration of it, and one after Forge's event, are refused, as upstream refuses them.
- **Config screens:** a Fabric mod's Mod Menu config screen is behind Forge's mod list button, as on NeoForge (all five,
  client run).
- **Failed initializers:** a Fabric mod whose entrypoint throws is named in the report and in Forge's loading warnings,
  and what it built but never registered is dropped at the registries' freeze with an error, where it stopped the
  freeze (all five, server run; HOLD-2). Fabricated's API runs what waits for the initializers once they ran
  (`Fabricated.get().afterEntrypoints`).
- **Registration order:** a Fabric mod may make its objects first and register them after, in any order, as Fabric
  lets it: a bucket made before its fluid is registered holds that fluid (all five), and on Forge 47 so do a liquid
  block made before its fluid and a music disc made before its sound, and a block item registered before its block is
  that block's item (server run; CORPUS-73). Forge's patches of those constructors, and Forge 47's of a block item's
  block, looked the object up in its registry and threw: TechReborn's fluids didn't load (seen on Forge 47, 64 and 65)
  nor Lootr's block items on Forge 47, and what they left registered stopped Forge's snapshot of the registries.
- **Registry aliases:** an old name a Fabric mod keeps for a renamed entry with Fabric API's `FabricRegistry.addAlias`
  names that entry in the registries Forge manages, in their lookups by name and by key (which a data pack's references
  go through) and in Forge's own registry, its snapshots and its sync to clients (all five, with Fabric API; server
  run; CORPUS-75). Fabric API kept the alias in vanilla's registry class, whose lookups Forge's wrapper replaces:
  Waystones' world generation names its renamed waystones by their old names, and its data pack, and with it the
  server, didn't load on Forge 64 and 65.
- **Registry entry added events:** Fabric API's `RegistryEntryAddedCallback` fires for the registries Forge manages,
  once an entry new to one is in it, with its raw id, whether a Fabric mod registered it through vanilla's API or a Forge
  mod through Forge's (all five, with Fabric API; server run; CORPUS-108). Upstream fires it from vanilla's registry
  method, which Forge's wrapper of those registries replaces, so it never fired for blocks, items, entity types and the
  rest. Architectury gives a spawn egg its entity type from a listener on that event: Artifacts' egg kept none, and EMI's,
  JEI's and REI's listings of its creative tab failed (EMI's own plugin with them). Where Forgified Fabric API provides
  the module, its own copy fires the event.
- **Registries made early and late (Forge 47):** a registry a Fabric mod adds to the root registry, and an entry it adds
  to a vanilla registry, from code that runs before the main entrypoints, such as `RegistryDataLoader`'s class
  initializer Wover hooks, are taken (with Fabric API's jar and with Forgified Fabric API, server and client; REG-10, as
  REG-3 on NeoForge): Forge 47 freezes the root again once it made its own registries, and its data pack registry event
  loads that class right after. A data pack registry a Fabric mod makes through `DynamicRegistries` loads its entries
  from `data/<namespace>/<registry namespace>/<path>`, Forge's path and Fabric's (REG-11). With Forgified Fabric API,
  a registry a Fabric initializer builds with `FabricRegistryBuilder` joins the root registry, and one it makes through
  `DynamicRegistries` is a data pack registry, synced, as with Fabric API's jar (PROVIDER-20, PROVIDER-21).
- **Recipe slots on the network (Forge 52, 61 to 66):** an empty optional ingredient, a shaped recipe's empty slot, is sent
  as vanilla sends it, an empty item list, and read back as empty (server run; CORPUS-83). Forge's patch of vanilla's
  codec threw writing it, so with Fabric API, whose recipe sync sends the recipes Fabric mods' clients need, a shaped
  recipe with an empty slot dropped the player's connection to the world they joined (seen on the Forge 65 corpus
  client). A Fabric mod's own ingredient, a subclass of vanilla's without
  Forge's `serializer()` (Fabric API's custom ingredients are one), goes in vanilla's form after a marker of its own,
  through the codec Fabric API wraps with its custom form, so one of Fabric API's goes as Fabric API sends it (server
  run, with Fabric API for its custom ingredients; CORPUS-85). Forge's codec threw writing it, and the Forge 65 and 66
  corpus clients waited for their player until the run's timeout. In a recipe slot, Forge's custom ingredients are
  read by Forge's codec, as Fabric API's wrapper took the -1 they start with for its own. Forge 52 threw writing a Fabric
  mod's own ingredient and Fabric API's too, and keeps no codec of vanilla's form, so the in-game mod writes 1.21.1's,
  the ingredient's items, and wraps it with Fabric API's custom form as upstream's hook, left out there, would (NET-13);
  its empty slot is vanilla's empty ingredient, which Forge 52 writes as vanilla does. Only the network form changes: a Fabric mod's subclass other than Fabric API's still can't be written as JSON (no mod seen doing it).
- **Custom ingredients and crafting size (Forge 47):** a recipe ingredient with Fabric API's `fabric:type` is that
  custom ingredient, as Forge reads every ingredient itself before Fabric API's hook can; inside an array it joins
  Forge's compound ingredient, as Forge's own custom ingredients do (CORPUS-99). On the network, Forge's custom
  ingredients, whose -1 marker Fabric API's reader took for its own, are read by Forge (NET-11). A size given to Porting
  Lib's `ShapedRecipeUtil.setCraftingSize` is given to Forge's too, so Create's 5 by 5 mechanical crafting recipes
  load. The test mod reads a `fabric:any` alone and in an array as a recipe reads an ingredient, and both come back from
  the network as they went (with Fabric API, server run).
- **Drop capture (Forge 47 and 52):** a dying entity's drops stay in Forge's capture after Forge's `LivingDropsEvent` (none
  stay where a listener cancelled it) until every mod's hook at the return of `dropAllDeathLoot` has run, and
  Fabricated adds what is still captured to the level after them, as Forge's method did. Porting Lib ports that
  capture to Fabric under Forge's names, so on Forge its hook there takes them, fires its own drops event and adds them;
  Forge's method used to take them first, and Porting Lib's hook threw as any mob died (CORPUS-105, DROP-1). The test mod's own
  hook there stands in for Porting Lib's: a pig it kills at the first tick leaves it its drops, which reach the level
  (server run).
- **Storages:** a Fabric mod's item and fluid storages are the `ITEM_HANDLER` and `FLUID_HANDLER` capabilities of its
  block entity, and a Forge mod's `ItemStackHandler` and `FluidTank` are Fabric's storages (all five, with Fabric API,
  server and client). A Forge handler only simulates until Fabric's outermost transaction commits, so an aborted one
  never reaches it, and two insertions in one transaction take no more than its slots hold; fluids move in whole
  millibuckets. On Forge 61 to 66 a fluid carrying data (Fabric's components, Forge's tag) doesn't cross. A Fabric mod's
  tank item is filled and drained through Forge's `FLUID_HANDLER_ITEM`, its stack changed in place when it stays the
  same item, and a Forge mod's through `FluidStorage.ITEM`, in Fabric's transaction. Items in items cross the same way
  on Forge 61 to 66: a Fabric mod's item storage in an item (a bag's) is its stack's `ITEM_HANDLER`, the stack changed
  in place, and a Forge mod's item handler on a stack is Fabric's `ItemStorage.ITEM`; a move that would leave another
  item in the bag's place, which Forge's handler of a stack can't hand back, doesn't happen. Fabric API for 1.20.1 has
  no lookup for item storages in items, so Forge 47 has none to cross.
- **Fuels and brewing (Forge 47):** an item `FuelRegistry` makes a fuel is one to Forge's furnace, and an awkward potion
  brewed with an item a `FabricBrewingRecipeRegistry` recipe names gives the recipe's potion (with Fabric API, server
  and client). Forge's furnace reads its burn times from vanilla's fuel table as tags load, where upstream's hook adding
  Fabric's fuels is left out with the rest of its furnace mixin, so the in-game mod adds them (FUEL-1). Upstream builds
  a brewing recipe with vanilla's constructor of a brewing mix, which Forge replaced, so every call threw and failed
  the initializer making it; the in-game mod builds it with Forge's (BREW-1). The report still lists that call among
  its linkage warnings.
- **Model baking:** a model loading plugin's before-bake and after-bake modifiers see the models baked (Forge 47, with
  Fabric API, client run). Forge 47 moved the bake's code into an overload that also takes the sprite getter, where
  pre-flight moves upstream's hooks, its before-bake variable modifier too (MODEL-3).
- **Model loaders (Forge 47 and 52):** Forge reads every block model's `loader` and fails the model when its own registry
  lacks that loader, where vanilla ignores the key and a Fabric mod reads its own loaders through a hook into vanilla's
  model reader (Moonlight's for Supplementaries, Porting Lib's for `porting_lib:obj` and the rest). A loader in a Fabric
  mod's namespace, or in no loaded mod's, is left to those hooks, as on Fabric; one in Forge's namespace or a Forge
  mod's fails as without Fabricated (CORPUS-92, MODEL-5, client run).
- **Language files on the server (Forge 47):** Forge's server reads every mod's language file with its own copy of
  vanilla's reader, which no Fabric mod hooks. The in-game mod has it use vanilla's reader, as a Fabric server does
  through Fabric API, so a Fabric mod's hook applies there: owo-lib's lets its files hold rich text, which Forge's copy
  refused (CORPUS-100, server run).
- **Fabric's renderer API:** a Fabric block model (one with its own emitter) is drawn in chunk sections, alone and
  inside a vanilla multipart model, as a falling block and with the breaking overlay, on Forge 61 to 66 as a minecart's
  block too and on Forge 47 as a block a piston moves, and a Fabric item model's quads in the hotbar, the hand, an item
  frame and on the ground (all five, with Fabric API, client run). On Forge 61 to 66 upstream's hooks send every block
  through Fabric's renderer, as on Fabric. Forge 61 moved the code three of them hook into, so pre-flight leaves out
  those for moving blocks, the breaking overlay and blocks drawn as entities, and the in-game mod sends a block whose
  model is a Fabric model, or a multipart or weighted model with one among its parts, to Fabric's renderer there
  instead; every other block keeps Forge's path. A model without an emitter of its own that reads Forge's model data (a
  Forge mod's level-aware model), drawn by Fabric's renderer as a block's model or inside a Fabric model, emits the
  parts for its model data, which it is asked with the level as Forge's chunk section compiler asks it; upstream's
  default emitter collects its parts without it (Forge 61 to 66, the renderer test mod's level-aware default check).
  Forge 61 to 66 also ask a block model for its particle sprite with Forge's model data where upstream's hooks ask
  Fabric's level-aware one, in places those hooks no longer reach: the screen effect inside a block (Forge 65 draws none
  for any block: its patch submits it only where a listener cancelled Forge's `RenderBlockScreenEffectEvent` for it), a
  block marker (Forge 64 to 66) and a block particle, whose sprite Forge sets again from the block's position. There the
  in-game mod asks Fabric's for such a model, and a model whose class gives its own sprite for model data keeps Forge's
  (PART-4; the test block's model, Forge 61 to 66). On Forge 47 upstream's hooks miss Forge's chunk section compiler and
  block renderers, which call Forge's overloads taking model data and a render type: pre-flight leaves out the chunk
  section redirect and keeps the hook that prepares Indigo's terrain context, and the in-game mod has Indigo draw such a
  block, once (at its first render type), in chunk sections, as a falling block, as a block a piston moves, with the
  breaking overlay and wherever the block renderer draws one block. Items keep Indigo's own hook there, and blocks drawn
  as entities their vanilla quads, as upstream draws them on 1.20.1. FRAPI rule 8: where Fabric's renderer (Indigo)
  calls a block model's emitter, a Fabric model that throws fails alone. The block is drawn from the model's parts (on
  Forge 47 by Forge's renderer, from the quads the model gives it), the failure is logged once and listed in the report,
  and the model's class is drawn that way from then on, but not the class of a model wrapping it (as Continuity's wrap
  others). A transform it left pushed never reaches another block: it is popped from Indigo's emitter, and on Forge 64
  to 66 a model drawn into another renderer's (Sodium's Fabric build) draws into an emitter of Fabricated's, dropped
  after a throw (FRAPI-12). The renderer test mod checks each of these with a model that throws in a chunk section, as a
  multipart model's part, inside a model wrapping it, and on Forge 61 to 66 as a minecart's block. The outline Fabric's
  renderer draws around a glowing falling block is checked on Forge 65 and, since v0.6.0-beta, on Forge 66. Fabricated's
  guards apply only with Fabric's renderer API installed (they name its types), and the two that sit in methods upstream
  replaces (blocks drawn as entities, and the breaking overlay on Forge 64) apply after upstream's replacement.

What else differs on Forge, for Fabric mods in general:

- **Covered by Forge itself:** upstream's fix for emerald trades with other mods' villager types, as Forge 61 to 66
  skip the same check and give no trade for a type without one, upstream's DataFixerUpper hooks for a dimension whose
  chunk generator type no mod registers, as Forge's patch to the same schema keeps the generator's data (all five), and
  on Forge 66 upstream's leave for a Fabric mod's fishing rod to keep its hook out, as Forge's own check keeps it out
  for any item performing its fishing rod action, which every `FishingRodItem` does. The report lists those hooks as
  left out or skipped.
- **Loading errors:** when Forge refuses a mod file or a dependency is missing, it loads only Minecraft and Forge to
  show the error. Fabricated then leaves the game's classes alone, so Forge's own error is what the player sees
  (LOADERR-1). Forge itself refuses two Fabric jars in the corpus: JEI's on Forge 61 to 66 (its module-info) and YACL
  3.6.6's on Forge 47 (its `mods.toml`). Their Forge builds work.
- **Jars built for Fabric and NeoForge at once** list their nested jars for NeoForge too. Fabricated hides that list
  from Forge's jar-in-jar locator and reads the jar itself, so each nested mod loads once, translated (JIJ-1).
- **Placeholders for Sinytra Connector:** a Fabric jar whose `META-INF/mods.toml` only tells Forge to install Connector
  (`connector:placeholder`, as Origins 1.10.2's has) loads as the Fabric mod it is: Fabricated's mod locator keeps it
  from Forge's mods-folder scan (MULTI-2, checked on a Forge 47 server with Origins 1.10.2). Any other jar with
  both loaders' metadata is Forge's.
- **Other copies of Fabric's parts:** a Fabric API module another Forge mod provides (one bundling Fabric API's modules)
  is that mod's, and Fabricated's copy, its mixins and its Forge hooks stand down, as on NeoForge (all five, server run
  with stand-ins; PROVIDER-1). Sinytra Connector and a second Fabricated jar are refused, the launch stopping with
  Fabricated's reason (DUP-1), and so, at once, is a Forge jar with its own copy of Fabric Loader, beside which Forge
  can't even build its error screen (all five; PROVIDER-2). Not Forgified Fabric API's on Forge 47 (next item).
- **Forgified Fabric API (Forge 47):** its 1.20.1 build nests Forgified Fabric Loader, whose packages are Fabric
  Loader's API, as Fabricated's own copy's are. Fabricated's in-game mod nests an empty jar under that loader's
  jar-in-jar identifier (`dev.su5ed.sinytra:fabric-loader`) at version 999.999.999, as Sinytra Connector does: Forge's
  jar-in-jar adds the newest version every range admits, so it adds the empty jar and leaves the copy out, and the mod
  locator lets the jar load (PROVIDER-2). Its modules are Forge mods whose `@Mod` classes start them and which use
  Fabric Loader's public API only (read off its jar), so Fabricated's copy serves them. Fabricated's own Fabric API
  modules stand down for theirs, with the parts of the in-game mod serving those modules and its transfer bridge
  (PROVIDER-1, PROVIDER-13, PROVIDER-16). The in-game mod is then loaded even without Fabric mods, as only it nests the
  empty jar. A copy under another identifier, in a jar's own entries, or with a range the empty jar's version doesn't
  satisfy is still refused. A provider's module is used as it is, and what it does differently from upstream is its own.
  Read in Forgified Fabric API's source (0.92.6): a mod's own loot table reports its source as vanilla, as Forge builds
  every mod's pack as built in (PROVIDER-14); a key binding registered once the game's options exist isn't refused, and
  on Forge they exist before mods load; its fake player is Forge's, whose connection drops every packet, so a fake
  player using an entity does nothing; a Fabric fuel's burn time is answered through Forge's burn time event, not
  vanilla's fuel table; a built-in pack a Fabric mod enables by default is offered but not switched on, as it switches
  such packs on as the options load, before Fabric mods register theirs; and its transfer bridge reports the last part
  of a millibucket filled as 0 mB, gives Fabric mods' items no fluid handler, never hands the context the item a Forge
  item's handler turns into, and lets a second insertion into a Forge item handler in one transaction miss the first,
  each simulated on the handler as it stands (PROVIDER-15). A stand-in nesting a copy as Forgified Fabric API does is a
  gate, and so is the server with Forgified Fabric API itself, its newest build, since it passed two extended runs in a
  row (CI-1), and the client with it, in extended runs, since v0.6.0-beta. The test mod takes those answers as the
  provider's there and checks the rest.
- **Default method bridges:** where an interface a Fabric mod's mixin adds to a game class brings a second default
  method beside one Forge's own interfaces give it (`IForgeLivingEntity.self()` on `LivingEntity`, `IForgeEntity`'s
  `isMultipartEntity()` on every entity class), the class declares the method, calling Forge's, as on NeoForge
  (CORPUS-11, CORPUS-63, DEFAULT-1); without it either call threw `AbstractMethodError`. Forge runs on ModLauncher, so
  the loader's transformer declares them before Mixin, and a Mixin extension of the in-game mod takes them out as Mixin
  starts on a class it applies mixins to and declares them again after. The test mod calls both kinds on a pig (all
  five, server run).
- **Before the entrypoints:** Fabric mods' handlers in a registry's freeze, and a hook of theirs into the game's options
  that throws, wait for their main entrypoints as on Fabric (REG-8, ENTRY-2; Forge's client creates the options before
  it loads mods), and the system property FML sets for an empty log marker is removed before any Fabric mod runs
  (CORPUS-60). A Fabric mod's jar a mod proposes for the class path as the game runs isn't added, with a loading
  warning (DOWNLOAD-1); Essential, whose loader does that, loads from its download in the next launch, as on NeoForge.
- **The difficult mods' Fabric builds:** each loads by default and the client reaches a world with every smoke check
  passing, a gate in extended runs: malilib with MiniHUD, Tweakeroo and Litematica; Sodium's, the renderer test mod's
  checks passing under it (with Indium on Forge 47), and Iris's with it (IRIS-1's repairs on Forge 47, IRIS-2's on Forge
  52); both of Essential's launches; and Flashback's on Forge 52 and 61 to 66 (PLUGIN-1's repair on 64 to 66; Modrinth
  has no build for 1.20.1). The compatibility plan refuses none of them on Forge.
- **Forge's coremods:** pre-flight reads the game's classes as Forge's own transformers leave them before Mixin
  applies any mod's mixins: Forge 47's coremods and Forge 61 to 66's `forge-transformers`. Each mob's `finalizeSpawn`
  call Forge changes is pointed at `ForgeEventFactory.onFinalizeSpawn` (on Forge 47 anywhere in the classes it lists;
  on Forge 61 to 66 in the methods it names and those classes' lambdas), and the fields Forge reads through getters are
  read so, so a Fabric mod's hook at such a call moves to Forge's hook or is left out with its reason, as on NeoForge
  (CORPUS-61; on Forge 61 to 66 CORPUS-78: Friends and Foes' patrol spawner hook stopped the Forge 64 and 65 servers).
- **MixinExtras** is 0.5.5 on every Forge version: the in-game mod nests it, and Forge's jar-in-jar locator picks it
  over the 0.5.4 Forge 61 to 66 offer (MIXEXT-3). Its mod's root is the in-game mod's files, which owo reads, as
  it reads every mod's (CORPUS-77: owo's client stopped on Forge 47). MixinExtras' own Fabric jar, nested under whatever id, isn't
  loaded: its init config has the name of the platform's, which Mixin then never ran (PLAT-7: Origins' on Forge 47).
- **`@ModifyArgs`:** Forge 47's Mixin 0.8.5 puts the classes it generates for a `@ModifyArgs` handler's arguments in no
  module of the game layer, so a game class naming one failed to load (the corpus server and client stopped as `Fluids`
  loaded, CORPUS-68). Fabricated's transformation service adds a module holding those packages, and no files, as
  Mixin 0.8.7 does on Forge 61 to 66; not when no Fabric mod is loaded, or a mod file already holds one of them.
- **A shadow of another mixin's field:** Forge's Mixin (SpongePowered's 0.8.5 and 0.8.7) finds a `@Shadow` field only
  among its class's own, where Fabric's also finds those the mixins it prepared before add: MoreCulling's shadow of
  the mesh Fabric API's renderer mixin adds to `BlockModelRenderState` stopped the Forge 26.x clients. Such a shadow is
  declared as the other mixin declares the field, and Mixin merges the two into one field, as on Fabric (CORPUS-74:
  the test mod's tick counter, all five, server run); one that can't be (a static shadow of an instance field, a
  writable one of a final field) disables its mod, or leaves its mixin out where it can.
- **Mixins adding constants to an enum Forge makes extensible:** a mod's `@Mutable` shadow (or accessor) of such an
  enum's values array, as FrozenLib's `MobCategory` mixin has, loses `@Mutable`, so Forge's enum extender, which runs
  after Mixin and finds the array only while it is final, still finds it, and makes it writable for the mod (ENUM-8,
  checked on a Forge 66 server with FrozenLib 3.0.3). Mixin's own `@Mutable` had stopped the game as the enum loaded.
- **Overrides Forge moved to an overload:** Forge 47's `ServerPlayer` has no `changeDimension(ServerLevel)` of its own:
  `Entity`'s passes the level's portal forcer on to `changeDimension(ServerLevel, ITeleporter)`, which `ServerPlayer`
  overrides with vanilla's code. A Fabric mod's hook into the method moves to that override, which every dimension
  change of a player runs once, Forge's teleporters' included (PRE-24: Carpet, Balm, FrozenLib, GlitchCore and Polymer;
  checked on a Forge 47 server with Carpet 1.4.112).
- **A local picked by its ordinal:** a `@ModifyVariable` picking a boolean, byte, char or short by its ordinal among
  those in scope gets the ordinal vanilla's local has in Forge's code, where Forge's patch declares one of the type
  elsewhere, and is left out where that can't be told and the ordinal is past the locals Mixin can see (PRE-25: Apoli's
  tool check in `ServerPlayerGameMode.destroyBlock` on Forge 47, which had stopped a server with Origins; checked on a
  Forge 47 server with Origins 1.10.2).
- **A local taken by its type where Forge's Mixin keeps others:** Forge's Mixin keeps a local among those it finds at a
  point after the local's scope has ended, where only same frames come between, so a MixinExtras `@Local` taking a local
  by its type finds more than one where the method has one in scope. Pre-flight names the one in scope by its ordinal
  among those Mixin keeps, and leaves the hook out where that can't be told (PRE-26: FrozenLib 1.9.3's gravity hook in
  `AbstractMinecart.tick` on Forge 47, which had stopped a server; checked on a Forge 47 server with FrozenLib 1.9.3).
- **The libraries Fabric's Mixin carries:** Fabric's Mixin carries Guava and Gson in its own package
  (`org.spongepowered.include`), which some Fabric mods name (Wover's item API); Forge's Mixin carries none. A mod's
  references there name the game's own Guava and Gson instead (LIB-3: BetterEnd's, BetterNether's, BCLib's and Wover's
  entrypoints on Forge 52 and 64 to 66; checked on a Forge 66 server and a Forge 52 server and client).
- **Loot pools made with vanilla's constructor:** Forge 47's loot pool takes a name after vanilla's arguments, and has
  no constructor without it, as NeoForge's (an `Optional` there): a Fabric mod's call of vanilla's constructor calls
  Forge's with no name, and its widener's line for vanilla's opens Forge's (LOOT-3: BCLib's merged loot pools,
  BetterNether's piglin bartering table among them; checked on a Forge 47 server). Forge 52 and 61 to 66 keep vanilla's.
- **Piston stickiness:** Forge 47 to 66 replace vanilla's `PistonStructureResolver.isSticky` and `canStickToEachOther`
  with the block state's `isStickyBlock` and `canStickTo`; both are put back where a Fabric mod's mixin hooks one of
  them, answering as Forge's code does (under their SRG names on Forge 47), so the mod's hooks of them apply (SEAM-1:
  Carpet's, FrozenLib's and Moonlight's sticky blocks); a Fabric mod's mixin of the resolver hooking neither leaves it as
  Forge has it. Forge's own sticky blocks and slime and honey stick as before.
  A Fabric mod's hook answering for every block answers for Forge mods' blocks too: Carpet 1.20.1's does, its own
  rules deciding each block's stickiness, as it does for other mods' blocks on Fabric. On Forge 47, whose
  `addBlockLine` declares its old state before vanilla's position, Mixin skips Carpet's hook keeping the line's
  positions and its check of the line is left out (SEAM-2), as on NeoForge 21.1 to 26.1.2; Forge 52 and 61 to 66 keep
  vanilla's order. Where a Forge mod's mixin hooks the resolver's `isStickyBlock` or `canStickTo` calls, or a place
  putting the methods back would shift, they aren't put back, and the Fabric mods' hooks of them find nothing, as on
  NeoForge with Moonlight's NeoForge build (its Forge builds not read). On Forge 47 a Forge mod's mixin names the
  resolver's methods by the development names its refmap maps, which pre-flight doesn't read, so any Forge mod's mixin of
  the resolver naming one of its methods keeps them from being put back.
- **Mixin field initialisers that branch:** Forge's Mixin refuses a mixin whose field initialisers, which Mixin merges
  into the target's constructors, branch or load or store an array element, which Fabric's Mixin takes. Pre-flight
  computes such a field's value in a method the mixin adds, which the initialiser calls, and leaves out a mixin it can't
  repair so (PRE-28: Cobblemon's item renderer and model loader hooks, which had stopped a Forge 52 client; checked on a
  Forge 52 client).
- **Static handlers on methods that take `this`:** Forge 47's Mixin (SpongePowered's 0.8.5) refuses a static `@Inject`
  handler on a method that takes `this`, which Fabric's Mixin calls without it. Pre-flight gives such a handler a bridge
  taking `this`, which calls it (LAMBDA-12: Architectury's screen click and release hooks into the `MouseHandler`
  lambdas Forge 47's patch made take `this`; the test mod's static handler, server run).
- **Pack sources a mod leaves immutable:** a Fabric mod adding a pack source the vanilla way, with a new immutable set,
  keeps it on every Forge version, as on NeoForge (PACK-1): the set is made mutable again before Forge adds its pack
  finders (PACK-11: FrozenLib's resource pack API, which had stopped a Forge 61 client; checked on a Forge 61 client
  with FrozenLib 2.3).
- **Constructor heads:** Forge 61 to 66's Mixin takes `@At("CTOR_HEAD")`, and `HEAD` with a static handler, only marked
  unsafe, which Fabric's Mixin's points are by default: pre-flight marks them, and a hook it moves into another
  constructor, as it marks call points there (CTOR-7: FrozenLib's, ImmediatelyFast's and Wover's hooks; FrozenLib's
  and Wover's checked on a Forge 66 server). On Forge 47, whose Mixin has neither, pre-flight leaves such hooks out.
- **Mixin configs a mod adds itself:** Forge 52's and 61 to 66's Mixin (SpongePowered's 0.8.7) gives a config added
  by name alone (`Mixins.addConfiguration`, as ReplayMod's plugin adds most of its configs) no environment, and stopped
  the game reading it; the mod's calls give the default one, as Fabric's Mixin does (LOADER-6). On every platform
  pre-flight checks such a config as a listed one where the mod's code names it by a constant, as ReplayMod's does, and
  reports one added by a name the code computes unchecked (MIXCFG-1). ReplayMod's apply on a Forge 66 client, which
  joins its world, and ReplayMod 1.20.1's on a Forge 47 client, which joins its world too: its recording config's
  scroll hook captures locals Forge's `MouseHandler.onScroll` has otherwise, and skips itself, so ReplayMod doesn't see
  the mouse wheel in a replay there.
- **Two mixins' unique fields of one name:** for the same reason, Forge's Mixin renames a private `@Unique` field only
  when its class declares one of that name, not when another mixin adds it, and two mixins' fields became one, where
  Fabric's Mixin keeps a field each: Fabric API's screen mixin and SpruceUI's both keep `tickingScreen` in `Minecraft`,
  and the Forge 64 corpus client stopped as it created its world (CORPUS-95). Pre-flight renames a Fabric mod's such
  field, below public, when another mixin adds one of its name and type (the test mod's two counters, all five, server
  run).
- **Locals where Mixin miscounts them:** Forge 61 to 66's Mixin 0.8.7 works out the locals at an injection point from
  the frames it recorded from the class's bytes, which Forge's ModLauncher hands it with their frames compressed
  (Forge 47's Mixin 0.8.5 reads a class's frames expanded): each frame then states only its change, and Mixin can count
  fewer locals than are live, so a local capture, `@ModifyVariable` or MixinExtras' `@Local` taking one beyond finds
  none. Resourceful Lib's `@Local PoseStack` in `ScreenEffectRenderer.renderScreenEffect` stopped the Forge 61 and 64
  corpus clients (CORPUS-79). There, before mixins apply to a class a Fabric mod's mixin targets, a Mixin extension of
  the in-game mod finds each method whose locals Mixin would count wrong at some frame and records its frames again
  from the method with its frames expanded, which Mixin counts as with its `CLASSREADER_EXPAND_FRAMES` option, and logs
  the method. Every other method, and every class no Fabric mod's mixin targets, stays as Mixin recorded it. The test
  mod takes a local so (all five, server run; Forge 47's Mixin counts it right without the repair).
- **Fields Forge 47 and 52 retyped or replaced:** a Fabric mod's accessor of a field Forge 47 gave another type (a brewing
  mix's `from` and `to`, holders there; the particle engine's providers, kept by id there) or replaced (a mob bucket's
  entity type, a supplier there) invokes a getter the in-game mod adds, which gives vanilla's type: the holder's value,
  a view by raw id of Forge's map, and the bucket's `getFishType` (CORPUS-37: Porting Lib's accessors, and with them its
  extensions and Farmer's Delight's entrypoint, failed pre-flight; CORPUS-94: Supplementaries' bucket accessor stopped
  the corpus server's data reload). A mod's own read of such a field by vanilla's type calls the getter too
  (CORPUS-107: REI's default client plugin reads a mix's `from` itself, and failed before its later displays). The test
  mod reads a mix's potions and the pufferfish bucket's type so on the server, the mix's `from` by the field itself
  too, and the providers on the client. Forge 52 keeps the bucket's type and the providers so too, and has the same
  getters (ACC-5; 1.21.1's brewing mix holds holders in vanilla). An accessor or read of another such field still
  fails.
- **Forge Config API Port** on Forge 61 to 66: its Fabric build loads, with its own copies of NeoForge's config classes,
  which Forge lacks (REPL-1, PLAT-5). On Forge 47 its version for 1.20.1 copies Forge's own config system, packages
  included, so its copies were left out for Forge's and its registry failed to link (Ponder's main entrypoint,
  CORPUS-81). There the jar is left out, as on NeoForge, and the in-game mod provides the port's Fabric API
  (`ForgeConfigRegistry`, `ForgeConfigPaths`, `ModConfigEvents`) on Forge's config system: each config is made with the
  Fabric mod's Forge container under the port's file name, its values readable right after registering, as with the
  port, and Forge loads, watches, syncs and unloads it as its mods' own, the port's events firing from Forge's. The test
  mod registers a config so and checks it each server run (PLAT-6: the descriptor had NeoForge's list before
  v0.6.0-beta). On Forge 52 the port's version for 1.21.1 copies NeoForge's config system and Forge's config classes:
  its jar loads as on Fabric for its NeoForge half, and the in-game mod provides its Forge half (`ForgeConfigRegistry`,
  `ForgeModConfigEvents` of `fabric.api.forge.v4`) on Forge's config system the same way, the jar's copy of that package
  left out for it (CFG-4); its test mod checks it as Forge 47's does. **Mod Menu**'s Fabric build isn't loaded on Forge,
  which lists mods itself; Fabric mods' Mod Menu entrypoints give it their config screens (above).
- **Enum extensions:** a Fabric mod adding enum constants with Mixin's enum extensions (Farmer's Delight Refabricated)
  is disabled on Forge 61 to 66, whose Mixin 0.8.7 predates them (CORPUS-36).
- **The corpus runs** (informational): the main corpus and the advanced one run on every Forge version in extended
  runs. What they met there, and how it was fixed, is in [roadmap.md](roadmap.md)'s *Compatibility baseline* and in
  [FINDINGS.md](FINDINGS.md); what is open, in its [*Open findings*](roadmap.md#open-findings). Lithostitched and
  Terralith, which pre-flight disabled until CORPUS-33, load on Forge 61 to 66.

### Unsupported on Forge

What Fabric mods can't rely on there. The report names every hook left out; these are the ones nothing on Forge does
instead.

**Not supported on Forge** (Forge's code differs where upstream hooks in):

- **Item API.** On Forge 61 to 66, `allowContinuingBlockBreaking` as the stack changes while mining (client), where
  Forge asks its own hook.
- **Content registries.** On Forge 64 to 66, an `EntityFluidInteractionRegistry` behaviour: entities swim, travel,
  sprint and float a boat in a fluid as its Forge fluid type lets them, which for a Fabric fluid is Fabricated's,
  treating it as water when it is tagged `minecraft:water` and as air otherwise.
- **Lifecycle events.** `BLOCK_ENTITY_UNLOAD` doesn't fire for a block entity a chunk drops as it reads it again (Forge
  61 to 66, server; Forge 47, client); the other block entity and chunk events fire.
- **Object builder.** A Fabric wood type's sign and hanging sign materials (Forge 61 and 64) and its hanging sign edit
  screen's texture (Forge 61 to 66).
- **Registry sync.** Data generation of other mods' dynamic registries (Forge 61 to 66). Forge syncs registry ids
  itself, and Fabricated runs the built-in registries' freeze after the main initializers (CORPUS-32), where upstream's
  bootstrap hook did. On Forge 61, and on Forge 47 for item colours and particles too, the remapping of block colours
  by id, which Forge does its own way; on Forge 47, upgrading a world from before 1.18 with other mods' structures.
- **Fluid rendering.** A Fabric fluid render handler's overlay sprite on the sides of a fluid beside glass or leaves;
  Forge's own overlay drawing stays (all five).
- **Forge 47's interface mixins.** Its Mixin (0.8.5) applies no injector in an interface mixin. Fabricated applies these
  there itself, as Mixin applies them on Forge 52 and 61 to 66 (INTERFACE-1, INTERFACE-2): an `@Inject` at `HEAD`,
  `RETURN`, `TAIL`, a call or a field access, capturing locals or not; a `@Redirect` and MixinExtras' `@WrapOperation`
  of a call; a `@ModifyConstant`; each with the target's parameters and MixinExtras' `@Local` where Mixin takes them.
  The oxidizable blocks registry's (the test mod's pair is in vanilla's oxidation map), `TooltipComponentCallback`'s,
  and Origins', owo-lib's, Carpet's, ServerCore's and Wilder Wild's hooks of this kind apply, and the test mod's
  interface mixin checks each kind (server run). Other injectors in an interface mixin (`@ModifyVariable`, `@ModifyArg`,
  `@ModifyReturnValue`, `@ModifyExpressionValue`...), slices, `@Coerce`, `@Share`, a `@Local` taken by name and a class
  constant a cast or an instanceof check names are left out there, as is a second `@Redirect` of a call one already
  replaces, at that call; a `@WrapOperation` wraps it, as MixinExtras chains them. Fabric API's recipe remainder hook is
  left out, as on Forge 52: Forge replaced the calls two of its handlers hook, and the other two hand state to them
  (ITEM-2); Forge's own hook answers from Fabric's remainder (*Items* above).
- **Sound (Forge 47).** A Fabric sound instance's own audio stream (`FabricSoundInstance.getAudioStream`).
- **Other hooks inside the HUD method.** A Fabric mod's mixin inside vanilla's HUD method (`Gui.render`; on Forge 64
  `Gui.extractRenderState`, on 65 and 66 `Hud.extractRenderState`) that wraps or redirects one of vanilla's calls,
  hooks in at a field, a constant or a jump, or at a call cancels, captures locals or takes arguments the called
  method doesn't, applies but never runs: Forge 61 to 66 skip that code, and on Forge 47 only hooks at the method's
  start and end run (HUD-4, above). Mixins into the methods that draw each part, which Forge's layers call, run.
- **Data generation only.** Removing entries through a Fabric tag appender, Fabric's key sort orders in generated files,
  and, by version, the advancement builder's Fabric interface and vanilla's block loot provider limited to vanilla's
  blocks; a Fabric model provider also writes item definitions for other mods' block items.

**Known limits** (the report says so where each shows, with its finding):

- **Spawn checks** (every Forge version). As on NeoForge, Forge makes `Mob.checkSpawnRules` and
  `checkSpawnObstruction` inside its own spawn position event's hook, so Fabric mods' hooks of them are left out:
  Architectury's spawn check event skips spawners and world generation, and Friends and Foes' world generation spawn
  hook doesn't run. Use the mod's Forge build (SPAWN-1).
- **Screen render wrappers** (Forge 65 and 66). Forge draws the screen in its own layer code, which reads the screen
  itself, so a Fabric mod's wrapper of the screen's render call is left out: Architectury's screen render events,
  FancyMenu's and SpruceUI's hooks around drawing a screen. An `@Inject` at the call applies, and on Forge 61 and 64
  Architectury's and FancyMenu's wrappers apply at Forge's hook. Use NeoForge, or the mod's Forge build (SCREEN-2).
- **Two equal registry entries.** Forge's registries tell entries apart by equality, so a mod registering two equal
  objects under two names fails at the second: Wover Story's main entrypoint on Forge 66 (two equal point-of-interest
  types), and the mod is marked as failed. On Fabric and NeoForge they are two entries. Use NeoForge, or the mod's
  Forge build (REG-13).

## Fabric mods tested

CI checks Fabricated's own test mods as gates on every version. Real mods run in these CI runs too, downloaded from
Modrinth in CI (never redistributed):

- **A corpus of popular Fabric mods** (`ci/mod-corpus.txt`: libraries, interface, rendering, performance and content
  mods, with their dependencies), on a server and a client, on every version (the Forge branches run it too).
  Informational, as two of their clients don't hold yet (below): each run's report lists which load and why the others
  are disabled. Known problems are tracked as CORPUS-n in
  [roadmap.md](roadmap.md#open-findings), and fixed ones in [FINDINGS.md](FINDINGS.md).
- **An advanced corpus of Fabric mods with deep hooks** (`ci/mod-corpus-advanced.txt`: world generation, gameplay
  systems, camera, sound, replays, server tools), on a server and a client of its own, so that one stopping the game
  can't hide the main corpus's results. Informational, as the main corpus is.
- **On v0.6.0-beta's release head** every corpus server starts, and every corpus client but 1.21.1's advanced one
  reaches its world: that one stays on Origins' screen (CORPUS-66). A client with Xaero's World Map can also hang as
  mc-runtime-test stops it (CORPUS-48, on 26.2 and 26.3 in extended runs 42533784185 and 42535987817). Both are the
  mods' own.
- **Sodium and Iris for NeoForge** with Fabric API and the renderer test mod: gated on 26.x, with and without CI's
  shader pack, and Sodium alone on 1.21.11 (see *Renderer mods*). Their Fabric builds load by default and are gated on
  every version, with and without the shader pack. Sodium Extra and Reese's Sodium Options (Fabric addons) next to
  Sodium for NeoForge: gated on 26.1.2 and 26.2; informational on 26.3, where their builds ask for a newer Sodium than
  the one CI pins.
- **Continuity** (connected textures through Fabric's renderer API, with its default resource pack): gated on every
  version it has a build for; its connected textures reach a chunk section.
- **Forgified Fabric API's renderer** beside Fabricated's seams, where Modrinth has a build of it (1.21.1, 26.1.2 and
  26.2): gated; every test block comes through its renderer, none through Fabricated's.
- **The NeoForge builds of JEI, REI, Jade, WTHIT and Simple Voice Chat**, pinned per version, with the test mod's
  plugins declared the Fabric way: gated. Each creates and calls its plugin on every version (WTHIT has no 26.3 build).
- **OneConfig and PolyPlus** (Fabric only), gated where Modrinth has builds for the version (every version since
  v0.6.0-beta): the client reaches its world with no Fabric mod disabled. Their nested
  Kotlin, Compose and MixinSquared jars split packages between jars, which Fabricated now serves from one module.

To report a result for another mod, see [CONTRIBUTING.md](../CONTRIBUTING.md#reporting-a-compatibility-result).
