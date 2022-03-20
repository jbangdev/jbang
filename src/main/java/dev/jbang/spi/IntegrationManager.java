package dev.jbang.spi;

import static dev.jbang.cli.BaseCommand.EXIT_GENERIC_ERROR;
import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.Source;
import dev.jbang.source.SourceSet;
import dev.jbang.util.Util;

/**
 * JBang uses a 'convention based interface' for build time integration.
 */
public class IntegrationManager {
	public static final String FILES = "files";
	public static final String NATIVE_IMAGE = "native-image";
	public static final String MAIN_CLASS = "main-class";
	public static final String JAVA_ARGS = "java-args";

	private static final GsonBuilder gsonb = new GsonBuilder().registerTypeHierarchyAdapter(Path.class,
			new PathTypeAdapter());

	/**
	 * Discovers all integration points and runs them.
	 * <p>
	 * If an integration point created a native image it returns the resulting
	 * image.
	 */
	public static IntegrationResult runIntegrations(SourceSet ss, Path tmpJarDir, Path pomPath,
			boolean nativeRequested, String requestedJavaVersion) {
		IntegrationResult result = new IntegrationResult(null, null, null);
		Source source = ss.getMainSource();

		LinkedHashMap<String, String> repos = new LinkedHashMap<>();
		LinkedHashMap<String, Path> deps = new LinkedHashMap<>();
		for (MavenRepo repo : ss.getRepositories()) {
			repos.put(repo.getId(), repo.getUrl());
		}
		for (ArtifactInfo art : ss.getClassPath().getArtifacts()) {
			deps.put(art.getCoordinate().toCanonicalForm(), art.getFile().toPath());
		}

		List<String> comments = source.getTags().collect(Collectors.toList());
		ClassLoader old = Thread.currentThread().getContextClassLoader();
		PrintStream oldout = System.out;
		try {
			URLClassLoader integrationCl = getClassLoader(deps);
			Thread.currentThread().setContextClassLoader(integrationCl);
			Set<String> classNames = loadIntegrationClassNames(integrationCl);
			for (String className : classNames) {
				Path srcPath = (source.getResourceRef().getFile() != null)
						? source.getResourceRef().getFile().toPath().toAbsolutePath()
						: null;
				IntegrationInput input = new IntegrationInput(className, srcPath, tmpJarDir, pomPath, repos, deps,
						comments,
						nativeRequested);
				IntegrationResult ir = requestedJavaVersion == null
						? runIntegrationEmbedded(input, integrationCl)
						: runIntegrationExternal(input, requestedJavaVersion);
				result = result.merged(ir);
			}
		} catch (ClassNotFoundException e) {
			throw new ExitException(EXIT_INVALID_INPUT, "Unable to load integration class", e);
		} catch (NoSuchMethodException e) {
			throw new ExitException(
					EXIT_INVALID_INPUT,
					"Integration class missing method with signature public static Map<String, byte[]> postBuild(Path classesDir, Path pomFile, List<Map.Entry<String, Path>> dependencies)",
					e);
		} catch (Exception e) {
			throw new ExitException(EXIT_GENERIC_ERROR, "Issue running postBuild()", e);
		} finally {
			Thread.currentThread().setContextClassLoader(old);
			System.setOut(oldout);
		}
		return result;
	}

