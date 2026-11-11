package io.github.cmdralph.fabricated.core.bytecode;

/**
 * Whether a block keeps the platform's default of an extension method, for the calls SEAM-3 puts back
 * ({@link VanillaCalls}). Its methods are copied into the game class put back in, as private helpers, so they name only
 * the JDK's classes. Each class of the object, its superclasses and the interfaces they implement that extend the
 * extension's interface are asked whether they declare the method ({@code getDeclaredMethod}), so only their own
 * methods' types are loaded: {@code Class.getMethod} loads the types of every public method of the classes and
 * interfaces, and Forge 66's extensions name client classes, absent on a dedicated server. Where it can't tell (a class
 * it can't load), the block is taken to override it, and the platform's call is made.
 */
final class KeepsDefault {
	private KeepsDefault() {
	}

	/** Whether neither the state's class nor the block's declares {@code name} over the extensions' interfaces. */
	static boolean keepsDefault(Object state, Object block, Class<?> stateExtension, Class<?> blockExtension, String name, Class<?>[] stateParams,
			Class<?>[] blockParams) {
		try {
			return !overrides(state.getClass(), stateExtension, name, stateParams) && !overrides(block.getClass(), blockExtension, name, blockParams);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** Whether the class, a superclass or an interface they implement that extends {@code extension} declares the method. */
	static boolean overrides(Class<?> type, Class<?> extension, String name, Class<?>[] params) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			if (declares(c, name, params)) return true;
			for (Class<?> itf : c.getInterfaces()) {
				if (itf != extension && extension.isAssignableFrom(itf) && overrides(itf, extension, name, params)) return true;
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
