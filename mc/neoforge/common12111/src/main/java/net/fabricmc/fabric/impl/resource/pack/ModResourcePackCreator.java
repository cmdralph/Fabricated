package net.fabricmc.fabric.impl.resource.pack;

import java.util.function.Consumer;

import io.github.cmdralph.fabricated.neoforge.resource.BuiltinPacks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Fabricated's version of upstream's mod resource pack creator: the pack finder Fabricated gives each pack repository
 * NeoForge builds ({@link BuiltinPacks}), which offers the built-in packs Fabric mods register. NeoForge itself builds
 * the packs of mod resources (ids {@code mod/<mod ids>}), which upstream's offers too, so this doesn't. Its instance has
 * the part of upstream's shape mods' mixins of upstream's class hook (PROVIDER-22): the pack type, the constructor
 * taking it and the known-packs flag, and {@link #loadPacks}; YUNG's API keeps the pack type its constructor was given
 * and offers its locate replacer's data pack at the end of {@link #loadPacks} where that is server data. Upstream's
 * constructor taking the type alone, its fields of the flag and of the packs' selection, its client provider and its
 * {@code registerModPack} aren't here, so a mixin of those is still left out. {@link #RESOURCE_PACK_SOURCE} is the
 * pack source other Fabric API modules compare against: {@code Resource#getFabricPackSource} reports it for resources
 * from NeoForge's mod packs.
 */
public class ModResourcePackCreator implements RepositorySource {
	public static final PackSource RESOURCE_PACK_SOURCE = new PackSource() {
		@Override
		public Component decorate(Component packName) {
			return Component.translatable("pack.nameAndSource", packName, Component.translatable("pack.source.fabricmod"));
		}

		@Override
		public boolean shouldAddAutomatically() {
			return true;
		}
	};

	private final PackType type;

	/**
	 * @param forKnownPacksManager whether the repository is the one a client keeps the packs a server names in (NeoForge's
	 *                             trusted repository), where upstream's creator offers its mod packs without their
	 *                             parents; the built-in packs are offered alike to every repository
	 */
	public ModResourcePackCreator(PackType type, boolean forKnownPacksManager) {
		this.type = type;
	}

	@Override
	public void loadPacks(Consumer<Pack> consumer) {
		BuiltinPacks.offer(this.type, consumer);
	}
}