	@Nonnull
	private static URLClassLoader getClassLoader(Map<String, Path> deps) {
		URL[] urls = deps.values().stream().map(path -> {
			try {
				return path.toUri().toURL();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}).toArray(URL[]::new);
		return new URLClassLoader(urls);
	}

	private static Set<String> loadIntegrationClassNames(URLClassLoader integrationCl) throws IOException {
		Set<String> classNames = new HashSet<>();
		Enumeration<URL> files = integrationCl.getResources("META-INF/jbang-integration.list");
		while (files.hasMoreElements()) {
			URL res = files.nextElement();
			try (InputStream in = res.openStream()) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (!line.startsWith("#")) {
						if (line.contains("#")) {
							line = line.substring(0, line.indexOf("#"));
							line = line.trim();
						}
						if (!line.isEmpty()) {
							classNames.add(line);
						}
					}

				}
			}
		}
		return classNames;
	}

	private static IntegrationResult runIntegrationEmbedded(IntegrationInput input, URLClassLoader integrationCl)
			throws Exception {
		Util.infoMsg("Post build with " + input.integrationClassName);

		if (input.source != null) {
			// TODO: should we add new properties to the integration method?
			System.setProperty("jbang.source", input.source.toString());
		}

		Class<?> clazz = Class.forName(input.integrationClassName, true, integrationCl);
		Method method = clazz.getDeclaredMethod("postBuild", Path.class, Path.class, List.class, List.class,
				List.class, boolean.class);

		if (Util.isVerbose()) {
			System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err)));
		} else {
			System.setOut(new PrintStream(new OutputStream() {
				public void write(int b) {
					// DO NOTHING
					// TODO: capture it for later print if error
				}
			}));
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(null, input.classes, input.pom,
				mapToList(input.repositories), mapToList(input.dependencies), input.comments, input.nativeRequested);

		@SuppressWarnings("unchecked")
		Map<String, byte[]> files = (Map<String, byte[]>) result.get(FILES);
		if (files != null) {
			for (Map.Entry<String, byte[]> entry : files.entrySet()) {
				Path target = input.classes.resolve(entry.getKey());
				Files.createDirectories(target.getParent());
				try (OutputStream out = Files.newOutputStream(target)) {
					out.write(entry.getValue());
				}
			}
		}

		Path nativeImage = null;
		String mainClass = null;
		List<String> javaArgs = null;
		Path image = (Path) result.get(NATIVE_IMAGE);
		if (image != null) {
			nativeImage = image;
		}
		String mc = (String) result.get(MAIN_CLASS);
		if (mc != null) {
			mainClass = mc;
		}
		@SuppressWarnings("unchecked")
		List<String> ja = (List<String>) result.get(JAVA_ARGS);
		if (ja != null) {
			javaArgs = ja;
		}

		return new IntegrationResult(nativeImage, mainClass, javaArgs);
	}

	private static IntegrationResult runIntegrationExternal(IntegrationInput input, String requestedJavaVersion)
			throws Exception {
		Gson parser = gsonb.create();
		Util.infoMsg("Running external post build for " + input.integrationClassName);

		List<String> args = new ArrayList<>();
		args.add(resolveInJavaHome("java", requestedJavaVersion)); // TODO
		args.add("-cp");
		args.add(Util.getJarLocation().toString());
		args.add("dev.jbang.spi.IntegrationManager");

		if (Util.isVerbose()) {
			Util.verboseMsg("Running: " + String.join(" ", args));
			Util.verboseMsg("Input: " + parser.toJson(input));
		}

		Process process = new ProcessBuilder(args)
													.redirectError(ProcessBuilder.Redirect.INHERIT)
													.start();

		try (Writer w = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
			parser.toJson(input, w);
		}

		String cmdOutput;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			cmdOutput = br.lines().collect(Collectors.joining());
		}

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(EXIT_GENERIC_ERROR, "External post build was interrupted", e);
		}

		Util.verboseMsg("Output: #" + process.exitValue() + " - " + cmdOutput);

		if (process.exitValue() == 0) {
			return parser.fromJson(new StringReader(cmdOutput), IntegrationResult.class);
		} else {
			throw new ExitException(EXIT_GENERIC_ERROR, "External post exited with error: " + cmdOutput);
		}
	}

	private static <K, V> List<Map.Entry<K, V>> mapToList(Map<K, V> map) {
		return new ArrayList<>(map.entrySet());
	}

	private static class PathTypeAdapter extends TypeAdapter<Path> {
		@Override
		public Path read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			String path = in.nextString();
			return Paths.get(path);
		}

		@Override
		public void write(JsonWriter out, Path path) throws IOException {
			if (path == null) {
				out.nullValue();
				return;
			}
			out.value(path.toString());
		}
	}

	public static void main(String... args) {
		Gson parser = gsonb.create();
		IntegrationInput input = parser.fromJson(new InputStreamReader(System.in), IntegrationInput.class);
		ClassLoader old = Thread.currentThread().getContextClassLoader();
		PrintStream oldout = System.out;
		String output = "";
		boolean ok = false;
		try {
			URLClassLoader integrationCl = getClassLoader(input.dependencies);
			Thread.currentThread().setContextClassLoader(integrationCl);
			IntegrationResult result = runIntegrationEmbedded(input, integrationCl);
			output = parser.toJson(result);
			ok = true;
		} catch (ClassNotFoundException e) {
			output = "Unable to load integration class";
		} catch (NoSuchMethodException e) {
			output = "Integration class missing method with signature public static Map<String, byte[]> postBuild(Path classesDir, Path pomFile, List<Map.Entry<String, Path>> dependencies)";
		} catch (Exception e) {
			output = "Issue running postBuild()";
		} finally {
			Thread.currentThread().setContextClassLoader(old);
			System.setOut(oldout);
		}
		System.out.println(output);
		System.exit(ok ? 0 : 1);
	}
}
