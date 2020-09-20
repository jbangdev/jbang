package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class ScriptResource {
	// original requested resource
	private final String originalResource;
	// cache folder it is stored inside
	private File sourceCacheDir;
	// physical file it is mapped to
	private File file;

	public ScriptResource(String scriptURL, File urlCache, File file) {
		this.originalResource = scriptURL;
		this.sourceCacheDir = urlCache;
		this.file = file;
	}

	public static ScriptResource forFile(File file) {
		return new ScriptResource(null, null, file);
	}

	public File getFile() {
		return file;
	}

	public File getSourceCacheDir() {
		return sourceCacheDir;
	}

	public String getOriginalResource() {
		return originalResource;
	}

	public Path fetchIfNeeded(String resource) {
		if (sourceCacheDir != null) {
			try {
				URI includeContext = new URI(originalResource);
				URI thingToFetch = includeContext.resolve(resource);
				return Util.downloadAndCacheFile(thingToFetch.toString(), true);
			} catch (URISyntaxException | IOException e) {
				throw new IllegalStateException("Could not download " + resource + " relatively to " + originalResource,
						e);
			}
		} else {
			return file
						.getAbsoluteFile()
						.toPath()
						.getParent()
						.resolve(resource);
		}
	}
}
