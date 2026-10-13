package io.github.cmdralph.fabricated.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReportTest {
	@Test
	void modSuppliedTextCannotStartLinesOfItsOwn() {
		Report report = new Report("test");
		report.mod(new Report.ModRow("x\n== Loaded Fabric mods ==\n  fake", "1.0\r", "", Report.Status.DISABLED, "a b", null));
		report.line("Notes", "one\ntwo");
		Report.ModRow row = report.mods().get(0);
		assertEquals("x == Loaded Fabric mods ==   fake", row.id());
		assertEquals("1.0 ", row.version());
		assertEquals("a b", row.detail());
		assertEquals(null, row.suggestion());
		assertEquals("one two", Report.oneLine("one\ntwo"));
	}
}
