package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for a Fabric mod's port of Forge's drop capture (CORPUS-105). Porting Lib's hook at
 * the return of {@code LivingEntity.dropAllDeathLoot} takes the drops that Forge's capture
 * ({@code captureDrops}, which Porting Lib adds under Forge's name on Fabric) holds, and adds them to the level. The test
 * mod's own such hook stands in for it ({@code LivingEntityCapturedDropsMixin}). At the first server tick, a pig dies
 * ({@code die}). The hook must find the pig's drops still captured after Forge's own drops event, and each of
 * them must be in the level once the hook added it. Forge 47 used to take them first: the hook found none, and Porting
 * Lib's threw. Where Porting Lib's entity module is installed (the corpus runs), its hook runs first and takes them, so
 * the test mod's finds none. Forge's method is called reflectively, by its own name: a Fabric mod doesn't compile
 * against Forge.
 */
public final class ForgeDropCaptureTest {
	private static final Method CAPTURE_DROPS;
	/** The pig dying in {@link #check}, whose drops {@link #takeCapturedDrops} records. */
	private static volatile LivingEntity dying;
	private static volatile boolean hookRan;
	private static volatile Collection<ItemEntity> taken;

	static {
		try {
			CAPTURE_DROPS = Entity.class.getMethod("captureDrops", Collection.class);
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
	public static void takeCapturedDrops(LivingEntity entity) {
		Collection<ItemEntity> drops = captureDrops(entity, null);
		if (entity == dying) {
			hookRan = true;
			taken = drops;
		}
		if (drops != null) drops.forEach(entity.level()::addFreshEntity);
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		if (!(BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("minecraft", "pig")).create(level) instanceof LivingEntity pig)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST drop capture FAILED: could not create a pig");
			return;
		}
		BlockPos site = ForgeTestMod.testSite(level, 24, 284, 8);
		pig.setPos(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
		dying = pig;
		try {
			pig.die(level.damageSources().generic());
		} catch (RuntimeException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST drop capture FAILED: the pig's death threw", e);
			return;
		} finally {
			dying = null;
		}
		boolean portingLib = FabricLoader.getInstance().isModLoaded("porting_lib_entity");
		Collection<ItemEntity> drops = taken;
		List<String> failed = new ArrayList<>();
		if (!hookRan) {
			failed.add("the hook at the return of dropAllDeathLoot didn't run");
		} else if (drops == null) {
			if (!portingLib) failed.add("the hook found no drops captured: Forge's method took them before it");
		} else if (drops.isEmpty()) {
			failed.add("the hook found the capture empty: the pig dropped nothing");
		} else {
			for (ItemEntity drop : drops) {
				if (level.getEntity(drop.getUUID()) != drop) failed.add(drop + " isn't in the level");
				drop.discard();
			}
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST drop capture OK{}", drops == null ? " (Porting Lib's hook took the drops)" : "");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST drop capture: {}", failed);
		}
	}

	@SuppressWarnings("unchecked")
	private static Collection<ItemEntity> captureDrops(Entity entity, Collection<ItemEntity> drops) {
		try {
			return (Collection<ItemEntity>) CAPTURE_DROPS.invoke(entity, drops);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("Forge's captureDrops threw", e.getCause());
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Forge's captureDrops", e);
		}
	}
}
