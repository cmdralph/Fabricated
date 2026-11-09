package io.github.cmdralph.fabricated.forge;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.objectweb.asm.Opcodes;
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
 * Records the frames of a method again, expanded, where Forge's Mixin would count its locals wrong (CORPUS-79,
 * {@link RecordedFrames}): before mixins apply to a class a Fabric mod's mixin targets, each method whose locals Mixin's
 * local computation would count wrong at some frame, from the frames it recorded and walks as Forge's ModLauncher hands
 * them, compressed, gets its recorded frames from the method with its frames expanded, through Mixin's own
 * {@code ClassInfo.Method}. Mixin records an expanded frame's size as the locals it states and counts them right, as with
 * its {@code CLASSREADER_EXPAND_FRAMES} option. Resourceful Lib's {@code @Local PoseStack} in Forge 61's and 64's
 * {@code ScreenEffectRenderer.renderScreenEffect} found none without it, and the client crashed.
 *
 * <p>A class no Fabric mod's mixin targets is left alone, and so is a method Mixin counts right, so Forge mods' mixins see
 * Mixin's frames as on Forge without Fabricated; a Forge mod's injector in a method this records again sees the locals
 * there are. Which mixins target the class is the class metadata's own record
 * ({@code ClassInfo.getMixins()}, package-private in Mixin, where each mixin adds itself as Mixin prepares it): Mixin takes
 * each config it selects out of {@code Mixins.getConfigs()}, so that set no longer has them as mixins apply. Where the
 * record can't be read, nothing is recorded again.
 *
 * <p>Registered only where Mixin records frames unexpanded ({@link #needed}): Forge 61 to 66's Mixin 0.8.7, which stopped
 * expanding them by default. Forge 47's Mixin 0.8.5 reads a class's metadata with its frames expanded, so it records each
 * frame's whole locals and counts them right.
 */
final class RecordedFrameRepair implements IExtension {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MIXIN_CONFIGS = "MixinConfigs";

	private final FabricatedState state;
	/** {@code ClassInfo.getMixins()}, the mixins targeting a class; null where it can't be read, and nothing is recorded again. */
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
		int recorded = 0;
		for (MethodNode method : node.methods) {
			// Found as Mixin's local computation finds it (Locals.getLocalsAt).
			ClassInfo.Method found = info.findMethod(method, method.access | ClassInfo.INCLUDE_INITIALISERS);
			List<ClassInfo.FrameData> frames = found == null ? null : found.getFrames();
			if (frames == null || !changesSize(frames)) continue;
			if (fabricTarget == null) fabricTarget = targetedByFabricMod(info);
			if (!fabricTarget) return;
			try {
				List<ClassInfo.FrameData> expanded = info.new Method(RecordedFrames.withExpandedFrames(node.name, method), false).getFrames();
				if (Arrays.equals(RecordedFrames.counted(method, read(frames)), RecordedFrames.counted(method, read(expanded)))) continue;
				frames.clear();
				frames.addAll(expanded);
				recorded++;
			} catch (RuntimeException e) {
				LOGGER.warn("Fabricated could not record the frames of {}.{}{} again ({}): Forge's Mixin keeps the frames it recorded, and may count the"
						+ " method's locals wrong for a Fabric mod's injector (CORPUS-79)", node.name, method.name, method.desc, e.toString());
			}
		}
		if (recorded > 0) {
			LOGGER.debug("Fabricated recorded the frames of {} method(s) of {} again, expanded, where Forge's Mixin would count their locals wrong (CORPUS-79)",
					recorded, node.name);
		}
	}

	/**
	 * Whether Mixin recorded a frame of the method as a change in size (an append or a chop), which its local computation
	 * can count wrong; a full frame states its own size.
	 */
	private static boolean changesSize(List<ClassInfo.FrameData> frames) {
		for (ClassInfo.FrameData frame : frames) {
			if (frame.type == Opcodes.F_APPEND || frame.type == Opcodes.F_CHOP) return true;
		}
		return false;
	}

	private static List<RecordedFrames.Frame> read(List<ClassInfo.FrameData> frames) {
		List<RecordedFrames.Frame> read = new ArrayList<>(frames.size());
		for (ClassInfo.FrameData frame : frames) read.add(new RecordedFrames.Frame(frame.type, frame.size));
		return read;
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
					+ " Fabric mod's injector taking a local where Forge's Mixin miscounts them may find the wrong ones (CORPUS-79)", e.toString());
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
				// Not a Fabric mod's config then, here: its classes keep Mixin's frames.
				LOGGER.warn("Fabricated could not read the mixin configs of {}'s translation ({}): {}", mod.translated().candidate().id(), overlay, e.toString());
			}
		}
		fabricConfigs = names;
		return names;
	}
}
