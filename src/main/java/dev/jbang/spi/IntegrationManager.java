package dev.jbang.spi;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.ScriptSource;
import dev.jbang.util.Util;

/**
 * JBang uses a 'convention based interface' for build time integration.
 */
public class IntegrationManager {

	public static final String FILES = "files";
	public static final String NATIVE_IMAGE = "native-image";
	public static final String MAIN_CLASS = "main-class";
	public static final String JAVA_ARGS = "java-args";

	/**
	 * Discovers all integration points and runs them.
	 * <p>
	 * If an integration point created a native image it returns the resulting
	 * image.
	 *
	 *
	 * @param repositories
	 * @param artifacts
	 * @param tmpJarDir
	 * @param pomPath
	 * @param script
	 * @param nativeRequested
	 * @return
	 */
	public static IntegrationResult runIntegration(List<MavenRepo> repositories, List<ArtifactInfo> artifacts,
			Path tmpJarDir,
			Path pomPath, ScriptSource script,
			boolean nativeRequested) {
		URL[] urls = artifacts.stream().map(s -> {
			try {
				return s.asFile().toURI().toURL();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}).toArray(URL[]::new);
		List<String> comments = script.getLines().stream().filter(s -> s.startsWith("//")).collect(Collectors.toList());
		URLClassLoader integrationCl = new URLClassLoader(urls);
		ClassLoader old = Thread.currentThread().getContextClassLoader();
		Map<String, byte[]> data = new HashMap<>();
		List<Map.Entry<String, String>> repos = repositories.stream()
															.map(s -> new MapRepoEntry(s.getId(), s.getUrl()))
															.collect(Collectors.toList());
		List<Map.Entry<String, Path>> deps = artifacts	.stream()
														.map(s -> new MapEntry(s.getCoordinate().toCanonicalForm(),
																s.asFile().toPath()))
														.collect(Collectors.toList());
		Path nativeImage = null;
		String mainClass = null;
		List<String> javaArgs = null;
		PrintStream oldout = System.out;
		try {
			// TODO: should we add new properties to the integration method?
			if (script.getResourceRef().getFile() != null) {
				System.setProperty("jbang.source", script.getResourceRef().getFile().getAbsolutePath());
			}
			Thread.currentThread().setContextClassLoader(integrationCl);
			Set<String> classNames = loadIntegrationClassNames(integrationCl);
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className, true, integrationCl);
				Method method = clazz.getDeclaredMethod("postBuild", Path.class, Path.class, List.class, List.class,
						List.class,
						boolean.class);
				Util.infoMsg("Post build with " + className);

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
				Map<String, Object> integrationResult = (Map<String, Object>) method.invoke(null, tmpJarDir, pomPath,
						repos, deps, comments, nativeRequested);
				@SuppressWarnings("unchecked")
				Map<String, byte[]> ret = (Map<String, byte[]>) integrationResult.get(FILES);
				if (ret != null) {
					data.putAll(ret);
				}
				Path image = (Path) integrationResult.get(NATIVE_IMAGE);
				if (image != null) {
					nativeImage = image;
				}
				String mc = (String) integrationResult.get(MAIN_CLASS);
				if (mc != null) {
					mainClass = mc;
				}
				@SuppressWarnings("unchecked")
				List<String> ja = (List<String>) integrationResult.get(JAVA_ARGS);
				if (ja != null) {
					javaArgs = ja;
				}
			}
			for (Map.Entry<String, byte[]> entry : data.entrySet()) {
				Path target = tmpJarDir.resolve(entry.getKey());
				Files.createDirectories(target.getParent());
				try (OutputStream out = Files.newOutputStream(target)) {
					out.write(entry.getValue());
				}
			}
		} catch (ClassNotFoundException e) {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Unable to load integration class", e);
		} catch (NoSuchMethodException e) {
			throw new ExitException(
					EXIT_UNEXPECTED_STATE,
					"Integration class missing method with signature public static Map<String, byte[]> postBuild(Path classesDir, Path pomFile, List<Map.Entry<String, Path>> dependencies)",
					e);
		} catch (Exception e) {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Issue running postBuild()", e);
		} finally {
			Thread.currentThread().setContextClassLoader(old);
			System.setOut(oldout);
		}
		return new IntegrationResult(nativeImage, mainClass, javaArgs);

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

	private static class MapEntry implements Map.Entry<String, Path> {
		private final String key;
		private Path value;

		private MapEntry(String key, Path value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public Path getValue() {
			return value;
		}

		@Override
		public Path setValue(Path value) {
			Path old = this.value;
			this.value = value;
			return old;
		}

		@Override
		public String toString() {
			return "MapEntry{" +
					"key='" + key + '\'' +
					", value=" + value +
					'}';
		}
	}

	private static class MapRepoEntry implements Map.Entry<String, String> {
		private final String key;
		private String value;

		private MapRepoEntry(String key, String value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public String getValue() {
			return value;
		}

		@Override
		public String setValue(String value) {
			String old = this.value;
			this.value = value;
			return old;
		}

		@Override
		public String toString() {
			return "MapEntry{" +
					"key='" + key + '\'' +
					", value=" + value +
					'}';

		}
	}

}
