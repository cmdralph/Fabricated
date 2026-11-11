package io.github.cmdralph.fabricated.core.report;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What Fabricated reports and can't fix: each known limit with the one line the report adds to what matches it, naming
 * its finding (COMPATIBILITY.md, FINDINGS.md). A pre-flight finding matches by the platform and its message, an
 * entrypoint's failure by the class and message of what it threw, at the root of its causes. Nothing else changes.
 */
public final class KnownLimits {
	/**
	 * @param loaders   the platforms a pre-flight finding matches on ({@code PlatformDescriptor.loaderName}); none for a failure
	 * @param exception the class of the failure's root cause it matches, or null for a pre-flight finding
	 * @param pattern   what the finding's or the exception's message holds
	 */
	private record Limit(Set<String> loaders, String exception, Pattern pattern, String reason) {
	}

	private static final List<Limit> LIMITS = List.of(
			// A hook of vanilla's spawn checks, which the platforms make inside their spawn event's hook (Mojang's and SRG's names).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("target=\"Lnet/minecraft/world/entity/Mob;(checkSpawnRules|checkSpawnObstruction|m_5545_|m_6914_)\\("),
					"NeoForge and Forge run spawn checks through their own event, so Fabric hooks of those checks can't apply;"
							+ " use the mod's NeoForge or Forge build (SPAWN-1)"),
			// A wrapper of the screen's render call where Forge 65 and 66 draw the screen in ForgeLayerInstance.drawScreen.
			new Limit(Set.of("Forge"), null,
					Pattern.compile("in extractRenderState expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
							+ "extractRenderStateWithTooltipAndSubtitles\\("),
					"Forge draws screens through its own layer code, so wrappers of a screen's render call can't apply;"
							+ " use NeoForge or the mod's Forge build (SCREEN-2)"),
			// ForgeRegistry.add, whose maps key a value by its equality.
			new Limit(Set.of(), IllegalArgumentException.class.getName(), Pattern.compile("has been registered twice, using the names "),
					"Forge's registries take two equal entries for one, so the mod's second one can't be registered;"
							+ " use NeoForge or the mod's Forge build (REG-13)"));

	private KnownLimits() {
	}

	/** The known limit a pre-flight finding's message is on {@code loader}, as the report says it, or null. */
	public static String ofFinding(String loader, String message) {
		for (Limit limit : LIMITS) {
			if (limit.exception() == null && limit.loaders().contains(loader) && limit.pattern().matcher(message).find()) return "Known limit: " + limit.reason();
		}
		return null;
	}

	/** The known limit an entrypoint's failure is, from the root of its causes, or null. */
	public static String ofFailure(Throwable root) {
		String message = root.getMessage();
		for (Limit limit : LIMITS) {
			if (root.getClass().getName().equals(limit.exception()) && message != null && limit.pattern().matcher(message).find()) {
				return "Known limit: " + limit.reason();
			}
		}
		return null;
	}
}
