package net.fabricmc.fabric.mixin.networking.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import io.github.cmdralph.fabricated.neoforge.networking.HasConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Fabricated's version of Fabric API's accessor (used by {@code ServerConfigurationNetworking.getServer}). It
 * declares upstream's {@code getConnection} through {@link HasConnection}: NeoForge already adds a public
 * {@code getConnection()}, so Mixin can't generate it.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor extends HasConnection {
	@Accessor
	MinecraftServer getServer();
}
