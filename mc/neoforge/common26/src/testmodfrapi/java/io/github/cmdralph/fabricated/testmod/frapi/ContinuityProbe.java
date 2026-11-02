package io.github.cmdralph.fabricated.testmod.frapi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * What Continuity's connected textures depend on, read by reflection where R6 sees none (CORPUS-56): its quad
 * processors, the ones it picks for the checked block's sprite, the sprite finder it looks quads' sprites up with, its
 * per-thread transform and feature state, and its configuration. Reflection keeps the test mod building without
 * Continuity, and working across its versions: a part it can't read says why, and the rest still print.
 *
 * <p>The same file is in each version's renderer test mod: it names no game or Fabric API class.
 */
final class ContinuityProbe {
	private static final String CLIENT = "me.pepperbell.continuity.client.";
	/** What Continuity's sprite finder found for the first quad the check saw, or null before one. */
	private static volatile String firstQuad;

	private ContinuityProbe() {
	}

	/** Records what Continuity's sprite finder finds for this quad, the first time; {@code sprite} is the block's own. */
	static void quad(Object quad, Object sprite) {
		if (firstQuad != null) return;
		String found;
		try {
			Object finder = null;
			for (Method method : type("util.RenderUtil").getDeclaredMethods()) {
				if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && method.getName().contains("SpriteFinder")) {
					method.setAccessible(true);
					finder = method.invoke(null);
					break;
				}
			}
			if (finder == null) {
				found = "no sprite finder yet";
			} else {
				Object result = call(finder, "find", quad);
				found = describe(result) + (result == sprite ? " (the block's own sprite)" : " (not the block's own sprite object)");
			}
		} catch (Throwable t) {
			found = "unreadable: " + t;
		}
		firstQuad = found;
	}

	/** Continuity's state for this block state and sprite, on this thread (call it where the block was drawn). */
	static String describe(Object state, Object sprite) {
		StringBuilder out = new StringBuilder("Continuity's state:");
		out.append(" processors ").append(attempt(ContinuityProbe::processors));
		out.append("; for this block's sprite ").append(attempt(() -> slice(state, sprite)));
		out.append("; its transform on this thread ").append(attempt(() -> {
			Object container = call(null, type("model.ModelObjectsContainer"), "get");
			Object transform = field(container, "ctmQuadTransform");
			return "active=" + call(transform, "isActive") + " (true here: a call left it active)";
		}));
		out.append("; connected textures state ").append(attempt(() -> {
			Object container = call(null, type("model.ModelObjectsContainer"), "get");
			Object states = field(container, "featureStates");
			return "enabled=" + call(call(states, "getConnectedTexturesState"), "isEnabled");
		}));
		out.append("; configuration ").append(attempt(() -> {
			Object config = field(null, type("config.ContinuityConfig"), "INSTANCE");
			return "connectedTextures=" + call(field(config, "connectedTextures"), "get");
		}));
		out.append("; its sprite finder, for the first quad: ").append(firstQuad);
		return out.toString();
	}

	private static String processors() throws ReflectiveOperationException {
		Object holders = field(null, type("model.QuadProcessors"), "processorHolders");
		if (!(holders instanceof Object[] array)) return "unreadable: " + holders;
		Map<String, Integer> kinds = new TreeMap<>();
		for (Object holder : array) kinds.merge(call(holder, "processor").getClass().getSimpleName(), 1, Integer::sum);
		String first = array.length == 0 ? "" : ", the first's sprites " + sprites(call(array[0], "processor"));
		return array.length + " " + kinds + first;
	}

	@SuppressWarnings("unchecked")
	private static String slice(Object state, Object sprite) throws ReflectiveOperationException {
		Object cache = call(null, type("model.QuadProcessors"), "getCache", state);
		Object slice = ((Function<Object, Object>) cache).apply(sprite);
		Object processors = call(slice, "processors");
		if (!(processors instanceof Object[] array)) return "unreadable: " + processors;
		StringBuilder names = new StringBuilder();
		for (Object processor : array) names.append(names.isEmpty() ? "" : ", ").append(processor.getClass().getSimpleName());
		return array.length + " processor(s) [" + names + "]" + (array.length == 0 ? "" : ", the first's sprites " + sprites(array[0]));
	}

	/** The first array of sprites a processor keeps: how many, and the first of them. */
	private static String sprites(Object processor) {
		for (Class<?> type = processor.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!field.getType().isArray() || !field.getType().getComponentType().getSimpleName().contains("Sprite")) continue;
				try {
					field.setAccessible(true);
					Object[] sprites = (Object[]) field.get(processor);
					return sprites == null ? "none" : sprites.length + (sprites.length > 0 ? " (first " + describe(sprites[0]) + ")" : "");
				} catch (ReflectiveOperationException | RuntimeException e) {
					return "unreadable: " + e;
				}
			}
		}
		return "none kept";
	}

	private static String describe(Object sprite) {
		return sprite == null ? "null" : String.valueOf(sprite);
	}

	private interface Read {
		Object get() throws Exception;
	}

	private static String attempt(Read read) {
		try {
			return String.valueOf(read.get());
		} catch (Throwable t) {
			return "unreadable: " + t;
		}
	}

	private static Class<?> type(String name) throws ClassNotFoundException {
		return Class.forName(CLIENT + name, false, ContinuityProbe.class.getClassLoader());
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		return field(target, target.getClass(), name);
	}

	private static Object field(Object target, Class<?> type, String name) throws ReflectiveOperationException {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Field field = c.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(target);
			} catch (NoSuchFieldException e) {
				// The superclass's
			}
		}
		throw new NoSuchFieldException(type.getName() + "." + name);
	}

	private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
		return call(target, target.getClass(), name, args);
	}

	private static Object call(Object target, Class<?> type, String name, Object... args) throws ReflectiveOperationException {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			for (Method method : c.getDeclaredMethods()) {
				if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
				Class<?>[] parameters = method.getParameterTypes();
				boolean fits = true;
				for (int i = 0; i < args.length && fits; i++) fits = args[i] == null || box(parameters[i]).isInstance(args[i]);
				if (!fits) continue;
				method.setAccessible(true);
				return method.invoke(target, args);
			}
		}
		for (Class<?> c : type.getInterfaces()) {
			for (Method method : c.getMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(target, args);
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + name + " with " + args.length + " argument(s)");
	}

	private static Class<?> box(Class<?> type) {
		if (!type.isPrimitive()) return type;
		if (type == boolean.class) return Boolean.class;
		if (type == int.class) return Integer.class;
		if (type == long.class) return Long.class;
		if (type == float.class) return Float.class;
		if (type == double.class) return Double.class;
		return Object.class;
	}
}
