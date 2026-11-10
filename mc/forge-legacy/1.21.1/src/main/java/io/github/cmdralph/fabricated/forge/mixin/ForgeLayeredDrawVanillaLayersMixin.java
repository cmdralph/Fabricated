package io.github.cmdralph.fabricated.forge.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/**
 * Vanilla's list of a Forge 52 HUD layer stack holds its layers while {@code Gui}'s constructor builds it, as vanilla's
 * does, and what a Fabric mod put in it is drawn in Forge's order (HUD-6). Forge's stacks ({@code ForgeLayeredDraw},
 * which extends vanilla's {@code LayeredDraw}) keep the layers vanilla's constructor adds by name, for Forge mods to
 * order against, and fill vanilla's list from that order only as the constructor ends ({@code resolveLayers}): until
 * then the list a Fabric mod reads or changes through an accessor is empty, and Apoli's hook, which adds its HUD after
 * the hotbar, stopped the client ("Index: 3, Size: 0"). Each layer a stack takes by name goes into vanilla's list too,
 * as vanilla's {@code add} puts it there (a nested stack as Forge draws it); once Forge has filled the list in its
 * order, what else the list held follows the layer of Forge's it followed, and a layer of Forge's taken out of it stays
 * out. A stack resolved once is Forge's alone from then on.
 */
@Mixin(ForgeLayeredDraw.class)
abstract class ForgeLayeredDrawVanillaLayersMixin {
	@Shadow
	@Final
	private List<ResourceLocation> order;

	/** Each layer the stack took by name, as vanilla's list has it. */
	@Unique
	private final Map<LayeredDraw.Layer, ResourceLocation> fabricated$named = new IdentityHashMap<>();

	@Unique
	private boolean fabricated$resolved;

	@Inject(method = "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/LayeredDraw$Layer;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
	private void fabricated$addLayer(ResourceLocation name, LayeredDraw.Layer layer, CallbackInfoReturnable<ForgeLayeredDraw> cir) {
		fabricated$add(name, layer);
	}

	@Inject(method = "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/LayeredDraw;Ljava/util/function/BooleanSupplier;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
	private void fabricated$addStack(ResourceLocation name, LayeredDraw stack, BooleanSupplier condition, CallbackInfoReturnable<ForgeLayeredDraw> cir) {
		fabricated$add(name, (graphics, delta) -> {
			if (condition.getAsBoolean()) stack.render(graphics, delta);
		});
	}

	@Unique
	private void fabricated$add(ResourceLocation name, LayeredDraw.Layer layer) {
		if (fabricated$resolved) return;
		fabricated$named.put(layer, name);
		((LayeredDrawLayersAccessor) this).fabricated$layers().add(layer);
	}

	/** Forge's resolution added a layer for each name of its order after what the list held, which goes back in around them. */
	@Inject(method = "resolveNested", at = @At("TAIL"))
	private void fabricated$keepOthers(CallbackInfo ci) {
		if (fabricated$resolved) return;
		fabricated$resolved = true;
		List<LayeredDraw.Layer> layers = ((LayeredDrawLayersAccessor) this).fabricated$layers();
		int held = layers.size() - order.size();
		List<LayeredDraw.Layer> resolved = new ArrayList<>(layers.subList(held, layers.size()));
		Map<ResourceLocation, List<LayeredDraw.Layer>> following = new HashMap<>();
		Set<ResourceLocation> kept = new HashSet<>();
		ResourceLocation last = null;
		for (LayeredDraw.Layer layer : layers.subList(0, held)) {
			ResourceLocation name = fabricated$named.get(layer);
			if (name == null) {
				following.computeIfAbsent(last, k -> new ArrayList<>()).add(layer);
			} else {
				kept.add(name);
				last = name;
			}
		}
		layers.clear();
		layers.addAll(following.getOrDefault(null, List.of()));
		for (int i = 0; i < order.size(); i++) {
			if (kept.contains(order.get(i))) layers.add(resolved.get(i));
			layers.addAll(following.getOrDefault(order.get(i), List.of()));
		}
	}
}
