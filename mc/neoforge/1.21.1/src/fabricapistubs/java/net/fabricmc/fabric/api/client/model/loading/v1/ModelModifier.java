package net.fabricmc.fabric.api.client.model.loading.v1;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's interfaces that Fabricated's test mods call, in Mojang's
 * names.
 */
public final class ModelModifier {
	/** The phase after every other wrapper's (Fabric's {@code fabric:wrap_last}). */
	public static final ResourceLocation WRAP_LAST_PHASE = ResourceLocation.fromNamespaceAndPath("fabric", "wrap_last");

	private ModelModifier() {
	}

	@FunctionalInterface
	public interface AfterBake {
		@Nullable BakedModel modifyModelAfterBake(@Nullable BakedModel model, Context context);

		interface Context {
			/** The model's id, for a model that isn't a top-level one (a block state's or an item's); else null. */
			@Nullable ResourceLocation resourceId();

			/** The block state's or item's model id, for a top-level model; else null. */
			@Nullable ModelResourceLocation topLevelId();
		}
	}
}
