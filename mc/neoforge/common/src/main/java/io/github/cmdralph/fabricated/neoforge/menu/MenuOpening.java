package io.github.cmdralph.fabricated.neoforge.menu;

import java.util.function.Supplier;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric's extended menus ({@code fabric-menu-api-v1}, {@code fabric-screen-handler-api-v1} on 1.21.11) on NeoForge's
 * menu opening data: an extended menu provider writes its opening data where NeoForge asks a provider for extra
 * client data, and the extended menu type reads it back where NeoForge creates the menu on the client. Fabric mods'
 * menus thereby open the same way as NeoForge mods' menus.
 */
public final class MenuOpening {
	/**
	 * Written before Fabric's data, so NeoForge always sends its extra-data opening packet (it falls back to vanilla's,
	 * which carries no data, when nothing was written) and the client can tell the data is Fabric's.
	 */
	private static final byte FABRIC_DATA = 0x46;
	private static final ThreadLocal<ServerPlayer> OPENER = new ThreadLocal<>();

	private MenuOpening() {
	}

	/** Runs {@code open} (the server player's {@code openMenu}) with {@code player} as the player opening a menu. */
	public static <R> R opening(ServerPlayer player, Supplier<R> open) {
		ServerPlayer previous = OPENER.get();
		OPENER.set(player);
		try {
			return open.get();
		} finally {
			if (previous == null) OPENER.remove();
			else OPENER.set(previous);
		}
	}

	/** The player whose {@code openMenu} is running on this thread. */
	public static ServerPlayer opener() {
		ServerPlayer player = OPENER.get();
		if (player == null) throw new IllegalStateException("Fabric menu opening data requested outside of ServerPlayer.openMenu");
		return player;
	}

	public static <D> void write(RegistryFriendlyByteBuf buffer, StreamCodec<? super RegistryFriendlyByteBuf, D> codec, D data) {
		buffer.writeByte(FABRIC_DATA);
		codec.encode(buffer, data);
	}

	public static <D> D read(RegistryFriendlyByteBuf buffer, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
		if (!buffer.isReadable() || buffer.readByte() != FABRIC_DATA) {
			throw new IllegalStateException("An extended menu was opened without its opening data; open it with an ExtendedMenuProvider");
		}
		return codec.decode(buffer);
	}
}
