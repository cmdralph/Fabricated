package net.fabricmc.fabric.mixin.registry.sync;

import io.github.cmdralph.fabricated.neoforge.registry.RegistrySyncBridge;
import net.minecraft.core.WritableRegistry;

/**
 * Not a mixin in Fabricated (Fabric API 0.116's name for {@code BuiltInRegistriesAccessor}): Fabric's
 * {@code FabricRegistryBuilder} calls this to register a new registry. NeoForge freezes the root registry after its
 * {@code NewRegistryEvent}, so this opens it while NeoForge's registration events run (when Fabric initializers run)
 * and Fabricated closes it again afterwards.
 */
public interface RegistriesAccessor {
	static WritableRegistry<WritableRegistry<?>> getWRITABLE_REGISTRY() {
		return RegistrySyncBridge.rootForRegistration();
	}
}
