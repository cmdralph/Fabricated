package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * Members NeoForge renamed that Fabric mods still reach by their old names, through the copies of NeoForge's API made
 * for Fabric: Forge Config API Port keeps FML's old config types ({@code COMMON}, {@code SERVER}), which FML 12 calls
 * {@code LOCAL} and {@code SYNCED}. A mod's references are renamed where the running platform has only the new name.
 */
final class PlatformRenames {
	/** Owner, then old name to new name. */
	private static final Map<String, Map<String, String>> KNOWN = Map.of(
			"net/neoforged/fml/config/ModConfig$Type", Map.of("COMMON", "LOCAL", "SERVER", "SYNCED"));

	private final Map<String, Map<String, String>> active;

	private PlatformRenames(Map<String, Map<String, String>> active) {
		this.active = active;
	}

	/** The renames the platform's classes call for: an old field missing there, its new one present. */
	static PlatformRenames of(ClassBytesSource platform) {
		Map<String, Map<String, String>> active = new HashMap<>();
		KNOWN.forEach((owner, renames) -> {
			ClassNode node = read(platform, owner);
			if (node == null) return;
			Map<String, String> applying = new HashMap<>();
			renames.forEach((from, to) -> {
				if (!hasField(node, from) && hasField(node, to)) applying.put(from, to);
			});
			if (!applying.isEmpty()) active.put(owner, Map.copyOf(applying));
		});
		return new PlatformRenames(Map.copyOf(active));
	}

	boolean isEmpty() {
		return active.isEmpty();
	}

	/** {@code classFile} with the renames applied, or null when it uses none of the renamed members. */
	byte[] apply(byte[] classFile) {
		if (active.isEmpty() || !mentionsOwner(classFile)) return null;
		boolean[] renamed = {false};
		ClassReader reader = new ClassReader(classFile);
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassRemapper(writer, new Remapper() {
			@Override
			public String mapFieldName(String owner, String name, String descriptor) {
				String to = active.getOrDefault(owner, Map.of()).get(name);
				if (to == null) return name;
				renamed[0] = true;
				return to;
			}
		}), 0);
		return renamed[0] ? writer.toByteArray() : null;
	}

	private boolean mentionsOwner(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		return active.keySet().stream().anyMatch(text::contains);
	}

	private static ClassNode read(ClassBytesSource platform, String owner) {
		try {
			byte[] data = platform.read(owner);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, ClassReader.SKIP_CODE);
			return node;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static boolean hasField(ClassNode node, String name) {
		return node.fields.stream().map((FieldNode f) -> f.name).anyMatch(name::equals);
	}
}
