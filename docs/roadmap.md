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
| `forge` | Modern Forge platform code: Forge 52 (Minecraft 1.21.1) and Forge 61 to 66 (1.21.11 and later) |
| `forge-legacy` | Legacy Forge platform code: Forge 47 (1.20.1) and Forge 43 (1.19.2, in beta) |

- Work happens on `claude/dev` (pull requests into `main`), and for Forge-only changes on `claude/dev-forge` and
  `claude/dev-forge-legacy`, cut from `forge` and `forge-legacy` with pull requests back into them.
- Shared changes land on `main` first and reach the Forge branches by merge (`scripts/sync-platform-branch.sh`, from a
  working branch cut from the platform branch). A platform branch is never merged into `main`.
- Logic that could be shared belongs on `main`; the platform branches stay as thin as possible. Code is never copied
  between branches.
- Nobody pushes to `main`, `forge` or `forge-legacy` directly. A pull request is merged once its full matrix is green.
- Commit messages, pull requests, release notes and documents carry no session links or tooling attribution.

## What comes next

The branch restructure is done: Forge for Minecraft 1.21.1 (Forge 52) moved from `forge-legacy` to `forge`. `forge`
holds modern Forge (1.21.1, and 1.21.11 and later) and `forge-legacy` legacy Forge (1.20.1, and 1.19.2 in beta).

In this order:

1. **v0.6.0-beta.3**, a compatibility release. What it contains is in its
   [release notes](releases/v0.6.0-beta.3.md) and in [`CHANGELOG.md`](CHANGELOG.md).
2. **Forge for Minecraft 1.19.2 (Forge 43)** on `forge-legacy`, sharing its code with 1.20.1: in beta, with Fabric's
   renderer API bridged as on Forge 47 ([`CHANGELOG.md`](CHANGELOG.md), 2026-11-18). What doesn't work there is in
   the support matrix's Forge 1.19.2 column.
3. **A bug-hunting pass, with no new features.** The representative mod set, popular combinations of mods and the
   difficult mods run on every loader and Minecraft version. Failures are grouped by root cause and fixed by impact; a
   parity gap between loaders or versions counts as a bug; every fix gets a regression test. The pass ends when the set
   loads and runs everywhere, and every failure that remains is documented and says so in the game, with a clear
   message.
4. **Finishing touches**, alongside the pass:
   - a smoke-boot CI job for every loader and Minecraft version;
   - GitHub issue forms;
   - a final check of the documents.

