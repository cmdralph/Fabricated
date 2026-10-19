package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * Members NeoForge renamed that Fabric mods still reach by their old names, through the copies of NeoForge's API made
 * for Fabric: Forge Config API Port keeps FML's old config types ({@code COMMON}, {@code SERVER}), which FML 12 calls
 * {@code LOCAL} and {@code SYNCED}. A mod's references are renamed where the running platform has only the new name.
 *
 * <p>The port's {@code ConfigurationScreen} also takes the mod's id where NeoForge's takes its container (in every
 * NeoForge version supported): a mod's {@code new ConfigurationScreen(modId, parent, ...)} passes the container that
 * Fabricated finds for that id instead.
 */
final class PlatformRenames {
	private static final String CONFIGURATION_SCREEN = "net/neoforged/neoforge/client/gui/ConfigurationScreen";
	private static final String PORT_CONSTRUCTOR = "(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;";
	private static final String CONTAINER = "Lnet/neoforged/fml/ModContainer;";
	/** Fabricated's (in its in-game mod): the container of a Fabric mod id. */
	private static final String CONTAINER_OF_OWNER = "io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortBridge";

	/** Owner, then old name to new name. */
	private static final Map<String, Map<String, String>> KNOWN = Map.of(
			"net/neoforged/fml/config/ModConfig$Type", Map.of("COMMON", "LOCAL", "SERVER", "SYNCED"));

	private final Map<String, Map<String, String>> active;

	private PlatformRenames(Map<String, Map<String, String>> active) {
		this.active = active;
	}

	/** The renames the platform's classes call for: an old field missing there, its new one present. */
	static PlatformRenames of(ClassBytesSource platform) {
		Map<String, Map<String, String>> active = new HashMap<>();
		KNOWN.forEach((owner, renames) -> {
			ClassNode node = read(platform, owner);
			if (node == null) return;
			Map<String, String> applying = new HashMap<>();
			renames.forEach((from, to) -> {
				if (!hasField(node, from) && hasField(node, to)) applying.put(from, to);
			});
			if (!applying.isEmpty()) active.put(owner, Map.copyOf(applying));
		});
		return new PlatformRenames(Map.copyOf(active));
	}

	/** {@code classFile} with the renames and adaptations applied, or null when it uses none of those members. */
	byte[] apply(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		boolean renames = active.keySet().stream().anyMatch(text::contains);
		boolean screens = text.contains(CONFIGURATION_SCREEN) && text.contains(PORT_CONSTRUCTOR);
		if (!renames && !screens) return null;
		boolean[] changed = {false};
		ClassReader reader = new ClassReader(classFile);
		// Maxs recomputed: the argument shuffling below needs up to two more stack slots.
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		ClassVisitor adapter = new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
						if (opcode == Opcodes.INVOKESPECIAL && owner.equals(CONFIGURATION_SCREEN) && method.equals("<init>")
								&& desc.startsWith(PORT_CONSTRUCTOR) && withContainer(desc, owner, itf)) {
							changed[0] = true;
							return;
						}
						super.visitMethodInsn(opcode, owner, method, desc, itf);
					}

					/** Calls NeoForge's constructor with the mod's container in place of its id (two or three arguments). */
					private boolean withContainer(String desc, String owner, boolean itf) {
						int args = org.objectweb.asm.Type.getArgumentTypes(desc).length;
						String converted = "(" + CONTAINER + desc.substring("(Ljava/lang/String;".length());
						if (args == 2) {
							// id, parent -> parent, id -> parent, container -> container, parent
							super.visitInsn(Opcodes.SWAP);
							convert();
							super.visitInsn(Opcodes.SWAP);
						} else if (args == 3) {
							// id, parent, x -> x, id, parent -> x, parent, id -> x, parent, container -> x, container, parent
							super.visitInsn(Opcodes.DUP_X2);
							super.visitInsn(Opcodes.POP);
							super.visitInsn(Opcodes.SWAP);
							convert();
							super.visitInsn(Opcodes.SWAP);
							// -> container, parent, x
							super.visitInsn(Opcodes.DUP2_X1);
							super.visitInsn(Opcodes.POP2);
						} else {
							return false;
						}
						super.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", converted, itf);
						return true;
					}

					private void convert() {
						super.visitMethodInsn(Opcodes.INVOKESTATIC, CONTAINER_OF_OWNER, "modContainer", "(Ljava/lang/String;)" + CONTAINER, false);
					}
				};
			}
		};
		reader.accept(!renames ? adapter : new ClassRemapper(adapter, new Remapper() {
			@Override
			public String mapFieldName(String owner, String name, String descriptor) {
				String to = active.getOrDefault(owner, Map.of()).get(name);
				if (to == null) return name;
				changed[0] = true;
				return to;
			}
		}), 0);
		return changed[0] ? writer.toByteArray() : null;
	}

	private static ClassNode read(ClassBytesSource platform, String owner) {
		try {
			byte[] data = platform.read(owner);
			if (data == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, ClassReader.SKIP_CODE);
			return node;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static boolean hasField(ClassNode node, String name) {
		return node.fields.stream().map((FieldNode f) -> f.name).anyMatch(name::equals);
	}
}
