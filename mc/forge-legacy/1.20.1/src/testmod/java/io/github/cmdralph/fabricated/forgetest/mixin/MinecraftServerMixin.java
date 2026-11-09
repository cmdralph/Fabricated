package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeBlockBreakTest;
import io.github.cmdralph.fabricated.forgetest.ForgeBrewingMixTest;
import io.github.cmdralph.fabricated.forgetest.ForgeBrewingTest;
import io.github.cmdralph.fabricated.forgetest.ForgeCommandTest;
import io.github.cmdralph.fabricated.forgetest.ForgeConfigPortTest;
import io.github.cmdralph.fabricated.forgetest.ForgeDimensionTest;
import io.github.cmdralph.fabricated.forgetest.ForgeDropCaptureTest;
import io.github.cmdralph.fabricated.forgetest.ForgeFluidTest;
import io.github.cmdralph.fabricated.forgetest.ForgeFuelTest;
import io.github.cmdralph.fabricated.forgetest.ForgeIngredientTest;
import io.github.cmdralph.fabricated.forgetest.ForgeInteractionTest;
import io.github.cmdralph.fabricated.forgetest.ForgeLanguageTest;
import io.github.cmdralph.fabricated.forgetest.ForgeMobBucketTest;
import io.github.cmdralph.fabricated.forgetest.ForgeRecipeConditionTest;
import io.github.cmdralph.fabricated.forgetest.ForgeRegistryAliasTest;
import io.github.cmdralph.fabricated.forgetest.ForgeSleepTest;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMarker;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import io.github.cmdralph.fabricated.forgetest.ForgeTransferTest;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/**
 * Runs the checks of Forge 47's half of the test mod at the end of the server's first tick, by when Forge has frozen the
 * registries and checked them, the data packs' tags are bound, and Forge has applied its biome modifiers: the interface
 * the access widener injects, an entity teleported to the End, the fluid types of the fluids it registered, a pig's drops
 * in Forge's capture, Fabric's command in the server's dispatcher, the config made through Forge Config API Port's
 * registry, Fabric's block break, use and attack, and sleep events, storages reaching Forge's capabilities, accessors of
 * fields Forge 47 replaced, Forge's recipe conditions and custom ingredients, vanilla's language reader, and the fuel and
 * brewing recipe Fabric's content registries made; then Forge's client hooks on a client. The interaction check finishes
 * at a later tick.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Unique
	private static boolean fabricatedForgeTest$checked;

	@Shadow
	public abstract ServerLevel overworld();

	@Shadow
	public abstract ServerLevel getLevel(ResourceKey<Level> key);

	@Shadow
	public abstract Commands getCommands();

	@Shadow
	public abstract RecipeManager getRecipeManager();

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedForgeTest$afterTick(CallbackInfo ci) {
		if (fabricatedForgeTest$checked) {
			ForgeTestMod.run("interaction check", () -> ForgeInteractionTest.tick(overworld()));
			return;
		}
		fabricatedForgeTest$checked = true;
		if ((Object) this instanceof ForgeTestMarker) ForgeTestMod.LOGGER.info("FABRICATED-TEST injected interface OK");
		else ForgeTestMod.LOGGER.error("FABRICATED-TEST injected interface FAILED: the server has no ForgeTestMarker");
		ServerLevel level = overworld();
		ForgeTestMod.run("dimensions check", () -> ForgeDimensionTest.check(level, getLevel(Level.END)));
		ForgeTestMod.run("fluid types check", () -> ForgeFluidTest.check(level));
		ForgeTestMod.run("drop capture check", () -> ForgeDropCaptureTest.check(level));
		ForgeTestMod.run("command check", () -> ForgeCommandTest.check(getCommands()));
		ForgeTestMod.run("registry alias check", ForgeRegistryAliasTest::check);
		ForgeTestMod.run("config port check", ForgeConfigPortTest::check);
		ForgeTestMod.run("block break check", () -> ForgeBlockBreakTest.check(level));
		ForgeTestMod.run("interaction check", () -> ForgeInteractionTest.check(level));
		ForgeTestMod.run("sleep check", () -> ForgeSleepTest.check(level));
		ForgeTestMod.run("storages check", () -> ForgeTransferTest.check(level));
		ForgeTestMod.run("brewing mix accessors check", ForgeBrewingMixTest::check);
		ForgeTestMod.run("mob bucket accessor check", ForgeMobBucketTest::check);
		ForgeTestMod.run("recipe condition check", () -> ForgeRecipeConditionTest.check(getRecipeManager()));
		ForgeTestMod.run("custom ingredients check", ForgeIngredientTest::check);
		ForgeTestMod.run("language files check", ForgeLanguageTest::check);
		ForgeTestMod.run("fuel check", ForgeFuelTest::check);
		ForgeTestMod.run("brewing recipe check", ForgeBrewingTest::check);
		ForgeTestMod.checkClient();
	}
}
