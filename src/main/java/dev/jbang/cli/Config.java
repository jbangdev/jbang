package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Configuration;
import dev.jbang.Settings;
import dev.jbang.util.ConfigUtil;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

@CommandDefinition(name = "config", description = "Read and write configuration options.", groupCommands = {
		Config.ConfigGet.class, Config.ConfigSet.class,
		Config.ConfigUnset.class,
		Config.ConfigList.class }, generateHelp = true, helpGroup = "Configuration")
public class Config extends BaseCommand {

	// IMPORTANT: These options have to be maintained manually! Make sure to add
	// an option for each configuration key that gets added!
	static AvailableOption[] extraOptions = {
			new AvailableOption(Settings.CONFIG_CACHE_EVICT,
					"Time that locally cached files are kept before they are evicted. Can be a simple number in seconds, an ISO8601 Duration or the word 'never'"),
			new AvailableOption(Settings.CONFIG_CONNECTION_TIMEOUT,
					"The timeout in milliseconds that will be used for any remote connections")
	};

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	static abstract class BaseConfigCommand extends BaseCommand {

		@Option(shortName = 'g', name = "global", hasValue = false, description = "Use the global (user) config file")
		boolean global;

		@Option(shortName = 'f', name = "file", description = "Path to the config file to use")
		Path configFile;

		protected Configuration getConfig(Path cwd, boolean strict) {
			Path cfgFile = getConfigFile(strict);
			if (cfgFile != null) {
				return Configuration.get(cfgFile);
			} else {
				return Configuration.getMerged();
			}
		}

		protected Path getConfigFile(boolean strict) {
			Path cfg;
			if (global) {
				cfg = Settings.getUserConfigFile();
			} else {
				Path cfgPath = configFile;
				if (cfgPath != null && Files.isDirectory(cfgPath)) {
					Path defaultConfig = cfgPath.resolve(Configuration.JBANG_CONFIG_PROPS);
					Path hiddenConfig = cfgPath.resolve(Settings.JBANG_DOT_DIR)
						.resolve(Configuration.JBANG_CONFIG_PROPS);
					if (!Files.exists(defaultConfig) && Files.exists(hiddenConfig)) {
						cfg = hiddenConfig;
					} else {
						cfg = defaultConfig;
					}
				} else {
					cfg = cfgPath;
				}
				if (strict && cfg != null && !Files.isRegularFile(cfg)) {
					throw new ExitException(EXIT_INVALID_INPUT, "Config file not found at: " + configFile);
				}
			}
			return cfg;
		}
	}

	@CommandDefinition(name = "get", description = "Get a configuration value", generateHelp = true)
	public static class ConfigGet extends BaseConfigCommand {
		@Argument(paramLabel = "key", arity = "1", description = "The name of the configuration option to get", required = true)
		String key;

		@Override
		public Integer doCall() throws IOException {
			Configuration cfg = getConfig(null, false);
			if (cfg.containsKey(key)) {
				String res = Objects.toString(cfg.get(key));
				System.out.println(res);
				return EXIT_OK;
			} else {
				Util.infoMsg("No configuration option found with that name: " + key);
				return EXIT_INVALID_INPUT;
			}
		}
	}

	@CommandDefinition(name = "set", description = "Set a configuration value", generateHelp = true)
	public static class ConfigSet extends BaseConfigCommand {
		@Argument(paramLabel = "key", index = "0", arity = "1", description = "The name of the configuration option to set", required = true)
		String keyOrKeyValue;

		@Argument(paramLabel = "value", index = "1", arity = "0..1", description = "The value to set for the configuration option")
		String value;

		@Override
		public Integer doCall() throws IOException {
			String key;
			String val;
			if (value != null) {
				key = keyOrKeyValue;
				val = value;
			} else {
				int eqIdx = keyOrKeyValue.indexOf('=');
				if (eqIdx >= 0) {
					key = keyOrKeyValue.substring(0, eqIdx);
					val = keyOrKeyValue.substring(eqIdx + 1);
				} else {
					throw new ExitException(EXIT_INVALID_INPUT,
							"Expected key=value format or separate key and value arguments");
				}
			}
			Path cfgFile = getConfigFile(false);
			if (cfgFile != null) {
				ConfigUtil.setConfigValue(cfgFile, key, val);
			} else {
				cfgFile = ConfigUtil.setNearestConfigValue(key, val);
			}
			Util.infoMsg("Option '" + key + "' set to '" + val + "' in " + cfgFile);
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "unset", description = "Remove a configuration value", generateHelp = true)
	public static class ConfigUnset extends BaseConfigCommand {
		@Argument(paramLabel = "key", arity = "1", description = "The name of the configuration option to remove", required = true)
		String key;

		@Override
		public Integer doCall() throws IOException {
			Configuration cfg = getConfig(null, false);
			if (cfg.containsKey(key)) {
				Path cfgFile = getConfigFile(false);
				if (cfgFile != null) {
					ConfigUtil.unsetConfigValue(cfgFile, key);
				} else {
					cfgFile = ConfigUtil.unsetNearestConfigValue(key);
				}
				if (cfgFile != null) {
					Util.infoMsg("Option '" + key + "' removed from " + cfgFile);
				} else {
					Util.warnMsg("Cannot remove built-in option '" + key + "'");
				}
				return EXIT_OK;
			} else {
				Util.infoMsg("No configuration option found with that name: " + key);
				return EXIT_INVALID_INPUT;
			}
		}
	}

