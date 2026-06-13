package dev.jbang.cli.completion;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import dev.jbang.Configuration;
import dev.jbang.cli.AvailableOption;
import dev.jbang.cli.Config;

/**
 * Provides tab-completion for the {@code key} argument of {@code jbang config}
 * subcommands ({@code get}, {@code set}).
 * <p>
 * Completes from two sources:
 * <ul>
 * <li>Available option keys gathered from the CLI command tree (same as
 * {@code config list --show-available})</li>
 * <li>User-set keys from the merged configuration that don't match a known
 * option name</li>
 * </ul>
 * Candidates include tab-separated descriptions for fish/zsh display.
 */
public class ConfigKeyCompleter implements OptionCompleter<CompleterInvocation> {

	private static String described(String candidate, String description) {
		return candidate + "\t" + description;
	}

	@Override
	public void complete(CompleterInvocation inv) {
		String partial = inv.getGivenCompleteValue();
		if (partial == null) {
			partial = "";
		}

		Set<String> candidates = new TreeSet<>();
		Map<String, String> availableKeys = getAvailableKeys();

		for (Map.Entry<String, String> entry : availableKeys.entrySet()) {
			String key = entry.getKey();
			String desc = entry.getValue();
			if (key.startsWith(partial)) {
				candidates.add(described(key, desc));
			}
		}

		// Also complete keys from the merged configuration (user-set values
		// that might not match known option names)
		try {
			Configuration cfg = Configuration.getMerged();
			for (String key : cfg.flatten().keySet()) {
				if (key.startsWith(partial)) {
					// Only add if not already present from available keys
					boolean alreadyPresent = availableKeys.containsKey(key);
					if (!alreadyPresent) {
						String val = cfg.get(key);
						candidates.add(described(key, val != null ? "= " + val : ""));
					}
				}
			}
		} catch (Exception e) {
			// best-effort
		}

		inv.addAllCompleterValues(candidates);

		// When there is exactly one candidate, strip the description so the
		// value is clean (aesh escapes spaces in single-candidate mode).
		if (candidates.size() == 1) {
			String single = candidates.iterator().next();
			int tab = single.indexOf('\t');
			if (tab >= 0) {
				inv.clearCompleterValues();
				inv.addCompleterValue(single.substring(0, tab));
			}
		}
	}

	/**
	 * Gathers all known configuration keys with descriptions, using the same source
	 * as {@code config list --show-available}.
	 *
	 * @return map of key → description
	 */
	static Map<String, String> getAvailableKeys() {
		Map<String, String> keys = new TreeMap<>();
		for (AvailableOption opt : Config.getAvailableOptions()) {
			keys.put(opt.key, opt.description);
		}
		return keys;
	}
}
