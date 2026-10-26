package io.github.cmdralph.fabricated.forge.objectbuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraftforge.event.entity.EntityAttributeModificationEvent;

/**
 * Fabric API's {@code FabricDefaultAttributeRegistry.MODIFY} (fabric-object-builder-api-v1 on 26.x, passed through) on
 * Forge. Upstream runs it as the built-in registries freeze, which Forge does at bootstrap, before any Fabric mod's
 * entrypoint has registered a listener, so it reached none. It runs in Forge's {@link EntityAttributeModificationEvent}
 * instead: after every registration, and before Forge builds the attributes the game reads from the default ones the
 * event changes. The plan leaves upstream's hook out, so it runs once.
 */
public final class DefaultAttributeModify {
	/** Upstream's implementation, which runs the event on the entity types with default attributes; absent without Fabric API, and before 26.x. */
	private static final String IMPL = "net.fabricmc.fabric.impl.object.builder.FabricDefaultAttributeRegistryImpl";

	private DefaultAttributeModify() {
	}

	public static void register() {
		EntityAttributeModificationEvent.BUS.addListener(event -> run());
	}

	private static void run() {
		Method invokeModify;
		try {
			invokeModify = Class.forName(IMPL, false, DefaultAttributeModify.class.getClassLoader()).getMethod("invokeModify");
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return;
		}
		try {
			invokeModify.invoke(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Fabric's default attribute modifications can't run", e);
		} catch (InvocationTargetException e) {
			// A listener's failure, which upstream lets through as the registries freeze.
			if (e.getCause() instanceof RuntimeException failure) throw failure;
			if (e.getCause() instanceof Error failure) throw failure;
			throw new IllegalStateException("Fabric's default attribute modifications failed", e.getCause());
		}
	}
}
