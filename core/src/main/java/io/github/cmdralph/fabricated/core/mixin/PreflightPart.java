package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.ConstructorInjects;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A part of one pre-flight run (the checker, the repairs and each repair family): what the run reads the game with
 * ({@link PreflightClasses}), under the names the parts use.
 */
abstract class PreflightPart {
	final PreflightClasses classes;
	final ClassIndex game;
	final InstalledClasses installed;
	final ClassBytesSource modClasses;
	final ClassBytesSource untransformable;
	final boolean clientSide;
	final boolean missingTargetsFatal;
	final boolean interfaceInjectors;
	final ConstructorInjects constructorInjects;
	final boolean argumentNames;
	final boolean exactStaticInjects;
	final boolean enumMixins;
	final boolean readsLocalTypes;
	final boolean seesMixinFields;
	final boolean handsOffConstructorArguments;
	final boolean overlapsWideArguments;
	final boolean keepsLocalsPastScope;
	final boolean restrictsInitialisers;
	final String loader;
	final ClassBytesSource vanilla;
	final Map<ClassNode, Set<String>> independent;
	final Map<String, String> installedRead;
	final Set<String> widened;

	PreflightPart(PreflightClasses classes) {
		this.classes = classes;
		this.game = classes.game;
		this.installed = classes.installed;
		this.modClasses = classes.modClasses;
		this.untransformable = classes.untransformable;
		this.clientSide = classes.clientSide;
		this.missingTargetsFatal = classes.missingTargetsFatal;
		this.interfaceInjectors = classes.interfaceInjectors;
		this.constructorInjects = classes.constructorInjects;
		this.argumentNames = classes.argumentNames;
		this.exactStaticInjects = classes.exactStaticInjects;
		this.enumMixins = classes.enumMixins;
		this.readsLocalTypes = classes.readsLocalTypes;
		this.seesMixinFields = classes.seesMixinFields;
		this.handsOffConstructorArguments = classes.handsOffConstructorArguments;
		this.overlapsWideArguments = classes.overlapsWideArguments;
		this.keepsLocalsPastScope = classes.keepsLocalsPastScope;
		this.restrictsInitialisers = classes.restrictsInitialisers;
		this.loader = classes.loader;
		this.vanilla = classes.vanilla;
		this.independent = classes.independent;
		this.installedRead = classes.installedRead;
		this.widened = classes.widened;
	}

	/** {@link PreflightClasses#isSubtype} */
	boolean isSubtype(String type, String supertype) {
		return classes.isSubtype(type, supertype);
	}

	/** {@link PreflightClasses#vanillaClass} */
	ClassNode vanillaClass(String internalName) {
		return classes.vanillaClass(internalName);
	}

	/** {@link PreflightClasses#callLeadingTo} */
	MethodInsnNode callLeadingTo(List<MethodNode> methods, String owner, String name, String desc) {
		return classes.callLeadingTo(methods, owner, name, desc);
	}

	/** {@link PreflightClasses#codeOf} */
	ClassNode codeOf(String internalName) {
		return classes.codeOf(internalName);
	}

	/** {@link PreflightClasses#declaration} */
	PreflightClasses.Code declaration(String owner, String name, String desc) {
		return classes.declaration(owner, name, desc);
	}

	/** {@link PreflightClasses#makesCall} */
	boolean makesCall(String hookOwner, String hookName, String hookDesc, String owner, String name, String desc, int depth) {
		return classes.makesCall(hookOwner, hookName, hookDesc, owner, name, desc, depth);
	}

	/** {@link PreflightClasses#isStaticCall} */
	boolean isStaticCall(String owner, String name, String desc) {
		return classes.isStaticCall(owner, name, desc);
	}

	/** {@link PreflightClasses#gameTarget} */
	ClassNode gameTarget(String internalName) {
		return classes.gameTarget(internalName);
	}

	/** {@link PreflightClasses#installedTarget} */
	ClassNode installedTarget(String internalName) {
		return classes.installedTarget(internalName);
	}

	/** {@link PreflightClasses#independentOf} */
	Set<String> independentOf(ClassNode mixin) {
		return classes.independentOf(mixin);
	}

	/** {@link PreflightClasses#stateGroupsOf} */
	Map<String, MixinPreflight.StateGroup> stateGroupsOf(ClassNode mixin) {
		return classes.stateGroupsOf(mixin);
	}

	/** {@link PreflightClasses#isUntransformable} */
	boolean isUntransformable(String target) {
		return classes.isUntransformable(target);
	}

	/**
	 * Whether an injector is the one alternative of its group that can apply, on vanilla's classes (VANILLA-1) as on this
	 * build's: its points are in a method it selects in vanilla's version of each target, so it applies on Fabric, and each
	 * other member of its group selects no method in vanilla's classes or this build's, or none with its points. The
	 * group is then that alternative alone, on Fabric as here, and repairing it is repairing an injector alone (LAMBDA-14).
	 * ReplayMod's jGui hooks a screen's char typed lambdas with two alternatives each, vanilla's lambda and OptiFine's
	 * ({@code lambda$onCharEvent$6}, by its own name), which no build here has. A member that can't be told (a pattern,
	 * a slice or an expression pre-flight doesn't read) makes it not judged.
	 */
	boolean soleAlternative(ClassNode mixin, MethodNode handler, AnnotationNode own, List<ClassNode> targets, Context ctx) {
		String group = PreflightAsm.group(handler);
		if (group == null) return false;
		List<ClassNode> originals = new ArrayList<>();
		for (ClassNode target : targets) {
			ClassNode original = vanillaClass(target.name);
			if (original == null) return false;
			originals.add(original);
		}
		for (ClassNode original : originals) {
			if (appliesNowhere(own, original, ctx) || !appliesIn(own, original, ctx)) return false;
		}
		for (MethodNode other : mixin.methods) {
			if (other == handler || !group.equals(PreflightAsm.group(other))) continue;
			AnnotationNode injector = Annotations.all(other).stream().filter(a -> PreflightAsm.isInjector(a.desc)).findFirst().orElse(null);
			if (injector == null) return false;
			for (ClassNode original : originals) {
				if (!appliesNowhere(injector, original, ctx)) return false;
			}
			for (ClassNode target : targets) {
				if (!appliesNowhere(injector, target, ctx)) return false;
			}
		}
		return true;
	}

	/** Whether an injector's points are in each method it selects in {@code owner}, and it selects one there. */
	static boolean appliesIn(AnnotationNode injector, ClassNode owner, Context ctx) {
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, owner, ctx);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		return !methods.isEmpty() && methods.stream().allMatch(m -> InjectionPoints.pointsIn(ats, slices, m, ctx) == InjectionPoints.Verdict.FOUND);
	}

	/**
	 * Whether an injector applies to nothing of {@code owner}: each of its selectors, a method's name (with or without its
	 * descriptor), names no method there, or only methods its points are missing from. False where that can't be told.
	 */
	static boolean appliesNowhere(AnnotationNode injector, ClassNode owner, Context ctx) {
		if (Annotations.value(injector, "target") != null || !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.isEmpty()) return false;
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		for (Object selector : selectors) {
			if (!(selector instanceof String raw)) return false;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) return false;
			for (MethodNode m : owner.methods) {
				if (ref.matchesMethod(m.name, m.desc) && (ats.isEmpty() || InjectionPoints.pointsIn(ats, slices, m, ctx) != InjectionPoints.Verdict.MISSING)) return false;
			}
		}
		return true;
	}
}
