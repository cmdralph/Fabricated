package io.github.cmdralph.fabricated.core.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.GamePackages;
import io.github.cmdralph.fabricated.core.bytecode.InstalledClasses;
import io.github.cmdralph.fabricated.core.remap.OfficialClasses;

/**
 * CI's guard of LEAVE-6's invoker bridges on a platform build: as the game starts, Fabricated proves that the platform's
 * method passes the parameter it added only to methods of its own code that ignore it ({@link AddedParameters}), but
 * not that the overrides of those methods in the game's and the platform's classes ignore it too (NeoForge's
 * {@code TntBlock.onCaughtFire}), as a scan of every class would cost each launch. This reads them once per pinned build:
 * every vanilla method of a game class the platform patched (one that names the platform's packages) that the verdict
 * accepts, and for each call its parameter reaches, every method of the game's and the platform's classes overriding
 * the code that call runs (a class or interface declaring it below the call's receiver type or that code's owner),
 * judged as the code it overrides ({@link AddedParameters#overrideReads}), and theirs in turn. One line says what it
 * found: {@code OK} with the bridged methods and the overrides, or {@code FAIL} naming the override that reads the
 * parameter.
 * <p>
 * Usage: {@code InvokerBridgeGuard <loader> <vanilla jar> [--mappings <Mojang's mappings>] <game jar>...}: the game's
 * jars in the order the game finds classes in them (the patched classes first, the platform's own, vanilla's for the
 * classes the platform left as they were), and vanilla's game in the names the game runs with, a server bundler's
 * nested jar read where it is one, and read through Mojang's mappings where they are given (an obfuscated game). Exit
 * status: 0 for OK, 1 for FAIL, 2 where the jars can't be read.
 */
public final class InvokerBridgeGuard {
	/** The start of the line CI's check reads ({@code ci/checks.tsv}'s {@code invoker-bridge-overrides}). */
	static final String LINE = "FABRICATED-GUARD invoker bridge overrides";
	private static final String BUNDLED_VERSIONS = "META-INF/versions.list";

	private InvokerBridgeGuard() {
	}

	/** What the guard found: each bridged method, its value and the overrides, and each override reading the parameter. */
	record Result(List<String> bridged, List<String> overrides, List<String> reading) {
		boolean ok() {
			return reading.isEmpty();
		}

		/** The line CI checks, with how long the guard took. */
		String line(long millis) {
			String found = bridged.isEmpty() ? "no method is bridged" : String.join("; ", bridged);
			String tail = overrides.isEmpty() ? "no game or platform class overrides what the parameters reach"
					: "overrides ignoring the parameter: " + String.join(", ", overrides);
			if (!ok()) return LINE + " FAIL in " + millis + " ms: " + found + "; " + String.join("; ", reading);
			return LINE + " OK in " + millis + " ms: " + found + "; " + tail;
		}
	}

