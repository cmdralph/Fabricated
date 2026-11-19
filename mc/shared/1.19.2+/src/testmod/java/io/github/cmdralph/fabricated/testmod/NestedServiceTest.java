package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import net.fabricmc.api.ModInitializer;

/**
 * CI test for PKG-6: the test mod's jar declares {@link NestedService} with a provider the jar it nests has, as OneConfig
 * 1.8.0's jar declares the UI bridges its nested jar holds. Fabric's class path finds a provider in any jar; a module
 * declares only providers it has, so the nested jar's module must declare it, or ServiceLoader finds none.
 */
public final class NestedServiceTest implements ModInitializer {
	private static final String PROVIDER = "io.github.cmdralph.fabricated.testmodservice.NestedProvider";

	@Override
	public void onInitialize() {
		List<String> found = new ArrayList<>();
		try {
			for (NestedService service : ServiceLoader.load(NestedService.class, NestedService.class.getClassLoader())) {
				found.add(service.getClass().getName() + " in " + service.getClass().getModule());
			}
		} catch (RuntimeException | LinkageError e) {
			TestMod.LOGGER.error("FABRICATED-TEST nested service FAILED", e);
			return;
		}
		if (found.size() == 1 && found.get(0).startsWith(PROVIDER + " ")) {
			TestMod.LOGGER.info("FABRICATED-TEST nested service OK ({})", found.get(0));
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST nested service FAILED: providers found {}, expected {} once", found, PROVIDER);
		}
	}
}
