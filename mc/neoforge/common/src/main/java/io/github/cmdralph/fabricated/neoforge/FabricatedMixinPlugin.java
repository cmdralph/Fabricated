package io.github.cmdralph.fabricated.neoforge;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * The plugin of Fabricated's first mixin config, whose {@link #onLoad} runs before any Fabric mod's mixin plugin: every
 * translated mod loads after Fabricated, FML registers mixin configs in that order, and Mixin loads their plugins in
 * that order. It creates the Fabric mods' language adapters there, as Fabric Loader does before Mixin starts (some mods
 * declare an adapter only to run code that early), and starts recording the Fabric mods' mixins that fail as Mixin
 * applies them, for the next launch to leave out ({@link MixinFailureRecorder}). It registers Fabricated's Mixin
 * extensions where the platform needs them ({@link MixinExtensions}: NeoForge 21.1's default bridges). It changes nothing
 * about which mixins apply.
 */
public final class FabricatedMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	@Override
	public void onLoad(String mixinPackage) {
		MixinFailureRecorder.install();
		FabricatedState state = FabricatedState.get();
		if (state == null) return;
		MixinExtensions.install();
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
