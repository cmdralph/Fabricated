package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;

/**
 * LOADER-6: ReplayMod's mixin plugin adds most of its mixin configs itself, with {@code Mixins.addConfiguration(String)}
 * from its {@code getMixins} ({@code ReplayModMMLauncher.run}), and SpongePowered's Mixin 0.8.6 and 0.8.7 give a config
 * added so no environment: every Forge 61 to 66 advanced corpus client stopped as Mixin read the first of them
 * ({@code NullPointerException}: "this.env" is null, extended run 42682670119). The calls go to the overload taking a
 * config source, with none, which gives the default environment, as Fabric's one-argument form does.
 */
class AddedMixinConfigsTest {
	private static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";
	private static final String SOURCE = "org/spongepowered/asm/mixin/extensibility/IMixinConfigSource";
	private static final String LAUNCHER = "com/example/core/Launcher";

	/** ReplayMod's {@code ReplayModMMLauncher.run}: two configs added by name, and one added with a source already. */
	private static byte[] launcher() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, LAUNCHER, null, "java/lang/Object", new String[] {"java/lang/Runnable"});
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
		run.visitCode();
		for (String config : List.of("mixins.core.example.json", "mixins.render.example.json")) {
			run.visitLdcInsn(config);
			run.visitMethodInsn(Opcodes.INVOKESTATIC, MIXINS, "addConfiguration", "(Ljava/lang/String;)V", false);
		}
		run.visitLdcInsn("mixins.sourced.example.json");
		run.visitInsn(Opcodes.ACONST_NULL);
		run.visitMethodInsn(Opcodes.INVOKESTATIC, MIXINS, "addConfiguration", "(Ljava/lang/String;L" + SOURCE + ";)V", false);
		run.visitInsn(Opcodes.RETURN);
		run.visitMaxs(0, 0);
		run.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Mixin's {@code Mixins} as SpongePowered's 0.8.7 has it, for the test: the one-argument form passes no environment,
	 * which Mixin stops on; the form taking a source records the config in {@code ADDED}, with the default environment.
	 */
	private static byte[] mixins() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, MIXINS, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "ADDED", "Ljava/util/List;", null, null).visitEnd();
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, MIXINS, "ADDED", "Ljava/util/List;");
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		MethodVisitor byName = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "addConfiguration", "(Ljava/lang/String;)V", null, null);
		byName.visitCode();
		byName.visitTypeInsn(Opcodes.NEW, "java/lang/NullPointerException");
		byName.visitInsn(Opcodes.DUP);
		byName.visitLdcInsn("\"this.env\" is null");
		byName.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V", false);
		byName.visitInsn(Opcodes.ATHROW);
		byName.visitMaxs(0, 0);
		byName.visitEnd();
		MethodVisitor sourced = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "addConfiguration", "(Ljava/lang/String;L" + SOURCE + ";)V",
				null, null);
		sourced.visitCode();
		sourced.visitFieldInsn(Opcodes.GETSTATIC, MIXINS, "ADDED", "Ljava/util/List;");
		sourced.visitVarInsn(Opcodes.ALOAD, 0);
		sourced.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
		sourced.visitInsn(Opcodes.POP);
		sourced.visitInsn(Opcodes.RETURN);
		sourced.visitMaxs(0, 0);
		sourced.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] source() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, SOURCE, null, "java/lang/Object", null);
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static List<?> run(byte[] launcher) throws Exception {
		ClassLoader loader = new BytesClassLoader(Map.of(LAUNCHER, launcher, MIXINS, mixins(), SOURCE, source()));
		Runnable run = (Runnable) Class.forName(LAUNCHER.replace('/', '.'), true, loader).getConstructor().newInstance();
		run.run();
		return (List<?>) Class.forName(MIXINS.replace('/', '.'), true, loader).getField("ADDED").get(null);
	}

	@Test
	void configsAddedByNameGetTheDefaultEnvironment() throws Exception {
		Throwable unchanged = null;
		try {
			run(launcher());
		} catch (NullPointerException e) {
			unchanged = e;
		}
		assertNotNull(unchanged, "the failure, as Forge's Mixin 0.8.7 has it");

		byte[] given = AddedMixinConfigs.withEnvironment(launcher());
		assertNotNull(given);
		assertEquals(List.of("mixins.core.example.json", "mixins.render.example.json", "mixins.sourced.example.json"), run(given),
				"each added once, through the overload that gives the default environment");
		assertNull(AddedMixinConfigs.withEnvironment(given), "nothing left to change");
	}

	/** MIXCFG-1: the names ReplayMod's launcher gives as constants, to either form, are found, for pre-flight to check them. */
	@Test
	void theConfigsAClassAddsByConstantNamesAreFound() {
		AddedMixinConfigs.Added added = AddedMixinConfigs.added(launcher());
		assertEquals(new AddedMixinConfigs.Added(List.of("mixins.core.example.json", "mixins.render.example.json", "mixins.sourced.example.json"), false),
				added);
		assertEquals(added, AddedMixinConfigs.added(AddedMixinConfigs.withEnvironment(launcher())), "as LOADER-6 rewrites them");
	}

	/** MIXCFG-1: a name the class's code computes (a parameter, or a choice of two constants jumping to the call) can't be read. */
	@Test
	void aConfigAddedByANameTheCodeComputesIsCounted() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, LAUNCHER, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "add", "(Ljava/lang/String;Z)V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, MIXINS, "addConfiguration", "(Ljava/lang/String;)V", false);
		org.objectweb.asm.Label other = new org.objectweb.asm.Label();
		org.objectweb.asm.Label call = new org.objectweb.asm.Label();
		mv.visitVarInsn(Opcodes.ILOAD, 1);
		mv.visitJumpInsn(Opcodes.IFEQ, other);
		mv.visitLdcInsn("mixins.one.example.json");
		mv.visitJumpInsn(Opcodes.GOTO, call);
		mv.visitLabel(other);
		mv.visitLdcInsn("mixins.other.example.json");
		mv.visitLabel(call);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, MIXINS, "addConfiguration", "(Ljava/lang/String;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		assertEquals(new AddedMixinConfigs.Added(List.of(), true), AddedMixinConfigs.added(cw.toByteArray()));
	}

	@Test
	void otherClassesAreLeftAsTheyAre() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Other", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "add", "()V", null, null);
		mv.visitCode();
		mv.visitLdcInsn("x.json");
		// Another class's method of the same name and descriptor.
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Mixins", "addConfiguration", "(Ljava/lang/String;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		assertNull(AddedMixinConfigs.withEnvironment(cw.toByteArray()));
		assertNull(AddedMixinConfigs.added(cw.toByteArray()));
	}

	@Test
	void onlySpongePoweredsMixinFrom086GivesNoEnvironment() {
		assertTrue(MixinCompat.addedConfigsWithoutEnvironment("0.8.7+Jenkins-b321.git-4053421aa10aaac6127d969028a29c94fe3054f6"), "Forge 61 to 66");
		assertTrue(MixinCompat.addedConfigsWithoutEnvironment("0.8.7"), "Forge 52 and 61 to 66");
		assertTrue(MixinCompat.addedConfigsWithoutEnvironment("0.8.6"));
		assertFalse(MixinCompat.addedConfigsWithoutEnvironment("0.8.5+Jenkins-b310.git-155314e6e91465dad727e621a569906a410cd6f4"), "Forge 47");
		assertFalse(MixinCompat.addedConfigsWithoutEnvironment("0.16.5+mixin.0.8.7"), "Fabric's Mixin, on NeoForge");
		assertFalse(MixinCompat.addedConfigsWithoutEnvironment("0.17.4+mixin.0.8.7"), "Fabric's Mixin, on NeoForge");
		assertFalse(MixinCompat.addedConfigsWithoutEnvironment(null));
	}
}
