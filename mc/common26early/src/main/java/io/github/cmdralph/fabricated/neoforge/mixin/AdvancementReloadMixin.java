package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.server.ReloadableServerRegistries;

/**
 * 26.1.2 and 26.2: nothing to do. {@code fabric-advancement-api-v1}'s own reload mixin applies on these versions
 * (on 26.3 Fabricated replaces it; see that version's class).
 */
@Mixin(ReloadableServerRegistries.class)
abstract class AdvancementReloadMixin {
}
