package io.github.cmdralph.fabricated.forgetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.electronwill.nightconfig.core.file.FileWatcher;

import net.fabricmc.loader.api.FabricLoader;

/**
 * A Fabric mod watching its config file with night-config's default file watcher, as Forge Config API Port's Fabric
 * build does, which gets Forge's copy of night-config there (CORPUS-116): the file is changed until the watcher runs its
 * handler. Forge 61 to 66's night-config runs that handler on an executor thread that isn't a daemon, which on Forge 66,
 * whose client no longer exits once its main thread ends, kept the game up after it closed, until the client's shutdown
 * watchdog wrote a crash report: the client run's exit check ({@code client-exit}) fails then.
 */
final class ForgeConfigWatcherTest {
	/**
	 * How often the file is changed before the check gives up, each change given two seconds: the watcher runs its handler
	 * once a file has stayed unchanged for its debounce time (half a second).
	 */
	private static final int TRIES = 15;

	private ForgeConfigWatcherTest() {
	}

	static void start() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("fabricated-forge-testmod-watched.txt");
		CountDownLatch changed = new CountDownLatch(1);
		try {
			Files.writeString(file, "0");
			FileWatcher.defaultInstance().addWatch(file, changed::countDown);
		} catch (IOException | RuntimeException e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST config file watcher FAILED: the file can't be watched", e);
			return;
		}
		// The watcher registers the file on its own thread: the file is changed until it has seen a change.
		Thread writer = new Thread(() -> {
			try {
				for (int i = 1; i <= TRIES; i++) {
					Files.writeString(file, Integer.toString(i));
					if (changed.await(2, TimeUnit.SECONDS)) {
						ForgeTestMod.LOGGER.info("FABRICATED-TEST config file watcher OK");
						return;
					}
				}
				ForgeTestMod.LOGGER.error("FABRICATED-TEST config file watcher FAILED: its handler didn't run after {} changes", TRIES);
			} catch (IOException e) {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST config file watcher FAILED: the file can't be written", e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "fabricated-forge-testmod-watched-file");
		writer.setDaemon(true);
		writer.start();
	}
}
