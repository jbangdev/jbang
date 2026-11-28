package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Description;

public class LogDemoIT extends BaseIT {

	@Test
	@Description("//VARS, //PROPS, //SOURCES, //DEPS and //RUNTIME_OPTIONS together drive a size limited rolling log")
	public void varsPropsSourcesDepsAndRuntimeOptions() throws IOException {
		assertThat(shell("jbang run -Dmax.size=1KB logdemo.java 5000"))
			.succeeded()
			.outContains("JVMARG:-Xmx64m")
			.outContains("JVMARG:-Dtinylog.writingthread=false")
			.outContains("JVMARG:-Dtinylog.writer=rolling file")
			.outContains("JVMARG:-Dtinylog.writer.backups=3")
			.outContains("JVMARG:-Dtinylog.writer.policies=size:1KB")
			.outContains("JVMARG:-Dlog.component=requests")
			.outContains("DONE");

		List<Path> logs;
		try (Stream<Path> files = Files.list(baseDir())) {
			logs = files.filter(p -> p.getFileName().toString().matches("logdemo_\\d+\\.log"))
				.collect(Collectors.toList());
		}
		// rotated at least once, and no more files than the backup limit allows
		Assertions.assertThat(logs.size()).isBetween(2, 4);
	}
}
