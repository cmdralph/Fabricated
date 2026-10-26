package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * The forge test mod's part for fabric-object-builder-api-v1's default attribute event on Forge 64 to 66, where
 * Fabricated passes the module through (with Fabric API's jar only): a cow's default armor, set through
 * {@code FabricDefaultAttributeRegistry.MODIFY}, must be what the game reads by the first tick, with the cow's other
 * defaults still there. Forge builds the attributes the game reads after its attribute modification event, in which the
 * in-game mod runs Fabric's.
 */
public final class ForgeAttributeTest {
	private static final double ARMOR = 7;
	private static final double COW_HEALTH = 10;
	private static boolean registered;

	private ForgeAttributeTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's object builder module. */
	public static void register() {
		FabricDefaultAttributeRegistry.MODIFY.register(context -> context.modify(EntityType.COW, (type, builder) -> builder.add(Attributes.ARMOR, ARMOR)));
		registered = true;
	}

	public static void check() {
		if (!registered) return;
		AttributeSupplier cow = DefaultAttributes.getSupplier(EntityType.COW);
		double armor = cow.getBaseValue(Attributes.ARMOR);
		double health = cow.getBaseValue(Attributes.MAX_HEALTH);
		if (armor == ARMOR && health == COW_HEALTH) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST default attributes OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST default attributes: a cow's armor is {} (MODIFY set {}), its health {}", armor, ARMOR, health);
		}
	}
}
