# Fabricated: changelog

One entry per working session, newest first: what changed, and how it was verified. Where each track stands now is in
[`roadmap.md`](roadmap.md); this file is its history.

## 2026-10-25: branch reorganisation, a glowing moving block's outline, 1.21.11 server linkage noise, platform seams

**Plan.** The owner renamed the working branch to `claude/dev` and replaced `1.20.1-legacy` with `forge-legacy` (Forge
for 1.20.1 and other legacy versions); `forge` and `forge-legacy` were fast-forwarded to `main`, and `main` gained an
updated README and `.claude/settings.json`. Then two items from the rendering track and the reports.

- Docs follow the new branches (`claude.md`, `PORTING.md`, `RESEARCH.md` §8, roadmap; D1 no longer lists NeoForge
  47.1), and the README's latest edit gets a few grammar and meaning fixes.
- M2b (26.2, 26.3): a glowing moving block's outline is drawn by Fabric's renderer, into the outline's buffers, as
  upstream does; NeoForge drew it from the model's parts, which a model with only `emitQuads` doesn't have. The test
  mod's falling blocks glow, and the FRAPI gate checks that the outline pass reached Fabric's renderer.
- LINK-1: on a 1.21.11 dedicated server, classes left in Intermediary's names are client code (the server's mappings
  name only the server's classes), so mods using client classes no longer fill the report with "missing class
  net.minecraft.class_NNN" warnings (about 650 on PR #66's run; 5 on run 40227052304).
- L1 seams for the Forge branches (`PORTING.md` §6, items 2 and 3, and item 4's trigger): the loader's NeoForge
  services in `loader/neoforge` and its six loader-free helpers in `loader/common`; NeoForge's part of the build in
  `mc/platform-neoforge.gradle`; the shared source folders from each version's `shared_sources`, the pass-through
  Fabric API jars where `namespace=OFFICIAL`; `build.yml` builds pull requests to `main` only. Checked by printing
  every version project's source sets, declared dependencies and placeholders before and after (only the loader's
  folders differ), then CI on all four versions (PR #69).
- The branches' build plugins are pinned in `settings.gradle` (ModDevGradle's `legacyforge` at `main`'s version,
  ForgeGradle 7.0.40); `main` applies neither. Research for the branches is in `PORTING.md` §4 and §5: ForgeGradle 7
  needs Gradle 9.3 or later and `legacyforge` configures on Gradle 9.7.0 in a scratch build.
- The branches' scaffolds (compile only, each built by the branch's own workflow for pull requests to it):
  `forge-legacy`'s `mc/1.20.1` on `legacyforge` with Forge 47.4.28 (#71) and `forge`'s `mc/forge-1.21.11` on
  ForgeGradle with Forge 61.2.4 (#72). Their first CI runs settled risk R1 (`legacyforge` builds on Gradle 9.7.0, in
  1 min 13 s) and showed ForgeGradle 7.0.40 building on it (4 min 18 s, of which 3 min 47 s preparing Forge); both
  pins moved to the newest builds those runs listed on Forge's maven. Work on them happens on `claude/forge-legacy`
  and `claude/forge`.
- LOADER-5: Fabricated reports Fabric Loader 0.20.1 (its public API is 0.19.5's), so mods built against 0.20, such
  as Sodium Extra on 26.x, are no longer refused for an old loader; mods declaring 0.20 get Mixin behaviour 0.17.5,
  lowered to the running Mixin.
- PRE-15: pre-flight sees a method another mixin adds as a soft implementation (`@Implements` with a prefix), which
  Mixin merges without the prefix, so ShulkerBoxTooltip's ender chest hook no longer disables it on 26.x.
- The build declares Fabric's exclusive repository before the platform's, so the `forge` branch's 26.x projects
  configure (#75).
- CAPTURE-2: when an anonymous class captures several values of one type under other names in NeoForge's build,
  pre-flight tells them apart by their place in vanilla's class, checked against the order each method reads them
  in, so Ledger's and malilib's hooks into `ChestBlock`'s menu provider can apply on 1.21.11.
- LOCAL-2: an `@Local` taken by type where NeoForge's patch added a local of that type gets the ordinal of vanilla's
  local, found as the one whose store aligns with vanilla's code (Ledger's item pickup logging, Puzzles Lib's fog
  hook).
- CORPUS-24, closed: Ledger's "NOT NULL constraint failed: players.player_name" on the 26.x advanced servers is
  Ledger's own, met through the test mod's Fabric fake player (read in its source and the logged stack).
- F3 design for modern Forge recorded in `PORTING.md` §4.1, and the branches' first F3 step: a plugin-layer language
  provider nested in the `forge` jar, compile only (on `claude/forge`).
- The 26.x note on NeoForge's translucent outline pass is corrected: NeoForge decides it from the model's
  context-free material flags (not its parts), which Fabric's contract makes correct everywhere; a candidate for
  accuracy, not a fault.

## 2026-10-21 (pass after PR #67): the platform decisions, and Java 17 for the shared modules

**Plan.** The owner decided the evaluation's open questions: 1.20.1 targets Forge 47.4 first (NeoForge 47.1
best-effort), the shared engine keeps to Java 17 from now on, modern Forge builds with ForgeGradle, and secondary
branches get CI through pull requests only. D3 and D4 were left to this session and are recorded as provisional.

- D2 as a build rule: the main code of `core`, `api` and `fabric-loader-api` compiles with `release = 17` (tests and
  the toolchain stay at 21), so the compiler rejects what Java 17 can't load. 66 calls of Java 21's sequenced
  collections became index calls, 8 type-pattern switches became `instanceof` chains (three of them as small helper
  methods), and 3 `instanceof` patterns of the expression's own type became null checks. No behaviour changes: the
  281 unit tests pass unchanged, and the classes are Java 17 class files (version 61).
- `RESEARCH.md` §8: decisions D1 to D6 and the build paths per branch; `claude.md`: the Java 17 rule with what to use
  instead; `PORTING.md`: decisions recorded, L0 and F0 done, §6 item 1 done, D6's CI policy; `CONTRIBUTING.md`,
  `ARCHITECTURE.md`: the shared modules' Java level.

## 2026-10-21 (pass after PR #66): 26.3 stable, and the platform evaluation

