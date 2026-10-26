package io.github.cmdralph.fabricated.loader;

import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * ModLauncher's transformer for the classes {@link FabricTransformationService} names: {@link FabricClassTransforms#beforeMixin}.
 * ModLauncher tells a transformer's node type from the type argument its class declares, so this is a class of its own.
 */
final class FabricClassTransformer implements ITransformer<ClassNode> {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private final Set<Target> targets;

	/** @param classes internal names */
	FabricClassTransformer(Set<String> classes) {
		this.targets = classes.stream().map(Target::targetClass).collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		FabricatedState state = FabricatedState.get();
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
	public Set<Target> targets() {
		return targets;
	}
}
