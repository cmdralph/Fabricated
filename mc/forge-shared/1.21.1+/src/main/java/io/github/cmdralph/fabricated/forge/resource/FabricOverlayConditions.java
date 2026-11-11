package io.github.cmdralph.fabricated.forge.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.cmdralph.fabricated.core.runtime.FabricOverlays;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * fabric-resource-conditions-api-v1's conditional overlays ({@code fabric:overlays}) on Forge 52 and 61 to 66 (PACK-12): the
 * overlays vanilla's {@code Pack.readPackMetadata} reads from a pack's metadata, which Forge reads every mod file's pack
 * with, with those whose Fabric conditions hold after them, as core's {@link FabricOverlays} decides; Forge has no
 * conditional overlays of its own. Upstream's hook there, which the Fabric API plan leaves out, made the whole metadata
 * unreadable for a section it couldn't read, native mods' packs included (from Forge 64; Forge 52 and 61 logged an error
 * for it). The section is read as upstream's codec reads it, its conditions with upstream's own codec
 * ({@code ResourceCondition.CODEC}), tested as upstream tests them, without registries.
 */
public final class FabricOverlayConditions {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1");
	private static final FabricOverlays OVERLAYS = new FabricOverlays(null);

	private FabricOverlayConditions() {
	}

	/**
	 * A pack's overlays with Fabric's.
	 *
	 * @param pack     the pack's id
	 * @param overlays the overlays the game read from its metadata
	 * @param metadata the pack's {@code pack.mcmeta}, if it has one
	 */
	public static List<String> overlays(String pack, List<String> overlays, @Nullable IoSupplier<InputStream> metadata) {
		if (!ENABLED || metadata == null) return overlays;
		JsonObject root;
		try (Reader reader = new InputStreamReader(metadata.get(), StandardCharsets.UTF_8)) {
			root = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			// The game read the pack's metadata, from a file of its own or from code: there are no Fabric overlays here.
			return overlays;
		}
		return OVERLAYS.overlays(pack, overlays, root.keySet(), () -> Upstream.applied(root.get(FabricOverlays.SECTION)), LOGGER::warn);
	}

	/** What reads upstream's classes, which exist only once the module is loaded. */
	private static final class Upstream {
		/** Upstream's check of an overlay's directory name. */
		private static final Pattern DIRECTORY = Pattern.compile("[-_a-zA-Z0-9.]+");
		private static final Codec<List<Entry>> SECTION = RecordCodecBuilder.<Entry>create(entry -> entry.group(
						Codec.STRING.comapFlatMap(Upstream::directory, directory -> directory).fieldOf("directory").forGetter(Entry::directory),
						ResourceCondition.CODEC.fieldOf("condition").forGetter(Entry::condition))
				.apply(entry, Entry::new)).listOf().fieldOf("entries").codec();

		private record Entry(String directory, ResourceCondition condition) {
		}

		private static DataResult<String> directory(String name) {
			return DIRECTORY.matcher(name).matches() ? DataResult.success(name) : DataResult.error(() -> "Directory name is invalid: " + name);
		}

		static List<String> applied(JsonElement section) {
			List<String> applied = new ArrayList<>();
			for (Entry entry : SECTION.parse(JsonOps.INSTANCE, section).getOrThrow(IllegalArgumentException::new)) {
				if (entry.condition().test(null)) applied.add(entry.directory());
			}
			return applied;
		}
	}
}
