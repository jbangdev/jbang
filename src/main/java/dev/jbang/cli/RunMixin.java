package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import picocli.CommandLine;

public class RunMixin {

	@CommandLine.Option(names = { "-R", "--runtime-option",
			"--java-options" }, description = "Options to pass to the Java runtime")
	public List<String> javaRuntimeOptions;

	@CommandLine.Option(names = {
			"--jfr" }, fallbackValue = "${default.run.jfr}", parameterConsumer = Run.KeyValueFallbackConsumer.class, arity = "0..1", description = "Launch with Java Flight Recorder enabled.")
	public String flightRecorderString;

	@CommandLine.Option(names = { "-d",
			"--debug" }, fallbackValue = "${default.run.debug}", parameterConsumer = Run.DebugFallbackConsumer.class, arity = "0..1", description = "Launch with java debug enabled. Set host/port or provide key/value list of JPDA options (default: ${FALLBACK-VALUE}) ")
	public Map<String, String> debugString;

	// should take arguments for package/classes when picocli fixes its flag
	// handling bug in release 4.6.
	// https://docs.oracle.com/cd/E19683-01/806-7930/assert-4/index.html
	@CommandLine.Option(names = { "--enableassertions", "--ea" }, description = "Enable assertions")
	public Boolean enableAssertions;

	@CommandLine.Option(names = { "--enablesystemassertions", "--esa" }, description = "Enable system assertions")
	public Boolean enableSystemAssertions;

	@CommandLine.Option(names = { "--javaagent" }, parameterConsumer = KeyValueConsumer.class)
	public Map<String, String> javaAgentSlots;

	@CommandLine.Option(names = {
			"--cds" }, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)", negatable = true)
	Boolean cds;

	@CommandLine.Option(names = { "-i", "--interactive" }, description = "Activate interactive mode")
	public Boolean interactive;

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
			// TODO: this is not handling case of special characters in the values
			// i.e. --debug=address=5000? or --debug=address=*:3333
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
		if (Boolean.TRUE.equals(cds)) {
			opts.add("--cds");
		}
		if (Boolean.TRUE.equals(interactive)) {
			opts.add("--interactive");
		}
		return opts;
	}
}
