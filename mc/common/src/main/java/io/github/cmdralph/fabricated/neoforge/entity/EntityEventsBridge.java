package io.github.cmdralph.fabricated.neoforge.entity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Native {@code fabric-entity-events-v1}: fires the vendored Fabric API events from NeoForge's entity events instead
 * of upstream's mixins, which conflict with NeoForge's patches. All events are server-side, as upstream.
 *
 * <p>Not fired, because NeoForge has no matching hook: {@code EntityElytraEvents}, and the sleep events other than
 * {@code ALLOW_SLEEPING}, {@code STOP_SLEEPING} (players) and {@code ALLOW_SETTING_SPAWN}. Listed in
 * docs/COMPATIBILITY.md together with the smaller differences noted below.
 */
public final class EntityEventsBridge {
	/**
	 * The entity being hit and the hit's damage, for Fabric's {@code ALLOW_DEATH}: NeoForge's death event has no amount
	 * and fires during the hit, after {@link LivingDamageEvent.Pre} and before {@link LivingDamageEvent.Post}. Server
	 * thread only; cleared when the hit ends, so no entity is held on to.
	 */
	private static LivingEntity damaged;
	private static float damage;
	/** Entities leaving for another level, by UUID: the copy that arrives is a new entity with the same UUID. */
	private static final Map<UUID, Travel> TRAVELLING = new ConcurrentHashMap<>();

	private record Travel(Entity original, ServerLevel origin) {
	}

	/** Effects applied outside a command (Fabricated can't tell which command, if any, added an effect). */
	private static final EffectEventContext CONTEXT = new EffectEventContext() {
		@Override
		public boolean isFromCommand() {
			return false;
		}

		@Override
		public String commandName() {
			return null;
		}
	};

	private EntityEventsBridge() {
	}

	public static void register() {
		var bus = NeoForge.EVENT_BUS;

		// Damage: Fabric's ALLOW_DAMAGE can cancel the hit; AFTER_DAMAGE reports what was taken.
		bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, e -> {
			LivingEntity entity = e.getEntity();
			if (entity.level() instanceof ServerLevel && !ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(entity, e.getSource(), e.getAmount())) {
				e.setCanceled(true);
			}
		});
		bus.addListener(LivingDamageEvent.Pre.class, e -> {
			if (e.getEntity().level() instanceof ServerLevel) {
				damaged = e.getEntity();
				damage = e.getOriginalDamage();
			}
		});
		bus.addListener(LivingDamageEvent.Post.class, e -> {
			LivingEntity entity = e.getEntity();
			if (!(entity.level() instanceof ServerLevel)) return;
			if (damaged == entity) damaged = null;
			// As upstream: not fired for the hit that kills, and "damage taken" is after shields but before armor.
			if (entity.isDeadOrDying()) return;
			float blocked = e.getBlockedDamage();
			ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(entity, e.getSource(), e.getOriginalDamage(),
					Math.max(0f, e.getOriginalDamage() - blocked), blocked > 0);
		});

