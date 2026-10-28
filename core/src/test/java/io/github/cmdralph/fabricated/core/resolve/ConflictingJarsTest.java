package io.github.cmdralph.fabricated.core.resolve;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConflictingJarsTest {
	@Test
	void connectorsOwnMetadataIsConnectors() {
		assertTrue(ConflictingJars.declaresConnector("modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\n[[mods]]\nmodId=\"connector\"\nversion=\"2.0.0\"\n"
				+ "[[dependencies.connector]]\nmodId=\"launchpad\"\n"));
		assertTrue(ConflictingJars.declaresConnector("[[ mods ]] # Connector\n  modId = 'connector' # its id\n"));
	}

	@Test
	void aDependencyOnConnectorIsNotConnector() {
		// The placeholders of mods only Connector or Fabricated loads depend on it (MULTI-1).
		assertFalse(ConflictingJars.declaresConnector("[[mods]]\nmodId=\"continuity\"\n[[dependencies.continuity]]\nmodId=\"connector\"\n"));
		assertFalse(ConflictingJars.declaresConnector("[[mods]]\nmodId=\"connectorextras\"\n"));
	}

	@Test
	void connectorOrASecondFabricatedJarIsRefusedByName() {
		String connector = ConflictingJars.refusal(List.of(Path.of("mods/fabricated-0.5.1.jar")), List.of(Path.of("mods/connector-2.0.0-full.jar")));
		assertTrue(connector.contains("Sinytra Connector (connector-2.0.0-full.jar)") && connector.contains("fabricated-0.5.1.jar"), connector);
		// DUP-1: an older build kept by mistake.
		String twice = ConflictingJars.refusal(List.of(Path.of("mods/fabricated-0.5.1.jar"), Path.of("mods/fabricated-0.5.0.jar")), List.of());
		assertTrue(twice.contains("2 Fabricated jars (fabricated-0.5.0.jar, fabricated-0.5.1.jar)"), twice);
		assertNull(ConflictingJars.refusal(List.of(Path.of("mods/fabricated-0.5.1.jar")), List.of()));
		assertNull(ConflictingJars.refusal(List.of(), List.of()));
	}
}
