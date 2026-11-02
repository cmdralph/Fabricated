package io.github.cmdralph.fabricated.core.translate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Pre-flight's view of mixins that use a copy of MixinExtras their mod relocated into its own package: Essential's, in
 * {@code gg/essential/lib/mixinextras}, which its copy registers with Mixin as it starts (MIXEXT-3). Pre-flight knows
 * MixinExtras' injectors and sugar by MixinExtras' own package, so it judged such a mixin's {@code @WrapWithCondition}
 * as no injector and its {@code @Local} parameters as the handler's own. Essential's screen drawing hooks on 26.1.2
 * capture the arguments of a call NeoForge's code no longer makes in a group that needs one of them to apply; pre-flight
 * left the group in, Mixin failed it after it had applied the mixin's other hooks (Essential's error handler lets the
 * game go on), MixinExtras never finished the class, and the next frame crashed on a reference it never set up.
 * <p>
 * The mod's class bytes are read with the relocated names as MixinExtras' own; what pre-flight then writes, its repaired
 * classes, and the handler a finding names, get the relocated names back, so the mod's classes keep using its own copy,
 * as they do on Fabric. A class naming both MixinExtras' own package and a relocated one, or several relocated ones, is
 * read as it is.
 */
final class RelocatedMixinExtras {
	private static final String OWN = "com/llamalad7/";
	/** A class of MixinExtras' API (injectors, sugar, expressions) in a package ending {@code mixinextras}: group 1 is the package's prefix. */
	private static final Pattern API = Pattern.compile("((?:[A-Za-z_$][A-Za-z0-9_$]*/)+)mixinextras/((?:injector|sugar|expression)/[A-Za-z0-9_$/]+)");
	/** The same, in MixinExtras' own package, as a descriptor names it. */
	private static final Pattern OWN_IN_DESCRIPTOR = Pattern.compile("L" + OWN + "mixinextras/((?:injector|sugar|expression)/[A-Za-z0-9_$/]+);");
	private static final byte[] MARK = "mixinextras/".getBytes(StandardCharsets.US_ASCII);

	/** The relocated package prefix each class read with MixinExtras' own names had, by its internal name. */
	private final Map<String, String> prefixes = new ConcurrentHashMap<>();

	/** {@code classes}, with each class that names a relocated MixinExtras read as naming MixinExtras' own. */
	ClassBytesSource view(ClassBytesSource classes) {
		return internalName -> canonical(internalName, classes.read(internalName));
	}

	/**
	 * {@code bytes} of {@code internalName} naming MixinExtras' own package where they name one relocated copy of it. The
	 * copy's own classes stay as they are.
	 */
	byte[] canonical(String internalName, byte[] bytes) {
		if (bytes == null || internalName.contains("mixinextras/") || indexOf(bytes, MARK) < 0) return bytes;
		Set<String> relocated = new HashSet<>();
		boolean[] own = {false};
		Remapper toOwn = new Remapper() {
			@Override
			public String map(String name) {
				Matcher m = API.matcher(name);
				if (!m.matches()) return name;
				if (m.group(1).equals(OWN)) {
					own[0] = true;
					return name;
				}
				relocated.add(m.group(1));
				return OWN + "mixinextras/" + m.group(2);
			}
		};
		byte[] remapped;
		try {
			remapped = remap(bytes, toOwn);
		} catch (RuntimeException e) {
			return bytes; // unreadable: pre-flight reports the class as it finds it
		}
		if (relocated.size() != 1 || own[0]) return bytes;
		prefixes.put(internalName, relocated.iterator().next());
		return remapped;
	}

	/** {@code bytes} of {@code internalName}, read with MixinExtras' own names, with its relocated names again. */
	byte[] relocated(String internalName, byte[] bytes) {
		String prefix = prefixes.get(internalName);
		if (prefix == null || bytes == null) return bytes;
		return remap(bytes, new Remapper() {
			@Override
			public String map(String name) {
				Matcher m = API.matcher(name);
				return m.matches() && m.group(1).equals(OWN) ? prefix + "mixinextras/" + m.group(2) : name;
			}
		});
	}

	/** Repaired classes (by entry name, {@code a/b/C.class}) with their relocated names again. */
	Map<String, byte[]> relocated(Map<String, byte[]> classes) {
		if (prefixes.isEmpty()) return classes;
		Map<String, byte[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> e : classes.entrySet()) {
			String name = e.getKey();
			out.put(name, name.endsWith(".class") ? relocated(name.substring(0, name.length() - ".class".length()), e.getValue()) : e.getValue());
		}
		return out;
	}

	/** {@code findings}, from {@code from} on, naming the handlers and types of their mixins by the relocated names. */
	void relocateFindings(List<PreflightFinding> findings, int from) {
		if (prefixes.isEmpty()) return;
		for (int i = from; i < findings.size(); i++) findings.set(i, relocated(findings.get(i)));
	}

	/** {@code findings} naming the handlers and types of their mixins by the relocated names. */
	List<PreflightFinding> relocated(List<PreflightFinding> findings) {
		if (prefixes.isEmpty()) return findings;
		List<PreflightFinding> out = new ArrayList<>(findings.size());
		for (PreflightFinding f : findings) out.add(relocated(f));
		return out;
	}

	private PreflightFinding relocated(PreflightFinding f) {
		String prefix = f.mixin() == null ? null : prefixes.get(f.mixin().replace('.', '/'));
		if (prefix == null) return f;
		return new PreflightFinding(f.severity(), f.config(), f.mixin(), relocated(f.message(), prefix), relocated(f.suggestion(), prefix),
				f.handler() == null ? null : relocated(f.handler(), prefix));
	}

	private static String relocated(String text, String prefix) {
		if (text == null || !text.contains(OWN)) return text;
		return OWN_IN_DESCRIPTOR.matcher(text).replaceAll(Matcher.quoteReplacement("L" + prefix + "mixinextras/") + "$1;");
	}

	private static byte[] remap(byte[] bytes, Remapper remapper) {
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(bytes).accept(new ClassRemapper(writer, remapper), 0);
		return writer.toByteArray();
	}

	private static int indexOf(byte[] data, byte[] needle) {
		outer:
		for (int i = 0; i <= data.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) continue outer;
			}
			return i;
		}
		return -1;
	}
}
