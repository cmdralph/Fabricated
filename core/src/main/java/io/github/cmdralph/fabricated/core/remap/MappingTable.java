package io.github.cmdralph.fabricated.core.remap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

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
	/** Matches intermediary member tokens. */
	public static final Pattern MEMBER_TOKEN = Pattern.compile("(?:method|field|comp)_\\d+");
	private static final int MAGIC = 0x46424D54; // "FBMT"
	private static final int FORMAT = 1;

	private final Map<String, String> classes;
	private final Map<String, String> flatMembers;
	private final Map<String, String> qualifiedMembers;
	private volatile Map<String, String> inverseClasses;

	public MappingTable(Map<String, String> classes, Map<String, String> flatMembers, Map<String, String> qualifiedMembers) {
		this.classes = Collections.unmodifiableMap(new TreeMap<>(classes));
		this.flatMembers = Collections.unmodifiableMap(new TreeMap<>(flatMembers));
		this.qualifiedMembers = Collections.unmodifiableMap(new TreeMap<>(qualifiedMembers));
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
		if (owner != null && desc != null) {
			String qualified = qualifiedMembers.get(owner + "." + name + desc);
			if (qualified != null) return qualified;
		}
		return name;
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

	public boolean isEmpty() {
		return classes.isEmpty() && flatMembers.isEmpty() && qualifiedMembers.isEmpty();
	}

	public Map<String, String> classes() {
		return classes;
	}

	/** Builds a table from per-member mappings, separating ambiguous names. */
	public static MappingTable fromMembers(Map<String, String> classes, Map<String, Map<String, String>> membersByName) {
		Map<String, String> flat = new TreeMap<>();
		Map<String, String> qualified = new TreeMap<>();
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
		for (Map.Entry<String, String> e : map.entrySet()) {
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
