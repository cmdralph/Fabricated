package io.github.cmdralph.fabricated.forgetest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1542;
import net.minecraft.class_2338;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for a Fabric mod's port of Forge's drop capture (CORPUS-105). Porting Lib's hook at
 * the return of {@code LivingEntity.dropAllDeathLoot} ({@code method_16080}) takes the drops that Forge's capture
 * ({@code captureDrops}, which Porting Lib adds under Forge's name on Fabric) holds, and adds them to the level. The test
 * mod's own such hook stands in for it ({@code LivingEntityCapturedDropsMixin}). At the first server tick, a pig dies
 * ({@code method_6078}). The hook must find the pig's drops still captured after Forge's own drops event, and each of
 * them must be in the level once the hook added it. Forge 47 used to take them first: the hook found none, and Porting
 * Lib's threw. Where Porting Lib's entity module is installed (the corpus runs), its hook runs first and takes them, so
 * the test mod's finds none. Forge's method is called reflectively, by its own name: a Fabric mod doesn't compile
 * against Forge.
 */
public final class ForgeDropCaptureTest {
	private static final Method CAPTURE_DROPS;
	/** The pig dying in {@link #check}, whose drops {@link #takeCapturedDrops} records. */
	private static volatile class_1309 dying;
	private static volatile boolean hookRan;
	private static volatile Collection<class_1542> taken;

	static {
		try {
			CAPTURE_DROPS = class_1297.class.getMethod("captureDrops", Collection.class);
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private ForgeDropCaptureTest() {
	}

	/**
	 * From the test mod's hook at the return of {@code dropAllDeathLoot}: takes what Forge's capture holds and adds it to
	 * the level, as Porting Lib's hook does.
	 */
	public static void takeCapturedDrops(class_1309 entity) {
		Collection<class_1542> drops = captureDrops(entity, null);
		if (entity == dying) {
			hookRan = true;
			taken = drops;
		}
		if (drops != null) drops.forEach(entity.method_37908()::method_8649);
	}

	/** At the first server tick, on the overworld. */
	public static void check(class_3218 level) {
		if (!(class_7923.field_41177.method_10223(new class_2960("minecraft", "pig")).method_5883(level) instanceof class_1309 pig)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST drop capture FAILED: could not create a pig");
			return;
		}
		class_2338 site = ForgeTestMod.testSite(level, 24, 284, 8);
		pig.method_5814(site.method_10263() + 0.5, site.method_10264(), site.method_10260() + 0.5);
		dying = pig;
		try {
			pig.method_6078(level.method_48963().method_48830());
		} catch (RuntimeException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST drop capture FAILED: the pig's death threw", e);
			return;
		} finally {
			dying = null;
		}
		boolean portingLib = FabricLoader.getInstance().isModLoaded("porting_lib_entity");
		Collection<class_1542> drops = taken;
		List<String> failed = new ArrayList<>();
		if (!hookRan) {
			failed.add("the hook at the return of dropAllDeathLoot didn't run");
		} else if (drops == null) {
			if (!portingLib) failed.add("the hook found no drops captured: Forge's method took them before it");
		} else if (drops.isEmpty()) {
			failed.add("the hook found the capture empty: the pig dropped nothing");
		} else {
			for (class_1542 drop : drops) {
				if (level.method_14190(drop.method_5667()) != drop) failed.add(drop + " isn't in the level");
				drop.method_31472();
			}
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST drop capture OK{}", drops == null ? " (Porting Lib's hook took the drops)" : "");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST drop capture: {}", failed);
		}
	}

	@SuppressWarnings("unchecked")
	private static Collection<class_1542> captureDrops(class_1297 entity, Collection<class_1542> drops) {
		try {
			return (Collection<class_1542>) CAPTURE_DROPS.invoke(entity, drops);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("Forge's captureDrops threw", e.getCause());
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Forge's captureDrops", e);
		}
	}
}
