package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** The smallest mob there is, for ObjectBuilderTest's entity type (never spawned in CI). */
public class TestMob extends PathfinderMob {
	public TestMob(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}
}
