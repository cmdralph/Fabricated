# Mixin inventory

Every mixin Fabricated ships, by config: target, injector, injection point, `require` and the versions that compile
it. Snapshot taken 2026-10-19 by scanning the sources (see `roadmap.md`), then updated by hand for that session's
changes; the list of configs, each config's entries, the per-config tables and the counts below were checked against
the configs, and each row's source folder against the source tree, on 2026-10-28. A commit that adds, removes or
retargets a mixin updates its row here; `scripts/check-mixin-inventory.py` fails when a config lists a mixin that its
table here leaves out. Source line numbers are left out on purpose; open the class. The Forge branches' configs are
under *Forge configs*, at the end.

Path shorthands: `N/` = `io/github/cmdralph/fabricated/neoforge/`, `n.m.` = `net.minecraft.`, `neo.` =
`net.neoforged.neoforge.`, `fapi.` = `net.fabricmc.fabric.`.

## Configs

Fabricated-owned (shipped in `game-mod.jar`, registered in `mc/neoforge/common/src/main/resources/META-INF/neoforge.mods.toml`;
a version's own configs come in through `version_mixin_configs` in its `gradle.properties`: every version's
`fabricated-biome.mixins.json`, and on 1.21.1 also `fabricated-entity-events`, `fabricated-object-builder` and
`fabricated-particles`). Each version takes a config from its own folder, else from the shared folder its
`shared_sources` lists that has it:

