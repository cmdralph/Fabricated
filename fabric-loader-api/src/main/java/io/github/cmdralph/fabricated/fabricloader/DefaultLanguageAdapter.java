package io.github.cmdralph.fabricated.fabricloader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * The {@code default} language adapter. Supported entrypoint notations:
 * <ul>
 *   <li>{@code pkg.Type} - instantiated through its public no-arg constructor;</li>
 *   <li>{@code pkg.Type::FIELD} - value of a static field;</li>
 *   <li>{@code pkg.Type::method} - a method adapted to the requested functional interface. Non-static
 *       methods are bound to a new instance created through the no-arg constructor.</li>
 * </ul>
 */
public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	private DefaultLanguageAdapter() {
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		String[] parts = value.split("::");
		if (parts.length > 2) {
			throw new LanguageAdapterException("Invalid entrypoint notation '" + value + "': more than one '::'");
		}

		Class<?> owner;
		try {
			owner = Class.forName(parts[0], true, FabricLoaderImpl.classLoaderFor(mod));
		} catch (ClassNotFoundException | LinkageError e) {
			throw new LanguageAdapterException("Could not load entrypoint class " + parts[0], e);
		}

		if (parts.length == 1) {
			if (!type.isAssignableFrom(owner)) {
				throw new LanguageAdapterException("Class " + owner.getName() + " cannot be cast to " + type.getName());
			}
			return type.cast(instantiate(owner));
		}

		String member = parts[1];
		List<Method> methods = new ArrayList<>();
		for (Method m : owner.getDeclaredMethods()) {
			if (m.getName().equals(member)) methods.add(m);
		}
		Field field = null;
		try {
			field = owner.getDeclaredField(member);
		} catch (NoSuchFieldException ignored) {
			// not a field reference
		}

		if (field != null && !methods.isEmpty()) {
			throw new LanguageAdapterException("Ambiguous entrypoint '" + value + "': both a field and a method are named " + member);
		}

		if (field != null) {
			if (!Modifier.isStatic(field.getModifiers())) {
				throw new LanguageAdapterException("Field " + value + " must be static");
			}
			if (!type.isAssignableFrom(field.getType())) {
				throw new LanguageAdapterException("Field " + value + " of type " + field.getType().getName() + " cannot be cast to " + type.getName());
			}
			try {
				field.setAccessible(true);
				return type.cast(field.get(null));
			} catch (IllegalAccessException | RuntimeException e) {
				throw new LanguageAdapterException("Could not read field " + value, e);
			}
		}

		if (methods.isEmpty()) {
			throw new LanguageAdapterException("Could not find field or method '" + member + "' in " + owner.getName());
		}
		if (methods.size() > 1) {
			throw new LanguageAdapterException("Found multiple methods named '" + member + "' in " + owner.getName());
		}
		if (!type.isInterface()) {
			throw new LanguageAdapterException("Cannot adapt method " + value + " to non-interface type " + type.getName());
		}

		Method method = methods.get(0);
		try {
			method.setAccessible(true);
			MethodHandle handle = MethodHandles.lookup().unreflect(method);
			if (!Modifier.isStatic(method.getModifiers())) {
				handle = handle.bindTo(instantiate(owner));
			}
			return MethodHandleProxies.asInterfaceInstance(type, handle);
		} catch (IllegalAccessException | RuntimeException e) {
			throw new LanguageAdapterException("Could not adapt method " + value + " to " + type.getName(), e);
		}
	}

	private static Object instantiate(Class<?> owner) throws LanguageAdapterException {
		try {
			Constructor<?> constructor = owner.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new LanguageAdapterException("Class " + owner.getName() + " has no no-argument constructor", e);
		} catch (InvocationTargetException e) {
			throw new LanguageAdapterException("Constructor of " + owner.getName() + " threw an exception", e.getCause());
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new LanguageAdapterException("Could not instantiate " + owner.getName(), e);
		}
	}
}
