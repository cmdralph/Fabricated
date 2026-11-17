package net.minecraftforge.api.fml.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge Config API Port's view of its config tracker (its version 4, for 1.19.2), provided by Fabricated: Forge's own
 * tracker, whose configs the port's registry adds to.
 */
public interface IConfigTracker {
	IConfigTracker INSTANCE = new IConfigTracker() {
		@Override
		public Map<ModConfig.Type, Set<ModConfig>> configSets() {
			return ConfigTracker.INSTANCE.configSets();
		}

		@Override
		public ConcurrentHashMap<String, ModConfig> fileMap() {
			return ConfigTracker.INSTANCE.fileMap();
		}
	};

	Map<ModConfig.Type, Set<ModConfig>> configSets();

	ConcurrentHashMap<String, ModConfig> fileMap();
}
