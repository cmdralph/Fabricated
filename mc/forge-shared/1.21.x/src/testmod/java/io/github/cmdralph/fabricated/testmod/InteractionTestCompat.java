package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;

/** Entity interaction for {@link InteractionTest}: Forge's entity interaction event with the clicked point. */
final class InteractionTestCompat {
	private InteractionTestCompat() {
	}

	static InteractionResult interact(Player player, Entity entity) {
		return ForgeHooks.onInteractEntityAt(entity, player, new Vec3(0, 1, 0), InteractionHand.MAIN_HAND);
	}
}
