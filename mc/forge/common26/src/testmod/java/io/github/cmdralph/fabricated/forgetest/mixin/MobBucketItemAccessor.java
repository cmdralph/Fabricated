package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.MobBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A mob bucket's entity type, as Inventory Profiles Next's accessor reads it: a field Forge replaced with a supplier, so
 * this invokes the in-game mod's bridge ({@code ForgeMobBucketTest}). The field is vanilla's, which Forge's game this
 * compiles against no longer has: the accessor names it as 26.x's Fabric mods do, in Mojang's names, and stays out of
 * the refmap.
 */
@Mixin(MobBucketItem.class)
public interface MobBucketItemAccessor {
	@Accessor(value = "type", remap = false)
	EntityType<?> fabricatedtest$type();
}
