package dev.jbang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

/**
 * JBang uses a 'convention based interface' for build time integration.
 *
 *
 */
public class IntegrationManager {

	public static void runIntegration(List<ArtifactInfo> artifacts, Path tmpJarDir, Path pomPath) {
		System.out.println("Test");
		URL[] urls = artifacts.stream().map(s -> {
			try {
				return s.asFile().toURI().toURL();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}).toArray(URL[]::new);
		URLClassLoader integrationCl = new URLClassLoader(urls);
		ClassLoader old = Thread.currentThread().getContextClassLoader();
		Map<String, byte[]> data = new HashMap<>();
		List<Map.Entry<String, Path>> deps = artifacts	.stream()
														.map(s -> new MapEntry(s.getCoordinate().toCanonicalForm(),
																s.asFile().toPath()))
														.collect(Collectors.toList());
		try {
			Thread.currentThread().setContextClassLoader(integrationCl);
			Set<String> classNames = loadIntegrationClassNames(integrationCl);
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className, true, integrationCl);
				Method method = clazz.getDeclaredMethod("postBuild", Path.class, Path.class, List.class);
				Map<String, byte[]> ret = (Map<String, byte[]>) method.invoke(null, tmpJarDir, pomPath, deps);
				data.putAll(ret);
			}
			for (Map.Entry<String, byte[]> entry : data.entrySet()) {
				Util.verboseMsg("writing " + entry.getKey());
				Path target = tmpJarDir.resolve(entry.getKey());
				Files.createDirectories(target.getParent());
				try (OutputStream out = Files.newOutputStream(target)) {
					out.write(entry.getValue());
				}
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Unable to load integration class", e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(
					"Integration class missing method with signature public static Map<String, byte[]> postBuild(Path classesDir, Path pomFile, List<Map.Entry<String, Path>> dependencies)",
					e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(old);
		}

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
	}

}
