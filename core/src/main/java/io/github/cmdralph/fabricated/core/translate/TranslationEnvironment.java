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
 * @param fabricatedVersion   Fabricated version (part of cache keys)
 * @param mappings            intermediary table, or {@code null} when {@code namespace} is official
 * @param mappingsHash        hash identifying {@code mappings}, or {@code "none"}
 * @param game                index of runtime game classes (Minecraft + NeoForge)
 * @param untransformable     classes loaded outside NeoForge's transforming class loader (the JDK, libraries)
 * @param installed           classes of the jars installed next to the Fabric mods (Fabricated's Fabric API, NeoForge
 *                            mods, and on 26.x the Fabric mods), which Fabric mixins may target; the pre-flight
 *                            results that depend on them are cached with their fingerprints
 * @param installedIndex      index of the game classes and {@code installed}
 * @param unsupportedPackages the packages only unsupported Fabric API modules have (internal names), with the module:
 *                            a mixin using a class there can't apply
 */
public record TranslationEnvironment(
		String minecraftVersion,
		String neoForgeVersion,
		boolean clientSide,
		Namespace namespace,
		String runtimeMixinVersion,
		String lowestMixinBehavior,
		String fabricatedVersion,
		MappingTable mappings,
		String mappingsHash,
		ClassIndex game,
		ClassBytesSource untransformable,
		InstalledClasses installed,
		ClassIndex installedIndex,
		Map<String, String> unsupportedPackages) {

	/** Bumped whenever translation output changes for the same inputs. */
	public static final int TRANSLATION_SCHEMA = 18;

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
