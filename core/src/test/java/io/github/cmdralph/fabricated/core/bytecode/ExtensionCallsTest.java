package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-8: vanilla's code on a block state or a stack, which NeoForge and Forge make through an extension of that object
 * taking more (the level, the position, the entity), put back for the objects whose classes keep the defaults: a block's
 * friction and sound type, a piglin's check of gold or safe armour, the sweep attack's box and, on NeoForge, an item's
 * enchantment level. Each fixture is the platform's code in its form (NeoForge 21.1's; NeoForge 26.x's, whose
 * enchantment level is an ItemInstance's and whose piglin check asks a tag; Forge 52's, whose block declares the sound
 * type itself; Forge 61's, whose sweep is doSweepAttack's; Forge 47's under SRG's names, comparing gold as an enum), the
 * platform's defaults leading back to vanilla's code as theirs do. The class rewritten answers as the platform's did for
 * vanilla's blocks and items, a platform mod's overriding the extension, a state class declaring vanilla's sound type
 * again, gold's material under another holder and an item stand-in that isn't a stack; a hook of vanilla's call runs
 * for the objects keeping the defaults, and only for them, and its answer decides there.
 */
public class ExtensionCallsTest {
	private static final String ENTITY = "net/minecraft/world/entity/Entity";
	private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
	private static final String FLYING = "net/minecraft/world/entity/FlyingMob";
	private static final String PLAYER = "net/minecraft/world/entity/player/Player";
	private static final String BLOCK_ITEM = "net/minecraft/world/item/BlockItem";
	private static final String PIGLIN = "net/minecraft/world/entity/monster/piglin/PiglinAi";
	private static final String HELPER = "net/minecraft/world/item/enchantment/EnchantmentHelper";

	/** A platform's form: its packages and its game's version. */
	enum Form {
		NEOFORGE(false, false, false), NEOFORGE_26(false, true, false), FORGE(true, false, false), FORGE_61(true, true, false), FORGE_47(true, false, true);

		/** {@code modern}: Minecraft 1.21.11 on (the piglin's tag, the sweep's own method; on NeoForge the ItemInstance). */
		final boolean forge, modern, srg;

		Form(boolean forge, boolean modern, boolean srg) {
			this.forge = forge;
			this.modern = modern;
			this.srg = srg;
		}

		String ext() {
			return forge ? "net.minecraftforge.common.extensions" : "net.neoforged.neoforge.common.extensions";
		}

		String stateExt() {
			return forge ? "IForgeBlockState" : "IBlockStateExtension";
		}

		String blockExt() {
			return forge ? "IForgeBlock" : "IBlockExtension";
		}

		String stackExt() {
			return forge ? "IForgeItemStack" : "IItemStackExtension";
		}

		String itemExt() {
			return forge ? "IForgeItem" : "IItemExtension";
		}

		boolean enchantments() {
			return !forge;
		}

		/** NeoForge 26.x: the enchantment level of an ItemInstance. */
		boolean instance() {
			return !forge && modern;
		}
	}

	private static final Map<String, String> SRG = new LinkedHashMap<>();
	static {
		for (String[] names : new String[][] {{"getBlock", "m_60734_"}, {"getFriction", "m_49958_"}, {"getSoundType", "m_60827_"}, {"soundOf", "m_49962_"},
				{"getItem", "m_41720_"}, {"getMaterial", "m_40401_"}, {"getBoundingBox", "m_20191_"}, {"inflate", "m_82377_"}, {"travel", "m_7023_"},
				{"playBlockFallSound", "m_21229_"}, {"playStepSound", "m_7355_"}, {"place", "m_40576_"}, {"isWearingGold", "m_34808_"}, {"attack", "m_5706_"}}) {
			SRG.put("$" + names[0] + "$", names[1]);
		}
	}

