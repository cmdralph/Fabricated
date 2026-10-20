package io.github.cmdralph.fabricated.core.bytecode;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/** Lookups on ASM tree annotations. */
public final class Annotations {
	public static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	public static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";

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

	/** The first annotation with {@code desc} on {@code method}, visible before invisible, or {@code null}. */
	public static AnnotationNode find(MethodNode method, String desc) {
		return find(method.visibleAnnotations, method.invisibleAnnotations, desc);
	}

	/** The first annotation with {@code desc} on {@code field}, visible before invisible, or {@code null}. */
	public static AnnotationNode find(FieldNode field, String desc) {
		return find(field.visibleAnnotations, field.invisibleAnnotations, desc);
	}

	/** The value of attribute {@code key} as ASM stores it, or {@code null} when not set. */
	public static Object value(AnnotationNode ann, String key) {
		if (ann.values == null) return null;
		for (int i = 0; i + 1 < ann.values.size(); i += 2) {
			if (key.equals(ann.values.get(i))) return ann.values.get(i + 1);
		}
		return null;
	}

	/** The annotations an attribute holds: one annotation, or an array of them; none for anything else. */
	public static List<AnnotationNode> list(Object value) {
		List<AnnotationNode> ret = new ArrayList<>();
		if (value instanceof AnnotationNode a) ret.add(a);
		if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof AnnotationNode a) ret.add(a);
			}
		}
		return ret;
	}

	/** Every annotation on {@code method}, visible before invisible. */
	public static List<AnnotationNode> all(MethodNode method) {
		List<AnnotationNode> ret = new ArrayList<>();
		if (method.visibleAnnotations != null) ret.addAll(method.visibleAnnotations);
		if (method.invisibleAnnotations != null) ret.addAll(method.invisibleAnnotations);
		return ret;
	}

	/** The side ({@code CLIENT} or {@code SERVER}) an {@code @Environment}-style annotation names, or null. */
	public static String side(AnnotationNode ann) {
		return ann != null && value(ann, "value") instanceof String[] env && env.length == 2 ? env[1] : null;
	}
}
