package dev.jbang.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

public class RunMixin {

	@OptionList(shortName = 'R', name = "runtime-option", aliases = {
			"java-options" }, description = "Options to pass to the Java runtime")
	public List<String> javaRuntimeOptions;

	@Option(name = "jfr", fallbackValue = "", description = "Launch with Java Flight Recorder enabled.")
	public String flightRecorderString;

	@Option(shortName = 'd', name = "debug", parser = DebugOptionParser.class, converter = DebugConverter.class, fallbackValue = "4004", description = "Launch with java debug enabled. Set host/port or provide key/value list of JPDA options (default: ${DEFAULT-VALUE})")
	public Map<String, String> debugString;

	@Option(name = "enableassertions", aliases = { "ea" }, hasValue = false, description = "Enable assertions")
	public Boolean enableAssertions;

	@Option(name = "enablesystemassertions", aliases = {
			"esa" }, hasValue = false, description = "Enable system assertions")
	public Boolean enableSystemAssertions;

	@OptionList(name = "javaagent")
	List<String> javaAgentRaw;

	private Map<String, String> javaAgentSlots;

	@Option(name = "cds", hasValue = false, negatable = true, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)")
	Boolean cds;

	@Option(shortName = 'i', name = "interactive", hasValue = false, description = "Activate interactive mode")
	public Boolean interactive;

	/**
	 * Returns the --javaagent options as a structured map, converting lazily from
	 * the raw {@code @OptionList} strings. Uses {@code LinkedHashMap} to preserve
	 * insertion order (agent ordering matters). Aesh's {@code @OptionGroup} can't
	 * be used here because it uses {@code HashMap} internally, losing order (see
	 * <a href="https://github.com/aeshell/aesh/issues/513">aesh#513</a>).
	 */
	public Map<String, String> getJavaAgentSlots() {
		if (javaAgentSlots == null && javaAgentRaw != null && !javaAgentRaw.isEmpty()) {
			javaAgentSlots = new LinkedHashMap<>();
			for (String raw : javaAgentRaw) {
				int eq = raw.indexOf('=');
				if (eq >= 0) {
					javaAgentSlots.put(raw.substring(0, eq), raw.substring(eq + 1));
				} else {
					javaAgentSlots.put(raw, null);
				}
			}
		}
		return javaAgentSlots;
	}

	public Boolean getCds() {
		return cds;
	}

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (javaRuntimeOptions != null) {
			for (String r : javaRuntimeOptions) {
				opts.add("-R");
				opts.add(r);
			}
		}
		if (flightRecorderString != null) {
			opts.add(flightRecorderString.isEmpty() ? "--jfr" : "--jfr=" + flightRecorderString);
		}
		if (debugString != null) {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, String> e : debugString.entrySet()) {
				if (sb.length() > 0) {
					sb.append(",");
				}
				sb.append(e.getKey()).append("=").append(e.getValue());
			}
			opts.add("--debug=" + sb);
		}
		if (Boolean.TRUE.equals(enableAssertions)) {
			opts.add("--enableassertions");
		}
		if (Boolean.TRUE.equals(enableSystemAssertions)) {
			opts.add("--enablesystemassertions");
		}
		Map<String, String> agentSlots = getJavaAgentSlots();
		if (agentSlots != null) {
			for (Map.Entry<String, String> e : agentSlots.entrySet()) {
				opts.add("--javaagent");
				opts.add(e.getValue() == null || e.getValue().isEmpty()
						? e.getKey()
						: e.getKey() + "=" + e.getValue());
			}
		}
		if (Boolean.TRUE.equals(getCds())) {
			opts.add("--cds");
		} else if (Boolean.FALSE.equals(getCds())) {
			opts.add("--no-cds");
		}
		if (Boolean.TRUE.equals(interactive)) {
			opts.add("--interactive");
		}
		return opts;
	}
}
