package io.github.cmdralph.fabricated.fabricloader.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

/**
 * Interval of versions. A {@code null} bound means unbounded on that side.
 *
 * <p>Set operations are exact for semantic versions. For non-semantic versions only point
 * intervals and the infinite interval can be combined, mirroring the limits of lexical ordering.
 */
public final class VersionIntervalImpl implements VersionInterval {
	private final Version min;
	private final boolean minInclusive;
	private final Version max;
	private final boolean maxInclusive;

	public VersionIntervalImpl(Version min, boolean minInclusive, Version max, boolean maxInclusive) {
		this.min = min;
		this.minInclusive = min != null && minInclusive;
		this.max = max;
		this.maxInclusive = max != null && maxInclusive;
	}

	@Override
	public boolean isSemantic() {
		return (min == null || min instanceof SemanticVersion) && (max == null || max instanceof SemanticVersion);
	}

	@Override
	public Version getMin() {
		return min;
	}

	@Override
	public boolean isMinInclusive() {
		return minInclusive;
	}

	@Override
	public Version getMax() {
		return max;
	}

	@Override
	public boolean isMaxInclusive() {
		return maxInclusive;
	}

	public static VersionInterval and(VersionInterval a, VersionInterval b) {
		if (a == null || b == null) return null;
		if (!a.isSemantic() || !b.isSemantic()) {
			return andPlain(a, b);
		}

		Bound lo = a.getMin() == null ? new Bound(b.getMin(), b.isMinInclusive())
				: b.getMin() == null ? new Bound(a.getMin(), a.isMinInclusive())
				: pick(a.getMin(), a.isMinInclusive(), b.getMin(), b.isMinInclusive(), true, true);
		Bound hi = a.getMax() == null ? new Bound(b.getMax(), b.isMaxInclusive())
				: b.getMax() == null ? new Bound(a.getMax(), a.isMaxInclusive())
				: pick(a.getMax(), a.isMaxInclusive(), b.getMax(), b.isMaxInclusive(), false, true);

		if (lo.version() != null && hi.version() != null) {
			int cmp = lo.version().compareTo(hi.version());
			if (cmp > 0 || cmp == 0 && (!lo.inclusive() || !hi.inclusive())) return null;
		}
		return new VersionIntervalImpl(lo.version(), lo.inclusive(), hi.version(), hi.inclusive());
	}

	private static VersionInterval andPlain(VersionInterval a, VersionInterval b) {
		if (isInfinite(a)) return b;
		if (isInfinite(b)) return a;
		if (Objects.equals(a.getMin(), b.getMin()) && Objects.equals(a.getMax(), b.getMax())
				&& a.isMinInclusive() == b.isMinInclusive() && a.isMaxInclusive() == b.isMaxInclusive()) {
			return a;
		}
		return null;
	}

	private static boolean isInfinite(VersionInterval interval) {
		return interval.getMin() == null && interval.getMax() == null;
	}

	public static List<VersionInterval> and(Collection<VersionInterval> a, Collection<VersionInterval> b) {
		if (a.isEmpty() || b.isEmpty()) return Collections.emptyList();
		List<VersionInterval> ret = new ArrayList<>();
		for (VersionInterval x : a) {
			for (VersionInterval y : b) {
				VersionInterval merged = and(x, y);
				if (merged != null) ret.add(merged);
			}
		}
		return normalize(ret);
	}

