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

import com.google.gson.Gson;

public class TrustedDomains {

	final static Pattern rLocalhost = Pattern.compile("(?i)^localhost(:\\d+)?$");
	final static Pattern r127 = Pattern.compile("(?i)^127.0.0.1(:\\d+)?$");

	String trustedDomains[];

	public TrustedDomains(String[] trustedDomains) {
		this.trustedDomains = trustedDomains;
	}

	public static TrustedDomains load(Path toPath) throws IOException {
		String rawtd = Util.readString(toPath);
		Gson parser = new Gson();
		String[] strings = parser.fromJson(rawtd, String[].class);
		if (strings == null) {
			throw new IllegalStateException("Could not parse " + toPath);
		} else {
			return new TrustedDomains(strings);
		}
	}

	private boolean isLocalhostAuthority(URI uri) {
		return uri.getScheme().equals("file") || rLocalhost.matcher(uri.getAuthority()).matches()
				|| r127.matcher(uri.getAuthority()).matches();
	}

	public String[] getTrustedDomains() {
		return trustedDomains;
	}

	/**
	 * Check whether a domain like https://www.microsoft.com matches the list of
	 * trusted domains.
	 *
	 * - Schemes must match - There's no subdomain matching. For example
	 * https://jbang.dev doesn't match https://www.jbang.dev - Star matches all
	 * subdomains. For example https://*.jbang.dev matches https://www.jbang.dev and
	 * https://foo.bar.jbangdev.com
	 */
	public boolean isURLDomainTrusted(URI url) throws URISyntaxException {
		if (isLocalhostAuthority(url)) {
			return true;
		}

		final String domain = String.format("%s://%s", url.getScheme(), url.getAuthority());

		for (String trustedDomain : trustedDomains) {
			if ("*".equals(trustedDomain)) {
				return true;
			}

			if (domain.equals(trustedDomain)) {
				return true;
			}

			URI parsedTrustedDomain;
			if (trustedDomain.startsWith("https://")) {
				parsedTrustedDomain = new URI(trustedDomain);
				if (!url.getScheme().equals(parsedTrustedDomain.getScheme())) {
					continue;
				}
			} else {
				parsedTrustedDomain = new URI("https://" + trustedDomain);
			}

			if (url.getAuthority().equals(parsedTrustedDomain.getAuthority())) {
				if (pathMatches(url.getPath(), parsedTrustedDomain.getPath())) {
					return true;
				} else {
					continue;
				}
			}

			if (trustedDomain.contains("*")) {

				String[] reversedAuthoritySegments = reverse(url.getAuthority().split("\\."));

				String[] reversedTrustedDomainAuthoritySegments = reverse(
						parsedTrustedDomain.getAuthority().split("\\."));

				boolean ruleIsSmaller = reversedTrustedDomainAuthoritySegments.length < reversedAuthoritySegments.length;
				boolean ruleHasStarAtEnd = reversedTrustedDomainAuthoritySegments[reversedTrustedDomainAuthoritySegments.length
						- 1].equals("*");
				if (ruleIsSmaller && ruleHasStarAtEnd) {
					reversedAuthoritySegments = Arrays.copyOfRange(reversedAuthoritySegments, 0,
							reversedTrustedDomainAuthoritySegments.length);
				}

				boolean authorityMatches = true;
				for (int i = 0; i < reversedAuthoritySegments.length; i++) {
					String val = reversedAuthoritySegments[i];
					String elementRule = reversedTrustedDomainAuthoritySegments[i];
					if (elementRule.equals("*")
							|| val.equals(elementRule)) {
						// they match
					} else {
						authorityMatches = false;
						break;
					}
				}

				if (authorityMatches && pathMatches(url.getPath(), parsedTrustedDomain.getPath())) {
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
		String trusteddomains = Settings.getTemplateEngine()
										.getTemplate("trusted-domains.json.qute")
										.data("trusteddomains", rules)
										.render();
		return trusteddomains;
	}

	public void add(List<String> trust, File storage) {

		Util.info("Adding " + trust + " to " + storage);

		Set<String> newrules = new LinkedHashSet<String>();

		newrules.addAll(Arrays.asList(trustedDomains));

		if (newrules.addAll(trust)) {
			String newdomains = getJSon(newrules);
			try {
				Util.writeString(storage.toPath(), newdomains);
			} catch (IOException e) {
				throw new ExitException(2, "Error when writing to " + storage, e);
			}
			trustedDomains = newrules.toArray(new String[0]);
		} else {
			Util.warnMsg("Already trusted domains. No changes made.");
		}

	}
}
