package io.github.cmdralph.fabricated.core.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The annotations and names mixin pre-flight reads, shared by the checker ({@link MixinPreflight}) and the repairs
 * ({@link MixinRepairs}).
 */
final class MixinNames {
	private MixinNames() {
	}

	static final String PSEUDO = "Lorg/spongepowered/asm/mixin/Pseudo;";

	static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

	static final String FINAL = "Lorg/spongepowered/asm/mixin/Final;";

	static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";

	static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

	static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

	static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

	static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";

	static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";

	static final String COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;";

	static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";
	static final String SHARE = "Lcom/llamalad7/mixinextras/sugar/Share;";

	static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";

	static final String INTRINSIC = "Lorg/spongepowered/asm/mixin/Intrinsic;";

	static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

	static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";

	static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

	static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";

	static final Set<String> WRAP_WITH_CONDITION = Set.of("Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");

	static final String MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";

	static final String MODIFY_RETURN_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;";

	/**
	 * Fabric Loader's own calls, which its launcher adds to the game where it runs mods' entrypoints, to the call NeoForge's
	 * build makes at that step, in the same method: Fabricated runs those entrypoints in NeoForge's mod loading. Mods inject
	 * after them to run once every mod has initialized (owo-lib freezes its registries after the server's, HOOK-1).
	 */
	/** The receiver, among the values {@link CallRepairs#insertedArgument(ClassNode, String, String, String)} sees pushed. */
	static final int THIS = Integer.MIN_VALUE;

	static final Map<String, String> LOADER_HOOKS = Map.of(
			"Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V",
			"Lnet/neoforged/neoforge/server/loading/ServerModLoader;load(Z)V");

	/** NeoForge's marker for the enums only its own enum extension system may extend. */
	static final String EXTENSIBLE_ENUM = "net/neoforged/fml/common/asm/enumextension/IExtensibleEnum";

	static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";

	static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";

	/** Mixin's argument bundle, the first parameter of a {@code @ModifyArgs} handler. */
	static final String ARGS = "Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;";

	/** The injectors whose {@code at} is one injection point in NeoForge 1.21.11's Mixin and an array in Fabric's. */
	static final Set<String> SINGLE_POINT_INJECTORS = Set.of(REDIRECT, MODIFY_VARIABLE, "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;");

	static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";

	/**
	 * The injectors whose handler takes the value it modifies and then the target's first parameters, as many as it
	 * declares, else Mixin refuses it whatever its {@code require} ({@code Injector.validateParams}).
	 */
	static final Set<String> VALUE_MODIFIERS = Set.of(MODIFY_RETURN_VALUE, MODIFY_EXPRESSION_VALUE, MODIFY_VARIABLE, MODIFY_CONSTANT);

	/**
	 * The injectors Mixin refuses with a handler that isn't static on a static target ({@code Injector.checkTargetModifiers},
	 * before they inject or at each point); MixinExtras' don't check.
	 */
	static final Set<String> STATIC_TARGET_CHECKED = Set.of(INJECT, MODIFY_CONSTANT, "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;");

	static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";

	/** The name prefix javac gives the fields holding the local variables an anonymous or local class captures. */
	static final String CAPTURED_PREFIX = "val$";

	/**
	 * The prefixes of a method Fabricated's in-game mod adds to a game class for a field the platform gave another type,
	 * followed by the field's name: it gets or sets the field's value as vanilla's type, and an {@code @Accessor} of the
	 * field invokes it instead ({@code MixinRepairs.retypedFieldBridge}, CORPUS-37).
	 */
	static final String BRIDGE_GETTER = "fabricated$get$";
	static final String BRIDGE_SETTER = "fabricated$set$";

	/** javac's name for a lambda body: the enclosing method's name and the lambda's index in the class. */
	static final Pattern LAMBDA = Pattern.compile("lambda\\$(.+)\\$\\d+");

	static final String NEOFORGE_PACKAGE = "net/neoforged/";

	/**
	 * The packages of the platforms' own code (NeoForge's, Forge's): where their patches put the hooks a game call moves
	 * into, which pre-flight follows to the call (HOOK-2, HOOK-3). Each platform has only its own, so both are named.
	 */
	static final List<String> PLATFORM_PACKAGES = List.of(NEOFORGE_PACKAGE, "net/minecraftforge/");

	/** Whether a class (internal name) is the platform's own code: NeoForge's or Forge's ({@link #PLATFORM_PACKAGES}). */
	static boolean isPlatformCode(String internalName) {
		if (internalName == null) return false;
		for (String prefix : PLATFORM_PACKAGES) {
			if (internalName.startsWith(prefix)) return true;
		}
		return false;
	}

	static final String CALLBACK_INFO_RETURNABLE = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
}
