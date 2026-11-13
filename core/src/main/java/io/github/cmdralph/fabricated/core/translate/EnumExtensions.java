package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
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
 * and lets nothing else change it, so the mixin's constants would never exist. Only a mixin with no interfaces and no
 * fields but its constants, each named after the mod (as NeoForge requires), is translated: into an enum NeoForge
 * doesn't give ids (not indexed), and into a named enum only with names already starting with the mod's id and a colon,
 * which NeoForge checks when the enum is made. Any other stays a pre-flight failure (CORPUS-10). Its other methods (an
 * injector into the enum, as Farmer's Delight's into {@code RecipeBookCategories}) stay an ordinary mixin into it
 * ({@link #rest}), which FML doesn't refuse, when they use nothing of the mixin that goes with its constants (CORPUS-65).
 * <p>A constant built by a constructor the enum allows from constant arguments is entered with them. One whose
 * arguments are computed when the class loads (a registered object, a static array), or whose constructor NeoForge
 * reserves for one taking suppliers where the mixin's takes values (as {@code Raid.RaiderType}'s; a supplier of a list
 * where it takes an array, as {@code RecipeBookCategories}'), is entered with a method that computes them as the
 * mixin's class initializer does, which NeoForge's loader calls when it adds the constant: in a class generated for the
 * mod ({@link #argumentsClasses}), and only from code that runs straight through and uses nothing of the mixin itself
 * (ENUM-3).
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
	static final Object NOT_CONSTANT = new Object();

	/** The descriptor of the method NeoForge's loader calls for each argument of a constant (its index, its type). */
	static final String ARGUMENTS_METHOD = "(ILjava/lang/Class;)Ljava/lang/Object;";
	private static final String SUPPLIER = "Ljava/util/function/Supplier;";
	private static final String LIST = "Ljava/util/List;";
	/** {@code Enum}'s methods, which the mixin's other methods may call on its constants (they become the enum's). */
	private static final Set<String> ENUM_OWN = Set.of("name()Ljava/lang/String;", "ordinal()I", "toString()Ljava/lang/String;", "hashCode()I",
			"equals(Ljava/lang/Object;)Z", "compareTo(Ljava/lang/Enum;)I", "getDeclaringClass()Ljava/lang/Class;");
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

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
	 * them (loading the values {@code loaded} describes, a constructor descriptor), each passed as {@code wraps} says;
	 * by the method {@code method} of the class {@code owner} once {@link #named} gives them.
	 */
	record Computed(List<AbstractInsnNode> code, String loaded, Wrap[] wraps, String owner, String method) {
	}

	/** How a value the mixin's code loads reaches the constructor the enum allows. */
	enum Wrap {
		/** As it is. */
		NONE,
		/** In a supplier of it, where the constructor takes a supplier of its type. */
		SUPPLIER,
		/** An array in a supplier of a list of its elements, where the constructor takes a supplier of such a list. */
		LIST_SUPPLIER
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
		List<MethodNode> others = new ArrayList<>();
		for (MethodNode method : mixin.methods) {
			if (method.name.equals("<clinit>")) clinit = method;
			else if (!enumMethod(method, self)) others.add(method);
		}
		if (clinit == null || constants.isEmpty() || !staysMixin(others, mixin.name, target, constants)) return null;
		// The constructors NeoForge allows, with their generic signatures.
		Map<String, String> allowed = new LinkedHashMap<>();
		for (MethodNode ctor : target.methods) {
			if (ctor.name.equals("<init>") && Annotations.find(ctor.invisibleAnnotations, ctor.visibleAnnotations, RESERVED_CONSTRUCTOR) == null) {
				allowed.put(ctor.desc, ctor.signature);
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
			Entry entry = allowed.containsKey(full) ? constantEntry(target.name, name, constructor, code, nameIndex, modId) : null;
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
	 * one taking, wherever the mixin's differs, a supplier of the type of the mixin's (or of a list of its array's
	 * elements), as its generic signature says (NeoForge's own constructors for content registered later); or null.
	 */
	private static Entry computedEntry(String enumName, String name, String constructor, List<AbstractInsnNode> code, Map<String, String> allowed) {
		Type[] own = Type.getArgumentTypes(constructor);
		String chosen = null;
		Wrap[] wraps = null;
		for (Map.Entry<String, String> candidate : allowed.entrySet()) {
			String full = candidate.getKey();
			Type[] types = Type.getArgumentTypes("(" + full.substring(NAME_AND_ORDINAL.length()));
			if (types.length != own.length) continue;
			List<GenericType> generic = parameters(candidate.getValue(), types.length);
			Wrap[] wrap = new Wrap[own.length];
			boolean fits = true;
			for (int i = 0; i < own.length && fits; i++) {
				wrap[i] = types[i].equals(own[i]) ? Wrap.NONE : wrap(own[i], types[i], generic == null ? null : generic.get(i));
				fits = wrap[i] != null;
			}
			if (!fits) continue;
			if (chosen != null) return null; // two fit: ambiguous
			chosen = "(" + full.substring(NAME_AND_ORDINAL.length());
			wraps = wrap;
		}
		return chosen == null ? null : new Entry(enumName, name, chosen, null, new Computed(List.copyOf(code), constructor, wraps, null, null));
	}

	/**
	 * How a value of type {@code own} reaches a parameter of type {@code taken} ({@code generic} its generic type, or
	 * null when the constructor has no signature): in a supplier, when the parameter is a supplier of that type; in a
	 * supplier of a list of its elements, when it is an array and the parameter a supplier of a list of them; or null.
	 * Without a signature the supplier's type isn't known, and nothing is wrapped.
	 */
	private static Wrap wrap(Type own, Type taken, GenericType generic) {
		if (!taken.getDescriptor().equals(SUPPLIER) || generic == null || generic.arguments.size() != 1) return null;
		GenericType supplied = generic.arguments.get(0);
		String erasure = supplied == null ? null : supplied.descriptor();
		if (erasure == null || own.getSort() != Type.OBJECT && own.getSort() != Type.ARRAY) return null;
		if (erasure.equals(own.getDescriptor())) return Wrap.SUPPLIER;
		if (own.getSort() != Type.ARRAY || !erasure.equals(LIST) || supplied.arguments.size() != 1 || supplied.arguments.get(0) == null) return null;
		String element = own.getDescriptor().substring(1);
		return (element.startsWith("L") || element.startsWith("[")) && element.equals(supplied.arguments.get(0).descriptor()) ? Wrap.LIST_SUPPLIER : null;
	}

	/**
	 * A method's parameter types from its generic signature, {@code count} of them: javac's signature of an enum's
	 * constructor leaves out the name and ordinal, which are dropped where it has them. Null without a signature, or when
	 * it doesn't give that many.
	 */
	private static List<GenericType> parameters(String signature, int count) {
		if (signature == null) return null;
		List<GenericType> parameters = new ArrayList<>();
		try {
			new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
				@Override
				public SignatureVisitor visitParameterType() {
					GenericType type = new GenericType();
					parameters.add(type);
					return type;
				}

				@Override
				public SignatureVisitor visitReturnType() {
					return new GenericType();
				}

				@Override
				public SignatureVisitor visitExceptionType() {
					return new GenericType();
				}

				@Override
				public SignatureVisitor visitClassBound() {
					return new GenericType();
				}

				@Override
				public SignatureVisitor visitInterfaceBound() {
					return new GenericType();
				}
			});
		} catch (RuntimeException e) {
			return null; // a malformed signature: nothing is known of the types
		}
		if (parameters.size() == count + 2) return parameters.subList(2, parameters.size());
		return parameters.size() == count ? parameters : null;
	}

	/**
	 * A type of a generic signature, as far as wrapping an argument needs it: its erasure ({@link #descriptor()}, null
	 * for a type variable) and its type arguments (null for a wildcard without a bound or with a lower one).
	 */
	private static final class GenericType extends SignatureVisitor {
		private String erasure;
		private GenericType component;
		final List<GenericType> arguments = new ArrayList<>();

		GenericType() {
			super(Opcodes.ASM9);
		}

		String descriptor() {
			if (component == null) return erasure;
			String inner = component.descriptor();
			return inner == null ? null : "[" + inner;
		}

		@Override
		public void visitBaseType(char descriptor) {
			erasure = String.valueOf(descriptor);
		}

		@Override
		public void visitTypeVariable(String name) {
			erasure = null;
		}

		@Override
		public SignatureVisitor visitArrayType() {
			component = new GenericType();
			return component;
		}

		@Override
		public void visitClassType(String name) {
			erasure = "L" + name + ";";
		}

		@Override
		public void visitInnerClassType(String name) {
			erasure = erasure.substring(0, erasure.length() - 1) + "$" + name + ";";
			arguments.clear();
		}

		@Override
		public void visitTypeArgument() {
			arguments.add(null);
		}

		@Override
		public SignatureVisitor visitTypeArgument(char wildcard) {
			GenericType argument = new GenericType();
			arguments.add(wildcard == SignatureVisitor.SUPER ? null : argument);
			return argument;
		}
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

	/** Whether the method is one javac gives the mixin as an enum: a constructor, its class initializer, {@code values()}, {@code valueOf(String)}. */
	private static boolean enumMethod(MethodNode method, String self) {
		return switch (method.name) {
			case "<init>", "<clinit>" -> true;
			case "values", "$values" -> method.desc.equals("()[" + self) && (method.access & Opcodes.ACC_STATIC) != 0;
			case "valueOf" -> method.desc.equals("(Ljava/lang/String;)" + self) && (method.access & Opcodes.ACC_STATIC) != 0;
			default -> false;
		};
	}

	/**
	 * Whether the mixin's other methods can stay a mixin once its constants go to NeoForge's file ({@link #rest}): none
	 * is abstract without being a shadow, and they use nothing of the mixin that goes with its constants. Of the mixin
	 * they may read its constants (the enum's fields NeoForge adds), call each other, and call what the enum declares or
	 * {@code Enum} gives it; not read its other fields, construct it, nor call {@code Enum}'s methods as its super class.
	 */
	static boolean staysMixin(List<MethodNode> methods, String mixin, ClassNode target, Set<String> constants) {
		String self = "L" + mixin + ";";
		Set<String> own = new HashSet<>();
		for (MethodNode m : methods) own.add(m.name + m.desc);
		for (MethodNode m : methods) {
			if ((m.access & Opcodes.ACC_NATIVE) != 0) return false;
			if ((m.access & Opcodes.ACC_ABSTRACT) != 0 && Annotations.find(m.invisibleAnnotations, m.visibleAnnotations, SHADOW) == null) return false;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof FieldInsnNode field && field.owner.equals(mixin)) {
					if (field.getOpcode() != Opcodes.GETSTATIC || !constants.contains(field.name) || !field.desc.equals(self)) return false;
				} else if (insn instanceof MethodInsnNode call) {
					if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals("java/lang/Enum")) return false;
					if (call.owner.equals(mixin) && !reachable(call.getOpcode(), call.name, call.desc, own, target, self)) return false;
				} else if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(mixin)) {
					return false;
				} else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (Object arg : indy.bsmArgs) {
						if (!(arg instanceof Handle h) || !h.getOwner().equals(mixin)) continue;
						int opcode = switch (h.getTag()) {
							case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
							case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
							case Opcodes.H_INVOKESPECIAL -> Opcodes.INVOKESPECIAL;
							default -> -1; // a field or constructor of the mixin
						};
						if (opcode < 0 || !reachable(opcode, h.getName(), h.getDesc(), own, target, self)) return false;
					}
				}
			}
		}
		return true;
	}

	/** Whether a call of the mixin's method stays valid once Mixin makes it the enum's: one of the methods kept, or the enum's own. */
	private static boolean reachable(int opcode, String name, String desc, Set<String> own, ClassNode target, String self) {
		if (own.contains(name + desc)) return true;
		if (opcode == Opcodes.INVOKESPECIAL) return false;
		String remapped = desc.replace(self, "L" + target.name + ";");
		boolean wantStatic = opcode == Opcodes.INVOKESTATIC;
		for (MethodNode m : target.methods) {
			if (m.name.equals(name) && m.desc.equals(remapped) && ((m.access & Opcodes.ACC_STATIC) != 0) == wantStatic) return true;
		}
		return !wantStatic && ENUM_OWN.contains(name + remapped);
	}

	/**
	 * The mixin without what NeoForge's enum extension file now carries (its constants, their array, its constructors,
	 * javac's enum methods): an abstract mixin class, not an enum, with its other methods as compiled (frames included),
	 * which Mixin applies as it would the enum mixin's, its references to the mixin becoming the enum's (CORPUS-65); null
	 * when it has none. Called only for a mixin {@link #of} translated.
	 */
	static byte[] rest(byte[] mixinBytes) {
		ClassNode node = new ClassNode();
		new ClassReader(mixinBytes).accept(node, 0);
		String self = "L" + node.name + ";";
		node.methods.removeIf(m -> enumMethod(m, self));
		if (node.methods.isEmpty()) return null;
		node.access = node.access & ~(Opcodes.ACC_ENUM | Opcodes.ACC_FINAL) | Opcodes.ACC_ABSTRACT;
		node.superName = "java/lang/Object";
		node.signature = null;
		node.fields.clear();
		ClassWriter cw = new ClassWriter(0);
		node.accept(cw);
		return cw.toByteArray();
	}

	/** The entries, the computed ones with the methods {@code owner} gives them, numbered from {@code first}. */
	static List<Entry> named(List<Entry> entries, String owner, int first) {
		List<Entry> named = new ArrayList<>(entries.size());
		int next = first;
		for (Entry e : entries) {
			Computed c = e.computed();
			named.add(c == null ? e : new Entry(e.enumName(), e.name(), e.constructor(), null,
					new Computed(c.code(), c.loaded(), c.wraps(), owner, "arguments" + next++)));
		}
		return named;
	}

	/**
	 * The class {@code owner}, and the supplier class it uses if it does, by internal name: for each computed entry it holds, the
	 * method NeoForge's loader calls for each of the constant's arguments. Each runs the mixin's code for them, then
	 * returns the one asked for, boxed, in a supplier of it (or of a list of an array's elements) where the constructor
	 * takes one.
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
				if (c.wraps()[i] == Wrap.LIST_SUPPLIER) {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "([Ljava/lang/Object;)Ljava/util/List;", true);
				}
				if (c.wraps()[i] != Wrap.NONE) {
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
			if (e.computed() != null && owner.equals(e.computed().owner())) for (Wrap w : e.computed().wraps()) supplies |= w != Wrap.NONE;
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
