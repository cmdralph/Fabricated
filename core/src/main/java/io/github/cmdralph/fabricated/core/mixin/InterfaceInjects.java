package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;

/**
 * The injectors of interface mixins, applied by Fabricated where the running Mixin applies none in an interface mixin
 * (INTERFACE-1): SpongePowered's 0.8.5, which Forge 47 runs, takes no non-public method in an interface mixin and applies
 * none of its injectors ({@code MixinPreProcessorInterface}, {@code MixinApplicatorInterface}), where Mixin does from
 * 0.8.6, as Fabric's does. Fabric API's copper hook ({@code fabric-content-registries-v0}'s {@code OxidizableMixin},
 * making the oxidation map its registry adds to mutable) never applied, so Create's main entrypoint failed registering
 * its copper (CORPUS-91); Origins' armour restriction, owo-lib's recipe remainders, Carpet's, ServerCore's and Wilder
 * Wild's hooks are interface mixins' injectors too.
 *
 * <p>Translation ({@link #mark}) makes such a handler, where its injector is one Fabricated applies, a method Mixin
 * merges as it is: public and synthetic (what 0.8.5 takes in an interface mixin), named for its mixin, its injector
 * annotation invisible (Mixin reads only visible ones), its selectors as the mixin's refmap resolves them, and marked.
 * Pre-flight checks that injector as it checks a class mixin's. Mixin merges the handler into its target, rewriting its
 * code for the target as it does any method it merges, and once the target's mixins have applied, Fabricated's Mixin
 * extension ({@link #apply}) makes it private again and has it called where its injector would have Mixin call it:
 * <ul>
 * <li>an {@code @Inject} at {@code HEAD}, {@code RETURN}, {@code TAIL}, a call or a field access, shifted to the
 * instruction before or after it or not, cancellable or not, its handler taking the callback info alone or after the
 * target's parameters, then the locals Mixin works out there where it captures them (its {@code CallbackInjector});</li>
 * <li>a {@code @Redirect} of a call (its {@code RedirectInjector});</li>
 * <li>MixinExtras' {@code @WrapOperation} of a call, its {@code Operation} doing the call (its
 * {@code WrapOperationInjector});</li>
 * <li>a {@code @ModifyConstant} of a number, string or class constant (its {@code ModifyConstantInjector}).</li>
 * </ul>
 * The last three take a prefix of the target's parameters after their own where the handler declares them, and each
 * MixinExtras' {@code @Local} parameters, found as Mixin's discriminator finds a local. Any other injector, slice,
 * {@code @Coerce} or sugar is left out, as before.
 */
public final class InterfaceInjects {
	/** Fabricated's annotation on a handler it applies, from the translated mixin to its target until it applies. */
	static final String MARKER = "Lio/github/cmdralph/fabricated/InterfaceInject;";
	private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String CALLBACK_INFO_RETURNABLE = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
	private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
	private static final Handle METAFACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
					+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
	/** The injectors Fabricated applies, each with the attributes of it that it applies as Mixin (MixinExtras) does. */
	private static final Map<String, Set<String>> INJECTORS = Map.of(
			MixinNames.INJECT, Set.of("method", "at", "cancellable", "locals", "id", "remap", "require", "expect", "allow"),
			MixinNames.REDIRECT, Set.of("method", "at", "remap", "require", "expect", "allow"),
			MixinNames.WRAP_OPERATION, Set.of("method", "at", "remap", "require", "expect", "allow"),
			MixinNames.MODIFY_CONSTANT, Set.of("method", "constant", "remap", "require", "expect", "allow"));
	static final String APPLIED = "@Inject, @Redirect, @ModifyConstant and @WrapOperation injectors";
	private static final Set<String> AT_KEYS = Set.of("value", "target", "ordinal", "opcode", "shift", "remap");
	private static final List<String> CONSTANT_VALUES = List.of("intValue", "floatValue", "longValue", "doubleValue", "stringValue", "classValue");
	private static final Set<String> CONSTANT_KEYS = Set.of("intValue", "floatValue", "longValue", "doubleValue", "stringValue", "classValue", "ordinal");
	private static final Set<String> LOCAL_KEYS = Set.of("ordinal", "index", "argsOnly");

	private InterfaceInjects() {
	}

	/** The locals Mixin works out at an instruction ({@code Locals.getLocalsAt}): each slot's descriptor, null for none; null where it can't. */
	@FunctionalInterface
	public interface LocalsAt {
		String[] at(MethodNode method, AbstractInsnNode insn);
	}

