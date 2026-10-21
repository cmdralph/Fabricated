# Fabricated beyond modern NeoForge: the 1.20.1 and Forge evaluation

Status: **architectural evaluation**, written 2026-10-21. Nothing here is implemented. `main` stays the active
branch: NeoForge for Minecraft 1.21.11, 26.1.2, 26.2 and 26.3, all four on stable NeoForge lines. This document
says what of the engine carries over to Minecraft 1.20.1 (branch `1.20.1-legacy`) and to Minecraft Forge (a
future `forge` branch), what the build needs, what must change on `main` first, and in which order the work goes.
Facts about other projects cite their repository, ref and file; what could not be checked is marked UNVERIFIED.

## 1. Branch model

| Branch | Platform | Phase | Rule |
|---|---|---|---|
| `main` | NeoForge 1.21.11, 26.1.2, 26.2, 26.3 | active | Every fix and feature lands here first. |
| `1.20.1-legacy` | Minecraft 1.20.1 on Forge 47.x (NeoForge 47.1 best-effort, §3.1) | architectural evaluation | Takes `main` by merge, never by cherry-pick; adds only its own files. |
| `forge` (future) | Minecraft Forge for the versions `main` covers (Forge 61 for 1.21.11, 64 to 66 for 26.1.2 to 26.3) | not started | Same rules as `1.20.1-legacy`. |

How code flows. Shared modules (`core`, `api`, `fabric-loader-api`, the Gradle wrapper, `ci/*.py`) are only ever
changed on `main`; a branch picks a change up by merging `main`. A branch adds its own directories (its loader
adapter, its `mc/<target>` projects, its CI target list) and never edits or deletes a file `main` owns, or every
merge conflicts. GitHub's merges ignore `merge=ours` attributes, so ownership comes from where files live, not from
merge settings. Today `1.20.1-legacy` is `main` as of PR #64 (`b5911bc`), with nothing of its own.

## 2. What of the engine is portable

Measured on `main` at PR #65 (`01c2fdf`): `core` 16,242 lines, `fabric-loader-api` 3,608, `api` 184, `loader`
1,458. About **80 % (17,100 of 21,500 lines) assumes neither NeoForge nor a Minecraft version**: the archive
sandbox, the translation cache, JSON, metadata parsing, dependency resolution, the Fabric Loader API, the linkage
check, the remapping machinery and nearly all of mixin pre-flight work on bytes and data alone.

| Area | Portable? | What binds it |
|---|---|---|
| `archive` (SafeArchive, limits), `cache`, `json`, `config`, `report`, `core/api`, `runtime`, `bytecode`, `metadata` | yes | `ModIds` already reserves `forge`. |
| `resolve` (FabricApiPlan, DependencyResolver, CompatPlan) | yes, data-driven | |
| `resolve` (MultiLoaderJars, PlatformLibraries, NestedNeoForgeJars, CandidateScanner) | no | `neoforge.mods.toml`, `net/neoforged/fml/common/Mod`, `javafml`, platform package prefixes; `CandidateScanner` drops a mod's nested MixinExtras because NeoForge provides one. |
| `mixin` (pre-flight, repairs, PatchAlignment, HandlerBridges) | about 96 % | `LOADER_HOOKS` (`ServerModLoader.load`), `IExtensibleEnum`, `net/neoforged/` hook heuristics; Mixin behaviour levels are Fabric-fork semantics (`MixinCompat`); MixinExtras features are assumed present. |
| `remap` (MappingTable, JarRemapper, IntermediaryRemapper) | yes | Driven by the mapping table. |
| `remap` (MappingComposer, OfficialClasses) | per version | Tiny v1 + ProGuard → Mojang names only. |
| `translate` (LibraryBridges, LinkageAnalysis, DefaultBridge) | yes | |
| `translate` (PackageGuard, ResourceLookups, Interop) | FML family | JPMS mod modules, which Forge's FML also uses (UNVERIFIED for 1.20.1's SecureJarHandler details). |
| `translate` (ModsTomlWriter, EnumExtensions, EnumValuesMixins, PlatformRenames, MixinCompat, about 270 lines of ModTranslator) | no | The `neoforge.mods.toml` dialect (`[[mixins]]` with `behaviorVersion`, `[[accessTransformers]]`, `enumExtensions`, `type="optional"`), NeoForge's enum extension format, NeoForge's patched names, `neoforge:conditions`, `neoforge/biome_modifier` paths. |
| `loader` | no (977 of 1,458 lines) | FML 10+ SPI: `IModFileCandidateLocator`, `IDependencyLocator`, `IModLanguageLoader`, `ClassProcessor`, `JarContents`; NeoForge's installer layout. |

