package io.github.cmdralph.fabricated.core.archive;

/** Thrown when an archive is malformed, unsafe, or exceeds a configured limit. */
public final class ArchiveException extends Exception {
	private static final long serialVersionUID = 1L;

	public ArchiveException(String message) {
		super(message);
	}

	public ArchiveException(String message, Throwable cause) {
		super(message, cause);
	}
}
