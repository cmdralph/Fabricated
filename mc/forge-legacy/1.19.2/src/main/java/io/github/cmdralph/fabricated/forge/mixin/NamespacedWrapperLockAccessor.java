package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The lock of Forge 43's wrappers of the registries it manages for vanilla, which refuses an entry registered through
 * vanilla's API ({@link ForgeRegistryWrapperLockMixin}). Forge's classes, package-private: named as strings, and nothing
 * in this mixin is remapped.
 */
@Mixin(targets = {"net.minecraftforge.registries.NamespacedWrapper", "net.minecraftforge.registries.NamespacedDefaultedWrapper"}, remap = false)
interface NamespacedWrapperLockAccessor {
	@Accessor(value = "locked", remap = false)
	void fabricated$setLocked(boolean locked);
}
