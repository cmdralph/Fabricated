package io.github.cmdralph.fabricated.core.bytecode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The second shape of {@link VanillaSeams}' table: vanilla's calls in a game method that a platform makes through its
 * extensions or hooks instead, put back as their own steps there, where the platform's code then does what vanilla's
 * does. Where the platform calls it, the rewrite asks whether the objects concerned keep the platform's default
 * ({@code fabricated$keepsDefault}: neither the state's class nor the block's overrides either interface's method,
 * {@link KeepsDefault}; on Forge, for the harvest checks, also that the player's class keeps
 * {@code Player.hasCorrectToolForDrops}), and then makes vanilla's calls, which hooks find; otherwise the platform's
 * call, as before. Each operand of the platform's call is evaluated as before, once, in the branch the call is in;
 * vanilla's branch loads again the plain ones it needs. What vanilla doesn't call there is called through private
 * helpers, out of hooks' sight, and a value kept goes in a local of type {@code Object}, which a hook capturing a local
 * of its type doesn't count. The method gets the frames its branches need.
 *
 * <p>Block breaking (SEAM-3): vanilla's {@code destroyBlock} (the server's and the client's) removes the block with
 * {@code level.removeBlock(pos, false)} (the server's, then {@code block.destroy}) or
 * {@code level.setBlock(pos, fluid.createLegacyBlock(), 11)} (the client's); NeoForge 21.1 to 26.3 and Forge 47 to 66 call
 * the state's {@code onDestroyedByPlayer} there (the server through a private {@code removeBlock} of theirs), whose
 * default makes those calls (NeoForge's) or {@code playerWillDestroy} and the {@code setBlock} (Forge's). What vanilla
 * doesn't call there (the block's {@code getBlock}, Forge's server reading the state and its {@code playerWillDestroy},
 * which vanilla makes before the tool is damaged) goes through the helpers.
 *
 * <p>Harvest checks (SEAM-4): vanilla's server {@code destroyBlock} asks {@code player.hasCorrectToolForDrops(state)},
 * and {@code BlockBehaviour.getDestroyProgress} asks it of its player; both platforms ask the state's
 * {@code canHarvestBlock(level, pos, player)} in the first and a hook of theirs in the second. NeoForge's defaults make
 * vanilla's call, then post its {@code PlayerEvent.HarvestCheck} with the answer ({@code EventHooks.doPlayerHarvestCheck}):
 * vanilla's call goes back with the event posted after it by a helper, as that method posts it, in the first for the
 * blocks keeping {@code canHarvestBlock}'s default, in the second always. Forge's default ({@code
 * ForgeHooks.isCorrectToolForDrops}) asks {@code player.hasCorrectToolForDrops(state)}, which Forge makes fire its event,
 * where the state needs the correct tool, and fires the event with {@code true} where it doesn't, which Forge's
 * {@code Player.hasCorrectToolForDrops} also does there: vanilla's call alone goes back, for the players whose class keeps
 * that method (and in {@code destroyBlock} the blocks keeping the default).
 *
 * <p>Item checks (SEAM-6): where vanilla asks {@code stack.is(Items.SHEARS)} (the beehive's harvest, the pumpkin's
 * carving, the tripwire's disarming; on Forge 65 and 66 a sheep's, a snow golem's and a mooshroom's shearing too),
 * {@code stack.is(Items.FISHING_ROD)} (whether a fishing hook's player still holds a rod, and on the client which hand
 * the line comes from) or {@code stack.is(Items.SHIELD)} (a shield's damage, a piglin's shield; up to 1.21.1), NeoForge
 * asks the stack's {@code canPerformAction(ability)} and Forge its {@code canPerformAction(toolAction)}. Their defaults
 * ask the item's, which answers no for these actions (a sword's sweep and dig and a pickaxe's dig, or a campfire's
 * dousing on NeoForge 26.3, are the only ones it says yes to), and vanilla's shears, rod and shield override it, answering
 * yes; so for an item keeping the default vanilla's check answers no too: it goes back for the stacks whose class and item
 * keep the default ({@link KeepsDefault}), and an item overriding it is asked as the platform has it. The stack the
 * platform's code pushes is kept and asked first, so its code runs once; vanilla's check is {@code is(Item)}, or from
 * Minecraft 26.1 on, whose classes are Java 25's, {@code TypedInstance.is(T)} as {@code is(Object)}. The client's elytra
 * layer asks {@code this.shouldRender(stack, entity)} where vanilla asks {@code stack.is(Items.ELYTRA)} (NeoForge 21.1,
 * Forge 47 and 52), whose default makes that check: it goes back for the layers whose class keeps that method.
 *
 * <p>Extensions' calls of their objects (SEAM-8): where vanilla asks a block state or a stack itself, both platforms ask
 * an extension of it taking more (the level, the position, the entity), whose default leads back to vanilla's code: the
 * block's friction ({@code state.getBlock().getFriction()}, the state's {@code getFriction(level, pos, entity)}) in the
 * movement of living entities, items, experience orbs, boats and flying mobs; the state's sound type ({@code
 * getSoundType()}, its {@code getSoundType(level, pos, entity)}) in a block's placing, an entity's step and fall sounds, a
 * horse's steps, a sniffer's digging and, on the client, a block's hit and break sounds; the piglin's check of gold or
 * safe armour (the stack's {@code makesPiglinsNeutral(entity)}); the sweep attack's box (the stack's {@code
 * getSweepHitBox(player, target)}); and on NeoForge an item's enchantment level ({@code getEnchantmentLevel(enchantment)},
 * whose default posts NeoForge's level event with the item's level). The object the platform's code pushes is kept and
 * asked whether its classes keep the defaults ({@link KeepsDefault}); then vanilla's code is made of it ({@link
 * OnReceiver}), or the call's other operands and the platform's call as before. Forge's sound type defaults ask the block
 * where vanilla asks the state, so there the state's class must keep vanilla's {@code getSoundType} too; 1.21.1's piglin
 * defaults compare the material with gold's by identity where vanilla asks {@code Holder.is}, so there the check is put
 * back where the two agree (every material but gold's under another holder).
 *
 * <p>The shovel's tool modifications (SEAM-13): where vanilla's {@code ShovelItem.useOn} looks the clicked block up in
 * {@code FLATTENABLES} (1.20.1 to 26.2), both platforms ask the state's {@code getToolModifiedState(context, action,
 * false)} of their shovel flatten action, and where vanilla checks for a lit campfire and douses it, NeoForge asks it of its
 * douse action, storing the answer. The defaults post the platform's tool modification event, answering its state where a
 * listener changed it (none where one cancelled it), then answer none where the held item can't do the action, and
 * otherwise vanilla's lookup, or vanilla's dousing. So for the blocks keeping both defaults the event and the action are
 * asked by a helper as they ask them, and where they leave the answer to vanilla's code, vanilla's code is made of the
 * state ({@link OnReceiver#FLATTEN}, {@link OnReceiver#DOUSE}): the dousing stores the doused state only where it douses,
 * as vanilla's does, so a hook having set the answer before keeps it. Forge asks the level's {@code isEmptyBlock} where
 * vanilla asks whether the block above the path is air: {@code LevelReader}'s default, which every level of the game keeps,
 * goes back for the levels whose class keeps it ({@link Code#LEVEL}).
 */
final class VanillaCalls {
	private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String BLOCK = "net/minecraft/world/level/block/Block";
	private static final String LEVEL = "net/minecraft/world/level/Level";
	private static final String SERVER_LEVEL = "net/minecraft/server/level/ServerLevel";
	private static final String POS = "Lnet/minecraft/core/BlockPos;";
	private static final String PLAYER_CLASS = "net/minecraft/world/entity/player/Player";
	private static final String SERVER_PLAYER = "net/minecraft/server/level/ServerPlayer";
	private static final String PLAYER = "L" + PLAYER_CLASS + ";";
	private static final String GETTER = "Lnet/minecraft/world/level/BlockGetter;";
	private static final String FLUID = "Lnet/minecraft/world/level/material/FluidState;";
	private static final String ITEM = "Lnet/minecraft/world/item/ItemStack;";
	private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";
	private static final String CLIENT_GAME_MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
	private static final String BEHAVIOUR = "net/minecraft/world/level/block/state/BlockBehaviour";
	private static final String DESTROY_BLOCK = "(" + POS + ")Z";
	private static final String DESTROY_PROGRESS = "(L" + STATE + ";" + PLAYER + GETTER + POS + ")F";
	private static final String NEOFORGE = "net/neoforged/";
	private static final String FORGE = "net/minecraftforge/";
	private static final String STACK_CLASS = "net/minecraft/world/item/ItemStack";
	private static final String ITEM_CLASS = "net/minecraft/world/item/Item";
	private static final String ITEMS = "net/minecraft/world/item/Items";
	private static final String ITEM_INSTANCE = "net/minecraft/world/item/ItemInstance";
	private static final String TYPED_INSTANCE = "net/minecraft/core/TypedInstance";
	private static final String ABILITY = "net/neoforged/neoforge/common/ItemAbility";
	private static final String ABILITIES = "net/neoforged/neoforge/common/ItemAbilities";
	private static final String TOOL_ACTION = "net/minecraftforge/common/ToolAction";
	private static final String TOOL_ACTIONS = "net/minecraftforge/common/ToolActions";
	/** The class file version of Java 25, which the game's classes are compiled for from Minecraft 26.1 on. */
	private static final int JAVA_25 = 69;

	/**
	 * A member of the game the rewrite names: by Mojang's names, and SRG's where the game runs with them (Forge 47, which
	 * is 1.20.1: {@code srgDesc} where 1.20.1's descriptor differs, else null).
	 */
	record Member(String owner, String name, String srgName, String desc, String srgDesc) {
		Member(String owner, String name, String srgName, String desc) {
			this(owner, name, srgName, desc, null);
		}

		String name(boolean srg) {
			return srg && srgName != null ? srgName : name;
		}

		String desc(boolean srg) {
			return srg && srgDesc != null ? srgDesc : desc;
		}

		/** As {@link VanillaSeams.Rewrite} and the marker name it: {@code owner;name(desc)}, or {@code owner;name:desc} for a field. */
		String key(boolean srg) {
			String desc = desc(srg);
			return owner + ";" + name(srg) + (desc.startsWith("(") ? "" : ":") + desc;
		}
	}

	/**
	 * What an extension's object is asked of with its holder, as the platform's defaults pass it on: the block state and
	 * its block, the stack and its item; the getter, the helper calling it and the noun a note names the holder by.
	 */
	record Holder(String type, Member getter, String helper, String noun) {
		String holderType() {
			return Type.getReturnType(getter.desc()).getInternalName();
		}

		String helperDesc() {
			return "(L" + type + ";)L" + holderType() + ";";
		}
	}

	/**
	 * The platform's extension a put-back stands in for: the object's method and its holder's, each an interface's
	 * default (or, where the platform's class declares it, as Forge 52 on declares the block's sound type, that class's),
	 * the holder's taking the object first as {@code first}; the helper asking whether an object's classes keep them, and
	 * the field it keeps its answers in; a vanilla method the object's class must keep too, where the platform's default
	 * reaches past it ({@code stateKeeps}: Forge's sound type asks the block, vanilla's the state), else null; and Forge's
	 * interfaces of the same methods, where a class's code can't tell its platform (SEAM-8's: NeoForge's FlyingMob names
	 * nothing of NeoForge's), which the class then finds as it runs, NeoForge's first ({@code altState} and
	 * {@code altBlock}, else null).
	 */
	record Extension(String stateInterface, String blockInterface, String name, String desc, String entry, String field, Holder holder, String first,
			Member stateKeeps, String altState, String altBlock) {
		Extension(String stateInterface, String blockInterface, String name, String desc, String entry, String field) {
			this(stateInterface, blockInterface, name, desc, entry, field, BLOCK_OF_STATE, STATE);
		}

		Extension(String stateInterface, String blockInterface, String name, String desc, String entry, String field, Holder holder, String first) {
			this(stateInterface, blockInterface, name, desc, entry, field, holder, first, null, null, null);
		}

		Extension(String stateInterface, String blockInterface, String name, String desc, String entry, String field, Holder holder, String first,
				Member stateKeeps) {
			this(stateInterface, blockInterface, name, desc, entry, field, holder, first, stateKeeps, null, null);
		}

		/** This extension (NeoForge's), or where the class's platform can't be told, Forge's: {@code forge}'s interfaces. */
		Extension or(Extension forge) {
			return new Extension(stateInterface, blockInterface, name, desc, entry, field, holder, first, stateKeeps, forge.stateInterface(), forge.blockInterface());
		}

		/** Forge's form alone, named outright where the class's platform is known (Forge 47's, by SRG's names); this where it has no other. */
		Extension forge() {
			return altState == null ? this : new Extension(altState, altBlock, name, desc, entry, field, holder, first, stateKeeps, null, null);
		}

		String blockDesc() {
			return "(L" + first + ";" + desc.substring(1);
		}

		String entryDesc() {
			return "(L" + holder.type() + ";)Z";
		}
	}

	/**
	 * What an item check compares (SEAM-6): vanilla's item, a field of {@code Items}; and the platform's action it asks of
	 * the stack in its place, by its class and field (none for the elytra layer, which the platform asks instead).
	 */
	record ItemCheck(Member item, String actions, String action) {
		/** The item as a note names it: {@code fishing rod}. */
		String noun() {
			return item.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		}
	}

	/** Which of vanilla's code a put-back makes in the platform's call's place, and why it can. */
	enum Code {
		/** NeoForge's server: its {@code removeBlock(pos, state, ...)}'s place gets {@code level.removeBlock(pos, false)} and, where it removed the block, {@code block.destroy(level, pos, state)}. */
		SERVER,
		/** Forge's server: as {@link #SERVER}, at its {@code removeBlock(pos, canHarvest)}, the state read at the position first and the default's {@code playerWillDestroy} made out of hooks' sight. */
		FORGE_SERVER,
		/** NeoForge's client: the extension call's place gets {@code level.setBlock(pos, fluid.createLegacyBlock(), 11)}. */
		CLIENT,
		/** Forge's client: as {@link #CLIENT}, after {@code block.playerWillDestroy(level, pos, state, player)}, which Forge's default makes there. */
		FORGE_CLIENT,
		/** NeoForge's server: the state's {@code canHarvestBlock(level, pos, player)}'s place gets {@code player.hasCorrectToolForDrops(state)}, then NeoForge's harvest check event with its answer. */
		HARVEST,
		/** Forge's server: the same place gets {@code player.hasCorrectToolForDrops(state)}, which fires Forge's event itself. */
		FORGE_HARVEST,
		/** NeoForge's {@code getDestroyProgress}: {@code EventHooks.doPlayerHarvestCheck(player, state, level, pos)}'s place gets its own code, vanilla's call then the event, always. */
		PROGRESS,
		/** Forge's {@code getDestroyProgress}: {@code ForgeHooks.isCorrectToolForDrops(state, player)}'s place gets {@code player.hasCorrectToolForDrops(state)}. */
		FORGE_PROGRESS,
		/** NeoForge's item checks: the stack's {@code canPerformAction(ability)}'s place gets {@code stack.is(item)}, for the stacks keeping the default. */
		ACTION,
		/** Forge's: the same, of its {@code ToolAction}. */
		FORGE_ACTION,
		/** The elytra layer (NeoForge 21.1, Forge 47 and 52): its {@code shouldRender(stack, entity)}'s place gets {@code stack.is(Items.ELYTRA)}, for the layers keeping that method. */
		LAYER,
		/**
		 * Either platform's extension call of a block state or a stack (SEAM-8): the call's place gets vanilla's code on the same
		 * object ({@link OnReceiver}), for the objects keeping the default. The call is the same on both, and the class needn't
		 * name either platform: it finds the platform's extensions as it runs ({@link Extension#altState}).
		 */
		RECEIVER,
		/**
		 * Forge's shovel (SEAM-13): the level's {@code isEmptyBlock(pos)}'s place gets vanilla's {@code
		 * level.getBlockState(pos).isAir()}, its default's code, for the levels whose class keeps that method.
		 */
		LEVEL,
		/**
		 * NeoForge 26.x's check of an entity's fluid types at its eyes (SEAM-9): {@code entity.getFluidInteraction()
		 * .isEyeInFluidMatching(entity, predicate)}'s place gets vanilla's {@code entity.isEyeInFluid(FluidTags.WATER)} where
		 * the entity's eyes are in vanilla's water, lava or no fluid and the two answer alike ({@link #VANILLA_EYES}).
		 */
		EYES;

		boolean forge() {
			return this == FORGE_SERVER || this == FORGE_CLIENT || this == FORGE_HARVEST || this == FORGE_PROGRESS || this == FORGE_ACTION || this == LEVEL;
		}

		/**
		 * The finding a put-back of this kind answers: block breaking, SEAM-3; harvest checks, SEAM-4; item checks, SEAM-6; an
		 * extension's call of its object, SEAM-8, or the shovel's (its row's, {@link PutBack#finding}); Forge's empty block check, SEAM-13.
		 */
		String finding() {
			return this == EYES ? "SEAM-9" : this == LEVEL ? "SEAM-13" : receiver() ? "SEAM-8" : item() ? "SEAM-6" : ordinal() < HARVEST.ordinal() ? "SEAM-3" : "SEAM-4";
		}

		boolean harvest() {
			return this == HARVEST || this == FORGE_HARVEST || this == PROGRESS || this == FORGE_PROGRESS;
		}

		/** Whether it puts back vanilla's code on the object the platform's extension call is made of (SEAM-8). */
		boolean receiver() {
			return this == RECEIVER;
		}

		/** Whether it puts back an item check (SEAM-6). */
		boolean item() {
			return this == ACTION || this == FORGE_ACTION || this == LAYER;
		}

		/** Whether the platform's call is the stack's {@code canPerformAction}. */
		boolean action() {
			return this == ACTION || this == FORGE_ACTION;
		}

		/** Whether the platform's call is a static hook's ({@code getDestroyProgress}'s). */
		boolean hookCall() {
			return this == PROGRESS || this == FORGE_PROGRESS;
		}

		/** The operand that is the player whose class must keep {@code Player.hasCorrectToolForDrops} (Forge's harvest checks), else -1. */
		int keepingPlayer() {
			return this == FORGE_HARVEST ? 3 : this == FORGE_PROGRESS ? 1 : -1;
		}
	}

	/**
	 * Vanilla's code on the object an extension's call is made of, which goes in that call's place (SEAM-8), the object on
	 * the stack: each pushes what the platform's call returned.
	 */
	enum OnReceiver {
		/** The state's {@code getBlock().getFriction()}, as vanilla's movement reads it. */
		FRICTION("blocks", "block", "the block state's getFriction(level, pos, entity)"),
		/** The state's {@code getSoundType()}, as vanilla's step, fall, placing and hit sounds read it. */
		SOUND("blocks", "block", "the block state's getSoundType(level, pos, entity)"),
		/** 1.20.1's gold check of the stack's item: an {@code ArmorItem} whose {@code getMaterial() == ArmorMaterials.GOLD}. */
		GOLD("items", "item", "the stack's makesPiglinsNeutral(entity)"),
		/** 1.21.1's: {@code getMaterial().is(ArmorMaterials.GOLD)}, the holders compared as {@code Holder.is} compares them. */
		GOLD_HOLDER("items", "item", "the stack's makesPiglinsNeutral(entity)"),
		/** 1.21.11 on: {@code stack.is(ItemTags.PIGLIN_SAFE_ARMOR)}. */
		SAFE_ARMOR("items", "item", "the stack's makesPiglinsNeutral(entity)"),
		/** The sweep attack's box, {@code target.getBoundingBox().inflate(1.0, 0.25, 1.0)}, the stack popped. */
		SWEEP("items", "item", "the stack's getSweepHitBox(player, target)"),
		/** {@code stack.getOrDefault(ENCHANTMENTS, EMPTY).getLevel(enchantment)}, NeoForge's level event posted after it as its default posts it. */
		ENCHANTMENT("items", "item", "the stack's getEnchantmentLevel(enchantment)"),
		/**
		 * The shovel's path: {@code FLATTENABLES.get(state.getBlock())}, after the platform's tool modification event and the
		 * held item's action, as the default asks them (SEAM-13).
		 */
		FLATTEN("blocks", "block", "the block state's getToolModifiedState(context, action, simulate)", "SHOVEL_FLATTEN"),
		/**
		 * The shovel's dousing of a campfire (NeoForge): {@code state.getBlock() instanceof CampfireBlock && state.getValue(LIT)}, then
		 * {@code CampfireBlock.dowse} and {@code state.setValue(LIT, false)}, after the event and the action as for {@link #FLATTEN}.
		 */
		DOUSE("blocks", "block", "the block state's getToolModifiedState(context, action, simulate)", "SHOVEL_DOUSE"),
		/**
		 * A crop's growth speed, NeoForge 21.1 and 21.11 (SEAM-12): vanilla's farmland check of the soil, {@code
		 * soil.is(Blocks.FARMLAND)}, its answer as the {@code TriState} NeoForge's code reads ({@code TRUE} or {@code FALSE},
		 * which it reads as is: 21.1's NeoForge's, 21.11's Minecraft's), for the soils that are vanilla's farmland or no farm
		 * block ({@link VanillaCalls#VANILLA_SOIL}).
		 */
		FARMLAND("soils", "soil", "the soil state's canSustainPlant(level, pos, facing, plant)"),
		/**
		 * Forge 43 to 61's (SEAM-12): vanilla's {@code soil.is(Blocks.FARMLAND)}, for the crops keeping their checks and, from
		 * Forge 52, the soils that are vanilla's farmland or no farm block ({@link VanillaCalls#VANILLA_SOIL}).
		 */
		FORGE_FARMLAND("soils", "soil", "the soil state's canSustainPlant(level, pos, facing, plantable)"),
		/**
		 * Forge 64 to 66's (SEAM-12): vanilla's {@code soil.is(BlockTags.GROWS_CROPS)}, for the crops keeping their checks and
		 * the soils that are no farmland block or are tagged so ({@link VanillaCalls#VANILLA_SOIL}).
		 */
		GROWS_CROPS("soils", "soil", "the soil state's canSustainPlant(level, pos, facing, plantable)"),
		/**
		 * Vanilla's moisture check of the soil in a crop's growth speed up to 1.21.11 (SEAM-12), {@code
		 * soil.getValue(FarmBlock.MOISTURE) > 0}, for vanilla's farmland ({@link VanillaCalls#VANILLA_FARMLAND}).
		 */
		MOISTURE("soils", "soil", "the soil state's isFertile(level, pos)"),
		/** 26.1 on: {@code soil.getValueOrElse(FarmlandBlock.MOISTURE, 0) > 0}, for vanilla's farmland (SEAM-12). */
		MOISTURE_OR_ELSE("soils", "soil", "the soil state's isFertile(level, pos)");

		final String nouns, noun, call;
		/** The tool action the platform's call takes, by its field's name (the shovel's, SEAM-13); null for the others. */
		final String action;

		OnReceiver(String nouns, String noun, String call) {
			this(nouns, noun, call, null);
		}

		OnReceiver(String nouns, String noun, String call, String action) {
			this.nouns = nouns;
			this.noun = noun;
			this.call = call;
			this.action = action;
		}

		/**
		 * The operands of the platform's call vanilla's code loads again: the sweep's target, the enchantment; the tool
		 * modification's context, action and simulate flag, which the event takes, and the dousing's state too.
		 */
		int[] reloads() {
			return switch (this) {
				case SWEEP -> new int[] {2};
				case ENCHANTMENT -> new int[] {1};
				case FLATTEN -> new int[] {1, 2, 3};
				case DOUSE -> new int[] {0, 1, 2, 3};
				default -> new int[0];
			};
		}

		/** The finding its put-back answers: the shovel's, SEAM-13; a crop's growth speed, SEAM-12; the others', SEAM-8. */
		String finding() {
			return action != null ? "SEAM-13" : soil() ? "SEAM-12" : "SEAM-8";
		}

		/** Whether it is a check of a crop's soil (SEAM-12), which the soil's state, and on Forge the crop, must also pass first. */
		boolean soil() {
			return this == FARMLAND || this == FORGE_FARMLAND || this == GROWS_CROPS || this == MOISTURE || this == MOISTURE_OR_ELSE;
		}

		/** Whether it is Forge's farmland check, whose condition takes the crop, the platform's call's last operand, too. */
		boolean takesCrop() {
			return this == FORGE_FARMLAND || this == GROWS_CROPS;
		}
	}

	/**
	 * A row: the game method (any of the name holding the platform's call where {@code desc} is null), the platform's call
	 * there whose place gets vanilla's calls, the extension whose default those calls stand for (null where no block's or
	 * item's class is asked), which calls, for an item check what it compares, and for an extension's call of its object
	 * (SEAM-8) vanilla's code on that object.
	 */
	record PutBack(String owner, String method, String srgMethod, String desc, Member platform, Extension extension, Code code, ItemCheck check,
			OnReceiver form) {
		PutBack(String owner, String method, String srgMethod, String desc, Member platform, Extension extension, Code code) {
			this(owner, method, srgMethod, desc, platform, extension, code, null, null);
		}

		PutBack(String owner, String method, String srgMethod, String desc, Member platform, Extension extension, Code code, ItemCheck check) {
			this(owner, method, srgMethod, desc, platform, extension, code, check, null);
		}

		/** The row with another form of its extension and vanilla's code, as the class's platform version has them. */
		PutBack with(Extension extension, OnReceiver form) {
			return new PutBack(owner, method, srgMethod, desc, platform, extension, code, check, form);
		}

		String method(boolean srg) {
			return srg ? srgMethod : method;
		}

		/**
		 * The package of the platform's own classes, which only its game's classes reference (none for the elytra layer's and
		 * SEAM-8's, either platform's, nor SEAM-9's, whose call takes NeoForge's predicate: only NeoForge's classes make it).
		 */
		String platformPackage() {
			return code == Code.LAYER || code.receiver() || code == Code.EYES ? null : code.forge() ? FORGE : NEOFORGE;
		}

		String loader() {
			return code.forge() ? "Forge" : "NeoForge";
		}

		/** The finding its put-back answers: its form's, for an extension's call of its object, else its kind's. */
		String finding() {
			return form != null ? form.finding() : code.finding();
		}
	}

	private static final String NEOFORGE_EXT = "net/neoforged/neoforge/common/extensions/";
	private static final String FORGE_EXT = "net/minecraftforge/common/extensions/";
	private static final Member GET_BLOCK = new Member(STATE, "getBlock", "m_60734_", "()L" + BLOCK + ";");
	private static final Member GET_ITEM = new Member(STACK_CLASS, "getItem", "m_41720_", "()L" + ITEM_CLASS + ";");
	private static final Holder BLOCK_OF_STATE = new Holder(STATE, GET_BLOCK, "fabricated$block", "block");
	private static final Holder ITEM_OF_STACK = new Holder(STACK_CLASS, GET_ITEM, "fabricated$item", "item");
	private static final String DESTROYED = "(L" + LEVEL + ";" + POS + PLAYER;
	private static final String KEEPS_DEFAULT = "fabricated$keepsDefault";
	private static final String KNOWN = "fabricated$overriding";
	private static final Extension NEOFORGE_21_1 = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"onDestroyedByPlayer", DESTROYED + "Z" + FLUID + ")Z", KEEPS_DEFAULT, KNOWN);
	private static final Extension NEOFORGE_BREAK = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"onDestroyedByPlayer", DESTROYED + ITEM + "Z" + FLUID + ")Z", KEEPS_DEFAULT, KNOWN);
	private static final Extension FORGE_BREAK = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "onDestroyedByPlayer",
			DESTROYED + "Z" + FLUID + ")Z", KEEPS_DEFAULT, KNOWN);
	private static final String HARVEST_DESC = "(" + GETTER + POS + PLAYER + ")Z";
	private static final Extension NEOFORGE_HARVEST = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension",
			"canHarvestBlock", HARVEST_DESC, "fabricated$keepsHarvestDefault", "fabricated$overridingHarvest");
	private static final Extension FORGE_HARVEST = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "canHarvestBlock", HARVEST_DESC,
			"fabricated$keepsHarvestDefault", "fabricated$overridingHarvest");
	/** The state's {@code canHarvestBlock}, which both platforms' {@code destroyBlock} asks. */
	private static final Member CAN_HARVEST = new Member(STATE, "canHarvestBlock", null, HARVEST_DESC);
	private static final String EVENT_HOOKS = "net/neoforged/neoforge/event/EventHooks";
	private static final String FORGE_HOOKS = "net/minecraftforge/common/ForgeHooks";
	private static final Member HARVEST_HOOK = new Member(EVENT_HOOKS, "doPlayerHarvestCheck", null, "(" + PLAYER + "L" + STATE + ";" + GETTER + POS + ")Z");
	private static final Member FORGE_HARVEST_HOOK = new Member(FORGE_HOOKS, "isCorrectToolForDrops", null, "(L" + STATE + ";" + PLAYER + ")Z");
	// The item checks: the stack's extension and the item's (on NeoForge 26.x the stack's is ItemInstance's, and the item's
	// takes an ItemInstance), and vanilla's items, by Mojang's names and SRG's.
	private static final String KEEPS_ACTION = "fabricated$keepsActionDefault";
	private static final String KNOWN_ACTION = "fabricated$overridingAction";
	private static final Extension NEOFORGE_ACTION = new Extension(NEOFORGE_EXT + "IItemStackExtension", NEOFORGE_EXT + "IItemExtension", "canPerformAction",
			"(L" + ABILITY + ";)Z", KEEPS_ACTION, KNOWN_ACTION, ITEM_OF_STACK, STACK_CLASS);
	private static final Extension NEOFORGE_ACTION_26 = new Extension(NEOFORGE_EXT + "ItemInstanceExtension", NEOFORGE_EXT + "IItemExtension", "canPerformAction",
			"(L" + ABILITY + ";)Z", KEEPS_ACTION, KNOWN_ACTION, ITEM_OF_STACK, ITEM_INSTANCE);
	private static final Extension FORGE_ACTION_EXT = new Extension(FORGE_EXT + "IForgeItemStack", FORGE_EXT + "IForgeItem", "canPerformAction",
			"(L" + TOOL_ACTION + ";)Z", KEEPS_ACTION, KNOWN_ACTION, ITEM_OF_STACK, STACK_CLASS);
	private static final Member CAN_PERFORM = new Member(STACK_CLASS, NEOFORGE_ACTION.name(), null, NEOFORGE_ACTION.desc());
	private static final Member FORGE_CAN_PERFORM = new Member(STACK_CLASS, FORGE_ACTION_EXT.name(), null, FORGE_ACTION_EXT.desc());
	private static final Member SHEARS = new Member(ITEMS, "SHEARS", "f_42574_", "L" + ITEM_CLASS + ";");
	private static final Member FISHING_ROD = new Member(ITEMS, "FISHING_ROD", "f_42523_", "L" + ITEM_CLASS + ";");
	private static final Member SHIELD = new Member(ITEMS, "SHIELD", "f_42740_", "L" + ITEM_CLASS + ";");
	private static final Member ELYTRA = new Member(ITEMS, "ELYTRA", "f_42741_", "L" + ITEM_CLASS + ";");
	/** Vanilla's check: {@code ItemStack.is(Item)}; from 26.1 on {@code TypedInstance.is(T)}, called as the stack's {@code is(Object)}. */
	private static final Member IS_ITEM = new Member(STACK_CLASS, "is", "m_150930_", "(L" + ITEM_CLASS + ";)Z");
	private static final Member IS_OBJECT = new Member(STACK_CLASS, "is", null, "(Ljava/lang/Object;)Z");
	private static final String ELYTRA_LAYER = "net/minecraft/client/renderer/entity/layers/ElytraLayer";
	private static final String FORGE_ELYTRA_LAYER = "net/minecraftforge/client/extensions/IForgeElytraLayer";
	private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
	/** The helper asking whether the layer's class keeps {@code shouldRender}, and its answers' field. */
	private static final String KEEPS_LAYER = "fabricated$keepsLayerDefault";
	private static final String KNOWN_LAYER = "fabricated$overridingLayer";

	/**
	 * The item checks' rows of a game method, each platform's: vanilla's check of the item, the platform's of the action;
	 * NeoForge's where it asks there too.
	 */
	private static List<PutBack> itemChecks(String owner, String method, String srgMethod, Member item, String action, boolean neoForge) {
		PutBack forge = new PutBack(owner, method, srgMethod, null, FORGE_CAN_PERFORM, FORGE_ACTION_EXT, Code.FORGE_ACTION, new ItemCheck(item, TOOL_ACTIONS, action));
		return neoForge ? List.of(new PutBack(owner, method, srgMethod, null, CAN_PERFORM, NEOFORGE_ACTION, Code.ACTION, new ItemCheck(item, ABILITIES, action)), forge)
				: List.of(forge);
	}

	private static final String BLOCKS = "net/minecraft/world/level/block/";
	private static final String ANIMALS = "net/minecraft/world/entity/animal/";
	/** The item checks' rows: the methods where vanilla asks for its shears, rod or shield, and the platforms ask an action, read in their classes. */
	private static final List<PutBack> ITEM_CHECKS = Stream.of(
			itemChecks(BLOCKS + "BeehiveBlock", "useItemOn", "m_6227_", SHEARS, "SHEARS_HARVEST", true),
			itemChecks(BLOCKS + "PumpkinBlock", "useItemOn", "m_6227_", SHEARS, "SHEARS_CARVE", true),
			itemChecks(BLOCKS + "TripWireBlock", "playerWillDestroy", "m_5707_", SHEARS, "SHEARS_DISARM", true),
			// Forge 65 and 66 only: NeoForge and Forge 47 to 64 shear these mobs from the shears item instead (NOT_PUT_BACK).
			itemChecks(ANIMALS + "sheep/Sheep", "mobInteract", "m_6071_", SHEARS, "SHEARS_HARVEST", false),
			itemChecks(ANIMALS + "golem/SnowGolem", "mobInteract", "m_6071_", SHEARS, "SHEARS_HARVEST", false),
			itemChecks(ANIMALS + "cow/MushroomCow", "mobInteract", "m_6071_", SHEARS, "SHEARS_HARVEST", false),
			itemChecks("net/minecraft/world/entity/monster/skeleton/Bogged", "mobInteract", "m_6071_", SHEARS, "SHEARS_HARVEST", false),
			itemChecks("net/minecraft/world/entity/projectile/FishingHook", "shouldStopFishing", "m_37136_", FISHING_ROD, "FISHING_ROD_CAST", true),
			// The client's line, from the hand holding the rod: 1.21.1's getPlayerHandPos, 1.20.1's render (SRG's m_7392_).
			itemChecks("net/minecraft/client/renderer/entity/FishingHookRenderer", "getPlayerHandPos", "m_7392_", FISHING_ROD, "FISHING_ROD_CAST", true),
			// Up to 1.21.1: from 1.21.5 a shield blocks by its components.
			itemChecks(PLAYER_CLASS, "hurtCurrentlyUsedShield", "m_7909_", SHIELD, "SHIELD_BLOCK", true),
			itemChecks("net/minecraft/world/entity/monster/piglin/StopHoldingItemIfNoLongerAdmiring", "lambda$create$0", "m_289159_", SHIELD, "SHIELD_BLOCK", true),
			List.of(new PutBack(ELYTRA_LAYER, "render", "m_6494_", null, new Member(ELYTRA_LAYER, "shouldRender", null, "(" + ITEM + "L" + LIVING + ";)Z"), null,
							Code.LAYER, new ItemCheck(ELYTRA, null, null)),
					// Forge 52's, from its IForgeElytraLayer<T>.
					new PutBack(ELYTRA_LAYER, "render", "m_6494_", null, new Member(ELYTRA_LAYER, "shouldRender", null, "(" + ITEM + "Ljava/lang/Object;)Z"), null,
							Code.LAYER, new ItemCheck(ELYTRA, null, null))))
			.flatMap(List::stream).toList();

	// SEAM-8: an extension's call of a block state or a stack where vanilla's code asks that object itself, the default
	// leading back to vanilla's code; the extensions by platform, and Forge's sound type's by version.
	private static final String LEVEL_READER = "Lnet/minecraft/world/level/LevelReader;";
	private static final String ENTITY_CLASS = "net/minecraft/world/entity/Entity";
	private static final String ENTITY = "L" + ENTITY_CLASS + ";";
	private static final String ENTITIES = "net/minecraft/world/entity/";
	private static final String SOUND_TYPE = "net/minecraft/world/level/block/SoundType";
	private static final String BOX = "net/minecraft/world/phys/AABB";
	private static final String HOLDER = "net/minecraft/core/Holder";
	private static final String STATE_BASE = "net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase";
	private static final String STATE_HOLDER = "net/minecraft/world/level/block/state/StateHolder";
	private static final String PIGLIN_AI = ENTITIES + "monster/piglin/PiglinAi";
	private static final String ENCHANTMENT_HELPER = "net/minecraft/world/item/enchantment/EnchantmentHelper";
	private static final String FRICTION_DESC = "(" + LEVEL_READER + POS + ENTITY + ")F";
	private static final String SOUND_DESC = "(" + LEVEL_READER + POS + ENTITY + ")L" + SOUND_TYPE + ";";
	private static final String PIGLIN_DESC = "(L" + LIVING + ";)Z";
	private static final String SWEEP_DESC = "(" + PLAYER + ENTITY + ")L" + BOX + ";";
	private static final String ENCHANTMENT_DESC = "(L" + HOLDER + ";)I";
	/** Vanilla's method of the state that Forge's sound type default reaches past, asking the block ({@link Extension#stateKeeps}). */
	private static final Member STATE_SOUND = new Member(STATE_BASE, "getSoundType", "m_60827_", "()L" + SOUND_TYPE + ";");
	private static final String KEEPS_FRICTION = "fabricated$keepsFrictionDefault";
	private static final String KNOWN_FRICTION = "fabricated$overridingFriction";
	private static final String KEEPS_SOUND = "fabricated$keepsSoundDefault";
	private static final String KNOWN_SOUND = "fabricated$overridingSound";
	private static final String KEEPS_PIGLIN = "fabricated$keepsPiglinDefault";
	private static final String KNOWN_PIGLIN = "fabricated$overridingPiglin";
	private static final String KEEPS_SWEEP = "fabricated$keepsSweepDefault";
	private static final String KNOWN_SWEEP = "fabricated$overridingSweep";
	private static final String KEEPS_ENCHANTMENT = "fabricated$keepsEnchantmentDefault";
	private static final String KNOWN_ENCHANTMENT = "fabricated$overridingEnchantment";
	private static final Extension FORGE_FRICTION = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "getFriction", FRICTION_DESC,
			KEEPS_FRICTION, KNOWN_FRICTION);
	private static final Extension FRICTION = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension", "getFriction",
			FRICTION_DESC, KEEPS_FRICTION, KNOWN_FRICTION).or(FORGE_FRICTION);
	/**
	 * Forge 52 on: the block's is {@code Block}'s own method ({@code IForgeBlock} declares it without a default). Forge's
	 * defaults ask the block where vanilla asks the state, so the state's class keeps vanilla's {@code getSoundType} too; on
	 * NeoForge, whose block's default asks the state, the same holds of the state's class, as a class can't tell its
	 * platform.
	 */
	private static final Extension FORGE_SOUND = new Extension(FORGE_EXT + "IForgeBlockState", BLOCK, "getSoundType", SOUND_DESC, KEEPS_SOUND, KNOWN_SOUND,
			BLOCK_OF_STATE, STATE, STATE_SOUND);
	/** Forge 47: {@code IForgeBlock}'s default. */
	private static final Extension FORGE_47_SOUND = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "getSoundType", SOUND_DESC,
			KEEPS_SOUND, KNOWN_SOUND, BLOCK_OF_STATE, STATE, STATE_SOUND);
	private static final Extension SOUND = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension", "getSoundType",
			SOUND_DESC, KEEPS_SOUND, KNOWN_SOUND, BLOCK_OF_STATE, STATE, STATE_SOUND).or(FORGE_SOUND);
	private static final Extension PIGLIN = new Extension(NEOFORGE_EXT + "IItemStackExtension", NEOFORGE_EXT + "IItemExtension", "makesPiglinsNeutral",
			PIGLIN_DESC, KEEPS_PIGLIN, KNOWN_PIGLIN, ITEM_OF_STACK, STACK_CLASS).or(new Extension(FORGE_EXT + "IForgeItemStack", FORGE_EXT + "IForgeItem",
					"makesPiglinsNeutral", PIGLIN_DESC, KEEPS_PIGLIN, KNOWN_PIGLIN, ITEM_OF_STACK, STACK_CLASS));
	private static final Extension SWEEP = new Extension(NEOFORGE_EXT + "IItemStackExtension", NEOFORGE_EXT + "IItemExtension", "getSweepHitBox", SWEEP_DESC,
			KEEPS_SWEEP, KNOWN_SWEEP, ITEM_OF_STACK, STACK_CLASS).or(new Extension(FORGE_EXT + "IForgeItemStack", FORGE_EXT + "IForgeItem", "getSweepHitBox",
					SWEEP_DESC, KEEPS_SWEEP, KNOWN_SWEEP, ITEM_OF_STACK, STACK_CLASS));
	/** NeoForge's alone: Forge 52 on keeps vanilla's lookup, and Forge 47's moves vanilla's whole loop into a method of its own. */
	private static final Extension NEOFORGE_ENCHANTMENT = new Extension(NEOFORGE_EXT + "IItemStackExtension", NEOFORGE_EXT + "IItemExtension",
			"getEnchantmentLevel", ENCHANTMENT_DESC, KEEPS_ENCHANTMENT, KNOWN_ENCHANTMENT, ITEM_OF_STACK, STACK_CLASS);
	/** NeoForge 26.x: the stack's is {@code ItemInstanceExtension}'s, the item's taking an {@code ItemInstance}. */
	private static final Extension NEOFORGE_ENCHANTMENT_26 = new Extension(NEOFORGE_EXT + "ItemInstanceExtension", NEOFORGE_EXT + "IItemExtension",
			"getEnchantmentLevel", ENCHANTMENT_DESC, KEEPS_ENCHANTMENT, KNOWN_ENCHANTMENT, ITEM_OF_STACK, ITEM_INSTANCE);
	private static final Member STATE_FRICTION = new Member(STATE, "getFriction", null, FRICTION_DESC);
	private static final Member STATE_SOUND_TYPE = new Member(STATE, "getSoundType", null, SOUND_DESC);
	private static final Member PIGLIN_NEUTRAL = new Member(STACK_CLASS, "makesPiglinsNeutral", null, PIGLIN_DESC);
	private static final Member SWEEP_BOX = new Member(STACK_CLASS, "getSweepHitBox", null, SWEEP_DESC);
	private static final Member ENCHANTMENT_LEVEL = new Member(STACK_CLASS, "getEnchantmentLevel", null, ENCHANTMENT_DESC);
	private static final Member INSTANCE_ENCHANTMENT_LEVEL = new Member(ITEM_INSTANCE, "getEnchantmentLevel", null, ENCHANTMENT_DESC);

	private static PutBack onReceiver(String owner, String method, String srgMethod, String desc, Member platform, Extension extension, OnReceiver form) {
		return new PutBack(owner, method, srgMethod, desc, platform, extension, Code.RECEIVER, null, form);
	}

	private static PutBack friction(String owner, String method, String srgMethod) {
		return onReceiver(owner, method, srgMethod, null, STATE_FRICTION, FRICTION, OnReceiver.FRICTION);
	}

	private static PutBack sound(String owner, String method, String srgMethod, String desc) {
		return onReceiver(owner, method, srgMethod, desc, STATE_SOUND_TYPE, SOUND, OnReceiver.SOUND);
	}

	/**
	 * SEAM-8's rows, read in the platforms' classes against vanilla's: where vanilla asks the block's friction (its movement
	 * of living entities, items, experience orbs, boats and, up to 1.21.1, flying mobs), the block state's sound type (a
	 * block's placing, an entity's step and fall sounds, a horse's steps, a sniffer's digging; on the client the hit sound
	 * of a block being broken and the level event's break sound), whether a piglin's target wears gold (1.20.1, 1.21.1) or
	 * safe armour (1.21.11 on), the sweep attack's box (in {@code attack} up to 1.21.1, in {@code doSweepAttack} from
	 * 1.21.11 on Forge) and, on NeoForge, an item's enchantment level. A row stands for both platforms, whose calls are
	 * the same.
	 */
	private static final List<PutBack> ON_RECEIVERS = List.of(
			friction(ENTITIES + "LivingEntity", "travel", "m_7023_"),
			friction(ENTITIES + "LivingEntity", "travelInAir", null),
			friction(ENTITIES + "item/ItemEntity", "tick", "m_8119_"),
			friction(ENTITIES + "ExperienceOrb", "tick", "m_8119_"),
			friction(ENTITIES + "FlyingMob", "travel", "m_7023_"),
			friction(ENTITIES + "vehicle/Boat", "getGroundFriction", "m_38377_"),
			friction(ENTITIES + "vehicle/boat/AbstractBoat", "getGroundFriction", null),
			sound("net/minecraft/world/item/BlockItem", "place", "m_40576_", null),
			// Vanilla's own step sound; the platforms' overloads of the combination and muffled ones, which take positions, aren't vanilla's.
			sound(ENTITY_CLASS, "playStepSound", "m_7355_", "(" + POS + "L" + STATE + ";)V"),
			sound(ENTITIES + "LivingEntity", "playBlockFallSound", "m_21229_", "()V"),
			sound(ENTITIES + "animal/horse/AbstractHorse", "playStepSound", "m_7355_", null),
			sound(ENTITIES + "animal/equine/AbstractHorse", "playStepSound", null, null),
			sound(ENTITIES + "animal/sniffer/Sniffer", "emitDiggingParticles", "m_272274_", null),
			sound(CLIENT_GAME_MODE, "continueDestroyBlock", "m_105283_", null),
			sound("net/minecraft/client/renderer/LevelRenderer", "levelEvent", "m_234304_", null),
			sound("net/minecraft/client/renderer/LevelEventHandler", "levelEvent", null, null),
			onReceiver(PIGLIN_AI, "isWearingGold", "m_34808_", null, PIGLIN_NEUTRAL, PIGLIN, OnReceiver.GOLD_HOLDER),
			onReceiver(PIGLIN_AI, "isWearingSafeArmor", null, null, PIGLIN_NEUTRAL, PIGLIN, OnReceiver.SAFE_ARMOR),
			onReceiver(PLAYER_CLASS, "attack", "m_5706_", null, SWEEP_BOX, SWEEP, OnReceiver.SWEEP),
			onReceiver(PLAYER_CLASS, "doSweepAttack", null, null, SWEEP_BOX, SWEEP, OnReceiver.SWEEP),
			onReceiver(ENCHANTMENT_HELPER, "getItemEnchantmentLevel", null, null, ENCHANTMENT_LEVEL, NEOFORGE_ENCHANTMENT, OnReceiver.ENCHANTMENT),
			onReceiver(ENCHANTMENT_HELPER, "getItemEnchantmentLevel", null, null, INSTANCE_ENCHANTMENT_LEVEL, NEOFORGE_ENCHANTMENT_26, OnReceiver.ENCHANTMENT));

	// SEAM-13: the shovel's tool modifications, the state's getToolModifiedState(context, action, simulate) of the platform's
	// action, and Forge's empty block check above the path; the helpers posting the platform's event as its default does.
	private static final String SHOVEL = "net/minecraft/world/item/ShovelItem";
	private static final String USE_ON_CONTEXT = "net/minecraft/world/item/context/UseOnContext";
	private static final String CAMPFIRE = "net/minecraft/world/level/block/CampfireBlock";
	private static final String KEEPS_TOOL = "fabricated$keepsToolDefault";
	private static final String KNOWN_TOOL = "fabricated$overridingTool";
	private static final Extension NEOFORGE_TOOL = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension", "getToolModifiedState",
			"(L" + USE_ON_CONTEXT + ";L" + ABILITY + ";Z)L" + STATE + ";", KEEPS_TOOL, KNOWN_TOOL);
	private static final Extension FORGE_TOOL = new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "getToolModifiedState",
			"(L" + USE_ON_CONTEXT + ";L" + TOOL_ACTION + ";Z)L" + STATE + ";", KEEPS_TOOL, KNOWN_TOOL);
	/** The level's empty block check, {@code LevelReader}'s default, which a level keeping it keeps; Forge 47's by SRG's name. */
	private static final Member IS_EMPTY_BLOCK = new Member(LEVEL, "isEmptyBlock", "m_46859_", "(" + POS + ")Z");
	private static final String LEVEL_READER_CLASS = "net/minecraft/world/level/LevelReader";

	/**
	 * The shovel's rows, read in the platforms' classes against vanilla's (1.20.1 to 26.2; 26.3's shovels flatten through
	 * their item's data): where vanilla's {@code ShovelItem.useOn} looks the block up in {@code FLATTENABLES}, both platforms
	 * ask the state's {@code getToolModifiedState} of the shovel's flatten action; where vanilla checks for a lit campfire and
	 * douses it, NeoForge asks it of the douse action; and where vanilla asks whether the block above is air, Forge asks the
	 * level's {@code isEmptyBlock}.
	 */
	private static final List<PutBack> TOOLS = List.of(
			onReceiver(SHOVEL, "useOn", "m_6225_", null, new Member(STATE, NEOFORGE_TOOL.name(), null, NEOFORGE_TOOL.desc()), NEOFORGE_TOOL, OnReceiver.FLATTEN),
			onReceiver(SHOVEL, "useOn", "m_6225_", null, new Member(STATE, NEOFORGE_TOOL.name(), null, NEOFORGE_TOOL.desc()), NEOFORGE_TOOL, OnReceiver.DOUSE),
			onReceiver(SHOVEL, "useOn", "m_6225_", null, new Member(STATE, FORGE_TOOL.name(), null, FORGE_TOOL.desc()), FORGE_TOOL, OnReceiver.FLATTEN),
			new PutBack(SHOVEL, "useOn", "m_6225_", null, IS_EMPTY_BLOCK, null, Code.LEVEL));

	// SEAM-12: a crop's growth speed, CropBlock.getGrowthSpeed. Vanilla checks each of the nine soil blocks below the crop
	// for farmland (1.19.2 to 1.21.11: soil.is(Blocks.FARMLAND); 26.1 on: soil.is(BlockTags.GROWS_CROPS)) and, on farmland,
	// its moisture (soil.getValue(FarmBlock.MOISTURE) > 0; 26.1 on: soil.getValueOrElse(FarmlandBlock.MOISTURE, 0) > 0).
	// Read in the platforms' classes (NeoForge 21.1.265, 21.11.45, 26.1.2.116, 26.2.0.90 and 26.3.0.95; Forge 43.5.2 and
	// 47.4.28 under SRG's names, 52.1.21, 61.2.4, 64.1.5, 65.1.5 and 66.1.0):
	// - NeoForge asks the soil state's canSustainPlant(level, pos, UP, plant), a TriState (NeoForge's on 21.1, Minecraft's from
	//   21.11, which NeoForge gives isDefault and isTrue) whose default (IBlockExtension's)
	//   is DEFAULT, for which 21.1 and 21.11 then ask soil.getBlock() instanceof FarmBlock and 26.x vanilla's own
	//   soil.is(BlockTags.GROWS_CROPS), and its isFertile(level, pos), whose default answers
	//   soil.getValue(MOISTURE) > 0 for a farm block (21.x's FarmBlock, 26.x's FarmlandBlock) and false otherwise.
	// - Forge asks the soil state's canSustainPlant(level, pos, UP, (IPlantable) crop), Block's own method, which for a crop
	//   (a CropBlock keeping its mayPlaceOn, VegetationBlock's or BushBlock's getPlant and IPlantable's getPlantType, the
	//   plant type CROP) answers crop.mayPlaceOn(soil) || soil.is(FARMLAND) (64 to 66: soil.is(GROWS_CROPS)), CropBlock's
	//   mayPlaceOn being soil.is(FARMLAND) on 43 and 47, soil.getBlock() instanceof FarmBlock on 52 and 61 and instanceof
	//   FarmlandBlock on 64 to 66; and its isFertile, whose default (IForgeBlock's) answers soil.getValue(MOISTURE) > 0 for
	//   vanilla's farmland (43 to 52: soil.is(FARMLAND); 61: a FarmBlock; 64 to 66: a FarmlandBlock) and false otherwise.
	// So for a soil keeping the defaults the platform's answer is vanilla's where VANILLA_SOIL says (NeoForge 21.x and
	// Forge 52 and 61: vanilla's farmland or no farm block; Forge 64 to 66: no farmland block, or tagged #grows_crops; Forge
	// 43 and 47 always, for a crop keeping its checks), and vanilla's farmland check goes back there; NeoForge 26.x already
	// makes it in its default's branch. The moisture check goes back for vanilla's farmland (VANILLA_FARMLAND), for which every
	// default answers vanilla's getValue(MOISTURE) > 0 (the property is farmland's), and which vanilla's code always checks
	// for moisture. Every other soil, and on Forge a crop overriding one of its checks or no CropBlock (a stem, the pitcher
	// crop), is asked as the platform has it, without the hooks.
	private static final String CROP = BLOCKS + "CropBlock";
	private static final String FARM_BLOCK = BLOCKS + "FarmBlock";
	private static final String FARMLAND_BLOCK = BLOCKS + "FarmlandBlock";
	/** NeoForge 21.1's TriState; 21.11 on, NeoForge's canSustainPlant answers Minecraft's own, which NeoForge gives the same methods. */
	private static final String TRI_STATE = "net/neoforged/neoforge/common/util/TriState";
	private static final String MINECRAFT_TRI_STATE = "net/minecraft/util/TriState";
	private static final String PLANTABLE = "net/minecraftforge/common/IPlantable";
	private static final String DIRECTION = "Lnet/minecraft/core/Direction;";
	private static final String SUSTAIN_DESC = "(" + GETTER + POS + DIRECTION + "L" + STATE + ";)L" + TRI_STATE + ";";
	private static final String SUSTAIN_1211_DESC = "(" + GETTER + POS + DIRECTION + "L" + STATE + ";)L" + MINECRAFT_TRI_STATE + ";";
	private static final String FORGE_SUSTAIN_DESC = "(" + GETTER + POS + DIRECTION + "L" + PLANTABLE + ";)Z";
	private static final String FERTILE_DESC = "(" + GETTER + POS + ")Z";
	private static final String KEEPS_SUSTAIN = "fabricated$keepsSustainDefault";
	private static final String KNOWN_SUSTAIN = "fabricated$overridingSustain";
	private static final String KEEPS_FERTILE = "fabricated$keepsFertileDefault";
	private static final String KNOWN_FERTILE = "fabricated$overridingFertile";
	private static final Extension SUSTAIN = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension", "canSustainPlant",
			SUSTAIN_DESC, KEEPS_SUSTAIN, KNOWN_SUSTAIN);
	private static final Extension SUSTAIN_1211 = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension", "canSustainPlant",
			SUSTAIN_1211_DESC, KEEPS_SUSTAIN, KNOWN_SUSTAIN);
	/** Forge's: the block's is {@code Block}'s own method, which {@code IForgeBlock} declares without a default. */
	private static final Extension FORGE_SUSTAIN = new Extension(FORGE_EXT + "IForgeBlockState", BLOCK, "canSustainPlant", FORGE_SUSTAIN_DESC, KEEPS_SUSTAIN,
			KNOWN_SUSTAIN);
	private static final Extension FERTILE = new Extension(NEOFORGE_EXT + "IBlockStateExtension", NEOFORGE_EXT + "IBlockExtension", "isFertile",
			FERTILE_DESC, KEEPS_FERTILE, KNOWN_FERTILE).or(new Extension(FORGE_EXT + "IForgeBlockState", FORGE_EXT + "IForgeBlock", "isFertile", FERTILE_DESC,
					KEEPS_FERTILE, KNOWN_FERTILE));
	/** The helper asking whether vanilla's farmland check answers the platform's for the soil (and on Forge the crop). */
	static final String VANILLA_SOIL = "fabricated$vanillaSoil";
	/** The helper asking whether the soil is vanilla's farmland, whose moisture every platform's default checks as vanilla does. */
	static final String VANILLA_FARMLAND = "fabricated$vanillaFarmland";
	/** The helper asking whether a crop's class keeps its checks Forge's canSustainPlant asks (Forge's farmland checks), and its answers' field. */
	private static final String KEEPS_CROP = "fabricated$keepsCropDefault";
	private static final String KNOWN_CROP = "fabricated$overridingCrop";
	/** The helper making NeoForge's TriState of vanilla's answer: {@code TRUE} or {@code FALSE}. */
	private static final String TRI_STATE_OF = "fabricated$triState";

	/** The growth speed's rows, read in the platforms' classes: NeoForge's and Forge's farmland checks, either platform's moisture check. */
	private static final List<PutBack> SOILS = List.of(
			onReceiver(CROP, "getGrowthSpeed", "m_52272_", null, new Member(STATE, SUSTAIN.name(), null, SUSTAIN_DESC), SUSTAIN, OnReceiver.FARMLAND),
			onReceiver(CROP, "getGrowthSpeed", "m_52272_", null, new Member(STATE, SUSTAIN.name(), null, SUSTAIN_1211_DESC), SUSTAIN_1211, OnReceiver.FARMLAND),
			onReceiver(CROP, "getGrowthSpeed", "m_52272_", null, new Member(STATE, FORGE_SUSTAIN.name(), null, FORGE_SUSTAIN_DESC), FORGE_SUSTAIN,
					OnReceiver.FORGE_FARMLAND),
			onReceiver(CROP, "getGrowthSpeed", "m_52272_", null, new Member(STATE, FERTILE.name(), null, FERTILE_DESC), FERTILE, OnReceiver.MOISTURE));

	// SEAM-9: an entity's eyes in water. Where vanilla asks entity.isEyeInFluid(FluidTags.WATER) in Player.getDestroySpeed (the
	// slower mining underwater) and in the HUD's air bubbles (Gui.extractAirBubbles, 26.2's and 26.3's Hud), NeoForge
	// 26.1.2.116, 26.2.0.90 and 26.3.0.95 ask entity.getFluidInteraction().isEyeInFluidMatching(entity, predicate) of the fluid
	// types at its eyes (read in their classes: type.getIsWaterLike() for the mining, e.canDrownInFluidType(type) for the
	// bubbles), which answers true where a tracker of the eyes' fluids has its predicate true. Where water or lava at the eyes
	// has it true, that answer is true whatever else is there; where the eyes are in no fluid type but vanilla's water's and
	// lava's, it is the predicate's answer for those. Vanilla's call answers whether they are in water (NeoForge's
	// Entity.isEyeInFluid(TagKey) asks its fluid interaction, by the water type on 26.1.2 and 26.2 and by the tag on 26.3):
	// VANILLA_EYES asks both and puts vanilla's call back where they agree. NeoForge 21.1 and 21.11 and Forge keep
	// vanilla's call in these places.
	private static final String FLUID_INTERACTION = ENTITIES + "EntityFluidInteraction";
	private static final String FLUID_TYPE = "net/neoforged/neoforge/fluids/FluidType";
	private static final String IN_FLUID_PREDICATE = "net/neoforged/neoforge/fluids/InFluidPredicate";
	private static final String PREDICATE_DESC = "(" + ENTITY + "L" + FLUID_TYPE + ";D)Z";
	private static final Member EYES_MATCHING = new Member(FLUID_INTERACTION, "isEyeInFluidMatching", null, "(" + ENTITY + "L" + IN_FLUID_PREDICATE + ";)Z");
	private static final Member FLUID_INTERACTION_OF = new Member(ENTITY_CLASS, "getFluidInteraction", null, "()L" + FLUID_INTERACTION + ";");
	private static final String TAG_KEY = "Lnet/minecraft/tags/TagKey;";
	private static final Member WATER_TAG = new Member("net/minecraft/tags/FluidTags", "WATER", null, TAG_KEY);
	/** The helper asking whether vanilla's call answers the platform's check for the entity ({@link #vanillaEyes}), and the predicate it asks with. */
	static final String VANILLA_EYES = "fabricated$vanillaEyes";
	private static final String VANILLA_EYES_DESC = "(" + ENTITY + "L" + IN_FLUID_PREDICATE + ";)Z";
	private static final String OTHER_FLUID = "fabricated$otherFluid";
	/** The helper asking whether an entity's class keeps Entity's isEyeInFluid, which vanilla's call put back makes, and its answers' field. */
	private static final String KEEPS_EYES = "fabricated$keepsEyesDefault";
	private static final String KNOWN_EYES = "fabricated$overridingEyes";

	/** The eye checks' rows, read in NeoForge 26.x's classes: the mining speed, the air bubbles (26.3's HUD is Hud). */
	private static final List<PutBack> EYES = List.of(
			new PutBack(PLAYER_CLASS, "getDestroySpeed", null, null, EYES_MATCHING, null, Code.EYES),
			new PutBack("net/minecraft/client/gui/Gui", "extractAirBubbles", null, null, EYES_MATCHING, null, Code.EYES),
			new PutBack("net/minecraft/client/gui/Hud", "extractAirBubbles", null, null, EYES_MATCHING, null, Code.EYES));

	/**
	 * The table: the platforms' forms of the server's and the client's {@code destroyBlock} and of {@code getDestroyProgress},
	 * read in their classes, the item checks, the extensions' calls of their objects, the shovel's tool modifications, a
	 * crop's growth speed and the eye checks.
	 */
	static final List<PutBack> PUT_BACKS = Stream.concat(Stream.of(
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, new Member(GAME_MODE, "removeBlock", null, "(" + POS + "L" + STATE + ";Z)Z"),
					NEOFORGE_21_1, Code.SERVER),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, new Member(GAME_MODE, "removeBlock", null, "(" + POS + "L" + STATE + ";Z" + ITEM + ")Z"),
					NEOFORGE_BREAK, Code.SERVER),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, new Member(GAME_MODE, "removeBlock", null, "(" + POS + "Z)Z"), FORGE_BREAK,
					Code.FORGE_SERVER),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, new Member(STATE, NEOFORGE_21_1.name(), null, NEOFORGE_21_1.desc()),
					NEOFORGE_21_1, Code.CLIENT),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, new Member(STATE, NEOFORGE_BREAK.name(), null, NEOFORGE_BREAK.desc()),
					NEOFORGE_BREAK, Code.CLIENT),
			new PutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, new Member(STATE, FORGE_BREAK.name(), null, FORGE_BREAK.desc()), FORGE_BREAK,
					Code.FORGE_CLIENT),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, CAN_HARVEST, NEOFORGE_HARVEST, Code.HARVEST),
			new PutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, CAN_HARVEST, FORGE_HARVEST, Code.FORGE_HARVEST),
			new PutBack(BEHAVIOUR, "getDestroyProgress", "m_5880_", DESTROY_PROGRESS, HARVEST_HOOK, null, Code.PROGRESS),
			new PutBack(BEHAVIOUR, "getDestroyProgress", "m_5880_", DESTROY_PROGRESS, FORGE_HARVEST_HOOK, null, Code.FORGE_PROGRESS)),
			Stream.of(ITEM_CHECKS, ON_RECEIVERS, TOOLS, SOILS, EYES).flatMap(List::stream)).toList();

	static final Set<String> CLASSES = PUT_BACKS.stream().map(PutBack::owner).collect(Collectors.toUnmodifiableSet());

	// Vanilla's calls and the fields they read, and what the helpers call.
	private static final Member REMOVE_BLOCK = new Member(SERVER_LEVEL, "removeBlock", "m_7471_", "(" + POS + "Z)Z");
	private static final Member DESTROY = new Member(BLOCK, "destroy", "m_6786_", "(Lnet/minecraft/world/level/LevelAccessor;" + POS + "L" + STATE + ";)V");
	private static final Member SET_BLOCK = new Member(LEVEL, "setBlock", "m_7731_", "(" + POS + "L" + STATE + ";I)Z");
	private static final Member LEGACY_BLOCK = new Member("net/minecraft/world/level/material/FluidState", "createLegacyBlock", "m_76188_", "()L" + STATE + ";");
	private static final Member WILL_DESTROY = new Member(BLOCK, "playerWillDestroy", "m_5707_", "(L" + LEVEL + ";" + POS + "L" + STATE + ";" + PLAYER + ")L"
			+ STATE + ";", "(L" + LEVEL + ";" + POS + "L" + STATE + ";" + PLAYER + ")V");
	private static final Member GET_BLOCK_STATE = new Member(SERVER_LEVEL, "getBlockState", "m_8055_", "(" + POS + ")L" + STATE + ";");
	private static final Member LEVEL_FIELD = new Member(GAME_MODE, "level", "f_9244_", "L" + SERVER_LEVEL + ";");
	private static final Member PLAYER_FIELD = new Member(GAME_MODE, "player", "f_9245_", "L" + SERVER_PLAYER + ";");
	/** Vanilla's harvest check, as the server's {@code destroyBlock} makes it (of its {@code ServerPlayer}) and {@code getDestroyProgress} (of its {@code Player}). */
	private static final Member SERVER_HAS_CORRECT_TOOL = new Member(SERVER_PLAYER, "hasCorrectToolForDrops", "m_36298_", "(L" + STATE + ";)Z");
	private static final Member HAS_CORRECT_TOOL = new Member(PLAYER_CLASS, "hasCorrectToolForDrops", "m_36298_", "(L" + STATE + ";)Z");
	/** The helper posting NeoForge's harvest check event with vanilla's answer, as {@code EventHooks.doPlayerHarvestCheck} does. */
	private static final String HARVEST_EVENT = "fabricated$harvestCheck";
	private static final String HARVEST_EVENT_DESC = "(Z" + PLAYER + "L" + STATE + ";" + GETTER + POS + ")Z";
	private static final String HARVEST_CHECK = "net/neoforged/neoforge/event/entity/player/PlayerEvent$HarvestCheck";
	private static final String EVENT_BUS = "net/neoforged/bus/api/IEventBus";
	private static final String EVENT = "net/neoforged/bus/api/Event";
	/** The helper asking whether the player's class keeps {@code Player.hasCorrectToolForDrops} (Forge's harvest checks), and its answers' field. */
	private static final String KEEPS_PLAYER = "fabricated$keepsPlayerHarvest";
	private static final String KNOWN_PLAYER = "fabricated$overridingPlayer";
	// SEAM-8's vanilla calls and the fields they read (1.20.1's material, by SRG's names, an ArmorMaterial and the enum's constant).
	private static final Member BLOCK_FRICTION = new Member(BLOCK, "getFriction", "m_49958_", "()F");
	private static final Member SOUND_OF_STATE = new Member(STATE, "getSoundType", "m_60827_", "()L" + SOUND_TYPE + ";");
	private static final Member SAFE_ARMOR_TAG = new Member("net/minecraft/tags/ItemTags", "PIGLIN_SAFE_ARMOR", null, "Lnet/minecraft/tags/TagKey;");
	private static final Member IS_TAG = new Member(STACK_CLASS, "is", null, "(Lnet/minecraft/tags/TagKey;)Z");
	private static final Member BOUNDING_BOX = new Member(ENTITY_CLASS, "getBoundingBox", "m_20191_", "()L" + BOX + ";");
	private static final Member INFLATE = new Member(BOX, "inflate", "m_82377_", "(DDD)L" + BOX + ";");
	private static final String ITEM_ENCHANTMENTS = "net/minecraft/world/item/enchantment/ItemEnchantments";
	private static final Member ENCHANTMENTS = new Member("net/minecraft/core/component/DataComponents", "ENCHANTMENTS", null,
			"Lnet/minecraft/core/component/DataComponentType;");
	private static final Member NO_ENCHANTMENTS = new Member(ITEM_ENCHANTMENTS, "EMPTY", null, "L" + ITEM_ENCHANTMENTS + ";");
	private static final String GET_OR_DEFAULT_DESC = "(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;";
	private static final Member GET_OR_DEFAULT = new Member(STACK_CLASS, "getOrDefault", null, GET_OR_DEFAULT_DESC);
	/** 26.x's, of an {@code ItemInstance}, an interface's method. */
	private static final Member INSTANCE_GET_OR_DEFAULT = new Member(ITEM_INSTANCE, "getOrDefault", null, GET_OR_DEFAULT_DESC);
	private static final Member LEVEL_OF = new Member(ITEM_ENCHANTMENTS, "getLevel", null, "(L" + HOLDER + ";)I");
	/** The helper posting NeoForge's enchantment level event with vanilla's level, as the stack's default posts it ({@code EventHooks.getEnchantmentLevelSpecific}). */
	private static final String ENCHANTMENT_EVENT = "fabricated$enchantmentLevel";
	private static final String LEVEL_SPECIFIC = "getEnchantmentLevelSpecific";
	private static final String ARMOR_ITEM = "net/minecraft/world/item/ArmorItem";
	private static final Member ARMOR_MATERIAL = new Member(ARMOR_ITEM, "getMaterial", "m_40401_", "()L" + HOLDER + ";", "()Lnet/minecraft/world/item/ArmorMaterial;");
	private static final Member GOLD_MATERIAL = new Member("net/minecraft/world/item/ArmorMaterials", "GOLD", "GOLD", "L" + HOLDER + ";",
			"Lnet/minecraft/world/item/ArmorMaterials;");
	/** {@code Holder.is(Holder)}, an interface's method. */
	private static final Member HOLDER_IS = new Member(HOLDER, "is", null, "(L" + HOLDER + ";)Z");
	/** The helper asking whether NeoForge's and Forge's 1.21.1 check of the stack's material, by identity, and vanilla's, by {@code Holder.is}, agree. */
	private static final String GOLD_AGREES = "fabricated$goldAgrees";
	// SEAM-13's vanilla calls and fields (1.20.1's by SRG's names, where Forge 47 makes the flatten and empty block checks),
	// and the helpers' calls of the platforms' event hooks.
	private static final Member FLATTENABLES = new Member(SHOVEL, "FLATTENABLES", "f_43110_", "Ljava/util/Map;");
	private static final Member MAP_GET = new Member("java/util/Map", "get", null, "(Ljava/lang/Object;)Ljava/lang/Object;");
	private static final Member CAMPFIRE_LIT = new Member(CAMPFIRE, "LIT", null, "Lnet/minecraft/world/level/block/state/properties/BooleanProperty;");
	private static final Member GET_VALUE = new Member(STATE, "getValue", null, "(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;");
	private static final Member SET_VALUE = new Member(STATE, "setValue", null,
			"(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;");
	private static final Member BOOLEAN_VALUE = new Member("java/lang/Boolean", "booleanValue", null, "()Z");
	private static final Member BOOLEAN_OF = new Member("java/lang/Boolean", "valueOf", null, "(Z)Ljava/lang/Boolean;");
	private static final Member DOWSE = new Member(CAMPFIRE, "dowse", null, "(" + ENTITY + "Lnet/minecraft/world/level/LevelAccessor;" + POS + "L" + STATE + ";)V");
	private static final Member CONTEXT_PLAYER = new Member(USE_ON_CONTEXT, "getPlayer", "m_43723_", "()" + PLAYER);
	private static final Member CONTEXT_LEVEL = new Member(USE_ON_CONTEXT, "getLevel", "m_43725_", "()L" + LEVEL + ";");
	private static final Member CONTEXT_POS = new Member(USE_ON_CONTEXT, "getClickedPos", "m_8083_", "()" + POS);
	private static final Member CONTEXT_ITEM = new Member(USE_ON_CONTEXT, "getItemInHand", "m_43722_", "()" + ITEM);
	private static final Member LEVEL_BLOCK_STATE = new Member(LEVEL, "getBlockState", "m_8055_", "(" + POS + ")L" + STATE + ";");
	private static final Member IS_AIR = new Member(STATE, "isAir", "m_60795_", "()Z");
	private static final String FORGE_EVENT_FACTORY = "net/minecraftforge/event/ForgeEventFactory";
	/** The helper making the platform's tool modification event and the held item's check of the action, as its default makes them. */
	private static final String TOOL_USE = "fabricated$toolUse";
	/** The helper asking whether the level's class keeps {@code LevelReader.isEmptyBlock}, and its answers' field. */
	private static final String KEEPS_LEVEL = "fabricated$keepsLevelDefault";
	private static final String KNOWN_LEVEL = "fabricated$overridingLevel";

	// SEAM-12's vanilla calls and fields (1.19.2's and 1.20.1's by SRG's names, where Forge 43 and 47 make the farmland and
	// moisture checks).
	private static final Member FARMLAND = new Member(BLOCKS + "Blocks", "FARMLAND", "f_50093_", "L" + BLOCK + ";");
	private static final Member IS_BLOCK = new Member(STATE, "is", "m_60713_", "(L" + BLOCK + ";)Z");
	private static final Member GROWS_CROPS = new Member("net/minecraft/tags/BlockTags", "GROWS_CROPS", null, "Lnet/minecraft/tags/TagKey;");
	private static final Member IS_BLOCK_TAG = new Member(STATE, "is", null, "(Lnet/minecraft/tags/TagKey;)Z");
	private static final String INTEGER_PROPERTY = "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;";
	private static final Member FARM_MOISTURE = new Member(FARM_BLOCK, "MOISTURE", "f_53243_", INTEGER_PROPERTY);
	private static final Member FARMLAND_MOISTURE = new Member(FARMLAND_BLOCK, "MOISTURE", null, INTEGER_PROPERTY);
	private static final Member STATE_VALUE = new Member(STATE, "getValue", "m_61143_", GET_VALUE.desc());
	private static final Member STATE_VALUE_OR_ELSE = new Member(STATE, "getValueOrElse", null,
			"(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Comparable;");
	private static final Member INT_VALUE = new Member("java/lang/Integer", "intValue", null, "()I");
	private static final Member INTEGER_OF = new Member("java/lang/Integer", "valueOf", null, "(I)Ljava/lang/Integer;");
	/** The crop's checks Forge's canSustainPlant asks: CropBlock's mayPlaceOn, and IPlantable's getPlantType and getPlant. */
	private static final Member MAY_PLACE_ON = new Member(CROP, "mayPlaceOn", "m_6266_", "(L" + STATE + ";" + GETTER + POS + ")Z");
	private static final Member PLANT_TYPE = new Member(PLANTABLE, "getPlantType", null, "(" + GETTER + POS + ")Lnet/minecraftforge/common/PlantType;");
	private static final Member PLANT = new Member(PLANTABLE, "getPlant", null, "(" + GETTER + POS + ")L" + STATE + ";");

	private VanillaCalls() {
	}

	/** The rows that apply to the class as it is: its method makes the platform's call, and the class is that platform's. */
	static List<PutBack> applicable(ClassNode node) {
		List<PutBack> rows = new ArrayList<>();
		if (!CLASSES.contains(node.name)) return rows;
		boolean srg = VanillaSeams.srg(node);
		for (PutBack row : PUT_BACKS) {
			if (!row.owner().equals(node.name)) continue;
			MethodNode method = holder(node, row, srg);
			if (method != null && !calls(method, row).isEmpty() && (row.platformPackage() == null || references(node, row.platformPackage()))
					&& (row.code() != Code.FORGE_CLIENT || calls(method, WILL_DESTROY.owner(), WILL_DESTROY.name(srg)).isEmpty())
					&& (row.code() != Code.LAYER || layerBase(node, row) != null)
					// From 1.21.11 vanilla makes the sweep's box in doSweepAttack: NeoForge's attack makes it for an overload of its own there.
					&& (row.form() != OnReceiver.SWEEP || !"attack".equals(row.method()) || node.methods.stream().noneMatch(m -> m.name.equals("doSweepAttack")))
					// NeoForge 26.x makes vanilla's farmland check in its canSustainPlant default's branch itself (SEAM-12).
					&& (row.form() != OnReceiver.FARMLAND || !java25(node.version))) {
				rows.add(variant(row, node, srg));
			}
		}
		return rows;
	}

	/**
	 * The row as the class's platform version has its extension and vanilla's code: NeoForge 26.x asks the stack's
	 * ItemInstance extension for an action, its item's taking an ItemInstance; Forge 47 asks {@code IForgeBlock}'s sound type
	 * default, which Forge 52 on declares on {@code Block}; and 1.20.1's piglin check compares the material with gold's by
	 * identity, 1.21.1's through {@code Holder.is}.
	 */
	private static PutBack variant(PutBack row, ClassNode node, boolean srg) {
		if (row.code() == Code.ACTION && java25(node.version)) return row.with(NEOFORGE_ACTION_26, null);
		// A crop's growth speed from 26.1 on: Forge 64 to 66's farmland check of #grows_crops, the moisture check's getValueOrElse.
		if (row.form() == OnReceiver.FORGE_FARMLAND && java25(node.version)) return row.with(row.extension(), OnReceiver.GROWS_CROPS);
		if (row.form() == OnReceiver.MOISTURE && java25(node.version)) return row.with(row.extension(), OnReceiver.MOISTURE_OR_ELSE);
		// Only Forge 47 runs with SRG's names: its interfaces are named outright.
		if (row.code().receiver() && srg) {
			return row.with(row.form() == OnReceiver.SOUND ? FORGE_47_SOUND : row.extension().forge(), row.form() == OnReceiver.GOLD_HOLDER ? OnReceiver.GOLD : row.form());
		}
		return row;
	}

	/**
	 * The class or interface declaring the elytra layer's {@code shouldRender}, which a layer keeping it keeps: the layer's
	 * class where it declares it (NeoForge 21.1, Forge 47), else the platform's interface it implements (Forge 52's
	 * {@code IForgeElytraLayer}); null where neither.
	 */
	private static String layerBase(ClassNode node, PutBack row) {
		if (indexOf(node, row.platform().name(), row.platform().desc()) >= 0) return node.name;
		for (String itf : node.interfaces) {
			if (itf.startsWith(FORGE) || itf.startsWith(NEOFORGE)) return itf;
		}
		return null;
	}

	/**
	 * Whether a put-back may apply to the class as its bytes are, told without parsing them: for a class of item checks,
	 * extensions' calls of their objects and eye checks alone, whether they name a row's platform call and, for an action,
	 * the action (Player's names no shield action from 1.21.5 on); true for any other class.
	 */
	static boolean mayApply(String owner, byte[] data) {
		boolean named = false;
		for (PutBack row : PUT_BACKS) {
			if (!row.owner().equals(owner)) continue;
			if (!row.code().item() && !row.code().receiver() && row.code() != Code.EYES) return true;
			named = true;
			// An extension's call by its descriptor too: NeoForge 26.x's Entity names vanilla's getSoundType() alone.
			if (contains(data, row.platform().name()) && (row.check() == null || row.check().action() == null || contains(data, row.check().action()))
					&& (!row.code().receiver() || contains(data, row.platform().desc()))) return true;
		}
		return !named;
	}

	/**
	 * Whether every row of the class is an extension's call of its object (SEAM-8), whose put-back asks nothing of the
	 * class's other methods: pre-flight's view reads only the rows' methods of such a class whole ({@link VanillaSeams}).
	 */
	static boolean receiversOnly(String owner) {
		boolean any = false;
		for (PutBack row : PUT_BACKS) {
			if (!row.owner().equals(owner)) continue;
			if (!row.code().receiver()) return false;
			any = true;
		}
		return any;
	}

	/** The names, Mojang's and SRG's, of the methods the class's rows put calls back in. */
	static Set<String> methodNames(String owner) {
		Set<String> names = new java.util.HashSet<>();
		for (PutBack row : PUT_BACKS) {
			if (!row.owner().equals(owner)) continue;
			if (row.method() != null) names.add(row.method());
			if (row.srgMethod() != null) names.add(row.srgMethod());
		}
		return names;
	}

	/** Whether the bytes hold the ASCII text, as a class's constant pool holds a name. */
	private static boolean contains(byte[] data, String ascii) {
		search:
		for (int i = 0, n = ascii.length(); i + n <= data.length; i++) {
			for (int j = 0; j < n; j++) {
				if (data[i + j] != (byte) ascii.charAt(j)) continue search;
			}
			return true;
		}
		return false;
	}

	/** The findings the class's rows answer, as reports name them ({@code SEAM-3, SEAM-4}); empty for a class the table doesn't name. */
	static String findings(String owner) {
		return PUT_BACKS.stream().filter(r -> r.owner().equals(owner)).map(PutBack::finding).distinct().sorted().collect(Collectors.joining(", "));
	}

	/**
	 * Puts back the class's calls ({@link #applicable}), all of a method's places or none; returns what it put back, as
	 * {@code method: owner;name(desc), ...}. The method gets a marker for each row, listing them, which pre-flight reads
	 * ({@link #restoredIn}).
	 */
	static List<String> restore(ClassNode node) {
		List<PutBack> rows = applicable(node);
		List<String> restored = new ArrayList<>();
		if (rows.isEmpty()) return restored;
		// The check's helpers, made once for the class.
		List<MethodNode> helpers = null;
		if (rows.stream().anyMatch(VanillaCalls::checks)) {
			try {
				helpers = keepsHelpers(node.name);
			} catch (RuntimeException e) {
				return restored; // the check can't be copied: nothing is put back, in pre-flight's view as at load
			}
		}
		boolean srg = VanillaSeams.srg(node);
		// Frames are worked out on a copy of the methods put back in with them expanded, the class's others left out (a large
		// class's, BlockBehaviour's, would cost far more than its one method); the rewritten methods are read back with them
		// compressed again.
		ClassNode holders = new ClassNode();
		holders.version = node.version;
		holders.access = node.access;
		holders.name = node.name;
		holders.superName = node.superName;
		holders.interfaces = new ArrayList<>(node.interfaces);
		for (PutBack row : rows) {
			MethodNode method = holder(node, row, srg);
			if (!holders.methods.contains(method)) holders.methods.add(method);
		}
		ClassNode expanded = read(write(holders), ClassReader.EXPAND_FRAMES);
		List<String> rewritten = new ArrayList<>();
		for (PutBack row : rows) {
			MethodNode method = holder(expanded, row, srg);
			Rewritten done = rewrite(expanded, method, row, srg, node);
			if (done == null) continue;
			addHelpers(expanded, row, srg, row.code() == Code.LAYER ? layerBase(node, row) : null, helpers);
			AnnotationNode marker = new AnnotationNode(VanillaSeams.MARKER);
			marker.values = new ArrayList<>(List.of("calls", done.calls(), "keeps", keeps(row, node), "places", done.places(), "platform", row.platform().key(false),
					"finding", row.finding(), "through", through(row, node), "note", note(row)));
			if (method.invisibleAnnotations == null) method.invisibleAnnotations = new ArrayList<>();
			method.invisibleAnnotations.add(marker);
			if (!rewritten.contains(method.name + method.desc)) rewritten.add(method.name + method.desc);
			restored.add(method.name + method.desc + ": " + String.join(", ", done.calls()));
		}
		if (rewritten.isEmpty()) return restored;
		byte[] written = write(expanded);
		// The helpers name only the JDK's classes and the class's own: the game can't load KeepsDefault.
		if (new String(written, StandardCharsets.ISO_8859_1).contains(KEEPS_OWNER)) throw new IllegalStateException("a copy of " + KEEPS_OWNER + " names it");
		ClassNode compressed = read(written, 0);
		for (MethodNode method : compressed.methods) {
			int at = indexOf(node, method.name, method.desc);
			if (at < 0) node.methods.add(method);
			else if (rewritten.contains(method.name + method.desc)) node.methods.set(at, method);
		}
		for (FieldNode field : compressed.fields) {
			if (field.name.startsWith("fabricated$") && node.fields.stream().noneMatch(f -> f.name.equals(field.name))) node.fields.add(field);
		}
		return restored;
	}

	/** Whether the row's put-back asks of an object's class (the block's, the item's, the player's, the layer's) first. */
	private static boolean checks(PutBack row) {
		return row.extension() != null || row.code().keepingPlayer() >= 0 || row.code() == Code.LAYER || row.code() == Code.LEVEL || row.code() == Code.EYES;
	}

	/**
	 * What a row's put-back in the class stands for, as its marker names it: the extension's default, the elytra layer's
	 * method, or the platform's hook.
	 */
	private static String keeps(PutBack row, ClassNode node) {
		if (row.extension() != null) return row.extension().blockInterface() + "." + row.extension().name();
		if (row.code() == Code.LEVEL) return LEVEL_READER_CLASS + "." + IS_EMPTY_BLOCK.name();
		if (row.code() == Code.EYES) return ENTITY_CLASS + ".isEyeInFluid";
		return (row.code() == Code.LAYER ? layerBase(node, row) : row.platform().owner()) + "." + row.platform().name();
	}

	/** How the platform makes vanilla's call, as pre-flight's note says it: {@code the block's IBlockExtension.onDestroyedByPlayer}. */
	private static String through(PutBack row, ClassNode node) {
		// SEAM-8's call is the same on both platforms, whose extensions a class may not name: the call as it is made.
		if (row.code().receiver()) return row.form().call;
		String keeps = keeps(row, node);
		if (row.code() == Code.EYES) return "the entity's fluid interaction's isEyeInFluidMatching(entity, predicate) of the fluid types at its eyes";
		String whose = row.extension() != null ? "the " + row.extension().holder().noun() + "'s " : row.code() == Code.LAYER ? "the layer's "
				: row.code() == Code.LEVEL ? "the level's " : "";
		return whose + keeps.substring(keeps.lastIndexOf('/') + 1);
	}

	/** What putting the call back does, as pre-flight's note of a hook of it says. */
	private static String note(PutBack row) {
		String loader = row.loader();
		return switch (row.code()) {
			case SERVER, FORGE_SERVER, CLIENT, FORGE_CLIENT -> "Fabricated puts it back there for the blocks keeping that method's default, so the hook applies to"
					+ " their breaking, and a block overriding it breaks as " + loader + " has it, without the hook";
			case HARVEST -> "Fabricated puts it back there for the blocks keeping that method's default, NeoForge's harvest check event following it as that"
					+ " default posts it, so the hook applies to their harvest check, and a block overriding it is checked as NeoForge has it, without the hook";
			case FORGE_HARVEST -> "Fabricated puts it back there for the blocks keeping that method's default broken by a player whose class keeps"
					+ " Player.hasCorrectToolForDrops, which fires Forge's harvest check event, so the hook applies to their harvest check; other breaks are"
					+ " checked as Forge has it, without the hook";
			case PROGRESS -> "Fabricated puts it back there, NeoForge's harvest check event following it as that method posts it, so the hook applies";
			case FORGE_PROGRESS -> "Fabricated puts it back there for a player whose class keeps Player.hasCorrectToolForDrops, which fires Forge's harvest"
					+ " check event, so the hook applies to their mining; other players' is checked as Forge has it, without the hook";
			case ACTION, FORGE_ACTION -> "Fabricated puts it back there for the items keeping that method's default, so the hook applies to their check, and"
					+ " an item overriding it (vanilla's " + row.check().noun() + " among them) is checked as " + loader + " has it, without the hook";
			case LAYER -> "Fabricated puts it back there for the layers keeping that method, so the hook applies to them, and a layer overriding it draws as"
					+ " the platform has it, without the hook";
			case RECEIVER -> onReceiverNote(row);
			case LEVEL -> "Fabricated puts it back there for the levels keeping that method, so the hook applies to them, and a level overriding it is asked"
					+ " as Forge has it, without the hook";
			case EYES -> "Fabricated puts it back there for the entities whose eyes are in vanilla's water, lava or no fluid, where it answers what"
					+ " NeoForge's check of those fluid types does, so the hook applies to them; an entity whose eyes are in another fluid, or that"
					+ " overrides Entity.isEyeInFluid, is asked as NeoForge has it, without the hook";
		};
	}

	/** {@link #note} of an extension's call of its object (SEAM-8). */
	private static String onReceiverNote(PutBack row) {
		String nouns = row.form().nouns, noun = row.form().noun;
		String event = row.form() == OnReceiver.ENCHANTMENT ? ", NeoForge's enchantment level event following it as that default posts it"
				: row.form().action != null ? ", after " + row.loader() + "'s tool modification event and the held item's check of the action, as that"
						+ " default makes them" : "";
		String also = row.extension().stateKeeps() != null ? ", as is one whose state's class declares vanilla's getSoundType again"
				: row.form() == OnReceiver.GOLD_HOLDER ? ", as is armour whose material the platform's check, by identity, and vanilla's Holder.is answer"
						+ " differently for (gold's under another holder)"
				: row.form() == OnReceiver.FARMLAND ? ", as is a farm block that isn't vanilla's farmland, which NeoForge counts as farmland"
				: row.form() == OnReceiver.FORGE_FARMLAND ? ", as is every soil of a crop that isn't a CropBlock (a stem, the pitcher crop) or overrides one of"
						+ " the checks Forge asks it (mayPlaceOn, getPlant, getPlantType), and, where Forge's crops take any farm block, one that isn't"
						+ " vanilla's farmland"
				: row.form() == OnReceiver.GROWS_CROPS ? ", as is a farmland block not tagged #minecraft:grows_crops, which Forge counts as farmland, and"
						+ " every soil of a crop that isn't a CropBlock (a stem, the pitcher crop) or overrides one of the checks Forge asks it (mayPlaceOn,"
						+ " getPlant, getPlantType)"
				: row.form() == OnReceiver.MOISTURE || row.form() == OnReceiver.MOISTURE_OR_ELSE ? ", as is every soil but vanilla's farmland" : "";
		return "Fabricated puts it back there for the " + nouns + " keeping that method's default" + event + ", so the hook applies to them, and "
				+ (noun.equals("item") ? "an " : "a ") + noun + " overriding it is asked as the platform has it, without the hook" + also;
	}

	/** What {@link #rewrite} put back: the calls, and in how many places. */
	private record Rewritten(List<String> calls, int places) {
	}

	/**
	 * Rewrites each place of the method that makes the platform's call; the calls put back, or null where a place isn't in
	 * the shape the row reads (the method is then left as it was).
	 */
	private static Rewritten rewrite(ClassNode owner, MethodNode method, PutBack row, boolean srg, ClassNode whole) {
		List<MethodInsnNode> places = calls(method, row);
		Map<AbstractInsnNode, State> frames = frames(owner, method);
		if (frames == null) return null;
		if (row.code().action()) return actions(owner, method, row, srg, places, frames);
		if (row.code().receiver()) return receivers(owner, method, row, srg, places, frames);
		if (row.code() == Code.LEVEL) return levels(owner, method, srg, places, frames);
		if (row.code() == Code.EYES) return eyes(owner, whole, method, places, frames);
		List<Site> sites = new ArrayList<>();
		for (MethodInsnNode place : places) {
			Site site = site(method, place, frames);
			if (site == null || !site.plain(needed(row)) || !site.receives(row.code())) return null;
			sites.add(site);
		}
		int temp = method.maxLocals;
		Set<String> putBack = new LinkedHashSet<>();
		for (Site site : sites) {
			Emitter out = new Emitter(owner, row, srg, site, temp);
			if (row.code() == Code.PROGRESS) {
				// No branch: vanilla's call goes in front of the hook's operands, and a helper posting the event takes the hook's place.
				out.harvestCall(putBack);
				method.instructions.insertBefore(site.start(), out.code);
				method.instructions.set(site.call(), new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, HARVEST_EVENT, HARVEST_EVENT_DESC, false));
				continue;
			}
			LabelNode platform = new LabelNode(), end = new LabelNode();
			out.vanilla(platform, end, putBack);
			method.instructions.insertBefore(site.start(), out.code);
			method.instructions.insertBefore(site.start(), platform);
			method.instructions.insertBefore(site.start(), frame(site.locals(), row.code() == Code.FORGE_SERVER ? temp : -1, site.under(), false));
			method.instructions.insert(site.call(), frame(site.locals(), -1, site.under(), true));
			method.instructions.insert(site.call(), end);
		}
		if (row.code() == Code.FORGE_SERVER) method.maxLocals = temp + 1;
		method.maxStack = Math.max(method.maxStack, sites.stream().mapToInt(s -> slots(s.under())).max().orElse(0) + 7);
		return new Rewritten(List.copyOf(putBack), sites.size());
	}

	/**
	 * The item checks' places (SEAM-6), each the stack the platform's code pushes, then the action's push and the
	 * platform's call: the stack is kept and its classes asked whether they keep the platform's default, then it gets
	 * vanilla's check, {@code stack.is(item)}, or the action and the platform's call as before. The stack's code runs once,
	 * as it did, and the method gets no local. Null where a place isn't so (a jump or a handler reaching between the stack
	 * and the call, a frame after it, a type a frame can't name).
	 */
	private static Rewritten actions(ClassNode owner, MethodNode method, PutBack row, boolean srg, List<MethodInsnNode> places, Map<AbstractInsnNode, State> frames) {
		Set<LabelNode> targets = targets(method);
		List<State> befores = new ArrayList<>();
		for (MethodInsnNode call : places) {
			AbstractInsnNode action = previousReal(call), stack = previousReal(action);
			if (stack == null) return null;
			for (AbstractInsnNode insn = stack.getNext(); insn != call; insn = insn.getNext()) {
				if (insn instanceof FrameNode || insn instanceof LabelNode label && targets.contains(label)) return null;
			}
			for (AbstractInsnNode next = call.getNext(); next != null && next.getOpcode() < 0; next = next.getNext()) {
				if (next instanceof FrameNode) return null;
			}
			State before = frames.get(action);
			if (before == null || before.stack().isEmpty() || !STACK_CLASS.equals(before.stack().get(before.stack().size() - 1))
					|| Stream.concat(before.stack().stream(), before.locals().stream()).anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
			befores.add(before);
		}
		Set<String> putBack = new LinkedHashSet<>();
		for (int i = 0; i < places.size(); i++) {
			MethodInsnNode call = places.get(i);
			State before = befores.get(i);
			LabelNode platform = new LabelNode(), end = new LabelNode();
			InsnList code = new InsnList();
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, row.extension().entry(), row.extension().entryDesc(), false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			itemCheck(code, owner.version, row, srg, putBack);
			code.add(new JumpInsnNode(Opcodes.GOTO, end));
			code.add(platform);
			code.add(frame(before.locals(), -1, before.stack(), false));
			method.instructions.insertBefore(previousReal(call), code);
			method.instructions.insert(call, frame(before.locals(), -1, before.stack().subList(0, before.stack().size() - 1), true));
			method.instructions.insert(call, end);
		}
		method.maxStack = Math.max(method.maxStack, befores.stream().mapToInt(b -> slots(b.stack())).max().orElse(0) + 1);
		return new Rewritten(List.copyOf(putBack), places.size());
	}

	/**
	 * Vanilla's check of the item check's item, of the stack on the stack: {@code is(Item)}, or in a class of Java 25's
	 * (Minecraft 26.1 on) {@code is(Object)}, {@code TypedInstance}'s {@code is(T)}.
	 */
	private static void itemCheck(InsnList code, int version, PutBack row, boolean srg, Set<String> putBack) {
		Member item = row.check().item();
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, item.owner(), item.name(srg), item.desc(srg)));
		Member is = java25(version) ? IS_OBJECT : IS_ITEM;
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, is.owner(), is.name(srg), is.desc(srg), false));
		putBack.add(is.key(srg));
	}

	/**
	 * A place of an extension's call of its object (SEAM-8): where the object's code ends, the stack and locals there, and
	 * a store of the call's result after it, with the state after that and, where a frame follows the store, its label
	 * ({@code join}: a jump's target, both branches meeting there).
	 */
	private record ObjectPlace(Site site, AbstractInsnNode split, State mid, VarInsnNode store, State after, LabelNode join, Dousing dousing) {
	}

	/**
	 * NeoForge's dousing's place (SEAM-13): the platform's call's result stored and asked whether it is null ({@code dup},
	 * {@code astore}, {@code ifnull}), the label that check skips to, past the level event it guards, and the method's level
	 * and position, each its one store of the context's {@code getLevel()} and {@code getClickedPos()}, which vanilla's
	 * {@code CampfireBlock.dowse} call takes.
	 */
	private record Dousing(LabelNode skip, int level, int pos) {
	}

	/**
	 * The extensions' calls of a block state or a stack (SEAM-8), each the object the platform's code pushes, then the
	 * call's other operands and the call: the object is kept and its classes asked whether they keep the platform's default
	 * (NeoForge 26.x's enchantment level, of an {@code ItemInstance}, first whether it is a stack; 1.21.1's piglin check
	 * then whether the platform's check of the material and vanilla's agree), then it gets vanilla's code
	 * ({@link OnReceiver}), or the other operands and the platform's call as before. The object's code runs once, as it did,
	 * and the other operands only for the platform's call, as vanilla's code makes none of them. Where the result is stored
	 * just after the call, vanilla's branch stores it there too, so a point after vanilla's call (an {@code INVOKE_ASSIGN}'s,
	 * Porting Lib's friction hooks) finds the value stored, each branch storing it once (a hook of the store or of a load of
	 * the local runs once a call), and the branches meet after the platform's store, where the local it starts is in scope
	 * (PRE-33); where a jump's target follows the store (AbstractHorse's second step sound), vanilla's branch goes there,
	 * its frame already the branches'. A single store both branches reach can't be the instruction just after vanilla's
	 * call, as Mixin's {@code INVOKE_ASSIGN} needs: their meeting's label comes before it. Null where a place isn't so
	 * ({@link #site}; an operand vanilla's code loads again that isn't plain).
	 */
	private static Rewritten receivers(ClassNode owner, MethodNode method, PutBack row, boolean srg, List<MethodInsnNode> places,
			Map<AbstractInsnNode, State> frames) {
		Type result = Type.getReturnType(row.platform().desc());
		Set<LabelNode> targets = targets(method);
		List<ObjectPlace> found = new ArrayList<>();
		for (MethodInsnNode call : places) {
			Site site = site(method, call, frames);
			if (site == null || site.operands().size() < 2 || !site.plain(row.form().reloads())) return null;
			AbstractInsnNode split = site.operands().get(1).get(0);
			State mid = frames.get(split);
			if (mid == null || mid.stack().size() != site.under().size() + 1) return null;
			AbstractInsnNode next = call.getNext();
			while (next != null && next.getOpcode() < 0) {
				if (next instanceof FrameNode || next instanceof LabelNode label && targets.contains(label)) break;
				next = next.getNext();
			}
			VarInsnNode store = next instanceof VarInsnNode var && var.getOpcode() == result.getOpcode(Opcodes.ISTORE) ? var : null;
			State after = null;
			LabelNode join = null;
			if (store != null) {
				// A frame after the store (a jump's target there, AbstractHorse's second step sound) is where the branches meet:
				// both bring the locals the platform's code brings there.
				LabelNode label = null;
				for (AbstractInsnNode insn = store.getNext(); insn != null && insn.getOpcode() < 0; insn = insn.getNext()) {
					if (insn instanceof LabelNode l) label = l;
					if (insn instanceof FrameNode) {
						if (label == null) return null;
						join = label;
						break;
					}
				}
				after = frames.get(store.getNext());
				if (after == null || after.stack().size() != site.under().size()
						|| after.locals().stream().anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
			}
			Dousing dousing = null;
			if (row.form() == OnReceiver.DOUSE && (store != null || (dousing = dousing(method, call, site)) == null)) return null;
			// Forge's farmland check asks of the crop too: its operand a local's load, cast to IPlantable, which the condition loads again.
			if (row.form().takesCrop() && cropSlot(site) < 0) return null;
			found.add(new ObjectPlace(site, split, mid, store, after, join, dousing));
		}
		Set<String> putBack = new LinkedHashSet<>();
		Extension extension = row.extension();
		boolean instance = row.platform().owner().equals(ITEM_INSTANCE);
		for (ObjectPlace place : found) {
			Site site = place.site();
			LabelNode platform = new LabelNode(), end = place.join() != null ? place.join() : new LabelNode();
			InsnList code = new InsnList();
			code.add(new InsnNode(Opcodes.DUP));
			if (instance) {
				code.add(new TypeInsnNode(Opcodes.INSTANCEOF, STACK_CLASS));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
				code.add(new InsnNode(Opcodes.DUP));
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, STACK_CLASS));
			}
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, extension.entry(), extension.entryDesc(), false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			if (row.form() == OnReceiver.GOLD_HOLDER) {
				code.add(new InsnNode(Opcodes.DUP));
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, GOLD_AGREES, "(" + ITEM + ")Z", false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			}
			if (row.form().soil()) {
				// Whether vanilla's check answers the platform's for this soil (and on Forge this crop), SEAM-12.
				code.add(new InsnNode(Opcodes.DUP));
				if (row.form().takesCrop()) code.add(new VarInsnNode(Opcodes.ALOAD, cropSlot(site)));
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, soilHelper(row.form()), soilHelperDesc(row.form()), false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			}
			vanillaOn(code, owner.name, row, srg, site, place.mid().locals(), site.under(), place.dousing(), putBack);
			if (place.store() != null) {
				code.add(new VarInsnNode(place.store().getOpcode(), place.store().var));
				// The local the store starts, named from vanilla's store on too.
				LabelNode stored = new LabelNode();
				code.add(stored);
				for (LocalVariableNode local : startedBy(method, place.store())) {
					method.localVariables.add(new LocalVariableNode(local.name, local.desc, local.signature, stored, platform, local.index));
				}
			}
			code.add(new JumpInsnNode(Opcodes.GOTO, end));
			code.add(platform);
			code.add(frameOf(place.mid().locals(), place.mid().stack()));
			method.instructions.insertBefore(place.split(), code);
			if (place.join() != null) continue;
			// Each branch stores once: the branches meet after the platform's store, past the labels that follow it, so the
			// frame there is where the local the store starts is in scope, which Mixin looks it up at (PRE-33).
			AbstractInsnNode last = place.store() != null ? place.store() : site.call();
			while (place.store() != null && last.getNext() instanceof LabelNode || place.store() != null && last.getNext() instanceof LineNumberNode) {
				last = last.getNext();
			}
			List<Object> after = new ArrayList<>(site.under());
			if (place.store() == null) after.add(frameType(result));
			method.instructions.insert(last, frameOf(place.store() != null ? place.after().locals() : place.mid().locals(), after));
			method.instructions.insert(last, end);
		}
		method.maxStack = Math.max(method.maxStack, found.stream().mapToInt(p -> slots(p.mid().stack())).max().orElse(0) + 8);
		return new Rewritten(List.copyOf(putBack), found.size());
	}

	/**
	 * The entries of the method's local variable table a store starts: of its slot, from a label between it and the next
	 * instruction. Mixin finds the local a point after vanilla's store modifies by the table there (SpongePowered's Mixin
	 * on Forge, which keeps locals once their scope has ended, too: PRE-26, PRE-33), so vanilla's store starts the same local.
	 */
	private static List<LocalVariableNode> startedBy(MethodNode method, VarInsnNode store) {
		if (method.localVariables == null) {
			method.localVariables = new ArrayList<>();
			return List.of();
		}
		Set<LabelNode> labels = new HashSet<>();
		for (AbstractInsnNode insn = store.getNext(); insn != null && insn.getOpcode() < 0; insn = insn.getNext()) {
			if (insn instanceof LabelNode label) labels.add(label);
		}
		return method.localVariables.stream().filter(local -> local.index == store.var && labels.contains(local.start)).toList();
	}

	/**
	 * Vanilla's code on the object on the stack ({@link OnReceiver}), leaving what the platform's call returned: its calls
	 * made as vanilla makes them, where hooks find them, what vanilla doesn't call there (the stack's item, NeoForge's level
	 * event) through the helpers.
	 */
	private static void vanillaOn(InsnList code, String owner, PutBack row, boolean srg, Site site, List<Object> locals, List<Object> under, Dousing dousing,
			Set<String> putBack) {
		switch (row.form()) {
			case FLATTEN, DOUSE -> {
				// The platform's event and the held item's action, as the default asks them (the helper): their answer where it
				// isn't vanilla's code's to give (the event's state, or none for an item without the action), else the state
				// itself, which vanilla's code is made of.
				LabelNode done = new LabelNode();
				code.add(new InsnNode(Opcodes.DUP));
				for (int operand = 1; operand <= 3; operand++) load(code, site, operand);
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, TOOL_USE, toolUseDesc(row), false));
				code.add(new InsnNode(Opcodes.DUP_X1));
				code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, done));
				if (row.form() == OnReceiver.FLATTEN) {
					code.add(new FieldInsnNode(Opcodes.GETSTATIC, FLATTENABLES.owner(), FLATTENABLES.name(srg), FLATTENABLES.desc()));
					code.add(new InsnNode(Opcodes.SWAP));
					invoke(code, GET_BLOCK, srg, false, putBack);
					invoke(code, MAP_GET, srg, true, putBack);
					code.add(new TypeInsnNode(Opcodes.CHECKCAST, STATE));
				} else {
					douse(code, site, dousing, locals, under, done, putBack);
				}
				List<Object> answered = new ArrayList<>(under);
				answered.add(STATE);
				code.add(done);
				code.add(frameOf(locals, answered));
			}
			case FARMLAND, FORGE_FARMLAND -> {
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, FARMLAND.owner(), FARMLAND.name(srg), FARMLAND.desc()));
				invoke(code, IS_BLOCK, srg, false, putBack);
				// NeoForge reads its TriState's answer as it is where it isn't DEFAULT: TRUE or FALSE, of vanilla's answer.
				if (row.form() == OnReceiver.FARMLAND) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, TRI_STATE_OF, triStateDesc(row), false));
			}
			case GROWS_CROPS -> {
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, GROWS_CROPS.owner(), GROWS_CROPS.name(), GROWS_CROPS.desc()));
				invoke(code, IS_BLOCK_TAG, srg, false, putBack);
			}
			case MOISTURE, MOISTURE_OR_ELSE -> {
				// The moisture as vanilla's code reads it, then its "> 0" as the platform's call's answer.
				if (row.form() == OnReceiver.MOISTURE) {
					code.add(new FieldInsnNode(Opcodes.GETSTATIC, FARM_MOISTURE.owner(), FARM_MOISTURE.name(srg), FARM_MOISTURE.desc()));
					invoke(code, STATE_VALUE, srg, false, putBack);
				} else {
					code.add(new FieldInsnNode(Opcodes.GETSTATIC, FARMLAND_MOISTURE.owner(), FARMLAND_MOISTURE.name(), FARMLAND_MOISTURE.desc()));
					code.add(new InsnNode(Opcodes.ICONST_0));
					code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGER_OF.owner(), INTEGER_OF.name(), INTEGER_OF.desc(), false));
					invoke(code, STATE_VALUE_OR_ELSE, srg, false, putBack);
				}
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
				code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, INT_VALUE.owner(), INT_VALUE.name(), INT_VALUE.desc(), false));
				LabelNode dry = new LabelNode(), done = new LabelNode();
				code.add(new JumpInsnNode(Opcodes.IFLE, dry));
				code.add(new InsnNode(Opcodes.ICONST_1));
				code.add(new JumpInsnNode(Opcodes.GOTO, done));
				code.add(dry);
				code.add(frameOf(locals, under));
				code.add(new InsnNode(Opcodes.ICONST_0));
				List<Object> answered = new ArrayList<>(under);
				answered.add(Opcodes.INTEGER);
				code.add(done);
				code.add(frameOf(locals, answered));
			}
			case FRICTION -> {
				invoke(code, GET_BLOCK, srg, false, putBack);
				invoke(code, BLOCK_FRICTION, srg, false, putBack);
			}
			case SOUND -> invoke(code, SOUND_OF_STATE, srg, false, putBack);
			case SAFE_ARMOR -> {
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, SAFE_ARMOR_TAG.owner(), SAFE_ARMOR_TAG.name(srg), SAFE_ARMOR_TAG.desc(srg)));
				invoke(code, IS_TAG, srg, false, putBack);
			}
			case SWEEP -> {
				code.add(new InsnNode(Opcodes.POP));
				load(code, site, 2);
				invoke(code, BOUNDING_BOX, srg, false, putBack);
				code.add(new InsnNode(Opcodes.DCONST_1));
				code.add(new LdcInsnNode(0.25D));
				code.add(new InsnNode(Opcodes.DCONST_1));
				invoke(code, INFLATE, srg, false, putBack);
			}
			case ENCHANTMENT -> {
				boolean instance = row.platform().owner().equals(ITEM_INSTANCE);
				code.add(new InsnNode(Opcodes.DUP));
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, ENCHANTMENTS.owner(), ENCHANTMENTS.name(), ENCHANTMENTS.desc()));
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, NO_ENCHANTMENTS.owner(), NO_ENCHANTMENTS.name(), NO_ENCHANTMENTS.desc()));
				invoke(code, instance ? INSTANCE_GET_OR_DEFAULT : GET_OR_DEFAULT, srg, instance, putBack);
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, ITEM_ENCHANTMENTS));
				load(code, site, 1);
				invoke(code, LEVEL_OF, srg, false, putBack);
				load(code, site, 1);
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, ENCHANTMENT_EVENT, enchantmentEventDesc(instance), false));
			}
			case GOLD, GOLD_HOLDER -> {
				// The stack's item, out of hooks' sight (vanilla's code read it before), then vanilla's check of it.
				LabelNode notArmor = new LabelNode(), no = new LabelNode(), done = new LabelNode();
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, ITEM_OF_STACK.helper(), ITEM_OF_STACK.helperDesc(), false));
				code.add(new InsnNode(Opcodes.DUP));
				code.add(new TypeInsnNode(Opcodes.INSTANCEOF, ARMOR_ITEM));
				code.add(new JumpInsnNode(Opcodes.IFEQ, notArmor));
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, ARMOR_ITEM));
				invoke(code, ARMOR_MATERIAL, srg, false, putBack);
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, GOLD_MATERIAL.owner(), GOLD_MATERIAL.name(srg), GOLD_MATERIAL.desc(srg)));
				if (row.form() == OnReceiver.GOLD) {
					code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, no));
					code.add(new InsnNode(Opcodes.ICONST_1));
				} else {
					invoke(code, HOLDER_IS, srg, true, putBack);
				}
				code.add(new JumpInsnNode(Opcodes.GOTO, done));
				List<Object> withItem = new ArrayList<>(under);
				withItem.add(ITEM_CLASS);
				code.add(notArmor);
				code.add(frameOf(locals, withItem));
				code.add(new InsnNode(Opcodes.POP));
				if (row.form() == OnReceiver.GOLD) {
					code.add(no);
					code.add(frameOf(locals, under));
				}
				code.add(new InsnNode(Opcodes.ICONST_0));
				List<Object> answered = new ArrayList<>(under);
				answered.add(Opcodes.INTEGER);
				code.add(done);
				code.add(frameOf(locals, answered));
			}
		}
	}

	/**
	 * Vanilla's dousing of a lit campfire, of the state on the stack (NeoForge's form, SEAM-13): where its block is a
	 * {@code CampfireBlock} and it is lit, {@code CampfireBlock.dowse(context.getPlayer(), level, pos, state)}, then
	 * {@code state.setValue(LIT, false)} goes to {@code done}, where the platform's code takes it, stores it and makes the
	 * level event; otherwise nothing is stored, as in vanilla's code, and the code goes past the platform's check to
	 * {@code dousing.skip()}. The campfire is doused before the level event, as NeoForge's default does.
	 */
	private static void douse(InsnList code, Site site, Dousing dousing, List<Object> locals, List<Object> under, LabelNode done, Set<String> putBack) {
		LabelNode no = new LabelNode();
		code.add(new InsnNode(Opcodes.DUP));
		invoke(code, GET_BLOCK, false, false, putBack);
		code.add(new TypeInsnNode(Opcodes.INSTANCEOF, CAMPFIRE));
		code.add(new JumpInsnNode(Opcodes.IFEQ, no));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, CAMPFIRE_LIT.owner(), CAMPFIRE_LIT.name(), CAMPFIRE_LIT.desc()));
		invoke(code, GET_VALUE, false, false, putBack);
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
		invoke(code, BOOLEAN_VALUE, false, false, putBack);
		code.add(new JumpInsnNode(Opcodes.IFEQ, no));
		load(code, site, 1);
		invoke(code, CONTEXT_PLAYER, false, false, putBack);
		code.add(new VarInsnNode(Opcodes.ALOAD, dousing.level()));
		code.add(new VarInsnNode(Opcodes.ALOAD, dousing.pos()));
		load(code, site, 0);
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DOWSE.owner(), DOWSE.name(), DOWSE.desc(), false));
		putBack.add(DOWSE.key(false));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, CAMPFIRE_LIT.owner(), CAMPFIRE_LIT.name(), CAMPFIRE_LIT.desc()));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BOOLEAN_OF.owner(), BOOLEAN_OF.name(), BOOLEAN_OF.desc(), false));
		putBack.add(BOOLEAN_OF.key(false));
		invoke(code, SET_VALUE, false, false, putBack);
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, STATE));
		code.add(new JumpInsnNode(Opcodes.GOTO, done));
		List<Object> withState = new ArrayList<>(under);
		withState.add(STATE);
		code.add(no);
		code.add(frameOf(locals, withState));
		code.add(new InsnNode(Opcodes.POP));
		code.add(new JumpInsnNode(Opcodes.GOTO, dousing.skip()));
	}

	/**
	 * NeoForge's dousing's place at its call ({@link Dousing}): the call's result checked as the platform's code checks it,
	 * its context the method's, whose level and position it keeps in locals stored once; null where it isn't so.
	 */
	private static Dousing dousing(MethodNode method, MethodInsnNode call, Site site) {
		AbstractInsnNode dup = nextReal(call), store = dup == null ? null : nextReal(dup), check = store == null ? null : nextReal(store);
		if (dup == null || dup.getOpcode() != Opcodes.DUP || !(store instanceof VarInsnNode stored) || stored.getOpcode() != Opcodes.ASTORE
				|| !(check instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFNULL) return null;
		List<AbstractInsnNode> context = site.operands().get(1);
		if (context.stream().filter(i -> i.getOpcode() >= 0).count() != 1 || !(context.get(context.size() - 1) instanceof VarInsnNode ctx)
				|| ctx.getOpcode() != Opcodes.ALOAD) return null;
		int level = storedOnce(method, ctx.var, CONTEXT_LEVEL), pos = storedOnce(method, ctx.var, CONTEXT_POS);
		return level < 0 || pos < 0 ? null : new Dousing(jump.label, level, pos);
	}

	/** The local the method keeps the result of its one call of the context's method in, stored nowhere else; -1 where it has none. */
	private static int storedOnce(MethodNode method, int context, Member getter) {
		int slot = -1;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(getter.owner()) || !call.name.equals(getter.name()) || !call.desc.equals(getter.desc())) continue;
			AbstractInsnNode receiver = previousReal(call), next = nextReal(call);
			if (slot >= 0 || !(receiver instanceof VarInsnNode from) || from.var != context || !(next instanceof VarInsnNode to) || to.getOpcode() != Opcodes.ASTORE) return -1;
			slot = to.var;
		}
		int stores = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof VarInsnNode var && var.var == slot && var.getOpcode() >= Opcodes.ISTORE && var.getOpcode() <= Opcodes.ASTORE) stores++;
		}
		return slot >= 0 && stores == 1 ? slot : -1;
	}

	/**
	 * Forge's empty block checks above the shovel's path (SEAM-13), each with the level and the position on the stack: the
	 * level is asked whether its class keeps {@code LevelReader}'s {@code isEmptyBlock}, then gets that default's code,
	 * vanilla's {@code level.getBlockState(pos).isAir()}, or the platform's call as before. Each operand is evaluated once,
	 * as it was, and the method gets no local. Null where a place isn't so (a frame after the call, a type a frame can't name).
	 */
	private static Rewritten levels(ClassNode owner, MethodNode method, boolean srg, List<MethodInsnNode> places, Map<AbstractInsnNode, State> frames) {
		List<State> befores = new ArrayList<>();
		for (MethodInsnNode call : places) {
			State before = frames.get(call);
			if (before == null || before.stack().size() < 2
					|| Stream.concat(before.stack().stream(), before.locals().stream()).anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
			for (AbstractInsnNode next = call.getNext(); next != null && next.getOpcode() < 0; next = next.getNext()) {
				if (next instanceof FrameNode) return null;
			}
			befores.add(before);
		}
		Set<String> putBack = new LinkedHashSet<>();
		for (int i = 0; i < places.size(); i++) {
			MethodInsnNode call = places.get(i);
			State before = befores.get(i);
			LabelNode platform = new LabelNode(), end = new LabelNode();
			InsnList code = new InsnList();
			code.add(new InsnNode(Opcodes.DUP2));
			code.add(new InsnNode(Opcodes.POP));
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, KEEPS_LEVEL, "(Ljava/lang/Object;)Z", false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			invoke(code, LEVEL_BLOCK_STATE, srg, false, putBack);
			invoke(code, IS_AIR, srg, false, putBack);
			code.add(new JumpInsnNode(Opcodes.GOTO, end));
			code.add(platform);
			code.add(frame(before.locals(), -1, before.stack(), false));
			method.instructions.insertBefore(call, code);
			method.instructions.insert(call, frame(before.locals(), -1, before.stack().subList(0, before.stack().size() - 2), true));
			method.instructions.insert(call, end);
		}
		method.maxStack = Math.max(method.maxStack, befores.stream().mapToInt(b -> slots(b.stack())).max().orElse(0) + 2);
		return new Rewritten(List.copyOf(putBack), places.size());
	}

	/**
	 * NeoForge 26.x's checks of an entity's fluid types at its eyes (SEAM-9), each {@code x.getFluidInteraction()
	 * .isEyeInFluidMatching(x, predicate)} of a local {@code x}, the predicate a lambda of the class capturing nothing whose
	 * code never reads its height (javac's {@code (e, type, _) -> ...}): first the entity and the predicate go to
	 * {@link #VANILLA_EYES}, which answers whether vanilla's call answers what that check does for the entity; then the
	 * entity gets vanilla's {@code x.isEyeInFluid(FluidTags.WATER)}, as vanilla's code makes it, or the platform's check as
	 * before. The entity is loaded again and the predicate made again (a constant: it captures nothing), the method gets no
	 * local. The lambda is read in the class as a whole ({@code whole}: {@code owner} holds the rows' methods alone). Null
	 * where a place isn't so.
	 */
	private static Rewritten eyes(ClassNode owner, ClassNode whole, MethodNode method, List<MethodInsnNode> places, Map<AbstractInsnNode, State> frames) {
		List<Site> sites = new ArrayList<>();
		List<InvokeDynamicInsnNode> predicates = new ArrayList<>();
		List<Integer> entities = new ArrayList<>();
		for (MethodInsnNode call : places) {
			Site site = site(method, call, frames);
			if (site == null || site.operands().size() != 3) return null;
			List<AbstractInsnNode> interaction = real(site.operands().get(0)), entity = real(site.operands().get(1)), predicate = real(site.operands().get(2));
			if (interaction.size() != 2 || !(interaction.get(0) instanceof VarInsnNode from) || from.getOpcode() != Opcodes.ALOAD
					|| !(interaction.get(1) instanceof MethodInsnNode getter) || !getter.name.equals(FLUID_INTERACTION_OF.name()) || !getter.desc.equals(FLUID_INTERACTION_OF.desc())
					|| entity.size() != 1 || !(entity.get(0) instanceof VarInsnNode same) || same.getOpcode() != Opcodes.ALOAD || same.var != from.var
					|| predicate.size() != 1 || !(predicate.get(0) instanceof InvokeDynamicInsnNode lambda) || !heightBlind(whole, lambda)
					|| !(site.locals().get(from.var) instanceof String type)) return null;
			sites.add(site);
			predicates.add(lambda);
			entities.add(from.var);
		}
		Set<String> putBack = new LinkedHashSet<>();
		for (int i = 0; i < sites.size(); i++) {
			Site site = sites.get(i);
			int entity = entities.get(i);
			String type = (String) site.locals().get(entity);
			LabelNode platform = new LabelNode(), end = new LabelNode();
			InsnList code = new InsnList();
			code.add(new VarInsnNode(Opcodes.ALOAD, entity));
			code.add(predicates.get(i).clone(Map.of()));
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, VANILLA_EYES, VANILLA_EYES_DESC, false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			code.add(new VarInsnNode(Opcodes.ALOAD, entity));
			code.add(new FieldInsnNode(Opcodes.GETSTATIC, WATER_TAG.owner(), WATER_TAG.name(), WATER_TAG.desc()));
			// Vanilla's call, of the entity's type as vanilla's code names it (Player's, where Artifacts' and FrozenLib's hooks look).
			Member isEyeInFluid = new Member(type, "isEyeInFluid", null, "(" + TAG_KEY + ")Z");
			invoke(code, isEyeInFluid, false, false, putBack);
			code.add(new JumpInsnNode(Opcodes.GOTO, end));
			code.add(platform);
			code.add(frame(site.locals(), -1, site.under(), false));
			method.instructions.insertBefore(site.start(), code);
			method.instructions.insert(site.call(), frame(site.locals(), -1, site.under(), true));
			method.instructions.insert(site.call(), end);
		}
		method.maxStack = Math.max(method.maxStack, sites.stream().mapToInt(s -> slots(s.under())).max().orElse(0) + 3);
		return new Rewritten(List.copyOf(putBack), sites.size());
	}

	/** The operand's real instructions. */
	private static List<AbstractInsnNode> real(List<AbstractInsnNode> operand) {
		return operand.stream().filter(insn -> insn.getOpcode() >= 0).toList();
	}

	/**
	 * Whether an {@code invokedynamic} makes an {@code InFluidPredicate} of a static method of the class, capturing nothing,
	 * whose code never reads its third parameter, the height: what it answers for an entity and a fluid type is the same
	 * whatever height it is given ({@link #vanillaEyes} asks it with none).
	 */
	private static boolean heightBlind(ClassNode owner, InvokeDynamicInsnNode lambda) {
		if (!lambda.desc.equals("()L" + IN_FLUID_PREDICATE + ";") || !"java/lang/invoke/LambdaMetafactory".equals(lambda.bsm.getOwner())
				|| !"metafactory".equals(lambda.bsm.getName()) || lambda.bsmArgs.length < 3 || !(lambda.bsmArgs[1] instanceof org.objectweb.asm.Handle impl)
				|| impl.getTag() != Opcodes.H_INVOKESTATIC || !impl.getOwner().equals(owner.name)) return false;
		int at = indexOf(owner, impl.getName(), impl.getDesc());
		Type[] params = Type.getArgumentTypes(impl.getDesc());
		if (at < 0 || params.length != 3 || params[2].getSort() != Type.DOUBLE || (owner.methods.get(at).access & Opcodes.ACC_STATIC) == 0) return false;
		int height = params[0].getSize() + params[1].getSize();
		for (AbstractInsnNode insn : owner.methods.get(at).instructions) {
			if (insn instanceof VarInsnNode var && (var.var == height || var.var == height + 1)) return false;
			if (insn instanceof org.objectweb.asm.tree.IincInsnNode inc && (inc.var == height || inc.var == height + 1)) return false;
		}
		return true;
	}

	/**
	 * {@code private static boolean fabricated$vanillaEyes(Entity entity, InFluidPredicate predicate)}: whether vanilla's
	 * {@code entity.isEyeInFluid(FluidTags.WATER)} answers what NeoForge's {@code isEyeInFluidMatching(entity, predicate)}
	 * does (SEAM-9), where the entity's class keeps Entity's {@code isEyeInFluid} (both, the tag's and the fluid type's).
	 * NeoForge's check answers yes where a fluid type the eyes are in passes the predicate; vanilla's call, as NeoForge's
	 * fluid interaction answers it ({@code isEyeInFluid(FluidTags.WATER)}), whether they are in water. Each of water's and
	 * lava's tracker is looked up, as vanilla's call makes, and the predicate asked of the types the eyes are in (without a
	 * height, which it doesn't read). Where water or lava at the eyes passes it, NeoForge's check answers yes whatever else
	 * the eyes are in (it answers whether any type they are in passes), so where vanilla's call answers yes too the two
	 * agree, a mod's fluid at the eyes or not, and vanilla's call goes back without asking more; where vanilla's answers no
	 * there (26.3: a mod's fluid of water's type, untagged), NeoForge's check stays. Where neither passes, NeoForge's check
	 * answers no unless another fluid type the eyes are in passes: where vanilla's call answers yes (gills in water),
	 * NeoForge's check stays at once; where it answers no, the eyes are asked for another fluid type
	 * ({@code fabricated$otherFluid}: one pass over the fluids at the eyes, as NeoForge's check makes), which keeps
	 * NeoForge's check, and vanilla's call goes back without one. So no path makes more passes over NeoForge's tracker map
	 * than NeoForge's check alone, and the eyes in water none, but where neither water nor lava passes and a mod's fluid is
	 * at the eyes, which makes this one and NeoForge's. Made of constants and the entity's own state: the predicates capture
	 * nothing, and it makes nothing such a pass doesn't.
	 */
	private static MethodNode vanillaEyes(String owner) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, VANILLA_EYES, VANILLA_EYES_DESC, null, null);
		InsnList code = method.instructions;
		String fi = FLUID_INTERACTION, fluidType = FLUID_TYPE;
		LabelNode no = new LabelNode(), notInWater = new LabelNode(), noneOfThem = new LabelNode(), oneOfThem = new LabelNode(), platform = new LabelNode();
		Object[] two = {ENTITY_CLASS, IN_FLUID_PREDICATE};
		Object[] all = {ENTITY_CLASS, IN_FLUID_PREDICATE, fi, fluidType, fluidType, Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER};
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, KEEPS_EYES, "(Ljava/lang/Object;)Z", false));
		code.add(new JumpInsnNode(Opcodes.IFEQ, no));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_CLASS, FLUID_INTERACTION_OF.name(), FLUID_INTERACTION_OF.desc(), false));
		code.add(new VarInsnNode(Opcodes.ASTORE, 2));
		vanillaType(code, "WATER_TYPE");
		code.add(new VarInsnNode(Opcodes.ASTORE, 3));
		vanillaType(code, "LAVA_TYPE");
		code.add(new VarInsnNode(Opcodes.ASTORE, 4));
		for (int slot : new int[] {3, 4}) {
			code.add(new VarInsnNode(Opcodes.ALOAD, 2));
			code.add(new VarInsnNode(Opcodes.ALOAD, slot));
			code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fi, "isEyeInFluid", "(L" + fluidType + ";)Z", false));
			code.add(new VarInsnNode(Opcodes.ISTORE, slot + 2));
		}
		// Vanilla's call's answer, as the fluid interaction gives it.
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, WATER_TAG.owner(), WATER_TAG.name(), WATER_TAG.desc()));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fi, "isEyeInFluid", "(" + TAG_KEY + ")Z", false));
		code.add(new VarInsnNode(Opcodes.ISTORE, 7));
		// Water or lava at the eyes passing the predicate: NeoForge's check answers yes.
		code.add(new VarInsnNode(Opcodes.ILOAD, 5));
		code.add(new JumpInsnNode(Opcodes.IFEQ, notInWater));
		predicateOf(code, 3);
		code.add(new JumpInsnNode(Opcodes.IFNE, oneOfThem));
		code.add(notInWater);
		code.add(new FrameNode(Opcodes.F_NEW, all.length, all, 0, new Object[0]));
		code.add(new VarInsnNode(Opcodes.ILOAD, 6));
		code.add(new JumpInsnNode(Opcodes.IFEQ, noneOfThem));
		predicateOf(code, 4);
		code.add(new JumpInsnNode(Opcodes.IFNE, oneOfThem));
		// Neither: they agree where vanilla's call answers no and the eyes are in no other fluid type.
		code.add(noneOfThem);
		code.add(new FrameNode(Opcodes.F_NEW, all.length, all, 0, new Object[0]));
		code.add(new VarInsnNode(Opcodes.ILOAD, 7));
		code.add(new JumpInsnNode(Opcodes.IFNE, platform));
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new InvokeDynamicInsnNode("test", "()L" + IN_FLUID_PREDICATE + ";", new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC,
				"java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				Type.getMethodType(PREDICATE_DESC), new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, owner, OTHER_FLUID, PREDICATE_DESC, false),
				Type.getMethodType(PREDICATE_DESC)));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fi, EYES_MATCHING.name(), EYES_MATCHING.desc(), false));
		code.add(new JumpInsnNode(Opcodes.IFNE, platform));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IRETURN));
		// One of them: NeoForge's check answers yes whatever else is at the eyes; they agree where vanilla's call answers yes.
		code.add(oneOfThem);
		code.add(new FrameNode(Opcodes.F_NEW, all.length, all, 0, new Object[0]));
		code.add(new VarInsnNode(Opcodes.ILOAD, 7));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(platform);
		code.add(new FrameNode(Opcodes.F_NEW, all.length, all, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(no);
		code.add(new FrameNode(Opcodes.F_NEW, two.length, two, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 6;
		method.maxLocals = 8;
		return method;
	}

	/** Pushes NeoForge's fluid type of the field ({@code NeoForgeMod.WATER_TYPE.value()}). */
	private static void vanillaType(InsnList code, String field) {
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/neoforged/neoforge/common/NeoForgeMod", field, "L" + HOLDER + ";"));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, HOLDER, "value", "()Ljava/lang/Object;", true));
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, FLUID_TYPE));
	}

	/** Pushes the predicate's answer for the entity and the fluid type in {@code slot}, with no height. */
	private static void predicateOf(InsnList code, int slot) {
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, slot));
		code.add(new InsnNode(Opcodes.DCONST_0));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, IN_FLUID_PREDICATE, "test", PREDICATE_DESC, true));
	}

	/** {@code private static boolean fabricated$otherFluid(Entity entity, FluidType type, double height)}: whether the type is neither vanilla's water's nor lava's. */
	private static MethodNode otherFluid() {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, OTHER_FLUID, PREDICATE_DESC, null, null);
		InsnList code = method.instructions;
		LabelNode vanilla = new LabelNode();
		for (String field : List.of("WATER_TYPE", "LAVA_TYPE")) {
			code.add(new VarInsnNode(Opcodes.ALOAD, 1));
			vanillaType(code, field);
			code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, vanilla));
		}
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(vanilla);
		code.add(new FrameNode(Opcodes.F_NEW, 3, new Object[] {ENTITY_CLASS, FLUID_TYPE, Opcodes.DOUBLE}, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 2;
		method.maxLocals = 4;
		return method;
	}

	/**
	 * {@code private static boolean fabricated$keepsEyesDefault(Object entity)}: {@link KeepsDefault#keepsMethods} of the
	 * entity for Entity's {@code isEyeInFluid} of a tag and of a fluid type, which vanilla's call put back makes; one answer
	 * for the class, kept as {@link #keepsDefault}'s are.
	 */
	private static MethodNode keepsEyes(String owner) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_EYES, "(Ljava/lang/Object;)Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, KNOWN_EYES, "java/lang/Object");
		askedFirst(code, owner, "java/lang/Object", parameter(), ASKED_ONE);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		List<String> params = List.of(TAG_KEY, "L" + FLUID_TYPE + ";");
		code.add(new IntInsnNode(Opcodes.BIPUSH, params.size()));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
		for (int i = 0; i < params.size(); i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			code.add(new LdcInsnNode(Type.getObjectType(ENTITY_CLASS)));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new IntInsnNode(Opcodes.BIPUSH, params.size()));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
		for (int i = 0; i < params.size(); i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			code.add(new LdcInsnNode("isEyeInFluid"));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new IntInsnNode(Opcodes.BIPUSH, params.size()));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "[Ljava/lang/Class;"));
		for (int i = 0; i < params.size(); i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			classes(code, new Type[] {Type.getType(params.get(i))});
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsMethods", KEEPS_METHODS, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 12;
		method.maxLocals = 2;
		return method;
	}

	/**
	 * The slot of the local Forge's farmland check's last operand, the crop, is loaded from ({@code (IPlantable) type}, the
	 * method's parameter, on every Forge version): its one instruction, or that and the cast. -1 where it isn't so.
	 */
	private static int cropSlot(Site site) {
		List<AbstractInsnNode> real = site.operands().get(site.operands().size() - 1).stream().filter(i -> i.getOpcode() >= 0).toList();
		if (real.isEmpty() || real.size() > 2 || !(real.get(0) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD) return -1;
		if (real.size() == 2 && !(real.get(1) instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals(PLANTABLE))) return -1;
		return load.var;
	}

	/** The helper asking whether a soil check goes back for the soil (SEAM-12): the moisture's {@link #VANILLA_FARMLAND}, the farmland's {@link #VANILLA_SOIL}. */
	private static String soilHelper(OnReceiver form) {
		return form == OnReceiver.MOISTURE || form == OnReceiver.MOISTURE_OR_ELSE ? VANILLA_FARMLAND : VANILLA_SOIL;
	}

	private static String soilHelperDesc(OnReceiver form) {
		return "(L" + STATE + ";" + (form.takesCrop() ? "Ljava/lang/Object;" : "") + ")Z";
	}

	private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
		do insn = insn.getNext(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	/** The real instructions of the place's operand, loaded again (a local, a constant, a field). */
	private static void load(InsnList code, Site site, int operand) {
		for (AbstractInsnNode insn : site.operands().get(operand)) {
			if (insn.getOpcode() >= 0) code.add(insn.clone(Map.of()));
		}
	}

	private static void invoke(InsnList code, Member member, boolean srg, boolean itf, Set<String> putBack) {
		code.add(new MethodInsnNode(itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, member.owner(), member.name(srg), member.desc(srg), itf));
		putBack.add(member.key(srg));
	}

	/** A frame of the locals and the stack, as {@link AnalyzerAdapter} has their types. */
	private static FrameNode frameOf(List<Object> locals, List<Object> stack) {
		List<Object> local = compact(locals), onStack = compact(stack);
		return new FrameNode(Opcodes.F_NEW, local.size(), local.toArray(), onStack.size(), onStack.toArray());
	}

	/** A frame's type for a value of the type (none of the calls returns a long or a double). */
	private static Object frameType(Type type) {
		return switch (type.getSort()) {
			case Type.FLOAT -> Opcodes.FLOAT;
			case Type.OBJECT -> type.getInternalName();
			case Type.ARRAY -> type.getDescriptor();
			default -> Opcodes.INTEGER;
		};
	}

	private static String enchantmentEventDesc(boolean instance) {
		return "(L" + (instance ? ITEM_INSTANCE : STACK_CLASS) + ";IL" + HOLDER + ";)I";
	}

	/** Whether a class file version, its minor part aside, is Java 25's or later: the game's classes from 26.1 on. */
	private static boolean java25(int version) {
		return (version & 0xFFFF) >= JAVA_25;
	}

	private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
		do insn = insn.getPrevious(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	/**
	 * The platform's call at a place, its operands' instructions, their types as the call takes them, and the frame before
	 * them.
	 */
	private record Site(MethodInsnNode call, List<List<AbstractInsnNode>> operands, List<Object> types, List<Object> locals, List<Object> under) {
		AbstractInsnNode start() {
			return operands.get(0).get(0);
		}

		/** Whether each operand vanilla's code loads again only loads a local or a constant, or reads a field. */
		boolean plain(int[] needed) {
			for (int i : needed) {
				int operand = i < 0 ? operands.size() + i : i;
				for (AbstractInsnNode insn : operands.get(operand)) {
					int op = insn.getOpcode();
					if (op >= 0 && !(op >= Opcodes.ACONST_NULL && op <= Opcodes.ALOAD) && op != Opcodes.GETFIELD && op != Opcodes.GETSTATIC) return false;
				}
			}
			return true;
		}

		/** Whether the operand vanilla's harvest check is made of is of the type vanilla makes it of ({@code ServerPlayer}, {@code Player}). */
		boolean receives(Code code) {
			if (!code.harvest()) return true;
			Member call = vanillaHarvest(code);
			return call.owner().equals(types.get(harvestPlayer(code)));
		}
	}

	/** The operands vanilla's code at the place loads again (negative from the end). */
	private static int[] needed(PutBack row) {
		return switch (row.code()) {
			case SERVER -> new int[] {0, 1, 2};
			case FORGE_SERVER -> new int[] {0, 1};
			case CLIENT -> new int[] {0, 1, 2, -1};
			case FORGE_CLIENT -> new int[] {0, 1, 2, 3, -1};
			case HARVEST -> new int[] {0, 1, 2, 3};
			case FORGE_HARVEST, PROGRESS, FORGE_PROGRESS -> harvestOperands(row.code());
			// The layer and the stack; an action's stack is kept, not loaded again (actions).
			case LAYER -> new int[] {0, 1};
			case ACTION, FORGE_ACTION -> new int[0];
			case RECEIVER -> row.form().reloads();
			// The level and the position are kept, not loaded again (levels); the entity is loaded again (eyes).
			case LEVEL, EYES -> new int[0];
		};
	}

	/** The operands vanilla's harvest check is made of, and the event's where it follows: the player and the state. */
	private static int[] harvestOperands(Code code) {
		return code == Code.FORGE_HARVEST ? new int[] {0, 3} : new int[] {0, 1};
	}

	/** Which operand of the platform's call is the player vanilla asks ({@code canHarvestBlock}'s last, the hooks' first or second). */
	private static int harvestPlayer(Code code) {
		return code == Code.HARVEST || code == Code.FORGE_HARVEST ? 3 : code == Code.PROGRESS ? 0 : 1;
	}

	/** Which operand is the state vanilla asks of. */
	private static int harvestState(Code code) {
		return code == Code.PROGRESS ? 1 : 0;
	}

	/** Vanilla's harvest check, as the method makes it: of the server's {@code ServerPlayer}, or {@code getDestroyProgress}'s {@code Player}. */
	private static Member vanillaHarvest(Code code) {
		return code == Code.HARVEST || code == Code.FORGE_HARVEST ? SERVER_HAS_CORRECT_TOOL : HAS_CORRECT_TOOL;
	}

	/**
	 * The place's operands, each the instructions that push it, found by the stack's depth before each instruction; null
	 * where the code there isn't straight (a jump, a label a jump or handler reaches, a store) or leaves something else
	 * on the stack. A static call has no receiver.
	 */
	private static Site site(MethodNode method, MethodInsnNode call, Map<AbstractInsnNode, State> frames) {
		State atCall = frames.get(call);
		if (atCall == null) return null;
		boolean receiver = call.getOpcode() != Opcodes.INVOKESTATIC;
		Type[] args = Type.getArgumentTypes(call.desc);
		int count = args.length + (receiver ? 1 : 0);
		int[] sizes = new int[count];
		if (receiver) sizes[0] = 1;
		for (int i = 0; i < args.length; i++) sizes[i + (receiver ? 1 : 0)] = args[i].getSize();
		int total = 0;
		for (int size : sizes) total += size;
		int base = atCall.stack().size() - total;
		if (base < 0) return null;
		// The operands' types as the stack has them at the call.
		List<Object> types = new ArrayList<>();
		for (int i = 0, slot = base; i < count; slot += sizes[i], i++) types.add(atCall.stack().get(slot));
		Set<LabelNode> targets = targets(method);
		List<List<AbstractInsnNode>> operands = new ArrayList<>();
		for (int i = 0; i < count; i++) operands.add(new ArrayList<>());
		int depth = atCall.stack().size();
		AbstractInsnNode insn = call.getPrevious();
		for (int operand = count - 1; operand >= 0; operand--) {
			depth -= sizes[operand];
			// The operand begins at the nearest instruction before which the stack is that deep.
			while (true) {
				if (insn == null || insn instanceof FrameNode || insn instanceof LabelNode label && targets.contains(label)) return null;
				int op = insn.getOpcode();
				if (op >= Opcodes.ISTORE && op <= Opcodes.SASTORE || op == Opcodes.IINC || insn instanceof JumpInsnNode || op >= Opcodes.IRETURN && op <= Opcodes.RETURN
						|| op == Opcodes.ATHROW || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) return null;
				State before = frames.get(insn);
				if (op >= 0 && before == null) return null;
				operands.get(operand).add(0, insn);
				if (op >= 0 && before.stack().size() < base) return null;
				if (op >= 0 && before.stack().size() == depth) break;
				insn = insn.getPrevious();
			}
			insn = insn.getPrevious();
		}
		// The frame after the call goes at the next instruction, which must have none of its own.
		for (AbstractInsnNode next = call.getNext(); next != null && next.getOpcode() < 0; next = next.getNext()) {
			if (next instanceof FrameNode) return null;
		}
		State atStart = frames.get(operands.get(0).get(0));
		List<Object> under = atStart.stack();
		if (under.stream().anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
		if (atStart.locals().stream().anyMatch(t -> !(t instanceof String) && !(t instanceof Integer))) return null;
		return new Site(call, operands, types, atStart.locals(), under);
	}

	/** Writes vanilla's branch of a place: what goes before the platform's operands. */
	private static final class Emitter {
		final InsnList code = new InsnList();
		private final String owner;
		private final PutBack row;
		private final boolean srg;
		private final Site site;
		private final int temp;
		private final int version;

		Emitter(ClassNode owner, PutBack row, boolean srg, Site site, int temp) {
			this.owner = owner.name;
			this.version = owner.version;
			this.row = row;
			this.srg = srg;
			this.site = site;
			this.temp = temp;
		}

		void vanilla(LabelNode platform, LabelNode end, Set<String> putBack) {
			Code removal = row.code();
			if (removal == Code.LAYER) {
				// The layer's class asked, then vanilla's check of the stack the layer draws for.
				load(0);
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, KEEPS_LAYER, "(Ljava/lang/Object;)Z", false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
				load(1);
				itemCheck(code, version, row, srg, putBack);
				code.add(new JumpInsnNode(Opcodes.GOTO, end));
				return;
			}
			if (removal == Code.FORGE_SERVER) {
				load(0);
				load(1);
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$stateAt", "(L" + owner + ";" + POS + ")Ljava/lang/Object;", false));
				code.add(new VarInsnNode(Opcodes.ASTORE, temp));
			}
			if (row.extension() != null) {
				state();
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, row.extension().entry(), row.extension().entryDesc(), false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			}
			if (removal.keepingPlayer() >= 0) {
				load(removal.keepingPlayer());
				code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, KEEPS_PLAYER, "(" + PLAYER + ")Z", false));
				code.add(new JumpInsnNode(Opcodes.IFEQ, platform));
			}
			if (removal.harvest()) {
				harvestCall(putBack);
			} else if (removal == Code.SERVER || removal == Code.FORGE_SERVER) {
				if (removal == Code.FORGE_SERVER) {
					load(0);
					load(1);
					state();
					code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$playerWillDestroy", "(L" + owner + ";" + POS + "L" + STATE + ";)V", false));
				}
				load(0);
				field(LEVEL_FIELD);
				load(1);
				code.add(new InsnNode(Opcodes.ICONST_0));
				call(REMOVE_BLOCK, putBack);
				code.add(new InsnNode(Opcodes.DUP));
				code.add(new JumpInsnNode(Opcodes.IFEQ, end));
				state();
				block();
				load(0);
				field(LEVEL_FIELD);
				load(1);
				state();
				call(DESTROY, putBack);
			} else {
				if (removal == Code.FORGE_CLIENT) {
					load(0);
					block();
					load(1);
					load(2);
					load(0);
					load(3);
					call(WILL_DESTROY, putBack);
					if (!WILL_DESTROY.desc(srg).endsWith(")V")) code.add(new InsnNode(Opcodes.POP));
				}
				load(1);
				load(2);
				load(site.operands().size() - 1);
				call(LEGACY_BLOCK, putBack);
				code.add(new IntInsnNode(Opcodes.BIPUSH, 11));
				call(SET_BLOCK, putBack);
			}
			code.add(new JumpInsnNode(Opcodes.GOTO, end));
		}

		/**
		 * Vanilla's harvest check, {@code player.hasCorrectToolForDrops(state)}; on NeoForge, where the place isn't the hook's
		 * own (its operands follow, {@link Code#PROGRESS}), the helper posting the event with its answer after it.
		 */
		void harvestCall(Set<String> putBack) {
			Code harvest = row.code();
			load(harvestPlayer(harvest));
			load(harvestState(harvest));
			call(vanillaHarvest(harvest), putBack);
			if (harvest != Code.HARVEST) return;
			for (int operand : new int[] {3, 0, 1, 2}) load(operand);
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, HARVEST_EVENT, HARVEST_EVENT_DESC, false));
		}

		private void load(int operand) {
			for (AbstractInsnNode insn : site.operands().get(operand)) {
				if (insn.getOpcode() >= 0) code.add(insn.clone(Map.of()));
			}
		}

		private void state() {
			if (row.code() == Code.FORGE_SERVER) {
				code.add(new VarInsnNode(Opcodes.ALOAD, temp));
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, STATE));
			} else {
				load(row.code() == Code.SERVER ? 2 : 0);
			}
		}

		private void block() {
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, BLOCK_OF_STATE.helper(), BLOCK_OF_STATE.helperDesc(), false));
		}

		private void field(Member field) {
			code.add(new FieldInsnNode(Opcodes.GETFIELD, field.owner(), field.name(srg), field.desc(srg)));
		}

		private void call(Member member, Set<String> putBack) {
			code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, member.owner(), member.name(srg), member.desc(srg), false));
			putBack.add(member.key(srg));
		}
	}

	/**
	 * The private helpers vanilla's branch calls: the checks, the block's {@code getBlock}, Forge's server's reads and
	 * NeoForge's harvest check event; each once.
	 */
	private static void addHelpers(ClassNode node, PutBack row, boolean srg, String layerBase, List<MethodNode> keepsHelpers) {
		Extension extension = row.extension();
		if (extension != null && indexOf(node, extension.entry(), extension.entryDesc()) < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, extension.field(), "Ljava/lang/ClassValue;", null, null));
			node.methods.add(keepsDefault(extension, node.name, srg));
		}
		if (row.form() == OnReceiver.GOLD_HOLDER && indexOf(node, GOLD_AGREES, "(" + ITEM + ")Z") < 0) node.methods.add(goldAgrees());
		if (row.form() != null && row.form().soil()) addSoilHelpers(node, row, srg);
		if (row.code() == Code.EYES && indexOf(node, VANILLA_EYES, VANILLA_EYES_DESC) < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KNOWN_EYES, "Ljava/lang/ClassValue;", null, null));
			node.methods.add(keepsEyes(node.name));
			node.methods.add(vanillaEyes(node.name));
			node.methods.add(otherFluid());
		}
		if (row.form() != null && row.form().action != null && indexOf(node, TOOL_USE, toolUseDesc(row)) < 0) node.methods.add(toolUse(row, srg));
		if (row.code() == Code.LEVEL && indexOf(node, KEEPS_LEVEL, "(Ljava/lang/Object;)Z") < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KNOWN_LEVEL, "Ljava/lang/ClassValue;", null, null));
			node.methods.add(keepsLevel(node.name, srg));
		}
		if (row.form() == OnReceiver.ENCHANTMENT) {
			boolean instance = row.platform().owner().equals(ITEM_INSTANCE);
			if (indexOf(node, ENCHANTMENT_EVENT, enchantmentEventDesc(instance)) < 0) node.methods.add(enchantmentEvent(instance));
		}
		if (layerBase != null && indexOf(node, KEEPS_LAYER, "(Ljava/lang/Object;)Z") < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KNOWN_LAYER, "Ljava/lang/ClassValue;", null, null));
			node.methods.add(keepsLayer(node.name, layerBase, row.platform()));
		}
		if (row.code().keepingPlayer() >= 0 && indexOf(node, KEEPS_PLAYER, "(" + PLAYER + ")Z") < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KNOWN_PLAYER, "Ljava/lang/ClassValue;", null, null));
			node.methods.add(keepsPlayer(node.name, srg));
		}
		if (checks(row) && indexOf(node, "fabricated$keeps", KEEPS) < 0) node.methods.addAll(keepsHelpers);
		Holder holder = extension == null ? null : extension.holder();
		if (holder != null && indexOf(node, holder.helper(), holder.helperDesc()) < 0) {
			node.methods.add(helper(holder.helper(), holder.helperDesc(), Opcodes.ARETURN, new VarInsnNode(Opcodes.ALOAD, 0), invoke(holder.getter(), srg)));
		}
		if ((row.code() == Code.HARVEST || row.code() == Code.PROGRESS) && indexOf(node, HARVEST_EVENT, HARVEST_EVENT_DESC) < 0) node.methods.add(harvestEvent());
		if (row.code() != Code.FORGE_SERVER) return;
		String gameMode = "L" + row.owner() + ";";
		node.methods.add(helper("fabricated$stateAt", "(" + gameMode + POS + ")Ljava/lang/Object;", Opcodes.ARETURN, new VarInsnNode(Opcodes.ALOAD, 0),
				get(LEVEL_FIELD, srg), new VarInsnNode(Opcodes.ALOAD, 1), invoke(GET_BLOCK_STATE, srg)));
		List<AbstractInsnNode> willDestroy = new ArrayList<>(List.of(new VarInsnNode(Opcodes.ALOAD, 2), invoke(GET_BLOCK, srg), new VarInsnNode(Opcodes.ALOAD, 0),
				get(LEVEL_FIELD, srg), new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ALOAD, 2), new VarInsnNode(Opcodes.ALOAD, 0),
				get(PLAYER_FIELD, srg), invoke(WILL_DESTROY, srg)));
		if (!WILL_DESTROY.desc(srg).endsWith(")V")) willDestroy.add(new InsnNode(Opcodes.POP));
		node.methods.add(helper("fabricated$playerWillDestroy", "(" + gameMode + POS + "L" + STATE + ";)V", Opcodes.RETURN,
				willDestroy.toArray(AbstractInsnNode[]::new)));
	}

	/** A crop's growth speed's helpers (SEAM-12): its check's condition, NeoForge's TriState of vanilla's answer, the crop's checks. */
	private static void addSoilHelpers(ClassNode node, PutBack row, boolean srg) {
		OnReceiver form = row.form();
		if (indexOf(node, soilHelper(form), soilHelperDesc(form)) < 0) {
			node.methods.add(soilHelper(form).equals(VANILLA_FARMLAND) ? vanillaFarmland(srg) : vanillaSoil(node.name, form, srg));
		}
		if (form == OnReceiver.FARMLAND && indexOf(node, TRI_STATE_OF, triStateDesc(row)) < 0) {
			node.methods.add(triStateOf(Type.getReturnType(row.platform().desc()).getInternalName()));
		}
		if (form.takesCrop() && indexOf(node, KEEPS_CROP, "(Ljava/lang/Object;)Z") < 0) {
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KNOWN_CROP, "Ljava/lang/ClassValue;", null, null));
			node.methods.add(keepsCrop(node.name, srg));
		}
	}

	/** {@code private static boolean fabricated$vanillaFarmland(BlockState soil)}: {@code soil.getBlock() == Blocks.FARMLAND}. */
	private static MethodNode vanillaFarmland(boolean srg) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, VANILLA_FARMLAND, "(L" + STATE + ";)Z", null, null);
		InsnList code = method.instructions;
		LabelNode no = new LabelNode();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(invoke(GET_BLOCK, srg));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, FARMLAND.owner(), FARMLAND.name(srg), FARMLAND.desc()));
		code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, no));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(no);
		code.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {STATE}, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 2;
		method.maxLocals = 1;
		return method;
	}

	/**
	 * {@code private static boolean fabricated$vanillaSoil(BlockState soil[, Object crop])}: whether vanilla's farmland check
	 * answers what the platform's canSustainPlant default does for the soil, and on Forge the crop (SEAM-12). NeoForge 21.1
	 * and 21.11, whose default's DEFAULT then asks {@code instanceof FarmBlock}, and Forge 52 and 61, whose CropBlock's
	 * {@code mayPlaceOn} asks the same: {@code soil.getBlock() == Blocks.FARMLAND || !(soil.getBlock() instanceof FarmBlock)}.
	 * Forge first asks that the crop is a {@code CropBlock} keeping {@code mayPlaceOn}, {@code getPlant} and
	 * {@code getPlantType} ({@link #keepsCrop}), the plant type then CROP, whose check is vanilla's: on Forge 43 and 47, whose
	 * {@code mayPlaceOn} is vanilla's {@code is(FARMLAND)}, nothing more; on Forge 64 to 66, whose {@code mayPlaceOn} asks
	 * {@code instanceof FarmlandBlock} and whose CROP check is vanilla's {@code is(GROWS_CROPS)}:
	 * {@code !(soil.getBlock() instanceof FarmlandBlock) || soil.is(BlockTags.GROWS_CROPS)}.
	 */
	private static MethodNode vanillaSoil(String owner, OnReceiver form, boolean srg) {
		boolean forge = form.takesCrop();
		Object[] params = forge ? new Object[] {STATE, "java/lang/Object"} : new Object[] {STATE};
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, VANILLA_SOIL, soilHelperDesc(form), null, null);
		InsnList code = method.instructions;
		LabelNode yes = new LabelNode(), no = new LabelNode();
		if (forge) {
			code.add(new VarInsnNode(Opcodes.ALOAD, 1));
			code.add(new TypeInsnNode(Opcodes.INSTANCEOF, CROP));
			code.add(new JumpInsnNode(Opcodes.IFEQ, no));
			code.add(new VarInsnNode(Opcodes.ALOAD, 1));
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, KEEPS_CROP, "(Ljava/lang/Object;)Z", false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, no));
		}
		if (form == OnReceiver.GROWS_CROPS) {
			code.add(new VarInsnNode(Opcodes.ALOAD, 0));
			code.add(invoke(GET_BLOCK, srg));
			code.add(new TypeInsnNode(Opcodes.INSTANCEOF, FARMLAND_BLOCK));
			code.add(new JumpInsnNode(Opcodes.IFEQ, yes));
			code.add(new VarInsnNode(Opcodes.ALOAD, 0));
			code.add(new FieldInsnNode(Opcodes.GETSTATIC, GROWS_CROPS.owner(), GROWS_CROPS.name(), GROWS_CROPS.desc()));
			code.add(invoke(IS_BLOCK_TAG, srg));
			code.add(new JumpInsnNode(Opcodes.IFEQ, no));
		} else if (!forge || !srg) {
			LabelNode farmland = new LabelNode();
			code.add(new VarInsnNode(Opcodes.ALOAD, 0));
			code.add(invoke(GET_BLOCK, srg));
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new FieldInsnNode(Opcodes.GETSTATIC, FARMLAND.owner(), FARMLAND.name(srg), FARMLAND.desc()));
			code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, farmland));
			code.add(new TypeInsnNode(Opcodes.INSTANCEOF, FARM_BLOCK));
			code.add(new JumpInsnNode(Opcodes.IFNE, no));
			code.add(new JumpInsnNode(Opcodes.GOTO, yes));
			code.add(farmland);
			code.add(new FrameNode(Opcodes.F_NEW, params.length, params, 1, new Object[] {BLOCK}));
			code.add(new InsnNode(Opcodes.POP));
		}
		code.add(yes);
		code.add(new FrameNode(Opcodes.F_NEW, params.length, params, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(no);
		code.add(new FrameNode(Opcodes.F_NEW, params.length, params, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 3;
		method.maxLocals = params.length;
		return method;
	}

	/** The descriptor of the row's {@code fabricated$triState}: of the TriState its platform's call answers (NeoForge's on 21.1, Minecraft's on 21.11). */
	private static String triStateDesc(PutBack row) {
		return "(Z)" + Type.getReturnType(row.platform().desc()).getDescriptor();
	}

	/**
	 * {@code private static TriState fabricated$triState(boolean answer)}: {@code answer ? TriState.TRUE : TriState.FALSE}, as
	 * NeoForge reads it, of {@code type} (NeoForge 21.1's TriState, 21.11's Minecraft's, which both have).
	 */
	private static MethodNode triStateOf(String type) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, TRI_STATE_OF, "(Z)L" + type + ";", null, null);
		InsnList code = method.instructions;
		LabelNode no = new LabelNode();
		code.add(new VarInsnNode(Opcodes.ILOAD, 0));
		code.add(new JumpInsnNode(Opcodes.IFEQ, no));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, type, "TRUE", "L" + type + ";"));
		code.add(new InsnNode(Opcodes.ARETURN));
		code.add(no);
		code.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {Opcodes.INTEGER}, 0, new Object[0]));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, type, "FALSE", "L" + type + ";"));
		code.add(new InsnNode(Opcodes.ARETURN));
		method.maxStack = 1;
		method.maxLocals = 1;
		return method;
	}

	/**
	 * {@code private static boolean fabricated$keepsCropDefault(Object crop)}: {@link KeepsDefault#keepsMethods} of the crop
	 * for the checks Forge's canSustainPlant asks of it, CropBlock's {@code mayPlaceOn}, {@code getPlant} (VegetationBlock's
	 * or BushBlock's, which CropBlock keeps) and IPlantable's {@code getPlantType}: one answer for the class, kept as
	 * {@link #keepsDefault}'s are.
	 */
	private static MethodNode keepsCrop(String owner, boolean srg) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_CROP, "(Ljava/lang/Object;)Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, KNOWN_CROP, "java/lang/Object");
		askedFirst(code, owner, "java/lang/Object", parameter(), ASKED_ONE);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		List<Member> checks = List.of(MAY_PLACE_ON, PLANT, PLANT_TYPE);
		List<String> bases = List.of(CROP, CROP, PLANTABLE);
		code.add(new IntInsnNode(Opcodes.BIPUSH, checks.size()));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
		for (int i = 0; i < checks.size(); i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			code.add(new LdcInsnNode(Type.getObjectType(bases.get(i))));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new IntInsnNode(Opcodes.BIPUSH, checks.size()));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
		for (int i = 0; i < checks.size(); i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			code.add(new LdcInsnNode(checks.get(i).name(srg)));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new IntInsnNode(Opcodes.BIPUSH, checks.size()));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "[Ljava/lang/Class;"));
		for (int i = 0; i < checks.size(); i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			classes(code, Type.getArgumentTypes(checks.get(i).desc(srg)));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsMethods", KEEPS_METHODS, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 12;
		method.maxLocals = 2;
		return method;
	}

	/**
	 * {@code private static boolean <entry>(BlockState state)}: {@link KeepsDefault#keepsDefault} of the state and its block
	 * (of the stack and its item), for the extension's methods, whose code is copied into the class beside it
	 * ({@link #keepsHelpers}), with the answers the class keeps in the extension's field, made the first time it asks (its
	 * initialiser stays as it is).
	 */
	private static MethodNode keepsDefault(Extension extension, String owner, boolean srg) {
		Holder holder = extension.holder();
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, extension.entry(), extension.entryDesc(), null, null);
		InsnList code = method.instructions;
		known(code, owner, extension.field(), holder.type());
		askedFirst(code, owner, holder.type(), objectAndHolder(owner, holder), ASKED_BOTH);
		code.add(objectAndHolder(owner, holder));
		type(code, owner, extension.stateInterface(), extension.altState());
		type(code, owner, extension.blockInterface(), extension.altBlock());
		code.add(new LdcInsnNode(extension.name()));
		classes(code, Type.getArgumentTypes(extension.desc()));
		classes(code, Type.getArgumentTypes(extension.blockDesc()));
		Member keeps = extension.stateKeeps();
		if (keeps != null) {
			// The state's class keeps vanilla's method too (KeepsDefault.keepsDefaultAndMethod).
			code.add(new LdcInsnNode(Type.getObjectType(keeps.owner())));
			code.add(new LdcInsnNode(keeps.name(srg)));
			classes(code, Type.getArgumentTypes(keeps.desc(srg)));
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsDefaultAndMethod", KEEPS_AND_METHOD, false));
		} else {
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keeps", KEEPS, false));
		}
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 15;
		method.maxLocals = 2;
		return method;
	}

	/**
	 * Pushes the extension's interface: the class, or where Forge's has another name ({@code alt}), the one the class's
	 * loader finds, NeoForge's first ({@link KeepsDefault#type}): only the first ask of a class's objects makes it.
	 */
	private static void type(InsnList code, String owner, String type, String alt) {
		if (alt == null) {
			code.add(new LdcInsnNode(Type.getObjectType(type)));
			return;
		}
		code.add(new LdcInsnNode(Type.getObjectType(owner)));
		code.add(new LdcInsnNode(type.replace('/', '.')));
		code.add(new LdcInsnNode(alt.replace('/', '.')));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$type", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Class;", false));
	}

	/**
	 * {@code private static boolean fabricated$goldAgrees(ItemStack stack)}: whether 1.21.1's platforms' check of the
	 * stack's gold armour, {@code getMaterial() == ArmorMaterials.GOLD}, and vanilla's, {@code getMaterial().is(GOLD)},
	 * answer the same, as they do for every material but gold's under another holder (a direct holder of gold's value,
	 * another registry's reference of its key): {@code !(item instanceof ArmorItem) || material == GOLD || !material.is(GOLD)}.
	 */
	private static MethodNode goldAgrees() {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, GOLD_AGREES, "(" + ITEM + ")Z", null, null);
		InsnList code = method.instructions;
		LabelNode armor = new LabelNode(), yes = new LabelNode(), no = new LabelNode();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, STACK_CLASS, GET_ITEM.name(), GET_ITEM.desc(), false));
		code.add(new VarInsnNode(Opcodes.ASTORE, 1));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new TypeInsnNode(Opcodes.INSTANCEOF, ARMOR_ITEM));
		code.add(new JumpInsnNode(Opcodes.IFNE, armor));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(armor);
		code.add(new FrameNode(Opcodes.F_NEW, 2, new Object[] {STACK_CLASS, ITEM_CLASS}, 0, new Object[0]));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, ARMOR_ITEM));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ARMOR_ITEM, ARMOR_MATERIAL.name(), ARMOR_MATERIAL.desc(), false));
		code.add(new VarInsnNode(Opcodes.ASTORE, 2));
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, GOLD_MATERIAL.owner(), GOLD_MATERIAL.name(), GOLD_MATERIAL.desc()));
		code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, yes));
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, GOLD_MATERIAL.owner(), GOLD_MATERIAL.name(), GOLD_MATERIAL.desc()));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, HOLDER, HOLDER_IS.name(), HOLDER_IS.desc(), true));
		code.add(new JumpInsnNode(Opcodes.IFNE, no));
		code.add(yes);
		code.add(new FrameNode(Opcodes.F_NEW, 3, new Object[] {STACK_CLASS, ITEM_CLASS, HOLDER}, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(no);
		code.add(new FrameNode(Opcodes.F_NEW, 3, new Object[] {STACK_CLASS, ITEM_CLASS, HOLDER}, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 2;
		method.maxLocals = 3;
		return method;
	}

	/**
	 * {@code private static int fabricated$enchantmentLevel(ItemStack stack, int level, Holder enchantment)} (an
	 * {@code ItemInstance} on NeoForge 26.x): NeoForge's enchantment level event, posted with vanilla's level as the stack's
	 * default posts it with the item's, {@code EventHooks.getEnchantmentLevelSpecific(level, stack, enchantment)}, the
	 * platform's own hook, compiled the same in NeoForge 21.1.265, 21.11.45, 26.1.2.116, 26.2.0.90 and 26.3.0.95.
	 */
	private static MethodNode enchantmentEvent(boolean instance) {
		String desc = enchantmentEventDesc(instance);
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, ENCHANTMENT_EVENT, desc, null, null);
		InsnList code = method.instructions;
		code.add(new VarInsnNode(Opcodes.ILOAD, 1));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_HOOKS, LEVEL_SPECIFIC, "(IL" + (instance ? ITEM_INSTANCE : STACK_CLASS) + ";L" + HOLDER + ";)I", false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 3;
		method.maxLocals = 3;
		return method;
	}

	/** Pushes the entry's object and its holder ({@code state.getBlock()}, {@code stack.getItem()}). */
	private static InsnList objectAndHolder(String owner, Holder holder) {
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, holder.helper(), holder.helperDesc(), false));
		return code;
	}

	private static final String ASKED_ONE = "(Ljava/lang/ClassValue;Ljava/lang/Object;)I";
	private static final String ASKED_BOTH = "(Ljava/lang/ClassValue;Ljava/lang/Object;Ljava/lang/Object;)I";

	/**
	 * After {@link #known}, the store on the stack: the answer kept for the classes of the objects {@code objects} pushes,
	 * where each was asked of before ({@link KeepsDefault#asked}), returned with nothing made; else the store again, kept in
	 * local 1, for the lookup's code after.
	 */
	private static void askedFirst(InsnList code, String owner, String param, InsnList objects, String askedDesc) {
		LabelNode ask = new LabelNode();
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new VarInsnNode(Opcodes.ASTORE, 1));
		code.add(objects);
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$asked", askedDesc, false));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new JumpInsnNode(Opcodes.IFLT, ask));
		code.add(new InsnNode(Opcodes.IRETURN));
		code.add(ask);
		code.add(new FrameNode(Opcodes.F_NEW, 2, new Object[] {param, "java/lang/ClassValue"}, 1, new Object[] {Opcodes.INTEGER}));
		code.add(new InsnNode(Opcodes.POP));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
	}

	/**
	 * {@code private static boolean fabricated$keepsLayerDefault(Object layer)}: {@link KeepsDefault#keepsMethod} of the
	 * elytra layer for {@code shouldRender}, which {@code base} declares, its answers kept as {@link #keepsDefault}'s are.
	 */
	private static MethodNode keepsLayer(String owner, String base, Member shouldRender) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_LAYER, "(Ljava/lang/Object;)Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, KNOWN_LAYER, "java/lang/Object");
		askedFirst(code, owner, "java/lang/Object", parameter(), ASKED_ONE);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new LdcInsnNode(Type.getObjectType(base)));
		code.add(new LdcInsnNode(shouldRender.name()));
		classes(code, Type.getArgumentTypes(shouldRender.desc()));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsMethod", KEEPS_METHOD, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 9;
		method.maxLocals = 2;
		return method;
	}

	/** The tool modification's action type the row's platform call takes: NeoForge's {@code ItemAbility} or Forge's {@code ToolAction}. */
	private static String toolAction(PutBack row) {
		return row.extension() == FORGE_TOOL ? TOOL_ACTION : ABILITY;
	}

	private static String toolUseDesc(PutBack row) {
		return "(L" + STATE + ";L" + USE_ON_CONTEXT + ";L" + toolAction(row) + ";Z)L" + STATE + ";";
	}

	/**
	 * {@code private static BlockState fabricated$toolUse(BlockState state, UseOnContext context, <action> action, boolean
	 * simulate)}: what the platform's defaults of the state's and the block's {@code getToolModifiedState} answer before
	 * their lookup of the action, read in NeoForge 21.1.265, 21.11.45, 26.1.2.116 and 26.2.0.90 and Forge 47.4.28, 52.1.21,
	 * 61.2.4, 64.1.5 and 65.1.5: the platform's tool modification event's state where it differs from the state
	 * ({@code EventHooks.onToolUse}, {@code ForgeEventFactory.onToolUse}: none where a listener cancels it), else none where
	 * the held item can't do the action ({@code context.getItemInHand().canPerformAction(action)}), else the state itself,
	 * for vanilla's code to answer.
	 */
	private static MethodNode toolUse(PutBack row, boolean srg) {
		String action = toolAction(row), desc = toolUseDesc(row);
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, TOOL_USE, desc, null, null);
		InsnList code = method.instructions;
		LabelNode unchanged = new LabelNode(), unable = new LabelNode();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new VarInsnNode(Opcodes.ILOAD, 3));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, action.equals(TOOL_ACTION) ? FORGE_EVENT_FACTORY : EVENT_HOOKS, "onToolUse", desc, false));
		code.add(new VarInsnNode(Opcodes.ASTORE, 4));
		code.add(new VarInsnNode(Opcodes.ALOAD, 4));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, unchanged));
		code.add(new VarInsnNode(Opcodes.ALOAD, 4));
		code.add(new InsnNode(Opcodes.ARETURN));
		Object[] locals = {STATE, USE_ON_CONTEXT, action, Opcodes.INTEGER, STATE};
		code.add(unchanged);
		code.add(new FrameNode(Opcodes.F_NEW, locals.length, locals, 0, new Object[0]));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(invoke(CONTEXT_ITEM, srg));
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, STACK_CLASS, "canPerformAction", "(L" + action + ";)Z", false));
		code.add(new JumpInsnNode(Opcodes.IFEQ, unable));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new InsnNode(Opcodes.ARETURN));
		code.add(unable);
		code.add(new FrameNode(Opcodes.F_NEW, locals.length, locals, 0, new Object[0]));
		code.add(new InsnNode(Opcodes.ACONST_NULL));
		code.add(new InsnNode(Opcodes.ARETURN));
		method.maxStack = 4;
		method.maxLocals = 5;
		return method;
	}

	/**
	 * {@code private static boolean fabricated$keepsLevelDefault(Object level)}: {@link KeepsDefault#keepsMethod} of the
	 * level for {@code LevelReader.isEmptyBlock}, its answers kept as {@link #keepsDefault}'s are.
	 */
	private static MethodNode keepsLevel(String owner, boolean srg) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_LEVEL, "(Ljava/lang/Object;)Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, KNOWN_LEVEL, "java/lang/Object");
		askedFirst(code, owner, "java/lang/Object", parameter(), ASKED_ONE);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new LdcInsnNode(Type.getObjectType(LEVEL_READER_CLASS)));
		code.add(new LdcInsnNode(IS_EMPTY_BLOCK.name(srg)));
		classes(code, Type.getArgumentTypes(IS_EMPTY_BLOCK.desc(srg)));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsMethod", KEEPS_METHOD, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 9;
		method.maxLocals = 2;
		return method;
	}

	/**
	 * {@code private static boolean fabricated$keepsPlayerHarvest(Player player)}: {@link KeepsDefault#keepsMethod} of the
	 * player for {@code Player.hasCorrectToolForDrops}, its answers kept as {@link #keepsDefault}'s are.
	 */
	private static MethodNode keepsPlayer(String owner, boolean srg) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, KEEPS_PLAYER, "(" + PLAYER + ")Z", null, null);
		InsnList code = method.instructions;
		known(code, owner, KNOWN_PLAYER, PLAYER_CLASS);
		askedFirst(code, owner, PLAYER_CLASS, parameter(), ASKED_ONE);
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new LdcInsnNode(Type.getObjectType(PLAYER_CLASS)));
		code.add(new LdcInsnNode(HAS_CORRECT_TOOL.name(srg)));
		classes(code, Type.getArgumentTypes(HAS_CORRECT_TOOL.desc(srg)));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$keepsMethod", KEEPS_METHOD, false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 9;
		method.maxLocals = 2;
		return method;
	}

	private static InsnList parameter() {
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		return code;
	}

	/**
	 * The bytes, as ISO-8859-1, of the {@code ClassValue} giving each class a slot for its answer ({@link KeepsDefault#known}),
	 * a class of the owner's package, which the game class defines beside it, hidden: {@code computeValue} makes an empty
	 * {@code Object[1]}.
	 */
	private static String slots(String owner) {
		String pkg = owner.lastIndexOf('/') < 0 ? "" : owner.substring(0, owner.lastIndexOf('/') + 1);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, pkg + "fabricated$Slots",
				"Ljava/lang/ClassValue<[Ljava/lang/Object;>;", "java/lang/ClassValue", null);
		org.objectweb.asm.MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/ClassValue", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		org.objectweb.asm.MethodVisitor compute = cw.visitMethod(Opcodes.ACC_PROTECTED, "computeValue", "(Ljava/lang/Class;)Ljava/lang/Object;", null, null);
		compute.visitCode();
		compute.visitInsn(Opcodes.ICONST_1);
		compute.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		compute.visitInsn(Opcodes.ARETURN);
		compute.visitMaxs(0, 0);
		compute.visitEnd();
		cw.visitEnd();
		return new String(cw.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	/** Pushes the class's store of answers in {@code field}, made the first time; the method's one parameter is of {@code param}. */
	private static void known(InsnList code, String owner, String field, String param) {
		LabelNode known = new LabelNode();
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, field, "Ljava/lang/ClassValue;"));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new JumpInsnNode(Opcodes.IFNONNULL, known));
		code.add(new InsnNode(Opcodes.POP));
		code.add(new LdcInsnNode(slots(owner)));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "fabricated$known", "(Ljava/lang/String;)Ljava/lang/ClassValue;", false));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field, "Ljava/lang/ClassValue;"));
		code.add(known);
		code.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {param}, 1, new Object[] {"java/lang/ClassValue"}));
	}

	/**
	 * {@code private static boolean fabricated$harvestCheck(boolean vanilla, Player player, BlockState state, BlockGetter
	 * level, BlockPos pos)}: NeoForge's {@code EventHooks.doPlayerHarvestCheck} past vanilla's call, compiled the same in
	 * NeoForge 21.1.265, 21.11.45, 26.1.2.116, 26.2.0.90 and 26.3.0.95:
	 * {@code NeoForge.EVENT_BUS.post(new PlayerEvent.HarvestCheck(player, state, level, pos, vanilla)).canHarvest()}.
	 */
	private static MethodNode harvestEvent() {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, HARVEST_EVENT, HARVEST_EVENT_DESC, null, null);
		InsnList code = method.instructions;
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/neoforged/neoforge/common/NeoForge", "EVENT_BUS", "L" + EVENT_BUS + ";"));
		code.add(new TypeInsnNode(Opcodes.NEW, HARVEST_CHECK));
		code.add(new InsnNode(Opcodes.DUP));
		for (int slot = 1; slot <= 4; slot++) code.add(new VarInsnNode(Opcodes.ALOAD, slot));
		code.add(new VarInsnNode(Opcodes.ILOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, HARVEST_CHECK, "<init>", "(" + PLAYER + "L" + STATE + ";" + GETTER + POS + "Z)V", false));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, EVENT_BUS, "post", "(L" + EVENT + ";)L" + EVENT + ";", true));
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, HARVEST_CHECK));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HARVEST_CHECK, "canHarvest", "()Z", false));
		code.add(new InsnNode(Opcodes.IRETURN));
		method.maxStack = 8;
		method.maxLocals = 5;
		return method;
	}

	/** Pushes a {@code Class[]} of the types. */
	private static void classes(InsnList code, Type[] types) {
		code.add(new IntInsnNode(Opcodes.BIPUSH, types.length));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
		for (int i = 0; i < types.length; i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new IntInsnNode(Opcodes.BIPUSH, i));
			if (types[i].getSort() == Type.BOOLEAN) code.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;"));
			else code.add(new LdcInsnNode(types[i]));
			code.add(new InsnNode(Opcodes.AASTORE));
		}
	}

	private static final String KEEPS = "(Ljava/lang/ClassValue;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;"
			+ "[Ljava/lang/Class;[Ljava/lang/Class;)Z";
	private static final String KEEPS_METHOD = "(Ljava/lang/ClassValue;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Z";
	private static final String KEEPS_METHODS = "(Ljava/lang/ClassValue;Ljava/lang/Object;[Ljava/lang/Class;[Ljava/lang/String;[[Ljava/lang/Class;)Z";
	private static final String KEEPS_AND_METHOD = "(Ljava/lang/ClassValue;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;"
			+ "[Ljava/lang/Class;[Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Z";
	private static final String KEEPS_OWNER = Type.getInternalName(KeepsDefault.class);
	private static volatile byte[] keepsClass;

	/**
	 * {@link KeepsDefault}'s methods, as javac wrote them, made the class's own private helpers: {@code fabricated$keeps},
	 * {@code fabricated$keepsMethod}, {@code fabricated$asked}, {@code fabricated$overrides} and {@code fabricated$declares}, their calls of each other
	 * the class's. They name only the JDK's classes. Read afresh for each class: a tree shared between threads isn't safe to
	 * copy (ASM's {@code MethodNode.accept} resets and sets its labels' state as it goes), and translation reads classes in
	 * parallel.
	 */
	private static List<MethodNode> keepsHelpers(String owner) {
		byte[] data = keepsClass;
		if (data == null) {
			try (java.io.InputStream in = KeepsDefault.class.getResourceAsStream(KeepsDefault.class.getSimpleName() + ".class")) {
				if (in == null) throw new IllegalStateException("no " + KEEPS_OWNER);
				keepsClass = data = in.readAllBytes();
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		List<MethodNode> helpers = new ArrayList<>();
		for (MethodNode helper : read(data, 0).methods) {
			if ((helper.access & Opcodes.ACC_STATIC) == 0 || helper.name.startsWith("<")) continue;
			helper.name = helperName(helper.name);
			helper.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
			// Of KeepsDefault's source: its generic signature, its locals' names and its lines.
			helper.signature = null;
			helper.localVariables = null;
			for (AbstractInsnNode insn : helper.instructions) {
				if (insn instanceof LineNumberNode) helper.instructions.remove(insn);
				if (insn instanceof MethodInsnNode call && call.owner.equals(KEEPS_OWNER)) {
					call.owner = owner;
					call.name = helperName(call.name);
				}
			}
			helpers.add(helper);
		}
		return helpers;
	}

	private static String helperName(String name) {
		return "fabricated$" + (name.equals("keepsDefault") ? "keeps" : name);
	}

	/** A private static method of straight code, its parameters' slots enough for what it loads. */
	private static MethodNode helper(String name, String desc, int returnOpcode, AbstractInsnNode... body) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
		for (AbstractInsnNode insn : body) method.instructions.add(insn);
		method.instructions.add(new InsnNode(returnOpcode));
		method.maxStack = 6;
		method.maxLocals = Type.getArgumentsAndReturnSizes(desc) >> 2;
		return method;
	}

	private static MethodInsnNode invoke(Member member, boolean srg) {
		return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, member.owner(), member.name(srg), member.desc(srg), false);
	}

	private static FieldInsnNode get(Member field, boolean srg) {
		return new FieldInsnNode(Opcodes.GETFIELD, field.owner(), field.name(srg), field.desc(srg));
	}

	/**
	 * A frame of the place: its locals (and {@code temp}, an {@code Object}, where not negative) and the stack below the
	 * platform's operands, with the call's result on it after it.
	 */
	private static FrameNode frame(List<Object> locals, int temp, List<Object> under, boolean result) {
		List<Object> local = compact(locals);
		if (temp >= 0) {
			int slots = slots(locals);
			for (int i = slots; i < temp; i++) local.add(Opcodes.TOP);
			local.add("java/lang/Object");
		}
		List<Object> stack = compact(under);
		if (result) stack.add(Opcodes.INTEGER);
		return new FrameNode(Opcodes.F_NEW, local.size(), local.toArray(), stack.size(), stack.toArray());
	}

	/** {@link AnalyzerAdapter}'s types as a frame's: a long or double once, not followed by its {@code TOP}. */
	private static List<Object> compact(List<Object> types) {
		List<Object> out = new ArrayList<>();
		for (int i = 0; i < types.size(); i++) {
			Object type = types.get(i);
			out.add(type);
			if (type == Opcodes.LONG || type == Opcodes.DOUBLE) i++;
		}
		return out;
	}

	private static int slots(List<Object> types) {
		return types.size();
	}

	/** The types of the locals and of the stack before an instruction, as {@link AnalyzerAdapter} has them. */
	private record State(List<Object> locals, List<Object> stack) {
	}

	/**
	 * The locals and the stack before each instruction of the method, as {@link AnalyzerAdapter} works them out from its
	 * expanded frames (none where the code isn't reached); null where it can't.
	 */
	private static Map<AbstractInsnNode, State> frames(ClassNode owner, MethodNode method) {
		Map<AbstractInsnNode, State> frames = new IdentityHashMap<>();
		AnalyzerAdapter flow = new AnalyzerAdapter(owner.name, method.access, method.name, method.desc, null);
		// Most instructions leave the locals as they were, and many the stack: their copies are shared (a large method's
		// thousands of states, at each rewrite in pre-flight's view).
		State last = null;
		try {
			for (AbstractInsnNode insn : method.instructions) {
				if (flow.stack != null) {
					List<Object> locals = last != null && last.locals().equals(flow.locals) ? last.locals() : List.copyOf(flow.locals);
					List<Object> stack = last != null && last.stack().equals(flow.stack) ? last.stack() : List.copyOf(flow.stack);
					last = last != null && locals == last.locals() && stack == last.stack() ? last : new State(locals, stack);
					frames.put(insn, last);
				}
				insn.accept(flow);
			}
		} catch (RuntimeException e) {
			return null;
		}
		return frames;
	}

	/** The labels a jump, a switch or an exception handler goes to, or a handler's range starts or ends at. */
	private static Set<LabelNode> targets(MethodNode method) {
		Set<LabelNode> targets = VanillaSeams.jumpTargets(method);
		for (TryCatchBlockNode block : method.tryCatchBlocks) {
			targets.add(block.start);
			targets.add(block.end);
		}
		return targets;
	}

	/** The row's method: by its name and descriptor, or where the row gives none, the first of its name making the platform's call. */
	private static MethodNode holder(ClassNode node, PutBack row, boolean srg) {
		for (MethodNode m : node.methods) {
			if (!m.name.equals(row.method(srg)) || row.desc() != null && !m.desc.equals(row.desc())) continue;
			if (row.desc() != null || !calls(m, row).isEmpty()) return m;
		}
		return null;
	}

	private static int indexOf(ClassNode node, String name, String desc) {
		for (int i = 0; i < node.methods.size(); i++) {
			MethodNode m = node.methods.get(i);
			if (m.name.equals(name) && m.desc.equals(desc)) return i;
		}
		return -1;
	}

	/**
	 * The method's calls of the row's platform call (by Mojang's name, or SRG's where the game runs with them): a static
	 * hook's, or an instance method's; an item check's, of the row's action, pushed just before; a tool modification's, of
	 * the row's action, pushed before a {@code false} simulate flag.
	 */
	private static List<MethodInsnNode> calls(MethodNode method, PutBack row) {
		List<MethodInsnNode> found = new ArrayList<>();
		if (method.instructions == null) return found;
		Member member = row.platform();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && (call.getOpcode() == Opcodes.INVOKESTATIC) == row.code().hookCall() && call.owner.equals(member.owner())
					&& (call.name.equals(member.name()) || call.name.equals(member.srgName())) && call.desc.equals(member.desc())
					&& (!row.code().action() || pushesAction(previousReal(call), row.check()))
					&& (row.form() == null || row.form().action == null || modifiesBy(call, row.form().action))) {
				found.add(call);
			}
		}
		return found;
	}

	/** Whether the instruction pushes the item check's action, a field of the platform's actions. */
	private static boolean pushesAction(AbstractInsnNode insn, ItemCheck check) {
		return insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(check.actions()) && field.name.equals(check.action());
	}

	/**
	 * Whether a tool modification's call takes the action, a field of either platform's actions, and {@code false} for its
	 * simulate flag, both pushed just before: the put-back makes the action's side effects (the dousing) as vanilla does.
	 */
	private static boolean modifiesBy(MethodInsnNode call, String action) {
		AbstractInsnNode simulate = previousReal(call), pushed = simulate == null ? null : previousReal(simulate);
		return simulate != null && simulate.getOpcode() == Opcodes.ICONST_0 && pushed instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
				&& (field.owner.equals(ABILITIES) || field.owner.equals(TOOL_ACTIONS)) && field.name.equals(action);
	}

	private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
		List<MethodInsnNode> found = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) found.add(call);
		}
		return found;
	}

	/** Whether the class's code names a class of the package (a call's, a field's or a type's owner). */
	private static boolean references(ClassNode node, String pkg) {
		for (MethodNode method : node.methods) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				String owner = insn instanceof MethodInsnNode m ? m.owner : insn instanceof FieldInsnNode f ? f.owner : insn instanceof TypeInsnNode t ? t.desc : null;
				if (owner != null && owner.startsWith(pkg)) return true;
			}
		}
		return false;
	}

	/**
	 * A vanilla call a platform makes otherwise in a game method, which Fabricated doesn't put back: the method (any of the
	 * name where {@code desc} is null), vanilla's call (its owner, its names), what shows the platform's code is there,
	 * and why, as pre-flight's hint for a hook of the call says it.
	 */
	private record NotPutBack(String owner, String method, String srgMethod, String desc, String callOwner, Set<String> names,
			BiPredicate<ClassNode, MethodNode> evidence, String why) {
	}

	/** The evidence of a method making a call of the class's method of that name. */
	private static BiPredicate<ClassNode, MethodNode> making(String owner, String name) {
		return (node, method) -> !calls(method, owner, name).isEmpty();
	}

	/** The evidence of a class of the platform's package whose method asks whether an object is of one of the classes. */
	private static BiPredicate<ClassNode, MethodNode> asking(String platform, String... types) {
		Set<String> asked = Set.of(types);
		return (node, method) -> references(node, platform) && Arrays.stream(method.instructions.toArray())
				.anyMatch(insn -> insn.getOpcode() == Opcodes.INSTANCEOF && asked.contains(((TypeInsnNode) insn).desc));
	}

	private static final String COMMON_HOOKS = "net/neoforged/neoforge/common/CommonHooks";
	private static final String MAP_ITEM = "net/minecraft/world/item/MapItem";
	private static final String CROSSBOW_ITEM = "net/minecraft/world/item/CrossbowItem";
	private static final String IS = "is";

	/**
	 * The calls not put back. Forge's server makes {@code playerWillDestroy} from its extension, once the tool is damaged,
	 * where vanilla makes it before the creative check: only the removal goes back there (SEAM-3). Both platforms' servers
	 * ask the held item's {@code canAttackBlock} (1.21.11 on: the stack's {@code canDestroyBlock}) inside their block break
	 * event's hook, which pre-cancels the event where the item says no and lets a listener overrule it, where vanilla returns
	 * before anything else; NeoForge 26.1.2 to 26.3's client too (SEAM-4). NeoForge and Forge 47 to 64 make a sheep's, a
	 * snow golem's, a bogged's and (NeoForge 21.1, Forge) a mooshroom's {@code mobInteract} ask for no shears, nor make the
	 * mob's {@code shear} call there: the shears item shears a mob through their shearable extension, which posts their
	 * events and drops what their loot says. The client's held item renderer (NeoForge 21.1 to 26.1.2's, and Forge's
	 * crossbow) and item frame renderer ask whether the item is a {@code MapItem} or a {@code CrossbowItem}, which other
	 * mods' items are too, where vanilla asks whether it is the vanilla item (SEAM-6).
	 */
	private static final List<NotPutBack> NOT_PUT_BACK = Stream.concat(Stream.of(
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, WILL_DESTROY.owner(), Set.of(WILL_DESTROY.name(), WILL_DESTROY.srgName()),
					making(FORGE_HOOKS, "onBlockBreakEvent"), "Forge's ServerPlayerGameMode makes that call through the block's IForgeBlock.onDestroyedByPlayer"
							+ " instead, which Fabricated doesn't put back there (SEAM-3)."),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, ITEM_CLASS, Set.of("canAttackBlock", "m_6777_"), making(FORGE_HOOKS, "onBlockBreakEvent"),
					breakEvent("Forge", "ServerPlayerGameMode", "canAttackBlock", "ForgeHooks.onBlockBreakEvent")),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, STACK_CLASS, Set.of("canDestroyBlock"), making(FORGE_HOOKS, "onBlockBreakEvent"),
					breakEvent("Forge", "ServerPlayerGameMode", "canDestroyBlock", "ForgeHooks.onBlockBreakEvent")),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, ITEM_CLASS, Set.of("canAttackBlock"), making(COMMON_HOOKS, "fireBlockBreak"),
					breakEvent("NeoForge", "ServerPlayerGameMode", "canAttackBlock", "CommonHooks.fireBlockBreak")),
			new NotPutBack(GAME_MODE, "destroyBlock", "m_9280_", DESTROY_BLOCK, STACK_CLASS, Set.of("canDestroyBlock"), making(COMMON_HOOKS, "fireBlockBreak"),
					breakEvent("NeoForge", "ServerPlayerGameMode", "canDestroyBlock", "CommonHooks.fireBlockBreak")),
			new NotPutBack(CLIENT_GAME_MODE, "destroyBlock", "m_105267_", DESTROY_BLOCK, STACK_CLASS, Set.of("canDestroyBlock"), making(COMMON_HOOKS, "fireBlockBreak"),
					breakEvent("NeoForge", "MultiPlayerGameMode", "canDestroyBlock", "CommonHooks.fireBlockBreak")),
			heldItems(NEOFORGE, "NeoForge", "a MapItem or a CrossbowItem", "map and crossbow items"), heldItems(FORGE, "Forge", "a CrossbowItem", "crossbow items"),
			new NotPutBack("net/minecraft/client/renderer/entity/ItemFrameRenderer", "getFrameModelResourceLoc", "m_174212_", null, STACK_CLASS,
					Set.of(IS, IS_ITEM.srgName()), asking(NEOFORGE, MAP_ITEM), mapItem("NeoForge")),
			new NotPutBack("net/minecraft/client/renderer/entity/ItemFrameRenderer", "getFrameModelResourceLoc", "m_174212_", null, STACK_CLASS,
					Set.of(IS, IS_ITEM.srgName()), asking(FORGE, MAP_ITEM), mapItem("Forge")),
			// 1.21.11 on: a leashed entity's friction, an interface's static method, whose class can't keep its answers.
			new NotPutBack(ENTITIES + "Leashable", "angularFriction", "angularFriction", null, BLOCK, Set.of(BLOCK_FRICTION.name()),
					making(STATE, STATE_FRICTION.name()), "Leashable's angularFriction asks the block state's getFriction(level, pos, entity) instead, and"
							+ " Leashable is an interface, which can't keep what Fabricated's check of a block's class answers, so Fabricated doesn't put"
							+ " vanilla's call back there (SEAM-8).")),
			// Each mob's class, before and after 1.21.11 moved them: its shears check, and the mob's shear call it guards
			// (Shearable.shear, SRG's m_5851_).
			Stream.of(ANIMALS + "Sheep", ANIMALS + "sheep/Sheep", ANIMALS + "SnowGolem", ANIMALS + "golem/SnowGolem", ANIMALS + "MushroomCow",
					ANIMALS + "cow/MushroomCow", "net/minecraft/world/entity/monster/Bogged", "net/minecraft/world/entity/monster/skeleton/Bogged")
					.flatMap(mob -> Stream.of(sheared(mob, STACK_CLASS, Set.of(IS, IS_ITEM.srgName()), "check"),
							sheared(mob, mob, Set.of("shear", "m_5851_"), "check and its shear call"))))
			.toList();

	private static String breakEvent(String loader, String gameMode, String check, String hook) {
		return loader + "'s " + gameMode + " asks the held item's " + check + " inside its block break event (" + hook + "), which can overrule the answer,"
				+ " so Fabricated doesn't put that check back there (SEAM-4).";
	}

	/** A mob's call in its {@code mobInteract} of vanilla's shearing, {@code what} the hint says isn't put back. */
	private static NotPutBack sheared(String mob, String callOwner, Set<String> names, String what) {
		return new NotPutBack(mob, "mobInteract", "m_6071_", null, callOwner, names, (node, method) -> calls(method, STACK_CLASS, CAN_PERFORM.name()).isEmpty(),
				mob.substring(mob.lastIndexOf('/') + 1) + "'s mobInteract asks for no shears here: the platform shears it from the shears item, through its"
						+ " shearable extension (NeoForge's IShearable, Forge's IForgeShearable), which posts its events and drops what its loot says, so"
						+ " Fabricated doesn't put vanilla's " + what + " back there (SEAM-6).");
	}

	/** The held item renderer's checks, which the platform asks of the item's class: NeoForge the map's and the crossbow's, Forge the crossbow's. */
	private static NotPutBack heldItems(String platform, String loader, String asked, String items) {
		return new NotPutBack("net/minecraft/client/renderer/ItemInHandRenderer", "renderArmWithItem", "m_109371_", null, STACK_CLASS, Set.of(IS, IS_ITEM.srgName()),
				asking(platform, MAP_ITEM, CROSSBOW_ITEM), loader + "'s ItemInHandRenderer asks whether the held item is " + asked + " instead, which other mods' "
						+ items + " are too, so Fabricated doesn't put vanilla's check back there (SEAM-6).");
	}

	private static String mapItem(String loader) {
		return loader + "'s ItemFrameRenderer asks whether the framed item is a MapItem instead, which other mods' map items are too, so Fabricated doesn't put"
				+ " vanilla's check back there (SEAM-6).";
	}

	/**
	 * Why the class's platform makes vanilla's call {@code owner;name} in its method otherwise, where Fabricated doesn't put
	 * that call back, as pre-flight's hint says it ({@code Forge's ServerPlayerGameMode makes that call through the block's
	 * IForgeBlock.onDestroyedByPlayer instead, which Fabricated doesn't put back there (SEAM-3).}); null for any other.
	 */
	static String notPutBack(ClassNode node, String method, String desc, String owner, String name) {
		boolean srg = VanillaSeams.srg(node);
		for (NotPutBack row : NOT_PUT_BACK) {
			if (!row.owner().equals(node.name) || !(srg ? row.srgMethod() : row.method()).equals(method) || row.desc() != null && !row.desc().equals(desc)
					|| !owner.equals(row.callOwner()) || !row.names().contains(name)) continue;
			int at = indexOf(node, method, desc);
			if (at >= 0 && node.methods.get(at).instructions != null && row.evidence().test(node, node.methods.get(at))) return row.why();
		}
		return null;
	}

	/**
	 * A vanilla method the platform keeps in a class but never calls there, with what shows it and why, as pre-flight's note
	 * of a hook applying in it says it: the hook applies and never runs.
	 */
	private record NeverCalled(String owner, String method, BiPredicate<ClassNode, MethodNode> evidence, String why) {
	}

	/** Whether a method's descriptor takes the sweep attack's box. */
	private static boolean takesBox(MethodNode method) {
		return List.of(Type.getArgumentTypes(method.desc)).contains(Type.getObjectType(BOX));
	}

	/**
	 * The methods never called. NeoForge 21.11 on makes the sweep attack's box in {@code Player.attack}, through the stack's
	 * {@code getSweepHitBox}, and hands it to an overload of {@code doSweepAttack} of its own taking it, where vanilla's
	 * {@code attack} calls vanilla's {@code doSweepAttack}, which makes the box and which NeoForge keeps, calling its overload
	 * from it (SEAM-8). Forge 61 on asks the extension in vanilla's {@code doSweepAttack}, which goes back.
	 */
	private static final List<NeverCalled> NEVER_CALLED = List.of(
			new NeverCalled(PLAYER_CLASS, "doSweepAttack", (node, method) -> !takesBox(method)
					&& node.methods.stream().anyMatch(m -> m.name.equals("doSweepAttack") && takesBox(m))
					&& node.methods.stream().anyMatch(m -> m.name.equals("attack") && m.instructions != null && !calls(m, STACK_CLASS, SWEEP_BOX.name()).isEmpty()),
					"it makes the sweep attack's box in attack through the stack's getSweepHitBox(player, target) and hands it to an overload of"
							+ " doSweepAttack of its own, so the hook never runs; Fabricated doesn't put vanilla's call back there (SEAM-8)"));

	/**
	 * Why the class's platform never calls its vanilla method {@code method(desc)}, which it keeps, so a hook applying there
	 * never runs, as pre-flight's note says it; null for any other.
	 */
	static String neverCalled(ClassNode node, String method, String desc) {
		for (NeverCalled row : NEVER_CALLED) {
			if (!row.owner().equals(node.name) || !row.method().equals(method)) continue;
			int at = indexOf(node, method, desc);
			if (at >= 0 && node.methods.get(at).instructions != null && row.evidence().test(node, node.methods.get(at))) return row.why();
		}
		return null;
	}

	/**
	 * In how many places of the method, as read, vanilla's call {@code owner;name(desc)} is put back (each makes each call
	 * once); 0 for none.
	 */
	static int places(MethodNode method, String owner, String name, String desc) {
		if (method.invisibleAnnotations == null) return 0;
		String key = owner + ";" + name + desc;
		for (AnnotationNode ann : method.invisibleAnnotations) {
			if (VanillaSeams.MARKER.equals(ann.desc) && Annotations.value(ann, "calls") instanceof List<?> calls && calls.contains(key)
					&& Annotations.value(ann, "places") instanceof Integer places) return places;
		}
		return 0;
	}

	/** The calls a class as read has had put back, as {@link #restore} marked its methods. */
	static List<VanillaSeams.Restored> restoredIn(ClassNode node) {
		List<VanillaSeams.Restored> found = new ArrayList<>();
		for (MethodNode method : node.methods) {
			if (method.invisibleAnnotations == null) continue;
			for (AnnotationNode ann : method.invisibleAnnotations) {
				if (!VanillaSeams.MARKER.equals(ann.desc) || !(Annotations.value(ann, "calls") instanceof List<?> calls)) continue;
				String keeps = String.valueOf(Annotations.value(ann, "keeps"));
				for (Object call : calls) {
					String key = String.valueOf(call);
					int semicolon = key.indexOf(';'), paren = key.indexOf('(');
					found.add(new VanillaSeams.Restored(key.substring(0, semicolon), key.substring(semicolon + 1, paren), key.substring(paren),
							method.name + method.desc, keeps, String.valueOf(Annotations.value(ann, "finding")), String.valueOf(Annotations.value(ann, "through")),
							String.valueOf(Annotations.value(ann, "note"))));
				}
			}
		}
		return found;
	}

	/**
	 * What putting the class's calls back changes ({@code restored}: a copy, put back), for {@link SeamConflicts}: the
	 * platform's calls they stand in for
	 * ({@code moved}), the methods holding them, the platform's own methods no longer reached for the blocks keeping the
	 * default ({@code bypassed}: its private {@code removeBlock}), and the game's other methods whose calls change for those
	 * blocks ({@code others}, by class, as names: {@link #others}).
	 */
	static void changes(ClassNode node, ClassNode restored, Set<String> moved, Set<String> holders, Set<String> bypassed, Map<String, Set<String>> others) {
		boolean srg = VanillaSeams.srg(node);
		for (PutBack row : applicable(node)) {
			MethodNode method = holder(restored, row, srg);
			Member platform = row.platform();
			if (method == null || method.invisibleAnnotations == null || method.invisibleAnnotations.stream()
					.noneMatch(a -> VanillaSeams.MARKER.equals(a.desc) && platform.key(false).equals(Annotations.value(a, "platform")))) continue;
			moved.add(platform.key(false));
			holders.add(method.name + method.desc);
			if (platform.owner().equals(node.name)) bypassed.add(platform.name() + platform.desc());
			others(row).forEach((owner, names) -> others.computeIfAbsent(owner, k -> new TreeSet<>()).addAll(names));
		}
	}

	/**
	 * The game's other methods whose calls change where a row's calls are put back, by class, as names (Mojang's and SRG's):
	 * the extensions' defaults, no longer called; on Forge's server {@code Level.removeBlock}, called where Forge's default
	 * sets the block itself; NeoForge's {@code EventHooks.doPlayerHarvestCheck}, which vanilla's call and the event's helper
	 * stand in for; Forge's {@code ForgeHooks.isCorrectToolForDrops}, no longer called, and {@code
	 * Player.hasCorrectToolForDrops}, called where Forge's default only fires the event (a block needing no tool). For the
	 * item checks, vanilla's {@code ItemStack.is}, called where the platform's code didn't (on NeoForge either era's stack
	 * extension), and the elytra layer's {@code shouldRender}, no longer called for the layers keeping it. For a crop's growth
	 * speed, vanilla's checks the defaults don't make and Forge's crop checks; for the eyes' check, Entity's and the fluid
	 * interaction's checks.
	 */
	static Map<String, Set<String>> others(PutBack row) {
		Map<String, Set<String>> others = new TreeMap<>();
		if (row.extension() != null) {
			Extension extension = row.extension();
			for (String type : java.util.Arrays.asList(extension.stateInterface(), extension.blockInterface(), extension.altState(), extension.altBlock())) {
				if (type != null) others.computeIfAbsent(type, k -> new TreeSet<>()).add(extension.name());
			}
		}
		if (row.code().item()) {
			others.computeIfAbsent(STACK_CLASS, k -> new TreeSet<>()).addAll(List.of(IS_ITEM.name(), IS_ITEM.srgName()));
			others.computeIfAbsent(TYPED_INSTANCE, k -> new TreeSet<>()).add(IS_OBJECT.name());
		}
		if (row.code().receiver()) {
			switch (row.form()) {
				// Forge's sound type defaults reach the block past the state's getSoundType, which vanilla's call then makes; Forge
				// 47's block extension (52 on, Block's own).
				case SOUND -> {
					others.computeIfAbsent(STATE_BASE, k -> new TreeSet<>()).addAll(List.of(STATE_SOUND.name(), STATE_SOUND.srgName()));
					others.computeIfAbsent(FORGE_47_SOUND.blockInterface(), k -> new TreeSet<>()).add(FORGE_47_SOUND.name());
				}
				// Vanilla's Holder.is, which 1.21.1's platforms' identity check doesn't make.
				case GOLD, GOLD_HOLDER -> others.computeIfAbsent(HOLDER, k -> new TreeSet<>()).add(HOLDER_IS.name());
				// The platforms' lookup of the shovel's path, which their block extension's default makes.
				case FLATTEN -> others.computeIfAbsent(SHOVEL, k -> new TreeSet<>()).add("getShovelPathingState");
				// A crop's growth speed (SEAM-12). Vanilla's farmland check, BlockStateBase.is, which the defaults don't make for
				// a farm block (NeoForge 21.x's and Forge 52's and 61's ask instanceof, Forge 64 to 66's mayPlaceOn too); and on
				// Forge the crop's checks Block's canSustainPlant asks, which the crops keeping them no longer reach: its
				// mayPlaceOn, its plant (BushBlock's, from 1.21.5 VegetationBlock's, IPlantable's) and its plant type.
				case FARMLAND -> others.computeIfAbsent(STATE_BASE, k -> new TreeSet<>()).addAll(List.of(IS_BLOCK.name(), IS_BLOCK.srgName()));
				case FORGE_FARMLAND, GROWS_CROPS -> {
					others.computeIfAbsent(STATE_BASE, k -> new TreeSet<>()).addAll(List.of(IS_BLOCK.name(), IS_BLOCK.srgName()));
					// 26.x's tag check, TypedInstance.is(TagKey) (BlockStateBase declares only is(TagKey, Predicate) there).
					if (row.form() == OnReceiver.GROWS_CROPS) others.computeIfAbsent(TYPED_INSTANCE, k -> new TreeSet<>()).add(IS_BLOCK_TAG.name());
					others.computeIfAbsent(CROP, k -> new TreeSet<>()).addAll(List.of(MAY_PLACE_ON.name(), MAY_PLACE_ON.srgName()));
					for (String plant : List.of(BLOCKS + "BushBlock", BLOCKS + "VegetationBlock", PLANTABLE)) {
						others.computeIfAbsent(plant, k -> new TreeSet<>()).add(PLANT.name());
					}
					others.computeIfAbsent(PLANTABLE, k -> new TreeSet<>()).add(PLANT_TYPE.name());
				}
				// Vanilla's farmland's moisture: the defaults of Forge 43 to 52 ask state.is(Blocks.FARMLAND) for it, which the
				// put-back's helper tells by its block; 26.x's vanilla reads StateHolder.getValueOrElse, where the defaults read
				// getValue, which it no longer reaches.
				case MOISTURE -> others.computeIfAbsent(STATE_BASE, k -> new TreeSet<>()).addAll(List.of(IS_BLOCK.name(), IS_BLOCK.srgName()));
				case MOISTURE_OR_ELSE -> others.computeIfAbsent(STATE_HOLDER, k -> new TreeSet<>()).addAll(List.of(STATE_VALUE_OR_ELSE.name(), STATE_VALUE.name()));
				// The stack's getTagEnchantments, no longer called, either era's stack extension.
				case ENCHANTMENT -> {
					others.computeIfAbsent(STACK_CLASS, k -> new TreeSet<>()).addAll(List.of("getTagEnchantments", "getEnchantments"));
					others.computeIfAbsent(NEOFORGE_ENCHANTMENT_26.stateInterface(), k -> new TreeSet<>()).addAll(List.of("getTagEnchantments", "getEnchantmentLevel"));
					others.computeIfAbsent(NEOFORGE_ENCHANTMENT.stateInterface(), k -> new TreeSet<>()).add(NEOFORGE_ENCHANTMENT.name());
				}
				default -> {
				}
			}
		}
		switch (row.code()) {
			case ACTION -> others.computeIfAbsent(NEOFORGE_ACTION_26.stateInterface(), k -> new TreeSet<>()).add(NEOFORGE_ACTION_26.name());
			// The eyes' check (SEAM-9): Entity's isEyeInFluid, the tag's and the fluid type's it may call, which vanilla's call
			// makes; the fluid interaction's checks of a fluid type, of a tag, and of the fluid types at the eyes, which the
			// entities whose call goes back no longer reach with NeoForge's predicate, and its lookup of a tag's fluid type.
			case EYES -> {
				others.computeIfAbsent(ENTITY_CLASS, k -> new TreeSet<>()).add("isEyeInFluid");
				others.computeIfAbsent(FLUID_INTERACTION, k -> new TreeSet<>()).addAll(List.of("isEyeInFluid", EYES_MATCHING.name(), "getFluidTypeByTag"));
			}
			case LAYER -> {
				others.computeIfAbsent(row.platform().owner(), k -> new TreeSet<>()).add(row.platform().name());
				others.computeIfAbsent(FORGE_ELYTRA_LAYER, k -> new TreeSet<>()).add(row.platform().name());
			}
			case FORGE_SERVER -> {
				for (String level : List.of(LEVEL, SERVER_LEVEL)) others.computeIfAbsent(level, k -> new TreeSet<>()).addAll(List.of(REMOVE_BLOCK.name(), REMOVE_BLOCK.srgName()));
			}
			case HARVEST, PROGRESS -> others.computeIfAbsent(EVENT_HOOKS, k -> new TreeSet<>()).add(HARVEST_HOOK.name());
			case LEVEL -> others.computeIfAbsent(LEVEL_READER_CLASS, k -> new TreeSet<>()).addAll(List.of(IS_EMPTY_BLOCK.name(), IS_EMPTY_BLOCK.srgName()));
			case FORGE_HARVEST, FORGE_PROGRESS -> {
				others.computeIfAbsent(FORGE_HOOKS, k -> new TreeSet<>()).add(FORGE_HARVEST_HOOK.name());
				others.computeIfAbsent(PLAYER_CLASS, k -> new TreeSet<>()).addAll(List.of(HAS_CORRECT_TOOL.name(), HAS_CORRECT_TOOL.srgName()));
			}
			default -> {
			}
		}
		return others;
	}

	/** Every class whose methods' calls a put-back in the class may change ({@link #others}), of any platform's rows. */
	static Set<String> otherClasses(String owner) {
		return otherMethods(owner).keySet();
	}

	/** {@link #others} of every platform's rows of the class, by class: what any put-back in it may change. */
	static Map<String, Set<String>> otherMethods(String owner) {
		Map<String, Set<String>> methods = new TreeMap<>();
		for (PutBack row : PUT_BACKS) {
			if (!row.owner().equals(owner)) continue;
			// With the forms another era's class gives the row (variant): 26.x's moisture check, Forge 64 to 66's farmland check.
			List<PutBack> forms = new ArrayList<>(List.of(row));
			if (row.form() == OnReceiver.MOISTURE) forms.add(row.with(row.extension(), OnReceiver.MOISTURE_OR_ELSE));
			if (row.form() == OnReceiver.FORGE_FARMLAND) forms.add(row.with(row.extension(), OnReceiver.GROWS_CROPS));
			for (PutBack form : forms) others(form).forEach((c, names) -> methods.computeIfAbsent(c, k -> new TreeSet<>()).addAll(names));
		}
		return methods;
	}

	/**
	 * The members the rewrite adds to the class's methods ({@code owner;name(desc)}, {@code owner;name:desc}): those its
	 * methods name more often once {@code restored}, but its helpers; and the members and classes those helpers name.
	 */
	static Set<String> added(ClassNode original, ClassNode restored) {
		Set<String> added = new LinkedHashSet<>();
		for (MethodNode method : restored.methods) {
			if (method.instructions == null) continue;
			if (method.name.startsWith("fabricated$")) {
				for (AbstractInsnNode insn : method.instructions) {
					String key = key(insn);
					if (key != null && !own(key, restored)) added.add(key);
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) added.add(type.getInternalName());
				}
				continue;
			}
			int at = indexOf(original, method.name, method.desc);
			if (at < 0) continue;
			Map<String, Integer> before = counts(original.methods.get(at)), after = counts(method);
			// The class's own methods it calls more often are those it declares again or adds (SEAM-1's, its helpers).
			after.forEach((key, n) -> {
				if (n > before.getOrDefault(key, 0) && !own(key, restored)) added.add(key);
			});
		}
		return added;
	}

	/** Whether a method of the class has more constant pushes once {@code restored} (its helpers apart). */
	static boolean addsConstants(ClassNode original, ClassNode restored) {
		for (MethodNode method : restored.methods) {
			int at = indexOf(original, method.name, method.desc);
			if (at >= 0 && !method.name.startsWith("fabricated$") && constants(method) > constants(original.methods.get(at))) return true;
		}
		return false;
	}

	private static int constants(MethodNode method) {
		int n = 0;
		if (method.instructions == null) return n;
		for (AbstractInsnNode insn : method.instructions) {
			int op = insn.getOpcode();
			if (op >= Opcodes.ACONST_NULL && op <= Opcodes.SIPUSH || insn instanceof LdcInsnNode) n++;
		}
		return n;
	}

	/** Whether the member is one of the class's methods (SEAM-1's, the helpers) or a field the helpers keep answers in. */
	private static boolean own(String key, ClassNode node) {
		return key.startsWith(node.name + ";") && (key.contains("(") || key.startsWith(node.name + ";fabricated$"));
	}

	private static Map<String, Integer> counts(MethodNode method) {
		Map<String, Integer> counts = new java.util.HashMap<>();
		if (method.instructions == null) return counts;
		for (AbstractInsnNode insn : method.instructions) {
			String key = key(insn);
			if (key != null) counts.merge(key, 1, Integer::sum);
		}
		return counts;
	}

	private static String key(AbstractInsnNode insn) {
		if (insn instanceof MethodInsnNode call) return call.owner + ";" + call.name + call.desc;
		if (insn instanceof FieldInsnNode field) return field.owner + ";" + field.name + ":" + field.desc;
		return null;
	}

	/**
	 * Whether every member and class {@code added} names is in the game, by the classes' own declarations and those they
	 * inherit (the JDK's taken as there): pre-flight puts nothing back that would fail as it runs. A member of a class the
	 * game's files don't hold (NeoForge's event bus, a library of the platform's) counts where the platform's code that the
	 * helpers copy names it the same way ({@code copied}, {@link #copied}). The classes are read for their members' names
	 * alone, each once in {@code classes}.
	 */
	static boolean resolves(Set<String> added, ClassIndex classes, Set<String> copied) {
		for (String key : added) {
			int semicolon = key.indexOf(';');
			if (semicolon < 0) {
				if (!key.startsWith("java/") && !classes.contains(key)) return false;
				continue;
			}
			String owner = key.substring(0, semicolon), member = key.substring(semicolon + 1);
			if (owner.startsWith("java/") || copied.contains(key) && !classes.contains(owner)) continue;
			if (!declares(classes, owner, member, member.indexOf('(') < 0, new LinkedHashSet<>())) return false;
		}
		return true;
	}

	/**
	 * The members named by the platform's code that the class's put-backs copy, as {@code owner;name(desc)}, read from the
	 * game: NeoForge's {@code EventHooks.doPlayerHarvestCheck}, whose post of its event the harvest checks' helper makes
	 * ({@link #harvestEvent}). None where no row copying it applies, or the game hasn't it.
	 */
	static Set<String> copied(ClassNode platformClass, ClassBytesSource game) {
		Set<String> copied = new java.util.HashSet<>();
		if (applicable(platformClass).stream().noneMatch(r -> r.code() == Code.HARVEST || r.code() == Code.PROGRESS)) return copied;
		byte[] data;
		try {
			data = game.read(EVENT_HOOKS);
		} catch (java.io.IOException e) {
			return copied;
		}
		if (data == null) return copied;
		// That method's calls and field reads alone: the class's others aren't parsed.
		new ClassReader(data).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (!name.equals(HARVEST_HOOK.name()) || !descriptor.equals(HARVEST_HOOK.desc())) return null;
				return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
						copied.add(owner + ";" + name + descriptor);
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						copied.add(owner + ";" + name + ":" + descriptor);
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return copied;
	}

	/** Whether the class or one of its supertypes declares the member ({@code name(desc)}, {@code name:desc}). */
	private static boolean declares(ClassIndex classes, String owner, String member, boolean field, Set<String> seen) {
		if (owner == null || !seen.add(owner)) return false;
		ClassIndex.ClassInfo info = classes.get(owner);
		if (info == null) return false;
		if ((field ? info.fieldAccess(member) : info.methodAccess(member)) != null) return true;
		if (declares(classes, info.superName(), member, field, seen)) return true;
		for (String itf : info.interfaces()) if (declares(classes, itf, member, field, seen)) return true;
		return false;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static ClassNode read(byte[] data, int flags) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, flags);
		return node;
	}
}
