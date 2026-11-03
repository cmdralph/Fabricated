package io.github.cmdralph.fabricated.forge;

import java.io.IOException;
import java.lang.reflect.Method;
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
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
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
 * Which mixins target the class is the class metadata's own record ({@code ClassInfo.getMixins()}, package-private in
 * Mixin, where each mixin adds itself as Mixin prepares it): Mixin takes each config it selects out of
 * {@code Mixins.getConfigs()}, so that set no longer has them as mixins apply. Where the record can't be read, nothing is
 * repaired.
 *
 * <p>Registered only where Mixin records frames unexpanded ({@link #needed}): Forge 61 to 66's Mixin 0.8.7, which stopped
 * expanding them by default. Forge 47's Mixin 0.8.5 reads a class's metadata with its frames expanded, so it records each
 * frame's whole locals and counts them right.
 */
final class RecordedFrameRepair implements IExtension {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MIXIN_CONFIGS = "MixinConfigs";

	private final FabricatedState state;
	/** {@code ClassInfo.getMixins()}, the mixins targeting a class; null where it can't be read, and nothing is repaired. */
	private Method targetingMixins = targetingMixins();
	/** The Fabric mods' mixin configs by name, read once a class needs them; Mixin calls the extension in its lock. */
	private Set<String> fabricConfigs;

	RecordedFrameRepair(FabricatedState state) {
		this.state = state;
	}

	/**
	 * Whether Mixin records a class's frames as its bytes have them, unexpanded, which its local computation miscounts:
	 * Mixin 0.8.7 does unless its {@code CLASSREADER_EXPAND_FRAMES} option is set ("restores the behaviour from versions
	 * 0.8.6 and below"); 0.8.5, which has no such option, always expands them. By name, as Forge 47's Mixin lacks it.
	 */
	static boolean needed() {
		MixinEnvironment.Option expand;
		try {
			expand = MixinEnvironment.Option.valueOf("CLASSREADER_EXPAND_FRAMES");
		} catch (IllegalArgumentException e) {
			return false;
		}
		return !MixinEnvironment.getCurrentEnvironment().getOption(expand);
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
			if (fabricTarget == null) fabricTarget = targetedByFabricMod(info);
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

	/** Whether a mixin of a Fabric mod's config targets the class, as its metadata records the mixins targeting it. */
	private boolean targetedByFabricMod(ClassInfo info) {
		if (targetingMixins == null) return false;
		Object mixins;
		try {
			mixins = targetingMixins.invoke(info);
		} catch (ReflectiveOperationException | RuntimeException e) {
			targetingMixins = null;
			LOGGER.warn("Fabricated could not read which mixins target {} ({}): from now on the frames Forge's Mixin records stay as they are (CORPUS-79)",
					info.getName(), e.toString());
			return false;
		}
		if (!(mixins instanceof Set<?> targeting)) return false;
		Set<String> fabric = fabricConfigs();
		for (Object mixin : targeting) {
			if (mixin instanceof IMixinInfo m && m.getConfig() != null && fabric.contains(m.getConfig().getName())) return true;
		}
		return false;
	}

	private static Method targetingMixins() {
		try {
			Method method = ClassInfo.class.getDeclaredMethod("getMixins");
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Fabricated can't see which mixins target a class in this Mixin ({}): the frames Forge's Mixin records stay as they are, so a"
					+ " Fabric mod's injector taking a local after a frame that chops locals may find none (CORPUS-79)", e.toString());
			return null;
		}
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
