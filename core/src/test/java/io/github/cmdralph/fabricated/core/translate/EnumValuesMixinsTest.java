package io.github.cmdralph.fabricated.core.translate;

import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.BODIES;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.BODY;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.COLOR;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.DELEGATED;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.HOLDER;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.MIXIN;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.NAMED;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.TARGET;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.body;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.color;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.holder;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.mixin;
import static io.github.cmdralph.fabricated.core.testutil.TestEnumBodies.target;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

class EnumValuesMixinsTest {
	/**
	 * ENUM-5: Wilder Wild's grass colour, shaped as NeoForge's {@code GrassColorModifier}: the enum forwards
	 * {@code modify} to a {@code Color} delegate its second constructor takes, and the mixin's constant overrides it with a
	 * body of its own, then hands itself to another class from its class initializer.
	 */
	@Test
	void aConstantWithABodyBecomesAConstantWithADelegateAddedByAMixin() throws Exception {
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(mixin()), node(target()), Map.of(BODY, node(body(false)))::get, null, BODIES);
		assertNotNull(result);
		assertEquals(List.of("TUNDRA"), result.constants());

		// The delegate runs the body.
		byte[] delegate = result.classes().get(BODIES + "$0");
		assertNotNull(delegate, result.classes().keySet().toString());
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = name.equals(COLOR.replace('/', '.')) ? color() : name.equals((BODIES + "$0").replace('/', '.')) ? delegate : null;
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Class<?> delegateClass = Class.forName((BODIES + "$0").replace('/', '.'), true, loader);
		Object instance = delegateClass.getConstructor().newInstance();
		assertEquals(42, delegateClass.getMethod("apply", int.class).invoke(instance, 21));

