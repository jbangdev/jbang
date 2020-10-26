package dev.jbang;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GistCollection {
	private String mainURL;
	public Map<String, Path> fileNameToPathMap = new HashMap<>();
	public List<GistFile> gistFiles = new ArrayList<>();
	public List<String> mainClasses = new ArrayList<>();

	public String getMainURL() {
		return mainURL;
	}

	public void setMainURL(String mainURL) {
		this.mainURL = mainURL;
	}

	public void add(GistFile gistFile) {
		gistFiles.add(gistFile);
		fileNameToPathMap.put(gistFile.filename, gistFile.path);
		if (Util.hasMainMethod(gistFile.content))
			mainClasses.add(gistFile.filename);
	}
}
