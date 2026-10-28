package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.ConstructorInjects;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * What one pre-flight run reads the game with, shared by the checker and every repair family: the game's, the
 * installed jars' and vanilla's classes (each parsed once per run), the platform's Mixin as the run judges it, and
 * what the run collects (the installed classes it read, the access transformers repairs need).
 */
final class PreflightClasses {
	final ClassIndex game;

	/** Classes {@link #declaration} walked, NeoForge's among them. */
	final Map<String, Optional<ClassNode>> codeCache = new HashMap<>();

	final InstalledClasses installed;

	final ClassBytesSource modClasses;

	final ClassBytesSource untransformable;

	final boolean clientSide;

	final boolean missingTargetsFatal;

	/** Whether the running Mixin applies injectors in interface mixins (INTERFACE-1: Forge 47's doesn't). */
	final boolean interfaceInjectors;

	/** Where the running Mixin takes an {@code @Inject} into a constructor (CTOR-2). */
	final ConstructorInjects constructorInjects;

	/** Whether the running Mixin knows arguments by their names where a {@code @ModifyVariable} takes arguments only (ARGS-1). */
	final boolean argumentNames;

	/** Whether the running Mixin refuses an {@code @Inject} whose handler is static where its target isn't (LAMBDA-6: Forge 47's does). */
	final boolean exactStaticInjects;

	/** The platform's loader, as messages name it ({@code NeoForge}, {@code Forge}). */
	final String loader;

	/** Vanilla's code of the game, or {@link ClassBytesSource#EMPTY} (VANILLA-1). */
	final ClassBytesSource vanilla;

	final Map<String, Optional<ClassNode>> vanillaCache = new HashMap<>();

	final Map<String, Optional<ClassNode>> targetCache = new HashMap<>();

	/**
	 * {@link MixinPreflight#independentHandlers} of each mixin as it was read: a repair moving an injector to a bridge (added to the
	 * mixin once all are made) takes its handler out of the mixin's injectors, so a later handler sharing its state would
	 * look independent.
	 */
	final Map<ClassNode, Set<String>> independent = new IdentityHashMap<>();

	/** The installed classes the checks read, with their fingerprints. */
	final Map<String, String> installedRead = new TreeMap<>();

	/** Access transformer lines {@link MixinPreflight#repairs} needs, by the method they make public. */
	final Set<String> widened = new TreeSet<>();

	/** The libraries' classes (the JDK, DataFixerUpper, ...), for the supertypes of the classes a game method calls. */
	ClassIndex libraries;

	PreflightClasses(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects) {
		this.interfaceInjectors = interfaceInjectors;
		this.constructorInjects = constructorInjects;
		this.argumentNames = argumentNames;
		this.exactStaticInjects = exactStaticInjects;
		this.game = game;
		this.installed = installed;
		this.modClasses = modClasses;
		this.untransformable = untransformable;
		this.clientSide = clientSide;
		this.missingTargetsFatal = missingTargetsFatal;
		this.vanilla = vanilla;
		this.loader = loader;
	}

	/** Whether {@code type} is, extends or implements {@code supertype}, as far as the game's and the libraries' classes tell. */
	boolean isSubtype(String type, String supertype) {
		if (libraries == null) libraries = new ClassIndex(untransformable);
		Set<String> seen = new HashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>(List.of(type));
		while (!queue.isEmpty()) {
			String name = queue.poll();
			if (name.equals(supertype)) return true;
			if (!seen.add(name)) continue;
			ClassIndex.ClassInfo info = game.contains(name) ? game.get(name) : libraries.get(name);
			if (info == null) continue;
			if (info.superName() != null) queue.add(info.superName());
			queue.addAll(info.interfaces());
		}
		return false;
	}

	/** Vanilla's version of a game class (parsed once per pre-flight), or null. */
	ClassNode vanillaClass(String internalName) {
		return vanillaCache.computeIfAbsent(internalName, n -> Optional.ofNullable(vanilla.readNode(n, 0))).orElse(null);
	}

