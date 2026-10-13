package io.github.cmdralph.fabricated.core.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.cmdralph.fabricated.core.archive.AtomicFiles;

/**
 * Human-readable diagnostic report written to {@code logs/fabric-bridge-report.txt}.
 *
 * <p>Thread-safe: discovery fills most sections, and runtime events (entrypoint failures, commands)
 * are appended later.
 */
public final class Report {
	public static final String FILE_NAME = "fabric-bridge-report.txt";

	/** Outcome of one Fabric jar. */
	public enum Status {
		LOADED, DISABLED, REJECTED, PROVIDED_NATIVELY, SKIPPED
	}

	/** One row in the mod table. Line breaks and other control characters (mod-supplied text) become spaces. */
	public record ModRow(String id, String version, String source, Status status, String detail, String suggestion) {
		public ModRow {
			id = oneLine(id);
			version = oneLine(version);
			source = oneLine(source);
			detail = oneLine(detail);
			suggestion = oneLine(suggestion);
		}
	}

	private final String fabricatedVersion;
	private final Map<String, String> environment = new LinkedHashMap<>();
	private final List<ModRow> mods = new ArrayList<>();
	private final Map<String, List<String>> sections = new LinkedHashMap<>();

	public Report(String fabricatedVersion) {
		this.fabricatedVersion = fabricatedVersion;
	}

	public synchronized Report environment(String key, String value) {
		environment.put(key, value);
		return this;
	}

	public synchronized Report mod(ModRow row) {
		mods.add(row);
		return this;
	}

	/** Adds a line to a named section (created on first use, in insertion order). */
	public synchronized Report line(String section, String line) {
		sections.computeIfAbsent(section, k -> new ArrayList<>()).add(oneLine(line));
		return this;
	}

	/** Keeps mod-supplied text from starting lines of its own (spoofed sections) in the report or NeoForge's warnings. */
	static String oneLine(String text) {
		if (text == null) return null;
		StringBuilder sb = null;
		for (int i = 0; i < text.length(); i++) {
			if (Character.isISOControl(text.charAt(i)) || text.charAt(i) == '\u2028' || text.charAt(i) == '\u2029') {
				if (sb == null) sb = new StringBuilder(text);
				sb.setCharAt(i, ' ');
			}
		}
		return sb == null ? text : sb.toString();
	}

	public synchronized List<ModRow> mods() {
		return List.copyOf(mods);
	}

	public synchronized long count(Status status) {
		return mods.stream().filter(m -> m.status() == status).count();
	}

	public synchronized String render() {
		StringBuilder sb = new StringBuilder();
		sb.append("Fabricated ").append(fabricatedVersion).append(" - Fabric compatibility report\n");
		sb.append("Generated ").append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\n");
		sb.append("Fabricated never downloads anything and sends no telemetry. Share this file when asking for help.\n\n");

		header(sb, "Environment");
		environment.forEach((k, v) -> sb.append("  ").append(pad(k, 22)).append(v).append('\n'));
		sb.append('\n');

		header(sb, "Summary");
		sb.append("  Loaded:              ").append(count(Status.LOADED)).append('\n');
		sb.append("  Provided natively:   ").append(count(Status.PROVIDED_NATIVELY)).append('\n');
		sb.append("  Disabled:            ").append(count(Status.DISABLED)).append('\n');
		sb.append("  Rejected jars:       ").append(count(Status.REJECTED)).append('\n');
		sb.append("  Skipped:             ").append(count(Status.SKIPPED)).append('\n');
		sb.append('\n');

		for (Status status : Status.values()) {
			List<ModRow> rows = mods.stream().filter(m -> m.status() == status).toList();
			if (rows.isEmpty()) continue;
			header(sb, title(status));
			for (ModRow row : rows) {
				sb.append("  ").append(row.id());
				if (row.version() != null && !row.version().isEmpty()) sb.append(' ').append(row.version());
				if (row.source() != null && !row.source().isEmpty()) sb.append("  [").append(row.source()).append(']');
				sb.append('\n');
				if (row.detail() != null && !row.detail().isEmpty()) sb.append("      ").append(row.detail()).append('\n');
				if (row.suggestion() != null && !row.suggestion().isEmpty()) sb.append("      Fix: ").append(row.suggestion()).append('\n');
			}
			sb.append('\n');
		}

		for (Map.Entry<String, List<String>> section : sections.entrySet()) {
			header(sb, section.getKey());
			for (String line : section.getValue()) sb.append("  ").append(line).append('\n');
			sb.append('\n');
		}
		return sb.toString();
	}

	/** Writes the report atomically into {@code logsDir}. */
	public void write(Path logsDir) throws IOException {
		Files.createDirectories(logsDir);
		AtomicFiles.writeAtomically(logsDir.resolve(FILE_NAME), render().getBytes(StandardCharsets.UTF_8), logsDir);
	}

	private static String title(Status status) {
		return switch (status) {
			case LOADED -> "Loaded Fabric mods";
			case PROVIDED_NATIVELY -> "Fabric API modules provided by Fabricated";
			case DISABLED -> "Disabled Fabric mods (the game continues without them)";
			case REJECTED -> "Rejected jars";
			case SKIPPED -> "Skipped";
		};
	}

	private static void header(StringBuilder sb, String title) {
		sb.append("== ").append(title).append(" ==\n");
	}

	private static String pad(String s, int width) {
		StringBuilder sb = new StringBuilder(s).append(':');
		while (sb.length() < width) sb.append(' ');
		return sb.toString();
	}
}
