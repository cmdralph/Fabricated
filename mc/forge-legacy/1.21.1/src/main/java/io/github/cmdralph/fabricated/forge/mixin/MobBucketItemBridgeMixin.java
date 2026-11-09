package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.MobBucketItem;

/**
 * A mob bucket's entity type as vanilla keeps it, for Fabric mods' accessors of it (CORPUS-94, as on Forge 47). Forge
 * 52 replaces vanilla's {@code type} field with a supplier of the type, which its {@code getFishType} reads, where
 * Supplementaries' and Inventory Profiles Next's accessors read the field. Pre-flight points an accessor of the field
 * at the bridge here ({@code MixinRepairs.retypedFieldBridge} in core), named after the field's runtime name: Forge 52
 * runs in Mojang's names, where Forge 47's bridge is named after the field's SRG name. The field is final in vanilla,
 * and no setter is bridged.
 */
@Mixin(MobBucketItem.class)
abstract class MobBucketItemBridgeMixin {
	@Shadow
	protected abstract EntityType<?> getFishType();

	/** Vanilla's {@code type}: the entity the bucket holds. */
	public EntityType<?> fabricated$get$type() {
		return getFishType();
	}
}
