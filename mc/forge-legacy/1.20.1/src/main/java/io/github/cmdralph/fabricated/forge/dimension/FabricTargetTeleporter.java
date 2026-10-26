package io.github.cmdralph.fabricated.forge.dimension;

import java.util.function.Function;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Native fabric-dimensions-v1 on Forge 47 (L4): the teleporter an entity changes dimension with while a Fabric mod
 * teleports it to a target of its own ({@code FabricDimensions.teleport}). Forge 47 moved vanilla's dimension change
 * into {@code changeDimension(ServerLevel, ITeleporter)} and a lambda the teleporter calls, where the End's special
 * cases (the obsidian platform, a player's credits on leaving the End for the overworld) depend on the teleporter: it
 * asks for no portal and is not vanilla's, which leaves them out as upstream's hook into vanilla's method did by hiding
 * the End's key. Where the entity lands still comes from the teleporter it replaces, which asks the entity's
 * {@code findDimensionEntryPoint}, which upstream's hook answers with the target.
 */
public final class FabricTargetTeleporter implements ITeleporter {
	private final ITeleporter replaced;

	public FabricTargetTeleporter(ITeleporter replaced) {
		this.replaced = replaced;
	}

	@Override
	public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
		return replaced.placeEntity(entity, currentWorld, destWorld, yaw, spawnPortal -> repositionEntity.apply(false));
	}

	@Override
	public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
		return replaced.getPortalInfo(entity, destWorld, defaultPortalInfo);
	}

	@Override
	public boolean isVanilla() {
		return false;
	}

	@Override
	public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
		return replaced.playTeleportSound(player, sourceWorld, destWorld);
	}
}
