package io.github.cmdralph.fabricated.forge.tag;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;

/**
 * Tag files' {@code fabric:remove} list ({@code fabric-tag-api-v1}) on Forge 47, which has the same feature under
 * {@code remove}: the entries are added to Forge's removals, which Forge's tag loader applies. Data written for either
 * loader then works, as on NeoForge. Forge's codec reads the file and the list is read from the same input, as a lenient
 * optional field (a list that doesn't read is left out, as Fabric's codec does).
 */
public final class FabricTagRemoval {
	private static final String KEY = "fabric:remove";

	private FabricTagRemoval() {
	}

	public static Codec<TagFile> codec(Codec<TagFile> forge) {
		return Codec.of(forge, new Decoder<>() {
			@Override
			public <T> DataResult<Pair<TagFile, T>> decode(DynamicOps<T> ops, T input) {
				return forge.decode(ops, input).map(pair -> pair.mapFirst(file -> merge(file, fabricRemove(ops, input))));
			}
		});
	}

	private static <T> List<TagEntry> fabricRemove(DynamicOps<T> ops, T input) {
		return ops.get(input, KEY).flatMap(list -> TagEntry.CODEC.listOf().parse(ops, list)).result().orElse(List.of());
	}

	private static TagFile merge(TagFile file, List<TagEntry> fabricRemove) {
		if (fabricRemove.isEmpty()) return file;
		List<TagEntry> remove = new ArrayList<>(file.remove());
		remove.addAll(fabricRemove);
		return new TagFile(file.entries(), file.replace(), List.copyOf(remove));
	}
}
