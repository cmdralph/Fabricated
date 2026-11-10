package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * Mixins into library classes NeoForge loads without mixin support that give the class interfaces another library
 * declares, with methods calling the class's own: adventure-platform-fabric makes authlib's {@code GameProfile} an
 * Adventure {@code Identity} and Brigadier's {@code CommandSyntaxException} a {@code ComponentMessageThrowable}, and
 * its player mixin casts the player's profile to {@code Identity} (LIB-2). LIB-1's bridges can't serve them: other
 * classes implement those interfaces, which have default methods. Instead a generated adapter implements the
 * interfaces for one library object: the mixin's methods become its own, calling the object's methods where the mixin
 * calls its shadows, and every other public method of the object's class is forwarded to the object, as the class's
 * methods take precedence over the interfaces' defaults where the mixin applies (its {@code equals}, {@code hashCode}
 * and {@code toString} too, so two adapters of one object are equal). The mod's casts to the interfaces wrap a library
 * object in an adapter, its checks against them count the library class, and its casts to the library class unwrap an
 * adapter. The object itself still doesn't implement the interfaces: code outside the mod checking it against them
 * sees it doesn't, as without the mixin, and two casts of one object are equal but not the same.
 *
 * <p>Only a mixin with no fields of its own or shadowed (state an adapter, made at each cast, couldn't keep), a trivial
 * constructor, no injector or other Mixin feature, no call of its target's superclass, and shadows of public methods
 * is adapted.
 */
final class LibraryAdapters {
	private static final String OBJECT = "java/lang/Object";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String TARGET = "fabricated$target";
	static final String CAST = "fabricated$cast";
	static final String UNWRAP = "fabricated$unwrap";
	private static final String IS = "fabricated$is";
	private static final String WRAP_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";
	/** Object's methods an adapter can't or needn't declare: final or protected ones. */
	private static final Set<String> OBJECT_KEPT = Set.of("getClass()Ljava/lang/Class;", "notify()V", "notifyAll()V", "wait()V", "wait(J)V",
			"wait(JI)V", "clone()Ljava/lang/Object;", "finalize()V");

	/** One interface an adapter serves for its library class, and the adapter's check standing for {@code instanceof} it. */
	record Adapted(String itf, String library, String adapter, String check) {
	}

	/** A generated adapter and the interfaces it serves. */
	record Adapter(String name, byte[] bytes, List<Adapted> served) {
	}

	private LibraryAdapters() {
	}

	/**
	 * The adapter named {@code name} standing in for {@code mixinBytes}, a mixin into the library class {@code target};
	 * null when it can't: the mixin keeps state, does more than implement its interfaces with the class's public methods
	 * and its own, or gives an interface another adapter or bridge already serves ({@code taken}).
	 *
	 * @param library the library's classes and the JDK's, by internal name (the target's hierarchy)
	 */
	static Adapter of(byte[] mixinBytes, String target, Function<String, ClassNode> library, Set<String> taken, String name) {
		ClassNode mixin = new ClassNode();
		new ClassReader(mixinBytes).accept(mixin, ClassReader.EXPAND_FRAMES);
		if ((mixin.access & Opcodes.ACC_INTERFACE) != 0 || !OBJECT.equals(mixin.superName) || mixin.interfaces.isEmpty() || !mixin.fields.isEmpty()
				|| !LibraryBridges.allowed(mixin.visibleAnnotations) || !LibraryBridges.allowed(mixin.invisibleAnnotations)
				|| mixin.interfaces.stream().anyMatch(taken::contains)) return null;
		ClassNode lib = library.apply(target);
		if (lib == null || (lib.access & Opcodes.ACC_INTERFACE) != 0) return null;
		Map<String, MethodNode> forwarded = publicMethods(target, library);
		if (forwarded == null) return null;

		ClassNode adapter = new ClassNode();
		adapter.visit(Math.max(mixin.version, Opcodes.V1_8), Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name,
				null, OBJECT, mixin.interfaces.toArray(String[]::new));
		adapter.visitSource(mixin.sourceFile, null);
		adapter.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, TARGET, "L" + target + ";", null, null));

		Set<String> declared = new HashSet<>();
		List<MethodNode> own = new ArrayList<>();
		for (MethodNode m : mixin.methods) {
			if (!LibraryBridges.allowed(Annotations.all(m))) return null;
			if (m.name.equals("<init>")) {
				if (!LibraryBridges.trivialConstructor(m)) return null;
				continue;
			}
			if (m.name.equals("<clinit>")) return null;
			AnnotationNode shadow = Annotations.find(m, SHADOW);
			if (shadow != null) {
				MethodNode real = forwarded.get(shadowName(m, shadow) + m.desc);
				boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
				if (real == null && isStatic) real = publicStatic(target, shadowName(m, shadow), m.desc, library);
				if (real == null || ((real.access & Opcodes.ACC_STATIC) != 0) != isStatic) return null;
				adapter.methods.add(forwarder(name, target, m.name, m.desc, real.name, isStatic, false));
				declared.add(m.name + m.desc);
				continue;
			}
			if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return null;
			own.add(m);
		}
		for (MethodNode m : own) {
			if (!retarget(m, mixin.name, name, target)) return null;
			if (!declared.add(m.name + m.desc)) return null;
			adapter.methods.add(m);
		}
		// The class's other public methods, which answer an interface's methods of their name before its defaults would.
		for (MethodNode m : forwarded.values()) {
			if (declared.contains(m.name + m.desc) || (m.access & Opcodes.ACC_STATIC) != 0) continue;
			adapter.methods.add(forwarder(name, target, m.name, m.desc, m.name, false, m.name.equals("equals") && m.desc.equals("(Ljava/lang/Object;)Z")));
			declared.add(m.name + m.desc);
		}

		adapter.methods.add(constructor(name, target));
		adapter.methods.add(cast(name, target));
		adapter.methods.add(unwrap(name, target));
		adapter.methods.add(check(IS + "Target", name, target));
		List<Adapted> served = new ArrayList<>();
		for (int i = 0; i < mixin.interfaces.size(); i++) {
			String check = IS + i;
			adapter.methods.add(check(check, mixin.interfaces.get(i), target));
			served.add(new Adapted(mixin.interfaces.get(i), target, name, check));
		}
		Set<String> names = new HashSet<>();
		for (MethodNode m : adapter.methods) {
			if (!names.add(m.name + m.desc)) return null;
		}
		ClassWriter writer = new ClassWriter(0);
		adapter.accept(writer);
		return new Adapter(name, writer.toByteArray(), List.copyOf(served));
	}

	/**
	 * {@code data} with its casts to the served interfaces wrapping a library object in its adapter, its checks against
	 * them counting the library class, and its casts to a served library class unwrapping an adapter; null when it has
	 * none. Not for the adapters themselves, whose checks are what a check becomes.
	 */
	static byte[] rewrite(byte[] data, Map<String, Adapted> served) {
		String text = new String(data, StandardCharsets.ISO_8859_1);
		if (served.keySet().stream().noneMatch(text::contains) && served.values().stream().noneMatch(a -> text.contains(a.library()))) return null;
		Map<String, String> unwrapping = new LinkedHashMap<>();
		served.values().forEach(a -> unwrapping.putIfAbsent(a.library(), a.adapter()));
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		boolean changed = false;
		for (MethodNode m : node.methods) {
			for (AbstractInsnNode insn : m.instructions.toArray()) {
				if (!(insn instanceof TypeInsnNode t)) continue;
				Adapted a = served.get(t.desc);
				if (a != null && t.getOpcode() == Opcodes.CHECKCAST) {
					m.instructions.insertBefore(t, new MethodInsnNode(Opcodes.INVOKESTATIC, a.adapter(), CAST, WRAP_DESC, false));
					changed = true;
				} else if (a != null && t.getOpcode() == Opcodes.INSTANCEOF) {
					m.instructions.set(t, new MethodInsnNode(Opcodes.INVOKESTATIC, a.adapter(), a.check(), "(Ljava/lang/Object;)Z", false));
					changed = true;
				} else if (t.getOpcode() == Opcodes.CHECKCAST && unwrapping.containsKey(t.desc)) {
					m.instructions.insertBefore(t, new MethodInsnNode(Opcodes.INVOKESTATIC, unwrapping.get(t.desc), UNWRAP, WRAP_DESC, false));
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
	 * Makes {@code m}, one of the mixin's own methods, the adapter's: its calls of the mixin's methods go to the
	 * adapter's, its casts to the library class unwrap an adapter, its casts to the mixin wrap a library object, and the
	 * mixin's name elsewhere (frames, descriptors, lambdas) becomes the adapter's. False when it uses the mixin's fields
	 * or calls its target's superclass, which an adapter can't stand for.
	 */
	private static boolean retarget(MethodNode m, String mixin, String adapter, String target) {
		String mixinDesc = "L" + mixin + ";";
		String adapterDesc = "L" + adapter + ";";
		m.desc = m.desc.replace(mixinDesc, adapterDesc);
		if (m.signature != null) m.signature = m.signature.replace(mixinDesc, adapterDesc);
		for (AbstractInsnNode insn : m.instructions.toArray()) {
			if (insn instanceof FieldInsnNode f) {
				if (f.owner.equals(mixin)) return false;
				f.desc = f.desc.replace(mixinDesc, adapterDesc);
			} else if (insn instanceof MethodInsnNode c) {
				if (c.getOpcode() == Opcodes.INVOKESPECIAL && !c.name.equals("<init>") && !c.owner.equals(mixin)) return false;
				if (c.owner.equals(mixin)) c.owner = adapter;
				c.desc = c.desc.replace(mixinDesc, adapterDesc);
			} else if (insn instanceof TypeInsnNode t) {
				if (t.desc.equals(mixin) && t.getOpcode() == Opcodes.CHECKCAST) {
					m.instructions.insertBefore(t, new MethodInsnNode(Opcodes.INVOKESTATIC, adapter, CAST, WRAP_DESC, false));
					t.desc = adapter;
				} else if (t.desc.equals(mixin) && t.getOpcode() == Opcodes.INSTANCEOF) {
					// The mixin is its target where it applies: an adapter, or one of the class's objects.
					m.instructions.set(t, new MethodInsnNode(Opcodes.INVOKESTATIC, adapter, IS + "Target", "(Ljava/lang/Object;)Z", false));
				} else if (t.desc.equals(target) && t.getOpcode() == Opcodes.CHECKCAST) {
					m.instructions.insertBefore(t, new MethodInsnNode(Opcodes.INVOKESTATIC, adapter, UNWRAP, WRAP_DESC, false));
				} else if (t.desc.equals(mixin)) {
					return false;
				} else {
					t.desc = t.desc.replace(mixinDesc, adapterDesc);
				}
			} else if (insn instanceof LdcInsnNode l && l.cst instanceof Type type && type.getDescriptor().contains(mixinDesc)) {
				return false; // the mixin's class as a value
			} else if (insn instanceof MultiANewArrayInsnNode a) {
				a.desc = a.desc.replace(mixinDesc, adapterDesc);
			} else if (insn instanceof InvokeDynamicInsnNode indy) {
				indy.desc = indy.desc.replace(mixinDesc, adapterDesc);
				if (indy.bsm.getOwner().equals(mixin)) return false;
				for (int i = 0; i < indy.bsmArgs.length; i++) {
					if (indy.bsmArgs[i] instanceof Handle h) {
						indy.bsmArgs[i] = new Handle(h.getTag(), h.getOwner().equals(mixin) ? adapter : h.getOwner(), h.getName(),
								h.getDesc().replace(mixinDesc, adapterDesc), h.isInterface());
					} else if (indy.bsmArgs[i] instanceof Type type) {
						indy.bsmArgs[i] = Type.getType(type.getDescriptor().replace(mixinDesc, adapterDesc));
					}
				}
			} else if (insn instanceof FrameNode frame) {
				LibraryBridges.replace(frame.local, mixin, adapter);
				LibraryBridges.replace(frame.stack, mixin, adapter);
			}
		}
		if (m.tryCatchBlocks != null) {
			for (TryCatchBlockNode block : m.tryCatchBlocks) {
				if (mixin.equals(block.type)) return false;
			}
		}
		if (m.localVariables != null) {
			for (LocalVariableNode lv : m.localVariables) {
				lv.desc = lv.desc.replace(mixinDesc, adapterDesc);
				lv.signature = null;
			}
		}
		m.visibleAnnotations = m.invisibleAnnotations = null;
		m.visibleTypeAnnotations = m.invisibleTypeAnnotations = null;
		m.visibleLocalVariableAnnotations = m.invisibleLocalVariableAnnotations = null;
		// Its access as declared: a private method of the mixin stays private to the adapter, a lambda's too.
		m.access &= ~Opcodes.ACC_ABSTRACT;
		return true;
	}

	/**
	 * The public methods of the class and its superclasses, by name and descriptor, the class's first: those an adapter
	 * forwards (but Object's final and protected ones). Null when a class of the hierarchy can't be read.
	 */
	private static Map<String, MethodNode> publicMethods(String target, Function<String, ClassNode> library) {
		Map<String, MethodNode> methods = new LinkedHashMap<>();
		for (String owner = target; owner != null; ) {
			ClassNode node = library.apply(owner);
			if (node == null) return null;
			for (MethodNode m : node.methods) {
				if ((m.access & Opcodes.ACC_PUBLIC) == 0 || m.name.startsWith("<") || (m.access & Opcodes.ACC_STATIC) != 0) continue;
				if (owner.equals(OBJECT) && OBJECT_KEPT.contains(m.name + m.desc)) continue;
				methods.putIfAbsent(m.name + m.desc, m);
			}
			owner = node.superName;
		}
		return methods;
	}

	/** The class's public static method of that name and descriptor, in it or a superclass; null when none is. */
	private static MethodNode publicStatic(String target, String name, String desc, Function<String, ClassNode> library) {
		for (String owner = target; owner != null; ) {
			ClassNode node = library.apply(owner);
			if (node == null) return null;
			for (MethodNode m : node.methods) {
				if (m.name.equals(name) && m.desc.equals(desc)) {
					return (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) ? m : null;
				}
			}
			owner = node.superName;
		}
		return null;
	}

	/** The name of the method a {@code @Shadow} stands for: its own, without Mixin's prefix ({@code shadow$} unless it says another). */
	private static String shadowName(MethodNode m, AnnotationNode shadow) {
		String prefix = Annotations.value(shadow, "prefix") instanceof String p ? p : "shadow$";
		return m.name.startsWith(prefix) && m.name.length() > prefix.length() ? m.name.substring(prefix.length()) : m.name;
	}

	/**
	 * A method of the adapter named {@code name} calling the library class's {@code real} on the adapted object (or, static,
	 * on the class) with its arguments; {@code equals} unwraps its argument, so that two adapters of an object are equal.
	 */
	private static MethodNode forwarder(String adapter, String target, String name, String desc, String real, boolean isStatic, boolean unwrapArgument) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0) | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
		int slot = 0;
		int stack = 0;
		if (!isStatic) {
			m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, adapter, TARGET, "L" + target + ";"));
			slot = 1;
			stack = 1;
		}
		for (Type arg : Type.getArgumentTypes(desc)) {
			m.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot));
			slot += arg.getSize();
			stack += arg.getSize();
		}
		if (unwrapArgument) m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, adapter, UNWRAP, WRAP_DESC, false));
		m.instructions.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL, target, real, desc, false));
		Type ret = Type.getReturnType(desc);
		m.instructions.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
		m.maxStack = Math.max(stack, ret.getSize());
		m.maxLocals = slot;
		return m;
	}

	private static MethodNode constructor(String adapter, String target) {
		MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", "(L" + target + ";)V", null, null);
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, OBJECT, "<init>", "()V", false));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, adapter, TARGET, "L" + target + ";"));
		m.instructions.add(new InsnNode(Opcodes.RETURN));
		m.maxStack = 2;
		m.maxLocals = 2;
		return m;
	}

	/** {@code Object fabricated$cast(Object)}: one of the library class's objects in a new adapter, anything else as it is. */
	private static MethodNode cast(String adapter, String target) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, CAST, WRAP_DESC, null, null);
		LabelNode other = new LabelNode();
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, target));
		m.instructions.add(new JumpInsnNode(Opcodes.IFEQ, other));
		m.instructions.add(new TypeInsnNode(Opcodes.NEW, adapter));
		m.instructions.add(new InsnNode(Opcodes.DUP));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, target));
		m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, adapter, "<init>", "(L" + target + ";)V", false));
		m.instructions.add(new InsnNode(Opcodes.ARETURN));
		m.instructions.add(other);
		m.instructions.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {OBJECT}, 0, new Object[0]));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new InsnNode(Opcodes.ARETURN));
		m.maxStack = 3;
		m.maxLocals = 1;
		return m;
	}

	/** {@code Object fabricated$unwrap(Object)}: the object an adapter adapts, anything else as it is. */
	private static MethodNode unwrap(String adapter, String target) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, UNWRAP, WRAP_DESC, null, null);
		LabelNode other = new LabelNode();
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, adapter));
		m.instructions.add(new JumpInsnNode(Opcodes.IFEQ, other));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, adapter));
		m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, adapter, TARGET, "L" + target + ";"));
		m.instructions.add(new InsnNode(Opcodes.ARETURN));
		m.instructions.add(other);
		m.instructions.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {OBJECT}, 0, new Object[0]));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new InsnNode(Opcodes.ARETURN));
		m.maxStack = 1;
		m.maxLocals = 1;
		return m;
	}

	/** {@code boolean <name>(Object)}: whether the object is of {@code type} (the interface, or the adapter) or the library class's. */
	private static MethodNode check(String name, String type, String target) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, "(Ljava/lang/Object;)Z", null, null);
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, type));
		m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		m.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, target));
		m.instructions.add(new InsnNode(Opcodes.IOR));
		m.instructions.add(new InsnNode(Opcodes.IRETURN));
		m.maxStack = 2;
		m.maxLocals = 1;
		return m;
	}
}
