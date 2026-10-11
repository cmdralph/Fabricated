package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.context.PacketContextImpl;
import net.minecraft.network.Connection;

/** Every connection carries Fabric's {@link PacketContext}. Adds a field and a method only. */
@Mixin(Connection.class)
abstract class ConnectionMixin implements PacketContextProvider {
	@Unique
	private volatile @Nullable PacketContextImpl fabricated$packetContext;

	@Override
	public PacketContext getPacketContext() {
		PacketContextImpl context = this.fabricated$packetContext;
		if (context == null) {
			synchronized (this) {
				context = this.fabricated$packetContext;
				if (context == null) this.fabricated$packetContext = context = new PacketContextImpl((Connection) (Object) this);
			}
		}
		return context;
	}
}
