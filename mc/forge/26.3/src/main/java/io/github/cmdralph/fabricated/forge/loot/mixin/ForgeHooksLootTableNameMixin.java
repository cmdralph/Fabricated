package io.github.cmdralph.fabricated.forge.loot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeHooks;

/**
 * Forge 66 names each loot table it decodes (its global loot modifiers look tables up by name) after Fabric's events
 * have had it: its patch of {@code ResourceManagerRegistryLoadTask} hands {@code ForgeHooks.onJsonDataParsed} the table
 * {@code ResourceManagerRegistryLoadTaskMixin} returns, which Fabric's {@code MODIFY} rebuilt. A Fabric mod may have
 * named it as it was built (Porting Lib Loot names each table from its {@code MODIFY} listener), and Forge refuses to
 * name a table twice, even by the same name, so a table that already has its name isn't named again (CORPUS-90), as
 * Fabricated's own naming on Forge 61 to 65 ({@code LootBridge.onTableLoaded}).
 */
@Mixin(ForgeHooks.class)
abstract class ForgeHooksLootTableNameMixin {
	@WrapOperation(method = "onJsonDataParsed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/storage/loot/LootTable;setLootTableId(Lnet/minecraft/resources/Identifier;)V"))
	private static void fabricated$nameOnce(LootTable table, Identifier name, Operation<Void> setLootTableId) {
		if (!name.equals(table.getLootTableId())) setLootTableId.call(table, name);
	}
}
