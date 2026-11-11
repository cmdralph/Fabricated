package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.GamePackages;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bytecode helpers the checker and the repair families share.
 */
final class PreflightAsm {
	private PreflightAsm() {
	}

	/** The methods an injector's selectors pick in {@code target}, as Mixin does (the first match of each); none when one is fuzzy. */
	static List<MethodNode> selectedMethods(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<MethodNode> methods = new ArrayList<>();
		for (Object selector : selectors) {
			if (!(selector instanceof String raw)) return List.of();
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) return List.of();
			target.methods.stream().filter(m -> ref.matchesMethod(m.name, m.desc)).findFirst().ifPresent(methods::add);
		}
		return methods;
	}

	/**
	 * The methods Mixin scans for the points of an injector whose every selector is a bare wildcard ({@code *}, alone or
	 * with a method descriptor): each of the target's methods with code that matches, but a static one where the handler
	 * isn't static and one another mixin merged (a seam of Fabricated's, HUD-7), as Mixin's {@code TargetSelectors} takes
	 * them for a selector matching any number of methods. Null for an injector with another selector, or a descriptor no
	 * method has (Mixin then tries without it) (WILD-1).
	 */
	static List<MethodNode> wildcardMethods(AnnotationNode injector, MethodNode handler, ClassNode target, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.isEmpty()) return null;
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		List<MethodNode> methods = new ArrayList<>();
		for (Object selector : selectors) {
			if (!(selector instanceof String raw)) return null;
			String s = ctx.resolve(raw).replaceAll("\\s+", "");
			if (!s.startsWith("*") || s.length() > 1 && s.charAt(1) != '(') return null;
			String desc = s.length() > 1 ? s.substring(1) : null;
			if (desc != null && target.methods.stream().noneMatch(m -> m.desc.equals(desc))) return null;
			for (MethodNode m : target.methods) {
				if (desc != null && !desc.equals(m.desc) || m.instructions == null || m.instructions.size() == 0
						|| !handlerStatic && (m.access & Opcodes.ACC_STATIC) != 0 || Annotations.find(m, MixinNames.MIXIN_MERGED) != null
						|| methods.contains(m)) continue;
				methods.add(m);
			}
		}
		return methods;
	}

	/** The indices of {@code method}'s instructions a point ({@code INVOKE}, {@code FIELD} or {@code NEW}) matches, in order. */
	static List<Integer> matching(MethodNode method, String point, MemberRef ref, int opcode) {
		List<Integer> found = new ArrayList<>();
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if ((opcode < 0 || insn.getOpcode() == opcode) && InjectionPoints.matchesInsn(point, ref, insn)) found.add(i);
		}
		return found;
	}

	static boolean isGameClass(String internalName) {
		return GamePackages.isGame(internalName);
	}

	/** Whether {@code handler} is in a named injector group ({@code @Group}), whose members Mixin checks together. */
	static boolean grouped(MethodNode handler) {
		return Annotations.find(handler, MixinNames.GROUP) != null;
	}

	/**
	 * The name of the injector group {@code handler} is in, null when it is in none. Mixin puts a {@code @Group} without
	 * a name in the default group, {@code default} unless the mixin's config names another.
	 */
	static String group(MethodNode handler) {
		AnnotationNode group = Annotations.find(handler, MixinNames.GROUP);
		if (group == null) return null;
		return Annotations.value(group, "name") instanceof String name && !name.isEmpty() ? name : "default";
	}

	/**
	 * Whether {@code handler} is one of the alternatives of an injector group: another of {@code mixin}'s methods is in
	 * its group. Mixin checks each mixin's groups on their own (per target), so a group's only member is required as an
	 * injector alone is: Mixin needs at least one injection of every group (GROUP-2).
	 */
	static boolean hasAlternatives(ClassNode mixin, MethodNode handler) {
		String name = group(handler);
		return name != null && mixin.methods.stream().anyMatch(m -> m != handler && name.equals(group(m)));
	}

	static boolean isInjector(String desc) {
		return desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !desc.equals(MixinNames.AT) && !desc.equals(MixinNames.GROUP)
				&& !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Slice") && !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Desc")
				&& !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Constant") && !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Coerce")
				|| desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
	}

	/** The last element of a non-empty list. */
	static <T> T last(List<T> list) {
		return list.get(list.size() - 1);
	}

	/** The handler's parameter types without MixinExtras sugar parameters; null if a parameter uses {@code @Coerce}. */
	static List<Type> handlerArgs(MethodNode handler) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> args = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			boolean sugar = false;
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (a.desc.equals(MixinNames.COERCE)) return null;
				if (a.desc.startsWith("Lcom/llamalad7/mixinextras/sugar/")) sugar = true;
			}
			if (!sugar) args.add(types[i]);
		}
		return args;
	}

	static boolean isCallbackInfo(Type type) {
		return type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;")
				|| type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;");
	}

	/** Whether the mixin declares a method {@code ref} selects that Mixin merges into the target (not a shadow or handler). */
	static boolean addsMethod(ClassNode mixin, MemberRef ref) {
		for (MethodNode m : mixin.methods) {
			if (!ref.matchesMethod(m.name, m.desc) || m.name.startsWith("<") || Annotations.find(m, MixinNames.SHADOW) != null) continue;
			if (Annotations.all(m).stream().noneMatch(a -> isInjector(a.desc))) return true;
		}
		return false;
	}

	/**
	 * A new private {@code @Unique} field of {@code mixin}, which Mixin merges as the mixin's own: named {@code base}, or
	 * {@code base} and a number where the mixin has a field of that name.
	 */
	static FieldNode uniqueField(ClassNode mixin, String base, String desc) {
		String name = base;
		for (int i = 2; hasField(mixin, name); i++) name = base + i;
		FieldNode field = new FieldNode(Opcodes.ACC_PRIVATE, name, desc, null, null);
		field.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MixinNames.UNIQUE)));
		mixin.fields.add(field);
		return field;
	}

	private static boolean hasField(ClassNode mixin, String name) {
		return mixin.fields.stream().anyMatch(f -> f.name.equals(name));
	}

	/** A mixin's priority, as Mixin reads it: its {@code @Mixin}'s, else its config's {@code mixinPriority}, else Mixin's default. */
	static int priority(ClassNode mixin, MixinConfigFile config) {
		AnnotationNode ann = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (ann != null && Annotations.value(ann, "priority") instanceof Integer priority) return priority;
		return config.tree().get("mixinPriority") instanceof Number priority ? priority.intValue() : MixinNames.DEFAULT_PRIORITY;
	}

	static boolean declares(ClassNode target, String name, String desc) {
		return declared(target, name, desc) != null;
	}

	static MethodNode declared(ClassNode target, String name, String desc) {
		for (MethodNode m : target.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) return m;
		}
		return null;
	}

	/** Whether {@code method} is marked deprecated. */
	static boolean deprecated(MethodNode method) {
		return (method.access & Opcodes.ACC_DEPRECATED) != 0 || Annotations.find(method, "Ljava/lang/Deprecated;") != null;
	}

	static String simpleName(String desc) {
		String internal = desc.substring(1, desc.length() - 1);
		return internal.substring(internal.lastIndexOf('/') + 1);
	}
}
