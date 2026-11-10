package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.cmdralph.fabricated.forgetest.mixin.PoiTypesAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The forge-legacy test mod's part for a Fabric mod writing {@code PoiTypes.TYPE_BY_STATE} as vanilla's, a holder for
 * each state, where Forge 47 keeps the types (POI-4): a barrel's state put with the fisherman's holder, as BCLib puts
 * the states of its workstation tags, must be the fisherman's to {@code PoiTypes.forState}, and Forge's map must hold
 * the type.
 */
public final class ForgePoiHolderTest {
	private ForgePoiHolderTest() {
	}

	/** At the first server tick. */
	public static void check() {
		Holder<PoiType> fisherman = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.FISHERMAN);
		BlockState barrel = Blocks.BARREL.defaultBlockState();
		Map<BlockState, Holder<PoiType>> byState = PoiTypesAccessor.fabricatedtest$typeByState();
		byState.put(barrel, fisherman);
		List<String> failed = new ArrayList<>();
		Object stored = ((Map<?, ?>) byState).get(barrel);
		if (!(stored instanceof PoiType)) failed.add("the map holds " + stored);
		Optional<Holder<PoiType>> found = PoiTypes.forState(barrel);
		if (found.isEmpty() || found.get().value() != fisherman.value()) failed.add("forState finds " + found);
		if (failed.isEmpty()) ForgeTestMod.LOGGER.info("FABRICATED-TEST poi holder OK");
		else ForgeTestMod.LOGGER.error("FABRICATED-TEST poi holder FAILED: {}", String.join("; ", failed));
	}
}
