package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import io.github.cmdralph.fabricated.forge.resource.FabricDataPackDefaults;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.DataPackConfig;

/**
 * Adds Fabric API's default data packs to those Forge 43's world creation screen starts a new world with
 * ({@link FabricDataPackDefaults}): where it loads its first data, and where it makes the screen, which keeps them for
 * the world. Worked out once, as upstream's hook does, for both.
 */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenFabricDataPacksMixin {
	@Unique
	private static DataPackConfig fabricated$defaults;

	@ModifyArg(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createDefaultLoadConfig(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/DataPackConfig;)Lnet/minecraft/server/WorldLoader$InitConfig;"), index = 1)
	private static DataPackConfig fabricated$loadWithFabricPacks(DataPackConfig forge) {
		return fabricated$defaults = FabricDataPackDefaults.with(forge);
	}

	@ModifyArg(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/world/level/DataPackConfig;Lnet/minecraft/client/gui/screens/worldselection/WorldGenSettingsComponent;)V"), index = 1)
	private static DataPackConfig fabricated$keepFabricPacks(DataPackConfig forge) {
		return fabricated$defaults != null ? fabricated$defaults : FabricDataPackDefaults.with(forge);
	}
}
