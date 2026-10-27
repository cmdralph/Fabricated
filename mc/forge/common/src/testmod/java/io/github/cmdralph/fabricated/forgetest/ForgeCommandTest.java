package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

/**
 * The forge test mod's part for fabric-command-api-v2 (with Fabric API's jar only): a command registered through
 * {@code CommandRegistrationCallback}, which Fabric API's hook into {@code Commands}' constructor fires, must be in the
 * server's dispatcher. Forge's Mixin takes that hook only at a point marked unsafe, as pre-flight marks it (CTOR-2).
 */
public final class ForgeCommandTest {
	private static final String COMMAND = "fabricatedtest";

	private ForgeCommandTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's command module. */
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(Commands.literal(COMMAND).executes(context -> 1)));
	}

	/** At the first server tick, with the server's commands. */
	public static void check(MinecraftServer server) {
		if (server.getCommands().getDispatcher().getRoot().getChild(COMMAND) != null) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST Fabric command registered OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST Fabric command {} is not in the server's dispatcher", COMMAND);
		}
	}
}
