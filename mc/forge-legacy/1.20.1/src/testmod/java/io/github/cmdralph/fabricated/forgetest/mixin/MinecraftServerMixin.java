package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeBiomeTest;
import io.github.cmdralph.fabricated.forgetest.ForgeDimensionTest;
import io.github.cmdralph.fabricated.forgetest.ForgeFluidTest;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMarker;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_5321;
import net.minecraft.class_5455;
import net.minecraft.class_6862;
import net.minecraft.class_7923;
import net.minecraft.server.MinecraftServer;

/**
 * Proves Forge's Mixin applies a translated Fabric mod's mixins (from the overlay's MixinConfigs) in the names Forge 47
 * runs the game with: logs after the first server tick. The method is named in Intermediary's names, as a Fabric mod for
 * 1.20.1 names it (its refmap would), {@code method_3748} being {@code tickServer}; Fabricated remaps it to SRG's
 * {@code m_5705_}. {@code MinecraftServer} keeps its name in every namespace. By then Forge has frozen the registries
 * and checked them, and the test mod's item must still be there, the data packs' tags are bound, and Forge has applied
 * its biome modifiers ({@code method_30611} is {@code registryAccess()}). The test mod then teleports an entity to the End
 * ({@code method_30002} is {@code overworld()}, {@code method_3847} {@code getLevel(ResourceKey)}), checks the fluid
 * types of the fluids it registered, and on a client whether Fabric's colour provider registry finds the game's block
 * colours.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Unique
	private static boolean fabricatedForgeTest$logged;

	@Shadow
	public abstract class_5455.class_6890 method_30611();

	@Shadow
	public abstract class_3218 method_30002();

	@Shadow
	public abstract class_3218 method_3847(class_5321<class_1937> key);

	@Inject(method = "method_3748", at = @At("TAIL"))
	private void fabricatedForgeTest$afterTick(CallbackInfo ci) {
		if (!fabricatedForgeTest$logged) {
			fabricatedForgeTest$logged = true;
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST mixin OK");
			if ((Object) this instanceof ForgeTestMarker) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST injected interface OK");
			class_2378<class_1792> items = class_7923.field_41178;
			if (ForgeTestMod.testItem != null && items.method_10223(new class_2960(ForgeTestMod.MOD_ID, ForgeTestMod.ITEM_PATH)) == ForgeTestMod.testItem) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST registry entry kept OK");
			}
			// The test mod's tag lists stone and dirt and removes dirt in fabric:remove, which Fabricated's in-game mod reads
			// into Forge's remove list.
			class_6862<class_1792> tag = class_6862.method_40092(items.method_30517(), new class_2960(ForgeTestMod.MOD_ID, "fabric_remove"));
			boolean stone = new class_1799(class_1802.field_20391).method_31573(tag);
			boolean dirt = new class_1799(class_1802.field_8831).method_31573(tag);
			if (stone && !dirt) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST fabric:remove OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST fabric:remove: the tag is loaded {}, stone is in it {}, dirt is in it {}",
						items.method_40266(tag).isPresent(), stone, dirt);
			}
			class_5455 registries = method_30611();
			ForgeBiomeTest.check(registries);
			ForgeDimensionTest.check(method_30002(), method_3847(class_1937.field_25181));
			ForgeFluidTest.check(method_30002());
			ForgeTestMod.checkColors();
		}
	}
}
