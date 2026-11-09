package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.locale.Language;

/**
 * CORPUS-100: a Fabric mod's language file is read on the server by vanilla's reader, where Fabric mods' hooks into it
 * apply (owo-lib's lets its files hold rich text), and its strings are in the server's language. Forge 47's server read
 * every mod's language file with its own copy of the reader, which no Fabric mod hooks, and refused owo's whole file;
 * Forge 52's needs no fix. The test mod's hook into vanilla's reader ({@code LanguageReaderMixin}) watches for its key,
 * which only its own language file has.
 */
public final class ForgeLanguageTest {
	public static final String KEY = "fabricated-forge-testmod.language";

	/** Set when vanilla's reader reads the test mod's key. */
	public static volatile boolean readByVanilla;

	private ForgeLanguageTest() {
	}

	/** At the first server tick, once the server read the mods' language files. */
	public static void check() {
		boolean known = Language.getInstance().has(KEY);
		if (readByVanilla && known) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST language files OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST language files FAILED: the test mod's key read by vanilla's reader {}, in the server's language {}",
					readByVanilla, known);
		}
	}
}
