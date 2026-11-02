package io.github.cmdralph.fabricated.core.bytecode;

import java.util.List;

/**
 * Game fields a platform types as a subtype of vanilla's type (CORPUS-33): Forge 61 to 66 make
 * {@code ChunkGenerator.featuresPerStep}, vanilla's {@code Supplier}, a {@code ClearableLazy}, which Forge reads and
 * invalidates. Each comes with the static method on the platform's type ({@code wrapOwner}, an interface) that makes the
 * platform's type from vanilla's, as the platform makes its own value.
 */
public final class PlatformRetypes {
	/** A field the platform types {@code platformDesc}, a subtype of vanilla's {@code vanillaDesc}. */
	public record Retype(String owner, String name, String vanillaDesc, String platformDesc, String wrapOwner, String wrapName) {
		/** The descriptor of the static method making the platform's type from vanilla's. */
		public String wrapDesc() {
			return "(" + vanillaDesc + ")" + platformDesc;
		}
	}

	public static final List<Retype> KNOWN = List.of(new Retype("net/minecraft/world/level/chunk/ChunkGenerator", "featuresPerStep",
			"Ljava/util/function/Supplier;", "Lnet/minecraftforge/common/util/ClearableLazy;", "net/minecraftforge/common/util/ClearableLazy",
			"concurrentOf"));

	private PlatformRetypes() {
	}

	/**
	 * The retype of a field named with vanilla's type, where the game's class has the platform's type and not vanilla's;
	 * null otherwise.
	 */
	public static Retype of(ClassIndex game, String owner, String name, String vanillaDesc) {
		if (game == null) return null;
		ClassIndex.ClassInfo info = game.get(owner);
		if (info == null || info.fieldAccess(name + ":" + vanillaDesc) != null) return null;
		for (Retype r : KNOWN) {
			if (r.owner().equals(owner) && r.name().equals(name) && r.vanillaDesc().equals(vanillaDesc)
					&& info.fieldAccess(name + ":" + r.platformDesc()) != null) return r;
		}
		return null;
	}
}
