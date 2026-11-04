package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * CI test mod part for {@code fabric-command-api-v2}'s argument types: the main initializer registers one, as Fabric mods
 * do, and as the server starts it must be in the argument type registry and known by its class, on a dedicated server and
 * on a client, where Fabricated runs the initializers in {@code Minecraft}'s constructor, after NeoForge's registration
 * events (PROVIDER-12: another mod's command API registering them only in such an event). A command taking it is
 * registered too, so the integrated server sends the joining player a command tree with it, which fails for a type the
 * game doesn't know.
 *
 * <p>The module is passed through from Fabric API's jar, or another mod's, and the test mod compiles only against
 * Fabricated's own modules, so its registry is called by reflection, by the name Fabric mods call.
 */
public final class ArgumentTypeTest implements ModInitializer {
	static final Identifier ID = Identifier.fromNamespaceAndPath("fabricated_testmod", "word");

	@Override
	public void onInitialize() {
		List<String> failed = new ArrayList<>();
		try {
			Class.forName("net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry")
					.getMethod("registerArgumentType", Identifier.class, Class.class, ArgumentTypeInfo.class)
					.invoke(null, ID, WordArgument.class, SingletonArgumentInfo.contextFree(WordArgument::new));
		} catch (ReflectiveOperationException e) {
			failed.add("registerArgumentType threw " + (e.getCause() != null ? e.getCause() : e));
		}
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> event.getDispatcher().register(
				Commands.literal("fabricated_testmod_word").then(Commands.argument("word", new WordArgument()).executes(context -> 1))));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> missing = new ArrayList<>(failed);
			if (!BuiltInRegistries.COMMAND_ARGUMENT_TYPE.containsKey(ID)) missing.add("not in the argument type registry");
			try {
				ArgumentTypeInfos.byClass(new WordArgument());
			} catch (IllegalArgumentException e) {
				missing.add("its class isn't known (" + e.getMessage() + ")");
			}
			TestMod.report("argument type", missing, "fabric-command-api-v2", Map.of());
		});
	}

	/** One word, as {@code StringArgumentType.word()} reads it. */
	public static final class WordArgument implements ArgumentType<String> {
		@Override
		public String parse(StringReader reader) {
			return reader.readUnquotedString();
		}
	}
}
