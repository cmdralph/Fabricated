package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.MobBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


/**
 * A mob bucket's entity type, as Supplementaries' accessor reads it: a field Forge 47 and 52 replaced with a supplier,
 * so this invokes the in-game mod's bridge (CORPUS-94, {@code ForgeMobBucketTest}). The field is vanilla's, which
 * Forge's game this compiles against no longer has: the accessor names it in Intermediary's names, as the mod's jar
 * does once Loom has remapped it, and stays out of the refmap.
 */
@Mixin(MobBucketItem.class)
public interface MobBucketItemAccessor {
	@Accessor(value = "field_7991", remap = false)
	EntityType<?> fabricatedtest$type();
}
