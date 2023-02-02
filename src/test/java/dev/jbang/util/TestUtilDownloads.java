package dev.jbang.util;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

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
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));
	}

	@Test
	void test2ReqSimple(WireMockRuntimeInfo wmri) throws IOException {
		UUID id = UUID.randomUUID();
		stubFor(any(urlEqualTo("/test.txt"))
											.withId(id)
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		editStub(any(urlEqualTo("/test.txt"))
												.withId(id)
												.willReturn(aResponse()
																		.withHeader("Content-Type", "text/plain")
																		.withBody("test2")));

		Path file2 = Util.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), equalTo(lmt));
		assertThat(Util.readString(file2), is("test"));
	}

	@Test
	void test2ReqWithLastModifiedSame(WireMockRuntimeInfo wmri) throws IOException {
		UUID id = UUID.randomUUID();
		stubFor(any(urlEqualTo("/test.txt"))
											.withId(id)
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withHeader("Last-Modified",
																			"Thu, 02 Feb 2023 22:22:49 GMT")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		editStub(any(urlEqualTo("/test.txt"))
												.withId(id)
												.willReturn(aResponse()
																		.withHeader("Content-Type", "text/plain")
																		.withHeader("Last-Modified",
																				"Thu, 02 Feb 2023 22:22:49 GMT")
																		.withBody("test2")));

		Path file2 = Util.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), equalTo(lmt));
		assertThat(Util.readString(file2), is("test"));
	}

	@Test
	void test2ReqWithLastModifiedUpdated(WireMockRuntimeInfo wmri) throws IOException {
		UUID id = UUID.randomUUID();
		stubFor(any(urlEqualTo("/test.txt"))
											.withId(id)
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withHeader("Last-Modified",
																			"Thu, 02 Feb 2023 22:22:49 GMT")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		editStub(any(urlEqualTo("/test.txt"))
												.withId(id)
												.willReturn(aResponse()
																		.withHeader("Content-Type", "text/plain")
																		.withHeader("Last-Modified",
																				"Sun, 02 Feb 3023 22:22:49 GMT")
																		.withBody("test2")));

		Path file2 = Util.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
		assertThat(Util.readString(file2), is("test2"));
	}

	@Test
	void test2ReqWithLastModifiedUpdatedNeverEvict(WireMockRuntimeInfo wmri) throws IOException {
		Configuration.instance().put("cache-evict", "never");

		UUID id = UUID.randomUUID();
		stubFor(any(urlEqualTo("/test.txt"))
											.withId(id)
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withHeader("Last-Modified",
																			"Thu, 02 Feb 2023 22:22:49 GMT")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		editStub(any(urlEqualTo("/test.txt"))
												.withId(id)
												.willReturn(aResponse()
																		.withHeader("Content-Type", "text/plain")
																		.withHeader("Last-Modified",
																				"Sun, 02 Feb 3023 22:22:49 GMT")
																		.withBody("test2")));

		Path file2 = Util.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), equalTo(lmt));
		assertThat(Util.readString(file2), is("test"));
	}

	@Test
	void test2ReqWithLastModifiedUpdatedEvict1(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		Configuration.instance().put("cache-evict", "1");

		UUID id = UUID.randomUUID();
		stubFor(any(urlEqualTo("/test.txt"))
											.withId(id)
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withHeader("Last-Modified",
																			"Thu, 02 Feb 2023 22:22:49 GMT")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(any(urlEqualTo("/test.txt"))
												.withId(id)
												.willReturn(aResponse()
																		.withHeader("Content-Type", "text/plain")
																		.withHeader("Last-Modified",
																				"Sun, 02 Feb 3023 22:22:49 GMT")
																		.withBody("test2")));

		Path file2 = Util.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
		assertThat(Util.readString(file2), is("test2"));
	}

	@Test
	void test2ReqWithLastModifiedUpdatedEvictPT1S(WireMockRuntimeInfo wmri) throws IOException, InterruptedException {
		Configuration.instance().put("cache-evict", "pt1s");

		UUID id = UUID.randomUUID();
		stubFor(any(urlEqualTo("/test.txt"))
											.withId(id)
											.willReturn(aResponse()
																	.withHeader("Content-Type", "text/plain")
																	.withHeader("Last-Modified",
																			"Thu, 02 Feb 2023 22:22:49 GMT")
																	.withBody("test")));

		String url = wmri.getHttpBaseUrl() + "/test.txt";
		Path file = Util.downloadAndCacheFile(url);
		FileTime lmt = Files.getLastModifiedTime(file);
		assertThat(file.toFile(), anExistingFile());
		assertThat(Util.readString(file), is("test"));

		Thread.sleep(1100);

		editStub(any(urlEqualTo("/test.txt"))
												.withId(id)
												.willReturn(aResponse()
																		.withHeader("Content-Type", "text/plain")
																		.withHeader("Last-Modified",
																				"Sun, 02 Feb 3023 22:22:49 GMT")
																		.withBody("test2")));

		Path file2 = Util.downloadAndCacheFile(url);
		assertThat(file2.toFile(), anExistingFile());
		assertThat(file2, equalTo(file));
		assertThat(Files.getLastModifiedTime(file2), not(equalTo(lmt)));
		assertThat(Util.readString(file2), is("test2"));
	}

}
