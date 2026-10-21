package io.github.cmdralph.fabricated.neoforge.objectbuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Native {@code fabric-object-builder-api-v1}, shared part: what upstream writes straight into Minecraft's tables
 * (default attributes, spawn placements) is collected here and handed to NeoForge, once Fabric's initializers have
 * registered their content. Upstream's mixins make those tables mutable, which NeoForge's patches conflict with.
 *
 * <p>On a dedicated server the initializers run in NeoForge's registration events, and everything goes through the
 * events NeoForge posts after every {@code RegisterEvent}: {@link EntityAttributeCreationEvent},
 * {@link EntityAttributeModificationEvent} (where Fabric's {@code FabricDefaultAttributeRegistry.MODIFY} runs on 26.x,
 * as upstream runs it just before registries freeze), then {@link RegisterSpawnPlacementsEvent}. On a client they run
 * later, in {@code Minecraft}'s constructor (CORPUS-16), and {@link #registerLate()} hands everything over there, as
 * NeoForge applies those events.
 */
public final class ObjectBuilderBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	/** Fabric's default attribute registrations (the map upstream's {@code DefaultAttributesAccessor} exposes). */
	private static final DefaultAttributeMap DEFAULT_ATTRIBUTES = new DefaultAttributeMap();
	/** Registrations for entity types that already have attributes (vanilla's or a NeoForge mod's). */
	private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier> OVERRIDES = new LinkedHashMap<>();
	private static final List<Placement<?>> PLACEMENTS = new ArrayList<>();
	/** Default attributes from entity type builders, built when NeoForge builds the attribute tables. */
	private static final Map<EntityType<? extends LivingEntity>, Supplier<AttributeSupplier.Builder>> BUILDER_ATTRIBUTES = new LinkedHashMap<>();

	private record Placement<T extends Entity>(EntityType<T> type, SpawnPlacementType placementType, Heightmap.Types heightmap,
			SpawnPlacements.SpawnPredicate<T> predicate) {
		void register(RegisterSpawnPlacementsEvent event) {
			event.register(type, placementType, heightmap, predicate, RegisterSpawnPlacementsEvent.Operation.REPLACE);
		}

		/** Vanilla's registration, which refuses a type that has a placement already, as on Fabric. */
		@SuppressWarnings({"unchecked", "rawtypes"})
		void registerInVanilla() {
			SpawnPlacements.register((EntityType) type, placementType, heightmap, (SpawnPlacements.SpawnPredicate) predicate);
		}
	}

	/** Changes the default attributes of the matching entity types, each through a builder holding the current ones. */
	@FunctionalInterface
	public interface AttributeModifier {
		void modify(Predicate<EntityType<? extends LivingEntity>> types, BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> consumer);
	}

	private static Consumer<AttributeModifier> fabricModify = modifier -> {
	};

	private ObjectBuilderBridge() {
	}

	/**
	 * @param initialized  whether Fabric's initializers have run (when NeoForge's events come; on a client they
	 *                     haven't, and {@link #registerLate()} hands everything over)
	 * @param fabricModify runs Fabric's attribute modification event with the given modifier (26.x; the 1.21.11 API
	 *                     has no such event)
	 */
	public static void register(IEventBus modBus, BooleanSupplier initialized, Consumer<AttributeModifier> fabricModify) {
		ObjectBuilderBridge.fabricModify = fabricModify;
		modBus.addListener(EventPriority.LOW, EntityAttributeCreationEvent.class, e -> {
			if (initialized.getAsBoolean()) createAttributes(e);
		});
		modBus.addListener(EventPriority.LOW, EntityAttributeModificationEvent.class, e -> {
			if (initialized.getAsBoolean()) modifyAttributes(e);
		});
		modBus.addListener(EventPriority.LOW, RegisterSpawnPlacementsEvent.class, e -> {
			if (!initialized.getAsBoolean()) return;
			synchronized (PLACEMENTS) {
				PLACEMENTS.forEach(p -> p.register(e));
			}
		});
	}

	/**
	 * Hands NeoForge what Fabric's initializers registered after its registration events (on a client, where they run
	 * in {@code Minecraft}'s constructor), as NeoForge applies those events: default attributes go into its attribute
	 * table ({@code CommonHooks.modifyAttributes}, read in NeoForge's source), spawn placements into vanilla's, through
	 * vanilla's own registration, which upstream calls.
	 */
	public static void registerLate() {
		Map<EntityType<? extends LivingEntity>, AttributeSupplier> table = neoForgeAttributes();
		if (table != null) {
			createAttributes(new EntityAttributeCreationEvent(table));
			Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> changes = new HashMap<>();
			modifyAttributes(new EntityAttributeModificationEvent(changes));
			changes.forEach((type, change) -> {
				AttributeSupplier current = DefaultAttributes.getSupplier(type);
				AttributeSupplier.Builder builder = current != null ? new AttributeSupplier.Builder(current) : AttributeSupplier.builder();
				builder.combine(change);
				table.put(type, builder.build());
			});
		}
		synchronized (PLACEMENTS) {
			for (Placement<?> placement : PLACEMENTS) {
				try {
					placement.registerInVanilla();
				} catch (IllegalStateException e) {
					LOGGER.warn("Spawn placement for {} not registered: {}", BuiltInRegistries.ENTITY_TYPE.getKey(placement.type()), e.getMessage());
				}
			}
		}
	}

	/** NeoForge's table of default attributes beyond vanilla's ({@code CommonHooks.FORGE_ATTRIBUTES}), or null. */
	@SuppressWarnings("unchecked")
	private static @Nullable Map<EntityType<? extends LivingEntity>, AttributeSupplier> neoForgeAttributes() {
		try {
			Field field = CommonHooks.class.getDeclaredField("FORGE_ATTRIBUTES");
			field.setAccessible(true);
			return (Map<EntityType<? extends LivingEntity>, AttributeSupplier>) field.get(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Default attributes Fabric mods registered can't be handed to NeoForge: its attribute table isn't accessible", e);
			return null;
		}
	}

	/** Backs {@code DefaultAttributesAccessor.getRegistry()}, which Fabric's {@code FabricDefaultAttributeRegistry} writes to. */
	public static Map<EntityType<? extends LivingEntity>, AttributeSupplier> defaultAttributes() {
		return DEFAULT_ATTRIBUTES;
	}

	/**
	 * Vanilla's own velocity flag on the entity type builder (Minecraft 26.3+), which {@code EntityType.trackDeltas()}
	 * reads there; null before 26.3. The builder is unobfuscated on 26.x, so the field keeps its name.
	 */
	private static final Field VANILLA_TRACK_DELTAS = trackDeltasField();

	private static Field trackDeltasField() {
		try {
			Field field = EntityType.Builder.class.getDeclaredField("trackDeltas");
			if (field.getType() != boolean.class) return null;
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			return null; // before 26.3
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Vanilla's velocity flag on EntityType.Builder can't be set; Fabric's alwaysUpdateVelocity uses NeoForge's", e);
			return null;
		}
	}

	/**
	 * Fabric's {@code alwaysUpdateVelocity}. Up to 26.2, NeoForge's {@code setShouldReceiveVelocityUpdates} backs
	 * {@code trackDeltas()}; from 26.3 vanilla has its own flag, and NeoForge's method backs {@code hasUpdateInterval()}
	 * instead, so only the vanilla flag is set there.
	 */
	public static <T extends Entity> EntityType.Builder<T> setAlwaysUpdateVelocity(EntityType.Builder<T> builder, boolean alwaysUpdateVelocity) {
		if (VANILLA_TRACK_DELTAS == null) return builder.setShouldReceiveVelocityUpdates(alwaysUpdateVelocity);
		try {
			VANILLA_TRACK_DELTAS.setBoolean(builder, alwaysUpdateVelocity);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Can't set the entity type builder's velocity flag", e);
		}
		return builder;
	}

	/** Fabric's {@code defaultAttributes} on an entity type builder. */
	public static void addDefaultAttributes(EntityType<? extends LivingEntity> type, Supplier<AttributeSupplier.Builder> attributes) {
		synchronized (DEFAULT_ATTRIBUTES) {
			BUILDER_ATTRIBUTES.put(type, attributes);
		}
	}

	/** Fabric's {@code spawnPlacement}/{@code spawnRestriction} on an entity type builder. */
	public static <T extends Entity> void addSpawnPlacement(EntityType<T> type, SpawnPlacementType placementType, Heightmap.Types heightmap,
			SpawnPlacements.SpawnPredicate<T> predicate) {
		synchronized (PLACEMENTS) {
			PLACEMENTS.add(new Placement<>(type, placementType, heightmap, predicate));
		}
	}

	private static void createAttributes(EntityAttributeCreationEvent event) {
		synchronized (DEFAULT_ATTRIBUTES) {
			// As upstream, the builder's attributes come first and a direct registration for the same type replaces them.
			BUILDER_ATTRIBUTES.forEach((type, attributes) -> {
				if (!DEFAULT_ATTRIBUTES.containsKey(type)) DEFAULT_ATTRIBUTES.put(type, attributes.get().build());
			});
			DEFAULT_ATTRIBUTES.forEach((type, supplier) -> {
				if (DefaultAttributes.hasSupplier(type)) {
					// NeoForge refuses a second creation; applied as a modification below instead.
					OVERRIDES.put(type, supplier);
				} else {
					event.put(type, supplier);
				}
			});
		}
	}

	private static void modifyAttributes(EntityAttributeModificationEvent event) {
		// What each type's attributes will be once NeoForge applies this event, so later changes build on earlier ones.
		Map<EntityType<? extends LivingEntity>, AttributeSupplier> current = new HashMap<>();
		synchronized (DEFAULT_ATTRIBUTES) {
			OVERRIDES.forEach((type, supplier) -> {
				apply(event, type, DefaultAttributes.getSupplier(type), supplier);
				current.put(type, supplier);
			});
			DEFAULT_ATTRIBUTES.closed = true;
		}

		fabricModify.accept((entityTypePredicate, consumer) -> {
			for (EntityType<? extends LivingEntity> type : event.getTypes()) {
				if (!entityTypePredicate.test(type)) continue;
				AttributeSupplier before = current.computeIfAbsent(type, DefaultAttributes::getSupplier);
				AttributeSupplier.Builder builder = new AttributeSupplier.Builder(before);
				consumer.accept(type, builder);
				AttributeSupplier after = builder.build();
				apply(event, type, before, after);
				current.put(type, after);
			}
		});
	}

	/** Hands NeoForge every attribute that {@code after} adds to or changes from {@code before}. */
	private static void apply(EntityAttributeModificationEvent event, EntityType<? extends LivingEntity> type, AttributeSupplier before, AttributeSupplier after) {
		BuiltInRegistries.ATTRIBUTE.listElements().forEach(attribute -> {
			Holder<Attribute> holder = attribute;
			if (!after.hasAttribute(holder)) return;
			double value = after.getBaseValue(holder);
			if (before == null || !before.hasAttribute(holder) || before.getBaseValue(holder) != value) {
				event.add(type, holder, value);
			}
		});
	}

	/** Warns about registrations that come after NeoForge has built the attribute tables, which can't take effect. */
	private static final class DefaultAttributeMap extends LinkedHashMap<EntityType<? extends LivingEntity>, AttributeSupplier> {
		private boolean closed;

		@Override
		public synchronized AttributeSupplier put(EntityType<? extends LivingEntity> key, AttributeSupplier value) {
			if (closed) {
				LOGGER.warn("Default attributes for {} were registered after NeoForge built the attribute tables; they are ignored. "
						+ "Register them in the mod's initializer.", BuiltInRegistries.ENTITY_TYPE.getKey(key));
			}
			return super.put(key, value);
		}
	}
}
