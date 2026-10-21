package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * Turns a Fabric enum mixin into one of NeoForge's extensible enums that its enum extension file can't carry (ENUM-5:
 * a name NeoForge's check refuses, a constant with a body of its own) into an ordinary mixin adding the constants as
 * mods' own NeoForge builds do (Wilder Wild's adds its grass colour so). FML refuses enum mixins into those enums
 * ({@code FMLMixinFeatureValidator}, ENUM-4), not other mixins: the generated one injects into the enum's class
 * initializer right after it sets its values array, makes each constant with one of the enum's constructors (through a
 * constructor invoker) at the next ordinals, appends them to the array, then runs the rest of the mixin's class
 * initializer with the constants in hand.
 * <p>A constant with a body overriding the one method the enum forwards to a delegate its other constructor takes
 * (NeoForge's way of making such an enum extensible, as {@code GrassColorModifier}'s {@code ColorModifier}) is made with
 * that constructor and a delegate class generated next to the mod's mixins, running the body. Only code that runs
 * straight through and uses nothing of the mixin moves (the arguments, the bodies, the rest of the class initializer),
 * and none when the mod's classes read a constant as a field of the enum, which the mixin can't add.
 */
final class EnumValuesMixins {
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String FINAL = "Lorg/spongepowered/asm/mixin/Final;";
	private static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String SHIFT = "Lorg/spongepowered/asm/mixin/injection/At$Shift;";
	private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	/** javac's name for an enum's array of its constants. */
	private static final String VALUES = "$VALUES";
	private static final Set<String> ENUM_METHODS = Set.of("values", "valueOf", "$values", "<init>");

	/** The mixin's replacement, and the delegate classes its constants' bodies became, by internal name. */
	record Result(byte[] mixin, Map<String, byte[]> classes, List<String> constants) {
	}

	/** A constant: its name, the enum's constructor making it (with name and ordinal), its arguments' code, its delegate or null. */
	private record Constant(String name, String constructor, List<AbstractInsnNode> arguments, String delegate) {
	}

	/** A body's delegate: the interface the enum forwards to, the method it implements, and the body's code. */
	private record Delegate(String itf, String method, MethodNode body) {
	}

	private EnumValuesMixins() {
	}

