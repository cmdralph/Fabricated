package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Registers the picture-in-picture renderers Fabric mods registered with {@code fabric-rendering-v1}'s
 * {@code PictureInPictureRendererRegistry} with NeoForge, whose GUI renderer creates and pools them itself (upstream's
 * own GUI renderer mixin is left out by the Fabric API plan).
 *
 * <p>NeoForge needs each renderer's render state class up front, which only a renderer knows: each Fabric factory is
 * called once while NeoForge gathers the renderers, and that renderer is the first one NeoForge gets.
 *
 * <p>On 26.2 and later only: on 26.1.2, Fabric's factories need the game renderer's submit queue, which doesn't exist
 * yet while NeoForge gathers the renderers (NeoForge's event there also takes renderers by buffer source).
 */
public final class PictureInPictureBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String REGISTRY = "net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry";
	private static final String REGISTRY_IMPL = "net.fabricmc.fabric.impl.client.rendering.PictureInPictureRendererRegistryImpl";

	private PictureInPictureBridge() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(RegisterPictureInPictureRenderersEvent.class, PictureInPictureBridge::registerFabricRenderers);
	}

	private static void registerFabricRenderers(RegisterPictureInPictureRenderersEvent event) {
		ClassLoader loader = PictureInPictureBridge.class.getClassLoader();
		Class<?> impl;
		try {
			impl = Class.forName(REGISTRY_IMPL, true, loader);
		} catch (ClassNotFoundException e) {
			return; // fabric-rendering-v1 is not installed
		}
		List<?> factories;
		MethodHandle create;
		MethodHandle register;
		Object context;
		try {
			register = MethodHandles.publicLookup().findVirtual(RegisterPictureInPictureRenderersEvent.class, "register",
					MethodType.methodType(void.class, Class.class, Supplier.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			LOGGER.debug("Fabric picture-in-picture renderers are not bridged on this version");
			return;
		}
		try {
			Field registered = impl.getDeclaredField("FACTORIES");
			registered.setAccessible(true);
			factories = List.copyOf((List<?>) registered.get(null));
			Class<?> factory = Class.forName(REGISTRY + "$Factory", false, loader);
			Class<?> contextType = Class.forName(REGISTRY + "$Context", false, loader);
			create = MethodHandles.publicLookup()
					.findVirtual(factory, "createRenderer", MethodType.methodType(PictureInPictureRenderer.class, contextType))
					.asType(MethodType.methodType(PictureInPictureRenderer.class, Object.class, Object.class));
			context = Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[] {contextType}, (proxy, method, args) -> switch (method.getName()) {
				case "minecraft" -> Minecraft.getInstance();
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "toString" -> "PictureInPictureRendererRegistry.Context";
				default -> throw new UnsupportedOperationException(method.getName());
			});
		} catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException | SecurityException e) {
			LOGGER.error("fabric-rendering-v1's picture-in-picture registry has an unexpected shape; renderers registered with it are not used", e);
			return;
		}
		for (Object factory : factories) {
			Supplier<PictureInPictureRenderer<?>> make = () -> {
				try {
					return (PictureInPictureRenderer<?>) create.invokeExact(factory, context);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable t) {
					throw new IllegalStateException(t);
				}
			};
			try {
				PictureInPictureRenderer<?> first = make.get();
				AtomicReference<PictureInPictureRenderer<?>> unused = new AtomicReference<>(first);
				Supplier<PictureInPictureRenderer<?>> renderers = () -> {
					PictureInPictureRenderer<?> renderer = unused.getAndSet(null);
					return renderer != null ? renderer : make.get();
				};
				register.invoke(event, first.getRenderStateClass(), renderers);
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				LOGGER.error("Fabric picture-in-picture renderer factory {} failed; its renderer is not used", factory, t);
			}
		}
		if (!factories.isEmpty()) LOGGER.debug("{} Fabric picture-in-picture renderer(s) registered with NeoForge", factories.size());
	}
}
