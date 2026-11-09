package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


/** {@code PotionBrewing.POTION_MIXES}, for {@code ForgeBrewingMixTest}. */
@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {
	@Accessor("POTION_MIXES")
	static List<?> fabricatedtest$potionMixes() {
		throw new AssertionError("mixin");
	}
}
