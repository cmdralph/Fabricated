package io.github.cmdralph.fabricated.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.api.internal.ApiHolder;

class ApiTest {
	private static final List<Class<?>> API = List.of(Fabricated.class, FabricatedApi.class, FabricMod.class, FabricMod.Status.class,
			FabricApiModule.class, FabricApiModule.Status.class);

	@Test
	void installsOnceAndIsFoundThroughTheEntryPoint() {
		assertTrue(Fabricated.get().isEmpty(), "nothing before Fabricated installs it");
		FabricatedApi api = new FabricatedApi() {
			@Override
			public String fabricatedVersion() {
				return "0.0.0";
			}

			@Override
			public String apiVersion() {
				return Fabricated.API_VERSION;
			}

			@Override
			public String fabricApiVersion() {
				return "0.0.0";
			}

			@Override
			public List<FabricMod> fabricMods() {
				return List.of(new FabricMod("a-b", "a_b", "1.0", "A", "a.jar", FabricMod.Status.LOADED, "", ""));
			}

			@Override
			public Optional<FabricMod> fabricMod(String id) {
				return fabricMods().stream().filter(m -> m.id().equals(id) || m.neoForgeId().equals(id)).findFirst();
			}

			@Override
			public List<FabricApiModule> fabricApiModules() {
				return List.of();
			}

			@Override
			public Optional<FabricApiModule> fabricApiModule(String id) {
				return Optional.empty();
			}

			@Override
			public boolean entrypointsComplete() {
				return true;
			}

			@Override
			public void afterEntrypoints(Runnable action) {
				action.run();
			}

			@Override
			public String renderReport() {
				return "";
			}
		};
		ApiHolder.install(api);
		assertSame(api, Fabricated.get().orElseThrow());
		assertThrows(IllegalStateException.class, () -> ApiHolder.install(api), "the first installation wins");
		assertTrue(api.isFabricModLoaded("a_b"));
		assertEquals(false, api.isFabricModLoaded("missing"));
	}

	/** The API must not mention Minecraft, NeoForge or Fabric types: one jar serves every version and loader. */
	@Test
	void usesOnlyJavaAndItsOwnTypes() {
		for (Class<?> type : API) {
			for (Method method : type.getDeclaredMethods()) {
				if (method.isSynthetic()) continue;
				check(method.getGenericReturnType(), method);
				for (Type parameter : method.getGenericParameterTypes()) check(parameter, method);
			}
			if (type.isRecord()) {
				for (RecordComponent component : type.getRecordComponents()) check(component.getGenericType(), component);
			}
		}
	}

	private static void check(Type type, Object where) {
		if (type instanceof ParameterizedType parameterized) {
			check(parameterized.getRawType(), where);
			for (Type argument : parameterized.getActualTypeArguments()) check(argument, where);
		} else if (type instanceof Class<?> c && c.isArray()) {
			check(c.getComponentType(), where);
		} else if (type instanceof Class<?> c && !c.isPrimitive()) {
			String name = c.getName();
			assertTrue(name.startsWith("java.") || name.startsWith("io.github.cmdralph.fabricated.api."), where + " uses " + name);
		}
	}
}
