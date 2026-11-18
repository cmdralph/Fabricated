package io.github.cmdralph.fabricated.core.report;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

/**
 * What Fabricated reports and can't fix: each known limit with the one line the report adds to what matches it, naming
 * its finding (COMPATIBILITY.md, FINDINGS.md). A pre-flight finding that leaves a hook out or disables its mod matches by
 * the platform and its message or suggestion, and a limit of one mod's hooks also by their config and mixin, each named;
 * an entrypoint's failure by the class and message of what it threw, at the root of its causes. Nothing else changes.
 */
public final class KnownLimits {
	/**
	 * @param loaders   the platforms a pre-flight finding matches on ({@code PlatformDescriptor.loaderName}); none for a failure
	 * @param exception the class of the failure's root cause it matches, or null for a pre-flight finding
	 * @param pattern   what the finding's or the exception's message holds; one with two groups matches only where they
	 *                  differ
	 * @param applied   whether it matches pre-flight's note of a hook that applies and never runs, rather than a finding
	 *                  leaving one out
	 * @param mixins    the mixins it matches, each as its config and class ({@code "<config> <mixin>"}); empty for one of
	 *                  any mod's
	 */
	private record Limit(Set<String> loaders, String exception, Pattern pattern, String reason, boolean applied, Set<String> mixins) {
		Limit(Set<String> loaders, String exception, Pattern pattern, String reason) {
			this(loaders, exception, pattern, reason, false, Set.of());
		}

		Limit(Set<String> loaders, String exception, Pattern pattern, String reason, boolean applied) {
			this(loaders, exception, pattern, reason, applied, Set.of());
		}

		/** A limit of one mod's hooks: those of {@code mixins} on {@code loaders} whose message matches {@code pattern}. */
		static Limit ofMod(Set<String> loaders, Set<String> mixins, Pattern pattern, String reason) {
			return new Limit(loaders, null, pattern, reason, false, mixins);
		}
	}

	/** The keys of a config's mixins in a package: {@code "<config> <package>.<name>"}. */
	private static Set<String> mixins(String config, String pkg, String... names) {
		Set<String> keys = new LinkedHashSet<>();
		for (String name : names) keys.add(config + " " + pkg + "." + name);
		return keys;
	}

	@SafeVarargs
	private static Set<String> union(Set<String>... sets) {
		Set<String> all = new LinkedHashSet<>();
		for (Set<String> set : sets) all.addAll(set);
		return all;
	}

	/** Porting Lib's mixins whose hooks Forge 47 leaves out (HOOK-5), as its Forge 47 corpus runs list them. */
	private static final Set<String> PORTING_LIB = union(
			mixins("porting_lib_base.mixins.json", "io.github.fabricators_of_create.porting_lib.mixin", "client.AbstractClientPlayerMixin",
					"client.AbstractContainerScreenMixin", "client.BlockRenderDispatcherMixin", "client.BoatRendererMixin", "client.HumanoidArmorLayerMixin",
					"client.ItemFrameRendererMixin", "client.LevelRendererMixin", "client.MinecraftMixin", "client.ModelBlockRendererMixin",
					"client.ParticleEngineMixin", "common.AxeItemMixin", "common.BeaconBlockEntityMixin", "common.BlockBehavior$PropertiesMixin",
					"common.BlockGetterMixin", "common.BlockItemMixin", "common.BlockLightEngineMixin", "common.EnchantmentHelperMixin",
					"common.ExperienceOrbMixin", "common.ItemStackMixin", "common.LightEngineMixin", "common.LivingEntityMixin",
					"common.PistonMovingBlockEntityMixin", "common.ServerPlayerGameModeMixin", "common.ShapedRecipeMixin",
					"common.ShapelessRecipe$SerializerMixin", "common.SignalGetterMixin", "common.StructureTemplateMixin", "common.WitherBossMixin"),
			mixins("porting_lib_entity.mixins.json", "io.github.fabricators_of_create.porting_lib.entity.mixin", "client.EffectRenderingInventoryScreenMixin",
					"common.LootItemRandomChanceWithLootingConditionMixin", "common.LootingEnchantFunctionMixin",
					"common.ServerGamePacketListenerImplInteractHandlerMixin", "common.SpreadPlayersCommandMixin"),
			mixins("porting_lib_fluids.mixins.json", "io.github.fabricators_of_create.porting_lib.fluids.mixin", "FlowingFluidMixin", "LiquidBlockMixin"),
			mixins("porting_lib_loot.mixins.json", "io.github.fabricators_of_create.porting_lib.loot.mixin", "LootDataTypeMixin"),
			mixins("porting_lib_models.mixins.json", "io.github.fabricators_of_create.porting_lib.models.mixin.client", "ItemRendererMixin",
					"ScreenEffectRendererMixin"),
			mixins("porting_lib_tool_actions.mixins.json", "io.github.fabricators_of_create.porting_lib.tool.mixin", "LivingEntityMixin", "PlayerMixin"));

