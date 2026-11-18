package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A method Fabricated declares in a game class as the class loads, for a Fabric mod's {@code @Invoker} of a vanilla
 * method the platform gave a parameter vanilla's never had (LEAVE-6): {@code fabricated$invoke$<name>}
 * ({@link MixinNames#BRIDGE_INVOKER}) takes vanilla's parameters and calls the platform's method with them and, for that
 * parameter, one fixed value, a constant of its type. Pre-flight declares it only where it proved, from the platform's
 * classes, that the platform's method passes that parameter only to its extensions' methods whose defaults ignore it,
 * and makes nothing else vanilla's doesn't ({@link AddedParameters}); it then points the invoker at it
 * ({@code MixinRepairs.changedMethodBridge}), and Mixin generates the invoker calling it, so the class declares it before
 * Mixin applies its mixins. Supplementaries' lit lumisene sets the blocks around it alight through vanilla's private
 * {@code FireBlock.checkBurnOut(level, pos, chance, random, age)}, to which the platforms add the side the fire comes from.
 *
 * @param target           the class (internal name) that gets the method
 * @param vanillaName      vanilla's method's name, as the game runs (SRG's on Forge 47), which the bridge is named after
 * @param descriptor       vanilla's method's descriptor, the bridge's
 * @param method           the platform's method's name: vanilla's, or that of the method replacing vanilla's (Forge 47's
 *                         {@code tryCatchFire})
 * @param methodDescriptor the platform's method's descriptor: vanilla's with one parameter more
 * @param parameter        that parameter's index among the platform's method's
 * @param constant         the static final field of that parameter's type the bridge passes for it, by its name
 */
public record InvokerBridge(String target, String vanillaName, String descriptor, String method, String methodDescriptor, int parameter,
		String constant) {
	/**
	 * The invisible annotation on the bridge, which only marks it: the JVM and reflection never read invisible annotations,
	 * and no class of that name exists. Pre-flight tells the bridge from one Fabricated's in-game mod adds by it.
	 */
	public static final String MARKER = "Lio/github/cmdralph/fabricated/loader/InvokerBridgeMethod;";

	/** The bridge's name: {@code fabricated$invoke$} and vanilla's method's. */
	public String name() {
		return MixinNames.BRIDGE_INVOKER + vanillaName;
	}

	/**
	 * The bridge as {@code node} gets it: {@code public R name(vanilla's parameters) { return this.method(the same, with
	 * Type.CONSTANT at its index); }}, calling a private method as Mixin's invokers do ({@code invokespecial}), with its
	 * maxs (it has no branches, so no frames) and its {@link #MARKER}. Null where the class hasn't the platform's method as
	 * an instance method, or already declares one of the bridge's name and descriptor.
	 */
	public MethodNode method(ClassNode node) {
		MethodNode platform = PreflightAsm.declared(node, method, methodDescriptor);
		if (platform == null || (platform.access & Opcodes.ACC_STATIC) != 0 || PreflightAsm.declares(node, name(), descriptor)) return null;
		Type[] taken = Type.getArgumentTypes(methodDescriptor);
		MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC, name(), descriptor, null, null);
		bridge.invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MARKER)));
		InsnList code = bridge.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		int slot = 1;
		for (int k = 0; k < taken.length; k++) {
			if (k == parameter) {
				code.add(new FieldInsnNode(Opcodes.GETSTATIC, taken[k].getInternalName(), constant, taken[k].getDescriptor()));
				continue;
			}
			code.add(new VarInsnNode(taken[k].getOpcode(Opcodes.ILOAD), slot));
			slot += taken[k].getSize();
		}
		boolean own = (platform.access & Opcodes.ACC_PRIVATE) != 0;
		code.add(new MethodInsnNode(own ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL, node.name, method, methodDescriptor, false));
		Type result = Type.getReturnType(methodDescriptor);
		code.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
		bridge.maxLocals = slot;
		// The receiver and the platform's arguments; then what it returns.
		bridge.maxStack = Math.max(Type.getArgumentsAndReturnSizes(methodDescriptor) >> 2, result.getSize());
		return bridge;
	}

	/** Whether the method is a bridge {@link #method} made, by its {@link #MARKER}. */
	static boolean isBridge(MethodNode method) {
		return method != null && method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> MARKER.equals(a.desc));
	}
}
