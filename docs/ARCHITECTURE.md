# Fabricated — Architecture (Phase 1 proposal)

> Status: **approved in Phase 1 and implemented**; the current version is 0.5.0 (`fabricated_version` in
> `gradle.properties`). §6.2 gives each Fabric API module's plan as the version plans hold it today; the measured
> state for each version is in [`COMPATIBILITY.md`](COMPATIBILITY.md). Facts referenced here are verified in [`RESEARCH.md`](RESEARCH.md).
> Items marked **[verify-in-build]** were confirmed in loader source code. They still get an automated test in Phase 2 before being relied on.

## 0. Goals and non-goals

**Goals**
* Load as many Fabric mods and libraries as possible on NeoForge **1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3** from one
  codebase (and, experimentally, on Forge 61 to 66 and Forge 47, on their own branches: [`PORTING.md`](PORTING.md)).
* Be lightweight: no runtime dependencies beyond what NeoForge ships, and nothing at all without Fabric mods. With them,
  little work on unchanged launches (PERF-2 lists what is still repeated), and per tick only the dispatch of the Fabric
  events Fabricated bridges, without allocation.
* Be robust: an isolatable failure never takes the game down, and every failure is attributed to a mod and explained.
* Be safe: mod jars are untrusted input, nothing runs during translation, nothing touches the network, no telemetry, and original jars are never modified.

**Non-goals** (see RESEARCH §4): the Fabric builds of rendering-engine replacements (Sodium/Iris; their NeoForge builds work next to Fabric mods on 26.x, see COMPATIBILITY.md), mods depending on Fabric Loader *internals*, heuristic auto-rewriting of mixins, MC versions outside the range, and running ≤1.21.11 Fabric builds on 26.x.

---

## 1. The pipeline at a glance

```
 mods/*.jar ──► [1] FabricCandidateLocator (IModFileCandidateLocator)
                   │  claims jars that contain fabric.mod.json, before FML rejects them
                   ▼
              [2] SafeArchive: validate zip (zip-slip, bombs, limits), SHA-256
                   ▼
              [3] FabricModJsonParser: fabric.mod.json → FabricModMetadata
                   │  (ids, versions, deps, entrypoints, mixins, AW/CT, nested jars)
                   ▼
              [4] Resolver (IDependencyLocator, runs once over all Fabric candidates)
                   │  - extract & dedupe nested jars (content-addressed)
                   │  - apply user config (disable, skip mixin, aliases)
                   │  - resolve Fabric dependency graph; drop unsatisfiable mods + dependents
                   │  - Fabric API module plan: native / pass-through / unsupported
                   ▼
              [5] Translator (per mod, cached, deterministic, parallel)
                   │  a. Remap         (1.21.1, 1.21.11: intermediary→Mojang; 26.x: identity)
                   │  b. Mixin prep    (refmap remap, config filtering, behaviorVersion)
                   │  c. Pre-flight    (mixin target/injection-point verification vs game bytecode)
                   │  d. AW/CT → AT    (+ interface-injection table)
                   │  e. Overlay       (generated META-INF/neoforge.mods.toml, AT, manifest; pack.mcmeta without NeoForge-only sections; data without neoforge:conditions; enum extensions)
                   ▼
              [6] Package guard: JPMS split-package detection & resolution
                   ▼
              [7] Hand to FML: the original-or-remapped jar's contents, the overlay over them
                   │  FML now applies ATs and mixin configs natively
                   ▼
              [8] Runtime: FabricLanguageLoader ─► FabricModContainer ─► entrypoints
                   │  Fabric Loader API impl (FabricLoader, MappingResolver, ...)
                   │  Fabric API: native bridges + pass-through modules
                   ▼
              [9] Diagnostics: logs/fabric-bridge-report.txt, ModLoadingIssues, /fabricated report
```

Steps 1–7 run during FML discovery, before any mod or Minecraft code is class-loaded. Steps 1–6 handle bytes only and **never load or run mod classes**.

---

## 2. Repository and module layout

```
Fabricated/
├── settings.gradle, build.gradle, gradle.properties, gradle/wrapper (Gradle 9.7.0)
├── core/                    Pure Java (release 17). No Minecraft, no FML. ~all logic that can be unit tested.
│   ├── Pipeline.java        the discovery-time pipeline: scan, resolve, translate, guard packages, linkage, runtime
│   ├── archive/             SafeArchive, limits, hashing, atomic writes, deterministic jar writer
│   ├── json/                small dependency-free JSON reader/writer with length and depth caps
│   ├── metadata/            fabric.mod.json model + parser (FabricModJsonParser, FabricModMetadata), dependencies,
│   │                        id normalisation, a mod's own hints (custom.fabricated)
│   ├── resolve/             candidate scan, dependency graph, nested-jar dedupe, Fabric API module plan, compat plan
│   ├── remap/               Tiny v1 + ProGuard parsers, mapping composition, ASM remapper,
│   │                        refmap/string-constant remapping, compact mapping cache
│   ├── accesswidener/       AW v1/v2 + classTweaker v1/v2 parser, AT writer, interface-injection table
│   ├── bytecode/            class index, classes of installed jars, linkage checker, @Environment stripping
│   ├── mixin/               mixin-config model, refmap model, pre-flight verifier and its repairs
│   ├── translate/           per-mod translation (ModTranslator), mods.toml writer, package guard, linkage analysis,
│   │                        enum extensions, library bridges
│   ├── cache/               content-addressed, integrity-checked, sandboxed cache
│   ├── config/              fabricated.json model + validation
│   ├── runtime/             FabricLoader implementation (FabricLoaderRuntime), mod containers, entrypoint runner,
│   │                        mapping resolvers, object share
│   ├── api/                 implementation of Fabricated's public API (FabricatedApiImpl)
│   └── report/              report model + text renderer
├── fabric-loader-api/       Binary-compatible `net.fabricmc.loader.api.*` and `net.fabricmc.api.*`
│                            types (Apache-2.0, from Fabric Loader; NOTICE attribution), with SemVer and
│                            version-predicate parsing
├── loader/                  Platform-facing glue: common/ (every platform), neoforge/ (FabricCandidateLocator,
│                            FabricDependencyLocator, FabricLanguageLoader, FabricModContainer, FabricClassProcessor:
│                            interface injection, @Environment stripping), in two variants: src/fml for FML 10 to 12
│                            (1.21.11, 26.x), src/modlauncher for NeoForge 21.1's FML 4 on ModLauncher (1.21.1),
│                            each behind its own Fml facade (the Forge platforms' are on their branches, forge and
│                            forge-legacy, with the same structure)
├── api/                     Fabricated's own small public API for NeoForge mods (see API.md)
├── mc/
│   ├── mc.gradle            Shared build logic: source sets, shared source folders, jars, test mods
│   ├── platform-*.gradle    Each platform's part of it (plugin settings, mod descriptor, game dependencies)
│   ├── shared/              Test sources every platform compiles (broken test mods, stand-ins)
│   ├── neoforge/
│   │   ├── common/          Code every NeoForge version compiles: bridges, mixins, the test mods (src/testmod)
│   │   ├── common121/       Code 1.21.1 and 1.21.11 compile (transfer, networking, loot and biome ports)
│   │   ├── common12111/     Code 1.21.11 and all 26.x compile (NeoForge's transfer API bridges, hooks
│   │   │                    whose targets differ on NeoForge 21.1)
│   │   ├── common26/        Code all 26.x versions compile (renderer API terrain bridge, 26.x mixins)
│   │   ├── common26early/   Code 26.1.2 and 26.2 compile
│   │   ├── common26late/    Code 26.2 and 26.3 compile
│   │   ├── 1.21.1/          What only 1.21.1 has (Java 21): vendored native Fabric API modules (src/fabricapi),
│   │   │                    fabric-api-plan.json, version-specific bridges; likewise 1.21.11 (Java 21),
│   │   └── ...              26.1.2, 26.2 and 26.3 (Java 25)
├── ci/                      Server, client and mod-corpus smoke tests run by GitHub Actions
└── docs/                    claude.md (start here), roadmap.md, RESEARCH, ARCHITECTURE, COMPATIBILITY, MIXINS, API
```

