package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonNetworkHandlerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * Fabric's configuration-task methods on the configuration listener, mapped to NeoForge: tasks are added to
 * NeoForge's task queue (during {@code ServerConfigurationConnectionEvents.CONFIGURE}) and finished through
 * NeoForge's {@code finishCurrentTask}, on the server thread. Adds methods only.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class ServerConfigurationPacketListenerImplMixin implements FabricServerConfigurationNetworkHandler {
	@Override
	public void addTask(ConfigurationTask task) {
		NetworkingBridge.addConfigurationTask((ServerConfigurationPacketListenerImpl) (Object) this, task);
	}

	@Override
	public void completeTask(ConfigurationTask.Type key) {
		ServerConfigurationPacketListenerImpl listener = (ServerConfigurationPacketListenerImpl) (Object) this;
		MinecraftServer server = ((ServerCommonNetworkHandlerAccessor) listener).getServer();
		// Fabric mods finish tasks from configuration handlers, which run on the network thread; the next task must start
		// on the server thread (vanilla's spawn preparation loads chunks).
		if (server.isSameThread()) {
			listener.finishCurrentTask(key);
		} else {
			server.execute(() -> {
				if (listener.getConnection().isConnected()) listener.finishCurrentTask(key);
			});
		}
	}
}
