package dev.jbang.cli;

import static java.lang.System.out;

import java.io.File;
import java.io.IOException;

import dev.jbang.Util;
import picocli.AutoComplete;
import picocli.CommandLine;

@CommandLine.Command(name = "completion", description = "Output auto-completion script for bash/zsh.\nUsage: source <(jbang --completion)")
public class Completion extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return completion();
	}

	public int completion() throws IOException {
		String script = AutoComplete.bash(
				spec.parent().name(),
				spec.parent().commandLine());
		// not PrintWriter.println: scripts with Windows line separators fail in strange
		// ways!

		File file = File.createTempFile("jbang-completion", "temp");
		Util.writeString(file.toPath(), script);

		out.print("cat " + file.getAbsolutePath());
		out.print('\n');
		out.flush();
		return 0;
	}
}
