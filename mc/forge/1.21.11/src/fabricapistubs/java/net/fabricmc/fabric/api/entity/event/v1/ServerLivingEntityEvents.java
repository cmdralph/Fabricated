package net.fabricmc.fabric.api.entity.event.v1;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public final class ServerLivingEntityEvents {
	public static final Event<AllowDamage> ALLOW_DAMAGE = null;
	public static final Event<AfterDamage> AFTER_DAMAGE = null;

	private ServerLivingEntityEvents() {
	}

	@FunctionalInterface
	public interface AllowDamage {
		boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
	}

	@FunctionalInterface
	public interface AfterDamage {
		void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked);
	}
}
