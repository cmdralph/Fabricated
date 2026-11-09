package io.github.cmdralph.fabricated.forgetest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1308;
import net.minecraft.class_1309;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for default bridges (CORPUS-11, CORPUS-63, DEFAULT-1) on Forge 47, as the NeoForge
 * test mod's. The JVM can't choose between two defaults with the same name and type on a class declaring neither: calling
 * either there throws ({@code AbstractMethodError} on Java 21, {@code IncompatibleClassChangeError} where the JVM reports
 * the conflict). Fabricated declares the method on the class the mod's interface is added to, calling Forge's default,
 * before Mixin for a class no mixin targets and after its mixins apply for one they do. Two such interfaces, each added by
 * a mixin:
 *
 * <ul>
 * <li>{@link OwnSelf} on {@code LivingEntity} ({@code class_1309}, {@code LivingEntitySelfMixin}), whose default
 * {@code self()} has the name and type of the default {@code LivingEntity} has from its own Forge extension interface
 * ({@code IForgeLivingEntity}). Both calls on a pig must answer the pig.
 * <li>{@link OwnMultipart} on {@code Mob} ({@code class_1308}, {@code MobMultipartMixin}), whose default
 * {@code isMultipartEntity()} has the name and type of the default {@code Mob} has through its superclass
 * {@code Entity}'s extension interface ({@code IForgeEntity}): the method calls the superclass's (DEFAULT-1). Both calls
 * on a pig must answer false, as Forge's default does.
 * </ul>
 *
 * A Fabric mod doesn't compile against Forge: Forge's methods are called reflectively, by their own (unobfuscated) names.
 * The game's classes are named as a Fabric mod for 1.20.1 names them.
 */
public final class ForgeDefaultBridgeTest {
	/** The test mod's own interface, added to {@code LivingEntity}. Its default is never the one that runs there. */
	public interface OwnSelf {
		default class_1309 self() {
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

	/** At the first server tick, on the overworld. */
	public static void check(class_3218 level) {
		class_2378<class_1299<?>> entityTypes = class_7923.field_41177;
		if (!(entityTypes.method_10223(new class_2960("minecraft", "pig")).method_5883(level) instanceof class_1308 pig)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: could not create a pig");
			ForgeTestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: could not create a pig");
			return;
		}
		checkSelf(pig);
		checkMultipart(pig);
	}

	/** The bridge for a default of the class's own interface (CORPUS-11, CORPUS-63). */
	private static void checkSelf(class_1308 pig) {
		if (!((Object) pig instanceof OwnSelf own)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: LivingEntitySelfMixin did not add OwnSelf to LivingEntity");
			return;
		}
		Object ours;
		try {
			ours = own.self();
		} catch (LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: the test mod's interface's self() on a pig", e);
			return;
		}
		Object forges;
		try {
			Method self = class_1309.class.getMethod("self");
			forges = self.invoke(pig);
		} catch (InvocationTargetException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: Forge's self() on a pig", e.getCause());
			return;
		} catch (ReflectiveOperationException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: LivingEntity has no public self()", e);
			return;
		}
		if (ours == pig && forges == pig) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST default bridge OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST default bridge FAILED: self() on a pig answered {} through the test mod's interface and {}"
					+ " through Forge's, where both must answer the pig", ours, forges);
		}
	}

	/** The bridge for a default the class has through its superclass's interface (DEFAULT-1). */
	private static void checkMultipart(class_1308 pig) {
		if (!((Object) pig instanceof OwnMultipart own)) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: MobMultipartMixin did not add OwnMultipart to Mob");
			return;
		}
		boolean ours;
		try {
			ours = own.isMultipartEntity();
		} catch (LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: the test mod's interface's isMultipartEntity() on a pig", e);
			return;
		}
		Object forges;
		try {
			Method multipart = class_1297.class.getMethod("isMultipartEntity");
			forges = multipart.invoke(pig);
		} catch (InvocationTargetException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: Forge's isMultipartEntity() on a pig", e.getCause());
			return;
		} catch (ReflectiveOperationException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: Entity has no public isMultipartEntity()", e);
			return;
		}
		if (!ours && Boolean.FALSE.equals(forges)) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST inherited default bridge OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST inherited default bridge FAILED: isMultipartEntity() on a pig answered {} through the test"
					+ " mod's interface and {} through Forge's, where both must answer false", ours, forges);
		}
	}
}
