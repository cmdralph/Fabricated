package io.github.cmdralph.fabricated.core.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.github.cmdralph.fabricated.core.remap.MappingTable;
import net.fabricmc.loader.api.MappingResolver;

/** {@link MappingResolver} implementations for unobfuscated and intermediary-based versions. */
public final class MappingResolvers {
	private MappingResolvers() {
	}

	/** Unobfuscated Minecraft: every namespace is the runtime namespace {@code official}. */
	public static MappingResolver identity() {
		return new MappingResolver() {
			@Override
			public Collection<String> getNamespaces() {
				return List.of("official");
			}

			@Override
			public String getCurrentRuntimeNamespace() {
				return "official";
			}

			@Override
			public String mapClassName(String namespace, String className) {
				return className;
			}

			@Override
			public String unmapClassName(String targetNamespace, String className) {
				return className;
			}

			@Override
			public String mapFieldName(String namespace, String owner, String name, String descriptor) {
				return name;
			}

			@Override
			public String mapMethodName(String namespace, String owner, String name, String descriptor) {
				return name;
			}
		};
	}

	/**
	 * Obfuscated Minecraft running with Mojang names. Mods ask to map from {@code intermediary}; the
	 * runtime namespace is reported as {@code named} so mods that check it call the map methods.
	 */
	public static MappingResolver intermediary(MappingTable table) {
		Objects.requireNonNull(table, "table");
		return new MappingResolver() {
			@Override
			public Collection<String> getNamespaces() {
				return List.of("intermediary", "named");
			}

			@Override
			public String getCurrentRuntimeNamespace() {
				return "named";
			}

			@Override
			public String mapClassName(String namespace, String className) {
				if (!"intermediary".equals(namespace)) return className;
				return table.mapClass(className.replace('.', '/')).replace('/', '.');
			}

			@Override
			public String unmapClassName(String targetNamespace, String className) {
				if (!"intermediary".equals(targetNamespace)) return className;
				return table.unmapClass(className.replace('.', '/')).replace('/', '.');
			}

			@Override
			public String mapFieldName(String namespace, String owner, String name, String descriptor) {
				if (!"intermediary".equals(namespace)) return name;
				return table.mapMember(owner.replace('.', '/'), name, descriptor);
			}

			@Override
			public String mapMethodName(String namespace, String owner, String name, String descriptor) {
				if (!"intermediary".equals(namespace)) return name;
				return table.mapMember(owner.replace('.', '/'), name, descriptor);
			}
		};
	}
}
