package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.mixin.InvokerBridge;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures;
import io.github.cmdralph.fabricated.core.report.DiagnosticsBlock;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.Candidate;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;
import io.github.cmdralph.fabricated.core.runtime.ModContainerImpl;
import io.github.cmdralph.fabricated.core.translate.DefaultBridge;
import io.github.cmdralph.fabricated.core.translate.Interop;

/**
 * State shared between Fabricated's discovery services (which run in NeoForge's early-services class
 * loader) and its in-game mod. Classes in this package are not part of the game module layer, so the
 * game layer resolves them through its parent loader and both sides see the same instance.
 */
public final class FabricatedState {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static volatile FabricatedState instance;

	private final Pipeline.Result result;
	private final Path logsDir;
	private final Map<String, String> fabricIdByNeoForgeId;
	private final Map<String, List<Interop.Annotation>> pluginAnnotations;
	private final FabricatedApiImpl api;
	/**
	 * Where a Fabric mod's mixin that fails as Mixin applies it is recorded (APPLY-1), and what the diagnostics block
	 * names of the launch.
	 */
	private final Pipeline.Diagnostics diagnostics;
	private final Map<String, Pipeline.LoadedMod> modsByFabricId;
	/** The mods as the diagnostics block tells them apart, made when first needed (a failure). */
	private volatile DiagnosticsBlock block;
	/** The mixins whose failure this launch recorded, at most {@link #MAX_FAILED}. */
	private final Map<String, Failed> failed = new ConcurrentHashMap<>();
	private static final int MAX_FAILED = 64;

	/** A failure this launch recorded: the class Mixin applied the mixin to (or empty), and whether the next launch leaves it out. */
	private record Failed(String target, boolean held) {
	}

