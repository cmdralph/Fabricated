package io.github.cmdralph.fabricated.loader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Reads a jar with its own copy of Fabric Loader's API without it ({@link FabricLoaderApiCopies}), on FML 4: FML reads
 * every jar, nested ones included, with the first reader that takes it. Any other jar is left to FML's readers, and so
 * is one this fails on.
 */
public final class FabricLoaderApiReader implements IModFileReader {
	@Override
	public int getPriority() {
		return FabricLoaderApiCopies.READER_PRIORITY;
	}

	@Override
	public IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
		try {
			if (jar.findFile(FabricLoaderApiCopies.MARKER).isPresent() || !hasApi(jar)) return null;
			Map<String, List<String>> services = new LinkedHashMap<>();
			for (SecureJar.Provider provider : jar.getMetaInfServices()) services.put(provider.serviceName(), provider.providers());
			Set<String> servicesLeftOut = FabricLoaderApiCopies.servicesLeftOut(services);
			// The union file system asks its filter about directories too (their paths end in '/'), which stay.
			JarContents filtered = new JarContentsBuilder().paths(jar.getPrimaryPath())
					.pathFilter((entry, base) -> entry.endsWith("/") || FabricLoaderApiCopies.keeps(entry, servicesLeftOut))
					.build();
			IModFile file = JarModsDotTomlModFileReader.createModFile(filtered, attributes.withReader(this));
			if (file != null) FabricLoaderApiCopies.note(jar.getPrimaryPath(), servicesLeftOut);
			return file;
		} catch (RuntimeException e) {
			FabricLoaderApiCopies.failed(jar.getPrimaryPath(), e);
			return null;
		}
	}

	/** Whether the jar has one of Fabric Loader's package directories, which its zip file system has for its files. */
	private static boolean hasApi(JarContents jar) {
		for (String prefix : FabricLoaderApiCopies.PACKAGES) {
			if (jar.findFile(prefix.substring(0, prefix.length() - 1)).isPresent()) return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return "Fabricated (Fabric Loader's API)";
	}
}
