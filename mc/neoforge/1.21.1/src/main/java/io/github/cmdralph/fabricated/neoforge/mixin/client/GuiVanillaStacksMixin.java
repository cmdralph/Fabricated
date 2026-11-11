package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.rendering.VanillaHudStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.gui.GuiLayerManager;

/**
 * Vanilla's HUD layer stacks for Fabric mods' hooks in {@code Gui}'s constructor (HUD-7). Vanilla's constructor builds
 * its two stacks as locals, then reads its root stack ({@code layers}) to add them to it, and Fabric mods hook that read
 * to add their own layers to a stack they capture (Apoli's resource bars). NeoForge 21.1 builds each stack as a layer
 * manager of its own, adds each to its root manager, which copies the stack's layers, and never reads {@code layers},
 * which it leaves empty and unused. Pre-flight points such hooks at {@link #fabricated$read$layers}, a seam holding
 * vanilla's stacks as locals, as vanilla's constructor holds them at the read, and making the read.
 *
 * <p>The seam runs as NeoForge's constructor adds vanilla's first stack to its root, which vanilla's read comes right
 * before, and what the hooks did to each vanilla stack goes into NeoForge's as NeoForge adds it ({@link VanillaHudStacks}):
 * a stack is told by the layers NeoForge put into it, not by where the constructor holds it. The wrapper requires
 * nothing ({@code require = 0}): where NeoForge's constructor adds no stack so, the seam runs as the constructor ends,
 * and the report names the layers the hooks added, which aren't drawn. The seam is public and not final, and the mixin
 * has the lowest priority, so Mixin applies Fabric mods' injectors to it: it takes one at a field access in a method
 * another mixin merged only from a mixin of a higher priority than that one's, which pre-flight checks.
 */
@Mixin(value = Gui.class, priority = Integer.MIN_VALUE)
public abstract class GuiVanillaStacksMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private LayeredDraw layers;
	@Shadow
	@Final
	private GuiLayerManager layerManager;

	/** Vanilla's stacks from the seam's run until the constructor ends. */
	@Unique
	private VanillaHudStacks fabricated$stacks;

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/client/gui/GuiLayerManager;add(Lnet/neoforged/neoforge/client/gui/GuiLayerManager;Ljava/util/function/BooleanSupplier;)Lnet/neoforged/neoforge/client/gui/GuiLayerManager;"),
			require = 0)
	private GuiLayerManager fabricated$addStack(GuiLayerManager manager, GuiLayerManager stack, BooleanSupplier shown, Operation<GuiLayerManager> add) {
		if (manager == layerManager) {
			if (fabricated$stacks == null && VanillaHudStacks.isFirst(stack)) fabricated$runSeam();
			if (fabricated$stacks != null) fabricated$stacks.placeInto(stack);
		}
		return add.call(manager, stack, shown);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricated$stacksBuilt(Minecraft minecraft, CallbackInfo ci) {
		if (fabricated$stacks == null) fabricated$runSeam();
		fabricated$stacks.finish();
		fabricated$stacks = null;
	}

	@Unique
	private void fabricated$runSeam() {
		fabricated$stacks = new VanillaHudStacks();
		fabricated$read$layers(minecraft);
	}

	/** The seam: vanilla's stacks, as the locals its constructor holds there, then its read of the root stack. */
	public void fabricated$read$layers(Minecraft minecraft) {
		LayeredDraw first = fabricated$stacks.first();
		LayeredDraw second = fabricated$stacks.second();
		Objects.requireNonNull(this.layers);
	}
}
