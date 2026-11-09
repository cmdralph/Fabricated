package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.remap.OfficialClasses;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;

/**
 * What Forge 52 and 61 to 66 do below the game where Forge 47 does otherwise ({@code loader/forge-shared/src/fml47}):
 * their securemodules (2.2.24 on Forge 52 and 61), upstream Mixin 0.8.7, and the game in Mojang's names, as their
 * installer leaves it (docs/PORTING.md §4.1, read in the forge branch's server smoke, and in Forge 52's install profile,
 * which names the same files): in {@code <libraries>/net/minecraft/<side>/<mc>/}, {@code <side>-<mc>-unpacked.jar} is
 * vanilla as Mojang's bundler holds it (obfuscated on 1.21.1 and 1.21.11, in Mojang's names on 26.x), and on 1.21.1 and
 * 1.21.11 {@code -official.jar} is vanilla in Mojang's names and {@code -mappings.tsrg} Mojang's mappings. Forge's own
 * jar is among the same libraries, and is the path of the {@code minecraft} mod file.
 */
final class ForgeStack {
	/** securemodules takes a mod file's manifest from its last path, and searches that first: the overlay goes last. */
	static final boolean OVERLAY_FIRST = false;

	private ForgeStack() {
	}

	/**
	 * Forge 52's descriptor (Minecraft 1.21.1): Forge's, with a mod's pack metadata in 1.21.1's form. Forge 52 builds each
	 * mod's pack with vanilla's {@code Pack.readPackMetadata} (its {@code ResourcePackLoader}), which gives no pack for
	 * metadata without a {@code pack_format}, the field 1.21.1 requires where 1.21.9 and later read {@code min_format} and
	 * {@code max_format}: 1.21.1's data pack format, with its resource pack format among those supported, as a mod's pack
	 * holds both.
	 */
	private static final PlatformDescriptor FORGE_52 = new PlatformDescriptor.Forge() {
		@Override
		public byte[] packMetadata(ModsTomlWriter.Spec spec) {
			Map<String, Object> pack = new LinkedHashMap<>();
			pack.put("description", spec.displayName());
			pack.put("pack_format", 48);
			pack.put("supported_formats", List.of(34, 48));
			return (Json.write(Map.of("pack", pack), true) + "\n").getBytes(StandardCharsets.UTF_8);
		}
	};

	/** Forge's ({@link PlatformDescriptor#FORGE}), or for Forge 52's Minecraft 1.21.1 {@link #FORGE_52}. */
	static PlatformDescriptor descriptor(String minecraftVersion) {
		return "1.21.1".equals(minecraftVersion) ? FORGE_52 : PlatformDescriptor.FORGE;
	}

	/** The files of the game a Fabric mod reads through the {@code minecraft} mod container: the {@code minecraft} mod file. */
	static List<Path> gameFiles(Path gameFile) {
		return List.of(gameFile);
	}

	/**
	 * Vanilla in Mojang's names, as the installer left it: {@code -official.jar}, and where the game isn't obfuscated
	 * (26.x) {@code -unpacked.jar}; else NeoForge's layout ({@link VanillaGameJar}), an obfuscated game read in Mojang's
	 * names through the mappings Forge's installer left (VANILLA-2).
	 */
	static Pipeline.VanillaGame vanillaGame(Path gameFile, String minecraftVersion, boolean client, Namespace namespace, String mojangMappingsPath,
			TranslationCache cache, List<SafeArchive> opened) {
		boolean obfuscated = namespace == Namespace.INTERMEDIARY;
		for (Path libraries : ForgeLibraries.directories(gameFile)) {
			Path official = ForgeLibraries.gameFile(libraries, client, minecraftVersion, "official.jar");
			if (Files.isRegularFile(official)) return ForgeLibraries.installedGame(official, "Mojang's names", opened);
			Path unpacked = ForgeLibraries.gameFile(libraries, client, minecraftVersion, "unpacked.jar");
			if (!obfuscated && Files.isRegularFile(unpacked)) return ForgeLibraries.installedGame(unpacked, "Mojang's names", opened);
		}
		Pipeline.VanillaGame vanilla = VanillaGameJar.find(gameFile, minecraftVersion, client, cache, opened);
		if (!obfuscated || vanilla.classes() == ClassBytesSource.EMPTY) return vanilla;
		return new Pipeline.VanillaGame(new OfficialClasses(vanilla.classes(), () -> {
			try {
				return Files.newBufferedReader(IntermediaryMappings.locateMojangMappings(minecraftVersion, client, mojangMappingsPath, gameFile),
						StandardCharsets.UTF_8);
			} catch (MappingException e) {
				throw new IOException(e.getMessage(), e);
			}
		}), vanilla.location() + ", in Mojang's names through its mappings");
	}

	/** The mapping files Intermediary is composed with: Mojang's alone. */
	static List<Path> mappingFiles(String minecraftVersion, Path mojang, Path gameFile) {
		return List.of(mojang);
	}

	/**
	 * Intermediary composed with Mojang's mappings. TSRG gives fields no descriptors, so fields sharing an obfuscated name
	 * are told apart by their descriptors in the installed game in Mojang's names ({@code -official.jar}).
	 */
	static MappingTable compose(Reader intermediary, List<Path> files) throws MappingException, IOException {
		Path mojang = files.get(0);
		Path official = mojang.resolveSibling(mojang.getFileName().toString().replace("-mappings.tsrg", "-official.jar"));
		try (Reader mappings = Files.newBufferedReader(mojang, StandardCharsets.UTF_8);
				ZipFile game = mojang.toString().endsWith(".tsrg") && Files.isRegularFile(official) ? new ZipFile(official.toFile()) : null) {
			return MappingComposer.compose(intermediary, mappings, game == null ? MappingComposer.FieldDescriptors.NONE : fieldDescriptors(game));
		}
	}

	/** Mixin 0.8.7 takes a constructor's point marked {@code unsafe}, as pre-flight marks it (CTOR-2): Fabricated registers no point. */
	static void registerInjectionPoints() {
	}

	/** The fields' descriptors in the game in Mojang's names, read a class at a time as the composer asks. */
	private static MappingComposer.FieldDescriptors fieldDescriptors(ZipFile game) {
		Map<String, Map<String, String>> classes = new HashMap<>();
		return (owner, name) -> {
			Map<String, String> fields = classes.get(owner);
			if (fields == null) classes.put(owner, fields = fields(game, owner));
			return fields.get(name);
		};
	}

	private static Map<String, String> fields(ZipFile game, String owner) throws IOException {
		ZipEntry entry = game.getEntry(owner + ".class");
		if (entry == null) return Map.of();
		byte[] data;
		try (InputStream in = game.getInputStream(entry)) {
			data = in.readAllBytes();
		}
		Map<String, String> fields = new HashMap<>();
		new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				fields.put(name, descriptor);
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return fields;
	}
}
