package dev.jbang.source.update;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;

public class FileUpdaters {

	private static final List<FileUpdateStrategy> strategies = new ArrayList<>();

	static {
		strategies.add(new JavaFileUpdateStrategy());
		strategies.add(new BuildJbangFileUpdateStrategy());
		// Future strategies can be added here:
		// strategies.add(new PomXmlFileUpdateStrategy());
		// strategies.add(new BuildGradleFileUpdateStrategy());
		// strategies.add(new RequirementsTxtFileUpdateStrategy());
	}

	public static FileUpdateStrategy forFile(Path file) {
		return strategies.stream()
			.filter(strategy -> strategy.canHandle(file))
			.findFirst()
			.orElseThrow(() -> new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Unsupported file type: " + file.getFileName()));
	}
}
