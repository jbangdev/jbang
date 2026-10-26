package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.source.MainClassScanner.MainScan;

public class TestMainClassScanner {

	/**
	 * A ClassBytesSource over a directory of compiled .class files (skips inner
	 * classes).
	 */
	private static MainClassScanner.ClassBytesSource dirSource(Path dir) {
		return consumer -> {
			try (Stream<Path> paths = Files.walk(dir)) {
				List<Path> classes = paths.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".class"))
					.filter(p -> !p.getFileName().toString().contains("$"))
					.collect(Collectors.toList());
				for (Path c : classes) {
					try (InputStream is = Files.newInputStream(c)) {
						consumer.accept(c.toString(), is);
					}
				}
			}
		};
	}

	@Test
	void findsMainAgentAndPremainCandidates(@TempDir Path dir) throws Exception {
		Path src = dir.resolve("src");
		Files.createDirectories(src.resolve("a"));
		Files.write(src.resolve("a/Main1.java"),
				"package a; public class Main1 { public static void main(String... x){} }".getBytes());
		Files.write(src.resolve("a/Main2.java"),
				"package a; public class Main2 { public static void main(String... x){} }".getBytes());
		Files.write(src.resolve("a/Agent.java"),
				("package a; import java.lang.instrument.Instrumentation;"
						+ " public class Agent {"
						+ " public static void agentmain(String s, Instrumentation i){}"
						+ " public static void premain(String s, Instrumentation i){} }")
					.getBytes());
		Path classes = dir.resolve("classes");
		javac(classes, src.resolve("a/Main1.java"), src.resolve("a/Main2.java"), src.resolve("a/Agent.java"));

		MainScan scan = MainClassScanner.scan(dirSource(classes));

		assertThat(scan.getMainClasses(), containsInAnyOrder("a.Main1", "a.Main2"));
		assertThat(scan.getAgentMain().orElse(null), equalTo("a.Agent"));
		assertThat(scan.getPreMain().orElse(null), equalTo("a.Agent"));
	}

	@Test
	void skipsUnparseableClassesWithoutFailing(@TempDir Path dir) throws Exception {
		Path src = dir.resolve("src");
		Files.createDirectories(src.resolve("a"));
		Files.write(src.resolve("a/Good.java"),
				"package a; public class Good { public static void main(String... x){} }".getBytes());
		Path classes = dir.resolve("classes");
		javac(classes, src.resolve("a/Good.java"));
		// a bogus .class file must not abort the scan
		Files.write(classes.resolve("a/Bogus.class"), "not a class file".getBytes());

		MainScan scan = MainClassScanner.scan(dirSource(classes));

		assertThat(scan.getMainClasses(), containsInAnyOrder("a.Good"));
	}

	private static void javac(Path outDir, Path... sources) throws Exception {
		Files.createDirectories(outDir);
		String javac = System.getProperty("java.home") + "/bin/javac";
		List<String> cmd = new ArrayList<>();
		cmd.add(javac);
		cmd.add("-d");
		cmd.add(outDir.toString());
		for (Path s : sources) {
			cmd.add(s.toString());
		}
		Process p = new ProcessBuilder(cmd).inheritIO().start();
		if (p.waitFor() != 0) {
			throw new IllegalStateException("javac failed");
		}
	}
}
