package dev.jbang.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import dev.jbang.Configuration;

public class RunMixin {

	@OptionList(shortName = 'R', name = "runtime-option", aliases = {
			"java-options" }, description = "Options to pass to the Java runtime")
	public List<String> javaRuntimeOptions;

	@Option(name = "jfr", parser = StrictOptionParser.class, description = "Launch with Java Flight Recorder enabled.")
	public String flightRecorderString;

	@Option(shortName = 'd', name = "debug", parser = DebugOptionParser.class, description = "Launch with java debug enabled.")
	public String debugRaw;

	public Map<String, String> debugString;

	@Option(name = "enableassertions", aliases = { "ea" }, hasValue = false, description = "Enable assertions")
	public Boolean enableAssertions;

	@Option(name = "enablesystemassertions", aliases = {
			"esa" }, hasValue = false, description = "Enable system assertions")
	public Boolean enableSystemAssertions;

	@OptionList(name = "javaagent")
	List<String> javaAgentRaw;

	public Map<String, String> javaAgentSlots;

	@Option(name = "cds", hasValue = false, negatable = true, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)")
	Boolean cds;

	@Option(shortName = 'i', name = "interactive", hasValue = false, description = "Activate interactive mode")
	public Boolean interactive;

	public void resolveAfterParse() {
		Configuration cfg = Configuration.instance();
		if ("".equals(debugRaw)) {
			String cfgDebug = cfg.get("run.debug");
			debugRaw = cfgDebug != null ? cfgDebug : "4004";
		}
		if ("".equals(flightRecorderString)) {
			String cfgJfr = cfg.get("run.jfr");
			flightRecorderString = cfgJfr != null ? cfgJfr : "";
		}
		resolveDebugArgs();
		resolveJavaAgentSlots();
	}

	public void resolveDebugArgs() {
		if (debugRaw != null && !debugRaw.isEmpty()) {
			debugString = new LinkedHashMap<>();
			for (String part : debugRaw.split(",")) {
				if (part.contains("=")) {
					String[] kv = part.split("=", 2);
					debugString.put(kv[0], kv[1]);
				} else {
					debugString.put("address", part);
				}
			}
		}
	}

	void resolveJavaAgentSlots() {
		if (javaAgentRaw != null && !javaAgentRaw.isEmpty()) {
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
			opts.add("--jfr");
			opts.add(flightRecorderString);
		}
		if (debugString != null) {
			for (Map.Entry<String, String> e : debugString.entrySet()) {
				opts.add("-d");
				opts.add(e.getKey() + "=" + e.getValue());
			}
		}
		if (Boolean.TRUE.equals(enableAssertions)) {
			opts.add("--enableassertions");
		}
		if (Boolean.TRUE.equals(enableSystemAssertions)) {
			opts.add("--enablesystemassertions");
		}
		if (javaAgentSlots != null) {
			for (Map.Entry<String, String> e : javaAgentSlots.entrySet()) {
				opts.add("--javaagent");
				opts.add(e.getKey() + "=" + e.getValue());
			}
		}
		if (Boolean.TRUE.equals(getCds())) {
			opts.add("--cds");
		}
		if (Boolean.TRUE.equals(interactive)) {
			opts.add("--interactive");
		}
		return opts;
	}
}
