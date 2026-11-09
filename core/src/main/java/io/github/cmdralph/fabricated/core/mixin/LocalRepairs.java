package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for locals and arguments: a local variable vanilla's code names that NeoForge's build orders
 * otherwise, a {@code @ModifyVariable} taking an argument by name, and local captures made to skip themselves instead
 * of crashing.
 */
final class LocalRepairs extends PreflightPart {
	LocalRepairs(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Gives a {@code @ModifyVariable} taking a method's argument by its name, with {@code argsOnly}, the argument's slot
	 * instead, where the running Mixin doesn't know arguments by their names (ARGS-1: SpongePowered's names them {@code arg}
	 * and their slot, so Fabric API's resource loader hook taking {@code enableProfiling} in
	 * {@code SimpleReloadInstance.create} failed on Forge 65; Fabric's Mixin takes their names from 0.17.0). The argument is
	 * the one of that name in the target method's local variable table, or in vanilla's, or else the only one of the
	 * handler's type; it must be the same slot in every target. Returns a description of each.
	 */
	List<String> argumentNameRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (argumentNames || !injector.desc.equals(MixinNames.MODIFY_VARIABLE) || !Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| !(Annotations.value(injector, "name") instanceof List<?> names) || names.isEmpty()
				|| Annotations.value(injector, "index") != null || Annotations.value(injector, "ordinal") != null) return List.of();
		Type type = Type.getReturnType(handler.desc);
		Integer slot = argumentSlotInEvery(injector, targets, names, type.getDescriptor()::equals, type, ctx);
		if (slot == null) return List.of();
		removeValue(injector, "name");
		Annotations.put(injector, "index", slot);
		return List.of(takesSlot(names, slot));
	}

