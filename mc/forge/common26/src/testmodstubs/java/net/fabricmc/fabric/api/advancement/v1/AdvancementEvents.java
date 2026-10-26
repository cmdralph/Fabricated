package net.fabricmc.fabric.api.advancement.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;

/**
 * Stand-in for the part of fabric-advancement-api-v1's {@code AdvancementEvents} the forge test mod uses (signatures as
 * upstream's on 26.2 and 26.3): Fabricated passes the module through, so the test mod compiles against this and runs
 * against Fabric API's jar. Never packaged.
 */
public final class AdvancementEvents {
	public static final Event<Modify> MODIFY = null;

	private AdvancementEvents() {
	}

	@FunctionalInterface
	public interface Modify {
		void modifyAdvancement(Identifier id, Advancement.Builder builder, AdvancementSource source, HolderLookup.Provider registries);
	}
}
