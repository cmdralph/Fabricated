package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for overloads: a selector pinned to the overload its handler takes, an injector moved to the
 * overload NeoForge turned the method into, and a constant NeoForge split into another method.
 */
final class OverloadRepairs extends PreflightPart {
	private final CallRepairs calls;

	OverloadRepairs(PreflightClasses classes, CallRepairs calls) {
		super(classes);
		this.calls = calls;
	}

	/**
	 * Pins a descriptor-less selector to the one overload whose parameters the handler takes, of several of that name,
	 * which Mixin would refuse the handler for any other of: an {@code @Inject} or {@code @WrapMethod} taking them all, or
	 * a modifier taking its value and then the first of them ({@link #fitsHandler}). Mixin picks the first of that name.
	 * <p>
	 * With {@code declaredFirst} false, where the class declares another first. Remapped, a selector Intermediary named
	 * uniquely may name several methods: LambDynamicLights' {@code @ModifyReturnValue} on
	 * {@code LevelRenderer.getLightColor} taking the block state, whose overload without it comes first (CORPUS-45).
	 * <p>
	 * With {@code declaredFirst} true, where it is the first the class declares: a mixin replacing it ({@code @Overwrite})
	 * still moves it after the others, as Mixin removes the method it replaces and adds its own last, so the injectors
	 * applied after that one pick another. Indigo's {@code renderModel} on 1.21.11, which NeoForge gave an overload taking
	 * a buffer source and the block, left Sodium's Fabric build's {@code @Inject} on that overload, and Mixin stopped the
	 * game ("Invalid descriptor", OVERLOAD-2). The class as the game ships it can't show that, and a mod's translation
	 * doesn't depend on the other mods, so the pin is made wherever the method has overloads.
	 * <p>
	 * Returns the pinned methods.
	 */
	static List<String> overloadByParameters(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx, boolean declaredFirst) {
		if (Annotations.value(injector, "locals") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		Predicate<MethodNode> fits = fitsHandler(handler, injector);
		if (fits == null) return List.of();
		List<String> pinned = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null || ref.desc() != null) continue;
			MethodNode first = null;
			MethodNode fitting = null;
			int named = 0;
			int fitted = 0;
			for (MethodNode m : target.methods) {
				if (!m.name.equals(ref.name())) continue;
				named++;
				if (first == null) first = m;
				if (fits.test(m)) {
					fitting = m;
					fitted++;
				}
			}
			if (fitted != 1 || named < 2 || (fitting == first) != declaredFirst) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, fitting.name + fitting.desc);
			pinned.add(fitting.name + fitting.desc);
		}
		return pinned;
	}

	/**
	 * Which methods Mixin would take {@code handler} for, by the target's parameters it takes, or null when it takes none
	 * (any overload would do) or the injector isn't judged so: an {@code @Inject} or {@code @WrapMethod} all of them before
	 * its callback info or operation; a {@code @ModifyReturnValue}, {@code @ModifyExpressionValue},
	 * {@code @ModifyVariable} or {@code @ModifyConstant} its value and then the first of them, as many as it declares
	 * (Mixin's {@code Injector.validateParams}), a return value modifier only on a method returning that value's type.
	 */
	static Predicate<MethodNode> fitsHandler(MethodNode handler, AnnotationNode injector) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.size() < 2) return null;
		Type last = args.get(args.size() - 1);
		if (injector.desc.equals(MixinNames.INJECT) && PreflightAsm.isCallbackInfo(last)
				|| injector.desc.equals(MixinNames.WRAP_METHOD) && last.getDescriptor().equals(MixinNames.OPERATION)) {
			List<Type> wanted = args.subList(0, args.size() - 1);
			return m -> List.of(Type.getArgumentTypes(m.desc)).equals(wanted);
		}
		if (!MixinNames.VALUE_MODIFIERS.contains(injector.desc)) return null;
		Type value = args.get(0);
		List<Type> captured = args.subList(1, args.size());
		boolean returned = injector.desc.equals(MixinNames.MODIFY_RETURN_VALUE);
		return m -> {
			List<Type> params = List.of(Type.getArgumentTypes(m.desc));
			return captured.size() <= params.size() && params.subList(0, captured.size()).equals(captured)
					&& (!returned || Type.getReturnType(m.desc).equals(value));
		};
	}

	/**
	 * Points the selectors of an injector whose injection points are missing from the method Mixin picks, but all occur in
	 * the one overload of the same name that method calls (NeoForge's way of keeping vanilla's signature while moving the
	 * code), at that overload; returns a description of each move. An {@code @Inject} whose points are there but whose
	 * captured locals aren't (the method only calls the overload now) moves the same way. A handler that takes the
	 * method's parameters gets them from a bridge taking the overload's, when the method only passes its own parameters
	 * on with one value it works out ({@link #insertedParameter}, as {@code MappedRegistry.register} with an id), or, an
	 * {@code @Inject}'s, in order followed by defaults. Its {@code @Local}s must find their locals in the overload
	 * ({@link #localsInOverload}). Where the overload makes a call the injector names through an overload of that call
	 * taking one more argument, the points count as found there as {@link CallRepairs#pointsThroughOverloads} would point
	 * them, and the call repairs, which run later, point them so (OVERLOAD-1). An injection at the end of a constructor
	 * nothing calls any more ({@link ConstructorRepairs#keptConstructorDelegate}), or an {@code @Inject}'s at the returns
	 * of a method the platform deprecated for the one it calls ({@link #deprecatedDelegate}), moves to the end of that
	 * one. One at such a method's start, which may cancel the method to do its work another way, moves to the start of
	 * that one only where the platform's class calls it in place of each of vanilla's calls of the method
	 * ({@link #calledInItsPlace}): Fabric API's armour renderers in {@code HumanoidArmorLayer.renderArmorPiece}
	 * (ARMOR-1), not Indigo's block renderer hook, which would draw a Fabric model in each of the calls NeoForge 21.1
	 * makes per render type, where Fabricated draws it once itself (FRAPI-5).
	 */
	List<String> overloadRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty() || Annotations.value(injector, "locals") != null) return List.of();
		// A variable modifier moves only where it picks its local by type alone, among the locals, which the other method's
		// code decides as the method's did: Fabric API's before-bake model hook, whose ModelBakerImpl.bake Forge 47 split
		// off into an overload taking the sprite getter (MODEL-3). By its slot, its place, its name or among the
		// parameters, it could pick another value there; by its place at a load of it, vanilla's code decides
		// ({@link #variableByOrdinalRepairs}, PRE-19).
		if (injector.desc.equals(MixinNames.MODIFY_VARIABLE) && (Annotations.value(injector, "index") != null || Annotations.value(injector, "ordinal") != null
				|| Annotations.value(injector, "name") != null || Boolean.TRUE.equals(Annotations.value(injector, "argsOnly")))) return List.of();
		// An @Inject handler takes the callback info only, or (bridged below) the target's parameters before it.
		boolean takesParameters = false;
		if (injector.desc.equals(MixinNames.INJECT)) {
			List<Type> args = PreflightAsm.handlerArgs(handler);
			if (args == null || args.isEmpty() || !PreflightAsm.isCallbackInfo(args.get(args.size() - 1))) return List.of();
			takesParameters = args.size() > 1;
		}
		boolean inject = injector.desc.equals(MixinNames.INJECT);
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) continue;
			MethodNode chosen = null;
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) {
					chosen = m;
					break;
				}
			}
			if (chosen == null) continue;
			MethodNode overload;
			String how;
			String why = "";
			List<AnnotationNode> found;
			Verdict inChosen = InjectionPoints.pointsIn(ats, slices, chosen, ctx);
			boolean localsGone = inject && inChosen == Verdict.FOUND && MixinPreflight.missingLocal(handler, List.of(chosen)) != null;
			if (inChosen == Verdict.MISSING || localsGone) {
				overload = calledOverload(chosen, target);
				MethodNode replacement = overload == null ? replacementOf(chosen, target) : null;
				if (replacement != null) overload = replacement;
				boolean split = overload == null && inChosen == Verdict.MISSING;
				if (split) overload = splitOff(chosen, target, ats, slices, ctx);
				if (overload == null || (overload.access & Opcodes.ACC_STATIC) != (chosen.access & Opcodes.ACC_STATIC)
						|| !split && !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))) continue;
				// The points as they are there, or as the call repairs will point them there: NeoForge's overload may make a
				// call the injector names through an overload of that call taking one more argument too, as
				// ClientLanguage.appendFrom's, which passes Language.loadFromJson a components consumer (PolyPlus's hooks).
				// Those repairs then move the points, and bridge the handler; this one can't, as they would bridge it again.
				found = ats;
				if (InjectionPoints.pointsIn(ats, slices, overload, ctx) != Verdict.FOUND) {
					found = split || localsGone || !slices.isEmpty() ? null : calls.pointsThroughOverloads(mixin, handler, injector, overload, ctx);
					if (found == null || InjectionPoints.pointsIn(found, slices, overload, ctx) != Verdict.FOUND) continue;
				}
				// Its callback info must suit the method it moves to: a returnable one only one returning the same. A handler
				// sharing the mixin's state with others that may stay moves only with them, which this doesn't do (HOOK-2's).
				if (split && (inject && !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))
						&& PreflightAsm.last(PreflightAsm.handlerArgs(handler)).getDescriptor().equals(MixinNames.CALLBACK_INFO_RETURNABLE)
						|| !independentOf(mixin).contains(handler.name + handler.desc))) continue;
				// A handler taking the method's parameters takes the other's as they are, when they are the same.
				boolean sameParameters = Arrays.equals(Type.getArgumentTypes(chosen.desc), Type.getArgumentTypes(overload.desc));
				int captured = sameParameters ? -1 : inject ? (takesParameters ? 0 : -1) : capturedFrom(handler, chosen);
				int inserted = replacement != null ? CallRepairs.insertedArgument(chosen.desc, overload.desc) : insertedParameter(chosen, overload);
				// A handler taking the first of the method's parameters after its own (Mixin passes it as many as it takes) gets
				// the overload's first: they must be the method's, before the one the overload adds (Fabric API's before-bake
				// model hook takes the model's id so, which NeoForge 21.1's overload takes first too, MODEL-4).
				int prefix = inject || captured >= 0 || sameParameters ? 0 : capturedPrefix(handler, chosen);
				if (prefix > 0 && inserted < prefix) continue;
				// Or, where the method passes its parameters on in order and then defaults, an @Inject handler taking exactly
				// them gets the overload's first ones: NeoForge 21.1's HumanoidArmorLayer.renderArmorPiece passes six zeros
				// for the limb animation's values (Figura's armour hooks, PRE-20).
				boolean forwarded = inject && captured >= 0 && inserted < 0 && ConstructorRepairs.forwardsItsParameters(chosen, overload)
						&& ConstructorRepairs.takesExactly(handler, chosen);
				List<Runnable> locals = LocalRepairs.localsFit(handler, overload) ? List.of() : localsInOverload(handler, overload, inserted, found, vanillas(target, chosen), ctx);
				if (locals == null || captured >= 0 && (found != ats || inserted < 0 && !forwarded || selectors.size() != 1
						|| (mixin.access & Opcodes.ACC_INTERFACE) != 0)) continue;
				locals.forEach(Runnable::run);
				if (captured >= 0) {
					String name = HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated");
					bridges.add(forwarded ? HandlerBridges.bridge(mixin.name, handler, injector, chosen, overload, name)
							: HandlerBridges.insertedParameterBridge(mixin.name, handler, injector, captured, Type.getArgumentTypes(chosen.desc).length, overload,
									inserted, name));
				}
				how = localsGone ? ", which " + loader + " reduced to a call to " : replacement != null ? ", which " + loader + " deprecated for "
						: split ? ", the code of which there " + loader + " split off into " : ", whose code " + loader + " moved into ";
			} else {
				// The injection points exist, but in a constructor nothing calls any more, or in a method the platform deprecated
				// for the one it calls (an @Inject's, at all of its returns, or at its start where the platform's class calls
				// that one in its place): its end, or its start, is that of the one it calls.
				boolean constructor = chosen.name.equals("<init>");
				boolean atStart = inject && !constructor && atStart(ats);
				ClassNode vanilla = vanillaClass(target.name);
				overload = constructor ? ConstructorRepairs.keptConstructorDelegate(chosen, target, vanilla)
						: inject && (atStart || atReturns(ats)) ? deprecatedDelegate(chosen, target, vanilla) : null;
				if (overload == null || !slices.isEmpty() || !atStart && !ConstructorRepairs.atEndOnly(ats) || !LocalRepairs.localsFit(handler, overload)
						|| atStart && !calledInItsPlace(chosen, overload, target, vanilla)) continue;
				if (atStart) why = ", as its class calls it in place of each of vanilla's calls of the first";
				if (takesParameters) {
					// The handler gets the method's own parameters from a bridge taking all of the other one's.
					if (selectors.size() != 1 || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !ConstructorRepairs.forwardsItsParameters(chosen, overload)
							|| !ConstructorRepairs.takesExactly(handler, chosen)) continue;
					bridges.add(HandlerBridges.bridge(mixin.name, handler, injector, chosen, overload, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
				}
				how = constructor ? ", which " + loader + " keeps only to call " : ", which " + loader + " deprecated for ";
			}
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, overload.name + overload.desc);
			moved.add(chosen.name + chosen.desc + how + overload.name + overload.desc + "; the injector is pointed there" + why);
		}
		return moved;
	}

	/**
	 * Points an {@code @Inject} at a method its target class no longer declares, where vanilla's class overrode it, at the
	 * overload the platform's class overrides in its place (PRE-24). The platform added the overload to the superclass,
	 * whose method now only passes its parameters, with a value it works out or defaults ({@link #insertedParameter},
	 * {@link ConstructorRepairs#forwardsItsParameters}), on to it, a virtual call of the instance; the class moved vanilla's
	 * override into an override of the overload. So each call of the method on the class runs the class's overload once,
	 * where it ran vanilla's override, with the same parameters, and the overload's start and returns are the method's.
	 * Forge 47 has no {@code ServerPlayer.changeDimension(ServerLevel)}: {@code Entity}'s calls
	 * {@code changeDimension(level, level.getPortalForcer())}, which {@code ServerPlayer} overrides with vanilla's code and
	 * Forge's event, as {@code ItemEntity}, {@code AbstractVillager} and {@code ThrownEnderpearl} do theirs; Carpet's,
	 * Balm's, FrozenLib's, GlitchCore's and Polymer's dimension change hooks name the method. The platform's own calls of
	 * the overload, which Fabric makes through the method (a teleporter of Forge's, a target of Fabric API's), run the
	 * hooks too. Vanilla's classes (VANILLA-1) must show the move: vanilla's class declares the method, and neither it nor
	 * the superclass declaring the method there has the overload. A handler taking the method's parameters gets them from
	 * a bridge taking the overload's. One injector, without captured locals or a slice, in a class mixin, whose points
	 * are all in the overload. Returns a description of the move, or nothing.
	 */
	List<String> inheritedOverloadRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(MixinNames.INJECT) || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || (target.access & Opcodes.ACC_INTERFACE) != 0
				|| Annotations.value(injector, "locals") != null || !Annotations.list(Annotations.value(injector, "slice")).isEmpty()
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw) || target.superName == null) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		Type[] args = Type.getArgumentTypes(handler.desc);
		if (ats.isEmpty() || args.length == 0 || !PreflightAsm.isCallbackInfo(args[args.length - 1])) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null || ref.name().startsWith("<") || PreflightAsm.addsMethod(mixin, ref)) return List.of();
		for (MethodNode m : target.methods) {
			if (ref.matchesMethod(m.name, m.desc)) return List.of();
		}
		// Vanilla's override, as Mixin would have picked it there.
		ClassNode vanillaTarget = vanillaClass(target.name);
		MethodNode overridden = null;
		for (MethodNode m : vanillaTarget == null ? List.<MethodNode>of() : vanillaTarget.methods) {
			if (ref.matchesMethod(m.name, m.desc)) {
				overridden = m;
				break;
			}
		}
		if (overridden == null || (overridden.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0) return List.of();
		// What the class inherits in its place, a class's method passing everything on to the overload, a virtual call.
		PreflightClasses.Code inherited = declaration(target.superName, overridden.name, overridden.desc);
		ClassNode owner = inherited == null ? null : codeOf(inherited.owner());
		MethodNode method = inherited == null ? null : inherited.method();
		if (owner == null || (owner.access & Opcodes.ACC_INTERFACE) != 0 || (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0) return List.of();
		MethodInsnNode call = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode m) call = m;
		}
		if (call == null || call.getOpcode() != Opcodes.INVOKEVIRTUAL || call.name.startsWith("<") || !isSubtype(target.name, call.owner)) return List.of();
		MethodNode overload = PreflightAsm.declared(target, call.name, call.desc);
		if (overload == null || (overload.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_ABSTRACT)) != 0
				|| !Type.getReturnType(overload.desc).equals(Type.getReturnType(overridden.desc))) return List.of();
		int inserted = insertedParameter(method, overload);
		boolean forwarded = inserted < 0 && ConstructorRepairs.forwardsItsParameters(method, overload);
		if (inserted < 0 && !forwarded) return List.of();
		ClassNode vanillaOwner = vanillaClass(inherited.owner());
		if (vanillaOwner == null || PreflightAsm.declared(vanillaOwner, overridden.name, overridden.desc) == null
				|| PreflightAsm.declared(vanillaOwner, overload.name, overload.desc) != null
				|| PreflightAsm.declared(vanillaTarget, overload.name, overload.desc) != null) return List.of();
		if (InjectionPoints.pointsIn(ats, List.of(), overload, ctx) != Verdict.FOUND) return List.of();
		if (args.length > 1) {
			if (!ConstructorRepairs.takesExactly(handler, method)) return List.of();
			String name = HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated");
			bridges.add(forwarded ? HandlerBridges.bridge(mixin.name, handler, injector, method, overload, name)
					: HandlerBridges.insertedParameterBridge(mixin.name, handler, injector, 0, Type.getArgumentTypes(method.desc).length, overload, inserted, name));
		}
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, overload.name + overload.desc);
		return List.of(overridden.name + overridden.desc + ", which " + loader + "'s " + target.name.replace('/', '.') + " no longer overrides: the "
				+ inherited.owner().replace('/', '.') + " method it inherits only passes its parameters on to " + overload.name + overload.desc
				+ ", which it overrides with vanilla's code; the injector is pointed there");
	}

	/**
	 * Points an {@code @Inject} that captures locals ({@code locals}) in a method NeoForge reduced to a call of an
	 * overload taking one more parameter ({@link #insertedParameter}) at that overload, through a bridge taking the
	 * overload's parameters, the callback info and its locals there as Mixin passes them
	 * ({@link HandlerBridges#capturedLocalsBridge}). The method has no locals left past its parameters, so Mixin can't
	 * capture any there and throws ("Invalid descriptor") whatever the capture mode. Each local the handler captures must
	 * be the overload's one local of its type in scope at each instruction the point picks, among locals whose slots
	 * follow one another from the first past the parameters (what Mixin sees in a slot no local is named for is unknown).
	 * FrozenLib's entry added event in {@code MappedRegistry.register} (CORPUS-51), whose NeoForge 21.1 overload takes the
	 * id and keeps it in a local before the reference, where vanilla's has the reference first; Continuity's emissive
	 * sprites in {@code SpriteSourceList.list} (NeoForge 1.21.11 and 26.x). The capture mode stays as it is (the soften
	 * step made it skip itself where the locals differ). Returns a description of the move, or nothing.
	 */
	List<String> capturedLocalsRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "locals") == null || (mixin.access & Opcodes.ACC_INTERFACE) != 0
				|| Annotations.value(injector, "slice") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| selectors.size() != 1 || !(selectors.get(0) instanceof String raw) || hasParameterAnnotations(handler)) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null) return List.of();
		MethodNode chosen = null;
		for (MethodNode m : target.methods) {
			if (ref.matchesMethod(m.name, m.desc)) {
				chosen = m;
				break;
			}
		}
		if (chosen == null || chosen.maxLocals > firstLocalSlot(chosen)) return List.of();
		// The handler takes the method's parameters, the callback info and the locals it captures.
		Type[] own = Type.getArgumentTypes(chosen.desc);
		Type[] args = Type.getArgumentTypes(handler.desc);
		if (args.length <= own.length + 1 || !Arrays.equals(Arrays.copyOf(args, own.length), own) || !PreflightAsm.isCallbackInfo(args[own.length])
				|| Type.getReturnType(handler.desc) != Type.VOID_TYPE) return List.of();
		MethodNode overload = calledOverload(chosen, target);
		if (overload == null) return List.of();
		int inserted = insertedParameter(chosen, overload);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (inserted < 0 || InjectionPoints.pointsIn(ats, List.of(), overload, ctx) != Verdict.FOUND) return List.of();
		List<Integer> positions = pointPositions(ats, overload, ctx);
		if (positions == null) return List.of();
		List<Type> locals = null;
		for (int position : positions) {
			List<Type> there = localsAt(overload, position);
			if (there == null || locals != null && !locals.equals(there)) return List.of();
			locals = there;
		}
		List<Type> captured = List.of(args).subList(own.length + 1, args.length);
		int[] picked = new int[captured.size()];
		int last = -1;
		for (int i = 0; i < captured.size(); i++) {
			Type type = captured.get(i);
			if (locals.stream().filter(type::equals).count() != 1 || captured.stream().filter(type::equals).count() != 1) return List.of();
			picked[i] = locals.indexOf(type);
			last = Math.max(last, picked[i]);
		}
		bridges.add(HandlerBridges.capturedLocalsBridge(mixin.name, handler, injector, overload, inserted, locals.subList(0, last + 1), picked,
				HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, overload.name + overload.desc);
		return List.of(chosen.name + chosen.desc + ", which " + loader + " reduced to a call to " + overload.name + overload.desc
				+ "; the injector is pointed there, taking its locals as they are there");
	}

	/**
	 * Points a {@code @ModifyVariable} that picks its local by its place among the locals of its type ({@code ordinal})
	 * at a load of it ({@code @At("LOAD")}), in a method the platform reduced to a call of a method taking one more
	 * parameter, at that method, where vanilla's version of the method (VANILLA-1) shows the move keeps what the mod
	 * hooks: Puzzles Lib's break speed hook modifies the speed, the first float, at its second load after the
	 * {@code onGround()} call in {@code Player.getDestroySpeed}, which Forge 47 deprecated for {@code getDigSpeed(state,
	 * pos)} and keeps only to call it (PRE-19). The other method's code could have locals of that type first, or more
	 * loads of the local (LOCAL-4), so vanilla's code aligned with it ({@link PatchAlignment}) must show:
	 * <ul>
	 * <li>the parameter it adds isn't of that type, and at the load the hook applies at, the local the ordinal picks among
	 * those its local variable table has in scope there (Mixin's way, parameters first, by slot) is the one it picks among
	 * vanilla's ({@link LocalRepairs#isVanillasLocal}): the patch added none of the type before it;</li>
	 * <li>each of vanilla's loads of that local the point could pick is still there, a load of the local the ordinal picks,
	 * and the slice's ends are vanilla's;</li>
	 * <li>with the point's ordinal, the load it picks in vanilla's code; the ordinal changes where the patch loads the
	 * local before it (Forge 47 passes the speed to its break speed event before returning it). Without one, the patch
	 * may add no load of it, which the hook would modify too.</li>
	 * </ul>
	 * The handler takes the parameters before the added one alone, if any, and no MixinExtras sugar. Without vanilla's
	 * code or a local variable table there, nothing moves. Returns a description of the move, or nothing.
	 */
	List<String> variableByOrdinalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.MODIFY_VARIABLE) || !(Annotations.value(injector, "ordinal") instanceof Integer ordinal) || ordinal < 0
				|| Annotations.value(injector, "index") != null || Annotations.value(injector, "name") != null
				|| Boolean.TRUE.equals(Annotations.value(injector, "argsOnly")) || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| selectors.size() != 1 || !(selectors.get(0) instanceof String raw) || hasParameterAnnotations(handler)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!point.equals("LOAD") || Annotations.value(at, "opcode") != null || Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null) {
			return List.of();
		}
		Type value = Type.getReturnType(handler.desc);
		Type[] args = Type.getArgumentTypes(handler.desc);
		if (value.getSort() == Type.VOID || args.length == 0 || !args[0].equals(value)) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null) return List.of();
		MethodNode chosen = null;
		for (MethodNode m : target.methods) {
			if (ref.matchesMethod(m.name, m.desc)) {
				chosen = m;
				break;
			}
		}
		if (chosen == null) return List.of();
		AnnotationNode slice = InjectionPoints.sliceFor(at, Annotations.list(Annotations.value(injector, "slice")));
		int pointOrdinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		InjectionPoints.Range inChosen = slice == null ? InjectionPoints.Range.ALL : InjectionPoints.sliceRange(slice, chosen, ctx);
		// The method must have lost the point: no load of the type there (PRE-17).
		if (inChosen == InjectionPoints.Range.UNEVALUATED || InjectionPoints.accessesAnyLocal(true, value, -1, pointOrdinal, inChosen, chosen)) return List.of();
		MethodNode overload = calledOverload(chosen, target);
		int inserted = overload == null ? -1 : insertedParameter(chosen, overload);
		boolean replaced = false;
		if (overload == null) {
			overload = replacementOf(chosen, target);
			inserted = overload == null ? -1 : CallRepairs.insertedArgument(chosen.desc, overload.desc);
			replaced = true;
		}
		if (overload == null || inserted < 0 || (overload.access & Opcodes.ACC_STATIC) != (chosen.access & Opcodes.ACC_STATIC)
				|| Type.getArgumentTypes(overload.desc)[inserted].equals(value) || overload.localVariables == null || overload.localVariables.isEmpty()
				|| capturedPrefix(handler, chosen) > inserted) return List.of();
		ClassNode original = vanillaClass(target.name);
		MethodNode before = original == null ? null : PreflightAsm.declared(original, chosen.name, chosen.desc);
		PatchAlignment alignment = before == null ? null : PatchAlignment.of(before, overload);
		if (alignment == null) return List.of();
		InjectionPoints.Range inVanilla = slice == null ? InjectionPoints.Range.ALL : InjectionPoints.sliceRange(slice, before, ctx);
		InjectionPoints.Range inOverload = slice == null ? InjectionPoints.Range.ALL : InjectionPoints.sliceRange(slice, overload, ctx);
		if (inVanilla == InjectionPoints.Range.UNEVALUATED || inOverload == InjectionPoints.Range.UNEVALUATED) return List.of();
		if (slice != null && (Annotations.value(slice, "from") != null && alignment.placeOf(inVanilla.start()) != inOverload.start()
				|| Annotations.value(slice, "to") != null && alignment.placeOf(inVanilla.end()) != inOverload.end())) return List.of();

		// Where Mixin applies the hook in the other method: the loads of the local the ordinal picks at each.
		int opcode = value.getOpcode(Opcodes.ILOAD);
		List<Integer> matched = new ArrayList<>();
		for (int i = Math.max(inOverload.start(), 0); i <= Math.min(inOverload.end(), overload.instructions.size() - 1); i++) {
			if (!(overload.instructions.get(i) instanceof VarInsnNode load) || load.getOpcode() != opcode) continue;
			int slot = nthLocalOfType(overload, value, ordinal, i, null);
			if (slot == AMBIGUOUS) return List.of();
			if (slot == load.var) matched.add(i);
		}
		// Vanilla's local: the slot of vanilla's loads aligned with those.
		int[] vanillaAt = new int[overload.instructions.size()];
		Arrays.fill(vanillaAt, -1);
		Set<Integer> vanillaPlaces = new HashSet<>();
		for (int i = 0; i < before.instructions.size(); i++) {
			int place = alignment.placeOf(i);
			if (place < 0) continue;
			vanillaAt[place] = i;
			vanillaPlaces.add(place);
		}
		int vanillaSlot = -1;
		for (int i : matched) {
			if (vanillaAt[i] < 0) continue;
			int slot = ((VarInsnNode) before.instructions.get(vanillaAt[i])).var;
			if (vanillaSlot >= 0 && vanillaSlot != slot) return List.of();
			vanillaSlot = slot;
		}
		if (vanillaSlot < 0) return List.of();
		// Each of vanilla's loads of it the point could pick is still one Mixin picks.
		List<Integer> vanillas = new ArrayList<>();
		for (int i = Math.max(inVanilla.start(), 0); i <= Math.min(inVanilla.end(), before.instructions.size() - 1); i++) {
			if (!(before.instructions.get(i) instanceof VarInsnNode load) || load.getOpcode() != opcode || load.var != vanillaSlot) continue;
			int place = alignment.placeOf(i);
			if (place < 0 || !matched.contains(place)) return List.of();
			vanillas.add(place);
		}
		List<Integer> applied;
		int newOrdinal = pointOrdinal;
		if (pointOrdinal >= 0) {
			if (pointOrdinal >= vanillas.size()) return List.of();
			applied = List.of(vanillas.get(pointOrdinal));
			newOrdinal = matched.indexOf(applied.get(0));
		} else {
			if (matched.size() != vanillas.size()) return List.of();
			applied = matched;
		}
		for (int i : applied) {
			int slot = nthLocalOfType(overload, value, ordinal, i, null);
			if (slot < 0 || slot != nthLocalOfType(overload, value, ordinal, i, vanillaPlaces)) return List.of();
		}
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, overload.name + overload.desc);
		String repointed = "";
		if (newOrdinal != pointOrdinal) {
			Annotations.put(at, "ordinal", newOrdinal);
			repointed = ", at its load with ordinal " + newOrdinal + " there, vanilla's with ordinal " + pointOrdinal + " (" + loader
					+ "'s code loads it before)";
		}
		return List.of(chosen.name + chosen.desc + ", which " + loader + (replaced ? " deprecated for " : " reduced to a call to ") + overload.name
				+ overload.desc + "; the injector is pointed there" + repointed + ", the local of type " + value.getClassName() + " with ordinal " + ordinal
				+ " being vanilla's there");
	}

	/** {@link #nthLocalOfType}'s answer where two locals share a slot at the instruction. */
	private static final int AMBIGUOUS = -2;

	/**
	 * The slot of the local with ordinal {@code ordinal} among the locals of {@code type} that {@code method}'s local
	 * variable table has in scope at the instruction at {@code position}, counted as Mixin counts them for a variable
	 * modifier (all of them by slot, {@code this} and the parameters first, a match only past {@code this}); with
	 * {@code vanillaPlaces}, among vanilla's locals alone ({@link LocalRepairs#isVanillasLocal}). -1 for none,
	 * {@link #AMBIGUOUS} where two locals share a slot there.
	 */
	private static int nthLocalOfType(MethodNode method, Type type, int ordinal, int position, Set<Integer> vanillaPlaces) {
		java.util.TreeMap<Integer, LocalVariableNode> bySlot = new java.util.TreeMap<>();
		for (LocalVariableNode lv : method.localVariables) {
			if (method.instructions.indexOf(lv.start) > position || position >= method.instructions.indexOf(lv.end)) continue;
			if (bySlot.put(lv.index, lv) != null) return AMBIGUOUS;
		}
		int base = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		int seen = 0;
		for (LocalVariableNode lv : bySlot.values()) {
			if (!lv.desc.equals(type.getDescriptor()) || vanillaPlaces != null && !LocalRepairs.isVanillasLocal(method, lv, vanillaPlaces)) continue;
			if (seen++ == ordinal) return lv.index >= base ? lv.index : -1;
		}
		return -1;
	}

	/**
	 * Turns an {@code @Inject} at the returns of a private static method that NeoForge deprecated and no longer calls into
	 * a wrapper ({@code @WrapOperation}) of the call NeoForge makes in its place, where vanilla's class calls it: the
	 * handler gets the call's result in a returnable callback info, as at the method's return, and the call returns what
	 * it leaves there. NeoForge 21.1 builds {@code Ingredient}'s codecs with {@code CraftingHelper.makeIngredientCodec}
	 * where vanilla's static initializer calls {@code Ingredient.codec(boolean)}, which it keeps, deprecated, and never
	 * calls ("We take over the codec creation entirely"), so Fabric API's recipe API, whose hook at that method's return
	 * adds Fabric's custom ingredients ({@code fabric:type}) to the codec, never ran, and no recipe using one loaded
	 * (Farmer's Delight's and Tech Reborn's on the 1.21.1 corpus server; DEPRECATED-2).
	 * <p>
	 * Only where nothing else can call the method (private, and neither its class nor that class's nest members call it
	 * any more), the method returns a reference, and each of vanilla's callers makes, in NeoForge's build, as many calls
	 * of one other static method with the method's descriptor as it made of the method, and no other new call of that
	 * descriptor. The callers are static, as the handler is. Returns a description of the change, or nothing.
	 */
	List<String> abandonedMethodWrappers(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(MixinNames.INJECT) || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || (handler.access & Opcodes.ACC_STATIC) == 0
				|| Annotations.value(injector, "locals") != null || Annotations.value(injector, "slice") != null || Annotations.value(injector, "target") != null
				|| Annotations.value(injector, "constraints") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| selectors.size() != 1 || !(selectors.get(0) instanceof String raw) || hasParameterAnnotations(handler)
				|| Type.getReturnType(handler.desc) != Type.VOID_TYPE) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty()) return List.of();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("RETURN") || at.values == null || at.values.size() != 2) return List.of(); // every return, nothing else
		}
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null) return List.of();
		MethodNode method = null;
		for (MethodNode m : target.methods) {
			if (ref.matchesMethod(m.name, m.desc)) {
				method = m;
				break;
			}
		}
		if (method == null || (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) return List.of();
		Type result = Type.getReturnType(method.desc);
		if (result.getSort() != Type.OBJECT && result.getSort() != Type.ARRAY) return List.of();
		// The handler takes the callback info, or the method's parameters and it.
		Type[] args = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(method.desc);
		if (args.length == 0 || !args[args.length - 1].getDescriptor().equals(MixinNames.CALLBACK_INFO_RETURNABLE)
				|| args.length != 1 && !Arrays.equals(Arrays.copyOf(args, args.length - 1), params)) return List.of();
		ClassNode vanilla = vanillaClass(target.name);
		MethodNode vanillaMethod = vanilla == null ? null : PreflightAsm.declared(vanilla, method.name, method.desc);
		if (!PreflightAsm.deprecated(method) || vanillaMethod == null || PreflightAsm.deprecated(vanillaMethod) || target.nestHostClass != null) {
			return List.of();
		}
		List<ClassNode> nest = new ArrayList<>(List.of(target));
		if (target.nestMembers != null) {
			for (String member : target.nestMembers) {
				ClassNode node = gameTarget(member);
				if (node == null) return List.of(); // a nest member's code unknown: it may call the method
				nest.add(node);
			}
		}
		for (ClassNode node : nest) {
			if (calls(node, target.name, method.name, method.desc) > 0) return List.of();
		}
		// Each of vanilla's callers, and what NeoForge's build calls there instead.
		List<String> callers = new ArrayList<>();
		String replacement = null;
		for (MethodNode caller : vanilla.methods) {
			int made = calls(caller, vanilla.name, method.name, method.desc);
			if (made == 0) continue;
			MethodNode patched = PreflightAsm.declared(target, caller.name, caller.desc);
			if (patched == null || (patched.access & Opcodes.ACC_STATIC) == 0) return List.of();
			java.util.Map<String, Integer> before = callsOfDescriptor(caller, method.desc);
			java.util.Map<String, Integer> after = callsOfDescriptor(patched, method.desc);
			before.merge(vanilla.name + "." + method.name, -made, Integer::sum);
			String added = null;
			for (var e : after.entrySet()) {
				int extra = e.getValue() - before.getOrDefault(e.getKey(), 0);
				if (extra < 0) return List.of(); // a call of that descriptor vanilla makes there is gone too
				if (extra == 0) continue;
				if (added != null || extra != made) return List.of();
				added = e.getKey();
			}
			for (var e : before.entrySet()) {
				if (e.getValue() > after.getOrDefault(e.getKey(), 0)) return List.of();
			}
			if (added == null || replacement != null && !replacement.equals(added)) return List.of();
			replacement = added;
			callers.add(patched.name + patched.desc);
		}
		if (replacement == null) return List.of();
		int dot = replacement.lastIndexOf('.');
		String owner = replacement.substring(0, dot);
		String name = replacement.substring(dot + 1);
		if (owner.equals(target.name) && name.equals(method.name)) return List.of();
		// The wrapper: (the call's arguments, the operation) -> its result, as the handler leaves it.
		List<Type> wrapperParams = new ArrayList<>(List.of(params));
		wrapperParams.add(Type.getType(MixinNames.OPERATION));
		MethodNode bridge = HandlerBridges.bridgeMethod(true, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated"), wrapperParams, result);
		InsnList code = bridge.instructions;
		int operation = HandlerBridges.slotOf(true, wrapperParams, params.length);
		int resultSlot = HandlerBridges.slotOf(true, wrapperParams, wrapperParams.size());
		code.add(new VarInsnNode(Opcodes.ALOAD, operation));
		code.add(new LdcInsnNode(params.length));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
		for (int i = 0; i < params.length; i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new LdcInsnNode(i));
			code.add(new VarInsnNode(params[i].getOpcode(Opcodes.ILOAD), HandlerBridges.slotOf(true, wrapperParams, i)));
			HandlerBridges.box(code, params[i]);
			code.add(new InsnNode(Opcodes.AASTORE));
		}
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, Type.getType(MixinNames.OPERATION).getInternalName(), "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
		// new CallbackInfoReturnable(name, cancellable, result), kept in a local
		String cir = Type.getType(MixinNames.CALLBACK_INFO_RETURNABLE).getInternalName();
		code.add(new VarInsnNode(Opcodes.ASTORE, resultSlot));
		code.add(new TypeInsnNode(Opcodes.NEW, cir));
		code.add(new InsnNode(Opcodes.DUP));
		code.add(new LdcInsnNode(method.name));
		code.add(new InsnNode(Boolean.TRUE.equals(Annotations.value(injector, "cancellable")) ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
		code.add(new VarInsnNode(Opcodes.ALOAD, resultSlot));
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, cir, "<init>", "(Ljava/lang/String;ZLjava/lang/Object;)V", false));
		code.add(new VarInsnNode(Opcodes.ASTORE, resultSlot + 1));
		int stack = 0;
		if (args.length > 1) {
			for (int i = 0; i < params.length; i++) {
				code.add(new VarInsnNode(params[i].getOpcode(Opcodes.ILOAD), HandlerBridges.slotOf(true, wrapperParams, i)));
				stack += params[i].getSize();
			}
		}
		code.add(new VarInsnNode(Opcodes.ALOAD, resultSlot + 1));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, mixin.name, handler.name, handler.desc, false));
		code.add(new VarInsnNode(Opcodes.ALOAD, resultSlot + 1));
		code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, cir, "getReturnValue", "()Ljava/lang/Object;", false));
		code.add(new TypeInsnNode(Opcodes.CHECKCAST, result.getSort() == Type.ARRAY ? result.getDescriptor() : result.getInternalName()));
		code.add(new InsnNode(Opcodes.ARETURN));
		// Filling the arguments' array (the operation, the array twice, an index and a value of up to two slots), making
		// the callback info (two references, a name, a flag, the result) or passing the handler its arguments.
		bridge.maxStack = Math.max(6, Math.max(5, stack + 1));
		bridge.maxLocals = resultSlot + 2;
		AnnotationNode wrapper = new AnnotationNode(MixinNames.WRAP_OPERATION);
		Annotations.put(wrapper, "method", new ArrayList<>(callers));
		AnnotationNode wrappedAt = new AnnotationNode(MixinNames.AT);
		Annotations.put(wrappedAt, "value", "INVOKE");
		Annotations.put(wrappedAt, "target", "L" + owner + ";" + name + method.desc);
		Annotations.put(wrapper, "at", new ArrayList<>(List.of(wrappedAt)));
		if (Annotations.value(injector, "remap") != null) Annotations.put(wrapper, "remap", Annotations.value(injector, "remap"));
		HandlerBridges.replaceInjector(handler, injector, wrapper, bridge);
		bridges.add(bridge);
		return List.of(method.name + method.desc + ", which " + loader + " deprecated and no longer calls: where vanilla's class calls it ("
				+ String.join(", ", callers) + "), " + loader + "'s calls " + owner.replace('/', '.') + "." + name + method.desc
				+ "; the injector wraps that call, handing the handler its result as at the method's return");
	}

	/** How many calls of {@code owner.name desc} {@code node}'s methods make. */
	private static int calls(ClassNode node, String owner, String name, String desc) {
		int count = 0;
		for (MethodNode m : node.methods) count += calls(m, owner, name, desc);
		return count;
	}

	/** How many calls of {@code owner.name desc} {@code method} makes. */
	private static int calls(MethodNode method, String owner, String name, String desc) {
		int count = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) count++;
		}
		return count;
	}

	/** The static calls of methods with descriptor {@code desc} {@code method} makes, counted by {@code owner.name}. */
	private static java.util.Map<String, Integer> callsOfDescriptor(MethodNode method, String desc) {
		java.util.Map<String, Integer> found = new java.util.TreeMap<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && call.desc.equals(desc)) {
				found.merge(call.owner + "." + call.name, 1, Integer::sum);
			}
		}
		return found;
	}

	/** Whether any parameter of {@code handler} carries an annotation (MixinExtras sugar, {@code @Coerce}). */
	static boolean hasParameterAnnotations(MethodNode handler) {
		for (int i = 0; i < Type.getArgumentTypes(handler.desc).length; i++) {
			if (!Annotations.ofParameter(handler, i).isEmpty()) return true;
		}
		return false;
	}

	/** The first local variable slot past {@code method}'s parameters (and {@code this}). */
	static int firstLocalSlot(MethodNode method) {
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type t : Type.getArgumentTypes(method.desc)) slot += t.getSize();
		return slot;
	}

	/**
	 * The types of {@code method}'s locals past its parameters in scope at the instruction at {@code position}, by slot,
	 * as a capturing handler takes them; null without a local variable table, or where two locals share a slot there or
	 * a slot before the last is no named local's.
	 */
	private static List<Type> localsAt(MethodNode method, int position) {
		if (method.localVariables == null || method.localVariables.isEmpty()) return null;
		int first = firstLocalSlot(method);
		java.util.TreeMap<Integer, LocalVariableNode> bySlot = new java.util.TreeMap<>();
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.index < first || method.instructions.indexOf(lv.start) > position || position >= method.instructions.indexOf(lv.end)) continue;
			if (bySlot.put(lv.index, lv) != null) return null;
		}
		List<Type> types = new ArrayList<>();
		int next = first;
		for (var e : bySlot.entrySet()) {
			if (e.getKey() != next) return null;
			Type type = Type.getType(e.getValue().desc);
			types.add(type);
			next += type.getSize();
		}
		return types;
	}

	/**
	 * Points a {@code @ModifyConstant} whose constants are in none of the methods it targets at the code NeoForge's
	 * patches split off from one ({@link #splitOff}), when they all are there: Carpet's update flag in
	 * {@code Level.setBlock}, now in {@code markAndNotifyBlock} (SPLIT-2). Only a handler taking the constant alone moves
	 * (others take the method's parameters too), and one sharing none of its mixin's state with its other injectors.
	 * Returns a description of each move.
	 */
	List<String> constantSplitRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.MODIFY_CONSTANT) || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| Annotations.value(injector, "slice") != null || PreflightAsm.handlerArgs(handler) == null || PreflightAsm.handlerArgs(handler).size() != 1) return List.of();
		List<AnnotationNode> constants = Annotations.list(Annotations.value(injector, "constant"));
		if (constants.isEmpty() || constants.stream().anyMatch(c -> Annotations.value(c, "ordinal") != null || Annotations.value(c, "slice") != null
				|| InjectionPoints.constantDescription(c) == null)) return List.of();
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) continue;
			MethodNode chosen = null;
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) {
					chosen = m;
					break;
				}
			}
			MethodNode from = chosen;
			if (from == null || constants.stream().anyMatch(c -> InjectionPoints.constantVerdict(c, from) != Verdict.MISSING)) continue;
			MethodNode split = splitOff(from, target, m -> constants.stream().allMatch(c -> InjectionPoints.constantVerdict(c, m) == Verdict.FOUND));
			if (split == null || (split.access & Opcodes.ACC_STATIC) != (from.access & Opcodes.ACC_STATIC)
					|| !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, split.name + split.desc);
			moved.add(from.name + from.desc + ", the code of which there " + loader + " split off into " + split.name + split.desc
					+ "; the injector is pointed there");
		}
		return moved;
	}

	/**
	 * The one method of the class {@code method} calls in which the injection points are, when none of them is anywhere
	 * in {@code method} (no ordinal, no slice) and nothing else in the class calls it: code NeoForge's patches split off
	 * into a method of their own, as {@code Level.setBlock}'s neighbour updates into {@code markAndNotifyBlock}
	 * (SPLIT-1). Null when none or several are.
	 */
	private static MethodNode splitOff(MethodNode method, ClassNode target, List<AnnotationNode> ats, List<AnnotationNode> slices, Context ctx) {
		if (!slices.isEmpty() || ats.stream().anyMatch(at -> Annotations.value(at, "ordinal") != null)) return null;
		return splitOff(method, target, callee -> InjectionPoints.pointsIn(ats, slices, callee, ctx) == Verdict.FOUND);
	}

	/**
	 * The one method of the class {@code method} calls that {@code holds} the injection points, when nothing else in the
	 * class calls it but methods that only pass their parameters on to it: NeoForge's way of keeping an entry point that
	 * skips its event, as {@code PersistentEntitySectionManager.addNewEntityWithoutEvent} next to {@code addEntity}, whose
	 * code it split off into {@code addEntityWithoutEvent} (SPLIT-2). Such a method is how this game build gets to the code
	 * a vanilla method of the class did. Null when none or several are.
	 */
	private static MethodNode splitOff(MethodNode method, ClassNode target, Predicate<MethodNode> holds) {
		MethodNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name)) continue;
			MethodNode callee = PreflightAsm.declared(target, call.name, call.desc);
			if (callee == null || callee == method || callee == found || callee.name.startsWith("<") || !holds.test(callee)) continue;
			if (found != null) return null;
			found = callee;
		}
		if (found == null) return null;
		int calls = 0;
		for (MethodNode m : target.methods) {
			if (m != method && onlyCalls(m, target, found)) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(target.name) && call.name.equals(found.name) && call.desc.equals(found.desc)) calls++;
			}
		}
		return calls == 1 ? found : null;
	}

	/** Whether {@code method} does nothing but call {@code callee} of its class, with its parameters and constants, and return. */
	private static boolean onlyCalls(MethodNode method, ClassNode target, MethodNode callee) {
		boolean called = false;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (called) return insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN;
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(target.name) || !call.name.equals(callee.name) || !call.desc.equals(callee.desc)) return false;
				called = true;
			} else if (!(insn instanceof VarInsnNode load && load.getOpcode() >= Opcodes.ILOAD && load.getOpcode() <= Opcodes.ALOAD)
					&& !(insn.getOpcode() >= Opcodes.ACONST_NULL && insn.getOpcode() <= Opcodes.LDC)) {
				return false;
			}
		}
		return false;
	}

	/**
	 * The method NeoForge's build replaced {@code method} with: the one method of the class it calls taking its parameters
	 * and one more, when NeoForge marks {@code method} deprecated, which vanilla's methods never are (DEPRECATED-1).
	 * {@code BoneMealItem.growCrop}'s code is in {@code applyBonemeal} there, which also takes the player. Where it calls
	 * none, the one other method of its name taking them and one more: Forge 52's {@code ParticleEngine.render} calls
	 * itself where Forge meant its overload taking a frustum, which the game calls instead (IRIS-2). Null otherwise.
	 */
	static MethodNode replacementOf(MethodNode method, ClassNode target) {
		if (!PreflightAsm.deprecated(method)) return null;
		MethodNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || CallRepairs.insertedArgument(method.desc, call.desc) < 0) continue;
			MethodNode callee = PreflightAsm.declared(target, call.name, call.desc);
			if (callee == null || callee == found || (callee.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) continue;
			if (found != null) return null;
			found = callee;
		}
		if (found != null) return found;
		for (MethodNode m : target.methods) {
			if (!m.name.equals(method.name) || CallRepairs.insertedArgument(method.desc, m.desc) < 0
					|| (m.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) continue;
			if (found != null) return null;
			found = m;
		}
		return found;
	}

	/**
	 * The method of its class that {@code method}, which the platform marks deprecated, hands everything to: its body
	 * passes its parameters in order and then defaults to that one and returns its result
	 * ({@link ConstructorRepairs#forwardsItsParameters}), and the platform added that one
	 * ({@link ConstructorRepairs#platformAdded}). The platform's code calls it in {@code method}'s place, so an injection
	 * at the end of {@code method} runs only where something else still calls that, while the end of the other one is
	 * the same moment for both: NeoForge 21.1 keeps {@code HumanoidArmorLayer.renderArmorPiece} as a call of an overload
	 * that also takes the limb animation's values, which its {@code render} calls instead (Figura's armour hooks,
	 * PRE-20). Vanilla's own deprecated methods that pass their parameters on call a method vanilla has. Null otherwise.
	 */
	static MethodNode deprecatedDelegate(MethodNode method, ClassNode target, ClassNode vanilla) {
		if (method.name.startsWith("<") || !PreflightAsm.deprecated(method)) return null;
		MethodInsnNode call = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode m) call = m;
		}
		MethodNode delegate = call == null || !call.owner.equals(target.name) ? null : PreflightAsm.declared(target, call.name, call.desc);
		return delegate != null && ConstructorRepairs.forwardsItsParameters(method, delegate) && ConstructorRepairs.platformAdded(method, delegate, vanilla)
				? delegate : null;
	}

	/** Whether every injection point is {@code RETURN} with no ordinal: all of the method's returns. */
	private static boolean atReturns(List<AnnotationNode> ats) {
		for (AnnotationNode at : ats) {
			if (!(Annotations.value(at, "value") instanceof String v) || !v.equalsIgnoreCase("RETURN") || Annotations.value(at, "ordinal") != null) return false;
		}
		return true;
	}

	/** Whether every injection point is {@code HEAD}, not shifted: the method's start. */
	private static boolean atStart(List<AnnotationNode> ats) {
		for (AnnotationNode at : ats) {
			if (!(Annotations.value(at, "value") instanceof String v) || !v.equalsIgnoreCase("HEAD") || Annotations.value(at, "shift") != null) return false;
		}
		return true;
	}

	/**
	 * Whether the platform's class calls {@code overload}, which {@code method} only passes its parameters on to
	 * ({@link #deprecatedDelegate}), in place of each of vanilla's calls of {@code method} (in {@code vanilla}, its class
	 * there), so that {@code overload} starts where, and as often as, vanilla's {@code method} did: {@code method} is
	 * private in vanilla's class and {@code overload} in the platform's, so no other class calls them but the classes of
	 * their nests, which are counted too; each method there calls them (the platform's {@code method} or
	 * {@code overload}, as the one calls the other) as many times as vanilla's calls {@code method}, at least once in
	 * all, each call inside as many backward jumps (loops); and no method handle names them, which may be called from
	 * anywhere. NeoForge 21.1's {@code HumanoidArmorLayer.render} calls the overload of {@code renderArmorPiece} taking
	 * the limb animation's values four times, where vanilla's calls {@code renderArmorPiece} four times (ARMOR-1).
	 * {@code ModelBlockRenderer.tesselateBlock} is public, for the other classes that call it (NeoForge 21.1's section
	 * compiler, once per render type of a block), so Indigo's hook at its start stays (FRAPI-5).
	 */
	private boolean calledInItsPlace(MethodNode method, MethodNode overload, ClassNode target, ClassNode vanilla) {
		MethodNode before = vanilla == null ? null : PreflightAsm.declared(vanilla, method.name, method.desc);
		if (before == null || (before.access & Opcodes.ACC_PRIVATE) == 0 || (overload.access & Opcodes.ACC_PRIVATE) == 0) return false;
		java.util.Map<String, List<Integer>> made = callsByMethod(vanilla, method, method, this::vanillaClass);
		return made != null && !made.isEmpty() && made.equals(callsByMethod(target, method, overload, this::gameTarget));
	}

	/**
	 * The calls of {@code method} or {@code overload} of {@code node} that the methods of {@code node} (but
	 * {@code method}) and of its nest members make, by class and method: each call's count of backward jumps around it,
	 * in order. Null where {@code node} is another class's nest member, a nest member can't be read, or a method handle
	 * names either method.
	 */
	static java.util.Map<String, List<Integer>> callsByMethod(ClassNode node, MethodNode method, MethodNode overload,
			java.util.function.Function<String, ClassNode> classes) {
		if (node.nestHostClass != null) return null;
		List<ClassNode> nest = new ArrayList<>(List.of(node));
		for (String member : node.nestMembers == null ? List.<String>of() : node.nestMembers) {
			ClassNode read = classes.apply(member);
			if (read == null) return null;
			nest.add(read);
		}
		Predicate<String> named = nameAndDesc -> nameAndDesc.equals(method.name + method.desc) || nameAndDesc.equals(overload.name + overload.desc);
		java.util.Map<String, List<Integer>> calls = new java.util.TreeMap<>();
		for (ClassNode c : nest) {
			for (MethodNode m : c.methods) {
				if (c == node && m.name.equals(method.name) && m.desc.equals(method.desc)) continue;
				List<Integer> jumps = new ArrayList<>();
				for (int i = 0; i < m.instructions.size(); i++) {
					AbstractInsnNode insn = m.instructions.get(i);
					Object[] constants = insn instanceof InvokeDynamicInsnNode indy ? indy.bsmArgs
							: insn instanceof LdcInsnNode ldc ? new Object[] {ldc.cst} : new Object[0];
					for (Object constant : constants) {
						if (constant instanceof Handle handle && handle.getOwner().equals(node.name) && named.test(handle.getName() + handle.getDesc())) {
							return null;
						}
					}
					if (insn instanceof MethodInsnNode call && call.owner.equals(node.name) && named.test(call.name + call.desc)) jumps.add(backwardJumpsAround(m, i));
				}
				if (!jumps.isEmpty()) calls.put(c.name + "." + m.name + m.desc, jumps);
			}
		}
		return calls;
	}

	/** How many jumps of {@code method} after the instruction at {@code index} go back to it or before it. */
	private static int backwardJumpsAround(MethodNode method, int index) {
		int jumps = 0;
		for (int i = index + 1; i < method.instructions.size(); i++) {
			if (method.instructions.get(i) instanceof JumpInsnNode jump && method.instructions.indexOf(jump.label) <= index) jumps++;
		}
		return jumps;
	}

	/** The one other overload of the same name {@code method} calls, or null (none, or several). */
	static MethodNode calledOverload(MethodNode method, ClassNode target) {
		MethodNode overload = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || !call.name.equals(method.name)
					|| call.desc.equals(method.desc)) continue;
			MethodNode candidate = PreflightAsm.declared(target, call.name, call.desc);
			if (candidate == null || candidate == overload) continue;
			if (overload != null) return null; // several overloads: ambiguous
			overload = candidate;
		}
		return overload;
	}

	/**
	 * The index of the one parameter {@code overload} has that {@code method} doesn't, when {@code method} only calls
	 * {@code overload} with its own parameters in order and a value it works out for that one (no stores or jumps), and
	 * returns the result: NeoForge's way of adding a parameter while keeping vanilla's signature, as
	 * {@code MappedRegistry.register(key, value, info)} calling {@code register(this.byId.size(), key, value, info)}. -1
	 * otherwise.
	 */
	static int insertedParameter(MethodNode method, MethodNode overload) {
		Type[] own = Type.getArgumentTypes(method.desc);
		Type[] all = Type.getArgumentTypes(overload.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		if (all.length != own.length + 1 || (overload.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) return -1;
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= 0) code.add(insn); // not labels, line numbers or frames
		}
		if (code.size() < 3 || !(code.get(code.size() - 2) instanceof MethodInsnNode call) || !call.name.equals(overload.name)
				|| !call.desc.equals(overload.desc) || code.get(code.size() - 1).getOpcode() != Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)) return -1;
		int[] slots = new int[own.length];
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < own.length; i++) {
			slots[i] = slot;
			slot += own[i].getSize();
		}
		int start = 0;
		if (!isStatic) {
			if (!(code.get(0) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return -1;
			start = 1;
		}
		int k = 0;
		while (k < own.length && isLoad(code.get(start), own[k], slots[k])) {
			start++;
			k++;
		}
		int end = code.size() - 2;
		int j = own.length;
		while (j > k && end > start && isLoad(code.get(end - 1), own[j - 1], slots[j - 1])) {
			end--;
			j--;
		}
		if (j != k || start == end) return -1;
		for (int i = start; i < end; i++) {
			int op = code.get(i).getOpcode();
			boolean stores = op >= Opcodes.ISTORE && op <= Opcodes.SASTORE || op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC || op == Opcodes.IINC;
			if (stores || code.get(i) instanceof JumpInsnNode || code.get(i) instanceof TableSwitchInsnNode || code.get(i) instanceof LookupSwitchInsnNode
					|| op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) return -1;
		}
		for (int i = 0; i < own.length; i++) {
			if (!own[i].equals(all[i < k ? i : i + 1])) return -1;
		}
		return k;
	}

	private static boolean isLoad(AbstractInsnNode insn, Type type, int slot) {
		return insn instanceof VarInsnNode load && load.getOpcode() == type.getOpcode(Opcodes.ILOAD) && load.var == slot;
	}

	/**
	 * Where a handler of an injector other than {@code @Inject} takes {@code method}'s parameters (all of them, as Mixin
	 * requires), after its own and before any MixinExtras sugar; -1 when it doesn't take them.
	 */
	private static int capturedFrom(MethodNode handler, MethodNode method) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		if (args == null || params.isEmpty() || args.size() <= params.size()) return -1;
		return args.subList(args.size() - params.size(), args.size()).equals(params) ? args.size() - params.size() : -1;
	}

	/**
	 * How many of {@code method}'s first parameters a handler that isn't an {@code @Inject}'s may take after its own,
	 * before any MixinExtras sugar, as Mixin passes them: the most its own end with, which may count some of its own too
	 * (never too few). 0 when they end with none.
	 */
	private static int capturedPrefix(MethodNode handler, MethodNode method) {
		Type[] own = Type.getArgumentTypes(handler.desc);
		List<Type> args = new ArrayList<>();
		for (int i = 0; i < own.length; i++) {
			if (Annotations.ofParameter(handler, i).stream().noneMatch(a -> a.desc.startsWith("Lcom/llamalad7/mixinextras/sugar/"))) args.add(own[i]);
		}
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		for (int k = Math.min(params.size(), args.size()); k > 0; k--) {
			if (args.subList(args.size() - k, args.size()).equals(params.subList(0, k))) return k;
		}
		return 0;
	}

	/** Vanilla's version (VANILLA-1) of {@code method}, a method of {@code target}, or null. */
	private MethodNode vanillas(ClassNode target, MethodNode method) {
		ClassNode original = vanillaClass(target.name);
		return original == null ? null : PreflightAsm.declared(original, method.name, method.desc);
	}

	/**
	 * How the handler's {@code @Local}s find their locals in {@code overload}, an injector moved there from the method
	 * that only calls it: by name, when the overload has a local of that name; by type, when one local of that type
	 * besides the parameter the method inserted ({@code inserted}) is in scope at each instruction the injection point
	 * picks ({@link #pointPositions}), which the {@code @Local} then names by its ordinal (MixinExtras counts the
	 * parameters too): architectury's explosion hook takes the entities' list in {@code ServerExplosion.hurtEntities},
	 * whose overload takes the exploded blocks' list too; by its ordinal among the locals of its type, where the overload
	 * takes vanilla's method's parameters and the one it inserts, as the local with that ordinal among those of vanilla's
	 * version of the method ({@code original}, VANILLA-1), whose code aligned with the overload's ({@link PatchAlignment})
	 * tells which of the overload's locals are vanilla's ({@link LocalRepairs#isVanillasLocal}; the inserted parameter
	 * is the platform's), the {@code @Local} naming it by its ordinal among all of the overload's: FrozenLib's bone meal
	 * hook takes {@code @Local(ordinal = 0) BlockState} at the {@code getBlock} call in {@code BoneMealItem.growCrop},
	 * whose code NeoForge 21.1 and Forge 52 moved into {@code applyBonemeal}, after their bone meal events (OVERLOAD-3);
	 * or, as {@link LocalRepairs#localsFit} allows, a parameter. The changes to make, or null when one can't be found
	 * that way.
	 */
	private static List<Runnable> localsInOverload(MethodNode handler, MethodNode overload, int inserted, List<AnnotationNode> ats, MethodNode original,
			Context ctx) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(overload.desc);
		boolean isStatic = (overload.access & Opcodes.ACC_STATIC) != 0;
		int insertedSlot = -1;
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < params.length; i++) {
			if (i == inserted) insertedSlot = slot;
			slot += params[i].getSize();
		}
		List<Runnable> changes = new ArrayList<>();
		Set<Integer> vanillaPlaces = null;
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(MixinNames.LOCAL)) continue;
				Type type = types[i];
				if (Boolean.TRUE.equals(Annotations.value(a, "argsOnly"))) {
					if (List.of(params).stream().filter(type::equals).count() != 1) return null;
				} else if (Annotations.value(a, "name") instanceof List<?> names && !names.isEmpty()) {
					if (overload.localVariables == null || overload.localVariables.stream().noneMatch(lv -> names.contains(lv.name))) return null;
				} else if (Annotations.value(a, "index") != null || type.getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) {
					return null;
				} else if (Annotations.value(a, "ordinal") != null) {
					if (!(Annotations.value(a, "ordinal") instanceof Integer nth) || nth < 0 || original == null || overload.localVariables == null) return null;
					if (vanillaPlaces == null) {
						// The ordinal counts the parameters: the overload's must be vanilla's method's, and the one it inserts.
						List<Type> own = new ArrayList<>(List.of(params));
						if (inserted >= 0) own.remove(inserted);
						if (!own.equals(List.of(Type.getArgumentTypes(original.desc)))) return null;
						PatchAlignment alignment = PatchAlignment.of(original, overload);
						if (alignment == null) return null;
						vanillaPlaces = alignment.vanillaPlaces();
					}
					List<Integer> positions = pointPositions(ats, overload, ctx);
					int ordinal = positions == null ? -1 : LocalRepairs.vanillasNthLocalOrdinal(overload, type.getDescriptor(), positions, vanillaPlaces, nth, insertedSlot);
					if (ordinal < 0) return null;
					if (ordinal != nth) changes.add(() -> Annotations.put(a, "ordinal", ordinal));
				} else {
					List<Integer> positions = pointPositions(ats, overload, ctx);
					int ordinal = positions == null ? -1 : ordinalAt(overload, type, insertedSlot, positions);
					if (ordinal < 0) return null;
					changes.add(() -> {
						a.values = a.values == null ? new ArrayList<>() : a.values;
						a.values.add("ordinal");
						a.values.add(ordinal);
					});
				}
			}
		}
		return changes;
	}

	/**
	 * The instructions of {@code method}, by index, at which an injector's points inject: each return, for points at the
	 * end; for one point naming a call, field access or construction (not shifted after it), each instruction it
	 * matches, or its ordinal-th. Null for any other point, or when it matches none.
	 */
	private static List<Integer> pointPositions(List<AnnotationNode> ats, MethodNode method, Context ctx) {
		List<Integer> positions = new ArrayList<>();
		if (ConstructorRepairs.atEndOnly(ats)) {
			for (int i = 0; i < method.instructions.size(); i++) {
				int op = method.instructions.get(i).getOpcode();
				if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) positions.add(i);
			}
			return positions.isEmpty() ? null : positions;
		}
		if (ats.size() != 1) return null;
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)
				|| Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return null;
		int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if ((opcode < 0 || insn.getOpcode() == opcode) && InjectionPoints.matchesInsn(point, ref, insn)) positions.add(i);
		}
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (ordinal >= 0) return ordinal < positions.size() ? List.of(positions.get(ordinal)) : null;
		return positions.isEmpty() ? null : positions;
	}

	/**
	 * The ordinal, among the locals of {@code type} in scope (parameters first, by slot), of the one besides the parameter
	 * at {@code insertedSlot} that is in scope at each of {@code positions} in {@code method}; -1 when there is no such
	 * single local (or no local variable table).
	 */
	private static int ordinalAt(MethodNode method, Type type, int insertedSlot, List<Integer> positions) {
		if (method.localVariables == null || method.localVariables.isEmpty()) return -1;
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			for (LocalVariableNode lv : method.localVariables) {
				if (lv.desc.equals(type.getDescriptor()) && method.instructions.indexOf(lv.start) <= i && i < method.instructions.indexOf(lv.end)) {
					inScope.add(lv.index);
				}
			}
			TreeSet<Integer> others = new TreeSet<>(inScope);
			others.remove(insertedSlot);
			if (others.size() != 1) return -1;
			int ordinal = inScope.headSet(others.first()).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}
}
