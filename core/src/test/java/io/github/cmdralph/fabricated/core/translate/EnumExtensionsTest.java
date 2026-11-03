package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.testutil.TestEnums;
import io.github.cmdralph.fabricated.core.testutil.TestEnums.Constant;

class EnumExtensionsTest {
	private static final String KIND = "net/minecraft/world/Kind";
	private static final String MIXIN = "com/example/mixin/KindMixin";
	private static final Constant WITH_ARGUMENTS = new Constant("MYMOD_ARGS", "(Ljava/lang/String;IZ)V", mv -> {
		mv.visitLdcInsn("text");
		mv.visitIntInsn(Opcodes.BIPUSH, 7);
		mv.visitInsn(Opcodes.ICONST_1);
	});

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
		return node;
	}

	private static ClassNode kind(boolean extensible) {
		return node(TestEnums.target(KIND, extensible, List.of("()V", "(Ljava/lang/String;IZ)V", "(J)V"), List.of("(J)V")));
	}

	private static List<EnumExtensions.Entry> entries(Constant constant, Consumer<ClassWriter> extra) {
		return EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(constant), extra)), kind(true), "mymod");
	}

	@Test
	void constantsWithConstantArgumentsBecomeEntries() throws Exception {
		ClassNode mixin = node(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN"), WITH_ARGUMENTS), cw -> {
		}));
		assertEquals(KIND, EnumExtensions.target(mixin));
		List<EnumExtensions.Entry> entries = EnumExtensions.of(mixin, kind(true), "mymod");
		assertEquals(List.of(new EnumExtensions.Entry(KIND, "MYMOD_PLAIN", "()V", List.of()),
				new EnumExtensions.Entry(KIND, "MYMOD_ARGS", "(Ljava/lang/String;IZ)V", List.of("text", 7, true))), entries);

		// NeoForge's format: {"entries": [{"enum", "name", "constructor", "parameters"}]}, the enum by its internal name
		// (FML refuses a descriptor).
		List<Object> written = Json.asList(Json.parseObject(EnumExtensions.json(entries)).get("entries"));
		assertEquals(2, written.size());
		Map<?, ?> second = (Map<?, ?>) written.get(1);
		assertEquals("net/minecraft/world/Kind", second.get("enum"));
		assertEquals("MYMOD_ARGS", second.get("name"));
		assertEquals("(Ljava/lang/String;IZ)V", second.get("constructor"));
		assertEquals(List.of("text", "7", "true"), Json.asList(second.get("parameters")).stream().map(String::valueOf).toList());
	}

	@Test
	void whatNeoForgeCantAddIsNotTranslated() {
		assertNull(entries(Constant.plain("OTHER_PLAIN"), cw -> {
		}), "NeoForge requires the mod's id first in the name");
		assertNull(entries(new Constant("MYMOD_ARGS", "(Ljava/lang/String;IZ)V", mv -> {
			mv.visitFieldInsn(Opcodes.GETSTATIC, MIXIN, "TEXT", "Ljava/lang/String;");
			mv.visitIntInsn(Opcodes.BIPUSH, 7);
			mv.visitInsn(Opcodes.ICONST_1);
		}), cw -> {
		}), "an argument computed from the mixin itself");
		assertNull(entries(new Constant("MYMOD_ARGS", "(Ljava/lang/String;IZ)V", mv -> {
			mv.visitFieldInsn(Opcodes.GETSTATIC, KIND, "TEXT", "Ljava/lang/String;");
			mv.visitIntInsn(Opcodes.BIPUSH, 7);
			mv.visitInsn(Opcodes.ICONST_1);
		}), cw -> {
		}), "an argument computed from the enum's members, which the mixin's code reaches from inside it");
		assertNull(entries(new Constant("MYMOD_ARGS", "(Ljava/lang/String;IZ)V", mv -> {
			mv.visitLdcInsn("text");
			mv.visitIntInsn(Opcodes.BIPUSH, 7);
			mv.visitVarInsn(Opcodes.ILOAD, 0);
		}), cw -> {
		}), "an argument kept in a local variable");
		assertNull(entries(new Constant("MYMOD_RESERVED", "(J)V", mv -> mv.visitInsn(Opcodes.LCONST_1)), cw -> {
		}), "a constructor NeoForge reserves");
		assertNull(entries(Constant.plain("MYMOD_PLAIN"), cw -> cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "describe",
				"()Ljava/lang/String;", null, null).visitEnd()), "an abstract method that isn't a shadow, which no ordinary mixin can keep");
		assertNull(entries(Constant.plain("MYMOD_PLAIN"), cw -> cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "count", "I", null, null)
				.visitEnd()), "a field besides the constants");
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN")), cw -> {
		})), kind(false), "mymod"), "an enum NeoForge doesn't make extensible (Mixin adds its constants)");
		ClassNode named = kind(true);
		named.visibleAnnotations = List.of(new AnnotationNode("Lnet/neoforged/fml/common/asm/enumextension/NamedEnum;"));
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN")), cw -> {
		})), named, "mymod"), "a named enum's constant without a name argument");
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(WITH_ARGUMENTS), cw -> {
		})), named, "mymod"), "a named enum's name not starting with the mod's id and a colon, which NeoForge refuses");
	}

	/**
	 * ENUM-3: a constant whose arguments are computed when the class loads is entered with a method that computes them as
	 * the mixin's class initializer does, which NeoForge's loader calls for each argument. Where NeoForge reserves the
	 * mixin's constructor for one taking a supplier ({@code Raid.RaiderType}'s), the value comes in a supplier.
	 */
	@Test
	void computedArgumentsComeFromAGeneratedMethod() throws Exception {
		List<EnumExtensions.Entry> plain = entries(new Constant("MYMOD_ARGS", "(Ljava/lang/String;IZ)V", mv -> {
			mv.visitFieldInsn(Opcodes.GETSTATIC, "com/example/Texts", "TEXT", "Ljava/lang/String;");
			mv.visitIntInsn(Opcodes.BIPUSH, 7);
			mv.visitInsn(Opcodes.ICONST_1);
		}), cw -> {
		});
		ClassNode raider = node(TestEnums.target(KIND, true, List.of("(Ljava/lang/Object;[I)V", "(Ljava/util/function/Supplier;[I)V"),
				List.of("(Ljava/lang/Object;[I)V"), Map.of("(Ljava/util/function/Supplier;[I)V", "(Ljava/util/function/Supplier<Ljava/lang/Object;>;[I)V"),
				cw -> {
				}));
		List<EnumExtensions.Entry> supplied = EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(new Constant("MYMOD_RAIDER",
				"(Ljava/lang/Object;[I)V", mv -> {
					mv.visitFieldInsn(Opcodes.GETSTATIC, "com/example/Texts", "TEXT", "Ljava/lang/String;");
					mv.visitInsn(Opcodes.ICONST_2);
					mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
					mv.visitInsn(Opcodes.DUP);
					mv.visitInsn(Opcodes.ICONST_0);
					mv.visitInsn(Opcodes.ICONST_5);
					mv.visitInsn(Opcodes.IASTORE);
					mv.visitInsn(Opcodes.DUP);
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitIntInsn(Opcodes.BIPUSH, 9);
					mv.visitInsn(Opcodes.IASTORE);
				})), cw -> {
		})), raider, "mymod");
		assertEquals("(Ljava/lang/String;IZ)V", plain.getFirst().constructor());
		assertEquals("(Ljava/util/function/Supplier;[I)V", supplied.getFirst().constructor(), "the constructor NeoForge allows");

		String owner = "com/example/FabricatedEnumArguments";
		List<EnumExtensions.Entry> entries = new java.util.ArrayList<>(EnumExtensions.named(plain, owner, 0));
		entries.addAll(EnumExtensions.named(supplied, owner, 1));
		List<Object> written = Json.asList(Json.parseObject(EnumExtensions.json(entries)).get("entries"));
		assertEquals(Map.of("class", owner, "method", "arguments0"), ((Map<?, ?>) written.get(0)).get("parameters"));
		assertEquals(Map.of("class", owner, "method", "arguments1"), ((Map<?, ?>) written.get(1)).get("parameters"));

		// Called as NeoForge's loader calls them: once per argument, with its index and type.
		ClassWriter texts = new ClassWriter(0);
		texts.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "com/example/Texts", null, "java/lang/Object", null);
		texts.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "TEXT", "Ljava/lang/String;", null, "text").visitEnd();
		texts.visitEnd();
		Map<String, byte[]> classes = new java.util.LinkedHashMap<>(Map.of("com/example/Texts", texts.toByteArray()));
		classes.putAll(EnumExtensions.argumentsClasses(owner, entries));
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get(name.replace('.', '/'));
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		Class<?> arguments = loader.loadClass(owner.replace('/', '.'));
		java.lang.reflect.Method first = arguments.getMethod("arguments0", int.class, Class.class);
		assertEquals("text", first.invoke(null, 0, String.class));
		assertEquals(7, first.invoke(null, 1, int.class));
		assertEquals(true, first.invoke(null, 2, boolean.class));
		java.lang.reflect.Method second = arguments.getMethod("arguments1", int.class, Class.class);
		assertEquals("text", ((java.util.function.Supplier<?>) second.invoke(null, 0, java.util.function.Supplier.class)).get());
		assertEquals(List.of(5, 9), java.util.Arrays.stream((int[]) second.invoke(null, 1, int[].class)).boxed().toList());
	}

	/** A constant of {@link #STACKS}' kind: built from an array of stacks made when the class loads, as Farmer's Delight's. */
	private static final String STACK = "com/example/Stack";
	private static final Constant STACKS = new Constant("MYMOD_COOKING", "([L" + STACK + ";)V", mv -> {
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitTypeInsn(Opcodes.ANEWARRAY, STACK);
		mv.visitInsn(Opcodes.DUP);
		mv.visitInsn(Opcodes.ICONST_0);
		mv.visitTypeInsn(Opcodes.NEW, STACK);
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, STACK, "<init>", "()V", false);
		mv.visitInsn(Opcodes.AASTORE);
	});

	/**
	 * {@code RecipeBookCategories}' shape: NeoForge reserves the constructor taking stacks for vanilla's and allows one
	 * taking a supplier of a list of them; {@code signature} is that one's generic signature, or null.
	 */
	private static ClassNode categories(String signature) {
		Map<String, String> signatures = signature == null ? Map.of() : Map.of("(Ljava/util/function/Supplier;)V", signature);
		return node(TestEnums.target(KIND, true, List.of("([L" + STACK + ";)V", "(Ljava/util/function/Supplier;)V"), List.of("([L" + STACK + ";)V"),
				signatures, cw -> {
				}));
	}

	/** An injector the mixin adds besides its constants, reading one of them (the enum's field once NeoForge adds it). */
	private static void injector(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "mymod$categories", "(Ljava/util/List;)V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, "categories");
		methods.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		Label end = new Label();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitJumpInsn(Opcodes.IFNULL, end);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETSTATIC, MIXIN, "MYMOD_COOKING", "L" + MIXIN + ";");
		mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
		mv.visitInsn(Opcodes.POP);
		mv.visitLabel(end);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/** A method the mixin adds besides its constants, with {@code body} (then a return). */
	private static Consumer<ClassWriter> method(Consumer<MethodVisitor> body) {
		return cw -> {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "mymod$other", "()V", null, null);
			mv.visitCode();
			body.accept(mv);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		};
	}

	/**
	 * CORPUS-65: Farmer's Delight's enum mixin into {@code RecipeBookCategories} adds an injector besides its constants,
	 * and builds them with the constructor NeoForge reserves, taking stacks, where the one it allows takes a supplier of
	 * a list of them. The constants go to NeoForge's file, their stacks given in a supplier of a list; the injector stays
	 * a mixin, an ordinary class, its code as compiled.
	 */
	@Test
	void anEnumMixinsOtherMethodsStayAMixinAndAnArrayComesAsASuppliedList() throws Exception {
		byte[] bytes = TestEnums.mixin(MIXIN, KIND, List.of(STACKS), EnumExtensionsTest::injector);
		List<EnumExtensions.Entry> entries = EnumExtensions.of(node(bytes),
				categories("(Ljava/util/function/Supplier<Ljava/util/List<L" + STACK + ";>;>;)V"), "mymod");
		assertNotNull(entries);
		assertEquals("(Ljava/util/function/Supplier;)V", entries.getFirst().constructor(), "the constructor NeoForge allows");
		assertArrayEquals(new EnumExtensions.Wrap[] {EnumExtensions.Wrap.LIST_SUPPLIER}, entries.getFirst().computed().wraps());

		// The argument as NeoForge's loader asks for it: a supplier of a list of the stacks the mixin's code makes.
		String owner = "com/example/FabricatedEnumArguments";
		ClassWriter stack = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		stack.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, STACK, null, "java/lang/Object", null);
		MethodVisitor init = stack.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		stack.visitEnd();
		Map<String, byte[]> classes = new java.util.LinkedHashMap<>(Map.of(STACK, stack.toByteArray()));
		classes.putAll(EnumExtensions.argumentsClasses(owner, EnumExtensions.named(entries, owner, 0)));
		byte[] rest = EnumExtensions.rest(bytes);
		assertNotNull(rest, "the injector stays");
		classes.put(MIXIN, rest);
		ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] b = classes.get(name.replace('.', '/'));
				if (b == null) throw new ClassNotFoundException(name);
				return defineClass(name, b, 0, b.length);
			}
		};
		Object supplied = loader.loadClass(owner.replace('/', '.')).getMethod("arguments0", int.class, Class.class)
				.invoke(null, 0, java.util.function.Supplier.class);
		List<?> stacks = (List<?>) ((java.util.function.Supplier<?>) supplied).get();
		assertEquals(1, stacks.size());
		assertEquals(STACK.replace('/', '.'), stacks.getFirst().getClass().getName());

		// What stays: no longer an enum, nor its constants, array, constructors or enum methods; the injector with its
		// annotation, and the class verifies (its frames kept).
		ClassNode kept = node(rest);
		assertEquals("java/lang/Object", kept.superName);
		assertTrue((kept.access & Opcodes.ACC_ENUM) == 0 && (kept.access & Opcodes.ACC_ABSTRACT) != 0, "an abstract class, not an enum");
		assertTrue(kept.fields.isEmpty(), "no fields");
		assertEquals(List.of("mymod$categories"), kept.methods.stream().map(m -> m.name).toList());
		MethodNode injector = kept.methods.getFirst();
		assertEquals("Lorg/spongepowered/asm/mixin/injection/Inject;", injector.visibleAnnotations.getFirst().desc);
		assertEquals("Lorg/spongepowered/asm/mixin/Mixin;", kept.invisibleAnnotations.getFirst().desc);
		assertEquals(1, Class.forName(MIXIN.replace('/', '.'), true, loader).getDeclaredMethods().length);

		assertNull(EnumExtensions.rest(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN")), cw -> {
		})), "nothing stays of a mixin with only constants");
	}

	/** What the other methods use of the mixin decides whether they can stay without its constants. */
	@Test
	void otherMethodsUsingWhatGoesWithTheConstantsAreNotTranslated() {
		ClassNode kind = kind(true);
		java.util.function.Function<Consumer<MethodVisitor>, List<EnumExtensions.Entry>> with = body -> EnumExtensions.of(
				node(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN")), method(body))), kind, "mymod");
		assertNotNull(with.apply(mv -> {
			mv.visitFieldInsn(Opcodes.GETSTATIC, MIXIN, "MYMOD_PLAIN", "L" + MIXIN + ";");
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MIXIN, "ordinal", "()I", false);
			mv.visitInsn(Opcodes.POP);
		}), "a constant, and what Enum gives it");
		assertNotNull(with.apply(mv -> {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, MIXIN, "mymod$other", "()V", false);
		}), "another of its methods");
		assertNull(with.apply(mv -> {
			mv.visitFieldInsn(Opcodes.GETSTATIC, MIXIN, "$VALUES", "[L" + MIXIN + ";");
			mv.visitInsn(Opcodes.POP);
		}), "the values array, which goes");
		assertNull(with.apply(mv -> {
			mv.visitTypeInsn(Opcodes.NEW, MIXIN);
			mv.visitInsn(Opcodes.POP);
		}), "a new constant");
		assertNull(with.apply(mv -> {
			mv.visitFieldInsn(Opcodes.GETSTATIC, MIXIN, "MYMOD_PLAIN", "L" + MIXIN + ";");
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MIXIN, "describe", "()Ljava/lang/String;", false);
			mv.visitInsn(Opcodes.POP);
		}), "a method neither it nor the enum has");
		assertNull(with.apply(mv -> {
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "toString", "()Ljava/lang/String;", false);
			mv.visitInsn(Opcodes.POP);
		}), "Enum's method as its super class's, which the mixin no longer extends");
	}

	/**
	 * A computed argument goes in a supplier only where the constructor's generic signature says the supplier gives its
	 * type: a supplier of something else would fail as the enum reads it (CORPUS-65's stacks in a supplier of an array).
	 */
	@Test
	void aSupplierIsUsedOnlyForTheTypeItsSignatureGives() {
		List<Constant> stacks = List.of(STACKS);
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, stacks, cw -> {
		})), categories(null), "mymod"), "no signature: what the supplier gives isn't known");
		List<EnumExtensions.Entry> array = EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, stacks, cw -> {
		})), categories("(Ljava/util/function/Supplier<[L" + STACK + ";>;)V"), "mymod");
		assertArrayEquals(new EnumExtensions.Wrap[] {EnumExtensions.Wrap.SUPPLIER}, array.getFirst().computed().wraps(), "a supplier of the array itself");
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, stacks, cw -> {
		})), categories("(Ljava/util/function/Supplier<Ljava/util/List<Ljava/lang/String;>;>;)V"), "mymod"), "a list of something else");
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, stacks, cw -> {
		})), categories("(Ljava/util/function/Supplier<Ljava/util/List<-L" + STACK + ";>;>;)V"), "mymod"), "a list of a lower bound");
		assertNotNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, stacks, cw -> {
		})), categories("(Ljava/util/function/Supplier<Ljava/util/List<+L" + STACK + ";>;>;)V"), "mymod"), "a list of an upper bound");
	}

	/** A named enum ({@code MobCategory}): its name argument carries over when it already starts with the mod's id. */
	@Test
	void aNamedEnumsConstantNamedForTheModBecomesAnEntry() {
		ClassNode named = kind(true);
		named.visibleAnnotations = List.of(new AnnotationNode("Lnet/neoforged/fml/common/asm/enumextension/NamedEnum;"));
		Constant glares = new Constant("MYMOD_GLARES", "(Ljava/lang/String;IZ)V", mv -> {
			mv.visitLdcInsn("mymod:glares");
			mv.visitIntInsn(Opcodes.BIPUSH, 15);
			mv.visitInsn(Opcodes.ICONST_1);
		});
		assertEquals(List.of(new EnumExtensions.Entry(KIND, "MYMOD_GLARES", "(Ljava/lang/String;IZ)V", List.of("mymod:glares", 15, true))),
				EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(glares), cw -> {
				})), named, "mymod"));
	}
}
