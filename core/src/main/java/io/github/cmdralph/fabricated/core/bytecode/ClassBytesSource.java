package io.github.cmdralph.fabricated.core.bytecode;

import java.io.IOException;

/** Supplies raw class file bytes by internal name. Implementations never define or load classes. */
@FunctionalInterface
public interface ClassBytesSource {
	/**
	 * @param internalName e.g. {@code net/minecraft/world/level/Level}
	 * @return the class file bytes, or {@code null} if this source does not contain the class
	 */
	byte[] read(String internalName) throws IOException;

	/** A source with no classes. */
	ClassBytesSource EMPTY = name -> null;

	/** Tries {@code this} first, then {@code other}. */
	default ClassBytesSource or(ClassBytesSource other) {
		return name -> {
			byte[] data = read(name);
			return data != null ? data : other.read(name);
		};
	}
}
