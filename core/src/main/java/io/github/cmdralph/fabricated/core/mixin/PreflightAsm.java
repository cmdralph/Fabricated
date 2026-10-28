package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
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
		return MixinNames.GAME_PREFIXES.stream().anyMatch(internalName::startsWith);
	}

	/** Whether {@code handler} is in a named injector group ({@code @Group}), whose members Mixin checks together. */
	static boolean grouped(MethodNode handler) {
		return Annotations.find(handler, MixinNames.GROUP) != null;
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

	static boolean declares(ClassNode target, String name, String desc) {
		return declared(target, name, desc) != null;
	}

	static MethodNode declared(ClassNode target, String name, String desc) {
		for (MethodNode m : target.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) return m;
		}
		return null;
	}

	static String simpleName(String desc) {
		String internal = desc.substring(1, desc.length() - 1);
		return internal.substring(internal.lastIndexOf('/') + 1);
	}
}
