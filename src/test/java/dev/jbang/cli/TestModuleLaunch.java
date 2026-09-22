package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;

/**
 * Executable spec for how `jbang` chooses classpath vs module execution and how
 * the {@code --module[=<module>[/<mainclass-or-glob>]]} grammar maps to java's
 * own {@code -m <module>[/<mainclass>]} launcher syntax.
 *
 * The jar under test is a manifest-less modular jar (like real modular CLIs,
 * e.g. jfmt): module {@code test.mod}, descriptor main {@code pkg.Main}, plus a
 * second main {@code pkg.Other} so any class scan would be ambiguous.
 */
public class TestModuleLaunch extends BaseTest {

	/**
	 * Build a modular jar: module test.mod, descriptor main pkg.Main, + pkg.Other.
	 */
	private Path buildJar(String dir, boolean withManifest) throws Exception {
		Path src = cwdDir.resolve(dir + "-src");
		Files.createDirectories(src.resolve("pkg"));
		Files.write(src.resolve("module-info.java"), "module test.mod {}".getBytes());
		Files.write(src.resolve("pkg/Main.java"),
				"package pkg; public class Main { public static void main(String... a){} }".getBytes());
		Files.write(src.resolve("pkg/Other.java"),
				"package pkg; public class Other { public static void main(String... a){} }".getBytes());
		Path classes = cwdDir.resolve(dir + "-classes");
		String javaHome = System.getProperty("java.home");
		exec(javaHome + "/bin/javac", "-d", classes.toString(),
				src.resolve("module-info.java").toString(),
				src.resolve("pkg/Main.java").toString(),
				src.resolve("pkg/Other.java").toString());
		Path jar = cwdDir.resolve(dir + ".jar");
		if (withManifest) {
			exec(javaHome + "/bin/jar", "--create", "--file", jar.toString(), "--main-class", "pkg.Main",
					"-C", classes.toString(), ".");
		} else {
			exec(javaHome + "/bin/jar", "--create", "--no-manifest", "--file", jar.toString(), "--main-class",
					"pkg.Main", "-C", classes.toString(), ".");
		}
		return jar;
	}

	// ---------- default (no flags): mode follows where the main-class came from
	// ----------

	@Test
	void default_noManifest_descriptorMain_runsAsModule() throws Exception {
		// no manifest Main-Class, descriptor has one -> module mode: bare -m test.mod
		Path jar = buildJar("d1", false);
		CaptureResult<Integer> r = checkedRun("run", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, allOf(containsString("-m test.mod"), not(containsString("test.mod/"))));
	}

	@Test
	void default_withManifest_runsOnClasspath() throws Exception {
		// manifest Main-Class present -> classpath, no -m
		Path jar = buildJar("d2", true);
		CaptureResult<Integer> r = checkedRun("run", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, allOf(containsString("pkg.Main"), not(containsString("-m "))));
	}

	// ---------- -m <class> (no --module): explicit main on classpath ----------

	@Test
	void mainOnly_runsClassOnClasspath() throws Exception {
		Path jar = buildJar("m1", false);
		CaptureResult<Integer> r = checkedRun("run", "-m", "pkg.Other", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, allOf(containsString("pkg.Other"), not(containsString("-m test.mod"))));
	}

	// ---------- --module forms ----------

	@Test
	void moduleBare_descriptorMain() throws Exception {
		Path jar = buildJar("b1", false);
		CaptureResult<Integer> r = checkedRun("run", "--module", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, allOf(containsString("-m test.mod"), not(containsString("test.mod/"))));
	}

	@Test
	void moduleNamed_descriptorMain() throws Exception {
		Path jar = buildJar("b2", false);
		CaptureResult<Integer> r = checkedRun("run", "--module=test.mod", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, allOf(containsString("-m test.mod"), not(containsString("test.mod/"))));
	}

	@Test
	void moduleWithClass_javaSyntax() throws Exception {
		// --module=<mod>/<class> maps verbatim to java's -m <mod>/<class>
		Path jar = buildJar("b3", false);
		CaptureResult<Integer> r = checkedRun("run", "--module=test.mod/pkg.Other", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, containsString("-m test.mod/pkg.Other"));
	}

	@Test
	void moduleWithGlob_promptsForSelection() throws Exception {
		// A glob is a request to search and choose, so it always prompts; running
		// automatically requires an explicit main class. Non-interactively it fails.
		Path jar = buildJar("b4", false);
		ExitException e = assertThrows(ExitException.class,
				() -> checkedRun("run", "--module=test.mod/pkg.Oth*", jar.toString()));
		assertThat(e.getMessage(), containsString("candidates"));
	}

	@Test
	void moduleEmptyModule_derivesModuleName() throws Exception {
		// jbang sugar: empty module part -> derive module from the jar
		Path jar = buildJar("b5", false);
		CaptureResult<Integer> r = checkedRun("run", "--module=/pkg.Other", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, containsString("-m test.mod/pkg.Other"));
	}

	@Test
	void moduleEmptyModuleGlob_promptsForSelection() throws Exception {
		// Empty-module derive + glob: still a search, so it prompts (fails
		// non-interactively).
		Path jar = buildJar("b6", false);
		ExitException e = assertThrows(ExitException.class,
				() -> checkedRun("run", "--module=/pkg.Oth*", jar.toString()));
		assertThat(e.getMessage(), containsString("candidates"));
	}

	@Test
	void moduleSplitForm_mainSeparate() throws Exception {
		// --module (bare) + --main X == --module=/X
		Path jar = buildJar("b7", false);
		CaptureResult<Integer> r = checkedRun("run", "--module", "--main", "pkg.Other", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, containsString("-m test.mod/pkg.Other"));
	}

	// ---------- anti-cases ----------

	@Test
	void conflictingMain_valueVsMainFlag_errors() throws Exception {
		// class given both in --module value and via --main, differing -> error
		Path jar = buildJar("a1", false);
		ExitException e = assertThrows(ExitException.class,
				() -> checkedRun("run", "--module=test.mod/pkg.Other", "--main", "pkg.Main", jar.toString()));
		assertThat(e.getMessage().toLowerCase(), containsString("main"));
	}

	@Test
	void unknownModule_isPassedThroughToJvm() throws Exception {
		// jbang does not pre-validate the module name (the launched name can
		// legitimately differ from enumerable module names); it emits the -m
		// launch and lets the JVM report an unknown module at runtime.
		Path jar = buildJar("a2", false);
		CaptureResult<Integer> r = checkedRun("run", "--module=does.not.exist", jar.toString());
		assertThat(r.result, equalTo(ExitException.EXIT_EXECUTE));
		assertThat(r.out, containsString("-m does.not.exist"));
	}

	@Test
	void globNoMatch_errors() throws Exception {
		Path jar = buildJar("a3", false);
		assertThrows(ExitException.class,
				() -> checkedRun("run", "--module=test.mod/pkg.Zzz*", jar.toString()));
	}

	private static void exec(String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).inheritIO().start();
		if (p.waitFor() != 0) {
			throw new IllegalStateException("command failed: " + String.join(" ", cmd));
		}
	}
}
