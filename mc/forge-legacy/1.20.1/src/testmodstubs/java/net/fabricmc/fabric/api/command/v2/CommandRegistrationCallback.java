package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.class_2168;
import net.minecraft.class_2170;
import net.minecraft.class_7157;

@FunctionalInterface
public interface CommandRegistrationCallback {
	Event<CommandRegistrationCallback> EVENT = null;

	void register(CommandDispatcher<class_2168> dispatcher, class_7157 registryAccess, class_2170.class_5364 environment);
}
