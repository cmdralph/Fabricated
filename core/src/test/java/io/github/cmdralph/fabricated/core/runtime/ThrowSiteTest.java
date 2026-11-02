package io.github.cmdralph.fabricated.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ThrowSiteTest {
	private static final String EMIT = "emitBlockQuads";
	private static final String WRAPPER = "me.pepperbell.continuity.client.model.CtmBakedModel";
	private static final String BROKEN = "com.example.BrokenModel";
	private static final String FORWARDING = "net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel";
	private static final String INDIGO = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext";

	private static StackTraceElement frame(String type, String method) {
		return new StackTraceElement(type, method, null, -1);
	}

	/** A throwable whose stack is the frames given, the first nearest the throw. */
	private static Throwable thrown(Throwable cause, StackTraceElement... frames) {
		Throwable error = new RuntimeException("test", cause);
		error.setStackTrace(frames);
		return error;
	}

	@Test
	void ownCodeThrew() {
		Throwable error = thrown(null, frame(BROKEN, EMIT), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(error, EMIT, BROKEN).kind());
	}

	@Test
	void helperItCalledCountsAsItsOwn() {
		Throwable error = thrown(null, frame("com.example.Helper", "compute"), frame(BROKEN, EMIT), frame(INDIGO, "tessellateBlock"));
		ThrowSite.Blame blame = ThrowSite.blame(error, EMIT, BROKEN);
		assertEquals(ThrowSite.Kind.OWN, blame.kind());
		assertNull(blame.nearest());
	}

	@Test
	void wrappedModelThrewNotTheWrapper() {
		// Continuity's model wraps a multipart model (upstream's emitter in vanilla's class) holding a broken part.
		Throwable error = thrown(null, frame(BROKEN, EMIT), frame("net.minecraft.client.resources.model.MultiPartBakedModel", EMIT), frame(WRAPPER, EMIT),
				frame(INDIGO, "tessellateBlock"));
		ThrowSite.Blame wrapper = ThrowSite.blame(error, EMIT, WRAPPER);
		assertEquals(ThrowSite.Kind.CALLED, wrapper.kind());
		assertEquals(BROKEN, wrapper.nearest().getClassName());
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(error, EMIT, BROKEN).kind());
	}

	@Test
	void inheritedEmitterIsTheDeclaringClasssCall() {
		// A wrapper that doesn't declare the method runs Fabric API's forwarding one, which calls the wrapped model.
		Throwable error = thrown(null, frame(BROKEN, EMIT), frame(FORWARDING, EMIT), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.CALLED, ThrowSite.blame(error, EMIT, FORWARDING).kind());
		Throwable own = thrown(null, frame(FORWARDING, EMIT), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(own, EMIT, FORWARDING).kind());
	}

	@Test
	void classCallingItselfIsItsOwnCall() {
		Throwable error = thrown(null, frame(WRAPPER, EMIT), frame(WRAPPER, EMIT), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(error, EMIT, WRAPPER).kind());
	}

	@Test
	void absentWhenItsCallIsNotOnTheStack() {
		// The renderer threw after the model's emitter returned.
		Throwable error = thrown(null, frame(INDIGO, "bufferQuad"), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.ABSENT, ThrowSite.blame(error, EMIT, WRAPPER).kind());
		assertEquals(ThrowSite.Kind.ABSENT, ThrowSite.blame(error, EMIT, null).kind(), "a declaring class not known");
	}

	@Test
	void deepestCauseHoldingTheCallDecides() {
		Throwable original = thrown(null, frame(BROKEN, EMIT), frame(WRAPPER, EMIT), frame(INDIGO, "tessellateBlock"));
		// The renderer's report, made where it caught the throw: no emitter on its stack.
		Throwable reported = thrown(original, frame(INDIGO, "tessellateBlock"), frame("net.minecraft.client.renderer.chunk.SectionCompiler", "compile"));
		assertEquals(ThrowSite.Kind.CALLED, ThrowSite.blame(reported, EMIT, WRAPPER).kind());
		// A wrapper catching the wrapped model's throw and throwing its own: still the wrapped model's.
		Throwable rethrown = thrown(original, frame(WRAPPER, EMIT), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.CALLED, ThrowSite.blame(rethrown, EMIT, WRAPPER).kind());
		// A cause from elsewhere, with no call of the method: the throwable above it decides.
		Throwable unrelated = thrown(null, frame("java.io.FileInputStream", "open"));
		Throwable own = thrown(unrelated, frame(WRAPPER, EMIT), frame(INDIGO, "tessellateBlock"));
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(own, EMIT, WRAPPER).kind());
	}

	@Test
	void causeCycleEnds() {
		RuntimeException a = new RuntimeException("a");
		RuntimeException b = new RuntimeException("b", a);
		a.initCause(b);
		a.setStackTrace(new StackTraceElement[] {frame(WRAPPER, EMIT)});
		b.setStackTrace(new StackTraceElement[] {frame(INDIGO, "tessellateBlock")});
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(a, EMIT, WRAPPER).kind());
		assertEquals(ThrowSite.Kind.OWN, ThrowSite.blame(b, EMIT, WRAPPER).kind());
	}

	@Test
	void blameHoldsAFrameOnlyForCalled() {
		assertThrows(IllegalArgumentException.class, () -> new ThrowSite.Blame(ThrowSite.Kind.CALLED, null));
		assertThrows(IllegalArgumentException.class, () -> new ThrowSite.Blame(ThrowSite.Kind.OWN, frame(BROKEN, EMIT)));
	}
}
