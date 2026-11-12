package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ClassBytesSourceTest {
	/** A memoised source answers as the source does, reading each class once, a class it hasn't too. */
	@Test
	void memoisedReadsEachClassOnce() throws Exception {
		Map<String, AtomicInteger> reads = new ConcurrentHashMap<>();
		ClassBytesSource source = name -> {
			reads.computeIfAbsent(name, n -> new AtomicInteger()).incrementAndGet();
			return switch (name) {
				case "a/A" -> new byte[] {1, 2, 3};
				case "a/Empty" -> new byte[0];
				default -> null;
			};
		};
		ClassBytesSource memoised = source.memoised();
		byte[] first = memoised.read("a/A");
		assertArrayEquals(new byte[] {1, 2, 3}, first);
		assertSame(first, memoised.read("a/A"));
		assertNull(memoised.read("a/Missing"));
		assertNull(memoised.read("a/Missing"));
		assertArrayEquals(new byte[0], memoised.read("a/Empty"), "an empty class file is no missing class");
		assertArrayEquals(new byte[0], memoised.read("a/Empty"));
		assertEquals(1, reads.get("a/A").get());
		assertEquals(1, reads.get("a/Missing").get());
		assertEquals(1, reads.get("a/Empty").get());
	}

	/** A read that fails is not kept: the next read tries again. */
	@Test
	void memoisedKeepsNoFailure() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		ClassBytesSource memoised = ((ClassBytesSource) name -> {
			if (reads.incrementAndGet() == 1) throw new IOException("busy");
			return new byte[] {4};
		}).memoised();
		assertThrows(IOException.class, () -> memoised.read("a/A"));
		assertArrayEquals(new byte[] {4}, memoised.read("a/A"));
		assertArrayEquals(new byte[] {4}, memoised.read("a/A"));
		assertEquals(2, reads.get());
	}

	/** Threads reading a class first at once all get the one copy kept. */
	@Test
	void memoisedGivesThreadsOneCopy() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ClassBytesSource memoised = ((ClassBytesSource) name -> {
			try {
				start.await();
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
			return name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}).memoised();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<byte[]>> reads = new ArrayList<>();
			for (int i = 0; i < 8; i++) reads.add(pool.submit(() -> memoised.read("a/A")));
			start.countDown();
			byte[] kept = memoised.read("a/A");
			for (Future<byte[]> read : reads) assertSame(kept, read.get());
		} finally {
			pool.shutdownNow();
		}
	}
}
