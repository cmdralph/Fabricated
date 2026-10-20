package io.github.cmdralph.fabricated.loader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.translate.DefaultBridge;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Declares the methods of {@link DefaultBridge}s after Mixin applies: where an interface a Fabric mod adds brings a
 * default method the class already has from another interface (CORPUS-11), the class declares it, calling the default
 * it had, so the JVM has one to pick. A class whose mixins declare the method by then (Fabricated's own resolve some
 * such pairs) keeps theirs.
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
		return state != null && !context.empty() && state.defaultBridges().containsKey(context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		ClassNode node = context.node();
		boolean changed = false;
		for (DefaultBridge bridge : FabricatedState.get().defaultBridges().getOrDefault(node.name, List.of())) {
			if (node.methods.stream().anyMatch(m -> m.name.equals(bridge.name()) && m.desc.equals(bridge.descriptor()))) continue;
			node.methods.add(bridgeMethod(bridge));
			context.audit("fabricated:default_bridge", bridge.owner() + "." + bridge.name());
			changed = true;
		}
		return changed ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
	}

	/** {@code public R name(args) { return Owner.super.name(args); }}, with its maxs (it has no branches, so no frames). */
	private static MethodNode bridgeMethod(DefaultBridge bridge) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, bridge.name(), bridge.descriptor(), null, null);
		InsnList code = method.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		int slot = 1;
		for (Type argument : Type.getArgumentTypes(bridge.descriptor())) {
			code.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
			slot += argument.getSize();
		}
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, bridge.owner(), bridge.name(), bridge.descriptor(), true));
		Type result = Type.getReturnType(bridge.descriptor());
		code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
		method.maxLocals = slot;
		method.maxStack = Math.max(slot, result.getSize());
		return method;
	}
}
