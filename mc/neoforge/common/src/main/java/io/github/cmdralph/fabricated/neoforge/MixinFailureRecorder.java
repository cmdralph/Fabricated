package io.github.cmdralph.fabricated.neoforge;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import io.github.cmdralph.fabricated.core.mixin.MixinFailures;
import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * Records a Fabric mod's mixin that fails as Mixin applies it, which pre-flight passed, for the next launch to leave
 * out (APPLY-1, core's {@link MixinFailures}). Mixin reports most failures through its error handlers, where
 * {@link Handler} sees them; an injector's critical failure ({@code InjectionError}) bypasses them, and is read off the
 * throwable as the game stops: from its crash report ({@code CrashReportMixin}) or, where it stops the game before
 * Minecraft makes one, from the thread's uncaught exception. Nothing here changes what Mixin does with a failure.
 */
public final class MixinFailureRecorder {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final AtomicBoolean INSTALLED = new AtomicBoolean();

	private MixinFailureRecorder() {
	}

	/** Registers {@link Handler} with Mixin and records from uncaught exceptions; before any mixin of a Fabric mod applies. */
	static void install() {
		if (!INSTALLED.compareAndSet(false, true)) return;
		Mixins.registerErrorHandlerClass(Handler.class.getName());
		Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, thrown) -> {
			record(thrown);
			if (previous != null) {
				previous.uncaughtException(thread, thrown);
			} else {
				// What the thread's group does without a default handler.
				System.err.print("Exception in thread \"" + thread.getName() + "\" ");
				thrown.printStackTrace(System.err);
			}
		});
	}

	/** Records the mixin a throwable (or one it holds) says failed, if it is a Fabric mod's. Never throws. */
	public static void record(Throwable thrown) {
		try {
			record(MixinFailures.find(thrown));
		} catch (RuntimeException | LinkageError e) {
			// It runs as the game stops: nothing here may stop it another way.
			LOGGER.debug("Could not read a mixin failure off {}", thrown, e);
		}
	}

	private static void record(MixinFailures.Named named) {
		FabricatedState state = FabricatedState.get();
		if (named == null || state == null) return;
		String recorded = state.recordMixinFailure(named);
		if (recorded != null) {
			LOGGER.error("Mixin {} failed as Mixin applied it ({}). Fabricated leaves it out from the next launch on, until the mod, Fabricated,"
					+ " Minecraft or NeoForge changes; please report it to Fabricated with logs/fabric-bridge-report.txt.", recorded, named.reason());
		}
	}

	/**
	 * Mixin's error handler for every failure it reports: records the failing mixin and leaves Mixin's action as it is
	 * (null). Mixin creates one for each failure, by name, through its service.
	 */
	public static final class Handler implements IMixinErrorHandler {
		public Handler() {
		}

		@Override
		public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
			seen(th, mixin);
			return null;
		}

		@Override
		public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
			seen(th, mixin);
			return null;
		}

		private static void seen(Throwable th, IMixinInfo mixin) {
			try {
				if (mixin == null || mixin.getConfig() == null) return;
				IMixinConfig config = mixin.getConfig();
				record(new MixinFailures.Named(config.getName() + ":" + mixin.getName(), FabricUtil.getModId(config),
						th == null ? "" : th.getClass().getSimpleName() + ": " + th.getMessage()));
			} catch (RuntimeException | LinkageError e) {
				LOGGER.debug("Could not record the failure of mixin {}", mixin, e);
			}
		}
	}
}
