package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import net.fabricmc.api.ModInitializer;

import io.github.cmdralph.fabricated.testmod.split.SplitService;

/**
 * CI test for PKG-6: the test mod's jar declares services whose providers the jars it nests have, as OneConfig 1.8.0's
 * jar declares the UI bridges its nested jar holds. Fabric's class path finds a provider in any jar; a module declares
 * only providers it has, so ServiceLoader finds each only where the module serving its class declares it:
 * {@link NestedService}'s, in a package of the nested jar's own, in the nested jar's module; {@link SplitService}'s, in a
 * package the test mod has classes in too, in the test mod's module, which serves that package. The test mod's own file
 * of {@link CommentedService} has a comment after its provider's name: found where the platform cuts it, as ServiceLoader
 * does, or Fabricated declares the module's services (Forge), and left out where the platform reads the file as it is
 * (NeoForge 21.1), so that the module is built; the checks say which (ci/checks.tsv).
 */
public final class NestedServiceTest implements ModInitializer {
	@Override
	public void onInitialize() {
		check("nested service", NestedService.class, "io.github.cmdralph.fabricated.testmodservice.NestedProvider", false);
		check("split package service", SplitService.class, "io.github.cmdralph.fabricated.testmod.split.SplitProvider", true);
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

	/** Logs OK where ServiceLoader finds {@code provider} as the one provider of {@code service}, in the test mod's module or another. */
	private static <S> void check(String check, Class<S> service, String provider, boolean inOwnModule) {
		List<String> found = new ArrayList<>();
		Module own = NestedServiceTest.class.getModule();
		boolean where = true;
		try {
			for (S instance : ServiceLoader.load(service, service.getClassLoader())) {
				found.add(instance.getClass().getName() + " in " + instance.getClass().getModule());
				where &= (instance.getClass().getModule() == own) == inOwnModule;
			}
		} catch (RuntimeException | LinkageError e) {
			TestMod.LOGGER.error("FABRICATED-TEST {} FAILED", check, e);
			return;
		}
		if (found.size() == 1 && found.get(0).startsWith(provider + " ") && where) {
			TestMod.LOGGER.info("FABRICATED-TEST {} OK ({})", check, found.get(0));
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST {} FAILED: providers found {}, expected {} once, {} the test mod's module", check, found, provider,
					inOwnModule ? "in" : "outside");
		}
	}
}
