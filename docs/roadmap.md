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
The same engine runs on Minecraft Forge, from each Forge platform's own branch, as experimental previews.

## How the project fits together

Rethought on 2026-10-26, when the three platforms came onto one working branch, and on 2026-10-28, when each went to
a branch of its own (*Decisions, 2026-10-28*).

**One engine, three platforms, a branch each.** Whatever can be decided without running the game is the engine, and every
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
5. **Gates grow.** A platform's checks become gates once they hold on every version, as Forge's server runs did once
   CI-1 and PLAT-4 were fixed. The Forge clients' runs held on every
   version before they reached a pull request, so they went in as gates.

## Rethink, 2026-10-27: the design against its four goals

Fabricated is meant to run Fabric mods on NeoForge and Forge faster, lighter, more stably and more securely than
Sinytra Connector. This pass checked the design and the way it is worked on against those four goals, for drift,
over-engineering and dead ends, without redesigning what holds.

- **Light** (no dependency but Fabric API's jar, translation cached): holds. The engine has no runtime dependency and
  a jar is translated once, keyed by its content and what it was checked against. The drift is in the process: three
  pull requests and three CI runs for every change, and one fact written into five documents.
- **Robust** (a broken mod is isolated, the game never crashes for it): holds, and most of the work went here.
  Pre-flight leaves out the hooks a platform's code can't take and disables a mod only when its own code depends on
  them (LEAVE-1). The risk is growth: `MixinPreflight` and its repair families gain a case with most findings (ARCH-1,
  task 4).
- **Safe** (jars are untrusted, no network, no telemetry): holds (`SafeArchive`, SEC-1 to SEC-5; only CI downloads
  mods). No change.
- **Fast** (startup and runtime): the cache makes a second start cheap, but the first start and the hot paths are not
  measured (PERF-1, PERF-2). No speed work until a measurement says where.

What changes, and why:

1. **One pull request per task, into `main`**, instead of one per release branch and pass. The three branches held the
   same tree, so their three pull requests checked the same code three times. (Superseded on 2026-10-28: each platform
   has its own branch, and a shared change reaches `forge` and `forge-legacy` by a sync pull request, *Branches*.)
2. **CI in two tiers.** A quick check on each push (`check.yml`: the unit tests, and one version of each platform the
   push changed booted on a server); the full matrix on the task's pull request, on release tags and by hand. The
   informational runs (the corpora, other mods' Fabric builds, the newest NeoForge build) wait for `extended`: their
   results move slowly and matter at a release. On `main`'s run 40518340743 a NeoForge job took 16 to 25 minutes, the
   gates about seven of them. A push to `main` only builds, so `main`'s Gradle caches stay current for pull requests,
   and a job installs the game once for all its runs.
3. **Local verification first.** The container reaches the game's mavens (an older note here said it couldn't), so a
   version's jars, its test mods and a server smoke run locally before a push. NeoForge 26.2 and 26.3 are the
   exception (*Unverified and blockers*).
4. **Forge parity through Fabric API's own modules and targeted bridges**, not ports of NeoForge's native modules.
   Upstream's modules load on Forge; where Forge's patched code doesn't take one of their hooks, the in-game mod fires
   the event from Forge's code (BREAK-1, SLEEP-1, SLEEP-2, DAMAGE-1). That costs less and keeps the platform folders
   thin. Task 2 continues this, and code a second platform needs moves to `mc/forge/common` or the engine.
5. **Test stand-ins only for what a check calls.** 1.21.11's Fabric API stand-ins and 1.20.1's Intermediary-named
   stubs grow by the members a check uses, no more.
6. **Documentation per task, not per change:** one CHANGELOG entry per task; this file and the parity table change
   when a row moves.

Unchanged: the engine and platform split, the platform descriptor, the cache, the security model. Noted and out of
this pass's scope: the stale remote branches `claude/forge` and `claude/forge-legacy` (deleted at the cleanup, with
the owner's approval), and cache eviction under GitHub's 10 GB limit, as each of `main`'s jobs saves a Gradle entry of
about 750 MB (to watch on the first runs).

## Rethink, 2026-10-28: sharing packs with Sinytra's ecosystem

The owner's report showed what packs really hold: Fabricated next to Forgified Fabric API, Sodium's NeoForge build,
Kotlin for Forge and NeoForge mods that bundle Fabric API modules, built by players who moved from Sinytra Connector.
This pass checked the specification against that, for what Fabricated owns, what it yields and what it refuses.
The four goals still decide: a pack must start (stable), nothing it loads is trusted (secure), and Fabricated adds
nothing it doesn't need (light).

**What Fabricated is beside that ecosystem: Connector's replacement, and a neighbour of its libraries.**

| Piece | Fabricated's part | Why |
|---|---|---|
| Sinytra Connector | Refused: the launch stops with an error naming both jars | Two loaders of Fabric mods can't share them; neither knows the other's mods. Decided 2026-10-28. |
| Forgified Fabric API (and NeoForge mods bundling Fabric API modules) | Each module it provides is its own; Fabricated's copy stands down, with its mixins and bridges | Its modules are NeoForge mods that NeoForge mods are built against; leaving them out would break those. What it does differently is its own (PROVIDER-5). |
| Forgified Fabric Loader (Fabric Loader's API in another jar) | Always Fabricated's; the other copy is left out as FML reads it | Fabricated's runtime knows every Fabric and NeoForge mod; mods use only the public API. |
| Kotlin for Forge with Fabric Language Kotlin | Kotlin for Forge's Kotlin, as any platform library | One copy of a package per layer. |
| Multi-loader jars whose NeoForge side is a placeholder for Connector | Loaded as the Fabric mods they are | Only Connector, or Fabricated, loads them. |
| A second Fabricated jar | Refused, as Connector is | The platform would run one by load order (DUP-1). |

**What changes:** nothing in the engine's shape. Every package Fabricated adds has one owner, decided before FML builds
the module layer (T1); a conflict is resolved or the affected mod is disabled and explained, and a
`ResolutionException` reaching a player is a bug. The checks: CI runs the real Forgified Fabric API with the test mods
on every NeoForge version it has a build for, and stand-ins for the rest (`ci/neoforge/standins.py`, the refusals).

**Considered and not done:**

- *Running beside Connector*, each loader taking some Fabric mods: every mod would need an owner, and mods of one
  loader would not see the other's. The error is cheaper and clearer.
- *Keeping Fabricated's modules and leaving Forgified Fabric API's out*: it changes other mods' jars and breaks the
  NeoForge mods built against them.
- *Shipping or requiring Forgified Fabric API*: heavier, and Fabricated's own modules already cover the Fabric mods it
  runs alone.
- *Patching Forgified Fabric API's behaviour* where it differs from Fabricated's (its networking refusing `minecraft`
  payloads, PROVIDER-5): it is that mod's code, and the decision is to yield to it.

**Noted for the Forge branches** (their next sync): the provider policy and the refusals are in the shared engine
(`NativeModules`, `PackageGuard`, `ConflictingJars`), but each Forge loader still has to call them. Connector and
Forgified Fabric API also exist for Forge 47 (1.20.1); whether packs there mix them with Fabricated is UNVERIFIED.

**The branch model supersedes item 1 of 2026-10-27:** each platform has its own branch (*Branches*, below), so a
shared change reaches `forge` and `forge-legacy` by a sync merge, not at the release.

## Decisions, 2026-10-28: sharing the game with Forgified Fabric API

A v0.5.0-alpha pack stopped before any mod ran: Forgified Fabric API's module jars and Fabricated's in-game mod both
had `net.fabricmc.fabric.api.screenhandler.v1` (PROVIDER-1). Packs mix Fabricated with Sinytra's ports and with
NeoForge mods that bundle Fabric API modules, so every package Fabricated adds now has one owner, decided before FML
builds the module layer, and a conflict is resolved or explained, never left to a `ResolutionException`. The owner's
decisions:

- **Fabric API modules: yield to theirs.** A module another mod provides is that mod's, and Fabricated's native copy
  stands down with its mixins and bridges (PROVIDER-1). The alternative, keeping Fabricated's and leaving theirs out,
  would change another mod's jar and break the NeoForge mods built against it.
- **Fabric Loader's API: always Fabricated's.** Another jar's copy is left out as FML reads it (PROVIDER-2):
  Fabricated's runtime knows every Fabric and NeoForge mod, and the mods built against Forgified Fabric Loader use
  only its public API.
- **Kotlin** is the platform's, as any library: Kotlin for Forge's copy wins over the one Fabric Language Kotlin
  bundles (`PackageGuard`, unchanged).

- **Sinytra Connector beside Fabricated: refused with a clear error.** Two loaders of Fabric mods can't share them,
  and neither knows the other's mods: the launch stops with an error naming both jars, and Fabricated loads no Fabric
  mod (task 4, `ConflictingJars`). Supporting both in one game would mean deciding, mod by mod, which loader takes it.
- **A second Fabricated jar is refused the same way** (decided, not asked: the platform would otherwise run one of them
  by load order, DUP-1).

**Branches** (task 2 of the v0.5.x update, the owner's request): `main` is NeoForge's branch, with the shared engine;
Forge 61 to 66 and Forge 47 are each on their own branch only (`forge`, `forge-legacy`), with the same folder
structure. Shared changes go to `main` and reach the Forge branches by merge (`scripts/sync-platform-branch.sh`), so
the engine stays one; a Forge branch is never merged back. A Forge branch's CI runs on pull requests into it, and the
release workflow builds each platform's jars from its own branch. What it costs: a shared change needs a sync pull
request per Forge branch before it ships there, and `forge-shared` is kept on both Forge branches by hand. No branch
was deleted.

## Next release: 0.6.0-beta

v0.5.1-alpha (`releases/v0.5.1-alpha.md`) was the v0.5.x update the owner planned on 2026-10-28: coexistence with
Sinytra's mods (T1, #217), a branch per platform (T2, #218 to #220), the specification rethought for those packs (T3,
#222), the reference pack's fixes (T4, #221), a cleanup (T5, #223) and the release (T6): the full matrix on every branch
and version, then PROVIDER-6 and DUP-2, which its extended runs found, fixed before it (#227, the owner's choice). The
release workflow made it a draft for the owner to publish. 0.6.0 is in development from then on, toward 0.6.0-beta,
which the next section defines.

## Beta: what 0.6.0-beta means

Defined on 2026-10-28, with the start of 0.6.0's development. Alpha meant the design could still move under a pack;
beta means it doesn't: NeoForge's targets are set, and the work to 0.6.0-beta is closing faults and turning the
informational runs into gates, not adding scope. Anything new that doesn't serve the criteria below goes into a track
here for after the beta.

**Platforms.** Beta is NeoForge's, on every version CI builds (1.21.1, 1.21.11, 26.1.2, 26.2, 26.3). Forge 61 to 66
and Forge 47 stay previews: they ship with each release from their own branches, with their gates green, but the beta
doesn't wait on their findings, and their notes say preview.

**Stability bar.** What a beta promises, on NeoForge:

1. **No launch stops for Fabricated.** A Fabric mod it can't run is disabled before it runs and the report says why,
   as a module conflict already is (T1). The only launches Fabricated stops are its refusals (Sinytra Connector, a
   second Fabricated jar), each with an error naming the jars. A mod's own fault, or another mod's module's
   (PROVIDER-5, PROVIDER-7), is that mod's, and the report names the mod.
2. **No open P0 or P1 on NeoForge**, by `FINDINGS.md`'s severities, UNVERIFIED ones included: a finding is fixed, or
   narrowed to "the mod is disabled and the report says why" with the evidence that lowers its severity.
3. **No open finding on untrusted input.** SEC-5's limits on nested jars hold across all the jars of a launch, not
   per jar.
4. **Upgrades don't break a game directory.** A cache from an older build is translated again (the schema is in the
   cache key), and a retired config key stays accepted and ignored. Both hold today; the beta keeps them as rules.
5. **The developer API (`docs/API.md`, 1.x) is stable.** A break needs the owner's decision, a major API version and
   a changelog entry.

**Compatibility targets.** Each one is a CI check on every NeoForge version, as a gate:

| Target | Check | Today |
|---|---|---|
| The test mods, with and without Fabric API, server and client | the smoke runs and the Fabric API client modules | gates |
| Fabric renderer and model APIs; Sodium and Iris for NeoForge | the renderer gates (26.x, 1.21.11) and the Sodium runs | gates, but FRAPI-5 (1.21.1) |
| Packs sharing the game with Forgified Fabric API and Sodium | the coexisting-mods server; the Polyfrost and Forgified Fabric API clients | the server a gate (with PROVIDER-3's and PROVIDER-6's checks); the clients informational |
| Popular Fabric mods: the mod corpus and the advanced corpus | each corpus's server and client reach a world, and every mod that doesn't load is named in the report with a reason | informational; 1.21.1's stop (CORPUS-44 to 46) |
| The owner's reference pack (Forgified Fabric API, Sodium, OneConfig, about 130 Fabric mods, 1.21.1) | boots to the title screen, its report explaining each disabled mod | OneConfig and PolyPlus reach a world (CORPUS-47); the whole pack isn't in CI (UNVERIFIED) |

A run becomes a gate once it holds on two extended runs in a row on the same code (CI-1: a check that can change
between runs of the same code is a defect of the check).

**Speed and weight.** A launch's cost is measured and kept: a cold and a warm launch per version in *Performance
baselines*, the warm one with PERF-2's re-reads gone. The beta's numbers become the bar later releases are held to; a
change costing more than 10% on either says why in its pull request.

**Ship criteria for 0.6.0-beta:**

1. The stability bar holds on `main`'s release head.
2. Every target above is a gate and green on that head, on every NeoForge version.
3. The full matrix is green on `main`, `forge` and `forge-legacy`, on two extended runs.
4. `COMPATIBILITY.md` matches the release head's CI, and the release notes say what changed since v0.5.1-alpha and
   what to do on upgrade.
5. The owner chooses the version and approves the tag, as for every release.

**What blocks it today** (2026-10-28): SER-1, CORPUS-44 to 46 and FRAPI-5 (the stability bar); SEC-5; the corpus and
coexistence clients not yet gates; PERF-2 and the warm-launch baseline. The order to work them in: the 1.21.1 corpus
stops (CORPUS-44 to 46), SER-1, SEC-5, FRAPI-5, then the gates and the baselines.

## v0.5.0

v0.4.0-alpha (`releases/v0.4.0-alpha.md`) shipped NeoForge's four versions and the Forge builds as server previews.
v0.5.0 is where the Forge targets get Fabric mods' client side and the platforms' coverage becomes comparable, with no
regression on NeoForge. Each item is done when its CI check is; the tracks below hold the detail.

The owner's plan to v0.5.0-alpha, in order (2026-10-27):

1. Rethink the design and make CI cheaper (above). Done (#207): a NeoForge job takes 7.5 minutes on a pull request,
   against 16 to 25.
2. Faster Forge development: Forge 61 to 66 and Forge 47 nearer NeoForge's coverage, first where the most Fabric mods
   are blocked, sharing code through the engine so the platform folders stay thin. Done in this task's pull request:
   the discovery code both Forge loaders share in `loader/forge-shared`, and the faults that kept the Forge corpus
   runs from testing Fabric mods at all (LOADERR-1, JIJ-1, MIXEXT-3, REPL-1, PLAT-5) or stopped them next (PRE-17,
   LINK-2). The corpus servers and clients now load 85 to 151 Fabric mods on Forge 64 to 66 and 47, where Forge loaded
   only itself; the next stops are PRE-18 and CORPUS-33 (*Findings*).
3. Minecraft 1.21.1: NeoForge 21.1 first, then Forge if it fits cleanly, on 1.21.11's Intermediary path, in the CI
   matrix with the same test mods. The owner chose the whole NeoForge target before the release (server, client,
   bridges, corpus) over a server preview. NeoForge 21.1 is done in this task's pull request: a loader variant for its
   FML 4 on ModLauncher, Fabric API 0.116.17 with 18 native modules, the storage and energy bridges on its handler
   capabilities, every server and client check with and without Fabric API, every Fabric API client module loading,
   and a matrix job like the other versions'. Forge
   for 1.21.1 doesn't fit cleanly and is not in this release: it is Forge 52, an older FML and ModLauncher than the
   Forge 61 to 66 the `forge` folder targets, so it would need a loader variant and an in-game mod of its own.
4. Cleanup, once: dead code, duplicated logic, stale workarounds, unused configuration, outdated documents, the module
   and package structure (ARCH-1), with the public API kept or its breaks documented. Done in this task's pull
   request: pre-flight's repairs in family classes behind one ordered table (ARCH-1), NeoForge 1.21.1's and 1.21.11's
   shared sources in `common121` and other copies merged (about 3,700 lines), the audits' dead code, stale CI steps
   and outdated documents (CHANGELOG). Left for later, as below: the copies a new kind of shared folder would merge.
5. Release v0.5.0-alpha: documents and versions, the full matrix green on every branch and version, the tag and notes
   for NeoForge, Forge and Forge 47 (a draft for the owner to publish). The extended runs on `main` before it
   (40625072551, 40625075054, 40625077485) stopped the 1.21.1 corpus servers; the owner chose to fix those first, in
   their own pull request: CORPUS-39 to 43 (two missing Fabric API methods, NeoForge 21.1 refusing vanilla's serializer
   registration, content a failed entrypoint left, a library package NeoForge 21.1's launcher ships). The rerun after
   it (40683010866) showed the next layer; the owner chose one bounded round: the rest of CORPUS-39 is fixed, and
   CORPUS-44 to 47 stay known limits of the release.

| Platform | Goals | Status |
|---|---|---|
| All | A branch per platform, each with the shared engine and its own platform's folders (*Branches*). | Done (#218 to #220) |
| All | Each Fabric API module's status on every platform, so the platforms can be compared: NeoForge's in `COMPATIBILITY.md`, the Forge platforms' in `docs/FORGE.md` and `docs/FORGE-LEGACY.md` on their branches. | Done: the Forge tables are the more limited of the server and client runs with Fabric API on every version |
| All | The engine's platform facts from the platform: the fields its coremods keep private (PLAT-4). | Done (PLAT-4, checked on all five Forge versions) |
| NeoForge (`main`) | The open corpus findings (below) that a generic fix covers. `preLaunch` right after FML's mixin bootstrap (*Loader and translation*, next). ARCH-1 is done (task 4). | Open |
| Forge 61 to 66 (`forge`) | Fabric mods' client entrypoints and a client run in CI. Entity and interaction events native on Forge's events, as `main` has them on NeoForge's (F4). The server runs made gates once their checks hold on every version. | Server runs gated (with Fabric API too); client entrypoints done, with a gated client run (REG-5 fixed on the way); interaction events checked, with no bridge needed; entity events bridged where Forge's code doesn't take upstream's hooks (DAMAGE-1), but for the effect events' hook into Brigadier; the corpus runs reach the game since LOADERR-1 (85 to 123 Fabric mods load) |
| Forge 47 (`forge-legacy`) | Fabric's networking through Forge 47's network stack and its storages on Forge 47's capabilities, then client entrypoints and a client run in CI (L4, `PORTING.md` §7). | Client entrypoints done, with a gated client run; networking checked (upstream's module carries a channel's packets both ways, no `SimpleChannel` port needed); storages on blocks and fluid storages in items bridged to Forge 47's capabilities, checked; the corpus runs reach the game since LOADERR-1 (135 and 151 Fabric mods load) |

## Tracks

### Platforms

Each capability on each platform, from CI. The detail of each port's steps is in [`PORTING.md`](PORTING.md) §7, and
each Fabric API module's status per platform in [`COMPATIBILITY.md`](COMPATIBILITY.md).

| Capability | NeoForge (`main`) | Forge 61 to 66 (`forge`) | Forge 47 (`forge-legacy`) |
|---|---|---|---|
| Minecraft versions | 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 | 1.21.11, 26.1.2, 26.2, 26.3 | 1.20.1 |
| Discovery, translation, the cache, the report | yes | yes, from the discovery code both Forge loaders share (`loader/forge-shared`); a translated jar's NeoForge jar-in-jar list hidden from Forge (JIJ-1), and the game's classes left alone while Forge shows a loading error (LOADERR-1) | yes, members remapped from Intermediary to SRG; the same shared code and fixes |
| Main and server entrypoints, registries open | yes; content built before them waits for them (HOLD-1, CORPUS-31), and the built-in registries' freeze comes after them (CORPUS-32) | yes, in Forge's registry events; content built before them waits for them (CORPUS-31), and the built-in registries' freeze comes after them (CORPUS-32) | yes, in Forge's registry events; content built before them waits for them (CORPUS-31), and the built-in registries' freeze comes after them (CORPUS-32) |
| Client entrypoints | yes | yes, right after the main ones in Forge's registry events, registries open | yes, right after the main ones in Forge's registry events, registries open |
| Mixins (pre-flight, its repairs, hooks left out), access wideners, injected interfaces, `@Environment` stripping | yes | yes, with MixinExtras 0.5.5 nested in the in-game mod over Forge's 0.5.4 (MIXEXT-3) | yes, with MixinExtras nested in the in-game mod, which also carries Fabric mods' hooks at a call in a constructor (CTOR-4); hooks into lambdas of the classes Forge patched found by vanilla's SRG names (LAMBDA-6), and bridged where Forge's patch made them take `this` or reordered their values (LAMBDA-7) |
| Official Fabric API jar | every module loads, server and client | every module loads, server and client | every module loads, server and client |
| Native Fabric API modules | 17 (18 on 1.21.1: particles) | 2 (`fabric-api-base`, `fabric-loot-api-v3`) | 6 (`fabric-api-base`, biome, both loot modules, dimensions, particles) |
| Bridges to the platform | fluid types, storages and energy with capabilities, plugins of other mods, custom values, renderer API | fluid types, storages with capabilities (on blocks, fluids in items), tags' `fabric:remove`, default attributes (Forge 64 to 66), advancement events (Forge 65 and 66), block break, sleep and after-damage events (BREAK-1, SLEEP-1, DAMAGE-1) | fluid types, storages with capabilities (on blocks, fluids in items), tags' `fabric:remove`, block break and sleep events (BREAK-1, SLEEP-1, SLEEP-2) |
| Networking | native, on NeoForge's payloads | upstream's module: a payload round trip with the integrated server (checked with Fabric API) | upstream's module: a channel round trip with the integrated server (checked with Fabric API) |
| Rendering (renderer API, models, Sodium and Iris for NeoForge) | yes | upstream's modules: Indigo builds chunk sections on all four (SUPER-1, LOCAL-4), Fabric's colour providers are read from Forge's map (COLOR-1), and Fabric's block dust tint event is asked where Forge 61 decides the tint (PART-2) | upstream's renderer API, Indigo, `fabric-rendering-v1` and model loading load, with Fabric's colour providers read from Forge's maps (COLOR-1), model loading's constructor hook wrapped (CTOR-4) and its before-bake hook moved to the overload Forge's code went to (MODEL-3); particles Fabricated's own, on Forge's particle engine (PART-1) |
| CI | server and client runs gated, and checks in the Fabric API runs; corpus runs informational | both server runs and the client run with the test mod gated; the client run with Fabric API informational | both server runs and the client run with the test mod gated; the client run with Fabric API informational |
| Releases | supported | experimental | experimental |

- **`main`:** NeoForge for 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3, every line stable (NeoForge tagged `26.3.0-stable`; CI
  pins 26.3.0.95 and no version is experimental any more).
- **Next** (the v0.5.0 table above): on `forge`, the corpus clients' `@Local` hooks pre-flight lets through (PRE-18) and
  Forge's retyped `featuresPerStep`, which Fabric's biome modifications and lithostitched write (CORPUS-33); the last
  entity events' hook Forge's code doesn't take, the effect
  events' hook into Brigadier, which Forge loads without mixins (block breaking, beds and the after-damage event are
  bridged since BREAK-1, SLEEP-1 and DAMAGE-1, and the use and attack callbacks need no bridge); on both, item storages
  in items (a backpack's) with Forge's item handlers on stacks, and the client run with the Fabric API jar made a gate
  once it has held for a while.
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
  PERF-2, CACHE-3 and ARCH-1 open then (CACHE-3 and ARCH-1 since fixed, `FINDINGS.md`); the design documents
  corrected where the code had moved on. CI no longer runs the
  game matrix for a change to documentation alone, and each job has a time limit.
- **Done (2026-10-27):** CI in two tiers: a quick check on each push, the full matrix on pull requests to `main`,
  release tags and by hand, the informational runs with `extended`; a push to `main` only builds; a job installs the
  game once for all its runs (*Rethink*, item 2).
- **Next:** the gates listed in the rendering-pipeline plan; checks for what the corpus mods do in the world.
- **Noted at the cleanup (task 4), for later:** the `fabricapi-overrides` copied between versions (about 875 lines;
  `import-fabric-api.sh` copies each version's alone, so identical copies need shared-overrides support there). Copies
  across platforms (the loot mixins Forge and NeoForge both have, the biome and particle code Forge 47 and NeoForge
  1.21.x share) stay per branch since each platform has its own (*Branches*); `FabricDependencyLocator`'s runs both
  Forge loaders have alike, the stand-ins in Forge's `src/fabricapistubs` and the test-mod source sets the Forge
  projects set up but never build are the Forge branches' to merge. Access widener records keep a `transitive` flag nothing acts on (every
  access transformer applies to every mod). The retired config keys (`logLevel`, `preferNativeNeoForgeMods`,
  `preferFabric`) stay accepted and ignored at 0.5.0, so older configs don't warn. Flashback's refusal
  (`compat-plan.json`, CORPUS-21) rests on NeoForge's bootstrap registry freeze, which CORPUS-32 moved after the
  entrypoints: whether Flashback still needs it is UNVERIFIED (a run with it lifted would tell).

## Findings

Open findings, by ID, with the severities [`FINDINGS.md`](FINDINGS.md) defines. A finding moves to `FINDINGS.md`,
with its cause, why the tests missed it and its fix, in the change that fixes it.

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| PRE-18 | P0 on Forge 65 and 66 clients, UNVERIFIED cause | `MixinPreflight` (MixinExtras `@Local` sugar in a method Forge patched) | ImmediatelyFast's `map_atlas_generation.MixinGuiGraphicsExtractor`, a `@WrapOperation` taking a `LocalRef`, stopped the Forge 65 corpus client ("failed injection check, (0/1) succeeded. Scanned 0 target(s)"), and Mod Menu's `MixinTitleScreen`, an `@Inject` taking a `LocalIntRef` and a `LocalBooleanRef`, the Forge 66 one ("SugarApplicationException: Unable to find matching local!"), run 40570195894. Pre-flight reported nothing for either. Which locals Forge's patched methods have there, and why pre-flight's `@Local` checks passed the hooks, is UNVERIFIED. | Next: reproduce with the two mixins against Forge 65's and 66's classes. Mod Menu stays out on Forge meanwhile (REPL-1). |
| FRAPI-5 | P1 on NeoForge 21.1 clients with Fabric API (Fabric models with their own `emitBlockQuads`) | 1.21.1 in-game mod (no counterpart of 1.21.11's `SectionCompilerMixin`) | Indigo's hook that sends a chunk section's blocks through Fabric's renderer is left out on 1.21.1 (NeoForge 21.1's `renderBatched` takes model data and a render type), and Fabricated has no bridge of its own there, so a Fabric model that isn't a vanilla adapter isn't drawn in chunk sections; NeoForge's model data and its own models are unaffected. 1.21.11's bridge is built on that version's block model parts, so 1.21.1 needs one on `BakedModel` and 0.116's `FabricBakedModel`. | Open. Decided in task 3: after the 1.21.1 port, measured on its corpus first (connected-texture and other renderer API mods). |
| PRE-19 | P2 | `MixinPreflight.overloadRepairs` (a variable modifier picking its local by its place among the locals) | Puzzles Lib's break speed hook is left out on Forge 47 (PRE-17) where it could move to `getDigSpeed`, the method Forge deprecated `getDestroySpeed` for: the overload repair moves only variable modifiers that pick their local by type alone (MODEL-3), as the other method's code could have other locals of that type first (LOCAL-4). | Move one by its ordinal where the other method adds no parameter of its type and its local variable table places the local the same, checked against vanilla's code (VANILLA-1). |
| LINK-3 | P3 | `LinkageAnalysis.unloadedUses` (LINK-2, a plugin config's mixin) | A mixin adding an interface of a mod that isn't loading disables its mod even where its config's plugin would skip it at runtime (a compatibility mixin for that mod). | Leave out that mixin only, as CORPUS-7 does during translation; needs the linkage round to feed the skip back into translation and its cache key. |
| CORPUS-33 | P0 on Forge 61 to 66 with Fabric API, UNVERIFIED (Fabric mods' biome feature modifications); P1 for lithostitched (Terralith and the mods needing it) | `ChunkGenerator.featuresPerStep` on Forge 61 to 66 | Forge makes the field a `ClearableLazy` (a `Lazy`, itself a `Supplier`, with `invalidate()` and `of(Supplier)`), where vanilla's is a `Supplier`. Pre-flight disables lithostitched, whose `ChunkGeneratorAccessor` expects a `Supplier` ("@Accessor target field featuresPerStep ... has a different type"; runs on `192c92f` and `d43a19e`), and with it Terralith and every mod requiring it. Fabric API's biome module writes the field with vanilla's type in a lambda of `BiomeModificationImpl.finalizeWorldGen`, run once a modification changes a biome's features (its access widener lines for the field are ignored, "does not exist"); that would throw `NoSuchFieldError` as the server starts. UNVERIFIED: no Forge run applies such a modification yet. | Next: an access bridge for a field the platform retyped to a subtype (a read takes Forge's lazy as the `Supplier`, a write wraps the value with `ClearableLazy.of`), for accessors and Fabric API's code; or Fabric's biome API native on Forge 61 to 66's biome modifiers, as on Forge 47. |
| CORPUS-34 | — | JEI's Fabric jar (Forge 61 to 66 corpus) | Forge's mods folder scan reads every jar's module-info; JEI's Fabric jar names packages it lacks, so Forge refuses it and loads only itself (LOADERR-1). | Not Fabricated's: JEI's Forge build is used. Left out of the Forge corpus runs (`00c3f37`). |
| CORPUS-35 | — | YACL's Fabric jar (Forge 47 corpus) | Its 3.6.6 Fabric jar carries a `mods.toml` asking for no `javafml` version, which Forge 47 refuses, loading only itself (LOADERR-1). | Not Fabricated's: YACL's Forge build is used. Left out of the Forge 47 corpus run (`00c3f37`). |
| CORPUS-36 | P2 | Farmer's Delight Refabricated (Forge 61 to 66 corpus) | Disabled: it "adds enum constants with Mixin's enum extensions, which need Mixin 0.17.1 or later; this Forge runs Mixin 0.8.7". Forge reads no enum extension file to translate them into (ENUM-1 is NeoForge's). | Not repairable on Forge's Mixin; its Forge build is used. |
| CORPUS-37 | P2 | Porting Lib's accessors module (Forge 47 corpus: Create Fabric, Farmer's Delight Refabricated) | Pre-flight disables it: accessors of fields Forge 47 retyped (`MinecraftServer.f_129740_`, `PotionBrewing$Mix.f_43532_` and `f_43534_`, and on the client `ParticleEngine.f_107293_`, PART-1's map), and with it the modules and mods needing it (LINK-2 for the base module's texture interface). | Not repaired: whether a bridge can reach each field's new type is UNVERIFIED; the mods' Forge builds are used. |
| CORPUS-38 | P3, UNVERIFIED | Fabric Language Kotlin (Forge corpus runs) | The linkage check lists `KotlinLanguageAdapter -> missing class net.fabricmc.loader.language.LanguageAdapter`, Fabric Loader's old adapter interface, which Fabricated doesn't provide. Kotlin mods load through its current adapter; whether any mod still names the old one is UNVERIFIED. | Provide the old interface if a mod needs it. |
| HOLD-2 | P0 on Forge, UNVERIFIED | Forge 61 to 66's and Forge 47's `MappedRegistryPendingHoldersMixin` and `NamespacedWrapperPendingHoldersMixin` | CORPUS-42's drop of the intrusive holders a failed Fabric entrypoint left is NeoForge's only: on Forge, content a mod built before its entrypoint failed would still stop the freeze. No Forge run has shown it. | Port the drop into their shared implementation with the next Forge pass, or once a run shows it. |
| SER-1 | P0 on NeoForge 21.11, UNVERIFIED trigger | `EntityDataSerializers.registerSerializer` on NeoForge 21.11 (and 26.x, unread) | NeoForge 21.11 refuses mods' calls to vanilla's method as 21.1 does (read in its sources); CORPUS-41's hook is 1.21.1's only, as Fabric API has a serializer registry on 1.21.11 that maps to NeoForge's. Whether any Fabric mod for 1.21.11 still calls vanilla's method is UNVERIFIED. | Move the hook to `common121` once a run shows such a mod. |
| CORPUS-44 | P0 on NeoForge 21.1 with Moonlight (1.21.1 corpus server) | `fabric-resource-loader-v0` on 1.21.1 (Fabricated's own, standing for v1) | Moonlight's `FilteredResManagerImpl` uses Fabric API's internal `net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack`, which Fabricated's resource loader doesn't have; the linkage check warns, and the server stops with `ClassNotFoundException` as resources load (run 40683010866). | A known limit of v0.5.0-alpha: provide the class's API on Fabricated's packs, or leave out what needs it. |
| CORPUS-45 | P0 on NeoForge 21.1 clients with LambDynamicLights, UNVERIFIED cause | LambDynamicLights' `CommonLevelRendererMixin` (`lambdynlights_runtime`) | The 1.21.1 corpus client stops as Mixin applies its `@ModifyReturnValue` on `LevelRenderer.getLightColor`: the handler fits the three-argument overload, but Mixin applied it to the two-argument one (run 40683010866). Upstream's selector names the three-argument overload with its descriptor, and NeoForge 21.1 doesn't patch either; what made it match the other is UNVERIFIED (the job log's tail holds no pre-flight report for it). | A known limit of v0.5.0-alpha: needs a run that keeps the client's report. |
| CORPUS-46 | P0 on NeoForge 21.1 (advanced corpus) | Pehkui's `reach.PlayerEntityMixin` (`pehkui.mixins.json`), `MixinPreflight` | Past CORPUS-43 the 1.21.1 advanced corpus server and client stop as Mixin applies Pehkui's reach mixin: "Critical injection failure: Callback method pehkui$attack$distance(D)D ... failed injection check, (0/1) succeeded. Scanned 0 target(s)" (run 40762508369). A required injector that finds no target on NeoForge 21.1, which pre-flight should leave out or disable its mod for; why pre-flight passed it is UNVERIFIED. | A known limit of v0.5.1-alpha. |
| ENTRY-1 | P2, UNVERIFIED on real mods | `EntrypointBridge` (a mod marked failed) | A Fabric mod whose main initializer fails after it registered a server configuration task can leave a singleplayer player waiting to join: the client initializer, which registers the client's side, doesn't run for a failed mod (the test mod with Forgified Fabric API on 1.21.1 before PROVIDER-6, run 40762508369: "Waiting for player to load" until the run's time limit). The report names the failure, not the wait. | Open: running a failed mod's other entrypoints, or dropping what it registered, each have a cost; to decide. |
| PROVIDER-5 | — | Forgified Fabric API's `fabric-networking-api-v1` (1.21.1 seen) | With it providing the module, NeoForge refuses a payload type in the `minecraft` namespace, which Fabricated's own networking registers (NET-4): the Fabric mod's initializer throws "Cannot register payload minecraft:... using the domain \"minecraft\"" and it is marked failed (the test mod's payload on the 1.21.1 client run, 40738724928; BetterEnd's ritual update UNVERIFIED). | Not Fabricated's: the module is the provider's (*Decisions, 2026-10-28*). The test mods skip that case when another mod provides networking. |
| PROVIDER-7 | — | Forgified Fabric API's `fabric-item-api-v1` on 26.x (26.1.2 and 26.2 seen) | Its `IItemExtensionMixin` answers NeoForge's `getEquipmentSlot` for an item with Fabric's equipment slot provider by casting the item to `LivingEntity` (`(LivingEntity) this`, read in its 26.1.2 source), which throws `ClassCastException`: the test mod's item stopped the servers with Forgified Fabric API once PROVIDER-6 let its initializers run (this change's CI, run 40772701272). A Fabric mod's item with an equipment slot provider (`FabricItem.Settings.equipmentSlot`) would stop the game wherever NeoForge asks its slot. | Not Fabricated's: the module is the provider's (*Decisions, 2026-10-28*); worth reporting to Forgified Fabric API (the owner's call). The 26.x test mod skips its item checks when another mod provides the module. |
| GSON-2 | P0 on Forge 61 to 66 and Forge 47, UNVERIFIED | `ProposedJars.install(loader)` on the Forge branches | GSON-1's cause, on the Forge platforms: their containers call the one-argument `install`, which goes to the game loader's parent, while Forge's ModLauncher makes the fallback its boot layer's loader, as NeoForge 21.1's does. | Open: the Forge branches' containers need `install(loader, Fml.gameFallback(loader))`, at their next sync with `main`. |
| KEYMAP-1 | P2 | Fabric mods' accessors to `KeyMapping.MAP` (OneConfig's keybind registry) | NeoForge retyped the field to `KeyMappingLookup`, so a `Map` accessor is left out and its callers throw `NoSuchMethodError` (OneConfig catches it; its keybinds may shadow Minecraft's). | Open: a `Map` view of NeoForge's lookup, served for such an accessor, would match Fabric; built if more mods than OneConfig need it. |
| PLAT-2 | P3, UNVERIFIED | core `PlatformLibraries` | Other prefixes also cover whole library families (`org/lwjgl/`, `io/netty/`), of which the platform may ship some modules only: a mod nesting another module (LWJGL's NanoVG, say) would lose it, as PLAT-1 did. No case seen yet. | Check each prefix against the packages the platform really has (the loader computes them), and keep a prefix only where the family is complete. |
| FC-5 | P3 | `TranslationCache.verifyIntegrity` | Hashes are memoised by file metadata. On Windows the key is size and mtime only, and `entry.json` itself isn't hashed. | Needs a decision on cost: re-hashing every launch versus trusting metadata. |
| FC-6 | P3 | `TranslationCache.store` | Two cold launches of the same side that share one game directory race on delete-then-move. | Needs a cross-process file lock. Rare. |
| FC-7 | P3, UNVERIFIED | cache cleanup | `Files.walk` may follow Windows directory junctions. | Must be verified on Windows first. |
| P3-2 | P3 | `LootBridge` | One global reload manager. Two overlapping reloads can label a loot table with the wrong source. | Low impact. Needs a per-reload key. |
| P3-8 | — | `BlockEntityTypeMixin` | Field replaced without `volatile`. | Won't fix: written only during mod initialization, before world threads start. |
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
| `fabricated-testmod-frapi` (this repo) | `ModelLoadingPlugin` after-bake modifier, its own `emitQuads` in sections, as a falling block and with the breaking overlay; a `fabric:type` model as a multipart part | — | **passes** on 26.1.2, 26.2, 26.3 | CI "Fabric renderer API and model formats (26.x)", run 246 |
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

- **NeoForge 26.2 and 26.3 don't build locally**: NeoForge's source transformer (JST 2.0.10) never applies an access
  transformer line to an anonymous class, here NeoForge's own `HolderSet$1 contents()`, and recompiling the game fails
  (26.1.2 makes the method public with a source patch instead). CI builds both from the same inputs; why it differs is
  UNVERIFIED. Locally, verify on 1.21.11 or 26.1.2, and let the quick check build 26.2 and 26.3.
- **Renderer API gate: passes** on 26.1.2, 26.2 and 26.3 (runs 236 and 237), and on 1.21.11 for chunk sections (runs
  365 and 366). On 26.3 the log shows the pre-flight
  moving `ModelBakeryMixin#onReturnInit` to NeoForge's constructor, then `frapi emitQuads called (level
  RenderSectionRegion, thread Worker-Main-2)` and `frapi terrain OK`: the Fabric model was drawn by a chunk-section
  compile worker.
- **Mixin's selection of the first match for a name-only selector** is the basis of `08aff28`. It is confirmed by
  run 229's crash (`SectionCompilerMixin`, "Scanned 0 target(s)"), by run 233's gate failure and by its passing once
  the selector was moved.
- UNVERIFIED: which Mixin behaviour version NeoForge 21.1's FML 4 applies a config at (`mixin_lowest=0.16.4` in
  `mc/neoforge/1.21.1/gradle.properties` assumes its own Mixin's), and the one Forge 47 accepts (`0.8.5`).
- UNVERIFIED: FC-7 (junctions), PH2-13's likelihood, whether Athena has Fabric builds for these versions (Continuity
  has one for 1.21.11: the corpus loads it), and an in-game allocation baseline for the meshing path.
