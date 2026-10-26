package io.github.cmdralph.fabricated.core.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the intermediary → Mojang table for an obfuscated Minecraft version from
 * <ul>
 *   <li>Fabric's Intermediary in Tiny v1 format ({@code official → intermediary}), and</li>
 *   <li>Mojang's official mappings ({@code mojang → official}), in ProGuard format as Mojang publishes them (and
 *   NeoForge's installer keeps them), or in TSRG v1 as Forge 61's installer leaves them.</li>
 * </ul>
 * Members are joined on {@code (official owner, official name, official descriptor)}, so ProGuard's
 * overloaded obfuscated names are handled correctly. TSRG gives fields no descriptor: a field whose obfuscated name its
 * class gives no other field is joined on its name, and fields sharing one are told apart by their descriptors in
 * Mojang's names ({@link FieldDescriptors}).
 *
 * <p>For Forge on 1.20.1, whose game has Mojang's class names and SRG member names, {@link #composeSrg} takes the
 * members from MCPConfig's obfuscated → SRG mappings (TSRG v2) instead.
 */
public final class MappingComposer {
	private static final Pattern LINE_PREFIX = Pattern.compile("^\\d+:\\d+:");
	private static final Pattern LINE_SUFFIX = Pattern.compile("\\):\\d+(?::\\d+)?$");

	/** How far ahead the format is looked for: past the comments a ProGuard file starts with. */
	private static final int FORMAT_LOOKAHEAD = 1 << 16;

	private MappingComposer() {
	}

	/**
	 * The descriptor, in Mojang's names, of one of the game's fields in Mojang's names (its owner and name); null when
	 * unknown. Only TSRG needs it, for the fields whose obfuscated name another field of their class has.
	 */
	@FunctionalInterface
	public interface FieldDescriptors {
		FieldDescriptors NONE = (mojangOwner, mojangName) -> null;

		String of(String mojangOwner, String mojangName) throws IOException;
	}

	/** Parsed ProGuard mappings keyed by official names. */
	static final class Proguard {
		/** official class → mojang class (internal names). */
		final Map<String, String> classes = new HashMap<>();
		/** mojang class → official class. */
		final Map<String, String> mojangToOfficial = new HashMap<>();
		/** "officialOwner.officialName:mojangDesc" for fields, before descriptor conversion. */
		final Map<String, Map<String, String>> rawFields = new HashMap<>();
		/** officialOwner → list of (officialName, mojangDesc, mojangName). */
		final Map<String, List<String[]>> rawMethods = new HashMap<>();
		/** officialOwner + "." + officialName + officialDesc → mojang name. */
		final Map<String, String> fields = new HashMap<>();
		final Map<String, String> methods = new HashMap<>();
		/** officialOwner + "." + officialName → mojang name, for fields TSRG gives without a descriptor. */
		final Map<String, String> fieldsByName = new HashMap<>();

		String field(String officialOwner, String officialName, String officialDesc) {
			String byDescriptor = fields.get(officialOwner + "." + officialName + officialDesc);
			return byDescriptor != null ? byDescriptor : fieldsByName.get(officialOwner + "." + officialName);
		}
	}

	public static MappingTable compose(Reader intermediaryTiny, Reader mojangMappings) throws IOException, MappingException {
		return compose(intermediaryTiny, mojangMappings, FieldDescriptors.NONE);
	}

	/**
	 * @param mojangMappings   Mojang's mappings, ProGuard or TSRG v1 (told apart by their first line)
	 * @param fieldDescriptors the descriptors of fields sharing an obfuscated name, for TSRG
	 */
	public static MappingTable compose(Reader intermediaryTiny, Reader mojangMappings, FieldDescriptors fieldDescriptors)
			throws IOException, MappingException {
		return compose(intermediaryTiny, readMojang(mojangMappings, fieldDescriptors));
	}

