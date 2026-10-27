package net.fabricmc.fabric.api.transfer.v1.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface ItemVariant extends TransferVariant<Item> {
	static ItemVariant blank() {
		throw new AssertionError("stand-in");
	}

	static ItemVariant of(ItemStack stack) {
		throw new AssertionError("stand-in");
	}

	static ItemVariant of(ItemLike item, @Nullable CompoundTag nbt) {
		throw new AssertionError("stand-in");
	}

	default boolean matches(ItemStack stack) {
		throw new AssertionError("stand-in");
	}

	default Item getItem() {
		throw new AssertionError("stand-in");
	}

	default ItemStack toStack(int count) {
		throw new AssertionError("stand-in");
	}
}
