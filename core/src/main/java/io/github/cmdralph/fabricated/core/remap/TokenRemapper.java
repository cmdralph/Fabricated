package io.github.cmdralph.fabricated.core.remap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites intermediary tokens inside free-form strings: mixin annotation values, refmap entries,
 * access widener files, service files and string constants used for reflection.
 *
 * <p>Only exact intermediary tokens are touched ({@code net/minecraft/class_N[$class_M...]} in slash or
 * dot form, a class Intermediary leaves under its own name followed by nested classes it names, and
 * {@code method_N}/{@code field_N}/{@code comp_N} bounded by non-identifier characters), so ordinary text is left
 * alone. So is a method reference with its owner and descriptor whose name Intermediary leaves under its official name
 * and the runtime renames (PLAIN-1: Fabric API's transfer refmap names
 * {@code Lnet/minecraft/class_2357;dispense(...)}, SRG's {@code m_6115_}). Where one intermediary name stands for members
 * the runtime names apart, the descriptor a selector gives right after it ({@code name:desc}, {@code name(desc)ret})
 * picks the member when that alone tells them apart (REMAP-5: a record component's field and its accessor method, which
 * SRG names {@code f_} and {@code m_}).
 */
public final class TokenRemapper {
	private static final Pattern CLASS_TOKEN = Pattern.compile("net([/.])minecraft\\1class_\\d+(?:\\$class_\\d+)*");
	/**
	 * A class Intermediary leaves under its own name, outside {@code net/minecraft/}, with nested classes it names:
	 * Mojang's {@code com.mojang.blaze3d} classes keep their names in the game's jar, their nested classes don't
	 * ({@code com/mojang/blaze3d/opengl/GlStateManager$class_1017}). Iris's access widener opens such classes on 1.21.11,
	 * and ImmediatelyFast's a field of such a type on 1.21.1; unmapped, both lines were dropped (TOKEN-1).
	 */
	private static final Pattern NESTED_CLASS_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:([/.])[A-Za-z_][A-Za-z0-9_]*)(?:\\1[A-Za-z_][A-Za-z0-9_]*)*(?:\\$class_\\d+)+");
	private static final Pattern MEMBER_TOKEN = Pattern.compile("(?<![A-Za-z0-9_$])(?:method|field|comp)_\\d+(?![A-Za-z0-9_])");
	/** A member's descriptor in a selector, right after its name: a field's after a colon, or a method's. */
	private static final Pattern FOLLOWING_DESCRIPTOR = Pattern.compile(":(\\[*(?:[ZBCSIJFD]|L[^;()]+;))|(\\((?:\\[*(?:[ZBCSIJFD]|L[^;()]+;))*\\)\\[*(?:[ZBCSIJFDV]|L[^;()]+;))");
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
		// Before the class tokens too: a member's descriptor is read in intermediary names.
		if (result.contains("method_") || result.contains("field_") || result.contains("comp_")) {
			Matcher m = MEMBER_TOKEN.matcher(result);
			Matcher desc = FOLLOWING_DESCRIPTOR.matcher(result);
			StringBuilder sb = new StringBuilder(result.length());
			while (m.find()) {
				String mapped = table.mapMemberFlat(m.group());
				if (mapped == null && desc.region(m.end(), result.length()).lookingAt()) {
					mapped = table.mapMember(null, m.group(), desc.group(1) != null ? desc.group(1) : desc.group(2));
				}
				m.appendReplacement(sb, Matcher.quoteReplacement(mapped != null ? mapped : m.group()));
			}
			m.appendTail(sb);
			result = sb.toString();
		}
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
		if (result.contains("$class_")) result = remapNestedClasses(result);
		return result;
	}

	/**
	 * Maps each {@link #NESTED_CLASS_TOKEN} the table knows; in a descriptor the token starts with its {@code L}, which
	 * is tried without it. Others are left as they are.
	 */
	private String remapNestedClasses(String s) {
		Matcher m = NESTED_CLASS_TOKEN.matcher(s);
		StringBuilder sb = new StringBuilder(s.length());
		while (m.find()) {
			String token = m.group();
			String sep = m.group(1);
			String internal = token.replace('.', '/');
			String mapped = table.mapClass(internal);
			String prefix = "";
			if (mapped.equals(internal) && internal.startsWith("L") && internal.indexOf('/') > 1) {
				String bare = table.mapClass(internal.substring(1));
				if (!bare.equals(internal.substring(1))) {
					prefix = "L";
					mapped = bare;
				}
			}
			if (sep.equals(".")) mapped = mapped.replace('/', '.');
			m.appendReplacement(sb, Matcher.quoteReplacement(mapped.equals(internal) ? token : prefix + mapped));
		}
		m.appendTail(sb);
		return sb.toString();
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
