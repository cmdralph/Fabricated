package io.github.cmdralph.fabricated.fabricloader.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Parses Fabric version predicates such as {@code >=1.2 <2}, {@code ~1.2}, {@code ^3}, {@code 1.2.x}
 * and {@code *}. Space-separated terms are combined with AND.
 */
public final class VersionPredicateParser {
	private static final VersionComparisonOperator[] OPERATORS = VersionComparisonOperator.values();

	private VersionPredicateParser() {
	}

	public static VersionPredicate parse(String predicate) throws VersionParsingException {
		Objects.requireNonNull(predicate, "predicate");
		List<SingleVersionPredicate> terms = new ArrayList<>();
		for (String rawTerm : predicate.split(" ")) {
			String term = rawTerm.trim();
			if (term.isEmpty() || term.equals("*")) continue;

			VersionComparisonOperator operator = VersionComparisonOperator.EQUAL;
			for (VersionComparisonOperator op : OPERATORS) {
				if (term.startsWith(op.getSerialized())) {
					operator = op;
					term = term.substring(op.getSerialized().length());
					break;
				}
			}

			Version version = VersionParser.parse(term, true);
			if (version instanceof SemanticVersion semVer) {
				if (semVer.hasWildcard()) {
					if (operator != VersionComparisonOperator.EQUAL) {
						throw new VersionParsingException("Invalid predicate: " + predicate
								+ ", version ranges with wildcards (.X) require using the equality operator or no operator at all!");
					}
					int count = semVer.getVersionComponentCount();
					int[] newComponents = new int[count - 1];
					for (int i = 0; i < count - 1; i++) {
						newComponents[i] = semVer.getVersionComponent(i);
					}
					version = new SemanticVersionImpl(newComponents, "", null);
					operator = count == 2 ? VersionComparisonOperator.SAME_TO_NEXT_MAJOR : VersionComparisonOperator.SAME_TO_NEXT_MINOR;
				}
			} else if (!operator.isMinInclusive() && !operator.isMaxInclusive()) {
				throw new VersionParsingException("Invalid predicate: " + predicate
						+ ", version ranges need to be semantic version compatible to use operators that exclude the bound!");
			}
			terms.add(new SingleVersionPredicate(operator, version));
		}

		if (terms.isEmpty()) return AnyVersionPredicate.INSTANCE;
		if (terms.size() == 1) return terms.getFirst();
		return new MultiVersionPredicate(terms);
	}

	public static List<VersionPredicate> parse(Collection<String> predicates) throws VersionParsingException {
		List<VersionPredicate> ret = new ArrayList<>(predicates.size());
		for (String predicate : predicates) {
			ret.add(parse(predicate));
		}
		return ret;
	}

	/** Matches every version. */
	public static final class AnyVersionPredicate implements VersionPredicate {
		public static final AnyVersionPredicate INSTANCE = new AnyVersionPredicate();

		private AnyVersionPredicate() {
		}

		@Override
		public boolean test(Version version) {
			Objects.requireNonNull(version, "version");
			return true;
		}

		@Override
		public Collection<? extends PredicateTerm> getTerms() {
			return Collections.emptyList();
		}

		@Override
		public VersionInterval getInterval() {
			return VersionInterval.INFINITE;
		}

		@Override
		public String toString() {
			return "*";
		}
	}

	/** One operator applied to one reference version. */
	public static final class SingleVersionPredicate implements VersionPredicate, VersionPredicate.PredicateTerm {
		private final VersionComparisonOperator operator;
		private final Version refVersion;

		public SingleVersionPredicate(VersionComparisonOperator operator, Version refVersion) {
			this.operator = operator;
			this.refVersion = refVersion;
		}

		@Override
		public boolean test(Version version) {
			Objects.requireNonNull(version, "version");
			return operator.test(version, refVersion);
		}

		@Override
		public List<PredicateTerm> getTerms() {
			return Collections.singletonList(this);
		}

		@Override
		public VersionInterval getInterval() {
			if (refVersion instanceof SemanticVersion semVer) {
				return new VersionIntervalImpl(operator.minVersion(semVer), operator.isMinInclusive(),
						operator.maxVersion(semVer), operator.isMaxInclusive());
			}
			return new VersionIntervalImpl(refVersion, true, refVersion, true);
		}

		@Override
		public VersionComparisonOperator getOperator() {
			return operator;
		}

		@Override
		public Version getReferenceVersion() {
			return refVersion;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof SingleVersionPredicate o && operator == o.operator && refVersion.equals(o.refVersion);
		}

		@Override
		public int hashCode() {
			return operator.ordinal() * 31 + refVersion.hashCode();
		}

		@Override
		public String toString() {
			return operator.getSerialized() + refVersion;
		}
	}

	/** Conjunction of several single predicates. */
	public static final class MultiVersionPredicate implements VersionPredicate {
		private final List<SingleVersionPredicate> predicates;

		public MultiVersionPredicate(List<SingleVersionPredicate> predicates) {
			this.predicates = List.copyOf(predicates);
		}

		@Override
		public boolean test(Version version) {
			Objects.requireNonNull(version, "version");
			for (SingleVersionPredicate predicate : predicates) {
				if (!predicate.test(version)) return false;
			}
			return true;
		}

		@Override
		public List<? extends PredicateTerm> getTerms() {
			return predicates;
		}

		@Override
		public VersionInterval getInterval() {
			VersionInterval ret = VersionInterval.INFINITE;
			for (SingleVersionPredicate predicate : predicates) {
				ret = VersionIntervalImpl.and(ret, predicate.getInterval());
				if (ret == null) return null;
			}
			return ret;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof MultiVersionPredicate o && predicates.equals(o.predicates);
		}

		@Override
		public int hashCode() {
			return predicates.hashCode();
		}

		@Override
		public String toString() {
			return predicates.stream().map(SingleVersionPredicate::toString).collect(Collectors.joining(" "));
		}
	}
}
