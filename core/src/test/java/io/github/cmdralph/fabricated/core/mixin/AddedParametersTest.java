package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
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
	static final String BURNER = "net/minecraft/world/Burner";
	private static final String BURNER2 = "net/minecraft/world/Burner2";
	static final String STATE = "net/minecraft/world/State";
	static final String BLOCK = "net/minecraft/world/Block";
	static final String SIDE = "net/minecraft/world/Side";
	private static final String STATE_EXT = "net/neoforged/neoforge/common/extensions/IStateExtension";
	static final String SUB_EXT = "net/neoforged/neoforge/common/extensions/ISubStateExtension";
	static final String OTHER_EXT = "net/neoforged/neoforge/common/extensions/IOtherExtension";
	private static final String BLOCK_EXT = "net/neoforged/neoforge/common/extensions/IBlockExtension";
	static final String HOOKS = "net/neoforged/neoforge/common/FireHooks";
	private static final String VANILLA = "(Ljava/lang/Object;L" + STATE + ";I)V";
	private static final String PLATFORM = "(Ljava/lang/Object;L" + STATE + ";IL" + SIDE + ";)V";
	static final String FLAMMABILITY = "(Ljava/lang/Object;L" + SIDE + ";)I";
	static final String BLOCK_FLAMMABILITY = "(L" + STATE + ";Ljava/lang/Object;L" + SIDE + ";)I";
	private static final String CAUGHT_FIRE = "(Ljava/lang/Object;L" + SIDE + ";)V";
	static final String TAKES_SIDE = "(L" + SIDE + ";)V";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final String PLUGIN_CONFIG = "{\"required\":true,\"package\":\"com.example.mixin\",\"plugin\":\"com.example.Plugin\",\"mixins\":[\"BurnerAccessor\"]}";
	/** What the hint of a left-out invoker says before LEAVE-6's reason. */
	private static final String HINT = " Fabricated gives an invoker of it a fixed Side only where NeoForge's method passes it only to methods of NeoForge's own"
			+ " code whose code ignores it; here ";

	/**
	 * The proof bridges the invoker: the platform's method passes the side only to its extensions' methods, whose defaults
	 * pass it to the block's, which ignores it. The invoker is pointed at the bridge, which the target declares as
	 * pre-flight reads it, so the check finds nothing; the bridge passes {@code Side.WEST}, what the platform's first call
	 * passes, and the report says so, and that the call runs the platform's version of vanilla's method.
	 */
	@Test
	void anInvokerOfAMethodWhoseAddedParameterOnlyGoesToDefaultsIgnoringItIsBridgedWithTheFirstCallsConstant() throws Exception {
		Run run = run("");
		assertEquals(Set.of("com/example/mixin/BurnerAccessor.class"), run.repaired.keySet(), run.notes.toString());
		assertEquals(1, run.notes.size(), run.notes.toString());
		PreflightFinding note = run.notes.get(0);
		assertEquals(Severity.INFO, note.severity());
		assertEquals("@Invoker invokeBurn" + VANILLA + " targets a method NeoForge gave a Side vanilla's never had: in net.minecraft.world.Burner it invokes"
				+ " fabricated$invoke$burn, which Fabricated declares there as the class loads, calling burn" + PLATFORM + " with Side.WEST, what"
				+ " NeoForge's first call of it passes, and which passes the Side only to IStateExtension.flammability and IStateExtension.caughtFire,"
				+ " whose code ignores it. So the value changes nothing for whatever keeps that code, and the call does what an invoker of a"
				+ " method NeoForge patched keeping its parameters does: it runs NeoForge's version of vanilla's method (LEAVE-6)", note.message());
		ClassNode accessor = node(run.repaired.get("com/example/mixin/BurnerAccessor.class"));
		MethodNode invoker = accessor.methods.get(0);
		assertEquals("invokeBurn" + VANILLA, invoker.name + invoker.desc, "the mod's calls keep their method");
		assertEquals(List.of("value", "fabricated$invoke$burn", "remap", false), invoker.visibleAnnotations.get(0).values);
		assertEquals(Map.of(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "WEST"), List.of("com.example.mixin.BurnerAccessor @Invoker invokeBurn"
				+ VANILLA)), run.preflight.invokerBridges());
		assertTrue(run.check().isEmpty(), "the target declares the bridge as pre-flight reads it: " + run.check());
	}

	/** Forge 47's form: vanilla's private method replaced with one of another name taking the side too (STRUCT-2), bridged alike. */
	@Test
	void anInvokerOfAPrivateMethodReplacedWithOneTakingAParameterMoreIsBridgedToTheReplacement() throws Exception {
		Run run = run("replaced");
		assertEquals(1, run.notes.size(), run.notes.toString());
		assertTrue(run.notes.get(0).message().contains("in net.minecraft.world.Burner it invokes fabricated$invoke$burn, which Fabricated declares there as"
				+ " the class loads, calling catchFire" + PLATFORM), run.notes.toString());
		assertEquals(Set.of(new InvokerBridge(BURNER, "burn", VANILLA, "catchFire", PLATFORM, 3, "WEST")), run.preflight.invokerBridges().keySet());
		assertTrue(run.check().isEmpty(), run.check().toString());
	}

	/**
	 * The side may go to any method of the platform's own code that ignores it: a static hook, or an extension's method
	 * called on the interface; or nowhere. The platform's code may make what vanilla's doesn't, and leave vanilla's out:
	 * the bridge runs the platform's version of the method, as the platform's own calls do, and proves only what the value
	 * changes.
	 */
	@Test
	void aStaticHookAnInterfaceCallAndCodeOfThePlatformsOwnAreBridged() throws Exception {
		Map<String, String> passes = new LinkedHashMap<>();
		passes.put("staticCallee", "which passes the Side only to IStateExtension.flammability, IStateExtension.caughtFire and FireHooks.ignite, whose code"
				+ " ignores it.");
		passes.put("interfaceCallee", "which passes the Side only to IStateExtension.flammability and IStateExtension.caughtFire, whose code ignores it.");
		passes.put("added", "which passes the Side only to IStateExtension.flammability and IStateExtension.caughtFire, whose code ignores it.");
		passes.put("dropsAll", "which passes the Side only to IStateExtension.caughtFire, whose code ignores it.");
		passes.put("inverted", "which passes the Side only to IStateExtension.flammability and IStateExtension.caughtFire, whose code ignores it.");
		passes.put("ignores", "which never reads the Side.");
		for (Map.Entry<String, String> e : passes.entrySet()) {
			Run run = run(e.getKey());
			assertEquals(1, run.notes.size(), e.getKey() + ": " + run.notes);
			assertTrue(run.notes.get(0).message().contains("what NeoForge's first call of it passes, and " + e.getValue()), run.notes.toString());
			assertEquals(1, run.preflight.invokerBridges().size(), e.getKey());
			assertTrue(run.check().isEmpty(), e.getKey() + ": " + run.check());
		}
	}

	/**
	 * Where the proof fails, the invoker stays out as before, the report saying why, each with its own reason, and naming
	 * the known limit: the platform's method stores into the side, reads it otherwise than to pass it straight to a method
	 * (checks it, copies it on the stack, captures it in a lambda), calls a method on it, passes it to the game's code, to a
	 * method by invokespecial, statically to an instance method, deeper than followed (a chain, a recursion), or a default
	 * it passes it to reads it; the default that runs (the maximally specific one) reads it, or two could run; its first
	 * call passes no constant, a field that isn't final, or of a class that can't be read, or it is never called; or which
	 * of its methods stands in for vanilla's can't be told, it has no code, or the target is an interface.
	 */
	@Test
	void anInvokerStaysOutWhereTheProofFailsEachWithItsReasonAndTheKnownLimit() throws Exception {
		Map<String, String> why = new LinkedHashMap<>();
		why.put("store", "Burner.burn stores another value in the Side");
		why.put("reads", "Burner.burn reads the Side otherwise than to pass it straight to a method");
		why.put("dup", "Burner.burn reads the Side otherwise than to pass it straight to a method");
		why.put("swap", "Burner.burn reads the Side otherwise than to pass it straight to a method");
		why.put("lambda", "Burner.burn reads the Side otherwise than to pass it straight to a method");
		why.put("receiver", "Burner.burn calls Side.ordinal on the Side");
		why.put("game", "Burner.burn passes the Side to Burner.spread, which runs Burner's, not a method of NeoForge's own code");
		why.put("special", "Burner.burn passes the Side to Burner.spreadPrivately by invokespecial (a private method's or a superclass's), which Fabricated"
				+ " doesn't follow");
		why.put("staticMismatch", "Burner.burn passes the Side to Burner.spread, which Burner declares as an instance method where the call is static");
		why.put("deep", "FireHooks.second passes the Side on deeper than Fabricated follows it (3 methods)");
		why.put("recursive", "FireHooks.loop passes the Side on deeper than Fabricated follows it (3 methods)");
		why.put("defaultReads", "IBlockExtension.flammability calls Side.ordinal on the Side");
		why.put("sub", "ISubStateExtension.flammability calls Side.hashCode on the Side");
		why.put("twoDefaults", "Burner.burn passes the Side to State.flammability, for which IStateExtension and IOtherExtension each have a default, so"
				+ " which one runs can't be told");
		why.put("mutable", "the first call of it in Burner passes Side.LAST, which isn't a static final field of Side");
		why.put("computed", "the first call of it in Burner passes no constant Side");
		why.put("noConstantClass", "the class of the constant the first call of it in Burner passes, Side, can't be read");
		why.put("noCall", "Burner never calls it, so no call of the platform's gives a value");
		why.put("overloads", "Burner has 2 methods of that name taking one parameter more than vanilla's, and which one stands in for it can't be told");
		why.put("abstract", "NeoForge's method has no code of its own (it is abstract or native)");
		why.put("interface", "Burner is an interface, and Fabricated declares bridges in classes only");
		for (Map.Entry<String, String> e : why.entrySet()) {
			Run run = run(e.getKey());
			assertTrue(run.notes.isEmpty() && run.repaired.isEmpty(), e.getKey() + ": " + run.notes);
			assertTrue(run.preflight.invokerBridges().isEmpty(), e.getKey());
			List<PreflightFinding> findings = run.check();
			assertEquals(1, findings.size(), e.getKey() + ": " + findings);
			PreflightFinding f = findings.get(0);
			assertEquals(Severity.LEFT_OUT, f.severity(), f.toString());
			assertTrue(f.message().startsWith("@Invoker target method burn" + VANILLA + " does not exist in net.minecraft.world.Burner"), f.toString());
			assertTrue(f.suggestion().endsWith(HINT + e.getValue() + " (LEAVE-6)."), e.getKey() + ": " + f.suggestion());
			String limit = KnownLimits.ofFinding("NeoForge", f);
			assertTrue(limit != null && limit.endsWith("(LEAVE-6)"), e.getKey() + ": " + limit);
		}
	}

	/** An {@code iinc} of the added parameter, an int, is a store into it. */
	@Test
	void anIincOfTheAddedParameterIsAStoreIntoIt() throws Exception {
		String platform = "(Ljava/lang/Object;IL" + STATE + ";I)V";
		Map<String, byte[]> game = baseGame();
		game.put(BURNER, simpleBurner(BURNER, platform, mv -> mv.visitIincInsn(2, 1)));
		Run run = run(game, Map.of(BURNER, burner(true, "")), accessor(BURNER), PLUGIN_CONFIG);
		PreflightFinding f = run.check().get(0);
		assertTrue(f.suggestion().contains("here Burner.burn stores another value in the int (LEAVE-6)."), f.suggestion());
	}

	/**
	 * Where pre-flight can't tell vanilla's method, it gives no verdict, and the line keeps the reasons it gave before,
	 * no LEAVE-6 clause and no LEAVE-6 limit: an invoker of an installed mod's class (another version of that mod gave its
	 * method a parameter), and a game class whose vanilla class can't be read.
	 */
	@Test
	void anInvokerOfAnInstalledModsClassOrWhereVanillaIsntReadGetsNoVerdict() throws Exception {
		String thing = "net/example/lib/Thing";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, thing, null, "java/lang/Object", null);
		MethodVisitor foo = cw.visitMethod(Opcodes.ACC_PRIVATE, "foo", "(IL" + SIDE + ";)V", null, null);
		foo.visitCode();
		foo.visitInsn(Opcodes.RETURN);
		foo.visitMaxs(0, 0);
		foo.visitEnd();
		cw.visitEnd();
		byte[] thingClass = cw.toByteArray();
		// What "vanilla's" class would be, were the target's judged: one declaring foo(I)V, never called.
		ClassWriter old = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		old.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, thing, null, "java/lang/Object", null);
		MethodVisitor oldFoo = old.visitMethod(Opcodes.ACC_PRIVATE, "foo", "(I)V", null, null);
		oldFoo.visitCode();
		oldFoo.visitInsn(Opcodes.RETURN);
		oldFoo.visitMaxs(0, 0);
		oldFoo.visitEnd();
		old.visitEnd();
		Map<String, byte[]> oldThing = Map.of(thing, old.toByteArray());
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("SomeLib", thing::equals, name -> name.equals(thing) ? thingClass : null)));
		byte[] access = accessor(List.of(thing), "invokeFoo", "(I)V", "foo", false);
		Map<String, byte[]> mod = Map.of("com/example/mixin/BurnerAccessor", access);
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(installed), installed, mod::get, ClassBytesSource.EMPTY, false, true,
				oldThing::get, "NeoForge");
		List<PreflightFinding> installedFindings = preflight.check("test.mixins.json", MixinConfigFile.parse(PLUGIN_CONFIG), Refmap.EMPTY, Set.of()).stream()
				.filter(f -> f.severity() != Severity.INFO).toList();
		Run unread = run("novanilla");
		for (PreflightFinding f : List.of(installedFindings.get(0), unread.check().get(0))) {
			assertEquals(Severity.LEFT_OUT, f.severity(), f.toString());
			assertFalse(f.suggestion().contains("LEAVE-6"), f.suggestion());
			assertTrue(f.suggestion().contains("changed its parameters"), f.suggestion());
			assertNull(KnownLimits.ofFinding("NeoForge", f), f.toString());
		}
	}

	/**
	 * Vanilla's static method the platform gave a parameter: a static invoker of it isn't bridged (Fabricated declares
	 * bridges for instance methods only), and the line says so, a known limit.
	 */
	@Test
	void aStaticInvokerOfAStaticMethodIsntBridgedAndTheLineSaysWhy() throws Exception {
		String vanilla = "(Ljava/lang/Object;I)V";
		String platform = "(Ljava/lang/Object;IL" + SIDE + ";)V";
		Map<String, byte[]> game = baseGame();
		game.put(BURNER, staticBurner(platform, true));
		Run run = run(game, Map.of(BURNER, staticBurner(vanilla, false)), accessor(List.of(BURNER), "invokeIgnite", vanilla, "ignite", true), PLUGIN_CONFIG);
		assertTrue(run.notes.isEmpty() && run.preflight.invokerBridges().isEmpty(), run.notes.toString());
		PreflightFinding f = run.check().get(0);
		assertTrue(f.suggestion().endsWith(HINT + "vanilla's method is static, and Fabricated bridges instance methods only (LEAVE-6)."), f.suggestion());
		assertTrue(KnownLimits.ofFinding("NeoForge", f).endsWith("(LEAVE-6)"));
	}

	/**
	 * An invoker of two targets is bridged in each, the note naming each target; where one can't be, neither is, and the
	 * invoker stays out.
	 */
	@Test
	void anInvokerOfSeveralTargetsIsBridgedInEachOrNone() throws Exception {
		Map<String, byte[]> game = baseGame();
		game.put(BURNER, burner(false, ""));
		game.put(BURNER2, rename(burner(false, ""), BURNER, BURNER2));
		Map<String, byte[]> vanilla = Map.of(BURNER, burner(true, ""), BURNER2, rename(burner(true, ""), BURNER, BURNER2));
		byte[] both = accessor(List.of(BURNER, BURNER2), "invokeBurn", VANILLA, "burn", false);
		Run run = run(game, vanilla, both, PLUGIN_CONFIG);
		assertEquals(1, run.notes.size(), run.notes.toString());
		String note = run.notes.get(0).message();
		assertTrue(note.contains("in net.minecraft.world.Burner it invokes fabricated$invoke$burn") && note.contains("; in net.minecraft.world.Burner2 it invokes"
				+ " fabricated$invoke$burn"), note);
		assertEquals(Set.of(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "WEST"), new InvokerBridge(BURNER2, "burn", VANILLA, "burn",
				PLATFORM, 3, "WEST")), run.preflight.invokerBridges().keySet());
		assertTrue(run.check().isEmpty(), run.check().toString());

		game.put(BURNER2, rename(burner(false, "reads"), BURNER, BURNER2));
		Run half = run(game, vanilla, both, PLUGIN_CONFIG);
		assertTrue(half.notes.isEmpty() && half.preflight.invokerBridges().isEmpty(), half.notes.toString());
		assertFalse(half.check().isEmpty());
	}

	/** A bridge whose invoker is left out with its mixin (a mixin Mixin rejects whole) is none the game declares. */
	@Test
	void theBridgesOfAnInvokerLeftOutWithItsMixinArentDeclared() throws Exception {
		Map<String, byte[]> game = baseGame();
		game.put(BURNER, burner(false, ""));
		// A shadow of a method the burner hasn't: Mixin rejects the mixin whole in a config that isn't required.
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
		MethodVisitor shadow = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "vanish", "()V", null, null);
		shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		shadow.visitEnd();
		cw.visitEnd();
		Run run = run(game, Map.of(BURNER, burner(true, "")), cw.toByteArray(), "{\"required\":false,\"package\":\"com.example.mixin\",\"mixins\":[\"BurnerAccessor\"]}");
		assertEquals(1, run.notes.size(), "the invoker was pointed at the bridge: " + run.notes);
		assertTrue(run.check().stream().anyMatch(f -> f.message().equals("@Shadow method vanish()V does not exist in net.minecraft.world.Burner")),
				run.check().toString());
		assertEquals(Map.of(), run.preflight.invokerBridges(), "the mixin is out whole");
	}

	/**
	 * The bridge a class gets runs as the platform's method with the constant in the added parameter's place: vanilla's
	 * parameters loaded in their slots, a wide one among them, the parameter added in the middle or last, the private
	 * method called with {@code invokespecial} and a public one with {@code invokevirtual}, its result returned. It is
	 * private, so Mixin's invoker calls it as one of a private method ({@code invokespecial}), and a subclass's method of
	 * its name doesn't stand in for it; and it changes no method the class has. Nothing where the class hasn't the
	 * platform's method, or declares the bridge already.
	 */
	@Test
	void theBridgeCallsThePlatformsMethodWithTheConstantAndIsPrivate() throws Exception {
		String fire = "net/minecraft/world/Fire";
		String sub = "net/minecraft/world/FireSub";
		String vanilla = "(Ljava/lang/Object;JI)Ljava/lang/String;";
		for (int added : new int[] {1, 3}) {
			for (int access : new int[] {Opcodes.ACC_PRIVATE, Opcodes.ACC_PUBLIC}) {
				List<Type> params = new ArrayList<>(List.of(Type.getArgumentTypes(vanilla)));
				params.add(added, Type.getObjectType(SIDE));
				String platform = Type.getMethodDescriptor(Type.getType(String.class), params.toArray(Type[]::new));
				ClassNode node = node(fireClass(fire, platform, access));
				List<String> before = node.methods.stream().map(m -> m.access + " " + m.name + m.desc).toList();
				InvokerBridge bridge = new InvokerBridge(fire, "burn", vanilla, "burn", platform, added, "WEST");
				MethodNode method = bridge.method(node);
				assertNotNull(method);
				assertTrue(InvokerBridge.isBridge(method));
				assertEquals(Opcodes.ACC_PRIVATE, method.access);
				node.methods.add(method);
				assertNull(bridge.method(node), "declared once");
				assertEquals(before, node.methods.subList(0, before.size()).stream().map(m -> m.access + " " + m.name + m.desc).toList(),
						"no method of the class changed");
				node.methods.add(mixinsInvoker(node.name, method));
				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				Map<String, byte[]> classes = Map.of(fire, writer.toByteArray(), SIDE, side(false), sub, fireSubclass(fire, sub, vanilla));
				ClassLoader loader = loader(classes);
				Class<?> loaded = loader.loadClass(fire.replace('/', '.'));
				assertTrue(java.lang.reflect.Modifier.isPrivate(loaded.getDeclaredMethod("fabricated$invoke$burn", Object.class, long.class, int.class).getModifiers()));
				// The invoker Mixin generates, on an object of a subclass declaring a method of the bridge's name and descriptor.
				Method invoke = loaded.getMethod("invokeBurn", Object.class, long.class, int.class);
				Object subclass = loader.loadClass(sub.replace('/', '.')).getConstructor().newInstance();
				assertEquals("level 12345678901 7 WEST", invoke.invoke(subclass, "level", 12345678901L, 7), added + " " + access);
			}
		}
		assertNull(new InvokerBridge(fire, "burn", vanilla, "catchFire", "(Ljava/lang/Object;JILnet/minecraft/world/Side;)Ljava/lang/String;", 3, "WEST")
				.method(node(fireClass(fire, "(Ljava/lang/Object;JILnet/minecraft/world/Side;)Ljava/lang/String;", Opcodes.ACC_PRIVATE))),
				"no method of that name to call");
	}

	/**
	 * As a class loads, it takes a bridge only where it has the platform's method and its own first call of it passes the
	 * bridge's constant, as the class pre-flight read did; otherwise it declares nothing and the error names the invokers
	 * and LEAVE-6. Of two bridges of one name and descriptor, it keeps the one its code backs; where two fit, neither.
	 */
	@Test
	void aClassDeclaresABridgeOnlyWhereItsOwnCodeBacksIt() throws Exception {
		Map<InvokerBridge, List<String>> users = new LinkedHashMap<>();
		InvokerBridge west = new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "WEST");
		InvokerBridge east = new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "EAST");
		InvokerBridge missing = new InvokerBridge(BURNER, "burn", VANILLA, "catchFire", PLATFORM, 3, "WEST");
		users.put(west, List.of("mod-a: com.a.Mixin @Invoker invokeBurn" + VANILLA));
		users.put(east, List.of("mod-b: com.b.Mixin @Invoker invokeBurn" + VANILLA));
		users.put(missing, List.of("mod-c: com.c.Mixin @Invoker invokeBurn" + VANILLA));
		ClassNode node = node(burner(false, ""));
		List<String> errors = new ArrayList<>();
		assertEquals(List.of("fabricated$invoke$burn"), InvokerBridge.declare(node, users, errors::add));
		assertEquals(1, node.methods.stream().filter(m -> m.name.equals("fabricated$invoke$burn")).count());
		assertEquals(2, errors.size(), errors.toString());
		assertTrue(errors.get(0).equals("Fabricated couldn't declare fabricated$invoke$burn" + VANILLA + " in net.minecraft.world.Burner for mod-b:"
				+ " com.b.Mixin @Invoker invokeBurn" + VANILLA + ": the class's first call of burn doesn't pass Side.EAST. Calls of those invokers fail (LEAVE-6)"),
				errors.get(0));
		assertTrue(errors.get(1).contains("for mod-c: com.c.Mixin @Invoker invokeBurn" + VANILLA + ": the class has no instance method catchFire" + PLATFORM
				+ " with code to call"), errors.get(1));

		// Two that fit: the class calls burn and its twin burnTwin first with WEST, and the bridges call one each.
		ClassNode twice = node(burner(false, "twin"));
		List<String> both = new ArrayList<>();
		Map<InvokerBridge, List<String>> twins = new LinkedHashMap<>();
		twins.put(west, List.of("mod-a: a"));
		twins.put(new InvokerBridge(BURNER, "burn", VANILLA, "burnTwin", PLATFORM, 3, "WEST"), List.of("mod-b: b"));
		assertEquals(List.of(), InvokerBridge.declare(twice, twins, both::add));
		assertTrue(both.size() == 1 && both.get(0).contains("2 bridges of that name fit the class"), both.toString());
	}

	/** What a translation cache entry read back must be, or it is dropped and the mod translated again. */
	@Test
	void aBridgeNoTranslationMakesIsMalformed() {
		assertNull(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "WEST").malformed());
		assertNull(new InvokerBridge(BURNER, "m_221150_", VANILLA, "tryCatchFire", PLATFORM, 3, "WEST").malformed());
		Map<InvokerBridge, String> bad = new LinkedHashMap<>();
		bad.put(new InvokerBridge("com/example/Thing", "burn", VANILLA, "burn", PLATFORM, 3, "WEST"), "isn't a game class");
		bad.put(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 3, "WEST;"), "isn't one a class can declare");
		bad.put(new InvokerBridge(BURNER, "<init>", VANILLA, "burn", PLATFORM, 3, "WEST"), "isn't one a class can declare");
		bad.put(new InvokerBridge(BURNER, "burn", null, "burn", PLATFORM, 3, "WEST"), "it has no descriptor");
		bad.put(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 2, "WEST"), "don't differ by one parameter at 2");
		bad.put(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, 9, "WEST"), "don't differ by one parameter at 9");
		bad.put(new InvokerBridge(BURNER, "burn", VANILLA, "burn", PLATFORM, -1, "WEST"), "don't differ by one parameter at -1");
		bad.put(new InvokerBridge(BURNER, "burn", VANILLA, "burn", VANILLA, 2, "WEST"), "don't differ by one parameter at 2");
		bad.put(new InvokerBridge(BURNER, "burn", VANILLA, "burn", "(Ljava/lang/Object;L" + STATE + ";IJ)V", 3, "WEST"), "isn't of a class or interface type");
		bad.put(new InvokerBridge(BURNER, "burn", "(", "burn", "(", 0, "WEST"), "can't be read");
		for (Map.Entry<InvokerBridge, String> e : bad.entrySet()) {
			String why = e.getKey().malformed();
			assertTrue(why != null && why.contains(e.getValue()), e.getKey() + " -> " + why);
		}
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
	 * as {@code variant} makes it ({@link #burner}'s, and: {@code sub}, the state also implementing a sub-interface of its
	 * extension whose default reads the side; {@code twoDefaults}, another interface with a default of the same method;
	 * {@code defaultReads}, the block's default asking the side's ordinal; {@code noConstantClass}, no side class;
	 * {@code novanilla}, no vanilla classes).
	 */
	private static Run run(String variant) throws Exception {
		Map<String, byte[]> game = baseGame();
		game.put(BURNER, burner(false, variant));
		if (variant.equals("sub")) game.put(STATE, state(STATE_EXT, SUB_EXT));
		if (variant.equals("twoDefaults")) game.put(STATE, state(STATE_EXT, OTHER_EXT));
		if (variant.equals("defaultReads")) game.put(BLOCK_EXT, blockExtension(true));
		if (variant.equals("mutable")) game.put(SIDE, side(true));
		if (variant.equals("noConstantClass")) game.remove(SIDE);
		Map<String, byte[]> vanilla = variant.equals("novanilla") ? Map.of() : Map.of(BURNER, burner(true, ""));
		return run(game, vanilla, accessor(BURNER), PLUGIN_CONFIG);
	}

	private static Run run(Map<String, byte[]> game, Map<String, byte[]> vanilla, byte[] accessor, String json) throws Exception {
		MixinConfigFile config = MixinConfigFile.parse(json);
		Map<String, byte[]> current = new HashMap<>(Map.of("com/example/mixin/BurnerAccessor", accessor));
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(game::get), InstalledClasses.NONE, n -> current.get(n), ClassBytesSource.EMPTY, false, true,
				vanilla::get, "NeoForge");
		List<PreflightFinding> notes = new ArrayList<>();
		Map<String, byte[]> repaired = preflight.repairs("test.mixins.json", config, Refmap.EMPTY, Set.of(), notes);
		// The check reads the mixins as translation leaves them.
		repaired.forEach((file, data) -> current.put(file.substring(0, file.length() - ".class".length()), data));
		return new Run(preflight, config, repaired, notes);
	}

	/** The game but the burner: the state, the block, the side, the platform's extensions and hooks. */
	static Map<String, byte[]> baseGame() {
		return new HashMap<>(Map.of(STATE, state(STATE_EXT), BLOCK, block(), SIDE, side(false), STATE_EXT, stateExtension(), BLOCK_EXT, blockExtension(false),
				SUB_EXT, subExtension(), OTHER_EXT, otherExtension(), HOOKS, hooks()));
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(null) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
	}

	/** The class with every mention of {@code from} made {@code to}'s. */
	private static byte[] rename(byte[] data, String from, String to) {
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(data).accept(new org.objectweb.asm.commons.ClassRemapper(writer, new org.objectweb.asm.commons.SimpleRemapper(from, to)), 0);
		return writer.toByteArray();
	}

	/** The mod's accessor: an invoker of vanilla's {@code burn}. */
	private static byte[] accessor(String target) {
		return accessor(List.of(target), "invokeBurn", VANILLA, "burn", false);
	}

	private static byte[] accessor(List<String> targets, String name, String desc, String invoked, boolean isStatic) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "com/example/mixin/BurnerAccessor", null, "java/lang/Object", null);
		AnnotationVisitor m = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor value = m.visitArray("value");
		for (String target : targets) value.visit(null, Type.getObjectType(target));
		value.visitEnd();
		m.visitEnd();
		MethodVisitor call = cw.visitMethod(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : Opcodes.ACC_ABSTRACT), name, desc, null, null);
		AnnotationVisitor invoker = call.visitAnnotation(INVOKER, true);
		invoker.visit("value", invoked);
		invoker.visitEnd();
		if (isStatic) {
			call.visitCode();
			call.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
			call.visitInsn(Opcodes.DUP);
			call.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
			call.visitInsn(Opcodes.ATHROW);
			call.visitMaxs(2, 4);
		}
		call.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static ClassWriter writer() {
		return new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				return "java/lang/Object";
			}
		};
	}

	/**
	 * The burner, vanilla's or the platform's: {@code tick(level, state)} burns twice, and {@code burn} removes the block
	 * where the state is flammable enough. The platform's as {@code variant} makes it: {@code replaced}, the method named
	 * {@code catchFire}; {@code mutable}, the first call passing a side that isn't final; {@code computed}, one the tick
	 * gets from a method; {@code noCall}, no call; {@code reads}, {@code store}, {@code dup}, {@code swap}, {@code lambda},
	 * {@code receiver}: the side checked for null, overwritten, copied, swapped, captured, asked its ordinal;
	 * {@code game}, {@code special}, {@code staticMismatch}, {@code staticCallee}, {@code deep}, {@code recursive}: the
	 * side passed to a method of the burner's, by invokespecial, statically to an instance method, to a platform hook that
	 * ignores it, through a chain of hooks, to a hook calling itself; {@code interfaceCallee}, the caught-fire extension
	 * called on the interface; {@code added}, a call vanilla's doesn't make; {@code dropsAll}, only the caught-fire call;
	 * {@code inverted}, the removal made whatever the flammability; {@code overloads}, a second method of the name taking one
	 * parameter more; {@code abstract}, the method abstract; {@code interface}, the burner an interface; {@code twin}, a twin method the tick also passes WEST to; {@code ignores}, vanilla's code.
	 */
	static byte[] burner(boolean vanilla, String variant) {
		ClassWriter cw = writer();
		boolean isAbstract = !vanilla && variant.equals("abstract");
		int itf = !vanilla && variant.equals("interface") ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : 0;
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | (isAbstract ? Opcodes.ACC_ABSTRACT : 0) | itf, BURNER, null, "java/lang/Object", null);
		String burn = vanilla ? "burn" : variant.equals("replaced") ? "catchFire" : "burn";
		String desc = vanilla ? VANILLA : PLATFORM;
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(Ljava/lang/Object;L" + STATE + ";)V", null, null);
		tick.visitCode();
		if (vanilla || !variant.equals("noCall")) {
			for (String side : List.of(variant.equals("mutable") ? "LAST" : "WEST", "EAST")) {
				for (String method : variant.equals("twin") ? List.of(burn, "burnTwin") : List.of(burn)) {
					tick.visitVarInsn(Opcodes.ALOAD, 0);
					tick.visitVarInsn(Opcodes.ALOAD, 1);
					tick.visitVarInsn(Opcodes.ALOAD, 2);
					tick.visitIntInsn(Opcodes.SIPUSH, 300);
					if (!vanilla && variant.equals("computed")) {
						tick.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "side", "()L" + SIDE + ";", false);
					} else if (!vanilla) {
						tick.visitFieldInsn(Opcodes.GETSTATIC, SIDE, side, "L" + SIDE + ";");
					}
					tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, method, desc, false);
				}
			}
		}
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		if (isAbstract) {
			cw.visitMethod(Opcodes.ACC_PROTECTED | Opcodes.ACC_ABSTRACT, burn, desc, null, null).visitEnd();
		} else {
			burnMethod(cw, burn, desc, vanilla || variant.equals("ignores"), variant);
			if (variant.equals("twin")) burnMethod(cw, "burnTwin", desc, false, "");
		}
		if (!vanilla && variant.equals("overloads")) burnMethod(cw, burn, "(Ljava/lang/Object;L" + STATE + ";IL" + BLOCK + ";)V", true, "");

		for (String[] own : new String[][] {{"remove", "(Ljava/lang/Object;)Z"}, {"spread", TAKES_SIDE}, {"event", "()V"}}) {
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
		MethodVisitor privately = cw.visitMethod(Opcodes.ACC_PRIVATE, "spreadPrivately", TAKES_SIDE, null, null);
		privately.visitCode();
		privately.visitInsn(Opcodes.RETURN);
		privately.visitMaxs(0, 0);
		privately.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The burner's {@code burn} ({@link #burner}'s variants); vanilla's where {@code vanilla}. */
	private static void burnMethod(ClassWriter cw, String burn, String desc, boolean vanilla, String variant) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, burn, desc, null, null);
		mv.visitCode();
		Label end = new Label();
		if (!vanilla && variant.equals("dropsAll")) {
			caughtFire(mv);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
			return;
		}
		if (!vanilla) {
			switch (variant) {
				case "reads" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitJumpInsn(Opcodes.IFNULL, end);
				}
				case "store" -> {
					mv.visitInsn(Opcodes.ACONST_NULL);
					mv.visitVarInsn(Opcodes.ASTORE, 4);
				}
				case "dup" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitInsn(Opcodes.DUP);
					mv.visitInsn(Opcodes.POP);
					mv.visitInsn(Opcodes.POP);
				}
				case "swap" -> {
					mv.visitInsn(Opcodes.ACONST_NULL);
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitInsn(Opcodes.SWAP);
					mv.visitInsn(Opcodes.POP);
					mv.visitInsn(Opcodes.POP);
				}
				case "lambda" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
									+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
					mv.visitInvokeDynamicInsn("run", "(L" + SIDE + ";)Ljava/lang/Runnable;", metafactory, Type.getType("()V"),
							new Handle(Opcodes.H_INVOKESTATIC, HOOKS, "ignite", TAKES_SIDE, false), Type.getType("()V"));
					mv.visitInsn(Opcodes.POP);
				}
				case "receiver" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIDE, "ordinal", "()I", false);
					mv.visitInsn(Opcodes.POP);
				}
				default -> {
				}
			}
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
		Label inverted = new Label();
		mv.visitJumpInsn(Opcodes.IF_ICMPLE, !vanilla && variant.equals("inverted") ? inverted : end);
		if (!vanilla && variant.equals("inverted")) mv.visitLabel(inverted);
		if (!vanilla) {
			if (variant.equals("interfaceCallee")) {
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitVarInsn(Opcodes.ALOAD, 4);
				mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, STATE_EXT, "caughtFire", CAUGHT_FIRE, true);
			} else {
				caughtFire(mv);
			}
			switch (variant) {
				case "game" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, "spread", TAKES_SIDE, false);
				}
				case "special" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, BURNER, "spreadPrivately", TAKES_SIDE, false);
				}
				case "staticMismatch" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, BURNER, "spread", TAKES_SIDE, false);
				}
				case "staticCallee", "deep", "recursive" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 4);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, variant.equals("deep") ? "first" : variant.equals("recursive") ? "loop" : "ignite", TAKES_SIDE, false);
				}
				case "added" -> {
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, "event", "()V", false);
				}
				default -> {
				}
			}
		}
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BURNER, "remove", "(Ljava/lang/Object;)Z", false);
		mv.visitInsn(Opcodes.POP);
		mv.visitLabel(end);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/** {@code state.caughtFire(level, side)}. */
	private static void caughtFire(MethodVisitor mv) {
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 4);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "caughtFire", CAUGHT_FIRE, false);
	}

	/**
	 * A burner whose platform's {@code burn} takes {@code desc} (vanilla's parameters and an int before the state), does
	 * {@code body} and returns; its tick passes 0 for the int.
	 */
	private static byte[] simpleBurner(String name, String desc, java.util.function.Consumer<MethodVisitor> body) {
		ClassWriter cw = writer();
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(Ljava/lang/Object;L" + STATE + ";)V", null, null);
		tick.visitCode();
		tick.visitVarInsn(Opcodes.ALOAD, 0);
		tick.visitVarInsn(Opcodes.ALOAD, 1);
		tick.visitInsn(Opcodes.ICONST_0);
		tick.visitVarInsn(Opcodes.ALOAD, 2);
		tick.visitIntInsn(Opcodes.SIPUSH, 300);
		tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "burn", desc, false);
		tick.visitInsn(Opcodes.RETURN);
		tick.visitMaxs(0, 0);
		tick.visitEnd();
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "burn", desc, null, null);
		mv.visitCode();
		body.accept(mv);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A burner with a static {@code ignite} of {@code desc}, passing its side, where {@code platform}, to a hook that ignores it. */
	private static byte[] staticBurner(String desc, boolean platform) {
		ClassWriter cw = writer();
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, BURNER, null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "ignite", desc, null, null);
		mv.visitCode();
		if (platform) {
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "ignite", TAKES_SIDE, false);
		}
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The game's block state, with the platform's extension interfaces: its block, and vanilla's {@code flammable()}. */
	private static byte[] state(String... interfaces) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, STATE, null, "java/lang/Object", interfaces);
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
		// What the bridge test's fire prints of a side: its constant's name.
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

	/** A sub-interface of the state extension whose default flammability asks the side's hash. */
	private static byte[] subExtension() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, SUB_EXT, null, "java/lang/Object", new String[] {STATE_EXT});
		MethodVisitor flammability = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammability", FLAMMABILITY, null, null);
		flammability.visitCode();
		flammability.visitVarInsn(Opcodes.ALOAD, 2);
		flammability.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SIDE, "hashCode", "()I", false);
		flammability.visitInsn(Opcodes.IRETURN);
		flammability.visitMaxs(0, 0);
		flammability.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An unrelated interface with a default flammability of its own, ignoring the side. */
	private static byte[] otherExtension() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, OTHER_EXT, null, "java/lang/Object", null);
		MethodVisitor flammability = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammability", FLAMMABILITY, null, null);
		flammability.visitCode();
		flammability.visitInsn(Opcodes.ICONST_0);
		flammability.visitInsn(Opcodes.IRETURN);
		flammability.visitMaxs(0, 0);
		flammability.visitEnd();
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
	 * The platform's fire hooks, static: {@code ignite} ignores the side; {@code first} passes it to {@code second}, which
	 * passes it to {@code ignite}; {@code loop} passes it to itself; {@code side()} answers one.
	 */
	private static byte[] hooks() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, HOOKS, null, "java/lang/Object", null);
		for (String[] hook : new String[][] {{"ignite", null}, {"first", "second"}, {"second", "ignite"}, {"loop", "loop"}}) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, hook[0], TAKES_SIDE, null, null);
			mv.visitCode();
			if (hook[1] != null) {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, hook[1], TAKES_SIDE, false);
			}
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		MethodVisitor side = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "side", "()L" + SIDE + ";", null, null);
		side.visitCode();
		side.visitFieldInsn(Opcodes.GETSTATIC, SIDE, "WEST", "L" + SIDE + ";");
		side.visitInsn(Opcodes.ARETURN);
		side.visitMaxs(0, 0);
		side.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The invoker Mixin's {@code AccessorGeneratorMethodProxy} generates in the target for an invoker of {@code target}:
	 * {@code invokespecial} where the method is private, else {@code invokevirtual}.
	 */
	private static MethodNode mixinsInvoker(String owner, MethodNode target) {
		MethodNode invoker = new MethodNode(Opcodes.ACC_PUBLIC, "invokeBurn", target.desc, null, null);
		invoker.visitCode();
		invoker.visitVarInsn(Opcodes.ALOAD, 0);
		int slot = 1;
		for (Type arg : Type.getArgumentTypes(target.desc)) {
			invoker.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
			slot += arg.getSize();
		}
		boolean isPrivate = (target.access & Opcodes.ACC_PRIVATE) != 0;
		invoker.visitMethodInsn(isPrivate ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL, owner, target.name, target.desc, false);
		invoker.visitInsn(Type.getReturnType(target.desc).getOpcode(Opcodes.IRETURN));
		invoker.visitMaxs(slot + 1, slot);
		invoker.visitEnd();
		return invoker;
	}

	/** A fire whose {@code burn} (the platform's, of {@code desc}, with {@code access}) answers its arguments as text, the side last. */
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

	/** A subclass of the fire declaring a public method of the bridge's name and descriptor, answering {@code "sub"}. */
	private static byte[] fireSubclass(String fire, String sub, String vanilla) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, sub, null, fire, null);
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, fire, "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		MethodVisitor own = cw.visitMethod(Opcodes.ACC_PUBLIC, "fabricated$invoke$burn", vanilla, null, null);
		own.visitCode();
		own.visitLdcInsn("sub");
		own.visitInsn(Opcodes.ARETURN);
		own.visitMaxs(0, 0);
		own.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
