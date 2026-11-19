package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * SEAM-9: vanilla's {@code isEyeInFluid(FluidTags.WATER)}, which NeoForge 26.x asks as its fluid interaction's
 * {@code isEyeInFluidMatching(entity, predicate)} of the fluid types at the entity's eyes in {@code Player.getDestroySpeed}
 * (water-like types) and the HUD's air bubbles (types the entity can drown in), put back for the entities whose eyes
 * are in vanilla's water, lava or no fluid where the two answer alike. Each fixture is NeoForge's code in its form
 * (26.1.2's fluid interaction, keyed by fluid type in an array map; 26.3's, a list of trackers by fluid, which answers
 * a tag by the fluid's tags, each question a pass through the list's iterator) against stand-ins whose defaults are
 * NeoForge's. The class rewritten answers what NeoForge's did for every entity and every fluid at its eyes; a hook of
 * vanilla's call (Artifacts' underwater mining speed, FrozenLib's air bubbles) runs where it goes back (water at the
 * eyes passing NeoForge's predicate, a mod's fluid there or not), and only there; and the put-back's own code makes
 * nothing on the common path (an entity with no fluid at its eyes).
 */
public class EyesInWaterTest {
	private static final String PLAYER = "net/minecraft/world/entity/player/Player";
	private static final String GUI = "net/minecraft/client/gui/Gui";

	/** NeoForge 26.x's fluid interaction: 26.1.2's and 26.2's trackers by fluid type, 26.3's by fluid. */
	enum Form {
		BY_TYPE, BY_FLUID
	}

	static Map<String, String> sources(Form form) {
		return sources(form, null);
	}