	@CommandDefinition(name = "list", description = "List active configuration values", generateHelp = true)
	public static class ConfigList extends BaseConfigCommand {
		@Option(name = "show-origin", hasValue = false, description = "Show the origin of the configuration")
		boolean showOrigin;

		@Option(name = "show-available", hasValue = false, description = "Show the available key names")
		boolean showAvailable;

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		OutputFormat format;

		@Override
		public Integer doCall() throws IOException {
			PrintStream out = System.out;
			if (showAvailable && showOrigin) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"Options '--show-available' and '--show-origin' cannot be used together");
			}
			if (showAvailable) {
				Set<AvailableOption> opts = new HashSet<>(Arrays.asList(Config.extraOptions));
				gatherKeys(JBang.class, opts);
				if (format == OutputFormat.json) {
					Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					parser.toJson(opts.stream().sorted().collect(Collectors.toList()), out);
				} else {
					opts.stream().sorted().forEach(opt -> {
						out.print(ConsoleOutput.yellow(opt.key));
						out.print(" = ");
						out.println(opt.description);
					});
				}
			} else {
				Configuration cfg = getConfig(null, true);
				if (showOrigin) {
					printConfigWithOrigin(out, cfg, format);
				} else {
					printConfig(out, cfg, format);
				}
			}
			return EXIT_OK;
		}

		@SuppressWarnings("unchecked")
		private void gatherKeys(Class<?> cmdClass, Set<AvailableOption> keys) {
			try {
				org.aesh.command.registry.CommandRegistry<org.aesh.command.invocation.CommandInvocation> registry = org.aesh.command.impl.registry.AeshCommandRegistryBuilder.<org.aesh.command.invocation.CommandInvocation>builder()
					.command((Class<org.aesh.command.Command<org.aesh.command.invocation.CommandInvocation>>) cmdClass)
					.create();
				for (String name : registry.getAllCommandNames()) {
					org.aesh.command.container.CommandContainer<org.aesh.command.invocation.CommandInvocation> container = registry
						.getCommand(name, "");
					gatherFromParser(container.getParser(), Collections.emptyList(), keys);
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to gather config keys", e);
			}
		}

		private void gatherFromParser(
				org.aesh.command.impl.parser.CommandLineParser<?> parser,
				List<String> parentPath, Set<AvailableOption> keys) {
			org.aesh.command.impl.internal.ProcessedCommand<?, ?> cmd = parser.getProcessedCommand();
			String cmdName = cmd.name();
			List<String> currentPath = new ArrayList<>(parentPath);
			if (!"jbang".equals(cmdName)) {
				currentPath.add(cmdName);
			}
			String path = currentPath.isEmpty() ? cmdName : String.join(".", currentPath);
			for (org.aesh.command.impl.internal.ProcessedOption opt : cmd.getOptions()) {
				if (opt.name() != null && !opt.name().isEmpty()) {
					String key = path + "." + opt.name().replace("-", "");
					keys.add(new AvailableOption(key, opt.description()));
				}
			}
			if (parser.isGroupCommand()) {
				parser.getAllNames();
				org.aesh.command.impl.parser.AeshCommandLineParser<?> aeshParser = (org.aesh.command.impl.parser.AeshCommandLineParser<?>) parser;
				if (aeshParser.getChildParsers() != null) {
					for (org.aesh.command.impl.parser.CommandLineParser<?> child : aeshParser.getChildParsers()) {
						gatherFromParser(child, currentPath, keys);
					}
				}
			}
		}

		private void printConfig(PrintStream out, Configuration cfg, OutputFormat format) {
			if (format == OutputFormat.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(cfg.asMap(), out);
			} else {
				cfg.flatten()
					.keySet()
					.stream()
					.sorted()
					.forEach(key -> out.println(ConsoleOutput.yellow(key) + " = " + cfg.get(key)));
			}
		}

		static class OriginOut {
			String resourceRef;
			Map<String, String> properties;
		}

		private void printConfigWithOrigin(PrintStream out, Configuration cfg, OutputFormat format) {
			List<OriginOut> orgs = new ArrayList<>();
			while (cfg != null) {
				OriginOut org = new OriginOut();
				org.resourceRef = cfg.getStoreRef().getOriginalResource();
				org.properties = cfg.asMap();
				orgs.add(org);
				cfg = cfg.getFallback();
			}

			if (format == OutputFormat.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(orgs, out);
			} else {
				Set<String> printedKeys = new HashSet<>();
				for (OriginOut org : orgs) {
					Set<String> keysToPrint = org.properties.keySet()
						.stream()
						.filter(key -> !printedKeys.contains(key))
						.collect(Collectors.toSet());
					if (!keysToPrint.isEmpty()) {
						out.println(ConsoleOutput.bold(org.resourceRef));
						keysToPrint.stream()
							.sorted()
							.forEach(key -> out.println(
									"   " + ConsoleOutput.yellow(key) + " = " + org.properties.get(key)));
						printedKeys.addAll(keysToPrint);
					}
				}
			}
		}
	}
}

class AvailableOption implements Comparable<AvailableOption> {
	final String key;
	final String description;

	public AvailableOption(String key, String description) {
		this.key = key;
		this.description = description;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		AvailableOption that = (AvailableOption) o;
		return key.equals(that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key);
	}

	@Override
	public int compareTo(AvailableOption o) {
		return key.compareTo(o.key);
	}
}
