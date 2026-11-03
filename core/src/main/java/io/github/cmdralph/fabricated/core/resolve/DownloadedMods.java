package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fabric mods that another Fabric mod's loader downloads as the game runs, which Fabricated loads from the next launch
 * (DOWNLOAD-1).
 * <p>
 * Essential's Fabric build is such a loader ({@code essential-loader}, which Essential's own jar and partner mods
 * nest): its {@code preLaunch} entrypoint downloads Essential into the game directory's {@code essential} folder, then
 * adds the jar to Fabric Loader's class loader, mod list and Mixin's configs (read in Essential Loader's source). NeoForge
 * builds the game's modules before any mod code runs, so a jar can't join them then. The jar the loader left in an
 * earlier launch is scanned with the mods folder's instead, through the same checks; Essential's loader, finding
 * Essential among the mods, lets it load as a regular mod, its own rule for that case, which applies no automatic
 * update. Fabricated never downloads anything itself, and reads nothing else of the loader's folder.
 * <p>
 * Only Essential: a partner mod's own download (in {@code essential/mods/<slug>}) is not looked for.
 */
public final class DownloadedMods {
	/** The id of Essential's loader. */
	public static final String ESSENTIAL_LOADER = "essential-loader";
	/** Report section listing the downloaded jars scanned this launch. */
	public static final String SECTION = "Mods other mods downloaded in an earlier launch (DOWNLOAD-1)";
	/** Report section for downloaded jars past {@link #STALE}, which the platform glue also shows as warnings. */
	public static final String STALE_SECTION = "Downloaded mods their loader doesn't update here";
	/** Past this age a downloaded jar is reported as old: its loader doesn't update it while Fabricated loads it. */
	public static final Duration STALE = Duration.ofDays(30);
	private static final String ESSENTIAL_DIR = "essential";
	private static final String EXTENSION = ".jar";

	/**
	 * A jar a loader downloaded.
	 *
	 * @param jar      the jar
	 * @param loader   the id of the Fabric mod whose loader downloaded it
	 * @param name     what it is, for messages ({@code Essential})
	 * @param modified when it was last written
	 */
	public record Download(Path jar, String loader, String name, Instant modified) {
		/** Whether it is older than {@link #STALE} at {@code now}. */
		public boolean stale(Instant now) {
			return modified.plus(STALE).isBefore(now);
		}
	}

	private DownloadedMods() {
	}

	/**
	 * The jars the loaders among {@code loadingIds} downloaded in an earlier launch for this Minecraft version.
	 *
	 * @param loadingIds       the ids of the Fabric mods about to load (nested ones included)
	 * @param gameDir          the game directory
	 * @param minecraftVersion the game's version, as Fabric's {@code minecraft} mod gives it
	 */
	public static List<Download> of(Collection<String> loadingIds, Path gameDir, String minecraftVersion) {
		List<Download> found = new ArrayList<>();
		if (loadingIds.contains(ESSENTIAL_LOADER)) {
			Path jar = latest(gameDir.resolve(ESSENTIAL_DIR), essentialBaseName(minecraftVersion));
			if (jar != null) found.add(new Download(jar, ESSENTIAL_LOADER, "Essential", modified(jar)));
		}
		return found;
	}

	/**
	 * Whether {@code jar} is where {@link #of} looks for a loader's download: a jar a mod adds to the class path there
	 * as the game runs loads from the next launch.
	 */
	public static boolean isLookedFor(Path jar, Path gameDir) {
		Path dir = gameDir.resolve(ESSENTIAL_DIR).toAbsolutePath().normalize();
		Path file = jar.toAbsolutePath().normalize();
		if (!dir.equals(file.getParent())) return false;
		String name = file.getFileName().toString();
		return name.startsWith("Essential (fabric_") && suffixNumber(name, name.substring(0, name.indexOf(')') + 1)) >= 0;
	}

	/** Essential Loader's name for Essential's jar ({@code Essential (fabric_26.1.2)}, before its extension). */
	static String essentialBaseName(String minecraftVersion) {
		return "Essential (fabric_" + minecraftVersion + ")";
	}

	/**
	 * The newest of {@code dir}'s jars named {@code base} (Essential Loader's rule: {@code base.jar} is the first, a
	 * newer download that couldn't replace it is {@code base.1.jar}, then {@code base.2.jar}), or null. Only regular files,
	 * not links: the loader writes the jar there itself.
	 */
	static Path latest(Path dir, String base) {
		if (!Files.isDirectory(dir)) return null;
		Path best = null;
		int bestNumber = -1;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
			for (Path file : files) {
				int number = suffixNumber(file.getFileName().toString(), base);
				if (number > bestNumber && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
					best = file;
					bestNumber = number;
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return best;
	}

	/** {@code name}'s number as one of {@code base}'s jars: 0 for {@code base.jar}, N for {@code base.N.jar}, else -1. */
	static int suffixNumber(String name, String base) {
		if (!name.startsWith(base) || !name.endsWith(EXTENSION) || name.length() < base.length() + EXTENSION.length()) return -1;
		String middle = name.substring(base.length(), name.length() - EXTENSION.length());
		if (middle.isEmpty()) return 0;
		if (middle.length() < 2 || middle.charAt(0) != '.' || middle.length() > 10) return -1;
		for (int i = 1; i < middle.length(); i++) {
			if (middle.charAt(i) < '0' || middle.charAt(i) > '9') return -1;
		}
		return Integer.parseInt(middle.substring(1));
	}

	private static Instant modified(Path jar) {
		try {
			return Files.getLastModifiedTime(jar, LinkOption.NOFOLLOW_LINKS).toInstant();
		} catch (IOException e) {
			return Instant.EPOCH;
		}
	}
}
