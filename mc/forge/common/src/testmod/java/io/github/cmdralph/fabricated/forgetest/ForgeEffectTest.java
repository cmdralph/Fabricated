package io.github.cmdralph.fabricated.forgetest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The forge test mod's part for fabric-entity-events-v1's effect events' context (with Fabric API's jar only): a pig is
 * given an effect by the {@code effect} command, run as the server runs commands, and then one by code. The command's
 * effect must reach {@code ALLOW_ADD} from the {@code effect} command, and the code's from none. Upstream learns the
 * command in a hook into Brigadier, which Forge loads without mixins, so the in-game mod gives the context where
 * vanilla's command execution runs a command (ExecuteCommandEffectMixin).
 */
public final class ForgeEffectTest {
	/** The pig being given effects, and what {@code ALLOW_ADD} saw of each effect it was given. */
	private static volatile LivingEntity target;
	private static final List<String> seen = new CopyOnWriteArrayList<>();

	private ForgeEffectTest() {
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's entity events module. */
	public static void register() {
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, ctx) -> {
			if (entity == target) seen.add(ctx.isFromCommand() ? "from the " + ctx.commandName() + " command" : "from no command");
			return true;
		});
	}

	/** At the first server tick. */
	public static void check(MinecraftServer server) {
		ServerLevel level = server.overworld();
		// By id: 26.3 moved the entity type constants out of EntityType.
		EntityType<?> pigType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
		Holder<MobEffect> slowness = BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace("slowness")).orElse(null);
		if (!(pigType.create(level, EntitySpawnReason.TRIGGERED) instanceof LivingEntity pig) || slowness == null) {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST effect command context: could not create a pig or find slowness");
			return;
		}
		target = pig;
		// The command names the pig as @s, the source's entity: it needn't be in the world.
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(pig).withSuppressedOutput(),
				"effect give @s minecraft:speed 5");
		pig.addEffect(new MobEffectInstance(slowness, 100));
		target = null;
		List<String> expected = List.of("from the effect command", "from no command");
		if (expected.equals(seen)) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST effect command context OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST effect command context: ALLOW_ADD saw the pig's effects {}, not {}", seen, expected);
		}
	}
}
