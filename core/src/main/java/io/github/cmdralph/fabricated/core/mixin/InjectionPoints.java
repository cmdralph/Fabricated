package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

/**
 * Where an injector's {@code @At} points fall in a target method's bytecode, as Mixin and MixinExtras find them: the
 * instructions a point selects (within its slice), its locals, the constants of {@code @ModifyConstant}, and whether a
 * MixinExtras expression's definitions occur. Pre-flight's checks and repairs ask it; what it can't evaluate (custom
 * points, wildcards, slices bounded by anything but a member reference or {@code HEAD}) is {@link Verdict#UNKNOWN}.
 */
final class InjectionPoints {
	static final String DEFINITION = "Lcom/llamalad7/mixinextras/expression/Definition;";
	static final String DEFINITIONS = "Lcom/llamalad7/mixinextras/expression/Definitions;";
	/** A slice bound that can't be evaluated. */
	private static final int UNEVALUATED_INDEX = Integer.MIN_VALUE;

	private InjectionPoints() {
	}

	/** A {@code @Constant}'s value as the source would write it, or null when pre-flight can't judge it. */
	static String constantDescription(AnnotationNode constant) {
		if (Annotations.value(constant, "expandZeroConditions") != null) return null;
		for (String key : List.of("intValue", "floatValue", "longValue", "doubleValue", "stringValue", "classValue")) {
			Object value = Annotations.value(constant, key);
			if (value instanceof Type type) return type.getClassName() + ".class";
			if (value instanceof String string) return "\"" + string + "\"";
			if (value != null) return value.toString();
		}
		return Boolean.TRUE.equals(Annotations.value(constant, "nullValue")) ? "null" : null;
	}

	/** Whether a {@code @Constant} (with its ordinal) occurs in {@code method}, as Mixin matches constants. */
	static Verdict constantVerdict(AnnotationNode constant, MethodNode method) {
		Object wanted = null;
		for (String key : List.of("intValue", "floatValue", "longValue", "doubleValue", "stringValue", "classValue")) {
			Object value = Annotations.value(constant, key);
			if (value != null) wanted = value;
		}
		boolean nullValue = Boolean.TRUE.equals(Annotations.value(constant, "nullValue"));
		int count = 0;
		for (AbstractInsnNode insn : method.instructions) {
			Object value = constantOf(insn);
			if (nullValue ? insn.getOpcode() == Opcodes.ACONST_NULL : value != null && value.equals(wanted)) count++;
		}
		int ordinal = Annotations.value(constant, "ordinal") instanceof Integer n ? n : -1;
		if (count == 0) return Verdict.MISSING;
		if (Annotations.value(constant, "slice") != null) return Verdict.UNKNOWN;
		return ordinal >= count ? Verdict.MISSING : Verdict.FOUND;
	}

