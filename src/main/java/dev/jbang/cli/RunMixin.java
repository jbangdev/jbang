package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
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

	@OptionGroup(name = "javaagent", defaultValue = "", description = "Java agent to use")
	public Map<String, String> javaAgentSlots;

	@Option(name = "cds", hasValue = false, negatable = true, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)")
	Boolean cds;

	@Option(shortName = 'i', name = "interactive", hasValue = false, description = "Activate interactive mode")
	public Boolean interactive;

	public Map<String, String> getJavaAgentSlots() {
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
