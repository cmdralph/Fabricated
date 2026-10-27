package io.github.cmdralph.fabricated.forgetest;

/**
 * fabric-rendering-v1's colour provider lookup is replaced on Forge 61 only (COLOR-1): 26.x's block colours have no
 * id-indexed map for upstream's to read, and Forge 64 to 66 leave upstream's as it is.
 */
public final class ForgeColorTest {
	private ForgeColorTest() {
	}

	public static void check() {
	}
}
