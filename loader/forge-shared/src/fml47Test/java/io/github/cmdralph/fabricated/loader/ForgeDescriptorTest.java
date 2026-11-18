package io.github.cmdralph.fabricated.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Forge 43's and 47's descriptors, which the loader picks by the game's Minecraft version. */
class ForgeDescriptorTest {
	/** PLAT-8: Minecraft ships JOML from 1.19.3 only, so on Forge 43 a Fabric mod's copy (Sodium 0.4's) is the mod's. */
	@Test
	void forge43sGameLacksJomlAndForge47sLacksNothing() {
		assertEquals(List.of("org/joml/"), ForgeDescriptor.FORGE_43.absentLibraryPackages());
		assertEquals(List.of(), ForgeDescriptor.FORGE_47.absentLibraryPackages());
	}

	@Test
	void eachMinecraftVersionGetsItsForgesDescriptor() {
		assertSame(ForgeDescriptor.FORGE_43, ForgeDescriptor.of("1.19.2"));
		assertSame(ForgeDescriptor.FORGE_47, ForgeDescriptor.of("1.20.1"));
	}
}
