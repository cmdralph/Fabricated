package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * CI test for default bridges (CORPUS-11, CORPUS-63): the test mod's {@code LivingEntitySelfMixin} adds
 * {@link OwnSelf} to {@code LivingEntity}, whose default {@code LivingEntity self()} has the name and type of the default
 * {@code LivingEntity} has from NeoForge's extension interface ({@code ILivingEntityExtension}), as Entity Culling's
 * interface on block entity renderers has NeoForge's {@code getRenderBoundingBox}. The JVM can't choose between two such
 * defaults on a class declaring neither, as every mob is: calling either there throws ({@code AbstractMethodError} on
 * Java 21, {@code IncompatibleClassChangeError} where the JVM reports the conflict). Fabricated declares the method on
 * {@code LivingEntity}, calling NeoForge's default; NeoForge calls it on every mob in a fluid. Both calls on a pig must
 * answer the pig, as NeoForge's default does.
 */
public final class DefaultBridgeTest implements ModInitializer {
	/** The test mod's own interface, added to {@code LivingEntity}. Its default is never the one that runs there. */
	public interface OwnSelf {
		default LivingEntity self() {
			return null;
		}
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(DefaultBridgeTest::check);
	}

	private static void check(MinecraftServer server) {
		EntityType<?> pigType = TestCompat.value(BuiltInRegistries.ENTITY_TYPE, Identifier.withDefaultNamespace("pig"));
		if (!(TestCompat.create(pigType, server.overworld()) instanceof LivingEntity pig)) {
			TestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: could not create a pig");
			return;
		}
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
}
