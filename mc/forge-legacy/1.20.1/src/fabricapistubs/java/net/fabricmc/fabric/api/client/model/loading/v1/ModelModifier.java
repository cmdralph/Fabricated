package net.fabricmc.fabric.api.client.model.loading.v1;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time stand-in: the members of Fabric API 0.92's interfaces (1.20.1) that the renderer test mod calls, in
 * Mojang's names.
 */
public final class ModelModifier {
	private ModelModifier() {
	}

	@FunctionalInterface
	public interface AfterBake {
		@Nullable BakedModel modifyModelAfterBake(@Nullable BakedModel model, Context context);

		interface Context {
			/** The model's id: a {@code ModelResourceLocation} for a block state's or an item's model. */
			ResourceLocation id();
		}
	}
}
