package io.github.cmdralph.fabricated.forge.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModelPart;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.IForgeBlockStateModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;

/**
 * FRAPI rule 8 on Forge 64 to 66: where Fabric's renderer API calls a block model's emitter, a Fabric model that throws
 * fails alone ({@link FrapiFailures}). On Forge upstream's own hooks send every block through Fabric's renderer:
 * Indigo's block renderer for chunk sections and moving blocks, the renderer API's for blocks drawn as entities (a
 * minecart's contents, an enderman's block) and for the block breaking overlay. Fabricated's mixins put {@link #emit}
 * (or, for the overlay, a guarded copy of upstream's hook) around each of those calls; nothing else changes.
 *
 * <p>A model class that threw is drawn from the parts it gives from then on (not a class wrapping it:
 * {@link FrapiFailures#putDown}), and a transform it left pushed on the emitter is popped, so no other block gets it.
 * A vanilla multipart or weighted model with such a class among its parts draws its other parts as before.
 *
 * <p>Where Forge asks a block model's particle material with its model data, and upstream's hooks asking Fabric's
 * level-aware one don't reach (the screen effect inside a block, a block marker, a block's breaking particles),
 * Fabricated's mixins ask Fabric's for a Fabric model ({@link #levelAwareParticle}, PART-4).
 *
 * <p>A model without an emitter of its own draws through Fabricated's default emitter ({@link #emitDefault}): one that
 * reads Forge's model data (a Forge mod's level-aware model), drawn by Fabric's renderer as a block's model or inside a
 * Fabric model, emits the parts Forge's renderer would draw, those for its model data, asked with the level. Upstream's
 * default emitter collects the parts without it, and upstream's chunk section hook, which pre-flight points at Forge's
 * overload, drops the model data Forge's compiler asked the block's model for.
 *
 * <p>The healthy path reads a {@code ClassValue} and, for a composite model, an int it keeps; it allocates nothing.
 * Models without an emitter of their own (vanilla's and Forge mods') are called as before, without a guard: their
 * quads come from their parts, as on Forge's own renderer. Called on chunk worker threads at once: the state here is
 * immutable, or confined to one thread.
 */
