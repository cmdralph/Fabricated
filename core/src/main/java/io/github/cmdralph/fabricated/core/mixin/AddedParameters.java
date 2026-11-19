package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Whether a vanilla method the platform gave a parameter vanilla's never had can serve a Fabric mod's {@code @Invoker} of
 * vanilla's with one fixed value of that parameter (LEAVE-6, {@link InvokerBridge}). The bridge calls the platform's own
 * version of vanilla's method, as an invoker of a method the platform patched without changing its parameters already
 * does; what Fabricated adds is the value, and it proves that the value changes nothing for whatever keeps the platform's
 * defaults: the platform's method only passes the parameter on to methods of the platform's own code whose code ignores it.
 * Vanilla's private {@code FireBlock.checkBurnOut(level, pos, chance, random, age)}, through which Supplementaries' lit
 * lumisene sets the blocks around it alight, takes the side the fire comes from too on NeoForge and Forge 52 to 66, and is
 * replaced by {@code tryCatchFire} taking it on Forge 43 and 47 (STRUCT-2): they pass it to the block state's
 * {@code getFlammability} and {@code onCaughtFire}, whose defaults pass it to the block's, which ignore it. A block
 * overriding those gets the platform's answer for the value passed. Proven from the classes, or there is no bridge
 * ({@link Verdict#why}):
 * <ol>
 *   <li>which method: a game class's, whose vanilla class is read and declares vanilla's method as an instance method
 *       with code, the target lacking it and having exactly one instance method with code of vanilla's name taking
 *       vanilla's parameters, in order one way only, and one more, returning the same; or the method replacing vanilla's
 *       private one ({@link ReplacedMethods}), taking them so;</li>
 *   <li>the parameter: never stored into, and each value loaded from it is an argument (not the receiver) of a call,
 *       straight from the load ({@link OverloadRepairs#pushedBy}), of a method the JVM runs from the platform's own code
 *       ({@link #runs}: the class's or a superclass's, else the one maximally specific default), which ignores it the same
 *       way, to {@link #DEPTH} methods; a method that never loads it ignores it;</li>
 *   <li>the value, by the rule the bridge follows: what the platform's first call of the method in its class passes, which
 *       must be a constant, a static final field of the parameter's type (the fire's tick passes {@code Direction.WEST} for
 *       the block east of it).</li>
 * </ol>
 * Overrides of those methods in the game's own classes, which the platform patched (NeoForge's {@code TntBlock} overrides
 * {@code onCaughtFire}), are a block's own answer, as a mod's block's are: nothing here reads them as the game starts.
 * CI reads them on each pinned platform build ({@link InvokerBridgeGuard}), each judged as the code it overrides.
 */
final class AddedParameters extends PreflightPart {
	/** How many methods deep the parameter is followed: the platform's method, the methods it passes it to, and theirs. */
	private static final int DEPTH = 3;

	private final ReplacedMethods replacedMethods;
	private final Map<String, Optional<Verdict>> verdicts = new HashMap<>();

	AddedParameters(PreflightClasses classes, ReplacedMethods replacedMethods) {
		super(classes);
		this.replacedMethods = replacedMethods;
	}

	/**
	 * The platform's method in vanilla's place ({@code method}, its added parameter at {@code parameter}), and either the
	 * bridge, with the platform's methods the method passes the parameter to ({@code passedTo}, as messages name them:
	 * {@code IBlockStateExtension.getFlammability}) and every call the parameter reaches, at any depth ({@code reached}),
	 * or why there is none ({@code why}, a clause for the report).
	 */
	record Verdict(MethodNode method, int parameter, InvokerBridge bridge, List<String> passedTo, List<Reached> reached, String why) {
		/** The added parameter's type, as messages name it: {@code Direction}. */
		String typeName() {
			return AddedParameters.typeName(Type.getArgumentTypes(method.desc)[parameter]);
		}
	}

	/**
	 * A call of the platform's own method the parameter is passed to, as the argument at {@code index}, the code the JVM
	 * runs for it, and the depth of the method making it (0: the platform's method in vanilla's place).
	 */
	record Reached(MethodInsnNode call, PreflightClasses.Code code, int index, int depth) {
	}

	/** The code the JVM runs for a call, or why there is none to follow ({@link #runs}). */
	private record Runs(PreflightClasses.Code code, String why) {
	}

	/**
	 * The verdict on vanilla's method {@code name desc} of {@code target}, decided once per pre-flight run; null where
	 * there is nothing for LEAVE-6 to judge, and the line keeps the other reasons it gives, no LEAVE-6 limit: the target
	 * isn't a game class (an installed mod's has no vanilla code, and its versions' changes are its own), vanilla's class
	 * can't be read (nothing tells vanilla's method), the target has the method, vanilla has none of that name and
	 * descriptor, or the target has no method in its place taking one parameter more. Every other case is judged, and a
	 * verdict without a bridge says why, which the line gives with LEAVE-6's limit.
	 */
	Verdict of(ClassNode target, String name, String desc) {
		return verdicts.computeIfAbsent(target.name + "." + name + desc, k -> Optional.ofNullable(judge(target, name, desc))).orElse(null);
	}

	private Verdict judge(ClassNode target, String name, String desc) {
		// A constructor's or initialiser's name: no invoker reaches here with one (Mixin invokes a constructor with NEW).
		if (!PreflightAsm.isGameClass(target.name) || name.startsWith("<") || PreflightAsm.declares(target, name, desc)) return null;
		ClassNode vanillaClass = vanillaClass(target.name);
		MethodNode vanilla = vanillaClass == null ? null : PreflightAsm.declared(vanillaClass, name, desc);
		if (vanilla == null) return null;
		List<MethodNode> named = new ArrayList<>();
		for (MethodNode m : target.methods) {
			if (m.name.equals(name) && added(desc, m.desc) >= 0) named.add(m);
		}
		MethodNode platform;
		if (named.isEmpty()) {
			ReplacedMethods.Replaced replaced = replacedMethods.replaced(target, name, desc);
			if (replaced == null || added(desc, replaced.method().desc) < 0) return null;
			platform = replaced.method();
		} else {
			platform = named.get(0);
		}
		int added = added(desc, platform.desc);
		String loaderName = loader + "'s";
		String why = null;
		if ((target.access & Opcodes.ACC_INTERFACE) != 0) {
			why = simpleName(target.name) + " is an interface, and Fabricated declares bridges in classes only";
		} else if (PreflightAsm.declares(vanillaClass, platform.name, platform.desc)) {
			why = "vanilla's class has " + platform.name + platform.desc + " too, so that is vanilla's own method, not " + loaderName + " version of the"
					+ " one the invoker names";
		} else if (named.size() > 1) {
			why = simpleName(target.name) + " has " + named.size() + " methods of that name taking one parameter more than vanilla's, and which one stands"
					+ " in for it can't be told";
		} else if ((vanilla.access & Opcodes.ACC_STATIC) != 0) {
			why = "vanilla's method is static, and Fabricated bridges instance methods only";
		} else if (!hasCode(vanilla)) {
			why = "vanilla's method has no code of its own";
		} else if ((platform.access & Opcodes.ACC_STATIC) != 0) {
			why = loaderName + " method is static where vanilla's isn't";
		} else if (!hasCode(platform)) {
			why = loaderName + " method has no code of its own (it is abstract or native)";
		}
		Type type = Type.getArgumentTypes(platform.desc)[added];
		List<Reached> reached = new ArrayList<>();
		if (why == null) why = passedOn(simpleName(target.name) + "." + platform.name, platform, slotOf(platform, added), type, 0, reached);
		String[] constant = {null};
		if (why == null) why = firstConstant(target, platform, added, type, constant);
		if (why != null) return new Verdict(platform, added, null, List.of(), List.of(), why);
		Set<String> passedTo = new LinkedHashSet<>();
		for (Reached r : reached) {
			if (r.depth() == 0) passedTo.add(simpleName(r.code().owner()) + "." + r.call().name);
		}
		return new Verdict(platform, added, new InvokerBridge(target.name, name, desc, platform.name, platform.desc, added, constant[0]),
				List.copyOf(passedTo), List.copyOf(reached), null);
	}

	/**
	 * The index of the parameter {@code platform} takes beyond {@code vanilla}'s: where it takes vanilla's parameters, in
	 * order one way only, and one more, returning the same; else -1.
	 */
	static int added(String vanilla, String platform) {
		if (!Type.getReturnType(vanilla).equals(Type.getReturnType(platform))) return -1;
		List<Type> own = List.of(Type.getArgumentTypes(vanilla));
		List<Type> more = List.of(Type.getArgumentTypes(platform));
		if (more.size() != own.size() + 1) return -1;
		int[] from = LambdaRepairs.inOrder(own, more, Type::equals);
		if (from == null) return -1;
		for (int k = 0; k < from.length; k++) {
			if (from[k] != k) return k;
		}
		return own.size();
	}

	/**
	 * Why {@code method} ({@code named}) doesn't pass its parameter in {@code slot} on to the platform's methods only, each
	 * ignoring it in turn ({@link #ignoredBy}); null where it does, or never loads it. The calls it passes it to, and theirs,
	 * are added to {@code reached}.
	 */
	private String passedOn(String named, MethodNode method, int slot, Type type, int depth, List<Reached> reached) {
		String what = "the " + typeName(type);
		Set<AbstractInsnNode> loads = new HashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof IincInsnNode inc && inc.var == slot) return named + " stores another value in " + what;
			if (!(insn instanceof VarInsnNode v)) continue;
			if (v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) {
				int size = v.getOpcode() == Opcodes.LSTORE || v.getOpcode() == Opcodes.DSTORE ? 2 : 1;
				if (v.var < slot + type.getSize() && slot < v.var + size) return named + " stores another value in " + what;
			} else if (v.var == slot && v.getOpcode() == type.getOpcode(Opcodes.ILOAD)) {
				loads.add(v);
			}
		}
		Set<AbstractInsnNode> passedLoads = new HashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (loads.isEmpty() || !(insn instanceof MethodInsnNode call)) continue;
			AbstractInsnNode[] pushed = OverloadRepairs.pushedBy(method, call);
			if (pushed == null) continue;
			int first = call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
			for (int i = 0; i < pushed.length; i++) {
				if (!loads.contains(pushed[i])) continue;
				if (i < first) return named + " calls " + simpleName(call.owner) + "." + call.name + " on " + what;
				String why = ignoredBy(named, call, i - first, what, depth, reached);
				if (why != null) return why;
				passedLoads.add(pushed[i]);
			}
		}
		return passedLoads.containsAll(loads) ? null : named + " reads " + what + " otherwise than to pass it straight to a method";
	}

	/** Why the method {@code call} makes doesn't ignore its argument at {@code index}, the parameter; null where it does. */
	private String ignoredBy(String named, MethodInsnNode call, int index, String what, int depth, List<Reached> reached) {
		String callee = simpleName(call.owner) + "." + call.name;
		if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
			return named + " passes " + what + " to " + callee + " by invokespecial (a private method's or a superclass's), which Fabricated doesn't follow";
		}
		Runs runs = runs(call);
		if (runs.why() != null) return named + " passes " + what + " to " + callee + ", " + runs.why();
		PreflightClasses.Code code = runs.code();
		if (!MixinNames.isPlatformCode(code.owner())) {
			return named + " passes " + what + " to " + callee + ", which runs " + simpleName(code.owner()) + "'s, not a method of " + loader + "'s own code";
		}
		if (depth + 1 >= DEPTH) return named + " passes " + what + " on deeper than Fabricated follows it (" + DEPTH + " methods)";
		reached.add(new Reached(call, code, index, depth));
		return passedOn(simpleName(code.owner()) + "." + call.name, code.method(), slotOf(call, index), Type.getArgumentTypes(call.desc)[index], depth + 1,
				reached);
	}

	/**
	 * Why {@code override}, {@code owner}'s method standing in for the code {@code reached} runs where the receiver is of
	 * {@code owner}'s class, doesn't ignore the parameter by the rule that code was judged by, from the same depth; null
	 * where it does. The calls it passes the parameter to, and theirs, are added to {@code more}. CI's guard asks it of the
	 * game's and the platform's overrides ({@link InvokerBridgeGuard}).
	 */
	String overrideReads(String owner, MethodNode override, Reached reached, List<Reached> more) {
		return passedOn(simpleName(owner) + "." + override.name, override, slotOf(reached.call(), reached.index()),
				Type.getArgumentTypes(reached.call().desc)[reached.index()], reached.depth() + 1, more);
	}

	/** Whether {@code type} is {@code root} or extends or implements it, as far as the classes read tell. */
	boolean isSubtype(String type, String root) {
		List<String> queue = new ArrayList<>(List.of(type));
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < queue.size(); i++) {
			String c = queue.get(i);
			if (c.equals(root)) return true;
			if (!seen.add(c)) continue;
			ClassNode node = readable(c);
			if (node == null) continue;
			if (node.superName != null) queue.add(node.superName);
			queue.addAll(node.interfaces);
		}
		return false;
	}

	/**
	 * The code the JVM runs for {@code call} on an object of the class it names, as the JVM selects it: the method of that
	 * class or the nearest superclass declaring it, else the one maximally specific default of their interfaces (JVMS
	 * 5.4.3.3); for a static call, the class's or a superclass's static method. Why there is none to follow where the
	 * method found has no code, isn't of the call's kind, is a superclass's private one (which the call resolves to and
	 * can't reach), can't be read, or more than one interface's default could run.
	 * Not {@link PreflightClasses#declaration}, which takes the first default it meets: the repairs that ask it (HOOK-2,
	 * HOOK-3, STRUCT-1's hooks, overloads) find calls through it, and would see others if it changed.
	 */
	private Runs runs(MethodInsnNode call) {
		boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
		List<String> superinterfaces = new ArrayList<>();
		for (String c = call.owner; c != null; ) {
			ClassNode node = readable(c);
			if (node == null) return new Runs(null, "whose class " + simpleName(c) + " can't be read");
			MethodNode m = PreflightAsm.declared(node, call.name, call.desc);
			// A superclass's private method is what the call resolves to, and can't reach (IllegalAccessError).
			if (m != null && !c.equals(call.owner) && (m.access & Opcodes.ACC_PRIVATE) != 0) {
				return new Runs(null, "which " + simpleName(c) + " declares private, so the call can't reach it");
			}
			if (m != null) {
				if (((m.access & Opcodes.ACC_STATIC) != 0) != isStatic) {
					return new Runs(null, "which " + simpleName(c) + " declares " + (isStatic ? "as an instance method" : "static") + " where the call is "
							+ (isStatic ? "static" : "not"));
				}
				return hasCode(m) ? new Runs(new PreflightClasses.Code(c, m), null) : new Runs(null, "which " + simpleName(c) + " declares without code");
			}
			superinterfaces.addAll(node.interfaces);
			if ((node.access & Opcodes.ACC_INTERFACE) != 0) break;
			c = node.superName;
		}
		if (isStatic) return new Runs(null, "which no class declares as a static method with code");
		// Every interface the class has, with the ones each extends.
		Map<String, ClassNode> all = new java.util.LinkedHashMap<>();
		for (int i = 0; i < superinterfaces.size(); i++) {
			String itf = superinterfaces.get(i);
			if (all.containsKey(itf)) continue;
			ClassNode node = readable(itf);
			if (node == null) return new Runs(null, "whose interface " + simpleName(itf) + " can't be read");
			all.put(itf, node);
			superinterfaces.addAll(node.interfaces);
		}
		ClassNode owner = readable(call.owner);
		if ((owner.access & Opcodes.ACC_INTERFACE) != 0) all.putIfAbsent(call.owner, owner);
		List<PreflightClasses.Code> candidates = new ArrayList<>();
		for (Map.Entry<String, ClassNode> e : all.entrySet()) {
			MethodNode m = PreflightAsm.declared(e.getValue(), call.name, call.desc);
			if (m != null && (m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) candidates.add(new PreflightClasses.Code(e.getKey(), m));
		}
		// The maximally specific ones: no other candidate's interface extends theirs.
		List<PreflightClasses.Code> specific = new ArrayList<>();
		for (PreflightClasses.Code c : candidates) {
			boolean less = false;
			for (PreflightClasses.Code other : candidates) {
				if (other != c && extendsInterface(other.owner(), c.owner(), all)) less = true;
			}
			if (!less) specific.add(c);
		}
		if (specific.isEmpty()) return new Runs(null, "which no class or interface there declares");
		if (specific.size() > 1) {
			return new Runs(null, "for which " + String.join(" and ", specific.stream().map(c -> simpleName(c.owner())).toList())
					+ " each have a default, so which one runs can't be told");
		}
		PreflightClasses.Code only = specific.get(0);
		return hasCode(only.method()) ? new Runs(only, null) : new Runs(null, "which " + simpleName(only.owner()) + " declares without code");
	}

	/** A class of the game, the platform or the JDK ({@code Object}, the interfaces enums have), with its code; or null. */
	private ClassNode readable(String internalName) {
		ClassNode node = codeOf(internalName);
		return node != null ? node : ClassIndex.JDK.readNode(internalName, 0);
	}

	/** Whether the interface {@code sub} extends {@code sup}, directly or not, among {@code all}. */
	private static boolean extendsInterface(String sub, String sup, Map<String, ClassNode> all) {
		List<String> queue = new ArrayList<>(List.of(sub));
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < queue.size(); i++) {
			ClassNode node = all.get(queue.get(i));
			if (node == null || !seen.add(queue.get(i))) continue;
			for (String itf : node.interfaces) {
				if (itf.equals(sup)) return true;
				queue.add(itf);
			}
		}
		return false;
	}

	/**
	 * Why the platform's first call of its method in the class passes no constant for the parameter, a static final field
	 * of the parameter's type read straight before the call; null where it does, its name in {@code constant[0]}.
	 */
	private String firstConstant(ClassNode target, MethodNode platform, int added, Type type, String[] constant) {
		String owner = simpleName(target.name);
		for (MethodNode m : target.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || !call.name.equals(platform.name) || !call.desc.equals(platform.desc)) {
					continue;
				}
				AbstractInsnNode[] pushed = OverloadRepairs.pushedBy(m, call);
				if (pushed == null) return "what the first call of it in " + owner + " passes for the " + typeName(type) + " can't be told (it is worked out across a branch)";
				if (!(pushed[added + 1] instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC || type.getSort() != Type.OBJECT
						|| !field.owner.equals(type.getInternalName()) || !field.desc.equals(type.getDescriptor())) {
					return "the first call of it in " + owner + " passes no constant " + typeName(type);
				}
				ClassNode fields = codeOf(field.owner);
				if (fields == null) return "the class of the constant the first call of it in " + owner + " passes, " + typeName(type) + ", can't be read";
				FieldNode declared = fields.fields.stream().filter(f -> f.name.equals(field.name) && f.desc.equals(field.desc)).findFirst().orElse(null);
				int fixed = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
				if (declared == null || (declared.access & fixed) != fixed) {
					return "the first call of it in " + owner + " passes " + typeName(type) + "." + field.name + ", which isn't a static final field of " + typeName(type);
				}
				constant[0] = field.name;
				return null;
			}
		}
		return owner + " never calls it, so no call of the platform's gives a value";
	}

	/** Whether the method has code: not abstract or native. */
	private static boolean hasCode(MethodNode method) {
		return (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0 && method.instructions.size() > 0;
	}

	/** A type as messages name it: {@code Direction}, {@code int}. */
	static String typeName(Type type) {
		String name = type.getClassName();
		return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
	}

	/** The local slot, in the method {@code call} calls, of its parameter at {@code index}. */
	private static int slotOf(MethodInsnNode call, int index) {
		Type[] args = Type.getArgumentTypes(call.desc);
		int slot = call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
		for (int k = 0; k < index; k++) slot += args[k].getSize();
		return slot;
	}

	/** The local slot of an instance method's parameter at {@code index}. */
	private static int slotOf(MethodNode method, int index) {
		Type[] args = Type.getArgumentTypes(method.desc);
		int slot = 1;
		for (int k = 0; k < index; k++) slot += args[k].getSize();
		return slot;
	}

	static String simpleName(String internalName) {
		return internalName.substring(internalName.lastIndexOf('/') + 1).replace('$', '.');
	}
}
