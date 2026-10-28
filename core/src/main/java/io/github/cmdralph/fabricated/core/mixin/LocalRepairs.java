package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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
		Integer slot = null;
		for (ClassNode target : targets) {
			List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
			if (methods.isEmpty()) return List.of();
			ClassNode original = vanillaClass(target.name);
			for (MethodNode m : methods) {
				Integer found = argumentSlot(m, names, type);
				MethodNode before = found == null && original != null ? PreflightAsm.declared(original, m.name, m.desc) : null;
				if (before != null) found = argumentSlot(before, names, type);
				if (found == null) found = onlyArgumentOfType(m, type);
				if (found == null || slot != null && !slot.equals(found)) return List.of();
				slot = found;
			}
		}
		if (slot == null) return List.of();
		removeValue(injector, "name");
		Annotations.put(injector, "index", slot);
		return List.of("the argument " + names.get(0) + " by its name, which " + loader + "'s Mixin doesn't know; it takes the argument's slot, "
				+ slot + ", instead");
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

	/** The slot of the method's argument that its local variable table names one of {@code names}, of {@code type}; null when none. */
	private static Integer argumentSlot(MethodNode method, List<?> names, Type type) {
		if (method.localVariables == null) return null;
		int end = argumentsEnd(method);
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.index < end && names.contains(lv.name) && lv.desc.equals(type.getDescriptor()) && isArgumentSlot(method, lv.index)) return lv.index;
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
	private static boolean isVanillasLocal(MethodNode method, LocalVariableNode local, Set<Integer> vanillaPlaces) {
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

	/** Makes an {@code @Inject} whose local capture fails hard skip itself instead; returns whether it changed. */
	static boolean softenLocalCapture(AnnotationNode injector) {
		if (!injector.desc.equals(MixinNames.INJECT) || !(Annotations.value(injector, "locals") instanceof String[] capture)
				|| !capture[1].equals("CAPTURE_FAILHARD") && !capture[1].equals("CAPTURE_FAILEXCEPTION")) return false;
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
	 * {@code @Local(argsOnly = true)} of a type exactly one of {@code method}'s parameters has (other locals differ).
	 */
	static boolean localsFit(MethodNode handler, MethodNode method) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(MixinNames.LOCAL)) continue;
				Type local = types[i];
				if (!Boolean.TRUE.equals(Annotations.value(a, "argsOnly")) || local.getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")
						|| params.stream().filter(local::equals).count() != 1) return false;
			}
		}
		return true;
	}
}
