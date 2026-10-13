package io.github.cmdralph.fabricated.core.translate;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.resolve.Candidate;

/**
 * A Fabric mod ready to be handed to NeoForge.
 *
 * @param candidate          the source candidate
 * @param neoForgeId         NeoForge mod id (Fabric id with {@code -} → {@code _})
 * @param jar                class/resource jar: the original file, or the remapped copy for 1.21.11
 * @param overlay            jar with generated metadata that takes priority over {@code jar}
 * @param findings           mixin pre-flight findings (FAIL findings never appear here; the mod would be rejected)
 * @param accessTransformers number of access transformer lines generated
 * @param notes              conversion notes and warnings (access widener, signatures)
 * @param injectedInterfaces interface injections to apply at class load
 * @param environmentClasses classes (internal names) with Fabric's {@code @Environment} annotations, stripped at class load
 * @param cacheHit           whether the outputs came from the cache
 * @param millis             translation time (0 on cache hit)
 */
public record TranslatedMod(
		Candidate candidate,
		String neoForgeId,
		Path jar,
		Path overlay,
		List<PreflightFinding> findings,
		int accessTransformers,
		List<String> notes,
		List<InjectedInterface> injectedInterfaces,
		Set<String> environmentClasses,
		boolean cacheHit,
		long millis) {
}
