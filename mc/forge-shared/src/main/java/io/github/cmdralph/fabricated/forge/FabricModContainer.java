package io.github.cmdralph.fabricated.forge;

import java.lang.annotation.ElementType;
import java.util.Map;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import io.github.cmdralph.fabricated.loader.PluginAnnotations;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;

/**
 * Forge's container for one Fabric mod, which Fabricated's language provider creates by name through the game's class
 * loader (docs/PORTING.md §4.1). Fabric mods have no mod object and don't use Forge's mod bus; their entrypoints run
 * from Fabricated's in-game mod. The container connects the mod to its classes ({@link ForgeLoaderHooks#attach}), and
 * adds to its scan data the annotations other mods' Forge builds find its plugins by ({@link PluginAnnotations}): Forge
 * makes every mod's container before it constructs any mod.
 */
public final class FabricModContainer extends ModContainer {
	public FabricModContainer(IModInfo info, ModFileScanData scan, ModuleLayer gameLayer) {
		super(info);
		// What ModLoadingContext hands the mod's code while the container is active: Fabric mods have no FML context
		// (Forge's own containers give theirs), and Forge reads this for every container, so it mustn't be unset.
		this.contextExtension = () -> null;
		String moduleName = info.getOwningFile().moduleName();
		if (ForgeLoaderHooks.attach(info.getModId(), gameLayer, moduleName).isEmpty()) {
			throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass",
					new IllegalStateException("Mod " + info.getModId() + " declares the Fabricated loader but Fabricated didn't translate it, or its module "
							+ moduleName + " isn't in the game layer"));
		}
		PluginAnnotations.add(info.getModId(), FabricatedState.get().pluginAnnotations(info.getModId()), scan.getAnnotations(),
				ModFileScanData.AnnotationData::annotationType, ModFileScanData.AnnotationData::clazz,
				(type, clazz) -> new ModFileScanData.AnnotationData(type, ElementType.TYPE, clazz, clazz.getClassName(), Map.of()));
	}

	/** Fabric mods have no mod object. */
	@Override
	public boolean matches(Object mod) {
		return false;
	}

	@Override
	public Object getMod() {
		return null;
	}

	/**
	 * The events of the configs made with this container (Forge Config API Port's API on Forge 47), which Forge's own
	 * containers post on the mod's event bus, go to {@link FabricConfigEvents}.
	 */
	@Override
	public void dispatchConfigEvent(IConfigEvent event) {
		FabricConfigEvents.dispatch(getModId(), event);
	}
}
