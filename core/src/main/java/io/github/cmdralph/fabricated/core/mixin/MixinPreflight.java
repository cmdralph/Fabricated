package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

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
public final class MixinPreflight extends PreflightPart {
	private final MixinRepairs repairer;

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
		super(new PreflightClasses(game, installed, modClasses, untransformable, clientSide, missingTargetsFatal, vanilla, loader,
				interfaceInjectors, constructorInjects, argumentNames, exactStaticInjects));
		this.repairer = new MixinRepairs(classes, this);
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
	 * The first point of an {@code @Inject} into a constructor that SpongePowered's Mixin rejects (CTOR-2: it throws as it
	 * applies the mixin, whatever the config's {@code required}): any but a return that isn't marked {@code unsafe}, or,
	 * on 0.8.5, any but a return. Null when it takes them all, or on Fabric's Mixin.
	 */
	private String constructorPointRejected(AnnotationNode injector, List<MethodNode> matched) {
		if (constructorInjects == ConstructorInjects.AFTER_DELEGATE || !injector.desc.equals(MixinNames.INJECT)
				|| matched.stream().noneMatch(m -> m.name.equals("<init>"))) return null;
		for (AnnotationNode at : Annotations.list(Annotations.value(injector, "at"))) {
			String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
			if (ConstructorRepairs.RETURN_POINTS.contains(point)) continue;
			if (constructorInjects == ConstructorInjects.UNSAFE_POINTS && Boolean.TRUE.equals(Annotations.value(at, "unsafe"))) continue;
			return point;
		}
		return null;
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
		boolean pseudo = Annotations.find(mixin.invisibleAnnotations, mixin.visibleAnnotations, MixinNames.PSEUDO) != null;

		for (String target : targetsOf(mixin, ctx)) {
			boolean isGame = PreflightAsm.isGameClass(target);
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
			if ((mixin.access & Opcodes.ACC_ENUM) != 0 && targetNode.interfaces.contains(MixinNames.EXTENSIBLE_ENUM)) {
				targetCtx.essential("Enum mixin adds constants to " + target.replace('/', '.') + ", one of NeoForge's extensible enums, which"
						+ " NeoForge lets only its own enum extensions change", "Install the NeoForge build of the mod, if it has one.");
				continue;
			}
			checkMembers(mixin, targetNode, targetCtx);
		}
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
			AnnotationNode group = Annotations.find(method, MixinNames.GROUP);
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
			AnnotationNode shadow = Annotations.find(field, MixinNames.SHADOW);
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
			AnnotationNode shadow = Annotations.find(method, MixinNames.SHADOW);
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
				// Mixin generates an instance accessor method in the target; one that already exists there (for
				// example one NeoForge added) makes Mixin throw when the target class loads. A static accessor or
				// invoker adds nothing to the target (Mixin rewrites the interface's own method), so a same-named
				// method there is harmless: typically the very method the invoker calls.
				if ((method.access & Opcodes.ACC_STATIC) == 0 && PreflightAsm.declares(target, method.name, method.desc)) {
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
					if (itf && !interfaceInjectors) {
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
	 * (for example {@code hashCode}) to a class that has none in vanilla.
	 */
	private static void checkMerged(MethodNode method, ClassNode target, Context ctx) {
		if ((method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT)) != 0 || method.name.startsWith("<")) return;
		if (Annotations.find(method, MixinNames.UNIQUE) != null
				|| Annotations.find(method, MixinNames.INTRINSIC) != null) return;
		if (!PreflightAsm.declares(target, method.name, method.desc)) return;
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
	static String accessorFieldDesc(MethodNode method) {
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

	private void checkInjector(ClassNode mixin, MethodNode handler, AnnotationNode ann, ClassNode target, Context ctx) {
		if (!(Annotations.value(ann, "method") instanceof List<?> selectors) || selectors.isEmpty()) return;
		// As Mixin: a grouped injector requires nothing by default, its group's minimum being the check.
		int require = Annotations.value(ann, "require") instanceof Integer r && r >= 0 ? r : PreflightAsm.grouped(handler) ? 0 : ctx.config().defaultRequire();
		List<AnnotationNode> slices = Annotations.list(Annotations.value(ann, "slice"));
		String injectorName = "@" + PreflightAsm.simpleName(ann.desc);

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

		if (ann.desc.equals(MixinNames.MODIFY_VARIABLE) && !anyFuzzy && !acceptsModifierSignature(handler, matched)) {
			ctx.classLevel(injectorName + " handler " + handler.name + Type.getMethodDescriptor(Type.getReturnType(handler.desc), PreflightAsm.handlerArgs(handler).toArray(Type[]::new))
							+ " does not match the parameters of " + matched.get(0).name + matched.get(0).desc + " in " + target.name.replace('/', '.'),
					(ctx.owner() == null ? loader + "'s build of Minecraft declares this method with different parameters"
							: ctx.changer(true) + " declares this method with different parameters") + "; the mod needs an update or this mixin must be skipped.");
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
		String outOfScope = slices.isEmpty() ? LocalRepairs.namedLocalOutOfScope(handler, ann, matched, ctx) : null;
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
				Verdict v = expression ? InjectionPoints.evaluateExpression(handler, m, ctx) : InjectionPoints.evaluate(point, resolved, ordinal, opcode, range, m);
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
								+ (ordinal >= 0 ? ", ordinal=" + ordinal : "") + "), which does not occur in that method",
						ctx.changer(true) + " changed the method body; the mod needs an update or this mixin must be skipped.");
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
	 * Mixin accepts a {@code @ModifyVariable} handler taking the variable and then the target's first parameters, as
	 * many as it declares (Mixin's {@code Injector.validateParams}: Balm's takes two of three); anything else is an
	 * {@code InvalidInjectionException} when the mixin applies, whatever its {@code require} (Farmer's Delight's hook
	 * into {@code CropBlock.getGrowthSpeed}, whose first parameter NeoForge's build made a block state). Handlers that
	 * use {@code @Coerce} are left to Mixin.
	 */
	private static boolean acceptsModifierSignature(MethodNode handler, List<MethodNode> targets) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
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
				if (!local.desc.equals(MixinNames.LOCAL) || Annotations.value(local, "name") != null || Annotations.value(local, "index") != null
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
}
