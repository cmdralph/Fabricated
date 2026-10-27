package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.OptionalInt;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.menu.MenuOpening;
import net.fabricmc.fabric.api.screenhandler.v1.FabricScreenHandlerFactory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/**
 * fabric-screen-handler-api-v1 on NeoForge's menu opening: the player opening a menu, for the opening data of Fabric's extended
 * menu providers (see {@link MenuOpening}), and Fabric's {@code shouldCloseCurrentScreen} where NeoForge asks the
 * provider whether to close the screen the client has open.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMenuMixin {
	private static final String OPEN_MENU = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;";

	@WrapMethod(method = OPEN_MENU)
	private OptionalInt fabricated$trackOpener(@Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData,
			Operation<OptionalInt> original) {
		return MenuOpening.opening((ServerPlayer) (Object) this, () -> original.call(provider, extraData));
	}

	@WrapOperation(method = OPEN_MENU, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/MenuProvider;shouldTriggerClientSideContainerClosingOnOpen()Z"))
	private boolean fabricated$shouldCloseCurrentScreen(MenuProvider provider, Operation<Boolean> original) {
		return original.call(provider) && ((FabricScreenHandlerFactory) provider).shouldCloseCurrentScreen();
	}
}
