package io.github.cmdralph.fabricated.core.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which of the calls to one method on a throw's stack the throw is put down to, when the objects implementing that
 * method call into each other: FRAPI rule 8's question when a Fabric block model wraps another (as Continuity's
 * connected texture models wrap the models they are given), so that a wrapped model that throws fails alone and its
 * wrapper keeps working.
 *
 * <p>The throw is put down to the call of the method nearest to where it was thrown (the frame nearest the top of the
 * stack whose method has that name), in the first throwable of the cause chain, counted from the deepest cause, whose
 * stack holds the asked-about class's call: a wrapper that catches the wrapped model's throw and throws its own still
 * sees it put down to the wrapped model. Code a call runs that is no call of the method (a helper, a library) counts as
 * that call's. Frames name classes, not objects, so a class calling into another instance of itself is its own call.
 * Failure path only: nothing here runs while models draw.
 */
public final class ThrowSite {
	private ThrowSite() {
	}

	/** Where, on a throw's stack, a class's call of the method is. */
	public enum Kind {
		/** The class's call is the call of the method nearest the throw: its own code threw. */
		OWN,
		/** The class's call is on the stack, but a call it made of the method is nearer the throw: that one threw. */
		CALLED,
		/** The class's call of the method isn't on the stack. */
		ABSENT
	}

	/**
	 * What a throw is put down to, for one class.
	 *
	 * @param kind    where the class's call is
	 * @param nearest for {@link Kind#CALLED}, the frame of the call nearest the throw (the class that threw); otherwise
	 *                null
	 */
	public record Blame(Kind kind, StackTraceElement nearest) {
		static final Blame OWN = new Blame(Kind.OWN, null);
		static final Blame ABSENT = new Blame(Kind.ABSENT, null);

		public Blame {
			Objects.requireNonNull(kind, "kind");
			if ((kind == Kind.CALLED) != (nearest != null)) throw new IllegalArgumentException("a frame for CALLED, and only for it");
		}
	}

	/**
	 * Puts a throw down to the calls of {@code method} on its stack, for the calls {@code declaringClass} makes (the class
	 * declaring the method a model runs: its own, or one it inherits from).
	 *
	 * @param declaringClass the declaring class's binary name, as frames give it; null (not known) is {@link Kind#ABSENT}
	 */
	public static Blame blame(Throwable error, String method, String declaringClass) {
		Objects.requireNonNull(error, "error");
		Objects.requireNonNull(method, "method");
		if (declaringClass == null) return Blame.ABSENT;
		List<Throwable> chain = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) chain.add(t);
		for (int i = chain.size() - 1; i >= 0; i--) {
			StackTraceElement nearest = null;
			for (StackTraceElement frame : chain.get(i).getStackTrace()) {
				if (!method.equals(frame.getMethodName())) continue;
				if (declaringClass.equals(frame.getClassName())) return nearest == null ? Blame.OWN : new Blame(Kind.CALLED, nearest);
				if (nearest == null) nearest = frame;
			}
		}
		return Blame.ABSENT;
	}
}
