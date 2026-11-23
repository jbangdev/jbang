package dev.jbang.util;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

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
	void test2ReqSimple(WireMockRuntimeInfo wmri) throws IOException {
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

		backdateCachedFile(file);
		FileTime backdatedLmt = Files.getLastModifiedTime(file);
		ZonedDateTime zlmt = ZonedDateTime.ofInstant(backdatedLmt.toInstant(), ZoneId.of("GMT"));
		String cachedLastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(zlmt);

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
	void test2ReqSimpleFresh(WireMockRuntimeInfo wmri) throws IOException {
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

		backdateCachedFile(file);

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
	void test2ReqWithLastModifiedUpdated(WireMockRuntimeInfo wmri) throws IOException {
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

		backdateCachedFile(file);

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
	void test2ReqWithLastModifiedUpdatedEvict1(WireMockRuntimeInfo wmri) throws IOException {
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

		backdateCachedFile(file);

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
	void test2ReqWithLastModifiedUpdatedEvictPT1S(WireMockRuntimeInfo wmri) throws IOException {
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

		backdateCachedFile(file);

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
	void test2ReqWithETagUpdated(WireMockRuntimeInfo wmri) throws IOException {
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

		backdateCachedFile(file);

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

	@Test
	void testTruncatedDownloadIsNotCached(WireMockRuntimeInfo wmri) throws IOException {
		// Server announces more bytes than it sends, like a dropped connection
		UUID id = UUID.randomUUID();
		stubFor(get(urlEqualTo("/jdk.tar.gz"))
			.withId(id)
			.willReturn(aResponse()
				.withHeader("ETag", "tag1")
				.withHeader("Content-Length", "1000")
				.withBody("partial")));

		String url = wmri.getHttpBaseUrl() + "/jdk.tar.gz";
		IOException ex = assertThrows(IOException.class, () -> NetUtil.downloadAndCacheFile(url));
		assertThat(ex.getMessage(), containsString("received 7 of 1000 bytes"));

		// Neither the partial file nor its ETag may end up in the cache, otherwise
		// it would be reused (and kept by 304 responses)
		Path saveDir = NetUtil.getUrlCacheDir(url);
		assertThat(Files.exists(saveDir), is(false));
		assertThat(Files.exists(NetUtil.getCacheMetaDir(saveDir)), is(false));

		// The next attempt downloads unconditionally and succeeds
		editStub(get(urlEqualTo("/jdk.tar.gz"))
			.withId(id)
			.andMatching(withoutHeader("If-None-Match"))
			.willReturn(aResponse()
				.withHeader("ETag", "tag1")
				.withBody("complete")));
		Path file = NetUtil.downloadAndCacheFile(url);
		assertThat(Util.readString(file), is("complete"));
	}

	// One valid chunk, then the connection drops in the middle of the next one.
	// WireMock (Jetty) controls the chunk framing so it can't produce this; the
	// failure must happen after some bytes were written to be meaningful.
	private static final String TRUNCATED_CHUNKED_RESPONSE = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n"
			+ "ETag: \"tag1\"\r\nConnection: close\r\n\r\n" + "5\r\nhello\r\n" + "10\r\npart";

	@Test
	@Timeout(30)
	void testTruncatedChunkedDownloadIsNotCached() throws Exception {
		withRawResponse(TRUNCATED_CHUNKED_RESPONSE, url -> {
			assertThrows(IOException.class, () -> NetUtil.downloadAndCacheFile(url));
			assertNotCached(url);
		});
	}

	@Test
	@Timeout(30)
	void testTruncatedChunkedUncachedDownloadLeavesNoFile(@TempDir Path dir) throws Exception {
		withRawResponse(TRUNCATED_CHUNKED_RESPONSE, url -> {
			assertThrows(IOException.class, () -> NetUtil.downloadFile(url, dir));
			assertThat(Files.exists(dir.resolve("download.tar.gz")), is(false));
		});
	}

	@Test
	@Timeout(30)
	void testContentLengthCheckedWithNonChunkedTransferEncoding() throws Exception {
		// The JDK only decodes "chunked"; for anything else the body is read by
		// Content-Length, so a short body must still be rejected
		withRawResponse("HTTP/1.1 200 OK\r\nTransfer-Encoding: gzip\r\nContent-Length: 1000\r\n"
				+ "ETag: \"tag1\"\r\nConnection: close\r\n\r\n" + "partial", url -> {
					assertThrows(IOException.class, () -> NetUtil.downloadAndCacheFile(url));
					assertNotCached(url);
				});
	}

	private static void assertNotCached(String url) {
		Path saveDir = NetUtil.getUrlCacheDir(url);
		assertThat(Files.exists(saveDir), is(false));
		assertThat(Files.exists(NetUtil.getCacheMetaDir(saveDir)), is(false));
	}

	private interface UrlClient {
		void run(String url) throws Exception;
	}

	/**
	 * Serves a raw, hand-written HTTP response (for framing WireMock can't produce)
	 * to the given client. Afterwards it verifies the response was actually
	 * delivered, so a broken fixture can't make an expected client failure pass.
	 */
	private static void withRawResponse(String response, UrlClient client) throws Exception {
		try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
			CompletableFuture<Void> delivered = new CompletableFuture<>();
			// Serve every connection (HttpURLConnection may retry a GET), so a failing
			// fixture makes the client fail fast instead of hanging on a retry
			Thread t = new Thread(() -> {
				while (!server.isClosed()) {
					try (Socket s = server.accept()) {
						BufferedReader in = new BufferedReader(
								new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
						String line;
						while ((line = in.readLine()) != null && !line.isEmpty()) {
							// skip request headers
						}
						s.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
						s.getOutputStream().flush();
						delivered.complete(null);
					} catch (Throwable e) {
						if (!server.isClosed()) {
							delivered.completeExceptionally(e);
						}
					}
				}
			});
			t.setDaemon(true);
			t.start();

			client.run("http://localhost:" + server.getLocalPort() + "/download.tar.gz");
			delivered.get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void testTruncatedUncachedDownloadLeavesNoFile(WireMockRuntimeInfo wmri, @TempDir Path dir) {
		stubFor(get(urlEqualTo("/file.zip"))
			.willReturn(aResponse()
				.withHeader("Content-Length", "1000")
				.withBody("partial")));

		String url = wmri.getHttpBaseUrl() + "/file.zip";
		assertThrows(IOException.class, () -> NetUtil.downloadFile(url, dir));
		assertThat(Files.exists(dir.resolve("file.zip")), is(false));
	}

	/**
	 * Backdates a file's last-modified time by 2 seconds so cache-evict checks see
	 * it as stale, without needing Thread.sleep(1100).
	 */
	private static void backdateCachedFile(Path file) throws IOException {
		Instant past = Instant.now().minusSeconds(2);
		Files.setLastModifiedTime(file, FileTime.from(past));
	}

	private static ValueMatcher<Request> withoutHeader(String hdr) {
		return req -> MatchResult.of(!req.containsHeader(hdr));
	}

}