**Plan.** PR #66's run (`9431800`) was green everywhere. Every 1.21.11 report reads vanilla's jar in Mojang's names
through its mappings, and Do a Barrel Roll loads on 1.21.11 clients too, so it now loads on every supported client.
LEFT_OUT went from 182 to 183 on 1.21.11 (Do a Barrel Roll's `InGameHudMixin`, counted now that the mod loads) and is
unchanged on 26.x. NeoForge's 26.3 line left beta (its `26.3.0-stable` tag), and the owner set a two-track plan:
`main` stays the active branch, `1.20.1-legacy` is in an architectural evaluation phase, and Forge gets a branch of
its own later.

- 26.3 is a stable target: CI pins NeoForge 26.3.0.95 and no version is experimental, so the 26.3 job is a gate like
  the others. README, CONTRIBUTING, ARCHITECTURE, COMPATIBILITY and RESEARCH no longer call it a beta.
- `PORTING.md`: the evaluation of the shared engine and the build for Minecraft 1.20.1 (Forge 47.4 first, NeoForge
  47.1 best-effort) and for Minecraft Forge on the versions `main` covers. It covers what carries over, the Java 17
  blockers, the facts each branch rests on (with sources), the build and CI layout, the behaviour-neutral seams to
  add on `main` first, the phases to a stable engine on each branch with exit criteria, risks, and the decisions
  the owner makes before any branch work starts.
- `claude.md`: the branch rule (shared modules change only on `main`; branches merge it and add files of their own).
  Roadmap: the platform tracks, and VANILLA-2 as seen on `9431800`.
- REG-4: on 26.3.0.95 the advanced corpus server and client stopped loading registries (Wilder Wild's features could not
  find FrozenLib's `config_predicate_provider` registry). NeoForge now loads from `RegistryDataLoader.WORLD_REGISTRIES`,
  which Wover replaces with a copy of its own, so registries added later were lost: Fabric mods', and NeoForge's own
  (`neoforge:biome_modifier`). Fabricated brings such a copy up to date whenever a Fabric registry is added and once
  mods have loaded; the test mod replaces the list as Wover does, so the server gate checks it.
- CI: the log digest cuts an error's text after 60 lines (NeoForge's registry report had pushed the rest of the summary
  out of the readable log), and the dedicated servers' summary lists Fabricated's warnings and errors.

## 2026-10-21 (pass after PR #65): vanilla's code on 1.21.11

**Plan.** PR #65's run (`9b322e1`) was green everywhere. Do a Barrel Roll loads on every 26.x client, each report names
its build ("Fabricated build", a digest per version), and none of the new rejections fired on the real corpus.
Do a Barrel Roll is still disabled on 1.21.11 clients. NeoForge's 1.21.11 camera patch is the one 26.x has, but
vanilla's 1.21.11 jar is obfuscated, so pre-flight had no vanilla code to read there.

- VANILLA-2: the loader finds vanilla's jar on 1.21.11 too and reads it through the Mojang mappings NeoForge's
  installer left. Each class is remapped from the obfuscated names to Mojang's. A member gets the name the class
  declaring it maps it to, found up the vanilla jar's own hierarchy.
- CACHE-3: the cache's index forgets a name it looked up but never stored (a failed translation), once unused for as long
  as the entries it would have named.
- CORPUS-22 (open): Biomes O' Plenty on 1.21.11 is disabled for a hook on a call NeoForge's patch removed. The hook's
  mixin shares state with the rest of the mod, so it can't be left out alone.
- Roadmap: VANILLA-1 recorded as seen on `9b322e1`.

## 2026-10-21 (health pass after PR #64): untrusted input, cache keys, bridged handlers

**Plan.** Before more mods: a review of the whole engine on four fronts. Stability of the translation pipeline and
pre-flight across 1.21.11 and 26.x, the untrusted-input model for Fabric jars, overhead and lightweightness, and
drift from the design documents. Four read-only reviews traced their findings to code; each defect below was
reproduced or shown by a test that fails without its fix.

- SEC-1: one crafted jar disabled every Fabric mod: a very long mixin config path or version overflowed the stack in a
  pattern, and the scan caught only checked exceptions. Now bounded and checked without recursion, and whatever a
  jar's metadata throws rejects that jar alone.
- SEC-2: a `neoforge.mods.toml` of blank lines hung discovery for minutes (a quadratic pattern). Now linear.
- SEC-3: a hostile mixin class in one jar failed, and blamed, every other mod whose pre-flight read it. Now skipped as
  unreadable.
- SEC-4: an entry may inflate past the size its header declares, which the ratio check and memory reservations
  trusted. Such an entry is refused when read.
- CACHE-1: the cached linkage result ignored overrides and installed NeoForge mods (a `skipMixins` didn't bring a
  disabled mod back). Its key now holds each mod's translation and the NeoForge mods.
- CACHE-2: a new build of the same version reused older translations unless the schema was bumped by hand, which six
  repairs didn't do. The build now records a digest of Fabricated's code and data, which the keys include.
- BRIDGE-1: bridging a handler left its MixinExtras expression annotations behind. They move with the injector.
- BRIDGE-2: a handler's independence from its mixin's other injectors was judged mid-repair. Now on the mixin as
  written.
- VANILLA-1: on PR #64's last run (`f80f687`) Do a Barrel Roll still didn't load on 26.x clients: the call NeoForge put
  in place of vanilla's, `setRotation(FFF)`, wasn't taken for an overload with one more argument, as which float is the
  added one can't be told by types. The old `setRotation(FF)`, which NeoForge keeps forwarding `(yRot, xRot, 0F)`, now
  tells it.
- Kept, with its comment corrected: `BlockApiCacheInvalidationMixin`, whose stated reason was stale but which also
  passes NeoForge mods' capability invalidations to Fabric's caches.
- CI: no game matrix for a change to documentation alone (`paths-ignore`), and time limits on the jobs. Commit and
  merge conventions recorded in `claude.md`: a commit per concern, Conventional Commit subjects, one push per round.
- Docs: the design documents corrected where the code had moved on (module classification, the cache's layout and
  key, the security and performance claims, renderer status on 1.21.11, the mixin inventory); fixed findings that sat
  in the open table moved to the fixed one.
- Open: SEC-5 (no budget across all jars), PERF-1 (allocation per packet, unmeasured), PERF-2 (startup work repeated
  with a warm cache), CACHE-3 (index entries never cleaned), ARCH-1 (`MixinPreflight`'s size and repair ordering).

## 2026-10-21 (pass after PR #63): vanilla's code for injection points, client stopping

**Plan.** PR #63's last run (`2036fbe`) passed the new client module gate on every 26.x version once HUD-1 was fixed,
and Freecam loads on 1.21.11. Do a Barrel Roll is still disabled everywhere: its camera hooks pick calls by ordinal,
and NeoForge's patches add and replace calls, so pre-flight, which only has NeoForge's code, can't tell which call an
ordinal meant; and an ordinal that still occurs may land on another call than the mod's, unnoticed.

- VANILLA-1: on 26.x the loader finds vanilla's game jar next to the libraries, and pre-flight aligns vanilla's version
  of a target method with this game build's. An injection point picked by ordinal follows vanilla's instruction: to its
  ordinal here, or to the overload call NeoForge's patch put in its place (with a bridged handler). Where the
  alignment can't tell (a repeated sequence), nothing moves.
- CI: the client test mod also checks that Fabric's `CLIENT_STOPPING` reaches mods; its FAILED line for a HUD element
  never appeared on `4046a27`, though it was logged from that event.
- LIFE-1: and on `c03acb1` the check failed on 26.1.2, 26.2 and 26.3. Fabricated fired `CLIENT_STOPPING` from NeoForge's
  `GameShuttingDownEvent`, which only `Minecraft.stop()` posts, and only while the game runs; a run loop that ends
  otherwise (mc-runtime-test's) never got it. It now fires from NeoForge's `ClientStoppingEvent`, which
  `Minecraft.destroy()` posts where Fabric API fires the event, on 1.21.11 and 26.x.
- Roadmap: HUD-1 and MIXEXT-2 recorded as seen on `2036fbe` and `2380938`.
- VANILLA-1 on `c03acb1`: every 26.x server and client found vanilla's jar, and Lithostitched's terrain hook moved to
  the call vanilla's ordinal meant; no version left out more hooks. Do a Barrel Roll's hook at the mirrored camera's call
  didn't move: its replacement sits beside an instruction the patch repeats (`-xRot`, then `-roll`), which the front and
  back alignments pair differently, and only an identical range was answered. Now the range both share is.

## 2026-10-21 (pass after PR #62): Fabric API's client modules in a gate, HUD elements, Freecam on 1.21.11

**Plan.** PR #62's last run (`1810c80`) moved Polymer's codec hook and three of Ledger's hooks on 1.21.11, with every
client in its world. The test mods check Fabric's renderer and model loading APIs in the world, but none of the client
modules nearly every client mod uses (key mappings, HUD elements, screen events, client commands), which Fabricated
passes through with some of their mixins left out and bridged from NeoForge's events instead. Of the advanced corpus,
Freecam is still disabled on 1.21.11 for its MixinExtras requirement alone.

- CI: on 26.x, the Fabric API client run's test mod registers a key mapping, two HUD elements (one added last, one
  attached after chat), screen init and draw listeners and a client command; a new gate step requires each to have
  worked once the player is in the world. A HUD element is reported the first time it is drawn: the first run checked
  40 ticks after joining, while the world's loading screen could still hide the HUD.
- HUD-1: Fabric HUD elements attached before or after a vanilla element (and replacements and removals of one) were
  never drawn on NeoForge: upstream keeps the vanilla elements' roots by identity, and Fabricated's HUD bridge looked
  them up with equal ids of its own. It now uses upstream's `VanillaHudElements` constants. Found by the new gate.
- MIXEXT-2: a mixin config asking for MixinExtras 0.5.4 or 0.5.5 on NeoForge 1.21.11's 0.5.3 asks for 0.5.3 instead, as
  those releases only fixed bugs its mixins run the same without, unless it has a `@Redirect` at several injection
  points. Freecam asks for the version it is built with: on `2380938` it loads on 1.21.11, none of its hooks left out,
  with the advanced client in its world.
- Roadmap: CAPTURE-1 and LAMBDA-4 recorded as seen on `1810c80`.

## 2026-10-21 (pass after PR #61): what NeoForge's 1.21.11 build names otherwise

**Plan.** PR #61's last run (`8c7718c`) moved architectury's entity and explosion events, Carpet's fill updates and
the bone meal hooks, with every advanced client in its world. Some hooks are still left out only on 1.21.11, where
NeoForge's build is recompiled from decompiled source and names or numbers some synthetic members otherwise.

- CAPTURE-1: a shadow of a value an anonymous class captures (`val$...`) that the class has under another name matches
  the one captured value of its type there, and is renamed: Polymer's registry codec hook on 1.21.11.
- LAMBDA-4: a lambda named without a descriptor is found by the parameters its `@Inject` handler takes: Ledger's
  portal hook on 1.21.11.
- Roadmap: SPLIT-2, LOCAL-1 and DEPRECATED-1 recorded as seen on `8c7718c`.

## 2026-10-21 (pass after PR #60): hooks into code NeoForge moved or deprecated

**Plan.** PR #60's last run (`36b6f96`) was as clean as PR #59's, and the advanced clients' linkage check no longer
named Polymer's creative tab call (CTOR-1). Of the hooks pre-flight still left out, several target code NeoForge's
patches moved elsewhere in the same class: architectury's entity and explosion events, Carpet's fill updates,
FrozenLib's and Wilder Wild's bone meal hooks.

- SPLIT-2: architectury's entity add event hooks `PersistentEntitySectionManager.addEntity`, whose code NeoForge's build
  moved into `addEntityWithoutEvent`, which an entry point skipping NeoForge's event calls too; Carpet's fill updates
  rule changes a constant NeoForge's `Level.setBlock` tests in `markAndNotifyBlock`. Both are pointed at the split-off
  code now. Moves (HOOK-2, HOOK-3, SPLIT-1) take a handler whose state no other injector of its mixin shares.
- LOCAL-1: architectury's explosion event takes the entities' list at the loop in `ServerExplosion.hurtEntities`, whose
  code NeoForge moved into an overload also taking the blocks' list; the moved hook's `@Local` gets the ordinal of the
  list the overload didn't add, as it already did at a method's end.
- DEPRECATED-1: an injection into a method NeoForge marks deprecated, whose code moved into the one method of its class
  it calls with one more parameter, moves there as into an overload: FrozenLib's bone meal hook into `growCrop`, now
  `applyBonemeal` taking the player. One at a call of such a method moves to the replacement's call the game build
  makes instead: Wilder Wild's at `useOn`'s call of `growCrop`. A handler a repair moved onto a bridge is no longer
  bridged again by the repairs after it (an `@Inject` without sugar taking the method's parameters was).
- Roadmap: CTOR-1 recorded as seen on `36b6f96`.

## 2026-10-21 (pass after PR #59): calls of game code NeoForge changed

**Plan.** PR #59's last run (`e00f341`) had every advanced client in its world on all four versions, with no failed
check. ANON-1 moved Polymer's codec and item stack hooks on every version, and HOOK-3 moved Ledger's explosion hook on
three. Beyond the hooks still left out, the advanced clients' linkage check named one call of game code NeoForge's build
doesn't have, on every version.

- CTOR-1: a subclass of `CreativeModeTab` calling vanilla's constructor (Polymer's tabs for a server's item groups)
  calls NeoForge's constructor taking a builder, with a builder Fabricated makes from vanilla's arguments. NeoForge's
  patches give vanilla's constructor eight more parameters, and Polymer swallows the `NoSuchMethodError`, so a server's
  Polymer item groups never showed in the creative inventory. Translation schema 36.
- Roadmap: SPLIT-1 and SIG-2 recorded as seen on `1b48295`, ANON-1 and HOOK-3 on `e00f341`.

## 2026-10-21 (pass after PR #58): anonymous classes as NeoForge's build numbers them

**Plan.** PR #58's last run had every advanced client in its world on all four versions, with no failed check; what is
left is mostly the hooks LEAVE-1 leaves out. A group of them name anonymous game classes.

- ANON-1: a mixin naming an anonymous game class that doesn't fit it is pointed at the one anonymous class of the same
  class that does (its selectors, shadows and injection points all there). NeoForge's build, recompiled from decompiled
  source, numbers some anonymous classes otherwise than Mojang's (inferred), so Polymer's codec hooks, No Chat Reports'
  JSON codec hook and Ledger's and malilib's chest hooks were left out on every version.
- HOOK-3: an `@Inject` at a call NeoForge's build makes through methods of its own (a default method of an extension
  interface calling another) is pointed at the call of NeoForge's that leads there: Ledger logs blocks explosions
  remove at a `setBlock` NeoForge makes in `IBlockExtension.onBlockExploded`.

## 2026-10-21 (pass after PR #57): Fabric's packet context, filled and bound everywhere packets are coded

**Plan.** PR #57's last run showed the 26.2 and 26.3 advanced clients joining their worlds for the first time
(NET-6), then losing the connection on Fabric's recipe sync, and the 26.1.2 advanced client stopping at its first
chunk on a feature order cycle.

- NET-7: NeoForge's packet splitter encodes each packet before vanilla's encoder, to measure it, and decodes the
  packets it reassembles; both now run with the connection's Fabric `PacketContext` bound. The context also holds what
  Fabric API puts in it: the server and its registries once the server's login listener exists, the player's profile
  once login succeeds (both sides), and the client's registries once its play listener exists. Polymer's ingredient
  codec read no context while the splitter measured the recipe sync, which closed the connection, and Polymer warned
  that chunk packets lacked the registries. The context's keys live in a class of their own: as
  `PacketContextImpl`'s, they read as null through `PacketContext`'s constants whenever a connection's context
  initialized first (the client runs with the test mods alone and with the recipe viewers): a class's initialization
  first initializes the interfaces with default methods it implements, before the class's own keys are set. The test
  mod sends a payload large enough to be split, whose codec checks the context on both sides.
- MODIFIER-1: a Fabric jar's NeoForge biome and structure modifiers (its NeoForge build's, which Fabric never reads)
  become modifiers over no biome or structure (`neoforge:none` is gone from 26.x's biome modifiers, and stopped the
  26.1.2 advanced client on the first try). YUNG's Extras' 26.1.2 Fabric jar carries its desert decorations as a
  NeoForge biome modifier and adds them from its Fabric code too, so they went into desert biomes twice: the 26.1.2
  advanced client stopped on a feature order cycle in a Biomes O' Plenty desert (inferred). The corpus scan found the
  files.
- HOOK-2: an injection at a call NeoForge's patches moved into a hook of theirs (a static method taking the call's
  receiver and arguments, at most one more, that makes the call) is pointed at the hook's call, its handlers bridged as
  for an overload. Architectury's screen render events, FancyMenu's screen hooks and Inventory Profiles Next's overlay
  were left out on every version: NeoForge renders the screen inside `ClientHooks.drawScreen` (26.2 on: `extractScreen`).
  A handler using the mixin's own state isn't moved: jGui (ReplayMod) keeps the screen's graphics from a
  `@ModifyArg` at the same call for its `@Inject` after it, and the moved `@Inject` alone crashed the 26.2 advanced
  client on `5069513`.
- SPLIT-1: an injection into code NeoForge's patches split off into a method of its own (`Level.setBlock`'s neighbour
  updates, now in `markAndNotifyBlock`) is pointed at that method when it alone holds the injection points, nothing
  else in the class calls it and the handler neither depends on the original method nor uses the mixin's own state:
  Carpet's update suppression.
- SIG-2: an `@Inject` handler taking a method's vanilla parameters, the callback info and MixinExtras sugar gets a
  bridge taking the parameters NeoForge's build gives the method, one more: Ledger's fire hooks take a `@Local` after
  the callback info, which SIG-1's bridge doesn't carry, and stayed left out on every version.
