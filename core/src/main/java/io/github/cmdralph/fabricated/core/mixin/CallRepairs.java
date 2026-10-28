package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;
import io.github.cmdralph.fabricated.core.mixin.PreflightClasses.Code;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for calls the game makes differently: through Fabric Loader's hooks, a subtype, a NeoForge hook
 * that took the call over, or an overload taking one more argument.
 */
final class CallRepairs extends PreflightPart {
	CallRepairs(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Points an {@code @Inject}'s {@code INVOKE} points at one of Fabric Loader's hooks ({@link MixinNames#LOADER_HOOKS}) at the call
	 * this game build makes at that step, when the methods it targets make that call once and not the hook's. Shifted as
	 * the mod shifts it: after the hook is after every mod's entrypoints, on both. Returns a description of each move.
	 */
	static List<String> loaderHookRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)
					|| Annotations.value(at, "ordinal") instanceof Integer ordinal && ordinal > 0) continue;
			String resolved = ctx.resolve(raw);
			String step = MixinNames.LOADER_HOOKS.get(resolved);
			if (step == null) continue;
			MemberRef hook = MemberRef.parse(resolved);
			MemberRef call = MemberRef.parse(step);
			if (calls(methods, hook) != 0 || calls(methods, call) != 1) continue;
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, step);
			}
			moved.add(hook.owner().replace('/', '.') + "." + hook.name() + ", which Fabric Loader adds where it runs mods' entrypoints;"
					+ " the injection point is pointed at " + call.owner().replace('/', '.') + "." + call.name() + ", where they run here");
		}
		return moved;
	}

	/** How many calls to {@code ref} (owner, name and descriptor) the methods make. */
	private static int calls(List<MethodNode> methods, MemberRef ref) {
		int n = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(ref.owner()) && call.name.equals(ref.name()) && call.desc.equals(ref.desc())) n++;
			}
		}
		return n;
	}

	/**
	 * Points the {@code INVOKE} points of an {@code @Inject} or {@code @ModifyExpressionValue} at a call this game build
	 * makes through a subtype of the class they name, when the named call is in none of the methods the injector
	 * targets: the same method, inherited. (NeoForge wraps vanilla's {@code Decoder} in a conditional {@code Codec} and
	 * calls {@code parse} on that.) Mixin matches a call's owner exactly. Neither injector's handler takes the call's
	 * receiver, so its type changing doesn't matter; the arguments and the result are the same. Only one subtype may
	 * make the call. Returns a description of each move.
	 */
	List<String> subtypeCallRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.INJECT) && !injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
			String subtype = null;
			int calls = 0;
			boolean named = false;
			boolean ambiguous = false;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || !ref.matchesMethod(call.name, call.desc)) continue;
					if (call.owner.equals(ref.owner())) {
						named = true;
					} else if (isSubtype(call.owner, ref.owner())) {
						ambiguous |= subtype != null && !subtype.equals(call.owner);
						subtype = call.owner;
						calls++;
					}
				}
			}
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || subtype == null || ambiguous || ordinal >= calls) continue;
			String retargeted = "L" + subtype + ";" + ref.name() + ref.desc();
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, retargeted);
			}
			moved.add(ref.owner().replace('/', '.') + "." + ref.name() + ", which this game build calls through "
					+ subtype.replace('/', '.') + " (the same method); the injection point is pointed there");
		}
		return moved;
	}

	/**
	 * Reads an injector's points that pick one of a target method's calls, field accesses or constructions by ordinal
	 * against vanilla's version of the method, when there is one (VANILLA-1). NeoForge's patches add and replace such
	 * instructions, so this game build's ordinal-th may not be the one the mod was written for, or there may be fewer.
	 * Vanilla's instructions aligned with this game build's ({@link PatchAlignment}) tell where vanilla's went: where it
	 * is still there, the point's ordinal becomes its ordinal here; where NeoForge's patch put a call of an overload taking
	 * one more argument in its place (its camera passes a roll along that way), the point is pointed at that call, with
	 * its handler bridged as {@link #insertedArgumentRepairs} does. A point inside a slice, or a target method vanilla
	 * doesn't declare, is left as it is. Returns a description of each change.
	 */
	List<String> vanillaPointRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (Annotations.value(injector, "slice") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = PreflightAsm.declared(original, patched.name, patched.desc);
		if (before == null) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		PatchAlignment alignment = null;
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "ordinal") instanceof Integer ordinal) || ordinal < 0
					|| !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy()) continue;
			int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
			List<Integer> inVanilla = PreflightAsm.matching(before, point, ref, opcode);
			if (ordinal >= inVanilla.size()) continue;
			if (alignment == null) alignment = PatchAlignment.of(before, patched);
			if (alignment == null) return moved;
			String what = (point.equals("INVOKE") ? "the call " : point.equals("FIELD") ? "the access " : "the construction ") + raw
					+ " with ordinal " + ordinal + " in vanilla's code, which ";
			int place = alignment.placeOf(inVanilla.get(ordinal));
			if (place == PatchAlignment.UNCERTAIN) continue;
			if (place >= 0) {
				int now = PreflightAsm.matching(patched, point, ref, opcode).indexOf(place);
				if (now < 0 || now == ordinal) continue;
				Annotations.put(at, "ordinal", now);
				moved.add(what + "has ordinal " + now + " in this game build, as " + loader + "'s patches add or replace others; the injection point is pointed there");
				continue;
			}
			boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
			if (!point.equals("INVOKE") || ref.owner() == null || ref.name() == null || ref.desc() == null || !pointOnly && ats.size() != 1
					|| !pointOnly && !MixinNames.WRAP_WITH_CONDITION.contains(injector.desc) && !injector.desc.equals(MixinNames.REDIRECT) && !injector.desc.equals(MixinNames.WRAP_OPERATION)) continue;
			ClassNode owner = gameTarget(ref.owner());
			MethodInsnNode replacement = replacingOverload(patched, alignment.replacementOf(inVanilla.get(ordinal)), ref, owner);
			if (replacement == null) continue;
			int inserted = insertedArgument(owner, ref.name(), ref.desc(), replacement.desc);
			if (!pointOnly && !bridgeTo(mixin, handler, injector, ref.owner(), ref.desc(), replacement.desc, inserted, replacement.getOpcode() == Opcodes.INVOKESTATIC,
					bridges)) continue;
			int now = 0;
			for (AbstractInsnNode insn = patched.instructions.getFirst(); insn != replacement; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(replacement.owner) && call.name.equals(replacement.name) && call.desc.equals(replacement.desc)) now++;
			}
			Annotations.put(at, "target", "L" + replacement.owner + ";" + replacement.name + replacement.desc);
			Annotations.put(at, "ordinal", now);
			moved.add(what + loader + "'s patch replaced with a call of " + replacement.name + replacement.desc + ", taking one more argument (ordinal " + now
					+ " here); the injection point is pointed there" + (pointOnly ? "" : ", through a bridge handler"));
		}
		return moved;
	}

	/**
	 * The one call, among the patched instructions {@code range} ({@code [from, to)}, null for none), of an overload of the
	 * named method taking one more argument (as {@code owner}, the named method's class, tells it); null when there is none
	 * or more than one.
	 */
	private static MethodInsnNode replacingOverload(MethodNode patched, int[] range, MemberRef ref, ClassNode owner) {
		if (range == null) return null;
		MethodInsnNode found = null;
		for (int i = range[0]; i < range[1]; i++) {
			if (!(patched.instructions.get(i) instanceof MethodInsnNode call) || !call.owner.equals(ref.owner()) || !call.name.equals(ref.name())
					|| insertedArgument(owner, ref.name(), ref.desc(), call.desc) < 0) continue;
			if (found != null) return null;
			found = call;
		}
		return found;
	}

	/**
	 * Gives a {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} handler written for a call of
	 * {@code owner} with {@code desc} a bridge taking the {@code overload} its point moves to, which has one more argument
	 * at {@code inserted}, and moves the injector onto it. Whether it could.
	 */
	private static boolean bridgeTo(ClassNode mixin, MethodNode handler, AnnotationNode injector, String owner, String desc, String overload, int inserted,
			boolean staticCall, List<MethodNode> bridges) {
		Type ownerType = Type.getObjectType(owner);
		MethodNode bridge = injector.desc.equals(MixinNames.WRAP_OPERATION) ? HandlerBridges.wrapperBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall, bridges)
				: HandlerBridges.callBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall,
						MixinNames.WRAP_WITH_CONDITION.contains(injector.desc) ? Type.BOOLEAN_TYPE : Type.getReturnType(desc), bridges);
		if (bridge == null) return false;
		HandlerBridges.moveInjector(handler, injector, bridge);
		bridges.add(bridge);
		return true;
	}

	/**
	 * Points the {@code INVOKE} and {@code NEW} points of an injector at the call this game build makes instead of the named one, an
	 * overload of the same method taking one more argument (NeoForge's patches pass an item stack or a level along that
	 * way), when the named call is in none of the methods the injector targets and that overload is the only one there.
	 * The handler of an {@code @Inject} or {@code @ModifyExpressionValue} doesn't see the arguments, so its {@code INVOKE}
	 * point also moves to an overload that takes more after them ({@link #appendsArguments}). One of a
	 * {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} that takes exactly the call's receiver and
	 * arguments (and the {@code Operation}), and then any MixinExtras sugar, gets a bridge taking the overload's, which calls it without the added
	 * argument; for {@code @WrapOperation}, with an {@code Operation} that puts it back when the original call is made.
	 * A {@code NEW} point selecting a constructor by its descriptor is moved the same way, to a constructor taking one
	 * more argument (NeoForge's patches give records components): its handlers take the arguments, as a static call's.
	 * An {@code INVOKE} point at a call NeoForge's patches moved into a hook of theirs moves to the hook's call
	 * ({@link #hookCall}), its handlers bridged the same way when the hook takes one more argument, when the handler shares
	 * none of the mixin's own state with its other injectors ({@link MixinPreflight#independentHandlers}) (HOOK-2).
	 * Returns a description of each move.
	 */
	List<String> insertedArgumentRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		// Only the point moves for these: their handlers don't see the call's arguments.
		boolean pointOnly = injector.desc.equals(MixinNames.INJECT) || injector.desc.equals(MixinNames.MODIFY_EXPRESSION_VALUE);
		boolean condition = MixinNames.WRAP_WITH_CONDITION.contains(injector.desc);
		if (!pointOnly && !condition && !injector.desc.equals(MixinNames.REDIRECT) && !injector.desc.equals(MixinNames.WRAP_OPERATION) || Annotations.value(injector, "slice") != null
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (methods.isEmpty() || !pointOnly && ats.size() != 1) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			boolean constructor = point.equals("NEW");
			if (!point.equals("INVOKE") && !constructor || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			// A call is owner.name(desc). A constructor is selected by a descriptor (args)Lowner; which its handlers are written
			// for as for a static factory: compared, and bridged, in that form.
			String owner;
			String name;
			String desc;
			if (constructor) {
				desc = ref.methodDescOr(null);
				if (desc == null || Type.getReturnType(desc).getSort() != Type.OBJECT || condition) continue;
				owner = Type.getReturnType(desc).getInternalName();
				name = "<init>";
			} else {
				if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
				owner = ref.owner();
				name = ref.name();
				desc = ref.desc();
			}
			String overload = null;
			boolean named = false;
			boolean ambiguous = false;
			boolean staticCall = constructor;
			int calls = 0;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(owner) || !call.name.equals(name)) continue;
					String callDesc = constructor ? call.desc.substring(0, call.desc.indexOf(')') + 1) + "L" + owner + ";" : call.desc;
					if (callDesc.equals(desc)) {
						named = true;
					} else if (insertedArgument(desc, callDesc) >= 0 || pointOnly && !constructor && appendsArguments(desc, callDesc)) {
						ambiguous |= overload != null && !overload.equals(callDesc);
						overload = callDesc;
						staticCall |= call.getOpcode() == Opcodes.INVOKESTATIC;
						calls++;
					}
				}
			}
			// The call itself, when it is in none of the methods: in a hook of NeoForge's that took it over, a static method
			// taking its receiver first, handled as a static call of that.
			MethodInsnNode hook = named || overload != null || constructor ? null : hookCall(methods, owner, name, desc);
			// The call of a method NeoForge deprecated, made to its replacement instead (DEPRECATED-1).
			MethodInsnNode replacement = named || overload != null || constructor || hook != null ? null : replacementCall(methods, owner, name, desc);
			// An @Inject sees none of the call's arguments: any call of NeoForge's that leads to it will do (HOOK-3).
			MethodInsnNode leading = hook != null || replacement != null || named || overload != null || constructor || !injector.desc.equals(MixinNames.INJECT) ? null
					: callLeadingTo(methods, owner, name, desc);
			// A handler sharing the mixin's state with others moves only with them, which this doesn't do: jGui (ReplayMod)
			// keeps the screen's graphics from a @ModifyArg at the same call for its @Inject after it.
			if ((hook != null || leading != null) && !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			if (leading != null) {
				int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
				if (ordinal >= countCalls(methods, leading)) continue;
				for (int i = 0; i + 1 < at.values.size(); i += 2) {
					if ("target".equals(at.values.get(i))) at.values.set(i + 1, "L" + leading.owner + ";" + leading.name + leading.desc);
				}
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which this game build makes through " + loader + "'s "
						+ leading.owner.replace('/', '.') + "." + leading.name + leading.desc + "; the injection point is pointed at that call");
				continue;
			}
			String callOwner = owner;
			String callName = name;
			String original = desc;
			if (hook != null) {
				callOwner = hook.owner;
				callName = hook.name;
				desc = staticForm(owner, desc, isStaticCall(owner, name, desc));
				overload = hook.desc;
				staticCall = true;
				calls = countCalls(methods, hook);
			} else if (replacement != null) {
				callOwner = replacement.owner;
				callName = replacement.name;
				overload = replacement.desc;
				staticCall = replacement.getOpcode() == Opcodes.INVOKESTATIC;
				calls = countCalls(methods, replacement);
			}
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || overload == null || ambiguous || ordinal >= calls) continue;
			int inserted = insertedArgument(desc, overload);
			// A hook taking just the call's receiver and arguments takes the handlers as they are.
			if (!pointOnly && !overload.equals(desc) && !bridgeTo(mixin, handler, injector, owner, desc, overload, inserted, staticCall, bridges)) continue;
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, constructor ? overload : "L" + callOwner + ";" + callName + overload);
			}
			String via = pointOnly || overload.equals(desc) ? "" : ", through a bridge handler";
			if (hook != null) {
				moved.add(owner.replace('/', '.') + "." + name + original + ", which this game build makes inside " + loader + "'s hook "
						+ callOwner.replace('/', '.') + "." + callName + overload + "; the injection point is pointed at the hook's call" + via);
				continue;
			}
			if (replacement != null) {
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which " + loader + " deprecated: this game build calls its replacement "
						+ callOwner.replace('/', '.') + "." + callName + overload + " instead; the injection point is pointed there" + via);
				continue;
			}
			int extra = Type.getArgumentTypes(overload).length - Type.getArgumentTypes(desc).length;
			String more = extra == 1 ? "one more argument" : extra + " more arguments";
			moved.add(constructor
					? "new " + owner.replace('/', '.') + desc.substring(0, desc.indexOf(')') + 1) + ", which this game build makes with a constructor taking " + more + ", "
							+ overload + "; the injection point is pointed there" + via
					: owner.replace('/', '.') + "." + name + desc + ", which this game build makes through an overload with " + more + ", " + name + overload
							+ "; the injection point is pointed there" + via);
		}
		return moved;
	}

	/**
	 * The call, in {@code methods}, of the replacement ({@link OverloadRepairs#replacementOf}) of {@code owner.name(desc)}, a method
	 * NeoForge's build deprecated: the call that build makes instead, as {@code BoneMealItem.useOn} calls
	 * {@code applyBonemeal} where vanilla's calls {@code growCrop} (Wilder Wild's bone meal hook; DEPRECATED-1). Null
	 * otherwise.
	 */
	private MethodInsnNode replacementCall(List<MethodNode> methods, String owner, String name, String desc) {
		Code named = declaration(owner, name, desc);
		MethodNode replacement = named == null ? null : OverloadRepairs.replacementOf(named.method(), codeOf(named.owner()));
		if (replacement == null) return null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && (call.owner.equals(owner) || call.owner.equals(named.owner()))
						&& call.name.equals(replacement.name) && call.desc.equals(replacement.desc)) return call;
			}
		}
		return null;
	}

	/**
	 * The one call, in {@code methods}, of a static method of NeoForge's that takes the named call's receiver (unless
	 * the call is static) and arguments, with at most one more, returns what the call returns, and makes the call itself,
	 * or through a static method of its class: a hook of NeoForge's patches that took the call over to fire events around
	 * it, as {@code ClientHooks.drawScreen} for {@code Screen.renderWithTooltipAndSubtitles}. Null when there is none, or
	 * more than one such hook (several calls of the same one are fine).
	 */
	private MethodInsnNode hookCall(List<MethodNode> methods, String owner, String name, String desc) {
		String form = staticForm(owner, desc, isStaticCall(owner, name, desc));
		MethodInsnNode found = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || !call.owner.startsWith(MixinNames.NEOFORGE_PACKAGE)
						|| !call.desc.equals(form) && insertedArgument(form, call.desc) < 0) continue;
				if (found != null && found.owner.equals(call.owner) && found.name.equals(call.name) && found.desc.equals(call.desc)) continue;
				if (!makesCall(call.owner, call.name, call.desc, owner, name, desc, 2)) continue;
				if (found != null) return null;
				found = call;
			}
		}
		return found;
	}

	/** The descriptor of a call as a static method taking the receiver first, unless the call is static already. */
	private static String staticForm(String owner, String desc, boolean isStatic) {
		return isStatic ? desc : "(L" + owner + ";" + desc.substring(1);
	}

	private static int countCalls(List<MethodNode> methods, MethodInsnNode target) {
		int calls = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(target.owner) && call.name.equals(target.name) && call.desc.equals(target.desc)) calls++;
			}
		}
		return calls;
	}

	/**
	 * {@link #insertedArgument(String, String)}, or, where the types can't tell (one more float among floats), what the
	 * named method of {@code owner} tells when it only forwards to the overload: NeoForge keeps a method it gave a
	 * parameter, deprecated, passing its own parameters in order and a default for the new one
	 * ({@code Camera.setRotation(yRot, xRot)} calls {@code setRotation(yRot, xRot, 0F)}). -1 when neither tells.
	 */
	static int insertedArgument(ClassNode owner, String name, String desc, String overload) {
		int byTypes = insertedArgument(desc, overload);
		if (byTypes >= 0 || owner == null || Type.getArgumentTypes(overload).length != Type.getArgumentTypes(desc).length + 1) return byTypes;
		MethodNode old = PreflightAsm.declared(owner, name, desc);
		if (old == null || old.instructions == null) return -1;
		boolean isStatic = (old.access & Opcodes.ACC_STATIC) != 0;
		// What each value pushed before the call is: the old method's parameter it loads, or -1 for anything else.
		Map<Integer, Integer> parameterAt = new HashMap<>();
		int slot = isStatic ? 0 : 1;
		Type[] params = Type.getArgumentTypes(desc);
		for (int i = 0; i < params.length; i++) {
			parameterAt.put(slot, i);
			slot += params[i].getSize();
		}
		List<Integer> pushed = new ArrayList<>();
		for (AbstractInsnNode insn : old.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(owner.name) || !call.name.equals(name) || !call.desc.equals(overload)) return -1;
				if (!isStatic && (pushed.isEmpty() || pushed.remove(0) != MixinNames.THIS)) return -1;
				if (pushed.size() != params.length + 1) return -1;
				int inserted = -1;
				for (int k = 0, own = 0; k < pushed.size(); k++) {
					if (pushed.get(k) == own) {
						own++;
					} else if (inserted < 0 && pushed.get(k) == -1) {
						inserted = k;
					} else {
						return -1;
					}
				}
				return inserted;
			}
			if (insn instanceof VarInsnNode load && insn.getOpcode() >= Opcodes.ILOAD && insn.getOpcode() <= Opcodes.ALOAD) {
				pushed.add(!isStatic && load.var == 0 && insn.getOpcode() == Opcodes.ALOAD ? MixinNames.THIS : parameterAt.getOrDefault(load.var, -2));
			} else if (insn.getOpcode() >= Opcodes.ACONST_NULL && insn.getOpcode() <= Opcodes.LDC) {
				pushed.add(-1); // a constant
			} else {
				return -1; // more than forwarding
			}
		}
		return -1;
	}

	/**
	 * The index of the one parameter {@code overload} adds to {@code desc}'s, with the same result, or -1: when it adds
	 * none or several, or when which one is the added can't be told (next to another of its type).
	 */
	static int insertedArgument(String desc, String overload) {
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length != own.length + 1 || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return -1;
		int found = -1;
		for (int added = 0; added < all.length; added++) {
			boolean fits = true;
			for (int i = 0, j = 0; i < all.length && fits; i++) {
				if (i != added) fits = all[i].equals(own[j++]);
			}
			if (!fits) continue;
			if (found >= 0) return -1;
			found = added;
		}
		return found;
	}

	/**
	 * Whether {@code overload} takes {@code desc}'s parameters and more after them, with the same result: what NeoForge's
	 * patches call to pass more along, as its {@code CustomPacketPayload.codec} with the protocol and the direction
	 * (Polymer's codec hooks, PRE-13). Only a point that sees no argument (an {@code @Inject}'s, a
	 * {@code @ModifyExpressionValue}'s) moves to any such overload.
	 */
	static boolean appendsArguments(String desc, String overload) {
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length <= own.length || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return false;
		for (int i = 0; i < own.length; i++) {
			if (!all[i].equals(own[i])) return false;
		}
		return true;
	}
}
