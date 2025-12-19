package dev.jbang.source.generators;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.devkitman.Jdk;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.Glob;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.Util;

public class JarCmdGenerator extends BaseCmdGenerator<JarCmdGenerator> {
	private List<String> runtimeOptions = Collections.emptyList();
	private boolean assertions;
	private boolean systemAssertions;
	private boolean classDataSharing;
	private String mainClass;
	private boolean mainRequired;
	private String moduleName;

	public JarCmdGenerator runtimeOptions(List<String> runtimeOptions) {
		if (runtimeOptions != null) {
			this.runtimeOptions = runtimeOptions;
		} else {
			this.runtimeOptions = Collections.emptyList();
		}
		return this;
	}

	public JarCmdGenerator assertions(boolean assertions) {
		this.assertions = assertions;
		return this;
	}

	public JarCmdGenerator systemAssertions(boolean systemAssertions) {
		this.systemAssertions = systemAssertions;
		return this;
	}

	public JarCmdGenerator classDataSharing(boolean classDataSharing) {
		this.classDataSharing = classDataSharing;
		return this;
	}

	public JarCmdGenerator mainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public JarCmdGenerator mainRequired(boolean mainRequired) {
		this.mainRequired = mainRequired;
		return this;
	}

	public JarCmdGenerator moduleName(String moduleName) {
		this.moduleName = moduleName;
		return this;
	}

	public JarCmdGenerator(BuildContext ctx) {
		super(ctx);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		Project project = ctx.getProject();
		String classpath = ctx.resolveClassPath().getClassPath();

		List<String> optionalArgs = new ArrayList<>();

		Jdk jdk = project.projectJdk();
		String javacmd = JavaUtil.resolveInJavaHome("java", jdk);

		if (jdk.majorVersion() > 9) {
			String opens = ctx.getProject().getManifestAttributes().get("Add-Opens");
			if (opens != null) {
				for (String val : opens.split(" ")) {
					optionalArgs.add("--add-opens=" + val + "=ALL-UNNAMED");
				}
			}

			String exports = ctx.getProject().getManifestAttributes().get("Add-Exports");
			if (exports != null) {
				for (String val : exports.split(" ")) {
					optionalArgs.add("--add-exports=" + val + "=ALL-UNNAMED");
				}
			}
		}

		addPropertyFlags(project.getProperties(), "-D", optionalArgs);

		if (debugString != null) {
			Map<String, String> fallbackDebug = new LinkedHashMap<>();
			fallbackDebug.put("transport", "dt_socket");
			fallbackDebug.put("server", "y");
			fallbackDebug.put("suspend", "y");
			fallbackDebug.put("address", "4004");
			// needed even though there is a fallbackvalue as user might have set some other
			// key/value
			// i.e. --debug=server=n
			fallbackDebug.putAll(debugString);

			String address = fallbackDebug.get("address");
			if (address != null && address.endsWith("?")) {
				Util.verboseMsg("Checking for available debug port " + address);
				address = address.substring(0, address.length() - 1);
				try {
					// Check if address is just a port number
					int port = Integer.parseInt(address);
					int maxAttempts = 10; // Don't try forever
					int attempts = 0;

					while (attempts < maxAttempts) {
						Util.verboseMsg("Checking for available debug port" + port + " attempts: " + attempts);
						try (ServerSocket socket = new ServerSocket(port)) {
							// Port is available, close socket and use this port
							socket.close();
							break;
						} catch (IOException e) {
							// Port in use, try a random port between 1024-65535
							port = 1024 + (int) (Math.random() * 64511);
							attempts++;
						}
					}

					// Update the address with the (potentially) new port
					fallbackDebug.put("address", String.valueOf(port));
				} catch (NumberFormatException e) {
					Util.verboseMsg("Problem parsing " + address + " as a port number", e); // Not just a number, leave
																							// address as-is
				}
			}

			optionalArgs.add(
					"-agentlib:jdwp=" + fallbackDebug.entrySet()
						.stream()
						.map(e -> e.getKey() + "=" + e.getValue())
						.collect(Collectors.joining(",")));
		}

		if (assertions) {
			optionalArgs.add("-ea");
		}

		if (systemAssertions) {
			optionalArgs.add("-esa");
		}

		if (project.enablePreview()) {
			optionalArgs.add("--enable-preview");
		}

		if (flightRecorderString != null) {
			// TODO: find way to generate ~/.jbang/script.jfc to configure flightrecorder to
			// have 0 ms thresholds
			String jfropt = "-XX:StartFlightRecording=" + flightRecorderString
				.replace("{baseName}",
						Util.getBaseName(
								project.getResourceRef()
									.getFile()
									.toString()));
			optionalArgs.add(jfropt);
			Util.verboseMsg("Flight recording enabled with:" + jfropt);
		}

		if (ctx.getJarFile() != null) {
			if (Util.isBlankString(classpath)) {
				classpath = ctx.getJarFile().toAbsolutePath().toString();
			} else {
				classpath = ctx.getJarFile().toAbsolutePath() + Settings.CP_SEPARATOR + classpath.trim();
			}
		}
		if (!Util.isBlankString(classpath)) {
			if (moduleName != null && project.getModuleName().isPresent()) {
				optionalArgs.addAll(Arrays.asList("-p", classpath));
			} else {
				optionalArgs.addAll(Arrays.asList("-classpath", classpath));
			}
		}

		if (classDataSharing || project.enableCDS()) {
			if (jdk.majorVersion() >= 13) {
				Path cdsJsa = ctx.getJsaFile().toAbsolutePath();
				if (Files.exists(cdsJsa)) {
					Util.verboseMsg("CDS: Using shared archive classes from " + cdsJsa);
					optionalArgs.add("-XX:SharedArchiveFile=" + cdsJsa);
				} else {
					Util.verboseMsg("CDS: Archiving Classes At Exit at " + cdsJsa);
					optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
				}
			} else {
				Util.warnMsg(
						"ClassDataSharing can only be used on Java versions 13 and later, you are on "
								+ jdk.majorVersion() + ". Rerun with `--java 13+` to enforce the minimum version");
			}
		}

		fullArgs.add(javacmd);

		fullArgs.addAll(project.getRuntimeOptions());
		fullArgs.addAll(runtimeOptions);
		fullArgs.addAll(ctx.resolveClassPath().getAutoDectectedModuleArguments(jdk));
		fullArgs.addAll(optionalArgs);

		String main = Optional.ofNullable(mainClass).orElse(project.getMainClass());
		if (main != null && !Glob.isGlob(main)) {
			if (moduleName != null && project.getModuleName().isPresent()) {
				String modName = moduleName.isEmpty() ? ModuleUtil.getModuleName(project) : moduleName;
				fullArgs.add("-m");
				fullArgs.add(modName + "/" + main);
			} else {
				fullArgs.add(main);
			}
		} else if (mainRequired) {
			List<ClassInfo> mains = Collections.emptyList();
			try {
				Indexer indexer = new Indexer();
				Index index;
				// Iterate all .class files in ctx.getJar and put in jandex index
				Path jarPath = ctx.getJarFile();
				if (jarPath != null && Files.exists(jarPath) && Files.isRegularFile(jarPath)) {
					try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
						java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
						while (entries.hasMoreElements()) {
							java.util.jar.JarEntry entry = entries.nextElement();
							if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
								try (InputStream is = jarFile.getInputStream(entry)) {
									indexer.index(is);
								}
							}
						}
					}
				}
				index = indexer.complete();

				Collection<ClassInfo> classes = index.getKnownClasses();

				mains = classes.stream()
					.filter(CompileBuildStep.getMainFinder())
					.collect(Collectors.toList());

			} catch (IOException e) {
				Util.warnMsg("Error indexing jar file: " + e.getMessage());
			}

