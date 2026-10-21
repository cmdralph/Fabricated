package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * Mixins into library classes NeoForge loads without mixin support (Brigadier, DataFixerUpper) that only give the
 * library class interfaces of the mod, with methods using the class's fields, as Carpet's command removal does
 * (LIB-1). Such a mixin can't apply, and the mod's casts to its interface would fail. Instead its methods become static
 * methods of a generated class, which reads and writes the library class's fields through reflection, and the mod's
 * casts to the interfaces, checks against them and calls of their methods call that class. Those libraries are on the
 * class path at runtime, in the unnamed module, whose packages are open to reflection.
 */
final class LibraryBridges {
	private static final String OBJECT = "java/lang/Object";
	private static final String FIELD = "java/lang/reflect/Field";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	/** The annotations of Mixin a bridged mixin's members may have: none changes what the mixin does. */
	private static final Set<String> ALLOWED = Set.of(SHADOW, "Lorg/spongepowered/asm/mixin/Unique;", "Lorg/spongepowered/asm/mixin/Final;",
			"Lorg/spongepowered/asm/mixin/Mutable;", Annotations.MIXIN);
	private static final String CAST = "fabricated$cast";
	private static final String IS = "fabricated$is";

	/** One of the mod's interfaces a bridged mixin gave a library class, the class standing in for it, and its methods ({@code name + desc}). */
	record Duck(String itf, String bridge, Set<String> methods) {
	}

	/** A generated class and the interfaces it serves. */
	record Bridge(String name, byte[] bytes, List<Duck> ducks) {
	}

	private LibraryBridges() {
	}

