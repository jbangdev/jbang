package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import io.qameta.allure.Description;

@EnabledForJreRange(min = JRE.JAVA_9)
public class JshIT extends BaseIT {

	@BeforeEach
	public void setup() {
		assumeTrue(testJavaMajorVersion >= 9, "Jsh is not supported on Java 8");
	}

	// Scenario: jshell helloworld
	@Test
	@Description("jshell helloworld")
	public void shouldRunJShellHelloWorld() {
		assertThat(shell("jbang helloworld.jsh"))
			.succeeded()
			.outIsExactly("Hello World" + lineSeparator());
	}

	// Scenario: jshell arguments
	@Test
	@Description("jshell arguments")
	public void shouldHandleJShellArguments() {
		assertThat(shell("jbang helloworld.jsh JSH!"))
			.succeeded()
			.outIsExactly("Hello JSH!" + lineSeparator());
	}

	// Scenario: jsh default system property
	@Test
	@Description("jsh default system property")
	public void shouldHandleDefaultSystemProperty() {
		assertThat(shell("jbang -Dvalue hello.jsh"))
			.succeeded()
			.outIsExactly("true" + lineSeparator());
	}

	// Scenario: jsh system property
	@Test
	@Description("jsh system property")
	public void shouldHandleSystemProperty() {
		assertThat(shell("jbang -Dvalue=hello hello.jsh"))
			.succeeded()
			.outIsExactly("hello" + lineSeparator());
	}

	// Scenario: jsh quoted system property
	@Test
	@Description("jsh quoted system property")
	public void shouldHandleQuotedSystemProperty() {
		assertThat(shell("jbang -Dvalue=\"a quoted\" hello.jsh"))
			.succeeded()
			.outIsExactly("a quoted" + lineSeparator());
	}

	// Scenario: jsh fail on --native
	@Test
	@Description("jsh fail on --native")
	public void shouldFailOnNative() {
		assertThat(shell("jbang --native hello.jsh"))
			.errContains(".jsh cannot be used with --native");
	}

	// Scenario: force jsh
	@Test
	@Description("force jsh")
	public void shouldForceJsh() {
		assertThat(shell("jbang --jsh hellojsh hello"))
			.succeeded()
			.outIsExactly("hello" + lineSeparator());
	}

	// Scenario: jsh sources
	@Test
	@Description("jsh sources")
	public void shouldHandleJshSources() {
		assertThat(shell("jbang main.jsh"))
			.succeeded()
			.outIsExactly("hello" + lineSeparator());
	}

	// Scenario: jsh with deps 1
	@Test
	@Description("jsh with deps")
	public void shouldHandleJshWithDeps() {
		assertThat(shell("jbang deps.jsh"))
			.succeeded()
			.errNotContains(".NoClassDef")
			.outContains("Fake output:");
	}

	// Scenario: as code option
	@Test
	@Description("as code option")
	public void shouldHandleCodeOption() {
		assertThat(shell("jbang --code \"System.out.println(\\\"Hello\\\")\" jbangtest"))
			.succeeded()
			.outIsExactly("Hello"
					+ lineSeparator());
	}

	// Scenario: jshell ordering
	@Test
	@Description("jshell ordering")
	public void shouldHandleJshellOrdering() {
		assertThat(shell("jbang -s helloworld.java --code \"helloworld.main()\""))
			.succeeded()
			.outIsExactly("Hello World"
					+ lineSeparator());
	}
}