package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.accesswidener.AccessTransformerConverter;
import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

/**
 * CORPUS-64: a Fabric mod's read of a game field the platform keeps private becomes a call of the getter the platform
 * reads it through, as the game's own reads are ({@link PlatformDescriptor#privateFields}: NeoForge's coremods replace
 * {@code Biome.climateSettings}' reads with {@code getModifiedClimateSettings()}, the climate after biome modifiers).
 * A mod's access widener can't widen such a field, as the platform's loader then refuses the class
 * ({@link AccessTransformerConverter}), so the mod's read threw {@code IllegalAccessError} where it ran: Fabric
 * Seasons' biome temperature hook read {@code Biome.climateSettings} and crashed the 1.21.1 client in its world.
 *
 * <p>Only where the game's class has the getter, returning the field's type, as the platform's rule is. Left as they
 * are: a write of the field (the getter has no setter; it fails where it runs, as before), and the code of the mod's
 * mixins into the field's class, which reads the field from inside that class once applied (and where a mixin may be
 * the getter itself).
 *
 * <p>CORPUS-107: a read of a field the platform gave another type, by vanilla's type, likewise calls the getter
 * Fabricated's in-game mod adds for it, which returns what vanilla's field holds as vanilla's type
 * ({@link TranslationEnvironment#bridgedFields}), as the mod's accessors of the field do (CORPUS-37). Forge 47 keeps
 * holders in a brewing mix's {@code from} and {@code to}, and REI's default client plugin, reading {@code from} itself,
 * failed with {@code NoSuchFieldError}, so none of its later displays registered. Only where the game's class has no
 * field of that name and the mod's type, and has the getter returning that type.
 */
final class PrivateFieldReads {
	/**
	 * A field read through its getter: the field's owner, name and type as the mod reads it, the getter's name, and
	 * whether the getter is Fabricated's bridge of a field the platform retyped.
	 */
	record Read(String owner, String name, String desc, String getter, boolean bridge) {
		String key() {
			return key(owner, name, desc);
		}

		static String key(String owner, String name, String desc) {
			return owner + "." + name + ":" + desc;
		}
	}

	private final ClassIndex game;
	private final Map<String, Read> reads;

	private PrivateFieldReads(ClassIndex game, Map<String, Read> reads) {
		this.game = game;
		this.reads = reads;
	}

	/**
	 * The private fields the game's classes have with their getters, and the retyped fields with their bridges.
	 *
	 * @param privateFields {@code owner field} in runtime names to the getter's name ({@link PlatformDescriptor#privateFields})
	 * @param bridgedFields {@code owner field} in runtime names to the bridge's name ({@link TranslationEnvironment#bridgedFields})
	 */
	static PrivateFieldReads of(ClassIndex game, Map<String, String> privateFields, Map<String, String> bridgedFields) {
		Map<String, Read> reads = new HashMap<>();
		if (game != null) {
			privateFields.forEach((field, getter) -> {
				int space = field.indexOf(' ');
				String owner = field.substring(0, space);
				String name = field.substring(space + 1);
				ClassIndex.ClassInfo info = game.get(owner);
				if (info == null) return;
				for (String member : info.fields().keySet()) {
					if (!member.startsWith(name + ":")) continue;
					String desc = member.substring(name.length() + 1);
					if (info.methodAccess(getter + "()" + desc) != null) {
						Read read = new Read(owner, name, desc, getter, false);
						reads.put(read.key(), read);
					}
				}
			});
			bridgedFields.forEach((field, bridge) -> {
				int space = field.indexOf(' ');
				String owner = field.substring(0, space);
				String name = field.substring(space + 1);
				ClassIndex.ClassInfo info = game.get(owner);
				if (info == null) return;
				String prefix = bridge + "()";
				info.methods().forEach((method, access) -> {
					if (!method.startsWith(prefix) || (access & Opcodes.ACC_STATIC) != 0 || (access & Opcodes.ACC_PUBLIC) == 0) return;
					String desc = method.substring(prefix.length());
					// The platform's field keeps the mod's type here: nothing to bridge.
					if (info.fieldAccess(name + ":" + desc) != null) return;
					Read read = new Read(owner, name, desc, bridge, true);
					reads.put(read.key(), read);
				});
			});
		}
		return new PrivateFieldReads(game, Map.copyOf(reads));
	}

