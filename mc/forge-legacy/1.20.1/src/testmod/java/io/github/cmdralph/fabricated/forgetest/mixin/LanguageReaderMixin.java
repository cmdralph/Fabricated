package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.function.BiConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import io.github.cmdralph.fabricated.forgetest.ForgeLanguageTest;
import net.minecraft.class_2477;

/**
 * A Fabric mod's hook into vanilla's language reader ({@code Language.loadFromJson}, {@code method_29425}), as owo-lib's
 * is: it watches the entries read for the test mod's key ({@link ForgeLanguageTest}). A parameter modifier at the head,
 * so it never meets owo's redirects in the same method.
 */
@Mixin(class_2477.class)
public abstract class LanguageReaderMixin {
	@ModifyVariable(method = "method_29425(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), argsOnly = true)
	private static BiConsumer<String, String> fabricatedForgeTest$watch(BiConsumer<String, String> output) {
		return (key, value) -> {
			if (ForgeLanguageTest.KEY.equals(key)) ForgeLanguageTest.readByVanilla = true;
			output.accept(key, value);
		};
	}
}
