package io.github.cmdralph.fabricated.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import io.github.cmdralph.fabricated.core.mixin.InterfaceInjects;
import io.github.cmdralph.fabricated.loader.FabricClassTransforms;
import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * Fabricated's Mixin extensions on Forge 61 to 66 and Forge 47: one declares default bridges after a class's mixins apply
 * (CORPUS-63, as on NeoForge 21.1), one records again, expanded, the frames of the methods whose locals Mixin would count
 * wrong in a class a Fabric mod's mixin targets, before they apply ({@link RecordedFrameRepair}, CORPUS-79, where Mixin
 * needs it: Forge 61 to 66), and one calls the interface mixins' {@code @Inject} handlers Mixin merged as methods, after
 * they apply ({@link InterfaceInjects}, INTERFACE-1, where translation made them so: Forge 47). Forge runs on
 * ModLauncher, where Mixin is a launch plugin and no transformer from the mods folder runs after it; a Mixin extension
 * runs as Mixin applies mixins to a class, before and after they apply, and nowhere else: the loader's transformer
 * declares the bridges before Mixin for the classes no mixin targets. Shared by Forge 47 and Forge 61 to 66.
 */
final class MixinExtensions {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private MixinExtensions() {
	}

	/**
	 * Registers the extensions with Mixin's transformer, those with work to do: the bridges where a Fabric mod brings
	 * any, the frame repair where any Fabric mod loads and Mixin records frames unexpanded
	 * ({@link RecordedFrameRepair#needed}: Forge 61 to 66), the interface mixins' handlers where any Fabric mod loads
	 * (translation makes them where Mixin applies no injector in an interface mixin: Forge 47). Called from Fabricated's mixin plugin as Mixin selects the configs
	 * (the plugin's {@code onLoad}), before Mixin activates its extensions for the configs it selected, and so before it
	 * applies a mixin of theirs.
	 */
	static void install() {
		FabricatedState state = FabricatedState.get();
		if (state == null) return;
		boolean bridges = !state.defaultBridges().isEmpty();
		boolean frames = !state.mods().isEmpty() && RecordedFrameRepair.needed();
		boolean handlers = !state.mods().isEmpty();
		if (!bridges && !frames && !handlers) return;
		Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
		if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
			List<IExtension> added = new ArrayList<>(3);
			if (bridges) added.add(new DefaultBridges(state));
			if (frames) added.add(new RecordedFrameRepair(state));
			if (handlers) added.add(new InterfaceHandlers());
			added.forEach(extensions::add);
			return;
		}
		if (bridges) {
			// The bridges declared before Mixin stay as Mixin applies mixins: only a mixin declaring one of those methods
			// itself, in a config requiring @Overwrite to replace a method, then fails.
			LOGGER.error("Fabricated could not register its Mixin extension with Mixin's transformer ({}): the default method bridges it declares"
					+ " before Mixin stay as mixins apply, so a mixin declaring one of those methods may fail. Classes with bridges: {}", transformer,
					String.join(", ", new TreeSet<>(state.defaultBridges().keySet())));
		}
		if (handlers) {
			LOGGER.error("Fabricated could not register its Mixin extension with Mixin's transformer ({}): the @Inject handlers of Fabric mods'"
					+ " interface mixins, which Forge 47's Mixin doesn't apply, are never called (INTERFACE-1)", transformer);
		}
		if (frames) {
			LOGGER.error("Fabricated could not register its Mixin extension with Mixin's transformer ({}): the frames Forge's Mixin records stay"
					+ " as they are, so a Fabric mod's injector taking a local where Forge's Mixin miscounts them may find the wrong ones (CORPUS-79)", transformer);
		}
	}

	/**
	 * Calls the {@code @Inject} handlers of interface mixins that Mixin merged into a class as methods, once its mixins have
	 * applied, where their injectors would have Mixin call them ({@link InterfaceInjects#apply}). Translation makes such
	 * handlers only where Mixin applies no injector in an interface mixin (Forge 47); elsewhere a class has none, and this
	 * only looks.
	 */
	private static final class InterfaceHandlers implements IExtension {
		@Override
		public boolean checkActive(MixinEnvironment environment) {
			return true;
		}

		@Override
		public void preApply(ITargetClassContext context) {
		}

		@Override
		public void postApply(ITargetClassContext context) {
			ClassNode node = context.getClassNode();
			int applied = InterfaceInjects.apply(node);
			if (applied > 0) LOGGER.debug("Fabricated applied {} @Inject handler(s) of interface mixins in {}, which Forge's Mixin doesn't apply (INTERFACE-1)",
					applied, node.name);
		}

		@Override
		public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
		}
	}

	/**
	 * Takes a class's default bridges out as Mixin starts applying its mixins, and declares them once they have applied,
	 * where no mixin declared the method ({@link FabricClassTransforms#removeDefaultBridges},
	 * {@link FabricClassTransforms#declareDefaultBridges}). Mixin runs both in its transformer's lock.
	 */
	private static final class DefaultBridges implements IExtension {
		private final FabricatedState state;

		DefaultBridges(FabricatedState state) {
			this.state = state;
		}

		@Override
		public boolean checkActive(MixinEnvironment environment) {
			return true;
		}

		@Override
		public void preApply(ITargetClassContext context) {
			ClassNode node = context.getClassNode();
			if (FabricClassTransforms.declaresDefaultBridges(state, node.name)) FabricClassTransforms.removeDefaultBridges(state, node);
		}

		@Override
		public void postApply(ITargetClassContext context) {
			ClassNode node = context.getClassNode();
			if (!FabricClassTransforms.declaresDefaultBridges(state, node.name)) return;
			FabricClassTransforms.declareDefaultBridges(state, node, (activity, detail) -> LOGGER.debug("{} in {}: {}", activity, node.name, detail));
		}

		@Override
		public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
		}
	}
}
