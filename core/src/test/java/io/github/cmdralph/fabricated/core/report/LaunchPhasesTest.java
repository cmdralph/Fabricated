package io.github.cmdralph.fabricated.core.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class LaunchPhasesTest {
	/** What another thread allocates counts, as the common pool's threads do while the pipeline translates. */
	@Test
	void totalsCountWhatOtherThreadsAllocate() throws InterruptedException {
		LaunchPhases phases = new LaunchPhases();
		byte[][] kept = new byte[16][];
		CountDownLatch allocated = new CountDownLatch(1);
		CountDownLatch measured = new CountDownLatch(1);
		Thread worker = new Thread(() -> {
			for (int i = 0; i < kept.length; i++) kept[i] = new byte[1024 * 1024];
			allocated.countDown();
			try {
				measured.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		worker.start();
		allocated.await();
		String totals = phases.totals();
		measured.countDown();
		worker.join();
		// ci/bench.py reads this from discovery's log line.
		Matcher m = Pattern.compile("\\d+ ms, (\\d+) MB allocated").matcher(totals);
		assertTrue(m.matches(), totals);
		assertTrue(Long.parseLong(m.group(1)) >= kept.length, totals);
	}
}
