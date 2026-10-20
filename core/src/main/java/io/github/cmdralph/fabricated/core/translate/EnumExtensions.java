package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.json.Json;

/**
 * Turns a Fabric enum mixin that only adds constants to one of NeoForge's extensible enums into entries of a NeoForge
 * enum extension file (ENUM-1). NeoForge's loader adds the constants of such an enum itself, from the files mods name,
 * and lets nothing else change it, so the mixin's constants would never exist. Only a mixin with nothing but constants
 * (no other members, no interfaces), each named after the mod (as NeoForge requires) and built by a constructor the
 * enum allows from constant arguments, is translated; any other stays a pre-flight failure (CORPUS-10).
 */
final class EnumExtensions {
	/** The file in the overlay, which the generated mods.toml names. */
	static final String PATH = "META-INF/fabricated/enum_extensions.json";
	static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";
	private static final String RESERVED_CONSTRUCTOR = "Lnet/neoforged/fml/common/asm/enumextension/ReservedConstructor;";
	/** Every enum constructor starts with the constant's name and ordinal, which NeoForge's entries leave out. */
	private static final String NAME_AND_ORDINAL = "(Ljava/lang/String;I";
	/** What javac gives an enum besides its constants; an enum mixin with anything else is not translated. */
	private static final Set<String> ENUM_METHODS = Set.of("<clinit>", "<init>", "values", "valueOf", "$values");
	private static final Object NOT_CONSTANT = new Object();

	/** One constant: its enum (a descriptor), its name, the constructor without the name and ordinal, its arguments. */
	record Entry(String enumDescriptor, String name, String constructor, List<Object> arguments) {
	}

	private EnumExtensions() {
	}

	/** The one class the mixin's {@code @Mixin} names, or null. */
	static String target(ClassNode mixin) {
		AnnotationNode ann = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (ann == null || Annotations.value(ann, "targets") != null) return null;
		return Annotations.value(ann, "value") instanceof List<?> types && types.size() == 1 && types.getFirst() instanceof Type type
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
		List<Entry> entries = new ArrayList<>();
		AbstractInsnNode insn = real(clinit.instructions.getFirst());
		while (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(mixin.name)) {
			insn = real(insn.getNext());
			if (insn == null || insn.getOpcode() != Opcodes.DUP) return null;
			insn = real(insn.getNext());
			if (!(insn instanceof LdcInsnNode ldc && ldc.cst instanceof String name)) return null;
			insn = real(insn.getNext());
			if (!(constant(insn) instanceof Integer)) return null;
			List<Object> values = new ArrayList<>();
			for (insn = real(insn.getNext()); !(insn instanceof MethodInsnNode); insn = real(insn.getNext())) {
				Object value = constant(insn);
				if (value == NOT_CONSTANT) return null;
				values.add(value);
			}
			MethodInsnNode init = (MethodInsnNode) insn;
			if (init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(mixin.name) || !init.name.equals("<init>")) return null;
			insn = real(insn.getNext());
			if (!(insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTSTATIC && put.owner.equals(mixin.name) && put.name.equals(name))) {
				return null;
			}
			String full = init.desc.replace(self, enumDescriptor);
			if (!full.startsWith(NAME_AND_ORDINAL) || !allowed.contains(full) || !name.toLowerCase(Locale.ROOT).startsWith(modId)) return null;
			String constructor = "(" + full.substring(NAME_AND_ORDINAL.length());
			List<Object> arguments = arguments(Type.getArgumentTypes(constructor), values);
			if (arguments == null) return null;
			entries.add(new Entry(enumDescriptor, name, constructor, arguments));
			insn = real(insn.getNext());
		}
		// Then only the values array (javac's $values(), or ECJ's inline array of the constants) and the return.
		for (; insn != null; insn = real(insn.getNext())) {
			if (!valuesArray(insn, mixin.name)) return null;
		}
		return entries.size() == constants.size() ? entries : null;
	}

	/** The file's text: NeoForge's {@code {"entries": [...]}}. */
	static String json(List<Entry> entries) {
		List<Object> list = new ArrayList<>();
		for (Entry entry : entries) {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("enum", entry.enumDescriptor());
			e.put("name", entry.name());
			e.put("constructor", entry.constructor());
			e.put("parameters", entry.arguments());
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
	private static Object constant(AbstractInsnNode insn) {
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
	private static AbstractInsnNode real(AbstractInsnNode insn) {
		while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
		return insn;
	}
}
