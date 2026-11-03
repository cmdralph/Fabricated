package fuzs.forgeconfigapiport.api.config.v2;

import java.util.Objects;

import io.github.cmdralph.fabricated.forge.config.ForgeConfigApiPortBridge;
import net.fabricmc.fabric.api.event.Event;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge Config API Port's per-mod config events (its version 8, for 1.20.1), fired from Forge's {@code ModConfigEvent}s
 * for the mod's configs, which Forge hands the mod's container.
 */
public final class ModConfigEvents {
	private ModConfigEvents() {
	}

	public static Event<Loading> loading(String modId) {
		return ForgeConfigApiPortBridge.events(Objects.requireNonNull(modId, "mod id is null")).loading();
	}

	public static Event<Reloading> reloading(String modId) {
		return ForgeConfigApiPortBridge.events(Objects.requireNonNull(modId, "mod id is null")).reloading();
	}

	public static Event<Unloading> unloading(String modId) {
		return ForgeConfigApiPortBridge.events(Objects.requireNonNull(modId, "mod id is null")).unloading();
	}

	@FunctionalInterface
	public interface Loading {
		void onModConfigLoading(ModConfig config);
	}

	@FunctionalInterface
	public interface Reloading {
		void onModConfigReloading(ModConfig config);
	}

	@FunctionalInterface
	public interface Unloading {
		void onModConfigUnloading(ModConfig config);
	}
}
