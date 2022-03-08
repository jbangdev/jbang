package dev.jbang;

import static dev.jbang.util.Util.verboseMsg;
import static dev.jbang.util.Util.warnMsg;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.codejive.properties.Properties;

import dev.jbang.source.ResourceRef;
import dev.jbang.util.Util;

public class Configuration {
	protected final Properties values = new Properties();

	private Configuration fallback;
	private ResourceRef storeRef;

	public static final String JBANG_CONFIG_PROPS = "jbang.properties";

	private static Configuration defaults;
	private static Configuration global;

	private Configuration() {
		this(null);
	}

	private Configuration(Configuration fallback) {
		this.fallback = fallback;
	}

	public Configuration getFallback() {
		return fallback;
	}

	public ResourceRef getStoreRef() {
		return storeRef;
	}

	/**
	 * Determines if the given key can be found in this Configuration or its
	 * fallback
	 * 
	 * @param key The key to find
	 * @return Boolean indicating if the key exists or not
	 */
	public boolean containsKey(String key) {
		boolean contains = values.containsKey(key);
		if (!contains && fallback != null) {
			contains = fallback.containsKey(key);
		}
		return contains;
	}

	/**
	 * Returns the given value from this Configuration or its fallback
	 * 
	 * @param key The key of the value to return
	 * @return The associated value if it was found or `null` if not
	 */
	public String get(String key) {
		String result;
		if (values.containsKey(key)) {
			result = values.get(key);
		} else if (fallback != null) {
			result = fallback.get(key);
		} else {
			result = null;
		}
		return result;
	}

	/**
	 * Returns the given value from this Configuration or its fallback
	 * 
	 * @param key          The key of the value to return
	 * @param defaultValue The value to return if the key wasn't found
	 * @return The associated value if it was found or `defaultValue` if not
	 */
	public String get(String key, String defaultValue) {
		return Objects.toString(get(key), defaultValue);
	}

	/**
	 * Sets the given key to the given value in this Configuration. Passing `null`
	 * as a value is the same as calling `remove()`.
	 * 
	 * @param key   The key of the value to set
	 * @param value The new value for the given key
	 * @return The old value for the given key or `null`
	 */
	public String put(String key, String value) {
		if (value != null) {
			return values.put(key, value);
		} else {
			return remove(key);
		}
	}

	/**
	 * Removes the given value from this Configuration or its fallback
	 *
	 * @param key The key of the value to remove
	 * @return The removed value if it was found or `null` if not
	 */
	public String remove(String key) {
		if (values.containsKey(key)) {
			return values.remove(key);
		} else {
			return fallback.remove(key);
		}
	}

	/**
	 * Returns the keys for this Configuration. NB: This will NOT return the keys
	 * for the fallback! Use `flatten()` first if you want all keys()
	 * 
	 * @return Set of keys
	 */
	public Set<String> keySet() {
		return values.keySet().stream().map(Objects::toString).collect(Collectors.toSet());
	}

	/**
	 * Returns a Configuration that is the union of the keys of this Configuration
	 * and those of its flattened fallback.
	 * 
	 * @return a Configuration object
	 */
	public Configuration flatten() {
		if (fallback != null) {
			Configuration cfg = create();
			return flatten(cfg);
		} else {
			return this;
		}
	}

	protected Configuration flatten(Configuration target) {
		if (fallback != null) {
			fallback.flatten(target);
		}
		target.values.putAll(values);
		return target;
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
	 * Returns a new Configuration that will look up any keys that it can't find
	 * among its own values in the given fallback Configuration
	 * 
	 * @param fallback The fallback configuration
	 * @return a Configuration object
	 */
	public static Configuration create(Configuration fallback) {
		return new Configuration(fallback);
	}

	/**
	 * Returns a cached Configuration read from the any config files that were found
	 * in the current environment. The `JBANG_CONFIG` environment variable can be
	 * used to override the normal lookup process. If in that case the file can't be
	 * found only the default values will be returned.
	 * 
	 * @return a Configuration object
	 */
	public static Configuration instance() {
		if (global == null) {
			String cfgFileName = System.getenv("JBANG_CONFIG");
			if (cfgFileName != null) {
				Path cfgFile = Util.getCwd().resolve(cfgFileName);
				if (Files.isReadable(cfgFile)) {
					global = get(cfgFile);
				} else {
					global = defaults();
				}
			} else {
				global = Configuration.getMerged();
			}
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

	public static Configuration get(Path catalogPath) {
		return get(ResourceRef.forNamedFile(catalogPath.toString(), catalogPath.toFile()));
	}

	private static Configuration get(ResourceRef ref) {
		Path configPath = ref.getFile().toPath();
		Configuration cfg = read(configPath);
		cfg.storeRef = ref;
		return cfg;
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
		Set<Path> configFiles = new LinkedHashSet<>();
		Util.findNearestFileWith(null, JBANG_CONFIG_PROPS, cfgFile -> {
			configFiles.add(cfgFile);
			return false;
		});

		ArrayList<Path> files = new ArrayList<>(configFiles);
		Collections.reverse(files);
		Configuration result = defaults();
		for (Path cfgFile : configFiles) {
			Configuration cfg = read(cfgFile);
			cfg.storeRef = ResourceRef.forFile(cfgFile.toFile());
			cfg.fallback = result;
			result = cfg;
		}

		return result;
	}

	// This returns the built-in Configuration that can be found in the resources
	public static Configuration getBuiltin() {
		String res = "classpath:/" + JBANG_CONFIG_PROPS;
		ResourceRef cfgRef = ResourceRef.forResource(res);
		if (cfgRef != null) {
			Path catPath = cfgRef.getFile().toPath();
			Configuration cfg = read(catPath);
			cfg.storeRef = cfgRef;
			return cfg;
		} else {
			return null;
		}
	}

	public static Configuration read(Path configFile) {
		Configuration cfg = new Configuration();
		if (Files.isRegularFile(configFile)) {
			try (Reader in = Files.newBufferedReader(configFile)) {
				Properties props = new Properties();
				props.load(in);
				cfg.values.putAll(props);
			} catch (IOException e) {
				warnMsg("Couldn't parse configuration: " + configFile);
			}
		}
		return cfg;
	}

	public static void write(Path configFile, Configuration cfg) throws IOException {
		verboseMsg(String.format("Writing configuration to %s", configFile));
		try (Writer out = Files.newBufferedWriter(configFile)) {
			cfg.values.store(out);
		}
	}

}
