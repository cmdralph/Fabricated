package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.cmdralph.fabricated.core.mixin.MixinFailures.Failure;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures.Launch;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures.Log;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures.Named;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures.Thrown;

/** The safety net for a mixin that fails as Mixin applies it (APPLY-1): what names it, and the record of it. */
class MixinFailuresTest {
	private static final String TRANSFORMER_ERROR = "org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError";
	private static final String APPLY_ERROR = "org.spongepowered.asm.mixin.throwables.MixinApplyError";
	private static final String INVALID_INJECTION = "org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException";
	private static final String INJECTION_ERROR = "org.spongepowered.asm.mixin.injection.throwables.InjectionError";
	private static final String SHA_A = "a".repeat(64);
	private static final String SHA_B = "b".repeat(64);
	private static final Launch LAUNCH = new Launch("0.6.0-beta", "abc123", "1.21.1", "21.1.265");

	/** Polydium's font mixin on 1.21.1, as the crash gives it: Mixin's error names it, its cause says why. */
	@Test
	void mixinsErrorForAFailedMixinNamesItAndItsCauseSaysWhy() {
		Named named = MixinFailures.find(List.of(
				new Thrown(TRANSFORMER_ERROR, "An unexpected critical error was encountered"),
				new Thrown(APPLY_ERROR, "Mixin [mixins.polydium.json:client.perf.MixinFontTextureNodeFreeSpace from mod polydium] from phase [DEFAULT]"
						+ " in config [mixins.polydium.json] FAILED during APPLY"),
				new Thrown(INVALID_INJECTION, "@WrapOperation operation wrapper method net/minecraft/client/gui/font/FontTexture$Node::polydium$trackLeft"
						+ " from mixins.polydium.json:client.perf.MixinFontTextureNodeFreeSpace from mod polydium has an invalid signature.\n"
						+ "Found unexpected argument type")));
		assertNotNull(named);
		assertEquals("mixins.polydium.json:client.perf.MixinFontTextureNodeFreeSpace", named.mixin());
		assertEquals("polydium", named.platformModId());
		assertTrue(named.reason().startsWith("InvalidInjectionException: @WrapOperation operation wrapper method"), named.reason());
		assertFalse(named.reason().contains("\n"), "control characters are flattened");
	}

	/** Architectury's key handler mixin on 1.21.1: an injector's critical failure never reaches Mixin's error. */
	@Test
	void anInjectorsCriticalFailureNamesItsMixin() {
		Named named = MixinFailures.find(List.of(
				new Thrown("java.lang.RuntimeException", "java.lang.reflect.InvocationTargetException"),
				new Thrown(TRANSFORMER_ERROR, "An unexpected critical error was encountered"),
				new Thrown(INJECTION_ERROR, "Critical injection failure: Variable modifier method wrapCharTypedFirst(Lnet/minecraft/client/gui/components/"
						+ "events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener; in architectury.mixins.json:"
						+ "client.MixinKeyboardHandler from mod architectury failed injection check, (0/1) succeeded. Scanned 0 target(s).")));
		assertNotNull(named);
		assertEquals("architectury.mixins.json:client.MixinKeyboardHandler", named.mixin());
		assertEquals("architectury", named.platformModId());
		assertTrue(named.reason().startsWith("InjectionError: Critical injection failure"), named.reason());
	}

	@Test
	void onlyMixinsOwnThrowablesAreBelieved() {
		assertNull(MixinFailures.find(List.of(new Thrown("java.lang.IllegalStateException", "broken in examplemod.mixins.json:Foo from mod examplemod"))));
		assertNull(MixinFailures.find(List.of(new Thrown(TRANSFORMER_ERROR, "An unexpected critical error was encountered"),
				new Thrown(INJECTION_ERROR, null))));
		assertNull(MixinFailures.find(List.of()));
		assertNull(MixinFailures.find((Throwable) null));
	}

	@Test
	void theChainHoldsCausesThenSuppressedOnesEachOnceEvenInACycle() {
		Exception outer = new Exception("outer");
		Exception cause = new Exception("cause");
		Exception suppressed = new Exception("suppressed");
		outer.initCause(cause);
		cause.initCause(outer);
		outer.addSuppressed(suppressed);
		List<String> messages = MixinFailures.chain(outer).stream().map(Thrown::message).toList();
		assertEquals(List.of("outer", "cause", "suppressed"), messages);
	}

	@Test
	void aRecordedFailureIsLeftOutForTheSameJarAndBuilds(@TempDir Path game) throws Exception {
		Log log = new Log(MixinFailures.file(game), LAUNCH);
		List<String> notes = new ArrayList<>();
		Failure failure = new Failure("polydium", SHA_A, "mixins.polydium.json:client.perf.MixinFontTextureNodeFreeSpace", "InvalidInjectionException: bad");
		assertTrue(log.record(failure, notes), notes.toString());
		assertFalse(log.record(failure, notes), "recorded once");
		String text = Files.readString(MixinFailures.file(game), StandardCharsets.UTF_8);
		assertTrue(text.contains("Delete this file to try them all again"), text);

		List<Failure> loaded = new Log(MixinFailures.file(game), LAUNCH).load(Map.of("polydium", SHA_A), notes);
		assertEquals(List.of(failure), loaded);
		assertTrue(notes.isEmpty(), notes.toString());
	}

