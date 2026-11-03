package io.github.cmdralph.fabricated.core.testutil;

import java.util.Map;

/** Defines the given classes (by internal name) itself, verifying them as any class loader does, before asking the tests' class loader. */
public final class BytesClassLoader extends ClassLoader {
	private final Map<String, byte[]> classes;

	public BytesClassLoader(Map<String, byte[]> classes) {
		super(BytesClassLoader.class.getClassLoader());
		this.classes = classes;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			byte[] data = classes.get(name.replace('.', '/'));
			if (data == null) return super.loadClass(name, resolve);
			Class<?> c = findLoadedClass(name);
			if (c == null) c = defineClass(name, data, 0, data.length);
			if (resolve) resolveClass(c);
			return c;
		}
	}
}
