package io.github.cmdralph.fabricated.core.remap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites intermediary tokens inside free-form strings: mixin annotation values, refmap entries,
 * access widener files, service files and string constants used for reflection.
 *
 * <p>Only exact intermediary tokens are touched ({@code net/minecraft/class_N[$class_M...]} in slash or
 * dot form, and {@code method_N}/{@code field_N}/{@code comp_N} bounded by non-identifier characters),
 * so ordinary text is left alone. So is a method reference with its owner and descriptor whose name Intermediary leaves
 * under its official name and the runtime renames (PLAIN-1: Fabric API's transfer refmap names
 * {@code Lnet/minecraft/class_2357;dispense(...)}, SRG's {@code m_6115_}).
 */
public final class TokenRemapper {
	private static final Pattern CLASS_TOKEN = Pattern.compile("net([/.])minecraft\\1class_\\d+(?:\\$class_\\d+)*");
	private static final Pattern MEMBER_TOKEN = Pattern.compile("(?<![A-Za-z0-9_$])(?:method|field|comp)_\\d+(?![A-Za-z0-9_])");
	/** {@code Lowner;name(desc)} with a Minecraft owner, as refmaps and {@code @At} targets name a method. */
	private static final Pattern METHOD_REFERENCE = Pattern.compile("L(net/minecraft/class_\\d+(?:\\$class_\\d+)*);"
			+ "([A-Za-z_$][A-Za-z0-9_$]*)(\\((?:\\[*(?:[ZBCSIJFD]|L[^;()]+;))*\\)\\[*(?:[ZBCSIJFDV]|L[^;()]+;))");

	private final MappingTable table;

	public TokenRemapper(MappingTable table) {
		this.table = table;
	}

	public String remap(String s) {
		if (s == null || s.indexOf('_') < 0) return s;
		String result = s;
		// Before the class tokens: the methods are keyed by their intermediary owner and descriptor.
		if (table.hasPlainMembers() && result.indexOf('(') >= 0) result = remapPlainMethods(result);
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

	private String remapPlainMethods(String s) {
		Matcher m = METHOD_REFERENCE.matcher(s);
		StringBuilder sb = null;
		int copied = 0;
		while (m.find()) {
			String plain = table.mapPlainMethod(m.group(1), m.group(2), m.group(3));
			if (plain == null) continue;
			if (sb == null) sb = new StringBuilder(s.length());
			sb.append(s, copied, m.start(2)).append(plain);
			copied = m.end(2);
		}
		return sb == null ? s : sb.append(s, copied, s.length()).toString();
	}
}
