package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine;

public class NativeMixin {
	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image")
	Boolean nativeImage;

	@CommandLine.Option(names = { "-N", "--native-option" }, description = "Options to pass to the native image tool")
	public List<String> nativeOptions;

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (Boolean.TRUE.equals(nativeImage)) {
			opts.add("--native");
		}
		if (nativeOptions != null) {
			for (String n : nativeOptions) {
				opts.add("-N");
				opts.add(n);
			}
		}
		return opts;
	}
}
