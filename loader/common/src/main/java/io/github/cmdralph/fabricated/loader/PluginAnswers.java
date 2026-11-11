package io.github.cmdralph.fabricated.loader;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * What Fabric mods' mixin config plugins answer Mixin as it selects their configs' mixins (LEAVE-4). Translation makes
 * the plugin of a config pre-flight left mixins out of call {@link #answered} with each answer ({@code ObservedPlugins}),
 * and the report then lists the mixins a plugin turned down apart from those pre-flight left out: on Fabric the plugin
 * leaves them out itself. Nothing here asks a plugin anything: Mixin does, as it would on Fabric.
 */
public final class PluginAnswers {
	/** Each mixin a plugin was asked about, and whether it ever answered to apply it. */
	private static final Map<String, Boolean> APPLIED = new ConcurrentHashMap<>();

	private PluginAnswers() {
	}

	/**
	 * Called from a Fabric mod's plugin, as translated, with what its {@code shouldApplyMixin} answered Mixin for
	 * {@code mixin}: records it, and returns it, but false for the mixins pre-flight left out whole ({@code leftOut}, one
	 * class name a line), which stay listed in the config for Mixin to ask the plugin about.
	 */
	public static boolean answered(boolean apply, String mixin, String leftOut) {
		if (mixin == null) return apply;
		APPLIED.merge(mixin, apply, Boolean::logicalOr);
		return apply && !("\n" + leftOut + "\n").contains("\n" + mixin + "\n");
	}

	/** The mixins a plugin was asked about and never answered to apply, by class name. */
	public static Set<String> rejected() {
		return APPLIED.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
	}
}
