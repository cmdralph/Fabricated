package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Game and Fabric API calls the forge-legacy test mod's shared checks make, as Minecraft 1.20.1 and Fabric API 0.92
 * name them.
 */
final class ForgeTestCompat {
	private ForgeTestCompat() {
	}

	/** A fake player in the level, as a Fabric mod gets one: Fabric API's, so only checks that need Fabric API's jar ask. */
	static ServerPlayer fakePlayer(ServerLevel level) {
		return FakePlayer.get(level);
	}

	/** Whether the player is one {@link #fakePlayer} gives. */
	static boolean isFakePlayer(Player player) {
		return player instanceof FakePlayer;
	}

	static Level level(Entity entity) {
		return entity.level();
	}

	static Registry<Biome> biomes(RegistryAccess registries) {
		return registries.registryOrThrow(Registries.BIOME);
	}

	/** A sound event, made as a Fabric mod makes one. */
	static SoundEvent sound(ResourceLocation id) {
		return SoundEvent.createVariableRangeEvent(id);
	}

	/** The id of the built-in pack registered under {@code namespace:path}: Fabric API 0.92 names it by its id. */
	static String builtinPackId(String namespace, String path) {
		return namespace + ":" + path;
	}

	/** A fluid that makes no new source blocks, as Fabric API's test fluids. */
	abstract static class NonRenewableFluid extends FlowingFluid {
		@Override
		public boolean canConvertToSource(Level level) {
			return false;
		}
	}
}
