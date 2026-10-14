package net.fabricmc.fabric.impl.networking;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

/**
 * Upstream's interface on {@code RegistryFriendlyByteBuf} (1.21.11), which {@code fabric-particles-v1} reads to
 * choose a particle encoding the receiving client understands. Fabricated never records the channels, so callers see
 * {@code null} and use their fallback encoding, which every client reads.
 */
public interface FabricRegistryByteBuf {
	void fabric_setSendableConfigurationChannels(Set<Identifier> globalChannels);

	@Nullable
	Set<Identifier> fabric_getSendableConfigurationChannels();
}
