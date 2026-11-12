package io.github.cmdralph.fabricated.core.bytecode;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

/**
 * Whether a block keeps the platform's default of an extension method, for the calls SEAM-3, SEAM-4, SEAM-6 and SEAM-8
 * put back ({@link VanillaCalls}), and whether an object's class keeps a vanilla class's method (SEAM-4: Forge's
 * {@code Player.hasCorrectToolForDrops}). Its methods are copied into the game class put back in, as private helpers, so
 * they name only the JDK's classes. Each class of the object, its superclasses below the one whose method it asks of and
 * the interfaces they implement that extend the extension's interface are asked whether they declare the method
 * ({@code getDeclaredMethod}), so only their own methods' types are loaded: {@code Class.getMethod} loads the types of
 * every public method of the classes and interfaces, and Forge 66's extensions name client classes, absent on a
 * dedicated server. Fabricated's default bridges, declared where a Fabric mod's interface brings a second default of the
 * method, don't count (SEAM-7, {@link #declares}). Where it can't tell (a class it can't load), the object is taken to
 * override it, and the platform's call is made. The answer is asked once for each class: the game class keeps the
 * answers ({@link #known}), one store for each method it asks of, as a lookup that fails throws. Once the classes were
 * asked of, the game class reads their answers first ({@link #asked}), so a check makes nothing: the lookup's arrays
 * only where a class is new. The store is a {@link ClassValue}, read without a lock (the server's and the client's
 * threads ask at once, every tick) and kept no longer than each class.
 */
final class KeepsDefault {
	private KeepsDefault() {
	}

	/**
	 * A store of the answers by class, for the game class to keep: a {@link ClassValue} giving each class a slot its answer
	 * goes into once asked, read without a lock or anything made, and kept no longer than the class. A class can't be a
	 * {@code ClassValue}'s and name only the JDK's classes, so {@code slots}, that subclass's bytes in the game class's
	 * package (ISO-8859-1), is defined beside it, a hidden class. Null where it can't be: each check then asks again.
	 */
	@SuppressWarnings("unchecked")
	static ClassValue<Object[]> known(String slots) {
		try {
			Class<?> type = MethodHandles.lookup().defineHiddenClass(slots.getBytes(StandardCharsets.ISO_8859_1), true).lookupClass();
			return (ClassValue<Object[]>) type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** The answer kept for the class: whether it declares the method; null where it wasn't asked of yet. */
	private static Boolean answer(ClassValue<Object[]> known, Class<?> type) {
		return known == null ? null : (Boolean) known.get(type)[0];
	}

	/** Keeps the class's answer; two threads asking at once keep the same one. */
	private static void keep(ClassValue<Object[]> known, Class<?> type, Boolean overrides) {
		if (known != null) known.get(type)[0] = overrides;
	}

	/** Whether neither the state's class nor the block's declares {@code name} over the extensions' interfaces. */
	static boolean keepsDefault(ClassValue<Object[]> known, Object state, Object block, Class<?> stateExtension, Class<?> blockExtension, String name,
			Class<?>[] stateParams, Class<?>[] blockParams) {
		try {
			return !overrides(known, state.getClass(), stateExtension, name, stateParams) && !overrides(known, block.getClass(), blockExtension, name, blockParams);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/**
	 * {@link #keepsDefault}, and the state's class keeps {@code base}'s {@code baseName} too: where the platform's default
	 * asks the block for what vanilla's call asks the state (Forge's sound type: vanilla's {@code state.getSoundType()},
	 * Forge's {@code block.getSoundType(state)}), a state class declaring vanilla's method again answers otherwise (SEAM-8).
	 * The state's class keeps one answer for both.
	 */
	static boolean keepsDefaultAndMethod(ClassValue<Object[]> known, Object state, Object block, Class<?> stateExtension, Class<?> blockExtension,
			String name, Class<?>[] stateParams, Class<?>[] blockParams, Class<?> base, String baseName, Class<?>[] baseParams) {
		try {
			return !overridesEither(known, state.getClass(), stateExtension, name, stateParams, base, baseName, baseParams)
					&& !overrides(known, block.getClass(), blockExtension, name, blockParams);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/**
	 * The extension's interface, by NeoForge's name or, where its loader has none, Forge's: where the game class can't
	 * tell its platform (SEAM-8: NeoForge's FlyingMob names nothing of NeoForge's), as {@code from}'s loader finds it. Null
	 * where it finds neither, which the checks then take for an override: the platform's call is made.
	 */
	static Class<?> type(Class<?> from, String neoForge, String forge) {
		ClassLoader loader = from.getClassLoader();
		try {
			return Class.forName(neoForge, false, loader);
		} catch (ClassNotFoundException | LinkageError e) {
			try {
				return Class.forName(forge, false, loader);
			} catch (ClassNotFoundException | LinkageError e2) {
				return null;
			}
		}
	}

	/** {@link #overrides}, where the class declares either method over its own type; true where it can't tell. */
	static boolean overridesEither(ClassValue<Object[]> known, Class<?> type, Class<?> extension, String name, Class<?>[] params, Class<?> base,
			String baseName, Class<?>[] baseParams) {
		Boolean answer = answer(known, type);
		if (answer == null) {
			try {
				answer = declaresOver(type, extension, name, params) || declaresOver(type, base, baseName, baseParams);
			} catch (RuntimeException | LinkageError e) {
				answer = true;
			}
			keep(known, type, answer);
		}
		return answer;
	}

	/** Whether the object's class keeps {@code base}'s method: no class between them declares it again. */
	static boolean keepsMethod(ClassValue<Object[]> known, Object object, Class<?> base, String name, Class<?>[] params) {
		try {
			return !overrides(known, object.getClass(), base, name, params);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/**
	 * {@link #keepsDefault}'s answer from the answers kept for the classes of the state and its block, told without making
	 * a lookup's arrays: 1 where both keep the default, 0 where one doesn't, -1 where one wasn't asked of yet.
	 */
	static int asked(ClassValue<Object[]> known, Object state, Object block) {
		int answer = asked(known, state);
		return answer != 1 ? answer : asked(known, block);
	}

	/**
	 * {@link #keepsMethod}'s answer from the one kept for the object's class: 1 where it keeps the method, 0 where it
	 * overrides it, -1 where it wasn't asked of yet.
	 */
	static int asked(ClassValue<Object[]> known, Object object) {
		Boolean overrides = answer(known, object.getClass());
		return overrides == null ? -1 : overrides ? 0 : 1;
	}

	/** {@link #declaresOver}, from {@code known} where it was asked for the class before; true where it can't tell. */
	static boolean overrides(ClassValue<Object[]> known, Class<?> type, Class<?> extension, String name, Class<?>[] params) {
		Boolean answer = answer(known, type);
		if (answer == null) {
			try {
				answer = declaresOver(type, extension, name, params);
			} catch (RuntimeException | LinkageError e) {
				answer = true;
			}
			keep(known, type, answer);
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

	/**
	 * Whether the class declares the method. A synthetic method that isn't a compiler's bridge is Fabricated's default
	 * bridge ({@code DefaultBridge}), which only calls the default the class had where a Fabric mod's interface brings
	 * another (SEAM-7: Porting Lib's {@code BlockStateExtensions} on Forge 47's {@code BlockState}): it overrides nothing,
	 * and the class's interfaces are asked as they would be without it.
	 */
	static boolean declares(Class<?> c, String name, Class<?>[] params) {
		try {
			java.lang.reflect.Method method = c.getDeclaredMethod(name, params);
			return !method.isSynthetic() || method.isBridge();
		} catch (NoSuchMethodException e) {
			return false;
		}
	}
}
