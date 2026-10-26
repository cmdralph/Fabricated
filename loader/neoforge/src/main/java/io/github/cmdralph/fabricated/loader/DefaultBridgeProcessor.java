package io.github.cmdralph.fabricated.loader;

import java.util.HashSet;
import java.util.Set;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Declares the methods of default bridges after Mixin applies ({@link FabricClassTransforms#declareDefaultBridges}),
 * so a class whose mixins declare the method by then keeps theirs.
 */
public final class DefaultBridgeProcessor implements ClassProcessor {
	private static final ProcessorName NAME = new ProcessorName("fabricated", "default_bridges");

	@Override
	public ProcessorName name() {
		return NAME;
	}

	@Override
	public Set<ProcessorName> runsAfter() {
		Set<ProcessorName> after = new HashSet<>(ClassProcessor.super.runsAfter());
		after.add(ClassProcessorIds.MIXIN);
		return after;
	}

	@Override
	public boolean handlesClass(SelectionContext context) {
		FabricatedState state = FabricatedState.get();
		return state != null && !context.empty() && FabricClassTransforms.declaresDefaultBridges(state, context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		return FabricClassTransforms.declareDefaultBridges(FabricatedState.get(), context.node(), context::audit) ? ComputeFlags.SIMPLE_REWRITE
				: ComputeFlags.NO_REWRITE;
	}
}
