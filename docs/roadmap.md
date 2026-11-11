# Fabricated: roadmap

What is open and what comes next. What works today is in [`COMPATIBILITY.md`](COMPATIBILITY.md), whose support
matrix is the source of truth for every loader and Minecraft version. What each release changed is in
[`CHANGELOG.md`](CHANGELOG.md), and fixed or closed findings in [`FINDINGS.md`](FINDINGS.md). This file changes in the
same change as the work, and stays forward-looking.

## Goals

Fabricated runs Fabric mods on NeoForge and Forge, for Minecraft 1.20.1 to 26.3. Every change serves four goals:

- **Lightweight:** no dependency beyond what the platform ships, translation cached between launches, a small jar and
  little RAM and CPU.
- **Robust:** a broken Fabric mod is disabled before it runs, or isolated after, and never crashes the game; the
  report says why.
- **Safe:** jars are untrusted input; no network access, no telemetry, original jars never changed.
- **Fast:** quick startup, cold and cached, and nothing on a hot path that allocates or locks.

The yardstick is what Sinytra Connector can't run: renderer replacements and shader pipelines (Sodium, Iris), mods that
load other mods (Essential), Fabric-only interface stacks (OneConfig, PolyPlus). Fabricated runs them with generic
capabilities, and refuses only what can't run safely or correctly, saying why.

Every loader and Minecraft version gets the same features, the same Fabric API coverage and the same test suite. A cell
of the support matrix that isn't supported says why.

## Branches

| Branch | Contains |
|---|---|
| `main` | The shared code (`core`, `api`, `fabric-loader-api`, `loader/common`, `mc/shared`, the code both Forge platforms share, the shared CI and the documents) and NeoForge for all its versions |
| `forge` | Modern Forge platform code: Forge 61 to 66, Minecraft 1.21.11 and later |
| `forge-legacy` | Legacy Forge platform code: Forge 47 (1.20.1) and Forge 52 (1.21.1, this pass) |

- Work happens on `claude/dev` (pull requests into `main`), and for Forge-only changes on `claude/dev-forge` and
  `claude/dev-forge-legacy`, cut from `forge` and `forge-legacy` with pull requests back into them.
- Shared changes land on `main` first and reach the Forge branches by merge (`scripts/sync-platform-branch.sh`, from a
  working branch cut from the platform branch). A platform branch is never merged into `main`.
- Logic that could be shared belongs on `main`; the platform branches stay as thin as possible. Code is never copied
  between branches.
- Nobody pushes to `main`, `forge` or `forge-legacy` directly. A pull request is merged once its full matrix is green.
- Commit messages, pull requests, release notes and documents carry no session links or tooling attribution.

## The v0.6.0-beta.2 pass

v0.6.0-beta is released (2026-11-09) on all three platforms. This pass brings every loader and Minecraft version to
equal support, adds Minecraft 1.21.1 on Forge and CurseForge deployment, and leaves the codebase smaller and cleaner.
The task table is the pass's checkpoint: it changes with every step, so the work can resume from it.

