package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.core.Vec3i;

/**
 * Modifies the {@code instanceof Vec3i} in {@code Vec3i.equals} (its first class constant; the cast is the second), with
 * the object and the class, as Lithium's collision context hooks modify vanilla's {@code instanceof LivingEntity}
 * checks (PRE-29), and logs once as positions are compared.
 */
@Mixin(Vec3i.class)
public abstract class Vec3iInstanceofMixin {
	@Unique
	private static boolean fabricatedTest$logged;

	@ModifyConstant(method = "equals", constant = @Constant(classValue = Vec3i.class, ordinal = 0))
	private static boolean fabricatedTest$isVec3i(Object other, Class<?> type) {
		boolean is = type.isInstance(other);
		if (is && !fabricatedTest$logged) {
			fabricatedTest$logged = true;
			TestMod.LOGGER.info("FABRICATED-TEST instanceof constant OK");
		}
		return is;
	}
}
