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

	/** Returns whether {@code version} lies within this interval. */
	public boolean contains(Version version) {
		if (min != null) {
			int cmp = version.compareTo(min);
			if (cmp < 0 || cmp == 0 && !minInclusive) return false;
		}
		if (max != null) {
			int cmp = version.compareTo(max);
			if (cmp > 0 || cmp == 0 && !maxInclusive) return false;
		}
		return true;
	}

	public static VersionInterval and(VersionInterval a, VersionInterval b) {
		if (a == null || b == null) return null;
		if (!a.isSemantic() || !b.isSemantic()) {
			return andPlain(a, b);
		}

		Version lo;
		boolean loInc;
		if (a.getMin() == null) {
			lo = b.getMin();
			loInc = b.isMinInclusive();
		} else if (b.getMin() == null) {
			lo = a.getMin();
			loInc = a.isMinInclusive();
		} else {
			int cmp = a.getMin().compareTo(b.getMin());
			if (cmp > 0) {
				lo = a.getMin();
				loInc = a.isMinInclusive();
			} else if (cmp < 0) {
				lo = b.getMin();
				loInc = b.isMinInclusive();
			} else {
				lo = a.getMin();
				loInc = a.isMinInclusive() && b.isMinInclusive();
			}
		}

		Version hi;
		boolean hiInc;
		if (a.getMax() == null) {
			hi = b.getMax();
			hiInc = b.isMaxInclusive();
		} else if (b.getMax() == null) {
			hi = a.getMax();
			hiInc = a.isMaxInclusive();
		} else {
			int cmp = a.getMax().compareTo(b.getMax());
			if (cmp < 0) {
				hi = a.getMax();
				hiInc = a.isMaxInclusive();
			} else if (cmp > 0) {
				hi = b.getMax();
				hiInc = b.isMaxInclusive();
			} else {
				hi = a.getMax();
				hiInc = a.isMaxInclusive() && b.isMaxInclusive();
			}
		}

		if (lo != null && hi != null) {
			int cmp = lo.compareTo(hi);
			if (cmp > 0 || cmp == 0 && (!loInc || !hiInc)) return null;
		}
		return new VersionIntervalImpl(lo, loInc, hi, hiInc);
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
		Version hi;
		boolean hiInc;
		if (a.getMax() == null || b.getMax() == null) {
			hi = null;
			hiInc = false;
		} else {
			int cmp = a.getMax().compareTo(b.getMax());
			if (cmp > 0) {
				hi = a.getMax();
				hiInc = a.isMaxInclusive();
			} else if (cmp < 0) {
				hi = b.getMax();
				hiInc = b.isMaxInclusive();
			} else {
				hi = a.getMax();
				hiInc = a.isMaxInclusive() || b.isMaxInclusive();
			}
		}
		return new VersionIntervalImpl(a.getMin(), a.isMinInclusive(), hi, hiInc);
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
