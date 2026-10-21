package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.archive.Hashing;

/**
 * The classes of the jars installed next to a Fabric mod (NeoForge mods, Fabricated's own Fabric API, and on 26.x the
 * other Fabric mods), which its mixins may target: an addon's mixins into the mod it extends. Jars are searched in
 * order; the first one with a class owns it. A class is read once (only the few classes mixins target are), so this is
 * safe to share.
 */
public final class InstalledClasses implements ClassBytesSource {
	/** No jars. */
	public static final InstalledClasses NONE = new InstalledClasses(List.of());

	/**
	 * One jar.
	 *
	 * @param owner    the name of the mod it belongs to, for messages
	 * @param contains whether it has a class, by internal name (without reading it)
	 * @param mixins   the mixin classes its configs list for this side, by internal name
	 */
	public record Jar(String owner, Predicate<String> contains, ClassBytesSource classes, Supplier<List<String>> mixins) {
		/** A jar without mixins. */
		public Jar(String owner, Predicate<String> contains, ClassBytesSource classes) {
			this(owner, contains, classes, List::of);
		}
	}

	/** A field or method a mixin of an installed jar adds to its target: the mod's name and the mixin class's internal name. */
	public record AddedMember(String owner, String mixin) {
	}

	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	/** Packages of the injector annotations: Mixin gives their handlers a name of its own as it merges them. */
	private static final List<String> INJECTOR_PACKAGES = List.of("Lorg/spongepowered/asm/mixin/injection/", "Lcom/llamalad7/mixinextras/injector/");

	private final List<Jar> jars;
	private final Map<String, Optional<Jar>> owners = new ConcurrentHashMap<>();
	private final Map<String, byte[]> read = new ConcurrentHashMap<>();
	/** By target class, the members the jars' mixins add, keyed {@code name:desc} for fields and {@code name(desc)} for methods. */
	private Map<String, Map<String, AddedMember>> added;

	public InstalledClasses(List<Jar> jars) {
		this.jars = List.copyOf(jars);
	}

	public boolean isEmpty() {
		return jars.isEmpty();
	}

	/** These jars, then {@code more}. */
	public InstalledClasses with(List<Jar> more) {
		if (more.isEmpty()) return this;
		List<Jar> all = new ArrayList<>(jars);
		all.addAll(more);
		return new InstalledClasses(all);
	}

	/** The name of the mod whose jar has the class, or {@code null} when no installed jar has it. */
	public String owner(String internalName) {
		Jar jar = jar(internalName);
		return jar == null ? null : jar.owner();
	}

	@Override
	public byte[] read(String internalName) throws IOException {
		Jar jar = jar(internalName);
		if (jar == null) return null;
		byte[] cached = read.get(internalName);
		if (cached != null) return cached;
		byte[] data = jar.classes().read(internalName);
		if (data != null) read.put(internalName, data);
		return data;
	}

	/** SHA-256 of the class file, or an empty string when no jar has it: what a cached pre-flight result depends on. */
	public String fingerprint(String internalName) throws IOException {
		byte[] data = read(internalName);
		return data == null ? "" : Hashing.sha256(data);
	}

	/**
	 * The mixin of an installed jar that adds a field, or a method, of this name and descriptor to a class, or null; a
	 * method's descriptor may be null for any. Mixin merges the members of every mixin into a class before an injector
	 * looks for its target method (its applicator's main pass comes before the injector passes), so a mixin may shadow or
	 * inject into what another mod's mixin adds: owo's creative screen mixin hooks a method Fabric API's mixin adds to
	 * the screen. Built on the first call, from every mixin the jars list.
	 */
	public AddedMember addedMember(String target, String name, String desc, boolean field) {
		Map<String, AddedMember> members = added().get(target);
		if (members == null) return null;
		if (field || desc != null) return members.get(field ? name + ":" + desc : name + desc);
		for (Map.Entry<String, AddedMember> e : members.entrySet()) {
			if (e.getKey().startsWith(name + "(")) return e.getValue();
		}
		return null;
	}

	private synchronized Map<String, Map<String, AddedMember>> added() {
		if (added != null) return added;
		Map<String, Map<String, AddedMember>> index = new HashMap<>();
		for (Jar jar : jars) {
			for (String mixin : jar.mixins().get()) {
				ClassNode node = readMixin(jar, mixin);
				if (node == null) continue;
				List<String> members = new ArrayList<>();
				for (FieldNode f : node.fields) {
					if (!annotated(f.invisibleAnnotations, false) && !annotated(f.visibleAnnotations, false)) members.add(f.name + ":" + f.desc);
				}
				for (MethodNode m : node.methods) {
					if (m.name.startsWith("<") || annotated(m.invisibleAnnotations, true) || annotated(m.visibleAnnotations, true)) continue;
					members.add(m.name + m.desc);
				}
				AddedMember adder = new AddedMember(jar.owner(), mixin);
				for (String target : targets(node)) {
					Map<String, AddedMember> byKey = index.computeIfAbsent(target, t -> new HashMap<>());
					for (String member : members) byKey.putIfAbsent(member, adder);
				}
			}
		}
		added = index;
		return added;
	}

	/** A mixin class of the jar, without its code; null when it is missing or unreadable (translating its mod reports that). */
	private static ClassNode readMixin(Jar jar, String mixin) {
		try {
			byte[] data = jar.classes().read(mixin);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			return node;
		} catch (IOException | RuntimeException e) {
			return null; // the bytes are untrusted: ASM throws on malformed ones
		}
	}

	/** Whether a member is a {@code @Shadow} (it adds nothing) or, for a method, an injector handler (Mixin renames those). */
	private static boolean annotated(List<AnnotationNode> annotations, boolean method) {
		if (annotations == null) return false;
		for (AnnotationNode a : annotations) {
			if (a.desc.equals(SHADOW)) return true;
			if (method && INJECTOR_PACKAGES.stream().anyMatch(a.desc::startsWith)) return true;
		}
		return false;
	}

	/** The classes a mixin names in its {@code @Mixin} annotation, by internal name. */
	private static List<String> targets(ClassNode mixin) {
		List<String> targets = new ArrayList<>();
		if (mixin.invisibleAnnotations == null) return targets;
		for (AnnotationNode a : mixin.invisibleAnnotations) {
			if (!a.desc.equals(MIXIN) || a.values == null) continue;
			for (int i = 0; i + 1 < a.values.size(); i += 2) {
				if (!(a.values.get(i + 1) instanceof List<?> list)) continue;
				for (Object o : list) {
					if (o instanceof Type t && t.getSort() == Type.OBJECT) targets.add(t.getInternalName());
					else if (o instanceof String name && "targets".equals(a.values.get(i))) targets.add(name.replace('.', '/'));
				}
			}
		}
		return targets;
	}

	private Jar jar(String internalName) {
		// Names come from mixin annotations of untrusted jars: only a class name reaches a jar (no "..", no absolute path),
		// as a NeoForge mod in development is a folder.
		if (!isClassName(internalName)) return null;
		return owners.computeIfAbsent(internalName, n -> jars.stream().filter(j -> j.contains().test(n)).findFirst()).orElse(null);
	}

	/** Whether {@code name} is a well-formed internal class name ({@code a/b/C$D}). */
	static boolean isClassName(String name) {
		if (name.isEmpty() || name.length() > 1024 || name.startsWith("/") || name.endsWith("/")) return false;
		for (String part : name.split("/", -1)) {
			if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '\\' || c == ':' || c == '.' || c == ';' || c == '[' || c < 0x20) return false;
		}
		return true;
	}
}
