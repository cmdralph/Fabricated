package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Applies Fabric class-tweaker {@code inject-interface} directives at class load: the interface is added
 * to the target class (and to its generic signature, so reflection sees the parameterised type). Mods
 * implement the interface's methods through their own mixins, exactly as on Fabric. Runs before Mixin.
 */
public final class InterfaceInjectionProcessor implements ClassProcessor {
	private static final ProcessorName NAME = new ProcessorName("fabricated", "interface_injection");

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
		return state != null && !context.empty() && state.injectedInterfaces().containsKey(context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		FabricatedState state = FabricatedState.get();
		ClassNode node = context.node();
		Map<String, List<InjectedInterface>> injections = state.injectedInterfaces();
		boolean changed = false;
		for (InjectedInterface injection : injections.getOrDefault(node.name, List.of())) {
			if (node.interfaces.contains(injection.interfaceName())) continue;
			node.interfaces.add(injection.interfaceName());
			if (node.signature != null) {
				node.signature = node.signature + "L" + injection.genericSignature() + ";";
			}
			context.audit("fabricated:inject_interface", injection.interfaceName());
			changed = true;
		}
		return changed ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
	}
}
