package io.github.cmdralph.fabricated.core.report;

import java.util.StringJoiner;

/**
 * How long each phase of a discovery took, for its log line and the report: a launch whose translations all come from
 * the cache still reads and checks what it hands the platform, and this says where that time goes. One thread only.
 */
public final class LaunchPhases {
	/** The report's section for them. */
	public static final String SECTION = "Launch timings";
	private final long start = System.nanoTime();
	private long last = start;
	private final StringJoiner phases = new StringJoiner(", ");

	/** Ends the phase running since the last call (or since this was made), under {@code name}. */
	public void end(String name) {
		long now = System.nanoTime();
		phases.add(name + " " + (now - last) / 1_000_000 + " ms");
		last = now;
	}

	/** The time since this was made. */
	public long totalMillis() {
		return (System.nanoTime() - start) / 1_000_000;
	}

	/** Each phase ended so far, with its time, in order. */
	@Override
	public String toString() {
		return phases.toString();
	}
}