		// Death: ALLOW_DEATH first (HIGHEST, so a veto stops NeoForge mods from seeing the death); AFTER_DEATH and
		// AFTER_KILLED_OTHER_ENTITY last, and only when nobody cancelled.
		bus.addListener(EventPriority.HIGHEST, LivingDeathEvent.class, e -> {
			LivingEntity entity = e.getEntity();
			if (!(entity.level() instanceof ServerLevel)) return;
			float amount = damaged == entity ? damage : 0f;
			// Players' ALLOW_DEATH listeners run too: Fabric API forwards this event to them.
			if (!ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(entity, e.getSource(), amount)) e.setCanceled(true);
		});
		bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, e -> {
			LivingEntity entity = e.getEntity();
			if (e.isCanceled() || !(entity.level() instanceof ServerLevel level)) return;
			ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(entity, e.getSource());
			Entity killer = e.getSource().getEntity();
			if (killer != null) ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.invoker().afterKilledOtherEntity(level, killer, entity, e.getSource());
		});

		bus.addListener(LivingConversionEvent.Post.class, e -> {
			if (e.getEntity() instanceof Mob previous && e.getOutcome() instanceof Mob converted && previous.level() instanceof ServerLevel) {
				// NeoForge doesn't pass the conversion parameters on; these are vanilla's usual ones.
				ServerLivingEntityEvents.MOB_CONVERSION.invoker().onConversion(previous, converted, ConversionParams.single(previous, true, true));
			}
		});

		// Players.
		bus.addListener(PlayerEvent.Clone.class, e -> {
			if (e.getEntity() instanceof ServerPlayer newPlayer && e.getOriginal() instanceof ServerPlayer oldPlayer) {
				ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(oldPlayer, newPlayer, !e.isWasDeath());
				RESPAWNING.put(newPlayer.getUUID(), oldPlayer);
			}
		});
		bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
			if (!(e.getEntity() instanceof ServerPlayer newPlayer)) return;
			ServerPlayer oldPlayer = RESPAWNING.remove(newPlayer.getUUID());
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(oldPlayer != null ? oldPlayer : newPlayer, newPlayer, e.isEndConquered());
		});
		bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
			if (e.getEntity() instanceof ServerPlayer player) ServerPlayerEvents.JOIN.invoker().onJoin(player);
		});
		bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				RESPAWNING.remove(player.getUUID());
				ServerPlayerEvents.LEAVE.invoker().onLeave(player);
			}
		});

		// Changing level. Players have their own NeoForge event; other entities arrive as a new entity with the
		// same UUID, so the departure is remembered until that entity joins the destination.
		bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
			if (!(e.getEntity() instanceof ServerPlayer player)) return;
			MinecraftServer server = player.level().getServer();
			ServerLevel origin = server.getLevel(e.getFrom());
			ServerLevel destination = server.getLevel(e.getTo());
			if (origin != null && destination != null) {
				EntityEventsCompat.afterPlayerChange(player, origin, destination);
			}
		});
		bus.addListener(EventPriority.LOWEST, EntityTravelToDimensionEvent.class, e -> {
			Entity entity = e.getEntity();
			if (!e.isCanceled() && !(entity instanceof Player) && entity.level() instanceof ServerLevel origin) {
				TRAVELLING.put(entity.getUUID(), new Travel(entity, origin));
			}
		});
		bus.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, e -> {
			if (TRAVELLING.isEmpty() || !(e.getLevel() instanceof ServerLevel destination)) return;
			Entity entity = e.getEntity();
			Travel travel = TRAVELLING.get(entity.getUUID());
			if (travel == null || travel.original() == entity || travel.origin() == destination) return;
			TRAVELLING.remove(entity.getUUID());
			EntityEventsCompat.afterEntityChange(travel.original(), entity, travel.origin(), destination);
		});
		// A teleport fires both events within one call; anything still here (the copy was never created) would otherwise
		// keep its entity and level alive, across integrated-server sessions too.
		bus.addListener(ServerTickEvent.Post.class, e -> {
			if (!TRAVELLING.isEmpty()) TRAVELLING.clear();
		});
		bus.addListener(ServerStoppedEvent.class, e -> {
			TRAVELLING.clear();
			RESPAWNING.clear();
		});

		// Sleeping (the parts NeoForge has events for).
		bus.addListener(CanPlayerSleepEvent.class, e -> {
			Player.BedSleepingProblem problem = EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(e.getEntity(), e.getPos());
			if (problem != null) e.setProblem(problem);
		});
		bus.addListener(PlayerWakeUpEvent.class, e -> {
			Player player = e.getEntity();
			if (player.level() instanceof ServerLevel) {
				player.getSleepingPos().ifPresent(pos -> EntitySleepEvents.STOP_SLEEPING.invoker().onStopSleeping(player, pos));
			}
		});
		// Fabric's event is about sleeping in a bed; NeoForge's also fires for respawn anchors and /spawnpoint.
		bus.addListener(PlayerSetSpawnEvent.class, e -> {
			Player player = e.getEntity();
			BlockPos spawn = e.getNewSpawn();
			if (player.level() instanceof ServerLevel level && spawn != null && !e.isForced()
					&& level.getBlockState(spawn).getBlock() instanceof BedBlock
					&& !EntitySleepEvents.ALLOW_SETTING_SPAWN.invoker().allowSettingSpawn(player, spawn)) {
				e.setCanceled(true);
			}
		});

		registerMobEffects(bus);
	}

	private static final Map<UUID, ServerPlayer> RESPAWNING = new ConcurrentHashMap<>();

	private static void registerMobEffects(net.neoforged.bus.api.IEventBus bus) {
		bus.addListener(EventPriority.HIGH, MobEffectEvent.Applicable.class, e -> {
			LivingEntity entity = e.getEntity();
			if (!(entity.level() instanceof ServerLevel)) return;
			if (!ServerMobEffectEvents.ALLOW_ADD.invoker().allowAdd(e.getEffectInstance(), entity, CONTEXT)) {
				e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
			}
		});
		bus.addListener(EventPriority.LOWEST, MobEffectEvent.Applicable.class, e -> {
			if (e.getEntity().level() instanceof ServerLevel && e.getApplicationResult()) {
				ServerMobEffectEvents.BEFORE_ADD.invoker().beforeAdd(e.getEffectInstance(), e.getEntity(), CONTEXT);
			}
		});
		// Removal before the effect ends (milk, commands, mods): ALLOW_EARLY_REMOVE can keep it.
		bus.addListener(EventPriority.HIGH, MobEffectEvent.Remove.class, e -> {
			LivingEntity entity = e.getEntity();
			MobEffectInstance instance = e.getEffectInstance();
			if (instance != null && entity.level() instanceof ServerLevel
					&& !ServerMobEffectEvents.ALLOW_EARLY_REMOVE.invoker().allowEarlyRemove(instance, entity, CONTEXT)) {
				e.setCanceled(true);
			}
		});
		bus.addListener(EventPriority.LOWEST, MobEffectEvent.Remove.class, e -> {
			if (!e.isCanceled() && e.getEffectInstance() != null) removing(e.getEffectInstance(), e.getEntity());
		});
		bus.addListener(EventPriority.LOWEST, MobEffectEvent.Expired.class, e -> {
			if (!e.isCanceled() && e.getEffectInstance() != null) removing(e.getEffectInstance(), e.getEntity());
		});
	}

	/** BEFORE_REMOVE; AFTER_REMOVE comes from {@link #afterEffectsRemoved} once the effect is gone. */
	private static void removing(MobEffectInstance instance, LivingEntity entity) {
		if (entity.level() instanceof ServerLevel) ServerMobEffectEvents.BEFORE_REMOVE.invoker().beforeRemove(instance, entity, CONTEXT);
	}

	/**
	 * Fabric's {@code AFTER_ADD}, from {@code LivingEntity.onEffectAdded} as upstream: once a new effect is in place, also
	 * when force-added. NeoForge's added event comes before that, and for refreshed effects too. The API's own
	 * listeners call the effect's {@code FabricMobEffect} hooks.
	 */
	public static void afterEffectAdded(LivingEntity entity, MobEffectInstance instance) {
		if (entity.level() instanceof ServerLevel) ServerMobEffectEvents.AFTER_ADD.invoker().afterAdd(instance, entity, CONTEXT);
	}

	/** Fabric's {@code AFTER_REMOVE}, from {@code LivingEntity.onEffectsRemoved} as upstream: once the effects are gone. */
	public static void afterEffectsRemoved(LivingEntity entity, Collection<MobEffectInstance> instances) {
		if (!(entity.level() instanceof ServerLevel)) return;
		for (MobEffectInstance instance : instances) ServerMobEffectEvents.AFTER_REMOVE.invoker().afterRemove(instance, entity, CONTEXT);
	}
}
