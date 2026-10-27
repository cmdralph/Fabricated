package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** {@link ForgeInteractionTest}'s use of an entity, through 1.21.11's interaction packet. */
final class ForgeInteractions {
	private ForgeInteractions() {
	}

	/** The packet a client sends as its player uses the entity, clicking a point on it, handled by the server. */
	static void interact(ServerPlayer player, Entity entity, InteractionHand hand) {
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(entity, false, hand, new Vec3(0, 1, 0)));
	}
}
