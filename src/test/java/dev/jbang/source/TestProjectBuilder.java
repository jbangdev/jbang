package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.cli.ExitException;

public class TestProjectBuilder extends BaseTest {

	@Test
	void testDuplicateAnonRepos() {
		ProjectBuilder pb = Project.builder();
		pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = pb.build(src);
		assertThrows(ExitException.class, () -> {
			BuildContext.forProject(prj).resolveClassPath();
		});
	}

	@Test
	void testDuplicateNamedRepos() {
		ProjectBuilder pb = Project.builder();
		pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = pb.build(src);
		assertThrows(ExitException.class, () -> {
			BuildContext.forProject(prj).resolveClassPath();
		});
	}

	@Test
	void testReposSameIdDifferentUrl() {
		ProjectBuilder pb = Project.builder();
		pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://bar"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = pb.build(src);
		assertThrows(IllegalArgumentException.class, () -> {
			BuildContext.forProject(prj).resolveClassPath();
		});
	}
}
