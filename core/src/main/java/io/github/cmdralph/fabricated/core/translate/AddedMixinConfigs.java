package io.github.cmdralph.fabricated.core.translate;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassFiles;

/**
 * The mixin configs a Fabric mod's code adds to Mixin itself, through {@code Mixins.addConfiguration(String)}: ReplayMod's
 * mixin plugin adds most of its configs so, from its {@code getMixins}, as Mixin selects configs (LOADER-6). Fabric's Mixin,
 * which NeoForge runs, and SpongePowered's 0.8.5 give such a config Mixin's default environment. SpongePowered's 0.8.6
 * and 0.8.7, which Forge 52 and 61 to 66 run, give it none ({@code Mixins.addConfiguration(String)} passes a null
 * environment, read in their classes), and Mixin stopped the game reading it ({@code NullPointerException} in
 * {@code MixinConfig.onLoad}, "this.env" is null). There the calls go to the overload taking a config source, with none,
 * which gives the default environment in every Mixin since 0.8.6, as Fabric's one-argument form does.
 *
 * <p>Pre-flight checks such a config as a listed one where the mod's code names it by a constant, as ReplayMod's does
 * (MIXCFG-1): {@link #added} finds those names.
 */
final class AddedMixinConfigs {
	private static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";
	private static final String ADD = "addConfiguration";
	private static final String BY_NAME = "(Ljava/lang/String;)V";
	private static final String SOURCE = "org/spongepowered/asm/mixin/extensibility/IMixinConfigSource";
	private static final String BY_NAME_AND_SOURCE = "(Ljava/lang/String;L" + SOURCE + ";)V";

	private AddedMixinConfigs() {
	}

	/**
	 * The configs a class adds to Mixin by name: {@code names}, those it gives as constants, and whether it adds others
	 * ({@code computed}) by names its code computes, which can't be read here.
	 */
	record Added(List<String> names, boolean computed) {
	}

	/** What {@code classFile} adds to Mixin through {@code Mixins}' methods adding configs, or null when it calls none. */
	static Added added(byte[] classFile) {
		if (!callsAdd(classFile)) return null;
		ClassNode node = new ClassNode();
		new ClassReader(classFile).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		Set<String> names = new TreeSet<>();
		boolean computed = false;
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || !call.owner.equals(MIXINS)
						|| !call.name.startsWith(ADD)) continue;
				// A constant name is javac's ldc right before the call, or right before the source given as null, a variable
				// or a field. Any other argument is computed, a label between them too (code jumping to the call, as a
				// choice of two names does).
				AbstractInsnNode name = call.desc.equals(BY_NAME) ? call.getPrevious()
						: call.desc.equals(BY_NAME_AND_SOURCE) && pushesSource(call.getPrevious()) ? call.getPrevious().getPrevious() : null;
				if (name instanceof LdcInsnNode ldc && ldc.cst instanceof String constant) names.add(constant);
				else computed = true;
			}
		}
		return names.isEmpty() && !computed ? null : new Added(List.copyOf(names), computed);
	}

	private static boolean pushesSource(AbstractInsnNode insn) {
		return insn != null && (insn.getOpcode() == Opcodes.ACONST_NULL || insn.getOpcode() == Opcodes.ALOAD || insn.getOpcode() == Opcodes.GETSTATIC);
	}

	private static boolean callsAdd(byte[] classFile) {
		return ClassFiles.contains(classFile, MIXINS) && ClassFiles.contains(classFile, ADD);
	}

	/** {@code classFile} with its calls of {@code Mixins.addConfiguration(String)} given the default environment, or null when it makes none. */
	static byte[] withEnvironment(byte[] classFile) {
		if (!callsAdd(classFile)) return null;
		boolean[] changed = {false};
		ClassReader reader = new ClassReader(classFile);
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					/** Whether a call here was given its source, one more stack slot. */
					private boolean pushedSource;

					@Override
					public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
						if (opcode == Opcodes.INVOKESTATIC && owner.equals(MIXINS) && method.equals(ADD) && desc.equals(BY_NAME)) {
							super.visitInsn(Opcodes.ACONST_NULL);
							super.visitMethodInsn(opcode, owner, method, BY_NAME_AND_SOURCE, itf);
							pushedSource = true;
							changed[0] = true;
							return;
						}
						super.visitMethodInsn(opcode, owner, method, desc, itf);
					}

					@Override
					public void visitMaxs(int maxStack, int maxLocals) {
						super.visitMaxs(pushedSource ? maxStack + 1 : maxStack, maxLocals);
					}
				};
			}
		}, 0);
		return changed[0] ? writer.toByteArray() : null;
	}
}
