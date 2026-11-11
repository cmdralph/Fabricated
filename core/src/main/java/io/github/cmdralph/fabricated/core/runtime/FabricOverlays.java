package io.github.cmdralph.fabricated.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Fabric API's conditional overlays ({@value #SECTION}, from fabric-resource-conditions-api-v1) of a pack the game reads
 * (PACK-12), which the platform's in-game mod applies in place of upstream's hook on the game's reading of a pack's
 * metadata:
 * <ul>
 * <li>A pack that declares the platform's own conditional overlays ({@value #NEOFORGE_SECTION} on NeoForge) is the
 * platform's, and its {@value #SECTION} isn't read: jars built for both loaders carry both, each gated by conditions
 * only that loader's build registers (Tectonic's, Terralith's). A Fabric jar loses its NeoForge sections as it is
 * translated (PACK-4), so a Fabric mod's pack never declares them.</li>
 * <li>A section that can't be read (a condition type nothing registers, a wrong shape) is left out, with one warning per
 * pack and reason; the pack keeps its other overlays. Upstream's hook made the whole pack's metadata unreadable there.</li>
 * <li>Otherwise the overlays whose conditions hold follow the pack's own, as upstream orders them.</li>
 * </ul>
 */
public final class FabricOverlays {
	/** Fabric API's section of a pack's metadata. */
	public static final String SECTION = "fabric:overlays";
	/** NeoForge's own conditional overlays, decided by its conditions. */
	public static final String NEOFORGE_SECTION = "neoforge:overlays";

	private final String platformSection;
	private final Set<String> warned = ConcurrentHashMap.newKeySet();

	/** @param platformSection the platform's own conditional overlays' section, or null where it has none (Forge) */
	public FabricOverlays(String platformSection) {
		this.platformSection = platformSection;
	}

	/**
	 * The overlays of a pack, with Fabric API's after them.
	 *
	 * @param pack     the pack's id, for the warning
	 * @param overlays the overlays the platform read from the pack's metadata
	 * @param sections the top-level sections of the pack's metadata
	 * @param applied  the overlays of the pack's {@value #SECTION} whose conditions hold; throws when the section can't
	 *                 be read
	 * @param warnings receives the warning for a section that can't be read, the first time for the pack and reason
	 */
	public List<String> overlays(String pack, List<String> overlays, Set<String> sections, Callable<List<String>> applied, Consumer<String> warnings) {
		if (!sections.contains(SECTION) || platformSection != null && sections.contains(platformSection)) return overlays;
		List<String> fabric;
		try {
			fabric = Objects.requireNonNull(applied.call(), "no overlays");
		} catch (Exception e) {
			String reason = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
			if (warned.add(pack + '\n' + reason)) {
				warnings.accept("Pack " + pack + ": its " + SECTION + " can't be read, so none of them apply (" + reason + ")");
			}
			return overlays;
		}
		if (fabric.isEmpty()) return overlays;
		List<String> all = new ArrayList<>(overlays);
		all.addAll(fabric);
		return List.copyOf(all);
	}
}
