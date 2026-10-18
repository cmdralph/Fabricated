package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Lazily parsed, cached view of class structure (names, hierarchy, members and access flags) read
 * from a {@link ClassBytesSource}. Thread-safe; used by the mixin pre-flight, linkage checks and the
 * access widener converter.
 */
public final class ClassIndex {
	private static final ClassInfo MISSING = new ClassInfo("", null, List.of(), Map.of(), Map.of());

	/** Structural information about one class. */
	public record ClassInfo(String name, String superName, List<String> interfaces,
			Map<String, Integer> methods, Map<String, Integer> fields) {
		/** @param nameAndDesc e.g. {@code tick()V} */
		public Integer methodAccess(String nameAndDesc) {
			return methods.get(nameAndDesc);
		}

		/** @param nameAndDesc e.g. {@code level:Lnet/minecraft/world/level/Level;} */
		public Integer fieldAccess(String nameAndDesc) {
			return fields.get(nameAndDesc);
		}
	}

	private final ClassBytesSource source;
	private final Map<String, List<String>> extraInterfaces;
	private final Map<String, ClassInfo> cache = new ConcurrentHashMap<>();

	/**
	 * Reads JDK class files as bytes from the runtime image so hierarchies that reach {@code java.lang.Object}
	 * resolve completely. No JDK class is defined or initialised by this.
	 */
	public static final ClassBytesSource JDK = name -> {
		if (!(name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/"))) return null;
		try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	};

	/** @param source the classes to index; JDK classes are added automatically */
	public ClassIndex(ClassBytesSource source) {
		this(source, Map.of());
	}

	/**
	 * @param extraInterfaces what is added to classes at load time, by target class: interfaces (Fabric's
	 *                        {@code inject-interface}, mixins' interfaces), which take part in member resolution like
	 *                        declared ones, and members mixins add, written {@code name:desc} for fields and
	 *                        {@code name(args)ret} for methods (treated as public)
	 */
	public ClassIndex(ClassBytesSource source, Map<String, List<String>> extraInterfaces) {
		this.source = source.or(JDK);
		this.extraInterfaces = Map.copyOf(extraInterfaces);
	}

	/** @return class info, or {@code null} if the class is not in the source */
	public ClassInfo get(String internalName) {
		ClassInfo info = cache.computeIfAbsent(internalName, this::load);
		return info == MISSING ? null : info;
	}

	public boolean contains(String internalName) {
		return get(internalName) != null;
	}

	/** Full tree (with code) of a class, not cached; {@code null} if absent or unreadable. */
	public ClassNode readNode(String internalName) {
		try {
			byte[] data = source.read(internalName);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, ClassReader.SKIP_FRAMES);
			return node;
		} catch (IOException | RuntimeException | StackOverflowError e) {
			return null;
		}
	}

	private ClassInfo load(String internalName) {
		byte[] data;
		try {
			data = source.read(internalName);
		} catch (IOException e) {
			return MISSING;
		}
		if (data == null) return MISSING;
		try {
			Collector collector = new Collector();
			new ClassReader(data).accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			List<String> interfaces = new ArrayList<>(collector.interfaces == null ? List.of() : List.of(collector.interfaces));
			for (String extra : extraInterfaces.getOrDefault(internalName, List.of())) {
				if (extra.indexOf('(') >= 0) collector.methods.putIfAbsent(extra, Opcodes.ACC_PUBLIC);
				else if (extra.indexOf(':') >= 0) collector.fields.putIfAbsent(extra, Opcodes.ACC_PUBLIC);
				else interfaces.add(extra);
			}
			return new ClassInfo(collector.name, collector.superName, List.copyOf(interfaces),
					Collections.unmodifiableMap(collector.methods), Collections.unmodifiableMap(collector.fields));
		} catch (RuntimeException | StackOverflowError e) {
			// Corrupt class files are treated as absent; callers report the consequence.
			return MISSING;
		}
	}

	/** Result of resolving a member through the class hierarchy. */
	public enum Resolution {
		/** Found on the class or a supertype within the index. */
		FOUND,
		/** Definitely absent: the full hierarchy is inside the index and has no such member. */
		MISSING,
		/** Hierarchy leaves the index (e.g. reaches JDK classes); cannot decide. */
		UNKNOWN
	}

	/** Resolves a method ({@code name + desc}) in {@code owner} or any supertype. */
	public Resolution resolveMethod(String owner, String nameAndDesc) {
		return resolve(owner, info -> info.methods().containsKey(nameAndDesc));
	}

	/** Resolves a field ({@code name:desc}) in {@code owner} or any supertype. */
	public Resolution resolveField(String owner, String nameAndDesc) {
		return resolve(owner, info -> info.fields().containsKey(nameAndDesc));
	}

	private Resolution resolve(String owner, Predicate<ClassInfo> has) {
		Deque<String> queue = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		queue.add(owner);
		boolean leftIndex = false;
		while (!queue.isEmpty()) {
			String name = queue.poll();
			if (!seen.add(name)) continue;
			ClassInfo info = get(name);
			if (info == null) {
				leftIndex = true;
				continue;
			}
			if (has.test(info)) return Resolution.FOUND;
			if (info.superName() != null) queue.add(info.superName());
			queue.addAll(info.interfaces());
		}
		return leftIndex ? Resolution.UNKNOWN : Resolution.MISSING;
	}

	private static final class Collector extends ClassVisitor {
		String name;
		String superName;
		String[] interfaces;
		final Map<String, Integer> methods = new LinkedHashMap<>();
		final Map<String, Integer> fields = new LinkedHashMap<>();

		Collector() {
			super(Opcodes.ASM9);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.name = name;
			this.superName = superName;
			this.interfaces = interfaces;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			methods.put(name + descriptor, access);
			return null;
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			fields.put(name + ":" + descriptor, access);
			return null;
		}
	}
}
