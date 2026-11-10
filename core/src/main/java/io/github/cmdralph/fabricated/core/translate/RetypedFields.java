package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.PlatformRetypes;
import io.github.cmdralph.fabricated.core.bytecode.PlatformRetypes.Retype;
import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight;
import io.github.cmdralph.fabricated.core.mixin.Refmap;

/**
 * Game fields a platform gives a subtype of vanilla's type ({@link PlatformRetypes}), which Fabric mods reach with
 * vanilla's (CORPUS-33: Forge 61 to 66's {@code ChunkGenerator.featuresPerStep}). A mod's classes are made to reach the platform's field: a read takes its value as it is (a
 * subtype of what the mod expects); a write gives the field the mod's value made into the platform's type, as the
 * platform makes its own ({@code ClearableLazy.concurrentOf}). The mod's mixins into the field's class are adapted too:
 * an {@code @Accessor} of the field, and every call of it in the mod, take the platform's type (a setter's value made
 * into it as a write's is), its entries in the mod's refmaps name it, and a {@code @Shadow} of the field has it.
 *
 * <p>Another mod's calls of the accessor, which this mod's translation doesn't see, keep vanilla's type: the accessor
 * of a mixin with one target keeps a method of vanilla's name and descriptor, which Mixin merges into the target with the
 * mixin's others, calling the adapted one there (a setter's value made into the platform's type), so they reach the field
 * through it (ACC-6: Wover's generator module calls its biome module's accessor). Each mod is translated on its own.
 *
 * <p>Left as they are, which pre-flight then reports as before: an accessor setter a method reference names (its
 * lambda would pass vanilla's type), and injectors whose point names the field (their handlers take vanilla's type).
 */
final class RetypedFields {
	private static final String MIXIN = Annotations.MIXIN;
	private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

	private final ClassIndex game;
	private final List<Retype> active;

	private RetypedFields(ClassIndex game, List<Retype> active) {
		this.game = game;
		this.active = active;
	}

	/** The retyped fields of the game's classes: the platform's type there, and none of vanilla's. */
	static RetypedFields of(ClassIndex game) {
		List<Retype> active = new ArrayList<>();
		for (Retype r : PlatformRetypes.KNOWN) {
			if (PlatformRetypes.of(game, r.owner(), r.name(), r.vanillaDesc()) != null) active.add(r);
		}
		return new RetypedFields(game, List.copyOf(active));
	}

	boolean isEmpty() {
		return active.isEmpty();
	}

	/** An accessor of a retyped field: its interface, name and descriptor, whether it sets the field, and the field. */
	private record AccessorKey(String owner, String name, String desc) {
	}

	/** {@code target}: the mixin's one target, which the method of vanilla's descriptor calls the accessor on; null for none. */
	private record Accessor(Retype field, boolean setter, String selector, String target) {
		String newDesc() {
			return setter ? "(" + field.platformDesc() + ")V" : "()" + field.platformDesc();
		}
	}

	/** What one mod's classes need: its accessors and shadows of retyped fields, and its classes' superclasses. */
	private record Plan(Map<AccessorKey, Accessor> accessors, Map<String, Retype> shadowsByMixin, Map<String, String> supers) {
	}

