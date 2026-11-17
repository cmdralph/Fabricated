package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.resource.pack.FabricPack;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

/**
 * What the test mod's mixin of upstream's mod resource pack creator ({@code ModResourcePackCreatorMixin}) checks from
 * 1.21.11 on (PROVIDER-22): its hook at the end of {@code loadPacks} of a creator made for server data ran, and once the
 * server has started, its pack repository has the test mod's built-in data pack, which that creator offers. Then what
 * mods call of the native resource loader's internals (PROVIDER-23): a creator made with the pack type alone, as REI's
 * Fabric build makes one to read the mods' data packs, offers the packs holding the test mod's data and its built-in
 * pack; and a pack's {@link FabricPack} side, through which Puzzles Lib hides its packs, hides it as NeoForge hides its
 * own.
 */
public final class PackCreatorHookTest {
	private static final String BUILTIN_PACK = "fabricated_testmod:extra";
	/** A data file of the test mod's jar, which only its mod pack holds. */
	private static final Identifier OWN_DATA = TestCompat.id("fabricated_testmod", "fabricated_test/hello.json");
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
			checkInternals();
		});
	}

	private static void checkInternals() {
		if (TestMod.providedByAnotherMod("fabric-resource-loader-v1")) {
			TestMod.LOGGER.info("FABRICATED-TEST resource loader internals SKIPPED: fabric-resource-loader-v1 is another mod's");
			return;
		}
		List<String> failed = new ArrayList<>();
		List<Pack> offered = new ArrayList<>();
		try {
			new ModResourcePackCreator(PackType.SERVER_DATA).loadPacks(offered::add);
		} catch (RuntimeException | LinkageError e) {
			failed.add("a creator made with the pack type alone fails: " + e);
		}
		boolean ownData = false;
		Pack builtin = null;
		for (Pack pack : offered) {
			if (pack.getId().equals(BUILTIN_PACK)) builtin = pack;
			try (PackResources resources = pack.open()) {
				ownData |= resources.getResource(PackType.SERVER_DATA, OWN_DATA) != null;
			}
		}
		if (!offered.isEmpty() && !ownData) failed.add("none of its " + offered.size() + " packs holds the test mod's " + OWN_DATA);
		if (!offered.isEmpty() && builtin == null) failed.add("it offers no " + BUILTIN_PACK);
		if (builtin != null) {
			FabricPack fabric = (FabricPack) (Object) builtin;
			if (fabric.fabric$isHidden() || builtin.isHidden()) failed.add("the built-in pack is hidden before a mod hides it");
			fabric.fabric$setParentsPredicate(enabled -> false);
			if (!fabric.fabric$isHidden()) failed.add("a pack whose parents predicate a mod set isn't hidden as Fabric API reads it");
			if (!builtin.isHidden()) failed.add("a pack a mod hid isn't hidden as NeoForge reads it");
			if (fabric.fabric$parentsEnabled(Set.of())) failed.add("its parents predicate isn't the one the mod set");
		}
		if (failed.isEmpty()) {
			TestMod.LOGGER.info("FABRICATED-TEST resource loader internals OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST resource loader internals FAILED: {}", String.join("; ", failed));
		}
	}
}
