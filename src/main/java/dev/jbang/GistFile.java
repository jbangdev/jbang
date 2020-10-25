package dev.jbang;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GistFile {
	public String filename;
	public String raw_url;
	public String content;
	public Path path;
	public List<String> sources = new ArrayList<>(); // eg sources.get(0) = two.java

	public GistFile(String filename, String raw_url, String content) {
		this.filename = filename;
		this.raw_url = raw_url;
		this.content = content;
		this.path = Util.cacheContent(raw_url, content);
		findSources();
	}

	private void findSources() {
		sources = Util.collectSources(this.content);
	}
}
