package io.github.cmdralph.fabricated.forgetest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.ingredients.CompoundIngredient;

/**
 * The forge test mod's part for ingredients on the network, as Fabric API's recipe sync writes a recipe's slots:
 * through vanilla's {@code Ingredient.CONTENTS_STREAM_CODEC} and {@code Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC}.
 * <ul>
 * <li>An empty slot (CORPUS-83). Forge's patch threw writing the empty one. It must go as vanilla's empty item list
 * and come back empty, and an item's and a tag's ingredients must come back as they went, Forge's codec reading
 * them.</li>
 * <li>A Fabric mod's own ingredient, a subclass without Forge's {@code serializer()} (CORPUS-85). Forge's patch threw
 * writing it. It must come back as the items it matches, alone, in a slot, and inside a Forge custom ingredient, and
 * that Forge ingredient in a slot must come back too.</li>
 * <li>With Fabric API's recipe module, one of its custom ingredients, which Forge's patch threw writing as well. It
 * must come back as that custom ingredient, as Fabric API sends it.</li>
 * </ul>
 */
public final class ForgeIngredientCodecTest {
	private ForgeIngredientCodecTest() {
	}

	/** At the first server tick, the tags bound. */
	public static void check(MinecraftServer server) {
		checkEmptySlots(server);
		checkFabricIngredients(server);
		if (FabricLoader.getInstance().isModLoaded("fabric-recipe-api-v1")) checkFabricApiIngredients(server);
	}

	private static void checkEmptySlots(MinecraftServer server) {
		List<String> failed = roundTrips(server, buf -> {
			List<String> found = new ArrayList<>();
			StreamCodec<RegistryFriendlyByteBuf, List<Optional<Ingredient>>> slots = Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list());
			Ingredient stone = Ingredient.of(Items.STONE);
			Ingredient planks = Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.PLANKS));
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buf, Optional.empty());
			int empty = buf.readableBytes();
			if (empty != 1 || buf.getByte(0) != 1) found.add("an empty slot is written as " + empty + " byte(s), not vanilla's empty list");
			slots.encode(buf, List.of(Optional.of(stone), Optional.empty(), Optional.of(planks)));
			if (Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buf).isPresent()) found.add("an empty slot is read back as an ingredient");
			List<Optional<Ingredient>> read = slots.decode(buf);
			if (read.size() != 3) {
				found.add("three slots are read back as " + read.size());
			} else {
				if (!read.get(0).map(i -> i.test(new ItemStack(Items.STONE)) && !i.test(new ItemStack(Items.DIRT))).orElse(false)) found.add("stone's slot is read back as " + read.get(0));
				if (read.get(1).isPresent()) found.add("the empty slot is read back as " + read.get(1));
				if (!read.get(2).map(i -> i.test(new ItemStack(Items.OAK_PLANKS)) && !i.test(new ItemStack(Items.STONE))).orElse(false)) found.add("the planks tag's slot is read back as " + read.get(2));
			}
			return found;
		});
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST optional ingredients on the network OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST optional ingredients on the network not as vanilla's: {}", String.join("; ", failed));
		}
	}

	private static void checkFabricIngredients(MinecraftServer server) {
		List<String> failed = roundTrips(server, buf -> {
			List<String> found = new ArrayList<>();
			Ingredient own = new ForgeTestIngredient();
			Ingredient.CONTENTS_STREAM_CODEC.encode(buf, own);
			Ingredient read = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
			if (!matchesOnly(read, Items.STONE)) found.add("the mod's ingredient is read back as " + read);
			Ingredient compound = CompoundIngredient.of(Ingredient.of(Items.DIRT), own);
			StreamCodec<RegistryFriendlyByteBuf, List<Optional<Ingredient>>> slots = Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list());
			slots.encode(buf, List.of(Optional.of(own), Optional.empty(), Optional.of(compound)));
			List<Optional<Ingredient>> slotsRead = slots.decode(buf);
			if (slotsRead.size() != 3) {
				found.add("three slots are read back as " + slotsRead.size());
			} else {
				if (!slotsRead.get(0).map(i -> matchesOnly(i, Items.STONE)).orElse(false)) found.add("the mod's ingredient's slot is read back as " + slotsRead.get(0));
				if (slotsRead.get(1).isPresent()) found.add("the empty slot is read back as " + slotsRead.get(1));
				if (!slotsRead.get(2).map(i -> matchesOnly(i, Items.STONE, Items.DIRT)).orElse(false)) found.add("the slot of Forge's compound ingredient holding it is read back as " + slotsRead.get(2));
			}
			return found;
		});
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST Fabric mods' ingredients on the network OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST Fabric mods' ingredients on the network not as upstream's: {}", String.join("; ", failed));
		}
	}

	/**
	 * Fabric API's {@code DefaultCustomIngredients.any} and {@code FabricIngredient.getCustomIngredient}, by reflection:
	 * the test mod doesn't compile against the recipe module. Outside a packet's encoding Fabric API writes its custom
	 * form, which starts with the same -1 as Forge's.
	 */
	private static void checkFabricApiIngredients(MinecraftServer server) {
		List<String> failed = roundTrips(server, buf -> {
			List<String> found = new ArrayList<>();
			Method custom;
			Ingredient stoneOrDirt;
			try {
				Method any = Class.forName("net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients").getMethod("any", Ingredient[].class);
				custom = Ingredient.class.getMethod("getCustomIngredient");
				stoneOrDirt = (Ingredient) any.invoke(null, (Object) new Ingredient[] {Ingredient.of(Items.STONE), Ingredient.of(Items.DIRT)});
			} catch (ReflectiveOperationException e) {
				found.add("Fabric API's custom ingredients can't be made: " + e);
				return found;
			}
			Ingredient.CONTENTS_STREAM_CODEC.encode(buf, stoneOrDirt);
			Ingredient read = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
			if (!isCustom(custom, read) || !matchesOnly(read, Items.STONE, Items.DIRT)) found.add("a custom ingredient is read back as " + read);
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buf, Optional.of(stoneOrDirt));
			Optional<Ingredient> slot = Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buf);
			if (!slot.map(i -> isCustom(custom, i) && matchesOnly(i, Items.STONE, Items.DIRT)).orElse(false)) found.add("a custom ingredient's slot is read back as " + slot);
			return found;
		});
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST Fabric API's custom ingredients on the network OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST Fabric API's custom ingredients on the network not as upstream's: {}", String.join("; ", failed));
		}
	}

	/** What a check found wrong, writing into and reading from one buffer, which must be read to its end. */
	private static List<String> roundTrips(MinecraftServer server, Function<RegistryFriendlyByteBuf, List<String>> check) {
		List<String> failed = new ArrayList<>();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		try {
			failed.addAll(check.apply(buf));
			if (buf.readableBytes() != 0) failed.add(buf.readableBytes() + " byte(s) left unread");
		} catch (RuntimeException e) {
			failed.add(e.toString());
		} finally {
			buf.release();
		}
		return failed;
	}

	/** Whether the ingredient matches the items and not cobblestone, which none of the checks' ingredients hold. */
	private static boolean matchesOnly(Ingredient ingredient, Item... items) {
		for (Item item : items) {
			if (!ingredient.test(new ItemStack(item))) return false;
		}
		return !ingredient.test(new ItemStack(Items.COBBLESTONE));
	}

	private static boolean isCustom(Method custom, Ingredient ingredient) {
		try {
			return custom.invoke(ingredient) != null;
		} catch (IllegalAccessException | InvocationTargetException e) {
			return false;
		}
	}
}
