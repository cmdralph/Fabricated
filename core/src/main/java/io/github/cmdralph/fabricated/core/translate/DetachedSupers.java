package io.github.cmdralph.fabricated.core.translate;

import java.util.Collection;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * SUPER-1: a mixin whose superclass isn't its target's (Mixin calls its super "detached") and that calls an interface's
 * default method as its super ({@code Interface.super.method()}) gets its target's superclass. SpongePowered's Mixin,
 * which Forge runs, rebinds every {@code super} call of a detached mixin to the class in the target's hierarchy that
 * implements the method; for an interface's default, found through the target's own interfaces, that is the target
 * itself, and the call keeps its interface reference, which the JVM refuses as the call first runs ("must be Methodref
 * constant"). Fabric's Mixin leaves calls to an interface alone (FabricMC/Mixin#150, from 0.15.0). With the mixin's
 * superclass its target's, Mixin keeps the mixin's calls as they are. Fabric API's renderer gives {@code SimpleModelWrapper},
 * a record, a mixin extending {@code Object} that calls {@code BlockStateModelPart.super.emitQuads}: on Forge 64 to 66
 * every client with the Fabric API jar crashed as it drew the first block.
 */
final class DetachedSupers {
	private DetachedSupers() {
	}

	/**
	 * The superclass {@code mixin} gets, or null where it keeps its own: it is no interface, its targets are classes with
	 * one superclass, which isn't the mixin's, and it calls an interface's method as its super. A mixin that also calls a
	 * method of its own superclass as its super keeps it, as that call needs Mixin's rebinding.
	 *
	 * @param classes reads any class (game, installed or the mod's), or returns null when absent
	 */
	static String alignedSuperclass(ClassNode mixin, Collection<String> targets, Function<String, ClassNode> classes) {
		if ((mixin.access & Opcodes.ACC_INTERFACE) != 0 || mixin.superName == null || targets.isEmpty()) return null;
		String targetSuper = null;
		for (String target : targets) {
			ClassNode node = classes.apply(target);
			if (node == null || (node.access & Opcodes.ACC_INTERFACE) != 0 || node.superName == null) return null;
			if (targetSuper == null) targetSuper = node.superName;
			else if (!targetSuper.equals(node.superName)) return null;
		}
		if (targetSuper.equals(mixin.superName)) return null;
		boolean interfaceSuperCall = false;
		for (MethodNode method : mixin.methods) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL || call.name.equals("<init>")) continue;
				if (call.owner.equals(mixin.superName)) return null;
				if (call.itf && !call.owner.equals(mixin.name)) interfaceSuperCall = true;
			}
		}
		return interfaceSuperCall ? targetSuper : null;
	}

	/**
	 * {@code data}, the mixin, with {@code superName} as its superclass: in its declaration, its generic signature, and its
	 * constructors' super calls, found as Mixin finds them (the first constructor call of the old superclass that no
	 * {@code new} precedes, so an initialiser's {@code new Object()} stays).
	 */
	static byte[] rewrite(byte[] data, String superName) {
		ClassReader reader = new ClassReader(data);
		ClassWriter writer = new ClassWriter(0);
		String oldSuper = reader.getSuperName();
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public void visit(int version, int access, String name, String signature, String superClass, String[] interfaces) {
				super.visit(version, access, name, withSuperclass(signature, oldSuper, superName), superName, interfaces);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
				if (!name.equals("<init>")) return mv;
				return new MethodVisitor(Opcodes.ASM9, mv) {
					private int news;
					private boolean found;

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if (opcode == Opcodes.NEW) news++;
						super.visitTypeInsn(opcode, type);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
						if (opcode == Opcodes.INVOKESPECIAL && method.equals("<init>")) {
							if (news > 0) {
								news--;
							} else if (!found && owner.equals(oldSuper)) {
								found = true;
								owner = superName;
							}
						}
						super.visitMethodInsn(opcode, owner, method, desc, itf);
					}
				};
			}
		}, 0);
		return writer.toByteArray();
	}

	/**
	 * A class's generic signature with {@code newSuper} as its superclass, or null: for no signature, or where the old
	 * superclass takes type arguments, which the new one doesn't (Mixin then reads the class's raw types).
	 */
	static String withSuperclass(String signature, String oldSuper, String newSuper) {
		if (signature == null || signature.contains("L" + oldSuper + "<")) return null;
		SignatureWriter writer = new SignatureWriter() {
			private boolean superclass;

			@Override
			public SignatureVisitor visitSuperclass() {
				superclass = true;
				return super.visitSuperclass();
			}

			@Override
			public void visitClassType(String name) {
				super.visitClassType(superclass && name.equals(oldSuper) ? newSuper : name);
				superclass = false;
			}
		};
		new SignatureReader(signature).accept(writer);
		return writer.toString();
	}
}
