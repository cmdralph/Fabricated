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
import io.github.cmdralph.fabricated.core.mixin.NativeModMixins;
import io.github.cmdralph.fabricated.core.resolve.NativeModules;
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
	record Found(List<Pipeline.NeoForgeMod> mods, InstalledClasses classes, List<NativeModules.Provider> providers, Set<String> libraryPackages) {
	}

	private NeoForgeMods() {
	}

	/**
	 * @param nested     whether to read the mods nested in the files too, and the classes of all (only Fabric mods need them)
	 * @param first      jars whose classes come before the mods' (Fabricated's own Fabric API)
	 * @param clientSide the side, whose mixins each jar lists (read once asked: what a Fabric mod's pre-flight reads of the
	 *                   NeoForge mods' mixins, and whether one keeps a class's vanilla methods from being put back, SEAM-1)
	 */
	static Found of(List<IModFile> files, boolean nested, List<InstalledClasses.Jar> first, boolean clientSide) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		List<InstalledClasses.Jar> jars = new ArrayList<>(first);
		List<NativeModules.Provider> providers = new ArrayList<>();
		TomlParser parser = new TomlParser();
		for (IModFile file : files) {
			List<IModInfo> infos = file.getModInfos();
			Map<String, List<String>> provides = infos.isEmpty() ? Map.of() : provides(file, parser);
			List<Pipeline.NeoForgeMod> declared = new ArrayList<>();
			for (IModInfo mod : infos) {
				if (mod.getModId().equals("minecraft")) continue;
				declared.add(new Pipeline.NeoForgeMod(mod.getModId(), mod.getVersion().toString(), mod.getDisplayName(),
						PlatformMods.withProvides(PlatformMods.plain(mod.getModProperties()), provides.getOrDefault(mod.getModId(), List.of())),
						List.of(file.getFilePath())));
			}
			mods.addAll(declared);
			if (!infos.isEmpty() && infos.stream().noneMatch(mod -> GAME.contains(mod.getModId()) || mod.getModId().equals(ModIds.FABRICATED))) {
				jars.add(new InstalledClasses.Jar(infos.getFirst().getDisplayName(), name -> Fml.contains(file, name + ".class"),
						name -> Fml.read(file, name + ".class"),
						() -> NativeModMixins.declaredMixins(entry -> Fml.read(file, entry), mixinConfigs(readToml(file)), clientSide), false,
						InstalledClasses.fingerprint(file.getFilePath(), "")));
				if (nested && !declared.isEmpty()) {
					providers.add(new NativeModules.Provider(declared.getFirst(), file.getFileName(), PlatformMods.internal(Fml.packages(file))));
				}
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
					List<Pipeline.NeoForgeMod> declared = PlatformMods.declared(parser.parse(toml.text()), toml.jarVersion(), path);
					mods.addAll(declared);
					if (declared.isEmpty() || toml.classes().isEmpty()) continue;
					Set<String> classes = toml.classes();
					providers.add(new NativeModules.Provider(declared.getFirst(), toml.origin(), PlatformMods.packagesOf(classes)));
					jars.add(new InstalledClasses.Jar(declared.getFirst().displayName(), classes::contains,
							name -> NestedNeoForgeJars.readClass(path, toml.chain(), name, ArchiveLimits.DEFAULTS),
							() -> NativeModMixins.declaredMixins(entry -> NestedNeoForgeJars.readFile(path, toml.chain(), entry, ArchiveLimits.DEFAULTS),
									mixinConfigs(toml.text()), clientSide), false, InstalledClasses.fingerprint(path, String.join("!", toml.chain()))));
				}
			} catch (IOException | JsonException | ParsingException e) {
				// NeoForge reports a broken nested jar itself; Fabric mods just don't see its mods.
				LOGGER.warn("Could not read the mods nested in {}: {}", file.getFileName(), e.toString());
			}
		}
		return new Found(mods, new InstalledClasses(jars), providers, Set.copyOf(libraryPackages));
	}

	/** The mods.toml of a mod file, as text; null where it has none, too large a one, or it can't be read. */
	private static String readToml(IModFile file) {
		try {
			byte[] toml = Fml.read(file, NestedNeoForgeJars.MODS_TOML);
			return toml == null || toml.length > NestedNeoForgeJars.MAX_TEXT_BYTES ? null : new String(toml, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	/** The mixin configs a mods.toml declares ({@code [[mixins]] config = ...}), in order; none where it can't be parsed. */
	static List<String> mixinConfigs(String toml) {
		List<String> configs = new ArrayList<>();
		if (toml == null) return configs;
		try {
			if (!(new TomlParser().parse(toml).get("mixins") instanceof List<?> mixins)) return configs;
			for (Object entry : mixins) {
				if (entry instanceof UnmodifiableConfig mixin && mixin.get("config") instanceof String config) configs.add(config);
			}
		} catch (ParsingException e) {
			// NeoForge reports a broken mods.toml itself.
		}
		return configs;
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
				if (entry instanceof UnmodifiableConfig mod && mod.get("modId") instanceof String id) {
					provides.put(id, PlatformMods.strings(mod.get("provides")));
				}
			}
		} catch (IOException | ParsingException e) {
			// NeoForge reports a broken mods.toml itself.
			LOGGER.debug("Could not read the mods.toml of {}", file.getFileName(), e);
		}
		return provides;
	}
}