* **Output:** one jar per MC version, `fabricated-<version>+mc<mc>.jar`. `core`, `loader`, `fabric-loader-api` and `api` are shaded in, all under the Fabricated-owned package `io.github.cmdralph.fabricated.*`, so they can't clash with anything. The `fabric-loader-api` types keep their original `net.fabricmc.*` package names because Fabric mods link against them.
* **No preprocessor** (no Stonecutter/Manifold). Version differences live in each `mc/neoforge/<version>` directory; code shared by every version lives once in `mc/neoforge/common`, by 1.21.1 and 1.21.11 in `mc/neoforge/common121`, by 1.21.11 and all 26.x in `mc/neoforge/common12111`, by all 26.x versions in `mc/neoforge/common26`, by 26.1.2 and 26.2 in `mc/neoforge/common26early`, and by 26.2 and 26.3 in `mc/neoforge/common26late` (see `sharedSources` in `mc/mc.gradle`).
* `core`, `api` and `fabric-loader-api` target Java 17 bytecode so the *same* compiled classes run on every line, and on Minecraft 1.20.1 (`RESEARCH.md` §8, D2).

---

## 3. Mod discovery and metadata translation

### 3.1 Claiming Fabric jars
`FabricCandidateLocator implements IModFileCandidateLocator` (ServiceLoader-registered) claims any jar in the mods folder containing `fabric.mod.json` and **no** `META-INF/neoforge.mods.toml`, which leaves multi-loader jars to NeoForge. A jar with both is claimed too when its NeoForge side starts nothing of its own while its Fabric side has entrypoints: Java's language loader and no class with NeoForge's `@Mod` (`MultiLoaderJars`, MULTI-1). That is a Fabric build that kept its NeoForge build's metadata, as Do a Barrel Roll's 26.x jars do; NeoForge would load its Fabric mixins as a NeoForge mod's. It only marks the jar as located (`addLocated`), so NeoForge doesn't report it as an incompatible Fabric jar; it is not a NeoForge mod file. `FabricDependencyLocator implements IDependencyLocator` then processes all candidates together, since resolution needs the global view. The same discovery pattern is used by prior art; this implementation is clean-room.

### 3.2 `fabric.mod.json` → descriptor
* `schemaVersion` 1 is fully supported. Schema 0 (pre-2019) is rejected with a clear message.
* **Strict but forgiving parsing:** unknown keys are ignored. Malformed *required* keys reject only that mod. Files are capped at 1 MiB.

| Fabric field | NeoForge / Fabricated translation |
|---|---|
| `id` (`^[a-z][a-z0-9-_]{1,63}$`) | NeoForge id = `-` → `_` (NeoForge allows `^[a-z][a-z0-9_]{1,63}$`). The original Fabric id is kept as an **alias** so `FabricLoader.isModLoaded("cloth-config")` keeps working. An id that's invalid after normalisation is rejected with a message. |
| `version` (SemVer or free-form) | `version` string unchanged. Comparison uses Fabric SemVer rules in `core`. |
| `name`, `description`, `authors`, `contributors`, `contact`, `license`, `icon` | `displayName`, `description`, `authors`, `credits`, `displayURL`/`issueTrackerURL`, `license`, `logoFile` |
| `environment` (`*`/`client`/`server`) | The mod is skipped (with an INFO note) when the dist doesn't match |
| `depends` / `recommends` / `suggests` / `conflicts` / `breaks` | Resolved **by Fabricated** (§3.4). NeoForge dependencies are also emitted for ordering (`ordering="AFTER"`, type `optional`) so FML's sort respects them without FML being able to abort the whole game over a missing one. Orderings that would close a cycle, which Fabric's dependencies may form and every platform's sort refuses, are left out, one per cycle, and the report names them (ORDER-1, `LoadOrderings`). |
| `provides` | Extra aliases (e.g. a library provides `fabric-api-base`) |
| `entrypoints` | Stored for the language loader (§4) |
| `mixins` (string or `{config, environment}`) | `[[mixins]]` entries in the generated toml, filtered by environment at generation time, since the dist is known at discovery |
| `accessWidener` / `classTweaker` | Converted to `META-INF/accesstransformer.cfg` in the overlay (§7) |
| `jars` | Nested jars, extracted & deduped (§3.3) |
| `languageAdapters` | Registered in Fabricated's adapter registry (e.g. Kotlin's adapter) |
| `custom` | Exposed through `ModMetadata.getCustomValue`. Otherwise ignored. |

The generated overlay `neoforge.mods.toml` uses `modLoader="fabricated"`, so FML hands these mods to `FabricLanguageLoader`, and `loaderVersion` is pinned to Fabricated's own range.

