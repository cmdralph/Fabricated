package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forgetest.LootNamedAsBuilt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Names a loot table as its builder builds it, where the test mod's MODIFY listener asked for that name, as Porting Lib
 * Loot's {@code LootTableMixin$BuilderMixin} does for every table (see {@link LootNamedAsBuilt}, CORPUS-90).
 */
@Mixin(LootTable.Builder.class)
public abstract class LootTableBuilderNamingMixin implements LootNamedAsBuilt {
	@Unique
	private Identifier fabricatedForgeTest$name;

	@Override
	public void fabricatedForgeTest$nameAsBuilt(Identifier name) {
		fabricatedForgeTest$name = name;
	}

	@Inject(method = "build", at = @At("RETURN"))
	private void fabricatedForgeTest$named(CallbackInfoReturnable<LootTable> cir) {
		if (fabricatedForgeTest$name != null) cir.getReturnValue().setLootTableId(fabricatedForgeTest$name);
	}
}
