# Fabricated: engineering memory

This file is the starting point for anyone (human or agent) working on Fabricated. **Read it first, then
[`progress.md`](progress.md).** It holds the architecture map, the rules the code follows and the commands to run
before committing. The longer design documents remain the reference for details:
[`ARCHITECTURE.md`](ARCHITECTURE.md) (pipeline, isolation, caching, security), [`RESEARCH.md`](RESEARCH.md) (verified
facts about NeoForge, FML and Fabric), [`COMPATIBILITY.md`](COMPATIBILITY.md) (what works, per version and module),
[`API.md`](API.md) (Fabricated's public API) and [`../CONTRIBUTING.md`](../CONTRIBUTING.md) (layout, build, adding a
version).

## Working rules

- **Verify, never assume.** A Minecraft, NeoForge, FML or Fabric API class, method or field is used only after
  reading it: in this repository, in the NeoForge sources for that line (`neoforged/NeoForge` branches `1.21.11`,
  `26.1.x`, `26.2.x`, `26.3.x`; the Minecraft code NeoForge changes is in `patches/`), or in the Fabric API sources
  at the tag this version's plan names (`fabricApiVersion` in `mc/<version>/src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json`).
  Signatures differ between versions (Fabric's renderer API changed between 26.2 and 26.3, for example). Anything
  that can't be checked is marked `UNVERIFIED` in `progress.md`.
- **Behaviour changes only fix a proven defect.** The commit message names the file, the failure and why tests
  missed it.
- **Commits** are small and atomic: `fix(core): ...`, `feat(frapi): ...`, `refactor(mc/26.x): ...`, `docs: ...`,
  `ci: ...`, with a body that explains why.
- **No placeholders** in merged code (`CONTRIBUTING.md`): an unsupported feature is reported in
  `fabric-bridge-report.txt`, `COMPATIBILITY.md` and `progress.md`, not stubbed.
- **Pull requests are merged once CI is green on their final commit** (the owner's standing decision, 2026-10-19):
  open the PR, drive it to green, merge it with a merge commit, then continue on the same branch from the new
  `main`.
- **Lightweight and readable over clever.** No abstraction without a second user, no dead code, no duplicated
  utilities. A refactor must measurably reduce coupling or duplication.

## Building and verifying

| What | Command | Where it runs |
|---|---|---|
| Minecraft-independent modules and all unit tests | `./gradlew -Pfabricated.coreOnly=true build` | anywhere (Maven Central only) |
| One version's jar and the test mods | `./gradlew :mc:26.2:fabricatedJar :mc:26.2:testmods` | needs `maven.neoforged.net`, Mojang and `maven.fabricmc.net` |
| Everything | `./gradlew build` | same |
| Real server / client with the test mods | `ci/smoke-server.sh`, `ci/smoke-client.sh` | CI (HeadlessMC + Xvfb for the client) |

**Cloud sandbox limit.** The Claude Code cloud container blocks `maven.neoforged.net`, Mojang and
`maven.fabricmc.net`, so only the `coreOnly` build runs there. The Minecraft modules compile, and their mixins
apply, only in GitHub Actions (`.github/workflows/build.yml`). The loop is: run the `coreOnly` build locally, push,
and read the CI job logs. `compileJava` doesn't check mixin targets or injection points; those fail when the game
applies them, which the CI server and client runs do on every push.

**What CI proves, per version** (`.github/workflows/build.yml`):
1. Unit tests (core, fabric-loader-api, api).
2. A real dedicated server with the test mods (gate), then with the official Fabric API jar (must start).
3. A real client, started headlessly with HeadlessMC, that joins a singleplayer world with the test mods (gate),
   then again with Fabric API. On 26.x that second run also loads the renderer API test mod, and a gate checks that
   Fabric block models are drawn in chunk sections (also inside a vanilla multipart model), as moving blocks and with
   the breaking overlay, and that Fabric's and NeoForge's custom model formats both load.
4. A corpus of popular Fabric mods from Modrinth (informational).

## Architecture

### How a Fabric mod reaches NeoForge at runtime

FML's discovery calls Fabricated's `FabricCandidateLocator` (registered through `META-INF/services` in the
`loader` source set), which claims every jar in `mods/` that has a `fabric.mod.json` and no
`neoforge.mods.toml`. `FabricDependencyLocator` then runs core's `Pipeline` over all candidates at once. The jar
goes through `SafeArchive`, its metadata is parsed, and the dependency graph and the Fabric API module plan are
resolved. Each surviving mod is then translated: remapped from intermediary to Mojang names on 1.21.11 (identity on
26.x), its mixins checked against NeoForge's patched game bytecode by pre-flight, and its access widener turned into
an access transformer. A cached overlay is generated holding `neoforge.mods.toml` with `modLoader="fabricated"`.
FML loads the jar plus overlay as an ordinary mod file, applies the access transformers and mixin configs itself,
and hands each mod to `FabricLanguageLoader`, which makes a `FabricModContainer`. Fabricated's in-game mod
(`FabricatedMod`) then runs the Fabric entrypoints. `main` runs on the first `RegisterEvent` after the attribute
registry's (registries are open then). `client` runs in `Minecraft`'s constructor (`MinecraftEntrypointsMixin`), and
`server` after `main` on a dedicated server. When a Fabric mod calls the Fabric API, the call goes either to a
module Fabricated vendors and bridges to NeoForge events ("native", `mc/<version>/src/fabricapi` plus the bridges in
`mc/common*`) or to the module's upstream code from the player's Fabric API jar ("pass-through"), whose mixins
passed pre-flight. Calls to the Fabric Loader API go to core's `FabricLoaderRuntime`.

Fabric code also sees NeoForge's own mods, as built-in mod containers (`NeoForgeMods`, loader): the ones NeoForge
found, and the ones nested in their files (jar-in-jar). Every NeoForge dependency locator is handed the same root
files, so the nested mods NeoForge's jar-in-jar locator adds are invisible to Fabricated's locator, however late it
runs; their `mods.toml` are read in place by core's `NestedNeoForgeJars` instead (Sodium for NeoForge nests its mod
in a service jar). A NeoForge mod's `modproperties` become its Fabric custom values, and the ids its `fabric:provides`
property lists are present too. That is how NeoForge mods speak to Fabric code: Sodium's
`fabric-renderer-api-v1:contains_renderer` makes Indigo stand down, and it provides `indium`. Of two copies of a
NeoForge mod the newest counts. The Fabric builds of mods that only work as their NeoForge build (Sodium, Iris) are
disabled with a message naming it (`Pipeline.NEOFORGE_BUILD_ONLY`).

### Modules and the dependency graph

```
fabric-loader-api   (Fabric Loader's public API, Apache-2.0; no deps)
api                 (Fabricated's public API for NeoForge mods; no deps)
core  ──api──► fabric-loader-api, api       (pure Java 21; compileOnly ASM)
mc/<version>        (one Gradle project per Minecraft version, MDG)
  ├─ source set loader  = loader/src/main/java ──► core          (FML SPI only; early-services class loader)
  ├─ source set main    = in-game NeoForge mod  ──compileOnly──► loader output, core
  │     sources: mc/<v>/src/main/java + mc/<v>/src/fabricapi/java (vendored native Fabric API modules)
  │              + mc/common (all) + mc/common26 (26.x) + mc/common26early (26.1.2, 26.2) + mc/common26late (26.2, 26.3)
  │     26.x only, compileOnly: fabric-renderer-api-v1's jar (pass-through module, typed calls)
  ├─ source sets testmod, testmodbroken, testmodfrapi (26.x)  ──compileOnly──► main output, fabric-loader-api, api
  └─ fabricatedJar = loader classes + core + fabric-loader-api + api + META-INF/fabricated/game-mod.jar (main)
```

**Illegal edges: none.** Checked: `core`, `api` and `fabric-loader-api` have no Minecraft, NeoForge or FML
dependency (their `build.gradle` files list only `fabric-loader-api`, `api`, ASM and JUnit), nothing depends on a
`mc/*` project, and there are no cycles. `loader` sees FML only (`neoForge.addModdingDependenciesTo(sourceSets.loader)`
gives it the game too, but it references FML SPI types only, so it runs before the game exists). The in-game mod
reaches `loader` and `core` through NeoForge's parent class loader at runtime (`compileOnly`), never the reverse.

The prompt that started the 2026-10-19 session described `api/` as "Fabric API surface re-implementations". It
isn't: `api/` is Fabricated's own small public API (see `API.md`). The Fabric API re-implementations are the
vendored modules in `mc/<version>/src/fabricapi` and the bridges in `mc/common*`.

### The version seam

| Seam | How | Type-safe? |
|---|---|---|
| Which code a version gets | Gradle source directories (`sharedSources` in `mc/mc.gradle`): each version compiles only its own and the shared folders that apply | compile-time |
| Discovery services → FML | `META-INF/services` in the `loader` source set: `IModFileCandidateLocator`, `IDependencyLocator`, `IModLanguageLoader`, `ClassProcessor` | compile-time against FML SPI |
| Version facts → core | `META-INF/fabricated/version.properties` (generated: Minecraft version, namespace, Mixin versions) and `fabric-api-plan.json` (data, per version) | data, validated when parsed |
| Discovery → in-game mod | `FabricatedState.get()` (a `volatile` static in `loader`, published once discovery ends) | compile-time |
| In-game mod → pass-through Fabric API modules | 26.x renderer API: typed calls, compiled against the module's own jar, in classes loaded only when the module is (`FabricBlockRendering`). Other modules (HUD, picture-in-picture, sprite lookups): `Class.forName` + `MethodHandle`s (`HudBridge`, `PictureInPictureBridge`), because 1.21.11 shares that code and its Fabric API jars use intermediary names | typed / reflective |
| Fabric mods → Fabricated | mixins and class tweakers, applied by FML; Fabric's injected interfaces added by `FabricClassProcessor` at class load | bytecode |

### Mixins

All Fabricated-owned mixin configs use `"required": true` and `"defaultRequire": 1`, so a missing target or injection
point fails at startup, in CI. Three injections set `require = 0` on purpose, each with a fallback:
`MinecraftEntrypointsMixin` (client entrypoints before `Thread.currentThread()`, with a `TAIL` fallback),
`MinecraftStartedMixin` (`CLIENT_STARTED` as `run()` starts, else on the first client tick), and
`ChunkGeneratorFeaturesMixin` (only needed while NeoForge's `refreshFeaturesPerStep`, present on all four lines,
exists). The full inventory (target, injector, point and versions of every mixin) is in [`MIXINS.md`](MIXINS.md).

### Threading model

| Thread | Fabricated code that runs there |
|---|---|
| FML discovery (early services; may be a worker pool) | `FabricCandidateLocator`, `FabricDependencyLocator` → core `Pipeline` (translates mods with `parallelStream()`), `TranslationCache` |
| Any class-loading thread | `FabricClassProcessor` (reads only the immutable `FabricatedState`) |
| Mod construction / mod-bus events | `FabricatedMod`, native module registration, entrypoints (`main` on the first `RegisterEvent`) |
| Render thread | client entrypoints (in `Minecraft`'s constructor), HUD/screen bridges, client networking handlers, moving blocks and the breaking overlay (`MovingBlockRenderMixin`, `BlockBreakingRenderMixin` → `FabricBlockRendering`, with their own per-thread renderer and emitter) |
| **Chunk-section compile workers (several at once)** | `SectionCompilerMixin` → `FabricBlockRendering` for every block: the decision is a `ClassValue` (and, for a vanilla multipart or weighted model, a field of the model, written once with the same answer by any thread); the Fabric renderer and emitter are per thread (`ThreadLocal`); nothing else shared is mutated |
| Resource reload executors | reload listeners of native modules (`fabric-resource-loader-v1`), model discovery (`ModelManagerExtraModelsMixin`) |
| Server thread | lifecycle, entity, interaction, loot, transfer and networking bridges |

## Fabric's renderer API (FRAPI)

### What exists, per version (verified against Fabric API sources)

| | 1.21.11 (0.141.6) | 26.1.2 (0.155.3) | 26.2 (0.161.0) | 26.3 (0.164.0) |
|---|---|---|---|---|
| `fabric-renderer-api-v1` | unsupported (its own mixins fail pre-flight, see `COMPATIBILITY.md`) | pass-through | pass-through | pass-through |
| `fabric-renderer-indigo` (the renderer) | unsupported | pass-through | pass-through | pass-through |
| Fabric models in the **world** (chunk meshing) | no | **yes**, `FabricBlockRendering` | **yes** | **yes** |
| Fabric models as **parts** of a vanilla multipart or weighted model | no | **yes** (the whole block goes through Fabric's renderer) | **yes** | **yes** |
| Fabric models in item / block entity rendering | no | upstream's mixins apply (not tested in CI) | same | same |
| Moving blocks (pistons, falling blocks) and the breaking overlay | no | **yes** | **yes** | **yes** (moving blocks may be forced translucent) |
| Block outlines, a moving block's glow outline | no | from the model's parts, as NeoForge sees them | same | same |

The 26.x surface is **not** the 1.x API most documentation describes. There is no `RenderMaterial`,
`MaterialFinder` or `BlendMode`. Material state is per quad, on `MutableQuadView`: `chunkLayer`,
`itemRenderType`, `emissive`, `diffuseShade` (26.1.2, 26.2) or `shadeDirectionOverride` (26.3),
`ambientOcclusion(TriState)`, `foilType`, `shadeMode`, `animated`, `tintIndex`, `atlas`, `tag`. Models implement
`FabricBlockStateModel.emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, BlockState, RandomSource, Predicate<Direction>)`,
injected into vanilla's `BlockStateModel`. `Renderer` has three methods: `quadEmitter(Consumer)`, `mutableMesh()`
and `altModelBlockRenderer(ao, cull, BlockColors)`.

### Design decision: bridge to the registered renderer, don't write a second one

Indigo, the reference renderer, already loads on 26.x and already is an `int[]`-backed, stride-encoded quad
emitter. Fabricated therefore doesn't duplicate it. A second renderer would add thousands of lines with no
user-visible gain. What was missing is the seam between FRAPI and the places NeoForge draws blocks. All of it goes
through `FabricBlockRendering` (`mc/common26/.../rendering`), which with `FrapiDefaults` and the mixins of
`fabricated-renderer.mixins.json` is the only code that names the renderer API's types:

- **The decision.** `FabricBlockRendering.draws(model)` is true when the model's class implements `emitQuads` itself:
  the declaring class of the resolved method is neither `FabricBlockStateModel` (whose default only draws the parts
  NeoForge sees) nor a `net.minecraft` class (vanilla models get upstream's equivalents through its own mixins). It's
  cached per class in a `ClassValue`. A vanilla multipart or weighted model (`MultiPartModelMixin`,
  `WeightedVariantsMixin`) answers for its parts instead, once, in a field of the model; when one part is a Fabric
  model, the whole block goes through Fabric's renderer, which draws the vanilla parts too.
- **Chunk sections.** `SectionCompilerMixin` wraps NeoForge's `ModelBlockRenderer.tesselateBlock` call in
  `SectionCompiler.compile` (NeoForge's overload that also takes its additional section renderers) with a
  `WrapWithCondition`. A Fabric model is drawn by `Renderer.get().altModelBlockRenderer(...)` into an emitter whose
  consumer writes each finished quad into `getOrBeginLayer(startedLayers, builders, quad.chunkLayer())`, the
  section's own buffers, as upstream does on Fabric.
- **Moving blocks** (pistons, falling blocks). `MovingBlockRenderMixin` does the same where moving blocks are drawn
  (26.1.2: `BlockFeatureRenderer.renderMovingBlockSubmits`; 26.2, 26.3: `MovingBlockFeatureRenderer.buildGroup`),
  into the moving block render type of each quad's layer. An outline submit (a glowing falling block) stays NeoForge's.
- **The breaking overlay.** The overlay takes model parts. `BlockBreakingRenderMixin` wraps NeoForge's level-aware
  `collectParts` call there (26.1.2: `BlockFeatureRenderer.renderBreakingBlockModelSubmits`; 26.2, 26.3:
  `LevelRenderer.submitBlockDestroyAnimation`) and adds a Fabric model's emitted quads, turned back into vanilla
  quads, as one part. The overlay itself is unchanged.

- **NeoForge-aware defaults.** `FrapiDefaults`, declared on `BlockStateModel` and `BlockStateModelPart` by
  `fabricated-renderer.mixins.json` (loaded only with `fabric_renderer_api_v1`, through FML's `requiredMods`):
  a model without its own `emitQuads` collects its parts with NeoForge's level-aware `collectParts`, and a part's quads
  keep NeoForge's part and quad ambient occlusion, as in Forgified Fabric API. Upstream's defaults drop both, which
  breaks NeoForge models whenever Fabric's renderer draws them: inside a Fabric wrapper model, or every model when a
  renderer such as Sodium meshes the world through the renderer API. Being declared on a `net.minecraft` interface,
  the default still counts as "draws nothing of its own" for `draws(model)`.

Upstream's own hooks for these places stay skipped because they send *every* block (every moving block, every
breaking overlay) through Fabric's renderer. On NeoForge that would drop level-aware `collectParts`, dynamic tints,
`Block.shouldRenderFace` with a position, per-part and per-quad ambient occlusion and NeoForge's enhanced lighter.

### FRAPI architecture rules (enforceable)

1. **No allocation and no boxing per block or per quad** in Fabricated's emission paths. Hooks on per-block calls
   use `@WrapWithCondition` or `@Inject`, never `@WrapOperation` (whose `Operation.call(Object...)` boxes primitives
   and allocates an array). Decisions are `ClassValue`s or fields, not maps keyed by models. One exception, outside
   meshing: the breaking overlay copies the broken block's quads once per frame, because the game draws them after
   the call returns (vanilla copies its parts list there too).
2. **Quad data has one owner, the active renderer.** Fabricated reads quads through `QuadView` and never keeps
   them (vanilla quads made from them for the game, as for the breaking overlay, are the game's). If Fabricated ever gets its own encoder (see the roadmap in `progress.md`), its `int[]` stride, offsets and
   bit masks live in one constants class that every reader and writer uses.
3. **Emitters and Fabric block renderers are thread-confined.** One per thread and use (`ThreadLocal`), bound to a
   section or a frame's buffers only for the duration of one call, never shared, never stored in a static field. Indigo's
   block renderer captures its thread's light cache when it is made, so sharing one across threads is a bug.
4. **Version-agnostic FRAPI code lives in `mc/common26`** behind the narrow seam above. Version-specific code goes in
   `mc/<version>` with a one-line comment naming the API difference that forces it. 1.21.11 needs its own work (see
   `progress.md`).
5. **Every cache has an explicit lifecycle tied to what it caches.** A cache keyed by a `Class` is a `ClassValue`
   (class lifetime, no pinning). A cache of baked models, sprites or atlases is cleared in a reload listener, or it
   lives in a field of the reloaded object itself. No static map keyed by models, states or identifiers outlives a
   resource reload.
6. **Classes that name a pass-through module's types load only after that module is known to be loaded.** The check
   (`FabricLoader.isModLoaded`) lives in a class that names only Minecraft and Fabricated types, and the code using the
   module sits in a nested holder class. Mixins that name them go in their own config, registered in the in-game mod's
   `neoforge.mods.toml` with `requiredMods` set to the module's NeoForge id (`fabricated-renderer.mixins.json`).
7. **New hot-path code needs an allocation assertion.** For Minecraft-independent code, a `core` unit test measures
   `com.sun.management.ThreadMXBean#getThreadAllocatedBytes` around a warmed-up loop and asserts zero. For code that
   only runs in the game, the CI client run is the test, and the allocation argument goes in the commit message
   (which call allocates nothing, and why).

## Mixin conventions

- Fabricated's own mixins live in `io.github.cmdralph.fabricated.neoforge.mixin` (client ones in `.client`). Each
  config owns its package (the build fails if one config's package contains another's).
- Mixins Fabricated adds to vendored native modules keep upstream's package (`net.fabricmc.fabric.mixin...`).
- Prefer `@Inject`, `@WrapWithCondition`, `@ModifyExpressionValue` and `@WrapOperation` (off hot paths) over
  `@Redirect` (only one mod can redirect a call) and `@Overwrite` (never).
- Methods a mixin adds to a target's interface are prefixed `fabricated$`. Handlers are named `fabricated$<what>`.
- Use `ordinal` only together with a comment saying which call it means, and why it's stable.
- `require = 0` only with a fallback, documented in the class Javadoc and in this file.
- Target methods by name and descriptor when the class has overloads that NeoForge changed.