What a beta release must hold, and how a release is cut, is in
[`CONTRIBUTING.md`](../CONTRIBUTING.md#releasing).

## Open findings

Severities as [`FINDINGS.md`](FINDINGS.md) defines them. A finding moves there, with its cause and fix, in the change
that fixes or closes it.

| ID | Sev | Location | Issue | Status |
|---|---|---|---|---|
| SEAM-14 | P2 on NeoForge 21.11 to 26.3 with FrozenLib (its hopper API's blacklisted containers); P3 on Forge 47 with Lithium (its hopper ejection optimisation) | NeoForge's `HopperBlockEntity.ejectItems` and `suckInItems`; Forge 47's `ejectItems` (STRUCT-1, `ReplacedMethods`); `KnownLimits` | NeoForge looks a hopper's containers up through a lookup of its own (a container, else a capability's item handler, else an entity's), so vanilla's `getAttachedContainer` and `getSourceContainer`, which FrozenLib hooks, are gone; which container it answers for an entity can differ from vanilla's random pick. Forge 47 replaced vanilla's private `ejectItems`, to which STRUCT-1 moves only an `@Inject`, not Lithium's `@Redirect` and `@ModifyVariable`. The hooks are left out, a known limit the report names. | Kept as a known limit: the lookup's `container()` isn't vanilla's call for entities' containers, and Lithium's hooks there only speed hoppers up |
| LOCAL-6 | P1 on every NeoForge and Forge version Ledger runs on (NeoForge 21.1 to 26.3, Forge 52 to 66) with Ledger 1.3.5 (its logging of an item put on a campfire, left out in the advanced corpus runs); for any Fabric mod's `@Local` of a value vanilla keeps in a local that the platform's code holds only until it reuses the slot | NeoForge's and Forge's game classes, compiled from the game's decompiled sources (`CampfireBlock.useItemOn`) | Ledger's `CampfireBlockMixin` takes `@Local BlockEntity` at the `Player.awardStat` call in `useItemOn`. Vanilla 1.21.1 keeps the block entity in slot 8 and the campfire cast from it in slot 9, both in scope there. NeoForge 21.1.265 and Forge 52.1.21 test `level.getBlockEntity(pos) instanceof CampfireBlockEntity` on the value kept in slot 9, which their local variable table doesn't name, put the campfire in slot 8, and store the held item in slot 9 before the call: no slot holds a block entity there, so Mixin finds none, and pre-flight, which judges such a local where the injector applies (PRE-36), leaves the hook out ("doesn't hold where its @At points are"). It isn't a frame count (FRAME-2): Fabric's Mixin 0.16.4 and SpongePowered's 0.8.7, their `Locals.getLocalsAt` run on the platforms' classes, find the same locals with the method's frames recorded compressed or expanded, and Forge's frame repair (CORPUS-79) can't bring the value back either (read in the three classes; Ledger's mixin read in its 1.3.5 jar, LGPL). Forge 61's and NeoForge 26.1.2's `useItemOn` keep the block entity in slot 9 and store the held item there before the call too (read in their classes), and the v0.6.0-beta.3 release's runs leave the hook out on all ten versions; the report names it a known limit. | Open: the platform's method would need the tested value kept in a slot of its own up to the point, a rewrite of its locals that nothing makes yet. |
| MODIFIER-4 | P3, no case seen | `ModTranslator` (MODIFIER-3) | A Fabric jar's Forge structure modifiers stay as they are: Forge has no structure modifier that changes nothing but `forge:none`, one object, which a registry refuses under a second name. On Forge one of them would change the structures as the mod's Fabric code does too (MODIFIER-2's case), and one of a type only its Forge build registers wouldn't decode. No corpus jar carries one. | Open: if one is seen, a structure modifier type that changes nothing and decodes to a new object for each file, registered by the in-game mod on every Forge version. |
| FRAME-2 | P2, UNVERIFIED on real mods | NeoForge's patched game classes, which NeoForge compiles from its sources; Fabric's Mixin's local computation (`Locals.getLocalsAt`, FRAME-1) | NeoForge's classes have other frames than vanilla's, and Mixin counts their locals otherwise: in NeoForge 26.1.2's server game, 125 methods whose vanilla code Mixin counts right lose a live local at some frame (`LivingEntity.knockback`, `LivingEntity.startUsingItem`, `ServerLevel.sendBlockUpdated`, `Entity.collideWithShapes` among them), and 79 whose vanilla code loses one are counted right (every method with frames, its frames recorded and walked as Mixin does and counted as `getLocalsAt` counts them; 1,093 of vanilla's 14,102 lose one on Fabric too; the client's classes unscanned). A Fabric mod's local capture, `@ModifyVariable` or `@Local` at such a point finds other locals than on Fabric, as Resourceful Lib's did in Forge's patched `renderScreenEffect` (CORPUS-79); no mod seen failing so: Ledger's campfire hook, left out on NeoForge 21.1 and Forge 52, isn't one (LOCAL-6), nor was its explosion fire hook (PRE-36), Mixin finding the same locals there with the frames recorded expanded. Fabric mods accepting Fabric Loader below 0.12 (or any) get Mixin's behaviour 0.9.2 on Fabric, whose local computation counts each frame's own size, and at least 0.16.5 on NeoForge (FML's floor, `MixinCompat.clampForNeoForge`). | Open until a Fabric mod is seen failing so: then record a method's frames again, as Forge's repair does, only where vanilla's method counts right and NeoForge's doesn't at a Fabric injector's point (pre-flight has vanilla's classes, VANILLA-1, and each injector's target), which keeps Fabric's count elsewhere. Running Forge's repair on NeoForge as it is would also change the count where Fabric's Mixin miscounts, and Fabric's Mixin has no `ClassInfo.getMixins()` for it to call. |
| PRE-27 | P2, no case seen | `MixinPreflight.narrowLocalsAtMost` (PRE-25), `LocalRepairs` (LOCAL-2, LOCAL-4, PRE-25's ordinals) | PRE-25's bound counts the locals Mixin can find from the method's stores, loads and frames, but pre-flight reads game classes without their frames, so the frames' slots, which only widen what Mixin finds, are never counted: the bound can be too low, and a modifier that would apply left out. On SpongePowered's Mixin the ordinal repairs count from the local variable table, where a local Mixin keeps past its scope in a lower slot (PRE-26) would shift the ordinal it means. | Open: count with the frames `PreflightClasses.withFrames` gives (the points' indexes taken in that method), then check Apoli's hook on a Forge 47 server again; count kept locals in the ordinal repairs where `keepsLocalsPastScope` holds. |
| CI-8 | — (CI only) | Forge 52's test mods (`mc/forge/1.21.1`, `mc/forge-shared`) | Forge 47's own test mod's brewing recipe and recipe condition checks aren't Forge 52's: they're written in 1.20.1's APIs (`FabricBrewingRecipeRegistry`, `PotionUtils` and a static `PotionBrewing`; Fabric API 0.92's resource conditions in Forge 47's recipe `conditions` list), which 1.21.1 replaced (`FabricBrewingRecipeRegistryBuilder`; Forge 52 reads one `forge:condition`), so whether Forge 52 brews a Fabric mod's potion recipe, and asks a Fabric resource condition named in Forge's field, is unchecked: `ci/checks.tsv` marks both for 1.21.1. The rest of Forge 47's own checks, and Forge 61 to 66's ingredients on the network, load conditions and damage events, run on Forge 52 from `mc/forge-shared` (MODEL-5, NET-13, DROP-1, ACC-5); its registration order and brewing mix checks have nothing of Forge 52's to check, and `recipe-custom-ingredient` and `forge-fapi-network-ingredients` check on Forge 52 what its custom ingredient check checks. | Open |
| IRIS-3 | P2, UNVERIFIED in play | Iris 1.8's `fabric.MixinLevelRenderer` (a redirect of `ParticleEngine.render` in `renderLevel`), NeoForge 21.1's `LevelRenderer` | On NeoForge 1.21.1 pre-flight leaves the redirect out, where Forge 52 repairs it: NeoForge's `renderLevel` calls the particle engine's overload taking a frustum. What Iris loses with it (its particle pass's choice of particles drawn with the level) is unchecked. | Open |
| HANG-1 | P2, intermittent, cause UNVERIFIED | Essential's Fabric build, second launch (`client-essential`), on Forge 66 (26.3) | In extended run 42682670119 the client stopped logging just after it created its window and never reached its entrypoints, with no crash and no game thread in the harness's dump, until the run's timeout; the same job passed on its re-run, as it did in the extended run before. | Open: watch the next extended runs; a second hang there makes it a fault to trace from the run's logs |
| MIX-3 | P3, no case seen | The Forge branches' mixin configs but `fabricated-loot` (`MIXINS.md`, *Forge configs*) | They leave `overwrites.requireAnnotations` unset, which every NeoForge config sets: a method a Forge mixin adds that its target already declares replaces the target's silently, without an `@Overwrite`, where on NeoForge the launch would stop on it. | Open: set it on each, checking every method the configs' mixins add against their targets on every version first, in its own pull request on each Forge branch (each launch applies them all, so CI shows a collision). |
| PERF-3 | P3, UNVERIFIED | `NeoForgeMods.of` (the mods' declarations, `NestedNeoForgeJars.readAll`); split from PERF-2 | A cached launch still reads every NeoForge mod's declarations and nested jars: the phase takes 16 to 23 ms on 1.21.1 and 40 to 68 ms on 1.21.11 and 26.x in CI's servers (extended run 42522554523), which have few NeoForge mods. What in it costs that, and how it grows with a pack of NeoForge mods, is unmeasured. | Read the report's *Launch timings* on a large pack; if the nested jars are the cost, keep their contents in the cache by the file's hash. |
| PROVIDER-19 | — (CI only), UNVERIFIED | The test mod's client block entity check (`BlockEntityEventsClientTest`) on the NeoForge 26.1.2 client with Forgified Fabric API's renderer, whose `fabric-lifecycle-events-v1` client mixins are upstream's (read in its `26.1.2` sources) | The check reported neither way there in extended run 41871298631 (no "client block entity events OK", no failure logged), where it passed in extended run 41858750959; that client's other checks passed, and the 26.2 one passed it. The provider's module unloads a client's block entities as it clears its level (`ClientPacketListener.clearLevel`), and on a respawn or a login. The job log's tail kept only the summary's failure, not the check's own line saying whether the client stopped in its world (the check's run 683 case). | Likely CI-5 (fixed), not the provider's: this client has the renderer API test mod too, whose terrain block replaced the check's chest, and the check misses wherever no other block entity is near spawn; run 41871298631's log kept no line to tell. The check passed on this client in extended runs 42522554523 and 42524794672. Closes once it holds here on two extended runs with CI-5's fix. |
| PART-5 | P3 on NeoForge 26.x clients with Fabric API, for Fabric block models whose particle sprite depends on the level (the view from inside such a block only) | The NeoForge 26.1.2, 26.2 and 26.3 plans of `fabric-renderer-api-v1` (its left-out `block.particle.ScreenEffectRendererMixin` on 26.1.2 and 26.2, `block.particle.LevelExtractorMixin` on 26.3) | The screen effect seen from inside a block asks the block's model for its particle sprite without the level, where upstream's hook gives Fabric's level-aware one: the plans leave upstream's hooks out and nothing asks the level-aware sprite in their place. On 26.3 NeoForge's `extractPlayerState` calls its own lookup (`getViewBlockingStateAndPos`), not vanilla's `getViewBlockingState`, whose view blocking check upstream's hook reads the position from (read in NeoForge's 26.3.x patch, while fixing PART-4 on Forge 64 to 66, whose lookup does the same). The 26.3 plan's reason doesn't name it; `COMPATIBILITY.md` lists the plain sprite on all three. | Open: as PART-4 on Forge, keep the block and position NeoForge's lookup found and ask a Fabric model's level-aware sprite there, with the Forge renderer test mod's screen effect check on NeoForge too. |
| CORPUS-38 | P3, UNVERIFIED | Fabric Language Kotlin (Forge corpus runs) | The linkage check lists `KotlinLanguageAdapter -> missing class net.fabricmc.loader.language.LanguageAdapter`, Fabric Loader's old adapter interface, which Fabricated doesn't provide. Kotlin mods load through its current adapter; whether any mod still names the old one is UNVERIFIED. | Provide the old interface if a mod needs it. |
| CORPUS-66 | — (CI only) | The 1.21.1 advanced corpus client's run (Origins) | The 1.21.1 advanced client reached its world (extended run 41539993361: the player's chunk loaded after 600 ticks) and then ran to the 10-minute limit: Origins opened its `ChooseOriginScreen` as the player joined, and mc-runtime-test waits for no screen before it finishes ("Screen not yet null"). As on Fabric, the screen waits for a player's choice. CORPUS-64's crash had hidden it. | Not Fabricated's. Open for CI's harness: the run should not wait on a mod's screen asking the player to choose (a corpus check that the world was reached, or Origins' choice made for the test), so the run says what loads. |
| HUD-9 | P2 on NeoForge 21.1 with Apoli (its air bubble sprites) and Puzzles Lib (its air supply value), on every NeoForge version with GlitchCore (UNVERIFIED for the 26.x methods) | `ReplacedMethods` (STRUCT-1), NeoForge's split HUD methods (HUD-8) | A hook at a call or a read NeoForge's own layer method makes in vanilla's place, with vanilla's code around it (vanilla's `renderPlayerHealth` air bubbles are NeoForge 21.1's `renderAirLevel`, read in 21.1.265's `Gui`), is left out as one of HUD-8's, where it might move to that method as STRUCT-1 moves one into a method the platform replaced: the call's arguments are the same, and the layer runs where vanilla's code would. Not judged: an alignment of a part of vanilla's method with NeoForge's method, and GlitchCore's hooks there keep state with a start hook of vanilla's whole method. | Open: a split method's part, with a proof that the layer's code is vanilla's part and runs once per frame where vanilla's did |
| LAMBDA-15 | P2, UNVERIFIED | JEI's `KeyBindsListEntryMixin` on NeoForge 26.2 and 26.3 | NeoForge's `KeyBindsList` entry's reset button lambda keeps vanilla's `setKey` call (beside its own `setToDefault`, read in NeoForge's 26.2.x and 26.3.x patches), and JEI's wrapper of it is left out ("does not occur in that method"): the lambda NeoForge's build creates in vanilla's place may take `this`, which LAMBDA-5's alignment then doesn't find. NeoForge 26.2 and 26.3 don't build locally, and their client classes weren't read. | Open: read the lambda in a 26.2 build |
| SCREEN-4 | P3, UNVERIFIED | FancyMenu's `MixinFabricTitleScreen` on NeoForge 1.21.11 to 26.3 and Forge 47 to 66 | Its condition on vanilla's drawing of the title screen's version string (`drawString`, 26.x `GuiGraphicsExtractor.text`) is left out on every platform and version the release ran, with no reason given. On NeoForge 21.1 the platform draws its branding lines through `BrandingControl` in that place, named a known limit (SCREEN-3); the other versions' title screens weren't read. | Open: read each version's `TitleScreen`, and name the limit where the platform draws its branding there too |
| SIG-4 | P3, mod-specific | Xaero's Minimap's `MixinOptionalGuiRenderer` on NeoForge 1.21.11 | Its `@ModifyVariable` of `GuiRenderer.preparePictureInPictureState(PictureInPictureRenderState, int)` is left out: NeoForge gave the method other parameters (the report names them). Xaero's mods are All Rights Reserved: no patch of the mod. | Open: whether SIG-1's bridge for a method given more parameters can take a `@ModifyVariable` |
| KEYMAP-1 | P3 | Fabric mods' accessors to `KeyMapping.MAP` (OneConfig's keybind registry) | NeoForge retyped the field to `KeyMappingLookup`, so a `Map` accessor is left out and its callers throw `NoSuchMethodError`. OneConfig catches it (warning that its keybinds may shadow Minecraft's) and leaves the Controls screen mirrors of its keybinds registered with Minecraft's keybinds, which on Fabric it takes out again as it makes each one (`MinecraftKeybindBridgeImpl.detached`, read in its source and in its 1.21.1 jar in extended run 41496005222). They don't shadow Minecraft's there: the mirrors have OneConfig's own names, and NeoForge's lookup keeps every binding of a key, where vanilla's map keeps the last. P3 since then (was P2). | Open, documented in `COMPATIBILITY.md`: a `Map` view of NeoForge's lookup, served for such an accessor, would take the mirrors out as on Fabric; built if a mod needs it for more than that. |
| FC-7 | P3, UNVERIFIED on Windows | cache cleanup (`TranslationCache`) | `Files.walk` walks a Windows directory junction as a directory, so cleaning a tree holding one would delete what it leads to. | Fixed, unverified: cleanup deletes a link itself (a directory whose real path isn't its parent's with its name, or a reparse point), never its target, and the cache refuses a junction among its own directories as it refuses a symbolic link. The symbolic link case is tested (`TranslationCacheTest`); junctions exist only on Windows, where neither CI nor the tests run, so that case is untested. It moves to `FINDINGS.md` once checked on Windows. |
| CACHE-5 | P3 | `FabricLoaderApiCopies.store` (NeoForge on FML 10 and later: 1.21.11, 26.x) | Each distinct copy of a jar left without Fabric Loader's API (Forgified Fabric API's nested Forgified Fabric Loader; up to 64 MiB) is written to `.fabricated/loader-api/<sha-256>/` and never deleted, so each update of such a jar leaves the old copy behind. It is written outside `TranslationCache`, without its checks for links in the path. | Open: keep the copies in the cache under their file name (their module's name comes from it), cleaned as the cache's other items; the reader runs before the pipeline, so its use must reach the pipeline's cache, as for CACHE-4. |
| CORPUS-5 | P3, cosmetic, UNVERIFIED | Traveler's Backpack (26.3 corpus) | NeoForge warns "Missing FluidModel" for its potion fluids. NeoForge's check runs inside `FluidStateModelSet.bake`, and upstream's `FluidStateModelSetMixin` wraps that method to add the models of Fabric-registered fluids after it returns, so the warning comes before those models are added. Whether the potion fluids have a model at all is UNVERIFIED. | Could register Fabric's fluid models through NeoForge's `RegisterFluidModelsEvent` instead. Low value. |
| CORPUS-17 | P3, UNVERIFIED | REI's Fabric build (26.2, 26.3 corpus) | REI logs "Failed to fill display for recipe" for Farmer's Delight's cooking pot recipes (2 to 4 times) and, on the 26.3 dedicated server, for 560 brewing recipes (run 379). REI logs them without a stack (run 381's digest found none to print). | Open: needs REI's own log at debug level, or its source. |
| CORPUS-25 | P3 | Terralith (26.3 server corpus, every run since at least run 420) | About 130 warnings per server run from NeoForge's `ModifiableBiomeInfo`: "Cannot modify spawn settings of biome terralith:alpha_islands, found unexpected attribute modifier OverrideModifier[]" (the first of them; runs 40241680133 and 40244234167). NeoForge 26.3 refuses to modify the spawns of a biome whose `natural_mob_spawns` attribute uses a modifier other than `overlay` (`ModifiableBiomeInfo`, read in its source), so biome modifiers can't add spawns to those biomes. | Open, likely Terralith's: the count fits every Terralith biome declaring an `override` modifier, which would warn on NeoForge without Fabricated too; UNVERIFIED, as Terralith's 26.x data isn't in its public repository. |

Open with no ID of their own, each left by a fixed finding:

- **What SEAM-12 leaves: a cactus's, a mushroom's and a stem's fruit's soil checks** on NeoForge 1.21.1 and 1.21.11
  (the stem's fruit) and Forge 47 to 61 (the cactus on 47, 52 and 61, the mushroom on 52 and 61, the stem's fruit on
  61). Both platforms ask the soil's `canSustainPlant` there, whose default answers for every kind of plant at once
  (Forge's asks the plant's type and checks a cactus's soil as vanilla does only for vanilla's cactus; NeoForge's
  answers in three ways), so a put-back isn't provably vanilla's for every plant and soil keeping the defaults: Farmer's
  Delight Refabricated's rich soil doesn't take cactus, mushrooms or stems' fruit through its hooks. The report names
  them a known limit; `COMPATIBILITY.md` lists it on NeoForge and Forge. On Forge vanilla's farmland check in a crop's
  growth speed goes back for `CropBlock`s keeping their checks alone: a stem's and the pitcher crop's growth ask the same
  method as Forge has it, and a hook of that check, which applies, doesn't run for them (the report's note of it names
  them). Next: read Forge's plant types and `mayPlaceOn` of `StemBlock` and `PitcherCropBlock` (47 to 66) and, where
  their checks are vanilla's, put the check back for them too.
