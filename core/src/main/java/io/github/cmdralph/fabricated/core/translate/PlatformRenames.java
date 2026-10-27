package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

/**
 * Members NeoForge renamed that Fabric mods still reach by their old names, through the copies of NeoForge's API made
 * for Fabric: Forge Config API Port keeps FML's old config types ({@code COMMON}, {@code SERVER}), which FML 12 calls
 * {@code LOCAL} and {@code SYNCED}. A mod's references are renamed where the running platform has only the new name.
 *
 * <p>The port's {@code ConfigurationScreen} also takes the mod's id where NeoForge's takes its container (in every
 * NeoForge version supported): a mod's {@code new ConfigurationScreen(modId, parent, ...)} passes the container that
 * Fabricated finds for that id instead, and its constructor references ({@code ConfigurationScreen::new}) point at
 * Fabricated's factories taking the id.
 *
 * <p>NeoForge's patches give vanilla's {@code CreativeModeTab} constructor eight more parameters, its builder's own
 * settings, and add a constructor taking the builder. A mod's subclass calling vanilla's constructor (which an access
 * widener opens: Polymer's tabs for a server's item groups) calls that one instead, with a builder Fabricated makes from
 * vanilla's arguments (CTOR-1).
 */
final class PlatformRenames {
	private static final String CONFIGURATION_SCREEN = "net/neoforged/neoforge/client/gui/ConfigurationScreen";
	private static final String PORT_CONSTRUCTOR = "(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;";
	private static final String CONTAINER = "Lnet/neoforged/fml/ModContainer;";
	/** Fabricated's (in its in-game mod): the container of a Fabric mod id. */
	private static final String CONTAINER_OF_OWNER = "io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortBridge";
	/** Fabricated's (client only): {@code configurationScreen(modId, parent, ...)} for each of the port's constructors. */
	private static final String SCREEN_FACTORIES = "io/github/cmdralph/fabricated/neoforge/config/ForgeConfigApiPortScreens";
	private static final String CREATIVE_TAB = "net/minecraft/world/item/CreativeModeTab";
	private static final String TAB_BUILDER = CREATIVE_TAB + "$Builder";
	/** Vanilla's constructor's parameters: row, column, type, name, icon, display items. */
	private static final String VANILLA_TAB_ARGUMENTS = "(L" + CREATIVE_TAB + "$Row;IL" + CREATIVE_TAB + "$Type;Lnet/minecraft/network/chat/Component;"
			+ "Ljava/util/function/Supplier;L" + CREATIVE_TAB + "$DisplayItemsGenerator;)";
	private static final String BUILDER_CONSTRUCTOR = "(L" + TAB_BUILDER + ";)V";
	/** Fabricated's: {@code builder(...)}, a builder made from vanilla's constructor arguments. */
	private static final String TAB_BUILDERS = "io/github/cmdralph/fabricated/neoforge/item/VanillaCreativeTabs";

	/** Owner, then old name to new name. */
	private static final Map<String, Map<String, String>> KNOWN = Map.of(
			"net/neoforged/fml/config/ModConfig$Type", Map.of("COMMON", "LOCAL", "SERVER", "SYNCED"));

	private final Map<String, Map<String, String>> active;
	/** Whether the game's creative tab has NeoForge's constructor taking a builder, and not vanilla's. */
	private final boolean tabBuilders;
	/** Whether the port's screens get Fabricated's factories, which the platform's in-game mod has when it provides the port. */
	private final boolean portScreens;

	private PlatformRenames(Map<String, Map<String, String>> active, boolean tabBuilders, boolean portScreens) {
		this.active = active;
		this.tabBuilders = tabBuilders;
		this.portScreens = portScreens;
	}

