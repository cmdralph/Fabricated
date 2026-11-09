package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

/**
 * Where SpongePowered's Mixin stores the values a MixinExtras wrapper ({@code @WrapOperation}, {@code @WrapWithCondition})
 * takes from the call or field access it wraps (PRE-22). Mixin keeps the locals it stores one injector's values in for the
 * next injector into the same method ({@code Target.generateArgMap}). Where a long or a double goes into one of those
 * locals, its releases (0.8.5 to 0.8.7, Forge's) take the slot after it too without checking that it is one of them
 * ({@code Target.allocateArgMapLocal}; Fabric's Mixin checks, read in 0.16.4 and 0.17.4). A local allocated between two
 * injectors' values, as MixinExtras allocates a {@code @Share}d one after the first handler taking it is called, is then
 * overwritten, and the class fails verification as it loads ("Bad local variable type").
 * <p>
 * The wrappers of one mixin into one method are followed in the order Mixin applies them: MixinExtras applies them after
 * every other injector of the class, the {@code @WrapWithCondition}s before the {@code @WrapOperation}s, each in the mixin's
 * order, at each of its points in the method's order, the locals its handler's sugar needs following its calls. A method
 * into which the mixin has an injector that stores values earlier ({@code @Redirect}, {@code @ModifyArg},
 * {@code @ModifyArgs}, {@code @ModifyReceiver}, the first version of {@code @WrapWithCondition}), or a wrapper whose
 * points pre-flight can't place, isn't judged; nor are other mixins' wrappers into the method, which Mixin may apply
 * between.
 */
