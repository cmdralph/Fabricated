package net.fabricmc.fabric.impl.networking;

/**
 * Fabric API's marker (an internal interface of {@code fabric-networking-api-v1} on 1.21) for a packet listener the
 * networking API leaves out: Fabric's fake player has one, and mods implement it on their own fake listeners (Flashback's
 * replay). As upstream, such a listener's connection isn't announced to {@code ServerPlayConnectionEvents.INIT}.
 */
public interface UntrackedNetworkHandler {
}