			if (mains.isEmpty()) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"No main class deduced, specified nor found in a manifest nor jar");
			} else {

				Stream<ClassInfo> filteredMains = mains.stream();

				if (Glob.isGlob(main)) {
					filteredMains = filteredMains
						.filter(m -> Glob.matches(main, m.name().toString()));
				}

				String[] mainClassOptions = filteredMains.map(m -> m.name().toString()).toArray(String[]::new);
				int result = Util.askInput(
						"No main class deduced, specified nor found in a manifest, but found these candidates:", 30, 0,
						mainClassOptions);

				if (result <= 0) {
					String mainClasses = mains.stream()
						.map(m -> "\n - " + m)
						.collect(Collectors.joining());
					throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
							"No main class deduced, specified nor found in a manifest, but found these candidates:\n"
									+ mainClasses + "\n\nUse -m <main class> to specify a main class.");
				} else {
					mainClass = mainClassOptions[result - 1];
					Util.verboseMsg("User chose main:" + mainClass);
					fullArgs.add(mainClass);
				}
			}
		}
		fullArgs.addAll(arguments);

		return fullArgs;
	}

	protected String generateCommandLineString(List<String> fullArgs) throws IOException {
		return CommandBuffer.of(fullArgs)
			.shell(shell)
			.applyWindowsMaxCliLimit()
			.asCommandLine();
	}

	private static void addPropertyFlags(Map<String, String> properties, String def, List<String> result) {
		properties.forEach((k, e) -> result.add(def + k + "=" + e));
	}

}
