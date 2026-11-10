package io.github.cmdralph.fabricated.core.mixin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.jar.Manifest;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * The mixins of the platform's own mods into the classes of the Fabric mods Fabricated translates. A mod whose one jar
 * serves both loaders hooks another mod's classes, on each loader, as that mod's build for the loader declares them:
 * ImmediatelyFast's Forge side hooks AppleSkin's HUD with a static {@code @Inject} handler, written against AppleSkin's
 * Forge build, whose {@code drawExhaustionOverlay} is static. AppleSkin's Fabric build, which Fabricated translates,
 * declares it an instance method, as ImmediatelyFast's Fabric side expects, and SpongePowered's Mixin 0.8.5, which
 * Forge 47 runs, takes an {@code @Inject} only where its handler is static exactly where its target is: it failed the
 * class, which stopped Forge 47's client at its first tick (CORPUS-87). From 0.8.6 Mixin takes a static handler for an
 * instance method, as Fabric's does.
 *
 * <p>Where the running Mixin refuses it, a static {@code @Inject} handler of such a mixin, whose every target method in
 * the translated classes is an instance method, is applied as an instance handler ({@link #instanceHandlers}, then
 * {@link #applyInstanceHandlers} as Mixin reads the mixin): a static method never reads {@code this}, so with its locals
 * one slot further it does what it did, and it is how the mod's own Fabric side declares the same hook. A handler the
 * mixin itself calls, one Mixin finds its target for through the mod's refmap, or one whose target a pattern selects is
 * left as it is, as is every other injector.
 */
public final class NativeModMixins {
	/** The manifest attribute Forge's Mixin reads a mod's mixin configs from. */
	static final String MIXIN_CONFIGS = "MixinConfigs";
	private static final String MANIFEST = "META-INF/MANIFEST.MF";

	private NativeModMixins() {
	}

	/** A file of a jar, by path, or null where it has none. */
	@FunctionalInterface
	public interface Files {
		byte[] read(String path) throws IOException;
	}

	/**
	 * The mixin classes a platform mod's jar lists for this side, by internal name, in order: on Forge, whose Mixin reads a
	 * mod's mixin configs from its manifest's {@code MixinConfigs}, not from its mods.toml. None where the jar's files
	 * can't be read (its mod's loader reports that).
	 */
	public static List<String> manifestMixins(Files files, boolean clientSide) {
		try {
			return listed(files, manifestConfigs(files.read(MANIFEST)), clientSide);
		} catch (IOException e) {
			return List.of();
		}
	}

	/** The mixin configs a jar's manifest names ({@code MixinConfigs}, which Forge's Mixin reads), in order. */
	static List<String> manifestConfigs(byte[] manifest) {
		if (manifest == null) return List.of();
		try {
			String value = new Manifest(new ByteArrayInputStream(manifest)).getMainAttributes().getValue(MIXIN_CONFIGS);
			if (value == null) return List.of();
			List<String> configs = new ArrayList<>();
			for (String config : value.split(",")) {
				if (!config.isBlank()) configs.add(config.trim());
			}
			return configs;
		} catch (IOException | RuntimeException e) {
			// The platform refuses a jar with a broken manifest itself.
			return List.of();
		}
	}

	/**
	 * The mixin classes the configs list for this side, by internal name, in order: those of a config the jar doesn't
	 * have, or that doesn't parse, are left out (its mod's loader reports it).
	 */
	static List<String> listed(Files files, Collection<String> configs, boolean clientSide) {
		Set<String> mixins = new LinkedHashSet<>();
		for (String path : configs) {
			try {
				byte[] data = files.read(path);
				if (data == null) continue;
				MixinConfigFile config = MixinConfigFile.parse(new String(data, StandardCharsets.UTF_8));
				for (String mixin : config.mixinsFor(clientSide)) mixins.add(config.internalName(mixin));
			} catch (IOException | JsonException | RuntimeException e) {
				// As above.
			}
		}
		return List.copyOf(mixins);
	}

	/**
	 * The {@code @Inject} handlers of the platform's mods' mixins ({@link InstalledClasses#nativeMixins}) to apply as
	 * instance handlers ({@link NativeModMixins}): by mixin class, each handler's name and descriptor. What is applied,
	 * and each such handler left as it is, is described to {@code notes}.
	 *
	 * @param installed the installed jars, the Fabric mods' with their classes as they will load
	 */
	public static Map<String, Set<String>> instanceHandlers(InstalledClasses installed, Consumer<String> notes) {
		Map<String, Set<String>> handlers = new TreeMap<>();
		Map<String, ClassNode> fabricClasses = new HashMap<>();
		for (InstalledClasses.ListedMixin listed : installed.nativeMixins()) {
			ClassNode outline = listed.classes().readNode(listed.mixin(), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			if (outline == null || (outline.access & Opcodes.ACC_INTERFACE) != 0) continue;
			List<String> targets = InstalledClasses.mixinTargets(outline);
			if (targets.isEmpty() || !targets.stream().allMatch(installed::isFabricMods)) continue;
			List<MethodNode> candidates = outline.methods.stream()
					.filter(m -> (m.access & Opcodes.ACC_STATIC) != 0 && Annotations.find(m, MixinNames.INJECT) != null).toList();
			if (candidates.isEmpty()) continue;
			List<ClassNode> targetNodes = new ArrayList<>();
			for (String target : targets) {
				ClassNode node = fabricClasses.computeIfAbsent(target, t -> installed.readNode(t, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES));
				if (node != null) targetNodes.add(node);
			}
			if (targetNodes.size() != targets.size()) continue;
			ClassNode mixin = null;
			for (MethodNode handler : candidates) {
				List<String> instanceTargets = instanceTargets(handler, targetNodes);
				if (instanceTargets.isEmpty()) continue;
				String where = "the static @Inject handler " + handler.name + " of " + listed.mixin().replace('/', '.') + " (" + listed.owner() + ") hooks "
						+ String.join(", ", instanceTargets) + ", which " + installed.owner(targets.get(0)) + "'s Fabric build declares an instance method";
				String kept = keptBecause(outline, handler);
				if (kept == null) {
					if (mixin == null) mixin = listed.classes().readNode(listed.mixin(), ClassReader.SKIP_FRAMES);
					if (mixin == null) continue;
					if (callsItself(mixin, handler)) kept = "the mixin calls it itself";
				}
				if (kept != null) {
					notes.accept(where + "; left as it is, as " + kept + ", so Mixin refuses it (CORPUS-87)");
					continue;
				}
				handlers.computeIfAbsent(listed.mixin(), k -> new TreeSet<>()).add(handler.name + handler.desc);
				notes.accept(where + "; Fabricated applies it as an instance handler, which Mixin takes there, as the mod's Fabric side declares it (CORPUS-87)");
			}
		}
		handlers.replaceAll((mixin, set) -> Set.copyOf(set));
		return Map.copyOf(handlers);
	}

	/**
	 * The methods the handler's {@code @Inject} selects in the targets, as {@code Class::method}, where each of them is
	 * an instance method; none where it selects any static one, or none at all, or selects by a pattern (Mixin then
	 * decides, as without this).
	 */
	private static List<String> instanceTargets(MethodNode handler, List<ClassNode> targets) {
		AnnotationNode inject = Annotations.find(handler, MixinNames.INJECT);
		if (!Annotations.list(Annotations.value(inject, "target")).isEmpty()) return List.of();
		if (!(Annotations.value(inject, "method") instanceof List<?> selectors) || selectors.isEmpty()) return List.of();
		List<String> found = new ArrayList<>();
		for (ClassNode target : targets) {
			for (Object o : selectors) {
				if (!(o instanceof String raw)) return List.of();
				MemberRef ref = MemberRef.parse(raw);
				if (ref.fuzzy() || ref.name() == null || (ref.owner() != null && !ref.owner().equals(target.name))) return List.of();
				for (MethodNode m : target.methods) {
					// Every method of the name, where Mixin takes the first: what loads may lack one (@Environment) or have one more (a mixin's).
					if (!ref.matchesMethod(m.name, m.desc)) continue;
					if ((m.access & Opcodes.ACC_STATIC) != 0) return List.of();
					found.add(target.name.replace('/', '.') + "::" + m.name);
				}
			}
		}
		return found.stream().distinct().toList();
	}

	/** Why the handler is left as it is, or null: what Mixin decides from what Fabricated doesn't read. */
	private static String keptBecause(ClassNode mixin, MethodNode handler) {
		if ((handler.access & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return "it is synchronized or has no code";
		AnnotationNode inject = Annotations.find(handler, MixinNames.INJECT);
		AnnotationNode annotation = Annotations.find(mixin.visibleAnnotations, mixin.invisibleAnnotations, Annotations.MIXIN);
		// Mixin finds the target of an injector it remaps through the mod's refmap, which may name another method.
		if (!Boolean.FALSE.equals(Annotations.value(inject, "remap")) && (annotation == null || !Boolean.FALSE.equals(Annotations.value(annotation, "remap")))) {
			return "Mixin finds its target through the mod's refmap";
		}
		return null;
	}

	/** Whether the mixin's code calls the handler or takes a handle of it, which an instance method would break. */
	private static boolean callsItself(ClassNode mixin, MethodNode handler) {
		for (MethodNode m : mixin.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && call.name.equals(handler.name) && call.desc.equals(handler.desc)) return true;
				if (insn instanceof InvokeDynamicInsnNode indy) {
					for (Object arg : indy.bsmArgs) {
						if (isHandle(arg, mixin, handler)) return true;
					}
				}
				if (insn instanceof LdcInsnNode ldc && isHandle(ldc.cst, mixin, handler)) return true;
			}
		}
		return false;
	}

	private static boolean isHandle(Object value, ClassNode mixin, MethodNode handler) {
		return value instanceof Handle h && h.getOwner().equals(mixin.name) && h.getName().equals(handler.name) && h.getDesc().equals(handler.desc);
	}

	/**
	 * Makes the mixin's static methods named (name and descriptor) instance methods, as Mixin reads the mixin: each local
	 * one slot further, {@code this} first in every full frame. Whether the class changed.
	 */
	public static boolean applyInstanceHandlers(ClassNode mixin, Set<String> handlers) {
		boolean changed = false;
		for (MethodNode m : mixin.methods) {
			if ((m.access & Opcodes.ACC_STATIC) == 0 || !handlers.contains(m.name + m.desc)) continue;
			toInstanceMethod(mixin, m);
			changed = true;
		}
		return changed;
	}

	private static void toInstanceMethod(ClassNode owner, MethodNode m) {
		m.access &= ~Opcodes.ACC_STATIC;
		for (AbstractInsnNode insn : m.instructions) {
			if (insn instanceof VarInsnNode v) v.var++;
			else if (insn instanceof IincInsnNode i) i.var++;
			else if (insn instanceof FrameNode f && (f.type == Opcodes.F_NEW || f.type == Opcodes.F_FULL)) {
				// A frame that lists its locals lists them from slot 0; the others (same, append, chop) build on the
				// method's first frame, which now has this.
				List<Object> locals = new ArrayList<>();
				locals.add(owner.name);
				if (f.local != null) locals.addAll(f.local);
				f.local = locals;
			}
		}
		if (m.localVariables != null) {
			for (LocalVariableNode local : m.localVariables) local.index++;
		}
		shift(m.visibleLocalVariableAnnotations);
		shift(m.invisibleLocalVariableAnnotations);
		m.maxLocals++;
	}

	private static void shift(List<LocalVariableAnnotationNode> annotations) {
		if (annotations == null) return;
		for (LocalVariableAnnotationNode a : annotations) a.index.replaceAll(i -> i + 1);
	}
}
