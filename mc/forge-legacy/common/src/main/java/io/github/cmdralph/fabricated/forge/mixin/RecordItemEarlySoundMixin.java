package io.github.cmdralph.fabricated.forge.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.EarlyRegistryObjects;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * A music disc made for a sound not registered yet ({@link EarlyRegistryObjects}, CORPUS-73): Forge 47's patch of
 * vanilla's constructor looks the sound's delegate up and threw. Such a disc keeps the sound itself; one made for a
 * registered sound keeps Forge's delegate. The supplier is Forge's field, set last in the constructor. Forge 61 has
 * no music disc item (jukebox songs are data).
 */
@Mixin(RecordItem.class)
abstract class RecordItemEarlySoundMixin {
	@Shadow(remap = false)
	@Final
	@Mutable
	private Supplier<SoundEvent> soundSupplier;

	@WrapOperation(method = "<init>(ILnet/minecraft/sounds/SoundEvent;Lnet/minecraft/world/item/Item$Properties;I)V", remap = false, at = @At(value = "INVOKE",
			target = "Lnet/minecraftforge/registries/IForgeRegistry;getDelegateOrThrow(Ljava/lang/Object;)Lnet/minecraft/core/Holder$Reference;", remap = false))
	private Holder.Reference<?> fabricated$delegateIfRegistered(IForgeRegistry<?> sounds, Object sound, Operation<Holder.Reference<?>> getDelegate) {
		return EarlyRegistryObjects.delegate(sounds, sound);
	}

	@Inject(method = "<init>(ILnet/minecraft/sounds/SoundEvent;Lnet/minecraft/world/item/Item$Properties;I)V", remap = false, at = @At("TAIL"))
	private void fabricated$soundItselfIfUnregistered(int comparatorOutput, SoundEvent sound, Item.Properties properties, int lengthInSeconds, CallbackInfo ci) {
		if (soundSupplier == null) soundSupplier = EarlyRegistryObjects.itself(sound);
	}
}
