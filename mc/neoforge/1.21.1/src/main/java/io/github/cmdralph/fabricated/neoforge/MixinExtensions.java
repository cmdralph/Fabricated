package io.github.cmdralph.fabricated.neoforge;

import java.util.TreeSet;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import io.github.cmdralph.fabricated.loader.FabricClassTransforms;
import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * Fabricated's Mixin extension on NeoForge 21.1, which declares default bridges after a class's mixins apply
 * ({@link DefaultBridges}, CORPUS-63). From NeoForge 21.11 on, a class processor of Fabricated's runs after Mixin on
 * every class (the loader's {@code DefaultBridgeProcessor}); NeoForge 21.1's FML 4 runs on ModLauncher, where Mixin is a
 * launch plugin and no transformer from the mods folder runs after it. A Mixin extension runs as Mixin applies mixins to
 * a class, before and after they apply, and nowhere else: the loader's transformer declares the bridges before Mixin for
 * the classes no mixin targets.
 */
final class MixinExtensions {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private MixinExtensions() {
	}

	/**
	 * Registers the extension with Mixin's transformer. Called from Fabricated's mixin plugin as Mixin selects the configs
	 * (the plugin's {@code onLoad}), before Mixin activates its extensions for the configs it selected, and so before it
	 * applies a mixin of theirs.
	 */
	static void install() {
		FabricatedState state = FabricatedState.get();
		if (state == null || state.defaultBridges().isEmpty()) return;
		Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
		if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
			extensions.add(new DefaultBridges(state));
		} else {
			// The bridges declared before Mixin stay as Mixin applies mixins: only a mixin declaring one of those methods
			// itself, in a config requiring @Overwrite to replace a method, then fails.
			LOGGER.error("Fabricated could not register its Mixin extension with Mixin's transformer ({}): the default method bridges it declares"
					+ " before Mixin stay as mixins apply, so a mixin declaring one of those methods may fail. Classes with bridges: {}", transformer,
					String.join(", ", new TreeSet<>(state.defaultBridges().keySet())));
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
