package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for fabric-command-api-v2 (with Fabric API's jar only): a command registered through
 * {@code CommandRegistrationCallback}, which Fabric API's hook into {@code Commands}' constructor fires, must be in the
 * server's dispatcher (CTOR-4: Forge 47's Mixin left that hook out).
 */
public final class ForgeCommandTest {
	static final String COMMAND = "fabricatedtest";

	private ForgeCommandTest() {
	}

	/** From the main entrypoint. */
	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-command-api-v2")) return;
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal(COMMAND).executes(context -> 1)));
	}

	/** At the first server tick, with the server's commands. */
	public static void check(Commands commands) {
		if (!FabricLoader.getInstance().isModLoaded("fabric-command-api-v2")) return;
		if (commands.getDispatcher().getRoot().getChild(COMMAND) != null) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST Fabric command registered OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST Fabric command {} is not in the server's dispatcher", COMMAND);
		}
	}
}
