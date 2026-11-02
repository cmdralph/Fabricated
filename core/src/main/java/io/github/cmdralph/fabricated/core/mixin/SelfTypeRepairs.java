package io.github.cmdralph.fabricated.core.mixin;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Descriptors that name a mixin class itself where Mixin, merging the mixin, needs its target's name (SELF-1).
 * <p>
 * A mixin's code is written against its own class: a {@code @Unique} field of the target's type, or a handler taking
 * two of the target's instances, has the mixin's type in its descriptor ({@code polydium$left} in Polydium's font
 * texture mixin, and its {@code @WrapOperation} handler taking two of them). Merging the mixin, Mixin names the target
 * there instead. Fabric's Mixin does so for every mixin since 0.17.1; the Mixin NeoForge runs (Fabric's 0.16.4) and
 * Forge's (SpongePowered's 0.8.5) do it only where the mixin has inner classes or a mixin superclass, or the target a
 * superclass another mixin targets. Elsewhere the mixin's type stays: such a handler fails its injection's check, and a
 * field of that type fails verification once the target's code stores the target into it, as the mixin class may not
 * be loaded. Fabricated names the target there as it translates the mod, as Fabric's Mixin does; where the running
 * Mixin would have done it, it finds the target's name already, and the result is the same.
 * <p>
 * Only for a class mixin with one target (Mixin merges a mixin with several into each, under each one's name), and only
 * the mixin's own type: another mixin's stands for whichever class of the target's hierarchy that one targets, which
 * Mixin resolves as it applies. Owners are left to Mixin, which renames those whatever its version.
 */
final class SelfTypeRepairs {
	private SelfTypeRepairs() {
	}

	/**
	 * Names {@code target} where the descriptors of a class mixin with that one target name the mixin itself: its
	 * fields' and methods' (with their local variables'), and those of the field accesses, calls, method handles, class
	 * constants and arrays its code has. Returns how many it renamed; none for an interface (an accessor mixin, whose
	 * methods Mixin generates or rewrites in place).
	 */
	static int retarget(ClassNode mixin, String target) {
		if ((mixin.access & Opcodes.ACC_INTERFACE) != 0 || target.equals(mixin.name)) return 0;
		Retyper retyper = new Retyper(mixin.name, target);
		for (FieldNode field : mixin.fields) {
			String desc = retyper.single(field.desc);
			if (desc.equals(field.desc)) continue;
			field.desc = desc;
			if (field.signature != null && retyper.mentions(field.signature)) field.signature = null;
			retyper.count++;
		}
		for (MethodNode method : mixin.methods) {
			String desc = retyper.method(method.desc);
			if (!desc.equals(method.desc)) {
				method.desc = desc;
				if (method.signature != null && retyper.mentions(method.signature)) method.signature = null;
				retyper.count++;
			}
			if (method.localVariables != null) {
				for (LocalVariableNode local : method.localVariables) {
					String localDesc = retyper.single(local.desc);
					if (localDesc.equals(local.desc)) continue;
					local.desc = localDesc;
					// As Mixin does with every local's signature it transforms.
					local.signature = null;
				}
			}
			for (AbstractInsnNode insn : method.instructions) {
				retyper.instruction(insn);
			}
		}
		return retyper.count;
	}

	/** Renames one class in descriptors, counting the references it renamed in code. */
	private static final class Retyper {
		private final String self;
		private final String real;
		int count;

		Retyper(String self, String real) {
			this.self = self;
			this.real = real;
		}

		boolean mentions(String signature) {
			return signature.contains("L" + self + ";") || signature.contains("L" + self + "<");
		}

		/** A field (or local) descriptor with the class renamed. */
		String single(String desc) {
			if (desc == null) return null;
			Type type = Type.getType(desc);
			return retype(type).getDescriptor();
		}

		/** A method descriptor with the class renamed. */
		String method(String desc) {
			Type type = Type.getMethodType(desc);
			Type[] args = type.getArgumentTypes();
			boolean changed = false;
			for (int i = 0; i < args.length; i++) {
				Type arg = retype(args[i]);
				changed |= arg != args[i];
				args[i] = arg;
			}
			Type ret = retype(type.getReturnType());
			changed |= ret != type.getReturnType();
			return changed ? Type.getMethodDescriptor(ret, args) : desc;
		}

		private Type retype(Type type) {
			if (type.getSort() == Type.OBJECT) {
				return type.getInternalName().equals(self) ? Type.getObjectType(real) : type;
			}
			if (type.getSort() == Type.ARRAY) {
				Type element = retype(type.getElementType());
				return element == type.getElementType() ? type : Type.getType("[".repeat(type.getDimensions()) + element.getDescriptor());
			}
			return type;
		}

		private Object constant(Object value) {
			if (value instanceof Type type) {
				Type renamed = type.getSort() == Type.METHOD ? Type.getMethodType(method(type.getDescriptor())) : retype(type);
				if (!renamed.getDescriptor().equals(type.getDescriptor())) {
					count++;
					return renamed;
				}
				return value;
			}
			if (value instanceof Handle handle) {
				boolean field = handle.getTag() <= Opcodes.H_PUTSTATIC;
				String desc = field ? single(handle.getDesc()) : method(handle.getDesc());
				if (desc.equals(handle.getDesc())) return value;
				count++;
				return new Handle(handle.getTag(), handle.getOwner(), handle.getName(), desc, handle.isInterface());
			}
			if (value instanceof ConstantDynamic dynamic) {
				Object[] args = new Object[dynamic.getBootstrapMethodArgumentCount()];
				boolean changed = false;
				for (int i = 0; i < args.length; i++) {
					Object arg = dynamic.getBootstrapMethodArgument(i);
					args[i] = constant(arg);
					changed |= args[i] != arg;
				}
				Handle bootstrap = (Handle) constant(dynamic.getBootstrapMethod());
				String desc = single(dynamic.getDescriptor());
				if (!changed && bootstrap == dynamic.getBootstrapMethod() && desc.equals(dynamic.getDescriptor())) return value;
				return new ConstantDynamic(dynamic.getName(), desc, bootstrap, args);
			}
			return value;
		}

		void instruction(AbstractInsnNode insn) {
			if (insn instanceof FieldInsnNode field) {
				String desc = single(field.desc);
				if (!desc.equals(field.desc)) {
					field.desc = desc;
					count++;
				}
			} else if (insn instanceof MethodInsnNode call) {
				String desc = method(call.desc);
				if (!desc.equals(call.desc)) {
					call.desc = desc;
					count++;
				}
			} else if (insn instanceof InvokeDynamicInsnNode dynamic) {
				String desc = method(dynamic.desc);
				if (!desc.equals(dynamic.desc)) {
					dynamic.desc = desc;
					count++;
				}
				dynamic.bsm = (Handle) constant(dynamic.bsm);
				for (int i = 0; i < dynamic.bsmArgs.length; i++) dynamic.bsmArgs[i] = constant(dynamic.bsmArgs[i]);
			} else if (insn instanceof LdcInsnNode ldc) {
				ldc.cst = constant(ldc.cst);
			} else if (insn instanceof TypeInsnNode type && type.desc.startsWith("[")) {
				// An array type (ANEWARRAY of arrays, CHECKCAST to one); a plain class name is an owner, which Mixin renames.
				String desc = single(type.desc);
				if (!desc.equals(type.desc)) {
					type.desc = desc;
					count++;
				}
			} else if (insn instanceof MultiANewArrayInsnNode array) {
				String desc = single(array.desc);
				if (!desc.equals(array.desc)) {
					array.desc = desc;
					count++;
				}
			}
		}
	}
}
