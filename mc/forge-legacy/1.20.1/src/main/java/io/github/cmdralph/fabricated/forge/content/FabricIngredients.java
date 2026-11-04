package io.github.cmdralph.fabricated.forge.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * Fabric API's custom ingredients where Forge 47 reads an ingredient (CORPUS-99, NET-11).
 * <ul>
 * <li>In JSON: Forge's patch of {@code Ingredient.fromJson} hands every ingredient to its own
 * {@code CraftingHelper.getIngredient} before vanilla's code, where Fabric API's hook reads a custom ingredient's
 * {@code fabric:type}. Forge read such an object as an item ingredient, and Fabric API's hook of that read refused it
 * ("Custom ingredient cannot be used inside an array ingredient"), so each recipe naming one failed, Create's mixing
 * recipes among them. {@code CraftingHelperFabricIngredientsMixin} asks here first: an object with a {@code fabric:type}
 * is that custom ingredient, read by its serializer as Fabric API's hook reads it, and a type Fabric API has no
 * serializer of fails as there. Inside an array it becomes part of Forge's compound ingredient, as Forge's own custom
 * ingredients do.</li>
 * <li>On the network: Forge and Fabric API both write -1 and then their type's id before an ingredient of their own.
 * Fabric API's hook of {@code Ingredient.fromNetwork} takes every -1 for its own and refused Forge's types (its
 * compound ingredient, which an array holding a custom ingredient becomes, among them). {@code IngredientForgeNetworkMixin}
 * asks here before it: a type Fabric API has no serializer of is Forge's, read by Forge's {@code CraftingHelper}; any
 * other ingredient is left to Fabric API's hook and Forge's code, the buffer as it was.</li>
 * </ul>
 */
public final class FabricIngredients {
	/** Fabric API's recipe module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-recipe-api-v1");
	/** The key of a custom ingredient's type in its JSON object (Fabric API's {@code CustomIngredientImpl.TYPE_KEY}). */
	private static final String TYPE_KEY = "fabric:type";
	/** What Forge and Fabric API both write first on the network for an ingredient of their own, its type's id next. */
	private static final int CUSTOM_MARKER = -1;

	private FabricIngredients() {
	}

	/** Fabric API's custom ingredient a JSON element describes, or null where it describes none. */
	public static Ingredient fromJson(JsonElement json) {
		if (!ENABLED || json == null || !json.isJsonObject()) return null;
		JsonObject object = json.getAsJsonObject();
		if (!object.has(TYPE_KEY)) return null;
		ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(object, TYPE_KEY));
		Ingredient ingredient = Upstream.read(type, object);
		if (ingredient == null) throw new IllegalArgumentException("Unknown custom ingredient type: " + type);
		return ingredient;
	}

	/**
	 * Forge's custom ingredient the buffer holds next, read; or null, the buffer as it was, where it holds another
	 * (Fabric API's custom ingredients and vanilla's are left to their readers).
	 */
	public static Ingredient forgeFromNetwork(FriendlyByteBuf buf) {
		if (!ENABLED) return null;
		int start = buf.readerIndex();
		if (buf.readVarInt() == CUSTOM_MARKER) {
			ResourceLocation type = buf.readResourceLocation();
			if (!Upstream.knows(type)) return CraftingHelper.getIngredient(type, buf);
		}
		buf.readerIndex(start);
		return null;
	}

	/** Everything that names Fabric API's recipe module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static Ingredient read(ResourceLocation type, JsonObject json) {
			CustomIngredientSerializer<?> serializer = CustomIngredientSerializer.get(type);
			return serializer == null ? null : serializer.read(json).toVanilla();
		}

		static boolean knows(ResourceLocation type) {
			return CustomIngredientSerializer.get(type) != null;
		}
	}
}
