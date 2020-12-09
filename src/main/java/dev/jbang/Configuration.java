package dev.jbang;

import static dev.jbang.util.Util.verboseMsg;
import static dev.jbang.util.Util.warnMsg;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.source.ResourceRef;
import dev.jbang.util.Util;

public class Configuration extends TreeMap<String, Object> {
	public transient String configPath;

	public static final String JBANG_CONFIG_JSON = "jbang-config.json";

	private static Configuration defaults;
	private static Configuration global;

	private Configuration() {
		super(String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * Returns a new empty Configuration
	 *
	 * @return a Configuration object
	 */
	public static Configuration create() {
		return new Configuration();
	}

	/**
	 * Returns a new Configuration initialized with the values from the given
	 * Configuration
	 *
	 * @return a Configuration object
	 */
	public static Configuration create(Configuration cfg) {
		Configuration result = new Configuration();
		result.putAll(cfg);
		return result;
	}

	/**
	 * Returns a cached Configuration read from the any config files that were found
	 * in the current environment
	 *
	 * @return a Configuration object
	 */
	public static Configuration instance() {
		if (global == null) {
			global = Configuration.getMerged();
		}
		return global;
	}

	/**
	 * Sets the globally cached Configuration object
	 * 
	 * @param cfg a Configuration object
	 */
	public static void instance(Configuration cfg) {
		global = cfg;
	}

	/**
	 * Returns the default Configuration
	 *
	 * @return a Configuration object
	 */
	public static Configuration defaults() {
		if (defaults == null) {
			defaults = Configuration.getBuiltin();
		}
		return defaults;
	}

	/**
	 * Returns a Config containing all the settings from local config files merged
	 * into one. This follows the system where settings that are "nearest" have
	 * priority. The Config starts out with all the values from `defaults`, any
	 * values read from files have priority.
	 *
	 * @return a Configuration object
	 */
	public static Configuration getMerged() {
		List<Configuration> configs = new ArrayList<>();
		findNearestConfigWith(Util.getCwd(), cfg -> {
			configs.add(0, cfg);
			return false;
		});

		Configuration result = create();
		for (Configuration cfg : configs) {
			merge(cfg, result);
		}

		return result;
	}

	static Configuration findNearestConfigWith(Path dir, Function<Configuration, Boolean> accept) {
		Path cfgFile = Util.findNearestFileWith(dir, JBANG_CONFIG_JSON, cfg -> accept.apply(read(cfg)));
		if (cfgFile != null) {
			return read(cfgFile);
		} else if (accept.apply(defaults())) {
			return defaults();
		}
		return Configuration.create();
	}

	// This returns the built-in Configuration that can be found in the resources
	public static Configuration getBuiltin() {
		String res = "classpath:/" + JBANG_CONFIG_JSON;
		ResourceRef cfgRef = ResourceRef.forResource(res);
		if (cfgRef != null) {
			Path catPath = cfgRef.getFile().toPath();
			Configuration cfg = read(catPath);
			cfg.configPath = res;
			return cfg;
		} else {
			return null;
		}
	}

	private static <R> void merge(Configuration cfg, Configuration result) {
		result.putAll(cfg);
	}

	public static Configuration read(Path configFile) {
		Configuration cfg = new Configuration();
		if (Files.isRegularFile(configFile)) {
			try (Reader in = Files.newBufferedReader(configFile)) {
				Gson parser = new Gson();
				Configuration tmp = parser.fromJson(in, Configuration.class);
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

	static void write(Path configFile, Configuration cfg) throws IOException {
		verboseMsg(String.format("Reading configuration from %s", configFile));
		try (Writer out = Files.newBufferedWriter(configFile)) {
			Gson parser = new GsonBuilder().setPrettyPrinting().create();
			parser.toJson(cfg, out);
		}
	}

	public static Path setNearestConfigValue(String key, String value) throws IOException {
		Path configFile = getConfigFile(null);
		setConfigValue(configFile, key, value);
		return configFile;
	}

	public static void setConfigValue(Path configFile, String key, String value) throws IOException {
		Configuration cfg = read(configFile);
		cfg.configPath = configFile.toString();
		cfg.put(key, value);
		write(configFile, cfg);
	}

	public static Path unsetNearestConfigValue(String key) throws IOException {
		Path configFile = findNearestLocalConfigWithKey(null, key);
		unsetConfigValue(configFile, key);
		return configFile;
	}

	public static void unsetConfigValue(Path configFile, String key) throws IOException {
		Configuration cfg = read(configFile);
		cfg.configPath = configFile.toString();
		if (cfg.containsKey(key)) {
			cfg.remove(key);
			write(configFile, cfg);
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
		return Util.findNearestFileWith(null, JBANG_CONFIG_JSON, p -> true);
	}

	private static Path findNearestLocalConfigWithKey(Path dir, String key) {
		return Util.findNearestFileWith(dir, JBANG_CONFIG_JSON, configFile -> {
			Configuration cfg = read(configFile);
			return cfg.containsKey(key);
		});
	}
}
