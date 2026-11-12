package io.github.cmdralph.fabricated.core.report;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;

/**
 * What Fabricated reports and can't fix: each known limit with the one line the report adds to what matches it, naming
 * its finding (COMPATIBILITY.md, FINDINGS.md). A pre-flight finding that leaves a hook out or disables its mod matches by
 * the platform and its message or suggestion, an entrypoint's failure by the class and message of what it threw, at the
 * root of its causes. Nothing else changes.
 */
public final class KnownLimits {
	/**
	 * @param loaders   the platforms a pre-flight finding matches on ({@code PlatformDescriptor.loaderName}); none for a failure
	 * @param exception the class of the failure's root cause it matches, or null for a pre-flight finding
	 * @param pattern   what the finding's or the exception's message holds; one with two groups matches only where they
	 *                  differ
	 */
	private record Limit(Set<String> loaders, String exception, Pattern pattern, String reason) {
	}

	private static final List<Limit> LIMITS = List.of(
			// A hook of vanilla's spawn checks, which the platforms make inside their spawn event's hook (Mojang's and SRG's names).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("target=\"Lnet/minecraft/world/entity/Mob;(?:checkSpawnRules|checkSpawnObstruction|m_5545_|m_6914_)\\("),
					"NeoForge and Forge run spawn checks through their own event, so Fabric hooks of those checks can't apply;"
							+ " use the mod's NeoForge or Forge build (SPAWN-1)"),
			// A wrapper of the screen's render call where Forge 65 and 66 draw the screen in ForgeLayerInstance.drawScreen.
			new Limit(Set.of("Forge"), null,
					Pattern.compile("in extractRenderState expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
							+ "extractRenderStateWithTooltipAndSubtitles\\("),
					"Forge draws screens through its own layer code, so wrappers of a screen's render call can't apply;"
							+ " use NeoForge or the mod's Forge build (SCREEN-2)"),
			// A hook of vanilla's playerWillDestroy call in the server's destroyBlock, which Forge makes from the block state's
			// onDestroyedByPlayer once the tool is damaged, and Fabricated doesn't put back there: pre-flight's hint names the
			// server's class (the client's method has the same name on Forge 52 on, and there the call goes back).
			new Limit(Set.of("Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;(?:playerWillDestroy|m_5707_)\\(.*Forge's"
							+ " ServerPlayerGameMode makes that call through the block's IForgeBlock\\.onDestroyedByPlayer instead", Pattern.DOTALL),
					"Forge calls playerWillDestroy from the block state's onDestroyedByPlayer, after the tool is damaged, so hooks of vanilla's call"
							+ " before it can't apply there; Fabricated puts back only the removal (SEAM-3)"),
			// A hook of vanilla's check of the held item in destroyBlock, which the platforms make inside their block break
			// event's hook, where a listener may overrule it: pre-flight's hint says so (NeoForge's client from 26.1.2 too).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/item/(?:Item;(?:canAttackBlock|m_6777_)|ItemStack;canDestroyBlock)\\("
							+ ".*asks the held item's (?:canAttackBlock|canDestroyBlock) inside its block break event", Pattern.DOTALL),
					"NeoForge and Forge ask the held item whether it can break the block inside their block break event, which a listener may"
							+ " overrule, so hooks of vanilla's check in destroyBlock can't apply there (SEAM-4)"),
			// A hook of vanilla's shears check in a mob's mobInteract, where the platforms shear the mob from the shears item
			// through their shearable extension: pre-flight's hint says so (Forge 65 and 66 ask an action there, which goes back).
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/item/ItemStack;(?:is|m_150930_)\\(.*'s mobInteract asks for no shears"
							+ " here: the platform shears it from the shears item", Pattern.DOTALL),
					"NeoForge and Forge shear a mob from the shears item, through their shearable extension, so hooks of vanilla's shears check in"
							+ " its mobInteract can't apply there (SEAM-6)"),
			// A hook of vanilla's map or crossbow check in the client's held item or item frame renderer, where the platforms ask
			// whether the item is of the item's class, which other mods' items are too: pre-flight's hint says so.
			new Limit(Set.of("NeoForge", "Forge"), null,
					Pattern.compile("expects @At\\(\"INVOKE\", target=\"Lnet/minecraft/world/item/ItemStack;(?:is|m_150930_)\\(.*(?:ItemInHandRenderer|ItemFrameRenderer)"
							+ " asks whether the (?:held|framed) item is a MapItem", Pattern.DOTALL),
					"NeoForge and Forge ask whether the item is a map (or crossbow) item there, which other mods' items are too, so hooks of vanilla's"
							+ " check of the vanilla item can't apply there (SEAM-6)"),
			// ForgeRegistry.add, whose maps key a value by its equality: two objects, by their identity hashes, not one object
			// under two names.
			new Limit(Set.of(), IllegalArgumentException.class.getName(),
					Pattern.compile("\\{(\\p{XDigit}+)} has been registered twice, using the names .*\\{(\\p{XDigit}+)}\\)$", Pattern.DOTALL),
					"Forge's registries take two equal entries for one, so the mod's second one can't be registered;"
							+ " use NeoForge or the mod's Forge build (REG-13)"));

	private KnownLimits() {
	}

	/**
	 * The known limit a pre-flight finding is on {@code loader}, as the report says it, or null; only a finding that leaves
	 * its hook out or disables its mod is one, never a warning (a {@code @Group}'s member whose group applies).
	 */
	public static String ofFinding(String loader, PreflightFinding finding) {
		if (finding.severity() != Severity.LEFT_OUT && finding.severity() != Severity.FAIL) return null;
		for (Limit limit : LIMITS) {
			if (limit.exception() == null && limit.loaders().contains(loader) && matches(limit, finding.message() + "\n" + finding.suggestion())) {
				return "Known limit: " + limit.reason();
			}
		}
		return null;
	}

	/** The known limit an entrypoint's failure is, from the root of its causes, or null. */
	public static String ofFailure(Throwable root) {
		String message = root.getMessage();
		for (Limit limit : LIMITS) {
			if (root.getClass().getName().equals(limit.exception()) && message != null && matches(limit, message)) return "Known limit: " + limit.reason();
		}
		return null;
	}

	private static boolean matches(Limit limit, String message) {
		Matcher m = limit.pattern().matcher(message);
		return m.find() && (m.groupCount() < 2 || !m.group(1).equals(m.group(2)));
	}
}
