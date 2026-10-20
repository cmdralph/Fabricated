package io.github.cmdralph.fabricated.neoforge;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

/**
 * {@code /fabricated}: prints a summary of Fabric mod loading. {@code /fabricated disabled} lists the Fabric mods that
 * didn't load, with why; {@code /fabricated why <mod>} tells one mod's outcome and what to do about it;
 * {@code /fabricated disable <mod>} and {@code enable <mod>} change {@code config/fabricated.json} for the next launch;
 * {@code /fabricated report} rewrites {@code logs/fabric-bridge-report.txt}. On a server all but the summary are limited
 * to the console, so players can't write files on the host or read its file names.
 */
final class FabricatedCommands {
	/** The most mods {@code /fabricated disabled} lists in chat; the report has them all. */
	private static final int MAX_LISTED = 20;

	private FabricatedCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, FabricatedState state, boolean clientSide) {
		Predicate<CommandSourceStack> local = source -> clientSide || source.getPlayer() == null;
		dispatcher.register(Commands.literal("fabricated")
				.executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(summary(state)), false);
					return 1;
				})
				.then(Commands.literal("disabled")
						.requires(local)
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
				.then(Commands.literal("why")
						.requires(local)
						.then(modArgument(state).executes(context -> {
							String id = StringArgumentType.getString(context, "mod");
							List<Report.ModRow> rows = state.report().mods().stream().filter(r -> r.id().equals(id)).toList();
							if (rows.isEmpty()) context.getSource().sendFailure(Component.literal("No Fabric mod " + id + " was found."));
							for (Report.ModRow row : rows) context.getSource().sendSuccess(() -> Component.literal(why(row)), false);
							return rows.size();
						})))
				.then(toggle("disable", true, state, local))
				.then(toggle("enable", false, state, local))
				.then(Commands.literal("report")
						.requires(local)
						.executes(context -> {
							state.writeReport();
							context.getSource().sendSuccess(() -> Component.literal("Wrote logs/" + Report.FILE_NAME), false);
							return 1;
						})));
	}

	/** A Fabric mod id, completed from the mods in the report (any id is accepted: one may be added next launch). */
	private static RequiredArgumentBuilder<CommandSourceStack, String> modArgument(FabricatedState state) {
		return Commands.argument("mod", StringArgumentType.word()).suggests((context, builder) ->
				SharedSuggestionProvider.suggest(state.report().mods().stream().map(Report.ModRow::id).distinct(), builder));
	}

	/** {@code disable <mod>} or {@code enable <mod>}: the mod's entry in the config file, for the next launch. */
	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, boolean disabled,
			FabricatedState state, Predicate<CommandSourceStack> local) {
		return Commands.literal(name).requires(local).then(modArgument(state).executes(context -> {
			String id = StringArgumentType.getString(context, "mod");
			try {
				FabricatedConfig.setModDisabled(FMLPaths.CONFIGDIR.get(), id, disabled);
			} catch (IOException | JsonException e) {
				context.getSource().sendFailure(Component.literal("Could not change config/" + FabricatedConfig.FILE_NAME + ": " + e.getMessage()));
				return 0;
			}
			context.getSource().sendSuccess(() -> Component.literal("Fabric mod " + id + " will be " + (disabled ? "disabled" : "enabled")
					+ " from the next launch (config/" + FabricatedConfig.FILE_NAME + ")."), false);
			return 1;
		}));
	}

	/** One mod's outcome, why, and what to do about it. */
	static String why(Report.ModRow row) {
		String outcome = switch (row.status()) {
			case LOADED -> "loaded";
			case DISABLED -> "disabled";
			case REJECTED -> "rejected";
			case PROVIDED_NATIVELY -> "provided by Fabricated";
			case SKIPPED -> "skipped";
		};
		return row.id() + (row.version().isEmpty() ? "" : " " + row.version()) + ": " + outcome
				+ (row.detail().isEmpty() ? "" : ". " + row.detail())
				+ (row.suggestion().isEmpty() ? "" : " Fix: " + row.suggestion());
	}

	static String summary(FabricatedState state) {
		Report report = state.report();
		return "Fabricated: " + report.count(Report.Status.LOADED) + " Fabric mod(s) loaded, "
				+ report.count(Report.Status.DISABLED) + " disabled, " + report.count(Report.Status.REJECTED) + " rejected, "
				+ report.count(Report.Status.PROVIDED_NATIVELY) + " Fabric API module(s) provided natively. "
				+ "Details: logs/" + Report.FILE_NAME;
	}
}
