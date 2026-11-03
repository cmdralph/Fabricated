package io.github.cmdralph.fabricated.core.runtime;

/**
 * A throw held where a mod's code called the code that threw, until Fabricated's own call into that mod's code returns,
 * on this thread (FRAPI rule 8 for wrapped models).
 *
 * <p>Fabricated catches what a Fabric block model throws where it calls into the model, and draws the block another way.
 * A model wrapping another (Continuity's connected texture models) may keep state through its wrapped model's call: a
 * per-thread transform it marks active, then resets once the call returns. A throw from the wrapped model unwinds the
 * wrapper with it, and that state stays as the call left it, on that thread, for the rest of the session; on Fabric the
 * same throw ends the game, so no mod guards against it. Held where the wrapper called its wrapped model, the throw
 * lets the wrapper finish, and Fabricated takes it back when its own call returns, as if the outermost model threw it.
 *
 * <p>{@link #begin} and {@link #end} bracket Fabricated's call; a caller holding throws (where a wrapper calls its
 * wrapped model) asks {@link #holding} first, and passes what it caught to {@link #hold}, rethrowing it when that is
 * false. Calls nest: a call begun inside another holds its own throws, and the outer call's come back to it after.
 */
public final class HeldThrows {
	private static final ThreadLocal<HeldThrows> CURRENT = ThreadLocal.withInitial(HeldThrows::new);

	/** How many calls holding throws are under way on this thread. */
	private int depth;
	/** The first throw held during the innermost of them, or null. */
	private Throwable held;

	private HeldThrows() {
	}

	/** Begins a call that holds throws on this thread: pass what this returns (the outer call's throw) to {@link #end}. */
	public static Throwable begin() {
		HeldThrows current = CURRENT.get();
		current.depth++;
		Throwable outer = current.held;
		current.held = null;
		return outer;
	}

	/**
	 * Ends the call {@link #begin} began: the throw held during it (the first one; the others are suppressed in it), or
	 * null. {@code outer} is what {@link #begin} returned. Call it however the call ended, a throw included.
	 */
	public static Throwable end(Throwable outer) {
		HeldThrows current = CURRENT.get();
		if (current.depth > 0) current.depth--;
		Throwable held = current.held;
		current.held = outer;
		return held;
	}

	/** Whether one of Fabricated's calls holding throws is under way on this thread. */
	public static boolean holding() {
		return CURRENT.get().depth > 0;
	}

	/**
	 * Holds {@code thrown} for the call under way. False, holding nothing, when no call is under way on this thread, or
	 * for a {@link VirtualMachineError}: the caller rethrows it then.
	 */
	public static boolean hold(Throwable thrown) {
		if (thrown == null || thrown instanceof VirtualMachineError) return false;
		HeldThrows current = CURRENT.get();
		if (current.depth == 0) return false;
		if (current.held == null) {
			current.held = thrown;
		} else if (current.held != thrown) {
			try {
				current.held.addSuppressed(thrown);
			} catch (RuntimeException e) {
				// A throw that suppresses itself (its cause chain): the first is what counts.
			}
		}
		return true;
	}
}
