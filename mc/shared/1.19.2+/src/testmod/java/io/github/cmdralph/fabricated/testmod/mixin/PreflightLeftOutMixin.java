package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.server.MinecraftServer;

/**
 * A mixin no game has a field for, which pre-flight leaves out whole and the test mod's plugin would apply: it stays in
 * the config for Mixin to ask the plugin about, and the plugin, as translated, turns it down (LEAVE-4). Applied, it would
 * stop the game; the report lists it as left out.
 */
@Mixin(MinecraftServer.class)
public abstract class PreflightLeftOutMixin {
	@Shadow
	private int fabricatedTestNoSuchField;
}
