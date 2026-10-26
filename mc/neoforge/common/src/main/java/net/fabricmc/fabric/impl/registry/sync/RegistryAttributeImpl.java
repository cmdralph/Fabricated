package net.fabricmc.fabric.impl.registry.sync;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cmdralph.fabricated.neoforge.registry.RegistrySyncBridge;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.resources.ResourceKey;

/**
 * Fabric's registry attributes. {@link RegistryAttribute#SYNCED} is mapped to NeoForge's registry sync flag, and a
 * registry NeoForge already syncs reports {@code SYNCED} too. The other attributes are recorded for mods that
 * read them; NeoForge's own registry handling doesn't depend on them.
 */
public final class RegistryAttributeImpl implements RegistryAttributeHolder {
	private static final Map<ResourceKey<?>, RegistryAttributeImpl> HOLDER_MAP = new ConcurrentHashMap<>();

	private final ResourceKey<?> key;
	private final EnumSet<RegistryAttribute> attributes = EnumSet.noneOf(RegistryAttribute.class);

	private RegistryAttributeImpl(ResourceKey<?> key) {
		this.key = key;
	}

	public static RegistryAttributeHolder getHolder(ResourceKey<?> key) {
		return HOLDER_MAP.computeIfAbsent(key, RegistryAttributeImpl::new);
	}

	/** Whether Fabric code marked this registry {@code SYNCED} (without asking NeoForge). */
	public static boolean markedSynced(ResourceKey<?> key) {
		RegistryAttributeImpl holder = HOLDER_MAP.get(key);
		return holder != null && holder.has(RegistryAttribute.SYNCED);
	}

	private synchronized boolean has(RegistryAttribute attribute) {
		return this.attributes.contains(attribute);
	}

	@Override
	public RegistryAttributeHolder addAttribute(RegistryAttribute attribute) {
		synchronized (this) {
			this.attributes.add(attribute);
		}
		if (attribute == RegistryAttribute.SYNCED) RegistrySyncBridge.markSynced(this.key);
		return this;
	}

	@Override
	public boolean hasAttribute(RegistryAttribute attribute) {
		return has(attribute) || attribute == RegistryAttribute.SYNCED && RegistrySyncBridge.syncedByNeoForge(this.key);
	}
}
