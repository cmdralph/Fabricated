package io.github.cmdralph.fabricated.neoforge.entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** The parts of the entity-event bridge whose names differ by version: Fabric's level-change events ({@code ServerEntityLevelChangeEvents}). */
final class EntityEventsCompat {
	private EntityEventsCompat() {
	}

	static void afterEntityChange(Entity original, Entity copy, ServerLevel origin, ServerLevel destination) {
		ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.invoker().afterChangeLevel(original, copy, origin, destination);
	}

	static void afterPlayerChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.invoker().afterChangeLevel(player, origin, destination);
	}

	/** The damage the entity took after armor and other reductions. */
	static float damageTaken(LivingDamageEvent.Post event) {
		return event.getInflictedDamage();
	}
}
