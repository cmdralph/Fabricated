package io.github.cmdralph.fabricated.core.metadata;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

/**
 * Parsed {@code fabric.mod.json} (schema 1), exposed through Fabric's own {@link ModMetadata} API so
 * mods querying other mods' metadata see the familiar types.
 *
 * @param classTweaker the access widener / class tweaker path inside the jar, or {@code null}
 * @param compatHints  the mod's own instructions for Fabricated ({@code custom.fabricated})
 */
public record FabricModMetadata(
		String type,
		String id,
		Version version,
		List<String> provides,
		ModEnvironment environment,
		Map<String, List<EntrypointSpec>> entrypoints,
		List<String> nestedJars,
		List<MixinEntry> mixins,
		String classTweaker,
		List<ModDependency> dependencies,
		String name,
		String description,
		List<Person> authors,
		List<Person> contributors,
		ContactInformation contact,
		List<String> license,
		SortedMap<Integer, String> icons,
		Map<String, String> languageAdapters,
		Map<String, CustomValue> customValues,
		CompatHints compatHints) implements ModMetadata {
	/** One entrypoint declaration. */
	public record EntrypointSpec(String adapter, String value) {
	}

	/** One mixin configuration and the environment it applies to. */
	public record MixinEntry(String config, ModEnvironment environment) {
	}

	public FabricModMetadata {
		provides = List.copyOf(provides);
		entrypoints = Collections.unmodifiableMap(entrypoints);
		nestedJars = List.copyOf(nestedJars);
		mixins = List.copyOf(mixins);
		dependencies = List.copyOf(dependencies);
		authors = List.copyOf(authors);
		contributors = List.copyOf(contributors);
		license = List.copyOf(license);
		icons = Collections.unmodifiableSortedMap(new TreeMap<>(icons));
		languageAdapters = Collections.unmodifiableMap(languageAdapters);
		customValues = Collections.unmodifiableMap(customValues);
	}

	/**
	 * Creates metadata for a mod that is not a Fabric mod (Minecraft, NeoForge, Java, or a NeoForge
	 * mod) so it can be queried through Fabric's API.
	 */
	public static FabricModMetadata builtin(String id, Version version, String name, String description, Map<String, CustomValue> customValues) {
		return new FabricModMetadata("builtin", id, version, List.of(), ModEnvironment.UNIVERSAL, Map.of(), List.of(),
				List.of(), null, List.of(), name, description, List.of(), List.of(), ContactInformation.EMPTY,
				List.of(), new TreeMap<>(), Map.of(), customValues, CompatHints.NONE);
	}

	/** This metadata with other mixin configs (one the mod declares that a jar nested in it holds goes to that jar). */
	public FabricModMetadata withMixins(List<MixinEntry> mixins) {
		return new FabricModMetadata(type, id, version, provides, environment, entrypoints, nestedJars, mixins, classTweaker, dependencies,
				name, description, authors, contributors, contact, license, icons, languageAdapters, customValues, compatHints);
	}

	/** This metadata with other compatibility hints (none, when the player turned the mod's off). */
	public FabricModMetadata withCompatHints(CompatHints hints) {
		return new FabricModMetadata(type, id, version, provides, environment, entrypoints, nestedJars, mixins, classTweaker, dependencies,
				name, description, authors, contributors, contact, license, icons, languageAdapters, customValues, hints);
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Collection<String> getProvides() {
		return provides;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		return environment;
	}

	@Override
	public Collection<ModDependency> getDependencies() {
		return dependencies;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public Collection<Person> getAuthors() {
		return authors;
	}

	@Override
	public Collection<Person> getContributors() {
		return contributors;
	}

	@Override
	public ContactInformation getContact() {
		return contact;
	}

	@Override
	public Collection<String> getLicense() {
		return license;
	}

	/** Picks the smallest icon at least {@code size} pixels wide, else the largest available. */
	@Override
	public Optional<String> getIconPath(int size) {
		if (icons.isEmpty()) return Optional.empty();
		SortedMap<Integer, String> tail = icons.tailMap(size);
		return Optional.of(tail.isEmpty() ? icons.get(icons.lastKey()) : tail.get(tail.firstKey()));
	}

	@Override
	public boolean containsCustomValue(String key) {
		return customValues.containsKey(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return customValues.get(key);
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		return customValues;
	}

	@Override
	@Deprecated
	public boolean containsCustomElement(String key) {
		return containsCustomValue(key);
	}

	@Override
	public String toString() {
		return id + " " + version.getFriendlyString();
	}
}
