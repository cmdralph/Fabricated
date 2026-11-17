package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

/**
 * Has the game read the test mod's models of the model loader checks (CORPUS-92, MODEL-5), each naming a loader Forge
 * lacks: 1.19.2 reads a block model only where a block state or a model asks for it, where 1.20.1 reads every model
 * file. They are asked for as a Fabric mod for 1.19.2 asks for a model of its own, through fabric-models-v0's model
 * provider (with Fabric API's jar only). A client entrypoint of 1.19.2's own.
 */
@Environment(EnvType.CLIENT)
public final class ForgeModelLoaderClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-models-v0")) return;
		ModelLoadingRegistry.INSTANCE.registerModelProvider((manager, out) -> {
			out.accept(new ResourceLocation(ForgeTestMod.NAMESPACE, "block/fabric_loader"));
			out.accept(new ResourceLocation(ForgeTestMod.NAMESPACE, "block/forge_loader"));
		});
	}
}
