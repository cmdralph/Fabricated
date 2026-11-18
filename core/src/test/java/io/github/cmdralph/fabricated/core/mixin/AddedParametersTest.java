package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.report.KnownLimits;

/**
 * LEAVE-6: an {@code @Invoker} of a vanilla method the platform gave a parameter vanilla's never had, as Supplementaries'
 * of {@code FireBlock.checkBurnOut}, to which NeoForge and Forge add the side the fire comes from. The game here is a
 * burner of that shape: vanilla's private {@code burn(level, state, chance)} asks the state's {@code flammable()}; the
 * platform's {@code burn(level, state, chance, side)} asks its extension's {@code flammability(level, side)} instead,
 * whose default asks the block's extension, whose default ignores the side, and its {@code caughtFire(level, side)},
 * whose default does nothing; the platform's tick passes {@code Side.WEST}, then {@code Side.EAST}.
 */
class AddedParametersTest {
	private static final String BURNER = "net/minecraft/world/Burner";
	private static final String STATE = "net/minecraft/world/State";
	private static final String BLOCK = "net/minecraft/world/Block";
	private static final String SIDE = "net/minecraft/world/Side";
	private static final String STATE_EXT = "net/neoforged/neoforge/common/extensions/IStateExtension";
	private static final String BLOCK_EXT = "net/neoforged/neoforge/common/extensions/IBlockExtension";
	private static final String VANILLA = "(Ljava/lang/Object;L" + STATE + ";I)V";
	private static final String PLATFORM = "(Ljava/lang/Object;L" + STATE + ";IL" + SIDE + ";)V";
	private static final String FLAMMABILITY = "(Ljava/lang/Object;L" + SIDE + ";)I";
	private static final String BLOCK_FLAMMABILITY = "(L" + STATE + ";Ljava/lang/Object;L" + SIDE + ";)I";
	private static final String CAUGHT_FIRE = "(Ljava/lang/Object;L" + SIDE + ";)V";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

