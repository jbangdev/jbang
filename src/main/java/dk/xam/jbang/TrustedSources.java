package dk.xam.jbang;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;

public class TrustedSources {

	final static Pattern rLocalhost = Pattern.compile("(?i)^localhost(:\\d+)?$");
	final static Pattern r127 = Pattern.compile("(?i)^127.0.0.1(:\\d+)?$");

	String trustedSources[];

	public TrustedSources(String[] trustedSources) {
		this.trustedSources = trustedSources;
	}

	public static TrustedSources load(Path toPath) throws IOException {
		String rawtd = Util.readString(toPath);
		Gson parser = new Gson();
		String[] strings = parser.fromJson(rawtd, String[].class);
		if (strings == null) {
			throw new IllegalStateException("Could not parse " + toPath);
		} else {
			return new TrustedSources(strings);
		}
	}

	private boolean isLocalhostAuthority(URI uri) {
		return uri.getScheme().equals("file") || rLocalhost.matcher(uri.getAuthority()).matches()
				|| r127.matcher(uri.getAuthority()).matches();
	}

	public String[] getTrustedSources() {
		return trustedSources;
	}

	/**
	 * Check whether a url like https://www.microsoft.com matches the list of
	 * trusted sources.
	 *
	 * - Schemes must match - There's no subdomain matching. For example
	 * https://jbang.dev doesn't match https://www.jbang.dev - Star matches all
	 * subdomains. For example https://*.jbang.dev matches https://www.jbang.dev and
	 * https://foo.bar.jbangdev.com
	 */
	public boolean isURLTrusted(URI url) throws URISyntaxException {
		if (isLocalhostAuthority(url)) {
			return true;
		}

		final String domain = String.format("%s://%s", url.getScheme(), url.getAuthority());

		for (String trustedSource : trustedSources) {
			if ("*".equals(trustedSource)) {
				return true;
			}

			if (domain.equals(trustedSource)) {
				return true;
			}

			URI parsedTrustedSource;
			if (trustedSource.startsWith("https://")) {
				parsedTrustedSource = new URI(trustedSource);
				if (!url.getScheme().equals(parsedTrustedSource.getScheme())) {
					continue;
				}
			} else {
				parsedTrustedSource = new URI("https://" + trustedSource);
			}

			if (url.getAuthority().equals(parsedTrustedSource.getAuthority())) {
				if (pathMatches(url.getPath(), parsedTrustedSource.getPath())) {
					return true;
				} else {
					continue;
				}
			}

			if (trustedSource.contains("*")) {

				String[] reversedAuthoritySegments = reverse(url.getAuthority().split("\\."));

				String[] reversedTrustedSourceAuthoritySegments = reverse(
						parsedTrustedSource.getAuthority().split("\\."));

				boolean ruleIsSmaller = reversedTrustedSourceAuthoritySegments.length < reversedAuthoritySegments.length;
				boolean ruleHasStarAtEnd = reversedTrustedSourceAuthoritySegments[reversedTrustedSourceAuthoritySegments.length
						- 1].equals("*");
				if (ruleIsSmaller && ruleHasStarAtEnd) {
					reversedAuthoritySegments = Arrays.copyOfRange(reversedAuthoritySegments, 0,
							reversedTrustedSourceAuthoritySegments.length);
				}

				boolean authorityMatches = true;
				for (int i = 0; i < reversedAuthoritySegments.length; i++) {
					String val = reversedAuthoritySegments[i];
					String elementRule = reversedTrustedSourceAuthoritySegments[i];
					if (elementRule.equals("*")
							|| val.equals(elementRule)) {
						// they match
					} else {
						authorityMatches = false;
						break;
					}
				}

				if (authorityMatches && pathMatches(url.getPath(), parsedTrustedSource.getPath())) {
					return true;
				}
			}

		}
		return false;
	}

	boolean pathMatches(String open, String rule) {
		if ("/".equals(rule)) {
			return true;
		}

		if (rule.endsWith("/")) {
			rule = rule.substring(0, rule.length() - 1);
		}

		String[] openSegments = open.split("/");
		String[] ruleSegments = rule.split("/");
		for (int i = 0; i < ruleSegments.length; i++) {
			if (openSegments.length - 1 < i || !ruleSegments[i].equals(openSegments[i])) {
				return false;
			}
		}

		return true;
	}

	/**
	 * in place reverse of array
	 * 
	 * @param array array to reverse
	 * @param <T>
	 * @return reversed array
	 */
	static <T> T[] reverse(T[] array) {
		int i, k;
		T temp;
		for (i = 0; i < array.length / 2; i++) {
			temp = array[i];
			array[i] = array[array.length - i - 1];
			array[array.length - i - 1] = temp;
		}
		return array;
	}

	String getJSon(Collection<String> rules) {

		rules = rules	.stream()
						.map(s -> new JsonPrimitive(s).toString())
						.collect(Collectors.toCollection(LinkedHashSet::new));

		String trustedsources = Settings.getTemplateEngine()
										.getTemplate("trusted-sources.json.qute")
										.data("trustedsources", rules)
										.render();
		return trustedsources;
	}

	public void add(List<String> trust, File storage) {

		Util.info("Adding " + trust + " to " + storage);

		Set<String> newrules = new LinkedHashSet<String>();

		newrules.addAll(Arrays.asList(trustedSources));

		if (newrules.addAll(trust)) {
			save(newrules, storage);
		} else {
			Util.warnMsg("Already trusted source(s). No changes made.");
		}

	}

	public void remove(List<String> trust, File storage) {

		Util.info("Removing " + trust + " from " + storage);

		Set<String> newrules = new LinkedHashSet<String>();

		newrules.addAll(Arrays.asList(trustedSources));

		if (newrules.removeAll(trust)) {
			save(newrules, storage);
		} else {
			Util.warnMsg("Not found in trusted source(s). No changes made.");
		}

	}

	private void save(Set<String> rules, File storage) {
		String newsources = getJSon(rules);
		try {
			Util.writeString(storage.toPath(), newsources);
		} catch (IOException e) {
			throw new ExitException(2, "Error when writing to " + storage, e);
		}
		trustedSources = rules.toArray(new String[0]);
	}
}
