package net.fabricmc.fabric.impl.networking.context;

import com.mojang.authlib.GameProfile;
import org.jspecify.annotations.NonNull;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

/**
 * The keys of Fabric API's values in a {@link PacketContext}, which both its constants and {@link PacketContextImpl}'s
 * are (Fabricated, NET-7). They live here, apart from both: initializing a class first initializes the interfaces with
 * default methods it implements, so {@code PacketContextImpl}, initialized first by a connection's first context,
 * initialized {@code PacketContext}, whose constants then read its keys before they were set and stayed null, and no
 * value was found through them. Made without {@link PacketContext#key}, whose call would initialize
 * {@code PacketContext} from here, the same cycle.
 */
public final class PacketContextKeys {
	public static final PacketContext.Key<MinecraftServer> SERVER_INSTANCE = key("server_instance");
	public static final PacketContext.Key<RegistryAccess> REGISTRY_ACCESS = key("registry_access");
	public static final PacketContext.Key<GameProfile> GAME_PROFILE = key("game_profile");
	public static final PacketContext.Key<@NonNull Connection> CONNECTION = key("connection");

	private PacketContextKeys() {
	}

	private static <T> PacketContext.Key<T> key(String path) {
		return new PacketContextImpl.KeyImpl<>(Identifier.fromNamespaceAndPath("fabric", path));
	}
}
