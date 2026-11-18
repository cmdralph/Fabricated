package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.cmdralph.fabricated.core.bytecode.GamePackages;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
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
 * ({@link MixinNames#BRIDGE_INVOKER}) takes vanilla's parameters and calls the platform's version of vanilla's method with
 * them and, for that parameter, one fixed value, a constant of its type. Pre-flight declares it only where it proved,
 * from the platform's classes, that the platform's method passes that parameter only to methods of its own code whose
 * code ignores it ({@link AddedParameters}); it then points the invoker at it ({@code MixinRepairs.changedMethodBridge}),
 * and Mixin generates the invoker calling it, so the class declares it before Mixin applies its mixins. Supplementaries'
 * lit lumisene sets the blocks around it alight through vanilla's private {@code FireBlock.checkBurnOut(level, pos,
 * chance, random, age)}, to which the platforms add the side the fire comes from.
 * <p>
 * The bridge is private: Mixin's invoker of a private method calls it with {@code invokespecial} (its
 * {@code AccessorGeneratorMethodProxy}, in Forge's 0.8.5 and Fabric's 0.16), so no other class can call it, and no
 * subclass's method of its name stands in for it.
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
	 * Why the bridge as written is none pre-flight could have made, or null where it is one: a game class's, of names that
	 * are identifiers, the platform's descriptor vanilla's with one parameter more at {@code parameter}, a class or
	 * interface type, for which {@code constant} is a field's name. A translation cache entry read back is checked so.
	 */
	public String malformed() {
		if (target == null || !GamePackages.isGame(target)) return "its class " + target + " isn't a game class";
		if (!identifier(vanillaName) || !identifier(method) || !identifier(constant)) return "a name in it isn't one a class can declare";
		if (descriptor == null || methodDescriptor == null) return "it has no descriptor";
		try {
			Type[] taken = Type.getArgumentTypes(methodDescriptor);
			if (parameter < 0 || parameter >= taken.length || AddedParameters.added(descriptor, methodDescriptor) != parameter) {
				return "its descriptors don't differ by one parameter at " + parameter;
			}
			if (taken[parameter].getSort() != Type.OBJECT) return "the parameter it gives a constant isn't of a class or interface type";
		} catch (RuntimeException e) {
			return "its descriptors can't be read";
		}
		return null;
	}

	/** Whether {@code name} is a Java identifier (what a method or field of a class may be named). */
	private static boolean identifier(String name) {
		if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;
		for (int i = 1; i < name.length(); i++) {
			if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
		}
		return true;
	}

	/**
	 * Why {@code node}, the class as it loads, can't take the bridge, or null where it can: it has the platform's method as
	 * an instance method with code, and its first call of that method passes {@code constant} for the parameter, as the
	 * class pre-flight read did. Where the class changed since (another build of the platform, a cache entry not of this
	 * game), the bridge would call nothing, or with a value no proof backs.
	 */
	String unfit(ClassNode node) {
		String bad = malformed();
		if (bad != null) return bad;
		if (!node.name.equals(target)) return "it is for " + target.replace('/', '.');
		MethodNode platform = PreflightAsm.declared(node, method, methodDescriptor);
		if (platform == null || (platform.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
			return "the class has no instance method " + method + methodDescriptor + " with code to call";
		}
		Type type = Type.getArgumentTypes(methodDescriptor)[parameter];
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(node.name) || !call.name.equals(method) || !call.desc.equals(methodDescriptor)) {
					continue;
				}
				AbstractInsnNode[] pushed = OverloadRepairs.pushedBy(m, call);
				boolean passes = pushed != null && pushed[parameter + 1] instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
						&& field.owner.equals(type.getInternalName()) && field.name.equals(constant) && field.desc.equals(type.getDescriptor());
				return passes ? null : "the class's first call of " + method + " doesn't pass " + AddedParameters.typeName(type) + "." + constant;
			}
		}
		return "the class never calls " + method;
	}

	/**
	 * The bridge as {@code node} gets it: {@code private R name(vanilla's parameters) { return this.method(the same, with
	 * Type.CONSTANT at its index); }}, calling a private method as Mixin's invokers do ({@code invokespecial}), with its
	 * maxs (it has no branches, so no frames) and its {@link #MARKER}. It changes no method the class has. Null where the
	 * class hasn't the platform's method as an instance method, or already declares one of the bridge's name and
	 * descriptor.
	 */
	public MethodNode method(ClassNode node) {
		MethodNode platform = PreflightAsm.declared(node, method, methodDescriptor);
		if (platform == null || (platform.access & Opcodes.ACC_STATIC) != 0 || PreflightAsm.declares(node, name(), descriptor)) return null;
		Type[] taken = Type.getArgumentTypes(methodDescriptor);
		MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE, name(), descriptor, null, null);
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

	/**
	 * Declares in {@code node}, as it loads, the bridges Fabric mods' invokers call there ({@code bridges}, each with its
	 * invokers, {@code "<mod>: <mixin> @Invoker <method>"}), and returns their names. A bridge the class can't take
	 * ({@link #unfit}) is declared nowhere, and {@code errors} gets why, naming its invokers, whose calls then fail as they
	 * did without it. Of bridges of one name and descriptor that differ, the class keeps the one its own code backs (its
	 * first call passes that constant, to that method), as pre-flight's rule chose it from the same class: the others,
	 * which no translation of this game could have made, are errors. Where more than one fits it, it keeps none: which one
	 * the invokers' translations meant can't be told.
	 */
	public static List<String> declare(ClassNode node, Map<InvokerBridge, List<String>> bridges, Consumer<String> errors) {
		Map<String, List<InvokerBridge>> byMethod = new LinkedHashMap<>();
		for (Map.Entry<InvokerBridge, List<String>> e : bridges.entrySet()) {
			InvokerBridge bridge = e.getKey();
			String unfit = bridge.unfit(node);
			if (unfit != null) {
				errors.accept(bridge.error(node, e.getValue(), unfit));
				continue;
			}
			byMethod.computeIfAbsent(bridge.name() + bridge.descriptor(), k -> new ArrayList<>()).add(bridge);
		}
		List<String> declared = new ArrayList<>();
		for (List<InvokerBridge> same : byMethod.values()) {
			InvokerBridge bridge = same.get(0);
			if (same.size() > 1) {
				List<String> invokers = new ArrayList<>();
				for (InvokerBridge other : same) invokers.addAll(bridges.get(other));
				errors.accept(bridge.error(node, invokers, same.size() + " bridges of that name fit the class, calling " + String.join(", ",
						same.stream().map(b -> b.method() + " with " + b.constant()).toList())));
				continue;
			}
			MethodNode method = bridge.method(node);
			if (method == null) {
				// The class declares the name itself (a mixin applied before Fabricated's transform, another transformer).
				errors.accept(bridge.error(node, bridges.get(bridge), "the class already declares " + bridge.name() + bridge.descriptor()));
				continue;
			}
			node.methods.add(method);
			declared.add(bridge.name());
		}
		return declared;
	}

	/** The error for an invoker bridge {@code node} doesn't get: what, why, and the invokers whose calls fail. */
	private String error(ClassNode node, List<String> invokers, String why) {
		return "Fabricated couldn't declare " + name() + descriptor + " in " + node.name.replace('/', '.') + " for " + String.join(", ", invokers)
				+ ": " + why + ". Calls of those invokers fail (LEAVE-6)";
	}

	/** Whether the method is a bridge {@link #method} made, by its {@link #MARKER}. */
	static boolean isBridge(MethodNode method) {
		return method != null && method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> MARKER.equals(a.desc));
	}
}
