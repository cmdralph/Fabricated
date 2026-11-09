package io.github.cmdralph.fabricated.testmod.frapi;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;

/**
 * What the renderer test mod's shared checks (mc/shared) ask of the platform they run on, Forge here (26.x). Every
 * platform's renderer test mod has this class with these methods, so the checks compile against each platform's.
 */
final class FrapiPlatform {
	/** The class through which Fabricated sends a block to Fabric's renderer on this platform. */
	static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering";

	private FrapiPlatform() {
	}

	/**
	 * Whether the platform loaded a mod of its own with this id: Forge's mod list has the Fabric mods Fabricated's
	 * language loader loads too, Fabric API's modules among them, which aren't. Forge 64 and later made its mod list's
	 * methods static, where Forge 61 reaches them through {@code ModList.get()}.
	 */
	static boolean isModLoaded(String modId) {
		Optional<?> container;
		try {
			Method byId = ModList.class.getMethod("getModContainerById", String.class);
			container = (Optional<?>) byId.invoke(Modifier.isStatic(byId.getModifiers()) ? null : ModList.class.getMethod("get").invoke(null), modId);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return container.map(ModContainer.class::cast)
				.filter(c -> c.getModInfo().getOwningFile().requiredLanguageLoaders().stream().noneMatch(l -> l.languageName().equals("fabricated")))
				.isPresent();
	}

	/** Forge has no composite blockstate model type of its own. */
	static boolean isPlatformComposite(BlockStateModel model) {
		return false;
	}

	/**
	 * The model, as one of the platform's with no {@code emitQuads} of its own, whose parts depend on the level: on Forge
	 * through its model data, which Forge asks of a model with the level.
	 */
	static BlockStateModel levelAware(BlockStateModel model) {
		return new LevelAwareModel(model);
	}

	/** A Fabric model wrapping another, told when the platform's renderer asks its parts its own way: with Forge's model data. */
	abstract static class PartsWrapper extends WrapperBlockStateModel {
		PartsWrapper(BlockStateModel wrapped) {
			super(wrapped);
		}

		abstract void partsAsked(String caller);

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
			partsAsked("model data");
			wrapped.collectParts(random, output, data);
		}
	}

	private record LevelAwareModel(BlockStateModel wrapped) implements BlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();

		@Override
		public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
			if (FrapiClientTestMod.isSection(level) && !TERRAIN.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi level-aware default OK ({})", state);
			return wrapped.getModelData(level, pos, state, data);
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
			wrapped.collectParts(random, output);
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
			wrapped.collectParts(random, output, data);
		}

		@Override
		public Material.Baked particleMaterial() {
			return wrapped.particleMaterial();
		}

		@Override
		public Material.Baked particleMaterial(@NotNull ModelData data) {
			return wrapped.particleMaterial(data);
		}

		@Override
		public int materialFlags() {
			return wrapped.materialFlags();
		}
	}
}
