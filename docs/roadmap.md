# Fabricated: roadmap

Read [`claude.md`](claude.md) first. Fabricated has no finish line: it keeps adding support and refactoring as Fabric,
NeoForge, Forge and the mods move. This file looks forward: the next release's goals, how the project fits together,
where each platform and track stands and what comes next, the open findings, the test matrices and what is still
unverified. It changes in the same change as the work and stays current rather than growing: what happened goes to
[`CHANGELOG.md`](CHANGELOG.md) (a dated entry each working session), a fixed finding moves to
[`FINDINGS.md`](FINDINGS.md), and each port's step-by-step record is in [`PORTING.md`](PORTING.md) §7.

## Vision

Fabric mods run on NeoForge natively, securely and fast: translated once when the game starts (and cached), checked
before they run (mixin pre-flight against NeoForge's patched code), and connected to NeoForge where NeoForge owns a
system, with no dependency but the official Fabric API jar. The aim is the best structured, lightest and most
maintainable way to do this, held to the rules in `claude.md`: every claim checked against source or CI, untrusted jars
handled safely, no network access, original jars never changed, and no code that a CI check doesn't exercise.
The same engine runs on Minecraft Forge, from the same tree, as experimental previews.

## How the project fits together

Rethought on 2026-10-26, when the three platforms came onto one working branch.

**One engine, three platforms, one tree.** Whatever can be decided without running the game is the engine, and every
platform shares it: `core` (archive safety, metadata, dependency resolution, remapping, access wideners, mixin
pre-flight and its repairs, the linkage check, the cache, the report), `fabric-loader-api`, `api` and `loader/common`.
A platform adds only what its loader decides: its discovery services (`loader/<platform>`) and, per Minecraft version,
the in-game mod, its native Fabric API modules and its Fabric API plan (`mc/<platform>/<version>`); what the engine
needs to know of a platform is its `PlatformDescriptor`. NeoForge is the supported platform; Forge 61 to 66 and Forge
47 are previews that use the engine unchanged.

**What keeps the work on track:**

1. **One place for each fact.** This file holds what is open or next. History is in `CHANGELOG.md`, fixed findings in
   `FINDINGS.md`, designs in the design documents; [`README.md`](README.md) says which document holds what.
2. **Parity in view.** The parity table below shows each capability on each platform, and a change that moves a row
   updates it. A feature that reaches one platform says, in the same pass, when or why not the others.
3. **The engine before the platform.** A failure seen on one platform is fixed in the engine when the engine is at
   fault, so every platform gets the fix: ALLOW-1, LEVEL-1, ARGS-1 and HIDE-1 came from Forge runs and hold on
   NeoForge too. Platform facts the engine relies on come from the platform's descriptor, never assumed from NeoForge's
   (PLAT-3, PLAT-4).
4. **Evidence or `UNVERIFIED`.** A status comes from a CI check. A check that can change between runs of the same code
   is a defect of the check (CI-1), not noise to read past.
5. **Gates grow.** A platform's checks become gates once they hold on every version; Forge 61 to 66's server runs are
   next.

## Next release: v0.5.0

v0.4.0-alpha (`releases/v0.4.0-alpha.md`) shipped NeoForge's four versions and the Forge builds as server previews.
v0.5.0 is where the Forge targets get Fabric mods' client side and the platforms' coverage becomes comparable, with no
regression on NeoForge. Each item is done when its CI check is; the tracks below hold the detail.

| Platform | Goals | Status |
|---|---|---|
| All | One working branch and one folder per platform, every branch holding the same tree. | Done (#156 to #158) |
| All | Each Fabric API module's status on every platform in `COMPATIBILITY.md`, so the platforms can be compared. | Done for the servers (*Fabric API modules on Forge*); the Forge clients follow their CI runs |
| All | The engine's platform facts from the platform: the fields its coremods keep private (PLAT-4). | Done (PLAT-4, checked on all five Forge versions) |
| NeoForge (`main`) | The open corpus findings (below) that a generic fix covers. `preLaunch` right after FML's mixin bootstrap (*Loader and translation*, next). ARCH-1: pre-flight's repair families out of `MixinPreflight`, with no change in behaviour. | Open |
| Forge 61 to 66 (`forge`) | Fabric mods' client entrypoints and a client run in CI. Entity and interaction events native on Forge's events, as `main` has them on NeoForge's (F4). The server runs made gates once their checks hold on every version. | Open; the checks' run-to-run noise is fixed (CI-1) |
| Forge 47 (`forge-legacy`) | Fabric's networking on `SimpleChannel` and its storages on Forge 47's capabilities, then client entrypoints and a client run in CI (L4, `PORTING.md` §7). | Open |

## Tracks

### Platforms

Each capability on each platform, from CI. The detail of each port's steps is in [`PORTING.md`](PORTING.md) §7, and
each Fabric API module's status per platform in [`COMPATIBILITY.md`](COMPATIBILITY.md).

| Capability | NeoForge (`main`) | Forge 61 to 66 (`forge`) | Forge 47 (`forge-legacy`) |
|---|---|---|---|
| Minecraft versions | 1.21.11, 26.1.2, 26.2, 26.3 | 1.21.11, 26.1.2, 26.2, 26.3 | 1.20.1 |
| Discovery, translation, the cache, the report | yes | yes | yes, members remapped from Intermediary to SRG |
| Main and server entrypoints, registries open | yes | yes, in Forge's registry events | yes, in Forge's registry events |
| Client entrypoints | yes | no | no |
| Mixins (pre-flight, its repairs, hooks left out), access wideners, injected interfaces, `@Environment` stripping | yes | yes | yes, with MixinExtras nested in the in-game mod |
| Official Fabric API jar | every module loads, server and client | every module loads on the server | every module loads on the server |
| Native Fabric API modules | 17 | 2 (`fabric-api-base`, `fabric-loot-api-v3`) | 5 (`fabric-api-base`, biome, both loot modules, dimensions) |
| Bridges to the platform | fluid types, storages and energy with capabilities, plugins of other mods, custom values, renderer API | fluid types, tags' `fabric:remove`, default attributes (Forge 64 to 66), advancement events (Forge 65 and 66) | fluid types, tags' `fabric:remove` |
| Networking | native, on NeoForge's payloads | upstream's module, unchecked | upstream's module, unchecked |
| Rendering (renderer API, models, Sodium and Iris for NeoForge) | yes | no client yet | no client yet |
| CI | server and client runs gated, and checks in the Fabric API runs; corpus runs informational | server runs informational | server run gated |
| Releases | supported | experimental | experimental |

- **`main`:** NeoForge for 1.21.11, 26.1.2, 26.2 and 26.3, every line stable (NeoForge tagged `26.3.0-stable`; CI
  pins 26.3.0.95 and no version is experimental any more).
- **Next** (the v0.5.0 table above): on `forge`, the client's entrypoints and a client run, then entity and interaction
  events on Forge's events; on `forge-legacy`, networking on `SimpleChannel` and storages on Forge 47's capabilities.
- **Decided** (`RESEARCH.md` §8): Forge 47.4 for 1.20.1, the shared modules kept loadable on Java 17, ForgeGradle for
  modern Forge, CI for pull requests only. L1, the behaviour-neutral seams on `main`: Java 17 for the shared modules
  (their main code compiles at release 17), the loader split (`loader/common`, `loader/neoforge`), the build split
  (`mc/platform-neoforge.gradle`, version conditions keyed on properties) and `build.yml` building pull requests to
  `main` only are done; CI from data and the platform profile follow when the branches run the game. Work happens on
  `claude/dev` and reaches all three branches by pull request (`claude.md`).

### Loader and translation

- **Done:** discovery, metadata, dependency resolution, access wideners, the translation cache, mixin pre-flight with
  its repairs (overloads NeoForge moved, constructors NeoForge kept for vanilla's signature or appended components to,
  with a bridge for handlers taking the parameters, calls made through a subtype or given one more argument, with a
  bridge for handlers taking the call's arguments), `@WrapMethod` handlers checked against the method they wrap,
  NeoForge mods visible to Fabric code
  (nested ones too, with their properties as custom values and their files as container roots). Pre-flight of mixins
  into Fabricated's Fabric API and installed NeoForge mods, nested ones included, and into other Fabric mods
  (R3: Iris's ~20 mixins into Sodium were "not verified" before; on 1.21.11 against their classes remapped as read), cached with the fingerprints of the classes it read. Every declared language adapter created before any Fabric mod's mixin plugin loads, as on
  Fabric (PolyPlus and Polydium declare adapters only to run code that early): translated mods load after
  Fabricated's mod, whose first mixin config's plugin creates them. CI: the test mod's adapter sets a property its own
  mixin plugin checks ("early language adapter OK"); passes on all four versions (`3d5d509`).
- **Next:**
  - `preLaunch` right after FML's mixin bootstrap instead of with `main`, closer to Fabric's order (it must run after
    every mixin config is registered, so a class it loads still gets its mixins).
  - The compatibility plan (`compat-plan.json`, done for refusals and pairings): `skipMixins` and `skipEntrypoints` per
    mod and version range, through the `CompatHints.Patch` path, added with the first entry that needs them (a CI run
    showing the mixin is the fault and the mod works without it).
  - Checking members other mods' mixins add against the mixins that use them (PRE-2 only stops judging them).
- **Later:** the deferred cache items (FC-5, FC-6, FC-7).

### Fabric API coverage

- **Done:** every module planned per version (`COMPATIBILITY.md`): native implementations on NeoForge's events where
  NeoForge owns the system, upstream's code passed through elsewhere, with its conflicting mixins left out. 26.3 on
  Fabric API 0.164.0+26.3.
- **Next:** keep the plans current with each Fabric API release (scripts/import-fabric-api.sh, the skipMixins check).

### Rendering

The Fabric renderer API on NeoForge:

| | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|---|
| Renderer API and Indigo load | yes (M5; upstream's conflicting mixins left out) | yes | yes | yes |
| Model-loading modifiers and extra models | yes (CORPUS-6) | yes since `08aff28` | yes | yes |
| Fabric block models in the world | yes (M5, CI) | yes (`FabricBlockRendering`) | yes | yes |
| Moving blocks (pistons, falling) and the breaking overlay | yes, from parts made from the model's quads (M5; CI checks a falling block and the overlay) | yes (M2) | yes | yes |
| Blocks drawn as entities, other mods drawing blocks from their parts | yes, from parts made from the model's quads (M5) | not checked | not checked | not checked |
| Fabric models nested in a vanilla multipart or weighted model | yes (M5, CI) | yes (M3) | yes | yes |
| Block outlines, a moving block's glow outline | the translucent outline pass from the parts made from the quads | block outlines from the model's parts, as NeoForge sees them; moving blocks have no outline | block outlines: same; the glow outline by Fabric's renderer (M2b; CI checks a glowing falling block) | same as 26.2 |
| Under Sodium for NeoForge | Sodium meshes the parts made from the quads, with Indigo registered as the renderer (M5; informational CI, run 374) | Sodium's own Fabric renderer, Indigo stands down (R1, CI) | same | same |

- **Done:** M1 (Fabric block models in chunk sections, `8e80c6e`, `0dbdfda`, `08aff28`), M2 (moving blocks and the
  breaking overlay, `4db3deb`), M3 (Fabric models inside vanilla multipart and weighted models, `4db3deb`), M4
  (Fabric's model formats through NeoForge's parsers, `c02d85f`), NeoForge-aware `emitQuads` defaults (`75e5867`).
- **Done:** M2b, a glowing moving block's outline (26.2, 26.3): Fabric's renderer draws the outline submit
  into the outline of each quad's render type, in the outline's colour, as upstream does, instead of leaving it to
  NeoForge, which draws it from the model's parts (none for a model with only `emitQuads`). The test mod's falling
  blocks glow, and its model logs when the same moving block reaches Fabric's renderer twice in one frame
  (`frapi moving block outline OK`, gated on 26.2 and 26.3); the gate passed on manual run 40227052304 (`2ce68ec`).
- **Candidate:** Fabric's level-aware `materialFlags` where NeoForge asks whether a block is translucent on 26.x (the
  pass its selection outline is drawn in, a moving block's batch). NeoForge asks its own level-aware method, whose
  default returns the model's context-free flags; upstream's redirects at vanilla's calls are left out. Fabric's
  contract makes the context-free flags hold in every context and lets callers skip the level-aware method, so this
  is accuracy (a model translucent at some positions counts as translucent at all), not a fault; the 2026-10-25 pass
  read the code (NeoForge's `LevelExtractor` and `SubmitNodeCollection` patches, upstream's `LevelExtractorMixin`) and
  corrected the earlier note that the outline pass reads the model's parts.
- **Done:** M5, the renderer API on 1.21.11 in chunk sections, alone and nested (its conflicting mixins
  left out, the sprite getter's finders from Fabricated), with NeoForge-aware `emitQuads` defaults (CI checks the level
  and baked colours). Then everywhere else NeoForge collects a model's parts: a Fabric model's parts are made from its
  quads (moving blocks, the breaking overlay, entities, Sodium for NeoForge), and Indigo's renderer is registered when
  a mod claims a renderer it doesn't register.
- **Candidate:** the same parts for Fabric models on 26.x, for other mods drawing blocks from their parts there (Fabric's
  and Sodium's renderers already cover the game's own paths).
- **Not planned:** a Fabricated-owned quad encoder. Indigo already is the `int[]` encoder; a second one would add code
  without a user-visible gain (`claude.md`).

### Rendering-pipeline mods (their Fabric builds)

**Goal.** Fabric mods that change how the game renders (renderer replacements, shader pipelines, renderer API
implementations, config and UI libraries drawing on their own) run from their Fabric jars, natively and without
external dependencies, wherever that can be made correct. Sodium and Iris have NeoForge builds, which stay the
recommendation; the Fabric-only stack (OneConfig v1, PolyPlus, Polydium) has no other route to NeoForge. Research:
source of Sodium (`dev`, 26.2, 26.1.2, 1.21.11), Iris 26.3, NeoForge's patches (26.1-26.3, 1.21.11), FML, Fabric API
0.164.0, Forgified Fabric API, OneConfig, PolyPlus and Polydium; Modrinth listings UNVERIFIED (not reachable from the
research sandbox).

**Rules for this track** (on top of `claude.md`):
- Only generic capabilities: a fix must hold for any mod using the same pattern, never a branch on a mod's id in
  code. Mod-specific facts are data (the compatibility plan above), reported to the user.
- No code copied from a mod. Sodium's licence (PolyForm Shield) bars it from GPL-3.0 code; a Sodium-specific platform
  layer is out of scope (below).
- Nothing on a per-block or per-vertex path allocates or locks (FRAPI rule 1); each change states its hot path.
- Every capability gets a CI check before it is called supported; a mod run that isn't gated says so (informational).
- A renderer mod's Fabric build stays refused by default while the NeoForge build is better; `loadUnsupported` loads it.

**What rendering mods need, by kind** (what NeoForge changes that a Fabric-on-NeoForge layer must answer):

| Kind | Examples | What NeoForge changes | Fabricated today |
|---|---|---|---|
| Mixins into world rendering and chunk meshing | Sodium, Polydium | Meshing takes NeoForge's extra inputs (model data snapshot, aux light, `AddSectionGeometryEvent` geometry, custom fluid renderers); render-stage events | Pre-flight and its repairs against NeoForge's bytecode |
| Fabric renderer API implementations | Indigo, Sodium's renderer | Level-aware `collectParts`, per-part and per-quad ambient occlusion, baked colours and normals on quads | `FabricBlockRendering` seam, `FrapiDefaults` (FRAPI-1, FRAPI-2), on every version since M5 |
| Shader pipelines | Iris | Pipelines derived at run time (`toBuilder`, a pipeline modifier stack on 26.3), NeoForge's own pipelines | `fabric-rendering-v1` passed through; nothing specific yet |
| Raw GL/LWJGL drawing | OneConfig (Compose and Skia) | NeoForge's early loading screen owns the first GL context; the Vulkan backend (26.2+) is patched | LWJGL is the platform's; nothing specific |
| Vertex formats and fast writers | Iris, Sodium | NeoForge applies baked colours and normals in `VertexConsumer.putBakedQuad`, which fast writers bypass | — |
| Early bootstrap | Sodium's `preLaunch`, PolyPlus and Polydium language adapters | The early loading screen starts before mods are discovered | `preLaunch` runs with `main`; adapters are created lazily (see *Loader and translation*) |
| Config and UI libraries | OneConfig, YACL, Cloth Config | HUD layers, screen events | HUD and screen bridges, Mod Menu entry points in NeoForge's mod list |
| Entity, block-entity and item renderers | PolyPlus cosmetics | Render events and feature renderer registration | Pre-flight |

**Where the Fabric builds stand** (CI's informational run, `loadUnsupported`, 26.x):
- Sodium 0.9.2 and Iris 1.11.4 (Fabric) load through Fabricated on 26.2 (run on `dbdff1d`): pre-flight passes them, Iris
  creates its pipeline, the client joins a world. It then crashed on the first block quad (SODIUM-1, below).
- With the constructor repair (`bee1fe4`), the 26.1.2 and 26.3 runs on `e68cb58` no longer crash: Sodium's Fabric build meshes
  the Fabric test model in its own `LevelSlice` on its chunk builder thread, and the moving block, breaking overlay,
  nested model and level-aware default checks all pass. Shaders weren't enabled.
- Beyond that, Sodium's Fabric platform services answer NeoForge's questions the Fabric way: no NeoForge model data,
  no `AddSectionGeometryEvent` geometry, no NeoForge fluid renderers, no `hidesNeighborFace`, no position-aware light
  emission, no baked colours in immediate mode. Generic work fixes about half (below); the rest is what Sodium for
  NeoForge's own platform module does.
- Iris's Fabric build needs Sodium's Fabric build (its fluid hook targets `sodium.fabric` classes); Iris for NeoForge
  needs Sodium for NeoForge. The compatibility plan refuses either mixed pair with a message, `loadUnsupported` or not.
- Indium has no build for these versions and isn't needed: Sodium provides `indium`.
- OneConfig and PolyPlus (Fabric only; CI's informational run): on `e88a2f9` the 26.3 client stopped at mod
  construction, with 15 of their nested library jars disabled for split packages (PKG-1), MixinSquared among them, so
  Polydium's MixinSquared selector failed. On `72f5472`, with PKG-1 fixed, 3 jars are disabled (adventure-platform-fabric
  for a mixin config in its nested jar, ADV-1); the 26.1.2 client gets to the render thread and fails in OneConfig's
  config manager on night-config's JSON module, which Fabricated took for NeoForge's (PLAT-1, fixed since); the 26.3
  client still stops at mod construction (the next run's summary prints the crash's cause). On `b2b91e8`, with PKG-2,
  PKG-3 and PLAT-1 fixed, 1 jar is disabled (ADV-1, fixed since) and both clients reach "Initializing game", where
  Skiko finds no native library (NATIVE-1, fixed since). On `cf41167` (pull request #33) no jar is disabled on 26.1.2
  and 26.3, and neither client writes a crash report; whether they reach a world is UNVERIFIED (the summary now prints
  the log's last lines when a client doesn't). On 1.21.11 adventure's shared jar failed pre-flight on prefixed
  `@Shadow` names (REMAP-1, fixed since), and the client wrote no crash report and no error, and reached no world
  (POLY-1). On `5b42545` (pull request #34) 1.21.11 disables only Fabric API's model loading module (its renderer API
  doesn't run on NeoForge) and crashes at "Initializing game" with an `UnsupportedOperationException`; on 26.1.2 and
  26.3 an exception reaches `Minecraft.run`, OneConfig logs "OneConfig UI warm-up failed: OneConfigUIScreen: setContent
  did not take (poisoned=true)", and the game stops without a crash report (POLY-1).

**Plan, by coverage per effort** (each with its CI check):

| # | Capability | Covers | Status |
|---|---|---|---|
| 1 | FRAPI-2: NeoForge's baked colours and normals kept by `FrapiDefaults` | NeoForge models drawn by any Fabric renderer | Done (`ce52fb2`). Gate "frapi baked colors OK": the terrain test block's model has a `neoforge_data` colour, checked as Fabric's renderer emits it. Passes on 26.2 and 26.3 (`3d5d509`) |
| 2 | Constructor repair for appended NeoForge components, with a handler bridge | Sodium's `BakedQuadMixin` (SODIUM-1); any mixin into NeoForge-extended records (`BakedQuad`, `MaterialInfo`, `FluidModel`) | Done (`bee1fe4`, unit tests). Gate "frapi baked quad constructor hook OK": the test mod's mixin at the end of `BakedQuad`'s vanilla constructor marks the quads NeoForge bakes. Passes on 26.2 and 26.3 (`3d5d509`) |
| 3 | Fabric-order early hooks | PolyPlus, Polydium, OneConfig | Language adapters done (server check "early language adapter OK"); `preLaunch` next |
| 4 | Pre-flight of mixins into other mods, pairing rules | Iris with Sodium, Polydium, OneConfig's compat mixins, Sodium addons | Done: installed NeoForge mods and, on 26.x, other Fabric mods (R3); pairing rules (`notWithNeoForge`, unit and pipeline tests) |
| 5 | Built-in compatibility plan (data) | Refusals and pairings; per-mod mixin skips | Done: core's `compat-plan.json` replaces the hard-coded map; skips wait for an entry with evidence (Sodium's window mixin S10 is UNVERIFIED) |
| 6 | Fabric fluid rendering backed by NeoForge's (custom fluid renderers on 26.x, `IClientFluidTypeExtensions` on 1.21.11) | NeoForge fluids under a Fabric renderer | Planned |
| 7 | Block-entity render data and NeoForge model data, bridged both ways | Fabric renderers reading NeoForge block entities' data | Planned |
| 8 | Renderer contract: report the active renderer, optional every-block routing | Third-party renderer API implementations | The log names the renderer drawing Fabric models (FRAPI-3, won't fix); every-block routing planned |
| 9 | Early-window opt-out for mods that need to act before the first GL context (driver workarounds) | Sodium's `preLaunch` workarounds | When a real report needs it |
| 10 | Corpus runs for the Polyfrost stack and Fabric Sodium/Iris | Evidence for 3-8 | Both exist (informational): Fabric Sodium/Iris with `loadUnsupported`, and OneConfig with PolyPlus where Modrinth has builds |

**Not planned:** a Sodium-specific NeoForge platform layer (the size of Sodium's own `neoforge` module, re-checked
every Sodium release, and a licence question), and Iris pipeline fixes beyond its public API. Sodium and Iris for
NeoForge already cover those users.

### Ecosystem integrations

- **Done:** Mod Menu's config screens in NeoForge's mod list, Forge Config API Port on NeoForge's own config system,
  a Fabric mod's Sodium options page and mixin overrides given to Sodium for NeoForge, NeoForge mod properties as
  Fabric custom values (`fabric:provides` included).
- **Done:** custom values reach NeoForge mods where they read them (CONV-1): all as mod properties, the
  namespaced ones also as keys of the file and of the mod's entry (Sodium's and Lithium's option overrides). The
  entrypoint conventions of other mods' NeoForge builds are one data file, `interop.json`.
- **Done:** plugins of recipe viewers, tooltip mods and Simple Voice Chat: a Fabric mod's JEI, EMI, REI, Jade and
  `voicechat` entrypoints become the annotations their NeoForge builds scan for (`Interop` and its data file
  `interop.json`, `FabricLanguageLoader`); WTHIT reads the jar's plugin file; Jade's per-mod settings become its mod
  properties. An informational CI client runs the viewers'
  NeoForge builds with the test mod's plugins: on `e68cb58` JEI, REI and Jade each constructed the test plugin once
  and called it on 26.1.2 and 26.3, and WTHIT did on 26.1.2 (Modrinth gave no WTHIT build for 26.3).
- **Done:** the hosts' builds are pinned per version (`plugin_hosts` in CI's matrix) and the run is a gate:
  each host must construct and call the test mod's plugin. On `b679b92` JEI, REI, Jade, WTHIT and Simple Voice Chat
  did on 1.21.11, 26.1.2 and 26.2, and all but WTHIT (no 26.3 build) on 26.3.
- **Done:** storages in items bridged both ways (`ItemStorage.ITEM`/`FluidStorage.ITEM` with NeoForge's
  item capabilities), and energy with Team Reborn's library when a Fabric mod brings it (blocks and items, both
  ways), the first bridge to a Fabric library rather than Fabric API. CI: the transfer check fills each side's fluid
  cell from the other; an energy check runs where Modrinth has the library, of its own or nested in Tech Reborn or
  Modern Industrialization (on `9621e53` it had neither for any version).
- **Next:** EMI once it has NeoForge builds for these versions.

### Quality: CI, corpus, performance

- **Done:** CI builds and tests every version, then runs a dedicated server and a client (Xvfb, a singleplayer world)
  with the test mods, with Fabric API, with Sodium and Iris for NeoForge (26.x), and with a corpus of popular Fabric
  mods from Modrinth (informational).
- **Done:** an informational server run on the newest NeoForge build of each version, next to the pinned
  one (Fabricated accepts every build of a version, and NeoForge's internals move between builds: REG-1).
- **Done:** CORPUS-2 (fixed by REG-2): on `72f5472` the corpus clients run through and exit normally, and Xaero's
  World Map alone reaches a world ("New world map session initialized!") on 26.1.2 and 26.3.
- **Done:** an advanced corpus of Fabric mods with deep hooks (`ci/mod-corpus-advanced.txt`), on a server
  and a client of its own; and a digest of each corpus log's errors with the first stack of the most frequent
  (`ci/log-digest.py`), as a mod that loads may still fail when its code runs.
- **Done:** a gate for Fabric API's client modules most client mods use, on 26.x: a Fabric key mapping in
  the game's options, HUD elements drawn (one added last, one attached after chat), a screen's init and draw events,
  client commands registered on joining (`ClientApiTestMod` in the Fabric API client run). 1.21.11 compiles its test mod against stand-ins, which
  these modules don't have yet.
- **Done (health pass after PR #64):** a review of the whole engine on four fronts (stability, untrusted input,
  overhead, drift from the design): SEC-1 to SEC-4, CACHE-1, CACHE-2, BRIDGE-1 and BRIDGE-2 fixed, SEC-5, PERF-1,
  PERF-2, CACHE-3 and ARCH-1 open; the design documents corrected where the code had moved on. CI no longer runs the
  game matrix for a change to documentation alone, and each job has a time limit.
- **Next:** the gates listed in the rendering-pipeline plan; checks for what the corpus mods do in the world.

## Findings

Open findings, by ID, with the severities [`FINDINGS.md`](FINDINGS.md) defines. A finding moves to `FINDINGS.md`,
with its cause, why the tests missed it and its fix, in the change that fixes it.

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| CI-2 | P3, UNVERIFIED | `ci/neoforge/smoke-server.sh`, `ci/neoforge/smoke-client.sh` ("a test mod check reported a failure") | The failure path prints the failed checks with `grep ... \| head -n 5` under `set -euo pipefail`: with more failed lines than `grep`'s buffer holds, `head`'s exit can kill `grep` with SIGPIPE and stop the script before its remaining diagnostics (CI-1's pattern). The run fails either way. | No run has had that many failed lines; fix with the next change to these scripts. |
| PLAT-2 | P3, UNVERIFIED | core `PlatformLibraries` | Other prefixes also cover whole library families (`org/lwjgl/`, `io/netty/`), of which the platform may ship some modules only: a mod nesting another module (LWJGL's NanoVG, say) would lose it, as PLAT-1 did. No case seen yet. | Check each prefix against the packages the platform really has (the loader computes them), and keep a prefix only where the family is complete. |
| FC-5 | P3 | `TranslationCache.verifyIntegrity` | Hashes are memoised by file metadata. On Windows the key is size and mtime only, and `entry.json` itself isn't hashed. | Needs a decision on cost: re-hashing every launch versus trusting metadata. |
| FC-6 | P3 | `TranslationCache.store` | Two cold launches of the same side that share one game directory race on delete-then-move. | Needs a cross-process file lock. Rare. |
| FC-7 | P3, UNVERIFIED | cache cleanup | `Files.walk` may follow Windows directory junctions. | Must be verified on Windows first. |
| P3-2 | P3 | `LootBridge` | One global reload manager. Two overlapping reloads can label a loot table with the wrong source. | Low impact. Needs a per-reload key. |
| P3-8 | — | `BlockEntityTypeMixin` | Field replaced without `volatile`. | Won't fix: written only during mod initialization, before world threads start. |
| CFG-2 | P3 | `gradle.properties`, `Pipeline` | The Fabric Loader API version is written in two places. | Build-time duplication only. |
| COMPAT-2 | — | NeoForge's loading logs | NeoForge's dedicated server (every version) and its 1.21.11 client log each mod loading warning as its raw translation key and arguments (`"{} [{}]", translationKey, translationArgs`), for every mod: Fabricated's show as `[Fabricated] {0} [[Fabric mod ... was not loaded: ...]]`. 26.x clients log the text, and the warnings screen formats it everywhere. | Won't fix: NeoForge's logging, and the line carries the whole text. Putting the text in the key instead would need escaping for the formatter, which those logs would then print. |
| CORPUS-12 | — | Zoomify (26.x corpus) | Its "universal" jar, the build Modrinth lists for Fabric, is a NeoForge mod too: NeoForge loads it itself and stops at startup without Kotlin for Forge ("Missing language loader klf"). Not Fabricated's to handle: the NeoForge mod's dependency is missing. | Taken out of the corpus (run 355). |
| CORPUS-13 | — | malilib (with Tweakeroo and MiniHUD), WTHIT (corpus, every version) | Past RELOAD-2, every corpus client crashed at "Initializing game" (run 357): `NoSuchMethodError` for malilib's `IMixinGuiRenderer.malilib_getSpecialGuiRenderers()`, which malilib calls from its own injector into `GameRenderer`'s constructor to add its picture-in-picture renderers. The accessor reads `GuiRenderer.pictureInPictureRenderers`, a map of renderers that NeoForge replaced with a map of renderer pools on every version, so it can't apply, and whether it is left out (PRE-9) or its interface is, malilib stops the game. WTHIT's Fabric build throws from its own entrypoint on NeoForge ("Fancy Mod Loader detected"); Fabricated logs it and goes on. | Not repairable in general: malilib would need to fill NeoForge's pools. Taken out of the corpus, so the corpus clients show what comes next; WTHIT's NeoForge build is used when installed. Both are in the compatibility plan, so a player gets the reason instead of a crash. PIP-1 gives the map back; malilib is still refused by the compatibility plan until CI shows it working. |
| CORPUS-14 | — | Friends and Foes (corpus, every version) | Three of its mixins fail pre-flight: its `@WrapOperation` of `Mob.checkSpawnRules` in `NaturalSpawner.spawnMobsForChunkGeneration`, a call NeoForge's build replaces, with `checkSpawnObstruction`, by one `EventHooks.checkSpawnPosition` hook (read in NeoForge's patch); its enum mixin into `Raid$RaiderType`, whose arguments (`FriendsAndFoesEntityTypes.ICEOLOGER.get()`, an `int[]`) are computed when the class loads, which NeoForge's enum extension file can't carry; and its shadow of `ParticleResources.providers`, a field NeoForge's build doesn't have. Its `MobCategory` constants translate since ENUM-2. | Not repairable in general. Its NeoForge build is used when installed. Since LEAVE-1 (run 379) its enum mixin alone kept it from loading: the mod's own code reads those constants. ENUM-3 translates it. |
| CORPUS-3 | — | Balm (Waystones), JEI (26.3 corpus) | Their mixins target code NeoForge changed: Balm `CropBlock.getGrowthSpeed`'s signature and `LivingEntity.removeAllEffects`'s loop, JEI `KeyMapping.setKey` (NeoForge calls `setKeyModifierAndCode` there instead). Pre-flight disables them with the reason. ShulkerBoxTooltip, listed here before, is PRE-7. | Not repairable in general; their NeoForge builds are used when installed. Since LEAVE-1 (run 379) both load without those mixins on every version: Balm loses only its `removeAllEffects` and `getGrowthSpeed` hooks, JEI its key-reset ones. |
| CORPUS-5 | P3, cosmetic, UNVERIFIED | Traveler's Backpack (26.3 corpus) | NeoForge warns "Missing FluidModel" for its potion fluids. NeoForge's check runs inside `FluidStateModelSet.bake`, and upstream's `FluidStateModelSetMixin` wraps that method to add the models of Fabric-registered fluids after it returns, so the warning comes before those models are added. Whether the potion fluids have a model at all is UNVERIFIED. | Could register Fabric's fluid models through NeoForge's `RegisterFluidModelsEvent` instead. Low value. |
| CORPUS-17 | P3, UNVERIFIED | REI's Fabric build (26.2, 26.3 corpus) | REI logs "Failed to fill display for recipe" for Farmer's Delight's cooking pot recipes (2 to 4 times) and, on the 26.3 dedicated server, for 560 brewing recipes (run 379). REI logs them without a stack (run 381's digest found none to print). | Open: needs REI's own log at debug level, or its source. |
| CORPUS-18 | — | Do a Barrel Roll (advanced corpus clients, every 26.x version) | Mixin stopped the client applying its `CameraMixin`: the argument modifier `doABarrelRoll$setRoll` found no injection point in `Camera.setRotation(FF)V`, which NeoForge reduced to a call of `setRotation(FFF)V` (read in the game's code on `0799e2a`). Pre-flight's overload repair moves exactly that case (`MixinPreflightTest.anArgumentModifierInAMethodThatOnlyCallsItsOverloadMovesThere`), yet no translated copy of the mixin was found. On `29da4f5` the diagnostics found no line of Fabricated's report naming the mod, which wasn't among its loaded, skipped or disabled mods either, while NeoForge's mod list in the crash report had it. Fabricated leaves a jar with `fabric.mod.json` to NeoForge only when it also has `META-INF/neoforge.mods.toml`, and Do a Barrel Roll's sources keep one for the NeoForge build: so NeoForge loaded the jar itself and applied the Fabric build's mixins untranslated (inferred: the jar's entries weren't listed). | MULTI-1. On `c725f6f` Fabricated took the jar on every version and disabled it with a reason instead: the 26.2 build depends on YetAnotherConfigLib, which the advanced corpus didn't fetch (its Modrinth build doesn't list it as required; added now), and pre-flight fails the 26.3 and 1.21.11 builds' `CameraMixin`, whose `@WrapWithCondition`s expect the third and fourth `setRotation(FF)V` calls in `Camera.alignWithEntity` (`setup` on 1.21.11), which NeoForge's has fewer of. |
| CORPUS-20 | — | owo-lib (corpus clients, every version) | Past LEAVE-2, PIP-1 and PRE-12, two of owo's mixins still failed and owo stayed disabled with every mod needing it: its GUI library's `GuiRendererMixin` (which adds an interface) wraps a read of `pictureInPictureRenderers` in `GuiRenderer.close`, which NeoForge's `close` no longer makes, and its `Matrix3x2fStackAccessor` targets JOML's `Matrix3x2fStack`, which FML loads outside its transforming class loader. | Closed by LEAVE-3 and LIB-1b: owo loads on every version, with the mixins NeoForge's code can't take left out (CI of PR #88: `braid.GuiRendererMixin`'s read in `close`, a `@Group` of `MinecraftMixin` neither of whose injectors finds its point, `DataResultMixin` into DataFixerUpper, `SimpleJsonResourceReloadListenerMixin`, and on 26.1.2 `braid.LevelRendererMixin`, on 1.21.11 `ui.GuiRendererMixin`'s `@ModifyArgs` in `executeDraw`). |
| SEC-5 | P3 | `CandidateScanner` (nested jars) | Nesting is bounded per top-level jar (depth 3, 1024 jars, 512 MiB of nested jars), not across all of them: many hostile jars cost that much each. The 1 MiB cap on metadata applies after reading the entry, which since SEC-4 is bounded by its declared size and the compression ratio. | Bounded by what the player puts in `mods/`; a global budget would need a size no legitimate pack exceeds. |
| PERF-1 | P3, UNVERIFIED | `PacketEncoderContextMixin`, `PacketDecoderContextMixin`, `GenericPacketSplitterContextMixin` (26.x) | Each wraps every packet's encoding or decoding (vanilla's too) with `@WrapMethod`, which allocates the `Operation`'s argument array, a `ScopedValue` carrier and a lambda per packet. Not measured. | Measure on a busy server; if it shows, bind the context only for packets whose codecs read it. |
| PERF-2 | P3 | `NeoForgeMods` (`NestedNeoForgeJars`), `FabricDependencyLocator`, `Pipeline` (1.21.11 mappings) | A launch with everything cached still reads every NeoForge mod's nested jars, re-reads and hashes `game-mod.jar`, and on 1.21.11 loads the mapping table, whichever mods need it. | Cache the nested NeoForge mods' metadata by their files' hashes; load the mapping table when a mod is translated or calls `MappingResolver`. |
| ARCH-1 | P3 | `MixinPreflight` (2,872 lines, 113 methods) | The checker, about fourteen repair families, call-graph helpers and state analysis share one class; repairs run in a fixed order, each guarded against an earlier one having moved the annotation, a guard a new repair placed between them could miss (BRIDGE-2 was one such interaction). | Split the repairs out by family behind one interface (pattern in, moves out), with the order and the guards in one place; worth doing before the next repair family. |
| CORPUS-22 | P3 | Biomes O' Plenty on 1.21.11 (`MixinEntity`) | Disabled on 1.21.11 servers and clients: its `@Redirect` at `FluidState.getTags()` in `Entity.updateFluidOnEyes`, which only records the fluid state at the eyes and returns the tags as they are, has no call to take, as NeoForge's 1.21.11 patch reads the state's fluid type there instead (read in its `1.21.11` branch). The mixin adds an interface whose method returns that state, and a field the redirect writes, so the hook can't be left out alone (LEAVE-1). | Not repaired: no call there gives the handler what it takes. Its NeoForge build is used when installed. |
| CORPUS-30 | — | Wilder Wild on 1.21.11 (advanced corpus client) | NeoForge's model loading logs "Missing textures in model wilderwild:block/potted_tumbleweed" (45 times on #150's run) and "Unresolved texture references in wilderwild:block/baobab_nut_hanging_stage_1". Its `1.21.11` branch's models have them: `potted_tumbleweed.json` names its sand texture `bminecraft:lock/sand`, and `baobab_nut_hanging_stage_1.json` declares the texture `#texture` while its faces read `##texture` and its particle `#texture`. Vanilla's model loading logs the same on Fabric. That the released jar matches the branch is UNVERIFIED. | Wilder Wild's own on 1.21.11; left as it is. |
| CORPUS-29 | — | Tech Reborn on 26.1.2 (server and client corpus, every run since at least #101) | 67 errors a server run (134 with the client): "Couldn't parse data file 'techreborn:crafting_table/uu_matter/misc/plant/twisting_vines': Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: reborncore:padded" (the first of them; CI of PR #133). Its `26.1` branch (`221345f`) has 67 recipe files of type `reborncore:padded` and no code registering a serializer of that name, RebornCore's included (only the JSON files name it). Vanilla's registry lookup fails the same on Fabric. That the released jar matches the branch is UNVERIFIED. | Tech Reborn's own on 26.1.2; left as it is. |
| CORPUS-28 | — | Wilder Wild 4.3.3 on 26.3 (advanced corpus client) | NeoForge's model loading logs "Exception loading blockstate definition: 'minecraft:reinforced_deepslate/mod/wilderwild' for variant: 'axis=x'" (3 times; CI of PR #127). Wilder Wild ships a reinforced deepslate blockstate with an `axis` property, for the pillar block it makes of it when `newReinforcedDeepslate` is on (the default). On 26.2 a mixin does that (`block.reinforced_deepslate.BlocksMixin`, in its `4.3.3-mc26.2` tag) and nothing is logged. Its 26.3 build does it through FrozenLib's block modification event instead (`WWBlockModifications`, a `frozenlib:events` entrypoint in its `frozenlib.json`; `4.3.3-mc26.3` tag), which the earlier note missed: it put the warning down to Wilder Wild shipping a blockstate for a mixin it had removed. | Fabricated's: LOADER-4, gone since #145 (the 26.3 advanced client logs it no more). |
| CORPUS-27 | — | FrozenLib's config (advanced corpus, every 26.x version) | FrozenLib logs "Unable to read config entry" for Wilder Wild's entries (`newReinforcedDeepslate`, `unpassableRail`, `reachBoostBeacon` and others; 28 a run on 26.1.2, CI of PR #127). Read in FrozenLib's `ConfigSerializer` and `ConfigData`: an entry is read from the config file's map, and an entry absent from it fails to parse and logs this; on a fresh config directory, as in every CI run, the map is empty, so each entry read before FrozenLib first saves the file logs it, and keeps its default. | FrozenLib's first-run behaviour, as on Fabric (by its source; not run there): left as it is. |
| CORPUS-26 | — | Translated mods' packs on 1.21.11 (First-person Model, its TRansition library) | Vanilla logs "Couldn't load mod/firstperson pack metadata: Pack declares support for format 75, but game versions supporting formats 17 to 81 require a supported_formats field" (8 to 12 times a run, client and server; CI of PR #91). NeoForge loads the pack anyway: a mod's pack whose metadata doesn't read is compatible to its `ResourcePackLoader.readMeta` (read in its 1.21.11 and 26.3 source). Fabric reads no mod's own pack metadata for its grouped mod resources, so the error is NeoForge's alone. Lootr's built-in `old_textures` pack logs the same family ("missing mandatory fields min_format and max_format"); a built-in pack's metadata is read on Fabric too, so whether it fails there is UNVERIFIED. | Noise, nothing lost: left as it is. |
| CORPUS-25 | P3 | Terralith (26.3 server corpus, every run since at least run 420) | About 130 warnings per server run from NeoForge's `ModifiableBiomeInfo`: "Cannot modify spawn settings of biome terralith:alpha_islands, found unexpected attribute modifier OverrideModifier[]" (the first of them; runs 40241680133 and 40244234167). NeoForge 26.3 refuses to modify the spawns of a biome whose `natural_mob_spawns` attribute uses a modifier other than `overlay` (`ModifiableBiomeInfo`, read in its source), so biome modifiers can't add spawns to those biomes. | Open, likely Terralith's: the count fits every Terralith biome declaring an `override` modifier, which would warn on NeoForge without Fabricated too; UNVERIFIED, as Terralith's 26.x data isn't in its public repository. |
| CORPUS-24 | — | Ledger (advanced corpus servers, every 26.x version) | Exposed logs "NOT NULL constraint failed: players.player_name" for `INSERT INTO players (first_join, last_join, player_id)` (run 40237889272: Ledger 1.3.22 on 26.1.2). The stack runs from Ledger's `DatabaseManager.insertActions` through `getOrCreatePlayerId`, which inserts a player row with its UUID alone for an action's source player who never joined; only Ledger's join handler writes the name (read in its source, 1.3.25 the same). Ledger takes any player as an action's source, fake players included, and the test mod's Fabric fake player (`InteractionTest`) breaks and uses blocks on every server smoke: the same happens on Fabric. | Closed: not Fabricated's (Ledger's, met through the test mod's fake player). |

## Compatibility test matrix (renderer and model APIs)


| Mod | What it uses | 1.21.11 | 26.x | Evidence |
|---|---|---|---|---|
| `fabricated-testmod-frapi` (this repo) | `ModelLoadingPlugin` after-bake modifier, its own `emitQuads` in sections, as a falling block and with the breaking overlay; a `fabric:type` model as a multipart part | — | **passes** on 26.1.2, 26.2, 26.3 | CI "Fabric renderer API in the world", run 246 |
| Traveler's Backpack | Custom unbaked model deserializer | loads | loads, its custom block models too (M4) | corpus run on `08e9a5f` |
| Continuity, Entity Model Features, Entity Texture Features | Renderer API (connected textures); entity rendering | not checked | 26.3: translated and in NeoForge's mod list; the corpus client runs through since REG-2, but nothing checks their rendering | corpus runs on `496d7a9`, `29dca60`, `72f5472` |
| Sodium and Iris for NeoForge | Sodium's own Fabric renderer meshes every block; Iris on Sodium | not tested | **passes** on 26.1.2 and 26.3, 26.2 see the session notes | CI "Fabric models under Sodium", run on `7fe52ed` |
| Athena | Renderer API (connected textures library) | not in corpus | not in corpus | same (UNVERIFIED) |
| Create Fabric | Renderer API, model loading | no Fabric build | no Fabric build for 26.3 | corpus fetch, run 233 |
| Supplementaries / Moonlight | Baked models through the renderer API on Fabric (UNVERIFIED) | no Fabric build | no Fabric build for 26.3 | corpus fetch, run 233 |

## Performance baselines

Translation time per mod on a cold cache, taken from `fabric-bridge-report.txt` in CI run 233 (GitHub-hosted
`ubuntu-latest`). Mods translate in parallel, so the sum is CPU work, not wall time.

| Version | Run | Mods translated | Sum | Median | Slowest |
|---|---|---|---|---|---|
| 26.3 | server, test mods + Fabric API | 23 | 572 ms | 19 ms | 120 ms |
| 26.3 | server, mod corpus | 79 | 3,425 ms | 12 ms | 385 ms |
| 1.21.11 (remapped) | client, test mods + Fabric API | 25 | 1,375 ms | 41 ms | 204 ms |
| 1.21.11 (remapped) | server, mod corpus | 87 | 15,844 ms | 59 ms | 3,433 ms |

The chunk meshing hot path has no frame-time or allocation measurement: CI has no GPU and no profiler. Per block,
`FabricBlockRendering` does a `ClassValue` lookup and a `ThreadLocal` read, and hooks the call with `@WrapWithCondition`, so
nothing is boxed or allocated. Fabric models go through Indigo's own path. A measured in-game allocation baseline is
open (see *Unverified and blockers*).

## Unverified and blockers

- **The local sandbox can't build the Minecraft modules** (`maven.neoforged.net`, Mojang and `maven.fabricmc.net`
  are blocked), so everything game-facing is verified only in CI.
- **Renderer API gate: passes** on 26.1.2, 26.2 and 26.3 (runs 236 and 237), and on 1.21.11 for chunk sections (runs
  365 and 366). On 26.3 the log shows the pre-flight
  moving `ModelBakeryMixin#onReturnInit` to NeoForge's constructor, then `frapi emitQuads called (level
  RenderSectionRegion, thread Worker-Main-2)` and `frapi terrain OK`: the Fabric model was drawn by a chunk-section
  compile worker.
- **Mixin's selection of the first match for a name-only selector** is the basis of `08aff28`. It is confirmed by
  run 229's crash (`SectionCompilerMixin`, "Scanned 0 target(s)"), by run 233's gate failure and by its passing once
  the selector was moved.
- UNVERIFIED: FC-7 (junctions), PH2-13's likelihood, whether Athena has Fabric builds for these versions (Continuity
  has one for 1.21.11: the corpus loads it), and an in-game allocation baseline for the meshing path.
