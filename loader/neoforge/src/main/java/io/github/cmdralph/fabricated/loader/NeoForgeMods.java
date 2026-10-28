package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.resolve.NestedNeoForgeJars;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * The NeoForge mods Fabric code sees: those NeoForge's discovery found, and those nested in their files (jar-in-jar).
 * Every dependency locator is handed the same files NeoForge found, so the nested mods, which NeoForge's own jar-in-jar
 * locator adds, are read here from each file's jar-in-jar list (Sodium for NeoForge nests its mod in a service jar).
 */
final class NeoForgeMods {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The version a mods.toml gives as its jar's version (NeoForge reads it from the jar's manifest). */
	private static final String JAR_VERSION = "${file.jarVersion}";
	/** The game's own files, whose classes pre-flight reads as the game's. */
	private static final Set<String> GAME = Set.of("minecraft", "neoforge");

	/**
	 * @param mods      the mods
	 * @param classes   their jars' classes, which Fabric mixins may target
	 * @param providers the packages of each mod file and nested mod jar (only read when the mods nested in the files
	 *                  are), with the first mod it declares: what other mods' packages Fabricated's own mustn't clash with
	 * @param libraryPackages the packages of the libraries nested in the files (read with the nested mods), which no
	 *                  Fabric jar may split or shadow: on 26.x Kotlin for Forge's Kotlin, in FML's plugin loader
	 */
	record Found(List<Pipeline.NeoForgeMod> mods, InstalledClasses classes, List<Provider> providers, Set<String> libraryPackages) {
	}

	/**
	 * The packages (internal form) of a mod file or nested mod jar, and the first mod it declares.
	 *
	 * @param origin where the jar is, for the report
	 */
	record Provider(Pipeline.NeoForgeMod mod, String origin, Set<String> packages) {
	}

	private NeoForgeMods() {
	}

	/**
	 * @param nested whether to read the mods nested in the files too, and the classes of all (only Fabric mods need them)
	 * @param first  jars whose classes come before the mods' (Fabricated's own Fabric API)
	 */
	static Found of(List<IModFile> files, boolean nested, List<InstalledClasses.Jar> first) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		List<InstalledClasses.Jar> jars = new ArrayList<>(first);
		List<Provider> providers = new ArrayList<>();
		TomlParser parser = new TomlParser();
		for (IModFile file : files) {
			List<IModInfo> infos = file.getModInfos();
			Map<String, List<String>> provides = infos.isEmpty() ? Map.of() : provides(file, parser);
			List<Pipeline.NeoForgeMod> declared = new ArrayList<>();
			for (IModInfo mod : infos) {
				if (mod.getModId().equals("minecraft")) continue;
				declared.add(new Pipeline.NeoForgeMod(mod.getModId(), mod.getVersion().toString(), mod.getDisplayName(),
						withProvides(plain(mod.getModProperties()), provides.getOrDefault(mod.getModId(), List.of())), List.of(file.getFilePath())));
			}
			mods.addAll(declared);
			if (!infos.isEmpty() && infos.stream().noneMatch(mod -> GAME.contains(mod.getModId()) || mod.getModId().equals(ModIds.FABRICATED))) {
				jars.add(new InstalledClasses.Jar(infos.getFirst().getDisplayName(), name -> Fml.contains(file, name + ".class"),
						name -> Fml.read(file, name + ".class")));
				if (nested && !declared.isEmpty()) providers.add(new Provider(declared.getFirst(), file.getFileName(), internal(Fml.packages(file))));
			}
		}
		if (!nested) return new Found(mods, InstalledClasses.NONE, List.of(), Set.of());
		Set<String> libraryPackages = new HashSet<>();
		for (IModFile file : files) {
			Path path = file.getFilePath();
			if (!Fml.contains(file, NestedNeoForgeJars.METADATA) || !Files.isRegularFile(path)) continue;
			try {
				NestedNeoForgeJars.Contents nestedJars = NestedNeoForgeJars.readAll(path, ArchiveLimits.DEFAULTS);
				libraryPackages.addAll(nestedJars.libraryPackages());
				for (NestedNeoForgeJars.ModsToml toml : nestedJars.mods()) {
					List<Pipeline.NeoForgeMod> declared = declared(parser.parse(toml.text()), toml.jarVersion(), path);
					mods.addAll(declared);
					if (declared.isEmpty() || toml.classes().isEmpty()) continue;
					Set<String> classes = toml.classes();
					providers.add(new Provider(declared.getFirst(), toml.origin(), packagesOf(classes)));
					jars.add(new InstalledClasses.Jar(declared.getFirst().displayName(), classes::contains,
							name -> NestedNeoForgeJars.readClass(path, toml.chain(), name, ArchiveLimits.DEFAULTS)));
				}
			} catch (IOException | JsonException | ParsingException e) {
				// NeoForge reports a broken nested jar itself; Fabric mods just don't see its mods.
				LOGGER.warn("Could not read the mods nested in {}: {}", file.getFileName(), e.toString());
			}
		}
		return new Found(mods, new InstalledClasses(jars), providers, Set.copyOf(libraryPackages));
	}

	private static Set<String> internal(Set<String> dottedPackages) {
		Set<String> packages = new HashSet<>();
		for (String p : dottedPackages) packages.add(p.replace('.', '/'));
		return packages;
	}

	private static Set<String> packagesOf(Set<String> classes) {
		Set<String> packages = new HashSet<>();
		for (String name : classes) {
			int slash = name.lastIndexOf('/');
			if (slash > 0) packages.add(name.substring(0, slash));
		}
		return packages;
	}

	/**
	 * The mods a mods.toml declares.
	 *
	 * @param container the jar it is nested in
	 */
	private static List<Pipeline.NeoForgeMod> declared(UnmodifiableConfig toml, String jarVersion, Path container) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		if (!(toml.get("mods") instanceof List<?> declared)) return mods;
		for (Object entry : declared) {
			if (!(entry instanceof UnmodifiableConfig mod) || !(mod.get("modId") instanceof String id)) continue;
			String version = mod.get("version") instanceof String v ? v : "1";
			String name = mod.get("displayName") instanceof String n ? n : id;
			Object properties = toml.get(List.of("modproperties", id));
			// Its jar is inside the container, which NeoForge extracts later: to Fabric code, the container is its root.
			mods.add(new Pipeline.NeoForgeMod(id, version.equals(JAR_VERSION) ? jarVersion : version, name,
					withProvides(properties instanceof UnmodifiableConfig config ? plain(config.valueMap()) : Map.of(), strings(mod.get("provides"))),
					List.of(container)));
		}
		return mods;
	}

	/**
	 * The ids each mod of a mod file says it provides ({@code provides} in its mods.toml entry). NeoForge itself ignores
	 * the key; Forgified Fabric API lists its modules' Fabric ids and the umbrella's {@code fabric} and {@code fabric-api}
	 * there, which Fabric mods depend on.
	 */
	private static Map<String, List<String>> provides(IModFile file, TomlParser parser) {
		Map<String, List<String>> provides = new LinkedHashMap<>();
		try {
			byte[] toml = Fml.read(file, NestedNeoForgeJars.MODS_TOML);
			if (toml == null || toml.length > NestedNeoForgeJars.MAX_TEXT_BYTES) return provides;
			if (!(parser.parse(new String(toml, StandardCharsets.UTF_8)).get("mods") instanceof List<?> declared)) return provides;
			for (Object entry : declared) {
				if (entry instanceof UnmodifiableConfig mod && mod.get("modId") instanceof String id) provides.put(id, strings(mod.get("provides")));
			}
		} catch (IOException | ParsingException e) {
			// NeoForge reports a broken mods.toml itself.
			LOGGER.debug("Could not read the mods.toml of {}", file.getFileName(), e);
		}
		return provides;
	}

	private static List<String> strings(Object value) {
		return value instanceof List<?> list ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
	}

	/** The mod's properties with the ids it provides added to those it lists as Fabric's provides, if any. */
	private static Map<String, Object> withProvides(Map<String, Object> properties, List<String> provides) {
		if (provides.isEmpty()) return properties;
		Map<String, Object> merged = new LinkedHashMap<>(properties);
		List<Object> ids = new ArrayList<>(merged.get(Pipeline.NeoForgeMod.PROVIDES) instanceof List<?> listed ? listed : List.of());
		for (String id : provides) if (!ids.contains(id)) ids.add(id);
		merged.put(Pipeline.NeoForgeMod.PROVIDES, ids);
		return merged;
	}

	/** Mod properties as plain values: TOML tables become maps; values TOML has and JSON hasn't (dates) become strings. */
	private static Map<String, Object> plain(Map<String, Object> properties) {
		Map<String, Object> plain = new LinkedHashMap<>();
		properties.forEach((key, value) -> plain.put(key, plainValue(value)));
		return plain;
	}

	private static Object plainValue(Object value) {
		if (value instanceof UnmodifiableConfig config) return plain(config.valueMap());
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> plain = new LinkedHashMap<>();
			map.forEach((key, v) -> plain.put(String.valueOf(key), plainValue(v)));
			return plain;
		}
		if (value instanceof List<?> list) {
			List<Object> plain = new ArrayList<>(list.size());
			for (Object v : list) plain.add(plainValue(v));
			return plain;
		}
		if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) return value;
		return String.valueOf(value);
	}
}