### 3.3 Nested jars (Fabric jar-in-jar)
* Extracted **from the validated archive only**, to `<gameDir>/.fabricated/cache/v1/nested/<sha256>.jar` (content-addressed, so it's written once).
* Max nesting depth is 3. Every level gets the same archive checks.
* Nested jars with `fabric.mod.json` become Fabric candidates. For duplicates by id, **highest version wins**, as in Fabric Loader. Fabric API modules nested inside mods are routed to the module plan (§6).
* A nested jar without `fabric.mod.json` is rejected and reported (Loom gives every nested library one).

### 3.4 Dependency resolution (why Fabricated, not FML, decides)
FML treats an unmet *required* dependency as a fatal loading error, which would let one Fabric mod block the game. Fabricated therefore resolves the Fabric graph itself first:
1. Build the graph over Fabric candidates, NeoForge mods (exposed under their ids), built-ins (`minecraft`, `java`, `fabricloader`, `fabric-api`, every `fabric-*` module id, `neoforge`), and aliases.
2. Version predicates use Fabric semantics (`>=1.2 <2`, `~1.2`, `^1.2`, `1.2.x`, `*`, arrays as OR).
3. A mod whose `depends`/`breaks` is unsatisfied is **disabled together with its dependents**, transitively. Each one gets a WARNING `ModLoadingIssue`, e.g. *"`mymod` needs `cloth-config >=15` (found 14.0.2). Install a newer Cloth Config."* `conflicts` produces a warning only, as in Fabric.
4. `fabricloader` is satisfied by the Fabric Loader version whose public API Fabricated reproduces (0.20.1,
   `Pipeline.FABRIC_LOADER_COMPAT_VERSION`). `minecraft` is matched against the running version.
5. **Native NeoForge mod vs. Fabric mod with the same id:** the NeoForge build is used, as NeoForge loads one mod per
   id; the player removes the NeoForge jar to use the Fabric one.
6. `strictDependencies=true` in config passes required deps straight to FML instead, for users who prefer a hard stop.

---

## 4. Entrypoint bridging

### 4.1 Lifecycle mapping (verified against NeoForge `CommonModLoader`)
NeoForge's sequence is: mod construction, then `unfreezeData()`, then a `RegisterEvent` per registry in mod order, then `freezeData()`, then `FMLCommonSetupEvent`, then the sided setup event, then IMC, then `FMLLoadCompleteEvent`. Vanilla registries are **frozen during mod construction**. Fabric mods call `Registry.register(...)` directly in `onInitialize`, so they must run while the registries are unfrozen. On the client NeoForge does all of this up to the freeze (and its configs) before it creates `Minecraft`, and the rest from `Minecraft`'s constructor, after the point where Fabric Loader runs the initializers.

| Fabric entrypoint | When Fabricated runs it | Ordering |
|---|---|---|
| `preLaunch` | **Not run as Fabric does** (Fabric runs it before game classes load). Fabricated runs it right before `main`, on the first `RegisterEvent` (`EntrypointBridge`). Mods doing classloader tricks there are unsupported; running it earlier is on the roadmap. | Dependency order |
| `main` (`ModInitializer`) | On a dedicated server (and in a data run), once, on the **first `RegisterEvent` after the attribute registry's** (which NeoForge posts first): registries are unfrozen, NeoForge's own attributes (part of every living entity's attribute builder) are registered, and NeoForge mods constructed before it are visible. On a client, where Fabric runs it: in `Minecraft`'s constructor, right before the `client` entrypoints, with the registries opened again (CORPUS-16); what NeoForge fills once its registration events end (attributes, spawn placements, fluid types, creative tab order) then takes what it registered | Fabric dependency order (topological, ties broken by id, so it's deterministic) |
| `client` (`ClientModInitializer`) | After *all* `main` entrypoints, on the client dist, where Fabric runs it: in `Minecraft`'s constructor just before it records the game thread, once the instance, game directory and user are set (NeoForge registers mods before it creates `Minecraft`, and initializers commonly use the instance), with the registries open until it ends, as Fabric API leaves them | Same |
| `server` (`DedicatedServerModInitializer`) | Immediately after all `main`, on a dedicated server | Same |
| Custom keys (`modmenu`, `rei_client`, `fabric-datagen`, `cardinal-components`, …) | **Never auto-run.** Instantiated lazily when another mod calls `FabricLoader.getEntrypoints(key, type)` / `getEntrypointContainers`, exactly like Fabric | Declaration order |

* Entrypoint value forms supported: `pkg.Class` (no-arg constructor, or must implement the type), `pkg.Class::staticField`, and `pkg.Class::method` (static or instance-on-singleton, adapted to the functional interface with a `Proxy`), all through the `LanguageAdapter` contract. `adapter: "kotlin"` works when a mod provides that adapter.
* Work the bridges collect during `main`/`client` (payload types, key mappings, commands, reload listeners) is **buffered** and flushed in the matching NeoForge event (`RegisterPayloadHandlersEvent`, `RegisterKeyMappingsEvent`, `RegisterCommandsEvent`, …) [verify-in-build: each event fires after the registry window].

### 4.2 Failure handling in entrypoints (§10 has the full model)
Each mod's entrypoints run inside a guard that records mod id, entrypoint key, class and exception.

---

## 5. Mixin handling

1. **Mixin is native:** FML applies `[[mixins]]` configs declared in the generated toml, using **Fabric's own Mixin fork** (`net.fabricmc:sponge-mixin`; 0.16.5 on 1.21.11, 0.17.4 on 26.x, the same version as Fabric Loader 0.19). MixinExtras ships with NeoForge. Fabricated does **not** bundle Mixin.
2. **Compatibility level:** Fabric Loader sets the per-config `FabricUtil.KEY_COMPATIBILITY` from the mod's `fabricloader` dependency. NeoForge exposes the same decoration as `[[mixins]] behaviorVersion`. Fabricated emits the value Fabric Loader would compute, clamped to what the running FML supports. [verify-in-build: version semantics]
3. **Refmaps:** on 1.21.11 each refmap is rewritten from intermediary to Mojang names in the cache copy. On 26.x refmaps (if present at all) are left alone.
4. **Pre-flight verifier (the robustness centrepiece).** It reads the **runtime game bytecode** (NeoForge-patched Minecraft, as bytes only) and every mixin class, and checks:
   * the `@Mixin` targets exist (incl. `targets = "…"` strings);
   * `@Shadow`/`@Accessor`/`@Invoker` members exist with matching descriptors;
   * the injector target methods exist (`method = "name(desc)"`, selector and wildcard forms, MixinExtras `@WrapOperation`/`@ModifyExpressionValue`/`@WrapWithCondition`/`@ModifyReturnValue` …);
   * the `@At` points can be found for `HEAD`, `RETURN`, `TAIL`, `INVOKE`, `INVOKE_ASSIGN`, `FIELD` and `NEW`, honouring `ordinal` and `opcode`. Other points (`JUMP`, `CONSTANT`, `LOAD`/`STORE`, slices) are checked conservatively: "unknown" never fails a mod, only "definitely absent" does.
   * `@Inject` handlers take either only the callback info or the target's parameters followed by it (MixinExtras sugar parameters such as `@Local` aside). NeoForge recompiles Minecraft, so a synthetic lambda can take its captured values in another order. Handlers that capture locals or use `@Coerce` are not judged;
   * `@Local(name = …)` captures name a local variable the target declares (when the game class carries local names), and `@ModifyConstant` constants occur in the target;
   * merged methods, `@Accessor`s and `@Invoker`s don't collide with a method the target already declares (NeoForge adds some, such as `hashCode` or public getters);
   * a `NEW` point with a constructor descriptor matches that constructor only, and targets in library classes NeoForge loads without mixin support (DataFixerUpper, Brigadier, the JDK) fail. A mixin into such a class that only gives it interfaces of the mod, implemented with the class's fields and the mixin's own methods (Carpet's command removal), never reaches that check: translation replaces it with a generated class serving the interfaces (`LibraryBridges`, LIB-1), whose static methods read and write the fields through reflection, and points the mod's casts to the interfaces, checks against them and calls of their methods at it.

   Mixins into the classes of the jars installed next to the Fabric mods get the same checks (`InstalledClasses`:
   Fabricated's Fabric API, then each NeoForge mod's jar, nested ones too, then the Fabric mods' jars, whose classes
   load unchanged on 26.x and are remapped as they are read on 1.21.11, as translating each mod remaps it). That covers a Fabric addon of a mod whose NeoForge build is installed (Sodium's,
   nested in a service jar) and of another Fabric mod. A Fabric jar is opened only when a target is in one of its
   packages (recorded at discovery). A target in none of them is not judged. The cache entry records each installed class pre-flight read with its SHA-256; when one
   differs at the next launch (the NeoForge mod was updated), the mod is translated again.

   The game is seen as Fabricated's own mixins make it: the fields they add (vanilla's picture-in-picture renderer
   map, which NeoForge replaced) are part of the game classes pre-flight reads (`RestoredFields`). A field or method a
   target lacks but an installed Fabric jar's mixin adds to it (Fabric API's `updateSelection` on the creative screen)
   is not judged: Mixin merges every mixin's members into a class before any injector looks for its target. The
   adding mixin's class is recorded with its SHA-256 like the installed classes above.

   Outcome per mixin:
   * A missing target class fails only where Mixin rejects it: in a required config at compatibility level 0.17.4 or later (before that, Mixin skips the target).
   * In a config with a plugin, which may disable mixins at runtime, an injector that would crash the game is **left out** instead: only its handler, as Mixin itself drops an injector that may fail. A problem with the whole mixin leaves out the mixin, with the mixins depending on its interfaces. Mixins the plugin adds at runtime (`getMixins()`, listed in no config) are found among the classes of the config's package, get the same repairs, and are checked the same way; only their failing injectors are left out, since the plugin may never add them.
   * Otherwise `required` or default-require mixins that **would fail** cause the **whole mod to be disabled before launch**, with a report like *"`examplemod`: mixin `ExampleMixin` targets `ItemStack#getTooltipLines` → `INVOKE List.add`, which NeoForge removed. Ask the author for a NeoForge build, or set `skipMixins` if this mixin is cosmetic."*
   * Non-required mixins that would fail produce a warning only.
5. **User overrides:** `skipMixins` drops entries from the cached copy of the mixin config JSON, never the original.
   An entry `"config.json:Name#member"` instead writes a copy of that mixin class without the methods or fields of
   that name (a handler, or a `@Shadow` of a field NeoForge replaced) into the overlay jar; pre-flight checks the copy.
6. **Runtime attribution:** a mixin failure that slips past pre-flight is Mixin's own crash, which names the mixin
   config; the report (§13) maps each config to its Fabric mod. Fabricated adds nothing to the crash report itself.
7. **No guessing.** Mixins are rewritten only where the right member is certain: javac's names for synthetic
   members differ in NeoForge's recompiled build while the member is the same, so pre-flight points an
   `@Accessor("val$name")` at the target's only captured field of that type, an injector into `lambda$method$N` at
   the target's only lambda of that enclosing method with the exact descriptor, and an accessor duplicating a
   public method the target already has at that method. A descriptor-less `@Inject` is pinned to the one overload whose
   parameters its handler takes when NeoForge's build declares another overload of that name first (Mixin picks the
   first). An injector whose method NeoForge turned into a delegate to a
   new overload holding the code is pointed at that overload when every injection point is there and the handler
   doesn't depend on the method's parameters. Each repair is a warning in the report; anything ambiguous is left to
   fail. Two changes only turn a crash into a missing effect, never the reverse: an `@Inject` whose local capture
   fails hard skips itself instead when NeoForge's locals differ, and in a config with a plugin (which may disable
   mixins at runtime) a mixin pre-flight proves would crash is left out instead of disabling the mod.

