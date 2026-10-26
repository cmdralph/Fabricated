package io.github.cmdralph.fabricated.core.remap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

class RemapTest {
	/** official -> intermediary, as Fabric publishes it. */
	static final String TINY = String.join("\n",
			"v1\tofficial\tintermediary",
			"CLASS\ta\tnet/minecraft/class_1",
			"CLASS\tb\tnet/minecraft/class_2",
			"CLASS\tb$a\tnet/minecraft/class_2$class_3",
			"FIELD\ta\tLb;\tc\tfield_10",
			"FIELD\ta\tI\tc\tfield_11",
			"METHOD\ta\t(Lb;)V\td\tmethod_20",
			"METHOD\ta\t(I)Lb;\td\tmethod_21",
			"METHOD\tb\t()V\te\tmethod_22",
			"") ;

	/** mojang -> official (ProGuard), including overloaded obfuscated names and inlining suffixes. */
	static final String PROGUARD = String.join("\n",
			"# a comment",
			"net.minecraft.world.Level -> a:",
			"    net.minecraft.world.Entity owner -> c",
			"    int count -> c",
			"    1:5:void addEntity(net.minecraft.world.Entity) -> d",
			"    6:7:net.minecraft.world.Entity entityAt(int):12:13 -> d",
			"net.minecraft.world.Entity -> b:",
			"    void tick() -> e",
			"net.minecraft.world.Entity$Removal -> b$a:",
			"");

	/** {@link #PROGUARD} in TSRG v1, as Forge 61's installer writes Mojang's mappings: fields without descriptors. */
	static final String TSRG = String.join("\n",
			"net/minecraft/world/Level a",
			"\towner c",
			"\tcount c",
			"\taddEntity (Lnet/minecraft/world/Entity;)V d",
			"\tentityAt (I)Lnet/minecraft/world/Entity; d",
			"net/minecraft/world/Entity b",
			"\ttick ()V e",
			"net/minecraft/world/Entity$Removal b$a",
			"");

	/**
	 * MCPConfig's obfuscated → SRG names in TSRG v2 for the same game: SRG class names, a parameter and a static marker
	 * under methods, fields sharing an obfuscated name with their descriptors.
	 */
	static final String TSRG2 = String.join("\n",
			"tsrg2 obf srg id",
			"a net/minecraft/src/C_1_ 1",
			"\tc Lb; f_10_ 10",
			"\tc I f_11_ 11",
			"\td (Lb;)V m_20_ 20",
			"\t\t0 o p_20_0_ 0",
			"\td (I)Lb; m_21_ 21",
			"\t\tstatic",
			"b net/minecraft/src/C_2_ 2",
			"\te ()V m_22_ 22",
			"\tf f_12_ 12",
			"\tg f_13_ 13",
			"\tg f_14_ 14",
			"b$a net/minecraft/src/C_3_ 3",
			"");

	static MappingTable table() throws Exception {
		return MappingComposer.compose(new StringReader(TINY), new StringReader(PROGUARD));
	}

	@Test
	void composesIntermediaryWithForgesTsrg() throws Exception {
		Map<String, String> types = Map.of("net/minecraft/world/Level.owner", "Lnet/minecraft/world/Entity;", "net/minecraft/world/Level.count", "I");
		MappingComposer.FieldDescriptors descriptors = (owner, name) -> types.get(owner + "." + name);
		assertArrayEquals(table().serialize(), MappingComposer.compose(new StringReader(TINY), new StringReader(TSRG), descriptors).serialize(),
				"the same table as from Mojang's ProGuard file");
		String obfuscatedDescriptors = TSRG.replace("(Lnet/minecraft/world/Entity;)V", "(Lb;)V").replace("(I)Lnet/minecraft/world/Entity;", "(I)Lb;");
		assertArrayEquals(table().serialize(), MappingComposer.compose(new StringReader(TINY), new StringReader(obfuscatedDescriptors), descriptors).serialize(),
				"method descriptors in the obfuscated names read the same");

		// Without the fields' descriptors, the two sharing the obfuscated name c are left out; a field with a name of its own isn't.
		String tiny = TINY + "FIELD\tb\tZ\tf\tfield_12\n";
		MappingTable withoutDescriptors = MappingComposer.compose(new StringReader(tiny), new StringReader(TSRG.replace("\ttick", "\tremoved f\n\ttick")));
		assertNull(withoutDescriptors.mapMemberFlat("field_10"));
		assertNull(withoutDescriptors.mapMemberFlat("field_11"));
		assertEquals("removed", withoutDescriptors.mapMemberFlat("field_12"));
		assertEquals("addEntity", withoutDescriptors.mapMemberFlat("method_20"));
		assertEquals("net/minecraft/world/Entity$Removal", withoutDescriptors.mapClass("net/minecraft/class_2$class_3"));
		MappingException v2 = assertThrows(MappingException.class, () -> MappingComposer.compose(new StringReader(TINY), new StringReader("tsrg2 left right\n")));
		assertTrue(v2.getMessage().contains("TSRG v2"), v2.getMessage());
	}