	/**
	 * Adapts a mod's classes and refmaps to the retyped fields, writing what changed into {@code target}. {@code read}
	 * reads an entry as the mod has it so far. Returns a note for each change made, for the report.
	 */
	List<String> apply(Map<String, byte[]> target, Iterable<String> entries, Set<String> refmapPaths, ModTranslator.FileView read)
			throws ArchiveException {
		if (active.isEmpty()) return List.of();
		List<String> names = new ArrayList<>();
		entries.forEach(names::add);
		Function<String, byte[]> current = name -> {
			try {
				return target.containsKey(name) ? target.get(name) : read.read(name);
			} catch (ArchiveException e) {
				return null;
			}
		};
		// Only classes naming a retyped field's name can reach it or declare an accessor of it.
		List<String> candidates = new ArrayList<>();
		Map<String, String> supers = new HashMap<>();
		for (String name : names) {
			if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
			byte[] data = current.apply(name);
			if (data == null) continue;
			ClassReader reader;
			try {
				reader = new ClassReader(data);
			} catch (RuntimeException e) {
				continue;
			}
			if (reader.getSuperName() != null) supers.put(reader.getClassName(), reader.getSuperName());
			if (active.stream().anyMatch(r -> Annotations.mentions(data, r.name().getBytes(StandardCharsets.UTF_8)))) candidates.add(name);
		}
		if (candidates.isEmpty()) return List.of();

		Map<String, Refmap> refmaps = new LinkedHashMap<>();
		for (String path : refmapPaths) {
			byte[] data = current.apply(path);
			if (data == null) continue;
			try {
				refmaps.put(path, Refmap.parse(new String(data, StandardCharsets.UTF_8)));
			} catch (JsonException ignored) {
				// The mixins' processing reports a refmap it can't read.
			}
		}

		Map<AccessorKey, Accessor> accessors = new HashMap<>();
		Map<String, Retype> shadows = new HashMap<>();
		List<String> notes = new ArrayList<>();
		for (String name : candidates) {
			ClassNode node = new ClassNode();
			try {
				new ClassReader(current.apply(name)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			} catch (RuntimeException e) {
				continue;
			}
			AnnotationNode mixin = Annotations.find(node.invisibleAnnotations, node.visibleAnnotations, MIXIN);
			if (mixin == null) continue;
			List<String> targets = mixinTargets(mixin);
			for (Retype r : active) {
				if (targets.stream().noneMatch(t -> reaches(t, r.owner(), supers))) continue;
				for (FieldNode f : node.fields) {
					if (f.name.equals(r.name()) && f.desc.equals(r.vanillaDesc()) && Annotations.find(f, SHADOW) != null) shadows.put(node.name, r);
				}
				if ((node.access & Opcodes.ACC_INTERFACE) == 0) continue;
				for (MethodNode m : node.methods) {
					AnnotationNode accessor = Annotations.find(m, ACCESSOR);
					if (accessor == null || (m.access & Opcodes.ACC_STATIC) != 0) continue;
					String desc = MixinPreflight.accessorFieldDesc(m);
					if (!r.vanillaDesc().equals(desc)) continue;
					String explicit = Annotations.value(accessor, "value") instanceof String s && !s.isEmpty() ? s : null;
					String key = explicit != null ? explicit : MixinPreflight.inferAccessorName(m.name);
					if (key == null || !r.name().equals(fieldName(resolve(refmaps, node.name, key)))) continue;
					accessors.put(new AccessorKey(node.name, m.name, m.desc), new Accessor(r, Type.getReturnType(m.desc).getSort() == Type.VOID, key,
							targets.size() == 1 ? targets.get(0) : null));
				}
			}
		}
		// Accessors are called from classes that don't name the field: every class naming an accessor's interface is adapted.
		List<String> callers = new ArrayList<>();
		if (!accessors.isEmpty()) {
			Set<String> owners = new HashSet<>();
			accessors.keySet().forEach(k -> owners.add(k.owner()));
			Set<String> done = new HashSet<>(candidates);
			for (String name : names) {
				if (!name.endsWith(".class") || name.startsWith("META-INF/") || done.contains(name)) continue;
				byte[] data = current.apply(name);
				if (data != null && owners.stream().anyMatch(o -> Annotations.mentions(data, o.getBytes(StandardCharsets.UTF_8)))) callers.add(name);
			}
		}
		// A setter a method reference names keeps vanilla's type (its lambda would pass that), and with it the mixin's
		// other accessors, which may share its refmap entry: pre-flight reports them as before.
		List<String> adapting = new ArrayList<>(candidates);
		adapting.addAll(callers);
		Set<String> kept = new HashSet<>();
		for (String name : adapting) {
			for (AccessorKey key : setterHandles(current.apply(name), accessors)) {
				if (kept.add(key.owner())) {
					notes.add("@Accessor " + key.owner().replace('/', '.') + "." + key.name() + key.desc() + " is named by a method reference, so the"
							+ " mixin's accessors keep vanilla's type of the field");
				}
			}
		}
		accessors.keySet().removeIf(key -> kept.contains(key.owner()));

		Plan plan = new Plan(accessors, shadows, supers);
		int classes = 0;
		for (String name : adapting) {
			byte[] adapted = adapt(current.apply(name), plan);
			if (adapted == null) continue;
			target.put(name, adapted);
			classes++;
		}
		for (String path : refmapPaths) {
			byte[] rewritten = rewriteRefmap(current.apply(path), accessors);
			if (rewritten != null) target.put(path, rewritten);
		}
		if (classes > 0) {
			for (Retype r : active) {
				notes.add(classes + " class(es) reach " + r.owner().replace('/', '.') + "." + r.name() + ", which this platform types "
						+ Type.getType(r.platformDesc()).getClassName() + " where vanilla has " + Type.getType(r.vanillaDesc()).getClassName()
						+ "; they reach it with the platform's type" + (accessors.isEmpty() ? "" : " (" + accessors.size() + " accessor(s))"));
			}
		}
		return notes;
	}

	/** The classes a mixin names as its targets, internal names. */
	private static List<String> mixinTargets(AnnotationNode mixin) {
		List<String> targets = new ArrayList<>();
		if (Annotations.value(mixin, "value") instanceof List<?> types) {
			for (Object t : types) if (t instanceof Type type) targets.add(type.getInternalName());
		}
		if (Annotations.value(mixin, "targets") instanceof List<?> names) {
			for (Object t : names) if (t instanceof String s) targets.add(s.replace('.', '/'));
		}
		return targets;
	}

	/** An accessor's selector as the mod's refmaps resolve it for that mixin, else as written. */
	private static String resolve(Map<String, Refmap> refmaps, String mixin, String key) {
		for (Refmap refmap : refmaps.values()) {
			String resolved = refmap.resolve(mixin, key);
			if (!resolved.equals(key)) return resolved;
		}
		return key;
	}

	/** The field name in a selector: {@code name}, {@code name:desc} or {@code Lowner;name:desc}. */
	private static String fieldName(String selector) {
		String s = selector;
		int semi = s.indexOf(';');
		if (s.startsWith("L") && semi > 0) s = s.substring(semi + 1);
		int colon = s.indexOf(':');
		return colon >= 0 ? s.substring(0, colon) : s;
	}

	/** Whether {@code owner} is {@code wanted} or a subclass of it, through the mod's classes and the game's. */
	private boolean reaches(String owner, String wanted, Map<String, String> supers) {
		Set<String> seen = new HashSet<>();
		String current = owner;
		while (current != null && seen.add(current)) {
			if (current.equals(wanted)) return true;
			String next = supers.get(current);
			if (next == null) {
				ClassIndex.ClassInfo info = game.get(current);
				next = info == null ? null : info.superName();
			}
			current = next;
		}
		return false;
	}

	/** The retyped field a field instruction names, through its owner; null when none. */
	private Retype fieldOf(String owner, String name, String desc, Plan plan) {
		for (Retype r : active) {
			if (!r.name().equals(name) || !r.vanillaDesc().equals(desc)) continue;
			if (r.equals(plan.shadowsByMixin().get(owner)) || reaches(owner, r.owner(), plan.supers())) return r;
		}
		return null;
	}

	/** The accessor setters of the plan that a class's method references (invokedynamic handles) name. */
	private static Set<AccessorKey> setterHandles(byte[] data, Map<AccessorKey, Accessor> accessors) {
		Set<AccessorKey> found = new HashSet<>();
		if (data == null || accessors.values().stream().noneMatch(Accessor::setter)) return found;
		new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitInvokeDynamicInsn(String n, String d, Handle bootstrap, Object... args) {
						for (Object arg : args) {
							if (!(arg instanceof Handle h)) continue;
							AccessorKey key = new AccessorKey(h.getOwner(), h.getName(), h.getDesc());
							Accessor a = accessors.get(key);
							if (a != null && a.setter()) found.add(key);
						}
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return found;
	}

	/** One class adapted to the plan, or null when nothing in it changes. */
	private byte[] adapt(byte[] data, Plan plan) {
		if (data == null) return null;
		ClassReader reader = new ClassReader(data);
		if ((reader.readUnsignedShort(6) & 0xFFFF) < Opcodes.V1_8) return null; // a static interface call needs Java 8
		boolean[] changed = {false};
		ClassWriter writer = new ClassWriter(reader, 0);
		ClassVisitor adapter = new ClassVisitor(Opcodes.ASM9, writer) {
			private String className;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				className = name;
				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public void visitEnd() {
				// Vanilla's descriptor of each accessor of this mixin, for other mods' calls: the adapted accessor, called on the
				// target class rather than the mixin, as Mixin points a merged method's calls of its mixin at the target and
				// would leave them interface calls.
				plan.accessors().forEach((key, a) -> {
					if (!key.owner().equals(className) || a.target() == null) return;
					MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, key.name(), key.desc(), null, null);
					mv.visitCode();
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitTypeInsn(Opcodes.CHECKCAST, a.target());
					if (a.setter()) {
						mv.visitVarInsn(Opcodes.ALOAD, 1);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, a.field().wrapOwner(), a.field().wrapName(), a.field().wrapDesc(), true);
					}
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, a.target(), key.name(), a.newDesc(), false);
					mv.visitInsn(a.setter() ? Opcodes.RETURN : Opcodes.ARETURN);
					mv.visitMaxs(a.setter() ? 2 : 1, a.setter() ? 2 : 1);
					mv.visitEnd();
				});
				super.visitEnd();
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				Retype shadow = plan.shadowsByMixin().get(className);
				if (shadow != null && shadow.name().equals(name) && shadow.vanillaDesc().equals(descriptor)) {
					changed[0] = true;
					return super.visitField(access, name, shadow.platformDesc(), null, value);
				}
				return super.visitField(access, name, descriptor, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				Accessor own = plan.accessors().get(new AccessorKey(className, name, descriptor));
				MethodVisitor mv = own != null
						? super.visitMethod(access, name, own.newDesc(), null, exceptions)
						: super.visitMethod(access, name, descriptor, signature, exceptions);
				if (own != null) changed[0] = true;
				return new MethodVisitor(Opcodes.ASM9, mv) {
					@Override
					public void visitFieldInsn(int opcode, String owner, String field, String desc) {
						Retype r = fieldOf(owner, field, desc, plan);
						if (r == null) {
							super.visitFieldInsn(opcode, owner, field, desc);
							return;
						}
						changed[0] = true;
						if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) wrap(r);
						super.visitFieldInsn(opcode, owner, field, r.platformDesc());
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
						Accessor a = plan.accessors().get(new AccessorKey(owner, method, desc));
						if (a == null) {
							super.visitMethodInsn(opcode, owner, method, desc, itf);
							return;
						}
						changed[0] = true;
						if (a.setter()) wrap(a.field());
						super.visitMethodInsn(opcode, owner, method, a.newDesc(), itf);
					}

					@Override
					public void visitInvokeDynamicInsn(String n, String d, Handle bootstrap, Object... args) {
						Object[] adapted = args.clone();
						for (int i = 0; i < adapted.length; i++) {
							if (!(adapted[i] instanceof Handle h)) continue;
							Accessor a = plan.accessors().get(new AccessorKey(h.getOwner(), h.getName(), h.getDesc()));
							// Getters only (setters named by a handle were left out): the lambda takes the subtype as vanilla's type.
							if (a == null || a.setter()) continue;
							adapted[i] = new Handle(h.getTag(), h.getOwner(), h.getName(), a.newDesc(), h.isInterface());
							changed[0] = true;
						}
						super.visitInvokeDynamicInsn(n, d, bootstrap, adapted);
					}

					private void wrap(Retype r) {
						super.visitMethodInsn(Opcodes.INVOKESTATIC, r.wrapOwner(), r.wrapName(), r.wrapDesc(), true);
					}
				};
			}
		};
		reader.accept(adapter, 0);
		return changed[0] ? writer.toByteArray() : null;
	}

	/**
	 * A refmap with the entries of the adapted accessors naming the platform's type, or null when it has none. Only an
	 * accessor's own entries change: an injection point naming the field keeps vanilla's type, as its handler does.
	 */
	@SuppressWarnings("unchecked")
	private static byte[] rewriteRefmap(byte[] data, Map<AccessorKey, Accessor> accessors) {
		if (data == null || accessors.isEmpty()) return null;
		Map<String, Map<String, Retype>> byMixin = new HashMap<>();
		accessors.forEach((key, a) -> byMixin.computeIfAbsent(key.owner(), k -> new HashMap<>()).put(a.selector(), a.field()));
		Object tree;
		try {
			tree = Json.parse(new String(data, StandardCharsets.UTF_8), Refmap.MAX_LENGTH, Json.DEFAULT_MAX_DEPTH);
		} catch (JsonException e) {
			return null;
		}
		if (!(tree instanceof Map<?, ?> root)) return null;
		boolean changed = false;
		List<Map<String, Object>> sections = new ArrayList<>();
		if (root.get("mappings") instanceof Map<?, ?> m) sections.add((Map<String, Object>) m);
		if (root.get("data") instanceof Map<?, ?> d) {
			for (Object env : d.values()) if (env instanceof Map<?, ?> e) sections.add((Map<String, Object>) e);
		}
		for (Map<String, Object> section : sections) {
			for (Map.Entry<String, Object> cls : section.entrySet()) {
				Map<String, Retype> selectors = byMixin.get(cls.getKey().replace('.', '/'));
				if (selectors == null || !(cls.getValue() instanceof Map<?, ?> entries)) continue;
				for (Map.Entry<String, Object> e : ((Map<String, Object>) entries).entrySet()) {
					Retype r = selectors.get(e.getKey());
					if (r == null || !(e.getValue() instanceof String value)) continue;
					String bare = r.name() + ":" + r.vanillaDesc();
					String owned = "L" + r.owner() + ";" + bare;
					if (value.equals(bare) || value.equals(owned)) {
						e.setValue(value.substring(0, value.length() - r.vanillaDesc().length()) + r.platformDesc());
						changed = true;
					}
				}
			}
		}
		return changed ? Json.write(tree, false).getBytes(StandardCharsets.UTF_8) : null;
	}
}
