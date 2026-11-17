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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric API's registry aliases ({@code FabricRegistry.addAlias}) in the registries Forge 52 manages (CORPUS-75), as on
 * Forge 61 to 66: Forge's {@code NamespacedWrapper} answers the lookups by name and key where Fabric API resolves an
 * alias in {@code MappedRegistry} itself, so this overrides {@code addAlias} for Forge's registries with Fabric API's
 * checks and its rule that an alias names an entry and never another alias, adds the alias to Forge's registry too, and
 * resolves it where the wrapper's lookups begin. Its lookups are 1.21.1's (read in Forge 52's {@code NamespacedWrapper}):
 * {@code get} answers the entry itself, where 1.21.11's {@code getValue} does. Like the registry's other writes, an
 * alias added before the Fabric main entrypoints, which Forge's lock would refuse, opens the registry for that write
 * ({@link NamespacedWrapperEarlyWriteMixin}).
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
	private final Map<ResourceLocation, ResourceLocation> fabricated$aliases = new HashMap<>();

	/** Fabric API's {@code FabricRegistry.addAlias}, which Fabric API implements in {@code MappedRegistry}. */
	public void addAlias(ResourceLocation old, ResourceLocation newId) {
		Objects.requireNonNull(old, "alias cannot be null");
		Objects.requireNonNull(newId, "aliased id cannot be null");
		ResourceLocation registry = delegate.getRegistryName();
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
		ResourceLocation deepest = fabricated$aliases.getOrDefault(newId, newId);
		// Forge's registry first: once it is locked, as after the registries' freeze, it refuses the alias, and then nothing
		// has changed (where Fabric API's validateWrite refuses it).
		fabricated$addToForge(old, deepest);
		for (Map.Entry<ResourceLocation, ResourceLocation> alias : fabricated$aliases.entrySet()) {
			if (old.equals(alias.getValue())) {
				fabricated$addToForge(alias.getKey(), deepest);
				alias.setValue(deepest);
			}
		}
		fabricated$aliases.put(old, deepest);
	}

	@Unique
	private void fabricated$addToForge(ResourceLocation alias, ResourceLocation name) {
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
		ResourceLocation aliased = fabricated$aliases.get(key.location());
		if (aliased != null) {
			throw new IllegalArgumentException("Tried registering %s to registry %s, but it is already an alias (for %s)"
					.formatted(key.location(), delegate.getRegistryName(), aliased));
		}
	}

	@ModifyVariable(method = {
			"get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
			"getOptional(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
			"containsKey(Lnet/minecraft/resources/ResourceLocation;)Z",
			"getHolder(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"
	}, at = @At("HEAD"), argsOnly = true)
	private ResourceLocation fabricated$aliasedName(ResourceLocation name) {
		return name == null ? null : fabricated$aliases.getOrDefault(name, name);
	}

	@ModifyVariable(method = {
			"get(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
			"containsKey(Lnet/minecraft/resources/ResourceKey;)Z",
			"getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
			"getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;",
			"registrationInfo(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;"
	}, at = @At("HEAD"), argsOnly = true)
	private ResourceKey<T> fabricated$aliasedKey(ResourceKey<T> key) {
		if (key == null) return null;
		ResourceLocation aliased = fabricated$aliases.get(key.location());
		return aliased == null ? key : ResourceKey.create(key.registryKey(), aliased);
	}
}
