package net.fabricmc.fabric.mixin.biome;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * The spawn costs of Forge's spawn settings builder, which has no way to remove one, for Fabric's biome modifications
 * to clear a cost (Fabricated, L4 on Forge 47). Forge's access transformer makes the field protected for its own
 * builder; the name is the one its comment gives.
 */
@Mixin(MobSpawnSettings.Builder.class)
public interface MobSpawnSettingsBuilderAccessor {
	@Accessor("mobSpawnCosts")
	Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> fabricated$getMobSpawnCosts();
}
