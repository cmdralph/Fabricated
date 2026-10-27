package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * The game's classes as pre-flight should see them: with the fields Fabricated's own mixins add, which Fabric mods'
 * mixins then shadow or access as the game's. Fabricated restores vanilla's map of picture-in-picture renderers on
 * {@code GuiRenderer}, which NeoForge replaced with renderer pools and Fabric mods read and replace (malilib, PIP-1), and
 * on Forge vanilla's maps of colour providers by registry id, beside the platform's own of the same name (COLOR-1). A
 * field of one of Fabricated's mixins counts when it is neither shadowed nor unique (named for Fabricated alone).
 *
 * <p>A class has a field when it has one of that name and descriptor, as Mixin finds fields. SpongePowered's Mixin
 * looks for a shadowed field among the class's own fields only ({@code MixinCompat.shadowsMissAddedFields}), so there
 * the platform's loader declares these fields before Mixin runs ({@link #declare}).
 */
public final class RestoredFields {
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";

	/** A field one of Fabricated's mixins restores: its declaration, without its value (the mixin's initialiser sets it). */
	public record Field(int access, String name, String descriptor, String signature) {
	}

	private RestoredFields() {
	}

	/**
	 * The fields Fabricated's own mixins restore, by target class (internal names; immutable). An unreadable file adds none:
	 * pre-flight then sees the game's classes as they are.
	 *
	 * @param fabricatedFiles Fabricated's in-game mod (jars or directories), whose root {@code *.mixins.json} configs list
	 *                        its mixins
	 */
	public static Map<String, List<Field>> of(List<Path> fabricatedFiles) {
		Map<String, List<Field>> added = new HashMap<>();
		for (Path file : fabricatedFiles) {
			try {
				collect(file, added);
			} catch (IOException | JsonException | RuntimeException e) {
				// Unreadable: pre-flight sees the game's classes as they are, as before.
			}
		}
		added.replaceAll((target, fields) -> List.copyOf(fields));
		return Map.copyOf(added);
	}

	/** The game's classes with the {@code restored} fields each lacks ({@link #declare}). */
	public static ClassBytesSource over(ClassBytesSource game, Map<String, List<Field>> restored) {
		if (restored.isEmpty()) return game;
		return name -> {
			byte[] data = game.read(name);
			List<Field> fields = restored.get(name);
			if (data == null || fields == null) return data;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, 0);
			if (!declare(node, fields)) return data;
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			return writer.toByteArray();
		};
	}

	/** Declares each of {@code fields} that {@code node} has no field of that name and descriptor for; whether it changed. */
	public static boolean declare(ClassNode node, List<Field> fields) {
		boolean changed = false;
		for (Field field : fields) {
			if (node.fields.stream().anyMatch(f -> f.name.equals(field.name()) && f.desc.equals(field.descriptor()))) continue;
			node.fields.add(new FieldNode(field.access(), field.name(), field.descriptor(), field.signature(), null));
			changed = true;
		}
		return changed;
	}

	private static void collect(Path file, Map<String, List<Field>> added) throws IOException, JsonException {
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

	private static void collect(String json, EntryReader files, Map<String, List<Field>> added) throws IOException, JsonException {
		MixinConfigFile config = MixinConfigFile.parse(json);
		for (String mixin : config.allMixins()) {
			byte[] data = files.read(config.internalName(mixin) + ".class");
			if (data == null) continue;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, ClassReader.SKIP_CODE);
			List<Field> fields = new ArrayList<>();
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_SYNTHETIC) == 0 && Annotations.find(field, SHADOW) == null && Annotations.find(field, UNIQUE) == null) {
					fields.add(new Field(field.access, field.name, field.desc, field.signature));
				}
			}
			if (fields.isEmpty()) continue;
			AnnotationNode ann = Annotations.find(node.invisibleAnnotations, node.visibleAnnotations, Annotations.MIXIN);
			if (ann == null || !(Annotations.value(ann, "value") instanceof List<?> targets)) continue;
			for (Object target : targets) {
				if (target instanceof Type type) added.computeIfAbsent(type.getInternalName(), k -> new ArrayList<>()).addAll(fields);
			}
		}
	}
}
