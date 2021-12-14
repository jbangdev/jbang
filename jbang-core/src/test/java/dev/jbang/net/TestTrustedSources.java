package dev.jbang.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.Gson;

import dev.jbang.BaseTest;

public class TestTrustedSources extends BaseTest {

	public static Iterable<Object[]> simple() {
		return Arrays.asList(new Object[][] {
				{ "simple", "https://x.org", new String[0], false },
				{ "simple", "https://x.org", new String[] { "https://x.org" }, true },
				{ "simple", "https://x.org/foo", new String[] { "https://x.org" }, true },
				{ "simple", "https://x.org", new String[] { "http://x.org" }, false },
				{ "simple", "http://x.org", new String[] { "https://x.org" }, false },
				{ "simple", "https://www.x.org", new String[] { "https://x.org" }, false },
				{ "simple", "https://www.x.org", new String[] { "https://www.x.org", "https://y.org" }, true },

				{ "defaultblessed", "https://github.com/jbangdev/jbang-catalog/", new String[] {}, true },
				{ "defaultblessed", "https://github.com/jbangdev-fake/jbang-catalog/", new String[] {}, false },

				{ "localhost", "https://127.0.0.1", new String[0], true },
				{ "localhost", "https://127.0.0.1:3000", new String[0], true },
				{ "localhost", "https://localhost", new String[0], true },
				{ "localhost", "https://localhost:3000", new String[0], true },

				{ "* star", "https://a.x.org", new String[] { "https://*.x.org" }, true },
				{ "* star", "https://a.b.x.org", new String[] { "https://*.x.org" }, true },
				{ "* star", "https://a.x.org", new String[] { "https://a.x.*" }, true },
				{ "* star", "https://a.x.org", new String[] { "https://a.*.org" }, true },
				{ "* star", "https://a.x.org", new String[] { "https://*.*.org" }, true },
				{ "* star", "https://a.b.x.org", new String[] { "https://*.b.*.org" }, true },
				{ "* star", "https://a.a.b.x.org", new String[] { "https://*.b.*.org" }, true },
				{ "* star", "https://a.a.b.x.org", new String[] { "*" }, true },

				{ "no scheme", "https://a.x.org", new String[] { "a.x.org" }, true },
				{ "no scheme", "https://a.x.org", new String[] { "*.x.org" }, true },
				{ "no scheme", "https://a.b.x.org", new String[] { "*.x.org" }, true },
				{ "no scheme", "https://x.org", new String[] { "*.x.org" }, true },

				{ "sub paths", "https://x.org/foo", new String[] { "https://x.org/foo" }, true },
				{ "sub paths", "https://x.org/foo/bar", new String[] { "https://x.org/foo" }, true },

				{ "sub paths", "https://x.org/foo", new String[] { "https://x.org/foo/" }, true },
				{ "sub paths", "https://x.org/foo/bar", new String[] { "https://x.org/foo/" }, true },

				{ "sub paths", "https://x.org/foo", new String[] { "x.org/foo" }, true },
				{ "sub paths", "https://x.org/foo", new String[] { "*.org/foo" }, true },

				{ "sub paths", "https://x.org/bar", new String[] { "https://x.org/foo" }, false },
				{ "sub paths", "https://x.org/bar", new String[] { "x.org/foo" }, false },
				{ "sub paths", "https://x.org/bar", new String[] { "*.org/foo" }, false },

				{ "sub paths", "https://x.org/foo/bar", new String[] { "https://x.org/foo" }, true },
				{ "sub paths", "https://x.org/foo2", new String[] { "https://x.org/foo" }, false },

				{ "sub paths", "https://www.x.org/foo", new String[] { "https://x.org/foo" }, false },

				{ "sub paths", "https://a.x.org/bar", new String[] { "https://*.x.org/foo" }, false },
				{ "sub paths", "https://a.b.x.org/bar", new String[] { "https://*.x.org/foo" }, false },

				{ "sub paths", "https://github.com",
						new String[] { "https://github.com/foo/bar", "https://github.com" }, true },

				{ "maxspecific", "https://twitter.com/maxandersen/status/1266904846239752192",
						new String[] {
								"*.youtube.com",
								"https://quarkus.io",
								"https://github.com",
								"https://stackoverflow.com",
								"*.morling.dev"
						}, false }
		});
	}

	@ParameterizedTest(name = "{0}: {1} with rules {2} = {3}")
	@MethodSource("simple")
	public void testTrustedSources(String type, String url, String[] rules, boolean expected)
			throws URISyntaxException {
		assertEquals(expected, new TrustedSources(rules).isURLTrusted(new URI(url)));
	}

	@Test
	public void testQuotedSources() {

		Set<String> s = new HashSet<>();
		s.add("\\*");
		s.add("Hey \\There");
		String jSon = new TrustedSources(null).getJSon(s);

		Gson parser = new Gson();
		String[] strings = parser.fromJson(jSon, String[].class);

		assertEquals(strings.length, 2);

	}

}
