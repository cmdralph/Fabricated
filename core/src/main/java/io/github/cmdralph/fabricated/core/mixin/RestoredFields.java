package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * The game's classes as pre-flight should see them: with the fields Fabricated's own mixins add, which Fabric mods'
 * mixins then shadow or access as the game's. Fabricated restores vanilla's map of picture-in-picture renderers on
 * {@code GuiRenderer}, which NeoForge replaced with renderer pools and Fabric mods read and replace (malilib, PIP-1). A
 * field of one of Fabricated's mixins counts when it is neither shadowed nor unique (named for Fabricated alone). So do
 * the methods they add for a field the platform gave another type or replaced ({@link MixinNames#BRIDGE_GETTER}),
 * which a Fabric mod's accessor of the field invokes (CORPUS-37, CORPUS-94), and its own reads of the field call
 * (CORPUS-107), and the seams they add for a method the platform never calls ({@link MixinNames#SEAM_START},
 * {@link MixinNames#SEAM_END}), which Fabric mods' hooks into that method are pointed at (HUD-4); pre-flight reads their
 * declarations, not their code. A seam for a read the platform's constructor never makes ({@link MixinNames#SEAM_READ},
 * HUD-7) keeps its code, its mixin's name made the game class's, as Mixin merges it: pre-flight finds the read and the
 * locals there that Fabric mods' hooks at it capture. Each carries Mixin's mark of a merged method, with its mixin's
 * priority. Each read of a class makes them anew: mods are translated in parallel.
 */
public final class RestoredFields {
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";

	/** By the game class's internal name. */
	private final Map<String, Added> added;

	private RestoredFields(Map<String, Added> added) {
		this.added = added;
	}

	/**
	 * @param fabricatedFiles Fabricated's in-game mod (jars or directories), whose root {@code *.mixins.json} configs list
	 *                        its mixins
	 */
	public static RestoredFields of(List<Path> fabricatedFiles) {
		Map<String, Added> added = new HashMap<>();
		for (Path file : fabricatedFiles) {
			try {
				collect(file, added);
			} catch (IOException | JsonException | RuntimeException e) {
				// Unreadable: pre-flight sees the game's classes as they are, as before.
			}
		}
		return new RestoredFields(Map.copyOf(added));
	}

	/**
	 * The fields a bridge getter serves ({@link MixinNames#BRIDGE_GETTER}), as {@code owner field} in the game's runtime
	 * names to the getter's name: a Fabric mod's own reads of them call it (CORPUS-107), as its accessors do.
	 */
	public Map<String, String> bridgedFields() {
		Map<String, String> fields = new TreeMap<>();
		added.forEach((owner, more) -> {
			for (AddedMethod method : more.methods()) {
				if (method.name().startsWith(MixinNames.BRIDGE_GETTER) && method.desc().startsWith("()")) {
					fields.put(owner + " " + method.name().substring(MixinNames.BRIDGE_GETTER.length()), method.name());
				}
			}
		});
		return fields;
	}

	/** The game's classes with what Fabricated's mixins add to them. */
	public ClassBytesSource over(ClassBytesSource game) {
		if (added.isEmpty()) return game;
		return name -> {
			byte[] data = game.read(name);
			Added more = added.get(name);
			if (data == null || more == null) return data;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, 0);
			for (FieldNode field : more.fields()) {
				if (node.fields.stream().noneMatch(f -> f.name.equals(field.name))) {
					node.fields.add(new FieldNode(field.access, field.name, field.desc, field.signature, null));
				}
			}
			for (AddedMethod method : more.methods()) {
				if (node.methods.stream().noneMatch(m -> m.name.equals(method.name()) && m.desc.equals(method.desc()))) node.methods.add(method.merged(name));
			}
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			return writer.toByteArray();
		};
	}

	/** What Fabricated's mixins add to one game class. */
	private record Added(List<FieldNode> fields, List<AddedMethod> methods) {
	}

	/**
	 * A method a mixin of Fabricated's, of that priority, adds to a game class; {@code mixinClass} is the mixin's class
	 * file where pre-flight reads the method's code (a read seam), else null.
	 */
	private record AddedMethod(int access, String name, String desc, String signature, String mixin, int priority, byte[] mixinClass) {
		/** The method as Mixin merges it into {@code target}, made anew, with Mixin's mark of a merged method. */
		MethodNode merged(String target) {
			MethodNode merged = mixinClass == null ? declaration(access, name, desc, signature) : withCode(target);
			AnnotationNode mark = new AnnotationNode(MixinNames.MIXIN_MERGED);
			mark.values = new ArrayList<>(List.of("mixin", mixin.replace('/', '.'), "priority", priority));
			if (merged.visibleAnnotations == null) merged.visibleAnnotations = new ArrayList<>();
			merged.visibleAnnotations.add(mark);
			return merged;
		}

		/** The method with its code, parsed from the mixin's class file, the mixin's name in it made the target's. */
		private MethodNode withCode(String target) {
			ClassNode node = new ClassNode();
			new ClassReader(mixinClass).accept(node, 0);
			MethodNode method = PreflightAsm.declared(node, name, desc);
			MethodNode copy = new MethodNode(access, name, desc, signature, method.exceptions == null ? null : method.exceptions.toArray(String[]::new));
			method.accept(new MethodRemapper(copy, new SimpleRemapper(mixin, target)));
			return copy;
		}
	}

	/** A bridge as pre-flight reads it: its declaration, with a body returning its type's default value. */
	private static MethodNode declaration(int access, String name, String desc, String signature) {
		MethodNode copy = new MethodNode(access & ~Opcodes.ACC_ABSTRACT, name, desc, signature, null);
		Type returned = Type.getReturnType(desc);
		switch (returned.getSort()) {
			case Type.VOID -> copy.visitInsn(Opcodes.RETURN);
			case Type.OBJECT, Type.ARRAY -> {
				copy.visitInsn(Opcodes.ACONST_NULL);
				copy.visitInsn(Opcodes.ARETURN);
			}
			case Type.LONG -> {
				copy.visitInsn(Opcodes.LCONST_0);
				copy.visitInsn(Opcodes.LRETURN);
			}
			case Type.FLOAT -> {
				copy.visitInsn(Opcodes.FCONST_0);
				copy.visitInsn(Opcodes.FRETURN);
			}
			case Type.DOUBLE -> {
				copy.visitInsn(Opcodes.DCONST_0);
				copy.visitInsn(Opcodes.DRETURN);
			}
			default -> {
				copy.visitInsn(Opcodes.ICONST_0);
				copy.visitInsn(Opcodes.IRETURN);
			}
		}
		copy.visitMaxs(2, 1 + (Type.getArgumentsAndReturnSizes(desc) >> 2));
		return copy;
	}

	private static void collect(Path file, Map<String, Added> added) throws IOException, JsonException {
		if (Files.isDirectory(file)) {
			List<Path> configs;
			try (Stream<Path> listed = Files.list(file)) {
				configs = listed.filter(p -> p.getFileName().toString().endsWith(".mixins.json")).toList();
			}
			for (Path config : configs) {
				collect(Files.readString(config), name -> {
					Path path = file.resolve(name);
					return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
				}, added);
			}
		} else if (Files.isRegularFile(file)) {
			try (ZipFile zip = new ZipFile(file.toFile())) {
				List<? extends ZipEntry> configs = zip.stream().filter(e -> !e.getName().contains("/") && e.getName().endsWith(".mixins.json")).toList();
				EntryReader entries = name -> {
					ZipEntry entry = zip.getEntry(name);
					if (entry == null) return null;
					try (var in = zip.getInputStream(entry)) {
						return in.readAllBytes();
					}
				};
				for (ZipEntry config : configs) collect(new String(entries.read(config.getName()), StandardCharsets.UTF_8), entries, added);
			}
		}
	}

	/** Reads one of the in-game mod's files by its path, or null when it has none there. */
	private interface EntryReader {
		byte[] read(String name) throws IOException;
	}

	private static void collect(String json, EntryReader files, Map<String, Added> added) throws IOException, JsonException {
		MixinConfigFile config = MixinConfigFile.parse(json);
		for (String mixin : config.allMixins()) {
			byte[] data = files.read(config.internalName(mixin) + ".class");
			if (data == null) continue;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, ClassReader.SKIP_CODE);
			List<FieldNode> fields = new ArrayList<>();
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_SYNTHETIC) == 0 && Annotations.find(field, SHADOW) == null && Annotations.find(field, UNIQUE) == null) {
					fields.add(field);
				}
			}
			int priority = PreflightAsm.priority(node, config);
			List<AddedMethod> methods = new ArrayList<>();
			for (MethodNode method : node.methods) {
				boolean bridge = method.name.startsWith(MixinNames.BRIDGE_GETTER) || method.name.startsWith(MixinNames.BRIDGE_SETTER)
						|| method.name.startsWith(MixinNames.SEAM_START) || method.name.startsWith(MixinNames.SEAM_END)
						|| method.name.startsWith(MixinNames.SEAM_READ);
				if (bridge && (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)) == 0 && (method.access & Opcodes.ACC_PUBLIC) != 0
						&& Annotations.find(method, SHADOW) == null) {
					methods.add(new AddedMethod(method.access, method.name, method.desc, method.signature, node.name, priority,
							method.name.startsWith(MixinNames.SEAM_READ) ? data : null));
				}
			}
			if (fields.isEmpty() && methods.isEmpty()) continue;
			AnnotationNode ann = Annotations.find(node.invisibleAnnotations, node.visibleAnnotations, Annotations.MIXIN);
			if (ann == null || !(Annotations.value(ann, "value") instanceof List<?> targets)) continue;
			for (Object target : targets) {
				if (!(target instanceof Type type)) continue;
				Added into = added.computeIfAbsent(type.getInternalName(), k -> new Added(new ArrayList<>(), new ArrayList<>()));
				into.fields().addAll(fields);
				into.methods().addAll(methods);
			}
		}
	}
}