	/** The game's stand-ins and the platform's classes in the form. */
	static Map<String, String> sources(Form form) {
		Map<String, String> s = new HashMap<>();
		String ext = form.ext();
		String state = "net.minecraft.world.level.block.state.BlockState", block = "net.minecraft.world.level.block.Block";
		String params = "net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity";
		String sound = "net.minecraft.world.level.block.SoundType";
		s.put("net/minecraft/core/BlockPos.java", "package net.minecraft.core; public final class BlockPos { public final int y; public BlockPos(int y) { this.y = y; }"
				+ " public String toString() { return \"pos\" + y; } }");
		s.put("net/minecraft/world/level/LevelReader.java", "package net.minecraft.world.level; public interface LevelReader { }");
		s.put("net/minecraft/world/level/Level.java", "package net.minecraft.world.level; public class Level implements LevelReader { public final java.util.Map<Integer, "
				+ state + "> blocks = new java.util.HashMap<>(); public " + state + " getBlockState(net.minecraft.core.BlockPos pos) { return blocks.get(pos.y); } }");
		s.put("net/minecraft/world/level/block/SoundType.java", "package net.minecraft.world.level.block; public final class SoundType { final String name;"
				+ " public SoundType(String name) { this.name = name; } public String toString() { return name; } }");
		// Vanilla's state: its sound type asks the block (BlockStateBase's), as vanilla's does.
		s.put("net/minecraft/world/level/block/state/BlockBehaviour.java", "package net.minecraft.world.level.block.state; public abstract class BlockBehaviour {"
				+ " public abstract static class BlockStateBase { public abstract " + block + " $getBlock$(); protected abstract BlockState asState();"
				+ " public " + sound + " $getSoundType$() { return $getBlock$().$soundOf$(asState()); } } }");
		s.put("net/minecraft/world/level/block/state/BlockState.java", "package net.minecraft.world.level.block.state; public class BlockState extends"
				+ " BlockBehaviour.BlockStateBase implements " + ext + "." + form.stateExt() + " { final " + block + " block; public BlockState(" + block + " block) {"
				+ " this.block = block; } public " + block + " $getBlock$() { return block; } protected BlockState asState() { return this; }"
				+ " public String toString() { return \"state of \" + block; } }");
		// Forge 52 on: the block declares the sound type itself, its extension without a default.
		boolean blockSound = form.forge && !form.srg;
		s.put("net/minecraft/world/level/block/Block.java", "package net.minecraft.world.level.block; public class Block implements " + ext + "." + form.blockExt() + " {"
				+ " final float friction; final SoundType sound; final String name; public Block(String name, float friction, SoundType sound) { this.name = name;"
				+ " this.friction = friction; this.sound = sound; } public float $getFriction$() { return friction; } public SoundType $soundOf$(" + state + " state) {"
				+ " return sound; } public String toString() { return name; }"
				+ (blockSound ? " public SoundType getSoundType(" + state + " state, " + params + ") { return this.$soundOf$(state); }" : "") + " }");
		s.put(ext.replace('.', '/') + "/" + form.stateExt() + ".java", "package " + ext + "; public interface " + form.stateExt() + " {"
				+ " default float getFriction(" + params + ") { " + state + " self = (" + state + ") this; return self.$getBlock$().getFriction(self, level, pos, entity); }"
				+ " default " + sound + " getSoundType(" + params + ") { " + state + " self = (" + state + ") this; return self.$getBlock$().getSoundType(self, level,"
				+ " pos, entity); } }");
		// NeoForge's block asks the state's sound type; Forge 47's asks its own; Forge 52's declares none.
		String soundDefault = form.forge ? (form.srg ? " default " + sound + " getSoundType(" + state + " state, " + params + ") { return ((" + block + ") this)"
				+ ".$soundOf$(state); }" : " " + sound + " getSoundType(" + state + " state, " + params + ");")
				: " default " + sound + " getSoundType(" + state + " state, " + params + ") { return state.$getSoundType$(); }";
		s.put(ext.replace('.', '/') + "/" + form.blockExt() + ".java", "package " + ext + "; public interface " + form.blockExt() + " {"
				+ " default float getFriction(" + state + " state, " + params + ") { return ((" + block + ") this).$getFriction$(); }" + soundDefault + " }");
		// The platform's mods' block overriding its extensions; vanilla's kind of block overriding vanilla's friction; a state class
		// declaring vanilla's sound type again.
		s.put("com/example/Slippery.java", "package com.example; public class Slippery extends " + block + " { public Slippery() { super(\"slippery\", 0.6F,"
				+ " new " + sound + "(\"stone\")); } @Override public float getFriction(" + state + " state, " + params + ") { Hook.LOG.add(\"slippery asked\"); return 0.8F; }"
				+ " @Override public " + sound + " getSoundType(" + state + " state, " + params + ") { Hook.LOG.add(\"slippery asked\"); return new " + sound + "(\"mod\"); } }");
		s.put("com/example/Ice.java", "package com.example; public class Ice extends " + block + " { public Ice() { super(\"ice\", 0.6F, new " + sound + "(\"glass\")); }"
				+ " @Override public float $getFriction$() { return 0.98F; } }");
		s.put("com/example/OddState.java", "package com.example; public class OddState extends " + state + " { public OddState(" + block + " block) { super(block); }"
				+ " @Override public " + sound + " $getSoundType$() { return new " + sound + "(\"odd\"); } }");
		items(s, form);
		sites(s, form);
		hook(s, form);
		Map<String, String> names = form.srg ? SRG : Map.of();
		s.replaceAll((file, text) -> {
			for (Map.Entry<String, String> e : SRG.entrySet()) {
				String plain = e.getKey().substring(1, e.getKey().length() - 1);
				text = text.replace(e.getKey(), names.getOrDefault(e.getKey(), plain.equals("soundOf") ? "getSoundType" : plain));
			}
			return text;
		});
		return s;
	}

