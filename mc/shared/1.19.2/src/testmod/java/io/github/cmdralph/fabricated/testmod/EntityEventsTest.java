package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * CI test for fabric-entity-events-v1 on Minecraft 1.19.2, whose Fabric API (0.77) has no mob effect or mob conversion
 * events: once the server has started, a pig is spawned, hurt, and killed; the events seen are logged as one marker
 * line.
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
		TestCompat.afterDamage(entity -> seen(entity, "after_damage"));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			seen(entity, "allow_death");
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> seen(entity, "after_death"));

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
			pig.hurt(DamageSource.GENERIC, 1.0f);
			pig.hurt(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
			TestMod.LOGGER.info("FABRICATED-TEST entity events seen: {}", String.join(",", SEEN));
			pig.discard();
		});
	}
}
