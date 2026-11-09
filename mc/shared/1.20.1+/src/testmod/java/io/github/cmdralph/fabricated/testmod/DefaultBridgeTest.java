package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * CI test for default bridges (CORPUS-11, CORPUS-63, DEFAULT-1). The JVM can't choose between two defaults with the same
 * name and type on a class declaring neither: calling either there throws ({@code AbstractMethodError} on Java 21,
 * {@code IncompatibleClassChangeError} where the JVM reports the conflict). Fabricated declares the method on the class
 * the mod's interface is added to, calling the platform's default. Two such interfaces, each added by a mixin:
 *
 * <ul>
 * <li>{@link OwnSelf} on {@code LivingEntity} ({@code LivingEntitySelfMixin}), whose default {@code LivingEntity self()}
 * has the name and type of the default {@code LivingEntity} has from its own NeoForge extension interface
 * ({@code ILivingEntityExtension}), as Entity Culling's interface on block entity renderers has NeoForge's
 * {@code getRenderBoundingBox}. NeoForge calls it on every mob in a fluid. Both calls on a pig must answer the pig.
 * <li>{@link OwnMultipart} on {@code Mob} ({@code MobMultipartMixin}), whose default {@code isMultipartEntity()} has the
 * name and type of the default {@code Mob} has through its superclass {@code Entity}'s extension interface
 * ({@code IEntityExtension}): the method calls the superclass's (DEFAULT-1). NeoForge calls it on every entity the
 * server starts tracking. Both calls on a pig must answer false, as NeoForge's default does.
 * </ul>
 */
public final class DefaultBridgeTest implements ModInitializer {
	/** The test mod's own interface, added to {@code LivingEntity}. Its default is never the one that runs there. */
	public interface OwnSelf {
		default LivingEntity self() {
			return null;
		}
	}

	/** The test mod's own interface, added to {@code Mob}. Its default is never the one that runs there. */
	public interface OwnMultipart {
		default boolean isMultipartEntity() {
			return true;
		}
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(DefaultBridgeTest::check);
	}

	private static void check(MinecraftServer server) {
		EntityType<?> pigType = TestCompat.value(BuiltInRegistries.ENTITY_TYPE, TestCompat.id("minecraft", "pig"));
		if (!(TestCompat.create(pigType, server.overworld()) instanceof Mob pig)) {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: could not create a pig");
			TestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: could not create a pig");
			return;
		}
		checkSelf(pig);
		checkMultipart(pig);
	}

	/** The bridge for a default of the class's own interface (CORPUS-11, CORPUS-63). */
	private static void checkSelf(Mob pig) {
		if (!((Object) pig instanceof OwnSelf own)) {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: LivingEntitySelfMixin did not add OwnSelf to LivingEntity");
			return;
		}
		Object ours;
		try {
			ours = own.self();
		} catch (LinkageError e) {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: the test mod's interface's self() on a pig", e);
			return;
		}
		Object platforms;
		try {
			Method self = LivingEntity.class.getMethod("self");
			platforms = self.invoke(pig);
		} catch (InvocationTargetException e) {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: NeoForge's self() on a pig", e.getCause());
			return;
		} catch (ReflectiveOperationException e) {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: LivingEntity has no public self()", e);
			return;
		}
		if (ours == pig && platforms == pig) {
			TestMod.LOGGER.info("FABRICATED-TEST default bridge OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: self() on a pig answered {} through the test mod's interface and {} through"
					+ " NeoForge's, where both must answer the pig", ours, platforms);
		}
	}

	/** The bridge for a default the class has through its superclass's interface (DEFAULT-1). */
	private static void checkMultipart(Mob pig) {
		if (!((Object) pig instanceof OwnMultipart own)) {
			TestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: MobMultipartMixin did not add OwnMultipart to Mob");
			return;
		}
		boolean ours;
		try {
			ours = own.isMultipartEntity();
		} catch (LinkageError e) {
			TestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: the test mod's interface's isMultipartEntity() on a pig", e);
			return;
		}
		Object platforms;
		try {
			Method multipart = Entity.class.getMethod("isMultipartEntity");
			platforms = multipart.invoke(pig);
		} catch (InvocationTargetException e) {
			TestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: NeoForge's isMultipartEntity() on a pig", e.getCause());
			return;
		} catch (ReflectiveOperationException e) {
			TestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: Entity has no public isMultipartEntity()", e);
			return;
		}
		if (!ours && Boolean.FALSE.equals(platforms)) {
			TestMod.LOGGER.info("FABRICATED-TEST inherited default bridge OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: isMultipartEntity() on a pig answered {} through the test mod's"
					+ " interface and {} through NeoForge's, where both must answer false", ours, platforms);
		}
	}
}
