# Fabricated — Architecture (Phase 1 proposal)

> Status: **approved and implemented in 0.1.0**, with one exception: the Fabric API plan in §6.2. Only some
> of the modules marked Native there are native so far (the current list is in
> [`COMPATIBILITY.md`](COMPATIBILITY.md), with the measured state for each version); the rest are
> pass-through until implemented. Facts referenced here are verified in [`RESEARCH.md`](RESEARCH.md).
> Items marked **[verify-in-build]** were confirmed in loader source code. They still get an automated test in Phase 2 before being relied on.

## 0. Goals and non-goals

**Goals**
* Load as many Fabric mods and libraries as possible on NeoForge **1.21.11, 26.1.2, 26.2 and 26.3** from one codebase.
* Be lightweight: no runtime dependencies beyond what NeoForge ships, near-zero work on unchanged launches, no idle per-tick cost.
* Be robust: an isolatable failure never takes the game down, and every failure is attributed to a mod and explained.
* Be safe: mod jars are untrusted input, nothing runs during translation, nothing touches the network, no telemetry, and original jars are never modified.

**Non-goals** (see RESEARCH §4): rendering-engine replacements (Sodium/Iris), mods depending on Fabric Loader *internals*, heuristic auto-rewriting of mixins, MC versions outside the range, and running ≤1.21.11 Fabric builds on 26.x.

---

## 1. The pipeline at a glance

```
 mods/*.jar ──► [1] FabricJarReader (IModFileReader)
                   │  claims jars that contain fabric.mod.json, before FML rejects them
                   ▼
              [2] SafeArchive: validate zip (zip-slip, bombs, limits), SHA-256
                   ▼
              [3] MetadataTranslator: fabric.mod.json → FabricModDescriptor
                   │  (ids, versions, deps, entrypoints, mixins, AW/CT, nested jars)
                   ▼
              [4] Resolver (IDependencyLocator, runs once over all Fabric candidates)
                   │  - extract & dedupe nested jars (content-addressed)
                   │  - apply user config (disable, skip mixin, aliases)
                   │  - resolve Fabric dependency graph; drop unsatisfiable mods + dependents
                   │  - Fabric API module plan: native / pass-through / unsupported
                   ▼
              [5] Translator (per mod, cached, deterministic, parallel)
                   │  a. Remap         (1.21.11: intermediary→Mojang; 26.x: identity)
                   │  b. Mixin prep    (refmap remap, config filtering, behaviorVersion)
                   │  c. Pre-flight    (mixin target/injection-point verification vs game bytecode)
                   │  d. AW/CT → AT    (+ interface-injection table)
                   │  e. Overlay       (generated META-INF/neoforge.mods.toml, AT, manifest)
                   ▼
              [6] Package guard: JPMS split-package detection & resolution
                   ▼
              [7] Hand to FML: CompositeJarContents(original-or-remapped jar, overlay)
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
├── core/                    Pure Java 21. No Minecraft, no FML. ~all logic that can be unit tested.
│   ├── archive/             SafeArchive, limits, hashing, deterministic jar writer
│   ├── metadata/            fabric.mod.json model + parser, SemVer & version predicates, id normalisation
│   ├── resolve/             dependency graph, nested-jar dedupe, Fabric API module plan
│   ├── remap/               Tiny v1 + ProGuard parsers, mapping composition, ASM remapper,
│   │                        refmap/string-constant remapping, compact mapping cache
│   ├── accesswidener/       AW v1/v2 + classTweaker v1 parser, AT writer, interface-injection table
│   ├── mixin/               mixin-config model, refmap model, pre-flight verifier
│   ├── cache/               content-addressed, integrity-checked, sandboxed cache
│   ├── config/              fabricated.json model + validation
│   └── report/              report model + text renderer
├── fabric-loader-api/       Binary-compatible `net.fabricmc.loader.api.*` and `net.fabricmc.api.*`
│                            types (Apache-2.0, from Fabric Loader; NOTICE attribution)
├── loader/                  FML-facing glue, compiled once against FML 10.0.36 SPI (identical through 12.x)
│                            FabricJarReader, FabricDependencyLocator, FabricLanguageLoader,
│                            FabricModContainer, FabricLoaderImpl, ClassProcessors (interface injection)
├── bridge-common/           MC-independent parts of Fabric API bridges (Event<T> impl, buffers)
├── mc/
│   ├── shared-26/           Source dir shared by all 26.x targets (added as extra srcDir)
│   ├── 1.21.11/             MC-facing bridges + VersionAdapter for 1.21.11 (Java 21)
│   ├── 26.1.2/              ... for 26.1.2 (Java 25)
│   ├── 26.2/                ... for 26.2   (Java 25)
│   └── 26.3/                ... for 26.3   (Java 25, NeoForge beta)
├── testmods/                Tiny Fabric test mods built in-repo (entrypoints, mixin, AW, networking)
└── docs/                    RESEARCH, ARCHITECTURE, COMPATIBILITY, CONTRIBUTING
```

