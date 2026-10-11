package io.github.cmdralph.fabricated.core.bytecode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Finds references from a mod's classes to classes or members that do not exist at runtime: the
 * classic {@code NoSuchMethodError}/{@code NoClassDefFoundError} after a NeoForge patch or a missing
 * Fabric API module.
 *
 * <p>Only references whose owner matches {@link #isChecked} are verified, against an index that is
 * complete for those namespaces. On a dedicated server, classes that reference client-only code or are
 * annotated {@code @Environment(EnvType.CLIENT)} are skipped because they never load there.
 */
public final class LinkageChecker {
	private static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
	private static final int MAX_ISSUES = 25;

	private final ClassIndex available;
	private final Predicate<String> isChecked;
	private final boolean clientSide;

	/**
	 * @param available index of every class that exists at runtime in the checked namespaces
	 * @param isChecked which owners (internal names) to verify
	 */
	public LinkageChecker(ClassIndex available, Predicate<String> isChecked, boolean clientSide) {
		this.available = available;
		this.isChecked = isChecked;
		this.clientSide = clientSide;
	}

	/** A missing reference. */
	public record Issue(String fromClass, String reference, String kind) {
		@Override
		public String toString() {
			return fromClass.replace('/', '.') + " -> missing " + kind + " " + reference;
		}
	}

	/** Checks one class file, appending at most a bounded number of issues to {@code out}. */
	public void check(byte[] classFile, List<Issue> out) {
		if (out.size() >= MAX_ISSUES) return;
		ClassNode node = new ClassNode();
		try {
			new ClassReader(classFile).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
		} catch (RuntimeException e) {
			out.add(new Issue("?", "unreadable class file", "class"));
			return;
		}
		if (isMixin(node)) return;
		if (!clientSide && (isClientOnly(node) || referencesClientCode(node))) return;

		Set<String> seen = new LinkedHashSet<>();
		checkClass(node.name, node.superName, seen, out);
		if (node.interfaces != null) {
			for (String itf : node.interfaces) checkClass(node.name, itf, seen, out);
		}
		for (MethodNode method : node.methods) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (out.size() >= MAX_ISSUES) return;
				switch (insn) {
					case MethodInsnNode m -> {
						if (m.owner.startsWith("[")) break;
						if (checkClass(node.name, m.owner, seen, out) && isChecked.test(m.owner)
								&& available.resolveMethod(m.owner, m.name + m.desc) == ClassIndex.Resolution.MISSING
								&& seen.add("m:" + m.owner + "." + m.name + m.desc)) {
							out.add(new Issue(node.name, m.owner.replace('/', '.') + "." + m.name + m.desc, "method"));
						}
					}
					case FieldInsnNode f -> {
						if (checkClass(node.name, f.owner, seen, out) && isChecked.test(f.owner)
								&& available.resolveField(f.owner, f.name + ":" + f.desc) == ClassIndex.Resolution.MISSING
								&& seen.add("f:" + f.owner + "." + f.name)) {
							out.add(new Issue(node.name, f.owner.replace('/', '.') + "." + f.name, "field"));
						}
					}
					case TypeInsnNode t -> checkClass(node.name, elementClass(t.desc), seen, out);
					case MultiANewArrayInsnNode a -> checkClass(node.name, elementClass(a.desc), seen, out);
					case LdcInsnNode ldc -> {
						if (ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) {
							checkClass(node.name, type.getInternalName(), seen, out);
						}
					}
					case InvokeDynamicInsnNode indy -> {
						for (Object arg : indy.bsmArgs) {
							if (arg instanceof Handle h) checkClass(node.name, h.getOwner(), seen, out);
						}
					}
					default -> {
					}
				}
			}
		}
	}

	/** @return whether the owner exists (or is not checked) */
	private boolean checkClass(String from, String owner, Set<String> seen, List<Issue> out) {
		if (owner == null || !isChecked.test(owner)) return true;
		if (available.contains(owner)) return true;
		if (seen.add("c:" + owner)) out.add(new Issue(from, owner.replace('/', '.'), "class"));
		return false;
	}

	private static String elementClass(String desc) {
		String d = desc;
		while (d.startsWith("[")) d = d.substring(1);
		if (d.startsWith("L") && d.endsWith(";")) return d.substring(1, d.length() - 1);
		if (d.length() == 1) return null; // primitive array
		return d;
	}

	/** Mixin classes reference shadowed members that Mixin, not the JVM, resolves. */
	private static boolean isMixin(ClassNode node) {
		for (List<AnnotationNode> list : java.util.Arrays.asList(node.visibleAnnotations, node.invisibleAnnotations)) {
			if (list == null) continue;
			for (AnnotationNode a : list) {
				if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return true;
			}
		}
		return false;
	}

	private static boolean isClientOnly(ClassNode node) {
		for (List<AnnotationNode> list : java.util.Arrays.asList(node.visibleAnnotations, node.invisibleAnnotations)) {
			if (list == null) continue;
			for (AnnotationNode a : list) {
				if (!a.desc.equals(ENVIRONMENT) || a.values == null) continue;
				for (int i = 0; i + 1 < a.values.size(); i += 2) {
					if (a.values.get(i + 1) instanceof String[] e && e.length == 2 && e[1].equals("CLIENT")) return true;
				}
			}
		}
		return false;
	}

	private static boolean referencesClientCode(ClassNode node) {
		if (isClientName(node.superName)) return true;
		if (node.interfaces != null) {
			for (String i : node.interfaces) {
				if (isClientName(i)) return true;
			}
		}
		for (MethodNode m : node.methods) {
			if (m.desc.contains("Lnet/minecraft/client/") || m.desc.contains("Lcom/mojang/blaze3d/")) return true;
			if (m.instructions == null) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode mi && isClientName(mi.owner)) return true;
				if (insn instanceof FieldInsnNode fi && isClientName(fi.owner)) return true;
				if (insn instanceof TypeInsnNode ti && isClientName(ti.desc)) return true;
			}
		}
		return false;
	}

	private static boolean isClientName(String name) {
		return name != null && (name.startsWith("net/minecraft/client/") || name.startsWith("com/mojang/blaze3d/"));
	}
}