| # | Task | Status |
|---|---|---|
| 1 | Rethink the project and the spec: the support matrix, the conclusions, the criteria, the plan (below) | Done |
| 2 | CI and workflows: one workflow and one test harness on every branch, the same test-mod suite everywhere, caching, CurseForge deployment | Done (#326, #329 on `main`; #327 on `forge-legacy`; #328 on `forge`): times below, in *CI times*. Found on the way and fixed: LEVEL-2, BIOME-3, POLY-2; open for task 3: BIOME-4 and the shared checks each Forge platform doesn't pass yet, marked in `ci/checks.tsv` |
| 3 | Forge parity, and Minecraft 1.21.1 on Forge (Forge 52) | In progress: Forge 61 to 66's shared and bridge gaps closed, BIOME-4 fixed (#330, #331); Forge 47 on the shared suite, REG-9, PACK-7 and POLY-3 fixed (#332, #333); API-2 fixed; both Forge platforms build one copy of the discovery services, `loader/forge-shared`, with Forge 47's differences in its `fml47` variant and Forge 61 to 66's in `modern`, which Forge 52's match (securemodules 2.2.24 and Mixin 0.8.7, as Forge 61's). The rows `ci/checks.tsv` marked "task 3" on Forge 47 closed (REG-10, REG-11, PROVIDER-20, PROVIDER-21, CI-7). Forge 52 (1.21.1) on `forge-legacy`: `mc/forge-legacy/1.21.1` builds with main's ForgeGradle build (`mc/platform-forge.gradle`), most of its in-game mod and test mod from `mc/forge-shared`'s version groups (`1.21.1+`, `1.21.x`, `1.20.1-1.21.1`, `1.20.1-1.21.11`); PACK-8, PACK-9, BIOME-5, REG-12, NET-12, COLOR-3 and FLUID-4 fixed; its server and client parts pass locally, the checks it can't pass yet marked in `ci/checks.tsv` with their findings. ITEM-1, INTERACT-1, FRAPI-11, CFG-4, FUEL-2, LAMBDA-10 and PACK-10 fixed on Forge 52, most with Forge 47's and Forge 61's code moved into `mc/forge-shared`, and HUD-5 closed (no fault); MODEL-5, and NET-13, DROP-1 and ACC-5, which Forge 47's and Forge 61 to 66's own checks found once shared with Forge 52, fixed the same way. Left: CI-8's brewing recipe and recipe condition checks |
| 4 | Compatibility: Fabric API coverage, mixin and remapping edge cases, the difficult mods, popular mods on every loader and version | In progress: the baseline below; PRE-20, ARMOR-1, FRAPI-12, FRAME-1 and CI-9 fixed; the advanced corpus runs on Forge |
| 5 | Security and robustness review, the release workflow's secrets included | Done in review: SEC-6 to SEC-10 and CACHE-4 fixed, CACHE-5 open; SEC-1 to SEC-5 and CACHE-1 to CACHE-3 still hold; the release workflow runs on tags and by hand only; its token reaches the upload step alone, by environment variable, never printed (its pin job only checks that it is set) |
| 6 | Optimization, cleanup and bug fixes, with a startup and memory benchmark in CI | — |
| 7 | Release v0.6.0-beta.2 | — |

### Conclusions of the rethink

Checked against the four goals, v0.6.0-beta's release heads and their CI runs.

**The engine holds.** Discovery, translation, the cache, pre-flight and the report are shared by every platform and
version (`core`, 536 unit tests), and none of the goals is at risk there. The design doesn't change.

**Parity gaps.**

1. **Minecraft 1.21.1 has no Forge build.** Forge 52 (52.1.21 is the newest) is modern Forge's generation below the
   game: ForgeGradle 7, Mojang's names at runtime, the same module stack and installer as Forge 61, and Mixin 0.8.7;
   like Forge 47 it has EventBus 6, JavaScript coremods and no MixinExtras. Fabric mods for 1.21.1 use Intermediary
   names, remapped to Mojang's as on NeoForge 1.21.1 and Forge 61 (task 3).
2. **The three platforms don't run the same tests.** The NeoForge test mods report 100 named checks, Forge 61 to 66's
   65 and Forge 47's 67; only 21 are common to all three. Each platform has its own test mod sources, Fabric API
   stand-ins and smoke scripts. The fix is one suite per Minecraft version, shared on `main` and built by every platform
   with that version, with each check's expected result per platform as data, so a platform that can't pass a check
   lists it as unsupported with the reason (task 2, then task 3 closes the gaps).
3. **Forge lacks features NeoForge has:** the `/fabricated` command; Fabric mods' plugins for JEI, REI, EMI, Jade and
   Simple Voice Chat (the annotations core's `Interop` computes reach NeoForge's scan data only); OneConfig and PolyPlus
   aren't run; the item, content registry, lifecycle, object builder, registry sync, fluid rendering, sound and HUD gaps
   `COMPATIBILITY.md` lists under *Unsupported on Forge*. Each is closed in task 3 or marked unsupported with the reason.
4. **Fabric API coverage is equal; its implementation differs.** On every platform every module of the official jar
   loads (game tests excepted). A module is native only where the platform owns the system and upstream's hooks can't
   apply (19 on NeoForge 1.21.1, 17 on its other versions, 6 on Forge 47, 3 on Forge 61 to 66); that stays so.

**Duplication across branches** (against *lightweight* and the branch model):

1. `loader/forge-shared`, `mc/forge-shared` and `mc/forge-mixinextras.gradle` (about 3,100 lines) are identical copies
   on both Forge branches. They move to `main`, which holds them as shared code only the Forge branches build (task 2).
2. Three CI workflows (`build.yml` 1,481 lines, `build-forge.yml` 383, `build-forge-legacy.yml` 448), two copies of
   `check.yml` and three sets of smoke scripts (about 600 to 650 lines each) that check different things. They become
   one workflow and one harness on `main`, with only a platform's install and launch steps in `ci/<platform>` (task 2).
3. `docs/FORGE.md` and `docs/FORGE-LEGACY.md` are identical copies. Their content is in `COMPATIBILITY.md` now, and the
   Forge branches drop them at their next sync, with their own READMEs: one README covers every platform.

**Noise.** The documents hold about 10,000 lines, much of it the history of how a decision came about. The
`CHANGELOG.md` is a log of working sessions rather than of releases, `claude.md` is a working memory, and
`PORTING.md` and `RESEARCH.md` are mostly records of past research. Task 6 makes `CHANGELOG.md` user-facing per
release, folds the working rules into `CONTRIBUTING.md`, keeps the verified facts and drops the rest. The CI folder's
diagnostic scripts that no job runs go in task 2.

**Releases.** Every jar is named `fabricated-<loader>-<version>+mc<minecraft>.jar`, the loader `neoforge` or `forge`
(`mc/mc.gradle`), and the version in it doesn't say beta yet. From beta.2 it is the full version
(`fabricated-neoforge-0.6.0-beta.2+mc26.3.jar`).
There is no CurseForge deployment yet: a release workflow uploads exactly the jars that passed the full matrix to one
CurseForge project, every loader's (task 2).

**Settled, not reopened this pass:** 1.20.1 is supported on Forge only. Fabric's renderer API keeps Fabric API's own
renderer, with Fabricated owning the seams (no second renderer or quad encoder). Login queries stay unsupported on
NeoForge (no hook in its login phase). `preLaunch` runs with `main`. A Fabric API module another mod provides
(Forgified Fabric API, Sodium's bundled ones) is that mod's, and Fabric Loader's API is always Fabricated's.

### What beta.2 means

1. **The support matrix is complete:** every cell for NeoForge 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3 and Forge 1.20.1,
   1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3 is supported, from CI, or unsupported with the reason.
2. **One test suite everywhere:** the same test mods per Minecraft version run on every loader with that version, on a
   server and a client, with and without Fabric API, with the renderer test mod, all as gates. A check a platform
   doesn't pass is a matrix cell marked unsupported, with the reason.
3. **The beta's stability bar holds on every branch:** no launch stopped by Fabricated other than its refusals (Sinytra
   Connector, a second Fabricated jar), a failed Fabric mod isolated where the platform allows, no open P0 or P1, no
   open finding on untrusted input, upgrades that don't break a game directory, and the developer API (1.x) stable.
4. **CI:** each change runs what it touches, a fast job on every push, the full matrix on pull requests into `main`,
   `forge` and `forge-legacy`, release tags and by hand; a startup and memory benchmark with recorded baselines, and a
   regression over 10% explained in its pull request.
5. **Green release heads:** the full matrix and an extended run green on each branch's release head. An informational
   run becomes a gate once it holds on two extended runs in a row on the same code.
6. **The release:** a jar per loader and version, named alike, uploaded to CurseForge (a dry run first) exactly as the
   full matrix tested it, and release notes covering NeoForge, Forge and Forge legacy, Fabric's renderer API and the
   support matrix.
7. **The documents** describe the project as it is, with no decision provenance or session narration, and
   `CHANGELOG.md` is user-facing.

### Plan and parallelization

Shared design changes and anything touching the same files stay sequential, on `claude/dev`. Independent work goes to
subagents, each in its own git worktree with files no other agent touches; they verify locally and never push. Their
results are reviewed and integrated, built and tested again, and pushed in batches.

| Task | Sequential (shared design, on `claude/dev`) | In parallel |
|---|---|---|
| 2 | Move the Forge-shared code to `main`; one workflow (`ci.yml`) whose plan job reads the branch's platform folders and what a change touches; one smoke harness with platform adapters; the shared test-mod layout and its check manifest. Then sync both Forge branches | A: the CurseForge release workflow and its script, with a dry run. B: the NeoForge test mods moved into the shared, platform-neutral layout. C and D, once A and B are in: each Forge branch's adapters and test mods on the shared layout (`claude/dev-forge`, `claude/dev-forge-legacy`) |
| 3 | Engine changes the Forge work needs, on `main` first | A: Forge 52 for 1.21.1 on `forge-legacy` (`mc/forge-legacy/1.21.1`, its loader's Forge 52 differences). B: modern Forge's gaps on `forge`. C, after A's loader changes: Forge 47's gaps (`mc/forge-legacy/1.20.1`). Legacy first |
| 4 | Generic engine fixes from what the runs show, on `main` | One agent per platform branch: local servers and clients with sets of popular Fabric mods, each version, findings and platform fixes |
| 5 | Fixes, integrated on `main` | Read-only audits: A archives, nested jars and the cache; B isolation and error paths; C the release workflow's secrets |
| 6 | Shared structure changes | A: the startup and memory benchmark in CI. B: dead code and duplication in `core` and `loader`. C: the documents. D: the Forge branches' structure, made consistent |
| 7 | The release, on every branch | — |

The network this work needs (the platforms' mavens, Mojang's servers, Modrinth for the corpus) is allowed in the
cloud environment `Fabricated`; the default environment reaches only Maven Central, where only the `coreOnly` build
runs.

## Open findings

Severities as [`FINDINGS.md`](FINDINGS.md) defines them. A finding moves there, with its cause and fix, in the change
that fixes or closes it.

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| MODIFIER-4 | P3, no case seen | `ModTranslator` (MODIFIER-3) | A Fabric jar's Forge structure modifiers stay as they are: Forge has no structure modifier that changes nothing but `forge:none`, one object, which a registry refuses under a second name. On Forge one of them would change the structures as the mod's Fabric code does too (MODIFIER-2's case), and one of a type only its Forge build registers wouldn't decode. No corpus jar carries one. | Open: if one is seen, a structure modifier type that changes nothing and decodes to a new object for each file, registered by the in-game mod on every Forge version. |
| FRAME-2 | P2, UNVERIFIED on real mods | NeoForge's patched game classes, which NeoForge compiles from its sources; Fabric's Mixin's local computation (`Locals.getLocalsAt`, FRAME-1) | NeoForge's classes have other frames than vanilla's, and Mixin counts their locals otherwise: in NeoForge 26.1.2's server game, 125 methods whose vanilla code Mixin counts right lose a live local at some frame (`LivingEntity.knockback`, `LivingEntity.startUsingItem`, `ServerLevel.sendBlockUpdated`, `Entity.collideWithShapes` among them), and 79 whose vanilla code loses one are counted right (every method with frames, its frames recorded and walked as Mixin does and counted as `getLocalsAt` counts them; 1,093 of vanilla's 14,102 lose one on Fabric too; the client's classes unscanned). A Fabric mod's local capture, `@ModifyVariable` or `@Local` at such a point finds other locals than on Fabric, as Resourceful Lib's did in Forge's patched `renderScreenEffect` (CORPUS-79); no mod seen failing so. Fabric mods accepting Fabric Loader below 0.12 (or any) get Mixin's behaviour 0.9.2 on Fabric, whose local computation counts each frame's own size, and at least 0.16.5 on NeoForge (FML's floor, `MixinCompat.clampForNeoForge`). | Open until a Fabric mod is seen failing so: then record a method's frames again, as Forge's repair does, only where vanilla's method counts right and NeoForge's doesn't at a Fabric injector's point (pre-flight has vanilla's classes, VANILLA-1, and each injector's target), which keeps Fabric's count elsewhere. Running Forge's repair on NeoForge as it is would also change the count where Fabric's Mixin miscounts, and Fabric's Mixin has no `ClassInfo.getMixins()` for it to call. |
| PRE-27 | P2, no case seen | `MixinPreflight.narrowLocalsAtMost` (PRE-25), `LocalRepairs` (LOCAL-2, LOCAL-4, PRE-25's ordinals) | PRE-25's bound counts the locals Mixin can find from the method's stores, loads and frames, but pre-flight reads game classes without their frames, so the frames' slots, which only widen what Mixin finds, are never counted: the bound can be too low, and a modifier that would apply left out. On SpongePowered's Mixin the ordinal repairs count from the local variable table, where a local Mixin keeps past its scope in a lower slot (PRE-26) would shift the ordinal it means. | Open: count with the frames `PreflightClasses.withFrames` gives (the points' indexes taken in that method), then check Apoli's hook on a Forge 47 server again; count kept locals in the ordinal repairs where `keepsLocalsPastScope` holds. |
| CI-8 | — (CI only) | Forge 52's test mods (`mc/forge-legacy/1.21.1`, `mc/forge-shared`) | Forge 47's own test mod's brewing recipe and recipe condition checks aren't Forge 52's: they're written in 1.20.1's APIs (`FabricBrewingRecipeRegistry`, `PotionUtils` and a static `PotionBrewing`; Fabric API 0.92's resource conditions in Forge 47's recipe `conditions` list), which 1.21.1 replaced (`FabricBrewingRecipeRegistryBuilder`; Forge 52 reads one `forge:condition`), so whether Forge 52 brews a Fabric mod's potion recipe, and asks a Fabric resource condition named in Forge's field, is unchecked: `ci/checks.tsv` marks both for 1.21.1. The rest of Forge 47's own checks, and Forge 61 to 66's ingredients on the network, load conditions and damage events, run on Forge 52 from `mc/forge-shared` (MODEL-5, NET-13, DROP-1, ACC-5); its registration order and brewing mix checks have nothing of Forge 52's to check, and `recipe-custom-ingredient` and `forge-fapi-network-ingredients` check on Forge 52 what its custom ingredient check checks. | Open |
| IRIS-3 | P2, UNVERIFIED in play | Iris 1.8's `fabric.MixinLevelRenderer` (a redirect of `ParticleEngine.render` in `renderLevel`), NeoForge 21.1's `LevelRenderer` | On NeoForge 1.21.1 pre-flight leaves the redirect out, where Forge 52 repairs it: NeoForge's `renderLevel` calls the particle engine's overload taking a frustum. What Iris loses with it (its particle pass's choice of particles drawn with the level) is unchecked. | Open |
| HANG-1 | P2, intermittent, cause UNVERIFIED | Essential's Fabric build, second launch (`client-essential`), on Forge 66 (26.3) | In extended run 42682670119 the client stopped logging just after it created its window and never reached its entrypoints, with no crash and no game thread in the harness's dump, until the run's timeout; the same job passed on its re-run, as it did in the extended run before. | Open: watch the next extended runs; a second hang there makes it a fault to trace from the run's logs |
| FRAME-1 | P2, UNVERIFIED on real mods | Fabricated's frame repair (CORPUS-79), which runs on Forge only | A Fabric mod's `@ModifyVariable` of the shape CORPUS-79 fixed on Forge (a local the method's compressed stack frames don't record) fails to apply on NeoForge's Mixin too: Forge's "recorded frames" check, ported to the shared test-mod suite, finds "0 candidate variables" on NeoForge 1.21.11 (Mixin 0.16.5) and 26.1.2 (0.17.4). | Task 4: run the frame repair on NeoForge where the same shape occurs, then add the check to the shared suite. |
| MIX-3 | P3, no case seen | The Forge branches' mixin configs but `fabricated-loot` (`MIXINS.md`, *Forge configs*) | They leave `overwrites.requireAnnotations` unset, which every NeoForge config sets: a method a Forge mixin adds that its target already declares replaces the target's silently, without an `@Overwrite`, where on NeoForge the launch would stop on it. | Open: set it on each, checking every method the configs' mixins add against their targets on every version first, in its own pull request on each Forge branch (each launch applies them all, so CI shows a collision). |
| PERF-3 | P3, UNVERIFIED | `NeoForgeMods.of` (the mods' declarations, `NestedNeoForgeJars.readAll`); split from PERF-2 | A cached launch still reads every NeoForge mod's declarations and nested jars: the phase takes 16 to 23 ms on 1.21.1 and 40 to 68 ms on 1.21.11 and 26.x in CI's servers (extended run 42522554523), which have few NeoForge mods. What in it costs that, and how it grows with a pack of NeoForge mods, is unmeasured. | Read the report's *Launch timings* on a large pack; if the nested jars are the cost, keep their contents in the cache by the file's hash. |
| PROVIDER-19 | — (CI only), UNVERIFIED | The test mod's client block entity check (`BlockEntityEventsClientTest`) on the NeoForge 26.1.2 client with Forgified Fabric API's renderer, whose `fabric-lifecycle-events-v1` client mixins are upstream's (read in its `26.1.2` sources) | The check reported neither way there in extended run 41871298631 (no "client block entity events OK", no failure logged), where it passed in extended run 41858750959; that client's other checks passed, and the 26.2 one passed it. The provider's module unloads a client's block entities as it clears its level (`ClientPacketListener.clearLevel`), and on a respawn or a login. The job log's tail kept only the summary's failure, not the check's own line saying whether the client stopped in its world (the check's run 683 case). | Likely CI-5 (fixed), not the provider's: this client has the renderer API test mod too, whose terrain block replaced the check's chest, and the check misses wherever no other block entity is near spawn; run 41871298631's log kept no line to tell. The check passed on this client in extended runs 42522554523 and 42524794672. Closes once it holds here on two extended runs with CI-5's fix. |
| PART-5 | P3 on NeoForge 26.x clients with Fabric API, for Fabric block models whose particle sprite depends on the level (the view from inside such a block only) | The NeoForge 26.1.2, 26.2 and 26.3 plans of `fabric-renderer-api-v1` (its left-out `block.particle.ScreenEffectRendererMixin` on 26.1.2 and 26.2, `block.particle.LevelExtractorMixin` on 26.3) | The screen effect seen from inside a block asks the block's model for its particle sprite without the level, where upstream's hook gives Fabric's level-aware one: the plans leave upstream's hooks out and nothing asks the level-aware sprite in their place. On 26.3 NeoForge's `extractPlayerState` calls its own lookup (`getViewBlockingStateAndPos`), not vanilla's `getViewBlockingState`, whose view blocking check upstream's hook reads the position from (read in NeoForge's 26.3.x patch, while fixing PART-4 on Forge 64 to 66, whose lookup does the same). The 26.3 plan's reason doesn't name it; `COMPATIBILITY.md` lists the plain sprite on all three. | Task 4: as PART-4 on Forge, keep the block and position NeoForge's lookup found and ask a Fabric model's level-aware sprite there, with the Forge renderer test mod's screen effect check on NeoForge too. |
| CORPUS-38 | P3, UNVERIFIED | Fabric Language Kotlin (Forge corpus runs) | The linkage check lists `KotlinLanguageAdapter -> missing class net.fabricmc.loader.language.LanguageAdapter`, Fabric Loader's old adapter interface, which Fabricated doesn't provide. Kotlin mods load through its current adapter; whether any mod still names the old one is UNVERIFIED. | Provide the old interface if a mod needs it. |
| CORPUS-66 | — (CI only) | The 1.21.1 advanced corpus client's run (Origins) | The 1.21.1 advanced client reached its world (extended run 41539993361: the player's chunk loaded after 600 ticks) and then ran to the 10-minute limit: Origins opened its `ChooseOriginScreen` as the player joined, and mc-runtime-test waits for no screen before it finishes ("Screen not yet null"). As on Fabric, the screen waits for a player's choice. CORPUS-64's crash had hidden it. | Not Fabricated's. Task 2: the run should not wait on a mod's screen asking the player to choose (a corpus check that the world was reached, or Origins' choice made for the test), so the run says what loads. |
| KEYMAP-1 | P3 | Fabric mods' accessors to `KeyMapping.MAP` (OneConfig's keybind registry) | NeoForge retyped the field to `KeyMappingLookup`, so a `Map` accessor is left out and its callers throw `NoSuchMethodError`. OneConfig catches it (warning that its keybinds may shadow Minecraft's) and leaves the Controls screen mirrors of its keybinds registered with Minecraft's keybinds, which on Fabric it takes out again as it makes each one (`MinecraftKeybindBridgeImpl.detached`, read in its source and in its 1.21.1 jar in extended run 41496005222). They don't shadow Minecraft's there: the mirrors have OneConfig's own names, and NeoForge's lookup keeps every binding of a key, where vanilla's map keeps the last. P3 since then (was P2). | Open, documented in `COMPATIBILITY.md`: a `Map` view of NeoForge's lookup, served for such an accessor, would take the mirrors out as on Fabric; built if a mod needs it for more than that. |
| FC-7 | P3, UNVERIFIED on Windows | cache cleanup (`TranslationCache`) | `Files.walk` walks a Windows directory junction as a directory, so cleaning a tree holding one would delete what it leads to. | Fixed, unverified: cleanup deletes a link itself (a directory whose real path isn't its parent's with its name, or a reparse point), never its target, and the cache refuses a junction among its own directories as it refuses a symbolic link. The symbolic link case is tested (`TranslationCacheTest`); junctions exist only on Windows, where neither CI nor the tests run, so that case is untested. It moves to `FINDINGS.md` once checked on Windows. |
| CACHE-5 | P3 | `FabricLoaderApiCopies.store` (NeoForge on FML 10 and later: 1.21.11, 26.x) | Each distinct copy of a jar left without Fabric Loader's API (Forgified Fabric API's nested Forgified Fabric Loader; up to 64 MiB) is written to `.fabricated/loader-api/<sha-256>/` and never deleted, so each update of such a jar leaves the old copy behind. It is written outside `TranslationCache`, without its checks for links in the path. | Open: keep the copies in the cache under their file name (their module's name comes from it), cleaned as the cache's other items; the reader runs before the pipeline, so its use must reach the pipeline's cache, as for CACHE-4. |
| CORPUS-5 | P3, cosmetic, UNVERIFIED | Traveler's Backpack (26.3 corpus) | NeoForge warns "Missing FluidModel" for its potion fluids. NeoForge's check runs inside `FluidStateModelSet.bake`, and upstream's `FluidStateModelSetMixin` wraps that method to add the models of Fabric-registered fluids after it returns, so the warning comes before those models are added. Whether the potion fluids have a model at all is UNVERIFIED. | Could register Fabric's fluid models through NeoForge's `RegisterFluidModelsEvent` instead. Low value. |
| CORPUS-17 | P3, UNVERIFIED | REI's Fabric build (26.2, 26.3 corpus) | REI logs "Failed to fill display for recipe" for Farmer's Delight's cooking pot recipes (2 to 4 times) and, on the 26.3 dedicated server, for 560 brewing recipes (run 379). REI logs them without a stack (run 381's digest found none to print). | Open: needs REI's own log at debug level, or its source. |
| CORPUS-25 | P3 | Terralith (26.3 server corpus, every run since at least run 420) | About 130 warnings per server run from NeoForge's `ModifiableBiomeInfo`: "Cannot modify spawn settings of biome terralith:alpha_islands, found unexpected attribute modifier OverrideModifier[]" (the first of them; runs 40241680133 and 40244234167). NeoForge 26.3 refuses to modify the spawns of a biome whose `natural_mob_spawns` attribute uses a modifier other than `overlay` (`ModifiableBiomeInfo`, read in its source), so biome modifiers can't add spawns to those biomes. | Open, likely Terralith's: the count fits every Terralith biome declaring an `override` modifier, which would warn on NeoForge without Fabricated too; UNVERIFIED, as Terralith's 26.x data isn't in its public repository. |
## Compatibility baseline (task 4)

The extended runs of 2026-11-09 on `main` (42624614743, 42682663433, then 42712511916), `forge` (42624619386,
42682670119, then 42712516460) and `forge-legacy` (42624625243, 42682675827, then 42712522675). Every gate holds but
those since fixed (FRAPI-12, CI-10). A corpus run is informational, so it reports "pass" whatever its checks say (CI-11
makes it count its failed checks); read by whether the game reached its world, 14 of the second round's 40 corpus runs
didn't, the advanced corpus on Forge among them for the first time. The third round ran before ENUM-8, LOADER-6 and
MULTI-2 were merged: on Forge every advanced corpus run stopped as in the second, and the Forge 52 client corpus stopped
too. The fourth round (2026-11-10: 42741690765, 42741694545, 42741699093), on the code through POI-3, before PRE-26: every
gate holds, and Forge 52's `client-sodium-fabric` held a second time, so it gates (CI-1). The main corpus fails no check on
NeoForge or on Forge 61 to 66; Forge 47's client fails one (block entities unloading), Forge 52's REG-6's. NeoForge's
advanced corpus fails only on 1.21.1 (CORPUS-66, and the resource loader's `/reload` check on the server, CORPUS-113); on
Forge, the advanced server reaches its world on Forge 61, and the others stop as the rows below say. The fifth round
(2026-11-10: 42768568693, 42768572948, 42768577406), on the code through MODIFIER-2: every gate holds. Of its 44 corpus
runs, 38 reach their world; the advanced servers start on Forge 52, 61, 65 and 66 (failing only CORPUS-113, the
ingredient checks Polymer's codec fails, CORPUS-114, and REG-13 on Forge 66), and the advanced clients reach their worlds
on Forge 61, 65 and 66, and on Forge 52 and NeoForge 1.21.1 wait on Origins' screen (CORPUS-66). Forge 47's and Forge 64's
advanced runs stopped on MODIFIER-2's no-op modifier registered twice (fixed since, MODIFIER-3), and Forge 47's server on
Ledger's mixin built for Java 21 (fixed since, JAVA-1; rows below). Some clients with the main corpus don't exit within the harness's limit (NeoForge 1.21.1, 26.2 and 26.3,
Forge 52): on 26.2 and 26.3 Xaero's World Map's world unload handler deadlocks with the integrated server's stop
(CORPUS-48, the mod's).

| Where | What | Status |
|---|---|---|
| NeoForge 1.21.1, advanced client | Figura disabled by pre-flight: its armor layer hooks don't occur in NeoForge's `HumanoidArmorLayer.renderArmorPiece` | Fixed (PRE-20), confirmed in the second round's corpus |
| NeoForge 1.21.11, advanced corpus | Biomes O' Plenty's Fabric build disabled by pre-flight: its `Entity.updateFluidOnEyes` redirect doesn't occur in NeoForge's method | Not repaired (CORPUS-22): NeoForge replaced the fluid tags on the eyes with its fluid types; its NeoForge build is the way |
| Forge 47, client corpus | Porting Lib's GUI utilities disabled by pre-flight: a `@ModifyArgs` call in `GuiGraphics` Forge changed | Not repaired (CORPUS-93): Forge gathers the tooltip components itself, which the module ports; Create's Forge build is the way |
| Forge 47, corpus | ModernFix names Fabric Loader's error GUI (`net.fabricmc.loader.impl.gui`), which Fabricated doesn't provide | No change: only lambdas `ModernFixPreLaunchFabric.onPreLaunch` never calls name it (read in 5.25.2+mc1.20.1) |
| Every version | Fabric Language Kotlin names Fabric Loader's old `LanguageAdapter` (CORPUS-38) | Open, no mod seen needing it |
| Forge 64 to 66, advanced server | Debugify's `CactusBlockMixin` (a MixinExtras expression with `@Local` definitions) fails Mixin's injection check in `Blocks.<clinit>`, which pre-flight passed: the server stops | Fixed (PRE-21), verified on a local Forge 66 server; the next extended run confirms it |
| Forge 61, advanced server | FrozenLib's mutable shadow of `MobCategory`'s `$VALUES`, where Forge extends the enum: `EntityType.<clinit>` throws | Fixed (ENUM-8): the shadow is no longer `@Mutable`, so Forge's enum extender finds the array; a local Forge 66 server with FrozenLib 3.0.3 starts; the next extended run confirms Forge 61 |
| Forge 61 to 66, advanced client | ReplayMod's mixin plugin adds configs while Mixin selects, which Forge's Mixin gives no environment: the client stops at launch | Fixed (LOADER-6): the mod's calls give the default environment; whether Forge's Mixin then applies the configs is UNVERIFIED, and pre-flight doesn't check them (MIXCFG-1) |
| Forge 47, advanced corpus | Origins' Fabric jar carries Connector's placeholder `mods.toml`, which Forge reads as a Forge mod needing Connector: Forge refuses the launch | Fixed (MULTI-2), and the MixinExtras copy it nests (PLAT-7), on a local Forge 47 server. That server then stopped on Apoli's `@ModifyVariable` `modifyEffectiveTool` in `ServerPlayerGameMode.destroyBlock`, whose boolean ordinal Forge 47's patch leaves past the booleans in scope: fixed (PRE-25), a local Forge 47 server with Origins starts. With FrozenLib 1.9.3 beside it, that server stopped next on FrozenLib's `@Local double` in `AbstractMinecart.tick`, which Forge's Mixin finds four of: fixed (PRE-26), a local Forge 47 server with FrozenLib, Origins, Carpet, Balm, GlitchCore and Polymer starts |
| Forge 64 to 66, client corpus | ShulkerBoxTooltip's `BundleMouseActionsMixin` (`@WrapOperation`s sharing locals in a method with `double` locals) fails verification when the menu opens (in the first round too) | Fixed (PRE-22): the wrapper Forge's Mixin would store over a shared local, and those sharing it, are left out; verified on a local Forge 66 client |
| Forge 64 to 66, advanced client | Emotecraft disabled: pre-flight fails an `@Inject` naming two methods, one of which exists, where Mixin needs one | Fixed (PRE-23), verified on a local Forge 66 client |
| Forge 47, advanced corpus | ReplayMod's 1.20.1 jar rejected for a duplicate directory entry (`assets/`) | Fixed (SEC-11), with pre-flight checking the configs ReplayMod adds from its code (MIXCFG-1), whose scroll hook stopped the client once the jar was accepted: a local Forge 47 client with ReplayMod 1.20.1-2.6.23 joins its world; the next extended run confirms it |
| Forge 47, advanced corpus | Carpet disabled, and hooks of five mods left out, at `ServerPlayer.changeDimension`, for which Forge 47 has an overload taking a teleporter | Fixed (PRE-24): on a local Forge 47 server Carpet loads, and its, Balm's, GlitchCore's and Polymer's hooks apply at the overload; FrozenLib's move there too |
| Every version, corpus and advanced corpus | Friends and Foes', Porting Lib's and YUNG's API's hooks at `StructureTemplate`'s private `placeEntities` (`m_74523_` on Forge 47) left out: NeoForge, Forge 47 and Forge 52 replaced it with `addEntitiesToWorld(level, pos, settings)`, Forge 61 to 66 with a `placeEntities` taking the settings in the pivot's place | Fixed (STRUCT-1) for the `@Inject`s at its call and its start (YUNG's API's and Friends and Foes' entity processing), which move to the platform's method; Porting Lib's wrapper and expression hooks, which port the platform's own entity processing, stay out, the report saying why. Friends and Foes' structure mob spawn wrapper in the method's lambda stays out on NeoForge and Forge 47 (*Not in this pass*) |
| Forge 47, 52 and 61 to 66, NeoForge 1.21.1 | owo-lib's registry freeze hooks, after Fabric Loader's `Hooks.startServer`, left out on dedicated servers: HOOK-1 mapped the hook to NeoForge's `ServerModLoader.load(boolean)` alone | Fixed (HOOK-4): mapped to NeoForge 21.1's and Forge's `ServerModLoader.load()` too |
| Forge 61 to 66 | Constructor hooks at points other than a return left out (FrozenLib, Wover, ImmediatelyFast, owo), where CTOR-4 serves Forge 47 alone | Fixed (CTOR-7) for FrozenLib, Wover and ImmediatelyFast: FrozenLib's and Wover's apply on a local Forge 66 server; ImmediatelyFast's, a client's, the next extended run confirms. owo's follow Fabric Loader's and Quilt's client start hooks, which neither Forge's nor NeoForge's `Minecraft` constructor calls: left out as on NeoForge, now reported as missing |
| Forge 66, advanced server | Wover Story's main entrypoint throws registering a `PoiType` "twice" (a local Forge 66 server with WorldWeaver 26.302.1; CI's advanced server stopped before the entrypoints, PRE-21, and in the fourth round on LIB-3); Fabricated disables the mod there, as the run's setting asks | Known limit (REG-13): its two point-of-interest types are equal records, which Forge's registry, keyed by equality, takes for one; no generic fix, and the failure says so |
| Forge 65 and 66, advanced client | `@WrapOperation`s of `Screen.extractRenderStateWithTooltipAndSubtitles` (Architectury, FancyMenu, SpruceUI) left out, which HOOK-2 points at NeoForge's hook | Known limit (SCREEN-2), which the report says: Forge's call, `drawScreen(graphics, x, y, delta)` of the `ForgeLayerInstance` `Gui` extends, doesn't take the screen the wrappers take as the call's receiver; `@Inject`s there move (HOOK-3). Forge 61 and 64 call the static `ForgeHooksClient.drawScreen(screen, ...)`, at which HOOK-2 applies Architectury's and FancyMenu's (the release's runs) |
| Forge 52, client corpus | MoreCulling's cull shape mixin, which clones an array, stops the client before its window: SpongePowered's Mixin finds no class for the array as it binds the mixin's calls (third round) | Fixed (SUPER-2), then Balm's drag hooks, at a call Forge 52 moved into its own hook (WILD-1): the fourth round's client corpus reaches its world, failing only REG-6's check |
| Every Forge version, corpus | The test mod's object builder check fails with the corpus loaded, on the servers and the clients' integrated servers: the point-of-interest type it registers isn't the one the game finds for its block (`FAILED: [poi]`, on a local Forge 52 client); it passes without the corpus | Fixed (POI-2: Lithium's copy of the platform's map), with Friends and Foes' and Moonlight's entrypoints, which then failed on Forge 52 and 61 to 66 (POI-3): confirmed by the fourth round's corpus on every Forge version. Wover Story's "registered twice" on Forge 66 isn't POI-3's: it registers one `PoiType` under two names, which Forge's registry refuses (the row above) |
| Forge 52, corpus | Moonlight's registry `moonlight:custom_map_data_types` is empty at the built-in registries' check Fabric API moves to the start, which REG-6 passes over only for Forge's own registries | Open, P3 (a logged error) |
| Forge 47, client corpus | The client's block entities don't unload as it leaves its world (third and fourth rounds; on Forge 26.1.2 in the third only, with the client not exiting normally on 26.1.2 and 26.2) | Open: the job logs keep only the summary; reproduce locally |
| Forge 52 and 64 to 66, advanced corpus | BetterEnd's, BetterNether's, BCLib's and Wover's entrypoints throw `ClassNotFoundException: org.spongepowered.include.com.google.common.collect.ImmutableList`: they name the Guava Fabric's Mixin jar carries, which Forge's Mixin doesn't (fourth round; the Forge 52 and 66 servers then stopped on what the failed entrypoints left half registered) | Fixed (LIB-3): a local Forge 66 advanced server starts, every Fabric entrypoint but Wover Story's (REG-13) running; on a local Forge 52 client and server the entrypoints run. The Forge 52 server then stopped creating its world: `NullPointerException` in `ForgeRegistry.makeSnapshot`, as Moonlight changes the leatherworker's point-of-interest type (a record) after Forge's maps keyed it by its equality: fixed (REG-14), a local Forge 52 advanced server saves its world's data and then stopped creating its levels, on Wover's generator calling Wover's biome module's accessor of a field Forge retypes: fixed (ACC-6), the server created its levels and stopped generating its first chunks on a feature order cycle in a Biomes O' Plenty desert, YUNG's Extras' Forge biome modifiers adding what its Fabric code adds: fixed (MODIFIER-2), a local Forge 52 advanced server starts, its only failing check the resource loader's `/reload` one, as on NeoForge 1.21.1 (Wover's tag event, the row below). The Forge 66 server fails two ingredient network checks with the corpus: Polymer's ingredient codec needs the packet context Fabric API binds only while a packet is coded, and the checks write outside a packet, which fails on Fabric too (CORPUS-114, not Fabricated's) |
| Forge 52, advanced client | Cobblemon's `client.ItemRendererMixin` fails as Mixin applies it: "Unsupported JUMP opcode in initialiser" (fourth round) | Fixed (PRE-28), with its `client.ModelLoaderMixin` (an array's stores there): on a local Forge 52 client both are repaired and Mixin applies the item renderer's. The client then stopped in `Gui`'s constructor: Apoli's `InGameHudMixin` adds its HUD renders at index 3 of the layers of the `LayeredDraw` it captures there, which Forge 52's `Gui` leaves empty until its constructor ends (`IndexOutOfBoundsException: Index: 3, Size: 0`), and at a read of `layers` Forge's patch makes twice: fixed (HUD-6, VANILLA-4), a local Forge 52 advanced client builds its HUD with the hook applied once and creates its world; past ACC-6 and MODIFIER-2 it joins it, HUD-6's check passing there with Apoli's hook applied, and waits on Origins' `ChooseOriginScreen` until its time limit, as on NeoForge 1.21.1 (CORPUS-66). NeoForge 21.1 applies it at Fabricated's seam for that read (HUD-7); Forge 61 to 66 have no `LayeredDraw` |
| Forge 61, advanced client | The client stops with an `UnsupportedOperationException` in `PackRepository.addPackFinder` (fourth round) | Fixed (PACK-11): FrozenLib's resource pack API leaves the client's pack sources immutable, and Forge adds its finders after; PACK-1's mixin, which NeoForge and Forge 47 had, now runs on every Forge version: a local Forge 61 advanced client joins its world and exits normally, every check passing |
| Forge 47, advanced client | `ClassNotFoundException: com.llamalad7.mixinextras.MixinExtrasBootstrap` (fourth round) | Not a stop: the digest's last exception chain of `debug.log` is Fabricated's probe of the MixinExtras version on Forge's own layers, logged at debug level. The console shows the client stopped on FrozenLib's gravity hook ("Found 4 candidate variables"), PRE-26, which the round ran before; a client run confirms it. The digest now opens on the console's last chain where no crash report names the stop, and leaves out exceptions logged at debug level (CI-12) |
| NeoForge 1.21.1 and Forge 52, advanced server | The resource loader's check that a listener runs again on `/reload` fails (fourth round; a local Forge 52 server once MODIFIER-2 let it start) | Not Fabricated's (CORPUS-113): on a local NeoForge 1.21.1 advanced server, with WorldWeaver 21.0.13 as the round fetched, `/reload` fails in Wover's biome tag handler ("`context` is null"), the data stays as it was and the listener doesn't run again, as on Fabric; on Forge 52 the reload fails in the same handler (`BiomeManagerImpl.onBootstrapTags`, from `TagLoader.loadAndBuild` through Wover's mixin). It is each run's one failed check |
| Forge 47 and 64, advanced corpus | Forge 47's client and Forge 64's server and client fail to load their registries: MODIFIER-2's no-op modifier, Forge's one `NoneBiomeModifier`, under each of YUNG's Extras' three names (fifth round) | Fixed (MODIFIER-3): a local Forge 64 advanced server starts, failing only the ingredient checks (CORPUS-114); the Forge 47 advanced client and server load their registries and create their worlds; a local Forge 52 advanced server is as before |
| Forge 47, advanced server | `UnsupportedClassVersionError` for `LivingEntity` (class version 65): Ledger 1.3.18's Java 21 mixin, which Mixin applies on Java 17 (fifth round) | Fixed (JAVA-1): Ledger isn't loaded, with the Java it needs; a local Forge 47 advanced server then creates its world |
| Forge 47, advanced client | Bobby's client entrypoint throws `NoSuchMethodError` for its accessor of `OptionInstance$IntRange`'s `maxInclusive`, which pre-flight left out under its intermediary name (fifth round) | Fixed (REMAP-5), on a local Forge 47 advanced client |
| Forge 47, advanced corpus | Past MODIFIER-3 and JAVA-1, the server and the client's integrated server stop creating their worlds: BCLib sets a final field of `BiomeGenerationSettings` | Fixed (BIOME-6): a local Forge 47 advanced server starts, every check passing; the advanced client's integrated server then stopped as a structure's barrel was placed: fixed (POI-4), the client joins its world and waits on Origins' screen, as on Forge 52 (CORPUS-66), or, in one of two local runs, loses its connection in a race of Polymer's and Fabric API's (NET-14, not Fabricated's). BCLib's loot pools, made with vanilla's constructor, are made with Forge's there, and BetterNether's bartering table is built (LOOT-3) |
| Forge 47, 52 and 61 to 66 | The advanced corpus didn't run on Forge | Runs on every Forge version in the extended runs (informational), with the main corpus's drops; its results set what task 4 takes on next |
| Forge 47, corpus and advanced corpus | Hooks in Fabric mods' interface mixins other than an `@Inject` at `HEAD`, `RETURN` or `TAIL` left out: Origins' armour restriction, owo-lib's recipe remainders, Carpet's, ServerCore's, Wilder Wild's, Porting Lib's custom light and redstone, reach-entity-attributes' (the v0.6.0-beta.2 release's corpus runs) | Fixed (INTERFACE-2): Fabricated applies `@Inject`s at calls and fields, capturing locals or not, `@Redirect`s, `@WrapOperation`s and `@ModifyConstant`s there; on local Forge 47 servers Apoli's, owo-lib's, Carpet's, ServerCore's and Wilder Wild's apply. Porting Lib's two and reach-entity-attributes' two stay out: Forge 47 replaced the calls and the constant they hook. Fabric API's recipe remainder hook is left out whole, as on Forge 52, its handlers sharing a ThreadLocal (ITEM-2) |

## Not in this pass

Candidates, each waiting for a mod or a report that needs it:

- Fabric's level-aware `materialFlags` where NeoForge 26.x asks whether a block is translucent (accuracy, not a fault:
  Fabric's contract allows the context-free answer).
- A `Map` view of NeoForge's `KeyMappingLookup` for accessors to `KeyMapping.MAP` (KEYMAP-1).
- `preLaunch` earlier than `main`; an early-window opt-out for mods acting before the first GL context.
- Fabric fluid rendering backed by NeoForge's fluid renderers, and NeoForge model data bridged to Fabric renderers.
- Per-mod `skipMixins` and `skipEntrypoints` in the compatibility plan (`compat-plan.json`), with the first entry that
  needs them.
- EMI's plugins, once EMI has builds for these versions.
- Wrappers PRE-22 leaves out kept instead: a mixin's wrappers reordered so a long or double is stored before the
  locals they share are, where they wrap different instructions (ShulkerBoxTooltip's bundle preview on Forge).
- Fabric Loader's old `LanguageAdapter` interface (CORPUS-38), if a mod needs it.
- Wrappers in a lambda of a method the platform replaced (STRUCT-1): Friends and Foes' `@WrapOperation` of a structure
  mob's `finalizeSpawn` in vanilla's `placeEntities` lambda, which fires its spawn event, on NeoForge and Forge 47,
  whose lambda in `addEntitiesToWorld` captures the settings where vanilla's captures their rotation, mirror and flag.
  Its handler would take vanilla's captured values worked out from the platform's two levels down (the lambda's, then
  the method's).
- Piston stickiness: Forge asks the block state's extensions (`isStickyBlock`, and `canStickTo` each way) where
  vanilla's `PistonStructureResolver` calls its static `isSticky` and `canStickToEachOther` (read in Forge 47's and
  66's classes), so Carpet's, FrozenLib's and Moonlight's hooks of those have nothing to hook. Their blocks'
  stickiness would come from answering the extensions from the mods' hooks, not from an injector move.
- Block breaking wrappers: Forge's `destroyBlock` (server and client) removes the block through the block state's
  `onDestroyedByPlayer` extension, which calls `playerWillDestroy` and sets the fluid's block, where vanilla calls
  `removeBlock`, `playerWillDestroy` and `setBlock` itself (read in Forge 47's and 66's classes). FrozenLib's, Wilder Wild's and Porting Lib's
  `@WrapOperation`s and `@ModifyExpressionValue`s of those calls would have to wrap the platform's extension method;
  `@Inject`s at them move to vanilla's place (CORPUS-72).
- Item checks the platform asks its extensions instead: Forge asks the stack's tool actions (`canPerformAction`) where
  vanilla asks `stack.is(Items.SHEARS)`, so BCLib's and Porting Lib's wrappers of that check
  have nothing to wrap (a shears item that is a `ShearsItem` gets the actions from the platform's, UNVERIFIED for these
  mods' own); Apoli's `ItemStack.is(Item)` hooks in the elytra and held item renderers are left out likewise.
- `CTOR_HEAD` on Forge 47: SpongePowered's 0.8.5 has neither `CTOR_HEAD` nor `unsafe`, so such hooks stay left out
  there (CTOR-7); Fabricated's `ConstructorPoint` could stand for it as it does for call points (CORPUS-103).

## Unverified

- Whether NeoForge 26.2 and 26.3 build locally: NeoForge's source transformer (JST 2.0.10) didn't apply an access
  transformer line to the anonymous class `HolderSet$1` in earlier local builds, while CI builds both.
- The Mixin behaviour version NeoForge 21.1's FML 4 applies a config at (`mixin_lowest=0.16.4` assumes its own Mixin's),
  and the one Forge 47 accepts (`0.8.5`).
- FC-7 on Windows (junctions), and an in-game allocation baseline for the chunk meshing path (CI has no GPU or
  profiler).
- In the game, until the next extended run: ENUM-8 on Forge 61 (checked on a local Forge 66 server), MULTI-2 on
  Forge 52 and 61 to 66 (checked on a local Forge 47 server), LOADER-6 on Forge 52 and 61 (checked on a local Forge
  66 client), SEC-11 and MIXCFG-1 in the Forge 47 advanced corpus (checked on a local Forge 47 client with ReplayMod),
  CTOR-7 on Forge 61 and 64 to 65, and ImmediatelyFast's client hook (checked on a local Forge 66 server).
- STRUCT-1 and HOOK-4 on NeoForge 1.21.11, 26.x and Forge 61, 64 and 65 (checked on local NeoForge 1.21.1, Forge 47,
  52 and 66 servers, and NeoForge 1.21.1's and Forge 47's corpus servers), NeoForge 26.3's `StructureTemplate` among
  them (read in 26.2's alone); YUNG's and Friends and Foes' processors on a structure that carries their entities (the
  hooks apply, and the test mod's own run).
- INTERFACE-2 and ITEM-2 on the Forge 47 and 52 client corpora (the server corpora checked locally), and chained
  handlers on one call in a corpus mod (core's tests cover them; no corpus mod has one).
- HUD-6 and HUD-7 with Apoli's own bars in a drawing of the HUD (no origin is chosen in the Forge 52 and NeoForge 1.21.1
  advanced clients' worlds; the test mods' layer of the same shape is checked there); MODIFIER-3 on Forge 61, 65 and 66
  (no corpus jar carries Forge modifiers there), and ACC-6's call from another mod on Forge 61 to 66 (a local Forge 66
  advanced server starts with the accessors adapted); REG-14 with a changed value on Forge 47 and 61 to 66 (their
  server parts pass with it).

## CI times

Wall time and job-minutes of a pull request's full matrix, before this pass's CI (v0.6.0-beta's last runs) and after
it (the first runs of `ci.yml`, before the base branches had written any of its caches):

| Run | Before | After |
|---|---|---|
| `main` pull request, NeoForge's five versions | 5 min, 50 job-min (22 jobs) | 6 to 7 min, 61 job-min (23 jobs) |
| `forge` pull request, four versions | 8 min, 32 job-min | 7 min, 35 job-min (15 jobs) |
| `forge-legacy` pull request, 1.20.1 | 7 min, 7 job-min | 5 min, 9 job-min (6 jobs) |
| Fast check on a push (unit tests and one version booted) | 2 min, 2 job-min | 2.5 min, 3 job-min |

The CI code shared by every branch replaced each branch's own: about 5,800 lines of workflows and smoke scripts in all
three became about 2,000 lines of workflow, harness and adapters, 470 of manifests and 390 of tests; the hand-written
Fabric API stand-ins went from about 6,900 lines to the 1,830 of game stand-ins Forge 47's Intermediary-named test mod
needs. The next pull requests show the times with the base branches' caches written.

## Performance baselines

The startup and memory benchmark (`ci/bench.py`) runs in each version's server part, on pull requests into `main`,
`forge` and `forge-legacy`, by hand and for the release, from the server run with the test mods and Fabric API
(`server-fapi`). It gates three numbers: what the JVM's threads allocate during Fabricated's discovery in the run's first
launch, which translates every Fabric mod, and in its second, with everything cached (discovery's log line and the
report's *Launch timings* give both), and the live heap once the server started, after a full GC (`jcmd`'s class
histogram, kept with the run's logs). Over seven local runs of NeoForge 1.21.1, four of 1.21.11 and three of 26.1.2,
the allocations held within 1% and the heap within 2%, where discovery's wall time varied by up to 18% on a quiet
machine and doubled beside a build, and its CPU time by about 20% with everything cached: the times are only reported.

The baselines are in `ci/bench.tsv`, one row per platform, version and metric, with why each last changed; the job's
summary shows each metric against its baseline. A metric more than 10% over its baseline fails the part, unless the
change records the new value there with its reason, and a pull request that changes a baseline must change its reason
too. NeoForge 1.21.1, 1.21.11 and 26.1.2 are recorded from local runs, which CI's runners matched within 1%; NeoForge
26.2 and 26.3, which don't build locally, and every Forge version from CI's runs. Forge's runs launch once, so its
cached allocation is only reported.
