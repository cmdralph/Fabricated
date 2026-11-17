package io.github.cmdralph.fabricated.command;

import java.util.function.Supplier;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** A command's feedback to its source, as Minecraft 1.19.2 sends it: made before the source is asked whether it takes it. */
final class CommandFeedback {
	private CommandFeedback() {
	}

	static void success(CommandSourceStack source, Supplier<String> text) {
		source.sendSuccess(Component.literal(text.get()), false);
	}
}
