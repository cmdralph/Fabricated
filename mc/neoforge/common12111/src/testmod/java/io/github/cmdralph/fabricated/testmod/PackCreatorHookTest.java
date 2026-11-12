package io.github.cmdralph.fabricated.testmod;

import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * What the test mod's mixin of upstream's mod resource pack creator ({@code ModResourcePackCreatorMixin}) checks from
 * 1.21.11 on (PROVIDER-22): its hook at the end of {@code loadPacks} of a creator made for server data ran, and once the
 * server has started, its pack repository has the test mod's built-in data pack, which that creator offers.
 */
public final class PackCreatorHookTest {
	private static final String BUILTIN_PACK = "fabricated_testmod:extra";
	private static final AtomicBoolean CHECKED = new AtomicBoolean();

	private PackCreatorHookTest() {
	}

	/** Called at the end of {@code loadPacks} of a creator made for server data; the first call sets up the check. */
	public static void loaded() {
		if (!CHECKED.compareAndSet(false, true)) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server.getPackRepository().getAvailableIds().contains(BUILTIN_PACK)) {
				TestMod.LOGGER.info("FABRICATED-TEST mod resource pack creator hooks OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST mod resource pack creator hooks FAILED: the server's pack repository hasn't {}, which the creator offers",
						BUILTIN_PACK);
			}
		});
	}
}
