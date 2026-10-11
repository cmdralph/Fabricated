package io.github.cmdralph.fabricated.core.remap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites intermediary tokens inside free-form strings: mixin annotation values, refmap entries,
 * access widener files, service files and string constants used for reflection.
 *
 * <p>Only exact intermediary tokens are touched ({@code net/minecraft/class_N[$class_M...]} in slash or
 * dot form, and {@code method_N}/{@code field_N}/{@code comp_N} bounded by non-identifier characters),
 * so ordinary text is left alone.
 */
public final class TokenRemapper {
	private static final Pattern CLASS_TOKEN = Pattern.compile("net([/.])minecraft\\1class_\\d+(?:\\$class_\\d+)*");
	private static final Pattern MEMBER_TOKEN = Pattern.compile("(?<![A-Za-z0-9_$])(?:method|field|comp)_\\d+(?![A-Za-z0-9_])");

	private final MappingTable table;

	public TokenRemapper(MappingTable table) {
		this.table = table;
	}

	public String remap(String s) {
		if (s == null || s.indexOf('_') < 0) return s;
		String result = s;
		if (result.contains("class_")) {
			Matcher m = CLASS_TOKEN.matcher(result);
			StringBuilder sb = new StringBuilder(result.length());
			while (m.find()) {
				String sep = m.group(1);
				String internal = m.group().replace('.', '/');
				String mapped = table.mapClass(internal);
				if (sep.equals(".")) mapped = mapped.replace('/', '.');
				m.appendReplacement(sb, Matcher.quoteReplacement(mapped));
			}
			m.appendTail(sb);
			result = sb.toString();
		}
		if (result.contains("method_") || result.contains("field_") || result.contains("comp_")) {
			Matcher m = MEMBER_TOKEN.matcher(result);
			StringBuilder sb = new StringBuilder(result.length());
			while (m.find()) {
				String mapped = table.mapMemberFlat(m.group());
				m.appendReplacement(sb, Matcher.quoteReplacement(mapped != null ? mapped : m.group()));
			}
			m.appendTail(sb);
			result = sb.toString();
		}
		return result;
	}
}