	/**
	 * Builds the intermediary → runtime table for Forge on 1.20.1, whose game has Mojang's class names and SRG member
	 * names ({@code m_12345_}, {@code f_12345_}): the members from MCPConfig's obfuscated → SRG mappings in TSRG v2
	 * ({@code joined.tsrg}, which Forge's installer keeps as {@code mcp_config-<version>-mappings.txt}), the classes from
	 * Mojang's mappings. A class Mojang's file doesn't name keeps its intermediary name, as with {@link #compose}: a
	 * server's file names no client class. Without Mojang's mappings the classes take the TSRG file's names, which suits a
	 * file already in Mojang's class names but not MCPConfig's own ({@code net/minecraft/src/C_1234_}).
	 *
	 * @param srgMappings    obfuscated → SRG names in TSRG v2, the first namespace the obfuscated one
	 * @param mojangMappings Mojang's mappings, ProGuard or TSRG v1, read for the class names only; null to keep the TSRG
	 *                       file's
	 */
	public static MappingTable composeSrg(Reader intermediaryTiny, Reader srgMappings, Reader mojangMappings) throws IOException, MappingException {
		Proguard srg = readTsrg2(new BufferedReader(srgMappings));
		if (mojangMappings != null) {
			Map<String, String> mojangClasses = readMojang(mojangMappings, FieldDescriptors.NONE).classes;
			srg.classes.clear();
			srg.classes.putAll(mojangClasses);
		}
		return compose(intermediaryTiny, srg, true);
	}

	/** Joins Intermediary with runtime names keyed by official ones. */
	private static MappingTable compose(Reader intermediaryTiny, Proguard pg) throws IOException, MappingException {
		return compose(intermediaryTiny, pg, false);
	}

