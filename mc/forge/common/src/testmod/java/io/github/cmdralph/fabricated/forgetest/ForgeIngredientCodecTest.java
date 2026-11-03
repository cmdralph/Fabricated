package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The forge test mod's part for optional ingredients on the network (CORPUS-83): a shaped recipe's slots as Fabric
 * API's recipe sync writes them, through vanilla's {@code Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC}, an empty slot
 * among them. Forge's patch threw writing the empty one; it must go as vanilla's empty item list and come back empty,
 * and an item's and a tag's ingredients must come back as they went, Forge's codec reading them.
 */
public final class ForgeIngredientCodecTest {
	private ForgeIngredientCodecTest() {
	}

	/** At the first server tick, the tags bound. */
	public static void check(MinecraftServer server) {
		List<String> failed = new ArrayList<>();
		StreamCodec<RegistryFriendlyByteBuf, List<Optional<Ingredient>>> slots = Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list());
		Ingredient stone = Ingredient.of(Items.STONE);
		Ingredient planks = Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.PLANKS));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		try {
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buf, Optional.empty());
			int empty = buf.readableBytes();
			if (empty != 1 || buf.getByte(0) != 1) failed.add("an empty slot is written as " + empty + " byte(s), not vanilla's empty list");
			slots.encode(buf, List.of(Optional.of(stone), Optional.empty(), Optional.of(planks)));
			if (Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buf).isPresent()) failed.add("an empty slot is read back as an ingredient");
			List<Optional<Ingredient>> read = slots.decode(buf);
			if (read.size() != 3) {
				failed.add("three slots are read back as " + read.size());
			} else {
				if (!read.get(0).map(i -> i.test(new ItemStack(Items.STONE)) && !i.test(new ItemStack(Items.DIRT))).orElse(false)) failed.add("stone's slot is read back as " + read.get(0));
				if (read.get(1).isPresent()) failed.add("the empty slot is read back as " + read.get(1));
				if (!read.get(2).map(i -> i.test(new ItemStack(Items.OAK_PLANKS)) && !i.test(new ItemStack(Items.STONE))).orElse(false)) failed.add("the planks tag's slot is read back as " + read.get(2));
			}
			if (buf.readableBytes() != 0) failed.add(buf.readableBytes() + " byte(s) left unread");
		} catch (RuntimeException e) {
			failed.add(e.toString());
		} finally {
			buf.release();
		}
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST optional ingredients on the network OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST optional ingredients on the network not as vanilla's: {}", String.join("; ", failed));
		}
	}
}
