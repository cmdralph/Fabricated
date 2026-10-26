package io.github.cmdralph.fabricated.forge.fluid;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.SoundAction;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

/**
 * The Forge fluid type of a Fabric mod's fluid. Forge asks every fluid for its type (to move entities in it, to let it
 * meet lava, to name it in a tank) and throws for a mod's fluid without one ("Mod fluids must override getFluidType"),
 * which every Fabric fluid is. Each fluid family (a source and its flowing fluid) gets one, registered under the
 * source's id.
 *
 * <p>Everything is read from Fabric's side when asked: the name, light, temperature, viscosity and bucket sounds from
 * Fabric's attributes ({@link FabricFluidAttributes}), and how entities behave from the fluid's tags, as vanilla does. A
 * fluid tagged {@code minecraft:water} is water to entities (they swim, drown, float boats and put out fires in it, and
 * it pushes them); any other leaves them alone, and they move through it as through air (see
 * {@code LivingEntityFluidTravelMixin}).
 */
public final class FabricFluidType extends FluidType {
	/** The type of each Fabric fluid, its source's and its flowing fluid's alike. */
	private static final Map<Fluid, FabricFluidType> TYPES = new ConcurrentHashMap<>();

	private final Fluid source;

	private FabricFluidType(Fluid source) {
		// Vanilla's path types for whatever the fluid's tags make it; the rest is asked below.
		super(Properties.create().pathType(null).adjacentPathType(null));
		this.source = source;
	}

	/**
	 * Registers a type for each Fabric fluid registered by then, once the Fabric mods' main initializers have run (in
	 * another registry's event): Forge's fluid type registry is opened for it if it isn't already.
	 */
	public static void registerAll() {
		ForgeRegistry<FluidType> registry = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.FLUID_TYPES);
		boolean locked = registry.isLocked();
		if (locked) registry.unfreeze();
		try {
			for (Fluid fluid : BuiltInRegistries.FLUID) {
				if (!needsType(fluid) || source(fluid) != fluid) continue;
				FabricFluidType type = of(fluid);
				if (!registry.containsValue(type)) registry.register(BuiltInRegistries.FLUID.getKey(fluid), type);
			}
		} finally {
			if (locked) registry.freeze();
		}
	}

	/**
	 * The type of a fluid whose class doesn't name one (Forge asks vanilla's types for such a fluid, see
	 * {@code FluidTypeMixin}), or null for vanilla's and Forge's own fluids.
	 */
	public static @Nullable FabricFluidType forFluid(Fluid fluid) {
		FabricFluidType type = TYPES.get(fluid);
		if (type != null || !needsType(fluid)) return type;
		return of(fluid);
	}

	/** Whether entities move through this fluid as through air: a Fabric fluid that isn't water to them. */
	public static boolean leavesEntitiesAlone(FluidState state) {
		return !state.isEmpty() && state.getType().getFluidType() instanceof FabricFluidType type && !type.water();
	}

	private static FabricFluidType of(Fluid fluid) {
		Fluid source = source(fluid);
		FabricFluidType type = TYPES.computeIfAbsent(source, FabricFluidType::new);
		TYPES.putIfAbsent(fluid, type);
		return type;
	}

	/**
	 * A fluid outside vanilla and Forge whose class doesn't override {@code getFluidType}. Vanilla's are told by
	 * identity, as Forge does, so a Fabric fluid asked before it is registered (its key is still the default) gets one.
	 */
	private static boolean needsType(Fluid fluid) {
		if (fluid == Fluids.EMPTY || fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER || fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) return false;
		if (BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals("forge")) return false; // its milk
		try {
			return fluid.getClass().getMethod("getFluidType").getDeclaringClass() == Fluid.class;
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Forge's Fluid.getFluidType is missing", e);
		}
	}

	/** The source of a Fabric mod's flowing fluid, which names the family; the fluid itself otherwise. */
	private static Fluid source(Fluid fluid) {
		Fluid source = fluid instanceof FlowingFluid flowing ? flowing.getSource() : null;
		return source != null && needsType(source) ? source : fluid;
	}

	/** The family's source fluid. */
	public Fluid source() {
		return source;
	}

	private boolean water() {
		return source.defaultFluidState().is(FluidTags.WATER);
	}

	@Override
	public Component getDescription() {
		return FabricFluidAttributes.name(source);
	}

	@Override
	public Component getDescription(FluidStack stack) {
		return getDescription();
	}

	/** The key of the name Fabric's default attributes give the fluid: its block's, or one made from its id without one. */
	@Override
	public String getDescriptionId() {
		Block block = FabricFluidAttributes.block(source);
		return block == Blocks.AIR ? Util.makeDescriptionId("block", BuiltInRegistries.FLUID.getKey(source)) : block.getDescriptionId();
	}

	@Override
	public String getDescriptionId(FluidStack stack) {
		return getDescriptionId();
	}

	@Override
	public int getLightLevel() {
		return FabricFluidAttributes.luminance(source);
	}

	@Override
	public int getDensity() {
		return FabricFluidAttributes.lighterThanAir(source) ? -1000 : 1000;
	}

	@Override
	public int getTemperature() {
		return FabricFluidAttributes.temperature(source);
	}

	@Override
	public int getViscosity() {
		return FabricFluidAttributes.viscosity(source);
	}

	@Override
	public @Nullable SoundEvent getSound(SoundAction action) {
		if (action == SoundActions.BUCKET_FILL) return FabricFluidAttributes.fillSound(source);
		if (action == SoundActions.BUCKET_EMPTY) return FabricFluidAttributes.emptySound(source);
		return super.getSound(action);
	}

	@Override
	public boolean canPushEntity(Entity entity) {
		return water();
	}

	@Override
	public boolean canSwim(Entity entity) {
		return water();
	}

	@Override
	public boolean canDrownIn(LivingEntity entity) {
		return water();
	}

	@Override
	public boolean canExtinguish(Entity entity) {
		return water();
	}

	@Override
	public boolean canExtinguish(FluidState state, BlockGetter getter, BlockPos pos) {
		return water();
	}

	@Override
	public boolean canHydrate(Entity entity) {
		return water();
	}

	@Override
	public boolean canHydrate(FluidStack stack) {
		return water();
	}

	@Override
	public boolean canHydrate(FluidState state, BlockGetter getter, BlockPos pos, BlockState source, BlockPos sourcePos) {
		return water();
	}

	@Override
	public boolean supportsBoating(AbstractBoat boat) {
		return water();
	}

	@Override
	public float getFallDistanceModifier(Entity entity) {
		return water() ? 0.0F : 1.0F;
	}

	/** An item falls through a fluid that isn't water to it, as through air. */
	@Override
	public void setItemMovement(ItemEntity entity) {
		if (water()) super.setItemMovement(entity);
		else entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, -entity.getGravity(), 0.0));
	}
}
