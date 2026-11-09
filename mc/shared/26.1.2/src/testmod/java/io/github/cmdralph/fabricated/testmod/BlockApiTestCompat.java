package io.github.cmdralph.fabricated.testmod;

import net.minecraft.server.level.ServerLevel;

/** 26.1.2's block API has no enchanting power or fluid flow events: nothing more to test. */
final class BlockApiTestCompat {
	private BlockApiTestCompat() {
	}

	static void init() {
	}

	static String check(ServerLevel level) {
		return null;
	}
}
