package io.github.cmdralph.fabricated.testmod.broken;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;

/**
 * Fails on purpose: CI checks that one failing Fabric mod does not stop the server (policy "disable"). Before failing it
 * leaves an intrusive holder nothing registers, as a mod that built a block and then threw would: the registries' freeze
 * after the entrypoints must drop it rather than stop the game (CORPUS-42, HOLD-2).
 */
public final class ThrowingMod implements ModInitializer {
	@Override
	public void onInitialize() {
		BuiltInRegistries.BLOCK.createIntrusiveHolder(Blocks.STONE);
		throw new IllegalStateException("intentional failure for Fabricated CI");
	}
}
