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
| 2 | CI and workflows: one workflow and one test harness on every branch, the same test-mod suite everywhere, caching, CurseForge deployment | In progress: the Forge-shared code is on `main`; next the workflow and harness (`ci.yml`, checks as data), the shared test mods on the real Fabric API, the release workflow with CurseForge, then the Forge branches' syncs |
| 3 | Forge parity, and Minecraft 1.21.1 on Forge (Forge 52, `forge-legacy`) | — |
| 4 | Compatibility: Fabric API coverage, mixin and remapping edge cases, the difficult mods, popular mods on every loader and version | — |
| 5 | Security and robustness review, the release workflow's secrets included | — |
| 6 | Optimization, cleanup and bug fixes, with a startup and memory benchmark in CI | — |
| 7 | Release v0.6.0-beta.2 | — |

### Conclusions of the rethink

Checked against the four goals, v0.6.0-beta's release heads and their CI runs.

**The engine holds.** Discovery, translation, the cache, pre-flight and the report are shared by every platform and
version (`core`, 536 unit tests), and none of the goals is at risk there. The design doesn't change.

**Parity gaps.**

1. **Minecraft 1.21.1 has no Forge build.** Forge 52 (52.1.21 is the newest) runs on ModLauncher, as Forge 47 does,
   and Fabric mods for 1.21.1 use Intermediary names, as on NeoForge 1.21.1, so it reuses the remapping path NeoForge
   1.21.1 has and the Forge loader `forge-legacy` has (task 3).
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

