package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.cli.Edit;
import dev.jbang.cli.Init;
import dev.jbang.cli.JBang;
import dev.jbang.cli.Run;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestConfiguration extends BaseTest {

	static final String config = "{\n" +
			"  \"foo\": \"baz\",\n" +
			"  \"init.template\": \"bye\",\n" +
			"  \"edit.open\": \"someeditor.cfg\",\n" +
			"  \"run.jfr\": \"dummyjfr.cfg\"\n" +
			"}";

	@BeforeEach
	void initCfg() throws IOException {
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_JSON), config.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	public void testWriteReadConfig() throws IOException {
		Configuration cfg = Configuration.create();
		cfg.put("foo", "bar");
		Path cfgFile = jbangTempDir.resolve(Configuration.JBANG_CONFIG_JSON);
		Configuration.write(cfgFile, cfg);
		Configuration cfg2 = Configuration.read(cfgFile);
		assertThat(cfg, equalTo(cfg2));
	}

	@Test
	public void testDefaults() {
		Configuration cfg = Configuration.defaults();
		assertThat(cfg.get("init.template"), equalTo("hello"));
		assertThat(cfg.get("run.debug"), equalTo("4004"));
	}

	@Test
	public void testInstance() {
		Configuration cfg = Configuration.instance();
		assertThat(cfg.get("foo"), equalTo("baz"));
		assertThat(cfg.get("init.template"), equalTo("bye"));
		assertThat(cfg.get("run.debug"), equalTo("4004"));
	}

	@Test
	public void testCommandDefaults() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("init", "dummy");
		Init init = (Init) pr.subcommand().commandSpec().userObject();
		assertThat(init.initTemplate, equalTo("bye"));
	}

	@Test
	public void testCommandEditOpenDefault() {
		environmentVariables.clear("JBANG_EDITOR");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get(), equalTo("someeditor.cfg"));
	}

	@Test
	public void testCommandEditOpenDefaultEnv() {
		environmentVariables.set("JBANG_EDITOR", "someeditor.env");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get(), equalTo("someeditor.env"));
	}

	@Test
	public void testCommandEditOpenFallback() {
		environmentVariables.clear("JBANG_EDITOR");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "--open", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get(), equalTo("someeditor.cfg"));
	}

	@Test
	public void testCommandEditOpenFallbackEnv() {
		environmentVariables.set("JBANG_EDITOR", "someeditor.env");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "--open", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get(), equalTo("someeditor.env"));
	}

	@Test
	public void testCommandFallbacks() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "dummy");
		Run run = (Run) pr.subcommand().commandSpec().userObject();
		assertThat(run.debugString, emptyOrNullString());
		assertThat(run.flightRecorderString, emptyOrNullString());
	}

	@Test
	public void testCommandFallbacks2() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--jfr", "--debug", "dummy");
		Run run = (Run) pr.subcommand().commandSpec().userObject();
		assertThat(run.debugString, equalTo("4004"));
		assertThat(run.flightRecorderString, equalTo("dummyjfr.cfg"));
	}

}
