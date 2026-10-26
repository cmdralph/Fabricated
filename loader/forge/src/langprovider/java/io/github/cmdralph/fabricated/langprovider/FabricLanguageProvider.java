package io.github.cmdralph.fabricated.langprovider;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ILifecycleEvent;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;

/**
 * Forge's language provider for translated Fabric mods ({@code modLoader="fabricated"} in the generated
 * {@code mods.toml}). Forge loads it into its plugin layer from {@code META-INF/fabricated/lang-provider.jar}, where
 * neither Fabricated's discovery (the service layer) nor the game is visible, so it uses forgespi and fmlcore alone
 * (docs/PORTING.md §4.1). The container is the in-game mod's: created by name through the game's class loader, as
 * Forge's own Java provider creates its own, and Fabric entrypoints run later from the in-game mod.
 */
public final class FabricLanguageProvider implements IModLanguageProvider {
	/** The loader name translated mods declare (core's {@code ModsTomlWriter.LANGUAGE_LOADER}). */
	static final String NAME = "fabricated";
	/** The in-game mod's container of a translated mod; its constructor takes the mod, its scan data and the game layer. */
	static final String CONTAINER = "io.github.cmdralph.fabricated.forge.FabricModContainer";

	@Override
	public String name() {
		return NAME;
	}

	/** Every mod of a file that names this provider gets the loader: Forge fails a mod id no provider claims. */
	@Override
	public Consumer<ModFileScanData> getFileVisitor() {
		return scan -> {
			Map<String, Loader> loaders = new HashMap<>();
			for (IModFileInfo file : scan.getIModInfoData()) {
				for (IModInfo mod : file.getMods()) loaders.put(mod.getModId(), Loader.INSTANCE);
			}
			scan.addLanguageLoader(loaders);
		};
	}

	@Override
	public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(Supplier<R> event) {
	}

	private enum Loader implements IModLanguageLoader {
		INSTANCE;

		@Override
		@SuppressWarnings("unchecked")
		public <T> T loadMod(IModInfo info, ModFileScanData scan, ModuleLayer gameLayer) {
			try {
				// Forge calls this with the game's class loader as the thread's, which can see the in-game mod.
				Class<?> container = Class.forName(CONTAINER, true, Thread.currentThread().getContextClassLoader());
				return (T) container.getConstructor(IModInfo.class, ModFileScanData.class, ModuleLayer.class).newInstance(info, scan, gameLayer);
			} catch (InvocationTargetException e) {
				if (e.getTargetException() instanceof ModLoadingException mle) throw mle;
				throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e.getTargetException());
			} catch (ReflectiveOperationException | LinkageError e) {
				throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
			}
		}
	}
}
