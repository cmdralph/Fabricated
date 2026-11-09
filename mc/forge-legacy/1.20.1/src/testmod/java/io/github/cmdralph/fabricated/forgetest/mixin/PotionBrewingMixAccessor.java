package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


/**
 * A brewing mix's {@code from} and {@code to}, as Porting Lib's accessor reads them: vanilla's potions, which Forge 47
 * keeps as holders, so these invoke the in-game mod's bridges (CORPUS-37, {@code ForgeBrewingMixTest}). The fields are
 * vanilla's, which Forge's game this compiles against no longer has: the accessors name them in Intermediary's names, as
 * the mod's jar does once Loom has remapped it, and stay out of the refmap.
 */
@Mixin(PotionBrewing.Mix.class)
public interface PotionBrewingMixAccessor {
	@Accessor(value = "field_8962", remap = false)
	Object fabricatedtest$from();

	@Accessor(value = "field_8961", remap = false)
	Object fabricatedtest$to();
}
