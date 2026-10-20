package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.bytecode.EnvironmentStripper;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Prepares classes for Fabric mods at class load, before Mixin:
 * <ul>
 *   <li>strips the members of Fabric mods' classes that {@code @Environment} reserves for the other side;</li>
 *   <li>applies class-tweaker {@code inject-interface} directives: the interface is added to the target class, and to
 *       its generic signature so reflection sees the parameterised type. Mods implement the interface's methods
 *       through their own mixins, as on Fabric.</li>
 * </ul>
 * Default method bridges come after Mixin ({@link DefaultBridgeProcessor}).
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
		if (state == null || context.empty()) return false;
		String name = context.type().getInternalName();
		return state.environmentClasses().contains(name) || state.injectedInterfaces().containsKey(name);
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		FabricatedState state = FabricatedState.get();
		ClassNode node = context.node();
		boolean changed = false;
		if (state.environmentClasses().contains(node.name) && EnvironmentStripper.strip(node, state.runtime().getEnvironmentType())) {
			context.audit("fabricated:environment_strip", node.name);
			changed = true;
		}
		for (InjectedInterface injection : state.injectedInterfaces().getOrDefault(node.name, List.of())) {
			if (node.interfaces.contains(injection.interfaceName())) continue;
			if (node.signature == null && injection.genericSignature().indexOf('<') >= 0) {
				// A class without generic types has no signature; it needs one to carry the interface's type arguments.
				StringBuilder signature = new StringBuilder("L").append(node.superName).append(';');
				for (String itf : node.interfaces) signature.append('L').append(itf).append(';');
				node.signature = signature.toString();
			}
			node.interfaces.add(injection.interfaceName());
			if (node.signature != null) node.signature += "L" + injection.genericSignature() + ";";
			context.audit("fabricated:inject_interface", injection.interfaceName());
			changed = true;
		}
		return changed ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
	}
}
