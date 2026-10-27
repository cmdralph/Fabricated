package net.fabricmc.fabric.api.transfer.v1.storage;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface TransferVariant<O> {
	boolean isBlank();

	O getObject();

	@Nullable
	CompoundTag getNbt();

	default boolean nbtMatches(@Nullable CompoundTag other) {
		throw new AssertionError("stand-in");
	}

	default boolean isOf(O object) {
		throw new AssertionError("stand-in");
	}

	@Nullable
	default CompoundTag copyNbt() {
		throw new AssertionError("stand-in");
	}
}
