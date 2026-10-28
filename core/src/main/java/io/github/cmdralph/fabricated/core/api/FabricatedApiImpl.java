package io.github.cmdralph.fabricated.core.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.FabricMod;
import io.github.cmdralph.fabricated.api.Fabricated;
import io.github.cmdralph.fabricated.api.FabricatedApi;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.core.resolve.FabricApiPlan;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;

/** Fabricated's {@link FabricatedApi}, built from the pipeline's report once discovery is done. */
public final class FabricatedApiImpl implements FabricatedApi {
	private final String fabricatedVersion;
	private final FabricApiPlan plan;
	private final Report report;
	private final FabricLoaderRuntime runtime;
	private final List<Runnable> pending = new ArrayList<>();
	private volatile Set<String> failedMods = Set.of();
	private volatile boolean entrypointsComplete;

	/**
	 * @param entrypointsPending whether Fabric entrypoints will run this launch (Fabricated's mod is loaded); when not,
	 *                           {@link #afterEntrypoints} actions run right away
	 */
	public FabricatedApiImpl(String fabricatedVersion, FabricApiPlan plan, Report report, FabricLoaderRuntime runtime, boolean entrypointsPending) {
		this.fabricatedVersion = fabricatedVersion;
		this.plan = plan;
		this.report = report;
		this.runtime = runtime;
		this.entrypointsComplete = !entrypointsPending;
	}

	@Override
	public String fabricatedVersion() {
		return fabricatedVersion;
	}

	@Override
	public String apiVersion() {
		return Fabricated.API_VERSION;
	}

	@Override
	public String fabricApiVersion() {
		return plan.fabricApiVersion();
	}

	@Override
	public List<FabricMod> fabricMods() {
		Set<String> failed = failedMods;
		List<FabricMod> mods = new ArrayList<>();
		for (Report.ModRow row : report.mods()) {
			FabricMod.Status status = switch (row.status()) {
				case LOADED -> failed.contains(row.id()) ? FabricMod.Status.FAILED : FabricMod.Status.LOADED;
				case DISABLED -> FabricMod.Status.DISABLED;
				case SKIPPED -> FabricMod.Status.SKIPPED;
				case REJECTED, PROVIDED_NATIVELY, PROVIDED_BY_MOD -> null;
			};
			if (status == null) continue;
			String name = runtime.getModContainer(row.id()).map(c -> c.getMetadata().getName()).orElse(row.id());
			mods.add(new FabricMod(row.id(), ModIds.toNeoForgeId(row.id()), row.version(), name, row.source(), status, row.detail(), row.suggestion()));
		}
		return List.copyOf(mods);
	}

	@Override
	public Optional<FabricMod> fabricMod(String id) {
		return fabricMods().stream().filter(m -> m.id().equals(id) || m.neoForgeId().equals(id)).findFirst();
	}

	@Override
	public List<FabricApiModule> fabricApiModules() {
		// A module another mod provides has a row saying so, which may follow one for the copy that was skipped for it.
		Map<String, Report.ModRow> rows = new HashMap<>();
		for (Report.ModRow row : report.mods()) {
			if (row.status() == Report.Status.PROVIDED_BY_MOD) rows.put(row.id(), row);
			else rows.putIfAbsent(row.id(), row);
		}
		List<FabricApiModule> modules = new ArrayList<>();
		for (FabricApiPlan.Module m : plan.modules().values()) {
			Report.ModRow row = rows.get(m.id());
			if (row != null && row.status() == Report.Status.PROVIDED_BY_MOD) {
				modules.add(new FabricApiModule(m.id(), FabricApiModule.Status.PROVIDED_BY_MOD, row.version(), "provided by " + row.source()));
				continue;
			}
			modules.add(switch (m.plan()) {
				case NATIVE -> new FabricApiModule(m.id(), FabricApiModule.Status.NATIVE, m.version(), m.reason());
				case UNSUPPORTED -> new FabricApiModule(m.id(), FabricApiModule.Status.UNSUPPORTED, "", m.reason());
				default -> row == null ? new FabricApiModule(m.id(), FabricApiModule.Status.NOT_INSTALLED, "", "")
						: row.status() == Report.Status.LOADED ? new FabricApiModule(m.id(), FabricApiModule.Status.LOADED, row.version(), m.reason())
						: new FabricApiModule(m.id(), FabricApiModule.Status.DISABLED, row.version(), row.detail());
			});
		}
		return List.copyOf(modules);
	}

	@Override
	public Optional<FabricApiModule> fabricApiModule(String id) {
		return fabricApiModules().stream().filter(m -> m.id().equals(id)).findFirst();
	}

	@Override
	public boolean entrypointsComplete() {
		return entrypointsComplete;
	}

	@Override
	public void afterEntrypoints(Runnable action) {
		synchronized (pending) {
			if (!entrypointsComplete) {
				pending.add(action);
				return;
			}
		}
		action.run();
	}

	@Override
	public String renderReport() {
		return report.render();
	}

	/**
	 * Called once the Fabric entrypoints have run: records which mods failed and runs the waiting actions, in order.
	 *
	 * @param onError told about an action that threw; the others still run
	 */
	public void markEntrypointsComplete(Set<String> failed, BiConsumer<Runnable, Throwable> onError) {
		List<Runnable> actions;
		synchronized (pending) {
			failedMods = Set.copyOf(failed);
			entrypointsComplete = true;
			actions = List.copyOf(pending);
			pending.clear();
		}
		for (Runnable action : actions) {
			try {
				action.run();
			} catch (RuntimeException | LinkageError e) {
				onError.accept(action, e);
			}
		}
	}
}
