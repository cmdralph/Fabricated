package io.github.cmdralph.fabricated.neoforge.lifecycle;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Setter side of Fabric's {@link EntityLoadData}, which Fabric API injects into {@link Entity}. Fabricated's
 * {@code EntityMixin} implements both on every entity; the values come from NeoForge's spawn and join events.
 */
public interface EntityLoadDataAccess {
	void fabricated$setLoadedFromDisk(boolean loadedFromDisk);

	void fabricated$setSpawnReason(@Nullable EntitySpawnReason spawnReason);

	static void set(Entity entity, boolean loadedFromDisk) {
		((EntityLoadDataAccess) entity).fabricated$setLoadedFromDisk(loadedFromDisk);
	}

	static void setSpawnReason(Entity entity, @Nullable EntitySpawnReason spawnReason) {
		((EntityLoadDataAccess) entity).fabricated$setSpawnReason(spawnReason);
	}

	static @Nullable EntitySpawnReason spawnReason(Entity entity) {
		return ((EntityLoadData) entity).spawnReason();
	}
}
