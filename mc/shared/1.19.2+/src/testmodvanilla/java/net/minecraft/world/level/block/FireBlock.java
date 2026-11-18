package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Stand-in, never packaged: vanilla's fire block as the test mod's invoker names it, with vanilla's private
 * {@code checkBurnOut}, which NeoForge and Forge 52 to 66 give the side the fire comes from and Forge 43 and 47 replace
 * with {@code tryCatchFire} (LEAVE-6), so Forge 43's and 47's annotation processor maps it for the mixin's refmap.
 */
public class FireBlock {
	private void checkBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age) {
	}
}
