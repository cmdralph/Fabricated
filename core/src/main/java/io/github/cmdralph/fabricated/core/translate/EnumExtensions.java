package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
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
import io.github.cmdralph.fabricated.core.json.Json;

/**
 * Turns a Fabric enum mixin that only adds constants to one of NeoForge's extensible enums into entries of a NeoForge
 * enum extension file (ENUM-1). NeoForge's loader adds the constants of such an enum itself, from the files mods name,
 * and lets nothing else change it, so the mixin's constants would never exist. Only a mixin with nothing but constants
 * (no other members, no interfaces), each named after the mod (as NeoForge requires), is translated: into an enum
 * NeoForge doesn't give ids (not indexed), and into a named enum only with names already starting with the mod's id
 * and a colon, which NeoForge checks when the enum is made. Any other stays a pre-flight failure (CORPUS-10).
 * <p>A constant built by a constructor the enum allows from constant arguments is entered with them. One whose
 * arguments are computed when the class loads (a registered object, a static array), or whose constructor NeoForge
 * reserves for one taking suppliers where the mixin's takes values (as {@code Raid.RaiderType}'s), is entered with a
 * method that computes them as the mixin's class initializer does, which NeoForge's loader calls when it adds the
 * constant: in a class generated for the mod ({@link #argumentsClasses}), and only from code that runs straight through
 * and uses nothing of the mixin itself (ENUM-3).
 */
final class EnumExtensions {
	/** The file in the overlay, which the generated mods.toml names. */
	static final String PATH = "META-INF/fabricated/enum_extensions.json";
	static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";
	private static final String RESERVED_CONSTRUCTOR = "Lnet/neoforged/fml/common/asm/enumextension/ReservedConstructor;";
	private static final String INDEXED_ENUM = "Lnet/neoforged/fml/common/asm/enumextension/IndexedEnum;";
	private static final String NAMED_ENUM = "Lnet/neoforged/fml/common/asm/enumextension/NamedEnum;";
	/** Every enum constructor starts with the constant's name and ordinal, which NeoForge's entries leave out. */
	private static final String NAME_AND_ORDINAL = "(Ljava/lang/String;I";
	/** What javac gives an enum besides its constants; an enum mixin with anything else is not translated. */
	private static final Set<String> ENUM_METHODS = Set.of("<clinit>", "<init>", "values", "valueOf", "$values");
	private static final Object NOT_CONSTANT = new Object();

	/** The descriptor of the method NeoForge's loader calls for each argument of a constant (its index, its type). */
	static final String ARGUMENTS_METHOD = "(ILjava/lang/Class;)Ljava/lang/Object;";
	private static final String SUPPLIER = "Ljava/util/function/Supplier;";

	/**
	 * One constant: its enum (an internal name, as NeoForge's file takes it), its name, the constructor without the name
	 * and ordinal, and its arguments, constants or {@code computed}.
	 */
	record Entry(String enumName, String name, String constructor, List<Object> arguments, Computed computed) {
		Entry(String enumName, String name, String constructor, List<Object> arguments) {
			this(enumName, name, constructor, arguments, null);
		}
	}

	/**
	 * Arguments computed when NeoForge's loader adds the constant: by the code the mixin's class initializer runs for
	 * them (loading the values {@code loaded} describes, a constructor descriptor), each wrapped in a supplier where
	 * {@code supplied}; by the method {@code method} of the class {@code owner} once {@link #named} gives them.
	 */
	record Computed(List<AbstractInsnNode> code, String loaded, boolean[] supplied, String owner, String method) {
	}

	private EnumExtensions() {
	}

	/** The one class the mixin's {@code @Mixin} names, or null. */
	static String target(ClassNode mixin) {
		AnnotationNode ann = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (ann == null || Annotations.value(ann, "targets") != null) return null;
		return Annotations.value(ann, "value") instanceof List<?> types && types.size() == 1 && types.get(0) instanceof Type type
				? type.getInternalName() : null;
	}

