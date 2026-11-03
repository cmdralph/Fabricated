package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.class_1845;

/**
 * A brewing mix's {@code from} and {@code to}, as Porting Lib's accessor reads them: vanilla's potions, which Forge 47
 * keeps as holders, so these invoke the in-game mod's bridges (CORPUS-37, {@code ForgeBrewingMixTest}).
 */
@Mixin(class_1845.class_1846.class)
public interface PotionBrewingMixAccessor {
	@Accessor("field_8962")
	Object fabricatedtest$from();

	@Accessor("field_8961")
	Object fabricatedtest$to();
}
