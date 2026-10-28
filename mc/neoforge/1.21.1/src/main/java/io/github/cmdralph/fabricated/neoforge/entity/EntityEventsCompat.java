package io.github.cmdralph.fabricated.neoforge.entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Fabric's level-change events under this version's API names ({@code ServerEntityWorldChangeEvents}). */
final class EntityEventsCompat {
	private EntityEventsCompat() {
	}

	static void afterEntityChange(Entity original, Entity copy, ServerLevel origin, ServerLevel destination) {
		ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.invoker().afterChangeWorld(original, copy, origin, destination);
	}

	static void afterPlayerChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.invoker().afterChangeWorld(player, origin, destination);
	}
}
