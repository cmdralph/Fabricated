package io.github.cmdralph.fabricated.core.bytecode;

import java.util.List;

/**
 * The packages of Minecraft's own code under the names the platforms run it with (Mojang's): {@code net.minecraft}, and
 * the packages of Mojang's own the game's jars hold, {@code com.mojang.blaze3d}, {@code com.mojang.math} and
 * {@code com.mojang.realmsclient}, with 26.3's {@code com.mojang.renderpearl} (its GPU backend). Mojang's libraries
 * (Brigadier, DataFixerUpper, authlib) are not the game's.
 * <p>
 * CORPUS-82: pre-flight knew only {@code net.minecraft} and Blaze3D, so it took 26.3's {@code GlCommandEncoder} for
 * another mod's class and judged no mixin of it: ImmediatelyFast's hook there, which takes an argument by its name, kept
 * that name where Forge 66's Mixin doesn't know it (PRE-18), and the client stopped.
 */
public final class GamePackages {
	private static final List<String> GAME = List.of("net/minecraft/", "com/mojang/blaze3d/", "com/mojang/math/", "com/mojang/realmsclient/",
			"com/mojang/renderpearl/");

	/** The game's packages a dedicated server lacks. */
	private static final List<String> CLIENT = List.of("net/minecraft/client/", "com/mojang/blaze3d/", "com/mojang/realmsclient/",
			"com/mojang/renderpearl/");

	private GamePackages() {
	}

	/** Whether a class (internal name) is the game's own code. */
	public static boolean isGame(String internalName) {
		return startsWithAny(internalName, GAME);
	}

	/** Whether a class (internal name) is the game's client-only code. */
	public static boolean isClient(String internalName) {
		return startsWithAny(internalName, CLIENT);
	}

	/** Whether a descriptor names a class of the game's client-only code. */
	public static boolean mentionsClient(String descriptor) {
		if (descriptor == null) return false;
		for (String prefix : CLIENT) {
			if (descriptor.contains("L" + prefix)) return true;
		}
		return false;
	}

	private static boolean startsWithAny(String internalName, List<String> prefixes) {
		if (internalName == null) return false;
		for (String prefix : prefixes) {
			if (internalName.startsWith(prefix)) return true;
		}
		return false;
	}
}