	/** NeoForge's hotbar layers, which vanilla's renderHotbarAndDecorations (extractHotbarAndDecorations) drew in one method. */
	private static final String HOTBAR_LAYERS = "NeoForge draws the hotbar and what vanilla draws with it (the contextual bar, the experience level,"
			+ " the selected item's name, the spectator tooltip) as layers of its own, in its own order, so hooks of vanilla's method drawing them"
			+ " can't apply; use the mod's NeoForge build (HUD-8)";
	/** NeoForge's health layers, which vanilla's renderPlayerHealth (extractPlayerHealth) drew in one method. */
	private static final String HEALTH_LAYERS = "NeoForge draws the player's health, armor, food and air as layers of its own, each from its part of"
			+ " vanilla's renderPlayerHealth, so hooks of vanilla's method there can't apply; use the mod's NeoForge build (HUD-8)";

	/** The hooks of vanilla's plant growth and survival checks the platforms make through their soil extensions (SEAM-12). */
	private static final Set<String> PLANTS = union(
			mixins("balm.mixins.json", "net.blay09.mods.balm.mixin", "FabricCropBlockMixin"),
			mixins("balm.fabric.mixins.json", "net.blay09.mods.balm.mixin", "FabricCropBlockMixin"),
			mixins("balm.fabric.mixins.json", "net.blay09.mods.balm.fabric.internal.mixin", "FabricCropBlockMixin"),
			mixins("farmersdelight.mixins.json", "vectorwing.farmersdelight.common.mixin.refabricated", "CropBlockMixin", "StemBlockMixin", "CactusBlockMixin",
					"MushroomBlockMixin"),
			mixins("farmersdelight.mixins.json", "vectorwing.farmersdelight.common.mixin", "CactusBlockMixin"));

	/** The hooks of vanilla's water and lava checks and fluid pushing the platforms make through their fluid types (SEAM-9). */
	private static final Set<String> FLUIDS = union(
			mixins("lithium-fabric.mixins.json", "net.caffeinemc.mods.lithium.fabric.mixin", "entity.collisions.fluid.EntityMixin",
					"experimental.entity.block_caching.fluid_pushing.EntityMixin"),
			mixins("wilderwild.mixins.json", "net.frozenblock.wilderwild.mixin", "block.mesoglea.EntityMixin", "block.mesoglea.EntityFluidInteractionMixin",
					"block.mesoglea.LivingEntityMixin", "warden.EntityMixin"),
			mixins("wilderwild.fabric.mixins.json", "net.frozenblock.wilderwild.mixin", "warden.EntityMixin"),
			mixins("mixin/frozenlib.block.mixins.json", "net.frozenblock.lib.block.mixin.waterlike", "EntityFluidInteractionMixin"),
			mixins("mixin/frozenlib.block.fabric.mixins.json", "net.frozenblock.lib.block.mixin.waterlike", "LivingEntityMixin"),
			mixins("mixin/frozenlib.entity.fabric.mixins.json", "net.frozenblock.lib.entity.mixin.client.suffocation", "HudMixin"),
			mixins("expandability.mixins.json", "be.florens.expandability.mixin.fabric.swimming", "EntityMixin"),
			mixins("expandability-common.mixins.json", "be.florens.expandability.mixin.swimming", "EntityMixin"),
			mixins("artifacts.fabric.mixins.json", "artifacts.fabric.mixin.ability.sinking", "PlayerMixin"),
			mixins("milk.mixins.json", "io.github.tropheusj.milk.mixin", "EntityMixin"));