	/**
	 * The renames the platform's classes call for: an old field missing there, its new one present; and the
	 * adaptations the game's classes do. {@code portScreens}: whether the platform's in-game mod provides Forge Config
	 * API Port's API, and so the factories its screens are adapted to (NeoForge's; on Forge the port's own jar loads,
	 * with its own screen: REPL-1).
	 */
	static PlatformRenames of(ClassBytesSource platform, ClassIndex game, boolean portScreens) {
		Map<String, Map<String, String>> active = new HashMap<>();
		KNOWN.forEach((owner, renames) -> {
			ClassNode node = platform.readNode(owner, ClassReader.SKIP_CODE);
			if (node == null) return;
			Map<String, String> applying = new HashMap<>();
			renames.forEach((from, to) -> {
				if (!hasField(node, from) && hasField(node, to)) applying.put(from, to);
			});
			if (!applying.isEmpty()) active.put(owner, Map.copyOf(applying));
		});
		ClassIndex.ClassInfo tab = game.get(CREATIVE_TAB);
		boolean tabBuilders = tab != null && tab.methodAccess("<init>" + BUILDER_CONSTRUCTOR) != null
				&& tab.methodAccess("<init>" + VANILLA_TAB_ARGUMENTS + "V") == null;
		return new PlatformRenames(Map.copyOf(active), tabBuilders, portScreens);
	}

	/** {@code classFile} with the renames and adaptations applied, or null when it uses none of those members. */
	byte[] apply(byte[] classFile) {
		String text = new String(classFile, StandardCharsets.ISO_8859_1);
		boolean renames = active.keySet().stream().anyMatch(text::contains);
		boolean screens = portScreens && text.contains(CONFIGURATION_SCREEN) && text.contains(PORT_CONSTRUCTOR);
		boolean tabs = tabBuilders && text.contains(VANILLA_TAB_ARGUMENTS);
		if (!renames && !screens && !tabs) return null;
		boolean[] changed = {false};
		ClassReader reader = new ClassReader(classFile);
		// Maxs recomputed: the argument shuffling below needs up to two more stack slots.
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		ClassVisitor adapter = new ClassVisitor(Opcodes.ASM9, writer) {
			private boolean tabSubclass;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				tabSubclass = tabs && CREATIVE_TAB.equals(superName);
				super.visit(version, access, name, signature, superName, interfaces);
			}

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
						if (tabSubclass && opcode == Opcodes.INVOKESPECIAL && owner.equals(CREATIVE_TAB) && method.equals("<init>")
								&& desc.equals(VANILLA_TAB_ARGUMENTS + "V")) {
							// row, column, type, name, icon, items -> builder; NeoForge's constructor is protected, so
							// only a subclass's call of it links.
							super.visitMethodInsn(Opcodes.INVOKESTATIC, TAB_BUILDERS, "builder", VANILLA_TAB_ARGUMENTS + "L" + TAB_BUILDER + ";", false);
							super.visitMethodInsn(Opcodes.INVOKESPECIAL, CREATIVE_TAB, "<init>", BUILDER_CONSTRUCTOR, false);
							changed[0] = true;
							return;
						}
						super.visitMethodInsn(opcode, owner, method, desc, itf);
					}

					@Override
					public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
						Object[] adapted = args.clone();
						for (int i = 0; i < adapted.length; i++) {
							if (adapted[i] instanceof Handle h && h.getTag() == Opcodes.H_NEWINVOKESPECIAL && h.getOwner().equals(CONFIGURATION_SCREEN)
									&& h.getDesc().startsWith(PORT_CONSTRUCTOR)) {
								adapted[i] = new Handle(Opcodes.H_INVOKESTATIC, SCREEN_FACTORIES, "configurationScreen",
										h.getDesc().substring(0, h.getDesc().length() - 1) + "L" + CONFIGURATION_SCREEN + ";", false);
								changed[0] = true;
							}
						}
						super.visitInvokeDynamicInsn(name, desc, bootstrap, adapted);
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

	private static boolean hasField(ClassNode node, String name) {
		return node.fields.stream().map((FieldNode f) -> f.name).anyMatch(name::equals);
	}
}
