package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.neoforge.fluid.FabricFluidType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge's client extensions for the fluid type of a Fabric mod's fluid ({@link FabricFluidType}): on 1.21.11
 * NeoForge looks up a fluid's textures and tint through them, to draw it in the world and in tanks, and so does
 * {@code FluidVariantRendering}. They are those of the fluid's {@code fabric-rendering-fluids-v1} render handler, and
 * water's for a fluid without one, as vanilla draws it.
 *
 * <p>The module is loaded like any Fabric mod, so its classes are looked up by name (as in {@link FabricFluidTint}).
 */
public final class FabricFluidClientExtensions implements IClientFluidTypeExtensions {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String REGISTRY = "net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry";
	private static final String HANDLER = "net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler";

	/** The fluid's render handler, or null: {@code (Fluid) -> Object}. */
	private static final @Nullable MethodHandle HANDLER_OF;
	/** {@code (Object handler, BlockAndTintGetter, BlockPos, FluidState) -> TextureAtlasSprite[]}. */
	private static final @Nullable MethodHandle SPRITES;
	/** {@code (Object handler, BlockAndTintGetter, BlockPos, FluidState) -> int}. */
	private static final @Nullable MethodHandle COLOR;

	static {
		MethodHandle handlerOf = null;
		MethodHandle sprites = null;
		MethodHandle color = null;
		ClassLoader loader = FabricFluidClientExtensions.class.getClassLoader();
		try {
			Class<?> registry = Class.forName(REGISTRY, false, loader);
			Class<?> handler = Class.forName(HANDLER, false, loader);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodType drawing = MethodType.methodType(void.class, BlockAndTintGetter.class, BlockPos.class, FluidState.class);
			handlerOf = MethodHandles.foldArguments(lookup.findVirtual(registry, "get", MethodType.methodType(handler, Fluid.class)),
					lookup.findStaticGetter(registry, "INSTANCE", registry)).asType(MethodType.methodType(Object.class, Fluid.class));
			sprites = lookup.findVirtual(handler, "getFluidSprites", drawing.changeReturnType(TextureAtlasSprite[].class))
					.asType(drawing.changeReturnType(TextureAtlasSprite[].class).insertParameterTypes(0, Object.class));
			color = lookup.findVirtual(handler, "getFluidColor", drawing.changeReturnType(int.class))
					.asType(drawing.changeReturnType(int.class).insertParameterTypes(0, Object.class));
		} catch (ClassNotFoundException e) {
			// fabric-rendering-fluids-v1 is not installed
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-rendering-fluids-v1 has an unexpected shape; Fabric fluids are drawn as water", e);
			handlerOf = null;
		}
		HANDLER_OF = handlerOf;
		SPRITES = sprites;
		COLOR = color;
	}

	private final Fluid fluid;

	private FabricFluidClientExtensions(Fluid fluid) {
		this.fluid = fluid;
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
			for (FluidType type : NeoForgeRegistries.FLUID_TYPES) {
				if (type instanceof FabricFluidType fabric) event.registerFluidType(new FabricFluidClientExtensions(fabric.source()), type);
			}
		});
	}

	private static IClientFluidTypeExtensions water() {
		return IClientFluidTypeExtensions.of(Fluids.WATER);
	}

	private @Nullable Object handler() {
		if (HANDLER_OF == null || SPRITES == null || COLOR == null) return null;
		try {
			return (Object) HANDLER_OF.invokeExact(fluid);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	/** The handler's sprite at {@code index} (still, flowing, overlay), or null if it has none (before textures load, too). */
	private static @Nullable Identifier sprite(Object handler, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, FluidState state, int index) {
		if (SPRITES == null) return null;
		try {
			TextureAtlasSprite[] sprites = (TextureAtlasSprite[]) SPRITES.invokeExact(handler, level, pos, state);
			return sprites != null && sprites.length > index && sprites[index] != null ? sprites[index].contents().name() : null;
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	/** The handler's tint, opaque. */
	private static int color(Object handler, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, FluidState state) {
		if (COLOR == null) return -1;
		try {
			return 0xFF000000 | (int) COLOR.invokeExact(handler, level, pos, state);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	private @Nullable Identifier sprite(int index) {
		Object handler = handler();
		return handler == null ? null : sprite(handler, null, null, fluid.defaultFluidState(), index);
	}

	private @Nullable Identifier sprite(FluidState state, BlockAndTintGetter level, BlockPos pos, int index) {
		Object handler = handler();
		return handler == null ? null : sprite(handler, level, pos, state, index);
	}

	@Override
	public Identifier getStillTexture() {
		Identifier sprite = sprite(0);
		return sprite != null ? sprite : water().getStillTexture();
	}

	@Override
	public Identifier getFlowingTexture() {
		Identifier sprite = sprite(1);
		return sprite != null ? sprite : water().getFlowingTexture();
	}

	@Override
	public @Nullable Identifier getOverlayTexture() {
		return handler() != null ? sprite(2) : water().getOverlayTexture();
	}

	@Override
	public int getTintColor() {
		Object handler = handler();
		return handler != null ? color(handler, null, null, fluid.defaultFluidState()) : water().getTintColor();
	}

	@Override
	public Identifier getStillTexture(FluidState state, BlockAndTintGetter level, BlockPos pos) {
		Identifier sprite = sprite(state, level, pos, 0);
		return sprite != null ? sprite : water().getStillTexture(state, level, pos);
	}

	@Override
	public Identifier getFlowingTexture(FluidState state, BlockAndTintGetter level, BlockPos pos) {
		Identifier sprite = sprite(state, level, pos, 1);
		return sprite != null ? sprite : water().getFlowingTexture(state, level, pos);
	}

	@Override
	public @Nullable Identifier getOverlayTexture(FluidState state, BlockAndTintGetter level, BlockPos pos) {
		return handler() != null ? sprite(state, level, pos, 2) : water().getOverlayTexture(state, level, pos);
	}

	@Override
	public int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
		Object handler = handler();
		return handler != null ? color(handler, level, pos, state) : water().getTintColor(state, level, pos);
	}
}
