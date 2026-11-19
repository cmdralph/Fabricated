package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.testutil.TestSources;

/**
 * SEAM-12: vanilla's farmland and moisture checks in a crop's growth speed ({@code CropBlock.getGrowthSpeed}), which the
 * platforms make through the soil state's {@code canSustainPlant} and {@code isFertile}, put back where vanilla's answer
 * is the platform's: on NeoForge 21.1 and 21.11 for the soils keeping both defaults that are vanilla's farmland or no
 * farm block (NeoForge counts every farm block as farmland); on Forge for the crops that are a {@code CropBlock} keeping
 * the checks Forge's {@code canSustainPlant} asks of them ({@code mayPlaceOn}, {@code getPlant}, {@code getPlantType}) and
 * the soils keeping it, from Forge 52 too where the soil is vanilla's farmland or no farm block, and on Forge 64 to 66 where
 * it is no farmland block or is tagged {@code #grows_crops}; the moisture check, on each, for vanilla's farmland keeping
 * {@code isFertile}. NeoForge 26.x makes vanilla's farmland check in its default's branch already. Each fixture is the
 * platform's {@code getGrowthSpeed} in its form (NeoForge's 21.x and 26.x, Forge 47's under SRG's names, 52's and 64's)
 * against stand-ins whose defaults are the platform's. The class rewritten answers what the platform's did for every
 * crop and every soil around it; hooks of vanilla's calls (Balm's of the farmland check, its 26.x wrapper of the moisture
 * check) run for the soils whose checks go back, and only for them.
 */
public class CropGrowthTest {
	private static final String CROP = "net/minecraft/world/level/block/CropBlock";

	/** A platform's form: its extensions, the era of its game (the farm block's name, its checks) and the names it runs with. */
	enum Form {
		NEOFORGE_21(false, false, false, false), NEOFORGE_1211(false, false, false, false), NEOFORGE_26(false, false, true, false), FORGE_47(true, true, false, false),
		FORGE_52(true, false, false, true), FORGE_64(true, false, true, true);

		final boolean forge, srg, java25, farmCrops;

		/** @param farmCrops whether Forge's CropBlock takes any farm block ({@code mayPlaceOn}: 52 and 61's FarmBlock, 64 to 66's FarmlandBlock) */
		Form(boolean forge, boolean srg, boolean java25, boolean farmCrops) {
			this.forge = forge;
			this.srg = srg;
			this.java25 = java25;
			this.farmCrops = farmCrops;
		}

		String ext() {
			return forge ? "net.minecraftforge.common.extensions" : "net.neoforged.neoforge.common.extensions";
		}

		String farm() {
			return java25 ? "FarmlandBlock" : "FarmBlock";
		}

		/** NeoForge's canSustainPlant's answer: its own TriState on 21.1, Minecraft's from 21.11. */
		String triState() {
			return this == NEOFORGE_21 ? "net.neoforged.neoforge.common.util.TriState" : "net.minecraft.util.TriState";
		}
	}

	/** The members Forge 47's game names by SRG's names, as {@code $name$} in the fixtures. */
	private static final Map<String, String> SRG = new LinkedHashMap<>();
	static {
		for (String[] names : new String[][] {{"getGrowthSpeed", "m_52272_"}, {"getBlock", "m_60734_"}, {"is", "m_60713_"}, {"getValue", "m_61143_"},
				{"FARMLAND", "f_50093_"}, {"MOISTURE", "f_53243_"}, {"mayPlaceOn", "m_6266_"}}) {
			SRG.put("$" + names[0] + "$", names[1]);
		}
	}

