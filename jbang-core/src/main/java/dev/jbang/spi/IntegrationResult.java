package dev.jbang.spi;

import java.nio.file.Path;
import java.util.List;

public class IntegrationResult {

	public final Path nativeImagePath;
	public final String mainClass;
	public final List<String> javaArgs;

	public IntegrationResult(Path nativeImagePath, String mainClass, List<String> javaArgs) {
		this.nativeImagePath = nativeImagePath;
		this.mainClass = mainClass;
		this.javaArgs = javaArgs;
	}
}
