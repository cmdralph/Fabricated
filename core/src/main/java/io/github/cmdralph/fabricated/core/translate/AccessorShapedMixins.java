package io.github.cmdralph.fabricated.core.translate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InterfaceInjects;

/**
 * What leaving out some of an interface mixin's methods, or repairing them, makes of it for Mixin (CORPUS-76, CORPUS-96).
 * Mixin takes an interface
 * mixin whose methods are all accessors and invokers (synthetic ones aside) for an accessor mixin, and makes its targets
 * implement the mixin itself, where the targets of an ordinary interface mixin get the mixin's own interfaces, less the
 * target itself (Mixin 0.8.5 to Fabric's, {@code MixinInfo.getVariant} and {@code MixinApplicatorInterface}). Leaving
 * out the other methods (injectors, where the running Mixin applies none in interface mixins, INTERFACE-1; or injectors
 * pre-flight finds would fail) turns the one into the other: AppleSkin's {@code TooltipComponentMixin}, which extends its
 * target {@code ClientTooltipComponent} for its two static injectors, then made {@code ClientTooltipComponent} its own
 * supertype, and Forge 47's client stopped with a {@code ClassCircularityError} as the interface loaded. Making its
 * injectors the synthetic methods Fabricated applies ({@link InterfaceInjects}, INTERFACE-1) does the same.
 * <p>
 * A mixin left out of methods with nothing for its targets (no accessor, no handler Fabricated applies, and no
 * interface but its targets) is left out whole, as it adds nothing once those methods are gone; one a config's plugin
 * adds at runtime, which no config lists, can't be, and one left with accessors, such handlers or other interfaces stays
 * an accessor mixin. Either keeps its targets out of its own interfaces, which an ordinary interface mixin adds nothing
 * with.
 */
final class AccessorShapedMixins {
	private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

	private AccessorShapedMixins() {
	}

	enum Outcome {
		/** It stays as it was: Mixin takes it as it did. */
		UNCHANGED,
		/** It adds nothing to its targets: its config no longer lists it. */
		LEAVE_OUT,
		/** It stays, without its targets among its interfaces ({@link Result#bytes()}). */
		REWRITTEN
	}

	/** @param bytes the mixin to use ({@link Outcome#REWRITTEN}), or {@code after}; {@code note} says why, or is null */
	record Result(Outcome outcome, byte[] bytes, String note) {
	}

	/**
	 * @param before the mixin with all its methods
	 * @param after  the mixin without those left out
	 * @param listed whether a config lists it (a plugin's mixin isn't, and can't be left out)
	 */
	static Result of(byte[] before, byte[] after, boolean listed) {
		return of(before, after, listed, "With the methods above left out");
	}

	/**
	 * What pre-flight's repairs make of the mixin: its {@code @Inject}s made handlers Fabricated applies leave it no
	 * method Mixin takes for an ordinary one. It is never left out: those handlers are what it adds.
	 *
	 * @param before the mixin as the mod has it
	 * @param after  the mixin repaired
	 */
	static Result ofRepaired(byte[] before, byte[] after) {
		return of(before, after, false, "With its injectors made handlers Fabricated applies (INTERFACE-1)");
	}

	private static Result of(byte[] before, byte[] after, boolean listed, String cause) {
		// Most mixins are class mixins: the header says so without reading the classes.
		if ((new ClassReader(after).getAccess() & Opcodes.ACC_INTERFACE) == 0) return new Result(Outcome.UNCHANGED, after, null);
		ClassNode original = read(before);
		ClassNode left = read(after);
		if (!ordinary(original) || ordinary(left)) return new Result(Outcome.UNCHANGED, after, null);
		List<String> targets = ModTranslator.mixinTargets(left);
		Set<String> own = new LinkedHashSet<>(left.interfaces);
		Set<String> self = new LinkedHashSet<>(own);
		self.retainAll(targets);
		own.removeAll(targets);
		boolean adds = left.methods.stream().anyMatch(m -> accessor(m) || InterfaceInjects.marked(m));
		String dottedTargets = String.join(", ", targets).replace('/', '.');
		if (listed && !adds && own.isEmpty()) {
			return new Result(Outcome.LEAVE_OUT, after, "Left out whole: with the methods above left out it adds nothing to " + dottedTargets
					+ ", and Mixin would take it for an accessor mixin and make " + dottedTargets + " implement it"
					+ (self.isEmpty() ? "" : ", which, as it extends " + String.join(", ", self).replace('/', '.') + ", would make that its own supertype"));
		}
		if (self.isEmpty()) return new Result(Outcome.UNCHANGED, after, null);
		left.interfaces.removeAll(self);
		if (left.signature != null) left.signature = withoutInterfaces(left.signature, self);
		ClassWriter writer = new ClassWriter(0);
		left.accept(writer);
		return new Result(Outcome.REWRITTEN, writer.toByteArray(), cause + ", Mixin takes it for an accessor mixin and makes "
				+ dottedTargets + " implement it; it no longer extends " + String.join(", ", self).replace('/', '.')
				+ ", which would make that its own supertype (CORPUS-76, CORPUS-96)");
	}

	/** Whether Mixin takes the interface for an ordinary interface mixin: it declares a method that is neither an accessor nor synthetic. */
	private static boolean ordinary(ClassNode mixin) {
		return mixin.methods.stream().anyMatch(m -> (m.access & Opcodes.ACC_SYNTHETIC) == 0 && !accessor(m));
	}

	/** Whether Mixin takes the method for an accessor or invoker: their annotations are visible ones. */
	private static boolean accessor(MethodNode method) {
		return Annotations.find(method.visibleAnnotations, ACCESSOR) != null || Annotations.find(method.visibleAnnotations, INVOKER) != null;
	}

	private static ClassNode read(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	/** A class signature without the interfaces named (internal names); its type parameters and superclass stay as they are. */
	static String withoutInterfaces(String signature, Set<String> removed) {
		List<NamedWriter> interfaces = new ArrayList<>();
		SignatureWriter head = new SignatureWriter() {
			@Override
			public SignatureVisitor visitInterface() {
				NamedWriter itf = new NamedWriter();
				interfaces.add(itf);
				return itf;
			}
		};
		new SignatureReader(signature).accept(head);
		StringBuilder out = new StringBuilder(head.toString());
		for (NamedWriter itf : interfaces) {
			if (!removed.contains(itf.name)) out.append(itf);
		}
		return out.toString();
	}

	/** A signature writer that remembers the outermost class type it writes. */
	private static final class NamedWriter extends SignatureWriter {
		private String name;

		@Override
		public void visitClassType(String name) {
			if (this.name == null) this.name = name;
			super.visitClassType(name);
		}
	}
}
