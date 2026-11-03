# Fabricated: roadmap

Read [`claude.md`](claude.md) first. Fabricated has no finish line: it keeps adding support and refactoring as Fabric,
NeoForge, Forge and the mods move. This file looks forward: the current pass and the next release's goals, how the
project fits together, where each platform and track stands and what comes next, the open findings, the test matrices
and what is still unverified. It changes in the same change as the work and stays current rather than growing: what
happened goes to [`CHANGELOG.md`](CHANGELOG.md) (a dated entry each working session), a fixed finding moves to
[`FINDINGS.md`](FINDINGS.md), and each port's step-by-step record is in [`PORTING.md`](PORTING.md) §7.

## Vision

Fabric mods run on NeoForge and Forge natively, securely and fast: translated once when the game starts (and cached),
checked before they run (mixin pre-flight against the platform's patched code), and connected to the platform where it
owns a system, with no dependency but the official Fabric API jar. The aim is the best structured, lightest and most
maintainable way to do this, held to the rules in `claude.md`: every claim checked against source or CI, untrusted jars
handled safely, no network access, original jars never changed, and no code that a CI check doesn't exercise.

The yardstick is what Sinytra Connector couldn't run (the owner, 2026-11-01): renderer replacements and shader
pipelines (Sodium, Iris), mods that load other mods (Essential), Fabric-only interface stacks (OneConfig, PolyPlus,
Polydium). Fabricated makes them run with generic capabilities, and refuses only what can't run safely or correctly,
saying why. A mod is never refused for being hard.

## How the project fits together

Rethought on 2026-10-26, when the three platforms came onto one working branch, on 2026-10-28, when each went to a
branch of its own, and on 2026-11-01, for the beta (*Standing decisions*).

**One engine, three platforms, a branch each.** Whatever can be decided without running the game is the engine, and every
platform shares it: `core` (archive safety, metadata, dependency resolution, remapping, access wideners, mixin
pre-flight and its repairs, the linkage check, the cache, the report), `fabric-loader-api`, `api` and `loader/common`.
A platform adds only what its loader decides: its discovery services (`loader/<platform>`) and, per Minecraft version,
the in-game mod, its native Fabric API modules and its Fabric API plan (`mc/<platform>/<version>`); what the engine
needs to know of a platform is its `PlatformDescriptor`. NeoForge is the primary platform; Forge 61 to 66 and Forge
47 use the engine unchanged, and from 0.6.0-beta are held to the same stability bar.

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

## Standing decisions

The owner's decisions that still hold, newest first. Their reasoning is in `CHANGELOG.md` under each date, and the
decisions about NeoForge, FML and Fabric facts in `RESEARCH.md` §8.

- **2026-11-01, the v0.6.0-beta pass** (next section): every platform reaches beta, held to one stability bar; Fabric's
  renderer API keeps Fabric API's renderer, and Fabricated builds the seams; the difficult mods' Fabric builds are made
  to run.
- **2026-10-28, packs shared with Sinytra's ecosystem.** Every package Fabricated adds has one owner, decided before
  the platform builds its module layer; a conflict is resolved, or the mod disabled and explained, never left to a
  `ResolutionException`. A Fabric API module another mod provides (Forgified Fabric API, NeoForge mods bundling
  modules) is that mod's, and Fabricated's copy stands down with its mixins and bridges (PROVIDER-1). Fabric Loader's
  API is always Fabricated's, and another jar's copy is left out (PROVIDER-2). A library the platform or a NeoForge mod
  has (Kotlin) is theirs (`PackageGuard`). Sinytra Connector and a second Fabricated jar are refused with an error
  naming the jars (DUP-1). Considered and not done: running beside Connector, keeping Fabricated's modules over
  Forgified Fabric API's, shipping Forgified Fabric API, and changing what its modules do differently (PROVIDER-5,
  PROVIDER-7).
- **2026-10-28, a branch per platform**: `main` (the engine and NeoForge), `forge` (Forge 61 to 66), `forge-legacy`
  (Forge 47). Shared changes reach the Forge branches by merge, never back (`claude.md`, *Branches*).
- **2026-10-27, how work runs**: local verification first; one pull request per task; CI in two tiers (a quick check on
  each push, the full matrix on pull requests to `main`, release tags and by hand, the informational runs with
  `extended`); documentation per task. Forge parity comes through Fabric API's own modules and targeted bridges, not
  ports of NeoForge's native modules, and test stand-ins grow only by what a check calls.
- **2026-10-21**: the shared modules stay loadable on Java 17 (D2); commits batch a concern, not a file.
- **2026-10-19**: a pull request is merged once CI is green on its final commit.

## The v0.6.0-beta pass (2026-11-01)

The owner's brief: leave alpha. Every branch and Minecraft version reaches beta quality, meaning a feature works
reliably and is documented, or it is cut or marked unsupported; nothing ships as "experimental". The pass also starts
serious work on Fabric's renderer API (FRAPI) and pushes general compatibility as far as it goes. Seven tasks, in order,
each finished before the next. This section is the pass's checkpoint: it changes with every step, so the work can
resume from it.

