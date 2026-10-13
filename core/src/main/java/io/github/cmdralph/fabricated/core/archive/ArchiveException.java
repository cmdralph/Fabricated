package io.github.cmdralph.fabricated.core.archive;

import java.io.IOException;

/** Thrown when an archive is malformed, unsafe, or exceeds a configured limit. */
public final class ArchiveException extends IOException {
	private static final long serialVersionUID = 1L;

	public ArchiveException(String message) {
		super(message);
	}

	public ArchiveException(String message, Throwable cause) {
		super(message, cause);
	}
}
