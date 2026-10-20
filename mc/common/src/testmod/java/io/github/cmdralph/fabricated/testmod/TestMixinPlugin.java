package io.github.cmdralph.fabricated.testmod;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The test mod's mixin config plugin: checks that Fabric's start-up order held (its language adapter ran first). It
 * loads no game class (not even through {@code TestMod}): Mixin is still starting.
 */
public final class TestMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestMixinPlugin.class);

	@Override
	public void onLoad(String mixinPackage) {
		boolean created = "created".equals(System.getProperty(EarlyAdapter.PROPERTY));
		LOGGER.info("FABRICATED-TEST early language adapter {}", created ? "OK" : "FAILED (not created before the mod's mixin plugin loaded)");
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
