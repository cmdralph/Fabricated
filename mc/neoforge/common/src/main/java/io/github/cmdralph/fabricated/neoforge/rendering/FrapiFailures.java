package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.neoforged.fml.ModList;

/**
 * FRAPI rule 8: a Fabric block model that throws where Fabricated calls into it (or the renderer drawing it) fails
 * alone. Its class is then drawn by NeoForge's renderer, from the parts it gives NeoForge, for the rest of the session;
 * the failure is logged once and listed in the report, naming the mod the class belongs to.
 *
 * <p>Each version's {@code FabricBlockRendering} keeps a {@link ModelClass} per model class (in a {@code ClassValue}),
 * so the healthy path reads a final field and, for a Fabric model, a volatile one. A composite model (multipart,
 * weighted) keeps its answer for one {@link #generation()}: marking a class bumps it, so composites holding that class
 * ask their parts again, once.
 */
public final class FrapiFailures {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Object LOCK = new Object();
	/** Model classes {@link #unattributed} logged (under {@link #LOCK}). */
	private static final Set<String> UNATTRIBUTED = new HashSet<>();
	/** Bumped (under {@link #LOCK}) after each class is marked broken. */
	private static volatile int generation;

	private FrapiFailures() {
	}

	/**
	 * What Fabricated knows about a block model class: whether Fabric's renderer draws it (its own emitter method), the
	 * class declaring that method (the frame a throw from it shows), and whether drawing it threw.
	 */
	public static final class ModelClass {
		/** A class Fabric's renderer doesn't draw. */
		public static final ModelClass NOT_FABRIC = new ModelClass(false, null);

		private final boolean fabric;
		private final @Nullable String emitter;
		private volatile boolean broken;

		public ModelClass(boolean fabric, @Nullable String emitter) {
			this.fabric = fabric;
			this.emitter = emitter;
		}

		/** Whether Fabric's renderer draws this class at all (broken or not). */
		public boolean fabric() {
			return fabric;
		}

		/** Whether Fabric's renderer draws this class now: a Fabric model whose drawing never threw. */
		public boolean drawn() {
			return fabric && !broken;
		}

		public boolean broken() {
			return broken;
		}
	}

	/** A model Fabric's renderer doesn't draw, nor any of its parts. */
	public static final int NONE = 0;
	/** A model Fabric's renderer draws: a Fabric model, or a composite with one among its parts and none broken. */
	public static final int FABRIC = 1;
	/** A Fabric model whose drawing threw, or a composite with one among its parts: NeoForge's renderer draws it. */
	public static final int BROKEN = 2;

	/** Changes each time a class is marked broken; composite models keep their answer for one value. */
	public static int generation() {
		return generation;
	}

	/**
	 * A composite model's state ({@link #NONE}, {@link #FABRIC}, {@link #BROKEN}) from what it keeps ({@code cache}, 0
	 * at first) when that is of the current generation; otherwise -1, and the composite computes its state and keeps
	 * {@link #cache}'s value for it. One int, so threads racing here see a whole answer.
	 */
	public static int cachedState(int cache, int generation) {
		return cache >>> 2 == generation + 1 ? cache & 3 : -1;
	}

	/** What a composite keeps for a state computed during {@code generation} (read before computing it). */
	public static int cache(int state, int generation) {
		return (generation + 1) << 2 | state;
	}

	/** The state of a composite from its parts' states: broken if one is, else Fabric's if one is. */
	public static int combine(int a, int b) {
		return Math.max(a, b);
	}

	/**
	 * Whether the throwable (or a cause) was thrown from inside this class's emitter method: one of its frames is that
	 * method, declared by the class that declares the model's.
	 */
	public static boolean threwIn(Throwable error, ModelClass model, String method) {
		if (model.emitter == null) return false;
		Set<Throwable> seen = new HashSet<>();
		for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
			for (StackTraceElement frame : t.getStackTrace()) {
				if (frame.getMethodName().equals(method) && frame.getClassName().equals(model.emitter)) return true;
			}
		}
		return false;
	}

	/**
	 * Marks a model class broken: NeoForge's renderer draws it from now on. The first time, logs the failure with its
	 * stack trace and adds a report line naming the class's mod. Called on the failure path only.
	 *
	 * @param where where it threw ("a chunk section", "a moving block", ...)
	 * @param block the block being drawn, for the log
	 */
	public static void markBroken(Class<?> type, ModelClass model, Throwable error, String where, Object block) {
		if (!model.fabric) return;
		synchronized (LOCK) {
			if (model.broken) return;
			model.broken = true;
			// After the flag: a composite seeing the new generation sees the flag too.
			generation++;
		}
		String text = "Fabric block models of class " + type.getName() + " (" + modOf(type) + ") threw while drawn in " + where + " (" + block
				+ "): " + error + ". NeoForge's renderer draws them from now on, from the parts they give it, which may look different"
				+ " or be empty; update the mod, or report it to its author.";
		LOGGER.error("{}", text, error);
		FabricatedState state = FabricatedState.get();
		if (state != null) {
			state.report().line("Rendering", text);
			state.writeReport();
		}
	}

	/**
	 * Logs, once per model class, a throw no Fabric model could be named for (a composite whose parts no longer show the
	 * one that threw): that block keeps Fabric's renderer and NeoForge's draws it each time it throws.
	 */
	public static void unattributed(Class<?> type, Throwable error, String where, Object block) {
		synchronized (LOCK) {
			if (!UNATTRIBUTED.add(type.getName())) return;
		}
		LOGGER.error("Drawing a block model of class {} with Fabric's renderer threw in {} ({}), and no Fabric model in it could be named:"
				+ " NeoForge's renderer draws it each time it throws", type.getName(), where, block, error);
	}

	/** The mod a class belongs to, as its module names it: the mod's name and id, or the module's name. */
	static String modOf(Class<?> type) {
		String module = type.getModule().getName();
		if (module == null) return "a class outside any mod";
		try {
			return ModList.get().getModContainerById(module)
					.map(container -> "mod " + container.getModInfo().getDisplayName() + ", " + module)
					.orElse("module " + module);
		} catch (RuntimeException | LinkageError e) {
			return "module " + module;
		}
	}
}
