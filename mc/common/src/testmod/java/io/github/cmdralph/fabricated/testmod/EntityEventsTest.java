package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * CI test for Fabricated's native fabric-entity-events-v1: once the server has started, a pig is spawned, given an
 * effect, has it removed, is hurt, and is killed; the events seen are logged as one marker line.
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
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, ctx) -> {
			seen(entity, "allow_add");
			return true;
		});
		ServerMobEffectEvents.AFTER_ADD.register((effect, entity, ctx) -> seen(entity, "after_add"));
		ServerMobEffectEvents.BEFORE_REMOVE.register((effect, entity, ctx) -> seen(entity, "before_remove"));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			LivingEntity pig = EntityType.PIG.create(level, EntitySpawnReason.TRIGGERED);
			if (pig == null) {
				TestMod.LOGGER.error("FABRICATED-TEST entity events: could not create a pig");
				return;
			}
			subject = pig;
			pig.setPos(0.5, level.getMaxY() - 10, 0.5);
			level.addFreshEntity(pig);
			pig.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
			pig.removeEffect(MobEffects.SPEED);
			pig.hurtServer(level, level.damageSources().generic(), 1.0f);
			pig.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			TestMod.LOGGER.info("FABRICATED-TEST entity events seen: {}", String.join(",", SEEN));
			pig.discard();
		});
	}
}
