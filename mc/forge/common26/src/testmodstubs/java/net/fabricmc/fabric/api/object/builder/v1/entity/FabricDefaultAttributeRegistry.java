package net.fabricmc.fabric.api.object.builder.v1.entity;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Stand-in for the part of fabric-object-builder-api-v1's {@code FabricDefaultAttributeRegistry} the forge test mod uses
 * (signatures as upstream's on 26.1.2, 26.2 and 26.3): Fabricated passes the module through, so the test mod compiles
 * against this and runs against Fabric API's jar. Never packaged.
 */
public final class FabricDefaultAttributeRegistry {
	public static final Event<ModifyDefaultAttribute> MODIFY = null;

	private FabricDefaultAttributeRegistry() {
	}

	public interface ModifyContext {
		void modify(Predicate<EntityType<? extends LivingEntity>> entityTypePredicate, ModifyConsumer consumer);

		default void modify(EntityType<? extends LivingEntity> entityType, ModifyConsumer consumer) {
			modify(Predicate.isEqual(entityType), consumer);
		}
	}

	@FunctionalInterface
	public interface ModifyDefaultAttribute {
		void modify(ModifyContext context);
	}

	@FunctionalInterface
	public interface ModifyConsumer {
		void accept(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder builder);
	}
}
