package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.bytecode.EnvironmentStripper;
import io.github.cmdralph.fabricated.core.bytecode.VanillaSeams;
import io.github.cmdralph.fabricated.core.mixin.NativeModMixins;
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;

/**
 * The class transforms Fabric mods need at class load, whichever loader runs them (NeoForge's class processors, Forge's
 * transformation service). Before Mixin:
 * <ul>
 *   <li>the members of Fabric mods' classes that {@code @Environment} reserves for the other side are stripped;</li>
 *   <li>class-tweaker {@code inject-interface} directives are applied: the interface is added to the target class, and
 *       to its generic signature so reflection sees the parameterised type. Mods implement the interface's methods
 *       through their own mixins, as on Fabric.</li>
 *   <li>the static handlers of other mods' mixins into Fabric mods' classes that the running Mixin would refuse for
 *       their instance targets are made instance handlers, as Mixin reads the mixin through the platform's transformers
 *       ({@link NativeModMixins}, CORPUS-87).</li>
 *   <li>vanilla's methods the platform took out of a game class Fabric mods' mixins target are put back, and its code
 *       calls them where vanilla does, so the mods' hooks of them apply ({@link VanillaSeams}, SEAM-1); and vanilla's
 *       calls the platform makes through its block state's extension or its hooks, where its code then does what vanilla's
 *       does (SEAM-3's block breaking, SEAM-4's harvest checks, SEAM-6's item checks).</li>
 * </ul>
 * After Mixin, {@link DefaultBridge}s are declared ({@link #declareDefaultBridges}). A platform where nothing of
 * Fabricated's runs after Mixin on every class (NeoForge 21.1's ModLauncher) declares them before Mixin too, for the
 * classes no mixin targets, and takes them out as Mixin starts on a class it applies mixins to, to declare them again
 * once it is done ({@link #removeDefaultBridges}). Each change is reported to an audit consumer as an activity and its
 * detail.
 */
public final class FabricClassTransforms {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private FabricClassTransforms() {
	}

	/** Whether {@link #beforeMixin} may change the class (internal name). */
	public static boolean transformsBeforeMixin(FabricatedState state, String internalName) {
		return state.environmentClasses().contains(internalName) || state.injectedInterfaces().containsKey(internalName)
				|| state.overridableMethods().containsKey(internalName) || state.instanceHandlers().containsKey(internalName)
				|| state.restoredSeams().contains(internalName);
	}

	/** Every class {@link #beforeMixin} may change, for a platform that names its targets up front. */
	public static Set<String> classesBeforeMixin(FabricatedState state) {
		Set<String> classes = new TreeSet<>(state.environmentClasses());
		classes.addAll(state.injectedInterfaces().keySet());
		classes.addAll(state.overridableMethods().keySet());
		classes.addAll(state.instanceHandlers().keySet());
		classes.addAll(state.restoredSeams());
		return classes;
	}

	/** Whether {@link #declareDefaultBridges} may change the class (internal name). */
	public static boolean declaresDefaultBridges(FabricatedState state, String internalName) {
		return state.defaultBridges().containsKey(internalName);
	}

