package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Makes item properties Fabric's {@link FabricItem.Properties} (26.x): the item id, and setting the model id. */
@Mixin(Item.Properties.class)
abstract class ItemPropertiesMixin implements FabricItem.Properties {
	@Shadow
	@Final
	@Mutable
	private DependantName<Item, Identifier> model;

	@Shadow
	private @Nullable ResourceKey<Item> id;

	@Override
	public Item.Properties modelId(Identifier modelId) {
		this.model = DependantName.fixed(modelId);
		return (Item.Properties) (Object) this;
	}

	@Override
	public @Nullable ResourceKey<Item> itemId() {
		return this.id;
	}
}