	/**
	 * The class named {@code name} standing in for {@code mixinBytes}, a mixin into the library class {@code target};
	 * null when it can't: the mixin does more than implement its interfaces with the class's fields and its own methods,
	 * or one of its interfaces has default methods or another implementation in the mod.
	 *
	 * @param library      the library's classes, by internal name (to find the fields the mixin shadows)
	 * @param modClass     the mod's classes, by internal name
	 * @param implementors how many of the mod's classes implement or extend an interface
	 */
	static Bridge of(byte[] mixinBytes, String target, Function<String, ClassNode> library, Function<String, ClassNode> modClass,
			ToIntFunction<String> implementors, String name) {
		ClassNode mixin = new ClassNode();
		new ClassReader(mixinBytes).accept(mixin, ClassReader.EXPAND_FRAMES);
		if ((mixin.access & Opcodes.ACC_INTERFACE) != 0 || !OBJECT.equals(mixin.superName) || mixin.interfaces.isEmpty()
				|| !allowed(mixin.visibleAnnotations) || !allowed(mixin.invisibleAnnotations)) return null;

		// Its fields only stand for the library class's: a field of its own would be state no class has.
		Map<String, String> declaring = new HashMap<>();
		for (FieldNode f : mixin.fields) {
			AnnotationNode shadow = Annotations.find(f, SHADOW);
			if (shadow == null || !allowed(f.visibleAnnotations) || !allowed(f.invisibleAnnotations) || !onlyRemap(shadow)) return null;
			String owner = declaringClass(target, f.name, f.desc, (f.access & Opcodes.ACC_STATIC) != 0, library);
			if (owner == null) return null;
			declaring.put(f.name + ":" + f.desc, owner);
		}

		Map<String, MethodNode> own = new HashMap<>();
		for (MethodNode m : mixin.methods) {
			if (!allowed(Annotations.all(m))) return null;
			if (m.name.equals("<init>")) {
				if (!trivialConstructor(m)) return null;
			} else if (Annotations.find(m, SHADOW) != null) {
				continue; // the library class's method: a body calling it isn't bridged (toStatic)
			} else if (m.name.equals("<clinit>") || (m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
				return null;
			} else {
				own.put(m.name + m.desc, m);
			}
		}

		List<Duck> ducks = new ArrayList<>();
		for (String itf : mixin.interfaces) {
			ClassNode node = modClass.apply(itf);
			if (node == null || (node.access & Opcodes.ACC_INTERFACE) == 0 || !node.interfaces.isEmpty() || implementors.applyAsInt(itf) != 1) return null;
			Set<String> methods = new LinkedHashSet<>();
			for (MethodNode m : node.methods) {
				if ((m.access & Opcodes.ACC_STATIC) != 0) continue;
				MethodNode impl = own.get(m.name + m.desc);
				if ((m.access & Opcodes.ACC_ABSTRACT) == 0 || impl == null || (impl.access & Opcodes.ACC_STATIC) != 0) return null;
				methods.add(m.name + m.desc);
			}
			ducks.add(new Duck(itf, name, Set.copyOf(methods)));
		}

		ClassNode bridge = new ClassNode();
		bridge.visit(Math.max(mixin.version, Opcodes.V1_8), Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name,
				null, OBJECT, null);
		bridge.visitSource(mixin.sourceFile, null);
		Accessors accessors = new Accessors(name, bridge);
		for (MethodNode m : own.values()) {
			if (!toStatic(m, mixin.name, target, own, declaring, accessors)) return null;
			bridge.methods.add(m);
		}
		bridge.methods.add(cast(target));
		bridge.methods.add(is(target));
		accessors.finish();
		Set<String> names = new HashSet<>();
		for (MethodNode m : bridge.methods) {
			if (!names.add(m.name + m.desc)) return null;
		}
		ClassWriter writer = new ClassWriter(0);
		bridge.accept(writer);
		return new Bridge(name, writer.toByteArray(), List.copyOf(ducks));
	}

	/**
	 * {@code data} with its casts to the ducks' interfaces, checks against them and calls of their methods (directly or
	 * as method references) going to their bridges; null when it has none.
	 */
	static byte[] rewrite(byte[] data, Map<String, Duck> ducks) {
		String text = new String(data, StandardCharsets.ISO_8859_1);
		if (ducks.keySet().stream().noneMatch(text::contains)) return null;
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		boolean changed = false;
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions.toArray()) {
				AbstractInsnNode replacement = null;
				if (insn instanceof TypeInsnNode t && ducks.get(t.desc) instanceof Duck d) {
					if (t.getOpcode() == Opcodes.CHECKCAST) {
						replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, d.bridge(), CAST, "(Ljava/lang/Object;)Ljava/lang/Object;", false);
					} else if (t.getOpcode() == Opcodes.INSTANCEOF) {
						replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, d.bridge(), IS, "(Ljava/lang/Object;)Z", false);
					}
				} else if (insn instanceof MethodInsnNode c && c.getOpcode() == Opcodes.INVOKEINTERFACE && ducks.get(c.owner) instanceof Duck d
						&& d.methods().contains(c.name + c.desc)) {
					replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, d.bridge(), c.name, withSelf(c.desc), false);
				} else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (int i = 0; i < indy.bsmArgs.length; i++) {
						if (indy.bsmArgs[i] instanceof Handle h && h.getTag() == Opcodes.H_INVOKEINTERFACE && ducks.get(h.getOwner()) instanceof Duck d
								&& d.methods().contains(h.getName() + h.getDesc())) {
							indy.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, d.bridge(), h.getName(), withSelf(h.getDesc()), false);
							// A bound receiver is captured as the bridge's first parameter, which must have its exact type.
							String receiver = "(L" + h.getOwner() + ";";
							if (indy.desc.startsWith(receiver)) indy.desc = "(Ljava/lang/Object;" + indy.desc.substring(receiver.length());
							changed = true;
						}
					}
				}
				if (replacement != null) {
					m.instructions.set(insn, replacement);
					changed = true;
				}
			}
		}
		if (!changed) return null;
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * Makes {@code m}, one of the mixin's own methods, a static method of the bridge: an instance method takes the
	 * library object first (cast to the class, in the slot {@code this} had), its shadowed fields are read and written
	 * through the bridge's accessors, and its calls of the mixin's methods go to the bridge. False when it uses the mixin
	 * otherwise (its shadowed methods, or its class as a value).
	 */
	private static boolean toStatic(MethodNode m, String mixin, String target, Map<String, MethodNode> own, Map<String, String> declaring,
			Accessors accessors) {
		boolean instance = (m.access & Opcodes.ACC_STATIC) == 0;
		for (AbstractInsnNode insn : m.instructions.toArray()) {
			if (insn instanceof FieldInsnNode f && f.owner.equals(mixin)) {
				String owner = declaring.get(f.name + ":" + f.desc);
				boolean staticAccess = f.getOpcode() == Opcodes.GETSTATIC || f.getOpcode() == Opcodes.PUTSTATIC;
				if (owner == null) return false;
				boolean read = f.getOpcode() == Opcodes.GETFIELD || f.getOpcode() == Opcodes.GETSTATIC;
				m.instructions.set(insn, accessors.call(owner, f.name, f.desc, staticAccess, read));
			} else if (insn instanceof MethodInsnNode c && c.owner.equals(mixin)) {
				MethodNode callee = own.get(c.name + c.desc);
				if (callee == null) return false;
				String desc = (callee.access & Opcodes.ACC_STATIC) == 0 ? withSelf(c.desc) : c.desc;
				m.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, accessors.bridge, c.name, desc, false));
			} else if (references(insn, mixin)) {
				return false;
			} else if (insn instanceof FrameNode frame) {
				replace(frame.local, mixin, target);
				replace(frame.stack, mixin, target);
			}
		}
		if (instance) {
			// this becomes the library object, as the class: what the frames now say of that slot.
			InsnList prologue = new InsnList();
			prologue.add(new VarInsnNode(Opcodes.ALOAD, 0));
			prologue.add(new TypeInsnNode(Opcodes.CHECKCAST, target));
			prologue.add(new VarInsnNode(Opcodes.ASTORE, 0));
			m.instructions.insert(prologue);
			m.desc = withSelf(m.desc);
			m.maxStack = Math.max(m.maxStack, 1);
		}
		m.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | (m.access & (Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC));
		m.signature = null;
		m.visibleAnnotations = m.invisibleAnnotations = null;
		m.visibleParameterAnnotations = m.invisibleParameterAnnotations = null;
		m.visibleAnnotableParameterCount = m.invisibleAnnotableParameterCount = 0;
		m.visibleTypeAnnotations = m.invisibleTypeAnnotations = null;
		m.visibleLocalVariableAnnotations = m.invisibleLocalVariableAnnotations = null;
		m.localVariables = null;
		m.parameters = null;
		m.attrs = null;
		return true;
	}

	/** The bridge's reflective accessors of the library class's fields, made as the methods' code needs them. */
	private static final class Accessors {
		final String bridge;
		final ClassNode node;
		final Map<String, Integer> indices = new HashMap<>();
		final InsnList init = new InsnList();

		Accessors(String bridge, ClassNode node) {
			this.bridge = bridge;
			this.node = node;
		}

		/** A call of the accessor reading or writing a field ({@code static} or of the object on the stack) declared in {@code owner}. */
		MethodInsnNode call(String owner, String name, String desc, boolean isStatic, boolean read) {
			String key = owner + "." + name + ":" + desc;
			Integer index = indices.get(key);
			if (index == null) {
				index = indices.size();
				indices.put(key, index);
				add(owner, name, desc, isStatic, index);
			}
			String self = isStatic ? "" : "Ljava/lang/Object;";
			return new MethodInsnNode(Opcodes.INVOKESTATIC, bridge, (read ? "fabricated$get" : "fabricated$set") + index,
					read ? "(" + self + ")" + desc : "(" + self + desc + ")V", false);
		}

		private void add(String owner, String name, String desc, boolean isStatic, int index) {
			String field = "fabricated$field" + index;
			node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, field, "Ljava/lang/reflect/Field;", null, null));
			init.add(new LdcInsnNode(Type.getObjectType(owner)));
			init.add(new LdcInsnNode(name));
			init.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false));
			init.add(new InsnNode(Opcodes.DUP));
			init.add(new InsnNode(Opcodes.ICONST_1));
			init.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FIELD, "setAccessible", "(Z)V", false));
			init.add(new FieldInsnNode(Opcodes.PUTSTATIC, bridge, field, "Ljava/lang/reflect/Field;"));

			Type type = Type.getType(desc);
			String self = isStatic ? "" : "Ljava/lang/Object;";
			int valueSlot = isStatic ? 0 : 1;
			String suffix = switch (type.getSort()) {
				case Type.BOOLEAN -> "Boolean";
				case Type.BYTE -> "Byte";
				case Type.CHAR -> "Char";
				case Type.SHORT -> "Short";
				case Type.INT -> "Int";
				case Type.LONG -> "Long";
				case Type.FLOAT -> "Float";
				case Type.DOUBLE -> "Double";
				default -> "";
			};
			String erased = suffix.isEmpty() ? "Ljava/lang/Object;" : desc;

			MethodNode get = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "fabricated$get" + index, "(" + self + ")" + desc, null, null);
			get.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, bridge, field, "Ljava/lang/reflect/Field;"));
			get.instructions.add(isStatic ? new InsnNode(Opcodes.ACONST_NULL) : new VarInsnNode(Opcodes.ALOAD, 0));
			get.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FIELD, "get" + suffix, "(Ljava/lang/Object;)" + erased, false));
			if (suffix.isEmpty()) get.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
			get.instructions.add(new InsnNode(type.getOpcode(Opcodes.IRETURN)));
			get.maxStack = Math.max(2, type.getSize());
			get.maxLocals = isStatic ? 0 : 1;
			node.methods.add(get);

			MethodNode set = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "fabricated$set" + index, "(" + self + desc + ")V", null, null);
			set.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, bridge, field, "Ljava/lang/reflect/Field;"));
			set.instructions.add(isStatic ? new InsnNode(Opcodes.ACONST_NULL) : new VarInsnNode(Opcodes.ALOAD, 0));
			set.instructions.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), valueSlot));
			set.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FIELD, "set" + suffix, "(Ljava/lang/Object;" + erased + ")V", false));
			set.instructions.add(new InsnNode(Opcodes.RETURN));
			set.maxStack = 2 + type.getSize();
			set.maxLocals = valueSlot + type.getSize();
			node.methods.add(set);
		}

		/** Adds the class initializer looking the fields up, when there are any. */
		void finish() {
			if (init.size() == 0) return;
			MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.instructions.add(init);
			clinit.instructions.add(new InsnNode(Opcodes.RETURN));
			clinit.maxStack = 3;
			clinit.maxLocals = 0;
			node.methods.add(clinit);
		}
	}

	/** {@code Object fabricated$cast(Object)}: the object, if it is one of the library class's (or null), as the cast to the interface. */
	private static MethodNode cast(String target) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, CAST, "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
		m.instructions.add(new LdcInsnNode(Type.getObjectType(target)));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "cast", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
		m.instructions.add(new InsnNode(Opcodes.ARETURN));
		m.maxStack = 2;
		m.maxLocals = 1;
		return m;
	}

	/** {@code boolean fabricated$is(Object)}: whether the object is one of the library class's, as the check against the interface. */
	private static MethodNode is(String target) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, IS, "(Ljava/lang/Object;)Z", null, null);
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, target));
		m.instructions.add(new InsnNode(Opcodes.IRETURN));
		m.maxStack = 1;
		m.maxLocals = 1;
		return m;
	}

	/** The class in {@code target}'s hierarchy declaring the field, with the same staticness; null when none does. */
	private static String declaringClass(String target, String name, String desc, boolean isStatic, Function<String, ClassNode> library) {
		for (String owner = target; owner != null; ) {
			ClassNode node = library.apply(owner);
			if (node == null) return null;
			for (FieldNode f : node.fields) {
				if (f.name.equals(name) && f.desc.equals(desc)) return ((f.access & Opcodes.ACC_STATIC) != 0) == isStatic ? owner : null;
			}
			owner = node.superName;
		}
		return null;
	}

	/** Whether the constructor only calls {@code Object}'s. */
	private static boolean trivialConstructor(MethodNode m) {
		List<Integer> opcodes = new ArrayList<>();
		for (AbstractInsnNode insn : m.instructions) {
			if (insn.getOpcode() >= 0) opcodes.add(insn.getOpcode());
		}
		return opcodes.equals(List.of(Opcodes.ALOAD, Opcodes.INVOKESPECIAL, Opcodes.RETURN));
	}

	private static boolean allowed(List<AnnotationNode> annotations) {
		if (annotations == null) return true;
		for (AnnotationNode a : annotations) {
			boolean mixinOwn = a.desc.startsWith("Lorg/spongepowered/asm/mixin/") || a.desc.startsWith("Lcom/llamalad7/mixinextras/");
			if (mixinOwn && !ALLOWED.contains(a.desc)) return false;
		}
		return true;
	}

	/** Whether a {@code @Shadow} names the field as the mixin declares it (no prefix or aliases). */
	private static boolean onlyRemap(AnnotationNode shadow) {
		if (shadow.values == null) return true;
		for (int i = 0; i < shadow.values.size(); i += 2) {
			if (!"remap".equals(shadow.values.get(i))) return false;
		}
		return true;
	}

	/** Whether the instruction names the class {@code name} other than as a field's or method's owner. */
	private static boolean references(AbstractInsnNode insn, String name) {
		String desc = "L" + name + ";";
		return switch (insn) {
			case TypeInsnNode t -> t.desc.equals(name) || t.desc.contains(desc);
			case FieldInsnNode f -> f.desc.contains(desc);
			case MethodInsnNode c -> c.desc.contains(desc);
			case LdcInsnNode l -> l.cst instanceof Type t && t.getDescriptor().contains(desc);
			case MultiANewArrayInsnNode a -> a.desc.contains(desc);
			case InvokeDynamicInsnNode indy -> indy.desc.contains(desc) || indy.bsm.getOwner().equals(name) || Arrays.stream(indy.bsmArgs)
					.anyMatch(arg -> arg instanceof Handle h && (h.getOwner().equals(name) || h.getDesc().contains(desc))
							|| arg instanceof Type t && t.getDescriptor().contains(desc));
			default -> false;
		};
	}

	private static void replace(List<Object> types, String from, String to) {
		if (types == null) return;
		types.replaceAll(t -> from.equals(t) ? to : t);
	}

	/** A method descriptor with the object it was called on as its first parameter. */
	private static String withSelf(String desc) {
		return "(Ljava/lang/Object;" + desc.substring(1);
	}
}
