package dev.jbang;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GistFile {
	public static boolean isGist;
	public String filename;
	public String raw_url;
	public String content;
	public Path path;
	public List<String> sources = new ArrayList<>(); // eg sources.get(0) = two.java
	public static Map<String, Path> fileNameToPathMap = new HashMap<>();
	public static List<GistFile> gistFiles = new ArrayList<>();

	public GistFile(String filename, String raw_url, String content) {
		this.filename = filename;
		this.raw_url = raw_url;
		this.content = content;
		this.path = Util.cacheContent(raw_url, content);
		fileNameToPathMap.put(this.filename, this.path);
		findSources();
	}

	private void findSources() {
		List<String> lines = Util.getLines(null, this.content);
		for (String line : lines) {
			if (!line.startsWith(Util.SOURCES_COMMENT_PREFIX))
				continue;
			String[] tmp1 = line.split("[ ;,]+");
			for (int i = 1; i < tmp1.length; ++i) {
				sources.add(tmp1[i]);
			}
		}
	}

	public static List<Path> getResolvedSourcePathsAsList() {
		Set<Path> resolvedSourcePaths = new HashSet<>();
		List<Path> sourcePaths = new ArrayList<Path>();
		for (GistFile gistFile : gistFiles) {
			for (String source : gistFile.sources) {
				Path path = fileNameToPathMap.get(source);
				if (path == null) {
					throw new IllegalStateException("Unable to find source: " + source);
				}
				resolvedSourcePaths.add(path);
			}
		}
		sourcePaths.addAll(resolvedSourcePaths);
		return sourcePaths;
	}

	public static boolean hasMainMethod(String content) {
		// (public[ ]{1,}static[ ]{1,}void[ ]{1,}main[ ]{0,}\()|(static[ ]{1,}public[
		// ]{1,}void[ ]{1,}main[ ]{0,}\()
		// TODO create regular expression
		return content.contains("public static void main(");
	}
}
