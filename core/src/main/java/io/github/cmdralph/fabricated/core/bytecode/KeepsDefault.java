package io.github.cmdralph.fabricated.core.bytecode;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Whether a block keeps the platform's default of an extension method, for the calls SEAM-3 and SEAM-4 put back
 * ({@link VanillaCalls}), and whether an object's class keeps a vanilla class's method (SEAM-4: Forge's
 * {@code Player.hasCorrectToolForDrops}). Its methods are copied into the game class put back in, as private helpers, so
 * they name only the JDK's classes. Each class of the object, its superclasses below the one whose method it asks of and
 * the interfaces they implement that extend the extension's interface are asked whether they declare the method
 * ({@code getDeclaredMethod}), so only their own methods' types are loaded: {@code Class.getMethod} loads the types of
 * every public method of the classes and interfaces, and Forge 66's extensions name client classes, absent on a
 * dedicated server. Where it can't tell (a class it can't load), the object is taken to override it, and the platform's
 * call is made. The answer is asked once for each class: the game class keeps the answers ({@link #known}), one store
 * for each method it asks of, as a lookup that fails throws.
 */
final class KeepsDefault {
	private KeepsDefault() {
	}

	/** A store of the answers by class, for the game class to keep: its keys weak, so it keeps no class from unloading. */
	static Map<Class<?>, Boolean> known() {
		return Collections.synchronizedMap(new WeakHashMap<>());
	}

	/** Whether neither the state's class nor the block's declares {@code name} over the extensions' interfaces. */
	static boolean keepsDefault(Map<Class<?>, Boolean> known, Object state, Object block, Class<?> stateExtension, Class<?> blockExtension, String name,
			Class<?>[] stateParams, Class<?>[] blockParams) {
		try {
			return !overrides(known, state.getClass(), stateExtension, name, stateParams) && !overrides(known, block.getClass(), blockExtension, name, blockParams);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** Whether the object's class keeps {@code base}'s method: no class between them declares it again. */
	static boolean keepsMethod(Map<Class<?>, Boolean> known, Object object, Class<?> base, String name, Class<?>[] params) {
		try {
			return !overrides(known, object.getClass(), base, name, params);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** {@link #declaresOver}, from {@code known} where it was asked for the class before; true where it can't tell. */
	static boolean overrides(Map<Class<?>, Boolean> known, Class<?> type, Class<?> extension, String name, Class<?>[] params) {
		Boolean answer = known.get(type);
		if (answer == null) {
			try {
				answer = declaresOver(type, extension, name, params);
			} catch (RuntimeException | LinkageError e) {
				answer = true;
			}
			known.put(type, answer);
		}
		return answer;
	}

	/**
	 * Whether the class, a superclass below {@code extension} (where that is a class) or an interface they implement that
	 * extends {@code extension} declares the method.
	 */
	static boolean declaresOver(Class<?> type, Class<?> extension, String name, Class<?>[] params) {
		for (Class<?> c = type; c != null && c != extension; c = c.getSuperclass()) {
			if (declares(c, name, params)) return true;
			for (Class<?> itf : c.getInterfaces()) {
				if (itf != extension && extension.isAssignableFrom(itf) && declaresOver(itf, extension, name, params)) return true;
			}
		}
		return false;
	}

	static boolean declares(Class<?> c, String name, Class<?>[] params) {
		try {
			c.getDeclaredMethod(name, params);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}
}
