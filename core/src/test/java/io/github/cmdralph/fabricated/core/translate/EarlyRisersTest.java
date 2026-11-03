package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.testutil.TestEarlyRisers;
import io.github.cmdralph.fabricated.core.testutil.TestJars;

/** CORPUS-80 on Forge 47: Fabric ASM's enum additions, read from the mods' early risers and made enum mixins. */
class EarlyRisersTest {
	private static final String TINKERERS = "com/chocohead/mm/api/ClassTinkerers";
	private static final String ADDER = "com/chocohead/mm/api/EnumAdder";
	private static final String BOOK_TYPE = "net/minecraft/world/inventory/RecipeBookType";
	private static final String CATEGORIES = "net/minecraft/client/RecipeBookCategories";
	private static final String STACK = "net/minecraft/world/item/ItemStack";
	/** The game's names for the mod's intermediary ones, as Fabric's mapping resolver gives them. */
	private static final UnaryOperator<String> MAPPINGS = name -> Map.of("net/minecraft/class_5421", BOOK_TYPE, "net/minecraft/class_314", CATEGORIES,
			"net/minecraft/class_1799", STACK).getOrDefault(name, name);
	private static final String RISER = "com/cooking/CookingASM";
	private static final String STACKS = "com/cooking/Stacks";

	/**
	 * Farmer's Delight's early riser: its recipe book type on both sides, and on a client its search category, whose
	 * constructor takes an array of item stacks, named through the mapping resolver, from a supplier a static method gives.
	 */
	@Test
	void farmersDelightsEarlyRiserGivesItsRecipeBookTypeAndOnAClientItsSearchCategory() {
		Function<String, ClassNode> classes = nodes(Map.of(RISER, TestEarlyRisers.cooking(RISER, "net.minecraft.class_5421", "net.minecraft.class_314",
				"net.minecraft.class_1799", RISER)));
		EarlyRisers.Addition cooking = new EarlyRisers.Addition(BOOK_TYPE, List.of(), "COOKING", new EarlyRisers.Given(List.of()));

		EarlyRisers.Read server = EarlyRisers.read("com.cooking.CookingASM", classes, false, MAPPINGS);
		assertNull(server.unread());
		assertEquals(List.of(cooking), server.additions());

		EarlyRisers.Read client = EarlyRisers.read("com.cooking.CookingASM", classes, true, MAPPINGS);
		assertNull(client.unread());
		assertEquals(List.of(cooking, new EarlyRisers.Addition(CATEGORIES, List.of(Type.getType("[L" + STACK + ";")), "COOKING_SEARCH",
				new EarlyRisers.Supplied(RISER, "stacks", "()Ljava/util/function/Supplier;", true, false))), client.additions());
		assertEquals("(Ljava/lang/String;I[L" + STACK + ";)V", client.additions().get(1).constructor());
		assertEquals(Map.of(BOOK_TYPE, List.of(cooking), CATEGORIES, List.of(client.additions().get(1))), EarlyRisers.byTarget(client.additions()));
	}

