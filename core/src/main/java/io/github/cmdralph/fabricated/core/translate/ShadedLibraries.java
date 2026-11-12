package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassFiles;

/**
 * The libraries Fabric's Mixin carries in its own package, {@code org/spongepowered/include/}: its jar is
 * SpongePowered's Mixin with Guava 21 and Gson relocated there (read in Fabric's 0.16.4 to 0.17.4 jars), and a Fabric
 * mod's code may name them. Wover's item API builds its smithing template lists with the relocated {@code ImmutableList},
 * and on Forge 52 and 64 to 66 BetterEnd's, BetterNether's, BCLib's and Wover's entrypoints threw
 * {@code ClassNotFoundException} (LIB-3): SpongePowered's own jars, which Forge runs (0.8.5 and 0.8.7), carry nothing
 * there and name the libraries' own packages, which the game ships.
 * <p>
 * A mod's reference to a class there that the platform doesn't have points at the library's own class where the
 * platform has that, as SpongePowered's Mixin's own references do. The game's Guava and Gson are later releases of the
 * same libraries, which keep the API outside Guava's {@code @Beta} (Wover's calls, {@code ImmutableList.builder},
 * {@code addAll} and {@code build}, are in Guava 33's with the same descriptors); a member a later release dropped fails
 * at the call, as the class did. Where the running Mixin carries the class (Fabric's, which NeoForge runs), the
 * reference stays as it is.
 */
final class ShadedLibraries {
	static final String SHADED = "org/spongepowered/include/";

	private final ClassBytesSource platform;
	/** Whether the library's own class takes the place of each relocated one asked. */
	private final Map<String, Boolean> unshaded = new ConcurrentHashMap<>();

	ShadedLibraries(ClassBytesSource platform) {
		this.platform = platform;
	}

	/** {@code classFile} naming the libraries' own classes where the platform lacks the relocated ones, or null when it names none. */
	byte[] apply(byte[] classFile) {
		if (!ClassFiles.contains(classFile, SHADED)) return null;
		boolean[] changed = {false};
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(classFile).accept(new ClassRemapper(writer, new Remapper() {
			@Override
			public String map(String internalName) {
				if (!internalName.startsWith(SHADED) || !unshaded.computeIfAbsent(internalName, ShadedLibraries.this::unshaded)) return internalName;
				changed[0] = true;
				return internalName.substring(SHADED.length());
			}
		}), 0);
		return changed[0] ? writer.toByteArray() : null;
	}

	private boolean unshaded(String relocated) {
		try {
			return platform.read(relocated) == null && platform.read(relocated.substring(SHADED.length())) != null;
		} catch (IOException e) {
			return false;
		}
	}
}
