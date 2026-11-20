package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import net.fabricmc.api.ModInitializer;

/**
 * CI test for PKG-8, NeoForge's half: the test mod's own file of {@link CommentedService} has a comment after its
 * provider's name, which ServiceLoader cuts on Fabric's class path. NeoForge's FML 10 and later cut it too, so the
 * provider is found; NeoForge 21.1 reads the file as it is, so Fabricated leaves it out and the module is built without
 * the service (ci/checks.tsv says which is checked where). Forge's half has no such file: Forge's own discovery reads
 * every jar of the mods folder before Fabricated does, and on Forge 43 and 47 such a file stops the game there.
 */
public final class CommentedServiceTest implements ModInitializer {
	@Override
	public void onInitialize() {
		try {
			List<String> found = new ArrayList<>();
			for (CommentedService instance : ServiceLoader.load(CommentedService.class, CommentedService.class.getClassLoader())) {
				found.add(instance.getClass().getName());
			}
			TestMod.LOGGER.info("FABRICATED-TEST commented service: {}", found.isEmpty() ? "none found" : "found " + String.join(", ", found));
		} catch (RuntimeException | LinkageError e) {
			TestMod.LOGGER.error("FABRICATED-TEST commented service FAILED", e);
		}
	}
}
