package dev.jbang.source.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileUpdateStrategy {
	boolean canHandle(Path file);

	void updateFile(Path file, List<String> dependencies) throws IOException;

}
