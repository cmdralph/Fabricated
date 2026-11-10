package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;

/**
 * The arguments of a vanilla constructor that the platform keeps only to call another one, handed to that one for the
 * injections a Fabric mod made into the first (IRIS-1): Forge 47 keeps {@code ShaderInstance}'s constructor taking a
 * shader's name as {@code this(provider, new ResourceLocation(name), format)} and moves its code into the constructor
 * taking the {@code ResourceLocation}, where Iris's hook at the shader's JSON parse now is; its handler takes the name.
 *
 * <p>Pre-flight points such an injector at the other constructor, through a bridge taking that one's parameters
 * ({@link HandlerBridges#handedOffBridge}), and gives the mixin a field ({@link #field}) holding the first one's
 * arguments, marked with Fabricated's annotation. Mixin merges both into the target. Once its mixins have applied,
 * Fabricated's Mixin extension ({@link #apply}) has the first constructor store its arguments in the field as it
 * starts, before its {@code this(...)} call (the JVM lets a constructor set its own class's fields before then), and
 * clear it once that call returns. The bridge calls the handler with them, and doesn't where the field is empty: a
 * construction through the other constructor alone, which Fabric doesn't have. No state outlives the construction, and
 * none is shared between threads or instances.
 */
public final class ConstructorHandoffs {
	/** Fabricated's annotation on a mixin's field holding a constructor's arguments; its value is that constructor's descriptor. */
	static final String MARKER = "Lio/github/cmdralph/fabricated/ConstructorHandoff;";
	private static final String ARGUMENTS = "[Ljava/lang/Object;";

	private ConstructorHandoffs() {
	}

	/**
	 * The field of {@code mixin} that holds the arguments of its target's constructor of descriptor {@code constructor}:
	 * the one it has, or a new one, private and {@code @Unique}, so Mixin merges it as the mixin's own.
	 */
	static FieldNode field(ClassNode mixin, String constructor) {
		for (FieldNode field : mixin.fields) {
			AnnotationNode marker = field.invisibleAnnotations == null ? null : Annotations.find(field.invisibleAnnotations, MARKER);
			if (marker != null && constructor.equals(Annotations.value(marker, "value"))) return field;
		}
		FieldNode field = PreflightAsm.uniqueField(mixin, "fabricated$" + mixin.name.replace('/', '_') + "$arguments", ARGUMENTS);
		AnnotationNode marker = new AnnotationNode(MARKER);
		Annotations.put(marker, "value", constructor);
		field.invisibleAnnotations = new ArrayList<>(List.of(marker));
		return field;
	}

	/**
	 * Has each constructor whose arguments a field Mixin merged into {@code target} holds ({@link #field}) store them in
	 * it as it starts and clear it once its call to the constructor it delegates to returns; the marker goes. A field
	 * whose constructor isn't there, or calls no constructor of its class, is left as it is: its bridge then never calls
	 * its handler.
	 *
	 * @param target the class, its mixins applied
	 * @return the constructors changed
	 */
	public static int apply(ClassNode target) {
		int applied = 0;
		for (FieldNode field : target.fields) {
			AnnotationNode marker = field.invisibleAnnotations == null ? null : Annotations.find(field.invisibleAnnotations, MARKER);
			if (marker == null) continue;
			field.invisibleAnnotations.remove(marker);
			String desc = (String) Annotations.value(marker, "value");
			MethodNode constructor = PreflightAsm.declared(target, "<init>", desc);
			if (constructor == null || !field.desc.equals(ARGUMENTS) || (field.access & Opcodes.ACC_STATIC) != 0) continue;
			int delegate = ConstructorRepairs.delegateCall(constructor, target);
			if (delegate < 0 || !(constructor.instructions.get(delegate) instanceof MethodInsnNode call)
					|| !call.owner.equals(target.name)) continue;
			InsnList clear = new InsnList();
			clear.add(new VarInsnNode(Opcodes.ALOAD, 0));
			clear.add(new InsnNode(Opcodes.ACONST_NULL));
			clear.add(new FieldInsnNode(Opcodes.PUTFIELD, target.name, field.name, field.desc));
			constructor.instructions.insert(call, clear);
			constructor.instructions.insert(store(target.name, field, Type.getArgumentTypes(desc)));
			// The receiver, the array twice, an index and a value of up to two slots.
			constructor.maxStack = Math.max(constructor.maxStack, 6);
			applied++;
		}
		return applied;
	}

	/** {@code this.field = new Object[] {arguments...}}, primitives boxed. */
	private static InsnList store(String owner, FieldNode field, Type[] arguments) {
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new LdcInsnNode(arguments.length));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
		int slot = 1;
		for (int i = 0; i < arguments.length; i++) {
			code.add(new InsnNode(Opcodes.DUP));
			code.add(new LdcInsnNode(i));
			code.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD), slot));
			HandlerBridges.box(code, arguments[i]);
			code.add(new InsnNode(Opcodes.AASTORE));
			slot += arguments[i].getSize();
		}
		code.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, field.name, field.desc));
		return code;
	}
}
