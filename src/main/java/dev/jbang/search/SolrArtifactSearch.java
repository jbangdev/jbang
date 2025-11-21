package dev.jbang.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.DefaultArtifact;

import com.google.gson.Gson;

/** Utility class for searching Maven artifacts via the Solr API. */
public class SolrArtifactSearch implements ArtifactSearch {
	private final String baseSearchUrl;
	private final boolean offsetInPages;

	public static SolrArtifactSearch createSmo() {
		return new SolrArtifactSearch("https://search.maven.org/solrsearch/select", false);
	}

	public static SolrArtifactSearch createCsc() {
		return new SolrArtifactSearch("https://central.sonatype.com/solrsearch/select", true);
	}

	private SolrArtifactSearch(String baseSearchUrl, boolean offsetInPages) {
		this.baseSearchUrl = baseSearchUrl;
		this.offsetInPages = offsetInPages;
	}

	/**
	 * Find artifacts matching the given pattern. This will return the first page of
	 * results. If the pattern to search for is a simple name (there are no colons
	 * in the string), the search will match any part of an artifact's group or
	 * name. If there's a single colon, the search will match any part of the group
	 * id and artifact id separately. If there are two colons, the search will match
	 * the group id and artifact id exactly, and will return the artifact's
	 * versions.
	 *
	 * @param artifactPattern The pattern to search for.
	 * @param count           The maximum number of results to return.
	 * @return The search result as an instance of
	 *         {@link ArtifactSearch.SearchResult}.
	 * @throws IOException If an error occurred during the search.
	 */
	public ArtifactSearch.SearchResult findArtifacts(String artifactPattern, int count) throws IOException {
		return select(artifactPattern, 0, count);
	}

	/**
	 * Find the next page of artifacts. This takes a
	 * {@link ArtifactSearch.SearchResult} returned by a previous call to
	 * {@link #findArtifacts(String, int)} and returns the next page of results.
	 *
	 * @param prevResult The previous search result.
	 * @return The next search result as an instance of
	 *         {@link ArtifactSearch.SearchResult}.
	 * @throws IOException If an error occurred during the search.
	 */
	public ArtifactSearch.SearchResult findNextArtifacts(ArtifactSearch.SearchResult prevResult)
			throws IOException {
		if (offsetInPages) {
			if ((prevResult.start + 1) * prevResult.count >= prevResult.total) {
				return null;
			}
		} else {
			if (prevResult.start + prevResult.count >= prevResult.total) {
				return null;
			}
		}
		int start = offsetInPages ? prevResult.start + 1 : prevResult.start + prevResult.count;
		ArtifactSearch.SearchResult result = select(prevResult.query, start, prevResult.count);
		return result.artifacts.isEmpty() ? null : result;
	}

	private ArtifactSearch.SearchResult select(String query, int start, int count) throws IOException {
		String finalQuery;
		String[] parts = query.startsWith("fc:") || query.startsWith("c:") ? new String[0] : query.split(":", -1);

		if (parts.length >= 3) {
			// Exact group/artifact match for retrieving versions
			finalQuery = String.format("g:%s AND a:%s", parts[0], parts[1]);
		} else if (parts.length == 2 && !query.startsWith("fc:")) {
			// Partial group/artifact match, we will filter the results
			// to remove those that match an inverted artifact/group
			finalQuery = String.format("%s %s", parts[0], parts[1]);
		} else {
			// Simple partial match
			finalQuery = query;
		}
		String searchUrl = String.format(
				this.baseSearchUrl + "?start=%d&rows=%d&q=%s",
				start,
				count,
				URLEncoder.encode(finalQuery, "UTF-8"));
		if (parts.length >= 3) {
			searchUrl += "&core=gav";
		}
		String agent = "blah";

		URL url = new URL(searchUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("User-Agent", agent);
		connection.setConnectTimeout(15000); // 15 seconds
		connection.setReadTimeout(60000); // 60 seconds

		int code = connection.getResponseCode();
		if (code != 200) {
			String err = "Search failed: Maven Central Search API returned an error: "
					+ code
					+ " "
					+ connection.getResponseMessage();
			if (code >= 500 && code < 600) {
				err += ". The service might be temporarily unavailable. You can try with a different search backend, run again using the -b option. ";
			}
			throw new IOException(err);
		}

		try (InputStream ins = connection.getInputStream()) {
			MvnSearchResult result = parseSearchResult(ins);
			List<DefaultArtifact> artifacts = result.response.docs.stream()
				.filter(d -> acceptDoc(d, parts))
				.map(SolrArtifactSearch::toArtifact)
				.collect(Collectors.toList());
			return new ArtifactSearch.SearchResult(
					artifacts, query, start, count, result.response.numFound);
		}
	}

	private static MvnSearchResult parseSearchResult(InputStream ins) throws IOException {
		Gson gson = new Gson();
		InputStreamReader rdr = new InputStreamReader(ins);
		MvnSearchResult result = gson.fromJson(rdr, MvnSearchResult.class);
		if (result.responseHeader.status != 0) {
			throw new IOException(
					"Search failed: Maven Search API did not return a valid response");
		}
		return result;
	}

	private static boolean acceptDoc(MsrDoc d, String[] parts) {
		return d.ec != null
				&& (d.ec.contains(".jar") || d.ec.contains("jar"))
				&& (parts.length != 2 || d.g.contains(parts[0]) && d.a.contains(parts[1]));
	}

	private static DefaultArtifact toArtifact(MsrDoc d) {
		return new DefaultArtifact(d.g, d.a, "", d.v != null ? d.v : d.latestVersion);
	}
}

class MvnSearchResult {
	public MsrHeader responseHeader;
	public MsrResponse response;
}

class MsrHeader {
	public int status;
}

class MsrResponse {
	public List<MsrDoc> docs;
	public int numFound;
	public int start;
}

class MsrDoc {
	public String g;
	public String a;
	public String v;
	public String latestVersion;
	public String p;
	public List<String> ec;
}