	/**
	 * Gives each MixinExtras {@code @Local(argsOnly = true, name = ...)} a handler takes the argument's slot instead, where
	 * the running Mixin doesn't know arguments by their names: MixinExtras finds the argument with Mixin's local variable
	 * discriminator, which on SpongePowered's Mixin names arguments {@code arg} and their slot, as for ARGS-1 (PRE-18:
	 * ImmediatelyFast's map atlas hook takes {@code @Local(name = "mapRenderState", argsOnly = true)} in
	 * {@code GuiGraphicsExtractor.map}, which failed on Forge 65 with "(0/1) succeeded", as ARGS-1's hook had). The
	 * argument is found as {@link #argumentNameRepairs} finds it: by its name in the target's local variable table or
	 * vanilla's, or else the only argument of the type the local holds (not for a {@code LocalRef}, whose type is in its
	 * signature). The same goes for a MixinExtras expression's {@code @Definition} of an argument by its name, which
	 * MixinExtras finds with the same discriminator (PRE-21: Debugify's cactus fix wraps
	 * {@code ?.getBlockState(pos.relative(direction))} in {@code CactusBlock.canSurvive}, defining {@code pos} as
	 * {@code @Local(type = BlockPos.class, name = "pos", argsOnly = true)}, so the expression matched nothing on Forge 64
	 * to 66 and the server stopped in {@code Blocks}' initializer: "(0/1) succeeded"). Returns a description of each.
	 */
	List<String> localArgumentNameRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (argumentNames) return List.of();
		List<String> repaired = new ArrayList<>();
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (!takesArgumentByName(local)) continue;
				List<?> names = (List<?>) Annotations.value(local, "name");
				Type held = heldType(params[p]);
				Predicate<String> accepts = held != null ? held.getDescriptor()::equals
						: desc -> desc.startsWith("L") || desc.startsWith("[");
				Integer slot = argumentSlotInEvery(injector, targets, names, accepts, held, ctx);
				if (slot == null) continue;
				removeValue(local, "name");
				Annotations.put(local, "index", slot);
				repaired.add(takesSlot(names, slot));
			}
		}
		for (AnnotationNode local : InjectionPoints.definitionLocals(handler)) {
			// A definition's local has the type it is given; without one, Mixin's discriminator matches no local at all.
			if (!takesArgumentByName(local) || !(Annotations.value(local, "type") instanceof Type held)) continue;
			List<?> names = (List<?>) Annotations.value(local, "name");
			Integer slot = argumentSlotInEvery(injector, targets, names, held.getDescriptor()::equals, held, ctx);
			if (slot == null) continue;
			removeValue(local, "name");
			Annotations.put(local, "index", slot);
			repaired.add("in its expression " + takesSlot(names, slot));
		}
		return repaired;
	}

	/** Whether an annotation is an {@code @Local(argsOnly = true)} taking the argument only by its name. */
	static boolean takesArgumentByName(AnnotationNode local) {
		return local.desc.equals(MixinNames.LOCAL) && Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))
				&& Annotations.value(local, "name") instanceof List<?> names && !names.isEmpty()
				&& Annotations.value(local, "index") == null && Annotations.value(local, "ordinal") == null;
	}

	/**
	 * The type of the local a handler parameter captures: the parameter's own type, or the one a MixinExtras ref of a
	 * primitive holds; null for a {@code LocalRef}, which holds any reference type.
	 */
	static Type heldType(Type param) {
		String desc = param.getDescriptor();
		if (!desc.startsWith(SUGAR_REFS)) return param;
		return switch (desc.substring(SUGAR_REFS.length())) {
			case "LocalBooleanRef;" -> Type.BOOLEAN_TYPE;
			case "LocalByteRef;" -> Type.BYTE_TYPE;
			case "LocalCharRef;" -> Type.CHAR_TYPE;
			case "LocalShortRef;" -> Type.SHORT_TYPE;
			case "LocalIntRef;" -> Type.INT_TYPE;
			case "LocalLongRef;" -> Type.LONG_TYPE;
			case "LocalFloatRef;" -> Type.FLOAT_TYPE;
			case "LocalDoubleRef;" -> Type.DOUBLE_TYPE;
			default -> null;
		};
	}

	private static final String SUGAR_REFS = "Lcom/llamalad7/mixinextras/sugar/ref/";

	/**
	 * The slot of the argument {@code names} names in every method the injector selects in every target: by its name in
	 * the method's local variable table, or in vanilla's version of it, or else the only argument of {@code type} (none
	 * when {@code type} is null); null unless it is the same slot everywhere.
	 */
	private Integer argumentSlotInEvery(AnnotationNode injector, List<ClassNode> targets, List<?> names, Predicate<String> accepts,
			Type type, Context ctx) {
		Integer slot = null;
		for (ClassNode target : targets) {
			List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
			if (methods.isEmpty()) return null;
			ClassNode original = vanillaClass(target.name);
			for (MethodNode m : methods) {
				Integer found = argumentSlot(m, names, accepts);
				MethodNode before = found == null && original != null ? PreflightAsm.declared(original, m.name, m.desc) : null;
				if (before != null) found = argumentSlot(before, names, accepts);
				if (found == null && type != null) found = onlyArgumentOfType(m, type);
				if (found == null || slot != null && !slot.equals(found)) return null;
				slot = found;
			}
		}
		return slot;
	}

	private String takesSlot(List<?> names, int slot) {
		return "the argument " + names.get(0) + " by its name, which " + loader + "'s Mixin doesn't know; it takes the argument's slot, "
				+ slot + ", instead";
	}

	/** Removes an annotation's value. */
	private static void removeValue(AnnotationNode annotation, String key) {
		if (annotation.values == null) return;
		for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
			if (key.equals(annotation.values.get(i))) {
				annotation.values.remove(i + 1);
				annotation.values.remove(i);
				return;
			}
		}
	}

	/** The slot of the method's argument that its local variable table names one of {@code names}, of a type {@code accepts}; null when none. */
	private static Integer argumentSlot(MethodNode method, List<?> names, Predicate<String> accepts) {
		if (method.localVariables == null) return null;
		int end = argumentsEnd(method);
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.index < end && names.contains(lv.name) && accepts.test(lv.desc) && isArgumentSlot(method, lv.index)) return lv.index;
		}
		return null;
	}

	/** The slot of the method's only argument of {@code type}; null when it has none or several. */
	private static Integer onlyArgumentOfType(MethodNode method, Type type) {
		Integer found = null;
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (arg.equals(type)) {
				if (found != null) return null;
				found = slot;
			}
			slot += arg.getSize();
		}
		return found;
	}

	/** The first slot after the method's arguments. */
	private static int argumentsEnd(MethodNode method) {
		return ((method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1) + (Type.getArgumentsAndReturnSizes(method.desc) >> 2) - 1;
	}

	/** Whether a slot is where one of the method's arguments starts (not {@code this}, nor the second half of a long or double). */
	private static boolean isArgumentSlot(MethodNode method, int index) {
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (slot == index) return true;
			slot += arg.getSize();
		}
		return false;
	}

	/**
	 * Names by its ordinal each {@code @Local} capturing a local by its type alone where this game build has several of
	 * that type in scope at the injection point, NeoForge's patch having added one (LOCAL-2: Ledger's item pickup hooks
	 * in {@code ItemEntity.playerTouch}, where NeoForge's patch copies the stack for its pickup event). Vanilla's version
	 * of the method (VANILLA-1) aligned with this build's ({@link PatchAlignment}) tells which local is vanilla's: the
	 * one whose store is one of vanilla's instructions, or a parameter, when exactly one of those in scope is, as vanilla
	 * had one of the type there for the capture to work at all. Its ordinal among the locals of the type in scope
	 * (parameters first, by slot, as {@link OverloadRepairs#ordinalAt} counts) must be the same at every instruction the point picks.
	 * An {@code @Local} by type with an ordinal names vanilla's local of that ordinal among vanilla's of the type, which
	 * gets its ordinal here where the patch added one before it (LOCAL-4: Fabric API's Indigo takes {@code @Local(ordinal
	 * = 0) Map} in {@code SectionCompiler.compile}, vanilla's map of buffers by layer, where Forge 61's patch first keeps
	 * the section's model data in a map). Only for an injector with one {@code @At} naming a call, field access or
	 * construction, outside a slice, into one method vanilla declares, as {@link MixinPreflight#ambiguousLocal} judges them. Returns a
	 * description of each.
	 */
	List<String> vanillaLocalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (Annotations.value(injector, "slice") != null) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = PreflightAsm.declared(original, patched.name, patched.desc);
		if (before == null || patched.localVariables == null || patched.localVariables.isEmpty()) return List.of();
		List<Integer> positions = PreflightAsm.matching(patched, point, ref, Annotations.value(at, "opcode") instanceof Integer o ? o : -1);
		int pointOrdinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (pointOrdinal >= 0) positions = pointOrdinal < positions.size() ? List.of(positions.get(pointOrdinal)) : List.of();
		if (positions.isEmpty()) return List.of();
		Set<Integer> vanillaPlaces = null;
		List<String> named = new ArrayList<>();
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				Object given = Annotations.value(local, "ordinal");
				if (!local.desc.equals(MixinNames.LOCAL) || Annotations.value(local, "name") != null || Annotations.value(local, "index") != null
						|| given != null && !(given instanceof Integer) || Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))
						|| params[p].getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
				if (vanillaPlaces == null) {
					PatchAlignment alignment = PatchAlignment.of(before, patched);
					if (alignment == null) return named;
					vanillaPlaces = new HashSet<>();
					for (int i = 0; i < before.instructions.size(); i++) {
						if (alignment.placeOf(i) >= 0) vanillaPlaces.add(alignment.placeOf(i));
					}
				}
				if (given instanceof Integer vanillas) {
					int ordinal = vanillasNthLocalOrdinal(patched, params[p].getDescriptor(), positions, vanillaPlaces, vanillas);
					if (ordinal < 0 || ordinal == vanillas) continue;
					Annotations.put(local, "ordinal", ordinal);
					named.add("the local of type " + params[p].getClassName() + " with ordinal " + vanillas + ", vanilla's, which has ordinal " + ordinal
							+ " in " + patched.name + " in " + target.name.replace('/', '.') + " there, " + loader + "'s patch having added one of the type"
							+ " before it; the @Local names it");
					continue;
				}
				int ordinal = vanillasLocalOrdinal(patched, params[p].getDescriptor(), positions, vanillaPlaces);
				if (ordinal < 0) continue;
				Annotations.put(local, "ordinal", ordinal);
				named.add("the local of type " + params[p].getClassName() + " by its type, of which " + patched.name + " in "
						+ target.name.replace('/', '.') + " has more than one there, " + loader + "'s patch having added one; vanilla's is the one with ordinal "
						+ ordinal + " here, and the @Local names it");
			}
		}
		return named;
	}

	/**
	 * Gives a {@code @ModifyVariable} picking its local by an ordinal among those of its type ({@code ordinal}, no name or
	 * slot) the ordinal vanilla's local has in this game build's code, where the platform's patch declares one of the type
	 * before or after it (PRE-25: Apoli's {@code modifyEffectiveTool} modifies vanilla's second boolean in scope at the
	 * {@code ItemStack.mineBlock} call in {@code ServerPlayerGameMode.destroyBlock}, its tool check; Forge 47's patch
	 * declares the first one, the block's removal, after the call, so the tool check is the first there, and Mixin, finding
	 * no second one, stopped a Forge 47 server with Origins). Vanilla's version of the method (VANILLA-1), aligned with this
	 * build's ({@link PatchAlignment}), tells which local is vanilla's: at the vanilla instruction each picked one is,
	 * the local the ordinal picks among those of the type its local variable table has in scope (parameters first, by
	 * slot); here, the parameter in that slot, or the local whose scope starts after the store aligned with the store
	 * vanilla's starts after. Its ordinal among the locals of the type in scope here must be the same at each instruction.
	 * Only for an injector with one unshifted {@code @At} naming a call, field access or construction, outside a slice,
	 * into one method vanilla declares, both with local variable tables. Returns a description of the change.
	 */
	List<String> vanillaVariableOrdinalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MixinNames.MODIFY_VARIABLE) || Annotations.value(injector, "slice") != null || Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "name") != null || Annotations.value(injector, "index") != null
				|| !(Annotations.value(injector, "ordinal") instanceof Integer vanillas) || vanillas < 0) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)
				|| Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		Type type = Type.getReturnType(handler.desc);
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = ref.fuzzy() || type.getSort() == Type.VOID || original == null ? List.of() : PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = PreflightAsm.declared(original, patched.name, patched.desc);
		if (before == null || patched.localVariables == null || patched.localVariables.isEmpty() || before.localVariables == null
				|| before.localVariables.isEmpty()) return List.of();
		List<Integer> positions = PreflightAsm.matching(patched, point, ref, Annotations.value(at, "opcode") instanceof Integer o ? o : -1);
		int pointOrdinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (pointOrdinal >= 0) positions = pointOrdinal < positions.size() ? List.of(positions.get(pointOrdinal)) : List.of();
		PatchAlignment alignment = positions.isEmpty() ? null : PatchAlignment.of(before, patched);
		if (alignment == null) return List.of();
		Map<Integer, Integer> vanillaAt = new HashMap<>();
		for (int i = 0; i < before.instructions.size(); i++) {
			if (alignment.placeOf(i) >= 0) vanillaAt.put(alignment.placeOf(i), i);
		}
		String desc = type.getDescriptor();
		int parameterSlots = argumentsEnd(patched);
		int found = -1;
		for (int p : positions) {
			Integer v = vanillaAt.get(p);
			if (v == null) return List.of();
			List<LocalVariableNode> vanillaScope = inScope(before, v, desc);
			vanillaScope.sort(java.util.Comparator.comparingInt(lv -> lv.index));
			if (vanillaScope.size() <= vanillas || vanillaScope.stream().map(lv -> lv.index).distinct().count() != vanillaScope.size()) return List.of();
			LocalVariableNode local = vanillaScope.get(vanillas);
			int slot;
			if (local.index < parameterSlots) {
				slot = local.index;
			} else {
				int store = storeBefore(before, local);
				int place = store < 0 ? -1 : alignment.placeOf(store);
				if (place < 0 || !(patched.instructions.get(place) instanceof VarInsnNode var) || var.getOpcode() != type.getOpcode(Opcodes.ISTORE)) return List.of();
				slot = var.var;
				int placed = place;
				if (patched.localVariables.stream().noneMatch(lv -> lv.index == var.var && lv.desc.equals(desc) && storeBefore(patched, lv) == placed)) {
					return List.of();
				}
			}
			List<LocalVariableNode> here = inScope(patched, p, desc);
			if (here.stream().noneMatch(lv -> lv.index == slot) || here.stream().map(lv -> lv.index).distinct().count() != here.size()) return List.of();
			int ordinal = (int) here.stream().filter(lv -> lv.index < slot).count();
			if (found >= 0 && found != ordinal) return List.of();
			found = ordinal;
		}
		if (found < 0 || found == vanillas) return List.of();
		Annotations.put(injector, "ordinal", found);
		return List.of("the local of type " + type.getClassName() + " with ordinal " + vanillas + " in " + patched.name + patched.desc + " in "
				+ target.name.replace('/', '.') + ", vanilla's there, which has ordinal " + found + " in this game build, where " + loader + "'s patch has "
				+ (found < vanillas ? "fewer" : "more") + " locals of the type in scope before it; the injector takes it by that ordinal");
	}

	/** The index of the instruction before a local's scope starts (its store), skipping labels, line numbers and frames; -1 for none. */
	private static int storeBefore(MethodNode method, LocalVariableNode local) {
		AbstractInsnNode previous = local.start.getPrevious();
		while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
		return previous == null ? -1 : method.instructions.indexOf(previous);
	}

	/**
	 * Adds this build's name to each {@code @Local(name = ...)} none of whose names a local of its type has in scope at the
	 * injection point, where exactly one local of that type is in scope there, the one MixinExtras would take by the type
	 * alone (LOCAL-3: Fabric API's lifecycle hook takes {@code @Local(name = "level") ServerLevel} at
	 * {@code ServerLevel.close()} in {@code MinecraftServer.stopServer}, a variable Forge 64's build, recompiled from
	 * decompiled source, names {@code levelx}, the method declaring another {@code level} before it). MixinExtras takes
	 * any of a {@code @Local}'s names, so where one of the mod's is in scope it still is. Only for an injector with one
	 * {@code @At} naming a call, field access or construction, outside a slice, in methods with local variable names.
	 * Returns a description of each.
	 */
	static List<String> namedLocalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.isEmpty()) return List.of();
		List<String> added = new ArrayList<>();
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			String desc = params[p].getDescriptor();
			if (desc.startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (!local.desc.equals(MixinNames.LOCAL) || !(Annotations.value(local, "name") instanceof List<?> names) || names.isEmpty()
						|| Annotations.value(local, "index") != null || Annotations.value(local, "ordinal") != null) continue;
				String found = null;
				boolean failed = false;
				methods:
				for (MethodNode m : methods) {
					List<Integer> positions = pointPositions(injector, m, ctx);
					if (positions == null || positions.isEmpty() || m.localVariables == null || m.localVariables.isEmpty()) {
						failed = true;
						break;
					}
					for (int i : positions) {
						List<LocalVariableNode> inScope = inScope(m, i, desc);
						if (inScope.stream().anyMatch(lv -> names.contains(lv.name))) continue;
						Set<String> named = new HashSet<>();
						Set<Integer> slots = new HashSet<>();
						for (LocalVariableNode lv : inScope) {
							named.add(lv.name);
							slots.add(lv.index);
						}
						String name = named.size() == 1 && slots.size() == 1 ? named.iterator().next() : null;
						if (name == null || found != null && !found.equals(name)) {
							failed = true;
							break methods;
						}
						found = name;
					}
				}
				if (failed || found == null) continue;
				List<Object> extended = new ArrayList<>(names);
				extended.add(found);
				Annotations.put(local, "name", extended);
				added.add("the local " + names.get(0) + " of type " + params[p].getClassName() + ", which " + methods.get(0).name + " in "
						+ target.name.replace('/', '.') + " names " + found + " where the injector applies; the @Local takes that name too");
			}
		}
		return added;
	}

	/**
	 * Gives each {@code @Local(index = N)} whose slot never holds a value of its type in this game build
	 * ({@link MixinPreflight#slotNeverHolds}) the slot this build keeps vanilla's local in (CORPUS-55: Porting Lib
	 * Entity's {@code GuiMixin} takes the effect, draw position and alpha of {@code Gui.renderEffects}'s loop by vanilla's
	 * slots, {@code @Local(index = 8) MobEffectInstance}, {@code @Local(index = 10) int} and so on, which Forge 47's build,
	 * recompiled from decompiled source, keeps in others). That slot is the one the instructions storing vanilla's slot
	 * store to here, vanilla's version of the method aligned with this build's (VANILLA-1, {@link PatchAlignment}), where
	 * exactly one such slot has a local of the type in scope wherever the injector applies; without vanilla's code, or
	 * where that tells nothing, the only local of the type in scope there, the one MixinExtras would take by the type
	 * alone, as {@link #namedLocalRepairs} takes it for a name. Either must be the same slot at every instruction the
	 * point picks in every method; only for an injector with one {@code @At} naming a call, field access or construction,
	 * outside a slice, in methods with local variable names. Returns a description of each.
	 */
	List<String> indexedLocalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.isEmpty()) return List.of();
		Map<MethodNode, List<Integer>> points = new HashMap<>();
		for (MethodNode m : methods) {
			List<Integer> positions = pointPositions(injector, m, ctx);
			if (positions == null || positions.isEmpty()) return List.of();
			points.put(m, positions);
		}
		ClassNode original = methods.size() == 1 ? vanillaClass(target.name) : null;
		MethodNode before = original == null ? null : PreflightAsm.declared(original, methods.get(0).name, methods.get(0).desc);
		PatchAlignment alignment = before == null ? null : PatchAlignment.of(before, methods.get(0));
		List<String> moved = new ArrayList<>();
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			Type type = heldType(params[p]);
			if (type == null) continue;
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				Integer index = MixinPreflight.indexOnly(local);
				if (index == null || !methods.stream().allMatch(m -> MixinPreflight.slotNeverHolds(type, m, index, target.name, game))) continue;
				Integer found = alignment == null ? null : vanillasSlot(before, methods.get(0), alignment, index, type, points.get(methods.get(0)));
				boolean vanillas = found != null;
				if (found == null) found = onlySlotInScope(points, type);
				if (found == null) continue;
				Annotations.put(local, "index", found);
				moved.add("the local of type " + type.getClassName() + " at slot " + index + ", which " + methods.get(0).name + " in "
						+ target.name.replace('/', '.') + " never holds there; " + (vanillas ? "this build keeps vanilla's local there in slot " + found
								: "the only one of the type where the injector applies is at slot " + found) + ", and the @Local takes that one");
			}
		}
		return moved;
	}

	/**
	 * The slot of this build's method the instructions storing vanilla's slot {@code slot} (a store of the type's kind)
	 * store to, where exactly one of those has a local of the type in scope at every one of {@code positions}; null
	 * otherwise. A store the patch changed, or whose place can't be told, tells nothing.
	 */
	private static Integer vanillasSlot(MethodNode before, MethodNode patched, PatchAlignment alignment, int slot, Type type, List<Integer> positions) {
		int store = type.getOpcode(Opcodes.ISTORE);
		Set<Integer> slots = new TreeSet<>();
		for (int i = 0; i < before.instructions.size(); i++) {
			if (!(before.instructions.get(i) instanceof VarInsnNode v) || v.var != slot || v.getOpcode() != store) continue;
			int place = alignment.placeOf(i);
			if (place >= 0 && patched.instructions.get(place) instanceof VarInsnNode there && there.getOpcode() == store) slots.add(there.var);
		}
		Integer found = null;
		for (int candidate : slots) {
			if (!positions.stream().allMatch(i -> inScope(patched, i, type.getDescriptor()).stream().anyMatch(lv -> lv.index == candidate))) continue;
			if (found != null) return null;
			found = candidate;
		}
		return found;
	}

	/** The slot of the only local of the type in scope at every point, the same in every method; null otherwise. */
	private static Integer onlySlotInScope(Map<MethodNode, List<Integer>> points, Type type) {
		Integer found = null;
		for (Map.Entry<MethodNode, List<Integer>> entry : points.entrySet()) {
			for (int i : entry.getValue()) {
				List<LocalVariableNode> inScope = inScope(entry.getKey(), i, type.getDescriptor());
				if (inScope.size() != 1 || found != null && found != inScope.get(0).index) return null;
				found = inScope.get(0).index;
			}
		}
		return found;
	}

	/**
	 * The first name a handler's {@code @Local(name = ...)} asks for that no local has in scope at an instruction the
	 * injector's point picks, in a method that declares one of its names elsewhere: MixinExtras looks among the locals in
	 * scope there. Only for an injector with one {@code @At} naming a call, field access or construction, as
	 * {@link #pointPositions} finds them; null otherwise.
	 */
	static String namedLocalOutOfScope(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, Context ctx) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (!local.desc.equals(MixinNames.LOCAL) || !(Annotations.value(local, "name") instanceof List<?> names) || names.isEmpty()) continue;
				for (MethodNode m : targets) {
					if (m.localVariables == null || m.localVariables.stream().noneMatch(lv -> names.contains(lv.name))) continue;
					List<Integer> positions = pointPositions(injector, m, ctx);
					if (positions == null) continue;
					for (int i : positions) {
						if (m.localVariables.stream().noneMatch(lv -> names.contains(lv.name) && m.instructions.indexOf(lv.start) <= i
								&& i < m.instructions.indexOf(lv.end))) return String.valueOf(names.get(0));
					}
				}
			}
		}
		return null;
	}

	/**
	 * The instructions an injector's single {@code @At} naming a call, field access or construction picks in a method, with
	 * its {@code ordinal} and {@code opcode}; null for any other point, several, or a fuzzy target.
	 */
	private static List<Integer> pointPositions(AnnotationNode injector, MethodNode method, Context ctx) {
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return null;
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return null;
		List<Integer> positions = PreflightAsm.matching(method, point, ref, Annotations.value(at, "opcode") instanceof Integer o ? o : -1);
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (ordinal >= 0) positions = ordinal < positions.size() ? List.of(positions.get(ordinal)) : List.of();
		return positions;
	}

	/** The locals of type {@code desc} in scope at the instruction at {@code index}. */
	private static List<LocalVariableNode> inScope(MethodNode method, int index, String desc) {
		List<LocalVariableNode> found = new ArrayList<>();
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.desc.equals(desc) && method.instructions.indexOf(lv.start) <= index && index < method.instructions.indexOf(lv.end)) found.add(lv);
		}
		return found;
	}

	/**
	 * The ordinal, among the locals of type {@code desc} in scope (parameters first, by slot), of the one of them that is
	 * vanilla's ({@link #isVanillasLocal}), the same at each of {@code positions}; -1 when there aren't several in scope
	 * at one of them, or not exactly one of those is vanilla's.
	 */
	private static int vanillasLocalOrdinal(MethodNode method, String desc, List<Integer> positions, Set<Integer> vanillaPlaces) {
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			int vanillas = 0;
			int chosen = -1;
			for (LocalVariableNode lv : method.localVariables) {
				if (!lv.desc.equals(desc) || method.instructions.indexOf(lv.start) > i || i >= method.instructions.indexOf(lv.end)) continue;
				inScope.add(lv.index);
				if (isVanillasLocal(method, lv, vanillaPlaces)) {
					vanillas++;
					chosen = lv.index;
				}
			}
			if (inScope.size() < 2 || vanillas != 1) return -1;
			int ordinal = inScope.headSet(chosen).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}

	/**
	 * The ordinal, among the locals of type {@code desc} in scope (parameters first, by slot), of the one with ordinal
	 * {@code nth} among those of them that are vanilla's ({@link #isVanillasLocal}), the same at each of {@code positions};
	 * -1 when fewer than {@code nth + 1} of them are vanilla's at one of them.
	 */
	private static int vanillasNthLocalOrdinal(MethodNode method, String desc, List<Integer> positions, Set<Integer> vanillaPlaces, int nth) {
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			TreeSet<Integer> vanillas = new TreeSet<>();
			for (LocalVariableNode lv : method.localVariables) {
				if (!lv.desc.equals(desc) || method.instructions.indexOf(lv.start) > i || i >= method.instructions.indexOf(lv.end)) continue;
				inScope.add(lv.index);
				if (isVanillasLocal(method, lv, vanillaPlaces)) vanillas.add(lv.index);
			}
			if (vanillas.size() <= nth) return -1;
			int chosen = vanillas.stream().skip(nth).findFirst().orElseThrow();
			int ordinal = inScope.headSet(chosen).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}

	/**
	 * Whether a local is vanilla's: a parameter, or one whose scope starts right after a store into its slot that is one
	 * of vanilla's instructions ({@code vanillaPlaces}, their places in this build's method), not one NeoForge's patch put
	 * there.
	 */
	static boolean isVanillasLocal(MethodNode method, LocalVariableNode local, Set<Integer> vanillaPlaces) {
		AbstractInsnNode store = local.start.getPrevious();
		while (store != null && store.getOpcode() < 0) store = store.getPrevious();
		if (store == null) {
			int parameterSlots = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
			for (Type type : Type.getArgumentTypes(method.desc)) parameterSlots += type.getSize();
			return local.index < parameterSlots;
		}
		return store instanceof VarInsnNode var && var.getOpcode() >= Opcodes.ISTORE && var.getOpcode() <= Opcodes.ASTORE && var.var == local.index
				&& vanillaPlaces.contains(method.instructions.indexOf(store));
	}

	/**
	 * Makes an {@code @Inject} that captures local variables skip itself where they differ instead of stopping the game:
	 * one whose capture fails hard is made to fail soft, and one that fails soft but is required (Debugify's on 1.21.1)
	 * is no longer required, as Mixin stops the game when it skips itself then. Returns whether it changed.
	 *
	 * @param require the injector's {@code require} when it gives none: its config's {@code defaultRequire}, or 0 in a
	 *                group, which counts its injections instead
	 */
	static boolean softenLocalCapture(AnnotationNode injector, int require) {
		if (!injector.desc.equals(MixinNames.INJECT) || !(Annotations.value(injector, "locals") instanceof String[] capture)) return false;
		boolean hard = capture[1].equals("CAPTURE_FAILHARD") || capture[1].equals("CAPTURE_FAILEXCEPTION");
		int required = Annotations.value(injector, "require") instanceof Integer r && r >= 0 ? r : require;
		if (!hard && !(capture[1].equals("CAPTURE_FAILSOFT") && required > 0)) return false;
		for (int i = 0; i + 1 < injector.values.size(); i += 2) {
			if ("locals".equals(injector.values.get(i))) injector.values.set(i + 1, new String[] {capture[0], "CAPTURE_FAILSOFT"});
			if ("require".equals(injector.values.get(i))) injector.values.set(i + 1, 0);
		}
		if (!(Annotations.value(injector, "require") instanceof Integer)) {
			injector.values.add("require");
			injector.values.add(0);
		}
		return true;
	}

	/**
	 * Whether the handler's {@code @Local}s still find what they captured in {@code method}: each must be an
	 * {@code @Local(argsOnly = true)} of a type exactly one of {@code method}'s parameters has (other locals differ), and
	 * one given a slot must find that parameter there.
	 */
	static boolean localsFit(MethodNode handler, MethodNode method) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(MixinNames.LOCAL)) continue;
				Type local = types[i];
				if (!Boolean.TRUE.equals(Annotations.value(a, "argsOnly")) || local.getDescriptor().startsWith(SUGAR_REFS)
						|| params.stream().filter(local::equals).count() != 1) return false;
				if (Annotations.value(a, "index") instanceof Integer slot && !local.equals(argumentAt(method, slot))) return false;
			}
		}
		return true;
	}

	/** The type of the method's argument that starts at a slot; null when none does. */
	private static Type argumentAt(MethodNode method, int index) {
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (slot == index) return arg;
			slot += arg.getSize();
		}
		return null;
	}
}
