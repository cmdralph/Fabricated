package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forgetest.ForgeLootTest;
import net.minecraft.class_2960;
import net.minecraft.class_52;

/**
 * Names a loot table as its builder builds it, where the test mod's MODIFY listener asked for that name, as Porting Lib
 * Loot's {@code LootTableMixin$BuilderMixin} does for every table ({@code method_338} is {@code build()}; see
 * {@link ForgeLootTest}, CORPUS-90).
 */
@Mixin(class_52.class_53.class)
public abstract class LootTableBuilderNamingMixin implements ForgeLootTest.NamedAsBuilt {
	@Unique
	private class_2960 fabricatedForgeTest$name;

	@Override
	public void fabricatedForgeTest$nameAsBuilt(class_2960 name) {
		fabricatedForgeTest$name = name;
	}

	@Inject(method = "method_338", at = @At("RETURN"))
	private void fabricatedForgeTest$named(CallbackInfoReturnable<class_52> cir) {
		if (fabricatedForgeTest$name != null) cir.getReturnValue().setLootTableId(fabricatedForgeTest$name);
	}
}
