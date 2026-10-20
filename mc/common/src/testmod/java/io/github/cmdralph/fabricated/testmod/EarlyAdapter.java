package io.github.cmdralph.fabricated.testmod;

import java.util.Map;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * A language adapter declared only to run code before Mixin starts, as some mods' are: Fabric Loader creates every
 * declared adapter first. {@link TestMixinPlugin} checks that it ran before the test mod's mixin plugin loaded.
 *
 * <p>For CI diagnostics it also says, when the game ends, which thread ended it and from where: a shutdown hook runs in
 * its own thread while the thread that called {@code System.exit} (or {@code DestroyJavaVM}, after the last game thread
 * ended) waits in {@code java.lang.Shutdown}. Printed to stdout, because logging may already be shut down.
 */
public final class EarlyAdapter implements LanguageAdapter {
	static final String PROPERTY = "fabricated.test.earlyAdapter";

	public EarlyAdapter() {
		System.setProperty(PROPERTY, "created");
		Runtime.getRuntime().addShutdownHook(new Thread(EarlyAdapter::printWhoEndedTheGame, "fabricated-testmod-shutdown"));
	}

	// Plain loops: a lambda here would spin a class while the game's class loaders may already be closed.
	private static void printWhoEndedTheGame() {
		for (Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
			boolean ending = false;
			for (StackTraceElement frame : thread.getValue()) ending |= frame.getClassName().equals("java.lang.Shutdown");
			if (!ending) continue;
			StringBuilder out = new StringBuilder("FABRICATED-TEST game ended from thread \"").append(thread.getKey().getName()).append("\":");
			for (StackTraceElement frame : thread.getValue()) out.append("\n\tat ").append(frame);
			System.out.println(out);
		}
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		throw new LanguageAdapterException("The test mod's early adapter creates no entrypoints");
	}
}
