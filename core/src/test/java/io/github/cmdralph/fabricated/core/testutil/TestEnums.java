package io.github.cmdralph.fabricated.core.testutil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Enums as javac compiles them, and enum mixins adding constants to them (ENUM-1). */
public final class TestEnums {
	private static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";
	private static final String RESERVED_CONSTRUCTOR = "Lnet/neoforged/fml/common/asm/enumextension/ReservedConstructor;";
	private static final String NAME_AND_ORDINAL = "(Ljava/lang/String;I";

	/**
	 * A constant of an enum mixin.
	 *
	 * @param constructor its constructor without the name and ordinal, as NeoForge's files give it
	 * @param arguments   loads the constructor's other arguments
	 */
	public record Constant(String name, String constructor, Consumer<MethodVisitor> arguments) {
		public static Constant plain(String name) {
			return new Constant(name, "()V", mv -> {
			});
		}
	}

	private TestEnums() {
	}

	/**
	 * An enum with these constructors (without the name and ordinal), those in {@code reserved} marked as NeoForge's
	 * reserved ones; one of NeoForge's extensible enums when {@code extensible}.
	 */
	public static byte[] target(String name, boolean extensible, List<String> constructors, List<String> reserved) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, name, null, "java/lang/Enum",
				extensible ? new String[] {EXTENSIBLE_ENUM} : null);
		for (String constructor : constructors) constructor(cw, constructor, reserved.contains(constructor));
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An enum mixin into {@code target} declaring {@code constants}; {@code extra} adds anything else. */
	public static byte[] mixin(String name, String target, List<Constant> constants, Consumer<ClassWriter> extra) {
		String self = "L" + name + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, name, null, "java/lang/Enum", null);
		AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = mixin.visitArray("value");
		targets.visit(null, Type.getObjectType(target));
		targets.visitEnd();
		mixin.visitEnd();
		for (Constant constant : constants) {
			cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, constant.name(), self, null, null).visitEnd();
		}
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + self, null, null).visitEnd();
		Set<String> constructors = new LinkedHashSet<>();
		for (Constant constant : constants) constructors.add(constant.constructor());
		for (String constructor : constructors) constructor(cw, constructor, false);

		MethodVisitor values = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$values", "()[" + self, null, null);
		values.visitCode();
		push(values, constants.size());
		values.visitTypeInsn(Opcodes.ANEWARRAY, name);
		for (int i = 0; i < constants.size(); i++) {
			values.visitInsn(Opcodes.DUP);
			push(values, i);
			values.visitFieldInsn(Opcodes.GETSTATIC, name, constants.get(i).name(), self);
			values.visitInsn(Opcodes.AASTORE);
		}
		values.visitInsn(Opcodes.ARETURN);
		values.visitMaxs(0, 0);
		values.visitEnd();

		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		for (int i = 0; i < constants.size(); i++) {
			Constant constant = constants.get(i);
			clinit.visitTypeInsn(Opcodes.NEW, name);
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitLdcInsn(constant.name());
			push(clinit, i);
			constant.arguments().accept(clinit);
			clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", NAME_AND_ORDINAL + constant.constructor().substring(1), false);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, constant.name(), self);
		}
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, name, "$values", "()[" + self, false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, "$VALUES", "[" + self);
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();

		extra.accept(cw);
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A private enum constructor calling {@code Enum}'s, marked as one NeoForge reserves when {@code reserved}. */
	private static void constructor(ClassWriter cw, String constructor, boolean reserved) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", NAME_AND_ORDINAL + constructor.substring(1), null, null);
		if (reserved) mv.visitAnnotation(RESERVED_CONSTRUCTOR, false).visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ILOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static void push(MethodVisitor mv, int value) {
		if (value <= 5) mv.visitInsn(Opcodes.ICONST_0 + value);
		else mv.visitIntInsn(Opcodes.BIPUSH, value);
	}
}