**Releases.** The jars are named `fabricated-<version>+mc<minecraft>.jar` on NeoForge and
`fabricated-forge-<version>+mc<minecraft>.jar` on Forge, and the version in them doesn't say beta. From beta.2 every
jar is `fabricated-<loader>-<version>+mc<minecraft>.jar`, with the full version (`fabricated-neoforge-0.6.0-beta.2+mc26.3.jar`).
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
| MIX-3 | P3, no case seen | The Forge branches' mixin configs but `fabricated-loot` (`MIXINS.md`, *Forge configs*) | They leave `overwrites.requireAnnotations` unset, which every NeoForge config sets: a method a Forge mixin adds that its target already declares replaces the target's silently, without an `@Overwrite`, where on NeoForge the launch would stop on it. | Open: set it on each, checking every method the configs' mixins add against their targets on every version first, in its own pull request on each Forge branch (each launch applies them all, so CI shows a collision). |
| PERF-3 | P3, UNVERIFIED | `NeoForgeMods.of` (the mods' declarations, `NestedNeoForgeJars.readAll`); split from PERF-2 | A cached launch still reads every NeoForge mod's declarations and nested jars: the phase takes 16 to 23 ms on 1.21.1 and 40 to 68 ms on 1.21.11 and 26.x in CI's servers (extended run 42522554523), which have few NeoForge mods. What in it costs that, and how it grows with a pack of NeoForge mods, is unmeasured. | Read the report's *Launch timings* on a large pack; if the nested jars are the cost, keep their contents in the cache by the file's hash. |
| PROVIDER-19 | — (CI only), UNVERIFIED | The test mod's client block entity check (`BlockEntityEventsClientTest`) on the NeoForge 26.1.2 client with Forgified Fabric API's renderer, whose `fabric-lifecycle-events-v1` client mixins are upstream's (read in its `26.1.2` sources) | The check reported neither way there in extended run 41871298631 (no "client block entity events OK", no failure logged), where it passed in extended run 41858750959; that client's other checks passed, and the 26.2 one passed it. The provider's module unloads a client's block entities as it clears its level (`ClientPacketListener.clearLevel`), and on a respawn or a login. The job log's tail kept only the summary's failure, not the check's own line saying whether the client stopped in its world (the check's run 683 case). | Likely CI-5 (fixed), not the provider's: this client has the renderer API test mod too, whose terrain block replaced the check's chest, and the check misses wherever no other block entity is near spawn; run 41871298631's log kept no line to tell. The check passed on this client in extended runs 42522554523 and 42524794672. Closes once it holds here on two extended runs with CI-5's fix. |
| PART-5 | P3 on NeoForge 26.x clients with Fabric API, for Fabric block models whose particle sprite depends on the level (the view from inside such a block only) | The NeoForge 26.1.2, 26.2 and 26.3 plans of `fabric-renderer-api-v1` (its left-out `block.particle.ScreenEffectRendererMixin` on 26.1.2 and 26.2, `block.particle.LevelExtractorMixin` on 26.3) | The screen effect seen from inside a block asks the block's model for its particle sprite without the level, where upstream's hook gives Fabric's level-aware one: the plans leave upstream's hooks out and nothing asks the level-aware sprite in their place. On 26.3 NeoForge's `extractPlayerState` calls its own lookup (`getViewBlockingStateAndPos`), not vanilla's `getViewBlockingState`, whose view blocking check upstream's hook reads the position from (read in NeoForge's 26.3.x patch, while fixing PART-4 on Forge 64 to 66, whose lookup does the same). The 26.3 plan's reason doesn't name it; `COMPATIBILITY.md` lists the plain sprite on all three. | Task 4: as PART-4 on Forge, keep the block and position NeoForge's lookup found and ask a Fabric model's level-aware sprite there, with the Forge renderer test mod's screen effect check on NeoForge too. |
| CORPUS-38 | P3, UNVERIFIED | Fabric Language Kotlin (Forge corpus runs) | The linkage check lists `KotlinLanguageAdapter -> missing class net.fabricmc.loader.language.LanguageAdapter`, Fabric Loader's old adapter interface, which Fabricated doesn't provide. Kotlin mods load through its current adapter; whether any mod still names the old one is UNVERIFIED. | Provide the old interface if a mod needs it. |
| CORPUS-66 | — (CI only) | The 1.21.1 advanced corpus client's run (Origins) | The 1.21.1 advanced client reached its world (extended run 41539993361: the player's chunk loaded after 600 ticks) and then ran to the 10-minute limit: Origins opened its `ChooseOriginScreen` as the player joined, and mc-runtime-test waits for no screen before it finishes ("Screen not yet null"). As on Fabric, the screen waits for a player's choice. CORPUS-64's crash had hidden it. | Not Fabricated's. Task 2: the run should not wait on a mod's screen asking the player to choose (a corpus check that the world was reached, or Origins' choice made for the test), so the run says what loads. |
| KEYMAP-1 | P3 | Fabric mods' accessors to `KeyMapping.MAP` (OneConfig's keybind registry) | NeoForge retyped the field to `KeyMappingLookup`, so a `Map` accessor is left out and its callers throw `NoSuchMethodError`. OneConfig catches it (warning that its keybinds may shadow Minecraft's) and leaves the Controls screen mirrors of its keybinds registered with Minecraft's keybinds, which on Fabric it takes out again as it makes each one (`MinecraftKeybindBridgeImpl.detached`, read in its source and in its 1.21.1 jar in extended run 41496005222). They don't shadow Minecraft's there: the mirrors have OneConfig's own names, and NeoForge's lookup keeps every binding of a key, where vanilla's map keeps the last. P3 since then (was P2). | Open, documented in `COMPATIBILITY.md`: a `Map` view of NeoForge's lookup, served for such an accessor, would take the mirrors out as on Fabric; built if a mod needs it for more than that. |
| FC-7 | P3, UNVERIFIED on Windows | cache cleanup (`TranslationCache`) | `Files.walk` walks a Windows directory junction as a directory, so cleaning a tree holding one would delete what it leads to. | Fixed, unverified: cleanup deletes a link itself (a directory whose real path isn't its parent's with its name, or a reparse point), never its target, and the cache refuses a junction among its own directories as it refuses a symbolic link. The symbolic link case is tested (`TranslationCacheTest`); junctions exist only on Windows, where neither CI nor the tests run, so that case is untested. It moves to `FINDINGS.md` once checked on Windows. |
| CORPUS-5 | P3, cosmetic, UNVERIFIED | Traveler's Backpack (26.3 corpus) | NeoForge warns "Missing FluidModel" for its potion fluids. NeoForge's check runs inside `FluidStateModelSet.bake`, and upstream's `FluidStateModelSetMixin` wraps that method to add the models of Fabric-registered fluids after it returns, so the warning comes before those models are added. Whether the potion fluids have a model at all is UNVERIFIED. | Could register Fabric's fluid models through NeoForge's `RegisterFluidModelsEvent` instead. Low value. |
| CORPUS-17 | P3, UNVERIFIED | REI's Fabric build (26.2, 26.3 corpus) | REI logs "Failed to fill display for recipe" for Farmer's Delight's cooking pot recipes (2 to 4 times) and, on the 26.3 dedicated server, for 560 brewing recipes (run 379). REI logs them without a stack (run 381's digest found none to print). | Open: needs REI's own log at debug level, or its source. |
| CORPUS-25 | P3 | Terralith (26.3 server corpus, every run since at least run 420) | About 130 warnings per server run from NeoForge's `ModifiableBiomeInfo`: "Cannot modify spawn settings of biome terralith:alpha_islands, found unexpected attribute modifier OverrideModifier[]" (the first of them; runs 40241680133 and 40244234167). NeoForge 26.3 refuses to modify the spawns of a biome whose `natural_mob_spawns` attribute uses a modifier other than `overlay` (`ModifiableBiomeInfo`, read in its source), so biome modifiers can't add spawns to those biomes. | Open, likely Terralith's: the count fits every Terralith biome declaring an `override` modifier, which would warn on NeoForge without Fabricated too; UNVERIFIED, as Terralith's 26.x data isn't in its public repository. |
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
- Fabric Loader's old `LanguageAdapter` interface (CORPUS-38), if a mod needs it.

