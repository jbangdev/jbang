package dev.jbang.cli;

import java.util.*;

import org.aesh.command.CommandDefinition;
import org.aesh.command.DefaultValueProvider;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.impl.internal.ProcessedOption;

import dev.jbang.Configuration;

public class JBangDefaultValueProvider implements DefaultValueProvider {

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

	static String getCommandPath(String commandName) {
		Map<Class<?>, String> paths = getClassPaths();
		for (Map.Entry<Class<?>, String> entry : paths.entrySet()) {
			if (entry.getValue().endsWith("." + commandName) || entry.getValue().equals(commandName)) {
				return entry.getValue();
			}
		}
		return commandName;
	}

	static Map<Class<?>, String> getClassPaths() {
		if (classToPath == null) {
			Map<Class<?>, String> paths = new HashMap<>();
			buildPaths(JBang.class, Collections.emptyList(), paths);
			classToPath = paths;
		}
		return classToPath;
	}

	private static void buildPaths(Class<?> cmdClass, List<String> parentNames, Map<Class<?>, String> paths) {
		GroupCommandDefinition gcd = cmdClass.getAnnotation(GroupCommandDefinition.class);
		if (gcd != null) {
			List<String> currentPath = new ArrayList<>(parentNames);
			if (!"jbang".equals(gcd.name())) {
				currentPath.add(gcd.name());
			}
			paths.put(cmdClass, currentPath.isEmpty() ? gcd.name() : String.join(".", currentPath));
			for (Class<?> child : gcd.groupCommands()) {
				buildPaths(child, currentPath, paths);
			}
		}
		CommandDefinition cd = cmdClass.getAnnotation(CommandDefinition.class);
		if (cd != null) {
			List<String> currentPath = new ArrayList<>(parentNames);
			currentPath.add(cd.name());
			paths.put(cmdClass, String.join(".", currentPath));
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
