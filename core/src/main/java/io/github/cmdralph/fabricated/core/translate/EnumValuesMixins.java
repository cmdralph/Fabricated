package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
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
 * initializer with the constants in hand. The same serves an enum mixin into any enum where the running Mixin applies
 * no enum mixins, as before 0.17.1 (ENUM-7: Forge's, whose Mixin 0.8.7 stopped the Forge 61 to 66 corpus clients on
 * Friends and Foes' mob categories); the enum's values array is found by its type there, as Forge 47's names may not be
 * javac's. An enum Forge makes extensible ({@code IExtensibleEnum}, as {@code MobCategory}) gets its constants from its own
 * {@code create} factories instead, at the end of its class initializer: Forge's enum extender writes them once Mixin has
 * applied, and finds the values array only while it is still final, which the shadow's {@code @Mutable} would undo; and
 * each registers its constant ({@code IExtensibleEnum.init}) in statics set after the values array, such as
 * {@code MobCategory}'s map by name, which its codec reads.
 * Where Forge's {@code init} of such an enum can't return (Forge 61 to 66's {@code RecipeBookType}, whose {@code init}
 * throws "This is not implemented yet"), the mixin also skips it for the mod's constants, which Fabric gives no such
 * hook (CORPUS-80: Farmer's Delight's recipe book type).
 * <p>A constant with a body overriding the one method the enum forwards to a delegate its other constructor takes
 * (NeoForge's way of making such an enum extensible, as {@code GrassColorModifier}'s {@code ColorModifier}) is made with
 * that constructor and a delegate class generated next to the mod's mixins, running the body. Only code that runs
 * straight through and uses nothing of the mixin moves (the arguments, the bodies, the rest of the class initializer).
 * <p>A mixin adds no public static fields, so the mod's reads of a constant as a field of the enum (Farmer's Delight's
 * {@code RecipeBookType.FARMERSDELIGHT_COOKING}) read it from a holder class generated next to the mixins instead
 * ({@link #holderClass}, {@link #withHeldConstants}).
 */
final class EnumValuesMixins {
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String FINAL = "Lorg/spongepowered/asm/mixin/Final;";
	private static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String SHIFT = "Lorg/spongepowered/asm/mixin/injection/At$Shift;";
	private static final String CALLBACK_INFO_CLASS = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String CALLBACK_INFO = "L" + CALLBACK_INFO_CLASS + ";";
	/** javac's name for an enum's array of its constants. */
	private static final String VALUES = "$VALUES";
	/** Forge's marker of an enum it makes extensible, whose {@code create} factories its enum extender writes. */
	static final String FORGE_EXTENSIBLE_ENUM = "net/minecraftforge/common/IExtensibleEnum";
	private static final String FACTORY = "create";
	/** Forge's hook its factories call on each constant they make ({@code IExtensibleEnum.init}). */
	private static final String INIT = "init";
	/** The holder's lookup of a constant by name. */
	private static final String FIND = "fabricated$constant";
	private static final String FIND_DESC = "([Ljava/lang/Enum;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Enum;";
	private static final Set<String> ENUM_METHODS = Set.of("values", "valueOf", "$values", "<init>");

	/** The mixin's replacement, and the delegate classes its constants' bodies became, by internal name. */
	record Result(byte[] mixin, Map<String, byte[]> classes, List<String> constants) {
	}

	/**
	 * A constant: its name, the enum's constructor making it (with name and ordinal), its arguments' code, its delegate or
	 * null, and the descriptor of the enum's factory making it (Forge's) or null.
	 */
	private record Constant(String name, String constructor, List<AbstractInsnNode> arguments, String delegate, String factory) {
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
	 * @param game    the game's classes by internal name, with code, or null (what Forge's {@code init} calls)
	 * @param bodies  the internal name to number the delegate classes from
	 * @return null when the mixin can't be turned into one
	 */
	static Result of(ClassNode mixin, ClassNode target, Function<String, ClassNode> classes, Function<String, ClassNode> game, String bodies) {
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
		String valuesArray = valuesField(target);
		if (valuesArray == null) return null;

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
			String factory = null;
			if (target.interfaces.contains(FORGE_EXTENSIBLE_ENUM)) {
				factory = factory(target, constructor);
				if (factory == null) return null;
			}
			made.add(new Constant(name, constructor, code, delegateClass, factory));
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
		boolean skipInit = target.interfaces.contains(FORGE_EXTENSIBLE_ENUM) && !initReturns(target, game);
		return new Result(mixinClass(mixin, target, valuesArray, made, rest, skipInit), delegates, List.copyOf(constants));
	}

	/**
	 * The name of the enum's array of its constants: javac's {@code $VALUES}, or else its one static synthetic field of
	 * that array's type (a platform's names for the game's, which ProGuard obfuscated); null when there is none or several.
	 */
	static String valuesField(ClassNode target) {
		String desc = "[L" + target.name + ";";
		int flags = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
		String found = null;
		for (FieldNode field : target.fields) {
			if ((field.access & flags) != flags || !field.desc.equals(desc)) continue;
			if (field.name.equals(VALUES)) return VALUES;
			if (found != null) return null;
			found = field.name;
		}
		return found;
	}

	/**
	 * Whether the enum's Forge {@code init}, which its factories call on each constant they make, can return. Not when it
	 * has no return, nor when it runs straight through a call to a method of the enum or the game that has none (Forge 61
	 * to 66's {@code RecipeBookType.init} calls {@code RecipeBookSettings.register}, which throws "This is not implemented
	 * yet", so no constant its factory makes survives it). True where that can't be told: a branch, a handler, a method
	 * without code here, or a call that may go elsewhere (a virtual one).
	 */
	static boolean initReturns(ClassNode target, Function<String, ClassNode> game) {
		MethodNode init = declared(target, INIT, "()V");
		if (init == null || init.instructions.size() == 0 || (init.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) return true;
		if (!returns(init)) return false;
		if (!init.tryCatchBlocks.isEmpty()) return true;
		for (AbstractInsnNode insn : init.instructions) {
			if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) return true;
		}
		for (AbstractInsnNode insn : init.instructions) {
			if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC && call.getOpcode() != Opcodes.INVOKESPECIAL) continue;
			ClassNode owner = call.owner.equals(target.name) ? target : game == null ? null : game.apply(call.owner);
			MethodNode called = owner == null ? null : declared(owner, call.name, call.desc);
			if (called != null && called.instructions.size() > 0 && (called.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0 && !returns(called)) {
				return false;
			}
		}
		return true;
	}

	private static MethodNode declared(ClassNode owner, String name, String desc) {
		for (MethodNode m : owner.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) return m;
		}
		return null;
	}

	/** Whether a method with code has a return: one with none can only throw (or never end). */
	private static boolean returns(MethodNode method) {
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) return true;
		}
		return false;
	}

	/**
	 * The class holding an enum's constants for the mod's reads of them as fields of the enum, which the generated mixin
	 * can't add: a public static final field per constant, set in its class initializer from the enum's values by name,
	 * which initializes the enum, and so runs the mixin, first. A constant the enum lacks then (its mixin didn't apply)
	 * fails the initializer naming it and {@code mixin} (dotted) rather than leaving the field null.
	 */
	static byte[] holderClass(String name, String target, List<String> constants, String mixin) {
		String enumDesc = "L" + target + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object", null);
		for (String constant : constants) {
			cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, constant, enumDesc, null, null).visitEnd();
		}
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, target, "values", "()[" + enumDesc, false);
		clinit.visitVarInsn(Opcodes.ASTORE, 0);
		for (String constant : constants) {
			clinit.visitVarInsn(Opcodes.ALOAD, 0);
			clinit.visitLdcInsn(constant);
			clinit.visitLdcInsn("Fabricated: " + target.replace('/', '.') + " has no constant " + constant + " once initialized: " + mixin
					+ ", which adds it, did not apply");
			clinit.visitMethodInsn(Opcodes.INVOKESTATIC, name, FIND, FIND_DESC, false);
			clinit.visitTypeInsn(Opcodes.CHECKCAST, target);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, constant, enumDesc);
		}
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();

		// for (int i = 0; i < values.length; i++) if (values[i].name().equals(name)) return values[i]; throw new IllegalStateException(message);
		MethodVisitor find = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, FIND, FIND_DESC, null, null);
		find.visitCode();
		Label loop = new Label();
		Label next = new Label();
		Label missing = new Label();
		find.visitInsn(Opcodes.ICONST_0);
		find.visitVarInsn(Opcodes.ISTORE, 3);
		find.visitLabel(loop);
		find.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
		find.visitVarInsn(Opcodes.ILOAD, 3);
		find.visitVarInsn(Opcodes.ALOAD, 0);
		find.visitInsn(Opcodes.ARRAYLENGTH);
		find.visitJumpInsn(Opcodes.IF_ICMPGE, missing);
		find.visitVarInsn(Opcodes.ALOAD, 0);
		find.visitVarInsn(Opcodes.ILOAD, 3);
		find.visitInsn(Opcodes.AALOAD);
		find.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Enum", "name", "()Ljava/lang/String;", false);
		find.visitVarInsn(Opcodes.ALOAD, 1);
		find.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
		find.visitJumpInsn(Opcodes.IFEQ, next);
		find.visitVarInsn(Opcodes.ALOAD, 0);
		find.visitVarInsn(Opcodes.ILOAD, 3);
		find.visitInsn(Opcodes.AALOAD);
		find.visitInsn(Opcodes.ARETURN);
		find.visitLabel(next);
		find.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		find.visitIincInsn(3, 1);
		find.visitJumpInsn(Opcodes.GOTO, loop);
		find.visitLabel(missing);
		find.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		find.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
		find.visitInsn(Opcodes.DUP);
		find.visitVarInsn(Opcodes.ALOAD, 2);
		find.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
		find.visitInsn(Opcodes.ATHROW);
		find.visitMaxs(0, 0);
		find.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The class with its reads of the constants as fields of the enum (and method handles to them) reading the
	 * {@code holder}'s fields instead; null when it has none.
	 */
	static byte[] withHeldConstants(byte[] data, String target, Collection<String> constants, String holder) {
		if (!new String(data, StandardCharsets.ISO_8859_1).contains(target)) return null;
		String enumDesc = "L" + target + ";";
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		boolean changed = false;
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(target) && field.desc.equals(enumDesc)
						&& constants.contains(field.name)) {
					field.owner = holder;
					changed = true;
				} else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (int i = 0; i < indy.bsmArgs.length; i++) {
						Handle held = held(indy.bsmArgs[i], target, enumDesc, constants, holder);
						if (held != null) {
							indy.bsmArgs[i] = held;
							changed = true;
						}
					}
				} else if (insn instanceof LdcInsnNode ldc) {
					Handle held = held(ldc.cst, target, enumDesc, constants, holder);
					if (held != null) {
						ldc.cst = held;
						changed = true;
					}
				}
			}
		}
		if (!changed) return null;
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** A handle reading one of the constants as a field of the enum, reading the holder's instead; null for any other value. */
	private static Handle held(Object value, String target, String enumDesc, Collection<String> constants, String holder) {
		if (!(value instanceof Handle h) || h.getTag() != Opcodes.H_GETSTATIC || !h.getOwner().equals(target) || !h.getDesc().equals(enumDesc)
				|| !constants.contains(h.getName())) return null;
		return new Handle(Opcodes.H_GETSTATIC, holder, h.getName(), h.getDesc(), false);
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
			if (uses(insn, foreign)) return null;
		}
		return new Delegate(forwarded.owner, forwarded.name, override);
	}

	/** Whether an instruction of an override uses {@code this}, branches, or names one of the {@code foreign} classes. */
	private static boolean uses(AbstractInsnNode insn, Set<String> foreign) {
		if (insn instanceof VarInsnNode var) return var.var == 0;
		if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) return true;
		if (insn instanceof FieldInsnNode field) return foreign.contains(field.owner) || mentions(field.desc, foreign);
		if (insn instanceof MethodInsnNode call) return foreign.contains(call.owner) || mentions(call.desc, foreign);
		if (insn instanceof TypeInsnNode type) return foreign.contains(type.desc) || mentions(type.desc, foreign);
		if (insn instanceof MultiANewArrayInsnNode array) return mentions(array.desc, foreign);
		if (insn instanceof InvokeDynamicInsnNode indy) return mentions(indy.desc, foreign) || handlesForeign(indy, foreign);
		return insn instanceof LdcInsnNode ldc && ldc.cst instanceof org.objectweb.asm.Type t && mentions(t.getDescriptor(), foreign);
	}

	/** Whether the enum has an instance field of that interface type (its delegate). */
	private static boolean holds(ClassNode target, String itf) {
		for (FieldNode field : target.fields) {
			if ((field.access & Opcodes.ACC_STATIC) == 0 && field.desc.equals("L" + itf + ";")) return true;
		}
		return false;
	}

	/**
	 * The descriptor of the enum's static {@code create} factory making a constant with that constructor, as Forge's enum
	 * extender pairs them (the constructor's parameters but the ordinal, returning the enum); null when it has none.
	 */
	static String factory(ClassNode target, String constructor) {
		org.objectweb.asm.Type[] parameters = org.objectweb.asm.Type.getArgumentTypes(constructor);
		if (parameters.length < 2 || !parameters[0].getDescriptor().equals("Ljava/lang/String;") || parameters[1] != org.objectweb.asm.Type.INT_TYPE) {
			return null;
		}
		org.objectweb.asm.Type[] arguments = new org.objectweb.asm.Type[parameters.length - 1];
		arguments[0] = parameters[0];
		System.arraycopy(parameters, 2, arguments, 1, parameters.length - 2);
		String desc = org.objectweb.asm.Type.getMethodDescriptor(org.objectweb.asm.Type.getObjectType(target.name), arguments);
		for (MethodNode m : target.methods) {
			if ((m.access & Opcodes.ACC_STATIC) != 0 && m.name.equals(FACTORY) && m.desc.equals(desc)) return desc;
		}
		return null;
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
			if (!movable(insn, mixin, constants, foreign)) return false;
		}
		return true;
	}

	private static boolean movable(AbstractInsnNode insn, String mixin, Set<String> constants, Set<String> foreign) {
		int op = insn.getOpcode();
		if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode
				|| insn instanceof VarInsnNode) return false;
		if (insn instanceof FieldInsnNode field) {
			return field.owner.equals(mixin) ? op == Opcodes.GETSTATIC && constants.contains(field.name) : !mentions(field.desc, foreign);
		}
		if (insn instanceof MethodInsnNode call) return !call.owner.equals(mixin) && !mentions(call.desc, foreign);
		if (insn instanceof TypeInsnNode type) return !type.desc.equals(mixin) && !mentions(type.desc, foreign);
		if (insn instanceof MultiANewArrayInsnNode array) return !mentions(array.desc, foreign);
		if (insn instanceof InvokeDynamicInsnNode indy) return !mentions(indy.desc, foreign) && !handlesForeign(indy, foreign);
		if (insn instanceof LdcInsnNode ldc) return !(ldc.cst instanceof org.objectweb.asm.Type t && mentions(t.getDescriptor(), foreign));
		return op != Opcodes.ATHROW && op != Opcodes.MONITORENTER && op != Opcodes.MONITOREXIT && (op < Opcodes.IRETURN || op > Opcodes.RETURN)
				&& op != Opcodes.IINC;
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
	 * constructor invoker per constructor used, and the injector adding the constants after the array is set. Where the
	 * enum's own factories make the constants (Forge's), the injector calls them at the class initializer's end, and the
	 * mixin has neither; with {@code skipInit}, a second injector skips Forge's {@code init} for the constants.
	 */
	private static byte[] mixinClass(ClassNode mixin, ClassNode target, String values, List<Constant> constants, List<AbstractInsnNode> rest,
			boolean skipInit) {
		String enumDesc = "L" + target.name + ";";
		String arrayDesc = "[" + enumDesc;
		boolean factories = constants.stream().allMatch(c -> c.factory() != null);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER, mixin.name, null, "java/lang/Object", null);
		copyMixinAnnotation(mixin.invisibleAnnotations, cw, false);
		copyMixinAnnotation(mixin.visibleAnnotations, cw, true);
		if (!factories) {
			FieldVisitor shadow = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, values, arrayDesc, null, null);
			// Mixin's member annotations are kept at runtime, and it reads them from there (@Mutable makes the field non-final).
			shadow.visitAnnotation(SHADOW, true).visitEnd();
			shadow.visitAnnotation(FINAL, true).visitEnd();
			shadow.visitAnnotation(MUTABLE, true).visitEnd();
			shadow.visitEnd();
		}

		Map<String, String> invokers = new LinkedHashMap<>();
		for (Constant c : constants) {
			if (c.factory() == null) invokers.computeIfAbsent(c.constructor(), k -> "fabricated$new" + invokers.size());
		}
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
		if (factories) {
			// At the end, as a Forge mod's own call would come: a factory registers its constant in statics the class
			// initializer sets after the values array (MobCategory's BY_NAME, read by its codec), still null right after it.
			at.visit("value", "TAIL");
		} else {
			at.visit("value", "FIELD");
			at.visit("opcode", Opcodes.PUTSTATIC);
			at.visit("target", enumDesc + values + ":" + arrayDesc);
			at.visitEnum("shift", SHIFT, "AFTER");
		}
		at.visitEnd();
		ats.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		Map<String, Integer> slots = new LinkedHashMap<>();
		if (factories) {
			// Locals: 0 the callback, 1... the constants. Each factory appends its constant to the values array.
			for (int i = 0; i < constants.size(); i++) {
				Constant c = constants.get(i);
				mv.visitLdcInsn(c.name());
				for (AbstractInsnNode insn : c.arguments()) insn.accept(mv);
				if (c.delegate() != null) {
					mv.visitTypeInsn(Opcodes.NEW, c.delegate());
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, c.delegate(), "<init>", "()V", false);
				}
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, target.name, FACTORY, c.factory(), false);
				mv.visitVarInsn(Opcodes.ASTORE, 1 + i);
				slots.put(c.name(), 1 + i);
			}
			restOf(mv, mixin, rest, slots);
			if (skipInit) skipInit(cw, mixin.name, constants);
			cw.visitEnd();
			return cw.toByteArray();
		}
		// Locals: 0 the callback, 1 the enum's values, 2 their count, 3 the values with the constants, 4... the constants.
		mv.visitFieldInsn(Opcodes.GETSTATIC, mixin.name, values, arrayDesc);
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
		mv.visitFieldInsn(Opcodes.PUTSTATIC, mixin.name, values, arrayDesc);
		restOf(mv, mixin, rest, slots);
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The injector skipping Forge's {@code init} for the constants, cancelling it at its head: Fabric gives them no such
	 * hook, and here it never returns ({@link #initReturns}). Forge's factory has appended the constant by then. The
	 * enum's other constants keep theirs.
	 */
	private static void skipInit(ClassWriter cw, String mixin, List<Constant> constants) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "fabricated$skipInit", "(" + CALLBACK_INFO + ")V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation(INJECT, true);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, INIT + "()V");
		methods.visitEnd();
		AnnotationVisitor ats = inject.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, AT);
		at.visit("value", "HEAD");
		at.visitEnd();
		ats.visitEnd();
		inject.visit("cancellable", Boolean.TRUE);
		inject.visitEnd();
		mv.visitCode();
		// Locals: 0 the constant, 1 the callback, 2 its name.
		Label cancel = new Label();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Enum");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Enum", "name", "()Ljava/lang/String;", false);
		mv.visitVarInsn(Opcodes.ASTORE, 2);
		for (Constant c : constants) {
			mv.visitLdcInsn(c.name());
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			mv.visitJumpInsn(Opcodes.IFNE, cancel);
		}
		mv.visitInsn(Opcodes.RETURN);
		mv.visitLabel(cancel);
		mv.visitFrame(Opcodes.F_FULL, 3, new Object[] {mixin, CALLBACK_INFO_CLASS, "java/lang/String"}, 0, new Object[0]);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CALLBACK_INFO_CLASS, "cancel", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/** The rest of the mixin's class initializer, its constants read from the locals, and the injector's end. */
	private static void restOf(MethodVisitor mv, ClassNode mixin, List<AbstractInsnNode> rest, Map<String, Integer> slots) {
		for (AbstractInsnNode insn : rest) {
			if (insn instanceof FieldInsnNode field && field.owner.equals(mixin.name)) mv.visitVarInsn(Opcodes.ALOAD, slots.get(field.name));
			else insn.accept(mv);
		}
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static void copyMixinAnnotation(List<AnnotationNode> annotations, ClassWriter cw, boolean visible) {
		if (annotations == null) return;
		for (AnnotationNode ann : annotations) {
			if (ann.desc.equals(Annotations.MIXIN)) ann.accept(cw.visitAnnotation(ann.desc, visible));
		}
	}
}
