package net.fabricmc.fabric.api.biome.v1;

public interface BiomeModificationContext {
	WeatherContext getWeather();

	interface WeatherContext {
		void setTemperature(float temperature);
	}
}
