package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Entity interaction for {@link InteractionTest} (26.x): the player's interaction with the clicked point, which posts
 * Forge's entity interaction event.
 */
final class InteractionTestCompat {
	private InteractionTestCompat() {
	}

	static InteractionResult interact(Player player, Entity entity) {
		return player.interactOn(entity, InteractionHand.MAIN_HAND, new Vec3(0, 1, 0));
	}
}
