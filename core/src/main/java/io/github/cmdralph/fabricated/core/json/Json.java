package io.github.cmdralph.fabricated.core.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, dependency-free JSON reader and writer.
 *
 * <p>Mod metadata is untrusted input, so the reader enforces a maximum input length and nesting
 * depth, rejects trailing content, and never evaluates anything. Parsed values are plain Java
 * types: {@link Map} (insertion ordered), {@link List}, {@link String}, {@link BigDecimal},
 * {@link Boolean} and {@code null}.
 *
 * <p>Fabric's {@code fabric.mod.json} parser tolerates comments in practice (GSON lenient mode), so
 * {@code //} and block comments are accepted and ignored.
 */
public final class Json {
	/** Default maximum input length in characters (1 MiB). */
	public static final int DEFAULT_MAX_LENGTH = 1 << 20;
	/** Default maximum nesting depth. */
	public static final int DEFAULT_MAX_DEPTH = 64;

	private Json() {
	}

	public static Object parse(String text) throws JsonException {
		return parse(text, DEFAULT_MAX_LENGTH, DEFAULT_MAX_DEPTH);
	}

	public static Object parse(String text, int maxLength, int maxDepth) throws JsonException {
		if (text.length() > maxLength) {
			throw new JsonException("JSON document exceeds " + maxLength + " characters");
		}
		Reader reader = new Reader(text, maxDepth);
		reader.skipWhitespace();
		Object value = reader.readValue(0);
		reader.skipWhitespace();
		if (!reader.atEnd()) {
			throw reader.error("Unexpected trailing content");
		}
		return value;
	}

	/** Parses a document that must be a JSON object. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(String text) throws JsonException {
		Object value = parse(text);
		if (!(value instanceof Map)) {
			throw new JsonException("Expected a JSON object at the top level");
		}
		return (Map<String, Object>) value;
	}

	/** Serialises a value tree. Map iteration order is preserved, so output is deterministic. */
	public static String write(Object value, boolean pretty) {
		StringBuilder sb = new StringBuilder();
		write(sb, value, pretty, 0);
		if (pretty) sb.append('\n');
		return sb.toString();
	}

	private static void write(StringBuilder sb, Object value, boolean pretty, int indent) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String s) {
			writeString(sb, s);
		} else if (value instanceof Boolean b) {
			sb.append(b.booleanValue());
		} else if (value instanceof BigDecimal d) {
			sb.append(d.toPlainString());
		} else if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new IllegalArgumentException("JSON cannot represent " + d);
			}
			sb.append(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
		} else if (value instanceof Number n) {
			sb.append(n.longValue());
		} else if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) {
				sb.append("{}");
				return;
			}
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : map.entrySet()) {
				if (!first) sb.append(',');
				first = false;
				newline(sb, pretty, indent + 1);
				writeString(sb, String.valueOf(e.getKey()));
				sb.append(pretty ? ": " : ":");
				write(sb, e.getValue(), pretty, indent + 1);
			}
			newline(sb, pretty, indent);
			sb.append('}');
		} else if (value instanceof Iterable<?> list) {
			if (!list.iterator().hasNext()) {
				sb.append("[]");
				return;
			}
			sb.append('[');
			boolean first = true;
			for (Object o : list) {
				if (!first) sb.append(',');
				first = false;
				newline(sb, pretty, indent + 1);
				write(sb, o, pretty, indent + 1);
			}
			newline(sb, pretty, indent);
			sb.append(']');
		} else {
			throw new IllegalArgumentException("Unsupported JSON value type " + value.getClass().getName());
		}
	}

	private static void newline(StringBuilder sb, boolean pretty, int indent) {
		if (!pretty) return;
		sb.append('\n');
		for (int i = 0; i < indent; i++) sb.append("  ");
	}

	private static void writeString(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				default -> {
					if (c < 0x20 || c == 0x2028 || c == 0x2029) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
	}

	private static final class Reader {
		private final String s;
		private final int maxDepth;
		private int pos;

		Reader(String s, int maxDepth) {
			this.s = s;
			this.maxDepth = maxDepth;
			// Tolerate a UTF-8 byte order mark decoded as U+FEFF.
			if (!s.isEmpty() && s.charAt(0) == '﻿') pos = 1;
		}

		boolean atEnd() {
			return pos >= s.length();
		}

		JsonException error(String message) {
			int line = 1;
			int col = 1;
			for (int i = 0; i < Math.min(pos, s.length()); i++) {
				if (s.charAt(i) == '\n') {
					line++;
					col = 1;
				} else {
					col++;
				}
			}
			return new JsonException(message + " at line " + line + ", column " + col);
		}

		void skipWhitespace() throws JsonException {
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
					pos++;
				} else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
					while (pos < s.length() && s.charAt(pos) != '\n') pos++;
				} else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '*') {
					int end = s.indexOf("*/", pos + 2);
					if (end < 0) throw error("Unterminated comment");
					pos = end + 2;
				} else {
					return;
				}
			}
		}

		Object readValue(int depth) throws JsonException {
			if (depth > maxDepth) throw error("JSON nesting deeper than " + maxDepth);
			if (atEnd()) throw error("Unexpected end of input");
			char c = s.charAt(pos);
			return switch (c) {
				case '{' -> readObject(depth);
				case '[' -> readArray(depth);
				case '"' -> readString();
				case 't' -> literal("true", Boolean.TRUE);
				case 'f' -> literal("false", Boolean.FALSE);
				case 'n' -> literal("null", null);
				default -> {
					if (c == '-' || c >= '0' && c <= '9') yield readNumber();
					throw error("Unexpected character '" + c + "'");
				}
			};
		}

		private Object literal(String word, Object value) throws JsonException {
			if (!s.startsWith(word, pos)) throw error("Invalid literal");
			pos += word.length();
			return value;
		}

		private Map<String, Object> readObject(int depth) throws JsonException {
			pos++; // {
			Map<String, Object> map = new LinkedHashMap<>();
			skipWhitespace();
			if (!atEnd() && s.charAt(pos) == '}') {
				pos++;
				return map;
			}
			while (true) {
				skipWhitespace();
				if (atEnd() || s.charAt(pos) != '"') throw error("Expected a string key");
				String key = readString();
				skipWhitespace();
				if (atEnd() || s.charAt(pos) != ':') throw error("Expected ':'");
				pos++;
				skipWhitespace();
				Object value = readValue(depth + 1);
				// Fabric's own parser tolerates repeated keys; the last occurrence wins.
				map.put(key, value);
				skipWhitespace();
				if (atEnd()) throw error("Unterminated object");
				char c = s.charAt(pos++);
				if (c == '}') return map;
				if (c != ',') throw error("Expected ',' or '}'");
				skipWhitespace();
				// Lenient: allow a trailing comma before '}'.
				if (!atEnd() && s.charAt(pos) == '}') {
					pos++;
					return map;
				}
			}
		}

		private List<Object> readArray(int depth) throws JsonException {
			pos++; // [
			List<Object> list = new ArrayList<>();
			skipWhitespace();
			if (!atEnd() && s.charAt(pos) == ']') {
				pos++;
				return list;
			}
			while (true) {
				skipWhitespace();
				list.add(readValue(depth + 1));
				skipWhitespace();
				if (atEnd()) throw error("Unterminated array");
				char c = s.charAt(pos++);
				if (c == ']') return list;
				if (c != ',') throw error("Expected ',' or ']'");
				skipWhitespace();
				if (!atEnd() && s.charAt(pos) == ']') {
					pos++;
					return list;
				}
			}
		}

		private String readString() throws JsonException {
			pos++; // opening quote
			StringBuilder sb = new StringBuilder();
			while (true) {
				if (atEnd()) throw error("Unterminated string");
				char c = s.charAt(pos++);
				if (c == '"') return sb.toString();
				if (c == '\\') {
					if (atEnd()) throw error("Unterminated escape");
					char e = s.charAt(pos++);
					switch (e) {
						case '"' -> sb.append('"');
						case '\\' -> sb.append('\\');
						case '/' -> sb.append('/');
						case 'b' -> sb.append('\b');
						case 'f' -> sb.append('\f');
						case 'n' -> sb.append('\n');
						case 'r' -> sb.append('\r');
						case 't' -> sb.append('\t');
						case 'u' -> {
							if (pos + 4 > s.length()) throw error("Truncated unicode escape");
							try {
								sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
							} catch (NumberFormatException ex) {
								throw error("Invalid unicode escape");
							}
							pos += 4;
						}
						default -> throw error("Invalid escape '\\" + e + "'");
					}
				} else {
					// Raw control characters (a literal line break in a description) are accepted, as Gson, which
					// Fabric Loader and Mixin read these files with, accepts them.
					sb.append(c);
				}
			}
		}

		private BigDecimal readNumber() throws JsonException {
			int start = pos;
			if (s.charAt(pos) == '-') pos++;
			while (!atEnd()) {
				char c = s.charAt(pos);
				if (c >= '0' && c <= '9' || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
					pos++;
				} else {
					break;
				}
			}
			String number = s.substring(start, pos);
			if (number.length() > 64) throw error("Number literal too long");
			try {
				return new BigDecimal(number);
			} catch (NumberFormatException e) {
				throw error("Invalid number '" + number + "'");
			}
		}
	}

	/** Convenience accessor for optional typed members of a parsed object. */
	public static List<Object> asList(Object value) {
		if (value == null) return Collections.emptyList();
		if (value instanceof List<?> list) {
			@SuppressWarnings("unchecked")
			List<Object> cast = (List<Object>) list;
			return cast;
		}
		return List.of(value);
	}
}
