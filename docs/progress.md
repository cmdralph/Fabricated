# Fabricated: progress log

Read [`claude.md`](claude.md) first. This file records what was done, what was found, what is left and what is
still unverified. Add a dated section at the top for each working session; keep the findings log, roadmap and
matrices current rather than appending copies.

## 2026-10-19: audit, fixes, and Fabric's renderer API in the world

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

## Findings log

Severity: **P0** crash or data loss on a normal launch · **P1** a supported feature silently doesn't work ·
**P2** wrong behaviour or a leak under realistic conditions · **P3** diagnostics, hygiene, unlikely conditions.

### Fixed

| ID | Sev | Location | Failure mode and trigger | Why tests missed it | Fix |
|---|---|---|---|---|---|
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

### Deferred

| ID | Sev | Location | Issue | Why deferred |
|---|---|---|---|---|
| FC-5 | P3 | `TranslationCache.verifyIntegrity` | Hashes are memoised by file metadata. On Windows the key is size and mtime only, and `entry.json` itself isn't hashed. | Needs a decision on cost: re-hashing every launch versus trusting metadata. |
| FC-6 | P3 | `TranslationCache.store` | Two cold launches of the same side that share one game directory race on delete-then-move. | Needs a cross-process file lock. Rare. |
| FC-7 | P3, UNVERIFIED | cache cleanup | `Files.walk` may follow Windows directory junctions. | Must be verified on Windows first. |
| FC-9 | P3 | `FabricDependencyLocator.run` | A late exception reports "Fabric mods will not be loaded" after some were already handed to FML. | Message accuracy only. Fixing it needs care around FML state. |
| FC-10b | P3 | `LazyEntrypoint` | Catches `Throwable` where Fabric catches `Exception`. A VM error thrown from an entrypoint a mod invokes itself reaches the mod wrapped. | The case Fabricated runs is fixed (PH2-14). |
| P3-2 | P3 | `LootBridge` | One global reload manager. Two overlapping reloads can label a loot table with the wrong source. | Low impact. Needs a per-reload key. |
| P3-6 | P3, UNVERIFIED | `ClientNetworkingImpl.currentConfiguration` | May keep the last configuration-phase listener if a disconnect skips `LoggingOut`. | Bounded to one slot. Not confirmed. |
| P3-8 | — | `BlockEntityTypeMixin` | Field replaced without `volatile`. | Won't fix: written only during mod initialization, before world threads start. |
| CFG-1 | P3 | `ModTranslator` | A `skipMixins` entry that matches no mixin is silently ignored, so a typo goes unnoticed. | Should become a report note. |
| CFG-2 | P3 | `gradle.properties`, `Pipeline` | The Fabric Loader API version is written in two places. | Build-time duplication only. |
| MODEL-1 | P2 | model loading (26.x) | NeoForge parses model files itself, so a Fabric `UnbakedModelDeserializer` is never used. Traveler's Backpack's custom block models are missing ("Missing model for variant" in the corpus run). | Roadmap M4. |

## Fabric's renderer API: status and roadmap

| | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|---|
| Renderer API and Indigo load | no (upstream's mixins fail pre-flight) | yes | yes | yes |
| Model-loading modifiers and extra models | disabled (needs the renderer API) | yes since `08aff28` | yes | yes |
| Fabric block models in the world | no | yes (`FabricTerrain`) | yes | yes |
| Moving blocks, block outlines, breaking animation | no | not through Fabric's renderer | same | same |
| Fabric models nested in a vanilla multipart or weighted model | no | not yet (M3) | same | same |

Milestones, in order:

- **M1, done:** the 26.x chunk meshing bridge (`8e80c6e`, `0dbdfda`) and the model-loading fix it needed
  (`08aff28`), with a CI gate that passes on all three 26.x versions.
- **M2:** moving blocks (`MovingBlockFeatureRenderer`) and the breaking animation for Fabric models on 26.x, the same
  way as M1 (only Fabric models go through Fabric's renderer).
- **M3:** Fabric models inside vanilla composites. `FabricTerrain.draws` decides per top-level model class, so a
  multipart whose part is a Fabric model takes NeoForge's path and the part is invisible. This needs a per-model check
  of the composite's parts, cached on the baked model, not per block.
- **M4:** Fabric's `UnbakedModelDeserializer` on NeoForge (route NeoForge's model parser to the deserializers Fabric
  mods registered). Fixes Traveler's Backpack's models.
- **M5:** 1.21.11. The renderer API's own mixins don't apply on NeoForge 1.21.11. That needs a native module or
  targeted skips plus a bridge like 26.x's. The 1.21.11 API differs (`RenderMaterial`, `BlendMode`).
- **Not planned:** a Fabricated-owned quad encoder. Indigo already is the `int[]` encoder. A second one would add code
  without a user-visible gain (see `claude.md`).

## Compatibility test matrix (renderer and model APIs)

| Mod | What it uses | 1.21.11 | 26.x | Evidence |
|---|---|---|---|---|
| `fabricated-testmod-frapi` (this repo) | `ModelLoadingPlugin` after-bake modifier, its own `emitQuads` in the world | — | **passes** on 26.1.2, 26.2, 26.3 | CI "Fabric renderer API in the world", runs 236 and 237 |
| Traveler's Backpack | Custom unbaked model deserializer | loads | loads, custom block models missing | corpus runs (run 233) |
| Continuity | Renderer API (connected textures) | not in corpus | not in corpus | add once a Fabric build for these versions is confirmed (UNVERIFIED) |
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
`FabricTerrain` does a `ClassValue` lookup and a `ThreadLocal` read, and hooks the call with `@WrapWithCondition`, so
nothing is boxed or allocated. Fabric models go through Indigo's own path. A measured in-game allocation baseline is
open (see *Blockers*).

## Blockers and unverified

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
