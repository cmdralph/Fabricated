package io.github.cmdralph.fabricated.forge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.ThrowSite;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;

/**
 * FRAPI rule 8 on Forge: a Fabric block model that throws while Fabric's renderer draws it fails alone. Upstream's own
 * hooks send blocks through Fabric's renderer on Forge, so Fabricated's guards sit at the renderer's entry points
 * (each version's {@code FabricBlockRendering}); a model class that threw there is drawn from the parts it gives, for
 * the rest of the session. The failure is logged once and listed in the report, naming the mod the class belongs to. A
 * throw is put down to the model whose code threw, not to one wrapping it ({@link #putDown}): a mod whose models wrap
 * others (Continuity's) keeps its own drawing when a model it wraps is broken. As NeoForge's, in Forge's terms; shared
 * by Forge 47 and Forge 61 to 66.
 *
 * <p>Each version keeps a {@link ModelClass} per model class (in a {@code ClassValue}), so the healthy path reads a final
 * field and, for a Fabric model, a volatile one. A composite model (multipart, weighted) keeps its answer for one
 * {@link #generation()}: marking a class bumps it, so composites holding that class ask their parts again, once.
 */
public final class FrapiFailures {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Object LOCK = new Object();
	/** Model classes {@link #unattributed} logged (under {@link #LOCK}). */
	private static final Set<String> UNATTRIBUTED = new HashSet<>();
	/** The classes that threw inside a wrapper, with the wrapper, {@link #wrappedThrew} logged (under {@link #LOCK}). */
	private static final Set<String> WRAPPED = new HashSet<>();
	/** Model classes whose parts threw, {@link #partsThrew} logged (under {@link #LOCK}). */
	private static final Set<String> PARTS_THREW = new HashSet<>();
	/** {@code ModList.getModContainerById} as {@code (String)Optional}; null if Forge's has none (then modules are named). */
	private static final @Nullable MethodHandle CONTAINER_BY_ID = containerById();
	/** Bumped (under {@link #LOCK}) after each class is marked broken. */
	private static volatile int generation;

	private FrapiFailures() {
	}

	/**
	 * What Fabricated knows about a block model class: whether Fabric's renderer draws it with the model's own emitter
	 * method, the class declaring that method (the frame a throw from it shows), and whether drawing it threw.
	 */
	public static final class ModelClass {
		/** A class whose quads Fabric's renderer takes from its parts, as Forge's renderer would. */
		public static final ModelClass NOT_FABRIC = new ModelClass(false, null);

		private final boolean fabric;
		private final @Nullable String emitter;
		private volatile boolean broken;

		public ModelClass(boolean fabric, @Nullable String emitter) {
			this.fabric = fabric;
			this.emitter = emitter;
		}

		/** Whether the class has its own emitter method (broken or not). */
		public boolean fabric() {
			return fabric;
		}

		/** Whether Fabric's renderer calls the class's own emitter method now: a Fabric model whose drawing never threw. */
		public boolean drawn() {
			return fabric && !broken;
		}

		public boolean broken() {
			return broken;
		}
	}

