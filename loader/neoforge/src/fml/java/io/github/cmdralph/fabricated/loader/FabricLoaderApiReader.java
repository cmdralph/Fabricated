package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Reads a jar with its own copy of Fabric Loader's API without it ({@link FabricLoaderApiCopies}), on FML 10 and later:
 * FML reads every jar, nested ones included, with the first reader that takes it. Any other jar is left to FML's
 * readers, and so is one this fails on. FML loads plugin libraries with a plain class loader, which can't leave files
 * out, so FML is handed a copy of the jar without them ({@link FabricLoaderApiCopies#store}), of the same type.
 */
public final class FabricLoaderApiReader implements IModFileReader {
	private static final String SERVICES = "META-INF/services/";

	@Override
	public int getPriority() {
		return FabricLoaderApiCopies.READER_PRIORITY;
	}

	@Override
	public IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
		try {
			if (jar.containsFile(FabricLoaderApiCopies.MARKER)) return null;
			List<String> paths = new ArrayList<>();
			boolean[] hasApi = {false};
			jar.visitContent((path, resource) -> {
				paths.add(path);
				if (FabricLoaderApiCopies.owned(path)) hasApi[0] = true;
			});
			if (!hasApi[0]) return null;
			Map<String, List<String>> services = new LinkedHashMap<>();
			for (String path : paths) {
				if (!path.startsWith(SERVICES) || path.indexOf('/', SERVICES.length()) >= 0) continue;
				byte[] text = jar.readFile(path);
				if (text != null) services.put(path.substring(SERVICES.length()), FabricLoaderApiCopies.providers(new String(text, StandardCharsets.UTF_8)));
			}
			Set<String> servicesLeftOut = FabricLoaderApiCopies.servicesLeftOut(services);
			Map<String, byte[]> kept = new TreeMap<>();
			long size = 0;
			for (String path : paths) {
				if (!FabricLoaderApiCopies.keeps(path, servicesLeftOut)) continue;
				byte[] data = jar.readFile(path);
				if (data == null) continue;
				size += data.length;
				if (size > FabricLoaderApiCopies.MAX_COPY_BYTES) throw new IOException("more than " + FabricLoaderApiCopies.MAX_COPY_BYTES + " bytes to copy");
				kept.put(path, data);
			}
			Path copy = FabricLoaderApiCopies.store(Fml.gameDir(), jar.getPrimaryPath(), kept);
			IModFile file = JarModsDotTomlModFileReader.createModFile(JarContents.ofPath(copy), attributes.withReader(this));
			if (file != null) FabricLoaderApiCopies.note(jar.getPrimaryPath(), servicesLeftOut);
			return file;
		} catch (IOException | RuntimeException e) {
			FabricLoaderApiCopies.failed(jar.getPrimaryPath(), e);
			return null;
		}
	}

	@Override
	public String toString() {
		return "Fabricated (Fabric Loader's API)";
	}
}
