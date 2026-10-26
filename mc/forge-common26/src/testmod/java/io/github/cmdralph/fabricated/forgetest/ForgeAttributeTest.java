package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * The forge test mod's part for fabric-object-builder-api-v1's default attribute event on Forge 64 to 66, where
 * Fabricated passes the module through (with Fabric API's jar only): a cow's default armor, set through
 * {@code FabricDefaultAttributeRegistry.MODIFY}, must be what the game reads by the first tick, with the cow's other
 * defaults still there. Forge builds the attributes the game reads after its attribute modification event, in which the
 * in-game mod runs Fabric's. The cow is looked up by its id: {@code EntityType}'s fields aren't the same across 26.x.
 */
public final class ForgeAttributeTest {
	private static final Identifier COW = Identifier.withDefaultNamespace("cow");
	private static final double ARMOR = 7;
	private static final double COW_HEALTH = 10;
	private static EntityType<? extends LivingEntity> cow;

	private ForgeAttributeTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's object builder module. */
	@SuppressWarnings("unchecked")
	public static void register() {
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(COW)) {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST default attributes: no entity type {}", COW);
			return;
		}
		EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(COW);
		FabricDefaultAttributeRegistry.MODIFY.register(context -> context.modify(type, (modified, builder) -> builder.add(Attributes.ARMOR, ARMOR)));
		cow = type;
	}

	public static void check() {
		if (cow == null) return;
		AttributeSupplier defaults = DefaultAttributes.getSupplier(cow);
		double armor = defaults.getBaseValue(Attributes.ARMOR);
		double health = defaults.getBaseValue(Attributes.MAX_HEALTH);
		if (armor == ARMOR && health == COW_HEALTH) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST default attributes OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST default attributes: a cow's armor is {} (MODIFY set {}), its health {}", armor, ARMOR, health);
		}
	}
}
