package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.neoforge.item.FabricEnchantments;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.enchantment.Enchantment;

/** Opens up enchantment builders for {@link FabricEnchantments}, and notes their use, as upstream's mixin. */
@Mixin(Enchantment.Builder.class)
abstract class EnchantmentBuilderMixin implements FabricEnchantments.Builder {
	@Shadow
	@Final
	private Enchantment.EnchantmentDefinition definition;
	@Shadow
	private HolderSet<Enchantment> exclusiveSet;
	@Shadow
	@Final
	private DataComponentMap.Builder effectMapBuilder;
	@Unique
	private boolean fabricated$modified;

	@Shadow
	private <E> List<E> getEffectsList(DataComponentType<List<E>> type) {
		throw new AssertionError();
	}

	/** Any builder method that returns the builder counts as a change. */
	@Inject(method = "*", at = @At("RETURN"))
	private void fabricated$markModified(CallbackInfoReturnable<?> cir) {
		if (cir.getReturnValue() == this) fabricated$modified = true;
	}

	@Override
	public Enchantment.EnchantmentDefinition fabricated$definition() {
		return definition;
	}

	@Override
	public HolderSet<Enchantment> fabricated$exclusiveSet() {
		return exclusiveSet;
	}

	@Override
	public DataComponentMap.Builder fabricated$effectMap() {
		return effectMapBuilder;
	}

	@Override
	public <E> List<E> fabricated$effectsList(DataComponentType<List<E>> type) {
		return getEffectsList(type);
	}

	@Override
	public void fabricated$resetModified() {
		fabricated$modified = false;
	}

	@Override
	public boolean fabricated$modified() {
		return fabricated$modified;
	}
}
