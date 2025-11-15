package dev.jbang.search;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Fuzz {

	public static <T> List<SearchFuzzedResult<T>> search(Collection<T> items, Scorer<T> scorer) {
		return search(items, new FuzzOptions<T>().scorer(scorer));
	}

	public static <T> List<SearchFuzzedResult<T>> search(Collection<T> items, FuzzOptions<T> opts) {
		return items.stream()
			.map(opts.scorer)
			.filter(s -> s.similarity() >= opts.similarityCutoff)
			.sorted(Comparator.comparing(SearchFuzzedResult<T>::similarity).reversed())
			.limit(opts.limit)
			.collect(Collectors.toList());
	}

	public static final class SearchFuzzedResult<T> {
		private final T item;
		private final SearchScorer matrix;
		private final int queryLength;
		private final int targetLength;

		public SearchFuzzedResult(T item, SearchScorer matrix, int queryLength, int targetLength) {
			this.item = item;
			this.matrix = matrix;
			this.queryLength = queryLength;
			this.targetLength = targetLength;
		}

		public T item() {
			return item;
		}

		public SearchScorer matrix() {
			return matrix;
		}

		public List<SearchScorer.Match> matches() {
			return matrix.matches();
		}

		public String target() {
			return matrix.target();
		}

		public String highlightTarget() {
			return matrix.highlightTarget();
		}

		public String highlightTarget(char marker) {
			return matrix.highlightTarget(marker);
		}

		public String highlightTarget(String startMarker, String endMarker) {
			return matrix.highlightTarget(startMarker, endMarker);
		}

		public int queryLength() {
			return queryLength;
		}

		public int targetLength() {
			return targetLength;
		}

		public String explain() {
			return String.format("%f, %f %s", distanceToTarget(), substringSimilarity(), highlightTarget());
		}

		public double similarity() {
			if (queryLength > targetLength) {
				return 0;
			}

			return (distanceToTarget() + substringSimilarity()) / 2;
		}

		private double distanceToTarget() {
			return (double) (targetLength - matrix.distance()) / (double) targetLength;
		}

		private double substringSimilarity() {
			return matrix.matches()
				.stream()
				.map(SearchScorer.Match::length)
				.filter(i -> i > 1)
				.mapToDouble(i -> (double) i)
				.sum() / (double) queryLength;
		}
	}

	public interface Scorer<T> extends Function<T, SearchFuzzedResult<T>> {
	}

	public static class FuzzOptions<T> {
		public double similarityCutoff = 0.2;
		public int limit = 20;
		public Scorer<T> scorer;

		public FuzzOptions<T> scorer(Scorer<T> scorer) {
			this.scorer = scorer;
			return this;
		}

		public FuzzOptions<T> limit(int limit) {
			this.limit = limit;
			return this;
		}

		public FuzzOptions<T> similarityCutoff(double similarityCutoff) {
			this.similarityCutoff = similarityCutoff;
			return this;
		}
	}
}