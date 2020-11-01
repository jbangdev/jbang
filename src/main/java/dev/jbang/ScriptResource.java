package dev.jbang;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Scanner;

public class ScriptResource {
	// original requested resource
	private final String originalResource;
	// cache folder it is stored inside
	private File sourceCacheDir;
	// physical file it is mapped to
	private File file;
	// This handles when main script file is in a package.
	// eg: /home/user/scripts/org/acme/Main.java, then
	// originalSourceFileBaseDir = /home/user/scripts
	private final File originalSourceFileBaseDir;

	public ScriptResource(String scriptURL, File urlCache, File file) {
		this.originalResource = scriptURL;
		this.sourceCacheDir = urlCache;
		this.file = file;
		if (Util.isJavaFile(scriptURL))
			this.originalSourceFileBaseDir = setOriginalSourceFileBaseDir(scriptURL);
		else
			this.originalSourceFileBaseDir = null;
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
		return fetchIfNeeded(resource, originalResource);
	}

	public Path fetchIfNeeded(String resource, String originalResource) {
		if (Util.isURL(resource) || Util.isURL(originalResource)) {
			try {
				URI thingToFetch = null;
				if (Util.isURL(resource)) {
					thingToFetch = new URI(resource);
				} else {
					URI includeContext = new URI(originalResource);
					thingToFetch = includeContext.resolve(resource);
				}
				return Util.downloadAndCacheFile(thingToFetch.toString(), true);
			} catch (URISyntaxException | IOException e) {
				throw new IllegalStateException("Could not download " + resource + " relatively to " + originalResource,
						e);
			}
		} else {
			return originalSourceFileBaseDir
											.getAbsoluteFile()
											.toPath()
											.resolve(resource);
		}
	}

	private File setOriginalSourceFileBaseDir(String scriptURL) {
		try (Scanner sc = new Scanner(new File(scriptURL))) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (!line.trim().startsWith("package"))
					continue;
				String[] pkgLine = line.split("package");
				if (pkgLine.length == 1)
					continue;
				String packageName = pkgLine[1];
				packageName = packageName.split(";")[0].trim(); // remove ';'
				String pkg = packageName.replace(".", File.separator); // a.b.c -> linux a/b/c, windows a\b\c
				String pkgFile = pkg + File.separator + Util.getFileName(scriptURL);
				return new File(scriptURL.substring(0, scriptURL.lastIndexOf(pkgFile)));
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		// if reached here, the script has default package
		return new File(Util.getFilePath(scriptURL));
	}

	public File getOriginalSourceFileBaseDir() {
		return originalSourceFileBaseDir;
	}
}