* **Output:** one jar per MC version, `fabricated-<version>+mc<mc>.jar`. `core`, `loader` and `bridge-common` are shaded in, all under the Fabricated-owned package `dev.fabricated.*`, so they can't clash with anything. The `fabric-loader-api` types keep their original `net.fabricmc.*` package names because Fabric mods link against them.
* **No preprocessor** (no Stonecutter/Manifold). Version differences live in each `mc/<ver>` directory; code shared by every version lives once in `mc/common`, by all 26.x versions in `mc/common26`, and by 26.1.2 and 26.2 in `mc/common26early`.
* `core` targets Java 21 bytecode so the *same* compiled classes run on every line.

---

## 3. Mod discovery and metadata translation

### 3.1 Claiming Fabric jars
`FabricJarReader implements IModFileReader` (ServiceLoader-registered) claims any `JarContents` containing `fabric.mod.json` and **no** `META-INF/neoforge.mods.toml`, which leaves multi-loader jars to NeoForge. It returns a lightweight *candidate* mod file. Because FML runs readers before its `IncompatibleModReason.FABRIC` check, the jar is no longer "broken". `FabricDependencyLocator implements IDependencyLocator` then processes all candidates together, since resolution needs the global view. The same discovery pattern is used by prior art; this implementation is clean-room.

### 3.2 `fabric.mod.json` → descriptor
* `schemaVersion` 1 is fully supported. Schema 0 (pre-2019) is rejected with a clear message.
* **Strict but forgiving parsing:** unknown keys are ignored. Malformed *required* keys reject only that mod. Files are capped at 1 MiB.

| Fabric field | NeoForge / Fabricated translation |
|---|---|
| `id` (`^[a-z][a-z0-9-_]{1,63}$`) | NeoForge id = `-` → `_` (NeoForge allows `^[a-z][a-z0-9_]{1,63}$`). The original Fabric id is kept as an **alias** so `FabricLoader.isModLoaded("cloth-config")` keeps working. An id that's invalid after normalisation is rejected with a message. |
| `version` (SemVer or free-form) | `version` string unchanged. Comparison uses Fabric SemVer rules in `core`. |
| `name`, `description`, `authors`, `contributors`, `contact`, `license`, `icon` | `displayName`, `description`, `authors`, `credits`, `displayURL`/`issueTrackerURL`, `license`, `logoFile` |
| `environment` (`*`/`client`/`server`) | The mod is skipped (with an INFO note) when the dist doesn't match |
| `depends` / `recommends` / `suggests` / `conflicts` / `breaks` | Resolved **by Fabricated** (§3.4). NeoForge dependencies are also emitted for ordering (`ordering="AFTER"`, type `optional`) so FML's sort respects them without FML being able to abort the whole game. |
| `provides` | Extra aliases (e.g. a library provides `fabric-api-base`) |
| `entrypoints` | Stored for the language loader (§4) |
| `mixins` (string or `{config, environment}`) | `[[mixins]]` entries in the generated toml, filtered by environment at generation time, since the dist is known at discovery |
| `accessWidener` / `classTweaker` | Converted to `META-INF/accesstransformer.cfg` in the overlay (§7) |
| `jars` | Nested jars, extracted & deduped (§3.3) |
| `languageAdapters` | Registered in Fabricated's adapter registry (e.g. Kotlin's adapter) |
| `custom` | Exposed through `ModMetadata.getCustomValue`. Otherwise ignored. |

