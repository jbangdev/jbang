package dk.xam.jbang;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.Test;
import org.sonatype.aether.artifact.Artifact;

class DependencyResolverTest {

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testFormatVersion() {
		DependencyUtil dr = new DependencyUtil();

		assertEquals("[1.0,)", dr.formatVersion("1.0+"));
	}

	@Test
	void testdepIdToArtifact() {
		DependencyUtil dr = new DependencyUtil();

		Artifact artifact = dr.depIdToArtifact("com.offbytwo:docopt:0.6.0.20150202:redhat@doc");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("0.6.0.20150202", artifact.getVersion());
		assertEquals("redhat", artifact.getClassifier());
		assertEquals("doc", artifact.getExtension());

		artifact = dr.depIdToArtifact("com.offbytwo:docopt:0.6.0.20150202");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("0.6.0.20150202", artifact.getVersion());
		assertEquals("", artifact.getClassifier());
		assertEquals("jar", artifact.getExtension());

		artifact = dr.depIdToArtifact("com.offbytwo:docopt:0.6+");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("[0.6,)", artifact.getVersion());
		assertEquals("", artifact.getClassifier());
		assertEquals("jar", artifact.getExtension());

		assertThrows(IllegalStateException.class, () -> dr.depIdToArtifact("bla?f"));
	}

	@Test
	void testdecodeEnv() {

		DependencyUtil dr = new DependencyUtil();

		assertThrows(IllegalStateException.class, () -> dr.decodeEnv("{{wonka}}"));
		assertEquals("wonka", dr.decodeEnv("wonka"));

		environmentVariables.set("test.value", "wonka");

		assertEquals("wonka", dr.decodeEnv("{{test.value}}"));

	}

	@Test
	void testdepIdWithPlaceHoldersToArtifact() {
		dk.xam.jbang.Detector detector = new dk.xam.jbang.Detector();
		detector.detect(new Properties(), Collections.emptyList());

		String gav = PropertiesValueResolver.replaceProperties(
				"com.example:my-native-library:1.0.0:${os.detected.jfxname}");

		Artifact artifact = new DependencyUtil().depIdToArtifact(gav);
		assertEquals("com.example", artifact.getGroupId());
		assertEquals("my-native-library", artifact.getArtifactId());
		assertEquals("1.0.0", artifact.getVersion());
		assertEquals(System.getProperty("os.detected.jfxname"), artifact.getClassifier());
		assertEquals("jar", artifact.getExtension());
	}

	@Test
	void testResolveDependenciesWithAether() {

		DependencyUtil dr = new DependencyUtil();

		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		List<Artifact> artifacts = dr.resolveDependenciesViaAether(deps, Collections.emptyList(), true);

		assertEquals(5, artifacts.size());

	}

	@Test
	void testResolveDependencies() {

		DependencyUtil dr = new DependencyUtil();

		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		String classpath = dr.resolveDependencies(deps, Collections.emptyList(), true);

		assertEquals(5, classpath.split(Settings.CP_SEPARATOR).length);

	}

	@Test
	void testResolveNativeDependencies() {
		DependencyUtil dr = new DependencyUtil();

		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Arrays.asList("com.github.docker-java:docker-java:3.1.5");

		String classpath = dr.resolveDependencies(deps, Collections.emptyList(), true);

		assertEquals(46, classpath.split(Settings.CP_SEPARATOR).length);

	}

	@Test
	void testResolveJavaModules() throws IOException {
		DependencyUtil dr = new DependencyUtil();

		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Arrays.asList("org.openjfx:javafx-graphics:11.0.2:mac", "com.offbytwo:docopt:0.6+");

		ModularClassPath cp = new ModularClassPath(dr.resolveDependencies(deps, Collections.emptyList(), true)) {
			@Override
			protected boolean supportsModules(String javacmd) {
				return true;
			}
		};

		List<String> ma = cp.getAutoDectectedModuleArguments("java");

		assertThat(ma, hasItem("--module-path"));

		assertThat(ma, not(hasItem("docopt")));

		assertThat(cp.getClassPath(), containsString("docopt"));
	}

}
