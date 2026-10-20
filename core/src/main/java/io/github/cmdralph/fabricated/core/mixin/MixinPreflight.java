package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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
 * disabled. An enum mixin adding constants to one of NeoForge's extensible enums always fails: the mod's own classes
 * read those constants.
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
	private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
	private static final String INTRINSIC = "Lorg/spongepowered/asm/mixin/Intrinsic;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
	private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
	private static final Set<String> WRAP_WITH_CONDITION = Set.of("Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");
	private static final String MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
	/** NeoForge's marker for the enums only its own enum extension system may extend. */
	private static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";
	private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
	private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
	private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
	private static final String DEFINITION = "Lcom/llamalad7/mixinextras/expression/Definition;";
	private static final String DEFINITIONS = "Lcom/llamalad7/mixinextras/expression/Definitions;";
	/** The name prefix javac gives the fields holding the local variables an anonymous or local class captures. */
	private static final String CAPTURED_PREFIX = "val$";
	/** javac's name for a lambda body: the enclosing method's name and the lambda's index in the class. */
	private static final Pattern LAMBDA = Pattern.compile("lambda\\$(.+)\\$\\d+");
	private static final Set<String> GAME_PREFIXES = Set.of("net/minecraft/", "com/mojang/blaze3d/");

	private final ClassIndex game;
	private final InstalledClasses installed;
	private final ClassBytesSource modClasses;
	private final ClassBytesSource untransformable;
	private final boolean clientSide;
	private final boolean missingTargetsFatal;
	private final Map<String, Optional<ClassNode>> targetCache = new HashMap<>();
	/** The installed classes the checks read, with their fingerprints. */
	private final Map<String, String> installedRead = new TreeMap<>();
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
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, hasPlugin, out, null);
			ClassNode node = modClasses.readNode(internal, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			if (node == null) {
				ctx.classLevel("Mixin class " + ctx.mixinDotted() + " listed in " + configName + " is missing from the mod jar",
						"The mod jar is incomplete or corrupted; re-download it.");
				continue;
			}
			checkMixin(node, ctx);
		}
		if (out.stream().anyMatch(f -> f.severity() == Severity.LEFT_OUT)) {
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
	 * Mixins that Mixin would reject but that can be pointed at what the game really has, before pre-flight checks them.
	 * Returns the mixin classes rewritten, by class file name; each repair is reported as a warning.
	 * <ul>
	 * <li>An accessor or invoker that every target already declares publicly, with the same descriptor (typically a
	 * method NeoForge added): Mixin cannot generate it, but the existing method does the job, so it becomes a
	 * synthetic {@code @Shadow} of it. (Synthetic, because Mixin treats an interface with any other non-accessor
	 * method as an interface mixin, which may only target interfaces.)</li>
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
	 * item stack along that way): the injection point is pointed at that call, with a bridge for a handler that takes
	 * the call's arguments.</li>
	 * <li>An {@code @Inject} that captures local variables and would crash when they differ
	 * ({@code CAPTURE_FAILHARD}, {@code CAPTURE_FAILEXCEPTION}): NeoForge's patches often add locals, so it is made to
	 * skip itself with Mixin's warning instead ({@code CAPTURE_FAILSOFT}, {@code require = 0}). Where the locals
	 * match, nothing changes.</li>
	 * <li>An injector targeting a lambda ({@code lambda$enclosing$3}) that the game numbers differently (NeoForge's
	 * 1.21.11 build is compiled from decompiled code, and NeoForge's patches add lambdas): it is pointed at the one
	 * lambda of the same enclosing method with the same descriptor.</li>
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
			Context ctx = new Context(configName, internal.replace('/', '.'), config, refmap, false, out, null);
			List<ClassNode> targets = new ArrayList<>();
			for (String target : targetsOf(node, ctx)) {
				ClassNode targetNode = gameTarget(target);
				if (targetNode == null) {
					targets = List.of();
					break;
				}
				targets.add(targetNode);
			}
			if (targets.isEmpty()) continue;
			boolean changed = false;
			List<MethodNode> bridges = new ArrayList<>();
			for (MethodNode method : node.methods) {
				for (AnnotationNode ann : allAnnotations(method)) {
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
						for (String moved : subtypeCallRepairs(ann, targets.getFirst(), ctx)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
						for (String moved : insertedArgumentRepairs(node, method, ann, targets.getFirst(), ctx, bridges)) {
							changed = true;
							out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@" + simpleName(ann.desc) + " " + method.name
									+ " expects a call to " + moved, ""));
						}
					}
				}
				if ((node.access & Opcodes.ACC_INTERFACE) == 0 || (method.access & Opcodes.ACC_STATIC) != 0) continue;
				AnnotationNode accessor = Annotations.find(method, ACCESSOR);
				String kind = accessor != null ? "@Accessor" : Annotations.find(method, INVOKER) != null ? "@Invoker" : null;
				if (kind == null) continue;
				if (targets.stream().allMatch(t -> declaresPublicInstance(t, method.name, method.desc))) {
					if (method.visibleAnnotations != null) method.visibleAnnotations.removeIf(MixinPreflight::isAccessorAnnotation);
					if (method.invisibleAnnotations != null) method.invisibleAnnotations.removeIf(MixinPreflight::isAccessorAnnotation);
					if (method.visibleAnnotations == null) method.visibleAnnotations = new ArrayList<>();
					method.visibleAnnotations.add(new AnnotationNode(SHADOW));
					method.access |= Opcodes.ACC_SYNTHETIC;
					changed = true;
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), kind + " " + method.name + method.desc
							+ " already exists publicly in " + targets.getFirst().name.replace('/', '.')
							+ " (added by NeoForge or this Minecraft version); the mixin uses that method instead of generating one", ""));
				} else if (accessor != null) {
					String field = capturedFieldRepair(method, accessor, targets, ctx);
					if (field == null) continue;
					changed = true;
					out.add(new PreflightFinding(Severity.WARN, configName, ctx.mixinDotted(), "@Accessor " + method.name + method.desc
							+ " targets the captured variable field " + field + " in " + targets.getFirst().name.replace('/', '.')
							+ ", which this game build names differently", ""));
				}
			}
			node.methods.addAll(bridges);
			if (!changed) continue;
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			repaired.put(internal + ".class", writer.toByteArray());
		}
		return repaired;
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
	 * code), at that overload; returns a description of each move. Only handlers that don't depend on the method's own
	 * parameters or locals are moved: not an {@code @Inject} taking them, nor one capturing locals (the overload's
	 * differ), except a {@code @Local(argsOnly = true)} of a type exactly one of the overload's parameters has.
	 */
	private static List<String> overloadRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<AnnotationNode> ats = annotationList(Annotations.value(injector, "at"));
		if (ats.isEmpty() || injector.desc.equals(MODIFY_VARIABLE) || Annotations.value(injector, "locals") != null) return List.of();
		// An @Inject handler takes the callback info only, or (bridged below) the target's parameters before it.
		boolean takesParameters = false;
		if (injector.desc.equals(INJECT)) {
			List<Type> args = handlerArgs(handler);
			if (args == null || args.isEmpty() || !isCallbackInfo(args.getLast())) return List.of();
			takesParameters = args.size() > 1;
		}
		List<AnnotationNode> slices = annotationList(Annotations.value(injector, "slice"));
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
			if (pointsIn(ats, slices, chosen, ctx) == Verdict.MISSING) {
				if (takesParameters) continue;
				overload = calledOverload(chosen, target);
				if (overload == null || (overload.access & Opcodes.ACC_STATIC) != (chosen.access & Opcodes.ACC_STATIC)
						|| !Type.getReturnType(overload.desc).equals(Type.getReturnType(chosen.desc))
						|| !localsFit(handler, overload) || pointsIn(ats, slices, overload, ctx) != Verdict.FOUND) continue;
				how = ", whose code NeoForge moved into ";
			} else {
				// The injection points exist, but in a constructor nothing calls any more: its end is the end of the one it calls.
				overload = keptConstructorDelegate(chosen, target);
				if (overload == null || !slices.isEmpty() || !atEndOnly(ats) || !localsFit(handler, overload)) continue;
				if (takesParameters) {
					// The handler gets the constructor's own parameters from a bridge taking all of the other one's.
					if (selectors.size() != 1 || (mixin.access & Opcodes.ACC_INTERFACE) != 0 || !forwardsItsParameters(chosen, overload)
							|| !takesExactly(handler, chosen)) continue;
					bridges.add(bridge(mixin.name, handler, injector, chosen, overload, freeName(mixin, bridges, handler.name + "$fabricated")));
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
		for (AnnotationNode at : annotationList(Annotations.value(injector, "at"))) {
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
	 * Points the {@code INVOKE} points of an injector at the call this game build makes instead of the named one, an
	 * overload of the same method taking one more argument (NeoForge's patches pass an item stack or a level along that
	 * way), when the named call is in none of the methods the injector targets and that overload is the only one there.
	 * The handler of an {@code @Inject} or {@code @ModifyExpressionValue} doesn't see the arguments. One of a
	 * {@code @Redirect}, {@code @WrapWithCondition} or {@code @WrapOperation} that takes exactly the call's receiver and
	 * arguments (and the {@code Operation}) gets a bridge taking the overload's, which calls it without the added
	 * argument; for {@code @WrapOperation}, with an {@code Operation} that puts it back when the original call is made.
	 * Returns a description of each move.
	 */
	private static List<String> insertedArgumentRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		// Only the point moves for these: their handlers don't see the call's arguments.
		boolean pointOnly = injector.desc.equals(INJECT) || injector.desc.equals(MODIFY_EXPRESSION_VALUE);
		boolean condition = WRAP_WITH_CONDITION.contains(injector.desc);
		if (!pointOnly && !condition && !injector.desc.equals(REDIRECT) && !injector.desc.equals(WRAP_OPERATION) || Annotations.value(injector, "slice") != null
				|| (mixin.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
		List<MethodNode> methods = selectedMethods(injector, target, ctx);
		List<AnnotationNode> ats = annotationList(Annotations.value(injector, "at"));
		if (methods.isEmpty() || !pointOnly && ats.size() != 1) return List.of();
		List<String> moved = new ArrayList<>();
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (!point.equals("INVOKE") || !(Annotations.value(at, "target") instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.owner() == null || ref.name() == null || ref.desc() == null) continue;
			String overload = null;
			boolean named = false;
			boolean ambiguous = false;
			boolean staticCall = false;
			int calls = 0;
			for (MethodNode method : methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(ref.owner()) || !call.name.equals(ref.name())) continue;
					if (call.desc.equals(ref.desc())) {
						named = true;
					} else if (insertedArgument(ref.desc(), call.desc) >= 0) {
						ambiguous |= overload != null && !overload.equals(call.desc);
						overload = call.desc;
						staticCall = call.getOpcode() == Opcodes.INVOKESTATIC;
						calls++;
					}
				}
			}
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (named || overload == null || ambiguous || ordinal >= calls) continue;
			int inserted = insertedArgument(ref.desc(), overload);
			if (!pointOnly) {
				Type owner = Type.getObjectType(ref.owner());
				MethodNode bridge = injector.desc.equals(WRAP_OPERATION) ? wrapperBridge(mixin, handler, owner, ref.desc(), overload, inserted, staticCall, bridges)
						: callBridge(mixin, handler, owner, ref.desc(), overload, inserted, staticCall, condition ? Type.BOOLEAN_TYPE : Type.getReturnType(ref.desc()),
								bridges);
				if (bridge == null) continue;
				moveInjector(handler, injector, bridge);
				bridges.add(bridge);
			}
			for (int i = 0; i + 1 < at.values.size(); i += 2) {
				if ("target".equals(at.values.get(i))) at.values.set(i + 1, "L" + ref.owner() + ";" + ref.name() + overload);
			}
			moved.add(ref.owner().replace('/', '.') + "." + ref.name() + ref.desc() + ", which this game build makes through an overload with one more argument, "
					+ ref.name() + overload + "; the injection point is pointed there" + (pointOnly ? "" : ", through a bridge handler"));
		}
		return moved;
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

	/** The parameters of a handler that takes a call's receiver (unless static) and arguments. */
	private static List<Type> callParameters(Type owner, String desc, boolean staticCall) {
		List<Type> params = new ArrayList<>();
		if (!staticCall) params.add(owner);
		params.addAll(List.of(Type.getArgumentTypes(desc)));
		return params;
	}

	/** {@code base}, numbered when the mixin already has a method of that name: two handlers may share theirs. */
	private static String freeName(ClassNode mixin, List<MethodNode> added, String base) {
		String name = base;
		for (int i = 2; nameTaken(mixin, added, name); i++) name = base + i;
		return name;
	}

	private static boolean nameTaken(ClassNode mixin, List<MethodNode> added, String name) {
		for (MethodNode m : mixin.methods) {
			if (m.name.equals(name)) return true;
		}
		for (MethodNode m : added) {
			if (m.name.equals(name)) return true;
		}
		return false;
	}

	/**
	 * A {@code @Redirect} or {@code @WrapWithCondition} handler for {@code overload}'s call: it calls {@code handler},
	 * written for {@code desc}'s, without the added argument. Null when {@code handler} takes anything but that call's
	 * receiver ({@code owner}) and arguments (a {@code @Coerce}d supertype included: the bridge wouldn't carry the
	 * annotation), or doesn't return {@code result}.
	 */
	private static MethodNode callBridge(ClassNode mixin, MethodNode handler, Type owner, String desc, String overload, int inserted, boolean staticCall,
			Type result, List<MethodNode> added) {
		if (!List.of(Type.getArgumentTypes(handler.desc)).equals(callParameters(owner, desc, staticCall))
				|| !Type.getReturnType(handler.desc).equals(result)) return null;
		List<Type> params = callParameters(owner, overload, staticCall);
		MethodNode bridge = bridgeMethod(handler, freeName(mixin, added, handler.name + "$fabricated"), params, Type.getReturnType(handler.desc));
		int skip = inserted + (staticCall ? 0 : 1);
		int stack = loadAllBut(bridge, handler, params, skip);
		callHandler(bridge, mixin.name, handler, stack);
		return bridge;
	}

	/**
	 * A {@code @WrapOperation} handler for {@code overload}'s call: it calls {@code handler}, written for {@code desc}'s,
	 * without the added argument, and with an {@code Operation} that puts it back before making the call (a lambda the
	 * bridge adds to {@code extra}). Null when {@code handler} takes anything but that call's receiver ({@code owner})
	 * and arguments and the {@code Operation}.
	 */
	private static MethodNode wrapperBridge(ClassNode mixin, MethodNode handler, Type owner, String desc, String overload, int inserted, boolean staticCall,
			List<MethodNode> extra) {
		Type operation = Type.getType(OPERATION);
		List<Type> expected = callParameters(owner, desc, staticCall);
		expected.add(operation);
		if (!List.of(Type.getArgumentTypes(handler.desc)).equals(expected)) return null;
		List<Type> params = callParameters(owner, overload, staticCall);
		int skip = inserted + (staticCall ? 0 : 1);
		Type added = params.get(skip);
		params.add(operation);
		MethodNode bridge = bridgeMethod(handler, freeName(mixin, extra, handler.name + "$fabricated"), params, Type.getReturnType(handler.desc));
		int stack = loadAllBut(bridge, handler, params.subList(0, params.size() - 1), skip);
		// The Operation the handler gets: the original one, with the added argument put back.
		MethodNode lambda = argumentInserter(freeName(mixin, extra, "lambda$" + handler.name + "$fabricated"), added, skip);
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, slotOf(handler, params, params.size() - 1)));
		bridge.instructions.add(new VarInsnNode(added.getOpcode(Opcodes.ILOAD), slotOf(handler, params, skip)));
		Type objects = Type.getType("([Ljava/lang/Object;)Ljava/lang/Object;");
		bridge.instructions.add(new InvokeDynamicInsnNode("call", Type.getMethodDescriptor(operation, operation, added),
				new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
						"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
								+ "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				objects, new Handle(Opcodes.H_INVOKESTATIC, mixin.name, lambda.name, lambda.desc, false), objects));
		callHandler(bridge, mixin.name, handler, stack + 1 + added.getSize());
		extra.add(lambda);
		return bridge;
	}

	/** {@code (Operation original, T value, Object[] args) -> original.call(args with value inserted at index)}. */
	private static MethodNode argumentInserter(String name, Type value, int index) {
		Type operation = Type.getType(OPERATION);
		MethodNode lambda = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name,
				Type.getMethodDescriptor(Type.getType(Object.class), operation, value, Type.getType(Object[].class)), null, null);
		int args = 1 + value.getSize();
		int all = args + 1;
		InsnList code = lambda.instructions;
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new InsnNode(Opcodes.ARRAYLENGTH));
		code.add(new InsnNode(Opcodes.ICONST_1));
		code.add(new InsnNode(Opcodes.IADD));
		code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
		code.add(new VarInsnNode(Opcodes.ASTORE, all));
		// System.arraycopy(args, 0, all, 0, index)
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new InsnNode(Opcodes.ICONST_0));
		code.add(new LdcInsnNode(index));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false));
		// all[index] = value
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new LdcInsnNode(index));
		code.add(new VarInsnNode(value.getOpcode(Opcodes.ILOAD), 1));
		box(code, value);
		code.add(new InsnNode(Opcodes.AASTORE));
		// System.arraycopy(args, index, all, index + 1, args.length - index)
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new LdcInsnNode(index));
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new LdcInsnNode(index + 1));
		code.add(new VarInsnNode(Opcodes.ALOAD, args));
		code.add(new InsnNode(Opcodes.ARRAYLENGTH));
		code.add(new LdcInsnNode(index));
		code.add(new InsnNode(Opcodes.ISUB));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false));
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new VarInsnNode(Opcodes.ALOAD, all));
		code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, operation.getInternalName(), "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
		code.add(new InsnNode(Opcodes.ARETURN));
		lambda.maxStack = 6;
		lambda.maxLocals = all + 1;
		return lambda;
	}

	private static void box(InsnList code, Type type) {
		String boxed = switch (type.getSort()) {
			case Type.BOOLEAN -> "java/lang/Boolean";
			case Type.CHAR -> "java/lang/Character";
			case Type.BYTE -> "java/lang/Byte";
			case Type.SHORT -> "java/lang/Short";
			case Type.INT -> "java/lang/Integer";
			case Type.FLOAT -> "java/lang/Float";
			case Type.LONG -> "java/lang/Long";
			case Type.DOUBLE -> "java/lang/Double";
			default -> null;
		};
		if (boxed != null) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, boxed, "valueOf", "(" + type.getDescriptor() + ")L" + boxed + ";", false));
	}

	/** An empty bridge with {@code handler}'s access (static or not) and the given signature. */
	private static MethodNode bridgeMethod(MethodNode handler, String name, List<Type> params, Type result) {
		return new MethodNode(Opcodes.ACC_PRIVATE | (handler.access & Opcodes.ACC_STATIC), name,
				Type.getMethodDescriptor(result, params.toArray(Type[]::new)), null, null);
	}

	/** The local variable slot of the bridge's parameter at {@code index}. */
	private static int slotOf(MethodNode handler, List<Type> params, int index) {
		int slot = (handler.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (int i = 0; i < index; i++) slot += params.get(i).getSize();
		return slot;
	}

	/** Loads {@code this} (unless static) and each of {@code params} but the one at {@code skip}; returns the stack size. */
	private static int loadAllBut(MethodNode bridge, MethodNode handler, List<Type> params, int skip) {
		int stack = 0;
		if ((handler.access & Opcodes.ACC_STATIC) == 0) {
			bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			stack++;
		}
		for (int i = 0; i < params.size(); i++) {
			if (i == skip) continue;
			bridge.instructions.add(new VarInsnNode(params.get(i).getOpcode(Opcodes.ILOAD), slotOf(handler, params, i)));
			stack += params.get(i).getSize();
		}
		return stack;
	}

	/** Calls {@code handler} with what is on the stack and returns its result. */
	private static void callHandler(MethodNode bridge, String mixin, MethodNode handler, int stack) {
		boolean isStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		bridge.instructions.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, mixin, handler.name, handler.desc, false));
		bridge.instructions.add(new InsnNode(Type.getReturnType(handler.desc).getOpcode(Opcodes.IRETURN)));
		bridge.maxStack = Math.max(stack, Type.getReturnType(handler.desc).getSize());
		bridge.maxLocals = slotOf(handler, List.of(Type.getArgumentTypes(bridge.desc)), Type.getArgumentTypes(bridge.desc).length);
	}

	/**
	 * Moves the injector annotation from {@code handler} to {@code bridge}; {@code handler} stays a plain method of the
	 * mixin, private (Mixin takes no other static methods) and {@code @Unique}, so that Mixin renames it rather than
	 * merging it over a method of the same name another mixin added (as an injector, Mixin gave it a name of its own).
	 */
	private static void moveInjector(MethodNode handler, AnnotationNode injector, MethodNode bridge) {
		handler.access = handler.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PRIVATE;
		if (Annotations.find(handler, UNIQUE) == null) {
			if (handler.visibleAnnotations == null) handler.visibleAnnotations = new ArrayList<>();
			handler.visibleAnnotations.add(new AnnotationNode(UNIQUE));
		}
		if (handler.visibleAnnotations != null && handler.visibleAnnotations.remove(injector)) {
			bridge.visibleAnnotations = new ArrayList<>(List.of(injector));
		} else if (handler.invisibleAnnotations != null && handler.invisibleAnnotations.remove(injector)) {
			bridge.invisibleAnnotations = new ArrayList<>(List.of(injector));
		}
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
			if (!handlerTypes[i].equals(params[i]) || !parameterAnnotations(handler, i).isEmpty()) return false;
		}
		return true;
	}

	/**
	 * A handler for {@code injector} at {@code canonical} instead of {@code delegate}: it takes {@code canonical}'s
	 * parameters and the callback info, and calls {@code handler} with {@code delegate}'s (the same values, see
	 * {@link #forwardsItsParameters}) and the callback info. The injector annotation moves to it; {@code handler} stays a
	 * plain method of the mixin.
	 */
	private static MethodNode bridge(String mixin, MethodNode handler, AnnotationNode injector, MethodNode delegate, MethodNode canonical, String name) {
		Type[] own = Type.getArgumentTypes(delegate.desc);
		Type[] all = Type.getArgumentTypes(canonical.desc);
		Type callbackInfo = Type.getArgumentTypes(handler.desc)[own.length];
		Type[] params = Arrays.copyOf(all, all.length + 1);
		params[all.length] = callbackInfo;
		boolean isStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE | (handler.access & Opcodes.ACC_STATIC), name, Type.getMethodDescriptor(Type.VOID_TYPE, params),
				null, null);
		int slot = 0;
		if (!isStatic) bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, slot++));
		int[] slots = new int[params.length];
		for (int i = 0; i < params.length; i++) {
			slots[i] = slot;
			slot += params[i].getSize();
		}
		int stack = isStatic ? 0 : 1;
		for (int i = 0; i < own.length; i++) {
			bridge.instructions.add(new VarInsnNode(own[i].getOpcode(Opcodes.ILOAD), slots[i]));
			stack += own[i].getSize();
		}
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, slots[params.length - 1]));
		bridge.instructions.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, mixin, handler.name, handler.desc, false));
		bridge.instructions.add(new InsnNode(Opcodes.RETURN));
		bridge.maxStack = stack + 1;
		bridge.maxLocals = slot;
		moveInjector(handler, injector, bridge);
		return bridge;
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

	/** A {@code @Constant}'s value as the source would write it, or null when pre-flight can't judge it. */
	private static String constantDescription(AnnotationNode constant) {
		if (Annotations.value(constant, "expandZeroConditions") != null) return null;
		for (String key : List.of("intValue", "floatValue", "longValue", "doubleValue", "stringValue", "classValue")) {
			Object value = Annotations.value(constant, key);
			if (value instanceof Type type) return type.getClassName() + ".class";
			if (value instanceof String string) return "\"" + string + "\"";
			if (value != null) return value.toString();
		}
		return Boolean.TRUE.equals(Annotations.value(constant, "nullValue")) ? "null" : null;
	}

	/** Whether a {@code @Constant} (with its ordinal) occurs in {@code method}, as Mixin matches constants. */
	private static Verdict constantVerdict(AnnotationNode constant, MethodNode method) {
		Object wanted = null;
		for (String key : List.of("intValue", "floatValue", "longValue", "doubleValue", "stringValue", "classValue")) {
			if (Annotations.value(constant, key) instanceof Object v) wanted = v;
		}
		boolean nullValue = Boolean.TRUE.equals(Annotations.value(constant, "nullValue"));
		int count = 0;
		for (AbstractInsnNode insn : method.instructions) {
			Object value = constantOf(insn);
			if (nullValue ? insn.getOpcode() == Opcodes.ACONST_NULL : value != null && value.equals(wanted)) count++;
		}
		int ordinal = Annotations.value(constant, "ordinal") instanceof Integer n ? n : -1;
		if (count == 0) return Verdict.MISSING;
		if (Annotations.value(constant, "slice") != null) return Verdict.UNKNOWN;
		return ordinal >= count ? Verdict.MISSING : Verdict.FOUND;
	}

	/** The constant an instruction pushes (boxed as {@code @Constant} stores it), or null. */
	private static Object constantOf(AbstractInsnNode insn) {
		int op = insn.getOpcode();
		if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
		if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) return (long) (op - Opcodes.LCONST_0);
		if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) return (float) (op - Opcodes.FCONST_0);
		if (op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) return (double) (op - Opcodes.DCONST_0);
		if (insn instanceof IntInsnNode push && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) return push.operand;
		if (insn instanceof LdcInsnNode ldc) return ldc.cst;
		return null;
	}

	/** Whether every injection point occurs in {@code method} (FOUND), one surely doesn't (MISSING), or it can't be told. */
	private static Verdict pointsIn(List<AnnotationNode> ats, List<AnnotationNode> slices, MethodNode method, Context ctx) {
		Verdict result = Verdict.FOUND;
		for (AnnotationNode at : ats) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (point.equals("MIXINEXTRAS:EXPRESSION")) return Verdict.UNKNOWN;
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer n ? n : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer n ? n : -1;
			AnnotationNode slice = sliceFor(at, slices);
			Range range = slice == null ? Range.ALL : sliceRange(slice, method, ctx);
			Verdict v = evaluate(point, rawTarget == null ? null : ctx.resolve(rawTarget), ordinal, opcode, range, method);
			if (v == Verdict.MISSING) return Verdict.MISSING;
			if (v == Verdict.UNKNOWN) result = Verdict.UNKNOWN;
		}
		return result;
	}

	/**
	 * Whether the handler's {@code @Local}s still find what they captured in {@code method}: each must be an
	 * {@code @Local(argsOnly = true)} of a type exactly one of {@code method}'s parameters has (other locals differ).
	 */
	private static boolean localsFit(MethodNode handler, MethodNode method) {
		Type[] types = Type.getArgumentTypes(handler.desc);
		List<Type> params = List.of(Type.getArgumentTypes(method.desc));
		for (int i = 0; i < types.length; i++) {
			for (AnnotationNode a : parameterAnnotations(handler, i)) {
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

	private static boolean isAccessorAnnotation(AnnotationNode annotation) {
		return annotation.desc.equals(ACCESSOR) || annotation.desc.equals(INVOKER);
	}

	private static boolean declaresPublicInstance(ClassNode target, String name, String desc) {
		MethodNode m = declared(target, name, desc);
		return m != null && (m.access & Opcodes.ACC_PUBLIC) != 0 && (m.access & Opcodes.ACC_STATIC) == 0;
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

	/** @param owner the installed mod the target belongs to, or {@code null} for a game class */
	private record Context(String configName, String mixinDotted, MixinConfigFile config, Refmap refmap, boolean hasPlugin,
			List<PreflightFinding> out, String owner) {
		String resolve(String selector) {
			return refmap.resolve(mixinDotted.replace('.', '/'), selector);
		}

		Context withOwner(String owner) {
			return new Context(configName, mixinDotted, config, refmap, hasPlugin, out, owner);
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

		/** A problem that makes Mixin reject the whole mixin; fatal when the config is required. */
		void classLevel(String message, String suggestion) {
			out.add(new PreflightFinding(config.required() ? fatal() : Severity.WARN, configName, mixinDotted, message, suggestion));
		}

		/**
		 * A mixin the mod's own code can't do without (the enum constants it adds, which the mod's classes read): fatal
		 * even where a plugin or an optional config would only leave it out, which would move the crash into the mod.
		 */
		void essential(String message, String suggestion) {
			out.add(new PreflightFinding(Severity.FAIL, configName, mixinDotted, message, suggestion));
		}

		void injector(MethodNode handler, int require, String message, String suggestion) {
			out.add(new PreflightFinding(require > 0 ? fatal() : Severity.WARN, configName, mixinDotted, message, suggestion,
					handler.name + handler.desc));
		}

		/** A config plugin may disable the mixin at runtime, so a crash only leaves the mixin out. */
		private Severity fatal() {
			return hasPlugin ? Severity.LEFT_OUT : Severity.FAIL;
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
			if ((mixin.access & Opcodes.ACC_ENUM) != 0 && targetNode.interfaces.contains(EXTENSIBLE_ENUM)) {
				targetCtx.essential("Enum mixin adds constants to " + target.replace('/', '.') + ", one of NeoForge's extensible enums, which"
						+ " NeoForge lets only its own enum extensions change", "Install the NeoForge build of the mod, if it has one.");
				continue;
			}
			checkMembers(mixin, targetNode, targetCtx);
		}
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
		String targetDotted = target.name.replace('/', '.');
		for (FieldNode field : mixin.fields) {
			AnnotationNode shadow = Annotations.find(field, SHADOW);
			if (shadow == null) continue;
			String name = ctx.resolve(field.name);
			MemberRef ref = MemberRef.parse(name);
			String fieldName = ref.nameOr(field.name);
			String desc = ref.fieldDescOr(field.desc);
			if (game.resolveField(target.name, fieldName + ":" + desc) == ClassIndex.Resolution.MISSING
					&& !aliasExists(shadow, target, desc, true)) {
				if (addedByAMixin(fieldName)) {
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
					if (addedByAMixin(methodName)) {
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
			for (AnnotationNode ann : allAnnotations(method)) {
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
		ctx.classLevel(nameExists
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
		ctx.classLevel("@Invoker target method " + methodName + desc + " does not exist in " + target.name.replace('/', '.'),
				"The method was renamed or removed by " + ctx.changer(false) + ".");
	}

	static String inferAccessorName(String method) {
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

	private static void checkInjector(ClassNode mixin, MethodNode handler, AnnotationNode ann, ClassNode target, Context ctx) {
		if (!(Annotations.value(ann, "method") instanceof List<?> selectors) || selectors.isEmpty()) return;
		// As Mixin: a grouped injector requires nothing by default, its group's minimum being the check.
		int require = Annotations.value(ann, "require") instanceof Integer r && r >= 0 ? r : grouped(handler) ? 0 : ctx.config().defaultRequire();
		List<AnnotationNode> slices = annotationList(Annotations.value(ann, "slice"));
		String injectorName = "@" + simpleName(ann.desc);

		List<MethodNode> matched = new ArrayList<>();
		boolean anyFuzzy = false;
		String lastSelector = null;
		String lastName = null;
		for (Object o : selectors) {
			if (!(o instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null) {
				anyFuzzy = true;
				continue;
			}
			lastSelector = ref.name() + (ref.desc() != null ? ref.desc() : "");
			lastName = ref.name();
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
			if (!anyFuzzy && lastSelector != null && addedByAMixin(lastName)) {
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

		if (ann.desc.equals(MODIFY_CONSTANT)) {
			for (AnnotationNode constant : annotationList(Annotations.value(ann, "constant"))) {
				String expected = constantDescription(constant);
				if (expected == null) continue;
				if (matched.stream().allMatch(m -> constantVerdict(constant, m) == Verdict.MISSING)) {
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

		for (AnnotationNode at : annotationList(Annotations.value(ann, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
			int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
			int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
			AnnotationNode slice = sliceFor(at, slices);
			Verdict verdict = Verdict.UNKNOWN;
			for (MethodNode m : matched) {
				Range range = slice == null ? Range.ALL : sliceRange(slice, m, ctx);
				Verdict v = point.equals("MIXINEXTRAS:EXPRESSION") ? evaluateExpression(handler, m, ctx)
						: evaluate(point, rawTarget == null ? null : ctx.resolve(rawTarget), ordinal, opcode, range, m);
				if (v == Verdict.FOUND) {
					verdict = Verdict.FOUND;
					break;
				}
				if (v == Verdict.MISSING && verdict == Verdict.UNKNOWN) verdict = Verdict.MISSING;
			}
			if (verdict == Verdict.MISSING) {
				ctx.injector(handler, require, injectorName + " in " + matched.getFirst().name + " expects @At(\"" + point + "\""
								+ (rawTarget != null ? ", target=\"" + ctx.resolve(rawTarget) + "\"" : "")
								+ (ordinal >= 0 ? ", ordinal=" + ordinal : "") + "), which does not occur in that method",
						ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
				return;
			}
			if (verdict == Verdict.FOUND && ann.desc.equals(WRAP_OPERATION) && rawTarget != null) {
				String mismatch = wrappedArityMismatch(handler, point, ctx.resolve(rawTarget), matched);
				if (mismatch != null) {
					ctx.classLevel(injectorName + " handler " + handler.name + " " + mismatch + " in " + matched.getFirst().name,
							(ctx.owner() == null ? "NeoForge's build of Minecraft" : ctx.changer(true)) + " calls this with different arguments; the mod needs an update or this handler must be skipped.");
					return;
				}
			}
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
			for (AnnotationNode a : parameterAnnotations(handler, i)) {
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
		List<AnnotationNode> ats = annotationList(Annotations.value(injector, "at"));
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
			for (AnnotationNode local : parameterAnnotations(handler, p)) {
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
						if (opcode >= 0 && insn.getOpcode() != opcode || !matchesInsn(point, ref, insn)) continue;
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
			for (AnnotationNode a : parameterAnnotations(handler, i)) {
				if (a.desc.equals(COERCE)) return null;
				if (a.desc.startsWith("Lcom/llamalad7/mixinextras/sugar/")) sugar = true;
			}
			if (!sugar) args.add(types[i]);
		}
		return args;
	}

	private static List<AnnotationNode> parameterAnnotations(MethodNode method, int index) {
		List<AnnotationNode> all = new ArrayList<>();
		if (method.visibleParameterAnnotations != null && index < method.visibleParameterAnnotations.length
				&& method.visibleParameterAnnotations[index] != null) all.addAll(method.visibleParameterAnnotations[index]);
		if (method.invisibleParameterAnnotations != null && index < method.invisibleParameterAnnotations.length
				&& method.invisibleParameterAnnotations[index] != null) all.addAll(method.invisibleParameterAnnotations[index]);
		return all;
	}

	private static boolean isCallbackInfo(Type type) {
		return type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;")
				|| type.getDescriptor().equals("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;");
	}

	private enum Verdict {
		FOUND, MISSING, UNKNOWN
	}

	/**
	 * Instruction index range an injection point is searched in (inclusive). {@link #ALL} is the whole method;
	 * {@link #UNEVALUATED} means a slice exists that pre-flight cannot compute, so only "the instruction occurs
	 * nowhere in the method" can be concluded.
	 */
	private record Range(int start, int end) {
		static final Range ALL = new Range(0, Integer.MAX_VALUE);
		static final Range UNEVALUATED = new Range(-1, -1);
	}

	private static List<AnnotationNode> annotationList(Object value) {
		List<AnnotationNode> ret = new ArrayList<>();
		if (value instanceof AnnotationNode a) ret.add(a);
		if (value instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof AnnotationNode a) ret.add(a);
			}
		}
		return ret;
	}

	/** The {@code @Slice} an {@code @At} uses (matched by id, as Mixin does), or {@code null} for none. */
	private static AnnotationNode sliceFor(AnnotationNode at, List<AnnotationNode> slices) {
		if (slices.isEmpty()) return null;
		String id = Annotations.value(at, "slice") instanceof String s ? s : "";
		for (AnnotationNode slice : slices) {
			String sliceId = Annotations.value(slice, "id") instanceof String s ? s : "";
			if (sliceId.equals(id)) return slice;
		}
		return null;
	}

	/**
	 * Mirrors Mixin's {@code MethodSlice.getSlice}: {@code from} defaults to the first instruction and falls back
	 * to it when its point is not found; {@code to} defaults to the last instruction and falls back to the
	 * start. Points select their first match (or the {@code ordinal}-th). Only member-reference points
	 * ({@code INVOKE}, {@code FIELD}, {@code NEW}) and {@code HEAD} are evaluated.
	 */
	private static Range sliceRange(AnnotationNode slice, MethodNode method, Context ctx) {
		int max = method.instructions.size() - 1;
		int start = slicePoint(annotationOrNull(Annotations.value(slice, "from")), method, ctx, 0, 0);
		if (start == UNEVALUATED_INDEX) return Range.UNEVALUATED;
		int end = slicePoint(annotationOrNull(Annotations.value(slice, "to")), method, ctx, max, start);
		if (end == UNEVALUATED_INDEX || start > end) return Range.UNEVALUATED;
		return new Range(start, end);
	}

	private static final int UNEVALUATED_INDEX = Integer.MIN_VALUE;

	private static AnnotationNode annotationOrNull(Object value) {
		return value instanceof AnnotationNode a ? a : null;
	}

	private static int slicePoint(AnnotationNode at, MethodNode method, Context ctx, int defaultValue, int failValue) {
		if (at == null) return defaultValue;
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (point.equals("HEAD")) return 0;
		if (!point.equals("INVOKE") && !point.equals("INVOKE_ASSIGN") && !point.equals("FIELD") && !point.equals("NEW")) return UNEVALUATED_INDEX;
		String rawTarget = Annotations.value(at, "target") instanceof String t ? t : null;
		if (rawTarget == null || rawTarget.contains(":LAST") || rawTarget.contains(":ONE") || rawTarget.contains(":FIRST")) return UNEVALUATED_INDEX;
		MemberRef ref = MemberRef.parse(ctx.resolve(rawTarget));
		if (ref.fuzzy()) return UNEVALUATED_INDEX;
		int ordinal = Annotations.value(at, "ordinal") instanceof Integer i ? i : -1;
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		int seen = 0;
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (opcode >= 0 && insn.getOpcode() != opcode) continue;
			if (!matchesInsn(point, ref, insn)) continue;
			if (ordinal < 0 || seen == ordinal) return i;
			seen++;
		}
		return failValue;
	}

	private static Verdict evaluate(String point, String target, int ordinal, int opcode, Range range, MethodNode method) {
		switch (point) {
			case "HEAD", "RETURN", "TAIL" -> {
				return method.instructions == null || method.instructions.size() == 0 ? Verdict.MISSING : Verdict.FOUND;
			}
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW" -> {
				if (target == null) return Verdict.UNKNOWN;
				MemberRef ref = MemberRef.parse(target);
				if (ref.fuzzy()) return Verdict.UNKNOWN;
				int countInMethod = 0;
				int countInRange = 0;
				for (int i = 0; i < method.instructions.size(); i++) {
					AbstractInsnNode insn = method.instructions.get(i);
					if (opcode >= 0 && insn.getOpcode() != opcode) continue;
					if (!matchesInsn(point, ref, insn)) continue;
					countInMethod++;
					if (i >= range.start() && i <= range.end()) countInRange++;
				}
				if (countInMethod == 0) return Verdict.MISSING;
				if (range == Range.UNEVALUATED) return Verdict.FOUND;
				if (countInRange == 0) return Verdict.MISSING;
				if (ordinal >= 0 && countInRange <= ordinal) return Verdict.MISSING;
				return Verdict.FOUND;
			}
			case "LOAD", "STORE" -> {
				// Not judged precisely (the local's type and index), but a slice without any local variable load or
				// store (as when its end point is gone and Mixin cuts it down to one instruction) can't match.
				if (range == Range.UNEVALUATED || method.instructions == null) return Verdict.UNKNOWN;
				boolean load = point.equals("LOAD");
				int last = Math.min(range.end(), method.instructions.size() - 1);
				for (int i = Math.max(range.start(), 0); i <= last; i++) {
					int op = method.instructions.get(i).getOpcode();
					if (load ? op >= Opcodes.ILOAD && op <= Opcodes.ALOAD : op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) return Verdict.UNKNOWN;
				}
				return Verdict.MISSING;
			}
			default -> {
				return Verdict.UNKNOWN;
			}
		}
	}

	/**
	 * A {@code @WrapOperation} handler takes the wrapped call's receiver (unless static) and arguments, then the
	 * {@code Operation}. Returns why that does not fit any matching call or constructor, or null when it does (or
	 * cannot be judged).
	 */
	private static String wrappedArityMismatch(MethodNode handler, String point, String target, List<MethodNode> methods) {
		Type[] args = Type.getArgumentTypes(handler.desc);
		int taken = -1;
		for (int i = 0; i < args.length; i++) {
			if (args[i].getDescriptor().equals(OPERATION)) {
				taken = i;
				break;
			}
		}
		MemberRef ref = MemberRef.parse(target);
		if (taken < 0 || ref.fuzzy()) return null;
		Set<Integer> passed = new java.util.TreeSet<>();
		for (MethodNode method : methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				switch (point) {
					case "INVOKE", "INVOKE_ASSIGN" -> {
						if (matchesInsn(point, ref, insn)) {
							passed.add(Type.getArgumentTypes(call.desc).length + (call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1));
						}
					}
					case "NEW" -> {
						String cls = newTarget(ref);
						if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>") && cls != null && cls.equals(call.owner)
								&& (ref.methodDescOr(null) == null || matchesInsn(point, ref, insn))) {
							passed.add(Type.getArgumentTypes(call.desc).length);
						}
					}
					default -> {
						return null;
					}
				}
			}
		}
		if (passed.isEmpty() || passed.contains(taken)) return null;
		return "takes " + taken + " value(s) before its Operation, but the wrapped " + (point.equals("NEW") ? "constructor" : "call") + " passes " + passed;
	}

	/**
	 * A MixinExtras expression can only match when what each of the handler's {@code @Definition}s names (a type, a
	 * method or a field) occurs in the target method. The expression itself is not evaluated.
	 */
	private static Verdict evaluateExpression(MethodNode handler, MethodNode method, Context ctx) {
		List<AnnotationNode> definitions = new ArrayList<>();
		for (AnnotationNode ann : allAnnotations(handler)) {
			if (ann.desc.equals(DEFINITION)) definitions.add(ann);
			else if (ann.desc.equals(DEFINITIONS)) definitions.addAll(annotationList(Annotations.value(ann, "value")));
		}
		if (definitions.isEmpty() || method.instructions == null) return Verdict.UNKNOWN;
		for (AnnotationNode definition : definitions) {
			if (!definitionOccurs(definition, method, ctx)) return Verdict.MISSING;
		}
		return Verdict.FOUND;
	}

	private static boolean definitionOccurs(AnnotationNode definition, MethodNode method, Context ctx) {
		List<AbstractInsnNode> insns = List.of(method.instructions.toArray());
		boolean judged = false;
		if (Annotations.value(definition, "type") instanceof List<?> types) {
			for (Object o : types) {
				if (!(o instanceof Type type) || (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY)) return true;
				judged = true;
				if (insns.stream().anyMatch(insn -> references(insn, type))) return true;
			}
		}
		for (String kind : List.of("method", "field")) {
			if (!(Annotations.value(definition, kind) instanceof List<?> refs)) continue;
			for (Object o : refs) {
				if (!(o instanceof String raw)) continue;
				MemberRef ref = MemberRef.parse(ctx.resolve(raw));
				if (ref.fuzzy() || ref.name() == null) return true;
				judged = true;
				String point = kind.equals("method") ? "INVOKE" : "FIELD";
				if (insns.stream().anyMatch(insn -> matchesInsn(point, ref, insn) || point.equals("INVOKE") && referencesHandle(insn, ref))) return true;
			}
		}
		return !judged;
	}

	/** Whether the mixin declares a method {@code ref} selects that Mixin merges into the target (not a shadow or handler). */
	private static boolean addsMethod(ClassNode mixin, MemberRef ref) {
		for (MethodNode m : mixin.methods) {
			if (!ref.matchesMethod(m.name, m.desc) || m.name.startsWith("<") || Annotations.find(m, SHADOW) != null) continue;
			if (allAnnotations(m).stream().noneMatch(a -> isInjector(a.desc))) return true;
		}
		return false;
	}

	/**
	 * Whether {@code insn} is a method reference or lambda ({@code invokedynamic}) to a method named and typed as
	 * {@code ref} selects; its owner may be a subclass (as {@code this::canHitEntity} names it), so it isn't compared.
	 */
	private static boolean referencesHandle(AbstractInsnNode insn, MemberRef ref) {
		if (!(insn instanceof InvokeDynamicInsnNode indy)) return false;
		for (Object arg : indy.bsmArgs) {
			if (arg instanceof Handle h && ref.matchesMethod(h.getName(), h.getDesc())) return true;
		}
		return false;
	}

	/** Whether {@code insn} uses {@code type}: creates, casts or checks it, calls or reads a member of it, or passes it. */
	private static boolean references(AbstractInsnNode insn, Type type) {
		String internal = type.getInternalName();
		String desc = type.getDescriptor();
		return switch (insn) {
			case TypeInsnNode t -> t.desc.equals(internal);
			case MethodInsnNode m -> m.owner.equals(internal) || m.desc.contains(desc);
			case FieldInsnNode f -> f.owner.equals(internal) || f.desc.contains(desc);
			case InvokeDynamicInsnNode d -> d.desc.contains(desc);
			case LdcInsnNode l -> type.equals(l.cst);
			default -> false;
		};
	}

	private static boolean matchesInsn(String point, MemberRef ref, AbstractInsnNode insn) {
		switch (point) {
			case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING" -> {
				if (!(insn instanceof MethodInsnNode m)) return false;
				return (ref.owner() == null || ref.owner().equals(m.owner)) && ref.matchesMethod(m.name, m.desc);
			}
			case "FIELD" -> {
				if (!(insn instanceof FieldInsnNode f)) return false;
				return (ref.owner() == null || ref.owner().equals(f.owner))
						&& ref.name() != null && ref.name().equals(f.name)
						&& (ref.desc() == null || ref.desc().equals(f.desc));
			}
			case "NEW" -> {
				String cls = newTarget(ref);
				String ctorDesc = ref.methodDescOr(null);
				if (ctorDesc != null) {
					// A constructor descriptor selects one constructor, as Mixin's BeforeNew does: count the
					// INVOKESPECIAL <init> with those arguments rather than any NEW of the class.
					String initDesc = ctorDesc.substring(0, ctorDesc.indexOf(')') + 1) + "V";
					return insn instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESPECIAL
							&& m.name.equals("<init>") && m.desc.equals(initDesc) && (cls == null || cls.equals(m.owner));
				}
				if (!(insn instanceof TypeInsnNode t) || t.getOpcode() != Opcodes.NEW) return false;
				return cls == null || cls.equals(t.desc);
			}
			default -> {
				return false;
			}
		}
	}

	/** Class created by a NEW selector: {@code Lpkg/C;}, {@code pkg/C} or a constructor descriptor. */
	private static String newTarget(MemberRef ref) {
		if (ref.owner() != null && ref.name() == null) return ref.owner();
		String ctorDesc = ref.methodDescOr(null);
		if (ctorDesc != null) {
			Type ret = Type.getReturnType(ctorDesc);
			return ret.getSort() == Type.OBJECT ? ret.getInternalName() : null;
		}
		if (ref.name() != null && ref.owner() == null) return ref.name();
		return ref.owner();
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

	private static List<AnnotationNode> allAnnotations(MethodNode method) {
		List<AnnotationNode> ret = new ArrayList<>();
		if (method.visibleAnnotations != null) ret.addAll(method.visibleAnnotations);
		if (method.invisibleAnnotations != null) ret.addAll(method.invisibleAnnotations);
		return ret;
	}

	private static String simpleName(String desc) {
		String internal = desc.substring(1, desc.length() - 1);
		return internal.substring(internal.lastIndexOf('/') + 1);
	}
}
