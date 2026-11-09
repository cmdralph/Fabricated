package io.github.cmdralph.fabricated.fabricapiremap;

import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.DeterministicJarWriter;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.remap.JarRemapper;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingTable;

/**
 * Remaps Fabric API's module jars from Intermediary's names to Mojang's with core's remapper, as Fabricated remaps a
 * Fabric mod for the game, so that what an obfuscated Minecraft version's projects compile against Fabric API is the
 * real one (mc/mc.gradle's remapFabricApi). Build time only: no Fabricated jar contains it.
 *
 * <p>Arguments: Intermediary's Tiny file, Mojang's mappings, the output directory, then {@code <module>=<jar>} for each
 * module jar, which is written there as {@code <module>.jar}. The directory holds those jars alone afterwards.
 */
public final class RemapFabricApi {
	private RemapFabricApi() {
	}

	public static void main(String[] args) throws Exception {
		MappingTable table;
		try (Reader tiny = Files.newBufferedReader(Path.of(args[0])); Reader mojang = Files.newBufferedReader(Path.of(args[1]))) {
			table = MappingComposer.compose(tiny, mojang);
		}
		JarRemapper remapper = new JarRemapper(table);
		Path out = Files.createDirectories(Path.of(args[2]));
		try (DirectoryStream<Path> earlier = Files.newDirectoryStream(out, "*.jar")) {
			for (Path jar : earlier) Files.delete(jar);
		}
		for (int i = 3; i < args.length; i++) {
			int split = args[i].indexOf('=');
			try (SafeArchive jar = SafeArchive.open(Path.of(args[i].substring(split + 1)), ArchiveLimits.DEFAULTS)) {
				// Refmaps stay as they are: nothing applies these jars' mixins, only compiles against their classes.
				DeterministicJarWriter.write(out.resolve(args[i].substring(0, split) + ".jar"), remapper.remap(jar, Set.of()).files());
			}
		}
	}
}
