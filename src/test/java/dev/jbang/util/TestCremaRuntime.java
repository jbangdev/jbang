package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.ExitException;

class TestCremaRuntime {
	@TempDir
	Path directory;

	@Test
	void ordinaryJvmIsUnsupported() {
		assertFalse(CremaRuntime.isSupported());
	}

	@Test
	void loadsDependencyResourcesAndServiceProviders() throws Exception {
		Path dependency = directory.resolve("dependency");
		Files.createDirectories(dependency);
		compile(dependency, "Greeting", "public interface Greeting { String text(); }");
		compile(dependency, "Provider",
				"public class Provider implements Greeting { public String text() { return \"service\"; } }");
		Path jar = directory.resolve("dependency.jar");
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
			addEntry(out, "Greeting.class", Files.readAllBytes(dependency.resolve("Greeting.class")));
			addEntry(out, "Provider.class", Files.readAllBytes(dependency.resolve("Provider.class")));
			addEntry(out, "META-INF/services/Greeting", "Provider\n".getBytes(StandardCharsets.UTF_8));
			addEntry(out, "message.txt", "resource".getBytes(StandardCharsets.UTF_8));
		}
		Path application = directory.resolve("application");
		Files.createDirectories(application);
		compile(application, "Application",
				"public class Application { public static void main(String[] a) throws Exception { "
						+ "ClassLoader cl = Thread.currentThread().getContextClassLoader(); "
						+ "if (cl.loadClass(\"Greeting\").getClassLoader() != cl) throw new AssertionError(); "
						+ "String resource = new String(cl.getResourceAsStream(\"message.txt\").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); "
						+ "String service = java.util.ServiceLoader.load(Greeting.class).iterator().next().text(); "
						+ "System.setProperty(\"crema.test.result\", a[0] + \":\" + resource + \":\" + service + \":\" + System.getProperty(\"crema.test.input\")); } }",
				jar);
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		String previousClasspath = System.getProperty("java.class.path");
		try {
			CremaRuntime.launch(Arrays.asList(application, jar), "Application", new String[] { "two words" },
					Collections.singletonMap("crema.test.input", "property"));
			assertEquals("two words:resource:service:property", System.getProperty("crema.test.result"));
			assertSame(previous, Thread.currentThread().getContextClassLoader());
		} finally {
			System.setProperty("java.class.path", previousClasspath);
			System.clearProperty("crema.test.input");
			System.clearProperty("crema.test.result");
		}
	}

	@Test
	void reportsApplicationFailureAndRestoresContextLoader() throws Exception {
		compile(directory, "Broken",
				"public class Broken { public static void main(String[] a) { throw new IllegalStateException(\"boom\"); } }");
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		String previousClasspath = System.getProperty("java.class.path");
		try {
			ExitException failure = assertThrows(ExitException.class,
					() -> CremaRuntime.launch(Collections.singletonList(directory), "Broken", new String[0],
							Collections.emptyMap()));
			assertEquals(ExitException.EXIT_GENERIC_ERROR, failure.getStatus());
			assertTrue(failure.getCause() instanceof IllegalStateException);
			assertSame(previous, Thread.currentThread().getContextClassLoader());
		} finally {
			System.setProperty("java.class.path", previousClasspath);
		}
	}

	private void compile(Path output, String name, String source, Path... dependencies) throws IOException {
		Path file = output.resolve(name + ".java");
		Files.writeString(file, source);
		String classpath = output.toString();
		for (Path dependency : dependencies) {
			classpath += java.io.File.pathSeparator + dependency;
		}
		assertEquals(0, ToolProvider.getSystemJavaCompiler()
			.run(null, null, null,
					"-classpath", classpath, "-d", output.toString(), file.toString()));
	}

	private void addEntry(JarOutputStream out, String name, byte[] content) throws IOException {
		out.putNextEntry(new JarEntry(name));
		out.write(content);
		out.closeEntry();
	}
}
