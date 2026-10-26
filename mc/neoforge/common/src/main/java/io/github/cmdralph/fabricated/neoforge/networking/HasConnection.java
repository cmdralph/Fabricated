package io.github.cmdralph.fabricated.neoforge.networking;

import net.minecraft.network.Connection;

/**
 * The {@code getConnection()} of Fabric API's {@code ServerCommonPacketListenerImplAccessor}, which some Fabric API
 * modules (such as data-attachment on 1.21.11) call through the accessor. NeoForge already adds a public
 * {@code getConnection()} to {@code ServerCommonPacketListenerImpl}, so Mixin can't generate an accessor for it; the
 * accessor extends this plain interface instead, and NeoForge's method implements it.
 */
public interface HasConnection {
	Connection getConnection();
}
