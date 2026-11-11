package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

/**
 * An {@code @Inject} capturing locals ({@code locals}) that Mixin skips at each of its points (SEAM-2): the locals it
 * finds there aren't those the handler takes, so it injects nothing, as {@code CAPTURE_FAILSOFT} has it (the soften
 * step makes every capture skip itself so). Its handler never runs, and an injector reading what it stores in the
 * mixin's fields would read nothing: Carpet's {@code @Redirect}s of the resolver's stickiness checks, which SEAM-1 lets
 * apply, read the positions its capture at {@code addBlockLine}'s second {@code getBlockState} keeps, where NeoForge
 * 21.1 to 26.1.2 and Forge 47 declare their {@code oldState} local before vanilla's position. Pre-flight notes such a
 * capture and leaves out only the injectors reading what it alone stores ({@link #unsupplied}), alone, where STATE-1
 * could ({@link MixinPreflight.Context#leaveOutReadersOfSkippedCaptures}); an alternative capture in the platform's order
 * storing the same fields keeps them all.
 * <p>
 * Mixin compares the handler's descriptor with the target's parameters, the callback info and, from the first slot past
 * the parameters, as many of the locals it finds as the handler takes, slots holding none skipped
 * ({@code CallbackInjector.Callback.checkDescriptor}, {@code Target.getCallbackDescriptor}; the same in SpongePowered's
 * 0.8.5 and Fabric's 0.16 and 0.17), exactly but for a {@code @Coerce} parameter. It finds them walking the method's
 * instructions up to the point ({@code Locals.getLocalsAt}, and Fabric's {@code getLocalsAt092} for mods of its
 * behaviour 0.9.2): a store gives its slot, from the next instruction on, the entry of the local variable table covering
 * that instruction there, a load the one covering the load, the last such entry where several do, and any stack map
 * frame may give each slot another (by the frame's locals and Mixin's count of them, which differs between versions and
 * frame encodings). So a slot's local is known where the last thing to give it one is a store or load after the last
 * frame before the point, with a table entry covering that instruction for the slot; before the first frame the slots
 * past the parameters hold none. A slot no entry covers there gets one from a table Mixin generates, or another entry
 * for the slot, and isn't known. A capture is judged only where every slot up to the last of the locals the handler takes
 * is known, at every point, in every target: one unshifted {@code @At} at a call, a field access or the method's returns,
 * no slice (not a construction: Mixin's versions differ in where they inject at a {@code NEW} and how they count its
 * ordinal), the targets with a local variable table, the handler without {@code @Coerce} parameters or
 * another method of its name (a {@code @Surrogate} Mixin would take instead).
 */
final class SkippedCaptures {
	private static final Set<String> CAPTURING = Set.of("CAPTURE_FAILSOFT", "CAPTURE_FAILHARD", "CAPTURE_FAILEXCEPTION");

	private SkippedCaptures() {
	}

	/**
	 * A capture Mixin skips at each of its points.
	 *
	 * @param method   a target method it is skipped in
	 * @param captured the locals the handler takes
	 * @param found    the locals Mixin finds at a point in that method, as many
	 */
	record Skipped(MethodNode method, List<Type> captured, List<Type> found) {
		String describe(String injectorName, MethodNode handler, Context ctx) {
			return injectorName + " " + handler.name + " captures the locals " + types(captured) + " in " + method.name + types(List.of(Type.getArgumentTypes(method.desc)))
					+ ", where Mixin finds " + types(found) + " in " + ctx.platform() + "'s code at each of its points";
		}
	}

