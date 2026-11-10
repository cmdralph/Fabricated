package io.github.cmdralph.fabricated.forge.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.BiMap;

import net.minecraftforge.registries.ForgeRegistry;

/**
 * Forge's maps of a registry's values follow a value whose equality changed after it was registered, before Forge
 * takes the registry's snapshot (REG-14). Forge keeps the values in Guava's hash maps, keyed by their equality ({@code
 * ids}, {@code names}, {@code keys}, {@code owners}), where vanilla's registry keys them by identity: a value whose
 * equality changes, as a record's does when a mod sets one of its components through an accessor (Moonlight adds
 * Amendments' cauldrons to the leatherworker's point-of-interest type so), stays in the place its old hash gave it, and
 * a lookup by it misses. The snapshot a server takes as it saves a world then failed it, as no name was found for the
 * value ({@code NullPointerException} in {@code makeSnapshot}). Each entry whose value its map no longer finds is put
 * again, as it is; a map in which two values are now equal, which Forge's maps can't hold (REG-13), is left as it is.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
abstract class ForgeRegistryRekeyMixin<V> {
	@Shadow
	@Final
	private BiMap<Integer, V> ids;
	@Shadow
	@Final
	private BiMap<?, V> names;
	@Shadow
	@Final
	private BiMap<?, V> keys;
	@Shadow
	@Final
	private BiMap<?, V> owners;

	@Inject(method = "makeSnapshot", at = @At("HEAD"))
	private void fabricated$rekeyChangedValues(CallbackInfoReturnable<?> cir) {
		fabricated$rekey(ids);
		fabricated$rekey(names);
		fabricated$rekey(keys);
		fabricated$rekey(owners);
	}

	@Unique
	private static <K, W> void fabricated$rekey(BiMap<K, W> map) {
		List<Map.Entry<K, W>> lost = map.entrySet().stream().filter(e -> !map.containsValue(e.getValue())).map(e -> Map.entry(e.getKey(), e.getValue())).toList();
		if (lost.isEmpty() || new HashSet<>(map.values()).size() != map.size()) return;
		for (Map.Entry<K, W> entry : lost) map.put(entry.getKey(), entry.getValue());
	}
}
