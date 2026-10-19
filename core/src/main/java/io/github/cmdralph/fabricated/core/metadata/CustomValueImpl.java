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
public sealed interface CustomValueImpl extends CustomValue {
	CustomValue NULL = NullImpl.INSTANCE;

	/** Converts a value produced by {@link io.github.cmdralph.fabricated.core.json.Json}, or a tree of the same plain types. */
	static CustomValue fromJson(Object value) {
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
	default CvObject getAsObject() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Object");
	}

	@Override
	default CvArray getAsArray() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Array");
	}

	@Override
	default String getAsString() {
		throw new ClassCastException("Can't convert " + getType().name() + " to String");
	}

	@Override
	default Number getAsNumber() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Number");
	}

	@Override
	default boolean getAsBoolean() {
		throw new ClassCastException("Can't convert " + getType().name() + " to Boolean");
	}

	record ObjectImpl(Map<String, CustomValue> entries) implements CustomValueImpl, CvObject {
		public ObjectImpl {
			entries = Collections.unmodifiableMap(entries);
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

	record ArrayImpl(List<CustomValue> entries) implements CustomValueImpl, CvArray {
		public ArrayImpl {
			entries = Collections.unmodifiableList(entries);
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

	record StringImpl(String value) implements CustomValueImpl {
		@Override
		public CvType getType() {
			return CvType.STRING;
		}

		@Override
		public String getAsString() {
			return value;
		}
	}

	record NumberImpl(Number value) implements CustomValueImpl {
		@Override
		public CvType getType() {
			return CvType.NUMBER;
		}

		@Override
		public Number getAsNumber() {
			return value;
		}
	}

	record BooleanImpl(boolean value) implements CustomValueImpl {
		@Override
		public CvType getType() {
			return CvType.BOOLEAN;
		}

		@Override
		public boolean getAsBoolean() {
			return value;
		}
	}

	enum NullImpl implements CustomValueImpl {
		INSTANCE;

		@Override
		public CvType getType() {
			return CvType.NULL;
		}
	}
}
