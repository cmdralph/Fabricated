package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * Fabric's configuration-task methods on the configuration listener, mapped to NeoForge: tasks are added to
 * NeoForge's task queue (during {@code ServerConfigurationConnectionEvents.CONFIGURE}) and finished through
 * NeoForge's {@code finishCurrentTask}. Adds methods only.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class ServerConfigurationPacketListenerImplMixin implements FabricServerConfigurationPacketListenerImpl {
	@Override
	public void addTask(ConfigurationTask task) {
		NetworkingBridge.addConfigurationTask((ServerConfigurationPacketListenerImpl) (Object) this, task);
	}

	@Override
	public void completeTask(ConfigurationTask.Type key) {
		((ServerConfigurationPacketListenerImpl) (Object) this).finishCurrentTask(key);
	}
}
