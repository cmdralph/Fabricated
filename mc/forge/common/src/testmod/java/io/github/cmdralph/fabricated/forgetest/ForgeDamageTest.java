package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The forge test mod's part for fabric-entity-events-v1's damage events (with Fabric API's jar only): a pig wearing a
 * helmet is hurt through the game's own {@code hurtServer} twice. An {@code ALLOW_DAMAGE} listener refuses the first
 * hit, which must leave the pig's health as it was. The second, a falling block's, must take its health down and fire
 * {@code AFTER_DAMAGE} with the damage the pig was dealt and the damage it took, which the helmet makes a quarter less,
 * unblocked. Forge 61's and 64's {@code hurtServer} drop the local upstream's after-damage hook reads the dealt damage
 * from, so the in-game mod fires that event there (DAMAGE-1).
 */
public final class ForgeDamageTest {
	/** The damage of the hit {@code ALLOW_DAMAGE} refuses, and of the one it lets through. */
	private static final float REFUSED = 3.0F;
	private static final float DEALT = 2.0F;
	/** The pig being hurt, and what {@code AFTER_DAMAGE} reported for it. Set by {@link #check}. */
	private static volatile LivingEntity target;
	private static volatile String afterDamage;

	private ForgeDamageTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's entity events module. */
	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> entity != target || amount != REFUSED);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (entity == target) afterDamage = baseDamageTaken + "/" + damageTaken + (blocked ? " blocked" : "");
		});
	}

	/** At the first server tick, on the overworld. */
	public static void check(ServerLevel level) {
		BlockPos pos = ForgeTestMod.testSite(level, 22, 4);
		List<String> failed = new ArrayList<>();
		// The pig's type by id: 26.3 moved the entity type constants out of EntityType.
		EntityType<?> pigType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
		if (!(pigType.create(level, EntitySpawnReason.TRIGGERED) instanceof LivingEntity pig)) {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST damage events: could not create a pig");
			return;
		}
		pig.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		pig.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		target = pig;
		float health = pig.getHealth();
		if (pig.hurtServer(level, level.damageSources().generic(), REFUSED)) failed.add("the hit ALLOW_DAMAGE refused went through");
		if (pig.getHealth() != health) failed.add("the hit ALLOW_DAMAGE refused took the pig's health from " + health + " to " + pig.getHealth());
		if (afterDamage != null) failed.add("AFTER_DAMAGE ran for the refused hit: " + afterDamage);
		if (!pig.hurtServer(level, level.damageSources().fallingBlock(null), DEALT)) failed.add("the falling block's hit didn't go through");
		if (pig.getHealth() >= health) failed.add("the falling block's hit left the pig's health at " + pig.getHealth());
		String expected = DEALT + "/" + DEALT * 0.75F;
		if (!expected.equals(afterDamage)) failed.add("AFTER_DAMAGE reported " + afterDamage + ", not " + expected);
		target = null;
		pig.discard();
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST damage events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST damage events: {}", String.join("; ", failed));
		}
	}
}
