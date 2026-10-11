package dev.jbang.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.tomakehurst.wiremock.WireMockServer;

import dev.jbang.BaseTest;
import dev.jbang.Configuration;
import dev.jbang.ExitException;
import dev.jbang.resources.ResourceNotFoundException;
import dev.jbang.util.NetUtil;
import dev.jbang.util.Util;

public class TestCatalogCache extends BaseTest {

	private String catalogUrl;

	@BeforeEach
	void initCatalog() {
		Catalog.clearCache();
		Configuration.instance().put("cache-evict", "1");
		catalogUrl = globalwms.baseUrl() + "/jbang-catalog.json";
		globalwms.stubFor(get(urlEqualTo("/jbang-catalog.json"))
			.willReturn(aResponse().withBody("{\"aliases\":{\"hello\":{\"script-ref\":\"hello.java\"}}}")));
	}

	@Test
	void testExpiredCatalogWhenConnectionIsRefused() throws Exception {
		Path cached = cacheCatalog();
		FileTime oldTime = Files.getLastModifiedTime(cached);
		globalwms.stop();

		CaptureResult<Catalog> result = captureOutput(() -> Catalog.getByRef(catalogUrl));

		assertTrue(result.result.aliases.containsKey("hello"));
		assertEquals(catalogUrl.substring(0, catalogUrl.lastIndexOf('/')), result.result.baseRef);
		assertEquals(cached, result.result.catalogRef.getFile());
		assertTrue(result.result.catalogRef.exists());
		assertEquals(catalogUrl, result.result.catalogRef.getOriginalResource());
		assertEquals(catalogUrl.substring(0, catalogUrl.lastIndexOf('/')) + "/next.json",
				result.result.catalogRef.resolve("next.json").getOriginalResource());
		assertEquals(oldTime, Files.getLastModifiedTime(cached));
		assertEquals(catalogUrl.substring(0, catalogUrl.lastIndexOf('/')) + "/hello.java",
				Alias.get(result.result, "hello").resolve());
		assertTrue(result.err.contains("using cached catalog"));
	}

	@Test
	void testCachedCatalogPreservesRelativeScriptBase() throws IOException {
		globalwms.resetAll();
		globalwms.stubFor(get(urlEqualTo("/jbang-catalog.json"))
			.willReturn(aResponse().withBody(
					"{\"base-ref\":\"scripts\",\"aliases\":{\"hello\":{\"script-ref\":\"hello.java\"}}}")));
		cacheCatalog();
		globalwms.stop();

		Catalog catalog = Catalog.getByRef(catalogUrl);

		assertEquals(catalogUrl.substring(0, catalogUrl.lastIndexOf('/')) + "/scripts/hello.java",
				Alias.get(catalog, "hello").resolve());
	}

	@Test
	void testCatalogRefreshResumesAfterConnectionFailure() throws Exception {
		cacheCatalog();
		int port = globalwms.port();
		globalwms.stop();
		Catalog.getByRef(catalogUrl);
		Catalog.clearCache();
		globalwms = new WireMockServer(options().port(port));
		globalwms.start();
		globalwms.stubFor(get(urlEqualTo("/jbang-catalog.json"))
			.willReturn(aResponse().withBody("{\"aliases\":{\"updated\":{\"script-ref\":\"updated.java\"}}}")));

		assertTrue(Catalog.getByRef(catalogUrl).aliases.containsKey("updated"));
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 403, 404, 500 })
	void testHttpErrorsAreNotHidden(int status) throws IOException {
		Path cached = cacheCatalog();
		globalwms.resetAll();
		globalwms.stubFor(get(urlEqualTo("/jbang-catalog.json"))
			.willReturn(aResponse().withStatus(status)));

		assertThrows(ResourceNotFoundException.class, () -> Catalog.getByRef(catalogUrl));
		assertTrue(Files.readString(cached).contains("hello"));
	}

	@Test
	void testFreshRequiresSuccessfulRefresh() throws IOException {
		cacheCatalog();
		globalwms.stop();
		Util.setFresh(true);

		assertThrows(ResourceNotFoundException.class, () -> Catalog.getByRef(catalogUrl));
	}

	@Test
	void testNoCachedCatalogStillFails() {
		globalwms.stop();

		assertThrows(ResourceNotFoundException.class, () -> Catalog.getByRef(catalogUrl));
	}

	@Test
	void testInvalidCachedCatalogStillFails() throws IOException {
		Path cached = cacheCatalog();
		Files.writeString(cached, "{\"aliases\":{\"broken\":{}}}");
		Files.setLastModifiedTime(cached, FileTime.from(Instant.now().minusSeconds(60)));
		globalwms.stop();

		assertThrows(ExitException.class, () -> Catalog.getByRef(catalogUrl));
	}

	@Test
	void testOtherDownloadsStillFailOnConnectionFailure() throws IOException {
		cacheCatalog();
		globalwms.stop();

		assertThrows(IOException.class, () -> NetUtil.downloadAndCacheFile(catalogUrl));
	}

	private Path cacheCatalog() throws IOException {
		Path cached = Catalog.getByRef(catalogUrl).catalogRef.getFile();
		Files.setLastModifiedTime(cached, FileTime.from(Instant.now().minusSeconds(60)));
		Catalog.clearCache();
		return cached;
	}
}
