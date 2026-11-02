package net.fabricmc.fabric.api.client.model.loading.v1;

import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** Compile-time stand-in: the members of Fabric API's 1.21.11 interfaces that the test mod calls, in Mojang's names. */
public final class ModelModifier {
	/** The phase after every other wrapper's (Fabric's {@code fabric:wrap_last}). */
	public static final Identifier WRAP_LAST_PHASE = Identifier.fromNamespaceAndPath("fabric", "wrap_last");

	private ModelModifier() {
	}

	@FunctionalInterface
	public interface AfterBakeBlock {
		BlockStateModel modifyModelAfterBake(BlockStateModel model, Context context);

		interface Context {
			/** The block state whose model this is. */
			BlockState state();
		}
	}

	@FunctionalInterface
	public interface AfterBakeItem {
		ItemModel modifyModelAfterBake(ItemModel model, Context context);

		interface Context {
			/** The id of the item whose model this is. */
			Identifier itemId();
		}
	}
}
