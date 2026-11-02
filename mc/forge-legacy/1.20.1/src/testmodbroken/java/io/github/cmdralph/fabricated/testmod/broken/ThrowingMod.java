package io.github.cmdralph.fabricated.testmod.broken;

import net.fabricmc.api.ModInitializer;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2378;
import net.minecraft.class_7923;

/**
 * mc/shared's test mod that fails on purpose, in Intermediary's names as a Fabric mod for 1.20.1 is compiled (against the
 * stand-ins in src/testmodstubs): Forge 47 runs the game with SRG's member names, which Fabricated remaps Intermediary's
 * to, so the shared copy's Mojang names wouldn't link here. Before failing it leaves an intrusive holder nothing
 * registers, as a mod that built a block and then threw would: the registries' freeze after the entrypoints must drop it
 * rather than stop the game (CORPUS-42, HOLD-2).
 */
public final class ThrowingMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// BuiltInRegistries.BLOCK.createIntrusiveHolder(Blocks.STONE), called on Registry, which declares it.
		class_2378<class_2248> blocks = class_7923.field_41175;
		blocks.method_40269(class_2246.field_10340);
		throw new IllegalStateException("intentional failure for Fabricated CI");
	}
}