	@Test
	void composesIntermediaryWithMcpConfigsSrgNames() throws Exception {
		String tiny = TINY + "FIELD\tb\tZ\tf\tfield_12\nFIELD\tb\tI\tg\tfield_13\nFIELD\tb\tJ\tg\tfield_14\n";
		MappingTable t = MappingComposer.composeSrg(new StringReader(tiny), new StringReader(TSRG2), new StringReader(PROGUARD));
		assertEquals("net/minecraft/world/Level", t.mapClass("net/minecraft/class_1"), "Mojang's class names");
		assertEquals("net/minecraft/world/Entity$Removal", t.mapClass("net/minecraft/class_2$class_3"));
		assertEquals("f_10_", t.mapMemberFlat("field_10"), "fields sharing an obfuscated name are joined by descriptor");
		assertEquals("f_11_", t.mapMemberFlat("field_11"));
		assertEquals("f_12_", t.mapMemberFlat("field_12"), "a field without a descriptor is joined on its name");
		assertNull(t.mapMemberFlat("field_13"), "fields sharing an obfuscated name without descriptors are left out");
		assertNull(t.mapMemberFlat("field_14"));
		assertEquals("m_20_", t.mapMemberFlat("method_20"), "overloaded obfuscated method names are joined by descriptor");
		assertEquals("m_21_", t.mapMemberFlat("method_21"));
		assertEquals("m_22_", t.mapMemberFlat("method_22"));
		assertEquals("net/minecraft/class_1", t.unmapClass("net/minecraft/world/Level"));

		// A server's Mojang file names no client class: such a class keeps its intermediary name, its members still map.
		String server = PROGUARD.replace("net.minecraft.world.Entity$Removal -> b$a:\n", "");
		MappingTable onServer = MappingComposer.composeSrg(new StringReader(tiny), new StringReader(TSRG2), new StringReader(server));
		assertEquals("net/minecraft/class_2$class_3", onServer.mapClass("net/minecraft/class_2$class_3"));
		assertEquals("m_22_", onServer.mapMemberFlat("method_22"));

		MappingTable srgClasses = MappingComposer.composeSrg(new StringReader(tiny), new StringReader(TSRG2), null);
		assertEquals("net/minecraft/src/C_1_", srgClasses.mapClass("net/minecraft/class_1"), "without Mojang's file, the TSRG file's class names");
		assertEquals("m_20_", srgClasses.mapMemberFlat("method_20"));

		MappingException v1 = assertThrows(MappingException.class, () -> MappingComposer.composeSrg(new StringReader(TINY), new StringReader(TSRG), null));
		assertTrue(v1.getMessage().startsWith("Not TSRG v2"), v1.getMessage());
		MappingException malformed = assertThrows(MappingException.class,
				() -> MappingComposer.composeSrg(new StringReader(TINY), new StringReader("tsrg2 obf srg id\na net/minecraft/src/C_1_\n"), null));
		assertTrue(malformed.getMessage().startsWith("Malformed class line 2"), malformed.getMessage());
	}

	@Test
	void aTruncatedMethodLineIsReportedAsMalformed() {
		// A mapping file cut off mid-line (an interrupted download, a disk full) must give the targeted message.
		String truncated = String.join("\n", "net.minecraft.world.Level -> a:", "    1:5:void addEntity(net.minecraft.world.Entity -> d", "");
		MappingException e = assertThrows(MappingException.class, () -> MappingComposer.compose(new StringReader(TINY), new StringReader(truncated)));
		assertTrue(e.getMessage().startsWith("Malformed method line 2"), e.getMessage());
	}

	@Test
	void composesIntermediaryWithMojangMappings() throws Exception {
		MappingTable t = table();
		assertEquals("net/minecraft/world/Level", t.mapClass("net/minecraft/class_1"));
		assertEquals("net/minecraft/world/Entity$Removal", t.mapClass("net/minecraft/class_2$class_3"));
		assertEquals("owner", t.mapMemberFlat("field_10"));
		assertEquals("count", t.mapMemberFlat("field_11"), "overloaded obfuscated field names are joined by descriptor");
		assertEquals("addEntity", t.mapMemberFlat("method_20"));
		assertEquals("entityAt", t.mapMemberFlat("method_21"), "inlining suffix is stripped");
		assertEquals("tick", t.mapMemberFlat("method_22"));
		assertEquals("net/minecraft/class_1", t.unmapClass("net/minecraft/world/Level"));
		assertEquals("unknown/Name", t.mapClass("unknown/Name"));
	}

