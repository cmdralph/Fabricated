package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

class MixinPreflightTest {
	private static final String TARGET = "net/minecraft/world/Thing";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";

	/** Game class: tick() calls helper(I)V once and reads field "count". */
	private static byte[] targetClass() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd();
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitFieldInsn(Opcodes.GETFIELD, TARGET, "count", "I");
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "helper", "(I)V", false);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		MethodVisitor helper = cw.visitMethod(Opcodes.ACC_PRIVATE, "helper", "(I)V", null, null);
		helper.visitCode();
		helper.visitInsn(Opcodes.RETURN);
		helper.visitMaxs(0, 2);
		helper.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** Builds a mixin class targeting {@link #TARGET}; {@code body} adds members. */
	private static byte[] mixin(String name, boolean pseudo, Consumer<ClassWriter> body) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor values = m.visitArray("value");
		values.visit(null, Type.getObjectType(name.contains("Missing") ? "net/minecraft/world/Gone" : TARGET));
		values.visitEnd();
		m.visitEnd();
		if (pseudo) cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Pseudo;", false).visitEnd();
		body.accept(cw);
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void inject(ClassWriter cw, String handler, String method, String atValue, String atTarget, Integer require, Integer ordinal) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, handler, "()V", null, null);
		AnnotationVisitor ann = mv.visitAnnotation(INJECT, false);
		AnnotationVisitor methods = ann.visitArray("method");
		methods.visit(null, method);
		methods.visitEnd();
		AnnotationVisitor ats = ann.visitArray("at");
		AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", atValue);
		if (atTarget != null) at.visit("target", atTarget);
		if (ordinal != null) at.visit("ordinal", ordinal);
		at.visitEnd();
		ats.visitEnd();
		if (require != null) ann.visit("require", require);
		ann.visitEnd();
		mv.visitEnd();
	}

	private static List<PreflightFinding> run(String configJson, String refmapJson, Map<String, byte[]> mixins, boolean client) throws Exception {
		ClassIndex game = new ClassIndex(Map.of(TARGET, targetClass())::get);
		Map<String, byte[]> mod = new HashMap<>(mixins);
		MixinPreflight preflight = new MixinPreflight(game, mod::get, client);
		Refmap refmap = refmapJson == null ? Refmap.EMPTY : Refmap.parse(refmapJson);
		return preflight.check("test.mixins.json", MixinConfigFile.parse(configJson), refmap, Set.of());
	}

	private static String config(boolean required, String... mixins) {
		return "{\"required\":" + required + ",\"package\":\"com.example.mixin\",\"mixins\":[\"" + String.join("\",\"", mixins) + "\"]}";
	}

	@Test
	void acceptsValidInjections() throws Exception {
		byte[] ok = mixin("com/example/mixin/Ok", false, cw -> {
			inject(cw, "head", "tick", "HEAD", null, 1, null);
			inject(cw, "invoke", "tick()V", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", 1, 0);
			inject(cw, "field", "tick", "FIELD", "Lnet/minecraft/world/Thing;count:I", 1, null);
			cw.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null)
					.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		});
		List<PreflightFinding> findings = run(config(true, "Ok"), null, Map.of("com/example/mixin/Ok", ok), false);
		assertTrue(findings.isEmpty(), findings.toString());
	}

	@Test
	void failsWhenRequiredInjectionTargetIsMissing() throws Exception {
		byte[] bad = mixin("com/example/mixin/Bad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		List<PreflightFinding> findings = run(config(true, "Bad"), null, Map.of("com/example/mixin/Bad", bad), false);
		assertEquals(1, findings.size());
		assertEquals(Severity.FAIL, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("removedMethod"));
	}

	@Test
	void warnsWhenInjectorIsOptional() throws Exception {
		byte[] soft = mixin("com/example/mixin/Soft", false, cw -> inject(cw, "h", "tick", "INVOKE", "Lnet/minecraft/world/Thing;gone()V", null, null));
		List<PreflightFinding> findings = run(config(true, "Soft"), null, Map.of("com/example/mixin/Soft", soft), false);
		assertEquals(Severity.WARN, findings.getFirst().severity(), "require defaults to 0");
	}

	@Test
	void respectsDefaultRequireAndOrdinal() throws Exception {
		byte[] ord = mixin("com/example/mixin/Ord", false, cw -> inject(cw, "h", "tick", "INVOKE", "Lnet/minecraft/world/Thing;helper(I)V", null, 3));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"mixins\":[\"Ord\"],\"injectors\":{\"defaultRequire\":1}}";
		List<PreflightFinding> findings = run(cfg, null, Map.of("com/example/mixin/Ord", ord), false);
		assertEquals(Severity.FAIL, findings.getFirst().severity(), "only one call exists, ordinal 3 cannot match");
	}

	@Test
	void resolvesSelectorsThroughRefmap() throws Exception {
		byte[] mapped = mixin("com/example/mixin/Mapped", false, cw -> inject(cw, "h", "devName", "INVOKE", "devTarget", 1, null));
		String refmap = "{\"mappings\":{\"com/example/mixin/Mapped\":{\"devName\":\"Lnet/minecraft/world/Thing;tick()V\",\"devTarget\":\"Lnet/minecraft/world/Thing;helper(I)V\"}}}";
		assertTrue(run(config(true, "Mapped"), refmap, Map.of("com/example/mixin/Mapped", mapped), false).isEmpty());
	}

	@Test
	void missingTargetClassDependsOnRequiredAndPseudo() throws Exception {
		byte[] missing = mixin("com/example/mixin/Missing", false, cw -> { });
		assertEquals(Severity.FAIL, run(config(true, "Missing"), null, Map.of("com/example/mixin/Missing", missing), false).getFirst().severity());
		assertEquals(Severity.WARN, run(config(false, "Missing"), null, Map.of("com/example/mixin/Missing", missing), false).getFirst().severity());
		byte[] pseudo = mixin("com/example/mixin/PseudoMissing", true, cw -> { });
		assertTrue(run(config(true, "PseudoMissing"), null, Map.of("com/example/mixin/PseudoMissing", pseudo), false).isEmpty());
	}

	@Test
	void missingShadowFails() throws Exception {
		byte[] shadow = mixin("com/example/mixin/Shadowy", false, cw -> cw.visitField(Opcodes.ACC_PRIVATE, "gone", "I", null, null)
				.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd());
		List<PreflightFinding> findings = run(config(true, "Shadowy"), null, Map.of("com/example/mixin/Shadowy", shadow), false);
		assertEquals(Severity.FAIL, findings.getFirst().severity());
		assertTrue(findings.getFirst().message().contains("@Shadow field gone"));
	}

	@Test
	void pluginDowngradesFailures() throws Exception {
		byte[] bad = mixin("com/example/mixin/Bad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.Plugin\",\"mixins\":[\"Bad\"]}";
		List<PreflightFinding> findings = run(cfg, null, Map.of("com/example/mixin/Bad", bad), false);
		assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.FAIL), findings.toString());
	}

	@Test
	void clientMixinsAreSkippedOnServer() throws Exception {
		byte[] bad = mixin("com/example/mixin/ClientBad", false, cw -> inject(cw, "h", "removedMethod", "HEAD", null, 1, null));
		String cfg = "{\"required\":true,\"package\":\"com.example.mixin\",\"client\":[\"ClientBad\"]}";
		assertTrue(run(cfg, null, Map.of("com/example/mixin/ClientBad", bad), false).isEmpty());
		assertEquals(Severity.FAIL, run(cfg, null, Map.of("com/example/mixin/ClientBad", bad), true).getFirst().severity());
	}

	@Test
	void reportsMixinClassMissingFromJar() throws Exception {
		List<PreflightFinding> findings = run(config(true, "Absent"), null, Map.of(), false);
		assertEquals(Severity.FAIL, findings.getFirst().severity());
	}

	@Test
	void parsesMemberSelectors() {
		assertEquals(new MemberRef("a/B", "foo", "(I)V", false), MemberRef.parse("La/B;foo(I)V"));
		assertEquals(new MemberRef(null, "foo", null, false), MemberRef.parse("foo"));
		assertEquals(new MemberRef("a/B", "bar", "I", false), MemberRef.parse("La/B;bar:I"));
		assertEquals(new MemberRef("a/b/C", "baz", "()V", false), MemberRef.parse("a.b.C.baz()V"));
		assertTrue(MemberRef.parse("foo*").fuzzy());
		assertTrue(MemberRef.parse("/regex/").fuzzy());
		assertTrue(MemberRef.parse("foo(I)").matchesMethod("foo", "(I)Z"), "descriptor without return type");
		assertEquals("foo", MixinPreflight.inferAccessorName("getFoo"));
		assertEquals("URL", MixinPreflight.inferAccessorName("getURL"));
		assertEquals("doThing", MixinPreflight.inferInvokerName("callDoThing"));
		assertEquals("<init>", MixinPreflight.inferInvokerName("newThing"));
	}
}
