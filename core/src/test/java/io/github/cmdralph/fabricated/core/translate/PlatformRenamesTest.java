package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

class PlatformRenamesTest {
	private static final String TYPE = "net/neoforged/fml/config/ModConfig$Type";
	private static final ClassIndex NO_GAME = new ClassIndex(ClassBytesSource.EMPTY);

	private static byte[] enumWith(String... constants) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ENUM, TYPE, null, "java/lang/Enum", null);
		for (String c : constants) cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM, c, "L" + TYPE + ";", null, null).visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mod class reading {@code ModConfig.Type.SERVER}. */
	private static byte[] user() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Config", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "type", "()Ljava/lang/Object;", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, TYPE, "SERVER", "L" + TYPE + ";");
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void renamesTheConfigTypesFml12Renamed() {
		PlatformRenames renames = PlatformRenames.of(Map.of(TYPE, enumWith("LOCAL", "CLIENT", "SYNCED", "STARTUP"))::get, NO_GAME, true);
		ClassNode node = new ClassNode();
		new ClassReader(renames.apply(user())).accept(node, 0);
		FieldInsnNode read = (FieldInsnNode) node.methods.getFirst().instructions.getFirst();
		assertEquals("SYNCED", read.name);
	}

	@Test
	void leavesModsAloneWhereTheOldNamesExist() {
		PlatformRenames renames = PlatformRenames.of(Map.of(TYPE, enumWith("COMMON", "CLIENT", "SERVER", "STARTUP"))::get, NO_GAME, true);
		assertNull(renames.apply(user()));
	}

	private static final String SCREEN = "net/minecraft/client/gui/screens/Screen";
	private static final String CONFIG_SCREEN = "net/neoforged/neoforge/client/gui/ConfigurationScreen";
	private static final String CONTAINER = "net/neoforged/fml/ModContainer";
	private static final String FILTER = "net/neoforged/neoforge/client/gui/ConfigurationScreen$ConfigurationSectionScreen$Filter";

	/** Stand-ins: NeoForge's constructors take (container, parent[, filter]) and store them; the container holds an id. */
	private static Map<String, byte[]> platform() {
		ClassWriter screen = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		screen.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, SCREEN, null, "java/lang/Object", null);
		constructor(screen, "java/lang/Object", "()V");
		ClassWriter filter = new ClassWriter(0);
		filter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, FILTER, null, "java/lang/Object", null);
		ClassWriter container = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		container.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CONTAINER, null, "java/lang/Object", null);
		container.visitField(Opcodes.ACC_PUBLIC, "id", "Ljava/lang/String;", null, null).visitEnd();
		MethodVisitor init = container.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitFieldInsn(Opcodes.PUTFIELD, CONTAINER, "id", "Ljava/lang/String;");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		ClassWriter bridge = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		bridge.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortBridge", null, "java/lang/Object", null);
		MethodVisitor of = bridge.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "modContainer", "(Ljava/lang/String;)L" + CONTAINER + ";", null, null);
		of.visitCode();
		of.visitTypeInsn(Opcodes.NEW, CONTAINER);
		of.visitInsn(Opcodes.DUP);
		of.visitVarInsn(Opcodes.ALOAD, 0);
		of.visitMethodInsn(Opcodes.INVOKESPECIAL, CONTAINER, "<init>", "(Ljava/lang/String;)V", false);
		of.visitInsn(Opcodes.ARETURN);
		of.visitMaxs(0, 0);
		of.visitEnd();
		ClassWriter config = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		config.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CONFIG_SCREEN, null, SCREEN, null);
		config.visitField(Opcodes.ACC_PUBLIC, "container", "L" + CONTAINER + ";", null, null).visitEnd();
		config.visitField(Opcodes.ACC_PUBLIC, "parent", "L" + SCREEN + ";", null, null).visitEnd();
		config.visitField(Opcodes.ACC_PUBLIC, "filter", "Ljava/lang/Object;", null, null).visitEnd();
		for (boolean withFilter : new boolean[] {false, true}) {
			MethodVisitor c = config.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + CONTAINER + ";L" + SCREEN + ";" + (withFilter ? "L" + FILTER + ";" : "") + ")V", null, null);
			c.visitCode();
			c.visitVarInsn(Opcodes.ALOAD, 0);
			c.visitMethodInsn(Opcodes.INVOKESPECIAL, SCREEN, "<init>", "()V", false);
			c.visitVarInsn(Opcodes.ALOAD, 0);
			c.visitVarInsn(Opcodes.ALOAD, 1);
			c.visitFieldInsn(Opcodes.PUTFIELD, CONFIG_SCREEN, "container", "L" + CONTAINER + ";");
			c.visitVarInsn(Opcodes.ALOAD, 0);
			c.visitVarInsn(Opcodes.ALOAD, 2);
			c.visitFieldInsn(Opcodes.PUTFIELD, CONFIG_SCREEN, "parent", "L" + SCREEN + ";");
			if (withFilter) {
				c.visitVarInsn(Opcodes.ALOAD, 0);
				c.visitVarInsn(Opcodes.ALOAD, 3);
				c.visitFieldInsn(Opcodes.PUTFIELD, CONFIG_SCREEN, "filter", "Ljava/lang/Object;");
			}
			c.visitInsn(Opcodes.RETURN);
			c.visitMaxs(0, 0);
			c.visitEnd();
		}
		ClassWriter screens = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		screens.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortScreens", null, "java/lang/Object", null);
		MethodVisitor factory = screens.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "configurationScreen",
				"(Ljava/lang/String;L" + SCREEN + ";)L" + CONFIG_SCREEN + ";", null, null);
		factory.visitCode();
		factory.visitTypeInsn(Opcodes.NEW, CONFIG_SCREEN);
		factory.visitInsn(Opcodes.DUP);
		factory.visitVarInsn(Opcodes.ALOAD, 0);
		factory.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortBridge", "modContainer",
				"(Ljava/lang/String;)L" + CONTAINER + ";", false);
		factory.visitVarInsn(Opcodes.ALOAD, 1);
		factory.visitMethodInsn(Opcodes.INVOKESPECIAL, CONFIG_SCREEN, "<init>", "(L" + CONTAINER + ";L" + SCREEN + ";)V", false);
		factory.visitInsn(Opcodes.ARETURN);
		factory.visitMaxs(0, 0);
		factory.visitEnd();
		return Map.of(SCREEN, screen.toByteArray(), FILTER, filter.toByteArray(), CONTAINER, container.toByteArray(),
				"io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortBridge", bridge.toByteArray(), CONFIG_SCREEN, config.toByteArray(),
				"io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortScreens", screens.toByteArray());
	}

	private static void constructor(ClassWriter cw, String superName, String desc) {
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
	}

	/** A mod class opening Forge Config API Port's screen: {@code new ConfigurationScreen("mod_id", parent[, filter])}. */
	private static byte[] screenUser() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Screens", null, "java/lang/Object", null);
		for (boolean withFilter : new boolean[] {false, true}) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, withFilter ? "filtered" : "plain",
					"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			mv.visitCode();
			mv.visitTypeInsn(Opcodes.NEW, CONFIG_SCREEN);
			mv.visitInsn(Opcodes.DUP);
			mv.visitLdcInsn("mod_id");
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitTypeInsn(Opcodes.CHECKCAST, SCREEN);
			if (withFilter) {
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitTypeInsn(Opcodes.CHECKCAST, FILTER);
			}
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, CONFIG_SCREEN, "<init>",
					"(Ljava/lang/String;L" + SCREEN + ";" + (withFilter ? "L" + FILTER + ";" : "") + ")V", false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		// ConfigurationScreen::new as the port's (modId, parent) factory, applied
		MethodVisitor ref = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "referenced",
				"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
		ref.visitCode();
		ref.visitInvokeDynamicInsn("apply", "()Ljava/util/function/BiFunction;", new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC,
				"java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				org.objectweb.asm.Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
				new org.objectweb.asm.Handle(Opcodes.H_NEWINVOKESPECIAL, CONFIG_SCREEN, "<init>", "(Ljava/lang/String;L" + SCREEN + ";)V", false),
				org.objectweb.asm.Type.getType("(Ljava/lang/String;L" + SCREEN + ";)L" + SCREEN + ";"));
		ref.visitLdcInsn("mod_id");
		ref.visitVarInsn(Opcodes.ALOAD, 0);
		ref.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/BiFunction", "apply",
				"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
		ref.visitInsn(Opcodes.ARETURN);
		ref.visitMaxs(0, 0);
		ref.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** On Forge the port's own jar loads, with its own screen taking the id, and Fabricated's factories aren't there (REPL-1). */
	@Test
	void forgeConfigApiPortScreensAreLeftAloneWhereThePlatformDoesNotProvideThePort() {
		assertNull(PlatformRenames.of(Map.<String, byte[]>of()::get, NO_GAME, false).apply(screenUser()));
	}

	@Test
	void forgeConfigApiPortScreensGetTheModsContainer() throws Exception {
		byte[] adapted = PlatformRenames.of(Map.<String, byte[]>of()::get, NO_GAME, true).apply(screenUser());
		Map<String, byte[]> classes = new java.util.HashMap<>(platform());
		classes.put("com/example/Screens", adapted);
		ClassLoader loader = new ClassLoader(null) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
		Class<?> screens = loader.loadClass("com.example.Screens");
		Object parent = loader.loadClass(SCREEN.replace('/', '.')).getConstructor().newInstance();
		Object filter = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {loader.loadClass(FILTER.replace('/', '.'))}, (p, m, a) -> null);
		for (String method : new String[] {"plain", "filtered", "referenced"}) {
			Object screen = screens.getMethod(method, Object.class, Object.class).invoke(null, parent, filter);
			Object container = screen.getClass().getField("container").get(screen);
			assertEquals("mod_id", container.getClass().getField("id").get(container));
			assertSame(parent, screen.getClass().getField("parent").get(screen));
			if (method.equals("filtered")) assertSame(filter, screen.getClass().getField("filter").get(screen));
		}
	}

	private static final String TAB = "net/minecraft/world/item/CreativeModeTab";
	private static final String BUILDER = TAB + "$Builder";
	private static final String ROW = TAB + "$Row";
	private static final String TAB_TYPE = TAB + "$Type";
	private static final String COMPONENT = "net/minecraft/network/chat/Component";
	private static final String ITEMS = TAB + "$DisplayItemsGenerator";
	private static final String VANILLA = "(L" + ROW + ";IL" + TAB_TYPE + ";L" + COMPONENT + ";Ljava/util/function/Supplier;L" + ITEMS + ";)";
	private static final String[][] BUILDER_FIELDS = {{"row", "L" + ROW + ";"}, {"column", "I"}, {"type", "L" + TAB_TYPE + ";"},
			{"name", "L" + COMPONENT + ";"}, {"icon", "Ljava/util/function/Supplier;"}, {"items", "L" + ITEMS + ";"}};

	/**
	 * Stand-ins: NeoForge's creative tab (its protected constructor keeps the builder, and vanilla's constructor only
	 * when {@code vanilla}), the builder holding vanilla's arguments, and Fabricated's factory filling it.
	 */
	private static Map<String, byte[]> game(boolean vanilla) {
		Map<String, byte[]> classes = new java.util.HashMap<>();
		for (String name : new String[] {ROW, TAB_TYPE}) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
			constructor(cw, "java/lang/Object", "()V");
			classes.put(name, cw.toByteArray());
		}
		for (String name : new String[] {COMPONENT, ITEMS}) {
			ClassWriter cw = new ClassWriter(0);
			cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
			classes.put(name, cw.toByteArray());
		}
		ClassWriter builder = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		builder.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, BUILDER, null, "java/lang/Object", null);
		for (String[] field : BUILDER_FIELDS) builder.visitField(Opcodes.ACC_PUBLIC, field[0], field[1], null, null).visitEnd();
		constructor(builder, "java/lang/Object", "()V");
		classes.put(BUILDER, builder.toByteArray());
		ClassWriter tab = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		tab.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TAB, null, "java/lang/Object", null);
		tab.visitField(Opcodes.ACC_PUBLIC, "builder", "L" + BUILDER + ";", null, null).visitEnd();
		MethodVisitor init = tab.visitMethod(Opcodes.ACC_PROTECTED, "<init>", "(L" + BUILDER + ";)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitFieldInsn(Opcodes.PUTFIELD, TAB, "builder", "L" + BUILDER + ";");
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		if (vanilla) constructor(tab, "java/lang/Object", VANILLA + "V");
		classes.put(TAB, tab.toByteArray());
		ClassWriter factory = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		factory.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "io/github/cmdralph/fabricated/neoforge/item/VanillaCreativeTabs", null, "java/lang/Object", null);
		MethodVisitor make = factory.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "builder", VANILLA + "L" + BUILDER + ";", null, null);
		make.visitCode();
		make.visitTypeInsn(Opcodes.NEW, BUILDER);
		make.visitInsn(Opcodes.DUP);
		make.visitMethodInsn(Opcodes.INVOKESPECIAL, BUILDER, "<init>", "()V", false);
		for (int i = 0; i < BUILDER_FIELDS.length; i++) {
			make.visitInsn(Opcodes.DUP);
			make.visitVarInsn(i == 1 ? Opcodes.ILOAD : Opcodes.ALOAD, i);
			make.visitFieldInsn(Opcodes.PUTFIELD, BUILDER, BUILDER_FIELDS[i][0], BUILDER_FIELDS[i][1]);
		}
		make.visitInsn(Opcodes.ARETURN);
		make.visitMaxs(0, 0);
		make.visitEnd();
		classes.put("io/github/cmdralph/fabricated/neoforge/item/VanillaCreativeTabs", factory.toByteArray());
		return classes;
	}

	/** A mod's tab (Polymer's for a server's item groups): {@code super(row, 3, type, name, icon, items)}. */
	private static byte[] tabSubclass() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Tab", null, TAB, null);
		MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + ROW + ";L" + TAB_TYPE + ";L" + COMPONENT
				+ ";Ljava/util/function/Supplier;L" + ITEMS + ";)V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitVarInsn(Opcodes.ALOAD, 1);
		init.visitInsn(Opcodes.ICONST_3);
		for (int i = 2; i <= 5; i++) init.visitVarInsn(Opcodes.ALOAD, i);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, TAB, "<init>", VANILLA + "V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(0, 0);
		init.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Test
	void aCreativeTabSubclassCallingVanillasConstructorGetsNeoForgesWithABuilder() throws Exception {
		Map<String, byte[]> classes = game(false);
		byte[] adapted = PlatformRenames.of(ClassBytesSource.EMPTY, new ClassIndex(classes::get), true).apply(tabSubclass());
		assertNotNull(adapted);
		classes.put("com/example/Tab", adapted);
		ClassLoader loader = new ClassLoader(null) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
		Class<?> row = loader.loadClass(ROW.replace('/', '.'));
		Class<?> type = loader.loadClass(TAB_TYPE.replace('/', '.'));
		Class<?> component = loader.loadClass(COMPONENT.replace('/', '.'));
		Class<?> items = loader.loadClass(ITEMS.replace('/', '.'));
		Object[] args = {row.getConstructor().newInstance(), type.getConstructor().newInstance(),
				java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {component}, (p, m, a) -> null),
				(java.util.function.Supplier<Object>) () -> "icon",
				java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {items}, (p, m, a) -> null)};
		Object tab = loader.loadClass("com.example.Tab").getConstructor(row, type, component, java.util.function.Supplier.class, items).newInstance(args);
		Object builder = tab.getClass().getField("builder").get(tab);
		assertSame(args[0], builder.getClass().getField("row").get(builder));
		assertEquals(3, builder.getClass().getField("column").get(builder));
		for (int i = 1; i < args.length; i++) assertSame(args[i], builder.getClass().getField(BUILDER_FIELDS[i + 1][0]).get(builder));
	}

	@Test
	void leavesCreativeTabsAloneWhereVanillasConstructorExists() {
		assertNull(PlatformRenames.of(ClassBytesSource.EMPTY, new ClassIndex(game(true)::get), true).apply(tabSubclass()));
	}
}