| Config file | Package | Versions | Sections | defaultRequire | overwrites.requireAnnotations | plugin |
|---|---|---|---|---|---|---|
| `mc/neoforge/1.21.1/src/main/resources/fabricated.mixins.json` | `io.github.cmdralph.fabricated.neoforge.mixin` | 1.21.1 | mixins 56, client 20 | 1 | true | `FabricatedMixinPlugin` (creates Fabric language adapters in `onLoad`; filters nothing) |
| `mc/neoforge/1.21.11/src/main/resources/fabricated.mixins.json` | same | 1.21.11 | mixins 65, client 24 | 1 | true | same |
| `mc/neoforge/common26/src/main/resources/fabricated.mixins.json` | same | 26.x | mixins 76, client 28 | 1 | true | same |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-biome.mixins.json` | `net.fabricmc.fabric.mixin.biome` | 1.21.1 | mixins 8 | 1 | true | none |
| `mc/neoforge/1.21.11/src/main/resources/fabricated-biome.mixins.json` | same | 1.21.11 | mixins 10 | 1 | true | none |
| `mc/neoforge/common26early/src/main/resources/fabricated-biome.mixins.json` | same | 26.1.2, 26.2 | mixins 10 | 1 | true | none |
| `mc/neoforge/26.3/src/main/resources/fabricated-biome.mixins.json` | same | 26.3 | mixins 6 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-content-registries.mixins.json` | `net.fabricmc.fabric.mixin.content.registry` | 1.21.1 | mixins 14 | 1 | true | none |
| `mc/neoforge/1.21.11/src/main/resources/fabricated-content-registries.mixins.json` | same | 1.21.11 | mixins 19 | 1 | true | none |
| `mc/neoforge/26.1.2/src/main/resources/fabricated-content-registries.mixins.json` | same | 26.1.2 | mixins 27 (`mixinextras.minVersion 0.5.0`) | 1 | true | none |
| `mc/neoforge/26.2/src/main/resources/fabricated-content-registries.mixins.json` | same | 26.2 | mixins 26 (`mixinextras.minVersion 0.5.0`) | 1 | true | none |
| `mc/neoforge/26.3/src/main/resources/fabricated-content-registries.mixins.json` | same | 26.3 | mixins 18 (`mixinextras.minVersion 0.5.0`) | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-entity-events.mixins.json` | `net.fabricmc.fabric.mixin.entity.event` | 1.21.1 | mixins 5 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-fabric-api.mixins.json` | `net.fabricmc.fabric.mixin.networking.accessor` | 1.21.1 | mixins 4 | 1 | true | none |
| `mc/neoforge/common12111/src/main/resources/fabricated-fabric-api.mixins.json` | same | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 4 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-lookup.mixins.json` | `net.fabricmc.fabric.mixin.lookup` | 1.21.1 | mixins 2 | 1 | true | none |
| `mc/neoforge/common12111/src/main/resources/fabricated-lookup.mixins.json` | same | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 2 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-loot.mixins.json` | `net.fabricmc.fabric.mixin.loot` | 1.21.1 | mixins 4 | 1 | true | none |
| `mc/neoforge/common12111/src/main/resources/fabricated-loot.mixins.json` | same | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 5 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-object-builder.mixins.json` | `net.fabricmc.fabric.mixin.object.builder` | 1.21.1 | mixins 2, client 2 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-particles.mixins.json` | `net.fabricmc.fabric.mixin.client.particle` | 1.21.1 | client 4 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-renderer.mixins.json` | `io.github.cmdralph.fabricated.neoforge.rendering.mixin` | 1.21.1, only with `fabric_renderer_api_v1` (FML `requiredMods`) | client 8 | 1 | true | none |
| `mc/neoforge/1.21.11/src/main/resources/fabricated-renderer.mixins.json` | same | 1.21.11, only with `fabric_renderer_api_v1` (FML `requiredMods`) | client 5 | 1 | true | none |
| `mc/neoforge/common26/src/main/resources/fabricated-renderer.mixins.json` | same | 26.x, only with `fabric_renderer_api_v1` (FML `requiredMods`) | client 4 | 1 | true | none |
| `mc/neoforge/1.21.1/src/main/resources/fabricated-transfer.mixins.json` | `net.fabricmc.fabric.mixin.transfer` | 1.21.1 | mixins 14 | 1 | true | none |
| `mc/neoforge/1.21.11/src/main/resources/fabricated-transfer.mixins.json` | same | 1.21.11 | mixins 16 | 1 | true | none |
| `mc/neoforge/common26/src/main/resources/fabricated-transfer.mixins.json` | same | 26.x | mixins 17 | 1 | true | none |

All have `"required": true`, `compatibilityLevel JAVA_21`, no `minVersion`, no config-level `priority`, no `refmap`, no `server` section.
Mixin classes per version (entries of that version's configs, counted 2026-10-28): 1.21.1 = 122, 1.21.11 = 138, 26.1.2 = 161, 26.2 = 160, 26.3 = 148. The orphan check
(every listed class resolves to exactly one file for each version, and no `@Mixin` class is compiled into a version
without being listed) was last run on 2026-10-28: every listed class resolves to one file, and 1.21.1 compiles one
`mc/neoforge/common` mixin that none of its configs lists, `content.registry.WorkAtComposterAccessor` (it lists its own
`FarmerWorkTaskAccessor`, which its Fabric API copy uses). It also listed its own `ServerLoginNetworkHandlerAccessor`
while common's `ServerLoginNetworkAddon` casts to `ServerLoginPacketListenerImplAccessor`, which it didn't apply: a
Fabric mod's login networking would have failed there with a `ClassCastException` (MIX-2, fixed). The per-config
tables below cover every config.

Test-mod configs (not shipped):

| Config | Package | defaultRequire | Mixins |
|---|---|---|---|
| `mc/shared/1.20.1+/src/testmod/resources/fabricated-testmod.mixins.json` | `io.github.cmdralph.fabricated.testmod.mixin` | 1 | Plugin `TestMixinPlugin`. `BlockRemovalMixin` (`@WrapOperation` of vanilla's `removeBlock` call in the server's `destroyBlock`, which the platforms make through the block state's `onDestroyedByPlayer` and Fabricated puts back for the blocks keeping its default, SEAM-3), `BuiltInRegistriesMixin` (builds content from vanilla's registry bootstrap, CORPUS-31, and sees the built-in registries frozen, CORPUS-32), `ChiseledBookShelfBlockMixin` (`@Inject` of the chiseled bookshelf's property lambda, named by vanilla's descriptor and taking the `Property` vanilla's takes, which NeoForge's recompiled game, 21.11 and later, takes as a `BooleanProperty`: through pre-flight's bridge there, LAMBDA-11), `DestroyProgressMixin` (`@WrapOperation` of vanilla's `hasCorrectToolForDrops` call in `BlockBehaviour.getDestroyProgress`, which the platforms make through a hook of theirs and Fabricated puts back, SEAM-4), `FrictionMixin` (`@WrapOperation` of vanilla's `getFriction` call in an item's movement, which the platforms make through the block state's `getFriction(level, pos, entity)` and Fabricated puts back for the blocks keeping its default, SEAM-8), `HarvestCheckMixin` (`@WrapOperation` of vanilla's `hasCorrectToolForDrops` call in the server's `destroyBlock`, which the platforms make through the block state's `canHarvestBlock` and Fabricated puts back for the blocks keeping its default, SEAM-4), `BlockDropsMixin` (`@Inject popResource HEAD`: records what a block is asked to drop while the harvest check runs, whose chunk's item entities may not show yet), `LivingEntityMixin` (a private `self()` helper, as Fabric API's entity events module has, HIDE-1), `LivingEntitySelfMixin` (adds an interface whose default `self()` is a second one beside NeoForge's, for the default bridge, CORPUS-63), `MappedRegistryMixin` (sees a registry frozen, as Flashback does, REG-8), `MinecraftServerMixin` (`@Inject tickServer TAIL`: mixins apply), `MobMultipartMixin` (adds an interface whose default `isMultipartEntity()` is a second one beside the one `Mob` has through `Entity`, for the default bridge, DEFAULT-1), `PackRepositoryMixin` (leaves a pack repository's sources immutable, as FrozenLib does, PACK-1), `PlayerListMixin` (replaces the server's player list, POLY-1), `PluginRejectedMixin` and `PreflightLeftOutMixin` (a `@Shadow` of a field no game has, left out whole by pre-flight: the plugin turns down the first, and would apply the second, which the plugin as translated turns down, LEAVE-4), `PreflightShapesMixin` (`@Inject tickServer HEAD` handler static on a method that takes `this`, LAMBDA-12), `RecordedFramesMixin` (takes a local after a frame that chops one, where Mixin may count too few locals: CORPUS-79, FRAME-1), `RegistryDataLoaderMixin` (writes to built-in registries from that class's initializer, as Wover does, REG-3), `ShearsCheckMixin` (`@WrapOperation` of vanilla's `ItemStack.is(Items.SHEARS)` call in the tripwire's `playerWillDestroy`, which the platforms make through the stack's `canPerformAction` and Fabricated puts back for the stacks whose item keeps its default, SEAM-6; one per version group, `1.20.1-1.21.11` with `is(Item)` and `26.x` with `is(Object)`), `StructureEntitiesMixin` (hooks at vanilla's private `StructureTemplate.placeEntities`, its call and its start, which the platforms replaced, STRUCT-1; one per version group, `1.20.1-1.21.1` and `1.21.11+`), `Vec3iInstanceofMixin` (`@ModifyConstant` of the `instanceof` in `Vec3i.equals`, taking the object and the class, PRE-29); server: `LoaderHookMixin` (after Fabric Loader's `Hooks.startServer`, as owo-lib's freeze, HOOK-4); client: `client.MinecraftStopMixin` (CI diagnostics: logs who asked the game to stop), `client.MinecraftEarlyReloadListenerMixin` (registers a reload listener from `Minecraft`'s constructor, before NeoForge's event), `client.McRuntimeTestTickHookMixin` (`@Pseudo`, CI only: holds mc-runtime-test's tick hook until the integrated server has run as many ticks in the world as the client, CI-15). All in `mc/shared/1.20.1+/src/testmod/java/.../mixin/` |
| `mc/shared/1.20.1+/src/testmod/resources/fabricated-testmod-mixinsemantics.mixins.json` | `io.github.cmdralph.fabricated.testmod.mixinsemantics` | 1 | Mixins applied as Fabric's Mixin applies them: `MinecraftServerCounterMixin` and `MinecraftServerCounterShadowMixin` (a `@Shadow` of a field another mixin adds, CORPUS-74), `MinecraftServerUniqueMixin` and `MinecraftServerUniqueTwinMixin` (two `@Unique` fields of one name stay a field each, CORPUS-95). All in `mc/shared/1.20.1+/src/testmod/java/.../mixinsemantics/` |
| `mc/shared/1.20.1+/src/testmod/resources/fabricated-testmod-vanilla.mixins.json` | `io.github.cmdralph.fabricated.testmod.vanilla` | 1 | `PistonStickyStartMixin` and `PistonStickinessMixin` (hooks of vanilla's `PistonStructureResolver.isSticky` and `canStickToEachOther`, which the platforms took out and Fabricated puts back, SEAM-1; compiled in `testmodvanilla` against a stand-in naming them, `mc/shared/1.20.1+/src/testmodvanilla/java/.../vanilla/`) |
| `mc/shared/26.x/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json` | `io.github.cmdralph.fabricated.testmod.frapi.mixin` | 1 | `BakedQuadMixin` (`@Inject <init> RETURN` with vanilla's parameters, `mc/shared/26.x/src/testmodfrapi/java/.../mixin/BakedQuadMixin.java`). Exercises pre-flight's constructor repair and handler bridge (CI's renderer API gate) |
| `mc/neoforge/1.21.1/src/testmod/resources/fabricated-testmod-neoforge.mixins.json` | `io.github.cmdralph.fabricated.testmod.neoforge` | 1 | NeoForge 1.21.1's half of the test mod (listed in its descriptor by `mc/platform-neoforge.gradle`): `HudLayersMixin` (a layer put into vanilla's first HUD stack at `Gui`'s constructor's read of `layers`, as Apoli does, and one replacing the second stack's demo overlay, which pre-flight points at Fabricated's seam; checks each is drawn as a layer of NeoForge's where vanilla draws it, the replaced layer not, and neither with the GUI hidden, HUD-7) and `LayeredDrawLayersAccessor`; common: `ConditionRereadMixin` (reads a JSON data loader's files' Fabric conditions again its own way, as Moonlight's NeoForge build does, dropping one whose `fabric:all_mods_loaded` names a mod NeoForge's mod list hasn't, while the module is Fabric API's own; the json loader conditions check then passes only where Fabricated decided them first, COND-1) and `ConditionsAsPreparedMixin` (priority 100: sees the test mod's conditioned files as prepared, and checks as they are applied that the one whose conditions upstream can't parse keeps them and the prepared objects are unchanged, COND-1) |
| `mc/shared/src/testmodbroken/resources/badmixin/fabricated-testmod-badmixin.mixins.json` | `io.github.cmdralph.fabricated.testmod.broken.mixin` | unset | `ImpossibleMixin` (`@Inject method="fabricatedMethodThatDoesNotExist" HEAD require=1`, `.../ImpossibleMixin.java`) and `UnmatchedCallMixin` (`@Inject method="tickServer"` at a call it never makes, `require=1`). Meant to fail pre-flight, which leaves their injectors out as the mixins are self-contained (`ci/neoforge/smoke-server.sh`); with pre-flight ignored, Mixin fails on each as it applies it, and the next launch leaves it out (`ci/neoforge/smoke-apply-failure.sh`, APPLY-1) |
| `mc/shared/src/testmodbroken/resources/badplugin/fabricated-testmod-badplugin.mixins.json` | `io.github.cmdralph.fabricated.testmod.broken.plugin` | unset | Plugin `FailingPlugin`, whose constructor throws. `LeftOutMixin` (a `@Shadow` of a field no game has, left out whole by pre-flight and kept listed for the plugin's answers: Mixin, the plugin failing, applies it all the same, which as translated, an empty interface, changes nothing; LEAVE-4). All in `mc/shared/src/testmodbroken/java/.../plugin/` |

## Fragility review

- **Every config** is `required`, with `defaultRequire: 1` and `overwrites.requireAnnotations: true` (the last one was
  missing from both `fabricated.mixins.json` files and `fabricated-fabric-api.mixins.json` until 2026-10-19). A
  missing target, a missing injection point or an accidental overwrite of a method the target declares fails when
  the game applies the mixin, which every CI run does.
- **`require = 0`**, each with a fallback (all in `mc/neoforge/common`, so on every version, but the last):
  `ChunkGeneratorFeaturesMixin` (`refreshFeaturesPerStep` HEAD: only needed while NeoForge declares that method), `client.MinecraftEntrypointsMixin` (client entrypoints before the constructor's first
  `Thread.currentThread()`, with a required `TAIL` fallback), `client.MinecraftStartedMixin` (`CLIENT_STARTED` after
  `run()` sets `gameThread`, else on the first client tick), and 1.21.1's `client.GuiVanillaStacksMixin` (the seam for
  Fabric mods' HUD layer hooks runs as `Gui`'s constructor adds vanilla's first stack to NeoForge's root, with a required
  `RETURN` fallback that runs it there and reports the layers the hooks added, which aren't drawn then, HUD-7).
- **Ordinal-based points:** the two `ordinal = 0` points above, and on 1.21.1 `entity.event.MooshroomEntityMixin`
  (`shear`'s first `addFreshEntity`, upstream's) and `client.MinecraftStoppingMixin` (`destroy`'s first `Logger.info`).
  Ordinal or index captures that are not injection points: `@Local(ordinal = 1) ItemStack` in
  `transfer.CrafterBlockMixin` and `index = 4` in `transfer.BucketItemMixin`, both 1.21.1's and 1.21.11's
  (`mc/neoforge/common121`; 26.x captures by name), and `@Local(argsOnly = true, ordinal = 0)` and `ordinal = 1` in
  1.21.11's `client.ModelManagerSpriteGetterMixin`.
- **`@Redirect`:** `BiomeSourceMixin` (`possibleBiomes`, upstream's) in every version's `fabricated-biome.mixins.json`
  (1.21.1's own copy, the others `mc/neoforge/common12111`'s), and 1.21.1's `transfer.SimpleInventoryMixin`
  (`setItem`'s `setChanged`).
  **`@Overwrite`, `@ModifyConstant`, `@ModifyArgs`:** none. `expect = 0`: none. `allow = 1`:
  `transfer.DropperBlockMixin.hookDispense` (upstream) and 1.21.1's `client.particle.BlockDustParticleMixin`.
- **Anonymous-class target:** `NetherBiomePresetMixin`, in every version's biome config (1.21.1's own copy, the others
  `mc/neoforge/common12111`'s), targets `MultiNoiseBiomeSourceParameterList$Preset$1` by name (upstream's).
- **Synthetic-lambda selectors** (break if NeoForge's recompile renumbers lambdas; caught at startup):
  `HoneycombItemMixin` and `WeatheringCopperMixin` (`lambda$static$0`), `ResourceManagerRegistryLoadTaskMixin`
  (`lambda$load$2`).
- **Wildcard selector:** `EnchantmentBuilderMixin` injects at `RETURN` of every method of `Enchantment$Builder`.
- **Selectors with descriptors where NeoForge added an overload:** `client.SectionCompilerMixin` (26.x and 1.21.11)
  and 1.21.1's renderer `SectionCompilerMixin` name NeoForge's `compile(..., List)`; with the name alone Mixin picks the first `compile`, NeoForge's delegate (this
  crashed CI on 2026-10-19). `client.ModelManagerExtraModelsMixin` names NeoForge's four-argument `discoverModelDependencies`.
- **Hot paths:** `client.SectionCompilerMixin` runs for every block of every chunk section, so it is a
  `@WrapWithCondition` (no allocation), not a `@WrapOperation`, whose `Operation.call(Object...)` boxes primitives.
  `client.MovingBlockRenderMixin` and `client.BlockBreakingRenderMixin` follow the same rule. `client.BlockStateModelWrapperMixin`
  runs for every block drawn as an entity (a minecart's, an enderman's, a block display's), every frame: a cancellable
  `@Inject` at `HEAD`, as upstream's own `submitModel` hook on the same render state is, whose only cost on NeoForge's path
  is the model-class lookup and the callback object. `client.MultiPartModelMixin`
  and `client.WeightedVariantsMixin` answer from a field after the first question, asked again once after a Fabric model
  class is marked broken (FRAPI rule 8). On 1.21.1 the renderer config's `SectionCompilerMixin` and
  `BlockRenderDispatcherMixin.fabricated$batched` run for every block of every section, once per render type: both read
  the model they are given (a local or an argument, no lookup), and `MultiPartBakedModelMixin` and
  `WeightedBakedModelMixin` keep their answer as on the other versions. Its `ForwardingBakedModelMixin` runs for every
  block a Fabric wrapper model draws (Continuity wraps every block's): one thread-local read outside a throw, and
  `Operation.call`'s argument array, which the JIT can drop once the call is inlined; task 6 measures it. The renderer
  configs' `WrapperBlockStateModelMixin` (1.21.11, 26.x) costs the same, and each draw of a Fabric model there two
  thread-local reads.
- **One name, different targets per version:** `client.MovingBlockRenderMixin` (26.1.2: `BlockFeatureRenderer`; 26.2,
  26.3: `MovingBlockFeatureRenderer`, which 26.3 adds forced translucency to) and `client.BlockBreakingRenderMixin`
  (26.1.2: `BlockFeatureRenderer`; 26.2, 26.3: `LevelRenderer`, from `mc/neoforge/common26late`).
  `LevelChunkBlockEntityMixin` touches `getBlockEntity` only at its rare `Map.remove` calls.
- **Client/common placement:** no mixin under `"mixins"` targets a client class; every mixin under `"client"` does,
  except `client.ClientReloadListenerMixin`, whose target `ReloadableResourceManager` is a common class (the client's
  resource manager is one).
- **Placeholder:** `AdvancementReloadMixin` is empty on 26.1.2 and 26.2 (`mc/neoforge/common26early`): the shared 26.x config
  lists it and only 26.3 needs it.
- **Priorities:** `WalkNodeEvaluatorMixin` (1.21.1: `LandPathNodeMakerMixin`) 999 and `LootTableMixin` 3000, both upstream's values;
  `PacketDecoderContextMixin`, `PacketEncoderContextMixin` and `GenericPacketSplitterContextMixin` 500 (26.x), so the
  packet context is bound around `PacketEncoderMeasuredMixin` and `GenericPacketSplitterMeasureMixin` (900, NET-10); and
  `client.GuiRendererPictureInPictureMixin` 100, so it applies before other mods' mixins, which shadow its field.

## Per-config tables

Columns: Source (versions) = the directory the class lives in, plus the versions that compile and list it. `n.m.` = `net.minecraft.`,
`neo.` = `net.neoforged.neoforge.`, `fapi.` = `net.fabricmc.fabric.`. `L…/X;` abbreviates a JVM descriptor's package. "default" in the
last column means the config's `defaultRequire` applies (1 for every Fabricated config). Mixins without injectors list what they merge.


### `mc/neoforge/1.21.1/src/main/resources/fabricated-biome.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.biome` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in `mc/neoforge/1.21.1/gradle.properties`

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BiomeSourceMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSource | @Redirect → getBiomes; adds method fabric_modifyBiomeSet (overridden by TheEndBiomeSourceMixin) | "possibleBiomes" | INVOKE `L…/Supplier;get()L…/Object;` | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setFoliageColorOverride | "foliageColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setGrassColorOverride | "grassColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientParticle | "ambientParticle" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientLoopSoundEvent | "ambientLoopSoundEvent" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientMoodSettings | "ambientMoodSettings" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientAdditionsSettings | "ambientAdditionsSettings" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.1 (1.21.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setBackgroundMusic | "backgroundMusic" |  | — | default |
| mixins | ChunkNoiseSamplerMixin | 1.21.1 (1.21.1) | n.m.world.level.levelgen.NoiseChunk | @Inject → init; @Unique fields seed | "<init>" | TAIL | — | default |
| mixins | ChunkNoiseSamplerMixin | 1.21.1 (1.21.1) | n.m.world.level.levelgen.NoiseChunk | @Inject → createMultiNoiseSampler | "cachedClimateSampler" | RETURN | — | default |
| mixins | MultiNoiseBiomeSourceMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSource | no injectors; implements BiomeSourceAccess; adds methods fabric_setModifyBiomeEntries, fabric_shouldModifyBiomeEntries; @Unique fields modifyBiomeEntries | | | | |
| mixins | MultiNoiseUtilMultiNoiseSamplerMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.Climate$Sampler | no injectors; implements MultiNoiseSamplerHooks; adds methods fabric_setSeed, fabric_getSeed, fabric_getEndBiomesSampler; @Unique fields seed, endBiomesSampler | | | | |
| mixins | NetherBiomePresetMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$1 (`targets`) | @Inject → apply | "apply" | RETURN cancellable=true | — | default |
| mixins | NoiseConfigMixin | 1.21.1 (1.21.1) | n.m.world.level.levelgen.RandomState | @Inject → init; @Shadow sampler | "<init>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → modifyCodec; extends BiomeSourceMixin (overrides fabric_modifyBiomeSet); @Shadow @Mutable CODEC; @Unique fields overrides, biomeSetModified, hasCheckedForModifiedSet | "<clinit>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → rememberLookup | "create" | HEAD | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → clearLookup | "create" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → init | "<init>" | RETURN | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.1 (1.21.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → getWeightedEndBiome | "getNoiseBiome" | RETURN cancellable=true | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AbstractFurnaceBlockEntityMixin | 1.21.1 (1.21.1) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → fuelTimeMapHook | "getFuel" | RETURN | — | default |
| mixins | AxeItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.AxeItem | @Accessor → getStrippedBlocks | "STRIPPABLES" |  | — | default |
| mixins | AxeItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.AxeItem | @Accessor → setStrippedBlocks | "STRIPPABLES" |  | — | default |
| mixins | BrewingRecipeRegistryBuilderMixin | 1.21.1 (1.21.1) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build; implements FabricBrewingRecipeRegistryBuilder; adds methods registerItemRecipe, registerPotionRecipe, registerRecipes, getEnabledFeatures; @Shadow enabledFeatures, containerMixes, potionMixes, expectPotion | "build" | HEAD | — | default |
| mixins | FarmerWorkTaskAccessor | 1.21.1 (1.21.1) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_setCompostables | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | FarmerWorkTaskAccessor | 1.21.1 (1.21.1) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_getCompostable | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | FireBlockMixin | 1.21.1 (1.21.1) | n.m.world.level.block.FireBlock | @Inject → afterConstruct; implements FireBlockHooks; adds methods fabric_getVanillaEntry | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | 1.21.1 (1.21.1) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | 1.21.1 (1.21.1) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftsToHeroTaskAccessor | 1.21.1 (1.21.1) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | HoeItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.HoeItem | @Accessor → getTillingActions | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | LandPathNodeMakerMixin | 1.21.1 (1.21.1) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local | — | default |
| mixins | PathContextMixin | 1.21.1 (1.21.1) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType; @Shadow getBlockState, level | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local | — | default |
| mixins | ShovelItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.ShovelItem | @Accessor → getPathStates | "FLATTENABLES" |  | — | default |
| mixins | VillagerEntityAccessor | 1.21.1 (1.21.1) | n.m.world.entity.npc.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerEntityAccessor | 1.21.1 (1.21.1) | n.m.world.entity.npc.Villager | @Accessor → fabric_setGatherableItems | "WANTED_ITEMS" |  | — | default |
| mixins | VillagerEntityAccessor | 1.21.1 (1.21.1) | n.m.world.entity.npc.Villager | @Accessor → fabric_getGatherableItems | "WANTED_ITEMS" |  | — | default |
| mixins | WeatheringCopperMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | FabricatedComposterBlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.ComposterBlock | @ModifyReturnValue → fabricated$tableChance | "getValue(L…/ItemStack;)F" | RETURN | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-entity-events.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.entity.event` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in `mc/neoforge/1.21.1/gradle.properties`

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | MobEntityMixin | 1.21.1 (1.21.1) | n.m.world.entity.Mob | @ModifyArg → afterEntityConverted | "convertTo" | INVOKE `L…/Level;addFreshEntity(L…/Entity;)Z`  sugar:@Local(argsOnly = true) | — | default |
| mixins | MooshroomEntityMixin | 1.21.1 (1.21.1) | n.m.world.entity.animal.MushroomCow | @ModifyArg → afterMooshroomConversion | "shear" | INVOKE `L…/Level;addFreshEntity(L…/Entity;)Z` | 0 | default |
| mixins | PigEntityMixin | 1.21.1 (1.21.1) | n.m.world.entity.animal.Pig | @ModifyArg → afterPiglinConversion; extends Animal | "thunderHit" | INVOKE `L…/ServerLevel;addFreshEntity(L…/Entity;)Z` | — | default |
| mixins | TadpoleEntityMixin | 1.21.1 (1.21.1) | n.m.world.entity.animal.frog.Tadpole | @ModifyArg → afterGrowingUpToFrog; extends AbstractFish | "ageUp()V" | INVOKE `L…/ServerLevel;addFreshEntityWithPassengers(L…/Entity;)V` | — | default |
| mixins | VillagerEntityMixin | 1.21.1 (1.21.1) | n.m.world.entity.npc.Villager | @ModifyArg → afterWitchConversion; extends AbstractVillager | "thunderHit" | INVOKE `L…/ServerLevel;addFreshEntityWithPassengers(L…/Entity;)V` | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-fabric-api.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.networking.accessor` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | EntityTrackerAccessor | 1.21.1 (1.21.1) | n.m.server.level.ChunkMap$TrackedEntity (`targets`) | @Accessor → getPlayersTracking | "seenBy" |  | — | default |
| mixins | ServerChunkLoadingManagerAccessor | 1.21.1 (1.21.1) | n.m.server.level.ChunkMap | @Accessor → getEntityMap | (from name getEntityMap) |  | — | default |
| mixins | ServerCommonNetworkHandlerAccessor | 1.21.1 (1.21.1) | n.m.server.network.ServerCommonPacketListenerImpl | @Accessor → getServer; extends HasConnection (`getConnection()`, which NeoForge declares) | (from name getServer) |  | — | default |
| mixins | ServerLoginPacketListenerImplAccessor | common (1.21.1) | n.m.server.network.ServerLoginPacketListenerImpl | @Accessor → getServer | (from name getServer) |  | — | default |
| mixins | ServerLoginPacketListenerImplAccessor | common (1.21.1) | n.m.server.network.ServerLoginPacketListenerImpl | @Accessor → getConnection | (from name getConnection) |  | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-lookup.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.lookup` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockEntityTypeAccessor | 1.21.1/fabricapi (1.21.1) | n.m.world.level.block.entity.BlockEntityType | @Accessor → getBlocks | "validBlocks" |  | — | default |
| mixins | ServerWorldMixin | 1.21.1/fabricapi (1.21.1) | n.m.server.level.ServerLevel | no injectors; implements ServerWorldCache; adds methods fabric_registerCache, fabric_invalidateCache; @Unique fields apiLookupCaches, apiLookupAccessesWithoutCleanup | | | | |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-loot.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.loot` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolBuilderMixin | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods apply, conditionally, self, with | | | | |
| mixins | LootTableAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequenceId | "randomSequence" |  | — | default |
| mixins | LootTableBuilderMixin | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-object-builder.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.object.builder` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in `mc/neoforge/1.21.1/gradle.properties`

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AbstractBlockAccessor | 1.21.1 (1.21.1) | n.m.world.level.block.state.BlockBehaviour | @Accessor → getProperties | (from name getProperties) |  | — | default |
| mixins | AbstractBlockSettingsAccessor | 1.21.1 (1.21.1) | n.m.world.level.block.state.BlockBehaviour$Properties | @Accessor (field from the name) getDestroyTime, getExplosionResistance, getHasCollision, getIsRandomlyTicking, getMapColor, getSoundType, getFriction, getSpeedFactor, getJumpFactor, getDynamicShape, getCanOcclude, getIsAir, isRequiresCorrectToolForDrops, getIsValidSpawn, getIsRedstoneConductor, getIsSuffocating, getIsViewBlocking, getHasPostProcess, getEmissiveRendering, getOffsetFunction, getDrops, getSpawnTerrainParticles, getRequiredFeatures, getIgnitedByLava, getLiquid, getForceSolidOff, getForceSolidOn, getPushReaction, getInstrument, getReplaceable; @Accessor("lightEmission") getLuminance; @Accessor (setter, field from the name) setHasCollision, setIsRandomlyTicking, setMapColor, setDynamicShape, setCanOcclude, setIsAir, setDrops, setRequiresCorrectToolForDrops, setSpawnTerrainParticles, setRequiredFeatures, setOffsetFunction, setIgnitedByLava, setLiquid, setForceSolidOff, setForceSolidOn, setReplaceable | | | | |
| client | client.ModelPredicateProviderRegistryAccessor | 1.21.1 (1.21.1) | n.m.client.renderer.item.ItemProperties | @Invoker → callRegisterGeneric | (from name callRegisterGeneric) |  | — | default |
| client | client.ModelPredicateProviderRegistrySpecificAccessor | 1.21.1 (1.21.1) | n.m.client.renderer.item.ItemProperties | @Invoker → callRegister | (from name callRegister) |  | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-particles.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.client.particle` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in `mc/neoforge/1.21.1/gradle.properties`

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockDustParticleMixin | 1.21.1 (1.21.1) | n.m.client.particle.TerrainParticle | @WrapOperation → removeUntintableParticles | "<init>(L…/ClientLevel;DDDDDDL…/BlockState;L…/BlockPos;)V" | INVOKE `L…/IClientBlockExtensions;areBreakingParticlesTinted(L…/BlockState;L…/ClientLevel;L…/BlockPos;)Z` remap=false | — | allow=1 |
| client | ParticleManagerAccessor | 1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | @Accessor → getParticleAtlasTexture | "textureAtlas" |  | — | default |
| client | ParticleManagerAccessor$SimpleSpriteProviderAccessor | 1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine$MutableSpriteSet (`targets`) | @Accessor → getSprites (an interface nested in ParticleManagerAccessor) | "sprites" |  | — | default |
| client | ParticleManagerMixin | 1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | @Inject → onRegisterDefaultFactories | "registerProviders()V" | RETURN | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-renderer.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.neoforge.rendering.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · loaded only when `fabric_renderer_api_v1` is a loaded mod, as on the other versions. Fabric block models drawn by Indigo where NeoForge 21.1 draws blocks (FRAPI-5).

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | AbstractRenderContextAccessor | 1.21.1 (1.21.1) | Indigo's AbstractRenderContext (`@Pseudo`, `targets`: package-private, a mod's) | @Accessor transformStack → fabricated$transformStack | | | | default |
| client | BlockRenderDispatcherMixin | 1.21.1 (1.21.1) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapWithCondition → fabricated$breakingOverlay | "renderBreakingTexture(L…/BlockState;L…/BlockPos;L…/BlockAndTintGetter;L…/PoseStack;L…/VertexConsumer;L…/ModelData;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…;L…/ModelData;L…/RenderType;)V` (NeoForge's overload) | — | default |
| client | BlockRenderDispatcherMixin | 1.21.1 (1.21.1) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapWithCondition → fabricated$batched | "renderBatched(L…/BlockState;L…/BlockPos;L…/BlockAndTintGetter;L…/PoseStack;L…/VertexConsumer;ZL…/RandomSource;L…/ModelData;L…/RenderType;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…;L…/ModelData;L…/RenderType;)V` (NeoForge's overload) | — | default |
| client | ClientHooksMixin | 1.21.1 (1.21.1) | neo.client.ClientHooks | @WrapWithCondition → fabricated$fabricModels (static) | "renderPistonMovedBlocks(L…/BlockPos;L…/BlockState;L…/PoseStack;L…/MultiBufferSource;L…/Level;ZIL…/BlockRenderDispatcher;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…;L…/ModelData;L…/RenderType;)V` (NeoForge's overload) | — | default |
| client | FallingBlockRendererMixin | 1.21.1 (1.21.1) | n.m.client.renderer.entity.FallingBlockRenderer | @WrapWithCondition → fabricated$fabricModels | "render(L…/FallingBlockEntity;FFL…/PoseStack;L…/MultiBufferSource;I)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…;L…/ModelData;L…/RenderType;)V` (NeoForge's overload) | — | default |
| client | ForwardingBakedModelMixin | 1.21.1 (1.21.1) | Fabric API's ForwardingBakedModel (`@Pseudo`, `targets`: a mod's) | @WrapMethod → fabricated$holdWrappedThrow | "emitBlockQuads(L…/BlockAndTintGetter;L…/BlockState;L…/BlockPos;L…/Supplier;L…/RenderContext;)V" | holds a wrapped model's throw while Fabricated's Indigo draw is under way (HeldThrows, CORPUS-56) | — | require=0 |
| client | MultiPartBakedModelMixin | 1.21.1 (1.21.1) | n.m.client.resources.model.MultiPartBakedModel | no injectors; implements FabricBlockRendering.Composite; @Shadow selectors; @Unique fabricated$state (cached per broken-class generation) | | | | |
| client | SectionCompilerMixin | 1.21.1 (1.21.1) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderChunkRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockRenderDispatcher;renderBatched(…;L…/ModelData;L…/RenderType;)V` sugar:@Local(argsOnly = true) ×2, @Local Map, @Local BakedModel; implements FabricBlockRendering.SectionLayers (@Shadow getOrBeginLayer) | — | default |
| client | SectionCompilerMixin | 1.21.1 (1.21.1) | n.m.client.renderer.chunk.SectionCompiler | @Inject → fabricated$endSection | same | RETURN | — | default |
| client | WeightedBakedModelMixin | 1.21.1 (1.21.1) | n.m.client.resources.model.WeightedBakedModel | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$state (cached per broken-class generation) | | | | |

### `mc/neoforge/1.21.1/src/main/resources/fabricated-transfer.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.transfer` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AbstractFurnaceBlockEntityMixin | 1.21.1 (1.21.1) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → setStackSuppressUpdate; extends BaseContainerBlockEntity; implements SpecialLogicInventory; adds methods fabric_setSuppress, fabric_onFinalCommit; @Unique fields fabric_suppressSpecialLogic; @Shadow items, cookingProgress, cookingTotalTime, getTotalCookTime | "setItem" | HEAD cancellable=true | — | default |
| mixins | BucketItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.BucketItem | @Accessor → fabric_getFluid | "content" |  | — | default |
| mixins | BucketItemMixin | common121 (1.21.1,1.21.11) | n.m.world.item.BucketItem | @ModifyVariable → hookEmptyingSound | "playEmptySound" | STORE index=4 | — | default |
| mixins | BundleContentsComponentAccessor | 1.21.1 (1.21.1) | n.m.world.item.component.BundleContents | @Invoker → getOccupancy | "getWeight" |  | — | default |
| mixins | ChiseledBookshelfBlockEntityMixin | 1.21.1 (1.21.1) | n.m.world.level.block.entity.ChiseledBookShelfBlockEntity | @Inject → setStackBypass; implements SpecialLogicInventory; adds methods fabric_setSuppress, fabric_onTransfer, fabric_onFinalCommit; @Unique fields fabric_suppressSpecialLogic, fabric_lastInteractedParticipant; @Shadow items, lastInteractedSlot, updateState | "setItem" | HEAD cancellable=true | — | default |
| mixins | ContainerComponentAccessor | 1.21.1 (1.21.1) | n.m.world.item.component.ItemContainerContents | @Accessor → fabric_getStacks | "items" |  | — | default |
| mixins | CrafterBlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.CrafterBlock | @Inject → transferOrSpawnStack | "dispenseItem" | INVOKE `L…/ItemStack;isEmpty()Z` shift=At.Shift.BEFORE  sugar:@Local,@Local,@Local(ordinal = 1) | — | default |
| mixins | DoubleInventoryAccessor | 1.21.1 (1.21.1) | n.m.world.CompoundContainer | @Accessor → fabric_getFirst | "container1" |  | — | default |
| mixins | DoubleInventoryAccessor | 1.21.1 (1.21.1) | n.m.world.CompoundContainer | @Accessor → fabric_getSecond | "container2" |  | — | default |
| mixins | DropperBlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.DropperBlock | @Inject → hookDispense | "dispenseFrom" | INVOKE `L…/DispenseItemBehavior;dispense(L…/BlockSource;L…/ItemStack;)L…/ItemStack;` cancellable=true | — | allow=1 |
| mixins | FluidMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @Inject → hookGetBucketFillSound | "getPickupSound" | HEAD cancellable=true | — | default |
| mixins | ItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.Item | no injectors; implements ItemVariantCache; adds methods fabric_getCachedItemVariant; @Unique fields cachedItemVariant | | | | |
| mixins | JukeboxBlockEntityMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.entity.JukeboxBlockEntity | @Inject → setStackBypass | "setTheItem" | HEAD cancellable=true | — | default |
| mixins | LockableContainerBlockEntityMixin | 1.21.1 (1.21.1) | n.m.world.level.block.entity.BaseContainerBlockEntity | @WrapOperation → fabric_redirectMarkDirty (@Unique); implements SpecialLogicInventory; adds methods fabric_setSuppress, fabric_onFinalCommit; @Unique fields fabric_suppressSpecialLogic | "setItem(IL…/ItemStack;)V" | INVOKE `L…/BaseContainerBlockEntity;setChanged()V` | — | default |
| mixins | SimpleInventoryMixin | 1.21.1 (1.21.1) | n.m.world.SimpleContainer | @Redirect → fabric_redirectMarkDirty (@Unique); implements SpecialLogicInventory; adds methods fabric_setSuppress, fabric_onFinalCommit; @Unique fields fabric_suppressSpecialLogic | "setItem(IL…/ItemStack;)V" | INVOKE `L…/SimpleContainer;setChanged()V` | — | default |

### `mc/neoforge/1.21.1/src/main/resources/fabricated.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.neoforge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ServerConfigurationPacketListenerImplMixin | 1.21.1 (1.21.1) | n.m.server.network.ServerConfigurationPacketListenerImpl | no injectors; implements FabricServerConfigurationNetworkHandler; adds methods addTask, completeTask | | | | |
| mixins | ServerCommonPacketListenerDisconnectMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| mixins | PlayerListViewMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.players.PlayerList | @ModifyReturnValue → fabricated$viewOfCurrentList (NeoForge's unmodifiable view follows the list in the field, POLY-1) | "getPlayers" | RETURN | — | default |
| mixins | ChunkGeneratorFeaturesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.ChunkGenerator | @Inject → fabricated$keepNeoForgesLazy | "refreshFeaturesPerStep" | HEAD | — | require=0 |
| mixins | CrashReportMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.CrashReport | @Inject → fabricated$recordMixinFailure (records a Fabric mod's mixin the crash's throwable says failed as Mixin applied it, for the next launch to leave out, APPLY-1) | "<init>(L…/String;L…/Throwable;)V" | TAIL | — | default |
| mixins | UtilMixin | 1.21.1 (1.21.1) | n.m.Util | @Inject → fabricated$recordMixinFailure (records a Fabric mod's mixin the throwable a game worker thread stops on says failed as Mixin applied it, for the next launch to leave out, APPLY-2) | "onThreadException(L…/Thread;L…/Throwable;)V" | HEAD | — | require=0 |
| mixins | ResourceMixin | 1.21.1 (1.21.1) | n.m.server.packs.resources.Resource | no injectors; implements FabricResource; adds methods getFabricPackSource | | | | |
| mixins | MappedRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | no injectors; implements FabricRegistry | | | | |
| mixins | MappedRegistryEarlyWriteMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepPendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$restorePendingHolders | "freeze" | RETURN | — | default |
| mixins | BuiltInRegistriesFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints (the bootstrap's `freeze` waits for the main entrypoints, as on Fabric, CORPUS-32) | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | GameDataFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.GameData | @WrapMethod → fabricated$freezeBuiltInRegistries (runs the waiting `freeze` in NeoForge's first freeze after the entrypoints) | "freezeData" |  | — | default |
| mixins | RegistryManagerLateRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.RegistryManager | @Inject → fabricated$allowFabricInitializers (lets a registry through NeoForge's tracking after `NewRegistryEvent` while a Fabric entrypoint runs, PROVIDER-6) | "trackModdedRegistry" | HEAD | — | default |
| mixins | DefaultAttributesValidationMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | PackRepositorySourcesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (the sources set made mutable again before NeoForge adds a pack finder, PACK-1) | "addPackFinder" (NeoForge's) | HEAD | — | default |
| mixins | EntityTypeBuilderMixin | 1.21.1 (1.21.1) | n.m.world.entity.EntityType$Builder | @WrapOperation → fabricated$allowNullId (Fabric's no-argument build() passes a null id); implements FabricEntityType.Builder, FabricEntityTypeImpl.Builder; adds methods alwaysUpdateVelocity, build, fabric_setLivingEntityBuilder, fabric_setMobEntityBuilder; @Unique fields fabricated$livingBuilder, fabricated$mobBuilder | "build" | INVOKE `L…/Util;fetchChoiceType(L…/DSL$TypeReference;L…/String;)L…/Type;` | — | default |
| mixins | EntityTypeBuilderMixin | 1.21.1 (1.21.1) | n.m.world.entity.EntityType$Builder | @Inject → fabricated$applyChildBuilders | "build" | RETURN | — | default |
| mixins | EntityDataSerializersMixin | common121 (1.21.1,1.21.11) | n.m.network.syncher.EntityDataSerializers | @Inject → fabricated$toNeoForgeRegistry (a mod's serializer goes to NeoForge's registry, CORPUS-41, SER-1) | "registerSerializer" | HEAD cancellable=true | — | default |
| mixins | BlockEntityTypeMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.entity.BlockEntityType | no injectors; implements FabricBlockEntityType; adds methods addSupportedBlock | | | | |
| mixins | BlockEntityTypeBuilderMixin | 1.21.1 (1.21.1) | n.m.world.level.block.entity.BlockEntityType$Builder | no injectors; implements FabricBlockEntityType.Builder; adds methods build (no data fixer type, as upstream); @Shadow build(Type) | | | | |
| mixins | PoiTypesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | BlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.Block | no injectors; implements FabricBlock; adds methods getAppearance, makesOpenTrapdoorAboveClimbable | | | | |
| mixins | BlockStateMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.state.BlockState | no injectors; implements FabricBlockState; adds methods getAppearance | | | | |
| mixins | ItemMixin | 1.21.1 (1.21.1) | n.m.world.item.Item | @Inject → fabricated$onConstruct; implements FabricItem, ItemExtensions, IItemExtension; adds methods fabric_getEquipmentSlotProvider, fabric_setEquipmentSlotProvider, fabric_getCustomDamageHandler, fabric_setCustomDamageHandler, getCraftingRemainingItem, hasCraftingRemainingItem, damageItem, supportsEnchantment, isPrimaryItemFor, getEquipmentSlot, shouldCauseReequipAnimation, shouldCauseBlockBreakReset, getCreatorModId; @Unique fields fabricated$equipmentSlotProvider, fabricated$customDamageHandler | "<init>" | RETURN | — | default |
| mixins | ItemPropertiesMixin | 1.21.1 (1.21.1) | n.m.world.item.Item$Properties | no injectors; implements FabricItem.Settings | | | | |
| mixins | ItemStackMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack | | | | |
| mixins | TooltipFlagMixin | 1.21.1 (1.21.1) | n.m.world.item.TooltipFlag | no injectors; implements FabricTooltipType | | | | |
| mixins | DataComponentMapBuilderMixin | 1.21.1 (1.21.1) | n.m.core.component.DataComponentMap$Builder | no injectors; implements FabricComponentMapBuilder; adds methods getOrCreate, getOrEmpty; @Shadow map, set | | | | |
| mixins | ServerPlayerGameModeMixin | 1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeRemoveBlock; @Shadow level, player; @Unique fields fabricated$removedBlockEntity, fabricated$removedState | "removeBlock(L…/BlockPos;L…/BlockState;Z)Z" | HEAD | — | default |
| mixins | ServerPlayerGameModeMixin | 1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;Z)Z" | RETURN | — | default |
| mixins | ServerPlayerGameModeMixin | 1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeVanillaRemoval (reads the state and block entity before vanilla's removal where Fabricated puts it back, SEAM-3) | "destroyBlock" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | require=0 |
| mixins | ServerPlayerGameModeMixin | 1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterVanillaRemoval (fires `PlayerBlockBreakEvents.AFTER` at vanilla's `Block.destroy` put back, SEAM-3) | "destroyBlock" | INVOKE `L…/Block;destroy(L…/LevelAccessor;L…/BlockPos;L…/BlockState;)V` | — | require=0 |
| mixins | FakePlayerMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | fapi.api.entity.FakePlayer | no injectors; implements IPlayerExtension; adds methods isFakePlayer | | | | |
| mixins | PlayerAdvancementsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$keepRealOwner | "setPlayer" | HEAD cancellable=true | — | default |
| mixins | PlayerAdvancementsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$noFakeAdvancements | "award" | HEAD cancellable=true | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @Inject → fabricated$startReload | "reload" | HEAD  sugar:@Local(argsOnly = true) | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | VibrationSystemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.gameevent.vibrations.VibrationSystem | @ModifyReturnValue → fabricated$tableFrequency | "getGameEventFrequency(L…/Holder;)I" | RETURN | — | default |
| mixins | NetworkAddonHolderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl<br>n.m.server.network.ServerLoginPacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| mixins | ServerPayloadSendMixin | 1.21.1 (1.21.1) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;L…/PacketSendListener;)V" | HEAD cancellable=true | — | default |
| mixins | BlockApiCacheInvalidationMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerLevel | @Inject → fabricated$invalidateBlockApiCaches | "invalidateCapabilities(L…/BlockPos;)V" | TAIL | — | default |
| mixins | LivingEntityFluidTravelMixin | 1.21.1 (1.21.1) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$throughFabricFluid | "travel" | INVOKE `L…/LivingEntity;isInFluidType(L…/FluidState;)Z` | — | default |
| mixins | FluidTypeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" (NeoForge's) | INVOKE `L…/CommonHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | NetworkRegistryAccessor | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor fabricated$registrations, fabricated$setup; @Accessor (setter) fabricated$setSetup | | | | |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToClient | "checkPacket(L…/Packet;L…/ServerCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToServer | "checkPacket(L…/Packet;L…/ClientCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | VanillaNamespacePayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ServerboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| mixins | GenericPacketSplitterMeasureMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapOperation → fabricated$keepMeasuredBytes (@Local argsOnly ChannelHandlerContext) | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" | INVOKE `L…/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V` | — | default |
| mixins | GenericPacketSplitterMeasureMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$dropWhatIsNotHandedOn | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" |  | — | default |
| mixins | PacketEncoderMeasuredMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.network.PacketEncoder | @WrapOperation → fabricated$writeMeasuredBytes (@Local argsOnly ChannelHandlerContext) | "encode(L…/ChannelHandlerContext;L…/Packet;L…/ByteBuf;)V" | INVOKE `L…/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V` | — | default |
| mixins | NetworkRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$clientboundCodec (also Fabric codecs of `minecraft` payloads, NET-4) | "getCodec" | HEAD cancellable=true | — | default |
| mixins | MenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.MenuProvider | no injectors; implements FabricScreenHandlerFactory | | | | |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
| mixins | PackOverlaysMixin | common121 (1.21.1,1.21.11) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, none for a pack declaring NeoForge's, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;Z)V` index=3  sugar:@Local(argsOnly = true), @Local PackResources | — | default |
| mixins | JsonReloadConditionsMixin | 1.21.1 (1.21.1) | n.m.server.packs.resources.SimplePreparableReloadListener | @Inject → fabricated$decideFabricConditionsFirst (priority 500: a JSON data loader's files decided by their Fabric conditions before other mods' hooks at the start of applying them, each that loads replaced by a copy without them, so a mod reading them again its own way, as Moonlight's NeoForge build does, finds none; one whose conditions upstream can't parse left as it is, COND-1) | "lambda$reload$1" | HEAD | — | default |
| mixins | SimpleJsonResourceReloadListenerAccessor | 1.21.1 (1.21.1) | n.m.server.packs.resources.SimpleJsonResourceReloadListener | @Accessor → fabricated$directory (the folder upstream's conditions name in their messages, COND-1) | "directory" |  | — | default |
| mixins | EnchantmentBuilderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.enchantment.Enchantment$Builder | @Inject → fabricated$markModified | "*" | RETURN | — | default |
| mixins | RegistryDataLoaderEnchantmentMixin | common121 (1.21.1,1.21.11) | n.m.resources.RegistryDataLoader | @ModifyExpressionValue → fabricated$modifyEnchantment | "loadElementFromResource" | INVOKE `L…/DataResult;getOrThrow()L…/Object;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true),@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$blockEntityLoaded | "setBlockEntity" | INVOKE `L…/Map;put(…)L…/Object;` sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @WrapWithCondition → fabricated$blockEntityUnloaded | "setBlockEntity", "removeBlockEntity" | INVOKE `L…/BlockEntity;setRemoved()V` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$removedBlockEntityDropped | "getBlockEntity(L…/BlockPos;L…/LevelChunk$EntityCreationType;)L…/BlockEntity;" | INVOKE `L…/Map;remove(L…/Object;)L…/Object;` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @Inject → fabricated$blockEntitiesReplaced | "replaceWithPacketData" | HEAD | — | default |
| mixins | ReloadableResourceManagerMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$reopenListeners (a listener registered the vanilla way after NeoForge sorted the listeners is added after them) | "registerReloadListener" | HEAD | — | default |
| client | client.BlockColorsLookupMixin | common121 (1.21.1,1.21.11) | n.m.client.color.block.BlockColors | no injectors; adds methods get | | | | |
| client | client.ItemColorsLookupMixin | 1.21.1 (1.21.1) | n.m.client.color.item.ItemColors | no injectors; adds methods get; @Shadow itemColors | | | | |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onCreated | "<init>" | TAIL | — | default |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| client | client.KeyMappingAccessor | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.KeyMapping | @Accessor → fabricated$getClickCount | "clickCount" |  | — | default |
| client | client.MinecraftEntrypointsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypoints | "<init>" | INVOKE `L…/Thread;currentThread()L…/Thread;` | 0 | require=0 |
| client | client.MinecraftEntrypointsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypointsLate | "<init>" | TAIL | — | default |
| client | client.MinecraftModLoadingEntrypointsMixin | 1.21.1 (1.21.1) | n.m.client.Minecraft | @Inject → fabricated$runEntrypointsAfterModLoading | "<init>" | INVOKE `L…/ClientModLoader;begin(L…/Minecraft;L…/PackRepository;L…/ReloadableResourceManager;)V` shift=At.Shift.AFTER remap=false | — | default |
| client | client.MinecraftStartedMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$clientStarted | "run" | FIELD `L…/Minecraft;gameThread:L…/Thread;` opcode=Opcodes.PUTFIELD shift=At.Shift.AFTER | 0 | require=0 |
| client | client.MinecraftStoppingMixin | 1.21.1 (1.21.1) | n.m.client.Minecraft | @Inject → fabricated$clientStopping | "destroy" | INVOKE `L…/Logger;info(L…/String;)V` shift=At.Shift.AFTER remap=false | 0 | default |
| client | client.LiquidBlockTintMixin | common121 (1.21.1,1.21.11) | n.m.client.renderer.block.LiquidBlockRenderer | @ModifyExpressionValue → fabricated$fabricFluidColor | "tesselate" | INVOKE `L…/IClientFluidTypeExtensions;getTintColor(L…/FluidState;L…/BlockAndTintGetter;L…/BlockPos;)I` | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$beforeDestroyBlock | "destroyBlock" | HEAD | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$afterDestroyBlock | "destroyBlock" | RETURN | — | default |
| client | client.ClientNetworkAddonHolderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl<br>n.m.client.multiplayer.ClientHandshakePacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| client | client.ClientPayloadSendMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;)V" | HEAD cancellable=true | — | default |
| client | client.ClientVanillaNamespacePayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ClientboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| client | client.SoundEngineStreamMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.sounds.SoundEngine | @WrapOperation → fabricated$fabricAudioStream | "play" | INVOKE `L…/SoundInstance;getStream(L…/SoundBufferLibrary;L…/Sound;Z)L…/CompletableFuture;` | — | default |
| client | client.ClientChunkCacheMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @ModifyExpressionValue → fabricated$unloadOutOfRange | "updateViewRadius" | INVOKE `L…/ClientChunkCache$Storage;inRange(II)Z` sugar:@Local LevelChunk | — | default |
| client | client.ClientChunkCacheMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @Inject → fabricated$unloadReplaced | "replaceWithPacketData" | NEW `L…/LevelChunk` sugar:@Local LevelChunk | — | default |
| client | client.ExtendedBlockModelDeserializerMixin | 1.21.1 (1.21.1) | neo.client.model.ExtendedBlockModelDeserializer | @WrapOperation → fabricated$leaveFabricModsLoadersToThem (a `loader` NeoForge doesn't know, in a Fabric mod's namespace, is left to that mod's hook, CORPUS-62) | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/BlockModel;" | INVOKE `L…/ExtendedBlockModelDeserializer;deserializeGeometry(L…/JsonDeserializationContext;L…/JsonObject;)L…/IUnbakedGeometry;` | — | default |
| client | client.OptionsDefaultPacksMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Options | @Inject → fabricated$defaultEnabledPacks | "loadSelectedResourcePacks(L…/PackRepository;)V" | HEAD | — | default |
| client | client.GuiVanillaStacksMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui prio=Integer.MIN_VALUE (the lowest, so Fabric mods' injectors at the seam's read apply there, HUD-7) | @WrapOperation → fabricated$addStack (where the root manager adds a stack, told by its layers: runs the seam before vanilla's first stack, then puts what Fabric mods' hooks did to each vanilla stack into NeoForge's, VanillaHudStacks, HUD-7); adds method fabricated$read$layers, the seam Fabric mods' hooks at vanilla's read of `layers` are pointed at (vanilla's stacks as locals, then the read); @Shadow minecraft, layers, layerManager; @Unique fields fabricated$stacks | "<init>" | INVOKE `L…/GuiLayerManager;add(L…/GuiLayerManager;L…/BooleanSupplier;)L…/GuiLayerManager;` | — | require=0 |
| client | client.GuiVanillaStacksMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricated$stacksBuilt (runs the seam if no stack was found, and reports the layers the hooks added to a stack never placed) | "<init>" | RETURN | — | default |
| client | client.GuiLayerManagerLayersAccessor | 1.21.1 (1.21.1) | neo.client.gui.GuiLayerManager | @Accessor → fabricated$layers (HUD-7) | "layers" |  | — | default |
| client | client.LayeredDrawLayersAccessor | 1.21.1 (1.21.1) | n.m.client.gui.LayeredDraw | @Accessor → fabricated$layers (HUD-7) | "layers" |  | — | default |

### `mc/neoforge/1.21.11/src/main/resources/fabricated-biome.mixins.json`

Versions: 1.21.11 · package `net.fabricmc.fabric.mixin.biome` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in `mc/neoforge/1.21.11/gradle.properties`

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BiomeAccessor | 1.21.11 (1.21.11) | n.m.world.level.biome.Biome | @Accessor → fabricated$setAttributes | "attributes" |  | — | default |
| mixins | BiomeSourceMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.BiomeSource | @Redirect → getBiomes; adds method modifyBiomeSet (@Unique, overridden by TheEndBiomeSourceMixin) | "possibleBiomes" | INVOKE `L…/Supplier;get()L…/Object;` | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.11 (1.21.11) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setFoliageColorOverride | "foliageColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.11 (1.21.11) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setDryFoliageColorOverride | "dryFoliageColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.21.11 (1.21.11) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setGrassColorOverride | "grassColorOverride" |  | — | default |
| mixins | ClimateSamplerMixin | 1.21.11 (1.21.11) | n.m.world.level.biome.Climate$Sampler | no injectors; implements MultiNoiseSamplerHooks; adds methods fabric_setSeed, fabric_getSeed, fabric_getEndBiomesSampler; @Unique fields seed, endBiomesSampler | | | | |
| mixins | MappedRegistryAccessor | 1.21.11 (1.21.11) | n.m.core.MappedRegistry | @Accessor → fabricated$getRegistrationInfos | "registrationInfos" |  | — | default |
| mixins | MultiNoiseBiomeSourceMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSource | no injectors; implements BiomeSourceAccess; adds methods fabric_setModifyBiomeEntries, fabric_shouldModifyBiomeEntries; @Unique fields modifyBiomeEntries | | | | |
| mixins | NetherBiomePresetMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$1 (`targets`) | @Inject → apply | "apply" | RETURN cancellable=true | — | default |
| mixins | NoiseChunkMixin | 1.21.11 (1.21.11) | n.m.world.level.levelgen.NoiseChunk | @Inject → init; @Unique fields seed | "<init>" | TAIL | — | default |
| mixins | NoiseChunkMixin | 1.21.11 (1.21.11) | n.m.world.level.levelgen.NoiseChunk | @Inject → createMultiNoiseSampler | "cachedClimateSampler" | RETURN | — | default |
| mixins | RandomStateMixin | 1.21.11 (1.21.11) | n.m.world.level.levelgen.RandomState | @Inject → init; @Shadow sampler | "<init>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.11 (1.21.11) | n.m.world.level.biome.TheEndBiomeSource | @Inject → modifyCodec; extends BiomeSourceMixin; @Shadow @Mutable CODEC; @Unique fields overrides, biomeSetModified, hasCheckedForModifiedSet | "<clinit>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.11 (1.21.11) | n.m.world.level.biome.TheEndBiomeSource | @Inject → rememberLookup | "create" | HEAD | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.11 (1.21.11) | n.m.world.level.biome.TheEndBiomeSource | @Inject → clearLookup | "create" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.11 (1.21.11) | n.m.world.level.biome.TheEndBiomeSource | @Inject → init | "<init>" | RETURN | — | default |
| mixins | TheEndBiomeSourceMixin | 1.21.11 (1.21.11) | n.m.world.level.biome.TheEndBiomeSource | @Inject → getWeightedEndBiome | "getNoiseBiome" | RETURN cancellable=true | — | default |

### `mc/neoforge/1.21.11/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 1.21.11 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockBehaviourBlockStateBaseMixin | 1.21.11 (1.21.11) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | no injectors; implements OxidizableBlocksRegistryImpl.RandomTickCacheRefresher; adds methods fabric_api$refreshRandomTickCache | | | | |
| mixins | BlockBehaviourAccessor | 1.21.11 (1.21.11) | n.m.world.level.block.state.BlockBehaviour | @Invoker → callHasRandomTicks | "isRandomlyTicking" |  | — | default |
| mixins | AxeItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.AxeItem | @Accessor → getStrippedBlocks | "STRIPPABLES" |  | — | default |
| mixins | AxeItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.AxeItem | @Accessor → setStrippedBlocks | "STRIPPABLES" |  | — | default |
| mixins | AxeItemMixin | 1.21.11 (1.21.11) | n.m.world.item.AxeItem | @ModifyArg → handleCustomStrippingBehavior | "getStripped" | INVOKE `L…/Optional;map(L…/Function;)L…/Optional;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | PotionBrewingBuilderMixin | 1.21.11 (1.21.11) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build | "build" | HEAD | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_setCompostables | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_getCompostable | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | FireBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.FireBlock | @Inject → afterConstruct | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftToHeroAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoeItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.HoeItem | @Accessor → getTillingActions | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | WalkNodeEvaluatorMixin | 1.21.11 (1.21.11) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local | — | default |
| mixins | WeatheringCopperMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | 1.21.11 (1.21.11) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local | — | default |
| mixins | ShovelItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.ShovelItem | @Accessor → getPathStates | "FLATTENABLES" |  | — | default |
| mixins | VillagerAccessor | 1.21.11 (1.21.11) | n.m.world.entity.npc.villager.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerMixin | 1.21.11 (1.21.11) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | FabricatedComposterBlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.ComposterBlock | @ModifyReturnValue → fabricated$tableChance | "getValue(L…/ItemStack;)F" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | 1.21.11 (1.21.11) | neo.common.DataMapHooks | @WrapOperation → fabricated$fuelEvents | "populateFuelValues" | INVOKE `L…/FuelValues$Builder;build()L…/FuelValues;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true) | — | default |

### `mc/neoforge/1.21.11/src/main/resources/fabricated-renderer.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.neoforge.rendering.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · loaded only when `fabric_renderer_api_v1` is a loaded mod, as on 26.x.

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockModelPartMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.BlockModelPart (interface) | no injectors; extends FabricBlockModelPart; adds default emitQuads (FrapiDefaults: a part without its own keeps NeoForge's ambient occlusion) | | | | |
| client | BlockStateModelMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.BlockStateModel (interface) | no injectors; extends FabricBlockStateModel, BlockStateModelExtension; adds default emitQuads (FrapiDefaults: the parts NeoForge's level-aware collectParts gives) and default collectParts (NeoForge's level-aware one: a Fabric model's parts made from the quads it emits) | | | | |
| client | LayerRenderStateMixin | 1.21.11 (1.21.11) | n.m.client.renderer.item.ItemStackRenderState$LayerRenderState | @Inject → fabricated$clearMesh | "clear()V" | RETURN | — | default |
| client | LayerRenderStateMixin | 1.21.11 (1.21.11) | n.m.client.renderer.item.ItemStackRenderState$LayerRenderState | @Inject → fabricated$drawMesh | "submit" | HEAD | — | default |
| client | MutableQuadViewAccessor | 1.21.11 (1.21.11) | Indigo's MutableQuadViewImpl (`@Pseudo`, `targets`: a mod's) | @Accessor transformStack → fabricated$transformStack | | | | default |
| client | WrapperBlockStateModelMixin | 1.21.11 (1.21.11) | Fabric API's WrapperBlockStateModel (`@Pseudo`, `targets`: a mod's) | @WrapMethod → fabricated$holdWrappedThrow | "emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V" | holds a wrapped model's throw while one of Fabricated's draws of a Fabric model is under way (HeldThrows, CORPUS-56) | — | require=0 |

### `mc/neoforge/1.21.11/src/main/resources/fabricated-transfer.mixins.json`

Versions: 1.21.11 · package `net.fabricmc.fabric.mixin.transfer` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AbstractFurnaceBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → setStackSuppressUpdate | "setItem" | HEAD cancellable=true | — | default |
| mixins | BaseContainerBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.BaseContainerBlockEntity | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |
| mixins | BucketItemAccessor | common121 (1.21.1,1.21.11) | n.m.world.item.BucketItem | @Accessor → fabric_getFluid | "content" |  | — | default |
| mixins | BucketItemMixin | common121 (1.21.1,1.21.11) | n.m.world.item.BucketItem | @ModifyVariable → hookEmptyingSound | "playEmptySound" | STORE index=4 | — | default |
| mixins | BundleContentsAccessor | 1.21.11 (1.21.11) | n.m.world.item.component.BundleContents | @Invoker → getOccupancy | "getWeight" |  | — | default |
| mixins | ChiseledBookShelfBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.ChiseledBookShelfBlockEntity | @Inject → setStackBypass | "setItem" | HEAD cancellable=true | — | default |
| mixins | CompoundContainerAccessor | 1.21.11 (1.21.11) | n.m.world.CompoundContainer | @Accessor → fabric_getFirst | "container1" |  | — | default |
| mixins | CompoundContainerAccessor | 1.21.11 (1.21.11) | n.m.world.CompoundContainer | @Accessor → fabric_getSecond | "container2" |  | — | default |
| mixins | CrafterBlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.CrafterBlock | @Inject → transferOrSpawnStack | "dispenseItem" | INVOKE `L…/ItemStack;isEmpty()Z` shift=At.Shift.BEFORE  sugar:@Local,@Local,@Local(ordinal = 1) | — | default |
| mixins | DropperBlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.DropperBlock | @Inject → hookDispense | "dispenseFrom" | INVOKE `L…/DispenseItemBehavior;dispense(L…/BlockSource;L…/ItemStack;)L…/ItemStack;` cancellable=true | — | allow=1 |
| mixins | FluidMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @Inject → hookGetBucketFillSound | "getPickupSound" | HEAD cancellable=true | — | default |
| mixins | ItemContainerContentsAccessor | 1.21.11 (1.21.11) | n.m.world.item.component.ItemContainerContents | @Accessor → fabric_getStacks | "items" |  | — | default |
| mixins | ItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.Item | no injectors; implements ItemVariantCache; adds methods fabric_getCachedItemVariant; @Unique fields cachedItemVariant | | | | |
| mixins | JukeboxBlockEntityMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.entity.JukeboxBlockEntity | @Inject → setStackBypass | "setTheItem" | HEAD cancellable=true | — | default |
| mixins | ListBackedContainerMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.ListBackedContainer | @WrapOperation → cancelMarkDirty | "setItem" | INVOKE `L…/ListBackedContainer;setChanged()V` | — | default |
| mixins | ShelfBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.ShelfBlockEntity | no injectors; implements SpecialLogicInventory, SpecialLogicAccess; adds methods fabric_onFinalCommit, fabric_setSuppress, fabric_shouldSuppressSpecialLogic; @Unique fields fabric_suppressSpecialLogic | | | | |
| mixins | SimpleContainerMixin | 1.21.11 (1.21.11) | n.m.world.SimpleContainer | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |

### `mc/neoforge/1.21.11/src/main/resources/fabricated.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.neoforge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ServerConfigurationPacketListenerImplMixin | 1.21.11 (1.21.11) | n.m.server.network.ServerConfigurationPacketListenerImpl | no injectors; implements FabricServerConfigurationNetworkHandler; adds methods addTask, completeTask | | | | |
| mixins | MinecraftServerMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | no injectors; implements DataResourceStore; adds methods getOrThrow | | | | |
| mixins | PlayerListViewMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.players.PlayerList | @ModifyReturnValue → fabricated$viewOfCurrentList (NeoForge's unmodifiable view follows the list in the field, POLY-1) | "getPlayers" | RETURN | — | default |
| mixins | ReloadableServerResourcesTagsLoadedMixin | 1.21.11 (1.21.11) | n.m.server.ReloadableServerResources | @Inject → fabricated$keepRegistries | "<init>" | TAIL | — | default |
| mixins | ReloadableServerResourcesTagsLoadedMixin | 1.21.11 (1.21.11) | n.m.server.ReloadableServerResources | @Inject → fabricated$tagsLoaded (fires Fabric's `TAGS_LOADED` where upstream does) | "updateStaticRegistryTags" | TAIL | — | default |
| mixins | ChunkGeneratorFeaturesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.ChunkGenerator | @Inject → fabricated$keepNeoForgesLazy | "refreshFeaturesPerStep" | HEAD | — | require=0 |
| mixins | CrashReportMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.CrashReport | @Inject → fabricated$recordMixinFailure (records a Fabric mod's mixin the crash's throwable says failed as Mixin applied it, for the next launch to leave out, APPLY-1) | "<init>(L…/String;L…/Throwable;)V" | TAIL | — | default |
| mixins | UtilMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.util.Util | @Inject → fabricated$recordMixinFailure (records a Fabric mod's mixin the throwable a game worker thread stops on says failed as Mixin applied it, for the next launch to leave out, APPLY-2) | "onThreadException(L…/Thread;L…/Throwable;)V" | HEAD | — | require=0 |
| mixins | ReloadableServerResourcesMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerResources | no injectors; implements FabricDataResourceStoreHolder; adds methods fabric$getDataResourceStore; @Unique fields fabricated$dataResourceStore | | | | |
| mixins | ResourceMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.Resource | no injectors; implements FabricResource; adds methods getFabricPackSource | | | | |
| mixins | MappedRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | no injectors; implements FabricRegistry | | | | |
| mixins | MappedRegistryEarlyWriteMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepPendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$restorePendingHolders | "freeze" | RETURN | — | default |
| mixins | MappedRegistryFrozenTagsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepBoundTags (NeoForge's freeze leaves the tags `freeze` bound); @Shadow frozen | "bindAllTagsToEmpty" | HEAD cancellable=true | — | default |
| mixins | BuiltInRegistriesFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints (the bootstrap's `freeze` waits for the main entrypoints, as on Fabric, CORPUS-32) | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | GameDataFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.GameData | @WrapMethod → fabricated$freezeBuiltInRegistries (runs the waiting `freeze` in NeoForge's first freeze after the entrypoints) | "freezeData" |  | — | default |
| mixins | RegistryManagerLateRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.RegistryManager | @Inject → fabricated$allowFabricInitializers (lets a registry through NeoForge's tracking after `NewRegistryEvent` while a Fabric entrypoint runs, PROVIDER-6) | "trackModdedRegistry" | HEAD | — | default |
| mixins | DefaultAttributesValidationMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | EntityDataSerializersMixin | common121 (1.21.1,1.21.11) | n.m.network.syncher.EntityDataSerializers | @Inject → fabricated$toNeoForgeRegistry (a mod's serializer goes to NeoForge's registry, CORPUS-41, SER-1) | "registerSerializer" | HEAD cancellable=true | — | default |
| mixins | PackRepositorySourcesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (the sources set made mutable again before NeoForge adds a pack finder, PACK-1) | "addPackFinder" (NeoForge's) | HEAD | — | default |
| mixins | MobEffectMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.effect.MobEffect | no injectors; implements FabricMobEffect | | | | |
| mixins | ExecuteCommandEffectMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.commands.execution.tasks.ExecuteCommand | @WrapOperation → fabricated$runExecutable (an effect a command gives comes from that command in Fabric's effect events, where upstream hooks Brigadier, EFFECT-1) | "execute" | INVOKE `Lcom/mojang/brigadier/context/ContextChain;runExecutable(…)I` | — | default |
| mixins | EntityTypeBuilderMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.EntityType$Builder | @Inject → fabricated$applyChildBuilders | "build" | RETURN | — | default |
| mixins | BlockEntityTypeMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.entity.BlockEntityType | no injectors; implements FabricBlockEntityType; adds methods addSupportedBlock | | | | |
| mixins | PoiTypesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | BlockMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.Block | no injectors; implements FabricBlock; adds methods getAppearance, makesOpenTrapdoorAboveClimbable | | | | |
| mixins | BlockStateMixin | common121 (1.21.1,1.21.11) | n.m.world.level.block.state.BlockState | no injectors; implements FabricBlockState; adds methods getAppearance | | | | |
| mixins | ItemMixin | 1.21.11 (1.21.11) | n.m.world.item.Item | @Inject → fabricated$onConstruct | "<init>" | RETURN | — | default |
| mixins | ItemPropertiesMixin | 1.21.11 (1.21.11) | n.m.world.item.Item$Properties | no injectors; implements FabricItem.Settings; adds methods modelId | | | | |
| mixins | ItemStackMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack | | | | |
| mixins | TooltipFlagMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.TooltipFlag | no injectors; implements FabricTooltipFlag | | | | |
| mixins | DataComponentMapBuilderMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.core.component.DataComponentMap$Builder | no injectors; implements FabricComponentMapBuilder; adds methods contains, getOrCreate, getOrEmpty | | | | |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | HEAD | — | default |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | RETURN | — | default |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeVanillaRemoval (reads the state and block entity before vanilla's removal where Fabricated puts it back, SEAM-3) | "destroyBlock" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | require=0 |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterVanillaRemoval (fires `PlayerBlockBreakEvents.AFTER` at vanilla's `Block.destroy` put back, SEAM-3) | "destroyBlock" | INVOKE `L…/Block;destroy(L…/LevelAccessor;L…/BlockPos;L…/BlockState;)V` | — | require=0 |
| mixins | BlockStateBaseMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | @Inject → fabricated$useWithoutItem | "useWithoutItem" | HEAD cancellable=true | — | default |
| mixins | ItemStackUseMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | @WrapOperation → fabricated$use | "use" | INVOKE `L…/Item;use(L…/Level;L…/Player;L…/InteractionHand;)L…/InteractionResult;` | — | default |
| mixins | PickItemMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromBlock | "handlePickItemFromBlock" | INVOKE `L…/BlockState;getCloneItemStack(L…/BlockPos;L…/LevelReader;ZL…/Player;)L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | PickItemMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromEntity | "handlePickItemFromEntity" | INVOKE `L…/Entity;getPickResult()L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | FakePlayerMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | fapi.api.entity.FakePlayer | no injectors; implements IPlayerExtension; adds methods isFakePlayer | | | | |
| mixins | PlayerAdvancementsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$keepRealOwner | "setPlayer" | HEAD cancellable=true | — | default |
| mixins | PlayerAdvancementsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$noFakeAdvancements | "award" | HEAD cancellable=true | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @Inject → fabricated$startReload | "reload" | HEAD  sugar:@Local(argsOnly = true) | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | VibrationSystemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.gameevent.vibrations.VibrationSystem | @ModifyReturnValue → fabricated$tableFrequency | "getGameEventFrequency(L…/Holder;)I" | RETURN | — | default |
| mixins | NetworkAddonHolderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl<br>n.m.server.network.ServerLoginPacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| mixins | ServerPayloadSendMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;Lio/netty/channel/ChannelFutureListener;)V" | HEAD cancellable=true | — | default |
| mixins | ServerCommonPacketListenerDisconnectMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| mixins | BlockApiCacheInvalidationMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerLevel | @Inject → fabricated$invalidateBlockApiCaches | "invalidateCapabilities(L…/BlockPos;)V" | TAIL | — | default |
| mixins | LivingEntityEffectsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectAdded | "onEffectAdded" | RETURN | — | default |
| mixins | LivingEntityEffectsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectsRemoved | "onEffectsRemoved" | RETURN | — | default |
| mixins | LivingEntityFluidTravelMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$throughFabricFluid (cancellable) | "shouldTravelInFluid" | RETURN | — | default |
| mixins | FluidTypeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" (NeoForge's) | INVOKE `L…/CommonHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | NetworkRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$clientboundCodec (also Fabric codecs of `minecraft` payloads, NET-4) | "getCodec" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryAccessor | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor fabricated$registrations, fabricated$setup; @Accessor (setter) fabricated$setSetup | | | | |
| mixins | NetworkRegistryHandlersAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor → fabricated$serverboundHandlers | "SERVERBOUND_HANDLERS" |  | — | default |
| mixins | NetworkRegistryHandlersAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor → fabricated$clientboundHandlers | "CLIENTBOUND_HANDLERS" |  | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToClient | "checkPacket(L…/Packet;L…/ServerCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToServer | "checkPacket(L…/Packet;L…/ClientCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | VanillaNamespacePayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ServerboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| mixins | GenericPacketSplitterMeasureMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapOperation → fabricated$keepMeasuredBytes (@Local argsOnly ChannelHandlerContext) | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" | INVOKE `L…/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V` | — | default |
| mixins | GenericPacketSplitterMeasureMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$dropWhatIsNotHandedOn | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" |  | — | default |
| mixins | PacketEncoderMeasuredMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.network.PacketEncoder | @WrapOperation → fabricated$writeMeasuredBytes (@Local argsOnly ChannelHandlerContext) | "encode(L…/ChannelHandlerContext;L…/Packet;L…/ByteBuf;)V" | INVOKE `L…/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V` | — | default |
| mixins | MenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.MenuProvider | no injectors; implements FabricScreenHandlerFactory | | | | |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
| mixins | PackOverlaysMixin | common121 (1.21.1,1.21.11) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, none for a pack declaring NeoForge's, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;Z)V` index=3  sugar:@Local(argsOnly = true), @Local PackResources | — | default |
| mixins | RegistryFriendlyByteBufMixin | 1.21.11 (1.21.11) | n.m.network.RegistryFriendlyByteBuf | no injectors; implements FabricRegistryByteBuf; adds methods fabric_getSendableConfigurationChannels, fabric_setSendableConfigurationChannels; @Unique fields fabricated$sendableConfigurationChannels | | | | |
| mixins | EnchantmentBuilderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.enchantment.Enchantment$Builder | @Inject → fabricated$markModified | "*" | RETURN | — | default |
| mixins | RegistryDataLoaderEnchantmentMixin | common121 (1.21.1,1.21.11) | n.m.resources.RegistryDataLoader | @ModifyExpressionValue → fabricated$modifyEnchantment | "loadElementFromResource" | INVOKE `L…/DataResult;getOrThrow()L…/Object;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true),@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$blockEntityLoaded | "setBlockEntity" | INVOKE `L…/Map;put(…)L…/Object;` sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @WrapWithCondition → fabricated$blockEntityUnloaded | "setBlockEntity", "removeBlockEntity" | INVOKE `L…/BlockEntity;setRemoved()V` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$removedBlockEntityDropped | "getBlockEntity(L…/BlockPos;L…/LevelChunk$EntityCreationType;)L…/BlockEntity;" | INVOKE `L…/Map;remove(L…/Object;)L…/Object;` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @Inject → fabricated$blockEntitiesReplaced | "replaceWithPacketData" | HEAD | — | default |
| mixins | ReloadableResourceManagerMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$reopenListeners (a listener registered the vanilla way after NeoForge sorted the listeners is added after them) | "registerReloadListener" | HEAD | — | default |
| client | client.BlockColorsLookupMixin | common121 (1.21.1,1.21.11) | n.m.client.color.block.BlockColors | no injectors; adds methods get | | | | |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onCreated | "<init>" | TAIL | — | default |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| client | client.ClientReloadListenerMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$holdBackEarlyListener (cancellable) | "registerReloadListener" | HEAD | — | default |
| client | client.KeyMappingAccessor | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.KeyMapping | @Accessor → fabricated$getClickCount | "clickCount" |  | — | default |
| client | client.MinecraftEntrypointsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypoints | "<init>" | INVOKE `L…/Thread;currentThread()L…/Thread;` | 0 | require=0 |
| client | client.MinecraftEntrypointsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypointsLate | "<init>" | TAIL | — | default |
| client | client.MinecraftStartedMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$clientStarted | "run" | FIELD `L…/Minecraft;gameThread:L…/Thread;` opcode=Opcodes.PUTFIELD shift=At.Shift.AFTER | 0 | require=0 |
| client | client.LiquidBlockTintMixin | common121 (1.21.1,1.21.11) | n.m.client.renderer.block.LiquidBlockRenderer | @ModifyExpressionValue → fabricated$fabricFluidColor | "tesselate" | INVOKE `L…/IClientFluidTypeExtensions;getTintColor(L…/FluidState;L…/BlockAndTintGetter;L…/BlockPos;)I` | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$beforeDestroyBlock | "destroyBlock" | HEAD | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$afterDestroyBlock | "destroyBlock" | RETURN | — | default |
| client | client.ClientNetworkAddonHolderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl<br>n.m.client.multiplayer.ClientHandshakePacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| client | client.ClientPayloadSendMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;)V" | HEAD cancellable=true | — | default |
| client | client.ClientVanillaNamespacePayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ClientboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| client | client.ParticleResourcesMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.client.particle.ParticleResources | no injectors; non-@Unique fields providers | | | | |
| client | client.SoundEngineStreamMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.sounds.SoundEngine | @WrapOperation → fabricated$fabricAudioStream | "play" | INVOKE `L…/SoundInstance;getStream(L…/SoundBufferLibrary;L…/Sound;Z)L…/CompletableFuture;` | — | default |
| client | ClientChunkCacheMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @ModifyExpressionValue → fabricated$unloadOutOfRange | "updateViewRadius" | INVOKE `L…/ClientChunkCache$Storage;inRange(II)Z` sugar:@Local LevelChunk | — | default |
| client | ClientChunkCacheMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @Inject → fabricated$unloadReplaced | "replaceWithPacketData" | NEW `L…/LevelChunk` sugar:@Local LevelChunk | — | default |
| client | OptionsDefaultPacksMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Options | @Inject → fabricated$defaultEnabledPacks | "loadSelectedResourcePacks(L…/PackRepository;)V" | HEAD | — | default |
| client | client.ModelManagerExtraModelsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.client.resources.model.ModelManager | @Inject → fabricated$fabricExtraModels | "discoverModelDependencies(…, StandaloneModelLoader$LoadedModels)" (NeoForge's four-argument overload) | INVOKE `L…/ModelDiscovery;resolve()L…/Map;` | — | default |
| client | client.UnbakedModelParserMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.client.model.UnbakedModelParser$Deserializer | @Inject → fabricated$fabricTypes | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/UnbakedModel;" | HEAD cancellable=true | — | default |
| client | client.BlockStateModelUnbakedMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.BlockStateModel$Unbaked | @ModifyExpressionValue → fabricated$fabricTypes | "<clinit>" | INVOKE `L…/Codec;flatComapMap(…)L…/Codec;` slice from PUTSTATIC `HARDCODED_WEIGHTED_CODEC` | — | default |
| client | client.ModelManagerExtraModelsUploadMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.ModelManager | @WrapOperation → fabricated$fabricExtraModels | "apply" | INVOKE `L…/ClientHooks;onModelBake(…)V` | — | default |
| client | client.ModelManagerSpriteGetterMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.ModelManager$1 | @Inject → fabricated$keepAtlases; adds method spriteFinder (Fabric's sprite getter API); @Shadow blockMissing; @Unique fabricated$atlases | "<init>" | RETURN sugar:@Local(argsOnly = true, ordinal = 0),@Local(argsOnly = true, ordinal = 1) | — | default |
| client | client.MultiPartModelMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.multipart.MultiPartModel | no injectors; implements FabricBlockRendering.Composite; @Shadow shared, blockState; @Unique fabricated$state (cached per broken-class generation) | | | | |
| client | client.SectionCompilerMixin | 1.21.11 (1.21.11) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$skipFabricParts | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's level-aware overload) | — | default |
| client | client.SectionCompilerMixin | 1.21.11 (1.21.11) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockRenderDispatcher;renderBatched(…;L…/Function;ZL…/List;)V` | — | default |
| client | client.WeightedVariantsMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$state (cached per broken-class generation) | | | | |
| client | client.GuiRendererPictureInPictureMixin | 1.21.11 (1.21.11) | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$prepareModRenderer (vanilla's map of picture-in-picture renderers given back, PIP-1); non-@Unique field pictureInPictureRenderers | "preparePictureInPictureState" | HEAD cancellable=true | — | default |
| client | client.GuiRendererPictureInPictureMixin | 1.21.11 (1.21.11) | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$closeModRenderers | "close" | TAIL | — | default |

### `mc/neoforge/26.1.2/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 26.1.2 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · mixinextras.minVersion=0.5.0

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AxeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.AxeItem | @Accessor → getStrippables | "STRIPPABLES" |  | — | default |
| mixins | AxeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.AxeItem | @Accessor → setStrippables | "STRIPPABLES" |  | — | default |
| mixins | AxeItemMixin | common26early (26.1.2,26.2) | n.m.world.item.AxeItem | @ModifyArg → handleCustomStrippingBehavior | "getStripped" | INVOKE `L…/Optional;map(L…/Function;)L…/Optional;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | BlockBehaviourAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour | @Invoker → callIsRandomlyTicking | (from name callIsRandomlyTicking) |  | — | default |
| mixins | BlockBehaviourBlockStateBaseMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | no injectors; implements OxidizableBlocksRegistryImpl.RandomTickCacheRefresher; adds methods fabric_api$refreshRandomTickCache | | | | |
| mixins | DecoratedPotPatternsAccessor | 26.1.2 (26.1.2) | n.m.world.level.block.entity.DecoratedPotPatterns | @Accessor → fabric_getDecoratedPotPatternsMap | "ITEM_TO_POT_TEXTURE" |  | — | default |
| mixins | DecoratedPotPatternsMixin | 26.1.2 (26.1.2) | n.m.world.level.block.entity.DecoratedPotPatterns | @ModifyExpressionValue → turnToMutable | "<clinit>" | INVOKE `L…/Map;ofEntries([L…/Map$Entry;)L…/Map;` | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → afterConstruct | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds(L…/BlockState;)I" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftToHeroAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.HoeItem | @Accessor → getTillables | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local(name = "pos") | — | default |
| mixins | PotionBrewingBuilderMixin | common26early (26.1.2,26.2) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build | "build" | HEAD | — | default |
| mixins | ShovelItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.ShovelItem | @Accessor → getFlattenables | "FLATTENABLES" |  | — | default |
| mixins | VillagerAccessor | common26early (26.1.2,26.2) | n.m.world.entity.npc.villager.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | WalkNodeEvaluatorMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local(name = "blockState") | — | default |
| mixins | WeatheringCopperMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_setCompostables | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_getCompostable | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | fluid.EntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @Invoker → callDoWaterSplashEffect | (from name callDoWaterSplashEffect) |  | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyArg → addCustomTags | "<init>" | INVOKE `L…/EntityFluidInteraction;<init>(L…/Set;)V` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → checkForCustomFluids | "isInLiquid" | RETURN | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → handleCustomFluidInteractionUpdates | "updateFluidInteraction" | RETURN | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyExpressionValue → checkIfInSwimmableFluid | "updateSwimming" | INVOKE `L…/Entity;isInWater()Z` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyExpressionValue → checkCustomFluids | "isVisuallyCrawling" | INVOKE `L…/Entity;isInWater()Z` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → preventParticlesInFluids | "canSpawnSprintParticle" | RETURN | — | default |
| mixins | fluid.FloatGoalMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.ai.goal.FloatGoal | @ModifyReturnValue → floatInCustomFluids | "canUse" | RETURN | — | default |
| mixins | fluid.LivingEntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Invoker → callJumpOutOfFluid | (from name callJumpOutOfFluid) |  | — | default |
| mixins | fluid.LivingEntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Invoker → callGetWaterSlowDown | (from name callGetWaterSlowDown) |  | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @ModifyExpressionValue → isInCustomFluid | "shouldTravelInFluid" | INVOKE `L…/LivingEntity;isInLava()Z` | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @WrapWithCondition → travelInCustomFluid | "travelInFluid" | INVOKE `L…/LivingEntity;travelInLava(L…/Vec3;DZD)V` | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @Inject → travelFlyingInCustomFluid | "travelFlying(L…/Vec3;FFF)V" | HEAD cancellable=true | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @ModifyExpressionValue → fixDoubleUpdateForCustomFluids | "checkFallDamage" | INVOKE `L…/LivingEntity;isInWater()Z` | — | default |
| mixins | fluid.SwimMixin | common26early (26.1.2,26.2) | n.m.world.entity.ai.behavior.Swim | @ModifyReturnValue → floatInCustomFluids | "shouldSwim" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | FabricatedComposterBlockMixin | common26early (26.1.2,26.2) | n.m.world.level.block.ComposterBlock | @ModifyReturnValue → fabricated$tableChance | "getValue(L…/ItemStack;)F" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | common26early (26.1.2,26.2) | neo.common.DataMapHooks | @WrapOperation → fabricated$fuelEvents | "populateFuelValues" | INVOKE `L…/FuelValues$Builder;build()L…/FuelValues;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true) | — | default |

### `mc/neoforge/26.2/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 26.2 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · mixinextras.minVersion=0.5.0

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AxeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.AxeItem | @Accessor → getStrippables | "STRIPPABLES" |  | — | default |
| mixins | AxeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.AxeItem | @Accessor → setStrippables | "STRIPPABLES" |  | — | default |
| mixins | AxeItemMixin | common26early (26.1.2,26.2) | n.m.world.item.AxeItem | @ModifyArg → handleCustomStrippingBehavior | "getStripped" | INVOKE `L…/Optional;map(L…/Function;)L…/Optional;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | BlockBehaviourAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour | @Invoker → callIsRandomlyTicking | (from name callIsRandomlyTicking) |  | — | default |
| mixins | BlockBehaviourBlockStateBaseMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | no injectors; implements OxidizableBlocksRegistryImpl.RandomTickCacheRefresher; adds methods fabric_api$refreshRandomTickCache | | | | |
| mixins | DecoratedPotPatternsMixin | 26.2 (26.2) | n.m.world.level.block.entity.DecoratedPotPatterns | @Inject → makeItemToPatternMappingsMutable | "itemToPatternMappings" | RETURN | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → afterConstruct | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds(L…/BlockState;)I" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftToHeroAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.HoeItem | @Accessor → getTillables | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local(name = "pos") | — | default |
| mixins | PotionBrewingBuilderMixin | common26early (26.1.2,26.2) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build | "build" | HEAD | — | default |
| mixins | ShovelItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.ShovelItem | @Accessor → getFlattenables | "FLATTENABLES" |  | — | default |
| mixins | VillagerAccessor | common26early (26.1.2,26.2) | n.m.world.entity.npc.villager.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | WalkNodeEvaluatorMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local(name = "blockState") | — | default |
| mixins | WeatheringCopperMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_setCompostables | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_getCompostable | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | fluid.EntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @Invoker → callDoWaterSplashEffect | (from name callDoWaterSplashEffect) |  | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyArg → addCustomTags | "<init>" | INVOKE `L…/EntityFluidInteraction;<init>(L…/Set;)V` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → checkForCustomFluids | "isInLiquid" | RETURN | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → handleCustomFluidInteractionUpdates | "updateFluidInteraction" | RETURN | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyExpressionValue → checkIfInSwimmableFluid | "updateSwimming" | INVOKE `L…/Entity;isInWater()Z` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyExpressionValue → checkCustomFluids | "isVisuallyCrawling" | INVOKE `L…/Entity;isInWater()Z` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → preventParticlesInFluids | "canSpawnSprintParticle" | RETURN | — | default |
| mixins | fluid.FloatGoalMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.ai.goal.FloatGoal | @ModifyReturnValue → floatInCustomFluids | "canUse" | RETURN | — | default |
| mixins | fluid.LivingEntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Invoker → callJumpOutOfFluid | (from name callJumpOutOfFluid) |  | — | default |
| mixins | fluid.LivingEntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Invoker → callGetWaterSlowDown | (from name callGetWaterSlowDown) |  | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @ModifyExpressionValue → isInCustomFluid | "shouldTravelInFluid" | INVOKE `L…/LivingEntity;isInLava()Z` | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @WrapWithCondition → travelInCustomFluid | "travelInFluid" | INVOKE `L…/LivingEntity;travelInLava(L…/Vec3;DZD)V` | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @Inject → travelFlyingInCustomFluid | "travelFlying(L…/Vec3;FFF)V" | HEAD cancellable=true | — | default |
| mixins | fluid.LivingEntityMixin | common26early (26.1.2,26.2) | n.m.world.entity.LivingEntity | @ModifyExpressionValue → fixDoubleUpdateForCustomFluids | "checkFallDamage" | INVOKE `L…/LivingEntity;isInWater()Z` | — | default |
| mixins | fluid.SwimMixin | common26early (26.1.2,26.2) | n.m.world.entity.ai.behavior.Swim | @ModifyReturnValue → floatInCustomFluids | "shouldSwim" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | FabricatedComposterBlockMixin | common26early (26.1.2,26.2) | n.m.world.level.block.ComposterBlock | @ModifyReturnValue → fabricated$tableChance | "getValue(L…/ItemStack;)F" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | common26early (26.1.2,26.2) | neo.common.DataMapHooks | @WrapOperation → fabricated$fuelEvents | "populateFuelValues" | INVOKE `L…/FuelValues$Builder;build()L…/FuelValues;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true) | — | default |

### `mc/neoforge/26.3/src/main/resources/fabricated-biome.mixins.json`

Versions: 26.3 · package `net.fabricmc.fabric.mixin.biome` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in `mc/neoforge/26.3/gradle.properties`; `neoforge.mods.toml` describes it as fabric-biome-api-v1's biome source mixins, unchanged from upstream

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BiomeSourceMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.BiomeSource | @Redirect → getBiomes; adds method modifyBiomeSet (@Unique, overridden by TheEndBiomeSourceMixin) | "possibleBiomes" | INVOKE `L…/Supplier;get()L…/Object;` | — | default |
| mixins | ClimateSamplerMixin | 26.3 (26.3) | n.m.world.level.biome.Climate$Sampler | no injectors; implements MultiNoiseSamplerHooks; adds methods fabric_setSeed, fabric_getSeed, fabric_getEndBiomesSampler; @Unique fields seed, endBiomesSampler | | | | |
| mixins | MultiNoiseBiomeSourceMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSource | no injectors; implements BiomeSourceAccess; adds methods fabric_setModifyBiomeEntries, fabric_shouldModifyBiomeEntries; @Unique fields modifyBiomeEntries | | | | |
| mixins | NetherBiomePresetMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$1 (`targets`) | @Inject → apply | "apply" | RETURN cancellable=true | — | default |
| mixins | RandomStateMixin | 26.3 (26.3) | n.m.world.level.levelgen.RandomState | @Inject → setSeed; @Shadow seed | "createClimateSampler" | RETURN | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → modifyCodec; extends BiomeSourceMixin; @Shadow @Mutable CODEC; @Unique fields overrides, biomeSetModified, hasCheckedForModifiedSet | "<clinit>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → rememberLookup | "create" | HEAD | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → clearLookup | "create" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → init | "<init>" | RETURN | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → getWeightedEndBiome | "getNoiseBiome" | RETURN cancellable=true | — | default |

### `mc/neoforge/26.3/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 26.3 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · mixinextras.minVersion=0.5.0

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockBehaviourAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour | @Invoker → callIsRandomlyTicking | (from name callIsRandomlyTicking) |  | — | default |
| mixins | BlockBehaviourBlockStateBaseMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | no injectors; implements OxidizableBlocksRegistryImpl.RandomTickCacheRefresher; adds methods fabric_api$refreshRandomTickCache | | | | |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → afterConstruct | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds(L…/BlockState;)I" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftToHeroAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoneycombItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local(name = "pos") | — | default |
| mixins | VillagerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | WalkNodeEvaluatorMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local(name = "blockState") | — | default |
| mixins | WeatheringCopperMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_setCompostables | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_getCompostable | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | fluid.EntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @Invoker → callDoWaterSplashEffect | (from name callDoWaterSplashEffect) |  | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyArg → addCustomTags | "<init>" | INVOKE `L…/EntityFluidInteraction;<init>(L…/Set;)V` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → checkForCustomFluids | "isInLiquid" | RETURN | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → handleCustomFluidInteractionUpdates | "updateFluidInteraction" | RETURN | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyExpressionValue → checkIfInSwimmableFluid | "updateSwimming" | INVOKE `L…/Entity;isInWater()Z` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyExpressionValue → checkCustomFluids | "isVisuallyCrawling" | INVOKE `L…/Entity;isInWater()Z` | — | default |
| mixins | fluid.EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | @ModifyReturnValue → preventParticlesInFluids | "canSpawnSprintParticle" | RETURN | — | default |
| mixins | fluid.FloatGoalMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.ai.goal.FloatGoal | @ModifyReturnValue → floatInCustomFluids | "canUse" | RETURN | — | default |
| mixins | fluid.LivingEntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Invoker → callJumpOutOfFluid | (from name callJumpOutOfFluid) |  | — | default |
| mixins | fluid.LivingEntityAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Invoker → callGetWaterSlowDown | (from name callGetWaterSlowDown) |  | — | default |
| mixins | fluid.LivingEntityMixin | 26.3 (26.3) | n.m.world.entity.LivingEntity | @WrapWithCondition → travelInCustomFluid | "travelInFluid" | INVOKE `L…/LivingEntity;travelInLava(L…/Vec3;DZD)V` | — | default |
| mixins | fluid.LivingEntityMixin | 26.3 (26.3) | n.m.world.entity.LivingEntity | @Inject → travelFlyingInCustomFluid | "travelFlying(L…/Vec3;FFF)V" | HEAD cancellable=true | — | default |
| mixins | fluid.LivingEntityMixin | 26.3 (26.3) | n.m.world.entity.LivingEntity | @ModifyExpressionValue → fixDoubleUpdateForCustomFluids | "checkFallDamage" | INVOKE `L…/LivingEntity;isInWater()Z` | — | default |
| mixins | fluid.SwimMixin | 26.3 (26.3) | n.m.world.entity.ai.behavior.Swim | @ModifyReturnValue → floatInCustomFluids | "checkExtraStartConditions" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | FabricatedDataMapHooksMixin | 26.3 (26.3) | neo.common.DataMapHooks | @ModifyReturnValue → fabricated$nextStage | "getNextOxidizedStage" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | 26.3 (26.3) | neo.common.DataMapHooks | @ModifyReturnValue → fabricated$previousStage | "getPreviousOxidizedStage" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | 26.3 (26.3) | neo.common.DataMapHooks | @ModifyReturnValue → fabricated$waxed | "getBlockWaxed" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | 26.3 (26.3) | neo.common.DataMapHooks | @ModifyReturnValue → fabricated$unwaxed | "getBlockUnwaxed" | RETURN | — | default |

### `mc/neoforge/common12111/src/main/resources/fabricated-fabric-api.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.networking.accessor` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ChunkMapAccessor | 1.21.11 (1.21.11) | n.m.server.level.ChunkMap | @Accessor → getEntityTrackers | "entityMap" |  | — | default |
| mixins | ChunkMapAccessor | common26 (26.1.2,26.2,26.3) | n.m.server.level.ChunkMap | @Accessor → getEntityMap | (from name getEntityMap) |  | — | default |
| mixins | EntityTrackerAccessor | 1.21.11 (1.21.11) | n.m.server.level.ChunkMap$TrackedEntity | @Accessor → getPlayersTracking | "seenBy" |  | — | default |
| mixins | EntityTrackerAccessor | common26 (26.1.2,26.2,26.3) | n.m.server.level.ChunkMap$TrackedEntity | @Accessor → getSeenBy | (from name getSeenBy) |  | — | default |
| mixins | ServerCommonPacketListenerImplAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Accessor → getServer | (from name getServer) |  | — | default |
| mixins | ServerLoginPacketListenerImplAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerLoginPacketListenerImpl | @Accessor → getServer | (from name getServer) |  | — | default |
| mixins | ServerLoginPacketListenerImplAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerLoginPacketListenerImpl | @Accessor → getConnection | (from name getConnection) |  | — | default |

### `mc/neoforge/common12111/src/main/resources/fabricated-lookup.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.lookup` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockEntityTypeAccessor | 1.21.11/fabricapi (1.21.11) | n.m.world.level.block.entity.BlockEntityType | @Accessor → getBlocks | "validBlocks" |  | — | default |
| mixins | BlockEntityTypeAccessor | 26.1.2/fabricapi (26.1.2) | n.m.world.level.block.entity.BlockEntityType | @Accessor → getBlocks | "validBlocks" |  | — | default |
| mixins | BlockEntityTypeAccessor | 26.2/fabricapi (26.2) | n.m.world.level.block.entity.BlockEntityType | @Accessor → getBlocks | "validBlocks" |  | — | default |
| mixins | BlockEntityTypeAccessor | 26.3/fabricapi (26.3) | n.m.world.level.block.entity.BlockEntityType | @Accessor → getBlocks | "validBlocks" |  | — | default |
| mixins | ServerLevelMixin | 1.21.11/fabricapi (1.21.11) | n.m.server.level.ServerLevel | no injectors; implements ServerWorldCache; adds methods fabric_invalidateCache, fabric_registerCache; @Unique fields apiLookupCaches, apiLookupAccessesWithoutCleanup | | | | |
| mixins | ServerLevelMixin | 26.1.2/fabricapi (26.1.2) | n.m.server.level.ServerLevel | no injectors; implements ServerLevelCache; adds methods fabric_invalidateCache, fabric_registerCache; @Unique fields apiLookupCaches, apiLookupAccessesWithoutCleanup | | | | |
| mixins | ServerLevelMixin | 26.2/fabricapi (26.2) | n.m.server.level.ServerLevel | no injectors; implements ServerLevelCache; adds methods fabric_invalidateCache, fabric_registerCache; @Unique fields apiLookupCaches, apiLookupAccessesWithoutCleanup | | | | |
| mixins | ServerLevelMixin | 26.3/fabricapi (26.3) | n.m.server.level.ServerLevel | no injectors; implements ServerLevelCache; adds methods fabric_invalidateCache, fabric_registerCache; @Unique fields apiLookupCaches, apiLookupAccessesWithoutCleanup | | | | |

### `mc/neoforge/common12111/src/main/resources/fabricated-loot.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.loot` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getCondition | "condition" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolBuilderMixin | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods apply, conditionally, self, with | | | | |
| mixins | LootPoolBuilderMixin | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, apply, self, when | | | | |
| mixins | LootPoolBuilderMixin | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, apply, self, when | | | | |
| mixins | LootTableAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequenceId | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableBuilderMixin | common121 (1.21.1,1.21.11) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |
| mixins | LootTableBuilderMixin | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |
| mixins | LootTableBuilderMixin | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |
| mixins | LootTableMixin | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable prio=3000 | @WrapMethod → fabric$modifyDrops | "getRandomItemsRaw(L…/LootContext;L…/Consumer;)V" |  | — | default |
| mixins | LootTableMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.storage.loot.LootTable prio=3000 | @WrapMethod → fabric$modifyDrops | "getRandomItemsRaw(L…/LootContext;L…/Consumer;)V" |  | — | default |

### `mc/neoforge/common26/src/main/resources/fabricated-transfer.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.transfer` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AbstractFurnaceBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → setStackSuppressUpdate | "setItem" | HEAD cancellable=true | — | default |
| mixins | BaseContainerBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.BaseContainerBlockEntity | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |
| mixins | BucketItemAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.item.BucketItem | @Accessor → fabric_getContent | "content" |  | — | default |
| mixins | BucketItemMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.BucketItem | @ModifyVariable → hookEmptyingSound | "playEmptySound" | STORE name="soundEvent" | — | default |
| mixins | BundleContentsAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.item.component.BundleContents | @Invoker → getWeight | "getWeight" |  | — | default |
| mixins | ChiseledBookShelfBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.ChiseledBookShelfBlockEntity | @Inject → setStackBypass | "setItem" | HEAD cancellable=true | — | default |
| mixins | CompoundContainerAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.CompoundContainer | @Accessor → fabric_getContainer1 | "container1" |  | — | default |
| mixins | CompoundContainerAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.CompoundContainer | @Accessor → fabric_getContainer2 | "container2" |  | — | default |
| mixins | CrafterBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.CrafterBlock | @Inject → transferOrSpawnStack | "dispenseItem" | INVOKE `L…/ItemStack;isEmpty()Z`  sugar:@Local(name = "direction"),@Local(name = "into"),@Local(name = "remaining") | — | default |
| mixins | DropperBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.DropperBlock | @Inject → hookDispense | "dispenseFrom" | INVOKE `L…/DispenseItemBehavior;dispense(L…/BlockSource;L…/ItemStack;)L…/ItemStack;` cancellable=true | — | allow=1 |
| mixins | FluidMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @Inject → hookGetBucketFillSound | "getPickupSound" | HEAD cancellable=true | — | default |
| mixins | ItemContainerContentsAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.item.component.ItemContainerContents | @Accessor → fabric_getItems | "items" |  | — | default |
| mixins | ItemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.Item | no injectors; implements ItemVariantCache; adds methods fabric_getCachedItemVariant; @Unique fields cachedItemVariant | | | | |
| mixins | ItemStackAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.item.ItemStack | @Invoker → validateComponents | "validateComponents" |  | — | default |
| mixins | JukeboxBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.JukeboxBlockEntity | @Inject → setStackBypass | "setTheItem" | HEAD cancellable=true | — | default |
| mixins | ListBackedContainerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.ListBackedContainer | @WrapOperation → cancelSetChanged | "setItem" | INVOKE `L…/ListBackedContainer;setChanged()V` | — | default |
| mixins | ShelfBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.ShelfBlockEntity | no injectors; implements SpecialLogicContainer, SpecialLogicAccess; adds methods fabric_onFinalCommit, fabric_setSuppress, fabric_shouldSuppressSpecialLogic; @Unique fields fabric_suppressSpecialLogic | | | | |
| mixins | SimpleContainerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.SimpleContainer | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |

### `mc/neoforge/common26/src/main/resources/fabricated.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.neoforge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | EntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.Entity | no injectors; implements EntityLoadData, EntityLoadDataSetter; adds methods fabric_setLoadedFromDisk, fabric_setSpawnReason, isLoadedFromDisk, spawnReason; @Unique fields fabricated$loadedFromDisk, fabricated$spawnReason | | | | |
| mixins | ConnectionMixin | common26 (26.1.2,26.2,26.3) | n.m.network.Connection | no injectors; implements PacketContextProvider; adds methods getPacketContext; @Unique fields fabricated$packetContext | | | | |
| mixins | ServerCommonPacketListenerImplMixin | common26 (26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | no injectors; implements PacketContextProvider; adds methods getPacketContext | | | | |
| mixins | ServerPlayerMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerPlayer | no injectors; implements PacketContextProvider; adds methods getPacketContext | | | | |
| mixins | ServerLoginPacketListenerImplMixin | common26 (26.1.2,26.2,26.3) | n.m.server.network.ServerLoginPacketListenerImpl | @Inject → fabricated$fillPacketContext; implements PacketContextProvider; adds methods getPacketContext | "<init>" | TAIL | — | default |
| mixins | ServerLoginPacketListenerImplMixin | common26 (26.1.2,26.2,26.3) | n.m.server.network.ServerLoginPacketListenerImpl | @Inject → fabricated$storeGameProfile | "finishLoginAndWaitForClient" | HEAD | — | default |
| mixins | ServerConfigurationPacketListenerImplMixin | common26 (26.1.2,26.2,26.3) | n.m.server.network.ServerConfigurationPacketListenerImpl | no injectors; implements FabricServerConfigurationPacketListenerImpl; adds methods addTask, completeTask | | | | |
| mixins | MinecraftServerMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | no injectors; implements DataResourceStore; adds methods getOrThrow | | | | |
| mixins | PlayerListViewMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.players.PlayerList | @ModifyReturnValue → fabricated$viewOfCurrentList (NeoForge's unmodifiable view follows the list in the field, POLY-1) | "getPlayers" | RETURN | — | default |
| mixins | ChunkGeneratorFeaturesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.ChunkGenerator | @Inject → fabricated$keepNeoForgesLazy | "refreshFeaturesPerStep" | HEAD | — | require=0 |
| mixins | CrashReportMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.CrashReport | @Inject → fabricated$recordMixinFailure (records a Fabric mod's mixin the crash's throwable says failed as Mixin applied it, for the next launch to leave out, APPLY-1) | "<init>(L…/String;L…/Throwable;)V" | TAIL | — | default |
| mixins | UtilMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.util.Util | @Inject → fabricated$recordMixinFailure (records a Fabric mod's mixin the throwable a game worker thread stops on says failed as Mixin applied it, for the next launch to leave out, APPLY-2) | "onThreadException(L…/Thread;L…/Throwable;)V" | HEAD | — | require=0 |
| mixins | ReloadableServerResourcesMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerResources | no injectors; implements FabricDataResourceStoreHolder; adds methods fabric$getDataResourceStore; @Unique fields fabricated$dataResourceStore | | | | |
| mixins | ResourceMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.Resource | no injectors; implements FabricResource; adds methods getFabricPackSource | | | | |
| mixins | MappedRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | no injectors; implements FabricRegistry | | | | |
| mixins | MappedRegistryEarlyWriteMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepPendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$restorePendingHolders | "freeze" | RETURN | — | default |
| mixins | MappedRegistryFrozenTagsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepBoundTags (NeoForge's freeze leaves the tags `freeze` bound); @Shadow frozen | "bindAllTagsToEmpty" | HEAD cancellable=true | — | default |
| mixins | BuiltInRegistriesFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints (the bootstrap's `freeze` waits for the main entrypoints, as on Fabric, CORPUS-32) | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | GameDataFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.GameData | @WrapMethod → fabricated$freezeBuiltInRegistries (runs the waiting `freeze` in NeoForge's first freeze after the entrypoints) | "freezeData" |  | — | default |
| mixins | RegistryManagerLateRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.RegistryManager | @Inject → fabricated$allowFabricInitializers (lets a registry through NeoForge's tracking after `NewRegistryEvent` while a Fabric entrypoint runs, PROVIDER-6) | "trackModdedRegistry" | HEAD | — | default |
| mixins | DefaultAttributesValidationMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | PackRepositorySourcesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (the sources set made mutable again before NeoForge adds a pack finder, PACK-1) | "addPackFinder" (NeoForge's) | HEAD | — | default |
| mixins | MobEffectMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.effect.MobEffect | no injectors; implements FabricMobEffect | | | | |
| mixins | ExecuteCommandEffectMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.commands.execution.tasks.ExecuteCommand | @WrapOperation → fabricated$runExecutable (an effect a command gives comes from that command in Fabric's effect events, where upstream hooks Brigadier, EFFECT-1) | "execute" | INVOKE `Lcom/mojang/brigadier/context/ContextChain;runExecutable(…)I` | — | default |
| mixins | EntityTypeBuilderMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.EntityType$Builder | @Inject → fabricated$applyChildBuilders | "build" | RETURN | — | default |
| mixins | BlockEntityTypeMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.BlockEntityType | no injectors; implements FabricBlockEntityType; adds methods addValidBlock | | | | |
| mixins | PoiTypesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | BlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.Block | no injectors; implements FabricBlock; adds methods getAppearance, getEnchantPowerBonus, makesOpenTrapdoorAboveClimbable | | | | |
| mixins | BlockStateMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockState | no injectors; implements FabricBlockState; adds methods getAppearance | | | | |
| mixins | BlockBehaviourPropertiesMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$Properties | no injectors; implements FabricBlock.FabricProperties; adds methods blockId | | | | |
| mixins | ItemMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.Item | @Inject → fabricated$onConstruct | "<init>" | RETURN | — | default |
| mixins | ItemDamageMixin | 26.3 (26.3) | n.m.world.item.Item | no injectors; implements IItemExtension, ItemExtensions; adds methods damageItem | | | | |
| mixins | ItemDamageMixin | common26early (26.1.2,26.2) | n.m.world.item.Item | no injectors; implements IItemExtension, ItemExtensions; adds methods damageItem | | | | |
| mixins | ItemPropertiesMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.Item$Properties | no injectors; implements FabricItem.Properties; adds methods itemId, modelId | | | | |
| mixins | ItemStackMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack | | | | |
| mixins | ItemStackRemainderMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack, ItemInstanceExtension; adds methods getCraftingRemainder | | | | |
| mixins | TooltipFlagMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.TooltipFlag | no injectors; implements FabricTooltipFlag | | | | |
| mixins | DataComponentMapBuilderMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.core.component.DataComponentMap$Builder | no injectors; implements FabricComponentMapBuilder; adds methods contains, getOrCreate, getOrEmpty | | | | |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | HEAD | — | default |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | RETURN | — | default |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeVanillaRemoval (reads the state and block entity before vanilla's removal where Fabricated puts it back, SEAM-3) | "destroyBlock" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | require=0 |
| mixins | ServerPlayerGameModeMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterVanillaRemoval (fires `PlayerBlockBreakEvents.AFTER` at vanilla's `Block.destroy` put back, SEAM-3) | "destroyBlock" | INVOKE `L…/Block;destroy(L…/LevelAccessor;L…/BlockPos;L…/BlockState;)V` | — | require=0 |
| mixins | BlockStateBaseMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | @Inject → fabricated$useWithoutItem | "useWithoutItem" | HEAD cancellable=true | — | default |
| mixins | ItemStackUseMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | @WrapOperation → fabricated$use | "use" | INVOKE `L…/Item;use(L…/Level;L…/Player;L…/InteractionHand;)L…/InteractionResult;` | — | default |
| mixins | PickItemMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromBlock | "handlePickItemFromBlock" | INVOKE `L…/BlockState;getCloneItemStack(L…/BlockPos;L…/LevelReader;ZL…/Player;)L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | PickItemMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromEntity | "handlePickItemFromEntity" | INVOKE `L…/Entity;getPickResult()L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | FakePlayerMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | fapi.api.entity.FakePlayer | no injectors; implements IPlayerExtension; adds methods isFakePlayer | | | | |
| mixins | PlayerAdvancementsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$keepRealOwner | "setPlayer" | HEAD cancellable=true | — | default |
| mixins | PlayerAdvancementsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$noFakeAdvancements | "award" | HEAD cancellable=true | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @Inject → fabricated$startReload | "reload" | HEAD  sugar:@Local(argsOnly = true) | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | VibrationSystemMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.gameevent.vibrations.VibrationSystem | @ModifyReturnValue → fabricated$tableFrequency | "getGameEventFrequency(L…/Holder;)I" | RETURN | — | default |
| mixins | NetworkAddonHolderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl<br>n.m.server.network.ServerLoginPacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| mixins | BlockApiCacheInvalidationMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerLevel | @Inject → fabricated$invalidateBlockApiCaches | "invalidateCapabilities(L…/BlockPos;)V" | TAIL | — | default |
| mixins | LivingEntityEffectsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectAdded | "onEffectAdded" | RETURN | — | default |
| mixins | LivingEntityEffectsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectsRemoved | "onEffectsRemoved" | RETURN | — | default |
| mixins | LivingEntityFluidTravelMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$throughFabricFluid (cancellable) | "shouldTravelInFluid" | RETURN | — | default |
| mixins | FluidTypeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" (NeoForge's) | INVOKE `L…/CommonHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | ServerPayloadSendMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;Lio/netty/channel/ChannelFutureListener;)V" | HEAD cancellable=true | — | default |
| mixins | ServerCommonPacketListenerDisconnectMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| mixins | NetworkRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$clientboundCodec (also Fabric codecs of `minecraft` payloads, NET-4) | "getCodec" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryAccessor | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor fabricated$registrations, fabricated$setup; @Accessor (setter) fabricated$setSetup | | | | |
| mixins | NetworkRegistryHandlersAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor → fabricated$serverboundHandlers | "SERVERBOUND_HANDLERS" |  | — | default |
| mixins | NetworkRegistryHandlersAccessor | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor → fabricated$clientboundHandlers | "CLIENTBOUND_HANDLERS" |  | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToClient | "checkPacket(L…/Packet;L…/ServerCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToServer | "checkPacket(L…/Packet;L…/ClientCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | VanillaNamespacePayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ServerboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| mixins | GenericPacketSplitterMeasureMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapOperation → fabricated$keepMeasuredBytes (@Local argsOnly ChannelHandlerContext) | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" | INVOKE `L…/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V` | — | default |
| mixins | GenericPacketSplitterMeasureMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$dropWhatIsNotHandedOn | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" |  | — | default |
| mixins | PacketEncoderMeasuredMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.network.PacketEncoder | @WrapOperation → fabricated$writeMeasuredBytes (@Local argsOnly ChannelHandlerContext) | "encode(L…/ChannelHandlerContext;L…/Packet;L…/ByteBuf;)V" | INVOKE `L…/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V` | — | default |
| mixins | PacketDecoderContextMixin | common26 (26.1.2,26.2,26.3) | n.m.network.PacketDecoder | @WrapMethod → fabricated$withPacketContext | "decode" |  | — | default |
| mixins | PacketEncoderContextMixin | common26 (26.1.2,26.2,26.3) | n.m.network.PacketEncoder | @WrapMethod → fabricated$withPacketContext | "encode(L…/ChannelHandlerContext;L…/Packet;L…/ByteBuf;)V" |  | — | default |
| mixins | GenericPacketSplitterContextMixin | common26 (26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$encodeWithPacketContext | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" |  | — | default |
| mixins | GenericPacketSplitterContextMixin | common26 (26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$decodeWithPacketContext | "handle" |  | — | default |
| mixins | EntityFluidInteractionTagMixin | common26 (applies on 26.2, 26.3; `@Pseudo`, 26.1 has no target) | n.m.world.entity.EntityFluidInteraction | @Inject → fabricated$typeOfModTag (cancellable) | "getFluidTypeByTag" | HEAD | — | default |
| mixins | MenuProviderMixin | common26 (26.1.2,26.2,26.3) | n.m.world.MenuProvider | no injectors; implements FabricMenuProvider | | | | |
| mixins | ServerPlayerMenuMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | common26 (26.1.2,26.2,26.3) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
| mixins | PackOverlaysMixin | 26.3 (26.3) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, none for a pack declaring NeoForge's, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;Z)V` index=3  sugar:@Local(argsOnly = true), @Local PackMetadataResources | — | default |
| mixins | PackOverlaysMixin | common26early (26.1.2,26.2) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, none for a pack declaring NeoForge's, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;Z)V` index=3  sugar:@Local(argsOnly = true), @Local PackResources | — | default |
| mixins | ResourcePackLoaderMixin | common26 (26.1.2,26.2,26.3) | neo.resource.ResourcePackLoader | @WrapOperation → fabricated$leaveOutUnreadableFabricPack (a Fabric mod's pack whose pack.mcmeta can't be read is left out with NeoForge's own error, PACK-12) | "packFinder" | INVOKE `L…/ResourcePackLoader;readWithOptionalMeta(L…/PackLocationInfo;L…/Pack$ResourcesSupplier;L…/PackType;L…/PackSelectionConfig;)L…/Pack;`  sugar:@Local | — | default |
| mixins | AdvancementReloadMixin | 26.3 (26.3) | n.m.server.ReloadableServerRegistries | @WrapOperation → fabricated$advancementReload | "reload" | INVOKE `L…/RegistryDataLoader;load(L…/ResourceManager;L…/List;L…/List;L…/Executor;L…/List;)L…/CompletableFuture;`  sugar:@Local(name = "loadingContextWithTags") | — | default |
| mixins | AdvancementReloadMixin | common26early (26.1.2,26.2) | n.m.server.ReloadableServerRegistries | no injectors | | | | |
| mixins | EnchantmentBuilderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.item.enchantment.Enchantment$Builder | @Inject → fabricated$markModified | "*" | RETURN | — | default |
| mixins | ResourceManagerRegistryLoadTaskMixin | common26 (26.1.2,26.2,26.3) | n.m.resources.ResourceManagerRegistryLoadTask | @Inject → fabricated$captureRegistries | "load" | HEAD | — | default |
| mixins | ResourceManagerRegistryLoadTaskMixin | common26 (26.1.2,26.2,26.3) | n.m.resources.ResourceManagerRegistryLoadTask | @WrapOperation → fabricated$modify | "lambda$load$2" | NEW `net/minecraft/resources/RegistryLoadTask$PendingRegistration`  sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$blockEntityLoaded | "setBlockEntity" | INVOKE `L…/Map;put(…)L…/Object;` sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @WrapWithCondition → fabricated$blockEntityUnloaded | "setBlockEntity", "removeBlockEntity" | INVOKE `L…/BlockEntity;setRemoved()V` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$removedBlockEntityDropped | "getBlockEntity(L…/BlockPos;L…/LevelChunk$EntityCreationType;)L…/BlockEntity;" | INVOKE `L…/Map;remove(L…/Object;)L…/Object;` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @Inject → fabricated$blockEntitiesReplaced | "replaceWithPacketData" | HEAD | — | default |
| mixins | ReloadableResourceManagerMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$reopenListeners (a listener registered the vanilla way after NeoForge sorted the listeners is added after them) | "registerReloadListener" | HEAD | — | default |
| client | client.ClientCommonPacketListenerImplMixin | common26 (26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | no injectors; implements PacketContextProvider; adds methods getPacketContext | | | | |
| client | client.LocalPlayerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.player.LocalPlayer | no injectors; implements PacketContextProvider; adds methods getPacketContext | | | | |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onCreated | "<init>" | TAIL | — | default |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| client | client.ClientReloadListenerMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$holdBackEarlyListener (cancellable) | "registerReloadListener" | HEAD | — | default |
| client | client.KeyMappingAccessor | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.KeyMapping | @Accessor → fabricated$getClickCount | "clickCount" |  | — | default |
| client | client.MinecraftEntrypointsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypoints | "<init>" | INVOKE `L…/Thread;currentThread()L…/Thread;` | 0 | require=0 |
| client | client.MinecraftEntrypointsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypointsLate | "<init>" | TAIL | — | default |
| client | client.MinecraftStartedMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$clientStarted | "run" | FIELD `L…/Minecraft;gameThread:L…/Thread;` opcode=Opcodes.PUTFIELD shift=At.Shift.AFTER | 0 | require=0 |
| client | client.ModelManagerExtraModelsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.client.resources.model.ModelManager | @Inject → fabricated$fabricExtraModels | "discoverModelDependencies(L…/Map;L…/BlockStateModelLoader$LoadedModels;L…/ClientItemInfoLoader$LoadedClientInfos;L…/StandaloneModelLoader$LoadedModels;)L…/ModelManager$ResolvedModels;" | INVOKE `L…/ModelDiscovery;resolve()L…/Map;`  sugar:@Local | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$beforeDestroyBlock | "destroyBlock" | HEAD | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$afterDestroyBlock | "destroyBlock" | RETURN | — | default |
| client | client.MouseHandlerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.MouseHandler | @WrapOperation → fabricated$scrollHotbar | "onScroll" | INVOKE `L…/Inventory;setSelectedSlot(I)V` | — | default |
| client | client.ClientNetworkAddonHolderMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl<br>n.m.client.multiplayer.ClientHandshakePacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| client | client.ClientPayloadSendMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;)V" | HEAD cancellable=true | — | default |
| client | client.ClientVanillaNamespacePayloadMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ClientboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| client | client.ClientHandshakePacketContextMixin | common26 (26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientHandshakePacketListenerImpl | @Inject → fabricated$storeGameProfile | "handleLoginFinished" | HEAD | — | default |
| client | client.ClientPacketListenerContextMixin | common26 (26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientPacketListener | @Inject → fabricated$storeRegistries | "<init>" | TAIL | — | default |
| client | client.SoundEngineStreamMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.sounds.SoundEngine | @WrapOperation → fabricated$fabricAudioStream | "play" | INVOKE `L…/SoundInstance;getStream(L…/SoundBufferLibrary;L…/Sound;Z)L…/CompletableFuture;` | — | default |
| client | client.ParticleResourcesMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.client.particle.ParticleResources | no injectors; non-@Unique fields providers | | | | |
| client | ClientChunkCacheMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @ModifyExpressionValue → fabricated$unloadOutOfRange | "updateViewRadius" | INVOKE `L…/ClientChunkCache$Storage;inRange(II)Z` sugar:@Local LevelChunk | — | default |
| client | ClientChunkCacheMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @Inject → fabricated$unloadReplaced | "replaceWithPacketData" | NEW `L…/LevelChunk` sugar:@Local LevelChunk | — | default |
| client | OptionsDefaultPacksMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Options | @Inject → fabricated$defaultEnabledPacks | "loadSelectedResourcePacks(L…/PackRepository;)V" | HEAD | — | default |
| client | SectionCompilerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true),@Local(name = "startedLayers"); implements FabricBlockRendering.SectionLayers | — | default |
| client | MovingBlockRenderMixin | 26.1.2 | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "renderMovingBlockSubmits" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true) BufferSource, OptionsRenderState, @Local(name = "poseStack"); implements FabricBlockRendering.MovingBlockBuffers | — | default |
| client | MovingBlockRenderMixin | 26.2, 26.3 (one file each) | n.m.client.renderer.feature.MovingBlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "buildGroup" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true) FeatureFrameContext, @Local(name = "submit"); extends RenderTypeFeatureRenderer; implements FabricBlockRendering.MovingBlockBuffers | — | default |
| client | BlockBreakingRenderMixin | 26.1.2 | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "renderBreakingBlockModelSubmits" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's overload) | — | default |
| client | BlockBreakingRenderMixin | common26late (26.2,26.3) | n.m.client.renderer.LevelRenderer | @WrapWithCondition → fabricated$fabricModels | "submitBlockDestroyAnimation(L…/PoseStack;L…/SubmitNodeCollector;L…/LevelRenderState;)V" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's overload) | — | default |
| client | BlockStateModelWrapperMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.model.BlockStateModelWrapper | @Inject → fabricated$fabricModels (a Fabric model fills the render state of a block drawn as an entity with its quads, through Fabric's mesh, instead of NeoForge's parts; upstream's @Overwrite, which takes every model, is left out by the plan); @Shadow model, transformation, updateTints | "update(L…/BlockModelRenderState;L…/BlockState;L…/BlockDisplayContext;J)V" | HEAD cancellable=true | — | default |
| client | client.GuiRendererPictureInPictureMixin | 26.1.2 | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$prepareModRenderer (vanilla's map of picture-in-picture renderers given back, PIP-1); non-@Unique field pictureInPictureRenderers | "preparePictureInPictureState" | HEAD cancellable=true | — | default |
| client | client.GuiRendererPictureInPictureMixin | 26.1.2 | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$closeModRenderers | "close" | TAIL | — | default |
| client | client.GuiRendererPictureInPictureMixin | common26late (26.2,26.3) | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$prepareModRenderer (vanilla's map of picture-in-picture renderers given back, PIP-1); non-@Unique field pictureInPictureRenderers | "preparePictureInPictureState" | HEAD cancellable=true | — | default |
| client | client.GuiRendererPictureInPictureMixin | common26late (26.2,26.3) | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$closeModRenderers | "close" | TAIL | — | default |
| client | MultiPartModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.multipart.MultiPartModel | no injectors; implements FabricBlockRendering.Composite; @Shadow shared, blockState; @Unique fabricated$state (cached per broken-class generation) | | | | |
| client | WeightedVariantsMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$state (cached per broken-class generation) | | | | |
| client | UnbakedModelParserMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.client.model.UnbakedModelParser$Deserializer | @Inject → fabricated$fabricTypes | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/UnbakedModel;" (with descriptor: the class also has the compiler's bridge method) | HEAD cancellable=true | — | default |
| client | BlockStateModelUnbakedMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModel$Unbaked (interface) | @ModifyExpressionValue → fabricated$fabricTypes | "<clinit>" | INVOKE `L…/Codec;flatComapMap(L…/Function;L…/Function;)L…/Codec;`, slice from PUTSTATIC `HARDCODED_WEIGHTED_CODEC` | — | default |

### `mc/neoforge/common26/src/main/resources/fabricated-renderer.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.neoforge.rendering.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · loaded only when `fabric_renderer_api_v1` (Fabric API's renderer module) is a loaded mod (`requiredMods` in `mc/neoforge/common/src/main/resources/META-INF/neoforge.mods.toml`), so its classes may name Fabric's renderer API.

| Section | Mixin | Versions | Target | Injector → handler | Method selector | @At | ordinal | require |
|---|---|---|---|---|---|---|---|---|
| client | BlockStateModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModel (interface) | no injectors; extends FabricBlockStateModel; adds default emitQuads (FrapiDefaults: NeoForge's level-aware collectParts) | | | | |
| client | BlockStateModelPartMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModelPart (interface) | no injectors; extends FabricBlockStateModelPart; adds default emitQuads (FrapiDefaults: NeoForge's part and quad ambient occlusion) | | | | |
| client | MutableQuadViewAccessor | common26 (26.1.2,26.2,26.3) | Indigo's MutableQuadViewImpl (`@Pseudo`, `targets`: a mod's) | @Accessor transformStack → fabricated$transformStack | | | | default |
| client | WrapperBlockStateModelMixin | common26 (26.1.2,26.2,26.3) | Fabric API's WrapperBlockStateModel (`@Pseudo`, `targets`: a mod's) | @WrapMethod → fabricated$holdWrappedThrow | "emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V" | holds a wrapped model's throw while one of Fabricated's draws of a Fabric model is under way (HeldThrows, CORPUS-56) | — | require=0 |

### `mc/neoforge/common26early/src/main/resources/fabricated-biome.mixins.json`

Versions: 26.1.2, 26.2 · package `net.fabricmc.fabric.mixin.biome` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · registered through `version_mixin_configs` in the `gradle.properties` of `mc/neoforge/26.1.2` and `mc/neoforge/26.2`

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BiomeAccessor | common26early (26.1.2,26.2) | n.m.world.level.biome.Biome | @Accessor → fabricated$setAttributes | "attributes" |  | — | default |
| mixins | BiomeSourceMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.BiomeSource | @Redirect → getBiomes; adds method modifyBiomeSet (@Unique, overridden by TheEndBiomeSourceMixin) | "possibleBiomes" | INVOKE `L…/Supplier;get()L…/Object;` | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | common26early (26.1.2,26.2) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setFoliageColorOverride | "foliageColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | common26early (26.1.2,26.2) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setDryFoliageColorOverride | "dryFoliageColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | common26early (26.1.2,26.2) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setGrassColorOverride | "grassColorOverride" |  | — | default |
| mixins | ClimateSamplerMixin | common26early (26.1.2,26.2) | n.m.world.level.biome.Climate$Sampler | no injectors; implements MultiNoiseSamplerHooks; adds methods fabric_setSeed, fabric_getSeed, fabric_getEndBiomesSampler; @Unique fields seed, endBiomesSampler | | | | |
| mixins | MappedRegistryAccessor | common26early (26.1.2,26.2) | n.m.core.MappedRegistry | @Accessor → fabricated$getRegistrationInfos | "registrationInfos" |  | — | default |
| mixins | MultiNoiseBiomeSourceMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSource | no injectors; implements BiomeSourceAccess; adds methods fabric_setModifyBiomeEntries, fabric_shouldModifyBiomeEntries; @Unique fields modifyBiomeEntries | | | | |
| mixins | NetherBiomePresetMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$1 (`targets`) | @Inject → apply | "apply" | RETURN cancellable=true | — | default |
| mixins | NoiseChunkMixin | common26early (26.1.2,26.2) | n.m.world.level.levelgen.NoiseChunk | @Inject → init; @Unique fields seed | "<init>" | TAIL | — | default |
| mixins | NoiseChunkMixin | common26early (26.1.2,26.2) | n.m.world.level.levelgen.NoiseChunk | @Inject → createMultiNoiseSampler | "cachedClimateSampler" | RETURN | — | default |
| mixins | RandomStateMixin | common26early (26.1.2,26.2) | n.m.world.level.levelgen.RandomState | @Inject → init; @Shadow sampler | "<init>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → modifyCodec; extends BiomeSourceMixin; @Shadow @Mutable CODEC; @Unique fields overrides, biomeSetModified, hasCheckedForModifiedSet | "<clinit>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → rememberLookup | "create" | HEAD | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → clearLookup | "create" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → init | "<init>" | RETURN | — | default |
| mixins | TheEndBiomeSourceMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.biome.TheEndBiomeSource | @Inject → getWeightedEndBiome | "getNoiseBiome" | RETURN cancellable=true | — | default |

### Test-mod configs (not shipped in the Fabricated jar)

### `mc/shared/1.20.1+/src/testmod/resources/fabricated-testmod.mixins.json`

Versions: 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.mixin` · required=true · defaultRequire=1 · plugin=`TestMixinPlugin` (checks the test mod's language adapter ran first) · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockRemovalMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @WrapOperation → fabricatedTest$removeBlock (leaves tuff where calcite breaks, as Wilder Wild leaves snow; counts the calls; SEAM-3) | "destroyBlock" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | default |
| mixins | BuiltInRegistriesMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Inject → fabricatedTest$buildContent (builds content as vanilla's bootstrap creates the registries' contents, CORPUS-31) | "createContents" | TAIL | — | default |
| mixins | BuiltInRegistriesMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Inject → fabricatedTest$frozen (sees the built-in registries frozen, as Wover does, CORPUS-32) | "freeze" | HEAD | — | default |
| mixins | ChiseledBookShelfBlockMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.ChiseledBookShelfBlock | @Inject → fabricatedTest$supertype (static; takes the `Property` vanilla's lambda takes, which NeoForge's recompiled game, 21.11 and later, takes as a `BooleanProperty`: through pre-flight's bridge there, LAMBDA-11) | `TestCompat.PROPERTY_LAMBDA` (vanilla's `lambda$createBlockStateDefinition$N` with its descriptor) | HEAD | — | default |
| mixins | DestroyProgressMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour | @WrapOperation → fabricatedTest$mining (lets a bare hand mine stone as fast as the right tool while the check asks, as Apoli's harvest power does; counts the calls; SEAM-4) | "getDestroyProgress" | INVOKE `L…/Player;hasCorrectToolForDrops(L…/BlockState;)Z` | — | default |
| mixins | BlockDropsMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.Block | @Inject → fabricatedTest$popped (records what a block is asked to drop while the harvest check runs, which reads drops from it, not from the item entities a chunk it has just loaded may not show yet; SEAM-4) | "popResource(L…/Level;L…/BlockPos;L…/ItemStack;)V" | HEAD | — | default |
| mixins | FrictionMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.item.ItemEntity | @WrapOperation → fabricatedTest$friction (makes packed ice rough while the check asks it to, as FrozenLib's friction API and Artifacts' snowshoes change the friction; counts the calls; SEAM-8) | "tick" | INVOKE `L…/Block;getFriction()F` | — | default |
| mixins | HarvestCheckMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @WrapOperation → fabricatedTest$harvest (lets a bare hand harvest stone, as Apoli's and Porting Lib's harvest hooks change the answer; counts the calls; SEAM-4) | "destroyBlock" | INVOKE `L…/ServerPlayer;hasCorrectToolForDrops(L…/BlockState;)Z` | — | default |
| mixins | LivingEntityMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricatedTest$useSelf; adds a @Unique private self(), the helper Fabric API's entity events module has (HIDE-1) | "getMaxHealth" | HEAD | — | default |
| mixins | LivingEntitySelfMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | none; adds `DefaultBridgeTest.OwnSelf`, whose default `self()` is a second one beside NeoForge's `ILivingEntityExtension.self()`, for the default bridge (CORPUS-11, CORPUS-63) | — | — | — | — |
| mixins | LootTableBuilderNamingMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.storage.loot.LootTable$Builder | @Inject → fabricatedTest$named; adds `LootNamedAsBuilt` (names the table it builds where MODIFY asked, as Porting Lib Loot does, CORPUS-90) | "build" | RETURN | — | default |
| mixins | MappedRegistryMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricatedTest$freezing (sees a registry frozen, as Flashback does; never before the main initializers, REG-8) | "freeze" | HEAD | — | default |
| mixins | MinecraftServerMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$afterTick | "tickServer" | TAIL | — | default |
| mixins | MobMultipartMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.Mob | none; adds `DefaultBridgeTest.OwnMultipart`, whose default `isMultipartEntity()` is a second one beside the one `Mob` has through `Entity`'s NeoForge extension (`IEntityExtension`), for the default bridge through a superclass (DEFAULT-1) | — | — | — | — |
| mixins | PackRepositoryMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository prio=900 | @Inject → fabricatedTest$immutableSources (leaves the sources in an immutable set, PACK-1, before the hooks of the default priority) | "<init>" | RETURN | — | default |
| mixins | PlayerListMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.players.PlayerList | @Inject → fabricatedTest$replaceList (replaces the player list, POLY-1) | "<init>" | RETURN | — | default |
| mixins | PluginRejectedMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | none; a @Shadow of a field no game has: pre-flight leaves it out whole, and the test mod's plugin turns it down, so the report lists it apart (LEAVE-4) | — | — | — | — |
| mixins | PreflightLeftOutMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | none; a @Shadow of a field no game has: pre-flight leaves it out whole, the test mod's plugin would apply it, and the plugin as translated turns it down (LEAVE-4) | — | — | — | — |
| mixins | PreflightShapesMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$static (static on a method that takes `this`; on Forge 47 through pre-flight's bridge, LAMBDA-12) | "tickServer" | HEAD | — | default |
| mixins | RecordedFramesMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | io.github.cmdralph.fabricated.testmod.RecordedFramesTest$Probe | @ModifyVariable → fabricatedTest$kept (the probe's StringBuilder after a chop, by type and ordinal: found where Mixin counts the locals there are, CORPUS-79; nowhere on NeoForge, as on Fabric, FRAME-1) | "probe" | INVOKE `L…/String;valueOf(L…/Object;)L…/String;` remap=false | 0 | require=0 |
| mixins | RegistryDataLoaderMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.resources.RegistryDataLoader | @Inject → fabricatedTest$writeEarly (writes to built-in registries, REG-3) | "<clinit>" | HEAD | — | default |
| mixins | ShearsCheckMixin | shared 1.20.1-1.21.11/testmod (1.20.1,1.21.1,1.21.11), 26.x/testmod (26.1.2,26.2,26.3; vanilla's call is `TypedInstance.is(T)`, called as `is(Object)`) | n.m.world.level.block.TripWireBlock | @WrapOperation → fabricatedTest$shears (lets a stick count as shears while the check asks it to, as BCLib's and Porting Lib's shears hooks widen the answer; counts the calls; SEAM-6) | "playerWillDestroy" | INVOKE `L…/ItemStack;is(L…/Item;)Z` (26.x: `is(Ljava/lang/Object;)Z`) | — | default |
| mixins | StructureEntitiesMixin | shared 1.20.1-1.21.1/testmod (1.20.1,1.21.1), 1.21.11+/testmod (1.21.11,26.1.2,26.2,26.3; vanilla's call and method take a `ProblemReporter` too) | n.m.world.level.levelgen.structure.templatesystem.StructureTemplate | @Inject → fabricatedTest$beforeEntities (keeps the test's placement settings in a `ThreadLocal`, as YUNG's API and Friends and Foes do, STRUCT-1) | "placeInWorld" | INVOKE `L…/StructureTemplate;placeEntities(…)V` | — | default |
| mixins | StructureEntitiesMixin | shared 1.20.1-1.21.1/testmod, 1.21.11+/testmod (as above) | n.m.world.level.levelgen.structure.templatesystem.StructureTemplate | @Inject → fabricatedTest$entities (vanilla's private method, which every platform replaced: pointed at the platform's, its parameters through a bridge, STRUCT-1) | "placeEntities" | HEAD cancellable=true | — | default |
| mixins | StructureEntitiesMixin | shared 1.20.1-1.21.1/testmod, 1.21.11+/testmod (as above) | n.m.world.level.levelgen.structure.templatesystem.StructureTemplate | @Inject → fabricatedTest$afterEntities | "placeInWorld" | INVOKE `L…/StructureTemplate;placeEntities(…)V` shift=AFTER | — | default |
| mixins | Vec3iInstanceofMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.Vec3i | @ModifyConstant → fabricatedTest$isVec3i (the `instanceof Vec3i`, its handler taking the object and the class, PRE-29) | "equals" | constant classValue=`Vec3i` | 0 | default |
| server | LoaderHookMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.Main | @Inject → fabricatedTest$afterLoaderHook (static; after Fabric Loader's server hook, as owo-lib's registry freeze: pointed where the platform loads mods, HOOK-1, HOOK-4) | "main" | INVOKE `Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V` shift=AFTER remap=false | — | default |
| client | client.MinecraftStopMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricatedTest$whoStops (CI diagnostics) | "stop" | HEAD | — | default |
| client | client.MinecraftEarlyReloadListenerMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricatedTest$registerEarly (a reload listener registered before NeoForge's event) | "<init>" | INVOKE `L…/ReloadableResourceManager;registerReloadListener(L…/PreparableReloadListener;)V` | 0 | default |
| client | client.McRuntimeTestTickHookMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | me.earth.mc_runtime_test.McRuntimeTest (`targets`) (@Pseudo) | @Inject → fabricatedTest$holdForTheServer (static; skips mc-runtime-test's tick while `ClientHold.holding()`: its count is up and the integrated server hasn't run as many ticks with the player, 60 seconds at most, CI-15) | "tickHook" remap=false | HEAD cancellable=true | — | default |

### `mc/shared/1.20.1+/src/testmod/resources/fabricated-testmod-mixinsemantics.mixins.json`

Versions: 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.mixinsemantics` · required=true · defaultRequire=1 · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | MinecraftServerCounterMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$count; adds a @Unique tick counter (CORPUS-74) | "tickServer" | HEAD | — | default |
| mixins | MinecraftServerCounterShadowMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer (priority 1200) | @Inject → fabricatedTest$checkShared; @Shadow of the counter the other mixin adds (CORPUS-74) | "tickServer" | TAIL | — | default |
| mixins | MinecraftServerUniqueMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$countOne; a @Unique field of the twin's name (CORPUS-95) | "tickServer" | HEAD | — | default |
| mixins | MinecraftServerUniqueTwinMixin | shared 1.20.1+/testmod (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$countTen, fabricatedTest$checkOwn; a @Unique field of the other's name (CORPUS-95) | "tickServer" | HEAD, TAIL | — | default |

### `mc/shared/1.20.1+/src/testmod/resources/fabricated-testmod-vanilla.mixins.json`

Versions: 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.vanilla` · required=true · defaultRequire=1 · its refmap on Forge 47 (forge-legacy)

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | PistonStickinessMixin | shared 1.20.1+/testmodvanilla (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.piston.PistonStructureResolver | @WrapOperation → fabricatedTest$askedSticky (counts the calls, as FrozenLib's and Moonlight's hooks wrap them) | "resolve", "addBlockLine" | INVOKE `L…/PistonStructureResolver;isSticky(L…/BlockState;)Z` | — | default |
| mixins | PistonStickinessMixin | shared 1.20.1+/testmodvanilla (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.piston.PistonStructureResolver | @WrapOperation → fabricatedTest$stick (smooth basalt sticks to all but calcite) | "addBlockLine", "addBranchingBlocks" | INVOKE `L…/PistonStructureResolver;canStickToEachOther(L…/BlockState;L…/BlockState;)Z` | — | default |
| mixins | PistonStickyStartMixin | shared 1.20.1+/testmodvanilla (1.20.1,1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.piston.PistonStructureResolver prio=900 | @Inject → fabricatedTest$sticky (static; smooth basalt is sticky, as Carpet's hook, SEAM-1; applied, and so run, before the default priority's callbacks at the method's start, Carpet 1.20.1's among them) | "isSticky" | HEAD, cancellable | — | default |

### `mc/shared/26.x/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.frapi.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BakedQuadMixin | shared 26.x/testmodfrapi (26.1.2,26.2,26.3) | n.m.c.resources.model.geometry.BakedQuad | @Inject → fabricatedTest$mark (moved by pre-flight to NeoForge's constructor through a bridge) | "<init>" | RETURN | — | default |

### `mc/neoforge/1.21.1/src/testmod/resources/fabricated-testmod-neoforge.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.testmod.neoforge` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ConditionRereadMixin | 1.21.1/testmod (1.21.1) | n.m.server.packs.resources.SimplePreparableReloadListener | @Inject → fabricatedTest$readConditionsAgain (drops a JSON data loader's file still carrying `fabric:load_conditions` whose `fabric:all_mods_loaded` names a mod NeoForge's mod list hasn't, as Moonlight's NeoForge build does, while fabric-resource-conditions-api-v1 is Fabric API's own, COND-1) | "lambda$reload$1" | HEAD | — | default |
| mixins | ConditionsAsPreparedMixin | 1.21.1/testmod (1.21.1) | n.m.server.packs.resources.SimplePreparableReloadListener | @Inject → fabricatedTest$asPrepared, fabricatedTest$leftAsTheyAre (priority 100: the test mod's kept file as prepared, then, as its loader applies them, whether the file whose conditions upstream can't parse keeps them and the kept file as prepared keeps its own, COND-1); @Unique field fabricatedTest$keptAsPrepared | "lambda$reload$1" | HEAD, TAIL | — | default |
| client | HudLayersMixin | 1.21.1/testmod (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedTest$addLayers (a layer put into the first stack's vanilla list at index 3, as Apoli's, and one replacing the second's first layer, pointed by pre-flight at Fabricated's seam `fabricated$read$layers`, HUD-7) | "<init>" | FIELD `L…/Gui;layers:L…/LayeredDraw;` opcode=GETFIELD sugar:@Local(ordinal=0) LayeredDraw, @Local(ordinal=1) LayeredDraw | — | default |
| client | HudLayersMixin | 1.21.1/testmod (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedTest$start, fabricatedTest$end (what a drawing of the HUD drew, by NeoForge's layer names; hides the GUI for a few frames once); @Unique fields fabricatedTest$drawn, fabricatedTest$shown, fabricatedTest$hiddenFrames, fabricatedTest$drawnHidden, fabricatedTest$reported | "render" | HEAD, TAIL | — | default |
| client | HudLayersMixin | 1.21.1/testmod (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedTest$demo (whether vanilla's demo overlay layer, which the test replaced, still drew) | "renderDemoOverlay" | HEAD | — | default |
| client | LayeredDrawLayersAccessor | 1.21.1/testmod (1.21.1) | n.m.client.gui.LayeredDraw | @Accessor → fabricatedTest$layers | "layers" |  | — | default |

### `mc/shared/src/testmodbroken/resources/badmixin/fabricated-testmod-badmixin.mixins.json`

Versions: all · package `io.github.cmdralph.fabricated.testmod.broken.mixin` · required=true · defaultRequire=unset (Mixin default 0) · plugin=none · overwrites.requireAnnotations=unset

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ImpossibleMixin | common/testmodbroken (test only) | n.m.server.MinecraftServer | @Inject → fabricatedTest$never | "fabricatedMethodThatDoesNotExist" | HEAD | — | require=1 |
| mixins | UnmatchedCallMixin | common/testmodbroken (test only) | n.m.server.MinecraftServer | @Inject → fabricatedTest$never | "tickServer" | INVOKE `L…/MinecraftServer;fabricatedCallThatIsNeverMade()V` | — | require=1 |

### `mc/shared/src/testmodbroken/resources/badplugin/fabricated-testmod-badplugin.mixins.json`

Versions: all · package `io.github.cmdralph.fabricated.testmod.broken.plugin` · required=true · defaultRequire=unset (Mixin default 0) · plugin=`FailingPlugin` · overwrites.requireAnnotations=unset

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LeftOutMixin | common/testmodbroken (test only) | n.m.server.MinecraftServer | none; a @Shadow of a field no game has: pre-flight leaves it out whole, and its config's plugin fails to load, so Mixin applies it, inert as translated (LEAVE-4) | — | — | — | — |

## Forge configs

The Forge branches' configs, the test mods' included: `forge` (Forge 61 to 66, Minecraft 1.21.11 and 26.x) and
`forge-legacy` (Forge 47 and 52, Minecraft 1.20.1 and 1.21.1). Generated from those branches' sources on 2026-11-08
(task 6), Forge 52's on 2026-11-09, in the per-config tables' format above; `scripts/check-mixin-inventory.py` checks
them on each branch, where the configs are. A version takes a config, and each mixin class, from its own folder
(`mc/<platform>/<version>`), else from the first folder its `shared_sources` lists that has it, `mc/forge-shared` among
them, with that folder's version groups that serve the version (`mc/forge-shared/1.21.1+` for 1.21.1 and later). `forge.` = `net.minecraftforge.`;
"—" in a configs table means the key is unset. Only `fabricated-loot` and the Forge 61 to 66 renderer test mod set
`overwrites.requireAnnotations`, which every NeoForge config sets: in the others, a method a mixin adds that its
target already declares replaces the target's without an `@Overwrite` (MIX-3).

## Forge 61 to 66 (`forge`): 89 mixins in 24 configs

| Config file | Package | Versions | Sections | defaultRequire | overwrites.requireAnnotations | plugin |
|---|---|---|---|---|---|---|
| `mc/forge/1.21.11/src/main/resources/fabricated-forge-color.mixins.json` | `io.github.cmdralph.fabricated.forge.color.mixin` | 1.21.11 | client 1 | 1 | — | none |
| `mc/forge/1.21.11/src/main/resources/fabricated-forge-conditions.mixins.json` | `io.github.cmdralph.fabricated.forge.resource.mixin` | 1.21.11 | mixins 2 | 1 | — | none |
| `mc/forge/1.21.11/src/main/resources/fabricated-forge-damage.mixins.json` | `io.github.cmdralph.fabricated.forge.damage.mixin` | 1.21.11 | mixins 1 | 1 | — | none |
| `mc/forge/1.21.11/src/main/resources/fabricated-forge-frapi.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.mixin` | 1.21.11 | client 11 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge/1.21.11/src/main/resources/fabricated-forge-loot.mixins.json` | `io.github.cmdralph.fabricated.forge.loot.mixin` | 1.21.11 | mixins 2 | 1 | — | none |
| `mc/forge/1.21.11/src/main/resources/fabricated-forge-particle.mixins.json` | `io.github.cmdralph.fabricated.forge.particle.mixin` | 1.21.11 | client 1 | 1 | — | none |
| `mc/forge/1.21.11/src/main/resources/fabricated-keybinding.mixins.json` | `net.fabricmc.fabric.mixin.client.keybinding` | 1.21.11 | client 1 | 1 | — | none |
| `mc/forge/26.1.2/src/main/resources/fabricated-forge-damage.mixins.json` | `io.github.cmdralph.fabricated.forge.damage.mixin` | 26.1.2 | mixins 1 | 1 | — | none |
| `mc/forge/26.1.2/src/main/resources/fabricated-forge-frapi-breaking.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.breaking` | 26.1.2 | client 1 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge/26.2/src/main/resources/fabricated-forge-frapi-breaking.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.breaking` | 26.2 | client 1 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge/26.3/src/main/resources/fabricated-forge-advancement.mixins.json` | `io.github.cmdralph.fabricated.forge.advancement.mixin` | 26.3 | mixins 1 | 1 | — | none |
| `mc/forge/26.3/src/main/resources/fabricated-forge-frapi-breaking.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.breaking` | 26.3 | client 1 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge/26.3/src/main/resources/fabricated-forge-loot.mixins.json` | `io.github.cmdralph.fabricated.forge.loot.mixin` | 26.3 | mixins 4 | 1 | — | none |
| `mc/forge/common/src/main/resources/fabricated-forge-entity.mixins.json` | `io.github.cmdralph.fabricated.forge.entity.mixin` | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 2 | 1 | — | none |
| `mc/forge/common/src/main/resources/fabricated-forge-interaction.mixins.json` | `io.github.cmdralph.fabricated.forge.interaction.mixin` | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 1 | 1 | — | none |
| `mc/forge/common/src/main/resources/fabricated-forge-screen.mixins.json` | `io.github.cmdralph.fabricated.forge.screen.mixin` | 1.21.11, 26.1.2, 26.2, 26.3 | client 2 | 1 | — | none |
| `mc/forge/common/src/main/resources/fabricated-forge.mixins.json` | `io.github.cmdralph.fabricated.forge.mixin` | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 25 | 1 | — | `FabricatedMixinPlugin` |
| `mc/forge/common/src/main/resources/fabricated-loot.mixins.json` | `net.fabricmc.fabric.mixin.loot` | 1.21.11, 26.1.2, 26.2, 26.3 | mixins 5 | 1 | true | none |
| `mc/forge/common/src/testmod/resources/fabricated-forge-testmod.mixins.json` | `io.github.cmdralph.fabricated.forgetest.mixin` | 1.21.11, 26.1.2, 26.2, 26.3 | client 1 | 1 | — | none |
| `mc/forge/common26/src/main/resources/fabricated-forge-frapi.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.mixin` | 26.1.2, 26.2, 26.3 | client 10 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge/common26/src/main/resources/fabricated-forge-screen-chars.mixins.json` | `io.github.cmdralph.fabricated.forge.screen.chars.mixin` | 26.1.2, 26.2, 26.3 | client 1 | 1 | — | none |
| `mc/forge/common26/src/main/resources/fabricated-keymapping.mixins.json` | `net.fabricmc.fabric.mixin.client.keymapping` | 26.1.2, 26.2, 26.3 | client 1 | 1 | — | none |
| `mc/forge/common26/src/testmodfrapi/resources/fabricated-testmod-frapi-forge.mixins.json` | `io.github.cmdralph.fabricated.testmod.frapi.forgemixin` | 26.1.2, 26.2, 26.3 | client 3 | 1 | true | none |
| `mc/forge/common26early/src/main/resources/fabricated-forge-loot.mixins.json` | `io.github.cmdralph.fabricated.forge.loot.mixin` | 26.1.2, 26.2 | mixins 2 | 1 | — | none |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-color.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.color.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockColorsLookupMixin | mc/forge-shared/1.20.1-1.21.11 (1.21.11) | n.m.client.color.block.BlockColors | no injectors; adds methods get; @Shadow blockColors | | | | |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-conditions.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.resource.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ConditionCodecMixin | 1.21.11 (1.21.11) | forge.common.crafting.conditions.ConditionCodec | @ModifyReturnValue → fabricated$fabricConditions | "wrap(Lcom/mojang/serialization/Decoder;)Lcom/mojang/serialization/Decoder;" | RETURN | — | default |
| mixins | RegistryOpsAccessor | 1.21.11 (1.21.11) | n.m.resources.RegistryOps | @Accessor → fabricated$lookupProvider | "lookupProvider" |  | — | default |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-damage.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.damage.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LivingEntityDamageMixin | common (1.21.11) | n.m.world.entity.LivingEntity | @WrapMethod → fabricated$keepOuterDamage; @Unique fields fabricated$damageDealt, fabricated$blocked | "hurtServer" |  | — | default |
| mixins | LivingEntityDamageMixin | common (1.21.11) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$noteDamageDealt | "hurtServer" | INVOKE `L…/LivingEntity;applyItemBlocking(L…/ServerLevel;L…/DamageSource;F)F` | — | default |
| mixins | LivingEntityDamageMixin | common (1.21.11) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterDamage | "hurtServer" | TAIL | — | default |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-item.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.item.mixin` · required=true · defaultRequire=1 · plugin=— · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ItemForgeHooksMixin | mc/forge-shared/1.21.1+ (1.21.11) | n.m.world.item.Item | no injectors; adds methods getEquipmentSlot, getCreatorModId (Forge's item hooks, answered from Fabric's item API) | | | | |
| mixins | ItemEnchantingHookMixin | common (1.21.11) | n.m.world.item.Item | no injectors; adds method canApplyAtEnchantingTable(ItemStack, Holder) (Forge's, answered from Fabric's item API) | | | | |
| mixins | ItemRecipeRemainderMixin | 1.21.11 (1.21.11) | n.m.world.item.Item | no injectors; adds method getCraftingRemainder(ItemStack) (Forge's, answered from Fabric's getRecipeRemainder) | | | | |
| mixins | ItemBuiltComponentsMixin | mc/forge-shared/1.21.x (1.21.11) | n.m.world.item.Item | @Inject → fabricated$buildAgainOnceChanged; @Shadow components, builtComponents; @Unique fields fabricated$builtFrom | "components()Lnet/minecraft/core/component/DataComponentMap;" | HEAD | — | default |

### `mc/forge/common26/src/main/resources/fabricated-forge-item.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.item.mixin` · required=true · defaultRequire=1 · plugin=— · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ItemForgeHooksMixin | mc/forge-shared/1.21.1+ (26.1.2, 26.2, 26.3) | n.m.world.item.Item | no injectors; adds methods getEquipmentSlot, getCreatorModId (Forge's item hooks, answered from Fabric's item API) | | | | |
| mixins | ItemEnchantingHookMixin | common (26.1.2, 26.2, 26.3) | n.m.world.item.Item | no injectors; adds method canApplyAtEnchantingTable(ItemStack, Holder) (Forge's, answered from Fabric's item API) | | | | |

### `mc/forge-shared/1.21.1+/src/main/resources/fabricated-forge-block.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.block.mixin` · required=true · defaultRequire=1 · plugin=— · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockForgeHooksMixin | mc/forge-shared/1.21.1+ (1.21.11, 26.1.2, 26.2, 26.3) | n.m.world.level.block.Block | no injectors; adds method makesOpenTrapdoorAboveClimbable (Forge's ladder rule, or a block in Fabric's trapdoor tag) | | | | |

### `mc/forge/common26late/src/main/resources/fabricated-forge-block-late.mixins.json`

Versions: 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.block.late.mixin` · required=true · defaultRequire=1 · plugin=— · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockEnchantPowerMixin | common26late (26.2, 26.3) | n.m.world.level.block.Block | no injectors; adds method getEnchantPowerBonus (Fabric's provided enchantment power for a block in the power-provider tag) | | | | |
| mixins | LiquidBlockFlowMixin | common26late (26.2, 26.3) | n.m.world.level.block.LiquidBlock | @WrapOperation → fabricated$allowFlow | "onPlace", "neighborChanged" | INVOKE `L…/FluidInteractionRegistry;canInteract(L…/Level;L…/BlockPos;)Z` | — | require=2 |

### `mc/forge/26.3/src/main/resources/fabricated-forge-biome.mixins.json`

Versions: 26.3 · package `io.github.cmdralph.fabricated.forge.biome.mixin` · required=true · defaultRequire=1 · plugin=— · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ModifiableBiomeInfoAttributesMixin | 26.3 (26.3) | n.mf.common.world.ModifiableBiomeInfo (remap=false) | @Inject → fabricated$keepAttributes | "applyBiomeModifiers" | HEAD | — | default |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-frapi.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.rendering.mixin` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | IndigoTerrainMixin | 1.21.11 (1.21.11) | fapi.impl.client.indigo.renderer.render.TerrainRenderContext (`targets`) (@Pseudo) | @Redirect → fabricated$guardEmit | "bufferModel" | INVOKE `L…/BlockStateModel;emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V` | — | default |
| client | IndigoTerrainLikeMixin | 1.21.11 (1.21.11) | fapi.impl.client.indigo.renderer.render.TerrainLikeRenderContext (`targets`) (@Pseudo) | @Redirect → fabricated$guardEmit | "bufferModel" | INVOKE `L…/BlockStateModel;emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V` | — | default |
| client | IndigoSimpleBlockMixin | 1.21.11 (1.21.11) | fapi.impl.client.indigo.renderer.render.SimpleBlockRenderContext (`targets`) (@Pseudo) | @Redirect → fabricated$guardEmit | "bufferModel" | INVOKE `L…/BlockStateModel;emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V` | — | default |
| client | IndigoEmitterMixin | 1.21.11 (1.21.11) | fapi.impl.client.indigo.renderer.mesh.MutableQuadViewImpl (`targets`) (@Pseudo) | no injectors; implements FabricBlockRendering.TransformDepth; adds methods fabricated$transformDepth, fabricated$clear; @Shadow transformStack, clear | | | | |
| client | MultiPartModelMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.multipart.MultiPartModel | @Inject → fabricated$keepShared; implements FabricBlockRendering.Composite; adds methods fabricated$state, fabricated$parts, fabricated$emitEach; @Shadow blockState; @Unique fields fabricated$shared, fabricated$state | "<init>" | RETURN | — | default |
| client | MultiPartSharedStateMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.multipart.MultiPartModel$SharedBakedState (`targets`) | no injectors; implements FabricBlockRendering.ModelSelector; adds methods fabricated$selectModels; @Shadow selectModels | | | | |
| client | WeightedVariantsMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; adds methods fabricated$state, fabricated$parts, fabricated$emitEach; @Shadow list; @Unique fields fabricated$state | | | | |
| client | MovingBlockMixin | 1.21.11 (1.21.11) | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapOperation → fabricated$drawMovingBlock | "render" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(L…/BlockAndTintGetter;L…/List;L…/BlockState;L…/BlockPos;L…/PoseStack;L…/VertexConsumer;ZI)V` @Local | — | default |
| client | BlockRenderDispatcherMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapOperation → fabricated$drawBreakingOverlay; @Shadow blockColors, getBlockModel | "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(L…/BlockAndTintGetter;L…/List;L…/BlockState;L…/BlockPos;L…/PoseStack;L…/VertexConsumer;ZI)V` | — | default |
| client | BlockRenderDispatcherMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.BlockRenderDispatcher | @Inject → fabricated$drawAsEntity | "renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/rendertype/RenderType;)V" | HEAD cancellable=true | — | default |
| client | ScreenEffectParticleMixin | 1.21.11 (1.21.11) | n.m.client.renderer.ScreenEffectRenderer | @Inject → fabricated$keepOverlayBlock; @Unique fields fabricated$overlay, fabricated$overlayLevel | "getOverlayBlock" | RETURN | — | default |
| client | ScreenEffectParticleMixin | 1.21.11 (1.21.11) | n.m.client.renderer.ScreenEffectRenderer | @WrapOperation → fabricated$levelAwareSprite | "renderScreenEffect" | INVOKE `L…/BlockModelShaper;getParticleIcon(L…/BlockState;)L…/TextureAtlasSprite;` | — | default |
| client | TerrainParticleSpriteMixin | 1.21.11 (1.21.11) | n.m.client.particle.TerrainParticle | @WrapOperation → fabricated$levelAwareSprite | "updateSprite" | INVOKE `L…/BlockModelShaper;getParticleIcon(L…/BlockState;L…/Level;L…/BlockPos;)L…/TextureAtlasSprite;` | — | default |
| client | BlockStateModelDefaultMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.BlockStateModel | no injectors; adds the default method emitQuads (a model without its own collects its parts with Forge's model data and the level) | | | | |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-loot.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.loot.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LoadingRegistriesMixin | 1.21.11 (1.21.11) | n.m.server.ReloadableServerRegistries | @WrapOperation → fabricated$noteLoadingRegistries | "reload" | INVOKE `L…/HolderLookup$Provider;createSerializationContext(L…/DynamicOps;)L…/RegistryOps;` | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (1.21.11) | n.m.server.ReloadableServerRegistries | @ModifyVariable → fabricated$startReload | "reload" | HEAD | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (1.21.11) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN | — | default |

### `mc/forge/1.21.11/src/main/resources/fabricated-forge-particle.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.forge.particle.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | TerrainParticleTintMixin | 1.21.11 (1.21.11) | n.m.client.particle.TerrainParticle | @WrapOperation → fabricated$allowBlockDustTint | "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V" | INVOKE `L…/IClientBlockExtensions;areBreakingParticlesTinted(L…/BlockState;L…/ClientLevel;L…/BlockPos;)Z` | — | default |

### `mc/forge/1.21.11/src/main/resources/fabricated-keybinding.mixins.json`

Versions: 1.21.11 · package `net.fabricmc.fabric.mixin.client.keybinding` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | KeyMappingCategoryMixin | 1.21.11 (1.21.11) | n.m.client.KeyMapping$Category | @Inject → onReturnRegister; @Shadow SORT_ORDER | "register(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/KeyMapping$Category;" | RETURN | — | default |

### `mc/forge/26.1.2/src/main/resources/fabricated-forge-damage.mixins.json`

Versions: 26.1.2 · package `io.github.cmdralph.fabricated.forge.damage.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LivingEntityDamageMixin | common (26.1.2) | n.m.world.entity.LivingEntity | @WrapMethod → fabricated$keepOuterDamage; @Unique fields fabricated$damageDealt, fabricated$blocked | "hurtServer" |  | — | default |
| mixins | LivingEntityDamageMixin | common (26.1.2) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$noteDamageDealt | "hurtServer" | INVOKE `L…/LivingEntity;applyItemBlocking(L…/ServerLevel;L…/DamageSource;F)F` | — | default |
| mixins | LivingEntityDamageMixin | common (26.1.2) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterDamage | "hurtServer" | TAIL | — | default |

### `mc/forge/26.1.2/src/main/resources/fabricated-forge-frapi-breaking.mixins.json`

Versions: 26.1.2 · package `io.github.cmdralph.fabricated.forge.rendering.breaking` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BreakingOverlayMixin | 26.1.2 (26.1.2) | n.m.client.renderer.feature.BlockFeatureRenderer prio=1100 | @Redirect → fabricated$guardEmit | "renderBreakingBlockModelSubmits" | INVOKE `L…/BlockStateModel;emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V` | — | require=0 |

### `mc/forge/26.2/src/main/resources/fabricated-forge-frapi-breaking.mixins.json`

Versions: 26.2 · package `io.github.cmdralph.fabricated.forge.rendering.breaking` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BreakingOverlayMixin | 26.2 (26.2) | n.m.client.renderer.LevelRenderer | @WrapOperation → fabricated$guard | "submitBlockDestroyAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V" | INVOKE `L…/SubmitNodeCollector;submitBreakingBlockModel(L…/PoseStack;L…/List;I)V` @Local | — | default |

### `mc/forge/26.3/src/main/resources/fabricated-forge-advancement.mixins.json`

Versions: 26.3 · package `io.github.cmdralph.fabricated.forge.advancement.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ResourceManagerRegistryLoadTaskMixin | 26.3 (26.3) | n.m.resources.ResourceManagerRegistryLoadTask | @WrapOperation → fabricated$modifyAdvancement; @Shadow resourceManager | "/^lambda\$load\$\d+$/" | INVOKE `L…/RegistryLoadTask$PendingRegistration;loadFromResource(L…/Decoder;L…/RegistryOps;L…/ResourceKey;L…/Resource;)L…/Either;` | — | default |

### `mc/forge/26.3/src/main/resources/fabricated-forge-frapi-breaking.mixins.json`

Versions: 26.3 · package `io.github.cmdralph.fabricated.forge.rendering.breaking` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BreakingOverlayMixin | 26.3 (26.3) | n.m.client.renderer.LevelRenderer | @WrapOperation → fabricated$guard | "submitBlockDestroyAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V" | INVOKE `L…/SubmitNodeCollector;submitBreakingBlockModel(L…/PoseStack;L…/List;IZ)V` @Local | — | default |

### `mc/forge/26.3/src/main/resources/fabricated-forge-loot.mixins.json`

Versions: 26.3 · package `io.github.cmdralph.fabricated.forge.loot.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ForgeHooksLootTableNameMixin | 26.3 (26.3) | forge.common.ForgeHooks | @WrapOperation → fabricated$nameOnce | "onJsonDataParsed" | INVOKE `L…/LootTable;setLootTableId(L…/Identifier;)V` | — | default |
| mixins | LoadingRegistriesMixin | 26.3 (26.3) | n.m.server.ReloadableServerRegistries | @WrapOperation → fabricated$noteLoadingRegistries | "reload" | INVOKE `L…/RegistryDataLoader;load(L…/ResourceManager;L…/List;L…/List;L…/Executor;)L…/CompletableFuture;` | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (26.3) | n.m.server.ReloadableServerRegistries | @ModifyVariable → fabricated$startReload | "reload" | HEAD | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (26.3) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN | — | default |
| mixins | ResourceManagerRegistryLoadTaskMixin | 26.3 (26.3) | n.m.resources.ResourceManagerRegistryLoadTask | @WrapOperation → fabricated$modifyLootTable | "/^lambda\$load\$\d+$/" | INVOKE `L…/RegistryLoadTask$PendingRegistration;loadFromResource(L…/Decoder;L…/RegistryOps;L…/ResourceKey;L…/Resource;)L…/Either;` | — | default |

### `mc/forge/common/src/main/resources/fabricated-forge-entity.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.entity.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ExecuteCommandEffectMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.commands.execution.tasks.ExecuteCommand | @WrapOperation → fabricated$runExecutable | "execute" | INVOKE `L…/ContextChain;runExecutable(L…/CommandContext;L…/Object;L…/ResultConsumer;Z)I` | — | default |
| mixins | LivingEntitySleepMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$setBedOccupied | "startSleeping", "/^lambda\$stopSleeping\$\d+$/" | INVOKE `L…/BlockState;setBedOccupied(L…/Level;L…/BlockPos;L…/LivingEntity;Z)V` | — | require=2 |
| mixins | LivingEntitySleepMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @ModifyReturnValue → fabricated$modifySleepingDirection | "getBedOrientation()Lnet/minecraft/core/Direction;" | RETURN | — | default |

### `mc/forge/common/src/main/resources/fabricated-forge-interaction.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.interaction.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ServerPlayerGameModeBreakMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeBreak; @Shadow level, player | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerPlayer;preventsBlockDrops()Z` cancellable=true | — | default |
| mixins | ServerPlayerGameModeBreakMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @WrapOperation → fabricated$afterBreak | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerPlayerGameMode;removeBlock(L…/BlockPos;Z)Z` | — | default |
| mixins | ServerPlayerGameModeBreakMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeVanillaRemoval (reads the state and block entity before vanilla's removal where Fabricated puts it back, SEAM-3); @Unique fields fabricated$removedState, fabricated$removedBlockEntity | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | require=0 |
| mixins | ServerPlayerGameModeBreakMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterVanillaRemoval (fires `AFTER` at vanilla's `Block.destroy` put back, SEAM-3) | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/Block;destroy(L…/LevelAccessor;L…/BlockPos;L…/BlockState;)V` | — | require=0 |
| mixins | ForgeHooksUseOnMixin | common (1.21.11, 26.1.2, 26.2, 26.3) | n.mf.common.ForgeHooks | @WrapOperation → fabricated$useOnEvent | "onPlaceItemIntoWorld" | INVOKE `L…/Item;useOn(L…/UseOnContext;)L…/InteractionResult;` | — | default |

### `mc/forge/common/src/main/resources/fabricated-forge-screen.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.screen.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | ForgeEventFactoryClientScreenInputMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.client.event.ForgeEventFactoryClient | @WrapOperation → fabricated$mouseClicked | "onScreenMouseClicked(Lnet/minecraft/client/gui/screens/Screen;DDLnet/minecraft/client/input/MouseButtonEvent;Z)Z" | INVOKE `L…/Screen;mouseClicked(L…/MouseButtonEvent;Z)Z` | — | default |
| client | ForgeEventFactoryClientScreenInputMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.client.event.ForgeEventFactoryClient | @WrapOperation → fabricated$mouseReleased | "onScreenMouseReleased(Lnet/minecraft/client/gui/screens/Screen;DDLnet/minecraft/client/input/MouseButtonEvent;)Z" | INVOKE `L…/Screen;mouseReleased(L…/MouseButtonEvent;)Z` | — | default |
| client | ForgeHooksClientScreenInputMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.client.ForgeHooksClient | @WrapOperation → fabricated$keyPressed | "onScreenKeyPressed(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/KeyEvent;)Z" | INVOKE `L…/Screen;keyPressed(L…/KeyEvent;)Z` | — | default |
| client | ForgeHooksClientScreenInputMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.client.ForgeHooksClient | @WrapOperation → fabricated$keyReleased | "onScreenKeyReleased(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/KeyEvent;)Z" | INVOKE `L…/Screen;keyReleased(L…/KeyEvent;)Z` | — | default |
| client | ForgeHooksClientScreenInputMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.client.ForgeHooksClient | @WrapOperation → fabricated$mouseDragged | "onScreenMouseDrag(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/MouseButtonEvent;DD)Z" | INVOKE `L…/Screen;mouseDragged(L…/MouseButtonEvent;DD)Z` | — | default |

### `mc/forge/common/src/main/resources/fabricated-forge.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | DefaultAttributeCopyMixin | common (1.21.11,26.1.2,26.2,26.3) | fapi.impl.object.builder.FabricDefaultAttributeRegistryImpl (`targets`) (@Pseudo) | @Inject → fabricated$copyWithForge | "createFromExistingSupplier" | HEAD cancellable=true | — | default |
| mixins | FluidTypeMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" | INVOKE `L…/ForgeHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | LivingEntityFluidTravelMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$throughFabricFluid | "shouldTravelInFluid" | RETURN cancellable=true | — | default |
| mixins | MappedRegistryPendingHoldersMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$beforeFreeze; implements PendingHolders; adds methods fabricated$keepPendingHolders, fabricated$restorePendingHolders; @Shadow unregisteredIntrusiveHolders, key; @Unique fields fabricated$pendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$afterFreeze | "freeze" | RETURN | — | default |
| mixins | NamespacedWrapperPendingHoldersMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$beforeFreeze | "freeze" | HEAD | — | default |
| mixins | NamespacedWrapperPendingHoldersMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$afterFreeze | "freeze" | RETURN | — | default |
| mixins | NamespacedWrapperEarlyWriteMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @WrapMethod → fabricated$openForEarlyWrite; @Shadow locked, delegate | "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;" |  | — | default |
| mixins | NamespacedWrapperAliasMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$refuseAliasName; adds methods addAlias; @Unique methods fabricated$addToForge; @Shadow locked, delegate; @Unique fields FABRICATED$LOGGER, fabricated$aliases | "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;" | HEAD | — | default |
| mixins | NamespacedWrapperAliasMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @ModifyVariable → fabricated$aliasedName | "get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;", "getValue(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;", "getOptional(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;", "containsKey(Lnet/minecraft/resources/Identifier;)Z", "getHolder(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;" | HEAD | — | default |
| mixins | NamespacedWrapperAliasMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @ModifyVariable → fabricated$aliasedKey | "get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", "getValue(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", "containsKey(Lnet/minecraft/resources/ResourceKey;)Z", "getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;", "registrationInfo(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;" | HEAD | — | default |
| mixins | NamespacedWrapperEntryAddedMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.registries.NamespacedWrapper (`targets`) | @WrapMethod → fabricated$entryAdded; @Shadow delegate | "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;" |  | — | default |
| mixins | ForgeRegistryEntryAddedMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.registries.ForgeRegistry | @WrapMethod → fabricated$entryAdded; @Unique methods fabricated$self, fabricated$fire | "register(Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)V" |  | — | default |
| mixins | ForgeRegistryEntryAddedMixin | common (1.21.11,26.1.2,26.2,26.3) | forge.registries.ForgeRegistry | @WrapMethod → fabricated$entryAddedWithId | "register(ILnet/minecraft/resources/Identifier;Ljava/lang/Object;)V" |  | — | default |
| mixins | ForgeRegistryRekeyMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | forge.registries.ForgeRegistry | @Inject → fabricated$rekeyChangedValues (an entry whose value its map no longer finds, its equality changed since it was registered, is put again before the snapshot, REG-14); @Shadow ids, names, keys, owners; @Unique methods fabricated$rekey | "makeSnapshot" | HEAD | — | default |
| mixins | BuiltInRegistriesFreezeMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | GameDataFreezeMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.registries.GameData | @Inject → fabricated$freezeBuiltInRegistries | "freezeData" | HEAD | — | default |
| mixins | TagFileRemoveMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @Inject → fabricated$readFabricRemove; @Shadow @Mutable CODEC | "<clinit>" | TAIL | — | default |
| mixins | IngredientNetworkCodecMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.crafting.Ingredient | @Inject → fabricated$sendAsUpstream; @Shadow @Mutable CONTENTS_STREAM_CODEC, @Mutable OPTIONAL_CONTENTS_STREAM_CODEC, VANILLA_CONTENTS_STREAM_CODEC | "<clinit>" | TAIL | — | default |
| mixins | ForgePayloadEncodeMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.network.protocol.common.custom.CustomPacketPayload$1 (`targets`) | @WrapOperation → fabricated$writeForgePayload | "writeCap" | INVOKE `L…/StreamCodec;encode(L…/Object;L…/Object;)V` | — | default |
| mixins | ResourcePackLoaderKnownPackMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.resource.ResourcePackLoader | @ModifyArg → fabricated$knownPack | "findPacks" | INVOKE `L…/PackLocationInfo;<init>(L…/String;L…/Component;L…/PackSource;L…/Optional;)V` index=3 | 0 | default |
| mixins | BucketItemEarlyFluidMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.BucketItem | @WrapOperation → fabricated$delegateIfRegistered; @Shadow @Mutable fluidSupplier | "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V" | INVOKE `L…/IForgeRegistry;getDelegateOrThrow(L…/Object;)L…/Holder$Reference;` | — | default |
| mixins | BucketItemEarlyFluidMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.BucketItem | @Inject → fabricated$fluidItselfIfUnregistered | "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V" | TAIL | — | default |
| mixins | PlayerListViewMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.server.players.PlayerList | @ModifyReturnValue → fabricated$viewOfCurrentList (Forge's unmodifiable view follows the list in the field, POLY-2) | "getPlayers" | RETURN | — | default |
| mixins | PoiTypesMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | PackRepositorySourcesMixin | mc/forge-shared (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (a set a Fabric mod made immutable is made mutable again before Forge adds a pack finder, PACK-1, PACK-11); @Shadow @Mutable sources | "addPackFinder" remap=false | HEAD | — | default |
| mixins | PackOverlaysMixin | 26.3 (26.3) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;)V` index=3  sugar:@Local(argsOnly = true), @Local PackMetadataResources | — | default |
| mixins | PackOverlaysMixin | mc/forge-shared/1.21.1-26.2 (1.21.11,26.1.2,26.2) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;Z)V` index=3  sugar:@Local(argsOnly = true), @Local PackResources | — | default |
| mixins | BiomeWeatherWriteMixin | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | fapi.impl.biome.modification.BiomeModificationContextImpl$WeatherContextImpl (`targets`) (@Pseudo) | @Redirect → fabricated$setClimate (the climate into the field and Forge's biome info, BIOME-3) | "setPrecipitation", "setTemperature", "setTemperatureModifier", "setDownfall" | FIELD `L…/Biome;climateSettings:L…/Biome$ClimateSettings;` opcode=PUTFIELD | — | default |
| mixins | BiomeClimateAccessor | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.biome.Biome | @Accessor @Mutable → fabricated$setClimateSettings | "climateSettings" |  | — | default |
| mixins | ModifiableBiomeInfoAccessor | mc/forge-shared/1.21.1+ (1.21.11,26.1.2,26.2,26.3) | forge.common.world.ModifiableBiomeInfo | @Accessor @Mutable → fabricated$setOriginalBiomeInfo; @Accessor → fabricated$setModifiedBiomeInfo | "originalBiomeInfo", "modifiedBiomeInfo" |  | — | default |

### `mc/forge/common/src/main/resources/fabricated-loot.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.loot` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getCondition | "condition" |  | — | default |
| mixins | LootPoolAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootPoolBuilderMixin | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods with, conditionally, apply; @Unique methods self; @Shadow entries, conditions, functions | | | | |
| mixins | LootPoolBuilderMixin | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, when, apply; @Unique methods self; @Shadow entries, conditions, functions | | | | |
| mixins | LootPoolBuilderMixin | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, when, apply; @Unique methods self; @Shadow entries | | | | |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequenceId | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableBuilderMixin | mc/forge-shared/1.21.x (1.21.11) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow @Mutable pools, functions | | | | |
| mixins | LootTableBuilderMixin | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow @Mutable pools, functions | | | | |
| mixins | LootTableBuilderMixin | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow @Mutable pools | | | | |
| mixins | LootTableMixin | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable prio=3000 | @WrapMethod → fabric$modifyDrops; implements FabricLootTable; adds methods fabric$setRegistryEntry; @Unique fields entry | "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V" |  | — | default |
| mixins | LootTableMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.storage.loot.LootTable prio=3000 | @WrapMethod → fabric$modifyDrops; implements FabricLootTable; adds methods fabric$setHolder; @Unique fields holder | "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V" |  | — | default |

### `mc/forge/common/src/testmod/resources/fabricated-forge-testmod.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forgetest.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | HudHooksMixin | 1.21.11 (1.21.11) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheEnd | "render" | TAIL | — | default |
| client | HudHooksMixin | 1.21.11 (1.21.11) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheCrosshair | "render" | INVOKE `L…/Gui;renderCrosshair(L…/GuiGraphics;L…/DeltaTracker;)V` | — | default |
| client | HudHooksMixin | 26.1.2 (26.1.2) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheEnd | "extractRenderState" | TAIL | — | default |
| client | HudHooksMixin | 26.1.2 (26.1.2) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheCrosshair | "extractRenderState" | INVOKE `L…/Gui;extractCrosshair(L…/GuiGraphicsExtractor;L…/DeltaTracker;)V` | — | default |
| client | HudHooksMixin | common26late (26.2,26.3) | n.m.client.gui.Hud | @Inject → fabricatedForgeTest$atTheEnd | "extractRenderState" | TAIL | — | default |
| client | HudHooksMixin | common26late (26.2,26.3) | n.m.client.gui.Hud | @Inject → fabricatedForgeTest$atTheCrosshair | "extractRenderState" | INVOKE `L…/Hud;extractCrosshair(L…/GuiGraphicsExtractor;L…/DeltaTracker;)V` | — | default |

### `mc/forge/common26/src/main/resources/fabricated-forge-frapi.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.rendering.mixin` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | IndigoBlockRendererMixin | common26 (26.1.2,26.2,26.3) | fapi.impl.client.indigo.renderer.render.AltModelBlockRendererImpl (`targets`) (@Pseudo) | @Redirect → fabricated$guardEmit | "tesselateBlock" | INVOKE `L…/BlockStateModel;emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V` | — | default |
| client | IndigoEmitterMixin | common26 (26.1.2,26.2,26.3) | fapi.impl.client.indigo.renderer.mesh.MutableQuadViewImpl (`targets`) (@Pseudo) | no injectors; implements FabricBlockRendering.TransformDepth; adds methods fabricated$transformDepth; @Shadow transformStack | | | | |
| client | MultiPartModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.multipart.MultiPartModel | @Inject → fabricated$keepShared; implements FabricBlockRendering.Composite; adds methods fabricated$state, fabricated$parts, fabricated$emitEach; @Shadow blockState; @Unique fields fabricated$shared, fabricated$state | "<init>" | RETURN | — | default |
| client | MultiPartSharedStateMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.multipart.MultiPartModel$SharedBakedState (`targets`) | no injectors; implements FabricBlockRendering.ModelSelector; adds methods fabricated$selectModels; @Shadow selectModels | | | | |
| client | WeightedVariantsMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; adds methods fabricated$state, fabricated$parts, fabricated$emitEach; @Shadow list; @Unique fields fabricated$state | | | | |
| client | EntityBlockModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.model.BlockStateModelWrapper prio=1100 | @Redirect → fabricated$guardEmit | "update" | INVOKE `L…/BlockStateModel;emitQuads(L…/QuadEmitter;L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/Predicate;)V` | — | require=0 |
| client | ScreenEffectParticleMixin | common26early (26.1.2,26.2) | n.m.client.renderer.ScreenEffectRenderer | @Inject → fabricated$keepOverlayBlock; @Unique fields fabricated$overlay, fabricated$overlayLevel | "getOverlayBlock" | RETURN | — | default |
| client | ScreenEffectParticleMixin | common26early (26.1.2,26.2) | n.m.client.renderer.ScreenEffectRenderer | @WrapOperation → fabricated$levelAwareMaterial | "renderScreenEffect", "submit" | INVOKE `L…/BlockStateModelSet;getParticleMaterial(L…/BlockState;)L…/Material$Baked;` | — | default |
| client | ScreenEffectParticleMixin | 26.3 (26.3) | n.m.client.renderer.extract.LevelExtractor | @WrapOperation → fabricated$keepOverlayBlock; @Unique fields fabricated$overlayState, fabricated$overlayPos, fabricated$overlayLevel | "getOverlayBlock" | INVOKE `L…/BlockState;isViewBlocking(L…/BlockGetter;L…/BlockPos;L…/AABB;)Z` | — | default |
| client | ScreenEffectParticleMixin | 26.3 (26.3) | n.m.client.renderer.extract.LevelExtractor | @WrapOperation → fabricated$levelAwareMaterial | "extractPlayerState" | INVOKE `L…/BlockStateModelSet;getParticleMaterial(L…/BlockState;)L…/Material$Baked;` | — | default |
| client | BlockMarkerParticleMixin | common26 (26.1.2,26.2,26.3) | n.m.client.particle.BlockMarker | @Redirect → fabricated$levelAwareMaterial | "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDLnet/minecraft/world/level/block/state/BlockState;)V" | INVOKE `L…/BlockStateModel;particleMaterial(L…/ModelData;)L…/Material$Baked;` | — | default |
| client | TerrainParticleSpriteMixin | common26 (26.1.2,26.2,26.3) | n.m.client.particle.TerrainParticle | @WrapOperation → fabricated$levelAwareMaterial | "updateSprite" | INVOKE `L…/BlockStateModel;particleMaterial(L…/ModelData;)L…/Material$Baked;` @Local | — | default |
| client | ParticleLevelAccessor | common26 (26.1.2,26.2,26.3) | n.m.client.particle.Particle | @Accessor → fabricated$level | "level" |  | — | default |
| client | BlockStateModelDefaultMixin | common26 (26.1.2, 26.2, 26.3) | n.m.client.renderer.block.model.BlockStateModel | no injectors; adds the default method emitQuads (a model without its own collects its parts with Forge's model data and the level) | | | | |

### `mc/forge/common26/src/main/resources/fabricated-forge-screen-chars.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.forge.screen.chars.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | ForgeHooksClientCharTypedMixin | common26 (26.1.2,26.2,26.3) | forge.client.ForgeHooksClient | @WrapOperation → fabricated$charTyped | "onScreenCharTyped(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/CharacterEvent;)Z" | INVOKE `L…/Screen;charTyped(L…/CharacterEvent;)Z` | — | default |

### `mc/forge/common26/src/main/resources/fabricated-keymapping.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.client.keymapping` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | KeyMappingCategoryMixin | common26 (26.1.2,26.2,26.3) | n.m.client.KeyMapping$Category | @Inject → onReturnRegister; @Shadow SORT_ORDER | "register(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/KeyMapping$Category;" | RETURN | — | default |

### `mc/forge/common26/src/testmodfrapi/resources/fabricated-testmod-frapi-forge.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.frapi.forgemixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | KeyboardHandlerInvoker | common26 (26.1.2,26.2,26.3) | n.m.client.KeyboardHandler | @Invoker → invokeKeyPress | "(from the name)" |  | — | default |
| client | KeyboardHandlerInvoker | common26 (26.1.2,26.2,26.3) | n.m.client.KeyboardHandler | @Invoker → invokeCharTyped | "(from the name)" |  | — | default |
| client | MouseHandlerInvoker | common26 (26.1.2,26.2,26.3) | n.m.client.MouseHandler | @Invoker → invokeOnButton | "(from the name)" |  | — | default |
| client | OverlayProbeMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.extract.LevelExtractor (`targets`) (@Pseudo) | @Inject → fabricatedTest$extracting | "extractPlayerState" | HEAD | — | require=0 |
| client | OverlayProbeMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.extract.LevelExtractor (`targets`) (@Pseudo) | @Inject → fabricatedTest$lookingUp | "getOverlayBlock" | HEAD | — | require=0 |
| client | OverlayProbeMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.extract.LevelExtractor (`targets`) (@Pseudo) | @Inject → fabricatedTest$overlayFound | "getOverlayBlock" | RETURN | — | require=0 |

### `mc/forge/common26early/src/main/resources/fabricated-forge-loot.mixins.json`

Versions: 26.1.2, 26.2 · package `io.github.cmdralph.fabricated.forge.loot.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LoadingRegistriesMixin | common26early (26.1.2,26.2) | n.m.server.ReloadableServerRegistries | @WrapOperation → fabricated$noteLoadingRegistries | "reload" | INVOKE `L…/HolderLookup$Provider;createSerializationContext(L…/DynamicOps;)L…/RegistryOps;` | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (26.1.2,26.2) | n.m.server.ReloadableServerRegistries | @ModifyVariable → fabricated$startReload | "reload" | HEAD | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (26.1.2,26.2) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN | — | default |

## Forge 47 (`forge-legacy`): 84 mixins in 14 configs

| Config file | Package | Versions | Sections | defaultRequire | overwrites.requireAnnotations | plugin |
|---|---|---|---|---|---|---|
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-biome.mixins.json` | `net.fabricmc.fabric.mixin.biome` | 1.20.1 | mixins 9 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-dimension.mixins.json` | `net.fabricmc.fabric.mixin.dimension` | 1.20.1 | mixins 1 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-color.mixins.json` | `io.github.cmdralph.fabricated.forge.color.mixin` | 1.20.1 | client 2 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-content.mixins.json` | `io.github.cmdralph.fabricated.forge.content.mixin` | 1.20.1 | mixins 2 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-entity.mixins.json` | `io.github.cmdralph.fabricated.forge.entity.mixin` | 1.20.1 | mixins 1 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-frapi.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.mixin` | 1.20.1 | client 6 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-interaction.mixins.json` | `io.github.cmdralph.fabricated.forge.interaction.mixin` | 1.20.1 | mixins 1 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-item.mixins.json` | `io.github.cmdralph.fabricated.forge.item.mixin` | 1.20.1 | mixins 2 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge.mixins.json` | `io.github.cmdralph.fabricated.forge.mixin` | 1.20.1 | mixins 30, client 4 | 1 | — | `FabricatedMixinPlugin` |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-keybinding.mixins.json` | `net.fabricmc.fabric.mixin.client.keybinding` | 1.20.1 | client 1 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-loot.mixins.json` | `net.fabricmc.fabric.mixin.loot` | 1.20.1 | mixins 7 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/main/resources/fabricated-particles.mixins.json` | `net.fabricmc.fabric.mixin.client.particle` | 1.20.1 | client 4 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/testmod/resources/fabricated-forge-testmod.mixins.json` | `io.github.cmdralph.fabricated.forgetest.mixin` | 1.20.1 | mixins 12, client 4 | 1 | — | none |
| `mc/forge-legacy/1.20.1/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json` | `io.github.cmdralph.fabricated.testmod.frapi.mixin` | 1.20.1 | client 1 | 1 | — | none |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-biome.mixins.json`

Versions: 1.20.1 · package `net.fabricmc.fabric.mixin.biome` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSource | @Redirect → getBiomes; adds methods fabric_modifyBiomeSet | "possibleBiomes" | INVOKE `L…/Supplier;get()L…/Object;` | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setFoliageColorOverride | "foliageColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setGrassColorOverride | "grassColorOverride" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientParticle | "ambientParticle" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientLoopSoundEvent | "ambientLoopSoundEvent" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientMoodSettings | "ambientMoodSettings" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setAmbientAdditionsSettings | "ambientAdditionsSettings" |  | — | default |
| mixins | BiomeSpecialEffectsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeSpecialEffects$Builder | @Accessor → fabricated$setBackgroundMusic | "backgroundMusic" |  | — | default |
| mixins | ClimateSamplerMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.Climate$Sampler | no injectors; implements MultiNoiseSamplerHooks; adds methods fabric_setSeed, fabric_getSeed, fabric_getEndBiomesSampler; @Unique fields seed, endBiomesSampler | | | | |
| mixins | MobSpawnSettingsBuilderAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.MobSpawnSettings$Builder | @Accessor → fabricated$getMobSpawnCosts | "mobSpawnCosts" |  | — | default |
| mixins | MultiNoiseBiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.MultiNoiseBiomeSource | no injectors; implements BiomeSourceAccess; adds methods fabric_setModifyBiomeEntries, fabric_shouldModifyBiomeEntries; @Unique fields modifyBiomeEntries | | | | |
| mixins | NetherBiomePresetMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$1 (`targets`) | @Inject → apply | "apply(Ljava/util/function/Function;)Lnet/minecraft/world/level/biome/Climate$ParameterList;" | RETURN cancellable=true | — | require=0 |
| mixins | NoiseChunkMixin | 1.20.1 (1.20.1) | n.m.world.level.levelgen.NoiseChunk | @Inject → init; @Unique fields seed | "<init>" | TAIL | — | default |
| mixins | NoiseChunkMixin | 1.20.1 (1.20.1) | n.m.world.level.levelgen.NoiseChunk | @Inject → createMultiNoiseSampler | "cachedClimateSampler" | RETURN | — | default |
| mixins | RandomStateMixin | 1.20.1 (1.20.1) | n.m.world.level.levelgen.RandomState | @Inject → init; @Shadow sampler | "<init>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → modifyCodec; extends BiomeSourceMixin; adds methods fabric_modifyBiomeSet; @Shadow @Mutable CODEC; @Unique fields overrides, biomeSetModified, hasCheckedForModifiedSet | "<clinit>" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → rememberLookup | "create" | HEAD | — | default |
| mixins | TheEndBiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → clearLookup | "create" | TAIL | — | default |
| mixins | TheEndBiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → init | "<init>" | RETURN | — | default |
| mixins | TheEndBiomeSourceMixin | 1.20.1 (1.20.1) | n.m.world.level.biome.TheEndBiomeSource | @Inject → getWeightedEndBiome | "getNoiseBiome" | RETURN cancellable=true | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-dimension.mixins.json`

Versions: 1.20.1 · package `net.fabricmc.fabric.mixin.dimension` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | EntityMixin | 1.20.1 (1.20.1) | n.m.server.level.ServerPlayer, n.m.world.entity.Entity | @Inject → getTeleportTarget; implements Teleportable; adds methods fabric_setCustomTeleportTarget; @Unique fields customTeleportTarget | "findDimensionEntryPoint" | HEAD cancellable=true | — | allow=1 |
| mixins | EntityMixin | 1.20.1 (1.20.1) | n.m.server.level.ServerPlayer, n.m.world.entity.Entity | @ModifyVariable → stopEndSpecificBehavior | "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;" | HEAD | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-color.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.color.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockColorsLookupMixin | mc/forge-shared/1.20.1-1.21.11 (1.20.1) | n.m.client.color.block.BlockColors | no injectors; adds methods get; @Shadow blockColors | | | | |
| client | ItemColorsLookupMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.color.item.ItemColors | no injectors; adds methods get; @Shadow itemColors | | | | |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-content.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.content.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | FabricBrewingRecipeRegistryMixin | 1.20.1 (1.20.1) | fapi.api.registry.FabricBrewingRecipeRegistry (`targets`) (@Pseudo) | @Redirect → fabricated$potionMix | "registerPotionRecipe" | NEW `net/minecraft/world/item/alchemy/PotionBrewing$Mix` | — | require=0 |
| mixins | FabricBrewingRecipeRegistryMixin | 1.20.1 (1.20.1) | fapi.api.registry.FabricBrewingRecipeRegistry (`targets`) (@Pseudo) | @Redirect → fabricated$itemMix | "registerItemRecipe" | NEW `net/minecraft/world/item/alchemy/PotionBrewing$Mix` | — | require=0 |
| mixins | FurnaceFuelTableMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → fabricated$addFabricFuels | "getFuel" | RETURN | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-entity.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.entity.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LivingEntitySleepMixin | 1.20.1 (1.20.1) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$setBedOccupied | "startSleeping", "/^lambda\$stopSleeping\$\d+$/" | INVOKE `L…/BlockState;setBedOccupied(L…/Level;L…/BlockPos;L…/LivingEntity;Z)V` | — | require=2 |
| mixins | LivingEntitySleepMixin | 1.20.1 (1.20.1) | n.m.world.entity.LivingEntity | @ModifyReturnValue → fabricated$modifySleepingDirection | "getBedOrientation()Lnet/minecraft/core/Direction;" | RETURN | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-frapi.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.rendering.mixin` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockRenderDispatcherMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapWithCondition → fabricated$breakingOverlay | "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(L…/BlockAndTintGetter;L…/BakedModel;L…/BlockState;L…/BlockPos;L…/PoseStack;L…/VertexConsumer;ZL…/RandomSource;JIL…/ModelData;L…/RenderType;)V` | — | default |
| client | BlockRenderDispatcherMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapWithCondition → fabricated$batched | "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(L…/BlockAndTintGetter;L…/BakedModel;L…/BlockState;L…/BlockPos;L…/PoseStack;L…/VertexConsumer;ZL…/RandomSource;JIL…/ModelData;L…/RenderType;)V` | — | default |
| client | FallingBlockRendererMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.renderer.entity.FallingBlockRenderer | @WrapWithCondition → fabricated$fabricModels | "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(L…/BlockAndTintGetter;L…/BakedModel;L…/BlockState;L…/BlockPos;L…/PoseStack;L…/VertexConsumer;ZL…/RandomSource;JIL…/ModelData;L…/RenderType;)V` | — | default |
| client | ForgeHooksClientMixin | 1.20.1 (1.20.1) | forge.client.ForgeHooksClient | @WrapWithCondition → fabricated$fabricModels | "renderPistonMovedBlocks(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;ZILnet/minecraft/client/renderer/block/BlockRenderDispatcher;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(L…/BlockAndTintGetter;L…/BakedModel;L…/BlockState;L…/BlockPos;L…/PoseStack;L…/VertexConsumer;ZL…/RandomSource;JIL…/ModelData;L…/RenderType;)V` | — | default |
| client | MultiPartBakedModelMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.resources.model.MultiPartBakedModel | no injectors; implements FabricBlockRendering.Composite; adds methods fabricated$state, fabricated$parts; @Shadow selectors; @Unique fields fabricated$state | | | | |
| client | RebuildTaskMixin | 1.20.1 (1.20.1) | n.m.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask (`targets`) | @WrapWithCondition → fabricated$fabricModels | "compile(FFFLnet/minecraft/client/renderer/ChunkBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk$RebuildTask$CompileResults;" | INVOKE `L…/BlockRenderDispatcher;renderBatched(L…/BlockState;L…/BlockPos;L…/BlockAndTintGetter;L…/PoseStack;L…/VertexConsumer;ZL…/RandomSource;L…/ModelData;L…/RenderType;)V` @Local | — | default |
| client | RebuildTaskMixin | 1.20.1 (1.20.1) | n.m.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask (`targets`) | @Inject → fabricated$endSection | "compile(FFFLnet/minecraft/client/renderer/ChunkBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk$RebuildTask$CompileResults;" | RETURN | — | default |
| client | WeightedBakedModelMixin | 1.20.1 (1.20.1) | n.m.client.resources.model.WeightedBakedModel | no injectors; implements FabricBlockRendering.Composite; adds methods fabricated$state, fabricated$parts; @Shadow list; @Unique fields fabricated$state | | | | |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-interaction.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.interaction.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeBreak; @Shadow level, player | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerPlayerGameMode;isCreative()Z` cancellable=true | — | default |
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.server.level.ServerPlayerGameMode | @WrapOperation → fabricated$afterBreak | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerPlayerGameMode;removeBlock(L…/BlockPos;Z)Z` | — | default |
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeVanillaRemoval (reads the state and block entity before vanilla's removal where Fabricated puts it back, SEAM-3); @Unique fields fabricated$removedState, fabricated$removedBlockEntity | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | require=0 |
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterVanillaRemoval (fires `AFTER` at vanilla's `Block.destroy` put back, SEAM-3) | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/Block;destroy(L…/LevelAccessor;L…/BlockPos;L…/BlockState;)V` | — | require=0 |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge-item.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.item.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ItemForgeHooksMixin | 1.20.1 (1.20.1) | n.m.world.item.Item | no injectors; implements IForgeItem; adds methods getEquipmentSlot, getCraftingRemainingItem, hasCraftingRemainingItem (Forge's item hooks answered from Fabric's item API) | | | | |
| mixins | ItemStackForgeHooksMixin | 1.20.1 (1.20.1) | n.m.world.item.ItemStack | @WrapOperation → fabricated$fabricSuitableFor | "isCorrectToolForDrops" | INVOKE `L…/Item;isCorrectToolForDrops(L…/ItemStack;L…/BlockState;)Z` remap=false | — | default |
| mixins | ItemStackForgeHooksMixin | 1.20.1 (1.20.1) | n.m.world.item.ItemStack | @WrapOperation → fabricated$fabricAttributeModifiers | "getAttributeModifiers" | INVOKE `L…/Item;getAttributeModifiers(L…/EquipmentSlot;L…/ItemStack;)L…/Multimap;` remap=false | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-forge.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | FluidTypeMixin | 1.20.1 (1.20.1) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" | INVOKE `L…/ForgeHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | LivingEntityDropCaptureMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.entity.LivingEntity prio=Integer.MAX_VALUE | @Inject → fabricated$captureAfterForgesEvent (Forge 47's or Forge 52's event, whichever the method asks) | "dropAllDeathLoot" | INVOKE `L…/ForgeHooks;onLivingDrops(L…/LivingEntity;L…/DamageSource;L…/Collection;IZ)Z` shift=AFTER remap=false, INVOKE `L…/ForgeEventFactory;onLivingDrops(L…/LivingEntity;L…/DamageSource;L…/Collection;Z)Z` shift=AFTER remap=false | — | default |
| mixins | LivingEntityDropCaptureMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.entity.LivingEntity prio=Integer.MAX_VALUE | @WrapWithCondition → fabricated$keepDropsCaptured | "dropAllDeathLoot" | INVOKE `L…/Collection;forEach(L…/Consumer;)V` remap=false | — | default |
| mixins | LivingEntityDropCaptureMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.entity.LivingEntity prio=Integer.MAX_VALUE | @Inject → fabricated$addCapturedDrops | "dropAllDeathLoot" | RETURN | — | default |
| mixins | LivingEntityFluidTravelMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$throughFabricFluid | "travel" | INVOKE `L…/LivingEntity;isInFluidType(L…/FluidState;)Z` | — | default |
| mixins | MappedRegistryPendingHoldersMixin | mc/forge-shared (1.20.1) | n.m.core.MappedRegistry | @Inject → fabricated$beforeFreeze; implements PendingHolders; adds methods fabricated$keepPendingHolders, fabricated$restorePendingHolders; @Shadow unregisteredIntrusiveHolders, key; @Unique fields fabricated$pendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | mc/forge-shared (1.20.1) | n.m.core.MappedRegistry | @Inject → fabricated$afterFreeze | "freeze" | RETURN | — | default |
| mixins | MappedRegistryEarlyWriteMixin | 1.20.1 (1.20.1) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite (a frozen built-in registry written to before the main entrypoints is opened, REG-10); @Shadow frozen | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | FabricRegistryBuilderRootMixin | 1.20.1 (1.20.1) | fapi.api.event.registry.FabricRegistryBuilder (`targets`) (@Pseudo) | @Inject → fabricated$joinRoot (the registry built joins the root registry, PROVIDER-20) | "buildAndRegister" remap=false | RETURN | — | default |
| mixins | NamespacedWrapperPendingHoldersMixin | 1.20.1 (1.20.1) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$beforeFreeze | "m_203521_" | HEAD | — | default |
| mixins | NamespacedWrapperPendingHoldersMixin | 1.20.1 (1.20.1) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$afterFreeze | "m_203521_" | RETURN | — | default |
| mixins | NamespacedWrapperAliasMixin | 1.20.1 (1.20.1) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$refuseAliasName; adds methods addAlias; @Shadow delegate; @Unique fields FABRICATED$LOGGER, fabricated$aliases | "m_255290_(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/core/Holder$Reference;" | HEAD | — | default |
| mixins | NamespacedWrapperAliasMixin | 1.20.1 (1.20.1) | forge.registries.NamespacedWrapper (`targets`) | @ModifyVariable → fabricated$aliasedName | "m_7745_(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;", "m_6612_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;", "m_7804_(Lnet/minecraft/resources/ResourceLocation;)Z" | HEAD | — | default |
| mixins | NamespacedWrapperAliasMixin | 1.20.1 (1.20.1) | forge.registries.NamespacedWrapper (`targets`) | @ModifyVariable → fabricated$aliasedKey | "m_6246_(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", "m_142003_(Lnet/minecraft/resources/ResourceKey;)Z", "m_203636_(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", "getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;", "m_245420_(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;" | HEAD | — | default |
| mixins | NamespacedWrapperEntryAddedMixin | 1.20.1 (1.20.1) | forge.registries.NamespacedWrapper (`targets`) | @WrapMethod → fabricated$entryAdded; @Shadow delegate | "m_203704_(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/core/Holder$Reference;" |  | — | default |
| mixins | ForgeRegistryEntryAddedMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | forge.registries.ForgeRegistry | @WrapMethod → fabricated$entryAdded; @Unique methods fabricated$self, fabricated$fire | "register(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V" |  | — | default |
| mixins | ForgeRegistryEntryAddedMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | forge.registries.ForgeRegistry | @WrapMethod → fabricated$entryAddedWithId | "register(ILnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V" |  | — | default |
| mixins | ForgeRegistryRekeyMixin | mc/forge-shared (1.20.1) | forge.registries.ForgeRegistry | @Inject → fabricated$rekeyChangedValues (an entry whose value its map no longer finds, its equality changed since it was registered, is put again before the snapshot, REG-14); @Shadow ids, names, keys, owners; @Unique methods fabricated$rekey | "makeSnapshot" | HEAD | — | default |
| mixins | BuiltInRegistriesFreezeMixin | mc/forge-shared (1.20.1) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | mc/forge-shared (1.20.1) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | BuiltInRegistriesValidateMixin | 1.20.1 (1.20.1) | n.m.core.registries.BuiltInRegistries | @ModifyArg → fabricated$passOverEmptyForgeRegistries | "validate" | INVOKE `L…/Registry;forEach(L…/Consumer;)V` | — | default |
| mixins | GameDataFreezeMixin | 1.20.1 (1.20.1) | forge.registries.GameData | @Inject → fabricated$freezeBuiltInRegistries | "freezeData" | HEAD | — | default |
| mixins | CraftingHelperFabricConditionsMixin | 1.20.1 (1.20.1) | forge.common.crafting.CraftingHelper | @Inject → fabricated$fabricCondition; @Shadow conditions | "getCondition" | HEAD cancellable=true | — | default |
| mixins | CraftingHelperFabricIngredientsMixin | 1.20.1 (1.20.1) | forge.common.crafting.CraftingHelper | @Inject → fabricated$fabricIngredient | "getIngredient(Lcom/google/gson/JsonElement;Z)Lnet/minecraft/world/item/crafting/Ingredient;" | HEAD cancellable=true | — | default |
| mixins | IngredientForgeNetworkMixin | 1.20.1 (1.20.1) | n.m.world.item.crafting.Ingredient prio=900 | @Inject → fabricated$forgeIngredient | "fromNetwork" | HEAD cancellable=true | — | default |
| mixins | LanguageHookVanillaReaderMixin | 1.20.1 (1.20.1) | forge.server.LanguageHook | @Inject → fabricated$vanillaReader; @Shadow modTable | "loadLocaleData(Ljava/io/InputStream;)V" | HEAD cancellable=true | — | default |
| mixins | ShapedRecipeUtilCraftingSizeMixin | 1.20.1 (1.20.1) | io.github.fabricators_of_create.porting_lib.util.ShapedRecipeUtil (`targets`) (@Pseudo) | @Inject → fabricated$forgeCraftingSize | "setCraftingSize" | TAIL | — | require=0 |
| mixins | TagFileRemoveMixin | 1.20.1 (1.20.1) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | BucketItemEarlyFluidMixin | mc/forge-shared (1.20.1) | n.m.world.item.BucketItem | @WrapOperation → fabricated$delegateIfRegistered; @Shadow @Mutable fluidSupplier | "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V" | INVOKE `L…/IForgeRegistry;getDelegateOrThrow(L…/Object;)L…/Holder$Reference;` | — | default |
| mixins | BucketItemEarlyFluidMixin | mc/forge-shared (1.20.1) | n.m.world.item.BucketItem | @Inject → fabricated$fluidItselfIfUnregistered | "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V" | TAIL | — | default |
| mixins | BlockItemEarlyBlockMixin | 1.20.1 (1.20.1) | n.m.world.item.BlockItem | @WrapOperation → fabricated$delegateIfRegistered; @Shadow block | "getBlock" | INVOKE `L…/IForgeRegistry;getDelegateOrThrow(L…/Object;)L…/Holder$Reference;` | — | default |
| mixins | BlockItemEarlyBlockMixin | 1.20.1 (1.20.1) | n.m.world.item.BlockItem | @WrapOperation → fabricated$blockItselfIfUnregistered | "getBlock" | INVOKE `L…/Holder$Reference;get()L…/Object;` | — | default |
| mixins | LiquidBlockEarlyFluidMixin | 1.20.1 (1.20.1) | n.m.world.level.block.LiquidBlock | @WrapOperation → fabricated$delegateIfRegistered; @Shadow @Mutable supplier | "<init>(Lnet/minecraft/world/level/material/FlowingFluid;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V" | INVOKE `L…/IForgeRegistry;getDelegateOrThrow(L…/Object;)L…/Holder$Reference;` | — | default |
| mixins | LiquidBlockEarlyFluidMixin | 1.20.1 (1.20.1) | n.m.world.level.block.LiquidBlock | @Inject → fabricated$fluidItselfIfUnregistered | "<init>(Lnet/minecraft/world/level/material/FlowingFluid;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V" | TAIL | — | default |
| mixins | RecordItemEarlySoundMixin | 1.20.1 (1.20.1) | n.m.world.item.RecordItem | @WrapOperation → fabricated$delegateIfRegistered; @Shadow @Mutable soundSupplier | "<init>(ILnet/minecraft/sounds/SoundEvent;Lnet/minecraft/world/item/Item$Properties;I)V" | INVOKE `L…/IForgeRegistry;getDelegateOrThrow(L…/Object;)L…/Holder$Reference;` | — | default |
| mixins | RecordItemEarlySoundMixin | 1.20.1 (1.20.1) | n.m.world.item.RecordItem | @Inject → fabricated$soundItselfIfUnregistered | "<init>(ILnet/minecraft/sounds/SoundEvent;Lnet/minecraft/world/item/Item$Properties;I)V" | TAIL | — | default |
| mixins | PotionBrewingMixBridgeMixin | 1.20.1 (1.20.1) | n.m.world.item.alchemy.PotionBrewing$Mix | no injectors; adds methods fabricated$get$f_43532_, fabricated$get$f_43534_; @Shadow f_43532_, f_43534_ | | | | |
| mixins | MobBucketItemBridgeMixin | 1.20.1 (1.20.1) | n.m.world.item.MobBucketItem | no injectors; adds methods fabricated$get$f_151134_; @Shadow getFishType | | | | |
| mixins | PackRepositorySourcesMixin | mc/forge-shared (1.20.1) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (a set a Fabric mod made immutable is made mutable again before Forge adds a pack finder, PACK-1); @Shadow f_10497_ | "addPackFinder" remap=false | HEAD | — | default |
| mixins | PlayerListViewMixin | 1.20.1 (1.20.1) | n.m.server.players.PlayerList | @ModifyReturnValue → fabricated$viewOfCurrentList (Forge's unmodifiable view follows the list in the field, as POLY-1 and POLY-2); @Shadow f_11196_ | "getPlayers" | RETURN | — | default |
| mixins | PoiTypesByStateMixin | 1.20.1 (1.20.1) | n.m.world.entity.ai.village.poi.PoiTypes | @ModifyExpressionValue → fabricated$storingTypes (the map of types by state is a view of Forge's that stores a holder's type, POI-4) | "<clinit>" | INVOKE `L…/GameData;getBlockStatePointOfInterestTypeMap()L…/Map;` remap=false | — | default |
| client | ParticleEngineProvidersBridgeMixin | 1.20.1 (1.20.1) | n.m.client.particle.ParticleEngine | no injectors; adds methods fabricated$get$f_107293_; @Shadow f_107293_; @Unique fields fabricated$providersById | | | | |
| client | ExtendedBlockModelDeserializerMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | forge.client.model.ExtendedBlockModelDeserializer | @WrapOperation → fabricated$fabricLoader | "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;" | INVOKE `L…/ExtendedBlockModelDeserializer;deserializeGeometry(L…/JsonDeserializationContext;L…/JsonObject;)L…/IUnbakedGeometry;` | — | default |
| client | GuiHudSeamsMixin | 1.20.1 (1.20.1) | n.m.client.gui.Gui | no injectors; implements HudSeams; adds methods fabricated$startHud, fabricated$start$m_280421_, fabricated$end$m_280421_; @Unique fields fabricated$hudGoesOn | | | | |
| client | ForgeGuiHudSeamsMixin | 1.20.1 (1.20.1) | forge.client.gui.overlay.ForgeGui (`targets`) | @Inject → fabricated$atHudStart | "m_280421_" | HEAD cancellable=true | — | default |
| client | ForgeGuiHudSeamsMixin | 1.20.1 (1.20.1) | forge.client.gui.overlay.ForgeGui (`targets`) | @Inject → fabricated$atHudEnd | "m_280421_" | TAIL | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-keybinding.mixins.json`

Versions: 1.20.1 · package `net.fabricmc.fabric.mixin.client.keybinding` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | KeyBindingAccessor | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.KeyMapping | @Accessor → fabric_getCategoryMap | "CATEGORY_SORT_ORDER" |  | — | default |
| client | KeyBindingAccessor | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.KeyMapping | @Accessor → fabric_getBoundKey | "key" |  | — | default |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-loot.mixins.json`

Versions: 1.20.1 · package `net.fabricmc.fabric.mixin.loot` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LootDataManagerMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootDataManager | @Inject → fabric_keepResourceManager; @Unique fields fabric_resourceManager | "reload" | HEAD | — | default |
| mixins | LootDataManagerMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootDataManager | @Inject → fabric_applyLootTableEvents | "apply" | HEAD | — | default |
| mixins | LootDataManagerMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootDataManager | @Inject → fabric_onLootTablesLoaded | "apply" | RETURN | — | default |
| mixins | LootPoolAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolBuilderMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods with, conditionally, apply; @Unique methods self; @Shadow entries, conditions, functions | | | | |
| mixins | LootTableAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequenceId | "randomSequence" |  | — | default |
| mixins | LootTableBuilderMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow pools, functions | | | | |
| mixins | table.LootPoolMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootPool | no injectors; implements FabricLootPool; adds methods getEntries, getConditions, getFunctions; @Shadow entries, conditions, functions | | | | |
| mixins | table.LootTableMixin | 1.20.1 (1.20.1) | n.m.world.level.storage.loot.LootTable | no injectors; implements FabricLootSupplier; adds methods getPools, getFunctions; @Shadow pools, functions | | | | |

### `mc/forge-legacy/1.20.1/src/main/resources/fabricated-particles.mixins.json`

Versions: 1.20.1 · package `net.fabricmc.fabric.mixin.client.particle` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockDustParticleMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.particle.TerrainParticle | @WrapOperation → removeUntintableParticles | "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V" | INVOKE `L…/IClientBlockExtensions;areBreakingParticlesTinted(L…/BlockState;L…/ClientLevel;L…/BlockPos;)Z` | — | allow=1 |
| client | ParticleManagerAccessor | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.particle.ParticleEngine | @Accessor → getParticleAtlasTexture | "textureAtlas" |  | — | default |
| client | ParticleManagerAccessor$SimpleSpriteProviderAccessor | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.particle.ParticleEngine$MutableSpriteSet (`targets`) | @Accessor → getSprites | "sprites" |  | — | default |
| client | ParticleManagerMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.particle.ParticleEngine | @Inject → onRegisterDefaultFactories | "registerProviders()V" | RETURN | — | default |

### `mc/forge-legacy/1.20.1/src/testmod/resources/fabricated-forge-testmod.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.forgetest.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

Written in Mojang's names; the test mod's jar names them in Intermediary's, its refmap from Mixin's annotation processor (`mc/platform-forge-legacy.gradle`). The accessors of fields Forge 47 replaced name them in Intermediary's, `remap = false`. The interface mixin's injectors, which Forge 47's processor only warns of there, are mapped as Fabric's processor maps them.

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BiomeGenerationSettingsAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeGenerationSettings | @Accessor → fabricatedtest$featureSet | "featureSet" |  | — | default |
| mixins | BiomeGenerationSettingsAccessor | 1.20.1 (1.20.1) | n.m.world.level.biome.BiomeGenerationSettings | @Accessor → fabricatedtest$setFeatureSet (no `@Mutable`: BIOME-6's check) | "featureSet" |  | — | default |
| mixins | CommandsMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.commands.Commands | @Inject → fabricatedForgeTest$beforeSetConsumer; @Unique fields fabricatedForgeTest$logged, fabricatedForgeTest$constructionLogged | "<init>" | INVOKE `L…/CommandDispatcher;setConsumer(L…/ResultConsumer;)V` remap=false | — | default |
| mixins | CommandsMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.commands.Commands | @Inject → fabricatedForgeTest$afterDispatcher | "<init>" | INVOKE `L…/CommandDispatcher;<init>()V` shift=BY by=2 | — | default |
| mixins | ContainerInterfaceMixin | 1.20.1 (1.20.1) | n.m.world.Container (an interface mixin: INTERFACE-1's checks) | @Inject → fabricatedForgeTest$beforeGetItem | "countItem" | INVOKE `L…/Container;getItem(I)L…/ItemStack;` locals=CAPTURE_FAILHARD | — | default |
| mixins | ContainerInterfaceMixin | 1.20.1 (1.20.1) | n.m.world.Container (an interface mixin: INTERFACE-1's checks) | @WrapOperation → fabricatedForgeTest$tenfold | "countItem" | INVOKE `L…/ItemStack;getCount()I` | — | default |
| mixins | ContainerInterfaceMixin | 1.20.1 (1.20.1) | n.m.world.Container (an interface mixin: INTERFACE-1's checks) | @Redirect → fabricatedForgeTest$test | "hasAnyMatching" | INVOKE `L…/Predicate;test(L…/Object;)Z` @Local | — | default |
| mixins | ContainerInterfaceMixin | 1.20.1 (1.20.1) | n.m.world.Container (an interface mixin: INTERFACE-1's checks) | @ModifyConstant → fabricatedForgeTest$maxStackSize | "getMaxStackSize" | CONSTANT intValue=64 | — | default |
| mixins | LanguageReaderMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.locale.Language | @ModifyVariable → fabricatedForgeTest$watch | "loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V" | HEAD | — | default |
| mixins | LivingEntityCapturedDropsMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.entity.LivingEntity prio=1500 | @Inject → fabricatedForgeTest$takeCapturedDrops | "dropAllDeathLoot" | RETURN | — | default |
| mixins | MinecraftServerArgsMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.server.MinecraftServer | @ModifyArgs → fabricatedForgeTest$tickChildrenArgs; @Unique fields fabricatedForgeTest$modified | "tickServer" | INVOKE `L…/MinecraftServer;tickChildren(L…/BooleanSupplier;)V` | — | default |
| mixins | MinecraftServerMixin | 1.20.1 (1.20.1) | n.m.server.MinecraftServer | @Inject → fabricatedForgeTest$afterTick (runs Forge 47's half's checks); @Shadow overworld, getLevel, getCommands, getRecipeManager; @Unique fields fabricatedForgeTest$checked | "tickServer" | TAIL | — | default |
| mixins | MinecraftServerWrapMixin | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.server.MinecraftServer | @WrapMethod → fabricatedForgeTest$wrapTick; @Unique fields fabricatedForgeTest$wrapped | "tickServer" |  | — | default |
| mixins | MobBucketItemAccessor | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.world.item.MobBucketItem | @Accessor → fabricatedtest$type | "field_7991" remap=false |  | — | default |
| mixins | PoiTypesAccessor | 1.20.1 (1.20.1) | n.m.world.entity.ai.village.poi.PoiTypes | @Accessor → fabricatedtest$typeByState (typed as vanilla's, holders: POI-4's check) | "TYPE_BY_STATE" |  | — | default |
| mixins | PotionBrewingAccessor | 1.20.1 (1.20.1) | n.m.world.item.alchemy.PotionBrewing | @Accessor → fabricatedtest$potionMixes | "POTION_MIXES" |  | — | default |
| mixins | PotionBrewingMixAccessor | 1.20.1 (1.20.1) | n.m.world.item.alchemy.PotionBrewing$Mix | @Accessor → fabricatedtest$from | "field_8962" remap=false |  | — | default |
| mixins | PotionBrewingMixAccessor | 1.20.1 (1.20.1) | n.m.world.item.alchemy.PotionBrewing$Mix | @Accessor → fabricatedtest$to | "field_8961" remap=false |  | — | default |
| client | HudHooksMixin | 1.20.1 (1.20.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheStart; @Unique fields fabricatedForgeTest$started, fabricatedForgeTest$endedCancelled, fabricatedForgeTest$endReported | "render" | HEAD cancellable=true | — | default |
| client | HudHooksMixin | 1.20.1 (1.20.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheEnd | "render" | TAIL | — | default |
| client | McRuntimeTestMixin | 1.20.1 (1.20.1) | me.earth.mc_runtime_test.McRuntimeTest (`targets`) (@Pseudo) | @Inject → fabricatedForgeTest$holdForServerChecks | "tickHook" | HEAD cancellable=true | — | default |
| client | MouseHandlerInvoker | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.MouseHandler | @Invoker → fabricatedtest$onPress | "onPress" |  | — | default |
| client | ParticleEngineProvidersAccessor | mc/forge-shared/1.20.1-1.21.1 (1.20.1) | n.m.client.particle.ParticleEngine | @Accessor → fabricatedtest$providers | "field_3835" remap=false |  | — | default |

### `mc/forge-legacy/1.20.1/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json`

Versions: 1.20.1 · package `io.github.cmdralph.fabricated.testmod.frapi.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | McRuntimeTestHoldMixin | 1.20.1 (1.20.1) | me.earth.mc_runtime_test.McRuntimeTest (`targets`) (@Pseudo) | @Inject → fabricatedFrapiTest$holdForMovingBlocks | "tickHook" | HEAD cancellable=true | — | default |

## Forge 52 (`forge-legacy`): 71 mixins in 14 configs

Minecraft 1.21.1, in Mojang's names without a refmap, as on Forge 61 to 66. The mixins come from `mc/forge-shared` and its
groups, as the source column says, but `NamespacedWrapperAliasMixin`, over 1.21.1's lookups, `LoadingRegistriesMixin`, on
1.21.1's tagless wrapper of the registries, `LiquidBlockRendererSpritesMixin`, Forge 52's fluid sprites (FLUID-4), the
item hooks Forge 52 names its own way (ITEM-1), the renderer seams in its section compiler and piston renderer and on
1.21.1's weighted model (FRAPI-11), its screen input hook (LAMBDA-10), its HUD layer stacks' vanilla lists (HUD-6), its ingredient codec, of which Forge 52 keeps no
vanilla form (NET-13), and the bridges of the fields it replaced, named after Mojang's names of them (ACC-5).

| Config file | Package | Versions | Sections | defaultRequire | overwrites.requireAnnotations | plugin |
|---|---|---|---|---|---|---|
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge.mixins.json` | `io.github.cmdralph.fabricated.forge.mixin` | 1.21.1 | mixins 26, client 6 | 1 | — | `FabricatedMixinPlugin` |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-loot.mixins.json` | `net.fabricmc.fabric.mixin.loot` | 1.21.1 | mixins 4 | 1 | true | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-loot.mixins.json` | `io.github.cmdralph.fabricated.forge.loot.mixin` | 1.21.1 | mixins 2 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-interaction.mixins.json` | `io.github.cmdralph.fabricated.forge.interaction.mixin` | 1.21.1 | mixins 1 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-entity.mixins.json` | `io.github.cmdralph.fabricated.forge.entity.mixin` | 1.21.1 | mixins 1 | 1 | — | none |
| `mc/forge-shared/1.21.1+/src/main/resources/fabricated-forge-block.mixins.json` | `io.github.cmdralph.fabricated.forge.block.mixin` | 1.21.1 | mixins 1 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-color.mixins.json` | `io.github.cmdralph.fabricated.forge.color.mixin` | 1.21.1 | client 2 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-keybinding.mixins.json` | `net.fabricmc.fabric.mixin.client.keybinding` | 1.21.1 | client 1 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-particles.mixins.json` | `net.fabricmc.fabric.mixin.client.particle` | 1.21.1 | client 4 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-item.mixins.json` | `io.github.cmdralph.fabricated.forge.item.mixin` | 1.21.1 | mixins 4 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-content.mixins.json` | `io.github.cmdralph.fabricated.forge.content.mixin` | 1.21.1 | mixins 1 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-frapi.mixins.json` | `io.github.cmdralph.fabricated.forge.rendering.mixin` | 1.21.1 | client 6 | 1 | — | `FrapiMixinPlugin` |
| `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-screen.mixins.json` | `io.github.cmdralph.fabricated.forge.screen.mixin` | 1.21.1 | client 1 | 1 | — | none |
| `mc/forge-legacy/1.21.1/src/testmod/resources/fabricated-forge-testmod.mixins.json` | `io.github.cmdralph.fabricated.forgetest.mixin` | 1.21.1 | mixins 6, client 5 | 1 | — | none |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | FluidTypeMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" | INVOKE `L…/ForgeHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | LivingEntityFluidTravelMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$throughFabricFluid | "travel" | INVOKE `L…/LivingEntity;isInFluidType(L…/FluidState;)Z` | — | default |
| mixins | MappedRegistryPendingHoldersMixin | mc/forge-shared (1.21.1) | n.m.core.MappedRegistry | @Inject → fabricated$beforeFreeze; implements PendingHolders; adds methods fabricated$keepPendingHolders, fabricated$restorePendingHolders; @Shadow unregisteredIntrusiveHolders, key; @Unique fields fabricated$pendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | mc/forge-shared (1.21.1) | n.m.core.MappedRegistry | @Inject → fabricated$afterFreeze | "freeze" | RETURN | — | default |
| mixins | NamespacedWrapperPendingHoldersMixin | mc/forge-shared/1.21.1+ (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$beforeFreeze | "freeze" | HEAD | — | default |
| mixins | NamespacedWrapperPendingHoldersMixin | mc/forge-shared/1.21.1+ (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$afterFreeze | "freeze" | RETURN | — | default |
| mixins | NamespacedWrapperEarlyWriteMixin | mc/forge-shared/1.21.1+ (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @WrapMethod → fabricated$openForEarlyWrite; @Shadow locked, delegate | "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;" |  | — | default |
| mixins | NamespacedWrapperAliasMixin | 1.21.1 (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @Inject → fabricated$refuseAliasName; adds methods addAlias; @Unique methods fabricated$addToForge; @Shadow locked, delegate; @Unique fields FABRICATED$LOGGER, fabricated$aliases | "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;" | HEAD | — | default |
| mixins | NamespacedWrapperAliasMixin | 1.21.1 (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @ModifyVariable → fabricated$aliasedName | "get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;", "getOptional(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;", "containsKey(Lnet/minecraft/resources/ResourceLocation;)Z", "getHolder(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;" | HEAD | — | default |
| mixins | NamespacedWrapperAliasMixin | 1.21.1 (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @ModifyVariable → fabricated$aliasedKey | "get(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", "containsKey(Lnet/minecraft/resources/ResourceKey;)Z", "getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", "getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;", "registrationInfo(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;" | HEAD | — | default |
| mixins | NamespacedWrapperEntryAddedMixin | mc/forge-shared/1.21.1+ (1.21.1) | forge.registries.NamespacedWrapper (`targets`) | @WrapMethod → fabricated$entryAdded; @Shadow delegate | "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;" |  | — | default |
| mixins | ForgeRegistryEntryAddedMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | forge.registries.ForgeRegistry | @WrapMethod → fabricated$entryAdded; @Unique methods fabricated$self, fabricated$fire | "register(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V" |  | — | default |
| mixins | ForgeRegistryEntryAddedMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | forge.registries.ForgeRegistry | @WrapMethod → fabricated$entryAddedWithId | "register(ILnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V" |  | — | default |
| mixins | ForgeRegistryRekeyMixin | mc/forge-shared (1.21.1) | forge.registries.ForgeRegistry | @Inject → fabricated$rekeyChangedValues (an entry whose value its map no longer finds, its equality changed since it was registered, is put again before the snapshot, REG-14); @Shadow ids, names, keys, owners; @Unique methods fabricated$rekey | "makeSnapshot" | HEAD | — | default |
| mixins | BuiltInRegistriesFreezeMixin | mc/forge-shared (1.21.1) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | mc/forge-shared (1.21.1) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | GameDataFreezeMixin | mc/forge-shared/1.21.1+ (1.21.1) | forge.registries.GameData | @Inject → fabricated$freezeBuiltInRegistries | "freezeData" | HEAD | — | default |
| mixins | TagFileRemoveMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.tags.TagFile | @Inject → fabricated$readFabricRemove; @Shadow @Mutable CODEC | "<clinit>" | TAIL | — | default |
| mixins | IngredientNetworkCodecMixin | 1.21.1 (1.21.1) | n.m.world.item.crafting.Ingredient | @Inject → fabricated$sendAsUpstream (a Fabric mod's own ingredient in 1.21.1's vanilla form, which Forge 52 keeps no codec of, NET-13); @Shadow @Mutable CONTENTS_STREAM_CODEC | "<clinit>" | TAIL | — | default |
| mixins | ForgePayloadEncodeMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.network.protocol.common.custom.CustomPacketPayload$1 (`targets`) | @WrapOperation → fabricated$writeForgePayload | "writeCap" | INVOKE `L…/StreamCodec;encode(L…/Object;L…/Object;)V` | — | default |
| mixins | ResourcePackLoaderKnownPackMixin | mc/forge-shared/1.21.1+ (1.21.1) | forge.resource.ResourcePackLoader | @ModifyArg → fabricated$knownPack (a Fabric mod's pack gets Fabric API's known-pack identity, PACK-10) | "findPacks" | INVOKE `L…/PackLocationInfo;<init>(L…/String;L…/Component;L…/PackSource;L…/Optional;)V` index=3 | 0 | default |
| mixins | BucketItemEarlyFluidMixin | mc/forge-shared (1.21.1) | n.m.world.item.BucketItem | @WrapOperation → fabricated$delegateIfRegistered; @Shadow @Mutable fluidSupplier | "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V" | INVOKE `L…/IForgeRegistry;getDelegateOrThrow(L…/Object;)L…/Holder$Reference;` | — | default |
| mixins | BucketItemEarlyFluidMixin | mc/forge-shared (1.21.1) | n.m.world.item.BucketItem | @Inject → fabricated$fluidItselfIfUnregistered | "<init>(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V" | TAIL | — | default |
| mixins | PlayerListViewMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.server.players.PlayerList | @ModifyReturnValue → fabricated$viewOfCurrentList (Forge's unmodifiable view follows the list in the field, POLY-2) | "getPlayers" | RETURN | — | default |
| mixins | PoiTypesMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | PackRepositorySourcesMixin | mc/forge-shared (1.21.1) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (a set a Fabric mod made immutable is made mutable again before Forge adds a pack finder, PACK-1, PACK-11); @Shadow @Mutable sources | "addPackFinder" remap=false | HEAD | — | default |
| mixins | PackOverlaysMixin | mc/forge-shared/1.21.1-26.2 (1.21.1) | n.m.server.packs.repository.Pack | @ModifyArg → fabricated$fabricOverlays (Fabric's conditional overlays after those the game read, a section that can't be read left out with one warning, PACK-12) | "readPackMetadata" | INVOKE `L…/Pack$Metadata;<init>(L…/Component;L…/PackCompatibility;L…/FeatureFlagSet;L…/List;Z)V` index=3  sugar:@Local(argsOnly = true), @Local PackResources | — | default |
| mixins | LivingEntityDropCaptureMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.entity.LivingEntity prio=Integer.MAX_VALUE | @Inject → fabricated$captureAfterForgesEvent (Forge 47's or Forge 52's event, whichever the method asks) | "dropAllDeathLoot" | INVOKE `L…/ForgeHooks;onLivingDrops(L…/LivingEntity;L…/DamageSource;L…/Collection;IZ)Z` shift=AFTER remap=false, INVOKE `L…/ForgeEventFactory;onLivingDrops(L…/LivingEntity;L…/DamageSource;L…/Collection;Z)Z` shift=AFTER remap=false | — | default |
| mixins | LivingEntityDropCaptureMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.entity.LivingEntity prio=Integer.MAX_VALUE | @WrapWithCondition → fabricated$keepDropsCaptured | "dropAllDeathLoot" | INVOKE `L…/Collection;forEach(L…/Consumer;)V` remap=false | — | default |
| mixins | LivingEntityDropCaptureMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.entity.LivingEntity prio=Integer.MAX_VALUE | @Inject → fabricated$addCapturedDrops | "dropAllDeathLoot" | RETURN | — | default |
| mixins | MobBucketItemBridgeMixin | 1.21.1 (1.21.1) | n.m.world.item.MobBucketItem | no injectors; adds methods fabricated$get$type (named after Mojang's name of the field, ACC-5); @Shadow getFishType | | | | |
| mixins | BiomeWeatherWriteMixin | mc/forge-shared/1.21.1+ (1.21.1) | fapi.impl.biome.modification.BiomeModificationContextImpl$WeatherContextImpl (`targets`) (@Pseudo) | @Redirect → fabricated$setClimate (the climate into the field and Forge's biome info, BIOME-3) | "setPrecipitation", "setTemperature", "setTemperatureModifier", "setDownfall" | FIELD `L…/Biome;climateSettings:L…/Biome$ClimateSettings;` opcode=PUTFIELD | — | default |
| mixins | BiomeClimateAccessor | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.level.biome.Biome | @Accessor @Mutable → fabricated$setClimateSettings | "climateSettings" |  | — | default |
| mixins | ModifiableBiomeInfoAccessor | mc/forge-shared/1.21.1+ (1.21.1) | forge.common.world.ModifiableBiomeInfo | @Accessor @Mutable → fabricated$setOriginalBiomeInfo; @Accessor → fabricated$setModifiedBiomeInfo | "originalBiomeInfo", "modifiedBiomeInfo" |  | — | default |
| client | LiquidBlockRendererSpritesMixin | 1.21.1 (1.21.1) | n.m.client.renderer.block.LiquidBlockRenderer (priority 1100: after upstream's hook at the same point) | @ModifyVariable → fabricated$overlaySlot | "tesselate" | INVOKE `L…/LiquidBlockRenderer;isNeighborSameFluid(L…/FluidState;L…/FluidState;)Z`, the first `TextureAtlasSprite[]` local | — | default |
| client | ExtendedBlockModelDeserializerMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | forge.client.model.ExtendedBlockModelDeserializer | @WrapOperation → fabricated$fabricLoader | "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;" | INVOKE `L…/ExtendedBlockModelDeserializer;deserializeGeometry(L…/JsonDeserializationContext;L…/JsonObject;)L…/IUnbakedGeometry;` | — | default |
| client | ParticleEngineProvidersBridgeMixin | 1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | no injectors; adds methods fabricated$get$providers (named after Mojang's name of the field, ACC-5); @Shadow providers; @Unique fields fabricated$providersById | | | | |
| client | ParticleEngineRenderMixin | 1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | @Redirect → fabricated$renderOverload (the call of the overload taking a frustum, with none, which Forge 52's vanilla method makes of itself, IRIS-2) | "render(L…/LightTexture;L…/Camera;F)V" | INVOKE `L…/ParticleEngine;render(L…/LightTexture;L…/Camera;F)V` | — | require=0 (none where Forge 52 calls the overload itself) |
| client | ForgeLayeredDrawVanillaLayersMixin | 1.21.1 (1.21.1) | forge.client.gui.overlay.ForgeLayeredDraw | @Inject → fabricated$addLayer (a layer a stack takes by name goes into vanilla's list too, as vanilla's `add` puts it there, until the stack is resolved, HUD-6); @Shadow order; @Unique fields fabricated$named, fabricated$resolved; @Unique methods fabricated$add | "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/LayeredDraw$Layer;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;" | INVOKE `L…/List;add(L…/Object;)Z` | — | default |
| client | ForgeLayeredDrawVanillaLayersMixin | 1.21.1 (1.21.1) | forge.client.gui.overlay.ForgeLayeredDraw | @Inject → fabricated$addStack (a nested stack, as Forge draws it) | "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/LayeredDraw;Ljava/util/function/BooleanSupplier;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;" | INVOKE `L…/List;add(L…/Object;)Z` | — | default |
| client | ForgeLayeredDrawVanillaLayersMixin | 1.21.1 (1.21.1) | forge.client.gui.overlay.ForgeLayeredDraw | @Inject → fabricated$keepOthers (what else vanilla's list held follows the layer of Forge's it followed, in Forge's order) | "resolveNested" | TAIL | — | default |
| client | LayeredDrawLayersAccessor | 1.21.1 (1.21.1) | n.m.client.gui.LayeredDraw | @Accessor → fabricated$layers | "layers" |  | — | default |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-loot.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.loot` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getCondition | "condition" |  | — | default |
| mixins | LootPoolAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootPoolBuilderMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods with, conditionally, apply; @Unique methods self; @Shadow entries, conditions, functions | | | | |
| mixins | LootPoolBuilderMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, when, apply; @Unique methods self; @Shadow entries, conditions, functions | | | | |
| mixins | LootPoolBuilderMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, when, apply; @Unique methods self; @Shadow entries | | | | |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequenceId | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootTableAccessor | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableBuilderMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow @Mutable pools, functions | | | | |
| mixins | LootTableBuilderMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow @Mutable pools, functions | | | | |
| mixins | LootTableBuilderMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods pool, apply, pools, modifyPools; @Unique methods self; @Shadow @Mutable pools | | | | |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-loot.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.loot.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LoadingRegistriesMixin | 1.21.1 (1.21.1) | n.m.server.ReloadableServerRegistries | @WrapOperation → fabricated$noteLoadingRegistries (@Coerce receiver) | "reload" | INVOKE `L…/ReloadableServerRegistries$EmptyTagLookupWrapper;createSerializationContext(L…/DynamicOps;)L…/RegistryOps;` | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.server.ReloadableServerRegistries | @ModifyVariable → fabricated$startReload | "reload" | HEAD | — | default |
| mixins | ReloadableServerRegistriesMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN | — | default |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-interaction.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.interaction.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeBreak; @Shadow level, player | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerPlayerGameMode;isCreative()Z` cancellable=true | — | default |
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @WrapOperation → fabricated$afterBreak | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerPlayerGameMode;removeBlock(L…/BlockPos;Z)Z` | — | default |
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeVanillaRemoval (reads the state and block entity before vanilla's removal where Fabricated puts it back, SEAM-3); @Unique fields fabricated$removedState, fabricated$removedBlockEntity | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/ServerLevel;removeBlock(L…/BlockPos;Z)Z` | — | require=0 |
| mixins | ServerPlayerGameModeBreakMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterVanillaRemoval (fires `AFTER` at vanilla's `Block.destroy` put back, SEAM-3) | "destroyBlock(Lnet/minecraft/core/BlockPos;)Z" | INVOKE `L…/Block;destroy(L…/LevelAccessor;L…/BlockPos;L…/BlockState;)V` | — | require=0 |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-entity.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.entity.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LivingEntitySleepMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.entity.LivingEntity | @WrapOperation → fabricated$setBedOccupied | "startSleeping", "/^lambda\$stopSleeping\$\d+$/" | INVOKE `L…/BlockState;setBedOccupied(L…/Level;L…/BlockPos;L…/LivingEntity;Z)V` | — | require=2 |
| mixins | LivingEntitySleepMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.entity.LivingEntity | @ModifyReturnValue → fabricated$modifySleepingDirection | "getBedOrientation()Lnet/minecraft/core/Direction;" | RETURN | — | default |

### `mc/forge-shared/1.21.1+/src/main/resources/fabricated-forge-block.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.block.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockForgeHooksMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.level.block.Block | no injectors; adds method makesOpenTrapdoorAboveClimbable (Forge's ladder rule, or a block in Fabric's trapdoor tag) | | | | |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-color.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.color.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockColorsLookupMixin | mc/forge-shared/1.20.1-1.21.11 (1.21.1) | n.m.client.color.block.BlockColors | no injectors; adds methods get; @Shadow blockColors | | | | |
| client | ItemColorsLookupMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.color.item.ItemColors | no injectors; adds methods get; @Shadow itemColors | | | | |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-keybinding.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.client.keybinding` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | KeyBindingAccessor | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.KeyMapping | @Accessor → fabric_getCategoryMap | "CATEGORY_SORT_ORDER" |  | — | default |
| client | KeyBindingAccessor | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.KeyMapping | @Accessor → fabric_getBoundKey | "key" |  | — | default |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-particles.mixins.json`

Versions: 1.21.1 · package `net.fabricmc.fabric.mixin.client.particle` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockDustParticleMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.particle.TerrainParticle | @WrapOperation → removeUntintableParticles | "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V" | INVOKE `L…/IClientBlockExtensions;areBreakingParticlesTinted(L…/BlockState;L…/ClientLevel;L…/BlockPos;)Z` | — | allow=1 |
| client | ParticleManagerAccessor | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | @Accessor → getParticleAtlasTexture | "textureAtlas" |  | — | default |
| client | ParticleManagerAccessor$SimpleSpriteProviderAccessor | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine$MutableSpriteSet (`targets`) | @Accessor → getSprites | "sprites" |  | — | default |
| client | ParticleManagerMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | @Inject → onRegisterDefaultFactories | "registerProviders()V" | RETURN | — | default |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-item.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.item.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ItemForgeHooksMixin | mc/forge-shared/1.21.1+ (1.21.1) | n.m.world.item.Item | no injectors; adds methods getEquipmentSlot, getCreatorModId (Forge's item hooks, answered from Fabric's item API, ITEM-1) | | | | |
| mixins | ItemEnchantingHookMixin | 1.21.1 (1.21.1) | n.m.world.item.Item | no injectors; adds method canApplyAtEnchantingTable(ItemStack, Enchantment) (Forge 52's, answered from Fabric's item API with the running server's holder); @Unique methods fabricated$holder | | | | |
| mixins | ItemRecipeRemainderMixin | 1.21.1 (1.21.1) | n.m.world.item.Item | no injectors; adds methods getCraftingRemainingItem(ItemStack), hasCraftingRemainingItem(ItemStack) (Forge 52's, answered from Fabric's getRecipeRemainder) | | | | |
| mixins | ItemBuiltComponentsMixin | mc/forge-shared/1.21.x (1.21.1) | n.m.world.item.Item | @Inject → fabricated$buildAgainOnceChanged; @Shadow components, builtComponents; @Unique fields fabricated$builtFrom | "components()Lnet/minecraft/core/component/DataComponentMap;" | HEAD | — | default |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-content.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.content.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | FurnaceFuelTableMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → fabricated$addFabricFuels (Fabric's fuels in the table Forge 52 reads its burn times from, FUEL-2) | "getFuel" | RETURN | — | default |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-frapi.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.rendering.mixin` · required=true · defaultRequire=1 · plugin=`FrapiMixinPlugin` · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BlockRenderDispatcherMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapWithCondition → fabricated$breakingOverlay | "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…ModelData;L…/RenderType;)V` | — | default |
| client | BlockRenderDispatcherMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.renderer.block.BlockRenderDispatcher | @WrapWithCondition → fabricated$batched | "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…ModelData;L…/RenderType;)V` | — | default |
| client | FallingBlockRendererMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.renderer.entity.FallingBlockRenderer | @WrapWithCondition → fabricated$fabricModels | "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…ModelData;L…/RenderType;)V` | — | default |
| client | MultiPartBakedModelMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.resources.model.MultiPartBakedModel | no injectors; implements FabricBlockRendering$Composite; adds methods fabricated$state, fabricated$parts; @Shadow selectors; @Unique fields fabricated$state | | | | |
| client | PistonHeadRendererMixin | 1.21.1 (1.21.1) | n.m.client.renderer.blockentity.PistonHeadRenderer | @WrapWithCondition → fabricated$fabricModels | "renderBlock" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…ModelData;L…/RenderType;)V` | — | default |
| client | SectionCompilerMixin | 1.21.1 (1.21.1) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels (@Local region, pack, the buffers by layer (ordinal 1: Forge's model data map is the first), the model); implements FabricBlockRendering$SectionLayers; @Shadow getOrBeginLayer | "compile" | INVOKE `L…/BlockRenderDispatcher;renderBatched(…ModelData;L…/RenderType;)V` | — | default |
| client | SectionCompilerMixin | 1.21.1 (1.21.1) | n.m.client.renderer.chunk.SectionCompiler | @Inject → fabricated$endSection | "compile" | RETURN | — | default |
| client | WeightedBakedModelMixin | 1.21.1 (1.21.1) | n.m.client.resources.model.WeightedBakedModel | no injectors; implements FabricBlockRendering$Composite; adds methods fabricated$state, fabricated$parts; @Shadow list; @Unique fields fabricated$state | | | | |

### `mc/forge-legacy/1.21.1/src/main/resources/fabricated-forge-screen.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forge.screen.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | ForgeEventFactoryClientScreenInputMixin | 1.21.1 (1.21.1) | forge.client.event.ForgeEventFactoryClient | @WrapOperation → fabricated$mouseClicked (Fabric's mouse click events around the screen's call, LAMBDA-10) | "onScreenMouseClicked" | INVOKE `L…/Screen;mouseClicked(DDI)Z` | — | default |
| client | ForgeEventFactoryClientScreenInputMixin | 1.21.1 (1.21.1) | forge.client.event.ForgeEventFactoryClient | @WrapOperation → fabricated$mouseReleased | "onScreenMouseReleased" | INVOKE `L…/Screen;mouseReleased(DDI)Z` | — | default |

### `mc/forge-legacy/1.21.1/src/testmod/resources/fabricated-forge-testmod.mixins.json`

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.forgetest.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=—

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | CommandsMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.commands.Commands | @Inject → fabricatedForgeTest$beforeSetConsumer; @Unique fields fabricatedForgeTest$logged, fabricatedForgeTest$constructionLogged | "<init>" | INVOKE `L…/CommandDispatcher;setConsumer(L…/ResultConsumer;)V` remap=false | — | default |
| mixins | CommandsMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.commands.Commands | @Inject → fabricatedForgeTest$afterDispatcher | "<init>" | INVOKE `L…/CommandDispatcher;<init>()V` shift=BY by=2 | — | default |
| mixins | LanguageReaderMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.locale.Language | @ModifyVariable → fabricatedForgeTest$watch | "loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V" | HEAD | — | default |
| mixins | LivingEntityCapturedDropsMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.entity.LivingEntity prio=1500 | @Inject → fabricatedForgeTest$takeCapturedDrops | "dropAllDeathLoot" | RETURN | — | default |
| mixins | MinecraftServerArgsMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.server.MinecraftServer | @ModifyArgs → fabricatedForgeTest$tickChildrenArgs; @Unique fields fabricatedForgeTest$modified | "tickServer" | INVOKE `L…/MinecraftServer;tickChildren(L…/BooleanSupplier;)V` | — | default |
| mixins | MinecraftServerWrapMixin | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.server.MinecraftServer | @WrapMethod → fabricatedForgeTest$wrapTick; @Unique fields fabricatedForgeTest$wrapped | "tickServer" |  | — | default |
| mixins | MobBucketItemAccessor | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.world.item.MobBucketItem | @Accessor → fabricatedtest$type | "field_7991" remap=false |  | — | default |
| client | HudHooksMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheStart; @Unique fields fabricatedForgeTest$started, fabricatedForgeTest$endedCancelled, fabricatedForgeTest$endReported | "render" | HEAD cancellable=true | — | default |
| client | HudHooksMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$atTheEnd | "render" | TAIL | — | default |
| client | HudLayersMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$afterTheHotbar (a layer put into the first stack's vanilla list at index 3, as Apoli's, HUD-6); @Unique fields fabricatedForgeTest$drawn, fabricatedForgeTest$reported | "<init>" | FIELD `L…/Gui;layers:L…/LayeredDraw;` opcode=GETFIELD | — | default |
| client | HudLayersMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$start | "render" | HEAD | — | default |
| client | HudLayersMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$hotbar | "renderHotbarAndDecorations" | HEAD | — | default |
| client | HudLayersMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$experience | "renderExperienceLevel" | HEAD | — | default |
| client | HudLayersMixin | 1.21.1 (1.21.1) | n.m.client.gui.Gui | @Inject → fabricatedForgeTest$end | "render" | TAIL | — | default |
| client | LayeredDrawLayersAccessor | 1.21.1 (1.21.1) | n.m.client.gui.LayeredDraw | @Accessor → fabricatedForgeTest$layers | "layers" |  | — | default |
| client | MouseHandlerInvoker | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.MouseHandler | @Invoker → fabricatedtest$onPress | "onPress" |  | — | default |
| client | ParticleEngineProvidersAccessor | mc/forge-shared/1.20.1-1.21.1 (1.21.1) | n.m.client.particle.ParticleEngine | @Accessor → fabricatedtest$providers | "field_3835" remap=false |  | — | default |

---
