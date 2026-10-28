/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.api.screenhandler.v1;

import io.github.cmdralph.fabricated.neoforge.menu.MenuOpening;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * An extension of {@code NamedScreenHandlerFactory} that can write additional data to a screen opening packet.
 * This is used for {@linkplain ExtendedScreenHandlerType extended screen handlers}.
 *
 * @see ExtendedScreenHandlerType usage examples
 */
public interface ExtendedScreenHandlerFactory<D> extends MenuProvider {
	/**
	 * Writes additional server -&gt; client screen opening data to the buffer.
	 *
	 * @param player the player that is opening the screen
	 * @return the screen opening data
	 */
	D getScreenOpeningData(ServerPlayer player);

	/**
	 * Fabricated: sends {@link #getScreenOpeningData} with NeoForge's menu opening data, where the client's
	 * {@link ExtendedScreenHandlerType} reads it back.
	 */
	@Override
	@SuppressWarnings("unchecked")
	default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
		if (!(menu.getType() instanceof ExtendedScreenHandlerType<?, ?> type)) {
			throw new IllegalArgumentException("[Fabric] Non-extended menu " + BuiltInRegistries.MENU.getKey(menu.getType())
					+ " must not be opened with an ExtendedScreenHandlerFactory!");
		}
		ServerPlayer player = MenuOpening.opener();
		// As on Fabric: the provider can reach the menu through the player while it writes the data.
		player.containerMenu = menu;
		MenuOpening.write(buffer, (StreamCodec<? super RegistryFriendlyByteBuf, D>) type.getPacketCodec(), getScreenOpeningData(player));
	}
}
