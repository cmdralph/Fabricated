package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.function.BiConsumer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.bytecode.EnvironmentStripper;
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;

/**
 * The class transforms Fabric mods need at class load, whichever loader runs them (NeoForge's class processors, Forge's
 * transformation service). Before Mixin:
 * <ul>
 *   <li>the members of Fabric mods' classes that {@code @Environment} reserves for the other side are stripped;</li>
 *   <li>class-tweaker {@code inject-interface} directives are applied: the interface is added to the target class, and
 *       to its generic signature so reflection sees the parameterised type. Mods implement the interface's methods
 *       through their own mixins, as on Fabric.</li>
 * </ul>
 * After Mixin, {@link DefaultBridge}s are declared ({@link #declareDefaultBridges}). Each change is reported to an audit
 * consumer as an activity and its detail.
 */
public final class FabricClassTransforms {
	private FabricClassTransforms() {
	}

	/** Whether {@link #beforeMixin} may change the class (internal name). */
	public static boolean transformsBeforeMixin(FabricatedState state, String internalName) {
		return state.environmentClasses().contains(internalName) || state.injectedInterfaces().containsKey(internalName);
	}

	/** Whether {@link #declareDefaultBridges} may change the class (internal name). */
	public static boolean declaresDefaultBridges(FabricatedState state, String internalName) {
		return state.defaultBridges().containsKey(internalName);
	}

	/** Strips the other side's members and adds injected interfaces; whether the class changed. */
	public static boolean beforeMixin(FabricatedState state, ClassNode node, BiConsumer<String, String> audit) {
		boolean changed = false;
		if (state.environmentClasses().contains(node.name) && EnvironmentStripper.strip(node, state.runtime().getEnvironmentType())) {
			audit.accept("fabricated:environment_strip", node.name);
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
			audit.accept("fabricated:inject_interface", injection.interfaceName());
			changed = true;
		}
		return changed;
	}

	/**
	 * Declares the methods of the class's {@link DefaultBridge}s: where an interface a Fabric mod adds brings a default
	 * method the class already has from another interface (CORPUS-11), the class declares it, calling the default it had,
	 * so the JVM has one to pick. A method the class declares by then (its mixins') is kept. Whether the class changed.
	 */
	public static boolean declareDefaultBridges(FabricatedState state, ClassNode node, BiConsumer<String, String> audit) {
		boolean changed = false;
		for (DefaultBridge bridge : state.defaultBridges().getOrDefault(node.name, List.of())) {
			if (node.methods.stream().anyMatch(m -> m.name.equals(bridge.name()) && m.desc.equals(bridge.descriptor()))) continue;
			node.methods.add(bridgeMethod(bridge));
			audit.accept("fabricated:default_bridge", bridge.owner() + "." + bridge.name());
			changed = true;
		}
		return changed;
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