---

## 6. Fabric API strategy (hybrid)

### 6.1 Layers
1. **Fabric Loader API** (`fabric-loader-api` + `loader`): `FabricLoader` (mods, entrypoints, `getConfigDir`/`getGameDir`, `getEnvironmentType`, `isDevelopmentEnvironment`, `getObjectShare`, `getLaunchArguments`, `getGameInstance`, `getMappingResolver`), `ModContainer` / `ModMetadata` for **all** mods (NeoForge mods appear too, so `isModLoaded` sees them), `Version` / `VersionPredicate`, `@Environment` / `EnvType`, and `LanguageAdapter`. `MappingResolver`: identity with runtime namespace `official` on 26.x (matching Fabric Loader's behaviour on unobfuscated MC). On 1.21.11 it maps `intermediary`→runtime and reports `named`, so mods that check it call `map*` and get Mojang names.
2. **Fabric API**, decided per module:
   * **Native**: Fabricated provides the module's public API itself, implemented on NeoForge events and systems. The upstream module jar is **not** loaded.
   * **Pass-through**: the module from the player's **official Fabric API jar** (Fabricated does not redistribute it) goes through the normal pipeline like any Fabric mod. Pre-flight decides whether it can load.
   * **Unsupported**: the module isn't loaded. Mods that `depends` on it are disabled with a clear message, and mods that only *use* it optionally keep working.
3. **Satisfying `fabric-api` dependencies:** the umbrella id `fabric-api` and each module id are "provided" when native, or pass-through and passing pre-flight.

### 6.2 Module classification

The same on every version unless a row says otherwise. 1.21.1's Fabric API (0.116) also carries the deprecated modules
later releases dropped (pass-through), and gets `fabric-particles-v1` natively (PART-3).

| Module (26.x name / 1.21.11 name) | Plan | Bridged onto |
|---|---|---|
| `fabric-api-base` | **Native** | upstream's code, vendored: every module builds on it |
| `fabric-lifecycle-events-v1` | **Native** | NeoForge's events: `ServerAboutToStart/Started/Stopping/StoppedEvent`, `ServerTickEvent`, `LevelTickEvent`, `ClientTickEvent`, level, chunk and entity load events, `TagsUpdatedEvent`, … |
| `fabric-networking-api-v1` | **Native** | NeoForge's payload system (login queries are not supported) |
| `fabric-registry-sync-v0` | **Native** | NeoForge's registries, which already sync ids to clients |
| `fabric-resource-loader-v1` (`-v0` on 1.21.1) | **Native** | NeoForge's reload-listener and pack-finder events |
| `fabric-entity-events-v1` | **Native** | NeoForge's entity events (elytra and most sleep events are not fired) |
| `fabric-events-interaction-v0` | **Native** | NeoForge's interaction events |
| `fabric-item-api-v1`, `fabric-block-api-v1` | **Native** | NeoForge's item hooks and events, and its block hooks |
| `fabric-object-builder-api-v1` | **Native** | NeoForge's registration events |
| `fabric-loot-api-v3` | **Native** | upstream's builder and drop mixins, its loading hook replaced by NeoForge's `LootTableLoadEvent` |
| `fabric-content-registries-v0` | **Native** | upstream's registries and mixins, with NeoForge's data maps backed by them where an entry is missing |
| `fabric-transfer-api-v1`, `fabric-api-lookup-api-v1` | **Native** | upstream's API and mixins (transfer's container mixins reworked onto NeoForge's transaction-aware `setItem`), bridged to NeoForge's capabilities (§6.3) |
| `fabric-menu-api-v1` / `fabric-screen-handler-api-v1` | **Native** | NeoForge's menu opening data |
| `fabric-crash-report-info-v1` | **Native** | nothing to bridge: NeoForge already lists every mod in crash reports |
| `fabric-biome-api-v1` | **Native** | upstream's API and biome source mixins, with biome modifications applied as a NeoForge biome modifier on NeoForge's builders (before 26.3, environment attributes on the biome) |
| `fabric-command-api-v2`, `fabric-convention-tags-v2` (and v1 on 1.21.11), `fabric-creative-tab-api-v1` / `fabric-item-group-api-v1`, `fabric-key-mapping-api-v1` / `fabric-key-binding-api-v1`, `fabric-screen-api-v1`, `fabric-rendering-v1`, `fabric-particles-v1`, `fabric-message-api-v1`, `fabric-resource-conditions-api-v1`, `fabric-resource-loader-v0`, `fabric-data-attachment-api-v1`, `fabric-block-getter-api-v2`/`block-view-api-v2`, `fabric-tag-api-v1`, `fabric-serialization-api-v1`, `fabric-sound-api-v1`, `fabric-recipe-api-v1`, `fabric-dimensions-v1`, `fabric-game-rule-api-v1`, `fabric-model-loading-api-v1`, `fabric-rendering-fluids-v1`, `fabric-transitive-access-wideners-v1`, `fabric-advancement-api-v1` (26.2, 26.3), `fabric-permission-api-v1` and `fabric-debug-api-v1` (26.x), `fabric-loot-api-v2` (1.21.11) | **Pass-through** | upstream code; a version's plan leaves out the upstream mixins NeoForge's code can't take (`skipMixins`), some of them replaced by Fabricated's own hooks |
| `fabric-renderer-api-v1`, `fabric-renderer-indigo` | **Pass-through** | upstream code with Indigo as the renderer; only Fabric models are sent through it, and NeoForge draws every other block (COMPATIBILITY.md) |
| `fabric-data-generation-api-v1` | **Pass-through** | mods' runtime code references its classes (Wover's entrypoints load its tag providers, DATAGEN-1); its hooks act only when the `fabric-api.datagen` property is set |
| `fabric-gametest-api-v1`, `fabric-client-gametest-api-v1` | **Unsupported** | development-time only |