	@Test
	void ambiguousNamesFallBackToQualifiedLookup() {
		MappingTable t = MappingTable.fromMembers(Map.of(), Map.of("method_5", Map.of(
				"net/minecraft/class_1.method_5()V", "first",
				"net/minecraft/class_9.method_5()V", "second")));
		assertNull(t.mapMemberFlat("method_5"));
		assertEquals("first", t.mapMember("net/minecraft/class_1", "method_5", "()V"));
		assertEquals("second", t.mapMember("net/minecraft/class_9", "method_5", "()V"));
		assertEquals("method_5", t.mapMember(null, "method_5", null));
		assertEquals("method_5", t.mapMember("com/mod/mixin/Shadowing", "method_5", "()V"), "the descriptor doesn't tell them apart");

		// A mixin's @Shadow (owner: the mixin) of one of two fields sharing an intermediary name, of different types.
		MappingTable fields = MappingTable.fromMembers(Map.of(), Map.of("field_7", Map.of(
				"net/minecraft/class_1.field_7I", "sleepingPlayers",
				"net/minecraft/class_9.field_7Lnet/minecraft/class_2;", "other")));
		assertEquals("sleepingPlayers", fields.mapMember("com/mod/mixin/Shadowing", "field_7", "I"));
	}

	@Test
	void serializationRoundTripsDeterministically() throws Exception {
		MappingTable t = table();
		byte[] a = t.serialize();
		MappingTable back = MappingTable.deserialize(a);
		assertArrayEquals(a, back.serialize());
		assertEquals(t.mapClass("net/minecraft/class_1"), back.mapClass("net/minecraft/class_1"));
	}

	@Test
	void tokenRemapperTouchesOnlyIntermediaryTokens() throws Exception {
		TokenRemapper r = new TokenRemapper(table());
		assertEquals("Lnet/minecraft/world/Level;addEntity(Lnet/minecraft/world/Entity;)V",
				r.remap("Lnet/minecraft/class_1;method_20(Lnet/minecraft/class_2;)V"));
		assertEquals("net.minecraft.world.Entity$Removal", r.remap("net.minecraft.class_2$class_3"));
		assertEquals("my_method_20_helper", r.remap("my_method_20_helper"), "embedded tokens are not touched");
		assertEquals("field_99999", r.remap("field_99999"), "unknown tokens stay");
		assertEquals("plain text", r.remap("plain text"));
	}

	@Test
	void remapsBytecodeAnnotationsAndStrings() throws Exception {
		byte[] input = sampleModClass();
		byte[] out = new JarRemapper(table()).remapClass(input, "test");
		ClassNode node = new ClassNode();
		new ClassReader(out).accept(node, 0);

		assertEquals("net/minecraft/world/Entity", node.superName, "superclass remapped");
		MethodNode tick = node.methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElse(null);
		assertTrue(tick != null, "override of method_22 renamed to tick so it still overrides");
		assertTrue(node.fields.stream().anyMatch(f -> f.name.equals("owner") && f.desc.equals("Lnet/minecraft/world/Entity;")),
				"shadow-style field declaration renamed");

		MethodNode run = node.methods.stream().filter(m -> m.name.equals("run")).findFirst().orElseThrow();
		List<String> calls = new ArrayList<>();
		List<String> strings = new ArrayList<>();
		for (AbstractInsnNode insn : run.instructions) {
			if (insn instanceof MethodInsnNode m) calls.add(m.owner + "." + m.name + m.desc);
			if (insn instanceof FieldInsnNode f) calls.add(f.owner + "." + f.name + ":" + f.desc);
			if (insn instanceof LdcInsnNode l && l.cst instanceof String s) strings.add(s);
		}
		assertTrue(calls.contains("net/minecraft/world/Level.addEntity(Lnet/minecraft/world/Entity;)V"), calls.toString());
		assertTrue(calls.contains("net/minecraft/world/Level.count:I"), calls.toString());
		assertEquals(List.of("net.minecraft.world.Level", "user text"), strings);

		String annotationValue = (String) node.invisibleAnnotations.getFirst().values.get(1);
		assertEquals("Lnet/minecraft/world/Level;addEntity(Lnet/minecraft/world/Entity;)V", annotationValue);
	}

