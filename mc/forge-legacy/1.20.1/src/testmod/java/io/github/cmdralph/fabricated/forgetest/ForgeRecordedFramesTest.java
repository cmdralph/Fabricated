package io.github.cmdralph.fabricated.forgetest;

/**
 * CORPUS-79: a Fabric mod's injector takes a local after a frame that chops locals, in a method shaped as Forge 61's and
 * 64's {@code ScreenEffectRenderer.renderScreenEffect}: four argument slots, three locals appended in one frame, then one
 * chopped, with the first still live. Forge 61 to 66's Mixin 0.8.7 records each of those frames as four locals and,
 * without Fabricated's repair of its recorded frames, finds no {@code StringBuilder} at the {@code String.valueOf} call,
 * so the test mod's {@code @ModifyVariable} there ({@code ForgeRecordedFramesMixin}) fails to apply and {@link Probe}
 * fails to load (the forge branch's first run of this probe). Forge 47's Mixin 0.8.5 records them expanded and counts
 * them right, with no repair. javac writes the frames: an append of {@code kept}, {@code x} and {@code y} after the first
 * {@code if}, a chop of {@code y} after the block.
 */
public final class ForgeRecordedFramesTest {
	private ForgeRecordedFramesTest() {
	}

	/** The mixin's target, a class of its own so that failing to load it fails this check alone. */
	public static final class Probe {
		private Probe() {
		}

		/** Gives "kept121+local4" for "1", "2", "3", "4" once the mixin appends "+local" to {@code kept}. */
		static String probe(Object a, Object b, Object c, Object d) {
			StringBuilder kept = new StringBuilder("kept");
			Object x = a;
			{
				Object y = b;
				if (c != null) kept.append(x);
				kept.append(y);
			}
			if (d != null) kept.append(x);
			String tail = String.valueOf(d);
			return kept.append(tail).toString();
		}
	}

	/** At the main entrypoint. */
	public static void check() {
		String result;
		try {
			result = Probe.probe("1", "2", "3", "4");
		} catch (RuntimeException | Error e) { // Mixin's own failures are errors
			ForgeTestMod.LOGGER.error("FABRICATED-TEST recorded frames FAILED: the probe's class didn't load", e);
			return;
		}
		if ("kept121+local4".equals(result)) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST recorded frames OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST recorded frames FAILED: the probe gave {}", result);
		}
	}
}
