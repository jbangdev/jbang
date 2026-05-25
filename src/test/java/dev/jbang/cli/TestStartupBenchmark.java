package dev.jbang.cli;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.registry.CommandRegistryException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

@Tag("benchmark")
class TestStartupBenchmark extends BaseTest {

	private static final int WARMUP_ITERATIONS = 20;
	private static final int MEASURED_ITERATIONS = 100;

	@Test
	void benchmarkParseCommand() {
		Map<String, String[]> scenarios = new LinkedHashMap<>();
		scenarios.put("version (no subcommand args)", new String[] { "version" });
		scenarios.put("run (minimal)", new String[] { "run", "dummy.java" });
		scenarios.put("run (with options)", new String[] { "run", "--java", "21", "--debug", "5005",
				"--ea", "--verbose", "dummy.java" });
		scenarios.put("run (with dependencies)", new String[] { "run", "--deps", "com.google.code.gson:gson:2.10",
				"--repos", "mavencentral", "-Dfoo=bar", "-Dbaz=qux", "dummy.java" });
		scenarios.put("build (with compile options)", new String[] { "build", "--java", "17",
				"-C", "-Xlint:all", "--module", "mymod", "dummy.java" });
		scenarios.put("alias list", new String[] { "alias", "list" });
		scenarios.put("init (with options)", new String[] { "init", "--template", "cli",
				"--java", "21", "--deps", "info.picocli:picocli:4.7.7", "dummy.java" });

		System.out.println();
		System.out.println("=".repeat(80));
		System.out.println("  CLI Startup Benchmark: parseCommand() timing");
		System.out.println("  (measures CLI framework overhead: registry build + arg parsing)");
		System.out.println("  Warmup: " + WARMUP_ITERATIONS + " iterations, Measured: " + MEASURED_ITERATIONS);
		System.out.println("=".repeat(80));

		for (Map.Entry<String, String[]> scenario : scenarios.entrySet()) {
			benchmarkScenario(scenario.getKey(), scenario.getValue());
		}

		System.out.println("=".repeat(80));
	}

	@Test
	void benchmarkExecuteVersion() {
		System.out.println();
		System.out.println("=".repeat(80));
		System.out.println("  CLI Startup Benchmark: full execute() for 'jbang version'");
		System.out.println("  (measures end-to-end: registry + parse + command execution)");
		System.out.println("  Warmup: " + WARMUP_ITERATIONS + " iterations, Measured: " + MEASURED_ITERATIONS);
		System.out.println("=".repeat(80));

		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			JBang.execute("version");
		}

		long[] times = new long[MEASURED_ITERATIONS];
		for (int i = 0; i < MEASURED_ITERATIONS; i++) {
			long start = System.nanoTime();
			JBang.execute("version");
			times[i] = System.nanoTime() - start;
		}

		printStats("version (full execute)", times);
		System.out.println("=".repeat(80));
	}

	@Test
	void benchmarkRegistryBuildOnly() throws CommandRegistryException {
		System.out.println();
		System.out.println("=".repeat(80));
		System.out.println("  CLI Startup Benchmark: CommandRegistry build only");
		System.out.println("  (measures cost of reflecting over all commands + options)");
		System.out.println("  Warmup: " + WARMUP_ITERATIONS + " iterations, Measured: " + MEASURED_ITERATIONS);
		System.out.println("=".repeat(80));

		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			AeshCommandRegistryBuilder.builder()
				.command(JBang.class)
				.create();
		}

		long[] times = new long[MEASURED_ITERATIONS];
		for (int i = 0; i < MEASURED_ITERATIONS; i++) {
			long start = System.nanoTime();
			AeshCommandRegistryBuilder.builder()
				.command(JBang.class)
				.create();
			times[i] = System.nanoTime() - start;
		}

		printStats("registry build", times);
		System.out.println("=".repeat(80));
	}

	private void benchmarkScenario(String label, String[] args) {
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			try {
				JBang.parseCommand(args);
			} catch (Exception e) {
				// Some commands may fail validation, we only care about parse timing
			}
		}

		long[] times = new long[MEASURED_ITERATIONS];
		for (int i = 0; i < MEASURED_ITERATIONS; i++) {
			long start = System.nanoTime();
			try {
				JBang.parseCommand(args);
			} catch (Exception e) {
				// ignore
			}
			times[i] = System.nanoTime() - start;
		}

		printStats(label, times);
	}

	private void printStats(String label, long[] timesNanos) {
		Arrays.sort(timesNanos);
		long min = timesNanos[0];
		long max = timesNanos[timesNanos.length - 1];
		long median = timesNanos[timesNanos.length / 2];
		long p95 = timesNanos[(int) (timesNanos.length * 0.95)];
		long sum = 0;
		for (long t : timesNanos) {
			sum += t;
		}
		double avg = (double) sum / timesNanos.length;

		System.out.printf("  %-40s  min=%6.2fms  avg=%6.2fms  median=%6.2fms  p95=%6.2fms  max=%6.2fms%n",
				label, ns2ms(min), ns2ms(avg), ns2ms(median), ns2ms(p95), ns2ms(max));
	}

	private double ns2ms(double nanos) {
		return nanos / 1_000_000.0;
	}

	private double ns2ms(long nanos) {
		return nanos / 1_000_000.0;
	}
}
