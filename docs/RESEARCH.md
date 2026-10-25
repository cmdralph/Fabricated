# Fabricated — Phase 0 Research

> Status: research complete, awaiting architecture approval before implementation.
> Research date: **2026-10-10**. Everything below was verified on that date unless marked *unverified*.

## 0. How this was researched (and its limits)

| Source | How it was used | Trust |
|---|---|---|
| `neoforged/NeoForge` git branches & tags (`1.21.11`, `26.1.x`, `26.2.x`, `26.3.x`) | Read each branch's `gradle.properties`, Gradle wrapper, installer build logic, event classes | Primary |
| `neoforged/FancyModLoader` (FML) source at tags `10.x` (pre-`11.0`), `11.0`, `11.x`, `12.0`, `main` | Diffed the mod-discovery, transformation and mixin SPI across all versions | Primary |
| `FabricMC/fabric` tags, `FabricMC/fabric-loader`, `FabricMC/intermediary` | Fabric API module lists per version, loader dependencies, mapping files, licences | Primary |
| `Sinytra/Connector` (branches `26.1.x`, `1.21.x`), `Sinytra/ForgifiedFabricAPI`, `Sinytra/Launchpad`, `Sinytra/ForgifiedFabricLoader`, `Sinytra/Adapter` | Architecture review and licence check, **read only, no code copied** | Primary |
| fabricmc.net posts for [1.21.11](https://fabricmc.net/2025/12/05/12111.html), [26.1](https://fabricmc.net/2026/03/14/261.html), [Removing Obfuscation](https://fabricmc.net/2025/10/31/obfuscation.html); neoforged.net posts for [21.11](https://neoforged.net/news/21.11release/) and [26.1](https://neoforged.net/news/26.1release/) | Release-level facts (Java version, obfuscation removal) | Primary, but read through search summaries because the sandbox can't fetch these sites directly |
| Third-party guides and trackers (server hosts, CurseForge listings) | Only for build numbers that git can't give (latest 26.3 beta build) | Secondary, marked *unverified* |

**Sandbox limitation:** this development container's network policy blocks `maven.neoforged.net`, `maven.fabricmc.net`, `meta.fabricmc.net`, Mojang's launcher/piston hosts, and `api.modrinth.com`. GitHub, Maven Central and the Gradle Plugin Portal are reachable. As a result:
* Exact *latest* NeoForge build numbers per line couldn't be read from the NeoForged Maven. The git branch/tag data is authoritative for the **line** and its toolchain, but not for the newest build number.
* The NeoForge-dependent Gradle modules can't be built inside this container until those hosts are allowed (see §7). GitHub Actions CI is unaffected.

---

## 1. Version matrix (Minecraft 1.21.11 → 26.3)

### 1.1 NeoForge lines in range

The NeoForge repository has exactly four release branches in range. Its "stable" marker tags show which lines have left beta.

| MC version(s) | NeoForge branch | NeoForge version scheme | Stable? | FML | Java | NeoForge's own Gradle | ModDevGradle (NeoForge repo) | Mixin (Fabric fork) | MixinExtras | Obfuscated MC? |
|---|---|---|---|---|---|---|---|---|---|---|
| **1.21.11** | `1.21.11` | `21.11.x` | ✅ (`21.11-stable` tag) | **10.0.36** | **21** | 9.2.0 | 2.0.124 | 0.16.5+mixin.0.8.7 | 0.5.3 | **Yes**; NeoForm `20251209.172050`. NeoForge runs on **Mojang names** at runtime. |
| **26.1 / 26.1.1 / 26.1.2** | `26.1.x` | 4 components: `26.1.2.N` (e.g. Connector builds against `26.1.2.100`) | ✅ (`26.1.2-stable`) | 11.0.15 | **25** | 9.2.0 | 2.0.140 | 0.17.4+mixin.0.8.7 | 0.5.5 | **No.** First unobfuscated release, with parameter names included. |
| **26.2** | `26.2.x` | `26.2.0.N` (a tracker reports `26.2.0.89`, *unverified*) | ✅ (`26.2.0-stable`) | 11.0.16 | 25 | 9.7.0 | 2.0.142 | 0.17.4+mixin.0.8.7 | 0.5.5 | No |
| **26.3** | `26.3.x` | `26.3.0.N` (CI's newest-build check finds `26.3.0.95`; earlier builds were `26.3.0.N-beta`) | ✅ (`26.3.0-stable`) | 12.0.10 | 25 | 9.7.0 | 2.0.142 | 0.17.4+mixin.0.8.7 | 0.5.5 | No |

Notes:
* NeoForge's git has marker tags `26.1.0` and `26.1.1`, so NeoForge builds existed for 26.1 and 26.1.1, but the maintained branch is now 26.1.**2**. Fabricated should target **26.1.2** and treat 26.1/26.1.1 as "may work, untested".
* ModDevGradle (MDG) is version-agnostic. One MDG version (2.0.142) can build against every line; Connector uses 2.0.141 for 26.1.2 and the 1.21.x branch uses 2.0.140.
* `minecraft 26.4` snapshots already exist (Fabric API has `+26.4` tags). They're **out of scope** for this request.
* NeoForge 1.21.11 renamed `ResourceLocation` → `Identifier`. 26.1 renamed `GuiGraphics` → `GuiGraphicsExtractor` and added `ItemStackTemplate`/`FluidStackTemplate` (from the NeoForge release posts). These renames are why MC-facing code must be per-version (see the architecture doc).

### 1.2 Fabric side

| MC | Fabric API (latest tag) | Fabric API modules | Fabric mods' bytecode namespace | Access-widener format | Notes |
|---|---|---|---|---|---|
| 1.21.11 | `0.141.6+1.21.11` | 42 | **intermediary** (`net/minecraft/class_1234`, `method_…`, `field_…`) | `accessWidener v2 intermediary` (or `classTweaker v1 intermediary`) | Last obfuscated version. Mods are remapped to intermediary at build time. |
| 1.21.11 (experimental unobfuscated jar) | `0.139.4+1.21.11_unobfuscated` | – | official | – | Mojang's experimental side release. **Not** what NeoForge 21.11 runs. Ignore. |
| 26.1 / 26.1.1 / 26.1.2 | `0.145.1+26.1`, `0.145.4+26.1.1`, `0.155.3+26.1.2` | 45 | **official = Mojang names**, no remapping at all | `classTweaker v1 official` (not remapped) | Fabric dropped Yarn and Intermediary support. New Loom doesn't remap. Mods built for ≤1.21.11 must be recompiled. |
| 26.2 | `0.161.0+26.2` | 45 | official | `classTweaker v1 official` | |
| 26.3 | `0.162.0+26.3` | 45 | official | `classTweaker v1 official` | |

* **Fabric Loader** latest is `0.19.5` (git tags; on 2026-10-25 `0.20.1`, with the same public API, Mixin
  `0.17.5+mixin.0.8.7` and a behaviour version 0.17.5 for mods declaring 0.20.0 or later). It ships `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`, MixinExtras 0.5.5, `class-tweaker 0.3.0` and `tiny-remapper 0.14.1`.
* **Same Mixin fork on both sides:** NeoForge uses Fabric's own Mixin fork (`net.fabricmc:sponge-mixin`). On 26.x it's the *same version* Fabric Loader uses (0.17.4). This is the single most important feasibility fact: Fabric mixin configs, injectors and MixinExtras features behave identically on NeoForge 26.x. On 1.21.11 NeoForge has 0.16.5, one minor behind, which is usually compatible.
* **Module renames in 26.1** (Mojang-name alignment), relevant for dependency satisfaction:

| 1.21.11 module | 26.x module |
|---|---|
| `fabric-key-binding-api-v1` | `fabric-key-mapping-api-v1` |
| `fabric-item-group-api-v1` | `fabric-creative-tab-api-v1` |
| `fabric-screen-handler-api-v1` | `fabric-menu-api-v1` |
| `fabric-block-view-api-v2` | `fabric-block-getter-api-v2` |
| – | new: `fabric-advancement-api-v1`, `fabric-debug-api-v1`, `fabric-permission-api-v1` |

  The other 38 modules share names across the whole range.

### 1.3 Obfuscation and mappings, per version

| MC | NeoForge runtime names | Fabric mod names | Translation needed | Where the mapping data comes from (offline) |
|---|---|---|---|---|
| 1.21.11 | Mojang (since NeoForge 1.20.5) | intermediary | **intermediary → Mojang** | `intermediary/1.21.11.tiny` (official→intermediary, **CC0**, ~4.4 MB, bundled) **composed with** Mojang's `client-1.21.11-mappings.txt` / `server-…` (Mojang→official). The NeoForge 1.21.11 installer already downloads this file into `libraries/net/minecraft/client/1.21.11/` (`DOWNLOAD_MOJMAPS` step), so Fabricated reads it **locally** and never redistributes Mojang mappings. |
| 26.1.x / 26.2 / 26.3 | Mojang (unobfuscated) | Mojang (unobfuscated) | **None** (identity) | – (Intermediary has no 26.x files, which confirms it's discontinued) |

Mojang's mappings carry a restrictive licence. Fabric's wiki says "use them at your own risk", and redistribution is the concern. The design above avoids redistribution entirely.

---

## 2. NeoForge loader internals that matter (FancyModLoader)

Diffing FML between the last 10.x commit (used by NeoForge 1.21.11) and `main` (12.x, 26.3) shows the SPI Fabricated needs is **unchanged in code** across the entire range. The only difference is a nullability-annotation import (`org.jetbrains` → `org.jspecify`):

| SPI type | Purpose for Fabricated | 10.0.36 → 12.x |
|---|---|---|
| `neoforgespi.locating.IModFileReader` | Claim jars containing `fabric.mod.json` before FML rejects them | identical |
| `neoforgespi.locating.IDependencyLocator` / `IDiscoveryPipeline` | Add translated mod files and generated jars | identical |
| `neoforgespi.locating.IModFile` | Mod file abstraction | identical |
| `fml.jarcontents.JarContents` / `CompositeJarContents` | Overlay generated metadata on top of an untouched jar (**later delegates take priority**, the same in 10.x and 12.x) | identical (annotation only) |
| `neoforgespi.transformation.ClassProcessor` / `ClassProcessorProvider` | Bytecode hooks (interface injection, diagnostics) | identical |
| `neoforgespi.language.IModLanguageLoader` (ServiceLoader) | Custom language loader that runs Fabric entrypoints | identical |
| `[[mixins]]` in `neoforge.mods.toml` with `config`, `requiredMods`, `behaviorVersion` | FML applies the mixin config. `behaviorVersion` is passed to Mixin as `FabricUtil.KEY_COMPATIBILITY`, which is exactly the knob Fabric Loader sets per mod | present in 10.x and 12.x |
| `META-INF/accesstransformer.cfg` (default AT path) | Access widener translation target | present |
| `ModLoadingIssue` (`WARNING` / `ERROR`) | Human-readable issues on NeoForge's loading-error screen | present |

Other loader facts:
* ModLauncher is gone (FML 10 already has `ClassProcessor`, with only test remnants of ModLauncher). Loading is still **JPMS module layers** (`ModuleClassLoader`), so **split packages between two mods are fatal** and must be handled.
* FML's `IncompatibleModReason.FABRIC` turns any jar with `fabric.mod.json` that no reader claimed into an error. Readers run first, so a Fabricated reader claims those jars cleanly.
* FML has an experimental branch `startup-experiments-nomodules`. If NeoForge ever drops JPMS, split-package handling becomes unnecessary. Watch it.
* NeoForge's core events used for bridging (`RegisterEvent`, `RegisterCommandsEvent`, `ServerStarting/Started/Stopping/StoppedEvent`, `ServerTickEvent`, `LevelTickEvent`, `RegisterPayloadHandlersEvent`, `BuildCreativeModeTabContentsEvent`, `AddServerReloadListenersEvent`, `LootTableLoadEvent`, `ItemTooltipEvent`, `UseItemOnBlockEvent`, `PlayerEvent`, plus on the client `RegisterKeyMappingsEvent`, `ClientTickEvent`, `ScreenEvent`, `RenderGuiEvent`, `RegisterGuiLayersEvent`, `EntityRenderersEvent`, `ModelEvent`, `RegisterColorHandlersEvent`, `RegisterParticleProvidersEvent`, `RegisterMenuScreensEvent`, `ClientPlayerNetworkEvent`, `RenderLevelStageEvent`, `AddClientReloadListenersEvent`) exist on **both** 1.21.11 and 26.3.
  Churn exists in edge events (`DataPackRegistryEvent`→`NewDatapackRegistryEvent`, `FurnaceFuelBurnTimeEvent` and `VillagerTradesEvent`/`WandererTradesEvent` removed, render-pipeline events reworked), so the version-adapter layer has to absorb it.

---

## 3. Prior art

### 3.1 Sinytra Connector

* **Licence:** MIT (© 2023 Su5eD). Copying would be allowed with attribution, but Fabricated is a clean-room design and **copies no Connector code**.
* **Version coverage today:** `3.0.0-beta.6+26.1.2` (branch `26.1.x`, MC 26.1.2, NeoForge 26.1.2.100), and the `1.21.x` branch for **1.21.1** (LTS, critical fixes only). **No 1.21.11, 26.2 or 26.3 release.** The 1.21.11 request ([Discussion #2136](https://github.com/Sinytra/Connector/discussions/2136)) was answered with "Connector & FFAPI will be updated to Minecraft 26.1 next", so 1.21.11 was skipped.
* **Architecture:**
  1. `ConnectorLocator` (an `IDependencyLocator`) gathers Fabric jars that a custom reader turned into *stub* mod files.
  2. `JarTransformer` runs a pipeline per jar on a thread pool, with NeoForge's **AutoRenamingTool** (`Renamer`) as the jar-rewriting engine. Steps: metadata translation, signature stripping, intermediary→Mojang renaming (1.21.x), refmap remapping, field-to-method redirects, and **Sinytra Adapter**. Adapter is a large heuristic engine that *rewrites mixins* so injections aimed at vanilla code still apply after NeoForge's patches changed that code.
  3. `SplitPackageMerger` works around JPMS split packages.
  4. **Launchpad** (`FabricModFactory`) turns translated jars into NeoForge `IModFile`s and supplies the Fabric Loader runtime (`forgified-fabric-loader`).
  5. **Forgified Fabric API** supplies the Fabric API.
  6. Cache: output jar plus a `.input` file holding `cacheVersion + SHA-256(input)`. Config: `config/connector.json` (`hiddenMods`, `globalModAliases`, `enableMixinSafeguard`).
* **Known weaknesses:**
  * **Coverage gaps:** one active MC version at a time; 1.21.11, 26.2 and 26.3 aren't supported today.
  * **Heavy install:** players need 3–4 separate downloads (Connector, FFAPI, Launchpad, optionally Connector Extras), and all versions must match.
  * **Licence mix:** Launchpad is **GPL-3.0**, while Connector, Adapter and Connector Extras are MIT and FFAPI and Forgified Fabric Loader are Apache-2.0. A packaged Connector setup is therefore effectively GPL-encumbered.
  * **Opaque heuristic mixin patching** (Adapter): powerful, but when it guesses wrong it fails at class-load time with hard-to-read Mixin errors.
  * **Cache integrity:** only the *input* hash is checked. A corrupted or tampered *output* jar is trusted as-is.
  * **Slow first launch:** every Fabric jar goes through a full rewrite, even on 26.x where no renaming is needed.
  * **Reported incompatibilities** on 26.1.2 (secondary source, *unverified*): about 354 of 400 tested mods passed. Failures include Sodium (use Embeddium instead), ReplayMod, Carpet, ViaFabricPlus, Skyblocker, Friends & Foes and owo-lib. OptiFine and hybrid servers are unsupported.

### 3.2 Forgified Fabric API (FFAPI)

* **Licence:** Apache-2.0 (a fork of Fabric API, also Apache-2.0).
* **Coverage:** branches `1.21.1`, `26.1.2` (`0.155.3`) and `26.2.x` (`0.161.0`). **No 1.21.11 or 26.3 branch.** Tags such as `0.162.0+26.3` exist, but they appear to be mirrored upstream tags with no matching branch (*unverified as releases*).
* **Architecture:** a full fork of the upstream Fabric API with **~1,522 Java files and 434 mixin classes**. Only **26 files** touch NeoForge's event bus. Most of the work is re-targeting upstream mixins to NeoForge-patched code by hand, every version. That's why it lags and why a single maintainer group can't keep many versions alive.
* **Weaknesses:** per-version maintenance is enormous, and it's tightly coupled to Connector's Forgified Fabric Loader / Launchpad runtime. Its loader API classes can't sit next to another provider of `net.fabricmc.loader.api` without a JPMS split-package conflict.

### 3.3 Other components

| Project | Licence | Relevance |
|---|---|---|
| Fabric Loader | Apache-2.0 | Its *public API* (`net.fabricmc.loader.api.*`, `net.fabricmc.api.*`) is what Fabric mods compile against, so Fabricated must provide binary-compatible types. Apache-2.0 allows redistributing the API types with attribution (NOTICE). The implementation will be clean-room. |
| Fabric API | Apache-2.0 | Players already download the official jar. It can be *consumed* (not redistributed) through the pass-through strategy (architecture §6). |
| Intermediary mappings | **CC0-1.0** | Safe to bundle `1.21.11.tiny`. |
| tiny-remapper | LGPL-3.0 and not on Maven Central | **Not used.** Fabricated uses its own small ASM-based remapper. |
| mapping-io | Apache-2.0 (Maven Central 0.9.1) | Not needed. The two formats involved (Tiny v1, ProGuard txt) are trivial to parse in-house, which saves a dependency. |
| Launchpad | **GPL-3.0** | **Never copied or linked**: Fabricated is a clean-room design. |

---

## 4. Feasibility assessment: go / no-go

### ✅ GO: realistic and valuable

1. **One loader core for every version in range.** The FML SPI is identical from 10.0.36 to 12.x, so mod discovery, metadata translation, caching, diagnostics, config, safety checks and entrypoint dispatch are written **once**.
2. **26.1.2, 26.2 and 26.3 are the easy part.** No renaming is needed, the Mixin fork is the same, and class tweakers are already in official names. Per-mod translation is metadata plus an overlay, so the cache is tiny and first launch is fast.
3. **1.21.11 is achievable offline:** bundled CC0 intermediary plus the Mojang mapping file the NeoForge installer already put on disk, composed and cached once. This is a version **neither Connector nor FFAPI supports**.
4. **Fabricated has a real niche:** **1.21.11, 26.2 and 26.3** have no working Fabric-on-NeoForge option today. On 26.1.2, Fabricated would be a lighter, safer alternative.
5. **Safety requirements are fully achievable:** zip validation, size caps, no code execution during translation, integrity-checked cache, no network, no telemetry, original jars never modified.

### ⚠️ Partially achievable: honest limits

1. **Fabric API is the hard part.** A from-scratch port of 42–45 modules (×4 MC lines) is the bulk of what Connector + FFAPI do with years of effort. It is **not** achievable in one pass. The proposed alternative is a **hybrid** (architecture §6):
   * **Native NeoForge-backed bridges** for the modules that *must* integrate with NeoForge systems: base/events, lifecycle, commands, networking, registry sync, resource loading, creative tabs, key mappings, convention tags, resource conditions, and the most-used client hooks.
   * **Pass-through** for the rest: the player's official Fabric API jar is loaded *as an ordinary Fabric mod*, module by module. The mixin pre-flight verifier (architecture §5) disables only the modules whose mixins can't apply on NeoForge, and reports them clearly.
   * **Unsupported, documented:** `*gametest*` (development-time only; `data-generation-api-v1` was too until mods' runtime code was found loading its classes, DATAGEN-1), and anything that fails pre-flight. `renderer-api-v1` / `renderer-indigo` were assessed here as unsupported (they would replace NeoForge's model pipeline); they now pass through on every version, with only Fabric models sent through Fabric's renderer (see COMPATIBILITY.md).
2. **Mixins into code that NeoForge patched** won't apply when the injection point was changed. Fabricated **detects these before launch** and disables the affected mod with a precise message, or leaves out a mixin nothing outside it calls into (LEAVE-1). It does **not** try heuristic auto-rewriting like Sinytra Adapter, which is a research project in itself. What it does instead are pre-flight repairs (`MixinPreflight.repairs`): generic, pattern-based rewrites made only where the game's bytecode shows the right member for certain (an overload NeoForge reordered or moved the code into, a renumbered lambda, an accessor of a method NeoForge added, …), each reported as a warning, with no per-mod rewrite tables.
3. **"Never crash the game"** holds for everything detectable before mod code runs (bad metadata, missing dependencies, archive violations, failing mixin pre-flight, split packages). Once a mod's code *has run* (entrypoint exception, a mixin failing at class-load time), the JVM can't unload it safely. The best honest behaviour is a clean, attributed NeoForge error screen with a suggested fix, not a raw crash.

### ❌ NO-GO: will not attempt, documented as unsupported

* The Fabric builds of rendering-engine replacements: **Sodium, Iris**, and OptiFine-likes. (Revised 2026-10-19: their NeoForge builds work next to Fabric mods on 26.x, with Fabric block models meshed by Sodium's own Fabric renderer; see COMPATIBILITY.md.)
* Mods that bring their own launch or bootstrap hacks: **Carpet** (deep server internals), **ReplayMod**, **ViaFabricPlus**, and mods using Fabric Loader *internals* (`net.fabricmc.loader.impl.*`), `preLaunch` classloader tricks, or Mixin plugins that reflect into Fabric Loader. (Revised 2026-10-21: Carpet and ReplayMod were assessed as no-go here; both are now in CI's advanced corpus, `ci/mod-corpus-advanced.txt`, and translation now handles two things that stopped them there: Carpet's mixins that only give Brigadier classes its interfaces (LIB-1) and the refmaps of ReplayMod's unlisted mixin configs on 1.21.11 (REMAP-2); see roadmap.md.)
* Fabric mods built for ≤1.21.11 running on 26.x. Fabric itself requires recompilation, and so does Fabricated.
* Hybrid servers (Mohist/Arclight) and mobile launchers.
* 26.4 and snapshots (out of the requested range).

### Verdict

**GO**, with the scope adjusted. Fabricated will be a lightweight, safe, multi-version Fabric loader layer for NeoForge 1.21.11 → 26.3 with a hybrid Fabric API strategy, pre-flight compatibility checks and excellent diagnostics. It will **not** claim Connector-level coverage of every Fabric mod on day one. The compatibility list (`docs/COMPATIBILITY.md`) will grow from real test results, never from assumptions.

---

## 5. Recommended toolchain for this repository

| Item | Choice | Why |
|---|---|---|
| Gradle | **9.7.0** wrapper | Matches NeoForge 26.2/26.3. ≥ 9.1 is required for Java 25. |
| Build plugin | **ModDevGradle 2.0.142** (`net.neoforged.moddev`) for every MC subproject | Version-agnostic, and used by both NeoForge and Connector. NeoGradle isn't needed. |
| Java | Toolchain **21** for `core`, `loader` and `mc-1.21.11`; toolchain **25** for `mc-26.*`. `core`, `api` and `fabric-loader-api` compile their main code at **release 17** (tests at 21) | Matches each NeoForge line's requirement. The shared modules' bytecode runs on every line, and on 1.20.1's Java 17 (§8, D2). |
| Runtime dependencies | **None beyond what NeoForge already ships** (ASM, Gson, Mixin, MixinExtras, night-config, SLF4J/log4j) | Lightweight requirement. |
| Test dependencies | JUnit Jupiter (Maven Central) | |
| Bundled data | `intermediary/1.21.11.tiny` (CC0), Fabric Loader API types (Apache-2.0, with NOTICE) | |

## 6. Licensing decision

* Originally MIT; the project has since moved to **GPL-3.0** (`LICENSE`). Apache-2.0 and CC0 inputs remain compatible.
* Add `NOTICE` for: Fabric Loader API types (Apache-2.0, FabricMC) and Intermediary 1.21.11 (CC0, FabricMC, attribution given as a courtesy).
* **No** code from Connector, Launchpad, Adapter, FFAPI or Forgified Fabric Loader. Their designs were studied; the implementation is clean-room.

## 7. Environment action needed for Phase 2

To build and run the NeoForge-dependent modules **inside this cloud container**, its network policy must allow:
`maven.neoforged.net`, `maven.fabricmc.net`, `libraries.minecraft.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `resources.download.minecraft.net`, plus `api.foojay.io` and its JDK download hosts (for the Java 25 toolchain, since only JDK 21 is installed locally).
Without these, Phase 2 can still compile and unit-test the MC-independent `core` here, while full builds run in GitHub Actions.

## 8. Platform decisions: Minecraft 1.20.1 and Forge (2026-10-21)

The owner's decisions after the platform evaluation. The evidence for each fact is in [`PORTING.md`](PORTING.md)
(§3 for 1.20.1, §4 for Forge, §5 for the build), with the repository, ref and file it was read in.

| ID | Decision | What it fixes |
|---|---|---|
| D1 | **1.20.1 targets MinecraftForge 47.4**, on the `forge-legacy` branch (Forge for 1.20.1 and other legacy versions). NeoForge's 1.20.1 build (47.1, a 2024 fork of Forge 47.1) is not a target: NeoForge needs no legacy branch (2026-10-25). | The 1.20.1 platform layer is written against forgespi 7 and Forge 47.4's `mods.toml` (`clientSideOnly`, the `accessTransformers` list). |
| D2 | **The shared modules stay loadable on Java 17.** `core`, `api` and `fabric-loader-api` compile their main code with `release = 17` (tests at 21, toolchain 21), so the build itself rejects Java 21-only APIs and language features in them. | Code written on `main` merges into `forge-legacy` without breaking it. |
| D3 | Native Fabric API modules on 1.20.1 (delegated): **`main`'s native set where Fabric API 0.92.12 has the module**, the rest passed through as on `main`; the list is fixed in phase L2 against the 1.20.1 corpus. | Which bridges the branch writes first. |
| D4 | 1.20.1 release criteria (delegated): **the gates `main` has**: the test mods' server and client smoke, the client module gate, Fabric API 0.92.12 booting, and corpus runs where no client or server crashes and every mod left out is reported with its reason. | When `forge-legacy`'s 1.20.1 target leaves its evaluation and stabilisation phases. |
| D5 | **Modern Forge builds with ForgeGradle** (version to verify against Gradle 9.7 when the `forge` branch starts). | ModDevGradle supports NeoForge, and Forge only up to 1.20.1 through `legacyforge`. |
| D6 | **CI on secondary branches runs for pull requests only.** A push builds only `main`; work on `forge` and `forge-legacy` is built when a pull request targets them (`.github/workflows/build.yml`: `push: branches: [main]`, `pull_request` for any base). | Runner time: each game target costs about 25 minutes per run. |

**Build paths.**

| Branch | Platform | Build plugin | Java | Runtime names | CI target |
|---|---|---|---|---|---|
| `main` | NeoForge 21.11, 26.1, 26.2, 26.3 | ModDevGradle 2.0.142 (`net.neoforged.moddev`) | 21 (1.21.11), 25 (26.x) | Mojang | NeoForge installers, HeadlessMC, mc-runtime-test |
| `forge-legacy` | Forge 47.4 (1.20.1) | ModDevGradle `net.neoforged.moddev.legacyforge` (`legacyForge { version = "1.20.1-47.4.x" }`, `reobfJar` to SRG, mixin refmaps); on Gradle 9.7 UNVERIFIED (R1) | 17 | Mojang classes, SRG members (`f_…_`, `m_…_`) from MCPConfig | Forge installer from `maven.minecraftforge.net`, HeadlessMC `forge-47.x`, mc-runtime-test's 1.20.1 LexForge build |
| `forge` | Forge 61 (1.21.11), 64 to 66 (26.1.2 to 26.3) | ForgeGradle (D5) | 21, 25 | Mojang | Forge installers |

**What 1.20.1 changes in the engine** (`PORTING.md` §3.2): discovery on forgespi 7 (`IModLocator`, `IDependencyLocator`,
`IModLanguageProvider`; the locator jar leaves normal mod loading), a remap target of Mojang class names with SRG
member names (Intermediary inverted, composed with MCPConfig's obfuscated → SRG file), Forge's `mods.toml` dialect
(`mandatory`, mixin configs in the manifest's `MixinConfigs`), MixinExtras provided by Fabricated (Forge 47 bundles
none), upstream Mixin 0.8.5, `IExtensibleEnum` instead of enum JSON, class transforms through ModLauncher's
`ITransformationService`, and Fabric API 0.92.12's modules bridged to Forge 47's events, capabilities and
`SimpleChannel`.
