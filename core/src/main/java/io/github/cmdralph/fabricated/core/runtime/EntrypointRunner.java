package io.github.cmdralph.fabricated.core.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runs the {@code main}, {@code client} and {@code server} entrypoints once, in mod load order, with a
 * per-entrypoint guard. A failing mod is recorded and its later entrypoints are skipped; what happens
 * next (error screen or continue) is decided by the caller from {@link #failures()}.
 */
public final class EntrypointRunner {
	/** Lets the platform glue attribute work to the right mod (e.g. NeoForge's ModLoadingContext). */
	public interface ContextHook {
		void enter(String modId);

		void exit();
	}

	/** One entrypoint that threw. */
	public record Failure(String modId, String key, String definition, Throwable error) {
		public String describe() {
			Throwable root = error;
			while (root.getCause() != null && root.getCause() != root) root = root.getCause();
			return modId + ": " + key + " entrypoint " + definition + " threw " + root.getClass().getName()
					+ (root.getMessage() != null ? ": " + root.getMessage() : "");
		}
	}

	private final FabricLoaderRuntime runtime;
	private final ContextHook hook;
	private final Set<String> failedMods = ConcurrentHashMap.newKeySet();
	private final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, Long> timings = Collections.synchronizedMap(new LinkedHashMap<>());

	public EntrypointRunner(FabricLoaderRuntime runtime, ContextHook hook) {
		this.runtime = runtime;
		this.hook = hook;
	}

	/**
	 * Runs every entrypoint declared under {@code key}.
	 *
	 * @return failures that occurred during this phase
	 */
	public <T> List<Failure> run(String key, Class<T> type, Consumer<? super T> invoker) {
		List<Failure> phase = new ArrayList<>();
		for (FabricLoaderRuntime.Declared d : runtime.declared(key)) {
			String modId = d.mod().getMetadata().getId();
			if (failedMods.contains(modId)) continue;
			long start = System.nanoTime();
			hook.enter(modId);
			try {
				T instance = runtime.instantiate(d, type);
				invoker.accept(instance);
			} catch (Throwable t) {
				if (t instanceof VirtualMachineError vme && !(t instanceof StackOverflowError)) throw vme;
				Failure f = new Failure(modId, key, d.spec().value(), t);
				phase.add(f);
				failures.add(f);
				failedMods.add(modId);
			} finally {
				hook.exit();
				timings.merge(modId + " (" + key + ")", (System.nanoTime() - start) / 1_000_000, Long::sum);
			}
		}
		return phase;
	}

	public List<Failure> failures() {
		synchronized (failures) {
			return List.copyOf(failures);
		}
	}

	public boolean hasFailed(String modId) {
		return failedMods.contains(modId);
	}

	/** Milliseconds spent per mod and entrypoint key. */
	public Map<String, Long> timings() {
		synchronized (timings) {
			return Map.copyOf(timings);
		}
	}
}