	public static void main(String[] args) {
		long start = System.nanoTime();
		if (args.length < 3) {
			System.out.println(LINE + " ERROR: usage: InvokerBridgeGuard <loader> <vanilla jar> [--mappings <file>] <game jar>...");
			System.exit(2);
		}
		int status;
		List<ZipFile> opened = new ArrayList<>();
		try {
			int next = 2;
			Path mappings = null;
			if (args[next].equals("--mappings")) {
				mappings = Path.of(args[next + 1]);
				next += 2;
			}
			// Each class from the first jar holding it, as the game finds it; read in parallel, as most of the time goes there.
			Map<String, ZipEntry> entries = new LinkedHashMap<>();
			Map<String, ZipFile> jars = new HashMap<>();
			for (int i = next; i < args.length; i++) {
				ZipFile jar = new ZipFile(args[i]);
				opened.add(jar);
				for (Enumeration<? extends ZipEntry> e = jar.entries(); e.hasMoreElements(); ) {
					ZipEntry entry = e.nextElement();
					String name = entry.getName();
					if (!name.endsWith(".class") || entry.isDirectory()) continue;
					String internal = name.substring(0, name.length() - ".class".length());
					if (entries.putIfAbsent(internal, entry) == null) jars.put(internal, jar);
				}
			}
			Map<String, byte[]> game = new ConcurrentHashMap<>();
			entries.keySet().parallelStream().forEach(name -> {
				try (InputStream in = jars.get(name).getInputStream(entries.get(name))) {
					game.put(name, in.readAllBytes());
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			ZipFile vanillaJar = vanillaJar(Path.of(args[1]), opened);
			ClassBytesSource vanilla = name -> {
				ZipEntry entry = vanillaJar.getEntry(name + ".class");
				if (entry == null) return null;
				try (InputStream in = vanillaJar.getInputStream(entry)) {
					return in.readAllBytes();
				}
			};
			// The same bytes as vanilla's, by the archives' checksums, which needs no reading: only in the same names.
			Predicate<String> unchanged = name -> {
				ZipEntry entry = vanillaJar.getEntry(name + ".class");
				return entry != null && entry.getCrc() == entries.get(name).getCrc() && entry.getSize() == game.get(name).length;
			};
			ClassBytesSource members = vanilla;
			if (mappings != null) {
				Path file = mappings;
				OfficialClasses official = new OfficialClasses(vanilla, () -> Files.newBufferedReader(file, StandardCharsets.UTF_8));
				vanilla = official;
				members = official::readMembers;
				unchanged = name -> false;
			}
			Result result = check(args[0], game, vanilla, members, unchanged);
			System.out.println(result.line((System.nanoTime() - start) / 1_000_000));
			status = result.ok() ? 0 : 1;
		} catch (IOException | RuntimeException e) {
			System.out.println(LINE + " ERROR: " + e);
			status = 2;
		}
		for (ZipFile z : opened) {
			try {
				z.close();
			} catch (IOException ignored) {
				// read only
			}
		}
		System.exit(status);
	}

	/** The archive of vanilla's classes: {@code jar}, or the game jar a server bundler holds (its first listed version). */
	private static ZipFile vanillaJar(Path jar, List<ZipFile> opened) throws IOException {
		ZipFile zip = new ZipFile(jar.toFile());
		opened.add(zip);
		ZipEntry list = zip.getEntry(BUNDLED_VERSIONS);
		if (list == null) return zip;
		String first;
		try (InputStream in = zip.getInputStream(list)) {
			first = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
		}
		String[] fields = first.split("\t");
		ZipEntry nested = fields.length == 3 ? zip.getEntry("META-INF/versions/" + fields[2]) : null;
		if (nested == null) throw new IOException(jar + " lists no game jar it holds");
		Path extracted = Files.createTempFile("fabricated-guard-vanilla", ".jar");
		extracted.toFile().deleteOnExit();
		try (InputStream in = zip.getInputStream(nested)) {
			Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
		}
		ZipFile game = new ZipFile(extracted.toFile());
		opened.add(game);
		return game;
	}

	/**
	 * The guard over {@code game}, every class the game runs by internal name, and {@code vanilla}'s classes in the same
	 * names, for {@code loader} as messages name it: {@code members} reads vanilla's classes at least with their members,
	 * and {@code unchanged} tells, where it can without reading them, the classes the platform left as vanilla's.
	 */
	static Result check(String loader, Map<String, byte[]> game, ClassBytesSource vanilla, ClassBytesSource members, Predicate<String> unchanged) {
		MixinPreflight preflight = new MixinPreflight(new ClassIndex(game::get), InstalledClasses.NONE, ClassBytesSource.EMPTY, ClassBytesSource.EMPTY, false,
				true, vanilla, loader);
		AddedParameters parameters = preflight.addedParameters;
		List<String> bridged = new ArrayList<>();
		Set<String> overrides = new LinkedHashSet<>();
		Set<String> reading = new LinkedHashSet<>();
		Map<String, List<String>> mentioning = new HashMap<>();
		Map<String, String> text = new HashMap<>();
		for (Map.Entry<String, List<MethodNode>> e : missing(game, members, unchanged).entrySet()) {
			String name = e.getKey();
			ClassNode target = parameters.codeOf(name);
			if (target == null) continue;
			for (MethodNode m : e.getValue()) {
				AddedParameters.Verdict verdict = parameters.of(target, m.name, m.desc);
				if (verdict == null || verdict.bridge() == null) continue;
				String method = AddedParameters.simpleName(name) + "." + m.name
						+ (verdict.method().name.equals(m.name) ? "" : " (" + verdict.method().name + ")");
				bridged.add(method + " with " + verdict.typeName() + "." + verdict.bridge().constant());
				guard(parameters, game, text, mentioning, method, verdict.reached(), overrides, reading);
			}
		}
		return new Result(List.copyOf(bridged), List.copyOf(overrides), List.copyOf(reading));
	}

	/**
	 * Every override of what each of {@code reached} runs, and of what they reach in turn, judged: its name added to
	 * {@code overrides} where it ignores the parameter, else to {@code reading}, with why, under {@code method}.
	 */
	private static void guard(AddedParameters parameters, Map<String, byte[]> game, Map<String, String> text, Map<String, List<String>> mentioning, String method,
			List<AddedParameters.Reached> reached, Set<String> overrides, Set<String> reading) {
		List<AddedParameters.Reached> queue = new ArrayList<>(reached);
		Set<String> seen = new LinkedHashSet<>();
		for (int i = 0; i < queue.size(); i++) {
			AddedParameters.Reached r = queue.get(i);
			if (r.call().getOpcode() == Opcodes.INVOKESTATIC || !seen.add(r.code().owner() + "." + r.call().name + r.call().desc + "@" + r.index())) continue;
			for (String owner : mentioning.computeIfAbsent(r.call().name, n -> mentioning(game, text, n))) {
				if (owner.equals(r.code().owner())) continue;
				ClassNode node = parameters.codeOf(owner);
				MethodNode override = node == null ? null : PreflightAsm.declared(node, r.call().name, r.call().desc);
				if (override == null || (override.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
						|| override.instructions.size() == 0) {
					continue;
				}
				if (!parameters.isSubtype(owner, r.call().owner) && !parameters.isSubtype(owner, r.code().owner())) continue;
				String named = AddedParameters.simpleName(owner) + "." + override.name;
				List<AddedParameters.Reached> more = new ArrayList<>();
				String why = parameters.overrideReads(owner, override, r, more);
				if (why == null) {
					overrides.add(named);
					queue.addAll(more);
				} else {
					reading.add(method + ": " + named + ", overriding " + AddedParameters.simpleName(r.code().owner()) + "." + r.call().name
							+ ", doesn't ignore it: " + why);
				}
			}
		}
	}

	/**
	 * Each game class the platform changed with the instance methods with code of vanilla's it lacks, in name order:
	 * where it left a class as vanilla's, the class has vanilla's methods, and one it added has no vanilla methods to
	 * stand in for. {@code vanilla} reads vanilla's classes with their members at least. Worked out in parallel, as
	 * reading vanilla's classes takes most of the time (through Mojang's mappings on an obfuscated game).
	 */
	private static Map<String, List<MethodNode>> missing(Map<String, byte[]> game, ClassBytesSource vanilla, Predicate<String> unchanged) {
		Map<String, List<MethodNode>> missing = new ConcurrentHashMap<>();
		game.keySet().parallelStream().filter(name -> GamePackages.isGame(name) && !unchanged.test(name)).forEach(name -> {
			byte[] mine = game.get(name);
			byte[] theirs = read(vanilla, name);
			if (theirs == null || Arrays.equals(theirs, mine)) return;
			ClassNode members = members(mine);
			List<MethodNode> lacking = new ArrayList<>();
			for (MethodNode m : members(theirs).methods) {
				if ((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0 && !m.name.startsWith("<")
						&& !PreflightAsm.declares(members, m.name, m.desc)) {
					lacking.add(m);
				}
			}
			if (!lacking.isEmpty()) missing.put(name, lacking);
		});
		return new TreeMap<>(missing);
	}

	/** The class's members, without their code. */
	private static ClassNode members(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return node;
	}

	private static byte[] read(ClassBytesSource source, String name) {
		try {
			return source.read(name);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The classes of the game whose bytes hold {@code name} (a method declaring it among them), in name order: each
	 * class's bytes read as Latin-1 text once ({@code text}), which the JDK searches fast.
	 */
	private static List<String> mentioning(Map<String, byte[]> game, Map<String, String> text, String name) {
		String wanted = new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, byte[]> e : game.entrySet()) {
			if (text.computeIfAbsent(e.getKey(), k -> new String(e.getValue(), StandardCharsets.ISO_8859_1)).contains(wanted)) found.add(e.getKey());
		}
		found.sort(null);
		return found;
	}
}