	/**
	 * @param mixin   the enum mixin, with code
	 * @param target  the enum it names, one of NeoForge's extensible enums
	 * @param classes the mod's classes by internal name, or null (a constant's body is a class of its own)
	 * @param bodies  the internal name to number the delegate classes from
	 * @return null when the mixin can't be turned into one
	 */
	static Result of(ClassNode mixin, ClassNode target, Function<String, ClassNode> classes, String bodies) {
		if ((mixin.access & Opcodes.ACC_ENUM) == 0 || !mixin.interfaces.isEmpty()) return null;
		String self = "L" + mixin.name + ";";
		Set<String> constants = new LinkedHashSet<>();
		for (FieldNode field : mixin.fields) {
			if ((field.access & Opcodes.ACC_ENUM) != 0 && field.desc.equals(self)) constants.add(field.name);
			else if ((field.access & Opcodes.ACC_SYNTHETIC) == 0 || !field.name.equals(VALUES)) return null;
		}
		MethodNode clinit = null;
		for (MethodNode method : mixin.methods) {
			if (method.name.equals("<clinit>")) clinit = method;
			else if (!ENUM_METHODS.contains(method.name) && Annotations.find(method.invisibleAnnotations, method.visibleAnnotations, SHADOW) == null) {
				return null;
			}
		}
		if (clinit == null || constants.isEmpty() || !clinit.tryCatchBlocks.isEmpty()) return null;

		// javac creates each constant in <clinit>: NEW (the mixin or its body's class), DUP, its name, its ordinal, its
		// arguments, the constructor, PUTSTATIC.
		List<Constant> made = new ArrayList<>();
		Map<String, byte[]> delegates = new LinkedHashMap<>();
		AbstractInsnNode insn = EnumExtensions.real(clinit.instructions.getFirst());
		while (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
			insn = EnumExtensions.real(insn.getNext());
			if (insn == null || insn.getOpcode() != Opcodes.DUP) return null;
			insn = EnumExtensions.real(insn.getNext());
			if (!(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String name) || !constants.contains(name)) return null;
			insn = EnumExtensions.real(insn.getNext());
			if (!(EnumExtensions.constant(insn) instanceof Integer)) return null;
			List<AbstractInsnNode> code = new ArrayList<>();
			for (insn = EnumExtensions.real(insn.getNext()); insn != null && !(insn instanceof MethodInsnNode call && call.owner.equals(type.desc));
					insn = EnumExtensions.real(insn.getNext())) {
				code.add(insn);
			}
			if (!(insn instanceof MethodInsnNode init) || !init.name.equals("<init>") || !EnumExtensions.computable(code, mixin.name, target.name)) return null;
			insn = EnumExtensions.real(insn.getNext());
			if (!(insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTSTATIC && put.owner.equals(mixin.name) && put.name.equals(name))) {
				return null;
			}
			String constructor = init.desc.replace(self, "L" + target.name + ";");
			String delegateClass = null;
			if (!type.desc.equals(mixin.name)) {
				Delegate delegate = delegate(classes == null ? null : classes.apply(type.desc), mixin, target);
				if (delegate == null) return null;
				constructor = constructor.replace(")V", "L" + delegate.itf() + ";)V");
				delegateClass = bodies + "$" + delegates.size();
				delegates.put(delegateClass, delegateClass(delegateClass, delegate));
			}
			if (!constructs(target, constructor)) return null;
			made.add(new Constant(name, constructor, code, delegateClass));
			insn = EnumExtensions.real(insn.getNext());
		}
		// Then javac's values array, and the rest of the class initializer up to its return.
		if (!(insn instanceof MethodInsnNode values && values.owner.equals(mixin.name) && values.name.equals("$values"))) return null;
		insn = EnumExtensions.real(insn.getNext());
		if (!(insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTSTATIC && put.owner.equals(mixin.name) && put.name.equals(VALUES))) return null;
		List<AbstractInsnNode> rest = new ArrayList<>();
		for (insn = EnumExtensions.real(insn.getNext()); insn != null && insn.getOpcode() != Opcodes.RETURN; insn = EnumExtensions.real(insn.getNext())) {
			rest.add(insn);
		}
		if (insn == null || EnumExtensions.real(insn.getNext()) != null || made.size() != constants.size() || !movable(rest, mixin.name, constants)) return null;
		return new Result(mixinClass(mixin, target, made, rest), delegates, List.copyOf(constants));
	}