	/** The items: stacks, armour and its materials, tags, boxes, enchantments; the platform's item extensions; mods' items. */
	private static void items(Map<String, String> s, Form form) {
		String ext = form.ext(), stack = "net.minecraft.world.item.ItemStack", living = "net.minecraft.world.entity.LivingEntity";
		String box = "net.minecraft.world.phys.AABB", holder = "net.minecraft.core.Holder", entity = "net.minecraft.world.entity.Entity";
		String player = "net.minecraft.world.entity.player.Player", enchantments = "net.minecraft.world.item.enchantment.ItemEnchantments";
		String instance = "net.minecraft.world.item.ItemInstance", instanceParam = form.instance() ? instance : stack;
		s.put("net/minecraft/core/Holder.java", "package net.minecraft.core; public interface Holder { boolean is(Holder other); Object key(); }");
		s.put("com/example/Ref.java", "package com.example; public final class Ref implements net.minecraft.core.Holder { final String key; public Ref(String key) {"
				+ " this.key = key; } public Object key() { return key; } public boolean is(net.minecraft.core.Holder other) { return other.key().equals(key); }"
				+ " public String toString() { return key; } }");
		s.put("net/minecraft/tags/TagKey.java", "package net.minecraft.tags; public final class TagKey { }");
		s.put("net/minecraft/tags/ItemTags.java", "package net.minecraft.tags; public class ItemTags { public static final TagKey PIGLIN_SAFE_ARMOR = new TagKey(); }");
		s.put("net/minecraft/world/phys/AABB.java", "package net.minecraft.world.phys; public final class AABB { final double x, y, z; public AABB(double x, double y,"
				+ " double z) { this.x = x; this.y = y; this.z = z; } public AABB $inflate$(double a, double b, double c) { return new AABB(x + a, y + b, z + c); }"
				+ " public String toString() { return x + \"/\" + y + \"/\" + z; } }");
		s.put("net/minecraft/core/component/DataComponentType.java", "package net.minecraft.core.component; public final class DataComponentType { }");
		s.put("net/minecraft/core/component/DataComponents.java", "package net.minecraft.core.component; public final class DataComponents {"
				+ " public static final DataComponentType ENCHANTMENTS = new DataComponentType(); }");
		s.put("net/minecraft/world/item/enchantment/ItemEnchantments.java", "package net.minecraft.world.item.enchantment; public final class ItemEnchantments {"
				+ " public static final ItemEnchantments EMPTY = new ItemEnchantments(java.util.Map.of()); final java.util.Map<" + holder + ", Integer> levels;"
				+ " public ItemEnchantments(java.util.Map<" + holder + ", Integer> levels) { this.levels = levels; } public int getLevel(" + holder + " enchantment) {"
				+ " return levels.getOrDefault(enchantment, 0); } }");
		String getOrDefault = " public Object getOrDefault(net.minecraft.core.component.DataComponentType type, Object fallback) { return type =="
				+ " net.minecraft.core.component.DataComponents.ENCHANTMENTS ? new " + enchantments + "(levels) : fallback; }";
		String levels = " public final java.util.Map<" + holder + ", Integer> levels = new java.util.HashMap<>();";
		// The stack: final, its item, its tags, its enchantments as a data component.
		String stackInterfaces = ext + "." + form.stackExt() + (form.instance() ? ", ItemInstance" : "");
		s.put("net/minecraft/world/item/ItemStack.java", "package net.minecraft.world.item; public final class ItemStack implements " + stackInterfaces + " {"
				+ " final Item item;" + levels + " public ItemStack(Item item) { this.item = item; } public Item $getItem$() { return item; }"
				+ (form.instance() ? " public Item item() { return item; }" : "")
				+ " public boolean is(net.minecraft.tags.TagKey tag) { return item.tags.contains(tag); }" + getOrDefault
				+ " public " + enchantments + " getEnchantments() { return (" + enchantments + ") getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, "
				+ enchantments + ".EMPTY); } public " + enchantments + " getTagEnchantments() { return getEnchantments(); } public String toString() { return \"stack of \" + item; } }");
		if (form.instance()) {
			s.put("net/minecraft/world/item/ItemInstance.java", "package net.minecraft.world.item; public interface ItemInstance extends " + ext + ".ItemInstanceExtension {"
					+ " Item item(); Object getOrDefault(net.minecraft.core.component.DataComponentType type, Object fallback); }");
			s.put("net/minecraft/world/item/ItemStackTemplate.java", "package net.minecraft.world.item; public final class ItemStackTemplate implements ItemInstance {"
					+ " final Item item;" + levels + " public ItemStackTemplate(Item item) { this.item = item; } public Item item() { return item; }" + getOrDefault + " }");
			s.put(ext.replace('.', '/') + "/ItemInstanceExtension.java", "package " + ext + "; public interface ItemInstanceExtension {"
					+ " default int getEnchantmentLevel(" + holder + " enchantment) { " + instance + " self = (" + instance + ") this; int level = self.item()"
					+ ".getEnchantmentLevel(self, enchantment); return net.neoforged.neoforge.event.EventHooks.getEnchantmentLevelSpecific(level, self, enchantment); }"
					+ " default " + enchantments + " getTagEnchantments() { return (" + enchantments + ") ((" + instance + ") this).getOrDefault("
					+ "net.minecraft.core.component.DataComponents.ENCHANTMENTS, " + enchantments + ".EMPTY); } }");
		}
		String stackEnchantment = form.enchantments() && !form.instance() ? " default int getEnchantmentLevel(" + holder + " enchantment) { " + stack + " self = ("
				+ stack + ") this; int level = self.$getItem$().getEnchantmentLevel(self, enchantment); return net.neoforged.neoforge.event.EventHooks"
				+ ".getEnchantmentLevelSpecific(level, self, enchantment); }" : "";
		s.put(ext.replace('.', '/') + "/" + form.stackExt() + ".java", "package " + ext + "; public interface " + form.stackExt() + " {"
				+ " default boolean makesPiglinsNeutral(" + living + " wearer) { " + stack + " self = (" + stack + ") this; return self.$getItem$().makesPiglinsNeutral(self,"
				+ " wearer); } default " + box + " getSweepHitBox(" + player + " player, " + entity + " target) { " + stack + " self = (" + stack + ") this;"
				+ " return self.$getItem$().getSweepHitBox(self, player, target); }" + stackEnchantment + " }");
		// The item's defaults: 1.20.1's gold by identity of the enum, 1.21.1's by identity of the holder, 1.21.11's the tag.
		String piglin = form.modern ? "return stack.is(net.minecraft.tags.ItemTags.PIGLIN_SAFE_ARMOR);"
				: "return stack.$getItem$() instanceof net.minecraft.world.item.ArmorItem && ((net.minecraft.world.item.ArmorItem) stack.$getItem$()).$getMaterial$()"
						+ " == net.minecraft.world.item.ArmorMaterials.GOLD;";
		String itemEnchantment = form.enchantments() ? " default int getEnchantmentLevel(" + instanceParam + " stack, " + holder + " enchantment) {"
				+ " return stack.getTagEnchantments().getLevel(enchantment); }" : "";
		s.put(ext.replace('.', '/') + "/" + form.itemExt() + ".java", "package " + ext + "; public interface " + form.itemExt() + " {"
				+ " default boolean makesPiglinsNeutral(" + stack + " stack, " + living + " wearer) { " + piglin + " }"
				+ " default " + box + " getSweepHitBox(" + stack + " stack, " + player + " player, " + entity + " target) {"
				+ " return target.$getBoundingBox$().$inflate$(1.0, 0.25, 1.0); }" + itemEnchantment + " }");
		if (form.enchantments()) {
			s.put("net/neoforged/neoforge/event/EventHooks.java", "package net.neoforged.neoforge.event; public class EventHooks { public static int"
					+ " getEnchantmentLevelSpecific(int level, " + instanceParam + " stack, " + holder + " enchantment) { com.example.Hook.LOG.add(\"event \" + level);"
					+ " return level + com.example.Hook.bonus; } }");
		}
		s.put("net/minecraft/world/item/Item.java", "package net.minecraft.world.item; public class Item implements " + ext + "." + form.itemExt() + " { final String name;"
				+ " public final java.util.Set<net.minecraft.tags.TagKey> tags = new java.util.HashSet<>(); public Item(String name) { this.name = name; }"
				+ " public String toString() { return name; } }");
		String material = form.srg ? "ArmorMaterial" : holder;
		if (form.srg) {
			s.put("net/minecraft/world/item/ArmorMaterial.java", "package net.minecraft.world.item; public interface ArmorMaterial { }");
			s.put("net/minecraft/world/item/ArmorMaterials.java", "package net.minecraft.world.item; public enum ArmorMaterials implements ArmorMaterial { GOLD, IRON }");
		} else {
			s.put("net/minecraft/world/item/ArmorMaterials.java", "package net.minecraft.world.item; public class ArmorMaterials { public static final " + holder
					+ " GOLD = new com.example.Ref(\"gold\"), IRON = new com.example.Ref(\"iron\"); }");
		}
		s.put("net/minecraft/world/item/ArmorItem.java", "package net.minecraft.world.item; public class ArmorItem extends Item { final " + material + " material;"
				+ " public ArmorItem(String name, " + material + " material) { super(name); this.material = material; } public " + material + " $getMaterial$() {"
				+ " return material; } }");
		// The platform's mods' items overriding the extensions.
		s.put("com/example/Neutral.java", "package com.example; public class Neutral extends net.minecraft.world.item.Item { public Neutral() { super(\"neutral\"); }"
				+ " @Override public boolean makesPiglinsNeutral(" + stack + " stack, " + living + " wearer) { Hook.LOG.add(\"neutral asked\"); return true; } }");
		s.put("com/example/Sweeper.java", "package com.example; public class Sweeper extends net.minecraft.world.item.Item { public Sweeper() { super(\"sweeper\"); }"
				+ " @Override public " + box + " getSweepHitBox(" + stack + " stack, " + player + " player, " + entity + " target) { Hook.LOG.add(\"sweeper asked\");"
				+ " return new " + box + "(9, 9, 9); } }");
		if (form.enchantments()) {
			s.put("com/example/Intrinsic.java", "package com.example; public class Intrinsic extends net.minecraft.world.item.Item { public Intrinsic() {"
					+ " super(\"intrinsic\"); } @Override public int getEnchantmentLevel(" + instanceParam + " stack, " + holder + " enchantment) {"
					+ " Hook.LOG.add(\"intrinsic asked\"); return 5; } }");
		}
	}

