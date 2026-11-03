package io.github.cmdralph.fabricated.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.mixin.RecordedFrames;
import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * Repairs the frames Forge's Mixin recorded for a method where its local computation would drop live locals (CORPUS-79,
 * {@link RecordedFrames}): before mixins apply to a class a Fabric mod's mixin targets, each chop leaving fewer locals than
 * its frame states is recorded again as a full frame of those locals, through Mixin's own {@code ClassInfo.Method}. Mixin
 * reads that as the frame's locals and none beyond; every other frame stays as Mixin recorded it. Resourceful Lib's
 * {@code @Local PoseStack} in Forge 61's and 64's {@code ScreenEffectRenderer.renderScreenEffect} found none without it,
 * and the client crashed.
 *
 * <p>A class no Fabric mod's mixin targets is left alone, so Forge mods' mixins see Mixin's frames as on Forge without
 * Fabricated; a Forge mod's injector in a class a Fabric mod's mixin targets sees the live locals it would otherwise miss.
 * Shared by Forge 47 (Mixin 0.8.5) and Forge 61 to 66 (Mixin 0.8.7), whose frame code is the same.
 */
final class RecordedFrameRepair implements IExtension {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MIXIN_CONFIGS = "MixinConfigs";

	private final FabricatedState state;
	/** The Fabric mods' mixin configs by name, read once a class needs them; Mixin calls the extension in its lock. */
	private Set<String> fabricConfigs;

	RecordedFrameRepair(FabricatedState state) {
		this.state = state;
	}

	@Override
	public boolean checkActive(MixinEnvironment environment) {
		return true;
	}

	@Override
	public void preApply(ITargetClassContext context) {
		ClassNode node = context.getClassNode();
		ClassInfo info = context.getClassInfo();
		Boolean fabricTarget = null;
		for (MethodNode method : node.methods) {
			if (!RecordedFrames.mayDropLiveLocals(method)) continue;
			// Found as Mixin's local computation finds it (Locals.getLocalsAt).
			ClassInfo.Method recorded = info.findMethod(method, method.access | ClassInfo.INCLUDE_INITIALISERS);
			List<ClassInfo.FrameData> frames = recorded == null ? null : recorded.getFrames();
			if (frames == null || frames.isEmpty()) continue;
			List<RecordedFrames.Frame> read = new ArrayList<>(frames.size());
			for (ClassInfo.FrameData frame : frames) read.add(new RecordedFrames.Frame(frame.index, frame.type, frame.size));
			List<Integer> chops = RecordedFrames.droppingChops(method, read);
			if (chops.isEmpty()) continue;
			if (fabricTarget == null) fabricTarget = targetedByFabricMod(node.name);
			if (!fabricTarget) return;
			List<ClassInfo.FrameData> full = info.new Method(RecordedFrames.asFullFrames(method), false).getFrames();
			for (int chop : chops) frames.set(chop, full.get(chop));
			LOGGER.info("Fabricated repaired the frames Mixin recorded for {}.{}{} at its chopping frames {}, where Forge's Mixin would drop live locals"
					+ " a Fabric mod's injector may take (CORPUS-79)", node.name, method.name, method.desc, chops);
		}
	}

	@Override
	public void postApply(ITargetClassContext context) {
	}

	@Override
	public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
	}

	/** Whether a mixin config of a Fabric mod targets the class (Mixin's own configs, as they were prepared). */
	private boolean targetedByFabricMod(String internalName) {
		Set<String> fabric = fabricConfigs();
		String target = internalName.replace('/', '.');
		for (Config config : Mixins.getConfigs()) {
			if (fabric.contains(config.getName()) && config.getConfig() != null && config.getConfig().getTargets().contains(target)) return true;
		}
		return false;
	}

	/** The mixin configs the Fabric mods' translations declare, in their overlays' manifests, where Mixin reads them. */
	private Set<String> fabricConfigs() {
		if (fabricConfigs != null) return fabricConfigs;
		Set<String> names = new HashSet<>();
		for (Pipeline.LoadedMod mod : state.mods()) {
			Path overlay = mod.translated().overlay();
			if (overlay == null || !Files.isRegularFile(overlay)) continue;
			try (JarFile jar = new JarFile(overlay.toFile())) {
				Manifest manifest = jar.getManifest();
				String configs = manifest == null ? null : manifest.getMainAttributes().getValue(MIXIN_CONFIGS);
				if (configs == null) continue;
				for (String config : configs.split(",")) {
					if (!config.isBlank()) names.add(config.trim());
				}
			} catch (IOException e) {
				// Not a Fabric mod's config then, for this repair: its classes keep Mixin's frames.
				LOGGER.warn("Fabricated could not read the mixin configs of {}'s translation ({}): {}", mod.translated().candidate().id(), overlay, e.toString());
			}
		}
		fabricConfigs = names;
		return names;
	}
}
