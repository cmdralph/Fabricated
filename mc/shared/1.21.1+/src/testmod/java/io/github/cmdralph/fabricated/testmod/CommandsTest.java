package io.github.cmdralph.fabricated.testmod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;

/**
 * CI test mod part for Fabricated's {@code /fabricated} command, run from the server's console: {@code why} tells this
 * mod's outcome, and {@code disable}/{@code enable} change a mod's entry in {@code config/fabricated.json}.
 */
public final class CommandsTest implements ModInitializer {
	/** A mod that isn't installed, so changing its entry changes nothing at the next launch. */
	private static final String DUMMY = "fabricated-ci-dummy";
	private static final Pattern DUMMY_ENTRY = Pattern.compile("\"" + DUMMY + "\": \\{\\s*\"disabled\": (true|false)");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
			CommandSourceStack console = server.createCommandSourceStack();
			Path config = FabricLoader.getInstance().getConfigDir().resolve("fabricated.json");
			try {
				if (dispatcher.execute("fabricated why fabricated-testmod", console) < 1) failed.add("why");
				if (dispatcher.execute("fabricated disable " + DUMMY, console) != 1 || !"true".equals(dummyEntry(config))) failed.add("disable");
				if (dispatcher.execute("fabricated enable " + DUMMY, console) != 1 || !"false".equals(dummyEntry(config))) failed.add("enable");
			} catch (CommandSyntaxException | IOException e) {
				failed.add(e.toString());
			}
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST commands OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST commands FAILED: {}", failed);
			}
		});
	}

	/** Whether the config file disables the dummy mod ("true" or "false"), or null without an entry. */
	private static @Nullable String dummyEntry(Path config) throws IOException {
		Matcher matcher = DUMMY_ENTRY.matcher(Files.readString(config));
		return matcher.find() ? matcher.group(1) : null;
	}
}
