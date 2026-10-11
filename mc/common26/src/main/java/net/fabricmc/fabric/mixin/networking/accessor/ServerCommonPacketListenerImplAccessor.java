package net.fabricmc.fabric.mixin.networking.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Fabricated's version of Fabric API's accessor (used by {@code ServerConfigurationNetworking.getServer}). It
 * omits upstream's {@code getConnection} accessor: NeoForge already adds a public {@code getConnection()}.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor {
	@Accessor
	MinecraftServer getServer();
}
