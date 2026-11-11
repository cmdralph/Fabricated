package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.api.FabricatedApiImpl;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.mixin.MixinFailures;
import io.github.cmdralph.fabricated.core.report.Report;
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
	/** Where a Fabric mod's mixin that fails as Mixin applies it is recorded (APPLY-1), or null for nowhere. */
	private final MixinFailures.Log mixinFailures;
	private final Map<String, Pipeline.LoadedMod> modsByNeoForgeId;

	FabricatedState(Pipeline.Result result, Path logsDir, FabricatedApiImpl api) {
		this(result, logsDir, api, null);
	}

	FabricatedState(Pipeline.Result result, Path logsDir, FabricatedApiImpl api, MixinFailures.Log mixinFailures) {
		this.result = result;
		this.logsDir = logsDir;
		this.api = api;
		this.mixinFailures = mixinFailures;
		Map<String, Pipeline.LoadedMod> mods = new HashMap<>();
		for (Pipeline.LoadedMod mod : result.mods()) mods.put(mod.translated().neoForgeId(), mod);
		this.modsByNeoForgeId = Map.copyOf(mods);
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

	/** Classes of loaded Fabric mods with {@code @Environment} annotations (see {@link FabricClassProcessor}). */
	public Set<String> environmentClasses() {
		return result.environmentClasses();
	}

	public Map<String, List<InjectedInterface>> injectedInterfaces() {
		return result.injectedInterfaces();
	}

	/** Methods to declare on game classes, by class (see {@link FabricClassProcessor}). */
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
	 * The game classes Fabric mods' mixins target that get vanilla methods the platform took out put back as they load
	 * (SEAM-1, {@link io.github.cmdralph.fabricated.core.bytecode.VanillaSeams}).
	 */
	public Set<String> restoredSeams() {
		return result.restoredSeams();
	}

	/**
	 * Records the mixin of a loaded Fabric mod that failed as Mixin applied it, which the next launch leaves out
	 * (APPLY-1); a mixin of any other mod is not recorded. Returns what it recorded ({@code <mixin> of <mod>}), or null
	 * where nothing new was. It runs as the game stops, so it never throws: what fails is logged.
	 */
	public String recordMixinFailure(MixinFailures.Named named) {
		if (named == null || mixinFailures == null) return null;
		Pipeline.LoadedMod mod = modsByNeoForgeId.get(named.platformModId());
		if (mod == null) return null;
		String id = mod.translated().candidate().id();
		List<String> notes = new ArrayList<>();
		boolean added = mixinFailures.record(new MixinFailures.Failure(id, mod.translated().candidate().sha256(), named.mixin(), named.reason()), notes);
		notes.forEach(n -> LOGGER.warn("The failure of mixin {} of {} was {}", named.mixin(), id, n));
		return added ? named.mixin() + " of " + id : null;
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
