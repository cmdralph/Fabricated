package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;

/**
 * The tint of a fluid drawn for a {@code fabric-rendering-fluids-v1} render handler. Upstream replaces vanilla's water
 * color with the handler's {@code getFluidColor} while the handler draws through vanilla's renderer; NeoForge's
 * renderer asks the fluid type for its tint instead (upstream's mixin handler is left out by the Fabric API plan), so
 * the handler's color replaces that tint's color, keeping its alpha.
 *
 * <p>The module is loaded like any Fabric mod, so its classes are looked up by name, and nothing changes when it is
 * not installed.
 */
public final class FabricFluidTint {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String RENDERING = "net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderingImpl";
	private static final String INFO = "net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderHandlerInfo";
	private static final String HANDLER = "net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler";

	/** The handler drawing right now on this thread, or null: {@code () -> Object}. */
	private static final @Nullable MethodHandle CURRENT_HANDLER;
	/** {@code (Object handler, BlockAndTintGetter, BlockPos, FluidState) -> int}. */
	private static final @Nullable MethodHandle COLOR;

	static {
		MethodHandle current = null;
		MethodHandle color = null;
		ClassLoader loader = FabricFluidTint.class.getClassLoader();
		try {
			Class<?> rendering = Class.forName(RENDERING, false, loader);
			Class<?> info = Class.forName(INFO, false, loader);
			Class<?> handler = Class.forName(HANDLER, false, loader);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			current = MethodHandles.filterReturnValue(lookup.findStatic(rendering, "getCurrentInfo", MethodType.methodType(info)),
					lookup.findGetter(info, "handler", handler)).asType(MethodType.methodType(Object.class));
			color = lookup.findVirtual(handler, "getFluidColor", MethodType.methodType(int.class, BlockAndTintGetter.class, BlockPos.class, FluidState.class))
					.asType(MethodType.methodType(int.class, Object.class, BlockAndTintGetter.class, BlockPos.class, FluidState.class));
		} catch (ClassNotFoundException e) {
			// fabric-rendering-fluids-v1 is not installed
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-rendering-fluids-v1 has an unexpected shape; fluid render handlers' colors are not used", e);
			current = null;
		}
		CURRENT_HANDLER = current;
		COLOR = color;
	}

	private FabricFluidTint() {
	}

	/** {@code tint} (NeoForge's, ARGB), with the color of the Fabric render handler drawing this fluid, if any. */
	public static int apply(int tint, BlockAndTintGetter level, BlockPos pos, FluidState fluid) {
		if (CURRENT_HANDLER == null || COLOR == null) return tint;
		try {
			Object handler = (Object) CURRENT_HANDLER.invokeExact();
			if (handler == null) return tint;
			int color = (int) COLOR.invokeExact(handler, level, pos, fluid);
			return (tint & 0xFF000000) | (color & 0xFFFFFF);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
