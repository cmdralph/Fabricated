package io.github.cmdralph.fabricated.loader;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;

/**
 * The module of a translated Fabric mod on Forge (docs/PORTING.md §4.1): an automatic module named after the mod's
 * Forge id, as Forge's own metadata for a mods.toml jar names it (whose constructor isn't public), with the jar's
 * packages and the services the package guard decided it declares ({@code PackageGuard.Decision#provides}), read as
 * ServiceLoader reads them on Fabric's class path. SecureJarHandler's own reading of the files, which Forge 43 and 47 have
 * and which takes a comment after a class name as part of it, is never asked for (PKG-6). A provider outside the jar's
 * packages is left out too, as the descriptor would refuse the module.
 */
final class FabricJarMetadata implements JarMetadata {
	private final String name;
	private final String version;
	private final SecureJar jar;
	private final Map<String, List<String>> provides;
	private ModuleDescriptor descriptor;

	FabricJarMetadata(String name, String version, SecureJar jar, Map<String, List<String>> provides) {
		this.name = name;
		this.version = version;
		this.jar = jar;
		this.provides = provides;
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
		provides.forEach((service, providers) -> {
			List<String> own = providers.stream().filter(p -> p.lastIndexOf('.') > 0 && packages.contains(p.substring(0, p.lastIndexOf('.')))).toList();
			if (!own.isEmpty()) builder.provides(service, own);
		});
		descriptor = builder.build();
		return descriptor;
	}
}
