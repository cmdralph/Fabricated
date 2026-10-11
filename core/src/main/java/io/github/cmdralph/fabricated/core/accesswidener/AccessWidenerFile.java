package io.github.cmdralph.fabricated.core.accesswidener;

import java.util.List;

/**
 * Parsed access widener ({@code accessWidener v1|v2}) or class tweaker ({@code classTweaker v1}).
 *
 * @param format             {@code accessWidener} or {@code classTweaker}
 * @param version            {@code v1} or {@code v2}
 * @param namespace          header namespace ({@code intermediary}, {@code named} or {@code official})
 * @param entries            access changes in file order
 * @param injectedInterfaces interface injections (class tweaker only)
 */
public record AccessWidenerFile(String format, String version, String namespace, List<Entry> entries,
		List<InjectedInterface> injectedInterfaces) {

	/** What the directive changes. */
	public enum Access {
		ACCESSIBLE,
		EXTENDABLE,
		MUTABLE
	}

	/** What the directive targets. */
	public enum Kind {
		CLASS,
		METHOD,
		FIELD
	}

	/**
	 * One access directive. For classes {@code name} and {@code descriptor} are {@code null}.
	 *
	 * @param line source line number, for messages
	 */
	public record Entry(Access access, Kind kind, String owner, String name, String descriptor, boolean transitive, int line) {
	}

	/**
	 * @param target          class receiving the interface (internal name)
	 * @param interfaceName   raw interface internal name (generic arguments removed)
	 * @param genericSignature the full declaration including generics, as written
	 */
	public record InjectedInterface(String target, String interfaceName, String genericSignature, boolean transitive, int line) {
	}
}
