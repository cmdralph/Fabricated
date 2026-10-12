package io.github.cmdralph.fabricated.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.metadata.FabricModJsonParser;
import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.metadata.ModOrigin;

class FabricLoaderRuntimeTest {
	static final List<String> CALLS = new ArrayList<>();

	public static final class Good implements ModInitializer {
		@Override
		public void onInitialize() {
			CALLS.add("good");
		}
	}

	public static final class Bad implements ModInitializer {
		@Override
		public void onInitialize() {
			throw new IllegalStateException("boom");
		}
	}

	private static FabricLoaderRuntime runtime() {
		return new FabricLoaderRuntime(EnvType.SERVER, Path.of("game"), Path.of("game/config"), "26.2", false,
				MappingResolvers.identity(), () -> new String[] {"--accessToken", "secret", "--nogui"});
	}

	private static ModContainerImpl mod(FabricLoaderRuntime rt, String id, String mainClass) throws Exception {
		FabricModMetadata md = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1.0\","
				+ "\"provides\":[\"" + id + "-provided\"],\"entrypoints\":{\"main\":[\"" + mainClass + "\"],\"custom\":[\"" + mainClass + "\"]}}");
		ModContainerImpl c = new ModContainerImpl(md, List.of(), new ModContainerImpl.Origin(ModOrigin.Kind.PATH, List.of(), null, null), null, List.of(), rt);
		rt.addMod(c, md.getProvides());
		rt.addEntrypoints(c, md.entrypoints(), Set.of());
		return c;
	}

	@Test
	void runsEntrypointsInOrderAndIsolatesFailures() throws Exception {
		CALLS.clear();
		FabricLoaderRuntime rt = runtime();
		mod(rt, "good_mod", Good.class.getName());
		mod(rt, "bad_mod", Bad.class.getName());
		AtomicReference<String> context = new AtomicReference<>();
		List<String> contexts = new ArrayList<>();
		EntrypointRunner runner = new EntrypointRunner(rt, new EntrypointRunner.ContextHook() {
			@Override
			public void enter(String modId) {
				context.set(modId);
				contexts.add(modId);
			}

			@Override
			public void exit() {
				context.set(null);
			}
		});
		List<EntrypointRunner.Failure> failures = runner.run("main", ModInitializer.class, ModInitializer::onInitialize);
		assertEquals(List.of("good"), CALLS);
		assertEquals(1, failures.size());
		assertEquals("bad_mod", failures.getFirst().modId());
		assertTrue(failures.getFirst().describe().contains("boom"));
		assertEquals(List.of("good_mod", "bad_mod"), contexts);
		assertEquals(null, context.get());
		assertTrue(runner.run("main", ModInitializer.class, ModInitializer::onInitialize).isEmpty(), "a failed mod's entrypoints are skipped");
	}

	@Test
	void exposesModsEntrypointsAndSharesInstances() throws Exception {
		FabricLoaderRuntime rt = runtime();
		mod(rt, "good_mod", Good.class.getName());
		assertTrue(rt.isModLoaded("good_mod"));
		assertTrue(rt.isModLoaded("good_mod-provided"));
		assertEquals(1, rt.getAllMods().size(), "aliases are not duplicated");
		List<ModInitializer> custom = rt.getEntrypoints("custom", ModInitializer.class);
		List<ModInitializer> main = rt.getEntrypoints("main", ModInitializer.class);
		assertSame(custom.getFirst(), main.getFirst(), "same declaration and type gives the same instance");
		assertEquals("good_mod", rt.getEntrypointContainers("main", ModInitializer.class).getFirst().getProvider().getMetadata().getId());
		assertEquals(List.of("--nogui"), List.of(rt.getLaunchArguments(true)));
		assertEquals(EnvType.SERVER, rt.getEnvironmentType());

		mod(rt, "bad_mod", "does.not.Exist");
		assertThrows(EntrypointException.class, () -> rt.getEntrypoints("main", ModInitializer.class));
	}

	@Test
	void objectShareNotifiesWaiters() {
		ObjectShareImpl share = new ObjectShareImpl();
		List<Object> seen = new ArrayList<>();
		share.whenAvailable("mod:key", (k, v) -> seen.add(v));
		share.put("mod:key", "value");
		assertEquals(List.of("value"), seen);
		assertEquals("value", share.putIfAbsent("mod:key", "other"));
		assertThrows(IllegalArgumentException.class, () -> share.get("nonamespace"));
	}

	@Test
	void mappingResolversTranslateIntermediaryNames() {
		MappingTable table = MappingTable.fromMembers(Map.of("net/minecraft/class_1", "net/minecraft/world/Level"),
				Map.of("method_5", Map.of("net/minecraft/class_1.method_5()V", "tick")));
		MappingResolver r = MappingResolvers.intermediary(table);
		assertEquals("named", r.getCurrentRuntimeNamespace());
		assertEquals("net.minecraft.world.Level", r.mapClassName("intermediary", "net.minecraft.class_1"));
		assertEquals("net.minecraft.class_1", r.unmapClassName("intermediary", "net.minecraft.world.Level"));
		assertEquals("tick", r.mapMethodName("intermediary", "net.minecraft.class_1", "method_5", "()V"));
		assertEquals("unchanged", r.mapClassName("official", "unchanged"));
		MappingResolver id = MappingResolvers.identity();
		assertEquals("official", id.getCurrentRuntimeNamespace());
		assertEquals("a.B", id.mapClassName("intermediary", "a.B"));
	}

	@Test
	void sanitizedLaunchArgumentsDropEverySensitiveValue() {
		FabricLoaderRuntime rt = new FabricLoaderRuntime(EnvType.CLIENT, Path.of("game"), Path.of("game/config"), "26.2", false,
				MappingResolvers.identity(), () -> new String[] {"--username", "Steve", "--proxyPass", "hunter2", "--UUID", "abc",
						"--userProperties", "{}", "--accessToken=secret", "--gameDir", "game"});
		assertEquals(List.of("--gameDir", "game"), List.of(rt.getLaunchArguments(true)));
	}

	@Test
	void hyphenatedFabricIdsFindTheirNeoForgeBuild() throws Exception {
		FabricLoaderRuntime rt = runtime();
		mod(rt, "cloth_config", "missing.Main");
		org.junit.jupiter.api.Assertions.assertTrue(rt.isModLoaded("cloth-config"));
		org.junit.jupiter.api.Assertions.assertTrue(rt.getModContainer("cloth-config").isPresent());
	}
}
