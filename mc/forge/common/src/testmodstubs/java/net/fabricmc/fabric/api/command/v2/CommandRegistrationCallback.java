package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Stand-in for fabric-command-api-v2's {@code CommandRegistrationCallback}, which the forge test mod uses (signatures as
 * upstream's on 1.21.11, 26.1.2, 26.2 and 26.3): Fabricated passes the module through, so the test mod compiles against
 * this and runs against Fabric API's jar. Never packaged.
 */
@FunctionalInterface
public interface CommandRegistrationCallback {
	Event<CommandRegistrationCallback> EVENT = null;

	void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection selection);
}
