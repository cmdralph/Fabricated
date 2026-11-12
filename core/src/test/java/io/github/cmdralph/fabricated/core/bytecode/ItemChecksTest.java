package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-6: vanilla's item checks, {@code stack.is(Items.SHEARS)} and its rod's and shield's, which NeoForge and Forge make
 * through the stack's {@code canPerformAction} of an action, put back for the stacks whose classes keep that default; and
 * the client's {@code stack.is(Items.ELYTRA)}, which they make through the elytra layer's {@code shouldRender}, for the
 * layers keeping it. Each fixture is the platform's code in its form (NeoForge 21.x's, NeoForge 26.x's, whose stack asks
 * through its ItemInstance and whose vanilla check is {@code TypedInstance.is(T)}, Forge's, Forge 47's under SRG's names),
 * against stand-ins of the game whose calls log what they do. The class rewritten does what the platform's did for an
 * empty hand, an item keeping the default, vanilla's shears, rod and shield, which override it, and items of the
 * platform's kind overriding it either way; a hook of the check put back runs where the item keeps the default, and only
 * there.
 */
public class ItemChecksTest {
	private static final String TRIPWIRE = "net/minecraft/world/level/block/TripWireBlock";
	private static final String BEEHIVE = "net/minecraft/world/level/block/BeehiveBlock";
	private static final String FISHING_HOOK = "net/minecraft/world/entity/projectile/FishingHook";
	private static final String PLAYER = "net/minecraft/world/entity/player/Player";
	private static final String LAYER = "net/minecraft/client/renderer/entity/layers/ElytraLayer";
	private static final String STACK = "net/minecraft/world/item/ItemStack";

	/** A platform's form: its extensions and actions, its stack's, and the names its game runs with. */
	enum Form {
		NEOFORGE(false, false, false), NEOFORGE_26(false, true, false), FORGE(true, false, false), FORGE_47(true, false, true);

		final boolean forge, typed, srg;

		Form(boolean forge, boolean typed, boolean srg) {
			this.forge = forge;
			this.typed = typed;
			this.srg = srg;
		}

		String ext() {
			return forge ? "net.minecraftforge.common.extensions" : "net.neoforged.neoforge.common.extensions";
		}

		String action() {
			return forge ? "net.minecraftforge.common.ToolAction" : "net.neoforged.neoforge.common.ItemAbility";
		}

		String actions() {
			return forge ? "net.minecraftforge.common.ToolActions" : "net.neoforged.neoforge.common.ItemAbilities";
		}

		String itemExt() {
			return forge ? "IForgeItem" : "IItemExtension";
		}

		/** What the item's extension method takes as the stack. */
		String stackParam() {
			return typed ? "net.minecraft.world.item.ItemInstance" : "net.minecraft.world.item.ItemStack";
		}
	}

	private static final Map<String, String> SRG = new LinkedHashMap<>();
	static {
		for (String[] names : new String[][] {{"getItem", "m_41720_"}, {"is", "m_150930_"}, {"SHEARS", "f_42574_"}, {"FISHING_ROD", "f_42523_"},
				{"SHIELD", "f_42740_"}, {"ELYTRA", "f_42741_"}, {"playerWillDestroy", "m_5707_"}, {"useItemOn", "m_6227_"}, {"shouldStopFishing", "m_37136_"},
				{"hurtCurrentlyUsedShield", "m_7909_"}, {"render", "m_6494_"}}) {
			SRG.put("$" + names[0] + "$", names[1]);
		}
	}

