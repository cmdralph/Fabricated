package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.bytecode.VanillaSeams;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
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
 * mod's own classes read those constants. So does any enum mixin where the running Mixin applies none, as before 0.17.1
 * (ENUM-7): translation turns those it can into ordinary mixins first.
 */
public final class MixinPreflight extends PreflightPart {
	private final MixinRepairs repairer;
	private final ReplacedMethods replacedMethods;

	/** Where the running Mixin takes an {@code @Inject} into a constructor (CTOR-2). */
	public enum ConstructorInjects {
		/** Anywhere after the call to the delegate constructor, as Fabric's Mixin does; not judged. */
		AFTER_DELEGATE,
		/** At a return, or at a point marked {@code unsafe} (SpongePowered's 0.8.6 and 0.8.7). */
		UNSAFE_POINTS,
		/** At a return only (SpongePowered's 0.8.5, whose points take no {@code unsafe}). */
		RETURNS_ONLY
	}

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
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, false);
	}

	/**
	 * @param exactStaticInjects whether the running Mixin refuses an {@code @Inject} whose handler is static where its
	 *                           target isn't, as SpongePowered's 0.8.5 does (LAMBDA-6); every Mixin refuses one that isn't
	 *                           static where its target is
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, true);
	}

	/**
	 * @param enumMixins whether the running Mixin applies enum mixins, as Fabric's does from 0.17.1 (ENUM-7); where it
	 *                   doesn't, an enum mixin that translation couldn't turn into an ordinary one fails
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, enumMixins, false);
	}

	/**
	 * @param readsLocalTypes whether the running Mixin stops on a local variable of a mixin's method whose class it can't
	 *                        find, as SpongePowered's and Fabric's before 0.17.0 do (LVT-1): repairs then leave such
	 *                        locals out of the methods' debug tables
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins,
			boolean readsLocalTypes) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, enumMixins, readsLocalTypes, true);
	}

	/**
	 * @param seesMixinFields whether the running Mixin sees the fields other mixins add to the same target as it
	 *                        prepares a mixin, as Fabric's does: it binds a {@code @Shadow} to one (CORPUS-74) and
	 *                        renames a {@code @Unique} field of the same name (CORPUS-95); where it doesn't, repairs
	 *                        declare such a shadow as the field the other mixin adds ({@link MixinRepairs#sharedFieldRepair}),
	 *                        and a shadow they can't fails, and rename such a unique field
	 *                        ({@link MixinRepairs#uniqueFieldRepair})
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins,
			boolean readsLocalTypes, boolean seesMixinFields) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, enumMixins, readsLocalTypes, seesMixinFields, false);
	}

	/**
	 * @param handsOffConstructorArguments whether the platform has the constructors a translated mixin's field names
	 *                                     hand their arguments to the one they call, once Mixin has applied the
	 *                                     class's mixins ({@link ConstructorHandoffs}, IRIS-1); repairs then point an
	 *                                     injector into a constructor the platform keeps only to call another one at
	 *                                     that one ({@link ConstructorRepairs#handoffRepairs})
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins,
			boolean readsLocalTypes, boolean seesMixinFields, boolean handsOffConstructorArguments) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, enumMixins, readsLocalTypes, seesMixinFields,
				handsOffConstructorArguments, false);
	}

	/**
	 * @param overlapsWideArguments whether the running Mixin may store a long or double over another local where it reuses
	 *                              an earlier injector's locals for an injector's values, as SpongePowered's releases do
	 *                              (PRE-22): a MixinExtras wrapper whose value would overwrite one of its mixin's
	 *                              shared locals so fails ({@link ArgumentLocals})
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins,
			boolean readsLocalTypes, boolean seesMixinFields, boolean handsOffConstructorArguments, boolean overlapsWideArguments) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, enumMixins, readsLocalTypes, seesMixinFields,
				handsOffConstructorArguments, overlapsWideArguments, false);
	}

	/**
	 * @param keepsLocalsPastScope whether the running Mixin keeps a local among those it finds at a point once the local's
	 *                             scope has ended, through the same frames it skips (PRE-26): a MixinExtras {@code @Local}
	 *                             taking a local by its type is then judged among the locals it keeps
	 *                             ({@link #keptLocalTypes}), and named by its ordinal among them where it can be
	 *                             ({@link LocalRepairs#keptLocalRepairs})
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins,
			boolean readsLocalTypes, boolean seesMixinFields, boolean handsOffConstructorArguments, boolean overlapsWideArguments,
			boolean keepsLocalsPastScope) {
		this(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader, interfaceInjectors,
				constructorInjects, argumentNames, exactStaticInjects, enumMixins, readsLocalTypes, seesMixinFields,
				handsOffConstructorArguments, overlapsWideArguments, keepsLocalsPastScope, false);
	}

	/**
	 * @param restrictsInitialisers whether the running Mixin refuses a mixin whose initialiser branches or loads or stores an
	 *                              array element, as SpongePowered's releases do (PRE-28): such a mixin then fails
	 *                              ({@link #checkInitialiser})
	 */
	public MixinPreflight(ClassIndex game, InstalledClasses installed, ClassBytesSource modClasses, ClassBytesSource untransformable,
			boolean clientSide, boolean missingTargetsFatal, ClassBytesSource vanilla, String loader, boolean interfaceInjectors,
			ConstructorInjects constructorInjects, boolean argumentNames, boolean exactStaticInjects, boolean enumMixins,
			boolean readsLocalTypes, boolean seesMixinFields, boolean handsOffConstructorArguments, boolean overlapsWideArguments,
			boolean keepsLocalsPastScope, boolean restrictsInitialisers) {
		super(new PreflightClasses(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader,
				interfaceInjectors, constructorInjects, argumentNames, exactStaticInjects, enumMixins, readsLocalTypes, seesMixinFields,
				handsOffConstructorArguments, overlapsWideArguments, keepsLocalsPastScope, restrictsInitialisers));
		this.repairer = new MixinRepairs(classes, this);
		this.replacedMethods = new ReplacedMethods(classes);
	}

	/**
	 * @param configName file name of the config (for messages)
	 * @param skip       mixin names (relative to the package) the user disabled
	 */
	public List<PreflightFinding> check(String configName, MixinConfigFile config, Refmap refmap, Set<String> skip) {
		List<PreflightFinding> out = new ArrayList<>();
		boolean hasPlugin = config.plugin() != null && !config.plugin().isBlank();
		Map<String, Optional<ClassNode>> modClassCache = new HashMap<>();
		for (String mixin : config.mixinsFor(clientSide)) {
			if (skip.contains(mixin)) continue;
			String internal = config.internalName(mixin);
			ClassNode node = modClasses.readNode(internal, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			boolean selfContained = node != null && selfContained(node);
			boolean addsFields = selfContained && addsFields(node);
			ClassNode code = node != null && (!selfContained || addsFields) ? modClasses.readNode(internal, ClassReader.SKIP_FRAMES) : null;
			Set<String> stateless = node == null || selfContained ? Set.of() : statelessHandlers(code);
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, hasPlugin, selfContained, addsFields,
					addsFields ? stateGroups(code) : Map.of(), stateless, out, null, loader);
			if (node == null) {
				ctx.classLevel("Mixin class " + ctx.mixinDotted() + " listed in " + configName + " is missing from the mod jar",
						"The mod jar is incomplete or corrupted; re-download it.");
				continue;
			}
			int from = out.size();
			if (restrictsInitialisers) checkInitialiser(code != null ? code : modClasses.readNode(internal, ClassReader.SKIP_FRAMES), ctx);
			checkMixin(node, ctx);
			ctx.leaveOutStateGroups(from);
			leaveOutSharers(out, from, configName, ctx.mixinDotted(), shares(node));
			ctx.leaveOutThreadLocalSharers(from, node, () -> threadLocalShares(code != null ? code : modClasses.readNode(internal, ClassReader.SKIP_FRAMES),
					modClassCache));
		}
		if (hasPlugin && out.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT)) {
			out.add(new PreflightFinding(Severity.INFO, configName, null, "This config uses a mixin plugin (" + config.plugin()
					+ ") that may disable mixins at runtime, so an injector that would crash the game is left out instead of disabling the mod.", ""));
		}
		return out;
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
	 * array of one ({@link MixinRepairs#singleInjectionPoints}).</li>
	 * </ul>
	 */
	public Map<String, byte[]> repairs(String configName, MixinConfigFile config, Refmap refmap, Set<String> skip,
			List<PreflightFinding> out) {
		return repairer.repairs(configName, config, refmap, skip, out);
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
	 * The game classes the mixins {@link #check} or {@link #repairs} read target that have vanilla methods put back
	 * ({@link VanillaSeams}): the game must have them put back as those classes load (SEAM-1).
	 */
	public Set<String> restoredSeams() {
		return Collections.unmodifiableSet(new TreeSet<>(restoredSeams));
	}

	/**
	 * The name a {@code @ModifyVariable} taking arguments only asks for, where the running Mixin doesn't know arguments by
	 * their names (ARGS-1) and no repair gave it the slot instead; null otherwise.
	 */
	private String argumentNameRejected(AnnotationNode injector) {
		if (argumentNames || !injector.desc.equals(MixinNames.MODIFY_VARIABLE) || !Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "index") != null || Annotations.value(injector, "ordinal") != null
				|| !(Annotations.value(injector, "name") instanceof List<?> names) || names.isEmpty()) return null;
		return String.valueOf(names.get(0));
	}

	/**
	 * The name the first MixinExtras {@code @Local(argsOnly = true)} of a handler asks for, where the running Mixin
	 * doesn't know arguments by their names (PRE-18, as ARGS-1) and no repair gave it the slot instead; null otherwise.
	 */
	private String localArgumentNameRejected(MethodNode handler) {
		if (argumentNames) return null;
		int params = Type.getArgumentTypes(handler.desc).length;
		for (int p = 0; p < params; p++) {
			for (AnnotationNode local : Annotations.ofParameter(handler, p)) {
				if (LocalRepairs.takesArgumentByName(local)) return String.valueOf(((List<?>) Annotations.value(local, "name")).get(0));
			}
		}
		return null;
	}

	/**
	 * The name the first {@code @Local(argsOnly = true)} a handler's MixinExtras expression defines asks for, where the
	 * running Mixin doesn't know arguments by their names and no repair gave it the slot instead (PRE-21, as PRE-18);
	 * null otherwise.
	 */
	private String definitionArgumentNameRejected(MethodNode handler) {
		if (argumentNames) return null;
		for (AnnotationNode local : InjectionPoints.definitionLocals(handler)) {
			if (LocalRepairs.takesArgumentByName(local)) return String.valueOf(((List<?>) Annotations.value(local, "name")).get(0));
		}
		return null;
	}

	/**
	 * The first point of an {@code @Inject} into a constructor that SpongePowered's Mixin rejects (CTOR-2: it throws as it
	 * applies the mixin, whatever the config's {@code required}): any but a return that isn't marked {@code unsafe}, or,
	 * on 0.8.5, any but a return that isn't Fabricated's {@link ConstructorPoint}. Mixin judges only the instructions a
	 * point finds, so a call, field or construction point that finds none in the constructors isn't one: the check of the
	 * points reports it as missing (CTOR-7: owo's hooks after Fabric Loader's and Quilt's client start hooks in
	 * {@code Minecraft}'s constructor, which neither Forge's nor NeoForge's constructor calls). Null when it takes them
	 * all, or on Fabric's Mixin.
	 */
	private String constructorPointRejected(AnnotationNode injector, List<MethodNode> matched, Context ctx) {
		if (constructorInjects == ConstructorInjects.AFTER_DELEGATE || !injector.desc.equals(MixinNames.INJECT)
				|| matched.stream().noneMatch(m -> m.name.equals("<init>"))) return null;
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (ConstructorRepairs.RETURN_POINTS.contains(point)) continue;
			if (constructorInjects == ConstructorInjects.UNSAFE_POINTS && Boolean.TRUE.equals(Annotations.value(at, "unsafe"))) continue;
			if (constructorInjects == ConstructorInjects.RETURNS_ONLY && point.equals(ConstructorPoint.AT)) continue;
			if (findsNothing(at, point, slices, matched, ctx)) continue;
			return point;
		}
		return null;
	}

	/** Whether a call, field or construction point finds no instruction in any of the constructors among {@code methods}. */
	private static boolean findsNothing(AnnotationNode at, String point, List<AnnotationNode> slices, List<MethodNode> methods, Context ctx) {
		if (!List.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW").contains(point) || !(Annotations.value(at, "target") instanceof String raw)) {
			return false;
		}
		AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
		for (MethodNode m : methods) {
			if (!m.name.equals("<init>")) continue;
			Range range = slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx);
			if (InjectionPoints.evaluate(point, ctx.resolve(raw), Annotations.value(at, "ordinal") instanceof Integer n ? n : -1,
					Annotations.value(at, "opcode") instanceof Integer n ? n : -1, range, m) != Verdict.MISSING) return false;
		}
		return true;
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
			if (Annotations.find(method, MixinNames.ACCESSOR) != null || Annotations.find(method, MixinNames.INVOKER) != null) return false;
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
			if ((field.access & Opcodes.ACC_SYNTHETIC) == 0 && Annotations.find(field, MixinNames.SHADOW) == null) return true;
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
			if (Annotations.find(field, MixinNames.SHADOW) == null) own.add(field.name + ":" + field.desc);
		}
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, MixinNames.SHADOW) == null) own.add(method.name + method.desc);
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

	static Set<String> independentHandlers(ClassNode mixin) {
		if (mixin == null) return Set.of();
		Set<String> ownFields = new HashSet<>();
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, MixinNames.SHADOW) == null) ownFields.add(field.name + ":" + field.desc);
		}
		Map<String, MethodNode> ownMethods = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, MixinNames.SHADOW) == null) ownMethods.put(method.name + method.desc, method);
		}
		Map<MethodNode, Set<String>> used = new LinkedHashMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.all(method).stream().anyMatch(a -> PreflightAsm.isInjector(a.desc))) {
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

	/**
	 * The locals each injector handler of a mixin shares with others through MixinExtras' {@code @Share} (SHARE-1), by
	 * handler (name and descriptor), each as its namespace and name; handlers sharing none are absent.
	 */
	static Map<String, Set<String>> shares(ClassNode mixin) {
		Map<String, Set<String>> shares = new TreeMap<>();
		if (mixin == null) return shares;
		for (MethodNode method : mixin.methods) {
			Set<String> keys = new TreeSet<>();
			for (int i = 0; i < Type.getArgumentTypes(method.desc).length; i++) {
				for (AnnotationNode ann : Annotations.ofParameter(method, i)) {
					if (ann.desc.equals(MixinNames.SHARE) && Annotations.value(ann, "value") instanceof String name) {
						keys.add((Annotations.value(ann, "namespace") instanceof String namespace ? namespace : "") + ":" + name);
					}
				}
			}
			if (!keys.isEmpty()) shares.put(method.name + method.desc, keys);
		}
		return shares;
	}

	/**
	 * The injectors sharing a local ({@code @Share}) with those of this mixin left out by the findings from {@code from}
	 * on, left out with them (SHARE-1), unless the whole mixin is: one left alone would read the shared value without the
	 * other's writing it, which Mixin's own skipping of an injector never does to a required one. Balm's crop growth hooks
	 * on Forge 61: the first captures the block state below into a shared local, which pre-flight left out where Forge's
	 * build has no such state (CORPUS-54), and the second, reading it, then failed its own injection check (Forge's build
	 * has no second float where it applies) and stopped the corpus client (extended run 41569829683).
	 */
	static void leaveOutSharers(List<PreflightFinding> out, int from, String configName, String mixinDotted, Map<String, Set<String>> shares) {
		if (shares.size() < 2) return;
		Set<String> leftOut = new LinkedHashSet<>();
		for (PreflightFinding f : out.subList(from, out.size())) {
			if (f.severity() != Severity.LEFT_OUT || !mixinDotted.equals(f.mixin())) continue;
			if (f.handler() == null) return;
			leftOut.add(f.handler());
		}
		ArrayDeque<String> next = new ArrayDeque<>(leftOut);
		while (!next.isEmpty()) {
			String with = next.pop();
			Set<String> keys = shares.get(with);
			if (keys == null) continue;
			shares.forEach((other, theirs) -> {
				if (leftOut.contains(other) || Collections.disjoint(keys, theirs)) return;
				leftOut.add(other);
				next.add(other);
				List<String> names = theirs.stream().filter(keys::contains).map(k -> k.substring(k.indexOf(':') + 1)).toList();
				out.add(new PreflightFinding(Severity.LEFT_OUT, configName, mixinDotted, "The injector of " + other + " goes with that of " + with
						+ ", which can't apply: they share the " + (names.size() == 1 ? "local " : "locals ") + String.join(", ", names)
						+ " (@Share), which it would read without the other's value", "", other));
			});
		}
	}

	/**
	 * The static {@code ThreadLocal} fields of the mod's other classes through which the injector handlers of a mixin hand
	 * state to each other (ITEM-2): by handler (name and descriptor), the fields (owner and name, dotted) it uses, itself,
	 * through the mixin's own methods or through the mod's static methods (two calls deep), that another handler uses too
	 * and one of them stores ({@code set}, {@code remove}, or the field replaced). Fabric API's {@code RecipeMixin} stores
	 * each stack's remainder, captured before one call of {@code Recipe.getRemainingItems}, in
	 * {@code RecipeRemainderHandler.REMAINDER_STACK}, which its redirects of the method's other calls read. Handlers that
	 * only read one, a context the mod's other code sets, hand each other nothing (Fabric API 0.116's
	 * {@code FluidRendererMixin}, whose hooks read the fluid render handler {@code FluidRenderingImpl} sets; Carpet's fill
	 * flag), nor do a static field of another type (a config's, a logger) or one only a handler uses.
	 */
	Map<String, Set<String>> threadLocalShares(ClassNode mixin, Map<String, Optional<ClassNode>> modClassCache) {
		Map<String, Set<String>> shares = new TreeMap<>();
		if (mixin == null) return shares;
		Map<String, MethodNode> own = new HashMap<>();
		for (MethodNode method : mixin.methods) own.put(method.name + method.desc, method);
		Map<String, Map<String, Boolean>> uses = new TreeMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.all(method).stream().noneMatch(a -> PreflightAsm.isInjector(a.desc))) continue;
			Map<String, Boolean> found = new TreeMap<>();
			threadLocalsUsed(mixin.name, own, method, 0, found, new HashSet<>(), modClassCache);
			if (!found.isEmpty()) uses.put(method.name + method.desc, found);
		}
		Map<String, Integer> users = new HashMap<>();
		Set<String> stored = new HashSet<>();
		uses.values().forEach(fields -> fields.forEach((f, written) -> {
			users.merge(f, 1, Integer::sum);
			if (written) stored.add(f);
		}));
		uses.forEach((handler, fields) -> {
			Set<String> shared = new TreeSet<>(fields.keySet());
			shared.removeIf(f -> users.get(f) < 2 || !stored.contains(f));
			if (!shared.isEmpty()) shares.put(handler, shared);
		});
		return shares;
	}

	/** {@link #threadLocalShares}' walk: each field the method uses, and whether it stores it. */
	private void threadLocalsUsed(String mixin, Map<String, MethodNode> own, MethodNode method, int depth, Map<String, Boolean> found,
			Set<MethodNode> seen, Map<String, Optional<ClassNode>> modClassCache) {
		if (!seen.add(method) || method.instructions == null) return;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode field && (field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC)
					&& !field.owner.equals(mixin) && threadLocal(Type.getType(field.desc), modClassCache) && modClass(field.owner, modClassCache) != null) {
				boolean stores = field.getOpcode() == Opcodes.PUTSTATIC || setsValue(method, field);
				found.merge(field.owner.replace('/', '.') + "." + field.name, stores, Boolean::logicalOr);
			} else if (insn instanceof MethodInsnNode call && call.owner.equals(mixin)) {
				MethodNode callee = own.get(call.name + call.desc);
				if (callee != null) threadLocalsUsed(mixin, own, callee, depth, found, seen, modClassCache);
			} else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && depth < 2) {
				ClassNode owner = modClass(call.owner, modClassCache);
				MethodNode callee = owner == null ? null : PreflightAsm.declared(owner, call.name, call.desc);
				if (callee != null) threadLocalsUsed(mixin, own, callee, depth + 1, found, seen, modClassCache);
			} else if (insn instanceof InvokeDynamicInsnNode indy) {
				for (Object arg : indy.bsmArgs) {
					MethodNode lambda = arg instanceof Handle handle && handle.getOwner().equals(mixin) ? own.get(handle.getName() + handle.getDesc()) : null;
					if (lambda != null) threadLocalsUsed(mixin, own, lambda, depth, found, seen, modClassCache);
				}
			}
		}
	}

	/**
	 * Whether the {@code ThreadLocal} a field read pushes has its value set or removed in the code that runs on from the
	 * read: the read's value, followed on the stack and through the locals (as a value no other is), is the receiver of a
	 * {@code set} or {@code remove} call before the code jumps away, returns or drops it. What the code before the read
	 * left in the locals and on the stack is unknown, and stays apart from it.
	 */
	private static boolean setsValue(MethodNode method, FieldInsnNode read) {
		Object value = new Object();
		AnalyzerAdapter flow = new AnalyzerAdapter("", Opcodes.ACC_STATIC, method.name, "()V", null);
		for (int i = 0; i < method.maxLocals; i++) flow.locals.add(Opcodes.TOP);
		for (int i = 0; i < method.maxStack; i++) flow.stack.add(Opcodes.TOP);
		flow.stack.add(value);
		try {
			for (AbstractInsnNode insn = read.getNext(); insn != null; insn = insn.getNext()) {
				if (insn instanceof FrameNode) continue;
				if (insn instanceof MethodInsnNode call && call.getOpcode() != Opcodes.INVOKESTATIC && (call.name.equals("set") || call.name.equals("remove"))) {
					int receiver = flow.stack.size() - (Type.getArgumentsAndReturnSizes(call.desc) >> 2);
					if (receiver >= 0 && flow.stack.get(receiver) == value) return true;
				}
				insn.accept(flow);
				if (flow.stack == null || !flow.stack.contains(value) && !flow.locals.contains(value)) return false;
			}
		} catch (RuntimeException e) {
			// Code the walk doesn't follow (a subroutine).
		}
		return false;
	}

	/** Whether a type is {@code ThreadLocal}, {@code InheritableThreadLocal} or a class of the mod extending one. */
	private boolean threadLocal(Type type, Map<String, Optional<ClassNode>> modClassCache) {
		String name = type.getSort() == Type.OBJECT ? type.getInternalName() : null;
		for (int i = 0; name != null && i < 5; i++) {
			if (name.equals("java/lang/ThreadLocal") || name.equals("java/lang/InheritableThreadLocal")) return true;
			if (name.startsWith("java/")) return false;
			ClassNode node = modClass(name, modClassCache);
			name = node == null ? null : node.superName;
		}
		return false;
	}

	/** A class of the mod, with its code (parsed once per config), or null. */
	private ClassNode modClass(String name, Map<String, Optional<ClassNode>> modClassCache) {
		return modClassCache.computeIfAbsent(name, n -> Optional.ofNullable(modClasses.readNode(n, ClassReader.SKIP_FRAMES))).orElse(null);
	}

	/**
	 * Injectors of one mixin that keep state together: the handlers (name and descriptor) and the mixin's own fields
	 * (name and descriptor) they share.
	 */
	record StateGroup(Set<String> handlers, Set<String> fields) {
	}

	/**
	 * For a self-contained mixin that adds fields (STATE-1): each injector's handler (name and descriptor) with the group of
	 * injectors keeping state together with it, joined by the mixin's own fields they use, themselves or through the
	 * mixin's methods they call, itself among them. A handler failing goes with its group; the mixin's other injectors share
	 * none of that state and stay. A handler that isn't in the map takes the whole mixin with it: its group shares a field
	 * with code that runs without an injector (an {@code @Overwrite}, a method the target or a subclass may call, a field
	 * other than a private one, which another mixin into the target may shadow), or the mixin has code pre-flight can't
	 * follow (a handler without code, a class nested in the mixin).
	 * <p>
	 * Fabric API's {@code FluidRendererMixin} on NeoForge 1.21.1: of its hooks into {@code tesselate}, which NeoForge's
	 * patches rewrote, two share a {@code ThreadLocal}, and the rest nothing; its hooks at the start of {@code tesselate}
	 * and at the end of the sprite reload share nothing either. Leaving the whole mixin out lost the reload's hook, which
	 * hands the water and lava sprites to Fabric API's fluid handlers, and Sodium's Fabric build crashed on the water
	 * handler's missing sprite.
	 */
	static Map<String, StateGroup> stateGroups(ClassNode mixin) {
		if (mixin == null) return Map.of();
		for (InnerClassNode inner : mixin.innerClasses) {
			if (inner.name.startsWith(mixin.name + "$")) return Map.of();
		}
		Set<String> ownFields = new HashSet<>();
		Set<String> outside = new HashSet<>(); // the fields code other than the injectors may use
		for (FieldNode field : mixin.fields) {
			if (Annotations.find(field, MixinNames.SHADOW) != null) continue;
			ownFields.add(field.name + ":" + field.desc);
			if ((field.access & Opcodes.ACC_PRIVATE) == 0) outside.add(field.name + ":" + field.desc);
		}
		Map<String, MethodNode> ownMethods = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			if (Annotations.find(method, MixinNames.SHADOW) == null) ownMethods.put(method.name + method.desc, method);
		}
		Set<String> called = new HashSet<>(); // the mixin's methods its other methods call or refer to
		for (MethodNode method : ownMethods.values()) {
			if (method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && !(call.name + call.desc).equals(method.name + method.desc)) {
					called.add(call.name + call.desc);
				} else if (insn instanceof InvokeDynamicInsnNode indy) {
					for (Object arg : indy.bsmArgs) {
						if (arg instanceof Handle handle && handle.getOwner().equals(mixin.name)) called.add(handle.getName() + handle.getDesc());
					}
				}
			}
		}
		Map<String, Set<String>> used = new LinkedHashMap<>();
		for (MethodNode method : mixin.methods) {
			String key = method.name + method.desc;
			if (!ownMethods.containsKey(key) || method.name.equals("<init>") || method.name.equals("<clinit>")) continue;
			if (Annotations.all(method).stream().anyMatch(a -> PreflightAsm.isInjector(a.desc))) {
				if (method.instructions == null || method.instructions.size() == 0) return Map.of(); // no code to judge
				used.put(key, fieldsUsed(mixin, method, ownFields, ownMethods, new HashSet<>()));
			} else if ((method.access & Opcodes.ACC_PRIVATE) == 0 || !called.contains(key) || Annotations.find(method, MixinNames.OVERWRITE) != null
					|| Annotations.find(method, MixinNames.INTRINSIC) != null) {
				outside.addAll(fieldsUsed(mixin, method, ownFields, ownMethods, new HashSet<>()));
			}
		}
		Map<String, StateGroup> groups = new HashMap<>();
		Set<String> grouped = new HashSet<>();
		for (String handler : used.keySet()) {
			if (grouped.contains(handler)) continue;
			Set<String> members = new TreeSet<>();
			Set<String> fields = new TreeSet<>();
			ArrayDeque<String> next = new ArrayDeque<>(List.of(handler));
			while (!next.isEmpty()) {
				String member = next.pop();
				if (!members.add(member)) continue;
				fields.addAll(used.get(member));
				used.forEach((other, theirs) -> {
					if (!members.contains(other) && !Collections.disjoint(theirs, used.get(member))) next.push(other);
				});
			}
			grouped.addAll(members);
			if (!Collections.disjoint(fields, outside)) continue;
			StateGroup group = new StateGroup(Collections.unmodifiableSet(members), Collections.unmodifiableSet(fields));
			for (String member : members) groups.put(member, group);
		}
		return groups;
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
	 *                      injectors keeping state with it, or the whole mixin, with it
	 * @param stateGroups   see {@link #stateGroups(ClassNode)}, for a self-contained mixin that adds fields
	 * @param stateless     see {@link #statelessHandlers(ClassNode)}, for a mixin that isn't self-contained
	 * @param owner         the installed mod the target belongs to, or {@code null} for a game class
	 */
	record Context(String configName, String mixinDotted, MixinConfigFile config, Refmap refmap, boolean hasPlugin,
			boolean selfContained, boolean addsFields, Map<String, StateGroup> stateGroups, Set<String> stateless, List<PreflightFinding> out,
			String owner, String loader) {
		String resolve(String selector) {
			return refmap.resolve(mixinDotted.replace('.', '/'), selector);
		}

		Context withOwner(String owner) {
			return new Context(configName, mixinDotted, config, refmap, hasPlugin, selfContained, addsFields, stateGroups, stateless, out, owner, loader);
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
		 * do); in a self-contained mixin that adds fields, it is with the injectors keeping state with it (STATE-1), or the
		 * whole mixin is (no handler named) where pre-flight can't tell which they are; otherwise it is fatal.
		 */
		void injector(MethodNode handler, int require, String message, String suggestion) {
			String key = handler.name + handler.desc;
			Severity severity = require <= 0 ? Severity.WARN : stateless.contains(key) ? Severity.LEFT_OUT : fatal();
			boolean wholeMixin = severity == Severity.LEFT_OUT && !hasPlugin && addsFields && !stateGroups.containsKey(key);
			out.add(new PreflightFinding(severity, configName, mixinDotted, message, suggestion, wholeMixin ? null : key));
		}

		/**
		 * The injectors keeping state with those of this mixin left out by the findings from {@code from} on, left out with
		 * them (STATE-1), unless the whole mixin is.
		 */
		void leaveOutStateGroups(int from) {
			if (!addsFields || hasPlugin || stateGroups.isEmpty()) return;
			Set<String> failing = new LinkedHashSet<>();
			for (PreflightFinding f : out.subList(from, out.size())) {
				if (f.severity() != Severity.LEFT_OUT || !mixinDotted.equals(f.mixin())) continue;
				if (f.handler() == null) return;
				failing.add(f.handler());
			}
			Map<String, String> goWith = new LinkedHashMap<>();
			for (String handler : failing) {
				StateGroup group = stateGroups.get(handler);
				if (group == null) continue;
				for (String other : group.handlers()) {
					if (!failing.contains(other)) goWith.putIfAbsent(other, handler);
				}
			}
			goWith.forEach((other, with) -> {
				List<String> fields = stateGroups.get(with).fields().stream().map(f -> f.substring(0, f.indexOf(':'))).toList();
				out.add(new PreflightFinding(Severity.LEFT_OUT, configName, mixinDotted, "The injector of " + other + " goes with that of " + with
						+ ", which can't apply: they keep state together in this mixin's " + (fields.size() == 1 ? "field " : "fields ")
						+ String.join(", ", fields), "", other));
			});
		}

		/**
		 * The injectors sharing a {@code ThreadLocal} of the mod's ({@link #threadLocalShares}) with those of this mixin left
		 * out by the findings from {@code from} on, left out with them (ITEM-2), unless the whole mixin is: one left alone
		 * would read a value the other never stored, or store one nothing reads. Each goes as its own failure would leave
		 * it out ({@link #injector}, under its own {@code require}): alone, or with the whole mixin where STATE-1 can't leave
		 * it out alone. One whose failure would disable the mod stays, as it did before, the report saying so. The mixin's
		 * code is only read where one of its injectors was left out alone.
		 *
		 * @param mixin the mixin, its handlers' annotations
		 */
		void leaveOutThreadLocalSharers(int from, ClassNode mixin, Supplier<Map<String, Set<String>>> shares) {
			Set<String> leftOut = new LinkedHashSet<>();
			for (PreflightFinding f : out.subList(from, out.size())) {
				if (f.severity() != Severity.LEFT_OUT || !mixinDotted.equals(f.mixin())) continue;
				if (f.handler() == null) return;
				leftOut.add(f.handler());
			}
			if (leftOut.isEmpty()) return;
			Map<String, Set<String>> shared = shares.get();
			Set<String> kept = new HashSet<>();
			ArrayDeque<String> next = new ArrayDeque<>(leftOut);
			while (!next.isEmpty()) {
				String with = next.pop();
				Set<String> fields = shared.get(with);
				if (fields == null) continue;
				for (Map.Entry<String, Set<String>> other : shared.entrySet()) {
					String key = other.getKey();
					if (leftOut.contains(key) || kept.contains(key) || Collections.disjoint(fields, other.getValue())) continue;
					String field = other.getValue().stream().filter(fields::contains).findFirst().orElseThrow();
					MethodNode handler = mixin.methods.stream().filter(m -> key.equals(m.name + m.desc)).findFirst().orElseThrow();
					AnnotationNode injector = Annotations.all(handler).stream().filter(a -> PreflightAsm.isInjector(a.desc)).findFirst().orElseThrow();
					Severity alone = require(handler, injector) <= 0 ? Severity.WARN : stateless.contains(key) ? Severity.LEFT_OUT : fatal();
					if (alone == Severity.FAIL) {
						kept.add(key);
						info("The injector of " + key + " shares the ThreadLocal " + field + " with " + with + ", which can't apply, but stays: leaving it"
								+ " out would disable the mod (ITEM-2)");
						continue;
					}
					boolean wholeMixin = alone == Severity.LEFT_OUT && !hasPlugin && addsFields && !stateGroups.containsKey(key);
					out.add(new PreflightFinding(Severity.LEFT_OUT, configName, mixinDotted, "The injector of " + key + " goes with that of " + with
							+ ": it shares the ThreadLocal " + field + " with " + with + ", which can't apply (ITEM-2)", "", wholeMixin ? null : key));
					if (wholeMixin) return;
					leftOut.add(key);
					next.add(key);
				}
			}
		}

		/**
		 * The injections an injector requires: its own {@code require}, else none in a group (whose minimum is the check, as
		 * Mixin's), else its config's default.
		 */
		int require(MethodNode handler, AnnotationNode injector) {
			return Annotations.value(injector, "require") instanceof Integer r && r >= 0 ? r : PreflightAsm.grouped(handler) ? 0 : config.defaultRequire();
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

	/**
	 * Whether Mixin, merging a generated accessor over a method of the same name and descriptor in this target, replaces
	 * it as it does on Fabric Loader: the target is a Fabric mod's own class (the one Fabric Loader loads), and the
	 * config doesn't require {@code @Overwrite} on a method that replaces one (Mixin throws then, there too).
	 */
	private boolean replacesAsOnFabric(ClassNode target, Context ctx) {
		return ctx.owner() != null && installed.isFabricMods(target.name) && !ctx.config().overwritesRequireAnnotations();
	}

	private void checkMixin(ClassNode mixin, Context ctx) {
		AnnotationNode mixinAnn = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, Annotations.MIXIN);
		if (mixinAnn == null) {
			ctx.classLevel("Class " + ctx.mixinDotted() + " is listed as a mixin but has no @Mixin annotation", "Report this to the mod author.");
			return;
		}
		boolean pseudo = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, MixinNames.PSEUDO) != null;

		for (String target : targetsOf(mixin, ctx)) {
			boolean isGame = PreflightAsm.isGameClass(target);
			String owner = isGame ? null : installed.owner(target);
			ClassNode targetNode = isGame ? mixinTarget(target) : owner != null ? installedTarget(target) : null;
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
				} else if ((mixin.access & Opcodes.ACC_ENUM) != 0 && !enumMixins) {
					// Another Fabric mod's enum, which Mixin would fail to apply it to as it does a game enum (ENUM-7), or none:
					// left out, as only the mod's code for that mod can read its constants.
					ctx.out().add(new PreflightFinding(Severity.LEFT_OUT, ctx.configName(), ctx.mixinDotted(), "Enum mixin into "
							+ target.replace('/', '.') + ", a class of another mod or none: " + noEnumMixins(), "Install the " + loader
							+ " build of the mod, if it has one."));
				} else {
					ctx.info("Target " + target.replace('/', '.') + " is not a class of Minecraft or of an installed mod, so it was not verified");
				}
				continue;
			}
			Context targetCtx = owner == null ? ctx : ctx.withOwner(owner);
			// FML's FMLMixinFeatureValidator stops the game on an enum mixin into one, though Mixin itself could apply it
			// (ENUM-4, which let them through, crashed the advanced corpus on Wilder Wild's).
			if ((mixin.access & Opcodes.ACC_ENUM) != 0 && targetNode.interfaces.contains(MixinNames.EXTENSIBLE_ENUM)) {
				targetCtx.essential("Enum mixin adds constants to " + target.replace('/', '.') + ", one of NeoForge's extensible enums, which"
						+ " NeoForge lets only its own enum extensions change", "Install the NeoForge build of the mod, if it has one.");
				continue;
			}
			// One translation couldn't turn into an ordinary mixin (EnumValuesMixins): Mixin before 0.17.1 stops the game
			// applying it (ENUM-7: Friends and Foes' shadowed constructor, a NullPointerException in Forge's Mixin 0.8.7).
			if ((mixin.access & Opcodes.ACC_ENUM) != 0 && !enumMixins) {
				// Fatal whatever the config: Mixin's error there is no invalid-mixin error, which an optional config logs.
				targetCtx.essential("Enum mixin into " + target.replace('/', '.') + ": " + noEnumMixins(), "Install the " + loader + " build of the mod, if it has one.");
				continue;
			}
			checkMembers(mixin, targetNode, targetCtx);
			if (isGame) noteRestoredSeams(mixin, targetNode, targetCtx);
		}
	}

	/**
	 * Notes each of the mixin's members that hooks a vanilla method the platform took out of the target and Fabricated
	 * puts back ({@link VanillaSeams}, SEAM-1): an injector or invoker naming it, through the refmap, or a shadow of it.
	 */
	private static void noteRestoredSeams(ClassNode mixin, ClassNode target, Context ctx) {
		List<VanillaSeams.Restored> seams = VanillaSeams.restoredIn(target);
		if (seams.isEmpty()) return;
		String simple = target.name.substring(target.name.lastIndexOf('/') + 1);
		for (MethodNode method : mixin.methods) {
			for (AnnotationNode ann : Annotations.all(method)) {
				boolean shadow = ann.desc.equals(MixinNames.SHADOW);
				for (VanillaSeams.Restored seam : seams) {
					if (shadow ? !method.desc.equals(seam.desc()) || !method.name.equals(seam.name()) && !method.name.equals("shadow$" + seam.name())
							: !namesSeam(ann, seam, target.name, ctx)) continue;
					ctx.info("@" + PreflightAsm.simpleName(ann.desc) + " " + method.name + " hooks " + seam.name() + seam.desc() + ", vanilla's method "
							+ ctx.loader() + "'s " + simple + " no longer has; Fabricated puts it back there, answering " + seam.answer() + " as "
							+ ctx.loader() + "'s code did (SEAM-1)");
				}
			}
		}
	}

	/** Whether a selector among the annotation's values (its own, its {@code @At}'s), through the refmap, names the seam. */
	private static boolean namesSeam(Object value, VanillaSeams.Restored seam, String owner, Context ctx) {
		if (value instanceof AnnotationNode ann) {
			for (int i = 1; ann.values != null && i < ann.values.size(); i += 2) {
				if (namesSeam(ann.values.get(i), seam, owner, ctx)) return true;
			}
			return false;
		}
		if (value instanceof List<?> list) return list.stream().anyMatch(v -> namesSeam(v, seam, owner, ctx));
		if (!(value instanceof String selector)) return false;
		MemberRef ref = MemberRef.parse(ctx.resolve(selector));
		return !ref.fuzzy() && seam.name().equals(ref.name()) && (ref.owner() == null || ref.owner().equals(owner))
				&& (ref.desc() == null || ref.desc().equals(seam.desc()));
	}

	/**
	 * A mixin whose initialiser, which Mixin merges into its target's constructors, branches or loads or stores an array
	 * element, which the running Mixin refuses as it applies the mixin ({@link Initialisers}, PRE-28).
	 */
	private void checkInitialiser(ClassNode mixin, Context ctx) {
		AbstractInsnNode refused = mixin == null ? null : Initialisers.refused(mixin);
		if (refused == null) return;
		ctx.classLevel("Its field initialisers, which Mixin merges into the constructors of its target, " + (Initialisers.branches(refused)
						? "branch" : "load or store an array element") + ", which " + loader + "'s Mixin refuses there (Fabric's Mixin takes it)",
				"The feature this mixin serves doesn't work on this " + loader + ".");
	}

	private String noEnumMixins() {
		return "this " + loader + "'s Mixin applies no enum mixins (Mixin's enum extensions came with 0.17.1), and an ordinary mixin"
				+ " can't add its constants instead (a constant with a body of its own, code that uses the mixin, or the mod's classes"
				+ " reading a constant as a field of the enum)";
	}

	/** The classes a mixin's {@code @Mixin} names, as internal names (none when it has no {@code @Mixin}). */
	static List<String> targetsOf(ClassNode mixin, Context ctx) {
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

	void checkMembers(ClassNode mixin, ClassNode target, Context ctx) {
		int findingsBefore = ctx.out().size();
		checkMembersOf(mixin, target, ctx);
		if (overlapsWideArguments) checkArgumentLocals(mixin, target, ctx, findingsBefore);
		checkGroups(mixin, ctx, findingsBefore);
	}

	/**
	 * The MixinExtras wrappers of the mixin that the running Mixin would store a long or double for over one of the
	 * mixin's shared locals (PRE-22: ShulkerBoxTooltip's three {@code @WrapOperation}s into
	 * {@code BundleMouseActions.onMouseScrolled}, sharing two locals, stopped the Forge 64 to 66 clients with a
	 * {@code VerifyError} as the test mod's menu opened); the injectors found failing from {@code from} on are no longer
	 * there. Mixin applies such a wrapper whatever its {@code require}, and the class then fails as it loads.
	 */
	private void checkArgumentLocals(ClassNode mixin, ClassNode target, Context ctx, int from) {
		Set<String> failing = new HashSet<>();
		for (PreflightFinding f : ctx.out().subList(from, ctx.out().size())) {
			if (!ctx.mixinDotted().equals(f.mixin()) || f.severity() == Severity.INFO) continue;
			if (f.handler() == null) {
				if (f.severity() != Severity.WARN) return;
			} else {
				failing.add(f.handler());
			}
		}
		for (ArgumentLocals.Overlap overlap : ArgumentLocals.overlaps(mixin, target, failing, ctx)) {
			ctx.injector(overlap.handler(), 1, "@" + PreflightAsm.simpleName(overlap.injector().desc) + " " + overlap.handler().name + " wraps "
							+ ArgumentLocals.describe(overlap.node()) + " in " + overlap.method().name + overlap.method().desc + " in "
							+ target.name.replace('/', '.') + ", whose " + overlap.value().getClassName() + " " + loader + "'s Mixin would store over"
							+ " the local this mixin shares as " + overlap.share() + " (@Share): it reuses an earlier injector's locals for the values"
							+ " without checking that a long or double's second slot is one of them (Fabric's Mixin checks), so the class would fail"
							+ " verification",
					"The feature this hook serves doesn't work on this " + loader + ".");
		}
	}

	/**
	 * Mixin checks the injectors of a group ({@code @Group}) together: fewer than its minimum applying stops the game,
	 * though each requires nothing alone (owo-lib's hooks after Fabric's and Quilt's loader calls stopped three corpus
	 * servers before HOOK-1; GROUP-1). The minimum is at least one, whatever the group declares: Essential's one-member
	 * groups, declaring none, stopped the Forge 61 to 66 clients where Forge's code had moved their call (GROUP-2). Each
	 * member pre-flight found can't apply has a warning from {@code from} on; when too few are left, every member fails
	 * as a required injector would, so a self-contained mixin loses the whole group, which Mixin then doesn't check.
	 */
	private static void checkGroups(ClassNode mixin, Context ctx, int from) {
		Map<String, Integer> minimums = new TreeMap<>();
		Map<String, List<MethodNode>> members = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			String name = PreflightAsm.group(method);
			if (name == null) continue;
			members.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
			int min = Annotations.value(Annotations.find(method, MixinNames.GROUP), "min") instanceof Integer declared ? declared : -1;
			minimums.merge(name, Math.max(min, 1), Math::max);
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
			AnnotationNode shadow = Annotations.find(field, MixinNames.SHADOW);
			if (shadow == null) continue;
			String name = ctx.resolve(field.name);
			MemberRef ref = MemberRef.parse(name);
			String fieldName = ref.nameOr(field.name);
			String desc = ref.fieldDescOr(field.desc);
			// Mixin looks for a shadowed field in the target class itself, not its supertypes.
			if (target.fields.stream().noneMatch(f -> f.name.equals(fieldName) && f.desc.equals(desc)) && !aliasExists(shadow, target, desc, true)) {
				InstalledClasses.AddedMember added = addedByInstalledMixin(target.name, fieldName, desc, true);
				if (added != null && !seesMixinFields) {
					// Repairs declare such a shadow as the field the other mixin adds where they can (MixinRepairs.sharedFieldRepair).
					ctx.classLevel("@Shadow field " + fieldName + " is not in " + targetDotted + "; " + addedBy(added) + " adds it, and " + loader
									+ "'s Mixin finds a @Shadow field only among the target's own (Fabric's also among those other mixins add)",
							"Ask the mod author to declare the field as " + added.owner() + "'s mixin does, as " + loader + "'s Mixin then merges the two into one field.");
				} else if (added != null) {
					ctx.info("@Shadow field " + fieldName + " is not in " + targetDotted + "; " + addedBy(added) + " adds it, so it was not verified");
				} else if (addedByAMixin(fieldName)) {
					ctx.info("@Shadow field " + fieldName + " is not in " + targetDotted + "; another mod's mixin may add it, so it was not verified");
				} else {
					ctx.classLevel("@Shadow field " + fieldName + " (" + desc + ") does not exist in " + targetDotted,
							ctx.changer(true) + " changed " + targetDotted + "; the mod needs an update.");
				}
			}
		}

		String replaced = platformFieldReplaced(mixin, target, ctx);
		if (replaced != null) {
			ctx.classLevel("Its static initializer replaces " + targetDotted + "." + replaced + ": " + loader + " goes on keeping its own object"
							+ ", so the game, reading the mixin's, would miss what " + loader + " adds to it later",
					"The feature this mixin serves doesn't work on this " + loader + ".");
		}

		for (MethodNode method : mixin.methods) {
			AnnotationNode shadow = Annotations.find(method, MixinNames.SHADOW);
			if (shadow != null) {
				String prefix = Annotations.value(shadow, "prefix") instanceof String p ? p : "shadow$";
				String name = method.name.startsWith(prefix) ? method.name.substring(prefix.length()) : method.name;
				MemberRef ref = MemberRef.parse(ctx.resolve(name));
				String methodName = ref.nameOr(name);
				String desc = ref.methodDescOr(method.desc);
				// Mixin looks for a shadowed method in the target class itself, not its supertypes (CORPUS-84); repairs drop the
				// shadow of one the target inherits where they can (MixinRepairs.inheritedShadowRepair).
				if (!PreflightAsm.declares(target, methodName, desc) && !aliasDeclared(shadow, target, desc)) {
					InstalledClasses.AddedMember added = addedByInstalledMixin(target.name, methodName, desc, false);
					ClassIndex.Resolution inherited = game.resolveMethod(target.name, methodName + desc);
					if (added != null) {
						ctx.info("@Shadow method " + methodName + " is not in " + targetDotted + "; " + addedBy(added) + " adds it, so it was not verified");
					} else if (addedByAMixin(methodName)) {
						ctx.info("@Shadow method " + methodName + " is not in " + targetDotted + "; another mod's mixin may add it, so it was not verified");
					} else if (inherited == ClassIndex.Resolution.MISSING) {
						ctx.classLevel("@Shadow method " + methodName + desc + " does not exist in " + targetDotted,
								ctx.changer(true) + " changed " + targetDotted + "; the mod needs an update.");
					} else {
						ctx.classLevel("@Shadow method " + methodName + desc + " is not declared in " + targetDotted + (inherited == ClassIndex.Resolution.FOUND
										? ", which inherits it," : " (a supertype pre-flight can't read may have it),")
										+ " and Mixin finds a @Shadow method only among the target's own methods",
								ctx.changer(true) + " moved it out of " + targetDotted + "; the mod needs an update.");
					}
				}
				continue;
			}

			if (Annotations.find(method, MixinNames.OVERWRITE) != null) {
				MemberRef ref = MemberRef.parse(ctx.resolve(method.name));
				String name = ref.nameOr(method.name);
				String desc = ref.methodDescOr(method.desc);
				if (!PreflightAsm.declares(target, name, desc)) {
					ctx.classLevel("@Overwrite target " + name + desc + " does not exist in " + targetDotted,
							"The overwritten method was changed by " + ctx.changer(false) + ".");
				}
				continue;
			}

			AnnotationNode accessor = Annotations.find(method, MixinNames.ACCESSOR);
			AnnotationNode invoker = accessor == null ? Annotations.find(method, MixinNames.INVOKER) : null;
			if (accessor != null || invoker != null) {
				// Mixin generates an instance accessor method in the target. One that already exists there replaces the
				// target's method, or makes Mixin throw where the config requires @Overwrite on a method that replaces
				// one; in a game class (a method NeoForge added, say) the repairs have made the accessor use it where
				// they could. A static accessor or invoker adds nothing to the target (Mixin rewrites the interface's
				// own method), so a same-named method there is harmless: typically the very method the invoker calls.
				boolean exists = (method.access & Opcodes.ACC_STATIC) == 0 && PreflightAsm.declares(target, method.name, method.desc);
				if (exists && accessor != null && replacesAsOnFabric(target, ctx)) {
					// A Fabric mod's own class is the one Fabric Loader loads, where Mixin replaces its method the same
					// way (Iris's accessor of Sodium's enumClass, named like Sodium's getEnumClass).
					ctx.info("@Accessor " + method.name + method.desc + " replaces " + ctx.owner() + "'s own method of that name in "
							+ targetDotted + ", as Mixin does on Fabric Loader");
					checkAccessor(method, accessor, target, ctx);
				} else if (exists) {
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
				if (PreflightAsm.isInjector(ann.desc)) {
					injector = true;
					// One Fabricated applies is checked as a class mixin's is (INTERFACE-1).
					if (itf && !interfaceInjectors && !InterfaceInjects.marked(method)) {
						ctx.injector(method, 1, "@" + PreflightAsm.simpleName(ann.desc) + " handler " + method.name + " is an injector in an interface mixin, which "
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
	 * (for example {@code hashCode}) to a class that has none in vanilla. An {@code @Intrinsic} the target declares is
	 * rejected whatever the config (Mixin takes it for a renamed interface method, and finds no rename): CORPUS-69, where
	 * one that only calls the superclass's is repaired ({@link MixinRepairs#superOnlyIntrinsic}).
	 */
	private static void checkMerged(MethodNode method, ClassNode target, Context ctx) {
		if ((method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT)) != 0 || method.name.startsWith("<")) return;
		if (Annotations.find(method, MixinNames.UNIQUE) != null) return;
		if (!PreflightAsm.declares(target, method.name, method.desc)) return;
		String where = method.name + method.desc + " already exists in " + target.name.replace('/', '.');
		if (Annotations.find(method, MixinNames.INTRINSIC) != null) {
			ctx.classLevel("@Intrinsic " + where + ", which Mixin rejects for a method that isn't a renamed interface method, so it "
					+ "rejects this mixin", ctx.changer(true) + " added this method; the mod needs an update.");
			return;
		}
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
	InstalledClasses.AddedMember addedByInstalledMixin(String target, String name, String desc, boolean field) {
		return recorded(installed.addedMember(target, name, desc, field));
	}

	/**
	 * The first mixin of an installed jar other than {@code mixin} (internal name) that adds a field of this name and
	 * descriptor to the target ({@link InstalledClasses#fieldAdders}), or null; recorded as {@link #addedByInstalledMixin}
	 * records it.
	 */
	InstalledClasses.AddedMember fieldAddedByAnotherMixin(String target, String name, String desc, String mixin) {
		for (InstalledClasses.AddedMember added : installed.fieldAdders(target, name, desc)) {
			if (!added.mixin().equals(mixin)) return recorded(added);
		}
		return null;
	}

	/** {@code added}, its mixin's fingerprint recorded: the results depend on that mixin. */
	private InstalledClasses.AddedMember recorded(InstalledClasses.AddedMember added) {
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

	static String addedBy(InstalledClasses.AddedMember added) {
		return added.owner() + "'s mixin " + added.mixin().replace('/', '.');
	}

	/** Whether the target declares a method one of a {@code @Shadow}'s aliases names: Mixin looks among its own only. */
	private static boolean aliasDeclared(AnnotationNode shadow, ClassNode target, String desc) {
		if (!(Annotations.value(shadow, "aliases") instanceof List<?> aliases)) return false;
		return aliases.stream().anyMatch(a -> a instanceof String alias && PreflightAsm.declares(target, alias, desc));
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
	public static String accessorFieldDesc(MethodNode method) {
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
		if (PreflightAsm.declares(target, methodName, desc)) return;
		ctx.accessorMember(method, "@Invoker target method " + methodName + desc + " does not exist in " + target.name.replace('/', '.'),
				"The method was renamed or removed by " + ctx.changer(false) + ".");
	}

	/** The field an {@code @Accessor} without a value names, as Mixin infers it from the method's name; null when none. */
	public static String inferAccessorName(String method) {
		return nameAfterPrefix(method, "get", "is", "set");
	}

	public static String inferInvokerName(String method) {
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

	/**
	 * Reports an injector none of whose selectors picks a method of the target. As Mixin's {@code TargetSelectors}, an
	 * injector fails only where none of them finds a method, so one that another installed mod's mixin adds is enough
	 * (PRE-23: Emotecraft's {@code @Inject(method = {"tick", "method_5773"})} into {@code Avatar}, whose {@code tick} Player
	 * Animation Library's mixin adds, and which pre-flight failed on the second name, which nothing has).
	 */
	private void reportNoTargetMethod(MethodNode handler, List<MemberRef> named, int require, String injectorName, ClassNode target, Context ctx) {
		String targetDotted = target.name.replace('/', '.');
		for (MemberRef ref : named) {
			InstalledClasses.AddedMember added = addedByInstalledMixin(target.name, ref.name(), ref.desc(), false);
			if (added != null) {
				ctx.info(injectorName + " " + handler.name + " targets method " + selector(ref) + ", which is not in " + targetDotted
						+ "; " + addedBy(added) + " adds it, so it was not verified");
				return;
			}
		}
		for (MemberRef ref : named) {
			if (addedByAMixin(ref.name())) {
				ctx.info(injectorName + " " + handler.name + " targets method " + selector(ref) + ", which is not in " + targetDotted
						+ "; another mod's mixin may add it, so it was not verified");
				return;
			}
		}
		List<String> selectors = named.stream().map(MixinPreflight::selector).distinct().toList();
		String hint = null;
		for (MemberRef ref : named) {
			if (hint == null) hint = replacedHint(target, ref.name(), ref.desc());
		}
		ctx.injector(handler, require, injectorName + (selectors.size() == 1 ? " targets method " + selectors.get(0) + ", which does not exist in "
						: " targets methods " + String.join(", ", selectors) + ", none of which exists in ") + targetDotted,
				hint != null ? hint : "The method was renamed or removed by " + ctx.changer(false) + ".");
	}

	/** {@link #replacedHint(ClassNode, String, String)} for a point's call of a method of the target. */
	private String replacedHint(ClassNode target, String rawTarget, Context ctx) {
		MemberRef ref = rawTarget == null ? null : MemberRef.parse(ctx.resolve(rawTarget));
		return ref == null || ref.fuzzy() || !target.name.equals(ref.owner()) ? null : replacedHint(target, ref.name(), ref.desc());
	}

	/**
	 * Why an injector naming a private method of vanilla's the platform replaced with one of its own, or its call, stays
	 * out ({@link ReplacedMethods}); null for any other method.
	 */
	private String replacedHint(ClassNode target, String name, String desc) {
		ReplacedMethods.Replaced replaced = name == null ? null : replacedMethods.replaced(target, name, desc);
		return replaced == null ? null : loader + " replaced that private method with " + replaced.named() + ", which " + replaced.caller().name
				+ " calls in its place; only an @Inject at its call or its start, or at its end where that method returns as vanilla's does, moves there"
				+ " (STRUCT-1).";
	}

	/** A selector's name and descriptor, as reports print it. */
	private static String selector(MemberRef ref) {
		return ref.name() + (ref.desc() != null ? ref.desc() : "");
	}

	private void checkInjector(ClassNode mixin, MethodNode handler, AnnotationNode ann, ClassNode target, Context ctx) {
		if (!(Annotations.value(ann, "method") instanceof List<?> selectors) || selectors.isEmpty()) return;
		int require = ctx.require(handler, ann);
		List<AnnotationNode> slices = Annotations.list(Annotations.value(ann, "slice"));
		String injectorName = "@" + PreflightAsm.simpleName(ann.desc);
		List<MethodNode> scanned = (mixin.access & Opcodes.ACC_INTERFACE) == 0 ? PreflightAsm.wildcardMethods(ann, handler, target, ctx) : null;
		if (scanned != null) {
			checkWildcardPoints(handler, ann, require, slices, injectorName, scanned, target, ctx);
			return;
		}

		List<MethodNode> matched = new ArrayList<>();
		boolean anyFuzzy = false;
		List<MemberRef> named = new ArrayList<>();
		for (Object o : selectors) {
			if (!(o instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) {
				anyFuzzy = true;
				continue;
			}
			named.add(ref);
			if (PreflightAsm.addsMethod(mixin, ref)) {
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
			if (!anyFuzzy && !named.isEmpty()) reportNoTargetMethod(handler, named, require, injectorName, target, ctx);
			return;
		}
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		for (MethodNode m : anyFuzzy || (mixin.access & Opcodes.ACC_INTERFACE) != 0 ? List.<MethodNode>of() : matched) {
			// As Mixin: no @Inject, @ModifyArg(s) or @ModifyConstant handler that isn't static takes a static target, and
			// SpongePowered's 0.8.5 takes an @Inject only where its handler is static exactly where its target is (from
			// 0.8.6 a static handler takes a method that takes this: Fabric API's furnace hook on Forge 66). An interface
			// mixin's handlers are static whatever they target. A selector a repair points elsewhere can meet either (LAMBDA-6).
			boolean targetStatic = (m.access & Opcodes.ACC_STATIC) != 0;
			boolean refused = targetStatic ? !handlerStatic && MixinNames.STATIC_TARGET_CHECKED.contains(ann.desc)
					: handlerStatic && exactStaticInjects && ann.desc.equals(MixinNames.INJECT);
			if (!refused) continue;
			ctx.classLevel(injectorName + " handler " + handler.name + (handlerStatic ? " is static" : " isn't static") + " where " + m.name + m.desc
							+ " in " + target.name.replace('/', '.') + (targetStatic ? " is" : " isn't") + ", which Mixin refuses",
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method otherwise (often a lambda that takes this where vanilla's doesn't)"
							: ctx.changer(true) + " declares this method otherwise") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}
		if (ann.desc.equals(MixinNames.INJECT) && !anyFuzzy && !acceptsCallbackSignature(handler, ann, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), PreflightAsm.handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters (often a lambda whose captured values are ordered differently)"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MixinNames.INJECT) && !anyFuzzy && capturesWithoutLocals(mixin, handler, ann, matched, ctx)) {
			// Mixin throws as it applies the mixin, which require doesn't cover (a config that isn't required survives it).
			ctx.injector(handler, ctx.config().required() ? 1 : 0, injectorName + " handler " + handler.name + " captures local variables of "
							+ matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.') + ", which has none past its parameters:"
							+ " Mixin rejects its descriptor whatever the capture mode",
					(ctx.owner() == null ? loader + "'s build of Minecraft reduced this method to a call" : ctx.changer(true) + " changed this method")
							+ "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MixinNames.WRAP_METHOD) && matched.stream().allMatch(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0)) {
			// MixinExtras throws while preparing the injector, which require doesn't cover (a config that isn't required survives it).
			ctx.injector(handler, ctx.config().required() ? 1 : 0, injectorName + " targets " + matched.get(0).name + matched.get(0).desc
							+ ", which is abstract in " + target.name.replace('/', '.'),
					"Mixin picks the first method of that name; the mod needs an update or this mixin must be skipped.");
			return;
		}
		if (ann.desc.equals(MixinNames.WRAP_METHOD) && !anyFuzzy && !acceptsWrapperSignature(handler, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), PreflightAsm.handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MixinNames.MODIFY_ARGS) && !anyFuzzy && !acceptsArgsSignature(handler, matched)) {
			// Mixin throws as it applies the mixin ("has an invalid signature"), which require doesn't cover.
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), PreflightAsm.handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters (often a lambda that captures more values)"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (MixinNames.VALUE_MODIFIERS.contains(ann.desc) && !anyFuzzy && !acceptsModifierSignature(handler, ann, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), PreflightAsm.handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		String unmatched = anyFuzzy ? null : unmatchedArgument(handler, ann, matched);
		if (unmatched != null) {
			// Without an ordinal Mixin throws as it applies the mixin, which require doesn't cover (a config that isn't
			// required survives it); with one the injector injects nothing, which require judges.
			boolean implicit = !(Annotations.value(ann, "ordinal") instanceof Integer o && o >= 0);
			ctx.injector(handler, implicit ? (ctx.config().required() ? 1 : 0) : require, injectorName + " " + handler.name + " modifies " + unmatched
							+ " of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.') + ", which "
							+ (matched.size() == 1 ? "that method doesn't have" : "none of those methods has"),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		String absentType = anyFuzzy ? null : absentLocalType(handler, ann, matched, target.name);
		String absentAtPoints = anyFuzzy || absentType != null ? null : absentLocalTypeAtPoints(handler, ann, matched, target.name, ctx);
		if (absentAtPoints != null) {
			// The method has such a local, but not where the point is: as above, Mixin's discriminator throws as the
			// injector applies (without an ordinal) or finds nothing at any point (with one).
			boolean implicit = !(Annotations.value(ann, "ordinal") instanceof Integer o && o >= 0);
			ctx.injector(handler, implicit ? (ctx.config().required() ? 1 : 0) : require, injectorName + " " + handler.name
							+ " modifies a local of type " + absentAtPoints + " in " + matched.get(0).name + matched.get(0).desc + " in "
							+ target.name.replace('/', '.') + ", which " + (matched.size() == 1 ? "that method doesn't have" : "none of those methods has")
							+ " where its @At points" + (implicit ? "" : " all") + " are (out of scope there)",
					(ctx.owner() == null ? loader + "'s build of Minecraft" : ctx.changer(true)) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		if (absentType != null) {
			// At a LOAD or STORE the point matches nothing, which require judges; elsewhere Mixin's discriminator throws as
			// the injector applies (without an ordinal) or finds nothing (with one), as for unmatchedArgument.
			boolean loadOrStore = Annotations.list(Annotations.value(ann, "at")).stream()
					.anyMatch(at -> Annotations.value(at, "value") instanceof String v && (v.equalsIgnoreCase("LOAD") || v.equalsIgnoreCase("STORE")));
			boolean implicit = !(Annotations.value(ann, "ordinal") instanceof Integer o && o >= 0);
			ctx.injector(handler, implicit && !loadOrStore ? (ctx.config().required() ? 1 : 0) : require, injectorName + " " + handler.name
							+ " modifies a local of type " + absentType + " in " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.')
							+ ", which " + (matched.size() == 1 ? "that method doesn't have" : "none of those methods has"),
					(ctx.owner() == null ? loader + "'s build of Minecraft" : ctx.changer(true)) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		int beyond = anyFuzzy ? -1 : ordinalBeyondLocals(handler, ann, matched, ctx);
		if (beyond >= 0) {
			ctx.injector(handler, require, injectorName + " " + handler.name + " modifies the local of type " + Type.getReturnType(handler.desc).getClassName()
							+ " with ordinal " + Annotations.value(ann, "ordinal") + " in " + matched.get(0).name + matched.get(0).desc + " in "
							+ target.name.replace('/', '.') + ", which has " + (beyond == 0 ? "none" : "at most " + beyond) + " of that type where its @At points are",
					(ctx.owner() == null ? loader + "'s build of Minecraft" : ctx.changer(true)) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MixinNames.MODIFY_CONSTANT)) {
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
		String otherSlot = indexedLocalOfOtherType(handler, matched, target.name, game);
		if (otherSlot != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable of type " + otherSlot + ", which "
							+ matched.get(0).name + " in " + target.name.replace('/', '.') + " never holds there",
					ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		String outOfScope = slices.isEmpty() ? LocalRepairs.namedLocalOutOfScope(handler, ann, matched, ctx) : null;
		if (outOfScope != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable " + outOfScope + ", which "
							+ matched.get(0).name + " in " + target.name.replace('/', '.') + " doesn't have in scope where the injector applies",
					ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		Ambiguity ambiguous = slices.isEmpty() ? ambiguousLocal(handler, ann, matched, target.name, ctx) : null;
		if (ambiguous != null && ambiguous.kept()) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable of type " + ambiguous.type()
							+ " by its type, of which " + loader + "'s Mixin finds more than one in " + matched.get(0).name + " in "
							+ target.name.replace('/', '.') + " there, keeping those whose scope ended before",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}
		if (ambiguous != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable of type " + ambiguous.type()
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
		String localArgument = localArgumentNameRejected(handler);
		if (localArgument != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the argument " + localArgument + " of " + matched.get(0).name
							+ " in " + target.name.replace('/', '.') + " by its name, which " + loader + "'s Mixin doesn't know (it names arguments arg and their"
							+ " slot; Fabric's Mixin takes their names from 0.17.0)",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}
		String definedArgument = definitionArgumentNameRejected(handler);
		if (definedArgument != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + "'s expression takes the argument " + definedArgument + " of "
							+ matched.get(0).name + " in " + target.name.replace('/', '.') + " by its name, which " + loader + "'s Mixin doesn't know"
							+ " (it names arguments arg and their slot; Fabric's Mixin takes their names from 0.17.0), so it matches nothing",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}
		String constructorPoint = constructorPointRejected(ann, matched, ctx);
		if (constructorPoint != null) {
			ctx.injector(handler, 1, injectorName + " " + handler.name + " injects into the constructor of " + target.name.replace('/', '.') + " at @At(\""
							+ constructorPoint + "\"), which " + loader + "'s Mixin takes only at a return"
							+ (constructorInjects == ConstructorInjects.UNSAFE_POINTS ? " or a point marked unsafe" : "") + " (Fabric's Mixin takes any point after super())",
					"The feature this hook serves doesn't work on this " + loader + ".");
			return;
		}
		String beforeDelegate = ConstructorRepairs.beforeDelegateRejected(handler, ann, target, matched, ctx);
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
				Verdict v = expression ? InjectionPoints.evaluateExpression(handler, m, ctx)
						: point.equals("CONSTANT") ? InjectionPoints.constantPointVerdict(Annotations.value(at, "args"), ordinal, range, m)
						: InjectionPoints.evaluate(point, resolved, ordinal, opcode, range, m);
				if (v != Verdict.MISSING && ann.desc.equals(MixinNames.MODIFY_VARIABLE) && (point.equals("LOAD") || point.equals("STORE")) && range != Range.UNEVALUATED) {
					int slot = modifiedSlot(handler, ann, m);
					Type variable = Type.getReturnType(handler.desc);
					if (slot >= 0) {
						v = InjectionPoints.accessesLocal(point.equals("LOAD"), Type.getArgumentTypes(handler.desc)[0], slot, ordinal, range, m) ? Verdict.FOUND : Verdict.MISSING;
					} else if (variable.getSort() != Type.VOID && !InjectionPoints.accessesAnyLocal(point.equals("LOAD"), variable, opcode, ordinal, range, m)) {
						v = Verdict.MISSING;
					}
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
								+ (point.equals("CONSTANT") && Annotations.value(at, "args") instanceof List<?> args ? ", args=" + args : "")
								+ (ordinal >= 0 ? ", ordinal=" + ordinal : "") + "), which does not occur in that method",
						replacedHint(target, rawTarget, ctx) != null ? replacedHint(target, rawTarget, ctx)
								: ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
				return;
			}
			if (verdict == Verdict.FOUND && ann.desc.equals(MixinNames.WRAP_OPERATION) && rawTarget != null) {
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
	 * The target's static field a mixin's static initializer assigns (a {@code @Shadow @Mutable} one), where the platform's
	 * patch has the target's own initializer take the platform's object for it, with that object's source
	 * ({@code "TYPE_BY_STATE, which is GameData.getBlockStatePointOfInterestTypeMap"}); null otherwise. NeoForge's and
	 * Forge's {@code PoiTypes.TYPE_BY_STATE} is the map their registry callbacks keep as point-of-interest types are
	 * registered: Lithium's copy of it into a faster map left every type registered after the class loaded unknown to
	 * {@code PoiTypes.forState} on Forge (POI-2).
	 */
	private String platformFieldReplaced(ClassNode mixin, ClassNode target, Context ctx) {
		MethodNode targetInit = PreflightAsm.declared(target, "<clinit>", "()V");
		if (PreflightAsm.declared(mixin, "<clinit>", "()V") == null || targetInit == null || targetInit.instructions == null) return null;
		Map<String, String> shadows = new HashMap<>();
		for (FieldNode field : mixin.fields) {
			if ((field.access & Opcodes.ACC_STATIC) == 0 || Annotations.find(field, MixinNames.SHADOW) == null
					|| Annotations.find(field, MixinNames.MUTABLE) == null) continue;
			shadows.put(field.name + ":" + field.desc, MemberRef.parse(ctx.resolve(field.name)).nameOr(field.name));
		}
		if (shadows.isEmpty()) return null;
		// The checks read mixins without their code: only such a mixin's is read.
		ClassNode code = modClasses.readNode(mixin.name, ClassReader.SKIP_FRAMES);
		MethodNode init = code == null ? null : PreflightAsm.declared(code, "<clinit>", "()V");
		if (init == null || init.instructions == null) return null;
		for (AbstractInsnNode insn : init.instructions) {
			if (!(insn instanceof FieldInsnNode put) || put.getOpcode() != Opcodes.PUTSTATIC || !put.owner.equals(mixin.name)) continue;
			String name = shadows.get(put.name + ":" + put.desc);
			String source = name == null ? null : platformValue(targetInit, target.name, name, put.desc);
			if (source != null) return name + ", which is " + source;
		}
		return null;
	}

	/** The platform's method or field whose value {@code init} stores in {@code owner.name}, named; null for any other value. */
	private static String platformValue(MethodNode init, String owner, String name, String desc) {
		for (AbstractInsnNode insn : init.instructions) {
			if (!(insn instanceof FieldInsnNode put) || put.getOpcode() != Opcodes.PUTSTATIC || !put.owner.equals(owner) || !put.name.equals(name)
					|| !put.desc.equals(desc)) continue;
			AbstractInsnNode value = put.getPrevious();
			while (value != null && value.getOpcode() < 0) value = value.getPrevious();
			String from = value instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC ? call.owner + "." + call.name
					: value instanceof FieldInsnNode get && get.getOpcode() == Opcodes.GETSTATIC ? get.owner + "." + get.name : null;
			if (from != null && MixinNames.isPlatformCode(from)) return from.substring(from.lastIndexOf('/') + 1).replace('$', '.');
		}
		return null;
	}

	/**
	 * An injector selecting its target's methods by a bare wildcard ({@link PreflightAsm#wildcardMethods}) applies wherever
	 * its points occur in them, and Mixin stops on too few injections whatever methods it scanned: judged by their count
	 * only, where every point is counted. Balm's drag hooks select {@code MouseHandler}'s methods so, at a call Forge 52's
	 * patch moved into a hook of its own (WILD-1).
	 */
	private static void checkWildcardPoints(MethodNode handler, AnnotationNode ann, int require, List<AnnotationNode> slices, String injectorName,
			List<MethodNode> methods, ClassNode target, Context ctx) {
		if (require < 1) return;
		int injections = 0;
		List<String> points = new ArrayList<>();
		for (AnnotationNode at : Annotations.list(Annotations.value(ann, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = Annotations.value(at, "target") instanceof String t ? ctx.resolve(t) : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			for (MethodNode m : methods) {
				int n = InjectionPoints.count(point, rawTarget, ordinal, opcode, slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx), m);
				if (n < 0) return;
				injections += n;
			}
			points.add("@At(\"" + point + "\"" + (rawTarget != null ? ", target=\"" + rawTarget + "\"" : "") + (ordinal >= 0 ? ", ordinal=" + ordinal : "") + ")");
		}
		if (points.isEmpty() || injections >= require) return;
		ctx.injector(handler, require, injectorName + " " + handler.name + " selects the methods of " + target.name.replace('/', '.') + " by \"*\" and applies "
						+ injections + " time(s) where it requires " + require + ": " + String.join(", ", points) + " occurs "
						+ (injections == 0 ? "in none of them" : "that often in them"),
				ctx.changer(true) + " changed the code it hooks; the mod needs an update or this mixin must be skipped.");
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
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null) return true;
		if (args.isEmpty() || !PreflightAsm.isCallbackInfo(args.get(args.size() - 1))) return true; // unusual shape: leave it to Mixin
		if (args.size() == 1) return true;
		List<Type> wanted = args.subList(0, args.size() - 1);
		for (MethodNode target : targets) {
			if (List.of(Type.getArgumentTypes(target.desc)).equals(wanted)) return true;
		}
		return false;
	}

	/**
	 * Whether an {@code @Inject} capturing locals ({@code locals}, its handler taking more than the target's parameters and
	 * the callback info) has one target method, with its injection points, and that method has no local variable past its
	 * parameters: Mixin can capture none there and, the handler's descriptor not being the plain one, throws "Invalid
	 * descriptor" whatever the capture mode, unless the mixin has a {@code @Surrogate} of that descriptor. NeoForge 21.1
	 * reduced {@code MappedRegistry.register} to a call of an overload taking the id, so FrozenLib's entry added event
	 * stopped the advanced corpus server (CORPUS-51) where {@code OverloadRepairs.capturedLocalsRepairs} can't move it.
	 */
	private static boolean capturesWithoutLocals(ClassNode mixin, MethodNode handler, AnnotationNode inject, List<MethodNode> targets, Context ctx) {
		if (Annotations.value(inject, "locals") == null || targets.size() != 1) return false;
		MethodNode target = targets.get(0);
		Type[] own = Type.getArgumentTypes(target.desc);
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.size() <= own.length + 1 || !PreflightAsm.isCallbackInfo(args.get(own.length))) return false;
		if (target.maxLocals > OverloadRepairs.firstLocalSlot(target)) return false;
		List<AnnotationNode> ats = Annotations.list(Annotations.value(inject, "at"));
		if (ats.isEmpty() || InjectionPoints.pointsIn(ats, Annotations.list(Annotations.value(inject, "slice")), target, ctx) != Verdict.FOUND) return false;
		Type[] plain = Arrays.copyOf(own, own.length + 1);
		plain[own.length] = args.get(own.length);
		String surrogate = Type.getMethodDescriptor(Type.VOID_TYPE, plain);
		return mixin.methods.stream().noneMatch(m -> m.name.equals(handler.name) && m.desc.equals(surrogate));
	}

	/**
	 * Mixin accepts a value modifier's handler ({@link MixinNames#VALUE_MODIFIERS}) taking the value and then the target's
	 * first parameters, as many as it declares (Mixin's {@code Injector.validateParams}: Balm's {@code @ModifyVariable}
	 * takes two of three); anything else is an {@code InvalidInjectionException} when the mixin applies, whatever its
	 * {@code require}, and the whole class fails with it: Farmer's Delight's hook into {@code CropBlock.getGrowthSpeed},
	 * whose first parameter NeoForge's build made a block state, and LambDynamicLights' {@code @ModifyReturnValue} on the
	 * overload of {@code LevelRenderer.getLightColor} Mixin picks, which took {@code LevelRenderer} from every mod
	 * (CORPUS-45). A {@code @ModifyConstant} of a class constant ({@code classValue}) that Mixin finds as an
	 * {@code instanceof} takes the object and the class instead of the value, and returns whether it is one or the class
	 * to check against ({@code RedirectInjector.injectAtInstanceOf}), exactly those two (Lithium's collision context hooks,
	 * PRE-29). Handlers that use {@code @Coerce} are left to Mixin.
	 */
	private static boolean acceptsModifierSignature(MethodNode handler, AnnotationNode injector, List<MethodNode> targets) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.size() <= 1) return true;
		Type object = Type.getType(Object.class);
		Type type = Type.getType(Class.class);
		boolean instanceOf = injector.desc.equals(MixinNames.MODIFY_CONSTANT) && args.get(0).equals(object) && args.get(1).equals(type)
				&& Annotations.list(Annotations.value(injector, "constant")).stream().anyMatch(c -> Annotations.value(c, "classValue") != null);
		Type result = Type.getReturnType(handler.desc);
		if (instanceOf && !result.equals(Type.BOOLEAN_TYPE) && !result.equals(type)) return false;
		List<Type> captured = args.subList(instanceOf ? 2 : 1, args.size());
		return targets.stream().allMatch(target -> {
			List<Type> params = List.of(Type.getArgumentTypes(target.desc));
			return captured.size() <= params.size() && params.subList(0, captured.size()).equals(captured);
		});
	}

	/**
	 * The argument a {@code @ModifyVariable} with {@code argsOnly} picks by its handler's type alone (by an ordinal among
	 * the arguments of that type, or as the only one), where no target method takes it; null where one does, or where the
	 * handler names the argument or its slot. Mixin's discriminator then looks only at the method's parameters, by their
	 * exact type. With an ordinal and too few of that type, the injector injects nothing; without one Mixin needs exactly
	 * one and throws as it applies the mixin ("Found 0 candidate variables but exactly 1 is required"). Architectury's
	 * char-typed screen hooks modify the {@code GuiEventListener} vanilla's {@code KeyboardHandler.charTyped} lambdas
	 * take, which NeoForge's 1.21.1 build passes them as a {@code Screen}; Mixin found none (CORPUS-57). A handler can't
	 * take the {@code Screen} in its place: it may return another listener, which the lambda couldn't hold.
	 */
	static String unmatchedArgument(MethodNode handler, AnnotationNode injector, List<MethodNode> targets) {
		if (!injector.desc.equals(MixinNames.MODIFY_VARIABLE) || targets.isEmpty() || !Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "name") != null || Annotations.value(injector, "index") instanceof Integer index && index >= 0
				|| Boolean.TRUE.equals(Annotations.value(injector, "print"))) return null;
		Type type = Type.getReturnType(handler.desc);
		if (type.getSort() == Type.VOID) return null;
		int ordinal = Annotations.value(injector, "ordinal") instanceof Integer o ? o : -1;
		for (MethodNode m : targets) {
			long count = List.of(Type.getArgumentTypes(m.desc)).stream().filter(type::equals).count();
			if (ordinal >= 0 ? count > ordinal : count == 1) return null;
		}
		return ordinal >= 0 ? "argument " + ordinal + " among those of type " + type.getClassName() : "the only argument of type " + type.getClassName();
	}

	/**
	 * The type of the local a {@code @ModifyVariable} picks by its type alone (as the only local of that type where it
	 * applies, or by an ordinal among them) where no target method can have a local of that type anywhere, as Mixin
	 * sees its locals ({@link #noLocalOfType}); null where one may, where the handler names the local or its slot, or
	 * where it looks only at the arguments ({@link #unmatchedArgument}). Mixin's discriminator then finds no candidate
	 * at any point: at a {@code LOAD} or {@code STORE} the point matches nothing ("has invalid IMPLICIT discriminator
	 * ... Found 0 candidate variables but exactly 1 is required"). Puzzles Lib's screen effect hook modifies the block
	 * state vanilla's {@code ScreenEffectRenderer.renderScreenEffect} stores; NeoForge's 1.21.1 build keeps the state and
	 * its position in a pair instead, and the 1.21.1 corpus client stopped as Mixin applied it (CORPUS-54). Only
	 * reference types are judged: a primitive local's type isn't read off the code as plainly.
	 */
	static String absentLocalType(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, String owner) {
		if (!injector.desc.equals(MixinNames.MODIFY_VARIABLE) || targets.isEmpty() || Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "name") != null || Annotations.value(injector, "index") instanceof Integer index && index >= 0
				|| Boolean.TRUE.equals(Annotations.value(injector, "print"))) return null;
		Type type = Type.getReturnType(handler.desc);
		if (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) return null;
		for (MethodNode m : targets) {
			if (!noLocalOfType(type, m, owner)) return null;
		}
		return type.getClassName();
	}

	/**
	 * Whether no local of the reference type {@code type} can be among {@code method}'s locals as Mixin builds them
	 * ({@code Locals.getLocalsAt}): from its arguments, its local variable table, and, where that table doesn't cover a
	 * slot, a table Mixin generates from the values the code stores. So the arguments and the table must name none of
	 * that type, and each value stored in a local must have a type read off the instruction that makes it (a call, a
	 * field read, a cast, a new object, a constant, another local the table types, a caught exception), never that
	 * type, and one type per slot (Mixin's generated table merges a slot's types into their common superclass). False
	 * where that can't be shown: no local variable table (as Mojang ships the game), a value made otherwise.
	 */
	static boolean noLocalOfType(Type type, MethodNode method, String owner) {
		if (method.instructions == null || method.instructions.size() == 0 || method.localVariables == null || method.localVariables.isEmpty()) return false;
		if (List.of(Type.getArgumentTypes(method.desc)).contains(type)) return false;
		for (LocalVariableNode local : method.localVariables) {
			if (local.desc.equals(type.getDescriptor())) return false;
		}
		Map<AbstractInsnNode, String> handlers = new HashMap<>();
		for (TryCatchBlockNode block : method.tryCatchBlocks) {
			String caught = block.type == null ? "java/lang/Throwable" : block.type;
			String previous = handlers.putIfAbsent(block.handler, caught);
			if (previous != null && !previous.equals(caught)) handlers.put(block.handler, null);
		}
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		int argSlots = isStatic ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) argSlots += arg.getSize();
		Map<Integer, Type> slotTypes = new HashMap<>();
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (insn.getOpcode() != Opcodes.ASTORE) continue;
			int slot = ((VarInsnNode) insn).var;
			Type stored = storedType(method, i, owner, isStatic, argSlots, handlers);
			if (stored == null || stored.equals(type)) return false;
			if (stored.getSort() == Type.VOID) continue; // null
			Type before = slotTypes.putIfAbsent(slot, stored);
			if (before != null && !before.equals(stored)) return false;
		}
		return true;
	}

	/**
	 * As {@link #absentLocalType}, where the method has a local of that type but none can be among Mixin's locals at the
	 * instructions the injector's {@code @At} picks ({@link #absentAt}): without an ordinal at any of them, as Mixin's
	 * discriminator then throws applying the injector, with one at all of them. Only call and field points ({@code INVOKE},
	 * {@code FIELD}) without a shift are judged, in methods with a local variable table. Balm's crop growth hook modifies
	 * the block state vanilla's {@code CropBlock.getGrowthSpeed} reads its farmland from, at the method's first call of
	 * {@code BlockState.is(Block)}: Forge's build asks the state {@code canSustainPlant} instead, so the first such call is
	 * one after the loop, where the state's local is gone, and the Forge 47 and 61 corpus servers stopped as Mixin applied
	 * it (CORPUS-54).
	 */
	static String absentLocalTypeAtPoints(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, String owner, Context ctx) {
		if (!injector.desc.equals(MixinNames.MODIFY_VARIABLE) || targets.isEmpty() || Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "name") != null || Annotations.value(injector, "index") instanceof Integer index && index >= 0
				|| Boolean.TRUE.equals(Annotations.value(injector, "print"))) return null;
		Type type = Type.getReturnType(handler.desc);
		if (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) return null;
		boolean implicit = !(Annotations.value(injector, "ordinal") instanceof Integer o && o >= 0);
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		boolean anyAbsent = false;
		boolean allAbsent = true;
		boolean anyPoint = false;
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			boolean shifted = Annotations.value(at, "shift") instanceof String[] shift && shift.length == 2 && !shift[1].equals("NONE")
					|| Annotations.value(at, "by") instanceof Integer by && by != 0;
			for (MethodNode m : targets) {
				List<Integer> picked = !point.equals("INVOKE") && !point.equals("FIELD") || rawTarget == null || shifted || m.instructions == null
						|| m.localVariables == null || m.localVariables.isEmpty() ? null
						: InjectionPoints.indexes(point, ctx.resolve(rawTarget), Annotations.value(at, "ordinal") instanceof Integer n ? n : -1,
								Annotations.value(at, "opcode") instanceof Integer n ? n : -1,
								InjectionPoints.sliceFor(at, slices) == null ? Range.ALL : InjectionPoints.sliceRange(InjectionPoints.sliceFor(at, slices), m, ctx), m);
				if (picked == null) {
					// Points not judged here may have the local.
					allAbsent = false;
					continue;
				}
				for (int index : picked) {
					anyPoint = true;
					if (absentAt(type, m, index, owner)) anyAbsent = true;
					else allAbsent = false;
				}
			}
		}
		return (implicit ? anyAbsent : anyPoint && allAbsent) ? type.getClassName() : null;
	}

	/**
	 * The most locals of its type a {@code @ModifyVariable} picking its local by an ordinal among those of its type
	 * ({@code ordinal}, no name or slot) can find at any instruction its {@code @At} points pick, where that is no more
	 * than the ordinal: Mixin's discriminator then finds no local at any of them and the injector injects nothing, which
	 * {@code require} judges (Apoli's {@code modifyEffectiveTool} modifies the second boolean in scope at the
	 * {@code ItemStack.mineBlock} call in {@code ServerPlayerGameMode.destroyBlock}, vanilla's tool check; Forge 47's patch
	 * declares the other one, the block's removal, after that call, and a Forge 47 server with Origins stopped: "(0/1)
	 * succeeded", PRE-25). Judged for a type whose locals Mixin takes only from the method's local variable table
	 * ({@link #narrowLocalsAtMost}): boolean, byte, char and short, at unshifted call and field points, in methods with a
	 * table. -1 where it may find more, or isn't judged.
	 */
	static int ordinalBeyondLocals(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, Context ctx) {
		if (!injector.desc.equals(MixinNames.MODIFY_VARIABLE) || targets.isEmpty() || Boolean.TRUE.equals(Annotations.value(injector, "argsOnly"))
				|| Annotations.value(injector, "name") != null || Annotations.value(injector, "index") instanceof Integer index && index >= 0
				|| Boolean.TRUE.equals(Annotations.value(injector, "print")) || !(Annotations.value(injector, "ordinal") instanceof Integer ordinal) || ordinal < 0) {
			return -1;
		}
		Type type = Type.getReturnType(handler.desc);
		if (!List.of(Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT).contains(type.getSort())) return -1;
		List<AnnotationNode> slices = Annotations.list(Annotations.value(injector, "slice"));
		int most = -1;
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			boolean shifted = Annotations.value(at, "shift") instanceof String[] shift && shift.length == 2 && !shift[1].equals("NONE")
					|| Annotations.value(at, "by") instanceof Integer by && by != 0;
			if (!point.equals("INVOKE") && !point.equals("FIELD") || rawTarget == null || shifted) return -1;
			AnnotationNode slice = InjectionPoints.sliceFor(at, slices);
			for (MethodNode m : targets) {
				if (m.instructions == null || m.localVariables == null || m.localVariables.isEmpty()) return -1;
				Range range = slice == null ? Range.ALL : InjectionPoints.sliceRange(slice, m, ctx);
				List<Integer> picked = range == Range.UNEVALUATED ? null : InjectionPoints.indexes(point, ctx.resolve(rawTarget),
						Annotations.value(at, "ordinal") instanceof Integer n ? n : -1, Annotations.value(at, "opcode") instanceof Integer n ? n : -1, range, m);
				if (picked == null) return -1;
				for (int index : picked) {
					int found = narrowLocalsAtMost(type, m, index);
					if (found > ordinal) return -1;
					most = Math.max(most, found);
				}
			}
		}
		return most;
	}

	/**
	 * At most how many locals of {@code type}, boolean, byte, char or short, are among the locals Mixin builds at the
	 * instruction at {@code point} ({@code Locals.getLocalsAt}, whatever its version). Mixin gives a slot a local at each
	 * store (from the instruction after it), load and stack map frame on its walk up to the point, the entry of the local
	 * variable table covering that instruction for the slot, else one its generated table has (an analyser's types, which
	 * give these an int's), else any other entry for the slot. A frame gives slots up to its size as Mixin counts it (each
	 * appending frame at least the argument slots more, a full frame its locals) and those below the highest slot a store
	 * or load gave. So: the arguments of the type, and the slots below the highest of those bounds with an entry of that
	 * type in the table.
	 */
	static int narrowLocalsAtMost(Type type, MethodNode method, int point) {
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		int initial = isStatic ? 0 : 1;
		Map<Integer, Type> arguments = new HashMap<>();
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			arguments.put(initial, arg);
			initial += arg.getSize();
		}
		int reach = initial;
		int frameSize = initial;
		for (int i = 0; i <= point && i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (insn instanceof VarInsnNode var) {
				boolean load = var.getOpcode() >= Opcodes.ILOAD && var.getOpcode() <= Opcodes.ALOAD;
				int size = var.getOpcode() == Opcodes.LLOAD || var.getOpcode() == Opcodes.DLOAD || var.getOpcode() == Opcodes.LSTORE
						|| var.getOpcode() == Opcodes.DSTORE ? 2 : 1;
				// A store gives its slot a local from the next instruction on.
				if (load || i < point) reach = Math.max(reach, var.var + size);
			} else if (insn instanceof FrameNode frame && frame.type != Opcodes.F_SAME && frame.type != Opcodes.F_SAME1) {
				int width = 0;
				for (Object local : frame.local == null ? List.of() : frame.local) width += local == Opcodes.LONG || local == Opcodes.DOUBLE ? 2 : 1;
				frameSize = switch (frame.type) {
					case Opcodes.F_NEW, Opcodes.F_FULL -> Math.max(initial, width);
					case Opcodes.F_APPEND -> frameSize + Math.max(initial, width);
					default -> frameSize;
				};
				reach = Math.max(reach, frameSize);
			}
		}
		int found = 0;
		for (int slot = isStatic ? 0 : 1; slot < reach; slot++) {
			int s = slot;
			if (type.equals(arguments.get(slot)) || method.localVariables.stream().anyMatch(lv -> lv.index == s && lv.desc.equals(type.getDescriptor()))) found++;
		}
		return found;
	}

	/** {@link #keptLocalTypes}' type of a slot whose local can't be told. */
	static final String UNTOLD = "?";

	/**
	 * The type of the local SpongePowered's Mixin keeps in each slot of a method at the instruction at {@code point}, as it
	 * walks the method with the frames ModLauncher hands it, the frames its bytes have, compressed ({@code Locals.getLocalsAt},
	 * read in 0.8.5 and 0.8.7; PRE-26): {@code this} and the arguments, then, at each store (from the instruction after it)
	 * and load, the entry of the local variable table covering it for the slot, the slot after a long or double emptied. A
	 * same frame changes nothing, so a local stays once its scope has ended, until the slot is stored or loaded again (Forge
	 * 47's {@code AbstractMinecart.tick} keeps the three doubles of its client branch at the first {@code Vec3.add} call
	 * after it). Any other frame may look a slot up again, and Mixin looks a slot no entry covers up in a table of its own,
	 * so such a slot is {@link #UNTOLD} until it is stored or loaded again. Null for a slot holding none.
	 */
	static String[] keptLocalTypes(MethodNode method, String owner, int point) {
		String[] kept = new String[method.maxLocals];
		int slot = 0;
		if ((method.access & Opcodes.ACC_STATIC) == 0) kept[slot++] = Type.getObjectType(owner).getDescriptor();
		for (Type arg : Type.getArgumentTypes(method.desc)) {
			if (slot < kept.length) kept[slot] = arg.getDescriptor();
			slot += arg.getSize();
		}
		VarInsnNode store = null;
		for (int i = 0; i <= point && i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (store != null) {
				String stored = coveringType(method, i, store.var);
				kept[store.var] = stored;
				if (store.var + 1 < kept.length && (stored.equals(UNTOLD) || Type.getType(stored).getSize() == 2)) {
					kept[store.var + 1] = stored.equals(UNTOLD) ? UNTOLD : null;
				}
				store = null;
			}
			if (insn instanceof FrameNode frame && frame.type != Opcodes.F_SAME && frame.type != Opcodes.F_SAME1) {
				Arrays.fill(kept, UNTOLD);
			} else if (insn instanceof VarInsnNode var) {
				if (var.getOpcode() >= Opcodes.ILOAD && var.getOpcode() <= Opcodes.ALOAD) {
					kept[var.var] = coveringType(method, i, var.var);
				} else {
					store = var;
				}
			}
		}
		return kept;
	}

	/** The type of the last entry of the method's local variable table for a slot covering the instruction at {@code index}; {@link #UNTOLD} for none. */
	private static String coveringType(MethodNode method, int index, int slot) {
		String found = UNTOLD;
		for (LocalVariableNode lv : method.localVariables) {
			if (lv.index == slot && method.instructions.indexOf(lv.start) <= index && index < method.instructions.indexOf(lv.end)) found = lv.desc;
		}
		return found;
	}

	/**
	 * Whether no local of the reference type {@code type} can be among the locals Mixin builds at the instruction at
	 * {@code point} ({@code Locals.getLocalsAt}, whatever its version). Mixin walks the method's instructions in order up
	 * to the point, setting a slot at each store, load and stack map frame to the entry of the local variable table that
	 * covers that instruction there, or, where none does, to any other entry for the slot (or one its generated table
	 * has). So: no argument and no entry covering the point has that type; each slot with an entry of that type is
	 * stored to after the last of those entries ends before the point, every instruction from there to the point covered
	 * by an entry of the slot (none of that type, then); and the other slots never hold a value of that type, their
	 * stores typed as {@link #noLocalOfType} reads them.
	 */
	static boolean absentAt(Type type, MethodNode method, int point, String owner) {
		InsnList insns = method.instructions;
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		if (!isStatic && Type.getObjectType(owner).equals(type) || List.of(Type.getArgumentTypes(method.desc)).contains(type)) return false;
		String desc = type.getDescriptor();
		Map<Integer, Integer> lastEnd = new HashMap<>();
		for (LocalVariableNode local : method.localVariables) {
			if (!local.desc.equals(desc)) continue;
			int start = insns.indexOf(local.start);
			int end = insns.indexOf(local.end);
			if (start <= point && end > point) return false;
			lastEnd.merge(local.index, end <= point ? end : 0, Math::max);
		}
		for (Map.Entry<Integer, Integer> slot : lastEnd.entrySet()) {
			if (!overwritten(method, slot.getKey(), slot.getValue(), point)) return false;
		}
		Map<AbstractInsnNode, String> handlers = catchTypes(method);
		int argSlots = isStatic ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) argSlots += arg.getSize();
		Map<Integer, Type> slotTypes = new HashMap<>();
		for (int i = 0; i < insns.size(); i++) {
			AbstractInsnNode insn = insns.get(i);
			if (insn.getOpcode() != Opcodes.ASTORE || lastEnd.containsKey(((VarInsnNode) insn).var)) continue;
			Type stored = storedType(method, i, owner, isStatic, argSlots, handlers);
			if (stored == null || stored.equals(type)) return false;
			if (stored.getSort() == Type.VOID) continue;
			Type before = slotTypes.putIfAbsent(((VarInsnNode) insn).var, stored);
			if (before != null && !before.equals(stored)) return false;
		}
		return true;
	}

	/** The type each exception handler of the method catches, by its label; null where its try blocks catch different types. */
	private static Map<AbstractInsnNode, String> catchTypes(MethodNode method) {
		Map<AbstractInsnNode, String> handlers = new HashMap<>();
		for (TryCatchBlockNode block : method.tryCatchBlocks) {
			String caught = block.type == null ? "java/lang/Throwable" : block.type;
			String previous = handlers.putIfAbsent(block.handler, caught);
			if (previous != null && !previous.equals(caught)) handlers.put(block.handler, null);
		}
		return handlers;
	}

	/**
	 * Whether slot {@code slot} of a method never holds a value of {@code type} (not {@code Object}) as Mixin builds the
	 * method's locals ({@code Locals.getLocalsAt}), which an {@code @Local(index = N)} capture needs it to (MixinExtras
	 * finds it with Mixin's discriminator, which takes the slot only where its local's type is the handler's, exactly).
	 * Mixin types the slot at each store, load and stack map frame by the entry of the local variable table that covers
	 * the instruction there, else by the table it generates from the method's code (its verifier's types), else by any
	 * entry for the slot. So: the method has a table (Mojang's builds have none, and Mixin's generated types aren't read
	 * here), the slot is not an argument's, and no entry for it has the type; for a primitive type, no instruction stores
	 * one of its kind there (an {@code int}'s store or increment for {@code int}, {@code boolean}, {@code byte},
	 * {@code char} and {@code short}, which the verifier doesn't tell apart); for a class or array type, each value stored
	 * there has a type read off plainly ({@link #storedType}) that is neither the type nor a subclass of it (as the
	 * verifier, merging two values, may type the slot by a superclass of theirs), as far as {@code hierarchy} tells.
	 */
	static boolean slotNeverHolds(Type type, MethodNode method, int slot, String owner, ClassIndex hierarchy) {
		if (type.getSort() == Type.VOID || type.getSort() == Type.METHOD || type.getDescriptor().equals("Ljava/lang/Object;")
				|| method.instructions == null || method.instructions.size() == 0 || method.localVariables == null || method.localVariables.isEmpty()) {
			return false;
		}
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		int argSlots = isStatic ? 0 : 1;
		for (Type arg : Type.getArgumentTypes(method.desc)) argSlots += arg.getSize();
		if (slot < argSlots) return false;
		for (LocalVariableNode local : method.localVariables) {
			if (local.index == slot && local.desc.equals(type.getDescriptor())) return false;
		}
		if (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) {
			int store = type.getOpcode(Opcodes.ISTORE);
			for (int i = 0; i < method.instructions.size(); i++) {
				AbstractInsnNode insn = method.instructions.get(i);
				if (insn instanceof VarInsnNode v && v.var == slot && v.getOpcode() == store
						|| insn instanceof IincInsnNode inc && inc.var == slot && store == Opcodes.ISTORE) return false;
			}
			return true;
		}
		Map<AbstractInsnNode, String> handlers = catchTypes(method);
		for (int i = 0; i < method.instructions.size(); i++) {
			if (!(method.instructions.get(i) instanceof VarInsnNode store) || store.var != slot || store.getOpcode() != Opcodes.ASTORE) continue;
			Type stored = storedType(method, i, owner, isStatic, argSlots, handlers);
			if (stored == null || stored.getSort() != Type.VOID && mayBeTyped(stored, type, hierarchy)) return false;
		}
		return true;
	}

	/**
	 * Whether the verifier may type a value of {@code stored} as {@code type} where it merges it with others: the types
	 * are the same, or {@code type} is a superclass of {@code stored}'s (an interface type only ever comes from a value of
	 * it). True where that can't be told (an array, a class the hierarchy doesn't have).
	 */
	private static boolean mayBeTyped(Type stored, Type type, ClassIndex hierarchy) {
		if (stored.equals(type)) return true;
		if (stored.getSort() == Type.ARRAY || type.getSort() == Type.ARRAY) return stored.getSort() == Type.ARRAY && type.getSort() == Type.ARRAY;
		if (stored.getSort() != Type.OBJECT) return false;
		String wanted = type.getInternalName();
		for (String c = stored.getInternalName(); !c.equals("java/lang/Object"); ) {
			if (c.equals(wanted)) return true;
			ClassIndex.ClassInfo info = hierarchy.get(c);
			if (info == null || info.superName() == null) return true;
			c = info.superName();
		}
		return false;
	}

	/**
	 * The first {@code @Local(index = N)} a handler's parameters capture whose slot holds no value of its type in any
	 * target method ({@link #slotNeverHolds}), as "type at slot N"; null where none does or that can't be shown. MixinExtras
	 * then finds no local ("Unable to find matching local!"), and the injector none of its targets (CORPUS-55: Porting Lib
	 * Entity's {@code GuiMixin} takes {@code @Local(index = 8) MobEffectInstance} in {@code Gui.renderEffects}, where Forge
	 * 47's build, recompiled from decompiled source, keeps the effect in another slot, and its draw position and alpha
	 * too). The index counts only without an ordinal or names, outside {@code argsOnly}; a {@code LocalRef}'s type is in its
	 * signature, which isn't read here (a primitive's ref holds that primitive).
	 */
	static String indexedLocalOfOtherType(MethodNode handler, List<MethodNode> targets, String owner, ClassIndex hierarchy) {
		if (targets.isEmpty()) return null;
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int i = 0; i < params.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				Integer index = indexOnly(a);
				Type type = LocalRepairs.heldType(params[i]);
				if (index == null || type == null) continue;
				if (targets.stream().allMatch(m -> slotNeverHolds(type, m, index, owner, hierarchy))) return type.getClassName() + " at slot " + index;
			}
		}
		return null;
	}

	/**
	 * The slot an {@code @Local} takes its local by: its {@code index}, where it has no ordinal or names and isn't
	 * {@code argsOnly}, as Mixin's discriminator reads them (an ordinal first, then the index); null otherwise.
	 */
	static Integer indexOnly(AnnotationNode local) {
		if (!local.desc.equals(MixinNames.LOCAL) || !(Annotations.value(local, "index") instanceof Integer index) || index < 0
				|| Annotations.value(local, "ordinal") instanceof Integer o && o >= 0 || Annotations.value(local, "name") instanceof List<?> n && !n.isEmpty()
				|| Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))) return null;
		return index;
	}

	/**
	 * Whether a store to {@code slot} at or after {@code from - 1} and before {@code point} is followed, up to the point,
	 * only by instructions an entry of the local variable table for that slot covers.
	 */
	private static boolean overwritten(MethodNode method, int slot, int from, int point) {
		InsnList insns = method.instructions;
		for (int i = point - 1; i >= Math.max(from - 1, 0); i--) {
			if (!(insns.get(i) instanceof VarInsnNode store) || store.var != slot || store.getOpcode() < Opcodes.ISTORE || store.getOpcode() > Opcodes.ASTORE) {
				continue;
			}
			for (int at = i + 1; at <= point; at++) {
				if (!covered(method, slot, at)) return false;
			}
			return true;
		}
		return false;
	}

	private static boolean covered(MethodNode method, int slot, int at) {
		for (LocalVariableNode local : method.localVariables) {
			if (local.index == slot && method.instructions.indexOf(local.start) <= at && method.instructions.indexOf(local.end) > at) return true;
		}
		return false;
	}

	/**
	 * The type of the value the {@code ASTORE} at {@code index} stores, read off the instruction right before it (none
	 * in between but line numbers); {@code VOID} for null, null where it can't be read off that plainly.
	 */
	private static Type storedType(MethodNode method, int index, String owner, boolean isStatic, int argSlots, Map<AbstractInsnNode, String> handlers) {
		AbstractInsnNode producer = method.instructions.get(index).getPrevious();
		while (producer instanceof LineNumberNode) producer = producer.getPrevious();
		if (producer instanceof FrameNode) {
			// A caught exception, stored where its handler starts.
			AbstractInsnNode label = producer.getPrevious();
			while (label instanceof LineNumberNode) label = label.getPrevious();
			String caught = label instanceof LabelNode ? handlers.get(label) : null;
			return caught == null ? null : Type.getObjectType(caught);
		}
		if (producer instanceof LabelNode) {
			String caught = handlers.get(producer);
			return caught == null ? null : Type.getObjectType(caught);
		}
		if (producer == null) return null;
		switch (producer.getOpcode()) {
			case Opcodes.ACONST_NULL:
				return Type.VOID_TYPE;
			case Opcodes.INVOKEVIRTUAL:
			case Opcodes.INVOKESTATIC:
			case Opcodes.INVOKEINTERFACE:
				return Type.getReturnType(((MethodInsnNode) producer).desc);
			case Opcodes.INVOKESPECIAL: {
				MethodInsnNode call = (MethodInsnNode) producer;
				return call.name.equals("<init>") ? Type.getObjectType(call.owner) : Type.getReturnType(call.desc);
			}
			case Opcodes.INVOKEDYNAMIC:
				return Type.getReturnType(((InvokeDynamicInsnNode) producer).desc);
			case Opcodes.GETFIELD:
			case Opcodes.GETSTATIC:
				return Type.getType(((FieldInsnNode) producer).desc);
			case Opcodes.CHECKCAST:
				return Type.getObjectType(((TypeInsnNode) producer).desc);
			case Opcodes.ANEWARRAY:
				return Type.getType("[" + Type.getObjectType(((TypeInsnNode) producer).desc).getDescriptor());
			case Opcodes.MULTIANEWARRAY:
				return Type.getType(((MultiANewArrayInsnNode) producer).desc);
			case Opcodes.LDC: {
				Object constant = ((LdcInsnNode) producer).cst;
				if (constant instanceof String) return Type.getType(String.class);
				if (constant instanceof Type t) return Type.getObjectType(t.getSort() == Type.METHOD ? "java/lang/invoke/MethodType" : "java/lang/Class");
				if (constant instanceof Handle) return Type.getObjectType("java/lang/invoke/MethodHandle");
				return null;
			}
			case Opcodes.ALOAD: {
				int slot = ((VarInsnNode) producer).var;
				int at = method.instructions.indexOf(producer);
				for (LocalVariableNode local : method.localVariables) {
					if (local.index == slot && method.instructions.indexOf(local.start) <= at && method.instructions.indexOf(local.end) > at) {
						return Type.getType(local.desc);
					}
				}
				if (!isStatic && slot == 0) return Type.getObjectType(owner);
				if (slot >= argSlots) return null;
				int arg = isStatic ? 0 : 1;
				for (Type argType : Type.getArgumentTypes(method.desc)) {
					if (arg == slot) return argType;
					arg += argType.getSize();
				}
				return null;
			}
			default:
				return null;
		}
	}

	/**
	 * Whether a {@code @ModifyArgs} handler takes Mixin's {@code Args} alone or followed by all of each target method's
	 * parameters, as Mixin requires ({@code ModifyArgsInjector.verifyTarget}); it throws as it applies the mixin
	 * otherwise (CORPUS-58: Ledger's anvil hook, where NeoForge's lambda captures one more value).
	 */
	private static boolean acceptsArgsSignature(MethodNode handler, List<MethodNode> targets) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.size() <= 1) return true;
		List<Type> wanted = args.subList(1, args.size());
		return targets.stream().allMatch(target -> List.of(Type.getArgumentTypes(target.desc)).equals(wanted));
	}

	/**
	 * MixinExtras accepts a {@code @WrapMethod} handler whose parameters, after sugar parameters are removed, are the
	 * target's parameters followed by the {@code Operation}, in each method it wraps; anything else is an
	 * {@code InvalidInjectionException} when the mixin applies, whatever its {@code require}. Handlers that use
	 * {@code @Coerce}, or whose shape is unusual, are left to Mixin.
	 */
	private static boolean acceptsWrapperSignature(MethodNode handler, List<MethodNode> targets) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.isEmpty() || !args.get(args.size() - 1).getDescriptor().equals(MixinNames.OPERATION)) return true;
		List<Type> wanted = args.subList(0, args.size() - 1);
		return targets.stream().allMatch(target -> List.of(Type.getArgumentTypes(target.desc)).equals(wanted));
	}

	/**
	 * The first name a handler's {@code @Local(name = ...)} parameter asks for that no target method declares, or null.
	 * Only targets that carry local variable names are judged.
	 */
	static String missingLocal(MethodNode handler, List<MethodNode> targets) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		for (int i = 0; i < params.length; i++) {
			for (AnnotationNode a : Annotations.ofParameter(handler, i)) {
				if (!a.desc.equals(MixinNames.LOCAL)) continue;
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
	 * A local an {@code @Local} captures by its type alone (no name, index or ordinal) when, at every instruction the
	 * injector's {@code @At} selects in every target, more than one local of that type is in scope: MixinExtras then can't
	 * tell which one is meant. Where the running Mixin keeps locals once their scope has ended (PRE-26), those it keeps
	 * there ({@link #keptLocalTypes}) count too. Null when that can't be shown (no local variable table, an injection point
	 * other than a call, field access or object creation, ...).
	 */
	private Ambiguity ambiguousLocal(MethodNode handler, AnnotationNode injector, List<MethodNode> targets, String owner, Context ctx) {
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
				if (!local.desc.equals(MixinNames.LOCAL) || Annotations.value(local, "name") != null || Annotations.value(local, "index") != null
						|| Annotations.value(local, "ordinal") != null || Boolean.TRUE.equals(Annotations.value(local, "argsOnly"))
						|| params[p].getDescriptor().startsWith("Lcom/llamalad7/mixinextras/sugar/ref/")) continue;
				String type = params[p].getDescriptor();
				boolean judged = false;
				boolean single = false;
				boolean singleInScope = false;
				for (MethodNode target : targets) {
					if (target.localVariables == null || target.localVariables.isEmpty()) return null;
					MethodNode framed = keepsLocalsPastScope ? classes.withFrames(owner, target) : null;
					MethodNode m = framed != null ? framed : target;
					int seen = 0;
					for (int i = 0; i < m.instructions.size(); i++) {
						AbstractInsnNode insn = m.instructions.get(i);
						if (opcode >= 0 && insn.getOpcode() != opcode || !InjectionPoints.matchesInsn(point, ref, insn)) continue;
						if (ordinal >= 0 && seen++ != ordinal) continue;
						judged = true;
						int inScope = 0;
						Set<Integer> found = new HashSet<>();
						for (LocalVariableNode lv : m.localVariables) {
							if (!lv.desc.equals(type) || m.instructions.indexOf(lv.start) > i || i >= m.instructions.indexOf(lv.end)) continue;
							inScope++;
							found.add(lv.index);
						}
						singleInScope |= inScope <= 1;
						if (framed == null) {
							single |= inScope <= 1;
							continue;
						}
						String[] kept = keptLocalTypes(framed, owner, i);
						for (int slot = (framed.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1; slot < kept.length; slot++) {
							if (type.equals(kept[slot])) found.add(slot);
						}
						single |= found.size() <= 1;
					}
				}
				if (judged && !single) return new Ambiguity(params[p].getClassName(), singleInScope);
			}
		}
		return null;
	}

	/**
	 * A local {@link #ambiguousLocal} finds more than one of.
	 *
	 * @param kept whether only counting the locals the running Mixin keeps once their scope has ended makes it so (PRE-26)
	 */
	private record Ambiguity(String type, boolean kept) {
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
}
