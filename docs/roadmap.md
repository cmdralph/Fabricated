# Fabricated: roadmap

Read [`claude.md`](claude.md) first. Fabricated has no finish line: it keeps adding support and refactoring as Fabric,
NeoForge and the mods move. This file says where each track stands and what comes next, and keeps the findings log,
the test matrices and what is still unverified. Update it in the same change as the work. Add a dated entry to the
changelog at the end for each working session; keep the rest current rather than appending copies.

## Vision

Fabric mods run on NeoForge natively, securely and fast: translated once when the game starts (and cached), checked
before they run (mixin pre-flight against NeoForge's patched code), and connected to NeoForge where NeoForge owns a
system, with no dependency but the official Fabric API jar. The aim is the best structured, lightest and most
maintainable way to do this, held to the rules in `claude.md`: every claim checked against source or CI, untrusted jars
handled safely, no network access, original jars never changed, and no code that a CI check doesn't exercise.

## Tracks

### Loader and translation

- **Done:** discovery, metadata, dependency resolution, access wideners, the translation cache, mixin pre-flight with
  its repairs (overloads NeoForge moved, constructors NeoForge kept for vanilla's signature or appended components to,
  with a bridge for handlers taking the parameters, calls made through a subtype), NeoForge mods visible to Fabric code
  (nested ones too, with their properties as custom values and their files as container roots). Pre-flight of mixins
  into Fabricated's Fabric API and installed NeoForge mods, nested ones included, and on 26.x into other Fabric mods
  (R3: Iris's ~20 mixins into Sodium were "not verified" before), cached with the fingerprints of the classes it read. Every declared language adapter created before any Fabric mod's mixin plugin loads, as on
  Fabric (PolyPlus and Polydium declare adapters only to run code that early): translated mods load after
  Fabricated's mod, whose first mixin config's plugin creates them. CI: the test mod's adapter sets a property its own
  mixin plugin checks ("early language adapter OK").
- **Next:**
  - `preLaunch` right after FML's mixin bootstrap instead of with `main`, closer to Fabric's order (it must run after
    every mixin config is registered, so a class it loads still gets its mixins).
  - A built-in, data-driven compatibility plan for third-party mods per Minecraft version (mod id and version range to
    `skipMixins`, `skipEntrypoints`, pairing rules, notes), like `fabric-api-plan.json`, replacing the hard-coded
    `NEOFORGE_BUILD_ONLY` map and reusing `CompatHints.Patch`.
  - Pre-flight of mixins into other Fabric mods' classes on 1.21.11 (their classes are only remapped as each is
    translated), and checking members other mods' mixins add against those mixins (PRE-2 only stops judging them).
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
| Renderer API and Indigo load | no (upstream's mixins fail pre-flight) | yes | yes | yes |
| Model-loading modifiers and extra models | disabled (needs the renderer API) | yes since `08aff28` | yes | yes |
| Fabric block models in the world | no | yes (`FabricBlockRendering`) | yes | yes |
| Moving blocks (pistons, falling) and the breaking overlay | no | yes (M2) | yes | yes |
| Fabric models nested in a vanilla multipart or weighted model | no | yes (M3) | yes | yes |
| Block outlines, a moving block's glow outline | no | from the model's parts, as NeoForge sees them | same | same |
| Under Sodium for NeoForge: Fabric models meshed by Sodium's own Fabric renderer, Indigo stands down | no (neither Sodium for NeoForge nor Fabricated has the renderer API there) | yes (R1, CI) | yes (R1, CI) | yes (R1, CI) |

- **Done:** M1 (Fabric block models in chunk sections, `8e80c6e`, `0dbdfda`, `08aff28`), M2 (moving blocks and the
  breaking overlay, `4db3deb`), M3 (Fabric models inside vanilla multipart and weighted models, `4db3deb`), M4
  (Fabric's model formats through NeoForge's parsers, `c02d85f`), NeoForge-aware `emitQuads` defaults (`75e5867`).
- **Next:** M2b, block outlines and a moving block's glow outline for Fabric models (today they come from the model's
  parts as NeoForge sees them, so a model with only `emitQuads` has none).
- **Later:** M5, the renderer API on 1.21.11 (its own mixins don't apply on NeoForge 1.21.11; the API differs:
  `RenderMaterial`, `BlendMode`).
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
| Fabric renderer API implementations | Indigo, Sodium's renderer | Level-aware `collectParts`, per-part and per-quad ambient occlusion, baked colours and normals on quads | 26.x: `FabricBlockRendering` seam, `FrapiDefaults` (FRAPI-1, FRAPI-2). 1.21.11: M5 |
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
  needs Sodium for NeoForge. A pairing rule in the compatibility plan should refuse the mixed pair with a message.
- Indium has no build for these versions and isn't needed: Sodium provides `indium`.

**Plan, by coverage per effort** (each with its CI check):

| # | Capability | Covers | Status |
|---|---|---|---|
| 1 | FRAPI-2: NeoForge's baked colours and normals kept by `FrapiDefaults` | NeoForge models drawn by any Fabric renderer | Done (`ce52fb2`). Gate "frapi baked colors OK": the terrain test block's model has a `neoforge_data` colour, checked as Fabric's renderer emits it |
| 2 | Constructor repair for appended NeoForge components, with a handler bridge | Sodium's `BakedQuadMixin` (SODIUM-1); any mixin into NeoForge-extended records (`BakedQuad`, `MaterialInfo`, `FluidModel`) | Done (`bee1fe4`, unit tests). Gate "frapi baked quad constructor hook OK": the test mod's mixin at the end of `BakedQuad`'s vanilla constructor marks the quads NeoForge bakes |
| 3 | Fabric-order early hooks | PolyPlus, Polydium, OneConfig | Language adapters done (server check "early language adapter OK"); `preLaunch` next |
| 4 | Pre-flight of mixins into other mods, pairing rules | Iris with Sodium, Polydium, OneConfig's compat mixins, Sodium addons | Done for installed NeoForge mods and, on 26.x, other Fabric mods (R3); pairing rules next |
| 5 | Built-in compatibility plan (data) | Sodium's window mixin, its renderer API config on 1.21.11, pairings | Next |
| 6 | Fabric fluid rendering backed by NeoForge's (custom fluid renderers on 26.x, `IClientFluidTypeExtensions` on 1.21.11) | NeoForge fluids under a Fabric renderer | Planned |
| 7 | Block-entity render data and NeoForge model data, bridged both ways | Fabric renderers reading NeoForge block entities' data | Planned |
| 8 | Renderer contract: resolve a late-registered renderer, report the active one, optional every-block routing | Third-party renderer API implementations | Planned |
| 9 | Early-window opt-out for mods that need to act before the first GL context (driver workarounds) | Sodium's `preLaunch` workarounds | When a real report needs it |
| 10 | Corpus runs for the Polyfrost stack and Fabric Sodium/Iris | Evidence for 3-8 | Fabric Sodium/Iris run exists (informational) |

**Not planned:** a Sodium-specific NeoForge platform layer (the size of Sodium's own `neoforge` module, re-checked
every Sodium release, and a licence question), and Iris pipeline fixes beyond its public API. Sodium and Iris for
NeoForge already cover those users.

### Ecosystem integrations

- **Done:** Mod Menu's config screens in NeoForge's mod list, Forge Config API Port on NeoForge's own config system,
  a Fabric mod's Sodium options page and mixin overrides given to Sodium for NeoForge, NeoForge mod properties as
  Fabric custom values (`fabric:provides` included).
- **Done:** plugins of recipe viewers and tooltip mods: a Fabric mod's JEI, EMI, REI and Jade entrypoints become the
  annotations their NeoForge builds scan for (`PluginAnnotations`, `FabricLanguageLoader`); WTHIT reads the jar's
  plugin file; Jade's per-mod settings become its mod properties. An informational CI client runs the viewers'
  NeoForge builds with the test mod's plugins: on `e68cb58` JEI, REI and Jade each constructed the test plugin once
  and called it on 26.1.2 and 26.3, and WTHIT did on 26.1.2 (Modrinth gave no WTHIT build for 26.3).
- **Next:** pin the viewers' builds and make that run a gate; EMI once it has NeoForge builds for these versions.

### Quality: CI, corpus, performance

- **Done:** CI builds and tests every version, then runs a dedicated server and a client (Xvfb, a singleplayer world)
  with the test mods, with Fabric API, with Sodium and Iris for NeoForge (26.x), and with a corpus of popular Fabric
  mods from Modrinth (informational).
- **Done (this pass):** an informational server run on the newest NeoForge build of each version, next to the pinned
  one (Fabricated accepts every build of a version, and NeoForge's internals move between builds: REG-1).
- **Next:** CORPUS-2 (Xaero's World Map stops the corpus client). It now runs alone in its own informational client,
  so the rest of the corpus reaches a world; that run shows whether it crashes by itself. The gates listed in the
  rendering-pipeline plan.

## Findings log

Severity: **P0** crash or data loss on a normal launch · **P1** a supported feature silently doesn't work ·
**P2** wrong behaviour or a leak under realistic conditions · **P3** diagnostics, hygiene, unlikely conditions.

### Fixed

| ID | Sev | Location | Failure mode and trigger | Why tests missed it | Fix |
|---|---|---|---|---|---|
| REG-1 | P0 on newer 26.3 builds | `NeoForgeRegistryInternals` | NeoForge's 26.3.x branch keeps each list of data-pack registries with its key set in a private record. The reflection accepted only a `List` field and found nothing, so `DynamicRegistries.register` threw and the game stopped on NeoForge's error screen. | CI pins NeoForge 26.3.0.69-beta, which predates the change; Fabricated's range accepts the newer builds. Found by the code audit. | `05f3a9c`: reads the record's components too, logs an unknown shape. A CI server run on the newest NeoForge build of each version; on `e68cb58` every server check passes on NeoForge 26.3.0.94, the synced dynamic registry included. |
| SODIUM-1 | P0 for Sodium's Fabric build | core `MixinPreflight` | NeoForge keeps `BakedQuad`'s vanilla constructor only as a delegate adding NeoForge components, and builds block quads with the full one, so Sodium's Fabric `BakedQuadMixin` never ran for them; Sodium's renderer threw on the first block quad. | Seen in CI's first run of Sodium's Fabric build (`loadUnsupported`). | `bee1fe4`: the constructor repair covers delegates appending NeoForge-typed arguments, with a bridge for handlers taking the parameters. Unit tests; verified on 26.3 (`e68cb58`): Sodium's Fabric build meshes the world without the crash. Gate "frapi baked quad constructor hook OK" (`6a842e5`). |
| FRAPI-2 | P2 | `FrapiDefaults` (26.x) | Upstream's `fromBakedQuad` sets vertex colours to white and no normals, so NeoForge quads drawn by Fabric's renderer lost their baked colours and normals. | Not yet seen in game; found reading Forgified Fabric API. | `ce52fb2`. |
| PRE-2 | P3 | core `MixinPreflight` | A mixin into a member another mod's mixin adds (Iris's `MixinSpriteContents` into Sodium's `sodium$setActive`) was reported missing: pre-flight sees the target's bytecode only. A required one would have disabled the mod. | Seen as a warning in CI's Fabric Iris run. | This pass: a missing member named as mixins name what they add (`modid$name`; the game's own members never are, bar `lambda$` and `access$`) is noted as not verified instead. Unit test. Checking it against the other mods' mixins is still open. |
| CFG-1 | P3 | `ModTranslator` | A `skipMixins` entry that named no mixin was silently ignored, so a typo went unnoticed. | — | This pass: a pre-flight note names it, where every config it could apply to was read on that side. Unit test. |
| FC-10b | P3 | `LazyEntrypoint` | Caught `Throwable` where Fabric catches `Exception`, wrapping VM errors. | — | `4022d88`, with the other audit items: `ModMenuBridge` and `HudBridge` no longer swallow or wrap VM errors, a per-block allocation in `FrapiDefaults`, pending respawns cleared on server stop, duplicated class reading merged, stale docs. |
| DISC-1 | P1 | `FabricDependencyLocator` | NeoForge hands every dependency locator the same root mod files, so mods nested in them (jar-in-jar, added by NeoForge's own locator) were never seen, however late Fabricated ran. A Fabric mod depending on such a mod (Sodium for NeoForge nests its mod) was disabled, and a Fabric build of it was loaded next to it. | Tests built the NeoForge mod list by hand; the code comment assumed the order of locators mattered. Found reading FML for the Sodium research. | `264b1b4`: `NestedNeoForgeJars` (core, bounded, in place) and `NeoForgeMods` (loader, NeoForge's TOML library). Unit tests. |
| IND-1 | P1 | `Pipeline` built-in containers | NeoForge mods' `modproperties` weren't visible to Fabric code. With Sodium for NeoForge, Indigo didn't see Sodium's `fabric-renderer-api-v1:contains_renderer` and its client entrypoint failed ("second rendering plug-in"); Fabric mods depending on `indium`, which Sodium provides, were disabled. | No test had a NeoForge mod with properties. | `264b1b4`: properties become custom values, `fabric:provides` ids aliases. `PipelineTest`. |
| FRAPI-1 | P2 | pass-through `fabric-renderer-api-v1` (26.x) | Upstream's `FabricBlockStateModel.emitQuads` default collects parts without the level, and its part default ignores NeoForge's ambient occlusion. NeoForge models drawn by Fabric's renderer (wrapped by a Fabric model, or every model under Sodium) lost their level and model data (connected textures). | Fabricated only sent Fabric models to Fabric's renderer, and the test model wrapped a vanilla one. | `75e5867`: `FrapiDefaults`, declared on `BlockStateModel` and `BlockStateModelPart` (as Forgified Fabric API does). CI check "frapi level-aware default OK". |
| MODEL-1 | P2 | model loading (26.x) | NeoForge parses model files itself, so a Fabric `UnbakedModelDeserializer` was never used (Traveler's Backpack's models were missing). | — | `c02d85f` (M4): NeoForge's parser hands `fabric:type` model files to Fabric's deserializers. CI checks. |
| MODEL-2 | P2 | pass-through `fabric-model-loading-api-v1` (26.x) | Upstream's codec replacement dropped NeoForge's blockstate model types (`type`, `neoforge:composite`). | — | `c02d85f`: upstream's mixin is left out; Fabric's types are added to the codec NeoForge builds. CI check. |
| PRE-1 | P2 | core `MixinPreflight` | Lithostitched's `PendingRegistrationMixin` injects at `Decoder.parse`, which NeoForge's code calls through `Codec` (the same method): the injection found no target and the mod was disabled. | — | `08e9a5f`: pre-flight points `@Inject` and `@ModifyExpressionValue` at the call made through the one subtype. Unit test. |
| COMPAT-1 | P1 | `Pipeline` built-in containers | NeoForge mods' Fabric containers had no files, so `findPath` and `getRootPaths` came back empty. A multi-loader mod that finds its own files through Fabric's API whenever it is present failed: LambDynamicLights' NeoForge build crashed the 26.3 corpus client at start ("cannot find the default configuration file inside of the mod's JAR"; its container comes from Yumi). | Containers of NeoForge mods were never asked for files in a test. Found by the corpus's new rendering group. | `398dd60`: the mod's file is its container's root; a nested mod still has none. `PipelineTest`. Verified in CI on `29dca60`: LambDynamicLights copies and loads its default configuration in the 26.3 corpus client (Yumi's source wasn't read; the fix confirms its lookup goes through Fabric's API). |
| CORPUS-4 | — | Puzzles Lib (26.3 corpus) | Required Fabric API 0.163.0 or newer; CI tested 0.162.0. | — | R2 (`496d7a9`): with 0.164.0+26.3 Puzzles Lib loads in the 26.3 corpus. |
| PH2-12 | P1 | Pass-through `fabric-model-loading-api-v1` on NeoForge 26.x; core `MixinPreflight` | Upstream's `ModelBakeryMixin#onReturnInit` injects at `RETURN` of `<init>` by name only. Mixin picks the first constructor: NeoForge's deprecated one, kept for vanilla's signature, which only calls `this(...)`. NeoForge's code calls the new one, so the bakery never got the model loading dispatcher. Block and item model modifiers (before and after bake) and extra model baking did nothing, with no error. | No test used a model modifier. Found by the new renderer API CI gate: the test block was placed, but its model was never wrapped. | `08aff28`. Pre-flight points end-of-constructor injectors at the constructor the kept one calls. It acts only on deprecated pure `this(...)` delegates, so vanilla's own delegating constructors are untouched. Unit test included. |
| PH2-01 | P2 | `FabricDependencyLocator`, `Pipeline.Result` | A mod NeoForge refuses (`addJarContent` empty) kept its `inject-interface` directives. Loading the target game class then threw `NoClassDefFoundError` for the mod's interface. | The tests never had NeoForge refuse a translated mod. | `dba8500`, with `PipelineTest` |
| PH2-02 | P2 | `RegistrySyncBridge` | The root registry callback was added from the mod constructor. FML constructs mods in parallel, so this was an unsynchronised mutation of a vanilla list. | A race that needs another mod mutating the same list during construction. | `b360564` |
| PH2-03 | P2 | Lifecycle bridges (server and client) | A block entity placed into, or removed from, an already loaded chunk fired no `BLOCK_ENTITY_LOAD`/`UNLOAD` (NeoForge's `ChunkEvent` covers whole chunks only). Mods tracking block entities leaked or missed them. | There was no block entity event test. | `b974b0c` (`LevelChunkBlockEntityMixin`), with CI checks on server and client |
| PH2-04 | P2 | `ClientLifecycleBridge` | When the client dropped a level (disconnect, dimension change), no `BLOCK_ENTITY_UNLOAD` fired. Mods tracking block entities kept the old `ClientLevel` alive. | No test leaves a world. | `b974b0c` (`ClientChunkLifecycle.levelUnloaded`) |
| PH2-05 | P2 | `ClientLifecycleBridge` | Chunks the client evicts without `drop()` (view distance shrinking, a chunk replaced by a packet) fired no `CHUNK_UNLOAD`. | Same. | `b974b0c` (`ClientChunkCacheMixin`) |
| PH2-06 | P2 | `TranslationCache`, `AtomicFiles`, `IntermediaryMappings` | A rename refused for a moment (Windows antivirus or indexer) disabled the mod. A failed mapping-blob write disabled every 1.21.11 Fabric mod. | CI runs on Linux. | `3fbfa31` (short retry, and the mapping blob is optional), with `AtomicFilesTest` |
| PH2-07 | P2 | `ModTranslator.memoryPermits` | The parallel remap budget (1.21.11) was estimated from the compressed jar size. A jar with a high compression ratio could exhaust the heap. | No test jar compresses well. | `30e669e` (declared uncompressed size), with `SafeArchiveTest` |
| PH2-08 | P3 | `Pipeline` report | An unexpected translation error showed only `e.toString()`, though `ARCHITECTURE.md` §10 promises the stack trace. | Tests checked that the mod was disabled, not the report. | `64471c3` |
| PH2-09 | P3 | Fabricated's mixin configs | `overwrites.requireAnnotations` was missing, so an accidental overwrite (a method name matching the target's) went unnoticed. | Nothing collides today. | `5e76035` |
| PH2-10 | P3 | `ModMenuBridge`, `ObjectBuilderBridge`, `FabricTerrain`, `RegistrySyncBridge` | A stack trace was dropped, a reflective fallback was silent, `VirtualMachineError` was wrapped in a crash report, and the registry was frozen off the main thread. | Hygiene. | `2ab7e09` |
| PH2-11 | P3 | Lifecycle bridges | The live block entity map was iterated around mod callbacks, so a listener creating a block entity would throw `ConcurrentModificationException`. | Hygiene. | `b974b0c` (copy first) |
| PH2-13 | P3 | `FabricDependencyLocator` | Package-guard exclusions filtered the jar but not the overlay, which could re-create a split package. Likelihood UNVERIFIED. | — | `2a7c02b` |
| PH2-14 | P3 | `EntrypointRunner` | An `OutOfMemoryError` from a mod's constructor or static initializer arrived wrapped, so it was recorded as the mod's failure and the game went on. | — | `3211d56`, with a unit test |
| PH2-15 | P3 | `MappingComposer`, `IntermediaryMappings` | A truncated mapping line or an invalid configured path threw an unchecked exception instead of the targeted message. | — | `3211d56`, with a unit test |

Defects in this session's own new code, found by CI before merge: the renderer API mixin first hit NeoForge's
deprecated 4-argument `SectionCompiler.compile`. It now targets the 5-argument overload by descriptor (`0dbdfda`,
crash in run 229).

### Open

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| FC-5 | P3 | `TranslationCache.verifyIntegrity` | Hashes are memoised by file metadata. On Windows the key is size and mtime only, and `entry.json` itself isn't hashed. | Needs a decision on cost: re-hashing every launch versus trusting metadata. |
| FC-6 | P3 | `TranslationCache.store` | Two cold launches of the same side that share one game directory race on delete-then-move. | Needs a cross-process file lock. Rare. |
| FC-7 | P3, UNVERIFIED | cache cleanup | `Files.walk` may follow Windows directory junctions. | Must be verified on Windows first. |
| FC-9 | P3 | `FabricDependencyLocator.run` | A late exception reports "Fabric mods will not be loaded" after some were already handed to FML. | Message accuracy only. Fixing it needs care around FML state. |
| FRAPI-3 | P3 | `FabricBlockRendering` | The registered renderer is resolved once; a renderer registering after Fabricated first draws a Fabric model would be ignored. | In the rendering-pipeline plan (8). |
| P3-2 | P3 | `LootBridge` | One global reload manager. Two overlapping reloads can label a loot table with the wrong source. | Low impact. Needs a per-reload key. |
| P3-6 | P3, UNVERIFIED | `ClientNetworkingImpl.currentConfiguration` | May keep the last configuration-phase listener if a disconnect skips `LoggingOut`. | Bounded to one slot. Not confirmed. |
| P3-8 | — | `BlockEntityTypeMixin` | Field replaced without `volatile`. | Won't fix: written only during mod initialization, before world threads start. |
| CFG-2 | P3 | `gradle.properties`, `Pipeline` | The Fabric Loader API version is written in two places. | Build-time duplication only. |
| CORPUS-1 | P3, cosmetic | Terralith, Lithostitched (Apollib) (26.1.2 and 26.3 corpus) | NeoForge warns "Error reading NeoForge-added overlays for mod/terralith, falling back to empty overlays" ("Unknown registry key in neoforge:condition_codecs: terralith:config", and `apollib:config` for Lithostitched). Their Fabric jars' `pack.mcmeta` carries the overlays of their NeoForge builds, gated by those builds' own condition types, which the Fabric builds don't register. NeoForge then loads none of those overlays, which is what the Fabric builds expect. | Nothing to fix; the warning could be silenced by ignoring NeoForge overlay metadata in Fabric jars. |
| CORPUS-2 | P2, cause unknown | Xaero's World Map (26.1.2 and 26.3 corpus) | The corpus client crashes on its first tick: `WorldMap.events` is null in World Map's `Minecraft.runTick` hook. Its client entrypoint ran without failure (0 entrypoint failures among 100 mods; XaeroLib's client stage 1/3 logged), its mixins passed pre-flight, and `CLIENT_STARTED` fired before the crash (the run()-start hook applies). What sets `events` before the first tick on Fabric is unknown: the mod is closed source and its jar isn't reachable from the development sandbox. | Needs the jar, or the mod author's help. |
| CORPUS-3 | — | Balm (Waystones), JEI, ShulkerBoxTooltip (26.3 corpus) | Their mixins target code NeoForge changed: Balm `CropBlock.getGrowthSpeed`'s signature and `LivingEntity.removeAllEffects`'s loop, JEI `KeyMapping.setKey`, ShulkerBoxTooltip a `setTooltipForNextFrame` overload. Pre-flight disables them with the reason. | Not repairable in general. Their NeoForge builds exist and are used when installed. |
| CORPUS-5 | P3, cosmetic, UNVERIFIED | Traveler's Backpack (26.3 corpus) | NeoForge warns "Missing FluidModel" for its potion fluids. NeoForge's check runs inside `FluidStateModelSet.bake`, and upstream's `FluidStateModelSetMixin` wraps that method to add the models of Fabric-registered fluids after it returns, so the warning comes before those models are added. Whether the potion fluids have a model at all is UNVERIFIED. | Could register Fabric's fluid models through NeoForge's `RegisterFluidModelsEvent` instead. Low value. |

## Compatibility test matrix (renderer and model APIs)


| Mod | What it uses | 1.21.11 | 26.x | Evidence |
|---|---|---|---|---|
| `fabricated-testmod-frapi` (this repo) | `ModelLoadingPlugin` after-bake modifier, its own `emitQuads` in sections, as a falling block and with the breaking overlay; a `fabric:type` model as a multipart part | — | **passes** on 26.1.2, 26.2, 26.3 | CI "Fabric renderer API in the world", run 246 |
| Traveler's Backpack | Custom unbaked model deserializer | loads | loads, its custom block models too (M4) | corpus run on `08e9a5f` |
| Continuity, Entity Model Features, Entity Texture Features | Renderer API (connected textures); entity rendering | not checked | 26.3: translated and in NeoForge's mod list; the corpus client still crashes on its first tick (CORPUS-2) before any world, so unexercised | corpus runs on `496d7a9`, `29dca60` |
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
- **Renderer API gate: passes** on 26.1.2, 26.2 and 26.3 (runs 236 and 237). On 26.3 the log shows the pre-flight
  moving `ModelBakeryMixin#onReturnInit` to NeoForge's constructor, then `frapi emitQuads called (level
  RenderSectionRegion, thread Worker-Main-2)` and `frapi terrain OK`: the Fabric model was drawn by a chunk-section
  compile worker.
- **Mixin's selection of the first match for a name-only selector** is the basis of `08aff28`. It is confirmed by
  run 229's crash (`SectionCompilerMixin`, "Scanned 0 target(s)"), by run 233's gate failure and by its passing once
  the selector was moved.
- UNVERIFIED: FC-7 (junctions), P3-6 (configuration listener), PH2-13's likelihood, whether Continuity and Athena have
  Fabric builds for these versions, and an in-game allocation baseline for the meshing path.

## Changelog

Newest first. Each entry: what changed, and how it was verified.

### 2026-10-20: rendering-pipeline track, R3, plugins for recipe viewers, audit

Pull request #28. `docs/progress.md` became this roadmap.

**Summary.**
- The rendering-pipeline track above, from source research and CI's first run of Sodium's and Iris's Fabric builds
  (`loadUnsupported`, a new config option): both load and reach a world on 26.2, then Sodium crashed on its first
  block quad. Fixed generically (SODIUM-1, a pre-flight repair), along with FRAPI-2.
- R3: pre-flight checks mixins into Fabricated's Fabric API and installed NeoForge mods (nested ones too); the cache
  keeps the fingerprints of the classes it read and translates again when one changes.
- Plugins for JEI, EMI, REI and Jade reach their NeoForge builds; Jade's per-mod settings too.
- A code audit: REG-1 (P0 on current NeoForge 26.3 builds) fixed, with a CI run on the newest NeoForge build; JVM
  errors no longer swallowed in three places; duplication and stale docs removed. No dead code found in core.

**Verification.** Core: `./gradlew -Pfabricated.coreOnly=true build` green locally, with new unit tests
(`InstalledClassesTest`, `PluginAnnotationsTest`, pre-flight of installed-mod targets, the bridge repair,
cache invalidation in `PipelineTest`, nested classes in `NestedNeoForgeJarsTest`). CI on `dbdff1d`: all versions green,
the Fabric Sodium/Iris run as described. CI on the later commits: see the pull request.

### 2026-10-19 (night): Fabric model formats (M4), and renderer mods (Sodium, Iris)

Pull request #23 (merged as `1ebfc5b`): M4. Pull request #24: renderer mods.

**Summary.** Fabric's custom model formats now work next to NeoForge's (M4: MODEL-1 and MODEL-2 fixed), and a new
pre-flight repair points an injection at a call NeoForge's code makes through a subtype (Lithostitched loads). For
renderer mods, the research (Sodium `dev`/26.2/26.1.2/1.21.11, Iris, Forgified Fabric API, NeoForge, FML) found that
Sodium for NeoForge already ships its own Fabric renderer API implementation and switches it on whenever
`fabric_renderer_api_v1` is a loaded mod, which Fabricated plus Fabric API gives it. Fabric block models are then
meshed by Sodium itself, so no Sodium-specific code is needed in Fabricated. What broke on that path is fixed:

- **DISC-1**: NeoForge mods nested in jars (jar-in-jar) were invisible to Fabric code (Sodium's mod is nested in a
  service jar). `NestedNeoForgeJars` and `NeoForgeMods` read them.
- **IND-1**: NeoForge `modproperties` weren't visible to Fabric code, so Indigo didn't stand down for Sodium's
  renderer. They are now Fabric custom values, and `fabric:provides` ids are present (Sodium provides Indium).
- **FRAPI-1**: upstream's `emitQuads` defaults drop the level, the model data and NeoForge's ambient occlusion when
  Fabric's renderer draws a NeoForge model, which under Sodium is every model. `FrapiDefaults` has the
  NeoForge-aware defaults, as Forgified Fabric API does.

Sodium's and Iris's Fabric builds are now refused with a message naming their NeoForge builds, and a Fabric mod's
Sodium integration (its options page, its overrides of Sodium's mixin options) is written where Sodium for NeoForge
reads it.

**Verification.** Core: 144 tests, 0 failures (local). The loader's TOML reading was compiled against night-config
3.8.0 and run on Sodium's real `neoforge.mods.toml`. CI on `08e9a5f` (#23's head): all four versions green. In the
26.3 corpus run Lithostitched loads (the subtype repair applies to `PendingRegistrationMixin#loadFromResource`), and
Traveler's Backpack's "Expected BackpackDynamicModel" errors are gone (four "Missing FluidModel" errors remain, see
CORPUS-5). CI on `75e5867`: the renderer gate's new check, a NeoForge `DelegateBlockStateModel` inside a Fabric
model getting the chunk section's level, passes on 26.1.2, 26.2 and 26.3.
CI on `7fe52ed` ran Sodium and Iris for NeoForge from Modrinth with Fabric API and the renderer API test mod (26.1.2:
Sodium 0.9.2, Iris 1.11.4; 26.2: Sodium 0.9.2, Iris 1.11.4; 26.3: Sodium 0.9.3-beta.1, Iris 1.11.7). On 26.1.2 and 26.3
every check passes: Indigo stands down ("Different rendering plugin detected"), Sodium meshes the Fabric test model on
its chunk builder thread (the level is Sodium's `LevelSlice`), the NeoForge model inside it gets that level, and the
falling block and the breaking overlay are drawn through Sodium's renderer. On 26.2 every check but the falling block
passed: the test dropped it from a fixed offset beside the player, and in that world (blocks placed at y=74) it likely
never fell into view before the client quit, six seconds after the blocks were placed. The test now drops each copy
down a cleared column beside the test block, onto a support at eye level, whatever the terrain.

### 2026-10-19 (later): Fabric models as moving blocks, in the breaking overlay and inside vanilla models

Pull request #22 (base `main` at `b7ff2a8`, after #21 merged). Roadmap items M2 and M3.

**Summary.** Fabric block models are now drawn everywhere NeoForge draws blocks, except outlines: chunk sections
(M1), moving blocks pushed by pistons or falling, and the breaking overlay (M2). A vanilla multipart or weighted model
with a Fabric model among its parts goes through Fabric's renderer as a whole (M3). Only those blocks take Fabric's
path; every other block keeps NeoForge's. `FabricTerrain` became `FabricBlockRendering`, still the only class naming
the renderer API's types. On 26.1.2, upstream's `@Overwrite` of the breaking renderer (every model through Fabric's
renderer) is left out in favour of the same hook as on 26.2 and 26.3.

**Verification.** CI run 246 (`1596fff`): all four versions green. The 26.x gate now requires four lines, logged by
the test models: `frapi terrain OK` and `frapi nested terrain OK` (section worker threads), `frapi moving block OK`
(a falling copy of the test block) and `frapi breaking OK` (the server sends breaking progress), on 26.1.2, 26.2 and
26.3. On 26.1.2 the breaking overlay hands the model the air block state, as NeoForge's own code there does (and as
upstream's replacement did); 26.2 and 26.3 hand it the block's state. The same run shows `08aff28`'s constructor
repair also applying to a corpus mod: Lithium's `EntityCollisionContextMixin#initVars`.

### 2026-10-19: audit, fixes, and Fabric's renderer API in the world

Branch `claude/great-ramanujan-9lcl5m`, pull request #21 (base `main` at `6664f72`).

**Summary.** A full audit of the loader, core and Minecraft-facing code for faults that only show in production
(threading, leaks across world switches, mixin fragility, swallowed exceptions, Windows file system behaviour,
memory). Fifteen defects are fixed (one P1, seven P2, seven P3). Eleven smaller or unconfirmed ones are deferred
with reasons. No P0 (crash on a normal launch, data loss) was found. Fabric's renderer API now draws Fabric block models into
NeoForge's chunk meshes on 26.x. That work found a P1: Fabric's model-loading modifiers never ran on NeoForge 26.x,
because of how Mixin picks a constructor. The architecture map, FRAPI design and rules, and mixin conventions are
in `claude.md`. The inventory of every mixin is in `MIXINS.md`.

**Verification.** `./gradlew -Pfabricated.coreOnly=true build`: 150 tests, 0 failures (local). The
Minecraft modules, server and client runs are verified by CI on every push (see `claude.md`, *Building and
verifying*). Run 233 (`e0c5ffa`): every version green except the new renderer API gate on 26.x. Its cause is fixed in
`08aff28`. Runs 236 and 237 (`3211d56`, every code change in this session): all four versions pass every gate,
including the renderer API gate on 26.1.2, 26.2 and 26.3.