	/** The game's methods making the platform's calls, as the platforms' have them. */
	private static void sites(Map<String, String> s, Form form) {
		String state = "net.minecraft.world.level.block.state.BlockState", sound = "net.minecraft.world.level.block.SoundType";
		String pos = "net.minecraft.core.BlockPos", level = "net.minecraft.world.level.Level", stack = "net.minecraft.world.item.ItemStack";
		String box = "net.minecraft.world.phys.AABB", holder = "net.minecraft.core.Holder";
		// A step sound: the state's, and where a block is above, its own, whose store a jump's target follows.
		s.put(ENTITY + ".java", "package net.minecraft.world.entity; public class Entity { public " + level + " level; public " + pos + " below = new " + pos + "(0);"
				+ " public " + box + " $getBoundingBox$() { return new " + box + "(1, 1, 1); } public void $playStepSound$(" + pos + " pos, " + state + " state) {"
				+ " " + state + " above = level.getBlockState(new " + pos + "(pos.y + 1)); " + sound + " sound = state.getSoundType(level, pos, this);"
				+ " if (above != null) { sound = above.getSoundType(level, pos, this); } com.example.Hook.LOG.add(\"step \" + sound); } }");
		// Movement's friction, stored then used; the fall sound, stored in a branch.
		s.put(LIVING + ".java", "package net.minecraft.world.entity; public class LivingEntity extends Entity { public final java.util.List<" + stack + "> armor ="
				+ " new java.util.ArrayList<>(); public String $travel$(boolean onGround) { " + pos + " pos = below; float friction = level.getBlockState(pos)"
				+ ".getFriction(level, pos, this); return \"travel \" + friction * 0.91F; } public void $playBlockFallSound$() { " + state + " state ="
				+ " level.getBlockState(below); if (state != null) { " + sound + " sound = state.getSoundType(level, below, this);"
				+ " com.example.Hook.LOG.add(\"fall \" + sound); } } }");
		// A flying mob's friction, in a conditional, used at once.
		s.put(FLYING + ".java", "package net.minecraft.world.entity; public class FlyingMob extends LivingEntity { @Override public String $travel$(boolean onGround) {"
				+ " float friction = onGround ? level.getBlockState(below).getFriction(level, below, this) * 0.91F : 0.91F; return \"fly \" + friction; } }");
		// The placed block's sound, of the context's player, a call vanilla's code doesn't make there.
		s.put("net/minecraft/world/item/context/BlockPlaceContext.java", "package net.minecraft.world.item.context; public class BlockPlaceContext {"
				+ " public net.minecraft.world.entity.Entity getPlayer() { com.example.Hook.LOG.add(\"player\"); return null; } }");
		s.put(BLOCK_ITEM + ".java", "package net.minecraft.world.item; public class BlockItem extends Item { public BlockItem() { super(\"block item\"); } public String"
				+ " $place$(" + level + " level, " + pos + " pos, " + state + " placed, net.minecraft.world.item.context.BlockPlaceContext context) { " + sound
				+ " sound = placed.getSoundType(level, pos, context.getPlayer()); return \"placed \" + sound; } }");
		// The piglin's armour check: 1.20.1's and 1.21.1's read the item first, as vanilla's does; 1.21.11's the stack of each slot.
		String piglin = form.modern ? " public static boolean isWearingSafeArmor(net.minecraft.world.entity.LivingEntity entity) { for (" + stack + " stack : entity.armor)"
				+ " { if (stack.makesPiglinsNeutral(entity)) return true; } return false; }"
				: " public static boolean $isWearingGold$(net.minecraft.world.entity.LivingEntity entity) { for (" + stack + " stack : entity.armor) {"
						+ " net.minecraft.world.item.Item item = stack.$getItem$(); if (stack.makesPiglinsNeutral(entity)) return true; } return false; }";
		s.put(PIGLIN + ".java", "package net.minecraft.world.entity.monster.piglin; public class PiglinAi {" + piglin + " }");
		// The sweep's box, of the main hand's stack: in attack (1.20.1, 1.21.1), in doSweepAttack (Forge 61 on); NeoForge 26.x's attack
		// makes it for an overload of its own, vanilla's doSweepAttack making vanilla's.
		String sweep;
		if (!form.modern) {
			sweep = " public String $attack$(net.minecraft.world.entity.Entity target) { " + box + " box = this.mainHand().getSweepHitBox(this, target);"
					+ " return \"swept \" + box; }";
		} else if (form.forge) {
			sweep = " public String doSweepAttack(net.minecraft.world.entity.Entity target, float damage, Object source, float scale) { " + box + " box ="
					+ " this.mainHand().getSweepHitBox(this, target); return \"swept \" + box; }";
		} else {
			sweep = " public String attack(net.minecraft.world.entity.Entity target) { " + box + " box = this.mainHand().getSweepHitBox(this, target); return"
					+ " doSweepAttack(target, 1F, null, 1F, box); } public String doSweepAttack(net.minecraft.world.entity.Entity target, float damage, Object source,"
					+ " float scale) { return doSweepAttack(target, damage, source, scale, target.getBoundingBox().inflate(1.0, 0.25, 1.0)); } public String"
					+ " doSweepAttack(net.minecraft.world.entity.Entity target, float damage, Object source, float scale, " + box + " box) { return \"swept \" + box; }";
		}
		s.put(PLAYER + ".java", "package net.minecraft.world.entity.player; public class Player extends net.minecraft.world.entity.LivingEntity { public " + stack
				+ " main; public " + stack + " mainHand() { com.example.Hook.LOG.add(\"hand\"); return main; }" + sweep + " }");
		if (form.enchantments()) {
			String of = form.instance() ? "net.minecraft.world.item.ItemInstance" : stack;
			s.put(HELPER + ".java", "package net.minecraft.world.item.enchantment; public class EnchantmentHelper { public static int getItemEnchantmentLevel(" + holder
					+ " enchantment, " + of + " stack) { return stack.getEnchantmentLevel(enchantment); } }");
		}
	}

