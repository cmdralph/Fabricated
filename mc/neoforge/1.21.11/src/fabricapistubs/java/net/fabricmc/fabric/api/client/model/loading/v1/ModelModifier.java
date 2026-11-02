package net.fabricmc.fabric.api.client.model.loading.v1;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;

/** Compile-time stand-in: the members of Fabric API's 1.21.11 interfaces that the test mod calls, in Mojang's names. */
public final class ModelModifier {
	private ModelModifier() {
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