	/**
	 * {@link #sources(Form)}, or a variant of NeoForge's places that mustn't be rewritten: {@code "deep"}, the mining speed's
	 * predicate reading the height; {@code "mixed"}, the air bubbles asking one player's fluid interaction about another.
	 */
	static Map<String, String> sources(Form form, String variant) {
		Map<String, String> sources = new HashMap<>();
		put(sources, "net.minecraft.core.Holder", "public interface Holder<T> { T value(); }");
		put(sources, "net.minecraft.tags.TagKey", "public record TagKey<T>(String name) { }");
		put(sources, "net.minecraft.tags.FluidTags", "public class FluidTags { public static final TagKey<Object> WATER = new TagKey<>(\"water\"),"
				+ " LAVA = new TagKey<>(\"lava\"); }");
		put(sources, "net.neoforged.neoforge.fluids.FluidType", """
				public class FluidType {
					final String name; final boolean waterLike, canDrown;
					public FluidType(String name, boolean waterLike, boolean canDrown) { this.name = name; this.waterLike = waterLike; this.canDrown = canDrown; }
					public boolean getIsWaterLike() { return waterLike; }
					public boolean canDrownIn(net.minecraft.world.entity.LivingEntity entity) { return canDrown; }
					public String toString() { return name; }
				}""");
		put(sources, "net.neoforged.neoforge.fluids.InFluidPredicate", "public interface InFluidPredicate<E extends net.minecraft.world.entity.Entity> {"
				+ " boolean test(E entity, FluidType fluidType, double height); }");
		put(sources, "net.neoforged.neoforge.common.NeoForgeMod", """
				public class NeoForgeMod {
					public static final net.minecraft.core.Holder<net.neoforged.neoforge.fluids.FluidType> WATER_TYPE = holder(new net.neoforged.neoforge.fluids.FluidType("water", true, true)),
							LAVA_TYPE = holder(new net.neoforged.neoforge.fluids.FluidType("lava", false, false));
					public static final net.neoforged.neoforge.fluids.FluidType MUD = new net.neoforged.neoforge.fluids.FluidType("mud", true, true);
					static net.minecraft.core.Holder<net.neoforged.neoforge.fluids.FluidType> holder(net.neoforged.neoforge.fluids.FluidType type) { return () -> type; }
				}""");
		// The fluid interaction: a tracker for each fluid the entity touches, whose eyes are inside or not. 26.1.2's and 26.2's
		// by fluid type, in an array map (fastutil's Reference2ObjectArrayMap): a type looked up in its keys (LOOKUPS), its
		// entries passed by isEyeInFluidMatching alone (PASSES), the tag's check the water type's or lava's. 26.3's by fluid,
		// each with its tag, in a list each check passes through its iterator (PASSES), the tag's check asking the fluids' tags.
		String interaction = form == Form.BY_TYPE ? """
				import net.neoforged.neoforge.fluids.FluidType;
				public class EntityFluidInteraction {
					public static int LOOKUPS, PASSES;
					final FluidType[] types = new FluidType[4];
					final boolean[] eyes = new boolean[4];
					final double[] heights = new double[4];
					int count;
					public void touch(FluidType type, net.minecraft.tags.TagKey<?> tag, boolean eyesInside, double height) {
						for (int i = 0; i < count; i++) if (types[i] == type) { eyes[i] |= eyesInside; heights[i] = Math.max(heights[i], height); return; }
						types[count] = type; eyes[count] = eyesInside; heights[count++] = height;
					}
					public <E extends Entity> boolean isEyeInFluidMatching(E entity, net.neoforged.neoforge.fluids.InFluidPredicate<E> predicate) {
						PASSES++;
						for (int i = 0; i < count; i++) if (eyes[i] && predicate.test(entity, types[i], heights[i])) return true;
						return false;
					}
					public boolean isEyeInFluid(FluidType fluid) {
						LOOKUPS++;
						for (int i = 0; i < count; i++) if (types[i] == fluid) return eyes[i];
						return false;
					}
					public boolean isEyeInFluid(net.minecraft.tags.TagKey<?> fluid) { return this.isEyeInFluid(getFluidTypeByTag(fluid)); }
					static FluidType getFluidTypeByTag(net.minecraft.tags.TagKey<?> fluid) {
						return fluid == net.minecraft.tags.FluidTags.WATER ? net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value()
								: net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value();
					}
				}""" : """
				import net.neoforged.neoforge.fluids.FluidType;
				public class EntityFluidInteraction {
					public static int LOOKUPS, PASSES;
					private final java.util.List<Tracker> trackers = new java.util.ArrayList<>();
					public void touch(FluidType type, net.minecraft.tags.TagKey<?> tag, boolean eyesInside, double height) {
						trackers.add(new Tracker(type, tag, eyesInside, height));
					}
					public <E extends Entity> boolean isEyeInFluidMatching(E entity, net.neoforged.neoforge.fluids.InFluidPredicate<E> predicate) {
						PASSES++;
						for (Tracker tracker : this.trackers) if (tracker.eyesInside && predicate.test(entity, tracker.type, tracker.height)) return true;
						return false;
					}
					public boolean isEyeInFluid(FluidType fluid) {
						PASSES++;
						for (Tracker tracker : this.trackers) if (tracker.eyesInside && tracker.type == fluid) return true;
						return false;
					}
					public boolean isEyeInFluid(net.minecraft.tags.TagKey<?> fluid) {
						PASSES++;
						for (Tracker tracker : this.trackers) if (tracker.eyesInside && tracker.tag == fluid) return true;
						return false;
					}
					static FluidType getFluidTypeByTag(net.minecraft.tags.TagKey<?> fluid) {
						return fluid == net.minecraft.tags.FluidTags.WATER ? net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value()
								: net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value();
					}
					private record Tracker(FluidType type, net.minecraft.tags.TagKey<?> tag, boolean eyesInside, double height) { }
				}""";
		put(sources, "net.minecraft.world.entity.EntityFluidInteraction", interaction);
		put(sources, "net.minecraft.world.entity.Entity", """
				public class Entity {
					final EntityFluidInteraction fluidInteraction = new EntityFluidInteraction();
					public EntityFluidInteraction getFluidInteraction() { return fluidInteraction; }
					public boolean isEyeInFluid(net.minecraft.tags.TagKey<?> type) { return %s; }
					public boolean isEyeInFluid(net.neoforged.neoforge.fluids.FluidType type) { return this.fluidInteraction.isEyeInFluid(type); }
				}""".formatted(form == Form.BY_TYPE ? "this.isEyeInFluid(EntityFluidInteraction.getFluidTypeByTag(type))"
						: "this.fluidInteraction.isEyeInFluid(type)"));
		put(sources, "net.minecraft.world.entity.LivingEntity", """
				public class LivingEntity extends Entity {
					public boolean canBreatheUnderwater() { return false; }
					public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
						if (type == net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value()) return !this.canBreatheUnderwater();
						return type.canDrownIn(this);
					}
				}""");
		// NeoForge's forms of the two places: the mining speed (water-like types), the air bubbles (drowning types). The
		// variants: a predicate reading the height, which the put-back can't ask without it; another player's eyes asked.
		put(sources, "net.minecraft.world.entity.player.Player", """
				public class Player extends net.minecraft.world.entity.LivingEntity {
					public float getDestroySpeed() {
						float speed = 1.0F;
						if (this.getFluidInteraction().isEyeInFluidMatching(this, (e, type, h) -> %stype.getIsWaterLike())) speed *= 0.2F;
						return speed;
					}
				}""".formatted("deep".equals(variant) ? "h > 0.5 && " : ""));
		put(sources, "net.minecraft.client.gui.Gui", """
				public class Gui {
					public String extractAirBubbles(net.minecraft.world.entity.player.Player player) {
						%s
						boolean isUnderWater = player.getFluidInteraction().isEyeInFluidMatching(%s, (e, type, h) -> e.canDrownInFluidType(type));
						return isUnderWater ? "bubbles" : "none";
					}
				}""".formatted("mixed".equals(variant) ? "net.minecraft.world.entity.player.Player other = new net.minecraft.world.entity.player.Player();" : "",
						"mixed".equals(variant) ? "other" : "player"));
		put(sources, "com.example.Gills", "public class Gills extends net.minecraft.world.entity.player.Player { @Override public boolean canBreatheUnderwater() {"
				+ " return true; } }");
		// An entity that can drown in any fluid, lava among them.
		put(sources, "com.example.Drowns", "public class Drowns extends net.minecraft.world.entity.player.Player { @Override public boolean canDrownInFluidType("
				+ "net.neoforged.neoforge.fluids.FluidType type) { return true; } }");
		put(sources, "com.example.OwnEyes", "public class OwnEyes extends net.minecraft.world.entity.player.Player { @Override public boolean isEyeInFluid("
				+ "net.minecraft.tags.TagKey<?> type) { Hook.LOG.add(\"own eyes asked\"); return true; } }");
		// An entity overriding the fluid type's check alone, which 26.1.2's check of the tag calls.
		put(sources, "com.example.OwnTypeEyes", "public class OwnTypeEyes extends net.minecraft.world.entity.player.Player { @Override public boolean"
				+ " isEyeInFluid(net.neoforged.neoforge.fluids.FluidType type) { Hook.LOG.add(\"own type eyes asked\"); return true; } }");
		// The hooks, as Artifacts' and FrozenLib's make them: of vanilla's call, its answer passed on.
		put(sources, "com.example.Hook", """
				public class Hook {
					public static final java.util.List<String> LOG = new java.util.ArrayList<>();
					public static boolean eyes(boolean underwater) { LOG.add("eyes hooked " + underwater); return underwater; }
				}""");
		return sources;
	}