	/**
	 * Makes an interface mixin's injector handler one Fabricated applies, where it can ({@link InterfaceInjects}): returns
	 * null once it has, else why it can't, the handler left as it was.
	 *
	 * @param mixin          the interface mixin
	 * @param handler        its handler
	 * @param injector       the handler's injector annotation
	 * @param targets        the mixin's target classes
	 * @param resolve        a selector as the mixin's refmap resolves it
	 * @param defaultRequire the injections its config requires of an injector by default
	 */
	static String mark(ClassNode mixin, MethodNode handler, AnnotationNode injector, List<ClassNode> targets, UnaryOperator<String> resolve,
			int defaultRequire) {
		try {
			for (AnnotationNode other : Annotations.all(handler)) {
				if (other != injector && (PreflightAsm.isInjector(other.desc) || other.desc.equals(MixinNames.GROUP))) {
					throw new Refused("it is also annotated @" + PreflightAsm.simpleName(other.desc));
				}
			}
			AnnotationNode resolved = new AnnotationNode(injector.desc);
			injector.accept(resolved);
			resolveSelectors(resolved, resolve);
			Injector parsed = Injector.of(resolved);
			int require = Annotations.value(injector, "require") instanceof Integer r && r >= 0 ? r : defaultRequire;
			int allow = Annotations.value(injector, "allow") instanceof Integer a ? a : -1;
			for (ClassNode target : targets) {
				Map<MethodNode, List<AbstractInsnNode>> sites = sites(target, handler, parsed);
				if (sites.isEmpty()) throw new Refused("it targets no method of " + dotted(target));
				int injections = 0;
				for (Map.Entry<MethodNode, List<AbstractInsnNode>> e : sites.entrySet()) {
					if (e.getValue().isEmpty()) throw new Refused("it finds no " + parsed.describe() + " in " + where(e.getKey(), target));
					injections += e.getValue().size();
				}
				if (injections < require) throw new Refused("it injects " + injections + " time(s) in " + dotted(target) + ", where it requires " + require);
				if (allow >= 0 && injections > allow) throw new Refused("it injects " + injections + " time(s) in " + dotted(target) + ", where it allows " + allow);
			}
			String name = "fabricated$" + mixin.name.substring(mixin.name.lastIndexOf('/') + 1) + "$" + handler.name;
			for (MethodNode m : mixin.methods) {
				if (m != handler && m.name.equals(name) && m.desc.equals(handler.desc)) throw new Refused("the mixin already declares " + name + handler.desc);
			}
			rename(mixin, handler, name);
			if (handler.visibleAnnotations != null) handler.visibleAnnotations.remove(injector);
			if (handler.invisibleAnnotations != null) handler.invisibleAnnotations.remove(injector);
			if (handler.invisibleAnnotations == null) handler.invisibleAnnotations = new ArrayList<>();
			handler.invisibleAnnotations.add(resolved);
			AnnotationNode marker = new AnnotationNode(MARKER);
			Annotations.put(marker, "require", require);
			handler.invisibleAnnotations.add(marker);
			handler.access = handler.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
			return null;
		} catch (Refused e) {
			return e.getMessage();
		}
	}

	/** Whether the method is a handler {@link #mark} made, which Fabricated applies once Mixin has merged it. */
	public static boolean marked(MethodNode method) {
		return method.invisibleAnnotations != null && Annotations.find(method.invisibleAnnotations, MARKER) != null;
	}

