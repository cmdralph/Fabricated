package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;

import cpw.mods.jarhandling.SecureJar;
import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.resolve.NestedNeoForgeJars;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

/**
 * The Forge mods Fabric code sees: those Forge's locators found, and those nested in their files (jar-in-jar). Forge
 * runs Fabricated's dependency locator before its own jar-in-jar locator, so the nested mods are read here from each
 * file's jar-in-jar list, as on NeoForge ({@code NeoForgeMods}).
 */
final class ForgeMods {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The version a mods.toml gives as its jar's version (Forge reads it from the jar's manifest). */
	private static final String JAR_VERSION = "${file.jarVersion}";
	/** The game's own files, whose classes pre-flight reads as the game's. */
	private static final Set<String> GAME = Set.of("minecraft", "forge");

	/**
	 * @param mods    the mods
	 * @param classes their jars' classes, which Fabric mixins may target
	 */
	record Found(List<Pipeline.NeoForgeMod> mods, InstalledClasses classes) {
	}

	private ForgeMods() {
	}

	/**
	 * @param nested whether to read the mods nested in the files too, and the classes of all (only Fabric mods need them)
	 * @param first  jars whose classes come before the mods' (Fabricated's own Fabric API)
	 */
	static Found of(Iterable<IModFile> files, boolean nested, List<InstalledClasses.Jar> first) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		List<InstalledClasses.Jar> jars = new ArrayList<>(first);
		for (IModFile file : files) {
			List<IModInfo> infos = modInfos(file);
			for (IModInfo mod : infos) {
				if (mod.getModId().equals("minecraft")) continue;
				mods.add(new Pipeline.NeoForgeMod(mod.getModId(), mod.getVersion().toString(), mod.getDisplayName(), plain(mod.getModProperties()),
						List.of(file.getFilePath())));
			}
			if (!infos.isEmpty() && infos.stream().noneMatch(mod -> GAME.contains(mod.getModId()))) {
				SecureJar jar = file.getSecureJar();
				jars.add(new InstalledClasses.Jar(infos.get(0).getDisplayName(), name -> contains(jar, name + ".class"), name -> read(jar, name + ".class")));
			}
		}
		if (!nested) return new Found(mods, InstalledClasses.NONE);
		TomlParser parser = new TomlParser();
		for (IModFile file : files) {
			Path path = file.getFilePath();
			if (!contains(file.getSecureJar(), NestedNeoForgeJars.METADATA) || !Files.isRegularFile(path)) continue;
			try {
				for (NestedNeoForgeJars.ModsToml toml : NestedNeoForgeJars.read(path, ArchiveLimits.DEFAULTS, ModsTomlWriter.FORGE_PATH)) {
					List<Pipeline.NeoForgeMod> declared = declared(parser.parse(toml.text()), toml.jarVersion(), path);
					mods.addAll(declared);
					if (declared.isEmpty() || toml.classes().isEmpty()) continue;
					Set<String> classes = toml.classes();
					jars.add(new InstalledClasses.Jar(declared.get(0).displayName(), classes::contains,
							name -> NestedNeoForgeJars.readClass(path, toml.chain(), name, ArchiveLimits.DEFAULTS)));
				}
			} catch (IOException | JsonException | ParsingException e) {
				// Forge reports a broken nested jar itself; Fabric mods just don't see its mods.
				LOGGER.warn("Could not read the mods nested in {}: {}", file.getFileName(), e.toString());
			}
		}
		return new Found(mods, new InstalledClasses(jars));
	}

	/**
	 * A file's mods; none for a file without mod metadata. {@code ModFile.getModInfos()} fails on such a file (Forge 47's
	 * own language and library jars: javafml, mclanguage, lowcode, fmlcore), which FML's code filters out by
	 * {@code getModFileInfo() != null} first; on Forge 61 to 66 every file this sees has it (#89's server smoke).
	 */
	static List<IModInfo> modInfos(IModFile file) {
		return file.getModFileInfo() == null ? List.of() : file.getModInfos();
	}

	static boolean contains(SecureJar jar, String path) {
		return jar.moduleDataProvider().findFile(path).isPresent();
	}

	/** A file of a mod file's jar, or null when it has none. */
	static byte[] read(SecureJar jar, String path) throws IOException {
		Optional<InputStream> stream = jar.moduleDataProvider().open(path);
		if (stream.isEmpty()) return null;
		try (InputStream in = stream.get()) {
			return in.readAllBytes();
		}
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
			// Its jar is inside the container, which Forge extracts later: to Fabric code, the container is its root.
			mods.add(new Pipeline.NeoForgeMod(id, version.equals(JAR_VERSION) ? jarVersion : version, name,
					properties instanceof UnmodifiableConfig config ? plain(config.valueMap()) : Map.of(), List.of(container)));
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
