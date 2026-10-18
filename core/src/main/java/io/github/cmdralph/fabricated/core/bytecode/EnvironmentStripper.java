package io.github.cmdralph.fabricated.core.bytecode;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.api.EnvType;

/**
 * Fabric Loader's environment stripping: members annotated {@code @Environment} for the other side are removed, and
 * interfaces listed in {@code @EnvironmentInterface} for the other side are dropped from the class. On a dedicated
 * server this keeps a common class that implements a client-only interface loadable. (A whole class annotated for the
 * other side is left alone: Fabric Loader refuses to load it, and NeoForge fails just the same when it is used.)
 */
public final class EnvironmentStripper {
	private static final String ENVIRONMENT_INTERFACE = "Lnet/fabricmc/api/EnvironmentInterface;";
	private static final String ENVIRONMENT_INTERFACES = "Lnet/fabricmc/api/EnvironmentInterfaces;";
	private static final byte[] MARKER = "Lnet/fabricmc/api/Environment".getBytes(StandardCharsets.UTF_8);

	private EnvironmentStripper() {
	}

	/** Whether a class file may carry the annotations (a byte search, no parsing): only those need {@link #strip}. */
	public static boolean mayNeedStripping(byte[] classFile) {
		outer:
		for (int i = 0; i <= classFile.length - MARKER.length; i++) {
			for (int j = 0; j < MARKER.length; j++) {
				if (classFile[i + j] != MARKER[j]) continue outer;
			}
			return true;
		}
		return false;
	}

	/** Strips {@code node} for {@code side}; returns whether anything was removed. */
	public static boolean strip(ClassNode node, EnvType side) {
		boolean changed = false;
		AnnotationNode list = Annotations.find(node.invisibleAnnotations, node.visibleAnnotations, ENVIRONMENT_INTERFACES);
		if (list != null && Annotations.value(list, "value") instanceof List<?> entries) {
			for (Object entry : entries) {
				if (entry instanceof AnnotationNode single) changed |= stripInterface(node, single, side);
			}
		}
		AnnotationNode single = Annotations.find(node.invisibleAnnotations, node.visibleAnnotations, ENVIRONMENT_INTERFACE);
		if (single != null) changed |= stripInterface(node, single, side);
		changed |= node.methods.removeIf(m -> otherSide(Annotations.find(m.invisibleAnnotations, m.visibleAnnotations, Annotations.ENVIRONMENT), side));
		changed |= node.fields.removeIf(f -> otherSide(Annotations.find(f.invisibleAnnotations, f.visibleAnnotations, Annotations.ENVIRONMENT), side));
		return changed;
	}

	private static boolean stripInterface(ClassNode node, AnnotationNode annotation, EnvType side) {
		if (!otherSide(annotation, side) || !(Annotations.value(annotation, "itf") instanceof Type itf)) return false;
		return node.interfaces.remove(itf.getInternalName());
	}

	/** Whether an {@code @Environment}-style annotation ({@code value = EnvType.X}) names the other side. */
	private static boolean otherSide(AnnotationNode annotation, EnvType side) {
		String named = Annotations.side(annotation);
		return named != null && !named.equals(side.name());
	}
}
