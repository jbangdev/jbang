package dev.jbang.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import dev.jbang.Configuration;
import dev.jbang.Settings;

public class ConfigUtil {
	public static Path setNearestConfigValue(String key, String value) throws IOException {
		Path configFile = getConfigFile(null);
		setConfigValue(configFile, key, value);
		return configFile;
	}

	public static void setConfigValue(Path configFile, String key, String value) throws IOException {
		Configuration cfg = Configuration.read(configFile);
		if (configFile != null) {
			cfg.put(key, value);
		}
		Configuration.write(configFile, cfg);
	}

	public static Path unsetNearestConfigValue(String key) throws IOException {
		Path configFile = findNearestLocalConfigWithKey(null, key);
		if (configFile != null) {
			unsetConfigValue(configFile, key);
		}
		return configFile;
	}

	public static void unsetConfigValue(Path configFile, String key) throws IOException {
		Configuration cfg = Configuration.read(configFile);
		if (cfg.containsKey(key)) {
			cfg.remove(key);
			Configuration.write(configFile, cfg);
		}
	}

	/**
	 * Will either return the path to the given config or search for the nearest
	 * config starting from cwd.
	 *
	 * @param config The config to return or null to return the nearest config
	 * @return Path to a config
	 */
	private static Path getConfigFile(Path config) {
		if (config == null) {
			config = findNearestLocalConfig();
			if (config == null) {
				config = Settings.getUserConfigFile();
			}
		}
		return config;
	}

	private static Path findNearestLocalConfig() {
		return Util.findNearestWith(null, Util.acceptFile(Configuration.JBANG_CONFIG_PROPS));
	}

	private static Path findNearestLocalConfigWithKey(Path dir, String key) {
		Function<Path, Path> accept = Util.acceptFile(Configuration.JBANG_CONFIG_PROPS);
		return Util.findNearestWith(dir, accept.andThen(Util.notNull(configFile -> {
			Configuration cfg = Configuration.read(configFile);
			return cfg.containsKey(key) ? configFile : null;
		})));
	}
}