	public static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
		List<VersionInterval> ret = new ArrayList<>(a.size() + 1);
		ret.addAll(a);
		if (b != null) ret.add(b);
		return normalize(ret);
	}

	public static List<VersionInterval> not(VersionInterval interval) {
		if (interval == null) return Collections.singletonList(VersionInterval.INFINITE);
		if (isInfinite(interval)) return Collections.emptyList();
		if (!interval.isSemantic()) {
			// Lexical complements are not representable; return the unconstrained interval.
			return Collections.singletonList(VersionInterval.INFINITE);
		}
		List<VersionInterval> ret = new ArrayList<>(2);
		if (interval.getMin() != null) {
			ret.add(new VersionIntervalImpl(null, false, interval.getMin(), !interval.isMinInclusive()));
		}
		if (interval.getMax() != null) {
			ret.add(new VersionIntervalImpl(interval.getMax(), !interval.isMaxInclusive(), null, false));
		}
		return ret;
	}

	public static List<VersionInterval> not(Collection<VersionInterval> intervals) {
		List<VersionInterval> ret = Collections.singletonList(VersionInterval.INFINITE);
		for (VersionInterval interval : intervals) {
			ret = and(ret, not(interval));
		}
		return ret;
	}

	/** Sorts semantic intervals by lower bound and merges overlapping or touching ones. */
	private static List<VersionInterval> normalize(List<VersionInterval> intervals) {
		List<VersionInterval> semantic = new ArrayList<>();
		List<VersionInterval> other = new ArrayList<>();
		for (VersionInterval interval : intervals) {
			if (interval == null) continue;
			if (interval.isSemantic()) semantic.add(interval);
			else if (!other.contains(interval)) other.add(interval);
		}
		semantic.sort(Comparator.comparing(VersionInterval::getMin, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(i -> !i.isMinInclusive()));
		List<VersionInterval> merged = new ArrayList<>();
		for (VersionInterval cur : semantic) {
			if (merged.isEmpty()) {
				merged.add(cur);
				continue;
			}
			VersionInterval last = merged.getLast();
			if (touches(last, cur)) {
				merged.set(merged.size() - 1, union(last, cur));
			} else {
				merged.add(cur);
			}
		}
		merged.addAll(other);
		return merged;
	}

	private static boolean touches(VersionInterval a, VersionInterval b) {
		if (a.getMax() == null || b.getMin() == null) return true;
		int cmp = a.getMax().compareTo(b.getMin());
		return cmp > 0 || cmp == 0 && (a.isMaxInclusive() || b.isMinInclusive());
	}

	private static VersionInterval union(VersionInterval a, VersionInterval b) {
		Bound hi = a.getMax() == null || b.getMax() == null ? new Bound(null, false)
				: pick(a.getMax(), a.isMaxInclusive(), b.getMax(), b.isMaxInclusive(), true, false);
		return new VersionIntervalImpl(a.getMin(), a.isMinInclusive(), hi.version(), hi.inclusive());
	}

	/** One end of an interval; {@code version} is {@code null} when unbounded. */
	private record Bound(Version version, boolean inclusive) {
	}

	/**
	 * Picks the greater ({@code greater}) or lesser of two bounds. When they are equal the bound is inclusive if
	 * both are ({@code tieRequiresBoth}) or if either is.
	 */
	private static Bound pick(Version a, boolean aInclusive, Version b, boolean bInclusive, boolean greater, boolean tieRequiresBoth) {
		int cmp = a.compareTo(b);
		if (cmp == 0) return new Bound(a, tieRequiresBoth ? aInclusive && bInclusive : aInclusive || bInclusive);
		return cmp > 0 == greater ? new Bound(a, aInclusive) : new Bound(b, bInclusive);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof VersionInterval o
				&& Objects.equals(min, o.getMin()) && minInclusive == o.isMinInclusive()
				&& Objects.equals(max, o.getMax()) && maxInclusive == o.isMaxInclusive();
	}

	@Override
	public int hashCode() {
		return Objects.hash(min, minInclusive, max, maxInclusive);
	}

	@Override
	public String toString() {
		if (min == null && max == null) return "(-∞,∞)";
		return (minInclusive ? "[" : "(") + (min == null ? "-∞" : min.getFriendlyString()) + ","
				+ (max == null ? "∞" : max.getFriendlyString()) + (maxInclusive ? "]" : ")");
	}
}
