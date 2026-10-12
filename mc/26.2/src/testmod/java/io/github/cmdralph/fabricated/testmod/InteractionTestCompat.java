package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/** Entity interaction for {@link InteractionTest} (26.2+): NeoForge's one entity interaction event, with the clicked point. */
final class InteractionTestCompat {
	private InteractionTestCompat() {
	}

	static InteractionResult interact(Player player, Entity entity) {
		return CommonHooks.onInteractEntity(player, entity, InteractionHand.MAIN_HAND, new Vec3(0, 1, 0));
	}
}
