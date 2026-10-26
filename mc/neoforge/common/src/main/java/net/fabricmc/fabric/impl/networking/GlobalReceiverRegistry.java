package net.fabricmc.fabric.impl.networking;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

/**
 * Fabricated implementation of Fabric API's global receiver registry: handlers that apply to every connection
 * of one phase (login, configuration or play) and side.
 *
 * @param <H> handler type of the phase
 */
public final class GlobalReceiverRegistry<H> {
	private final Map<Identifier, H> handlers = new ConcurrentHashMap<>();

	public boolean registerGlobalReceiver(Identifier channelName, H handler) {
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		Objects.requireNonNull(handler, "Channel handler cannot be null");
		return this.handlers.putIfAbsent(channelName, handler) == null;
	}

	public @Nullable H unregisterGlobalReceiver(Identifier channelName) {
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		return this.handlers.remove(channelName);
	}

	public Set<Identifier> getChannels() {
		return Set.copyOf(this.handlers.keySet());
	}

	public @Nullable H getHandler(Identifier channelName) {
		return this.handlers.get(channelName);
	}
}
