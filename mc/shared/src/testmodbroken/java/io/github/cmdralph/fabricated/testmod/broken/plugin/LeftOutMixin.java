package io.github.cmdralph.fabricated.testmod.broken.plugin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.server.MinecraftServer;

/**
 * A mixin no game has a field for, which pre-flight leaves out whole, in a config whose plugin fails to load
 * ({@link FailingPlugin}): Mixin applies it all the same, as translated an empty interface, which changes nothing.
 * Applied as the mod has it, it would stop the game (LEAVE-4).
 */
@Mixin(MinecraftServer.class)
public abstract class LeftOutMixin {
	@Shadow
	private int fabricatedTestNoSuchField;
}