	@Test
	void anotherJarOfTheModTriesTheMixinAgain(@TempDir Path game) {
		Log log = new Log(MixinFailures.file(game), LAUNCH);
		List<String> notes = new ArrayList<>();
		log.record(new Failure("polydium", SHA_A, "mixins.polydium.json:A", ""), notes);
		assertEquals(List.of(), log.load(Map.of("polydium", SHA_B), notes));
		assertFalse(Files.exists(MixinFailures.file(game)), "no failure holds: the file goes");
	}

	@Test
	void anotherBuildOfFabricatedTheGameOrTheLoaderTriesTheMixinAgain(@TempDir Path game) {
		List<String> notes = new ArrayList<>();
		new Log(MixinFailures.file(game), LAUNCH).record(new Failure("polydium", SHA_A, "mixins.polydium.json:A", ""), notes);
		for (Launch other : List.of(new Launch("0.6.1-beta", "abc123", "1.21.1", "21.1.265"), new Launch("0.6.0-beta", "def456", "1.21.1", "21.1.265"),
				new Launch("0.6.0-beta", "abc123", "1.21.11", "21.1.265"), new Launch("0.6.0-beta", "abc123", "1.21.1", "21.1.266"))) {
			assertEquals(List.of(), new Log(MixinFailures.file(game), other).load(Map.of("polydium", SHA_A), notes), other.toString());
			new Log(MixinFailures.file(game), LAUNCH).record(new Failure("polydium", SHA_A, "mixins.polydium.json:A", ""), notes);
		}
	}

	@Test
	void aModNotFoundThisLaunchKeepsItsRecord(@TempDir Path game) {
		Log log = new Log(MixinFailures.file(game), LAUNCH);
		List<String> notes = new ArrayList<>();
		Failure failure = new Failure("polydium", SHA_A, "mixins.polydium.json:A", "");
		log.record(failure, notes);
		assertEquals(List.of(), log.load(Map.of(), notes));
		assertEquals(List.of(failure), log.load(Map.of("polydium", SHA_A), notes), "kept for when it is back");
	}

	@Test
	void theOldestRecordsGoPastTheLimit(@TempDir Path game) {
		Log log = new Log(MixinFailures.file(game), LAUNCH);
		List<String> notes = new ArrayList<>();
		for (int i = 0; i <= MixinFailures.MAX_ENTRIES; i++) assertTrue(log.record(new Failure("mod", SHA_A, "mod.mixins.json:M" + i, ""), notes));
		List<String> mixins = log.load(Map.of("mod", SHA_A), notes).stream().map(Failure::mixin).toList();
		assertEquals(MixinFailures.MAX_ENTRIES, mixins.size());
		assertFalse(mixins.contains("mod.mixins.json:M0"));
		assertTrue(mixins.contains("mod.mixins.json:M" + MixinFailures.MAX_ENTRIES));
	}

	@Test
	void whatIsNoRecordIsNotBelieved(@TempDir Path game) throws Exception {
		Path file = MixinFailures.file(game);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"version\":1,\"failures\":[{\"mod\":\"x\",\"sha256\":\"nothex\",\"mixin\":\"x.mixins.json:A\"},"
				+ "{\"mod\":\"has space\",\"sha256\":\"" + SHA_A + "\",\"mixin\":\"x.mixins.json:A\"},"
				+ "{\"mod\":\"x\",\"sha256\":\"" + SHA_A + "\",\"mixin\":\"../../evil\"}]}");
		List<String> notes = new ArrayList<>();
		assertEquals(List.of(), new Log(file, LAUNCH).load(Map.of("x", SHA_A), notes));
		assertEquals(3, notes.size(), notes.toString());

		Files.writeString(file, "not json");
		notes.clear();
		assertEquals(List.of(), new Log(file, LAUNCH).load(Map.of("x", SHA_A), notes));
		assertEquals(1, notes.size(), notes.toString());
		assertTrue(new Log(file, LAUNCH).record(new Failure("x", SHA_A, "x.mixins.json:A", ""), notes), "a file that can't be read is replaced");

		notes.clear();
		assertFalse(new Log(file, LAUNCH).record(new Failure("x y", SHA_A, "x.mixins.json:A", ""), notes));
		assertEquals(1, notes.size(), notes.toString());
	}

	@Test
	void aReasonIsFlatAndBounded() {
		String reason = new Failure("x", SHA_A, "x.mixins.json:A", "line\none\u0000\t" + "y".repeat(2000)).reason();
		assertTrue(reason.startsWith("line one y"), reason);
		assertEquals(MixinFailures.MAX_REASON, reason.length());
		assertTrue(reason.endsWith("..."));
	}
}