	private static void put(Map<String, String> sources, String className, String body) {
		int dot = className.lastIndexOf('.');
		sources.put(className.replace('.', '/') + ".java", "package " + className.substring(0, dot) + ";\n" + body);
	}

	private static Map<String, byte[]> restored(Map<String, byte[]> platform) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		for (String owner : List.of(PLAYER, GUI)) {
			ClassNode node = node(platform.get(owner));
			VanillaSeams.Restoring done = VanillaSeams.restoreWhole(node);
			assertNull(done.failure());
			assertTrue(!done.restored().isEmpty(), owner);
			restored.put(owner, write(node));
		}
		return restored;
	}

	/** What is at the entity's eyes: nothing, vanilla's water or lava or both, a mod's fluid type, a mod's fluid of water's type, or with the other. */
	private static final List<String> EYES = List.of("none", "water", "lava", "water lava", "mud", "water mud", "watery", "watery mud", "feet water");
	private static final List<String> ENTITIES = List.of("net.minecraft.world.entity.player.Player", "com.example.Gills", "com.example.OwnEyes",
			"com.example.OwnTypeEyes", "com.example.Drowns");

	@Test
	void theChecksAnswerWhatNeoForgesDidForEveryEntityAndFluid(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = TestSources.compileWithLocals(dir.resolve(form.name()), sources(form)), restored = restored(platform);
			for (String entity : ENTITIES) {
				for (String eyes : EYES) {
					assertEquals(ask(platform, form, entity, eyes), ask(restored, form, entity, eyes), form + " " + entity + " " + eyes);
				}
			}
			for (String owner : List.of(PLAYER, GUI)) {
				List<String> seams = VanillaSeams.restoredIn(node(restored.get(owner))).stream().map(r -> r.owner().substring(r.owner().lastIndexOf('/') + 1) + "."
						+ r.name() + " in " + r.in().substring(0, r.in().indexOf('(')) + " " + r.finding()).distinct().toList();
				assertEquals(List.of("Player.isEyeInFluid in " + (owner.equals(GUI) ? "extractAirBubbles" : "getDestroySpeed") + " SEAM-9"), seams, form + " " + owner);
				assertTrue(VanillaSeams.findings(owner).contains("SEAM-9"), VanillaSeams.findings(owner));
			}
			// A predicate reading the height, or another entity's eyes asked: NeoForge's check stays, no vanilla call there.
			for (String variant : List.of("deep", "mixed")) {
				Map<String, byte[]> other = TestSources.compileWithLocals(dir.resolve(form.name() + variant), sources(form, variant));
				String owner = variant.equals("deep") ? PLAYER : GUI;
				ClassNode node = node(other.get(owner));
				VanillaSeams.restoreWhole(node);
				assertTrue(VanillaSeams.restoredIn(node).isEmpty(), form + " " + variant);
				MethodNode place = node.methods.stream().filter(m -> m.name.equals(variant.equals("deep") ? "getDestroySpeed" : "extractAirBubbles")).findFirst()
						.orElseThrow();
				assertEquals(0, calls(place, "isEyeInFluid"), form + " " + variant);
			}
		}
	}

	/**
	 * Artifacts' hook of the mining speed's check and FrozenLib's of the air bubbles' (an {@code @ModifyExpressionValue} of
	 * vanilla's {@code Player.isEyeInFluid}): once put back each runs for an entity whose eyes are in vanilla's water, lava
	 * or nothing, or in water passing NeoForge's predicate with a mod's fluid too, with vanilla's answer, and for no other (a
	 * mod's fluid alone at its eyes, an entity overriding the check, one the platform's predicate answers otherwise for: gills
	 * in water, which can't drown there); the answer is NeoForge's.
	 */
	@Test
	void hooksOfVanillasCallRunWhereItGoesBack(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = TestSources.compileWithLocals(dir.resolve(form.name()), sources(form));
			assertEquals(0, calls(node(platform.get(PLAYER)).methods.stream().filter(m -> m.name.equals("getDestroySpeed")).findFirst().orElseThrow(),
					"isEyeInFluid"), "NeoForge's code makes no vanilla call there");
			Map<String, byte[]> hooked = hooked(restored(platform));
			String player = ENTITIES.get(0);
			// Water with a mod's fluid too: water passes both predicates, so NeoForge's checks answer yes whatever else is at the
			// eyes, as vanilla's call does: it goes back, with NeoForge's answers.
			for (String eyes : List.of("none", "water", "lava", "water lava", "feet water", "water mud")) {
				List<String> asked = ask(hooked, form, player, eyes);
				assertEquals(2, asked.stream().filter(l -> l.startsWith("eyes hooked")).count(), form + " " + eyes + " " + asked);
				assertEquals(ask(platform, form, player, eyes).subList(0, 2), asked.subList(asked.size() - 2, asked.size()), form + " " + eyes + " " + asked);
			}
			for (String eyes : List.of("mud")) {
				List<String> asked = ask(hooked, form, player, eyes);
				assertTrue(asked.stream().noneMatch(l -> l.startsWith("eyes hooked")), form + " " + eyes + " " + asked);
			}
			// A mod's fluid of water's type: 26.1.2 asks the type (both water), 26.3 the fluid's tags (it isn't tagged water).
			List<String> watery = ask(hooked, form, player, "watery");
			assertEquals(form == Form.BY_TYPE ? 2 : 0, watery.stream().filter(l -> l.startsWith("eyes hooked")).count(), form + " " + watery);
			List<String> own = ask(hooked, form, "com.example.OwnEyes", "none");
			assertTrue(own.stream().noneMatch(l -> l.startsWith("eyes hooked") || l.equals("own eyes asked")), form + " " + own);
			// Gills in water: the mining's check (water-like) goes back, the bubbles' (can it drown?) answers otherwise and stays.
			List<String> gills = ask(hooked, form, "com.example.Gills", "water");
			assertEquals(1, gills.stream().filter(l -> l.startsWith("eyes hooked")).count(), form + " " + gills);
		}
	}

	/**
	 * What the mining speed's check asks of the fluid interaction, per what is at the entity's eyes: two questions before
	 * vanilla's call, which goes back as the third, with the eyes in no fluid or in water (26.3's passes over its trackers;
	 * 26.1.2's lookups in its map, and one pass over its entries with no water at the eyes), more only with a mod's fluid
	 * or lava alone there; NeoForge's check is one pass.
	 */
	@Test
	void theCheckAsksTheFluidInteractionTwiceBeforeTheCallItPutsBack(@TempDir Path dir) throws Exception {
		// Lookups, passes.
		Map<Form, Map<String, List<Integer>>> expected = Map.of(
				Form.BY_FLUID, Map.of("none", List.of(0, 3), "water", List.of(0, 3), "water mud", List.of(0, 3), "mud", List.of(0, 4), "lava", List.of(0, 5)),
				Form.BY_TYPE, Map.of("none", List.of(2, 1), "water", List.of(3, 0), "water mud", List.of(3, 0), "mud", List.of(1, 3), "lava", List.of(3, 2)));
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = TestSources.compileWithLocals(dir.resolve(form.name()), sources(form)), restored = restored(platform);
			for (Map.Entry<String, List<Integer>> e : expected.get(form).entrySet()) {
				assertEquals(List.of(0, 1), asked(platform, e.getKey()), form + " " + e.getKey() + ": NeoForge's");
				assertEquals(e.getValue(), asked(restored, e.getKey()), form + " " + e.getKey());
			}
		}
	}

	/** The fluid interaction's lookups and passes the mining speed's check makes once, with what is at the player's eyes. */
	private static List<Integer> asked(Map<String, byte[]> classes, String eyes) throws Exception {
		ClassLoader loader = loader(classes);
		Object entity = loader.loadClass(ENTITIES.get(0)).getConstructor().newInstance();
		Class<?> mod = loader.loadClass("net.neoforged.neoforge.common.NeoForgeMod"), holder = loader.loadClass("net.minecraft.core.Holder");
		Class<?> tags = loader.loadClass("net.minecraft.tags.FluidTags");
		Object interaction = loader.loadClass("net.minecraft.world.entity.Entity").getMethod("getFluidInteraction").invoke(entity);
		Method touch = interaction.getClass().getMethod("touch", loader.loadClass("net.neoforged.neoforge.fluids.FluidType"), loader.loadClass("net.minecraft.tags.TagKey"),
				boolean.class, double.class);
		for (String fluid : eyes.split(" ")) {
			switch (fluid) {
				case "water" -> touch.invoke(interaction, holder.getMethod("value").invoke(mod.getField("WATER_TYPE").get(null)), tags.getField("WATER").get(null), true, 1.0);
				case "lava" -> touch.invoke(interaction, holder.getMethod("value").invoke(mod.getField("LAVA_TYPE").get(null)), tags.getField("LAVA").get(null), true, 1.0);
				case "mud" -> touch.invoke(interaction, mod.getField("MUD").get(null), null, true, 1.0);
				default -> {
				}
			}
		}
		interaction.getClass().getField("LOOKUPS").setInt(null, 0);
		interaction.getClass().getField("PASSES").setInt(null, 0);
		entity.getClass().getMethod("getDestroySpeed").invoke(entity);
		return List.of(interaction.getClass().getField("LOOKUPS").getInt(null), interaction.getClass().getField("PASSES").getInt(null));
	}

	/**
	 * The common path, an entity with no fluid at its eyes: the put-back's own code makes nothing (warmed up, then measured
	 * over many calls), over 26.3's tracker list, each pass through its iterator, which the compiled code makes without
	 * one, as NeoForge's own check does. 26.1.2's tracker map (fastutil's {@code Reference2ObjectArrayMap}) makes an entry
	 * for each tracker its entries' iterator passes, in NeoForge's check as in the put-back's one pass there, which this
	 * fixture's array doesn't model.
	 */
	@Test
	void theCheckMakesNothingWhereTheEyesAreInNoFluid(@TempDir Path dir) throws Throwable {
		Map<String, byte[]> platform = TestSources.compileWithLocals(dir.resolve("p"), sources(Form.BY_FLUID)), restored = restored(platform);
		ClassLoader loader = loader(restored);
		Object player = loader.loadClass(ENTITIES.get(0)).getConstructor().newInstance();
		Method speed = player.getClass().getMethod("getDestroySpeed");
		Object gui = loader.loadClass(GUI.replace('/', '.')).getConstructor().newInstance();
		Method bubbles = gui.getClass().getMethod("extractAirBubbles", loader.loadClass(PLAYER.replace('/', '.')));
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		// Through a method handle, whose calls make nothing themselves (reflection boxes the answer).
		java.lang.invoke.MethodHandle mining = java.lang.invoke.MethodHandles.publicLookup().unreflect(speed);
		java.lang.invoke.MethodHandle air = java.lang.invoke.MethodHandles.publicLookup().unreflect(bubbles);
		long measured = Long.MAX_VALUE;
		for (int round = 0; round < 5 && measured != 0; round++) {
			for (int i = 0; i < 200_000; i++) {
				float f = (float) mining.invoke(player);
				Object s = air.invoke(gui, player);
				if (f != 1.0F || !"none".equals(s)) throw new AssertionError(f + " " + s);
			}
			long before = threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
			for (int i = 0; i < 100_000; i++) {
				float f = (float) mining.invoke(player);
				if (f != 1.0F) throw new AssertionError(f);
			}
			measured = threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
		}
		assertEquals(0, measured, "bytes made by 100,000 checks put back");
	}

	/**
	 * The entity's answers, the mining speed's and the air bubbles', with what is at its eyes (heights 1, the feet's water
	 * not at the eyes): what the hooks and the entity logged, then the answers.
	 */
	@SuppressWarnings("unchecked")
	private static List<String> ask(Map<String, byte[]> classes, Form form, String entityClass, String eyes) throws Exception {
		ClassLoader loader = loader(classes);
		Object entity = loader.loadClass(entityClass).getConstructor().newInstance();
		Class<?> fluidType = loader.loadClass("net.neoforged.neoforge.fluids.FluidType"), tagKey = loader.loadClass("net.minecraft.tags.TagKey");
		Class<?> mod = loader.loadClass("net.neoforged.neoforge.common.NeoForgeMod"), holder = loader.loadClass("net.minecraft.core.Holder");
		Object water = holder.getMethod("value").invoke(mod.getField("WATER_TYPE").get(null)), lava = holder.getMethod("value").invoke(mod.getField("LAVA_TYPE").get(null));
		Object mud = mod.getField("MUD").get(null);
		Class<?> tags = loader.loadClass("net.minecraft.tags.FluidTags");
		Object waterTag = tags.getField("WATER").get(null), lavaTag = tags.getField("LAVA").get(null);
		Object interaction = loader.loadClass("net.minecraft.world.entity.Entity").getMethod("getFluidInteraction").invoke(entity);
		Method touch = interaction.getClass().getMethod("touch", fluidType, tagKey, boolean.class, double.class);
		for (String fluid : eyes.split(" ")) {
			switch (fluid) {
				case "water" -> touch.invoke(interaction, water, waterTag, true, 1.0);
				case "lava" -> touch.invoke(interaction, lava, lavaTag, true, 1.0);
				case "mud" -> touch.invoke(interaction, mud, null, true, 1.0);
				case "watery" -> touch.invoke(interaction, water, null, true, 1.0);
				case "feet" -> touch.invoke(interaction, water, waterTag, false, 0.3);
				default -> {
				}
			}
		}
		Object speed = entity.getClass().getMethod("getDestroySpeed").invoke(entity);
		Object gui = loader.loadClass(GUI.replace('/', '.')).getConstructor().newInstance();
		Object bubbles = gui.getClass().getMethod("extractAirBubbles", loader.loadClass(PLAYER.replace('/', '.'))).invoke(gui, entity);
		List<String> log = new ArrayList<>((List<String>) loader.loadClass("com.example.Hook").getField("LOG").get(null));
		log.add("speed " + speed);
		log.add("air " + bubbles);
		return log;
	}

	/**
	 * The classes with the hooks woven in, as Mixin would weave an {@code @ModifyExpressionValue} of vanilla's
	 * {@code Player.isEyeInFluid}: after each such call its answer passes the hook. Each finds its point, or the test fails.
	 */
	private static Map<String, byte[]> hooked(Map<String, byte[]> classes) {
		Map<String, byte[]> hooked = new HashMap<>(classes);
		for (String owner : List.of(PLAYER, GUI)) {
			ClassNode node = node(classes.get(owner));
			int found = 0;
			for (MethodNode method : node.methods) {
				if (method.name.startsWith("fabricated$")) continue;
				for (AbstractInsnNode insn : method.instructions.toArray()) {
					if (insn instanceof MethodInsnNode call && call.owner.equals(PLAYER) && call.name.equals("isEyeInFluid")) {
						method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "eyes", "(Z)Z", false));
						found++;
					}
				}
			}
			assertEquals(1, found, owner + ": the hook's point");
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
				@Override
				protected String getCommonSuperClass(String a, String b) {
					return "java/lang/Object";
				}
			};
			node.accept(writer);
			hooked.put(owner, writer.toByteArray());
		}
		return hooked;
	}

	private static int calls(MethodNode method, String name) {
		int n = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals(name)) n++;
		}
		return n;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(EyesInWaterTest.class.getClassLoader()) {
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
