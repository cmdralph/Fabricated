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
			mv.visitFieldInsn(Opcodes.GETSTATIC, "com/example/Texts", "TEXT", "Ljava/lang/String;");
			mv.visitIntInsn(Opcodes.BIPUSH, 7);
			mv.visitInsn(Opcodes.ICONST_1);
		}), cw -> {
		}), "an argument computed when the class loads");
		assertNull(entries(new Constant("MYMOD_RESERVED", "(J)V", mv -> mv.visitInsn(Opcodes.LCONST_1)), cw -> {
		}), "a constructor NeoForge reserves");
		assertNull(entries(Constant.plain("MYMOD_PLAIN"), cw -> cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "describe",
				"()Ljava/lang/String;", null, null).visitEnd()), "a method the mixin adds, which NeoForge's file can't carry");
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN")), cw -> {
		})), kind(false), "mymod"), "an enum NeoForge doesn't make extensible (Mixin adds its constants)");
		ClassNode named = kind(true);
		named.visibleAnnotations = List.of(new AnnotationNode("Lnet/neoforged/fml/common/asm/enumextension/NamedEnum;"));
		assertNull(EnumExtensions.of(node(TestEnums.mixin(MIXIN, KIND, List.of(Constant.plain("MYMOD_PLAIN")), cw -> {
		})), named, "mymod"), "a named enum, whose name argument NeoForge checks");
	}
}
