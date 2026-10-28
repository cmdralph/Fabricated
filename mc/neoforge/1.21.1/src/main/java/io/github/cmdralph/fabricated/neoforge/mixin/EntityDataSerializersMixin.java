package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.objectbuilder.EntityDataSerializerBridge;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;

/** A mod's call to vanilla's {@code registerSerializer} registers in NeoForge's registry (see {@link EntityDataSerializerBridge}). */
@Mixin(EntityDataSerializers.class)
abstract class EntityDataSerializersMixin {
	@Inject(method = "registerSerializer", at = @At("HEAD"), cancellable = true)
	private static void fabricated$toNeoForgeRegistry(EntityDataSerializer<?> serializer, CallbackInfo ci) {
		if (EntityDataSerializerBridge.register(serializer)) ci.cancel();
	}
}
