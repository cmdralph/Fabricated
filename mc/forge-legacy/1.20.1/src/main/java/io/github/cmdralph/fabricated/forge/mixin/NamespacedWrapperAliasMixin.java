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

import com.mojang.serialization.Lifecycle;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Fabric API's registry aliases ({@code FabricRegistry.addAlias}) in the registries Forge manages (CORPUS-75). Fabric
 * API keeps an alias in {@code MappedRegistry} and resolves it where {@code MappedRegistry}'s lookups by name and key
 * begin; Forge's {@code NamespacedWrapper} answers those lookups itself, from its holders and Forge's registry, so an
 * alias added to one was kept where nothing looked, and data naming an entry by its old name failed (Waystones'
 * renamed waystones, seen on Forge 64 and 65). This class overrides {@code addAlias} for Forge's registries with Fabric
 * API's checks and its rule that an alias names an entry and never another alias, adds the alias to Forge's registry
 * too (whose own lookups, snapshots and sync to clients then know it), and resolves it where the wrapper's lookups
 * begin, as Fabric API does in {@code MappedRegistry}'s.
 *
 * <p>Forge's jar is in SRG's names at run time, so the targets are the SRG names of {@code Registry}'s {@code
 * get(ResourceLocation)} ({@code m_7745_}), {@code getOptional} ({@code m_6612_}), {@code containsKey} ({@code
 * m_7804_}, {@code m_142003_}), {@code get(ResourceKey)} ({@code m_6246_}) and {@code getHolder} ({@code m_203636_}),
 * and of {@code WritableRegistry.register} ({@code m_255290_}), read from MCPConfig's and Fabric's intermediary
 * mappings for 1.20.1; the wrapper's {@code getOrCreateHolderOrThrow} by either name, as vanilla's private one is
 * {@code m_245420_}. Nothing in this mixin is remapped.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper", remap = false)
abstract class NamespacedWrapperAliasMixin<T> {
	@Unique
	private static final Logger FABRICATED$LOGGER = LoggerFactory.getLogger("Fabricated");

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
		// has changed (where Fabric API's assertNotFrozen refuses it).
		delegate.addAlias(old, deepest);
		for (Map.Entry<ResourceLocation, ResourceLocation> alias : fabricated$aliases.entrySet()) {
			if (old.equals(alias.getValue())) {
				delegate.addAlias(alias.getKey(), deepest);
				alias.setValue(deepest);
			}
		}
		fabricated$aliases.put(old, deepest);
	}

	/** Fabric API refuses an entry under a name that is an alias. */
	@Inject(method = "m_255290_(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/core/Holder$Reference;",
			at = @At("HEAD"))
	private void fabricated$refuseAliasName(ResourceKey<T> key, T value, Lifecycle lifecycle, CallbackInfoReturnable<?> cir) {
		ResourceLocation aliased = fabricated$aliases.get(key.location());
		if (aliased != null) {
			throw new IllegalArgumentException("Tried registering %s to registry %s, but it is already an alias (for %s)"
					.formatted(key.location(), delegate.getRegistryName(), aliased));
		}
	}

	@ModifyVariable(method = {
			"m_7745_(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
			"m_6612_(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
			"m_7804_(Lnet/minecraft/resources/ResourceLocation;)Z"
	}, at = @At("HEAD"), argsOnly = true)
	private ResourceLocation fabricated$aliasedName(ResourceLocation name) {
		return name == null ? null : fabricated$aliases.getOrDefault(name, name);
	}

	@ModifyVariable(method = {
			"m_6246_(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
			"m_142003_(Lnet/minecraft/resources/ResourceKey;)Z",
			"m_203636_(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
			"getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;",
			"m_245420_(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"
	}, at = @At("HEAD"), argsOnly = true)
	private ResourceKey<T> fabricated$aliasedKey(ResourceKey<T> key) {
		if (key == null) return null;
		ResourceLocation aliased = fabricated$aliases.get(key.location());
		return aliased == null ? key : ResourceKey.create(ResourceKey.createRegistryKey(key.registry()), aliased);
	}
}