public final class FabricBlockRendering {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** Fabric's renderer API is installed. Its classes are only touched then. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1");
	/** Fabric's block model method, the frame a model's throw shows. */
	private static final String EMIT = "emitQuads";
	/** Where {@link #emit} is called from, for the failure's log line. */
	public static final String RENDERER = "Fabric's block renderer (a chunk section or a moving block)";
	public static final String ENTITY = "an entity (a block it carries or shows)";
	public static final String BREAKING = "the breaking overlay";
	/**
	 * Whether a model class implements {@code emitQuads} itself, and whether that threw. Vanilla's model classes get
	 * upstream's emitters (its mixins), and models without an implementation use the interface's default, which draws
	 * their parts: neither needs a guard.
	 */
	private static final ClassValue<FrapiFailures.ModelClass> MODEL_CLASSES = new ClassValue<>() {
		@Override
		protected FrapiFailures.ModelClass computeValue(Class<?> type) {
			Class<?> owner;
			try {
				owner = type.getMethod(EMIT, QuadEmitter.class, BlockAndTintGetter.class, BlockPos.class, BlockState.class,
						RandomSource.class, Predicate.class).getDeclaringClass();
			} catch (NoSuchMethodException e) {
				return FrapiFailures.ModelClass.NOT_FABRIC; // not a FabricBlockStateModel: the renderer API didn't apply to this class
			} catch (LinkageError e) {
				// getMethod resolves the types every public method names: one from a mod that isn't installed throws. Asked
				// while a chunk section compiles, so it must not escape; the class keeps the unguarded path.
				LOGGER.warn("Block models of class {} are drawn without Fabricated's guard: a type their methods name is missing ({})", type.getName(), e.toString());
				return FrapiFailures.ModelClass.NOT_FABRIC;
			}
			if (owner == FabricBlockStateModel.class || owner.getName().startsWith("net.minecraft.")) return FrapiFailures.ModelClass.NOT_FABRIC;
			LOGGER.debug("Block models of class {} emit their own quads; Fabricated guards them", type.getName());
			return new FrapiFailures.ModelClass(true, owner.getName());
		}
	};
	/**
	 * Whether a model class gives a particle material of its own for Forge's model data ({@code particleMaterial(ModelData)}),
	 * as a Forge mod's model, or a vanilla model Forge patched (its weighted model on Forge 65), may: Forge asks that
	 * where vanilla asks the model's material.
	 */
	private static final ClassValue<Boolean> FORGE_PARTICLE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("particleMaterial", ModelData.class).getDeclaringClass() != IForgeBlockStateModel.class;
			} catch (NoSuchMethodException e) {
				return false;
			} catch (LinkageError e) {
				return true; // a type its methods name is missing (MODEL_CLASSES warns of it): Forge's call, as before
			}
		}
	};
	/** Whether a model class reads Forge's model data: it gives its own {@code getModelData}, or its own parts for model data. */
	private static final ClassValue<Boolean> FORGE_MODEL_DATA = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("getModelData", BlockAndTintGetter.class, BlockPos.class, BlockState.class, ModelData.class)
						.getDeclaringClass() != IForgeBlockStateModel.class
						|| type.getMethod("collectParts", RandomSource.class, List.class, ModelData.class).getDeclaringClass() != IForgeBlockStateModel.class;
			} catch (NoSuchMethodException | LinkageError e) {
				return false; // a type its methods name is missing (MODEL_CLASSES warns of it): upstream's default
			}
		}
	};
	/** Leaves are drawn opaque when the player turned their transparency off, as upstream's default emitter does. */
	private static final QuadTransform OPAQUE = quad -> {
		quad.chunkLayer(ChunkSectionLayer.SOLID);
		return true;
	};
	/** Each thread's list for a broken model's parts (the failure path only). */
	private static final ThreadLocal<List<BlockStateModelPart>> PARTS = ThreadLocal.withInitial(ArrayList::new);
	/**
	 * Each thread's parts for {@link #emitDefault}, reused for every model it draws: a model's are those added after the
	 * ones of the models being drawn around it (a part may draw a model), removed when it is done.
	 */
	private static final ThreadLocal<List<BlockStateModelPart>> DEFAULT_PARTS = ThreadLocal.withInitial(ArrayList::new);

	private FabricBlockRendering() {
	}

	/**
	 * A vanilla model made of other models (multipart, weighted variants), which Fabricated's mixins let say whether one
	 * of them is a Fabric model, or broken, and draw them one by one through {@link #emit}. The answer is kept in the
	 * model, which a resource reload replaces, for one {@link FrapiFailures#generation()}.
	 */
	public interface Composite {
		/** {@link FrapiFailures#NONE}, {@link FrapiFailures#FABRIC} or {@link FrapiFailures#BROKEN}, from {@link #state(List)}. */
		int fabricated$state();

		/** The models it is made of (any its state selects). Asked on the failure path only. */
		List<BlockStateModel> fabricated$parts();

		/** Emits its quads as upstream's emitter does, each of its models through {@link #emit}. */
		void fabricated$emitEach(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				Predicate<@Nullable Direction> cullTest, String where);
	}

	/**
	 * A vanilla multipart model's shared state, which selects the models a block state draws: package-private in vanilla,
	 * so Fabricated's mixin lets it answer through this.
	 */
	public interface ModelSelector {
		List<BlockStateModel> fabricated$selectModels(BlockState state);
	}

	/** Fabric's renderer's emitter (Indigo's), which Fabricated's mixin lets say how many transforms are pushed on it. */
	public interface TransformDepth {
		int fabricated$transformDepth();
	}

	/** Whether this model emits its own quads, or would if drawing it hadn't thrown ({@link FrapiFailures}). */
	public static int state(BlockStateModel model) {
		if (!ENABLED) return FrapiFailures.NONE;
		FrapiFailures.ModelClass type = MODEL_CLASSES.get(model.getClass());
		if (type.fabric()) return type.broken() ? FrapiFailures.BROKEN : FrapiFailures.FABRIC;
		return model instanceof Composite composite ? composite.fabricated$state() : FrapiFailures.NONE;
	}

	/** A composite model's state, from its parts' (a composite keeps it: see {@link Composite}). */
	public static int state(List<BlockStateModel> parts) {
		int state = FrapiFailures.NONE;
		for (BlockStateModel part : parts) {
			state = FrapiFailures.combine(state, state(part));
			if (state == FrapiFailures.BROKEN) break;
		}
		return state;
	}

	/**
	 * Whether a block's particle material is Fabric's level-aware one ({@code FabricBlockStateModel.particleMaterial}
	 * with the level) where Forge asks the model with its model data instead (PART-4): unless the model's class gives its
	 * own material for model data, and the model neither is a Fabric model nor holds one. A model doing neither gives the
	 * same material both ways. Called only where Fabric's renderer API is installed (Fabricated's mixins asking it are).
	 */
	public static boolean levelAwareParticle(BlockStateModel model) {
		return !FORGE_PARTICLE.get(model.getClass()) || state(model) != FrapiFailures.NONE;
	}

	/**
	 * A model's quads into Fabric's emitter, where upstream's code calls {@code model.emitQuads} with the same arguments.
	 * A Fabric model (or a composite holding one) that throws is put down ({@link FrapiFailures#putDown}), the transforms
	 * it left pushed are popped, and the block is drawn from its parts this time: the quads it emitted before the throw
	 * stay. A model class marked broken is drawn from its parts at once.
	 */
	public static void emit(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest, String where) {
		int modelState = state(model);
		if (modelState == FrapiFailures.NONE) {
			((FabricBlockStateModel) model).emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}
		if (modelState == FrapiFailures.BROKEN) {
			emitInstead(model, emitter, level, pos, state, random, cullTest, where);
			return;
		}
		int depth = depth(emitter);
		try {
			((FabricBlockStateModel) model).emitQuads(emitter, level, pos, state, random, cullTest);
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			restore(emitter, depth);
			markBroken(model, t, where + " at " + pos, state);
			emitInstead(model, emitter, level, pos, state, random, cullTest, where);
		}
	}

	/**
	 * The renderer API's default {@code emitQuads}, of a model without its own ({@code BlockStateModelDefaultMixin}), as
	 * upstream's: its parts, its leaves opaque where the player turned their transparency off. A model reading Forge's
	 * model data gives the parts for its model data, which it is asked with the level and the data Forge keeps for the
	 * block, as Forge's chunk section compiler asks it.
	 */
	public static void emitDefault(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		boolean opaque = ModelBlockRenderer.forceOpaque(Minecraft.getInstance().options.cutoutLeaves().get(), state);
		if (opaque) emitter.pushTransform(OPAQUE);
		List<BlockStateModelPart> parts = DEFAULT_PARTS.get();
		int first = parts.size();
		try {
			if (FORGE_MODEL_DATA.get(model.getClass())) {
				ModelDataManager manager = level.getModelDataManager();
				model.collectParts(random, parts, model.getModelData(level, pos, state, manager == null ? ModelData.EMPTY : manager.getAtOrEmpty(pos)));
			} else {
				model.collectParts(random, parts);
			}
			for (int i = first; i < parts.size(); i++) ((FabricBlockStateModelPart) parts.get(i)).emitQuads(emitter, cullTest);
		} finally {
			// Not subList(first, size).clear(): that allocates, for every block drawn so.
			while (parts.size() > first) parts.removeLast();
			if (opaque) emitter.popTransform();
		}
	}

	/**
	 * What is drawn for a model whose emitter threw: a composite's models one by one (its healthy Fabric models keep their
	 * own quads), any other model's parts.
	 */
	private static void emitInstead(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest, String where) {
		if (model instanceof Composite composite) {
			composite.fabricated$emitEach(emitter, level, pos, state, random, cullTest, where);
		} else {
			emitParts(model, emitter, random, cullTest, where + " at " + pos, state);
		}
	}

	/**
	 * A model's parts into the emitter, as the renderer API's default emitter would. If its parts throw too, nothing more
	 * of the block is drawn (logged once for the class).
	 */
	public static void emitParts(BlockStateModel model, QuadEmitter emitter, RandomSource random, Predicate<@Nullable Direction> cullTest, String where, BlockState state) {
		List<BlockStateModelPart> parts = PARTS.get();
		int depth = depth(emitter);
		try {
			model.collectParts(random, parts);
			for (int i = 0; i < parts.size(); i++) ((FabricBlockStateModelPart) parts.get(i)).emitQuads(emitter, cullTest);
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			restore(emitter, depth);
			FrapiFailures.partsThrew(model.getClass(), t, where, state);
		} finally {
			parts.clear();
		}
	}

	/**
	 * Collects a model's parts for a vanilla consumer (the breaking overlay's), as the platform would. False if they
	 * threw (logged once for the class): the consumer then gets none.
	 */
	public static boolean collectParts(BlockStateModel model, RandomSource random, List<BlockStateModelPart> parts, String where, BlockState state) {
		int size = parts.size();
		try {
			model.collectParts(random, parts);
			return true;
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			parts.subList(size, parts.size()).clear();
			FrapiFailures.partsThrew(model.getClass(), t, where, state);
			return false;
		}
	}

	/**
	 * Marks what threw broken: the model's class if it is a Fabric model, or its Fabric parts in a composite, as the
	 * stack trace puts the throw down to them ({@link FrapiFailures#putDown}: not a wrapper, when a model it wraps threw).
	 */
	public static void markBroken(BlockStateModel model, Throwable error, String where, BlockState state) {
		List<BlockStateModel> fabricModels = new ArrayList<>();
		fabricModels(model, fabricModels, 0);
		FrapiFailures.putDown(model, fabricModels, MODEL_CLASSES, EMIT, error, where, state);
	}

	/** The Fabric models a model is, or is made of (through nested composites, to a depth no vanilla model reaches). */
	private static void fabricModels(BlockStateModel model, List<BlockStateModel> out, int depth) {
		if (MODEL_CLASSES.get(model.getClass()).fabric()) {
			out.add(model);
		} else if (model instanceof Composite composite && depth < 8) {
			List<BlockStateModel> parts;
			try {
				parts = composite.fabricated$parts();
			} catch (RuntimeException | LinkageError e) {
				return; // a part selector that throws: the parts found elsewhere are marked
			}
			for (BlockStateModel part : parts) fabricModels(part, out, depth + 1);
		}
	}

	/** How many transforms are pushed on the emitter; -1 if it isn't Indigo's (then none can be popped). */
	public static int depth(QuadEmitter emitter) {
		return emitter instanceof TransformDepth transforms ? transforms.fabricated$transformDepth() : -1;
	}

	/** Pops the transforms a model left pushed on the emitter, down to {@code depth}. */
	public static void restore(QuadEmitter emitter, int depth) {
		if (depth < 0) return;
		try {
			while (depth(emitter) > depth) emitter.popTransform();
			emitter.clear();
		} catch (RuntimeException e) {
			LOGGER.warn("Could not reset Fabric's emitter after a block model threw: later blocks it draws may be transformed wrongly", e);
		}
	}
}
