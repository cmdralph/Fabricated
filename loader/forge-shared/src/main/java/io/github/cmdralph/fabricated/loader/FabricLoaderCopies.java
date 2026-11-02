package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Another jar's copy of Fabric Loader's API, on Forge (PROVIDER-2): Forgified Fabric API for 1.20.1 nests Forgified
 * Fabric Loader through Forge's jar-in-jar, with the same packages as Fabricated's own copy, and Java can't put two
 * modules exporting one package in the game's layer: Forge stops as it starts, with a {@code ResolutionException}
 * naming neither mod. NeoForge reads such a jar without its copy; Forge has no reader to do that with, so Fabricated
 * refuses the launch instead, naming the jar ({@link ConflictingMods}). Only the JDK is used here.
 */
final class FabricLoaderCopies {
	/** Fabric Loader's packages (internal form, with a trailing '/'), as NeoForge's reader leaves them out. */
	static final List<String> PACKAGES = List.of("net/fabricmc/loader/", "net/fabricmc/api/");
	/** Where Forge's jar-in-jar keeps the jars it nests. */
	static final String JAR_IN_JAR = "META-INF/jarjar/";
	/** How deep nested jars are read: Forge's jar-in-jar nests jars in jars, and a copy is rarely deeper than one. */
	static final int MAX_DEPTH = 3;
	/** The most entries read in one jar and the jars it nests, so a crafted archive can't keep discovery reading. */
	static final int MAX_ENTRIES = 200_000;

	private FabricLoaderCopies() {
	}

	/** Whether a path of a jar is in Fabric Loader's packages. */
	static boolean owned(String path) {
		for (String prefix : PACKAGES) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * Where a jar (not a Fabricated build) has a copy of Fabric Loader's API: "" for its own entries, the path of the
	 * nested jar Forge's jar-in-jar would add otherwise, or null for none.
	 */
	static String copyIn(ZipFile jar) throws IOException {
		int[] budget = {MAX_ENTRIES};
		Enumeration<? extends ZipEntry> entries = jar.entries();
		while (entries.hasMoreElements() && budget[0]-- > 0) {
			if (owned(entries.nextElement().getName())) return "";
		}
		entries = jar.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.isDirectory() || !entry.getName().startsWith(JAR_IN_JAR) || !entry.getName().endsWith(".jar")) continue;
			try (InputStream in = jar.getInputStream(entry)) {
				if (nestedCopy(in, 1, budget)) return entry.getName();
			}
			if (budget[0] <= 0) break;
		}
		return null;
	}

	/** Whether a nested jar, or a jar it nests in turn, has a copy, within the depth and the entries left. */
	private static boolean nestedCopy(InputStream in, int depth, int[] budget) throws IOException {
		ZipInputStream zip = new ZipInputStream(in);
		for (ZipEntry entry; budget[0]-- > 0 && (entry = zip.getNextEntry()) != null; ) {
			String name = entry.getName();
			if (owned(name)) return true;
			if (depth < MAX_DEPTH && !entry.isDirectory() && name.startsWith(JAR_IN_JAR) && name.endsWith(".jar")
					&& nestedCopy(zip, depth + 1, budget)) return true;
		}
		return false;
	}
}
