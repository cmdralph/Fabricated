package io.github.cmdralph.fabricated.loader;

import java.util.Set;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Prepares classes for Fabric mods at class load, before Mixin ({@link FabricClassTransforms#beforeMixin}): strips the
 * other side's members and applies {@code inject-interface} directives. Default method bridges come after Mixin
 * ({@link DefaultBridgeProcessor}).
 */
public final class FabricClassProcessor implements ClassProcessor {
	private static final ProcessorName NAME = new ProcessorName("fabricated", "fabric_classes");

	@Override
	public ProcessorName name() {
		return NAME;
	}

	@Override
	public Set<ProcessorName> runsBefore() {
		return Set.of(ClassProcessorIds.MIXIN);
	}

	@Override
	public boolean handlesClass(SelectionContext context) {
		FabricatedState state = FabricatedState.get();
		return state != null && !context.empty() && FabricClassTransforms.transformsBeforeMixin(state, context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		return FabricClassTransforms.beforeMixin(FabricatedState.get(), context.node(), context::audit) ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
	}
}
