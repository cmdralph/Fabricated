package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.io.InputStream;
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
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.MixinEntry;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Runs the {@link LinkageChecker} over every translated mod against an index of everything that will
 * exist at runtime (game classes, Fabricated's native Fabric API, and all Fabric jars). Results are
 * cached per mod, keyed by the mod and a fingerprint of the whole mod set.
 */
public final class LinkageAnalysis {
	/**
	 * Owners verified: Minecraft, Blaze3D, the Fabric API namespace and Fabric Loader's, whose classes Fabricated
	 * provides itself (its public API and the internals mods commonly use), so a mod reaching for any other loader
	 * internal is reported before it fails.
	 */
	public static final Predicate<String> CHECKED = name -> name.startsWith("net/minecraft/") || name.startsWith("com/mojang/blaze3d/")
			|| name.startsWith("net/fabricmc/fabric/") || isLoaderClass(name) && LoaderClasses.READABLE;

	/** Fabricated's own Fabric Loader classes, which load next to core (from its jar). */
	static final ClassBytesSource LOADER_CLASSES = name -> {
		if (!isLoaderClass(name)) return null;
		try (InputStream in = FabricLoader.class.getClassLoader().getResourceAsStream(name + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	};

	/** Whether Fabricated's loader classes can be read here; if not, loader references aren't checked at all. */
	private static final class LoaderClasses {
		static final boolean READABLE = readable();

		private static boolean readable() {
			try {
				return LOADER_CLASSES.read(Type.getInternalName(FabricLoader.class)) != null;
			} catch (IOException e) {
				return false;
			}
		}
	}

	/** Ends an issue of a mixin that needs a Fabric mod that isn't loading (see {@link #isUnloadedUse}). */
	private static final String UNLOADED = ", which isn't loading";

	/**
	 * Whether a linkage issue is a required mixin calling into a class only a Fabric mod that isn't loading has, or a
	 * mixin adding such an interface to its targets: Mixin can't find that class while it applies the mixin, or the
	 * game can't define the target then, which stops the game.
	 */
	public static boolean isUnloadedUse(String issue) {
		return issue.endsWith(UNLOADED) && issue.contains(" -> needs class ");
	}

	/** Whether a linkage issue is a missing piece of Fabric Loader (as reported by {@link LinkageChecker.Issue}). */
	public static boolean isLoaderIssue(String issue) {
		int missing = issue.indexOf(" -> missing ");
		String reference = missing < 0 ? issue : issue.substring(missing);
		return reference.contains(" net.fabricmc.loader.") || reference.contains(" net.fabricmc.api.");
	}

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
		return analyse(mods, runtime, platformInterfaces, setFingerprint, Map.of(), name -> false);
	}

	/**
	 * {@link #analyse(List, ClassBytesSource, Supplier, String)}, also finding the mixins that need a Fabric mod that
	 * isn't loading ({@link #isUnloadedUse}).
	 *
	 * @param unloaded       packages that only Fabric mods that aren't loading have, each with one such mod's id
	 *                       ({@code setFingerprint} must cover them)
	 * @param installedClass whether an installed NeoForge mod has a class, which then exists after all
	 */
	public Map<String, List<String>> analyse(List<TranslatedMod> mods, ClassBytesSource runtime,
			Supplier<Map<String, List<String>>> platformInterfaces, String setFingerprint, Map<String, String> unloaded,
			Predicate<String> installedClass) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		LinkageChecker checker = null;
		ClassIndex index = null;
		for (TranslatedMod mod : mods) {
			String blob = "linkage-" + Hashing.sha256("v6|" + mod.candidate().sha256() + "|" + setFingerprint + "|" + (clientSide ? "c" : "s")).substring(0, 40) + ".json";
			List<String> issues = readCached(blob);
			if (issues == null) {
				// Built only when some result is not cached: it reads every mod's classes once.
				if (checker == null) {
					Map<String, List<String>> added = addedInterfaces(mods);
					platformInterfaces.get().forEach((target, ifaces) -> added.computeIfAbsent(target, k -> new ArrayList<>()).addAll(ifaces));
					index = new ClassIndex(runtime.or(LOADER_CLASSES), added);
					checker = new LinkageChecker(index, CHECKED, clientSide);
				}
				try {
					ClassIndex present = index;
					issues = check(mod, checker, unloaded, name -> present.contains(name) || installedClass.test(name));
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
	private List<String> check(TranslatedMod mod, LinkageChecker checker, Map<String, String> unloaded, Predicate<String> exists) throws IOException {
		List<LinkageChecker.Issue> issues = new ArrayList<>();
		List<String> unloadedUses = new ArrayList<>();
		// The overlay's copy of a class (rewritten during translation) is the one that loads.
		try (SafeArchive archive = SafeArchive.open(mod.jar(), limits.forTranslatedJars());
				SafeArchive overlay = SafeArchive.open(mod.overlay(), limits.forTranslatedJars())) {
			for (String name : archive.classEntryNames()) {
				byte[] data = overlay.contains(name) ? overlay.read(name) : archive.read(name);
				if (data != null) checker.check(data, issues);
			}
			if (!unloaded.isEmpty()) unloadedUses(mod, archive, overlay, unloaded, exists, unloadedUses);
		} catch (ArchiveException e) {
			return List.of("could not be analysed: " + e.getMessage());
		}
		List<String> all = new ArrayList<>(unloadedUses);
		issues.forEach(i -> all.add(i.toString()));
		return all;
	}

	/**
	 * The required mixins (their config required, without a plugin that could skip them) that call a method of, or use
	 * a field of, a class only a Fabric mod that isn't loading has: Mixin looks that class up while it applies the
	 * mixin, and stops the game when it can't find it (Continuity's mixin into the renderer API, run 362). And any
	 * mixin adding such an interface to its targets (LINK-2): Mixin only warns that it can't load the interface, and the
	 * game then can't define the target class, whether the config is required or not (Porting Lib's base module's
	 * texture mixin on Forge 47 clients, whose interface is its extensions module's). Its config's plugin could skip it,
	 * but where it doesn't the game can't start, while the mod disabled says why and loads once the other one does.
	 */
	private void unloadedUses(TranslatedMod mod, SafeArchive archive, SafeArchive overlay, Map<String, String> unloaded, Predicate<String> exists,
			List<String> out) throws ArchiveException {
		FabricModMetadata md = mod.candidate().metadata();
		EnvType side = clientSide ? EnvType.CLIENT : EnvType.SERVER;
		for (MixinEntry entry : md.compatHints().mixinsOr(md.mixins())) {
			if (!entry.environment().matches(side)) continue;
			byte[] json = overlay.contains(entry.config()) ? overlay.read(entry.config()) : archive.read(entry.config());
			if (json == null) continue;
			MixinConfigFile config;
			try {
				config = MixinConfigFile.parse(new String(json, StandardCharsets.UTF_8));
			} catch (JsonException e) {
				continue; // translation reported it
			}
			boolean strict = config.required() && config.plugin() == null;
			for (String mixin : config.mixinsFor(clientSide)) {
				String name = config.internalName(mixin);
				byte[] data = overlay.contains(name + ".class") ? overlay.read(name + ".class") : archive.read(name + ".class");
				ClassNode node = data == null ? null : readNode(data);
				if (node == null) continue;
				String owner = strict ? unloadedOwner(node, unloaded, exists) : null;
				if (owner != null) {
					out.add(name.replace('/', '.') + " -> needs class " + owner.replace('/', '.') + " of " + unloaded.get(packageOf(owner)) + UNLOADED);
					continue;
				}
				String added = unloadedInterface(node, unloaded, exists);
				if (added != null) {
					out.add(name.replace('/', '.') + " adds an interface to its targets -> needs class " + added.replace('/', '.') + " of "
							+ unloaded.get(packageOf(added)) + UNLOADED);
				}
			}
		}
	}

	/** The mixin's class, or null when it can't be read (translation reported that). */
	private static ClassNode readNode(byte[] data) {
		ClassNode node = new ClassNode();
		try {
			new ClassReader(data).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		} catch (RuntimeException | StackOverflowError e) {
			return null;
		}
		return node;
	}

	/** The first interface a mixin adds to its targets that only a mod that isn't loading has, or null. */
	private static String unloadedInterface(ClassNode node, Map<String, String> unloaded, Predicate<String> exists) {
		for (String added : node.interfaces) {
			if (unloaded.containsKey(packageOf(added)) && !exists.test(added)) return added;
		}
		return null;
	}

	/** The first class a mixin's code calls into or reads from that only a mod that isn't loading has, or null. */
	private static String unloadedOwner(ClassNode node, Map<String, String> unloaded, Predicate<String> exists) {
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				String owner = insn instanceof MethodInsnNode m ? m.owner : insn instanceof FieldInsnNode f ? f.owner : null;
				if (owner != null && !owner.startsWith("[") && unloaded.containsKey(packageOf(owner)) && !exists.test(owner)) return owner;
			}
		}
		return null;
	}

	private static String packageOf(String internalName) {
		int slash = internalName.lastIndexOf('/');
		return slash < 0 ? "" : internalName.substring(0, slash);
	}

	private static boolean isLoaderClass(String name) {
		return name.startsWith("net/fabricmc/loader/") || name.startsWith("net/fabricmc/api/");
	}
}
