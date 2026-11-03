package io.github.cmdralph.fabricated.core.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import io.github.cmdralph.fabricated.core.testutil.BytesClassLoader;
import io.github.cmdralph.fabricated.core.testutil.TestSources;

class LibraryAdaptersTest {
	/** Mixin's annotations as the adapted mixins use them: a shadow with Mixin's prefix, and an injector. */
	static final Map<String, String> ANNOTATIONS = Map.of(
			"org/spongepowered/asm/mixin/Shadow.java", "package org.spongepowered.asm.mixin; public @interface Shadow { boolean remap() default true; String prefix() default \"shadow$\"; }",
			"org/spongepowered/asm/mixin/injection/Inject.java", "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); }");

	/** A library as authlib is: a profile with an id and a name, equal to another of the same id and name. */
	static final Map<String, String> LIBRARY = Map.of(
			"lib/Profile.java", """
					package lib;
					import java.util.UUID;
					public class Profile {
						private final UUID id;
						private final String name;
						public Profile(UUID id, String name) { this.id = id; this.name = name; }
						public UUID getId() { return id; }
						public String name() { return name; }
						@Override public boolean equals(Object o) { return o instanceof Profile p && p.id.equals(id) && p.name.equals(name); }
						@Override public int hashCode() { return id.hashCode(); }
						@Override public String toString() { return "Profile[" + name + "]"; }
					}""");

	/** Another library's interfaces, as Adventure's: implemented elsewhere too, with default methods. */
	static final Map<String, String> API = Map.of(
			"api/Identified.java", "package api; public interface Identified { Identity identity(); }",
			"api/Identity.java", """
					package api;
					import java.util.UUID;
					public interface Identity extends Identified {
						UUID uuid();
						String display();
						default Identity identity() { return this; }
						default String name() { return "nobody"; }
						default String describe() { return name() + "/" + uuid(); }
						static Identity of(UUID uuid) {
							return new Identity() {
								public UUID uuid() { return uuid; }
								public String display() { return uuid.toString(); }
							};
						}
					}""");

	/** adventure-platform-fabric's profile mixin, a player's use of it, and other code checking and casting identities. */
	static final Map<String, String> MOD = Map.of(
			"mod/mixin/ProfileMixin.java", """
					package mod.mixin;
					import java.util.UUID;
					import java.util.function.Supplier;
					import org.spongepowered.asm.mixin.*;
					@Mixin(value = lib.Profile.class, remap = false)
					public abstract class ProfileMixin implements api.Identity {
						@Shadow public abstract UUID shadow$getId();
						@Override public UUID uuid() { return this.shadow$getId(); }
						@Override public String display() { Supplier<String> name = () -> ((lib.Profile) (Object) this).name(); return name.get().toUpperCase(); }
					}""",
			"mod/Player.java", """
					package mod;
					public class Player {
						public final lib.Profile profile;
						public Player(lib.Profile profile) { this.profile = profile; }
						public api.Identity identity() { return (api.Identity) (Object) this.profile; }
						public static boolean isIdentity(Object o) { return o instanceof api.Identity; }
						public static api.Identity cast(Object o) { return (api.Identity) o; }
						public static lib.Profile back(Object o) { return (lib.Profile) o; }
					}""");

	@Test
	void aMixinGivingALibraryClassAnotherLibrarysInterfaceBecomesAnAdapterTheModsCastsWrap(@TempDir Path dir) throws Exception {
		// LIB-2: adventure-platform-fabric's GameProfileMixin, an Adventure Identity, and its player mixin's cast of the profile.
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, ANNOTATIONS, LIBRARY, API, MOD);
		LibraryAdapters.Adapter adapter = LibraryAdapters.of(classes.get("mod/mixin/ProfileMixin"), "lib/Profile", name -> node(classes, name),
				Set.of(), "mod/ProfileMixin$FabricatedAdapter");
		assertNotNull(adapter);
		assertEquals(1, adapter.served().size());
		LibraryAdapters.Adapted adapted = adapter.served().getFirst();
		assertEquals("api/Identity", adapted.itf());
		Map<String, LibraryAdapters.Adapted> served = Map.of(adapted.itf(), adapted);
		assertNull(LibraryAdapters.rewrite(classes.get("api/Identity"), Map.of("api/Identified", adapted)), "a class not using the interfaces stays as it is");

		Map<String, byte[]> loaded = new HashMap<>(classes);
		loaded.put(adapter.name(), adapter.bytes());
		loaded.put("mod/Player", LibraryAdapters.rewrite(classes.get("mod/Player"), served));
		ClassLoader loader = new BytesClassLoader(loaded);
		Class<?> profileClass = loader.loadClass("lib.Profile");
		Class<?> identityClass = loader.loadClass("api.Identity");
		Class<?> playerClass = loader.loadClass("mod.Player");
		UUID id = UUID.randomUUID();
		Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(id, "Steve");
		Object player = playerClass.getConstructor(profileClass).newInstance(profile);

