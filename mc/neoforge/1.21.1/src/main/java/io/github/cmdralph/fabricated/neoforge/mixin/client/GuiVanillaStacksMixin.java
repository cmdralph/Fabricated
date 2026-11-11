package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Objects;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.neoforge.rendering.VanillaHudStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.gui.GuiLayerManager;

/**
 * Vanilla's HUD layer stacks for Fabric mods' hooks in {@code Gui}'s constructor (HUD-7). Vanilla's constructor builds
 * its two stacks as locals, then reads its root stack ({@code layers}) to add them to it, and Fabric mods hook that read
 * to add their own layers to a stack they capture (Apoli's resource bars). NeoForge 21.1 builds each stack as a layer
 * manager of its own and never reads {@code layers}, which it leaves empty and unused. Pre-flight points such hooks at
 * {@link #fabricated$read$layers}, a seam holding vanilla's stacks as locals, as vanilla's constructor holds them at the
 * read, and making the read. It runs where NeoForge's constructor reads its root manager, with both stacks built and
 * neither copied into the root yet; what the hooks added then goes into NeoForge's stacks ({@link VanillaHudStacks}).
 * The seam is public and not final, and the mixin has the lowest priority, so Mixin applies Fabric mods' injectors to
 * it: it takes one at a field access in a method another mixin merged only from a mixin of a higher priority than that
 * one's, which pre-flight checks.
 */
@Mixin(value = Gui.class, priority = Integer.MIN_VALUE)
public abstract class GuiVanillaStacksMixin {
	@Shadow
	@Final
	private LayeredDraw layers;

	/** Vanilla's stacks while the seam runs. */
	@Unique
	private VanillaHudStacks fabricated$stacks;

	// The first of NeoForge's managers there is the player's health group it adds to the first stack; the stacks follow.
	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;layerManager:Lnet/neoforged/neoforge/client/gui/GuiLayerManager;",
			opcode = Opcodes.GETFIELD))
	private void fabricated$vanillaStacks(Minecraft minecraft, CallbackInfo ci, @Local(ordinal = 1) GuiLayerManager first,
			@Local(ordinal = 2) GuiLayerManager second) {
		fabricated$stacks = new VanillaHudStacks(first, second);
		fabricated$read$layers(minecraft);
		fabricated$stacks.addToNeoForge();
		fabricated$stacks = null;
	}

	/** The seam: vanilla's stacks, as the locals its constructor holds there, then its read of the root stack. */
	public void fabricated$read$layers(Minecraft minecraft) {
		LayeredDraw first = fabricated$stacks.first();
		LayeredDraw second = fabricated$stacks.second();
		Objects.requireNonNull(this.layers);
	}
}