	/** The game's stand-ins and the platform's classes in the form; {@code interfaceLayer}, Forge 52's elytra layer, asking its interface. */
	static Map<String, String> sources(Form form, boolean interfaceLayer) {
		Map<String, String> sources = new HashMap<>();
		String ext = form.ext(), action = form.action(), actions = form.actions();
		sources.put(action.replace('.', '/') + ".java", "package " + action.substring(0, action.lastIndexOf('.')) + "; public final class "
				+ action.substring(action.lastIndexOf('.') + 1) + " { final String name; public " + action.substring(action.lastIndexOf('.') + 1)
				+ "(String name) { this.name = name; } public String toString() { return name; } }");
		String type = action.substring(action.lastIndexOf('.') + 1);
		StringBuilder fields = new StringBuilder();
		for (String name : List.of("SHEARS_HARVEST", "SHEARS_CARVE", "SHEARS_DISARM", "FISHING_ROD_CAST", "SHIELD_BLOCK", "SWORD_SWEEP")) {
			fields.append(" public static final ").append(type).append(' ').append(name).append(" = new ").append(type).append("(\"").append(name).append("\");");
		}
		sources.put(actions.replace('.', '/') + ".java", "package " + actions.substring(0, actions.lastIndexOf('.')) + "; public final class "
				+ actions.substring(actions.lastIndexOf('.') + 1) + " {" + fields + " public static final java.util.Set<" + type + "> SHEARS = java.util.Set.of(SHEARS_HARVEST,"
				+ " SHEARS_CARVE, SHEARS_DISARM); }");
		sources.put("net/minecraft/world/item/Item.java", "package net.minecraft.world.item; public class Item implements " + ext + "." + form.itemExt() + " {"
				+ " final String name; public Item(String name) { this.name = name; } public String toString() { return name; } }");
		// The item's default answers no but for a sword's sweep, as Forge 61 on and NeoForge 21.11 on have it; a method naming a
		// class the game lacks as it runs, as Forge 66's extensions name client classes.
		sources.put(ext.replace('.', '/') + "/" + form.itemExt() + ".java", "package " + ext + "; public interface " + form.itemExt() + " {"
				+ " default boolean canPerformAction(" + form.stackParam() + " stack, " + action + " action) { return action == " + actions + ".SWORD_SWEEP"
				+ " && stack.toString().equals(\"sword\"); } default void drawn(com.example.ClientOnly client) { } }");
		sources.put("com/example/ClientOnly.java", "package com.example; public class ClientOnly { }");
		if (form.typed) {
			sources.put("net/minecraft/core/TypedInstance.java", "package net.minecraft.core; public interface TypedInstance<T> { T value();"
					+ " default boolean is(T type) { if (!com.example.Hook.quiet) com.example.Hook.LOG.add(\"is \" + type); return value() == type; } }");
			sources.put("net/minecraft/world/item/ItemInstance.java", "package net.minecraft.world.item; public interface ItemInstance extends"
					+ " net.minecraft.core.TypedInstance<Item>, " + ext + ".ItemInstanceExtension { }");
			sources.put(ext.replace('.', '/') + "/ItemInstanceExtension.java", "package " + ext + "; public interface ItemInstanceExtension {"
					+ " default boolean canPerformAction(" + action + " action) { net.minecraft.world.item.ItemInstance self = (net.minecraft.world.item.ItemInstance) this;"
					+ " return self.value().canPerformAction(self, action); } }");
			sources.put("net/minecraft/world/item/ItemStack.java", "package net.minecraft.world.item; public final class ItemStack implements ItemInstance {"
					+ " final Item item; public ItemStack(Item item) { this.item = item; } public Item value() { return item; } public Item $getItem$() { return item; }"
					+ " public boolean isEmpty() { return item == Items.AIR; } public String toString() { return item.toString(); } }");
		} else {
			String stackExt = form.forge ? "IForgeItemStack" : "IItemStackExtension";
			sources.put(ext.replace('.', '/') + "/" + stackExt + ".java", "package " + ext + "; public interface " + stackExt + " {"
					+ " default boolean canPerformAction(" + action + " action) { net.minecraft.world.item.ItemStack self = (net.minecraft.world.item.ItemStack) this;"
					+ " return self.$getItem$().canPerformAction(self, action); } }");
			sources.put("net/minecraft/world/item/ItemStack.java", "package net.minecraft.world.item; public final class ItemStack implements " + ext + "." + stackExt
					+ " { final Item item; public ItemStack(Item item) { this.item = item; } public Item $getItem$() { return item; }"
					+ " public boolean isEmpty() { return item == Items.AIR; } public boolean $is$(Item type) { if (!com.example.Hook.quiet) com.example.Hook.LOG.add(\"is \" + type);"
					+ " return item == type; } public String toString() { return item.toString(); } }");
		}
		String stackType = form.stackParam();
		// Vanilla's tools, overriding the default with the actions they can do.
		sources.put("net/minecraft/world/item/ShearsItem.java", "package net.minecraft.world.item; public class ShearsItem extends Item { public ShearsItem(String name) {"
				+ " super(name); } @Override public boolean canPerformAction(" + stackType + " stack, " + action + " action) { return " + actions + ".SHEARS.contains(action); } }");
		sources.put("net/minecraft/world/item/FishingRodItem.java", "package net.minecraft.world.item; public class FishingRodItem extends Item { public FishingRodItem("
				+ "String name) { super(name); } @Override public boolean canPerformAction(" + stackType + " stack, " + action + " action) { return action == " + actions
				+ ".FISHING_ROD_CAST; } }");
		sources.put("net/minecraft/world/item/ShieldItem.java", "package net.minecraft.world.item; public class ShieldItem extends Item { public ShieldItem(String name) {"
				+ " super(name); } @Override public boolean canPerformAction(" + stackType + " stack, " + action + " action) { return action == " + actions + ".SHIELD_BLOCK; } }");
		sources.put("net/minecraft/world/item/Items.java", "package net.minecraft.world.item; public class Items { public static final Item AIR = new Item(\"air\");"
				+ " public static final Item STICK = new Item(\"stick\"); public static final Item SWORD = new Item(\"sword\");"
				+ " public static final Item $SHEARS$ = new ShearsItem(\"shears\"); public static final Item $FISHING_ROD$ = new FishingRodItem(\"rod\");"
				+ " public static final Item $SHIELD$ = new ShieldItem(\"shield\"); public static final Item $ELYTRA$ = new Item(\"elytra\"); }");
		// A platform mod's tools: one doing every action, one of vanilla's shears' class doing none.
		sources.put("com/example/Multitool.java", "package com.example; public class Multitool extends net.minecraft.world.item.Item { public Multitool() { super(\"multitool\"); }"
				+ " @Override public boolean canPerformAction(" + stackType + " stack, " + action + " action) { Hook.LOG.add(\"multitool asked \" + action); return true; } }");
		sources.put("com/example/Blunt.java", "package com.example; public class Blunt extends net.minecraft.world.item.ShearsItem { public Blunt() { super(\"blunt\"); }"
				+ " @Override public boolean canPerformAction(" + stackType + " stack, " + action + " action) { Hook.LOG.add(\"blunt asked \" + action); return false; } }");
		sources.put("com/example/Hook.java", "package com.example; public class Hook { public static final java.util.List<String> LOG = new java.util.ArrayList<>();"
				+ " public static boolean answer = true, quiet;"
				+ " public static boolean answer(boolean vanilla) { LOG.add(\"answered \" + vanilla); return answer; } }");
		// Checks made many times over, as the game makes them, for the allocation test.
		sources.put("com/example/Loop.java", "package com.example; import net.minecraft.world.item.ItemStack; public class Loop {"
				+ " public static int harvest(net.minecraft.world.level.block.BeehiveBlock block, ItemStack stack, int times) { int n = 0;"
				+ " for (int i = 0; i < times; i++) if (block.$useItemOn$(stack, false).length() == 9) n++; return n; }"
				+ " public static int draw(net.minecraft.client.renderer.entity.layers.ElytraLayer<net.minecraft.world.entity.LivingEntity> layer,"
				+ " net.minecraft.world.entity.LivingEntity entity, ItemStack stack, int times) { for (int i = 0; i < times; i++) layer.$render$(entity, stack);"
				+ " return times; } }");
		sources.put(PLAYER + ".java", "package net.minecraft.world.entity.player; import net.minecraft.world.item.ItemStack; public class Player {"
				+ " public ItemStack useItem, main, off; public Player(ItemStack main, ItemStack off) { this.main = main; this.off = off; this.useItem = main; }"
				+ " public ItemStack getMainHandItem() { return main; } public ItemStack getOffhandItem() { return off; }"
				+ " public void $hurtCurrentlyUsedShield$(float damage) { if (this.useItem.canPerformAction(" + actions + ".SHIELD_BLOCK)) com.example.Hook.LOG.add(\"shield hurt\"); }"
				+ " public boolean sweeps() { return main.canPerformAction(" + actions + ".SWORD_SWEEP); } }");
		// The tripwire's disarming: the stack is a call's result, asked once.
		sources.put(TRIPWIRE + ".java", "package net.minecraft.world.level.block; import net.minecraft.world.entity.player.Player; public class TripWireBlock {"
				+ " public boolean $playerWillDestroy$(Player player) { if (!player.getMainHandItem().isEmpty() && player.getMainHandItem().canPerformAction(" + actions
				+ ".SHEARS_DISARM)) { com.example.Hook.LOG.add(\"disarmed\"); return true; } return false; } }");
		// The beehive's harvest, in a conditional, after another action's call, which stays as it is.
		sources.put(BEEHIVE + ".java", "package net.minecraft.world.level.block; import net.minecraft.world.item.ItemStack; public class BeehiveBlock {"
				+ " public String $useItemOn$(ItemStack stack, boolean carving) { if (carving && stack.canPerformAction(" + actions + ".SHEARS_CARVE)) return \"carved\";"
				+ " return stack.canPerformAction(" + actions + ".SHEARS_HARVEST) ? \"harvested\" : \"none\"; } }");
		// The fishing hook's two hands, each asked.
		sources.put(FISHING_HOOK + ".java", "package net.minecraft.world.entity.projectile; import net.minecraft.world.entity.player.Player;"
				+ " import net.minecraft.world.item.ItemStack; public class FishingHook { public boolean $shouldStopFishing$(Player player) {"
				+ " ItemStack main = player.getMainHandItem(); ItemStack off = player.getOffhandItem(); boolean a = main.canPerformAction(" + actions + ".FISHING_ROD_CAST);"
				+ " boolean b = off.canPerformAction(" + actions + ".FISHING_ROD_CAST); com.example.Hook.LOG.add(\"rod \" + a + \" \" + b); return !a && !b; } }");
		// The elytra layer, asking its own method (NeoForge 21.1, Forge 47) or its interface's (Forge 52); a platform mod's layer overriding it.
		sources.put("net/minecraft/world/entity/LivingEntity.java", "package net.minecraft.world.entity; public class LivingEntity { }");
		String should = " boolean shouldRender(net.minecraft.world.item.ItemStack stack, T entity) { return stack.$getItem$() == net.minecraft.world.item.Items.$ELYTRA$; }";
		sources.put(LAYER + ".java", "package net.minecraft.client.renderer.entity.layers; public class ElytraLayer<T extends net.minecraft.world.entity.LivingEntity>"
				+ (interfaceLayer ? " implements net.minecraftforge.client.extensions.IForgeElytraLayer<T> {" : " {") + " public void $render$(T entity,"
				+ " net.minecraft.world.item.ItemStack stack) { if (this.shouldRender(stack, entity)) com.example.Hook.LOG.add(\"drawn\"); }"
				+ (interfaceLayer ? "" : " public" + should) + " }");
		if (interfaceLayer) {
			sources.put("net/minecraftforge/client/extensions/IForgeElytraLayer.java", "package net.minecraftforge.client.extensions; public interface"
					+ " IForgeElytraLayer<T> { default" + should + " }");
		}
		sources.put("com/example/OwnLayer.java", "package com.example; public class OwnLayer extends net.minecraft.client.renderer.entity.layers.ElytraLayer<"
				+ "net.minecraft.world.entity.LivingEntity> { @Override public boolean shouldRender(net.minecraft.world.item.ItemStack stack,"
				+ " net.minecraft.world.entity.LivingEntity entity) { Hook.LOG.add(\"own layer\"); return true; } }");
		Map<String, String> names = form.srg ? SRG : Map.of();
		sources.replaceAll((file, text) -> {
			for (Map.Entry<String, String> e : SRG.entrySet()) text = text.replace(e.getKey(), names.getOrDefault(e.getKey(), e.getKey().substring(1, e.getKey().length() - 1)));
			return text;
		});
		return sources;
	}

