package net.fabricmc.fabric.impl.resource;

import java.util.Objects;

import io.github.cmdralph.fabricated.neoforge.resource.ResourceRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Fabricated implementation of Fabric's resource loader (26.x API names). Registrations are recorded in
 * {@link ResourceRegistry}; Fabricated hands them to NeoForge's reload-listener and pack-finder events.
 */
public class ResourceLoaderImpl implements ResourceLoader {
	private static final ResourceLoaderImpl CLIENT = new ResourceLoaderImpl(PackType.CLIENT_RESOURCES);

	protected final ResourceRegistry registry;

	protected ResourceLoaderImpl(PackType type) {
		this.registry = ResourceRegistry.of(type);
	}

	public static ResourceLoaderImpl get(PackType type) {
		Objects.requireNonNull(type, "The pack type should not be null.");
		return type == PackType.SERVER_DATA ? DataResourceLoaderImpl.INSTANCE : CLIENT;
	}

	@Override
	public void registerReloadListener(Identifier id, PreparableReloadListener listener) {
		this.registry.addListener(id, listener);
	}

	@Override
	public void addListenerOrdering(Identifier firstListener, Identifier secondListener) {
		this.registry.addOrdering(firstListener, secondListener);
	}

	public static boolean registerBuiltinPack(Identifier id, String subPath, ModContainer container, PackActivationType activationType) {
		return registerBuiltinPack(id, subPath, container, Component.literal(id.getNamespace() + '/' + id.getPath()), activationType);
	}

	/** Same signature as upstream (Fabric API's deprecated resource-loader-v0 calls it). */
	public static boolean registerBuiltinPack(Identifier id, String subPath, ModContainer container, Component displayName, PackActivationType activationType) {
		return ResourceRegistry.addBuiltinPack(id, subPath, container, displayName, activationType);
	}
}
