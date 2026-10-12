package io.github.cmdralph.fabricated.neoforge.interaction;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * The version-specific part of {@link InteractionBridge} (26.1): Fabric's entity callback runs once per interaction,
 * from NeoForge's {@link PlayerInteractEvent.EntityInteractSpecific} (which carries the clicked point and comes before
 * {@link PlayerInteractEvent.EntityInteract}); the block break event is {@link BreakBlockEvent}.
 */
final class InteractionCompat {
	private InteractionCompat() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.EntityInteractSpecific.class, event -> {
			InteractionResult result = InteractionBridge.useEntity(event.getEntity(), event.getHand(), event.getTarget(), event.getLocalPos());
			if (result != null) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BreakBlockEvent.class, event -> {
			if (event.getLevel() instanceof Level level && !InteractionBridge.beforeBreak(level, event.getPlayer(), event.getPos(), event.getState())) {
				event.setCanceled(true);
			}
		});
	}
}
