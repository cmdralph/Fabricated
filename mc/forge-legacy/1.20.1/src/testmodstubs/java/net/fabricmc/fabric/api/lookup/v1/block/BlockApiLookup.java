package net.fabricmc.fabric.api.lookup.v1.block;

import net.minecraft.class_1937;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2680;

public interface BlockApiLookup<A, C> {
	default A find(class_1937 world, class_2338 pos, C context) {
		throw new AssertionError("stand-in");
	}

	void registerForBlocks(BlockApiProvider<A, C> provider, class_2248... blocks);

	@FunctionalInterface
	interface BlockApiProvider<A, C> {
		A find(class_1937 world, class_2338 pos, class_2680 state, class_2586 blockEntity, C context);
	}
}
