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
| 4 | Fabric's renderer API on NeoForge and Forge (*FRAPI plan*) | Next |
| 5 | General compatibility, the difficult mods' Fabric builds first (*Difficult mods*) | |
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
| The Fabric builds of Sodium and Iris | Run them, gate them on every version they have builds for, lift the refusal | 5 |
| Essential's Fabric build | Run it, on what its loader really needs (UNVERIFIED until its source is read), lift the refusal | 5 |
| malilib and the mods needing it | They reach a world on 26.3 (run 40800647792): gate on every version, lift the refusal | 5 |
| Flashback | Run it without the refusal, which rests on a registry freeze CORPUS-32 moved; lift it if Flashback starts | 5 |
| WTHIT's Fabric build | Stays refused: its own entrypoint refuses NeoForge, and WTHIT's NeoForge build reads the same plugin files | — |
| Login queries (`ServerLoginNetworking`) | Unsupported today: NeoForge has no hook there. Candidate: run them in the configuration phase, where NeoForge has tasks (whether mods' queries tolerate the later phase is UNVERIFIED) | 5 |
| `preLaunch`, run with `main` | Moved after FML's mixin bootstrap, closer to where Fabric runs it (Sodium's and Essential's early code) | 5 |
| Fabric models in 1.21.1's chunk sections (FRAPI-5) | Built | 4 |
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
| Fabric's renderer and model APIs (the renderer test mod: sections, nested models, moving blocks, the breaking overlay, items) | gates on 26.x and 1.21.11, items aside; 1.21.1 after FRAPI-5 | none yet (task 4) | none yet (task 4) |
| Sodium and Iris for NeoForge | gates on 26.x; 1.21.11 informational | — (no Forge builds) | — |
| The Fabric builds of Sodium and Iris, with a shader pack | informational on 26.x (they reach a world on 26.3) | after NeoForge, where they have builds (task 5) | as Forge 61 to 66 |
| OneConfig and PolyPlus; malilib with MiniHUD and Tweakeroo | informational (both reach a world on 26.3) | not run | not run |
| Packs sharing the game with Forgified Fabric API and Sodium for NeoForge | the server a gate, the clients informational | Forgified Fabric API refused (PROVIDER-2), coexisting tried in task 5 | as Forge 61 to 66 |
| Popular Fabric mods: both corpora reach a world, every mod not loaded named with a reason | informational; 1.21.1's stops (CORPUS-50 to 52) | informational; every corpus stops before a world (CORPUS-53, CORPUS-54) | informational; stops before a world (CORPUS-54, CORPUS-55) |
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

**What blocks it** (2026-11-02, after task 3): on NeoForge, FRAPI-5; on Forge, PRE-18 until a corpus client checks it,
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

### Difficult mods (task 5)

Decision 3. The rules of the rendering-pipeline track hold (generic capabilities first; no code copied from a mod;
nothing on a per-block or per-vertex path allocates or locks; a check before "supported"), with one change: where no
generic capability can do the job, Fabricated may carry a compatibility patch for one mod, as data or as a patch mod
(`API.md`, *Compat patches*), written from the mod's public sources, never copied from them. Before such a patch ships
for a mod under a restrictive licence (Sodium's PolyForm Shield), the owner settles the licence question (open).

Where each stands, and what it needs:

| Mod | Today | Needs | Gate |
|---|---|---|---|
| Sodium (Fabric build) | Refused by default. With `loadUnsupported` on 26.x it meshes the world, and the renderer test mod's checks pass under it (26.3, run 40800647792); 1.21.1 and 1.21.11 never ran | What its Fabric platform code doesn't answer the NeoForge way: NeoForge's fluid renderers (through Fabric's fluid render handlers), the geometry NeoForge mods add to sections (`AddSectionGeometryEvent`), position-aware light emission and face culling, baked colours in immediate mode (*Rendering-pipeline mods*) | The Fabric Sodium client on every version with a build, a NeoForge mod's fluid and section geometry in view |
| Iris (Fabric build) | Runs with Sodium's on 26.x (`loadUnsupported`); shaders not checked with it | A shader pack drawing with NeoForge's own render pipelines | The same client with CI's shader pack: Iris's "Using shaderpack", and no pipeline errors |
| The mixed pairs (Iris for NeoForge with Sodium's Fabric build, and the reverse) | Refused even with `loadUnsupported` | Nothing: each hooks classes only its own partner build has, so it can't link | Kept refused, with the reason |
| Essential (Fabric build) | Refused | Its loader downloads Essential when the game starts and adds it through Fabric Loader's internals (the mod list, the class loader, Mixin's configs). What those internals must do, and whether it can happen early enough on FML, is UNVERIFIED until its loader's source is read; the downloaded jar must go through Fabricated's pipeline | A client with Essential's Fabric build reaching the title screen, offline |
| OneConfig, PolyPlus, Polydium | Reach a world on 26.3 and 1.21.1 (CORPUS-47). adventure-platform-fabric is disabled (its mixins give library classes Adventure's interfaces), and OneConfig's keybinds can't reach `KeyMapping.MAP` (KEYMAP-1) | A way to give library classes a mod's interfaces (LIB-1 covers only a mod's own); KEYMAP-1's `Map` view | The Polyfrost client on every version with builds, adventure-platform-fabric loaded |
| malilib, MiniHUD, Tweakeroo, Litematica | Refused; with `loadUnsupported` they reach a world on 26.3 (PIP-1) | Runs on the other versions; Litematica added to the run | The malilib client on every version with builds |
| Flashback | Refused (CORPUS-21) | A run without the refusal (CORPUS-32 moved the freeze the refusal rests on) | The advanced corpus client |

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
- **Task 4 (FRAPI):** the plan above.
- **Task 5 (compatibility):** the difficult mods above; then the corpora by impact, the login queries candidate,
  `preLaunch` moved, ENTRY-1, LINK-3, PRE-19 and KEYMAP-1. On Forge: the corpus stops (CORPUS-53 to 55) and PRE-18's
  check, item storages in items, the effect events' hook, Fabric mods' own HUD mixins and coexisting with Forgified
  Fabric API; HUD-3 on NeoForge.
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
| Rendering (renderer API, models, Sodium and Iris for NeoForge) | yes | upstream's modules: Indigo builds chunk sections on all four (SUPER-1, LOCAL-4), Fabric's colour providers are read from Forge's map (COLOR-1), and Fabric's block dust tint event is asked where Forge 61 decides the tint (PART-2) | upstream's renderer API, Indigo, `fabric-rendering-v1` and model loading load, with Fabric's colour providers read from Forge's maps (COLOR-1), model loading's constructor hook wrapped (CTOR-4) and its before-bake hook moved to the overload Forge's code went to (MODEL-3); particles Fabricated's own, on Forge's particle engine (PART-1) |
| CI | server and client runs gated, and checks in the Fabric API runs; corpus runs informational | both server runs and both client runs gated, with a stand-ins server and a refusal server; corpus runs informational | as Forge 61 to 66 |
| Releases | supported; beta from 0.6.0 | experimental until 0.6.0-beta, then held to the same bar (decision 1) | experimental until 0.6.0-beta, then held to the same bar (decision 1) |

- **`main`:** NeoForge for 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3, every line stable (NeoForge tagged `26.3.0-stable`; CI
  pins 26.3.0.95 and no version is experimental any more).
- **Next** (the v0.6.0-beta pass, after task 3): FRAPI's renderer test mod on both Forge branches (task 4, R5); then
  (task 5) the corpus stops (CORPUS-53 to 55) and PRE-18's check, the effect events' hook into Brigadier, which Forge
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
  (`compat-plan.json`, CORPUS-21) rests on NeoForge's bootstrap registry freeze, which CORPUS-32 moved after the
  entrypoints: whether Flashback still needs it is UNVERIFIED (a run with it lifted would tell).

## Findings

Open findings, by ID, with the severities [`FINDINGS.md`](FINDINGS.md) defines. A finding moves to `FINDINGS.md`,
with its cause, why the tests missed it and its fix, in the change that fixes it.

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| PRE-18 | P0 on Forge 65 and 66 clients, UNVERIFIED cause | `MixinPreflight` (MixinExtras `@Local` sugar in a method Forge patched) | ImmediatelyFast's `map_atlas_generation.MixinGuiGraphicsExtractor`, a `@WrapOperation` taking a `LocalRef`, stopped the Forge 65 corpus client ("failed injection check, (0/1) succeeded. Scanned 0 target(s)"), and Mod Menu's `MixinTitleScreen`, an `@Inject` taking a `LocalIntRef` and a `LocalBooleanRef`, the Forge 66 one ("SugarApplicationException: Unable to find matching local!"), run 40570195894. Pre-flight reported nothing for either. Which locals Forge's patched methods have there, and why pre-flight's `@Local` checks passed the hooks, is UNVERIFIED. | Fixed in core on `main` (task 3: a MixinExtras local taking an argument by its name gets the argument's slot, found as ARGS-1 finds it; pre-flight reports one it can't place), and synced to both Forge branches (#240, #241). Mod Menu stays out on Forge (REPL-1), its API now Fabricated's own there. Not yet checked end to end: the Forge 61 to 66 corpus clients now stop as the game bootstraps (CORPUS-53, extended run 41385577023), before ImmediatelyFast's hook loads; the check moves to task 5 with CORPUS-53. |
| FRAPI-5 | P1 on NeoForge 21.1 clients with Fabric API (Fabric models with their own `emitBlockQuads`) | 1.21.1 in-game mod (no counterpart of 1.21.11's `SectionCompilerMixin`) | Indigo's hook that sends a chunk section's blocks through Fabric's renderer is left out on 1.21.1 (NeoForge 21.1's `renderBatched` takes model data and a render type), and Fabricated has no bridge of its own there, so a Fabric model that isn't a vanilla adapter isn't drawn in chunk sections; NeoForge's model data and its own models are unaffected. 1.21.11's bridge is built on that version's block model parts, so 1.21.1 needs one on `BakedModel` and 0.116's `FabricBakedModel`. | Open. Decided in task 3: after the 1.21.1 port, measured on its corpus first (connected-texture and other renderer API mods). |
| PRE-19 | P2 | `MixinPreflight.overloadRepairs` (a variable modifier picking its local by its place among the locals) | Puzzles Lib's break speed hook is left out on Forge 47 (PRE-17) where it could move to `getDigSpeed`, the method Forge deprecated `getDestroySpeed` for: the overload repair moves only variable modifiers that pick their local by type alone (MODEL-3), as the other method's code could have other locals of that type first (LOCAL-4). | Move one by its ordinal where the other method adds no parameter of its type and its local variable table places the local the same, checked against vanilla's code (VANILLA-1). |
| LINK-3 | P3 | `LinkageAnalysis.unloadedUses` (LINK-2, a plugin config's mixin) | A mixin adding an interface of a mod that isn't loading disables its mod even where its config's plugin would skip it at runtime (a compatibility mixin for that mod). | Leave out that mixin only, as CORPUS-7 does during translation; needs the linkage round to feed the skip back into translation and its cache key. |
| CORPUS-34 | — | JEI's Fabric jar (Forge 61 to 66 corpus) | Forge's mods folder scan reads every jar's module-info; JEI's Fabric jar names packages it lacks, so Forge refuses it and loads only itself (LOADERR-1). | Not Fabricated's: JEI's Forge build is used. Left out of the Forge corpus runs (`00c3f37`). |
| CORPUS-35 | — | YACL's Fabric jar (Forge 47 corpus) | Its 3.6.6 Fabric jar carries a `mods.toml` asking for no `javafml` version, which Forge 47 refuses, loading only itself (LOADERR-1). | Not Fabricated's: YACL's Forge build is used. Left out of the Forge 47 corpus run (`00c3f37`). |
| CORPUS-36 | P2 | Farmer's Delight Refabricated (Forge 61 to 66 corpus) | Disabled: it "adds enum constants with Mixin's enum extensions, which need Mixin 0.17.1 or later; this Forge runs Mixin 0.8.7". Forge reads no enum extension file to translate them into (ENUM-1 is NeoForge's). | Not repairable on Forge's Mixin; its Forge build is used. |
| CORPUS-37 | P2 | Porting Lib's accessors module (Forge 47 corpus: Create Fabric, Farmer's Delight Refabricated) | Pre-flight disables it: accessors of fields Forge 47 retyped (`MinecraftServer.f_129740_`, `PotionBrewing$Mix.f_43532_` and `f_43534_`, and on the client `ParticleEngine.f_107293_`, PART-1's map), and with it the modules and mods needing it (LINK-2 for the base module's texture interface). | Not repaired: whether a bridge can reach each field's new type is UNVERIFIED; the mods' Forge builds are used. |
| CORPUS-38 | P3, UNVERIFIED | Fabric Language Kotlin (Forge corpus runs) | The linkage check lists `KotlinLanguageAdapter -> missing class net.fabricmc.loader.language.LanguageAdapter`, Fabric Loader's old adapter interface, which Fabricated doesn't provide. Kotlin mods load through its current adapter; whether any mod still names the old one is UNVERIFIED. | Provide the old interface if a mod needs it. |
| CORPUS-48 | P2 on NeoForge 26.2 and 26.3 clients with Xaero's World Map 1.47.2 (the corpus client), UNVERIFIED for a player's quit | Xaero's World Map's `WorldDataHandler.onServerWorldUnload` | Once the 26.2 and 26.3 corpus clients reached their world, mc-runtime-test's stop halted the integrated server, and its thread waited in `WorldDataHandler.onServerWorldUnload` (`xaeroworldmap@1.47.2`, line 106, from `Level.close` in `MinecraftServer.stopServer`) while the render thread joined it, until the run's 10-minute limit (run 41344706881's thread dump, 26.3; 26.2's client ran as long). In v0.5.1's tag run (40800647792) the same clients exited in about a minute, and the 1.21.11 and 26.1.2 ones exit normally. What it waits on, and whether a player's own quit to the title screen hangs, is UNVERIFIED. | Task 5: reproduce with Xaero's maps alone on 26.3, and read what the unload waits for. The client runs stop such a hang 2 minutes after the world is reached (`SMOKE_EXIT_TIMEOUT`). |
| CORPUS-50 | P0 on NeoForge 21.1 with Supplementaries and Moonlight (the 1.21.1 corpus server), UNVERIFIED cause | Supplementaries' dynamic data (Moonlight's dynamic packs) | Past CORPUS-44 and CORPUS-49 the 1.21.1 corpus server fails to load its data packs: "Missing tag: 'supplementaries:timber_frames' in 'minecraft:item'" and more of Supplementaries' item tags, after Supplementaries logged "Failed to generate recipe for sign post" (diagnostics run 41362521380). Why its dynamic data isn't there is UNVERIFIED. | Task 5: read what Supplementaries' generation throws, and whether Moonlight's dynamic packs reach the server's data. |
| CORPUS-51 | P0 on NeoForge 21.1 with FrozenLib (the advanced corpus server) | FrozenLib's `frozenlib_quiltmc_registry.mixins.json:MappedRegistryMixin`, pre-flight's local capture check | Past CORPUS-46 the advanced corpus server stops as Mixin applies `@Inject quilt$invokeEntryAddEvent`: "InvalidInjectionException: Invalid descriptor" for the locals it captures in `MappedRegistry.register` (diagnostics run 41362521380). Pre-flight reported it as a local capture to skip if NeoForge's locals differ, and let it through. | Task 5: compare its captured locals with NeoForge 21.1's `register`, and why pre-flight's check passed them. |
| CORPUS-52 | P0 on NeoForge 21.1 clients with ImmediatelyFast (the 1.21.1 corpus client) | ImmediatelyFast's `GuiGraphics` `@WrapMethod` `restoreDepthTestState`, its access widener | The client crashes as it renders: "IllegalAccessError: class net.minecraft.client.gui.GuiGraphics tried to access private field com.mojang.blaze3d.platform.GlStateManager.DEPTH" (diagnostics run 41362521380). The access widener line that opens the field wasn't applied on NeoForge 1.21.1; why is UNVERIFIED. | Task 5: read ImmediatelyFast's access widener for the field and what Fabricated's converter made of it. |
| CORPUS-53 | P0 on Forge 61 to 66 with the corpus, UNVERIFIED cause | The Forge 61 to 66 corpus clients, and the Forge 64 to 66 corpus servers | Every Forge 61 to 66 corpus client stops as the game bootstraps, with "MixinTransformerError: An unexpected critical error was encountered" while `EntityTypes`' static initializer loads a class (the crash report's "Bootstrap"; extended run 41385577023, and v0.5.1's tag run 40763699822 on 26.3, so not task 3's). The Forge 64 to 66 corpus servers end right after MixinExtras initializes, with no exception or crash report the corpus summary finds. The summary prints a crash report's first lines, not its innermost cause, which is in the runs' corpus artifacts; which mod's mixin fails, and whether the servers stop on the same, is UNVERIFIED. On Forge 61 the client's log also has CORPUS-54's exception. | Task 5: read the crash reports' causes (the corpus summary could print a crash's innermost cause), then fix pre-flight or the mod's case. PRE-18's check waits on it. |
| CORPUS-54 | P0 on Forge 61 and Forge 47 with the corpus | Pre-flight's variable modifier checks (a corpus mod's `@ModifyVariable` into `CropBlock.getGrowthSpeed`) | The Forge 61 and Forge 47 corpus servers stop as Mixin applies an implicit variable modifier in `CropBlock.getGrowthSpeed`, handler `getGrowthSpeedCaptureLocals`: "InvalidImplicitDiscriminatorException: Found 0 candidate variables but exactly 1 is required" (extended runs 41385577023 and 41386136422). Forge patches that method (CORPUS-3 notes NeoForge's change to it), and pre-flight let the hook through. Which corpus mod's handler it is is UNVERIFIED (the summary cuts the line). | Task 5: find the mod, and why pre-flight's check of an implicit variable modifier passed a method with no candidate local of its type. |
| CORPUS-55 | P0 on Forge 47 with the corpus, UNVERIFIED cause | The Forge 47 corpus client | Past pre-flight, the Forge 47 corpus client reaches no world; its log has "IllegalStateException: Couldn't find method in cpw.mods.modlauncher.TransformingClassLoader" (extended run 41386136422; the server's log has it too, before CORPUS-54 stops it). What looked the method up, and which method, is UNVERIFIED. | Task 5: read the client's log in the run's corpus artifact. |
| HUD-3 | P3, UNVERIFIED in a run | NeoForge's `HudBridge` (every version with `HudElementRegistry`) | Vanilla draws the experience level, the held item's name and the spectator tooltip only in some game modes, and upstream's mixin wraps those calls inside vanilla's conditions, so a Fabric element attached to one of them, or replacing it, follows it. NeoForge's layers check the conditions inside themselves and the bridge wraps each layer whole, so such an element is drawn in modes where vanilla's isn't (read in the bridge's code; whether the health bars, which vanilla draws only where the player can be hurt, have the same gap is UNVERIFIED). Forge's bridge checks vanilla's conditions first (HUD-2). | Task 5: check vanilla's condition around each of those layers, as Forge's bridge does. |
| ENTRY-1 | P2, UNVERIFIED on real mods | `EntrypointBridge` (a mod marked failed) | A Fabric mod whose main initializer fails after it registered a server configuration task can leave a singleplayer player waiting to join: the client initializer, which registers the client's side, doesn't run for a failed mod (the test mod with Forgified Fabric API on 1.21.1 before PROVIDER-6, run 40762508369: "Waiting for player to load" until the run's time limit). The report names the failure, not the wait. | Open: running a failed mod's other entrypoints, or dropping what it registered, each have a cost; to decide. |
| PROVIDER-5 | — | Forgified Fabric API's `fabric-networking-api-v1` (1.21.1 seen) | With it providing the module, NeoForge refuses a payload type in the `minecraft` namespace, which Fabricated's own networking registers (NET-4): the Fabric mod's initializer throws "Cannot register payload minecraft:... using the domain \"minecraft\"" and it is marked failed (the test mod's payload on the 1.21.1 client run, 40738724928; BetterEnd's ritual update UNVERIFIED). | Not Fabricated's: the module is the provider's (*Standing decisions*, 2026-10-28). The test mods skip that case when another mod provides networking. |
| PROVIDER-7 | — | Forgified Fabric API's `fabric-item-api-v1` on 26.x (26.1.2 and 26.2 seen) | Its `IItemExtensionMixin` answers NeoForge's `getEquipmentSlot` for an item with Fabric's equipment slot provider by casting the item to `LivingEntity` (`(LivingEntity) this`, read in its 26.1.2 source), which throws `ClassCastException`: the test mod's item stopped the servers with Forgified Fabric API once PROVIDER-6 let its initializers run (this change's CI, run 40772701272). A Fabric mod's item with an equipment slot provider (`FabricItem.Settings.equipmentSlot`) would stop the game wherever NeoForge asks its slot. | Not Fabricated's: the module is the provider's (*Standing decisions*, 2026-10-28); worth reporting to Forgified Fabric API (the owner's call). The 26.x test mod skips its item checks when another mod provides the module. |
| KEYMAP-1 | P2 | Fabric mods' accessors to `KeyMapping.MAP` (OneConfig's keybind registry) | NeoForge retyped the field to `KeyMappingLookup`, so a `Map` accessor is left out and its callers throw `NoSuchMethodError` (OneConfig catches it; its keybinds may shadow Minecraft's). | Open: a `Map` view of NeoForge's lookup, served for such an accessor, would match Fabric; built if more mods than OneConfig need it. |
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
