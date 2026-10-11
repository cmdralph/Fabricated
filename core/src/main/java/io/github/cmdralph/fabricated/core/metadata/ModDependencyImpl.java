package io.github.cmdralph.fabricated.core.metadata;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.fabricloader.version.VersionPredicateParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * A dependency declared in {@code fabric.mod.json}. The version requirement is a list of
 * predicates combined with OR; an empty list never matches (Fabric semantics).
 */
public final class ModDependencyImpl implements ModDependency {
	private final Kind kind;
	private final String modId;
	private final List<String> rawRequirements;
	private final List<VersionPredicate> predicates;

	public ModDependencyImpl(Kind kind, String modId, List<String> rawRequirements) throws VersionParsingException {
		this.kind = kind;
		this.modId = modId;
		this.rawRequirements = List.copyOf(rawRequirements);
		this.predicates = List.copyOf(VersionPredicateParser.parse(rawRequirements));
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	@Override
	public String getModId() {
		return modId;
	}

	@Override
	public boolean matches(Version version) {
		for (VersionPredicate predicate : predicates) {
			if (predicate.test(version)) return true;
		}
		return false;
	}

	@Override
	public Collection<VersionPredicate> getVersionRequirements() {
		return predicates;
	}

	@Override
	public List<VersionInterval> getVersionIntervals() {
		List<VersionInterval> ret = List.of();
		for (VersionPredicate predicate : predicates) {
			ret = VersionInterval.or(ret, predicate.getInterval());
		}
		return ret;
	}

	/** The requirement exactly as written, for messages. */
	public String requirementString() {
		if (rawRequirements.isEmpty()) return "(none)";
		return rawRequirements.stream().map(r -> r.isBlank() || r.equals("*") ? "any version" : r)
				.collect(Collectors.joining(" or "));
	}

	@Override
	public String toString() {
		return kind.getKey() + " " + modId + " " + requirementString();
	}
}
