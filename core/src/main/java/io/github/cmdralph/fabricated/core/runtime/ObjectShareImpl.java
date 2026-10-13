package io.github.cmdralph.fabricated.core.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import net.fabricmc.loader.api.ObjectShare;

/** Thread-safe {@link ObjectShare}; keys must be namespaced as {@code modid:name}. */
public final class ObjectShareImpl implements ObjectShare {
	private final Map<String, Object> values = new HashMap<>();
	private final Map<String, List<BiConsumer<String, Object>>> pending = new HashMap<>();

	@Override
	public synchronized Object get(String key) {
		validate(key);
		return values.get(key);
	}

	@Override
	public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
		validate(key);
		Object value;
		synchronized (this) {
			value = values.get(key);
			if (value == null) {
				pending.computeIfAbsent(key, k -> new ArrayList<>()).add(consumer);
				return;
			}
		}
		consumer.accept(key, value);
	}

	@Override
	public Object put(String key, Object value) {
		validate(key);
		Objects.requireNonNull(value, "value");
		Object previous;
		List<BiConsumer<String, Object>> waiting;
		synchronized (this) {
			previous = values.put(key, value);
			waiting = pending.remove(key);
		}
		notifyWaiting(waiting, key, value);
		return previous;
	}

	@Override
	public Object putIfAbsent(String key, Object value) {
		validate(key);
		Objects.requireNonNull(value, "value");
		List<BiConsumer<String, Object>> waiting;
		synchronized (this) {
			// Check and insert under one lock, so two callers can't both see the key absent.
			Object existing = values.putIfAbsent(key, value);
			if (existing != null) return existing;
			waiting = pending.remove(key);
		}
		notifyWaiting(waiting, key, value);
		return null;
	}

	@Override
	public synchronized Object remove(String key) {
		validate(key);
		return values.remove(key);
	}

	/** Runs the consumers that were waiting for {@code key}, outside the lock; {@code waiting} may be null. */
	private static void notifyWaiting(List<BiConsumer<String, Object>> waiting, String key, Object value) {
		if (waiting != null) {
			for (BiConsumer<String, Object> c : waiting) c.accept(key, value);
		}
	}

	private static void validate(String key) {
		Objects.requireNonNull(key, "key");
		if (key.indexOf(':') <= 0) throw new IllegalArgumentException("Invalid key '" + key + "': must be of the form modid:name");
	}
}
