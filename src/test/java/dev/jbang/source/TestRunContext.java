package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.cli.ExitException;

public class TestRunContext extends BaseTest {

	@Test
	void testDuplicateAnonRepos() {
		RunContext ctx = new RunContext();
		ctx.setAdditionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = ctx.forFile(src);
		assertThrows(ExitException.class, () -> {
			prj.resolveClassPath();
		});
	}

	@Test
	void testDuplicateNamedRepos() {
		RunContext ctx = new RunContext();
		ctx.setAdditionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = ctx.forFile(src);
		assertThrows(ExitException.class, () -> {
			prj.resolveClassPath();
		});
	}

	@Test
	void testReposSameIdDifferentUrl() {
		RunContext ctx = new RunContext();
		ctx.setAdditionalRepositories(Arrays.asList("foo=http://foo", "foo=http://bar"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = ctx.forFile(src);
		assertThrows(IllegalArgumentException.class, () -> {
			prj.resolveClassPath();
		});
	}
}
