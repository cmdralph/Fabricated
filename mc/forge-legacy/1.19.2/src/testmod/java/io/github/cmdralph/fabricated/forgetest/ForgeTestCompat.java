package io.github.cmdralph.fabricated.forgetest;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Game and Fabric API calls the forge-legacy test mod's shared checks make, as Minecraft 1.19.2 and Fabric API 0.77
 * name them.
 */
final class ForgeTestCompat {
	private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5d3c2b1a-0000-4000-8000-00000000fa4e"), "[Fabricated test]");

	private ForgeTestCompat() {
	}

	/**
	 * A fake player in the level, as a Fabric mod for 1.19.2 makes one, Fabric API 0.77 having none: Forge's, with a
	 * connection that handles the packets it is handed as a player's does and sends nothing (Forge's own drops them,
	 * {@code FakePlayerNetHandler.handleInteract}).
	 */
	static ServerPlayer fakePlayer(ServerLevel level) {
		FakePlayer player = new FakePlayer(level, PROFILE);
		player.connection = new Listener(level.getServer(), player);
		return player;
	}

	/** Whether the player is one {@link #fakePlayer} gives. */
	static boolean isFakePlayer(Player player) {
		return player instanceof FakePlayer;
	}

	static Level level(Entity entity) {
		return entity.level;
	}

	static Registry<Biome> biomes(RegistryAccess registries) {
		return registries.registryOrThrow(Registry.BIOME_REGISTRY);
	}

	/** A sound event, made as a Fabric mod makes one. */
	static SoundEvent sound(ResourceLocation id) {
		return new SoundEvent(id);
	}

	/** The id of the built-in pack registered under {@code namespace:path}: Fabric API 0.77 names it {@code namespace/path}. */
	static String builtinPackId(String namespace, String path) {
		return namespace + "/" + path;
	}

	/** A fluid that makes no new source blocks, as Fabric API's test fluids. */
	abstract static class NonRenewableFluid extends FlowingFluid {
		@Override
		public boolean canConvertToSource() {
			return false;
		}
	}

	/** The fake player's side of a connection that goes nowhere. */
	private static final class Listener extends ServerGamePacketListenerImpl {
		Listener(MinecraftServer server, ServerPlayer player) {
			super(server, new Connection(PacketFlow.SERVERBOUND), player);
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
		}
	}
}
