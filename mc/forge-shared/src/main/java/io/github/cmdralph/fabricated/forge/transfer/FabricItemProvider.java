package io.github.cmdralph.fabricated.forge.transfer;

import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

/**
 * One of Forge's capabilities of a Fabric mod's item stack, there once Fabric's lookup finds a storage of its kind for
 * the stack. Fabric mods register storages for their items while they initialize, but a stack may be made before then,
 * so a stack without one is asked again each time; once found, the handler stays, as Forge's own item handlers do.
 */
final class FabricItemProvider<H> implements ICapabilityProvider {
	private final Kind<H> kind;
	private final ItemStack stack;
	private @Nullable H handler;
	private LazyOptional<H> capability = LazyOptional.empty();

	FabricItemProvider(Kind<H> kind, ItemStack stack) {
		this.kind = kind;
		this.stack = stack;
	}

	/**
	 * A capability a provider answers: the handler it makes for a stack, over Fabric's storage for the stack, and whether
	 * the handler found that storage.
	 */
	record Kind<H>(Capability<? super H> capability, Function<ItemStack, H> handler, Predicate<H> found) {
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
		if (capability != kind.capability()) return LazyOptional.empty();
		if (!this.capability.isPresent()) {
			if (handler == null) handler = kind.handler().apply(stack);
			if (!kind.found().test(handler)) return LazyOptional.empty();
			H found = handler;
			this.capability = LazyOptional.of(() -> found);
		}
		return this.capability.cast();
	}
}
