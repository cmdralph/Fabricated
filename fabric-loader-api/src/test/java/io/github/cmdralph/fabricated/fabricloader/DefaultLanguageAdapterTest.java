package io.github.cmdralph.fabricated.fabricloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

class DefaultLanguageAdapterTest {
	/** Minimal container; the adapter only uses it to find a class loader. */
	private static final ModContainer MOD = new ModContainer() {
		@Override
		public ModMetadata getMetadata() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Path> getRootPaths() {
			return List.of();
		}

		@Override
		public ModOrigin getOrigin() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<ModContainer> getContainingMod() {
			return Optional.empty();
		}

		@Override
		public Collection<ModContainer> getContainedMods() {
			return List.of();
		}

		@Override
		@Deprecated
		public Path getRootPath() {
			throw new UnsupportedOperationException();
		}

		@Override
		@Deprecated
		public Path getPath(String file) {
			throw new UnsupportedOperationException();
		}
	};

	public static final class Greeter implements Supplier<String> {
		@Override
		public String get() {
			return "hello";
		}
	}

	public static final Supplier<String> FIELD = () -> "field";

	public static String staticMethod() {
		return "static";
	}

	public String instanceMethod() {
		return "instance";
	}

	@Test
	void createsClassFieldAndMethodEntrypoints() throws Exception {
		String self = DefaultLanguageAdapterTest.class.getName();
		@SuppressWarnings("unchecked")
		Supplier<String> fromClass = DefaultLanguageAdapter.INSTANCE.create(MOD, Greeter.class.getName(), Supplier.class);
		assertEquals("hello", fromClass.get());

		@SuppressWarnings("unchecked")
		Supplier<String> fromField = DefaultLanguageAdapter.INSTANCE.create(MOD, self + "::FIELD", Supplier.class);
		assertSame(FIELD, fromField);

		@SuppressWarnings("unchecked")
		Supplier<String> fromStatic = DefaultLanguageAdapter.INSTANCE.create(MOD, self + "::staticMethod", Supplier.class);
		assertEquals("static", fromStatic.get());

		@SuppressWarnings("unchecked")
		Supplier<String> fromInstance = DefaultLanguageAdapter.INSTANCE.create(MOD, self + "::instanceMethod", Supplier.class);
		assertEquals("instance", fromInstance.get());
	}

	@Test
	void reportsBadEntrypoints() {
		assertThrows(LanguageAdapterException.class, () -> DefaultLanguageAdapter.INSTANCE.create(MOD, "does.not.Exist", Runnable.class));
		assertThrows(LanguageAdapterException.class, () -> DefaultLanguageAdapter.INSTANCE.create(MOD, Greeter.class.getName(), Runnable.class));
		assertThrows(LanguageAdapterException.class, () -> DefaultLanguageAdapter.INSTANCE.create(MOD, DefaultLanguageAdapterTest.class.getName() + "::missing", Runnable.class));
		assertThrows(LanguageAdapterException.class, () -> DefaultLanguageAdapter.INSTANCE.create(MOD, "a::b::c", Runnable.class));
	}
}