	/**
	 * Strips the other side's members, adds injected interfaces, removes {@code final} from the methods Fabric mods
	 * override where only the platform's build declares it (FINAL-2; the platform's own access transformers have run by
	 * then), makes instance handlers of another mod's mixin's static handlers (CORPUS-87) and puts back vanilla's methods
	 * the platform took out (SEAM-1) and its calls (SEAM-3, SEAM-4, SEAM-6, SEAM-8); whether the class changed.
	 */
	public static boolean beforeMixin(FabricatedState state, ClassNode node, BiConsumer<String, String> audit) {
		boolean changed = false;
		if (state.environmentClasses().contains(node.name) && EnvironmentStripper.strip(node, state.runtime().getEnvironmentType())) {
			audit.accept("fabricated:environment_strip", node.name);
			changed = true;
		}
		for (InjectedInterface injection : state.injectedInterfaces().getOrDefault(node.name, List.of())) {
			if (node.interfaces.contains(injection.interfaceName())) continue;
			if (node.signature == null && injection.genericSignature().indexOf('<') >= 0) {
				// A class without generic types has no signature; it needs one to carry the interface's type arguments.
				StringBuilder signature = new StringBuilder("L").append(node.superName).append(';');
				for (String itf : node.interfaces) signature.append('L').append(itf).append(';');
				node.signature = signature.toString();
			}
			node.interfaces.add(injection.interfaceName());
			if (node.signature != null) node.signature += "L" + injection.genericSignature() + ";";
			audit.accept("fabricated:inject_interface", injection.interfaceName());
			changed = true;
		}
		Set<String> overridable = state.overridableMethods().getOrDefault(node.name, Set.of());
		for (MethodNode method : node.methods) {
			if ((method.access & Opcodes.ACC_FINAL) == 0 || !overridable.contains(method.name + method.desc)) continue;
			method.access &= ~Opcodes.ACC_FINAL;
			audit.accept("fabricated:overridable", method.name);
			changed = true;
		}
		Set<String> handlers = state.instanceHandlers().getOrDefault(node.name, Set.of());
		if (!handlers.isEmpty() && NativeModMixins.applyInstanceHandlers(node, handlers)) {
			audit.accept("fabricated:instance_handler", String.join(", ", handlers));
			changed = true;
		}
		if (state.restoredSeams().contains(node.name)) {
			// All or nothing, never failing the class's loading: the class keeps its own code where the rewrite fails.
			VanillaSeams.Restoring seams = VanillaSeams.restoreWhole(node);
			if (seams.failure() != null) {
				LOGGER.error("Fabricated couldn't put vanilla's {} back in {} ({}): the class loads as the platform has it, so Fabric mods' hooks of"
						+ " them find nothing there", VanillaSeams.putsBackCalls(node.name) ? "calls" : "methods", node.name.replace('/', '.'),
						VanillaSeams.findings(node.name), seams.failure());
			} else if (!seams.restored().isEmpty()) {
				audit.accept("fabricated:vanilla_seam", String.join(", ", seams.restored()));
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Declares the methods of the class's {@link DefaultBridge}s: where an interface a Fabric mod adds brings a default
	 * method the class already has from another interface (CORPUS-11), the class declares it, calling the default it had,
	 * so the JVM has one to pick. A method the class declares by then (its mixins') is kept. Whether the class changed.
	 */
	public static boolean declareDefaultBridges(FabricatedState state, ClassNode node, BiConsumer<String, String> audit) {
		boolean changed = false;
		for (DefaultBridge bridge : state.defaultBridges().getOrDefault(node.name, List.of())) {
			if (node.methods.stream().anyMatch(m -> m.name.equals(bridge.name()) && m.desc.equals(bridge.descriptor()))) continue;
			node.methods.add(bridge.method(!bridge.owner().equals(node.superName)));
			audit.accept("fabricated:default_bridge", bridge.owner() + "." + bridge.name());
			changed = true;
		}
		return changed;
	}

	/**
	 * Takes out the class's {@link DefaultBridge} methods declared before Mixin, so its mixins apply to the class without
	 * them, as where bridges are declared only after Mixin: a mixin declaring such a method adds its own, which Mixin
	 * would otherwise refuse where the mixin's config requires {@code @Overwrite} to replace a method. Only the methods
	 * Fabricated made go, by their {@link DefaultBridge#MARKER}. Whether the class changed.
	 */
	public static boolean removeDefaultBridges(FabricatedState state, ClassNode node) {
		List<DefaultBridge> bridges = state.defaultBridges().getOrDefault(node.name, List.of());
		if (bridges.isEmpty()) return false;
		return node.methods.removeIf(m -> DefaultBridge.isBridge(m) && bridges.stream().anyMatch(b -> b.name().equals(m.name) && b.descriptor().equals(m.desc)));
	}
}