- PRE-14: pre-flight checks a `@ModifyVariable` handler's parameters as Mixin does (the variable, then the target's
  first parameters, as many as it takes): Farmer's Delight's crop growth hook takes `getGrowthSpeed`'s vanilla
  parameters, NeoForge's build takes a block state first, and Mixin stopped the 1.21.11 corpus server once ENUM-6 let
  the mod load. The hook is now left out (LEAVE-1) and the mod loads. Asking for all of the target's parameters, as
  first written, disabled Balm and Waystones on every corpus run.
- LEAVE-3: a failing injector whose handler uses nothing of its mixin's own (no field of the mixin, no call to its
  methods) is left out alone even when the mixin adds an interface the mod's code uses: owo-lib's `GuiRendererMixin`
  hooks a field read NeoForge's `GuiRenderer.close` doesn't make, which disabled owo on every advanced client. A unit test
  covers a handler that keeps state of the mixin's, which still fails.
- LIB-1b: an accessor mixin into a library class NeoForge loads without mixin support is served by a generated class,
  as LIB-1's interfaces are: owo-lib's accessor of JOML's `Matrix3x2fStack` fields, which disabled owo on every
  advanced client. The accessor stays in its config, which Mixin needs to load it as the mod's interface; unit and
  pipeline tests run and translate one.
- FLUID-2: on 26.2 and 26.3, an entity's fluid lookups by tag answer for a mod's fluid tag, as vanilla's do, where
  NeoForge's build threw for any tag but water's and lava's: Biomes O' Plenty's screen overlay asks whether the eyes
  are in its blood, which crashed the 26.2 advanced client once it joined its world. The tag stands for its fluids'
  type, or for none when they have none or several. The test mod asks with its own fluid's tag.
- ENUM-6: a class tweaker's enum constants no longer need Mixin 0.17.1 when NeoForge's loader adds them from the mod's
  enum extension file (ENUM-1), or when no enum mixin adds them on this side (a client enum's, on a server). Farmer's
  Delight Refabricated was disabled on every 1.21.11 corpus run, whose NeoForge runs Mixin 0.16.5. A pipeline test runs
  both cases with Mixin 0.16.5; the translation schema is bumped.
- CI: the client smoke test logs the modules of the libraries LIB-1's bridges reflect on, and the advanced corpus
  step lists the NeoForge biome and structure modifiers its Fabric jars carry: NeoForge applies them, Fabric doesn't, and
  the 26.1.2 advanced client stopped on a feature order cycle in one Biomes O' Plenty biome (inferred cause to check: a
  feature added both by a mod's Fabric code and by its NeoForge data).

## 2026-10-21 (pass after PR #56): Polymer's handshake, Carpet's Brigadier hooks, and Do a Barrel Roll's jar

**Plan.** PR #56's last run showed every 26.x advanced server starting (ITEMCOMP-1) and the 1.21.11 advanced client
past Polymer's codecs (PRE-13), stopping where NeoForge refuses to send Polymer's handshake. The 26.x advanced clients
still stop on Do a Barrel Roll's camera hook.

- NET-5: a payload NeoForge has no registration for is sent, as vanilla sends it, where NeoForge threw: no NeoForge or
  Fabric mod declared it, so a mod put it on the wire with its own codec (Polymer's handshake, which Polymer's own hook
  receives).
- LIB-1: a mixin into a library class NeoForge loads without mixin support that only gives the class interfaces of
  the mod, implemented with the class's fields and its own methods, becomes a generated class: its methods become
  static ones reading and writing the fields through reflection, and the mod's casts to the interfaces, checks against
  them and calls of their methods (method references too) call that class. Carpet's two mixins into Brigadier's
  command node and dispatcher, which remove a script's commands, disabled Carpet on every version. Unit tests compile
  such a library, mixins and mod with javac, translate them and run the result; the translation schema is bumped.
- MULTI-1: a jar with both Fabric's and NeoForge's metadata is a Fabric mod when its NeoForge side starts nothing of
  its own (Java's language loader, no class with NeoForge's `@Mod`) and its Fabric side has entrypoints. Do a Barrel
  Roll's 26.x Fabric jars keep the NeoForge build's `neoforge.mods.toml`, so NeoForge loaded them itself and applied
  their Fabric mixins, which stopped every 26.x advanced client (CORPUS-18). The mixin diagnostics pointed there: no
  line of Fabricated's report named the mod, and NeoForge's mod list did (the jar's entries weren't listed; the next
  run's log names each jar Fabricated takes this way).
- NET-4: a Fabric payload type in the `minecraft` namespace, which NeoForge refuses to register (BetterEnd's ritual
  update), goes as vanilla's own payloads do: NeoForge's codec lookup gets its Fabric codec, each side announces the
  ones it receives with `minecraft:register` as Fabric does, and the vanilla path of the custom payload handler hands
  them to Fabric's receivers. The test mod sends one both ways.
- NET-6: on 26.x, packets are decoded and encoded with the connection's Fabric `PacketContext` bound, as upstream's
  networking module does; Polymer's hello payload failed to decode without it (26.2 advanced client).
- ATTR-1: on a client, the check that living entity types have attributes waits for the main entrypoints, where
  Fabric mods register them; it logged an error for each of their entity types before (BetterNether's).
- CI: the mixin diagnostics also print what the report says of the failing mixin's mod (CORPUS-18).

## 2026-10-21 (pass after PR #55): Polymer's payload codecs, and default components on 26.3

**Plan.** PR #55's last run showed the 26.1.2 and 26.2 advanced servers starting (LAMBDA-3), Tectonic's pack loading
(PACK-4) and Freecam disabled with its reason (MIXEXT-1). The next stops: the 1.21.11 advanced client on Polymer's
payload codec, the 26.3 advanced server on Fabric's default component event.

- PRE-13: an `@Inject`'s or `@ModifyExpressionValue`'s call point moves to the overload NeoForge's patches call with
  more arguments after the named ones, as its custom payload packets call `CustomPacketPayload.codec` with the
  protocol and direction. Polymer's codec hooks were left out, and its hello payload then failed to encode.
- ITEMCOMP-1: on 26.x, Fabric's default component changes run once the items' components are bound, as upstream runs
  them, not in NeoForge's event before the binding: Wover's predicate reads an item's components.
- SIG-1: LAMBDA-3's bridge also takes a method NeoForge gave more parameters after the ones a handler takes (Ledger's
  fire logging, `FireBlock.checkBurnOut` with a face).
- CI: the mixin diagnostics also print the overlays they found and the report's lines on the mixin (CORPUS-18).

## 2026-10-21 (pass after PR #54): lambdas NeoForge's patches change, and MixinExtras requirements

**Plan.** PR #54's runs showed every 26.x advanced server stopping at world creation on Wover's world state, and the
1.21.11 advanced client stopping on Freecam's MixinExtras requirement.

- LAMBDA-3: an `@Inject` into a lambda whose captured values NeoForge's build orders differently, or captures more of,
  gets a bridge taking the lambda's parameters as the game has them. Wover's hook into `WorldLoader`'s registry load,
  which publishes its world state, and FrozenLib's into `RegistryDataLoader`'s were left out before.
- MIXEXT-1: a mod whose mixin config asks for a newer MixinExtras than NeoForge ships is disabled with that reason,
  instead of MixinExtras stopping the game. The translation schema is bumped.
- PACK-4, finished: a built-in pack's metadata is read from the translated files when translation changed them (PR #54
  only left the NeoForge sections out of the copy, and Tectonic's pack was still skipped). The test mod's built-in pack
  has such a section.
- GROUP-1: pre-flight checks injector groups' minimums, as Mixin does; a group too few of whose injectors can apply is
  left out of a self-contained mixin (or fails the mod), instead of stopping the game as owo-lib's did.

## 2026-10-21 (pass after PR #53): main entrypoints where Fabric runs them, and late payload types

**Plan.** PR #53's runs showed the 26.x malilib clients and the 1.21.11 advanced server stopping on payload types
registered after NeoForge's network registration (MiniHUD, Ledger), Axiom failing again for reading the client in its
main entrypoint, and Flashback not loaded for one large native library.

- NET-3: a Fabric payload type registered after NeoForge's payload registration event is registered with NeoForge
  then, with its registry reopened for it; an id NeoForge has for the other direction is registered again for both.
  The test mod registers one as the server starts, and the client sends it after joining.
- CORPUS-16: on a client, main entrypoints run with the client ones, in `Minecraft`'s constructor, where Fabric Loader
  runs them, with the registries opened again. What NeoForge fills once its registration events end takes what they
  register: default attributes (Fabric's attribute modification event runs after the client initializers, as upstream
  runs it), spawn placements, fluid types and the creative tabs' order; Forge Config API Port configs registered then
  are loaded right away; late tooltip providers go before or after all component tooltips. A dedicated server keeps
  the old order. Client checks: the game instance exists in main, and a tab registered there comes after the vanilla
  tabs.
- POI-1: a point-of-interest type registered through vanilla's `PoiTypes.register` (Fabric API's helper, WorldWeaver)
  no longer fails as defined twice: NeoForge maps its states as it is registered, vanilla then again. BetterEnd's and
  BetterNether's main entrypoints threw there on the 26.2 advanced client. The servers' missing BetterEnd content
  (CORPUS-19) is still unexplained: they logged no such failure.
- LOADER-3: `ResourceBundle.getBundle` with a `ResourceBundle.Control` in a Fabric mod's code makes the same lookup
  through Fabricated's `ResourceBundles`: Java refuses a control from a named module, and LuckPerms loads its
  translations so. The translation schema is bumped.
- CORPUS-21: the compatibility plan refuses Flashback's Fabric build. Its registry freeze hook starts Flashback, whose
  start-up needs the client, on the first freeze: NeoForge freezes at bootstrap, Fabric API only after the client
  initializers. It stopped the 1.21.11 advanced corpus client at bootstrap.
- LIMIT-1: one archive entry may be 128 MiB (was 64 MiB): Flashback ships a 71 MB native library. The limits on the
  whole archive and the compression ratio check are unchanged.
- HOLD-1: with CORPUS-16, every 26.x advanced client stopped at NeoForge's freeze before `Minecraft` exists on
  BetterNether's entity types, made before its main entrypoint registers them. While the main entrypoints are still to
  run, that freeze keeps such waiting holders for the registration; the freeze after them checks them as vanilla does.
- HOOK-1: an `@Inject` after Fabric Loader's `Hooks.startServer` (owo-lib's registry freeze) is pointed at NeoForge's
  `ServerModLoader.load`, where Fabricated runs the entrypoints that hook marks. owo's group stopped the 1.21.11,
  26.1.2 and 26.2 corpus servers.
- PACK-4: a Fabric jar's built-in packs lose the `neoforge:` sections of their `pack.mcmeta` too (Tectonic's pack was
  skipped on the 26.x advanced servers).
- SIDE-1: a dependency on a mod installed for the other side only is a suggestion there, as Fabric Loader resolves it:
  Emotecraft, which requires the client-only key binding module, loads on a server again.
- CI: when Mixin stops a client on an injector pre-flight passed, the summary prints the handler's annotations and its
  target methods' code (CORPUS-18); the server summaries list entrypoint failures and slow entrypoints (CORPUS-19).
- Roadmap: PR #53's results (PIP-1, BIOME-2 and LOADER-2 verified as far as the next failure), and what is still open:
  FrozenLib's entrypoint injection (LOADER-4), BetterEnd's payload type in the `minecraft` namespace (NET-4), Freecam's
  MixinExtras requirement on 1.21.11 (MIXEXT-1), and Wover's world state at world creation (CORPUS-19, whose data
  pack errors are gone).

## 2026-10-21 (pass after PR #52): picture-in-picture renderers, and mixins into what other mods' mixins add

**Plan.** PR #52's runs showed malilib (MiniHUD, Tweakeroo, Litematica) still refused for the picture-in-picture
renderer map NeoForge replaced (CORPUS-13), and owo-lib disabled partly for a hook into a method Fabric API's own mixin
adds to the creative screen.

