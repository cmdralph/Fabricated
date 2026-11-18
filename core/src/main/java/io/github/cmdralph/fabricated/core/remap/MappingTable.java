package io.github.cmdralph.fabricated.core.remap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Intermediary → runtime (Mojang) names.
 *
 * <p>Intermediary member names ({@code method_N}, {@code field_N}, {@code comp_N}) are globally unique
 * tokens that are shared by a method and all its overrides, so a flat lookup by name is exact for
 * almost every member. The few intermediary names that map to more than one runtime name are kept in
 * {@link #qualifiedMembers} keyed by {@code owner.name(desc)} (all in intermediary) and are absent
 * from {@link #flatMembers}.
 *
 * <p>{@link #plainMembers} holds the methods Intermediary leaves under their official names (unobfuscated there: a
 * functional interface's {@code open}, {@code createMenu}), which SRG renames all the same (PLAIN-1): only Forge
 * 43's and 47's tables have them, keyed like the qualified members. It holds the fields SRG renames so too, keyed by
 * owner and name alone (PLAIN-2: Forge 43's, the fields of Mojang's {@code com/mojang/blaze3d} classes).
 *
 * <p>{@link #overloadedMethods} holds, by intermediary name, the descriptor (in intermediary) of the methods whose runtime
 * name their class gives another method too: a mixin selector naming one by its intermediary name alone, as Fabric Loom
 * writes them, picks it on Fabric and the first of that name at runtime (CORPUS-45).
 */
public final class MappingTable {
	private static final int MAGIC = 0x46424D54; // "FBMT"
	private static final int FORMAT = 3;

	private final Map<String, String> classes;
	private final Map<String, String> flatMembers;
	private final Map<String, String> qualifiedMembers;
	private final Map<String, String> plainMembers;
	private final Map<String, String> overloadedMethods;
	/**
	 * {@link #plainMembers} by {@code name+desc}, for a descriptor that names a Minecraft class, to their runtime name when
	 * it is the same for every owner.
	 */
	private volatile Map<String, String> plainByNameAndDesc;
	/** {@link #plainMembers}' names and descriptors, whatever their owner. */
	private volatile Set<String> plainNamesAndDescs;
	/** The names of {@link #plainMembers}' fields, whatever their owner. */
	private volatile Set<String> plainFieldNames;
	/** {@link #plainMembers}' methods by {@code owner.name}, then by descriptor. */
	private volatile Map<String, Map<String, String>> plainMethodsByName;
	/** {@link #plainMembers}' fields by owner, then by name. */
	private volatile Map<String, Map<String, String>> plainFieldsByOwner;
	private volatile Map<String, String> inverseClasses;
	/** Qualified members by {@code name+desc}, to their runtime name when it is the same for every owner. */
	private volatile Map<String, String> byNameAndDesc;

	public MappingTable(Map<String, String> classes, Map<String, String> flatMembers, Map<String, String> qualifiedMembers) {
		this(classes, flatMembers, qualifiedMembers, Map.of(), Map.of());
	}

	private MappingTable(Map<String, String> classes, Map<String, String> flatMembers, Map<String, String> qualifiedMembers,
			Map<String, String> plainMembers, Map<String, String> overloadedMethods) {
		// Hash maps for lookups; serialize() sorts, so the cached form stays deterministic.
		this.classes = Map.copyOf(classes);
		this.flatMembers = Map.copyOf(flatMembers);
		this.qualifiedMembers = Map.copyOf(qualifiedMembers);
		this.plainMembers = Map.copyOf(plainMembers);
		this.overloadedMethods = Map.copyOf(overloadedMethods);
	}

	/** Maps an internal class name ({@code a/b/C}); unknown names are returned unchanged. */
	public String mapClass(String internalName) {
		return classes.getOrDefault(internalName, internalName);
	}

	/** Reverse of {@link #mapClass}; unknown names are returned unchanged. */
	public String unmapClass(String runtimeInternalName) {
		Map<String, String> inverse = inverseClasses;
		if (inverse == null) {
			Map<String, String> built = new HashMap<>(classes.size() * 2);
			classes.forEach((k, v) -> built.put(v, k));
			inverse = built;
			inverseClasses = inverse;
		}
		return inverse.getOrDefault(runtimeInternalName, runtimeInternalName);
	}

	/**
	 * Maps a member name.
	 *
	 * @param owner intermediary internal name of the owner, or {@code null} when unknown
	 * @param desc  intermediary descriptor, or {@code null} when unknown
	 */
	public String mapMember(String owner, String name, String desc) {
		String flat = flatMembers.get(name);
		if (flat != null) return flat;
		if (desc != null) {
			String qualified = owner != null ? qualifiedMembers.get(owner + "." + name + desc) : null;
			if (qualified != null) return qualified;
			// The owner a mixin names is the mixin itself (its @Shadow members and their uses): the descriptor
			// alone tells the members sharing this intermediary name apart, when they map alike.
			String byDesc = byNameAndDesc().get(name + desc);
			if (byDesc != null && !byDesc.isEmpty()) return byDesc;
		}
		return name;
	}

	private Map<String, String> byNameAndDesc() {
		Map<String, String> index = byNameAndDesc;
		if (index == null) {
			Map<String, String> built = new HashMap<>();
			for (Map.Entry<String, String> e : qualifiedMembers.entrySet()) {
				String member = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
				// An empty value marks a name and descriptor that map differently for different owners.
				built.merge(member, e.getValue(), (a, b) -> a.equals(b) ? a : "");
			}
			index = built;
			byNameAndDesc = index;
		}
		return index;
	}

	/**
	 * The runtime name of a method Intermediary leaves under its official name and SRG renames (PLAIN-1), or null: the
	 * owner's own, else, where the descriptor names a Minecraft class, the one every owner of that name and descriptor
	 * gives it, which an override in a mod's class takes. A descriptor of the JDK's and libraries' types alone is judged
	 * by its owner only: a mod's {@code run()V} implementing {@link Runnable} keeps its name.
	 *
	 * @param owner intermediary internal name of the owner, as the instruction or declaration names it
	 * @param desc  intermediary descriptor
	 */
	public String mapPlainMethod(String owner, String name, String desc) {
		if (plainMembers.isEmpty() || desc == null) return null;
		String own = owner != null ? plainMembers.get(owner + "." + name + desc) : null;
		if (own != null || !desc.contains("Lnet/minecraft/")) return own;
		String shared = plainByNameAndDesc().get(name + desc);
		return shared == null || shared.isEmpty() ? null : shared;
	}

	/**
	 * The runtime name the owner itself gives a method Intermediary leaves under its official name and SRG renames
	 * (PLAIN-1), or null: the owner's declaration alone, whatever other owners give that name and descriptor.
	 *
	 * @param owner intermediary internal name of the owner
	 * @param desc  intermediary descriptor
	 */
	public String ownPlainMethod(String owner, String name, String desc) {
		return plainMembers.isEmpty() || owner == null || desc == null ? null : plainMembers.get(owner + "." + name + desc);
	}

	/**
	 * Whether some owner gives a method of that name and descriptor (intermediary) a runtime name of its own (PLAIN-1): only
	 * then can an override of it need one.
	 */
	public boolean isPlainMethod(String name, String desc) {
		if (plainMembers.isEmpty() || desc == null) return false;
		Set<String> index = plainNamesAndDescs;
		if (index == null) {
			Set<String> built = new HashSet<>();
			for (String key : plainMembers.keySet()) built.add(key.substring(key.lastIndexOf('.') + 1));
			index = built;
			plainNamesAndDescs = index;
		}
		return index.contains(name + desc);
	}

	private Map<String, String> plainByNameAndDesc() {
		Map<String, String> index = plainByNameAndDesc;
		if (index == null) {
			Map<String, String> built = new HashMap<>();
			for (Map.Entry<String, String> e : plainMembers.entrySet()) {
				String member = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
				if (!member.contains("Lnet/minecraft/")) continue;
				// An empty value marks a name and descriptor that map differently for different owners.
				built.merge(member, e.getValue(), (a, b) -> a.equals(b) ? a : "");
			}
			index = built;
			plainByNameAndDesc = index;
		}
		return index;
	}

	/**
	 * The runtime name of a field Intermediary leaves under its official name and SRG renames (PLAIN-2), or null: the
	 * owner's own, as the instruction, declaration or selector names it.
	 *
	 * @param owner intermediary internal name of the owner
	 */
	public String mapPlainField(String owner, String name) {
		return owner == null || !isPlainField(name) ? null : plainMembers.get(owner + "." + name);
	}

	/**
	 * The runtime names of the owner's own methods of that name that Intermediary leaves under their official names and
	 * SRG renames, by intermediary descriptor; empty when none (PLAIN-2: a mixin's selector naming one by its name alone).
	 *
	 * @param owner intermediary internal name of the owner
	 */
	public Map<String, String> plainMethodsNamed(String owner, String name) {
		if (plainMembers.isEmpty() || owner == null) return Map.of();
		Map<String, Map<String, String>> index = plainMethodsByName;
		if (index == null) {
			Map<String, Map<String, String>> built = new HashMap<>();
			for (Map.Entry<String, String> e : plainMembers.entrySet()) {
				int paren = e.getKey().indexOf('(');
				if (paren > 0) built.computeIfAbsent(e.getKey().substring(0, paren), k -> new TreeMap<>()).put(e.getKey().substring(paren), e.getValue());
			}
			index = built;
			plainMethodsByName = index;
		}
		return index.getOrDefault(owner + "." + name, Map.of());
	}

	/**
	 * The owner's own fields that Intermediary leaves under their official names and SRG renames, by name, to their
	 * runtime names; empty when none (PLAIN-2: an accessor's target, which Mixin matches ignoring case).
	 *
	 * @param owner intermediary internal name of the owner
	 */
	public Map<String, String> plainFieldsOf(String owner) {
		if (plainMembers.isEmpty() || owner == null) return Map.of();
		Map<String, Map<String, String>> index = plainFieldsByOwner;
		if (index == null) {
			Map<String, Map<String, String>> built = new HashMap<>();
			for (Map.Entry<String, String> e : plainMembers.entrySet()) {
				String key = e.getKey();
				if (key.indexOf('(') >= 0) continue;
				int dot = key.lastIndexOf('.');
				built.computeIfAbsent(key.substring(0, dot), k -> new TreeMap<>()).put(key.substring(dot + 1), e.getValue());
			}
			index = built;
			plainFieldsByOwner = index;
		}
		return index.getOrDefault(owner, Map.of());
	}

	/** Whether some owner gives a field of that name a runtime name of its own (PLAIN-2). */
	public boolean isPlainField(String name) {
		if (plainMembers.isEmpty()) return false;
		Set<String> index = plainFieldNames;
		if (index == null) {
			Set<String> built = new HashSet<>();
			for (String key : plainMembers.keySet()) {
				if (key.indexOf('(') < 0) built.add(key.substring(key.lastIndexOf('.') + 1));
			}
			index = built;
			plainFieldNames = index;
		}
		return index.contains(name);
	}

	/** Whether this table has members Intermediary leaves under their official names (Forge 43's and 47's, PLAIN-1). */
	public boolean hasPlainMembers() {
		return !plainMembers.isEmpty();
	}

	/**
	 * The intermediary descriptor of the method of that intermediary name, when its class gives another method its runtime
	 * name (overloads Intermediary names apart), or null: such a method needs its descriptor in a selector to stay the one
	 * the selector picks.
	 */
	public String overloadedDescriptor(String intermediaryName) {
		return overloadedMethods.get(intermediaryName);
	}

	/** Whether any method's runtime name is shared by an overload Intermediary names apart. */
	public boolean hasOverloadedMethods() {
		return !overloadedMethods.isEmpty();
	}

	/** Flat member lookup only; {@code null} if the name is unknown or ambiguous. */
	public String mapMemberFlat(String name) {
		return flatMembers.get(name);
	}

	public int classCount() {
		return classes.size();
	}

	public int memberCount() {
		return flatMembers.size() + qualifiedMembers.size() + plainMembers.size();
	}

	/** This table with the methods Intermediary leaves under their official names, keyed like the qualified ones (PLAIN-1). */
	MappingTable withPlainMembers(Map<String, String> plain) {
		return new MappingTable(classes, flatMembers, qualifiedMembers, plain, overloadedMethods);
	}

	/** This table with the methods whose runtime name an overload shares, by intermediary name, to their descriptor. */
	MappingTable withOverloadedMethods(Map<String, String> overloaded) {
		return new MappingTable(classes, flatMembers, qualifiedMembers, plainMembers, overloaded);
	}

	/** Builds a table from per-member mappings, separating ambiguous names. */
	public static MappingTable fromMembers(Map<String, String> classes, Map<String, Map<String, String>> membersByName) {
		Map<String, String> flat = new HashMap<>();
		Map<String, String> qualified = new HashMap<>();
		for (Map.Entry<String, Map<String, String>> e : membersByName.entrySet()) {
			Set<String> targets = new HashSet<>(e.getValue().values());
			if (targets.size() == 1) {
				flat.put(e.getKey(), targets.iterator().next());
			} else {
				qualified.putAll(e.getValue());
			}
		}
		return new MappingTable(classes, flat, qualified);
	}

	/** Compact, deterministic binary form used by the on-disk mapping cache. */
	public byte[] serialize() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(MAGIC);
			out.writeInt(FORMAT);
			writeMap(out, classes);
			writeMap(out, flatMembers);
			writeMap(out, qualifiedMembers);
			writeMap(out, plainMembers);
			writeMap(out, overloadedMethods);
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("In-memory serialization failed", e);
		}
	}

	public static MappingTable deserialize(byte[] data) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		if (in.readInt() != MAGIC) throw new IOException("Not a Fabricated mapping table");
		int format = in.readInt();
		if (format != FORMAT) throw new IOException("Unsupported mapping table format " + format);
		Map<String, String> classes = readMap(in);
		Map<String, String> flat = readMap(in);
		Map<String, String> qualified = readMap(in);
		Map<String, String> plain = readMap(in);
		Map<String, String> overloaded = readMap(in);
		if (in.available() != 0) throw new IOException("Trailing data in mapping table");
		return new MappingTable(classes, flat, qualified, plain, overloaded);
	}

	private static void writeMap(DataOutputStream out, Map<String, String> map) throws IOException {
		out.writeInt(map.size());
		for (Map.Entry<String, String> e : new TreeMap<>(map).entrySet()) {
			out.writeUTF(e.getKey());
			out.writeUTF(e.getValue());
		}
	}

	private static Map<String, String> readMap(DataInputStream in) throws IOException {
		int size = in.readInt();
		if (size < 0 || size > 5_000_000) throw new IOException("Invalid mapping table size " + size);
		Map<String, String> map = new HashMap<>(Math.max(16, size * 2));
		for (int i = 0; i < size; i++) {
			map.put(in.readUTF(), in.readUTF());
		}
		return map;
	}
}
