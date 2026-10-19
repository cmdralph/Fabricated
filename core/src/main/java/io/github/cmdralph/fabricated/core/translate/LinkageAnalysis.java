package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.LinkageChecker;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Runs the {@link LinkageChecker} over every translated mod against an index of everything that will
 * exist at runtime (game classes, Fabricated's native Fabric API, and all Fabric jars). Results are
 * cached per mod, keyed by the mod and a fingerprint of the whole mod set.
 */
public final class LinkageAnalysis {
	/** Owners verified: Minecraft, Blaze3D and the Fabric API namespace. */
	public static final Predicate<String> CHECKED = name -> name.startsWith("net/minecraft/")
			|| name.startsWith("com/mojang/blaze3d/") || name.startsWith("net/fabricmc/fabric/");

	private final TranslationCache cache;
	private final ArchiveLimits limits;
	private final boolean clientSide;

	public LinkageAnalysis(TranslationCache cache, ArchiveLimits limits, boolean clientSide) {
		this.cache = cache;
		this.limits = limits;
		this.clientSide = clientSide;
	}

	/**
	 * @param mods               translated mods
	 * @param runtime            classes available at runtime (game + native Fabric API + all Fabric jars)
	 * @param platformInterfaces interfaces Fabricated's own (native Fabric API) mixins add to game classes, by
	 *                           target; see {@link #mixinAdditions(byte[], Map)}
	 * @param setFingerprint     identifies the mod set and game version for cache keys
	 * @return issues per NeoForge id (only mods with issues are present)
	 */
	public Map<String, List<String>> analyse(List<TranslatedMod> mods, ClassBytesSource runtime,
			Supplier<Map<String, List<String>>> platformInterfaces, String setFingerprint) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		LinkageChecker checker = null;
		for (TranslatedMod mod : mods) {
			String blob = "linkage-" + Hashing.sha256("v4|" + mod.candidate().sha256() + "|" + setFingerprint + "|" + (clientSide ? "c" : "s")).substring(0, 40) + ".json";
			List<String> issues = readCached(blob);
			if (issues == null) {
				// Built only when some result is not cached: it reads every mod's classes once.
				if (checker == null) {
					Map<String, List<String>> added = addedInterfaces(mods);
					platformInterfaces.get().forEach((target, ifaces) -> added.computeIfAbsent(target, k -> new ArrayList<>()).addAll(ifaces));
					checker = new LinkageChecker(new ClassIndex(runtime, added), CHECKED, clientSide);
				}
				try {
					issues = check(mod, checker);
					cache.storeBlob(blob, Json.write(issues, false).getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					// Not cached: reading the jar may well work next launch (caching itself is an optimisation only).
					if (issues == null) issues = List.of("could not be analysed: " + e.getMessage());
				}
			}
			if (!issues.isEmpty()) result.put(mod.neoForgeId(), issues);
		}
		return result;
	}

	/**
	 * Interfaces that exist on game classes at runtime without being declared there: Fabric's
	 * {@code inject-interface} directives, and interfaces implemented by mixin classes (Mixin adds them to
	 * every target), which is how older Fabric API modules add their extension interfaces.
	 */
	private Map<String, List<String>> addedInterfaces(List<TranslatedMod> mods) {
		Map<String, List<String>> added = new HashMap<>();
		for (TranslatedMod mod : mods) {
			for (var ii : mod.injectedInterfaces()) added.computeIfAbsent(ii.target(), k -> new ArrayList<>()).add(ii.interfaceName());
			try (SafeArchive archive = SafeArchive.open(mod.jar(), limits.forTranslatedJars())) {
				for (String name : archive.classEntryNames()) {
					byte[] data = archive.read(name);
					if (data != null) mixinAdditions(data, added);
				}
			} catch (IOException | RuntimeException | StackOverflowError e) {
				// The mod is reported by check() if it cannot be read; nothing to add here.
			}
		}
		return added;
	}

	/**
	 * Adds what a mixin class adds to each of its targets: the interfaces it implements, and the fields and methods
	 * it merges into them (not shadows, uniques, injectors or accessors), in {@link ClassIndex}'s
	 * {@code name:desc} / {@code name(args)ret} form. Ignores non-mixin classes.
	 */
	public static void mixinAdditions(byte[] data, Map<String, List<String>> added) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		AnnotationNode mixin = Annotations.find(node.invisibleAnnotations, Annotations.MIXIN);
		if (mixin == null) return;
		List<String> additions = new ArrayList<>(node.interfaces == null ? List.of() : node.interfaces);
		for (FieldNode field : node.fields) {
			if (merged(field.access, field.visibleAnnotations, field.invisibleAnnotations)) additions.add(field.name + ":" + field.desc);
		}
		for (MethodNode method : node.methods) {
			if (!method.name.startsWith("<") && merged(method.access, method.visibleAnnotations, method.invisibleAnnotations)) {
				additions.add(method.name + method.desc);
			}
		}
		if (additions.isEmpty()) return;
		for (String key : List.of("value", "targets")) {
			if (!(Annotations.value(mixin, key) instanceof List<?> list)) continue;
			for (Object target : list) {
				String internal = target instanceof Type type ? type.getInternalName()
						: target instanceof String str ? str.replace('.', '/') : null;
				if (internal != null) added.computeIfAbsent(internal, k -> new ArrayList<>()).addAll(additions);
			}
		}
	}

	/** Whether a mixin member is merged into the target as a new member: not synthetic, and without mixin annotations. */
	private static boolean merged(int access, List<AnnotationNode> visible, List<AnnotationNode> invisible) {
		if ((access & Opcodes.ACC_SYNTHETIC) != 0) return false;
		for (List<AnnotationNode> annotations : List.of(visible == null ? List.<AnnotationNode>of() : visible,
				invisible == null ? List.<AnnotationNode>of() : invisible)) {
			for (AnnotationNode annotation : annotations) {
				if (annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/") || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/")) return false;
			}
		}
		return true;
	}

	private List<String> readCached(String blob) {
		try {
			Optional<byte[]> data = cache.readBlob(blob);
			if (data.isEmpty()) return null;
			Object parsed = Json.parse(new String(data.get(), StandardCharsets.UTF_8));
			if (!(parsed instanceof List<?> list)) return null;
			List<String> ret = new ArrayList<>();
			for (Object o : list) ret.add(String.valueOf(o));
			return ret;
		} catch (IOException | JsonException e) {
			return null;
		}
	}

	/** The mod's linkage issues; an unsafe jar is one, as it is every time. Other read errors are thrown. */
	private List<String> check(TranslatedMod mod, LinkageChecker checker) throws IOException {
		List<LinkageChecker.Issue> issues = new ArrayList<>();
		// The overlay's copy of a class (rewritten during translation) is the one that loads.
		try (SafeArchive archive = SafeArchive.open(mod.jar(), limits.forTranslatedJars());
				SafeArchive overlay = SafeArchive.open(mod.overlay(), limits.forTranslatedJars())) {
			for (String name : archive.classEntryNames()) {
				byte[] data = overlay.contains(name) ? overlay.read(name) : archive.read(name);
				if (data != null) checker.check(data, issues);
			}
		} catch (ArchiveException e) {
			return List.of("could not be analysed: " + e.getMessage());
		}
		return issues.stream().map(LinkageChecker.Issue::toString).toList();
	}
}
