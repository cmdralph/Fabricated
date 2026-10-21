package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

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
				"()Ljava/lang/String;", null, null).visitEnd()), "a method the mixin adds, which NeoForge's file can't carry");
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
				List.of("(Ljava/lang/Object;[I)V")));
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