	boolean isEmpty() {
		return reads.isEmpty();
	}

	/**
	 * Rewrites the reads in the mod's classes, putting each class it changes in {@code target}.
	 *
	 * @param target  where the mod's classes as translated are, and the changed ones go
	 * @param entries the mod's file names
	 * @param read    the mod's files as they are, for one {@code target} doesn't have
	 * @return the report's notes
	 */
	List<String> apply(Map<String, byte[]> target, Iterable<String> entries, ModTranslator.FileView read) throws ArchiveException {
		if (reads.isEmpty()) return List.of();
		List<String> names = new ArrayList<>();
		entries.forEach(names::add);
		List<byte[]> fieldNames = reads.values().stream().map(r -> r.name().getBytes(StandardCharsets.UTF_8)).toList();
		Map<String, Set<String>> readers = new TreeMap<>();
		for (String name : names) {
			if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
			byte[] data = target.containsKey(name) ? target.get(name) : read.read(name);
			// Only a class naming a field's name can read it.
			if (data == null || fieldNames.stream().noneMatch(field -> Annotations.mentions(data, field))) continue;
			ClassNode node = new ClassNode();
			try {
				new ClassReader(data).accept(node, 0);
			} catch (RuntimeException e) {
				continue;
			}
			if (rewrite(node, readers)) {
				// The getter's call takes the read's place on the stack, so the frames and maximums stand.
				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				target.put(name, writer.toByteArray());
			}
		}
		List<String> notes = new ArrayList<>();
		readers.forEach((field, classes) -> {
			Read r = reads.get(field);
			notes.add(classes.size() + " class(es) read " + r.owner().replace('/', '.') + "." + r.name() + (r.bridge()
					? ", which the platform gave another type; they read it as vanilla's type through Fabricated's " + r.getter() + "(): "
					: ", which the platform keeps private; they read it through its " + r.getter() + "(), as the game's own code does: ")
					+ String.join(", ", classes));
		});
		return notes;
	}

	/**
	 * The private field an instruction reads, found as the JVM finds it: in the class it names, or a superclass that
	 * doesn't declare the field itself (a read through a subclass of {@code Structure}, or in one of the mod's, names
	 * the subclass). Null when it reads none.
	 */
	private Read resolve(ClassNode node, FieldInsnNode field) {
		String member = field.name + ":" + field.desc;
		String owner = field.owner;
		for (int depth = 0; owner != null && depth < 64; depth++) {
			Read r = reads.get(Read.key(owner, field.name, field.desc));
			if (r != null) return r;
			ClassIndex.ClassInfo info = game.get(owner);
			if (info == null) {
				// The mod's own class, read here, whose superclass may be the game's.
				if (!owner.equals(node.name) || node.fields.stream().anyMatch(f -> (f.name + ":" + f.desc).equals(member))) return null;
				owner = node.superName;
				continue;
			}
			if (info.fields().containsKey(member)) return null;
			owner = info.superName();
		}
		return null;
	}

	/** Rewrites {@code node}'s reads, adding it to {@code readers} by field; whether it changed. */
	boolean rewrite(ClassNode node, Map<String, Set<String>> readers) {
		List<String> mixinTargets = ModTranslator.mixinTargets(node);
		Function<String, Set<String>> classes = k -> new TreeSet<>();
		boolean changed = false;
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn.getOpcode() != Opcodes.GETFIELD) continue;
				FieldInsnNode field = (FieldInsnNode) insn;
				Read r = resolve(node, field);
				if (r == null || mixinTargets.contains(r.owner())) continue;
				method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, r.owner(), r.getter(), "()" + r.desc(), false));
				readers.computeIfAbsent(r.key(), classes).add(node.name.replace('/', '.'));
				changed = true;
			}
		}
		return changed;
	}
}