	/**
	 * The capture {@code inject} makes, where Mixin skips it at each of its points in {@code targets}; null where it applies
	 * somewhere, or where that can't be told.
	 *
	 * @param framed each target method with the frames its bytes have, as Mixin walks it (null when unknown)
	 */
	static Skipped skipped(ClassNode mixin, MethodNode handler, AnnotationNode inject, List<MethodNode> targets, BiFunction<String, MethodNode, MethodNode> framed,
			String owner, Context ctx) {
		if (!inject.desc.equals(MixinNames.INJECT) || !(Annotations.value(inject, "locals") instanceof String[] capture) || capture.length != 2
				|| !CAPTURING.contains(capture[1]) || targets.isEmpty() || Annotations.value(inject, "slice") != null) return null;
		if (mixin.methods.stream().anyMatch(m -> m != handler && m.name.equals(handler.name))) return null;
		for (int i = 0; i < Type.getArgumentTypes(handler.desc).length; i++) {
			if (Annotations.ofParameter(handler, i).stream().anyMatch(a -> a.desc.equals(MixinNames.COERCE))) return null;
		}
		List<Type> args = PreflightAsm.handlerArgs(handler);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(inject, "at"));
		if (args == null || ats.size() != 1) return null;
		Skipped skipped = null;
		for (MethodNode target : targets) {
			List<Type> own = List.of(Type.getArgumentTypes(target.desc));
			if (args.size() <= own.size() + 1 || !args.subList(0, own.size()).equals(own) || !PreflightAsm.isCallbackInfo(args.get(own.size()))) return null;
			List<Type> captured = args.subList(own.size() + 1, args.size());
			MethodNode m = framed.apply(owner, target);
			if (m == null || m.instructions == null || m.localVariables == null || m.localVariables.isEmpty()) return null;
			List<Integer> positions = positions(ats.get(0), m, ctx);
			if (positions == null || positions.isEmpty()) return null;
			for (int position : positions) {
				List<Type> found = foundAt(m, position, captured.size());
				if (found == null || found.equals(captured)) return null;
				if (skipped == null) skipped = new Skipped(target, List.copyOf(captured), found);
			}
		}
		return skipped;
	}

	/**
	 * The instructions of {@code method} an {@code @At} injects at, by index: for a call or field access, each it matches,
	 * or its ordinal-th; for {@code RETURN} each return, or its ordinal-th; for {@code TAIL} the last. Null for any other
	 * point (a construction among them: Mixin 0.8.7, 0.16 and 0.17 inject at the {@code NEW} and count its ordinal over
	 * every {@code NEW} of the class, 0.8.5 otherwise), or a shifted one.
	 */
	private static List<Integer> positions(AnnotationNode at, MethodNode method, Context ctx) {
		if (Annotations.value(at, "shift") instanceof String[] shift && shift.length == 2 && !shift[1].equals("NONE")
				|| Annotations.value(at, "by") instanceof Integer by && by != 0) return null;
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		List<Integer> found;
		if (point.equals("RETURN") || point.equals("TAIL")) {
			found = new ArrayList<>();
			for (int i = 0; i < method.instructions.size(); i++) {
				int op = method.instructions.get(i).getOpcode();
				if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) found.add(i);
			}
			if (point.equals("TAIL")) return found.isEmpty() ? found : List.of(found.get(found.size() - 1));
		} else if ((point.equals("INVOKE") || point.equals("FIELD")) && Annotations.value(at, "target") instanceof String raw) {
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy()) return null;
			found = PreflightAsm.matching(method, point, ref, Annotations.value(at, "opcode") instanceof Integer o ? o : -1);
		} else {
			return null;
		}
		if (ordinal >= 0) return ordinal < found.size() ? List.of(found.get(ordinal)) : List.of();
		return found;
	}

	/**
	 * The first {@code count} locals Mixin finds at the instruction at {@code point}, from the first slot past the
	 * parameters, slots holding none skipped; null where a slot before the last of them isn't known (see the class).
	 */
	static List<Type> foundAt(MethodNode method, int point, int count) {
		int first = OverloadRepairs.firstLocalSlot(method);
		int size = Math.max(method.maxLocals, first);
		String[] local = new String[size];
		boolean[] known = new boolean[size];
		Arrays.fill(known, true); // before the first frame, only this and the parameters hold locals
		VarInsnNode store = null;
		for (int i = 0; i <= point && i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (store != null) {
				give(method, i, store.var, local, known);
				store = null;
			}
			if (insn instanceof FrameNode) {
				Arrays.fill(known, false);
			} else if (insn instanceof VarInsnNode var && var.var < size) {
				if (var.getOpcode() >= Opcodes.ILOAD && var.getOpcode() <= Opcodes.ALOAD) give(method, i, var.var, local, known);
				else store = var;
			}
		}
		List<Type> found = new ArrayList<>();
		for (int slot = first; slot < size && found.size() < count; slot++) {
			if (!known[slot]) return null;
			if (local[slot] != null) found.add(Type.getType(local[slot]));
		}
		return found.size() == count ? found : null;
	}

	/**
	 * Gives a slot the local Mixin does at the instruction at {@code index}: the last entry of the table covering it for
	 * the slot. A long or double stored empties the slot after it in Mixin's main walk, but not in its 0.9.2 walk, nor
	 * loaded: that slot isn't known then.
	 */
	private static void give(MethodNode method, int index, int slot, String[] local, boolean[] known) {
		String covering = null;
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.index == slot && method.instructions.indexOf(lv.start) <= index && index < method.instructions.indexOf(lv.end)) covering = lv.desc;
		}
		known[slot] = covering != null;
		local[slot] = covering;
		if (covering != null && Type.getType(covering).getSize() == 2 && slot + 1 < known.length) known[slot + 1] = false;
	}

	/** How a skipped capture's finding ends, which {@link MixinPreflight.Context#leaveOutReadersOfSkippedCaptures} finds it by. */
	static final String SKIPPED = ": Mixin skips it there (SEAM-2)";

	/**
	 * What an injector reads that no injector still applying stores.
	 *
	 * @param fields  the mixin's own fields it reads so (names)
	 * @param writers the injectors storing them, none of which applies (handlers' names and descriptors)
	 */
	record Unsupplied(Set<String> fields, Set<String> writers) {
	}

	/**
	 * The injectors of {@code mixin} left reading what a capture Mixin skips ({@code skipped}) stores in the mixin's own
	 * fields, with no injector that applies storing it, by handler (name and descriptor), and in turn those left reading
	 * what those store so: a field one of them reads goes unstored where one of those stores it (a {@code PUTFIELD} or
	 * {@code PUTSTATIC}, itself or through the mixin's methods it calls), every injector storing it is among them or
	 * doesn't apply ({@code gone}, which holds {@code skipped}: those left out too) and the mixin's initialisers store none
	 * of it. One storing it itself, or another that applies storing it (an alternative capture in the platform's order),
	 * keeps it stored: nothing goes then. What only injectors left out for other reasons store is STATE-1's to judge. Null
	 * where that can't be told: a class nested in the mixin, an injector without code.
	 */
	static Map<String, Unsupplied> unsupplied(ClassNode mixin, Set<String> skipped, Set<String> gone) {
		for (InnerClassNode inner : mixin.innerClasses) {
			if (inner.name.startsWith(mixin.name + "$")) return null;
		}
		Set<String> own = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, MixinNames.SHADOW) == null) own.add(field.name + ":" + field.desc);
		}
		Map<String, MethodNode> methods = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, MixinNames.SHADOW) == null) methods.put(method.name + method.desc, method);
		}
		Map<String, Set<String>> reads = new LinkedHashMap<>();
		Map<String, Set<String>> writes = new LinkedHashMap<>();
		Set<String> initialised = new HashSet<>();
		for (MethodNode method : mixin.methods) {
			String key = method.name + method.desc;
			if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
				access(mixin, method, own, methods, new HashSet<>(), initialised, new HashSet<>());
				continue;
			}
			if (!methods.containsKey(key) || Annotations.all(method).stream().noneMatch(a -> PreflightAsm.isInjector(a.desc))) continue;
			if (method.instructions == null || method.instructions.size() == 0) return null;
			Set<String> read = new HashSet<>();
			Set<String> written = new HashSet<>();
			access(mixin, method, own, methods, read, written, new HashSet<>());
			reads.put(key, read);
			writes.put(key, written);
		}
		Set<String> without = new HashSet<>(gone);
		Map<String, Unsupplied> left = new LinkedHashMap<>();
		for (boolean changed = true; changed; ) {
			changed = false;
			for (Map.Entry<String, Set<String>> reader : reads.entrySet()) {
				if (without.contains(reader.getKey())) continue;
				Set<String> fields = new TreeSet<>();
				Set<String> writers = new TreeSet<>();
				for (String field : reader.getValue()) {
					if (initialised.contains(field)) continue;
					Set<String> by = new TreeSet<>();
					boolean stored = false;
					boolean fromSkipped = false;
					for (Map.Entry<String, Set<String>> writer : writes.entrySet()) {
						if (!writer.getValue().contains(field)) continue;
						if (without.contains(writer.getKey())) by.add(writer.getKey());
						else stored = true;
						fromSkipped |= skipped.contains(writer.getKey()) || left.containsKey(writer.getKey());
					}
					if (stored || !fromSkipped) continue;
					fields.add(field.substring(0, field.indexOf(':')));
					writers.addAll(by);
				}
				if (fields.isEmpty()) continue;
				left.put(reader.getKey(), new Unsupplied(fields, writers));
				without.add(reader.getKey());
				changed = true;
			}
		}
		return left;
	}

	/** The mixin's own fields {@code method} reads and stores, itself or through the mixin's own methods it calls or refers to. */
	private static void access(ClassNode mixin, MethodNode method, Set<String> own, Map<String, MethodNode> methods, Set<String> reads, Set<String> writes,
			Set<MethodNode> seen) {
		if (!seen.add(method) || method.instructions == null) return;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode field && field.owner.equals(mixin.name) && own.contains(field.name + ":" + field.desc)) {
				boolean store = field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC;
				(store ? writes : reads).add(field.name + ":" + field.desc);
			} else if (insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && methods.get(call.name + call.desc) != null) {
				access(mixin, methods.get(call.name + call.desc), own, methods, reads, writes, seen);
			} else if (insn instanceof InvokeDynamicInsnNode indy) {
				for (Object arg : indy.bsmArgs) {
					if (!(arg instanceof Handle handle) || !handle.getOwner().equals(mixin.name)) continue;
					if (own.contains(handle.getName() + ":" + handle.getDesc())) {
						reads.add(handle.getName() + ":" + handle.getDesc());
						writes.add(handle.getName() + ":" + handle.getDesc());
					}
					MethodNode callee = methods.get(handle.getName() + handle.getDesc());
					if (callee != null) access(mixin, callee, own, methods, reads, writes, seen);
				}
			}
		}
	}

	/** Types as a parameter list of their simple names: {@code (BlockState, int, BlockPos)}. */
	static String types(List<Type> types) {
		return types.stream().map(t -> {
			String name = t.getClassName();
			return name.substring(name.lastIndexOf('.') + 1);
		}).collect(Collectors.joining(", ", "(", ")"));
	}
}
