package io.github.cmdralph.fabricated.core.remap;

/** Thrown when mapping files are missing or malformed. */
public final class MappingException extends Exception {
	private static final long serialVersionUID = 1L;

	public MappingException(String message) {
		super(message);
	}

	public MappingException(String message, Throwable cause) {
		super(message, cause);
	}
}
