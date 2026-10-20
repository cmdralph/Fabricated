package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PackageGuardTest {
	/** As NeoForge reads a service file: a provider per line, comments and blank lines ignored. */
	@Test
	void readsTheProvidersOfAServiceFile() {
		assertEquals(Set.of("com/example/impl", "org/other"),
				PackageGuard.providerPackages("# providers\ncom.example.impl.First\r\n\n  org.other.Second # inline\ncom.example.impl.Third"));
		assertEquals(Set.of(), PackageGuard.providerPackages("# none\n\n"));
	}
}