	/** The hook a Fabric mod's wrapper of vanilla's call stands for: it logs the value, and while answering, changes it. */
	private static void hook(Map<String, String> s, Form form) {
		String material = form.srg ? " public static net.minecraft.world.item.ArmorMaterial material(net.minecraft.world.item.ArmorMaterial m) { LOG.add(\"hooked \" + m);"
				+ " return answer ? net.minecraft.world.item.ArmorMaterials.GOLD : m; }" : "";
		s.put("com/example/Hook.java", "package com.example; public class Hook { public static final java.util.List<String> LOG = new java.util.ArrayList<>();"
				+ " public static boolean answer; public static int bonus;"
				+ " public static float friction(float f) { LOG.add(\"hooked \" + f); return answer ? 0.25F : f; }"
				+ " public static net.minecraft.world.level.block.SoundType sound(net.minecraft.world.level.block.SoundType t) { LOG.add(\"hooked \" + t);"
				+ " return answer ? new net.minecraft.world.level.block.SoundType(\"snow\") : t; }"
				+ " public static boolean piglin(boolean b) { LOG.add(\"hooked \" + b); return answer || b; }"
				+ " public static net.minecraft.world.phys.AABB box(net.minecraft.world.phys.AABB b) { LOG.add(\"hooked \" + b); return answer ? b.$inflate$(1, 1, 1) : b; }"
				+ " public static int level(int l) { LOG.add(\"hooked \" + l); return answer ? l + 100 : l; }" + material + " }");
	}

	/** NeoForge 21.1's classes, as the fixtures have them, for pre-flight's tests. */
	public static Map<String, String> neoForgeSources() {
		return sources(Form.NEOFORGE);
	}

	/** Forge 47's, under SRG's names. */
	public static Map<String, String> forge47Sources() {
		return sources(Form.FORGE_47);
	}

	private static Map<String, byte[]> platform(Path dir, Form form) throws Exception {
		return TestSources.compileWithLocals(dir, sources(form));
	}

	/** The classes making the platform's calls, each with its put-backs as it loads. */
	private static List<String> sitesOf(Form form) {
		List<String> sites = new ArrayList<>(List.of(ENTITY, LIVING, FLYING, BLOCK_ITEM, PIGLIN, PLAYER));
		if (form.enchantments()) sites.add(HELPER);
		// NeoForge 26.x's sweep isn't put back (its attack makes the box for an overload of its own): Player stays as it is.
		if (form == Form.NEOFORGE_26) sites.remove(PLAYER);
		return sites;
	}

