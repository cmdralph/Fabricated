package io.github.cmdralph.fabricated.testmod;

/**
 * CORPUS-79, FRAME-1: a Fabric mod's variable modifier takes a local after a frame that chops locals, in a method shaped as
 * Forge 61's and 64's {@code ScreenEffectRenderer.renderScreenEffect}: four argument slots, three locals appended in one
 * frame, then one chopped, with the first still live. javac writes the frames: an append of {@code kept}, {@code x} and
 * {@code y} after the first {@code if}, a chop of {@code y} after the block. Mixin records each of those frames' sizes as
 * at least the argument slots where it records the class's frames compressed, counts four locals after the chop where six
 * are live, and finds no {@code StringBuilder} at the {@code String.valueOf} call, so the test mod's
 * {@code @ModifyVariable} there ({@code RecordedFramesMixin}, not required) doesn't apply.
 *
 * <p>Where each platform's Mixin finds it is ci/checks.tsv's: on Forge 61 to 66 once Fabricated records the method's
 * frames again expanded, and on Forge 47, whose Mixin 0.8.5 records them expanded ("recorded frames OK"); on NeoForge
 * nowhere, as on Fabric, whose Mixin NeoForge runs and hands the class to as Fabric Loader does ("recorded frames as on
 * Fabric OK"): a Fabric mod's injector taking that local fails there on Fabric too.
 */
public final class RecordedFramesTest {
	private RecordedFramesTest() {
	}

	/** The mixin's target, a class of its own so that failing to load it fails this check alone. */
	public static final class Probe {
		private Probe() {
		}

		/** Gives "kept121+local4" for "1", "2", "3", "4" once the mixin appends "+local" to {@code kept}, else "kept1214". */
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
			TestMod.LOGGER.error("FABRICATED-TEST recorded frames FAILED: the probe's class didn't load", e);
			return;
		}
		if ("kept121+local4".equals(result)) {
			TestMod.LOGGER.info("FABRICATED-TEST recorded frames OK");
		} else if ("kept1214".equals(result)) {
			TestMod.LOGGER.info("FABRICATED-TEST recorded frames as on Fabric OK: Mixin found no StringBuilder after the chop");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST recorded frames FAILED: the probe gave {}", result);
		}
	}
}
