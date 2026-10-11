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
 */
public final class FabricModMetadata implements ModMetadata {
	/** One entrypoint declaration. */
	public record EntrypointSpec(String adapter, String value) {
	}

	/** One mixin configuration and the environment it applies to. */
	public record MixinEntry(String config, ModEnvironment environment) {
	}

	private final String type;
	private final String id;
	private final Version version;
	private final List<String> provides;
	private final ModEnvironment environment;
	private final Map<String, List<EntrypointSpec>> entrypoints;
	private final List<String> nestedJars;
	private final List<MixinEntry> mixins;
	private final String classTweaker;
	private final List<ModDependency> dependencies;
	private final String name;
	private final String description;
	private final List<Person> authors;
	private final List<Person> contributors;
	private final ContactInformation contact;
	private final List<String> license;
	private final SortedMap<Integer, String> icons;
	private final Map<String, String> languageAdapters;
	private final Map<String, CustomValue> customValues;

	FabricModMetadata(String type, String id, Version version, List<String> provides, ModEnvironment environment,
			Map<String, List<EntrypointSpec>> entrypoints, List<String> nestedJars, List<MixinEntry> mixins,
			String classTweaker, List<ModDependency> dependencies, String name, String description,
			List<Person> authors, List<Person> contributors, ContactInformation contact, List<String> license,
			SortedMap<Integer, String> icons, Map<String, String> languageAdapters,
			Map<String, CustomValue> customValues) {
		this.type = type;
		this.id = id;
		this.version = version;
		this.provides = List.copyOf(provides);
		this.environment = environment;
		this.entrypoints = Collections.unmodifiableMap(entrypoints);
		this.nestedJars = List.copyOf(nestedJars);
		this.mixins = List.copyOf(mixins);
		this.classTweaker = classTweaker;
		this.dependencies = List.copyOf(dependencies);
		this.name = name;
		this.description = description;
		this.authors = List.copyOf(authors);
		this.contributors = List.copyOf(contributors);
		this.contact = contact;
		this.license = List.copyOf(license);
		this.icons = Collections.unmodifiableSortedMap(new TreeMap<>(icons));
		this.languageAdapters = Collections.unmodifiableMap(languageAdapters);
		this.customValues = Collections.unmodifiableMap(customValues);
	}

	/**
	 * Creates metadata for a mod that is not a Fabric mod (Minecraft, NeoForge, Java, or a NeoForge
	 * mod) so it can be queried through Fabric's API.
	 */
	public static FabricModMetadata builtin(String id, Version version, String name, String description) {
		return new FabricModMetadata("builtin", id, version, List.of(), ModEnvironment.UNIVERSAL, Map.of(), List.of(),
				List.of(), null, List.of(), name, description, List.of(), List.of(), ContactInformation.EMPTY,
				List.of(), new TreeMap<>(), Map.of(), Map.of());
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

	public Map<String, List<EntrypointSpec>> entrypoints() {
		return entrypoints;
	}

	public List<String> nestedJars() {
		return nestedJars;
	}

	public List<MixinEntry> mixins() {
		return mixins;
	}

	/** The access widener / class tweaker path inside the jar, or {@code null}. */
	public String classTweaker() {
		return classTweaker;
	}

	public Map<String, String> languageAdapters() {
		return languageAdapters;
	}

	public SortedMap<Integer, String> icons() {
		return icons;
	}

	@Override
	public String toString() {
		return id + " " + version.getFriendlyString();
	}
}
