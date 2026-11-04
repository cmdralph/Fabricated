package net.fabricmc.fabric.api.entity.event.v1.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that the forge test mod calls, in Mojang's names.
 */
public final class ServerMobEffectEvents {
	public static final Event<AllowAdd> ALLOW_ADD = null;

	private ServerMobEffectEvents() {
	}

	@FunctionalInterface
	public interface AllowAdd {
		boolean allowAdd(MobEffectInstance effectInstance, LivingEntity entity, EffectEventContext ctx);
	}
}
