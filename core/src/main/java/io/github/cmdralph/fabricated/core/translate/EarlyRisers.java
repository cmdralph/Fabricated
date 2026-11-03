package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Fabric ASM's enum additions, read from the early risers that declare them (CORPUS-80: Farmer's Delight's recipe book
 * types on Forge 47). Fabric ASM (Manningham Mills, mod id {@code mm}) runs the mods' {@code mm:early_risers}
 * entrypoints from its Mixin config plugin before the game's classes load; their
 * {@code ClassTinkerers.enumBuilder(...).addEnum(...).build()} calls declare constants that it then adds to the enums as
 * they load, through mixins it serves from Fabric's class loader. That plugin can't run on NeoForge or Forge, whose class
 * loaders take no added URLs ({@code Plugin.fishAddURL}: "Couldn't find method in TransformingClassLoader", and Mixin drops
 * its config), so the constants were never made, and the mods failed on them ({@code ClassTinkerers.getEnum}, which looks
 * a constant up by name). Fabricated reads the calls from the early risers' code instead, without running it, and makes
 * each enum's additions an enum mixin as javac would compile one ({@link #mixinClass}); the platform's own way with enum
 * mixins takes them from there (NeoForge's enum extension file, ENUM-1; an ordinary mixin, ENUM-5 and ENUM-7). Read from
 * Fabric ASM's documentation (its README), not its code, which is MPL-2.0.
 * <p>Only code that runs straight through to the calls on constants is read: strings, class literals and the primitive
 * classes, boxed constants, arrays of them, {@code MappingResolver.mapClassName} from intermediary names, string
 * concatenation, and the side ({@code getEnvironmentType}), whose branches are followed for this side. A constant's
 * arguments are constants, or come from a supplier: one a static method of the mod returns, or a lambda capturing
 * nothing, whose code runs as the enum is initialized, as Fabric ASM's does. Anything else (another of Fabric ASM's
 * tools, a call of other code, a loop) leaves the early riser unread, and the report says why.
 */
final class EarlyRisers {
	static final String ENTRYPOINT = "mm:early_risers";
	/** Fabric ASM's Mixin config plugin, which runs the early risers on Fabric. */
	static final String PLUGIN = "com.chocohead.mm.Plugin";
	/** Fabric ASM's access transformer in a mod's jar, which its plugin applies on Fabric. */
	static final String ACCESS_TRANSFORMER = "silky.at";
	private static final String TINKERERS = "com/chocohead/mm/api/ClassTinkerers";
	private static final String ADDER = "com/chocohead/mm/api/EnumAdder";
	private static final String ADDER_DESC = "L" + ADDER + ";";
	private static final String LOADER = "net/fabricmc/loader/api/FabricLoader";
	private static final String RESOLVER = "net/fabricmc/loader/api/MappingResolver";
	private static final String ENV_TYPE = "net/fabricmc/api/EnvType";
	private static final String SUPPLIER = "java/util/function/Supplier";
	private static final String SUPPLIER_DESC = "L" + SUPPLIER + ";";
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String NAME_AND_ORDINAL = "(Ljava/lang/String;I";
	/** The helper's method giving a constant's argument (or, at -1, checking them); one per constant. */
	static final String ARGUMENT = "fabricated$argument";
	/** Enough for any early riser declaring constants; a loop runs past it. */
	private static final int STEPS = 20_000;
	private static final Map<String, Type> PRIMITIVE_CLASSES = Map.of("java/lang/Integer", Type.INT_TYPE, "java/lang/Long", Type.LONG_TYPE,
			"java/lang/Boolean", Type.BOOLEAN_TYPE, "java/lang/Byte", Type.BYTE_TYPE, "java/lang/Short", Type.SHORT_TYPE, "java/lang/Character",
			Type.CHAR_TYPE, "java/lang/Float", Type.FLOAT_TYPE, "java/lang/Double", Type.DOUBLE_TYPE);

	/**
	 * A constant to add.
	 *
	 * @param target     the enum, by internal name, as the game names it here
	 * @param parameters its constructor's parameter types after the name and ordinal
	 */
	record Addition(String target, List<Type> parameters, String name, Arguments arguments) {
		String constructor() {
			return Type.getMethodDescriptor(Type.VOID_TYPE, parameters.toArray(Type[]::new)).replace("(", NAME_AND_ORDINAL);
		}
	}

	/** Where a constant's arguments come from. */
	sealed interface Arguments permits Given, Supplied {
	}

	/** Constants: strings, boxed primitives, classes ({@link Type}) and nulls, one per parameter. */
	record Given(List<Object> values) implements Arguments {
	}

