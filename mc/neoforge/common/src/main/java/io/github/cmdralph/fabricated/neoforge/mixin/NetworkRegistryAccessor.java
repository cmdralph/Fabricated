package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NeoForge's payload registrations and their lock, for Fabric payload types registered after NeoForge's registration
 * event ({@code NetworkingBridge}, NET-3); the handler tables later versions keep beside them are reached through
 * {@code FabricPayloads}. No code is changed.
 */
@Mixin(NetworkRegistry.class)
public interface NetworkRegistryAccessor {
	@Accessor("PAYLOAD_REGISTRATIONS")
	static Map<ConnectionProtocol, ? extends Map<Identifier, ?>> fabricated$registrations() {
		throw new AssertionError();
	}

	@Accessor("setup")
	static boolean fabricated$setup() {
		throw new AssertionError();
	}

	@Accessor("setup")
	static void fabricated$setSetup(boolean setup) {
		throw new AssertionError();
	}
}