	/** Constants given as they are, of each kind a constructor may take, and a lambda supplier in the early riser itself. */
	@Test
	void givenArgumentsAndALambdaSupplierAreRead() {
		byte[] riser = TestEarlyRisers.riser("com/cooking/Riser", run -> {
			run.visitLdcInsn("com.game.Kind");
			run.visitInsn(Opcodes.ICONST_3);
			run.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
			store(run, 0, mv -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;"));
			store(run, 1, mv -> mv.visitLdcInsn(Type.getType("Ljava/lang/String;")));
			store(run, 2, mv -> mv.visitLdcInsn(Type.getType("Ljava/lang/Object;")));
			run.visitMethodInsn(Opcodes.INVOKESTATIC, TINKERERS, "enumBuilder", "(Ljava/lang/String;[Ljava/lang/Class;)L" + ADDER + ";", false);
			run.visitLdcInsn("GIVEN");
			run.visitInsn(Opcodes.ICONST_3);
			run.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
			store(run, 0, mv -> {
				mv.visitIntInsn(Opcodes.BIPUSH, 42);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			});
			store(run, 1, mv -> mv.visitLdcInsn("text"));
			store(run, 2, mv -> mv.visitInsn(Opcodes.ACONST_NULL));
			run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "addEnum", "(Ljava/lang/String;[Ljava/lang/Object;)L" + ADDER + ";", false);
			run.visitLdcInsn("SUPPLIED");
			lambda(run, STACKS);
			run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "addEnum", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + ADDER + ";", false);
			run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "build", "()V", false);
		});
		EarlyRisers.Read read = EarlyRisers.read("com.cooking.Riser", nodes(Map.of("com/cooking/Riser", riser, STACKS, TestEarlyRisers.stacks(STACKS, STACK))),
				false, UnaryOperator.identity());
		assertNull(read.unread());
		List<Type> parameters = List.of(Type.INT_TYPE, Type.getType("Ljava/lang/String;"), Type.getType("Ljava/lang/Object;"));
		assertEquals(List.of(new EarlyRisers.Addition("com/game/Kind", parameters, "GIVEN", new EarlyRisers.Given(java.util.Arrays.asList(42, "text", null))),
				new EarlyRisers.Addition("com/game/Kind", parameters, "SUPPLIED", new EarlyRisers.Supplied(STACKS, "lambda$stacks$0", "()[Ljava/lang/Object;",
						false, false))), read.additions());
	}

	/** What isn't read leaves the early riser unread, saying why, and adds nothing. */
	@Test
	void anEarlyRiserDoingAnythingElseIsUnreadWithItsReason() {
		Map<String, Consumer<MethodVisitor>> risers = new LinkedHashMap<>();
		risers.put("it uses Fabric ASM's ClassTinkerers.addTransformation, which Fabricated doesn't run", run -> {
			run.visitLdcInsn("net.minecraft.class_1");
			run.visitInsn(Opcodes.ACONST_NULL);
			run.visitMethodInsn(Opcodes.INVOKESTATIC, TINKERERS, "addTransformation", "(Ljava/lang/String;Ljava/util/function/Consumer;)V", false);
		});
		risers.put("it calls com.cooking.Other.prepare", run -> run.visitMethodInsn(Opcodes.INVOKESTATIC, "com/cooking/Other", "prepare", "()V", false));
		risers.put("it gives EXTRA 1 argument(s) for a constructor taking 0", run -> {
			run.visitLdcInsn("com.game.Kind");
			run.visitMethodInsn(Opcodes.INVOKESTATIC, TINKERERS, "enumBuilder", "(Ljava/lang/String;)L" + ADDER + ";", false);
			run.visitLdcInsn("EXTRA");
			run.visitInsn(Opcodes.ICONST_1);
			run.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
			store(run, 0, mv -> mv.visitLdcInsn("one too many"));
			run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "addEnum", "(Ljava/lang/String;[Ljava/lang/Object;)L" + ADDER + ";", false);
			run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ADDER, "build", "()V", false);
		});
		risers.put("it runs too long to read (a loop)", run -> {
			Label loop = new Label();
			run.visitLabel(loop);
			run.visitJumpInsn(Opcodes.GOTO, loop);
		});
		risers.put("it reads net.minecraft.class_2.FIELD", run -> {
			run.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/class_2", "FIELD", "Ljava/lang/Object;");
			run.visitInsn(Opcodes.POP);
		});
		for (Map.Entry<String, Consumer<MethodVisitor>> e : risers.entrySet()) {
			EarlyRisers.Read read = EarlyRisers.read("com.cooking.Riser", nodes(Map.of("com/cooking/Riser", TestEarlyRisers.riser("com/cooking/Riser", e.getValue()))),
					true, UnaryOperator.identity());
			assertEquals(e.getKey(), read.unread());
			assertEquals(List.of(), read.additions());
		}
		assertEquals("its class com.cooking.Missing isn't in the mod", EarlyRisers.read("com.cooking.Missing", name -> null, true, UnaryOperator.identity()).unread());
	}

	/**
	 * The generated enum mixin, its helper and the supplier's class made reachable load and run as the JVM would run
	 * them merged into the enum: each constant is made from its arguments, the supplied ones from the supplier the mod
	 * gives, run once per constant, its array checked as Fabric ASM checks it.
	 */
	@Test
	void theGeneratedMixinMakesItsConstantsWithTheirSuppliedArguments() throws Exception {
		String mixin = "com/cooking/fabricatedearlyrisers/KindAdditions";
		String helper = STACKS + "$FabricatedArguments";
		String element = "com/game/Stack";
		Type parameter = Type.getType("[L" + element + ";");
		EarlyRisers.Addition given = new EarlyRisers.Addition("com/game/Kind", List.of(Type.INT_TYPE, Type.getType("Ljava/lang/Object;"),
				Type.getType("Ljava/lang/Class;"), Type.getType("Ljava/lang/String;")), "GIVEN", new EarlyRisers.Given(java.util.Arrays.asList(7, 3L,
						Type.getType("Ljava/lang/String;"), null)));
		EarlyRisers.Addition factory = new EarlyRisers.Addition("com/game/Kind", List.of(parameter), "FACTORY",
				new EarlyRisers.Supplied(STACKS, "stacks", "()Ljava/util/function/Supplier;", true, false));
		EarlyRisers.Addition lambda = new EarlyRisers.Addition("com/game/Kind", List.of(parameter), "LAMBDA",
				new EarlyRisers.Supplied(STACKS, "lambda$stacks$0", "()[Ljava/lang/Object;", false, false));
		Map<EarlyRisers.Addition, String> helpers = new LinkedHashMap<>();
		helpers.put(factory, helper);
		helpers.put(lambda, helper);

		byte[] stacks = EarlyRisers.withReachableSuppliers(TestEarlyRisers.stacks(STACKS, element), Set.of("stacks()Ljava/util/function/Supplier;",
				"lambda$stacks$0()[Ljava/lang/Object;"));
		assertNotNull(stacks);
		ClassNode reachable = new ClassNode();
		new ClassReader(stacks).accept(reachable, ClassReader.SKIP_CODE);
		for (MethodNode m : reachable.methods) assertEquals(0, m.access & Opcodes.ACC_PRIVATE, m.name + " is no longer private");
		assertNull(EarlyRisers.withReachableSuppliers(stacks, Set.of("stacks()Ljava/util/function/Supplier;")), "nothing left to change");

		Map<String, byte[]> classes = new HashMap<>();
		classes.put(mixin, EarlyRisers.mixinClass(mixin, "com/game/Kind", List.of(given, factory, lambda), helpers));
		classes.put(helper, EarlyRisers.helperClass(helper, List.of(factory, lambda)));
		classes.put(STACKS, stacks);
		classes.put(element, TestJars.emptyClass(element, "java/lang/Object"));
		ClassNode node = new ClassNode();
		new ClassReader(classes.get(mixin)).accept(node, 0);
		assertTrue(node.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")
				&& a.values.get(1).equals(List.of(Type.getObjectType("com/game/Kind")))), "an enum mixin into the enum");
		assertTrue((node.access & Opcodes.ACC_ENUM) != 0);

		ClassLoader loader = loader(classes);
		Class<?> kind = Class.forName(mixin.replace('/', '.'), true, loader);
		// Merged into the enum, whose values() it has none of: its constants by their fields, then its values array.
		for (String constant : List.of("GIVEN", "FACTORY", "LAMBDA")) assertEquals(constant, ((Enum<?>) kind.getField(constant).get(null)).name());
		Field values = kind.getDeclaredField("$VALUES");
		values.setAccessible(true);
		assertEquals(List.of(0, 1, 2), java.util.Arrays.stream((Object[]) values.get(null)).map(c -> ((Enum<?>) c).ordinal()).toList());
		Class<?> arguments = loader.loadClass(helper.replace('/', '.'));
		Method first = arguments.getMethod(EarlyRisers.ARGUMENT + "$0", int.class);
		Object array = first.invoke(null, 0);
		assertInstanceOf(loader.loadClass(element.replace('/', '.')).arrayType(), array);
		assertEquals(2, ((Object[]) array).length);
		assertNotSame(array, first.invoke(null, 0), "the supplier's array is forgotten after its last argument");
		Field cache = arguments.getDeclaredField("arguments$0");
		cache.setAccessible(true);
		assertNull(cache.get(null));
		assertEquals(2, ((Object[]) arguments.getMethod(EarlyRisers.ARGUMENT + "$1", int.class).invoke(null, 0)).length);
	}

	/** A supplier giving the wrong number of arguments fails as Fabric ASM's does, naming the constant. */
	@Test
	void aSupplierGivingTheWrongNumberOfArgumentsFails() throws Exception {
		String helper = STACKS + "$FabricatedArguments";
		EarlyRisers.Supplied supplied = new EarlyRisers.Supplied(STACKS, "lambda$stacks$0", "()[Ljava/lang/Object;", false, false);
		EarlyRisers.Addition two = new EarlyRisers.Addition("com/game/Kind", List.of(Type.getType("Ljava/lang/Object;"), Type.getType("Ljava/lang/Object;")),
				"TWO", supplied);
		EarlyRisers.Addition none = new EarlyRisers.Addition("com/game/Kind", List.of(), "NONE", supplied);
		Map<String, byte[]> classes = new HashMap<>();
		classes.put(helper, EarlyRisers.helperClass(helper, List.of(two, none)));
		classes.put(STACKS, EarlyRisers.withReachableSuppliers(TestEarlyRisers.stacks(STACKS, "java/lang/Object"), Set.of("lambda$stacks$0()[Ljava/lang/Object;")));
		Class<?> arguments = loader(classes).loadClass(helper.replace('/', '.'));
		InvocationTargetException wrong = assertThrows(InvocationTargetException.class,
				() -> arguments.getMethod(EarlyRisers.ARGUMENT + "$0", int.class).invoke(null, 0));
		assertInstanceOf(IllegalArgumentException.class, wrong.getCause());
		assertEquals("Fabricated: Fabric ASM's supplier of the arguments of com.game.Kind.TWO gives no array of 2 argument(s)", wrong.getCause().getMessage());
		InvocationTargetException checked = assertThrows(InvocationTargetException.class,
				() -> arguments.getMethod(EarlyRisers.ARGUMENT + "$1", int.class).invoke(null, -1));
		assertInstanceOf(IllegalArgumentException.class, checked.getCause(), "-1 checks a constant taking no arguments");
		assertArrayEquals(new String[] {"arguments$0", "arguments$1"}, java.util.Arrays.stream(arguments.getDeclaredFields()).map(Field::getName).sorted()
				.toArray(String[]::new));
	}

	private static void store(MethodVisitor run, int index, Consumer<MethodVisitor> value) {
		run.visitInsn(Opcodes.DUP);
		run.visitInsn(Opcodes.ICONST_0 + index);
		value.accept(run);
		run.visitInsn(Opcodes.AASTORE);
	}

	/** {@code () -> owner.lambda$stacks$0()} as a supplier, as javac compiles a lambda. */
	private static void lambda(MethodVisitor mv, String owner) {
		mv.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;", new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory",
				"metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				Type.getType("()Ljava/lang/Object;"), new Handle(Opcodes.H_INVOKESTATIC, owner, "lambda$stacks$0", "()[Ljava/lang/Object;", false),
				Type.getType("()[Ljava/lang/Object;"));
	}

	private static Function<String, ClassNode> nodes(Map<String, byte[]> classes) {
		return name -> {
			byte[] data = classes.get(name);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, 0);
			return node;
		};
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(EarlyRisersTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] bytes = classes.get(name.replace('.', '/'));
				if (bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
	}
}
