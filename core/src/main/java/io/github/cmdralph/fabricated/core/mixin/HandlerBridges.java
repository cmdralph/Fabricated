package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * The bridge methods pre-flight's repairs add to a mixin: each takes the arguments NeoForge's code has at an injection
 * point and calls the mod's handler with the ones it was written for, and the injector moves onto it. A bridge leaves an
 * argument NeoForge added out (for a {@code @WrapOperation}, with an {@code Operation} that puts it back), or takes a
 * constructor's parameters for a handler written for the one NeoForge kept. The former handler becomes private and
 * {@code @Unique}, so Mixin merges it as the mod's own method.
 */
final class HandlerBridges {
	/** The package of MixinExtras' sugar annotations ({@code @Local}, {@code @Share}, {@code @Cancellable}). */
	private static final String SUGAR = "Lcom/llamalad7/mixinextras/sugar/";
	/** {@code @Expression}, {@code @Definition} and {@code @Definitions}. */
	private static final String EXPRESSION_PACKAGE = "Lcom/llamalad7/mixinextras/expression/";
	private static final String COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;";

	private HandlerBridges() {
	}

	/** The parameters of a handler that takes a call's receiver (unless static) and arguments. */
	static List<Type> callParameters(Type owner, String desc, boolean staticCall) {
		List<Type> params = new ArrayList<>();
		if (!staticCall) params.add(owner);
		params.addAll(List.of(Type.getArgumentTypes(desc)));
		return params;
	}

	/** {@code base}, numbered when the mixin already has a method of that name: two handlers may share theirs. */
	static String freeName(ClassNode mixin, List<MethodNode> added, String base) {
		String name = base;
		for (int i = 2; nameTaken(mixin, added, name); i++) name = base + i;
		return name;
	}

	static boolean nameTaken(ClassNode mixin, List<MethodNode> added, String name) {
		for (MethodNode m : mixin.methods) {
			if (m.name.equals(name)) return true;
		}
		for (MethodNode m : added) {
			if (m.name.equals(name)) return true;
		}
		return false;
	}

	/**
	 * A {@code @Redirect} or {@code @WrapWithCondition} handler for {@code overload}'s call: it calls {@code handler},
	 * written for {@code desc}'s, without the added argument. Null when {@code handler} takes anything but that call's
	 * receiver ({@code owner}) and arguments (one {@code @Coerce}d to a supertype too, the bridge's coerced so), then
	 * the first of {@code method}'s parameters and MixinExtras sugar ({@link #sugarAfter}), or doesn't return {@code result}.
	 */
	static MethodNode callBridge(ClassNode mixin, MethodNode handler, Type owner, String desc, String overload, int inserted, boolean staticCall,
			Type result, List<Type> method, List<MethodNode> added) {
		List<Type> call = callParameters(owner, desc, staticCall);
		List<Type> sugar = sugarAfter(handler, call, method);
		if (sugar == null || !Type.getReturnType(handler.desc).equals(result)) return null;
		int skip = inserted + (staticCall ? 0 : 1);
		List<Type> params = withCoerced(handler, call, callParameters(owner, overload, staticCall), skip);
		int sugarFrom = params.size();
		params.addAll(sugar);
		MethodNode bridge = bridgeMethod(handler, freeName(mixin, added, handler.name + "$fabricated"), params, Type.getReturnType(handler.desc));
		int stack = loadAllBut(bridge, handler, params, skip);
		moveSugar(handler, call.size(), bridge, sugarFrom, sugar.size());
		coerced(handler, call.size(), bridge, skip);
		callHandler(bridge, mixin.name, handler, stack);
		return bridge;
	}

