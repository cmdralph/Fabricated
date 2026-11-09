package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for Fabric API's custom ingredients on Forge 47 (CORPUS-99 and NET-11, with Fabric
 * API). Forge 47 reads every ingredient's JSON itself before Fabric API's hook can, and failed each custom ingredient
 * (Create's mixing recipes name them); and Forge and Fabric API both write -1 before an ingredient of their own on the
 * network, which Fabric API's reader took for its own. Read as a recipe reads an ingredient ({@code fromJson}), a
 * {@code fabric:any} of dirt and sticks must be Fabric API's custom ingredient, matching both and not cobblestone, and
 * an array of it and stone Forge's compound ingredient (not a custom one), matching all three and not cobblestone. Each
 * must come back from the network as it went ({@code toNetwork} writes, {@code fromNetwork} reads), Forge's compound
 * holding Fabric API's ingredient included, and the buffer must be read to its end. Fabric API's
 * {@code getCustomIngredient}, which its mixin adds to the game's ingredient, is reached by reflection: the test mod
 * doesn't compile against the recipe module.
 */
public final class ForgeIngredientTest {
	private static final String ANY = "{\"fabric:type\": \"fabric:any\", \"ingredients\": [{\"item\": \"minecraft:dirt\"}, {\"item\": \"minecraft:stick\"}]}";

	private ForgeIngredientTest() {
	}

	/** At the first server tick, with Fabric API's recipe and networking modules. */
	public static void check() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-recipe-api-v1") || !FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) return;
		List<String> failed = new ArrayList<>();
		try {
			Method custom = Ingredient.class.getMethod("getCustomIngredient");
			JsonObject any = JsonParser.parseString(ANY).getAsJsonObject();
			JsonArray anyOrStone = new JsonArray();
			anyOrStone.add(any);
			anyOrStone.add(JsonParser.parseString("{\"item\": \"minecraft:stone\"}"));
			Ingredient dirtOrStick = Ingredient.fromJson(any, false);
			Ingredient compound = Ingredient.fromJson(anyOrStone, false);
			expect(failed, "the custom ingredient", dirtOrStick, custom, true, Items.DIRT, Items.STICK);
			expect(failed, "the array of it and stone", compound, custom, false, Items.DIRT, Items.STICK, Items.STONE);
			FriendlyByteBuf buf = PacketByteBufs.create();
			dirtOrStick.toNetwork(buf);
			compound.toNetwork(buf);
			expect(failed, "the custom ingredient from the network", Ingredient.fromNetwork(buf), custom, true, Items.DIRT, Items.STICK);
			expect(failed, "the array from the network", Ingredient.fromNetwork(buf), custom, false, Items.DIRT, Items.STICK, Items.STONE);
			if (buf.readableBytes() != 0) failed.add(buf.readableBytes() + " byte(s) left unread");
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed.add(e.toString());
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST Fabric API's custom ingredients OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST Fabric API's custom ingredients not as upstream's: {}", String.join("; ", failed));
		}
	}

	/** Adds what is wrong with an ingredient: whether it is a custom one, and that it matches the items and not cobblestone. */
	private static void expect(List<String> failed, String what, Ingredient ingredient, Method custom, boolean isCustom, Item... items)
			throws ReflectiveOperationException {
		if ((custom.invoke(ingredient) != null) != isCustom) failed.add(what + " is " + (isCustom ? "not " : "") + "a custom ingredient: " + ingredient);
		for (Item item : items) {
			if (!ingredient.test(new ItemStack(item))) failed.add(what + " doesn't match " + item);
		}
		if (ingredient.test(new ItemStack(Items.COBBLESTONE))) failed.add(what + " matches cobblestone");
	}
}
