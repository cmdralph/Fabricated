package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * CI test for Fabricated's native fabric-entity-events-v1 (Minecraft 1.21.1, whose Fabric API has no mob effect events):
 * once the server has started, a pig is spawned, hurt, and killed, and a zombie is converted to a drowned; the events
 * seen are logged as one marker line.
 */
public final class EntityEventsTest implements ModInitializer {
	private static final List<String> SEEN = new ArrayList<>();
	private static LivingEntity subject;

	private static void seen(LivingEntity entity, String what) {
		if (entity == subject) SEEN.add(what);
	}

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			seen(entity, "allow_damage");
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> seen(entity, "after_damage"));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			seen(entity, "allow_death");
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> seen(entity, "after_death"));
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, keepEquipment) -> {
			if (previous.getType() == EntityType.ZOMBIE && converted.getType() == EntityType.DROWNED && keepEquipment) {
				TestMod.LOGGER.info("FABRICATED-TEST entity events: mob conversion OK");
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			LivingEntity pig = EntityType.PIG.create(level);
			if (pig == null) {
				TestMod.LOGGER.error("FABRICATED-TEST entity events: could not create a pig");
				return;
			}
			subject = pig;
			pig.setPos(0.5, level.getMaxBuildHeight() - 11, 0.5);
			level.addFreshEntity(pig);
			pig.hurt(level.damageSources().generic(), 1.0f);
			pig.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
			TestMod.LOGGER.info("FABRICATED-TEST entity events seen: {}", String.join(",", SEEN));
			pig.discard();

			// Upstream's own mixin on Mob.convertTo, kept on NeoForge 21.1 (EntityEventsBridge).
			Mob zombie = EntityType.ZOMBIE.create(level);
			if (zombie != null) {
				zombie.setPos(2.5, level.getMaxBuildHeight() - 11, 0.5);
				level.addFreshEntity(zombie);
				Mob drowned = zombie.convertTo(EntityType.DROWNED, true);
				if (drowned != null) drowned.discard();
			}
		});
	}
}
