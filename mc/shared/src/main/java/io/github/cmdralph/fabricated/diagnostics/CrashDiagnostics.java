package io.github.cmdralph.fabricated.diagnostics;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import io.github.cmdralph.fabricated.core.mixin.MixinFailures;
import io.github.cmdralph.fabricated.core.report.DiagnosticsBlock;
import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * What every platform's in-game mod does as the game stops on a throwable, from the places that see it: Mixin's error
 * handlers ({@link Handler}), a thread's uncaught exception, the game's crash reports and its worker threads' failures
 * ({@code Util.onThreadException}), each through a thin hook of the platform's. A Fabric mod's mixin the throwable says
 * failed as Mixin applied it, which pre-flight passed, is recorded for the next launch to leave out (APPLY-1,
 * {@link FabricatedState#diagnose}), and the diagnostics block naming the Fabric mods involved ({@link DiagnosticsBlock})
 * goes to the log, once for each mod's mixin or class, and to the crash report. Nothing here changes what the game or
 * Mixin does with the throwable, and nothing here throws; a failure while it runs on a thread (a class it loads failing
 * too) is left alone.
 */
public final class CrashDiagnostics {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final AtomicBoolean INSTALLED = new AtomicBoolean();
	/** Set while this thread diagnoses a failure. */
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
	/** The blocks logged so far, by the mod and mixin or class they name first: each is logged once. */
	private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
	private static final int MAX_LOGGED = 16;
	/** The decoration Fabric's Mixin, which NeoForge runs, gives a mod's config: its mod's id ({@code FabricUtil.KEY_MOD_ID}). */
	private static final String MOD_ID = "fabric-modId";

	private CrashDiagnostics() {
	}

	/**
	 * Registers {@link Handler} with Mixin and sees the threads' uncaught exceptions: before any mixin of a Fabric mod
	 * applies (Fabricated's mixin config plugin).
	 */
	public static void install() {
		if (!INSTALLED.compareAndSet(false, true)) return;
		Mixins.registerErrorHandlerClass(Handler.class.getName());
		Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, thrown) -> {
			stopped(thrown);
			if (previous != null) {
				previous.uncaughtException(thread, thrown);
			} else {
				// What the thread's group does without a default handler.
				System.err.print("Exception in thread \"" + thread.getName() + "\" ");
				thrown.printStackTrace(System.err);
			}
		});
	}

	/** Records the mixin failure a throwable names, for a failure the game goes on past (a Fabric entrypoint's). */
	public static void record(Throwable thrown) {
		FabricatedState state = FabricatedState.get();
		if (state == null || BUSY.get()) return;
		BUSY.set(true);
		try {
			state.recordMixinFailure(MixinFailures.find(thrown));
		} catch (RuntimeException | LinkageError e) {
			LOGGER.debug("Could not read a mixin failure off {}", thrown, e);
		} finally {
			BUSY.set(false);
		}
	}

	/** A throwable a thread stops on, or that the game's workers hand {@code Util.onThreadException}. */
	public static void stopped(Throwable thrown) {
		log(diagnose(null, thrown, List.of()));
	}

	/**
	 * A crash report's throwable, with those the platform holds beside it ({@code more}: mod loading's errors, which a
	 * crash report for them holds only as text): returns the block for the report, or null.
	 */
	public static String crashReport(Throwable thrown, List<Throwable> more) {
		String block = diagnose(null, thrown, more);
		log(block);
		return block;
	}

	private static String diagnose(MixinFailures.Named named, Throwable thrown, List<Throwable> more) {
		FabricatedState state = FabricatedState.get();
		if (state == null || BUSY.get()) return null;
		BUSY.set(true);
		try {
			return named != null ? state.diagnose(named, thrown, more) : state.diagnose(thrown, more);
		} finally {
			BUSY.set(false);
		}
	}

	/** Logs a block once for the mod and mixin or class it names first, at most {@link #MAX_LOGGED}. */
	private static void log(String block) {
		if (block == null) return;
		try {
			String[] lines = block.split("\n", 4);
			String first = lines.length > 2 ? lines[1] + "\n" + lines[2] : block;
			if (LOGGED.size() < MAX_LOGGED && LOGGED.add(first)) LOGGER.error("{}", block);
		} catch (RuntimeException e) {
			// Nothing here may stop the game another way.
		}
	}

	/**
	 * Mixin's error handler for every failure it reports: records the failing mixin, with the class it applied it to,
	 * and logs the block where Mixin stops the game for it; Mixin's action stays as it is (null). Mixin creates one for
	 * each failure, by name, through its service.
	 */
	public static final class Handler implements IMixinErrorHandler {
		public Handler() {
		}

		@Override
		public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
			seen(null, th, mixin, action);
			return null;
		}

		@Override
		public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
			seen(targetClassName, th, mixin, action);
			return null;
		}

		private static void seen(String target, Throwable th, IMixinInfo mixin, ErrorAction action) {
			try {
				IMixinConfig config = mixin == null ? null : mixin.getConfig();
				if (config == null) return;
				// A failure as Mixin prepares a mixin is no one target's: the mixin's own, where it has one.
				List<String> targets = target == null ? mixin.getTargetClasses() : null;
				if (targets != null && targets.size() == 1) target = targets.get(0);
				Object modId = config.hasDecoration(MOD_ID) ? config.<Object>getDecoration(MOD_ID) : null;
				MixinFailures.Named named = new MixinFailures.Named(config.getName() + ":" + mixin.getName(), modId instanceof String id ? id : null,
						th == null ? "" : th.getClass().getSimpleName() + ": " + th.getMessage(), target);
				String block = diagnose(named, th, List.of());
				// Mixin goes on past a warning; an error stops the class from loading, and the game with it.
				if (action == ErrorAction.ERROR) log(block);
			} catch (RuntimeException | LinkageError e) {
				LOGGER.debug("Could not record the failure of mixin {}", mixin, e);
			}
		}
	}
}
