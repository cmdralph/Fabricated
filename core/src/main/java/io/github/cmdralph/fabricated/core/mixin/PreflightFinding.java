package io.github.cmdralph.fabricated.core.mixin;

/**
 * One pre-flight result.
 *
 * @param severity    how bad it is
 * @param config      mixin config file name
 * @param mixin       mixin class (dotted), or {@code null} for config-level findings
 * @param message     what is wrong, in plain language
 * @param suggestion  what the user can do
 */
public record PreflightFinding(Severity severity, String config, String mixin, String message, String suggestion) {

	public enum Severity {
		/** The mixin would crash the game when applied; the mod must not load. */
		FAIL,
		/**
		 * The mixin would crash the game when applied, but its config's plugin may disable it at runtime: only this
		 * mixin is left out.
		 */
		LEFT_OUT,
		/** The mixin would silently not apply (part of the mod's behaviour is missing). */
		WARN,
		/** Informational (e.g. a target could not be verified). */
		INFO
	}

	@Override
	public String toString() {
		return severity + " [" + config + (mixin != null ? " " + mixin : "") + "] " + message;
	}
}
