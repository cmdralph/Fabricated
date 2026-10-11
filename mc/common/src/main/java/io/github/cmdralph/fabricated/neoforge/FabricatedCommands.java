package io.github.cmdralph.fabricated.neoforge;

import com.mojang.brigadier.CommandDispatcher;

import io.github.cmdralph.fabricated.core.report.Report;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /fabricated}: prints a summary of Fabric mod loading. {@code /fabricated report} rewrites
 * {@code logs/fabric-bridge-report.txt}; on a server it is limited to the console so players cannot
 * write files on the host.
 */
final class FabricatedCommands {
	private FabricatedCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, FabricatedState state, boolean clientSide) {
		dispatcher.register(Commands.literal("fabricated")
				.executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(summary(state)), false);
					return 1;
				})
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
