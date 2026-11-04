package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * A mod loader class whose instance methods the platform made static, the class an interface and its static accessor
 * private, that a Fabric mod still calls through that accessor (PLUGIN-1): Forge 64 turned FML's {@code LoadingModList}
 * so, and MixinConstraints, which Flashback nests, calls {@code LoadingModList.get().getModFileById(id)} in its Forge
 * abstraction, built for Forge 47. That call can't link on Forge 64 to 66 ({@code IncompatibleClassChangeError}), and
 * MixinConstraints' mixin plugin stopped the game as Mixin selected Flashback's config.
 *
 * <p>A mod's call of such a method on what the accessor returns, with only its arguments pushed in between, becomes a
 * call of the static method, without the accessor's. Only code that can't link changes: a static call of an interface's
 * method by a method reference, and a virtual call of an interface. The loader's packages are the only ones looked at;
 * where the receiver comes from anywhere else, the call is left as it is.
 */
final class PlatformStatics {
	private static final List<String> LOADER_PACKAGES = List.of("net/minecraftforge/fml/", "net/neoforged/fml/");

	private final ClassBytesSource platform;
	private final Map<String, Optional<ClassNode>> read = new ConcurrentHashMap<>();

	PlatformStatics(ClassBytesSource platform) {
		this.platform = platform;
	}

	/** {@code classFile} with such calls made static, or null when it makes none. */
	byte[] apply(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		if (LOADER_PACKAGES.stream().noneMatch(text::contains)) return null;
		ClassNode node = new ClassNode();
		new ClassReader(classFile).accept(node, 0);
		boolean changed = false;
		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode accessor && accessor.getOpcode() == Opcodes.INVOKESTATIC && !accessor.itf) {
					changed |= callStatic(method, accessor);
				}
			}
		}
		if (!changed) return null;
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * Where {@code accessor} is a loader interface's private static {@code ()} method returning it, and its result is the
	 * receiver of the next call, of a static method of that interface, removes it and makes that call static.
	 */
	private boolean callStatic(MethodNode method, MethodInsnNode accessor) {
		if (LOADER_PACKAGES.stream().noneMatch(accessor.owner::startsWith) || !accessor.desc.equals("()L" + accessor.owner + ";")) return false;
		ClassNode owner = platformClass(accessor.owner);
		if (owner == null || (owner.access & Opcodes.ACC_INTERFACE) == 0 || !declares(owner, accessor.name, accessor.desc, Opcodes.ACC_PRIVATE)) return false;
		int pushed = 0;
		for (AbstractInsnNode insn = accessor.getNext(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FrameNode) return false;
			if (insn.getOpcode() < 0) continue; // labels, line numbers
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(accessor.owner) || call.getOpcode() != Opcodes.INVOKEVIRTUAL && call.getOpcode() != Opcodes.INVOKEINTERFACE
						|| pushed != (Type.getArgumentsAndReturnSizes(call.desc) >> 2) - 1 || !declares(owner, call.name, call.desc, 0)) return false;
				method.instructions.remove(accessor);
				method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, call.owner, call.name, call.desc, true));
				return true;
			}
			int size = pushes(insn);
			if (size == 0) return false;
			pushed += size;
		}
		return false;
	}

	/** The stack size an instruction that only pushes a value pushes; 0 for any other. */
	private static int pushes(AbstractInsnNode insn) {
		int op = insn.getOpcode();
		if (op == Opcodes.LLOAD || op == Opcodes.DLOAD || op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1 || op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) return 2;
		if (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD || op >= Opcodes.ACONST_NULL && op <= Opcodes.FCONST_2 || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) return 1;
		if (insn instanceof LdcInsnNode ldc) return ldc.cst instanceof Long || ldc.cst instanceof Double ? 2 : 1;
		if (op == Opcodes.GETSTATIC) return Type.getType(((FieldInsnNode) insn).desc).getSize();
		return 0;
	}

	/** Whether {@code owner} declares the method as a static one, with all of {@code access} (as {@code ACC_PRIVATE}) or none, where 0. */
	private static boolean declares(ClassNode owner, String name, String desc, int access) {
		for (MethodNode m : owner.methods) {
			if (!m.name.equals(name) || !m.desc.equals(desc) || (m.access & Opcodes.ACC_STATIC) == 0) continue;
			return access == 0 ? (m.access & Opcodes.ACC_PRIVATE) == 0 : (m.access & access) == access;
		}
		return false;
	}

	private ClassNode platformClass(String name) {
		return read.computeIfAbsent(name, n -> Optional.ofNullable(platform.readNode(n, ClassReader.SKIP_CODE))).orElse(null);
	}
}
