package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code PoiTypes.TYPE_BY_STATE} typed as vanilla's, a holder for each state, as BCLib writes it, where Forge 47 keeps
 * the types. For {@code ForgePoiHolderTest}.
 */
@Mixin(PoiTypes.class)
public interface PoiTypesAccessor {
	@Accessor("TYPE_BY_STATE")
	static Map<BlockState, Holder<PoiType>> fabricatedtest$typeByState() {
		throw new AssertionError("mixin");
	}
}