final class ArgumentLocals {
	private static final String WRAP_WITH_CONDITION = "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;";
	private static final String CANCELLABLE = "Lcom/llamalad7/mixinextras/sugar/Cancellable;";
	private static final String SUGAR_REFS = "Lcom/llamalad7/mixinextras/sugar/ref/";
	/** The injectors that store the values of the instruction they apply at before MixinExtras' wrappers do. */
	private static final Set<String> EARLIER_STORES = Set.of(MixinNames.REDIRECT, "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			MixinNames.MODIFY_ARGS, "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;", "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");

	private ArgumentLocals() {
	}

	/**
	 * A wrapper whose {@code value} (a long or a double the instruction at {@code node} of {@code method} takes) Mixin would
	 * store over the local of the mixin's shared local {@code share}.
	 */
	record Overlap(MethodNode handler, AnnotationNode injector, MethodNode method, AbstractInsnNode node, Type value, String share) {
	}

	private record Wrapper(MethodNode handler, AnnotationNode injector) {
	}

	/**
	 * The wrappers of {@code mixin} into {@code target} that Mixin would store a long or double over one of the mixin's
	 * shared locals for, leaving out of what follows each found and the handlers sharing a local with it, as SHARE-1
	 * leaves them out; the handlers in {@code excluded} (name and descriptor) are left out from the start.
	 */
	static List<Overlap> overlaps(ClassNode mixin, ClassNode target, Set<String> excluded, Context ctx) {
		Map<String, Set<String>> shares = MixinPreflight.shares(mixin);
		Set<String> out = new HashSet<>(excluded);
		List<Overlap> found = new ArrayList<>();
		for (Overlap overlap = firstOverlap(mixin, target, out, ctx); overlap != null; overlap = firstOverlap(mixin, target, out, ctx)) {
			found.add(overlap);
			Set<String> seen = new HashSet<>();
			ArrayDeque<String> next = new ArrayDeque<>(List.of(overlap.handler().name + overlap.handler().desc));
			while (!next.isEmpty()) {
				String handler = next.pop();
				if (!seen.add(handler)) continue;
				out.add(handler);
				Set<String> keys = shares.getOrDefault(handler, Set.of());
				shares.forEach((other, theirs) -> {
					if (!seen.contains(other) && !Collections.disjoint(keys, theirs)) next.add(other);
				});
			}
		}
		return found;
	}

	private static Overlap firstOverlap(ClassNode mixin, ClassNode target, Set<String> out, Context ctx) {
		List<Wrapper> wrappers = new ArrayList<>();
		for (String kind : List.of(WRAP_WITH_CONDITION, MixinNames.WRAP_OPERATION)) {
			for (MethodNode handler : mixin.methods) {
				if (out.contains(handler.name + handler.desc)) continue;
				AnnotationNode injector = Annotations.find(handler, kind);
				if (injector != null) wrappers.add(new Wrapper(handler, injector));
			}
		}
		if (wrappers.size() < 2) return null;
		for (MethodNode method : target.methods) {
			if (method.instructions == null || method.instructions.size() == 0) continue;
			List<Wrapper> into = wrappers.stream().filter(w -> PreflightAsm.selectedMethods(w.injector(), target, ctx).contains(method)).toList();
			if (into.size() < 2 || storesEarlier(mixin, out, target, method, ctx)) continue;
			Overlap overlap = simulate(mixin, into, target, method, ctx);
			if (overlap != null) return overlap;
		}
		return null;
	}

	/** Whether the mixin has an injector into {@code method} that stores values before MixinExtras' wrappers do, or may. */
	private static boolean storesEarlier(ClassNode mixin, Set<String> out, ClassNode target, MethodNode method, Context ctx) {
		for (MethodNode handler : mixin.methods) {
			if (out.contains(handler.name + handler.desc)) continue;
			for (AnnotationNode ann : Annotations.all(handler)) {
				if (!EARLIER_STORES.contains(ann.desc)) continue;
				List<MethodNode> selected = PreflightAsm.selectedMethods(ann, target, ctx);
				if (selected.isEmpty() || selected.contains(method)) return true;
			}
		}
		return false;
	}

	/** Mixin's allocation of the method's locals, wrapper by wrapper; the first overlap, or null (also where it can't be told). */
	private static Overlap simulate(ClassNode mixin, List<Wrapper> wrappers, ClassNode target, MethodNode method, Context ctx) {
		int next = method.maxLocals;
		List<Integer> reused = new ArrayList<>(); // Target.argMapVars
		Map<String, Integer> shared = new HashMap<>(); // a share's id: its local
		Map<Integer, String> shareNames = new HashMap<>();
		for (Wrapper w : wrappers) {
			List<AbstractInsnNode> nodes = nodes(w.injector(), method, ctx);
			if (nodes == null) return null;
			for (AbstractInsnNode node : nodes) {
				Type[] values = storedValues(node, target);
				if (values == null) return null;
				int index = 0;
				for (Type value : values) {
					int size = value.getSize();
					if (index >= reused.size()) {
						for (int o = 0; o < size; o++) reused.add(next + o);
						next += size;
					} else if (size > 1 && index + size > reused.size()) {
						int local = reused.get(index);
						int nextLocal = next++;
						if (nextLocal == local + 1) {
							reused.add(nextLocal);
						} else {
							reused.set(index, nextLocal);
							reused.add(next++);
						}
					} else if (size > 1 && reused.get(index + 1) != reused.get(index) + 1 && shareNames.containsKey(reused.get(index) + 1)) {
						return new Overlap(w.handler(), w.injector(), method, node, value, shareNames.get(reused.get(index) + 1));
					}
					index += size;
				}
			}
			// The handler's sugar at each of its calls (SugarInjector.transformHandlerCalls): a @Share's local where the
			// method's first handler takes it, a mutable @Local's reference and a @Cancellable's callback at each call.
			Type[] params = Type.getArgumentTypes(w.handler().desc);
			for (int n = 0; n < nodes.size(); n++) {
				for (int p = 0; p < params.length; p++) {
					for (AnnotationNode sugar : Annotations.ofParameter(w.handler(), p)) {
						if (sugar.desc.equals(MixinNames.SHARE) && Annotations.value(sugar, "value") instanceof String name) {
							String namespace = Annotations.value(sugar, "namespace") instanceof String given ? given : mixin.name.replace('/', '.');
							if (shared.putIfAbsent(namespace + ":" + name, next) == null) shareNames.put(next++, name);
						} else if (sugar.desc.equals(MixinNames.LOCAL) && params[p].getDescriptor().startsWith(SUGAR_REFS)
								|| sugar.desc.equals(CANCELLABLE)) {
							next++;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * The instructions a wrapper applies at in {@code method}, in its order, as Mixin finds them (each instruction once);
	 * null where a point isn't a call or field access pre-flight can place.
	 */
	private static List<AbstractInsnNode> nodes(AnnotationNode injector, MethodNode method, Context ctx) {
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		Set<Integer> indexes = new TreeSet<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") && !point.equals("FIELD") || !(Annotations.value(at, "target") instanceof String raw)) return null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			Range range = slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, method, ctx);
			List<Integer> picked = InjectionPoints.indexes(point, ctx.resolve(raw), ordinal, opcode, range, method);
			if (picked == null) return null;
			indexes.addAll(picked);
		}
		List<AbstractInsnNode> nodes = new ArrayList<>();
		for (int i : indexes) nodes.add(method.instructions.get(i));
		return nodes;
	}

	/** The values a wrapper stores of the instruction it wraps, as MixinExtras takes them; null for another instruction. */
	private static Type[] storedValues(AbstractInsnNode node, ClassNode target) {
		if (node instanceof MethodInsnNode call) {
			Type[] args = Type.getArgumentTypes(call.desc);
			if (call.name.equals("<init>") || call.getOpcode() == Opcodes.INVOKESTATIC) return args;
			Type[] values = new Type[args.length + 1];
			values[0] = Type.getObjectType(call.getOpcode() == Opcodes.INVOKESPECIAL ? target.name : call.owner);
			System.arraycopy(args, 0, values, 1, args.length);
			return values;
		}
		if (node instanceof FieldInsnNode field) {
			Type owner = Type.getObjectType(field.owner);
			Type type = Type.getType(field.desc);
			return switch (field.getOpcode()) {
				case Opcodes.GETSTATIC -> new Type[0];
				case Opcodes.PUTSTATIC -> new Type[] {type};
				case Opcodes.GETFIELD -> new Type[] {owner};
				default -> new Type[] {owner, type};
			};
		}
		return null;
	}

	/** The instruction an overlap is at, as reports name it. */
	static String describe(AbstractInsnNode node) {
		if (node instanceof MethodInsnNode call) return "the call to " + call.owner.replace('/', '.') + "." + call.name + call.desc;
		if (node instanceof FieldInsnNode field) return "the access to " + field.owner.replace('/', '.') + "." + field.name;
		return "an instruction";
	}
}