- **What SEAM-9 leaves of vanilla's fluid checks** on NeoForge 21.1 to 26.3 and Forge 47 to 66: the platforms ask
  their fluid types where vanilla asks for water or lava (NeoForge 26.x whether a type is water-like, which a mod's
  fluid may be) and push an entity through every fluid type at once in code of their own, so Lithium's fluid pushing
  caches, Wilder Wild's mesoglea, FrozenLib's water-like fluids, Expandability's swimming events and Milk Lib's effect
  clearing (Forge 47) have no call that is vanilla's for a mod's fluid, and the pushing none at all. On Forge 64 to 66
  Fabric API's content registries' fluid hooks (a living entity's breathing, swimming and fluid jumping, a boat's
  floating) are left out too: Forge keeps vanilla's breathing in `baseTick` in a branch that never runs and breathes
  through `ForgeHooks.onLivingBreathe` and its events, which refill no air in lava, where vanilla does. The report names
  them a known limit; Additional Entity Attributes' water speed stays FLUID-5. Next, for Fabric API's custom fluids on
  Forge 64 to 66: a bridge of their drowning and swimming into Forge's breathing event and fluid types.
- **What STRUCT-2 doesn't move** into a private method the platform replaced at each of its calls: a hook at vanilla's
  calls of the method; the hooks inside it where another injector may be into the method without pre-flight telling (a
  selector by pattern, by a name vanilla has several methods of, or by `@Desc`) or one is a `@Group`'s alternative; a
  handler sharing under a `@Share` namespace; and one whose callback info parameter carries an annotation. The report's
  hint says what moves. On Forge 47's corpus servers the hooks still out at STRUCT-1's methods are Lithium's, Fabric
  API's transfer hook and Porting Lib's, each with its reason in the report.