	/**
	 * The entries {@code mixin} stands for, or null when it isn't an enum mixin into an extensible enum that can be
	 * translated.
	 *
	 * @param mixin  the mixin, with code
	 * @param target the enum it names, or null when absent
	 * @param modId  the mod's NeoForge id, which each constant's name must start with
	 */
	static List<Entry> of(ClassNode mixin, ClassNode target, String modId) {
		if ((mixin.access & Opcodes.ACC_ENUM) == 0 || !mixin.interfaces.isEmpty() || target == null || !target.interfaces.contains(EXTENSIBLE_ENUM)) {
			return null;
		}
		// NeoForge gives an indexed enum's new constants their id, so the mixin's own values for it wouldn't carry over.
		if (Annotations.find(target.invisibleAnnotations, target.visibleAnnotations, INDEXED_ENUM) != null) return null;
		// A named enum's name argument (the first, unless the annotation says) must start with the mod's id and a colon.
		AnnotationNode named = Annotations.find(target.invisibleAnnotations, target.visibleAnnotations, NAMED_ENUM);
		int nameIndex = named == null ? -1 : Annotations.value(named, "value") instanceof Integer index ? index : 0;
		String self = "L" + mixin.name + ";";
		String enumDescriptor = "L" + target.name + ";";
		Set<String> constants = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if ((field.access & Opcodes.ACC_ENUM) != 0 && field.desc.equals(self)) constants.add(field.name);
			else if ((field.access & Opcodes.ACC_SYNTHETIC) == 0 || !field.name.equals("$VALUES")) return null;
		}
		MethodNode clinit = null;
		for (MethodNode method : mixin.methods) {
			if (!ENUM_METHODS.contains(method.name)) return null;
			if (method.name.equals("<clinit>")) clinit = method;
		}
		if (clinit == null || constants.isEmpty()) return null;
		Set<String> allowed = new HashSet<>();
		for (MethodNode ctor : target.methods) {
			if (ctor.name.equals("<init>") && Annotations.find(ctor.invisibleAnnotations, ctor.visibleAnnotations, RESERVED_CONSTRUCTOR) == null) {
				allowed.add(ctor.desc);
			}
		}

