package io.github.cmdralph.fabricated.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;

/**
 * The causes of mod loading's errors so far. NeoForge's crash report for a mod loading that failed holds a placeholder
 * exception, the failures being its errors' causes ({@code CrashReportExtender.dumpModLoadingCrashReport}): an
 * injector's critical failure as a mod's constructor loads the class reaches the report only that way, as on NeoForge
 * 1.21.11 and 26.x's dedicated servers, where Fabricated's own constructor loads {@code MinecraftServer}
 * ({@code CrashReportMixin}, APPLY-1).
 */
public final class LoadingErrors {
	private LoadingErrors() {
	}

	/** The causes, none where they can't be read. Never throws. */
	public static List<Throwable> causes() {
		List<Throwable> causes = new ArrayList<>();
		try {
			for (ModLoadingIssue issue : ModLoader.getLoadingErrors()) {
				if (issue.cause() != null) causes.add(issue.cause());
			}
		} catch (RuntimeException | LinkageError e) {
			// It runs as the game stops: nothing here may stop it another way.
		}
		return causes;
	}
}
