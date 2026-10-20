package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import io.github.cmdralph.fabricated.core.resolve.NestedNeoForgeJars;
import net.neoforged.fml.jarcontents.JarContents;
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
	 * @param mods    the mods
	 * @param classes their jars' classes, which Fabric mixins may target
	 */
	record Found(List<Pipeline.NeoForgeMod> mods, InstalledClasses classes) {
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
		for (IModFile file : files) {
			List<IModInfo> infos = file.getModInfos();
			for (IModInfo mod : infos) {
				if (mod.getModId().equals("minecraft")) continue;
				mods.add(new Pipeline.NeoForgeMod(mod.getModId(), mod.getVersion().toString(), mod.getDisplayName(), plain(mod.getModProperties()),
						List.of(file.getFilePath())));
			}
			if (!infos.isEmpty() && infos.stream().noneMatch(mod -> GAME.contains(mod.getModId()))) {
				JarContents contents = file.getContents();
				jars.add(new InstalledClasses.Jar(infos.getFirst().getDisplayName(), name -> contents.containsFile(name + ".class"),
						name -> contents.readFile(name + ".class")));
			}
		}
		if (!nested) return new Found(mods, InstalledClasses.NONE);
		TomlParser parser = new TomlParser();
		for (IModFile file : files) {
			Path path = file.getFilePath();
			if (!file.getContents().containsFile(NestedNeoForgeJars.METADATA) || !Files.isRegularFile(path)) continue;
			try {
				for (NestedNeoForgeJars.ModsToml toml : NestedNeoForgeJars.read(path, ArchiveLimits.DEFAULTS)) {
					List<Pipeline.NeoForgeMod> declared = declared(parser.parse(toml.text()), toml.jarVersion());
					mods.addAll(declared);
					if (declared.isEmpty() || toml.classes().isEmpty()) continue;
					Set<String> classes = toml.classes();
					jars.add(new InstalledClasses.Jar(declared.getFirst().displayName(), classes::contains,
							name -> NestedNeoForgeJars.readClass(path, toml.chain(), name, ArchiveLimits.DEFAULTS)));
				}
			} catch (IOException | JsonException | ParsingException e) {
				// NeoForge reports a broken nested jar itself; Fabric mods just don't see its mods.
				LOGGER.warn("Could not read the mods nested in {}: {}", file.getFileName(), e.toString());
			}
		}
		return new Found(mods, new InstalledClasses(jars));
	}

	/** The mods a mods.toml declares. */
	private static List<Pipeline.NeoForgeMod> declared(UnmodifiableConfig toml, String jarVersion) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		if (!(toml.get("mods") instanceof List<?> declared)) return mods;
		for (Object entry : declared) {
			if (!(entry instanceof UnmodifiableConfig mod) || !(mod.get("modId") instanceof String id)) continue;
			String version = mod.get("version") instanceof String v ? v : "1";
			String name = mod.get("displayName") instanceof String n ? n : id;
			Object properties = toml.get(List.of("modproperties", id));
			// No files of its own to Fabric code: it is inside another jar (NeoForge extracts it later).
			mods.add(new Pipeline.NeoForgeMod(id, version.equals(JAR_VERSION) ? jarVersion : version, name,
					properties instanceof UnmodifiableConfig config ? plain(config.valueMap()) : Map.of(), List.of()));
		}
		return mods;
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
