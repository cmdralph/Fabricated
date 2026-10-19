package io.github.cmdralph.fabricated.core.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the intermediary → Mojang table for an obfuscated Minecraft version from
 * <ul>
 *   <li>Fabric's Intermediary in Tiny v1 format ({@code official → intermediary}), and</li>
 *   <li>Mojang's official mappings in ProGuard format ({@code mojang → official}).</li>
 * </ul>
 * Members are joined on {@code (official owner, official name, official descriptor)}, so ProGuard's
 * overloaded obfuscated names are handled correctly.
 */
public final class MappingComposer {
	private static final Pattern LINE_PREFIX = Pattern.compile("^\\d+:\\d+:");
	private static final Pattern LINE_SUFFIX = Pattern.compile("\\):\\d+(?::\\d+)?$");

	private MappingComposer() {
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
	}

	public static MappingTable compose(Reader intermediaryTiny, Reader mojangProguard) throws IOException, MappingException {
		Proguard pg = readProguard(mojangProguard);
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
			String key = owner + "." + officialName + desc;
			String mojang = p[0].equals("FIELD") ? pg.fields.get(key) : pg.methods.get(key);
			if (mojang == null || mojang.equals(intermediaryName)) continue;
			String intermediaryOwner = officialToIntermediary.getOrDefault(owner, owner);
			String intermediaryDesc = remapDescriptor(desc, officialToIntermediary);
			members.computeIfAbsent(intermediaryName, k -> new HashMap<>())
					.put(intermediaryOwner + "." + intermediaryName + intermediaryDesc, mojang);
		}
		return MappingTable.fromMembers(classes, members);
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
