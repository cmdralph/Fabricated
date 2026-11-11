package io.github.cmdralph.fabricated.testmod.mixin;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.StructureEntitiesTest;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * YUNG's API's and Friends and Foes' entity processing hooks, as {@link StructureEntitiesTest} checks them (STRUCT-1):
 * the settings kept in a {@code ThreadLocal} at vanilla's call of its private {@code placeEntities}, its start, taking
 * its parameters and cancelling, and after the call. Only the test's own placement is touched.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureEntitiesMixin {
	@Unique
	private static final ThreadLocal<StructurePlaceSettings> fabricatedTest$placing = new ThreadLocal<>();

	@Inject(method = "placeInWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V"))
	private void fabricatedTest$beforeEntities(ServerLevelAccessor level, BlockPos pos, BlockPos pivot, StructurePlaceSettings settings, RandomSource random,
			int flags, CallbackInfoReturnable<Boolean> cir) {
		if (settings != StructureEntitiesTest.placing()) return;
		fabricatedTest$placing.set(settings);
		StructureEntitiesTest.step("before the call");
	}

	// Named with its descriptor, as a mod compiled against vanilla's class has it in its refmap: the platform's class the
	// test mod compiles against has no such method.
	@Inject(method = "placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Mirror;"
			+ "Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V",
			at = @At("HEAD"), cancellable = true)
	private void fabricatedTest$entities(ServerLevelAccessor level, BlockPos pos, Mirror mirror, Rotation rotation, BlockPos pivot, BoundingBox box,
			boolean finalize, ProblemReporter problems, CallbackInfo ci) {
		StructurePlaceSettings settings = fabricatedTest$placing.get();
		if (settings == null) return;
		boolean vanillas = mirror == settings.getMirror() && rotation == settings.getRotation() && pivot == settings.getRotationPivot()
				&& box == settings.getBoundingBox() && finalize == settings.shouldFinalizeEntities() && problems != null;
		StructureEntitiesTest.step(vanillas ? "start, with vanilla's parameters" : "start, with " + Arrays.asList(mirror, rotation, pivot, box, finalize, problems));
		ci.cancel();
	}

	@Inject(method = "placeInWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeEntities(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;ZLnet/minecraft/util/ProblemReporter;)V",
			shift = At.Shift.AFTER))
	private void fabricatedTest$afterEntities(ServerLevelAccessor level, BlockPos pos, BlockPos pivot, StructurePlaceSettings settings, RandomSource random,
			int flags, CallbackInfoReturnable<Boolean> cir) {
		if (fabricatedTest$placing.get() == null) return;
		fabricatedTest$placing.remove();
		StructureEntitiesTest.step("after the call");
	}
}
