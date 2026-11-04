package io.github.cmdralph.fabricated.forgetest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1856;
import net.minecraft.class_2540;

/**
 * The forge-legacy test mod's part for Fabric API's custom ingredients on Forge 47 (CORPUS-99 and NET-11, with Fabric
 * API). Forge 47 reads every ingredient's JSON itself before Fabric API's hook can, and failed each custom ingredient
 * (Create's mixing recipes name them); and Forge and Fabric API both write -1 before an ingredient of their own on the
 * network, which Fabric API's reader took for its own. Read as a recipe reads an ingredient ({@code method_8102}), a
 * {@code fabric:any} of dirt and sticks must be Fabric API's custom ingredient, matching both and not cobblestone, and
 * an array of it and stone Forge's compound ingredient (not a custom one), matching all three and not cobblestone. Each
 * must come back from the network as it went ({@code method_8088} writes, {@code method_8086} reads), Forge's compound
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
			Method custom = class_1856.class.getMethod("getCustomIngredient");
			JsonObject any = JsonParser.parseString(ANY).getAsJsonObject();
			JsonArray anyOrStone = new JsonArray();
			anyOrStone.add(any);
			anyOrStone.add(JsonParser.parseString("{\"item\": \"minecraft:stone\"}"));
			class_1856 dirtOrStick = class_1856.method_8102(any, false);
			class_1856 compound = class_1856.method_8102(anyOrStone, false);
			expect(failed, "the custom ingredient", dirtOrStick, custom, true, class_1802.field_8831, class_1802.field_8600);
			expect(failed, "the array of it and stone", compound, custom, false, class_1802.field_8831, class_1802.field_8600, class_1802.field_20391);
			class_2540 buf = PacketByteBufs.create();
			dirtOrStick.method_8088(buf);
			compound.method_8088(buf);
			expect(failed, "the custom ingredient from the network", class_1856.method_8086(buf), custom, true, class_1802.field_8831, class_1802.field_8600);
			expect(failed, "the array from the network", class_1856.method_8086(buf), custom, false, class_1802.field_8831, class_1802.field_8600, class_1802.field_20391);
			if (buf.readableBytes() != 0) failed.add(buf.readableBytes() + " byte(s) left unread");
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed.add(e.toString());
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST Fabric API's custom ingredients OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST Fabric API's custom ingredients not as upstream's: {}", String.join("; ", failed));
		}
	}

	/** Adds what is wrong with an ingredient: whether it is a custom one, and that it matches the items and not cobblestone. */
	private static void expect(List<String> failed, String what, class_1856 ingredient, Method custom, boolean isCustom, class_1792... items)
			throws ReflectiveOperationException {
		if ((custom.invoke(ingredient) != null) != isCustom) failed.add(what + " is " + (isCustom ? "not " : "") + "a custom ingredient: " + ingredient);
		for (class_1792 item : items) {
			if (!ingredient.test(new class_1799(item))) failed.add(what + " doesn't match " + item);
		}
		if (ingredient.test(new class_1799(class_1802.field_20412))) failed.add(what + " matches cobblestone");
	}
}
