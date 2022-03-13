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

	public IntegrationResult merged(IntegrationResult ir) {
		if (ir.nativeImagePath == null && ir.mainClass == null && ir.javaArgs == null) {
			return this;
		} else if (nativeImagePath == null && mainClass == null && javaArgs == null) {
			return ir;
		} else {
			return new IntegrationResult(
					nativeImagePath != null ? nativeImagePath : ir.nativeImagePath,
					mainClass != null ? mainClass : ir.mainClass,
					javaArgs != null ? javaArgs : ir.javaArgs);
		}
	}
}
