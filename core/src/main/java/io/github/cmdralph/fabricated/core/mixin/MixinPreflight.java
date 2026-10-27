package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;

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
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Static verification of a mixin config against the runtime game bytecode, before any mod code runs.
 *
 * <p>The verifier only reports what it can prove. A finding of severity {@link Severity#FAIL} means the
 * mixin would make Mixin throw during class loading: a missing target class (non-{@code @Pseudo}) or
 * missing {@code @Shadow}/{@code @Overwrite}/{@code @Accessor}/{@code @Invoker} target in a
 * {@code required} config, or an injector whose effective {@code require} is at least 1 and whose
 * target method or injection point does not exist (inside its {@code @Slice}, computed as Mixin does), or a
 * target class that NeoForge loads without mixin support (the JDK, libraries such as DataFixerUpper), where
 * the mixin would silently never apply. Targets in the jars installed next to the Fabric mods (Fabricated's Fabric
 * API, NeoForge mods: an addon's mixins into the NeoForge build of the mod it extends) are checked as game classes are,
 * except that a missing one is not reported (it may be in another Fabric mod). Selectors and injection points it cannot
 * evaluate (wildcards, slice bounds other than member references or {@code HEAD}, custom points, targets in other
 * Fabric mods) are never reported as failures. When the config declares a plugin, which may disable mixins at runtime,
 * a failure is {@link Severity#LEFT_OUT} instead: the failing injector (or the mixin) is left out rather than the mod
 * disabled. So is a failure of a self-contained mixin, one nothing outside it can call (it adds no interface to its
 * target and has no accessor or invoker) (LEAVE-1): only the failing injector is left out when the mixin keeps no state
 * of its own (it adds no fields), the whole mixin otherwise, so that injectors sharing its fields apply all or none. An
 * enum mixin adding constants to one of NeoForge's extensible enums always fails: the
 * mod's own classes read those constants.
 */
public final class MixinPreflight {
	private static final String PSEUDO = "Lorg/spongepowered/asm/mixin/Pseudo;";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;";
	private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";
	static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
	private static final String INTRINSIC = "Lorg/spongepowered/asm/mixin/Intrinsic;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
	private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
	private static final Set<String> WRAP_WITH_CONDITION = Set.of("Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");
	private static final String MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
	/**
	 * Fabric Loader's own calls, which its launcher adds to the game where it runs mods' entrypoints, to the call NeoForge's
	 * build makes at that step, in the same method: Fabricated runs those entrypoints in NeoForge's mod loading. Mods inject
	 * after them to run once every mod has initialized (owo-lib freezes its registries after the server's, HOOK-1).
	 */
	/** The receiver, among the values {@link #insertedArgument(ClassNode, String, String, String)} sees pushed. */
	private static final int THIS = Integer.MIN_VALUE;
	private static final Map<String, String> LOADER_HOOKS = Map.of(
			"Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V",
			"Lnet/neoforged/neoforge/server/loading/ServerModLoader;load(Z)V");
	/** NeoForge's marker for the enums only its own enum extension system may extend. */
	private static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";
	private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
	/** The injectors whose {@code at} is one injection point in NeoForge 1.21.11's Mixin and an array in Fabric's. */
	private static final Set<String> SINGLE_POINT_INJECTORS = Set.of(REDIRECT, MODIFY_VARIABLE, "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;");
	private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
	static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
	/** The name prefix javac gives the fields holding the local variables an anonymous or local class captures. */
	private static final String CAPTURED_PREFIX = "val$";
	/** javac's name for a lambda body: the enclosing method's name and the lambda's index in the class. */
	private static final Pattern LAMBDA = Pattern.compile("lambda\\$(.+)\\$\\d+");
	private static final Set<String> GAME_PREFIXES = Set.of("net/minecraft/", "com/mojang/blaze3d/");
	private static final String NEOFORGE_PACKAGE = "net/neoforged/";
	private static final String CALLBACK_INFO_RETURNABLE = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";

	private final ClassIndex game;
	/** Classes {@link #declaration} walked, NeoForge's among them. */
	private final Map<String, Optional<ClassNode>> codeCache = new HashMap<>();
	private final InstalledClasses installed;
	private final ClassBytesSource modClasses;
	private final ClassBytesSource untransformable;
	private final boolean clientSide;
	private final boolean missingTargetsFatal;
	/** Whether the running Mixin applies injectors in interface mixins (INTERFACE-1: Forge 47's doesn't). */
	private final boolean interfaceInjectors;
	/** Where the running Mixin takes an {@code @Inject} into a constructor (CTOR-2). */
	private final ConstructorInjects constructorInjects;
	/** Whether the running Mixin knows arguments by their names where a {@code @ModifyVariable} takes arguments only (ARGS-1). */
	private final boolean argumentNames;

	/** Where the running Mixin takes an {@code @Inject} into a constructor (CTOR-2). */
	public enum ConstructorInjects {
		/** Anywhere after the call to the delegate constructor, as Fabric's Mixin does; not judged. */
		AFTER_DELEGATE,
		/** At a return, or at a point marked {@code unsafe} (SpongePowered's 0.8.6 and 0.8.7). */
		UNSAFE_POINTS,
		/** At a return only (SpongePowered's 0.8.5, whose points take no {@code unsafe}). */
		RETURNS_ONLY
	}
	/** The platform's loader, as messages name it ({@code NeoForge}, {@code Forge}). */
	private final String loader;
	/** Vanilla's code of the game, or {@link ClassBytesSource#EMPTY} (VANILLA-1). */
	private final ClassBytesSource vanilla;
	private final Map<String, Optional<ClassNode>> vanillaCache = new HashMap<>();
	private final Map<String, Optional<ClassNode>> targetCache = new HashMap<>();
	/**
	 * {@link #independentHandlers} of each mixin as it was read: a repair moving an injector to a bridge (added to the
	 * mixin once all are made) takes its handler out of the mixin's injectors, so a later handler sharing its state would
	 * look independent.
	 */
	private final Map<ClassNode, Set<String>> independent = new IdentityHashMap<>();
	/** The installed classes the checks read, with their fingerprints. */
	private final Map<String, String> installedRead = new TreeMap<>();
	/** Access transformer lines {@link #repairs} needs, by the method they make public. */
	private final Set<String> widened = new TreeSet<>();
	/** The libraries' classes (the JDK, DataFixerUpper, ...), for the supertypes of the classes a game method calls. */
	private ClassIndex libraries;

	/**
	 * @param game                runtime game classes (Minecraft as patched by NeoForge), and {@code installed}'s classes
	 * @param installed           the classes of the jars installed next to the Fabric mods (Fabricated's Fabric API,
	 *                            NeoForge mods): mixins targeting them are checked as those into the game are
	 * @param modClasses          the mod's own classes (already remapped to runtime names)
	 * @param untransformable     classes that exist at runtime but are loaded outside NeoForge's transforming class
	 *                            loader (the JDK and libraries such as DataFixerUpper); mixins into them never apply
	 * @param clientSide          whether the physical side is the client
	 * @param missingTargetsFatal whether Mixin rejects a mixin whose target class doesn't exist (in a required config),
	 *                            as it does from compatibility level 0.17.4; before, it only skips that target
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, "NeoForge");
	}

	/** @param loader the platform's loader, as messages name it ({@code NeoForge}, {@code Forge}) */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, true);
	}

	/**
	 * @param interfaceInjectors whether the running Mixin applies injectors in interface mixins and takes their
	 *                           non-public methods, as it does from 0.8.6 (INTERFACE-1)
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				ConstructorInjects.AFTER_DELEGATE);
	}

	/** @param constructorInjects where the running Mixin takes an {@code @Inject} into a constructor (CTOR-2) */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, true);
	}

	/**
	 * @param argumentNames whether the running Mixin knows a method's arguments by their names where a
	 *                      {@code @ModifyVariable} takes arguments only, as Fabric's does from 0.17.0 (ARGS-1)
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames) {
		this.interfaceInjectors = interfaceInjectors;
		this.constructorInjects = constructorInjects;
		this.argumentNames = argumentNames;
		this.game = game;
		this.installed = installed;
		this.modClasses = modClasses;
		this.untransformable = untransformable;
		this.clientSide = clientSide;
		this.missingTargetsFatal = missingTargetsFatal;
		this.vanilla = vanilla;
		this.loader = loader;
	}

	/**
	 * @param configName file name of the config (for messages)
	 * @param skip       mixin names (relative to the package) the user disabled
	 */
	public List<PreflightFinding> check(String configName, MixinConfigFile config, Refmap refmap, Set<String> skip) {
		List<PreflightFinding> out = new ArrayList<>();
		boolean hasPlugin = config.plugin() != null && !config.plugin().isBlank();
		for (String mixin : config.mixinsFor(clientSide)) {
			if (skip.contains(mixin)) continue;
			String internal = config.internalName(mixin);
			ClassNode node = modClasses.readNode(internal, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			boolean selfContained = node != null && selfContained(node);
			Set<String> stateless = node == null || selfContained ? Set.of()
					: statelessHandlers(modClasses.readNode(internal, ClassReader.SKIP_FRAMES));
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, hasPlugin, selfContained,
					selfContained && addsFields(node), stateless, out, null, loader);
			if (node == null) {
				ctx.classLevel("Mixin class " + ctx.mixinDotted() + " listed in " + configName + " is missing from the mod jar",
						"The mod jar is incomplete or corrupted; re-download it.");
				continue;
			}
			checkMixin(node, ctx);
		}
		if (hasPlugin && out.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT)) {
			out.add(new PreflightFinding(Severity.INFO, configName, null, "This config uses a mixin plugin (" + config.plugin()
					+ ") that may disable mixins at runtime, so an injector that would crash the game is left out instead of disabling the mod.", ""));
		}
		return out;
	}

	/**
	 * The installed classes {@link #check} read (the targets in installed jars, and their supertypes there), and the
	 * targets no installed jar has, with their {@link InstalledClasses#fingerprint}s (empty for an absent class): its
	 * results hold while those are the same.
	 */
	public Map<String, String> installedClasses() {
		return Collections.unmodifiableMap(new TreeMap<>(installedRead));
	}

	/**
	 * The access transformer lines the mixins {@link #repairs} rewrote need: game methods made public, which their
	 * accessors and invokers now use.
	 */
	public Set<String> accessTransformers() {
		return Collections.unmodifiableSet(new TreeSet<>(widened));
	}

	/**
	 * Mixins that Mixin would reject but that can be pointed at what the game really has, before pre-flight checks them.
	 * Returns the mixin classes rewritten, by class file name; each repair is reported as a warning.
	 * <ul>
	 * <li>An accessor or invoker that every target already declares publicly, with the same descriptor (typically a
	 * method NeoForge added): Mixin cannot generate it, but the existing method does the job, so it becomes a
	 * synthetic {@code @Shadow} of it. (Synthetic, because Mixin treats an interface with any other non-accessor
	 * method as an interface mixin, which may only target interfaces.) Where a target declares it without public access
	 * (vanilla's {@code EditBox.isEditable()}, which JourneyMap's accessor of the field of that name clashes with), the
	 * mod's access transformer makes it public ({@link #accessTransformers}), and the same applies (ACC-1).</li>
	 * <li>An {@code @Accessor} of a local variable an anonymous class captured ({@code val$name}), where the game names
	 * that field after the decompiled variable instead (NeoForge's 1.21.11 build, for example): it is pointed at the
	 * one captured field of the same type in each target.</li>
	 * <li>An {@code @Inject} that names an overloaded method without a descriptor, whose handler takes the parameters of
	 * exactly one of the overloads, while the first one (the one Mixin picks) has others: NeoForge's build orders methods
	 * differently. The selector is pinned to the overload the handler was written for.</li>
	 * <li>An injector whose method NeoForge turned into a delegate to a new overload of the same name that now holds the
	 * code (NeoForge keeps vanilla's signature that way): when the injection points occur only in that overload and the
	 * handler doesn't depend on the method's parameters, the injector is pointed at the overload.</li>
	 * <li>An injector at the end of a constructor NeoForge kept for vanilla's signature (only calling {@code this(...)}
	 * with NeoForge's defaults, and deprecated or appending arguments of NeoForge's types, as {@code BakedQuad}'s), which
	 * the game no longer calls: it is pointed at the constructor that one calls, which every caller now goes through. An
	 * {@code @Inject} handler taking the constructor's parameters gets them from a bridge taking the other one's.</li>
	 * <li>An {@code @Inject} or {@code @ModifyExpressionValue} at a call this game build makes through a subtype of the
	 * class named (the same inherited method, as NeoForge's conditional {@code Codec} for vanilla's {@code Decoder}):
	 * the injection point is pointed at that call.</li>
	 * <li>An injector at a call this game build makes to an overload taking one more argument (NeoForge's patches pass an
	 * item stack along that way), or at a constructor call that takes one more (a record NeoForge gave a component):
	 * the injection point is pointed at that call, with a bridge for a handler that takes the call's arguments.</li>
	 * <li>An {@code @Inject} that captures local variables and would crash when they differ
	 * ({@code CAPTURE_FAILHARD}, {@code CAPTURE_FAILEXCEPTION}): NeoForge's patches often add locals, so it is made to
	 * skip itself with Mixin's warning instead ({@code CAPTURE_FAILSOFT}, {@code require = 0}). Where the locals
	 * match, nothing changes.</li>
	 * <li>An injector targeting a lambda ({@code lambda$enclosing$3}) that the game numbers differently (NeoForge's
	 * 1.21.11 build is compiled from decompiled code, and NeoForge's patches add lambdas): it is pointed at the lambda
	 * created where vanilla's code creates the one named, or else at the one lambda of the same enclosing method with the
	 * same descriptor.</li>
	 * <li>A {@code @Redirect}, {@code @ModifyArg}, {@code @ModifyArgs} or {@code @ModifyVariable} whose {@code at} is an
	 * array of one ({@link #singleInjectionPoints}).</li>
	 * </ul>
	 */
	public Map<String, byte[]> repairs(String configName, MixinConfigFile config, Refmap refmap, Set<String> skip,
			List<PreflightFinding> out) {
		Map<String, byte[]> repaired = new TreeMap<>();
		for (String mixin : config.mixinsFor(clientSide)) {
			if (skip.contains(mixin)) continue;
			String internal = config.internalName(mixin);
			ClassNode node = modClasses.readNode(internal, 0);
			if (node == null) continue;
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, false, false, false, Set.of(), out, null, loader);
			independent.put(node, independentHandlers(node));
			boolean changed = singleInjectionPoints(node);
			for (String moved : anonymousTargetRepairs(node, ctx)) {
				changed = true;
				out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "Target class " + moved, ""));
			}
			List<ClassNode> targets = new ArrayList<>();
			for (String target : targetsOf(node, ctx)) {
				ClassNode targetNode = gameTarget(target);
				if (targetNode == null) {
					targets = List.of();
					break;
				}
				targets.add(targetNode);
			}
			if (targets.isEmpty()) {
				// Not a game class's: only the injection points are made single.
				if (changed) repaired.put(internal + ".class", write(node));
				continue;
			}
			List<MethodNode> bridges = new ArrayList<>();
			for (MethodNode method : node.methods) {
				for (AnnotationNode ann : Annotations.all(method)) {
					if (!isInjector(ann.desc)) continue;
					// A grouped injector is one of the mod's alternatives: another may name the lambda by this build's number.
					for (String lambda : grouped(method) ? List.<String>of() : vanillaLambdaRepairs(ann, targets, ctx)) {
						changed = true;
						out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
								+ " targets " + lambda + ", which this game build numbers differently", ""));
					}
					for (String lambda : lambdaRepairs(method, ann, targets, ctx)) {
						changed = true;
						out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
								+ " targets " + lambda + ", which this game build numbers differently", ""));
					}
					for (String named : argumentNameRepairs(method, ann, targets, ctx)) {
						changed = true;
						out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@ModifyVariable " + method.name + " takes " + named, ""));
					}
					for (String marked : constructorPointRepairs(method, ann, targets, ctx)) {
						changed = true;
						out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Inject " + method.name + " injects into " + marked, ""));
					}
					for (String wrapped : constructorCallWrappers(node, method, ann, targets, ctx, bridges)) {
						changed = true;
						out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Inject " + method.name + " injects into " + wrapped, ""));
					}
					// Wrapped, the handler is a plain method of the mixin: its injector is done with.
					if (!Annotations.all(method).contains(ann)) continue;
					if (softenLocalCapture(ann)) {
						changed = true;
						out.add(new PreflightFinding(Severity.INFO, configName, ctx.mixinDotted(), "@Inject " + method.name
								+ " captures local variables; if " + loader + "'s code has other locals there, it is skipped instead of crashing", ""));
					}
					// A group's alternative for Fabric Loader's hook too: the other alternatives are for other loaders' hooks.
					if (targets.size() == 1) {
						for (String moved : loaderHookRepairs(ann, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
					}
					// A grouped injector is one of the mod's alternatives for code that differs: pointing it elsewhere could make
					// more of the group apply than the group allows.
					if (targets.size() == 1 && !grouped(method)) {
						for (String pinned : overloadByParameters(method, ann, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " targets " + pinned + ", which this game build declares after another overload; it is pinned to that one", ""));
						}
						for (String moved : vanillaPointRepairs(node, method, ann, targets.get(0), ctx, bridges)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects " + moved, ""));
						}
						if (!Annotations.all(method).contains(ann)) continue;
						for (String local : vanillaLocalRepairs(method, ann, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " captures " + local, ""));
						}
						for (String local : namedLocalRepairs(method, ann, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " captures " + local, ""));
						}
						for (String moved : overloadRepairs(node, method, ann, targets.get(0), ctx, bridges)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " targets " + moved, ""));
						}
						// A repair that bridged the handler moved the injector onto the bridge: it is done with.
						if (!Annotations.all(method).contains(ann)) continue;
						for (String moved : constantSplitRepairs(node, method, ann, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@ModifyConstant " + method.name
									+ " targets " + moved, ""));
						}
						for (String moved : subtypeCallRepairs(ann, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
						for (String lambda : lambdaCallRepairs(ann, method, targets.get(0), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call " + lambda, ""));
						}
						for (String moved : insertedArgumentRepairs(node, method, ann, targets.get(0), ctx, bridges)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
						if (!Annotations.all(method).contains(ann)) continue;
						List<String> captured = capturedValueRepairs(node, method, ann, targets.get(0), ctx, bridges);
						// Where the parameters can't be matched by type (one the method has twice), in order, around one added.
						for (String lambda : captured.isEmpty() ? addedParameterRepairs(node, method, ann, targets.get(0), ctx, bridges) : captured) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " targets " + lambda, ""));
						}
					}
				}
				if ((node.access & Opcodes.ACC_INTERFACE) == 0 || (method.access & Opcodes.ACC_STATIC) != 0) continue;
				AnnotationNode accessor = Annotations.find(method, ACCESSOR);
				String kind = accessor != null ? "@Accessor" : Annotations.find(method, INVOKER) != null ? "@Invoker" : null;
				if (kind == null) continue;
				if (targets.stream().allMatch(t -> declaresInstance(t, method.name, method.desc))) {
					if (method.visibleAnnotations != null) method.visibleAnnotations.removeIf(MixinPreflight::isAccessorAnnotation);
					if (method.invisibleAnnotations != null) method.invisibleAnnotations.removeIf(MixinPreflight::isAccessorAnnotation);
					if (method.visibleAnnotations == null) method.visibleAnnotations = new ArrayList<>();
					method.visibleAnnotations.add(new AnnotationNode(SHADOW));
					method.access |= Opcodes.ACC_SYNTHETIC;
					changed = true;
					boolean madePublic = false;
					for (ClassNode t : targets) {
						if (declaresPublicInstance(t, method.name, method.desc)) continue;
						widened.add("public " + t.name.replace('/', '.') + " " + method.name + method.desc);
						madePublic = true;
					}
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), kind + " " + method.name + method.desc
							+ " already exists " + (madePublic ? "" : "publicly ") + "in " + targets.get(0).name.replace('/', '.')
							+ " (added by " + loader + " or this Minecraft version); the mixin uses that method instead of generating one"
							+ (madePublic ? ", made public for it" : ""), ""));
				} else if (accessor != null) {
					String field = capturedFieldRepair(method, accessor, targets, ctx);
					if (field == null) continue;
					changed = true;
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Accessor " + method.name + method.desc
							+ " targets the captured variable field " + field + " in " + targets.get(0).name.replace('/', '.')
							+ ", which this game build names differently", ""));
				}
			}
			for (FieldNode field : node.fields) {
				String captured = capturedShadowRepair(node, field, targets, ctx);
				if (captured == null) continue;
				changed = true;
				out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Shadow field " + captured + " is a captured variable field of "
						+ targets.get(0).name.replace('/', '.') + ", which this game build names " + field.name + "; the mixin uses that", ""));
			}
			node.methods.addAll(bridges);
			if (changed) repaired.put(internal + ".class", write(node));
		}
		return repaired;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * Gives the injectors whose {@code at} Fabric's Mixin declares as an array, and the Mixin NeoForge 1.21.11 runs as a
	 * single injection point ({@code @Redirect}, {@code @ModifyArg}, {@code @ModifyArgs}, {@code @ModifyVariable}), that
	 * single point where the array holds one, as both read it. MixinExtras 0.5.3, which NeoForge 1.21.11 runs, reads a
	 * {@code @Redirect}'s {@code at} as one annotation and crashed on Friends and Foes' (run 382). Returns whether it
	 * changed anything.
	 */
	static boolean singleInjectionPoints(ClassNode mixin) {
		boolean changed = false;
		for (MethodNode method : mixin.methods) {
			for (AnnotationNode ann : Annotations.all(method)) {
				if (!SINGLE_POINT_INJECTORS.contains(ann.desc) || ann.values == null) continue;
				for (int i = 0; i + 1 < ann.values.size(); i += 2) {
					if ("at".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof List<?> points && points.size() == 1) {
						ann.values.set(i + 1, points.get(0));
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Points the selectors of an injector that name a missing lambda ({@code lambda$enclosing$N}) at the only lambda of
	 * the same enclosing method and descriptor that every target has under one other name, and returns the selectors'
	 * original names. A selector without a descriptor is matched by the parameters an {@code @Inject} handler takes,
	 * which are the lambda's (Ledger's portal hook on 1.21.11, whose lambdas NeoForge's recompiled build numbers
	 * otherwise; LAMBDA-4); one taking none isn't judged.
	 */
	private static List<String> lambdaRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<String> repaired = new ArrayList<>();
		next:
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			Matcher lambda = ref.fuzzy() || ref.name() == null ? null : LAMBDA.matcher(ref.name());
			if (lambda == null || !lambda.matches()) continue;
			List<Type> params = ref.desc() == null ? lambdaParameters(handler, injector) : null;
			if (ref.desc() == null && params == null) continue;
			String prefix = "lambda$" + lambda.group(1) + "$";
			MethodNode renamed = null;
			for (ClassNode target : targets) {
				if (ref.desc() == null && target.methods.stream().anyMatch(m -> m.name.equals(ref.name()))) continue next;
				MethodNode only = null;
				for (MethodNode m : target.methods) {
					if (ref.desc() != null ? !m.desc.equals(ref.desc()) : !List.of(Type.getArgumentTypes(m.desc)).equals(params)
							|| Type.getReturnType(m.desc).equals(Type.VOID_TYPE) == returnsValue(handler)) continue;
					if (m.name.equals(ref.name())) continue next; // present: nothing to repair
					if (!m.name.startsWith(prefix) || !LAMBDA.matcher(m.name).matches()) continue;
					if (only != null) {
						only = null;
						break; // ambiguous
					}
					only = m;
				}
				if (only == null || (renamed != null && !(renamed.name + renamed.desc).equals(only.name + only.desc))) {
					renamed = null;
					break;
				}
				renamed = only;
			}
			if (renamed == null) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, renamed.name + renamed.desc);
			repaired.add(ref.name() + (ref.desc() != null ? ref.desc() : ""));
		}
		return repaired;
	}

	/**
	 * Points the selectors of an injector that name a lambda ({@code lambda$enclosing$N}) by vanilla's numbering at the
	 * lambda this game build creates in its place, when vanilla's code is at hand (VANILLA-1), and returns the selectors'
	 * original names. NeoForge's patches add lambdas, which numbers the class's later ones otherwise: its {@code Blocks}
	 * patch makes the powered rail's constructor reference a lambda, so Wilder Wild's hooks into the ice blocks' spawn
	 * predicates named lambdas of another shape on every version, and a hook into one of the same shape would have
	 * applied to the wrong lambda without a word (LAMBDA-5). The lambdas the creating method makes are aligned with
	 * vanilla's by their shape and code ({@link PatchAlignment#places}); a lambda that another lambda creates isn't
	 * followed, nor one whose place the alignment can't tell.
	 */
	private List<String> vanillaLambdaRepairs(AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<String> repaired = new ArrayList<>();
		next:
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null || !LAMBDA.matcher(ref.name()).matches()) continue;
			MethodNode placed = null;
			for (ClassNode target : targets) {
				MethodNode here = lambdaInVanillasPlace(target, ref);
				if (here == null || placed != null && !(placed.name + placed.desc).equals(here.name + here.desc)) continue next;
				placed = here;
			}
			if (placed == null || placed.name.equals(ref.name()) && (ref.desc() == null || placed.desc.equals(ref.desc()))) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, placed.name + placed.desc);
			repaired.add(ref.name() + (ref.desc() != null ? ref.desc() : ""));
		}
		return repaired;
	}

	/**
	 * The lambda of {@code target} that this game build creates where vanilla's version of the class creates the one
	 * {@code ref} names, or null when that can't be told.
	 */
	private MethodNode lambdaInVanillasPlace(ClassNode target, MemberRef ref) {
		ClassNode original = vanillaClass(target.name);
		if (original == null) return null;
		MethodNode lambda = null;
		for (MethodNode m : original.methods) {
			if (!m.name.equals(ref.name()) || ref.desc() != null && !m.desc.equals(ref.desc())) continue;
			if (lambda != null) return null;
			lambda = m;
		}
		if (lambda == null) return null;
		MethodNode creator = null;
		for (MethodNode m : original.methods) {
			if (!createsLambda(m, lambda)) continue;
			if (creator != null) return null;
			creator = m;
		}
		if (creator == null || LAMBDA.matcher(creator.name).matches()) return null;
		MethodNode patched = null;
		for (MethodNode m : target.methods) {
			if (m.name.equals(creator.name) && m.desc.equals(creator.desc)) patched = m;
		}
		if (patched == null) return null;
		List<CreatedLambda> vanillas = createdLambdas(original, creator);
		List<CreatedLambda> games = createdLambdas(target, patched);
		int index = 0;
		while (index < vanillas.size() && vanillas.get(index).body() != lambda) index++;
		int[] places = PatchAlignment.places(vanillas.stream().map(CreatedLambda::shape).toArray(String[]::new),
				games.stream().map(CreatedLambda::shape).toArray(String[]::new));
		if (index == vanillas.size() || places == null || places[index] < 0) return null;
		return games.get(places[index]).body();
	}

	/** A lambda a method creates: its body, and its shape as alignment compares it (the creating instruction's type, its descriptor and code). */
	private record CreatedLambda(MethodNode body, String shape) {
	}

	/** The lambdas of {@code owner} that {@code method} creates, in the order it creates them. */
	private static List<CreatedLambda> createdLambdas(ClassNode owner, MethodNode method) {
		List<CreatedLambda> created = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof InvokeDynamicInsnNode indy)) continue;
			for (Object arg : indy.bsmArgs) {
				if (!(arg instanceof Handle h) || !h.getOwner().equals(owner.name) || !LAMBDA.matcher(h.getName()).matches()) continue;
				for (MethodNode m : owner.methods) {
					if (m.name.equals(h.getName()) && m.desc.equals(h.getDesc())) {
						created.add(new CreatedLambda(m, indy.name + indy.desc + " " + m.desc + "\n" + PatchAlignment.code(m)));
					}
				}
			}
		}
		return created;
	}

	/** Whether an {@code @Inject} handler takes a returnable callback info: its target returns a value. */
	private static boolean returnsValue(MethodNode handler) {
		List<Type> args = handlerArgs(handler);
		return args != null && !args.isEmpty() && args.get(args.size() - 1).getDescriptor().equals(CALLBACK_INFO_RETURNABLE);
	}

	/** The parameters an {@code @Inject} handler takes before its callback info (and any sugar), or null when it takes none. */
	private static List<Type> lambdaParameters(MethodNode handler, AnnotationNode injector) {
		List<Type> args = injector.desc.equals(INJECT) ? handlerArgs(handler) : null;
		if (args == null || args.size() < 2 || !isCallbackInfo(args.get(args.size() - 1))) return null;
		return args.subList(0, args.size() - 1);
	}

	/**
	 * Pins a descriptor-less {@code @Inject} or {@code @WrapMethod} selector to the one overload whose parameters the
	 * handler takes, when Mixin would pick another (the first of that name); returns the pinned methods.
	 */
	private static List<String> overloadByParameters(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		boolean inject = injector.desc.equals(INJECT);
		if (!inject && !injector.desc.equals(WRAP_METHOD) || Annotations.value(injector, "locals") != null
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<Type> args = handlerArgs(handler);
		if (args == null || args.size() < 2
				|| !(inject ? isCallbackInfo(args.get(args.size() - 1)) : args.get(args.size() - 1).getDescriptor().equals(OPERATION))) return List.of();
		List<Type> wanted = args.subList(0, args.size() - 1);
		List<String> pinned = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null || ref.desc() != null) continue;
			MethodNode first = null;
			MethodNode fitting = null;
			int fits = 0;
			for (MethodNode m : target.methods) {
				if (!m.name.equals(ref.name())) continue;
				if (first == null) first = m;
				if (List.of(Type.getArgumentTypes(m.desc)).equals(wanted)) {
					fitting = m;
					fits++;
				}
			}
			if (fits != 1 || fitting == first) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, fitting.name + fitting.desc);
			pinned.add(fitting.name + fitting.desc);
		}
		return pinned;
	}

	/**
	 * Points the selectors of an injector whose injection points are missing from the method Mixin picks, but all occur in
	 * the one overload of the same name that method calls (NeoForge's way of keeping vanilla's signature while moving the
	 * code), at that overload; returns a description of each move. An {@code @Inject} whose points are there but whose
	 * captured locals aren't (the method only calls the overload now) moves the same way. A handler that takes the
	 * method's parameters gets them from a bridge taking the overload's, when the method only passes its own parameters
	 * on with one value it works out ({@link #insertedParameter}, as {@code MappedRegistry.register} with an id). Its
	 * {@code @Local}s must find their locals in the overload ({@link #localsInOverload}).
	 */
	private List<String> overloadRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty() || injector.desc.equals(MODIFY_VARIABLE) || Annotations.value(injector, "locals") != null) return List.of();
		// An @Inject handler takes the callback info only, or (bridged below) the target's parameters before it.
		boolean takesParameters = false;
		if (injector.desc.equals(INJECT)) {
			List<Type> args = handlerArgs(handler);
			if (args == null || args.isEmpty() || !isCallbackInfo(args.get(args.size() - 1))) return List.of();
			takesParameters = args.size() > 1;
		}
		boolean inject = injector.desc.equals(INJECT);
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) continue;
			MethodNode chosen = null;
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) {
					chosen = m;
					break;
				}
			}
			if (chosen == null) continue;
			MethodNode overload;
			String how;
			Verdict inChosen = InjectionPoints.pointsIn(ats, slices, chosen, ctx);
			boolean localsGone = inject && inChosen == Verdict.FOUND && missingLocal(handler, List.of(chosen)) != null;
			if (inChosen == Verdict.MISSING || localsGone) {
				overload = calledOverload(chosen, target);
				MethodNode replacement = overload == null ? replacementOf(chosen, target) : null;
				if (replacement != null) overload = replacement;
				boolean split = overload == null && inChosen == Verdict.MISSING;
				if (split) overload = splitOff(chosen, target, ats, slices, ctx);
				if (overload == null || (overload.access & Opcodes.ACC_STATIC) != (chosen.access & Opcodes.ACC_STATIC)
						|| !split && !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))
						|| InjectionPoints.pointsIn(ats, slices, overload, ctx) != Verdict.FOUND) continue;
				// Its callback info must suit the method it moves to: a returnable one only one returning the same. A handler
				// sharing the mixin's state with others that may stay moves only with them, which this doesn't do (HOOK-2's).
				if (split && (inject && !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))
						&& last(handlerArgs(handler)).getDescriptor().equals(CALLBACK_INFO_RETURNABLE)
						|| !independentOf(mixin).contains(handler.name + handler.desc))) continue;
				// A handler taking the method's parameters takes the other's as they are, when they are the same.
				boolean sameParameters = Arrays.equals(Type.getArgumentTypes(chosen.desc), Type.getArgumentTypes(overload.desc));
				int captured = sameParameters ? -1 : inject ? (takesParameters ? 0 : -1) : capturedFrom(handler, chosen);
				int inserted = replacement != null ? insertedArgument(chosen.desc, overload.desc) : insertedParameter(chosen, overload);
				List<Runnable> locals = localsFit(handler, overload) ? List.of() : localsInOverload(handler, overload, inserted, ats, ctx);
				if (locals == null || captured >= 0 && (inserted < 0 || selectors.size() != 1 || (mixin.access & Opcodes.ACC_INTERFACE) != 0)) continue;
				locals.forEach(Runnable::run);
				if (captured >= 0) {
					bridges.add(HandlerBridges.insertedParameterBridge(mixin.name, handler, injector, captured, Type.getArgumentTypes(chosen.desc).length,
							overload, inserted, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
				}
				how = localsGone ? ", which " + loader + " reduced to a call to " : replacement != null ? ", which " + loader + " deprecated for "
						: split ? ", the code of which there " + loader + " split off into " : ", whose code " + loader + " moved into ";
			} else {
				// The injection points exist, but in a constructor nothing calls any more: its end is the end of the one it calls.
				overload = keptConstructorDelegate(chosen, target);
				if (overload == null || !slices.isEmpty() || !atEndOnly(ats) || !localsFit(handler, overload)) continue;
				if (takesParameters) {
					// The handler gets the constructor's own parameters from a bridge taking all of the other one's.
					if (selectors.size() != 1 || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !forwardsItsParameters(chosen, overload)
							|| !takesExactly(handler, chosen)) continue;
					bridges.add(HandlerBridges.bridge(mixin.name, handler, injector, chosen, overload, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
				}
				how = ", which " + loader + " keeps only to call ";
			}
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, overload.name + overload.desc);
			moved.add(chosen.name + chosen.desc + how + overload.name + overload.desc + "; the injector is pointed there");
		}
		return moved;
	}

	/**
	 * Points an {@code @Inject}'s {@code INVOKE} points at one of Fabric Loader's hooks ({@link #LOADER_HOOKS}) at the call
	 * this game build makes at that step, when the methods it targets make that call once and not the hook's. Shifted as
	 * the mod shifts it: after the hook is after every mod's entrypoints, on both. Returns a description of each move.
	 */
	private static List<String> loaderHookRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(INJECT) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = selectedMethods(injector, target, ctx);
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)
					|| Annotations.value(at, "ordinal") instanceof Integer ordinal && ordinal > 0) continue;
			String resolved = ctx.resolve(raw);
			String step = LOADER_HOOKS.get(resolved);
			if (step == null) continue;
			MemberRef hook = MemberRef.parse(resolved);
			MemberRef call = MemberRef.parse(step);
			if (calls(methods, hook) != 0 || calls(methods, call) != 1) continue;
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, step);
			}
			moved.add(hook.owner().replace('/', '.') + "." + hook.name() + ", which Fabric Loader adds where it runs mods' entrypoints;"
					+ " the injection point is pointed at " + call.owner().replace('/', '.') + "." + call.name() + ", where they run here");
		}
		return moved;
	}

	/** How many calls to {@code ref} (owner, name and descriptor) the methods make. */
	private static int calls(List<MethodNode> methods, MemberRef ref) {
		int n = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(ref.owner()) && call.name.equals(ref.name()) && call.desc.equals(ref.desc())) n++;
			}
		}
		return n;
	}

	/**
	 * Points the {@code INVOKE} points of an {@code @Inject} or {@code @ModifyExpressionValue} at a call this game build
	 * makes through a subtype of the class they name, when the named call is in none of the methods the injector
	 * targets: the same method, inherited. (NeoForge wraps vanilla's {@code Decoder} in a conditional {@code Codec} and
	 * calls {@code parse} on that.) Mixin matches a call's owner exactly. Neither injector's handler takes the call's
	 * receiver, so its type changing doesn't matter; the arguments and the result are the same. Only one subtype may
	 * make the call. Returns a description of each move.
	 */
	private List<String> subtypeCallRepairs(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(INJECT) && !injector.desc.equals(MODIFY_EXPRESSION_VALUE) || Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = selectedMethods(injector, target, ctx);
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
			String subtype = null;
			int calls = 0;
			boolean named = false;
			boolean ambiguous = false;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || !ref.matchesMethod(call.name, call.desc)) continue;
					if (call.owner.equals(ref.owner())) {
						named = true;
					} else if (isSubtype(call.owner, ref.owner())) {
						ambiguous |= subtype != null && !subtype.equals(call.owner);
						subtype = call.owner;
						calls++;
					}
				}
			}
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || subtype == null || ambiguous || ordinal >= calls) continue;
			String retargeted = "L" + subtype + ";" + ref.name() + ref.desc();
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, retargeted);
			}
			moved.add(ref.owner().replace('/', '.') + "." + ref.name() + ", which this game build calls through "
					+ subtype.replace('/', '.') + " (the same method); the injection point is pointed there");
		}
		return moved;
	}

	/**
	 * Gives an {@code @Inject} into a lambda a bridge taking the lambda's parameters as this game build has them, when
	 * NeoForge's build captures the same values in another order, or more of them: javac orders a lambda's captured values
	 * by their first use in it, which NeoForge's patches change (Wover's hook into {@code WorldLoader}'s registry load,
	 * FrozenLib's into {@code RegistryDataLoader}'s; LAMBDA-3). The bridge passes each of the handler's parameters the
	 * lambda's of its type: in order, when the handler's types occur in the lambda's that way only once; otherwise by
	 * type, where a type the lambda has more than once is only passed to a parameter the handler never uses. A method
	 * NeoForge gave more parameters gets the same bridge, in order only (Ledger's hooks into {@code FireBlock.checkBurnOut},
	 * which takes a face too; SIG-1). Returns a description of the bridged method.
	 */
	private static List<String> capturedValueRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(INJECT) || Annotations.value(injector, "locals") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| annotatedParameters(handler.visibleParameterAnnotations) || annotatedParameters(handler.invisibleParameterAnnotations)) return List.of();
		List<MethodNode> methods = selectedMethods(injector, target, ctx);
		if (methods.size() != 1 || methods.get(0).name.startsWith("<")) return List.of();
		MethodNode lambda = methods.get(0);
		boolean isLambda = LAMBDA.matcher(lambda.name).matches();
		Type[] own = Type.getArgumentTypes(handler.desc);
		if (own.length < 2 || !isCallbackInfo(own[own.length - 1])
				|| (handler.access & Opcodes.ACC_STATIC) != (lambda.access & Opcodes.ACC_STATIC)) return List.of();
		List<Type> wanted = List.of(own).subList(0, own.length - 1);
		List<Type> captured = List.of(Type.getArgumentTypes(lambda.desc));
		// A patch captures a value or two more; a lambda taking many more is more likely another one under that name.
		if (wanted.equals(captured) || captured.size() > wanted.size() + 2) return List.of();
		int[] from = inOrder(wanted, captured);
		if (from == null && isLambda) from = byType(handler, wanted, captured);
		if (from == null) return List.of();

		List<Type> params = new ArrayList<>(captured);
		params.add(own[own.length - 1]);
		MethodNode bridge = HandlerBridges.bridgeMethod(handler, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated"), params,
				Type.getReturnType(handler.desc));
		int stack = 0;
		if ((handler.access & Opcodes.ACC_STATIC) == 0) {
			bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			stack++;
		}
		for (int index : from) {
			bridge.instructions.add(new VarInsnNode(params.get(index).getOpcode(Opcodes.ILOAD), HandlerBridges.slotOf(handler, params, index)));
			stack += params.get(index).getSize();
		}
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, HandlerBridges.slotOf(handler, params, params.size() - 1)));
		HandlerBridges.callHandler(bridge, mixin.name, handler, stack + 1);
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, lambda.name + lambda.desc);
		HandlerBridges.moveInjector(handler, injector, bridge);
		bridges.add(bridge);
		return List.of(lambda.name + lambda.desc + (isLambda ? ", whose captured values this game build passes in another order or with more"
				: ", which this game build declares with more parameters") + "; a bridge passes the handler the ones it takes");
	}

	/** Where each of {@code wanted} is in {@code captured}, taken in order, when they occur in it that way only once; else null. */
	private static int[] inOrder(List<Type> wanted, List<Type> captured) {
		int n = wanted.size();
		int m = captured.size();
		// ways[i][j]: in how many ways (up to 2) wanted from i on occurs in order in captured from j on.
		int[][] ways = new int[n + 1][m + 1];
		for (int j = 0; j <= m; j++) ways[n][j] = 1;
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				ways[i][j] = Math.min(2, ways[i][j + 1] + (wanted.get(i).equals(captured.get(j)) ? ways[i + 1][j + 1] : 0));
			}
		}
		if (ways[0][0] != 1) return null;
		int[] from = new int[n];
		for (int i = 0, j = 0; i < n; j++) {
			if (wanted.get(i).equals(captured.get(j)) && ways[i + 1][j + 1] == 1) from[i++] = j;
		}
		return from;
	}

	/**
	 * Where each of {@code wanted} is in {@code captured} by its type: the k-th parameter of a type goes to the lambda's
	 * k-th of that type, which is only certain where the lambda has one; a type it has more than once only for a
	 * parameter the handler never uses. Null when a type is missing, or such a parameter is used.
	 */
	private static int[] byType(MethodNode handler, List<Type> wanted, List<Type> captured) {
		int[] from = new int[wanted.size()];
		Map<Type, Integer> seen = new HashMap<>();
		int slot = (handler.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (int i = 0; i < wanted.size(); i++) {
			Type type = wanted.get(i);
			int k = seen.merge(type, 1, Integer::sum) - 1;
			List<Integer> at = new ArrayList<>();
			for (int j = 0; j < captured.size(); j++) {
				if (captured.get(j).equals(type)) at.add(j);
			}
			if (at.size() <= k || at.size() > 1 && usesSlot(handler, slot)) return null;
			from[i] = at.get(k);
			slot += type.getSize();
		}
		return from;
	}

	/** Whether {@code method} reads or writes the local variable in {@code slot}. */
	private static boolean usesSlot(MethodNode method, int slot) {
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof VarInsnNode v && v.var == slot || insn instanceof IincInsnNode inc && inc.var == slot) return true;
		}
		return false;
	}

	/** The injectors at a call or field access whose handler takes only what that instruction takes and gives. */
	private static final Set<String> CALL_SITE_INJECTORS = Set.of(WRAP_OPERATION, MODIFY_EXPRESSION_VALUE, REDIRECT,
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");

	/**
	 * Adds to a call-site injector's methods the lambda a target method's call moved into: when the call or field access
	 * it hooks is in none of that method's instructions but in exactly one lambda the method creates, as where NeoForge's
	 * build runs vanilla's code from an {@code Optional} callback (FrozenLib's registry call in
	 * {@code RegistryDataLoader.loadElementFromResource}, on 1.21.11; LAMBDA-2). The handler takes the instruction's own
	 * receiver, arguments or result, which are the same in the lambda; one that takes MixinExtras sugar (the method's
	 * locals), isn't static where the lambda is, or whose point has an ordinal or a slice, isn't moved. Returns a
	 * description of each lambda added.
	 */
	private static List<String> lambdaCallRepairs(AnnotationNode injector, MethodNode handler, ClassNode target, Context ctx) {
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (!CALL_SITE_INJECTORS.contains(injector.desc) || Annotations.value(injector, "slice") != null || ats.size() != 1
				|| annotatedParameters(handler.visibleParameterAnnotations) || annotatedParameters(handler.invisibleParameterAnnotations)
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!Set.of("INVOKE", "INVOKE_ASSIGN", "FIELD", "NEW").contains(point) || Annotations.value(at, "ordinal") != null
				|| !(Annotations.value(at, "target") instanceof String raw)) return List.of();
		String resolved = ctx.resolve(raw);
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		List<String> added = new ArrayList<>();
		for (MethodNode method : selectedMethods(injector, target, ctx)) {
			if (InjectionPoints.count(point, resolved, -1, opcode, Range.ALL, method) != 0) continue;
			MethodNode only = null;
			for (MethodNode lambda : target.methods) {
				if (!lambda.name.startsWith("lambda$" + method.name + "$") || !createsLambda(method, lambda)
						|| InjectionPoints.count(point, resolved, -1, opcode, Range.ALL, lambda) <= 0) continue;
				if (only != null) {
					only = null;
					break; // in two lambdas: not judged
				}
				only = lambda;
			}
			if (only == null || (only.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) == 0) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.add(only.name + only.desc);
			added.add(MemberRef.parse(resolved).name() + " in " + method.name + ", which this game build makes in its lambda " + only.name
					+ "; the lambda is added to its methods");
		}
		return added;
	}

	/** Whether {@code method} creates {@code lambda} (its {@code invokedynamic} names the lambda's body). */
	private static boolean createsLambda(MethodNode method, MethodNode lambda) {
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof InvokeDynamicInsnNode indy)) continue;
			for (Object arg : indy.bsmArgs) {
				if (arg instanceof Handle h && h.getName().equals(lambda.name) && h.getDesc().equals(lambda.desc)) return true;
			}
		}
		return false;
	}

	private static boolean annotatedParameters(List<AnnotationNode>[] byParameter) {
		if (byParameter == null) return false;
		for (List<AnnotationNode> annotations : byParameter) {
			if (annotations != null && !annotations.isEmpty()) return true;
		}
		return false;
	}

	/** Whether {@code type} is, extends or implements {@code supertype}, as far as the game's and the libraries' classes tell. */
	private boolean isSubtype(String type, String supertype) {
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

	/** The methods an injector's selectors pick in {@code target}, as Mixin does (the first match of each); none when one is fuzzy. */
	private static List<MethodNode> selectedMethods(AnnotationNode injector, ClassNode target, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<MethodNode> methods = new ArrayList<>();
		for (Object selector : selectors) {
			if (!(selector instanceof String raw)) return List.of();
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) return List.of();
			target.methods.stream().filter(m -> ref.matchesMethod(m.name, m.desc)).findFirst().ifPresent(methods::add);
		}
		return methods;
	}

	/**
	 * Gives a {@code @ModifyVariable} taking a method's argument by its name, with {@code argsOnly}, the argument's slot
	 * instead, where the running Mixin doesn't know arguments by their names (ARGS-1: SpongePowered's names them {@code arg}
	 * and their slot, so Fabric API's resource loader hook taking {@code enableProfiling} in
	 * {@code SimpleReloadInstance.create} failed on Forge 65; Fabric's Mixin takes their names from 0.17.0). The argument is
	 * the one of that name in the target method's local variable table, or in vanilla's, or else the only one of the
	 * handler's type; it must be the same slot in every target. Returns a description of each.
	 */
	private List<String> argumentNameRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (argumentNames || !injector.desc.equals(MODIFY_VARIABLE) || !Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| !(Annotations.value(injector, "name") instanceof List<?> names) || names.isEmpty()
				|| Annotations.value(injector, "index") != null || Annotations.value(injector, "ordinal") != null) return List.of();
		Type type = Type.getReturnType(handler.desc);
		Integer slot = null;
		for (ClassNode target : targets) {
			List<MethodNode> methods = selectedMethods(injector, target, ctx);
			if (methods.isEmpty()) return List.of();
			ClassNode original = vanillaClass(target.name);
			for (MethodNode m : methods) {
				Integer found = argumentSlot(m, names, type);
				MethodNode before = found == null && original != null ? declared(original, m.name, m.desc) : null;
				if (before != null) found = argumentSlot(before, names, type);
				if (found == null) found = onlyArgumentOfType(m, type);
				if (found == null || slot != null && !slot.equals(found)) return List.of();
				slot = found;
			}
		}
		if (slot == null) return List.of();
		removeValue(injector, "name");
		Annotations.put(injector, "index", slot);
		return List.of("the argument " + names.get(0) + " by its name, which " + loader + "'s Mixin doesn't know; it takes the argument's slot, "
				+ slot + ", instead");
	}

	/** Removes an annotation's value. */
	private static void removeValue(AnnotationNode annotation, String key) {
		if (annotation.values == null) return;
		for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
			if (key.equals(annotation.values.get(i))) {
				annotation.values.remove(i + 1);
				annotation.values.remove(i);
				return;
			}
		}
	}

	/** The slot of the method's argument that its local variable table names one of {@code names}, of {@code type}; null when none. */
	private static Integer argumentSlot(MethodNode method, List<?> names, Type type) {
		if (method.localVariables == null) return null;
		int end = argumentsEnd(method);
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.index < end && names.contains(lv.name) && lv.desc.equals(type.getDescriptor()) && isArgumentSlot(method, lv.index)) return lv.index;
		}
		return null;
	}

	/** The slot of the method's only argument of {@code type}; null when it has none or several. */
	private static Integer onlyArgumentOfType(MethodNode method, Type type) {
		Integer found = null;
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (arg.equals(type)) {
				if (found != null) return null;
				found = slot;
			}
			slot += arg.getSize();
		}
		return found;
	}

	/** The first slot after the method's arguments. */
	private static int argumentsEnd(MethodNode method) {
		return ((method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1) + (Type.getArgumentsAndReturnSizes(method.desc) >> 2) - 1;
	}

	/** Whether a slot is where one of the method's arguments starts (not {@code this}, nor the second half of a long or double). */
	private static boolean isArgumentSlot(MethodNode method, int index) {
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (slot == index) return true;
			slot += arg.getSize();
		}
		return false;
	}

	/**
	 * The name a {@code @ModifyVariable} taking arguments only asks for, where the running Mixin doesn't know arguments by
	 * their names (ARGS-1) and no repair gave it the slot instead; null otherwise.
	 */
	private String argumentNameRejected(AnnotationNode injector) {
		if (argumentNames || !injector.desc.equals(MODIFY_VARIABLE) || !Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "index") != null || Annotations.value(injector, "ordinal") != null
				|| !(Annotations.value(injector, "name") instanceof List<?> names) || names.isEmpty()) return null;
		return String.valueOf(names.get(0));
	}

	/**
	 * The first point of an {@code @Inject} into a constructor that SpongePowered's Mixin rejects (CTOR-2: it throws as it
	 * applies the mixin, whatever the config's {@code required}): any but a return that isn't marked {@code unsafe}, or,
	 * on 0.8.5, any but a return. Null when it takes them all, or on Fabric's Mixin.
	 */
	private String constructorPointRejected(AnnotationNode injector, List<MethodNode> matched) {
		if (constructorInjects == ConstructorInjects.AFTER_DELEGATE || !injector.desc.equals(INJECT)
				|| matched.stream().noneMatch(m -> m.name.equals("<init>"))) return null;
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (RETURN_POINTS.contains(point)) continue;
			if (constructorInjects == ConstructorInjects.UNSAFE_POINTS && Boolean.TRUE.equals(Annotations.value(at, "unsafe"))) continue;
			return point;
		}
		return null;
	}

	/**
	 * The injectors Mixin refuses with a handler that isn't static at a constructor's instruction up to its call to the
	 * delegate constructor, where the instance doesn't exist yet ({@code Injector.checkTargetForNode}, the same in
	 * SpongePowered's Mixin and Fabric's), with the points pre-flight finds for each: a call, and for a redirect a field
	 * access too (a redirected construction takes another path).
	 */
	private static final Map<String, Set<String>> STATIC_BEFORE_DELEGATE = Map.of(
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;", Set.of("INVOKE"),
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", Set.of("INVOKE"),
			REDIRECT, Set.of("INVOKE", "FIELD"));

	/**
	 * The point of an injector whose handler isn't static that picks, in a constructor of {@code owner}, an instruction up
	 * to its call to the delegate constructor ({@code super(...)} or {@code this(...)}), which Mixin refuses ("handler before
	 * this() invocation must be static", {@link #STATIC_BEFORE_DELEGATE}) wherever it is required (CTOR-3: Fabric API's
	 * shader program hook modifies vanilla's {@code ResourceLocation} construction in {@code ShaderInstance}'s constructor,
	 * which Forge 47's patch moved into the arguments of a {@code this(...)} call). Null when there is none.
	 */
	private static String beforeDelegateRejected(MethodNode handler, AnnotationNode injector, ClassNode owner, List<MethodNode> matched, Context ctx) {
		Set<String> points = STATIC_BEFORE_DELEGATE.get(injector.desc);
		if (points == null || (handler.access & Opcodes.ACC_STATIC) != 0 || matched.stream().noneMatch(m -> m.name.equals("<init>"))) return null;
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!points.contains(point)) continue;
			String target = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			for (MethodNode m : matched) {
				if (!m.name.equals("<init>")) continue;
				int delegate = delegateCall(m, owner);
				List<Integer> picked = delegate < 0 ? null
						: InjectionPoints.indexes(point, target, ordinal, opcode, slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx), m);
				if (picked != null && picked.stream().anyMatch(i -> i <= delegate)) return point;
			}
		}
		return null;
	}

	/** Points an {@code @Inject} into a constructor may take anywhere on SpongePowered's Mixin: its returns (CTOR-2). */
	private static final Set<String> RETURN_POINTS = Set.of("RETURN", "TAIL");
	/** Points whose instructions pre-flight finds, so it can tell whether they follow the delegate constructor call. */
	private static final Set<String> PLACED_POINTS = Set.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW");

	/**
	 * Marks {@code unsafe} the points of an {@code @Inject} into a constructor that SpongePowered's Mixin takes only so
	 * marked (CTOR-2): any but a return, where Fabric's Mixin takes any point after the call to the delegate constructor
	 * ({@code super(...)} or {@code this(...)}). A point is marked when every instruction it picks in every constructor
	 * selected follows that call, which gives Fabric's behaviour: fabric-command-api-v2's hook into {@code Commands}'
	 * constructor, which stopped Forge 61's server. Returns a description of each point marked.
	 */
	private List<String> constructorPointRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (constructorInjects != ConstructorInjects.UNSAFE_POINTS || !injector.desc.equals(INJECT)) return List.of();
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		List<String> marked = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!PLACED_POINTS.contains(point) || Boolean.TRUE.equals(Annotations.value(at, "unsafe"))) continue;
			String target = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			boolean picked = false;
			boolean afterDelegate = true;
			String where = null;
			for (ClassNode t : targets) {
				for (MethodNode m : selectedMethods(injector, t, ctx)) {
					if (!m.name.equals("<init>")) continue;
					int delegate = delegateCall(m, t);
					Range range = slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx);
					List<Integer> at2 = InjectionPoints.indexes(point, target, ordinal, opcode, range, m);
					if (delegate < 0 || at2 == null) {
						afterDelegate = false;
						continue;
					}
					for (int i : at2) {
						picked = true;
						afterDelegate &= i > delegate;
					}
					where = t.name.replace('/', '.');
				}
			}
			if (!picked || !afterDelegate) continue;
			Annotations.put(at, "unsafe", true);
			marked.add("the constructor of " + where + " at @At(\"" + point + "\"" + (target != null ? ", target=\"" + target + "\"" : "")
					+ ") after its super() call, which " + loader + "'s Mixin takes only at a point marked unsafe; it is so marked, as Fabric's Mixin takes it");
		}
		return marked;
	}

	/**
	 * Turns an {@code @Inject} at a call in a constructor into a MixinExtras {@code @WrapOperation} of that call, where
	 * the running Mixin takes an {@code @Inject} into a constructor only at a return (CTOR-4: SpongePowered's 0.8.5, which
	 * Forge 47 runs; Fabricated's in-game mod carries MixinExtras there). The wrapper calls the handler with the
	 * constructor's parameters and a callback info of its own just before the call, or just after it ({@code shift =
	 * AFTER}), as Fabric's Mixin runs the handler: fabric-command-api-v2's hook into {@code Commands}' constructor, which
	 * registers Fabric mods' commands, and model loading's into {@code ModelBakery}'s. Only where every call the point picks
	 * in every constructor selected follows the call to the delegate constructor and is the same plain call (not a
	 * {@code super} call or a construction), and the handler takes the constructor's parameters (or none) and the callback
	 * info, and nothing else: not cancellable, no locals, no sugar, no shift but {@code AFTER}. Returns a description of
	 * the injector wrapped.
	 */
	private List<String> constructorCallWrappers(ClassNode mixin, MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx,
			List<MethodNode> bridges) {
		if (constructorInjects != ConstructorInjects.RETURNS_ONLY || !injector.desc.equals(INJECT)
				|| Boolean.TRUE.equals(Annotations.value(injector, "cancellable")) || Annotations.value(injector, "locals") != null
				|| Annotations.value(injector, "target") != null || Annotations.value(injector, "constraints") != null
				|| !Type.getReturnType(handler.desc).equals(Type.VOID_TYPE)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		Object shift = Annotations.value(at, "shift");
		String shiftName = shift instanceof String[] e && e.length == 2 ? e[1] : shift == null ? "NONE" : null;
		// BEFORE puts the point an instruction earlier, among the call's arguments: a wrapper can't run the handler there.
		if (!point.equals("INVOKE") || Annotations.value(at, "by") != null || !"NONE".equals(shiftName) && !"AFTER".equals(shiftName)) return List.of();
		Type[] own = Type.getArgumentTypes(handler.desc);
		if (own.length == 0 || !own[own.length - 1].getDescriptor().equals(CALLBACK_INFO)) return List.of();
		for (int i = 0; i < own.length; i++) {
			if (!Annotations.ofParameter(handler, i).isEmpty()) return List.of();
		}
		List<Type> constructorParams = List.of(own).subList(0, own.length - 1);
		String target = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		AnnotationNode slice = InjectionPoints.sliceFor(at, Annotations.list(Annotations.value(injector, "slice")));
		MethodInsnNode call = null;
		String where = null;
		for (ClassNode t : targets) {
			for (MethodNode m : selectedMethods(injector, t, ctx)) {
				if (!m.name.equals("<init>")) return List.of();
				if (!constructorParams.isEmpty() && !constructorParams.equals(List.of(Type.getArgumentTypes(m.desc)))) return List.of();
				int delegate = delegateCall(m, t);
				List<Integer> picked = delegate < 0 ? null
						: InjectionPoints.indexes(point, target, ordinal, opcode, slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx), m);
				if (picked == null) return List.of();
				for (int index : picked) {
					if (index <= delegate || !(m.instructions.get(index) instanceof MethodInsnNode insn) || insn.getOpcode() == Opcodes.INVOKESPECIAL) return List.of();
					if (call != null && (call.getOpcode() == Opcodes.INVOKESTATIC) != (insn.getOpcode() == Opcodes.INVOKESTATIC)
							|| call != null && !(call.owner.equals(insn.owner) && call.desc.equals(insn.desc))) return List.of();
					call = insn;
				}
				where = t.name.replace('/', '.');
			}
		}
		if (call == null) return List.of();
		boolean staticCall = call.getOpcode() == Opcodes.INVOKESTATIC;
		MethodNode bridge = HandlerBridges.constructorCallWrapper(mixin, handler, Type.getObjectType(call.owner), call.desc, staticCall,
				constructorParams, "AFTER".equals(shiftName), bridges);
		AnnotationNode wrapper = new AnnotationNode(WRAP_OPERATION);
		for (String key : List.of("method", "slice", "remap", "require", "expect", "allow")) {
			Object value = Annotations.value(injector, key);
			if (value != null) Annotations.put(wrapper, key, value);
		}
		AnnotationNode wrappedAt = new AnnotationNode(at.desc);
		for (int i = 0; at.values != null && i < at.values.size(); i += 2) {
			if (at.values.get(i).equals("shift")) continue;
			Annotations.put(wrappedAt, (String) at.values.get(i), at.values.get(i + 1));
		}
		Annotations.put(wrapper, "at", new ArrayList<>(List.of(wrappedAt)));
		HandlerBridges.replaceInjector(handler, injector, wrapper, bridge);
		bridges.add(bridge);
		return List.of("the constructor of " + where + " at @At(\"INVOKE\", target=\"" + target + "\"), which " + loader
				+ "'s Mixin takes only at a return; it wraps that call (MixinExtras' @WrapOperation) and runs the handler "
				+ ("AFTER".equals(shiftName) ? "after" : "before") + " it, as Fabric's Mixin does");
	}

	private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";

	/**
	 * The index of a constructor's call to its delegate constructor ({@code super(...)} or {@code this(...)}): the first
	 * {@code INVOKESPECIAL <init>} of its class or superclass that doesn't initialise an instance the constructor makes
	 * itself ({@code new}); -1 when there is none.
	 */
	private static int delegateCall(MethodNode constructor, ClassNode owner) {
		Map<String, Integer> made = new HashMap<>();
		for (int i = 0; i < constructor.instructions.size(); i++) {
			AbstractInsnNode insn = constructor.instructions.get(i);
			if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
				made.merge(type.desc, 1, Integer::sum);
			} else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>")) {
				Integer pending = made.get(call.owner);
				if (pending != null && pending > 0) {
					made.put(call.owner, pending - 1);
				} else if (call.owner.equals(owner.name) || call.owner.equals(owner.superName)) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Reads an injector's points that pick one of a target method's calls, field accesses or constructions by ordinal
	 * against vanilla's version of the method, when there is one (VANILLA-1). NeoForge's patches add and replace such
	 * instructions, so this game build's ordinal-th may not be the one the mod was written for, or there may be fewer.
	 * Vanilla's instructions aligned with this game build's ({@link PatchAlignment}) tell where vanilla's went: where it
	 * is still there, the point's ordinal becomes its ordinal here; where NeoForge's patch put a call of an overload taking
	 * one more argument in its place (its camera passes a roll along that way), the point is pointed at that call, with
	 * its handler bridged as {@link #insertedArgumentRepairs} does. A point inside a slice, or a target method vanilla
	 * doesn't declare, is left as it is. Returns a description of each change.
	 */
	private List<String> vanillaPointRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (Annotations.value(injector, "slice") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = declared(original, patched.name, patched.desc);
		if (before == null) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		PatchAlignment alignment = null;
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "ordinal") instanceof Integer ordinal) || ordinal < 0
					|| !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy()) continue;
			int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
			List<Integer> inVanilla = matching(before, point, ref, opcode);
			if (ordinal >= inVanilla.size()) continue;
			if (alignment == null) alignment = PatchAlignment.of(before, patched);
			if (alignment == null) return moved;
			String what = (point.equals("INVOKE") ? "the call " : point.equals("FIELD") ? "the access " : "the construction ") + raw
					+ " with ordinal " + ordinal + " in vanilla's code, which ";
			int place = alignment.placeOf(inVanilla.get(ordinal));
			if (place == PatchAlignment.UNCERTAIN) continue;
			if (place >= 0) {
				int now = matching(patched, point, ref, opcode).indexOf(place);
				if (now < 0 || now == ordinal) continue;
				Annotations.put(at, "ordinal", now);
				moved.add(what + "has ordinal " + now + " in this game build, as " + loader + "'s patches add or replace others; the injection point is pointed there");
				continue;
			}
			boolean pointOnly = injector.desc.equals(INJECT) || injector.desc.equals(MODIFY_EXPRESSION_VALUE);
			if (!point.equals("INVOKE") || ref.owner() == null || ref.name() == null || ref.desc() == null || !pointOnly && ats.size() != 1
					|| !pointOnly && !WRAP_WITH_CONDITION.contains(injector.desc) && !injector.desc.equals(REDIRECT) && !injector.desc.equals(WRAP_OPERATION)) continue;
			ClassNode owner = gameTarget(ref.owner());
			MethodInsnNode replacement = replacingOverload(patched, alignment.replacementOf(inVanilla.get(ordinal)), ref, owner);
			if (replacement == null) continue;
			int inserted = insertedArgument(owner, ref.name(), ref.desc(), replacement.desc);
			if (!pointOnly && !bridgeTo(mixin, handler, injector, ref.owner(), ref.desc(), replacement.desc, inserted, replacement.getOpcode() == Opcodes.INVOKESTATIC,
					bridges)) continue;
			int now = 0;
			for (AbstractInsnNode insn = patched.instructions.getFirst(); insn != replacement; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(replacement.owner) && call.name.equals(replacement.name) && call.desc.equals(replacement.desc)) now++;
			}
			Annotations.put(at, "target", "L" + replacement.owner + ";" + replacement.name + replacement.desc);
			Annotations.put(at, "ordinal", now);
			moved.add(what + loader + "'s patch replaced with a call of " + replacement.name + replacement.desc + ", taking one more argument (ordinal " + now
					+ " here); the injection point is pointed there" + (pointOnly ? "" : ", through a bridge handler"));
		}
		return moved;
	}

	/**
	 * Names by its ordinal each {@code @Local} capturing a local by its type alone where this game build has several of
	 * that type in scope at the injection point, NeoForge's patch having added one (LOCAL-2: Ledger's item pickup hooks
	 * in {@code ItemEntity.playerTouch}, where NeoForge's patch copies the stack for its pickup event). Vanilla's version
	 * of the method (VANILLA-1) aligned with this build's ({@link PatchAlignment}) tells which local is vanilla's: the
	 * one whose store is one of vanilla's instructions, or a parameter, when exactly one of those in scope is, as vanilla
	 * had one of the type there for the capture to work at all. Its ordinal among the locals of the type in scope
	 * (parameters first, by slot, as {@link #ordinalAt} counts) must be the same at every instruction the point picks.
	 * An {@code @Local} by type with an ordinal names vanilla's local of that ordinal among vanilla's of the type, which
	 * gets its ordinal here where the patch added one before it (LOCAL-4: Fabric API's Indigo takes {@code @Local(ordinal
	 * = 0) Map} in {@code SectionCompiler.compile}, vanilla's map of buffers by layer, where Forge 61's patch first keeps
	 * the section's model data in a map). Only for an injector with one {@code @At} naming a call, field access or
	 * construction, outside a slice, into one method vanilla declares, as {@link #ambiguousLocal} judges them. Returns a
	 * description of each.
	 */
	private List<String> vanillaLocalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (Annotations.value(injector, "slice") != null) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return List.of();
		ClassNode original = vanillaClass(target.name);
		List<MethodNode> methods = original == null ? List.of() : selectedMethods(injector, target, ctx);
		if (methods.size() != 1) return List.of();
		MethodNode patched = methods.get(0);
		MethodNode before = declared(original, patched.name, patched.desc);
		if (before == null || patched.localVariables == null || patched.localVariables.isEmpty()) return List.of();
		List<Integer> positions = matching(patched, point, ref, Annotations.value(at, "opcode") instanceof Integer o ? o : -1);
		int pointOrdinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (pointOrdinal >= 0) positions = pointOrdinal < positions.size() ? List.of(positions.get(pointOrdinal)) : List.of();
		if (positions.isEmpty()) return List.of();
		Set<Integer> vanillaPlaces = null;
		List<String> named = new ArrayList<>();
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				Object given = Annotations.value(local, "ordinal");
				if (!local.desc.equals(LOCAL) || Annotations.value(local, "name") != null || Annotations.value(local, "index") != null
						|| given != null && !(given instanceof Integer) || Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))
						|| params[p].getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
				if (vanillaPlaces == null) {
					PatchAlignment alignment = PatchAlignment.of(before, patched);
					if (alignment == null) return named;
					vanillaPlaces = new HashSet<>();
					for (int i = 0; i < before.instructions.size(); i++) {
						if (alignment.placeOf(i) >= 0) vanillaPlaces.add(alignment.placeOf(i));
					}
				}
				if (given instanceof Integer vanillas) {
					int ordinal = vanillasNthLocalOrdinal(patched, params[p].getDescriptor(), positions, vanillaPlaces, vanillas);
					if (ordinal < 0 || ordinal == vanillas) continue;
					Annotations.put(local, "ordinal", ordinal);
					named.add("the local of type " + params[p].getClassName() + " with ordinal " + vanillas + ", vanilla's, which has ordinal " + ordinal
							+ " in " + patched.name + " in " + target.name.replace('/', '.') + " there, " + loader + "'s patch having added one of the type"
							+ " before it; the @Local names it");
					continue;
				}
				int ordinal = vanillasLocalOrdinal(patched, params[p].getDescriptor(), positions, vanillaPlaces);
				if (ordinal < 0) continue;
				Annotations.put(local, "ordinal", ordinal);
				named.add("the local of type " + params[p].getClassName() + " by its type, of which " + patched.name + " in "
						+ target.name.replace('/', '.') + " has more than one there, " + loader + "'s patch having added one; vanilla's is the one with ordinal "
						+ ordinal + " here, and the @Local names it");
			}
		}
		return named;
	}

	/**
	 * Adds this build's name to each {@code @Local(name = ...)} none of whose names a local of its type has in scope at the
	 * injection point, where exactly one local of that type is in scope there, the one MixinExtras would take by the type
	 * alone (LOCAL-3: Fabric API's lifecycle hook takes {@code @Local(name = "level") ServerLevel} at
	 * {@code ServerLevel.close()} in {@code MinecraftServer.stopServer}, a variable Forge 64's build, recompiled from
	 * decompiled source, names {@code levelx}, the method declaring another {@code level} before it). MixinExtras takes
	 * any of a {@code @Local}'s names, so where one of the mod's is in scope it still is. Only for an injector with one
	 * {@code @At} naming a call, field access or construction, outside a slice, in methods with local variable names.
	 * Returns a description of each.
	 */
	private static List<String> namedLocalRepairs(MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (Annotations.value(injector, "slice") != null) return List.of();
		List<MethodNode> methods = selectedMethods(injector, target, ctx);
		if (methods.isEmpty()) return List.of();
		List<String> added = new ArrayList<>();
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			String desc = params[p].getDescriptor();
			if (desc.startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (!local.desc.equals(LOCAL) || !(Annotations.value(local, "name") instanceof List<?> names) || names.isEmpty()
						|| Annotations.value(local, "index") != null || Annotations.value(local, "ordinal") != null) continue;
				String found = null;
				boolean failed = false;
				methods:
				for (MethodNode m : methods) {
					List<Integer> positions = pointPositions(injector, m, ctx);
					if (positions == null || positions.isEmpty() || m.localVariables == null || m.localVariables.isEmpty()) {
						failed = true;
						break;
					}
					for (int i : positions) {
						List<LocalVariableNode> inScope = inScope(m, i, desc);
						if (inScope.stream().anyMatch(lv -> names.contains(lv.name))) continue;
						Set<String> named = new HashSet<>();
						Set<Integer> slots = new HashSet<>();
						for (LocalVariableNode lv : inScope) {
							named.add(lv.name);
							slots.add(lv.index);
						}
						String name = named.size() == 1 && slots.size() == 1 ? named.iterator().next() : null;
						if (name == null || found != null && !found.equals(name)) {
							failed = true;
							break methods;
						}
						found = name;
					}
				}
				if (failed || found == null) continue;
				List<Object> extended = new ArrayList<>(names);
				extended.add(found);
				Annotations.put(local, "name", extended);
				added.add("the local " + names.get(0) + " of type " + params[p].getClassName() + ", which " + methods.get(0).name + " in "
						+ target.name.replace('/', '.') + " names " + found + " where the injector applies; the @Local takes that name too");
			}
		}
		return added;
	}

	/**
	 * The first name a handler's {@code @Local(name = ...)} asks for that no local has in scope at an instruction the
	 * injector's point picks, in a method that declares one of its names elsewhere: MixinExtras looks among the locals in
	 * scope there. Only for an injector with one {@code @At} naming a call, field access or construction, as
	 * {@link #pointPositions} finds them; null otherwise.
	 */
	private static String namedLocalOutOfScope(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, Context ctx) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (!local.desc.equals(LOCAL) || !(Annotations.value(local, "name") instanceof List<?> names) || names.isEmpty()) continue;
				for (MethodNode m : targets) {
					if (m.localVariables == null || m.localVariables.stream().noneMatch(lv -> names.contains(lv.name))) continue;
					List<Integer> positions = pointPositions(injector, m, ctx);
					if (positions == null) continue;
					for (int i : positions) {
						if (m.localVariables.stream().noneMatch(lv -> names.contains(lv.name) && m.instructions.indexOf(lv.start) <= i
								&& i < m.instructions.indexOf(lv.end))) return String.valueOf(names.get(0));
					}
				}
			}
		}
		return null;
	}

	/**
	 * The instructions an injector's single {@code @At} naming a call, field access or construction picks in a method, with
	 * its {@code ordinal} and {@code opcode}; null for any other point, several, or a fuzzy target.
	 */
	private static List<Integer> pointPositions(AnnotationNode injector, MethodNode method, Context ctx) {
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return null;
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return null;
		List<Integer> positions = matching(method, point, ref, Annotations.value(at, "opcode") instanceof Integer o ? o : -1);
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (ordinal >= 0) positions = ordinal < positions.size() ? List.of(positions.get(ordinal)) : List.of();
		return positions;
	}

	/** The locals of type {@code desc} in scope at the instruction at {@code index}. */
	private static List<LocalVariableNode> inScope(MethodNode method, int index, String desc) {
		List<LocalVariableNode> found = new ArrayList<>();
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.desc.equals(desc) && method.instructions.indexOf(lv.start) <= index && index < method.instructions.indexOf(lv.end)) found.add(lv);
		}
		return found;
	}

	/**
	 * The ordinal, among the locals of type {@code desc} in scope (parameters first, by slot), of the one of them that is
	 * vanilla's ({@link #isVanillasLocal}), the same at each of {@code positions}; -1 when there aren't several in scope
	 * at one of them, or not exactly one of those is vanilla's.
	 */
	private static int vanillasLocalOrdinal(MethodNode method, String desc, List<Integer> positions, Set<Integer> vanillaPlaces) {
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			int vanillas = 0;
			int chosen = -1;
			for (LocalVariableNode lv : method.localVariables) {
				if (!lv.desc.equals(desc) || method.instructions.indexOf(lv.start) > i || i >= method.instructions.indexOf(lv.end)) continue;
				inScope.add(lv.index);
				if (isVanillasLocal(method, lv, vanillaPlaces)) {
					vanillas++;
					chosen = lv.index;
				}
			}
			if (inScope.size() < 2 || vanillas != 1) return -1;
			int ordinal = inScope.headSet(chosen).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}

	/**
	 * The ordinal, among the locals of type {@code desc} in scope (parameters first, by slot), of the one with ordinal
	 * {@code nth} among those of them that are vanilla's ({@link #isVanillasLocal}), the same at each of {@code positions};
	 * -1 when fewer than {@code nth + 1} of them are vanilla's at one of them.
	 */
	private static int vanillasNthLocalOrdinal(MethodNode method, String desc, List<Integer> positions, Set<Integer> vanillaPlaces, int nth) {
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			TreeSet<Integer> vanillas = new TreeSet<>();
			for (LocalVariableNode lv : method.localVariables) {
				if (!lv.desc.equals(desc) || method.instructions.indexOf(lv.start) > i || i >= method.instructions.indexOf(lv.end)) continue;
				inScope.add(lv.index);
				if (isVanillasLocal(method, lv, vanillaPlaces)) vanillas.add(lv.index);
			}
			if (vanillas.size() <= nth) return -1;
			int chosen = vanillas.stream().skip(nth).findFirst().orElseThrow();
			int ordinal = inScope.headSet(chosen).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}

	/**
	 * Whether a local is vanilla's: a parameter, or one whose scope starts right after a store into its slot that is one
	 * of vanilla's instructions ({@code vanillaPlaces}, their places in this build's method), not one NeoForge's patch put
	 * there.
	 */
	private static boolean isVanillasLocal(MethodNode method, LocalVariableNode local, Set<Integer> vanillaPlaces) {
		AbstractInsnNode store = local.start.getPrevious();
		while (store != null && store.getOpcode() < 0) store = store.getPrevious();
		if (store == null) {
			int parameterSlots = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
			for (Type type : Type.getArgumentTypes(method.desc)) parameterSlots += type.getSize();
			return local.index < parameterSlots;
		}
		return store instanceof VarInsnNode var && var.getOpcode() >= Opcodes.ISTORE && var.getOpcode() <= Opcodes.ASTORE && var.var == local.index
				&& vanillaPlaces.contains(method.instructions.indexOf(store));
	}

	/**
	 * The one call, among the patched instructions {@code range} ({@code [from, to)}, null for none), of an overload of the
	 * named method taking one more argument (as {@code owner}, the named method's class, tells it); null when there is none
	 * or more than one.
	 */
	private static MethodInsnNode replacingOverload(MethodNode patched, int[] range, MemberRef ref, ClassNode owner) {
		if (range == null) return null;
		MethodInsnNode found = null;
		for (int i = range[0]; i < range[1]; i++) {
			if (!(patched.instructions.get(i) instanceof MethodInsnNode call) || !call.owner.equals(ref.owner()) || !call.name.equals(ref.name())
					|| insertedArgument(owner, ref.name(), ref.desc(), call.desc) < 0) continue;
			if (found != null) return null;
			found = call;
		}
		return found;
	}

	/** The indices of {@code method}'s instructions a point ({@code INVOKE}, {@code FIELD} or {@code NEW}) matches, in order. */
	private static List<Integer> matching(MethodNode method, String point, MemberRef ref, int opcode) {
		List<Integer> found = new ArrayList<>();
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if ((opcode < 0 || insn.getOpcode() == opcode) && InjectionPoints.matchesInsn(point, ref, insn)) found.add(i);
		}
		return found;
	}

	/** Vanilla's version of a game class (parsed once per pre-flight), or null. */
	private ClassNode vanillaClass(String internalName) {
		return vanillaCache.computeIfAbsent(internalName, n -> Optional.ofNullable(vanilla.readNode(n, 0))).orElse(null);
	}

	/**
	 * Gives a {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} handler written for a call of
	 * {@code owner} with {@code desc} a bridge taking the {@code overload} its point moves to, which has one more argument
	 * at {@code inserted}, and moves the injector onto it. Whether it could.
	 */
	private static boolean bridgeTo(ClassNode mixin, MethodNode handler, AnnotationNode injector, String owner, String desc, String overload, int inserted,
			boolean staticCall, List<MethodNode> bridges) {
		Type ownerType = Type.getObjectType(owner);
		MethodNode bridge = injector.desc.equals(WRAP_OPERATION) ? HandlerBridges.wrapperBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall, bridges)
				: HandlerBridges.callBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall,
						WRAP_WITH_CONDITION.contains(injector.desc) ? Type.BOOLEAN_TYPE : Type.getReturnType(desc), bridges);
		if (bridge == null) return false;
		HandlerBridges.moveInjector(handler, injector, bridge);
		bridges.add(bridge);
		return true;
	}

	/**
	 * Points the {@code INVOKE} and {@code NEW} points of an injector at the call this game build makes instead of the named one, an
	 * overload of the same method taking one more argument (NeoForge's patches pass an item stack or a level along that
	 * way), when the named call is in none of the methods the injector targets and that overload is the only one there.
	 * The handler of an {@code @Inject} or {@code @ModifyExpressionValue} doesn't see the arguments, so its {@code INVOKE}
	 * point also moves to an overload that takes more after them ({@link #appendsArguments}). One of a
	 * {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} that takes exactly the call's receiver and
	 * arguments (and the {@code Operation}), and then any MixinExtras sugar, gets a bridge taking the overload's, which calls it without the added
	 * argument; for {@code @WrapOperation}, with an {@code Operation} that puts it back when the original call is made.
	 * A {@code NEW} point selecting a constructor by its descriptor is moved the same way, to a constructor taking one
	 * more argument (NeoForge's patches give records components): its handlers take the arguments, as a static call's.
	 * An {@code INVOKE} point at a call NeoForge's patches moved into a hook of theirs moves to the hook's call
	 * ({@link #hookCall}), its handlers bridged the same way when the hook takes one more argument, when the handler shares
	 * none of the mixin's own state with its other injectors ({@link #independentHandlers}) (HOOK-2).
	 * Returns a description of each move.
	 */
	private List<String> insertedArgumentRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		// Only the point moves for these: their handlers don't see the call's arguments.
		boolean pointOnly = injector.desc.equals(INJECT) || injector.desc.equals(MODIFY_EXPRESSION_VALUE);
		boolean condition = WRAP_WITH_CONDITION.contains(injector.desc);
		if (!pointOnly && !condition && !injector.desc.equals(REDIRECT) && !injector.desc.equals(WRAP_OPERATION) || Annotations.value(injector, "slice") != null
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		List<MethodNode> methods = selectedMethods(injector, target, ctx);
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (methods.isEmpty() || !pointOnly && ats.size() != 1) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			boolean constructor = point.equals("NEW");
			if (!point.equals("INVOKE") && !constructor || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			// A call is owner.name(desc). A constructor is selected by a descriptor (args)Lowner; which its handlers are written
			// for as for a static factory: compared, and bridged, in that form.
			String owner;
			String name;
			String desc;
			if (constructor) {
				desc = ref.methodDescOr(null);
				if (desc == null || Type.getReturnType(desc).getSort() != Type.OBJECT || condition) continue;
				owner = Type.getReturnType(desc).getInternalName();
				name = "<init>";
			} else {
				if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
				owner = ref.owner();
				name = ref.name();
				desc = ref.desc();
			}
			String overload = null;
			boolean named = false;
			boolean ambiguous = false;
			boolean staticCall = constructor;
			int calls = 0;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(owner) || !call.name.equals(name)) continue;
					String callDesc = constructor ? call.desc.substring(0, call.desc.indexOf(')') + 1) + "L" + owner + ";" : call.desc;
					if (callDesc.equals(desc)) {
						named = true;
					} else if (insertedArgument(desc, callDesc) >= 0 || pointOnly && !constructor && appendsArguments(desc, callDesc)) {
						ambiguous |= overload != null && !overload.equals(callDesc);
						overload = callDesc;
						staticCall |= call.getOpcode() == Opcodes.INVOKESTATIC;
						calls++;
					}
				}
			}
			// The call itself, when it is in none of the methods: in a hook of NeoForge's that took it over, a static method
			// taking its receiver first, handled as a static call of that.
			MethodInsnNode hook = named || overload != null || constructor ? null : hookCall(methods, owner, name, desc);
			// The call of a method NeoForge deprecated, made to its replacement instead (DEPRECATED-1).
			MethodInsnNode replacement = named || overload != null || constructor || hook != null ? null : replacementCall(methods, owner, name, desc);
			// An @Inject sees none of the call's arguments: any call of NeoForge's that leads to it will do (HOOK-3).
			MethodInsnNode leading = hook != null || replacement != null || named || overload != null || constructor || !injector.desc.equals(INJECT) ? null
					: callLeadingTo(methods, owner, name, desc);
			// A handler sharing the mixin's state with others moves only with them, which this doesn't do: jGui (ReplayMod)
			// keeps the screen's graphics from a @ModifyArg at the same call for its @Inject after it.
			if ((hook != null || leading != null) && !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			if (leading != null) {
				int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
				if (ordinal >= countCalls(methods, leading)) continue;
				for (int i = 0; i + 1 < at.values.size(); i += 2) {
					if ("target".equals(at.values.get(i))) at.values.set(i + 1, "L" + leading.owner + ";" + leading.name + leading.desc);
				}
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which this game build makes through " + loader + "'s "
						+ leading.owner.replace('/', '.') + "." + leading.name + leading.desc + "; the injection point is pointed at that call");
				continue;
			}
			String callOwner = owner;
			String callName = name;
			String original = desc;
			if (hook != null) {
				callOwner = hook.owner;
				callName = hook.name;
				desc = staticForm(owner, desc, isStaticCall(owner, name, desc));
				overload = hook.desc;
				staticCall = true;
				calls = countCalls(methods, hook);
			} else if (replacement != null) {
				callOwner = replacement.owner;
				callName = replacement.name;
				overload = replacement.desc;
				staticCall = replacement.getOpcode() == Opcodes.INVOKESTATIC;
				calls = countCalls(methods, replacement);
			}
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || overload == null || ambiguous || ordinal >= calls) continue;
			int inserted = insertedArgument(desc, overload);
			// A hook taking just the call's receiver and arguments takes the handlers as they are.
			if (!pointOnly && !overload.equals(desc) && !bridgeTo(mixin, handler, injector, owner, desc, overload, inserted, staticCall, bridges)) continue;
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, constructor ? overload : "L" + callOwner + ";" + callName + overload);
			}
			String via = pointOnly || overload.equals(desc) ? "" : ", through a bridge handler";
			if (hook != null) {
				moved.add(owner.replace('/', '.') + "." + name + original + ", which this game build makes inside " + loader + "'s hook "
						+ callOwner.replace('/', '.') + "." + callName + overload + "; the injection point is pointed at the hook's call" + via);
				continue;
			}
			if (replacement != null) {
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which " + loader + " deprecated: this game build calls its replacement "
						+ callOwner.replace('/', '.') + "." + callName + overload + " instead; the injection point is pointed there" + via);
				continue;
			}
			int extra = Type.getArgumentTypes(overload).length - Type.getArgumentTypes(desc).length;
			String more = extra == 1 ? "one more argument" : extra + " more arguments";
			moved.add(constructor
					? "new " + owner.replace('/', '.') + desc.substring(0, desc.indexOf(')') + 1) + ", which this game build makes with a constructor taking " + more + ", "
							+ overload + "; the injection point is pointed there" + via
					: owner.replace('/', '.') + "." + name + desc + ", which this game build makes through an overload with " + more + ", " + name + overload
							+ "; the injection point is pointed there" + via);
		}
		return moved;
	}

	/**
	 * The call, in {@code methods}, of the replacement ({@link #replacementOf}) of {@code owner.name(desc)}, a method
	 * NeoForge's build deprecated: the call that build makes instead, as {@code BoneMealItem.useOn} calls
	 * {@code applyBonemeal} where vanilla's calls {@code growCrop} (Wilder Wild's bone meal hook; DEPRECATED-1). Null
	 * otherwise.
	 */
	private MethodInsnNode replacementCall(List<MethodNode> methods, String owner, String name, String desc) {
		Code named = declaration(owner, name, desc);
		MethodNode replacement = named == null ? null : replacementOf(named.method(), codeOf(named.owner()));
		if (replacement == null) return null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && (call.owner.equals(owner) || call.owner.equals(named.owner()))
						&& call.name.equals(replacement.name) && call.desc.equals(replacement.desc)) return call;
			}
		}
		return null;
	}

	/**
	 * The one call, in {@code methods}, of a static method of NeoForge's that takes the named call's receiver (unless
	 * the call is static) and arguments, with at most one more, returns what the call returns, and makes the call itself,
	 * or through a static method of its class: a hook of NeoForge's patches that took the call over to fire events around
	 * it, as {@code ClientHooks.drawScreen} for {@code Screen.renderWithTooltipAndSubtitles}. Null when there is none, or
	 * more than one such hook (several calls of the same one are fine).
	 */
	private MethodInsnNode hookCall(List<MethodNode> methods, String owner, String name, String desc) {
		String form = staticForm(owner, desc, isStaticCall(owner, name, desc));
		MethodInsnNode found = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || !call.owner.startsWith(NEOFORGE_PACKAGE)
						|| !call.desc.equals(form) && insertedArgument(form, call.desc) < 0) continue;
				if (found != null && found.owner.equals(call.owner) && found.name.equals(call.name) && found.desc.equals(call.desc)) continue;
				if (!makesCall(call.owner, call.name, call.desc, owner, name, desc, 2)) continue;
				if (found != null) return null;
				found = call;
			}
		}
		return found;
	}

	/**
	 * The one call, in {@code methods}, of a method NeoForge declares (in its own classes, or as a default method of its
	 * extension interfaces) that makes the named call, itself or through other methods NeoForge declares: as
	 * {@code BlockBehaviour.onExplosionHit} removing the block through {@code IBlockStateExtension.onBlockExploded} and
	 * {@code IBlockExtension.onBlockExploded} (Ledger's explosion logging). Null when none or several do.
	 */
	private MethodInsnNode callLeadingTo(List<MethodNode> methods, String owner, String name, String desc) {
		MethodInsnNode found = null;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				if (found != null && found.owner.equals(call.owner) && found.name.equals(call.name) && found.desc.equals(call.desc)) continue;
				Code code = declaration(call.owner, call.name, call.desc);
				if (code == null || !code.owner().startsWith(NEOFORGE_PACKAGE) || !leadsTo(code, owner, name, desc, 2)) continue;
				if (found != null) return null;
				found = call;
			}
		}
		return found;
	}

	/** Whether the code makes the call, itself or, to {@code depth}, through methods NeoForge declares that it calls. */
	private boolean leadsTo(Code code, String owner, String name, String desc, int depth) {
		for (AbstractInsnNode insn : code.method().instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) return true;
			if (depth <= 1) continue;
			Code next = declaration(call.owner, call.name, call.desc);
			if (next != null && next.owner().startsWith(NEOFORGE_PACKAGE) && leadsTo(next, owner, name, desc, depth - 1)) return true;
		}
		return false;
	}

	/** A class of the game or NeoForge with its code (parsed once per pre-flight), or null. */
	private ClassNode codeOf(String internalName) {
		return codeCache.computeIfAbsent(internalName, n -> Optional.ofNullable(game.readNode(n))).orElse(null);
	}

	/** A method with code and the class declaring it. */
	private record Code(String owner, MethodNode method) {
	}

	/**
	 * The code a call of {@code owner.name(desc)} runs unless a subclass overrides it: the method in the class or its
	 * superclasses, else a default method of an interface they implement, as far as the game's classes go. Null when
	 * none has code.
	 */
	private Code declaration(String owner, String name, String desc) {
		List<String> interfaces = new ArrayList<>();
		for (String c = owner; c != null; ) {
			ClassNode node = codeOf(c);
			if (node == null) break; // the JDK's, or a library's
			MethodNode m = declared(node, name, desc);
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
			MethodNode m = declared(node, name, desc);
			if (m != null && m.instructions != null && m.instructions.size() > 0) return new Code(itf, m);
			interfaces.addAll(node.interfaces);
		}
		return null;
	}

	/** Whether the static method makes the call, in its own code or, to {@code depth}, in static methods of its class it calls. */
	private boolean makesCall(String hookOwner, String hookName, String hookDesc, String owner, String name, String desc, int depth) {
		ClassNode hookClass = game.readNode(hookOwner);
		MethodNode hook = hookClass == null ? null : declared(hookClass, hookName, hookDesc);
		if (hook == null || (hook.access & Opcodes.ACC_STATIC) == 0 || hook.instructions == null) return false;
		for (AbstractInsnNode insn : hook.instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) return true;
			if (depth > 1 && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(hookOwner)
					&& makesCall(hookOwner, call.name, call.desc, owner, name, desc, depth - 1)) return true;
		}
		return false;
	}

	/** The descriptor of a call as a static method taking the receiver first, unless the call is static already. */
	private static String staticForm(String owner, String desc, boolean isStatic) {
		return isStatic ? desc : "(L" + owner + ";" + desc.substring(1);
	}

	/** Whether the game declares the named method static (one it doesn't declare is taken as an instance method). */
	private boolean isStaticCall(String owner, String name, String desc) {
		ClassNode node = game.readNode(owner);
		MethodNode method = node == null ? null : declared(node, name, desc);
		return method != null && (method.access & Opcodes.ACC_STATIC) != 0;
	}

	private static int countCalls(List<MethodNode> methods, MethodInsnNode target) {
		int calls = 0;
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(target.owner) && call.name.equals(target.name) && call.desc.equals(target.desc)) calls++;
			}
		}
		return calls;
	}

	/**
	 * {@link #insertedArgument(String, String)}, or, where the types can't tell (one more float among floats), what the
	 * named method of {@code owner} tells when it only forwards to the overload: NeoForge keeps a method it gave a
	 * parameter, deprecated, passing its own parameters in order and a default for the new one
	 * ({@code Camera.setRotation(yRot, xRot)} calls {@code setRotation(yRot, xRot, 0F)}). -1 when neither tells.
	 */
	static int insertedArgument(ClassNode owner, String name, String desc, String overload) {
		int byTypes = insertedArgument(desc, overload);
		if (byTypes >= 0 || owner == null || Type.getArgumentTypes(overload).length != Type.getArgumentTypes(desc).length + 1) return byTypes;
		MethodNode old = declared(owner, name, desc);
		if (old == null || old.instructions == null) return -1;
		boolean isStatic = (old.access & Opcodes.ACC_STATIC) != 0;
		// What each value pushed before the call is: the old method's parameter it loads, or -1 for anything else.
		Map<Integer, Integer> parameterAt = new HashMap<>();
		int slot = isStatic ? 0 : 1;
		Type[] params = Type.getArgumentTypes(desc);
		for (int i = 0; i < params.length; i++) {
			parameterAt.put(slot, i);
			slot += params[i].getSize();
		}
		List<Integer> pushed = new ArrayList<>();
		for (AbstractInsnNode insn : old.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(owner.name) || !call.name.equals(name) || !call.desc.equals(overload)) return -1;
				if (!isStatic && (pushed.isEmpty() || pushed.remove(0) != THIS)) return -1;
				if (pushed.size() != params.length + 1) return -1;
				int inserted = -1;
				for (int k = 0, own = 0; k < pushed.size(); k++) {
					if (pushed.get(k) == own) {
						own++;
					} else if (inserted < 0 && pushed.get(k) == -1) {
						inserted = k;
					} else {
						return -1;
					}
				}
				return inserted;
			}
			if (insn instanceof VarInsnNode load && insn.getOpcode() >= Opcodes.ILOAD && insn.getOpcode() <= Opcodes.ALOAD) {
				pushed.add(!isStatic && load.var == 0 && insn.getOpcode() == Opcodes.ALOAD ? THIS : parameterAt.getOrDefault(load.var, -2));
			} else if (insn.getOpcode() >= Opcodes.ACONST_NULL && insn.getOpcode() <= Opcodes.LDC) {
				pushed.add(-1); // a constant
			} else {
				return -1; // more than forwarding
			}
		}
		return -1;
	}

	/**
	 * The index of the one parameter {@code overload} adds to {@code desc}'s, with the same result, or -1: when it adds
	 * none or several, or when which one is the added can't be told (next to another of its type).
	 */
	static int insertedArgument(String desc, String overload) {
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length != own.length + 1 || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return -1;
		int found = -1;
		for (int added = 0; added < all.length; added++) {
			boolean fits = true;
			for (int i = 0, j = 0; i < all.length && fits; i++) {
				if (i != added) fits = all[i].equals(own[j++]);
			}
			if (!fits) continue;
			if (found >= 0) return -1;
			found = added;
		}
		return found;
	}

	/**
	 * Whether {@code overload} takes {@code desc}'s parameters and more after them, with the same result: what NeoForge's
	 * patches call to pass more along, as its {@code CustomPacketPayload.codec} with the protocol and the direction
	 * (Polymer's codec hooks, PRE-13). Only a point that sees no argument (an {@code @Inject}'s, a
	 * {@code @ModifyExpressionValue}'s) moves to any such overload.
	 */
	static boolean appendsArguments(String desc, String overload) {
		Type[] own = Type.getArgumentTypes(desc);
		Type[] all = Type.getArgumentTypes(overload);
		if (all.length <= own.length || !Type.getReturnType(desc).equals(Type.getReturnType(overload))) return false;
		for (int i = 0; i < own.length; i++) {
			if (!all[i].equals(own[i])) return false;
		}
		return true;
	}

	/**
	 * Bridges an {@code @Inject} handler taking the parameters vanilla's method has, then the callback info and MixinExtras
	 * sugar, into the one method its selector picks, which NeoForge's build declares with one more parameter: the bridge
	 * takes the method's parameters, and the sugar, and calls the handler without the added one, which Mixin would refuse.
	 * {@link #capturedValueRepairs} bridges such a handler without sugar (SIG-1); Ledger's hooks into
	 * {@code FireBlock.checkBurnOut}, which NeoForge's build gives the side the fire burns from, take a {@code @Local}
	 * after the callback info. The injection points must be in that method. Returns a description of the bridge.
	 */
	private static List<String> addedParameterRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(INJECT) || Annotations.value(injector, "locals") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw)) return List.of();
		List<Type> args = handlerArgs(handler);
		if (args == null || args.size() < 2 || !isCallbackInfo(args.get(args.size() - 1))) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null) return List.of();
		MethodNode chosen = target.methods.stream().filter(m -> ref.matchesMethod(m.name, m.desc)).findFirst().orElse(null);
		if (chosen == null) return List.of();
		List<Type> own = args.subList(0, args.size() - 1);
		// The sugar follows the callback info, where the bridge takes it over.
		if (!List.of(Type.getArgumentTypes(handler.desc)).subList(0, args.size()).equals(args)) return List.of();
		String vanilla = Type.getMethodDescriptor(Type.getReturnType(chosen.desc), own.toArray(Type[]::new));
		int inserted = insertedArgument(vanilla, chosen.desc);
		if (inserted < 0 || InjectionPoints.pointsIn(Annotations.list(Annotations.value(injector, "at")),
				Annotations.list(Annotations.value(injector, "slice")), chosen, ctx) != Verdict.FOUND) return List.of();
		bridges.add(HandlerBridges.insertedParameterBridge(mixin.name, handler, injector, 0, own.size(), chosen, inserted,
				HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		return List.of(chosen.name + vanilla + ", which this game build declares with one more parameter, " + chosen.name + chosen.desc
				+ "; the handler is called through a bridge taking it");
	}

	/**
	 * Points a {@code @ModifyConstant} whose constants are in none of the methods it targets at the code NeoForge's
	 * patches split off from one ({@link #splitOff}), when they all are there: Carpet's update flag in
	 * {@code Level.setBlock}, now in {@code markAndNotifyBlock} (SPLIT-2). Only a handler taking the constant alone moves
	 * (others take the method's parameters too), and one sharing none of its mixin's state with its other injectors.
	 * Returns a description of each move.
	 */
	private List<String> constantSplitRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
		if (!injector.desc.equals(MODIFY_CONSTANT) || !(Annotations.value(injector, "method") instanceof List<?> selectors)
				|| Annotations.value(injector, "slice") != null || handlerArgs(handler) == null || handlerArgs(handler).size() != 1) return List.of();
		List<AnnotationNode> constants = Annotations.list(Annotations.value(injector, "constant"));
		if (constants.isEmpty() || constants.stream().anyMatch(c -> Annotations.value(c, "ordinal") != null || Annotations.value(c, "slice") != null
				|| InjectionPoints.constantDescription(c) == null)) return List.of();
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) continue;
			MethodNode chosen = null;
			for (MethodNode m : target.methods) {
				if (ref.matchesMethod(m.name, m.desc)) {
					chosen = m;
					break;
				}
			}
			MethodNode from = chosen;
			if (from == null || constants.stream().anyMatch(c -> InjectionPoints.constantVerdict(c, from) != Verdict.MISSING)) continue;
			MethodNode split = splitOff(from, target, m -> constants.stream().allMatch(c -> InjectionPoints.constantVerdict(c, m) == Verdict.FOUND));
			if (split == null || (split.access & Opcodes.ACC_STATIC) != (from.access & Opcodes.ACC_STATIC)
					|| !independentOf(mixin).contains(handler.name + handler.desc)) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, split.name + split.desc);
			moved.add(from.name + from.desc + ", the code of which there " + loader + " split off into " + split.name + split.desc
					+ "; the injector is pointed there");
		}
		return moved;
	}

	/**
	 * The one method of the class {@code method} calls in which the injection points are, when none of them is anywhere
	 * in {@code method} (no ordinal, no slice) and nothing else in the class calls it: code NeoForge's patches split off
	 * into a method of their own, as {@code Level.setBlock}'s neighbour updates into {@code markAndNotifyBlock}
	 * (SPLIT-1). Null when none or several are.
	 */
	private static MethodNode splitOff(MethodNode method, ClassNode target, List<AnnotationNode> ats, List<AnnotationNode> slices, Context ctx) {
		if (!slices.isEmpty() || ats.stream().anyMatch(at -> Annotations.value(at, "ordinal") != null)) return null;
		return splitOff(method, target, callee -> InjectionPoints.pointsIn(ats, slices, callee, ctx) == Verdict.FOUND);
	}

	/**
	 * The one method of the class {@code method} calls that {@code holds} the injection points, when nothing else in the
	 * class calls it but methods that only pass their parameters on to it: NeoForge's way of keeping an entry point that
	 * skips its event, as {@code PersistentEntitySectionManager.addNewEntityWithoutEvent} next to {@code addEntity}, whose
	 * code it split off into {@code addEntityWithoutEvent} (SPLIT-2). Such a method is how this game build gets to the code
	 * a vanilla method of the class did. Null when none or several are.
	 */
	private static MethodNode splitOff(MethodNode method, ClassNode target, Predicate<MethodNode> holds) {
		MethodNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name)) continue;
			MethodNode callee = declared(target, call.name, call.desc);
			if (callee == null || callee == method || callee == found || callee.name.startsWith("<") || !holds.test(callee)) continue;
			if (found != null) return null;
			found = callee;
		}
		if (found == null) return null;
		int calls = 0;
		for (MethodNode m : target.methods) {
			if (m != method && onlyCalls(m, target, found)) continue;
			for (AbstractInsnNode insn : m.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(target.name) && call.name.equals(found.name) && call.desc.equals(found.desc)) calls++;
			}
		}
		return calls == 1 ? found : null;
	}

	/** Whether {@code method} does nothing but call {@code callee} of its class, with its parameters and constants, and return. */
	private static boolean onlyCalls(MethodNode method, ClassNode target, MethodNode callee) {
		boolean called = false;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (called) return insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN;
			if (insn instanceof MethodInsnNode call) {
				if (!call.owner.equals(target.name) || !call.name.equals(callee.name) || !call.desc.equals(callee.desc)) return false;
				called = true;
			} else if (!(insn instanceof VarInsnNode load && load.getOpcode() >= Opcodes.ILOAD && load.getOpcode() <= Opcodes.ALOAD)
					&& !(insn.getOpcode() >= Opcodes.ACONST_NULL && insn.getOpcode() <= Opcodes.LDC)) {
				return false;
			}
		}
		return false;
	}

	/**
	 * The method NeoForge's build replaced {@code method} with: the one method of the class it calls taking its parameters
	 * and one more, when NeoForge marks {@code method} deprecated, which vanilla's methods never are (DEPRECATED-1).
	 * {@code BoneMealItem.growCrop}'s code is in {@code applyBonemeal} there, which also takes the player. Null otherwise.
	 */
	private static MethodNode replacementOf(MethodNode method, ClassNode target) {
		if ((method.access & Opcodes.ACC_DEPRECATED) == 0 && Annotations.find(method, "Ljava/lang/Deprecated;") == null) return null;
		MethodNode found = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || insertedArgument(method.desc, call.desc) < 0) continue;
			MethodNode callee = declared(target, call.name, call.desc);
			if (callee == null || callee == found || (callee.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) continue;
			if (found != null) return null;
			found = callee;
		}
		return found;
	}

	/** The one other overload of the same name {@code method} calls, or null (none, or several). */
	private static MethodNode calledOverload(MethodNode method, ClassNode target) {
		MethodNode overload = null;
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(target.name) || !call.name.equals(method.name)
					|| call.desc.equals(method.desc)) continue;
			MethodNode candidate = declared(target, call.name, call.desc);
			if (candidate == null || candidate == overload) continue;
			if (overload != null) return null; // several overloads: ambiguous
			overload = candidate;
		}
		return overload;
	}

	/**
	 * The index of the one parameter {@code overload} has that {@code method} doesn't, when {@code method} only calls
	 * {@code overload} with its own parameters in order and a value it works out for that one (no stores or jumps), and
	 * returns the result: NeoForge's way of adding a parameter while keeping vanilla's signature, as
	 * {@code MappedRegistry.register(key, value, info)} calling {@code register(this.byId.size(), key, value, info)}. -1
	 * otherwise.
	 */
	static int insertedParameter(MethodNode method, MethodNode overload) {
		Type[] own = Type.getArgumentTypes(method.desc);
		Type[] all = Type.getArgumentTypes(overload.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		if (all.length != own.length + 1 || (overload.access & Opcodes.ACC_STATIC) != (method.access & Opcodes.ACC_STATIC)) return -1;
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= 0) code.add(insn); // not labels, line numbers or frames
		}
		if (code.size() < 3 || !(code.get(code.size() - 2) instanceof MethodInsnNode call) || !call.name.equals(overload.name)
				|| !call.desc.equals(overload.desc) || code.get(code.size() - 1).getOpcode() != Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)) return -1;
		int[] slots = new int[own.length];
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < own.length; i++) {
			slots[i] = slot;
			slot += own[i].getSize();
		}
		int start = 0;
		if (!isStatic) {
			if (!(code.get(0) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return -1;
			start = 1;
		}
		int k = 0;
		while (k < own.length && isLoad(code.get(start), own[k], slots[k])) {
			start++;
			k++;
		}
		int end = code.size() - 2;
		int j = own.length;
		while (j > k && end > start && isLoad(code.get(end - 1), own[j - 1], slots[j - 1])) {
			end--;
			j--;
		}
		if (j != k || start == end) return -1;
		for (int i = start; i < end; i++) {
			int op = code.get(i).getOpcode();
			boolean stores = op >= Opcodes.ISTORE && op <= Opcodes.SASTORE || op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC || op == Opcodes.IINC;
			if (stores || code.get(i) instanceof JumpInsnNode || code.get(i) instanceof TableSwitchInsnNode || code.get(i) instanceof LookupSwitchInsnNode
					|| op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) return -1;
		}
		for (int i = 0; i < own.length; i++) {
			if (!own[i].equals(all[i < k ? i : i + 1])) return -1;
		}
		return k;
	}

	private static boolean isLoad(AbstractInsnNode insn, Type type, int slot) {
		return insn instanceof VarInsnNode load && load.getOpcode() == type.getOpcode(Opcodes.ILOAD) && load.var == slot;
	}

	/**
	 * Where a handler of an injector other than {@code @Inject} takes {@code method}'s parameters (all of them, as Mixin
	 * requires), after its own and before any MixinExtras sugar; -1 when it doesn't take them.
	 */
	private static int capturedFrom(MethodNode handler, MethodNode method) {
		List<Type> args = handlerArgs(handler);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		if (args == null || params.isEmpty() || args.size() <= params.size()) return -1;
		return args.subList(args.size() - params.size(), args.size()).equals(params) ? args.size() - params.size() : -1;
	}

	/**
	 * How the handler's {@code @Local}s find their locals in {@code overload}, an injector moved there from the method
	 * that only calls it: by name, when the overload has a local of that name; by type, when one local of that type
	 * besides the parameter the method inserted ({@code inserted}) is in scope at each instruction the injection point
	 * picks ({@link #pointPositions}), which the {@code @Local} then names by its ordinal (MixinExtras counts the
	 * parameters too): architectury's explosion hook takes the entities' list in {@code ServerExplosion.hurtEntities},
	 * whose overload takes the exploded blocks' list too; or, as {@link #localsFit} allows, a parameter. The changes to
	 * make, or null when one can't be found that way.
	 */
	private static List<Runnable> localsInOverload(MethodNode handler, MethodNode overload, int inserted, List<AnnotationNode> ats, Context ctx) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(overload.desc);
		boolean isStatic = (overload.access & Opcodes.ACC_STATIC) != 0;
		int insertedSlot = -1;
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < params.length; i++) {
			if (i == inserted) insertedSlot = slot;
			slot += params[i].getSize();
		}
		List<Runnable> changes = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(LOCAL)) continue;
				Type type = types[i];
				if (Boolean.TRUE.equals(Annotations.value(a, "argsOnly"))) {
					if (List.of(params).stream().filter(type::equals).count() != 1) return null;
				} else if (Annotations.value(a, "name") instanceof List<?> names && !names.isEmpty()) {
					if (overload.localVariables == null || overload.localVariables.stream().noneMatch(lv -> names.contains(lv.name))) return null;
				} else if (Annotations.value(a, "index") != null || Annotations.value(a, "ordinal") != null
						|| type.getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) {
					return null;
				} else {
					List<Integer> positions = pointPositions(ats, overload, ctx);
					int ordinal = positions == null ? -1 : ordinalAt(overload, type, insertedSlot, positions);
					if (ordinal < 0) return null;
					changes.add(() -> {
						a.values = a.values == null ? new ArrayList<>() : a.values;
						a.values.add("ordinal");
						a.values.add(ordinal);
					});
				}
			}
		}
		return changes;
	}

	/**
	 * The instructions of {@code method}, by index, at which an injector's points inject: each return, for points at the
	 * end; for one point naming a call, field access or construction (not shifted after it), each instruction it
	 * matches, or its ordinal-th. Null for any other point, or when it matches none.
	 */
	private static List<Integer> pointPositions(List<AnnotationNode> ats, MethodNode method, Context ctx) {
		List<Integer> positions = new ArrayList<>();
		if (atEndOnly(ats)) {
			for (int i = 0; i < method.instructions.size(); i++) {
				int op = method.instructions.get(i).getOpcode();
				if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) positions.add(i);
			}
			return positions.isEmpty() ? null : positions;
		}
		if (ats.size() != 1) return null;
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!List.of("INVOKE", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)
				|| Annotations.value(at, "shift") != null || Annotations.value(at, "by") != null) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return null;
		int opcode = Annotations.value(at, "opcode") instanceof Integer o ? o : -1;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if ((opcode < 0 || insn.getOpcode() == opcode) && InjectionPoints.matchesInsn(point, ref, insn)) positions.add(i);
		}
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
		if (ordinal >= 0) return ordinal < positions.size() ? List.of(positions.get(ordinal)) : null;
		return positions.isEmpty() ? null : positions;
	}

	/**
	 * The ordinal, among the locals of {@code type} in scope (parameters first, by slot), of the one besides the parameter
	 * at {@code insertedSlot} that is in scope at each of {@code positions} in {@code method}; -1 when there is no such
	 * single local (or no local variable table).
	 */
	private static int ordinalAt(MethodNode method, Type type, int insertedSlot, List<Integer> positions) {
		if (method.localVariables == null || method.localVariables.isEmpty()) return -1;
		int found = -1;
		for (int i : positions) {
			TreeSet<Integer> inScope = new TreeSet<>();
			for (LocalVariableNode lv : method.localVariables) {
				if (lv.desc.equals(type.getDescriptor()) && method.instructions.indexOf(lv.start) <= i && i < method.instructions.indexOf(lv.end)) {
					inScope.add(lv.index);
				}
			}
			TreeSet<Integer> others = new TreeSet<>(inScope);
			others.remove(insertedSlot);
			if (others.size() != 1) return -1;
			int ordinal = inScope.headSet(others.first()).size();
			if (found >= 0 && found != ordinal) return -1;
			found = ordinal;
		}
		return found;
	}

	/**
	 * The constructor {@code constructor} hands everything to, when it is vanilla's signature as NeoForge keeps it: its
	 * body is {@code this(...)} (vanilla's arguments plus NeoForge's defaults) and nothing after it, and it is marked
	 * deprecated or passes its own parameters followed by constants for parameters of NeoForge's types (as
	 * {@code BakedQuad}'s, a record NeoForge added components to). The game's own code calls the other constructor, so
	 * an injection at the end of this one would never run, while the end of the other one is the same moment for both.
	 * Null for any other constructor: vanilla's own delegating constructors (an injection there is meant for that one
	 * only) are never deprecated and pass no NeoForge types.
	 */
	private static MethodNode keptConstructorDelegate(MethodNode constructor, ClassNode target) {
		if (!constructor.name.equals("<init>")) return null;
		MethodNode delegate = thisCall(constructor, target);
		if (delegate == null) return null;
		boolean deprecated = (constructor.access & Opcodes.ACC_DEPRECATED) != 0 || Annotations.find(constructor, "Ljava/lang/Deprecated;") != null;
		if (deprecated) return delegate;
		Type[] all = Type.getArgumentTypes(delegate.desc);
		int own = Type.getArgumentTypes(constructor.desc).length;
		boolean neoForgeAdded = false;
		for (int i = own; i < all.length; i++) neoForgeAdded |= all[i].getSort() == Type.OBJECT && all[i].getInternalName().startsWith("net/neoforged/");
		return neoForgeAdded && forwardsItsParameters(constructor, delegate) ? delegate : null;
	}

	/** The constructor of {@code target} that {@code constructor}'s body, {@code this(...)} and nothing after it, calls; or null. */
	private static MethodNode thisCall(MethodNode constructor, ClassNode target) {
		MethodNode delegate = null;
		for (AbstractInsnNode insn : constructor.instructions) {
			if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
			if (delegate != null) {
				if (insn.getOpcode() != Opcodes.RETURN) return null; // does more after this(...)
			} else if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(target.name)) {
				return null; // makes another instance: its constructor call is not this(...)
			} else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>")
					&& call.owner.equals(target.name)) {
				delegate = declared(target, call.name, call.desc);
				if (delegate == null) return null;
			}
		}
		return delegate;
	}

	/**
	 * Whether {@code constructor} is {@code this(its parameters in order, then constants)}: the other constructor gets
	 * exactly what it got, plus defaults.
	 */
	private static boolean forwardsItsParameters(MethodNode constructor, MethodNode delegate) {
		Type[] own = Type.getArgumentTypes(constructor.desc);
		Type[] all = Type.getArgumentTypes(delegate.desc);
		if (all.length <= own.length) return false;
		for (int i = 0; i < own.length; i++) {
			if (!own[i].equals(all[i])) return false;
		}
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : constructor.instructions) {
			if (insn.getOpcode() >= 0) code.add(insn); // not labels, line numbers or frames
		}
		if (code.size() != own.length + (all.length - own.length) + 3) return false;
		if (!(code.get(0) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return false;
		int slot = 1;
		for (int i = 0; i < own.length; i++) {
			if (!(code.get(1 + i) instanceof VarInsnNode load) || load.getOpcode() != own[i].getOpcode(Opcodes.ILOAD) || load.var != slot) return false;
			slot += own[i].getSize();
		}
		for (int i = 1 + own.length; i < code.size() - 2; i++) {
			int op = code.get(i).getOpcode();
			boolean constant = op >= Opcodes.ACONST_NULL && op <= Opcodes.DCONST_1 || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH
					|| op == Opcodes.LDC || op == Opcodes.GETSTATIC;
			if (!constant) return false;
		}
		return code.get(code.size() - 2) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.desc.equals(delegate.desc)
				&& code.get(code.size() - 1).getOpcode() == Opcodes.RETURN;
	}

	/** Whether {@code handler} takes {@code method}'s parameters and a callback info, and nothing else (no captured locals or sugar). */
	private static boolean takesExactly(MethodNode handler, MethodNode method) {
		Type[] handlerTypes = Type.getArgumentTypes(handler.desc);
		Type[] params = Type.getArgumentTypes(method.desc);
		if (handlerTypes.length != params.length + 1 || !isCallbackInfo(handlerTypes[params.length])) return false;
		for (int i = 0; i < params.length; i++) {
			if (!handlerTypes[i].equals(params[i]) || !Annotations.ofParameter(handler, i).isEmpty()) return false;
		}
		return true;
	}

	/** Whether every injection point is the method's end ({@code RETURN} or {@code TAIL}). */
	private static boolean atEndOnly(List<AnnotationNode> ats) {
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("RETURN") && !point.equals("TAIL")) return false;
		}
		return true;
	}

	/** Makes an {@code @Inject} whose local capture fails hard skip itself instead; returns whether it changed. */
	private static boolean softenLocalCapture(AnnotationNode injector) {
		if (!injector.desc.equals(INJECT) || !(Annotations.value(injector, "locals") instanceof String[] capture)
				|| !capture[1].equals("CAPTURE_FAILHARD") && !capture[1].equals("CAPTURE_FAILEXCEPTION")) return false;
		for (int i = 0; i + 1 < injector.values.size(); i += 2) {
			if ("locals".equals(injector.values.get(i))) injector.values.set(i + 1, new String[] {capture[0], "CAPTURE_FAILSOFT"});
			if ("require".equals(injector.values.get(i))) injector.values.set(i + 1, 0);
		}
		if (!(Annotations.value(injector, "require") instanceof Integer)) {
			injector.values.add("require");
			injector.values.add(0);
		}
		return true;
	}

	/**
	 * Whether the handler's {@code @Local}s still find what they captured in {@code method}: each must be an
	 * {@code @Local(argsOnly = true)} of a type exactly one of {@code method}'s parameters has (other locals differ).
	 */
	private static boolean localsFit(MethodNode handler, MethodNode method) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(LOCAL)) continue;
				Type local = types[i];
				if (!Boolean.TRUE.equals(Annotations.value(a, "argsOnly")) || local.getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")
						|| params.stream().filter(local::equals).count() != 1) return false;
			}
		}
		return true;
	}

	/**
	 * Points an {@code @Accessor} of a missing captured variable field ({@code val$name}) at the only captured field of
	 * the same type that every target has under one other name, and returns the original field name; or returns
	 * {@code null} and changes nothing.
	 */
	private static String capturedFieldRepair(MethodNode method, AnnotationNode accessor, List<ClassNode> targets, Context ctx) {
		String explicit = Annotations.value(accessor, "value") instanceof String s && !s.isEmpty() ? s : null;
		if (explicit == null) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(explicit));
		String name = ref.nameOr(explicit);
		String desc = ref.fieldDescOr(accessorFieldDesc(method));
		if (!name.startsWith(CAPTURED_PREFIX) || desc == null) return null;
		String renamed = null;
		for (ClassNode target : targets) {
			String only = null;
			for (FieldNode f : target.fields) {
				if (f.name.equals(name)) return null; // present: nothing to repair
				if ((f.access & Opcodes.ACC_STATIC) != 0 || !f.desc.equals(desc)) continue;
				if (!f.name.startsWith(CAPTURED_PREFIX) && (f.access & Opcodes.ACC_SYNTHETIC) == 0) continue;
				if (only != null) return null; // ambiguous
				only = f.name;
			}
			if (only == null || (renamed != null && !renamed.equals(only))) return null;
			renamed = only;
		}
		if (renamed == null) return null;
		for (int i = 0; i + 1 < accessor.values.size(); i += 2) {
			if ("value".equals(accessor.values.get(i))) accessor.values.set(i + 1, renamed);
		}
		return name;
	}

	/**
	 * Renames a {@code @Shadow} field of a missing captured variable field ({@code val$name}) after the only captured field
	 * of the same type that every target has under one other name, as {@link #capturedFieldRepair} does for accessors
	 * (NeoForge's 1.21.11 build names them after the decompiled variables), with the mixin's own uses of it; returns its
	 * original name, or {@code null} and changes nothing.
	 */
	private static String capturedShadowRepair(ClassNode mixin, FieldNode field, List<ClassNode> targets, Context ctx) {
		if (Annotations.find(field, SHADOW) == null || (field.access & Opcodes.ACC_STATIC) != 0) return null;
		String name = MemberRef.parse(ctx.resolve(field.name)).nameOr(field.name);
		if (!name.equals(field.name) || !name.startsWith(CAPTURED_PREFIX)) return null;
		String renamed = null;
		for (ClassNode target : targets) {
			String only = null;
			for (FieldNode f : target.fields) {
				if (f.name.equals(name)) return null; // present: nothing to repair
				if ((f.access & Opcodes.ACC_STATIC) != 0 || !f.desc.equals(field.desc)) continue;
				if (!f.name.startsWith(CAPTURED_PREFIX) && (f.access & Opcodes.ACC_SYNTHETIC) == 0) continue;
				if (only != null) return null; // ambiguous
				only = f.name;
			}
			if (only == null || (renamed != null && !renamed.equals(only))) return null;
			renamed = only;
		}
		if (renamed == null) return null;
		for (MethodNode method : mixin.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode use && use.owner.equals(mixin.name) && use.name.equals(name) && use.desc.equals(field.desc)) {
					use.name = renamed;
				}
			}
		}
		field.name = renamed;
		return name;
	}

	private static boolean isAccessorAnnotation(AnnotationNode annotation) {
		return annotation.desc.equals(ACCESSOR) || annotation.desc.equals(INVOKER);
	}

	private static boolean declaresPublicInstance(ClassNode target, String name, String desc) {
		MethodNode m = declared(target, name, desc);
		return m != null && (m.access & Opcodes.ACC_PUBLIC) != 0 && (m.access & Opcodes.ACC_STATIC) == 0;
	}

	private static boolean declaresInstance(ClassNode target, String name, String desc) {
		MethodNode m = declared(target, name, desc);
		return m != null && (m.access & Opcodes.ACC_STATIC) == 0;
	}

	private static boolean isGameClass(String internalName) {
		return GAME_PREFIXES.stream().anyMatch(internalName::startsWith);
	}

	/** A Minecraft class as the game has it (parsed once per pre-flight), or null when absent or not a game class. */
	private ClassNode gameTarget(String internalName) {
		return isGameClass(internalName) ? targetCache.computeIfAbsent(internalName, t -> Optional.ofNullable(game.readNode(t))).orElse(null) : null;
	}

	/** A class of an installed jar (parsed once per pre-flight), recorded with its supertypes there; null when unreadable. */
	private ClassNode installedTarget(String internalName) {
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
	 * Whether nothing outside a mixin can call into what it adds to its target, so the mod's own code doesn't depend on
	 * it applying: it adds no interface (the mod's classes cast targets to those) and has no accessor or invoker. An enum
	 * mixin (its constants) never is; a mixin that is itself an interface only when it has no abstract method (its
	 * accessors, invokers and shadows are), as owo's handlers into DataFixerUpper's {@code DataResult}.
	 */
	static boolean selfContained(ClassNode mixin) {
		if ((mixin.access & Opcodes.ACC_ENUM) != 0 || !mixin.interfaces.isEmpty()) return false;
		boolean itf = (mixin.access & Opcodes.ACC_INTERFACE) != 0;
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, ACCESSOR) != null || Annotations.find(method, INVOKER) != null) return false;
			if (itf && (method.access & Opcodes.ACC_ABSTRACT) != 0) return false;
		}
		return true;
	}

	/**
	 * Whether a mixin adds fields to its target (any but its {@code @Shadow}s and the compiler's synthetic ones): state its
	 * injectors may share.
	 */
	static boolean addsFields(ClassNode mixin) {
		for (FieldNode field : mixin.fields) {
			if ((field.access & Opcodes.ACC_SYNTHETIC) == 0 && Annotations.find(field, SHADOW) == null) return true;
		}
		return false;
	}

	/**
	 * The handlers (name and descriptor) of a mixin that use none of its own fields and call none of its own methods
	 * (LEAVE-3). When the mixin adds an interface or accessors, which the mod's code uses, such a handler failing is left
	 * out alone: the interface and the mixin's other members still apply, and nothing the handler does is kept for them.
	 * owo-lib's {@code GuiRendererMixin}, which adds an interface, hooks a field read NeoForge's {@code GuiRenderer.close}
	 * doesn't make, only to keep its own renderer from closing shared ones.
	 */
	static Set<String> statelessHandlers(ClassNode mixin) {
		if (mixin == null) return Set.of();
		Set<String> own = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, SHADOW) == null) own.add(field.name + ":" + field.desc);
		}
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, SHADOW) == null) own.add(method.name + method.desc);
		}
		Set<String> stateless = new HashSet<>();
		for (MethodNode method : mixin.methods) {
			if (method.instructions == null || method.instructions.size() == 0) continue; // no code to judge
			boolean usesOwn = false;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode field && field.owner.equals(mixin.name) && own.contains(field.name + ":" + field.desc)
						|| insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && own.contains(call.name + call.desc)
						|| insn instanceof InvokeDynamicInsnNode indy && List.of(indy.bsmArgs).stream()
								.anyMatch(arg -> arg instanceof Handle handle && handle.getOwner().equals(mixin.name))) {
					usesOwn = true;
					break;
				}
			}
			if (!usesOwn) stateless.add(method.name + method.desc);
		}
		return stateless;
	}

	/**
	 * The handlers (name and descriptor) of a mixin's injectors that share none of its own fields with its other
	 * injectors: no other injector's handler uses a field they use, themselves or through the mixin's own methods they
	 * call. Such a handler can be pointed elsewhere alone, as nothing it keeps is another injector's (SPLIT-2). Its state
	 * may be the mixin's other members': architectury's entity manager mixin keeps the level its duck interface is given
	 * for its hook alone. A stateless handler ({@link #statelessHandlers}) is one.
	 */
	private Set<String> independentOf(ClassNode mixin) {
		return independent.computeIfAbsent(mixin, MixinPreflight::independentHandlers);
	}

	static Set<String> independentHandlers(ClassNode mixin) {
		if (mixin == null) return Set.of();
		Set<String> ownFields = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, SHADOW) == null) ownFields.add(field.name + ":" + field.desc);
		}
		Map<String, MethodNode> ownMethods = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, SHADOW) == null) ownMethods.put(method.name + method.desc, method);
		}
		Map<MethodNode, Set<String>> used = new LinkedHashMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.all(method).stream().anyMatch(a -> isInjector(a.desc))) {
				used.put(method, fieldsUsed(mixin, method, ownFields, ownMethods, new HashSet<>()));
			}
		}
		Set<String> independent = new HashSet<>();
		used.forEach((handler, fields) -> {
			if (handler.instructions == null || handler.instructions.size() == 0) return; // no code to judge
			if (used.entrySet().stream().noneMatch(other -> other.getKey() != handler && !Collections.disjoint(fields, other.getValue()))) {
				independent.add(handler.name + handler.desc);
			}
		});
		return independent;
	}

	/** The mixin's own fields {@code method} uses, itself or through the mixin's own methods it calls or refers to. */
	private static Set<String> fieldsUsed(ClassNode mixin, MethodNode method, Set<String> ownFields, Map<String, MethodNode> ownMethods,
			Set<MethodNode> seen) {
		Set<String> fields = new HashSet<>();
		if (!seen.add(method) || method.instructions == null) return fields;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode field && field.owner.equals(mixin.name) && ownFields.contains(field.name + ":" + field.desc)) {
				fields.add(field.name + ":" + field.desc);
			} else if (insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && ownMethods.get(call.name + call.desc) != null) {
				fields.addAll(fieldsUsed(mixin, ownMethods.get(call.name + call.desc), ownFields, ownMethods, seen));
			} else if (insn instanceof InvokeDynamicInsnNode indy) {
				for (Object arg : indy.bsmArgs) {
					if (!(arg instanceof Handle handle) || !handle.getOwner().equals(mixin.name)) continue;
					if (ownFields.contains(handle.getName() + ":" + handle.getDesc())) fields.add(handle.getName() + ":" + handle.getDesc());
					MethodNode callee = ownMethods.get(handle.getName() + handle.getDesc());
					if (callee != null) {
						fields.addAll(fieldsUsed(mixin, callee, ownFields, ownMethods, seen));
					}
				}
			}
		}
		return fields;
	}

	/**
	 * @param selfContained see {@link #selfContained(ClassNode)}: a failure then leaves the failing injector, or the whole
	 *                      mixin, out instead of disabling the mod
	 * @param addsFields    see {@link #addsFields(ClassNode)}: a failing injector of a self-contained mixin then takes the
	 *                      whole mixin with it
	 * @param stateless     see {@link #statelessHandlers(ClassNode)}, for a mixin that isn't self-contained
	 * @param owner         the installed mod the target belongs to, or {@code null} for a game class
	 */
	record Context(String configName, String mixinDotted, MixinConfigFile config, Refmap refmap, boolean hasPlugin,
			boolean selfContained, boolean addsFields, Set<String> stateless, List<PreflightFinding> out, String owner, String loader) {
		String resolve(String selector) {
			return refmap.resolve(mixinDotted.replace('.', '/'), selector);
		}

		Context withOwner(String owner) {
			return new Context(configName, mixinDotted, config, refmap, hasPlugin, selfContained, addsFields, stateless, out, owner, loader);
		}

		/** What changed the target since the mod was built: the loader or this Minecraft version, or this version of the mod. */
		String changer(boolean sentenceStart) {
			if (owner == null) return loader + " or this Minecraft version";
			return (sentenceStart ? "This" : "this") + " version of " + owner;
		}

		/** Whose code the target is: the loader's (and Minecraft's), or the installed mod's. */
		String platform() {
			return owner == null ? loader : owner;
		}

		/** A problem that makes Mixin reject the whole mixin; fatal when the config is required, unless it can be left out. */
		void classLevel(String message, String suggestion) {
			out.add(new PreflightFinding(config.required() ? fatal() : Severity.WARN, configName, mixinDotted, message, suggestion));
		}

		/**
		 * A broken accessor or invoker. Mixin would reject the whole interface, and the mod's code, which casts to it,
		 * would then fail to load it; left out alone, the interface still applies and only a call to this method fails.
		 * Fatal where the config is required and has no plugin, as Mixin would stop the game.
		 */
		void accessorMember(MethodNode method, String message, String suggestion) {
			out.add(new PreflightFinding(config.required() && !hasPlugin ? Severity.FAIL : Severity.LEFT_OUT, configName, mixinDotted, message,
					suggestion, method.name + method.desc));
		}

		/**
		 * A mixin the mod's own code can't do without (the enum constants it adds, which the mod's classes read): fatal
		 * even where a plugin or an optional config would only leave it out, which would move the crash into the mod.
		 */
		void essential(String message, String suggestion) {
			out.add(new PreflightFinding(Severity.FAIL, configName, mixinDotted, message, suggestion));
		}

		/**
		 * An injector that can't apply. Where it is required: with a plugin, in a self-contained mixin that adds no
		 * fields, or with a handler that uses nothing of the mixin's own (LEAVE-3), only it is left out (as a plugin may
		 * do); in a self-contained mixin that adds fields, the whole mixin is (no handler named); otherwise it is fatal.
		 */
		void injector(MethodNode handler, int require, String message, String suggestion) {
			Severity severity = require <= 0 ? Severity.WARN : stateless.contains(handler.name + handler.desc) ? Severity.LEFT_OUT : fatal();
			boolean wholeMixin = severity == Severity.LEFT_OUT && !hasPlugin && addsFields;
			out.add(new PreflightFinding(severity, configName, mixinDotted, message, suggestion, wholeMixin ? null : handler.name + handler.desc));
		}

		/**
		 * A failure that would stop the game: left out when a config plugin may disable the mixin at runtime, or when
		 * the mixin is self-contained, so the mod loads without it; fatal otherwise.
		 */
		private Severity fatal() {
			return hasPlugin || selfContained ? Severity.LEFT_OUT : Severity.FAIL;
		}

		void info(String message) {
			out.add(new PreflightFinding(Severity.INFO, configName, mixinDotted, message, ""));
		}
	}

	private void checkMixin(ClassNode mixin, Context ctx) {
		AnnotationNode mixinAnn = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (mixinAnn == null) {
			ctx.classLevel("Class " + ctx.mixinDotted() + " is listed as a mixin but has no @Mixin annotation", "Report this to the mod author.");
			return;
		}
		boolean pseudo = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, PSEUDO) != null;

		for (String target : targetsOf(mixin, ctx)) {
			boolean isGame = isGameClass(target);
			String owner = isGame ? null : installed.owner(target);
			ClassNode targetNode = isGame ? gameTarget(target) : owner != null ? installedTarget(target) : null;
			if (targetNode == null) {
				// Absent now, it may be installed later: the result holds only while no installed jar has it.
				if (!isGame && owner == null) installedRead.putIfAbsent(target, "");
				if (pseudo) continue;
				if (isGame && missingTargetsFatal) {
					ctx.classLevel("Target class " + target.replace('/', '.') + " does not exist in this Minecraft/" + loader + " version",
							"The mod was likely built for a different Minecraft version, or " + loader + " removed this class.");
				} else if (isGame) {
					ctx.out().add(new PreflightFinding(Severity.WARN, ctx.configName(), ctx.mixinDotted(), "Target class " + target.replace('/', '.')
							+ " does not exist in this Minecraft/" + loader + " version (or on this side); Mixin skips it", ""));
				} else if (isUntransformable(target)) {
					ctx.classLevel("Target class " + target.replace('/', '.') + " is a library class that " + loader + " loads without mixin support, so this mixin would silently never apply",
							"Ask the mod author for a " + loader + " version, or skip this mixin in config/fabricated.json if the feature is optional.");
				} else {
					ctx.info("Target " + target.replace('/', '.') + " is not a class of Minecraft or of an installed mod, so it was not verified");
				}
				continue;
			}
			Context targetCtx = owner == null ? ctx : ctx.withOwner(owner);
			// FML's FMLMixinFeatureValidator stops the game on an enum mixin into one, though Mixin itself could apply it
			// (ENUM-4, which let them through, crashed the advanced corpus on Wilder Wild's).
			if ((mixin.access & Opcodes.ACC_ENUM) != 0 && targetNode.interfaces.contains(EXTENSIBLE_ENUM)) {
				targetCtx.essential("Enum mixin adds constants to " + target.replace('/', '.') + ", one of NeoForge's extensible enums, which"
						+ " NeoForge lets only its own enum extensions change", "Install the NeoForge build of the mod, if it has one.");
				continue;
			}
			checkMembers(mixin, targetNode, targetCtx);
		}
	}

	/**
	 * Points a mixin at the anonymous class of the same game class that fits it, when the one it names doesn't: NeoForge's
	 * build of the game, recompiled from decompiled source, numbers some anonymous classes otherwise than Mojang's, which
	 * mods are built against (Polymer's hooks into {@code ByteBufCodecs}' and {@code ItemStack}'s stream codecs, No Chat
	 * Reports' into the JSON one, Ledger's and malilib's into {@code ChestBlock}'s; ANON-1). A class fits when pre-flight
	 * finds nothing but notes in the mixin against it: its selectors, shadows and injection points are all there, a shadow
	 * of a captured value perhaps under another name ({@link #capturedRenames}). Only a single fitting class is taken.
	 * Returns a description of each move.
	 */
	private List<String> anonymousTargetRepairs(ClassNode mixin, Context ctx) {
		AnnotationNode mixinAnn = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (mixinAnn == null || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !(Annotations.value(mixinAnn, "targets") instanceof List<?> names)) {
			return List.of();
		}
		List<String> moved = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			if (!(names.get(i) instanceof String raw)) continue;
			String target = ctx.resolve(raw).replace('.', '/');
			int dollar = target.lastIndexOf('$');
			if (!isGameClass(target) || dollar < 0 || !target.substring(dollar + 1).matches("\\d+")) continue;
			ClassNode named = gameTarget(target);
			if (named != null && fits(mixin, named, ctx)) continue;
			ClassNode original = vanillaClass(target);
			Map<String, String> namedRenames = named == null ? null : capturedRenames(mixin, named, original);
			if (namedRenames != null && !namedRenames.isEmpty() && fits(renamed(mixin, namedRenames), named, ctx)) {
				renameShadows(mixin, namedRenames);
				moved.add(target.replace('/', '.') + " names the values it captures otherwise in this game build " + namedRenames
						+ "; the mixin's shadows of them are renamed");
				continue;
			}
			ClassNode outer = gameTarget(target.substring(0, dollar));
			if (outer == null) continue;
			ClassNode match = null;
			Map<String, String> matchRenames = Map.of();
			for (InnerClassNode inner : outer.innerClasses) {
				// Anonymous classes have neither a simple name nor an outer class in the attribute.
				if (inner.innerName != null || inner.outerName != null || inner.name.equals(target)
						|| !inner.name.startsWith(outer.name + "$") || inner.name.indexOf('$', outer.name.length() + 1) >= 0) continue;
				ClassNode candidate = gameTarget(inner.name);
				Map<String, String> renames = candidate == null ? null : capturedRenames(mixin, candidate, original);
				if (renames == null || !fits(renames.isEmpty() ? mixin : renamed(mixin, renames), candidate, ctx)) continue;
				if (match != null) {
					match = null;
					break;
				}
				match = candidate;
				matchRenames = renames;
			}
			if (match == null) continue;
			renameShadows(mixin, matchRenames);
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) names;
			values.set(i, raw.indexOf('/') >= 0 ? match.name : match.name.replace('/', '.'));
			moved.add(target.replace('/', '.') + " doesn't fit the mixin in this game build, whose anonymous classes are numbered differently; "
					+ match.name.replace('/', '.') + " does" + (matchRenames.isEmpty() ? "" : ", naming the values it captures otherwise " + matchRenames)
					+ ", and the mixin is pointed there");
		}
		return moved;
	}

	/**
	 * New names for the mixin's shadows of values an anonymous class captures ({@code val$name}) that {@code target} has
	 * under another name: the one captured value of the same type there (CAPTURE-1), or, of several, the one in the place
	 * the shadow's has among vanilla's of that type in {@code original}, the class the mixin names in vanilla's code
	 * ({@link #inVanillasOrder}, CAPTURE-2). NeoForge's 1.21.11 build names them after its own parameter names
	 * (recompiled from decompiled source; inferred from Polymer's {@code val$registryKey} missing there). A shadow in SRG's
	 * names is one of them where vanilla's class declares it synthetic, and takes a synthetic field of its type: Forge
	 * 47's build, recompiled too, names them as javac does (Fabric API's interaction hook shadows {@code f_143670_} of
	 * {@code ServerGamePacketListenerImpl$1}, there {@code val$entity}; CAPTURE-3). Empty when every shadow is there, null
	 * when one's new name can't be told (none of its type, several that vanilla's class doesn't tell apart, or one
	 * another shadow has).
	 */
	private static Map<String, String> capturedRenames(ClassNode mixin, ClassNode target, ClassNode original) {
		Map<String, String> renames = new TreeMap<>();
		Set<String> kept = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, SHADOW) == null) continue;
			// SRG names a captured value, and the outer instance, as it does any field (f_143670_), where Mojang's names
			// are javac's (val$entity, this$0): vanilla's class marks it synthetic (CAPTURE-3).
			boolean javacNamed = field.name.startsWith("val$");
			if (!javacNamed && !synthetic(original, field.desc).contains(field.name)) continue;
			if (target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc))) {
				kept.add(field.name);
				continue;
			}
			List<String> sameType = javacNamed ? captured(target, field.desc) : synthetic(target, field.desc);
			String renamed = sameType.size() == 1 ? sameType.get(0) : inVanillasOrder(field.name, field.desc, javacNamed, sameType, target, original);
			if (renamed == null) return null;
			renames.put(field.name, renamed);
		}
		Set<String> to = new HashSet<>(renames.values());
		return to.size() == renames.size() && Collections.disjoint(to, kept) ? renames : null;
	}

	/** The values an anonymous class captures of one type ({@code val$} fields), in the order it declares them. */
	private static List<String> captured(ClassNode type, String desc) {
		return type.fields.stream().filter(f -> f.name.startsWith("val$") && f.desc.equals(desc)).map(f -> f.name).toList();
	}

	/**
	 * A class's synthetic instance fields of one type, in the order it declares them: an anonymous class's captured values
	 * and outer instance, whatever their names. Empty without the class.
	 */
	private static List<String> synthetic(ClassNode type, String desc) {
		if (type == null) return List.of();
		return type.fields.stream().filter(f -> (f.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_SYNTHETIC && f.desc.equals(desc))
				.map(f -> f.name).toList();
	}

	/**
	 * Which of {@code target}'s captured values of a type is vanilla's {@code name}, when it captures several (Ledger's and
	 * malilib's hooks into {@code ChestBlock}'s menu provider shadow the two chest block entities it captures): the one in
	 * the place {@code name} has among {@code original}'s of the type. The order javac declares captured values in follows
	 * the class's code, which a recompiled build keeps; as that is inferred, not read, the place is taken only when
	 * {@code original} captures as many of the type and every method both classes declare reads them in the same order,
	 * matched by place, with at least one such read. Null otherwise, or without vanilla's class. {@code javacNamed}: the
	 * values go by javac's names ({@code val$name}) in both classes, else by vanilla's synthetic fields under SRG's.
	 */
	private static String inVanillasOrder(String name, String desc, boolean javacNamed, List<String> sameType, ClassNode target, ClassNode original) {
		if (original == null || sameType.size() < 2) return null;
		List<String> originals = javacNamed ? captured(original, desc) : synthetic(original, desc);
		int place = originals.indexOf(name);
		if (place < 0 || originals.size() != sameType.size()) return null;
		Map<String, String> byPlace = new HashMap<>();
		for (int i = 0; i < originals.size(); i++) byPlace.put(originals.get(i), sameType.get(i));
		boolean compared = false;
		for (MethodNode method : original.methods) {
			if (method.name.equals("<init>") || method.instructions == null) continue;
			MethodNode same = target.methods.stream().filter(m -> m.name.equals(method.name) && m.desc.equals(method.desc)).findFirst().orElse(null);
			if (same == null) continue;
			List<String> expected = reads(method, original.name, byPlace.keySet()).stream().map(byPlace::get).toList();
			if (!expected.equals(reads(same, target.name, byPlace.values()))) return null;
			compared |= !expected.isEmpty();
		}
		return compared ? sameType.get(place) : null;
	}

	/** The reads of {@code fields} of {@code owner} in a method, in order. */
	private static List<String> reads(MethodNode method, String owner, Collection<String> fields) {
		List<String> reads = new ArrayList<>();
		if (method.instructions == null) return reads;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETFIELD && read.owner.equals(owner) && fields.contains(read.name)) {
				reads.add(read.name);
			}
		}
		return reads;
	}

	/** A copy of {@code mixin} with its shadows renamed. */
	private static ClassNode renamed(ClassNode mixin, Map<String, String> renames) {
		ClassNode copy = new ClassNode();
		mixin.accept(copy);
		renameShadows(copy, renames);
		return copy;
	}

	/** Renames the mixin's shadow fields, and its code's uses of them. */
	private static void renameShadows(ClassNode mixin, Map<String, String> renames) {
		if (renames.isEmpty()) return;
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, SHADOW) != null) field.name = renames.getOrDefault(field.name, field.name);
		}
		for (MethodNode method : mixin.methods) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof FieldInsnNode use && use.owner.equals(mixin.name)) use.name = renames.getOrDefault(use.name, use.name);
			}
		}
	}

	/** Whether pre-flight finds nothing but notes in {@code mixin} against {@code target}. */
	private boolean fits(ClassNode mixin, ClassNode target, Context ctx) {
		List<PreflightFinding> found = new ArrayList<>();
		checkMembers(mixin, target, new Context(ctx.configName(), ctx.mixinDotted(), ctx.config(), ctx.refmap(), false, false, false, Set.of(), found, null, loader));
		return found.stream().allMatch(f -> f.severity() == Severity.INFO);
	}

	/** The classes a mixin's {@code @Mixin} names, as internal names (none when it has no {@code @Mixin}). */
	private static List<String> targetsOf(ClassNode mixin, Context ctx) {
		AnnotationNode mixinAnn = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		List<String> targets = new ArrayList<>();
		if (mixinAnn == null) return targets;
		if (Annotations.value(mixinAnn, "value") instanceof List<?> types) {
			for (Object t : types) {
				if (t instanceof Type type) targets.add(type.getInternalName());
			}
		}
		if (Annotations.value(mixinAnn, "targets") instanceof List<?> names) {
			for (Object n : names) {
				if (n instanceof String s) targets.add(ctx.resolve(s).replace('.', '/'));
			}
		}
		return targets;
	}

	private boolean isUntransformable(String target) {
		try {
			return untransformable.read(target) != null;
		} catch (IOException e) {
			return false;
		}
	}

	private void checkMembers(ClassNode mixin, ClassNode target, Context ctx) {
		int findingsBefore = ctx.out().size();
		checkMembersOf(mixin, target, ctx);
		checkGroups(mixin, ctx, findingsBefore);
	}

	/**
	 * Mixin checks the injectors of a named group ({@code @Group}) together: fewer than its minimum applying stops the
	 * game, though each requires nothing alone (owo-lib's hooks after Fabric's and Quilt's loader calls stopped three
	 * corpus servers before HOOK-1; GROUP-1). Each member pre-flight found can't apply has a warning from {@code from}
	 * on; when too few are left, every member fails as a required injector would, so a self-contained mixin loses the
	 * whole group, which Mixin then doesn't check.
	 */
	private static void checkGroups(ClassNode mixin, Context ctx, int from) {
		Map<String, Integer> minimums = new TreeMap<>();
		Map<String, List<MethodNode>> members = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			AnnotationNode group = Annotations.find(method, GROUP);
			if (group == null || !(Annotations.value(group, "name") instanceof String name)) continue;
			members.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
			if (Annotations.value(group, "min") instanceof Integer min) minimums.merge(name, min, Math::max);
		}
		if (minimums.isEmpty()) return;
		Set<String> missing = new HashSet<>();
		for (PreflightFinding f : ctx.out().subList(from, ctx.out().size())) {
			if (f.severity() == Severity.WARN && f.handler() != null) missing.add(f.handler());
		}
		minimums.forEach((name, min) -> {
			List<MethodNode> group = members.get(name);
			long applying = group.stream().filter(m -> !missing.contains(m.name + m.desc)).count();
			if (applying >= min) return;
			for (MethodNode m : group) {
				ctx.injector(m, 1, "@Group " + name + " needs " + min + " of its " + group.size() + " injectors to apply, and "
								+ (applying == 0 ? "none" : "only " + applying) + " can",
						ctx.changer(true) + " changed the code its injectors hook; the mod needs an update or this mixin must be skipped.");
			}
		});
	}

	private void checkMembersOf(ClassNode mixin, ClassNode target, Context ctx) {
		String targetDotted = target.name.replace('/', '.');
		for (FieldNode field : mixin.fields) {
			AnnotationNode shadow = Annotations.find(field, SHADOW);
			if (shadow == null) continue;
			String name = ctx.resolve(field.name);
			MemberRef ref = MemberRef.parse(name);
			String fieldName = ref.nameOr(field.name);
			String desc = ref.fieldDescOr(field.desc);
			// Mixin looks for a shadowed field in the target class itself, not its supertypes.
			if (target.fields.stream().noneMatch(f -> f.name.equals(fieldName) && f.desc.equals(desc)) && !aliasExists(shadow, target, desc, true)) {
				InstalledClasses.AddedMember added = addedByInstalledMixin(target.name, fieldName, desc, true);
				if (added != null) {
					ctx.info("@Shadow field " + fieldName + " is not in " + targetDotted + "; " + addedBy(added) + " adds it, so it was not verified");
				} else if (addedByAMixin(fieldName)) {
					ctx.info("@Shadow field " + fieldName + " is not in " + targetDotted + "; another mod's mixin may add it, so it was not verified");
				} else {
					ctx.classLevel("@Shadow field " + fieldName + " (" + desc + ") does not exist in " + targetDotted,
							ctx.changer(true) + " changed " + targetDotted + "; the mod needs an update.");
				}
			}
		}

		for (MethodNode method : mixin.methods) {
			AnnotationNode shadow = Annotations.find(method, SHADOW);
			if (shadow != null) {
				String prefix = Annotations.value(shadow, "prefix") instanceof String p ? p : "shadow$";
				String name = method.name.startsWith(prefix) ? method.name.substring(prefix.length()) : method.name;
				MemberRef ref = MemberRef.parse(ctx.resolve(name));
				String methodName = ref.nameOr(name);
				String desc = ref.methodDescOr(method.desc);
				if (game.resolveMethod(target.name, methodName + desc) == ClassIndex.Resolution.MISSING
						&& !aliasExists(shadow, target, desc, false)) {
					InstalledClasses.AddedMember added = addedByInstalledMixin(target.name, methodName, desc, false);
					if (added != null) {
						ctx.info("@Shadow method " + methodName + " is not in " + targetDotted + "; " + addedBy(added) + " adds it, so it was not verified");
					} else if (addedByAMixin(methodName)) {
						ctx.info("@Shadow method " + methodName + " is not in " + targetDotted + "; another mod's mixin may add it, so it was not verified");
					} else {
						ctx.classLevel("@Shadow method " + methodName + desc + " does not exist in " + targetDotted,
								ctx.changer(true) + " changed " + targetDotted + "; the mod needs an update.");
					}
				}
				continue;
			}

			if (Annotations.find(method, OVERWRITE) != null) {
				MemberRef ref = MemberRef.parse(ctx.resolve(method.name));
				String name = ref.nameOr(method.name);
				String desc = ref.methodDescOr(method.desc);
				if (!declares(target, name, desc)) {
					ctx.classLevel("@Overwrite target " + name + desc + " does not exist in " + targetDotted,
							"The overwritten method was changed by " + ctx.changer(false) + ".");
				}
				continue;
			}

			AnnotationNode accessor = Annotations.find(method, ACCESSOR);
			AnnotationNode invoker = accessor == null ? Annotations.find(method, INVOKER) : null;
			if (accessor != null || invoker != null) {
				// Mixin generates an instance accessor method in the target; one that already exists there (for
				// example one NeoForge added) makes Mixin throw when the target class loads. A static accessor or
				// invoker adds nothing to the target (Mixin rewrites the interface's own method), so a same-named
				// method there is harmless: typically the very method the invoker calls.
				if ((method.access & Opcodes.ACC_STATIC) == 0 && declares(target, method.name, method.desc)) {
					ctx.classLevel((accessor != null ? "@Accessor " : "@Invoker ") + method.name + method.desc + " already exists in "
									+ targetDotted + " (added by " + ctx.changer(false) + "), so Mixin cannot generate it",
							"The mod needs an update; until then it cannot load.");
				} else if (accessor != null) {
					checkAccessor(method, accessor, target, ctx);
				} else {
					checkInvoker(method, invoker, target, ctx);
				}
				continue;
			}

			boolean itf = (mixin.access & Opcodes.ACC_INTERFACE) != 0;
			boolean injector = false;
			for (AnnotationNode ann : Annotations.all(method)) {
				if (isInjector(ann.desc)) {
					injector = true;
					if (itf && !interfaceInjectors) {
						ctx.injector(method, 1, "@" + simpleName(ann.desc) + " handler " + method.name + " is an injector in an interface mixin, which "
										+ loader + "'s Mixin doesn't apply (Mixin does from 0.8.6), so it would drop the whole mixin",
								"The feature this hook serves doesn't work on this " + loader + ".");
						break;
					}
					checkInjector(mixin, method, ann, target, ctx);
				}
			}
			if (!injector && itf && !interfaceInjectors && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC)) == 0) {
				ctx.classLevel("Method " + method.name + method.desc + " of an interface mixin is not public, which " + loader
						+ "'s Mixin doesn't take (Mixin does from 0.8.6), so it drops the whole mixin", "The mod needs a build for this " + loader + ".");
			} else if (!injector) {
				checkMerged(method, target, ctx);
			}
		}
	}

	/**
	 * A plain mixin method is merged into the target. If the target already declares it, Mixin replaces the
	 * target method only when overwrites are allowed without {@code @Overwrite}; with
	 * {@code overwrites.requireAnnotations} it throws instead. Typical cause: NeoForge added the method
	 * (for example {@code hashCode}) to a class that has none in vanilla.
	 */
	private static void checkMerged(MethodNode method, ClassNode target, Context ctx) {
		if ((method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT)) != 0 || method.name.startsWith("<")) return;
		if (Annotations.find(method, UNIQUE) != null
				|| Annotations.find(method, INTRINSIC) != null) return;
		if (!declares(target, method.name, method.desc)) return;
		String where = method.name + method.desc + " already exists in " + target.name.replace('/', '.');
		if (ctx.config().overwritesRequireAnnotations()) {
			ctx.classLevel(where + " and the config requires @Overwrite to replace it, so Mixin rejects this mixin",
					ctx.changer(true) + " added this method; the mod needs an update.");
		} else {
			ctx.out().add(new PreflightFinding(Severity.WARN, ctx.configName(), ctx.mixinDotted(),
					where + "; the mixin silently replaces it, which may change " + ctx.platform() + "'s behaviour", ""));
		}
	}

	/** Prefixes of the members javac generates, which a target's own bytecode has: never a mixin's. */
	private static final List<String> JAVAC_PREFIXES = List.of("lambda$", "access$", "this$", "val$");

	/**
	 * Whether a member of this name, missing from the target's bytecode, may be one another mod's mixin adds: mixins name
	 * the members they add {@code modid$name} by convention, a form the game's own members don't take (bar javac's
	 * {@link #JAVAC_PREFIXES}). Pre-flight sees only the target's bytecode, so it doesn't judge those.
	 */
	static boolean addedByAMixin(String member) {
		return member.indexOf('$') > 0 && JAVAC_PREFIXES.stream().noneMatch(member::startsWith);
	}

	/**
	 * The mixin of an installed jar that adds this member to the target ({@link InstalledClasses#addedMember}), or null;
	 * the result then depends on that mixin, so it is recorded with its fingerprint.
	 */
	private InstalledClasses.AddedMember addedByInstalledMixin(String target, String name, String desc, boolean field) {
		InstalledClasses.AddedMember added = installed.addedMember(target, name, desc, field);
		if (added != null && !installedRead.containsKey(added.mixin())) {
			String fingerprint;
			try {
				fingerprint = installed.fingerprint(added.mixin());
			} catch (IOException e) {
				fingerprint = "";
			}
			installedRead.put(added.mixin(), fingerprint);
		}
		return added;
	}

	private static String addedBy(InstalledClasses.AddedMember added) {
		return added.owner() + "'s mixin " + added.mixin().replace('/', '.');
	}

	private boolean aliasExists(AnnotationNode shadow, ClassNode target, String desc, boolean field) {
		if (!(Annotations.value(shadow, "aliases") instanceof List<?> aliases)) return false;
		for (Object a : aliases) {
			if (!(a instanceof String alias)) continue;
			ClassIndex.Resolution r = field
					? game.resolveField(target.name, alias + ":" + desc)
					: game.resolveMethod(target.name, alias + desc);
			if (r != ClassIndex.Resolution.MISSING) return true;
		}
		return false;
	}

	private static void checkAccessor(MethodNode method, AnnotationNode ann, ClassNode target, Context ctx) {
		String explicit = Annotations.value(ann, "value") instanceof String s && !s.isEmpty() ? s : null;
		String name = explicit != null ? explicit : inferAccessorName(method.name);
		if (name == null) return;
		MemberRef ref = MemberRef.parse(ctx.resolve(name));
		String fieldName = ref.nameOr(name);
		String desc = ref.fieldDescOr(accessorFieldDesc(method));
		// As Mixin's AccessorInfo: the field type must match exactly; the name must match, or match ignoring case
		// with no other such field. Without a known type, fall back to the name alone.
		int caseInsensitive = 0;
		for (FieldNode f : target.fields) {
			if (desc != null && !f.desc.equals(desc)) continue;
			if (f.name.equals(fieldName)) return;
			if (f.name.equalsIgnoreCase(fieldName)) caseInsensitive++;
		}
		if (caseInsensitive == 1) return;
		boolean nameExists = target.fields.stream().anyMatch(f -> f.name.equals(fieldName));
		ctx.accessorMember(method, nameExists
						? "@Accessor target field " + fieldName + " in " + target.name.replace('/', '.') + " has a different type than " + desc
						: "@Accessor target field " + fieldName + " does not exist in " + target.name.replace('/', '.'),
				nameExists ? ctx.changer(true) + " changed the field's type." : "The field was renamed or removed by " + ctx.changer(false) + ".");
	}

	/** The field type an accessor reads or writes: a getter's return type or a setter's only parameter. */
	private static String accessorFieldDesc(MethodNode method) {
		Type[] args = Type.getArgumentTypes(method.desc);
		Type ret = Type.getReturnType(method.desc);
		if (args.length == 0 && ret.getSort() != Type.VOID) return ret.getDescriptor();
		if (args.length == 1 && ret.getSort() == Type.VOID) return args[0].getDescriptor();
		return null;
	}

	private static void checkInvoker(MethodNode method, AnnotationNode ann, ClassNode target, Context ctx) {
		String explicit = Annotations.value(ann, "value") instanceof String s && !s.isEmpty() ? s : null;
		String name = explicit != null ? explicit : inferInvokerName(method.name);
		if (name == null || name.equals("<init>")) return;
		MemberRef ref = MemberRef.parse(ctx.resolve(name));
		String methodName = ref.nameOr(name);
		String desc = ref.methodDescOr(method.desc);
		if (declares(target, methodName, desc)) return;
		ctx.accessorMember(method, "@Invoker target method " + methodName + desc + " does not exist in " + target.name.replace('/', '.'),
				"The method was renamed or removed by " + ctx.changer(false) + ".");
	}

	/** The field an {@code @Accessor} without a value names, as Mixin infers it from the method's name; null when none. */
	public static String inferAccessorName(String method) {
		return nameAfterPrefix(method, "get", "is", "set");
	}

	static String inferInvokerName(String method) {
		String name = nameAfterPrefix(method, "call", "invoke");
		if (name != null) return name;
		if (method.startsWith("new") || method.startsWith("create")) return "<init>";
		return null;
	}

	/**
	 * {@code getFooBar} -> {@code fooBar} for the first matching prefix followed by an upper-case letter, as Mixin's
	 * {@code AccessorInfo.AccessorName} inflects it: a name all in upper case keeps its case ({@code getURL} ->
	 * {@code URL}, {@code setX_SIDE_LENGTH} -> {@code X_SIDE_LENGTH}), any other has its first letter lowered
	 * ({@code getURLFoo} -> {@code uRLFoo}).
	 */
	private static String nameAfterPrefix(String method, String... prefixes) {
		for (String prefix : prefixes) {
			if (method.length() > prefix.length() && method.startsWith(prefix)) {
				char first = method.charAt(prefix.length());
				if (first < 'A' || first > 'Z') continue;
				String name = method.substring(prefix.length());
				return name.toUpperCase(Locale.ROOT).equals(name) ? name : Character.toLowerCase(first) + name.substring(1);
			}
		}
		return null;
	}

	/** Whether {@code handler} is in a named injector group ({@code @Group}), whose members Mixin checks together. */
	private static boolean grouped(MethodNode handler) {
		return Annotations.find(handler, GROUP) != null;
	}

	private static boolean isInjector(String desc) {
		return desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !desc.equals(AT) && !desc.equals(GROUP)
				&& !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Slice") && !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Desc")
				&& !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Constant") && !desc.startsWith("Lorg/spongepowered/asm/mixin/injection/Coerce")
				|| desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
	}

	private void checkInjector(ClassNode mixin, MethodNode handler, AnnotationNode ann, ClassNode target, Context ctx) {
		if (!(Annotations.value(ann, "method") instanceof List<?> selectors) || selectors.isEmpty()) return;
		// As Mixin: a grouped injector requires nothing by default, its group's minimum being the check.
		int require = Annotations.value(ann, "require") instanceof Integer r && r >= 0 ? r : grouped(handler) ? 0 : ctx.config().defaultRequire();
		List<AnnotationNode> slices = Annotations.list(Annotations.value(ann, "slice"));
		String injectorName = "@" + simpleName(ann.desc);

		List<MethodNode> matched = new ArrayList<>();
		boolean anyFuzzy = false;
		String lastSelector = null;
		String lastName = null;
		String lastDesc = null;
		for (Object o : selectors) {
			if (!(o instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) {
				anyFuzzy = true;
				continue;
			}
			lastSelector = ref.name() + (ref.desc() != null ? ref.desc() : "");
			lastName = ref.name();
			lastDesc = ref.desc();
			if (addsMethod(mixin, ref)) {
				anyFuzzy = true; // a method the mixin itself merges into the target: not judged
				continue;
			}
			for (MethodNode m : target.methods) {
				if (!ref.matchesMethod(m.name, m.desc)) continue;
				// Mixin keys an injector's targets by method (InjectionInfo.prepare), so a method that several selectors
				// name is injected once: LambDynamicLights names a hook by its Intermediary and its Mojang name, which
				// select the same method once remapped.
				if (!matched.contains(m)) matched.add(m);
				// As Mixin: a selector without a descriptor (or quantifier) picks the first method of that name only.
				if (ref.desc() == null) break;
			}
		}
		if (matched.isEmpty()) {
			InstalledClasses.AddedMember added = !anyFuzzy && lastSelector != null ? addedByInstalledMixin(target.name, lastName, lastDesc, false) : null;
			if (added != null) {
				ctx.info(injectorName + " " + handler.name + " targets method " + lastSelector + ", which is not in " + target.name.replace('/', '.')
						+ "; " + addedBy(added) + " adds it, so it was not verified");
			} else if (!anyFuzzy && lastSelector != null && addedByAMixin(lastName)) {
				ctx.info(injectorName + " " + handler.name + " targets method " + lastSelector + ", which is not in " + target.name.replace('/', '.')
						+ "; another mod's mixin may add it, so it was not verified");
			} else if (!anyFuzzy && lastSelector != null) {
				ctx.injector(handler, require, injectorName + " targets method " + lastSelector
								+ ", which does not exist in " + target.name.replace('/', '.'),
						"The method was renamed or removed by " + ctx.changer(false) + ".");
			}
			return;
		}
		if (ann.desc.equals(INJECT) && !anyFuzzy && !acceptsCallbackSignature(handler, ann, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters (often a lambda whose captured values are ordered differently)"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(WRAP_METHOD) && matched.stream().allMatch(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0)) {
			// MixinExtras throws while preparing the injector, which require doesn't cover (a config that isn't required survives it).
			ctx.injector(handler, ctx.config().required() ? 1 : 0, injectorName + " targets " + matched.get(0).name + matched.get(0).desc
							+ ", which is abstract in " + target.name.replace('/', '.'),
					"Mixin picks the first method of that name; the mod needs an update or this mixin must be skipped.");
			return;
		}
		if (ann.desc.equals(WRAP_METHOD) && !anyFuzzy && !acceptsWrapperSignature(handler, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MODIFY_VARIABLE) && !anyFuzzy && !acceptsModifierSignature(handler, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MODIFY_CONSTANT)) {
			for (AnnotationNode constant : Annotations.list(Annotations.value(ann, "constant"))) {
				String expected = InjectionPoints.constantDescription(constant);
				if (expected == null) continue;
				if (matched.stream().allMatch(m -> InjectionPoints.constantVerdict(constant, m) == Verdict.MISSING)) {
					ctx.injector(handler, require, injectorName + " in " + matched.get(0).name + " expects the constant " + expected
									+ ", which does not occur in that method",
							ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
					return;
				}
			}
		}

		String missingLocal = missingLocal(handler, matched);
		if (missingLocal != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable " + missingLocal + ", which "
							+ matched.get(0).name + " in " + target.name.replace('/', '.') + " does not declare",
					ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		String outOfScope = slices.isEmpty() ? namedLocalOutOfScope(handler, ann, matched, ctx) : null;
		if (outOfScope != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable " + outOfScope + ", which "
							+ matched.get(0).name + " in " + target.name.replace('/', '.') + " doesn't have in scope where the injector applies",
					ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		String ambiguous = slices.isEmpty() ? ambiguousLocal(handler, ann, matched, ctx) : null;
		if (ambiguous != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable " + ambiguous
							+ " by its type, which " + matched.get(0).name + " in " + target.name.replace('/', '.') + " has more than one of there",
					ctx.changer(true) + " added a local of that type; the mod needs an update or this mixin must be skipped.");
			return;
		}

		String argument = argumentNameRejected(ann);
		if (argument != null) {
			ctx.injector(handler, require, injectorName + " " + handler.name + " takes the argument " + argument + " of " + matched.get(0).name + " in "
							+ target.name.replace('/', '.') + " by its name, which " + loader + "'s Mixin doesn't know (it names arguments arg and their slot;"
							+ " Fabric's Mixin takes their names from 0.17.0)",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}
		String constructorPoint = constructorPointRejected(ann, matched);
		if (constructorPoint != null) {
			ctx.injector(handler, 1, injectorName + " " + handler.name + " injects into the constructor of " + target.name.replace('/', '.') + " at @At(\""
							+ constructorPoint + "\"), which " + loader + "'s Mixin takes only at a return"
							+ (constructorInjects == ConstructorInjects.UNSAFE_POINTS ? " or a point marked unsafe" : "") + " (Fabric's Mixin takes any point after super())",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}
		String beforeDelegate = beforeDelegateRejected(handler, ann, target, matched, ctx);
		if (beforeDelegate != null) {
			ctx.injector(handler, 1, injectorName + " " + handler.name + " takes a point in the constructor of " + target.name.replace('/', '.') + " at @At(\""
							+ beforeDelegate + "\") before its this() or super() call, where Mixin takes only a static handler: " + ctx.changer(false)
							+ " moved the point there",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}

		// Mixin counts an injector's injections in all its target methods against require: a method that lost its point
		// can leave too few, though the point is found in another (FrozenLib's, where NeoForge moved a call into a lambda,
		// REQ-1). Counted only where that can happen and every target is known.
		int injections = require > 1 && !anyFuzzy ? 0 : -1;
		List<String> without = new ArrayList<>();
		// Mixin also caps them at allow (at least require, and one): more is the same critical error as too few, whatever
		// the config's required (Fabric API's furnace hook on Forge 47, whose patch makes the call it hooks twice, ALLOW-1).
		int allow = Annotations.value(ann, "allow") instanceof Integer a ? Math.max(Math.max(require, 1), a) : Integer.MAX_VALUE;
		int allowed = allow < Integer.MAX_VALUE && !anyFuzzy ? 0 : -1;
		Set<String> with = new TreeSet<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(ann, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			Verdict verdict = Verdict.UNKNOWN;
			for (MethodNode m : matched) {
				Range range = slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx);
				boolean expression = point.equals("MIXINEXTRAS:EXPRESSION");
				String resolved = rawTarget == null ? null : ctx.resolve(rawTarget);
				Verdict v = expression ? InjectionPoints.evaluateExpression(handler, m, ctx) : InjectionPoints.evaluate(point, resolved, ordinal, opcode, range, m);
				if (v != Verdict.MISSING && ann.desc.equals(MODIFY_VARIABLE) && (point.equals("LOAD") || point.equals("STORE")) && range != Range.UNEVALUATED) {
					int slot = modifiedSlot(handler, ann, m);
					if (slot >= 0) v = InjectionPoints.accessesLocal(point.equals("LOAD"), Type.getArgumentTypes(handler.desc)[0], slot, ordinal, range, m) ? Verdict.FOUND : Verdict.MISSING;
				}
				if (injections >= 0) {
					int n = expression ? -1 : InjectionPoints.count(point, resolved, ordinal, opcode, range, m);
					if (n == 0) without.add(m.name);
					injections = n < 0 ? -1 : injections + n;
				}
				if (allowed >= 0) {
					int n = expression ? -1 : InjectionPoints.count(point, resolved, ordinal, opcode, range, m);
					if (n > 0) with.add(m.name);
					allowed = n < 0 ? -1 : allowed + n;
				}
				if (v == Verdict.FOUND) verdict = Verdict.FOUND;
				else if (v == Verdict.MISSING && verdict == Verdict.UNKNOWN) verdict = Verdict.MISSING;
			}
			if (verdict == Verdict.MISSING) {
				ctx.injector(handler, require, injectorName + " in " + matched.get(0).name + " expects @At(\"" + point + "\""
								+ (rawTarget != null ? ", target=\"" + ctx.resolve(rawTarget) + "\"" : "")
								+ (ordinal >= 0 ? ", ordinal=" + ordinal : "") + "), which does not occur in that method",
						ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
				return;
			}
			if (verdict == Verdict.FOUND && ann.desc.equals(WRAP_OPERATION) && rawTarget != null) {
				String mismatch = InjectionPoints.wrappedArityMismatch(handler, point, ctx.resolve(rawTarget), matched);
				if (mismatch != null) {
					ctx.classLevel(injectorName + " handler " + handler.name + " " + mismatch + " in " + matched.get(0).name,
							(ctx.owner() == null ? loader + "'s build of Minecraft" : ctx.changer(true)) + " calls this with different arguments; the mod needs an update or this handler must be skipped.");
					return;
				}
			}
		}
		if (allowed > allow) {
			ctx.injector(handler, 1, injectorName + " " + handler.name + " applies " + allowed + " time(s) where it allows " + allow
							+ ": its point occurs that often in " + String.join(", ", with) + " in " + target.name.replace('/', '.'),
					ctx.changer(true) + " changed " + (with.size() == 1 ? "that method" : "those methods") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}
		if (injections >= 0 && injections < require) {
			ctx.injector(handler, require, injectorName + " " + handler.name + " applies " + injections + " time(s) where it requires " + require
							+ ": its point does not occur in " + String.join(", ", new TreeSet<>(without)) + " in " + target.name.replace('/', '.'),
					ctx.changer(true) + " changed " + (without.size() == 1 ? "that method" : "those methods") + "; the mod needs an update or this mixin must be skipped.");
		}
	}

	/**
	 * The slot of the local a {@code @ModifyVariable} modifies in {@code method}, where its annotation alone tells it: its
	 * {@code index}, or with {@code argsOnly} the argument of the handler's type it names by its ordinal (the only one of
	 * that type without one), as Mixin's discriminator takes them; -1 otherwise (a name, or a local Mixin finds by the
	 * local variable table). A {@code LOAD} or {@code STORE} point then picks that slot's loads or stores only (PRE-16:
	 * Fabric API's shader import hook modifies an import's name where vanilla stores it back into its argument, a store
	 * Forge 47's patch removed, and pre-flight took any store in the method for it).
	 */
	private static int modifiedSlot(MethodNode handler, AnnotationNode injector, MethodNode method) {
		Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
		if (handlerArgs.length == 0 || Annotations.value(injector, "name") != null) return -1;
		if (Annotations.value(injector, "index") instanceof Integer index && index >= 0) return index;
		if (!Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))) return -1;
		int ordinal = Annotations.value(injector, "ordinal") instanceof Integer o ? o : -1;
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		List<Integer> matching = new ArrayList<>();
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (arg.equals(handlerArgs[0])) matching.add(slot);
			slot += arg.getSize();
		}
		if (ordinal >= 0) return ordinal < matching.size() ? matching.get(ordinal) : -1;
		return matching.size() == 1 ? matching.get(0) : -1;
	}

	/**
	 * Mixin accepts an {@code @Inject} handler whose parameters, after MixinExtras sugar parameters are removed,
	 * are either just the callback info or the target's parameters followed by the callback info; anything else
	 * is an {@code InvalidInjectionException} when the mixin applies. Handlers that capture locals or use
	 * {@code @Coerce} are not judged.
	 */
	private static boolean acceptsCallbackSignature(MethodNode handler, AnnotationNode inject, List<MethodNode> targets) {
		if (Annotations.value(inject, "locals") != null) return true;
		List<Type> args = handlerArgs(handler);
		if (args == null) return true;
		if (args.isEmpty() || !isCallbackInfo(args.get(args.size() - 1))) return true; // unusual shape: leave it to Mixin
		if (args.size() == 1) return true;
		List<Type> wanted = args.subList(0, args.size() - 1);
		for (MethodNode target : targets) {
			if (List.of(Type.getArgumentTypes(target.desc)).equals(wanted)) return true;
		}
		return false;
	}

	/**
	 * Mixin accepts a {@code @ModifyVariable} handler taking the variable and then the target's first parameters, as
	 * many as it declares (Mixin's {@code Injector.validateParams}: Balm's takes two of three); anything else is an
	 * {@code InvalidInjectionException} when the mixin applies, whatever its {@code require} (Farmer's Delight's hook
	 * into {@code CropBlock.getGrowthSpeed}, whose first parameter NeoForge's build made a block state). Handlers that
	 * use {@code @Coerce} are left to Mixin.
	 */
	private static boolean acceptsModifierSignature(MethodNode handler, List<MethodNode> targets) {
		List<Type> args = handlerArgs(handler);
		if (args == null || args.size() <= 1) return true;
		List<Type> captured = args.subList(1, args.size());
		return targets.stream().allMatch(target -> {
			List<Type> params = List.of(Type.getArgumentTypes(target.desc));
			return captured.size() <= params.size() && params.subList(0, captured.size()).equals(captured);
		});
	}

	/**
	 * MixinExtras accepts a {@code @WrapMethod} handler whose parameters, after sugar parameters are removed, are the
	 * target's parameters followed by the {@code Operation}, in each method it wraps; anything else is an
	 * {@code InvalidInjectionException} when the mixin applies, whatever its {@code require}. Handlers that use
	 * {@code @Coerce}, or whose shape is unusual, are left to Mixin.
	 */
	private static boolean acceptsWrapperSignature(MethodNode handler, List<MethodNode> targets) {
		List<Type> args = handlerArgs(handler);
		if (args == null || args.isEmpty() || !args.get(args.size() - 1).getDescriptor().equals(OPERATION)) return true;
		List<Type> wanted = args.subList(0, args.size() - 1);
		return targets.stream().allMatch(target -> List.of(Type.getArgumentTypes(target.desc)).equals(wanted));
	}

	/**
	 * The first name a handler's {@code @Local(name = ...)} parameter asks for that no target method declares, or null.
	 * Only targets that carry local variable names are judged.
	 */
	private static String missingLocal(MethodNode handler, List<MethodNode> targets) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int i = 0; i < params.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(LOCAL)) continue;
				if (Annotations.value(a, "name") instanceof List<?> names && !names.isEmpty()) {
					if (!judgedMissing(targets, local -> names.contains(local.name))) continue;
					return String.valueOf(names.get(0));
				}
				// By type alone: the method must have a local of that type somewhere (or a parameter, with argsOnly).
				Type type = params[i];
				if (Annotations.value(a, "index") != null || type.getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
				if (Boolean.TRUE.equals(Annotations.value(a, "argsOnly"))) {
					if (targets.stream().noneMatch(m -> List.of(Type.getArgumentTypes(m.desc)).contains(type))) {
						return "of type " + type.getClassName() + " among its parameters";
					}
				} else if (judgedMissing(targets, local -> local.desc.equals(type.getDescriptor()))) {
					return "of type " + type.getClassName();
				}
			}
		}
		return null;
	}

	/**
	 * The type of a local an {@code @Local} captures by its type alone (no name, index or ordinal) when, at every
	 * instruction the injector's {@code @At} selects in every target, more than one local of that type is in scope:
	 * MixinExtras then can't tell which one is meant. Null when that can't be shown (no local variable table, an
	 * injection point other than a call, field access or object creation, ...).
	 */
	private static String ambiguousLocal(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, Context ctx) {
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.size() != 1) return null;
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!point.equals("INVOKE") && !point.equals("FIELD") && !point.equals("NEW")
				|| !(Annotations.value(at, "target") instanceof String raw)) return null;
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy()) return null;
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int p = 0; p < params.length; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (!local.desc.equals(LOCAL) || Annotations.value(local, "name") != null || Annotations.value(local, "index") != null
						|| Annotations.value(local, "ordinal") != null || Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))
						|| params[p].getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
				String type = params[p].getDescriptor();
				boolean judged = false;
				boolean single = false;
				for (MethodNode m : targets) {
					if (m.localVariables == null || m.localVariables.isEmpty()) return null;
					int seen = 0;
					for (int i = 0; i < m.instructions.size(); i++) {
						AbstractInsnNode insn = m.instructions.get(i);
						if (opcode >= 0 && insn.getOpcode() != opcode || !InjectionPoints.matchesInsn(point, ref, insn)) continue;
						if (ordinal >= 0 && seen++ != ordinal) continue;
						judged = true;
						int inScope = 0;
						for (LocalVariableNode lv : m.localVariables) {
							if (lv.desc.equals(type) && m.instructions.indexOf(lv.start) <= i && i < m.instructions.indexOf(lv.end)) inScope++;
						}
						single |= inScope <= 1;
					}
				}
				if (judged && !single) return "of type " + params[p].getClassName();
			}
		}
		return null;
	}

	/** Whether the targets' local variable tables (where they have one) show no local {@code wanted} accepts. */
	private static boolean judgedMissing(List<MethodNode> targets, Predicate<LocalVariableNode> wanted) {
		boolean judged = false;
		for (MethodNode m : targets) {
			if (m.localVariables == null || m.localVariables.isEmpty()) continue;
			judged = true;
			for (LocalVariableNode local : m.localVariables) {
				if (wanted.test(local)) return false;
			}
		}
		return judged;
	}

	/** The last element of a non-empty list. */
	private static <T> T last(List<T> list) {
		return list.get(list.size() - 1);
	}

	/** The handler's parameter types without MixinExtras sugar parameters; null if a parameter uses {@code @Coerce}. */
	private static List<Type> handlerArgs(MethodNode handler) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> args = new ArrayList<>();
		for (int i = 0; i < types.length; i++) {
			boolean sugar = false;
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (a.desc.equals(COERCE)) return null;
				if (a.desc.startsWith("Lcom/llamalad7/mixinextras/sugar/")) sugar = true;
			}
			if (!sugar) args.add(types[i]);
		}
		return args;
	}

	private static boolean isCallbackInfo(Type type) {
		return type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;")
				|| type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;");
	}

	/** Whether the mixin declares a method {@code ref} selects that Mixin merges into the target (not a shadow or handler). */
	private static boolean addsMethod(ClassNode mixin, MemberRef ref) {
		for (MethodNode m : mixin.methods) {
			if (!ref.matchesMethod(m.name, m.desc) || m.name.startsWith("<") || Annotations.find(m, SHADOW) != null) continue;
			if (Annotations.all(m).stream().noneMatch(a -> isInjector(a.desc))) return true;
		}
		return false;
	}

	private static boolean declares(ClassNode target, String name, String desc) {
		return declared(target, name, desc) != null;
	}

	private static MethodNode declared(ClassNode target, String name, String desc) {
		for (MethodNode m : target.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) return m;
		}
		return null;
	}

	private static String simpleName(String desc) {
		String internal = desc.substring(1, desc.length() - 1);
		return internal.substring(internal.lastIndexOf('/') + 1);
	}
}
