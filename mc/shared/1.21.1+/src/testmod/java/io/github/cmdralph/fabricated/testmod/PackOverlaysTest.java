package io.github.cmdralph.fabricated.testmod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric's conditional overlays ({@code fabric:overlays}, fabric-resource-conditions-api-v1) as the game reads a pack's
 * metadata, with Fabric API (PACK-12), on packs written from the test mod's resources (fabricated_overlay_packs; as a
 * mod's own pack.mcmeta, a Fabric jar's would lose its NeoForge sections in translation): a native mod's pack built for
 * both loaders, whose fabric:overlays name a condition only its Fabric build registers, as Terralith's and Tectonic's do,
 * keeps the overlays the platform gives the same pack without that section; packs whose fabric:overlays can't be read
 * keep their other overlays (ci/checks.tsv counts the warning for each, as they are read twice); and a Fabric pack's
 * overlays apply where their conditions hold. The line names the native pack's overlays, NeoForge's or none on Forge.
 */
public final class PackOverlaysTest implements ModInitializer {
	private static final String CHECK = "pack overlays";
	private static final List<String> PACKS = List.of("native", "native_control", "unreadable", "misshapen", "fabric");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> check());
	}

	private static void check() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) {
			TestMod.LOGGER.info("FABRICATED-TEST {} SKIPPED: fabric-resource-conditions-api-v1 isn't installed", CHECK);
			return;
		}
		if (Fabricated.get().flatMap(api -> api.fabricApiModule("fabric-resource-conditions-api-v1"))
				.map(module -> module.status() == FabricApiModule.Status.PROVIDED_BY_MOD).orElse(false)) {
			TestMod.LOGGER.info("FABRICATED-TEST {} SKIPPED: fabric-resource-conditions-api-v1 is another mod's, which reads the overlays its own way", CHECK);
			return;
		}
		Path root = FabricLoader.getInstance().getGameDir().resolve("fabricated-testmod-overlay-packs");
		Map<String, @Nullable List<String>> read = new LinkedHashMap<>();
		List<String> failed = new ArrayList<>();
		try {
			for (String pack : PACKS) {
				Path folder = root.resolve(pack);
				Files.createDirectories(folder);
				try (InputStream metadata = Objects.requireNonNull(PackOverlaysTest.class.getResourceAsStream("/fabricated_overlay_packs/" + pack + ".json"), pack)) {
					Files.write(folder.resolve("pack.mcmeta"), metadata.readAllBytes());
				}
				read.put(pack, overlays(root, pack));
			}
			// As the game reads them again on a reload: no second warning.
			overlays(root, "unreadable");
			overlays(root, "misshapen");
		} catch (IOException e) {
			TestMod.LOGGER.error("FABRICATED-TEST {} FAILED: the packs weren't written", CHECK, e);
			return;
		}
		read.forEach((pack, overlays) -> {
			if (overlays == null) failed.add(pack + " has no metadata");
		});
		List<String> nativeOverlays = read.get("native");
		if (!Objects.equals(nativeOverlays, read.get("native_control"))) failed.add("native's overlays aren't the platform's alone");
		if (!List.of("vanilla_overlay").equals(read.get("unreadable"))) failed.add("unreadable keeps only its own overlay");
		if (!List.of("vanilla_overlay").equals(read.get("misshapen"))) failed.add("misshapen keeps only its own overlay");
		if (!List.of("fabric_holds").equals(read.get("fabric"))) failed.add("fabric applies the overlay whose condition holds");
		if (failed.isEmpty()) {
			TestMod.LOGGER.info("FABRICATED-TEST {} OK (native {})", CHECK, nativeOverlays);
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST {} FAILED: {} (read {})", CHECK, failed, read);
		}
	}

	private static @Nullable List<String> overlays(Path root, String pack) {
		return TestCompat.packOverlays("fabricated_testmod:" + pack, root.resolve(pack));
	}
}
