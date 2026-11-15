package io.github.cmdralph.fabricated.forge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import net.minecraftforge.fml.ModList;

/**
 * Forge's mod list, as the in-game mod reads it on every Forge version: Forge 64 and later (Minecraft 26.x) made
 * {@code ModList}'s methods static, where Forge 47, 52 and 61 reach them through {@code ModList.get()}, so neither call
 * compiles on both.
 */
public final class ForgeModList {
	private ForgeModList() {
	}

	/** {@code ModList.getModContainerById} as {@code (String)Optional}. */
	public static MethodHandle containerById() throws ReflectiveOperationException {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		Method byId = ModList.class.getMethod("getModContainerById", String.class);
		MethodHandle handle = lookup.unreflect(byId);
		if (!Modifier.isStatic(byId.getModifiers())) {
			handle = MethodHandles.collectArguments(handle, 0, lookup.unreflect(ModList.class.getMethod("get")));
		}
		return handle.asType(MethodType.methodType(Optional.class, String.class));
	}
}
