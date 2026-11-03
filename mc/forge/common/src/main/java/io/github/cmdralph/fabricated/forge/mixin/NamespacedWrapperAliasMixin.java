package io.github.cmdralph.fabricated.forge.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.MainEntrypoints;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric API's registry aliases ({@code FabricRegistry.addAlias}) in the registries Forge manages (CORPUS-75). Fabric
 * API keeps an alias in {@code MappedRegistry} and resolves it where {@code MappedRegistry}'s lookups by name and key
 * begin; Forge's {@code NamespacedWrapper} answers those lookups itself, from its own maps and Forge's registry, so an
 * alias added to one was kept where nothing looked. A mod that renamed its entries and kept their old names as aliases
 * then failed wherever data still names them: Waystones' world generation names its renamed waystones, and its data
 * pack didn't load. This class overrides {@code addAlias} for Forge's registries with Fabric API's checks and its rule
 * that an alias names an entry and never another alias, adds the alias to Forge's registry too (whose own lookups,
 * snapshots and sync to clients then know it), and resolves it where the wrapper's lookups begin, as Fabric API does in
 * {@code MappedRegistry}'s. Like the registry's other writes, an alias added before the Fabric main entrypoints, which
 * Forge's lock would refuse, opens the registry for that write ({@link NamespacedWrapperEarlyWriteMixin}).
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper")
abstract class NamespacedWrapperAliasMixin<T> {
	@Unique
	private static final Logger FABRICATED$LOGGER = LoggerFactory.getLogger("Fabricated");

	@Shadow
	boolean locked;

	@Shadow
	@Final
	private ForgeRegistry<T> delegate;

	/** Each alias and the name of the entry it stands for, never another alias (Fabric API's invariant). */
	@Unique
	private final Map<Identifier, Identifier> fabricated$aliases = new HashMap<>();

	/** Fabric API's {@code FabricRegistry.addAlias}, which Fabric API implements in {@code MappedRegistry}. */
	public void addAlias(Identifier old, Identifier newId) {
		Objects.requireNonNull(old, "alias cannot be null");
		Objects.requireNonNull(newId, "aliased id cannot be null");
		Identifier registry = delegate.getRegistryName();
		if (fabricated$aliases.containsKey(old)) {
			throw new IllegalArgumentException("Tried adding %s as an alias for %s, but it is already an alias (for %s) in registry %s"
					.formatted(old, newId, fabricated$aliases.get(old), registry));
		}
		if (delegate.containsKey(old)) {
			throw new IllegalArgumentException("Tried adding %s as an alias, but it is already present in registry %s".formatted(old, registry));
		}
		if (old.equals(fabricated$aliases.get(newId))) {
			throw new IllegalArgumentException("Making %1$s an alias of %2$s would create a cycle, as %2$s is already an alias of %1$s (registry %3$s)"
					.formatted(old, newId, registry));
		}
		if (!delegate.containsKey(newId)) {
			FABRICATED$LOGGER.warn("Adding {} as an alias for {}, but the latter doesn't exist in registry {}", old, newId, registry);
		}
		Identifier deepest = fabricated$aliases.getOrDefault(newId, newId);
		// Forge's registry first: once it is locked, as after the registries' freeze, it refuses the alias, and then nothing
		// has changed (where Fabric API's validateWrite refuses it).
		fabricated$addToForge(old, deepest);
		for (Map.Entry<Identifier, Identifier> alias : fabricated$aliases.entrySet()) {
			if (old.equals(alias.getValue())) {
				fabricated$addToForge(alias.getKey(), deepest);
				alias.setValue(deepest);
			}
		}
		fabricated$aliases.put(old, deepest);
	}

	@Unique
	private void fabricated$addToForge(Identifier alias, Identifier name) {
		if (!delegate.isLocked() || !MainEntrypoints.pending()) {
			delegate.addAlias(alias, name);
			return;
		}
		boolean wasLocked = locked;
		// Clears the wrapper's lock with the registry's own (ForgeRegistry.unfreeze), as NamespacedWrapperEarlyWriteMixin does.
		delegate.unfreeze();
		try {
			delegate.addAlias(alias, name);
		} finally {
			delegate.freeze();
			locked = wasLocked;
		}
	}

	/** Fabric API refuses an entry under a name that is an alias. */
	@Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
			at = @At("HEAD"))
	private void fabricated$refuseAliasName(ResourceKey<T> key, T value, RegistrationInfo info, CallbackInfoReturnable<?> cir) {
		Identifier aliased = fabricated$aliases.get(key.identifier());
		if (aliased != null) {
			throw new IllegalArgumentException("Tried registering %s to registry %s, but it is already an alias (for %s)"
					.formatted(key.identifier(), delegate.getRegistryName(), aliased));
		}
	}

	@ModifyVariable(method = {
			"get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;",
			"getValue(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;",
			"getOptional(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;",
			"containsKey(Lnet/minecraft/resources/Identifier;)Z",
			"getHolder(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;"
	}, at = @At("HEAD"), argsOnly = true)
	private Identifier fabricated$aliasedName(Identifier name) {
		return name == null ? null : fabricated$aliases.getOrDefault(name, name);
	}

	@ModifyVariable(method = {
			"get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
			"getValue(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
			"containsKey(Lnet/minecraft/resources/ResourceKey;)Z",
			"getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;",
			"registrationInfo(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;"
	}, at = @At("HEAD"), argsOnly = true)
	private ResourceKey<T> fabricated$aliasedKey(ResourceKey<T> key) {
		if (key == null) return null;
		Identifier aliased = fabricated$aliases.get(key.identifier());
		return aliased == null ? key : ResourceKey.create(key.registryKey(), aliased);
	}
}