		// javac creates each constant in <clinit>: NEW, DUP, its name, its ordinal, its arguments, the constructor, PUTSTATIC.
		if (!clinit.tryCatchBlocks.isEmpty()) return null;
		List<Entry> entries = new ArrayList<>();
		AbstractInsnNode insn = real(clinit.instructions.getFirst());
		while (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(mixin.name)) {
			insn = real(insn.getNext());
			if (insn == null || insn.getOpcode() != Opcodes.DUP) return null;
			insn = real(insn.getNext());
			if (!(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String name)) return null;
			insn = real(insn.getNext());
			if (!(constant(insn) instanceof Integer)) return null;
			List<AbstractInsnNode> code = new ArrayList<>();
			for (insn = real(insn.getNext()); insn != null && !(insn instanceof MethodInsnNode call && call.owner.equals(mixin.name)); insn = real(insn.getNext())) {
				code.add(insn);
			}
			if (!(insn instanceof MethodInsnNode init) || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.name.equals("<init>")) return null;
			insn = real(insn.getNext());
			if (!(insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTSTATIC && put.owner.equals(mixin.name) && put.name.equals(name))) {
				return null;
			}
			String full = init.desc.replace(self, enumDescriptor);
			if (!full.startsWith(NAME_AND_ORDINAL) || !name.toLowerCase(Locale.ROOT).startsWith(modId)) return null;
			String constructor = "(" + full.substring(NAME_AND_ORDINAL.length());
			Entry entry = allowed.contains(full) ? constantEntry(target.name, name, constructor, code, nameIndex, modId) : null;
			if (entry == null && nameIndex < 0 && computable(code, mixin.name, target.name)) entry = computedEntry(target.name, name, constructor, code, allowed);
			if (entry == null) return null;
			entries.add(entry);
			insn = real(insn.getNext());
		}
		// Then only the values array (javac's $values(), or ECJ's inline array of the constants) and the return.
		for (; insn != null; insn = real(insn.getNext())) {
			if (!valuesArray(insn, mixin.name)) return null;
		}
		return entries.size() == constants.size() ? entries : null;
	}

	/** The entry of a constant whose arguments are all constants NeoForge's file can carry, or null. */
	private static Entry constantEntry(String enumName, String name, String constructor, List<AbstractInsnNode> code, int nameIndex, String modId) {
		List<Object> values = new ArrayList<>();
		for (AbstractInsnNode insn : code) {
			Object value = constant(insn);
			if (value == NOT_CONSTANT) return null;
			values.add(value);
		}
		List<Object> arguments = arguments(Type.getArgumentTypes(constructor), values);
		if (arguments == null || nameIndex >= 0 && !(nameIndex < arguments.size() && arguments.get(nameIndex) instanceof String value
				&& value.startsWith(modId + ":"))) return null;
		return new Entry(enumName, name, constructor, arguments);
	}

	/**
	 * The entry of a constant whose arguments are computed, with the constructor the enum allows: the mixin's, or the only
	 * one taking a supplier wherever the mixin's differs, and takes an object (NeoForge's own constructors for content
	 * registered later); or null.
	 */
	private static Entry computedEntry(String enumName, String name, String constructor, List<AbstractInsnNode> code, Set<String> allowed) {
		Type[] own = Type.getArgumentTypes(constructor);
		String chosen = null;
		boolean[] supplied = null;
		for (String full : allowed) {
			Type[] types = Type.getArgumentTypes("(" + full.substring(NAME_AND_ORDINAL.length()));
			if (types.length != own.length) continue;
			boolean[] wrap = new boolean[own.length];
			boolean fits = true;
			for (int i = 0; i < own.length && fits; i++) {
				if (types[i].equals(own[i])) continue;
				wrap[i] = types[i].getDescriptor().equals(SUPPLIER) && (own[i].getSort() == Type.OBJECT || own[i].getSort() == Type.ARRAY);
				fits = wrap[i];
			}
			if (!fits) continue;
			if (chosen != null) return null; // two fit: ambiguous
			chosen = "(" + full.substring(NAME_AND_ORDINAL.length());
			supplied = wrap;
		}
		return chosen == null ? null : new Entry(enumName, name, chosen, null, new Computed(List.copyOf(code), constructor, supplied, null, null));
	}

	/**
	 * Whether code loading a constant's arguments can run elsewhere, as it does in the mixin's class initializer: it runs
	 * straight through (no jumps), keeps nothing in local variables, writes no field, and uses nothing of the mixin, nor
	 * members of the enum (which the mixin's code, merged into it, could reach privately).
	 */
	static boolean computable(List<AbstractInsnNode> code, String mixin, String target) {
		String self = "L" + mixin + ";";
		for (AbstractInsnNode insn : code) {
			int op = insn.getOpcode();
			if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode || insn instanceof VarInsnNode
					|| insn instanceof IincInsnNode || insn instanceof InvokeDynamicInsnNode || op >= Opcodes.IRETURN && op <= Opcodes.RETURN
					|| op == Opcodes.ATHROW || op == Opcodes.MONITORENTER || op == Opcodes.MONITOREXIT || op == Opcodes.PUTFIELD
					|| op == Opcodes.PUTSTATIC) return false;
			if (usesMixin(insn, mixin, target, self)) return false;
		}
		return true;
	}

	private static boolean usesMixin(AbstractInsnNode insn, String mixin, String target, String self) {
		if (insn instanceof FieldInsnNode field) return field.owner.equals(mixin) || field.owner.equals(target) || field.desc.contains(self);
		if (insn instanceof MethodInsnNode method) return method.owner.equals(mixin) || method.owner.equals(target) || method.desc.contains(self);
		if (insn instanceof TypeInsnNode type) return type.desc.equals(mixin) || type.desc.contains(self);
		if (insn instanceof MultiANewArrayInsnNode array) return array.desc.contains(self);
		return insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && type.getDescriptor().contains(self);
	}

	/** The entries, the computed ones with the methods {@code owner} gives them, numbered from {@code first}. */
	static List<Entry> named(List<Entry> entries, String owner, int first) {
		List<Entry> named = new ArrayList<>(entries.size());
		int next = first;
		for (Entry e : entries) {
			Computed c = e.computed();
			named.add(c == null ? e : new Entry(e.enumName(), e.name(), e.constructor(), null,
					new Computed(c.code(), c.loaded(), c.supplied(), owner, "arguments" + next++)));
		}
		return named;
	}

	/**
	 * The class {@code owner}, and the supplier class it uses if it does, by internal name: for each computed entry it holds, the
	 * method NeoForge's loader calls for each of the constant's arguments. Each runs the mixin's code for them, then
	 * returns the one asked for, boxed, in a supplier of it where the constructor takes one.
	 */
	static Map<String, byte[]> argumentsClasses(String owner, List<Entry> entries) {
		String value = owner + "$Value";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, owner, null, "java/lang/Object", null);
		for (Entry entry : entries) {
			Computed c = entry.computed();
			if (c == null || !owner.equals(c.owner())) continue;
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, c.method(), ARGUMENTS_METHOD, null, null);
			mv.visitCode();
			for (AbstractInsnNode insn : c.code()) insn.accept(mv);
			// Straight through: the values into locals (after the index and type), then into an array, boxed.
			Type[] types = Type.getArgumentTypes(c.loaded());
			int[] slots = new int[types.length];
			for (int i = 0, slot = 2; i < types.length; slot += types[i].getSize(), i++) slots[i] = slot;
			for (int i = types.length - 1; i >= 0; i--) mv.visitVarInsn(types[i].getOpcode(Opcodes.ISTORE), slots[i]);
			mv.visitLdcInsn(types.length);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
			for (int i = 0; i < types.length; i++) {
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn(i);
				mv.visitVarInsn(types[i].getOpcode(Opcodes.ILOAD), slots[i]);
				box(mv, types[i]);
				if (c.supplied()[i]) {
					mv.visitTypeInsn(Opcodes.NEW, value);
					mv.visitInsn(Opcodes.DUP_X1);
					mv.visitInsn(Opcodes.SWAP);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, value, "<init>", "(Ljava/lang/Object;)V", false);
				}
				mv.visitInsn(Opcodes.AASTORE);
			}
			mv.visitVarInsn(Opcodes.ILOAD, 0);
			mv.visitInsn(Opcodes.AALOAD);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		cw.visitEnd();
		Map<String, byte[]> classes = new LinkedHashMap<>();
		classes.put(owner, cw.toByteArray());
		boolean supplies = false;
		for (Entry e : entries) {
			if (e.computed() != null && owner.equals(e.computed().owner())) for (boolean b : e.computed().supplied()) supplies |= b;
		}
		if (supplies) classes.put(value, supplierClass(value));
		return classes;
	}

	/** A supplier of the value it is made with. */
	private static byte[] supplierClass(String name) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object",
				new String[] {"java/util/function/Supplier"});
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "value", "Ljava/lang/Object;", null, null).visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitFieldInsn(Opcodes.PUTFIELD, name, "value", "Ljava/lang/Object;");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/Object;", null, null);
		get.visitCode();
		get.visitVarInsn(Opcodes.ALOAD, 0);
		get.visitFieldInsn(Opcodes.GETFIELD, name, "value", "Ljava/lang/Object;");
		get.visitInsn(Opcodes.ARETURN);
		get.visitMaxs(0, 0);
		get.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void box(MethodVisitor mv, Type type) {
		String owner = switch (type.getSort()) {
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
		if (owner != null) mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "valueOf", "(" + type.getDescriptor() + ")L" + owner + ";", false);
	}

	/** The file's text: NeoForge's {@code {"entries": [...]}}. */
	static String json(List<Entry> entries) {
		List<Object> list = new ArrayList<>();
		for (Entry entry : entries) {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("enum", entry.enumName());
			e.put("name", entry.name());
			e.put("constructor", entry.constructor());
			Computed c = entry.computed();
			e.put("parameters", c == null ? entry.arguments() : Map.of("class", c.owner(), "method", c.method()));
			list.add(e);
		}
		return Json.write(Map.of("entries", list), true);
	}

	/** The arguments as NeoForge's file gives them for these parameter types, or null where it can't. */
	private static List<Object> arguments(Type[] types, List<Object> values) {
		if (types.length != values.size()) return null;
		List<Object> arguments = new ArrayList<>(values.size());
		for (int i = 0; i < types.length; i++) {
			Object value = values.get(i);
			Object argument = switch (types[i].getSort()) {
				case Type.BOOLEAN -> value instanceof Integer n ? (Object) (n != 0) : NOT_CONSTANT;
				case Type.CHAR -> value instanceof Integer n ? String.valueOf((char) n.intValue()) : NOT_CONSTANT;
				case Type.BYTE, Type.SHORT, Type.INT -> value instanceof Integer ? value : NOT_CONSTANT;
				case Type.LONG -> value instanceof Long ? value : NOT_CONSTANT;
				case Type.FLOAT -> value instanceof Float ? value : NOT_CONSTANT;
				case Type.DOUBLE -> value instanceof Double ? value : NOT_CONSTANT;
				// Of objects, NeoForge's file takes a string or null.
				default -> value == null || value instanceof String && types[i].getDescriptor().equals("Ljava/lang/String;") ? value : NOT_CONSTANT;
			};
			if (argument == NOT_CONSTANT) return null;
			arguments.add(argument);
		}
		return arguments;
	}

	/** The constant an instruction loads ({@code null} for {@code ACONST_NULL}), or {@link #NOT_CONSTANT}. */
	static Object constant(AbstractInsnNode insn) {
		if (insn == null) return NOT_CONSTANT;
		int op = insn.getOpcode();
		if (op == Opcodes.ACONST_NULL) return null;
		if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
		if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) return (long) (op - Opcodes.LCONST_0);
		if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) return (float) (op - Opcodes.FCONST_0);
		if (op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) return (double) (op - Opcodes.DCONST_0);
		if (insn instanceof IntInsnNode push && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) return push.operand;
		if (insn instanceof LdcInsnNode ldc && !(ldc.cst instanceof Type)) return ldc.cst;
		return NOT_CONSTANT;
	}

	/** Whether the instruction belongs to building and storing the values array, or the return. */
	private static boolean valuesArray(AbstractInsnNode insn, String self) {
		return switch (insn.getOpcode()) {
			case Opcodes.RETURN, Opcodes.DUP, Opcodes.AASTORE, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
					Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.BIPUSH, Opcodes.SIPUSH -> true;
			case Opcodes.ANEWARRAY -> ((TypeInsnNode) insn).desc.equals(self);
			case Opcodes.GETSTATIC, Opcodes.PUTSTATIC -> ((FieldInsnNode) insn).owner.equals(self);
			case Opcodes.INVOKESTATIC -> ((MethodInsnNode) insn).owner.equals(self) && ((MethodInsnNode) insn).name.equals("$values");
			default -> false;
		};
	}

	/** The instruction or the next one that is code (not a label, line number or frame). */
	static AbstractInsnNode real(AbstractInsnNode insn) {
		while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
		return insn;
	}
}
