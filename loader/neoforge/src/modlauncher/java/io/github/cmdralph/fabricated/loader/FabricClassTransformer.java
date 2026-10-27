package io.github.cmdralph.fabricated.loader;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * ModLauncher's transformer for the classes {@link FabricTransformationService} names: {@link FabricClassTransforms#beforeMixin}.
 * NeoForge's ModLauncher 11 asks a transformer for its node type ({@link #getTargetType}), where Forge's reads it from
 * the class's type argument.
 */
final class FabricClassTransformer implements ITransformer<ClassNode> {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private final Set<Target<ClassNode>> targets;
	private final Supplier<Optional<ModuleLayer>> gameLayer;
	private final AtomicBoolean layerChecked = new AtomicBoolean();

	/**
	 * @param classes   internal names
	 * @param gameLayer the game's module layer, once ModLauncher has built it ({@link GameLayerCheck})
	 */
	FabricClassTransformer(Set<String> classes, Supplier<Optional<ModuleLayer>> gameLayer) {
		this.targets = classes.stream().map(Target::targetClass).collect(Collectors.toUnmodifiableSet());
		this.gameLayer = gameLayer;
	}

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		FabricatedState state = FabricatedState.get();
		if (state != null && !layerChecked.get()) {
			gameLayer.get().ifPresent(game -> {
				if (layerChecked.compareAndSet(false, true)) GameLayerCheck.run(game, state.injectedInterfaces());
			});
		}
		// A target that doesn't exist reaches a transformer as an empty node.
		if (state != null && input.name != null) {
			FabricClassTransforms.beforeMixin(state, input, (activity, detail) -> LOGGER.debug("{} in {}: {}", activity, input.name, detail));
		}
		return input;
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<Target<ClassNode>> targets() {
		return targets;
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
