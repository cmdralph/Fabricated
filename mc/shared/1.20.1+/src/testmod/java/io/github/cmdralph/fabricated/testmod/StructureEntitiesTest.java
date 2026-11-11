package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * STRUCT-1: a structure template with an entity, placed as the server starts, which {@code StructureEntitiesMixin}'s
 * hooks at vanilla's private {@code placeEntities} see, as YUNG's API's and Friends and Foes' do: one at its call that
 * keeps the settings in a {@code ThreadLocal}, one at its start taking its parameters, which cancels the placement, one
 * after its call. The platforms place entities in a method of their own; the hooks must run there, in that order,
 * with vanilla's parameters. The template's block is air, which the settings' processor leaves out.
 */
public final class StructureEntitiesTest {
	private static volatile StructurePlaceSettings placing;
	private static final List<String> steps = new ArrayList<>();

	private StructureEntitiesTest() {
	}

	/** The settings of the test's placement while it runs, else null: the hooks act on that placement alone. */
	public static StructurePlaceSettings placing() {
		return placing;
	}

	/** A hook's step, as it ran. */
	public static void step(String step) {
		steps.add(step);
	}

	static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		StructureTemplate template = new StructureTemplate();
		template.load(level.holderLookup(Registries.BLOCK), templateTag());
		BlockPos at = new BlockPos(0, 200, 0);
		StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.FRONT_BACK).setRotation(Rotation.CLOCKWISE_90)
				.setRotationPivot(new BlockPos(1, 0, 1)).setBoundingBox(new BoundingBox(-8, 190, -8, 8, 210, 8)).setFinalizeEntities(true)
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		steps.clear();
		placing = settings;
		try {
			template.placeInWorld(level, at, at, settings, level.getRandom(), 2);
		} finally {
			placing = null;
		}
		List<String> expected = List.of("before the call", "start, with vanilla's parameters", "after the call");
		if (steps.equals(expected)) TestMod.LOGGER.info("FABRICATED-TEST structure entity hooks OK");
		else TestMod.LOGGER.error("FABRICATED-TEST structure entity hooks FAILED: {} where {} was expected", steps, expected);
	}

	/** A one-block template of air with an armor stand on it, as a structure file has it. */
	private static CompoundTag templateTag() {
		CompoundTag tag = new CompoundTag();
		tag.put("size", ints(1, 1, 1));
		CompoundTag air = new CompoundTag();
		air.putString("Name", "minecraft:air");
		ListTag palette = new ListTag();
		palette.add(air);
		tag.put("palette", palette);
		CompoundTag block = new CompoundTag();
		block.put("pos", ints(0, 0, 0));
		block.putInt("state", 0);
		ListTag blocks = new ListTag();
		blocks.add(block);
		tag.put("blocks", blocks);
		CompoundTag nbt = new CompoundTag();
		nbt.putString("id", "minecraft:armor_stand");
		CompoundTag entity = new CompoundTag();
		ListTag pos = new ListTag();
		for (double d : new double[] {0.5, 0, 0.5}) pos.add(DoubleTag.valueOf(d));
		entity.put("pos", pos);
		entity.put("blockPos", ints(0, 0, 0));
		entity.put("nbt", nbt);
		ListTag entities = new ListTag();
		entities.add(entity);
		tag.put("entities", entities);
		return tag;
	}

	private static ListTag ints(int... values) {
		ListTag list = new ListTag();
		for (int v : values) list.add(IntTag.valueOf(v));
		return list;
	}
}
