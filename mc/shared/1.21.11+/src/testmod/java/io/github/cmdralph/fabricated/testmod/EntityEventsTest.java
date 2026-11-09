package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * CI test for Fabricated's native fabric-entity-events-v1: once the server has started, a pig is spawned, given an
 * effect, has it removed, is given one by the effect command and one more by code, is hurt, and is killed; the events
 * seen are logged as one marker line, and where the effects came from as another (EFFECT-1).
 */
public final class EntityEventsTest implements ModInitializer {
	private static final List<String> SEEN = new ArrayList<>();
	/** Where each effect the pig was given came from, as {@code ALLOW_ADD}'s context said. */
	private static final List<String> CONTEXTS = new ArrayList<>();
	private static final String NOT_FROM_THE_COMMAND = "the effect command's effect came from no command";
	private static LivingEntity subject;

	private static void seen(LivingEntity entity, String what) {
		if (entity == subject) SEEN.add(what);
	}

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			seen(entity, "allow_damage");
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> seen(entity, "after_damage"));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			seen(entity, "allow_death");
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> seen(entity, "after_death"));
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, ctx) -> {
			seen(entity, "allow_add");
			if (entity == subject) CONTEXTS.add(ctx.isFromCommand() ? "from the " + ctx.commandName() + " command" : "from no command");
			return true;
		});
		ServerMobEffectEvents.AFTER_ADD.register((effect, entity, ctx) -> seen(entity, "after_add"));
		ServerMobEffectEvents.BEFORE_REMOVE.register((effect, entity, ctx) -> seen(entity, "before_remove"));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			// Looked up by id: the EntityType constants moved to EntityTypes in 26.x.
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
			if (!(type.create(level, EntitySpawnReason.TRIGGERED) instanceof LivingEntity pig)) {
				TestMod.LOGGER.error("FABRICATED-TEST entity events: could not create a pig");
				return;
			}
			subject = pig;
			pig.setPos(0.5, level.getMaxY() - 10, 0.5);
			level.addFreshEntity(pig);
			pig.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
			pig.removeEffect(MobEffects.SPEED);
			checkEffectContext(server, pig);
			pig.hurtServer(level, level.damageSources().generic(), 1.0f);
			pig.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			TestMod.LOGGER.info("FABRICATED-TEST entity events seen: {}", String.join(",", SEEN));
			pig.discard();
		});
	}

	/**
	 * Gives the pig an effect with the effect command, run as the server runs commands, and then one by code: the
	 * command's must come from the effect command, and the code's, as the one before, from none, which also checks that
	 * the context ends with the command. Upstream sets it in a hook into Brigadier, which NeoForge loads without mixins,
	 * so Fabricated sets it where vanilla's command execution runs a command (ExecuteCommandEffectMixin).
	 */
	private static void checkEffectContext(MinecraftServer server, LivingEntity pig) {
		// The command names the pig as @s, the source's entity.
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(pig).withSuppressedOutput(),
				"effect give @s minecraft:jump_boost 5");
		pig.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100));
		List<String> expected = List.of("from no command", "from the effect command", "from no command");
		List<String> failed = new ArrayList<>();
		if (List.of("from no command", "from no command", "from no command").equals(CONTEXTS)) {
			failed.add(NOT_FROM_THE_COMMAND);
		} else if (!expected.equals(CONTEXTS)) {
			failed.add("ALLOW_ADD saw the pig's effects " + CONTEXTS + ", not " + expected);
		}
		TestMod.report("effect command context", failed, "fabric-entity-events-v1", Map.of(NOT_FROM_THE_COMMAND,
				"its source sets the context in a hook into Brigadier's ContextChain, a library NeoForge loads without mixins"));
	}
}
