package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NeoForge's handler tables (Minecraft 1.21.11 and later keep them beside the payload registrations), for Fabric
 * payload types registered again after NeoForge's registration event ({@code FabricPayloads}, NET-3). No code is changed.
 */
@Mixin(NetworkRegistry.class)
public interface NetworkRegistryHandlersAccessor {
	@Accessor("SERVERBOUND_HANDLERS")
	static Map<ConnectionProtocol, ? extends Map<Identifier, ?>> fabricated$serverboundHandlers() {
		throw new AssertionError();
	}

	@Accessor("CLIENTBOUND_HANDLERS")
	static Map<ConnectionProtocol, ? extends Map<Identifier, ?>> fabricated$clientboundHandlers() {
		throw new AssertionError();
	}
}
