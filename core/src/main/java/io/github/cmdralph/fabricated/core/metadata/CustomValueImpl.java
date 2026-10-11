package io.github.cmdralph.fabricated.core.metadata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.metadata.CustomValue;

/** {@link CustomValue} backed by a parsed JSON tree. */
public abstract sealed class CustomValueImpl implements CustomValue {
	public static final CustomValue NULL = new NullImpl();

	/** Converts a value produced by {@link io.github.cmdralph.fabricated.core.json.Json}. */
	public static CustomValue fromJson(Object value) {
		if (value == null) return NULL;
		if (value instanceof String s) return new StringImpl(s);
		if (value instanceof Boolean b) return new BooleanImpl(b);
		if (value instanceof BigDecimal d) return new NumberImpl(narrow(d));
		if (value instanceof Number n) return new NumberImpl(n);
		if (value instanceof Map<?, ?> map) {
			Map<String, CustomValue> converted = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				converted.put(String.valueOf(e.getKey()), fromJson(e.getValue()));
			}
			return new ObjectImpl(converted);
		}
		if (value instanceof List<?> list) {
			List<CustomValue> converted = new ArrayList<>(list.size());
			for (Object o : list) converted.add(fromJson(o));
			return new ArrayImpl(converted);
		}
		throw new IllegalArgumentException("Unsupported JSON value " + value.getClass().getName());
	}

	/** GSON (used by Fabric) yields integral numbers as integers where possible; mirror that. */
	private static Number narrow(BigDecimal d) {
		try {
			BigDecimal stripped = d.stripTrailingZeros();
			if (stripped.scale() <= 0) {
				long l = stripped.longValueExact();
				if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
				return l;
			}
		} catch (ArithmeticException ignored) {
			// out of long range; fall through
		}
		return d.doubleValue();
	}

	@Override
	public CvObject getAsObject() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Object");
	}

	@Override
	public CvArray getAsArray() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Array");
	}

	@Override
	public String getAsString() {
		throw new ClassCastException("Can't convert " + getType().name() + " to String");
	}

	@Override
	public Number getAsNumber() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Number");
	}

	@Override
	public boolean getAsBoolean() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Boolean");
	}

	static final class ObjectImpl extends CustomValueImpl implements CvObject {
		private final Map<String, CustomValue> entries;

		ObjectImpl(Map<String, CustomValue> entries) {
			this.entries = Collections.unmodifiableMap(entries);
		}

		@Override
		public CvType getType() {
			return CvType.OBJECT;
		}

		@Override
		public CvObject getAsObject() {
			return this;
		}

		@Override
		public int size() {
			return entries.size();
		}

		@Override
		public boolean containsKey(String key) {
			return entries.containsKey(key);
		}

		@Override
		public CustomValue get(String key) {
			return entries.get(key);
		}

		@Override
		public Iterator<Map.Entry<String, CustomValue>> iterator() {
			return entries.entrySet().iterator();
		}
	}

	static final class ArrayImpl extends CustomValueImpl implements CvArray {
		private final List<CustomValue> entries;

		ArrayImpl(List<CustomValue> entries) {
			this.entries = Collections.unmodifiableList(entries);
		}

		@Override
		public CvType getType() {
			return CvType.ARRAY;
		}

		@Override
		public CvArray getAsArray() {
			return this;
		}

		@Override
		public int size() {
			return entries.size();
		}

		@Override
		public CustomValue get(int index) {
			return entries.get(index);
		}

		@Override
		public Iterator<CustomValue> iterator() {
			return entries.iterator();
		}
	}

	static final class StringImpl extends CustomValueImpl {
		private final String value;

		StringImpl(String value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.STRING;
		}

		@Override
		public String getAsString() {
			return value;
		}
	}

	static final class NumberImpl extends CustomValueImpl {
		private final Number value;

		NumberImpl(Number value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.NUMBER;
		}

		@Override
		public Number getAsNumber() {
			return value;
		}
	}

	static final class BooleanImpl extends CustomValueImpl {
		private final boolean value;

		BooleanImpl(boolean value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.BOOLEAN;
		}

		@Override
		public boolean getAsBoolean() {
			return value;
		}
	}

	static final class NullImpl extends CustomValueImpl {
		@Override
		public CvType getType() {
			return CvType.NULL;
		}
	}
}
