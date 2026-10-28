# Mixin inventory

Every mixin Fabricated ships, by config: target, injector, injection point, `require` and the versions that compile
it. Snapshot taken 2026-10-19 by scanning the sources (see `roadmap.md`), then updated by hand for that session's
changes; the list of configs, each config's entries, the per-config tables and the counts below were checked against
the configs, and each row's source folder against the source tree, on 2026-10-28. A commit that adds, removes or
retargets a mixin updates its row here; `scripts/check-mixin-inventory.py` fails when a config lists a mixin that its
table here leaves out. Source line numbers are left out on purpose; open the class.

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
| `mc/neoforge/1.21.1/src/main/resources/fabricated.mixins.json` | `io.github.cmdralph.fabricated.neoforge.mixin` | 1.21.1 | mixins 48, client 15 | 1 | true | `FabricatedMixinPlugin` (creates Fabric language adapters in `onLoad`; filters nothing) |
| `mc/neoforge/1.21.11/src/main/resources/fabricated.mixins.json` | same | 1.21.11 | mixins 57, client 23 | 1 | true | same |
| `mc/neoforge/common26/src/main/resources/fabricated.mixins.json` | same | 26.x | mixins 68, client 26 | 1 | true | same |
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
| `mc/neoforge/1.21.1/src/main/resources/fabricated-renderer.mixins.json` | `io.github.cmdralph.fabricated.neoforge.rendering.mixin` | 1.21.1, only with `fabric_renderer_api_v1` (FML `requiredMods`) | none | 1 | true | none |
| `mc/neoforge/1.21.11/src/main/resources/fabricated-renderer.mixins.json` | same | 1.21.11, only with `fabric_renderer_api_v1` (FML `requiredMods`) | client 2 | 1 | true | none |
| `mc/neoforge/common26/src/main/resources/fabricated-renderer.mixins.json` | same | 26.x, only with `fabric_renderer_api_v1` (FML `requiredMods`) | client 2 | 1 | true | none |
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
| `mc/neoforge/common/src/testmod/resources/fabricated-testmod.mixins.json` | `io.github.cmdralph.fabricated.testmod.mixin` | 1 | Plugin `TestMixinPlugin`. `BuiltInRegistriesMixin` (builds content from vanilla's registry bootstrap, CORPUS-31, and sees the built-in registries frozen, CORPUS-32), `LivingEntityMixin` (a private `self()` helper, as Fabric API's entity events module has, HIDE-1), `MinecraftServerMixin` (`@Inject tickServer TAIL`: mixins apply), `PackRepositoryMixin` (leaves a pack repository's sources immutable, as FrozenLib does, PACK-1), `PlayerListMixin` (replaces the server's player list, POLY-1), `RegistryDataLoaderMixin` (writes to built-in registries from that class's initializer, as Wover does, REG-3); client: `client.MinecraftStopMixin` (CI diagnostics: logs who asked the game to stop), `client.MinecraftEarlyReloadListenerMixin` (registers a reload listener from `Minecraft`'s constructor, before NeoForge's event). All in `mc/neoforge/common/src/testmod/java/.../mixin/` |
| `mc/neoforge/common26/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json` | `io.github.cmdralph.fabricated.testmod.frapi.mixin` | 1 | `BakedQuadMixin` (`@Inject <init> RETURN` with vanilla's parameters, `mc/neoforge/common26/src/testmodfrapi/java/.../mixin/BakedQuadMixin.java`). Exercises pre-flight's constructor repair and handler bridge (CI's renderer API gate) |
| `mc/shared/src/testmodbroken/resources/badmixin/fabricated-testmod-badmixin.mixins.json` | `io.github.cmdralph.fabricated.testmod.broken.mixin` | unset | `ImpossibleMixin` (`@Inject method="fabricatedMethodThatDoesNotExist" HEAD require=1`, `.../ImpossibleMixin.java`). Meant to fail pre-flight, which leaves its injector out as the mixin is self-contained (`ci/neoforge/smoke-server.sh`) |

## Fragility review

- **Every config** is `required`, with `defaultRequire: 1` and `overwrites.requireAnnotations: true` (the last one was
  missing from both `fabricated.mixins.json` files and `fabricated-fabric-api.mixins.json` until 2026-10-19). A
  missing target, a missing injection point or an accidental overwrite of a method the target declares fails when
  the game applies the mixin, which every CI run does.
- **`require = 0`**, each with a fallback (all in `mc/neoforge/common`, so on every version):
  `ChunkGeneratorFeaturesMixin` (`refreshFeaturesPerStep` HEAD: only needed while NeoForge declares that method), `client.MinecraftEntrypointsMixin` (client entrypoints before the constructor's first
  `Thread.currentThread()`, with a required `TAIL` fallback), `client.MinecraftStartedMixin` (`CLIENT_STARTED` after
  `run()` sets `gameThread`, else on the first client tick).
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
  names NeoForge's `compile(..., List)`; with the name alone Mixin picks the first `compile`, NeoForge's delegate (this
  crashed CI on 2026-10-19). `client.ModelManagerExtraModelsMixin` names NeoForge's four-argument `discoverModelDependencies`.
- **Hot paths:** `client.SectionCompilerMixin` runs for every block of every chunk section, so it is a
  `@WrapWithCondition` (no allocation), not a `@WrapOperation`, whose `Operation.call(Object...)` boxes primitives.
  `client.MovingBlockRenderMixin` and `client.BlockBreakingRenderMixin` follow the same rule. `client.MultiPartModelMixin`
  and `client.WeightedVariantsMixin` answer from a field after the first question.
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
  `PacketDecoderContextMixin`, `PacketEncoderContextMixin` and `GenericPacketSplitterContextMixin` 500 (26.x); and
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

Versions: 1.21.1 · package `io.github.cmdralph.fabricated.neoforge.rendering.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · loaded only when `fabric_renderer_api_v1` is a loaded mod, as on the other versions · lists no mixins (its `client` section is empty).

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
| mixins | ResourceMixin | 1.21.1 (1.21.1) | n.m.server.packs.resources.Resource | no injectors; implements FabricResource; adds methods getFabricPackSource | | | | |
| mixins | MappedRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | no injectors; implements FabricRegistry | | | | |
| mixins | MappedRegistryEarlyWriteMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepPendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$restorePendingHolders | "freeze" | RETURN | — | default |
| mixins | BuiltInRegistriesFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @WrapOperation → fabricated$freezeAfterMainEntrypoints (the bootstrap's `freeze` waits for the main entrypoints, as on Fabric, CORPUS-32) | "bootStrap" | INVOKE `L…/BuiltInRegistries;freeze()V` | — | default |
| mixins | BuiltInRegistriesInvoker | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Invoker → fabricated$freeze | "freeze" |  | — | default |
| mixins | GameDataFreezeMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.registries.GameData | @WrapMethod → fabricated$freezeBuiltInRegistries (runs the waiting `freeze` in NeoForge's first freeze after the entrypoints) | "freezeData" |  | — | default |
| mixins | DefaultAttributesValidationMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | PackRepositorySourcesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (the sources set made mutable again before NeoForge adds a pack finder, PACK-1) | "addPackFinder" (NeoForge's) | HEAD | — | default |
| mixins | EntityTypeBuilderMixin | 1.21.1 (1.21.1) | n.m.world.entity.EntityType$Builder | @WrapOperation → fabricated$allowNullId (Fabric's no-argument build() passes a null id); implements FabricEntityType.Builder, FabricEntityTypeImpl.Builder; adds methods alwaysUpdateVelocity, build, fabric_setLivingEntityBuilder, fabric_setMobEntityBuilder; @Unique fields fabricated$livingBuilder, fabricated$mobBuilder | "build" | INVOKE `L…/Util;fetchChoiceType(L…/DSL$TypeReference;L…/String;)L…/Type;` | — | default |
| mixins | EntityTypeBuilderMixin | 1.21.1 (1.21.1) | n.m.world.entity.EntityType$Builder | @Inject → fabricated$applyChildBuilders | "build" | RETURN | — | default |
| mixins | EntityDataSerializersMixin | 1.21.1 (1.21.1) | n.m.network.syncher.EntityDataSerializers | @Inject → fabricated$toNeoForgeRegistry (a mod's serializer goes to NeoForge's registry, CORPUS-41) | "registerSerializer" | HEAD cancellable=true | — | default |
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
| mixins | ServerPlayerGameModeMixin | 1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeRemoveBlock; @Shadow level, player; @Unique fields fabricated$removedBlockEntity | "removeBlock(L…/BlockPos;L…/BlockState;Z)Z" | HEAD | — | default |
| mixins | ServerPlayerGameModeMixin | 1.21.1 (1.21.1) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;Z)Z" | RETURN | — | default |
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
| mixins | NetworkRegistryMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$clientboundCodec (also Fabric codecs of `minecraft` payloads, NET-4) | "getCodec" | HEAD cancellable=true | — | default |
| mixins | MenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.MenuProvider | no injectors; implements FabricScreenHandlerFactory | | | | |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
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
| mixins | DefaultAttributesValidationMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | PackRepositorySourcesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (the sources set made mutable again before NeoForge adds a pack finder, PACK-1) | "addPackFinder" (NeoForge's) | HEAD | — | default |
| mixins | MobEffectMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.effect.MobEffect | no injectors; implements FabricMobEffect | | | | |
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
| mixins | MenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.MenuProvider | no injectors; implements FabricScreenHandlerFactory | | | | |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | common121 (1.21.1,1.21.11) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | common121 (1.21.1,1.21.11) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
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
| client | client.ModelManagerExtraModelsMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.client.resources.model.ModelManager | @Inject → fabricated$fabricExtraModels | "discoverModelDependencies(…, StandaloneModelLoader$LoadedModels)" (NeoForge's four-argument overload) | INVOKE `L…/ModelDiscovery;resolve()L…/Map;` | — | default |
| client | client.UnbakedModelParserMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.client.model.UnbakedModelParser$Deserializer | @Inject → fabricated$fabricTypes | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/UnbakedModel;" | HEAD cancellable=true | — | default |
| client | client.BlockStateModelUnbakedMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.BlockStateModel$Unbaked | @ModifyExpressionValue → fabricated$fabricTypes | "<clinit>" | INVOKE `L…/Codec;flatComapMap(…)L…/Codec;` slice from PUTSTATIC `HARDCODED_WEIGHTED_CODEC` | — | default |
| client | client.ModelManagerExtraModelsUploadMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.ModelManager | @WrapOperation → fabricated$fabricExtraModels | "apply" | INVOKE `L…/ClientHooks;onModelBake(…)V` | — | default |
| client | client.ModelManagerSpriteGetterMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.ModelManager$1 | @Inject → fabricated$keepAtlases; adds method spriteFinder (Fabric's sprite getter API); @Shadow blockMissing; @Unique fabricated$atlases | "<init>" | RETURN sugar:@Local(argsOnly = true, ordinal = 0),@Local(argsOnly = true, ordinal = 1) | — | default |
| client | client.MultiPartModelMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.multipart.MultiPartModel | no injectors; implements FabricBlockRendering.Composite; @Shadow shared, blockState; @Unique fabricated$fabricPart | | | | |
| client | client.SectionCompilerMixin | 1.21.11 (1.21.11) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$skipFabricParts | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's level-aware overload) | — | default |
| client | client.SectionCompilerMixin | 1.21.11 (1.21.11) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockRenderDispatcher;renderBatched(…;L…/Function;ZL…/List;)V` | — | default |
| client | client.WeightedVariantsMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$fabricPart | | | | |
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
| mixins | DefaultAttributesValidationMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | PackRepositorySourcesMixin | common (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricated$keepSourcesMutable (the sources set made mutable again before NeoForge adds a pack finder, PACK-1) | "addPackFinder" (NeoForge's) | HEAD | — | default |
| mixins | MobEffectMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | n.m.world.effect.MobEffect | no injectors; implements FabricMobEffect | | | | |
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
| client | SectionCompilerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true),@Local(name = "startedLayers"); implements FabricBlockRendering.SectionLayers | — | default |
| client | MovingBlockRenderMixin | 26.1.2 | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "renderMovingBlockSubmits" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true) BufferSource, OptionsRenderState, @Local(name = "poseStack"); implements FabricBlockRendering.MovingBlockBuffers | — | default |
| client | MovingBlockRenderMixin | 26.2, 26.3 (one file each) | n.m.client.renderer.feature.MovingBlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "buildGroup" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true) FeatureFrameContext, @Local(name = "submit"); extends RenderTypeFeatureRenderer; implements FabricBlockRendering.MovingBlockBuffers | — | default |
| client | BlockBreakingRenderMixin | 26.1.2 | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "renderBreakingBlockModelSubmits" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's overload) | — | default |
| client | BlockBreakingRenderMixin | common26late (26.2,26.3) | n.m.client.renderer.LevelRenderer | @WrapWithCondition → fabricated$fabricModels | "submitBlockDestroyAnimation(L…/PoseStack;L…/SubmitNodeCollector;L…/LevelRenderState;)V" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's overload) | — | default |
| client | client.GuiRendererPictureInPictureMixin | 26.1.2 | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$prepareModRenderer (vanilla's map of picture-in-picture renderers given back, PIP-1); non-@Unique field pictureInPictureRenderers | "preparePictureInPictureState" | HEAD cancellable=true | — | default |
| client | client.GuiRendererPictureInPictureMixin | 26.1.2 | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$closeModRenderers | "close" | TAIL | — | default |
| client | client.GuiRendererPictureInPictureMixin | common26late (26.2,26.3) | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$prepareModRenderer (vanilla's map of picture-in-picture renderers given back, PIP-1); non-@Unique field pictureInPictureRenderers | "preparePictureInPictureState" | HEAD cancellable=true | — | default |
| client | client.GuiRendererPictureInPictureMixin | common26late (26.2,26.3) | n.m.client.gui.render.GuiRenderer prio=100 | @Inject → fabricated$closeModRenderers | "close" | TAIL | — | default |
| client | MultiPartModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.multipart.MultiPartModel | no injectors; implements FabricBlockRendering.Composite; @Shadow shared, blockState; @Unique fabricated$fabricPart | | | | |
| client | WeightedVariantsMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$fabricPart | | | | |
| client | UnbakedModelParserMixin | common12111 (1.21.11,26.1.2,26.2,26.3) | neo.client.model.UnbakedModelParser$Deserializer | @Inject → fabricated$fabricTypes | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/UnbakedModel;" (with descriptor: the class also has the compiler's bridge method) | HEAD cancellable=true | — | default |
| client | BlockStateModelUnbakedMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModel$Unbaked (interface) | @ModifyExpressionValue → fabricated$fabricTypes | "<clinit>" | INVOKE `L…/Codec;flatComapMap(L…/Function;L…/Function;)L…/Codec;`, slice from PUTSTATIC `HARDCODED_WEIGHTED_CODEC` | — | default |

### `mc/neoforge/common26/src/main/resources/fabricated-renderer.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.neoforge.rendering.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · loaded only when `fabric_renderer_api_v1` (Fabric API's renderer module) is a loaded mod (`requiredMods` in `mc/neoforge/common/src/main/resources/META-INF/neoforge.mods.toml`), so its classes may name Fabric's renderer API.

| Section | Mixin | Versions | Target | Injector → handler | Method selector | @At | ordinal | require |
|---|---|---|---|---|---|---|---|---|
| client | BlockStateModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModel (interface) | no injectors; extends FabricBlockStateModel; adds default emitQuads (FrapiDefaults: NeoForge's level-aware collectParts) | | | | |
| client | BlockStateModelPartMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModelPart (interface) | no injectors; extends FabricBlockStateModelPart; adds default emitQuads (FrapiDefaults: NeoForge's part and quad ambient occlusion) | | | | |

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

### `mc/neoforge/common/src/testmod/resources/fabricated-testmod.mixins.json`

Versions: 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.mixin` · required=true · defaultRequire=1 · plugin=`TestMixinPlugin` (checks the test mod's language adapter ran first) · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BuiltInRegistriesMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Inject → fabricatedTest$buildContent (builds content as vanilla's bootstrap creates the registries' contents, CORPUS-31) | "createContents" | TAIL | — | default |
| mixins | BuiltInRegistriesMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.core.registries.BuiltInRegistries | @Inject → fabricatedTest$frozen (sees the built-in registries frozen, as Wover does, CORPUS-32) | "freeze" | HEAD | — | default |
| mixins | LivingEntityMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricatedTest$useSelf; adds a @Unique private self(), the helper Fabric API's entity events module has (HIDE-1) | "getMaxHealth" | HEAD | — | default |
| mixins | MinecraftServerMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$afterTick | "tickServer" | TAIL | — | default |
| mixins | PackRepositoryMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.repository.PackRepository | @Inject → fabricatedTest$immutableSources (leaves the sources in an immutable set, PACK-1) | "<init>" | RETURN | — | default |
| mixins | PlayerListMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.server.players.PlayerList | @Inject → fabricatedTest$replaceList (replaces the player list, POLY-1) | "<init>" | RETURN | — | default |
| mixins | RegistryDataLoaderMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.resources.RegistryDataLoader | @Inject → fabricatedTest$writeEarly (writes to built-in registries, REG-3) | "<clinit>" | HEAD | — | default |
| client | client.MinecraftStopMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricatedTest$whoStops (CI diagnostics) | "stop" | HEAD | — | default |
| client | client.MinecraftEarlyReloadListenerMixin | common/testmod (1.21.1,1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricatedTest$registerEarly (a reload listener registered before NeoForge's event) | "<init>" | INVOKE `L…/ReloadableResourceManager;registerReloadListener(L…/PreparableReloadListener;)V` | 0 | default |

### `mc/neoforge/common26/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.frapi.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BakedQuadMixin | common26/testmodfrapi (26.1.2,26.2,26.3) | n.m.c.resources.model.geometry.BakedQuad | @Inject → fabricatedTest$mark (moved by pre-flight to NeoForge's constructor through a bridge) | "<init>" | RETURN | — | default |

### `mc/shared/src/testmodbroken/resources/badmixin/fabricated-testmod-badmixin.mixins.json`

Versions: all · package `io.github.cmdralph.fabricated.testmod.broken.mixin` · required=true · defaultRequire=unset (Mixin default 0) · plugin=none · overwrites.requireAnnotations=unset

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ImpossibleMixin | common/testmodbroken (test only) | n.m.server.MinecraftServer | @Inject → fabricatedTest$never | "fabricatedMethodThatDoesNotExist" | HEAD | — | require=1 |

---
