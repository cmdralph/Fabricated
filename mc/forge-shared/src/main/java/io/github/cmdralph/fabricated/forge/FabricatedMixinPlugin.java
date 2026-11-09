package io.github.cmdralph.fabricated.forge;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * The plugin of Fabricated's first mixin config on Forge, whose {@link #onLoad} runs as Mixin selects the configs, before
 * it applies any mixin and before any Fabric mod's mixin plugin loads: every translated mod loads after Fabricated, Forge
 * hands Mixin the mod files in that order, and Mixin registers each file's configs (its manifest's) and loads their
 * plugins in that order. It registers Fabricated's Mixin extensions ({@link MixinExtensions}, CORPUS-63) and creates the
 * Fabric mods' language adapters, as Fabric Loader does before Mixin starts (some mods declare an adapter only to run
 * code that early). It changes nothing about which mixins apply. Shared by Forge 47 and Forge 61 to 66.
 */
public final class FabricatedMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	@Override
	public void onLoad(String mixinPackage) {
		MixinExtensions.install();
		FabricatedState state = FabricatedState.get();
		if (state == null) return;
		for (String failure : state.runtime().createLanguageAdapters()) LOGGER.error("Fabric language adapter not created: {}", failure);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
