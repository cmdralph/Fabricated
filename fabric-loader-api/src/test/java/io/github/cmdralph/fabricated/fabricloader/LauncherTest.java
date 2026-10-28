package io.github.cmdralph.fabricated.fabricloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;

class LauncherTest {
	/** OneConfig loads the entrypoint of a jar it added to the class path with the launcher's {@code loadIntoTarget}. */
	@Test
	void aClassLoadsIntoTheGamesLoader() throws Exception {
		assertEquals(LauncherTest.class, FabricLauncherBase.getLauncher().loadIntoTarget(LauncherTest.class.getName()));
		assertThrows(ClassNotFoundException.class, () -> FabricLauncherBase.getLauncher().loadIntoTarget("com.example.NotThere"));
	}
}
