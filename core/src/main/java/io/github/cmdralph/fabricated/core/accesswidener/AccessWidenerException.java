package io.github.cmdralph.fabricated.core.accesswidener;

/** A malformed access widener or class tweaker. */
public final class AccessWidenerException extends Exception {
	private static final long serialVersionUID = 1L;
	private final int line;

	public AccessWidenerException(int line, String message) {
		super(line > 0 ? "line " + line + ": " + message : message);
		this.line = line;
	}

	public int line() {
		return line;
	}
}
