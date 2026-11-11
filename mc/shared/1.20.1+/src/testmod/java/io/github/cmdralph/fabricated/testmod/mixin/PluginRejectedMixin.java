package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.server.MinecraftServer;

/**
 * A mixin no game has a field for, which pre-flight leaves out whole and the test mod's plugin turns down too, as
 * Pehkui's turns down the {@code compatNNN} mixins of other versions: the report lists it apart from the mixins
 * pre-flight left out (LEAVE-4).
 */
@Mixin(MinecraftServer.class)
public abstract class PluginRejectedMixin {
	@Shadow
	private int fabricatedTestNoSuchField;
}
