package io.github.cmdralph.fabricated.forge.resource.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import io.github.cmdralph.fabricated.forge.resource.FabricResourceConditions;
import net.minecraft.resources.RegistryOps;

/** The registries a decode sees, which Fabric's resource conditions take (see {@link FabricResourceConditions}). */
@Mixin(RegistryOps.class)
public interface RegistryOpsAccessor {
	@Accessor("lookupProvider")
	RegistryOps.RegistryInfoLookup fabricated$lookupProvider();
}
