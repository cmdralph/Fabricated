package io.github.cmdralph.fabricated.core.mixin;

/**
 * The injection point pre-flight names in place of an {@code @Inject}'s point into a constructor where the running
 * Mixin takes one there only at a return: SpongePowered's 0.8.5, which Forge 47 runs, has no {@code unsafe} points
 * (CORPUS-103). Fabricated's in-game mod there registers it with Mixin under {@link #NAMESPACE} and {@link #CODE}. It
 * finds what the point it stands for finds ({@link #POINT}, with that point's target, ordinal, opcode, slice and
 * arguments), moved as that point was ({@link #SHIFT}), and Mixin takes what it finds anywhere in a constructor, as
 * Fabric's Mixin takes its points, which are {@code unsafe} by default; an instance handler still only after the
 * delegate constructor call.
 */
public final class ConstructorPoint {
	/** The namespace the in-game mod registers the point under. */
	public static final String NAMESPACE = "fabricated";
	/** The point's code in that namespace. */
	public static final String CODE = "CONSTRUCTOR";
	/** How an {@code @At} names the point: Mixin upper-cases a registered point's namespace and code. */
	public static final String AT = "FABRICATED:CONSTRUCTOR";
	/** The argument naming the point it stands for: {@code INVOKE}, {@code INVOKE_ASSIGN}, {@code INVOKE_STRING}, {@code FIELD} or {@code NEW}. */
	public static final String POINT = "fabricated.point";
	/** The argument giving how many instructions Mixin moved that point by: -1 for {@code BEFORE}, 1 for {@code AFTER}, {@code by} for {@code BY}. */
	public static final String SHIFT = "fabricated.shift";

	private ConstructorPoint() {
	}
}
