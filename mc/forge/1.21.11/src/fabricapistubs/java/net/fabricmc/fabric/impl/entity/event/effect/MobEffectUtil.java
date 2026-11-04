package net.fabricmc.fabric.impl.entity.event.effect;

import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class MobEffectUtil {
	private MobEffectUtil() {
	}

	public static void pushContext(EffectEventContext context) {
		throw new UnsupportedOperationException();
	}

	public static void popContext() {
		throw new UnsupportedOperationException();
	}
}