	/** NeoForge 21.x's classes, as the fixtures have them, for pre-flight's tests. */
	public static Map<String, String> neoForgeSources() {
		return sources(Form.NEOFORGE, false);
	}

	/** Forge 47's, under SRG's names. */
	public static Map<String, String> forge47Sources() {
		return sources(Form.FORGE_47, false);
	}

	private static Map<String, byte[]> platform(Path dir, Form form, boolean interfaceLayer) throws Exception {
		return TestSources.compileWithLocals(dir, sources(form, interfaceLayer));
	}

	/**
	 * The classes with the game classes put back, as they load; for NeoForge 26.x's form the classes are rewritten as Java 25's,
	 * as that game's are, and loaded as the JVM running the tests can.
	 */
	private static Map<String, byte[]> restored(Map<String, byte[]> platform, Form form, String... names) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		for (String name : names) {
			ClassNode node = node(platform.get(name));
			int version = node.version;
			if (form.typed) node.version = 69;
			VanillaSeams.Restoring done = VanillaSeams.restoreWhole(node);
			assertNull(done.failure());
			assertTrue(!done.restored().isEmpty(), form + " " + name);
			node.version = version;
			restored.put(name, write(node));
		}
		return restored;
	}

	private static final List<String> HANDS = List.of("AIR", "STICK", "SWORD", "SHEARS", "FISHING_ROD", "SHIELD", "com.example.Multitool", "com.example.Blunt");

	/** Whether the hand's item keeps the platform's default: a plain item, the empty hand's among them. */
	private static boolean keeps(String hand) {
		return hand.equals("AIR") || hand.equals("STICK") || hand.equals("SWORD");
	}

	/**
	 * How many times the check asks vanilla's question once put back: of each stack whose item keeps the default (the
	 * tripwire not of an empty hand, the fishing hook of the empty off hand too).
	 */
	private static int vanillaChecks(String check, String hand) {
		int own = keeps(hand) && !(check.equals("tripwire") && hand.equals("AIR")) ? 1 : 0;
		return check.equals("fishing") ? own + 1 : own;
	}

	@Test
	void vanillasItemChecksArePutBackWhereTheItemKeepsThePlatformsDefault(@TempDir Path dir) throws Exception {
		Map<String, String> success = Map.of("tripwire", "disarmed", "beehive", "answer harvested", "carving", "answer harvested", "fishing", "rod true true",
				"shield", "shield hurt");
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form, false);
			Map<String, byte[]> restored = restored(platform, form, TRIPWIRE, BEEHIVE, FISHING_HOOK, PLAYER);
			for (String hand : HANDS) {
				for (String check : List.of("tripwire", "beehive", "carving", "fishing", "shield")) {
					String what = form + " " + hand + " " + check;
					// Every item, unhooked, does what the platform's code made it do; vanilla's check is made of the plain ones alone.
					List<String> before = run(platform, check, hand, false), after = run(restored, check, hand, false);
					List<String> asked = after.stream().filter(l -> l.startsWith("is ")).toList();
					after.removeIf(l -> l.startsWith("is "));
					assertEquals(before, after, what);
					int vanilla = vanillaChecks(check, hand);
					assertEquals(vanilla, asked.size(), what + " " + asked);
					// A hook of the check put back, answering yes, runs for those alone, and decides for them.
					List<String> hooked = run(restored, check, hand, true);
					assertEquals(vanilla, hooked.stream().filter(l -> l.startsWith("answered false")).count(), what + " " + hooked);
					if (keeps(hand) && !hand.equals("AIR")) assertTrue(hooked.contains(success.get(check)), what + " " + hooked);
				}
			}
			// Pre-flight reads what was put back: vanilla's call, the platform's it stands in for, in each place.
			String is = form.typed ? "is(Ljava/lang/Object;)Z" : (form.srg ? "m_150930_" : "is") + "(Lnet/minecraft/world/item/Item;)Z";
			for (String name : List.of(TRIPWIRE, BEEHIVE, FISHING_HOOK, PLAYER)) {
				List<VanillaSeams.Restored> seams = VanillaSeams.restoredIn(node(restored.get(name)));
				assertEquals(List.of(STACK + ";" + is + " SEAM-6"), seams.stream().map(r -> r.owner() + ";" + r.name() + r.desc() + " " + r.finding()).toList(),
						form + " " + name);
				assertEquals("the item's " + form.itemExt() + ".canPerformAction", seams.get(0).through());
				assertTrue(seams.get(0).note().contains("an item overriding it (vanilla's "), seams.get(0).note());
			}
			assertEquals(2, VanillaSeams.putBackPlaces(method(node(restored.get(FISHING_HOOK)), "shouldStopFishing", form), STACK, is.substring(0, is.indexOf('(')),
					is.substring(is.indexOf('('))));
			// The other action's call is left as the platform has it.
			assertTrue(calls(method(node(restored.get(BEEHIVE)), "useItemOn", form), "canPerformAction") == 2, form.name());
			assertTrue(calls(method(node(restored.get(PLAYER)), "sweeps", null), "canPerformAction") == 1, form.name());
		}
	}

	/**
	 * The elytra layer: vanilla's check of the stack, put back for the layers whose class keeps {@code shouldRender}, which
	 * NeoForge 21.1 and Forge 47 declare on the layer and Forge 52 on its interface; a layer overriding it draws as the
	 * platform has it, without the hook.
	 */
	@Test
	void vanillasElytraCheckIsPutBackWhereTheLayerKeepsThePlatformsMethod(@TempDir Path dir) throws Exception {
		for (Form form : List.of(Form.NEOFORGE, Form.FORGE, Form.FORGE_47)) {
			for (boolean interfaceLayer : List.of(false, true)) {
				if (interfaceLayer && form != Form.FORGE) continue;
				String what = form + (interfaceLayer ? " interface" : "");
				Map<String, byte[]> platform = platform(dir.resolve(what.replace(' ', '-')), form, interfaceLayer);
				Map<String, byte[]> restored = restored(platform, form, LAYER);
				for (String layer : List.of(LAYER.replace('/', '.'), "com.example.OwnLayer")) {
					for (String item : List.of("ELYTRA", "STICK")) {
						List<String> before = draw(platform, layer, item, false), after = draw(restored, layer, item, false);
						boolean vanilla = !layer.endsWith("OwnLayer");
						assertEquals(vanilla ? 1 : 0, after.stream().filter(l -> l.startsWith("is ")).count(), what + " " + layer + " " + after);
						after.removeIf(l -> l.startsWith("is "));
						assertEquals(before, after, what + " " + layer + " " + item);
						// A hook answering no hides vanilla's layer, never the platform mod's.
						List<String> hooked = draw(restored, layer, item, true);
						assertEquals(vanilla, hooked.stream().anyMatch(l -> l.startsWith("answered")), what + " " + hooked);
						assertEquals(!vanilla, hooked.contains("drawn"), what + " " + hooked);
					}
				}
				List<VanillaSeams.Restored> seams = VanillaSeams.restoredIn(node(restored.get(LAYER)));
				assertEquals(1, seams.size(), what);
				assertEquals("the layer's " + (interfaceLayer ? "IForgeElytraLayer" : "ElytraLayer") + ".shouldRender", seams.get(0).through(), what);
				assertEquals("SEAM-6", seams.get(0).finding());
			}
		}
	}

	/** One check of the game's code, by a player holding the hand's item (an empty hand for AIR), hooked or not. */
	private static List<String> run(Map<String, byte[]> classes, String check, String hand, boolean hook) throws Exception {
		ClassLoader loader = loader(hook ? answering(classes, false) : classes);
		Object stack = stack(loader, hand);
		Class<?> stackClass = loader.loadClass(STACK.replace('/', '.'));
		Object player = loader.loadClass(PLAYER.replace('/', '.')).getConstructor(stackClass, stackClass).newInstance(stack, stack(loader, "AIR"));
		Object answer = switch (check) {
			case "tripwire" -> invoke(loader, TRIPWIRE, 1, player);
			case "beehive" -> invoke(loader, BEEHIVE, 2, stack, false);
			case "carving" -> invoke(loader, BEEHIVE, 2, stack, true);
			case "fishing" -> invoke(loader, FISHING_HOOK, 1, player);
			default -> {
				for (java.lang.reflect.Method m : player.getClass().getMethods()) {
					if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class) m.invoke(player, 1f);
				}
				yield null;
			}
		};
		List<String> log = log(loader);
		log.add("answer " + answer);
		return log;
	}

	/** The layer drawing for a stack of the item, hooked or not (the hook answering no). */
	private static List<String> draw(Map<String, byte[]> classes, String layer, String item, boolean hook) throws Exception {
		ClassLoader loader = loader(hook ? answering(classes, true) : classes);
		Object instance = loader.loadClass(layer).getConstructor().newInstance();
		Object entity = loader.loadClass("net.minecraft.world.entity.LivingEntity").getConstructor().newInstance();
		Class<?> layerClass = loader.loadClass(LAYER.replace('/', '.'));
		if (hook) loader.loadClass("com.example.Hook").getField("answer").set(null, false);
		for (java.lang.reflect.Method m : layerClass.getMethods()) {
			if (m.getReturnType() == void.class && m.getParameterCount() == 2 && m.getParameterTypes()[1].getName().endsWith("ItemStack")) m.invoke(instance, entity, stack(loader, item));
		}
		return log(loader);
	}

	/** A stack of {@code Items}' field, or of a mod's item class. */
	private static Object stack(ClassLoader loader, String hand) throws Exception {
		Object item;
		if (hand.contains(".")) {
			item = loader.loadClass(hand).getConstructor().newInstance();
		} else {
			Class<?> items = loader.loadClass("net.minecraft.world.item.Items");
			java.lang.reflect.Field field;
			try {
				field = items.getField(hand);
			} catch (NoSuchFieldException e) {
				field = items.getField(SRG.get("$" + hand + "$"));
			}
			item = field.get(null);
		}
		return loader.loadClass(STACK.replace('/', '.')).getConstructor(loader.loadClass("net.minecraft.world.item.Item")).newInstance(item);
	}

	private static Object invoke(ClassLoader loader, String owner, int params, Object... args) throws Exception {
		Class<?> type = loader.loadClass(owner.replace('/', '.'));
		Object instance = type.getConstructor().newInstance();
		for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
			if (m.getParameterCount() == params && !m.getName().startsWith("fabricated$") && !m.isSynthetic()) return m.invoke(instance, args);
		}
		throw new AssertionError("no method in " + owner);
	}

	@SuppressWarnings("unchecked")
	private static List<String> log(ClassLoader loader) throws Exception {
		return new ArrayList<>((List<String>) loader.loadClass("com.example.Hook").getField("LOG").get(null));
	}

	/**
	 * The classes with each call of vanilla's check put back made through a hook logging vanilla's answer, as a Fabric mod's
	 * wrapper of it would: the stack's {@code is} calls outside the helpers.
	 */
	private static Map<String, byte[]> answering(Map<String, byte[]> classes, boolean layer) {
		Map<String, byte[]> hooked = new HashMap<>(classes);
		for (String name : layer ? List.of(LAYER) : List.of(TRIPWIRE, BEEHIVE, FISHING_HOOK, PLAYER)) {
			ClassNode node = node(classes.get(name));
			for (MethodNode method : node.methods) {
				if (method.name.startsWith("fabricated$")) continue;
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof MethodInsnNode call && call.owner.equals(STACK) && call.desc.endsWith(")Z") && !call.name.equals("canPerformAction")
							&& !call.name.equals("isEmpty")) {
						method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "answer", "(Z)Z", false));
					}
				}
			}
			hooked.put(name, write(node));
		}
		return hooked;
	}

	/**
	 * Whether an item keeps the default is asked once for each class, in the class's field; asked from many threads at once,
	 * as the server's and the client's threads may, every answer is the platform's and each class is asked of once.
	 */
	@Test
	void theAnswersAreKeptForEachClassAcrossThreads(@TempDir Path dir) throws Exception {
		Map<String, byte[]> restored = restored(platform(dir, Form.NEOFORGE, false), Form.NEOFORGE, BEEHIVE);
		ClassLoader loader = loader(answering(restored, false));
		Class<?> beehive = loader.loadClass(BEEHIVE.replace('/', '.'));
		Object block = beehive.getConstructor().newInstance();
		java.lang.reflect.Method use = beehive.getMethod("useItemOn", loader.loadClass(STACK.replace('/', '.')), boolean.class);
		Map<String, Object> stacks = new LinkedHashMap<>();
		for (String hand : HANDS) stacks.put(hand, stack(loader, hand));
		Map<String, Object> expected = new HashMap<>();
		for (Map.Entry<String, Object> e : stacks.entrySet()) expected.put(e.getKey(), keeps(e.getKey()) || e.getKey().endsWith("Multitool") || e.getKey().equals("SHEARS")
				? "harvested" : "none");
		ExecutorService pool = Executors.newFixedThreadPool(8);
		AtomicInteger wrong = new AtomicInteger();
		try {
			for (int round = 0; round < 20; round++) {
				CyclicBarrier start = new CyclicBarrier(8);
				List<Future<?>> done = new ArrayList<>();
				for (int t = 0; t < 8; t++) {
					done.add(pool.submit(() -> {
						start.await();
						for (int i = 0; i < 200; i++) {
							for (Map.Entry<String, Object> e : stacks.entrySet()) {
								if (!expected.get(e.getKey()).equals(use.invoke(block, e.getValue(), false))) wrong.incrementAndGet();
							}
						}
						return null;
					}));
				}
				for (Future<?> f : done) f.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
		assertEquals(0, wrong.get());
		java.lang.reflect.Field field = beehive.getDeclaredField("fabricated$overridingAction");
		field.setAccessible(true);
		Map<String, Boolean> known = new HashMap<>();
		((Map<?, ?>) field.get(null)).forEach((type, overrides) -> known.put(((Class<?>) type).getName(), (Boolean) overrides));
		assertEquals(Map.of(STACK.replace('/', '.'), false, "net.minecraft.world.item.Item", false, "net.minecraft.world.item.ShearsItem", true,
				"net.minecraft.world.item.FishingRodItem", true, "net.minecraft.world.item.ShieldItem", true, "com.example.Multitool", true, "com.example.Blunt", true), known);
	}

	/**
	 * A check of a stack, or of a layer, whose classes were asked of before makes nothing: the answers kept are read first,
	 * the lookup's arrays made only for a class asked of the first time. The checks run every tick (a fishing hook's) and
	 * every frame (the elytra layer's).
	 */
	@Test
	void aCheckOfClassesAskedBeforeMakesNothing(@TempDir Path dir) throws Exception {
		ClassLoader loader = loader(restored(platform(dir, Form.NEOFORGE, false), Form.NEOFORGE, BEEHIVE, LAYER));
		loader.loadClass("com.example.Hook").getField("quiet").set(null, true);
		Class<?> loop = loader.loadClass("com.example.Loop"), stackClass = loader.loadClass(STACK.replace('/', '.'));
		Class<?> layerClass = loader.loadClass(LAYER.replace('/', '.')), living = loader.loadClass("net.minecraft.world.entity.LivingEntity");
		Object block = loader.loadClass(BEEHIVE.replace('/', '.')).getConstructor().newInstance();
		Object layer = layerClass.getConstructor().newInstance(), entity = living.getConstructor().newInstance();
		java.lang.reflect.Method harvest = loop.getMethod("harvest", block.getClass(), stackClass, int.class);
		java.lang.reflect.Method draw = loop.getMethod("draw", layerClass, living, stackClass, int.class);
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().threadId();
		for (String hand : List.of("STICK", "SHEARS")) {
			Object stack = stack(loader, hand);
			for (boolean measured : List.of(false, true)) {
				long before = threads.getThreadAllocatedBytes(thread);
				harvest.invoke(null, block, stack, 100_000);
				draw.invoke(null, layer, entity, stack, 100_000);
				long made = threads.getThreadAllocatedBytes(thread) - before;
				if (measured) assertTrue(made < 4096, hand + ": 200,000 checks made " + made + " bytes");
			}
		}
	}

	/** Translation reads classes in parallel: every class put back at once is the same, for each platform's form. */
	@Test
	void classesPutBackInParallelAreTheSame(@TempDir Path dir) throws Exception {
		List<Callable<byte[]>> restores = new ArrayList<>();
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form, false);
			for (String name : List.of(TRIPWIRE, FISHING_HOOK, LAYER)) {
				restores.add(() -> {
					ClassNode node = node(platform.get(name));
					assertTrue(!VanillaSeams.restore(node).isEmpty(), form + " " + name);
					return write(node);
				});
			}
		}
		List<byte[]> references = new ArrayList<>();
		for (Callable<byte[]> restore : restores) references.add(restore.call());
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			for (int round = 0; round < 50; round++) {
				CyclicBarrier start = new CyclicBarrier(8);
				List<Future<byte[]>> copies = new ArrayList<>();
				for (int i = 0; i < 8; i++) {
					Callable<byte[]> restore = restores.get((round + i) % restores.size());
					copies.add(pool.submit(() -> {
						start.await();
						return restore.call();
					}));
				}
				for (int i = 0; i < copies.size(); i++) {
					assertArrayEquals(references.get((round + i) % restores.size()), copies.get(i).get(60, TimeUnit.SECONDS), "round " + round + ", copy " + i);
				}
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void whatPuttingTheChecksBackChangesIsToldForNativeMixins(@TempDir Path dir) throws Exception {
		Map<String, byte[]> platform = platform(dir, Form.NEOFORGE, false);
		VanillaSeams.Rewrite tripwire = VanillaSeams.rewriteOf(node(platform.get(TRIPWIRE)));
		assertEquals(Set.of(STACK + ";canPerformAction(Lnet/neoforged/neoforge/common/ItemAbility;)Z"), tripwire.movedCalls());
		assertEquals(Set.of("playerWillDestroy(L" + PLAYER + ";)Z"), tripwire.holders());
		assertTrue(tripwire.added().containsAll(Set.of(STACK + ";is(Lnet/minecraft/world/item/Item;)Z", "net/minecraft/world/item/Items;SHEARS:Lnet/minecraft/world/item/Item;",
				STACK + ";getItem()Lnet/minecraft/world/item/Item;", "net/neoforged/neoforge/common/extensions/IItemExtension")), tripwire.added().toString());
		assertEquals(Map.of("net/neoforged/neoforge/common/extensions/IItemExtension", Set.of("canPerformAction"),
				"net/neoforged/neoforge/common/extensions/IItemStackExtension", Set.of("canPerformAction"),
				"net/neoforged/neoforge/common/extensions/ItemInstanceExtension", Set.of("canPerformAction"), STACK, Set.of("is", "m_150930_"),
				"net/minecraft/core/TypedInstance", Set.of("is")), tripwire.others());
		assertTrue(!tripwire.constants());
		// The layer: its own method, no longer reached for the layers keeping it.
		VanillaSeams.Rewrite layer = VanillaSeams.rewriteOf(node(platform.get(LAYER)));
		assertEquals(Set.of("shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"), layer.bypassed());
		assertEquals(Set.of("shouldRender"), layer.others().get(LAYER));
		// The game's view of a class whose bytes name no action its rows ask of: read as it is, not parsed.
		byte[] sweeping = platform.get(PLAYER).clone();
		ClassNode node = node(sweeping);
		node.methods.removeIf(m -> m.name.equals("hurtCurrentlyUsedShield"));
		byte[] noShield = write(node);
		assertTrue(!VanillaCalls.mayApply(PLAYER, noShield));
		assertSame(noShield, VanillaSeams.over(name -> name.equals(PLAYER) ? noShield : platform.get(name)).read(PLAYER));
		assertTrue(VanillaCalls.mayApply(PLAYER, platform.get(PLAYER)));
		assertTrue(VanillaCalls.mayApply("net/minecraft/server/level/ServerPlayerGameMode", new byte[0]));
	}

	/**
	 * A native mod's mixin of the platform's call, of the item extensions' method, or of vanilla's check itself keeps the
	 * class as the platform has it; one of another of the stack's methods doesn't.
	 */
	@Test
	void aNativeMixinOfWhatMovesKeepsTheClass(@TempDir Path dir) throws Exception {
		Map<String, String> cases = new LinkedHashMap<>();
		cases.put("wrap", "net.minecraft.world.level.block.TripWireBlock| @WrapOperation(method = \"playerWillDestroy\", at = @At(value = \"INVOKE\", target ="
				+ " \"Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/neoforged/neoforge/common/ItemAbility;)Z\")) private boolean w(ItemStack s, Object a,"
				+ " Operation<Boolean> o) { return true; }");
		cases.put("is", "net.minecraft.world.item.ItemStack| @Inject(method = \"is\", at = @At(\"HEAD\")) private void h(CallbackInfo ci) { }");
		cases.put("other", "net.minecraft.world.item.ItemStack| @Inject(method = \"isEmpty\", at = @At(\"HEAD\")) private void h(CallbackInfo ci) { }");
		for (Map.Entry<String, String> e : cases.entrySet()) {
			String[] parts = e.getValue().split("\\|", 2);
			Map<String, String> sources = new HashMap<>(sources(Form.NEOFORGE, false));
			sources.putAll(mixinSources());
			sources.put("com/moon/NativeMixin.java", "package com.moon; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
					+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; import com.llamalad7.mixinextras.injector.wrapoperation.*;"
					+ " import net.minecraft.world.item.ItemStack; @Mixin(" + parts[0] + ".class) public abstract class NativeMixin {" + parts[1] + " }");
			Map<String, byte[]> classes = TestSources.compile(dir.resolve(e.getKey()), TestSources.MIXIN_ANNOTATIONS, sources);
			InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight", classes::containsKey, classes::get,
					() -> List.of("com/moon/NativeMixin"))));
			SeamConflicts.Conflict found = SeamConflicts.find(node(classes.get(TRIPWIRE)), installed);
			assertEquals(e.getKey().equals("other") ? null : new SeamConflicts.Conflict("moonlight", "com/moon/NativeMixin", true), found, e.getKey());
		}
	}

	/**
	 * The decision reads each native mixin once, whatever number of seam classes the game has: one read for every class
	 * would read a modpack's thousands of mixins as many times again.
	 */
	@Test
	void theDecisionReadsEachNativeMixinOnce(@TempDir Path dir) throws Exception {
		Map<String, String> sources = new HashMap<>(sources(Form.NEOFORGE, false));
		sources.putAll(mixinSources());
		List<String> mixins = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			sources.put("com/moon/M" + i + ".java", "package com.moon; import org.spongepowered.asm.mixin.*; import org.spongepowered.asm.mixin.injection.*;"
					+ " import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; @Mixin(net.minecraft.world.item.ItemStack.class) public abstract class M" + i
					+ " { @Inject(method = \"isEmpty\", at = @At(\"HEAD\")) private void h(CallbackInfo ci) { } }");
			mixins.add("com/moon/M" + i);
		}
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, sources);
		Map<String, Integer> reads = new HashMap<>();
		ClassBytesSource counting = name -> {
			reads.merge(name, 1, Integer::sum);
			return classes.get(name);
		};
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("moonlight", classes::containsKey, counting, () -> mixins)));
		SeamConflicts.Decision decision = SeamConflicts.decide(classes::get, installed);
		assertEquals(Map.of(), decision.kept());
		for (String mixin : mixins) assertEquals(1, reads.get(mixin), mixin + " " + reads);
	}

	/** The Mixin and MixinExtras annotations the native mixins use. */
	private static Map<String, String> mixinSources() {
		return Map.of("org/spongepowered/asm/mixin/injection/At.java", "package org.spongepowered.asm.mixin.injection; public @interface At { String value();"
						+ " String target() default \"\"; }",
				"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", "package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo { }",
				"com/llamalad7/mixinextras/injector/wrapoperation/WrapOperation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
						+ " public @interface WrapOperation { String[] method(); org.spongepowered.asm.mixin.injection.At[] at(); }",
				"com/llamalad7/mixinextras/injector/wrapoperation/Operation.java", "package com.llamalad7.mixinextras.injector.wrapoperation;"
						+ " public interface Operation<R> { R call(Object... args); }");
	}

	private static MethodNode method(ClassNode node, String name, Form form) {
		String named = form != null && form.srg ? SRG.getOrDefault("$" + name + "$", name) : name;
		return node.methods.stream().filter(m -> m.name.equals(named)).findFirst().orElseThrow();
	}

	private static int calls(MethodNode method, String name) {
		int n = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals(name)) n++;
		}
		return n;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(ItemChecksTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = name.equals("com.example.ClientOnly") ? null : classes.get(name.replace('.', '/'));
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
