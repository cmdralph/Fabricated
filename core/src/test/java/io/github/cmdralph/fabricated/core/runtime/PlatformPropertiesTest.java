package io.github.cmdralph.fabricated.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class PlatformPropertiesTest {
	/** The properties as FML's service provider leaves them for a marker list (null: unset). */
	private static Properties asFmlSetsThem(String markers) {
		Properties properties = new Properties();
		if (markers != null) properties.setProperty("forge.logging.markers", markers);
		String selection = properties.getProperty("forge.logging.markers", "");
		Arrays.stream(selection.split(",")).forEach(marker -> properties.setProperty("forge.logging.marker." + marker.toLowerCase(Locale.ROOT), "ACCEPT"));
		return properties;
	}

	@Test
	void theEmptyMarkerFmlSetsWithoutAMarkerListIsRemoved() {
		// CORPUS-60: Ledger's konf reads every system property as a dotted path, and "forge.logging.marker." has an empty part.
		Properties production = asFmlSetsThem(null);
		assertEquals("ACCEPT", production.getProperty(PlatformProperties.EMPTY_LOG_MARKER));
		assertTrue(PlatformProperties.dropEmptyLogMarker(production));
		assertNull(production.getProperty(PlatformProperties.EMPTY_LOG_MARKER));
		assertTrue(production.isEmpty(), "nothing else was set: " + production);

		Properties gap = asFmlSetsThem("SCAN,,REGISTRIES");
		assertTrue(PlatformProperties.dropEmptyLogMarker(gap));
		assertEquals("ACCEPT", gap.getProperty("forge.logging.marker.scan"), "the named markers stay");
		assertEquals("ACCEPT", gap.getProperty("forge.logging.marker.registries"));
	}

	@Test
	void namedMarkersAndAPropertySetOtherwiseAreLeftAsTheyAre() {
		Properties development = asFmlSetsThem("REGISTRIES");
		assertFalse(PlatformProperties.dropEmptyLogMarker(development));
		assertEquals("ACCEPT", development.getProperty("forge.logging.marker.registries"));

		Properties own = new Properties();
		own.setProperty(PlatformProperties.EMPTY_LOG_MARKER, "DENY");
		assertFalse(PlatformProperties.dropEmptyLogMarker(own), "not FML's value");
		assertEquals("DENY", own.getProperty(PlatformProperties.EMPTY_LOG_MARKER));

		Properties named = new Properties();
		named.setProperty("forge.logging.markers", "SCAN");
		named.setProperty(PlatformProperties.EMPTY_LOG_MARKER, "ACCEPT");
		assertFalse(PlatformProperties.dropEmptyLogMarker(named), "a list without an empty name: someone else set it");
		assertFalse(PlatformProperties.dropEmptyLogMarker(new Properties()));
	}
}
