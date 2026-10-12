package net.fabricmc.fabric.mixin.object.builder;

import java.util.Map;

import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Not a mixin in Fabricated: Fabric's {@code FabricDefaultAttributeRegistry} writes its registrations here. Upstream
 * makes Minecraft's own table mutable; Fabricated collects them and hands them to NeoForge's
 * {@code EntityAttributeCreationEvent} instead (see {@link ObjectBuilderBridge}).
 */
public interface DefaultAttributesAccessor {
	static Map<EntityType<? extends LivingEntity>, AttributeSupplier> getRegistry() {
		return ObjectBuilderBridge.defaultAttributes();
	}
}
