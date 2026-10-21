package io.github.cmdralph.fabricated.core.testutil;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * ENUM-5's shape, as Wilder Wild's grass colour meets NeoForge's {@code GrassColorModifier}: an extensible enum forwarding
 * {@code modify} to a {@code Color} delegate its second constructor takes, and an enum mixin adding a constant with a
 * body of its own, which its class initializer hands to another class.
 */
public final class TestEnumBodies {
	private static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";
	public static final String TARGET = "net/minecraft/world/Modifier";
	public static final String COLOR = "net/minecraft/world/Modifier$Color";
	public static final String MIXIN = "com/example/mixin/ModifierMixin";
	public static final String BODY = "com/example/mixin/ModifierMixin$1";
	public static final String HOLDER = "com/example/Holder";
	public static final String BODIES = "com/example/FabricatedEnumBodies";
	public static final String NAMED = "(Ljava/lang/String;ILjava/lang/String;)V";
	public static final String DELEGATED = "(Ljava/lang/String;ILjava/lang/String;L" + COLOR + ";)V";

	private TestEnumBodies() {
	}

	public static byte[] holder() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, HOLDER, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "TUNDRA", "L" + TARGET + ";", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The extensible enum: {@code modify(int)} forwards to the {@code Color} delegate the second constructor takes. */
	public static byte[] target() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, TARGET, null, "java/lang/Enum",
				new String[] {EXTENSIBLE_ENUM});
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[L" + TARGET + ";", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "delegate", "L" + COLOR + ";", null, null).visitEnd();
		for (String constructor : List.of(NAMED, DELEGATED)) cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constructor, null, null).visitEnd();
		MethodVisitor modify = cw.visitMethod(Opcodes.ACC_PUBLIC, "modify", "(I)I", null, null);
		modify.visitCode();
		modify.visitVarInsn(Opcodes.ALOAD, 0);
		modify.visitFieldInsn(Opcodes.GETFIELD, TARGET, "delegate", "L" + COLOR + ";");
		modify.visitVarInsn(Opcodes.ILOAD, 1);
		modify.visitMethodInsn(Opcodes.INVOKEINTERFACE, COLOR, "apply", "(I)I", true);
		modify.visitInsn(Opcodes.IRETURN);
		modify.visitMaxs(0, 0);
		modify.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	public static byte[] color() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, COLOR, null, "java/lang/Object", null);
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "apply", "(I)I", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The enum mixin as javac compiles Wilder Wild's: one constant with a body, then a static block handing it on. */
	public static byte[] mixin() {
		String self = "L" + MIXIN + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM | Opcodes.ACC_ABSTRACT, MIXIN, null, "java/lang/Enum", null);
		AnnotationVisitor ann = cw.visitAnnotation(Annotations.MIXIN, false);
		AnnotationVisitor targets = ann.visitArray("value");
		targets.visit(null, Type.getObjectType(TARGET));
		targets.visitEnd();
		ann.visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "TUNDRA", self, null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + self, null, null).visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", NAMED, null, null);
		init.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		init.visitEnd();
		MethodVisitor modify = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "modify", "(I)I", null, null);
		modify.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		modify.visitEnd();
		cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$values", "()[" + self, null, null).visitEnd();
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitTypeInsn(Opcodes.NEW, BODY);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitLdcInsn("TUNDRA");
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitLdcInsn("example_tundra");
		clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, BODY, "<init>", NAMED, false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, MIXIN, "TUNDRA", self);
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, MIXIN, "$values", "()[" + self, false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, MIXIN, "$VALUES", "[" + self);
		clinit.visitLdcInsn(Type.getObjectType(TARGET));
		clinit.visitFieldInsn(Opcodes.GETSTATIC, MIXIN, "TUNDRA", self);
		clinit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "cast", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		clinit.visitTypeInsn(Opcodes.CHECKCAST, TARGET);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, HOLDER, "TUNDRA", "L" + TARGET + ";");
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The constant's body: {@code modify} doubles its argument, or reads its instance when {@code usesThis}. */
	public static byte[] body(boolean usesThis) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, BODY, null, MIXIN, null);
		MethodVisitor init = cw.visitMethod(0, "<init>", NAMED, null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitVarInsn(Opcodes.ILOAD, 2);
		init.visitVarInsn(Opcodes.ALOAD, 3);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, MIXIN, "<init>", NAMED, false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor modify = cw.visitMethod(Opcodes.ACC_PUBLIC, "modify", "(I)I", null, null);
		modify.visitCode();
		if (usesThis) {
			modify.visitVarInsn(Opcodes.ALOAD, 0);
			modify.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);
		} else {
			modify.visitVarInsn(Opcodes.ILOAD, 1);
			modify.visitInsn(Opcodes.ICONST_2);
			modify.visitInsn(Opcodes.IMUL);
		}
		modify.visitInsn(Opcodes.IRETURN);
		modify.visitMaxs(0, 0);
		modify.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
