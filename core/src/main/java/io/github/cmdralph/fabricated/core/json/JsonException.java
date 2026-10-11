package io.github.cmdralph.fabricated.core.json;

/** Thrown when a JSON document is malformed or exceeds the configured limits. */
public final class JsonException extends Exception {
	private static final long serialVersionUID = 1L;

	public JsonException(String message) {
		super(message);
	}
}