	/**
	 * A {@code @WrapOperation} handler for {@code overload}'s call: it calls {@code handler}, written for {@code desc}'s,
	 * without the added argument, and with an {@code Operation} that puts it back before making the call (a lambda the
	 * bridge adds to {@code extra}). Null when {@code handler} takes anything but that call's receiver ({@code owner})
	 * and arguments (as {@link #callBridge} takes them), the {@code Operation}, then the first of {@code method}'s
	 * parameters and MixinExtras sugar.
	 */
	static MethodNode wrapperBridge(ClassNode mixin, MethodNode handler, Type owner, String desc, String overload, int inserted, boolean staticCall,
			List<Type> method, List<MethodNode> extra) {
		Type operation = Type.getType(MixinNames.OPERATION);
		List<Type> expected = callParameters(owner, desc, staticCall);
		expected.add(operation);
		List<Type> sugar = sugarAfter(handler, expected, method);
		if (sugar == null) return null;
		int skip = inserted + (staticCall ? 0 : 1);
		List<Type> params = withCoerced(handler, expected.subList(0, expected.size() - 1), callParameters(owner, overload, staticCall), skip);
		Type added = params.get(skip);
		int operationAt = params.size();
		params.add(operation);
		params.addAll(sugar);
		MethodNode bridge = bridgeMethod(handler, freeName(mixin, extra, handler.name + "$fabricated"), params, Type.getReturnType(handler.desc));
		int stack = loadAllBut(bridge, handler, params.subList(0, operationAt), skip);
		// The Operation the handler gets: the original one, with the added argument put back.
		MethodNode lambda = argumentInserter(freeName(mixin, extra, "lambda$" + handler.name + "$fabricated"), added, skip);
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, slotOf(handler, params, operationAt)));
		bridge.instructions.add(new VarInsnNode(added.getOpcode(Opcodes.ILOAD), slotOf(handler, params, skip)));
		Type objects = Type.getType("([Ljava/lang/Object;)Ljava/lang/Object;");
		bridge.instructions.add(new InvokeDynamicInsnNode("call", Type.getMethodDescriptor(operation, operation, added),
				new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
						"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
								+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				objects, new Handle(Opcodes.H_INVOKESTATIC, mixin.name, lambda.name, lambda.desc, false), objects));
		int sugarSize = 0;
		for (int i = operationAt + 1; i < params.size(); i++) {
			bridge.instructions.add(new VarInsnNode(params.get(i).getOpcode(Opcodes.ILOAD), slotOf(handler, params, i)));
			sugarSize += params.get(i).getSize();
		}
		moveSugar(handler, expected.size(), bridge, operationAt + 1, sugar.size());
		coerced(handler, expected.size() - 1, bridge, skip);
		callHandler(bridge, mixin.name, handler, stack + 1 + Math.max(added.getSize(), sugarSize));
		extra.add(lambda);
		return bridge;
	}

	/**
	 * Replaces {@code handler}'s {@code injector} with {@code replacement} on {@code bridge}, in the same list of
	 * annotations (Mixin's and MixinExtras' injectors are both kept at run time); {@code handler} stays a plain method of
	 * the mixin, as {@link #moveInjector} leaves it.
	 */
	static void replaceInjector(MethodNode handler, AnnotationNode injector, AnnotationNode replacement, MethodNode bridge) {
		moveInjector(handler, injector, bridge);
		for (List<AnnotationNode> list : Arrays.asList(bridge.visibleAnnotations, bridge.invisibleAnnotations)) {
			if (list == null) continue;
			int at = list.indexOf(injector);
			if (at >= 0) list.set(at, replacement);
		}
	}

	/**
	 * The types of {@code handler}'s parameters after {@code leading}, which it must start with: the first of
	 * {@code method}'s parameters, which Mixin passes a handler taking them (Fabric API's before-bake model hook takes
	 * the model's id that way), then MixinExtras sugar ({@code @Local}, {@code @Share}, {@code @Cancellable}), which
	 * MixinExtras gives a bridge as it would the handler. A bridge takes them too and passes them on. One of
	 * {@code leading} may be {@code @Coerce}d to another reference type ({@link #coerced}), which a bridge takes as the
	 * handler does. Null when the handler doesn't start with {@code leading} so, one of those is sugar, or one after the
	 * method's parameters isn't sugar.
	 *
	 * @param method the parameters of the methods the injector applies to, as far as they are the same
	 */
	static List<Type> sugarAfter(MethodNode handler, List<Type> leading, List<Type> method) {
		List<Type> own = List.of(Type.getArgumentTypes(handler.desc));
		if (own.size() < leading.size()) return null;
		for (int i = 0; i < own.size(); i++) {
			boolean coerce = false;
			boolean sugar = false;
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				coerce |= a.desc.equals(COERCE);
				sugar |= a.desc.startsWith(SUGAR);
			}
			if (i < leading.size() ? sugar || !own.get(i).equals(leading.get(i)) && !(coerce && isReference(own.get(i)) && isReference(leading.get(i)))
					: coerce) return null;
		}
		int captured = 0;
		while (leading.size() + captured < own.size() && captured < method.size() && !isSugar(handler, leading.size() + captured)
				&& own.get(leading.size() + captured).equals(method.get(captured))) captured++;
		for (int i = leading.size() + captured; i < own.size(); i++) {
			if (!isSugar(handler, i)) return null;
		}
		return own.subList(leading.size(), own.size());
	}

	/** Whether {@code handler}'s parameter at {@code index} is MixinExtras sugar. */
	private static boolean isSugar(MethodNode handler, int index) {
		return Annotations.ofParameter(handler, index).stream().anyMatch(a -> a.desc.startsWith(SUGAR));
	}

	private static boolean isReference(Type type) {
		return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
	}

	/**
	 * {@code params}, a bridge's parameters for a call with one more argument than {@code handler}'s at {@code skip},
	 * with the type {@code handler} {@code @Coerce}s each of its first {@code count} to, and that annotation on the
	 * bridge's: Mixin then coerces the bridge's as it would the handler's. Call once the bridge exists.
	 */
	static void coerced(MethodNode handler, int count, MethodNode bridge, int skip) {
		Type[] own = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(bridge.desc);
		for (int i = 0; i < count; i++) {
			int at = i < skip ? i : i + 1;
			// Where the handler's class file has it: Mixin reads it there.
			for (boolean visible : new boolean[] {true, false}) {
				List<AnnotationNode>[] from = visible ? handler.visibleParameterAnnotations : handler.invisibleParameterAnnotations;
				AnnotationNode coerce = from == null || i >= from.length || from[i] == null ? null
						: from[i].stream().filter(a -> a.desc.equals(COERCE)).findFirst().orElse(null);
				if (coerce == null) continue;
				if (!own[i].equals(params[at])) throw new IllegalStateException("bridge parameter " + at + " isn't " + own[i]);
				if (visible && bridge.visibleParameterAnnotations == null) bridge.visibleParameterAnnotations = newParameterAnnotations(params.length);
				if (!visible && bridge.invisibleParameterAnnotations == null) bridge.invisibleParameterAnnotations = newParameterAnnotations(params.length);
				List<AnnotationNode>[] to = visible ? bridge.visibleParameterAnnotations : bridge.invisibleParameterAnnotations;
				if (to[at] == null) to[at] = new ArrayList<>();
				to[at].add(coerce);
			}
		}
	}

	/**
	 * {@code params}, a bridge's parameters for the call with one more argument at {@code skip}, with {@code handler}'s
	 * own types where it {@code @Coerce}s one of {@code leading}.
	 */
	static List<Type> withCoerced(MethodNode handler, List<Type> leading, List<Type> params, int skip) {
		Type[] own = Type.getArgumentTypes(handler.desc);
		List<Type> out = new ArrayList<>(params);
		for (int i = 0; i < leading.size(); i++) {
			if (!own[i].equals(leading.get(i))) out.set(i < skip ? i : i + 1, own[i]);
		}
		return out;
	}

	/** Moves the annotations of {@code handler}'s {@code count} sugar parameters from {@code from} to {@code bridge}'s from {@code to}. */
	static void moveSugar(MethodNode handler, int from, MethodNode bridge, int to, int count) {
		if (count == 0) return;
		int params = Type.getArgumentTypes(bridge.desc).length;
		for (int i = 0; i < count; i++) {
			if (handler.visibleParameterAnnotations != null && from + i < handler.visibleParameterAnnotations.length) {
				if (bridge.visibleParameterAnnotations == null) bridge.visibleParameterAnnotations = newParameterAnnotations(params);
				bridge.visibleParameterAnnotations[to + i] = handler.visibleParameterAnnotations[from + i];
				handler.visibleParameterAnnotations[from + i] = null;
			}
			if (handler.invisibleParameterAnnotations != null && from + i < handler.invisibleParameterAnnotations.length) {
				if (bridge.invisibleParameterAnnotations == null) bridge.invisibleParameterAnnotations = newParameterAnnotations(params);
				bridge.invisibleParameterAnnotations[to + i] = handler.invisibleParameterAnnotations[from + i];
				handler.invisibleParameterAnnotations[from + i] = null;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static List<AnnotationNode>[] newParameterAnnotations(int count) {
		return (List<AnnotationNode>[]) new List<?>[count];
	}

	/** {@code (Operation original, T value, Object[] args) -> original.call(args with value inserted at index)}. */
	static MethodNode argumentInserter(String name, Type value, int index) {
		Type operation = Type.getType(MixinNames.OPERATION);
		MethodNode lambda = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name,
				Type.getMethodDescriptor(Type.getType(Object.class), operation, value, Type.getType(Object[].class)), null, null);
		int args = 1 + value.getSize();
		int all = args + 1;
		InsnList code = lambda.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new InsnNode(Opcodes.ARRAYLENGTH));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IADD));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
		code.add(new VarInsnNode(Opcodes.ASTORE, all));
		// System.arraycopy(args, 0, all, 0, index)
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new LdcInsnNode(index));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false));
		// all[index] = value
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new LdcInsnNode(index));
		code.add(new VarInsnNode(value.getOpcode(Opcodes.ILOAD), 1));
		box(code, value);
		code.add(new InsnNode(Opcodes.AASTORE));
		// System.arraycopy(args, index, all, index + 1, args.length - index)
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new LdcInsnNode(index));
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new LdcInsnNode(index + 1));
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new InsnNode(Opcodes.ARRAYLENGTH));
		code.add(new LdcInsnNode(index));
		code.add(new InsnNode(Opcodes.ISUB));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, operation.getInternalName(), "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
		code.add(new InsnNode(Opcodes.ARETURN));
		lambda.maxStack = 6;
		lambda.maxLocals = all + 1;
		return lambda;
	}

	static void box(InsnList code, Type type) {
		String boxed = boxOf(type);
		if (boxed != null) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, boxed, "valueOf", "(" + type.getDescriptor() + ")L" + boxed + ";", false));
	}

	/** Casts the object on the stack to {@code type}, unboxing a primitive. */
	static void unbox(InsnList code, Type type) {
		String boxed = boxOf(type);
		if (boxed == null) {
			if (!type.getDescriptor().equals("Ljava/lang/Object;")) {
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getSort() == Type.ARRAY ? type.getDescriptor() : type.getInternalName()));
			}
			return;
		}
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, boxed));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, boxed, type.getClassName() + "Value", "()" + type.getDescriptor(), false));
	}

	/** The class boxing a primitive type; null for any other. */
	private static String boxOf(Type type) {
		return switch (type.getSort()) {
			case Type.BOOLEAN -> "java/lang/Boolean";
			case Type.CHAR -> "java/lang/Character";
			case Type.BYTE -> "java/lang/Byte";
			case Type.SHORT -> "java/lang/Short";
			case Type.INT -> "java/lang/Integer";
			case Type.FLOAT -> "java/lang/Float";
			case Type.LONG -> "java/lang/Long";
			case Type.DOUBLE -> "java/lang/Double";
			default -> null;
		};
	}

	/** An empty bridge with {@code handler}'s access (static or not) and the given signature. */
	static MethodNode bridgeMethod(MethodNode handler, String name, List<Type> params, Type result) {
		return bridgeMethod((handler.access & Opcodes.ACC_STATIC) != 0, name, params, result);
	}

	/** An empty bridge, static or taking {@code this}, with the given signature. */
	static MethodNode bridgeMethod(boolean isStatic, String name, List<Type> params, Type result) {
		return new MethodNode(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0), name,
				Type.getMethodDescriptor(result, params.toArray(Type[]::new)), null, null);
	}

	/** The local variable slot of the bridge's parameter at {@code index}, for a bridge with {@code handler}'s access. */
	static int slotOf(MethodNode handler, List<Type> params, int index) {
		return slotOf((handler.access & Opcodes.ACC_STATIC) != 0, params, index);
	}

	/** The local variable slot of the parameter at {@code index} of a method, static or taking {@code this}, taking {@code params}. */
	static int slotOf(boolean isStatic, List<Type> params, int index) {
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < index; i++) slot += params.get(i).getSize();
		return slot;
	}

	/** Loads {@code this} (unless static) and each of {@code params} but the one at {@code skip}; returns the stack size. */
	static int loadAllBut(MethodNode bridge, MethodNode handler, List<Type> params, int skip) {
		int stack = 0;
		if ((handler.access & Opcodes.ACC_STATIC) == 0) {
			bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			stack++;
		}
		for (int i = 0; i < params.size(); i++) {
			if (i == skip) continue;
			bridge.instructions.add(new VarInsnNode(params.get(i).getOpcode(Opcodes.ILOAD), slotOf(handler, params, i)));
			stack += params.get(i).getSize();
		}
		return stack;
	}

	/** Calls {@code handler} with what is on the stack and returns its result. */
	static void callHandler(MethodNode bridge, String mixin, MethodNode handler, int stack) {
		boolean isStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		bridge.instructions.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, mixin, handler.name, handler.desc, false));
		bridge.instructions.add(new InsnNode(Type.getReturnType(handler.desc).getOpcode(Opcodes.IRETURN)));
		bridge.maxStack = Math.max(stack, Type.getReturnType(handler.desc).getSize());
		bridge.maxLocals = slotOf((bridge.access & Opcodes.ACC_STATIC) != 0, List.of(Type.getArgumentTypes(bridge.desc)), Type.getArgumentTypes(bridge.desc).length);
	}

	/**
	 * Moves the injector annotation from {@code handler} to {@code bridge}, with the MixinExtras expression's
	 * {@code @Expression} and {@code @Definition}s, which MixinExtras reads from the injector's method; {@code handler}
	 * stays a plain method of the mixin, private (Mixin takes no other static methods) and {@code @Unique}, so that Mixin
	 * renames it rather than merging it over a method of the same name another mixin added (as an injector, Mixin gave it
	 * a name of its own).
	 */
	static void moveInjector(MethodNode handler, AnnotationNode injector, MethodNode bridge) {
		handler.access = handler.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PRIVATE;
		if (Annotations.find(handler, MixinNames.UNIQUE) == null) {
			if (handler.visibleAnnotations == null) handler.visibleAnnotations = new ArrayList<>();
			handler.visibleAnnotations.add(new AnnotationNode(MixinNames.UNIQUE));
		}
		if (handler.visibleAnnotations != null && handler.visibleAnnotations.remove(injector)) {
			bridge.visibleAnnotations = new ArrayList<>(List.of(injector));
		} else if (handler.invisibleAnnotations != null && handler.invisibleAnnotations.remove(injector)) {
			bridge.invisibleAnnotations = new ArrayList<>(List.of(injector));
		}
		bridge.visibleAnnotations = withExpression(handler.visibleAnnotations, bridge.visibleAnnotations);
		bridge.invisibleAnnotations = withExpression(handler.invisibleAnnotations, bridge.invisibleAnnotations);
	}

	/** {@code to} with the expression annotations taken out of {@code from}. */
	private static List<AnnotationNode> withExpression(List<AnnotationNode> from, List<AnnotationNode> to) {
		if (from == null || from.stream().noneMatch(a -> a.desc.startsWith(EXPRESSION_PACKAGE))) return to;
		List<AnnotationNode> moved = to == null ? new ArrayList<>() : to;
		for (Iterator<AnnotationNode> it = from.iterator(); it.hasNext(); ) {
			AnnotationNode a = it.next();
			if (!a.desc.startsWith(EXPRESSION_PACKAGE)) continue;
			it.remove();
			moved.add(a);
		}
		return moved;
	}

	/**
	 * A handler for {@code injector} at {@code overload} instead of the method that only calls it with one value inserted
	 * among its own parameters (at {@code inserted}): it takes what {@code handler} takes, with the {@code count}
	 * parameters it takes from {@code from} on (that method's) replaced by the overload's, and calls {@code handler} with
	 * everything but the inserted one. The injector annotation and the annotations of what follows them (callback info,
	 * MixinExtras sugar) move to it; {@code handler} stays a plain method of the mixin.
	 */
	static MethodNode insertedParameterBridge(String mixin, MethodNode handler, AnnotationNode injector, int from, int count, MethodNode overload,
			int inserted, String name) {
		List<Type> own = List.of(Type.getArgumentTypes(handler.desc));
		List<Type> params = new ArrayList<>(own.subList(0, from));
		params.addAll(List.of(Type.getArgumentTypes(overload.desc)));
		params.addAll(own.subList(from + count, own.size()));
		MethodNode bridge = bridgeMethod(handler, name, params, Type.getReturnType(handler.desc));
		int stack = loadAllBut(bridge, handler, params, from + inserted);
		moveSugar(handler, from + count, bridge, from + count + 1, own.size() - from - count);
		callHandler(bridge, mixin, handler, stack);
		moveInjector(handler, injector, bridge);
		return bridge;
	}

	/**
	 * A handler for a local-capturing {@code @Inject} at {@code overload} instead of the method that only calls it with one
	 * value inserted among its own parameters (at {@code inserted}): it takes the overload's parameters, the callback info
	 * and {@code locals}, the overload's first locals there as Mixin passes them, and calls {@code handler} with the
	 * method's parameters (all but the inserted one), the callback info and, for each local {@code handler} captures, the
	 * one of {@code locals} at its index in {@code picked}. The injector annotation moves to it; {@code handler} stays a
	 * plain method of the mixin.
	 */
	static MethodNode capturedLocalsBridge(String mixin, MethodNode handler, AnnotationNode injector, MethodNode overload, int inserted, List<Type> locals,
			int[] picked, String name) {
		List<Type> overloadParams = List.of(Type.getArgumentTypes(overload.desc));
		Type callbackInfo = Type.getArgumentTypes(handler.desc)[overloadParams.size() - 1];
		List<Type> params = new ArrayList<>(overloadParams);
		params.add(callbackInfo);
		params.addAll(locals);
		MethodNode bridge = bridgeMethod(handler, name, params, Type.VOID_TYPE);
		int stack = loadAllBut(bridge, handler, params.subList(0, overloadParams.size() + 1), inserted);
		for (int p : picked) {
			int index = overloadParams.size() + 1 + p;
			bridge.instructions.add(new VarInsnNode(params.get(index).getOpcode(Opcodes.ILOAD), slotOf(handler, params, index)));
			stack += params.get(index).getSize();
		}
		callHandler(bridge, mixin, handler, stack);
		moveInjector(handler, injector, bridge);
		return bridge;
	}

	/**
	 * A handler for {@code injector} at {@code canonical} instead of {@code delegate}: it takes {@code canonical}'s
	 * parameters and the callback info, and calls {@code handler} with {@code delegate}'s (the same values, see
	 * {@link ConstructorRepairs#forwardsItsParameters}) and the callback info. The injector annotation moves to it; {@code handler} stays a
	 * plain method of the mixin.
	 */
	static MethodNode bridge(String mixin, MethodNode handler, AnnotationNode injector, MethodNode delegate, MethodNode canonical, String name) {
		Type[] own = Type.getArgumentTypes(delegate.desc);
		Type[] all = Type.getArgumentTypes(canonical.desc);
		Type callbackInfo = Type.getArgumentTypes(handler.desc)[own.length];
		Type[] params = Arrays.copyOf(all, all.length + 1);
		params[all.length] = callbackInfo;
		boolean isStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE | (handler.access & Opcodes.ACC_STATIC), name, Type.getMethodDescriptor(Type.VOID_TYPE, params),
				null, null);
		int slot = 0;
		if (!isStatic) bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, slot++));
		int[] slots = new int[params.length];
		for (int i = 0; i < params.length; i++) {
			slots[i] = slot;
			slot += params[i].getSize();
		}
		int stack = isStatic ? 0 : 1;
		for (int i = 0; i < own.length; i++) {
			bridge.instructions.add(new VarInsnNode(own[i].getOpcode(Opcodes.ILOAD), slots[i]));
			stack += own[i].getSize();
		}
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, slots[params.length - 1]));
		bridge.instructions.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, mixin, handler.name, handler.desc, false));
		bridge.instructions.add(new InsnNode(Opcodes.RETURN));
		bridge.maxStack = stack + 1;
		bridge.maxLocals = slot;
		moveInjector(handler, injector, bridge);
		return bridge;
	}

	/**
	 * A handler for {@code injector} at {@code delegate} instead of {@code constructor}, which only calls it: it takes
	 * {@code delegate}'s parameters and the callback info, and calls {@code handler}, written for {@code constructor},
	 * with the arguments {@code constructor} was called with, which {@code arguments} holds during that call
	 * ({@link ConstructorHandoffs}), and the callback info; where it holds none, it returns. The injector annotation moves
	 * to it; {@code handler} stays a plain method of the mixin.
	 */
	static MethodNode handedOffBridge(String mixin, MethodNode handler, AnnotationNode injector, MethodNode constructor, MethodNode delegate,
			FieldNode arguments, String name) {
		Type[] own = Type.getArgumentTypes(constructor.desc);
		List<Type> params = new ArrayList<>(List.of(Type.getArgumentTypes(delegate.desc)));
		params.add(Type.getArgumentTypes(handler.desc)[own.length]);
		MethodNode bridge = bridgeMethod(handler, name, params, Type.VOID_TYPE);
		InsnList code = bridge.instructions;
		LabelNode handedOff = new LabelNode();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new FieldInsnNode(Opcodes.GETFIELD, mixin, arguments.name, arguments.desc));
		code.add(new JumpInsnNode(Opcodes.IFNONNULL, handedOff));
		code.add(new InsnNode(Opcodes.RETURN));
		code.add(handedOff);
		code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		int stack = 1;
		int peak = 1;
		for (int i = 0; i < own.length; i++) {
			code.add(new VarInsnNode(Opcodes.ALOAD, 0));
			code.add(new FieldInsnNode(Opcodes.GETFIELD, mixin, arguments.name, arguments.desc));
			code.add(new LdcInsnNode(i));
			code.add(new InsnNode(Opcodes.AALOAD));
			unbox(code, own[i]);
			peak = Math.max(peak, stack + 2);
			stack += own[i].getSize();
		}
		code.add(new VarInsnNode(Opcodes.ALOAD, slotOf(handler, params, params.size() - 1)));
		callHandler(bridge, mixin, handler, stack + 1);
		bridge.maxStack = Math.max(bridge.maxStack, peak);
		moveInjector(handler, injector, bridge);
		return bridge;
	}
}
