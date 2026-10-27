package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.class_2248;
import net.minecraft.class_322;

public interface ColorProviderRegistry<T, Provider> {
	ColorProviderRegistry<class_2248, class_322> BLOCK = null;

	Provider get(T object);
}
