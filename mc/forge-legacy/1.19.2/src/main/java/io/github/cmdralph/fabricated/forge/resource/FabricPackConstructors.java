package io.github.cmdralph.fabricated.forge.resource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * A pack's constructor a Fabric mod made, as vanilla 1.19.2 types it: Fabric API's resource loader makes the packs of
 * the Fabric mods with a lambda of {@code Pack.PackConstructor}, whose one method vanilla's takes seven arguments. Forge
 * 43 made that method a default calling a method of its own taking an eighth (whether the pack is hidden), which is
 * the one its {@code Pack.create} calls: the lambda doesn't implement it, and the call threw {@code AbstractMethodError}
 * as the server started. Such a constructor is called by vanilla's method; a pack it makes isn't hidden, as on Fabric.
 */
public final class FabricPackConstructors {
	private static final Class<?>[] FORGE_PARAMETERS = {String.class, Component.class, boolean.class, Supplier.class, PackMetadataSection.class,
			Pack.Position.class, PackSource.class, boolean.class};
	/** Whether a constructor's class implements Forge's method. */
	private static final ClassValue<Boolean> IMPLEMENTS_FORGES = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				Method method = type.getMethod("create", FORGE_PARAMETERS);
				return !Modifier.isAbstract(method.getModifiers());
			} catch (NoSuchMethodException e) {
				return false;
			}
		}
	};

	private FabricPackConstructors() {
	}

	/** Whether the constructor implements vanilla's method alone. */
	public static boolean vanillaOnly(Pack.PackConstructor constructor) {
		return !IMPLEMENTS_FORGES.get(constructor.getClass());
	}

	/** The pack, by vanilla's method. */
	@SuppressWarnings("deprecation")
	public static Pack create(Pack.PackConstructor constructor, String id, Component title, boolean required, Supplier<PackResources> resources,
			PackMetadataSection metadata, Pack.Position position, PackSource source) {
		return constructor.create(id, title, required, resources, metadata, position, source);
	}
}
