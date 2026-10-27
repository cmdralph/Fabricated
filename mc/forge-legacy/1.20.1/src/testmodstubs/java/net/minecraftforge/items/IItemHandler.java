package net.minecraftforge.items;

import net.minecraft.class_1799;

/** {@code IItemHandler}. */
public interface IItemHandler {
	int getSlots();

	class_1799 getStackInSlot(int slot);

	class_1799 insertItem(int slot, class_1799 stack, boolean simulate);

	class_1799 extractItem(int slot, int amount, boolean simulate);
}
