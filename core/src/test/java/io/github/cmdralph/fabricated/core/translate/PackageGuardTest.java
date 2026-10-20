package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PackageGuardTest {
	/** As NeoForge reads a service file: a provider per line, comments and blank lines ignored. */
	@Test
	void readsTheProvidersOfAServiceFile() {
		assertEquals(Set.of("com.example.impl.First", "org.other.Second", "com.example.impl.Third"),
				PackageGuard.providers("# providers\ncom.example.impl.First\r\n\n  org.other.Second # inline\ncom.example.impl.Third"));
		assertEquals(Set.of(), PackageGuard.providers("# none\n\n"));
	}
}
