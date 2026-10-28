package io.github.cmdralph.fabricated.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.archive.AtomicFiles;
import io.github.cmdralph.fabricated.core.archive.DeterministicJarWriter;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.resolve.ConflictingJars;

/**
 * Keeps Fabric Loader's API single-sourced: Fabricated's own copy is the one the game loads, always (the provider policy
 * for the loader, PROVIDER-2). Another jar's copy of Fabric Loader's packages (Forgified Fabric Loader, which Forgified
 * Fabric API nests) is left out of it as FML reads it ({@code FabricLoaderApiReader}, one per FML generation): two copies
 * of a package can't both load, and the mods built against the other copy use Fabric Loader's public API, which
 * Fabricated implements. The service files naming a class left out go with it; the rest of the jar loads as it would.
 */
final class FabricLoaderApiCopies {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** Fabric Loader's packages (internal form, with a trailing '/'). */
	static final List<String> PACKAGES = List.of("net/fabricmc/loader/", "net/fabricmc/api/");
	/** Ahead of FML's own reader (priority 0), which reads the jar whole. */
	static final int READER_PRIORITY = 100;
	/**
	 * A file every Fabricated build has (any platform, any version): such a jar is left as FML reads it, its copy of the
	 * API being the one that stays.
	 */
	static final String MARKER = ConflictingJars.FABRICATED_MARKER;
	/** The most a jar's copy without the API may hold ({@link #store}); a larger one is left to FML. */
	static final long MAX_COPY_BYTES = 64L << 20;
	private static final String SERVICES = "META-INF/services/";
	/** What was left out of which jar, for the report; FML may read a nested jar more than once. */
	private static final Set<String> NOTES = new CopyOnWriteArraySet<>();

	private FabricLoaderApiCopies() {
	}

	/** Whether a path of a jar is in Fabric Loader's packages. */
	static boolean owned(String path) {
		for (String prefix : PACKAGES) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * The service files to leave out with the classes: those naming a provider in Fabric Loader's packages, which
	 * {@link java.util.ServiceLoader} would fail on.
	 *
	 * @param services each service file's name (the service's class name) and the providers it names
	 */
	static Set<String> servicesLeftOut(Map<String, List<String>> services) {
		Set<String> leftOut = new TreeSet<>();
		services.forEach((service, providers) -> {
			for (String provider : providers) {
				if (owned(provider.trim().replace('.', '/'))) {
					leftOut.add(SERVICES + service);
					return;
				}
			}
		});
		return leftOut;
	}

	/** The providers a service file's text names, without comments and blank lines. */
	static List<String> providers(String serviceFile) {
		return serviceFile.lines().map(line -> {
			int hash = line.indexOf('#');
			return (hash < 0 ? line : line.substring(0, hash)).trim();
		}).filter(line -> !line.isEmpty()).toList();
	}

	/** Whether a jar's path stays once Fabric Loader's packages and {@code servicesLeftOut} are left out. */
	static boolean keeps(String path, Set<String> servicesLeftOut) {
		return !owned(path) && !servicesLeftOut.contains(path);
	}

	/**
	 * Writes a jar of {@code files} under the game directory's {@code .fabricated/loader-api/<sha-256>/}, with the
	 * original jar's file name (its module's name may come from it), unless it is there already. For an FML whose plugin
	 * libraries load with a plain class loader, which can't leave files out: the copy loads as the original would have.
	 */
	static Path store(Path gameDir, Path original, Map<String, byte[]> files) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DeterministicJarWriter.write(out, files);
		byte[] data = out.toByteArray();
		Path dir = gameDir.toAbsolutePath().normalize().resolve(".fabricated").resolve("loader-api").resolve(Hashing.sha256(data));
		String name = original.getFileName() == null ? "" : original.getFileName().toString();
		Path target = dir.resolve(name.endsWith(".jar") && name.indexOf('/') < 0 && name.indexOf('\\') < 0 ? name : "library.jar");
		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !Hashing.sha256(target).equals(Hashing.sha256(data))) {
			Files.createDirectories(dir);
			AtomicFiles.writeAtomically(target, data, dir);
		}
		return target;
	}

	/** @param services the service files left out */
	static void note(Path jar, Set<String> services) {
		String note = jar.getFileName() + " has its own copy of Fabric Loader's API, which is left out: Fabricated's is the one the game loads"
				+ (services.isEmpty() ? "" : "; the service files " + services + " name its classes and go with it");
		if (NOTES.add(note)) LOGGER.info(note);
	}

	static void failed(Path jar, Exception e) {
		LOGGER.warn("Fabricated could not leave Fabric Loader's API out of {}, which FML reads as it is: {}", jar, e.toString());
	}

	/** The lines {@link #note} wrote, in order. */
	static List<String> notes() {
		return List.copyOf(NOTES);
	}
}
