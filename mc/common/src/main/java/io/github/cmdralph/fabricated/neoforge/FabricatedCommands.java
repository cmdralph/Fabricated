package io.github.cmdralph.fabricated.neoforge;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /fabricated}: prints a summary of Fabric mod loading. {@code /fabricated disabled} lists the Fabric mods that
 * didn't load, with why, and {@code /fabricated report} rewrites {@code logs/fabric-bridge-report.txt}; on a server both
 * are limited to the console, so players can't write files on the host or read its file names.
 */
final class FabricatedCommands {
	/** The most mods {@code /fabricated disabled} lists in chat; the report has them all. */
	private static final int MAX_LISTED = 20;

	private FabricatedCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, FabricatedState state, boolean clientSide) {
		dispatcher.register(Commands.literal("fabricated")
				.executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(summary(state)), false);
					return 1;
				})
				.then(Commands.literal("disabled")
						.requires(source -> clientSide || source.getPlayer() == null)
						.executes(context -> {
							List<Report.ModRow> rows = state.report().mods().stream()
									.filter(r -> r.status() == Report.Status.DISABLED || r.status() == Report.Status.REJECTED).toList();
							if (rows.isEmpty()) context.getSource().sendSuccess(() -> Component.literal("No Fabric mod was left out."), false);
							for (Report.ModRow row : rows.subList(0, Math.min(rows.size(), MAX_LISTED))) {
								String line = row.id() + (row.version().isEmpty() ? "" : " " + row.version()) + ": " + row.detail();
								context.getSource().sendSuccess(() -> Component.literal(line), false);
							}
							if (rows.size() > MAX_LISTED) {
								context.getSource().sendSuccess(() -> Component.literal("... and " + (rows.size() - MAX_LISTED) + " more: see logs/" + Report.FILE_NAME), false);
							}
							return rows.size();
						}))
				.then(Commands.literal("report")
						.requires(source -> clientSide || source.getPlayer() == null)
						.executes(context -> {
							state.writeReport();
							context.getSource().sendSuccess(() -> Component.literal("Wrote logs/" + Report.FILE_NAME), false);
							return 1;
						})));
	}

	static String summary(FabricatedState state) {
		Report report = state.report();
		return "Fabricated: " + report.count(Report.Status.LOADED) + " Fabric mod(s) loaded, "
				+ report.count(Report.Status.DISABLED) + " disabled, " + report.count(Report.Status.REJECTED) + " rejected, "
				+ report.count(Report.Status.PROVIDED_NATIVELY) + " Fabric API module(s) provided natively. "
				+ "Details: logs/" + Report.FILE_NAME;
	}
}