	/** As Loom's static mixin remapping leaves a {@code @Shadow} with a prefix (adventure-platform's {@code shadow$comp_925}). */
	@Test
	void remapsIntermediaryNamesBehindAPrefix() throws Exception {
		String mixin = "com/example/LevelMixin";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, mixin, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PRIVATE, "shadow$field_11", "I", null, null).visitEnd();
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "shadow$method_22", "()V", null, null).visitEnd();
		MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$method_22$0", "()V", null, null);
		lambda.visitCode();
		lambda.visitInsn(Opcodes.RETURN);
		lambda.visitMaxs(0, 0);
		lambda.visitEnd();
		MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
		run.visitCode();
		run.visitVarInsn(Opcodes.ALOAD, 0);
		run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mixin, "shadow$method_22", "()V", false);
		run.visitVarInsn(Opcodes.ALOAD, 0);
		run.visitFieldInsn(Opcodes.GETFIELD, mixin, "shadow$field_11", "I");
		run.visitInsn(Opcodes.POP);
		run.visitInsn(Opcodes.RETURN);
		run.visitMaxs(1, 1);
		run.visitEnd();
		cw.visitEnd();

		ClassNode node = new ClassNode();
		new ClassReader(new JarRemapper(table()).remapClass(cw.toByteArray(), "test")).accept(node, 0);
		assertEquals(Set.of("shadow$tick", "lambda$method_22$0", "run"), node.methods.stream().map(m -> m.name).collect(Collectors.toSet()),
				"a lambda's name is the mod's own");
		assertEquals("shadow$count", node.fields.getFirst().name);
		List<String> uses = new ArrayList<>();
		for (AbstractInsnNode insn : node.methods.stream().filter(m -> m.name.equals("run")).findFirst().orElseThrow().instructions) {
			if (insn instanceof MethodInsnNode m) uses.add(m.name);
			if (insn instanceof FieldInsnNode f) uses.add(f.name);
		}
		assertEquals(List.of("shadow$tick", "shadow$count"), uses);
	}

	@Test
	void remapIsDeterministic() throws Exception {
		JarRemapper remapper = new JarRemapper(table());
		assertArrayEquals(remapper.remapClass(sampleModClass(), "a"), remapper.remapClass(sampleModClass(), "a"));
	}

	@Test
	void remapsWholeJarIncludingRefmapsAndStripsSignatures(@TempDir Path tmp) throws Exception {
		Path jar = TestJars.create()
				.text("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMain-Class: x.Y\r\n\r\nName: a/B.class\r\nSHA-256-Digest: abc\r\n\r\n")
				.text("META-INF/SIGNER.SF", "sig")
				.text("META-INF/SIGNER.RSA", "sig")
				.bytes("com/example/ModClass.class", sampleModClass())
				.text("example.refmap.json", "{\"mappings\":{\"com/example/Mixin\":{\"addEntity\":\"Lnet/minecraft/class_1;method_20(Lnet/minecraft/class_2;)V\"}}}")
				.text("META-INF/services/net.minecraft.class_1", "net.minecraft.class_2\n")
				.write(tmp.resolve("mod.jar"));
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			JarRemapper.Result result = new JarRemapper(table()).remap(archive, Set.of("example.refmap.json"));
			assertEquals(List.of("META-INF/SIGNER.SF", "META-INF/SIGNER.RSA"), result.strippedSignatureFiles());
			String manifest = new String(result.files().get("META-INF/MANIFEST.MF"), StandardCharsets.UTF_8);
			assertTrue(manifest.contains("Main-Class: x.Y"));
			assertFalse(manifest.contains("Digest"));
			String refmap = new String(result.files().get("example.refmap.json"), StandardCharsets.UTF_8);
			assertTrue(refmap.contains("Lnet/minecraft/world/Level;addEntity(Lnet/minecraft/world/Entity;)V"), refmap);
			assertEquals("net.minecraft.world.Entity\n",
					new String(result.files().get("META-INF/services/net.minecraft.world.Level"), StandardCharsets.UTF_8));
		}
	}

	/** A class shaped like a production Fabric mod class compiled against intermediary. */
	static byte[] sampleModClass() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/ModClass", null, "net/minecraft/class_2", null);
		AnnotationVisitor av = cw.visitAnnotation("Lcom/example/Target;", false);
		av.visit("value", "Lnet/minecraft/class_1;method_20(Lnet/minecraft/class_2;)V");
		av.visitEnd();
		cw.visitField(Opcodes.ACC_PRIVATE, "field_10", "Lnet/minecraft/class_2;", null, null).visitEnd();

		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "method_22", "()V", null, null);
		tick.visitCode();
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 1);
		tick.visitEnd();

		MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(Lnet/minecraft/class_1;Lnet/minecraft/class_2;)V", null, null);
		run.visitCode();
		run.visitVarInsn(Opcodes.ALOAD, 0);
		run.visitVarInsn(Opcodes.ALOAD, 1);
		run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_1", "method_20", "(Lnet/minecraft/class_2;)V", false);
		run.visitVarInsn(Opcodes.ALOAD, 0);
		run.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/class_1", "field_11", "I");
		run.visitInsn(Opcodes.POP);
		run.visitLdcInsn("net.minecraft.class_1");
		run.visitInsn(Opcodes.POP);
		run.visitLdcInsn("user text");
		run.visitInsn(Opcodes.POP);
		run.visitInsn(Opcodes.RETURN);
		run.visitMaxs(2, 2);
		run.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
