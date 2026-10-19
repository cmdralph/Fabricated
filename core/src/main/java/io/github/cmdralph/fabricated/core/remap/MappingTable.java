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
 */
public final class MappingTable {
	private static final int MAGIC = 0x46424D54; // "FBMT"
	private static final int FORMAT = 1;

	private final Map<String, String> classes;
	private final Map<String, String> flatMembers;
	private final Map<String, String> qualifiedMembers;
	private volatile Map<String, String> inverseClasses;
	/** Qualified members by {@code name+desc}, to their runtime name when it is the same for every owner. */
	private volatile Map<String, String> byNameAndDesc;

	public MappingTable(Map<String, String> classes, Map<String, String> flatMembers, Map<String, String> qualifiedMembers) {
		// Hash maps for lookups; serialize() sorts, so the cached form stays deterministic.
		this.classes = Map.copyOf(classes);
		this.flatMembers = Map.copyOf(flatMembers);
		this.qualifiedMembers = Map.copyOf(qualifiedMembers);
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

	/** Flat member lookup only; {@code null} if the name is unknown or ambiguous. */
	public String mapMemberFlat(String name) {
		return flatMembers.get(name);
	}

	public int classCount() {
		return classes.size();
	}

	public int memberCount() {
		return flatMembers.size() + qualifiedMembers.size();
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
		if (in.available() != 0) throw new IOException("Trailing data in mapping table");
		return new MappingTable(classes, flat, qualified);
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
