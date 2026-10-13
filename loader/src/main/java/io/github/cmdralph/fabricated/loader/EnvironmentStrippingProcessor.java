package io.github.cmdralph.fabricated.loader;

import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.core.bytecode.EnvironmentStripper;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Fabric Loader's environment stripping for Fabric mods' classes (see {@link EnvironmentStripper}), at class load and
 * before Mixin. Only classes found to carry the annotations during translation are looked at.
 */
public final class EnvironmentStrippingProcessor implements ClassProcessor {
	private static final ProcessorName NAME = new ProcessorName("fabricated", "environment_stripping");

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
		return state != null && !context.empty() && state.environmentClasses().contains(context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		ClassNode node = context.node();
		boolean changed = EnvironmentStripper.strip(node, FabricatedState.get().runtime().getEnvironmentType());
		if (changed) context.audit("fabricated:environment_strip", node.name);
		return changed ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
	}
}
