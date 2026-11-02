package io.github.cmdralph.fabricated.forge.rendering;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The plugin of Fabricated's renderer mixin configs on Forge (FRAPI rule 8's guards): their mixins apply only when Fabric's
 * renderer API is installed. On Forge the renderer API is upstream's, which comes with the Fabric API jar, and the guards
 * name its types (a model's emitter): without it Mixin can't read them, and the first model class it would change fails to
 * load. Without the renderer API there is nothing to guard, so nothing of these configs applies.
 *
 * <p>Asked when Mixin selects the configs, after Fabricated resolved the Fabric mods (Fabric mods' own plugins ask Fabric
 * Loader the same way). Shared by Forge 47 and Forge 61 to 66.
 */
public final class FrapiMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The module the guards serve; as {@code FabricBlockRendering.ENABLED} asks. */
	private static final String MODULE = "fabric-renderer-api-v1";

	private boolean enabled;

	@Override
	public void onLoad(String mixinPackage) {
		try {
			enabled = FabricLoader.getInstance().isModLoaded(MODULE);
		} catch (RuntimeException | LinkageError e) {
			// Fabricated's loader didn't run (it would have set Fabric Loader up): no Fabric mod is loaded, so nor is the API.
			LOGGER.warn("Fabricated could not ask whether Fabric's renderer API is installed; its guards in {} stay out", mixinPackage, e);
			enabled = false;
		}
		if (!enabled) LOGGER.debug("Fabric's renderer API isn't installed: Fabricated's guards in {} stay out", mixinPackage);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return enabled;
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
