package dev.jbang.jvm.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;

public class NetUtils {

    public static final RequestConfig DEFAULT_REQUEST_CONFIG =
            RequestConfig.custom()
                    .setConnectionRequestTimeout(10000)
                    .setConnectTimeout(10000)
                    .setSocketTimeout(30000)
                    .build();

    public static <T> T readJsonFromUrl(String url, Class<T> klass) throws IOException {
        HttpClientBuilder builder = createDefaultHttpClientBuilder();
        return readJsonFromUrl(builder, url, klass);
    }

    public static <T> T readJsonFromUrl(HttpClientBuilder builder, String url, Class<T> klass)
            throws IOException {
        return requestUrl(builder, url, response -> handleJsonResult(klass, response));
    }

    public static Path downloadFromUrl(String url) throws IOException {
        HttpClientBuilder builder = createDefaultHttpClientBuilder();
        return downloadFromUrl(builder, url);
    }

    public static Path downloadFromUrl(HttpClientBuilder builder, String url) throws IOException {
        return requestUrl(builder, url, NetUtils::handleDownloadResult);
    }

    public static HttpClientBuilder createDefaultHttpClientBuilder() {
        CacheConfig cacheConfig = CacheConfig.custom().setMaxCacheEntries(1000).build();

        FileHttpCacheStorage cacheStorage = new FileHttpCacheStorage(Paths.get("http-cache"));

        // return HttpClientBuilder.create().setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG);
        return CachingHttpClientBuilder.create()
                .setCacheConfig(cacheConfig)
                .setHttpCacheStorage(cacheStorage)
                .setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG);
    }

    public static <T> T requestUrl(
            HttpClientBuilder builder, String url, Function<HttpResponse, T> responseHandler)
            throws IOException {
        try (CloseableHttpClient httpClient = builder.build()) {
            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int responseCode = response.getStatusLine().getStatusCode();
                if (responseCode != 200) {
                    throw new IOException(
                            "Failed to read from URL: "
                                    + url
                                    + ", response code: #"
                                    + responseCode);
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("Failed to read from URL: " + url + ", no content");
                }
                return responseHandler.apply(response);
            }
        } catch (UncheckedIOException e) {
            throw new IOException("Failed to read from URL: " + url + ", " + e.getMessage(), e);
        }
    }

    private static <T> T handleJsonResult(Class<T> klass, HttpResponse response) {
        try {
            String mimeType = ContentType.getOrDefault(response.getEntity()).getMimeType();
            if (!mimeType.equals("application/json")) {
                throw new IOException("Unexpected MIME type: " + mimeType);
            }
            HttpEntity entity = response.getEntity();
            try (InputStream is = entity.getContent()) {
                Gson parser = new GsonBuilder().create();
                return parser.fromJson(new InputStreamReader(is), klass);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path handleDownloadResult(HttpResponse response) {
        try {
            HttpEntity entity = response.getEntity();
            try (InputStream is = entity.getContent()) {
                // TODO implement
                return null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