This table summarises data (`mc/neoforge/<version>/src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json`: each
module's `plan`, `reason` and `skipMixins`), not code, so it can change per MC version without code changes.

### 6.3 Bridges between Fabric's systems and NeoForge's

Where both loaders have a system for the same thing, a Fabric mod and a NeoForge mod meet through a bridge rather
than one of them losing out (`neoforge/transfer`):

* **Storages** (`TransferBridge`): Fabric storages and NeoForge resource handlers see each other on blocks
  (`ItemStorage.SIDED`/`FluidStorage.SIDED` and `Capabilities.Item/Fluid.BLOCK`) and in items (`*.ITEM` and
  `Capabilities.Item/Fluid.ITEM`). An item's storage changes the item through the other side's access to where it is
  (`AccessItemContext`, `ContextItemAccess`). Each side answers for its own mods' content, told apart by namespace:
  NeoForge's capabilities for Fabric mods' blocks and items come from Fabric's whole lookups, and Fabric's lookups fall
  back to NeoForge's capabilities for NeoForge mods' blocks and items, so neither ever asks the other back. Vanilla
  blocks and items keep each side's own wrappers.
* **Transactions** (`FabricTransactions`, `NeoTransactions`): an operation from one side runs in a transaction the
  other side mirrors, and its result follows the original's (abort, nested commit, commit). Where a transaction
  already mirrors one of the other side's, as when an item's handler calls back into its access, it stands for that
  one instead of nesting a new mirror, so every transaction closes inside its parent on both sides.
* **Energy** (`EnergyBridge`): Team Reborn's energy API, the Fabric tech mods' shared library, and NeoForge's energy
  capabilities, loaded only when a Fabric mod brings the library. Fabricated compiles against never-packaged
  stand-ins of its API (`integrationstubs`) and finds its lookups by id.
* **Fluid types** (`neoforge/fluid/FabricFluidType`): NeoForge asks every fluid for a fluid type and throws for a
  mod's fluid without one. `FluidTypeMixin` answers for Fabric fluids where NeoForge would look up vanilla's types,
  with one registered type per fluid family that reads Fabric's attributes and the fluid's tags when asked.
  `LivingEntityFluidTravelMixin` keeps a fluid that isn't water to entities from moving them, as on Fabric. On
  1.21.11 the type's client extensions come from the Fabric render handler (`FabricFluidClientExtensions`).

**Scope honesty:** the native modules are the ones marked above. Where one falls short of upstream, its plan's `reason` says how (login queries in networking, elytra and most sleep events in entity events, …). Every gap ends up in `docs/COMPATIBILITY.md`.

**Other mods' copies (the provider policy).** A package loads from one module only, so before FML builds the module
layer every package Fabricated would add has one owner (COMPATIBILITY.md, *Mods that bring Fabric API or Fabric
Loader*):

* *A Fabric API module another mod provides is that mod's (PROVIDER-1).* `NeoForgeMods` lists the NeoForge mods and the
  packages of their jars; a mod provides a module by its id, its `provides` or one of the module's packages
  (`FabricDependencyLocator.providingByPackages`). `Pipeline.providedElsewhere` gives the plan's modules so provided,
  whose ids then satisfy dependencies and whose rows read `PROVIDED_BY_MOD`. For a native one, core's `NativeModules`
  says what stands down, from two data files: the version's `native-modules.json` (each native module's packages,
  written by `scripts/native-module-index.py` from the plan's official Fabric API release; a package Fabric API keeps
  in two modules is given to one, as Forgified Fabric API does) and the in-game mod's `mixin-modules.json` (the
  module each of Fabricated's mixins serves; the build's `checkNativeModules` refuses a mixin or package missing from
  them). The in-game mod is then handed to FML without those packages, with an overlay holding its `mods.toml` and
  mixin configs without the mixins that stand down, and `NativeFabricApi` registers no bridge of a module another mod
  provides (`FabricatedState.providedElsewhere`). Code that serves no module (entrypoints, registries, fluid types,
  vanilla behaviour Fabric mods rely on) always runs.
