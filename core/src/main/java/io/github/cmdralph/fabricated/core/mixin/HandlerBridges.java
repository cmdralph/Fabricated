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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
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
	 * receiver ({@code owner}) and arguments (a {@code @Coerce}d supertype included: the bridge wouldn't carry the
	 * annotation) and then MixinExtras sugar, or doesn't return {@code result}.
	 */
	static MethodNode callBridge(ClassNode mixin, MethodNode handler, Type owner, String desc, String overload, int inserted, boolean staticCall,
			Type result, List<MethodNode> added) {
		List<Type> call = callParameters(owner, desc, staticCall);
		List<Type> sugar = sugarAfter(handler, call);
		if (sugar == null || !Type.getReturnType(handler.desc).equals(result)) return null;
		List<Type> params = callParameters(owner, overload, staticCall);
		int skip = inserted + (staticCall ? 0 : 1);
		int sugarFrom = params.size();
		params.addAll(sugar);
		MethodNode bridge = bridgeMethod(handler, freeName(mixin, added, handler.name + "$fabricated"), params, Type.getReturnType(handler.desc));
		int stack = loadAllBut(bridge, handler, params, skip);
		moveSugar(handler, call.size(), bridge, sugarFrom, sugar.size());
		callHandler(bridge, mixin.name, handler, stack);
		return bridge;
	}

	/**
	 * A {@code @WrapOperation} handler for {@code overload}'s call: it calls {@code handler}, written for {@code desc}'s,
	 * without the added argument, and with an {@code Operation} that puts it back before making the call (a lambda the
	 * bridge adds to {@code extra}). Null when {@code handler} takes anything but that call's receiver ({@code owner})
	 * and arguments, the {@code Operation}, and then MixinExtras sugar.
	 */
	static MethodNode wrapperBridge(ClassNode mixin, MethodNode handler, Type owner, String desc, String overload, int inserted, boolean staticCall,
			List<MethodNode> extra) {
		Type operation = Type.getType(MixinPreflight.OPERATION);
		List<Type> expected = callParameters(owner, desc, staticCall);
		expected.add(operation);
		List<Type> sugar = sugarAfter(handler, expected);
		if (sugar == null) return null;
		List<Type> params = callParameters(owner, overload, staticCall);
		int skip = inserted + (staticCall ? 0 : 1);
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
		callHandler(bridge, mixin.name, handler, stack + 1 + Math.max(added.getSize(), sugarSize));
		extra.add(lambda);
		return bridge;
	}

	/**
	 * The types of {@code handler}'s parameters after {@code leading}, which it must start with: MixinExtras sugar
	 * ({@code @Local}, {@code @Share}, {@code @Cancellable}), which MixinExtras gives a bridge as it would the handler, so
	 * a bridge takes them too and passes them on. Null when the handler doesn't start with {@code leading}, one of those
	 * is {@code @Coerce}d or sugar (the bridge passes them as they are), or one after them isn't sugar.
	 */
	static List<Type> sugarAfter(MethodNode handler, List<Type> leading) {
		List<Type> own = List.of(Type.getArgumentTypes(handler.desc));
		if (own.size() < leading.size() || !own.subList(0, leading.size()).equals(leading)) return null;
		for (int i = 0; i < own.size(); i++) {
			boolean sugar = false;
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (a.desc.equals(COERCE)) return null;
				sugar |= a.desc.startsWith(SUGAR);
			}
			if (sugar != i >= leading.size()) return null;
		}
		return own.subList(leading.size(), own.size());
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
		Type operation = Type.getType(MixinPreflight.OPERATION);
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
		String boxed = switch (type.getSort()) {
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
		if (boxed != null) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, boxed, "valueOf", "(" + type.getDescriptor() + ")L" + boxed + ";", false));
	}

	/** An empty bridge with {@code handler}'s access (static or not) and the given signature. */
	static MethodNode bridgeMethod(MethodNode handler, String name, List<Type> params, Type result) {
		return new MethodNode(Opcodes.ACC_PRIVATE | (handler.access & Opcodes.ACC_STATIC), name,
				Type.getMethodDescriptor(result, params.toArray(Type[]::new)), null, null);
	}

	/** The local variable slot of the bridge's parameter at {@code index}. */
	static int slotOf(MethodNode handler, List<Type> params, int index) {
		int slot = (handler.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
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
		bridge.maxLocals = slotOf(handler, List.of(Type.getArgumentTypes(bridge.desc)), Type.getArgumentTypes(bridge.desc).length);
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
		if (Annotations.find(handler, MixinPreflight.UNIQUE) == null) {
			if (handler.visibleAnnotations == null) handler.visibleAnnotations = new ArrayList<>();
			handler.visibleAnnotations.add(new AnnotationNode(MixinPreflight.UNIQUE));
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
	 * A handler for {@code injector} at {@code canonical} instead of {@code delegate}: it takes {@code canonical}'s
	 * parameters and the callback info, and calls {@code handler} with {@code delegate}'s (the same values, see
	 * {@link #forwardsItsParameters}) and the callback info. The injector annotation moves to it; {@code handler} stays a
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
}
