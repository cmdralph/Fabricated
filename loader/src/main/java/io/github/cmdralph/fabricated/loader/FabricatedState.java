package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.accesswidener.AccessWidenerFile.InjectedInterface;
import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;
import io.github.cmdralph.fabricated.core.runtime.ModContainerImpl;

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
	private final Map<String, Pipeline.LoadedMod> byNeoForgeId;
	private final Map<String, String> fabricIdByNeoForgeId;

	FabricatedState(Pipeline.Result result, Path logsDir) {
		this.result = result;
		this.logsDir = logsDir;
		Map<String, Pipeline.LoadedMod> map = new LinkedHashMap<>();
		Map<String, String> ids = new LinkedHashMap<>();
		for (Pipeline.LoadedMod mod : result.mods()) {
			map.put(mod.translated().neoForgeId(), mod);
			ids.put(mod.translated().neoForgeId(), mod.translated().candidate().id());
		}
		this.byNeoForgeId = Collections.unmodifiableMap(map);
		this.fabricIdByNeoForgeId = Collections.unmodifiableMap(ids);
	}

	/** The state, or {@code null} when Fabricated did not run discovery (e.g. it failed to start). */
	public static FabricatedState get() {
		return instance;
	}

	static void set(FabricatedState state) {
		instance = state;
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
		return byNeoForgeId.containsKey(neoForgeId);
	}

	/** Fabric id for a translated mod's NeoForge id. */
	public Optional<String> fabricId(String neoForgeId) {
		return Optional.ofNullable(fabricIdByNeoForgeId.get(neoForgeId));
	}

	public Optional<ModContainerImpl> fabricContainer(String neoForgeId) {
		String fabricId = fabricIdByNeoForgeId.get(neoForgeId);
		return fabricId == null ? Optional.empty() : Optional.ofNullable(result.containers().get(fabricId));
	}

	public Map<String, List<InjectedInterface>> injectedInterfaces() {
		return result.injectedInterfaces();
	}

	/** Writes {@code logs/fabric-bridge-report.txt}; failures are logged, never thrown. */
	public void writeReport() {
		try {
			result.report().write(logsDir);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", Report.FILE_NAME, e.toString());
		}
	}
}
