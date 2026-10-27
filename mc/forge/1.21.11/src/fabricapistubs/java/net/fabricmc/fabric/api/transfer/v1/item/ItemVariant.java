package net.fabricmc.fabric.api.transfer.v1.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public interface ItemVariant extends TransferVariant<Item> {
	static ItemVariant blank() {
		throw new AssertionError("stand-in");
	}

	static ItemVariant of(ItemStack stack) {
		throw new AssertionError("stand-in");
	}

	static ItemVariant of(ItemLike item) {
		throw new AssertionError("stand-in");
	}

	default boolean matches(ItemStack stack) {
		throw new AssertionError("stand-in");
	}

	default Item getItem() {
		throw new AssertionError("stand-in");
	}

	default ItemStack toStack() {
		throw new AssertionError("stand-in");
	}

	default ItemStack toStack(int count) {
		throw new AssertionError("stand-in");
	}
}
