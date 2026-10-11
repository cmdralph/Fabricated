package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.lifecycle.EntityLoadDataAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Makes every entity implement Fabric's {@link EntityLoadData}, as Fabric API's lifecycle module does with an
 * injected interface. Only adds fields and methods; no Minecraft code is changed.
 */
@Mixin(Entity.class)
abstract class EntityMixin implements EntityLoadData, EntityLoadDataAccess {
	@Unique
	private boolean fabricated$loadedFromDisk;
	@Unique
	private @Nullable EntitySpawnReason fabricated$spawnReason;

	@Override
	public boolean isLoadedFromDisk() {
		return fabricated$loadedFromDisk;
	}

	@Override
	public @Nullable EntitySpawnReason spawnReason() {
		return fabricated$spawnReason;
	}

	@Override
	public void fabricated$setLoadedFromDisk(boolean loadedFromDisk) {
		this.fabricated$loadedFromDisk = loadedFromDisk;
	}

	@Override
	public void fabricated$setSpawnReason(@Nullable EntitySpawnReason spawnReason) {
		this.fabricated$spawnReason = spawnReason;
	}
}
