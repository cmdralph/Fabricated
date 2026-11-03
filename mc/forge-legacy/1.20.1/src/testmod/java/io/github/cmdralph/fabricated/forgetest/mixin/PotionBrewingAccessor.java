package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.class_1845;

/** {@code PotionBrewing.POTION_MIXES} ({@code field_8956}), for {@code ForgeBrewingMixTest}. */
@Mixin(class_1845.class)
public interface PotionBrewingAccessor {
	@Accessor("field_8956")
	static List<?> fabricatedtest$potionMixes() {
		throw new AssertionError("mixin");
	}
}
