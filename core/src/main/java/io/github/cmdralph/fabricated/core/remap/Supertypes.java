package io.github.cmdralph.fabricated.core.remap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;

/**
 * The direct supertypes of classes, by intermediary name: what remapping a Fabric jar knows of the classes it names, so
 * that a mod's override of a method Intermediary leaves under its official name, and the runtime renames, takes the
 * runtime's name too where its descriptor alone doesn't tell (CORPUS-86: Moonlight's {@code CaveFilter.Type} overrides
 * {@code PlacementModifierType.codec()}, which SRG names {@code m_191869_}).
 */
@FunctionalInterface
public interface Supertypes {
	/** Knows no class. */
	Supertypes NONE = name -> null;

	/**
	 * @param internalName a class's intermediary internal name
	 * @return its superclass (if any), then its interfaces, by intermediary name; null if the class isn't known
	 */
	List<String> of(String internalName);

	/** These classes, then {@code next}'s for a class these don't know. */
	default Supertypes or(Supertypes next) {
		return name -> {
			List<String> found = of(name);
			return found != null ? found : next.of(name);
		};
	}

	/**
	 * Classes read from their bytes, by intermediary name, as a Fabric jar has them.
	 *
	 * @param classBytes a class's bytes by its internal name (without {@code .class}), or null when there is no such class
	 */
	static Supertypes ofClassFiles(Function<String, byte[]> classBytes) {
		return name -> {
			byte[] data = classBytes.apply(name);
			if (data == null) return null;
			try {
				return of(new ClassReader(data));
			} catch (RuntimeException e) {
				return null; // not a class remapping can read; remapping it reports the problem
			}
		};
	}

	/**
	 * Classes known by their runtime names (the game's), to their intermediary ones.
	 *
	 * @param runtimeSupertypes a class's superclass, then interfaces, by runtime names, or null when it isn't known
	 */
	static Supertypes ofRuntime(Function<String, List<String>> runtimeSupertypes, MappingTable table) {
		return name -> {
			List<String> runtime = runtimeSupertypes.apply(table.mapClass(name));
			if (runtime == null) return null;
			List<String> intermediary = new ArrayList<>(runtime.size());
			for (String type : runtime) intermediary.add(table.unmapClass(type));
			return intermediary;
		};
	}

	/** The superclass (if any), then the interfaces, of the class a reader reads. */
	static List<String> of(ClassReader reader) {
		List<String> types = new ArrayList<>();
		if (reader.getSuperName() != null) types.add(reader.getSuperName());
		types.addAll(List.of(reader.getInterfaces()));
		return types;
	}
}
