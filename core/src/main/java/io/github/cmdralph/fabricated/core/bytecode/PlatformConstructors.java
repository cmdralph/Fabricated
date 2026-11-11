package io.github.cmdralph.fabricated.core.bytecode;

import java.util.List;
import java.util.Set;

/**
 * Game constructors a platform replaced with one taking vanilla's parameters and then a name (LOOT-3): {@code LootPool}'s,
 * whose name Forge 47 takes as a {@code String} and NeoForge (21.1 to 26.3) as an {@code Optional<String>}, neither keeping
 * vanilla's (read in their {@code LootPool} classes; Forge 52 and 61 to 66 keep it). A mod's call of vanilla's calls the
 * platform's with no name, as the platform's builder passes for a pool given none (null on Forge 47, an empty
 * {@code Optional} on NeoForge: their {@code LootPool$Builder.build}), and its widener's line for vanilla's opens the
 * platform's.
 */
public final class PlatformConstructors {
	/** The classes, by internal name. */
	public static final Set<String> NAMED = Set.of("net/minecraft/world/level/storage/loot/LootPool");
	/** The types a platform takes the name as. */
	private static final List<String> NAME_TYPES = List.of("Ljava/lang/String;", "Ljava/util/Optional;");

	private PlatformConstructors() {
	}

	/**
	 * The descriptor of the game's constructor of {@code owner} taking vanilla's {@code vanillaDesc} and a name, where the
	 * game has that one and not vanilla's; null otherwise.
	 */
	public static String of(ClassIndex game, String owner, String vanillaDesc) {
		if (game == null || !NAMED.contains(owner) || !vanillaDesc.endsWith(")V")) return null;
		ClassIndex.ClassInfo info = game.get(owner);
		if (info == null || info.methodAccess("<init>" + vanillaDesc) != null) return null;
		String parameters = vanillaDesc.substring(0, vanillaDesc.length() - 2);
		for (String type : NAME_TYPES) {
			if (info.methodAccess("<init>" + parameters + type + ")V") != null) return parameters + type + ")V";
		}
		return null;
	}

	/** Whether the platform's constructor ({@link #of}) takes the name as an {@code Optional}, else as a {@code String}. */
	public static boolean optionalName(String platformDesc) {
		return platformDesc.endsWith("Ljava/util/Optional;)V");
	}
}
