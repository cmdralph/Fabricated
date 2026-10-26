package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class HiddenDefaultsTest {
	private static final String SELF = "()Lgame/Living;";

	private final Map<String, ClassNode> classes = new HashMap<>();

	/** A class (an interface when {@code itf}) with these methods, each {@code access name descriptor}. */
	private ClassNode add(String name, boolean itf, String superName, List<String> interfaces, Object... methods) {
		ClassNode node = new ClassNode();
		node.name = name;
		node.access = Opcodes.ACC_PUBLIC | (itf ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : 0);
		node.superName = superName;
		node.interfaces.addAll(interfaces);
		for (int i = 0; i < methods.length; i += 3) {
			node.methods.add(new MethodNode((int) methods[i], (String) methods[i + 1], (String) methods[i + 2], null, null));
		}
		classes.put(name, node);
		return node;
	}

	/** As NeoForge's ILivingEntityExtension and Forge's IForgeLivingEntity on LivingEntity, which a mob extends. */
	private void platform() {
		add("neo/LivingExtension", true, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, "self", SELF);
		add("game/Entity", false, "java/lang/Object", List.of());
		add("game/Living", false, "game/Entity", List.of("neo/LivingExtension"));
		add("game/Pig", false, "game/Living", List.of());
	}

	private static MethodNode annotated(MethodNode method, String desc) {
		method.invisibleAnnotations = List.of(new AnnotationNode(desc));
		return method;
	}

	private Map<String, String> renames(ClassNode mixin, String... targets) {
		return HiddenDefaults.renames(mixin, List.of(targets), classes::get);
	}

	@Test
	void aPrivateMethodNamedAsAnInheritedDefaultIsRenamed() {
		platform();
		// fabric-entity-events-v1's effect LivingEntityMixin: @Unique private LivingEntity self().
		ClassNode mixin = add("mod/LivingEntityMixin", false, "game/Entity", List.of(), Opcodes.ACC_PRIVATE, "self", SELF,
				Opcodes.ACC_PRIVATE, "isClient", "()Z");
		annotated(mixin.methods.get(0), "Lorg/spongepowered/asm/mixin/Unique;");
		Map<String, String> renames = renames(mixin, "game/Living");
		assertEquals(Map.of("self" + SELF, "self$fabricated$" + Integer.toHexString("mod/LivingEntityMixin".hashCode())), renames);

		// Into a class whose superclass has the interface; an unannotated private method too.
		ClassNode pigMixin = add("mod/PigMixin", false, "java/lang/Object", List.of(), Opcodes.ACC_PRIVATE, "self", SELF);
		assertEquals(List.of("self" + SELF), List.copyOf(renames(pigMixin, "game/Pig").keySet()));
	}

	@Test
	void aDefaultTheMixinItselfAddsCounts() {
		platform();
		add("mod/Duck", true, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC, "box", "()I");
		ClassNode mixin = add("mod/EntityMixin", false, "java/lang/Object", List.of("mod/Duck"), Opcodes.ACC_PRIVATE, "box", "()I");
		assertEquals(List.of("box()I"), List.copyOf(renames(mixin, "game/Entity").keySet()));
	}

	@Test
	void nothingHiddenIsRenamed() {
		platform();
		ClassNode mixin = add("mod/LivingEntityMixin", false, "java/lang/Object", List.of(),
				Opcodes.ACC_PUBLIC, "self", SELF, // overrides the default, as the mod means it to
				Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "self", SELF, // the JVM passes static methods over
				Opcodes.ACC_PRIVATE, "self", "()Lgame/Entity;"); // another descriptor is another method
		assertEquals(Map.of(), renames(mixin, "game/Living"));

		ClassNode shadow = add("mod/ShadowMixin", false, "java/lang/Object", List.of(), Opcodes.ACC_PRIVATE, "self", SELF);
		annotated(shadow.methods.get(0), "Lorg/spongepowered/asm/mixin/Shadow;");
		assertEquals(Map.of(), renames(shadow, "game/Living"), "a @Shadow is the target's own method");
		ClassNode handler = add("mod/HandlerMixin", false, "java/lang/Object", List.of(), Opcodes.ACC_PRIVATE, "self", SELF);
		annotated(handler.methods.get(0), "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");
		assertEquals(Map.of(), renames(handler, "game/Living"), "Mixin names an injector's handler itself");

		ClassNode plain = add("mod/PlainMixin", false, "java/lang/Object", List.of(), Opcodes.ACC_PRIVATE, "self", SELF);
		assertEquals(Map.of(), renames(plain, "game/Absent"), "an absent target");
		assertEquals(Map.of(), renames(plain, "neo/LivingExtension"), "an interface target");
		add("game/Living", false, "game/Entity", List.of("neo/LivingExtension"), Opcodes.ACC_PUBLIC, "self", SELF);
		assertEquals(Map.of(), renames(plain, "game/Pig"), "a superclass declares the method, which the JVM finds before the default");
		add("game/Living", false, "game/Entity", List.of("neo/LivingExtension"), Opcodes.ACC_PRIVATE, "self", SELF);
		assertEquals(Map.of(), renames(plain, "game/Living"), "the target declares it (Mixin renames a @Unique one itself)");
		add("game/Living", false, "game/Entity", List.of("neo/LivingExtension"));
		add("neo/LivingExtension", true, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "self", SELF);
		assertEquals(Map.of(), renames(plain, "game/Living"), "an abstract interface method is no default");
		add("neo/LivingExtension", true, "java/lang/Object", List.of(), Opcodes.ACC_PRIVATE, "self", SELF);
		assertEquals(Map.of(), renames(plain, "game/Living"), "nor is a private one");
	}

	@Test
	void theRenamedMethodsReferencesFollow() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "mod/M", null, "java/lang/Object", null);
		MethodVisitor self = cw.visitMethod(Opcodes.ACC_PRIVATE, "self", SELF, null, null);
		self.visitCode();
		self.visitInsn(Opcodes.ACONST_NULL);
		self.visitInsn(Opcodes.ARETURN);
		self.visitMaxs(0, 0);
		self.visitEnd();
		MethodVisitor use = cw.visitMethod(Opcodes.ACC_PRIVATE, "use", "()Ljava/lang/Object;", null, null);
		use.visitCode();
		use.visitVarInsn(Opcodes.ALOAD, 0);
		use.visitMethodInsn(Opcodes.INVOKESPECIAL, "mod/M", "self", SELF, false);
		use.visitInsn(Opcodes.POP);
		use.visitLdcInsn(new Handle(Opcodes.H_INVOKESPECIAL, "mod/M", "self", SELF, false));
		use.visitInsn(Opcodes.ARETURN);
		use.visitMaxs(0, 0);
		use.visitEnd();
		cw.visitEnd();
		Map<String, String> renames = Map.of("self" + SELF, "self$fabricated$1");

		ClassNode out = new ClassNode();
		new ClassReader(HiddenDefaults.rewrite(cw.toByteArray(), "mod/M", renames)).accept(out, 0);
		assertTrue(out.methods.stream().anyMatch(m -> m.name.equals("self$fabricated$1")));
		assertTrue(out.methods.stream().noneMatch(m -> m.name.equals("self")));
		MethodNode rewritten = out.methods.stream().filter(m -> m.name.equals("use")).findFirst().orElseThrow();
		MethodInsnNode call = (MethodInsnNode) Arrays.stream(rewritten.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
				.findFirst().orElseThrow();
		assertEquals("self$fabricated$1", call.name);
		LdcInsnNode handle = (LdcInsnNode) Arrays.stream(rewritten.instructions.toArray()).filter(LdcInsnNode.class::isInstance)
				.findFirst().orElseThrow();
		assertEquals("self$fabricated$1", ((Handle) handle.cst).getName());

		assertNull(HiddenDefaults.rewrite(cw.toByteArray(), "mod/Other", renames), "another class's method of that name");
	}

	/** The JVM's behaviour the renaming is for, and that the renamed method no longer hides the default. */
	@Test
	void theDefaultIsReachableOnASubclassOnceRenamed() throws Throwable {
		byte[] itf = itf();
		byte[] target = target();
		byte[] sub = sub();
		assertThrows(AbstractMethodError.class, () -> callSelf(itf, target, sub), "HotSpot: the private method hides the default from subclasses");

		Map<String, String> renames = Map.of("self()Lp/Target;", "self$fabricated$1");
		Object instance = callSelf(itf, HiddenDefaults.rewrite(target, "p/Target", renames), sub);
		assertSame(Boolean.TRUE, instance);
	}

	private static byte[] itf() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "p/Ext", null, "java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "self", "()Lp/Target;", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitTypeInsn(Opcodes.CHECKCAST, "p/Target");
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** The target as Mixin leaves it: the mixin's private {@code self()} merged into it. */
	private static byte[] target() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "p/Target", null, "java/lang/Object", new String[] {"p/Ext"});
		constructor(cw, "java/lang/Object");
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "self", "()Lp/Target;", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] sub() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "p/Sub", null, "p/Target", null);
		constructor(cw, "p/Target");
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void constructor(ClassWriter cw, String superName) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/** Whether {@code Ext.self()} called on a new {@code Sub} returns it; throws what the call throws. */
	private static Object callSelf(byte[] itf, byte[] target, byte[] sub) throws Throwable {
		Map<String, byte[]> defined = Map.of("p.Ext", itf, "p.Target", target, "p.Sub", sub);
		ClassLoader loader = new ClassLoader(HiddenDefaultsTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = defined.get(name);
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
		Object instance = loader.loadClass("p.Sub").getConstructor().newInstance();
		Method self = loader.loadClass("p.Ext").getMethod("self");
		try {
			return self.invoke(instance) == instance;
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

}