	/**
	 * Whether a {@code CONSTANT} point, with the {@code args} Mixin's {@code BeforeConstant} reads, picks an instruction in
	 * the slice: the constant (the ordinal-th with an ordinal), as Mixin matches it. A constant the platform's patch
	 * replaced leaves the injector nothing (CORPUS-46: Pehkui's reach hook modifies vanilla's 9.0 in
	 * {@code Player.attack}, which NeoForge 21.1 replaced with its interaction range). {@link Verdict#UNKNOWN} for what
	 * pre-flight doesn't judge: no value, {@code expandZeroConditions}, an argument it doesn't know, a value Mixin's
	 * strict parsing might read otherwise.
	 */
	static Verdict constantPointVerdict(Object args, int ordinal, Range range, MethodNode method) {
		if (!(args instanceof List<?> list) || method.instructions == null) return Verdict.UNKNOWN;
		Object wanted = null;
		boolean nullValue = false;
		for (Object entry : list) {
			if (!(entry instanceof String arg)) return Verdict.UNKNOWN;
			int eq = arg.indexOf('=');
			String key = eq < 0 ? arg : arg.substring(0, eq);
			String value = eq < 0 ? "" : arg.substring(eq + 1);
			// Mixin neither trims arguments nor parses numbers with spaces around them.
			boolean numeric = !value.isEmpty() && value.equals(value.strip());
			try {
				switch (key) {
					case "nullValue" -> nullValue = Boolean.parseBoolean(value);
					case "intValue" -> wanted = numeric ? Integer.valueOf(value) : null;
					case "longValue" -> wanted = numeric ? Long.valueOf(value) : null;
					case "floatValue" -> wanted = numeric && plainDecimal(value) ? Float.valueOf(value) : null;
					case "doubleValue" -> wanted = numeric && plainDecimal(value) ? Double.valueOf(value) : null;
					case "stringValue" -> wanted = value;
					case "classValue" -> wanted = value.isEmpty() ? null : Type.getObjectType(value.replace('.', '/'));
					case "log" -> {
					}
					default -> {
						return Verdict.UNKNOWN;
					}
				}
			} catch (NumberFormatException e) {
				return Verdict.UNKNOWN;
			}
			if (wanted == null && !nullValue && !key.equals("log")) return Verdict.UNKNOWN;
		}
		if (wanted == null && !nullValue) return Verdict.UNKNOWN;
		int countInMethod = 0;
		int countInRange = 0;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			Object value = constantOf(insn);
			boolean match = nullValue ? insn.getOpcode() == Opcodes.ACONST_NULL : value != null && value.equals(wanted);
			if (!match) continue;
			countInMethod++;
			if (i >= range.start() && i <= range.end()) countInRange++;
		}
		if (countInMethod == 0 || ordinal >= countInMethod) return Verdict.MISSING;
		if (range == Range.UNEVALUATED) return Verdict.UNKNOWN;
		return countInRange == 0 || ordinal >= countInRange ? Verdict.MISSING : Verdict.FOUND;
	}

	/** A decimal number, with an optional exponent and type suffix, as both Mixin's parser and Java's read alike. */
	private static boolean plainDecimal(String value) {
		return value.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?[fFdD]?");
	}

	/** The constant an instruction pushes (boxed as {@code @Constant} stores it), or null. */
	static Object constantOf(AbstractInsnNode insn) {
		int op = insn.getOpcode();
		if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
		if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) return (long) (op - Opcodes.LCONST_0);
		if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) return (float) (op - Opcodes.FCONST_0);
		if (op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) return (double) (op - Opcodes.DCONST_0);
		if (insn instanceof IntInsnNode push && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) return push.operand;
		if (insn instanceof LdcInsnNode ldc) return ldc.cst;
		return null;
	}

	/** Whether every injection point occurs in {@code method} (FOUND), one surely doesn't (MISSING), or it can't be told. */
	static Verdict pointsIn(List<AnnotationNode> ats, List<AnnotationNode> slices, MethodNode method, Context ctx) {
		Verdict result = Verdict.FOUND;
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (point.equals("MIXINEXTRAS:EXPRESSION")) return Verdict.UNKNOWN;
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer n ? n : -1;
			AnnotationNode slice = sliceFor(at, slices);
			Range range = slice == null ? Range.ALL : sliceRange(slice, method, ctx);
			Verdict v = point.equals("CONSTANT") ? constantPointVerdict(Annotations.value(at, "args"), ordinal, range, method)
					: evaluate(point, rawTarget == null ? null : ctx.resolve(rawTarget), ordinal, opcode, range, method);
			if (v == Verdict.MISSING) return Verdict.MISSING;
			if (v == Verdict.UNKNOWN) result = Verdict.UNKNOWN;
		}
		return result;
	}

	enum Verdict {
		FOUND, MISSING, UNKNOWN
	}

	/**
	 * Instruction index range an injection point is searched in (inclusive). {@link #ALL} is the whole method;
	 * {@link #UNEVALUATED} means a slice exists that pre-flight cannot compute, so only "the instruction occurs
	 * nowhere in the method" can be concluded.
	 */
	record Range(int start, int end) {
		static final Range ALL = new Range(0, Integer.MAX_VALUE);
		static final Range UNEVALUATED = new Range(-1, -1);
	}

	/** The {@code @Slice} an {@code @At} uses (matched by id, as Mixin does), or {@code null} for none. */
	static AnnotationNode sliceFor(AnnotationNode at, List<AnnotationNode> slices) {
		if (slices.isEmpty()) return null;
		String id = Annotations.value(at, "slice") instanceof String s ? s : "";
		for (AnnotationNode slice : slices) {
			String sliceId = Annotations.value(slice, "id") instanceof String s ? s : "";
			if (sliceId.equals(id)) return slice;
		}
		return null;
	}

	/**
	 * Mirrors Mixin's {@code MethodSlice.getSlice}: {@code from} defaults to the first instruction and falls back
	 * to it when its point is not found; {@code to} defaults to the last instruction and falls back to the
	 * start. Points select their first match (or the {@code ordinal}-th). Only member-reference points
	 * ({@code INVOKE}, {@code FIELD}, {@code NEW}) and {@code HEAD} are evaluated.
	 */
	static Range sliceRange(AnnotationNode slice, MethodNode method, Context ctx) {
		int max = method.instructions.size() - 1;
		int start = slicePoint(annotationOrNull(Annotations.value(slice, "from")), method, ctx, 0, 0);
		if (start == UNEVALUATED_INDEX) return Range.UNEVALUATED;
		int end = slicePoint(annotationOrNull(Annotations.value(slice, "to")), method, ctx, max, start);
		if (end == UNEVALUATED_INDEX || start > end) return Range.UNEVALUATED;
		return new Range(start, end);
	}

	static AnnotationNode annotationOrNull(Object value) {
		return value instanceof AnnotationNode a ? a : null;
	}

	static int slicePoint(AnnotationNode at, MethodNode method, Context ctx, int defaultValue, int failValue) {
		if (at == null) return defaultValue;
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (point.equals("HEAD")) return 0;
		if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") && !point.equals("FIELD") && !point.equals("NEW")) return UNEVALUATED_INDEX;
		String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
		if (rawTarget == null || rawTarget.contains(":LAST") || rawTarget.contains(":ONE") || rawTarget.contains(":FIRST")) return UNEVALUATED_INDEX;
		MemberRef ref = MemberRef.parse(ctx.resolve(rawTarget));
		if (ref.fuzzy()) return UNEVALUATED_INDEX;
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		int seen = 0;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (opcode >= 0 && insn.getOpcode() != opcode) continue;
			if (!matchesInsn(point, ref, insn)) continue;
			if (ordinal < 0 || seen == ordinal) return i;
			seen++;
		}
		return failValue;
	}

	static Verdict evaluate(String point, String target, int ordinal, int opcode, Range range, MethodNode method) {
		if (point.equals("LOAD") || point.equals("STORE")) {
			// Not judged precisely (the local's type and index), but a slice without any local variable load or
			// store (as when its end point is gone and Mixin cuts it down to one instruction) can't match.
			if (range == Range.UNEVALUATED || method.instructions == null) return Verdict.UNKNOWN;
			boolean load = point.equals("LOAD");
			int last = Math.min(range.end(), method.instructions.size() - 1);
			for (int i = Math.max(range.start(), 0); i <= last; i++) {
				int op = method.instructions.get(i).getOpcode();
				if (load ? op >= Opcodes.ILOAD && op <= Opcodes.ALOAD : op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) return Verdict.UNKNOWN;
			}
			return Verdict.MISSING;
		}
		int n = count(point, target, ordinal, opcode, range, method);
		return n == 0 ? Verdict.MISSING : n > 0 || n == PRESENT ? Verdict.FOUND : Verdict.UNKNOWN;
	}

	/**
	 * Whether a {@code LOAD} or {@code STORE} point of a local of {@code type} in {@code slot} picks an instruction in the
	 * slice: a load or store of that type into the slot (an int's increment counts as a store), the ordinal-th of them
	 * with an ordinal, as Mixin's {@code BeforeLoadLocal} and {@code AfterStoreLocal} match them.
	 */
	static boolean accessesLocal(boolean load, Type type, int slot, int ordinal, Range range, MethodNode method) {
		int opcode = type.getOpcode(load ? Opcodes.ILOAD : Opcodes.ISTORE);
		int seen = 0;
		int last = Math.min(range.end(), method.instructions.size() - 1);
		for (int i = Math.max(range.start(), 0); i <= last; i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			boolean match = insn instanceof VarInsnNode var && var.getOpcode() == opcode && var.var == slot
					|| !load && opcode == Opcodes.ISTORE && insn instanceof IincInsnNode inc && inc.var == slot;
			if (match && (ordinal < 0 || seen++ == ordinal)) return true;
		}
		return false;
	}

	/**
	 * Whether a {@code LOAD} or {@code STORE} point of a local of {@code type} whose slot isn't known can pick an
	 * instruction in the slice: Mixin's {@code BeforeLoadLocal} and {@code AfterStoreLocal} match only the loads or stores
	 * with that type's opcode (or the point's {@code opcode}), so a slice with no more of them than the point's ordinal
	 * can't match (PRE-17: Puzzles Lib's break speed hook modifies a float where {@code Player.getDestroySpeed} loads it,
	 * a method Forge 47 reduced to a call of {@code getDigSpeed}, which has no float). An int's increment counts as a store.
	 */
	static boolean accessesAnyLocal(boolean load, Type type, int opcode, int ordinal, Range range, MethodNode method) {
		int wanted = opcode >= 0 ? opcode : type.getOpcode(load ? Opcodes.ILOAD : Opcodes.ISTORE);
		int seen = 0;
		int last = Math.min(range.end(), method.instructions.size() - 1);
		for (int i = Math.max(range.start(), 0); i <= last; i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (insn.getOpcode() == wanted || !load && wanted == Opcodes.ISTORE && insn instanceof IincInsnNode) seen++;
		}
		return seen > Math.max(ordinal, 0);
	}

	/** {@link #count}'s answer for a point the method has where the injections it makes there aren't counted. */
	static final int PRESENT = -2;

	/**
	 * How many injections Mixin makes at the point in {@code method} (each instruction it matches in the slice, or one
	 * with an ordinal); {@link #PRESENT} where the method has it but the slice couldn't be evaluated, -1 where the point
	 * isn't judged.
	 */
	static int count(String point, String target, int ordinal, int opcode, Range range, MethodNode method) {
		switch (point) {
			case "HEAD", "TAIL" -> {
				return method.instructions == null || method.instructions.size() == 0 ? 0 : 1;
			}
			case "RETURN" -> {
				// Mixin's BeforeReturn: each return instruction, or with an ordinal, only the ordinal-th in the slice.
				if (method.instructions == null || method.instructions.size() == 0) return 0;
				int countInMethod = 0;
				int countInRange = 0;
				for (int i = 0; i < method.instructions.size(); i++) {
					int op = method.instructions.get(i).getOpcode();
					if (op < Opcodes.IRETURN || op > Opcodes.RETURN) continue;
					countInMethod++;
					if (i >= range.start() && i <= range.end()) countInRange++;
				}
				if (ordinal < 0) return range != Range.UNEVALUATED && countInRange > 0 ? countInRange : PRESENT;
				if (countInMethod <= ordinal) return 0;
				if (range == Range.UNEVALUATED) return PRESENT;
				return countInRange <= ordinal ? 0 : 1;
			}
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW" -> {
				if (target == null) return -1;
				MemberRef ref = MemberRef.parse(target);
				if (ref.fuzzy()) return -1;
				int countInMethod = 0;
				int countInRange = 0;
				for (int i = 0; i < method.instructions.size(); i++) {
					AbstractInsnNode insn = method.instructions.get(i);
					if (opcode >= 0 && insn.getOpcode() != opcode) continue;
					if (!matchesInsn(point, ref, insn)) continue;
					countInMethod++;
					if (i >= range.start() && i <= range.end()) countInRange++;
				}
				if (countInMethod == 0) return 0;
				if (range == Range.UNEVALUATED) return PRESENT;
				if (countInRange == 0) return 0;
				if (ordinal >= 0) return countInRange <= ordinal ? 0 : 1;
				return countInRange;
			}
			default -> {
				return -1;
			}
		}
	}

	/**
	 * The indexes of the instructions a call, field or construction point picks in {@code method} (each one it matches in
	 * the slice, or the ordinal-th), or null where they aren't found here: another point, a fuzzy target, a slice that
	 * couldn't be evaluated.
	 */
	static List<Integer> indexes(String point, String target, int ordinal, int opcode, Range range, MethodNode method) {
		if (target == null || range == Range.UNEVALUATED || method.instructions == null) return null;
		if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") && !point.equals("INVOKE_STRING") && !point.equals("FIELD") && !point.equals("NEW")) {
			return null;
		}
		MemberRef ref = MemberRef.parse(target);
		if (ref.fuzzy()) return null;
		List<Integer> picked = new ArrayList<>();
		int seen = 0;
		for (int i = Math.max(range.start(), 0); i <= Math.min(range.end(), method.instructions.size() - 1); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (opcode >= 0 && insn.getOpcode() != opcode) continue;
			if (!matchesInsn(point, ref, insn)) continue;
			if (ordinal < 0 || seen == ordinal) picked.add(i);
			seen++;
		}
		return picked;
	}

	/**
	 * A {@code @WrapOperation} handler takes the wrapped call's receiver (unless static) and arguments, then the
	 * {@code Operation}. Returns why that does not fit any matching call or constructor, or null when it does (or
	 * cannot be judged).
	 */
	static String wrappedArityMismatch(MethodNode handler, String point, String target, List<MethodNode> methods) {
		Type[] args = Type.getArgumentTypes(handler.desc);
		int taken = -1;
		for (int i = 0; i < args.length; i++) {
			if (args[i].getDescriptor().equals(MixinNames.OPERATION)) {
				taken = i;
				break;
			}
		}
		MemberRef ref = MemberRef.parse(target);
		if (taken < 0 || ref.fuzzy()) return null;
		Set<Integer> passed = new java.util.TreeSet<>();
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				switch (point) {
					case "INVOKE", "INVOKE_ASSIGN" -> {
						if (matchesInsn(point, ref, insn)) {
							passed.add(Type.getArgumentTypes(call.desc).length + (call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1));
						}
					}
					case "NEW" -> {
						String cls = newTarget(ref);
						if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>") && cls != null && cls.equals(call.owner)
								&& (ref.methodDescOr(null) == null || matchesInsn(point, ref, insn))) {
							passed.add(Type.getArgumentTypes(call.desc).length);
						}
					}
					default -> {
						return null;
					}
				}
			}
		}
		if (passed.isEmpty() || passed.contains(taken)) return null;
		return "takes " + taken + " value(s) before its Operation, but the wrapped " + (point.equals("NEW") ? "constructor" : "call") + " passes " + passed;
	}

	/**
	 * A MixinExtras expression can only match when what each of the handler's {@code @Definition}s names (a type, a
	 * method, a field or a local variable by its name) occurs in the target method. The expression itself is not
	 * evaluated.
	 */
	static Verdict evaluateExpression(MethodNode handler, MethodNode method, Context ctx) {
		List<AnnotationNode> definitions = definitions(handler);
		if (definitions.isEmpty() || method.instructions == null) return Verdict.UNKNOWN;
		for (AnnotationNode definition : definitions) {
			if (!definitionOccurs(definition, method, ctx)) return Verdict.MISSING;
		}
		return Verdict.FOUND;
	}

	/** The MixinExtras {@code @Definition}s of a handler, alone or in its {@code @Definitions}. */
	static List<AnnotationNode> definitions(MethodNode handler) {
		List<AnnotationNode> definitions = new ArrayList<>();
		for (AnnotationNode ann : Annotations.all(handler)) {
			if (ann.desc.equals(DEFINITION)) definitions.add(ann);
			else if (ann.desc.equals(DEFINITIONS)) definitions.addAll(Annotations.list(Annotations.value(ann, "value")));
		}
		return definitions;
	}

	/**
	 * The {@code @Local}s a handler's {@code @Definition}s name local variables by. MixinExtras finds each with Mixin's
	 * local variable discriminator, as it finds a handler's {@code @Local} parameters.
	 */
	static List<AnnotationNode> definitionLocals(MethodNode handler) {
		List<AnnotationNode> locals = new ArrayList<>();
		for (AnnotationNode definition : definitions(handler)) locals.addAll(Annotations.list(Annotations.value(definition, "local")));
		return locals;
	}

	static boolean definitionOccurs(AnnotationNode definition, MethodNode method, Context ctx) {
		List<AbstractInsnNode> insns = List.of(method.instructions.toArray());
		boolean judged = false;
		for (AnnotationNode local : Annotations.list(Annotations.value(definition, "local"))) {
			Boolean declared = namedLocalDeclared(local, method);
			if (declared == null || declared) return true;
			judged = true;
		}
		if (Annotations.value(definition, "type") instanceof List<?> types) {
			for (Object o : types) {
				if (!(o instanceof Type type) || (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY)) return true;
				judged = true;
				if (insns.stream().anyMatch(insn -> references(insn, type))) return true;
			}
		}
		for (String kind : List.of("method", "field")) {
			if (!(Annotations.value(definition, kind) instanceof List<?> refs)) continue;
			for (Object o : refs) {
				if (!(o instanceof String raw)) continue;
				MemberRef ref = MemberRef.parse(ctx.resolve(raw));
				if (ref.fuzzy() || ref.name() == null) return true;
				judged = true;
				String point = kind.equals("method") ? "INVOKE" : "FIELD";
				if (insns.stream().anyMatch(insn -> matchesInsn(point, ref, insn) || point.equals("INVOKE") && referencesHandle(insn, ref))) return true;
			}
		}
		return !judged;
	}

	/**
	 * Whether the method's local variable table declares a local an expression's {@code @Local} definition names, by its
	 * name and of its type, as Mixin's discriminator matches it (PRE-21); null where that isn't judged: an argument
	 * ({@code argsOnly}, which the running Mixin may not know by its name), a local taken by its index or ordinal, or a
	 * method without the table.
	 */
	static Boolean namedLocalDeclared(AnnotationNode local, MethodNode method) {
		if (Boolean.TRUE.equals(Annotations.value(local, "argsOnly")) || Annotations.value(local, "index") != null
				|| Annotations.value(local, "ordinal") != null || !(Annotations.value(local, "type") instanceof Type type)
				|| !(Annotations.value(local, "name") instanceof List<?> names) || names.isEmpty()
				|| method.localVariables == null || method.localVariables.isEmpty()) return null;
		return method.localVariables.stream().anyMatch(lv -> names.contains(lv.name) && lv.desc.equals(type.getDescriptor()));
	}

	/**
	 * Whether {@code insn} is a method reference or lambda ({@code invokedynamic}) to a method named and typed as
	 * {@code ref} selects; its owner may be a subclass (as {@code this::canHitEntity} names it), so it isn't compared.
	 */
	static boolean referencesHandle(AbstractInsnNode insn, MemberRef ref) {
		if (!(insn instanceof InvokeDynamicInsnNode indy)) return false;
		for (Object arg : indy.bsmArgs) {
			if (arg instanceof Handle h && ref.matchesMethod(h.getName(), h.getDesc())) return true;
		}
		return false;
	}

	/** Whether {@code insn} uses {@code type}: creates, casts or checks it, calls or reads a member of it, or passes it. */
	static boolean references(AbstractInsnNode insn, Type type) {
		String internal = type.getInternalName();
		String desc = type.getDescriptor();
		if (insn instanceof TypeInsnNode t) return t.desc.equals(internal);
		if (insn instanceof MethodInsnNode m) return m.owner.equals(internal) || m.desc.contains(desc);
		if (insn instanceof FieldInsnNode f) return f.owner.equals(internal) || f.desc.contains(desc);
		if (insn instanceof InvokeDynamicInsnNode d) return d.desc.contains(desc);
		if (insn instanceof LdcInsnNode l) return type.equals(l.cst);
		return false;
	}

	static boolean matchesInsn(String point, MemberRef ref, AbstractInsnNode insn) {
		switch (point) {
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING" -> {
				if (!(insn instanceof MethodInsnNode m)) return false;
				return (ref.owner() == null || ref.owner().equals(m.owner)) && ref.matchesMethod(m.name, m.desc);
			}
			case "FIELD" -> {
				if (!(insn instanceof FieldInsnNode f)) return false;
				return (ref.owner() == null || ref.owner().equals(f.owner))
						&& ref.name() != null && ref.name().equals(f.name)
						&& (ref.desc() == null || ref.desc().equals(f.desc));
			}
			case "NEW" -> {
				String cls = newTarget(ref);
				String ctorDesc = ref.methodDescOr(null);
				if (ctorDesc != null) {
					// A constructor descriptor selects one constructor, as Mixin's BeforeNew does: count the
					// INVOKESPECIAL <init> with those arguments rather than any NEW of the class.
					String initDesc = ctorDesc.substring(0, ctorDesc.indexOf(')') + 1) + "V";
					return insn instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESPECIAL
							&& m.name.equals("<init>") && m.desc.equals(initDesc) && (cls == null || cls.equals(m.owner));
				}
				if (!(insn instanceof TypeInsnNode t) || t.getOpcode() != Opcodes.NEW) return false;
				return cls == null || cls.equals(t.desc);
			}
			default -> {
				return false;
			}
		}
	}

	/** Class created by a NEW selector: {@code Lpkg/C;}, {@code pkg/C} or a constructor descriptor. */
	static String newTarget(MemberRef ref) {
		if (ref.owner() != null && ref.name() == null) return ref.owner();
		String ctorDesc = ref.methodDescOr(null);
		if (ctorDesc != null) {
			Type ret = Type.getReturnType(ctorDesc);
			return ret.getSort() == Type.OBJECT ? ret.getInternalName() : null;
		}
		if (ref.name() != null && ref.owner() == null) return ref.name();
		return ref.owner();
	}
}
