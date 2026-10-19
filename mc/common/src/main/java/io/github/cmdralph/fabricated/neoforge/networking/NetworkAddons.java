package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Where Fabric's per-connection networking state ("addons") lives: in a field of the packet listener it belongs to
 * (added by a mixin), so it goes away with the listener. A static map from listener to addon would never release
 * either, as the addon refers to its listener.
 */
public final class NetworkAddons {
	private NetworkAddons() {
	}

	/** Implemented by the packet listeners through {@code NetworkAddonHolderMixin}. */
	public interface Holder {
		@Nullable Object fabricated$networkAddon();

		void fabricated$setNetworkAddon(Object addon);
	}

	/** The listener's addon of {@code type}, created on first use (the field is volatile: only creation locks). */
	public static <L, A> A get(L listener, Class<A> type, Function<L, A> factory) {
		Holder holder = (Holder) listener;
		Object addon = holder.fabricated$networkAddon();
		if (type.isInstance(addon)) return type.cast(addon);
		synchronized (holder) {
			addon = holder.fabricated$networkAddon();
			if (type.isInstance(addon)) return type.cast(addon);
			A created = factory.apply(listener);
			holder.fabricated$setNetworkAddon(created);
			return created;
		}
	}
}
