package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * A built-in resource pack a Fabric mod enables by default (ResourceLoaderTest's {@code extra_assets}) is switched on
 * the first time the client is offered it, as Fabric API does on Fabric, and its resources load. CI starts each client
 * in a new game directory, where the pack is offered for the first time. Where another mod provides the resource loader
 * and leaves the pack unselected for the reason {@link TestCompat#PROVIDERS_DEFAULT_PACK} gives, the check is skipped.
 */
public final class DefaultResourcePackTest implements ClientModInitializer {
	private static final String PACK = "fabricated_testmod:extra_assets";

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			PackRepository repository = client.getResourcePackRepository();
			Pack pack = repository.getPack(PACK);
			boolean selected = repository.getSelectedIds().contains(PACK);
			boolean loaded = client.getResourceManager()
					.getResource(TestCompat.id("fabricated_testmod", "fabricated_test/extra_assets.json")).isPresent();
			if (selected && loaded) {
				TestMod.LOGGER.info("FABRICATED-TEST builtin resource pack enabled by default OK");
			} else if (pack != null && !selected && TestCompat.PROVIDERS_DEFAULT_PACK != null && TestMod.providedByAnotherMod("fabric-resource-loader-v0")) {
				TestMod.LOGGER.info("FABRICATED-TEST builtin resource pack enabled by default SKIPPED: fabric-resource-loader-v0 is another mod's: {} (offered, not selected; PROVIDER-8)",
						TestCompat.PROVIDERS_DEFAULT_PACK);
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST builtin resource pack enabled by default FAILED (offered={}, compatibility={}, selected={}, resource loaded={})",
						pack != null, pack == null ? "none" : pack.getCompatibility(), selected, loaded);
			}
		});
	}
}
