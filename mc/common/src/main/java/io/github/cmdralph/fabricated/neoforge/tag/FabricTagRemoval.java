package io.github.cmdralph.fabricated.neoforge.tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;

/**
 * Tag files' {@code fabric:remove} list ({@code fabric-tag-api-v1}) on NeoForge, which has the same feature under
 * {@code remove}: the entries are added to NeoForge's removals, which NeoForge's tag loader applies. Data written for
 * either loader then works.
 */
public final class FabricTagRemoval {
	private FabricTagRemoval() {
	}

	public static Codec<TagFile> codec(Codec<TagFile> neoForge) {
		return RecordCodecBuilder.create(i -> i.group(
				MapCodec.assumeMapUnsafe(neoForge).forGetter(Function.identity()),
				TagEntry.CODEC.listOf().lenientOptionalFieldOf("fabric:remove", List.of()).forGetter(file -> List.of())
		).apply(i, FabricTagRemoval::merge));
	}

	private static TagFile merge(TagFile file, List<TagEntry> fabricRemove) {
		if (fabricRemove.isEmpty()) return file;
		List<TagEntry> remove = new ArrayList<>(file.remove());
		remove.addAll(fabricRemove);
		return new TagFile(file.entries(), file.replace(), List.copyOf(remove));
	}
}
