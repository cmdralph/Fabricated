package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.sound.FabricSoundStreams;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;

/** Fabric sound instances' own audio streams, where NeoForge asks the instance for its stream (see {@link FabricSoundStreams}). */
@Mixin(SoundEngine.class)
abstract class SoundEngineStreamMixin {
	@WrapOperation(method = "play", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getStream(Lnet/minecraft/client/sounds/SoundBufferLibrary;Lnet/minecraft/client/resources/sounds/Sound;Z)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<AudioStream> fabricated$fabricAudioStream(SoundInstance instance, SoundBufferLibrary library, Sound sound, boolean looping,
			Operation<CompletableFuture<AudioStream>> original) {
		return FabricSoundStreams.providesStream(instance)
				? FabricSoundStreams.stream(instance, library, sound, looping)
				: original.call(instance, library, sound, looping);
	}
}
