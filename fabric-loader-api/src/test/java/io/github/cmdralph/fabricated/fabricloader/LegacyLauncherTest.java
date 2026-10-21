package io.github.cmdralph.fabricated.fabricloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.launch.common.FabricLauncherBase;

class LegacyLauncherTest {
	@AfterEach
	void forgetAppender() {
		FabricLoaderImpl.classPathAppender(path -> {
			throw new UnsupportedOperationException();
		});
	}

	/** LuckPerms adds each library it downloads with Fabric Loader's legacy launcher (LOADER-2). */
	@Test
	void aProposedJarJoinsTheClassPath() throws Exception {
		List<Path> added = new ArrayList<>();
		FabricLoaderImpl.classPathAppender(added::add);
		Path jar = Path.of("libs", "adventure-api.jar").toAbsolutePath();

		FabricLauncherBase.getLauncher().propose(jar.toUri().toURL());

		assertEquals(List.of(jar), added);
	}

	@Test
	void onlyAFileCanBeProposed() {
		FabricLoaderImpl.classPathAppender(path -> {
		});
		assertThrows(IllegalArgumentException.class, () -> FabricLauncherBase.getLauncher().propose(URI.create("https://example.com/lib.jar").toURL()));
	}
}
