package io.github.cmdralph.fabricated.forge.diagnostics;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.LoadingFailedException;
import net.minecraftforge.fml.ModLoadingException;

/**
 * Mod loading's errors, for the crash report Forge makes of them: it holds a placeholder exception, the errors being
 * only its categories' text ({@code CrashReportExtender.dumpModLoadingCrashReport}, on Forge 43 to 66's servers and
 * clients). The dump hands them here ({@code CrashReportExtenderMixin}), and the report it makes next on its thread
 * takes their causes ({@code CrashReportMixin}): an injector's critical failure as a mod's constructor loads the class
 * reaches the report only that way (APPLY-1).
 */
public final class LoadingErrors {
	private static final ThreadLocal<List<Throwable>> HELD = new ThreadLocal<>();

	private LoadingErrors() {
	}

	/** Holds the causes of {@code error}'s errors for the next crash report this thread makes. Never throws. */
	public static void hold(LoadingFailedException error) {
		try {
			List<Throwable> causes = new ArrayList<>();
			for (ModLoadingException e : error.getErrors()) causes.add(e.getCause() != null ? e.getCause() : e);
			HELD.set(causes);
		} catch (RuntimeException | LinkageError e) {
			// It runs as the game stops: nothing here may stop it another way.
		}
	}

	/** The causes held for this thread's crash report, taken: none where none are. */
	public static List<Throwable> take() {
		List<Throwable> held = HELD.get();
		HELD.remove();
		return held == null ? List.of() : held;
	}
}