		// The mixin: the enum's values shadowed, a constructor invoker taking the delegate, and the injector after the
		// values are set, which makes the constant, appends it and runs the rest of the class initializer.
		ClassNode mixin = node(result.mixin());
		assertEquals(MIXIN, mixin.name);
		assertTrue((mixin.access & Opcodes.ACC_ENUM) == 0, "an ordinary mixin, which FML doesn't refuse");
		assertNotNull(Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN));
		assertEquals("$VALUES", mixin.fields.getFirst().name);
		// Mixin reads its member annotations (kept at runtime) from the visible ones.
		assertEquals(List.of("Lorg/spongepowered/asm/mixin/Shadow;", "Lorg/spongepowered/asm/mixin/Final;", "Lorg/spongepowered/asm/mixin/Mutable;"),
				mixin.fields.getFirst().visibleAnnotations.stream().map(a -> a.desc).toList());
		MethodNode invoker = method(mixin, "fabricated$new0");
		assertEquals(DELEGATED.replace(")V", ")L" + TARGET + ";"), invoker.desc);
		assertEquals("<init>", Annotations.value(annotation(invoker, "Lorg/spongepowered/asm/mixin/gen/Invoker;"), "value"));
		MethodNode adds = method(mixin, "fabricated$addConstants");
		AnnotationNode inject = annotation(adds, "Lorg/spongepowered/asm/mixin/injection/Inject;");
		assertEquals(List.of("<clinit>"), Annotations.value(inject, "method"));
		AnnotationNode at = (AnnotationNode) ((List<?>) Annotations.value(inject, "at")).getFirst();
		assertEquals("L" + TARGET + ";$VALUES:[L" + TARGET + ";", Annotations.value(at, "target"));
		assertEquals(Opcodes.PUTSTATIC, Annotations.value(at, "opcode"));
		boolean made = false;
		boolean handed = false;
		for (AbstractInsnNode insn : adds.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals("fabricated$new0")) made = true;
			if (insn instanceof FieldInsnNode field && field.owner.equals(HOLDER)) handed = true;
			assertTrue(!(insn instanceof MethodInsnNode call) || !call.owner.equals(BODY), "the body's class isn't used");
		}
		assertTrue(made && handed, "makes the constant and hands it on");
	}

	/**
	 * The generated injector runs as Mixin merges it: in the enum's class initializer after its values are set, its own
	 * references and invoker pointing at the enum. The new constant follows the enum's own, at the next ordinal, with the
	 * body behind its delegate, and the class initializer's rest has run.
	 */
	@Test
	void theInjectorAddsTheConstantWhenMergedIntoTheEnum() throws Exception {
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(mixin()), node(target()), Map.of(BODY, node(body(false)))::get, null, BODIES);
		assertNotNull(result);
		assertAddsTheConstantWhenMerged(result, false);
	}

	/**
	 * ENUM-7 on Forge: an enum Forge makes extensible ({@code IExtensibleEnum}, as {@code MobCategory}) gets its constants
	 * from its own {@code create} factories, which Forge's enum extender writes once Mixin has applied. The extender finds
	 * the values array only while it is final, so the mixin shadows none (a {@code @Mutable} shadow made it non-final and
	 * stopped the Forge 66 corpus clients as {@code MobCategory} loaded, extended run 41569829683) and invokes no
	 * constructor; without a factory for the constructor a constant uses, it stays an enum mixin (pre-flight disables its mod).
	 * The factories run at the class initializer's end: each registers its constant in a map the initializer sets after the
	 * values array ({@code MobCategory.BY_NAME}), which was still null right after it and stopped the Forge 61 to 66 corpus
	 * servers and clients as {@code MobCategory} loaded (extended run 41574438102).
	 */
	@Test
	void anEnumForgeMakesExtensibleGetsItsConstantsFromItsOwnFactories() throws Exception {
		String create = "(Ljava/lang/String;Ljava/lang/String;L" + COLOR + ";)L" + TARGET + ";";
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(mixin()), forgeExtensible(create), Map.of(BODY, node(body(false)))::get, null, BODIES);
		assertNotNull(result);
		ClassNode mixin = node(result.mixin());
		assertTrue(mixin.fields.isEmpty(), "no shadow of the values array: " + mixin.fields.stream().map(f -> f.name).toList());
		assertTrue(mixin.methods.stream().noneMatch(m -> m.visibleAnnotations != null
				&& m.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;"))), "no constructor invoker");
		MethodNode adds = method(mixin, "fabricated$addConstants");
		AnnotationNode at = (AnnotationNode) ((List<?>) Annotations.value(annotation(adds, "Lorg/spongepowered/asm/mixin/injection/Inject;"), "at")).getFirst();
		assertEquals("TAIL", Annotations.value(at, "value"), "after the statics the factory registers its constant in");
		assertNull(Annotations.value(at, "target"));
		boolean created = false;
		for (AbstractInsnNode insn : adds.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(TARGET) && call.name.equals("create") && call.desc.equals(create)) created = true;
			assertTrue(!(insn instanceof FieldInsnNode field) || !field.name.equals("$VALUES"), "the factory appends to the values array");
		}
		assertTrue(created, "makes the constant with the enum's factory");
		assertAddsTheConstantWhenMerged(result, true);

		assertNull(EnumValuesMixins.of(node(mixin()), forgeExtensible("(Ljava/lang/String;Ljava/lang/String;)L" + TARGET + ";"),
				Map.of(BODY, node(body(false)))::get, null, BODIES), "no factory for the delegate's constructor");
	}

	/** {@link #target()} as Forge makes an enum extensible: its marker, and a {@code create} factory of that descriptor. */
	private static ClassNode forgeExtensible(String create) {
		ClassNode target = node(target());
		target.interfaces = new java.util.ArrayList<>(List.of(EnumValuesMixins.FORGE_EXTENSIBLE_ENUM));
		target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", create, null, null));
		return target;
	}

	/**
	 * Runs the generated injector as Mixin merges it into a runnable {@link #target()} with NONE, and, with
	 * {@code factory}, a {@code create} factory written as Forge's enum extender writes it: the constant follows the enum's
	 * own, at the next ordinal, with the body behind its delegate, and the class initializer's rest has run. The factory
	 * registers the constant in the enum's map by name, as Forge's {@code IExtensibleEnum.init} does.
	 */
	private static void assertAddsTheConstantWhenMerged(EnumValuesMixins.Result result, boolean factory) throws Exception {
		ClassNode handler = new ClassNode();
		new ClassReader(result.mixin()).accept(new org.objectweb.asm.commons.ClassRemapper(handler,
				new org.objectweb.asm.commons.SimpleRemapper(MIXIN, TARGET)), 0);
		Map<String, byte[]> classes = new java.util.HashMap<>(Map.of(TARGET, merged(method(handler, "fabricated$addConstants"), factory), COLOR, color(),
				HOLDER, holder()));
		result.classes().forEach(classes::put);
		ClassLoader loader = new ClassLoader(EnumValuesMixinsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get(name.replace('.', '/'));
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Class<?> modifier = Class.forName(TARGET.replace('/', '.'), true, loader);
		Object[] values = (Object[]) modifier.getMethod("values").invoke(null);
		assertEquals(2, values.length);
		Enum<?> tundra = (Enum<?>) values[1];
		assertEquals("TUNDRA", tundra.name());
		assertEquals(1, tundra.ordinal());
		assertEquals(42, modifier.getMethod("modify", int.class).invoke(tundra, 21));
		assertTrue(Class.forName(HOLDER.replace('/', '.'), true, loader).getField("TUNDRA").get(null) == tundra);
		if (factory) {
			java.lang.reflect.Field byName = modifier.getDeclaredField("BY_NAME");
			byName.setAccessible(true);
			assertEquals(Map.of("NONE", values[0], "TUNDRA", tundra), byName.get(null));
		}
	}

	/**
	 * A runnable {@link #target()} with NONE, and the handler and an invoker merged in as Mixin would; with
	 * {@code factory}, the delegate constructor's {@code create} factory as Forge's enum extender writes it, and a map by
	 * name set after the values array, as Forge's {@code MobCategory} has, with the handler called at the end (TAIL).
	 */
	private static byte[] merged(MethodNode handler, boolean factory) {
		String self = "L" + TARGET + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, TARGET, null, "java/lang/Enum", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "NONE", self, null, null).visitEnd();
		// Not final: Mixin removes final from a field its mixin shadows as @Mutable.
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + self, null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "delegate", "L" + COLOR + ";", null, null).visitEnd();
		if (factory) cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "BY_NAME", "Ljava/util/Map;", null, null).visitEnd();
		for (String constructor : List.of(NAMED, DELEGATED)) {
			MethodVisitor init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", constructor, null, null);
			init.visitCode();
			init.visitVarInsn(Opcodes.ALOAD, 0);
			init.visitVarInsn(Opcodes.ALOAD, 1);
			init.visitVarInsn(Opcodes.ILOAD, 2);
			init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
			init.visitVarInsn(Opcodes.ALOAD, 0);
			if (constructor.equals(DELEGATED)) init.visitVarInsn(Opcodes.ALOAD, 4);
			else init.visitInsn(Opcodes.ACONST_NULL);
			init.visitFieldInsn(Opcodes.PUTFIELD, TARGET, "delegate", "L" + COLOR + ";");
			init.visitInsn(Opcodes.RETURN);
			init.visitMaxs(0, 0);
			init.visitEnd();
		}
		MethodVisitor modify = cw.visitMethod(Opcodes.ACC_PUBLIC, "modify", "(I)I", null, null);
		modify.visitCode();
		modify.visitVarInsn(Opcodes.ALOAD, 0);
		modify.visitFieldInsn(Opcodes.GETFIELD, TARGET, "delegate", "L" + COLOR + ";");
		modify.visitVarInsn(Opcodes.ILOAD, 1);
		modify.visitMethodInsn(Opcodes.INVOKEINTERFACE, COLOR, "apply", "(I)I", true);
		modify.visitInsn(Opcodes.IRETURN);
		modify.visitMaxs(0, 0);
		modify.visitEnd();
		MethodVisitor values = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[" + self, null, null);
		values.visitCode();
		values.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "$VALUES", "[" + self);
		values.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[" + self, "clone", "()Ljava/lang/Object;", false);
		values.visitTypeInsn(Opcodes.CHECKCAST, "[" + self);
		values.visitInsn(Opcodes.ARETURN);
		values.visitMaxs(0, 0);
		values.visitEnd();
		MethodVisitor invoker = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "fabricated$new0", DELEGATED.replace(")V", ")" + self), null, null);
		invoker.visitCode();
		invoker.visitTypeInsn(Opcodes.NEW, TARGET);
		invoker.visitInsn(Opcodes.DUP);
		invoker.visitVarInsn(Opcodes.ALOAD, 0);
		invoker.visitVarInsn(Opcodes.ILOAD, 1);
		invoker.visitVarInsn(Opcodes.ALOAD, 2);
		invoker.visitVarInsn(Opcodes.ALOAD, 3);
		invoker.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", DELEGATED, false);
		invoker.visitInsn(Opcodes.ARETURN);
		invoker.visitMaxs(0, 0);
		invoker.visitEnd();
		if (factory) {
			// new Modifier(name, $VALUES.length, id, color), appended to $VALUES (which the extender leaves non-final).
			MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED, "create",
					"(Ljava/lang/String;Ljava/lang/String;L" + COLOR + ";)" + self, null, null);
			create.visitCode();
			create.visitTypeInsn(Opcodes.NEW, TARGET);
			create.visitInsn(Opcodes.DUP);
			create.visitVarInsn(Opcodes.ALOAD, 0);
			create.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "$VALUES", "[" + self);
			create.visitInsn(Opcodes.ARRAYLENGTH);
			create.visitVarInsn(Opcodes.ALOAD, 1);
			create.visitVarInsn(Opcodes.ALOAD, 2);
			create.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", DELEGATED, false);
			create.visitVarInsn(Opcodes.ASTORE, 3);
			create.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "$VALUES", "[" + self);
			create.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "$VALUES", "[" + self);
			create.visitInsn(Opcodes.ARRAYLENGTH);
			create.visitInsn(Opcodes.ICONST_1);
			create.visitInsn(Opcodes.IADD);
			create.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false);
			create.visitTypeInsn(Opcodes.CHECKCAST, "[" + self);
			create.visitVarInsn(Opcodes.ASTORE, 4);
			create.visitVarInsn(Opcodes.ALOAD, 4);
			create.visitVarInsn(Opcodes.ALOAD, 4);
			create.visitInsn(Opcodes.ARRAYLENGTH);
			create.visitInsn(Opcodes.ICONST_1);
			create.visitInsn(Opcodes.ISUB);
			create.visitVarInsn(Opcodes.ALOAD, 3);
			create.visitInsn(Opcodes.AASTORE);
			create.visitVarInsn(Opcodes.ALOAD, 4);
			create.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "$VALUES", "[" + self);
			// IExtensibleEnum.init, as MobCategory's: BY_NAME.put(name, constant), which throws while BY_NAME is unset.
			create.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "BY_NAME", "Ljava/util/Map;");
			create.visitVarInsn(Opcodes.ALOAD, 0);
			create.visitVarInsn(Opcodes.ALOAD, 3);
			create.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
			create.visitInsn(Opcodes.POP);
			create.visitVarInsn(Opcodes.ALOAD, 3);
			create.visitInsn(Opcodes.ARETURN);
			create.visitMaxs(0, 0);
			create.visitEnd();
		}
		handler.invisibleAnnotations = null;
		handler.accept(cw);
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitTypeInsn(Opcodes.NEW, TARGET);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitLdcInsn("NONE");
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitLdcInsn("none");
		clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, TARGET, "<init>", NAMED, false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "NONE", self);
		clinit.visitInsn(Opcodes.ICONST_1);
		clinit.visitTypeInsn(Opcodes.ANEWARRAY, TARGET);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "NONE", self);
		clinit.visitInsn(Opcodes.AASTORE);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "$VALUES", "[" + self);
		if (factory) {
			clinit.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitLdcInsn("NONE");
			clinit.visitFieldInsn(Opcodes.GETSTATIC, TARGET, "NONE", self);
			clinit.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
			clinit.visitInsn(Opcodes.POP);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, TARGET, "BY_NAME", "Ljava/util/Map;");
		}
		clinit.visitInsn(Opcodes.ACONST_NULL);
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET, handler.name, handler.desc, false);
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * ENUM-7: where the game's names aren't javac's (Forge 47 runs SRG names, and ProGuard renamed the enum's values
	 * array), the generated mixin shadows and injects at the enum's one static synthetic array of its own type.
	 */
	@Test
	void theEnumsValuesArrayIsFoundByItsTypeWhereTheGameRenamedIt() throws Exception {
		ClassNode target = node(target());
		target.fields.stream().filter(f -> f.name.equals("$VALUES")).findFirst().orElseThrow().name = "f_48371_";
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(mixin()), target, Map.of(BODY, node(body(false)))::get, null, BODIES);
		assertNotNull(result);
		ClassNode mixin = node(result.mixin());
		assertEquals("f_48371_", mixin.fields.getFirst().name);
		AnnotationNode inject = annotation(method(mixin, "fabricated$addConstants"), "Lorg/spongepowered/asm/mixin/injection/Inject;");
		AnnotationNode at = (AnnotationNode) ((List<?>) Annotations.value(inject, "at")).getFirst();
		assertEquals("L" + TARGET + ";f_48371_:[L" + TARGET + ";", Annotations.value(at, "target"));
		for (AbstractInsnNode insn : method(mixin, "fabricated$addConstants").instructions) {
			assertTrue(!(insn instanceof FieldInsnNode field) || !field.name.equals("$VALUES"), "only the enum's own name");
		}

		// Two such arrays: which is the values isn't known, so the enum mixin stays one (and pre-flight judges it).
		org.objectweb.asm.tree.FieldNode other = new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
				"f_48372_", "[L" + TARGET + ";", null, null);
		target.fields.add(other);
		assertNull(EnumValuesMixins.of(node(mixin()), target, Map.of(BODY, node(body(false)))::get, null, BODIES));
	}

	@Test
	void aBodyUsingItsInstanceStaysAnEnumMixin() throws Exception {
		assertNull(EnumValuesMixins.of(node(mixin()), node(target()), Map.of(BODY, node(body(true)))::get, null, BODIES));
	}

	private static final String BOOK_TYPE = "net/minecraft/world/inventory/BookType";
	private static final String BOOK_SETTINGS = "net/minecraft/stats/BookSettings";
	private static final String BOOK_MIXIN = "com/example/mixin/BookTypeMixin";
	private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String CONSTANTS = "com/example/FabricatedEnumConstants";
	private static final String READER = "com/example/Reader";

	/**
	 * CORPUS-80: Farmer's Delight's recipe book type on Forge 61 to 66, whose {@code RecipeBookType} Forge makes extensible
	 * with an {@code init} that calls {@code RecipeBookSettings.register}, which throws "This is not implemented yet". Each
	 * constant Forge's {@code create} factory makes goes through {@code init}, so the mixin cancels it for the mod's
	 * constants, which Fabric gives no such hook, and only for those: another constant's {@code create} still fails as it
	 * would without Fabricated. Where {@code init} returns (Forge 47's registers the recipe book's tags), it runs.
	 */
	@Test
	void aForgeEnumWhoseInitNeverReturnsGetsItsConstantsWithoutIt() throws Exception {
		Map<String, ClassNode> game = Map.of(BOOK_SETTINGS, node(bookSettings(true)));
		ClassNode target = node(bookType(false));
		assertTrue(!EnumValuesMixins.initReturns(target, game::get), "register throws");
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(bookMixin()), target, null, game::get, "com/example/FabricatedEnumBodies");
		assertNotNull(result);
		ClassNode mixin = node(result.mixin());
		AnnotationNode inject = annotation(method(mixin, "fabricated$skipInit"), "Lorg/spongepowered/asm/mixin/injection/Inject;");
		assertEquals(List.of("init()V"), Annotations.value(inject, "method"));
		assertEquals(Boolean.TRUE, Annotations.value(inject, "cancellable"));
		AnnotationNode at = (AnnotationNode) ((List<?>) Annotations.value(inject, "at")).getFirst();
		assertEquals("HEAD", Annotations.value(at, "value"));

		ClassLoader loader = loader(Map.of(BOOK_TYPE, mergedBookType(result.mixin()), BOOK_SETTINGS, bookSettings(true), CALLBACK_INFO, callbackInfo()));
		Class<?> type = Class.forName(BOOK_TYPE.replace('/', '.'), true, loader);
		Object[] values = (Object[]) type.getMethod("values").invoke(null);
		assertEquals(List.of("CRAFTING", "COOKING"), java.util.Arrays.stream(values).map(v -> ((Enum<?>) v).name()).toList());
		assertEquals(1, ((Enum<?>) values[1]).ordinal());
		java.lang.reflect.InvocationTargetException other = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> type.getMethod("create", String.class).invoke(null, "OTHER"));
		assertEquals("This is not implemented yet", other.getCause().getMessage(), "another constant's init still runs");

		// Forge 47's init returns: the constants get it as Forge mods' own do.
		Map<String, ClassNode> registering = Map.of(BOOK_SETTINGS, node(bookSettings(false)));
		assertTrue(EnumValuesMixins.initReturns(target, registering::get));
		EnumValuesMixins.Result kept = EnumValuesMixins.of(node(bookMixin()), target, null, registering::get, "com/example/FabricatedEnumBodies");
		assertTrue(node(kept.mixin()).methods.stream().noneMatch(m -> m.name.equals("fabricated$skipInit")));
		// What can't be told keeps it: init branching, or calling what isn't here.
		assertTrue(EnumValuesMixins.initReturns(target, name -> null));
		assertTrue(EnumValuesMixins.initReturns(node(bookType(true)), game::get), "a branch");
	}

	/**
	 * CORPUS-80: the generated mixin adds no fields, so the mod's reads of its constants as fields of the enum (Farmer's
	 * Delight's {@code FDRecipeBookTypes.COOKING = RecipeBookType.FARMERSDELIGHT_COOKING}) read them from a holder, which
	 * finds them among the enum's values by name. The enum's own constants are read as they were, and a constant the enum
	 * lacks fails the holder naming it and the mixin.
	 */
	@Test
	void theModsReadsOfTheConstantsAsFieldsOfTheEnumReadThemFromAHolder() throws Exception {
		Map<String, ClassNode> game = Map.of(BOOK_SETTINGS, node(bookSettings(true)));
		EnumValuesMixins.Result result = EnumValuesMixins.of(node(bookMixin()), node(bookType(false)), null, game::get, "com/example/FabricatedEnumBodies");
		assertEquals(List.of("COOKING"), result.constants());
		byte[] reader = EnumValuesMixins.withHeldConstants(reader(), BOOK_TYPE, result.constants(), CONSTANTS);
		assertNotNull(reader);
		ClassNode rewritten = node(reader);
		List<String> owners = new java.util.ArrayList<>();
		for (MethodNode m : rewritten.methods) {
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof FieldInsnNode f) owners.add(f.owner + "." + f.name);
				if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc && ldc.cst instanceof org.objectweb.asm.Handle h) owners.add(h.getOwner() + "." + h.getName());
			}
		}
		assertEquals(List.of(CONSTANTS + ".COOKING", BOOK_TYPE + ".CRAFTING", CONSTANTS + ".COOKING"), owners);
		assertNull(EnumValuesMixins.withHeldConstants(bookSettings(true), BOOK_TYPE, result.constants(), CONSTANTS), "reads none");

		Map<String, byte[]> classes = new java.util.HashMap<>(Map.of(BOOK_TYPE, mergedBookType(result.mixin()), BOOK_SETTINGS, bookSettings(true),
				CALLBACK_INFO, callbackInfo(), READER, reader, CONSTANTS, EnumValuesMixins.holderClass(CONSTANTS, BOOK_TYPE, result.constants(), "com.example.mixin.BookTypeMixin")));
		ClassLoader loader = loader(classes);
		Class<?> type = Class.forName(BOOK_TYPE.replace('/', '.'), false, loader);
		Object cooking = Class.forName(READER.replace('/', '.'), true, loader).getMethod("cooking").invoke(null);
		assertEquals("COOKING", ((Enum<?>) cooking).name());
		assertTrue(cooking == ((Object[]) type.getMethod("values").invoke(null))[1], "the enum's own constant");

		classes.put(CONSTANTS, EnumValuesMixins.holderClass(CONSTANTS, BOOK_TYPE, List.of("MISSING"), "com.example.mixin.BookTypeMixin"));
		Class<?> holder = Class.forName(CONSTANTS.replace('/', '.'), false, loader(classes));
		Throwable failed = org.junit.jupiter.api.Assertions.assertThrows(ExceptionInInitializerError.class,
				() -> java.lang.invoke.MethodHandles.lookup().ensureInitialized(holder)).getCause();
		assertEquals("Fabricated: net.minecraft.world.inventory.BookType has no constant MISSING once initialized: com.example.mixin.BookTypeMixin,"
				+ " which adds it, did not apply", failed.getMessage());
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(EnumValuesMixinsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get(name.replace('.', '/'));
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
	}

	/** Farmer's Delight's enum mixin, shaped: one plain constant, COOKING. */
	private static byte[] bookMixin() {
		return io.github.cmdralph.fabricated.core.testutil.TestEnums.mixin(BOOK_MIXIN, BOOK_TYPE,
				List.of(io.github.cmdralph.fabricated.core.testutil.TestEnums.Constant.plain("COOKING")), cw -> {
				});
	}

	/** Forge 61 to 66's {@code RecipeBookSettings}, shaped: {@code register} throws, or (Forge 47's) returns. */
	private static byte[] bookSettings(boolean throwing) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, BOOK_SETTINGS, null, "java/lang/Object", null);
		MethodVisitor register = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "(L" + BOOK_TYPE + ";)V", null, null);
		register.visitCode();
		if (throwing) {
			register.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
			register.visitInsn(Opcodes.DUP);
			register.visitLdcInsn("This is not implemented yet");
			register.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
			register.visitInsn(Opcodes.ATHROW);
		} else {
			register.visitInsn(Opcodes.RETURN);
		}
		register.visitMaxs(0, 0);
		register.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Forge's {@code RecipeBookType}, shaped, as pre-flight reads it: Forge's marker, CRAFTING, its {@code create(String)}
	 * factory (Forge's enum extender writes the code), and its {@code init} calling {@code register}; with {@code branching},
	 * only when a static says so.
	 */
	private static byte[] bookType(boolean branching) {
		String self = "L" + BOOK_TYPE + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, BOOK_TYPE, null, "java/lang/Enum",
				new String[] {EnumValuesMixins.FORGE_EXTENSIBLE_ENUM});
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "CRAFTING", self, null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", "[" + self, null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "REGISTER", "Z", null, null).visitEnd();
		MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
		ctor.visitCode();
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();
		MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "(Ljava/lang/String;)" + self, null, null);
		create.visitCode();
		create.visitInsn(Opcodes.ACONST_NULL);
		create.visitInsn(Opcodes.ARETURN);
		create.visitMaxs(0, 0);
		create.visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "init", "()V", null, null);
		init.visitCode();
		org.objectweb.asm.Label skip = new org.objectweb.asm.Label();
		if (branching) {
			init.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "REGISTER", "Z");
			init.visitJumpInsn(Opcodes.IFEQ, skip);
		}
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESTATIC, BOOK_SETTINGS, "register", "(" + self + ")V", false);
		init.visitLabel(skip);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Forge 61 to 66's {@code RecipeBookType} as it runs: CRAFTING; {@code create} as Forge's enum extender writes it (the
	 * constant appended to the values array, which the extender leaves non-final, then {@code init}); {@code init} calling
	 * {@code register}, with the mixin's {@code fabricated$skipInit} called at its head as Mixin would; and the mixin's
	 * {@code fabricated$addConstants} at the class initializer's end.
	 */
	private static byte[] mergedBookType(byte[] mixinBytes) {
		String self = "L" + BOOK_TYPE + ";";
		String array = "[" + self;
		ClassNode mixin = new ClassNode();
		new ClassReader(mixinBytes).accept(new org.objectweb.asm.commons.ClassRemapper(mixin, new org.objectweb.asm.commons.SimpleRemapper(BOOK_MIXIN, BOOK_TYPE)), 0);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, BOOK_TYPE, null, "java/lang/Enum", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "CRAFTING", self, null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$VALUES", array, null, null).visitEnd();
		MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
		ctor.visitCode();
		ctor.visitVarInsn(Opcodes.ALOAD, 0);
		ctor.visitVarInsn(Opcodes.ALOAD, 1);
		ctor.visitVarInsn(Opcodes.ILOAD, 2);
		ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();
		MethodVisitor values = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()" + array, null, null);
		values.visitCode();
		values.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "$VALUES", array);
		values.visitMethodInsn(Opcodes.INVOKEVIRTUAL, array, "clone", "()Ljava/lang/Object;", false);
		values.visitTypeInsn(Opcodes.CHECKCAST, array);
		values.visitInsn(Opcodes.ARETURN);
		values.visitMaxs(0, 0);
		values.visitEnd();
		MethodVisitor create = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED, "create", "(Ljava/lang/String;)" + self, null, null);
		create.visitCode();
		create.visitTypeInsn(Opcodes.NEW, BOOK_TYPE);
		create.visitInsn(Opcodes.DUP);
		create.visitVarInsn(Opcodes.ALOAD, 0);
		create.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "$VALUES", array);
		create.visitInsn(Opcodes.ARRAYLENGTH);
		create.visitMethodInsn(Opcodes.INVOKESPECIAL, BOOK_TYPE, "<init>", "(Ljava/lang/String;I)V", false);
		create.visitVarInsn(Opcodes.ASTORE, 1);
		create.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "$VALUES", array);
		create.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "$VALUES", array);
		create.visitInsn(Opcodes.ARRAYLENGTH);
		create.visitInsn(Opcodes.ICONST_1);
		create.visitInsn(Opcodes.IADD);
		create.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false);
		create.visitTypeInsn(Opcodes.CHECKCAST, array);
		create.visitVarInsn(Opcodes.ASTORE, 2);
		create.visitVarInsn(Opcodes.ALOAD, 2);
		create.visitVarInsn(Opcodes.ALOAD, 2);
		create.visitInsn(Opcodes.ARRAYLENGTH);
		create.visitInsn(Opcodes.ICONST_1);
		create.visitInsn(Opcodes.ISUB);
		create.visitVarInsn(Opcodes.ALOAD, 1);
		create.visitInsn(Opcodes.AASTORE);
		create.visitVarInsn(Opcodes.ALOAD, 2);
		create.visitFieldInsn(Opcodes.PUTSTATIC, BOOK_TYPE, "$VALUES", array);
		create.visitVarInsn(Opcodes.ALOAD, 1);
		create.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BOOK_TYPE, "init", "()V", false);
		create.visitVarInsn(Opcodes.ALOAD, 1);
		create.visitInsn(Opcodes.ARETURN);
		create.visitMaxs(0, 0);
		create.visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "init", "()V", null, null);
		init.visitCode();
		org.objectweb.asm.Label original = new org.objectweb.asm.Label();
		init.visitTypeInsn(Opcodes.NEW, CALLBACK_INFO);
		init.visitInsn(Opcodes.DUP);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, CALLBACK_INFO, "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ASTORE, 1);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, BOOK_TYPE, "fabricated$skipInit", "(L" + CALLBACK_INFO + ";)V", false);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CALLBACK_INFO, "isCancelled", "()Z", false);
		init.visitJumpInsn(Opcodes.IFEQ, original);
		init.visitInsn(Opcodes.RETURN);
		init.visitLabel(original);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESTATIC, BOOK_SETTINGS, "register", "(" + self + ")V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		for (String handler : List.of("fabricated$addConstants", "fabricated$skipInit")) {
			MethodNode m = method(mixin, handler);
			m.visibleAnnotations = null;
			m.invisibleAnnotations = null;
			m.accept(cw);
		}
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		clinit.visitTypeInsn(Opcodes.NEW, BOOK_TYPE);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitLdcInsn("CRAFTING");
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, BOOK_TYPE, "<init>", "(Ljava/lang/String;I)V", false);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, BOOK_TYPE, "CRAFTING", self);
		clinit.visitInsn(Opcodes.ICONST_1);
		clinit.visitTypeInsn(Opcodes.ANEWARRAY, BOOK_TYPE);
		clinit.visitInsn(Opcodes.DUP);
		clinit.visitInsn(Opcodes.ICONST_0);
		clinit.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "CRAFTING", self);
		clinit.visitInsn(Opcodes.AASTORE);
		clinit.visitFieldInsn(Opcodes.PUTSTATIC, BOOK_TYPE, "$VALUES", array);
		clinit.visitInsn(Opcodes.ACONST_NULL);
		clinit.visitMethodInsn(Opcodes.INVOKESTATIC, BOOK_TYPE, "fabricated$addConstants", "(L" + CALLBACK_INFO + ";)V", false);
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Mixin's {@code CallbackInfo}, as far as a cancellable injector uses it. */
	private static byte[] callbackInfo() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, CALLBACK_INFO, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "cancelled", "Z", null, null).visitEnd();
		MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		ctor.visitCode();
		ctor.visitVarInsn(Opcodes.ALOAD, 0);
		ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();
		MethodVisitor cancel = cw.visitMethod(Opcodes.ACC_PUBLIC, "cancel", "()V", null, null);
		cancel.visitCode();
		cancel.visitVarInsn(Opcodes.ALOAD, 0);
		cancel.visitInsn(Opcodes.ICONST_1);
		cancel.visitFieldInsn(Opcodes.PUTFIELD, CALLBACK_INFO, "cancelled", "Z");
		cancel.visitInsn(Opcodes.RETURN);
		cancel.visitMaxs(0, 0);
		cancel.visitEnd();
		MethodVisitor cancelled = cw.visitMethod(Opcodes.ACC_PUBLIC, "isCancelled", "()Z", null, null);
		cancelled.visitCode();
		cancelled.visitVarInsn(Opcodes.ALOAD, 0);
		cancelled.visitFieldInsn(Opcodes.GETFIELD, CALLBACK_INFO, "cancelled", "Z");
		cancelled.visitInsn(Opcodes.IRETURN);
		cancelled.visitMaxs(0, 0);
		cancelled.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The mod's class reading the constant as a field of the enum ({@code cooking()}), a handle to it, and the enum's own
	 * CRAFTING.
	 */
	private static byte[] reader() {
		String self = "L" + BOOK_TYPE + ";";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, READER, null, "java/lang/Object", null);
		MethodVisitor cooking = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "cooking", "()Ljava/lang/Object;", null, null);
		cooking.visitCode();
		cooking.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "COOKING", self);
		cooking.visitInsn(Opcodes.ARETURN);
		cooking.visitMaxs(0, 0);
		cooking.visitEnd();
		MethodVisitor crafting = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "crafting", "()Ljava/lang/Object;", null, null);
		crafting.visitCode();
		crafting.visitFieldInsn(Opcodes.GETSTATIC, BOOK_TYPE, "CRAFTING", self);
		crafting.visitInsn(Opcodes.ARETURN);
		crafting.visitMaxs(0, 0);
		crafting.visitEnd();
		MethodVisitor handle = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle", "()Ljava/lang/Object;", null, null);
		handle.visitCode();
		handle.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_GETSTATIC, BOOK_TYPE, "COOKING", self, false));
		handle.visitInsn(Opcodes.ARETURN);
		handle.visitMaxs(0, 0);
		handle.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static MethodNode method(ClassNode node, String name) {
		return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(() -> new AssertionError("no method " + name));
	}

	private static AnnotationNode annotation(MethodNode method, String desc) {
		AnnotationNode ann = Annotations.find(null, method.visibleAnnotations, desc);
		assertNotNull(ann, desc + " on " + method.name);
		return ann;
	}
}
