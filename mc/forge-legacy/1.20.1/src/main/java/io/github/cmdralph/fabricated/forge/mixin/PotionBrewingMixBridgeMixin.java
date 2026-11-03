package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * A brewing mix's input and output as vanilla keeps them, for Fabric mods' accessors of them (CORPUS-37). Forge 47
 * keeps holders of the potions or items ({@code Holder.Reference}) in the mix's {@code from} and {@code to}, where
 * vanilla keeps the potions or items themselves, which Porting Lib's and EMI's accessors read. Pre-flight points an
 * accessor of either field at its bridge here ({@code MixinRepairs.retypedFieldBridge} in core), named after the
 * field's runtime name, so the fields are named so here too and not remapped. The fields are final on both, and no
 * setter is bridged.
 */
@Mixin(PotionBrewing.Mix.class)
abstract class PotionBrewingMixBridgeMixin {
	@Shadow(remap = false)
	@Final
	public Holder.Reference<?> f_43532_;

	@Shadow(remap = false)
	@Final
	public Holder.Reference<?> f_43534_;

	/** Vanilla's {@code from}: the potion or item brewed. */
	public Object fabricated$get$f_43532_() {
		return f_43532_.value();
	}

	/** Vanilla's {@code to}: the potion or item the brewing gives. */
	public Object fabricated$get$f_43534_() {
		return f_43534_.value();
	}
}
