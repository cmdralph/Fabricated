package io.github.cmdralph.fabricated.core.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

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

	private final Path root;
	private final boolean reuse;
	private final boolean verify;
	private final Map<String, Long> lastUsed = new ConcurrentSkipListMap<>();
	private final Map<String, Map<String, Object>> hashMemo = new ConcurrentSkipListMap<>();
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
			if (Files.isSymbolicLink(p)) throw new IOException("Refusing to use a cache path containing a symbolic link: " + p);
			if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("Cache path component is not a directory: " + p);
			}
			Files.createDirectories(p);
		}
		this.root = candidate;
		this.reuse = reuse;
		this.verify = verify;
		Files.createDirectories(root.resolve("entries"));
		Files.createDirectories(root.resolve("nested"));
		Files.createDirectories(root.resolve("tmp"));
		this.launch = loadIndex() + 1;
		cleanTmp();
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
	public record Entry(String name, Path dir, Map<String, Object> metadata) {
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

	/** Returns a verified entry, or empty when absent, disabled, or failing verification (then deleted). */
	public Optional<Entry> lookup(String name) {
		lastUsed.put(name, launch);
		if (!reuse) return Optional.empty();
		Path dir = root.resolve("entries").resolve(name);
		Path entryJson = dir.resolve(ENTRY_FILE);
		if (!Files.isRegularFile(entryJson, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
		try {
			Map<String, Object> meta = Json.parseObject(Files.readString(entryJson, StandardCharsets.UTF_8));
			if (!(meta.get("files") instanceof Map<?, ?> files)) throw new JsonException("missing files");
			for (Map.Entry<?, ?> f : files.entrySet()) {
				String fileName = String.valueOf(f.getKey());
				if (!SAFE_NAME.matcher(fileName).matches()) throw new JsonException("unsafe file name " + fileName);
				Path file = dir.resolve(fileName);
				if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new JsonException("missing " + fileName);
				if (verify && !Hashing.sha256(file).equals(f.getValue())) throw new JsonException("hash mismatch for " + fileName);
			}
			return Optional.of(new Entry(name, dir, meta));
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
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			lastUsed.put(name, launch);
			return new Entry(name, target, meta);
		} finally {
			deleteQuietly(tmp);
		}
	}

	/** Stores a nested jar content-addressed by SHA-256 and returns its path. */
	public Path storeNested(byte[] data) throws IOException {
		String sha = Hashing.sha256(data);
		Path target = root.resolve("nested").resolve(sha + ".jar");
		if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && (!verify || Hashing.sha256(target).equals(sha))) {
			lastUsed.put("nested/" + sha, launch);
			return target;
		}
		Path tmp = Files.createTempFile(root.resolve("tmp"), "nested", ".jar");
		try {
			Files.write(tmp, data);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
		lastUsed.put("nested/" + sha, launch);
		return target;
	}

	/** Stores an arbitrary content-addressed blob (e.g. a mapping table) under {@code blobs/}. */
	public Path storeBlob(String fileName, byte[] data) throws IOException {
		if (!SAFE_NAME.matcher(fileName).matches()) throw new IllegalArgumentException("Unsafe blob name " + fileName);
		Path dir = Files.createDirectories(root.resolve("blobs"));
		Path target = dir.resolve(fileName);
		Path tmp = Files.createTempFile(root.resolve("tmp"), "blob", ".bin");
		try {
			Files.write(tmp, data);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
		lastUsed.put("blobs/" + fileName, launch);
		return target;
	}

	/** Reads a blob stored with {@link #storeBlob}, or empty if absent or reuse is disabled. */
	public Optional<byte[]> readBlob(String fileName) throws IOException {
		if (!SAFE_NAME.matcher(fileName).matches()) throw new IllegalArgumentException("Unsafe blob name " + fileName);
		lastUsed.put("blobs/" + fileName, launch);
		if (!reuse) return Optional.empty();
		Path file = root.resolve("blobs").resolve(fileName);
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
		return Optional.of(Files.readAllBytes(file));
	}

	/**
	 * SHA-256 of a file, memoised by (path, size, modification time) across launches so unchanged
	 * mods are not re-hashed.
	 */
	public String sha256(Path file) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
		String key = file.toAbsolutePath().normalize().toString();
		Map<String, Object> memo = hashMemo.get(key);
		long size = attrs.size();
		long mtime = attrs.lastModifiedTime().toMillis();
		if (memo != null && memo.get("size") instanceof Number s && s.longValue() == size
				&& memo.get("mtime") instanceof Number m && m.longValue() == mtime && memo.get("sha256") instanceof String sha) {
			memo.put("launch", launch);
			return sha;
		}
		String sha = Hashing.sha256(file);
		Map<String, Object> entry = new ConcurrentHashMap<>();
		entry.put("size", size);
		entry.put("mtime", mtime);
		entry.put("sha256", sha);
		entry.put("launch", launch);
		hashMemo.put(key, entry);
		return sha;
	}

	/**
	 * Deletes entries, nested jars and blobs that have not been used for {@code maxUnusedLaunches}
	 * launches, then persists the index. Call once per launch after all lookups.
	 */
	public void finish(int maxUnusedLaunches) {
		gcDirectory(root.resolve("entries"), "", maxUnusedLaunches, true);
		gcDirectory(root.resolve("nested"), "nested/", maxUnusedLaunches, false);
		gcDirectory(root.resolve("blobs"), "blobs/", maxUnusedLaunches, false);
		hashMemo.values().removeIf(m -> !(m.get("launch") instanceof Number n) || launch - n.longValue() > maxUnusedLaunches);
		try {
			Map<String, Object> index = new LinkedHashMap<>();
			index.put("launch", launch);
			index.put("lastUsed", new TreeMap<>(lastUsed));
			Map<String, Object> hashes = new TreeMap<>();
			hashMemo.forEach((k, v) -> hashes.put(k, new TreeMap<>(v)));
			index.put("hashes", hashes);
			Path tmp = Files.createTempFile(root.resolve("tmp"), "index", ".json");
			Files.writeString(tmp, Json.write(index, true), StandardCharsets.UTF_8);
			Files.move(tmp, root.resolve(INDEX_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			notes.add("Could not save cache index: " + e.getMessage());
		}
	}

	private void gcDirectory(Path dir, String prefix, int maxUnused, boolean directories) {
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
					if (directories) deleteQuietly(p);
					else Files.deleteIfExists(p);
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
			Map<String, Object> json = Json.parseObject(Files.readString(index, StandardCharsets.UTF_8));
			if (json.get("lastUsed") instanceof Map<?, ?> m) {
				for (Map.Entry<?, ?> e : m.entrySet()) {
					if (e.getValue() instanceof Number n) lastUsed.put(String.valueOf(e.getKey()), n.longValue());
				}
			}
			if (json.get("hashes") instanceof Map<?, ?> h) {
				for (Map.Entry<?, ?> e : h.entrySet()) {
					if (e.getValue() instanceof Map<?, ?> v) {
						Map<String, Object> copy = new ConcurrentHashMap<>();
						v.forEach((k, val) -> copy.put(String.valueOf(k), val));
						hashMemo.put(String.valueOf(e.getKey()), copy);
					}
				}
			}
			return json.get("launch") instanceof Number n ? n.longValue() : 0;
		} catch (IOException | JsonException e) {
			notes.add("Cache index was unreadable and has been reset: " + e.getMessage());
			return 0;
		}
	}

	private void cleanTmp() {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root.resolve("tmp"))) {
			for (Path p : stream) deleteQuietly(p);
		} catch (IOException e) {
			notes.add("Could not clean temporary cache files: " + e.getMessage());
		}
	}

	static void deleteQuietly(Path path) {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
		try {
			Files.walkFileTree(path, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {
			// Best effort; a leftover temporary file is harmless and is cleaned next launch.
		}
	}
}
