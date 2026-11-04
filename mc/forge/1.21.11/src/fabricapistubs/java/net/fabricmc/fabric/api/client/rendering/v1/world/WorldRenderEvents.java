package net.fabricmc.fabric.api.client.rendering.v1.world;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that the renderer test mod uses, in Mojang's
 * names.
 */
public final class WorldRenderEvents {
	public static final Event<AfterBlockOutlineExtraction> AFTER_BLOCK_OUTLINE_EXTRACTION = null;
	public static final Event<EndExtraction> END_EXTRACTION = null;

	private WorldRenderEvents() {
	}

	@FunctionalInterface
	public interface AfterBlockOutlineExtraction {
		void afterBlockOutlineExtraction(WorldExtractionContext context, @Nullable HitResult result);
	}

	@FunctionalInterface
	public interface EndExtraction {
		void endExtraction(WorldExtractionContext context);
	}
}