	/**
	 * A supplier of the arguments array: the static method {@code owner.name desc} returns it ({@code factory}), or is
	 * the code of a lambda that returns the array itself.
	 */
	record Supplied(String owner, String name, String desc, boolean factory, boolean itf) implements Arguments {
	}

	/** An early riser's additions for this side, or why it couldn't be read ({@code unread}, else null). */
	record Read(String riser, List<Addition> additions, String unread) {
	}

	/** A reason the early riser isn't read. */
	private static final class Unread extends Exception {
		Unread(String message) {
			super(message, null, false, false);
		}
	}

	private enum Token {
		NULL, LOADER, RESOLVER, CLIENT, SERVER
	}

	private record Str(String value) {
	}

	private record Num(Object value) {
	}

	private record ClassValue(Type type) {
	}

	private record SupplierValue(Supplied supplied) {
	}

	private record ArrayValue(Type element, Object[] slots) {
	}

	private static final class Builder {
		final String target;
		final List<Type> parameters;
		final List<Addition> additions = new ArrayList<>();

		Builder(String target, List<Type> parameters) {
			this.target = target;
			this.parameters = parameters;
		}
	}

	private EarlyRisers() {
	}

	/**
	 * Reads one early riser.
	 *
	 * @param entry    the entrypoint as {@code fabric.mod.json} gives it: a class implementing {@code Runnable}, or
	 *                 {@code Class::method}, a static method
	 * @param classes  the mod's classes by internal name, or null
	 * @param client   whether this is a client
	 * @param mapClass the game's internal name for one in the mod's names (its intermediary ones), else unchanged
	 */
	static Read read(String entry, Function<String, ClassNode> classes, boolean client, UnaryOperator<String> mapClass) {
		int sep = entry.indexOf("::");
		String owner = (sep < 0 ? entry : entry.substring(0, sep)).replace('.', '/');
		String riser = entry;
		ClassNode node = classes.apply(owner);
		if (node == null) return new Read(riser, List.of(), "its class " + owner.replace('/', '.') + " isn't in the mod");
		MethodNode run = null;
		for (MethodNode m : node.methods) {
			boolean wanted = sep < 0 ? m.name.equals("run") && m.desc.equals("()V") && (m.access & Opcodes.ACC_STATIC) == 0
					: m.name.equals(entry.substring(sep + 2)) && m.desc.equals("()V") && (m.access & Opcodes.ACC_STATIC) != 0;
			if (wanted) run = m;
		}
		if (run == null || run.instructions.size() == 0) return new Read(riser, List.of(), "it has no code to run here");
		if (!run.tryCatchBlocks.isEmpty()) return new Read(riser, List.of(), "it catches exceptions");
		if (sep < 0 && !plainConstructor(node)) return new Read(riser, List.of(), "its constructor does more than make the object");
		try {
			return new Read(riser, interpret(run, classes, client, mapClass), null);
		} catch (Unread e) {
			return new Read(riser, List.of(), e.getMessage());
		}
	}

