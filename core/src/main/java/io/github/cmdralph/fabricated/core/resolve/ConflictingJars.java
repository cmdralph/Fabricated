package io.github.cmdralph.fabricated.core.resolve;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Jars in the mods folder that can't run beside Fabricated, which then loads no Fabric mod and stops the launch with an
 * error naming them: Sinytra Connector, which loads Fabric mods too (two loaders can't share them, docs/roadmap.md), and
 * a second Fabricated jar, of which the platform would run one depending on its load order (DUP-1).
 */
public final class ConflictingJars {
	/** A class only Fabricated's own jars have. */
	public static final String FABRICATED_MARKER = "io/github/cmdralph/fabricated/loader/FabricatedInfo.class";
	/** Sinytra Connector's jar's module name ({@code Automatic-Module-Name}). */
	public static final String CONNECTOR_MODULE = "org.sinytra.connector";
	private static final Pattern CONNECTOR_MOD_ID = Pattern.compile("modId[ \\t]*=[ \\t]*[\"']connector[\"'][ \\t]*(#.*)?");
	private static final Pattern MODS_TABLE = Pattern.compile("\\[\\[[ \\t]*mods[ \\t]*]][ \\t]*(#.*)?");

	private ConflictingJars() {
	}

	/**
	 * Whether a NeoForge jar's metadata (its {@code neoforge.mods.toml}) declares Sinytra Connector's mod: in its
	 * {@code [[mods]]}, not a dependency on it, as the placeholders of mods only Connector or Fabricated loads have.
	 */
	public static boolean declaresConnector(String modsToml) {
		boolean mods = false;
		for (String line : modsToml.split("\\R")) {
			String trimmed = line.strip();
			if (trimmed.startsWith("[")) {
				mods = MODS_TABLE.matcher(trimmed).matches();
			} else if (mods && CONNECTOR_MOD_ID.matcher(trimmed).matches()) {
				return true;
			}
		}
		return false;
	}

	/** The error that stops the launch, or null when nothing conflicts. */
	public static String refusal(List<Path> fabricated, List<Path> connector) {
		if (!connector.isEmpty()) {
			return "Sinytra Connector (" + names(connector) + ") and Fabricated" + (fabricated.isEmpty() ? "" : " (" + names(fabricated) + ")")
					+ " both load Fabric mods, and one game can't run both: remove one of them from the mods folder."
					+ " Fabricated loads no Fabric mod this launch.";
		}
		if (fabricated.size() > 1) {
			return "The mods folder has " + fabricated.size() + " Fabricated jars (" + names(fabricated) + "), and only one can run:"
					+ " keep the one for this game's version and remove the others. Fabricated loads no Fabric mod this launch.";
		}
		return null;
	}

	private static String names(List<Path> jars) {
		return jars.stream().map(jar -> String.valueOf(jar.getFileName())).sorted().collect(Collectors.joining(", "));
	}
}