	/**
	 * The proof bridges the invoker: the platform's method passes the side only to its extensions' methods, whose defaults
	 * pass it to the block's, which ignores it, and makes nothing else vanilla's doesn't. The invoker is pointed at the
	 * bridge, which the target declares as pre-flight reads it, so the check finds nothing; the bridge passes
	 * {@code Side.WEST}, what the platform's first call passes, and the report says so. Without the bridge the invoker
	 * was left out, and a call of it failed.
	 */
	@Test
	void anInvokerOfAMethodWhoseAddedParameterOnlyGoesToDefaultsIgnoringItIsBridgedWithTheFirstCallsConstant() throws Exception {
		Run run = run("");
		assertEquals(Set.of("com/example/mixin/BurnerAccessor.class"), run.repaired.keySet(), run.notes.toString());
		assertEquals(1, run.notes.size(), run.notes.toString());
		PreflightFinding note = run.notes.get(0);
		assertEquals(Severity.INFO, note.severity());
		assertEquals("@Invoker invokeBurn" + VANILLA + " targets a method NeoForge gave a Side vanilla's never had in net.minecraft.world.Burner, burn" + PLATFORM
				+ "; it invokes fabricated$invoke$burn, which Fabricated declares there as the class loads, calling that method with Side.WEST, what"
				+ " NeoForge's first call of it passes: NeoForge's method passes the Side only to IStateExtension.flammability and"
				+ " IStateExtension.caughtFire, whose defaults ignore it, and makes nothing else vanilla's doesn't, so for whatever keeps those"
				+ " defaults it does what it does for NeoForge's own calls (LEAVE-6)", note.message());
		ClassNode accessor = node(run.repaired.get("com/example/mixin/BurnerAccessor.class"));
		MethodNode invoker = accessor.methods.get(0);
		assertEquals("invokeBurn" + VANILLA, invoker.name + invoker.desc, "the mod's calls keep their method");
		assertEquals(List.of("value", "fabricated$invoke$burn", "remap", false), invoker.visibleAnnotations.get(0).values);
		assertEquals(Set.of(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "WEST")), run.preflight.invokerBridges());
		assertTrue(run.check().isEmpty(), "the target declares the bridge as pre-flight reads it: " + run.check());
	}

	/** Forge 47's form: vanilla's private method replaced with one of another name taking the side too (STRUCT-2), bridged alike. */
	@Test
	void anInvokerOfAPrivateMethodReplacedWithOneTakingAParameterMoreIsBridgedToTheReplacement() throws Exception {
		Run run = run("replaced");
		assertEquals(1, run.notes.size(), run.notes.toString());
		assertTrue(run.notes.get(0).message().contains("gave a Side vanilla's never had in net.minecraft.world.Burner, catchFire" + PLATFORM
				+ "; it invokes fabricated$invoke$burn"), run.notes.toString());
		assertEquals(Set.of(new InvokerBridge(BURNER, "burn", VANILLA, "catchFire", PLATFORM, 3, "WEST")), run.preflight.invokerBridges());
		assertTrue(run.check().isEmpty(), run.check().toString());
	}

	/**
	 * Where the proof fails, the invoker stays out as before, the report saying why and naming the known limit: the
	 * platform's method reads the side otherwise than to pass it on, passes it to a method of the game's, or a default it
	 * passes it to reads it; it makes something vanilla's doesn't; or its first call passes no constant.
	 */
	@Test
	void anInvokerStaysOutWhereTheAddedParameterIsReadOrTheMethodIsntVanillasOtherwise() throws Exception {
		Map<String, String> why = new java.util.LinkedHashMap<>();
		why.put("reads", "Burner.burn reads the Side otherwise than to pass it to a method");
		why.put("game", "Burner.burn passes the Side to Burner.spread, which isn't a method of NeoForge's own code");
		why.put("defaultReads", "IBlockExtension.flammability calls Side.ordinal on the Side");
		why.put("receiver", "Burner.burn calls Side.ordinal on the Side");
		why.put("added", "Burner.burn makes what vanilla's doesn't (a call of Burner.event)");
		why.put("mutable", "the first call of it in Burner passes no constant Side");
		for (Map.Entry<String, String> e : why.entrySet()) {
			Run run = run(e.getKey());
			assertTrue(run.notes.isEmpty() && run.repaired.isEmpty(), e.getKey() + ": " + run.notes);
			assertTrue(run.preflight.invokerBridges().isEmpty(), e.getKey());
			List<PreflightFinding> findings = run.check();
			assertEquals(1, findings.size(), e.getKey() + ": " + findings);
			PreflightFinding f = findings.get(0);
			assertEquals(Severity.LEFT_OUT, f.severity(), f.toString());
			assertTrue(f.message().startsWith("@Invoker target method burn" + VANILLA + " does not exist in net.minecraft.world.Burner"), f.toString());
			assertTrue(f.suggestion().endsWith(" Fabricated gives an invoker of it a fixed Side only where NeoForge's method passes it only to its"
					+ " extensions' methods whose defaults ignore it, and makes nothing else vanilla's doesn't; " + e.getValue() + " (LEAVE-6)."), e.getKey() + ": " + f);
			String limit = KnownLimits.ofFinding("NeoForge", f);
			assertTrue(limit != null && limit.endsWith("(LEAVE-6)"), e.getKey() + ": " + limit);
		}
	}

	/**
	 * The bridge a class gets runs as the platform's method with the constant in the added parameter's place: vanilla's
	 * parameters loaded in their slots, a wide one among them, the parameter added in the middle or last, the private
	 * method called with {@code invokespecial} and a public one with {@code invokevirtual}, its result returned; nothing
	 * where the class hasn't the platform's method, or declares the bridge already.
	 */
	@Test
	void theBridgeCallsThePlatformsMethodWithTheConstantInTheAddedParametersPlace() throws Exception {
		String fire = "net/minecraft/world/Fire";
		String vanilla = "(Ljava/lang/Object;JI)Ljava/lang/String;";
		for (int added : new int[] {1, 3}) {
			for (int access : new int[] {Opcodes.ACC_PRIVATE, Opcodes.ACC_PUBLIC}) {
				List<Type> params = new ArrayList<>(List.of(Type.getArgumentTypes(vanilla)));
				params.add(added, Type.getObjectType(SIDE));
				String platform = Type.getMethodDescriptor(Type.getType(String.class), params.toArray(Type[]::new));
				ClassNode node = node(fireClass(fire, platform, access));
				InvokerBridge bridge = new InvokerBridge(fire, "burn", vanilla, "burn", platform, added, "WEST");
				MethodNode method = bridge.method(node);
				assertNotNull(method);
				assertTrue(InvokerBridge.isBridge(method));
				node.methods.add(method);
				assertNull(bridge.method(node), "declared once");
				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				Map<String, byte[]> classes = Map.of(fire, writer.toByteArray(), SIDE, side(false));
				Class<?> loaded = new ClassLoader(null) {
					@Override
					protected Class<?> findClass(String name) throws ClassNotFoundException {
						byte[] data = classes.get(name.replace('.', '/'));
						if (data == null) throw new ClassNotFoundException(name);
						return defineClass(name, data, 0, data.length);
					}
				}.loadClass(fire.replace('/', '.'));
				Object instance = loaded.getConstructor().newInstance();
				Method invoke = loaded.getMethod("fabricated$invoke$burn", Object.class, long.class, int.class);
				assertEquals("level 12345678901 7 WEST", invoke.invoke(instance, "level", 12345678901L, 7), added + " " + access);
			}
		}
		assertNull(new InvokerBridge(fire, "burn", vanilla, "catchFire", "(Ljava/lang/Object;JILnet/minecraft/world/Side;)Ljava/lang/String;", 3, "WEST")
				.method(node(fireClass(fire, "(Ljava/lang/Object;JILnet/minecraft/world/Side;)Ljava/lang/String;", Opcodes.ACC_PRIVATE))),
				"no method of that name to call");
	}

	/** One pre-flight run of the accessor against the game: its repairs, the run, the configs' check. */
	private record Run(MixinPreflight preflight, MixinConfigFile config, Map<String, byte[]> repaired, List<PreflightFinding> notes) {
		/** The config's check, of the mixins as the repairs left them. */
		List<PreflightFinding> check() {
			return preflight.check("test.mixins.json", config, Refmap.EMPTY, Set.of()).stream().filter(f -> f.severity() != Severity.INFO).toList();
		}
	}

	/**
	 * Pre-flight of the accessor in a config with a plugin, which leaves a broken invoker out, with the platform's game
	 * as {@code variant} makes it: "" as described; {@code replaced}: the method replaced by {@code catchFire};
	 * {@code reads}: it also checks the side for null; {@code game}: it passes the side to a method of the burner's;
	 * {@code defaultReads}: the block's default asks the side's ordinal; {@code receiver}: the method asks it itself;
	 * {@code added}: it also calls a method of the burner's vanilla's doesn't; {@code mutable}: the tick's first call
	 * passes a side read from a field that isn't final.
	 */
	private static Run run(String variant) throws Exception {
		Map<String, byte[]> game = new HashMap<>(Map.of(BURNER, burner(false, variant), STATE, state(), BLOCK, block(), SIDE, side(variant.equals("mutable")),
				STATE_EXT, stateExtension(), BLOCK_EXT, blockExtension(variant.equals("defaultReads"))));
		Map<String, byte[]> vanilla = Map.of(BURNER, burner(true, ""));
		MixinConfigFile config = MixinConfigFile.parse("{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.Plugin\","
				+ "\"mixins\":[\"BurnerAccessor\"]}");
		Map<String, byte[]> current = new HashMap<>(Map.of("com/example/mixin/BurnerAccessor", accessor()));
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(game::get), InstalledClasses.NONE, n -> current.get(n), ClassBytesSource.EMPTY, false, true,
				vanilla::get, "NeoForge");
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		// The check reads the mixins as translation leaves them.
		repaired.forEach((file, data) -> current.put(file.substring(0, file.length() - ".class".length()), data));
		return new Run(preflight, config, repaired, notes);
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	/** The mod's accessor: an invoker of vanilla's {@code burn}. */
	private static byte[] accessor() {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/BurnerAccessor", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targets = m.visitArray("value");
		targets.visit(null, Type.getObjectType(BURNER));
		targets.visitEnd();
		m.visitEnd();
		MethodVisitor call = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "invokeBurn", VANILLA, null, null);
		AnnotationVisitor invoker = call.visitAnnotation(INVOKER, true);
		invoker.visit("value", "burn");
		invoker.visitEnd();
		call.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The burner, vanilla's or the platform's ({@link #run}'s variants): {@code tick(level, state)} burns twice, and
	 * {@code burn} removes the block where the state is flammable enough.
	 */
	private static byte[] burner(boolean vanilla, String variant) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				return "java/lang/Object";
			}
		};
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, BURNER, null, "java/lang/Object", null);
		String burn = vanilla ? "burn" : variant.equals("replaced") ? "catchFire" : "burn";
		String desc = vanilla ? VANILLA : PLATFORM;
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(Ljava/lang/Object;L" + STATE + ";)V", null, null);
		tick.visitCode();
		for (String side : List.of(variant.equals("mutable") ? "LAST" : "WEST", "EAST")) {
			tick.visitVarInsn(Opcodes.ALOAD, 0);
			tick.visitVarInsn(Opcodes.ALOAD, 1);
			tick.visitVarInsn(Opcodes.ALOAD, 2);
			tick.visitIntInsn(Opcodes.SIPUSH, 300);
			if (!vanilla) tick.visitFieldInsn(Opcodes.GETSTATIC, SIDE, side, "L" + SIDE + ";");
			tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, burn, desc, false);
		}
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();

		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, burn, desc, null, null);
		mv.visitCode();
		Label end = new Label();
		if (!vanilla && variant.equals("reads")) {
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitJumpInsn(Opcodes.IFNULL, end);
		}
		if (!vanilla && variant.equals("receiver")) {
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIDE, "ordinal", "()I", false);
			mv.visitInsn(Opcodes.POP);
		}
		if (vanilla) {
			// state.flammable() > chance
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "flammable", "()I", false);
		} else {
			// state.flammability(level, side) > chance
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "flammability", FLAMMABILITY, false);
		}
		mv.visitVarInsn(Opcodes.ILOAD, 3);
		mv.visitJumpInsn(Opcodes.IF_ICMPLE, end);
		if (!vanilla) {
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "caughtFire", CAUGHT_FIRE, false);
		}
		if (!vanilla && variant.equals("game")) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 4);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, "spread", "(L" + SIDE + ";)V", false);
		}
		if (!vanilla && variant.equals("added")) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, "event", "()V", false);
		}
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, "remove", "(Ljava/lang/Object;)Z", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitLabel(end);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		for (String[] own : new String[][] {{"remove", "(Ljava/lang/Object;)Z"}, {"spread", "(L" + SIDE + ";)V"}, {"event", "()V"}}) {
			MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, own[0], own[1], null, null);
			m.visitCode();
			if (own[1].endsWith("Z")) {
				m.visitInsn(Opcodes.ICONST_1);
				m.visitInsn(Opcodes.IRETURN);
			} else {
				m.visitInsn(Opcodes.RETURN);
			}
			m.visitMaxs(0, 0);
			m.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The game's block state, with the platform's extension: its block, and vanilla's {@code flammable()}. */
	private static byte[] state() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, STATE, null, "java/lang/Object", new String[] {STATE_EXT});
		MethodVisitor block = cw.visitMethod(Opcodes.ACC_PUBLIC, "block", "()L" + BLOCK + ";", null, null);
		block.visitCode();
		block.visitInsn(Opcodes.ACONST_NULL);
		block.visitInsn(Opcodes.ARETURN);
		block.visitMaxs(0, 0);
		block.visitEnd();
		MethodVisitor flammable = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammable", "()I", null, null);
		flammable.visitCode();
		flammable.visitInsn(Opcodes.ICONST_5);
		flammable.visitInsn(Opcodes.IRETURN);
		flammable.visitMaxs(0, 0);
		flammable.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The game's block, with the platform's extension. */
	private static byte[] block() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, BLOCK, null, "java/lang/Object", new String[] {BLOCK_EXT});
		MethodVisitor odds = cw.visitMethod(Opcodes.ACC_PUBLIC, "odds", "(L" + STATE + ";)I", null, null);
		odds.visitCode();
		odds.visitInsn(Opcodes.ICONST_5);
		odds.visitInsn(Opcodes.IRETURN);
		odds.visitMaxs(0, 0);
		odds.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The sides: {@code WEST} and {@code EAST}, static final; {@code LAST}, static only. */
	private static byte[] side(boolean withMutable) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, SIDE, null, "java/lang/Object", null);
		for (String name : List.of("WEST", "EAST")) cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name, "L" + SIDE + ";", null, null).visitEnd();
		if (withMutable) cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "LAST", "L" + SIDE + ";", null, null).visitEnd();
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		clinit.visitCode();
		for (String name : List.of("WEST", "EAST")) {
			clinit.visitTypeInsn(Opcodes.NEW, SIDE);
			clinit.visitInsn(Opcodes.DUP);
			clinit.visitLdcInsn(name);
			clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, SIDE, "<init>", "(Ljava/lang/String;)V", false);
			clinit.visitFieldInsn(Opcodes.PUTSTATIC, SIDE, name, "L" + SIDE + ";");
		}
		clinit.visitInsn(Opcodes.RETURN);
		clinit.visitMaxs(0, 0);
		clinit.visitEnd();
		// What the bridge test's burner prints of a side: its constant's name.
		MethodVisitor string = cw.visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
		string.visitCode();
		Label east = new Label();
		string.visitVarInsn(Opcodes.ALOAD, 0);
		string.visitFieldInsn(Opcodes.GETSTATIC, SIDE, "WEST", "L" + SIDE + ";");
		string.visitJumpInsn(Opcodes.IF_ACMPNE, east);
		string.visitLdcInsn("WEST");
		string.visitInsn(Opcodes.ARETURN);
		string.visitLabel(east);
		string.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		string.visitLdcInsn("EAST");
		string.visitInsn(Opcodes.ARETURN);
		string.visitMaxs(0, 0);
		string.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The platform's state extension: its defaults pass the side to the block's flammability, and ignore it catching fire. */
	private static byte[] stateExtension() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, STATE_EXT, null, "java/lang/Object", null);
		MethodVisitor flammability = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammability", FLAMMABILITY, null, null);
		flammability.visitCode();
		flammability.visitVarInsn(Opcodes.ALOAD, 0);
		flammability.visitTypeInsn(Opcodes.CHECKCAST, STATE);
		flammability.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "block", "()L" + BLOCK + ";", false);
		flammability.visitVarInsn(Opcodes.ALOAD, 0);
		flammability.visitTypeInsn(Opcodes.CHECKCAST, STATE);
		flammability.visitVarInsn(Opcodes.ALOAD, 1);
		flammability.visitVarInsn(Opcodes.ALOAD, 2);
		flammability.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BLOCK, "flammability", BLOCK_FLAMMABILITY, false);
		flammability.visitInsn(Opcodes.IRETURN);
		flammability.visitMaxs(0, 0);
		flammability.visitEnd();
		MethodVisitor caught = cw.visitMethod(Opcodes.ACC_PUBLIC, "caughtFire", CAUGHT_FIRE, null, null);
		caught.visitCode();
		caught.visitInsn(Opcodes.RETURN);
		caught.visitMaxs(0, 0);
		caught.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The platform's block extension: its default answers the block's odds, ignoring the side, or ({@code reads}) asks its ordinal first. */
	private static byte[] blockExtension(boolean reads) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, BLOCK_EXT, null, "java/lang/Object", null);
		MethodVisitor flammability = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammability", BLOCK_FLAMMABILITY, null, null);
		flammability.visitCode();
		if (reads) {
			flammability.visitVarInsn(Opcodes.ALOAD, 3);
			flammability.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIDE, "ordinal", "()I", false);
			flammability.visitInsn(Opcodes.POP);
		}
		flammability.visitVarInsn(Opcodes.ALOAD, 0);
		flammability.visitTypeInsn(Opcodes.CHECKCAST, BLOCK);
		flammability.visitVarInsn(Opcodes.ALOAD, 1);
		flammability.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BLOCK, "odds", "(L" + STATE + ";)I", false);
		flammability.visitInsn(Opcodes.IRETURN);
		flammability.visitMaxs(0, 0);
		flammability.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A fire whose {@code burn} (the platform's, of {@code desc}, with {@code access}) answers its arguments as text, the
	 * side last, for {@link #theBridgeCallsThePlatformsMethodWithTheConstantInTheAddedParametersPlace}.
	 */
	private static byte[] fireClass(String fire, String desc, int access) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, fire, null, "java/lang/Object", null);
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor burn = cw.visitMethod(access, "burn", desc, null, null);
		burn.visitCode();
		// level + " " + time + " " + chance + " " + side, whatever order they come in
		Type[] args = Type.getArgumentTypes(desc);
		int[] slots = new int[args.length];
		for (int k = 0, slot = 1; k < args.length; slot += args[k].getSize(), k++) slots[k] = slot;
		burn.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		burn.visitInsn(Opcodes.DUP);
		burn.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
		String[] order = {"Ljava/lang/Object;", "J", "I", "L" + SIDE + ";"};
		for (int o = 0; o < order.length; o++) {
			for (int k = 0; k < args.length; k++) {
				if (!args[k].getDescriptor().equals(order[o])) continue;
				if (o > 0) {
					burn.visitLdcInsn(" ");
					burn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				}
				burn.visitVarInsn(args[k].getOpcode(Opcodes.ILOAD), slots[k]);
				String appended = args[k].getSort() == Type.OBJECT ? "Ljava/lang/Object;" : args[k].getDescriptor();
				burn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(" + appended + ")Ljava/lang/StringBuilder;", false);
			}
		}
		burn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
		burn.visitInsn(Opcodes.ARETURN);
		burn.visitMaxs(0, 0);
		burn.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
