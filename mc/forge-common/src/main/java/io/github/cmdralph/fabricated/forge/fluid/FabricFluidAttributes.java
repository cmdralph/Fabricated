package io.github.cmdralph.fabricated.forge.fluid;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

/**
 * A Fabric fluid's attributes as Fabric API's transfer module answers them ({@code FluidVariantAttributes}), for
 * {@link FabricFluidType}. The module is passed through on Forge, so it is there only with Fabric API's jar: its methods
 * are looked up once, and without them the answers are those of its default attributes (the fluid's block's light and
 * name, water's temperature and viscosity, the bucket's sounds), as for a fluid no mod gave attributes.
 */
final class FabricFluidAttributes {
	private static final String VARIANT = "net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant";
	private static final String ATTRIBUTES = "net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes";
	/** Fabric's {@code FluidConstants.WATER_TEMPERATURE} and {@code WATER_VISCOSITY}, its defaults. */
	private static final int WATER_TEMPERATURE = 300;
	private static final int WATER_VISCOSITY = 1000;

	/** Upstream's methods, or null without the module. */
	private record Upstream(Method variant, Method name, Method luminance, Method temperature, Method viscosity, Method lighterThanAir,
			Method fillSound, Method emptySound) {
	}

	private static volatile Upstream upstream;
	private static volatile boolean looked;

	private FabricFluidAttributes() {
	}

	static Component name(Fluid fluid) {
		Upstream api = upstream();
		if (api != null) return (Component) call(api.name(), fluid);
		Block block = block(fluid);
		return block == Blocks.AIR ? Component.translatable(Util.makeDescriptionId("block", BuiltInRegistries.FLUID.getKey(fluid))) : block.getName();
	}

	static int luminance(Fluid fluid) {
		Upstream api = upstream();
		return api != null ? (int) call(api.luminance(), fluid) : fluid.defaultFluidState().createLegacyBlock().getLightEmission();
	}

	static int temperature(Fluid fluid) {
		Upstream api = upstream();
		return api != null ? (int) call(api.temperature(), fluid) : WATER_TEMPERATURE;
	}

	static int viscosity(Fluid fluid) {
		Upstream api = upstream();
		return api != null ? (int) call(api.viscosity(), fluid, (Object) null) : WATER_VISCOSITY; // no level: Forge asks the type without one
	}

	static boolean lighterThanAir(Fluid fluid) {
		Upstream api = upstream();
		return api != null && (boolean) call(api.lighterThanAir(), fluid);
	}

	static SoundEvent fillSound(Fluid fluid) {
		Upstream api = upstream();
		return api != null ? (SoundEvent) call(api.fillSound(), fluid) : fluid.getPickupSound().orElse(SoundEvents.BUCKET_FILL);
	}

	static SoundEvent emptySound(Fluid fluid) {
		Upstream api = upstream();
		return api != null ? (SoundEvent) call(api.emptySound(), fluid) : SoundEvents.BUCKET_EMPTY;
	}

	static Block block(Fluid fluid) {
		return fluid.defaultFluidState().createLegacyBlock().getBlock();
	}

	private static Upstream upstream() {
		if (!looked) {
			upstream = find();
			looked = true;
		}
		return upstream;
	}

	private static Upstream find() {
		ClassLoader loader = FabricFluidAttributes.class.getClassLoader();
		try {
			Class<?> variant = Class.forName(VARIANT, false, loader);
			Class<?> attributes = Class.forName(ATTRIBUTES, false, loader);
			return new Upstream(variant.getMethod("of", Fluid.class), attributes.getMethod("getName", variant),
					attributes.getMethod("getLuminance", variant), attributes.getMethod("getTemperature", variant),
					attributes.getMethod("getViscosity", variant, Level.class), attributes.getMethod("isLighterThanAir", variant),
					attributes.getMethod("getFillSound", variant), attributes.getMethod("getEmptySound", variant));
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return null;
		}
	}

	/** Calls one of upstream's attribute methods on the fluid's variant, with the arguments after it. */
	private static Object call(Method method, Fluid fluid, Object... more) {
		try {
			Object[] arguments = new Object[1 + more.length];
			arguments[0] = upstream.variant().invoke(null, fluid);
			System.arraycopy(more, 0, arguments, 1, more.length);
			return method.invoke(null, arguments);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Fabric's fluid attributes can't be read", e);
		} catch (InvocationTargetException e) {
			// A mod's attribute handler's failure, which upstream lets through.
			if (e.getCause() instanceof RuntimeException failure) throw failure;
			if (e.getCause() instanceof Error failure) throw failure;
			throw new IllegalStateException("Fabric's fluid attributes failed", e.getCause());
		}
	}
}
