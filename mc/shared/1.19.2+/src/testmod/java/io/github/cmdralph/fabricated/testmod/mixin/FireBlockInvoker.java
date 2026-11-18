package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;

/**
 * An invoker of vanilla's private {@code FireBlock.checkBurnOut}, as Supplementaries' lit lumisene sets the blocks around
 * it alight through one: NeoForge and Forge 52 to 66 give the method the side the fire comes from, and Forge 43 and 47
 * replace it with {@code tryCatchFire}, taking it too, so pre-flight points the invoker at the bridge Fabricated declares
 * for it (LEAVE-6, {@code VanillaFireInvokerTest}).
 */
@Mixin(FireBlock.class)
public interface FireBlockInvoker {
	@Invoker("checkBurnOut")
	void invokeCheckBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age);
}
