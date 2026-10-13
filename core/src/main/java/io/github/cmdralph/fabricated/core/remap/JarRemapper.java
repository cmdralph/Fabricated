package io.github.cmdralph.fabricated.core.remap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.mixin.Refmap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

/**
 * Produces the remapped contents of a Fabric jar. Pure bytecode processing: no class is ever loaded.
 *
 * <ul>
 *   <li>{@code .class} files are rewritten with {@link IntermediaryRemapper}; frames are copied, not
 *       recomputed, because renaming does not change stack shapes.</li>
 *   <li>Refmaps and service files have intermediary tokens rewritten.</li>
 *   <li>Signature files are removed (a remapped jar cannot keep valid signatures) and per-entry digest
 *       sections are dropped from the manifest.</li>
 *   <li>Everything else is copied unchanged.</li>
 * </ul>
 */
public final class JarRemapper {
	private final MappingTable table;
	private final TokenRemapper tokens;
	private final IntermediaryRemapper remapper;

	public JarRemapper(MappingTable table) {
		this.table = table;
		this.tokens = new TokenRemapper(table);
		this.remapper = new IntermediaryRemapper(table);
	}

	/** Result of remapping one jar. */
	public record Result(Map<String, byte[]> files, List<String> strippedSignatureFiles) {
	}

	/**
	 * @param refmaps paths of refmap JSON files inside the jar (from the mixin configs)
	 */
	public Result remap(SafeArchive archive, Set<String> refmaps) throws ArchiveException, MappingException {
		Map<String, byte[]> out = new LinkedHashMap<>();
		List<String> stripped = new java.util.ArrayList<>();
		for (String name : archive.entryNames()) {
			if (archive.isDirectory(name)) continue;
			if (isSignatureFile(name)) {
				stripped.add(name);
				continue;
			}
			byte[] data = archive.read(name);
			if (data == null) continue;
			if (name.endsWith(".class")) {
				byte[] remapped = remapClass(data, name);
				String newName = remapClassPath(name);
				out.put(newName, remapped);
			} else if (name.equals("META-INF/MANIFEST.MF")) {
				out.put(name, cleanManifest(data));
			} else if (name.startsWith("META-INF/services/")) {
				String service = name.substring("META-INF/services/".length());
				out.put("META-INF/services/" + tokens.remap(service), tokens.remap(new String(data, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
			} else if (refmaps.contains(name)) {
				out.put(name, remapRefmap(data, name));
			} else {
				out.put(name, data);
			}
		}
		return new Result(out, stripped);
	}

	public byte[] remapClass(byte[] data, String nameForErrors) throws MappingException {
		try {
			ClassReader reader = new ClassReader(data);
			ClassWriter writer = new ClassWriter(0);
			reader.accept(new ClassRemapper(writer, remapper), 0);
			return writer.toByteArray();
		} catch (RuntimeException e) {
			throw new MappingException("Failed to remap " + nameForErrors + ": " + e, e);
		}
	}

	private String remapClassPath(String name) {
		String internal = name.substring(0, name.length() - ".class".length());
		return table.mapClass(internal) + ".class";
	}

	/** Rewrites every string value of a refmap (keys are mod-side names and stay as they are). */
	public byte[] remapRefmap(byte[] data, String nameForErrors) throws MappingException {
		try {
			Object tree = Json.parse(new String(data, StandardCharsets.UTF_8), Refmap.MAX_LENGTH, Json.DEFAULT_MAX_DEPTH);
			return Json.write(remapJsonValues(tree), true).getBytes(StandardCharsets.UTF_8);
		} catch (JsonException e) {
			throw new MappingException("Refmap " + nameForErrors + " is not valid JSON: " + e.getMessage(), e);
		}
	}

	private Object remapJsonValues(Object node) {
		if (node instanceof String s) return tokens.remap(s);
		if (node instanceof Map<?, ?> map) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) copy.put(String.valueOf(e.getKey()), remapJsonValues(e.getValue()));
			return copy;
		}
		if (node instanceof List<?> list) {
			List<Object> copy = new java.util.ArrayList<>(list.size());
			for (Object o : list) copy.add(remapJsonValues(o));
			return copy;
		}
		return node;
	}

	static boolean isSignatureFile(String name) {
		if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) return false;
		String upper = name.toUpperCase(Locale.ROOT);
		return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
				|| upper.startsWith("META-INF/SIG-");
	}

	/** Keeps main attributes, drops per-entry sections (which carry now-invalid digests). */
	static byte[] cleanManifest(byte[] data) {
		try {
			Manifest original = new Manifest(new ByteArrayInputStream(data));
			Manifest clean = new Manifest();
			clean.getMainAttributes().putAll(original.getMainAttributes());
			if (clean.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) == null) {
				clean.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			clean.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			// A broken manifest is replaced by a minimal one rather than failing the whole mod.
			return "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
		}
	}
}