		Object identity = playerClass.getMethod("identity").invoke(player);
		assertTrue(identityClass.isInstance(identity), "the cast gave an Identity");
		assertEquals(id, identityClass.getMethod("uuid").invoke(identity), "the mixin's method, calling the profile's through its shadow");
		assertSame(identity, identityClass.getMethod("identity").invoke(identity), "the interface's default, as on the profile");
		assertEquals("Steve", identityClass.getMethod("name").invoke(identity), "the profile's own method answers before the interface's default");
		assertEquals("Steve/" + id, identityClass.getMethod("describe").invoke(identity), "a default calling both");
		Object again = playerClass.getMethod("identity").invoke(player);
		assertNotSame(identity, again);
		assertEquals(identity, again, "two adapters of one profile are equal, as the profile is to itself");
		assertEquals(profile.hashCode(), identity.hashCode());
		assertEquals("Profile[Steve]", identity.toString());
		assertEquals("STEVE", identityClass.getMethod("display").invoke(identity), "a lambda of the mixin's casting it to the class");

		Method isIdentity = playerClass.getMethod("isIdentity", Object.class);
		assertTrue((Boolean) isIdentity.invoke(null, profile), "a check against the interface counts the library class");
		assertTrue((Boolean) isIdentity.invoke(null, identity));
		assertFalse((Boolean) isIdentity.invoke(null, "text"));
		Object other = identityClass.getMethod("of", UUID.class).invoke(null, id);
		assertSame(other, playerClass.getMethod("cast", Object.class).invoke(null, other), "another Identity is cast as it is");
		InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> playerClass.getMethod("cast", Object.class).invoke(null, "text"));
		assertTrue(e.getCause() instanceof ClassCastException, "a cast of another object still fails: " + e.getCause());
		assertSame(profile, playerClass.getMethod("back", Object.class).invoke(null, identity), "a cast to the class unwraps the adapter");
		assertSame(profile, playerClass.getMethod("back", Object.class).invoke(null, profile));
	}

	@Test
	void aMixinKeepingStateInjectingOrServingATakenInterfaceIsNotAdapted(@TempDir Path dir) throws Exception {
		Map<String, String> mod = Map.of(
				"mod/mixin/StatefulMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(lib.Profile.class)
						public abstract class StatefulMixin implements api.Identity {
							@Unique private java.util.UUID cached;
							@Override public java.util.UUID uuid() { return cached; }
							@Override public String display() { return ""; }
						}""",
				"mod/mixin/InjectingMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(lib.Profile.class)
						public abstract class InjectingMixin implements api.Identity {
							@Override public java.util.UUID uuid() { return null; }
							@Override public String display() { return ""; }
							@org.spongepowered.asm.mixin.injection.Inject(method = "getId") private void onGetId() { }
						}""",
				"mod/mixin/HiddenShadowMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(lib.Profile.class)
						public abstract class HiddenShadowMixin implements api.Identity {
							@Shadow private java.util.UUID id;
							@Override public java.util.UUID uuid() { return id; }
							@Override public String display() { return ""; }
						}""",
				"mod/mixin/PlainMixin.java", """
						package mod.mixin;
						import org.spongepowered.asm.mixin.*;
						@Mixin(lib.Profile.class)
						public abstract class PlainMixin implements api.Identity {
							@Override public java.util.UUID uuid() { return null; }
							@Override public String display() { return ""; }
						}""");
		Map<String, byte[]> classes = TestSources.compile(dir, TestSources.MIXIN_ANNOTATIONS, ANNOTATIONS, LIBRARY, API, mod);
		for (String mixin : new String[] {"StatefulMixin", "InjectingMixin", "HiddenShadowMixin"}) {
			assertNull(LibraryAdapters.of(classes.get("mod/mixin/" + mixin), "lib/Profile", name -> node(classes, name), Set.of(), "mod/A"), mixin);
		}
		assertNotNull(LibraryAdapters.of(classes.get("mod/mixin/PlainMixin"), "lib/Profile", name -> node(classes, name), Set.of(), "mod/A"));
		assertNull(LibraryAdapters.of(classes.get("mod/mixin/PlainMixin"), "lib/Profile", name -> node(classes, name), Set.of("api/Identity"), "mod/A"),
				"another adapter or bridge serves the interface");
		assertNull(LibraryAdapters.of(classes.get("mod/mixin/PlainMixin"), "lib/Profile", name -> name.equals("java/lang/Object") ? null : node(classes, name),
				Set.of(), "mod/A"), "the class's hierarchy can't be read");
	}

	private static ClassNode node(Map<String, byte[]> classes, String name) {
		byte[] data = classes.get(name);
		if (data == null && name.equals("java/lang/Object")) {
			try (var in = Object.class.getResourceAsStream("Object.class")) {
				data = in.readAllBytes();
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		}
		if (data == null) return null;
		ClassNode node = new ClassNode();
		new ClassReader(data).accept(node, ClassReader.SKIP_FRAMES);
		return node;
	}
}
