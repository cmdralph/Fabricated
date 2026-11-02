package net.fabricmc.fabric.impl.resource.pack;

import io.github.cmdralph.fabricated.neoforge.resource.FabricBuiltinPackSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Source of the built-in packs Fabric mods register. Same name as upstream, which other Fabric API modules test
 * with {@code instanceof}. Unlike upstream (which patches {@code MinecraftServer} to keep "normal" packs off),
 * the activation type decides {@link #shouldAddAutomatically()} directly, which vanilla already honours. Built-in
 * resource packs enabled by default are switched on by {@code DefaultResourcePacks}.
 */
public record BuiltinModPackSource(String modId, boolean addAutomatically) implements FabricBuiltinPackSource {
	@Override
	public boolean shouldAddAutomatically() {
		return this.addAutomatically;
	}

	@Override
	public Component decorate(Component packName) {
		return Component.translatable("pack.nameAndSource", packName, Component.translatable("pack.source.builtinMod", this.modId))
				.withStyle(ChatFormatting.GRAY);
	}
}