	/** A model with no emitter method of its own, nor any of its parts. */
	public static final int NONE = 0;
	/** A Fabric model, or a composite with one among its parts and none broken. */
	public static final int FABRIC = 1;
	/** A Fabric model whose drawing threw, or a composite with one among its parts: drawn from its parts. */
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
	 * Puts a throw while drawing a model down to the Fabric models it is (the model, or a composite's Fabric parts), from
	 * the stack trace ({@link ThrowSite}), and marks what threw broken. Called on the failure path only; the caller draws
	 * this block from its parts whatever is marked.
	 * <ul>
	 * <li>A Fabric model whose own emitter threw (the emitter call nearest the throw is its class's) is marked.</li>
	 * <li>If none did, and one of them called a model that threw (a model it wraps, as Continuity's wrap the models they
	 * are given), nothing is marked: the wrapper's other blocks keep their own drawing, and this one is drawn from its
	 * parts each time it throws. Logged once for each wrapper and class that threw ({@link #wrappedThrew}).</li>
	 * <li>If the stack shows none of their emitters, all of them are marked; with no Fabric model at all, the throw is
	 * {@link #unattributed}.</li>
	 * </ul>
	 *
	 * @param drawn        the model drawn
	 * @param fabricModels the Fabric models it is, or is made of
	 * @param classes      what Fabricated knows about each model class (each version's)
	 * @param method       the emitter method's name (each version's)
	 */
	public static void putDown(Object drawn, List<?> fabricModels, ClassValue<ModelClass> classes, String method, Throwable error, String where, Object block) {
		boolean marked = false;
		Class<?> wrapper = null;
		StackTraceElement wrapped = null;
		for (Object fabricModel : fabricModels) {
			ModelClass type = classes.get(fabricModel.getClass());
			ThrowSite.Blame blame = ThrowSite.blame(error, method, type.emitter);
			if (blame.kind() == ThrowSite.Kind.OWN) {
				markBroken(fabricModel.getClass(), type, error, where, block);
				marked = true;
			} else if (blame.kind() == ThrowSite.Kind.CALLED && wrapped == null) {
				wrapper = fabricModel.getClass();
				wrapped = blame.nearest();
			}
		}
		if (marked) return;
		if (wrapped != null) {
			wrappedThrew(wrapper, wrapped, error, where, block);
			return;
		}
		for (Object fabricModel : fabricModels) markBroken(fabricModel.getClass(), classes.get(fabricModel.getClass()), error, where, block);
		if (fabricModels.isEmpty()) unattributed(drawn.getClass(), error, where, block);
	}

	/**
	 * Marks a model class broken: it is drawn from its parts from now on. The first time, logs the failure with its stack
	 * trace and adds a report line naming the class's mod (and writes the report: Forge has no rewrite once loading
	 * completes). Called on the failure path only.
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
				+ "): " + error + ". They are drawn from the parts they give from now on, which may look different or be empty; update"
				+ " the mod, or report it to its author.";
		LOGGER.error("{}", text, error);
		report(text);
	}

	/**
	 * Logs, once for each wrapper class and class that threw inside it, that a model a Fabric model wraps threw while
	 * drawn: the wrapper isn't marked, so its other blocks keep their own drawing, and this block is drawn from its parts
	 * each time it throws. A report line names both mods.
	 */
	static void wrappedThrew(Class<?> wrapper, StackTraceElement thrower, Throwable error, String where, Object block) {
		synchronized (LOCK) {
			if (!WRAPPED.add(thrower.getClassName() + " in " + wrapper.getName())) return;
		}
		String text = "A model wrapped by a Fabric block model of class " + wrapper.getName() + " (" + modOf(wrapper) + ") threw while drawn in "
				+ where + " (" + block + "), in class " + thrower.getClassName() + " (" + modOf(thrower.getModuleName()) + "): " + error
				+ ". That block is drawn from its parts each time it throws; the wrapper's other blocks keep their own drawing. Update the"
				+ " mod the class that threw belongs to, or report it to its author.";
		LOGGER.error("{}", text, error);
		report(text);
	}

	/**
	 * Logs, once per model class, a throw no Fabric model could be named for (a composite whose parts no longer show the
	 * one that threw): that block is drawn from its parts each time it throws.
	 */
	public static void unattributed(Class<?> type, Throwable error, String where, Object block) {
		synchronized (LOCK) {
			if (!UNATTRIBUTED.add(type.getName())) return;
		}
		LOGGER.error("Drawing a block model of class {} with Fabric's renderer threw in {} ({}), and no Fabric model in it could be named:"
				+ " it is drawn from its parts each time it throws", type.getName(), where, block, error);
	}

	/**
	 * Logs, once per model class, that the parts a broken model gives threw too: nothing more of that block is drawn
	 * where it threw. A report line names the class's mod.
	 */
	public static void partsThrew(Class<?> type, Throwable error, String where, Object block) {
		synchronized (LOCK) {
			if (!PARTS_THREW.add(type.getName())) return;
		}
		String text = "The parts of block models of class " + type.getName() + " (" + modOf(type) + ") threw while drawn in " + where + " ("
				+ block + "): " + error + ". Nothing more of such a block is drawn there; update the mod, or report it to its author.";
		LOGGER.error("{}", text, error);
		report(text);
	}

	private static void report(String text) {
		FabricatedState state = FabricatedState.get();
		if (state != null) {
			state.report().line("Rendering", text);
			state.writeReport();
		}
	}

	/** The mod a class belongs to, as its module names it: the mod's name and id, or the module's name. */
	static String modOf(Class<?> type) {
		return modOf(type.getModule().getName());
	}

	/** The mod a module is, by its name: the mod's name and id, or the module's name. */
	@SuppressWarnings("unchecked")
	static String modOf(@Nullable String module) {
		if (module == null) return "a class outside any mod";
		if (CONTAINER_BY_ID == null) return "module " + module;
		try {
			return ((Optional<? extends ModContainer>) CONTAINER_BY_ID.invokeExact(module))
					.map(container -> "mod " + container.getModInfo().getDisplayName() + ", " + module)
					.orElse("module " + module);
		} catch (Throwable e) {
			if (e instanceof VirtualMachineError fatal) throw fatal;
			return "module " + module;
		}
	}

	/**
	 * {@code ModList.getModContainerById} as {@code (String)Optional}: Forge 64 and later (Minecraft 26.x) made
	 * {@code ModList}'s methods static, where Forge 47 and 61 reach them through {@code ModList.get()} (as
	 * {@code ModMenuBridge} finds it).
	 */
	private static @Nullable MethodHandle containerById() {
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Method byId = ModList.class.getMethod("getModContainerById", String.class);
			MethodHandle handle = lookup.unreflect(byId);
			if (!Modifier.isStatic(byId.getModifiers())) {
				handle = MethodHandles.collectArguments(handle, 0, lookup.unreflect(ModList.class.getMethod("get")));
			}
			return handle.asType(MethodType.methodType(Optional.class, String.class));
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Forge's ModList has no public getModContainerById: rendering failures name modules, not mods ({})", e.toString());
			return null;
		}
	}
}
