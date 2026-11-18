package io.github.cmdralph.fabricated.core.remap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
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
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.mixin.Refmap;
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
	void joinsTheMethodsIntermediaryLeavesUnderTheirOfficialNamesToSrgs() throws Exception {
		// PLAIN-1: Intermediary leaves Pack.ResourcesSupplier.open (official c.open, unobfuscated) as it is, and lists no
		// method for it; SRG names it m_247679_. Fabric API's lambda implementing it stopped Forge 47's server
		// (AbstractMethodError). Here d.run()V is a Minecraft method SRG renames too, whose descriptor names no Minecraft
		// class: a mod's Runnable.run keeps its name.
		MappingTable t = plainSrgTable();
		String supplier = "net/minecraft/class_4";
		String openDesc = "(Ljava/lang/String;)Lnet/minecraft/class_1;";
		assertEquals("m_247679_", t.mapPlainMethod(supplier, "open", openDesc), "the owner's own");
		assertEquals("m_247679_", t.mapPlainMethod("mod/Supplier", "open", openDesc), "an override in a mod's class, by a descriptor naming Minecraft's class");
		assertEquals("m_99_", t.mapPlainMethod("net/minecraft/class_5", "run", "()V"));
		assertNull(t.mapPlainMethod("mod/Task", "run", "()V"), "a descriptor of the JDK's types alone is judged by its owner");
		assertNull(t.mapPlainMethod(supplier, "e", "()V"), "an obfuscated name Intermediary renames isn't one");
		assertNull(table().mapPlainMethod(supplier, "open", openDesc), "Mojang's names keep official ones: NeoForge's table has none");
		assertEquals("m_247679_", MappingTable.deserialize(t.serialize()).mapPlainMethod(supplier, "open", openDesc), "cached with the table");

		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "mod/Supplier", null, "java/lang/Object", new String[] {supplier, "java/lang/Runnable"});
		MethodVisitor open = cw.visitMethod(Opcodes.ACC_PUBLIC, "open", openDesc, null, null);
		open.visitInsn(Opcodes.ACONST_NULL);
		open.visitInsn(Opcodes.ARETURN);
		open.visitMaxs(1, 2);
		open.visitEnd();
		MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
		run.visitInsn(Opcodes.RETURN);
		run.visitMaxs(0, 1);
		run.visitEnd();
		MethodVisitor use = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "(L" + supplier + ";Lnet/minecraft/class_5;)L" + supplier + ";", null, null);
		use.visitVarInsn(Opcodes.ALOAD, 0);
		use.visitLdcInsn("id");
		use.visitMethodInsn(Opcodes.INVOKEINTERFACE, supplier, "open", openDesc, true);
		use.visitInsn(Opcodes.POP);
		use.visitVarInsn(Opcodes.ALOAD, 1);
		use.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/class_5", "run", "()V", true);
		Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
		use.visitInvokeDynamicInsn("open", "()L" + supplier + ";", metafactory, Type.getMethodType(openDesc),
				new Handle(Opcodes.H_INVOKESTATIC, "mod/Supplier", "lambda$use$0", openDesc, false), Type.getMethodType(openDesc));
		use.visitInsn(Opcodes.ARETURN);
		use.visitMaxs(2, 2);
		use.visitEnd();
		cw.visitEnd();

		ClassNode node = new ClassNode();
		new ClassReader(new JarRemapper(t).remapClass(cw.toByteArray(), "test")).accept(node, 0);
		List<String> declared = node.methods.stream().map(m -> m.name).toList();
		assertTrue(declared.contains("m_247679_"), "the override takes SRG's name: " + declared);
		assertTrue(declared.contains("run"), "Runnable.run keeps its name: " + declared);
		MethodNode remappedUse = node.methods.stream().filter(m -> m.name.equals("use")).findFirst().orElseThrow();
		List<String> calls = new ArrayList<>();
		for (AbstractInsnNode insn : remappedUse.instructions) {
			if (insn instanceof MethodInsnNode call) calls.add(call.owner + "." + call.name);
			if (insn instanceof InvokeDynamicInsnNode indy) calls.add("lambda " + indy.name);
		}
		assertEquals(List.of("net/minecraft/src/C_4_.m_247679_", "net/minecraft/src/C_5_.m_99_", "lambda m_247679_"), calls);
	}

	@Test
	void anOverrideOfAMethodOnlySrgRenamesTakesItsNameFromTheTypeItInherits(@TempDir Path tmp) throws Exception {
		// CORPUS-86: Moonlight's CaveFilter.Type implements PlacementModifierType and overrides codec(), which Intermediary
		// leaves under its official name and SRG names m_191869_. Its descriptor names no Minecraft class, and other owners
		// give codec() other names (class_10's m_200_ here), so only the class's supertypes tell. It kept codec, and Forge
		// 47's server stopped loading its data packs (AbstractMethodError).
		String tiny = TINY + "CLASS\th\tnet/minecraft/class_9\nCLASS\ti\tnet/minecraft/class_10\nCLASS\tj\tnet/minecraft/class_11\n";
		String tsrg2 = TSRG2 + "h net/minecraft/src/C_9_ 9\n\tcodec ()Lcom/mojang/serialization/Codec; m_191869_ 191869\n"
				+ "i net/minecraft/src/C_10_ 10\n\tcodec ()Lcom/mojang/serialization/Codec; m_200_ 200\n"
				+ "j net/minecraft/src/C_11_ 11\n";
		MappingTable t = MappingComposer.composeSrg(new StringReader(tiny), new StringReader(tsrg2), null);
		String codec = "()Lcom/mojang/serialization/Codec;";
		assertNull(t.mapPlainMethod("mod/CaveType", "codec", codec), "the descriptor alone doesn't tell");
		// The game, by its runtime names: class_11 (a placement filter) implements class_9 without declaring codec().
		Map<String, List<String>> game = Map.of("net/minecraft/src/C_11_", List.of("java/lang/Object", "net/minecraft/src/C_9_"),
				"net/minecraft/src/C_9_", List.of("java/lang/Object"));
		Supertypes gameTypes = Supertypes.ofRuntime(game::get, t);

		Path jar = TestJars.create()
				.bytes("mod/CaveType.class", codecClass("mod/CaveType", "java/lang/Object", true, "net/minecraft/class_9"))
				.bytes("mod/Base.class", codecClass("mod/Base", "java/lang/Object", false, "net/minecraft/class_9"))
				.bytes("mod/Sub.class", codecClass("mod/Sub", "mod/Base", true))
				.bytes("mod/Filter.class", codecClass("mod/Filter", "net/minecraft/class_11", true))
				.bytes("mod/Sized.class", codecClass("mod/Sized", "java/lang/Object", true, "net/minecraft/class_10"))
				.bytes("mod/Own.class", codecClass("mod/Own", "java/lang/Object", true, "java/util/function/Supplier"))
				.bytes("mod/User.class", userOfCodecs())
				.write(tmp.resolve("mod.jar"));
		Map<String, byte[]> files;
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t, gameTypes).remap(archive, Set.of()).files();
		}
		assertEquals(List.of("m_191869_"), declaredCodecs(files.get("mod/CaveType.class")), "an override of the interface's");
		assertEquals(List.of("m_191869_"), declaredCodecs(files.get("mod/Sub.class")), "one inherited through another of the jar's classes");
		assertEquals(List.of("m_191869_"), declaredCodecs(files.get("mod/Filter.class")), "one inherited through the game's classes");
		assertEquals(List.of("m_200_"), declaredCodecs(files.get("mod/Sized.class")), "another interface's, by its own name");
		assertEquals(List.of("codec"), declaredCodecs(files.get("mod/Own.class")), "a class inheriting no such method keeps its name");
		ClassNode user = new ClassNode();
		new ClassReader(files.get("mod/User.class")).accept(user, 0);
		List<String> calls = new ArrayList<>();
		for (AbstractInsnNode insn : user.methods.stream().filter(m -> m.name.equals("use")).findFirst().orElseThrow().instructions) {
			if (insn instanceof MethodInsnNode call) calls.add(call.owner + "." + call.name);
		}
		assertEquals(List.of("mod/CaveType.m_191869_", "mod/Sub.m_191869_", "net/minecraft/src/C_11_.m_191869_", "mod/Own.codec"), calls,
				"calls naming a class that inherits the method too");

		// One class at a time, as pre-flight reads another mod's, with the jar's classes read as needed.
		byte[] sub = codecClass("mod/Sub", "mod/Base", true);
		Map<String, byte[]> classes = Map.of("mod/Base", codecClass("mod/Base", "java/lang/Object", false, "net/minecraft/class_9"), "mod/Sub", sub);
		assertEquals(List.of("m_191869_"), declaredCodecs(new JarRemapper(t, gameTypes).with(Supertypes.ofClassFiles(classes::get)).remapClass(sub, "Sub")));
		assertEquals(List.of("codec"), declaredCodecs(new JarRemapper(t).remapClass(sub, "Sub")), "unknown supertypes tell nothing");
		assertEquals(List.of("codec"), declaredCodecs(new JarRemapper(table(), gameTypes).remapClass(codecClass("mod/CaveType", "java/lang/Object", true,
				"net/minecraft/class_1"), "CaveType")), "Mojang's names keep official ones: NeoForge's table has none");
	}

	/** A class declaring {@code codec()} or not, with the given supertypes. */
	private static byte[] codecClass(String name, String superName, boolean declares, String... interfaces) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | (declares ? 0 : Opcodes.ACC_ABSTRACT), name, null, superName, interfaces);
		if (declares) {
			MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, "codec", "()Lcom/mojang/serialization/Codec;", null, null);
			m.visitInsn(Opcodes.ACONST_NULL);
			m.visitInsn(Opcodes.ARETURN);
			m.visitMaxs(1, 1);
			m.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A class calling {@code codec()} on the test's classes. */
	private static byte[] userOfCodecs() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "mod/User", null, "java/lang/Object", null);
		MethodVisitor use = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "(Lmod/CaveType;Lmod/Sub;Lnet/minecraft/class_11;Lmod/Own;)V", null, null);
		String codec = "()Lcom/mojang/serialization/Codec;";
		String[] owners = {"mod/CaveType", "mod/Sub", "net/minecraft/class_11", "mod/Own"};
		for (int i = 0; i < owners.length; i++) {
			use.visitVarInsn(Opcodes.ALOAD, i);
			use.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owners[i], "codec", codec, false);
			use.visitInsn(Opcodes.POP);
		}
		use.visitInsn(Opcodes.RETURN);
		use.visitMaxs(1, 4);
		use.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The names of a class's methods of {@code codec()}'s descriptor. */
	private static List<String> declaredCodecs(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node.methods.stream().filter(m -> m.desc.equals("()Lcom/mojang/serialization/Codec;")).map(m -> m.name).toList();
	}

	@Test
	void aRefmapNamesAMethodIntermediaryLeavesUnderItsOfficialNameBySrgs() throws Exception {
		// PLAIN-1: Fabric API's transfer refmap gives DropperBlockMixin's @At target as
		// Lnet/minecraft/class_2357;dispense(...), a name Intermediary leaves and SRG renames (m_6115_). Under that name no
		// call occurs on Forge 47, whose pre-flight left the hook out.
		MappingTable t = plainSrgTable();
		String target = "Lnet/minecraft/class_4;open(Ljava/lang/String;)Lnet/minecraft/class_1;";
		String refmap = "{\"mappings\":{\"mod/DropperMixin\":{\"open\":\"" + target + "\",\"run\":\"Lnet/minecraft/class_5;run()V\","
				+ "\"tick\":\"Lnet/minecraft/class_4;tick()V\"}}}";
		String remapped = new String(new JarRemapper(t).remapRefmap(refmap.getBytes(StandardCharsets.UTF_8), "test"), StandardCharsets.UTF_8);
		assertTrue(remapped.contains("\"Lnet/minecraft/src/C_4_;m_247679_(Ljava/lang/String;)Lnet/minecraft/src/C_1_;\""), remapped);
		assertTrue(remapped.contains("\"Lnet/minecraft/src/C_5_;m_99_()V\""), "the owner's own, whatever its descriptor: " + remapped);
		assertTrue(remapped.contains("\"Lnet/minecraft/src/C_4_;tick()V\""), "a name SRG doesn't rename stays: " + remapped);
		assertTrue(remapped.contains("\"open\":"), "keys are the mod's names: " + remapped);
		assertEquals("@At(target=\"Lnet/minecraft/src/C_4_;m_247679_(Ljava/lang/String;)Lnet/minecraft/src/C_1_;\")",
				new TokenRemapper(t).remap("@At(target=\"" + target + "\")"), "the same in an annotation's string");
		assertTrue(new TokenRemapper(table()).remap(target).contains(";open(Ljava/lang/String;)"), "Mojang's names keep official ones");
	}

	@Test
	void aRefmapKeyNamingAnIntermediaryMethodIsRewrittenAsTheAnnotationIs() throws Exception {
		// REMAP-3: Fabric API's screen hooks name MouseHandler's lambdas as method_1611([ZLnet/minecraft/client/gui/screen/Screen;DDI)V,
		// with Yarn's names in the descriptor, which the refmap resolves. The annotation's string became m_168078_(...),
		// which the refmap's key, left as written, didn't match: the selector was read as it stood.
		MappingTable t = plainSrgTable();
		String selector = "method_60([ZLnet/minecraft/client/gui/screen/Screen;DDI)V";
		String refmap = "{\"mappings\":{\"mod/MouseMixin\":{\"" + selector + "\":\"Lnet/minecraft/class_7;method_60(F)F\",\"open\":\"Lnet/minecraft/class_4;tick()V\"}}}";
		Refmap remapped = Refmap.parse(new String(new JarRemapper(t).remapRefmap(refmap.getBytes(StandardCharsets.UTF_8), "test"), StandardCharsets.UTF_8));
		String annotation = new TokenRemapper(t).remap(selector);
		assertEquals("m_60_([ZLnet/minecraft/client/gui/screen/Screen;DDI)V", annotation);
		assertEquals("Lnet/minecraft/src/C_7_;m_60_(F)F", remapped.resolve("mod/MouseMixin", annotation), "the annotation's selector finds its entry");
		assertEquals("Lnet/minecraft/src/C_4_;tick()V", remapped.resolve("mod/MouseMixin", "open"), "a key in the mod's names stays");
	}

	@Test
	void anOfficialNameIntermediaryRenamesWithAnotherDescriptorIsJoinedToo() throws Exception {
		// PLAIN-1: Intermediary renames three unrelated get(F) methods on 1.20.1, which kept IoSupplier.get() (SRG's
		// m_247737_) out of the table; Fabric API's resource pack calling it stopped Forge 47's server (NoSuchMethodError).
		// An override of a renamed method shares its name and descriptor, and stays out (references to it go by the
		// intermediary name).
		MappingTable t = plainSrgTable();
		assertEquals("m_247737_", t.mapPlainMethod("net/minecraft/class_6", "get", "()Ljava/lang/Object;"));
		assertEquals("m_60_", t.mapMemberFlat("method_60"), "the renamed get(F)F maps by its intermediary name");
		assertNull(t.mapPlainMethod("net/minecraft/class_8", "get", "(F)F"), "an override of the renamed get(F)F isn't joined");
	}

	@Test
	void joinsTheFieldsIntermediaryLeavesUnderTheirOfficialNamesToSrgs(@TempDir Path tmp) throws Exception {
		// PLAIN-2: MCPConfig for 1.19.2 renames the members of the classes the official jar keeps unobfuscated, which
		// Intermediary lists none of: GlStateManager.BLEND is f_84066_ on Forge 43. Iris's accessor of it, by its official
		// name, failed pre-flight there, and its redirect of RenderSystem.setShader, named so in its @At, found no call.
		String gl = "com/mojang/blaze3d/platform/GlStateManager";
		String tiny = TINY + "CLASS\tc\tnet/minecraft/class_4\nFIELD\tc\tI\tlisted\tfield_40\n";
		String tsrg2 = TSRG2 + gl + " net/minecraft/src/C_3112_ 3112\n\tBLEND f_84066_ 84066\n\tDEPTH f_84067_ 84067\n"
				+ "\t_enableBlend ()V m_84525_ 84525\n"
				+ "com/mojang/blaze3d/systems/RenderSystem net/minecraft/src/C_3_ 3\n\tsetShader (Ljava/util/function/Supplier;)V m_157427_ 157427\n"
				+ "\tinitRenderer (IZ)V m_69580_ 69580\n\t_setShaderTexture (II)V m_157176_ 157176\n\t_setShaderTexture (ILa;)V m_157179_ 157179\n"
				+ "c net/minecraft/src/C_4_ 4\n\tlisted f_40_ 40\n\tkept f_41_ 41\n\tsame same 42\n";
		MappingTable t = MappingComposer.composeSrg(new StringReader(tiny), new StringReader(tsrg2), null);
		assertEquals("f_84066_", t.mapPlainField(gl, "BLEND"), "a field of a class the official jar keeps");
		assertEquals("f_41_", t.mapPlainField("net/minecraft/class_4", "kept"), "one the official jar keeps in a class it renames");
		assertNull(t.mapPlainField("net/minecraft/class_4", "listed"), "one Intermediary renames maps by its intermediary name");
		assertEquals("f_40_", t.mapMemberFlat("field_40"));
		assertNull(t.mapPlainField("net/minecraft/class_4", "same"), "one SRG keeps");
		assertNull(t.mapPlainField("mod/Other", "BLEND"), "another owner's");
		assertEquals("m_84525_", t.ownPlainMethod(gl, "_enableBlend", "()V"));
		assertNull(table().mapPlainField(gl, "BLEND"), "Mojang's names keep official ones: NeoForge's table has none");
		assertEquals("f_84066_", MappingTable.deserialize(t.serialize()).mapPlainField(gl, "BLEND"), "cached with the table");

		TokenRemapper tokens = new TokenRemapper(t);
		assertEquals("L" + gl + ";f_84066_:L" + gl + "$BlendState;", tokens.remap("L" + gl + ";BLEND:L" + gl + "$BlendState;"), "a field's @At target");
		assertEquals("Lcom/mojang/blaze3d/systems/RenderSystem;m_157427_(Ljava/util/function/Supplier;)V",
				tokens.remap("Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V"), "a method's, whose owner Intermediary keeps");
		assertEquals("Lmod/Other;setShader(Ljava/util/function/Supplier;)V", tokens.remap("Lmod/Other;setShader(Ljava/util/function/Supplier;)V"),
				"an owner outside Minecraft's is judged by itself");
		assertEquals("L" + gl + ";BLEND:I", new TokenRemapper(table()).remap("L" + gl + ";BLEND:I"));

		// An accessor interface of Iris's kind, a mixin shadowing the field and the method, and a class using the field.
		ClassWriter accessors = new ClassWriter(0);
		accessors.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "mod/GlAccessor", null, "java/lang/Object", null);
		AnnotationVisitor mixin = accessors.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor value = mixin.visitArray("value");
		value.visit(null, Type.getObjectType(gl));
		value.visitEnd();
		mixin.visitEnd();
		accessorMethod(accessors, "getBlend", "()Ljava/lang/Object;", "Lorg/spongepowered/asm/mixin/gen/Accessor;", "BLEND");
		accessorMethod(accessors, "getDEPTH", "()Ljava/lang/Object;", "Lorg/spongepowered/asm/mixin/gen/Accessor;", null);
		accessorMethod(accessors, "callEnable", "()V", "Lorg/spongepowered/asm/mixin/gen/Invoker;", "_enableBlend");
		accessorMethod(accessors, "getCount", "()I", "Lorg/spongepowered/asm/mixin/gen/Accessor;", "count");
		accessors.visitEnd();

		ClassWriter shadows = new ClassWriter(0);
		shadows.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "mod/GlMixin", null, "java/lang/Object", null);
		AnnotationVisitor targets = shadows.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("targets");
		targets.visit(null, gl.replace('/', '.'));
		targets.visitEnd();
		shadows.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "BLEND", "Ljava/lang/Object;", null, null).visitEnd();
		MethodVisitor enable = shadows.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "_enableBlend", "()V", null, null);
		enable.visitInsn(Opcodes.RETURN);
		enable.visitMaxs(0, 0);
		enable.visitEnd();
		MethodVisitor use = shadows.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "use", "()Ljava/lang/Object;", null, null);
		use.visitMethodInsn(Opcodes.INVOKESTATIC, "mod/GlMixin", "_enableBlend", "()V", false);
		use.visitFieldInsn(Opcodes.GETSTATIC, "mod/GlMixin", "BLEND", "Ljava/lang/Object;");
		use.visitInsn(Opcodes.ARETURN);
		use.visitMaxs(1, 0);
		use.visitEnd();
		shadows.visitEnd();

		ClassWriter user = new ClassWriter(0);
		user.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "mod/GlUser", null, "java/lang/Object", null);
		user.visitField(Opcodes.ACC_STATIC, "BLEND", "Ljava/lang/Object;", null, null).visitEnd();
		MethodVisitor read = user.visitMethod(Opcodes.ACC_STATIC, "read", "()V", null, null);
		read.visitFieldInsn(Opcodes.GETSTATIC, gl, "BLEND", "Ljava/lang/Object;");
		read.visitFieldInsn(Opcodes.GETSTATIC, "mod/GlUser", "BLEND", "Ljava/lang/Object;");
		read.visitInsn(Opcodes.POP2);
		read.visitInsn(Opcodes.RETURN);
		read.visitMaxs(2, 0);
		read.visitEnd();
		user.visitEnd();

		// Hooks into RenderSystem's methods by their names alone, or with their descriptors, as Iris's are (remap = false).
		ClassWriter hooks = new ClassWriter(0);
		hooks.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "mod/RenderMixin", null, "java/lang/Object", null);
		AnnotationVisitor render = hooks.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		render.visit(null, Type.getObjectType("com/mojang/blaze3d/systems/RenderSystem"));
		render.visitEnd();
		String[][] hooked = {{"initRenderer"}, {"_setShaderTexture"}, {"_setShaderTexture(II)V"}, {"setShader", "own"}};
		for (int i = 0; i < hooked.length; i++) {
			MethodVisitor handler = hooks.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "hook" + i, "()V", null, null);
			AnnotationVisitor inject = handler.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
			AnnotationVisitor methods = inject.visitArray("method");
			for (String selector : hooked[i]) methods.visit(null, selector);
			methods.visitEnd();
			inject.visitEnd();
			handler.visitInsn(Opcodes.RETURN);
			handler.visitMaxs(0, 0);
			handler.visitEnd();
		}
		hooks.visitEnd();

		Path jar = TestJars.create().bytes("mod/GlAccessor.class", accessors.toByteArray()).bytes("mod/GlMixin.class", shadows.toByteArray())
				.bytes("mod/GlUser.class", user.toByteArray()).bytes("mod/RenderMixin.class", hooks.toByteArray()).write(tmp.resolve("gl.jar"));
		// RenderSystem's methods in its class file's order: a selector by a name alone picks the first of that name.
		GameMethods order = name -> name.equals("com/mojang/blaze3d/systems/RenderSystem")
				? List.of("m_69580_(IZ)V", "m_157179_(ILnet/minecraft/src/C_1_;)V", "m_157176_(II)V", "m_157427_(Ljava/util/function/Supplier;)V") : null;
		Map<String, byte[]> files;
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t, Supertypes.NONE, order).remap(archive, Set.of()).files();
		}
		ClassNode accessor = new ClassNode();
		new ClassReader(files.get("mod/GlAccessor.class")).accept(accessor, 0);
		Map<String, Object> written = new java.util.LinkedHashMap<>();
		for (MethodNode m : accessor.methods) {
			List<Object> values = m.invisibleAnnotations.get(0).values;
			written.put(m.name, values == null ? null : values.get(1));
		}
		assertEquals(Map.of("getBlend", "f_84066_", "getDEPTH", "f_84067_", "callEnable", "m_84525_", "getCount", "count"), written,
				"an accessor's or invoker's target, named or inferred, takes SRG's name; another stays");

		ClassNode shadowing = new ClassNode();
		new ClassReader(files.get("mod/GlMixin.class")).accept(shadowing, 0);
		assertEquals(List.of("f_84066_"), shadowing.fields.stream().map(f -> f.name).toList(), "a mixin's shadow of its target's field");
		List<String> uses = new ArrayList<>();
		for (MethodNode m : shadowing.methods) {
			if (!m.name.equals("use")) {
				uses.add("declares " + m.name);
				continue;
			}
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call) uses.add(call.name);
				if (insn instanceof FieldInsnNode field) uses.add(field.name);
			}
		}
		assertEquals(List.of("declares m_84525_", "m_84525_", "f_84066_"), uses, "and of its method, and their uses");

		ClassNode using = new ClassNode();
		new ClassReader(files.get("mod/GlUser.class")).accept(using, 0);
		List<String> reads = new ArrayList<>();
		for (AbstractInsnNode insn : using.methods.get(0).instructions) {
			if (insn instanceof FieldInsnNode field) reads.add(field.owner + "." + field.name);
		}
		assertEquals(List.of(gl + ".f_84066_", "mod/GlUser.BLEND"), reads, "a read of the game's field; a class's own keeps its name");
		assertEquals(List.of("BLEND"), using.fields.stream().map(f -> f.name).toList());

		ClassNode hooking = new ClassNode();
		new ClassReader(files.get("mod/RenderMixin.class")).accept(hooking, 0);
		List<Object> selectors = new ArrayList<>();
		for (MethodNode m : hooking.methods) selectors.add(m.visibleAnnotations.get(0).values.get(1));
		assertEquals(List.of(List.of("m_69580_"), List.of("m_157179_"), List.of("m_157176_(II)V"), List.of("m_157427_", "own")),
				selectors.stream().map(s -> List.copyOf((List<?>) s)).toList(), "an injector's method of its target's, by name (the first of"
						+ " that name in the class, which Mixin picks, still by name alone) or with its descriptor; another stays");

		// Without the class's order, a name its overloads give apart stays, with a note: which one Mixin picks isn't known.
		JarRemapper.Result unordered;
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			unordered = new JarRemapper(t).remap(archive, Set.of());
		}
		ClassNode unknown = new ClassNode();
		new ClassReader(unordered.files().get("mod/RenderMixin.class")).accept(unknown, 0);
		assertEquals(List.of("_setShaderTexture"), unknown.methods.get(1).visibleAnnotations.get(0).values.get(1));
		assertTrue(unordered.leftOutMembers().stream().anyMatch(n -> n.contains("\"_setShaderTexture\" as it is")), unordered.leftOutMembers().toString());
	}

	@Test
	void aSelectorByNameAloneKeepsPickingTheOneOverloadMixinPicksOnForge47(@TempDir Path tmp) throws Exception {
		// PLAIN-2 on Forge 47's table, which has plain methods and no plain fields: a Fabric mixin's @Inject(method =
		// "getElement") into LootDataResolver, whose two overloads Intermediary leaves under that name and SRG names apart.
		// Loom's refmap keys it to one overload; turned into a selector per overload, the annotation no longer matched the
		// key, and the hook went into both. Unkeyed, Mixin takes the first of the name in the class: so does the remap.
		String tiny = TINY + "CLASS\th\tnet/minecraft/class_9\n";
		String tsrg2 = TSRG2 + "h net/minecraft/src/C_9_ 9\n\tgetElement (La;)Ljava/lang/Object; m_278667_ 278667\n"
				+ "\tgetElement (Lb;)Ljava/util/Optional; m_278789_ 278789\n";
		MappingTable t = MappingComposer.composeSrg(new StringReader(tiny), new StringReader(tsrg2), null);
		assertNull(t.mapPlainField("net/minecraft/class_9", "getElement"));
		assertEquals(Map.of(), t.plainFieldsOf("net/minecraft/class_9"), "Forge 47's table has no plain fields");
		String resolver = "net/minecraft/class_9";
		GameMethods order = name -> name.equals("net/minecraft/src/C_9_")
				? List.of("<init>()V", "m_278789_(Lnet/minecraft/src/C_2_;)Ljava/util/Optional;", "m_278667_(Lnet/minecraft/src/C_1_;)Ljava/lang/Object;") : null;

		String pinned = "L" + resolver + ";getElement(Lnet/minecraft/class_1;)Ljava/lang/Object;";
		Path keyed = TestJars.create().bytes("mod/LootMixin.class", injectingMixin("mod/LootMixin", List.of(resolver), "getElement"))
				.text("mod.refmap.json", "{\"mappings\":{\"mod/LootMixin\":{\"getElement\":\"" + pinned + "\"}}}").write(tmp.resolve("keyed.jar"));
		Map<String, byte[]> files;
		try (SafeArchive archive = SafeArchive.open(keyed, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t, Supertypes.NONE, order).remap(archive, Set.of("mod.refmap.json")).files();
		}
		assertEquals(List.of("getElement"), injectSelectors(files.get("mod/LootMixin.class")), "a selector the refmap keys stays, as Mixin reads it there");
		Refmap refmap = Refmap.parse(new String(files.get("mod.refmap.json"), StandardCharsets.UTF_8));
		assertEquals("Lnet/minecraft/src/C_9_;m_278667_(Lnet/minecraft/src/C_1_;)Ljava/lang/Object;", refmap.resolve("mod/LootMixin", "getElement"),
				"and the refmap's value is mapped");

		Path unkeyed = TestJars.create().bytes("mod/LootMixin.class", injectingMixin("mod/LootMixin", List.of(resolver), "getElement"))
				.bytes("mod/DescribedMixin.class", injectingMixin("mod/DescribedMixin", List.of(resolver), "getElement(Lnet/minecraft/class_1;)Ljava/lang/Object;"))
				.write(tmp.resolve("unkeyed.jar"));
		try (SafeArchive archive = SafeArchive.open(unkeyed, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t, Supertypes.NONE, order).remap(archive, Set.of()).files();
		}
		assertEquals(List.of("m_278789_"), injectSelectors(files.get("mod/LootMixin.class")), "the first of the name in the class, by name alone");
		assertEquals(List.of("m_278667_(Lnet/minecraft/src/C_1_;)Ljava/lang/Object;"), injectSelectors(files.get("mod/DescribedMixin.class")));

		// The order is the game class's, which the platform's patches add methods to: one of its own named so (in Mojang's
		// names) or under an SRG name of vanilla's, placed first, isn't what Fabric picks. The first of vanilla's own, which
		// the mapping table knows, is; where the class has another method of its runtime name, its descriptor pins it.
		GameMethods patched = name -> name.equals("net/minecraft/src/C_9_") ? List.of("getElement(Lnet/minecraftforge/Extra;)Ljava/lang/Object;",
				"m_278667_(Lnet/minecraftforge/Extra;)Ljava/lang/Object;", "m_278789_(Lnet/minecraft/src/C_2_;)Ljava/util/Optional;",
				"m_278667_(Lnet/minecraft/src/C_1_;)Ljava/lang/Object;") : null;
		GameMethods shared = name -> name.equals("net/minecraft/src/C_9_") ? List.of("m_278789_(Lnet/minecraftforge/Extra;)Ljava/util/Optional;",
				"m_278789_(Lnet/minecraft/src/C_2_;)Ljava/util/Optional;", "m_278667_(Lnet/minecraft/src/C_1_;)Ljava/lang/Object;") : null;
		try (SafeArchive archive = SafeArchive.open(unkeyed, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t, Supertypes.NONE, patched).remap(archive, Set.of()).files();
		}
		assertEquals(List.of("m_278789_"), injectSelectors(files.get("mod/LootMixin.class")), "the platform's methods placed first aren't picked");
		try (SafeArchive archive = SafeArchive.open(unkeyed, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t, Supertypes.NONE, shared).remap(archive, Set.of()).files();
		}
		assertEquals(List.of("m_278789_(Lnet/minecraft/src/C_2_;)Ljava/util/Optional;"), injectSelectors(files.get("mod/LootMixin.class")),
				"vanilla's, pinned by its descriptor where the platform's own of that runtime name comes first");
	}

	@Test
	void aMixinOfSeveralClassesKeepsAMemberTheyNameApart(@TempDir Path tmp) throws Exception {
		// PLAIN-2: a mixin of GlStateManager's DestFactor and SourceFactor shadowing the value both have, which Forge 43
		// names f_84646_ in one and f_84751_ in the other. Its shadow took the first target's name, which the second lacks.
		// No one name finds it in each, so it stays, with a note, as do an accessor and a selector of such a member; one
		// both name alike takes that name.
		String dest = "com/mojang/blaze3d/platform/GlStateManager$DestFactor";
		String source = "com/mojang/blaze3d/platform/GlStateManager$SourceFactor";
		String tsrg2 = TSRG2 + dest + " net/minecraft/src/C_4534_ 4534\n\tvalue f_84646_ 84646\n\tapply ()V m_1_ 1\n\tshared ()V m_3_ 3\n"
				+ source + " net/minecraft/src/C_4535_ 4535\n\tvalue f_84751_ 84751\n\tapply ()V m_2_ 2\n\tshared ()V m_3_ 3\n";
		MappingTable t = MappingComposer.composeSrg(new StringReader(TINY), new StringReader(tsrg2), null);
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "mod/FactorMixin", null, "java/lang/Object", null);
		AnnotationVisitor value = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		value.visit(null, Type.getObjectType(dest));
		value.visit(null, Type.getObjectType(source));
		value.visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "value", "I", null, null).visitEnd();
		MethodVisitor read = cw.visitMethod(Opcodes.ACC_PUBLIC, "read", "()I", null, null);
		read.visitVarInsn(Opcodes.ALOAD, 0);
		read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "mod/FactorMixin", "shared", "()V", false);
		read.visitVarInsn(Opcodes.ALOAD, 0);
		read.visitFieldInsn(Opcodes.GETFIELD, "mod/FactorMixin", "value", "I");
		read.visitInsn(Opcodes.IRETURN);
		read.visitMaxs(1, 1);
		read.visitEnd();
		MethodVisitor shared = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "shared", "()V", null, null);
		shared.visitEnd();
		accessorMethod(cw, "getValue", "()I", "Lorg/spongepowered/asm/mixin/gen/Accessor;", "value");
		MethodVisitor hook = cw.visitMethod(Opcodes.ACC_PRIVATE, "hook", "()V", null, null);
		AnnotationVisitor inject = hook.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, "apply()V");
		methods.visitEnd();
		inject.visitEnd();
		hook.visitInsn(Opcodes.RETURN);
		hook.visitMaxs(0, 1);
		hook.visitEnd();
		cw.visitEnd();

		Path jar = TestJars.create().bytes("mod/FactorMixin.class", cw.toByteArray()).write(tmp.resolve("factor.jar"));
		JarRemapper.Result result;
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			result = new JarRemapper(t).remap(archive, Set.of());
		}
		ClassNode node = new ClassNode();
		new ClassReader(result.files().get("mod/FactorMixin.class")).accept(node, 0);
		assertEquals(List.of("value"), node.fields.stream().map(f -> f.name).toList(), "the shadow its targets name apart stays");
		List<String> uses = new ArrayList<>();
		for (AbstractInsnNode insn : node.methods.stream().filter(m -> m.name.equals("read")).findFirst().orElseThrow().instructions) {
			if (insn instanceof FieldInsnNode f) uses.add(f.name);
			if (insn instanceof MethodInsnNode m) uses.add(m.name);
		}
		assertEquals(List.of("m_3_", "value"), uses, "a method both name alike takes that name");
		assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("m_3_")), "its declaration too");
		MethodNode getter = node.methods.stream().filter(m -> m.name.equals("getValue")).findFirst().orElseThrow();
		assertEquals(List.of("value", "value"), getter.invisibleAnnotations.get(0).values, "an accessor of it stays");
		assertEquals(List.of("apply()V"), node.methods.stream().filter(m -> m.name.equals("hook")).findFirst().orElseThrow().visibleAnnotations.get(0).values.get(1),
				"a selector of a method they name apart stays");
		String notes = String.join("\n", result.leftOutMembers());
		assertTrue(notes.contains("Left mod/FactorMixin.value under its name"), notes);
		assertTrue(notes.contains("accessor target \"value\""), notes);
		assertTrue(notes.contains("selector \"apply()V\""), notes);
	}

	@Test
	void anAccessorTakesTheFieldMixinMatchesAndAnEmptyTargetIsLeftOut(@TempDir Path tmp) throws Exception {
		// PLAIN-2: Mixin reads @Accessor("") as no target and infers one; the remap wrote the inferred one as a second value,
		// and Mixin read the first, "". And where no field has the accessor's name, Mixin takes the one field whose name
		// differs only in case (getBlend for BLEND): the remap looked the name up as it was and wrote nothing.
		String gl = "com/mojang/blaze3d/platform/GlStateManager";
		String tsrg2 = TSRG2 + gl + " net/minecraft/src/C_3112_ 3112\n\tBLEND f_84066_ 84066\n\tDEPTH f_84067_ 84067\n\tMODE f_1_ 1\n\tMode f_2_ 2\n";
		MappingTable t = MappingComposer.composeSrg(new StringReader(TINY), new StringReader(tsrg2), null);
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "mod/GlAccessor", null, "java/lang/Object", null);
		AnnotationVisitor value = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		value.visit(null, Type.getObjectType(gl));
		value.visitEnd();
		accessorMethod(cw, "getDEPTH", "()Ljava/lang/Object;", "Lorg/spongepowered/asm/mixin/gen/Accessor;", "");
		accessorMethod(cw, "getBlend", "()Ljava/lang/Object;", "Lorg/spongepowered/asm/mixin/gen/Accessor;", null);
		accessorMethod(cw, "getBlendState", "()Ljava/lang/Object;", "Lorg/spongepowered/asm/mixin/gen/Accessor;", "blend");
		accessorMethod(cw, "getMode", "()I", "Lorg/spongepowered/asm/mixin/gen/Accessor;", null);
		cw.visitEnd();
		Path jar = TestJars.create().bytes("mod/GlAccessor.class", cw.toByteArray()).write(tmp.resolve("gl.jar"));
		Map<String, byte[]> files;
		try (SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS)) {
			files = new JarRemapper(t).remap(archive, Set.of()).files();
		}
		ClassNode node = new ClassNode();
		new ClassReader(files.get("mod/GlAccessor.class")).accept(node, 0);
		Map<String, Object> written = new java.util.LinkedHashMap<>();
		for (MethodNode m : node.methods) written.put(m.name, m.invisibleAnnotations.get(0).values == null ? List.of() : m.invisibleAnnotations.get(0).values);
		assertEquals(List.of("value", "f_84067_"), written.get("getDEPTH"), "the empty target is left out for the inferred one");
		assertEquals(List.of("value", "f_84066_"), written.get("getBlend"), "an inferred name matching a field but for its case");
		assertEquals(List.of("value", "f_84066_"), written.get("getBlendState"), "a written one too");
		assertEquals(List.of(), written.get("getMode"), "two fields matching but for case: none is the one");
	}

	@Test
	void longRunsOfIdentifierCharactersAreScannedQuickly() throws Exception {
		// SEC-12: the patterns finding a member reference with any owner (PLAIN-2) and a class Intermediary keeps with
		// nested classes it names (TOKEN-1) started at every letter, and scanned what followed again from each: a string of
		// 64,000 'L's took 56 s, and one of 4,000 path segments overflowed the stack. Every string of a mod's class, its
		// annotations, refmaps (to 16 MiB), access wideners and service files is remapped so.
		MappingTable forge43 = MappingComposer.composeSrg(new StringReader(TINY), new StringReader(TSRG2
				+ "com/mojang/blaze3d/platform/GlStateManager net/minecraft/src/C_3112_ 3112\n\tBLEND f_84066_ 84066\n"), null);
		List<String> hostile = List.of("L".repeat(1_000_000) + ";(:", "La/b;".repeat(200_000) + "x(", "a/".repeat(500_000) + " x$class_1",
				"a".repeat(1_000_000) + " b/c$class_1", "a.".repeat(500_000) + "b$class_1", "field_1:L" + "a".repeat(1_000_000),
				"La/b;c(" + "L".repeat(1_000_000), "./".repeat(400_000) + "a/b$class_1", "/.".repeat(400_000) + "a.b$class_1 x",
				"/a".repeat(400_000) + " $class_1");
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			for (MappingTable table : List.of(forge43, table())) {
				TokenRemapper tokens = new TokenRemapper(table);
				for (String s : hostile) assertEquals(s, tokens.remap(s));
			}
		});
		TokenRemapper tokens = new TokenRemapper(forge43);
		assertEquals("Lcom/mojang/blaze3d/platform/GlStateManager;f_84066_:I", tokens.remap("Lcom/mojang/blaze3d/platform/GlStateManager;BLEND:I"),
				"a reference still matches");
		assertEquals("(Lcom/mojang/blaze3d/platform/GlStateManager;f_84066_:I)", tokens.remap("(Lcom/mojang/blaze3d/platform/GlStateManager;BLEND:I)"));
		assertEquals("Lcom/mojang/blaze3d/platform/GlStateManager$class_1017;", tokens.remap("Lcom/mojang/blaze3d/platform/GlStateManager$class_1017;"),
				"a nested class token is still read (unknown here, so kept)");
		assertEquals("xLcom/mojang/blaze3d/platform/GlStateManager;BLEND:I", tokens.remap("xLcom/mojang/blaze3d/platform/GlStateManager;BLEND:I"),
				"an L inside a word starts no reference");
	}

	/** A mixin of the targets with one {@code @Inject(method = selector)} handler. */
	private static byte[] injectingMixin(String name, List<String> targets, String selector) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		AnnotationVisitor value = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitArray("value");
		for (String target : targets) value.visit(null, Type.getObjectType(target));
		value.visitEnd();
		MethodVisitor handler = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "hook", "()V", null, null);
		AnnotationVisitor inject = handler.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, selector);
		methods.visitEnd();
		inject.visitEnd();
		handler.visitInsn(Opcodes.RETURN);
		handler.visitMaxs(0, 0);
		handler.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The selectors of a class's {@code @Inject}. */
	private static List<?> injectSelectors(byte[] cls) {
		ClassNode node = new ClassNode();
		new ClassReader(cls).accept(node, 0);
		return List.copyOf((List<?>) node.methods.get(0).visibleAnnotations.get(0).values.get(1));
	}

	/** An abstract or static accessor or invoker method with its annotation, its target written out or not. */
	private static void accessorMethod(ClassWriter cw, String name, String desc, String annotation, String target) {
		MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, desc, null, null);
		AnnotationVisitor av = m.visitAnnotation(annotation, false);
		if (target != null) av.visit("value", target);
		av.visitEnd();
		m.visitEnd();
	}

	/**
	 * Intermediary and SRG names for PLAIN-1: c.open, d.run and e.get(), which Intermediary leaves under their official
	 * names, f.get(F)F, which it renames, and g's override of that.
	 */
	private static MappingTable plainSrgTable() throws Exception {
		String tiny = TINY + "CLASS\tc\tnet/minecraft/class_4\nCLASS\td\tnet/minecraft/class_5\nCLASS\te\tnet/minecraft/class_6\n"
				+ "CLASS\tf\tnet/minecraft/class_7\nCLASS\tg\tnet/minecraft/class_8\nMETHOD\tf\t(F)F\tget\tmethod_60\n";
		String tsrg2 = TSRG2 + "c net/minecraft/src/C_4_ 4\n\topen (Ljava/lang/String;)La; m_247679_ 247679\n"
				+ "d net/minecraft/src/C_5_ 5\n\trun ()V m_99_ 99\n"
				+ "e net/minecraft/src/C_6_ 6\n\tget ()Ljava/lang/Object; m_247737_ 247737\n"
				+ "f net/minecraft/src/C_7_ 7\n\tget (F)F m_60_ 60\n"
				+ "g net/minecraft/src/C_8_ 8\n\tget (F)F m_60_ 60\n";
		return MappingComposer.composeSrg(new StringReader(tiny), new StringReader(tsrg2), null);
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
	void aSelectorsDescriptorTellsApartMembersIntermediaryNamesAlikeAndTheRuntimeDoesnt() {
		// REMAP-5: Bobby's refmap names OptionInstance.IntRange's maxInclusive field "comp_594:I"; Intermediary names the
		// IntRangeBase method maxInclusive() comp_594 too, and SRG names the two f_231640_ and m_214118_.
		MappingTable t = MappingTable.fromMembers(Map.of("net/minecraft/class_1", "net/minecraft/client/IntRange"), Map.of("comp_594", Map.of(
				"net/minecraft/class_1.comp_594I", "f_231640_",
				"net/minecraft/class_2.comp_594()I", "m_214118_")));
		TokenRemapper r = new TokenRemapper(t);
		assertEquals("f_231640_:I", r.remap("comp_594:I"));
		assertEquals("Lnet/minecraft/client/IntRange;f_231640_:I", r.remap("Lnet/minecraft/class_1;comp_594:I"));
		assertEquals("m_214118_()I", r.remap("comp_594()I"));
		assertEquals("comp_594", r.remap("comp_594"), "without a descriptor nothing tells them apart");
	}

	/** Two overloads Intermediary names apart and Mojang's names alike, and a method of their class named once. */
	private static MappingTable overloadedTable() throws Exception {
		String tiny = String.join("\n", "v1\tofficial\tintermediary",
				"CLASS\ta\tnet/minecraft/class_1",
				"CLASS\tb\tnet/minecraft/class_2",
				"METHOD\ta\t(Lb;)I\td\tmethod_30",
				"METHOD\ta\t(Lb;I)I\te\tmethod_31",
				"METHOD\ta\t()V\tf\tmethod_32",
				"");
		String proguard = String.join("\n",
				"net.minecraft.client.renderer.LevelRenderer -> a:",
				"    int getLightColor(net.minecraft.core.BlockPos) -> d",
				"    int getLightColor(net.minecraft.core.BlockPos,int) -> e",
				"    void tick() -> f",
				"net.minecraft.core.BlockPos -> b:",
				"");
		return MappingComposer.compose(new StringReader(tiny), new StringReader(proguard));
	}

	@Test
	void knowsTheMethodsWhoseRuntimeNameAnOverloadShares() throws Exception {
		MappingTable t = overloadedTable();
		assertEquals("(Lnet/minecraft/class_2;)I", t.overloadedDescriptor("method_30"));
		assertEquals("(Lnet/minecraft/class_2;I)I", t.overloadedDescriptor("method_31"));
		assertNull(t.overloadedDescriptor("method_32"), "tick is its class's only one");
		assertNull(table().overloadedDescriptor("method_20"), "addEntity and entityAt share an obfuscated name only");
		assertEquals("(Lnet/minecraft/class_2;I)I", MappingTable.deserialize(t.serialize()).overloadedDescriptor("method_31"));
	}

	/**
	 * Fabric Loom names a method by its intermediary name alone where that is unique: LambDynamicLights'
	 * {@code @ModifyReturnValue(method = "method_23793")} picks the getLightColor taking a block state on Fabric, and must
	 * pick it remapped too, not the first getLightColor (CORPUS-45).
	 */
	@Test
	void anInjectorSelectorNamingAnOverloadByItsIntermediaryNameKeepsPickingIt() throws Exception {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "com/mod/mixin/LightMixin", null, "java/lang/Object", null);
		MethodVisitor modifier = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "light", "(ILnet/minecraft/class_2;I)I", null, null);
		AnnotationVisitor returned = modifier.visitAnnotation("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;", true);
		AnnotationVisitor methods = returned.visitArray("method");
		methods.visit(null, "method_31");
		methods.visit(null, "method_32");
		methods.visitEnd();
		returned.visitEnd();
		modifier.visitEnd();
		MethodVisitor invoker = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "callLight", "(Lnet/minecraft/class_2;I)I", null, null);
		AnnotationVisitor invokes = invoker.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", false);
		invokes.visit("value", "method_31");
		invokes.visitEnd();
		invoker.visitEnd();
		cw.visitEnd();
		ClassNode node = new ClassNode();
		new ClassReader(new JarRemapper(overloadedTable()).remapClass(cw.toByteArray(), "test")).accept(node, 0);
		MethodNode light = node.methods.stream().filter(m -> m.name.equals("light")).findFirst().orElseThrow();
		assertEquals(List.of("getLightColor(Lnet/minecraft/core/BlockPos;I)I", "tick"), light.visibleAnnotations.getFirst().values.get(1),
				"the overload gets its descriptor; tick, its class's only one, stays a name");
		MethodNode call = node.methods.stream().filter(m -> m.name.equals("callLight")).findFirst().orElseThrow();
		assertEquals("getLightColor", call.invisibleAnnotations.getFirst().values.get(1), "an invoker names the method alone");
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
	void tokenRemapperMapsTheNestedClassesOfAClassIntermediaryKeepsByName() throws Exception {
		// TOKEN-1: Mojang's blaze3d classes keep their names in the game's jar, their nested classes don't; Iris's access
		// widener opens GlStateManager$class_1017, and ImmediatelyFast's a field of such a type.
		String tiny = TINY + String.join("\n",
				"CLASS\tcom/mojang/blaze3d/opengl/GlStateManager\tcom/mojang/blaze3d/opengl/GlStateManager",
				"CLASS\tcom/mojang/blaze3d/opengl/GlStateManager$a\tcom/mojang/blaze3d/opengl/GlStateManager$class_1017",
				"FIELD\tcom/mojang/blaze3d/opengl/GlStateManager\tLcom/mojang/blaze3d/opengl/GlStateManager$a;\tc\tfield_4900",
				"");
		String proguard = PROGUARD + String.join("\n",
				"com.mojang.blaze3d.opengl.GlStateManager -> com.mojang.blaze3d.opengl.GlStateManager:",
				"    com.mojang.blaze3d.opengl.GlStateManager$TextureState TEXTURE -> c",
				"com.mojang.blaze3d.opengl.GlStateManager$TextureState -> com.mojang.blaze3d.opengl.GlStateManager$a:",
				"");
		TokenRemapper r = new TokenRemapper(MappingComposer.compose(new StringReader(tiny), new StringReader(proguard)));
		assertEquals("accessible\tclass\tcom/mojang/blaze3d/opengl/GlStateManager$TextureState",
				r.remap("accessible\tclass\tcom/mojang/blaze3d/opengl/GlStateManager$class_1017"));
		assertEquals("accessible\tfield\tcom/mojang/blaze3d/opengl/GlStateManager\tTEXTURE\tLcom/mojang/blaze3d/opengl/GlStateManager$TextureState;",
				r.remap("accessible\tfield\tcom/mojang/blaze3d/opengl/GlStateManager\tfield_4900\tLcom/mojang/blaze3d/opengl/GlStateManager$class_1017;"),
				"in a descriptor too");
		assertEquals("com.mojang.blaze3d.opengl.GlStateManager$TextureState", r.remap("com.mojang.blaze3d.opengl.GlStateManager$class_1017"));
		assertEquals("(Lnet/minecraft/world/Entity;Lcom/mojang/blaze3d/opengl/GlStateManager$TextureState;)V",
				r.remap("(Lnet/minecraft/class_2;Lcom/mojang/blaze3d/opengl/GlStateManager$class_1017;)V"));
		assertEquals("com/example/Thing$class_9", r.remap("com/example/Thing$class_9"), "a class the table doesn't know stays");

		// After a path's lead, as Class.getResource is asked for a class file: SEC-12's start rule had left these alone.
		String n = "com/mojang/blaze3d/opengl/GlStateManager$class_1017";
		String mapped = "com/mojang/blaze3d/opengl/GlStateManager$TextureState";
		assertEquals("/" + mapped + ".class", r.remap("/" + n + ".class"));
		assertEquals("./" + mapped, r.remap("./" + n));
		assertEquals("/" + mapped.replace('/', '.'), r.remap("/" + n.replace('/', '.')));
		assertEquals("." + mapped.replace('/', '.'), r.remap("." + n.replace('/', '.')));
		assertEquals(mapped + ".class", r.remap(n + ".class"));
		assertEquals("[L" + mapped + ";", r.remap("[L" + n + ";"));
		assertEquals("Ljava/util/List<L" + mapped + ";>;", r.remap("Ljava/util/List<L" + n + ";>;"));
		assertEquals("{\"" + mapped + "\"}", r.remap("{\"" + n + "\"}"));
		assertEquals("assets/" + n, r.remap("assets/" + n), "a path in another folder isn't the class");
		assertEquals("(IL" + n + ";)V", r.remap("(IL" + n + ";)V"), "after a primitive in a descriptor: unmapped, as before SEC-12 (PLAIN-2's limits)");
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

	/**
	 * As Loom names the accessors of a jar without a refmap (Do a Barrel Roll's {@code KeyBindingAccessor.getField_1655}):
	 * Mixin reads the target from the name, which no member has on the runtime's names, so the remapper writes it out and
	 * remaps it (ACC-4). A target written out, or one read from a name of the mod's own, is left as it is.
	 */
	@Test
	void anAccessorNamedForAnIntermediaryMemberGetsItsTargetWrittenOut() throws Exception {
		String accessor = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
		String invoker = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/LevelAccessor", null, "java/lang/Object", null);
		String[][] methods = {
				{"getField_11", "()I", accessor, null},
				{"setField_10", "(Lnet/minecraft/class_2;)V", accessor, ""},
				{"invokeMethod_22", "()V", invoker, null},
				{"getOwner", "()Lnet/minecraft/class_2;", accessor, null},
				{"isField_11", "()Z", accessor, "field_10"},
				{"getField_11", "()J", "Lcom/example/NotAnAccessor;", null},
		};
		for (String[] m : methods) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, m[0], m[1], null, null);
			AnnotationVisitor av = mv.visitAnnotation(m[2], false);
			if (m[3] != null) av.visit("value", m[3]);
			av.visitEnd();
			mv.visitEnd();
		}
		cw.visitEnd();

		ClassNode node = new ClassNode();
		new ClassReader(new JarRemapper(table()).remapClass(cw.toByteArray(), "test")).accept(node, 0);
		Map<String, Object> targets = new java.util.LinkedHashMap<>();
		for (MethodNode m : node.methods) {
			List<Object> values = m.invisibleAnnotations.getFirst().values;
			targets.put(m.name + m.desc, values == null ? null : values.get(1));
		}
		Map<String, Object> expected = new java.util.LinkedHashMap<>();
		expected.put("getField_11()I", "count");
		expected.put("setField_10(Lnet/minecraft/world/Entity;)V", "owner");
		expected.put("invokeMethod_22()V", "tick");
		expected.put("getOwner()Lnet/minecraft/world/Entity;", null);
		expected.put("isField_11()Z", "owner");
		expected.put("getField_11()J", null);
		assertEquals(expected, targets, "the accessors keep their names; only the targets Mixin would read from them are written out");
	}

	/**
	 * Elementa's {@code UtilitiesKt}, in short: a {@code width} for each loader's text component, Fabric's taking
	 * {@code class_2} and Forge's {@code Entity}, with Kotlin's {@code width$default} for each, and a field for each.
	 * Remapped, each pair is one member, and Essential's Fabric build stopped the game with "Duplicate method name"
	 * (REMAP-4): the other loader's twins are left out and the Fabric members, which the jar's code reaches, are kept.
	 */
	@Test
	void anotherLoadersTwinOfAFabricMemberIsLeftOutAndTheFabricOneKept() throws Exception {
		String owner = "com/example/UtilitiesKt";
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, owner, null, "java/lang/Object", null);
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "LEVEL", "Lnet/minecraft/class_1;", null, null).visitEnd();
		cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "LEVEL", "Lnet/minecraft/world/Level;", null, null).visitEnd();
		returning(cw, "width", "(Lnet/minecraft/class_2;F)F", Opcodes.FCONST_1);
		returning(cw, "width$default", "(Lnet/minecraft/class_2;FILjava/lang/Object;)F", Opcodes.FCONST_1);
		returning(cw, "width", "(Lnet/minecraft/world/Entity;F)F", Opcodes.FCONST_2);
		returning(cw, "width$default", "(Lnet/minecraft/world/Entity;FILjava/lang/Object;)F", Opcodes.FCONST_2);
		// Forge 1.8's text component, which no mapping names, stays: it meets no other member.
		returning(cw, "width", "(Lnet/minecraft/util/IChatComponent;F)F", Opcodes.FCONST_0);
		cw.visitEnd();

		List<String> notes = new ArrayList<>();
		ClassNode node = new ClassNode();
		new ClassReader(new JarRemapper(table()).remapClass(cw.toByteArray(), "test", notes)).accept(node, 0);
		assertEquals(List.of("width(Lnet/minecraft/world/Entity;F)F", "width$default(Lnet/minecraft/world/Entity;FILjava/lang/Object;)F",
				"width(Lnet/minecraft/util/IChatComponent;F)F"), node.methods.stream().map(m -> m.name + m.desc).toList());
		assertTrue(node.methods.stream().filter(m -> m.desc.contains("Entity")).allMatch(m -> m.instructions.getFirst().getOpcode() == Opcodes.FCONST_1),
				"the Fabric members' code is kept");
		assertEquals(List.of("LEVEL:Lnet/minecraft/world/Level;"), node.fields.stream().map(f -> f.name + ":" + f.desc).toList());
		assertEquals(3, notes.size(), notes.toString());
		assertTrue(notes.contains("Left out com/example/UtilitiesKt.width(Lnet/minecraft/world/Entity;F)F, written for another loader: remapped, it is the same member "
				+ "as the Fabric width(Lnet/minecraft/class_2;F)F, which is kept (REMAP-4)"), notes.toString());

		// An override and the mod's own method of the game's name meet too, but neither is another loader's: both stay, with a note.
		ClassWriter clash = new ClassWriter(0);
		clash.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Ticking", null, "net/minecraft/class_2", null);
		returning(clash, "method_22", "()V", Opcodes.RETURN);
		returning(clash, "tick", "()V", Opcodes.RETURN);
		clash.visitEnd();
		List<String> clashNotes = new ArrayList<>();
		ClassNode ticking = new ClassNode();
		new ClassReader(new JarRemapper(table()).remapClass(clash.toByteArray(), "test", clashNotes)).accept(ticking, 0);
		assertEquals(2, ticking.methods.size());
		assertEquals(List.of("Remapped, [method_22()V, tick()V] of com/example/Ticking are all tick()V, which a class can't hold twice; the class will fail to load (REMAP-4)"),
				clashNotes);

		// A class without twins gets no note.
		List<String> none = new ArrayList<>();
		new JarRemapper(table()).remapClass(sampleModClass(), "test", none);
		assertEquals(List.of(), none);
	}

	/** A static method whose code is the one instruction {@code value}, then a return of its type. */
	private static void returning(ClassWriter cw, String name, String desc, int value) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, desc, null, null);
		mv.visitCode();
		if (value != Opcodes.RETURN) {
			mv.visitInsn(value);
			mv.visitInsn(Opcodes.FRETURN);
		} else {
			mv.visitInsn(Opcodes.RETURN);
		}
		mv.visitMaxs(1, Type.getArgumentsAndReturnSizes(desc) >> 2);
		mv.visitEnd();
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
			assertEquals(List.of(), result.leftOutMembers());
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
