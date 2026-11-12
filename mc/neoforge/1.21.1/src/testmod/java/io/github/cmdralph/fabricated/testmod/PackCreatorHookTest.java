package io.github.cmdralph.fabricated.testmod;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * The data pack the test mod's mixin of upstream's mod resource pack creator offers ({@code ModResourcePackCreatorMixin}),
 * as YUNG's API offers its locate replacer's (PROVIDER-22): a pack of metadata alone, which no world selects by itself.
 * Once the server has started, its pack repository must have it among its packs.
 */
public final class PackCreatorHookTest {
	private static final String ID = "fabricated_testmod:creator_hook";
	private static final AtomicBoolean CHECKED = new AtomicBoolean();

	private PackCreatorHookTest() {
	}

	/** The pack, for the creator a pack repository got for server data; the first call sets up the check. */
	public static @Nullable Pack pack() {
		if (CHECKED.compareAndSet(false, true)) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				if (server.getPackRepository().isAvailable(ID)) {
					TestMod.LOGGER.info("FABRICATED-TEST mod resource pack creator hooks OK");
				} else {
					TestMod.LOGGER.error("FABRICATED-TEST mod resource pack creator hooks FAILED: the server's pack repository hasn't the pack offered at the end of loadPacks");
				}
			});
		}
		PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Fabricated test: mod resource pack creator hook"),
				PackSource.create(PackSource.NO_DECORATION, false), Optional.empty());
		Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
			@Override
			public PackResources openPrimary(PackLocationInfo location) {
				return new MetadataOnly(location);
			}

			@Override
			public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
				return new MetadataOnly(location);
			}
		};
		return Pack.readMetaAndCreate(info, resources, PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false));
	}

	/** A pack with its {@code pack.mcmeta} and nothing else. */
	private static final class MetadataOnly extends AbstractPackResources {
		MetadataOnly(PackLocationInfo location) {
			super(location);
		}

		@Override
		public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
			if (path.length != 1 || !PACK_META.equals(path[0])) return null;
			String meta = "{\"pack\":{\"description\":\"Fabricated test\",\"pack_format\":"
					+ SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA) + "}}";
			return () -> new ByteArrayInputStream(meta.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
			return null;
		}

		@Override
		public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			return Set.of();
		}

		@Override
		public void close() {
		}
	}
}
