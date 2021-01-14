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

	public FileRef toFileRef(String fileReference) {
		String[] split = fileReference.split(" // ")[0].split("=");
		String ref = null;
		String dest = null;

		if (split.length == 1) {
			ref = split[0];
		} else if (split.length == 2) {
			ref = split[0];
			dest = split[1];
		} else {
			throw new IllegalStateException("Invalid file reference: " + fileReference);
		}

		if (FileRef.isURL(fileReference)) {
			return new URLRef(this.originalResource, ref, dest);
		}
		if (FileRef.isURL(originalResource)) {
			return new URLRef(this.originalResource, ref, dest);
		} else {
			return new FileRef(this.originalResource, ref, dest);
		}
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
