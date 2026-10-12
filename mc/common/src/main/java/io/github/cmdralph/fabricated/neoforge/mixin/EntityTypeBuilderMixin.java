package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Objects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.fabricmc.fabric.impl.object.builder.FabricEntityTypeImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Makes {@link EntityType.Builder} Fabric's {@link FabricEntityType.Builder}. Fabric's two flags map onto the
 * builder methods NeoForge already adds; the living and mob settings (default attributes, spawn placement) are handed
 * on when the type is built, as upstream does. Only observes {@code build}'s result; no Minecraft code is changed.
 */
@Mixin(EntityType.Builder.class)
abstract class EntityTypeBuilderMixin<T extends Entity> implements FabricEntityType.Builder<T>, FabricEntityTypeImpl.Builder {
	@Unique
	private FabricEntityTypeImpl.Builder.Living<? extends LivingEntity> fabricated$livingBuilder;
	@Unique
	private FabricEntityTypeImpl.Builder.Mob<? extends net.minecraft.world.entity.Mob> fabricated$mobBuilder;

	@Unique
	@SuppressWarnings("unchecked")
	private EntityType.Builder<T> fabricated$self() {
		return (EntityType.Builder<T>) (Object) this;
	}

	@Override
	public EntityType.Builder<T> alwaysUpdateVelocity(boolean alwaysUpdateVelocity) {
		return fabricated$self().setShouldReceiveVelocityUpdates(alwaysUpdateVelocity);
	}

	@Override
	public EntityType.Builder<T> canPotentiallyExecuteCommands(boolean canPotentiallyExecuteCommands) {
		return fabricated$self().setOnlyOpCanSetNbt(canPotentiallyExecuteCommands);
	}

	@Override
	public void fabric_setLivingEntityBuilder(FabricEntityTypeImpl.Builder.Living<? extends LivingEntity> livingBuilder) {
		this.fabricated$livingBuilder = Objects.requireNonNull(livingBuilder, "Cannot set null living entity builder");
	}

	@Override
	public void fabric_setMobEntityBuilder(FabricEntityTypeImpl.Builder.Mob<? extends net.minecraft.world.entity.Mob> mobBuilder) {
		this.fabricated$mobBuilder = Objects.requireNonNull(mobBuilder, "Cannot set null mob entity builder");
	}

	@Inject(method = "build", at = @At("RETURN"))
	private void fabricated$applyChildBuilders(ResourceKey<EntityType<?>> key, CallbackInfoReturnable<EntityType<T>> cir) {
		if (fabricated$livingBuilder != null) {
			fabricated$livingBuilder.onBuild(fabricated$castLiving(cir.getReturnValue()));
		}
		if (fabricated$mobBuilder != null) {
			fabricated$mobBuilder.onBuild(fabricated$castMob(cir.getReturnValue()));
		}
	}

	@Unique
	@SuppressWarnings("unchecked")
	private static <E extends LivingEntity> EntityType<E> fabricated$castLiving(EntityType<?> type) {
		return (EntityType<E>) type;
	}

	@Unique
	@SuppressWarnings("unchecked")
	private static <E extends net.minecraft.world.entity.Mob> EntityType<E> fabricated$castMob(EntityType<?> type) {
		return (EntityType<E>) type;
	}
}
