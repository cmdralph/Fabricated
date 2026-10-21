# Mixin inventory

Every mixin Fabricated ships, by config: target, injector, injection point, `require` and the versions that compile
it. Snapshot taken 2026-10-19 by scanning the sources (see `roadmap.md`), then updated by hand for that session's
changes. A commit that adds, removes or retargets a mixin updates its row here. Source line numbers are left out on
purpose; open the class.

Path shorthands: `N/` = `io/github/cmdralph/fabricated/neoforge/`, `n.m.` = `net.minecraft.`, `neo.` =
`net.neoforged.neoforge.`, `fapi.` = `net.fabricmc.fabric.`.

## Configs

Fabricated-owned (shipped in `game-mod.jar`, registered in `mc/common/src/main/resources/META-INF/neoforge.mods.toml:22-51`):

| Config file | Package | Versions | Sections | defaultRequire | overwrites.requireAnnotations | plugin |
|---|---|---|---|---|---|---|
| `mc/1.21.11/src/main/resources/fabricated.mixins.json` | `io.github.cmdralph.fabricated.neoforge.mixin` | 1.21.11 | mixins 52, client 23 | 1 | true | `FabricatedMixinPlugin` (creates Fabric language adapters in `onLoad`; filters nothing) |
| `mc/common26/src/main/resources/fabricated.mixins.json` | same | 26.1.2, 26.2, 26.3 | mixins 62, client 26 | 1 | true | same |
| `mc/common/src/main/resources/fabricated-fabric-api.mixins.json` | `net.fabricmc.fabric.mixin.networking.accessor` | all | mixins 4 | 1 | true | none |
| `mc/common/src/main/resources/fabricated-lookup.mixins.json` | `net.fabricmc.fabric.mixin.lookup` | all | mixins 2 (classes in `mc/<v>/src/fabricapi/java`) | 1 | true | none |
| `mc/common/src/main/resources/fabricated-loot.mixins.json` | `net.fabricmc.fabric.mixin.loot` | all | mixins 5 | 1 | true | none |
| `mc/1.21.11/src/main/resources/fabricated-content-registries.mixins.json` | `net.fabricmc.fabric.mixin.content.registry` | 1.21.11 | mixins 19 | 1 | true | none |
| `mc/26.1.2/src/main/resources/fabricated-content-registries.mixins.json` | same | 26.1.2 | mixins 27 (`mixinextras.minVersion 0.5.0`) | 1 | true | none |
| `mc/26.2/src/main/resources/fabricated-content-registries.mixins.json` | same | 26.2 | mixins 26 (`mixinextras.minVersion 0.5.0`) | 1 | true | none |
| `mc/26.3/src/main/resources/fabricated-content-registries.mixins.json` | same | 26.3 | mixins 18 (`mixinextras.minVersion 0.5.0`) | 1 | true | none |
| `mc/1.21.11/src/main/resources/fabricated-transfer.mixins.json` | `net.fabricmc.fabric.mixin.transfer` | 1.21.11 | mixins 16 | 1 | true | none |
| `mc/common26/src/main/resources/fabricated-transfer.mixins.json` | same | 26.x | mixins 17 | 1 | true | none |
| `mc/common26/src/main/resources/fabricated-renderer.mixins.json` | `io.github.cmdralph.fabricated.neoforge.rendering.mixin` | 26.x, only with `fabric_renderer_api_v1` (FML `requiredMods`) | client 2 | 1 | true | none |
| `mc/1.21.11/src/main/resources/fabricated-renderer.mixins.json` | same | 1.21.11, only with `fabric_renderer_api_v1` | client 2 (`BlockStateModelMixin`, `BlockModelPartMixin`: the NeoForge-aware `emitQuads` defaults, as on 26.x, and NeoForge's level-aware `collectParts` for Fabric models, made from their quads) | 1 | true | none |

All have `"required": true`, `compatibilityLevel JAVA_21`, no `minVersion`, no config-level `priority`, no `refmap`, no `server` section.
Mixin classes per version (2026-10-19 scan, before this session added 3): 1.21.11 = 96, 26.1.2 = 116, 26.2 = 115, 26.3 = 107. Every listed class resolves to exactly one file for
each version. No `@Mixin` class is compiled into a version without being listed in that version's configs (orphan check is clean).

Test-mod configs (not shipped):

| Config | Package | defaultRequire | Mixins |
|---|---|---|---|
| `mc/common/src/testmod/resources/fabricated-testmod.mixins.json` | `io.github.cmdralph.fabricated.testmod.mixin` | 1 | `MinecraftServerMixin` (`@Inject tickServer TAIL`, `mc/common/src/testmod/java/.../mixin/MinecraftServerMixin.java`) |
| `mc/common26/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json` | `io.github.cmdralph.fabricated.testmod.frapi.mixin` | 1 | `BakedQuadMixin` (`@Inject <init> RETURN` with vanilla's parameters, `mc/common26/src/testmodfrapi/java/.../mixin/BakedQuadMixin.java`). Exercises pre-flight's constructor repair and handler bridge (CI's renderer API gate) |
| `mc/common/src/testmodbroken/resources/badmixin/fabricated-testmod-badmixin.mixins.json` | `io.github.cmdralph.fabricated.testmod.broken.mixin` | unset | `ImpossibleMixin` (`@Inject method="fabricatedMethodThatDoesNotExist" HEAD require=1`, `.../ImpossibleMixin.java`). Meant to fail pre-flight, which leaves its injector out as the mixin is self-contained (`ci/smoke-server.sh`) |

## Fragility review

- **Every config** is `required`, with `defaultRequire: 1` and `overwrites.requireAnnotations: true` (the last one was
  missing from both `fabricated.mixins.json` files and `fabricated-fabric-api.mixins.json` until 2026-10-19). A
  missing target, a missing injection point or an accidental overwrite of a method the target declares fails when
  the game applies the mixin, which every CI run does.
- **`require = 0`**, each with a fallback (all in `mc/common`, so on every version):
  `ChunkGeneratorFeaturesMixin` (`refreshFeaturesPerStep` HEAD: only needed while NeoForge declares that method, which
  it does on all four lines), `client.MinecraftEntrypointsMixin` (client entrypoints before the constructor's first
  `Thread.currentThread()`, with a required `TAIL` fallback), `client.MinecraftStartedMixin` (`CLIENT_STARTED` after
  `run()` sets `gameThread`, else on the first client tick).
- **Ordinal-based points:** only the two `ordinal = 0` points above. Ordinal or index captures that are not
  injection points: `@Local(ordinal = 1) ItemStack` in 1.21.11's `transfer.CrafterBlockMixin` and `index = 4` in
  1.21.11's `transfer.BucketItemMixin` (26.x captures by name).
- **`@Overwrite`, `@Redirect`, `@ModifyConstant`, `@ModifyArgs`:** none. `expect = 0`: none. `allow = 1`:
  `transfer.DropperBlockMixin.hookDispense` (upstream).
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
  (26.1.2: `BlockFeatureRenderer`; 26.2, 26.3: `LevelRenderer`, from `mc/common26late`).
  `LevelChunkBlockEntityMixin` touches `getBlockEntity` only at its rare `Map.remove` calls.
- **Client/common placement:** no mixin under `"mixins"` targets a client class; every mixin under `"client"` does.
- **Placeholder:** `AdvancementReloadMixin` is empty on 26.1.2 and 26.2 (`mc/common26early`): the shared 26.x config
  lists it and only 26.3 needs it.
- **Priorities:** `WalkNodeEvaluatorMixin` 999 and `LootTableMixin` 3000, both upstream's values.

## Per-config tables

Columns: Source (versions) = the directory the class lives in, plus the versions that compile and list it. `n.m.` = `net.minecraft.`,
`neo.` = `net.neoforged.neoforge.`, `fapi.` = `net.fabricmc.fabric.`. `L…/X;` abbreviates a JVM descriptor's package. "default" in the
last column means the config's `defaultRequire` applies (1 for every Fabricated config). Mixins without injectors list what they merge.


### `mc/1.21.11/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 1.21.11 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockBehaviourBlockStateBaseMixin | 1.21.11 (1.21.11) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | no injectors; implements OxidizableBlocksRegistryImpl.RandomTickCacheRefresher; adds methods fabric_api$refreshRandomTickCache | | | | |
| mixins | BlockBehaviourAccessor | 1.21.11 (1.21.11) | n.m.world.level.block.state.BlockBehaviour | @Invoker → callHasRandomTicks | "isRandomlyTicking" |  | — | default |
| mixins | AxeItemAccessor | 1.21.11 (1.21.11) | n.m.world.item.AxeItem | @Accessor → getStrippedBlocks | "STRIPPABLES" |  | — | default |
| mixins | AxeItemAccessor | 1.21.11 (1.21.11) | n.m.world.item.AxeItem | @Accessor → setStrippedBlocks | "STRIPPABLES" |  | — | default |
| mixins | AxeItemMixin | 1.21.11 (1.21.11) | n.m.world.item.AxeItem | @ModifyArg → handleCustomStrippingBehavior | "getStripped" | INVOKE `L…/Optional;map(L…/Function;)L…/Optional;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | PotionBrewingBuilderMixin | 1.21.11 (1.21.11) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build | "build" | HEAD | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_setCompostables | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | WorkAtComposterAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.WorkAtComposter | @Accessor → fabric_getCompostable | "COMPOSTABLE_ITEMS" |  | — | default |
| mixins | FireBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.FireBlock | @Inject → afterConstruct | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftToHeroAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoeItemAccessor | 1.21.11 (1.21.11) | n.m.world.item.HoeItem | @Accessor → getTillingActions | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | WalkNodeEvaluatorMixin | 1.21.11 (1.21.11) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local | — | default |
| mixins | WeatheringCopperMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | 1.21.11 (1.21.11) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local | — | default |
| mixins | ShovelItemAccessor | 1.21.11 (1.21.11) | n.m.world.item.ShovelItem | @Accessor → getPathStates | "FLATTENABLES" |  | — | default |
| mixins | VillagerAccessor | 1.21.11 (1.21.11) | n.m.world.entity.npc.villager.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerMixin | 1.21.11 (1.21.11) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | FabricatedComposterBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.ComposterBlock | @ModifyReturnValue → fabricated$tableChance | "getValue(L…/ItemStack;)F" | RETURN | — | default |
| mixins | FabricatedDataMapHooksMixin | 1.21.11 (1.21.11) | neo.common.DataMapHooks | @WrapOperation → fabricated$fuelEvents | "populateFuelValues" | INVOKE `L…/FuelValues$Builder;build()L…/FuelValues;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true) | — | default |

### `mc/1.21.11/src/main/resources/fabricated-transfer.mixins.json`

Versions: 1.21.11 · package `net.fabricmc.fabric.mixin.transfer` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | AbstractFurnaceBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.AbstractFurnaceBlockEntity | @Inject → setStackSuppressUpdate | "setItem" | HEAD cancellable=true | — | default |
| mixins | BaseContainerBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.BaseContainerBlockEntity | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |
| mixins | BucketItemAccessor | 1.21.11 (1.21.11) | n.m.world.item.BucketItem | @Accessor → fabric_getFluid | "content" |  | — | default |
| mixins | BucketItemMixin | 1.21.11 (1.21.11) | n.m.world.item.BucketItem | @ModifyVariable → hookEmptyingSound | "playEmptySound" | STORE index=4 | — | default |
| mixins | BundleContentsAccessor | 1.21.11 (1.21.11) | n.m.world.item.component.BundleContents | @Invoker → getOccupancy | "getWeight" |  | — | default |
| mixins | ChiseledBookShelfBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.ChiseledBookShelfBlockEntity | @Inject → setStackBypass | "setItem" | HEAD cancellable=true | — | default |
| mixins | CompoundContainerAccessor | 1.21.11 (1.21.11) | n.m.world.CompoundContainer | @Accessor → fabric_getFirst | "container1" |  | — | default |
| mixins | CompoundContainerAccessor | 1.21.11 (1.21.11) | n.m.world.CompoundContainer | @Accessor → fabric_getSecond | "container2" |  | — | default |
| mixins | CrafterBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.CrafterBlock | @Inject → transferOrSpawnStack | "dispenseItem" | INVOKE `L…/ItemStack;isEmpty()Z` shift=At.Shift.BEFORE  sugar:@Local,@Local,@Local(ordinal = 1) | — | default |
| mixins | DropperBlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.DropperBlock | @Inject → hookDispense | "dispenseFrom" | INVOKE `L…/DispenseItemBehavior;dispense(L…/BlockSource;L…/ItemStack;)L…/ItemStack;` cancellable=true | — | allow=1 |
| mixins | FluidMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @Inject → hookGetBucketFillSound | "getPickupSound" | HEAD cancellable=true | — | default |
| mixins | ItemContainerContentsAccessor | 1.21.11 (1.21.11) | n.m.world.item.component.ItemContainerContents | @Accessor → fabric_getStacks | "items" |  | — | default |
| mixins | ItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.Item | no injectors; implements ItemVariantCache; adds methods fabric_getCachedItemVariant; @Unique fields cachedItemVariant | | | | |
| mixins | JukeboxBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.JukeboxBlockEntity | @Inject → setStackBypass | "setTheItem" | HEAD cancellable=true | — | default |
| mixins | ListBackedContainerMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.ListBackedContainer | @WrapOperation → cancelMarkDirty | "setItem" | INVOKE `L…/ListBackedContainer;setChanged()V` | — | default |
| mixins | ShelfBlockEntityMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.ShelfBlockEntity | no injectors; implements SpecialLogicInventory, SpecialLogicAccess; adds methods fabric_onFinalCommit, fabric_setSuppress, fabric_shouldSuppressSpecialLogic; @Unique fields fabric_suppressSpecialLogic | | | | |
| mixins | SimpleContainerMixin | 1.21.11 (1.21.11) | n.m.world.SimpleContainer | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |

### `mc/1.21.11/src/main/resources/fabricated.mixins.json`

Versions: 1.21.11 · package `io.github.cmdralph.fabricated.neoforge.mixin` · required=true · defaultRequire=1 · plugin=`FabricatedMixinPlugin` · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ServerConfigurationPacketListenerImplMixin | 1.21.11 (1.21.11) | n.m.server.network.ServerConfigurationPacketListenerImpl | no injectors; implements FabricServerConfigurationNetworkHandler; adds methods addTask, completeTask | | | | |
| mixins | MinecraftServerMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | no injectors; implements DataResourceStore; adds methods getOrThrow | | | | |
| mixins | ChunkGeneratorFeaturesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.ChunkGenerator | @Inject → fabricated$keepNeoForgesLazy | "refreshFeaturesPerStep" | HEAD | — | require=0 |
| mixins | ReloadableServerResourcesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerResources | no injectors; implements FabricDataResourceStoreHolder; adds methods fabric$getDataResourceStore; @Unique fields fabricated$dataResourceStore | | | | |
| mixins | ResourceMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.Resource | no injectors; implements FabricResource; adds methods getFabricPackSource | | | | |
| mixins | MappedRegistryMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | no injectors; implements FabricRegistry | | | | |
| mixins | MappedRegistryEarlyWriteMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepPendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$restorePendingHolders | "freeze" | RETURN | — | default |
| mixins | DefaultAttributesValidationMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | MobEffectMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.effect.MobEffect | no injectors; implements FabricMobEffect | | | | |
| mixins | EntityTypeBuilderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.EntityType$Builder | @Inject → fabricated$applyChildBuilders | "build" | RETURN | — | default |
| mixins | BlockEntityTypeMixin | 1.21.11 (1.21.11) | n.m.world.level.block.entity.BlockEntityType | no injectors; implements FabricBlockEntityType; adds methods addSupportedBlock | | | | |
| mixins | PoiTypesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | BlockMixin | 1.21.11 (1.21.11) | n.m.world.level.block.Block | no injectors; implements FabricBlock; adds methods getAppearance, makesOpenTrapdoorAboveClimbable | | | | |
| mixins | BlockStateMixin | 1.21.11 (1.21.11) | n.m.world.level.block.state.BlockState | no injectors; implements FabricBlockState; adds methods getAppearance | | | | |
| mixins | ItemMixin | 1.21.11 (1.21.11) | n.m.world.item.Item | @Inject → fabricated$onConstruct | "<init>" | RETURN | — | default |
| mixins | ItemPropertiesMixin | 1.21.11 (1.21.11) | n.m.world.item.Item$Properties | no injectors; implements FabricItem.Settings; adds methods modelId | | | | |
| mixins | ItemStackMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack | | | | |
| mixins | TooltipFlagMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.TooltipFlag | no injectors; implements FabricTooltipFlag | | | | |
| mixins | DataComponentMapBuilderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.component.DataComponentMap$Builder | no injectors; implements FabricComponentMapBuilder; adds methods contains, getOrCreate, getOrEmpty | | | | |
| mixins | ServerPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | HEAD | — | default |
| mixins | ServerPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | RETURN | — | default |
| mixins | BlockStateBaseMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | @Inject → fabricated$useWithoutItem | "useWithoutItem" | HEAD cancellable=true | — | default |
| mixins | ItemStackUseMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | @WrapOperation → fabricated$use | "use" | INVOKE `L…/Item;use(L…/Level;L…/Player;L…/InteractionHand;)L…/InteractionResult;` | — | default |
| mixins | PickItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromBlock | "handlePickItemFromBlock" | INVOKE `L…/BlockState;getCloneItemStack(L…/BlockPos;L…/LevelReader;ZL…/Player;)L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | PickItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromEntity | "handlePickItemFromEntity" | INVOKE `L…/Entity;getPickResult()L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | FakePlayerMixin | common (1.21.11,26.1.2,26.2,26.3) | fapi.api.entity.FakePlayer | no injectors; implements IPlayerExtension; adds methods isFakePlayer | | | | |
| mixins | PlayerAdvancementsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$keepRealOwner | "setPlayer" | HEAD cancellable=true | — | default |
| mixins | PlayerAdvancementsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$noFakeAdvancements | "award" | HEAD cancellable=true | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @Inject → fabricated$startReload | "reload" | HEAD  sugar:@Local(argsOnly = true) | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | VibrationSystemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.gameevent.vibrations.VibrationSystem | @ModifyReturnValue → fabricated$tableFrequency | "getGameEventFrequency(L…/Holder;)I" | RETURN | — | default |
| mixins | NetworkAddonHolderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl<br>n.m.server.network.ServerLoginPacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| mixins | ServerPayloadSendMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;Lio/netty/channel/ChannelFutureListener;)V" | HEAD cancellable=true | — | default |
| mixins | ServerCommonPacketListenerDisconnectMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| mixins | BlockApiCacheInvalidationMixin | 1.21.11 (1.21.11) | n.m.server.level.ServerLevel | @Inject → fabricated$invalidateBlockApiCaches | "invalidateCapabilities(L…/BlockPos;)V" | TAIL | — | default |
| mixins | LivingEntityEffectsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectAdded | "onEffectAdded" | RETURN | — | default |
| mixins | LivingEntityEffectsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectsRemoved | "onEffectsRemoved" | RETURN | — | default |
| mixins | LivingEntityFluidTravelMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$throughFabricFluid (cancellable) | "shouldTravelInFluid" | RETURN | — | default |
| mixins | FluidTypeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" (NeoForge's) | INVOKE `L…/CommonHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | NetworkRegistryMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$clientboundCodec (also Fabric codecs of `minecraft` payloads, NET-4) | "getCodec" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryAccessor | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor fabricated$registrations, fabricated$serverboundHandlers, fabricated$clientboundHandlers, fabricated$setup; @Accessor (setter) fabricated$setSetup | | | | |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToClient | "checkPacket(L…/Packet;L…/ServerCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToServer | "checkPacket(L…/Packet;L…/ClientCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | VanillaNamespacePayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ServerboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| mixins | MenuProviderMixin | 1.21.11 (1.21.11) | n.m.world.MenuProvider | no injectors; implements FabricScreenHandlerFactory | | | | |
| mixins | ServerPlayerMenuMixin | 1.21.11 (1.21.11) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | 1.21.11 (1.21.11) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | 1.21.11 (1.21.11) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
| mixins | RegistryFriendlyByteBufMixin | 1.21.11 (1.21.11) | n.m.network.RegistryFriendlyByteBuf | no injectors; implements FabricRegistryByteBuf; adds methods fabric_getSendableConfigurationChannels, fabric_setSendableConfigurationChannels; @Unique fields fabricated$sendableConfigurationChannels | | | | |
| mixins | EnchantmentBuilderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.enchantment.Enchantment$Builder | @Inject → fabricated$markModified | "*" | RETURN | — | default |
| mixins | RegistryDataLoaderEnchantmentMixin | 1.21.11 (1.21.11) | n.m.resources.RegistryDataLoader | @ModifyExpressionValue → fabricated$modifyEnchantment | "loadElementFromResource" | INVOKE `L…/DataResult;getOrThrow()L…/Object;`  sugar:@Local(argsOnly = true),@Local(argsOnly = true),@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$blockEntityLoaded | "setBlockEntity" | INVOKE `L…/Map;put(…)L…/Object;` sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @WrapWithCondition → fabricated$blockEntityUnloaded | "setBlockEntity", "removeBlockEntity" | INVOKE `L…/BlockEntity;setRemoved()V` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$removedBlockEntityDropped | "getBlockEntity(L…/BlockPos;L…/LevelChunk$EntityCreationType;)L…/BlockEntity;" | INVOKE `L…/Map;remove(L…/Object;)L…/Object;` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @Inject → fabricated$blockEntitiesReplaced | "replaceWithPacketData" | HEAD | — | default |
| client | client.BlockColorsLookupMixin | 1.21.11 (1.21.11) | n.m.client.color.block.BlockColors | no injectors; adds methods get | | | | |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onCreated | "<init>" | TAIL | — | default |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| client | client.ClientReloadListenerMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$holdBackEarlyListener (cancellable) | "registerReloadListener" | HEAD | — | default |
| client | client.KeyMappingAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.KeyMapping | @Accessor → fabricated$getClickCount | "clickCount" |  | — | default |
| client | client.MinecraftEntrypointsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypoints | "<init>" | INVOKE `L…/Thread;currentThread()L…/Thread;` | 0 | require=0 |
| client | client.MinecraftEntrypointsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypointsLate | "<init>" | TAIL | — | default |
| client | client.MinecraftStartedMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$clientStarted | "run" | FIELD `L…/Minecraft;gameThread:L…/Thread;` opcode=Opcodes.PUTFIELD shift=At.Shift.AFTER | 0 | require=0 |
| client | client.LiquidBlockTintMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.LiquidBlockRenderer | @ModifyExpressionValue → fabricated$fabricFluidColor | "tesselate" | INVOKE `L…/IClientFluidTypeExtensions;getTintColor(L…/FluidState;L…/BlockAndTintGetter;L…/BlockPos;)I` | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$beforeDestroyBlock | "destroyBlock" | HEAD | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$afterDestroyBlock | "destroyBlock" | RETURN | — | default |
| client | client.ClientNetworkAddonHolderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl<br>n.m.client.multiplayer.ClientHandshakePacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| client | client.ClientPayloadSendMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;)V" | HEAD cancellable=true | — | default |
| client | client.ClientVanillaNamespacePayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ClientboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| client | client.ParticleResourcesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.particle.ParticleResources | no injectors; non-@Unique fields providers | | | | |
| client | client.SoundEngineStreamMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.sounds.SoundEngine | @WrapOperation → fabricated$fabricAudioStream | "play" | INVOKE `L…/SoundInstance;getStream(L…/SoundBufferLibrary;L…/Sound;Z)L…/CompletableFuture;` | — | default |
| client | ClientChunkCacheMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @ModifyExpressionValue → fabricated$unloadOutOfRange | "updateViewRadius" | INVOKE `L…/ClientChunkCache$Storage;inRange(II)Z` sugar:@Local LevelChunk | — | default |
| client | ClientChunkCacheMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @Inject → fabricated$unloadReplaced | "replaceWithPacketData" | NEW `L…/LevelChunk` sugar:@Local LevelChunk | — | default |
| client | client.ModelManagerExtraModelsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.resources.model.ModelManager | @Inject → fabricated$fabricExtraModels | "discoverModelDependencies(…, StandaloneModelLoader$LoadedModels)" (NeoForge's four-argument overload) | INVOKE `L…/ModelDiscovery;resolve()L…/Map;` | — | default |
| client | client.UnbakedModelParserMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.client.model.UnbakedModelParser$Deserializer | @Inject → fabricated$fabricTypes | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/UnbakedModel;" | HEAD cancellable=true | — | default |
| client | client.BlockStateModelUnbakedMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.BlockStateModel$Unbaked | @ModifyExpressionValue → fabricated$fabricTypes | "<clinit>" | INVOKE `L…/Codec;flatComapMap(…)L…/Codec;` slice from PUTSTATIC `HARDCODED_WEIGHTED_CODEC` | — | default |
| client | client.ModelManagerExtraModelsUploadMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.ModelManager | @WrapOperation → fabricated$fabricExtraModels | "apply" | INVOKE `L…/ClientHooks;onModelBake(…)V` | — | default |
| client | client.ModelManagerSpriteGetterMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.ModelManager$1 | @Inject → fabricated$keepAtlases; adds method spriteFinder (Fabric's sprite getter API); @Shadow blockMissing; @Unique fabricated$atlases | "<init>" | RETURN sugar:@Local(argsOnly = true, ordinal = 0),@Local(argsOnly = true, ordinal = 1) | — | default |
| client | client.MultiPartModelMixin | 1.21.11 (1.21.11) | n.m.client.renderer.block.model.multipart.MultiPartModel | no injectors; implements FabricBlockRendering.Composite; @Shadow shared, blockState; @Unique fabricated$fabricPart | | | | |
| client | client.SectionCompilerMixin | 1.21.11 (1.21.11) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$skipFabricParts | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's level-aware overload) | — | default |
| client | client.SectionCompilerMixin | 1.21.11 (1.21.11) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/BlockRenderDispatcher;renderBatched(…;L…/Function;ZL…/List;)V` | — | default |
| client | client.WeightedVariantsMixin | 1.21.11 (1.21.11) | n.m.client.resources.model.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$fabricPart | | | | |

### `mc/26.1.2/src/main/resources/fabricated-content-registries.mixins.json`

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
| mixins | GiveGiftToHeroAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.HoeItem | @Accessor → getTillables | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local(name = "pos") | — | default |
| mixins | PotionBrewingBuilderMixin | common26early (26.1.2,26.2) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build | "build" | HEAD | — | default |
| mixins | ShovelItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.ShovelItem | @Accessor → getFlattenables | "FLATTENABLES" |  | — | default |
| mixins | VillagerAccessor | common26early (26.1.2,26.2) | n.m.world.entity.npc.villager.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | WalkNodeEvaluatorMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local(name = "blockState") | — | default |
| mixins | WeatheringCopperMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
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

### `mc/26.2/src/main/resources/fabricated-content-registries.mixins.json`

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
| mixins | GiveGiftToHeroAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoeItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.HoeItem | @Accessor → getTillables | "TILLABLES" |  | — | default |
| mixins | HoneycombItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local(name = "pos") | — | default |
| mixins | PotionBrewingBuilderMixin | common26early (26.1.2,26.2) | n.m.world.item.alchemy.PotionBrewing$Builder | @Inject → build | "build" | HEAD | — | default |
| mixins | ShovelItemAccessor | common26early (26.1.2,26.2) | n.m.world.item.ShovelItem | @Accessor → getFlattenables | "FLATTENABLES" |  | — | default |
| mixins | VillagerAccessor | common26early (26.1.2,26.2) | n.m.world.entity.npc.villager.Villager | @Accessor → fabric_setItemFoodValues | "FOOD_POINTS" |  | — | default |
| mixins | VillagerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | WalkNodeEvaluatorMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local(name = "blockState") | — | default |
| mixins | WeatheringCopperMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
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

### `mc/26.3/src/main/resources/fabricated-content-registries.mixins.json`

Versions: 26.3 · package `net.fabricmc.fabric.mixin.content.registry` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · mixinextras.minVersion=0.5.0

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | BlockBehaviourAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour | @Invoker → callIsRandomlyTicking | (from name callIsRandomlyTicking) |  | — | default |
| mixins | BlockBehaviourBlockStateBaseMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | no injectors; implements OxidizableBlocksRegistryImpl.RandomTickCacheRefresher; adds methods fabric_api$refreshRandomTickCache | | | | |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → afterConstruct | "<init>" | RETURN | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricBurnChance | "getIgniteOdds(L…/BlockState;)I" | HEAD cancellable=true | — | default |
| mixins | FireBlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.FireBlock | @Inject → getFabricSpreadChance | "getBurnOdds" | HEAD cancellable=true | — | default |
| mixins | GiveGiftToHeroAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Accessor → fabric_getGifts | "GIFTS" |  | — | default |
| mixins | GiveGiftToHeroMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.behavior.GiveGiftToHero | @Inject → makeMutable | "<clinit>" | TAIL | — | default |
| mixins | HoneycombItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.HoneycombItem | @Inject → createWaxables | "lambda$static$0" | RETURN cancellable=true | — | default |
| mixins | PathfindingContextMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.PathfindingContext | @Inject → onGetNodeType | "getPathTypeFromState" | INVOKE_ASSIGN `L…/BlockPos$MutableBlockPos;set(III)L…/BlockPos$MutableBlockPos;` cancellable=true sugar:@Local(name = "pos") | — | default |
| mixins | VillagerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.entity.npc.villager.Villager | @WrapOperation → useGatherableItemsSet | "wantsToPickUp" | INVOKE `L…/ItemStack;is(L…/TagKey;)Z` | — | default |
| mixins | WalkNodeEvaluatorMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.pathfinder.WalkNodeEvaluator prio=999 | @Inject → getCommonNodeType | "getPathTypeFromState" | INVOKE `L…/BlockState;getBlock()L…/Block;` cancellable=true sugar:@Local(name = "blockState") | — | default |
| mixins | WeatheringCopperMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.WeatheringCopper | @Inject → createOxidationLevelIncreasesMap | "lambda$static$0" | RETURN cancellable=true | — | default |
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

### `mc/common/src/main/resources/fabricated-fabric-api.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.networking.accessor` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ChunkMapAccessor | 1.21.11 (1.21.11) | n.m.server.level.ChunkMap | @Accessor → getEntityTrackers | "entityMap" |  | — | default |
| mixins | ChunkMapAccessor | common26 (26.1.2,26.2,26.3) | n.m.server.level.ChunkMap | @Accessor → getEntityMap | (from name getEntityMap) |  | — | default |
| mixins | EntityTrackerAccessor | 1.21.11 (1.21.11) | n.m.server.level.ChunkMap$TrackedEntity | @Accessor → getPlayersTracking | "seenBy" |  | — | default |
| mixins | EntityTrackerAccessor | common26 (26.1.2,26.2,26.3) | n.m.server.level.ChunkMap$TrackedEntity | @Accessor → getSeenBy | (from name getSeenBy) |  | — | default |
| mixins | ServerCommonPacketListenerImplAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Accessor → getServer | (from name getServer) |  | — | default |
| mixins | ServerLoginPacketListenerImplAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerLoginPacketListenerImpl | @Accessor → getServer | (from name getServer) |  | — | default |
| mixins | ServerLoginPacketListenerImplAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerLoginPacketListenerImpl | @Accessor → getConnection | (from name getConnection) |  | — | default |

### `mc/common/src/main/resources/fabricated-lookup.mixins.json`

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

### `mc/common/src/main/resources/fabricated-loot.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `net.fabricmc.fabric.mixin.loot` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | LootPoolAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getRolls | "rolls" |  | — | default |
| mixins | LootPoolAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getBonusRolls | "bonusRolls" |  | — | default |
| mixins | LootPoolAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getEntries | "entries" |  | — | default |
| mixins | LootPoolAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getConditions | "conditions" |  | — | default |
| mixins | LootPoolAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootPool | @Accessor → fabric_getFunctions | "functions" |  | — | default |
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
| mixins | LootPoolBuilderMixin | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods apply, conditionally, self, with | | | | |
| mixins | LootPoolBuilderMixin | 26.3 (26.3) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, apply, self, when | | | | |
| mixins | LootPoolBuilderMixin | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootPool$Builder | no injectors; implements FabricLootPoolBuilder; adds methods add, apply, self, when | | | | |
| mixins | LootTableAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequenceId | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getModifier | "modifier" |  | — | default |
| mixins | LootTableAccessor | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getPools | "pools" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getFunctions | "functions" |  | — | default |
| mixins | LootTableAccessor | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable | @Accessor → fabric_getRandomSequence | "randomSequence" |  | — | default |
| mixins | LootTableBuilderMixin | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |
| mixins | LootTableBuilderMixin | 26.3 (26.3) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |
| mixins | LootTableBuilderMixin | common26early (26.1.2,26.2) | n.m.world.level.storage.loot.LootTable$Builder | no injectors; implements FabricLootTableBuilder; adds methods apply, modifyPools, pool, pools, self | | | | |
| mixins | LootTableMixin | 1.21.11 (1.21.11) | n.m.world.level.storage.loot.LootTable prio=3000 | @WrapMethod → fabric$modifyDrops | "getRandomItemsRaw(L…/LootContext;L…/Consumer;)V" |  | — | default |
| mixins | LootTableMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.storage.loot.LootTable prio=3000 | @WrapMethod → fabric$modifyDrops | "getRandomItemsRaw(L…/LootContext;L…/Consumer;)V" |  | — | default |

### `mc/common26/src/main/resources/fabricated-transfer.mixins.json`

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
| mixins | FluidMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @Inject → hookGetBucketFillSound | "getPickupSound" | HEAD cancellable=true | — | default |
| mixins | ItemContainerContentsAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.item.component.ItemContainerContents | @Accessor → fabric_getItems | "items" |  | — | default |
| mixins | ItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.Item | no injectors; implements ItemVariantCache; adds methods fabric_getCachedItemVariant; @Unique fields cachedItemVariant | | | | |
| mixins | ItemStackAccessor | common26 (26.1.2,26.2,26.3) | n.m.world.item.ItemStack | @Invoker → validateComponents | "validateComponents" |  | — | default |
| mixins | JukeboxBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.JukeboxBlockEntity | @Inject → setStackBypass | "setTheItem" | HEAD cancellable=true | — | default |
| mixins | ListBackedContainerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.ListBackedContainer | @WrapOperation → cancelSetChanged | "setItem" | INVOKE `L…/ListBackedContainer;setChanged()V` | — | default |
| mixins | ShelfBlockEntityMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.ShelfBlockEntity | no injectors; implements SpecialLogicContainer, SpecialLogicAccess; adds methods fabric_onFinalCommit, fabric_setSuppress, fabric_shouldSuppressSpecialLogic; @Unique fields fabric_suppressSpecialLogic | | | | |
| mixins | SimpleContainerMixin | common26 (26.1.2,26.2,26.3) | n.m.world.SimpleContainer | @Inject → fabricated$deferSetChanged | "setItem(IL…/ItemStack;)V" | HEAD cancellable=true | — | default |

### `mc/common26/src/main/resources/fabricated.mixins.json`

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
| mixins | MinecraftServerMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | no injectors; implements DataResourceStore; adds methods getOrThrow | | | | |
| mixins | ChunkGeneratorFeaturesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.ChunkGenerator | @Inject → fabricated$keepNeoForgesLazy | "refreshFeaturesPerStep" | HEAD | — | require=0 |
| mixins | ReloadableServerResourcesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerResources | no injectors; implements FabricDataResourceStoreHolder; adds methods fabric$getDataResourceStore; @Unique fields fabricated$dataResourceStore | | | | |
| mixins | ResourceMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.Resource | no injectors; implements FabricResource; adds methods getFabricPackSource | | | | |
| mixins | MappedRegistryMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | no injectors; implements FabricRegistry | | | | |
| mixins | MappedRegistryEarlyWriteMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$openForEarlyWrite | "validateWrite(Lnet/minecraft/resources/ResourceKey;)V" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$keepPendingHolders | "freeze" | HEAD | — | default |
| mixins | MappedRegistryPendingHoldersMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.MappedRegistry | @Inject → fabricated$restorePendingHolders | "freeze" | RETURN | — | default |
| mixins | DefaultAttributesValidationMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.attributes.DefaultAttributes | @Inject → fabricated$validateAfterMainEntrypoints | "validate" | HEAD cancellable=true | — | default |
| mixins | MobEffectMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.effect.MobEffect | no injectors; implements FabricMobEffect | | | | |
| mixins | EntityTypeBuilderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.EntityType$Builder | @Inject → fabricated$applyChildBuilders | "build" | RETURN | — | default |
| mixins | BlockEntityTypeMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.entity.BlockEntityType | no injectors; implements FabricBlockEntityType; adds methods addValidBlock | | | | |
| mixins | PoiTypesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.ai.village.poi.PoiTypes | @Inject → fabricated$withoutCallbackEntries | "registerBlockStates" | HEAD | — | default |
| mixins | BlockMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.Block | no injectors; implements FabricBlock; adds methods getAppearance, getEnchantPowerBonus, makesOpenTrapdoorAboveClimbable | | | | |
| mixins | BlockStateMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockState | no injectors; implements FabricBlockState; adds methods getAppearance | | | | |
| mixins | BlockBehaviourPropertiesMixin | common26 (26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$Properties | no injectors; implements FabricBlock.FabricProperties; adds methods blockId | | | | |
| mixins | ItemMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.Item | @Inject → fabricated$onConstruct | "<init>" | RETURN | — | default |
| mixins | ItemDamageMixin | 26.3 (26.3) | n.m.world.item.Item | no injectors; implements IItemExtension, ItemExtensions; adds methods damageItem | | | | |
| mixins | ItemDamageMixin | common26early (26.1.2,26.2) | n.m.world.item.Item | no injectors; implements IItemExtension, ItemExtensions; adds methods damageItem | | | | |
| mixins | ItemPropertiesMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.Item$Properties | no injectors; implements FabricItem.Properties; adds methods itemId, modelId | | | | |
| mixins | ItemStackMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack | | | | |
| mixins | ItemStackRemainderMixin | common26 (26.1.2,26.2,26.3) | n.m.world.item.ItemStack | no injectors; implements FabricItemStack, ItemInstanceExtension; adds methods getCraftingRemainder | | | | |
| mixins | TooltipFlagMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.TooltipFlag | no injectors; implements FabricTooltipFlag | | | | |
| mixins | DataComponentMapBuilderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.core.component.DataComponentMap$Builder | no injectors; implements FabricComponentMapBuilder; adds methods contains, getOrCreate, getOrEmpty | | | | |
| mixins | ServerPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$beforeRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | HEAD | — | default |
| mixins | ServerPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.level.ServerPlayerGameMode | @Inject → fabricated$afterRemoveBlock | "removeBlock(L…/BlockPos;L…/BlockState;ZL…/ItemStack;)Z" | RETURN | — | default |
| mixins | BlockStateBaseMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.block.state.BlockBehaviour$BlockStateBase | @Inject → fabricated$useWithoutItem | "useWithoutItem" | HEAD cancellable=true | — | default |
| mixins | ItemStackUseMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.ItemStack | @WrapOperation → fabricated$use | "use" | INVOKE `L…/Item;use(L…/Level;L…/Player;L…/InteractionHand;)L…/InteractionResult;` | — | default |
| mixins | PickItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromBlock | "handlePickItemFromBlock" | INVOKE `L…/BlockState;getCloneItemStack(L…/BlockPos;L…/LevelReader;ZL…/Player;)L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | PickItemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerGamePacketListenerImpl | @WrapOperation → fabricated$pickFromEntity | "handlePickItemFromEntity" | INVOKE `L…/Entity;getPickResult()L…/ItemStack;`  sugar:@Local(argsOnly = true) | — | default |
| mixins | FakePlayerMixin | common (1.21.11,26.1.2,26.2,26.3) | fapi.api.entity.FakePlayer | no injectors; implements IPlayerExtension; adds methods isFakePlayer | | | | |
| mixins | PlayerAdvancementsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$keepRealOwner | "setPlayer" | HEAD cancellable=true | — | default |
| mixins | PlayerAdvancementsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.PlayerAdvancements | @Inject → fabricated$noFakeAdvancements | "award" | HEAD cancellable=true | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @Inject → fabricated$startReload | "reload" | HEAD  sugar:@Local(argsOnly = true) | — | default |
| mixins | ReloadableServerRegistriesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.ReloadableServerRegistries | @ModifyReturnValue → fabricated$afterReload | "reload" | RETURN  sugar:@Local(argsOnly = true) | — | default |
| mixins | VibrationSystemMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.gameevent.vibrations.VibrationSystem | @ModifyReturnValue → fabricated$tableFrequency | "getGameEventFrequency(L…/Holder;)I" | RETURN | — | default |
| mixins | NetworkAddonHolderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl<br>n.m.server.network.ServerLoginPacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| mixins | BlockApiCacheInvalidationMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerLevel | @Inject → fabricated$invalidateBlockApiCaches | "invalidateCapabilities(L…/BlockPos;)V" | TAIL | — | default |
| mixins | LivingEntityEffectsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectAdded | "onEffectAdded" | RETURN | — | default |
| mixins | LivingEntityEffectsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$afterEffectsRemoved | "onEffectsRemoved" | RETURN | — | default |
| mixins | LivingEntityFluidTravelMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.entity.LivingEntity | @Inject → fabricated$throughFabricFluid (cancellable) | "shouldTravelInFluid" | RETURN | — | default |
| mixins | FluidTypeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.material.Fluid | @WrapOperation → fabricated$fabricFluidType | "getFluidType" (NeoForge's) | INVOKE `L…/CommonHooks;getVanillaFluidType(L…/Fluid;)L…/FluidType;` | — | default |
| mixins | ServerPayloadSendMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;Lio/netty/channel/ChannelFutureListener;)V" | HEAD cancellable=true | — | default |
| mixins | ServerCommonPacketListenerDisconnectMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| mixins | NetworkRegistryMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$clientboundCodec (also Fabric codecs of `minecraft` payloads, NET-4) | "getCodec" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryAccessor | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Accessor fabricated$registrations, fabricated$serverboundHandlers, fabricated$clientboundHandlers, fabricated$setup; @Accessor (setter) fabricated$setSetup | | | | |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToClient | "checkPacket(L…/Packet;L…/ServerCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | NetworkRegistryUnregisteredPayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.network.registration.NetworkRegistry | @Inject → fabricated$sendUnregisteredToServer | "checkPacket(L…/Packet;L…/ClientCommonPacketListener;)V" | HEAD cancellable=true | — | default |
| mixins | VanillaNamespacePayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.network.ServerCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ServerboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| mixins | PacketDecoderContextMixin | common26 (26.1.2,26.2,26.3) | n.m.network.PacketDecoder | @WrapMethod → fabricated$withPacketContext | "decode" |  | — | default |
| mixins | PacketEncoderContextMixin | common26 (26.1.2,26.2,26.3) | n.m.network.PacketEncoder | @WrapMethod → fabricated$withPacketContext | "encode(L…/ChannelHandlerContext;L…/Packet;L…/ByteBuf;)V" |  | — | default |
| mixins | GenericPacketSplitterContextMixin | common26 (26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$encodeWithPacketContext | "encode(L…/ChannelHandlerContext;L…/Packet;L…/List;)V" |  | — | default |
| mixins | GenericPacketSplitterContextMixin | common26 (26.1.2,26.2,26.3) | neo.network.filters.GenericPacketSplitter | @WrapMethod → fabricated$decodeWithPacketContext | "handle" |  | — | default |
| mixins | MenuProviderMixin | common26 (26.1.2,26.2,26.3) | n.m.world.MenuProvider | no injectors; implements FabricMenuProvider | | | | |
| mixins | ServerPlayerMenuMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerPlayer | @WrapMethod → fabricated$trackOpener | OPEN_MENU |  | — | default |
| mixins | ServerPlayerMenuMixin | common26 (26.1.2,26.2,26.3) | n.m.server.level.ServerPlayer | @WrapOperation → fabricated$shouldCloseCurrentScreen | OPEN_MENU | INVOKE `L…/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z` | — | default |
| mixins | SimpleMenuProviderMixin | common26 (26.1.2,26.2,26.3) | n.m.world.SimpleMenuProvider | no injectors; implements MenuProvider; adds methods writeClientSideData | | | | |
| mixins | TagFileRemoveMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.tags.TagFile | @ModifyExpressionValue → fabricated$readFabricRemove | "<clinit>" | INVOKE `L…/RecordCodecBuilder;create(L…/Function;)L…/Codec;` | — | default |
| mixins | ConditionalOpsMixin | common (1.21.11,26.1.2,26.2,26.3) | neo.common.conditions.ConditionalOps | @ModifyReturnValue → fabricated$fabricConditions | "createConditionalCodec(L…/Codec;L…/String;)L…/Codec;" | RETURN | — | default |
| mixins | AdvancementReloadMixin | 26.3 (26.3) | n.m.server.ReloadableServerRegistries | @WrapOperation → fabricated$advancementReload | "reload" | INVOKE `L…/RegistryDataLoader;load(L…/ResourceManager;L…/List;L…/List;L…/Executor;L…/List;)L…/CompletableFuture;`  sugar:@Local(name = "loadingContextWithTags") | — | default |
| mixins | AdvancementReloadMixin | common26early (26.1.2,26.2) | n.m.server.ReloadableServerRegistries | no injectors | | | | |
| mixins | EnchantmentBuilderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.item.enchantment.Enchantment$Builder | @Inject → fabricated$markModified | "*" | RETURN | — | default |
| mixins | ResourceManagerRegistryLoadTaskMixin | common26 (26.1.2,26.2,26.3) | n.m.resources.ResourceManagerRegistryLoadTask | @Inject → fabricated$captureRegistries | "load" | HEAD | — | default |
| mixins | ResourceManagerRegistryLoadTaskMixin | common26 (26.1.2,26.2,26.3) | n.m.resources.ResourceManagerRegistryLoadTask | @WrapOperation → fabricated$modify | "lambda$load$2" | NEW `net/minecraft/resources/RegistryLoadTask$PendingRegistration`  sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$blockEntityLoaded | "setBlockEntity" | INVOKE `L…/Map;put(…)L…/Object;` sugar:@Local(argsOnly = true) | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @WrapWithCondition → fabricated$blockEntityUnloaded | "setBlockEntity", "removeBlockEntity" | INVOKE `L…/BlockEntity;setRemoved()V` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @ModifyExpressionValue → fabricated$removedBlockEntityDropped | "getBlockEntity(L…/BlockPos;L…/LevelChunk$EntityCreationType;)L…/BlockEntity;" | INVOKE `L…/Map;remove(L…/Object;)L…/Object;` | — | default |
| mixins | LevelChunkBlockEntityMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.world.level.chunk.LevelChunk | @Inject → fabricated$blockEntitiesReplaced | "replaceWithPacketData" | HEAD | — | default |
| client | client.ClientCommonPacketListenerImplMixin | common26 (26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | no injectors; implements PacketContextProvider; adds methods getPacketContext | | | | |
| client | client.LocalPlayerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.player.LocalPlayer | no injectors; implements PacketContextProvider; adds methods getPacketContext | | | | |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onCreated | "<init>" | TAIL | — | default |
| client | client.ClientConfigurationPacketListenerImplMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientConfigurationPacketListenerImpl | @Inject → fabricated$onDisconnect | "onDisconnect" | HEAD | — | default |
| client | client.ClientReloadListenerMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.server.packs.resources.ReloadableResourceManager | @Inject → fabricated$holdBackEarlyListener (cancellable) | "registerReloadListener" | HEAD | — | default |
| client | client.KeyMappingAccessor | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.KeyMapping | @Accessor → fabricated$getClickCount | "clickCount" |  | — | default |
| client | client.MinecraftEntrypointsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypoints | "<init>" | INVOKE `L…/Thread;currentThread()L…/Thread;` | 0 | require=0 |
| client | client.MinecraftEntrypointsMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$runClientEntrypointsLate | "<init>" | TAIL | — | default |
| client | client.MinecraftStartedMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.Minecraft | @Inject → fabricated$clientStarted | "run" | FIELD `L…/Minecraft;gameThread:L…/Thread;` opcode=Opcodes.PUTFIELD shift=At.Shift.AFTER | 0 | require=0 |
| client | client.ModelManagerExtraModelsMixin | common26 (26.1.2,26.2,26.3) | n.m.client.resources.model.ModelManager | @Inject → fabricated$fabricExtraModels | "discoverModelDependencies(L…/Map;L…/BlockStateModelLoader$LoadedModels;L…/ClientItemInfoLoader$LoadedClientInfos;L…/StandaloneModelLoader$LoadedModels;)L…/ModelManager$ResolvedModels;" | INVOKE `L…/ModelDiscovery;resolve()L…/Map;`  sugar:@Local | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$beforeDestroyBlock | "destroyBlock" | HEAD | — | default |
| client | client.MultiPlayerGameModeMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.MultiPlayerGameMode | @Inject → fabricated$afterDestroyBlock | "destroyBlock" | RETURN | — | default |
| client | client.MouseHandlerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.MouseHandler | @WrapOperation → fabricated$scrollHotbar | "onScroll" | INVOKE `L…/Inventory;setSelectedSlot(I)V` | — | default |
| client | client.ClientNetworkAddonHolderMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl<br>n.m.client.multiplayer.ClientHandshakePacketListenerImpl | no injectors; implements NetworkAddons.Holder; adds methods fabricated$networkAddon, fabricated$setNetworkAddon; @Unique fields fabricated$networkAddon | | | | |
| client | client.ClientPayloadSendMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$dropUnsendableFabricPayload | "send(L…/Packet;)V" | HEAD cancellable=true | — | default |
| client | client.ClientVanillaNamespacePayloadMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientCommonPacketListenerImpl | @Inject → fabricated$receiveVanillaNamespace | "handleCustomPayload(L…/ClientboundCustomPayloadPacket;)V" | HEAD cancellable=true | — | default |
| client | client.ClientHandshakePacketContextMixin | common26 (26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientHandshakePacketListenerImpl | @Inject → fabricated$storeGameProfile | "handleLoginFinished" | HEAD | — | default |
| client | client.ClientPacketListenerContextMixin | common26 (26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientPacketListener | @Inject → fabricated$storeRegistries | "<init>" | TAIL | — | default |
| client | client.SoundEngineStreamMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.sounds.SoundEngine | @WrapOperation → fabricated$fabricAudioStream | "play" | INVOKE `L…/SoundInstance;getStream(L…/SoundBufferLibrary;L…/Sound;Z)L…/CompletableFuture;` | — | default |
| client | client.ParticleResourcesMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.particle.ParticleResources | no injectors; non-@Unique fields providers | | | | |
| client | ClientChunkCacheMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @ModifyExpressionValue → fabricated$unloadOutOfRange | "updateViewRadius" | INVOKE `L…/ClientChunkCache$Storage;inRange(II)Z` sugar:@Local LevelChunk | — | default |
| client | ClientChunkCacheMixin | common (1.21.11,26.1.2,26.2,26.3) | n.m.client.multiplayer.ClientChunkCache | @Inject → fabricated$unloadReplaced | "replaceWithPacketData" | NEW `L…/LevelChunk` sugar:@Local LevelChunk | — | default |
| client | SectionCompilerMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.chunk.SectionCompiler | @WrapWithCondition → fabricated$fabricModels | "compile(L…/SectionPos;L…/RenderSectionRegion;L…/VertexSorting;L…/SectionBufferBuilderPack;L…/List;)L…/SectionCompiler$Results;" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true),@Local(name = "startedLayers"); implements FabricBlockRendering.SectionLayers | — | default |
| client | MovingBlockRenderMixin | 26.1.2 | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "renderMovingBlockSubmits" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true) BufferSource, OptionsRenderState, @Local(name = "poseStack"); implements FabricBlockRendering.MovingBlockBuffers | — | default |
| client | MovingBlockRenderMixin | 26.2, 26.3 (one file each) | n.m.client.renderer.feature.MovingBlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "buildGroup" | INVOKE `L…/ModelBlockRenderer;tesselateBlock(…)V` sugar:@Local(argsOnly = true) FeatureFrameContext, @Local(name = "submit"); extends RenderTypeFeatureRenderer; implements FabricBlockRendering.MovingBlockBuffers | — | default |
| client | BlockBreakingRenderMixin | 26.1.2 | n.m.client.renderer.feature.BlockFeatureRenderer | @WrapWithCondition → fabricated$fabricModels | "renderBreakingBlockModelSubmits" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's overload) | — | default |
| client | BlockBreakingRenderMixin | common26late (26.2,26.3) | n.m.client.renderer.LevelRenderer | @WrapWithCondition → fabricated$fabricModels | "submitBlockDestroyAnimation(L…/PoseStack;L…/SubmitNodeCollector;L…/LevelRenderState;)V" | INVOKE `L…/BlockStateModel;collectParts(L…/BlockAndTintGetter;L…/BlockPos;L…/BlockState;L…/RandomSource;L…/List;)V` (NeoForge's overload) | — | default |
| client | MultiPartModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.multipart.MultiPartModel | no injectors; implements FabricBlockRendering.Composite; @Shadow shared, blockState; @Unique fabricated$fabricPart | | | | |
| client | WeightedVariantsMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.WeightedVariants | no injectors; implements FabricBlockRendering.Composite; @Shadow list; @Unique fabricated$fabricPart | | | | |
| client | UnbakedModelParserMixin | common26 (26.1.2,26.2,26.3) | neo.client.model.UnbakedModelParser$Deserializer | @Inject → fabricated$fabricTypes | "deserialize(L…/JsonElement;L…/Type;L…/JsonDeserializationContext;)L…/UnbakedModel;" (with descriptor: the class also has the compiler's bridge method) | HEAD cancellable=true | — | default |
| client | BlockStateModelUnbakedMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModel$Unbaked (interface) | @ModifyExpressionValue → fabricated$fabricTypes | "<clinit>" | INVOKE `L…/Codec;flatComapMap(L…/Function;L…/Function;)L…/Codec;`, slice from PUTSTATIC `HARDCODED_WEIGHTED_CODEC` | — | default |

### `mc/common26/src/main/resources/fabricated-renderer.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.neoforge.rendering.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true · loaded only when `fabric_renderer_api_v1` (Fabric API's renderer module) is a loaded mod (`requiredMods` in `mc/common/src/main/resources/META-INF/neoforge.mods.toml`), so its classes may name Fabric's renderer API.

| Section | Mixin | Versions | Target | Injector → handler | Method selector | @At | ordinal | require |
|---|---|---|---|---|---|---|---|---|
| client | BlockStateModelMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModel (interface) | no injectors; extends FabricBlockStateModel; adds default emitQuads (FrapiDefaults: NeoForge's level-aware collectParts) | | | | |
| client | BlockStateModelPartMixin | common26 (26.1.2,26.2,26.3) | n.m.client.renderer.block.dispatch.BlockStateModelPart (interface) | no injectors; extends FabricBlockStateModelPart; adds default emitQuads (FrapiDefaults: NeoForge's part and quad ambient occlusion) | | | | |

### Test-mod configs (not shipped in the Fabricated jar)

### `mc/common/src/testmod/resources/fabricated-testmod.mixins.json`

Versions: 1.21.11, 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.mixin` · required=true · defaultRequire=1 · plugin=`TestMixinPlugin` (checks the test mod's language adapter ran first) · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | MinecraftServerMixin | common/testmod (1.21.11,26.1.2,26.2,26.3) | n.m.server.MinecraftServer | @Inject → fabricatedTest$afterTick | "tickServer" | TAIL | — | default |

### `mc/common26/src/testmodfrapi/resources/fabricated-testmod-frapi.mixins.json`

Versions: 26.1.2, 26.2, 26.3 · package `io.github.cmdralph.fabricated.testmod.frapi.mixin` · required=true · defaultRequire=1 · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| client | BakedQuadMixin | common26/testmodfrapi (26.1.2,26.2,26.3) | n.m.c.resources.model.geometry.BakedQuad | @Inject → fabricatedTest$mark (moved by pre-flight to NeoForge's constructor through a bridge) | "<init>" | RETURN | — | default |

### `mc/common/src/testmodbroken/resources/badmixin/fabricated-testmod-badmixin.mixins.json`

Versions: all · package `io.github.cmdralph.fabricated.testmod.broken.mixin` · required=true · defaultRequire=unset (Mixin default 0) · plugin=none · overwrites.requireAnnotations=true

| Section | Mixin | Source (versions) | Target(s) | Injector → handler | method | @At value / target / extras | ordinal | require/expect/allow |
|---|---|---|---|---|---|---|---|---|
| mixins | ImpossibleMixin | common/testmodbroken (test only) | n.m.server.MinecraftServer | @Inject → fabricatedTest$never | "fabricatedMethodThatDoesNotExist" | HEAD | — | require=1 |

---
