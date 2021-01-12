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
	private File file;

	public ScriptResource(String scriptURL, File file) {
		this.originalResource = scriptURL;
		this.file = file;
	}

	public static ScriptResource forFile(File file) {
		return new ScriptResource(null, file);
	}

	public boolean isURL() {
		return originalResource != null && Util.isURL(originalResource);
	}

	public File getFile() {
		return file;
	}

	public String getOriginalResource() {
		return originalResource;
	}

	public Path fetchIfNeeded(String resource) {
		return fetchIfNeeded(resource, originalResource, false);
	}

	public Path fetchIfNeeded(String resource, String originalResource, boolean fresh) {
		if (Util.isURL(resource) || Util.isURL(originalResource)) {
			try {
				URI thingToFetch = null;
				if (Util.isURL(resource)) {
					thingToFetch = new URI(resource);
				} else {
					URI includeContext = new URI(originalResource);
					thingToFetch = includeContext.resolve(resource);
				}
				return Util.downloadAndCacheFile(thingToFetch.toString(), fresh);
			} catch (URISyntaxException | IOException e) {
				throw new IllegalStateException("Could not download " + resource + " relatively to " + originalResource,
						e);
			}
		} else {
			return new File(originalResource)
												.getAbsoluteFile()
												.toPath()
												.getParent()
												.resolve(resource);
		}
	}
}