| # | Task | Status |
|---|---|---|
| 1 | Rethink the project and the spec: the conclusions, the beta criteria, the FRAPI and difficult-mods plans (below) | Done |
| 2 | CI and workflows: the matrix from what a change touches, caching, fewer game boots per job, with times before and after | Done (#236, #237, #238) |
| 3 | Parity on every platform and version, and the crashes v0.5 left open | Done (#239, #240, #241); PRE-18's check on Forge carried to task 5, behind CORPUS-53 |
| 4 | Fabric's renderer API on NeoForge and Forge (*FRAPI plan*) | Done (#243, #244, #245: R1 to R7; FRAPI-5 to FRAPI-9 and PACK-6 fixed); R8 stays a candidate |
| 5 | General compatibility, the difficult mods' Fabric builds first (*Difficult mods*) | In progress (#247 to #253 on main; #254 and #249 on the Forge branches): the difficult mods' Fabric builds load and are gated on NeoForge; the Forge corpus stops read and fixed one by one (CORPUS-70 to 79) |
| 6 | Optimization, cleanup and bug fixes, no features | |
| 7 | Release v0.6.0-beta | |

### The owner's decisions

1. **Beta on every platform.** NeoForge, Forge 61 to 66 and Forge 47 meet the same stability bar (*Beta*, below).
   Their coverage may differ: what a platform lacks is listed as unsupported in its table, with the reason. Forge's open
   P0s (PRE-18 until it is checked, CORPUS-53 to 55) block the beta as NeoForge's do.
2. **FRAPI on Fabric API's renderer.** The renderer a mod registers draws (Fabric API's Indigo by default; Sodium's or
   Forgified Fabric API's when they're installed), and Fabricated owns the seams between it and each platform's
   pipeline. No second renderer or quad encoder.
3. **The difficult mods' Fabric builds run.** Sodium, Iris, Essential, OneConfig and PolyPlus, and mods like them, are
   made to run from their Fabric jars: the project exists to fix what Sinytra Connector couldn't. The compatibility
   plan's refusals (`neoForgeBuildOnly`) are lifted one at a time, each once its mod's CI run is a gate; a refusal
   stays only where a mod can't run safely or correctly, with the reason.

### Rethink: the design against the four goals

Checked again for drift, over-engineering and dead ends, against v0.5.1's runs (a pull request's full matrix, run
40795950758, and the release tag's extended run, 40800647792).

- **Light**: holds. A jar is translated once and cached, and the engine has no runtime dependency. Open: PERF-2's
  re-reads on a cached launch (task 6). The process is the heavier side: a NeoForge job boots the game up to 16 times
  (five servers, and up to eleven clients on 26.x), each boot taking about 40 to 70 seconds, and a shared change takes
  three pull requests to reach every branch.
- **Robust**: holds before mod code runs (pre-flight, LEAVE-1, the dependency graph). After it runs, rendering is the
  gap: a Fabric model that throws while it emits stops the game, as the game turns an exception in a section compile
  into a crash (to confirm with a test in task 4, FRAPI rule 8 below). ENTRY-1 is the other gap (a failed mod can leave
  a join waiting).
- **Safe**: holds (`SafeArchive`, SEC-1 to SEC-5). Decision 3 adds one case, a mod that downloads code as it runs
  (Essential's loader): Fabricated itself still never touches the network, and what such a mod adds goes through the
  same pipeline as a jar in `mods/` (the archive checks, pre-flight, the package guard), or isn't loaded.
- **Fast**: only translation is measured. The launch baselines and PERF-1 are task 6's; the FRAPI hot-path rules hold.

What changes:

1. **History left this file.** The rethinks of 2026-10-27 and 2026-10-28, their decisions and the v0.5.0 plan were
   history: what still holds is in *Standing decisions*, the rest in `CHANGELOG.md`.
2. **"Experimental" goes.** It meant three things: the Forge builds (held to the beta bar, decision 1), a matrix flag
   in `build.yml` no version sets (removed in task 2), and the refusals of the difficult mods' Fabric builds, with
   `loadUnsupported` as the switch to test them (decision 3; the switch stays for the player, for whatever is still
   refused).
3. **Every informational CI run becomes a gate or goes.** Thirteen runs are informational: two on every pull request
   (the client with Fabric API, whose log later steps gate, and 1.21.1's client with Forgified Fabric API and Sodium for
   NeoForge) and eleven with `extended` (the newest NeoForge build, Sodium for NeoForge on 1.21.11, the shader pack, the
   Fabric builds of Sodium and Iris, Sodium addons, OneConfig and PolyPlus, malilib, and both corpora on a server and a
   client). One that holds on two extended runs in a row on the same code becomes a gate (CI-1); one that tests nothing
   the beta promises is dropped.
4. **Forge gets the checks NeoForge has.** No Forge job runs the renderer test mod or gates the client with Fabric API
   (tasks 3 and 4), and the Forge loaders don't yet call the provider policy and the refusals the engine has (task 3).
5. **GSON-2 was fixed** on both Forge branches (#233, #234): it is in `FINDINGS.md` now.
6. **Dead ends dropped:** the renderer contract's every-block routing (no consumer; FRAPI-3 won't fix) and a
   Fabricated-owned quad encoder (decision 2). **Reopened:** code for one mod's Fabric build, which the
   rendering-pipeline track ruled out for Sodium. Decision 3 allows it where no generic capability does the job, as a
   compatibility patch (*Difficult mods*).
7. **Nothing removed for over-engineering now.** The candidates are task 6's: the `fabricapi-overrides` copies, access
   wideners' unused `transitive` flag and the retired config keys (*Quality*, below).

Each item still experimental or undecided, with its decision:

| Item | Decision | Task |
|---|---|---|
| The Forge 61 to 66 and Forge 47 builds | Beta, their tables listing what's unsupported | 3 |
| The Fabric builds of Sodium and Iris | Done on NeoForge (#247): loaded by default and gated on every version, Iris's but on 1.21.1 (pre-flight refuses its particle hook, which NeoForge's code doesn't make); with a shader pack informational. Refused on Forge until a Forge run gates them | 5 |
| Essential's Fabric build | Done on NeoForge (#247, DOWNLOAD-1): what its loader downloads in one launch loads from the next, without Essential's automatic updates; both launches gated on every version. Refused on Forge | 5 |
| malilib and the mods needing it | Done on NeoForge (#247): malilib, MiniHUD, Tweakeroo and Litematica gated on every version. Refused on Forge | 5 |
| Flashback | Done on NeoForge (#247): its freeze hook waits for the main entrypoints (REG-8); gated on every version. Refused on Forge | 5 |
| WTHIT's Fabric build | Stays refused: its own entrypoint refuses NeoForge, and WTHIT's NeoForge build reads the same plugin files | — |
| Login queries (`ServerLoginNetworking`) | Stay unsupported (decided in task 5, 2026-11-02): NeoForge has no hook in the login phase, and the configuration phase can't stand in for it, as their main users (a proxy's player forwarding) answer the login itself. The API links and its receivers are never called, as `COMPATIBILITY.md` says; a corpus mod needing them would reopen it | 5 |
| `preLaunch`, run with `main` | Stays where it runs, first of the entrypoints (decided in task 5, 2026-11-02): running it at the game's start would load the mods' classes before NeoForge builds their containers, which set the class loaders Fabricated loads them with, and buys the mods it was for nothing: Sodium's driver checks act before a GL context NeoForge's early window has already made (item 9 below), and Essential's loader is DOWNLOAD-1's. A mod that needs it earlier would reopen it | 5 |
| Fabric models in 1.21.1's chunk sections (FRAPI-5) | Done (`9e3a1f0`, gated) | 4 |
| Data generation, game tests | Kept: data generation passes through and generates nothing unless asked; game tests are development-only and unsupported | — |

### Beta: what 0.6.0-beta means

Redefined on 2026-11-01 (decision 1), from the definition of 2026-10-28. Alpha meant the design could still move under
a pack; beta means it doesn't: the targets are set, and the work to 0.6.0-beta is closing faults and turning runs into
gates. Work that serves none of the criteria below goes into a track for after the beta.

**Platforms.** NeoForge (1.21.1, 1.21.11, 26.1.2, 26.2, 26.3), Forge 61 to 66 (1.21.11, 26.1.2, 26.2, 26.3) and Forge
47 (1.20.1) are all beta, each from its own branch. Coverage may differ: each platform's table (`COMPATIBILITY.md`,
`docs/FORGE.md`, `docs/FORGE-LEGACY.md`) says what works, from CI, and marks the rest unsupported with the reason.
"Preview" and "experimental" leave the documents.

**Stability bar**, on every platform:

1. **No launch stops for Fabricated.** A Fabric mod it can't run is disabled before it runs and the report says why.
   The only launches Fabricated stops are its refusals (Sinytra Connector, a second Fabricated jar), each with an error
   naming the jars. A mod's own fault, or another mod's module's (PROVIDER-5, PROVIDER-7), is that mod's, and the report
   names the mod.
2. **A Fabric mod that fails after it runs stays isolated where the platform lets it:** a failed initializer is marked
   failed and the game goes on (`onEntrypointFailure`), and a Fabric model that throws while it is drawn is drawn by
   the platform's renderer from then on (FRAPI rule 8).
3. **No open P0 or P1**, by `FINDINGS.md`'s severities, UNVERIFIED ones included: a finding is fixed, or narrowed to
   "the mod is disabled and the report says why" with the evidence that lowers its severity.
4. **No open finding on untrusted input.** SEC-5's limits on nested jars hold across all the jars of a launch, not per
   jar.
5. **Upgrades don't break a game directory.** A cache from an older build is translated again (the schema is in the
   cache key), and a retired config key stays accepted and ignored.
6. **The developer API (`docs/API.md`, 1.x) is stable.** A break needs the owner's decision, a major API version and
   a changelog entry.

**Compatibility targets.** Each one is a CI check on every version of its platform, as a gate:

| Target | NeoForge | Forge 61 to 66 | Forge 47 |
|---|---|---|---|
| The test mods, with and without Fabric API, server and client | gates | gates (task 3) | gates (task 3) |
| Fabric API's client modules (key mappings, HUD, screens, client commands) | gates on 26.x and 1.21.1; 1.21.11's stand-ins lack them | gates on 26.x (the client API test mod); on 1.21.11 the forge test mod's key binding and HUD checks | the forge test mod's HUD callback and screen input checks |
| Fabric's renderer and model APIs (the renderer test mod: sections, nested models, moving blocks, the breaking overlay, items, blocks drawn as entities, broken models) | gates on every version (task 4) | gates on every version (task 4, R5), minecarts' blocks included; Forge 66's glow outline informational | gates (task 4, R5), a piston's block included; blocks drawn as entities keep vanilla's quads, as upstream on 1.20.1 |
| Sodium and Iris for NeoForge | gates on 26.x; 1.21.11 informational | — (no Forge builds) | — |
| The Fabric builds of Sodium and Iris, with a shader pack | gates on every version without the shader pack (Iris's but on 1.21.1); with it informational, not on 1.21.1 | refused; after NeoForge, where they have builds (task 5) | as Forge 61 to 66 |
| malilib with MiniHUD, Tweakeroo and Litematica; Essential's Fabric build; Flashback | gates on every version (task 5) | refused, not run | refused, not run |
| OneConfig and PolyPlus | informational (they reach a world on 26.3 and 1.21.1) | not run | not run |
| Packs sharing the game with Forgified Fabric API and Sodium for NeoForge | the server a gate, the clients informational | Forgified Fabric API refused (PROVIDER-2), coexisting tried in task 5 | as Forge 61 to 66 |
| Popular Fabric mods: both corpora reach a world, every mod not loaded named with a reason | informational; 1.21.1's servers start since CORPUS-61's fix (extended run 41512581949), its client stops in its world on Puzzles Lib's screen effect hook (CORPUS-54, fixed, to verify) | informational; every corpus stops before a world (CORPUS-53, CORPUS-54) | informational; stops before a world (CORPUS-54, CORPUS-55) |
| The owner's reference pack (1.21.1: Forgified Fabric API, Sodium, OneConfig, about 130 Fabric mods) | the owner's check before the release (not in CI) | — | — |

A run becomes a gate once it holds on two extended runs in a row on the same code (CI-1).

**Speed and weight.** A launch's cost is measured and kept: a cold and a warm launch per version in *Performance
baselines*, the warm one with PERF-2's re-reads gone. The beta's numbers become the bar later releases are held to; a
change costing more than 10% on either says why in its pull request.

**Ship criteria for 0.6.0-beta:**

1. The stability bar holds on every branch's release head.
2. Every target above is a gate and green on those heads, on every version.
3. The full matrix is green on `main`, `forge` and `forge-legacy`, on two extended runs.
4. `COMPATIBILITY.md` and the Forge tables match the release heads' CI, and the release notes say what changed since
   v0.5.1-alpha on each platform (FRAPI's status included) and what to do on upgrade.
5. The owner chooses the version and approves the tag, as for every release.

**What blocks it** (2026-11-02, task 4 done): on Forge, PRE-18 until a corpus client checks it,
which CORPUS-53 now stops first (CORPUS-33, LAMBDA-8 and HOLD-2 are fixed, and task 3 fixed PACK-5, KEYMAP-3, HUD-2,
FUEL-1 and BREW-1 on the way); everywhere, SEC-5, the targets not yet gates and the launch baselines. The corpus
findings task 5 takes (CORPUS-50 to 55 among them) block only where a run shows them on a mod the beta names, and the
corpus target is a gate only once every corpus run reaches a world.

### FRAPI plan (task 4)

What exists is in *Rendering* (below) and `claude.md` (*Fabric's renderer API*). On NeoForge 26.x and 1.21.11, Fabric
models are drawn in chunk sections, nested in vanilla multipart and weighted models, as moving blocks and with the
breaking overlay, through the registered renderer, with NeoForge-aware defaults. On Forge 61 to 66 and Forge 47,
upstream's own hooks take Forge's code where they can (SUPER-1, LOCAL-4), and no renderer test checks them. The design
rules 1 to 7 in `claude.md` hold; the plan adds two:

8. **A broken FRAPI consumer fails alone.** Every seam that calls into a Fabric model or renderer (sections, moving
   blocks, the breaking overlay, the parts made from a model's quads) catches what it throws. That model's class is
   then drawn by the platform's own renderer, from its parts, and the failure is logged once and listed in the report,
   naming the mod the class belongs to. A `ClassValue` holds the flag, so the healthy path pays nothing; quads emitted
   before the throw stay in that one build of the section. Where upstream's hooks call the renderer themselves
   (Forge), the same guard wraps Indigo's entry points, in a mixin config loaded only with Indigo.
9. **Every seam has a check on every platform it exists on**, in the renderer test mod, before it is called supported.

Work items, by how many Fabric mods they reach:

| # | Item | Where | Check |
|---|---|---|---|
| R1 | Rule 8 on every seam | NeoForge, every version; Forge, at Indigo's entry points | The renderer test mod gets a model that throws on its tenth emit: the client keeps running, draws the block from its parts and logs it once |
| R2 | FRAPI-5: Fabric models in 1.21.1's chunk sections, as 1.21.11 has them, on Fabric API 0.116's API (`FabricBakedModel`, `RenderContext`); then moving blocks and the breaking overlay | NeoForge 1.21.1 | The renderer test mod's 1.21.1 build, with the same terrain, nested, moving block and overlay checks |
| R3 | Fabric item models: in screens, in hand, as dropped items and in item frames | NeoForge, every version (upstream's item hooks are unchecked on 26.x, and NeoForge rewrote item rendering on 1.21.x) | An item with a Fabric model, drawn in a screen and in the world, logs that it emitted |
| R4 | Blocks drawn as entities and by block entities (a minecart's contents, an enderman's block) | NeoForge 26.x (1.21.11 has them from the parts) | A minecart carrying the terrain block |
| R5 | The renderer test mod on Forge, with the fixes it shows | Forge 61 to 66, Forge 47 | NeoForge's checks, gated |
| R6 | Real FRAPI consumers in CI: Continuity (connected textures through the renderer API) and the corpus's other users of it, with their resource packs | every version they have builds for | Informational, then gated by CI-1's rule |
| R7 | Another registered renderer, Forgified Fabric API's, beside Fabricated's seams | NeoForge 1.21.1 and 26.x | The coexisting client's renderer checks (whether the seams then draw a block twice is UNVERIFIED) |
| R8 | Fabric's level-aware `materialFlags` where NeoForge asks whether a block is translucent | NeoForge 26.x | A candidate only: Fabric's contract allows today's answer (*Rendering*) |

**Speed.** No seam allocates per block or per quad (rule 1); R1's guard is a `try` around the existing call and a
`ClassValue` read. Each item's commit states its hot path. An allocation probe in the renderer test mod (bytes the
chunk workers allocate over a fixed number of section rebuilds, with and without Fabric models) is added if it holds
between runs; otherwise rule 7 stands as it is.

**Status (2026-11-02).** Done on every platform (#243 for NeoForge, #244 and #245 for Forge), every check a gate
unless said:

- **R1:** rule 8 on every NeoForge seam (`FrapiFailures`, `44d841b`): a model that throws in a section, as a moving
  block, with the overlay or as an entity's block is drawn by NeoForge from then on, logged once and in the report,
  and the transform it left pushed reaches no other block. A throw is the model's whose code threw, not its wrapper's
  (FRAPI-7, `46383c0`, and on 1.21.1 `dd65318`, found by R6): Continuity's models wrap others.
- **R2:** FRAPI-5 on 1.21.1 (`9e3a1f0`), with 1.21.1's renderer checks.
- **R3:** Fabric item models in the hotbar, in hand, dropped and in item frames, on every version (`eaf35e7`): no fix
  needed. 26.x's API gives no call as the quads are drawn, so its check logs as they are emitted.
- **R4:** blocks drawn as entities on 26.x (FRAPI-6, `8f114da`); 1.21.11 draws them from the parts. On 1.21.1 a
  minecart's block comes from vanilla's quads, as upstream on Fabric draws it, so a model with only Fabric quads is
  invisible there, as on Fabric.
- **R6:** Continuity in CI on every version it has a build for (`a8f1c38`), informational: no connected textures seen yet
  (run 41404975032). On 1.21.1 Continuity's model draws the test's glass through Fabricated's seam (FRAPI-7 had
  switched its models off), with vanilla's sprite; on 1.21.11 and 26.x its sprite hook is left out and the check sees
  vanilla's glass model. Both are CORPUS-56, task 5. Found on the way: PACK-6 and FRAPI-7.
- **R7:** with Forgified Fabric API's renderer (`a8fbe84`, extended runs), Fabricated's seams stand down on 1.21.1,
  26.1.2 and 26.2 (every block logs `seam=other`, extended run 41400541531); it has no 1.21.11 or 26.3 build. Its own
  modules' answers there are its own (the test mod's checks of them fail, informational).
- **R8:** stays a candidate.
- **R5:** the renderer test mod on both Forge branches, its checks gated on every Forge version (runs 41421652876
  and 41423804163). Rule 8 at Indigo's entry points on Forge 61 to 66 (`4169249`), where upstream's hooks draw every
  block; FRAPI-8 on Forge 61, whose moved code left Fabric models undrawn as moving blocks, with the overlay and as
  entities' blocks; FRAPI-9 on Forge 47 (`b020730`), where Fabric models were invisible, as FRAPI-5 on 1.21.1, now
  drawn through Indigo in chunk sections, falling, moved by a piston, with the overlay and through `renderBatched`. The guards load only with
  Fabric's renderer API (`FrapiMixinPlugin`, `a46fbae`). Forge 47's blocks drawn as entities keep vanilla's quads,
  as upstream on 1.20.1, and its overlay needs no flicker fix (Forge's own). Forge 66's glow outline stays
  informational.

### Difficult mods (task 5)

Decision 3. The rules of the rendering-pipeline track hold (generic capabilities first; no code copied from a mod;
nothing on a per-block or per-vertex path allocates or locks; a check before "supported"), with one change: where no
generic capability can do the job, Fabricated may carry a compatibility patch for one mod, as data or as a patch mod
(`API.md`, *Compat patches*), written from the mod's public sources, never copied from them. Before such a patch ships
for a mod under a restrictive licence (Sodium's PolyForm Shield), the owner settles the licence question (open).

Where each stands, and what it needs:

| Mod | Today | Needs | Gate |
|---|---|---|---|
| Sodium (Fabric build) | Loads by default on NeoForge (`8cf0106`), a gate on every version (`547e2c6`): the client reaches a world, the renderer test mod's checks pass under it and Sodium's renderer draws Fabric's block models. Fixed on the way: CTOR-5 (1.21.1's quads), STATE-1 (Fabric API's fluid sprites), OVERLOAD-2 (1.21.11), FRAPI-10. Refused on Forge until a Forge run gates it | What its Fabric platform code doesn't answer the NeoForge way, the gaps `COMPATIBILITY.md` lists (decision 2026-11-02: no patch): NeoForge's fluid renderers (through Fabric's fluid render handlers), the geometry NeoForge mods add to sections (`AddSectionGeometryEvent`), position-aware light emission and face culling, baked colours in immediate mode (*Rendering-pipeline mods*) | A gate on every version; a NeoForge mod's fluid and section geometry in view not yet checked |
| Iris (Fabric build) | Loads with Sodium's by default on NeoForge, a gate on 1.21.11 and 26.x. On 1.21.1 pre-flight refuses its particle hook (a `@Redirect` of `ParticleEngine.RENDER_ORDER`, which NeoForge's `render` doesn't read) and disables it, with the reason. Fixed on the way: TOKEN-1 (1.21.11's access widener), the accessor replacing Sodium's own method (`f696c80`). With CI's shader pack: informational | A shader pack drawing with NeoForge's own render pipelines; on 1.21.1, a place for its particle hook | The shader pack client a gate once it holds (CI-1) |
| The mixed pairs (Iris for NeoForge with Sodium's Fabric build, and the reverse) | Refused even with `loadUnsupported` | Nothing: each hooks classes only its own partner build has, so it can't link | Kept refused, with the reason |
| Essential (Fabric build) | Loads on NeoForge (DOWNLOAD-1): its loader downloads Essential in the first launch, and Essential loads from the next, as a regular mod without its automatic updates (a jar older than 30 days is reported). Both launches a gate on every version. Fixed on the way: MIXEXT-4 (its relocated MixinExtras), REMAP-4 (Elementa). Refused on Forge | Loads from its download in the next launch (the owner's decision, 2026-11-02) | Done on NeoForge |
| OneConfig, PolyPlus, Polydium | Reach a world on every version with builds, no mod disabled (extended run 41512581949; CORPUS-47). adventure-platform-fabric loads with adapters for its two library mixins (LIB-2, verified there), and with it OneConfig's chat event; OneConfig's keybinds can't reach `KeyMapping.MAP` (KEYMAP-1, P3, documented). Polydium's deferred sky (`@WrapMethod` of a `LevelRenderer` lambda NeoForge gives other parameters, PRE-6), its render thread stack (a library class) and its two Vulkan pipeline hooks (26.2) are left out | KEYMAP-1's `Map` view, if built; a captured-values bridge for a `@WrapMethod` of a lambda, as `@Inject`s get (LAMBDA-3) | The Polyfrost client on every version with builds, adventure-platform-fabric loaded |
| malilib, MiniHUD, Tweakeroo, Litematica | Load by default on NeoForge, a gate on every version (Litematica joined the run). Refused on Forge | — (Litematica's schematic rendering hooks apply on 26.2 through a captured-values bridge since LAMBDA-9's fix, extended run 41512581949) | Done on NeoForge |
| Flashback | Loads by default on NeoForge, a gate on every version: REG-8 holds its registry freeze hook until the main entrypoints, NET-9 gives it Fabric API's untracked listener marker, SELF-2 keeps its enum mixin. Refused on Forge | — | Done on NeoForge |

The owner's reports of errors, mixin conflicts and mods clashing across Fabricated's versions are the other input: their
logs become findings, and the corpora grow by the mods they name.

### The rest of the pass, by task

- **Task 2 (CI).** The baseline: the quick check takes about 5 minutes; a pull request's full NeoForge matrix about 11.5
  minutes of wall time (the core job 1 minute, then five version jobs in parallel taking 7.5 to 10.3 minutes, run
  40795950758); a release tag's extended run about 22 minutes (version jobs of 16 to 21 minutes, run 40800647792).
  The Forge workflows: a pull request to `forge` about 7.5 minutes (the core job 1.3 minutes, then four version jobs of
  3.5 to 6 minutes, run 40797440573), to `forge-legacy` about 6 minutes (run 40797453188); their extended runs about 10
  minutes. Already there: cancelling stale runs, skipping documentation-only changes, the quick check on each push, the
  full matrix only on pull requests, tags and by hand, and one game install per job.
  - **Done on `main`'s branch** (its pull request): the full matrix runs the versions a change touches
    (`ci/changed-targets.py --full`: a version's own folder runs that version, shared code all of them; a tag or a run
    by hand all of them; a merge into `main` builds what it changed). Each version is built once, and its jar and test
    mods tested in parallel parts, which cut the game boots per job from up to 16 to 8 at most: `server` (seven
    launches, eight with `extended`), `client` (three), `mods` (other mods' NeoForge builds: Sodium and Iris on 26.x,
    the plugin hosts), and with `extended` the informational runs in `mods` (the Fabric builds) and a `corpus` part. The
    version jobs no longer wait for the core job. The quick check stands down while the branch has a pull request open,
    whose full matrix covers the push (it ran both on each push before). A version's CI pins are read from its folder
    (the loader, Fabric API, the plugin hosts), not repeated in the workflow; the unused `experimental` flag is gone.
  - **Decided:** the game installs are not cached across runs. A part installs one side once (10 to 25 seconds), and
    pushes to `main` run no game, so only pull requests would save them: an estimated 2 GB per pull request against
    GitHub's 10 GB per repository, which would evict `main`'s Gradle caches, whose loss costs minutes per job.
  - **Moved:** 1.21.1's client with Forgified Fabric API and Sodium for NeoForge, informational, from every pull request
    to `extended` (with the `mods` part's other informational runs).
  - **After** (`main`'s pull request, #236): a pull request's full matrix 5.4 minutes of wall time (the plan job 6
    seconds, the builds 1.1 to 1.2 minutes, every part done by 5.3 minutes; 48 job-minutes against 46, run
    41344142404); an extended run 20 minutes (run 41344706881), held by the 26.2 and 26.3 corpus clients at their
    10-minute limit (CORPUS-48), every other part done by 11 minutes. A client that reached its world and doesn't exit
    is now stopped 2 minutes later, not at that limit: the next extended run took 14.3 minutes (41347080112, its
    parts waiting up to 4.7 minutes for runners), the 26.2 and 26.3 corpus parts 9.7 minutes instead of 18.5 and
    15.7. The quick check stands down in 6 seconds (run 41344139306).
  - **The Forge branches** (#237 into `forge`, #238 into `forge-legacy`, synced from `main`): the same plan job, the
    pins from each version's folder, Java selected once, no wait for the core job, and the same exit timeout. Their
    jobs are not split into parts: a Forge version job boots the game four times (six with `extended`). A pull request
    to `forge` 5.9 minutes (7.5 before; run 41349136428, of which 2.5 minutes were 26.3's informational classpath
    report), to `forge-legacy` 7.0 minutes (6 before; run 41349147135, its one version job alone 6.8 minutes, so one
    run's variance outweighs the core job's minute).
- **Task 3 (parity).** NeoForge: CORPUS-44, 45 and 46, and SER-1. Forge: PRE-18, CORPUS-33 and HOLD-2; the provider
  policy and the refusals wired into both Forge loaders; the client with Fabric API, and Fabric API's client modules,
  made gates; the last entity events' hook and item storages in items. Each platform's table then lists what is
  unsupported, with the reason.
  - **Done on `main`'s branch** (its pull request): CORPUS-44, 45 and 46, CORPUS-49 (new: 1.21.1's JSON data loaders
    ignored Fabric's load conditions) and KEYMAP-2 (new: Fabric key bindings on NeoForge 21.1, now native) on 1.21.1;
    SER-1 on 1.21.11; the engine's halves of PRE-18 (a MixinExtras local by an argument's name), CORPUS-33 (fields a
    platform retyped to a subtype, `RetypedFields`) and LAMBDA-8 (new: two pre-flight lambda gaps Forge's data loaders
    showed); the provider policy's platform-neutral parts moved from NeoForge's locator into core, for both Forge
    loaders (`NativeModules.providingByPackages`, a platform's own descriptor among nested jars, the mixin configs a
    manifest names, the in-game mod written without what stands down). CI gates added: 1.21.1's JSON loader conditions
    (CORPUS-49), its key binding in the options with its saved key (KEYMAP-2), the failing test mod's unregistered
    holder (CORPUS-42's drop, every NeoForge server). Diagnostics runs on the 1.21.1 corpora found CORPUS-50 to 52, for
    task 5.
  - **Done on the Forge branches** (#240, #241, synced from #239): HOLD-2 ported, with the report write Forge has no
    other place for; CORPUS-33's and LAMBDA-8's fixes checked on Forge 61 to 66 (a biome modification, load conditions
    in a JSON data loader); the provider policy (PROVIDER-1) and the refusals (DUP-1; PROVIDER-2, a jar with its own
    copy of Fabric Loader, by the owner's decision) in both Forge loaders; Fabric's screen events (SCREEN-1), Mod Menu
    config screens, `afterEntrypoints` and entrypoint failures reported, as on NeoForge. Found and fixed on the way:
    PACK-5 (a world holding a Fabric mod's registry data asked to confirm experimental settings), KEYMAP-3 (Fabric key
    mappings refused; the module is native on Forge 61 to 66) and HUD-2 (Fabric HUD elements never drawn from Forge's
    layers) on Forge 61 to 66; HUD-2's `HudRenderCallback`, FUEL-1 (Fabric fuels) and BREW-1 (Fabric brewing recipes) on
    Forge 47. CI gates added: both client runs; the 26.x client API test mod; load conditions, a biome modification and
    the dropped holder on the servers; a stand-ins server and a refusal server per version. `docs/FORGE.md` and
    `FORGE-LEGACY.md` drop "experimental", take their tables from the green runs (41382942938, 41385466464) and list
    what is unsupported on Forge, with the task that takes each on.
  - **Carried to task 5:** PRE-18's check on Forge (the engine's fix is in, but the extended run's corpus clients,
    41385577023, now stop before ImmediatelyFast's hook loads, at CORPUS-53), the Forge corpus stops
    (CORPUS-53 to 55; CORPUS-53's client stop is in v0.5.1's 26.3 run too), HUD-3 (new: NeoForge's HUD wrapping misses
    vanilla's conditions around three elements), Fabric mods' own HUD mixins on Forge, and coexisting with Forgified
    Fabric API on Forge.
  - **Decided:** the owner deferred Forge's item storages in items and the effect events' hook to task 5, listed as
    unsupported until then (2026-11-01), and chose to refuse Forgified Fabric API on Forge now and try coexisting in
    task 5 (2026-11-02).
- **Task 4 (FRAPI):** done (the plan's *Status*). Carried to task 5: CORPUS-56, MODEL-4, the guards in upstream's
  overwritten methods against a mod overwriting them at a higher priority, and PACK-6 checked on Forge; to task 6,
  the Forge configs' rows in `MIXINS.md` and what a Fabric model's throw in a chunk section costs on Forge 47 (about
  1.4 s of a worker in R5's run).
- **Task 5 (compatibility):** the difficult mods above; then the corpora by impact, the login queries candidate,
  `preLaunch` moved, ENTRY-1, LINK-3, PRE-19 and KEYMAP-1. On Forge: the corpus stops (CORPUS-53 to 55) and PRE-18's
  check, item storages in items, the effect events' hook, Fabric mods' own HUD mixins and coexisting with Forgified
  Fabric API; HUD-3 on NeoForge.
  - **Done on `main`'s branch (#247):** the Fabric builds of Sodium, Iris, Essential, malilib (with MiniHUD,
    Tweakeroo and Litematica) and Flashback load by default on NeoForge, each a gate on every version (Iris's but on
    1.21.1); their refusals stay on Forge until the Forge runs gate them. A mixin that failed as Mixin applied it is
    left out from the next launch (APPLY-1). HUD-3 and MODEL-4 are fixed, and the corpora's stops on 1.21.1 that
    CORPUS-51 and CORPUS-52 (TOKEN-1) were. LINK-3 is fixed; KEYMAP-1 is P3 and documented. The fixes on the way are
    in `CHANGELOG.md`.
- **Task 6 (optimization and cleanup):** PERF-1, PERF-2 and the launch baselines; the cache findings (FC-5, FC-6,
  FC-7); SEC-5, CI-3, PLAT-2 and P3-2; the cleanup candidates in *Quality* (below); and whatever tasks 3 to 5 leave.
- **Task 7 (release):** the ship criteria above.

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
| Native Fabric API modules | 17 (18 on 1.21.1: particles) | 3 (`fabric-api-base`, `fabric-loot-api-v3`, the key mapping module, KEYMAP-3) | 6 (`fabric-api-base`, biome, both loot modules, dimensions, particles) |
| Bridges to the platform | fluid types, storages and energy with capabilities, plugins of other mods, custom values, renderer API | fluid types, storages with capabilities (on blocks, fluids in items), tags' `fabric:remove`, default attributes (Forge 64 to 66), advancement events (Forge 65 and 66), block break, sleep and after-damage events (BREAK-1, SLEEP-1, DAMAGE-1), load conditions in Forge 61's conditional decoder, a Fabric mod's pack identity (PACK-5), screen events (SCREEN-1), HUD elements on Forge's layers (HUD-2) | fluid types, storages with capabilities (on blocks, fluids in items), tags' `fabric:remove`, block break and sleep events (BREAK-1, SLEEP-1, SLEEP-2), Fabric fuels and brewing recipes (FUEL-1, BREW-1), `HudRenderCallback` (HUD-2) |
| Networking | native, on NeoForge's payloads | upstream's module: a payload round trip with the integrated server (checked with Fabric API) | upstream's module: a channel round trip with the integrated server (checked with Fabric API) |
| Rendering (renderer API, models, Sodium and Iris for NeoForge) | yes | upstream's modules: Indigo builds chunk sections on all four (SUPER-1, LOCAL-4), Fabric's colour providers are read from Forge's map (COLOR-1), and Fabric's block dust tint event is asked where Forge 61 decides the tint (PART-2); Fabric models drawn through Indigo with rule 8 at its entry points, and on Forge 61 from Fabricated's seams where upstream's hooks are left out (FRAPI-8; CI's renderer test mod) | upstream's renderer API, Indigo, `fabric-rendering-v1` and model loading load, with Fabric's colour providers read from Forge's maps (COLOR-1), model loading's constructor hook wrapped (CTOR-4) and its before-bake hook moved to the overload Forge's code went to (MODEL-3); particles Fabricated's own, on Forge's particle engine (PART-1); Fabric models drawn through Indigo from Fabricated's seams, as Forge's renderers call its own overloads, with rule 8 (FRAPI-9; CI's renderer test mod) |
| CI | server and client runs gated, and checks in the Fabric API runs; corpus runs informational | both server runs and both client runs gated, with a stand-ins server and a refusal server; corpus runs informational | as Forge 61 to 66 |
| Releases | supported; beta from 0.6.0 | experimental until 0.6.0-beta, then held to the same bar (decision 1) | experimental until 0.6.0-beta, then held to the same bar (decision 1) |

- **`main`:** NeoForge for 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3, every line stable (NeoForge tagged `26.3.0-stable`; CI
  pins 26.3.0.95 and no version is experimental any more).
- **Next** (the v0.6.0-beta pass, task 4 done): task 5: the corpus stops (CORPUS-53 to 55) and PRE-18's check, the effect events' hook into Brigadier, which Forge
  loads without mixins, item storages in items (a backpack's) with Forge's item handlers on stacks, Fabric mods' own
  HUD mixins, and coexisting with Forgified Fabric API.
- **Decided** (`RESEARCH.md` §8): Forge 47.4 for 1.20.1, the shared modules kept loadable on Java 17, ForgeGradle for
  modern Forge, CI for pull requests only. L1, the behaviour-neutral seams on `main`: Java 17 for the shared modules
  (their main code compiles at release 17), the loader split (`loader/common`, `loader/neoforge`), the build split
  (`mc/platform-neoforge.gradle`, version conditions keyed on properties) and `build.yml` building pull requests to
  `main` only are done; CI from data and the platform profile follow when the branches run the game. Work happens on a
  working branch and reaches the platform branches by pull request (`claude.md`).

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
  - `preLaunch` stays with `main` (task 5's decision, *Experimental items*); moving it waits for a mod that needs it
    earlier.
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

| | 1.21.1 | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|---|---|
| Renderer API and Indigo load | yes | yes (M5; upstream's conflicting mixins left out) | yes | yes | yes |
| Model-loading modifiers and extra models | yes (MODEL-4 since `a58f964`: a model baked as another's part or parent too) | yes (CORPUS-6) | yes since `08aff28` | yes | yes |
| Fabric block models in the world | yes (FRAPI-5, CI) | yes (M5, CI) | yes (`FabricBlockRendering`) | yes | yes |
| Moving blocks (pistons, falling) and the breaking overlay | yes, by Indigo's block context (FRAPI-5; CI checks a falling block, a piston's and the overlay) | yes, from parts made from the model's quads (M5; CI checks a falling block and the overlay) | yes (M2) | yes | yes |
| Blocks drawn as entities, other mods drawing blocks from their parts | through `renderBatched`, yes (FRAPI-5); a minecart's block from vanilla's quads, as upstream (invisible for a model with only Fabric quads, as on Fabric) | yes, from parts made from the model's quads (M5) | yes, Fabric models only (FRAPI-6; CI checks a minecart's block) | yes | yes |
| Fabric models nested in a vanilla multipart or weighted model | yes (FRAPI-5, CI) | yes (M5, CI) | yes (M3) | yes | yes |
| Block outlines, a moving block's glow outline | not checked | the translucent outline pass from the parts made from the quads | block outlines from the model's parts, as NeoForge sees them; moving blocks have no outline | block outlines: same; the glow outline by Fabric's renderer (M2b; CI checks a glowing falling block) | same as 26.2 |
| Under Sodium for NeoForge | not checked (task 5) | Sodium meshes the parts made from the quads, with Indigo registered as the renderer (M5; informational CI, run 374) | Sodium's own Fabric renderer, Indigo stands down (R1, CI) | same | same |
| Fabric item models (hotbar, hand, dropped, item frame) | yes (CI) | yes (CI) | yes (CI) | yes | yes |
| A Fabric model that throws fails alone, a wrapper of one too (rule 8, FRAPI-7) | yes (CI) | yes (CI) | yes (CI) | yes | yes |
| Forgified Fabric API's renderer beside Fabricated's seams (R7) | they stand down (extended CI) | no build | they stand down | they stand down | no build |
| Continuity's connected textures (R6, informational) | yes, since CORPUS-56's fix (extended run 41496005222) | yes, since CORPUS-56's fix (extended run 41512581949) | same | same | same |

- **Done (task 4):** the *FRAPI plan*'s R1 to R7, with FRAPI-5 to FRAPI-9 and PACK-6 fixed; its *Status* has each.
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
external dependencies, wherever that can be made correct (decision 3 of the v0.6.0-beta pass: their Fabric builds are
made to run, *Difficult mods*). Sodium and Iris also have NeoForge builds; the Fabric-only stack (OneConfig v1,
PolyPlus, Polydium) has no other route to NeoForge. Research:
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
- A renderer mod's Fabric build stays refused by default until its CI run is a gate (decision 3); `loadUnsupported`
  loads it before then.

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

**Where the Fabric builds stand.** Since task 5 (#247) Sodium's and Iris's load by default on NeoForge, and their
client run is a gate on every version (Iris's but on 1.21.1), with the gaps below listed in `COMPATIBILITY.md`; the
same client with CI's shader pack is informational. Before that (CI's informational run, `loadUnsupported`, 26.x):
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
| 3 | Fabric-order early hooks | PolyPlus, Polydium, OneConfig | Language adapters done (server check "early language adapter OK"); `preLaunch` stays with `main` (decided in task 5, *Experimental items*) |
| 4 | Pre-flight of mixins into other mods, pairing rules | Iris with Sodium, Polydium, OneConfig's compat mixins, Sodium addons | Done: installed NeoForge mods and, on 26.x, other Fabric mods (R3); pairing rules (`notWithNeoForge`, unit and pipeline tests) |
| 5 | Built-in compatibility plan (data) | Refusals and pairings; per-mod mixin skips | Done: core's `compat-plan.json` replaces the hard-coded map; skips wait for an entry with evidence (Sodium's window mixin S10 is UNVERIFIED) |
| 6 | Fabric fluid rendering backed by NeoForge's (custom fluid renderers on 26.x, `IClientFluidTypeExtensions` on 1.21.11) | NeoForge fluids under a Fabric renderer | Planned |
| 7 | Block-entity render data and NeoForge model data, bridged both ways | Fabric renderers reading NeoForge block entities' data | Planned |
| 8 | Renderer contract: report the active renderer, optional every-block routing | Third-party renderer API implementations | The log names the renderer drawing Fabric models (FRAPI-3, won't fix); every-block routing planned |
| 9 | Early-window opt-out for mods that need to act before the first GL context (driver workarounds) | Sodium's `preLaunch` workarounds | When a real report needs it |
| 10 | Corpus runs for the Polyfrost stack and Fabric Sodium/Iris | Evidence for 3-8 | Both exist: Fabric Sodium/Iris a gate on NeoForge since task 5 (its shader pack run informational), OneConfig with PolyPlus informational where Modrinth has builds |

**Reconsidered on 2026-11-01** (decision 3): code for one mod's Fabric build is allowed as a compatibility patch where
no generic capability does the job (*Difficult mods*). A port of Sodium's own NeoForge platform module stays out (its
size, re-checked every Sodium release, and its licence); Iris fixes beyond its public API are decided per finding.

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
  game once for all its runs (*Standing decisions*, 2026-10-27).
- **Next:** task 2 of the v0.6.0-beta pass; the gates listed in *Beta*; checks for what the corpus mods do in the world.
- **Noted at the cleanup (task 4), for later:** the `fabricapi-overrides` copied between versions (about 875 lines;
  `import-fabric-api.sh` copies each version's alone, so identical copies need shared-overrides support there). Copies
  across platforms (the loot mixins Forge and NeoForge both have, the biome and particle code Forge 47 and NeoForge
  1.21.x share) stay per branch since each platform has its own (`claude.md`, *Branches*); `FabricDependencyLocator`'s runs both
  Forge loaders have alike, the stand-ins in Forge's `src/fabricapistubs` and the test-mod source sets the Forge
  projects set up but never build are the Forge branches' to merge. Access widener records keep a `transitive` flag nothing acts on (every
  access transformer applies to every mod). The retired config keys (`logLevel`, `preferNativeNeoForgeMods`,
  `preferFabric`) stay accepted and ignored at 0.5.0, so older configs don't warn. Flashback's refusal
  (`compat-plan.json`, CORPUS-21) is lifted on NeoForge (task 5): a run with it lifted stopped in another early
  freeze, which REG-8 holds until the main entrypoints.

## Findings

Open findings, by ID, with the severities [`FINDINGS.md`](FINDINGS.md) defines. A finding moves to `FINDINGS.md`,
with its cause, why the tests missed it and its fix, in the change that fixes it.

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| CORPUS-79 | P0 on Forge 64 clients with ResourcefulLib (the corpus client) | ResourcefulLib's `ScreenEffectRendererMixin`, Forge 64's `ScreenEffectRenderer` | The 26.1.2 corpus client crashed initializing the game (extended run 41594474962): "Critical injection failure" of ResourcefulLib's `rlib_renderScreenEffect`, an `@Inject` (with a MixinExtras `@Local` `PoseStack`) into the screen effect method of `ScreenEffectRenderer`, whose injection point found nothing in Forge's build; pre-flight let it through. UNVERIFIED cause: the mixin's `@At` hasn't been read. | Open: the Forge evidence step prints the mixin and Forge's method (next extended run). |
| CORPUS-78 | P0 on Forge 61 to 66 with a Fabric mod hooking a `finalizeSpawn` call Forge's spawn transformer changes (Friends and Foes: the Forge 64 and 65 corpus servers) | `PlatformCoremods.forge` (new), `PlatformDescriptor.FORGE`, `MixinNames.isPlatformCode` (new: `CallRepairs.hookCall`, `PreflightClasses.callLeadingTo`) | The 26.1.2 and 26.2 corpus servers crashed in the server tick (extended run 41594474962): "Critical injection failure" of Friends and Foes' `PatrolSpawnerMixin.friendsandfoes$onSpawnEntity`, at a `PatrollingMonster`'s `finalizeSpawn` call in `PatrolSpawner.spawnPatrolMember`. Forge 61 to 66's `MethodRedirector` (in `forge-transformers`, a ModLauncher transformer, so before Mixin) points that call at `ForgeEventFactory.onFinalizeSpawn` in the methods its `finalize_spawn_targets.json` names and in the listed classes' synthetic methods. Pre-flight read Forge 61 to 66's game as its jars have it, and its hook following knew only NeoForge's package. Why tests missed it: no Forge test mod hooks a spawn call, and the corpus servers stopped earlier until CORPUS-70 and CORPUS-73. | Fixed in core: pre-flight reads Forge 61 to 66's classes as `MethodRedirector` and `FieldToMethodTransformer` leave them (each version's target list, copied from Forge's sources, ships in core), and a hook at such a call moves to Forge's hook, as on NeoForge 21.1 (`PlatformCoremodsTest`, `MixinPreflightTest.aHookAtASpawnCallForge61To66sTransformerPointsAtItsEventHookMovesToTheHook`). Verify: the Forge 64 and 65 corpus servers past Friends and Foes' hook. |
| CORPUS-77 | P2 on Forge 47 clients with owo-lib (the corpus client) | owo's client entrypoint, Fabricated's mod container for MixinExtras | owo's client initializer threw "Mod mixinextras has no root path" (extended run 41590139262): the container Fabricated lists for the MixinExtras the in-game mod nests has no root path on Forge 47. UNVERIFIED cause. | Open: read where owo asks for MixinExtras' root and what the container answers on Forge 47. |
| CORPUS-76 | P0 on Forge 47 clients with REI, ShulkerBoxTooltip or Traveler's Backpack (the corpus client) | `ClientTooltipComponent`'s supertypes, as the mods' mixins and Fabricated's translations leave them | The corpus client crashed in its loading overlay (extended run 41590139262): `ClassCircularityError: net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent` as `ModelBakery.init` loaded the interface, and the three mods' entrypoints threw the same. The stack (the interface's definition asking for a supertype, whose own definition asked for `ClientTooltipComponent` again) means the interface was given a supertype that itself extends it: an interface a mixin adds to it, or one injected into it, that is its own subtype. UNVERIFIED which. | Open: the Forge 47 evidence step prints the mods' mixins of the interface and the classes extending it, as they ship and as translated (`ci/javap-dump.py`'s `subtypes:`, new), in the next extended run. |
| CORPUS-74 | P0 on Forge 61 to 66 clients with MoreCulling, and Forge 47's with owo-lib (the corpus clients); on Forge for any mod shadowing a field another mod's mixin adds | `MixinRepairs.sharedFieldRepair` (new), `MixinCompat.shadowsMixinFields` (new), `MixinPreflight.checkMembersOf` | The Forge 26.2 corpus client crashed as Mixin applied MoreCulling's `BlockModelRenderState_fabricCullMixin` (extended run 41590136017): "@Shadow field mesh was not located in the target class net.minecraft.client.renderer.block.BlockModelRenderState", the field Fabric API's renderer mixin adds there. The Forge 47 corpus client crashed the same way on owo's creative screen mixin, which shadows Fabric API's `fabric_currentPage` (extended run 41590139262). Fabric's Mixin finds a `@Shadow` field among the fields of the mixins it prepared before (`TargetClassContext`'s `mixinFields`, from 0.12.0, read in its sources); SpongePowered's, which Forge runs (0.8.5 and 0.8.7), looks in the target's own fields only. Pre-flight took such a shadow as fine wherever an installed mixin adds the field. Why tests missed it: no test mod shadowed a field another mixin adds, and the corpus clients stopped earlier. | Fixed in core: where the running Mixin finds only the target's own fields, such a shadow is declared as the other mixin declares the field (its access and signature, Mixin's annotations off), and Mixin, which adds a mixin's new field only where its target has none of that name and descriptor yet, merges the two into one, used by both mods' code as on Fabric; one that can't be declared so (a static shadow of an instance field, a writable one of a final field, an alias) fails pre-flight there (`MixinPreflightTest.aShadowOfAFieldAnotherModsMixinAddsIsThatFieldWhereMixinFindsOnlyTheTargetsOwn`). The Forge test mods count the server's ticks in one mixin's field and read it through another's shadow (both Forge branches, after the sync). Verify: the Forge 26.x corpus clients past MoreCulling and Forge 47's past owo. |
| CORPUS-72 | P1 on Forge 47 with Modern Industrialization (the corpus) | Magna (its nested tool library), Modern Industrialization's `MIStartup` | Modern Industrialization's main entrypoint threw `ClassNotFoundException: dev.draylar.magna.api.MagnaTool` (extended run 41590139262): pre-flight disabled Magna, whose classes Modern Industrialization's items extend. UNVERIFIED which of Magna's mixins fails and why. | Open: read Magna's pre-flight failure on Forge 47; a mod whose required dependency is disabled should be disabled with it, with the reason, rather than fail at its entrypoint. |
| CORPUS-71 | P0 on Forge 61 to 66 clients with GeckoLib (the corpus clients) | `NamespacedWrapperEarlyWriteMixin` (new, `forge`) | Forge locks the vanilla registries it manages once it has taken its vanilla snapshot. GeckoLib's constants, loaded on a client as the client bootstrap builds the special model renderers, register its data components from their static initializer, which works on Fabric, and the registry refused the write. Why tests missed it: the test mods register in their entrypoints only. | Fixed on `forge` (#254): until the main entrypoints have run, a write to a locked registry opens it for that write and leaves it as it was, as NeoForge's `MappedRegistryEarlyWriteMixin` does. Verify: the Forge 61 to 66 corpus clients past GeckoLib's constants. |
| ENUM-7 | P0 on Forge 61 to 66 with Friends and Foes (the corpus clients' and servers' CORPUS-53); on Forge 47 and NeoForge 21.1 and 21.11 for an enum mixin into an enum NeoForge doesn't make extensible; fixed in core; on Forge the fix's mixin stopped Forge's enum extender, fixed again in core; then its factories ran before the enum's map by name was set, fixed in core, to verify | `ModTranslator.withEnumExtensions`, `EnumValuesMixins` (an enum's values array found by its type), `MixinPreflight.checkMixin` (an enum mixin where Mixin applies none) | Mixin applies enum mixins from Fabric's 0.17.1 only. Where it is older (Forge's 0.8.5 and 0.8.7, NeoForge 21.1's and 21.11's), only an enum mixin into one of NeoForge's extensible enums was translated (ENUM-1, ENUM-5), and only a class tweaker's declared constants were judged (ENUM-6): Friends and Foes' enum mixin into `MobCategory`, which declares its constants with a `@Shadow` constructor and no class tweaker line, went to Forge's Mixin 0.8.7, which threw a `NullPointerException` attaching that constructor ("Attach error for friendsandfoes-fabric.mixins.json:AddCustomSpawnGroupMixin", `MixinPreProcessorStandard.checkMethodNotUnique`), and every Forge 61 to 66 corpus client stopped as `EntityTypes`' class initializer loaded `MobCategory` (extended run 41556865877's 26.2 and 26.3 crash reports; the evidence step printed their innermost cause). | Why tests missed it: no test had an enum mixin into an enum NeoForge doesn't make extensible where Mixin is older than 0.17.1, and pre-flight judged enum mixins only against NeoForge's extensible enums. Fix: there, an enum mixin becomes an ordinary mixin adding its constants into the enum's class initializer, as ENUM-5's do (Friends and Foes reads its categories with `valueOf`); one that can't (a constant with a body, code using the mixin, the mod's classes reading a constant as a field of the enum) disables its mod with the reason, as Mixin would stop the game whatever the config (`PipelineTest.anEnumMixinBecomesAMixinAddingItsConstantsWhereMixinAppliesNoEnumMixins`, `EnumValuesMixinsTest.theEnumsValuesArrayIsFoundByItsTypeWhereTheGameRenamedIt`). After the sync (extended run 41569829683) the Forge 66 corpus client and server got past Mixin and stopped in Forge's own enum extender as `MobCategory` loaded ("Enum has create method but we could not find $VALUES", `RuntimeEnumExtender`): `MobCategory` is one of the enums Forge makes extensible (`IExtensibleEnum`), whose extender runs after Mixin and finds the values array only while it is still final, and the generated mixin's `@Mutable` shadow of it had made it non-final. Why tests missed it: no test had an enum Forge makes extensible, and the generated mixin ran only merged by hand, without Forge's extender. Fix: into such an enum the generated mixin makes the constants with the enum's own `create` factories, which the extender writes, and shadows no values array; a constant whose constructor has no factory leaves it an enum mixin, which pre-flight judges (`EnumValuesMixinsTest.anEnumForgeMakesExtensibleGetsItsConstantsFromItsOwnFactories`, which runs it against a factory written as the extender writes one). After the next sync (extended run 41574438102) every Forge 61 to 66 corpus server and client stopped as `MobCategory` loaded, with a `NullPointerException` in `MobCategory.init` ("MobCategory.BY_NAME is null"): each factory registers its constant in the enum's map by name (Forge's `IExtensibleEnum.init`), which the class initializer sets after the values array, and the generated injector ran right after the array. Why tests missed it: the test's enum had no such map. Fix: into an enum Forge makes extensible, the injector calls the factories at the class initializer's end (`TAIL`), as a Forge mod's own call comes after it, with the map set (the test's enum now registers each constant in a map by name, as `MobCategory`'s factory does). Verify: the Forge 61 to 66 corpus clients and servers past their bootstrap. |
| SELF-3 | P1 on Forge 66 clients with Fabric API (its moving block renderer); on every platform for a mixin whose inner class calls a member Mixin renames as it merges it; fixed in core, to verify | `MixinRepairs.copiesInnerClasses` (new), before `SelfTypeRepairs` (SELF-1) | SELF-1 names the target in a mixin's descriptors where they name the mixin. Mixin copies a mixin's inner classes (an anonymous class, typically) into each target and does that renaming itself there, and the copy finds the mixin's members by the descriptors it calls them with, which name the mixin, matched against the mixin's as Mixin read them. Renamed first by SELF-1, a member Mixin renames as it merges it (javac's synthetic accessor for the inner class, or a `@Unique` private method) kept its old name in the copy: Fabric API's `MovingBlockFeatureRendererMixin` (26.3) has an anonymous quad consumer calling `access$100` (its outer class's superclass's protected `getVertexBuilder`), and on Forge 66 every falling or pushed Fabric block model threw `NoSuchMethodError` for it, drawn from its parts by rule 8 instead (PR run 41556829133's client with Fabric API; the moving block gate failed). | Why tests missed it: SELF-1's tests had no mixin with inner classes, and NeoForge's moving block path is Fabricated's own mixin, not Fabric's. Fix: a mixin whose inner classes Mixin copies (one neither static and synthetic nor a mixin) is left to Mixin; a mixin whose only inner class is javac's switch table is still renamed (`aMixinWhoseInnerClassesMixinCopiesKeepsItsOwnType`). Verify: the Forge 26.3 client's moving block gate, after the sync. |
| PLAT-6 | P1 on Forge 47 with Forge Config API Port's Fabric build (and the mods using it); fixed on `forge-legacy`, to verify | `loader/forge-legacy`'s `ForgeDescriptor` (wraps `PlatformDescriptor.FORGE` method by method) | Forge 47's descriptor missed the two methods added to the interface since it was written, `replacedMods` and `providedPackages`, so it answered NeoForge's defaults: Forge Config API Port's Fabric jar was left out as if the in-game mod provided its API on NeoForge's config system, which Forge 47's in-game mod doesn't, and a Fabric mod's copy of NeoForge's packages was left out though Forge has none (read in the code while porting CORPUS-61; FORGE-LEGACY.md said the jar loads, REPL-1 and PLAT-5). Forge 61 to 66 pass `PlatformDescriptor.FORGE` itself. | Fixed in #249 (`65270cc`): it answers Forge's, and describes Forge 47's coremods (CORPUS-61's Forge half). The stand-ins server run checks that Mod Menu's stand-in is left out and Forge Config API Port's isn't; to verify there and in Forge 47's corpus, which has Forge Config API Port. |
| PRE-18 | P0 on Forge 65 and 66 clients, UNVERIFIED cause | `MixinPreflight` (MixinExtras `@Local` sugar in a method Forge patched) | ImmediatelyFast's `map_atlas_generation.MixinGuiGraphicsExtractor`, a `@WrapOperation` taking a `LocalRef`, stopped the Forge 65 corpus client ("failed injection check, (0/1) succeeded. Scanned 0 target(s)"), and Mod Menu's `MixinTitleScreen`, an `@Inject` taking a `LocalIntRef` and a `LocalBooleanRef`, the Forge 66 one ("SugarApplicationException: Unable to find matching local!"), run 40570195894. Pre-flight reported nothing for either. Which locals Forge's patched methods have there, and why pre-flight's `@Local` checks passed the hooks, is UNVERIFIED. | Fixed in core on `main` (task 3: a MixinExtras local taking an argument by its name gets the argument's slot, found as ARGS-1 finds it; pre-flight reports one it can't place), and synced to both Forge branches (#240, #241). Mod Menu stays out on Forge (REPL-1), its API now Fabricated's own there. Not yet checked end to end: the Forge 61 to 66 corpus clients now stop as the game bootstraps (CORPUS-53, extended run 41385577023), before ImmediatelyFast's hook loads; the check moves to task 5 with CORPUS-53. |
| PRE-19 | P2 | `MixinPreflight.overloadRepairs` (a variable modifier picking its local by its place among the locals) | Puzzles Lib's break speed hook is left out on Forge 47 (PRE-17) where it could move to `getDigSpeed`, the method Forge deprecated `getDestroySpeed` for: the overload repair moves only variable modifiers that pick their local by type alone (MODEL-3), as the other method's code could have other locals of that type first (LOCAL-4). | Fixed in core, to verify on Forge 47: `OverloadRepairs.variableByOrdinalRepairs` moves one by its ordinal, at a load, where the other method adds no parameter of its type, its local variable table places vanilla's local at that ordinal, and each of vanilla's loads of it is still there (`PatchAlignment` with vanilla's code, VANILLA-1); the point's ordinal follows vanilla's load where the patch loads the local before it (Forge's break speed event). `MixinPreflightTest.aVariableModifierByOrdinalMovesToTheMethodTheDeprecatedOneCallsAtVanillasLoad` and `...StaysWhereThePatchAddsALocalOfItsTypeOrALoadOfIt`. `forge-legacy` doesn't read vanilla's code yet (its game jar is obfuscated; the SRG-named game jar Forge's installer leaves is the likely source, unchecked): the Forge sync. |
| CORPUS-34 | — | JEI's Fabric jar (Forge 61 to 66 corpus) | Forge's mods folder scan reads every jar's module-info; JEI's Fabric jar names packages it lacks, so Forge refuses it and loads only itself (LOADERR-1). | Not Fabricated's: JEI's Forge build is used. Left out of the Forge corpus runs (`00c3f37`). |
| CORPUS-35 | — | YACL's Fabric jar (Forge 47 corpus) | Its 3.6.6 Fabric jar carries a `mods.toml` asking for no `javafml` version, which Forge 47 refuses, loading only itself (LOADERR-1). | Not Fabricated's: YACL's Forge build is used. Left out of the Forge 47 corpus run (`00c3f37`). |
| CORPUS-36 | P2 | Farmer's Delight Refabricated (Forge 61 to 66 corpus) | Disabled: it "adds enum constants with Mixin's enum extensions, which need Mixin 0.17.1 or later; this Forge runs Mixin 0.8.7". Forge reads no enum extension file to translate them into (ENUM-1 is NeoForge's). | ENUM-7 turns enum mixins into ordinary mixins adding their constants where Mixin can't apply them, which may load it: to read in the Forge corpus runs after the sync; otherwise its Forge build is used. |
| CORPUS-37 | P2 | Porting Lib's accessors module (Forge 47 corpus: Create Fabric, Farmer's Delight Refabricated) | Pre-flight disables it: accessors of fields Forge 47 retyped (`MinecraftServer.f_129740_`, `PotionBrewing$Mix.f_43532_` and `f_43534_`, and on the client `ParticleEngine.f_107293_`, PART-1's map), and with it the modules and mods needing it (LINK-2 for the base module's texture interface). | Not repaired: whether a bridge can reach each field's new type is UNVERIFIED; the mods' Forge builds are used. |
| CORPUS-38 | P3, UNVERIFIED | Fabric Language Kotlin (Forge corpus runs) | The linkage check lists `KotlinLanguageAdapter -> missing class net.fabricmc.loader.language.LanguageAdapter`, Fabric Loader's old adapter interface, which Fabricated doesn't provide. Kotlin mods load through its current adapter; whether any mod still names the old one is UNVERIFIED. | Provide the old interface if a mod needs it. |
| CORPUS-48 | P2 on NeoForge 26.2 and 26.3 clients with Xaero's World Map 1.47.2 and the 1.21.1 one with 1.47.0 (the corpus clients), UNVERIFIED for a player's quit | Xaero's World Map's `WorldDataHandler.onServerWorldUnload` | Once the 26.2 and 26.3 corpus clients reached their world, mc-runtime-test's stop halted the integrated server, and its thread waited in `WorldDataHandler.onServerWorldUnload` (`xaeroworldmap@1.47.2`, line 106, from `Level.close` in `MinecraftServer.stopServer`) while the render thread joined it, until the run's 10-minute limit (run 41344706881's thread dump, 26.3; 26.2's client ran as long). In v0.5.1's tag run (40800647792) the same clients exited in about a minute, and the 1.21.11 and 26.1.2 ones exit normally. The 1.21.1 corpus client's server thread waited there too (line 106 of 1.47.0), as the game saved after CORPUS-63's crash (extended run 41527263549), and that client ran to the limit again after reaching its world in extended run 41539993361, whose kept log lost its thread dumps. In extended run 41548210823 it reached its world again (Farmer's Delight loaded, CORPUS-65) and was stopped 2 minutes later: its server thread was at that line 106 of 1.47.0, running, with no wait or park above it in its stack (a loop, most likely), while the render thread joined it in `MinecraftServer.halt`, called from mc-runtime-test's stop on the render thread. A player's quit doesn't stop the server that way: the client's own disconnect waits for it in `Minecraft.disconnect` (`waitForServer`, in NeoForge 21.1's patch of it). Extended run 41552920159 printed the method (`xaeroworldmap-fabric-1.21.1-1.47.0.jar` and Fabricated's translation, the same code): line 106 is `serverCaps.loaded = false` inside `synchronized (serverCaps)`, the world's `ServerWorldCapabilities`, so no loop is there; the dump's filter had kept only the frames, without the thread's state or the monitor it waits for. Most likely the server thread waits for that monitor, held by another of Xaero's threads that waits in turn for the render thread, which mc-runtime-test's stop blocks in `halt`'s join (a player's quit keeps the render thread running tasks in `waitForServer`): UNVERIFIED. | Task 5 or 6: the evidence step now prints each main thread's state and monitors, and the thread holding a monitor they wait for: read it in the next extended run, then reproduce with Xaero's maps alone on 26.3. The client runs stop such a hang 2 minutes after the world is reached (`SMOKE_EXIT_TIMEOUT`). |
| CORPUS-53 | P0 on Forge 61 to 66 with the corpus; the causes found (ENUM-7, SHARE-1), Forge 61's server's UNVERIFIED | The Forge 61 to 66 corpus clients, and the Forge 64 to 66 corpus servers | Every Forge 61 to 66 corpus client stops as the game bootstraps, with "MixinTransformerError: An unexpected critical error was encountered" while `EntityTypes`' static initializer loads a class (the crash report's "Bootstrap"; extended run 41385577023, and v0.5.1's tag run 40763699822 on 26.3, so not task 3's). Extended run 41556865877's evidence step printed the innermost cause on 26.2 and 26.3: Friends and Foes' enum mixin into `MobCategory`, which Forge's Mixin can't apply (ENUM-7). The Forge 64 to 66 corpus servers end right after MixinExtras initializes, with no exception or crash report in their logs; the step printed the smoke script's output as the console, not the server's own (`server.out`), so their stop is unread, likely the same (`MobCategory` loads in the server's bootstrap too): UNVERIFIED. On Forge 61 the client's log also has CORPUS-54's exception. After the sync (extended run 41569829683), with ENUM-7's first fix: the Forge 66 corpus client and server both stopped in Forge's enum extender (ENUM-7's Forge case, its row); the Forge 61 client stopped on Balm's second crop hook (SHARE-1), and the Forge 61 server's console ends in log4j's frames of a trace whose top isn't printed, likely the same (`Blocks` loads in the server's bootstrap too): UNVERIFIED. | Clients and servers: ENUM-7 and SHARE-1, to verify after the next sync; Forge 61's server read then. PRE-18's check waits on the clients reaching a world. |
| CORPUS-54 | P0 on Forge 61 and Forge 47 with the corpus; P0 on NeoForge 21.1 clients with Puzzles Lib (the 1.21.1 corpus client); fixed, verified on NeoForge and Forge 47; Forge 61's stop moved to SHARE-1 | `MixinPreflight.absentLocalType` (a variable modifier picking its local by its type alone), `absentLocalTypeAtPoints` (new: the same at the points its `@At` picks) | The Forge 61 and Forge 47 corpus servers stop as Mixin applies an implicit variable modifier in `CropBlock.getGrowthSpeed`, handler `getGrowthSpeedCaptureLocals`: "InvalidImplicitDiscriminatorException: Found 0 candidate variables but exactly 1 is required" (extended runs 41385577023 and 41386136422). The same on NeoForge 21.1: the 1.21.1 corpus client crashed in its world (exit status 255, extended run 41512581949) on Puzzles Lib's `ScreenEffectRendererFabricMixin`, a `@ModifyVariable` at the `STORE` of a `BlockState` in `ScreenEffectRenderer.renderScreenEffect`, where NeoForge keeps the block state in a `Pair` and stores no `BlockState` ("Critical injection failure: Variable modifier method renderScreenEffect(...)"; APPLY-1 recorded it for the next launch). Pre-flight checked only that such a method stores some local. On Forge the handler is Balm 7.3.44's `FabricCropBlockMixin.getGrowthSpeedCaptureLocals` (read in its source): it modifies the `BlockState` at the first call of `BlockState.is(Block)`, vanilla's farmland check inside the loop; Forge's build asks `canSustainPlant` there, so the first call is after the loop, where the state's local is gone, though the method has one (the method-wide check passed it). | Fixed locally: a variable modifier picking its local by its type alone (no ordinal, index, name or `argsOnly`), whose methods have no local of that type by their local variable tables, arguments and stores, is treated as Mixin will treat it: where Mixin would stop on it, it is left out (or its mod disabled) with the reason; where Mixin would inject nothing, pre-flight warns. `MixinPreflightTest.aVariableModifierByTypeFailsWhereTheMethodHasNoLocalOfThatType` fails without it. Verified on NeoForge in extended run 41536167292 (on `32c8c5d`): the 1.21.1 corpus client stayed in its world and exited normally, where it had crashed on Puzzles Lib's hook. Forge's: pre-flight also judges the points a call or field `@At` picks, in methods with a local variable table (as Forge's patched methods have): no argument or table entry of the type there, and each slot that held one stored to again since, every instruction from that store to the point covered by the table, so that Mixin's locals there (`Locals.getLocalsAt`) can't have one (`MixinPreflightTest.aVariableModifierByTypeFailsWhereTheLocalIsOutOfScopeAtItsPoint`). Verified on Forge 47 in extended run 41569833541 (on `8cb2da3`): Balm's hook was left out, with the hook keeping state with it (STATE-1), and the corpus server went past the crop hook to CORPUS-68. On Forge 61 (extended run 41569829683) the hook was left out too, but Balm's 1.21.11 build shares the state with its second hook through MixinExtras' `@Share`, which STATE-1 doesn't follow: SHARE-1. |
| CORPUS-55 | P0 on Forge 47 with Porting Lib (the corpus client); fixed in core, to verify | Porting Lib Entity's `GuiMixin`, `MixinPreflight.indexedLocalOfOtherType`, `MixinPreflight.slotNeverHolds`, `LocalRepairs.indexedLocalRepairs` (new) | The Forge 47 corpus client stopped as it loaded `Gui` (extended run 41563919281, the console CI now prints): Mixin's `InjectionError` for `porting_lib_entity.mixins.json:client.GuiMixin`, handler `shouldRenderGuiIcon`, a `@WrapWithCondition` of `List.add` in `Gui.renderEffects` (`m_280523_`), caused by "SugarApplicationException: Failed to validate sugar @Local(index = 8) MobEffectInstance ... Unable to find matching local!". The handler takes the loop's effect, draw position and alpha by vanilla's slots (`@Local(index = 8) MobEffectInstance`, `index = 10` and `11` ints, `index = 12` float; read in its 1.20.1 source); Forge 47's `Gui`, recompiled from decompiled source, keeps other locals there, and Mixin's discriminator takes a slot only where its local has the handler's type exactly. Pre-flight judged `@Local` by name or type, never by index. The `IllegalStateException: Couldn't find method in TransformingClassLoader` this row first named is Fabric ASM's (`com.chocohead.mm.Plugin.fishAddURL`): Mixin catches it and drops that config ("Failed to select mixin config"), and the game goes on. | Fix: an `@Local(index = N)` whose slot never holds a value of its type in this build (no table entry of the type; every value stored there of another kind, or of a class none of whose superclasses is it) takes the slot the instructions storing vanilla's slot store to here (vanilla's method aligned with this build's, VANILLA-1), or else the only local of the type in scope where the injector applies; where neither tells, the injector is judged failed as its `require` says (`MixinPreflightTest.aLocalTakenByItsIndexMovesToTheSlotThisBuildKeepsItInWhereItsSlotNeverHoldsOne`). Not judged: a slot that holds another local of the type here, which MixinExtras then takes. Verify: the Forge 47 corpus client without Porting Lib's injection failure; extended run 41569833541's client stopped before `Gui` loads (CORPUS-68). |
| ENTRY-1 | P2, UNVERIFIED on real mods | `EntrypointBridge` (a mod marked failed) | A Fabric mod whose main initializer fails after it registered a server configuration task can leave a singleplayer player waiting to join: the client initializer, which registers the client's side, doesn't run for a failed mod (the test mod with Forgified Fabric API on 1.21.1 before PROVIDER-6, run 40762508369: "Waiting for player to load" until the run's time limit). The report names the failure, not the wait. | Decided in task 5 (2026-11-02): only with `"onEntrypointFailure": "disable"`, which a player sets to keep loading past a failure (the default stops on NeoForge's error screen). The failed mod's other entrypoints stay skipped, as `README.md` says; running them would run code its own initializer didn't prepare, and dropping what it registered would take registry entries other mods and saved worlds may already refer to. Listed in `COMPATIBILITY.md`'s unsupported table. |
| PROVIDER-12 | P2 for packs with Forgified Fabric API on NeoForge 21.1 clients, UNVERIFIED | Forgified Fabric API's 1.21.1 `fabric-command-api-v2`, `fabric-recipe-api-v1`, `fabric-rendering-fluids-v1`; `EntrypointBridge.runClient` | Read in its 1.21.1 source while reading PROVIDER-11: some of its modules subscribe to NeoForge's events from their own Fabric main or client entrypoints, which Fabricated runs on a client in `Minecraft`'s constructor, after NeoForge's registration events: its command API's `RegisterEvent` listener for the command argument types Fabric mods register, its recipe API's `DeferredRegister` of ingredient types (`fabric_wrapper`), and its fluid rendering's `FMLClientSetupEvent`. On a client those would miss their events, so a Fabric mod's argument type or custom ingredient may be missing on a 1.21.1 client with it, and a client joining a server would then see a registry mismatch. UNVERIFIED: no run shows it. | Task 5 or 6: a coexisting client check that a Fabric mod's argument type registered through it is in the registry; then run the provider's own entrypoints before NeoForge's events, or hand late registrations over, as PROVIDER-11 does. |
| LVT-1 | P0 on the Forge 65 server with owo-lib (the corpus server); on Forge 47 to 66 and NeoForge 21.1 and 21.11 for a common mixin with a local of a client class (or any class the side lacks); fixed in core, to verify | `MixinRepairs.withoutUnfoundLocals` (new), `MixinCompat.readsLocalTypes` (new), `PreflightClasses.found` | With ENUM-7 fixed, the Forge 26.2 corpus server stopped as Mixin applied owo's `ui.SlotMixin` (extended run 41578332996): "ClassMetadataNotFoundException: net.minecraft.client.gui.navigation.ScreenRectangle" in "Transform LVT -> var=scissor". owo 0.13.1's common slot mixin keeps a client-only rectangle in a field and its setter (read in its 26.2 source). SpongePowered's Mixin (0.8.5 to 0.8.7, read in their sources) looks up the class of every local in a mixin method's debug table as it merges the method (`MixinTargetContext.transformLVT` through `ClassInfo.forName`) and throws on one it can't find; Fabric's does too before 0.17.0, and from it asks first whether the class is a mixin and looks up nothing else, so on Fabric the mixin applies on a dedicated server. Why tests missed it: no test mixin had a local of a class missing on its side, and the corpus stopped earlier (ENUM-7). | Fix: where the running Mixin looks locals up, pre-flight leaves out of the debug tables of a mixin's methods the locals whose class it doesn't find on this side (the game's, the installed jars', the mod's own, the JDK's and libraries'), with an INFO line; the tables are debug information only, and a local of another Fabric mod's class going too costs a debugger its name (`MixinPreflightTest.aLocalOfAClassThisSideLacksLeavesTheDebugTableWhereMixinLooksItUp`). Verify: the Forge 26.2 corpus server past owo's slot mixin. |
| PROVIDER-13 | P0 for packs with Forgified Fabric API on Forge 47 once coexisting with it (forge-legacy's 3b8bdf4, not merged): the server stopped loading data packs; fixed in core, to verify | `NativeModules.modules` (new), `NativeModules.standDown`, `Pipeline.providedElsewhere`, `Pipeline.Result.alsoProvidedElsewhere` (new), the locators | With coexisting tried, the Forge 47 server with Forgified Fabric API 0.92.6 stopped as the data packs' tags loaded (extended run 41578329787's informational "server with Forgified Fabric API" step): "NoSuchMethodError: FuelRegistryImpl.apply(Map)" from Fabricated's fuel hook (`FabricFuels`, FUEL-1), and "LootTable cannot be cast to FabricLootSupplier". Two gaps in the provider policy: only the plan's modules could count as provided, so the parts of the in-game mod serving modules Fabricated passes through (fabric-content-registries-v0's fuel and brewing hooks, the renderer seams of fabric-renderer-api-v1) stayed beside the provider's copy, and Forgified Fabric API's own fuel registry has no `apply`; and a mixin config in a provided module's package stood down whole, with the mixins in its sub-package of a module the provider lacks (Forge 47's loot config, in fabric-loot-api-v2's package, has fabric-loot-tables-v1's table mixins, and Forgified Fabric API 1.20.1 has no fabric-loot-tables-v1), which Fabricated's copy of that module needs. Why tests missed it: the stand-in provider's gate provides plan modules only, with no sub-package config, and the real one's run was refused until 3b8bdf4. | Fix: a module beyond the plan that the data files name (`native-modules.json`, `mixin-modules.json`) counts as provided too, for the stand-down and for the in-game mod's bridges (`FabricatedState.providedElsewhere`); a config in a provided module's package keeps the mixins of a sub-package whose module isn't provided, and stands down whole only when none is left or its plugin's package leaves (`NativeModulesTest.aConfigOfAProvidedModuleKeepsTheMixinsOfAModuleNotProvided`, `aConfigWhosePluginLeavesStandsDownWhole`). On forge-legacy, by the sync: fabric-loot-tables-v1's initializer runs whenever Fabricated's copy of it does, on the provider's fabric-loot-api-v2 events. Verify: the Forge 47 server with Forgified Fabric API past its data packs, then 3b8bdf4 merges. |
| CORPUS-68 | P0 on Forge 47 with a Fabric mod using `@ModifyArgs` (the corpus server and client); fixed on both Forge branches, to verify | The Forge transformation service's `completeScan` (new), `loader/forge-shared` | The Forge 47 corpus server and client stopped in their bootstrap with "NoClassDefFoundError: org/spongepowered/asm/synthetic/args/Args$5" as `Fluids` loaded (extended run 41569833541): the class Mixin generates for a `@ModifyArgs` handler's arguments. ModLauncher's class loader finds a game class only in a module of the game layer that holds its package, and offers one it has no bytes for to the launch plugins, where Mixin generates it; Mixin 0.8.7 (Forge 61 to 66) adds a module of its own holding its generated classes' packages (`mixin_synthetic`), and Forge 47's Mixin 0.8.5 adds none, so the class loader asked its parents, which have no such class. Why tests missed it: the test mods use no `@ModifyArgs`, and the corpus stopped earlier (CORPUS-54). | Fix: where the running Mixin's transformation service doesn't add that module itself (no `completeScan` of its own, so Mixin before 0.8.6), Fabricated's adds one to the game layer, holding the two packages and no files (an empty directory under `.fabricated`), as Mixin 0.8.7 does; not when no Fabric mod is loaded, or when a mod file already holds one of the packages, as two modules of a layer can't. A no-op on Forge 61 to 66. Verify: the Forge 47 corpus server and client past `Fluids`. |
| CORPUS-69 | P0 on Forge 47 with Lithium (the corpus server and client); fixed in core, to verify | `MixinRepairs.superOnlyIntrinsic` (new), `MixinPreflight.checkMerged` | With CORPUS-68 fixed, the Forge 47 corpus server and client stopped as Mixin applied Lithium's `inventory_change_listening.ChestBlockEntityMixin` (extended run 41574441274): "@Intrinsic method must be prefixed interface method, no rename encountered" on `setCachedState` (`m_155250_`). The mixin declares that method only to call the superclass's, so that its `@Inject` at the method's return has a method to inject into; vanilla's chest has none, and Forge 47 patches the chest to override it. Mixin (0.8.5 and 0.8.7) merges an `@Intrinsic` into a method its target has only for an interface method it renamed, and throws on any other that isn't synthetic, whatever the config. Why tests missed it: pre-flight passed every `@Intrinsic` as one Mixin merges, and the corpus stopped earlier (CORPUS-68). | Fix: an `@Intrinsic` (not displacing) of a class mixin that only calls its superclass's method with its own arguments is dropped where every target declares that method, not static nor abstract: the target's method is kept, as `@Intrinsic` keeps a method its target has, and the injector injects into it; nothing is lost, as the dropped method only stood in for the superclass's (an INFO line in the report). Another `@Intrinsic` the target declares (code of its own, or displacing the target's) is reported as Mixin rejecting the mixin, which disables its mod or leaves the mixin out where it can be (`MixinPreflightTest.anIntrinsicOnlyCallingSuperIsDroppedWhereTheTargetDeclaresIt`). Verify: the Forge 47 corpus server and client past Lithium's chest. |
| SHARE-1 | P0 on Forge 61 with Balm (the corpus client, CORPUS-53); on every platform for a left-out injector sharing a local with others through MixinExtras' `@Share`; fixed in core, to verify | `MixinPreflight.shares`, `MixinPreflight.leaveOutSharers` (new) | The Forge 61 corpus client stopped in its bootstrap as `Blocks` loaded: "Critical injection failure: Variable modifier method getGrowthSpeed(FL...Block;L...BlockGetter;L...BlockPos;L...LocalRef;)F in balm.fabric.mixins.json:FabricCropBlockMixin failed injection check, (0/1) succeeded" (extended run 41569829683). Balm's 1.21.11 build (read in its source) captures the block state below with `getGrowthSpeedCaptureLocals` into a local shared through `@Share("farmBlock")`, and its second hook, a variable modifier of the second float local at the same calls, reads it. Pre-flight left the first out (CORPUS-54) and the second alone, which read no state, and failed besides: Forge's build has no second float where it applies. STATE-1 groups only injectors sharing a mixin's fields; Balm's 1.20.1 build shares a static field, which it followed on Forge 47. Why tests missed it: no test had injectors sharing a local through `@Share`. | Fix: a left-out injector takes with it every injector of its mixin sharing a local with it through `@Share` (by namespace and name), with the reason (`MixinPreflightTest.anInjectorSharingALocalWithOneLeftOutGoesWithIt`); a not-required one Mixin skips is left to Mixin, as on Fabric. Not judged: a variable modifier of a primitive local by its ordinal where too few such locals are in scope, as the second hook was. Verify: the Forge 61 corpus client past `Blocks`. |
| CORPUS-66 | — (CI only) | The 1.21.1 advanced corpus client's run (Origins) | The 1.21.1 advanced client reached its world (extended run 41539993361: the player's chunk loaded after 600 ticks) and then ran to the 10-minute limit: Origins opened its `ChooseOriginScreen` as the player joined, and mc-runtime-test waits for no screen before it finishes ("Screen not yet null"). As on Fabric, the screen waits for a player's choice. CORPUS-64's crash had hidden it. | Not Fabricated's. Task 5 or 6: the run should not wait on a mod's screen asking the player to choose (a corpus check that the world was reached, or Origins' choice made for the test), so the run says what loads. |
| PROVIDER-5 | — | Forgified Fabric API's `fabric-networking-api-v1` (1.21.1 seen) | With it providing the module, NeoForge refuses a payload type in the `minecraft` namespace, which Fabricated's own networking registers (NET-4): the Fabric mod's initializer throws "Cannot register payload minecraft:... using the domain \"minecraft\"" and it is marked failed (the test mod's payload on the 1.21.1 client run, 40738724928; BetterEnd's ritual update UNVERIFIED). | Not Fabricated's: the module is the provider's (*Standing decisions*, 2026-10-28). The test mods skip that case when another mod provides networking. |
| PROVIDER-7 | — | Forgified Fabric API's `fabric-item-api-v1` on 26.x (26.1.2 and 26.2 seen) | Its `IItemExtensionMixin` answers NeoForge's `getEquipmentSlot` for an item with Fabric's equipment slot provider by casting the item to `LivingEntity` (`(LivingEntity) this`, read in its 26.1.2 source), which throws `ClassCastException`: the test mod's item stopped the servers with Forgified Fabric API once PROVIDER-6 let its initializers run (this change's CI, run 40772701272). A Fabric mod's item with an equipment slot provider (`FabricItem.Settings.equipmentSlot`) would stop the game wherever NeoForge asks its slot. | Not Fabricated's: the module is the provider's (*Standing decisions*, 2026-10-28); worth reporting to Forgified Fabric API (the owner's call). The 26.x test mod skips its item checks when another mod provides the module. |
| KEYMAP-1 | P3 | Fabric mods' accessors to `KeyMapping.MAP` (OneConfig's keybind registry) | NeoForge retyped the field to `KeyMappingLookup`, so a `Map` accessor is left out and its callers throw `NoSuchMethodError`. OneConfig catches it (warning that its keybinds may shadow Minecraft's) and leaves the Controls screen mirrors of its keybinds registered with Minecraft's keybinds, which on Fabric it takes out again as it makes each one (`MinecraftKeybindBridgeImpl.detached`, read in its source and in its 1.21.1 jar in extended run 41496005222). They don't shadow Minecraft's there: the mirrors have OneConfig's own names, and NeoForge's lookup keeps every binding of a key, where vanilla's map keeps the last. P3 since then (was P2). | Open, documented in `COMPATIBILITY.md`: a `Map` view of NeoForge's lookup, served for such an accessor, would take the mirrors out as on Fabric; built if a mod needs it for more than that. |
| CI-3 | P3 (CI only) | The client checks of `.github/workflows/build.yml` and `ci/neoforge/smoke-client.sh`, which read `logs/latest.log` | A client run crossing 00:00 UTC has its log rotated by log4j: the lines before midnight move to `logs/<date>-1.log.gz`, and their checks fail with the lines missing (#230's run 40790221936 on 26.1.2: `model deserializer OK`, `neoforge blockstate type OK`, `key mapping OK`, all in the rotated file). | Open: after the game exits, the smoke script puts the run's rotated logs (oldest first) in front of `latest.log`; the run directory is new each step, so they are this run's. |
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
| SEC-5 | P3 | `CandidateScanner` (nested jars) | Nesting is bounded per top-level jar (depth 8, 1024 jars, 512 MiB of nested jars), not across all of them: many hostile jars cost that much each. The 1 MiB cap on metadata applies after reading the entry, which since SEC-4 is bounded by its declared size and the compression ratio. | Bounded by what the player puts in `mods/`; a global budget would need a size no legitimate pack exceeds. |
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
