package io.github.cmdralph.fabricated.core.accesswidener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Access;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Entry;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Kind;

/**
 * Parser for Fabric access wideners (v1, v2) and class tweakers (v1), following the grammar of
 * Fabric's class-tweaker library: whitespace-separated tokens, {@code #} comments, optional
 * {@code transitive-} prefix (v2 and class tweakers), and {@code inject-interface} (class tweakers).
 */
public final class AccessWidenerParser {
	private static final Pattern V1_DELIMITER = Pattern.compile("\\s+");
	private static final Pattern V2_DELIMITER = Pattern.compile("[ \\t]+");
	private static final String TRANSITIVE = "transitive-";

	private AccessWidenerParser() {
	}

	public static AccessWidenerFile parse(String text) throws AccessWidenerException {
		List<String> lines = text.lines().toList();
		if (lines.isEmpty()) throw new AccessWidenerException(1, "Empty file");
		String headerLine = lines.getFirst();
		String[] header = headerLine.trim().split("\\s+");
		if (header.length != 3 || !header[0].equals("accessWidener") && !header[0].equals("classTweaker")) {
			throw new AccessWidenerException(1, "Invalid header '" + headerLine + "'; expected 'accessWidener <v1|v2> <namespace>' or 'classTweaker v1 <namespace>'");
		}
		boolean classTweaker = header[0].equals("classTweaker");
		String version = header[1];
		if (classTweaker ? !version.equals("v1") : !version.equals("v1") && !version.equals("v2")) {
			throw new AccessWidenerException(1, "Unsupported " + header[0] + " version " + version);
		}
		boolean v2Grammar = classTweaker || version.equals("v2");

		List<Entry> entries = new ArrayList<>();
		List<InjectedInterface> injections = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			int lineNo = i + 1;
			String line = lines.get(i);
			int comment = line.indexOf('#');
			if (comment >= 0) line = line.substring(0, comment);
			if (!v2Grammar) line = line.trim();
			if (line.isBlank()) continue;
			line = line.stripTrailing();
			if (Character.isWhitespace(line.charAt(0))) {
				throw new AccessWidenerException(lineNo, "Leading whitespace is not allowed");
			}
			String[] tokens = (v2Grammar ? V2_DELIMITER : V1_DELIMITER).split(line);
			String first = tokens[0];

			if (classTweaker && (first.equals("inject-interface") || first.equals(TRANSITIVE + "inject-interface"))) {
				if (tokens.length != 3) throw new AccessWidenerException(lineNo, "Expected 'inject-interface <class> <interface>'");
				checkClassName(tokens[1], lineNo);
				String full = tokens[2];
				int generic = full.indexOf('<');
				String raw = generic >= 0 ? full.substring(0, generic) : full;
				checkClassName(raw, lineNo);
				injections.add(new InjectedInterface(tokens[1], raw, full, first.startsWith(TRANSITIVE), lineNo));
				continue;
			}

			boolean transitive = false;
			if (v2Grammar && first.startsWith(TRANSITIVE)) {
				first = first.substring(TRANSITIVE.length());
				transitive = true;
			}
			Access access = switch (first.toLowerCase(Locale.ROOT)) {
				case "accessible" -> Access.ACCESSIBLE;
				case "extendable" -> Access.EXTENDABLE;
				case "mutable" -> Access.MUTABLE;
				default -> throw new AccessWidenerException(lineNo, "Unknown access type '" + first + "'");
			};
			if (tokens.length < 2) throw new AccessWidenerException(lineNo, "Expected class, method or field after " + first);
			switch (tokens[1]) {
				case "class" -> {
					if (tokens.length != 3) throw new AccessWidenerException(lineNo, "Expected '<access> class <className>'");
					if (access == Access.MUTABLE) throw new AccessWidenerException(lineNo, "Classes cannot be made mutable");
					checkClassName(tokens[2], lineNo);
					entries.add(new Entry(access, Kind.CLASS, tokens[2], null, null, transitive, lineNo));
				}
				case "method" -> {
					if (tokens.length != 5) throw new AccessWidenerException(lineNo, "Expected '<access> method <className> <name> <descriptor>'");
					if (access == Access.MUTABLE) throw new AccessWidenerException(lineNo, "Methods cannot be made mutable");
					checkClassName(tokens[2], lineNo);
					if (!tokens[4].startsWith("(")) throw new AccessWidenerException(lineNo, "Invalid method descriptor " + tokens[4]);
					entries.add(new Entry(access, Kind.METHOD, tokens[2], tokens[3], tokens[4], transitive, lineNo));
				}
				case "field" -> {
					if (tokens.length != 5) throw new AccessWidenerException(lineNo, "Expected '<access> field <className> <name> <descriptor>'");
					if (access == Access.EXTENDABLE) throw new AccessWidenerException(lineNo, "Fields cannot be made extendable");
					checkClassName(tokens[2], lineNo);
					entries.add(new Entry(access, Kind.FIELD, tokens[2], tokens[3], tokens[4], transitive, lineNo));
				}
				default -> throw new AccessWidenerException(lineNo, "Unsupported target type '" + tokens[1] + "'");
			}
		}
		return new AccessWidenerFile(header[0], header[2], List.copyOf(entries), List.copyOf(injections));
	}

	private static void checkClassName(String name, int line) throws AccessWidenerException {
		if (name.contains(".")) throw new AccessWidenerException(line, "Class names must use '/' (a/b/C), found " + name);
		if (name.isEmpty() || name.startsWith("/") || name.endsWith("/")) throw new AccessWidenerException(line, "Invalid class name '" + name + "'");
	}
}
