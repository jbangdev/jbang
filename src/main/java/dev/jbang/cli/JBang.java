package dev.jbang.cli;

import java.io.IOException;

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.Execution;
import org.aesh.command.Executor;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.option.Option;
import org.aesh.command.registry.CommandRegistry;

import dev.jbang.Main;
import dev.jbang.util.Util;

@GroupCommandDefinition(name = "jbang", description = "jbang is a tool for building and running .java/.jsh scripts and jar packages.", groupCommands = {
		Run.class, Build.class, Edit.class, Init.class, Alias.class, Template.class, Catalog.class, Trust.class,
		Cache.class, Completion.class, Jdk.class, Version.class, Wrapper.class, Info.class, App.class,
		Export.class, Config.class,
		Deps.class }, generateHelp = true, helpSectionProvider = ExternalCommandsProvider.class, defaultValueProvider = JBangDefaultValueProvider.class)
public class JBang extends BaseCommand {

	@Option(shortName = 'V', name = "version", hasValue = false, description = "Display version info (use `jbang --verbose version` for more details)")
	boolean versionRequested;

	@Override
	public void beforeParse() {
		Util.setVerbose(false);
		Util.setQuiet(false);
		Util.setOffline(false);
		Util.setFresh(false);
		Util.setPreview(false);
		Util.setPrintExceptions(false);
	}

	public Integer doCall() throws IOException {
		if (versionRequested) {
			System.out.println(Util.getJBangVersion());
			return EXIT_OK;
		}
		if (commandInvocation != null) {
			System.out.println(commandInvocation.getHelpInfo());
		}
		return EXIT_OK;
	}

	public static int execute(String... args) {
		try {
			String[] newArgs = Main.handleDefaultRun(args);
			CommandResult result = AeshRuntimeRunner.builder()
				.command(JBang.class)
				.args(newArgs)
				.execute();
			return result != null ? result.getResultValue() : BaseCommand.EXIT_OK;
		} catch (ExitException e) {
			return e.getStatus();
		} catch (RuntimeException e) {
			Throwable cause = e.getCause();
			Throwable deepest = e;
			while (cause != null) {
				if (cause instanceof ExitException) {
					return ((ExitException) cause).getStatus();
				}
				deepest = cause;
				cause = cause.getCause();
			}
			if (deepest instanceof RuntimeException) {
				throw (RuntimeException) deepest;
			}
			throw e;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends Command> T parseCommand(String... args) {
		try {
			String[] newArgs = Main.handleDefaultRun(args);
			CommandRegistry registry = AeshCommandRegistryBuilder.builder().command(JBang.class).create();
			org.aesh.command.CommandRuntime runtime = AeshCommandRuntimeBuilder.builder()
				.commandRegistry(registry)
				.build();
			String commandName = (String) registry.getAllCommandNames().iterator().next();
			Executor<?> executor = runtime.buildExecutor(commandName, newArgs);
			Execution<?> execution = executor.getExecutions().get(executor.getExecutions().size() - 1);
			execution.populateCommand();
			T cmd = (T) execution.getCommand();
			// Workaround: aesh inherited options are propagated via parser-level
			// reflection but afterParse() may not fire reliably in the
			// buildExecutor path. Scan raw args for global flags as fallback.
			applyParentFlags(newArgs);
			return cmd;
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse command", e);
		}
	}

	private static void applyParentFlags(String[] args) {
		for (String arg : args) {
			if ("--".equals(arg)) {
				break;
			}
			switch (arg) {
			case "--verbose":
				Util.setVerbose(true);
				break;
			case "--quiet":
				Util.setQuiet(true);
				break;
			case "--offline":
			case "-o":
				Util.setOffline(true);
				break;
			case "--fresh":
				Util.setFresh(true);
				break;
			case "--preview":
				Util.setPreview(true);
				break;
			case "--stacktrace":
			case "-x":
				Util.setPrintExceptions(true);
				break;
			}
		}
	}
}