	/** Whether any of the mod's classes reads one of the constants as a field of the enum, which the generated mixin doesn't add. */
	static boolean readAsFields(Iterable<ClassNode> classes, String target, List<String> constants) {
		for (ClassNode node : classes) {
			for (MethodNode method : node.methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof FieldInsnNode field && field.owner.equals(target) && constants.contains(field.name)) return true;
				}
			}
		}
		return false;
	}

	/**
	 * The delegate a constant's body becomes: the body's class extends the mixin and overrides one method, which the enum
	 * forwards to an interface held in a field of its own, with the same descriptor; the body runs straight through and
	 * uses nothing of its class or the mixin. Null otherwise.
	 */
	private static Delegate delegate(ClassNode body, ClassNode mixin, ClassNode target) {
		if (body == null || !mixin.name.equals(body.superName) || !body.fields.isEmpty() || !body.interfaces.isEmpty()) return null;
		MethodNode override = null;
		for (MethodNode m : body.methods) {
			if (m.name.equals("<init>")) continue;
			if (override != null || (m.access & Opcodes.ACC_STATIC) != 0) return null;
			override = m;
		}
		if (override == null || !override.tryCatchBlocks.isEmpty()) return null;
		MethodInsnNode forwarded = null;
		for (MethodNode m : target.methods) {
			if (!m.name.equals(override.name) || !m.desc.equals(override.desc) || (m.access & Opcodes.ACC_ABSTRACT) != 0) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEINTERFACE || !call.desc.equals(override.desc)) continue;
				if (forwarded != null || !holds(target, call.owner)) return null;
				forwarded = call;
			}
		}
		if (forwarded == null) return null;
		Set<String> foreign = Set.of(body.name, mixin.name);
		for (AbstractInsnNode insn : override.instructions) {
			boolean uses = switch (insn) {
				case VarInsnNode var -> var.var == 0;
				case JumpInsnNode jump -> true;
				case TableSwitchInsnNode table -> true;
				case LookupSwitchInsnNode lookup -> true;
				case FieldInsnNode field -> foreign.contains(field.owner) || mentions(field.desc, foreign);
				case MethodInsnNode call -> foreign.contains(call.owner) || mentions(call.desc, foreign);
				case TypeInsnNode type -> foreign.contains(type.desc) || mentions(type.desc, foreign);
				case MultiANewArrayInsnNode array -> mentions(array.desc, foreign);
				case InvokeDynamicInsnNode indy -> mentions(indy.desc, foreign) || handlesForeign(indy, foreign);
				case LdcInsnNode ldc -> ldc.cst instanceof org.objectweb.asm.Type t && mentions(t.getDescriptor(), foreign);
				default -> false;
			};
			if (uses) return null;
		}
		return new Delegate(forwarded.owner, forwarded.name, override);
	}

	/** Whether the enum has an instance field of that interface type (its delegate). */
	private static boolean holds(ClassNode target, String itf) {
		for (FieldNode field : target.fields) {
			if ((field.access & Opcodes.ACC_STATIC) == 0 && field.desc.equals("L" + itf + ";")) return true;
		}
		return false;
	}

	private static boolean constructs(ClassNode target, String constructor) {
		for (MethodNode m : target.methods) {
			if (m.name.equals("<init>") && m.desc.equals(constructor)) return true;
		}
		return false;
	}

	/**
	 * Whether the rest of the mixin's class initializer can run in the enum's: straight through, with no local variables,
	 * reading the mixin only for its own constants.
	 */
	private static boolean movable(List<AbstractInsnNode> rest, String mixin, Set<String> constants) {
		Set<String> foreign = Set.of(mixin);
		for (AbstractInsnNode insn : rest) {
			int op = insn.getOpcode();
			boolean ok = switch (insn) {
				case JumpInsnNode jump -> false;
				case TableSwitchInsnNode table -> false;
				case LookupSwitchInsnNode lookup -> false;
				case VarInsnNode var -> false;
				case FieldInsnNode field -> field.owner.equals(mixin) ? op == Opcodes.GETSTATIC && constants.contains(field.name) : !mentions(field.desc, foreign);
				case MethodInsnNode call -> !call.owner.equals(mixin) && !mentions(call.desc, foreign);
				case TypeInsnNode type -> !type.desc.equals(mixin) && !mentions(type.desc, foreign);
				case MultiANewArrayInsnNode array -> !mentions(array.desc, foreign);
				case InvokeDynamicInsnNode indy -> !mentions(indy.desc, foreign) && !handlesForeign(indy, foreign);
				case LdcInsnNode ldc -> !(ldc.cst instanceof org.objectweb.asm.Type t && mentions(t.getDescriptor(), foreign));
				default -> op != Opcodes.ATHROW && op != Opcodes.MONITORENTER && op != Opcodes.MONITOREXIT && (op < Opcodes.IRETURN || op > Opcodes.RETURN)
						&& op != Opcodes.IINC;
			};
			if (!ok) return false;
		}
		return true;
	}

	private static boolean mentions(String descriptor, Set<String> classes) {
		for (String name : classes) {
			if (descriptor.contains("L" + name + ";")) return true;
		}
		return false;
	}

	private static boolean handlesForeign(InvokeDynamicInsnNode indy, Set<String> classes) {
		for (Object arg : indy.bsmArgs) {
			if (arg instanceof Handle h && (classes.contains(h.getOwner()) || mentions(h.getDesc(), classes))) return true;
		}
		return false;
	}

	/** A public class implementing the delegate interface with the body's code (an instance method of the same descriptor). */
	private static byte[] delegateClass(String name, Delegate delegate) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object",
				new String[] {delegate.itf()});
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, delegate.method(), delegate.body().desc, null, null);
		mv.visitCode();
		for (AbstractInsnNode insn : delegate.body().instructions) {
			if (insn.getOpcode() >= 0) insn.accept(mv);
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The mixin replacing the enum mixin, under its name and {@code @Mixin}: a shadow of the enum's values array, a
	 * constructor invoker per constructor used, and the injector adding the constants after the array is set.
	 */
	private static byte[] mixinClass(ClassNode mixin, ClassNode target, List<Constant> constants, List<AbstractInsnNode> rest) {
		String enumDesc = "L" + target.name + ";";
		String arrayDesc = "[" + enumDesc;
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER, mixin.name, null, "java/lang/Object", null);
		copyMixinAnnotation(mixin.invisibleAnnotations, cw, false);
		copyMixinAnnotation(mixin.visibleAnnotations, cw, true);
		FieldVisitor values = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, VALUES, arrayDesc, null, null);
		// Mixin's member annotations are kept at runtime, and it reads them from there (@Mutable makes the field non-final).
		values.visitAnnotation(SHADOW, true).visitEnd();
		values.visitAnnotation(FINAL, true).visitEnd();
		values.visitAnnotation(MUTABLE, true).visitEnd();
		values.visitEnd();

		Map<String, String> invokers = new LinkedHashMap<>();
		for (Constant c : constants) invokers.computeIfAbsent(c.constructor(), k -> "fabricated$new" + invokers.size());
		invokers.forEach((constructor, name) -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, constructor.replace(")V", ")" + enumDesc), null, null);
			AnnotationVisitor invoker = mv.visitAnnotation(INVOKER, true);
			invoker.visit("value", "<init>");
			invoker.visitEnd();
			mv.visitCode();
			mv.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
			mv.visitInsn(Opcodes.DUP);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
			mv.visitInsn(Opcodes.ATHROW);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		});

		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "fabricated$addConstants", "(" + CALLBACK_INFO + ")V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation(INJECT, true);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, "<clinit>");
		methods.visitEnd();
		AnnotationVisitor ats = inject.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "FIELD");
		at.visit("opcode", Opcodes.PUTSTATIC);
		at.visit("target", enumDesc + VALUES + ":" + arrayDesc);
		at.visitEnum("shift", SHIFT, "AFTER");
		at.visitEnd();
		ats.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		// Locals: 0 the callback, 1 the enum's values, 2 their count, 3 the values with the constants, 4... the constants.
		mv.visitFieldInsn(Opcodes.GETSTATIC, mixin.name, VALUES, arrayDesc);
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitInsn(Opcodes.ARRAYLENGTH);
		mv.visitVarInsn(Opcodes.ISTORE, 2);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ILOAD, 2);
		mv.visitLdcInsn(constants.size());
		mv.visitInsn(Opcodes.IADD);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false);
		mv.visitTypeInsn(Opcodes.CHECKCAST, arrayDesc);
		mv.visitVarInsn(Opcodes.ASTORE, 3);
		Map<String, Integer> slots = new LinkedHashMap<>();
		for (int i = 0; i < constants.size(); i++) {
			Constant c = constants.get(i);
			mv.visitLdcInsn(c.name());
			mv.visitVarInsn(Opcodes.ILOAD, 2);
			mv.visitLdcInsn(i);
			mv.visitInsn(Opcodes.IADD);
			for (AbstractInsnNode insn : c.arguments()) insn.accept(mv);
			if (c.delegate() != null) {
				mv.visitTypeInsn(Opcodes.NEW, c.delegate());
				mv.visitInsn(Opcodes.DUP);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, c.delegate(), "<init>", "()V", false);
			}
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, mixin.name, invokers.get(c.constructor()), c.constructor().replace(")V", ")" + enumDesc), false);
			mv.visitVarInsn(Opcodes.ASTORE, 4 + i);
			mv.visitVarInsn(Opcodes.ALOAD, 3);
			mv.visitVarInsn(Opcodes.ILOAD, 2);
			mv.visitLdcInsn(i);
			mv.visitInsn(Opcodes.IADD);
			mv.visitVarInsn(Opcodes.ALOAD, 4 + i);
			mv.visitInsn(Opcodes.AASTORE);
			slots.put(c.name(), 4 + i);
		}
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitFieldInsn(Opcodes.PUTSTATIC, mixin.name, VALUES, arrayDesc);
		// The rest of the mixin's class initializer, its constants read from the locals.
		for (AbstractInsnNode insn : rest) {
			if (insn instanceof FieldInsnNode field && field.owner.equals(mixin.name)) mv.visitVarInsn(Opcodes.ALOAD, slots.get(field.name));
			else insn.accept(mv);
		}
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void copyMixinAnnotation(List<AnnotationNode> annotations, ClassWriter cw, boolean visible) {
		if (annotations == null) return;
		for (AnnotationNode ann : annotations) {
			if (ann.desc.equals(Annotations.MIXIN)) ann.accept(cw.visitAnnotation(ann.desc, visible));
		}
	}
}