	/**
	 * The one call, in {@code methods}, of a method NeoForge declares (in its own classes, or as a default method of its
	 * extension interfaces) that makes the named call, itself or through other methods NeoForge declares: as
	 * {@code BlockBehaviour.onExplosionHit} removing the block through {@code IBlockStateExtension.onBlockExploded} and
	 * {@code IBlockExtension.onBlockExploded} (Ledger's explosion logging). Null when none or several do.
	 */
	MethodInsnNode callLeadingTo(List<MethodNode> methods, String owner, String name, String desc) {
		MethodInsnNode found = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				if (found != null && found.owner.equals(call.owner) && found.name.equals(call.name) && found.desc.equals(call.desc)) continue;
				Code code = declaration(call.owner, call.name, call.desc);
				if (code == null || !code.owner().startsWith(MixinNames.NEOFORGE_PACKAGE) || !leadsTo(code, owner, name, desc, 2)) continue;
				if (found != null) return null;
				found = call;
			}
		}
		return found;
	}

	/** Whether the code makes the call, itself or, to {@code depth}, through methods NeoForge declares that it calls. */
	boolean leadsTo(Code code, String owner, String name, String desc, int depth) {
		for (AbstractInsnNode insn : code.method().instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) return true;
			if (depth <= 1) continue;
			Code next = declaration(call.owner, call.name, call.desc);
			if (next != null && next.owner().startsWith(MixinNames.NEOFORGE_PACKAGE) && leadsTo(next, owner, name, desc, depth - 1)) return true;
		}
		return false;
	}

	/** A class of the game or NeoForge with its code (parsed once per pre-flight), or null. */
	ClassNode codeOf(String internalName) {
		return codeCache.computeIfAbsent(internalName, n -> Optional.ofNullable(game.readNode(n))).orElse(null);
	}

	/** A method with code and the class declaring it. */
	record Code(String owner, MethodNode method) {
	}

	/**
	 * The code a call of {@code owner.name(desc)} runs unless a subclass overrides it: the method in the class or its
	 * superclasses, else a default method of an interface they implement, as far as the game's classes go. Null when
	 * none has code.
	 */
	Code declaration(String owner, String name, String desc) {
		List<String> interfaces = new ArrayList<>();
		for (String c = owner; c != null; ) {
			ClassNode node = codeOf(c);
			if (node == null) break; // the JDK's, or a library's
			MethodNode m = PreflightAsm.declared(node, name, desc);
			if (m != null) return m.instructions != null && m.instructions.size() > 0 ? new Code(c, m) : null;
			interfaces.addAll(node.interfaces);
			c = node.superName;
		}
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < interfaces.size(); i++) {
			String itf = interfaces.get(i);
			if (!seen.add(itf)) continue;
			ClassNode node = codeOf(itf);
			if (node == null) continue;
			MethodNode m = PreflightAsm.declared(node, name, desc);
			if (m != null && m.instructions != null && m.instructions.size() > 0) return new Code(itf, m);
			interfaces.addAll(node.interfaces);
		}
		return null;
	}

	/** Whether the static method makes the call, in its own code or, to {@code depth}, in static methods of its class it calls. */
	boolean makesCall(String hookOwner, String hookName, String hookDesc, String owner, String name, String desc, int depth) {
		ClassNode hookClass = game.readNode(hookOwner);
		MethodNode hook = hookClass == null ? null : PreflightAsm.declared(hookClass, hookName, hookDesc);
		if (hook == null || (hook.access & Opcodes.ACC_STATIC) == 0 || hook.instructions == null) return false;
		for (AbstractInsnNode insn : hook.instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) return true;
			if (depth > 1 && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(hookOwner)
					&& makesCall(hookOwner, call.name, call.desc, owner, name, desc, depth - 1)) return true;
		}
		return false;
	}

	/** Whether the game declares the named method static (one it doesn't declare is taken as an instance method). */
	boolean isStaticCall(String owner, String name, String desc) {
		ClassNode node = game.readNode(owner);
		MethodNode method = node == null ? null : PreflightAsm.declared(node, name, desc);
		return method != null && (method.access & Opcodes.ACC_STATIC) != 0;
	}

	/** A Minecraft class as the game has it (parsed once per pre-flight), or null when absent or not a game class. */
	ClassNode gameTarget(String internalName) {
		return PreflightAsm.isGameClass(internalName) ? targetCache.computeIfAbsent(internalName, t -> Optional.ofNullable(game.readNode(t))).orElse(null) : null;
	}

	/** A class of an installed jar (parsed once per pre-flight), recorded with its supertypes there; null when unreadable. */
	ClassNode installedTarget(String internalName) {
		Deque<String> queue = new ArrayDeque<>(List.of(internalName));
		while (!queue.isEmpty()) {
			String name = queue.poll();
			if (installedRead.containsKey(name) || installed.owner(name) == null) continue;
			String fingerprint;
			try {
				fingerprint = installed.fingerprint(name);
			} catch (IOException e) {
				fingerprint = "";
			}
			installedRead.put(name, fingerprint);
			ClassIndex.ClassInfo info = game.get(name);
			if (info == null) continue;
			if (info.superName() != null) queue.add(info.superName());
			queue.addAll(info.interfaces());
		}
		return targetCache.computeIfAbsent(internalName, t -> Optional.ofNullable(game.readNode(t))).orElse(null);
	}

	/**
	 * The handlers (name and descriptor) of a mixin's injectors that share none of its own fields with its other
	 * injectors: no other injector's handler uses a field they use, themselves or through the mixin's own methods they
	 * call. Such a handler can be pointed elsewhere alone, as nothing it keeps is another injector's (SPLIT-2). Its state
	 * may be the mixin's other members': architectury's entity manager mixin keeps the level its duck interface is given
	 * for its hook alone. A stateless handler ({@link MixinPreflight#statelessHandlers}) is one.
	 */
	Set<String> independentOf(ClassNode mixin) {
		return independent.computeIfAbsent(mixin, MixinPreflight::independentHandlers);
	}

	boolean isUntransformable(String target) {
		try {
			return untransformable.read(target) != null;
		} catch (IOException e) {
			return false;
		}
	}
}
