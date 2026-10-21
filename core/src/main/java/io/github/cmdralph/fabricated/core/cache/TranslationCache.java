package io.github.cmdralph.fabricated.core.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.cmdralph.fabricated.core.archive.AtomicFiles;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * On-disk cache of translation outputs under {@code <gameDir>/.fabricated/cache/v1}.
 *
 * <ul>
 *   <li><b>Sandboxed:</b> the root must resolve inside the game directory and no path component may be
 *       a symbolic link.</li>
 *   <li><b>Integrity-checked:</b> every entry records the SHA-256 of each file it contains; entries are
 *       verified before use and rebuilt if anything differs.</li>
 *   <li><b>Atomic:</b> entries are written to a temporary directory and moved into place.</li>
 *   <li><b>Self-cleaning:</b> entries unused for a configurable number of launches are deleted.</li>
 * </ul>
 */
public final class TranslationCache {
	public static final String LAYOUT_VERSION = "v1";
	private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_.-]{1,160}");
	private static final String ENTRY_FILE = "entry.json";
	private static final String INDEX_FILE = "index.json";
	private static final int BLOB_HASH_LENGTH = 32;

	private final Path root;
	private final boolean reuse;
	private final boolean verify;
	private final Map<String, Long> lastUsed = new ConcurrentSkipListMap<>();
	private final Map<String, Memo> hashMemo = new ConcurrentSkipListMap<>();
	/** {@code <parent jar sha>!/<entry>} → the nested jar extracted from it, so unchanged parents aren't re-read. */
	private final Map<String, NestedRef> nestedIndex = new ConcurrentSkipListMap<>();
	private final List<String> notes = Collections.synchronizedList(new ArrayList<>());
	private final long launch;

	/**
	 * @param reuse  whether existing entries may be reused (config {@code cache.enabled})
	 * @param verify whether entries are re-hashed before reuse (config {@code cache.verifyIntegrity})
	 */
	public TranslationCache(Path gameDir, boolean reuse, boolean verify) throws IOException {
		Path game = gameDir.toAbsolutePath().normalize();
		Files.createDirectories(game);
		Path realGame = game.toRealPath();
		Path candidate = realGame.resolve(".fabricated").resolve("cache").resolve(LAYOUT_VERSION).normalize();
		if (!candidate.startsWith(realGame)) {
			throw new IOException("Cache directory escapes the game directory: " + candidate);
		}
		Path p = realGame;
		for (Path part : realGame.relativize(candidate)) {
			p = p.resolve(part);
			createRealDirectory(p);
		}
		this.root = candidate;
		this.reuse = reuse;
		this.verify = verify;
		// Cleanup deletes inside these, so they must not lead out of the game directory either.
		for (String dir : new String[] {"entries", "nested", "tmp", "blobs"}) createRealDirectory(root.resolve(dir));
		this.launch = loadIndex() + 1;
		cleanTmp();
	}

	private static void createRealDirectory(Path p) throws IOException {
		if (Files.isSymbolicLink(p)) throw new IOException("Refusing to use a cache path containing a symbolic link: " + p);
		if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Cache path component is not a directory: " + p);
		}
		Files.createDirectories(p);
	}

	public Path root() {
		return root;
	}

	/** Notes about cache maintenance (rebuilt entries, deletions) for the report. */
	public List<String> notes() {
		synchronized (notes) {
			return List.copyOf(notes);
		}
	}

	/** A complete cache entry. */
	public record Entry(Path dir, Map<String, Object> metadata) {
		public Path file(String fileName) {
			return dir.resolve(fileName);
		}
	}

	/** Directory-safe entry name: {@code <modid>-<first 40 hex of key>}. */
	public static String entryName(String modId, String keyHash) {
		String name = (modId + "-" + keyHash.substring(0, Math.min(40, keyHash.length()))).toLowerCase(java.util.Locale.ROOT);
		if (!SAFE_NAME.matcher(name).matches()) throw new IllegalArgumentException("Unsafe cache entry name " + name);
		return name;
	}

	/** A file's SHA-256, valid while its {@code stamp} (size, times, file key) is unchanged. */
	private record Memo(String stamp, String sha256, long launch) {
	}

	private record NestedRef(String sha256, long launch) {
	}

	/**
	 * Returns a verified entry, or empty when absent, disabled, or failing verification (then deleted).
	 *
	 * @param requiredFiles files the entry must hold (and list in its {@code entry.json})
	 */
	public Optional<Entry> lookup(String name, String... requiredFiles) {
		lastUsed.put(name, launch);
		if (!reuse) return Optional.empty();
		Path dir = root.resolve("entries").resolve(name);
		Path entryJson = dir.resolve(ENTRY_FILE);
		if (Files.isSymbolicLink(dir) || !Files.isRegularFile(entryJson, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
		try {
			Map<String, Object> meta = Json.parseObject(Files.readString(entryJson, StandardCharsets.UTF_8));
			if (!(meta.get("files") instanceof Map<?, ?> files)) throw new JsonException("missing files");
			for (String required : requiredFiles) {
				if (!files.containsKey(required)) throw new JsonException("missing " + required);
			}
			for (Map.Entry<?, ?> f : files.entrySet()) {
				String fileName = String.valueOf(f.getKey());
				if (!SAFE_NAME.matcher(fileName).matches()) throw new JsonException("unsafe file name " + fileName);
				Path file = dir.resolve(fileName);
				if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new JsonException("missing " + fileName);
				if (verify && !sha256(file).equals(f.getValue())) throw new JsonException("hash mismatch for " + fileName);
			}
			return Optional.of(new Entry(dir, meta));
		} catch (IOException | JsonException | RuntimeException e) {
			notes.add("Cache entry " + name + " failed verification (" + e.getMessage() + ") and will be rebuilt");
			deleteQuietly(dir);
			return Optional.empty();
		}
	}

	/**
	 * Atomically stores an entry.
	 *
	 * @param files    file name → content (names must be simple, e.g. {@code overlay.jar})
	 * @param metadata extra JSON-serialisable metadata recorded in {@code entry.json}
	 */
	public Entry store(String name, Map<String, byte[]> files, Map<String, Object> metadata) throws IOException {
		Path tmp = Files.createTempDirectory(root.resolve("tmp"), "entry");
		try {
			Map<String, Object> hashes = new TreeMap<>();
			for (Map.Entry<String, byte[]> f : files.entrySet()) {
				if (!SAFE_NAME.matcher(f.getKey()).matches() || f.getKey().equals(ENTRY_FILE)) {
					throw new IllegalArgumentException("Unsafe cache file name " + f.getKey());
				}
				Files.write(tmp.resolve(f.getKey()), f.getValue());
				hashes.put(f.getKey(), Hashing.sha256(f.getValue()));
			}
			Map<String, Object> meta = new LinkedHashMap<>(metadata);
			meta.put("files", hashes);
			Files.writeString(tmp.resolve(ENTRY_FILE), Json.write(meta, true), StandardCharsets.UTF_8);
			Path target = root.resolve("entries").resolve(name);
			deleteQuietly(target);
			AtomicFiles.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			lastUsed.put(name, launch);
			for (Map.Entry<String, Object> h : hashes.entrySet()) remember(target.resolve(h.getKey()), (String) h.getValue());
			return new Entry(target, meta);
		} finally {
			deleteQuietly(tmp);
		}
	}

	/** Stores a nested jar content-addressed by SHA-256 and returns its path. */
	public Path storeNested(byte[] data) throws IOException {
		String sha = Hashing.sha256(data);
		Path target = root.resolve("nested").resolve(sha + ".jar");
		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || (verify && !sha256(target).equals(sha))) {
			AtomicFiles.writeAtomically(target, data, root.resolve("tmp"));
			remember(target, sha);
		}
		lastUsed.put("nested/" + sha + ".jar", launch);
		return target;
	}

	/** {@link #storeNested(byte[])}, remembering that {@code entry} of the jar hashed {@code parentSha} holds it. */
	public Path storeNested(byte[] data, String parentSha, String entry) throws IOException {
		Path target = storeNested(data);
		nestedIndex.put(parentSha + "!/" + entry, new NestedRef(nestedSha(target), launch));
		return target;
	}

	/**
	 * The nested jar a previous launch extracted from {@code entry} of the jar hashed {@code parentSha}, if it is still
	 * in the cache (and intact, when verifying): the parent's content decides the nested jar's, so it needn't be read.
	 */
	public Optional<Path> nested(String parentSha, String entry) throws IOException {
		if (!reuse) return Optional.empty();
		NestedRef ref = nestedIndex.get(parentSha + "!/" + entry);
		if (ref == null) return Optional.empty();
		Path target = root.resolve("nested").resolve(ref.sha256() + ".jar");
		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || (verify && !sha256(target).equals(ref.sha256()))) return Optional.empty();
		nestedIndex.put(parentSha + "!/" + entry, new NestedRef(ref.sha256(), launch));
		lastUsed.put("nested/" + target.getFileName(), launch);
		return Optional.of(target);
	}

	/** The SHA-256 of a jar stored by {@link #storeNested}, which is its file name. */
	public static String nestedSha(Path nestedJar) {
		String name = nestedJar.getFileName().toString();
		return name.substring(0, name.length() - ".jar".length());
	}

	/**
	 * Stores a blob derived from known inputs (e.g. a mapping table) under {@code blobs/}. Its name says which inputs
	 * it came from, not what it holds, so the file starts with the SHA-256 of its content for {@link #readBlob}.
	 */
	public Path storeBlob(String fileName, byte[] data) throws IOException {
		if (!SAFE_NAME.matcher(fileName).matches()) throw new IllegalArgumentException("Unsafe blob name " + fileName);
		Path target = root.resolve("blobs").resolve(fileName);
		byte[] file = new byte[BLOB_HASH_LENGTH + data.length];
		System.arraycopy(Hashing.sha256Digest().digest(data), 0, file, 0, BLOB_HASH_LENGTH);
		System.arraycopy(data, 0, file, BLOB_HASH_LENGTH, data.length);
		AtomicFiles.writeAtomically(target, file, root.resolve("tmp"));
		lastUsed.put("blobs/" + fileName, launch);
		return target;
	}

	/** Reads a blob stored with {@link #storeBlob}, or empty if absent, damaged (then deleted) or reuse is disabled. */
	public Optional<byte[]> readBlob(String fileName) throws IOException {
		if (!SAFE_NAME.matcher(fileName).matches()) throw new IllegalArgumentException("Unsafe blob name " + fileName);
		lastUsed.put("blobs/" + fileName, launch);
		if (!reuse) return Optional.empty();
		Path file = root.resolve("blobs").resolve(fileName);
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
		byte[] bytes = Files.readAllBytes(file);
		if (bytes.length < BLOB_HASH_LENGTH || !blobIntact(bytes)) {
			notes.add("Cache item blobs/" + fileName + " failed verification and will be rebuilt");
			deleteQuietly(file);
			return Optional.empty();
		}
		return Optional.of(Arrays.copyOfRange(bytes, BLOB_HASH_LENGTH, bytes.length));
	}

	/** Whether a blob file's content matches the hash it starts with. */
	private static boolean blobIntact(byte[] file) {
		MessageDigest digest = Hashing.sha256Digest();
		digest.update(file, BLOB_HASH_LENGTH, file.length - BLOB_HASH_LENGTH);
		return MessageDigest.isEqual(digest.digest(), Arrays.copyOf(file, BLOB_HASH_LENGTH));
	}

	/**
	 * SHA-256 of a file, memoised across launches so unchanged mods are not re-hashed. The memo is keyed by path,
	 * size, modification time and, where the file system has them, change time and file key (inode): copying a jar
	 * over another with its old timestamp kept ({@code cp -p}, a restored backup) still changes the change time.
	 */
	public String sha256(Path file) throws IOException {
		String key = memoKey(file);
		String stamp = stamp(file);
		Memo memo = hashMemo.get(key);
		String sha = memo != null && memo.stamp().equals(stamp) ? memo.sha256() : Hashing.sha256(file);
		hashMemo.put(key, new Memo(stamp, sha, launch));
		return sha;
	}

	/** Records the hash of a file this cache just wrote, so the next launch doesn't hash it again. */
	private void remember(Path file, String sha) throws IOException {
		hashMemo.put(memoKey(file), new Memo(stamp(file), sha, launch));
	}

	private static String memoKey(Path file) {
		return file.toAbsolutePath().normalize().toString();
	}

	private static String stamp(Path file) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
		return attrs.size() + "|" + attrs.lastModifiedTime().toMillis() + "|" + changeTime(file) + "|" + attrs.fileKey();
	}

	private static Object changeTime(Path file) {
		try {
			return Files.getAttribute(file, "unix:ctime");
		} catch (UnsupportedOperationException | IllegalArgumentException | IOException e) {
			return "";
		}
	}

	/**
	 * Deletes entries, nested jars and blobs that have not been used for {@code maxUnusedLaunches}
	 * launches, then persists the index. Call once per launch after all lookups.
	 */
	public void finish(int maxUnusedLaunches) {
		gcDirectory(root.resolve("entries"), "", maxUnusedLaunches);
		gcDirectory(root.resolve("nested"), "nested/", maxUnusedLaunches);
		gcDirectory(root.resolve("blobs"), "blobs/", maxUnusedLaunches);
		// What is left that long unused has no file to clean: a name looked up but never stored (a failed translation).
		lastUsed.values().removeIf(used -> launch - used > maxUnusedLaunches);
		hashMemo.values().removeIf(m -> launch - m.launch() > maxUnusedLaunches);
		nestedIndex.values().removeIf(n -> launch - n.launch() > maxUnusedLaunches);
		try {
			Map<String, Object> index = new LinkedHashMap<>();
			index.put("launch", launch);
			index.put("lastUsed", new TreeMap<>(lastUsed));
			Map<String, Object> hashes = new TreeMap<>();
			hashMemo.forEach((k, m) -> hashes.put(k, List.of(m.stamp(), m.sha256(), m.launch())));
			index.put("hashes", hashes);
			Map<String, Object> nested = new TreeMap<>();
			nestedIndex.forEach((k, n) -> nested.put(k, List.of(n.sha256(), n.launch())));
			index.put("nested", nested);
			AtomicFiles.writeAtomically(root.resolve(INDEX_FILE), Json.write(index, true).getBytes(StandardCharsets.UTF_8), root.resolve("tmp"));
		} catch (IOException e) {
			notes.add("Could not save cache index: " + e.getMessage());
		}
	}

	private void gcDirectory(Path dir, String prefix, int maxUnused) {
		if (!Files.isDirectory(dir)) return;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) {
				String name = prefix + p.getFileName();
				Long used = lastUsed.get(name);
				if (used == null) {
					// First time seen by this index (e.g. created before an upgrade): start counting now.
					lastUsed.put(name, launch);
					continue;
				}
				if (launch - used > maxUnused) {
					deleteQuietly(p);
					lastUsed.remove(name);
					notes.add("Removed unused cache item " + name);
				}
			}
		} catch (IOException e) {
			notes.add("Cache cleanup of " + dir + " failed: " + e.getMessage());
		}
	}

	private long loadIndex() {
		Path index = root.resolve(INDEX_FILE);
		if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) return 0;
		try {
			// Fabricated's own file, which grows with the number of mods: not held to the cap for mod-supplied JSON.
			Map<String, Object> json = Json.parseObject(Files.readString(index, StandardCharsets.UTF_8), 64 << 20);
			if (json.get("lastUsed") instanceof Map<?, ?> m) {
				for (Map.Entry<?, ?> e : m.entrySet()) {
					if (e.getValue() instanceof Number n) lastUsed.put(String.valueOf(e.getKey()), n.longValue());
				}
			}
			if (json.get("hashes") instanceof Map<?, ?> h) {
				h.forEach((k, v) -> {
					if (v instanceof List<?> l && l.size() == 3 && l.get(0) instanceof String stamp && l.get(1) instanceof String sha
							&& l.get(2) instanceof Number used) {
						hashMemo.put(String.valueOf(k), new Memo(stamp, sha, used.longValue()));
					}
				});
			}
			if (json.get("nested") instanceof Map<?, ?> n) {
				n.forEach((k, v) -> {
					if (v instanceof List<?> l && l.size() == 2 && l.get(0) instanceof String sha && SAFE_NAME.matcher(sha + ".jar").matches()
							&& l.get(1) instanceof Number used) {
						nestedIndex.put(String.valueOf(k), new NestedRef(sha, used.longValue()));
					}
				});
			}
			return json.get("launch") instanceof Number n ? n.longValue() : 0;
		} catch (IOException | JsonException e) {
			notes.add("Cache index was unreadable and has been reset: " + e.getMessage());
			return 0;
		}
	}

	private void cleanTmp() {
		// Leftovers from a crashed launch. Recent files may belong to another game instance using the same game
		// directory right now (a client and a server started from one folder), so they are left alone.
		long cutoff = System.currentTimeMillis() - 60 * 60 * 1000L;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root.resolve("tmp"))) {
			for (Path p : stream) {
				if (Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis() < cutoff) deleteQuietly(p);
			}
		} catch (IOException e) {
			notes.add("Could not clean temporary cache files: " + e.getMessage());
		}
	}

	static void deleteQuietly(Path path) {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
		// Files.walk does not follow symbolic links; reverse order deletes children before their directory.
		try (Stream<Path> walk = Files.walk(path)) {
			for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
		} catch (IOException | UncheckedIOException ignored) {
			// Best effort; a leftover temporary file is harmless and is cleaned next launch.
		}
	}
}
