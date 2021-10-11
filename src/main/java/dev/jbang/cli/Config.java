package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.jbang.Configuration;
import dev.jbang.Settings;
import dev.jbang.util.ConfigUtil;
import dev.jbang.util.ConsoleOutput;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "config", description = "Read and write configuration options.")
public class Config {

	@CommandLine.Option(names = { "--global", "-g" }, description = "Use the global (user) config file")
	boolean global;

	@CommandLine.Option(names = { "--file", "-f" }, description = "Path to the config file to use")
	Path configFile;

	@CommandLine.Command(name = "get", description = "Get a configuration value")
	public Integer get(
			@CommandLine.Parameters(index = "0", arity = "1", description = "The name of the configuration option to get") String key) {
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

	@CommandLine.Command(name = "set", description = "Set a configuration value")
	public Integer set(
			@CommandLine.Parameters(index = "0", arity = "1", description = "The name of the configuration option to set") String key,
			@CommandLine.Parameters(index = "1", arity = "1", description = "The value to set for the configuration option") String value)
			throws IOException {
		Path cfgFile = getConfigFile(false);
		if (cfgFile != null) {
			ConfigUtil.setConfigValue(cfgFile, key, value);
		} else {
			cfgFile = ConfigUtil.setNearestConfigValue(key, value);
		}
		Util.verboseMsg("Option '" + key + "' set to '" + value + "' in " + cfgFile);
		return EXIT_OK;
	}

	@CommandLine.Command(name = "unset", description = "Remove a configuration value")
	public Integer unset(
			@CommandLine.Parameters(index = "0", arity = "1", description = "The name of the configuration option to set") String key)
			throws IOException {
		Configuration cfg = getConfig(null, false);
		if (cfg.containsKey(key)) {
			Path cfgFile = getConfigFile(false);
			if (cfgFile != null) {
				ConfigUtil.unsetConfigValue(cfgFile, key);
			} else {
				cfgFile = ConfigUtil.unsetNearestConfigValue(key);
			}
			Util.verboseMsg("Option '" + key + "' removed from in " + cfgFile);
			return EXIT_OK;
		} else {
			Util.infoMsg("No configuration option found with that name: " + key);
			return EXIT_INVALID_INPUT;
		}
	}

	@CommandLine.Command(name = "list", description = "List active configuration values")
	public Integer list(
			@CommandLine.Option(names = {
					"--show-origin" }, description = "Show the origin of the catalog") boolean showOrigin) {
		PrintStream out = System.out;
		Configuration cfg = getConfig(null, true);
		if (showOrigin) {
			while (cfg != null) {
				Set<String> printedKeys = new HashSet<>();
				printConfigWithOrigin(out, cfg, printedKeys);
				cfg = cfg.getFallback();
			}
		} else {
			printConfig(out, cfg);
		}
		return EXIT_OK;
	}

	private void printConfig(PrintStream out, Configuration cfg) {
		cfg.keySet().stream().sorted().forEach(key -> out.println(ConsoleOutput.yellow(key) + " = " + cfg.get(key)));
	}

	private void printConfigWithOrigin(PrintStream out, Configuration cfg, Set<String> printedKeys) {
		Set<String> keysToPrint = cfg	.keySet()
										.stream()
										.filter(key -> !printedKeys.contains(key))
										.collect(Collectors.toSet());
		if (!keysToPrint.isEmpty()) {
			out.println(ConsoleOutput.bold(cfg.getStoreRef().getOriginalResource()));
			keysToPrint	.stream()
						.sorted()
						.forEach(key -> out.println("   " + ConsoleOutput.yellow(key) + " = " + cfg.get(key)));
			printedKeys.addAll(keysToPrint);
		}
	}

	private Configuration getConfig(Path cwd, boolean strict) {
		Path cfgFile = getConfigFile(strict);
		if (cfgFile != null) {
			return Configuration.read(cfgFile);
		} else {
			return Configuration.getMerged();
		}
	}

	private Path getConfigFile(boolean strict) {
		Path cfg;
		if (global) {
			cfg = Settings.getUserConfigFile();
		} else {
			if (configFile != null && Files.isDirectory(configFile)) {
				Path defaultConfig = configFile.resolve(Configuration.JBANG_CONFIG_JSON);
				Path hiddenConfig = configFile	.resolve(Settings.JBANG_DOT_DIR)
												.resolve(Configuration.JBANG_CONFIG_JSON);
				if (!Files.exists(defaultConfig) && Files.exists(hiddenConfig)) {
					cfg = hiddenConfig;
				} else {
					cfg = defaultConfig;
				}
			} else {
				cfg = configFile;
			}
			if (strict && cfg != null && !Files.isRegularFile(cfg)) {
				throw new IllegalArgumentException("Config file not found at: " + configFile);
			}
		}
		return cfg;
	}
}
