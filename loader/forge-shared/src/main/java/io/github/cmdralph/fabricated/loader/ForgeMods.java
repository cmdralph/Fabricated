package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.mixin.NativeModMixins;
import io.github.cmdralph.fabricated.core.resolve.NativeModules;
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
	/** The game's own files, whose classes pre-flight reads as the game's. */
	private static final Set<String> GAME = Set.of("minecraft", "forge");

	/**
	 * @param mods            the mods
	 * @param classes         their jars' classes, which Fabric mixins may target, with the mixins their manifests list,
	 *                        which Forge's Mixin applies (CORPUS-87)
	 * @param providers       the packages of each mod file and nested mod jar (only read when the mods nested in the files
	 *                        are), with the first mod it declares: what other mods' packages Fabricated's own mustn't clash
	 *                        with, and which native module a mod bundles (the provider policy, {@link NativeModules})
	 * @param libraryPackages the packages of the libraries nested in the files (read with the nested mods), which Forge's
	 *                        jar-in-jar locator adds after this one ran: no Fabric jar may split them
	 */
	record Found(List<Pipeline.NeoForgeMod> mods, InstalledClasses classes, List<NativeModules.Provider> providers, Set<String> libraryPackages) {
	}

	private ForgeMods() {
	}

	/**
	 * @param nested     whether to read the mods nested in the files too, and the classes of all (only Fabric mods need them)
	 * @param clientSide whether the game is a client, for the mixins the mods' configs list for it
	 * @param first      jars whose classes come before the mods' (Fabricated's own Fabric API)
	 */
	static Found of(Iterable<IModFile> files, boolean nested, boolean clientSide, List<InstalledClasses.Jar> first) {
		List<Pipeline.NeoForgeMod> mods = new ArrayList<>();
		List<InstalledClasses.Jar> jars = new ArrayList<>(first);
		List<NativeModules.Provider> providers = new ArrayList<>();
		TomlParser parser = new TomlParser();
		for (IModFile file : files) {
			List<IModInfo> infos = modInfos(file);
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
				SecureJar jar = file.getSecureJar();
				jars.add(new InstalledClasses.Jar(infos.get(0).getDisplayName(), name -> contains(jar, name + ".class"), name -> read(jar, name + ".class"),
						() -> NativeModMixins.manifestMixins(entry -> readText(jar, entry), clientSide), false,
						InstalledClasses.fingerprint(file.getFilePath(), "")));
				if (nested && !declared.isEmpty()) {
					providers.add(new NativeModules.Provider(declared.get(0), file.getFileName(), PlatformMods.internal(jar.getPackages())));
				}
			}
		}
		if (!nested) return new Found(mods, InstalledClasses.NONE, List.of(), Set.of());
		Set<String> libraryPackages = new HashSet<>();
		for (IModFile file : files) {
			Path path = file.getFilePath();
			if (!contains(file.getSecureJar(), NestedNeoForgeJars.METADATA) || !Files.isRegularFile(path)) continue;
			try {
				NestedNeoForgeJars.Contents nestedJars = NestedNeoForgeJars.readAll(path, ArchiveLimits.DEFAULTS, ModsTomlWriter.FORGE_PATH);
				libraryPackages.addAll(nestedJars.libraryPackages());
				for (NestedNeoForgeJars.ModsToml toml : nestedJars.mods()) {
					List<Pipeline.NeoForgeMod> declared = PlatformMods.declared(parser.parse(toml.text()), toml.jarVersion(), path);
					mods.addAll(declared);
					if (declared.isEmpty() || toml.classes().isEmpty()) continue;
					Set<String> classes = toml.classes();
					providers.add(new NativeModules.Provider(declared.get(0), toml.origin(), PlatformMods.packagesOf(classes)));
					jars.add(new InstalledClasses.Jar(declared.get(0).displayName(), classes::contains,
							name -> NestedNeoForgeJars.readClass(path, toml.chain(), name, ArchiveLimits.DEFAULTS),
							() -> NativeModMixins.manifestMixins(entry -> NestedNeoForgeJars.readFile(path, toml.chain(), entry, ArchiveLimits.DEFAULTS),
									clientSide), false, InstalledClasses.fingerprint(path, String.join("!", toml.chain()))));
				}
			} catch (IOException | JsonException | ParsingException e) {
				// Forge reports a broken nested jar itself; Fabric mods just don't see its mods.
				LOGGER.warn("Could not read the mods nested in {}: {}", file.getFileName(), e.toString());
			}
		}
		return new Found(mods, new InstalledClasses(jars), providers, Set.copyOf(libraryPackages));
	}

	/**
	 * The ids each mod of a mod file says it provides ({@code provides} in its mods.toml entry). Forge itself ignores the
	 * key; Forgified Fabric API lists its modules' Fabric ids there on NeoForge, which Fabric mods depend on.
	 */
	private static Map<String, List<String>> provides(IModFile file, TomlParser parser) {
		Map<String, List<String>> provides = new LinkedHashMap<>();
		try {
			byte[] toml = readText(file.getSecureJar(), ModsTomlWriter.FORGE_PATH);
			if (toml == null) return provides;
			if (!(parser.parse(new String(toml, StandardCharsets.UTF_8)).get("mods") instanceof List<?> declared)) return provides;
			for (Object entry : declared) {
				if (entry instanceof UnmodifiableConfig mod && mod.get("modId") instanceof String id) {
					provides.put(id, PlatformMods.strings(mod.get("provides")));
				}
			}
		} catch (IOException | ParsingException e) {
			// Forge reports a broken mods.toml itself.
			LOGGER.debug("Could not read the mods.toml of {}", file.getFileName(), e);
		}
		return provides;
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

	/** A text file of a mod file's jar, or null when it has none or it is over {@link NestedNeoForgeJars#MAX_TEXT_BYTES}. */
	private static byte[] readText(SecureJar jar, String path) throws IOException {
		Optional<InputStream> stream = jar.moduleDataProvider().open(path);
		if (stream.isEmpty()) return null;
		try (InputStream in = stream.get()) {
			byte[] data = in.readNBytes(NestedNeoForgeJars.MAX_TEXT_BYTES + 1);
			return data.length > NestedNeoForgeJars.MAX_TEXT_BYTES ? null : data;
		}
	}
}
