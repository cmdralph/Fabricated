package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * CI test for HIDE-1: the default {@code self()} that {@code LivingEntity} inherits from the platform's extension
 * interface (NeoForge's {@code ILivingEntityExtension}) must answer on a pig, though the test mod's
 * {@code LivingEntityMixin} gives {@code LivingEntity} a private {@code self()} of its own. The platform calls that
 * default on every entity in a fluid. Under the mixin's name, the JVM would find no implementation of it in any
 * subclass of {@code LivingEntity} ({@code AbstractMethodError}).
 */
public final class HiddenDefaultTest implements ModInitializer {
	/** Set by the mixin's helper, under whatever name it has. */
	public static volatile boolean helperRan;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(HiddenDefaultTest::check);
	}

	private static void check(MinecraftServer server) {
		Method self = platformSelf();
		if (self == null) {
			TestMod.LOGGER.error("FABRICATED-TEST hidden default FAILED: LivingEntity inherits no default self() from its interfaces");
			return;
		}
		EntityType<?> pigType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
		if (!(pigType.create(server.overworld(), EntitySpawnReason.TRIGGERED) instanceof LivingEntity pig)) {
			TestMod.LOGGER.error("FABRICATED-TEST hidden default FAILED: could not create a pig");
			return;
		}
		try {
			Object result = self.invoke(pig);
			if (result == pig && helperRan) {
				TestMod.LOGGER.info("FABRICATED-TEST hidden default OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST hidden default FAILED: {}.self() on a pig returned {}, the mixin's helper ran: {}",
						self.getDeclaringClass().getName(), result, helperRan);
			}
		} catch (InvocationTargetException e) {
			TestMod.LOGGER.error("FABRICATED-TEST hidden default FAILED", e.getCause());
		} catch (IllegalAccessException e) {
			TestMod.LOGGER.error("FABRICATED-TEST hidden default FAILED", e);
		}
	}

	/** The public default {@code LivingEntity self()} of an interface {@code LivingEntity} implements, or null. */
	private static Method platformSelf() {
		for (Class<?> itf : LivingEntity.class.getInterfaces()) {
			try {
				Method m = itf.getMethod("self");
				if (m.isDefault() && m.getReturnType() == LivingEntity.class) return m;
			} catch (NoSuchMethodException e) {
				// Not this interface's.
			}
		}
		return null;
	}
}
