package dev.jbang.cli;

import java.util.List;
import java.util.Map;

import picocli.CommandLine;

public class RunMixin {

	@CommandLine.Option(names = { "-R", "--runtime-option",
			"--java-options" }, description = "Options to pass to the Java runtime")
	public List<String> javaRuntimeOptions;

	@CommandLine.Option(names = {
			"--jfr" }, fallbackValue = "${jbang.run.jfr}", parameterConsumer = Run.KeyValueFallbackConsumer.class, arity = "0..1", description = "Launch with Java Flight Recorder enabled.")
	public String flightRecorderString;

	@CommandLine.Option(names = { "-d",
			"--debug" }, fallbackValue = "${jbang.run.debug}", parameterConsumer = Run.DebugFallbackConsumer.class, arity = "0..1", description = "Launch with java debug enabled on specified port (default: ${FALLBACK-VALUE}) ")
	public String debugString;

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

}
