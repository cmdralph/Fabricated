package io.github.cmdralph.fabricated.forge.content;

import net.fabricmc.fabric.impl.content.registry.FlammableBlockRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The blocks Fabric API's flammable block registry ({@code FlammableBlockRegistry}, fabric-content-registries-v0,
 * passed through) names for vanilla's fire. Forge 43 takes a block for flammable only where its material is
 * ({@code IForgeBlock.isFlammable}), which vanilla 1.19.2's fire doesn't ask, nor Forge 47's: a block a Fabric mod made
 * flammable whose material isn't, as stone's, never burned. {@code BlockFabricFlammableMixin} takes such a block for
 * flammable by its odds alone, as vanilla's fire does.
 */
public final class FabricFlammableBlocks {
	/** Fabric API's content registries module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-content-registries-v0");

	private FabricFlammableBlocks() {
	}

	/** Whether Fabric's registry has an entry of its own for the block, for vanilla's fire. */
	public static boolean registered(Block block) {
		return ENABLED && Upstream.registered(block);
	}

	/** Everything that names Fabric's content registries module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean registered(Block block) {
			return FlammableBlockRegistryImpl.getInstance(Blocks.FIRE).getFabric(block) != null;
		}
	}
}