	private static Map<String, byte[]> restored(Map<String, byte[]> platform, Form form) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		for (String name : sitesOf(form)) {
			ClassNode node = node(platform.get(name));
			VanillaSeams.Restoring done = VanillaSeams.restoreWhole(node);
			assertNull(done.failure(), form + " " + name);
			assertTrue(!done.restored().isEmpty(), form + " " + name);
			restored.put(name, write(node));
		}
		return restored;
	}

	/** The vanilla calls a Fabric mod's wrapper hooks, and the hook's method for each, of the result's type. */
	private static String hookOf(MethodInsnNode call, Form form) {
		String name = call.name;
		if (call.owner.equals("net/minecraft/world/level/block/Block") && name.equals(form.srg ? "m_49958_" : "getFriction") && call.desc.equals("()F")) return "friction";
		if (call.owner.equals("net/minecraft/world/level/block/state/BlockState") && name.equals(form.srg ? "m_60827_" : "getSoundType") && call.desc.startsWith("()")) {
			return "sound";
		}
		if (call.owner.equals("net/minecraft/core/Holder") && name.equals("is")) return "piglin";
		if (call.owner.equals("net/minecraft/world/item/ItemStack") && name.equals("is") && call.desc.equals("(Lnet/minecraft/tags/TagKey;)Z")) return "piglin";
		if (call.owner.equals("net/minecraft/world/item/ArmorItem") && name.equals("m_40401_")) return "material";
		if (call.owner.equals("net/minecraft/world/phys/AABB") && name.equals(form.srg ? "m_82377_" : "inflate")) return "box";
		if (call.owner.equals("net/minecraft/world/item/enchantment/ItemEnchantments") && name.equals("getLevel")) return "level";
		return null;
	}

	/** The classes with each of vanilla's calls the sites make (outside Fabricated's helpers) passed through the hook, as a Fabric mod's wrapper would. */
	private static Map<String, byte[]> hooked(Map<String, byte[]> classes, Form form) {
		Map<String, byte[]> hooked = new HashMap<>(classes);
		for (String name : sitesOf(form)) {
			ClassNode node = node(classes.get(name));
			for (MethodNode method : node.methods) {
				if (method.name.startsWith("fabricated$")) continue;
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || hookOf(call, form) == null) continue;
					String type = org.objectweb.asm.Type.getReturnType(call.desc).getDescriptor();
					method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", hookOf(call, form), "(" + type + ")" + type, false));
				}
			}
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			hooked.put(name, writer.toByteArray());
		}
		return hooked;
	}

	/** What a run of a site found: its answer and what was logged. */
	private record Run(String answer, List<String> log) {
		long hooked() {
			return log.stream().filter(l -> l.startsWith("hooked")).count();
		}

		/** The log without the hook's lines and the operands the platform's call alone evaluates. */
		List<String> plain() {
			return log.stream().filter(l -> !l.startsWith("hooked") && !l.equals("player")).toList();
		}
	}

	private static final List<String> BLOCKS = List.of("plain", "ice", "slippery", "odd");

	/** Whether the block (and its state) keeps the defaults the site's call has: friction asks no state of its own; sound asks the state's class too. */
	private static boolean keepsBlock(String block, boolean sound) {
		return block.equals("plain") || block.equals("ice") || block.equals("odd") && !sound;
	}

	/** One run of a block site, of a state of the block, hooked or not, the hook answering or not. */
	private static Run runBlock(Map<String, byte[]> classes, String site, String block, boolean answer) throws Exception {
		ClassLoader loader = loader(classes);
		Class<?> hook = loader.loadClass("com.example.Hook");
		hook.getField("answer").set(null, answer);
		Class<?> blockClass = loader.loadClass("net.minecraft.world.level.block.Block"), stateClass = loader.loadClass("net.minecraft.world.level.block.state.BlockState");
		Class<?> soundClass = loader.loadClass("net.minecraft.world.level.block.SoundType");
		Object b = switch (block) {
			case "ice" -> loader.loadClass("com.example.Ice").getConstructor().newInstance();
			case "slippery" -> loader.loadClass("com.example.Slippery").getConstructor().newInstance();
			default -> blockClass.getConstructor(String.class, float.class, soundClass).newInstance("plain", 0.6F, soundClass.getConstructor(String.class).newInstance("stone"));
		};
		Object state = block.equals("odd") ? loader.loadClass("com.example.OddState").getConstructor(blockClass).newInstance(b)
				: stateClass.getConstructor(blockClass).newInstance(b);
		Class<?> levelClass = loader.loadClass("net.minecraft.world.level.Level"), posClass = loader.loadClass("net.minecraft.core.BlockPos");
		Object level = levelClass.getConstructor().newInstance();
		@SuppressWarnings("unchecked") Map<Integer, Object> blocks = (Map<Integer, Object>) levelClass.getField("blocks").get(level);
		blocks.put(0, state);
		Object pos = posClass.getConstructor(int.class).newInstance(0);
		Object answered;
		switch (site) {
			case "travel", "fly", "fall" -> {
				Object entity = loader.loadClass(site.equals("fly") ? FLYING.replace('/', '.') : LIVING.replace('/', '.')).getConstructor().newInstance();
				entity.getClass().getField("level").set(entity, level);
				answered = site.equals("fall") ? invoke(entity, 0, "") : invoke(entity, 1, "", true);
			}
			case "step", "stepAbove" -> {
				if (site.equals("stepAbove")) blocks.put(1, state);
				Object entity = loader.loadClass(ENTITY.replace('/', '.')).getConstructor().newInstance();
				entity.getClass().getField("level").set(entity, level);
				answered = invoke(entity, 2, "", pos, state);
			}
			default -> {
				Object item = loader.loadClass(BLOCK_ITEM.replace('/', '.')).getConstructor().newInstance();
				Object context = loader.loadClass("net.minecraft.world.item.context.BlockPlaceContext").getConstructor().newInstance();
				answered = invoke(item, 4, "", level, pos, state, context);
			}
		}
		// A sound site's answer is what it played.
		List<String> log = log(loader);
		return new Run(answered + " " + log.stream().filter(l -> l.startsWith("step ") || l.startsWith("fall ")).toList(), log);
	}

	/** The block sites and how many places of each a run passes. */
	private static final Map<String, Integer> BLOCK_SITES = Map.of("travel", 1, "fly", 1, "fall", 1, "step", 1, "stepAbove", 2, "place", 1);

	@Test
	void vanillasCallsOfTheBlockStateArePutBackWhereTheBlockKeepsTheDefaults(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form);
			Map<String, byte[]> restored = restored(platform, form);
			for (Map.Entry<String, Integer> site : BLOCK_SITES.entrySet()) {
				boolean sound = !site.getKey().equals("travel") && !site.getKey().equals("fly");
				for (String block : BLOCKS) {
					String what = form + " " + site.getKey() + " " + block;
					boolean keeps = keepsBlock(block, sound);
					// Unhooked, every block answers as the platform's code had it; the platform's call's other operands are evaluated only for it.
					Run before = runBlock(platform, site.getKey(), block, false), after = runBlock(restored, site.getKey(), block, false);
					assertEquals(before.answer(), after.answer(), what);
					assertEquals(before.plain(), after.plain(), what);
					if (site.getKey().equals("place")) assertEquals(keeps ? 0 : 1, after.log().stream().filter("player"::equals).count(), what + " " + after.log());
					// Hooked, the hook runs for the blocks keeping the defaults, at each place, and its answer decides there.
					Run hooked = runBlock(hooked(restored, form), site.getKey(), block, true);
					assertEquals(keeps ? site.getValue() : 0, hooked.hooked(), what + " " + hooked.log());
					if (keeps) assertTrue(hooked.answer().contains(sound ? "snow" : String.valueOf(0.25F * (site.getKey().equals("fly") || site.getKey().equals("travel") ? 0.91F : 1))),
							what + " " + hooked);
					else assertEquals(before.answer(), hooked.answer(), what);
				}
			}
			// Pre-flight reads what was put back: vanilla's calls, each the block state's call stands in for.
			String getBlock = form.srg ? "m_60734_" : "getBlock", getFriction = form.srg ? "m_49958_" : "getFriction", getSound = form.srg ? "m_60827_" : "getSoundType";
			assertEquals(List.of("net/minecraft/world/level/block/state/BlockState;" + getBlock + "()Lnet/minecraft/world/level/block/Block; SEAM-8",
					"net/minecraft/world/level/block/Block;" + getFriction + "()F SEAM-8", "net/minecraft/world/level/block/state/BlockState;" + getSound
							+ "()Lnet/minecraft/world/level/block/SoundType; SEAM-8"),
					VanillaSeams.restoredIn(node(restored.get(LIVING))).stream().map(r -> r.owner() + ";" + r.name() + r.desc() + " " + r.finding()).toList(),
					form.name());
			VanillaSeams.Restored step = VanillaSeams.restoredIn(node(restored.get(ENTITY))).get(0);
			assertEquals("the block state's getSoundType(level, pos, entity)", step.through());
			assertTrue(step.note().contains("a block overriding it is asked as the platform has it"), step.note());
			assertEquals(2, VanillaSeams.putBackPlaces(method(node(restored.get(ENTITY)), form.srg ? "m_7355_" : "playStepSound"), "net/minecraft/world/level/block/state/BlockState",
					getSound, "()Lnet/minecraft/world/level/block/SoundType;"));
			// Vanilla's friction, as an INVOKE_ASSIGN point reads it: the call stored to the platform's local, as the platform's is,
			// and the local named from there on as from the platform's store (PRE-30: Forge's Mixin reads it off the table there).
			MethodNode travel = method(node(restored.get(LIVING)), form.srg ? "m_7023_" : "travel");
			int stores = 0;
			for (AbstractInsnNode insn : travel.instructions) {
				if (insn instanceof MethodInsnNode call && call.name.equals(getFriction) && call.desc.equals("()F")) {
					AbstractInsnNode next = call.getNext();
					assertTrue(next instanceof VarInsnNode store && store.getOpcode() == Opcodes.FSTORE, form + ": vanilla's friction isn't stored where the platform's is");
					VarInsnNode store = (VarInsnNode) next;
					AbstractInsnNode after = store.getNext();
					assertTrue(travel.localVariables.stream().anyMatch(local -> local.name.equals("friction") && local.index == store.var && local.desc.equals("F")
							&& travel.instructions.indexOf(local.start) == travel.instructions.indexOf(after)), form + ": vanilla's store starts no local");
					stores++;
				}
			}
			assertEquals(1, stores, form.name());
			assertEquals(2, travel.localVariables.stream().filter(local -> local.name.equals("friction")).count(), form.name());
		}
	}

	/** Each item case of the item sites, by site. */
	private static List<String> itemsOf(String site, Form form) {
		return switch (site) {
			case "piglin" -> form.modern ? List.of("plain", "safe", "neutral") : form.srg ? List.of("plain", "gold", "iron", "neutral")
					: List.of("plain", "gold", "iron", "otherGold", "neutral");
			case "sweep" -> List.of("plain", "sweeper");
			default -> form.instance() ? List.of("plain", "intrinsic", "template") : List.of("plain", "intrinsic");
		};
	}

	/** Whether the item keeps the defaults and, for 1.21.1's piglin check, its material answers alike: gold's under another holder doesn't. */
	private static boolean keepsItem(String item) {
		return !item.equals("neutral") && !item.equals("sweeper") && !item.equals("intrinsic") && !item.equals("template") && !item.equals("otherGold");
	}

	/** One run of an item site, with a stack of the item (a stand-in that isn't a stack for {@code template}). */
	private static Run runItem(Map<String, byte[]> classes, Form form, String site, String item, boolean answer) throws Exception {
		ClassLoader loader = loader(classes);
		Class<?> hook = loader.loadClass("com.example.Hook");
		hook.getField("answer").set(null, answer);
		hook.getField("bonus").set(null, 1);
		Class<?> itemClass = loader.loadClass("net.minecraft.world.item.Item"), armor = loader.loadClass("net.minecraft.world.item.ArmorItem");
		Class<?> materials = loader.loadClass("net.minecraft.world.item.ArmorMaterials");
		Class<?> materialType = form.srg ? loader.loadClass("net.minecraft.world.item.ArmorMaterial") : loader.loadClass("net.minecraft.core.Holder");
		Object it = switch (item) {
			case "gold", "iron" -> armor.getConstructor(String.class, materialType).newInstance(item, materials.getField(item.toUpperCase()).get(null));
			case "otherGold" -> armor.getConstructor(String.class, materialType).newInstance(item, loader.loadClass("com.example.Ref").getConstructor(String.class)
					.newInstance("gold"));
			case "neutral" -> loader.loadClass("com.example.Neutral").getConstructor().newInstance();
			case "sweeper" -> loader.loadClass("com.example.Sweeper").getConstructor().newInstance();
			case "intrinsic" -> loader.loadClass("com.example.Intrinsic").getConstructor().newInstance();
			default -> itemClass.getConstructor(String.class).newInstance(item);
		};
		if (item.equals("safe")) {
			@SuppressWarnings("unchecked") Set<Object> tags = (Set<Object>) itemClass.getField("tags").get(it);
			tags.add(loader.loadClass("net.minecraft.tags.ItemTags").getField("PIGLIN_SAFE_ARMOR").get(null));
		}
		Class<?> stackClass = loader.loadClass(item.equals("template") ? "net.minecraft.world.item.ItemStackTemplate" : "net.minecraft.world.item.ItemStack");
		Object stack = stackClass.getConstructor(itemClass).newInstance(it);
		Object sharpness = loader.loadClass("com.example.Ref").getConstructor(String.class).newInstance("sharpness");
		@SuppressWarnings("unchecked") Map<Object, Integer> levels = (Map<Object, Integer>) stackClass.getField("levels").get(stack);
		levels.put(sharpness, 2);
		Object answered;
		switch (site) {
			case "piglin" -> {
				Object entity = loader.loadClass(LIVING.replace('/', '.')).getConstructor().newInstance();
				@SuppressWarnings("unchecked") List<Object> armour = (List<Object>) entity.getClass().getField("armor").get(entity);
				armour.add(stack);
				answered = invoke(loader.loadClass(PIGLIN.replace('/', '.')), 1, "", entity);
			}
			case "sweep" -> {
				Object player = loader.loadClass(PLAYER.replace('/', '.')).getConstructor().newInstance();
				player.getClass().getField("main").set(player, stack);
				Object target = loader.loadClass(ENTITY.replace('/', '.')).getConstructor().newInstance();
				answered = form.modern && form.forge ? invoke(player, 4, "doSweepAttack", target, 1F, null, 1F) : invoke(player, 1, form.srg ? "m_5706_" : "attack", target);
			}
			default -> answered = invoke(loader.loadClass(HELPER.replace('/', '.')), 2, "", sharpness, stack);
		}
		return new Run(String.valueOf(answered), log(loader));
	}

	@Test
	void vanillasChecksOfTheStackArePutBackWhereTheItemKeepsTheDefaults(@TempDir Path dir) throws Exception {
		Map<String, String> hookedAnswer = Map.of("piglin", "true", "sweep", "swept 3.0/2.25/3.0", "enchantment", "103");
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form);
			Map<String, byte[]> restored = restored(platform, form);
			for (String site : form.enchantments() ? List.of("piglin", "sweep", "enchantment") : List.of("piglin", "sweep")) {
				// NeoForge 26.x's sweep isn't put back: its attack makes the box for an overload of its own.
				if (site.equals("sweep") && form == Form.NEOFORGE_26) continue;
				for (String item : itemsOf(site, form)) {
					String what = form + " " + site + " " + item;
					// Up to 1.21.1 vanilla's gold check asks the material of armour alone.
					boolean keeps = keepsItem(item) && !(site.equals("piglin") && !form.modern && item.equals("plain"));
					Run before = runItem(platform, form, site, item, false), after = runItem(restored, form, site, item, false);
					assertEquals(before.answer(), after.answer(), what);
					assertEquals(before.plain(), after.plain(), what);
					Run hooked = runItem(hooked(restored, form), form, site, item, true);
					assertEquals(keeps ? 1 : 0, hooked.hooked(), what + " " + hooked.log());
					assertEquals(keeps ? hookedAnswer.get(site) : before.answer(), hooked.answer(), what);
				}
			}
			// The enchantment level's event follows vanilla's level, as the default posts it with the item's.
			if (form.enchantments()) assertEquals(List.of("hooked 2", "event 102"), runItem(hooked(restored, form), form, "enchantment", "plain", true).log());
			// The hand is asked once, as the platform's code asks it, the sweep's box made of the target as vanilla makes it.
			if (form != Form.NEOFORGE_26) assertEquals(List.of("hand"), runItem(restored, form, "sweep", "plain", false).log());
		}
	}

	/** NeoForge 26.x's attack, which makes the sweep's box for an overload of its own, isn't rewritten: vanilla makes the box in doSweepAttack. */
	@Test
	void neoForge26sSweepStaysAsItIs(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = platform(dir, Form.NEOFORGE_26);
		ClassNode player = node(platform.get(PLAYER));
		assertTrue(VanillaCalls.applicable(player).isEmpty());
		assertTrue(VanillaSeams.restore(player).isEmpty());
	}

	/**
	 * What putting the calls back changes, for the native mods' mixins: the platform's call, the method holding it, vanilla's
	 * calls named anew, and the defaults no longer reached, of both platforms' extensions (a class may name neither).
	 */
	@Test
	void whatPuttingTheCallsBackChangesIsToldForNativeMixins(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = platform(dir, Form.NEOFORGE);
		VanillaSeams.Rewrite flying = VanillaSeams.rewriteOf(node(platform.get(FLYING)));
		assertEquals(Set.of("net/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/entity/Entity;)F"), flying.movedCalls());
		assertEquals(Set.of("travel(Z)Ljava/lang/String;"), flying.holders());
		assertTrue(flying.added().contains("net/minecraft/world/level/block/Block;getFriction()F"), flying.added().toString());
		assertEquals(Set.of("getFriction"), flying.others().get("net/neoforged/neoforge/common/extensions/IBlockExtension"));
		assertEquals(Set.of("getFriction"), flying.others().get("net/minecraftforge/common/extensions/IForgeBlock"));
		VanillaSeams.Rewrite place = VanillaSeams.rewriteOf(node(platform.get(BLOCK_ITEM)));
		assertEquals(Set.of("getSoundType", "m_60827_"), place.others().get("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase"));
		VanillaSeams.Rewrite enchantment = VanillaSeams.rewriteOf(node(platform.get(HELPER)));
		assertEquals(Set.of("getEnchantments", "getTagEnchantments"), enchantment.others().get("net/minecraft/world/item/ItemStack"));
		assertTrue(enchantment.added().contains("net/neoforged/neoforge/event/EventHooks;getEnchantmentLevelSpecific(ILnet/minecraft/world/item/ItemStack;"
				+ "Lnet/minecraft/core/Holder;)I"), enchantment.added().toString());
		// A class whose bytes name none of its rows' platform calls isn't parsed.
		assertTrue(VanillaCalls.mayApply(FLYING, platform.get(FLYING)));
		assertTrue(!VanillaCalls.mayApply(FLYING, platform.get("net/minecraft/world/level/Level")));
	}

	/**
	 * A native mod's mixin of the platform's call, of either platform's extension defaults or of the state's sound type,
	 * which Forge's defaults don't ask, keeps the class as the platform has it; one of another method doesn't.
	 */
	@Test
	void aNativeMixinOfWhatMovesKeepsTheClass(@TempDir Path dir) throws Exception {
		Map<String, String> cases = new LinkedHashMap<>();
		cases.put("wrap", "net.minecraft.world.entity.FlyingMob| @WrapOperation(method = \"travel\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/entity/Entity;)F\")) private float w(Object s, Object l, Object p, Object e, Operation<Float> o) { return 0F; }");
		cases.put("default", "net.neoforged.neoforge.common.extensions.IBlockExtension| @Inject(method = \"getFriction\", at = @At(\"HEAD\")) private void h(CallbackInfo ci) { }");
		cases.put("forge", "net.minecraftforge.common.extensions.IForgeBlock| @Inject(method = \"getFriction\", at = @At(\"HEAD\")) private void h(CallbackInfo ci) { }");
		cases.put("other", "net.minecraft.world.level.block.Block| @Inject(method = \"toString\", at = @At(\"HEAD\")) private void h(CallbackInfo ci) { }");
		for (Map.Entry<String, String> e : cases.entrySet()) {
			String[] parts = e.getValue().split("\\|", 2);
			Map<String, String> sources = new HashMap<>(sources(Form.NEOFORGE));
			sources.putAll(Map.of("org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value();"
							+ " String target() default \"\"; }",
					"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }",
					"org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo { }",
					"com/llamalad7/mixinextras/injector/wrapoperation/WrapOperation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
							+ " public @interface WrapOperation { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); }",
					"com/llamalad7/mixinextras/injector/wrapoperation/Operation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
							+ " public interface Operation<R> { R call(Object... args); }",
					// Forge's interface, which a NeoForge class names nowhere: the conflict check reads both platforms' extensions.
					"net/minecraftforge/common/extensions/IForgeBlock.java", "package net.minecraftforge.common.extensions; public interface IForgeBlock { }"));
			sources.put("com/moon/NativeMixin.java", "package com.moon; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
					+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; import com.llamalad7.mixinextras.injector.wrapoperation.*;"
					+ " @Mixin(" + parts[0] + ".class) public abstract class NativeMixin {" + parts[1] + " }");
			Map<String, byte[]> classes = TestSources.compile(dir.resolve(e.getKey()), TestSources.MIXIN_ANNOTATIONS, sources);
			InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight", classes::containsKey, classes::get,
					() -> List.of("com/moon/NativeMixin"))));
			SeamConflicts.Conflict found = SeamConflicts.find(node(classes.get(FLYING)), installed);
			assertEquals(e.getKey().equals("other") ? null : new SeamConflicts.Conflict("moonlight", "com/moon/NativeMixin", true), found, e.getKey());
		}
	}

	/** Leashable's friction, an interface's, isn't put back: an interface can't keep the answers; a hook of it is told why. */
	@Test
	void leashablesFrictionIsNotPutBack(@TempDir Path dir) throws Exception {
		Map<String, String> sources = new HashMap<>(sources(Form.NEOFORGE));
		sources.put("net/minecraft/world/entity/Leashable.java", "package net.minecraft.world.entity; public interface Leashable { static float angularFriction(Entity e) {"
				+ " return e.level.getBlockState(e.below).getFriction(e.level, e.below, e) * 0.91F; } }");
		Map<String, byte[]> classes = TestSources.compile(dir, sources);
		ClassNode leashable = node(classes.get("net/minecraft/world/entity/Leashable"));
		assertTrue(VanillaSeams.restore(leashable).isEmpty());
		String why = VanillaSeams.callNotPutBack(leashable, "angularFriction", "(Lnet/minecraft/world/entity/Entity;)F", "net/minecraft/world/level/block/Block", "getFriction");
		assertTrue(why != null && why.endsWith("(SEAM-8)."), why);
	}

	private static Object invoke(Object target, int params, String name, Object... args) throws Exception {
		Class<?> type = target instanceof Class<?> c ? c : target.getClass();
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
				if (m.getParameterCount() != params || m.getName().startsWith("fabricated$") || m.isSynthetic() || !name.isEmpty() && !m.getName().equals(name)
						|| java.lang.reflect.Modifier.isAbstract(m.getModifiers()) || m.getName().equals("mainHand") || m.getName().equals("getBoundingBox")
						|| m.getName().equals("m_20191_") || m.getName().equals("toString")) continue;
				if (params == 1 && m.getParameterTypes()[0] == boolean.class && !(args[0] instanceof Boolean)) continue;
				m.setAccessible(true);
				return m.invoke(target instanceof Class<?> ? null : target, args);
			}
		}
		throw new AssertionError("no method of " + params + " in " + type);
	}

	@SuppressWarnings("unchecked")
	private static List<String> log(ClassLoader loader) throws Exception {
		return new ArrayList<>((List<String>) loader.loadClass("com.example.Hook").getField("LOG").get(null));
	}

	private static MethodNode method(ClassNode node, String name) {
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(ExtensionCallsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}
}