	/**
	 * @param plain whether the runtime renames methods Intermediary leaves under their official names, as SRG does (PLAIN-1):
	 *              their official name, which Fabric mods use, is then joined to the runtime one too
	 */
	private static MappingTable compose(Reader intermediaryTiny, Proguard pg, boolean plain) throws IOException, MappingException {
		Map<String, String> classes = new HashMap<>();
		Map<String, Map<String, String>> members = new HashMap<>();
		// official class → intermediary class, to translate descriptors for qualified keys
		Map<String, String> officialToIntermediary = new HashMap<>();
		List<String[]> memberLines = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(intermediaryTiny)) {
			String header = br.readLine();
			if (header == null) throw new MappingException("Empty intermediary file");
			String[] h = header.split("\t");
			if (h.length < 3 || !h[0].equals("v1") || !h[1].equals("official") || !h[2].equals("intermediary")) {
				throw new MappingException("Unexpected intermediary header: " + header + " (expected 'v1\\tofficial\\tintermediary')");
			}
			String line;
			int lineNo = 1;
			while ((line = br.readLine()) != null) {
				lineNo++;
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] p = line.split("\t");
				switch (p[0]) {
					case "CLASS" -> {
						if (p.length < 3) throw new MappingException("Malformed CLASS line " + lineNo);
						officialToIntermediary.put(p[1], p[2]);
						String mojang = pg.classes.get(p[1]);
						if (mojang != null && !mojang.equals(p[2])) classes.put(p[2], mojang);
					}
					case "FIELD", "METHOD" -> {
						if (p.length < 5) throw new MappingException("Malformed " + p[0] + " line " + lineNo);
						memberLines.add(p);
					}
					default -> {
						// Unknown line types are ignored for forward compatibility.
					}
				}
			}
		}

		for (String[] p : memberLines) {
			String owner = p[1];
			String desc = p[2];
			String officialName = p[3];
			String intermediaryName = p[4];
			String mojang = p[0].equals("FIELD") ? pg.field(owner, officialName, desc) : pg.methods.get(owner + "." + officialName + desc);
			if (mojang == null || mojang.equals(intermediaryName)) continue;
			String intermediaryOwner = officialToIntermediary.getOrDefault(owner, owner);
			String intermediaryDesc = remapDescriptor(desc, officialToIntermediary);
			members.computeIfAbsent(intermediaryName, k -> new HashMap<>())
					.put(intermediaryOwner + "." + intermediaryName + intermediaryDesc, mojang);
		}
		MappingTable table = MappingTable.fromMembers(classes, members);
		return plain ? table.withPlainMembers(plainMethods(memberLines, pg, officialToIntermediary)) : table;
	}

	/**
	 * The methods Intermediary leaves under their official names, which the runtime renames: those the runtime file names
	 * and Intermediary doesn't, under a name Intermediary renames nowhere (an override of a renamed method shares its
	 * obfuscated name, and Intermediary lists the method only where it is first declared). Keyed by intermediary owner,
	 * name and descriptor (PLAIN-1: {@code Pack$ResourcesSupplier.open}, which SRG names {@code m_247679_}).
	 */
	private static Map<String, String> plainMethods(List<String[]> memberLines, Proguard pg, Map<String, String> officialToIntermediary) {
		Set<String> listed = new HashSet<>();
		Set<String> renamed = new HashSet<>();
		for (String[] p : memberLines) {
			if (!p[0].equals("METHOD")) continue;
			listed.add(p[1] + "." + p[3] + p[2]);
			if (!p[3].equals(p[4])) renamed.add(p[3]);
		}
		Map<String, String> plain = new HashMap<>();
		for (Map.Entry<String, String> e : pg.methods.entrySet()) {
			String key = e.getKey();
			int paren = key.indexOf('(');
			int dot = key.lastIndexOf('.', paren);
			String owner = key.substring(0, dot);
			String name = key.substring(dot + 1, paren);
			if (name.equals(e.getValue()) || name.startsWith("<") || renamed.contains(name) || listed.contains(key)) continue;
			plain.put(officialToIntermediary.getOrDefault(owner, owner) + "." + name + remapDescriptor(key.substring(paren), officialToIntermediary),
					e.getValue());
		}
		return plain;
	}

	/** Mojang's mappings in either format: TSRG v1's lines have no {@code " -> "}, ProGuard's do. */
	static Proguard readMojang(Reader reader, FieldDescriptors fieldDescriptors) throws IOException, MappingException {
		BufferedReader br = new BufferedReader(reader);
		br.mark(FORMAT_LOOKAHEAD);
		String first = br.readLine();
		while (first != null && (first.isBlank() || first.stripLeading().startsWith("#"))) first = br.readLine();
		br.reset();
		if (first != null && first.startsWith("tsrg2 ")) throw new MappingException("Mojang's mappings are in TSRG v2, which Fabricated doesn't read");
		return first != null && !first.contains(" -> ") ? readTsrg(br, fieldDescriptors) : readProguard(br);
	}

	/**
	 * TSRG v2 ({@code tsrg2 <namespaces>}, then a name per namespace on class lines and on tab-indented member lines,
	 * methods with their descriptor in the first namespace's names after the first name, fields with or without one;
	 * parameter and {@code static} lines indented twice), from the first namespace to the second, as MCPConfig's
	 * {@code obf srg id}. A field whose obfuscated name its class gives another field, both without descriptors, is left
	 * out rather than joined to either.
	 */
	static Proguard readTsrg2(BufferedReader br) throws IOException, MappingException {
		String header = br.readLine();
		String[] namespaces = header == null ? new String[0] : header.strip().split(" ");
		if (namespaces.length < 3 || !namespaces[0].equals("tsrg2")) throw new MappingException("Not TSRG v2 mappings: the first line is " + header);
		int names = namespaces.length - 1;
		Proguard pg = new Proguard();
		Set<String> sharedFieldNames = new HashSet<>();
		String currentOfficial = null;
		String line;
		int lineNo = 1;
		while ((line = br.readLine()) != null) {
			lineNo++;
			if (line.isBlank() || line.startsWith("\t\t") || line.stripLeading().startsWith("#")) continue;
			String[] p = line.strip().split(" ");
			if (!line.startsWith("\t")) {
				if (p.length != names) throw new MappingException("Malformed class line " + lineNo + ": " + line);
				pg.classes.put(p[0], p[1]);
				currentOfficial = p[0];
				continue;
			}
			if (currentOfficial == null) throw new MappingException("Member before any class at line " + lineNo);
			if (p.length == names) {
				String key = currentOfficial + "." + p[0];
				String earlier = pg.fieldsByName.putIfAbsent(key, p[1]);
				if (earlier != null && !earlier.equals(p[1])) sharedFieldNames.add(key);
			} else if (p.length == names + 1 && p[1].startsWith("(")) {
				pg.methods.put(currentOfficial + "." + p[0] + p[1], p[2]);
			} else if (p.length == names + 1) {
				pg.fields.put(currentOfficial + "." + p[0] + p[1], p[2]);
			} else {
				throw new MappingException("Malformed member line " + lineNo + ": " + line);
			}
		}
		pg.fieldsByName.keySet().removeAll(sharedFieldNames);
		return pg;
	}

	/**
	 * TSRG v1 ({@code mojang official} class lines, then tab-indented {@code mojang official} field lines and
	 * {@code mojang descriptor official} method lines), as Forge 61's installer writes Mojang's mappings. Method
	 * descriptors are taken to official names whichever names they use: Mojang's classes are in packages and the
	 * obfuscated ones aren't, and the classes the obfuscation kept have the same name in both.
	 */
	static Proguard readTsrg(BufferedReader br, FieldDescriptors fieldDescriptors) throws IOException, MappingException {
		Proguard pg = new Proguard();
		// official owner → official field name → Mojang names (more than one where the obfuscation reused the name)
		Map<String, Map<String, List<String>>> rawFields = new HashMap<>();
		List<String[]> rawMethods = new ArrayList<>();
		String currentOfficial = null;
		String line;
		int lineNo = 0;
		while ((line = br.readLine()) != null) {
			lineNo++;
			if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
			String[] p = line.strip().split(" ");
			if (!Character.isWhitespace(line.charAt(0))) {
				if (p.length != 2) throw new MappingException("Malformed class line " + lineNo + ": " + line);
				pg.classes.put(p[1], p[0]);
				pg.mojangToOfficial.put(p[0], p[1]);
				currentOfficial = p[1];
				continue;
			}
			if (currentOfficial == null) throw new MappingException("Member before any class at line " + lineNo);
			if (p.length == 2) {
				rawFields.computeIfAbsent(currentOfficial, k -> new HashMap<>()).computeIfAbsent(p[1], k -> new ArrayList<>()).add(p[0]);
			} else if (p.length == 3 && p[1].startsWith("(")) {
				rawMethods.add(new String[] {currentOfficial, p[2], p[1], p[0]});
			} else {
				throw new MappingException("Malformed member line " + lineNo + ": " + line);
			}
		}
		for (String[] m : rawMethods) pg.methods.put(m[0] + "." + m[1] + remapDescriptor(m[2], pg.mojangToOfficial), m[3]);
		for (Map.Entry<String, Map<String, List<String>>> owner : rawFields.entrySet()) {
			String officialOwner = owner.getKey();
			for (Map.Entry<String, List<String>> field : owner.getValue().entrySet()) {
				List<String> mojangNames = field.getValue();
				if (mojangNames.size() == 1) {
					pg.fieldsByName.put(officialOwner + "." + field.getKey(), mojangNames.get(0));
					continue;
				}
				for (String mojangName : mojangNames) {
					String desc = fieldDescriptors.of(pg.classes.getOrDefault(officialOwner, officialOwner), mojangName);
					if (desc != null) pg.fields.put(officialOwner + "." + field.getKey() + remapDescriptor(desc, pg.mojangToOfficial), mojangName);
				}
			}
		}
		return pg;
	}

	static Proguard readProguard(Reader reader) throws IOException, MappingException {
		Proguard pg = new Proguard();
		String currentOfficial = null;
		try (BufferedReader br = new BufferedReader(reader)) {
			String line;
			int lineNo = 0;
			while ((line = br.readLine()) != null) {
				lineNo++;
				if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
				if (!Character.isWhitespace(line.charAt(0))) {
					// "a.b.Mojang -> official:"
					int arrow = line.indexOf(" -> ");
					if (arrow < 0 || !line.endsWith(":")) throw new MappingException("Malformed class line " + lineNo + ": " + line);
					String mojang = line.substring(0, arrow).replace('.', '/');
					String official = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
					pg.classes.put(official, mojang);
					pg.mojangToOfficial.put(mojang, official);
					currentOfficial = official;
					continue;
				}
				if (currentOfficial == null) throw new MappingException("Member before any class at line " + lineNo);
				String member = line.strip();
				int arrow = member.indexOf(" -> ");
				if (arrow < 0) throw new MappingException("Malformed member line " + lineNo + ": " + line);
				String left = member.substring(0, arrow);
				String officialName = member.substring(arrow + 4);
				int paren = left.indexOf('(');
				if (paren < 0) {
					// field: "type name"
					int space = left.lastIndexOf(' ');
					if (space < 0) throw new MappingException("Malformed field line " + lineNo);
					String type = left.substring(0, space);
					String mojangName = left.substring(space + 1);
					pg.rawFields.computeIfAbsent(currentOfficial, k -> new HashMap<>())
							.put(officialName + ":" + type, mojangName);
				} else {
					// method: "[line:line:]ret name(args)"
					// Strip "start:end:" line numbers before and ":origStart:origEnd" (inlining) after the signature.
					String sig = LINE_PREFIX.matcher(left).replaceFirst("");
					sig = LINE_SUFFIX.matcher(sig).replaceFirst(")");
					int space = sig.indexOf(' ');
					if (space < 0) throw new MappingException("Malformed method line " + lineNo);
					String ret = sig.substring(0, space);
					String rest = sig.substring(space + 1);
					int p = rest.indexOf('(');
					int close = rest.lastIndexOf(')');
					if (p <= 0 || close < p) throw new MappingException("Malformed method line " + lineNo + ": " + line);
					String mojangName = rest.substring(0, p);
					String args = rest.substring(p + 1, close);
					pg.rawMethods.computeIfAbsent(currentOfficial, k -> new ArrayList<>())
							.add(new String[] {officialName, args, ret, mojangName});
				}
			}
		}

		// Convert Mojang-named types to official descriptors now that all classes are known.
		for (Map.Entry<String, Map<String, String>> e : pg.rawFields.entrySet()) {
			for (Map.Entry<String, String> f : e.getValue().entrySet()) {
				String key = f.getKey();
				int sep = key.indexOf(':');
				String officialName = key.substring(0, sep);
				String desc = typeToDescriptor(key.substring(sep + 1), pg.mojangToOfficial);
				pg.fields.put(e.getKey() + "." + officialName + desc, f.getValue());
			}
		}
		for (Map.Entry<String, List<String[]>> e : pg.rawMethods.entrySet()) {
			for (String[] m : e.getValue()) {
				StringBuilder desc = new StringBuilder("(");
				if (!m[1].isEmpty()) {
					for (String arg : m[1].split(",")) desc.append(typeToDescriptor(arg, pg.mojangToOfficial));
				}
				desc.append(')').append(typeToDescriptor(m[2], pg.mojangToOfficial));
				pg.methods.put(e.getKey() + "." + m[0] + desc, m[3]);
			}
		}
		return pg;
	}

	/** Converts a Java source type ({@code int[]}, {@code a.b.C}) into a descriptor using {@code classMap}. */
	static String typeToDescriptor(String type, Map<String, String> classMap) {
		int dims = 0;
		String t = type;
		while (t.endsWith("[]")) {
			dims++;
			t = t.substring(0, t.length() - 2);
		}
		String base = switch (t) {
			case "void" -> "V";
			case "boolean" -> "Z";
			case "byte" -> "B";
			case "char" -> "C";
			case "short" -> "S";
			case "int" -> "I";
			case "long" -> "J";
			case "float" -> "F";
			case "double" -> "D";
			default -> {
				String internal = t.replace('.', '/');
				yield "L" + classMap.getOrDefault(internal, internal) + ";";
			}
		};
		return "[".repeat(dims) + base;
	}

	/** Rewrites class names inside a descriptor. */
	static String remapDescriptor(String desc, Map<String, String> classMap) {
		StringBuilder sb = new StringBuilder(desc.length());
		int i = 0;
		while (i < desc.length()) {
			char c = desc.charAt(i);
			if (c == 'L') {
				int end = desc.indexOf(';', i);
				if (end < 0) return desc;
				String name = desc.substring(i + 1, end);
				sb.append('L').append(classMap.getOrDefault(name, name)).append(';');
				i = end + 1;
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString();
	}
}
