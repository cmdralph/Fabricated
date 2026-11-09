package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The mixin configs a Fabric mod's code adds to Mixin itself, through {@code Mixins.addConfiguration(String)}: ReplayMod's
 * mixin plugin adds most of its configs so, from its {@code getMixins}, as Mixin selects configs (LOADER-6). Fabric's Mixin,
 * which NeoForge runs, and SpongePowered's 0.8.5 give such a config Mixin's default environment. SpongePowered's 0.8.6
 * and 0.8.7, which Forge 52 and 61 to 66 run, give it none ({@code Mixins.addConfiguration(String)} passes a null
 * environment, read in their classes), and Mixin stopped the game reading it ({@code NullPointerException} in
 * {@code MixinConfig.onLoad}, "this.env" is null). There the calls go to the overload taking a config source, with none,
 * which gives the default environment in every Mixin since 0.8.6, as Fabric's one-argument form does.
 */
final class AddedMixinConfigs {
	private static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";
	private static final String ADD = "addConfiguration";
	private static final String BY_NAME = "(Ljava/lang/String;)V";
	private static final String SOURCE = "org/spongepowered/asm/mixin/extensibility/IMixinConfigSource";
	private static final String BY_NAME_AND_SOURCE = "(Ljava/lang/String;L" + SOURCE + ";)V";

	private AddedMixinConfigs() {
	}

	/** {@code classFile} with its calls of {@code Mixins.addConfiguration(String)} given the default environment, or null when it makes none. */
	static byte[] withEnvironment(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		if (!text.contains(MIXINS) || !text.contains(ADD)) return null;
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
