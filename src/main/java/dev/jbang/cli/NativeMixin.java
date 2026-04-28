package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

public class NativeMixin {

	@Option(shortName = 'n', name = "native", hasValue = false, description = "Build using native-image")
	public Boolean nativeImage;

	@OptionList(shortName = 'N', name = "native-option", description = "Options to pass to the native image tool")
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
