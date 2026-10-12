package io.github.cmdralph.fabricated.core.bytecode;

import java.util.List;

import org.objectweb.asm.tree.AnnotationNode;

/** Lookups on ASM tree annotations. */
public final class Annotations {
	private Annotations() {
	}

	/** The first annotation with {@code desc} in {@code first}, then in {@code second}; either list may be null. */
	public static AnnotationNode find(List<AnnotationNode> first, List<AnnotationNode> second, String desc) {
		AnnotationNode a = find(first, desc);
		return a != null ? a : find(second, desc);
	}

	/** The first annotation with {@code desc} in {@code list} (which may be null), or {@code null}. */
	public static AnnotationNode find(List<AnnotationNode> list, String desc) {
		if (list == null) return null;
		for (AnnotationNode a : list) {
			if (a.desc.equals(desc)) return a;
		}
		return null;
	}

	/** The value of attribute {@code key} as ASM stores it, or {@code null} when not set. */
	public static Object value(AnnotationNode ann, String key) {
		if (ann.values == null) return null;
		for (int i = 0; i + 1 < ann.values.size(); i += 2) {
			if (key.equals(ann.values.get(i))) return ann.values.get(i + 1);
		}
		return null;
	}
}
