package io.github.cmdralph.fabricated.core.accesswidener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Access;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.Entry;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;

/**
 * Converts access widener / class tweaker directives (already in runtime names) to NeoForge access
 * transformer lines.
 *
 * <p>NeoForge's AT engine only ever widens access, so {@code private-f} means "keep the current access,
 * remove {@code final}", which is exactly what {@code mutable} needs. Directives for the same target are
 * merged into one line. Members widened by Fabric also widen their owning class, as Fabric does.
 * <p>Fabric also makes a private instance method made accessible final, unless a mod makes it extendable: it merges
 * all mods' wideners first. NeoForge reads each mod's file on its own and refuses one's {@code +f} next to another's
 * {@code -f} for the same member, which stopped the game (AT-1), so that final isn't added: where another mod extends
 * the method the merged line is Fabric's, and otherwise the method is only left overridable.
 * {@code inject-interface} has no AT equivalent and is returned separately for the interface-injection
 * class processor.
 */
public final class AccessTransformerConverter {
	private AccessTransformerConverter() {
	}

	/**
	 * Fields NeoForge's {@code ReplaceFieldWithGetterAccess} coremod requires to stay private (it throws while
	 * loading the class otherwise, which crashes the game), with the getter it reads them through instead, which
	 * NeoForge's patches add to the class (read in {@code NeoForgeCoreMod} of NeoForge 21.11 and 26.x). A field
	 * counts only where the game's class has that getter: only NeoForge's 26.3 build replaces {@code Biome.attributes},
	 * which its 21.11, 26.1 and 26.2 builds leave a plain field Fabric API's biome module widens (BIOME-2).
	 */
	static final Map<String, String> PLATFORM_PRIVATE_FIELDS = Map.of(
			"net/minecraft/world/level/biome/Biome climateSettings", "getModifiedClimateSettings",
			"net/minecraft/world/level/biome/Biome specialEffects", "getModifiedSpecialEffects",
			"net/minecraft/world/level/biome/Biome attributes", "getModifiedEnvironmentAttributes",
			"net/minecraft/world/level/levelgen/structure/Structure settings", "getModifiedStructureSettings",
			"net/minecraft/world/level/block/FlowerPotBlock potted", "getPotted");

	/** Conversion output. */
	public record Result(String accessTransformer, List<InjectedInterface> injectedInterfaces, List<String> warnings, int lineCount) {
		public boolean isEmpty() {
			return lineCount == 0;
		}
	}

	/** In widening order: a lower ordinal is more accessible. */
	private enum Modifier {
		PUBLIC, PROTECTED, DEFAULT, PRIVATE
	}

	private static final class Target {
		Modifier modifier = Modifier.PRIVATE;
		boolean removeFinal;

		void widen(Modifier m) {
			if (m.ordinal() < modifier.ordinal()) modifier = m;
		}

		String render(String target) {
			return modifier.name().toLowerCase(Locale.ROOT) + (removeFinal ? "-f" : "") + " " + target;
		}
	}

	/**
	 * @param game index of runtime game classes used to validate targets; may be {@code null}
	 */
	/** Whether a NeoForge coremod reads this field through its getter here; without the game's classes, whether one may. */
	private static boolean platformPrivate(ClassIndex.ClassInfo owner, String field) {
		String getter = PLATFORM_PRIVATE_FIELDS.get(field);
		return getter != null && (owner == null || owner.methods().keySet().stream().anyMatch(m -> m.startsWith(getter + "(")));
	}

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
					if (e.access() == Access.ACCESSIBLE && platformPrivate(owner, e.owner() + " " + e.name())) {
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