* *Fabric Loader's API is Fabricated's (PROVIDER-2).* `FabricLoaderApiReader`, an FML mod file reader ahead of FML's
  own, reads any jar but a Fabricated build that has `net/fabricmc/loader/` or `net/fabricmc/api/` without them and
  without the service files naming their classes (`FabricLoaderApiCopies`). On FML 4 the jar's contents are filtered;
  FML 10 and later load plugin libraries with a plain class loader, which can't filter, so they get a copy of the jar
  without those files under `.fabricated/loader-api/<sha-256>/`, of the same type.

---

## 7. Remapping (1.21.11) and determinism

### 7.1 Mapping construction (offline, once per MC version)
1. `intermediary-1.21.11.tiny` (bundled, CC0) gives *official → intermediary*.
2. Mojang mappings (`client-1.21.11-mappings.txt` on the client, `server-…` on the server) come from the **local** libraries directory the NeoForge installer populated. For the dev environment, an explicit path can be set in config or the system property `fabricated.mojmapsPath`. If the file is missing, *all* 1.21.11 Fabric mods are disabled with one clear message explaining how to repair the install. **Nothing is downloaded.**
3. Compose them to *intermediary → Mojang* for classes, fields (name and descriptor) and methods (name and descriptor). Intermediary method names are hierarchy-consistent, so flat lookup is correct. Duplicated intermediary names are handled descriptor-qualified.
4. Serialise to a compact, deterministic binary (`blobs/mappings-1_21_11-<hash>.bin` in the cache) for fast reload. The hash covers the Minecraft version, Fabricated's version (whose jar bundles Intermediary), the Mojang file's SHA-256 and the side (`IntermediaryMappings`).

### 7.2 Jar remapping
* ASM `ClassRemapper` with a Fabricated `Remapper` over the composed table. Everything is processed as bytes, with **no classloading**.
* Also remapped: mixin refmaps, mixin annotation strings (`@Mixin(targets=…)`, `method=…`, `@At(target=…)`) for mods that ship without refmaps, `META-INF/services` entries naming MC classes, access widener files, and **string constants matching** `net/minecraft/class_\d+`, `net.minecraft.class_\d+`, `method_\d+` or `field_\d+` (exact token match only, for reflection users).
* Mods compiled against **NeoForge-absent intermediary members** (e.g. a method NeoForge removed) are caught by pre-flight and the linkage scan, not at runtime.

### 7.3 Determinism
Output jars are byte-for-byte reproducible:
* entries are sorted, with a fixed timestamp (1980-02-01T00:00:00Z) and no extra fields;
* fixed compression level, normalised manifest, no signature files (stripped, and the report notes it);
* the stable ASM writer runs without `COMPUTE_FRAMES` unless needed, and then with a deterministic common-superclass resolver that reads bytes and never loads classes.

A unit test remaps the same input twice and compares SHA-256.

---

## 8. Access widener / class tweaker → access transformer

| AW / CT directive | AT output (target names already in runtime namespace) |
|---|---|
| `accessible class C` | `public C` |
| `extendable class C` | `public-f C` |
| `accessible method C m d` | `public C m d` |
| `extendable method C m d` | `protected-f C m d` (`public-f` if already public) |
| `accessible field C f d` | `public C f` |
| `mutable field C f d` | `<current access>-f C f`, with the current access read from game bytecode (AT has no "keep access" modifier) |
| `transitive-*` | same as the non-transitive form (ATs are global) |
| `inject-interface C I` (class tweaker) | **not expressible in AT.** Applied by Fabricated's `ClassProcessor` (FML SPI), which adds `I` to `C` at load time. Generic signatures are kept. |
| `extend-enum C K` (class tweaker v2) | nothing: the line only declares a constant the mod's enum mixin adds, which Mixin (0.17.1 or later) does itself. The mod is disabled if the running Mixin is older, or if `C` is one of NeoForge's `IExtensibleEnum`s (FML refuses enum mixins into those, ENUM-4) and the mixin can become neither an enum extension (ENUM-3) nor an ordinary mixin adding the constants (ENUM-5). |

* Fabric also makes a private instance method made `accessible` final unless a mod extends it, from all mods' wideners
  merged. NeoForge reads each mod's file on its own and stops the game when one's `+f` meets another's `-f` on the
  same member (AT-1), so that final isn't written: where another mod extends the method, the lines merge to Fabric's.
* Headers are validated: `accessWidener v1|v2 <ns>` and `classTweaker v1|v2 <ns>`. The namespace must be `intermediary` (1.21.11) or `official` (26.x), otherwise the mod is rejected with an explanation.
* Entries targeting members that don't exist are dropped with a warning (Fabric Loader would crash).
* The AT file is written into the overlay at `META-INF/accesstransformer.cfg`, which FML picks up by default.

---

## 9. Per-version abstraction

There is no adapter interface: each version is its own Gradle project (`mc/neoforge/<version>`), and what differs between
versions is decided at compile time by which source folders it compiles, plus two data files.

