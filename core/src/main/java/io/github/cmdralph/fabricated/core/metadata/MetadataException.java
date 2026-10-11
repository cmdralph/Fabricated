package io.github.cmdralph.fabricated.core.metadata;

/** Thrown when {@code fabric.mod.json} is missing, malformed or uses an unsupported schema. */
public final class MetadataException extends Exception {
	private static final long serialVersionUID = 1L;

	public MetadataException(String message) {
		super(message);
	}

	public MetadataException(String message, Throwable cause) {
		super(message, cause);
	}
}
