package io.github.cmdralph.fabricated.core.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class LazyMappingTableTest {
	@Test
	void loadsOnceWhenFirstAsked() throws Exception {
		MappingTable table = MappingTable.fromMembers(Map.of("net/minecraft/class_1", "net/minecraft/world/Level"), Map.of());
		AtomicInteger loads = new AtomicInteger();
		LazyMappingTable lazy = new LazyMappingTable(() -> {
			loads.incrementAndGet();
			return table;
		});
		assertNull(lazy.loaded());
		assertEquals(0, loads.get());
		assertSame(table, lazy.table());
		assertSame(table, lazy.table());
		assertSame(table, lazy.loaded());
		assertEquals(1, loads.get());
	}

	/** A failure is reported at every use, without loading again (mods translate in parallel). */
	@Test
	void reportsAFailureWithoutLoadingAgain() {
		AtomicInteger loads = new AtomicInteger();
		LazyMappingTable lazy = new LazyMappingTable(() -> {
			loads.incrementAndGet();
			throw new IOException("blob unreadable");
		});
		MappingException first = assertThrows(MappingException.class, lazy::table);
		assertTrue(first.getMessage().contains("blob unreadable"), first.getMessage());
		assertThrows(MappingException.class, lazy::table);
		assertEquals(1, loads.get());
		assertNull(lazy.loaded());
		assertThrows(MappingException.class, new LazyMappingTable(() -> null)::table);
	}
}
