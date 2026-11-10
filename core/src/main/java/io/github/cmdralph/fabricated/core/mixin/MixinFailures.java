package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.archive.AtomicFiles;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * The safety net for a Fabric mod's mixin that fails as Mixin applies it, which pre-flight passed (APPLY-1). Such a
 * failure stops the launch: Mixin's error handlers see only some failures and can't make one safe to go on past, as
 * the class it was applying to is left half transformed. So it is recorded as the launch stops (the mixin, its mod's jar,
 * and why), and the next launch leaves the mixin out, as pre-flight leaves out one it finds broken, and reports it. A
 * record holds for the jar of the mod and the builds of Fabricated, the game and the loader it failed with: once any of
 * them changes, the mixin is tried again, as it is when the file is deleted.
 *
 * <p>The file is Fabricated's own, under the game directory's {@code .fabricated}; what it holds is checked as it is
 * read, and the most it can do is leave mixins out, as the player's {@code skipMixins} can.
 */
public final class MixinFailures {
	public static final String FILE_NAME = "mixin-failures.json";
	static final int MAX_ENTRIES = 64;
	static final int MAX_REASON = 600;
	private static final int MAX_FILE = 256 << 10;
	private static final int MAX_CHAIN = 64;
	private static final int MAX_MESSAGE = 16 << 10;
	private static final int MAX_FIELD = 200;
	private static final int VERSION = 1;
	/** A mixin as Mixin names it in its messages: {@code <config>:<mixin> from mod <id>}. */
	private static final String NAME = "([A-Za-z0-9_.$-]{1,200}\\.json):([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*) from mod ([^\\s\\]]{1,100})";
	private static final Pattern NAMED = Pattern.compile(NAME);
	/** Mixin's error for a mixin that failed ({@code MixinApplyError}, {@code MixinPrepareError}): it names that one. */
	private static final Pattern FAILED = Pattern.compile("Mixin \\[" + NAME + "\\] from phase \\[[^\\]]*\\] in config \\[[^\\]]*\\] FAILED during [A-Z]+");
	private static final Pattern MIXIN = Pattern.compile("[A-Za-z0-9_.$-]{1,200}\\.json:[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
	private static final Pattern MOD_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	/** The packages of the throwables whose messages name a mixin that failed: Mixin's and MixinExtras'. */
	private static final List<String> MIXIN_PACKAGES = List.of("org.spongepowered.asm.mixin.", "com.llamalad7.mixinextras.");
	private static final String ABOUT = "Mixins of Fabric mods that stopped a launch as Mixin applied them. Fabricated leaves each out"
			+ " until its mod's jar, Fabricated, Minecraft or the loader changes. Delete this file to try them all again.";

	private MixinFailures() {
	}

	/** Where the failures are recorded for a game directory. */
	public static Path file(Path gameDir) {
		return gameDir.resolve(".fabricated").resolve(FILE_NAME);
	}

	/** The builds a recorded failure holds for: once one of them changes, the mixin is tried again. */
	public record Launch(String fabricatedVersion, String fabricatedBuild, String minecraftVersion, String loaderVersion) {
		public Launch {
			fabricatedVersion = field(fabricatedVersion);
			fabricatedBuild = field(fabricatedBuild);
			minecraftVersion = field(minecraftVersion);
			loaderVersion = field(loaderVersion);
		}

		private static String field(String value) {
			return value == null ? "" : clean(value, MAX_FIELD);
		}
	}

	/**
	 * A mixin that failed, as Mixin names it.
	 *
	 * @param mixin         its {@code skipMixins} entry ({@code <config>:<mixin>})
	 * @param platformModId the id Mixin gives its mod: the translated mod's id on the platform
	 * @param reason        why it failed
	 */
	public record Named(String mixin, String platformModId, String reason) {
		public Named {
			Objects.requireNonNull(mixin, "mixin");
			Objects.requireNonNull(platformModId, "platformModId");
			reason = reason == null ? "" : clean(reason, MAX_REASON);
		}
	}

	/**
	 * A recorded failure.
	 *
	 * @param mod    the Fabric mod's id
	 * @param sha256 the hash of the mod's jar
	 * @param mixin  the mixin's {@code skipMixins} entry ({@code <config>:<mixin>})
	 * @param reason why it failed
	 */
	public record Failure(String mod, String sha256, String mixin, String reason) {
		public Failure {
			Objects.requireNonNull(mod, "mod");
			Objects.requireNonNull(sha256, "sha256");
			Objects.requireNonNull(mixin, "mixin");
			reason = reason == null ? "" : clean(reason, MAX_REASON);
		}

		boolean valid() {
			return MOD_ID.matcher(mod).matches() && SHA256.matcher(sha256).matches() && MIXIN.matcher(mixin).matches();
		}

		boolean sameAs(Failure other) {
			return mod.equals(other.mod) && sha256.equals(other.sha256) && mixin.equals(other.mixin);
		}
	}

	/** One throwable of a chain: its class's name and message. */
	record Thrown(String className, String message) {
	}

	/** The mixin a throwable (with its causes and suppressed throwables) says failed as Mixin applied or prepared it, or null. */
	public static Named find(Throwable thrown) {
		return find(chain(thrown));
	}

	/** A throwable and those it holds, causes before suppressed ones, each once, at most {@link #MAX_CHAIN}. */
	static List<Thrown> chain(Throwable thrown) {
		List<Thrown> out = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<Throwable> todo = new ArrayDeque<>();
		if (thrown != null) todo.push(thrown);
		while (!todo.isEmpty() && out.size() < MAX_CHAIN) {
			Throwable t = todo.pop();
			if (!seen.add(t)) continue;
			String message;
			try {
				message = t.getMessage();
			} catch (RuntimeException e) {
				message = null;
			}
			if (message != null && message.length() > MAX_MESSAGE) message = message.substring(0, MAX_MESSAGE);
			out.add(new Thrown(t.getClass().getName(), message));
			Throwable[] suppressed = t.getSuppressed();
			for (int i = suppressed.length - 1; i >= 0; i--) {
				if (suppressed[i] != null) todo.push(suppressed[i]);
			}
			if (t.getCause() != null) todo.push(t.getCause());
		}
		return out;
	}

	static Named find(List<Thrown> chain) {
		// Mixin's own error for a mixin that failed names exactly that one.
		for (int i = 0; i < chain.size(); i++) {
			Matcher m = matcher(chain.get(i), FAILED);
			if (m != null) return named(chain, i, m);
		}
		// An injector's critical failure (InjectionError) bypasses that error, and Mixin's error handlers; its message
		// names its mixin first.
		for (int i = 0; i < chain.size(); i++) {
			Matcher m = matcher(chain.get(i), NAMED);
			if (m != null) return named(chain, i, m);
		}
		return null;
	}

	private static Matcher matcher(Thrown t, Pattern pattern) {
		if (t.message() == null || MIXIN_PACKAGES.stream().noneMatch(t.className()::startsWith)) return null;
		Matcher m = pattern.matcher(t.message());
		return m.find() ? m : null;
	}

	/** The mixin {@code m} found in {@code chain[at]}, with the deepest message from there on that names it as why. */
	private static Named named(List<Thrown> chain, int at, Matcher m) {
		String mixin = m.group(1) + ":" + m.group(2);
		String named = mixin + " from mod ";
		Thrown why = chain.get(at);
		for (int i = at + 1; i < chain.size(); i++) {
			Thrown t = chain.get(i);
			if (t.message() != null && t.message().contains(named) && MIXIN_PACKAGES.stream().anyMatch(t.className()::startsWith)) why = t;
		}
		String simpleName = why.className().substring(why.className().lastIndexOf('.') + 1);
		return new Named(mixin, m.group(3), simpleName + ": " + why.message());
	}

	/** Control characters as spaces, runs of white space as one, at most {@code max} characters. */
	static String clean(String text, int max) {
		String flat = text.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
		return flat.length() <= max ? flat : flat.substring(0, max - 3) + "...";
	}

	/** The recorded failures of a game directory, for one launch's builds. Thread-safe. */
	public static final class Log {
		private final Path file;
		private final Launch launch;
		private final Object lock = new Object();

		public Log(Path file, Launch launch) {
			this.file = Objects.requireNonNull(file, "file");
			this.launch = Objects.requireNonNull(launch, "launch");
		}

		/**
		 * The failures recorded with this launch's builds, except those of a mod whose jar is another now: those, and the
		 * others recorded with other builds, are dropped from the file. A file that can't be read or holds what isn't a
		 * record is noted in {@code notes} and left as it is.
		 *
		 * @param jars the hashes of the Fabric mods' jars found this launch, by mod id
		 */
		public List<Failure> load(Map<String, String> jars, List<String> notes) {
			synchronized (lock) {
				Map<String, Object> root;
				try {
					root = read();
				} catch (IOException | JsonException e) {
					notes.add(file.getFileName() + " could not be read, so no mixin is left out for stopping a launch: " + e.getMessage());
					return List.of();
				}
				if (root == null) return List.of();
				List<Failure> kept = new ArrayList<>();
				List<Object> entries = new ArrayList<>();
				boolean dropped = false;
				for (Object o : root.get("failures") instanceof List<?> list ? list : List.of()) {
					Failure f = o instanceof Map<?, ?> m ? failure(m) : null;
					if (f == null) {
						notes.add(file.getFileName() + " holds an entry that isn't a failure Fabricated recorded; it is left out of the file");
						dropped = true;
						continue;
					}
					String jar = jars.get(f.mod());
					if (!launch.equals(launchOf((Map<?, ?>) o)) || jar != null && !jar.equals(f.sha256())) {
						dropped = true;
						continue;
					}
					entries.add(o);
					if (jar != null) kept.add(f);
				}
				if (dropped) {
					try {
						write(entries);
					} catch (IOException e) {
						notes.add(file.getFileName() + " could not be rewritten without the failures that no longer hold: " + e.getMessage());
					}
				}
				return List.copyOf(kept);
			}
		}

		/**
		 * Records a failure with this launch's builds; returns whether it was new. The oldest records go past
		 * {@link #MAX_ENTRIES}. It runs as the game stops, so it never throws: what fails is returned in {@code notes}.
		 */
		public boolean record(Failure failure, List<String> notes) {
			if (!failure.valid()) {
				notes.add("not recorded, as it names no Fabric mod's mixin: " + failure);
				return false;
			}
			synchronized (lock) {
				try {
					List<Object> entries = new ArrayList<>();
					Map<String, Object> root;
					try {
						root = read();
					} catch (IOException | JsonException e) {
						// A file that can't be read is replaced: what it held can't be told apart from what it should.
						root = null;
					}
					if (root != null && root.get("failures") instanceof List<?> list) {
						for (Object o : list) {
							Failure f = o instanceof Map<?, ?> m ? failure(m) : null;
							if (f == null) continue;
							if (f.sameAs(failure) && launch.equals(launchOf((Map<?, ?>) o))) return false;
							entries.add(o);
						}
					}
					entries.add(entry(failure));
					while (entries.size() > MAX_ENTRIES) entries.remove(0);
					write(entries);
					return true;
				} catch (IOException | RuntimeException e) {
					notes.add("could not record it in " + file + ": " + e);
					return false;
				}
			}
		}

		private Map<String, Object> read() throws IOException, JsonException {
			byte[] bytes;
			try {
				if (Files.size(file) > MAX_FILE) throw new IOException("it is larger than " + (MAX_FILE >> 10) + " KiB");
				bytes = Files.readAllBytes(file);
			} catch (NoSuchFileException e) {
				return null;
			}
			Map<String, Object> root = Json.parseObject(new String(bytes, StandardCharsets.UTF_8), MAX_FILE);
			if (!(root.get("version") instanceof Number n) || n.intValue() != VERSION) throw new JsonException("it is of another version");
			return root;
		}

		private void write(List<Object> entries) throws IOException {
			if (entries.isEmpty()) {
				Files.deleteIfExists(file);
				return;
			}
			Map<String, Object> root = new LinkedHashMap<>();
			root.put("about", ABOUT);
			root.put("version", VERSION);
			root.put("failures", entries);
			Path dir = file.toAbsolutePath().getParent();
			Files.createDirectories(dir);
			AtomicFiles.writeAtomically(file, (Json.write(root, true) + "\n").getBytes(StandardCharsets.UTF_8), dir);
		}

		private Map<String, Object> entry(Failure f) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("mod", f.mod());
			m.put("sha256", f.sha256());
			m.put("mixin", f.mixin());
			m.put("reason", f.reason());
			m.put("fabricated", launch.fabricatedVersion());
			m.put("build", launch.fabricatedBuild());
			m.put("minecraft", launch.minecraftVersion());
			m.put("loader", launch.loaderVersion());
			m.put("recorded", Instant.now().toString());
			return m;
		}

		private static Failure failure(Map<?, ?> m) {
			if (!(m.get("mod") instanceof String mod) || !(m.get("sha256") instanceof String sha) || !(m.get("mixin") instanceof String mixin)) return null;
			Failure f = new Failure(mod, sha, mixin, m.get("reason") instanceof String r ? r : "");
			return f.valid() ? f : null;
		}

		private static Launch launchOf(Map<?, ?> m) {
			return new Launch(string(m, "fabricated"), string(m, "build"), string(m, "minecraft"), string(m, "loader"));
		}

		private static String string(Map<?, ?> m, String key) {
			return m.get(key) instanceof String s ? s : "";
		}
	}
}
