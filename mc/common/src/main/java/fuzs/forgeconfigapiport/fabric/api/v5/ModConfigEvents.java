package fuzs.forgeconfigapiport.fabric.api.v5;

import java.util.Objects;

import io.github.cmdralph.fabricated.neoforge.config.ForgeConfigApiPortBridge;
import net.fabricmc.fabric.api.event.Event;
import net.neoforged.fml.config.ModConfig;

/** Forge Config API Port's per-mod config events, fired from NeoForge's {@code ModConfigEvent}s on the mod's bus. */
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
		void onModConfigLoading(ModConfig modConfig);
	}

	@FunctionalInterface
	public interface Reloading {
		void onModConfigReloading(ModConfig modConfig);
	}

	@FunctionalInterface
	public interface Unloading {
		void onModConfigUnloading(ModConfig modConfig);
	}
}
