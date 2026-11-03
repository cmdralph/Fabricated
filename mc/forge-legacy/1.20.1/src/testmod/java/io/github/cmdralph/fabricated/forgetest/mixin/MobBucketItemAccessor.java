package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.class_1299;
import net.minecraft.class_1785;

/**
 * A mob bucket's entity type, as Supplementaries' accessor reads it: a field Forge 47 replaced with a supplier, so this
 * invokes the in-game mod's bridge (CORPUS-94, {@code ForgeMobBucketTest}).
 */
@Mixin(class_1785.class)
public interface MobBucketItemAccessor {
	@Accessor("field_7991")
	class_1299<?> fabricatedtest$type();
}
