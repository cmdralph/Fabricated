package net.fabricmc.fabric.api.client.particle.v1;

import java.util.List;

import net.minecraft.class_1058;
import net.minecraft.class_1059;
import net.minecraft.class_4002;

public interface FabricSpriteProvider extends class_4002 {
	class_1059 getAtlas();

	List<class_1058> getSprites();
}
