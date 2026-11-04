package io.github.cmdralph.fabricated.forge.entity;

import java.util.List;
import java.util.function.IntSupplier;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.impl.entity.event.effect.EffectEventContextImpl;
import net.fabricmc.fabric.impl.entity.event.effect.MobEffectUtil;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The command a mob effect comes from, for Fabric API's effect events (fabric-entity-events-v1, passed through): the
 * {@code EffectEventContext} their listeners get. Upstream sets it in a hook into Brigadier's
 * {@code ContextChain.runExecutable}, a library class Forge loads without mixins, so that hook is left out, and
 * {@code ExecuteCommandEffectMixin} sets the same context where vanilla's command execution calls it, which runs every
 * command the game runs (a player's, the console's, a function's, a command block's). Only a mod running a command
 * through Brigadier's own {@code CommandDispatcher.execute} still goes without it.
 */
public final class EffectCommandContext {
	/** Fabric API's entity events module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-entity-events-v1");

	private EffectCommandContext() {
	}

	/**
	 * Runs vanilla's call of {@code executable}, inside the context upstream's hook gives it.
	 *
	 * @param run vanilla's call, answering the command's result
	 */
	public static int run(CommandContext<?> executable, IntSupplier run) {
		return ENABLED ? Upstream.run(executable, run) : run.getAsInt();
	}

	/** Everything that names Fabric's entity events module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static int run(CommandContext<?> executable, IntSupplier run) {
			// As upstream: the command is named by its first node, a literal; anything else runs without a context.
			List<? extends ParsedCommandNode<?>> nodes = executable.getNodes();
			if (nodes.isEmpty() || !(nodes.get(0).getNode() instanceof LiteralCommandNode<?> command)) return run.getAsInt();
			MobEffectUtil.pushContext(new EffectEventContextImpl(true, command.getName()));
			try {
				return run.getAsInt();
			} finally {
				MobEffectUtil.popContext();
			}
		}
	}
}