## Unverified

- Whether NeoForge 26.2 and 26.3 build locally: NeoForge's source transformer (JST 2.0.10) didn't apply an access
  transformer line to the anonymous class `HolderSet$1` in earlier local builds, while CI builds both.
- The Mixin behaviour version NeoForge 21.1's FML 4 applies a config at (`mixin_lowest=0.16.4` assumes its own Mixin's),
  and the one Forge 47 accepts (`0.8.5`).
- FC-7 on Windows (junctions), and an in-game allocation baseline for the chunk meshing path (CI has no GPU or
  profiler).

## Performance baselines

Translation time per mod on a cold cache, from `fabric-bridge-report.txt` (GitHub-hosted `ubuntu-latest`). Mods
translate in parallel, so the sum is CPU work, not wall time.

| Version | Run | Mods translated | Sum | Median | Slowest |
|---|---|---|---|---|---|
| 26.3 | server, test mods + Fabric API | 23 | 572 ms | 19 ms | 120 ms |
| 26.3 | server, mod corpus | 79 | 3,425 ms | 12 ms | 385 ms |
| 1.21.11 (remapped) | client, test mods + Fabric API | 25 | 1,375 ms | 41 ms | 204 ms |
| 1.21.11 (remapped) | server, mod corpus | 87 | 15,844 ms | 59 ms | 3,433 ms |

Discovery of a launch (the report's *Launch timings*: Fabricated's work before NeoForge loads the mods) on CI's
dedicated servers, v0.6.0-beta. The Fabric API server runs the test mods with Fabric API, the stand-ins server the
test mods alone. Runners differ by up to a third between runs.

| Version | Fabric API server, cold | Fabric API server, cached | Stand-ins server, cold | Stand-ins server, cached |
|---|---|---|---|---|
| 1.21.1 | 2,618 ms | 492 ms | 1,919 ms | 391 ms |
| 1.21.11 | 2,465 ms | 406 ms | 1,843 ms | 312 ms |
| 26.1.2 | 1,327 ms | 524 ms | 864 ms | 406 ms |
| 26.2 | 1,445 ms | 533 ms | 890 ms | 415 ms |
| 26.3 | 1,412 ms | 510 ms | 854 ms | 415 ms |

Per block, the chunk meshing seam does a `ClassValue` lookup and a `ThreadLocal` read and hooks the call with
`@WrapWithCondition`, so nothing is boxed or allocated; Fabric models go through Indigo's own path.
