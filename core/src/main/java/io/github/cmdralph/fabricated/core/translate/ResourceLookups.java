package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Points the lookups a Fabric mod's code makes as from an unnamed module, where Fabric runs mods, at Fabricated's
 * helpers, as NeoForge runs each mod in a module of its own:
 * <ul>
 *   <li>{@code Class.getResource} and {@code Class.getResourceAsStream} go to {@code ClassResources}, which also asks the
 *       class loader when the class's own module has no such file (RES-1). The stack takes the class and the name
 *       either way.</li>
 *   <li>{@code ResourceBundle.getBundle} with a {@code ResourceBundle.Control}, which the JDK refuses from a named module
 *       (LuckPerms loads its translations so), goes to {@code ResourceBundles}, which makes the same lookup (LOADER-3).
 *       The forms taking the caller's class loader get the calling class as one more argument.</li>
 * </ul>
 */
final class ResourceLookups {
	/** Fabricated's (in its library jar, which Fabric mods read). */
	static final String CLASS_RESOURCES = "io/github/cmdralph/fabricated/loader/ClassResources";
	static final String RESOURCE_BUNDLES = "io/github/cmdralph/fabricated/loader/ResourceBundles";
	private static final String STREAM = "(Ljava/lang/String;)Ljava/io/InputStream;";
	private static final String URL = "(Ljava/lang/String;)Ljava/net/URL;";
	private static final String BUNDLE = "Ljava/util/ResourceBundle;";
	private static final String CONTROL = "Ljava/util/ResourceBundle$Control;";
	/** {@code getBundle} with a control that looks bundles up with the caller's class loader, which the helper is given. */
	private static final Set<String> CALLER_BUNDLES = Set.of(
			"(Ljava/lang/String;" + CONTROL + ")" + BUNDLE,
			"(Ljava/lang/String;Ljava/util/Locale;" + CONTROL + ")" + BUNDLE);
	private static final String LOADER_BUNDLE = "(Ljava/lang/String;Ljava/util/Locale;Ljava/lang/ClassLoader;" + CONTROL + ")" + BUNDLE;

	private ResourceLookups() {
	}

	/** {@code classFile} with those calls pointed at Fabricated's helpers, or null when it makes none. */
	static byte[] apply(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		boolean resources = text.contains("getResource") && text.contains("java/lang/Class");
		boolean bundles = text.contains("getBundle") && text.contains("ResourceBundle$Control");
		if (!resources && !bundles) return null;
		boolean[] changed = {false};
		ClassReader reader = new ClassReader(classFile);
		ClassWriter writer = new ClassWriter(reader, 0);
		String className = reader.getClassName();
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					/** Whether the calling class was pushed for a call here, one more stack slot. */
					private boolean pushedCaller;

					@Override
					public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
						if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")
								&& (method.equals("getResourceAsStream") && desc.equals(STREAM) || method.equals("getResource") && desc.equals(URL))) {
							super.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_RESOURCES, method, "(Ljava/lang/Class;" + desc.substring(1), false);
							changed[0] = true;
							return;
						}
						if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/util/ResourceBundle") && method.equals("getBundle")) {
							if (CALLER_BUNDLES.contains(desc)) {
								super.visitLdcInsn(Type.getObjectType(className));
								super.visitMethodInsn(Opcodes.INVOKESTATIC, RESOURCE_BUNDLES, method, desc.replace(")", "Ljava/lang/Class;)"), false);
								pushedCaller = true;
								changed[0] = true;
								return;
							}
							if (desc.equals(LOADER_BUNDLE)) {
								super.visitMethodInsn(Opcodes.INVOKESTATIC, RESOURCE_BUNDLES, method, desc, false);
								changed[0] = true;
								return;
							}
						}
						super.visitMethodInsn(opcode, owner, method, desc, itf);
					}

					@Override
					public void visitMaxs(int maxStack, int maxLocals) {
						super.visitMaxs(pushedCaller ? maxStack + 1 : maxStack, maxLocals);
					}
				};
			}
		}, 0);
		return changed[0] ? writer.toByteArray() : null;
	}
}