	static Map<String, String> sources(Form form) {
		Map<String, String> sources = new HashMap<>();
		String ext = form.ext(), farm = form.farm();
		put(sources, "net.minecraft.core.BlockPos", """
				public record BlockPos(int x, int y, int z) {
					public BlockPos offset(int dx, int dy, int dz) { return new BlockPos(x + dx, y + dy, z + dz); }
					public BlockPos below() { return offset(0, -1, 0); }
					public BlockPos above() { return offset(0, 1, 0); }
					public BlockPos north() { return offset(0, 0, -1); }
					public BlockPos south() { return offset(0, 0, 1); }
					public BlockPos west() { return offset(-1, 0, 0); }
					public BlockPos east() { return offset(1, 0, 0); }
				}""");
		put(sources, "net.minecraft.core.Direction", "public enum Direction { UP, DOWN }");
		put(sources, "net.minecraft.tags.TagKey", "public record TagKey<T>(String name) { }");
		put(sources, "net.minecraft.tags.BlockTags", "public class BlockTags { public static final TagKey<net.minecraft.world.level.block.Block> GROWS_CROPS"
				+ " = new TagKey<>(\"grows_crops\"); }");
		put(sources, "net.minecraft.world.level.block.state.properties.Property", "public class Property<T extends Comparable<T>> { }");
		put(sources, "net.minecraft.world.level.block.state.properties.IntegerProperty", "public class IntegerProperty extends Property<Integer> { }");
		put(sources, "net.minecraft.world.level.BlockGetter", "public interface BlockGetter { net.minecraft.world.level.block.state.BlockState"
				+ " getBlockState(net.minecraft.core.BlockPos pos); }");
		put(sources, "com.example.Level", """
				public class Level implements net.minecraft.world.level.BlockGetter {
					final java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new java.util.HashMap<>();
					public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
						return blocks.getOrDefault(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
					public void set(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { blocks.put(pos, state); }
				}""");
		// The state: its block, its moisture where it has one, and its tags.
		put(sources, "net.minecraft.world.level.block.state.BlockState", """
				public class BlockState implements %s.%s {
					final net.minecraft.world.level.block.Block block;
					final Integer moisture;
					public BlockState(net.minecraft.world.level.block.Block block, Integer moisture) { this.block = block; this.moisture = moisture; }
					public net.minecraft.world.level.block.Block $getBlock$() { return block; }
					public boolean $is$(net.minecraft.world.level.block.Block other) { return block == other; }
					public boolean is(net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) { return net.minecraft.world.level.block.Blocks.TAGGED.contains(block); }
					@SuppressWarnings("unchecked")
					public <T extends Comparable<T>> T $getValue$(net.minecraft.world.level.block.state.properties.Property<T> property) {
						if (moisture == null) throw new IllegalArgumentException("no moisture: " + block);
						return (T) moisture;
					}
					@SuppressWarnings("unchecked")
					public <T extends Comparable<T>> T getValueOrElse(net.minecraft.world.level.block.state.properties.Property<T> property, T fallback) {
						return moisture == null ? fallback : (T) moisture;
					}
					public String toString() { return block + (moisture == null ? "" : "[" + moisture + "]"); }
				}""".formatted(ext, form.forge ? "IForgeBlockState" : "IBlockStateExtension"));
		String farmCheck = form.forge && !form.java25 && !form.farmCrops ? "state.$is$(Blocks.$FARMLAND$)" : "state.$getBlock$() instanceof " + farm;
		if (form.forge) {
			put(sources, "net.minecraftforge.common.PlantType", "public enum PlantType { CROP, PLAINS }");
			put(sources, "net.minecraftforge.common.IPlantable", """
					public interface IPlantable {
						default PlantType getPlantType(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
							return this instanceof net.minecraft.world.level.block.CropBlock ? PlantType.CROP : PlantType.PLAINS;
						}
						net.minecraft.world.level.block.state.BlockState getPlant(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos);
					}""");
			put(sources, ext + ".IForgeBlockState", """
					import net.minecraft.world.level.block.state.BlockState;
					public interface IForgeBlockState {
						default boolean canSustainPlant(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing,
								net.minecraftforge.common.IPlantable plantable) {
							return ((BlockState) this).$getBlock$().canSustainPlant((BlockState) this, level, pos, facing, plantable);
						}
						default boolean isFertile(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
							return ((BlockState) this).$getBlock$().isFertile((BlockState) this, level, pos);
						}
					}""");
			// Forge's isFertile default: 47 and 52 ask for vanilla's farmland, 61 any FarmBlock, 64 to 66 any FarmlandBlock.
			String fertile = form.srg || !form.java25 ? "state.$is$(net.minecraft.world.level.block.Blocks.$FARMLAND$)"
					: "state.$getBlock$() instanceof net.minecraft.world.level.block." + farm;
			put(sources, ext + ".IForgeBlock", """
					import net.minecraft.world.level.block.state.BlockState;
					public interface IForgeBlock {
						boolean canSustainPlant(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
								net.minecraft.core.Direction facing, net.minecraftforge.common.IPlantable plantable);
						default boolean isFertile(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
							if (%s) return state.$getValue$(net.minecraft.world.level.block.%s.$MOISTURE$) > 0;
							return false;
						}
					}""".formatted(fertile, farm));
			// Block's canSustainPlant, as Forge patches it: a bush's mayPlaceOn, then by the plant's type.
			String crops = form.java25 ? "state.is(net.minecraft.tags.BlockTags.GROWS_CROPS)" : "state.$is$(Blocks.$FARMLAND$)";
			put(sources, "net.minecraft.world.level.block.Block", """
					import net.minecraft.world.level.block.state.BlockState;
					public class Block implements %s.IForgeBlock {
						final String name;
						public Block(String name) { this.name = name; }
						public BlockState defaultBlockState() { return new BlockState(this, null); }
						public boolean canSustainPlant(BlockState state, net.minecraft.world.level.BlockGetter world, net.minecraft.core.BlockPos pos,
								net.minecraft.core.Direction facing, net.minecraftforge.common.IPlantable plantable) {
							BlockState plant = plantable.getPlant(world, pos.above());
							net.minecraftforge.common.PlantType type = plantable.getPlantType(world, pos.above());
							if (plant.$getBlock$() == Blocks.CACTUS) return state.$is$(Blocks.CACTUS);
							if (plantable instanceof BushBlock bush && bush.$mayPlaceOn$(state, world, pos)) return true;
							if (net.minecraftforge.common.PlantType.CROP.equals(type)) return %s;
							if (net.minecraftforge.common.PlantType.PLAINS.equals(type)) return state.$is$(Blocks.DIRT) || this == Blocks.$FARMLAND$;
							return false;
						}
						public String toString() { return name; }
					}""".formatted(ext, crops));
			put(sources, "net.minecraft.world.level.block.BushBlock", """
					import net.minecraft.world.level.block.state.BlockState;
					public class BushBlock extends Block implements net.minecraftforge.common.IPlantable {
						public BushBlock(String name) { super(name); }
						protected boolean $mayPlaceOn$(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
							return state.$is$(Blocks.DIRT) || state.$is$(Blocks.$FARMLAND$);
						}
						public BlockState getPlant(net.minecraft.world.level.BlockGetter world, net.minecraft.core.BlockPos pos) {
							BlockState state = world.getBlockState(pos);
							return state.$getBlock$() == this ? state : defaultBlockState();
						}
					}""");
		} else {
			put(sources, form.triState(), """
					public enum TriState {
						TRUE, DEFAULT, FALSE;
						public boolean isTrue() { return this == TRUE; }
						public boolean isDefault() { return this == DEFAULT; }
					}""");
			put(sources, ext + ".IBlockStateExtension", """
					import net.minecraft.world.level.block.state.BlockState;
					public interface IBlockStateExtension {
						default net.neoforged.neoforge.common.util.TriState canSustainPlant(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
								net.minecraft.core.Direction facing, BlockState plant) {
							return ((BlockState) this).getBlock().canSustainPlant((BlockState) this, level, pos, facing, plant);
						}
						default boolean isFertile(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
							return ((BlockState) this).getBlock().isFertile((BlockState) this, level, pos);
						}
					}""");
			put(sources, ext + ".IBlockExtension", """
					import net.minecraft.world.level.block.state.BlockState;
					public interface IBlockExtension {
						default net.neoforged.neoforge.common.util.TriState canSustainPlant(BlockState state, net.minecraft.world.level.BlockGetter level,
								net.minecraft.core.BlockPos soilPosition, net.minecraft.core.Direction facing, BlockState plant) {
							return net.neoforged.neoforge.common.util.TriState.DEFAULT;
						}
						default boolean isFertile(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
							if (state.getBlock() instanceof net.minecraft.world.level.block.%s) return state.getValue(net.minecraft.world.level.block.%s.MOISTURE) > 0;
							return false;
						}
					}""".formatted(farm, farm));
			put(sources, "net.minecraft.world.level.block.Block", """
					public class Block implements %s.IBlockExtension {
						final String name;
						public Block(String name) { this.name = name; }
						public net.minecraft.world.level.block.state.BlockState defaultBlockState() { return new net.minecraft.world.level.block.state.BlockState(this, null); }
						public String toString() { return name; }
					}""".formatted(ext));
			put(sources, "net.minecraft.world.level.block.BushBlock", "public class BushBlock extends Block { public BushBlock(String name) { super(name); } }");
		}
		put(sources, "net.minecraft.world.level.block." + farm, """
				public class %s extends Block {
					public static final net.minecraft.world.level.block.state.properties.IntegerProperty $MOISTURE$ = new net.minecraft.world.level.block.state.properties.IntegerProperty();
					public %s(String name) { super(name); }
				}""".formatted(farm, farm));
		put(sources, "net.minecraft.world.level.block.Blocks", """
				public class Blocks {
					public static final Block AIR = new Block("air"), DIRT = new Block("dirt"), STONE = new Block("stone"), CACTUS = new Block("cactus");
					public static final Block $FARMLAND$ = new %s("farmland");
					public static final Block OWN_FARM = new com.example.OwnFarm(), OVERRIDING = new com.example.OverridingSoil(), TAGGED_DIRT = new Block("tagged dirt");
					public static final Block WHEAT = new CropBlock("wheat"), PICKY = new com.example.PickyCrop(), STEM = new BushBlock("stem");
					public static final Block PLAINS_CROP = new com.example.PlainsCrop(), CACTUS_PLANT = new com.example.CactusPlant();
					public static final java.util.Set<Block> TAGGED = java.util.Set.of($FARMLAND$, TAGGED_DIRT);
				}""".formatted(farm));
		// The platform's crop: its growth speed, the soil checks in its form, each soil's farmland decision and fertility.
		String decide, plant, method;
		if (form.forge) {
			method = "Block type";
			decide = "soil.canSustainPlant(level, below.offset(x, 0, z), net.minecraft.core.Direction.UP, (net.minecraftforge.common.IPlantable) type)";
			plant = "";
		} else {
			method = "BlockState cropState";
			plant = "Block type = cropState.getBlock();";
			String vanilla = form.java25 ? "soil.is(net.minecraft.tags.BlockTags.GROWS_CROPS)" : "soil.getBlock() instanceof " + farm;
			decide = "decision.isDefault() ? " + vanilla + " : decision.isTrue()";
		}
		put(sources, "net.minecraft.world.level.block.CropBlock", """
				import net.minecraft.core.BlockPos;
				import net.minecraft.world.level.BlockGetter;
				import net.minecraft.world.level.block.state.BlockState;
				public class CropBlock extends BushBlock {
					public CropBlock(String name) { super(name); }
					protected boolean $mayPlaceOn$(BlockState state, BlockGetter level, BlockPos pos) { return %s; }
					public static float $getGrowthSpeed$(%s, BlockGetter level, BlockPos pos) {
						%s
						float speed = 1.0F;
						BlockPos below = pos.below();
						for (int x = -1; x <= 1; x++) {
							for (int z = -1; z <= 1; z++) {
								float soilSpeed = 0.0F;
								BlockState soil = level.getBlockState(below.offset(x, 0, z));
								%s
								if (%s) {
									soilSpeed = 1.0F;
									if (soil.isFertile(level, pos.offset(x, 0, z))) soilSpeed = 3.0F;
								}
								if (x != 0 || z != 0) soilSpeed /= 4.0F;
								speed += soilSpeed;
							}
						}
						if (level.getBlockState(pos.west()).$is$(type) || level.getBlockState(pos.east()).$is$(type)) speed /= 2.0F;
						return speed;
					}
				}""".formatted(farmCheck, method, plant, form.forge ? "" : "net.neoforged.neoforge.common.util.TriState decision = soil.canSustainPlant(level,"
						+ " below.offset(x, 0, z), net.minecraft.core.Direction.UP, cropState);", decide));
		// A platform mod's farm block (a farm block that isn't vanilla's farmland), a soil answering both checks itself, and
		// a crop taking dirt too.
		put(sources, "com.example.OwnFarm", "public class OwnFarm extends net.minecraft.world.level.block." + farm + " { public OwnFarm() { super(\"own farm\"); } }");
		String overriding = form.forge ? """
				@Override public boolean canSustainPlant(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
						net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing, net.minecraftforge.common.IPlantable plantable) {
					Hook.LOG.add("overriding soil asked"); return true;
				}""" : """
				@Override public net.neoforged.neoforge.common.util.TriState canSustainPlant(net.minecraft.world.level.block.state.BlockState state,
						net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing,
						net.minecraft.world.level.block.state.BlockState plant) {
					Hook.LOG.add("overriding soil asked"); return net.neoforged.neoforge.common.util.TriState.TRUE;
				}""";
		put(sources, "com.example.OverridingSoil", """
				public class OverridingSoil extends net.minecraft.world.level.block.Block {
					public OverridingSoil() { super("overriding"); }
					%s
					@Override public boolean isFertile(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
							net.minecraft.core.BlockPos pos) {
						Hook.LOG.add("overriding soil fertile"); return true;
					}
				}""".formatted(overriding));
		// On Forge, crops overriding the other checks Forge's canSustainPlant asks: a plant type of PLAINS (dirt sustains it), a
		// plant answering as a cactus (only a cactus sustains it).
		put(sources, "com.example.PlainsCrop", "public class PlainsCrop extends net.minecraft.world.level.block.CropBlock { public PlainsCrop() { super(\"plains crop\"); }"
				+ (form.forge ? " @Override public net.minecraftforge.common.PlantType getPlantType(net.minecraft.world.level.BlockGetter level,"
						+ " net.minecraft.core.BlockPos pos) { return net.minecraftforge.common.PlantType.PLAINS; }" : "") + " }");
		put(sources, "com.example.CactusPlant", "public class CactusPlant extends net.minecraft.world.level.block.CropBlock { public CactusPlant() { super(\"cactus plant\"); }"
				+ (form.forge ? " @Override public net.minecraft.world.level.block.state.BlockState getPlant(net.minecraft.world.level.BlockGetter world,"
						+ " net.minecraft.core.BlockPos pos) { return net.minecraft.world.level.block.Blocks.CACTUS.defaultBlockState(); }" : "") + " }");
		put(sources, "com.example.PickyCrop", """
				public class PickyCrop extends net.minecraft.world.level.block.CropBlock {
					public PickyCrop() { super("picky"); }
					@Override protected boolean $mayPlaceOn$(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
							net.minecraft.core.BlockPos pos) {
						return state.$is$(net.minecraft.world.level.block.Blocks.DIRT);
					}
				}""");
		// The hooks, as Balm's make them: of the farmland check, which takes the soil and may speed it up (its custom farmland);
		// of the 26.x moisture check, which may read it as moist.
		put(sources, "com.example.Hook", """
				public class Hook {
					public static final java.util.List<String> LOG = new java.util.ArrayList<>();
					public static boolean custom;
					public static float farmland(float speed, net.minecraft.world.level.block.state.BlockState soil) {
						LOG.add("farmland hooked " + soil);
						return custom && soil.$getBlock$() == net.minecraft.world.level.block.Blocks.OWN_FARM ? 3.0F : speed;
					}
					public static Comparable<?> moisture(Comparable<?> moisture) { LOG.add("moisture hooked " + moisture); return custom ? Integer.valueOf(7) : moisture; }
				}""");
		if (!form.forge) sources.replaceAll((file, text) -> text.replace("net.neoforged.neoforge.common.util.TriState", form.triState()));
		Map<String, String> names = form.srg ? SRG : Map.of();
		sources.replaceAll((file, text) -> {
			for (String key : SRG.keySet()) text = text.replace(key, names.getOrDefault(key, key.substring(1, key.length() - 1)));
			return text;
		});
		return sources;
	}

