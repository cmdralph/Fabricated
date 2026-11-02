package net.fabricmc.fabric.api.client.model.loading.v1;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.Identifier;

/** Compile-time stand-in: the members of Fabric API's 1.21.11 interface that the test mod calls, in Mojang's names. */
public interface CustomUnbakedBlockStateModel extends BlockStateModel.Unbaked {
	static void register(Identifier id, MapCodec<? extends CustomUnbakedBlockStateModel> codec) {
		throw new AssertionError("stub");
	}

	MapCodec<? extends CustomUnbakedBlockStateModel> codec();
}
