package io.github.cmdralph.fabricated.core.metadata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;

/** Immutable {@link ContactInformation} and {@link Person} implementations. */
public final class ContactInformationImpl implements ContactInformation {
	private final Map<String, String> map;

	public ContactInformationImpl(Map<String, String> map) {
		this.map = Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}

	@Override
	public Optional<String> get(String key) {
		return Optional.ofNullable(map.get(key));
	}

	@Override
	public Map<String, String> asMap() {
		return map;
	}

	/** A person with a name and optional contact information. */
	public record PersonImpl(String name, ContactInformation contact) implements Person {
		@Override
		public String getName() {
			return name;
		}

		@Override
		public ContactInformation getContact() {
			return contact;
		}
	}
}
