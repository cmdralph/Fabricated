package io.github.cmdralph.fabricated.forge.damage;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fabric API's after-damage event (fabric-entity-events-v1, passed through) where Forge 61 and 64 hurt an entity
 * (DAMAGE-1). Upstream fires {@code ServerLivingEntityEvents.AFTER_DAMAGE} at the end of
 * {@code LivingEntity.hurtServer}, reading the damage the entity was dealt from vanilla's copy of it before blocking,
 * which the two versions' patches drop (they pass the damage taken twice where vanilla passes both). On Forge 64
 * upstream names the copy, so pre-flight left the hook out and the event never ran; on Forge 61 it takes the second
 * float local, which there is the damage a shield blocked, so the event ran with that instead. The plan skips the hook
 * on both, and {@code LivingEntityDamageMixin} fires the event with the damage vanilla's copy holds: what
 * {@code applyItemBlocking} is asked about. Forge 65 and 66 keep the copy, and upstream's hook applies there.
 */
public final class DamageEvents {
	/** Fabric API's entity events module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1");

	private DamageEvents() {
	}

	/**
	 * At the end of {@code hurtServer}, as upstream: unless the entity is dead or dying, the event with the damage it was
	 * dealt, the damage it took and whether a shield blocked any.
	 */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (ENABLED && !entity.isDeadOrDying()) Upstream.afterDamage(entity, source, baseDamageTaken, damageTaken, blocked);
	}

	/** Everything that names Fabric's entity events module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
			ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(entity, source, baseDamageTaken, damageTaken, blocked);
		}
	}
}
