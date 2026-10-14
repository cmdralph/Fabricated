package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.fabricmc.fabric.impl.networking.FabricRegistryByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** Upstream's {@link FabricRegistryByteBuf} on 1.21.11, as upstream (see the interface). Adds a field and methods only. */
@Mixin(RegistryFriendlyByteBuf.class)
abstract class RegistryFriendlyByteBufMixin implements FabricRegistryByteBuf {
	@Unique
	private @Nullable Set<Identifier> fabricated$sendableConfigurationChannels;

	@Override
	public void fabric_setSendableConfigurationChannels(Set<Identifier> globalChannels) {
		this.fabricated$sendableConfigurationChannels = Objects.requireNonNull(globalChannels);
	}

	@Override
	public @Nullable Set<Identifier> fabric_getSendableConfigurationChannels() {
		return this.fabricated$sendableConfigurationChannels;
	}
}
