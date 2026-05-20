package dev.jbang.cli;

import java.util.*;

import org.aesh.command.Command;
import org.aesh.command.DefaultValueProvider;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.parser.AeshCommandLineParser;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.registry.CommandRegistry;

import dev.jbang.Configuration;

public class JBangDefaultValueProvider implements DefaultValueProvider {

	// Options that use StrictOptionParser/DebugOptionParser with "" (empty string)
	// as a sentinel for "used without value". The provider must not supply defaults
	// for these because the sentinel triggers config-file lookup in
	// RunMixin.resolveAfterParse() instead.
	private static final Set<String> FALLBACK_OPTIONS = new HashSet<>(Arrays.asList("debug", "jfr"));

	private static volatile Map<Class<?>, String> classToPath;

	@Override
	public String defaultValue(ProcessedOption option) {
		if (option.name() == null) {
			return null;
		}

		if (FALLBACK_OPTIONS.contains(option.name())) {
			return null;
		}

		String optName = option.name().replace("-", "");
		String fullPath = null;

		if (option.parent() != null && option.parent().getCommand() != null) {
			fullPath = getCommandPathForClass(option.parent().getCommand().getClass());
		}

		if (fullPath != null) {
			if (fullPath.startsWith("app.install.")) {
				return null;
			}

			String key = fullPath + "." + optName;
			String val = getValue(key);
			if (val != null) {
				return val;
			}
		}

		return getValue(optName);
	}

	static String getCommandPathForClass(Class<?> cmdClass) {
		Map<Class<?>, String> paths = getClassPaths();
		return paths.get(cmdClass);
	}

	static Map<Class<?>, String> getClassPaths() {
		if (classToPath == null) {
			Map<Class<?>, String> paths = new HashMap<>();
			buildPaths(JBang.class, Collections.emptyList(), paths);
			classToPath = paths;
		}
		return classToPath;
	}

	@SuppressWarnings("unchecked")
	private static void buildPaths(Class<?> cmdClass, List<String> parentNames, Map<Class<?>, String> paths) {
		try {
			CommandRegistry<CommandInvocation> registry = AeshCommandRegistryBuilder
				.<CommandInvocation>builder()
				.command((Class<Command<CommandInvocation>>) cmdClass)
				.create();
			for (String name : registry.getAllCommandNames()) {
				CommandLineParser<?> parser = registry.getCommand(name, "").getParser();
				buildPathsFromParser(parser, parentNames, paths);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to build command paths", e);
		}
	}

	private static void buildPathsFromParser(CommandLineParser<?> parser, List<String> parentNames,
			Map<Class<?>, String> paths) {
		String cmdName = parser.getProcessedCommand().name();
		List<String> currentPath = new ArrayList<>(parentNames);
		if (!"jbang".equals(cmdName)) {
			currentPath.add(cmdName);
		}
		Object cmd = parser.getProcessedCommand().getCommand();
		if (cmd != null) {
			paths.put(cmd.getClass(), currentPath.isEmpty() ? cmdName : String.join(".", currentPath));
		}
		if (parser.isGroupCommand()) {
			parser.getAllNames();
			AeshCommandLineParser<?> aeshParser = (AeshCommandLineParser<?>) parser;
			if (aeshParser.getChildParsers() != null) {
				for (CommandLineParser<?> child : aeshParser.getChildParsers()) {
					buildPathsFromParser(child, currentPath, paths);
				}
			}
		}
	}

	private static String getValue(String key) {
		String propkey = "jbang." + key;
		if (System.getProperties().containsKey(propkey)) {
			return System.getProperty(propkey);
		}
		Configuration cfg = Configuration.instance();
		if (cfg.containsKey(key)) {
			return Objects.toString(cfg.get(key));
		}
		return null;
	}
}
