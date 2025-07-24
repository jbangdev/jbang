package dev.jbang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.cli.Edit;
import dev.jbang.cli.Init;
import dev.jbang.cli.JBang;
import dev.jbang.cli.Run;
import dev.jbang.source.ResourceRef;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestConfiguration extends BaseTest {

	static final String config = "foo = baz\n" +
			"init.template = bye\n" +
			"edit.open = someeditor.cfg\n" +
			"run.jfr = dummyjfr.cfg\n";

	@BeforeEach
	void initCfg() throws IOException {
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	public void testValues() {
		Configuration cfg = Configuration.create();
		cfg.put("foo", "bar");
		assertThat(cfg.get("foo")).isEqualTo("bar");
	}

	@Test
	public void testValuesInherited() {
		Configuration fallback = Configuration.create();
		fallback.put("foo", "bar");
		Configuration cfg = Configuration.create(fallback);
		assertThat(cfg.get("foo")).isEqualTo("bar");
	}

	@Test
	public void testValuesInheritedOverridden() {
		Configuration fallback = Configuration.create();
		fallback.put("foo", "bar");
		Configuration cfg = Configuration.create(fallback);
		cfg.put("foo", "baz");
		assertThat(cfg.get("foo")).isEqualTo("baz");
	}

	@Test
	public void testValuesInheritedRemoved() {
		Configuration fallback = Configuration.create();
		fallback.put("foo", "bar");
		Configuration cfg = Configuration.create(fallback);
		cfg.put("foo", null);
		assertThat(cfg.get("foo")).isNull();
	}

	@Test
	public void testWriteReadConfig() throws IOException {
		Configuration fallback = Configuration.create();
		fallback.put("foo", "abc");
		fallback.put("bar", "def");
		Configuration cfg = Configuration.create(fallback);
		cfg.put("bar", "ghi");
		cfg.put("baz", "jkl");
		Path cfgFile = jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		Configuration.write(cfgFile, cfg.flatten());
		Configuration cfg2 = Configuration.read(cfgFile);
		assertThat(cfg2.get("foo")).isEqualTo("abc");
		assertThat(cfg2.get("bar")).isEqualTo("ghi");
		assertThat(cfg2.get("baz")).isEqualTo("jkl");
	}

	@Test
	public void testReadAndGetConfig() throws IOException {
		Path cfgFile = jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		Configuration cfgRead = Configuration.read(cfgFile);
		assertThat(cfgRead.getStoreRef()).isNull();
		Configuration cfgGet = Configuration.get(cfgFile);
		assertThat(cfgGet.getStoreRef()).isNotNull();
		assertThat(cfgGet.getStoreRef()).isEqualTo(ResourceRef.forResource(cfgFile.toString()));
	}

	@Test
	public void testDefaults() {
		Configuration cfg = Configuration.defaults();
		assertThat(cfg.get("init.template")).isEqualTo("hello");
		assertThat(cfg.get("run.debug")).isEqualTo("4004");
	}

	@Test
	public void testInstance() {
		Configuration cfg = Configuration.instance();
		assertThat(cfg.get("foo")).isEqualTo("baz");
		assertThat(cfg.get("init.template")).isEqualTo("bye");
		assertThat(cfg.get("run.debug")).isEqualTo("4004");
	}

	@Test
	public void testInstanceOverride() throws IOException {
		String config = "foo = explicit";
		environmentVariables.set("JBANG_CONFIG", "explicit-config");
		try {
			Files.write(Util.getCwd().resolve("explicit-config"), config.getBytes());
			Configuration cfg = Configuration.instance();
			assertThat(cfg.get("foo")).isEqualTo("explicit");
		} finally {
			environmentVariables.clear("JBANG_CONFIG");
		}
	}

	@Test
	public void testCommandDefaults() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("init", "dummy");
		Init init = (Init) pr.subcommand().commandSpec().userObject();
		assertThat(init.initTemplate).isEqualTo("bye");
	}

	@Test
	public void testCommandEditOpenDefault() {
		environmentVariables.clear("JBANG_EDITOR");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get()).isEqualTo("someeditor.cfg");
	}

	@Test
	public void testCommandEditOpenDefaultEnv() {
		environmentVariables.set("JBANG_EDITOR", "someeditor.env");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get()).isEqualTo("someeditor.env");
	}

	@Test
	public void testCommandEditOpenFallback() {
		environmentVariables.clear("JBANG_EDITOR");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "--open", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get()).isEqualTo("someeditor.cfg");
	}

	@Test
	public void testCommandEditOpenFallbackEnv() {
		environmentVariables.set("JBANG_EDITOR", "someeditor.env");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("edit", "--open", "dummy");
		Edit edit = (Edit) pr.subcommand().commandSpec().userObject();
		assertThat(edit.editor.get()).isEqualTo("someeditor.env");
	}

	@Test
	public void testCommandFallbacks() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "dummy");
		Run run = (Run) pr.subcommand().commandSpec().userObject();
		assertThat(run.runMixin.debugString).isNull();
		assertThat(run.runMixin.flightRecorderString).isNullOrEmpty();
	}

	@Test
	public void testCommandFallbacks2() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--jfr", "--debug", "dummy");
		Run run = (Run) pr.subcommand().commandSpec().userObject();
		assertThat(run.runMixin.debugString)
			.satisfies(
				arg -> assertThat(arg).containsEntry("address", "4004"),
				arg -> assertThat(arg).hasSize(1)
			);
		assertThat(run.runMixin.flightRecorderString).isEqualTo("dummyjfr.cfg");
	}

}
