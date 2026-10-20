package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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
	 */
	public record Jar(String owner, Predicate<String> contains, ClassBytesSource classes) {
	}

	private final List<Jar> jars;
	private final Map<String, Optional<Jar>> owners = new ConcurrentHashMap<>();
	private final Map<String, byte[]> read = new ConcurrentHashMap<>();

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

	private Jar jar(String internalName) {
		return owners.computeIfAbsent(internalName, n -> jars.stream().filter(j -> j.contains().test(n)).findFirst()).orElse(null);
	}
}
