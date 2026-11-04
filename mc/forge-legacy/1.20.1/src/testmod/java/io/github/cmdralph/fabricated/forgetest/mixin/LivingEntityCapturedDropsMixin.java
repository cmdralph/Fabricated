package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeDropCaptureTest;
import net.minecraft.class_1282;
import net.minecraft.class_1309;

/**
 * Stands in for Porting Lib's hook at the return of {@code LivingEntity.dropAllDeathLoot} ({@code method_16080}), which
 * takes the drops Forge's capture holds and adds them to the level (see {@link ForgeDropCaptureTest}). Its priority puts
 * it after Porting Lib's, at the default one, where both are installed: Porting Lib's then takes the drops.
 */
@Mixin(value = class_1309.class, priority = 1500)
public abstract class LivingEntityCapturedDropsMixin {
	@Inject(method = "method_16080", at = @At("RETURN"))
	private void fabricatedForgeTest$takeCapturedDrops(class_1282 source, CallbackInfo ci) {
		ForgeDropCaptureTest.takeCapturedDrops((class_1309) (Object) this);
	}
}
