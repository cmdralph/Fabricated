package io.github.cmdralph.fabricated.core.translate;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.remap.MappingTable;

/**
 * Everything about the running game that affects translation output.
 *
 * @param minecraftVersion    e.g. {@code 1.21.11} or {@code 26.2}
 * @param neoForgeVersion     NeoForge version (part of cache keys: pre-flight depends on its patches)
 * @param clientSide          physical side
 * @param namespace           namespace Fabric mods for this version are compiled against
 * @param runtimeMixinVersion running Mixin version, e.g. {@code 0.17.4+mixin.0.8.7}
 * @param mixinExtrasVersion  running MixinExtras version, e.g. {@code 0.5.3}, or {@code null} when unknown
 * @param fabricatedBuild     Fabricated's version and the digest of its code and data (part of cache keys: a
 *                            translation is reused only by the build that made it)
 * @param mappings            intermediary table, or {@code null} when {@code namespace} is official
 * @param mappingsHash        hash identifying {@code mappings}, or {@code "none"}
 * @param game                index of runtime game classes (Minecraft + NeoForge)
 * @param untransformable     classes loaded outside NeoForge's transforming class loader (the JDK, libraries)
 * @param vanilla             vanilla's code of the game in the game's names, which pre-flight reads to tell where
 *                            NeoForge's patches moved an injection point (VANILLA-1; on 1.21.11 the obfuscated jar read
 *                            through Mojang's mappings, VANILLA-2), or {@link ClassBytesSource#EMPTY} when it wasn't found
 * @param installed           classes of the jars installed next to the Fabric mods (Fabricated's Fabric API, NeoForge
 *                            mods, and on 26.x the Fabric mods), which Fabric mixins may target; the pre-flight
 *                            results that depend on them are cached with their fingerprints
 * @param installedIndex      index of the game classes and {@code installed}
 * @param unsupportedPackages the packages only unsupported Fabric API modules have (internal names), with the module:
 *                            a mixin using a class there can't apply
 * @param descriptor          how the platform's loader learns of a translated mod ({@link PlatformDescriptor#NEOFORGE}
 *                            on {@code main})
 */
public record TranslationEnvironment(
		String minecraftVersion,
		String neoForgeVersion,
		boolean clientSide,
		Namespace namespace,
		String runtimeMixinVersion,
		String mixinExtrasVersion,
		String lowestMixinBehavior,
		String fabricatedBuild,
		MappingTable mappings,
		String mappingsHash,
		ClassIndex game,
		ClassBytesSource untransformable,
		ClassBytesSource vanilla,
		InstalledClasses installed,
		ClassIndex installedIndex,
		Map<String, String> unsupportedPackages,
		PlatformDescriptor descriptor) {

	/**
	 * The format of cached translations. Builds of Fabricated differ in {@link #fabricatedBuild}, so a change to what
	 * translating produces needs no bump; one to how an entry is read back (the overlay's layout, its metadata) does.
	 */
	public static final int TRANSLATION_SCHEMA = 39;

	/** Namespace Fabric mods are distributed in. */
	public enum Namespace {
		/** Obfuscated Minecraft (≤ 1.21.11): mods use Fabric Intermediary names. */
		INTERMEDIARY,
		/** Unobfuscated Minecraft (26.x): mods use Mojang's names, identical to the runtime. */
		OFFICIAL
	}

	public TranslationEnvironment {
		if (namespace == Namespace.INTERMEDIARY && mappings == null) {
			throw new IllegalArgumentException("Intermediary translation requires mappings");
		}
		unsupportedPackages = Collections.unmodifiableMap(new TreeMap<>(unsupportedPackages));
	}

	public String side() {
		return clientSide ? "client" : "server";
	}
}
