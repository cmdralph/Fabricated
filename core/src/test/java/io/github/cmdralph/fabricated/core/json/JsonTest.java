package io.github.cmdralph.fabricated.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {
	@Test
	void parsesAllValueTypes() throws Exception {
		Object v = Json.parse("{\"a\":1,\"b\":[true,false,null],\"c\":\"x\\ny\\u0041\",\"d\":-2.5e1,\"e\":{}}");
		Map<?, ?> m = (Map<?, ?>) v;
		assertEquals(new BigDecimal("1"), m.get("a"));
		assertEquals(java.util.Arrays.asList(true, false, null), m.get("b"));
		assertEquals("x\nyA", m.get("c"));
		assertEquals(0, new BigDecimal("-25").compareTo((BigDecimal) m.get("d")));
		assertEquals(Map.of(), m.get("e"));
	}

	@Test
	void toleratesCommentsTrailingCommasAndBom() throws Exception {
		Object v = Json.parse("﻿{ // comment\n \"a\": [1, 2,], /* block */ \"b\": 3, }");
		assertEquals(List.of(new BigDecimal(1), new BigDecimal(2)), ((Map<?, ?>) v).get("a"));
	}

	@Test
	void rejectsMalformedAndOversizedInput() {
		assertThrows(JsonException.class, () -> Json.parse("{\"a\":}"));
		assertThrows(JsonException.class, () -> Json.parse("[1,2"));
		assertThrows(JsonException.class, () -> Json.parse("{} extra"));
		assertThrows(JsonException.class, () -> Json.parse("\"unterminated"));
		assertThrows(JsonException.class, () -> Json.parse("[".repeat(100) + "]".repeat(100)), "depth limit");
		assertThrows(JsonException.class, () -> Json.parse("\"" + "x".repeat(100) + "\"", 50, 10), "length limit");
	}

	@Test
	void writeIsDeterministicAndRoundTrips() throws Exception {
		String json = "{\"z\":1,\"a\":[\"q\\\"uote\",{\"k\":false}],\"n\":null}";
		Object parsed = Json.parse(json);
		String written = Json.write(parsed, false);
		assertEquals(json, written);
		assertEquals(written, Json.write(Json.parse(Json.write(parsed, true)), false));
	}
}