	private static final List<Limit> LIMITS = List.of(
			// A hook of vanilla's spawn checks, which the platforms make inside their spawn event's hook (Mojang's and SRG's names).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("target=\"Lnet/minecraft/world/entity/Mob;(?:checkSpawnRules|checkSpawnObstruction|m_5545_|m_6914_)\\("),
					"NeoForge and Forge run spawn checks through their own event, so Fabric hooks of those checks can't apply;"
							+ " use the mod's NeoForge or Forge build (SPAWN-1)"),
			// A wrapper of the screen's render call where Forge 65 and 66 draw the screen in ForgeLayerInstance.drawScreen.
			new Limit(Set.of("Forge"), null,
					Pattern.compile("in extractRenderState expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
							+ "extractRenderStateWithTooltipAndSubtitles\\("),
					"Forge draws screens through its own layer code, so wrappers of a screen's render call can't apply;"
							+ " use NeoForge or the mod's Forge build (SCREEN-2)"),
			// owo-lib's and Architectury's wrappers of the screen's mouse click, release or drag in Forge 52's MouseHandler lambdas,
			// which make it inside Forge's event factory, the screen's answer stored by the factory itself (a drag's returns nothing).
			Limit.ofMod(Set.of("Forge"), union(mixins("owo.mixins.json", "io.wispforest.owo.mixin.ui.layers", "MouseMixin"),
							mixins("architectury.mixins.json", "dev.architectury.mixin.fabric.client", "MixinMouseHandler")),
					Pattern.compile("^@(?:WrapOperation|Redirect|WrapWithCondition) in lambda\\$(?:onPress\\$[01]|handleAccumulatedMovement\\$\\d+) expects"
							+ " @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;mouse(?:Clicked|Released|Dragged)\\("),
					"Forge 52 hands a screen its mouse clicks, releases and drags inside its own event factory, which takes the screen's answer"
							+ " itself, so wrappers and redirects of those calls can't apply; use NeoForge or the mod's Forge build (LAMBDA-14)"),
			// owo-lib's hook keeping the key modifiers for its wrappers of those calls on Forge 52, left out with them.
			Limit.ofMod(Set.of("Forge"), mixins("owo.mixins.json", "io.wispforest.owo.mixin.ui.layers", "MouseMixin"),
					Pattern.compile("^The injector of captureMods\\(.* goes with that of passModifiersToBraid\\$mouse(?:Clicked|Released)\\(", Pattern.DOTALL),
					"Forge 52 hands a screen its mouse clicks and releases inside its own event factory, so owo-lib's wrappers passing the key"
							+ " modifiers to its braid layers can't apply, nor the hook keeping the modifiers for them: a braid layer gets no modifiers"
							+ " with a click or a release (LAMBDA-14)"),
			// A hook of vanilla's setKey in the Controls screen's reset of every key binding, which the platforms make with
			// their own setToDefault (resetting the key's modifier too): Mojang's names, and Forge 47's SRG ones.
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("in (?:lambda\\$addFooter\\$0|m_268771_) expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/KeyMapping;(?:setKey|m_90848_)\\("),
					"NeoForge and Forge reset every key binding in the Controls screen with their own setToDefault, which resets its modifier too,"
							+ " so hooks of vanilla's setKey call there can't apply (LAMBDA-14)"),
			// Architectury's modifier of the GuiEventListener vanilla's KeyboardHandler.charTyped lambdas take, which the platforms'
			// take as the screen (Mojang's names, and Forge 47's SRG ones).
			Limit.ofMod(Set.of("NeoForge", "Forge"), mixins("architectury.mixins.json", "dev.architectury.mixin.fabric.client", "MixinKeyboardHandler"),
					Pattern.compile("^@ModifyVariable \\S+ modifies argument \\d+ among those of type net\\.minecraft\\.client\\.gui\\.components\\.events\\.GuiEventListener"
							+ " of (?:lambda\\$charTyped\\$\\d+|m_\\d+_)\\(Lnet/minecraft/client/gui/screens/Screen;"),
					"the platform's KeyboardHandler hands a typed character to the screen as a Screen where vanilla's hands it on as a"
							+ " GuiEventListener, so a hook replacing that listener with another can't apply there (CORPUS-57)"),
			// A wrapper of vanilla's tooltip call in a container screen's renderTooltip (extractTooltip), which Forge 61 to 66
			// make through their overload taking the item, which works out the tooltip's style from it.
			new Limit(Set.of("Forge"), null,
					Pattern.compile("^@(?:WrapOperation|Redirect|WrapWithCondition) in (?:renderTooltip|extractTooltip) expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/(?:GuiGraphics|GuiGraphicsExtractor);"
							+ "setTooltipForNextFrame\\(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;"),
					"Forge hands a container screen's item tooltip to its own overload taking the item, which works out the tooltip's style from it,"
							+ " so wrappers and redirects of vanilla's call there can't apply; use NeoForge or the mod's Forge build (SCREEN-3)"),
			// A hook of vanilla's hotbar method, which NeoForge splits into layers of its own (STRUCT-1's hint names its method
			// left in vanilla's call's place), and Farmer's Delight's hooks keeping the frame's delta tracker it captures there.
			new Limit(Set.of("NeoForge"), null,
					Pattern.compile("targets method (?:render|extract)HotbarAndDecorations\\b.*\\bNeoForge replaced that private method with updateContextualBarRenderer\\(\\)V",
							Pattern.DOTALL),
					HOTBAR_LAYERS),
			Limit.ofMod(Set.of("NeoForge"), mixins("farmersdelight.mixins.json", "vectorwing.farmersdelight.common.mixin.refabricated", "GuiMixin"),
					Pattern.compile("^The injector of farmersdelightrefabricated\\$render(?:Hearts|Nourishment)\\(.* goes with that of farmersdelightrefabricated\\$captureDeltaTracker\\(",
							Pattern.DOTALL),
					HOTBAR_LAYERS),
			// Apoli's, GlitchCore's and Puzzles Lib's hooks in vanilla's renderPlayerHealth (extractPlayerHealth), whose parts
			// NeoForge draws as layers of its own, and GlitchCore's hooks keeping state with one there.
			Limit.ofMod(Set.of("NeoForge"), union(mixins("apoli.mixins.json", "io.github.apace100.apoli.mixin", "InGameHudMixin"),
							mixins("glitchcore.fabric.mixins.json", "glitchcore.fabric.mixin.client", "MixinGui", "MixinHud"),
							mixins("puzzleslib.fabric.mixins.json", "fuzs.puzzleslib.fabric.mixin.client", "GuiFabricMixin")),
					Pattern.compile("^@\\w+ in (?:render|extract)PlayerHealth expects @At\\("), HEALTH_LAYERS),
			Limit.ofMod(Set.of("NeoForge"), mixins("glitchcore.fabric.mixins.json", "glitchcore.fabric.mixin.client", "MixinGui", "MixinHud"),
					Pattern.compile("^The injector of .* goes with that of (?:onRenderPlayerHealth|onExtractFoodLevel)\\(", Pattern.DOTALL), HEALTH_LAYERS),
			// A hook at vanilla's drawing of its HUD stacks in Gui.render, or of the boss overlay among them, which NeoForge 21.1
			// draws as its own layers (21.11: the boss overlay one).
			new Limit(Set.of("NeoForge"), null,
					Pattern.compile("in render expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/(?:LayeredDraw;render|Gui;renderBossOverlay)\\("),
					"NeoForge draws vanilla's HUD as layers of its own, in its own order, so hooks at vanilla's calls drawing it in Gui.render can't"
							+ " apply; use the mod's NeoForge build (HUD-8)"),
			// FancyMenu's condition on vanilla's drawing of the version string in TitleScreen.render, which NeoForge 21.1 draws
			// as its branding lines, each through BrandingControl's callback.
			Limit.ofMod(Set.of("NeoForge"), mixins("fancymenu.fabric.mixins.json", "de.keksuccino.fancymenu.mixin.mixins.fabric.client", "MixinFabricTitleScreen"),
					Pattern.compile("^@WrapWithCondition in render expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/GuiGraphics;drawString\\("
							+ "Lnet/minecraft/client/gui/Font;Ljava/lang/String;III\\)I\"\\)"),
					"NeoForge 21.1 draws the title screen's version and branding lines itself, one at a time through its BrandingControl, where"
							+ " vanilla draws one version string, so FancyMenu's condition on vanilla's drawing of it can't apply: the title screen"
							+ " shows NeoForge's lines whatever FancyMenu's layout decides for vanilla's (SCREEN-3)"),
			// A hook of vanilla's playerWillDestroy call in the server's destroyBlock, which Forge makes from the block state's
			// onDestroyedByPlayer once the tool is damaged, and Fabricated doesn't put back there: pre-flight's hint names the
			// server's class (the client's method has the same name on Forge 52 on, and there the call goes back).
			new Limit(Set.of("Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;(?:playerWillDestroy|m_5707_)\\(.*Forge's"
							+ " ServerPlayerGameMode makes that call through the block's IForgeBlock\\.onDestroyedByPlayer instead", Pattern.DOTALL),
					"Forge calls playerWillDestroy from the block state's onDestroyedByPlayer, after the tool is damaged, so hooks of vanilla's call"
							+ " before it can't apply there; Fabricated puts back only the removal (SEAM-3)"),
			// A hook of vanilla's check of the held item in destroyBlock, which the platforms make inside their block break
			// event's hook, where a listener may overrule it: pre-flight's hint says so (NeoForge's client from 26.1.2 too).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/item/(?:Item;(?:canAttackBlock|m_6777_)|ItemStack;canDestroyBlock)\\("
							+ ".*asks the held item's (?:canAttackBlock|canDestroyBlock) inside its block break event", Pattern.DOTALL),
					"NeoForge and Forge ask the held item whether it can break the block inside their block break event, which a listener may"
							+ " overrule, so hooks of vanilla's check in destroyBlock can't apply there (SEAM-4)"),
			// A hook of vanilla's shears check in a mob's mobInteract, or of the mob's shear call it guards, where the platforms
			// shear the mob from the shears item through their shearable extension: pre-flight's hint says so (Forge 65 and 66
			// ask an action there, which goes back).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/(?:item/ItemStack;(?:is|m_150930_)|entity/[\\w/$]+;(?:shear|m_5851_))\\("
							+ ".*'s mobInteract asks for no shears here: the platform shears it from the shears item", Pattern.DOTALL),
					"NeoForge and Forge shear a mob from the shears item, through their shearable extension, so hooks of vanilla's shears check in"
							+ " its mobInteract can't apply there (SEAM-6)"),
			// A hook of vanilla's map or crossbow check in the client's held item or item frame renderer, where the platforms ask
			// whether the item is of the item's class, which other mods' items are too: pre-flight's hint says so.
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/item/ItemStack;(?:is|m_150930_)\\(.*(?:ItemInHandRenderer|ItemFrameRenderer)"
							+ " asks whether the (?:held|framed) item is a MapItem", Pattern.DOTALL),
					"NeoForge and Forge ask whether the item is a map (or crossbow) item there, which other mods' items are too, so hooks of vanilla's"
							+ " check of the vanilla item can't apply there (SEAM-6)"),
			// A hook of vanilla's friction in Leashable's angularFriction (1.21.11 on), an interface's method, which can't keep the
			// answers the put-back's check needs: pre-flight's hint says so.
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;getFriction\\(\\)F.*Leashable is an interface",
							Pattern.DOTALL),
					"NeoForge and Forge ask the block state's friction extension in Leashable's angularFriction, an interface's method, where Fabricated"
							+ " can't put vanilla's call back, so hooks of vanilla's friction there can't apply (SEAM-8)"),
			// A hook in vanilla's doSweepAttack, which NeoForge 21.11 on keeps and never calls, making the sweep attack's box in
			// attack for an overload of its own: the hook applies and never runs, as pre-flight's note says.
			new Limit(Set.of("NeoForge"), null,
					Pattern.compile("applies in doSweepAttack\\([^)]*\\)\\S*, vanilla's method, which NeoForge's Player never calls"),
					"NeoForge makes the sweep attack's box in attack, for an overload of doSweepAttack of its own, so hooks in vanilla's doSweepAttack"
							+ " apply there but never run (SEAM-8)", true),
			// An invoker of a vanilla method of the game's the platform gave a parameter vanilla's never had, or replaced with a
			// method taking one (Forge 43's and 47's FireBlock.tryCatchFire), where Fabricated gives it no fixed value of it (the
			// platform's method or one it passes it to reads it, which method stands in for vanilla's or runs can't be told,
			// vanilla's is static, or the invoker can't call the bridge): pre-flight's hint says why. Before STRUCT-2's, which
			// a replaced method's line matches too. A line with no such hint (the target an installed mod's class, or vanilla's
			// class unread) is no LEAVE-6 limit: nothing there was judged.
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("^@Invoker target method .*\\n.* Fabricated gives an invoker of it a fixed .* only where", Pattern.DOTALL),
					"NeoForge and Forge give that method, or the one replacing it, a parameter vanilla's never had, and Fabricated gives the invoker no"
							+ " fixed value of it to call their method with (the hint says why), so the mod's invoker of vanilla's method has nothing to"
							+ " call, and the feature calling it fails when it does (LEAVE-6)"),
			// An invoker of a private method of vanilla's the platform replaced with one of its own taking other parameters, which
			// no bridge serves and LEAVE-6's verdict didn't judge: pre-flight's hint names that method.
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("^@Invoker target method .* replaced that private method with .*, and has no method taking vanilla's parameters"
							+ " for the invoker to call", Pattern.DOTALL),
					"NeoForge and Forge replaced that private method with one of their own taking other parameters, which no bridge of Fabricated's"
							+ " serves, so the mod's invoker of vanilla's method has nothing to call, and the feature calling it fails when it does (STRUCT-2)"),
			// ForgeRegistry.add, whose maps key a value by its equality: two objects, by their identity hashes, not one object
			// under two names.
			new Limit(Set.of(), IllegalArgumentException.class.getName(),
					Pattern.compile("\\{(\\p{XDigit}+)} has been registered twice, using the names .*\\{(\\p{XDigit}+)}\\)$", Pattern.DOTALL),
					"Forge's registries take two equal entries for one, so the mod's second one can't be registered;"
							+ " use NeoForge or the mod's Forge build (REG-13)"),
			// Additional Entity Attributes' hooks of vanilla's water sinking constant in goDownInWater (Forge 47's m_21208_),
			// which the platforms replace with the fluid type's sinkInFluid.
			Limit.ofMod(Set.of("NeoForge", "Forge"), union(mixins("additionalentityattributes.mixins.json", "de.dafuqs.additionalentityattributes.mixin.common",
							"LivingEntityMixin"), mixins("additionalentityattributes.mixins.json", "de.dafuqs.additionalentityattributes.mixin.compat.connectormod.absent",
							"ClientLivingEntityMixin")),
					Pattern.compile("^@ModifyExpressionValue in (?:goDownInWater|m_21208_) expects @At\\(\"CONSTANT\", args=\\[doubleValue=-0\\.03999999910593033D]\\)"),
					"NeoForge and Forge move vanilla's water sinking speed into the fluid type's sinkInFluid, which every fluid uses, so a hook of"
							+ " vanilla's water constant would act in other mods' fluids too, unlike on Fabric: Additional Entity Attributes' water-speed"
							+ " attribute doesn't change how fast an entity sinks in water (FLUID-5)"),
			// Reach Entity Attributes' hooks of vanilla's reach constants and container checks, which Forge 47 makes from its own
			// reach attributes: the methods named by their SRG names, and the lambda Forge's container check has.
			Limit.ofMod(Set.of("Forge"), mixins("mixins.reach-entity-attributes.json", "com.jamieswhiteshirt.reachentityattributes.mixin", "ItemMixin",
							"ServerPlayNetworkHandlerMixin", "ScreenHandlerMixin", "InventoryValidationMixin", "VehicleInventoryValidationMixin",
							"client.ClientPlayerInteractionManagerMixin", "client.GameRendererMixin"),
					Pattern.compile("^@(?:ModifyConstant in (?:m_41435_|m_105286_) expects the constant 5\\.0|ModifyConstant in m_109087_ expects the constant 6\\.0"
							+ "|ModifyConstant in m_6371_ expects the constant 64\\.0|ModifyConstant handler getActualReachDistance\\(DLnet/minecraft/world/level/block/Block;"
							+ "Lnet/minecraft/world/entity/player/Player;\\)D does not match the parameters of lambda\\$stillValid\\$0\\("
							+ "|(?:Inject handler checkWithinActualReach|ModifyConstant handler getActualReachDistance) is an injector in an interface mixin, .* finds no"
							+ " (?:INVOKE Lnet/minecraft/core/BlockPos;m_123341_\\(\\)I in m_271806_\\(|CONSTANT 8\\.0 in m_219954_\\())", Pattern.DOTALL),
					"Forge 47 works out reach from its own attributes (ForgeMod.BLOCK_REACH and ENTITY_REACH), and the constants, the call and the"
							+ " container checks Reach Entity Attributes' hooks change are gone from the methods it hooks, so its reach attributes don't"
							+ " change reach, and a Fabric mod giving reach through them gives none (ATTR-2)"),
			// Porting Lib's ports of Forge's hooks, which Forge 47's own code makes where vanilla's code they hook is now Forge's:
			// the hooks of game methods named by their SRG names, and those left out with them.
			Limit.ofMod(Set.of("Forge"), PORTING_LIB,
					Pattern.compile("^(?:@\\w+ .*\\bm_\\d+_\\b|The injector of .* goes with that of )", Pattern.DOTALL),
					"Forge 47's own code makes the hooks Porting Lib ports, where vanilla's code they hook is now Forge's, so they are left out;"
							+ " Porting Lib's block and entity interfaces carry Forge's extension names and descriptors, so Forge's own calls reach a block"
							+ " implementing one (read in the hooks, not checked in game) (HOOK-5)"),
			// Ledger's hook taking the block entity CampfireBlock.useItemOn keeps in a local, a slot the platforms' recompiled
			// method reuses before the hook's point.
			Limit.ofMod(Set.of("NeoForge", "Forge"), mixins("ledger.mixins.json", "com.github.quiltservertools.ledger.mixin.blocks", "CampfireBlockMixin"),
					Pattern.compile("^@Inject handler logCampfireAddItem captures the local variable of type net\\.minecraft\\.world\\.level\\.block\\.entity\\.BlockEntity,"
							+ " which useItemOn in net\\.minecraft\\.world\\.level\\.block\\.CampfireBlock doesn't hold where its @At points are"),
					"the platform's CampfireBlock.useItemOn, compiled from the game's decompiled sources, reuses the block entity's local before the"
							+ " player's stat is awarded, so Ledger's hook taking it there is left out, and an item put on a campfire isn't logged (LOCAL-6)"),
			// OneConfig's accessor of KeyMapping.MAP as vanilla's Map, which NeoForge makes its own KeyMappingLookup.
			Limit.ofMod(Set.of("NeoForge"), mixins("mixins.oneconfigv1.json", "org.polyfrost.oneconfig.internal.mixin.keybind", "KeyMappingRegistryAccessor"),
					Pattern.compile("^@Accessor target field MAP in net\\.minecraft\\.client\\.KeyMapping has a different type than Ljava/util/Map;"),
					"NeoForge makes KeyMapping.MAP its own KeyMappingLookup, so OneConfig's accessor of it as a Map is left out: OneConfig catches"
							+ " the failed call, warns that its keybinds may shadow Minecraft's, and leaves the mirrors of its keybinds it adds to the"
							+ " Controls screen registered, where Minecraft's own bindings of their keys still work (KEYMAP-1)"),
			// Balm's and Farmer's Delight's hooks of vanilla's soil checks in a crop's growth speed and a cactus's, a mushroom's and a
			// stem's fruit's survival, which the platforms make through the soil's canSustainPlant and isFertile (NeoForge's growth
			// speed taking the crop's state): those methods, and the hooks left out with them.
			Limit.ofMod(Set.of("NeoForge", "Forge"), PLANTS,
					Pattern.compile("^(?:The injector of .* goes with that of |@\\w+ .*\\b(?:getGrowthSpeed|m_52272_|randomTick|canSurvive|m_7898_)\\b)", Pattern.DOTALL),
					"NeoForge and Forge ask the soil's canSustainPlant and isFertile where vanilla checks for farmland in a crop's growth speed"
							+ " (NeoForge passing the crop's state, not its block), and canSustainPlant where vanilla checks the soil of a cactus, a"
							+ " mushroom or a stem's fruit, so hooks of vanilla's checks there can't apply: Balm's custom farmland doesn't speed crops up,"
							+ " and Farmer's Delight's rich soil doesn't, nor take cactus, mushrooms or stems' fruit, through them (SEAM-12)"),
			// FrozenLib's hooks of vanilla's container lookups in the hopper's ejectItems and suckInItems, which NeoForge 21.11 on
			// makes through a lookup of its own answering a container or an item handler.
			Limit.ofMod(Set.of("NeoForge"), mixins("mixin/frozenlib.block.mixins.json", "net.frozenblock.lib.block.mixin.storage.hopper", "HopperBlockEntityMixin"),
					Pattern.compile("^@\\w+ in (?:ejectItems|suckInItems) expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/entity/HopperBlockEntity;"
							+ "(?:getAttachedContainer|getSourceContainer)\\("),
					"NeoForge looks a hopper's containers up through a lookup of its own, which answers a container or an item handler, where"
							+ " vanilla's getAttachedContainer and getSourceContainer were, so FrozenLib's hooks of them can't apply: hoppers move items"
							+ " to and from the containers FrozenLib's hopper API keeps them from (SEAM-14)"),
			// Lithium's hooks in vanilla's private ejectItems, which Forge 47 replaced with one of its own, to which only an @Inject moves.
			Limit.ofMod(Set.of("Forge"), mixins("lithium.mixins.json", "me.jellysquid.mods.lithium.mixin.block.hopper", "HopperBlockEntityMixin"),
					Pattern.compile("^@(?:Redirect|ModifyVariable) targets method m_155562_\\("),
					"Forge 47 replaced vanilla's private ejectItems with one of its own, to which only an @Inject moves, so Lithium's other hooks"
							+ " there are left out: hoppers eject items as Forge has it, without Lithium's optimisation of it (SEAM-14)"),
			// Hooks of vanilla's water and lava checks and fluid pushing, which the platforms make through their fluid types, every
			// fluid at once: those methods, and the hooks left out with them.
			Limit.ofMod(Set.of("NeoForge", "Forge"), FLUIDS,
					Pattern.compile("^(?:The injector of .* goes with that of |@\\w+ .*\\b(?:updateFluidHeightAndDoFluidPushing|updateFluidOnEyes|updateSwimming"
							+ "|updateFluidInteraction|update|baseTick|getDestroySpeed|extractAirBubbles|setInWater|m_204031_|m_5844_|m_6075_)\\b)", Pattern.DOTALL),
					"NeoForge and Forge make vanilla's water and lava checks and its fluid pushing through their fluid types, every fluid at once,"
							+ " so hooks of vanilla's fluid calls there can't apply: entities are pushed without Lithium's fluid caches, Wilder Wild's"
							+ " mesoglea and FrozenLib's water-like fluids aren't seen by entities' fluid checks, and Expandability's swimming events,"
							+ " Artifacts' underwater mining speed and Milk Lib's effect clearing don't run there (SEAM-9)"));

	private KnownLimits() {
	}

	/**
	 * The known limit a pre-flight finding is on {@code loader}, as the report says it, or null; only a finding that leaves
	 * its hook out or disables its mod is one, never a warning (a {@code @Group}'s member whose group applies), and a note
	 * of a hook that applies and never runs (SEAM-8: NeoForge's sweep attack).
	 */
	public static String ofFinding(String loader, PreflightFinding finding) {
		boolean note = finding.severity() == Severity.INFO;
		if (!note && finding.severity() != Severity.LEFT_OUT && finding.severity() != Severity.FAIL) return null;
		for (Limit limit : LIMITS) {
			if (limit.exception() == null && limit.applied() == note && limit.loaders().contains(loader)
					&& (limit.mixins().isEmpty() || limit.mixins().contains(finding.config() + " " + finding.mixin()))
					&& matches(limit, finding.message() + "\n" + finding.suggestion())) {
				return "Known limit: " + limit.reason();
			}
		}
		return null;
	}

	/** The known limit an entrypoint's failure is, from the root of its causes, or null. */
	public static String ofFailure(Throwable root) {
		String message = root.getMessage();
		for (Limit limit : LIMITS) {
			if (root.getClass().getName().equals(limit.exception()) && message != null && matches(limit, message)) return "Known limit: " + limit.reason();
		}
		return null;
	}

	private static boolean matches(Limit limit, String message) {
		Matcher m = limit.pattern().matcher(message);
		return m.find() && (m.groupCount() < 2 || !m.group(1).equals(m.group(2)));
	}
}
