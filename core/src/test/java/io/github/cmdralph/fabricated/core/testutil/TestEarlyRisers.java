package io.github.cmdralph.fabricated.core.testutil;

import java.util.function.Consumer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Fabric ASM early risers as javac compiles them (CORPUS-80), in the shape of Farmer's Delight's 1.20.1
 * {@code FarmersDelightASM}: its recipe book type on both sides, and on a client a search category whose constructor
 * takes an array of the game's class, from a supplier a static method returns.
 */
public final class TestEarlyRisers {
	private static final String LOADER = "net/fabricmc/loader/api/FabricLoader";
	private static final String RESOLVER = "net/fabricmc/loader/api/MappingResolver";
	private static final String ENV_TYPE = "net/fabricmc/api/EnvType";
	private static final String TINKERERS = "com/chocohead/mm/api/ClassTinkerers";
	private static final String ADDER = "com/chocohead/mm/api/EnumAdder";
	private static final String SUPPLIER = "java/util/function/Supplier";

	private TestEarlyRisers() {
	}

	/**
	 * The early riser {@code name}: {@code bookType} gets {@code COOKING} (no arguments) on both sides; on a client
	 * {@code search} gets {@code COOKING_SEARCH}, its constructor taking an array of {@code element} (named as
	 * {@code "[L" + element + ";"}), from the supplier {@code supplierOwner.stacks()} returns. The class names are given
	 * dotted, as the mod passes them to {@code mapClassName("intermediary", ...)}.
	 */
	public static byte[] cooking(String name, String bookType, String search, String element, String supplierOwner) {
		ClassWriter cw = writer();
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", new String[] {"java/lang/Runnable"});
		constructor(cw);
		MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
		run.visitCode();
		run.visitMethodInsn(Opcodes.INVOKESTATIC, LOADER, "getInstance", "()L" + LOADER + ";", true);
		run.visitMethodInsn(Opcodes.INVOKEINTERFACE, LOADER, "getMappingResolver", "()L" + RESOLVER + ";", true);
		run.visitVarInsn(Opcodes.ASTORE, 1);
		map(run, bookType);
		run.visitVarInsn(Opcodes.ASTORE, 2);
		run.visitVarInsn(Opcodes.ALOAD, 2);
		run.visitMethodInsn(Opcodes.INVOKESTATIC, TINKERERS, "enumBuilder", "(Ljava/lang/String;)L" + ADDER + ";", false);
		run.visitLdcInsn("COOKING");
		run.visitInsn(Opcodes.ICONST_0);
		run.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "addEnum", "(Ljava/lang/String;[Ljava/lang/Object;)L" + ADDER + ";", false);
		run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "build", "()V", false);
		Label end = new Label();
		run.visitMethodInsn(Opcodes.INVOKESTATIC, LOADER, "getInstance", "()L" + LOADER + ";", true);
		run.visitMethodInsn(Opcodes.INVOKEINTERFACE, LOADER, "getEnvironmentType", "()L" + ENV_TYPE + ";", true);
		run.visitFieldInsn(Opcodes.GETSTATIC, ENV_TYPE, "CLIENT", "L" + ENV_TYPE + ";");
		run.visitJumpInsn(Opcodes.IF_ACMPNE, end);
		map(run, search);
		run.visitVarInsn(Opcodes.ASTORE, 3);
		map(run, element);
		run.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", new Handle(Opcodes.H_INVOKESTATIC,
				"java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
						+ "Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), "[L\u0001;");
		run.visitVarInsn(Opcodes.ASTORE, 4);
		run.visitVarInsn(Opcodes.ALOAD, 3);
		run.visitInsn(Opcodes.ICONST_1);
		run.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
		run.visitInsn(Opcodes.DUP);
		run.visitInsn(Opcodes.ICONST_0);
		run.visitVarInsn(Opcodes.ALOAD, 4);
		run.visitInsn(Opcodes.AASTORE);
		run.visitMethodInsn(Opcodes.INVOKESTATIC, TINKERERS, "enumBuilder", "(Ljava/lang/String;[Ljava/lang/String;)L" + ADDER + ";", false);
		run.visitLdcInsn("COOKING_SEARCH");
		run.visitMethodInsn(Opcodes.INVOKESTATIC, supplierOwner, "stacks", "()L" + SUPPLIER + ";", false);
		run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "addEnum", "(Ljava/lang/String;L" + SUPPLIER + ";)L" + ADDER + ";", false);
		run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "build", "()V", false);
		run.visitLabel(end);
		run.visitInsn(Opcodes.RETURN);
		run.visitMaxs(0, 0);
		run.visitEnd();
		if (supplierOwner.equals(name)) stacks(cw, name, element);
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An early riser whose {@code run} is {@code code} then a return. */
	public static byte[] riser(String name, Consumer<MethodVisitor> code) {
		ClassWriter cw = writer();
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", new String[] {"java/lang/Runnable"});
		constructor(cw);
		MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
		run.visitCode();
		code.accept(run);
		run.visitInsn(Opcodes.RETURN);
		run.visitMaxs(0, 0);
		run.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A class whose private {@code stacks()} returns a supplier, a lambda, of {@code new Object[] {new element[length]}},
	 * as Farmer's Delight's {@code getSearchCategoryStacks} returns its categories' icons.
	 */
	public static byte[] stacks(String name, String element) {
		ClassWriter cw = writer();
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
		constructor(cw);
		stacks(cw, name, element);
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void stacks(ClassWriter cw, String name, String element) {
		String elementName = element.replace('.', '/');
		MethodVisitor stacks = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "stacks", "()L" + SUPPLIER + ";", null, null);
		stacks.visitCode();
		stacks.visitInvokeDynamicInsn("get", "()L" + SUPPLIER + ";", new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				Type.getType("()Ljava/lang/Object;"), new Handle(Opcodes.H_INVOKESTATIC, name, "lambda$stacks$0", "()[Ljava/lang/Object;", false),
				Type.getType("()[Ljava/lang/Object;"));
		stacks.visitInsn(Opcodes.ARETURN);
		stacks.visitMaxs(0, 0);
		stacks.visitEnd();
		MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$stacks$0", "()[Ljava/lang/Object;",
				null, null);
		lambda.visitCode();
		lambda.visitInsn(Opcodes.ICONST_1);
		lambda.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		lambda.visitInsn(Opcodes.DUP);
		lambda.visitInsn(Opcodes.ICONST_0);
		lambda.visitInsn(Opcodes.ICONST_2);
		lambda.visitTypeInsn(Opcodes.ANEWARRAY, elementName);
		lambda.visitInsn(Opcodes.AASTORE);
		lambda.visitInsn(Opcodes.ARETURN);
		lambda.visitMaxs(0, 0);
		lambda.visitEnd();
	}

	private static void map(MethodVisitor mv, String dotted) {
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitLdcInsn("intermediary");
		mv.visitLdcInsn(dotted);
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESOLVER, "mapClassName", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", true);
	}

	private static void constructor(ClassWriter cw) {
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
	}

	private static ClassWriter writer() {
		return new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				return "java/lang/Object";
			}
		};
	}
}
