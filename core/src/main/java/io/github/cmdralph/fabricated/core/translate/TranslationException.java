package io.github.cmdralph.fabricated.core.translate;

import java.util.List;

import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;

/** A mod that cannot be translated, with a user-facing reason and suggestion. */
public final class TranslationException extends Exception {
	private static final long serialVersionUID = 1L;
	private final String suggestion;
	private final transient List<PreflightFinding> findings;

	public TranslationException(String message, String suggestion) {
		this(message, suggestion, List.of(), null);
	}

	public TranslationException(String message, String suggestion, List<PreflightFinding> findings, Throwable cause) {
		super(message, cause);
		this.suggestion = suggestion;
		this.findings = List.copyOf(findings);
	}

	public String suggestion() {
		return suggestion;
	}

	public List<PreflightFinding> findings() {
		return findings;
	}
}
