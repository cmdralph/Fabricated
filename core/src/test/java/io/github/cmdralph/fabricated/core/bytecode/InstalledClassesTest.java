package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.archive.Hashing;

class InstalledClassesTest {
	@Test
	void theFirstJarWithAClassOwnsItAndItIsReadOnce() throws Exception {
		byte[] api = {1};
		byte[] mod = {2};
		AtomicInteger reads = new AtomicInteger();
		Map<String, byte[]> modClasses = Map.of("a/Shared", mod, "a/Own", mod);
		InstalledClasses installed = new InstalledClasses(List.of(
				new InstalledClasses.Jar("Fabric API", "a/Shared"::equals, name -> api),
				new InstalledClasses.Jar("Sodium", modClasses::containsKey, name -> {
					reads.incrementAndGet();
					return modClasses.get(name);
				})));

		assertEquals("Fabric API", installed.owner("a/Shared"));
		assertArrayEquals(api, installed.read("a/Shared"));
		assertEquals("Sodium", installed.owner("a/Own"));
		assertArrayEquals(mod, installed.read("a/Own"));
		assertArrayEquals(mod, installed.read("a/Own"));
		assertEquals(1, reads.get(), "a class is read from its jar once");
		assertEquals(Hashing.sha256(mod), installed.fingerprint("a/Own"));
		assertNull(installed.owner("a/Absent"));
		assertNull(installed.read("a/Absent"));
		assertEquals("", installed.fingerprint("a/Absent"));
	}

	/** Names come from untrusted mixins; only a class name reaches a jar (a NeoForge mod in development is a folder). */
	@Test
	void onlyClassNamesReachTheJars() throws Exception {
		InstalledClasses installed = new InstalledClasses(List.of(new InstalledClasses.Jar("Any", name -> true, name -> new byte[] {1})));
		for (String bad : List.of("../secret", "a/../../b", "/etc/x", "a\\b", "a//b", "C:/x", "a/./b", "")) {
			assertNull(installed.owner(bad), bad);
			assertNull(installed.read(bad), bad);
		}
		assertEquals("Any", installed.owner("net/caffeinemc/Renderer$Inner"));
	}
}
