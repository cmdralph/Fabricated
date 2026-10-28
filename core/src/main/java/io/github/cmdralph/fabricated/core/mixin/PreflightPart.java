package io.github.cmdralph.fabricated.core.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.ConstructorInjects;

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

	/** {@link PreflightClasses#isUntransformable} */
	boolean isUntransformable(String target) {
		return classes.isUntransformable(target);
	}
}
