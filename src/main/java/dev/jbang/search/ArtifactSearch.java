package dev.jbang.search;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;

public interface ArtifactSearch {

	/**
	 * Find artifacts matching the given pattern. This will return the first page of
	 * results. If the pattern to search for is a simple name (there are no colons
	 * in the string), the search will match any part of an artifact's group or
	 * name. If there's a single colon, the search will match any part of the group
	 * id and artifact id separately. If there are two colons, the search will match
	 * the group id and artifact id exactly, and will return the artifact's
	 * versions. If the pattern starts with "fc:", the search will match the full
	 * class name. If the pattern starts with "c:", the search will match the class
	 * name.
	 *
	 * @param artifactPattern The pattern to search for.
	 * @param count           The maximum number of results to return.
	 * @return The search result as an instance of {@link SearchResult}.
	 * @throws IOException If an error occurred during the search.
	 */
	SearchResult findArtifacts(String artifactPattern, int count) throws IOException;

	/**
	 * Find the next page of artifacts. This takes a {@link SearchResult} returned
	 * by a previous call to {@link #findArtifacts(String, int)} and returns the
	 * next page of results.
	 *
	 * @param prevResult The previous search result.
	 * @return The next search result as an instance of {@link SearchResult}.
	 * @throws IOException If an error occurred during the search.
	 */
	SearchResult findNextArtifacts(SearchResult prevResult) throws IOException;

	enum Backends {
		rest_smo,
		rest_csc;
		// smo_smo,
		// smo_csc;
	}

	static ArtifactSearch getBackend(Backends backend) {
		if (backend != null) {
			switch (backend) {
			case rest_smo:
				return SolrArtifactSearch.createSmo();
			case rest_csc:
				return SolrArtifactSearch.createCsc();
			// case smo_smo:
			// return SearchSmoApiImpl.createSmo();
			// case smo_csc:
			// return SearchSmoApiImpl.createCsc();
			}
		}
		return SolrArtifactSearch.createSmo();
	}

	/**
	 * Hold the result of a search while also functioning as a kind of bookmark for
	 * paging purposes.
	 */
	class SearchResult {
		/** The artifacts that matched the search query. */
		public final List<? extends Artifact> artifacts;

		/** The search query that produced this result. */
		public final String query;

		/**
		 * The index of the first artifact in this result relative to the total result
		 * set.
		 */
		public final int start;

		/** The maximum number of results to return */
		public final int count;

		/** The total number of artifacts that matched the search query. */
		public final int total;

		/**
		 * Create a new search result.
		 *
		 * @param artifacts The artifacts that matched the search query.
		 * @param query     The search query that produced this result.
		 * @param start     The index of the first artifact in this result relative to
		 *                  the total result set.
		 * @param count     The maximum number of results to return.
		 * @param total     The total number of artifacts that matched the search query.
		 */
		public SearchResult(
				List<? extends Artifact> artifacts, String query, int start, int count, int total) {
			this.artifacts = Collections.unmodifiableList(artifacts);

			this.query = query;
			this.start = start;
			this.count = count;
			this.total = total;
		}
	}
}
