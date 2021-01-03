package dev.jbang;

import static dev.jbang.Util.verboseMsg;
import static dev.jbang.Util.warnMsg;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;

import com.google.gson.Gson;

public class ConfigUtil {

	public static class Config extends HashMap<String, Object> {
		public String editor() {
			return getString("editor", null);
		}

		private String getString(String key, String defaultValue) {
			if (containsKey(key)) {
				return Objects.toString(get(key));
			} else {
				return defaultValue;
			}
		}
	}

	public static final Config defaults;

	static {
		defaults = new Config();
	}

	/**
	 * Returns a Config containing all the settings from local config files merged
	 * into one. This follows the system where settings that are "nearest" have
	 * priority. The Config starts out with all the values from `defaults`, any
	 * values read from files have priority.
	 *
	 * @param cwd The current working directory
	 * @return a Config object
	 */
	public static Config getMergedConfig(Path cwd) {
		if (cwd == null) {
			cwd = Util.getCwd();
		}
		Config result = new Config();
		result.putAll(defaults);
		mergeConfig(Settings.getUserConfigFile(), result);
		Util.mergeLocalFiles(cwd, Paths.get(Settings.CONFIG_JSON), result, ConfigUtil::mergeConfig);
		return result;
	}

	private static void mergeConfig(Path catalogFile, Config result) {
		Config rd = readConfig(catalogFile);
		result.putAll(rd);
	}

	public static Config readConfig(Path configFile) {
		Config cfg = new Config();
		if (Files.isRegularFile(configFile)) {
			try (Reader in = Files.newBufferedReader(configFile)) {
				Gson parser = new Gson();
				Config tmp = parser.fromJson(in, Config.class);
				if (tmp != null) {
					cfg = tmp;
				} else {
					warnMsg("Couldn't parse configuration: " + configFile);
				}
			} catch (IOException e) {
				// Ignore errors
			}
		}
		return cfg;
	}

	public static void writeConfig(Path configFile, Config cfg) throws IOException {
		verboseMsg(String.format("Reading configuration from %s", configFile));
		Util.writeString(configFile, toJSon(cfg));
	}

	static String toJSon(Config cfg) {
		String config = Settings.getTemplateEngine()
								.getTemplate("jbang-config.json.qute")
								.data("config", cfg)
								.render();
		return config;
	}

	public static Path setNearestConfigValue(Path cwd, String key, String value) throws IOException {
		Path configFile = getConfigFile(cwd, null);
		setConfigValue(configFile, key, value);
		return configFile;
	}

	public static void setConfigValue(Path configFile, String key, String value) throws IOException {
		Config cfg = readConfig(configFile);
		cfg.put(key, value);
		writeConfig(configFile, cfg);
	}

	public static Path unsetNearestConfigValue(Path cwd, String key) throws IOException {
		Path configFile = getConfigFile(cwd, null);
		unsetConfigValue(configFile, key);
		return configFile;
	}

	public static void unsetConfigValue(Path configFile, String key) throws IOException {
		Config cfg = readConfig(configFile);
		if (cfg.containsKey(key)) {
			cfg.remove(key);
			writeConfig(configFile, cfg);
		}
	}

	/**
	 * Will either return the path to the given config or search for the nearest
	 * config starting from cwd.
	 *
	 * @param cwd    The folder to use as a starting point for getting the nearest
	 *               config
	 * @param config The config to return or null to return the nearest config
	 * @return Path to a config
	 */
	public static Path getConfigFile(Path cwd, Path config) {
		if (config == null) {
			config = findNearestLocalConfig(cwd);
			if (config == null) {
				config = Settings.getUserConfigFile();
			}
		}
		return config;
	}

	private static Path findNearestLocalConfig(Path dir) {
		return Util.findNearestFileWith(dir, Settings.CONFIG_JSON, p -> true);
	}

	private static Path findNearestLocalConfigWithKey(Path dir, String key) {
		return Util.findNearestFileWith(dir, Settings.CONFIG_JSON, configFile -> {
			Config cfg = readConfig(configFile);
			return cfg.containsKey(key);
		});
	}
}
