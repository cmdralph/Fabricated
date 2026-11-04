package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * The {@code @Inject} handlers of interface mixins, applied by Fabricated where the running Mixin applies no injector in
 * an interface mixin (INTERFACE-1): SpongePowered's 0.8.5, which Forge 47 runs, takes no non-public method in an
 * interface mixin and applies none of its injectors ({@code MixinPreProcessorInterface}, {@code MixinApplicatorInterface}),
 * where Mixin does from 0.8.6, as Fabric's does. Fabric API's copper hook ({@code fabric-content-registries-v0}'s
 * {@code OxidizableMixin}, making the oxidation map its registry adds to mutable) never applied, so Create's main
 * entrypoint failed registering its copper (CORPUS-91); AppleSkin's tooltip and Cardinal Components' bucket hooks are
 * interface mixins' injectors too.
 *
 * <p>Translation ({@link #mark}) makes such a handler, where its injector is one Fabricated applies, a method Mixin
 * merges as it is: public and synthetic (what 0.8.5 takes in an interface mixin), named for its mixin, without its
 * {@code @Inject}, with what the injector does recorded in an annotation of Fabricated's. Mixin merges it into its target,
 * rewriting its code for the target as it does any method it merges, and once the target's mixins have applied,
 * Fabricated's Mixin extension ({@link #apply}) makes it private again and calls it where the injector would have Mixin
 * call it, as Mixin's {@code CallbackInjector} does: an {@code @Inject} at {@code HEAD}, {@code RETURN} or {@code TAIL},
 * cancellable or not, its handler taking the callback info alone or after the target's parameters. Any other injector in
 * an interface mixin is left out, as before.
 */
public final class InterfaceInjects {
	/** Fabricated's annotation on a handler it applies, from the translated mixin to its target until it applies. */
	static final String MARKER = "Lio/github/cmdralph/fabricated/InterfaceInject;";
	private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String CALLBACK_INFO_RETURNABLE = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
	/** The attributes of an {@code @Inject} and of its {@code @At}s that Fabricated applies as Mixin does. */
	private static final Set<String> INJECT_KEYS = Set.of("method", "at", "cancellable", "id", "remap", "require", "expect", "allow");
	private static final Set<String> AT_KEYS = Set.of("value", "ordinal", "remap");

	private InterfaceInjects() {
	}

	/**
	 * Makes an interface mixin's {@code @Inject} handler one Fabricated applies, where it can ({@link InterfaceInjects}):
	 * returns null once it has, else why it can't, the handler left as it was.
	 *
	 * @param mixin          the interface mixin
	 * @param handler        its handler
	 * @param inject         the handler's {@code @Inject}
	 * @param targets        the mixin's target classes
	 * @param resolve        a selector as the mixin's refmap resolves it
	 * @param defaultRequire the injections its config requires of an injector by default
	 */
	static String mark(ClassNode mixin, MethodNode handler, AnnotationNode inject, List<ClassNode> targets, UnaryOperator<String> resolve,
			int defaultRequire) {
		String refused = refusedAttribute(inject, INJECT_KEYS, "@Inject");
		if (refused != null) return refused;
		for (AnnotationNode other : Annotations.all(handler)) {
			if (other != inject && (PreflightAsm.isInjector(other.desc) || other.desc.equals(MixinNames.GROUP))) {
				return "it is also annotated @" + PreflightAsm.simpleName(other.desc);
			}
		}
		List<String> points = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(inject, "at"))) {
			refused = refusedAttribute(at, AT_KEYS, "@At");
			if (refused != null) return refused;
			String value = Annotations.value(at, "value") instanceof String v ? v : "";
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer o ? o : -1;
			if (!value.equals("HEAD") && !value.equals("RETURN") && !value.equals("TAIL")) return "it injects at " + value;
			if (ordinal >= 0 && !value.equals("RETURN")) return "it injects at an ordinal of " + value;
			points.add(ordinal >= 0 ? value + ":" + ordinal : value);
		}
		if (points.isEmpty()) return "it names no injection point";
		if (Type.getReturnType(handler.desc).getSort() != Type.VOID) return "its handler returns a value";
		if (hasParameterAnnotations(handler)) return "its handler's parameters are annotated (sugar or @Coerce)";
		Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
		if (handlerArgs.length == 0) return "its handler takes no callback info";

		List<String> recorded = new ArrayList<>();
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		int require = Annotations.value(inject, "require") instanceof Integer r && r >= 0 ? r : defaultRequire;
		int allow = Annotations.value(inject, "allow") instanceof Integer a ? a : -1;
		for (ClassNode target : targets) {
			List<MethodNode> matched = new ArrayList<>();
			if (Annotations.value(inject, "method") instanceof List<?> selectors) {
				for (Object o : selectors) {
					if (!(o instanceof String raw)) continue;
					MemberRef ref = MemberRef.parse(resolve.apply(raw));
					if (ref.fuzzy() || ref.name() == null) return "it selects its target method by a pattern";
					for (MethodNode m : target.methods) {
						if (!ref.matchesMethod(m.name, m.desc)) continue;
						// As Mixin: a method two selectors name is injected once; one without a descriptor picks the first.
						if (!matched.contains(m)) matched.add(m);
						if (ref.desc() == null) break;
					}
				}
			}
			if (matched.isEmpty()) return "it targets no method of " + target.name.replace('/', '.');
			int injections = 0;
			for (MethodNode m : matched) {
				String where = m.name + m.desc + " in " + target.name.replace('/', '.');
				if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return "it targets " + where + ", which has no code";
				if (((m.access & Opcodes.ACC_STATIC) != 0) != handlerStatic) return "its handler is " + (handlerStatic ? "" : "not ") + "static where " + where + " is"
						+ (handlerStatic ? " not" : "");
				Type returnType = Type.getReturnType(m.desc);
				String info = returnType.getSort() == Type.VOID ? CALLBACK_INFO : CALLBACK_INFO_RETURNABLE;
				Type[] targetArgs = Type.getArgumentTypes(m.desc);
				if (!handlerArgs[handlerArgs.length - 1].getInternalName().equals(info)) return "its handler doesn't take the callback info " + where + " gives";
				if (handlerArgs.length > 1 && !List.of(handlerArgs).subList(0, handlerArgs.length - 1).equals(List.of(targetArgs))) {
					return "its handler's parameters aren't those of " + where;
				}
				int found = pointsIn(m, points).size();
				if (found == 0) return "it finds no " + String.join(" or ", points) + " in " + where;
				injections += found;
				recorded.add(target.name + " " + m.name + " " + m.desc);
			}
			if (injections < require) return "it injects " + injections + " time(s) in " + target.name.replace('/', '.') + ", where it requires " + require;
			if (allow >= 0 && injections > allow) return "it injects " + injections + " time(s) in " + target.name.replace('/', '.') + ", where it allows " + allow;
		}

		String name = "fabricated$" + mixin.name.substring(mixin.name.lastIndexOf('/') + 1) + "$" + handler.name;
		for (MethodNode m : mixin.methods) {
			if (m != handler && m.name.equals(name) && m.desc.equals(handler.desc)) return "the mixin already declares " + name + handler.desc;
		}
		rename(mixin, handler, name);
		if (handler.visibleAnnotations != null) handler.visibleAnnotations.remove(inject);
		if (handler.invisibleAnnotations != null) handler.invisibleAnnotations.remove(inject);
		AnnotationNode marker = new AnnotationNode(MARKER);
		Annotations.put(marker, "targets", recorded);
		Annotations.put(marker, "points", points);
		Annotations.put(marker, "id", Annotations.value(inject, "id") instanceof String id ? id : "");
		Annotations.put(marker, "cancellable", Boolean.TRUE.equals(Annotations.value(inject, "cancellable")));
		Annotations.put(marker, "require", require);
		if (handler.invisibleAnnotations == null) handler.invisibleAnnotations = new ArrayList<>();
		handler.invisibleAnnotations.add(marker);
		handler.access = handler.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
		return null;
	}

	/** Whether the method is a handler {@link #mark} made, which Fabricated applies once Mixin has merged it. */
	public static boolean marked(MethodNode method) {
		return method.invisibleAnnotations != null && Annotations.find(method.invisibleAnnotations, MARKER) != null;
	}

	/**
	 * Applies the handlers {@link #mark} made that Mixin merged into a class, once the class's mixins have applied: each
	 * made private again and called where its injector would have Mixin call it. All the injection points are found
	 * before any is injected, as Mixin finds every injector's before injecting any (so a handler's cancellation, which
	 * returns, is no {@code RETURN} of another's).
	 *
	 * @param target the class, its mixins applied
	 * @return the handlers applied
	 * @throws IllegalStateException where a handler injects fewer times than its injector requires, as Mixin then fails
	 *                               the class
	 */
	public static int apply(ClassNode target) {
		List<Runnable> injections = new ArrayList<>();
		int applied = 0;
		for (MethodNode handler : target.methods) {
			AnnotationNode marker = handler.invisibleAnnotations == null ? null : Annotations.find(handler.invisibleAnnotations, MARKER);
			if (marker == null) continue;
			handler.invisibleAnnotations.remove(marker);
			handler.access = handler.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC) | Opcodes.ACC_PRIVATE;
			List<String> points = strings(Annotations.value(marker, "points"));
			String id = (String) Annotations.value(marker, "id");
			boolean cancellable = Boolean.TRUE.equals(Annotations.value(marker, "cancellable"));
			int require = Annotations.value(marker, "require") instanceof Integer r ? r : 0;
			int count = 0;
			for (String recorded : strings(Annotations.value(marker, "targets"))) {
				String[] parts = recorded.split(" ");
				if (!parts[0].equals(target.name)) continue;
				for (MethodNode method : target.methods) {
					if (!method.name.equals(parts[1]) || !method.desc.equals(parts[2])) continue;
					String callbackId = id.isEmpty() ? method.name : id;
					for (AbstractInsnNode at : pointsIn(method, points)) {
						injections.add(() -> inject(target, method, handler, at, callbackId, cancellable));
						count++;
					}
				}
			}
			if (count < require) {
				throw new IllegalStateException("The handler " + handler.name + handler.desc + " of an interface mixin injects " + count + " time(s) in "
						+ target.name.replace('/', '.') + ", where its @Inject requires " + require + " (INTERFACE-1)");
			}
			applied++;
		}
		injections.forEach(Runnable::run);
		return applied;
	}

	/** The instructions a method's injection points select, each once, in the order the points name them. */
	private static Set<AbstractInsnNode> pointsIn(MethodNode method, List<String> points) {
		Set<AbstractInsnNode> found = new LinkedHashSet<>();
		for (String point : points) found.addAll(points(method, point));
		return found;
	}

	/**
	 * The instructions one point selects, as Mixin's: {@code HEAD} the method's first, {@code RETURN} each return (or the
	 * one its ordinal counts), {@code TAIL} the last return.
	 */
	private static List<AbstractInsnNode> points(MethodNode method, String point) {
		List<AbstractInsnNode> returns = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) returns.add(insn);
		}
		if (point.equals("HEAD")) return method.instructions.size() == 0 ? List.of() : List.of(method.instructions.getFirst());
		if (point.equals("TAIL")) return returns.isEmpty() ? List.of() : List.of(returns.get(returns.size() - 1));
		if (point.startsWith("RETURN:")) {
			int ordinal = Integer.parseInt(point.substring("RETURN:".length()));
			return ordinal < returns.size() ? List.of(returns.get(ordinal)) : List.of();
		}
		return returns;
	}

	/**
	 * Calls the handler before {@code at}, as Mixin's {@code CallbackInjector}: the callback info made for the call (at a
	 * return of a value, holding the value), the handler given the target's parameters where it takes them, and where it
	 * is cancellable a cancelled call returning (the value the callback info holds).
	 */
	private static void inject(ClassNode owner, MethodNode method, MethodNode handler, AbstractInsnNode at, String id, boolean cancellable) {
		Type returnType = Type.getReturnType(method.desc);
		Type[] args = Type.getArgumentTypes(method.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		boolean atValue = at.getOpcode() >= Opcodes.IRETURN && at.getOpcode() <= Opcodes.ARETURN;
		boolean object = returnType.getSort() >= Type.ARRAY;
		String info = returnType.getSort() == Type.VOID ? CALLBACK_INFO : CALLBACK_INFO_RETURNABLE;
		InsnList code = new InsnList();
		int value = -1;
		if (atValue) {
			value = method.maxLocals;
			method.maxLocals += returnType.getSize();
			code.add(new InsnNode(returnType.getSize() == 2 ? Opcodes.DUP2 : Opcodes.DUP));
			code.add(new VarInsnNode(returnType.getOpcode(Opcodes.ISTORE), value));
		}
		int callbackInfo = method.maxLocals++;
		code.add(new TypeInsnNode(Opcodes.NEW, info));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new LdcInsnNode(id));
		code.add(new InsnNode(cancellable ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
		String constructor = "(Ljava/lang/String;Z)V";
		if (atValue) {
			code.add(new VarInsnNode(returnType.getOpcode(Opcodes.ILOAD), value));
			constructor = "(Ljava/lang/String;Z" + (object ? "Ljava/lang/Object;" : returnType.getDescriptor()) + ")V";
		}
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, info, "<init>", constructor, false));
		code.add(new VarInsnNode(Opcodes.ASTORE, callbackInfo));
		if (!isStatic) code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		int argSlots = 0;
		if (Type.getArgumentTypes(handler.desc).length > 1) {
			int slot = isStatic ? 0 : 1;
			for (Type arg : args) {
				code.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot));
				slot += arg.getSize();
				argSlots += arg.getSize();
			}
		}
		code.add(new VarInsnNode(Opcodes.ALOAD, callbackInfo));
		boolean itf = (owner.access & Opcodes.ACC_INTERFACE) != 0;
		code.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, owner.name, handler.name, handler.desc, itf));
		if (cancellable) {
			LabelNode carryOn = new LabelNode();
			code.add(new VarInsnNode(Opcodes.ALOAD, callbackInfo));
			code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, info, "isCancelled", "()Z", false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, carryOn));
			if (returnType.getSort() == Type.VOID) {
				code.add(new InsnNode(Opcodes.RETURN));
			} else {
				code.add(new VarInsnNode(Opcodes.ALOAD, callbackInfo));
				code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, info, object ? "getReturnValue" : "getReturnValue" + returnType.getDescriptor(),
						"()" + (object ? "Ljava/lang/Object;" : returnType.getDescriptor()), false));
				if (object) code.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
				code.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
			}
			code.add(carryOn);
		}
		method.instructions.insertBefore(at, code);
		// What the call needs on top of what the method has there: the callback info's construction (with the value), or the
		// handler's receiver, parameters and callback info.
		method.maxStack += Math.max(4 + 2 * returnType.getSize(), 2 + argSlots);
	}

	/** Renames a mixin's method, and its own code's uses of it. */
	private static void rename(ClassNode mixin, MethodNode method, String name) {
		String old = method.name;
		for (MethodNode m : mixin.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && call.name.equals(old) && call.desc.equals(method.desc)) {
					call.name = name;
				} else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (int i = 0; i < indy.bsmArgs.length; i++) {
						if (indy.bsmArgs[i] instanceof Handle h && h.getOwner().equals(mixin.name) && h.getName().equals(old) && h.getDesc().equals(method.desc)) {
							indy.bsmArgs[i] = new Handle(h.getTag(), h.getOwner(), name, h.getDesc(), h.isInterface());
						}
					}
				}
			}
		}
		method.name = name;
	}

	private static String refusedAttribute(AnnotationNode annotation, Set<String> known, String kind) {
		if (annotation.values == null) return null;
		for (int i = 0; i < annotation.values.size(); i += 2) {
			if (!known.contains(annotation.values.get(i))) return "its " + kind + " sets " + annotation.values.get(i);
		}
		return null;
	}

	private static boolean hasParameterAnnotations(MethodNode method) {
		for (int i = 0; i < Type.getArgumentTypes(method.desc).length; i++) {
			if (!Annotations.ofParameter(method, i).isEmpty()) return true;
		}
		return false;
	}

	private static List<String> strings(Object value) {
		List<String> strings = new ArrayList<>();
		if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof String s) strings.add(s);
			}
		}
		return strings;
	}
}
