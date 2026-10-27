package net.fabricmc.fabric.api.event.player;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that the forge test mod calls, in Mojang's names.
 */
@FunctionalInterface
public interface UseItemCallback {
	Event<UseItemCallback> EVENT = null;

	InteractionResult interact(Player player, Level world, InteractionHand hand);
}
