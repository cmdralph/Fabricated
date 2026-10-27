package io.github.cmdralph.fabricated.neoforge.interaction;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The version-specific part of {@link InteractionBridge} (1.21.1). As upstream, Fabric's entity callback runs for
 * both kinds of entity interaction: with the clicked point from {@link PlayerInteractEvent.EntityInteractSpecific}, and
 * without one from {@link PlayerInteractEvent.EntityInteract} (server side, where upstream hooks the packet handler).
 * The block break event is {@link BlockEvent.BreakEvent}.
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
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.EntityInteract.class, event -> {
			if (event.getLevel().isClientSide()) return;
			InteractionResult result = InteractionBridge.useEntity(event.getEntity(), event.getHand(), event.getTarget(), null);
			if (result != null) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BlockEvent.BreakEvent.class, event -> {
			if (event.getLevel() instanceof Level level && !InteractionBridge.beforeBreak(level, event.getPlayer(), event.getPos(), event.getState())) {
				event.setCanceled(true);
			}
		});
	}
}