	FabricatedState(Pipeline.Result result, Path logsDir, FabricatedApiImpl api, Pipeline.Diagnostics diagnostics) {
		this.result = result;
		this.logsDir = logsDir;
		this.api = api;
		this.diagnostics = diagnostics;
		Map<String, Pipeline.LoadedMod> mods = new HashMap<>();
		for (Pipeline.LoadedMod mod : result.mods()) mods.put(mod.translated().candidate().id(), mod);
		this.modsByFabricId = Map.copyOf(mods);
		Map<String, String> ids = new LinkedHashMap<>();
		Map<String, List<Interop.Annotation>> plugins = new HashMap<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			ids.put(mod.translated().neoForgeId(), mod.translated().candidate().id());
			if (!mod.pluginAnnotations().isEmpty()) plugins.put(mod.translated().neoForgeId(), mod.pluginAnnotations());
		}
		this.fabricIdByNeoForgeId = Collections.unmodifiableMap(ids);
		this.pluginAnnotations = Map.copyOf(plugins);
	}

	/** The state, or {@code null} when Fabricated did not run discovery (e.g. it failed to start). */
	public static FabricatedState get() {
		return instance;
	}

	static void set(FabricatedState state) {
		instance = state;
	}

	/** Fabricated's public API (also published through {@code Fabricated.get()}). */
	public FabricatedApiImpl api() {
		return api;
	}

	public FabricLoaderRuntime runtime() {
		return result.runtime();
	}

	public FabricatedConfig config() {
		return result.config();
	}

	public Report report() {
		return result.report();
	}

	/** Loaded Fabric mods in load order. */
	public List<Pipeline.LoadedMod> mods() {
		return result.mods();
	}

	public boolean isFabricMod(String neoForgeId) {
		return fabricIdByNeoForgeId.containsKey(neoForgeId);
	}

	/** What to add to a Fabric mod's scan data for other mods' platform builds to find its plugins ({@link PluginAnnotations}). */
	public List<Interop.Annotation> pluginAnnotations(String neoForgeId) {
		return pluginAnnotations.getOrDefault(neoForgeId, List.of());
	}

	public Optional<ModContainerImpl> fabricContainer(String neoForgeId) {
		String fabricId = fabricIdByNeoForgeId.get(neoForgeId);
		return fabricId == null ? Optional.empty() : Optional.ofNullable(result.containers().get(fabricId));
	}

	/**
	 * Whether another mod provides the Fabric API module (its Fabric id, e.g. {@code fabric-networking-api-v1}): then
	 * that mod's copy is the one loaded, and Fabricated's own part of the module (its packages, mixins and bridges)
	 * stands down.
	 */
	public boolean providedElsewhere(String module) {
		return result.providedElsewhere().contains(module);
	}

	/** Classes of loaded Fabric mods with {@code @Environment} annotations (see {@link FabricClassTransforms#beforeMixin}). */
	public Set<String> environmentClasses() {
		return result.environmentClasses();
	}

	public Map<String, List<InjectedInterface>> injectedInterfaces() {
		return result.injectedInterfaces();
	}

	/** Methods to declare on game classes, by class (see {@link FabricClassTransforms#declareDefaultBridges}). */
	public Map<String, List<DefaultBridge>> defaultBridges() {
		return result.defaultBridges();
	}

	/** Game methods Fabric mods override that the platform made final (name and descriptor), by class (FINAL-2). */
	public Map<String, Set<String>> overridableMethods() {
		return result.overridableMethods();
	}

	/**
	 * The static handlers of other mods' mixins into Fabric mods' classes to apply as instance handlers (name and
	 * descriptor), by mixin class (CORPUS-87, {@link io.github.cmdralph.fabricated.core.mixin.NativeModMixins}).
	 */
	public Map<String, Set<String>> instanceHandlers() {
		return result.instanceHandlers();
	}

	/**
	 * The game classes in which Fabric mods' mixins hook vanilla methods the platform took out, which get them put back as they load
	 * (SEAM-1, {@link io.github.cmdralph.fabricated.core.bytecode.VanillaSeams}).
	 */
	public Set<String> restoredSeams() {
		return result.restoredSeams();
	}

	/**
	 * The methods game classes declare as they load for Fabric mods' invokers of vanilla methods the platform gave a
	 * parameter vanilla's never had, by class (LEAVE-6, {@link InvokerBridge}).
	 */
	public Map<String, List<InvokerBridge>> invokerBridges() {
		return result.invokerBridges();
	}

	/**
	 * Records the mixin failure a throwable the game stops on names (or one the platform holds beside it, {@code more}:
	 * mod loading's errors), if it is a loaded Fabric mod's, for the next launch to leave out (APPLY-1), and returns the
	 * diagnostics block for the throwable: the Fabric mods it involves, or null where it involves none. It runs as the
	 * game stops, so it never throws: what fails is logged.
	 */
	public String diagnose(Throwable thrown, List<Throwable> more) {
		try {
			MixinFailures.Named named = MixinFailures.find(thrown);
			for (int i = 0; named == null && i < more.size(); i++) named = MixinFailures.find(more.get(i));
			return diagnose(named, thrown, more);
		} catch (RuntimeException | LinkageError | VirtualMachineError e) {
			LOGGER.debug("Could not diagnose {}", thrown, e);
			return null;
		}
	}

	/**
	 * {@link #diagnose(Throwable, List)} for a mixin failure Mixin reported ({@code named}, or null), with the throwable
	 * it gave (or null).
	 */
	public String diagnose(MixinFailures.Named named, Throwable thrown, List<Throwable> more) {
		try {
			recordMixinFailure(named);
			return diagnosticsBlock(named, thrown, more);
		} catch (RuntimeException | LinkageError | VirtualMachineError e) {
			LOGGER.debug("Could not diagnose {}", thrown, e);
			return null;
		}
	}

	/**
	 * Records the mixin of a loaded Fabric mod that failed as Mixin applied it, which the next launch leaves out
	 * (APPLY-1), and logs it the first time; a mixin of any other mod is not recorded. Never throws for what it writes:
	 * what fails is logged.
	 */
	public void recordMixinFailure(MixinFailures.Named named) {
		if (named == null || diagnostics == null) return;
		DiagnosticsBlock.Mod of = block().modOf(named.config(), named.platformModId());
		Pipeline.LoadedMod mod = of == null ? null : modsByFabricId.get(of.id());
		if (mod == null) return;
		String id = mod.translated().candidate().id();
		List<String> notes = new ArrayList<>();
		boolean added = diagnostics.mixinFailures().record(new MixinFailures.Failure(id, mod.translated().candidate().sha256(), named.mixin(), named.reason(),
				named.target()), notes);
		notes.forEach(n -> LOGGER.warn("The failure of mixin {} of {} was {}", named.mixin(), id, n));
		if (failed.size() < MAX_FAILED || failed.containsKey(named.mixin())) {
			Failed now = new Failed(named.target(), added || notes.isEmpty());
			failed.merge(named.mixin(), now, (was, is) -> new Failed(was.target().isEmpty() ? is.target() : was.target(), was.held() || is.held()));
		}
		if (added) {
			LOGGER.error("Mixin {} of {} failed as Mixin applied it ({}). Fabricated leaves it out from the next launch on, until the mod, Fabricated,"
					+ " Minecraft or {} changes; please report it to Fabricated with logs/{}.", named.mixin(), id, named.reason(),
					diagnostics.launch().loader(), Report.FILE_NAME);
		}
	}

	/**
	 * The diagnostics block for a failure: the mod of the mixin Mixin named ({@code named}, or null), with the class it
	 * applied it to, then the mods whose code the stack traces run. Null where none is a loaded Fabric mod, or where the
	 * mixin is another mod's: the block names only what is certain.
	 */
	String diagnosticsBlock(MixinFailures.Named named, Throwable thrown, List<Throwable> more) {
		if (diagnostics == null) return null;
		DiagnosticsBlock.Entry first = null;
		boolean held = false;
		if (named != null) {
			DiagnosticsBlock.Mod mod = block().modOf(named.config(), named.platformModId());
			if (mod == null) return null;
			Failed f = failed.get(named.mixin());
			held = f != null && f.held();
			first = new DiagnosticsBlock.Entry(mod, named.mixin(), !named.target().isEmpty() || f == null ? named.target() : f.target(), null);
		}
		List<DiagnosticsBlock.Entry> entries = block().entries(thrown, more, first);
		String outcome = first == null ? DiagnosticsBlock.UNCHANGED : held ? DiagnosticsBlock.LEFT_OUT : DiagnosticsBlock.NOT_RECORDED;
		return DiagnosticsBlock.render(entries, diagnostics.launch(), outcome);
	}

	/** The loaded Fabric mods as the diagnostics block tells them apart: their own packages and their mixin configs. */
	private DiagnosticsBlock block() {
		DiagnosticsBlock b = block;
		if (b != null) return b;
		List<DiagnosticsBlock.Owner> owners = new ArrayList<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			Candidate c = mod.translated().candidate();
			Set<String> packages = new HashSet<>(c.packages());
			// A package left out for the platform's copy: its classes are the platform's.
			packages.removeAll(mod.packages().excludedPackages());
			Set<String> configs = new HashSet<>();
			c.metadata().mixins().forEach(m -> configs.add(m.config()));
			if (c.metadata().compatHints() != null) c.metadata().compatHints().mixinsOr(List.of()).forEach(m -> configs.add(m.config()));
			owners.add(new DiagnosticsBlock.Owner(Pipeline.diagnosed(c), mod.translated().neoForgeId(), packages, configs));
		}
		b = new DiagnosticsBlock(owners, diagnostics.mixinNamesMods());
		block = b;
		return b;
	}

	/**
	 * Writes {@code logs/fabric-bridge-report.txt}, the mixins Fabric mods' plugins turned down by then apart from those
	 * pre-flight left out ({@link PluginAnswers}); failures are logged, never thrown.
	 */
	public void writeReport() {
		try {
			Pipeline.leftOutByPlugins(result.report(), result.mods(), PluginAnswers.rejected());
		} catch (RuntimeException e) {
			LOGGER.warn("Could not list the mixins Fabric mods' plugins left out: {}", e.toString());
		}
		try {
			result.report().write(logsDir);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", Report.FILE_NAME, e.toString());
		}
	}
}
