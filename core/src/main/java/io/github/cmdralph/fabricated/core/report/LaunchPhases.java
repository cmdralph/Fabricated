package io.github.cmdralph.fabricated.core.report;

import java.lang.management.ManagementFactory;
import java.util.StringJoiner;

/**
 * How long each phase of a discovery took, and what the JVM's threads allocated meanwhile, for its log line and the
 * report: a launch whose translations all come from the cache still reads and checks what it hands the platform, and
 * this says where that time goes. The allocations, unlike the time, hold from one machine to the next, so CI's
 * benchmark gates on them (ci/bench.py). One thread only.
 */
public final class LaunchPhases {
	/** The report's section for them. */
	public static final String SECTION = "Launch timings";
	/** The JVM's count of what each thread allocated, null where it keeps none. */
	private static final com.sun.management.ThreadMXBean THREADS = threads();
	private final long start = System.nanoTime();
	private final long allocatedAtStart = allocated();
	private long last = start;
	private final StringJoiner phases = new StringJoiner(", ");

	/** Ends the phase running since the last call (or since this was made), under {@code name}. */
	public void end(String name) {
		long now = System.nanoTime();
		phases.add(name + " " + (now - last) / 1_000_000 + " ms");
		last = now;
	}

	/** The time since this was made, and what the JVM's threads allocated meanwhile: "812 ms, 95 MB allocated". */
	public String totals() {
		long millis = (System.nanoTime() - start) / 1_000_000;
		return millis + " ms" + (THREADS == null ? "" : ", " + (allocated() - allocatedAtStart) / (1024 * 1024) + " MB allocated");
	}

	/** Each phase ended so far, with its time, in order. */
	@Override
	public String toString() {
		return phases.toString();
	}

	/**
	 * The bytes the live threads allocated since each started: the pipeline translates on the common pool's threads
	 * too, which this thread's count leaves out (and which stay alive a minute once idle).
	 */
	private static long allocated() {
		if (THREADS == null) return 0;
		long sum = 0;
		// -1 for a thread that ended since its id was read.
		for (long bytes : THREADS.getThreadAllocatedBytes(THREADS.getAllThreadIds())) sum += Math.max(bytes, 0);
		return sum;
	}

	private static com.sun.management.ThreadMXBean threads() {
		try {
			if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()
					&& bean.isThreadAllocatedMemoryEnabled()) return bean;
		} catch (LinkageError e) {
			// A JVM without the jdk.management module.
		}
		return null;
	}
}
