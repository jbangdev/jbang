package dev.jbang.catalog;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.jbang.util.Util;

public class ImplicitCatalogRef {
	private static final String GITHUB_URL = "https://github.com/";
	private static final String GITLAB_URL = "https://gitlab.com/";
	private static final String BITBUCKET_URL = "https://bitbucket.org/";
	final String org;
	final String repo;
	final String ref; // Branch or Commit
	final String path;

	private ImplicitCatalogRef(String org, String repo, String ref, String path) {
		this.org = org;
		this.repo = repo;
		this.ref = ref;
		this.path = path;
	}

	public boolean isPossibleCommit() {
		return ref.matches("[0-9a-f]{5,40}");
	}

	public String url(String host, String infix) {
		return host + org + "/" + repo + infix + ref + "/" + path + Catalog.JBANG_CATALOG_JSON;
	}

	/**
	 * Return ImplicitCatalogRef if can be parsed into a git accessible structure.
	 * 
	 * @param name
	 * @return null if cannot parse it
	 */
	public static ImplicitCatalogRef parse(String name) {
		if (Util.isURL(name)) {
			return null;
		}
		if (name.startsWith("/")) {
			name = Catalog.JBANG_DEFAULT_CATALOG + name;
		}
		String[] parts = name.split("~", 2);
		String path;
		if (parts.length == 2) {
			path = parts[1] + "/";
		} else {
			path = "";
		}
		String[] names = parts[0].split("/");
		if (names.length > 3) {
			return null;
		}
		String org = names[0];
		String repo;
		if (names.length >= 2 && !names[1].isEmpty()) {
			repo = names[1];
		} else {
			repo = Catalog.JBANG_CATALOG_REPO;
		}
		String ref;
		if (names.length == 3 && !names[2].isEmpty()) {
			ref = names[2];
		} else {
			ref = Catalog.DEFAULT_REF;
		}
		return new ImplicitCatalogRef(org, repo, ref, path);
	}

	public static Optional<String> getImplicitCatalogUrl(String catalogName) {
		Optional<ImplicitCatalogRef> icr = Optional.ofNullable(parse(catalogName));
		Optional<String> url = chain(
				() -> Util.isURL(catalogName) ? tryDownload(catalogName) : Optional.empty(),
				() -> catalogName.contains(".") ? tryDownload("https://" + catalogName) : Optional.empty(),
				() -> icr.isPresent() ? tryDownload(icr.get().url(GITHUB_URL, "/blob/")) : Optional.empty(),
				() -> icr.isPresent() && icr.get().isPossibleCommit() ? tryDownload(icr.get().url(GITHUB_URL, "/blob/"))
						: Optional.empty(),
				() -> icr.isPresent() ? tryDownload(icr.get().url(GITLAB_URL, "/-/blob/")) : Optional.empty(),
				() -> icr.isPresent() && icr.get().isPossibleCommit()
						? tryDownload(icr.get().url(GITLAB_URL, "/-/blob/"))
						: Optional.empty(),
				() -> icr.isPresent() ? tryDownload(icr.get().url(BITBUCKET_URL, "/src/")) : Optional.empty(),
				() -> icr.isPresent() && icr.get().isPossibleCommit()
						? tryDownload(icr.get().url(BITBUCKET_URL, "/src/"))
						: Optional.empty())
											.findFirst();
		return url;
	}

	private static Optional<String> tryDownload(String url) {
		try {
			Catalog.getByRef(url);
			Util.verboseMsg("Catalog found at " + url);
			return Optional.of(url);
		} catch (Exception ex) {
			Util.verboseMsg("No catalog found at " + url, ex);
			return Optional.empty();
		}
	}

	@SafeVarargs
	public static <T> Stream<T> chain(Supplier<Optional<T>>... suppliers) {
		return Arrays	.stream(suppliers)
						.map(Supplier::get)
						.flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty));
	}
}
