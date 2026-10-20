package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;

/**
 * How other mods' NeoForge builds find what a Fabric mod offers them (data file {@code interop.json}, shipped with
 * core). On Fabric a mod offers its plugin for another mod, or its page in another mod's options, as an entrypoint.
 * The NeoForge builds of those mods look instead for an annotation on the class in NeoForge's scan data (JEI, EMI,
 * REI, Jade, Simple Voice Chat), which the loader adds, or for a mod property naming the class (Sodium), which the
 * translated mod's {@code mods.toml} gets. WTHIT needs nothing: on NeoForge it reads the jar's plugin file, which the
 * translated mod keeps. A convention of one library lives in the data, never as a branch on its name in code.
 *
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "entrypoints": {
 *     "jei_mod_plugin": { "host": "JEI", "annotation": "Lmezz/jei/api/JeiPlugin;" },
 *     "sodium:config_api_user": { "host": "Sodium", "property": "sodium:config_api_user" }
 *   }
 * }
 * }</pre>
 *
 * <p>The NeoForge builds only load the class by name and call its public no-argument constructor, which is what
 * Fabric's default language adapter does with a plain class name; any other entrypoint value is reported instead. A
 * property names one class: the first. Properties are part of the cached translation, so changing one needs a new
 * {@code TranslationEnvironment.TRANSLATION_SCHEMA}.
 */
public final class Interop {
	public static final int SCHEMA = 1;

	/**
	 * What one entrypoint key means to another mod's NeoForge build: exactly one of an annotation and a property.
	 *
	 * @param host       the mod that reads it, for the report
	 * @param annotation the annotation type its NeoForge build looks for on the class, e.g. {@code Lmezz/jei/api/JeiPlugin;}
	 * @param property   the mod property its NeoForge build reads the class name from
	 */
	record Convention(String host, String annotation, String property) {
	}

	/**
	 * An annotation to add to a mod's scan data.
	 *
	 * @param descriptor the annotation type, e.g. {@code Lmezz/jei/api/JeiPlugin;}
	 * @param className  the plugin class, as a binary name ({@code a.b.Outer$Inner})
	 * @param host       the mod that reads it, for the report
	 */
	public record Annotation(String descriptor, String className, String host) {
	}

	private static final Map<String, Convention> BUILTIN = builtin();

	private Interop() {
	}

	/**
	 * The annotations other mods' NeoForge builds find a mod's plugins by.
	 *
	 * @param entrypoints the mod's entrypoints, as Fabricated runs them (compatibility hints and skips applied)
	 * @param notes       receives a line for each plugin the NeoForge build of its host can't load
	 */
	public static List<Annotation> annotations(Map<String, List<EntrypointSpec>> entrypoints, List<String> notes) {
		List<Annotation> annotations = new ArrayList<>();
		for (Map.Entry<String, List<EntrypointSpec>> e : entrypoints.entrySet()) {
			Convention convention = BUILTIN.get(e.getKey());
			if (convention == null || convention.annotation() == null) continue;
			for (EntrypointSpec spec : e.getValue()) {
				if (plainClass(spec, convention, "plugin", notes)) {
					annotations.add(new Annotation(convention.annotation(), spec.value(), convention.host()));
				}
			}
		}
		return annotations;
	}

	/**
	 * The mod properties other mods' NeoForge builds read a mod's classes from, by property name.
	 *
	 * @param notes receives a line for each entrypoint left out
	 */
	static Map<String, String> properties(Map<String, List<EntrypointSpec>> entrypoints, List<String> notes) {
		Map<String, String> properties = new LinkedHashMap<>();
		for (Map.Entry<String, List<EntrypointSpec>> e : entrypoints.entrySet()) {
			Convention convention = BUILTIN.get(e.getKey());
			if (convention == null || convention.property() == null || e.getValue().isEmpty()) continue;
			EntrypointSpec first = e.getValue().getFirst();
			if (!plainClass(first, convention, "entrypoint", notes)) continue;
			properties.put(convention.property(), first.value());
			if (e.getValue().size() > 1) {
				notes.add(convention.host() + " for NeoForge takes one " + e.getKey() + " class per mod: " + first.value() + " is used, the others are left out");
			}
		}
		return properties;
	}

	private static boolean plainClass(EntrypointSpec spec, Convention convention, String what, List<String> notes) {
		if (spec.adapter().equals("default") && !spec.value().contains("::")) return true;
		notes.add("Its " + convention.host() + " " + what + " " + spec.value() + (spec.adapter().equals("default") ? "" : " (language adapter " + spec.adapter() + ")")
				+ " is left out: " + convention.host() + " for NeoForge only creates it from a class with a public no-argument constructor");
		return false;
	}

	private static Map<String, Convention> builtin() {
		try (InputStream in = Interop.class.getResourceAsStream("interop.json")) {
			if (in == null) throw new IllegalStateException("This Fabricated build is missing its interop.json");
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException | JsonException e) {
			throw new IllegalStateException("This Fabricated build's interop.json is broken: " + e.getMessage(), e);
		}
	}

	static Map<String, Convention> parse(String json) throws JsonException {
		Map<String, Object> root = Json.parseObject(json);
		if (!(root.get("schema") instanceof Number schema) || schema.intValue() != SCHEMA) throw new JsonException("schema must be " + SCHEMA);
		if (!(root.get("entrypoints") instanceof Map<?, ?> entrypoints)) throw new JsonException("entrypoints missing");
		Map<String, Convention> conventions = new LinkedHashMap<>();
		for (Map.Entry<?, ?> e : entrypoints.entrySet()) {
			String key = String.valueOf(e.getKey());
			if (!(e.getValue() instanceof Map<?, ?> fields)) throw new JsonException("The convention of " + key + " must be an object");
			String host = null;
			String annotation = null;
			String property = null;
			for (Map.Entry<?, ?> field : fields.entrySet()) {
				switch (String.valueOf(field.getKey())) {
					case "host" -> host = text(field.getValue(), key);
					case "annotation" -> annotation = text(field.getValue(), key);
					case "property" -> property = text(field.getValue(), key);
					default -> throw new JsonException("Unknown key " + field.getKey() + " in the convention of " + key);
				}
			}
			if (host == null) throw new JsonException("The convention of " + key + " names no host");
			if ((annotation == null) == (property == null)) throw new JsonException("The convention of " + key + " must have one of annotation and property");
			if (annotation != null && !(annotation.startsWith("L") && annotation.endsWith(";"))) {
				throw new JsonException("The annotation of " + key + " must be a type descriptor (Lpackage/Name;)");
			}
			conventions.put(key, new Convention(host, annotation, property));
		}
		return Map.copyOf(conventions);
	}

	private static String text(Object value, String key) throws JsonException {
		if (!(value instanceof String text) || text.isBlank()) throw new JsonException("A value in the convention of " + key + " must be a non-empty string");
		return text;
	}
}
