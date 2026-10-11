package io.github.cmdralph.fabricated.core.accesswidener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Access;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Entry;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Kind;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

import org.objectweb.asm.Opcodes;

/**
 * Converts access widener / class tweaker directives (already in runtime names) to NeoForge access
 * transformer lines.
 *
 * <p>NeoForge's AT engine only ever widens access, so {@code private-f} means "keep the current access,
 * remove {@code final}", which is exactly what {@code mutable} needs. Directives for the same target are
 * merged into one line. Members widened by Fabric also widen their owning class, as Fabric does.
 * {@code inject-interface} has no AT equivalent and is returned separately for the interface-injection
 * class processor.
 */
public final class AccessTransformerConverter {
	private AccessTransformerConverter() {
	}

	/**
	 * Fields NeoForge's {@code ReplaceFieldWithGetterAccess} coremod requires to stay private (it throws while
	 * loading the class otherwise, which crashes the game). Union of NeoForge 21.11 and 26.x
	 * ({@code NeoForgeCoreMod}); entries that do not exist on a version are simply never matched.
	 */
	static final Set<String> PLATFORM_PRIVATE_FIELDS = Set.of(
			"net/minecraft/world/level/biome/Biome climateSettings",
			"net/minecraft/world/level/biome/Biome specialEffects",
			"net/minecraft/world/level/biome/Biome attributes",
			"net/minecraft/world/level/levelgen/structure/Structure settings",
			"net/minecraft/world/level/block/FlowerPotBlock potted");

	/** Conversion output. */
	public record Result(String accessTransformer, List<InjectedInterface> injectedInterfaces, List<String> warnings, int lineCount) {
		public boolean isEmpty() {
			return lineCount == 0;
		}
	}

	private enum Modifier {
		PUBLIC("public"), PROTECTED("protected"), DEFAULT("default"), PRIVATE("private");

		final String token;

		Modifier(String token) {
			this.token = token;
		}
	}

	private enum Final {
		LEAVE(""), REMOVE("-f"), ADD("+f");

		final String suffix;

		Final(String suffix) {
			this.suffix = suffix;
		}
	}

	private static final class Target {
		Modifier modifier = Modifier.PRIVATE;
		boolean removeFinal;
		boolean addFinal;

		void widen(Modifier m) {
			if (m.ordinal() < modifier.ordinal()) modifier = m;
		}

		String render(String target) {
			Final f = removeFinal ? Final.REMOVE : addFinal ? Final.ADD : Final.LEAVE;
			return modifier.token + f.suffix + " " + target;
		}
	}

	/**
	 * @param game index of runtime game classes used to validate targets and to apply Fabric's
	 *             "private methods made accessible become final" rule; may be {@code null}
	 */
	public static Result convert(AccessWidenerFile file, ClassIndex game) {
		Map<String, Target> targets = new TreeMap<>();
		List<String> warnings = new ArrayList<>();

		for (Entry e : file.entries()) {
			ClassIndex.ClassInfo owner = game != null ? game.get(e.owner()) : null;
			if (game != null && owner == null) {
				warnings.add("line " + e.line() + ": class " + e.owner() + " does not exist on this Minecraft/NeoForge version; ignored");
				continue;
			}
			String ownerDotted = e.owner().replace('/', '.');
			switch (e.kind()) {
				case CLASS -> {
					Target t = targets.computeIfAbsent(ownerDotted, k -> new Target());
					t.widen(Modifier.PUBLIC);
					if (e.access() == Access.EXTENDABLE) t.removeFinal = true;
				}
				case METHOD -> {
					Integer access = owner != null ? owner.methodAccess(e.name() + e.descriptor()) : null;
					if (game != null && access == null) {
						warnings.add("line " + e.line() + ": method " + e.owner() + "." + e.name() + e.descriptor() + " does not exist; ignored");
						continue;
					}
					Target t = targets.computeIfAbsent(ownerDotted + " " + e.name() + e.descriptor(), k -> new Target());
					Target cls = targets.computeIfAbsent(ownerDotted, k -> new Target());
					cls.widen(Modifier.PUBLIC);
					if (e.access() == Access.ACCESSIBLE) {
						t.widen(Modifier.PUBLIC);
						boolean wasPrivate = access != null && (access & Opcodes.ACC_PRIVATE) != 0;
						boolean isStatic = access != null && (access & Opcodes.ACC_STATIC) != 0;
						if (wasPrivate && !isStatic && !e.name().equals("<init>")) t.addFinal = true;
					} else {
						t.widen(Modifier.PROTECTED);
						t.removeFinal = true;
						cls.removeFinal = true;
					}
				}
				case FIELD -> {
					Integer access = owner != null ? owner.fieldAccess(e.name() + ":" + e.descriptor()) : null;
					if (game != null && access == null) {
						warnings.add("line " + e.line() + ": field " + e.owner() + "." + e.name() + " does not exist; ignored");
						continue;
					}
					if (e.access() == Access.ACCESSIBLE && PLATFORM_PRIVATE_FIELDS.contains(e.owner() + " " + e.name())) {
						warnings.add("line " + e.line() + ": field " + e.owner() + "." + e.name()
								+ " must stay private on NeoForge (a NeoForge coremod replaces it with a getter); not widened. "
								+ "Code reading it directly fails when it runs.");
						continue;
					}
					Target t = targets.computeIfAbsent(ownerDotted + " " + e.name(), k -> new Target());
					if (e.access() == Access.ACCESSIBLE) {
						t.widen(Modifier.PUBLIC);
						targets.computeIfAbsent(ownerDotted, k -> new Target()).widen(Modifier.PUBLIC);
					} else {
						t.removeFinal = true;
					}
				}
			}
		}

		// Extendable wins over Fabric's implicit "+f" for accessible private methods.
		for (Target t : targets.values()) {
			if (t.removeFinal) t.addFinal = false;
		}

		List<InjectedInterface> injections = new ArrayList<>();
		for (InjectedInterface ii : file.injectedInterfaces()) {
			if (game != null && game.get(ii.target()) == null) {
				warnings.add("line " + ii.line() + ": interface injection target " + ii.target() + " does not exist; ignored");
				continue;
			}
			injections.add(ii);
		}

		StringBuilder sb = new StringBuilder("# Generated by Fabricated from a Fabric ").append(file.format()).append(". Do not edit.\n");
		for (Map.Entry<String, Target> e : targets.entrySet()) {
			sb.append(e.getValue().render(e.getKey())).append('\n');
		}
		return new Result(sb.toString(), List.copyOf(injections), List.copyOf(warnings), targets.size());
	}
}
