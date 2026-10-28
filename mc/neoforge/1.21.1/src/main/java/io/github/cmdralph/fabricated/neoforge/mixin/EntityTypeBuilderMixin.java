package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;

import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.fabricmc.fabric.impl.object.builder.FabricEntityTypeImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Makes {@link EntityType.Builder} Fabric's {@link FabricEntityType.Builder} (1.21.1). Fabric's velocity flag maps onto
 * the builder's existing setting (see {@link ObjectBuilderBridge#setAlwaysUpdateVelocity}); the living and mob settings
 * (default attributes, spawn placement) are handed on when the type is built, as upstream does. As upstream, Fabric's
 * {@code build()} builds without an id, skipping the data fixer lookup an id is for.
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
		return ObjectBuilderBridge.setAlwaysUpdateVelocity(fabricated$self(), alwaysUpdateVelocity);
	}

	@Shadow
	public abstract EntityType<T> build(String id);

	@Override
	public EntityType<T> build() {
		return build(null);
	}

	@WrapOperation(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;fetchChoiceType(Lcom/mojang/datafixers/DSL$TypeReference;Ljava/lang/String;)Lcom/mojang/datafixers/types/Type;"))
	private @Nullable Type<?> fabricated$allowNullId(DSL.TypeReference reference, @Nullable String id, Operation<Type<?>> original) {
		return id == null ? null : original.call(reference, id);
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
	private void fabricated$applyChildBuilders(String id, CallbackInfoReturnable<EntityType<T>> cir) {
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
