package dev.jbang;

import java.io.File;

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
}