	/** Whether the class's constructors only call {@code Object}'s (Fabric makes the early riser with one). */
	private static boolean plainConstructor(ClassNode node) {
		if (!"java/lang/Object".equals(node.superName)) return false;
		for (MethodNode m : node.methods) {
			if (!m.name.equals("<init>")) continue;
			for (AbstractInsnNode insn : m.instructions) {
				int op = insn.getOpcode();
				if (op < 0 || op == Opcodes.ALOAD || op == Opcodes.RETURN) continue;
				if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals("java/lang/Object")
						&& call.name.equals("<init>")) continue;
				return false;
			}
		}
		return true;
	}

	private static List<Addition> interpret(MethodNode run, Function<String, ClassNode> classes, boolean client, UnaryOperator<String> mapClass)
			throws Unread {
		List<Addition> additions = new ArrayList<>();
		Map<Integer, Object> locals = new HashMap<>();
		Deque<Object> stack = new ArrayDeque<>();
		AbstractInsnNode insn = run.instructions.getFirst();
		for (int steps = 0; insn != null; steps++) {
			if (steps > STEPS) throw new Unread("it runs too long to read (a loop)");
			int op = insn.getOpcode();
			AbstractInsnNode next = insn.getNext();
			if (op < 0 || op == Opcodes.NOP || op == Opcodes.CHECKCAST) {
				insn = next;
				continue;
			}
			Object constant = EnumExtensions.constant(insn);
			if (constant != EnumExtensions.NOT_CONSTANT) {
				stack.push(constant == null ? Token.NULL : constant instanceof String s ? new Str(s) : new Num(constant));
			} else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) {
				stack.push(new ClassValue(type));
			} else if (insn instanceof VarInsnNode var && op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) {
				Object value = locals.get(var.var);
				if (value == null) throw new Unread("it reads a local it didn't set");
				stack.push(value);
			} else if (insn instanceof VarInsnNode var && op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
				locals.put(var.var, pop(stack));
			} else if (op == Opcodes.POP) {
				pop(stack);
			} else if (op == Opcodes.DUP) {
				stack.push(peek(stack));
			} else if (insn instanceof TypeInsnNode array && op == Opcodes.ANEWARRAY) {
				stack.push(new ArrayValue(Type.getObjectType(array.desc), new Object[index(pop(stack))]));
			} else if (op == Opcodes.AASTORE) {
				Object value = pop(stack);
				int index = index(pop(stack));
				if (!(pop(stack) instanceof ArrayValue array) || index >= array.slots().length) throw new Unread("it stores into an array it doesn't make");
				array.slots()[index] = value;
			} else if (insn instanceof FieldInsnNode field && op == Opcodes.GETSTATIC) {
				stack.push(staticField(field));
			} else if (insn instanceof MethodInsnNode call) {
				call(call, stack, additions, classes, client, mapClass);
			} else if (insn instanceof InvokeDynamicInsnNode indy) {
				stack.push(dynamic(indy, stack, classes));
			} else if (insn instanceof JumpInsnNode jump) {
				next = jumps(jump, stack) ? jump.label : next;
			} else if (op == Opcodes.RETURN) {
				return additions;
			} else {
				throw new Unread("it runs an instruction it isn't read for (opcode " + op + ")");
			}
			insn = next;
		}
		throw new Unread("it doesn't return");
	}

	private static Object pop(Deque<Object> stack) throws Unread {
		if (stack.isEmpty()) throw new Unread("it uses a value it didn't make");
		return stack.pop();
	}

	private static Object peek(Deque<Object> stack) throws Unread {
		if (stack.isEmpty()) throw new Unread("it uses a value it didn't make");
		return stack.peek();
	}

	private static int index(Object value) throws Unread {
		if (value instanceof Num n && n.value() instanceof Integer i && i >= 0 && i < 1_000) return i;
		throw new Unread("it makes or fills an array by a number it doesn't give");
	}

	private static Object staticField(FieldInsnNode field) throws Unread {
		if (field.owner.equals(ENV_TYPE) && field.name.equals("CLIENT")) return Token.CLIENT;
		if (field.owner.equals(ENV_TYPE) && field.name.equals("SERVER")) return Token.SERVER;
		Type primitive = PRIMITIVE_CLASSES.get(field.owner);
		if (primitive != null && field.name.equals("TYPE")) return new ClassValue(primitive);
		throw new Unread("it reads " + field.owner.replace('/', '.') + "." + field.name);
	}

	/** Whether the jump is taken; only on values it knows. */
	private static boolean jumps(JumpInsnNode jump, Deque<Object> stack) throws Unread {
		int op = jump.getOpcode();
		if (op == Opcodes.GOTO) return true;
		if (op == Opcodes.IF_ACMPEQ || op == Opcodes.IF_ACMPNE) {
			Object b = pop(stack);
			Object a = pop(stack);
			if (!(a instanceof Token) || !(b instanceof Token)) throw new Unread("it compares values it doesn't know");
			return (a == b) == (op == Opcodes.IF_ACMPEQ);
		}
		if (op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) return (pop(stack) == Token.NULL) == (op == Opcodes.IFNULL);
		if (op == Opcodes.IFEQ || op == Opcodes.IFNE) {
			Object value = pop(stack);
			if (!(value instanceof Num n) || !(n.value() instanceof Integer i)) throw new Unread("it tests a value it doesn't know");
			return (i == 0) == (op == Opcodes.IFEQ);
		}
		throw new Unread("it branches on a value it doesn't know");
	}

	private static void call(MethodInsnNode call, Deque<Object> stack, List<Addition> additions, Function<String, ClassNode> classes, boolean client,
			UnaryOperator<String> mapClass) throws Unread {
		String owner = call.owner;
		String name = call.name;
		String desc = call.desc;
		if (owner.equals(LOADER) && name.equals("getInstance") && desc.equals("()L" + LOADER + ";")) {
			stack.push(Token.LOADER);
			return;
		}
		if (owner.equals(LOADER) && call.getOpcode() == Opcodes.INVOKEINTERFACE) {
			if (pop(stack) != Token.LOADER) throw new Unread("it asks Fabric Loader of an object it doesn't know");
			switch (name + desc) {
				case "getMappingResolver()L" + RESOLVER + ";" -> stack.push(Token.RESOLVER);
				case "getEnvironmentType()L" + ENV_TYPE + ";" -> stack.push(client ? Token.CLIENT : Token.SERVER);
				case "isDevelopmentEnvironment()Z" -> stack.push(new Num(0));
				default -> throw new Unread("it calls FabricLoader." + name);
			}
			return;
		}
		if (owner.equals(RESOLVER) && name.equals("mapClassName") && desc.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
			String className = string(pop(stack));
			String namespace = string(pop(stack));
			if (pop(stack) != Token.RESOLVER) throw new Unread("it maps a name with an object it doesn't know");
			if (!namespace.equals("intermediary")) throw new Unread("it maps a name from the " + namespace + " namespace");
			stack.push(new Str(mapClass.apply(className.replace('.', '/')).replace('/', '.')));
			return;
		}
		if (call.getOpcode() == Opcodes.INVOKESTATIC && name.equals("valueOf") && PRIMITIVE_CLASSES.containsKey(owner)
				&& desc.equals("(" + PRIMITIVE_CLASSES.get(owner).getDescriptor() + ")L" + owner + ";")) {
			Object value = pop(stack);
			if (!(value instanceof Num n)) throw new Unread("it boxes a value it doesn't know");
			stack.push(new Num(boxed(PRIMITIVE_CLASSES.get(owner), n.value())));
			return;
		}
		if (owner.equals(TINKERERS)) {
			if (!name.equals("enumBuilder")) throw new Unread("it uses Fabric ASM's ClassTinkerers." + name + ", which Fabricated doesn't run");
			List<Type> parameters = List.of();
			if (!desc.equals("(Ljava/lang/String;)" + ADDER_DESC)) {
				if (!desc.equals("(Ljava/lang/String;[Ljava/lang/Class;)" + ADDER_DESC) && !desc.equals("(Ljava/lang/String;[Ljava/lang/String;)" + ADDER_DESC)
						&& !desc.equals("(Ljava/lang/String;[Ljava/lang/Object;)" + ADDER_DESC)) {
					throw new Unread("it calls ClassTinkerers.enumBuilder" + desc);
				}
				if (!(pop(stack) instanceof ArrayValue types)) throw new Unread("it gives enumBuilder parameter types it doesn't know");
				parameters = new ArrayList<>();
				for (Object type : types.slots()) parameters.add(parameterType(type, mapClass));
			}
			String target = mapClass.apply(string(pop(stack)).replace('.', '/'));
			stack.push(new Builder(target, List.copyOf(parameters)));
			return;
		}
		if (owner.equals(ADDER)) {
			switch (name + desc) {
				case "addEnum(Ljava/lang/String;[Ljava/lang/Object;)" + ADDER_DESC -> {
					if (!(pop(stack) instanceof ArrayValue values)) throw new Unread("it gives addEnum arguments it doesn't know");
					String constant = string(pop(stack));
					Builder builder = builder(pop(stack));
					builder.additions.add(new Addition(builder.target, builder.parameters, constant, given(values, builder.parameters, constant)));
					stack.push(builder);
				}
				case "addEnum(Ljava/lang/String;" + SUPPLIER_DESC + ")" + ADDER_DESC -> {
					if (!(pop(stack) instanceof SupplierValue supplier)) throw new Unread("it gives addEnum a supplier it doesn't know");
					String constant = string(pop(stack));
					Builder builder = builder(pop(stack));
					builder.additions.add(new Addition(builder.target, builder.parameters, constant, supplier.supplied()));
					stack.push(builder);
				}
				case "build()V" -> additions.addAll(builder(pop(stack)).additions);
				default -> throw new Unread("it uses Fabric ASM's EnumAdder." + name + ", which Fabricated doesn't translate");
			}
			return;
		}
		// A supplier a static method of the mod returns (Farmer's Delight's getSearchCategoryStacks).
		if (call.getOpcode() == Opcodes.INVOKESTATIC && desc.equals("()" + SUPPLIER_DESC) && declaresStatic(classes, owner, name, desc)) {
			stack.push(new SupplierValue(new Supplied(owner, name, desc, true, call.itf)));
			return;
		}
		throw new Unread("it calls " + owner.replace('/', '.') + "." + name);
	}

	private static boolean declaresStatic(Function<String, ClassNode> classes, String owner, String name, String desc) {
		ClassNode node = classes.apply(owner);
		if (node == null) return false;
		for (MethodNode m : node.methods) {
			if (m.name.equals(name) && m.desc.equals(desc) && (m.access & Opcodes.ACC_STATIC) != 0 && m.instructions.size() > 0) return true;
		}
		return false;
	}

	/** A string concatenation of known values, or a lambda supplier capturing nothing. */
	private static Object dynamic(InvokeDynamicInsnNode indy, Deque<Object> stack, Function<String, ClassNode> classes) throws Unread {
		Handle bsm = indy.bsm;
		if (bsm.getOwner().equals("java/lang/invoke/StringConcatFactory") && bsm.getName().equals("makeConcatWithConstants")
				&& indy.bsmArgs.length >= 1 && indy.bsmArgs[0] instanceof String recipe) {
			Type[] args = Type.getArgumentTypes(indy.desc);
			String[] values = new String[args.length];
			for (int i = args.length - 1; i >= 0; i--) {
				Object value = pop(stack);
				if (value instanceof Str s) values[i] = s.value();
				else if (value instanceof Num n) values[i] = String.valueOf(n.value());
				else throw new Unread("it joins strings with a value it doesn't know");
			}
			StringBuilder out = new StringBuilder();
			int arg = 0;
			int constant = 1;
			for (char c : recipe.toCharArray()) {
				if (c == '\u0001') {
					if (arg >= values.length) throw new Unread("it joins strings it doesn't give");
					out.append(values[arg++]);
				} else if (c == '\u0002') {
					if (constant >= indy.bsmArgs.length) throw new Unread("it joins strings it doesn't give");
					out.append(indy.bsmArgs[constant++]);
				} else {
					out.append(c);
				}
			}
			return new Str(out.toString());
		}
		if (bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && indy.name.equals("get") && indy.desc.equals("()" + SUPPLIER_DESC)
				&& indy.bsmArgs.length >= 3 && indy.bsmArgs[1] instanceof Handle impl && impl.getTag() == Opcodes.H_INVOKESTATIC
				&& Type.getArgumentTypes(impl.getDesc()).length == 0 && Type.getReturnType(impl.getDesc()).getSort() >= Type.ARRAY
				&& declaresStatic(classes, impl.getOwner(), impl.getName(), impl.getDesc())) {
			return new SupplierValue(new Supplied(impl.getOwner(), impl.getName(), impl.getDesc(), false, impl.isInterface()));
		}
		throw new Unread("it makes a value with invokedynamic it isn't read for (" + bsm.getOwner().replace('/', '.') + "." + bsm.getName() + ")");
	}

	private static String string(Object value) throws Unread {
		if (value instanceof Str s) return s.value();
		throw new Unread("it gives a string it doesn't know");
	}

	private static Builder builder(Object value) throws Unread {
		if (value instanceof Builder b) return b;
		throw new Unread("it adds constants with a builder it doesn't know");
	}

	/** A parameter type given as a class, or as a name (internal, dotted, or an array's descriptor). */
	private static Type parameterType(Object value, UnaryOperator<String> mapClass) throws Unread {
		if (value instanceof ClassValue c) return mapped(c.type(), mapClass);
		if (value instanceof Str s) {
			String name = s.value().replace('.', '/');
			if (name.length() == 1 && "ZBCSIJFD".contains(name)) return Type.getType(name);
			if (name.startsWith("[") || name.startsWith("L") && name.endsWith(";")) {
				try {
					return mapped(Type.getType(name), mapClass);
				} catch (IllegalArgumentException e) {
					throw new Unread("it names a parameter type " + s.value() + " it can't read");
				}
			}
			return Type.getObjectType(mapClass.apply(name));
		}
		throw new Unread("it gives a parameter type it doesn't know");
	}

	private static Type mapped(Type type, UnaryOperator<String> mapClass) {
		if (type.getSort() == Type.OBJECT) return Type.getObjectType(mapClass.apply(type.getInternalName()));
		if (type.getSort() != Type.ARRAY) return type;
		return Type.getType("[".repeat(type.getDimensions()) + mapped(type.getElementType(), mapClass).getDescriptor());
	}

	private static Object boxed(Type primitive, Object value) throws Unread {
		if (!(value instanceof Number n)) throw new Unread("it boxes a value it doesn't know");
		int sort = primitive.getSort();
		if (sort == Type.BOOLEAN) return Boolean.valueOf(n.intValue() != 0);
		if (sort == Type.CHAR) return Character.valueOf((char) n.intValue());
		if (sort == Type.BYTE) return Byte.valueOf(n.byteValue());
		if (sort == Type.SHORT) return Short.valueOf(n.shortValue());
		if (sort == Type.INT) return Integer.valueOf(n.intValue());
		if (sort == Type.LONG) return Long.valueOf(n.longValue());
		if (sort == Type.FLOAT) return Float.valueOf(n.floatValue());
		return Double.valueOf(n.doubleValue());
	}

	/** The given arguments, one per parameter, each one the parameter takes. */
	private static Given given(ArrayValue array, List<Type> parameters, String constant) throws Unread {
		if (array.slots().length != parameters.size()) {
			throw new Unread("it gives " + constant + " " + array.slots().length + " argument(s) for a constructor taking " + parameters.size());
		}
		List<Object> values = new ArrayList<>();
		for (int i = 0; i < parameters.size(); i++) {
			Object slot = array.slots()[i];
			Object value = slot == null || slot == Token.NULL ? null : slot instanceof Str s ? s.value() : slot instanceof Num n ? n.value()
					: slot instanceof ClassValue c ? c.type() : Token.NULL;
			if (value == Token.NULL || !takes(parameters.get(i), value)) {
				throw new Unread("it gives " + constant + " an argument " + (i + 1) + " its constructor doesn't take as it is");
			}
			values.add(value);
		}
		return new Given(values);
	}

	/** Whether a constant value can be passed to the parameter as it is (unboxed for a primitive of its own kind). */
	private static boolean takes(Type parameter, Object value) {
		String name = parameter.getSort() == Type.OBJECT ? parameter.getInternalName() : null;
		if (value == null) return parameter.getSort() == Type.OBJECT || parameter.getSort() == Type.ARRAY;
		if (parameter.getSort() != Type.OBJECT && parameter.getSort() != Type.ARRAY) return parameter.equals(primitiveOf(value));
		if (name == null) return false;
		if (name.equals("java/lang/Object") || name.equals("java/io/Serializable")) return true;
		if (value instanceof String) return name.equals("java/lang/String") || name.equals("java/lang/CharSequence") || name.equals("java/lang/Comparable");
		if (value instanceof Type) return name.equals("java/lang/Class");
		Type primitive = primitiveOf(value);
		String box = primitive == null ? null : boxOf(primitive);
		return name.equals(box) || name.equals("java/lang/Comparable") || name.equals("java/lang/Number") && value instanceof Number;
	}

	private static Type primitiveOf(Object value) {
		if (value instanceof Integer) return Type.INT_TYPE;
		if (value instanceof Long) return Type.LONG_TYPE;
		if (value instanceof Float) return Type.FLOAT_TYPE;
		if (value instanceof Double) return Type.DOUBLE_TYPE;
		if (value instanceof Boolean) return Type.BOOLEAN_TYPE;
		if (value instanceof Character) return Type.CHAR_TYPE;
		if (value instanceof Byte) return Type.BYTE_TYPE;
		if (value instanceof Short) return Type.SHORT_TYPE;
		return null;
	}

	private static String boxOf(Type primitive) {
		for (Map.Entry<String, Type> e : PRIMITIVE_CLASSES.entrySet()) {
			if (e.getValue().equals(primitive)) return e.getKey();
		}
		return null;
	}

	/**
	 * An enum mixin into {@code target} declaring its additions, as javac compiles {@code @Mixin(Target.class) enum
	 * Name { A(...), B(...); Name(...) {} }}: each constant is made in the class initializer from its arguments, the given
	 * ones as constants and the supplied ones from the {@link #ARGUMENT} methods of the helper class {@code arguments}
	 * names for the constant (numbered in its order there), then the values array.
	 */
	static byte[] mixinClass(String name, String target, List<Addition> additions, Map<Addition, String> arguments) {
		String self = "L" + name + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC, name,
				"Ljava/lang/Enum<" + self + ">;", "java/lang/Enum", null);
		AnnotationVisitor mixin = cw.visitAnnotation(MIXIN, false);
		AnnotationVisitor targets = mixin.visitArray("value");
		targets.visit(null, Type.getObjectType(target));
		targets.visitEnd();
		mixin.visitEnd();
		for (Addition a : additions) {
			cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, a.name(), self, null, null).visitEnd();
		}
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + self, null, null).visitEnd();
		Set<String> constructors = new LinkedHashSet<>();
		for (Addition a : additions) constructors.add(a.constructor());
		for (String constructor : constructors) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constructor, null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitVarInsn(Opcodes.ILOAD, 2);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		MethodVisitor values = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$values", "()[" + self, null, null);
		values.visitCode();
		push(values, additions.size());
		values.visitTypeInsn(Opcodes.ANEWARRAY, name);
		for (int i = 0; i < additions.size(); i++) {
			values.visitInsn(Opcodes.DUP);
			push(values, i);
			values.visitFieldInsn(Opcodes.GETSTATIC, name, additions.get(i).name(), self);
			values.visitInsn(Opcodes.AASTORE);
		}
		values.visitInsn(Opcodes.ARETURN);
		values.visitMaxs(0, 0);
		values.visitEnd();

		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		for (int i = 0; i < additions.size(); i++) {
			Addition a = additions.get(i);
			clinit.visitTypeInsn(Opcodes.NEW, name);
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitLdcInsn(a.name());
			push(clinit, i);
			if (a.arguments() instanceof Given given) {
				for (int p = 0; p < a.parameters().size(); p++) given(clinit, a.parameters().get(p), given.values().get(p));
			} else {
				String helper = arguments.get(a);
				String method = ARGUMENT + "$" + helperIndex(arguments, a);
				if (a.parameters().isEmpty()) {
					// Fabric ASM runs the supplier, and checks it gives no argument.
					push(clinit, -1);
					clinit.visitMethodInsn(Opcodes.INVOKESTATIC, helper, method, "(I)Ljava/lang/Object;", false);
					clinit.visitInsn(Opcodes.POP);
				}
				for (int p = 0; p < a.parameters().size(); p++) {
					push(clinit, p);
					clinit.visitMethodInsn(Opcodes.INVOKESTATIC, helper, method, "(I)Ljava/lang/Object;", false);
					unbox(clinit, a.parameters().get(p));
				}
			}
			clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", a.constructor(), false);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, a.name(), self);
		}
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, name, "$values", "()[" + self, false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, "$VALUES", "[" + self);
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The addition's number among those of its helper class (the order the map gives them). */
	private static int helperIndex(Map<Addition, String> arguments, Addition addition) {
		String helper = arguments.get(addition);
		int n = 0;
		for (Map.Entry<Addition, String> e : arguments.entrySet()) {
			if (e.getKey() == addition) return n;
			if (e.getValue().equals(helper)) n++;
		}
		throw new IllegalStateException("no helper for " + addition.name());
	}

	/** Loads a given argument as its parameter takes it. */
	private static void given(MethodVisitor mv, Type parameter, Object value) {
		if (value == null) {
			mv.visitInsn(Opcodes.ACONST_NULL);
			return;
		}
		if (value instanceof Type type) {
			mv.visitLdcInsn(type);
			return;
		}
		if (value instanceof String s) {
			mv.visitLdcInsn(s);
			return;
		}
		Type primitive = primitiveOf(value);
		Object pushed = value instanceof Boolean b ? (Integer) (b ? 1 : 0) : value instanceof Character c ? (Integer) (int) c
				: value instanceof Byte || value instanceof Short ? (Integer) ((Number) value).intValue() : value;
		mv.visitLdcInsn(pushed);
		if (parameter.getSort() == Type.OBJECT) {
			String box = boxOf(primitive);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, box, "valueOf", "(" + primitive.getDescriptor() + ")L" + box + ";", false);
		}
	}

	/** Casts an argument the helper gives to its parameter's type, unboxing a primitive. */
	private static void unbox(MethodVisitor mv, Type parameter) {
		if (parameter.getSort() == Type.OBJECT || parameter.getSort() == Type.ARRAY) {
			if (!parameter.getInternalName().equals("java/lang/Object")) {
				mv.visitTypeInsn(Opcodes.CHECKCAST, parameter.getSort() == Type.ARRAY ? parameter.getDescriptor() : parameter.getInternalName());
			}
			return;
		}
		String box = boxOf(parameter);
		mv.visitTypeInsn(Opcodes.CHECKCAST, box);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, box, parameter.getClassName() + "Value", "()" + parameter.getDescriptor(), false);
	}

	/**
	 * The class giving the supplied arguments of {@code supplied}'s constants (in that order), next to their suppliers'
	 * class, so that it may call them once they are no longer private ({@link #withReachableSuppliers}): its
	 * {@link #ARGUMENT}{@code $n(int)} runs constant {@code n}'s supplier at its first argument, checks it gives an
	 * array of one value per parameter (Fabric ASM's {@code IllegalArgumentException} otherwise), and gives each in turn,
	 * forgetting the array after the last; -1 checks only.
	 */
	static byte[] helperClass(String name, List<Addition> supplied) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				return "java/lang/Object";
			}
		};
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object", null);
		for (int n = 0; n < supplied.size(); n++) {
			Addition a = supplied.get(n);
			Supplied s = (Supplied) a.arguments();
			String field = "arguments$" + n;
			cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, field, "[Ljava/lang/Object;", null, null).visitEnd();
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, ARGUMENT + "$" + n, "(I)Ljava/lang/Object;", null, null);
			mv.visitCode();
			Label have = new Label();
			mv.visitFieldInsn(Opcodes.GETSTATIC, name, field, "[Ljava/lang/Object;");
			mv.visitJumpInsn(Opcodes.IFNONNULL, have);
			// arguments$n = check(supplier.get())
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, s.owner(), s.name(), s.desc(), s.itf());
			if (s.factory()) mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, SUPPLIER, "get", "()Ljava/lang/Object;", true);
			mv.visitIntInsn(Opcodes.SIPUSH, a.parameters().size());
			mv.visitLdcInsn("Fabricated: Fabric ASM's supplier of the arguments of " + a.target().replace('/', '.') + "." + a.name()
					+ " gives no array of " + a.parameters().size() + " argument(s)");
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, name, "fabricated$check", "(Ljava/lang/Object;ILjava/lang/String;)[Ljava/lang/Object;", false);
			mv.visitFieldInsn(Opcodes.PUTSTATIC, name, field, "[Ljava/lang/Object;");
			mv.visitLabel(have);
			// value = index < 0 ? null : arguments$n[index]; if (index < 0 || index == length - 1) arguments$n = null
			Label check = new Label();
			Label done = new Label();
			mv.visitVarInsn(Opcodes.ILOAD, 0);
			mv.visitJumpInsn(Opcodes.IFLT, check);
			mv.visitFieldInsn(Opcodes.GETSTATIC, name, field, "[Ljava/lang/Object;");
			mv.visitVarInsn(Opcodes.ILOAD, 0);
			mv.visitInsn(Opcodes.AALOAD);
			mv.visitVarInsn(Opcodes.ASTORE, 1);
			mv.visitVarInsn(Opcodes.ILOAD, 0);
			mv.visitFieldInsn(Opcodes.GETSTATIC, name, field, "[Ljava/lang/Object;");
			mv.visitInsn(Opcodes.ARRAYLENGTH);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitInsn(Opcodes.ISUB);
			mv.visitJumpInsn(Opcodes.IF_ICMPNE, done);
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitFieldInsn(Opcodes.PUTSTATIC, name, field, "[Ljava/lang/Object;");
			mv.visitLabel(done);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitLabel(check);
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitFieldInsn(Opcodes.PUTSTATIC, name, field, "[Ljava/lang/Object;");
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		// static Object[] fabricated$check(Object value, int length, String message)
		MethodVisitor check = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "fabricated$check", "(Ljava/lang/Object;ILjava/lang/String;)[Ljava/lang/Object;",
				null, null);
		check.visitCode();
		Label bad = new Label();
		check.visitVarInsn(Opcodes.ALOAD, 0);
		check.visitTypeInsn(Opcodes.INSTANCEOF, "[Ljava/lang/Object;");
		check.visitJumpInsn(Opcodes.IFEQ, bad);
		check.visitVarInsn(Opcodes.ALOAD, 0);
		check.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/Object;");
		check.visitInsn(Opcodes.ARRAYLENGTH);
		check.visitVarInsn(Opcodes.ILOAD, 1);
		check.visitJumpInsn(Opcodes.IF_ICMPNE, bad);
		check.visitVarInsn(Opcodes.ALOAD, 0);
		check.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/Object;");
		check.visitInsn(Opcodes.ARETURN);
		check.visitLabel(bad);
		check.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
		check.visitInsn(Opcodes.DUP);
		check.visitVarInsn(Opcodes.ALOAD, 2);
		check.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false);
		check.visitInsn(Opcodes.ATHROW);
		check.visitMaxs(0, 0);
		check.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The class with the supplier methods {@code methods} names (name and descriptor) no longer private, so that the
	 * helper next to it may call them: package-private, or public in an interface, whose methods can't be.
	 */
	static byte[] withReachableSuppliers(byte[] data, Set<String> methods) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		boolean itf = (node.access & Opcodes.ACC_INTERFACE) != 0;
		boolean changed = false;
		for (MethodNode m : node.methods) {
			if (!methods.contains(m.name + m.desc) || (m.access & Opcodes.ACC_PRIVATE) == 0) continue;
			m.access = m.access & ~Opcodes.ACC_PRIVATE | (itf ? Opcodes.ACC_PUBLIC : 0);
			changed = true;
		}
		if (!changed) return null;
		ClassWriter cw = new ClassWriter(0);
		node.accept(cw);
		return cw.toByteArray();
	}

	/** The additions by enum, in the order they were declared. */
	static Map<String, List<Addition>> byTarget(List<Addition> additions) {
		Map<String, List<Addition>> out = new LinkedHashMap<>();
		for (Addition a : additions) out.computeIfAbsent(a.target(), k -> new ArrayList<>()).add(a);
		return out;
	}

	private static void push(MethodVisitor mv, int value) {
		if (value >= -1 && value <= 5) mv.visitInsn(Opcodes.ICONST_0 + value);
		else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) mv.visitIntInsn(Opcodes.BIPUSH, value);
		else mv.visitIntInsn(Opcodes.SIPUSH, value);
	}
}
