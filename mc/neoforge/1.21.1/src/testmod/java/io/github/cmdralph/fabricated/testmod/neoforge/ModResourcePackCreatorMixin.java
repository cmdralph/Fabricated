package io.github.cmdralph.fabricated.testmod.neoforge;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.PackCreatorHookTest;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

/**
 * A mixin of upstream's mod resource pack creator, Fabric API's internal class, of the shape YUNG's API's locate replacer
 * has (PROVIDER-22): the pack type, read at the end of the constructor taking it, and a data pack offered at the end of
 * {@code loadPacks} where that is server data. {@link PackCreatorHookTest} checks that the pack reached the server's pack
 * repository.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator")
public abstract class ModResourcePackCreatorMixin {
	@Shadow
	@Final
	private PackType type;
	@Unique
	private boolean fabricatedTest$serverData;

	@Inject(method = "<init>(Lnet/minecraft/server/packs/PackType;Z)V", at = @At("RETURN"))
	private void fabricatedTest$created(PackType packType, boolean forKnownPacksManager, CallbackInfo ci) {
		this.fabricatedTest$serverData = packType == PackType.SERVER_DATA;
	}

	@Inject(method = "loadPacks", at = @At("RETURN"))
	private void fabricatedTest$offerPack(Consumer<Pack> consumer, CallbackInfo ci) {
		if (!this.fabricatedTest$serverData || this.type != PackType.SERVER_DATA) return;
		Pack pack = PackCreatorHookTest.pack();
		if (pack != null) consumer.accept(pack);
	}
}
