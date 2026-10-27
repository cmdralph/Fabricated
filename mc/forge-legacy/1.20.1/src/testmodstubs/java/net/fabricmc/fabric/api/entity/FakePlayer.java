package net.fabricmc.fabric.api.entity;

import net.minecraft.class_3218;
import net.minecraft.class_3222;

public class FakePlayer extends class_3222 {
	private FakePlayer() {
	}

	public static FakePlayer get(class_3218 world) {
		throw new AssertionError("stand-in");
	}
}
