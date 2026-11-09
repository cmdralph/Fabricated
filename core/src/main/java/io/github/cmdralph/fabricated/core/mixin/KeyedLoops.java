package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

/**
 * A Fabric mod's {@code @Redirect} of a read of a static list its target method loops over, where the platform loops
 * over the keys of a map it orders by that list instead, its own keys among them (IRIS-1): Forge 47's
 * {@code ParticleEngine.render} goes over the render types of the particles it holds, Forge mods' own after vanilla's,
 * where vanilla's goes over {@code RENDER_ORDER}, which Iris redirects to pick the types each of its passes renders.
 *
 * <p>Pre-flight points such a redirect at the map's {@code keySet()} call ({@link #repairs}), through a bridge that has
 * the handler's own reads of the list see the list's elements followed by the map's other keys ({@link #keys}) and
 * returns what the handler returns, in its order, as the keys to go over ({@link #inOrder}). The handler so picks among
 * the platform's keys as it picks among the list's on Fabric, as if the list held them: one that copies the list and
 * takes elements out keeps the platform's own, one that returns a list of its own leaves them out. The platform's loop
 * finds nothing in the map for a key the map doesn't hold, as vanilla's does. The bridge and the handler call this
 * class's public methods as the game runs; it is in Fabricated's jar, beside the loader's classes the game's code calls
 * ({@code EarlyHandlers}).
 */
public final class KeyedLoops {
	static final String OWNER = "io/github/cmdralph/fabricated/core/mixin/KeyedLoops";
	/** The static list types a loop goes over that a {@code List} can stand for. */
	private static final Set<String> LISTS = Set.of("Ljava/util/List;", "Ljava/util/Collection;", "Ljava/lang/Iterable;");
	private static final String KEY_SET = "()Ljava/util/Set;";

	private KeyedLoops() {
	}

	/** {@code list}'s elements, then {@code keys}' others, each in order: what a handler's reads of the list see. */
	public static List<Object> keys(Iterable<?> list, Set<?> keys) {
		List<Object> all = new ArrayList<>();
		list.forEach(all::add);
		for (Object key : keys) {
			if (!all.contains(key)) all.add(key);
		}
		return Collections.unmodifiableList(all);
	}

	/** What a handler's read of the list gives: the keys a bridge calling it set, or the list where none is calling it. */
	public static Object seen(Object keys, Object list) {
		return keys != null ? keys : list;
	}

	/** The keys a handler picked, once each in its order, for the platform's loop over a map's key set. */
	public static Set<Object> inOrder(Iterable<?> picked) {
		Set<Object> keys = new LinkedHashSet<>();
		picked.forEach(keys::add);
		return Collections.unmodifiableSet(keys);
	}

