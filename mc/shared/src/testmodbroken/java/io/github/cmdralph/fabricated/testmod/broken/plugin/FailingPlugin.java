package io.github.cmdralph.fabricated.testmod.broken.plugin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * A mixin config plugin that fails as Mixin constructs it, as one does that touches what the platform hasn't (Fabric
 * Loader's internals, or Fabric's Mixin's own API on Forge's): Mixin logs the error and then applies every mixin of the
 * config, {@link LeftOutMixin} too, which pre-flight left out whole and the config keeps listed for the plugin's answers.
 * As translated, that mixin applies nothing, and the server starts (LEAVE-4).
 */
public final class FailingPlugin implements IMixinConfigPlugin {
	public FailingPlugin() {
		throw new IllegalStateException("intentional plugin failure for Fabricated CI");
	}

	@Override
	public void onLoad(String mixinPackage) {
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