	/**
	 * Applies the handlers {@link #mark} made that Mixin merged into a class, once the class's mixins have applied: each
	 * made private again and called where its injector would have Mixin call it. All the injection points are found
	 * before any is injected, as Mixin finds every injector's before injecting any (so a handler's cancellation, which
	 * returns, is no {@code RETURN} of another's), and a point a {@code @Redirect} or {@code @WrapOperation} replaced is
	 * where the handler's call is, as Mixin follows it.
	 *
	 * @param target the class, its mixins applied
	 * @param locals the locals Mixin works out, for the handlers that capture them
	 * @param warn   what Mixin would warn of: a handler skipped where it captures other locals than Mixin finds there
	 *               ({@code CAPTURE_FAILSOFT}), or where another handler already replaced the call it replaces
	 * @return the handlers applied
	 * @throws IllegalStateException where a handler injects fewer times than its injector requires, or doesn't fit what
	 *                               it finds, as Mixin then fails the class
	 */
	public static int apply(ClassNode target, LocalsAt locals, Consumer<String> warn) {
		record Planned(MethodNode handler, Injector injector, int require, int allow, Map<MethodNode, List<AbstractInsnNode>> sites) {
		}
		List<Planned> planned = new ArrayList<>();
		for (MethodNode handler : List.copyOf(target.methods)) {
			AnnotationNode marker = handler.invisibleAnnotations == null ? null : Annotations.find(handler.invisibleAnnotations, MARKER);
			if (marker == null) continue;
			AnnotationNode injector = handler.invisibleAnnotations.stream().filter(a -> INJECTORS.containsKey(a.desc)).findFirst().orElse(null);
			handler.invisibleAnnotations.remove(marker);
			handler.invisibleAnnotations.remove(injector);
			handler.access = handler.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC) | Opcodes.ACC_PRIVATE;
			try {
				if (injector == null) throw new Refused("its injector is missing");
				Injector parsed = Injector.of(injector);
				planned.add(new Planned(handler, parsed, Annotations.value(marker, "require") instanceof Integer r ? r : 0,
						Annotations.value(injector, "allow") instanceof Integer a ? a : -1, sites(target, handler, parsed)));
			} catch (Refused e) {
				throw failed(handler, target, e.getMessage());
			}
		}
		Map<AbstractInsnNode, AbstractInsnNode> replaced = new HashMap<>();
		for (Planned p : planned) {
			int injected = 0;
			for (Map.Entry<MethodNode, List<AbstractInsnNode>> e : p.sites().entrySet()) {
				for (AbstractInsnNode at : e.getValue()) {
					try {
						if (inject(target, e.getKey(), p.handler(), p.injector(), at, replaced, locals, warn)) injected++;
					} catch (Refused r) {
						throw failed(p.handler(), target, r.getMessage());
					}
				}
			}
			if (injected < p.require()) {
				throw failed(p.handler(), target, "it injects " + injected + " time(s), where its " + p.injector().kind() + " requires " + p.require());
			}
			if (p.allow() >= 0 && injected > p.allow()) {
				throw failed(p.handler(), target, "it injects " + injected + " time(s), where its " + p.injector().kind() + " allows " + p.allow());
			}
		}
		return planned.size();
	}

	private static IllegalStateException failed(MethodNode handler, ClassNode target, String why) {
		return new IllegalStateException("The handler " + handler.name + handler.desc + " of an interface mixin can't apply in " + dotted(target) + ": "
				+ why + " (INTERFACE-1)");
	}

	/**
	 * Each method of the target the injector targets, with the instructions Mixin finds there (each once, shifted where
	 * its {@code @At} is), where the handler fits what the injector gives it: as Mixin picks a method, one selector
	 * without a descriptor the first of its name.
	 */
	private static Map<MethodNode, List<AbstractInsnNode>> sites(ClassNode target, MethodNode handler, Injector injector) throws Refused {
		Map<MethodNode, List<AbstractInsnNode>> sites = new LinkedHashMap<>();
		int sugar = sugar(handler).size();
		Type[] args = Type.getArgumentTypes(handler.desc);
		Type[] own = Arrays.copyOf(args, args.length - sugar);
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		for (String selector : injector.methods()) {
			MemberRef ref = MemberRef.parse(selector);
			if (ref.fuzzy() || ref.name() == null) throw new Refused("it selects its target method by a pattern");
			for (MethodNode m : target.methods) {
				if (!ref.matchesMethod(m.name, m.desc) || marked(m)) continue;
				// As Mixin: a method two selectors name is injected once.
				if (!sites.containsKey(m)) sites.put(m, null);
				if (ref.desc() == null) break;
			}
		}
		for (MethodNode m : sites.keySet()) {
			String where = where(m, target);
			if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) throw new Refused("it targets " + where + ", which has no code");
			boolean targetStatic = (m.access & Opcodes.ACC_STATIC) != 0;
			// Mixin 0.8.5's CallbackInjector has the modifiers match exactly; its other injectors refuse a handler that isn't
			// static only on a static target.
			if (injector.desc().equals(MixinNames.INJECT) ? handlerStatic != targetStatic : targetStatic && !handlerStatic) {
				throw new Refused("its handler is " + (handlerStatic ? "" : "not ") + "static where " + where + " is" + (handlerStatic ? " not" : ""));
			}
			List<AbstractInsnNode> found = injector.find(m);
			for (AbstractInsnNode at : found) injector.check(own, Type.getReturnType(handler.desc), m, at, where);
			sites.put(m, found);
		}
		return sites;
	}

	/**
	 * Calls the handler where it injects at {@code found} (or where the call of another handler that replaced it is), as
	 * its injector would have Mixin call it. Returns whether it did: not where it captures other locals than Mixin finds
	 * there ({@code CAPTURE_FAILSOFT}) or replaces what another handler already replaced, as Mixin skips them.
	 */
	private static boolean inject(ClassNode owner, MethodNode method, MethodNode handler, Injector injector, AbstractInsnNode found,
			Map<AbstractInsnNode, AbstractInsnNode> replaced, LocalsAt locals, Consumer<String> warn) throws Refused {
		AbstractInsnNode at = replaced.getOrDefault(found, found);
		String where = where(method, owner);
		if (!injector.desc().equals(MixinNames.INJECT) && at != found) {
			warn.accept(injector.kind() + " handler " + handler.name + " of an interface mixin is skipped in " + where + ": another interface mixin's"
					+ " handler already replaced what it replaces there, which Fabricated doesn't chain (INTERFACE-1)");
			return false;
		}
		Type[] args = Type.getArgumentTypes(handler.desc);
		List<AnnotationNode> sugar = sugar(handler);
		int own = args.length - sugar.size();
		InsnList sugarLoads = new InsnList();
		for (int i = own; i < args.length; i++) {
			sugarLoads.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD), local(owner, method, at, args[i], sugar.get(i - own), locals, where)));
		}
		InsnList code = new InsnList();
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		switch (injector.desc()) {
			case MixinNames.INJECT -> {
				return callback(owner, method, handler, injector, at, own, sugarLoads, locals, warn, where);
			}
			case MixinNames.MODIFY_CONSTANT -> {
				if (!handlerStatic) method.instructions.insertBefore(at, new VarInsnNode(Opcodes.ALOAD, 0));
				loadArguments(code, method, own - 1);
				code.add(sugarLoads);
				AbstractInsnNode call = invoke(owner, handler);
				code.add(call);
				method.instructions.insert(at, code);
				replaced.put(at, call);
			}
			default -> {
				// A @Redirect or @WrapOperation of a call: its receiver and arguments stored, then given to the handler.
				MethodInsnNode original = (MethodInsnNode) at;
				Type[] stack = stack(original);
				int[] slots = new int[stack.length];
				for (int i = 0; i < stack.length; i++) {
					slots[i] = method.maxLocals;
					method.maxLocals += stack[i].getSize();
				}
				for (int i = stack.length - 1; i >= 0; i--) code.add(new VarInsnNode(stack[i].getOpcode(Opcodes.ISTORE), slots[i]));
				if (!handlerStatic) code.add(new VarInsnNode(Opcodes.ALOAD, 0));
				for (int i = 0; i < stack.length; i++) code.add(new VarInsnNode(stack[i].getOpcode(Opcodes.ILOAD), slots[i]));
				int extra = own - stack.length;
				if (injector.desc().equals(MixinNames.WRAP_OPERATION)) {
					code.add(operation(owner, handler, original, stack));
					extra--;
				}
				loadArguments(code, method, extra);
				code.add(sugarLoads);
				AbstractInsnNode call = invoke(owner, handler);
				code.add(call);
				method.instructions.insertBefore(original, code);
				method.instructions.remove(original);
				replaced.put(original, call);
			}
		}
		method.maxStack += argumentSlots(handler.desc) + 2;
		return true;
	}

	/**
	 * Calls an {@code @Inject} handler before {@code at}, as Mixin's {@code CallbackInjector}: the callback info made for
	 * the call (at a return of a value, holding the value), the handler given the target's parameters where it takes
	 * them, then the locals it captures, the first non-argument ones Mixin finds there, and where it is cancellable a
	 * cancelled call returning (the value the callback info holds). Returns whether it did: not where it captures other
	 * locals than those, which Mixin warns of where it captures them {@code CAPTURE_FAILSOFT}, and fails the class otherwise.
	 */
	private static boolean callback(ClassNode owner, MethodNode method, MethodNode handler, Injector injector, AbstractInsnNode at, int own,
			InsnList sugarLoads, LocalsAt locals, Consumer<String> warn, String where) throws Refused {
		Type returnType = Type.getReturnType(method.desc);
		Type[] args = Type.getArgumentTypes(method.desc);
		Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		int info = own == 1 ? 0 : args.length;
		InsnList captured = new InsnList();
		if (own > info + 1) {
			List<Type> wanted = List.of(handlerArgs).subList(info + 1, own);
			String[] there = locals.at(method, at);
			List<Type> available = new ArrayList<>();
			for (int slot = firstLocal(method); there != null && slot < there.length && available.size() < wanted.size(); slot++) {
				if (there[slot] == null) continue;
				Type type = Type.getType(there[slot]);
				available.add(type);
				captured.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot));
			}
			if (!available.equals(wanted)) {
				String message = "@Inject handler " + handler.name + " of an interface mixin captures the locals " + names(wanted) + " in " + where
						+ ", where Mixin finds " + (there == null ? "none it can tell" : names(available));
				if (!"CAPTURE_FAILSOFT".equals(injector.capture())) throw new Refused(message);
				warn.accept(message + "; it is skipped there, as Mixin skips it (CAPTURE_FAILSOFT, INTERFACE-1)");
				return false;
			}
		}
		boolean atValue = at.getOpcode() >= Opcodes.IRETURN && at.getOpcode() <= Opcodes.ARETURN;
		boolean object = returnType.getSort() >= Type.ARRAY;
		String infoClass = returnType.getSort() == Type.VOID ? CALLBACK_INFO : CALLBACK_INFO_RETURNABLE;
		InsnList code = new InsnList();
		int value = -1;
		if (atValue) {
			value = method.maxLocals;
			method.maxLocals += returnType.getSize();
			code.add(new InsnNode(returnType.getSize() == 2 ? Opcodes.DUP2 : Opcodes.DUP));
			code.add(new VarInsnNode(returnType.getOpcode(Opcodes.ISTORE), value));
		}
		int callbackInfo = method.maxLocals++;
		code.add(new TypeInsnNode(Opcodes.NEW, infoClass));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new LdcInsnNode(injector.id().isEmpty() ? method.name : injector.id()));
		code.add(new InsnNode(injector.cancellable() ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
		String constructor = "(Ljava/lang/String;Z)V";
		if (atValue) {
			code.add(new VarInsnNode(returnType.getOpcode(Opcodes.ILOAD), value));
			constructor = "(Ljava/lang/String;Z" + (object ? "Ljava/lang/Object;" : returnType.getDescriptor()) + ")V";
		}
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, infoClass, "<init>", constructor, false));
		code.add(new VarInsnNode(Opcodes.ASTORE, callbackInfo));
		if (!isStatic) code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		loadArguments(code, method, info);
		code.add(new VarInsnNode(Opcodes.ALOAD, callbackInfo));
		code.add(captured);
		code.add(sugarLoads);
		code.add(invoke(owner, handler));
		if (injector.cancellable()) {
			LabelNode carryOn = new LabelNode();
			code.add(new VarInsnNode(Opcodes.ALOAD, callbackInfo));
			code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, infoClass, "isCancelled", "()Z", false));
			code.add(new JumpInsnNode(Opcodes.IFEQ, carryOn));
			if (returnType.getSort() == Type.VOID) {
				code.add(new InsnNode(Opcodes.RETURN));
			} else {
				code.add(new VarInsnNode(Opcodes.ALOAD, callbackInfo));
				code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, infoClass, object ? "getReturnValue" : "getReturnValue" + returnType.getDescriptor(),
						"()" + (object ? "Ljava/lang/Object;" : returnType.getDescriptor()), false));
				if (object) code.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
				code.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
			}
			code.add(carryOn);
		}
		method.instructions.insertBefore(at, code);
		// What the call needs on top of what the method has there: the callback info's construction (with the value), or the
		// handler's receiver, parameters, callback info and locals.
		method.maxStack += Math.max(4 + 2 * returnType.getSize(), 2 + argumentSlots(handler.desc));
		return true;
	}

	/**
	 * The {@code Operation} of a {@code @WrapOperation} of {@code call}, as MixinExtras makes one: a lambda of a bridge
	 * the target declares, which checks it is given the call's receiver and arguments, unboxes them, does the call and
	 * returns what it returns, boxed.
	 */
	private static AbstractInsnNode operation(ClassNode owner, MethodNode handler, MethodInsnNode call, Type[] stack) {
		Type returnType = Type.getReturnType(call.desc);
		Type boxed = boxed(returnType);
		String name = "fabricated$operation$" + handler.name;
		Set<String> taken = owner.methods.stream().map(m -> m.name).collect(Collectors.toSet());
		for (int n = 1; taken.contains(name); n++) name = "fabricated$operation$" + handler.name + "$" + n;
		MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name,
				"([Ljava/lang/Object;)" + boxed.getDescriptor(), null, null);
		InsnList code = bridge.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new LdcInsnNode(stack.length));
		code.add(new LdcInsnNode(Arrays.stream(stack).map(Type::getClassName).collect(Collectors.joining(", ", "[", "]"))));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime", "checkArgumentCount",
				"([Ljava/lang/Object;ILjava/lang/String;)V", false));
		for (int i = 0; i < stack.length; i++) {
			code.add(new VarInsnNode(Opcodes.ALOAD, 0));
			code.add(new LdcInsnNode(i));
			code.add(new InsnNode(Opcodes.AALOAD));
			Type type = stack[i];
			if (type.getSort() < Type.ARRAY) {
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, boxed(type).getInternalName()));
				code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, boxed(type).getInternalName(), type.getClassName() + "Value", "()" + type.getDescriptor(), false));
			} else if (!type.getDescriptor().equals("Ljava/lang/Object;")) {
				code.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
			}
		}
		code.add(new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf));
		if (returnType.getSort() == Type.VOID) {
			code.add(new InsnNode(Opcodes.ACONST_NULL));
		} else if (returnType.getSort() < Type.ARRAY) {
			code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, boxed.getInternalName(), "valueOf", "(" + returnType.getDescriptor() + ")" + boxed.getDescriptor(), false));
		}
		code.add(new InsnNode(Opcodes.ARETURN));
		bridge.maxLocals = 1;
		bridge.maxStack = Math.max(3, Arrays.stream(stack).mapToInt(Type::getSize).sum() + 2);
		owner.methods.add(bridge);
		boolean itf = (owner.access & Opcodes.ACC_INTERFACE) != 0;
		return new InvokeDynamicInsnNode("call", "()L" + OPERATION + ";", METAFACTORY, Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Object;"),
				new Handle(Opcodes.H_INVOKESTATIC, owner.name, name, bridge.desc, itf), Type.getMethodType(bridge.desc));
	}

	/**
	 * The slot of the local a handler's {@code @Local} parameter takes at {@code at}, as Mixin's discriminator finds it in
	 * the locals Mixin works out there (or in {@code this} and the target's parameters, {@code argsOnly}), past
	 * {@code this}: the one of its type with its ordinal (counted from {@code this}, as Mixin counts) or at its index, or,
	 * with neither, the only one of its type.
	 */
	private static int local(ClassNode owner, MethodNode method, AbstractInsnNode at, Type type, AnnotationNode local, LocalsAt locals, String where)
			throws Refused {
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		String[] slots;
		if (Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))) {
			slots = new String[firstLocal(method)];
			int slot = 0;
			if (!isStatic) slots[slot++] = Type.getObjectType(owner.name).getDescriptor();
			for (Type arg : Type.getArgumentTypes(method.desc)) {
				slots[slot] = arg.getDescriptor();
				slot += arg.getSize();
			}
		} else {
			slots = locals.at(method, at);
			if (slots == null) throw new Refused("its @Local " + type.getClassName() + " finds no locals Mixin can tell in " + where);
		}
		int ordinal = Annotations.value(local, "ordinal") instanceof Integer o ? o : -1;
		int index = Annotations.value(local, "index") instanceof Integer i ? i : -1;
		int base = isStatic ? 0 : 1;
		List<Integer> ofType = new ArrayList<>();
		int seen = 0;
		for (int slot = 0; slot < slots.length; slot++) {
			if (!type.getDescriptor().equals(slots[slot])) continue;
			if (slot >= base && (ordinal >= 0 ? seen == ordinal : slot == index)) return slot;
			if (slot >= base) ofType.add(slot);
			seen++;
		}
		if (ordinal < 0 && index < base && ofType.size() == 1) return ofType.get(0);
		throw new Refused("its @Local " + type.getClassName() + (ordinal >= 0 ? " with ordinal " + ordinal : index >= base ? " at " + index : "")
				+ " finds " + (ofType.isEmpty() ? "no local" : ofType.size() + " locals") + " of that type in " + where);
	}

	/** The handler's {@code @Local} parameters, which come last, each's annotation; refused where it has other sugar. */
	private static List<AnnotationNode> sugar(MethodNode handler) throws Refused {
		Type[] args = Type.getArgumentTypes(handler.desc);
		List<AnnotationNode> sugar = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			AnnotationNode local = null;
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (a.desc.equals(MixinNames.LOCAL)) {
					refuseAttributes(a, LOCAL_KEYS, "@Local");
					local = a;
				} else if (a.desc.startsWith("Lorg/spongepowered/") || a.desc.startsWith("Lcom/llamalad7/")) {
					throw new Refused("its handler's parameter " + (i + 1) + " is annotated @" + PreflightAsm.simpleName(a.desc));
				}
			}
			if (local != null && args[i].getSort() == Type.OBJECT && args[i].getInternalName().startsWith("com/llamalad7/mixinextras/sugar/ref/")) {
				throw new Refused("its handler's @Local takes a reference to the local");
			}
			if (local != null) sugar.add(local);
			else if (!sugar.isEmpty()) throw new Refused("its handler takes parameter " + (i + 1) + " after its @Local");
		}
		return sugar;
	}

	/** An injector Fabricated applies, its selectors resolved: the methods it targets, its points and what it does there. */
	private record Injector(String desc, List<String> methods, List<Point> points, boolean cancellable, String capture, String id) {
		static Injector of(AnnotationNode ann) throws Refused {
			Set<String> keys = INJECTORS.get(ann.desc);
			if (keys == null) throw new Refused("it is a @" + PreflightAsm.simpleName(ann.desc));
			refuseAttributes(ann, keys, "@" + PreflightAsm.simpleName(ann.desc));
			boolean inject = ann.desc.equals(MixinNames.INJECT);
			List<String> methods = new ArrayList<>();
			if (Annotations.value(ann, "method") instanceof List<?> selectors) {
				for (Object o : selectors) {
					if (o instanceof String s) methods.add(s);
				}
			}
			List<Point> points = new ArrayList<>();
			for (AnnotationNode constant : Annotations.list(Annotations.value(ann, "constant"))) {
				refuseAttributes(constant, CONSTANT_KEYS, "@Constant");
				Object value = CONSTANT_VALUES.stream().map(k -> Annotations.value(constant, k)).filter(v -> v != null).findFirst()
						.orElseThrow(() -> new Refused("its @Constant names no value"));
				points.add(new Point("CONSTANT", null, ordinal(constant), -1, 0, value));
			}
			for (AnnotationNode at : Annotations.list(Annotations.value(ann, "at"))) {
				refuseAttributes(at, AT_KEYS, "@At");
				String value = Annotations.value(at, "value") instanceof String v ? v : "";
				String target = Annotations.value(at, "target") instanceof String t ? t : null;
				int ordinal = ordinal(at);
				String shift = Annotations.value(at, "shift") instanceof String[] s ? s[1] : "NONE";
				boolean member = value.equals("INVOKE") || value.equals("FIELD");
				if (!(inject ? Set.of("HEAD", "RETURN", "TAIL", "INVOKE", "FIELD") : Set.of("INVOKE")).contains(value)) throw new Refused("it injects at " + value);
				if (ordinal >= 0 && (value.equals("HEAD") || value.equals("TAIL"))) throw new Refused("it injects at an ordinal of " + value);
				if (member && (target == null || MemberRef.parse(target).fuzzy() || MemberRef.parse(target).name() == null)) {
					throw new Refused("its @At(" + value + ") names no " + (value.equals("FIELD") ? "field" : "method") + " plainly");
				}
				if (!shift.equals("NONE") && !(inject && (shift.equals("BEFORE") || shift.equals("AFTER")))) throw new Refused("its @At shifts " + shift);
				int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
				points.add(new Point(value, target, ordinal, opcode, shift.equals("BEFORE") ? -1 : shift.equals("AFTER") ? 1 : 0, null));
			}
			if (points.isEmpty()) throw new Refused("it names no injection point");
			String capture = Annotations.value(ann, "locals") instanceof String[] e && !e[1].equals("NO_CAPTURE") ? e[1] : null;
			if (capture != null && !capture.equals("CAPTURE_FAILSOFT") && !capture.equals("CAPTURE_FAILHARD")) throw new Refused("it captures locals " + capture);
			return new Injector(ann.desc, methods, points, Boolean.TRUE.equals(Annotations.value(ann, "cancellable")), capture,
					Annotations.value(ann, "id") instanceof String id ? id : "");
		}

		String kind() {
			return "@" + PreflightAsm.simpleName(desc);
		}

		String describe() {
			return points.stream().map(Point::toString).collect(Collectors.joining(" or "));
		}

		/** The instructions its points pick in a method, each once, in the order the points name them, shifted as they say. */
		List<AbstractInsnNode> find(MethodNode method) throws Refused {
			InsnList insns = method.instructions;
			Set<AbstractInsnNode> found = new LinkedHashSet<>();
			for (Point point : points) {
				List<AbstractInsnNode> returns = new ArrayList<>();
				for (AbstractInsnNode insn : insns) {
					if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) returns.add(insn);
				}
				List<AbstractInsnNode> picked = new ArrayList<>();
				switch (point.value()) {
					case "HEAD" -> {
						if (insns.size() > 0) picked.add(insns.getFirst());
					}
					case "TAIL" -> {
						if (!returns.isEmpty()) picked.add(returns.get(returns.size() - 1));
					}
					case "RETURN" -> {
						if (point.ordinal() < 0) picked.addAll(returns);
						else if (point.ordinal() < returns.size()) picked.add(returns.get(point.ordinal()));
					}
					case "CONSTANT" -> {
						int seen = 0;
						for (AbstractInsnNode insn : insns) {
							if (!point.constant().equals(InjectionPoints.constantOf(insn))) continue;
							if (point.ordinal() < 0 || seen == point.ordinal()) picked.add(insn);
							seen++;
						}
					}
					default -> {
						for (int index : InjectionPoints.indexes(point.value(), point.target(), point.ordinal(), point.opcode(), Range.ALL, method)) {
							picked.add(insns.get(index));
						}
					}
				}
				for (AbstractInsnNode insn : picked) {
					int index = insns.indexOf(insn) + point.shift();
					if (index < 0 || index >= insns.size()) throw new Refused("its shifted @At is outside " + method.name + method.desc);
					found.add(insns.get(index));
				}
			}
			return List.copyOf(found);
		}

		/**
		 * Checks the handler fits what the injector gives it at {@code at}, as Mixin's injector does: the callback info
		 * (with the target's parameters before it, and locals after it where it captures them); the call's receiver and
		 * arguments, returning what it does (with the {@code Operation} after them); the constant, returning its type.
		 * Each but the {@code @Inject} may take a prefix of the target's parameters after those.
		 */
		void check(Type[] own, Type returnType, MethodNode method, AbstractInsnNode at, String where) throws Refused {
			Type[] targetArgs = Type.getArgumentTypes(method.desc);
			Type[] expected;
			Type expectedReturn;
			if (desc.equals(MixinNames.INJECT)) {
				if (returnType.getSort() != Type.VOID) throw new Refused("its handler returns a value");
				String info = Type.getReturnType(method.desc).getSort() == Type.VOID ? CALLBACK_INFO : CALLBACK_INFO_RETURNABLE;
				int index = own.length == 1 ? 0 : targetArgs.length;
				if (own.length == 0 || index >= own.length || !own[index].getDescriptor().equals("L" + info + ";")) {
					throw new Refused("its handler doesn't take the callback info " + where + " gives");
				}
				if (!Arrays.equals(own, 0, index, targetArgs, 0, index)) throw new Refused("its handler's parameters aren't those of " + where);
				if (own.length > index + 1 && capture == null) throw new Refused("its handler takes more than the callback info " + where + " gives");
				// Capturing, Mixin gives the target's parameters before the callback info.
				if (capture != null && index == 0 && targetArgs.length > 0) throw new Refused("its handler captures locals without the parameters of " + where);
				return;
			}
			if (desc.equals(MixinNames.MODIFY_CONSTANT)) {
				Object constant = InjectionPoints.constantOf(at);
				Type type = constant instanceof Integer ? Type.INT_TYPE : constant instanceof Float ? Type.FLOAT_TYPE : constant instanceof Long ? Type.LONG_TYPE
						: constant instanceof Double ? Type.DOUBLE_TYPE : constant instanceof String ? Type.getType(String.class) : Type.getType(Class.class);
				expected = new Type[] {type};
				expectedReturn = type;
			} else {
				MethodInsnNode call = (MethodInsnNode) at;
				if (call.getOpcode() == Opcodes.INVOKESPECIAL) throw new Refused("it replaces a constructor's or super call in " + where);
				expected = stack(call);
				if (desc.equals(MixinNames.WRAP_OPERATION)) {
					expected = Arrays.copyOf(expected, expected.length + 1);
					expected[expected.length - 1] = Type.getObjectType(OPERATION);
				}
				expectedReturn = Type.getReturnType(call.desc);
			}
			int extra = own.length - expected.length;
			if (!returnType.equals(expectedReturn) || extra < 0 || extra > targetArgs.length || !Arrays.equals(own, 0, expected.length, expected, 0, expected.length)
					|| !Arrays.equals(own, expected.length, own.length, targetArgs, 0, extra)) {
				throw new Refused("its handler doesn't take and return what its " + kind() + " gives it in " + where + ": "
						+ Type.getMethodDescriptor(expectedReturn, expected));
			}
		}
	}

	/** An injection point: an {@code @At}'s (its value, target, ordinal, opcode and shift) or a {@code @Constant}'s. */
	private record Point(String value, String target, int ordinal, int opcode, int shift, Object constant) {
		@Override
		public String toString() {
			String what = constant != null ? value + " " + constant : target != null ? value + " " + target : value;
			return ordinal >= 0 ? what + ":" + ordinal : what;
		}
	}

	/** Why Fabricated can't apply an injector, as the report gives it. */
	private static final class Refused extends Exception {
		private static final long serialVersionUID = 1L;

		Refused(String why) {
			super(why, null, false, false);
		}
	}

	/** Has a copy of an injector name its target methods and calls as the mixin's refmap resolves them. */
	private static void resolveSelectors(AnnotationNode injector, UnaryOperator<String> resolve) {
		if (Annotations.value(injector, "method") instanceof List<?> selectors) {
			List<Object> resolved = new ArrayList<>();
			for (Object o : selectors) resolved.add(o instanceof String s ? resolve.apply(s) : o);
			Annotations.put(injector, "method", resolved);
		}
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			if (Annotations.value(at, "target") instanceof String target) Annotations.put(at, "target", resolve.apply(target));
		}
	}

	private static void refuseAttributes(AnnotationNode annotation, Set<String> known, String kind) throws Refused {
		if (annotation.values == null) return;
		for (int i = 0; i < annotation.values.size(); i += 2) {
			if (!known.contains(annotation.values.get(i))) throw new Refused("its " + kind + " sets " + annotation.values.get(i));
		}
	}

	private static int ordinal(AnnotationNode annotation) {
		return Annotations.value(annotation, "ordinal") instanceof Integer o ? o : -1;
	}

	/** What a call takes off the stack: its receiver, unless it is static, and its arguments. */
	private static Type[] stack(MethodInsnNode call) {
		Type[] args = Type.getArgumentTypes(call.desc);
		if (call.getOpcode() == Opcodes.INVOKESTATIC) return args;
		Type[] stack = new Type[args.length + 1];
		stack[0] = Type.getObjectType(call.owner);
		System.arraycopy(args, 0, stack, 1, args.length);
		return stack;
	}

	/** Loads the first {@code count} of the method's parameters. */
	private static void loadArguments(InsnList code, MethodNode method, int count) {
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		Type[] args = Type.getArgumentTypes(method.desc);
		for (int i = 0; i < count; i++) {
			code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD), slot));
			slot += args[i].getSize();
		}
	}

	/** The call of a handler merged into {@code owner}, made private again. */
	private static MethodInsnNode invoke(ClassNode owner, MethodNode handler) {
		boolean isStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		return new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, owner.name, handler.name, handler.desc,
				(owner.access & Opcodes.ACC_INTERFACE) != 0);
	}

	private static int firstLocal(MethodNode method) {
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) slot += arg.getSize();
		return slot;
	}

	private static int argumentSlots(String desc) {
		return (Type.getArgumentsAndReturnSizes(desc) >> 2) - 1;
	}

	private static Type boxed(Type type) {
		return switch (type.getSort()) {
			case Type.VOID -> Type.getObjectType("java/lang/Void");
			case Type.BOOLEAN -> Type.getObjectType("java/lang/Boolean");
			case Type.CHAR -> Type.getObjectType("java/lang/Character");
			case Type.BYTE -> Type.getObjectType("java/lang/Byte");
			case Type.SHORT -> Type.getObjectType("java/lang/Short");
			case Type.INT -> Type.getObjectType("java/lang/Integer");
			case Type.FLOAT -> Type.getObjectType("java/lang/Float");
			case Type.LONG -> Type.getObjectType("java/lang/Long");
			case Type.DOUBLE -> Type.getObjectType("java/lang/Double");
			default -> type;
		};
	}

	private static String names(List<Type> types) {
		return types.stream().map(Type::getClassName).collect(Collectors.joining(", ", "[", "]"));
	}

	private static String where(MethodNode method, ClassNode owner) {
		return method.name + method.desc + " in " + dotted(owner);
	}

	private static String dotted(ClassNode node) {
		return node.name.replace('/', '.');
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
}