The generated overlay `neoforge.mods.toml` uses `modLoader="fabricated"`, so FML hands these mods to `FabricLanguageLoader`, and `loaderVersion` is pinned to Fabricated's own range.

### 3.3 Nested jars (Fabric jar-in-jar)
* Extracted **from the validated archive only**, to `<gameDir>/.fabricated/cache/nested/<sha256>.jar` (content-addressed, so it's written once).
* Max nesting depth is 3. Every level gets the same archive checks.
* Nested jars with `fabric.mod.json` become Fabric candidates. For duplicates by id, **highest version wins**, as in Fabric Loader. Fabric API modules nested inside mods are routed to the module plan (§6).
* Nested plain libraries are added as FML libraries (`FMLModType: LIBRARY` in the overlay manifest).

### 3.4 Dependency resolution (why Fabricated, not FML, decides)
FML treats an unmet *required* dependency as a fatal loading error, which would let one Fabric mod block the game. Fabricated therefore resolves the Fabric graph itself first:
1. Build the graph over Fabric candidates, NeoForge mods (exposed under their ids), built-ins (`minecraft`, `java`, `fabricloader`, `fabric-api`, every `fabric-*` module id, `neoforge`), and aliases.
2. Version predicates use Fabric semantics (`>=1.2 <2`, `~1.2`, `^1.2`, `1.2.x`, `*`, arrays as OR).
3. A mod whose `depends`/`breaks` is unsatisfied is **disabled together with its dependents**, transitively. Each one gets a WARNING `ModLoadingIssue`, e.g. *"`mymod` needs `cloth-config >=15` (found 14.0.2). Install a newer Cloth Config."* `conflicts` produces a warning only, as in Fabric.
4. `fabricloader` is satisfied by Fabricated's declared compatible Fabric Loader version (configurable). `minecraft` is matched against the running version.
5. **Native NeoForge mod vs. Fabric mod with the same id:** the NeoForge build is preferred by default and the Fabric jar is skipped with an INFO note. Config can flip this per mod.
6. `strictDependencies=true` in config passes required deps straight to FML instead, for users who prefer a hard stop.

---

## 4. Entrypoint bridging

### 4.1 Lifecycle mapping (verified against NeoForge `CommonModLoader`)
NeoForge's sequence is: mod construction, then `unfreezeData()`, then a `RegisterEvent` per registry in mod order, then `freezeData()`, then `FMLCommonSetupEvent`, then the sided setup event, then IMC, then `FMLLoadCompleteEvent`. Vanilla registries are **frozen during mod construction**. Fabric mods call `Registry.register(...)` directly in `onInitialize`, so they must run while the registries are unfrozen.

| Fabric entrypoint | When Fabricated runs it | Ordering |
|---|---|---|
| `preLaunch` | **Not run as Fabric does** (Fabric runs it before game classes load). Fabricated runs it at language-loader init, before any MC class is touched by Fabricated, and documents the difference. Mods doing classloader tricks there are unsupported. | Dependency order |
| `main` (`ModInitializer`) | Once, on the **first `RegisterEvent` after the attribute registry's** (which NeoForge posts first): registries are unfrozen, NeoForge's own attributes (part of every living entity's attribute builder) are registered, and NeoForge mods constructed before it are visible | Fabric dependency order (topological, ties broken by id, so it's deterministic) |
| `client` (`ClientModInitializer`) | Immediately after *all* `main` entrypoints, on the client dist (same as Fabric: all main, then all client) | Same |
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
   * merged methods, `@Accessor`s and `@Invoker`s don't collide with a method the target already declares (NeoForge adds some, such as `hashCode` or public getters);
   * a `NEW` point with a constructor descriptor matches that constructor only, and targets in library classes NeoForge loads without mixin support (DataFixerUpper, Brigadier, the JDK) fail.

   Outcome per mixin:
   * `required` or default-require mixins that **would fail** cause the **whole mod to be disabled before launch**, with a report like *"`examplemod`: mixin `ExampleMixin` targets `ItemStack#getTooltipLines` → `INVOKE List.add`, which NeoForge removed. Ask the author for a NeoForge build, or set `skipMixins` if this mixin is cosmetic."*
   * Non-required mixins that would fail produce a warning only.
5. **User overrides:** `skipMixins` drops entries from the cached copy of the mixin config JSON, never the original.
6. **Runtime attribution:** a mixin failure that slips past pre-flight is enriched through `ICrashReportHeader`/`CrashReportCallables` with the owning Fabric mod id and a suggested fix.
7. **No heuristic rewriting.** An optional, *data-driven* retarget table (`mc/<ver>/retargets.json`: exact `from` → `to` member pairs) is supported, so known NeoForge-patched methods can be redirected explicitly and reviewably. It starts empty and grows from compatibility testing.

---

## 6. Fabric API strategy (hybrid)

### 6.1 Layers
1. **Fabric Loader API** (`fabric-loader-api` + `loader`): `FabricLoader` (mods, entrypoints, `getConfigDir`/`getGameDir`, `getEnvironmentType`, `isDevelopmentEnvironment`, `getObjectShare`, `getLaunchArguments`, `getGameInstance`, `getMappingResolver`), `ModContainer` / `ModMetadata` for **all** mods (NeoForge mods appear too, so `isModLoaded` sees them), `Version` / `VersionPredicate`, `@Environment` / `EnvType`, and `LanguageAdapter`. `MappingResolver`: identity with runtime namespace `official` on 26.x (matching Fabric Loader's behaviour on unobfuscated MC). On 1.21.11 it maps `intermediary`→runtime and reports `named`, so mods that check it call `map*` and get Mojang names.
2. **Fabric API**, decided per module:
   * **Native**: Fabricated provides the module's public API itself, implemented on NeoForge events and systems. The upstream module jar is **not** loaded.
   * **Pass-through**: the module from the player's **official Fabric API jar** (Fabricated does not redistribute it) goes through the normal pipeline like any Fabric mod. Pre-flight decides whether it can load.
   * **Unsupported**: the module isn't loaded. Mods that `depends` on it are disabled with a clear message, and mods that only *use* it optionally keep working.
3. **Satisfying `fabric-api` dependencies:** the umbrella id `fabric-api` and each module id are "provided" when native, or pass-through and passing pre-flight.

### 6.2 Initial module classification (first implementation pass)

| Module (26.x name / 1.21.11 name) | Plan | Bridged onto |
|---|---|---|
| `fabric-api-base` | **Native** | Event<T> implementation, `PhaseSorting` |
| `fabric-lifecycle-events-v1` | **Native** | `ServerStarting/Started/Stopping/StoppedEvent`, `ServerTickEvent`, `LevelTickEvent`, `ClientTickEvent`, chunk/entity load events, `AddServerReloadListenersEvent` |
| `fabric-command-api-v2` | **Native** | `RegisterCommandsEvent`, `RegisterClientCommandsEvent` |
| `fabric-networking-api-v1` | **Native** | `RegisterPayloadHandlersEvent` (+ NeoForge's connection/login events). Fabric payloads become NeoForge payload registrations, so the NeoForge handshake stays happy. |
| `fabric-registry-sync-v0` | **Native (no-op/compat)** | NeoForge already syncs registries. Its API surface is provided, but sync is left to NeoForge. |
| `fabric-resource-loader-v1` (+ v0 deprecated) | **Native** | `AddServerReloadListenersEvent` / `AddClientReloadListenersEvent`, `AddPackFindersEvent`, built-in mod resource packs |
| `fabric-resource-conditions-api-v1` | **Native** | Fabric's `fabric:load_conditions` is evaluated by a NeoForge condition codec |
| `fabric-convention-tags-v2` | **Native** | Both ecosystems use the `c:` namespace, so this is constants plus datagen helpers |
| `fabric-creative-tab-api-v1` / `fabric-item-group-api-v1` | **Native** | `BuildCreativeModeTabContentsEvent` |
| `fabric-key-mapping-api-v1` / `fabric-key-binding-api-v1` | **Native** | `RegisterKeyMappingsEvent` |
| `fabric-screen-api-v1` | **Native** | `ScreenEvent.*` |
| `fabric-rendering-v1` (subset: entity/block-entity renderers, layers, color providers, HUD, world render events, tooltip components) | **Native (partial)** | `EntityRenderersEvent`, `RegisterColorHandlersEvent`, `RegisterGuiLayersEvent`, `RenderLevelStageEvent`, … Unbridged parts are listed in COMPATIBILITY.md. |
| `fabric-particles-v1` | **Native** | `RegisterParticleProvidersEvent` |
| `fabric-menu-api-v1` / `fabric-screen-handler-api-v1` | **Native** | NeoForge `IMenuTypeExtension` / `RegisterMenuScreensEvent` |
| `fabric-events-interaction-v0`, `fabric-entity-events-v1`, `fabric-message-api-v1`, `fabric-loot-api-v3` | **Native** | `PlayerInteractEvent`, `UseItemOnBlockEvent`, `LivingDeathEvent`, `ServerChatEvent`, `LootTableLoadEvent`, … |
| `fabric-data-attachment-api-v1` | **Native** | NeoForge data attachments |
| `fabric-api-lookup-api-v1`, `fabric-block-api-v1`, `fabric-block-getter-api-v2`/`block-view-api-v2`, `fabric-item-api-v1`, `fabric-object-builder-api-v1`, `fabric-tag-api-v1`, `fabric-serialization-api-v1`, `fabric-sound-api-v1`, `fabric-transfer-api-v1`, `fabric-recipe-api-v1`, `fabric-content-registries-v0`, `fabric-dimensions-v1`, `fabric-game-rule-api-v1`, `fabric-biome-api-v1`, `fabric-model-loading-api-v1`, `fabric-rendering-fluids-v1`, `fabric-crash-report-info-v1`, `fabric-transitive-access-wideners-v1`, `fabric-advancement-api-v1`, `fabric-permission-api-v1` | **Pass-through** (promoted to Native when pre-flight or testing shows a conflict) | upstream code |
| `fabric-renderer-api-v1`, `fabric-renderer-indigo` | **Unsupported** | would replace NeoForge's model pipeline |
| `fabric-data-generation-api-v1`, `fabric-gametest-api-v1`, `fabric-client-gametest-api-v1`, `fabric-debug-api-v1` | **Unsupported** | development-time only |

This table is data (`mc/<ver>/fabric-api-plan.json`), not code, so it can change per MC version without code changes.

**Scope honesty:** Phase 2 delivers the full core plus the native modules marked above. Their quality varies: lifecycle, commands, networking, resource loader, creative tabs, key mappings and screen events are complete. Rendering is a documented subset. Every gap ends up in `docs/COMPATIBILITY.md`.

---

## 7. Remapping (1.21.11) and determinism

### 7.1 Mapping construction (offline, once per MC version)
1. `intermediary-1.21.11.tiny` (bundled, CC0) gives *official → intermediary*.
2. Mojang mappings (`client-1.21.11-mappings.txt` on the client, `server-…` on the server) come from the **local** libraries directory the NeoForge installer populated. For the dev environment, an explicit path can be set in config or the system property `fabricated.mojmapsPath`. If the file is missing, *all* 1.21.11 Fabric mods are disabled with one clear message explaining how to repair the install. **Nothing is downloaded.**
3. Compose them to *intermediary → Mojang* for classes, fields (name and descriptor) and methods (name and descriptor). Intermediary method names are hierarchy-consistent, so flat lookup is correct. Duplicated intermediary names are handled descriptor-qualified.
4. Serialise to a compact binary (`mappings-1.21.11-<sha256(inputs)>.bin`, sorted arrays) for fast reload. The SHA-256 covers both inputs.

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

* Headers are validated: `accessWidener v1|v2 <ns>` and `classTweaker v1 <ns>`. The namespace must be `intermediary` (1.21.11) or `official` (26.x), otherwise the mod is rejected with an explanation.
* Entries targeting members that don't exist are dropped with a warning (Fabric Loader would crash).
* The AT file is written into the overlay at `META-INF/accesstransformer.cfg`, which FML picks up by default.

---

## 9. Per-version abstraction

`VersionAdapter` (ServiceLoader, exactly one per `mc/<ver>` jar):

```java
public interface VersionAdapter {
    String minecraftVersion();                  // "1.21.11", "26.2", ...
    MappingProvider mappings();                 // Identity or IntermediaryToMojang
    String fabricMixinNamespace();              // "intermediary" | "official"
    FabricApiPlan fabricApiPlan();              // loaded from fabric-api-plan.json
    RetargetTable mixinRetargets();             // loaded from retargets.json
    void registerNativeBridges(BridgeContext ctx); // subscribes NeoForge event bridges
}
```

* Everything MC-agnostic stays in `core`/`loader`. Each `mc/<ver>` contains only the bridges, which reference MC/NeoForge classes, and the two data files.
* **Adding a version** (summarised from `CONTRIBUTING.md`): copy the nearest `mc/<ver>` directory, set `minecraft_version`/`neo_version` in its `gradle.properties`, add it to `settings.gradle` and the CI matrix, fix compile errors (usually renamed MC/NeoForge classes), adjust `fabric-api-plan.json`, and run the test mods. No core changes are expected.

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
| Mixin pre-flight | required injection point absent | Mod disabled, names mixin, target and point | ✅ |
| Split package | two mods share `com.foo` | Identical duplicate classes are filtered out. Otherwise the lower-priority mod is disabled. | ✅ |
| Entrypoint exception | `onInitialize` throws | Default `onEntrypointFailure="error"`: NeoForge's **error screen** lists mod, entrypoint, cause and fix (*"Disable `x` in config/fabricated.json or update it"*). With `"disable"`: the mod is marked failed, its remaining entrypoints are skipped, and loading continues with a warning. Partially registered content may remain, which the report states. | ⚠️ configurable |
| Mixin apply at runtime | injector fails in a way pre-flight couldn't see | Crash report enriched with the Fabric mod id and fix | ❌ (JVM can't unload) |

* All issues go through `ModLoadingIssue` (FML's own screen: warnings are dismissible, errors block with explanation) **and** into `logs/fabric-bridge-report.txt`.
* **Rule:** anything detectable before mod code runs is isolated. After mod code runs, Fabricated explains the failure instead of crashing raw, and never claims an isolation it can't guarantee.

---

## 11. Caching

* **Root:** `<gameDir>/.fabricated/cache/v<schema>/` (resolved, normalised, and checked to be inside the game dir; symlinks rejected).
* **Key:** `sha256(input jar) + mcVersion + fabricatedVersion + translationSchema + mappingHash + configFingerprint(mod)`. Mod id and version go into the file name for readability; the hash is what makes the entry unique.
* **Entry:** `<key>.jar` (remapped jar, 1.21.11 only, since 26.x uses the original jar + overlay with no copy), `<key>.overlay/` (generated toml/AT/mixin configs), `<key>.json` (input hash, **output hash**, sizes, pre-flight results, timings).
* **Integrity:** on each launch the output SHA-256 is verified before use. A mismatch means the entry is deleted and rebuilt, and the report notes it. Writes are atomic (temp file + `ATOMIC_MOVE`).
* **Skip work:** unchanged inputs mean hash-only work, and input hashes are memoised by (path, size, mtime) in `index.json`, re-hashed when any changes.
* **GC:** entries unused for N launches (default 5) are deleted.

## 12. Configuration (`config/fabricated.json`)

Read during discovery, before NeoForge's config system exists, so it's plain JSON parsed with Gson (already shipped by NeoForge). The file is created with defaults and comments in a sibling `fabricated.json.md`.

```json
{
  "schema": 1,
  "onEntrypointFailure": "error",
  "strictDependencies": false,
  "preferNativeNeoForgeMods": true,
  "cache": { "enabled": true, "verifyIntegrity": true, "maxUnusedLaunches": 5 },
  "limits": { "maxJarBytes": 268435456, "maxEntryBytes": 67108864, "maxTotalUncompressedBytes": 536870912,
              "maxEntries": 65536, "maxCompressionRatio": 100, "maxNestingDepth": 3 },
  "mods": {
    "examplemod": { "disabled": false, "skipMixins": ["examplemod.mixins.json:client.FooMixin"],
                    "skipEntrypoints": ["client"], "aliases": ["example"], "preferFabric": false }
  },
  "mojangMappingsPath": null
}
```

Unknown keys produce a warning. Invalid values fall back to defaults with a warning, and the config never crashes the game.

## 13. Diagnostics

* `logs/fabric-bridge-report.txt`, written on every launch and on demand. It contains: environment (MC, NeoForge, FML, Java, Fabricated); per-mod status (loaded/disabled + reason) with source jar, hash, cache hit/miss and timings; the Fabric API module plan; mixin pre-flight results; AW→AT conversions; split-package decisions; config overrides applied; and warnings.
* `/fabricated report` regenerates the report and prints a summary. It's a server command that needs permission level 2. A client-side command with the same name works for any player on their own client.
* Logging uses SLF4J with the marker `FABRICATED`.

## 14. Security model

| Threat | Mitigation |
|---|---|
| Path traversal / zip-slip | Entry names normalised. Reject absolute, `..`, backslash, drive letters, NUL, and duplicates. Extraction only into cache dirs, verified with `toRealPath` prefix checks. |
| Decompression bombs | Caps on jar size, entry size, total uncompressed size, entry count and compression ratio. Sizes are counted while streaming; declared sizes aren't trusted. |
| Malformed JSON / huge metadata | 1 MiB cap and strict types. Parse errors are per-mod. |
| Code execution during translation | Bytes-only processing (ASM `ClassReader`). No `Class.forName`, no service loading from mod jars, no scripting. A test asserts no mod class is loaded during translation. |
| Cache tampering | Output SHA-256 verified before use. Cache confined to the game dir, symlinks refused. |
| Network | Fabricated never opens sockets/URLs. A unit test scans compiled Fabricated classes for `java/net/(Socket|URL|http)` usage. There's no updater, downloader or telemetry. |
| Original jar modification | Inputs opened read-only and never written. A test hashes inputs before and after. |

## 15. Performance targets

* Warm launch overhead for 100 Fabric mods: **< 300 ms** (hash check + overlay reuse).
* Cold translation on 26.x: overlay generation only, **< 20 ms per mod**. On 1.21.11: remap ≈ jar size / throughput, parallelised over cores.
* Mapping table for 1.21.11 is loaded lazily and kept only if a mod calls `MappingResolver`.
* **No tick listeners** are registered unless a Fabric listener exists for that event: Fabric `Event` invokers register their NeoForge subscription lazily on first `register(...)`.

## 16. Testing and CI

* **Unit (core, runs anywhere):** metadata parsing/translation, version predicates, id normalisation, dependency resolution, archive safety (crafted malicious zips generated in-test), Tiny/ProGuard parsing and composition, remapping (incl. determinism), refmap rewriting, AW/CT→AT, pre-flight verifier (synthetic target/mixin classes generated with ASM), cache integrity, config validation, report rendering. **No third-party binaries are committed.** Fixtures are generated in tests.
* **Integration (per MC version, MDG):** `gameTestServer` run with the in-repo `testmods` (entrypoints, registry, mixin, AW, command, networking, nested jar, failing-mod isolation), plus a client smoke run under Xvfb that boots to the title screen and exits.
* **Real-mod smoke (CI only):** a pinned list of Fabric mods per MC version, fetched by **CI** (never by Fabricated) with SHA-256 pins. Results feed `docs/COMPATIBILITY.md`.
* **GitHub Actions:** matrix `{1.21.11: JDK 21, 26.1.2: JDK 25, 26.2: JDK 25, 26.3: JDK 25}`. Steps: build, unit tests, game tests, smoke. 26.3 is marked `continue-on-error` while NeoForge 26.3 is beta.

## 17. Licensing

MIT for Fabricated. `NOTICE` covers the Fabric Loader API types (Apache-2.0) and Intermediary 1.21.11 (CC0). No code from Connector, Launchpad (GPL-3.0), Adapter, FFAPI or Forgified Fabric Loader.
