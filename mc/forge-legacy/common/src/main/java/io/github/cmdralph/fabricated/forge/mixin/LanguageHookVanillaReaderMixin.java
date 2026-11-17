package io.github.cmdralph.fabricated.forge.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.locale.Language;
import net.minecraftforge.server.LanguageHook;

/**
 * Forge 47's server reads each language file with vanilla's reader ({@code Language.loadFromJson}), as a Fabric server
 * does through Fabric API's resource loader, so Fabric mods' hooks into that reader apply (CORPUS-100). Forge's
 * {@code LanguageHook} has its own copy of the reader, which no Fabric mod hooks: owo-lib's language mixin lets its
 * language files hold rich text (an array or object where vanilla wants a string, skipped there), and Forge's copy
 * refused owo's whole file. Vanilla's reader does what Forge's copy does (the same format fix, the same error for a
 * value that isn't a string), and the stream is closed after it, as Forge closes it.
 */
@Mixin(value = LanguageHook.class, remap = false)
abstract class LanguageHookVanillaReaderMixin {
	@Shadow
	private static Map<String, String> modTable;

	@Inject(method = "loadLocaleData(Ljava/io/InputStream;)V", at = @At("HEAD"), cancellable = true)
	private static void fabricated$vanillaReader(InputStream stream, CallbackInfo ci) {
		ci.cancel();
		try (InputStream in = stream) {
			Language.loadFromJson(in, modTable::put);
		} catch (IOException ignored) {
			// Only the close can throw it, which Forge's reader ignores too.
		}
	}
}