**Java level.** `core`, `api` and `fabric-loader-api` compile with `release = 21`, which 1.20.1's Java 17 can't load.
A trial compile at `--release 17` finds only two kinds of blocker: 66 calls of `List.getFirst/getLast/removeFirst/
addFirst` (Java 21's sequenced collections; 3 of them in `fabric-loader-api`) and 8 `switch` statements with type
patterns (53 labels, in `PatchAlignment`, `InjectionPoints`, `LinkageChecker`, `EnumExtensions`, `LibraryBridges`,
`EnumValuesMixins` and `LinkageAnalysis`). No record patterns, `when` guards or newer APIs. The tests use about 160
more `getFirst`/`getLast` calls. Generated classes already target Java 17.

**Where platform facts already come in as data.** `version.properties` (Minecraft version, namespace, Mixin
behaviour floor and fallback, build digest), the per-version `fabric-api-plan.json`, `compat-plan.json`,
`interop.json`, the bundled `intermediary.tiny`, and `Pipeline.Input`, through which the loader hands core the game's
classes, the platform's packages, the installed mods, the mapping supplier and vanilla's jar. That is the seam the
other platforms build on.

## 3. Minecraft 1.20.1

### 3.1 Platform facts

| Fact | Evidence |
|---|---|
| Forge for 1.20.1 is maintained: branch `1.20.1`, tags 47.0 to 47.4, commits into 2026-10. | MinecraftForge/MinecraftForge `1.20.1` (`d13dd32`, about 47.4.28) |
| NeoForge's 1.20.1 build is a fork of Forge 47.1: same `forge` artifact and mod id, `net.minecraftforge` packages, its FML from FancyModLoader 47.2.x; last commit 2024-05-28. | neoforged/NeoForge `1.20.1` (`a3317fc`), its `build.gradle`; neoforged/FancyModLoader `1.20.1` |
| Discovery SPI is forgespi 7.0.1: `IModLocator.scanMods()` and `IDependencyLocator.scanMods(loaded)` return lists of `ModFile`s built by `ModFileFactory`; languages come from `IModLanguageProvider`, whose file visitor registers loaders with `ModFileScanData.addLanguageLoader`. No `IModFileCandidateLocator`. | MinecraftForge/forgespi (`d2b38bf`), `forgespi/locating`, `forgespi/language` |
| A `mods/` jar providing a locator joins ModLauncher's service layer and is then left out of normal mod loading; language providers load from the plugin layer and need an `Implementation-Version`. | Forge `1.20.1`: `ModDirTransformerDiscoverer.java:28-33`, `ModsFolderLocator.java:49`, `LanguageLoadingProvider.java:79,97` |
| Metadata is `META-INF/mods.toml`; dependencies use `mandatory`, not `type`; no `[[mixins]]` (Mixin reads the jar manifest's `MixinConfigs`); `clientSideOnly` and an `accessTransformers` list only from 47.3.25 and 47.4.20. | Forge `ModInfo.java:215`, `ModFileInfo.java`; SpongePowered/Mixin `releases/0.8.5` `MixinPlatformAgentDefault.java:43` |
| In production, classes have Mojang names and members SRG names (`f_90987_`, `m_…_`); the launch handlers name the domain `srg`. | Forge `CommonClientLaunchHandler.java:20`, its `accesstransformer.cfg`, `FMLLoader.java:163` |
| The installer leaves MCPConfig in the libraries (`de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412`, a merged obfuscated→SRG mappings file among them), keyed by obfuscated names as Intermediary is. | Forge `build.gradle:1383-1595` (installer processors). That every launcher keeps the files is UNVERIFIED. |
| Mixin is upstream 0.8.5, not Fabric's fork; MixinExtras is not bundled (mods nest `mixinextras-forge`). Java 17. | Forge `build.gradle:65,167,308` |
| No JSON enum extensions: only `IExtensibleEnum` with static `create` factories on enums Forge patched. No `ClassProcessor` equivalent: ModLauncher's `ITransformationService` (from `mods/`, explicit targets) or JS coremods. | Forge `IExtensibleEnum.java`, `RuntimeEnumExtender.java`; ModLauncher 10.0.9 `ITransformer.java` |
| The old capability system (`LazyOptional`, `AttachCapabilitiesEvent`), `SimpleChannel` networking, `forge:conditions` read by serializers rather than codecs; `RegisterEvent` exists. | Forge `1.20.1` sources |
| Fabric API's last 1.20.1 release is 0.92.12+1.20.1 (Fabric Loader 0.16.10). The renderer API is the old one (`RenderMaterial`, `MaterialFinder`, `RenderContext`). Fabric mods for 1.20.1 were built against Fabric's Mixin 0.12.5 to 0.15.5, and from Fabric Loader 0.15 on with MixinExtras 0.3 to 0.4. | FabricMC/fabric `0.92.12+1.20.1`; FabricMC/fabric-loader tags, `gradle.properties` |

Prior art: Sinytra Connector (MIT) targets Forge 47.4 on 1.20.1. It remaps Fabric jars from Intermediary to SRG at
load time, nests MixinExtras, and relies on a separate Forgified Fabric API. Fabricated takes nothing from it (the
project's rule); it only shows the platform can carry this design.

**Target choice.** MinecraftForge 47.4.x is the maintained line and the one the 1.20.1 ecosystem uses; NeoForge 47.1
is the same code base two years older. The plan targets Forge 47.4 and treats NeoForge 47.1 as best-effort (no
`clientSideOnly`, no `accessTransformers` list). Owner decision D1.

### 3.2 What each subsystem needs on 1.20.1

| Subsystem | Carries over | Needs |
|---|---|---|
| Archive sandbox, cache, JSON, metadata, dependency resolution, Fabric Loader API, linkage check | as is | Java 17 class files. |
| Remapping | the machinery | A second target namespace: Mojang class names with SRG member names. Intermediary → SRG is Intermediary inverted, composed with MCPConfig's merged obfuscated → SRG file; `MappingResolver` reports `srg` as the runtime namespace. Access transformers come out in SRG member names. |
| Vanilla code for pre-flight (VANILLA-1, VANILLA-2) | `OfficialClasses`' design | The same remapper with MCPConfig's SRG names instead of ProGuard's. |
| Mixin pre-flight and repairs | the checker and every generic repair | Platform facts as data: no behaviour levels (upstream 0.8.5), Forge's patch names and hooks instead of NeoForge's, and a gate on what the running MixinExtras supports (pre-flight assumes MixinExtras today). Fabric mods built against Fabric's Mixin fork may use what upstream 0.8.5 lacks: an open question, answerable only with a corpus (risk R2). |
| MixinExtras | | Provided by Fabricated: kept when a Fabric mod nests it (today it is dropped because NeoForge has one), else bundled. |
| Mod descriptor | the overlay mechanism | A `mods.toml` writer in Forge's dialect: `mandatory`, `MixinConfigs` in the manifest, access transformers at `META-INF/accesstransformer.cfg`. |
| Enum extensions (ENUM-1 and later) | the analysis | A different application: NeoForge's JSON doesn't exist. Options are an `ITransformationService` adding the constants, or letting Mixin apply the enum mixins directly, as on Fabric. Whether Forge refuses enum mixins is UNVERIFIED. |
| Injected interfaces and default bridges (`FabricClassProcessor`, `DefaultBridgeProcessor`) | the class transforms | An `ITransformationService` whose transformers target the classes the mods' injection tables name (known after discovery, which ModLauncher calls `transformers()` after). |
| Discovery and language loading (`loader`) | the helpers (FabricatedInfo, FabricatedState, ProposedJars, ClassResources) | A new adapter on forgespi 7. The locator jar leaves normal mod loading, so Fabricated ships as a service jar with its language provider and in-game mod nested and served by the locator. The in-game mod is already a nested `game-mod.jar`; the language provider needs its own `LANGPROVIDER` jar. |
| Native Fabric API modules and bridges (`mc/*`) | the design, not the code | Re-vendored from Fabric API 0.92.12, bridged to Forge 47's events, capabilities (`LazyOptional`), `SimpleChannel` networking and serializer-based conditions. The renderer bridge targets the old renderer API. |
| Data packs | the stripping and translation logic | Forge's keys (`forge:conditions`, `forge/biome_modifier`) and 1.20.1's formats. |

## 4. Minecraft Forge (modern)

| Fact | Evidence |
|---|---|
| Forge is maintained for every version `main` targets: branches `1.21.11` (Forge 61, recommended build 61.2), `26.1.2` (64.1 RB), `26.2` (65.1 RB), `26.3` (66.1 RB, 2026-10-21). That the binaries are published is UNVERIFIED (Forge's maven is not reachable here). | MinecraftForge/MinecraftForge branches and tags |
| Discovery is forgespi 8.0: the same `IModLocator`/`IDependencyLocator` list shapes as 1.20.1, `ModFile`s built by `ModFileFactory` over `SecureJar`; `IModLanguageProvider` in the plugin layer. No `ClassProcessor`: `ITransformationService` from `mods/` (called after the mod scan); JS coremods are gone. | MinecraftForge/forgespi `8.0`; Forge `26.3` `ModDiscoverer.java`; ModLauncher `LaunchPluginHandler.java:38-43` |
| `META-INF/mods.toml` with `mandatory`; no `[[mixins]]` (manifest `MixinConfigs`), no `enumExtensions`; access transformers as a top-level list. | Forge `26.3` `ModFileParser.java:33`, `ModFile.identifyMods` |
| Mojang names at runtime since 1.20.6; upstream Mixin 0.8.7; MixinExtras 0.5.4 added by Forge when a mod declares mixin configs. | Forge `ForgeProdLaunchHandler.getNaming()` on `1.20.x` to `26.3`; `JarInJarDependencyLocator.java:74,236` |
| EventBus 7: each event has its own static bus (`X.BUS.addListener`), events are records or `MutableEvent`s, cancellation is a listener's return value, `Event.Result` gone. Every bridge Fabricated has is written against NeoForge's bus. | MinecraftForge/EventBus 7.0.x |
| Old capabilities, `ChannelBuilder` networking (with a payload channel), no data maps, `AddGuiOverlayLayersEvent` for HUD layers, `forge:condition` (single object), `IExtensibleEnum` only. | Forge `26.3` sources |
| Forge is LGPL-2.1 with an exception for mods using it through ordinary Java mechanisms; ModLauncher LGPL-3.0. Calling these APIs from a GPL-3.0 mod appears compatible (not legal advice). | Forge `LICENSE.txt` |

So a `forge` branch keeps `core` and the remapping as they are (the same names at runtime as on NeoForge), and
rewrites `loader` (forgespi 8, close to the 1.20.1 adapter) and every bridge in `mc/*` (EventBus 7, capabilities,
networking, HUD layers). The build tool for modern Forge is ForgeGradle, not ModDevGradle (which supports NeoForge
and, through `legacyforge`, Forge up to 1.20.1); its version and its Gradle 9.7 compatibility are UNVERIFIED.

## 5. Build and CI

**Today.** `settings.gradle` includes every `mc/<dir>` with a `build.gradle`; each `mc/<v>` applies
`net.neoforged.moddev` and the shared `mc/mc.gradle`, which picks shared source folders by version (`common`,
`common26`, `common26early`, `common26late`), builds the in-game mod as `game-mod.jar` and merges `loader`, `core`,
`api` and `fabric-loader-api` into `fabricatedJar`. Its `else` branches assume "not 26.x" means 1.21.11, and so do
CI's step conditions. CI's matrix, installers (`neoforge-<v>-installer.jar`), HeadlessMC launch names and
mc-runtime-test assets are NeoForge's.

**1.20.1 with ModDevGradle's `legacyforge` plugin.** MDG ships `net.neoforged.moddev.legacyforge` at the same
version as the main plugin (our 2.0.142 has it): "support for developing mods against MinecraftForge and Vanilla
Minecraft versions 1.17 up to 1.20.1", configured as `legacyForge { version = "1.20.1-47.3.0" }`, NeoForge 47.1
through `setNeoForgeVersion`. It reobfuscates the `jar` task to SRG (`reobfJar`; other jars through
`obfuscation.reobfuscate(...)`), and mixin refmaps come from `mixin { add sourceSets.main, '<name>.refmap.json' }`
with the `MixinConfigs` manifest attribute (neoforged/ModDevGradle `LEGACY.md`). Its own builds use Gradle 8.14; a
Gradle 9 fix landed in June 2025, so **legacyforge on our Gradle 9.7 is UNVERIFIED** (risk R1). A `mc/1.20.1`
project would apply it, reobfuscate `game-mod.jar` (loader classes name no game members), build against Java 17,
and use its own source folders: `mc/common` is NeoForge's and 1.21's (45 of its 149 files import `net.neoforged`).

**CI.** A 1.20.1 job: Java 17, Forge's installer from `maven.minecraftforge.net` (`--installServer`,
`--installClient`), HeadlessMC launching `forge-47.x`, and mc-runtime-test's `1.20.1` LexForge asset (its 1.20.1
module has Fabric and LexForge builds; NeoForge 1.20.1 is marked partial in its README). `ci/fetch-corpus.py`
already takes the loader as an argument.

## 6. Work on `main` first (seams)

Each of these is behaviour-neutral for NeoForge, lands on `main` as its own pull request with CI, and is what makes
`main` mergeable into the branches without breaking them. Per the rule against abstractions without a second user,
the abstractions below are added when the branch work starts (owner decision D2 on timing), not before.

1. **Java 17 for the shared modules**: `release = 17` for `core`, `api`, `fabric-loader-api` (toolchain stays 21):
   the 66 sequenced-collection calls and 8 type-pattern switches rewritten. Without it, any later `getFirst()` on
   `main` breaks a branch at merge time with no textual conflict.
2. **One loader per platform**: `loader/` moves to `loader/neoforge`, chosen per target by a `loader_dir` property;
   the platform-free helpers move to a shared source folder.
3. **The build split**: `mc/mc.gradle` into what every target shares and `platform-neoforge.gradle`; the version
   conditions keyed on a property, not on "not 26.x".
4. **CI from data**: the matrix read from `ci/targets.json` (`fromJSON`), the smoke scripts taking the loader and
   installer as parameters, each version's server checks in `ci/checks/<mc>.txt`; the long-lived branches added to
   the workflow's `push` branches.
5. **A platform profile** (data, per target, part of the cache key): the descriptor path and dialect, the
   platform's package prefixes and provided libraries, who provides MixinExtras, the data-pack keys, the Mixin facts
   (absorbing `mixin.*` from `version.properties`), the loader hook map, the rename table and the coremod-private
   fields, plus the runtime namespace. Two small interfaces where data isn't enough: the descriptor writer and the
   enum extension strategy. A second `MappingSupplier` for SRG.
6. **Tests that name NeoForge** (`NestedNeoForgeJarsTest`, `MultiLoaderJarsTest`, `PlatformRenamesTest`, …) run
   against the NeoForge profile, so a branch can run them against its own.

## 7. Roadmap

### 1.20.1-legacy

| Phase | Work | Exit criterion |
|---|---|---|
| L0 Evaluation (now) | This document; decisions D1 to D6. | Decisions recorded. |
| L1 Seams on `main` | §6, items 1 to 6, as separate pull requests. | `main` green on all four versions after each; `core` builds at release 17. |
| L2 Scaffolding | Merge `main` into `1.20.1-legacy`; `mc/1.20.1` on `legacyforge` (R1 settled first); Fabric API 0.92.12 plan (which modules native); compile only. | `:mc:1.20.1:fabricatedJar` builds in CI. |
| L3 Discovery and translation | forgespi 7 adapter (locator, dependency locator, language provider, nested jars); Intermediary → SRG composer from MCPConfig; Forge descriptor writer; MixinExtras provision; class transforms through `ITransformationService`. | The test mods' server smoke passes on Forge 47.4 (entrypoints, registries, mixins, access wideners). |
| L4 Native Fabric API | Lifecycle, registries, networking on `SimpleChannel`, resource loader, events, transfer on `LazyOptional` capabilities, the old renderer API; client smoke with HeadlessMC and mc-runtime-test. | Server and client smoke gates green; Fabric API 0.92.12 boots. |
| L5 Corpus and stabilisation | 1.20.1 corpus from Modrinth; pre-flight repairs against Forge 47's patches, vanilla code through MCPConfig; Fabric's Mixin fork features measured (R2). | Release criteria met (to be set with the owner, as for `main`). |

### forge

| Phase | Work | Exit criterion |
|---|---|---|
| F0 Evaluation (now) | This document; decision D5. | |
| F1 Seams on `main` | The same as L1, plus nothing Forge-only: EventBus 7 bridges live in the branch's `mc/*`. | |
| F2 Scaffolding | ForgeGradle build for 1.21.11 (Forge 61) and 26.x (64 to 66); compile only. | Jars build in CI. |
| F3 Discovery and translation | forgespi 8 adapter (shared design with L3); descriptor writer; class transforms. | Server smoke on one version. |
| F4 Bridges | Native Fabric API modules against EventBus 7, old capabilities, `ChannelBuilder` networking, `AddGuiOverlayLayersEvent`. | Server and client smoke gates on every version. |
| F5 Corpus | As L5. | Release criteria. |

Order: `main` first, always. L1 serves both branches; after it, L2 to L5 and F2 to F5 can proceed in either order
or in parallel, as the owner sets priorities. The two branches' adapters share their shape (forgespi 7 and 8 have
the same locator interfaces), so the second one is a port of the first.

## 8. Risks and decisions

| ID | Risk | Mitigation |
|---|---|---|
| R1 | ModDevGradle `legacyforge` on Gradle 9.7 is untested upstream. | Try it first in L2; if it fails, a branch-local Gradle wrapper (the wrapper is then a branch-owned file). |
| R2 | Fabric mods for 1.20.1 were built against Fabric's Mixin fork (0.12.5 to 0.15.5); upstream 0.8.5 may lack what they use. | Measure on the corpus in L5; pre-flight reports the gap per mod rather than crashing. |
| R3 | Discovery: the locator jar leaves normal mod loading, and a language provider must sit in the plugin layer; how a single download carries all three parts is UNVERIFIED. | Prototype in L3 before the bridges. |
| R4 | SRG names: every name-bearing data file (access transformers, refmaps, rename tables) must target members by SRG name. | One mapping table drives all of them, as Intermediary → Mojang does today. |
| R5 | CI cost: every target adds about 25 minutes per run. | Branch CI runs only for pull requests to that branch; docs-only changes build nothing (already so). |

Decisions for the owner:

- **D1:** 1.20.1 targets MinecraftForge 47.4 first, with NeoForge 47.1 best-effort.
- **D2:** when L1 starts. It is behaviour-neutral, and item 1 (Java 17) is cheapest done early.
- **D3:** which Fabric API modules are native on 1.20.1, and which are pass-through.
- **D4:** the 1.20.1 release criteria.
- **D5:** the build tool for modern Forge (ForgeGradle, version to verify).
- **D6:** CI policy for the branches (R5).
