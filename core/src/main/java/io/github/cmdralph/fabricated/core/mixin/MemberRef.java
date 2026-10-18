package io.github.cmdralph.fabricated.core.mixin;

/**
 * A parsed Mixin member selector such as {@code tick}, {@code tick()V}, {@code Lnet/minecraft/A;tick()V}
 * or {@code Lnet/minecraft/A;level:Lnet/minecraft/Level;}.
 *
 * @param owner internal owner name, or {@code null}
 * @param name  member name, or {@code null} for a bare owner reference
 * @param desc  method descriptor (starting with {@code (}), field descriptor, or {@code null}
 * @param fuzzy whether the selector uses wildcards, quantifiers or regular expressions that this
 *              parser does not evaluate; fuzzy selectors are never reported as missing
 */
public record MemberRef(String owner, String name, String desc, boolean fuzzy) {

	public static MemberRef parse(String selector) {
		String s = selector.trim();
		if (s.isEmpty()) return new MemberRef(null, null, null, true);
		if (s.startsWith("/") || s.contains("*") || s.contains("{") || s.startsWith("@")) {
			return new MemberRef(null, null, null, true);
		}
		String owner = null;
		int paren = s.indexOf('(');
		int semicolon = s.indexOf(';');
		int firstColon = s.indexOf(':');
		// An owner (Lpkg/Owner;) ends before any descriptor: LOGGER:Lorg/slf4j/Logger; is a field named LOGGER.
		if (s.startsWith("L") && semicolon > 0 && (paren < 0 || semicolon < paren) && (firstColon < 0 || semicolon < firstColon)) {
			owner = s.substring(1, semicolon);
			s = s.substring(semicolon + 1);
		} else {
			int dot = s.lastIndexOf('.', paren < 0 ? s.length() : paren);
			int colon = s.indexOf(':');
			if (dot > 0 && (colon < 0 || dot < colon)) {
				owner = s.substring(0, dot).replace('.', '/');
				s = s.substring(dot + 1);
			}
		}
		if (s.isEmpty()) return new MemberRef(owner, null, null, false);
		paren = s.indexOf('(');
		int colon = s.indexOf(':');
		if (paren >= 0) {
			return new MemberRef(owner, s.substring(0, paren), s.substring(paren), false);
		}
		if (colon >= 0) {
			return new MemberRef(owner, s.substring(0, colon), s.substring(colon + 1), false);
		}
		return new MemberRef(owner, s, null, false);
	}

	/** This selector's name, or {@code fallback} when it has none. */
	public String nameOr(String fallback) {
		return name != null ? name : fallback;
	}

	/** This selector's descriptor if it is a method descriptor, otherwise {@code fallback}. */
	public String methodDescOr(String fallback) {
		return desc != null && desc.startsWith("(") ? desc : fallback;
	}

	/** This selector's descriptor if it is a field descriptor, otherwise {@code fallback}. */
	public String fieldDescOr(String fallback) {
		return desc != null && !desc.startsWith("(") ? desc : fallback;
	}

	/** Matches a method declaration; a descriptor without return type matches any return type. */
	public boolean matchesMethod(String methodName, String methodDesc) {
		if (name == null || !name.equals(methodName)) return false;
		if (desc == null) return true;
		return desc.endsWith(")") ? methodDesc.startsWith(desc) : methodDesc.equals(desc);
	}
}
