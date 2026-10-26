package io.github.cmdralph.fabricated.loader;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.Set;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;

/**
 * The module of a translated Fabric mod on Forge (docs/PORTING.md §4.1): an automatic module named after the mod's
 * Forge id, as Forge's own metadata for a mods.toml jar names it (whose constructor isn't public), with the jar's
 * packages and the services it provides from them. A service file naming a class outside the jar is left out of the
 * descriptor, which would otherwise refuse the module.
 */
final class FabricJarMetadata implements JarMetadata {
	private final String name;
	private final String version;
	private final SecureJar jar;
	private ModuleDescriptor descriptor;

	FabricJarMetadata(String name, String version, SecureJar jar) {
		this.name = name;
		this.version = version;
		this.jar = jar;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String version() {
		return version;
	}

	@Override
	public ModuleDescriptor descriptor() {
		if (descriptor != null) return descriptor;
		Set<String> packages = jar.getPackages();
		ModuleDescriptor.Builder builder = ModuleDescriptor.newAutomaticModule(name).packages(packages);
		try {
			builder.version(version);
		} catch (IllegalArgumentException e) {
			// Not a version Java's modules take: the module goes without one, as Fabric code reads the mod's own.
		}
		for (SecureJar.Provider provider : jar.getProviders()) {
			List<String> own = provider.providers().stream().filter(p -> p.lastIndexOf('.') > 0 && packages.contains(p.substring(0, p.lastIndexOf('.')))).toList();
			if (!own.isEmpty()) builder.provides(provider.serviceName(), own);
		}
		descriptor = builder.build();
		return descriptor;
	}
}
