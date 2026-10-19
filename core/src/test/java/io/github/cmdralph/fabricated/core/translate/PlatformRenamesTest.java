package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class PlatformRenamesTest {
	private static final String TYPE = "net/neoforged/fml/config/ModConfig$Type";

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
		PlatformRenames renames = PlatformRenames.of(Map.of(TYPE, enumWith("LOCAL", "CLIENT", "SYNCED", "STARTUP"))::get);
		ClassNode node = new ClassNode();
		new ClassReader(renames.apply(user())).accept(node, 0);
		FieldInsnNode read = (FieldInsnNode) node.methods.getFirst().instructions.getFirst();
		assertEquals("SYNCED", read.name);
	}

	@Test
	void leavesModsAloneWhereTheOldNamesExist() {
		PlatformRenames renames = PlatformRenames.of(Map.of(TYPE, enumWith("COMMON", "CLIENT", "SERVER", "STARTUP"))::get);
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

	@Test
	void forgeConfigApiPortScreensGetTheModsContainer() throws Exception {
		byte[] adapted = PlatformRenames.of(Map.<String, byte[]>of()::get).apply(screenUser());
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
}