	private static void put(Map<String, String> sources, String className, String body) {
		int dot = className.lastIndexOf('.');
		sources.put(className.replace('.', '/') + ".java", "package " + className.substring(0, dot) + ";\n" + body);
	}

	private static Map<String, byte[]> platform(Path dir, Form form) throws Exception {
		return TestSources.compileWithLocals(dir, sources(form));
	}

	/** The crop's class with the checks put back, as it loads; a 26.x form's read as Java 25's, as the game's are, and loaded as written. */
	private static Map<String, byte[]> restored(Map<String, byte[]> platform, Form form) {
		Map<String, byte[]> restored = new HashMap<>(platform);
		ClassNode node = node(platform.get(CROP));
		int version = node.version;
		if (form.java25) node.version = 69;
		VanillaSeams.Restoring done = VanillaSeams.restoreWhole(node);
		assertNull(done.failure());
		assertTrue(!done.restored().isEmpty(), form.name());
		node.version = version;
		restored.put(CROP, write(node));
		return restored;
	}

	/** The soils around the crop, by the stand-in's field: each kept by every place, then each place but the middle in turn. */
	private static final List<String> SOILS = List.of("FARMLAND", "DRY_FARMLAND", "DIRT", "STONE", "OWN_FARM", "OVERRIDING", "TAGGED_DIRT");
	private static final List<String> CROPS = List.of("WHEAT", "PICKY", "STEM", "PLAINS_CROP", "CACTUS_PLANT");

