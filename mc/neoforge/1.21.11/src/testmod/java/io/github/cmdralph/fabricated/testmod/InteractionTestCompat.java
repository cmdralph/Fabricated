package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/** Entity interaction for {@link InteractionTest}: NeoForge's entity interaction event with the clicked point. */
final class InteractionTestCompat {
	private InteractionTestCompat() {
	}

	static InteractionResult interact(Player player, Entity entity) {
		return CommonHooks.onInteractEntityAt(player, entity, new Vec3(0, 1, 0), InteractionHand.MAIN_HAND);
	}
}
