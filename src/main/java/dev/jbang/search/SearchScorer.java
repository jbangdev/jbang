package dev.jbang.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchScorer {
	private final int[][] matrix;
	private final List<Match> matches;
	private final String query;
	private final String target;

	public SearchScorer(int[][] matrix, List<Match> matches, String query, String target) {
		this.matrix = matrix;
		this.matches = matches;
		this.query = query;
		this.target = target;
	}

	public int[][] matrix() {
		return matrix;
	}

	public List<Match> matches() {
		return matches;
	}

	public List<Integer> substringLengths() {
		if (matches.isEmpty()) {
			return Collections.emptyList();
		}

		List<Integer> lengths = new ArrayList<>(matches.size());

		for (Match match : matches) {
			lengths.add(match.length());
		}

		return lengths;
	}

	public int distance() {
		return matrix[matrix.length - 1][matrix[0].length - 1];
	}

	public static SearchScorer perfect() {
		return new SearchScorer(new int[][] { new int[] { 0 } }, Collections.<Match>emptyList(), "", "");
	}

	public static SearchScorer calculate(String query, String target) {
		int[][] matrix = levenshteinDistance(query, target);
		List<Match> matches = consecutiveSubstrings(query, target);

		return new SearchScorer(matrix, matches, query, target);
	}

	private static List<Match> consecutiveSubstrings(String query, String target) {
		List<Match> matches = new ArrayList<>();
		int max = 0;
		int pos = 0;
		int bestTargetStart = -1;
		for (int i = 0; i < query.length();) {
			for (int j = pos; j < target.length(); j++) {
				int subLen = 0;
				for (int k = 0; j + k < target.length()
						&& i + k < query.length()
						&& target.charAt(j + k) == query.charAt(i + k); k++) {
					subLen++;
				}
				if (subLen > max) {
					max = subLen;
					bestTargetStart = j;
				}
			}

			if (max > 0) {
				matches.add(new Match(i, bestTargetStart, max));
				i += max;
				pos = bestTargetStart + max;
				max = 0;
				bestTargetStart = -1;
			} else {
				i++;
			}
		}

		return matches;
	}

	public String query() {
		return query;
	}

	public String target() {
		return target;
	}

	public String highlightTarget() {
		return highlightTarget("\u001b[32m", "\u001b[0m");
	}

	public String highlightTarget(char marker) {
		String markerStr = String.valueOf(marker);
		return highlightTarget(markerStr, markerStr);
	}

	public String highlightTarget(String startMarker, String endMarker) {
		if (target == null || target.isEmpty() || matches.isEmpty()) {
			return target;
		}

		int estimatedSize = target.length() + matches.size() * (startMarker.length() + endMarker.length());
		StringBuilder highlighted = new StringBuilder(Math.max(estimatedSize, target.length()));
		int cursor = 0;

		for (Match match : matches) {
			int start = match.targetStart();
			int end = start + match.length();

			if (cursor < start) {
				highlighted.append(target, cursor, start);
			}

			highlighted.append(startMarker);
			highlighted.append(target, start, end);
			highlighted.append(endMarker);
			cursor = end;
		}

		if (cursor < target.length()) {
			highlighted.append(target.substring(cursor));
		}

		return highlighted.toString();
	}

	private static int[][] levenshteinDistance(String query, String target) {
		int[][] matrix = new int[query.length() + 1][target.length() + 1];
		// init matrix
		for (int i = 0; i < matrix.length; i++) {
			matrix[i][0] = i;
		}

		for (int i = 0; i < matrix[0].length; i++) {
			matrix[0][i] = i;
		}

		// algorithm
		for (int i = 1; i < query.length() + 1; i++) {
			for (int j = 1; j < target.length() + 1; j++) {
				if (query.charAt(i - 1) == target.charAt(j - 1)) {
					matrix[i][j] = matrix[i - 1][j - 1];
				} else {
					matrix[i][j] = min(matrix[i - 1][j - 1], matrix[i - 1][j], matrix[i][j - 1]) + 1;
				}
			}
		}

		return matrix;
	}

	private static int min(int... vals) {
		int minValue = Integer.MAX_VALUE;

		for (int i = 0; i < vals.length; i++) {
			if (vals[i] < minValue) {
				minValue = vals[i];
			}
		}

		return minValue;
	}

	public static final class Match {
		private final int queryStart;
		private final int targetStart;
		private final int length;

		public Match(int queryStart, int targetStart, int length) {
			this.queryStart = queryStart;
			this.targetStart = targetStart;
			this.length = length;
		}

		public int queryStart() {
			return queryStart;
		}

		public int targetStart() {
			return targetStart;
		}

		public int length() {
			return length;
		}
	}
}