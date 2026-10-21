package io.github.cmdralph.fabricated.core.remap;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * Vanilla's game classes in Mojang's names, read from its obfuscated jar with Mojang's ProGuard mappings (VANILLA-2):
 * 1.21.11's game jar names classes and members as the obfuscator left them, and NeoForge's game, which pre-flight
 * compares them with, runs in Mojang's. Obfuscated member names repeat across classes, so a member is looked up where
 * it is declared, up the vanilla jar's own class hierarchy. The mappings are read when the first class is; when they
 * can't be, no class is.
 */
public final class OfficialClasses implements ClassBytesSource {
	/** Opens Mojang's ProGuard mappings for the game. */
	@FunctionalInterface
	public interface MappingsSource {
		Reader open() throws IOException;
	}

	private final ClassBytesSource official;
	private final MappingsSource mappings;
	private final Map<String, List<String>> supertypes = new ConcurrentHashMap<>();
	private MappingComposer.Proguard proguard;
	private boolean unreadable;

	/**
	 * @param official the vanilla jar's classes, by obfuscated internal name
	 */
	public OfficialClasses(ClassBytesSource official, MappingsSource mappings) {
		this.official = official;
		this.mappings = mappings;
	}

	@Override
	public byte[] read(String mojangName) throws IOException {
		MappingComposer.Proguard pg = proguard();
		if (pg == null) return null;
		byte[] data = official.read(pg.mojangToOfficial.getOrDefault(mojangName, mojangName));
		if (data == null) return null;
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(data).accept(new ClassRemapper(writer, new ToMojang(pg)), 0);
		return writer.toByteArray();
	}

	private synchronized MappingComposer.Proguard proguard() {
		if (proguard == null && !unreadable) {
			try (Reader reader = mappings.open()) {
				proguard = MappingComposer.readProguard(reader);
			} catch (IOException | MappingException | RuntimeException e) {
				unreadable = true; // pre-flight then reads no vanilla code, as where none was found
			}
		}
		return proguard;
	}

	/** Official names to Mojang's; a member's name is the one Mojang gave it in the class declaring it. */
	private final class ToMojang extends Remapper {
		private final MappingComposer.Proguard pg;

		ToMojang(MappingComposer.Proguard pg) {
			this.pg = pg;
		}

		@Override
		public String map(String internalName) {
			return pg.classes.getOrDefault(internalName, internalName);
		}

		@Override
		public String mapMethodName(String owner, String name, String descriptor) {
			if (name.startsWith("<")) return name;
			String mapped = declared(owner, name + descriptor, pg.methods);
			return mapped != null ? mapped : name;
		}

		@Override
		public String mapFieldName(String owner, String name, String descriptor) {
			String mapped = declared(owner, name + descriptor, pg.fields);
			return mapped != null ? mapped : name;
		}

		private String declared(String owner, String member, Map<String, String> names) {
			Deque<String> queue = new ArrayDeque<>(List.of(owner));
			Set<String> seen = new HashSet<>();
			while (!queue.isEmpty()) {
				String type = queue.poll();
				if (!seen.add(type)) continue;
				String mapped = names.get(type + "." + member);
				if (mapped != null) return mapped;
				queue.addAll(supertypes(type));
			}
			return null;
		}
	}

	/** The superclass and interfaces of a vanilla class, by obfuscated name; none for a class the jar doesn't have. */
	private List<String> supertypes(String type) {
		return supertypes.computeIfAbsent(type, t -> {
			List<String> found = new ArrayList<>();
			try {
				byte[] data = official.read(t);
				if (data == null) return found;
				ClassReader reader = new ClassReader(data);
				if (reader.getSuperName() != null) found.add(reader.getSuperName());
				found.addAll(List.of(reader.getInterfaces()));
			} catch (IOException | RuntimeException e) {
				// An unreadable class ends the walk there.
			}
			return found;
		});
	}
}