- PIP-1: the GUI renderer has vanilla's map of picture-in-picture renderers again, which NeoForge replaced with renderer
  pools: renderers Fabric mods put in it (malilib's) prepare the states NeoForge has no pool for, and pre-flight sees the
  map. CI loads malilib, MiniHUD and Tweakeroo on their own until it is verified; the compatibility plan still refuses
  malilib meanwhile.
- PRE-12: a field or method a target lacks that an installed Fabric jar's mixin adds to it is not judged by pre-flight
  (Mixin merges every mixin's members before it injects): owo's hook into the `updateSelection` Fabric API's creative
  tab mixin adds. The result is cached with the adding mixin's class.
- BIOME-2: `Biome.attributes` is widened where NeoForge leaves it a plain field (1.21.11, 26.1.2, 26.2): a field stays
  private only where the game's class has the getter NeoForge's coremod reads it through, not wherever any version's
  coremod names it. Fabric API's biome module stopped the 1.21.11 advanced corpus server reading it.
- LOADER-2: a jar a Fabric mod adds to the class path while running (Fabric Loader's `addToClassPath`, or its legacy
  launcher's `propose`, which LuckPerms uses for the libraries it downloads) is found for the classes no mod's module
  has: Fabricated puts a URL class loader in front of the fallback FML's module class loader hands those to.
- Roadmap: what PR #52's last runs showed, still open: Do a Barrel Roll's camera mixin on 26.x (CORPUS-18), BetterEnd's
  content on the 26.x advanced servers (CORPUS-19), owo's two remaining mixins (CORPUS-20), and what NeoForge runs
  before `Minecraft` exists (CORPUS-16).
- The translation schema is bumped (pre-flight's results change).

## 2026-10-21 (pass after PR #51): biome modifications on 26.3, and what the advanced corpus stopped on

**Plan.** Run 384 (PR #51's last) confirmed the 1.21.11 corpus client reaches a world with Friends and Foes loaded
(MIX-1's plugin-mixin repair). The remaining stops were Fabric API's biome module on 26.3 (BIOME-1: the corpus server
and client crash creating a world), a mixin pre-flight passed that failed once the game ran (Polymer), and an accessor
pre-flight couldn't repair (JourneyMap).

- BIOME-1: on 26.3 Fabricated ships `fabric-biome-api-v1`. Upstream's biome modifications replace `Biome` fields
  NeoForge keeps private and no longer reads (NeoForge reads its modifiable biome info), so adding a mob spawn, which
  is an environment attribute on 26.3, crashed world creation. They now run as a NeoForge biome modifier
  (`fabricated:fabric_biome_api`, declared by Fabricated's data pack) on NeoForge's builders, Fabric's phases mapped
  onto NeoForge's; upstream's API and biome source mixins ship unchanged. A version can now declare mixin configs of
  its own (`version_mixin_configs`). The test mod adds a spawn and changes plains' temperature on 26.3, and checks both
  once the server has started. Not built or run locally (Mojang's and NeoForge's servers are unreachable here): CI is
  its first build. Run 385 (this PR's first): it builds, the test mod's checks pass, and the 26.3 corpus client and
  server, which crashed on it, reach a world and start. With the corpus mods the test mod's spawn wasn't in plains,
  though (the corpus server's check failed); the cause wasn't in the log's tail. One way it can happen: a biome whose
  spawns aren't an overlay, which NeoForge doesn't write spawns into; such spawns are now set on the attribute as
  upstream sets them, and the check reports what plains has when it fails.
- RET-1: pre-flight took `@At("RETURN", ordinal = n)` as present in any method with code. Polymer's stream codec
  injector asks for a second return this game build's `decode` doesn't have: it passed pre-flight and stopped the
  advanced corpus at startup (1.21.11 server, run 383; 26.1.2 client, run 384). Return points now count returns as
  Mixin does.
- ACC-1: JourneyMap's `EditBoxAccessor` declares `isEditable()Z`, which `EditBox` already declares without public
  access, so Mixin can't generate it and JourneyMap was disabled on every version (runs 381 to 383). The repair that
  points such an accessor at the existing method now covers non-public ones too, made public by the mod's access
  transformer.

- DELEGATE-1: NeoForge keeps vanilla's `MappedRegistry.register(key, value, info)` as a call to an overload taking an
  id first. FrozenLib's registry mixin captures the new entry's id at the method's end, which the kept method no longer
  has, so FrozenLib and Wilder Wild were disabled on every version (runs 381 to 384). Injectors on such a method now
  move to the overload: a handler taking the method's parameters gets them from a bridge taking the overload's, and a
  captured local is found there by name, or by type as the one local of that type besides the added parameter. The
  repair moving injectors into an overload also stopped moving a handler that takes the method's parameters unchanged,
  which would have failed once the game ran.

- RES-1: the advanced corpus client stopped on every version while Mixin prepared Lattice's config (Axiom's and
  Flashback's config library): its plugin reads Minecraft's `/version.json` through its own class, which on NeoForge
  looks only in the mod's own module, while Fabric's unnamed modules see the whole class path. A Fabric mod's
  `Class.getResource(AsStream)` calls now go through Fabricated's `ClassResources`, which asks the class loader too when
  the module has no such file. The test mod checks it on every version.
- SHADOW-1: Server Translations shadows the captured `val$codec` of vanilla's anonymous `EncoderCache$2`, which NeoForge's
  1.21.11 build names otherwise. Pre-flight looked the field up through the class's supertypes, one of which (a
  DataFixerUpper interface) it has no bytes of, so it couldn't tell, and the mixin stopped the 1.21.11 advanced corpus
  server (run 385). A shadowed field is now looked for in the target class only, as Mixin does, and one of a captured
  variable is pointed at the only captured field of its type, as accessors of them already were.
- CORPUS-15: GlitchCore's client render helper mixin uses Fabric API's client rendering module, which a dedicated
  server doesn't load, so GlitchCore was disabled on every corpus server (run 385), and Biomes O' Plenty with it. A mod
  only for the other side no longer counts as one that isn't loading: on Fabric it isn't there either, and a mixin
  using it is in a class only that side loads.
- REMAP-2: ReplayMod adds some of its mixin configs to Mixin itself instead of listing them in its metadata; on 1.21.11
  their refmaps weren't remapped and still named intermediary classes, which stopped the advanced corpus client (run on
  `b0f3606`). Refmaps of the mixin configs at a jar's root are now remapped, listed or not.
- REQ-1: pre-flight took an injection point found in any of an injector's target methods as enough, while Mixin counts
  the injections in all of them against `require`. FrozenLib wraps a call in two methods with `require = 2`, and
  NeoForge's 1.21.11 build moved it out of one, which stopped the 1.21.11 advanced corpus (run on `b0f3606`).
  Pre-flight now counts them. LAMBDA-2: an injector at a call NeoForge's build moved into a lambda of the method
  (here an `Optional` callback) gets the lambda added to its methods, so FrozenLib's hook applies there.
- LEAVE-2: an interface mixin with only handlers (no accessor, invoker or shadow) counts as self-contained, so one that
  can't apply is left out instead of disabling its mod (owo-lib's debug hooks into DataFixerUpper on 26.2).
- OBF-1: on 26.x, a mod whose mixins name intermediary classes (a build for 1.21.11 or older) is disabled with the
  build to install: Modrinth lists Do a Barrel Roll's 1.21.11 build for 26.2, which crashed the 26.2 advanced corpus
  client (run on `b871cd5`).
- PACK-1: a pack repository's sources stay mutable for NeoForge's pack finders when a Fabric mod replaced them with
  an immutable set, as vanilla has them: FrozenLib's resource pack API does, which crashed the 26.1.2 advanced corpus
  client in `Minecraft`'s constructor (run on `b871cd5`). The test mod checks it on every version.
- PACK-3: a Fabric mod's built-in packs take Fabric API's known-pack identity (namespace `vanilla`, the pack's id, the
  mod's version). Without one, the registry entries a built-in pack adds were experimental, and creating or loading a
  world with them asked for a confirmation that Fabric never shows. The test mod checks its built-in pack's damage type
  is stable.
- PACK-2: a Fabric mod's built-in packs are read with translation's changed files over the jar's, so DATA-1 reaches
  them too (Tectonic's worldgen modifiers are in its built-in pack). The test mod checks a damage type under
  `neoforge:false` in its own data and in its built-in pack on every version.
- DATA-1: a Fabric jar's data files lose their `neoforge:conditions`, which Fabric never reads: Tectonic's name a
  condition type only its NeoForge build registers, which stopped the 1.21.11 advanced corpus server (run on `b871cd5`).
  The Fabric build's own `fabric:load_conditions` still decide.
- DATAGEN-1: Fabric API's data generation module loads (from the player's Fabric API jar, as on Fabric): Wover's block
  and item entrypoints load its tag providers at startup and failed without it, so BetterEnd and BetterNether registered
  nothing and the 26.2 and 26.3 advanced corpus servers stopped loading their data (run on `b871cd5`). Its hooks act
  only when the `fabric-api.datagen` property is set. Its tag appender mixins, which add removal methods NeoForge's tag
  appenders already have, and on 26.3 its loot sub-provider mixins are left out (data generation only).
  Fabricated's native registry sync gained upstream's `DynamicRegistriesImpl.FABRIC_DYNAMIC_REGISTRY_KEYS`, which
  the module's dynamic registry provider reads: without it the linkage check disabled the module (run on `3e39947`).
- ENUM-5: an enum mixin into one of NeoForge's extensible enums that its enum extension file can't carry (a name not
  starting with the mod's id, a constant with a body of its own) becomes an ordinary mixin adding the constants to the
  enum's values from its class initializer, as mods' own NeoForge builds do; FML refuses only enum mixins. A constant's
  body becomes a delegate class where the enum forwards the overridden method to one (Wilder Wild's tundra grass colour).
  UNVERIFIED in the game until the advanced corpus runs.
- ENUM-4 (reverted): letting enum mixins into NeoForge's extensible enums through to Mixin, on the reading that NeoForge
  didn't refuse them, crashed the 26.2 and 26.3 advanced corpus at startup on Wilder Wild's: FML's
  `FMLMixinFeatureValidator` refuses them (run on `b0f3606`). Such a mixin disables its mod again.
- Run on `fad7762` (this PR): CI green; RES-1 and BIOME-1 pass on 26.3; FrozenLib and Wilder Wild load on the 26.3
  advanced client (DELEGATE-1, ENUM-4). The advanced corpus still stopped before reaching a world, for three new
  reasons, each fixed here:
  - LOADER-1: FrozenLib's mixin plugin injects its entrypoints into Fabric Loader's internal storage, and its code
    names internal classes Fabricated lacked, which stopped 26.2 and 26.3 at startup. The classes now exist, with no
    instance to reach, so FrozenLib's reflective lookup fails as it plans for and it runs its entrypoints itself.
  - AT-1: one mod's access widener made `InstrumentItem.getInstrument` final (Fabric's implicit final for an accessible
    private method) and another's extended it; NeoForge refuses the conflict, which stopped 1.21.11. That implicit final
    isn't written any more, so the lines merge to Fabric's result.
  - REG-3: Wover creates its registries through vanilla's `BuiltInRegistries`, and registers entries in vanilla ones,
    while NeoForge processes its data pack registry event, after NeoForge froze them, which stopped 26.1.2. A frozen
    built-in registry written to then is opened: the root until NeoForge's registration ends, as it is for
    `FabricRegistryBuilder`; any other registry before NeoForge's registration events, which open and freeze every
    registry themselves. The test mod checks both, from where Wover writes (run on `b0f3606` showed the root part
    working, then Wover's next write).
- CI prints the causes of a server or client crash report (a mod loading crash's issues are below its description).
- The translation schema is bumped (28): translations cached by a build before PR #51 are made again.

Run 384 also showed, still open: Moonlight's reload listener on 26.1.2 (CORPUS-16), and on 26.3 the advanced corpus
server stopping on BIOME-1 as well.

## 2026-10-21 (pass after PR #50): more Fabric mods load, and run

**Plan.** The corpus showed one mixin NeoForge's code can't take disabling a whole mod, and with it every mod that
needs it. Load those mods without what can't apply where nothing else depends on it, then check what the mods that
now load do as the game runs, with deep-hook mods in a corpus of their own.

- LEAVE-1: a failure in a self-contained mixin (no interface added to its target, no accessor or invoker, so nothing
  outside it calls into it) no longer disables the mod. Only the failing injectors are left out when the mixin adds
  no fields of its own; the whole mixin goes when it does, as its injectors may share them. A mixin the mod's own code
  calls into still disables the mod, as do enum mixins into NeoForge's extensible enums. The report lists what was
  left out in its own section, the mod's row counts it, and the log names those mods at startup. Run 379, with whole
  mixins left out: the corpus disabled 2 or 3 mods per version (owo, Friends and Foes, and Farmer's Delight
  Refabricated on 1.21.11, whose enum extensions need a newer Mixin), down from 7 to 10 on run 377. Balm, Waystones,
  JEI, No Chat Reports, Moonlight, YUNG's mods, Comforts, Expandability and codecui loaded, and every gate passed.
- SCAN-1: Fabric mods find their package directories while their entrypoints run. YUNG's API scans its own package
  with Reflections to find what it registers, which starts from the package's directory; NeoForge's class loader finds
  only files (read in FML's source), so the scan found nothing and the 26.1.2 corpus client crashed loading
  registries once LEAVE-1 let YUNG's API load (run 379). The thread's context class loader now also finds the Fabric
  mods' directories. Core's tests cover finding them; the test mod scans one of its packages as Reflections does.
  Run 381: every version passed the new gate, and YUNG's mods no longer stopped the 26.1.2 client.
- Moonlight's 26.1.2 Fabric build reads `Minecraft.getInstance()` in its main entrypoint, which runs before
  `Minecraft` exists on NeoForge (CORPUS-16, open): its entrypoint is marked as failed, and on run 381 its reload
  listener, finding its setup never ran, then stopped the 26.1.2 corpus client.
- ENUM-3: an enum mixin whose constants are built from values computed when the class loads (a registered object, a
  static array) becomes a NeoForge enum extension too: the arguments come from a method Fabricated generates next to
  the mod's mixins, which runs the mixin's code for them when NeoForge adds the constant, in suppliers where NeoForge
  reserves the mixin's constructor for one taking them. That is what still kept Friends and Foes from loading on
  every version. Core's tests define the generated classes and call them as NeoForge's loader does. Run 382: Friends
  and Foes loaded on every version. Loading, it showed two more problems: MIX-1 below, and on 26.3 Fabric API's own
  biome module, which writes a field NeoForge keeps private when a mod adds a spawn (BIOME-1, open: world creation
  crashed).
- MIX-1: Fabric's Mixin declares the injection points of `@Redirect`, `@ModifyArg`, `@ModifyArgs` and
  `@ModifyVariable` as arrays, and MixinExtras 0.5.3 on NeoForge 1.21.11 reads a `@Redirect`'s as one annotation: the
  1.21.11 corpus client crashed on Friends and Foes' (run 382). An array of one is now rewritten as that point. Run
  383 still crashed: the `@Redirect` is in a mixin Friends and Foes' plugin adds only when it finds mc-runtime-test
  (CI's client driver), and the mixins a plugin adds were checked but not repaired. They now get the same repairs
  (`PipelineTest.mixinsAPluginAddsGetTheRepairsOfListedOnes`).
- VER-1: a Fabric jar whose version doesn't start with a digit stopped the launch, as NeoForge's loader refuses such
  a version: every advanced corpus run of run 381 stopped at startup on a library FancyMenu nests
  (`animated-gif-lib-1.7`). It is now written as `0+<version>`.
- LOOT-2: Fabric's loot table handlers run for one table at a time. NeoForge fires its loot event on parallel
  workers, where Farmer's Delight builds a tag that REI's mixin isn't safe for: the 26.3 corpus client crashed
  loading registries on run 381 (upstream's own hook runs in that parallel parse too, read in its source).
- CI: an advanced corpus of Fabric mods with deep hooks (world generation, gameplay systems, camera, sound, replays,
  server tools) runs on a server and a client of its own; run 381 fetched 35 to 45 of them per version, and its
  pre-flight disabled Carpet (Brigadier is a library NeoForge loads without mixin support), JourneyMap (an accessor
  of a method the game has), FrozenLib with Wilder Wild (a captured local NeoForge's registry lacks) and, on 1.21.11,
  Biomes O' Plenty and Emotecraft. WorldEdit's jar is a NeoForge mod too and is out of that list. Each corpus log gets
  a digest of its errors with the first stack of the most frequent (`ci/log-digest.py`), which found the stacks of
  both crashes above; REI's "Failed to fill display" errors (CORPUS-17) come without one.

## 2026-10-20 (pass after PR #49): renderer and deep-hook mods

**Plan.** Find out why CI failed on `main` after PR #49, then broaden which mods load, renderer-changing and
deep-hook mods first, keeping facts about other people's mods in data. Where a mod can't run on NeoForge, the report
says why and what to install instead.

- `main`'s failure (run 368, the plain 26.1.2 client): the client launcher stopped with status 255 before the game
  wrote a log, on code whose other clients passed. Run 365's plain 1.21.11 client failure left no readable log
  either. The smoke script now launches once more when the launcher stops before the game starts, and the CI
  summary prints the launcher's last lines whenever a run has no game log.
- Compatibility-plan entries can say what to do instead (`suggestion`). Essential's Fabric builds are refused with
  the reason: its loader is a stub that downloads Essential and injects it through Fabric Loader internals. malilib
  and WTHIT entries name their NeoForge builds.
- The linkage check covers mods' references to Fabric Loader, against the classes Fabricated provides. A missing
  one is a warning, and never drops a Fabric API module.
- CORPUS-15 is fixed: a mod is disabled when a required mixin without a plugin calls code of a Fabric mod that
  isn't loading. The report names that mod, instead of Mixin crashing at class load. Core's unit tests cover it.
- CI: corpus runs list every disabled mod with its reason, and any Fabric Loader internals a mod reaches for. A
  new informational run puts Fabric addons of Sodium (Sodium Extra, Reese's Sodium Options) next to Sodium for
  NeoForge on 26.x.
- Fabric's renderer API on 1.21.11 has the NeoForge-aware defaults 26.x has: level-aware parts, NeoForge's
  ambient occlusion, and the colours and normals of NeoForge's quads. Run 370 stopped at the 1.21.11 build (the
  vendored `TriState` stub also needed its `BooleanFunction`); run 371 passed on all four versions, the new 1.21.11 checks included.
- On 1.21.11, everywhere NeoForge collects a model's parts outside the chunk sections it compiles, a Fabric model's
  parts are now made from the quads it emits, one part per render layer, keeping what NeoForge's quads carry. Moving
  blocks, the breaking overlay, blocks drawn as entities, NeoForge's translucent outline pass, other mods and Sodium
  for NeoForge see Fabric models (CI checks the parts, a falling block and Sodium; the other callers are where
  NeoForge's code collects parts). That replaces this pass's earlier moving-block mixin. The test mod's Fabric models
  are now plain models with no parts of their own, as Fabric mods write them, so the check that NeoForge's parts of
  one hold its quads (baked colour kept) can only pass through the conversion (run 372: 1 part, 6 quads).
- Sodium for NeoForge 1.21.11 says in its metadata that it contains a Fabric renderer its NeoForge build doesn't
  ship (read in its 1.21.11 source), so Indigo stood down and Fabric's renderer API had none (run 372: no parts under
  Sodium). Once the client initializers ran, Fabricated now registers Indigo's renderer when none registered. Run
  374: under Sodium for NeoForge 1.21.11, the test block and the multipart part with it were meshed in Sodium's own
  section level.
- The 1.21.11 checks for the breaking overlay and the moving block now tell what is drawn by the game's frames on
  the stack, as the level a caller passes doesn't: 1.21.11 hands the overlay a `BlockBreakingRenderState` (run 376),
  a moving block's render state, and collects a moving block's parts with the client level. So the moving block line
  of runs 371 and 372 came from the breaking overlay, and the falling block itself went unchecked until now. A first
  stack check also matched its own method name (run 374).

## 2026-10-20 (pass after PR #48): generalizing

**Plan.** First features and fixes, generalized wherever a rule can replace a case: what a mod declares, its
mixins and its custom values decide what Fabricated does, and the facts about other people's mods that can't be
derived stay data (`compat-plan.json`, `interop.json`). Then a last phase of cleanup, refactoring, performance and
security review in the same pass.

- Custom values reach NeoForge mods where they read them, and the entrypoint conventions are data (CONV-1). Core's
  187 unit tests pass, among them one reading the generated `mods.toml` with NeoForge's parser.
- Results of the run on `main` after PR #48 (run 359) and of run 360 on `f9ec04e`: every gate passed on all four
  versions. With malilib and WTHIT out (CORPUS-13), the corpus clients reach their worlds again. Pre-flight disabled
  six mods on 1.21.11 and five on 26.3; the summary now prints their reasons in full. One was generically repairable:
  resourcefullib's wrapper of a record constructor NeoForge gave a component (PRE-10), whose handler also takes a
  `@Local` (PRE-11). The others need NeoForge builds: Balm and JEI (CORPUS-3), Friends and Foes (its wrapped
  `Mob.checkSpawnRules` and `checkSpawnObstruction` calls are one NeoForge hook there, and it adds constants to
  `MobCategory` and `Raid$RaiderType`, extensible enums NeoForge doesn't let mixins change), No Chat Reports (a mixin
  into authlib, a library NeoForge loads without mixin support).
- An enum mixin into a named enum translates when its names already start with the mod's id (ENUM-2).
- Fabric's renderer API loads on 1.21.11 (M5): its plan leaves out the upstream mixins NeoForge's build conflicts with,
  Fabricated gives the model baker's sprite getter its sprite finders (upstream's mixin reads captured-variable fields
  NeoForge's recompiled game names differently: it takes them from the constructor's arguments instead, told apart by
  atlas), and Fabric block models, alone and in vanilla composites, are drawn in chunk sections. Run 365 logged both
  checks on 1.21.11 (now a gate); the corpus client there no longer crashes on Continuity's mixin. 1.21.11's renderer
  mixin config is empty, as the shared `mods.toml` declares it for the renderer API (run 364 stopped in pre-loading).
- Bridges pass a handler's MixinExtras sugar on (PRE-11), so resourcefullib's wrapper is repaired at last. A review of
  this pass's changes found two defects, both fixed: non-finite custom numbers broke the generated `mods.toml`
  (TOML-1), and a model class naming a missing type crashed chunk compilation (FRAPI-4). CORPUS-15 is recorded open.
- Run 366 on `1e8d1dd`: every gate passed on all four versions. resourcefullib loads on 26.3 (PRE-11), the 1.21.11
  corpus client reaches its world, and Fabric models are meshed under Sodium and Iris on 26.x. Run 365's failure of the
  plain 1.21.11 client didn't recur on the same code for that client; its log had scrolled out of reach, so the CI
  summary now repeats that client's failed checks and errors.

## 2026-10-21: item storages bridged, Fabric fluids get fluid types, shader pack evidence

**Summary.**
- Storages in items are bridged: a NeoForge machine fills a Fabric mod's tank item through
  `Capabilities.Fluid.ITEM`, a Fabric machine a NeoForge mod's through `FluidStorage.ITEM`, each item changed through
  the other side's access to where it is (`AccessItemContext`, `ContextItemAccess`). That crosses back in one
  transaction (the handler calls the access), which the transaction mirrors couldn't do, so they now stand for each
  other: a NeoForge transaction opened for a Fabric one takes a borrowed snapshot in `FabricTransactions`, and a
  Fabric transaction opened for a NeoForge one gets no mirror in `NeoTransactions`. The documented limitation (a
  storage using one on the other side in the same transaction) is gone with it.
- Each side of the storage and energy bridges answers for its own mods' content, told apart by namespace (XFER-1):
  NeoForge's capabilities for Fabric mods' blocks and items come from Fabric's whole lookups, fallbacks included, and
  Fabric's lookups fall back to NeoForge's capabilities for NeoForge mods' blocks and items. Neither side asks the
  other back, so the guard against it is gone, and NeoForge code no longer runs Fabric's item lookup for every
  NeoForge mod's item it inspects. The test mod's NeoForge-side content moved to a namespace of its own, and its
  Fabric energy block offers its energy through a fallback.
- Energy with Team Reborn's library (`EnergyBridge`), loaded only when a Fabric mod brings it; compiled against
  never-packaged stand-ins of its API (`integrationstubs`), its lookups found by id.
- `/fabricated why <mod>` (one mod's outcome and suggested fix) and `/fabricated disable|enable <mod>` (its entry in
  `config/fabricated.json`, for the next launch, its other settings kept; comments in the file are not). CI: the test mod runs them from the
  server console and reads the config back.
- The corpus gains Fabric tech mods (Modern Industrialization, Tech Reborn), for the transfer and energy bridges.
- Fabric mods' fluids get NeoForge fluid types (FLUID-1, found by the corpus): one per family, registered, its
  name, light, temperature, viscosity and bucket sounds read from Fabric's `FluidVariantAttributes`, how entities
  behave in it from the fluid's tags as vanilla does (`minecraft:water` makes it water to them; any other leaves
  them alone). Living entities move through a fluid that isn't water as through air, where NeoForge would leave them
  hanging (26.2 and later) or move them as in lava (1.21.11). On 1.21.11, whose renderer and `FluidVariantRendering`
  read textures from the type's client extensions, those come from the fluid's Fabric render handler, water's
  without one. `FluidFlowEvents.ALLOW` (26.2+) now reaches Fabric fluids too, which have types to hook. CI: the test
  mod registers a fluid of each kind, checks that both of a family's fluids share one registered type, its light and
  name, and which is water-like, places one in the world, and checks that a pig falls through a column of it.
- Pre-flight checks `@WrapMethod` handlers' parameters against the method they wrap (PRE-6, a crash of the 26.2
  Polyfrost client): a mismatch disables the mod with the reason instead of crashing the game.
- Pre-flight repairs an injector at a call NeoForge's patches give one more argument (PRE-7, ShulkerBoxTooltip):
  the injection point moves to the overload, and a handler that takes the call's arguments gets a bridge leaving the
  added one out (and, for `@WrapOperation`, putting it back for the original call). Injectors in a named `@Group`
  are left to their group, by every repair (PRE-7's first version moved one of Iris's alternatives), and require
  nothing on their own (PRE-8), as in Mixin.
- Results of the dispatched run on `ae39988` (run 351): all versions green. ShulkerBoxTooltip loads on every version;
  the corpus clients reach their worlds with 6 mods disabled on 1.21.11, 6 on 26.1.2, 5 on 26.2 and 3 on 26.3. The
  26.2 Polyfrost client no longer crashes on Polydium (PRE-6). The informational 26.2 client with the Fabric builds of
  Sodium and Iris crashed on Iris's `MixinLevelRenderer` group (fixed in the next commit, above).
- Fabric's configuration-phase `DISCONNECT` events fire on the server and the client (NET-1).
- A jar whose service file names a provider class it doesn't have no longer stops the game from starting (PKG-4,
  found by the widened corpus: spark's): the file is left out of its module, with a report note.
- A broken accessor or invoker is left out alone, so its interface still applies and the mod's casts to it work
  (PRE-9; malilib's crashed the 1.21.11 corpus client with its whole interface left out). Failing the mod instead,
  tried first, disabled OneConfig, Iris's Fabric build and adventure-platform-fabric.
- A reload listener a mod registers from its mixin into `Minecraft`'s constructor, before NeoForge's event, is added
  through that event instead of stopping the game (RELOAD-2, found by the widened corpus).
- Results of the dispatched run on `ae2eee1` (run 355): every gate passed on all four versions. The corpus got past
  FML's module scan with spark's two service files left out. The 1.21.11 corpus client then crashed on malilib's
  accessor (PRE-9, fixed in the next commit), and the 26.x clients and servers stopped in FML's loader on Zoomify's
  universal jar, a NeoForge mod too that needs Kotlin for Forge (CORPUS-12: taken out of the corpus).
- Results of the dispatched run on `fbc4d06` (run 356): every gate passed on all four versions. The corpus servers
  ran; the corpus clients stopped at "Initializing game" on a reload listener registered before NeoForge's event
  (RELOAD-2). Failing a mod over a broken accessor (PRE-9's first form) disabled OneConfig (and with it PolyPlus and
  Polydium), Iris's Fabric build and adventure-platform-fabric, and the Polyfrost client crashed starting its server
  ("No platform-specific hooks found for adventure platform"); both are reworked in the next commit. Iris loaded the
  shader pack on 26.1.2 and 26.3.
- Results of the dispatched run on `fab3e29` (run 357): every gate passed on all four versions, the early reload
  listener check included. The Polyfrost clients reached their worlds again, and Iris's Fabric build loaded and
  meshed terrain on 26.x. Iris loaded the shader pack on 26.1.2 and 26.2; the 26.3 shader step ended after 7 seconds
  without the line (why is UNVERIFIED). The corpus servers ran; every corpus client got past RELOAD-2 and then
  crashed on malilib's accessor, which malilib calls while the game starts (CORPUS-13: malilib, Tweakeroo, MiniHUD
  and WTHIT taken out of the corpus).
- Results of the dispatched run on `61d0b0c` (run 353): every gate passed on all four versions, the configuration
  disconnect check included. The informational corpus server and clients stopped at FML's module scan on every
  version (PKG-4, fixed in the next commit).
- The mod corpus gains 24 popular Fabric mods (libraries, interface, rendering, performance and content), so its
  runs show more of what fails.
- The energy check takes Team Reborn's library from Tech Reborn's or Modern Industrialization's jar
  (`ci/nested-jar.py`) where the library has no build of its own on Modrinth.
- Results of the dispatched run on `cbe5511` (run 350, XFER-1): all versions green. The energy check ran and passed
  on 26.1.2, 26.2 and 26.3, with the library taken from Tech Reborn's jar (6.0.5, 6.1.1, 6.2.0); Modrinth has no build
  of it for 1.21.11, of its own or nested. Every corpus client reached its world; pre-flight disabled 7 mods on
  1.21.11 (owo, JEI, Farmer's Delight, Continuity, Balm, ShulkerBoxTooltip, and one the summary cuts off), 7 on
  26.1.2 (Moonlight, CodecUI, ShulkerBoxTooltip, Balm, Comforts, owo, and one cut off), 6 on 26.2 (ShulkerBoxTooltip,
  Balm and Waystones, owo, JEI, Comforts) and 4 on 26.3 (ShulkerBoxTooltip, JEI, Balm and Waystones). The 26.2 corpus client no longer
  crashes on a Fabric fluid (FLUID-1). The 26.2 Polyfrost client crashed on Polydium's `@WrapMethod` (PRE-6, fixed in
  the next commit).
- Results of the dispatched run on `9621e53`: all versions green. Iris logged `Using shaderpack: fabricated-test` on
  26.1.2, 26.2 and 26.3, with every Fabric renderer check passing under the pack. The transfer check (item and fluid
  cells both ways) and the commands check pass on all four versions. The energy check ran nowhere: Modrinth has no
  build of Team Reborn's library for any of them. The 26.2 corpus client crashed on a Fabric fluid (FLUID-1).
- Results of pull request #47 on `4e9542e`: all versions green. With `BlockStateModelDefaultsMixin` gone, the renderer
  API checks on 26.x pass (the bridges declare its two methods). On 1.21.11 the corpus disables the same 7 mods as
  before, so checking mixins into other Fabric mods there disabled none that worked. The new run with Sodium, Iris
  and a shader pack passes every Fabric renderer check on 26.3; whether Iris loaded the pack is UNVERIFIED (the job
  log tool reaches only the log's last lines, which the summary step fills): the summary now prints Iris's lines.

## 2026-10-21 (late, 7): one way to resolve conflicting defaults

**Summary.**
- Results of pull request #46 on `d3a8e49`: all versions green. On 1.21.11 the model loading API loads again, its
  wrapper models' `emitQuads` calls listed as linkage warnings, the "Fabric model loading API (1.21.11)" check
  passes, and the corpus client reaches its world (7 mods disabled by pre-flight, as before: Balm, Waystones,
  ShulkerBoxTooltip, owo, Continuity, JEI, Farmer's Delight). The 26.3 corpus report of the run before (`89311cd`)
  shows Entity Culling's bridge, and that client reached its world (CORPUS-11).
- `BlockStateModelDefaultsMixin` is gone: it declared NeoForge's `createGeometryKey` and `particleMaterial` on
  `BlockStateModel` by hand, where Fabric API's renderer adds `FabricBlockStateModel`'s defaults of both. The
  translation finds both pairs (the 26.3 report listed them) and `DefaultBridgeProcessor` declares them the same way.

**Verification.** Core: 175 tests pass. The removed mixin's methods are what the bridges declare (`Owner.super` of
NeoForge's `BlockStateModelExtension`); CI's renderer API checks on 26.x call them on Fabric models, and a missing
declaration crashes there (`IncompatibleClassChangeError`), as it did before the mixin existed. CI: see the pull
request.

## 2026-10-21 (late, 6): conflicting default methods an added interface brings (CORPUS-11)

**Summary.**
- Results of pull request #45 on `cd0a119`: all versions green. FML accepts the enum extension file, Farmer's Delight
  loads on 26.3 and the corpus client's player joins its world (ENUM-1 works). The POLY-1 regression check passes in
  every client run.
- CORPUS-11: Entity Culling 1.11.3's interface mixin gives `BlockEntityRenderer` a second default
  `getRenderBoundingBox`, beside NeoForge's, and the game crashed on the first renderer declaring neither. Translation
  now finds such defaults, for mixins' interfaces and class tweakers' injections, and a class processor declares each
  on its target after Mixin applies, where no mixin declared it, calling NeoForge's default.

- CI on `5a3c446`: green, and the 26.3 corpus client reached its world with Farmer's Delight and Entity Culling, but no
  bridge was reported: the game class index read only Minecraft's jar, not NeoForge's, where its extension interfaces
  are. It now reads NeoForge's jar after Minecraft's.
- CI on `57027f6`: red. With the bridges found, declaring them before Mixin made Fabricated's own
  `BlockStateModelDefaultsMixin` fail (it declares two of them, Mixin won't overwrite without `@Overwrite`), and every
  client stopped. They are now declared after Mixin, only where no mixin declared the method.
- CI on `89311cd`: the 26.x versions green. The 26.3 corpus report shows Entity Culling's bridge
  (`BlockEntityRenderer` gets `getRenderBoundingBox`, calling `IBlockEntityRendererExtension`'s default) and the
  client reached its world; it also found the two `BlockStateModel` pairs Fabricated's own mixin declares. 1.21.11 red:
  with NeoForge's classes in the index, the linkage check now resolves methods of patched game classes through
  NeoForge's extension interfaces and could tell the model loading API's wrapper models call a missing `emitQuads` (the
  renderer API's, which its plan loads it without and NeoForge never calls), so it disabled the module and Lootr and
  Traveler's Backpack lost their models. A Fabric API module planned without a dependency is no longer disabled for
  such calls; they stay linkage warnings.

**Verification.** Core: 175 tests pass, among them `DefaultBridgeTest` (a bridge for an interface and a class target;
none for an abstract method, another descriptor, a subinterface of NeoForge's, a target declaring the method, an
absent target) and a pipeline test (an interface mixin's bridge, kept in the cache), and the refused-mods test now
covers bridges. A pipeline test covers the linkage rule (a module planned without the renderer API keeps a call
of its `emitQuads`; another module calling it is disabled), and fails without the change. Entity Culling's history has the mixin (`723866e`) and its removal (`7eb3d3f`). NeoForge injects
`IBlockEntityRendererExtension` into `BlockEntityRenderer` (its `injected-interfaces.json`), a direct superinterface,
as `Owner.super` requires. The class processor's bridge builds only in CI. CI: see the pull request.

## 2026-10-21 (late, 5): NeoForge enum extensions from enum mixins (ENUM-1), a POLY-1 regression test

**Summary.**
- Results of pull request #44 on `f10c484`: all versions green; the 26.3 corpus client reaches its world with Farmer's
  Delight disabled for its enum mixin (CORPUS-10 holds), and the 1.21.11 dedicated server passes the `TAGS_LOADED`
  check.
- ENUM-1: an enum mixin that only adds constants to one of NeoForge's extensible enums becomes the mod's NeoForge enum
  extension file, named in its generated `neoforge.mods.toml`, when NeoForge can take each constant (its name starts
  with the mod's id; a constructor NeoForge allows; constant arguments). Other such mixins still disable their mod.
- POLY-1 regression test: the test mod replaces the server's player list as the Polyfrost stack does, and a smoke
  check reads Fabricated's warning. The POLY-1 diagnostics (the integrated server's pause inputs, the player list's
  placement and removal logs, the mixin lists) are removed.

- CI on `74e82a0`: FML refused the first enum extension file (its `enum` is an internal name, not a descriptor) and stopped
  the 26.3 corpus client while loading; fixed. Enums NeoForge marks indexed or named are not translated.

**Verification.** Core: 171 tests pass, among them the enum extension unit tests (entries, the file's format, and six
cases NeoForge can't take) and a pipeline test (the mod loads with its file and the toml key, its other mixins kept;
a mod whose constant takes a computed argument stays disabled). FML's `EnumPrototype` and `RuntimeEnumExtender` were
read for the format and its rules (the name prefix, `@ReservedConstructor`). The workflow YAML parses and the smoke
scripts pass `bash -n`. CI: see the pull request.

## 2026-10-21 (late, 4): Polyfrost reaches its world (POLY-1), item components before tags loaded (CORPUS-9)

**Summary.**
- Results of pull request #43 on `6f6ae41`: the 26.3 corpus client's crash comes from Jade's Fabric build building an
  item stack in Fabric's `TAGS_LOADED`, which the 26.x bridge fired before NeoForge binds the items' default components;
  the Polyfrost clients' integrated server counts the player while its list returns none, and pauses.
- CORPUS-9: `TAGS_LOADED` after a data load fires once the items' default components are bound, as upstream (26.x).
  The dedicated server's smoke test builds an item stack in it, which found that 1.21.11 never fired it for a data load
  (NeoForge's event there carries a lookup provider, which the bridge skipped): it now fires where upstream fires it.
  On `38082b8` the 26.x servers pass the check and the 26.3 corpus client gets past world creation.
- POLY-1 fixed: NeoForge's `PlayerList.getPlayers()` returns a view made when the list is constructed, so a mixin
  replacing the list left it empty. `getPlayers()` now follows the list in the field. On `38082b8` the Polyfrost
  clients reach their world on 26.3 and 1.21.11 and pass every check.
- CORPUS-10: an enum mixin adding constants to one of NeoForge's extensible enums now disables its mod even when the
  config has a plugin (Farmer's Delight crashed the 26.3 corpus client's server). ENUM-1 plans translating such mixins.
- CI: both player counts, whether each integrated server tick ended paused, and the mixins applied to the integrated
  server and the player list.
- COMPAT-2 (won't fix): the unformatted `[Fabricated] {0} [[...]]` lines are NeoForge's logging (its dedicated server,
  and its 1.21.11 client); 26.x clients and the warnings screen format them.

**Verification.** Core: 168 tests pass; the new pre-flight test fails without the change (the finding was `LEFT_OUT`).
The workflow YAML parses and the server smoke script passes `bash -n`. NeoForge's 26.1, 26.2 and 26.3 branches post
`TagsUpdatedEvent.ServerDataLoad` and then `DefaultDataComponentsBoundEvent(false, false)` in that order, on the same
thread, from `updateComponentsAndStaticRegistryTags`; the client posts its `TagsUpdatedEvent` after
`RegistryDataCollector` has bound the components. NeoForge 1.21.11's `TagsUpdatedEvent` for a data load carries
`registryLookup`, a lookup provider; upstream's 1.21.11 mixin keeps the registries from `ReloadableServerResources`'
constructor. NeoForge's 1.21.11, 26.1, 26.2 and 26.3 branches all patch `getPlayers()` to return `playersView`, an
unmodifiable view initialised from `players` with the list. CI on `38082b8`: 26.1.2, 26.2 and 26.3 green; 1.21.11 red
only on the new `TAGS_LOADED` check (fixed in this push).

## 2026-10-21 (late, 3): the integrated server never ticks

**Summary.**
- Results of pull request #42 on `320123c`: CORPUS-8 holds. The 1.21.11 corpus client reaches its world with 7 Fabric
  mods disabled (listed as warnings); the 26.3 corpus client gets past loading and crashes on an item stack built before
  item components are bound (CORPUS-9). POLY-1: the integrated server places the player and still never ticks; half a
  minute later its list is empty without a removal.
- CI: which server and player list each POLY-1 line is about, the integrated server's pause inputs at each tick, and
  more frames of each `Caused by` in crash reports.
- COMPAT-2 (cosmetic, open): 1.21.11's FML shows Fabricated's warnings with their placeholder unformatted.

**Verification.** The workflow YAML parses. The test mod's new mixins build only in CI; the diagnostics injectors not
every version may have are optional (`require = 0`). CI: see the pull request.

## 2026-10-21 (late, 2): where the player goes missing, warnings that stop CI clients

**Summary.**
- Results of pull request #41 on `7f3550f`: CORPUS-7 holds (the 1.21.11 corpus disables Continuity before launch,
  naming the renderer API class its mixin uses). The thread dumps now have the game's threads. POLY-1: the integrated
  server never ticked and has no player while the client is in its world. The 26.x corpus clients are stopped by
  mc-runtime-test at NeoForge's warnings screen (CORPUS-8).
- CORPUS-8: CI runs with extra mods keep NeoForge's loading warnings off the screen; the summary prints them.
- CI: the test mod logs the player's way into the integrated server and out of it.

**Verification.** The workflow YAML parses and the shell script passes `bash -n`. CI: see the pull request.

## 2026-10-21 (late): mixins that need an unsupported module, POLY-1 narrowed

**Summary.**
- Results of pull request #40 on `d581ead`: CORPUS-6 holds (the 1.21.11 gate passes: a Fabric-format model file is
  read, and the baked extra models reach the model manager). The 1.21.11 corpus client now gets past model loading and
  crashes on Continuity's renderer API mixin instead (CORPUS-7, fixed here). The Polyfrost clients have no overlay,
  aren't paused and have no chunk at all (POLY-1: the overlay hypothesis is ruled out). The 26.3 corpus client fails
  to read NeoForge's own model files ("zip file closed") during model loading, then closes FML; its shutdown line did
  not print.
- CORPUS-7: pre-flight disables a mod whose mixin uses a class only an unsupported Fabric API module has.
- PRE-5: the classes a mixin uses are collected from its code and annotations too.
- CI: the next POLY-1 diagnostics (packet rate, the integrated server's view, the game JVM's thread dump), the shutdown
  line written to the process's own output, and who calls `Minecraft.stop()`, with its stack.

**Verification.** `:core:test` (167 tests) passes, with `PipelineTest.aMixinUsingAnUnsupportedModuleFailsItsMod`. The
workflow YAML parses and the shell script passes `bash -n`. CI: see the pull request.

## 2026-10-21 (even later): model loading on 1.21.11, thread dumps by SIGQUIT

**Summary.**
- Results of pull request #39 on `e1e19c9`: the 1.21.11 Polyfrost client now reaches its world too, and waits there for
  its chunk as the 26.3 one does (POLY-1). The thread dump section stayed empty: jstack can't attach to the game.
- CORPUS-6: Fabric API's model loading module loads on 1.21.11, without the renderer API (`dropDependencies` in the
  plan), with the bridge 26.x uses for its extra models and Fabric's model formats, now shared. On `e1e19c9` the 1.21.11
  corpus showed exactly the failure this fixes: the client entrypoints of Continuity, Lootr and Traveler's Backpack
  could not find model loading classes, and Continuity's `AtlasManager` mixin then crashed the client.
  On the first CI run of this change every 1.21.11 client with Fabric API crashed: upstream's hook handing the baked
  extra models to the model manager finds no local of their type at the end of NeoForge's `ModelManager.apply`. It is
  left out on 1.21.11, and Fabricated hands them over from NeoForge's bake event call.
- CI: the thread dump before the timeout is taken with SIGQUIT. For a client that ends without reaching a world, the
  test mod's shutdown hook prints which thread ended the game and from where (the 26.x corpus clients close FML right
  after model loading with no error).
- CORPUS-1: a translated Fabric mod's `pack.mcmeta` loses the sections only NeoForge reads, as on Fabric, so the
  corpus no longer logs errors for Terralith's and Lithostitched's NeoForge overlays.
- CI: half a minute into its world, the test mod logs the client's overlay (with its reload's progress), whether it
  is paused, and whether its chunk and any others arrived. Hypothesis for POLY-1 (UNVERIFIED): the integrated server
  stops while the client is paused, and an overlay pauses it with no screen open. Polydium's `MixinHideReloadOverlay`
  keeps a reload's `LoadingOverlay`, hidden, until the reload is done, so a reload that never finishes would leave the
  player waiting for a chunk the server never sends.

**Verification.** `:core:test` (166 tests) passes, with `PipelineTest.aModuleLoadsWithoutTheDependenciesItsPlanDrops` and
`TranslateHelpersTest.packMetadataLosesOnlyNeoForgeSections`.
The workflow YAML parses; the shell script passes `bash -n`; the summary's new awk was run on a sample. The Minecraft
modules build in CI only: see the pull request, including the new 1.21.11 gate "Fabric model loading API".

## 2026-10-21 (later still): where the Polyfrost world stalls

**Summary.**
- Results of pull request #38 on `039fd37`: with Polydium's marker, no prompt opens; the 26.3 client waits in its world
  for its chunk, which never arrives (POLY-1).
- CI: the summary prints the render and server threads near the timeout, or the start of the thread dump when it has
  neither.

**Verification.** The workflow YAML parses, and the summary's new lines were run, as parsed, on sample dumps (with both
threads, and with a jstack error only). CI: see the pull request.

## 2026-10-21 (later): Polyfrost reaches a world

**Summary.**
- Results of pull request #37 on `97bb819`: with ROOT-1, OneConfig's client entrypoint runs, and the 26.3 Polyfrost
  client reaches a world, where Polydium asks whether to switch to Vulkan. The world test waits for no screen to be open,
  so the run ended at its timeout.
- CI: Polydium's marker is created before the Polyfrost run, as after a player answered its prompt (POLY-1).

**Verification.** The workflow YAML parses. CI: see the pull request.

## 2026-10-21: every mod has a root

**Summary.**
- Results of pull request #36 on `6c17ffa`: "late vanilla reload listener OK" passes on all four versions, and the
  1.21.11 Polyfrost client no longer crashes on RELOAD-1. Every version then fails in OneConfig's list of mods, with
  the frames that name it.
- ROOT-1: built-in mods and nested NeoForge mods have a root path, as every mod has on Fabric.

**Verification.** Core: `./gradlew -Pfabricated.coreOnly=true build` green locally, with a new `PipelineTest` case.
CI: see the pull request.

## 2026-10-20 (night, latest): late reload listeners

**Summary.**
- Results of pull request #35 on `8ca959a`: the summary names each Polyfrost client's stop. 1.21.11 crashed on
  RELOAD-1; on all versions OneConfig's client entrypoint throws `NoSuchElementException` (POLY-1), and on 26.x the game
  then stops by itself. The 1.21.11 corpus client crashes on CORPUS-6.
- RELOAD-1: a reload listener registered the vanilla way after NeoForge sorted them is added last instead of throwing.
- CI: each first error of a client that reached no world comes with its exception's first frames.

**Verification.** The workflow YAML parses and the summary's awk was run, as parsed, on a sample log. Client check "late
vanilla reload listener OK". CI: see the pull request.

## 2026-10-20 (night, later): prefixed shadows on obfuscated versions

**Summary.**
- Results of pull request #33 on `cf41167`: on 26.1.2 and 26.3 the Polyfrost run disables no jar and no client crashes
  (NATIVE-1 and ADV-1 fixed). The report confirms Skiko's runtime jars sit beside `skiko-awt`.
- REMAP-1: a `@Shadow` with a prefix (`shadow$comp_925`) is remapped on 1.21.11; adventure's shared jar failed
  pre-flight on it. On `5b42545` it loads, and the 1.21.11 Polyfrost run disables only Fabric API's model loading
  module.
- POLY-1 (open): no Polyfrost client reaches a world yet; the summary now prints what stopped each one. The 26.x
  corpus clients reach no world either on `5b42545` (their logs end after model loading, without a crash report),
  which the same output will explain.
- CI: the summary prints the last log lines of a client that reached no world, and its render thread near the timeout
  (POLY-1).

**Verification.** Core: `./gradlew -Pfabricated.coreOnly=true build` green locally, with a new `RemapTest` case. CI:
see the pull request.

## 2026-10-20 (night): native libraries of jars without classes, mixin configs of nested jars

**Summary.**
- PKG-2 (pull request #31) and PKG-3 (#32): MixinSquared's Fabric jar loads beside OneConfig's copy of its common
  classes. On `b2b91e8` the Polyfrost run disables 1 jar (ADV-1) and both clients get to "Initializing game".
- NATIVE-1: the root files of a nested jar without classes are served by the modules beside it too, as Skiko needs
  for its native library. The Polyfrost summary prints which modules serve them.
- ADV-1: a mixin config a mod declares but its nested jar holds is applied by that jar (adventure-platform-fabric).

**Verification.** Core: `./gradlew -Pfabricated.coreOnly=true build` green locally, with new `PipelineTest` and `CandidateScannerTest` cases. CI:
see the pull request.

## 2026-10-20 (later): split packages, registries for client initializers, review fixes

After pull request #28, from its last CI runs and an independent review of its diff.

**Summary.**
- PKG-1: a package split between Fabric jars (Kotlin and Compose libraries, OneConfig's modules, MixinSquared) is
  served by one module instead of disabling the later jar. Found by the Polyfrost CI run, where Polydium then
  crashed on its MixinSquared selector.
- REG-2: the registries are open while Fabric client initializers run, as Fabric API leaves them; Xaero's Minimap
  stopped the corpus clients. Likely CORPUS-2's cause too.
- Review fixes: PRE-3 (a cached pre-flight result outlived the install of a mod it couldn't check), PRE-4, DISC-2,
  CFG-3, and the baked colour gate (FRAPI-2b). One `MixinPreflight` constructor, one constant for Fabricated's mod id.
- CI: the plugin hosts' builds are pinned per version and the run is a gate; the test mod's field-reference Jade
  entry is now valid on Fabric (Jade's Fabric build in the corpus failed on it).
- Results on `72f5472` (pull request #29): the corpus clients run through and Xaero's World Map reaches a world
  (CORPUS-2 fixed by REG-2); the Polyfrost run disables 3 jars instead of 18. Then PLAT-1: night-config's JSON
  module was taken for the platform's. The CI summary now prints each crash's description and errors.

**Verification.** Core: `./gradlew -Pfabricated.coreOnly=true build` green locally, with new tests (`PackageGuardTest`,
split packages and a target installed later in `PipelineTest`, javac's names in `MixinPreflightTest`). CI: see the pull
request.

## 2026-10-20: rendering-pipeline track, R3, plugins for recipe viewers, audit

Pull request #28. `docs/progress.md` became this roadmap.

**Summary.**
- The rendering-pipeline track above, from source research and CI's first run of Sodium's and Iris's Fabric builds
  (`loadUnsupported`, a new config option): both load and reach a world on 26.2, then Sodium crashed on its first
  block quad. Fixed generically (SODIUM-1, a pre-flight repair), along with FRAPI-2.
- R3: pre-flight checks mixins into Fabricated's Fabric API and installed NeoForge mods (nested ones too); the cache
  keeps the fingerprints of the classes it read and translates again when one changes.
- Plugins for JEI, EMI, REI and Jade reach their NeoForge builds; Jade's per-mod settings too.
- A code audit: REG-1 (P0 on current NeoForge 26.3 builds) fixed, with a CI run on the newest NeoForge build; JVM
  errors no longer swallowed in three places; duplication and stale docs removed. No dead code found in core.
- Fabric's start-up order: language adapters are created before any Fabric mod's mixin plugin loads (PolyPlus and
  Polydium rely on it). Pre-flight also checks mixins into other Fabric mods on 26.x, stops judging members another
  mod's mixin may add (PRE-2), notes `skipMixins` entries that name nothing (CFG-1), and only looks up well-formed
  class names.
- CI: gates for FRAPI-2 and the constructor repair; informational runs with OneConfig and PolyPlus, and with Xaero's
  World Map alone so the rest of the corpus reaches a world (CORPUS-2).

**Verification.** Core: `./gradlew -Pfabricated.coreOnly=true build` green locally, with new unit tests
(`InstalledClassesTest`, `PluginAnnotationsTest`, pre-flight of installed-mod targets, the bridge repair,
cache invalidation in `PipelineTest`, nested classes in `NestedNeoForgeJarsTest`). CI on `dbdff1d`: all versions green,
the Fabric Sodium/Iris run as described. CI on the later commits: see the pull request.

## 2026-10-19 (night): Fabric model formats (M4), and renderer mods (Sodium, Iris)

Pull request #23 (merged as `1ebfc5b`): M4. Pull request #24: renderer mods.

**Summary.** Fabric's custom model formats now work next to NeoForge's (M4: MODEL-1 and MODEL-2 fixed), and a new
pre-flight repair points an injection at a call NeoForge's code makes through a subtype (Lithostitched loads). For
renderer mods, the research (Sodium `dev`/26.2/26.1.2/1.21.11, Iris, Forgified Fabric API, NeoForge, FML) found that
Sodium for NeoForge already ships its own Fabric renderer API implementation and switches it on whenever
`fabric_renderer_api_v1` is a loaded mod, which Fabricated plus Fabric API gives it. Fabric block models are then
meshed by Sodium itself, so no Sodium-specific code is needed in Fabricated. What broke on that path is fixed:

- **DISC-1**: NeoForge mods nested in jars (jar-in-jar) were invisible to Fabric code (Sodium's mod is nested in a
  service jar). `NestedNeoForgeJars` and `NeoForgeMods` read them.
- **IND-1**: NeoForge `modproperties` weren't visible to Fabric code, so Indigo didn't stand down for Sodium's
  renderer. They are now Fabric custom values, and `fabric:provides` ids are present (Sodium provides Indium).
- **FRAPI-1**: upstream's `emitQuads` defaults drop the level, the model data and NeoForge's ambient occlusion when
  Fabric's renderer draws a NeoForge model, which under Sodium is every model. `FrapiDefaults` has the
  NeoForge-aware defaults, as Forgified Fabric API does.

Sodium's and Iris's Fabric builds are now refused with a message naming their NeoForge builds, and a Fabric mod's
Sodium integration (its options page, its overrides of Sodium's mixin options) is written where Sodium for NeoForge
reads it.

**Verification.** Core: 144 tests, 0 failures (local). The loader's TOML reading was compiled against night-config
3.8.0 and run on Sodium's real `neoforge.mods.toml`. CI on `08e9a5f` (#23's head): all four versions green. In the
26.3 corpus run Lithostitched loads (the subtype repair applies to `PendingRegistrationMixin#loadFromResource`), and
Traveler's Backpack's "Expected BackpackDynamicModel" errors are gone (four "Missing FluidModel" errors remain, see
CORPUS-5). CI on `75e5867`: the renderer gate's new check, a NeoForge `DelegateBlockStateModel` inside a Fabric
model getting the chunk section's level, passes on 26.1.2, 26.2 and 26.3.
CI on `7fe52ed` ran Sodium and Iris for NeoForge from Modrinth with Fabric API and the renderer API test mod (26.1.2:
Sodium 0.9.2, Iris 1.11.4; 26.2: Sodium 0.9.2, Iris 1.11.4; 26.3: Sodium 0.9.3-beta.1, Iris 1.11.7). On 26.1.2 and 26.3
every check passes: Indigo stands down ("Different rendering plugin detected"), Sodium meshes the Fabric test model on
its chunk builder thread (the level is Sodium's `LevelSlice`), the NeoForge model inside it gets that level, and the
falling block and the breaking overlay are drawn through Sodium's renderer. On 26.2 every check but the falling block
passed: the test dropped it from a fixed offset beside the player, and in that world (blocks placed at y=74) it likely
never fell into view before the client quit, six seconds after the blocks were placed. The test now drops each copy
down a cleared column beside the test block, onto a support at eye level, whatever the terrain.

## 2026-10-19 (later): Fabric models as moving blocks, in the breaking overlay and inside vanilla models

Pull request #22 (base `main` at `b7ff2a8`, after #21 merged). Roadmap items M2 and M3.

**Summary.** Fabric block models are now drawn everywhere NeoForge draws blocks, except outlines: chunk sections
(M1), moving blocks pushed by pistons or falling, and the breaking overlay (M2). A vanilla multipart or weighted model
with a Fabric model among its parts goes through Fabric's renderer as a whole (M3). Only those blocks take Fabric's
path; every other block keeps NeoForge's. `FabricTerrain` became `FabricBlockRendering`, still the only class naming
the renderer API's types. On 26.1.2, upstream's `@Overwrite` of the breaking renderer (every model through Fabric's
renderer) is left out in favour of the same hook as on 26.2 and 26.3.

**Verification.** CI run 246 (`1596fff`): all four versions green. The 26.x gate now requires four lines, logged by
the test models: `frapi terrain OK` and `frapi nested terrain OK` (section worker threads), `frapi moving block OK`
(a falling copy of the test block) and `frapi breaking OK` (the server sends breaking progress), on 26.1.2, 26.2 and
26.3. On 26.1.2 the breaking overlay hands the model the air block state, as NeoForge's own code there does (and as
upstream's replacement did); 26.2 and 26.3 hand it the block's state. The same run shows `08aff28`'s constructor
repair also applying to a corpus mod: Lithium's `EntityCollisionContextMixin#initVars`.

## 2026-10-19: audit, fixes, and Fabric's renderer API in the world

Branch `claude/great-ramanujan-9lcl5m`, pull request #21 (base `main` at `6664f72`).

**Summary.** A full audit of the loader, core and Minecraft-facing code for faults that only show in production
(threading, leaks across world switches, mixin fragility, swallowed exceptions, Windows file system behaviour,
memory). Fifteen defects are fixed (one P1, seven P2, seven P3). Eleven smaller or unconfirmed ones are deferred
with reasons. No P0 (crash on a normal launch, data loss) was found. Fabric's renderer API now draws Fabric block models into
NeoForge's chunk meshes on 26.x. That work found a P1: Fabric's model-loading modifiers never ran on NeoForge 26.x,
because of how Mixin picks a constructor. The architecture map, FRAPI design and rules, and mixin conventions are
in `claude.md`. The inventory of every mixin is in `MIXINS.md`.

**Verification.** `./gradlew -Pfabricated.coreOnly=true build`: 150 tests, 0 failures (local). The
Minecraft modules, server and client runs are verified by CI on every push (see `claude.md`, *Building and
verifying*). Run 233 (`e0c5ffa`): every version green except the new renderer API gate on 26.x. Its cause is fixed in
`08aff28`. Runs 236 and 237 (`3211d56`, every code change in this session): all four versions pass every gate,
including the renderer API gate on 26.1.2, 26.2 and 26.3.

