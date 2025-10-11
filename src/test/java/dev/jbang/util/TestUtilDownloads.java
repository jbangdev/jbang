package dev.jbang.util;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.ValueMatcher;

import dev.jbang.BaseTest;
import dev.jbang.Configuration;

@WireMockTest
public class TestUtilDownloads extends BaseTest {

	@BeforeEach
	void resetConfig() {
		Configuration.instance(null);
	}

	@Test
	void test1ReqSimple(WireMockRuntimeInfo wmri) throws IOException {
		stubFor(get(urlEqualTo("/test.txt"))
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));
	}

	@Test
	void test2ReqSimple(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		ZonedDateTime zlmt = ZonedDateTime.ofInstant(lmt.toInstant(), ZoneId.of("GMT"));
		String cachedLastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(zlmt);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.withHeader("If-Modified-Since", new EqualToPattern(
					cachedLastModified))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBody("test2")));

		Util.withCacheEvict("0", () -> {
			Path file2 = NetUtil.downloadAndCacheFile(url);
			assertThat(file2.toFile(), anExistingFile());
			assertThat(file2, equalTo(file));
			assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
			assertThat(Util.readString(file2), is("test2"));
			return 0;
		});
	}

	@Test
	void test2ReqSimpleFresh(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBody("test2")));

		Util.freshly(() -> {
			Path file2 = NetUtil.downloadAndCacheFile(url);
			assertThat(file2.toFile(), anExistingFile());
			assertThat(file2, equalTo(file));
			assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
			assertThat(Util.readString(file2), is("test2"));
			return null;
		});
	}

	@Test
	void test2ReqWithLastModifiedSame(WireMockRuntimeInfo wmri) throws IOException {
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Thu, 02 Feb 2023 22:22:49 GMT")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		ZonedDateTime zlmt = ZonedDateTime.ofInstant(lmt.toInstant(), ZoneId.of("GMT"));
		String cachedLastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(zlmt);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.withHeader("If-Modified-Since", new EqualToPattern(
					cachedLastModified))
			.willReturn(aResponse()
				.withStatus(304)
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Thu, 02 Feb 2023 22:22:49 GMT")
				.withBody("test2")));

		Path file2 = NetUtil.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), greaterThanOrEqualTo(lmt));
		assertThat(Util.readString(file2), is("test"));
	}

	@Test
	void test2ReqWithLastModifiedUpdated(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Thu, 02 Feb 2023 22:22:49 GMT")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Sun, 02 Feb 3023 22:22:49 GMT")
				.withBody("test2")));

		Util.withCacheEvict("0", () -> {
			Path file2 = NetUtil.downloadAndCacheFile(url);
			assertThat(file2.toFile(), anExistingFile());
			assertThat(file2, equalTo(file));
			assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
			assertThat(Util.readString(file2), is("test2"));
			return 0;
		});
	}

	@Test
	void test2ReqWithLastModifiedUpdatedNeverEvict(WireMockRuntimeInfo wmri) throws IOException {
		Configuration.instance().put("cache-evict", "never");

		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Thu, 02 Feb 2023 22:22:49 GMT")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Sun, 02 Feb 3023 22:22:49 GMT")
				.withBody("test2")));

		Path file2 = NetUtil.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), equalTo(lmt));
		assertThat(Util.readString(file2), is("test"));
	}

	@Test
	void test2ReqWithLastModifiedUpdatedEvict1(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		Configuration.instance().put("cache-evict", "1");

		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Thu, 02 Feb 2023 22:22:49 GMT")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Sun, 02 Feb 3023 22:22:49 GMT")
				.withBody("test2")));

		Path file2 = NetUtil.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
		assertThat(Util.readString(file2), is("test2"));
	}

	@Test
	void test2ReqWithLastModifiedUpdatedEvictPT1S(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		Configuration.instance().put("cache-evict", "pt1s");

		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Thu, 02 Feb 2023 22:22:49 GMT")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("Last-Modified",
						"Sun, 02 Feb 3023 22:22:49 GMT")
				.withBody("test2")));

		Path file2 = NetUtil.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
		assertThat(Util.readString(file2), is("test2"));
	}

	@Test
	void test2ReqWithETagSame(WireMockRuntimeInfo wmri) throws IOException {
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("ETag", "tag1")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		ZonedDateTime zlmt = ZonedDateTime.ofInstant(lmt.toInstant(), ZoneId.of("GMT"));
		String cachedLastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(zlmt);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));
		Path etag = NetUtil.etagFile(file, NetUtil.getCacheMetaDir(file.getParent()));
		assertThat(etag.toFile(), anExistingFile());
		assertThat(Util.readString(etag), is("tag1"));

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.withHeader("If-None-Match", new EqualToPattern("tag1"))
			.withHeader("If-Modified-Since", new EqualToPattern(cachedLastModified))
			.willReturn(aResponse()
				.withStatus(304)
				.withHeader("Content-Type", "text/plain")
				.withHeader("ETag", "tag1")
				.withBody("test2")));

		Path file2 = NetUtil.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), greaterThanOrEqualTo(lmt));
		assertThat(Util.readString(file2), is("test"));
		Path etag2 = NetUtil.etagFile(file, NetUtil.getCacheMetaDir(file2.getParent()));
		assertThat(etag2.toFile(), anExistingFile());
		assertThat(Util.readString(etag2), is("tag1"));
	}

	@Test
	void test2ReqWithETagUpdated(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/test.txt"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("ETag", "tag1")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = NetUtil.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));
		Path etag = NetUtil.etagFile(file, NetUtil.getCacheMetaDir(file.getParent()));
		assertThat(etag.toFile(), anExistingFile());
		assertThat(Util.readString(etag), is("tag1"));

		Thread.sleep(1100);

		editStub(get(urlEqualTo("/test.txt"))
			.withId(id)
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withHeader("ETag", "tag2")
				.withBody("test2")));

		Util.withCacheEvict("0", () -> {
			Path file2 = NetUtil.downloadAndCacheFile(url);
			assertThat(file2.toFile(), anExistingFile());
			assertThat(file2, equalTo(file));
			assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
			assertThat(Util.readString(file2), is("test2"));
			Path etag2 = NetUtil.etagFile(file, NetUtil.getCacheMetaDir(file2.getParent()));
			assertThat(etag2.toFile(), anExistingFile());
			assertThat(Util.readString(etag2), is("tag2"));
			return 0;
		});
	}

	@Test
	void testReqUrlWithParams(WireMockRuntimeInfo wmri) throws IOException {
		stubFor(get(urlEqualTo("/test.txt?path=foo/bar"))
			.andMatching(withoutHeader("If-None-Match"))
			.andMatching(withoutHeader("If-Modified-Since"))
			.willReturn(aResponse()
				.withHeader("Content-Type",
						"text/plain")
				.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt?path=foo/bar";
		Path file = NetUtil.downloadAndCacheFile(url);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));
	}

	private static ValueMatcher<Request> withoutHeader(String hdr) {
		return req -> MatchResult.of(!req.containsHeader(hdr));
	}

}
