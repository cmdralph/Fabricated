package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
	private final Map<String, Optional<ClassNode>> targetCache = new HashMap<>();
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
			boolean clientSide, boolean missingTargetsFatal) {
		this.game = game;
		this.installed = installed;
		this.modClasses = modClasses;
		this.untransformable = untransformable;
		this.clientSide = clientSide;
		this.missingTargetsFatal = missingTargetsFatal;
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
					selfContained && addsFields(node), stateless, out, null);
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
	 * 1.21.11 build is compiled from decompiled code, and NeoForge's patches add lambdas): it is pointed at the one
	 * lambda of the same enclosing method with the same descriptor.</li>
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
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, false, false, false, Set.of(), out, null);
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
					for (String lambda : lambdaRepairs(ann, targets, ctx)) {
						changed = true;
						out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
								+ " targets " + lambda + ", which this game build numbers differently", ""));
					}
					if (softenLocalCapture(ann)) {
						changed = true;
						out.add(new PreflightFinding(Severity.INFO, configName, ctx.mixinDotted(), "@Inject " + method.name
								+ " captures local variables; if NeoForge's code has other locals there, it is skipped instead of crashing", ""));
					}
					// A group's alternative for Fabric Loader's hook too: the other alternatives are for other loaders' hooks.
					if (targets.size() == 1) {
						for (String moved : loaderHookRepairs(ann, targets.getFirst(), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
					}
					// A grouped injector is one of the mod's alternatives for code that differs: pointing it elsewhere could make
					// more of the group apply than the group allows.
					if (targets.size() == 1 && !grouped(method)) {
						for (String pinned : overloadByParameters(method, ann, targets.getFirst(), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " targets " + pinned + ", which this game build declares after another overload; it is pinned to that one", ""));
						}
						for (String moved : overloadRepairs(node, method, ann, targets.getFirst(), ctx, bridges)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " targets " + moved, ""));
						}
						// A repair that bridged the handler moved the injector onto the bridge: it is done with.
						if (!Annotations.all(method).contains(ann)) continue;
						for (String moved : constantSplitRepairs(node, method, ann, targets.getFirst(), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@ModifyConstant " + method.name
									+ " targets " + moved, ""));
						}
						for (String moved : subtypeCallRepairs(ann, targets.getFirst(), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
						for (String lambda : lambdaCallRepairs(ann, method, targets.getFirst(), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call " + lambda, ""));
						}
						for (String moved : insertedArgumentRepairs(node, method, ann, targets.getFirst(), ctx, bridges)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
						if (!Annotations.all(method).contains(ann)) continue;
						List<String> captured = capturedValueRepairs(node, method, ann, targets.getFirst(), ctx, bridges);
						// Where the parameters can't be matched by type (one the method has twice), in order, around one added.
						for (String lambda : captured.isEmpty() ? addedParameterRepairs(node, method, ann, targets.getFirst(), ctx, bridges) : captured) {
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
							+ " already exists " + (madePublic ? "" : "publicly ") + "in " + targets.getFirst().name.replace('/', '.')
							+ " (added by NeoForge or this Minecraft version); the mixin uses that method instead of generating one"
							+ (madePublic ? ", made public for it" : ""), ""));
				} else if (accessor != null) {
					String field = capturedFieldRepair(method, accessor, targets, ctx);
					if (field == null) continue;
					changed = true;
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Accessor " + method.name + method.desc
							+ " targets the captured variable field " + field + " in " + targets.getFirst().name.replace('/', '.')
							+ ", which this game build names differently", ""));
				}
			}
			for (FieldNode field : node.fields) {
				String captured = capturedShadowRepair(node, field, targets, ctx);
				if (captured == null) continue;
				changed = true;
				out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Shadow field " + captured + " is a captured variable field of "
						+ targets.getFirst().name.replace('/', '.') + ", which this game build names " + field.name + "; the mixin uses that", ""));
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
						ann.values.set(i + 1, points.getFirst());
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Points the selectors of an injector that name a missing lambda ({@code lambda$enclosing$N} with a descriptor) at
	 * the only lambda of the same enclosing method and descriptor that every target has under one other name, and
	 * returns the selectors' original names.
	 */
	private static List<String> lambdaRepairs(AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<String> repaired = new ArrayList<>();
		next:
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			Matcher lambda = ref.fuzzy() || ref.name() == null || ref.desc() == null ? null : LAMBDA.matcher(ref.name());
			if (lambda == null || !lambda.matches()) continue;
			String prefix = "lambda$" + lambda.group(1) + "$";
			String renamed = null;
			for (ClassNode target : targets) {
				String only = null;
				for (MethodNode m : target.methods) {
					if (!m.desc.equals(ref.desc())) continue;
					if (m.name.equals(ref.name())) continue next; // present: nothing to repair
					if (!m.name.startsWith(prefix) || !LAMBDA.matcher(m.name).matches()) continue;
					if (only != null) {
						only = null;
						break; // ambiguous
					}
					only = m.name;
				}
				if (only == null || (renamed != null && !renamed.equals(only))) {
					renamed = null;
					break;
				}
				renamed = only;
			}
			if (renamed == null) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, renamed + ref.desc());
			repaired.add(ref.name() + ref.desc());
		}
		return repaired;
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
				|| !(inject ? isCallbackInfo(args.getLast()) : args.getLast().getDescriptor().equals(OPERATION))) return List.of();
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
	private static List<String> overloadRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (ats.isEmpty() || injector.desc.equals(MODIFY_VARIABLE) || Annotations.value(injector, "locals") != null) return List.of();
		// An @Inject handler takes the callback info only, or (bridged below) the target's parameters before it.
		boolean takesParameters = false;
		if (injector.desc.equals(INJECT)) {
			List<Type> args = handlerArgs(handler);
			if (args == null || args.isEmpty() || !isCallbackInfo(args.getLast())) return List.of();
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
						&& handlerArgs(handler).getLast().getDescriptor().equals(CALLBACK_INFO_RETURNABLE)
						|| !independentHandlers(mixin).contains(handler.name + handler.desc))) continue;
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
				how = localsGone ? ", which NeoForge reduced to a call to " : replacement != null ? ", which NeoForge deprecated for "
						: split ? ", the code of which there NeoForge split off into " : ", whose code NeoForge moved into ";
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
				how = ", which NeoForge keeps only to call ";
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
		if (methods.size() != 1 || methods.getFirst().name.startsWith("<")) return List.of();
		MethodNode lambda = methods.getFirst();
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
		AnnotationNode at = ats.getFirst();
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
	 * ({@link #hookCall}), its handlers bridged the same way when the hook takes one more argument, when the handler uses
	 * nothing of the mixin's own ({@link #statelessHandlers}) (HOOK-2).
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
			if ((hook != null || leading != null) && !independentHandlers(mixin).contains(handler.name + handler.desc)) continue;
			if (leading != null) {
				int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
				if (ordinal >= countCalls(methods, leading)) continue;
				for (int i = 0; i + 1 < at.values.size(); i += 2) {
					if ("target".equals(at.values.get(i))) at.values.set(i + 1, "L" + leading.owner + ";" + leading.name + leading.desc);
				}
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which this game build makes through NeoForge's "
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
			if (!pointOnly && !overload.equals(desc)) {
				Type ownerType = Type.getObjectType(owner);
				MethodNode bridge = injector.desc.equals(WRAP_OPERATION) ? HandlerBridges.wrapperBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall, bridges)
						: HandlerBridges.callBridge(mixin, handler, ownerType, desc, overload, inserted, staticCall, condition ? Type.BOOLEAN_TYPE : Type.getReturnType(desc), bridges);
				if (bridge == null) continue;
				HandlerBridges.moveInjector(handler, injector, bridge);
				bridges.add(bridge);
			}
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, constructor ? overload : "L" + callOwner + ";" + callName + overload);
			}
			String via = pointOnly || overload.equals(desc) ? "" : ", through a bridge handler";
			if (hook != null) {
				moved.add(owner.replace('/', '.') + "." + name + original + ", which this game build makes inside NeoForge's hook "
						+ callOwner.replace('/', '.') + "." + callName + overload + "; the injection point is pointed at the hook's call" + via);
				continue;
			}
			if (replacement != null) {
				moved.add(owner.replace('/', '.') + "." + name + desc + ", which NeoForge deprecated: this game build calls its replacement "
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
				|| !(selectors.getFirst() instanceof String raw)) return List.of();
		List<Type> args = handlerArgs(handler);
		if (args == null || args.size() < 2 || !isCallbackInfo(args.getLast())) return List.of();
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
	private static List<String> constantSplitRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx) {
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
					|| !independentHandlers(mixin).contains(handler.name + handler.desc)) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, split.name + split.desc);
			moved.add(from.name + from.desc + ", the code of which there NeoForge split off into " + split.name + split.desc
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
				|| !call.desc.equals(overload.desc) || code.getLast().getOpcode() != Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)) return -1;
		int[] slots = new int[own.length];
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < own.length; i++) {
			slots[i] = slot;
			slot += own[i].getSize();
		}
		int start = 0;
		if (!isStatic) {
			if (!(code.getFirst() instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return -1;
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
		AnnotationNode at = ats.getFirst();
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
		if (!(code.getFirst() instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return false;
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
				&& code.getLast().getOpcode() == Opcodes.RETURN;
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
			} else if (insn instanceof MethodInsnNode call && call.owner.equals(mixin.name) && ownMethods.get(call.name + call.desc) instanceof MethodNode callee) {
				fields.addAll(fieldsUsed(mixin, callee, ownFields, ownMethods, seen));
			} else if (insn instanceof InvokeDynamicInsnNode indy) {
				for (Object arg : indy.bsmArgs) {
					if (!(arg instanceof Handle handle) || !handle.getOwner().equals(mixin.name)) continue;
					if (ownFields.contains(handle.getName() + ":" + handle.getDesc())) fields.add(handle.getName() + ":" + handle.getDesc());
					if (ownMethods.get(handle.getName() + handle.getDesc()) instanceof MethodNode callee) {
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
			boolean selfContained, boolean addsFields, Set<String> stateless, List<PreflightFinding> out, String owner) {
		String resolve(String selector) {
			return refmap.resolve(mixinDotted.replace('.', '/'), selector);
		}

		Context withOwner(String owner) {
			return new Context(configName, mixinDotted, config, refmap, hasPlugin, selfContained, addsFields, stateless, out, owner);
		}

		/** What changed the target since the mod was built: NeoForge or this Minecraft version, or this version of the mod. */
		String changer(boolean sentenceStart) {
			if (owner == null) return "NeoForge or this Minecraft version";
			return (sentenceStart ? "This" : "this") + " version of " + owner;
		}

		/** Whose code the target is: NeoForge's (and Minecraft's), or the installed mod's. */
		String platform() {
			return owner == null ? "NeoForge" : owner;
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
					ctx.classLevel("Target class " + target.replace('/', '.') + " does not exist in this Minecraft/NeoForge version",
							"The mod was likely built for a different Minecraft version, or NeoForge removed this class.");
				} else if (isGame) {
					ctx.out().add(new PreflightFinding(Severity.WARN, ctx.configName(), ctx.mixinDotted(), "Target class " + target.replace('/', '.')
							+ " does not exist in this Minecraft/NeoForge version (or on this side); Mixin skips it", ""));
				} else if (isUntransformable(target)) {
					ctx.classLevel("Target class " + target.replace('/', '.') + " is a library class that NeoForge loads without mixin support, so this mixin would silently never apply",
							"Ask the mod author for a NeoForge version, or skip this mixin in config/fabricated.json if the feature is optional.");
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
	 * finds nothing but notes in the mixin against it: its selectors, shadows and injection points are all there. Only a
	 * single fitting class is taken. Returns a description of each move.
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
			ClassNode outer = gameTarget(target.substring(0, dollar));
			if (outer == null) continue;
			ClassNode match = null;
			for (InnerClassNode inner : outer.innerClasses) {
				// Anonymous classes have neither a simple name nor an outer class in the attribute.
				if (inner.innerName != null || inner.outerName != null || inner.name.equals(target)
						|| !inner.name.startsWith(outer.name + "$") || inner.name.indexOf('$', outer.name.length() + 1) >= 0) continue;
				ClassNode candidate = gameTarget(inner.name);
				if (candidate == null || !fits(mixin, candidate, ctx)) continue;
				if (match != null) {
					match = null;
					break;
				}
				match = candidate;
			}
			if (match == null) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) names;
			values.set(i, raw.indexOf('/') >= 0 ? match.name : match.name.replace('/', '.'));
			moved.add(target.replace('/', '.') + " doesn't fit the mixin in this game build, whose anonymous classes are numbered differently; "
					+ match.name.replace('/', '.') + " does, and the mixin is pointed there");
		}
		return moved;
	}

	/** Whether pre-flight finds nothing but notes in {@code mixin} against {@code target}. */
	private boolean fits(ClassNode mixin, ClassNode target, Context ctx) {
		List<PreflightFinding> found = new ArrayList<>();
		checkMembers(mixin, target, new Context(ctx.configName(), ctx.mixinDotted(), ctx.config(), ctx.refmap(), false, false, false, Set.of(), found, null));
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

			boolean injector = false;
			for (AnnotationNode ann : Annotations.all(method)) {
				if (isInjector(ann.desc)) {
					injector = true;
					checkInjector(mixin, method, ann, target, ctx);
				}
			}
			if (!injector) checkMerged(method, target, ctx);
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

	/** {@code getFooBar} -> {@code fooBar} for the first matching prefix followed by an upper-case letter. */
	private static String nameAfterPrefix(String method, String... prefixes) {
		for (String prefix : prefixes) {
			if (method.length() > prefix.length() && method.startsWith(prefix) && Character.isUpperCase(method.charAt(prefix.length()))) {
				return decapitalize(method.substring(prefix.length()));
			}
		}
		return null;
	}

	private static String decapitalize(String s) {
		// Mixin keeps all-caps names (e.g. "getURL" -> "URL") as they are.
		if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) return s;
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
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
				matched.add(m);
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
							+ " does not match the parameters of " + matched.getFirst().name + matched.getFirst().desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? "NeoForge's build of Minecraft declares this method with different parameters (often a lambda whose captured values are ordered differently)"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(WRAP_METHOD) && matched.stream().allMatch(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0)) {
			// MixinExtras throws while preparing the injector, which require doesn't cover (a config that isn't required survives it).
			ctx.injector(handler, ctx.config().required() ? 1 : 0, injectorName + " targets " + matched.getFirst().name + matched.getFirst().desc
							+ ", which is abstract in " + target.name.replace('/', '.'),
					"Mixin picks the first method of that name; the mod needs an update or this mixin must be skipped.");
			return;
		}
		if (ann.desc.equals(WRAP_METHOD) && !anyFuzzy && !acceptsWrapperSignature(handler, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.getFirst().name + matched.getFirst().desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? "NeoForge's build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MODIFY_VARIABLE) && !anyFuzzy && !acceptsModifierSignature(handler, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.getFirst().name + matched.getFirst().desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? "NeoForge's build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
			return;
		}

		if (ann.desc.equals(MODIFY_CONSTANT)) {
			for (AnnotationNode constant : Annotations.list(Annotations.value(ann, "constant"))) {
				String expected = InjectionPoints.constantDescription(constant);
				if (expected == null) continue;
				if (matched.stream().allMatch(m -> InjectionPoints.constantVerdict(constant, m) == Verdict.MISSING)) {
					ctx.injector(handler, require, injectorName + " in " + matched.getFirst().name + " expects the constant " + expected
									+ ", which does not occur in that method",
							ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
					return;
				}
			}
		}

		String missingLocal = missingLocal(handler, matched);
		if (missingLocal != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable " + missingLocal + ", which "
							+ matched.getFirst().name + " in " + target.name.replace('/', '.') + " does not declare",
					ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
			return;
		}
		String ambiguous = slices.isEmpty() ? ambiguousLocal(handler, ann, matched, ctx) : null;
		if (ambiguous != null) {
			ctx.injector(handler, require, injectorName + " handler " + handler.name + " captures the local variable " + ambiguous
							+ " by its type, which " + matched.getFirst().name + " in " + target.name.replace('/', '.') + " has more than one of there",
					ctx.changer(true) + " added a local of that type; the mod needs an update or this mixin must be skipped.");
			return;
		}

		// Mixin counts an injector's injections in all its target methods against require: a method that lost its point
		// can leave too few, though the point is found in another (FrozenLib's, where NeoForge moved a call into a lambda,
		// REQ-1). Counted only where that can happen and every target is known.
		int injections = require > 1 && !anyFuzzy ? 0 : -1;
		List<String> without = new ArrayList<>();
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
				if (injections >= 0) {
					int n = expression ? -1 : InjectionPoints.count(point, resolved, ordinal, opcode, range, m);
					if (n == 0) without.add(m.name);
					injections = n < 0 ? -1 : injections + n;
				}
				if (v == Verdict.FOUND) verdict = Verdict.FOUND;
				else if (v == Verdict.MISSING && verdict == Verdict.UNKNOWN) verdict = Verdict.MISSING;
			}
			if (verdict == Verdict.MISSING) {
				ctx.injector(handler, require, injectorName + " in " + matched.getFirst().name + " expects @At(\"" + point + "\""
								+ (rawTarget != null ? ", target=\"" + ctx.resolve(rawTarget) + "\"" : "")
								+ (ordinal >= 0 ? ", ordinal=" + ordinal : "") + "), which does not occur in that method",
						ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
				return;
			}
			if (verdict == Verdict.FOUND && ann.desc.equals(WRAP_OPERATION) && rawTarget != null) {
				String mismatch = InjectionPoints.wrappedArityMismatch(handler, point, ctx.resolve(rawTarget), matched);
				if (mismatch != null) {
					ctx.classLevel(injectorName + " handler " + handler.name + " " + mismatch + " in " + matched.getFirst().name,
							(ctx.owner() == null ? "NeoForge's build of Minecraft" : ctx.changer(true)) + " calls this with different arguments; the mod needs an update or this handler must be skipped.");
					return;
				}
			}
		}
		if (injections >= 0 && injections < require) {
			ctx.injector(handler, require, injectorName + " " + handler.name + " applies " + injections + " time(s) where it requires " + require
							+ ": its point does not occur in " + String.join(", ", new TreeSet<>(without)) + " in " + target.name.replace('/', '.'),
					ctx.changer(true) + " changed " + (without.size() == 1 ? "that method" : "those methods") + "; the mod needs an update or this mixin must be skipped.");
		}
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
		if (args.isEmpty() || !isCallbackInfo(args.getLast())) return true; // unusual shape: leave it to Mixin
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
		if (args == null || args.isEmpty() || !args.getLast().getDescriptor().equals(OPERATION)) return true;
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
					return String.valueOf(names.getFirst());
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
		AnnotationNode at = ats.getFirst();
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
