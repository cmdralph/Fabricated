package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMarker;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

/**
 * Proves Forge's Mixin applies a translated Fabric mod's mixins (from the overlay's MixinConfigs): logs after the first
 * server tick, whether the server has the interface the test mod's class tweaker injects, whether the test mod's item
 * is still in the registry Forge has frozen and checked by then, whether its item tag's removals applied, whether
 * Fabric's loot table events reached dirt's loot table and drops, whether Fabric's default attribute event reached a
 * cow's defaults, whether Fabric's advancement modify event reached a vanilla advancement, whether its fluids have
 * fluid types, on a client whether Fabric's colour provider registry finds the game's block colours, whether a
 * command registered through Fabric's command event is in the server's dispatcher, whether Fabric's block break
 * events fire as a player breaks blocks, and whether a block Fabric's sleep events admit as a bed survives being slept on.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Unique
	private static boolean fabricatedForgeTest$logged;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedForgeTest$afterTick(CallbackInfo ci) {
		if (!fabricatedForgeTest$logged) {
			fabricatedForgeTest$logged = true;
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST mixin OK");
			if ((Object) this instanceof ForgeTestMarker) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST injected interface OK");
			if (ForgeTestMod.testItem != null && BuiltInRegistries.ITEM.getValue(ForgeTestMod.ITEM_ID) == ForgeTestMod.testItem) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST registry entry kept OK");
			}
			ForgeTestMod.checkTagRemoval();
			ForgeTestMod.checkLoot((MinecraftServer) (Object) this);
			ForgeTestMod.checkDefaultAttributes();
			ForgeTestMod.checkAdvancements((MinecraftServer) (Object) this);
			ForgeTestMod.checkFluids((MinecraftServer) (Object) this);
			ForgeTestMod.checkColors();
			ForgeTestMod.checkCommands((MinecraftServer) (Object) this);
			ForgeTestMod.checkBlockBreaks((MinecraftServer) (Object) this);
			ForgeTestMod.checkSleeping((MinecraftServer) (Object) this);
			ForgeTestMod.checkStorages((MinecraftServer) (Object) this);
		}
	}
}
