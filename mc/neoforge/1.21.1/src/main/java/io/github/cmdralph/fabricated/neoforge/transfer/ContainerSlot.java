package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/**
 * A Fabric slot holding the stack a NeoForge item capability belongs to, for a Fabric storage of that item to act on
 * ({@link FabricItemFluidHandler}, {@link EnergyBridge}). Fabric's slot takes a snapshot by putting a copy of the stack
 * in its place, so a transaction never changes the stack it began with; once the outermost one commits, an item that
 * stayed the same is written into that stack, which stays the container.
 */
final class ContainerSlot extends SingleStackStorage {
	private ItemStack stack;
	/** The container when no transaction is open: the stack NeoForge's caller holds, unless it was replaced. */
	private ItemStack committed;

	ContainerSlot(ItemStack container) {
		stack = container;
		committed = container;
	}

	/** The container now: the stack it began with, or the different item that replaced it. */
	ItemStack stack() {
		return stack;
	}

	@Override
	protected ItemStack getStack() {
		return stack;
	}

	@Override
	protected void setStack(ItemStack stack) {
		this.stack = stack;
	}

	@Override
	protected void onFinalCommit() {
		if (stack != committed && !committed.isEmpty() && ItemStack.isSameItem(committed, stack)) {
			committed.applyComponents(patchTo(committed, stack));
			committed.setCount(stack.getCount());
			stack = committed;
		}
		committed = stack;
	}

	/** The patch that gives {@code into} the components of {@code from}, a stack of the same item. */
	private static DataComponentPatch patchTo(ItemStack into, ItemStack from) {
		Set<DataComponentType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
		addTypes(into.getComponentsPatch(), types);
		addTypes(from.getComponentsPatch(), types);
		DataComponentPatch.Builder patch = DataComponentPatch.builder();
		for (DataComponentType<?> type : types) copy(patch, type, from);
		return patch.build();
	}

	/** The components the patch sets or removes: the only ones where a stack of the item differs from its default. */
	private static void addTypes(DataComponentPatch patch, Set<DataComponentType<?>> types) {
		DataComponentPatch.SplitResult split = patch.split();
		types.addAll(split.added().keySet());
		types.addAll(split.removed());
	}

	private static <T> void copy(DataComponentPatch.Builder patch, DataComponentType<T> type, ItemStack from) {
		T value = from.get(type);
		if (value == null) {
			patch.remove(type);
		} else {
			patch.set(type, value);
		}
	}
}