* **Code.** `sharedSources` in `mc/mc.gradle` adds `mc/neoforge/common` to every version, `mc/neoforge/common12111` to
  1.21.11 and 26.x, `mc/neoforge/common26` to all 26.x versions, and `mc/neoforge/common26early` / `mc/neoforge/common26late` to the pairs that share an API. A version's own folder holds
  only what differs (renamed Minecraft or NeoForge classes). Shared code calls the game by 1.21.11's names; a version
  whose names are older lists the renames in its `renamed_classes` property (1.21.1: `Identifier=ResourceLocation`), and
  the build compiles a renamed copy of the shared folders for it. Small per-version helpers (`GameCompat`, the test
  mod's `TestCompat`) hold the calls whose signatures changed. The vendored native Fabric API modules are per version
  (`mc/neoforge/<version>/src/fabricapi`), because upstream's sources differ per version.
* **Data.** `META-INF/fabricated/fabric-api-plan.json` (per version: which Fabric API modules are native,
  pass-through or unsupported, and which upstream mixins to skip) and the generated `version.properties` (Minecraft
  version, mapping namespace, Mixin versions, Fabricated's version and build digest). Core reads the plan and `loader`'s `FabricatedInfo` the properties, each validated when parsed. Facts about other
  people's mods are core's `compat-plan.json` (`CompatPlan`): Fabric builds that can't run on NeoForge, and Fabric
  builds that can't run next to a NeoForge mod; a unit test parses the shipped file.
* **Mixin differences in Fabric mods** are repaired by core's pre-flight against the running game's bytecode
  (`MixinPreflight.repairs`: overloads NeoForge reordered or moved code into, constructors NeoForge kept for
  vanilla's signature, calls and constructor calls made through a subtype or given one more argument (through bridge
  handlers that pass a handler's MixinExtras sugar on), renumbered lambdas, calls moved into a lambda of the method, hard local captures, accessors of members
  NeoForge added), not by per-version tables. The injector repairs run in one order (`MixinRepairs`), each from its
  family's class (`LambdaRepairs`, `OverloadRepairs`, `CallRepairs`, `ConstructorRepairs`, `LocalRepairs`). Enum mixins into
  NeoForge's extensible enums become its enum extension entries (`EnumExtensions`), named enums included when their
  names already carry the mod's id. Mixins only giving a library class the mod's interfaces become generated classes
  the mod calls instead (`LibraryBridges`).
* Everything Minecraft-agnostic stays in `core`/`loader`. The full seam table is in [`claude.md`](claude.md).
* **Adding a version** (summarised from `CONTRIBUTING.md`): copy the nearest `mc/neoforge/<version>` directory, set
  `minecraft_version`/`neoforge_version` in its `gradle.properties` (`settings.gradle` finds the folder), add it to the CI matrix, fix
  compile errors (usually renamed MC/NeoForge classes), adjust `fabric-api-plan.json`, and run the test mods. No core
  changes are expected.

---

## 10. Failure model

| Stage | Example | Behaviour | Game continues? |
|---|---|---|---|
| Archive validation | zip-slip entry, size bomb, corrupt zip | Mod rejected, WARNING issue + report | ✅ |
| Metadata | invalid JSON, bad id, schema 0 | Mod rejected | ✅ |
| Config | user-disabled mod | Skipped, INFO | ✅ |
| Dependencies | missing/too-old dependency, `breaks` | Mod and dependents disabled, WARNING with the exact fix | ✅ |
| Fabric API plan | depends on an unsupported module | Mod disabled, names the module | ✅ |
| Mapping (1.21.11) | Mojang mapping file missing | All 1.21.11 Fabric mods disabled, one clear issue | ✅ |
| Remap/translate | unexpected bytecode error | Mod disabled, stack trace in report | ✅ |
| Mixin config elsewhere | a mod declares a config its nested jar holds (adventure-platform-fabric) | The nested jar applies it (`CandidateScanner`): its translation pre-flights it and its mod file declares it. A config no jar holds disables the mod. | ✅ |
| Mixin pre-flight | required injection point absent | Mod disabled, names mixin, target and point | ✅ |
| Split package | two mods share `com.foo` | Identical duplicate classes are filtered out. Classes only the later mod has are served by the earlier mod's module (`PackageGuard`, the package's other files added to that module as a filtered path), as Fabric's single class path allows: Kotlin, Compose and MixinSquared jars split packages this way. A service file whose providers go with the package goes too: hidden when that module declares the same service with the same providers, served by it otherwise. A class both have with different bytes (two versions of a library they bundle) is served from the earlier jar, as the first jar on Fabric's class path would be, with a warning in the report. A service that can't follow its providers disables the later mod. | ✅ |
| Service file naming a missing provider | a shaded library's `META-INF/services` file lists a class its jar doesn't have (or one in a package the platform's copy serves) | Left out of the mod's module, with a report note: a module declares only providers it has, or no mod loads (FML refuses the module before the game starts). On Fabric's class path such a file fails only when the service is loaded. | ✅ |
| Resources in another jar | a library's native libraries are in a jar without classes (Skiko) | `Class.getResource` in a module searches that module only. The root files of a nested jar without classes are also served by the modules of its parent and of the jars nested beside it, as Fabric's class path finds any jar's; a file a module has itself stays its own. | ✅ |
| Entrypoint exception | `onInitialize` throws | Default `onEntrypointFailure="error"`: NeoForge's **error screen** lists mod, entrypoint, cause and fix (*"Disable `x` in config/fabricated.json or update it"*). With `"disable"`: the mod is marked failed, its remaining entrypoints are skipped, and loading continues with a warning. Partially registered content may remain, which the report states; content built but never registered is dropped at the registries' freeze, with an error naming it (CORPUS-42), instead of failing vanilla's holder check. | ⚠️ configurable |
| Mixin apply at runtime | injector fails in a way pre-flight couldn't see | Mixin's crash names the config; the report maps it to the mod | ❌ (JVM can't unload) |

* All issues go through `ModLoadingIssue` (FML's own screen: warnings are dismissible, errors block with explanation) **and** into `logs/fabric-bridge-report.txt`.
* **Rule:** anything detectable before mod code runs is isolated. After mod code runs, Fabricated explains the failure instead of crashing raw, and never claims an isolation it can't guarantee.

---

## 11. Caching

* **Root:** `<gameDir>/.fabricated/cache/v1/` (`TranslationCache.LAYOUT_VERSION`), resolved against the real game directory and checked to be inside it; no path component may be a symbolic link.
* **Key** (`ModTranslator.translate`): a SHA-256 over the input jar's SHA-256, the Minecraft and NeoForge versions, the side, Fabricated's build (its version and a digest of its own classes and data, `fabricated.build` in the `version.properties` that `mc/mc.gradle` generates), the translation schema (`TRANSLATION_SCHEMA`), the mapping table's hash, the running Mixin and MixinExtras versions, the mod's config overrides, the NeoForge ids it loads after, its mixin configs, the packages only unsupported Fabric API modules have, and whether vanilla's code was found. Rebuilding Fabricated from the same sources keeps the digest; any change to its code or data translates every mod again.
* **Entry:** `entries/<modid>-<first 40 hex of the key>/` holds `<modid>-overlay.jar` (the generated files of step 5e, with rewritten mixin classes and configs), `<modid>.jar` (the remapped jar, on 1.21.1 and 1.21.11; 26.x uses the original jar + overlay with no copy; named after the mod, as FML's logs name a mod file by its first jar) and `entry.json` (each file's SHA-256, the input hash, versions, side, timings, AT lines, pre-flight findings, notes, and the installed classes pre-flight read, with their fingerprints). Nested jars are stored once as `nested/<sha256>.jar`. Derived data (1.21.11's composed mapping table, linkage results) goes to `blobs/`, each file starting with its content's SHA-256.
* **Integrity:** with `cache.verifyIntegrity` (on by default) each file of an entry is checked against `entry.json` before use, and a blob against its leading hash. A mismatch means it is deleted and rebuilt, and the report notes it. An entry is written to `tmp/` and moved into place atomically.
* **Skip work:** an unchanged mod costs a lookup and hash checks. File hashes are memoised in `index.json` by path, with the size, modification time and, where the file system has them, change time and file key, and re-hashed when any changes. An entry is translated again when an installed class its pre-flight read has changed.
* **GC:** entries, nested jars and blobs unused for `cache.maxUnusedLaunches` launches (default 5) are deleted; `index.json` records each one's last use.

## 12. Configuration (`config/fabricated.json`)

Read during discovery, before NeoForge's config system exists, so it's plain JSON, parsed by core's own `json.Json`
(no library). The keys are documented in the README's Configuration section.

```json
{
  "schema": 1,
  "onEntrypointFailure": "error",
  "strictDependencies": false,
  "cache": { "enabled": true, "verifyIntegrity": true, "maxUnusedLaunches": 5 },
  "limits": { "maxJarBytes": 268435456, "maxEntryBytes": 134217728, "maxTotalUncompressedBytes": 536870912,
              "maxEntries": 65536, "maxCompressionRatio": 100, "maxNestingDepth": 3 },
  "mods": {
    "examplemod": { "disabled": false, "skipMixins": ["examplemod.mixins.json:client.FooMixin"],
                    "skipEntrypoints": ["client"], "aliases": ["example"] }
  },
  "mojangMappingsPath": null
}
```

Unknown keys produce a warning. Invalid values fall back to defaults with a warning, and the config never crashes the game.

## 13. Diagnostics

* `logs/fabric-bridge-report.txt`, written on every launch and on demand. It contains: environment (MC, NeoForge, FML, Java, Fabricated); per-mod status (loaded/disabled + reason) with source jar, hash, cache hit/miss and timings; the Fabric API module plan; mixin pre-flight results; AW→AT conversions; split-package decisions; config overrides applied; and warnings.
* `/fabricated report` regenerates the report and prints a summary. It's a server command that needs permission level 2. A client-side command with the same name works for any player on their own client.
* Logging uses SLF4J, through loggers named `Fabricated`.

## 14. Security model

| Threat | Mitigation |
|---|---|
| Path traversal / zip-slip | Entry names are validated, not rewritten: a jar with an empty, absolute, `.` or `..` segment, a backslash, a drive letter, a control character (NUL included) or a duplicate name is rejected. Nothing is extracted under an entry's name: nested jars go to the cache as `nested/<sha256>.jar`, and the cache root is checked to resolve inside the real game directory. |
| Decompression bombs | Caps on jar size, entry size, total uncompressed size, entry count and compression ratio. Declared sizes are checked when the jar is opened; reads count the bytes actually inflated, and refuse an entry that inflates past the size its header declares. |
| Malformed JSON / huge metadata | 1 MiB cap and strict types. Parse errors are per-mod. |
| Code execution during translation | Bytes-only processing (ASM `ClassReader`). No mod class is loaded, no service loading from mod jars, no scripting. |
| Cache tampering | Each cached file's SHA-256 verified before use (`cache.verifyIntegrity`, on by default). Cache confined to the game dir, symlinks refused. |
| Network | Fabricated never opens sockets/URLs. A unit test (`NoNetworkTest`) scans the compiled classes of `core` and `fabric-loader-api` (not `loader`, `api` or the `mc` projects) for references to `java.net` sockets, URLs and HTTP connections, `java.net.http`, socket channels and `javax.net`. There's no updater, downloader or telemetry. |
| Original jar modification | Inputs opened read-only and never written. A test (`SafeArchiveTest`) hashes a jar before and after reading it through `SafeArchive`. |

## 15. Performance targets

* Warm launch overhead for 100 Fabric mods: **< 300 ms** (memoised hash checks + cached entries reused; the NeoForge mods' packages and the mods nested in their jars are still read on every launch with a Fabric mod).
* Cold translation on 26.x: overlay generation only, **< 20 ms per mod**. On 1.21.11: remap ≈ jar size / throughput, parallelised over cores.
* The 1.21.11 mapping table is read from the cache (or composed) during discovery only when there is a Fabric mod to translate, and then stays in memory for `MappingResolver`.
* With no Fabric jar installed, Fabricated's in-game mod isn't added (`FabricDependencyLocator`; a development workspace always loads it), so it registers no listeners and does nothing per tick. With any Fabric mod, the native modules' bridges (`NativeFabricApi`: `LifecycleBridge`, `ClientLifecycleBridge`, …) register their NeoForge listeners when the in-game mod is constructed, whether or not a Fabric listener exists: each bridged tick event then costs a NeoForge event dispatch and a call of the Fabric event's invoker per tick, with no listeners to run.

## 16. Testing and CI

* **Unit (core, runs anywhere):** metadata parsing/translation, version predicates, id normalisation, dependency resolution, archive safety (crafted malicious zips generated in-test), Tiny/ProGuard parsing and composition, remapping (incl. determinism), refmap rewriting, AW/CT→AT, pre-flight verifier (synthetic target/mixin classes generated with ASM), cache integrity, config validation, report rendering. **No third-party binaries are committed.** Fixtures are generated in tests.
* **Integration (per MC version, CI):** no `gameTestServer` run. A real NeoForge dedicated server starts with the in-repo test mods (`ci/neoforge/smoke-server.sh`: entrypoints, the Fabric Loader API, registries, mixins, access wideners, the native Fabric API modules, commands, and the isolation of a missing dependency, an impossible mixin and a throwing entrypoint), plus a client started headlessly under Xvfb (`ci/neoforge/smoke-client.sh`) that joins a singleplayer world and exits.
* **Real-mod smoke (CI only):** Fabric mods from Modrinth (`ci/mod-corpus.txt`, `ci/mod-corpus-advanced.txt` and projects named in the workflow), fetched by **CI** (never by Fabricated) with `ci/fetch-corpus.py`: each project's newest build for the version unless a Modrinth version id is named (the plugin hosts' are), each file checked against the SHA-512 Modrinth lists. Results feed `docs/COMPATIBILITY.md`.
* **GitHub Actions:** a `core` job (JDK 21: the Minecraft-independent modules' build and unit tests), then a matrix `{1.21.1: JDK 21, 1.21.11: JDK 21, 26.1.2: JDK 25, 26.2: JDK 25, 26.3: JDK 25}` (the JDK the game runs on). Steps: build Fabricated and the test mods, then the server and client runs, gates and informational ones. A version whose NeoForge line is in beta is marked `experimental` (`continue-on-error`); none is now, 26.3 having left beta.

## 17. Licensing

GPL-3.0 for Fabricated. `NOTICE` covers the Fabric Loader API types and vendored Fabric API modules (Apache-2.0, which GPL-3.0 can include) and the Intermediary mappings bundled for 1.20.1, 1.21.1 and 1.21.11 (CC0). No code from Connector, Launchpad (GPL-3.0), Adapter, FFAPI or Forgified Fabric Loader.
