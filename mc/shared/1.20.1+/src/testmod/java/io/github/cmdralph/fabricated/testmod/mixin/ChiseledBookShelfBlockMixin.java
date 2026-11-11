package io.github.cmdralph.fabricated.testmod.mixin;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestCompat;
import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Hooks the lambda that adds each slot's property to the chiseled bookshelf's states, naming it as vanilla does and taking
 * the {@code Property} vanilla's takes, which NeoForge's recompiled game (21.11 and later) takes as the
 * {@code BooleanProperty} it is: as JEI's and owo-lib's char typed hooks take the {@code GuiEventListener} the platform's
 * lambda takes as a {@code Screen}, through pre-flight's bridge there (LAMBDA-11). Logs once as the blocks are built, at
 * bootstrap (not through {@code TestMod}'s logger: the mod isn't constructed yet).
 */
@Mixin(ChiseledBookShelfBlock.class)
public abstract class ChiseledBookShelfBlockMixin {
	@Unique
	private static boolean fabricatedTest$logged;

	@Inject(method = TestCompat.PROPERTY_LAMBDA, at = @At("HEAD"))
	private static void fabricatedTest$supertype(StateDefinition.Builder<?, ?> builder, Property<?> property, CallbackInfo ci) {
		if (fabricatedTest$logged || !(property instanceof BooleanProperty)) return;
		fabricatedTest$logged = true;
		LoggerFactory.getLogger(TestMod.MOD_ID).info("FABRICATED-TEST supertype handler OK");
	}
}
