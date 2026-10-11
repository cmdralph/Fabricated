package net.fabricmc.fabric.mixin.networking.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/** Fabricated's version of Fabric API's accessor (used by {@code ServerLoginNetworking.getServer}). */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerImplAccessor {
	@Accessor
	MinecraftServer getServer();

	@Accessor
	Connection getConnection();
}
