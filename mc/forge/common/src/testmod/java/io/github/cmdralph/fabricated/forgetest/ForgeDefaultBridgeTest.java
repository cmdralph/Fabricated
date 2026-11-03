package io.github.cmdralph.fabricated.forgetest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The forge test mod's part for default bridges (CORPUS-11, CORPUS-63, DEFAULT-1), as the NeoForge test mod's. The JVM
 * can't choose between two defaults with the same name and type on a class declaring neither: calling either there throws
 * ({@code AbstractMethodError} on Java 21, {@code IncompatibleClassChangeError} where the JVM reports the conflict).
 * Fabricated declares the method on the class the mod's interface is added to, calling Forge's default, before Mixin for
 * a class no mixin targets and after its mixins apply for one they do. Two such interfaces, each added by a mixin:
 *
 * <ul>
 * <li>{@link OwnSelf} on {@code LivingEntity} ({@code LivingEntitySelfMixin}), whose default {@code LivingEntity self()}
 * has the name and type of the default {@code LivingEntity} has from its own Forge extension interface
 * ({@code IForgeLivingEntity}). Both calls on a pig must answer the pig.
 * <li>{@link OwnMultipart} on {@code Mob} ({@code MobMultipartMixin}), whose default {@code isMultipartEntity()} has the
 * name and type of the default {@code Mob} has through its superclass {@code Entity}'s extension interface
 * ({@code IForgeEntity}): the method calls the superclass's (DEFAULT-1). Both calls on a pig must answer false, as
 * Forge's default does.
 * </ul>
 */
public final class ForgeDefaultBridgeTest {
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

	private ForgeDefaultBridgeTest() {
	}

	/** At the first server tick. */
	public static void check(MinecraftServer server) {
		// The pig's type by id: 26.3 moved the entity type constants out of EntityType.
		EntityType<?> pigType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
		if (!(pigType.create(server.overworld(), EntitySpawnReason.TRIGGERED) instanceof Mob pig)) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST default bridge FAILED: could not create a pig");
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST inherited default bridge FAILED: could not create a pig");
			return;
		}
		try {
			checkSelf(pig);
			checkMultipart(pig);
		} finally {
			pig.discard();
		}
	}

	/** The bridge for a default of the class's own interface (CORPUS-11, CORPUS-63). */
	private static void checkSelf(Mob pig) {
		if (!((Object) pig instanceof OwnSelf own)) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST default bridge FAILED: LivingEntitySelfMixin did not add OwnSelf to LivingEntity");
			return;
		}
		Object ours;
		try {
			ours = own.self();
		} catch (LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST default bridge FAILED: the test mod's interface's self() on a pig", e);
			return;
		}
		Object forges;
		try {
			Method self = LivingEntity.class.getMethod("self");
			forges = self.invoke(pig);
		} catch (InvocationTargetException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST default bridge FAILED: Forge's self() on a pig", e.getCause());
			return;
		} catch (ReflectiveOperationException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST default bridge FAILED: LivingEntity has no public self()", e);
			return;
		}
		if (ours == pig && forges == pig) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST default bridge OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST default bridge FAILED: self() on a pig answered {} through the test mod's interface and {}"
					+ " through Forge's, where both must answer the pig", ours, forges);
		}
	}

	/** The bridge for a default the class has through its superclass's interface (DEFAULT-1). */
	private static void checkMultipart(Mob pig) {
		if (!((Object) pig instanceof OwnMultipart own)) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST inherited default bridge FAILED: MobMultipartMixin did not add OwnMultipart to Mob");
			return;
		}
		boolean ours;
		try {
			ours = own.isMultipartEntity();
		} catch (LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST inherited default bridge FAILED: the test mod's interface's isMultipartEntity() on a pig", e);
			return;
		}
		Object forges;
		try {
			Method multipart = Entity.class.getMethod("isMultipartEntity");
			forges = multipart.invoke(pig);
		} catch (InvocationTargetException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST inherited default bridge FAILED: Forge's isMultipartEntity() on a pig", e.getCause());
			return;
		} catch (ReflectiveOperationException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST inherited default bridge FAILED: Entity has no public isMultipartEntity()", e);
			return;
		}
		if (!ours && Boolean.FALSE.equals(forges)) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST inherited default bridge OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST inherited default bridge FAILED: isMultipartEntity() on a pig answered {} through the test"
					+ " mod's interface and {} through Forge's, where both must answer false", ours, forges);
		}
	}
}
