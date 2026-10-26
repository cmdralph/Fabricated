package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.impl.event.lifecycle.EntityLoadDataSetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Makes every entity implement Fabric's {@link EntityLoadData} and its setter side, as Fabric API's lifecycle module
 * does. Only adds fields and methods; no Minecraft code is changed.
 */
@Mixin(Entity.class)
abstract class EntityMixin implements EntityLoadData, EntityLoadDataSetter {
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
	public void fabric_setLoadedFromDisk(boolean loadedFromDisk) {
		this.fabricated$loadedFromDisk = loadedFromDisk;
	}

	@Override
	public void fabric_setSpawnReason(@Nullable EntitySpawnReason spawnReason) {
		this.fabricated$spawnReason = spawnReason;
	}
}
