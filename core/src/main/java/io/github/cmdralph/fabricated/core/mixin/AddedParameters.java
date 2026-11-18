package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Whether a vanilla method the platform gave a parameter vanilla's never had can serve a Fabric mod's {@code @Invoker} of
 * vanilla's with one fixed value of it (LEAVE-6, {@link InvokerBridge}): where the platform's method only passes that
 * parameter on to its extensions' methods, whose defaults ignore it, the method does the same for any value of it, for
 * every block (item, entity) keeping those defaults, as it does for the platform's own calls; one overriding them gets
 * the platform's answer for the value passed. Vanilla's private {@code FireBlock.checkBurnOut(level, pos, chance, random,
 * age)}, through which Supplementaries' lit lumisene sets the blocks around it alight, takes the side the fire comes from
 * too on NeoForge and Forge 52 to 66, and is replaced by {@code tryCatchFire} taking it on Forge 43 and 47 (STRUCT-2):
 * they pass it to the block state's {@code getFlammability} and {@code onCaughtFire}, whose defaults pass it to the
 * block's, which ignore it. Proven from the classes, every part of it, or there is no bridge ({@link Verdict#why}):
 * <ol>
 *   <li>the platform's method is an instance method with code, of vanilla's name or the one replacing vanilla's private
 *       method ({@link ReplacedMethods}), taking vanilla's parameters, in order one way only, and one more, returning the
 *       same;</li>
 *   <li>it never stores into that parameter, and each value it loads from it is an argument (not the receiver) of a call,
 *       straight from the load ({@link OverloadRepairs#pushedBy}), of a method the platform's own code declares (an
 *       extension interface's default, a method of a class of its packages: {@link PreflightClasses#declaration}), which
 *       ignores it the same way, to {@link #DEPTH} calls; a method that never loads it ignores it;</li>
 *   <li>without those calls, the loads and constants only they take, and the dropping of what they answer, the platform's
 *       method makes nothing vanilla's doesn't, in vanilla's order (each instruction compared as {@link PatchAlignment}
 *       compares them, by its opcode and what it names or pushes, not its local slots): it may leave some of vanilla's code
 *       out, as the platforms' leave out the TNT check their TNT block's caught-fire override makes;</li>
 *   <li>the value, by the rule the bridge follows: what the platform's first call of the method in its class passes, which
 *       must be a constant, a static final field of the parameter's type (the fire's tick passes {@code Direction.WEST} for
 *       the block east of it).</li>
 * </ol>
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
	 * {@code IBlockStateExtension.getFlammability}), or why there is none ({@code why}, a clause for the report).
	 */
	record Verdict(MethodNode method, int parameter, InvokerBridge bridge, List<String> passedTo, String why) {
		/** The added parameter's type, as messages name it: {@code Direction}. */
		String typeName() {
			return AddedParameters.typeName(Type.getArgumentTypes(method.desc)[parameter]);
		}
	}

	/** A call of the platform's own method the parameter is passed to, and the code it runs. */
	private record Passed(MethodInsnNode call, PreflightClasses.Code code) {
	}

	/**
	 * The verdict on vanilla's method {@code name desc} of {@code target}, decided once per pre-flight run; null where the
	 * target has it, or has no method in its place taking one parameter more (nothing to bridge).
	 */
	Verdict of(ClassNode target, String name, String desc) {
		return verdicts.computeIfAbsent(target.name + "." + name + desc, k -> Optional.ofNullable(judge(target, name, desc))).orElse(null);
	}

	private Verdict judge(ClassNode target, String name, String desc) {
		if ((target.access & Opcodes.ACC_INTERFACE) != 0 || name.startsWith("<") || PreflightAsm.declares(target, name, desc)) return null;
		MethodNode platform = null;
		int added = -1;
		for (MethodNode m : target.methods) {
			int k = m.name.equals(name) && hasCode(m) ? added(desc, m.desc) : -1;
			if (k < 0) continue;
			if (platform != null) return null;
			platform = m;
			added = k;
		}
		if (platform == null) {
			ReplacedMethods.Replaced replaced = replacedMethods.replaced(target, name, desc);
			added = replaced == null || !hasCode(replaced.method()) ? -1 : added(desc, replaced.method().desc);
			if (added < 0) return null;
			platform = replaced.method();
		}
		ClassNode vanillaClass = vanillaClass(target.name);
		MethodNode vanilla = vanillaClass == null ? null : PreflightAsm.declared(vanillaClass, name, desc);
		if (vanillaClass != null && (vanilla == null || vanilla.instructions.size() == 0 || (vanilla.access & Opcodes.ACC_STATIC) != 0)) return null;
		Type type = Type.getArgumentTypes(platform.desc)[added];
		String own = simpleName(target.name) + "." + platform.name;
		String why;
		List<Passed> passed = new ArrayList<>();
		String constant = null;
		if (vanilla == null) {
			why = "vanilla's class, which it is compared with, can't be read";
		} else {
			why = passedOn(own, platform, slotOf(platform, added), type, 0, passed);
			if (why == null) why = madeByVanilla(own, vanilla, platform, passed);
			if (why == null) {
				constant = firstConstant(target, platform, added, type);
				if (constant == null) why = "the first call of it in " + simpleName(target.name) + " passes no constant " + typeName(type);
			}
		}
		if (why != null) return new Verdict(platform, added, null, List.of(), why);
		Set<String> passedTo = new LinkedHashSet<>();
		for (Passed p : passed) passedTo.add(simpleName(p.code().owner()) + "." + p.call().name);
		return new Verdict(platform, added, new InvokerBridge(target.name, name, desc, platform.name, platform.desc, added, constant), List.copyOf(passedTo), null);
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
	 * ignoring it in turn ({@link #ignoredBy}); null where it does, or never loads it. The calls it passes it to are added to
	 * {@code passed} at depth 0.
	 */
	private String passedOn(String named, MethodNode method, int slot, Type type, int depth, List<Passed> passed) {
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
				String why = ignoredBy(named, call, i - first, what, depth, passed);
				if (why != null) return why;
				passedLoads.add(pushed[i]);
			}
		}
		return passedLoads.containsAll(loads) ? null : named + " reads " + what + " otherwise than to pass it to a method";
	}

	/** Why the method {@code call} makes doesn't ignore its argument at {@code index}, the parameter; null where it does. */
	private String ignoredBy(String named, MethodInsnNode call, int index, String what, int depth, List<Passed> passed) {
		String callee = simpleName(call.owner) + "." + call.name;
		PreflightClasses.Code code = call.getOpcode() == Opcodes.INVOKESPECIAL ? null : declaration(call.owner, call.name, call.desc);
		if (code == null || !MixinNames.isPlatformCode(code.owner())) {
			return named + " passes " + what + " to " + callee + ", which isn't a method of " + loader + "'s own code";
		}
		boolean isStatic = (code.method().access & Opcodes.ACC_STATIC) != 0;
		if (isStatic != (call.getOpcode() == Opcodes.INVOKESTATIC)) return named + " passes " + what + " to " + callee + ", which can't be told";
		if (depth + 1 >= DEPTH) return named + " passes " + what + " on deeper than Fabricated follows it (" + DEPTH + " methods)";
		if (depth == 0) passed.add(new Passed(call, code));
		Type[] args = Type.getArgumentTypes(call.desc);
		int slot = isStatic ? 0 : 1;
		for (int k = 0; k < index; k++) slot += args[k].getSize();
		return passedOn(simpleName(code.owner()) + "." + call.name, code.method(), slot, args[index], depth + 1, passed);
	}

	/**
	 * Why the platform's method ({@code named}), but for the calls it passes the parameter to, the loads and constants only
	 * they take and the dropping of what they answer where it drops it at once (Forge 61 to 66's {@code onCaughtFire}
	 * answers whether the block burnt), makes something vanilla's doesn't, in vanilla's order; null where it makes nothing
	 * else.
	 */
	private static String madeByVanilla(String named, MethodNode vanilla, MethodNode platform, List<Passed> passed) {
		Set<AbstractInsnNode> theirs = new HashSet<>();
		Set<LabelNode> entered = PreflightAsm.jumpTargets(platform);
		for (Passed p : passed) {
			theirs.add(p.call());
			for (AbstractInsnNode pushed : OverloadRepairs.pushedBy(platform, p.call())) {
				int op = pushed.getOpcode();
				if (op >= Opcodes.ACONST_NULL && op <= Opcodes.LDC || op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) theirs.add(pushed);
			}
			int answer = Type.getReturnType(p.call().desc).getSize();
			AbstractInsnNode next = p.call().getNext();
			while (next != null && next.getOpcode() < 0 && !entered.contains(next)) next = next.getNext();
			if (next != null && answer > 0 && next.getOpcode() == (answer == 2 ? Opcodes.POP2 : Opcodes.POP)) theirs.add(next);
		}
		List<String> own = new ArrayList<>();
		for (AbstractInsnNode insn : vanilla.instructions) {
			if (insn.getOpcode() >= 0) own.add(PatchAlignment.token(insn));
		}
		int j = 0;
		for (AbstractInsnNode insn : platform.instructions) {
			if (insn.getOpcode() < 0 || theirs.contains(insn)) continue;
			String token = PatchAlignment.token(insn);
			while (j < own.size() && !own.get(j).equals(token)) j++;
			if (j == own.size()) return named + " makes what vanilla's doesn't (" + readable(insn) + ")";
			j++;
		}
		return null;
	}

	/**
	 * The constant the platform's first call of its method in the class passes for the parameter: the name of a static
	 * final field of the parameter's type, read straight before the call; null where that call passes another value, or
	 * the class makes none.
	 */
	private String firstConstant(ClassNode target, MethodNode platform, int added, Type type) {
		for (MethodNode m : target.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || !call.name.equals(platform.name) || !call.desc.equals(platform.desc)) {
					continue;
				}
				AbstractInsnNode[] pushed = OverloadRepairs.pushedBy(m, call);
				if (pushed == null || !(pushed[added + 1] instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC || type.getSort() != Type.OBJECT
						|| !field.owner.equals(type.getInternalName()) || !field.desc.equals(type.getDescriptor())) {
					return null;
				}
				ClassNode owner = codeOf(field.owner);
				FieldNode declared = owner == null ? null : owner.fields.stream().filter(f -> f.name.equals(field.name) && f.desc.equals(field.desc)).findFirst().orElse(null);
				int constant = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
				return declared != null && (declared.access & constant) == constant ? field.name : null;
			}
		}
		return null;
	}

	/** Whether the method is an instance method with code, as the platform's method in vanilla's place must be. */
	private static boolean hasCode(MethodNode method) {
		return (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0 && method.instructions.size() > 0;
	}

	/** A type as messages name it: {@code Direction}, {@code int}. */
	private static String typeName(Type type) {
		String name = type.getClassName();
		return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
	}

	/** The local slot of an instance method's parameter at {@code index}. */
	private static int slotOf(MethodNode method, int index) {
		Type[] args = Type.getArgumentTypes(method.desc);
		int slot = 1;
		for (int k = 0; k < index; k++) slot += args[k].getSize();
		return slot;
	}

	/** An instruction as a message names it: the method it calls, the field it reads or writes, or what kind it is. */
	private static String readable(AbstractInsnNode insn) {
		if (insn instanceof MethodInsnNode call) return "a call of " + simpleName(call.owner) + "." + call.name;
		if (insn instanceof FieldInsnNode field) return "a use of " + simpleName(field.owner) + "." + field.name;
		if (insn instanceof VarInsnNode local) return (local.getOpcode() >= Opcodes.ISTORE ? "a store into" : "a load of") + " its local " + local.var;
		if (insn instanceof JumpInsnNode) return "a branch";
		int op = insn.getOpcode();
		return op == Opcodes.POP || op == Opcodes.POP2 ? "a value dropped" : "an instruction of opcode " + op;
	}

	private static String simpleName(String internalName) {
		return internalName.substring(internalName.lastIndexOf('/') + 1).replace('$', '.');
	}
}
