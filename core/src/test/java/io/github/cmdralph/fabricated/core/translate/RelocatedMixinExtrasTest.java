package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.MixinConfigFile;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.Refmap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

class RelocatedMixinExtrasTest {
	private static final String TARGET = "net/minecraft/world/Thing";
	private static final String MIXIN = "com/example/mixin/Draw";
	private static final String RELOCATED = "gg/example/lib/mixinextras/";
	private static final String LOCAL_INT_REF = "L" + RELOCATED + "sugar/ref/LocalIntRef;";
	private static final String RECORD = "record(IL" + RELOCATED + "sugar/ref/LocalIntRef;)Z";
	private static final String CONFIG = "{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.Plugin\",\"mixins\":[\"Draw\"]}";

	/** A target whose {@code tick} makes none of the calls the mixin's group hooks. */
	private static byte[] target() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitInsn(Opcodes.ICONST_0);
		tick.visitVarInsn(Opcodes.ISTORE, 1);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Essential's screen drawing hooks, in short: a group needing one of two injectors, a {@code @WrapWithCondition} of
	 * the mod's relocated MixinExtras taking a local through its {@code @Local}, and an {@code @Inject}.
	 */
	private static byte[] mixin() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, MIXIN, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(TARGET));
		values.visitEnd();
		m.visitEnd();
		MethodVisitor record = cw.visitMethod(Opcodes.ACC_PRIVATE, "record", "(I" + LOCAL_INT_REF + ")Z", null, null);
		group(record);
		injector(record, "L" + RELOCATED + "injector/v2/WrapWithCondition;", "Lcom/example/Screen;draw(I)V");
		record.visitParameterAnnotation(1, "L" + RELOCATED + "sugar/Local;", false).visitEnd();
		record.visitCode();
		record.visitVarInsn(Opcodes.ALOAD, 2);
		record.visitMethodInsn(Opcodes.INVOKEINTERFACE, RELOCATED + "sugar/ref/LocalIntRef", "get", "()I", true);
		record.visitInsn(Opcodes.POP);
		record.visitInsn(Opcodes.ICONST_1);
		record.visitInsn(Opcodes.IRETURN);
		record.visitMaxs(1, 3);
		record.visitEnd();
		MethodVisitor optiFine = cw.visitMethod(Opcodes.ACC_PRIVATE, "optiFine", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		group(optiFine);
		injector(optiFine, "Lorg/spongepowered/asm/mixin/injection/Inject;", "Lnet/optifine/Reflector;callVoid()V");
		optiFine.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void group(MethodVisitor mv) {
		AnnotationVisitor group = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Group;", false);
		group.visit("name", "capture_args");
		group.visit("min", 1);
		group.visitEnd();
	}

	private static void injector(MethodVisitor mv, String desc, String target) {
		AnnotationVisitor ann = mv.visitAnnotation(desc, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, "tick");
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "INVOKE");
		at.visit("target", target);
		at.visitEnd();
		ats.visitEnd();
		ann.visitEnd();
	}

	private static Set<String> names(byte[] bytes) {
		Set<String> names = new HashSet<>();
		new ClassReader(bytes).accept(new ClassRemapper(new ClassWriter(0), new Remapper() {
			@Override
			public String map(String name) {
				names.add(name);
				return name;
			}
		}), 0);
		return names;
	}

	@Test
	void preflightJudgesARelocatedMixinExtrasAsItsOwnAndNamesTheHandlersAsTheJarDoes() throws Exception {
		Map<String, byte[]> mod = Map.of(MIXIN, mixin());
		ClassIndex game = new ClassIndex(Map.of(TARGET, target())::get);
		MixinConfigFile config = MixinConfigFile.parse(CONFIG);

		// Read as it is, the group has the @Inject only: the relocated @WrapWithCondition isn't counted as an injector.
		List<PreflightFinding> plain = new MixinPreflight(game, InstalledClasses.NONE, mod::get, ClassBytesSource.EMPTY, false, true, ClassBytesSource.EMPTY)
				.check("test.mixins.json", config, Refmap.EMPTY, Set.of());
		assertTrue(plain.stream().noneMatch(f -> RECORD.equals(f.handler())), plain.toString());

		RelocatedMixinExtras relocated = new RelocatedMixinExtras();
		MixinPreflight preflight = new MixinPreflight(game, InstalledClasses.NONE, relocated.view(mod::get), ClassBytesSource.EMPTY, false, true,
				ClassBytesSource.EMPTY);
		List<PreflightFinding> findings = relocated.relocated(preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()));
		List<PreflightFinding> group = findings.stream().filter(f -> f.message().startsWith("@Group capture_args needs 1 of its 2 injectors to apply")).toList();
		assertTrue(group.stream().allMatch(f -> f.severity() == Severity.LEFT_OUT), findings.toString());
		assertEquals(Set.of(RECORD, "optiFine(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
				group.stream().map(PreflightFinding::handler).collect(Collectors.toSet()), "the handlers as the jar names them: " + findings);
		assertTrue(findings.stream().noneMatch(f -> f.message().contains("com/llamalad7") || String.valueOf(f.handler()).contains("com/llamalad7")), findings.toString());
	}

	@Test
	void theClassIsReadWithMixinExtrasOwnNamesAndWrittenBackWithTheRelocatedOnes() throws Exception {
		RelocatedMixinExtras relocated = new RelocatedMixinExtras();
		byte[] original = mixin();
		byte[] canonical = relocated.canonical(MIXIN, original);
		Set<String> read = names(canonical);
		assertTrue(read.contains("com/llamalad7/mixinextras/injector/v2/WrapWithCondition") && read.contains("com/llamalad7/mixinextras/sugar/ref/LocalIntRef")
				&& read.stream().noneMatch(n -> n.startsWith(RELOCATED)), read.toString());
		assertEquals(names(original), names(relocated.relocated(MIXIN, canonical)));
		List<PreflightFinding> findings = new ArrayList<>(List.of(new PreflightFinding(Severity.LEFT_OUT, "test.mixins.json", MIXIN.replace('/', '.'),
				"handler record(ILcom/llamalad7/mixinextras/sugar/ref/LocalIntRef;)Z", "", "record(ILcom/llamalad7/mixinextras/sugar/ref/LocalIntRef;)Z")));
		relocated.relocateFindings(findings, 0);
		assertEquals(RECORD, findings.getFirst().handler());
		assertEquals("handler " + RECORD, findings.getFirst().message());

		// A class without MixinExtras, one naming MixinExtras' own package too, and the relocated copy's own classes stay as they are.
		ClassWriter plain = new ClassWriter(0);
		plain.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Plain", null, "java/lang/Object", null);
		plain.visitEnd();
		byte[] plainBytes = plain.toByteArray();
		assertArrayEquals(plainBytes, relocated.canonical("com/example/Plain", plainBytes));
		byte[] mixed = remapOne(original, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "com/llamalad7/mixinextras/sugar/Share");
		assertArrayEquals(mixed, relocated.canonical("com/example/mixin/Mixed", mixed));
		assertArrayEquals(original, relocated.canonical(RELOCATED + "sugar/ref/LocalIntRefImpl", original));
	}

	/** {@code bytes} with {@code from} named {@code to}. */
	private static byte[] remapOne(byte[] bytes, String from, String to) {
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor remapper = new ClassRemapper(writer, new Remapper() {
			@Override
			public String map(String name) {
				return name.equals(from) ? to : name;
			}
		});
		new ClassReader(bytes).accept(remapper, 0);
		return writer.toByteArray();
	}
}
