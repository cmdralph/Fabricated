package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.FabricatedMod;
import net.minecraft.core.Registry;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric mods register their entries through vanilla's API, which Forge 43's wrapper of a registry it manages refuses
 * once Forge's vanilla snapshot has locked it ("Can not register to a locked registry"), even while Forge's own registry
 * is open: in that registry's event, and while the Fabric mods' entrypoints run ({@link FabricatedMod}). As Forge 47
 * does, opening the registry ({@code unfreeze}) opens its wrapper too, and freezing it locks the wrapper again. Forge
 * calls {@code unfreeze} for a registry's event alone. Forge's own class, whose names are not remapped.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
abstract class ForgeRegistryWrapperLockMixin<V> {
	@Shadow
	abstract Registry<V> getWrapper();

	@Inject(method = "freeze", at = @At("RETURN"))
	private void fabricated$lockWrapper(CallbackInfo ci) {
		if (getWrapper() instanceof NamespacedWrapperLockAccessor wrapper) wrapper.fabricated$setLocked(true);
	}

	@Inject(method = "unfreeze", at = @At("RETURN"))
	private void fabricated$unlockWrapper(CallbackInfo ci) {
		if (getWrapper() instanceof NamespacedWrapperLockAccessor wrapper) wrapper.fabricated$setLocked(false);
	}
}