- **What PLAIN-2 leaves under official names on Forge 43** (MCPConfig for 1.19.2 renames the members of the classes
  the official jar keeps unobfuscated, `com/mojang/blaze3d`'s): an `@At` target naming such a method without its
  owner, a selector with a quantifier (`name*`, `name{2}`), a string's field reference naming a class that inherits
  the field, an access widener's line naming such a member, and Fabric Loader's mapping resolver asked for one keep
  the official name, so the hook, the widening or the lookup finds nothing. No mod seen does any of them; the report
  names a hook left out so. On every platform, a nested class of a class Intermediary keeps by name right after a
  primitive in a descriptor (`(IL…GlStateManager$class_1017;)V`) keeps its intermediary name (TOKEN-1's token starts
  at the primitive's letter).

- **What PROVIDER-23 leaves of Fabric API's internals on NeoForge 1.21.11 to 26.3.** A hidden pack's enabled state
  doesn't follow its parents, as upstream's predicate says: NeoForge has no such link, so a required hidden pack is
  always on and another is off once its world or the game loads again. Traveler's Backpack's mixin of upstream's
  `ClientNetworking.openScreen`, which keeps its menus closed while ReplayMod replays a recording, is left out: the
  native menu module opens menus through NeoForge's packet. Polymer's `@Pseudo` mixins of Fabric's registry sync
  (`RegistrySyncManager`, `StateIdTracker`) have nothing to apply to, as NeoForge syncs the registries; Mixin skips them
  without an error, and the report names each in an INFO line (PRE-37).

## Compatibility baseline

Where the bug-hunting pass starts from. The last extended runs made by hand are those of 2026-11-10: 42768568693 on
`main`, 42768572948 on `forge` and 42768577406 on `forge-legacy`, on the code through MODIFIER-2. Every gate held. The
corpus runs are informational (`ci/runs.tsv`): 38 of the 44 reached their world. The v0.6.0-beta.2 release ran the
extended matrix after them (42805036593), and the fixes since are compared with that run's reports. What stopped a run
then and is fixed is in [`FINDINGS.md`](FINDINGS.md), some of it checked on local servers and clients only
(*Unverified*, below); the v0.6.0-beta.3 release's extended runs are the next baseline.

What the corpus runs still show, each with its reason:

| Where | What | Status |
|---|---|---|
| Forge 52, corpus | Moonlight's registry `moonlight:custom_map_data_types` is empty at the built-in registries' check Fabric API moves to the start, which REG-6 passes over only for Forge's own registries | Open, P3 (a logged error) |
| Forge 47, client corpus | The client's block entities don't unload as it leaves its world (the test mod's check fails) | Open: the job logs keep only the summary; reproduce locally |
| NeoForge 1.21.1, Forge 52 and Forge 47, advanced client | The client reaches its world and waits on Origins' `ChooseOriginScreen` until the run's time limit | The mod's screen, as on Fabric (CORPUS-66, open for CI's harness) |
| Forge 47, advanced client | In one of two local runs the client lost its connection as it joined: a custom payload reaching the play handler before Fabric API's networking addon is set | Not Fabricated's (NET-14): a race of Polymer's and Fabric API's |
| NeoForge 1.21.1, 26.1.2 and 26.3 and Forge 52, 64 and 65 (the v0.6.0-beta.3 release's run), client corpus | The client doesn't exit within the harness's limit: Xaero's World Map's world unload handler deadlocks with the integrated server's stop | The mod's (CORPUS-48); the harness stops the client and the run's other results stand |
| NeoForge 1.21.1 and Forge 52, advanced server | `/reload` fails in Wover's biome tag handler ("`context` is null"), as on Fabric: the data stays as it was | Not Fabricated's (CORPUS-113); the check that a listener runs again on `/reload` is skipped there (`ci/checks.tsv`) |
| Forge, advanced server (seen on Forge 64 and 66) | Two of the test mod's ingredient network checks fail: Polymer's ingredient codec needs the packet context Fabric API binds only while a packet is coded, and the checks write outside a packet | Not Fabricated's (CORPUS-114): they fail so on Fabric too |
| Forge 66, advanced server | Wover Story's main entrypoint throws registering a `PoiType` "twice": its two point-of-interest types are equal records, which Forge's registry, keyed by equality, takes for one. The run disables the mod, as its setting asks | Known limit (REG-13), which the failure says |
| Forge 65 and 66, advanced client | `@WrapOperation`s of `Screen.extractRenderStateWithTooltipAndSubtitles` (Architectury, FancyMenu, SpruceUI) left out: Forge's call, `drawScreen(graphics, x, y, delta)` of its layer code, doesn't take the screen the wrappers take as the call's receiver | Known limit (SCREEN-2), which the report says; `@Inject`s there move (HOOK-3), and on Forge 61 and 64 Architectury's and FancyMenu's wrappers apply at Forge's static hook (HOOK-2) |
| NeoForge 1.21.11, advanced corpus | Biomes O' Plenty's Fabric build disabled by pre-flight: its `Entity.updateFluidOnEyes` redirect doesn't occur in NeoForge's method | Not repaired (CORPUS-22): NeoForge replaced the fluid tags on the eyes with its fluid types; its NeoForge build is the way |
| Forge 47, client corpus | Porting Lib's GUI utilities disabled by pre-flight: a `@ModifyArgs` call in `GuiGraphics` Forge changed | Not repaired (CORPUS-93): Forge gathers the tooltip components itself, which the module ports; Create's Forge build is the way |
| Forge 47, corpus | ModernFix names Fabric Loader's error GUI (`net.fabricmc.loader.impl.gui`), which Fabricated doesn't provide | No change: only lambdas `ModernFixPreLaunchFabric.onPreLaunch` never calls name it (read in 5.25.2+mc1.20.1) |

## Not in this pass

Candidates outside the steps above, each waiting for a mod or a report that needs it:

- One check of whether the platform's game has a library, by package, read off the game's classes, in place of the
  descriptor's list of the provided libraries its game lacks (`absentLibraryPackages`, PLAT-8) and of the families it
  ships in part (`PlatformLibraries.PARTIAL_FAMILIES`, PLAT-2).
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
- Block breaking, what SEAM-3 leaves: vanilla's removal calls in `destroyBlock` are put back for the blocks keeping the
  platform's `onDestroyedByPlayer`; a block overriding it breaks as the platform has it, without Fabric mods' hooks of
  vanilla's calls. On Forge, wrappers of the server's `playerWillDestroy` call stay left out (a known limit: Forge makes
  it after the tool is damaged; `@Inject`s at it move to vanilla's place, CORPUS-72). The client rows are checked by
  unit tests only, not in game.
- Harvest checks, what SEAM-4 leaves: vanilla's `hasCorrectToolForDrops` is put back in the server's `destroyBlock` and
  in `getDestroyProgress`; a block overriding `canHarvestBlock` (and on Forge a player whose class overrides
  `hasCorrectToolForDrops`) is checked as the platform has it, without Fabric mods' hooks. Hooks of the held item's
  `canAttackBlock`/`canDestroyBlock` in `destroyBlock` stay left out (a known limit: both platforms ask it inside their
  block break event, which a listener may overrule; Porting Lib's break event hook), as do hooks of vanilla's call in
  NeoForge's `DoorBlock.playerWillDestroy`, which asks NeoForge's own overload (no Fabric mod in the corpora hooks it;
  a row of `VanillaCalls` with the player's check could put it back).
- Returns a patch merges (VANILLA-5 leaves them): a hook picking vanilla's return by ordinal where the platform's
  method returns there and on another path at once, as NeoForge 21.1's and Forge 52's `MushroomBlock.canSurvive` does
  (BetterNether's mushroom hook, at vanilla's second return), still finds no return there, as pre-flight warns. Putting
  it at the shared return behind a check of the path taken would keep it to vanilla's path. Apoli 2.9.2's break
  action, at vanilla's survival return on Forge 47, is skipped by Mixin there for its captured locals, which Forge's
  `destroyBlock` declares otherwise.
- Item checks, what SEAM-6 leaves: vanilla's `stack.is(...)` of shears, a rod and a shield is put back where the
  platform asks the stack's `canPerformAction` for the stacks keeping its default, and the elytra layer's for the layers
  keeping `shouldRender`; an item overriding `canPerformAction` (vanilla's tools among them) is asked as the platform
  has it, without Fabric mods' hooks. NeoForge and Forge 47 to 64 shear mobs from the shears item through their
  shearable extension, which posts their events, where vanilla's `mobInteract` checks for shears: hooks of that check
  stay left out (a known limit: BCLib's sheep and snow golem hooks), and BCLib's mooshroom hook wraps the bowl check
  alone where the shears check is gone, with no report line. The held item and item frame renderers ask whether the
  item is a `MapItem` or a `CrossbowItem`, which other mods' items are too: hooks of vanilla's check there stay left out
  (a known limit: Apoli's held item transforms, Porting Lib's maps in item frames). The client rows (the fishing line's
  hand, the elytra layer) are checked by unit tests only, not in game.
- Extensions' calls, what SEAM-8 leaves: vanilla's friction, sound type, piglin, sweep and enchantment level calls are
  put back where the platform asks an extension of the state or the stack, for the objects keeping its defaults; one
  overriding the extension (a native mod's block or item) is asked as the platform has it, without Fabric mods' hooks.
  `Leashable.angularFriction` (1.21.11 on) is an interface's method, which can't keep the answers of a block's class:
  hooks of vanilla's friction there stay left out (a known limit); a helper class holding the answers could take them.
  From 1.21.11 NeoForge's `attack` makes the sweep's box for an overload of its own and doesn't call vanilla's
  `doSweepAttack`, whose hooks apply and never run: pre-flight notes them, a known limit the report names; putting the
  call back would need `attack` to call vanilla's `doSweepAttack` for the stacks keeping NeoForge's default. The
  platforms' own methods making these calls
  (Forge 47's enchantment loop, the entity's combination and muffled step sounds and the block item's place sound,
  which take positions, Forge 66's `ClientLevel.playBreakingSound`) and Forge 52 on's camel step check, which asks the
  sound type where vanilla asks a tag, aren't vanilla's calls and aren't put back; no Fabric mod in the corpora hooks
  vanilla's there. The client rows (the hit and break sounds) are checked by unit tests only.
- The seam conflict check, a follow-up: a native mixin's `method` list naming a method the class doesn't have counts as
  what can't be told, whatever else the list names, and keeps the class as the platform has it. Beside Forgified Fabric
  API (NeoForge 21.1, Forge 47) `LivingEntity` so keeps the platform's calls, and Fabric mods' friction and fall sound
  hooks there find nothing: its entity events mixin lists `lambda$stopSleeping$9` (Forge 47: `m_260785_`) beside a
  method the class has. Judging such a list by the names the class has would put it back.
- A locals capture Mixin skips at each of its points is left out with the injectors keeping state with it (SEAM-2);
  rewriting it into MixinExtras `@Local`s, each the local vanilla's order has there, would keep Carpet's chains and
  chests sticking along a push on NeoForge 21.1 to 26.1.2 and Forge 47. It needs the platform's locals matched to
  vanilla's (`LocalRepairs.isVanillasLocal`) and, on Forge 47, the locals 0.8.5 keeps past their scope (PRE-26).
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
- STRUCT-1 with real mods: YUNG's and Friends and Foes' processors on a structure that carries their entities (the
  hooks apply, and the test mod's own run, a gate on every loader and version: `structure-entity-hooks`); Carpet's
  hopper counters and FrozenLib's ejection guard on Forge 47's `ejectItems` in play (they apply; no check moves items
  through a hopper), and Carpet's explosion event after Forge 61 to 66's `hurtEntities` call (read in its jar, not
  run).
- INTERFACE-2 and ITEM-2 on the Forge 47 and 52 client corpora (the server corpora checked locally), and chained
  handlers on one call in a corpus mod (core's tests cover them; no corpus mod has one).
- HUD-6 and HUD-7 with Apoli's own bars in a drawing of the HUD (no origin is chosen in the Forge 52 and NeoForge 1.21.1
  advanced clients' worlds; the test mods' layer of the same shape is checked there); MODIFIER-3 on Forge 61, 65 and 66
  (no corpus jar carries Forge modifiers there), and ACC-6's call from another mod on Forge 61 to 66 (a local Forge 66
  advanced server starts with the accessors adapted); REG-14 with a changed value on Forge 47 and 61 to 66 (their
  server parts pass with it).

## CI times

Wall time of a pull request's full matrix, from the pull requests of 2026-11-12 to 2026-11-16 (their runs' start to
end; `main`'s 43311682436 to 43906671666, `forge`'s 43313486666 and 43341842127, `forge-legacy`'s 43313492243 and
43341845804):

| Run | Wall time | Jobs |
|---|---|---|
| `main` pull request, NeoForge's five versions | 6 to 8 min | 23 |
| `forge` pull request, four versions | 5 min | 19 |
| `forge-legacy` pull request, 1.20.1 and 1.21.1 | 6 to 9 min | 11 |

The two Forge rows were measured with 1.21.1 on `forge-legacy`; with it on `forge`, neither branch's matrix is measured
yet.

The fast check on a push (the unit tests and one version booted) took 2.5 minutes and 3 job-minutes in `ci.yml`'s first
runs; it and the matrix's job-minutes aren't measured since.

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
too. Every baseline is recorded from CI's runs, as its row says. Forge's runs launch once, so its cached allocation is
only reported.