	@Test
	void theGrowthSpeedIsThePlatformsForEveryCropAndSoil(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form), restored = restored(platform, form);
			for (String crop : CROPS) {
				for (String soil : SOILS) {
					for (String around : SOILS) {
						String what = form + " " + crop + " on " + soil + " amid " + around;
						assertEquals(grow(platform, form, crop, soil, around, false), grow(restored, form, crop, soil, around, false), what);
					}
				}
			}
			// What was put back, for pre-flight: the farmland check (but on NeoForge 26.x, whose code makes it) and the moisture check.
			List<String> seams = VanillaSeams.restoredIn(node(restored.get(CROP))).stream().map(r -> r.owner().substring(r.owner().lastIndexOf('/') + 1) + "."
					+ r.name() + " " + r.finding()).distinct().sorted().toList();
			List<String> expected = new ArrayList<>(form.java25 ? List.of("BlockState.getValueOrElse SEAM-12") : List.of("BlockState." + name(form, "getValue") + " SEAM-12"));
			if (!form.java25 || form.forge) expected.add(form.java25 ? "BlockState.is SEAM-12" : "BlockState." + name(form, "is") + " SEAM-12");
			assertEquals(expected.stream().sorted().toList(), seams, form.name());
			assertEquals("SEAM-12", VanillaSeams.findings(CROP));
		}
	}

	/**
	 * Balm's hooks: of the farmland check (an injection at vanilla's {@code is} of the soil, which takes the soil and may set
	 * its speed: its custom farmland, here the platform mod's farm block), and on 26.x of the moisture check, whose value it
	 * may change. Once put back, each runs for the soils whose check goes back (vanilla's farmland; on NeoForge 21.x and Forge
	 * from 52 no other farm block; on Forge the crops keeping their checks), and the speed it sets is the growth speed's; a
	 * soil answering the platform's checks itself, a farm block the platform counts as farmland and, on Forge, a crop
	 * overriding its checks or no CropBlock are asked as the platform has it, without the hooks.
	 */
	@Test
	void hooksOfVanillasChecksRunForTheSoilsWhoseChecksGoBack(@TempDir Path dir) throws Exception {
		for (Form form : Form.values()) {
			Map<String, byte[]> platform = platform(dir.resolve(form.name()), form);
			// As the platform has it, the method makes neither vanilla's moisture check nor (but on NeoForge 26.x) its farmland check.
			MethodNode before = growthSpeed(node(platform.get(CROP)), form);
			assertEquals(0, calls(before, form.java25 ? "getValueOrElse" : name(form, "getValue")), form.name());
			assertEquals(form.java25 && !form.forge ? 1 : 0, farmlandChecks(before, form), form.name());
			Map<String, byte[]> hooked = hooked(restored(platform, form), form);
			// Vanilla's farmland: the hooks run, at each of the nine places.
			List<String> farmland = grow(hooked, form, "WHEAT", "FARMLAND", "FARMLAND", false);
			assertEquals(9, farmland.stream().filter(l -> l.startsWith(form.java25 ? "moisture hooked" : "farmland hooked")).count(), form + " " + farmland);
			List<String> moist = grow(hooked, form, "WHEAT", "DRY_FARMLAND", "DRY_FARMLAND", true);
			if (form.java25) assertEquals("speed 10.0", moist.get(moist.size() - 1), form + " " + moist);
			// The platform mod's farm block, which Balm's hook speeds up where its check goes back (Forge 47's crops ask vanilla's
			// farmland alone), and which NeoForge 21.x and Forge from 52 count as farmland themselves.
			List<String> own = grow(hooked, form, "WHEAT", "OWN_FARM", "OWN_FARM", true);
			long ownHooked = own.stream().filter(l -> l.startsWith("farmland hooked")).count();
			if (form == Form.NEOFORGE_26) {
				// NeoForge's own call of vanilla's check, in its default's branch, which the hook found before too.
				assertEquals(9, ownHooked, form + " " + own);
			} else if (form == Form.FORGE_47) {
				// Forge 47's crops ask for vanilla's farmland alone: the hook speeds the farm block up, as on Fabric (1 + 3 + 8 * 3 / 4).
				assertEquals(9, ownHooked, form + " " + own);
				assertEquals("speed 10.0", own.get(own.size() - 1), form + " " + own);
			} else {
				List<String> asPlatform = grow(platform, form, "WHEAT", "OWN_FARM", "OWN_FARM", true);
				assertEquals(0, ownHooked, form + " " + own);
				assertEquals(asPlatform.get(asPlatform.size() - 1), own.get(own.size() - 1), form + " " + own);
			}
			// A soil answering the platform's checks itself, and on Forge a crop overriding its checks or no crop, are the platform's.
			List<String> overriding = grow(hooked, form, "WHEAT", "OVERRIDING", "OVERRIDING", false);
			assertTrue(overriding.contains("overriding soil asked") && overriding.stream().noneMatch(l -> l.contains("hooked")), form + " " + overriding);
			if (form.forge) {
				for (String crop : List.of("PICKY", "STEM")) {
					List<String> other = grow(hooked, form, crop, "FARMLAND", "FARMLAND", false);
					assertTrue(other.stream().noneMatch(l -> l.startsWith("farmland hooked")), form + " " + crop + " " + other);
				}
			}
		}
	}

	/**
	 * The crop's growth speed at (0, 1, 0), the middle soil below it {@code soil} and the eight around {@code around}
	 * (DRY_FARMLAND: farmland at moisture 0, FARMLAND at 7; a farm block at 7 too): what the soils and the hooks logged, then
	 * the speed.
	 */
	@SuppressWarnings("unchecked")
	private static List<String> grow(Map<String, byte[]> classes, Form form, String crop, String soil, String around, boolean custom) throws Exception {
		ClassLoader loader = loader(classes);
		Class<?> hook = loader.loadClass("com.example.Hook");
		hook.getField("custom").set(null, custom);
		Class<?> blocks = loader.loadClass("net.minecraft.world.level.block.Blocks"), blockClass = loader.loadClass("net.minecraft.world.level.block.Block");
		Class<?> stateClass = loader.loadClass("net.minecraft.world.level.block.state.BlockState");
		Class<?> posClass = loader.loadClass("net.minecraft.core.BlockPos");
		Object level = loader.loadClass("com.example.Level").getConstructor().newInstance();
		java.lang.reflect.Method set = level.getClass().getMethod("set", posClass, stateClass);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				String which = x == 0 && z == 0 ? soil : around;
				Object block = blocks.getField(which.equals("DRY_FARMLAND") ? name(form, "FARMLAND") : which.equals("FARMLAND") ? name(form, "FARMLAND") : which).get(null);
				boolean farm = which.contains("FARMLAND") || which.equals("OWN_FARM");
				Object state = stateClass.getConstructor(blockClass, Integer.class).newInstance(block, farm ? Integer.valueOf(which.equals("DRY_FARMLAND") ? 0 : 7) : null);
				set.invoke(level, posClass.getConstructor(int.class, int.class, int.class).newInstance(x, 0, z), state);
			}
		}
		Object cropBlock = blocks.getField(crop).get(null);
		Object pos = posClass.getConstructor(int.class, int.class, int.class).newInstance(0, 1, 0);
		Class<?> cropClass = loader.loadClass(CROP.replace('/', '.'));
		Object speed = null;
		for (java.lang.reflect.Method m : cropClass.getDeclaredMethods()) {
			if (m.getName().equals(name(form, "getGrowthSpeed"))) {
				m.setAccessible(true);
				Object first = form.forge ? cropBlock : blockClass.getMethod("defaultBlockState").invoke(cropBlock);
				speed = m.invoke(null, first, level, pos);
			}
		}
		List<String> log = new ArrayList<>((List<String>) hook.getField("LOG").get(null));
		log.add("speed " + speed);
		return log;
	}

	/**
	 * The classes with Balm's hooks woven in, as Mixin would weave them: before each of vanilla's farmland checks (the first
	 * {@code is} of the soil in each of its places: up to 1.21.11 {@code is(Block)}, on Forge 64 to 66 {@code is(TagKey)}),
	 * the soil's speed passes the hook with the soil; after 26.x's moisture check, its value passes the other. Each finds
	 * its point as in vanilla's code, or the test fails.
	 */
	private static Map<String, byte[]> hooked(Map<String, byte[]> classes, Form form) {
		Map<String, byte[]> hooked = new HashMap<>(classes);
		ClassNode node = node(classes.get(CROP));
		MethodNode method = growthSpeed(node, form);
		String state = "net/minecraft/world/level/block/state/BlockState";
		int speedSlot = -1, soilSlot = -1;
		for (var local : method.localVariables) {
			if (local.name.equals("soilSpeed")) speedSlot = local.index;
			if (local.name.equals("soil")) soilSlot = local.index;
		}
		int farmlands = 0, moistures = 0;
		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (isFarmlandCheck(call, form) && farmlands++ == 0) {
				InsnList hook = new InsnList();
				hook.add(new VarInsnNode(Opcodes.FLOAD, speedSlot));
				hook.add(new VarInsnNode(Opcodes.ALOAD, soilSlot));
				hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "farmland", "(FL" + state + ";)F", false));
				hook.add(new VarInsnNode(Opcodes.FSTORE, speedSlot));
				// Before the check, its operands pushed (the soil and the farmland block, or the tag), as Mixin injects at a call.
				method.instructions.insertBefore(call, hook);
			}
			if (call.name.equals("getValueOrElse") && moistures++ == 0) {
				method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/example/Hook", "moisture", "(Ljava/lang/Comparable;)Ljava/lang/Comparable;",
						false));
			}
		}
		assertTrue(speedSlot >= 0 && soilSlot >= 0 && (form.java25 ? moistures > 0 : farmlands > 0), form + ": a hook's point is missing");
		// The class is written with its frames worked out again, as Mixin writes the classes it weaves.
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String a, String b) {
				return "java/lang/Object";
			}
		};
		node.accept(writer);
		hooked.put(CROP, writer.toByteArray());
		return hooked;
	}

	/** Whether the call is vanilla's farmland check of the soil, as the form's vanilla code makes it. */
	private static boolean isFarmlandCheck(MethodInsnNode call, Form form) {
		if (!call.owner.equals("net/minecraft/world/level/block/state/BlockState") || !call.name.equals(name(form, "is"))) return false;
		AbstractInsnNode operand = call.getPrevious();
		return operand instanceof FieldInsnNode field && (form.java25 ? field.name.equals("GROWS_CROPS") : field.name.equals(name(form, "FARMLAND")));
	}

	private static int farmlandChecks(MethodNode method, Form form) {
		int n = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && isFarmlandCheck(call, form)) n++;
		}
		return n;
	}

	private static String name(Form form, String name) {
		return form.srg ? SRG.getOrDefault("$" + name + "$", name) : name;
	}

	private static MethodNode growthSpeed(ClassNode node, Form form) {
		return node.methods.stream().filter(m -> m.name.equals(name(form, "getGrowthSpeed"))).findFirst().orElseThrow();
	}

	private static int calls(MethodNode method, String name) {
		int n = 0;
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.name.equals(name)) n++;
		}
		return n;
	}

	private static ClassLoader loader(Map<String, byte[]> classes) {
		return new ClassLoader(CropGrowthTest.class.getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				byte[] data = classes.get(name.replace('.', '/'));
				if (data == null) throw new ClassNotFoundException(name);
				return defineClass(name, data, 0, data.length);
			}
		};
	}

	private static ClassNode node(byte[] data) {
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, 0);
		return node;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}
}
