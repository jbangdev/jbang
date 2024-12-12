package dev.jbang.jvm.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;

public class FileHttpCacheStorage implements HttpCacheStorage {

    private final Path cacheDir;
    private final DefaultHttpCacheEntrySerializer serializer;

    public FileHttpCacheStorage(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.serializer = new DefaultHttpCacheEntrySerializer();
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create cache directory", e);
        }
    }

    @Override
    public synchronized void putEntry(String key, HttpCacheEntry entry) throws IOException {
        Path filePath = cacheDir.resolve(encodeKey(key));
        try (OutputStream os = Files.newOutputStream(filePath);
                BufferedOutputStream bos = new BufferedOutputStream(os)) {
            serializer.writeTo(entry, bos);
        }
    }

    @Override
    public synchronized HttpCacheEntry getEntry(String key) throws IOException {
        Path filePath = cacheDir.resolve(encodeKey(key));
        if (Files.exists(filePath)) {
            try (InputStream is = Files.newInputStream(filePath);
                    BufferedInputStream bis = new BufferedInputStream(is)) {
                return serializer.readFrom(bis);
            }
        }
        return null;
    }

    @Override
    public synchronized void removeEntry(String key) throws IOException {
        Path filePath = cacheDir.resolve(encodeKey(key));
        Files.deleteIfExists(filePath);
    }

    @Override
    public synchronized void updateEntry(String key, HttpCacheUpdateCallback callback)
            throws IOException, HttpCacheUpdateException {
        Path filePath = cacheDir.resolve(encodeKey(key));
        HttpCacheEntry existingEntry = null;
        if (Files.exists(filePath)) {
            try (InputStream is = Files.newInputStream(filePath);
                    BufferedInputStream bis = new BufferedInputStream(is)) {
                existingEntry = serializer.readFrom(bis);
            }
        }
        HttpCacheEntry updatedEntry = callback.update(existingEntry);
        putEntry(key, updatedEntry);
    }

    private String encodeKey(String key) {
        // You can use more sophisticated encoding if necessary
        return key.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
