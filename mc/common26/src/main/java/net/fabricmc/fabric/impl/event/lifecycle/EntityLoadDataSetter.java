package net.fabricmc.fabric.impl.event.lifecycle;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Setter side of Fabric's {@link EntityLoadData}, under the name and package Fabric API's lifecycle module gives it:
 * some mods reach it (puzzleslib's mob mixin does). Every entity implements both (Fabricated's {@code EntityMixin});
 * the values come from NeoForge's spawn and join events.
 */
public interface EntityLoadDataSetter {
	void fabric_setSpawnReason(@Nullable EntitySpawnReason reason);

	void fabric_setLoadedFromDisk(boolean isLoadedFromDisk);
}