	/**
	 * Points a {@code @Redirect} of a read of a static list ({@code GETSTATIC}) of its target that the method it names
	 * doesn't make, nor the overload of the same name that method calls ({@link OverloadRepairs#calledOverload}) or the
	 * method the platform deprecated it for ({@link OverloadRepairs#replacementOf}: Forge 47 runs vanilla's code under
	 * its SRG names, so its overload's name isn't vanilla's method's there, and Forge 52's vanilla method calls itself
	 * instead of it), at the one call there to {@code keySet()}
	 * of a map field of the target that the platform's code goes over (its result's {@code iterator()} called at once)
	 * and fills, in a constructor, with a map made from the list (the list read and that field the next one stored):
	 * Forge 47's {@code ParticleEngine.render} and its particles' map, sorted by vanilla's render order with Forge's
	 * types after. The handler ({@code ()} to the list's type, not static) is called from a bridge taking the map, as a
	 * redirect of that call is ({@link #bridge}); its reads of the list, the field itself or its {@code @Shadow}, read
	 * a field of the mixin's while the bridge calls it ({@link #seen}). Returns a description of the move, or nothing.
	 */
	static List<String> repairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx, List<MethodNode> bridges) {
		if (!injector.desc.equals(MixinNames.REDIRECT) || (handler.access & Opcodes.ACC_STATIC) != 0 || (mixin.access & Opcodes.ACC_INTERFACE) != 0
				|| !Annotations.list(Annotations.value(injector, "slice")).isEmpty()
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		Object opcode = Annotations.value(at, "opcode");
		if (!"FIELD".equals(Annotations.value(at, "value")) || !(Annotations.value(at, "target") instanceof String fieldRaw)
				|| Annotations.value(at, "ordinal") != null || Annotations.value(at, "slice") != null
				|| opcode != null && !Integer.valueOf(Opcodes.GETSTATIC).equals(opcode)) return List.of();
		MemberRef read = MemberRef.parse(ctx.resolve(fieldRaw));
		if (read.fuzzy() || !target.name.equals(read.owner()) || read.name() == null || read.desc() == null) return List.of();
		FieldNode list = target.fields.stream().filter(f -> f.name.equals(read.name()) && f.desc.equals(read.desc())).findFirst().orElse(null);
		if (list == null || (list.access & Opcodes.ACC_STATIC) == 0 || !LISTS.contains(list.desc) || !handler.desc.equals("()" + list.desc)) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null) return List.of();
		MethodNode chosen = target.methods.stream().filter(m -> ref.matchesMethod(m.name, m.desc)).findFirst().orElse(null);
		if (chosen == null || (chosen.access & Opcodes.ACC_STATIC) != 0 || reads(chosen, target, list)) return List.of();
		MethodNode method = chosen;
		MethodInsnNode keySet = keySetLoop(method, target, list);
		if (keySet == null) {
			method = OverloadRepairs.calledOverload(chosen, target);
			if (method == null) method = OverloadRepairs.replacementOf(chosen, target);
			if (method == null || (method.access & Opcodes.ACC_STATIC) != 0 || reads(method, target, list)) return List.of();
			keySet = keySetLoop(method, target, list);
			if (keySet == null) return List.of();
		}
		FieldInsnNode map = (FieldInsnNode) previous(keySet);
		FieldNode keys = keysField(mixin, handler, list);
		int rewritten = seeKeys(handler, mixin, target, list, keys);
		bridges.add(bridge(mixin.name, target.name, handler, injector, list, keySet, keys, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, method.name + method.desc);
		Annotations.put(at, "value", "INVOKE");
		Annotations.put(at, "target", "L" + keySet.owner + ";keySet" + KEY_SET);
		if (opcode != null) at.values.subList(at.values.indexOf("opcode"), at.values.indexOf("opcode") + 2).clear();
		String field = target.name.replace('/', '.') + "." + list.name;
		return List.of(field + " in " + chosen.name + chosen.desc + ", where " + ctx.loader() + " goes over the keys of its map " + map.name
				+ (method == chosen ? "" : " in " + method.name + method.desc) + " instead, which it orders by that list and which holds keys of its own;"
				+ " the redirect is pointed at that loop, which goes over the keys the handler returns"
				+ (rewritten > 0 ? ", and the handler's reads of the list see the map's other keys after its elements" : ""));
	}

	/** Whether {@code method} reads {@code list}, a static field of {@code target}. */
	private static boolean reads(MethodNode method, ClassNode target, FieldNode list) {
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode get && get.getOpcode() == Opcodes.GETSTATIC && get.owner.equals(target.name)
					&& get.name.equals(list.name) && get.desc.equals(list.desc)) return true;
		}
		return false;
	}

	/**
	 * {@code method}'s one call to {@code keySet()}, when it is of a map field of {@code target}'s ({@code this.map}) that
	 * a constructor fills from {@code list} ({@link #filledFrom}), and its result is only gone over (its
	 * {@code iterator()} called at once); null otherwise.
	 */
	private static MethodInsnNode keySetLoop(MethodNode method, ClassNode target, FieldNode list) {
		MethodInsnNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.name.equals("keySet") || !call.desc.equals(KEY_SET)) continue;
			if (found != null) return null;
			found = call;
		}
		if (found == null || !(previous(found) instanceof FieldInsnNode map) || map.getOpcode() != Opcodes.GETFIELD || !map.owner.equals(target.name)
				|| !(previous(map) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0
				|| !(next(found) instanceof MethodInsnNode iterator) || !iterator.name.equals("iterator") || !iterator.desc.equals("()Ljava/util/Iterator;")
				|| !filledFrom(target, map.name, list)) return null;
		return found;
	}

	/** Whether a constructor of {@code target} stores in its field {@code map} what it makes after reading {@code list}. */
	private static boolean filledFrom(ClassNode target, String map, FieldNode list) {
		for (MethodNode constructor : target.methods) {
			if (!constructor.name.equals("<init>")) continue;
			boolean read = false;
			for (AbstractInsnNode insn : constructor.instructions) {
				if (!(insn instanceof FieldInsnNode field)) continue;
				if (field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(target.name) && field.name.equals(list.name)) {
					read = true;
				} else if (read && (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC)) {
					if (field.owner.equals(target.name) && field.name.equals(map)) return true;
					read = false;
				}
			}
		}
		return false;
	}

	private static AbstractInsnNode previous(AbstractInsnNode insn) {
		do insn = insn.getPrevious(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	private static AbstractInsnNode next(AbstractInsnNode insn) {
		do insn = insn.getNext(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	/** The mixin's new field holding the keys while the bridge calls {@code handler}: private, {@code @Unique}, of the list's type. */
	private static FieldNode keysField(ClassNode mixin, MethodNode handler, FieldNode list) {
		String base = "fabricated$" + handler.name + "$keys";
		String name = base;
		for (int i = 2; taken(mixin, name); i++) name = base + i;
		FieldNode field = new FieldNode(Opcodes.ACC_PRIVATE, name, list.desc, null, null);
		field.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MixinNames.UNIQUE)));
		mixin.fields.add(field);
		return field;
	}

	private static boolean taken(ClassNode mixin, String name) {
		return mixin.fields.stream().anyMatch(f -> f.name.equals(name));
	}

	/**
	 * Has each of {@code handler}'s reads of {@code list} (of the target's field, or of the mixin's {@code @Shadow} of it)
	 * give the keys the bridge set in {@code keys} instead, where it set them ({@link #seen}); returns how many.
	 */
	private static int seeKeys(MethodNode handler, ClassNode mixin, ClassNode target, FieldNode list, FieldNode keys) {
		int rewritten = 0;
		for (AbstractInsnNode insn : handler.instructions.toArray()) {
			if (!(insn instanceof FieldInsnNode get) || get.getOpcode() != Opcodes.GETSTATIC || !get.name.equals(list.name) || !get.desc.equals(list.desc)
					|| !get.owner.equals(target.name) && !get.owner.equals(mixin.name)) continue;
			InsnList before = new InsnList();
			before.add(new VarInsnNode(Opcodes.ALOAD, 0));
			before.add(new FieldInsnNode(Opcodes.GETFIELD, mixin.name, keys.name, keys.desc));
			handler.instructions.insertBefore(get, before);
			InsnList after = new InsnList();
			after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "seen", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
			after.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getType(list.desc).getInternalName()));
			handler.instructions.insert(get, after);
			rewritten++;
		}
		// The receiver and the keys below the list, where it pushed the list alone.
		if (rewritten > 0) handler.maxStack += 2;
		return rewritten;
	}

	/**
	 * A handler for {@code injector} at {@code keySet}: it takes the map, sets {@code keys} to the list's elements and
	 * the map's other keys ({@link #keys}), calls {@code handler}, clears {@code keys} and returns the handler's pick as
	 * a set in its order ({@link #inOrder}). The injector annotation moves to it; {@code handler} stays a plain method of
	 * the mixin.
	 */
	private static MethodNode bridge(String mixin, String target, MethodNode handler, AnnotationNode injector, FieldNode list, MethodInsnNode keySet,
			FieldNode keys, String name) {
		MethodNode bridge = HandlerBridges.bridgeMethod(handler, name, List.of(Type.getObjectType(keySet.owner)), Type.getType("Ljava/util/Set;"));
		InsnList code = bridge.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new FieldInsnNode(Opcodes.GETSTATIC, target, list.name, list.desc));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new MethodInsnNode(keySet.getOpcode(), keySet.owner, "keySet", KEY_SET, keySet.itf));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "keys", "(Ljava/lang/Iterable;Ljava/util/Set;)Ljava/util/List;", false));
		code.add(new FieldInsnNode(Opcodes.PUTFIELD, mixin, keys.name, keys.desc));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, mixin, handler.name, handler.desc, false));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new InsnNode(Opcodes.ACONST_NULL));
		code.add(new FieldInsnNode(Opcodes.PUTFIELD, mixin, keys.name, keys.desc));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "inOrder", "(Ljava/lang/Iterable;)Ljava/util/Set;", false));
		code.add(new InsnNode(Opcodes.ARETURN));
		bridge.maxStack = 3;
		bridge.maxLocals = 2;
		HandlerBridges.moveInjector(handler, injector, bridge);
		return bridge;
	}
}
