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
