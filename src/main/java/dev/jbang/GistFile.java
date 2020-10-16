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
}
