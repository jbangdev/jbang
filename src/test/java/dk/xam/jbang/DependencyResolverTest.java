package dk.xam.jbang;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.Test;
import org.sonatype.aether.artifact.Artifact;

class DependencyResolverTest {

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testFormatVersion() {
		var dr = new DependencyUtil();

		assertEquals("[1.0,)", dr.formatVersion("1.0+"));
	}

	@Test
	void testdepIdToArtifact() {
		var dr = new DependencyUtil();

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

		var dr = new DependencyUtil();

		assertThrows(IllegalStateException.class, () -> dr.decodeEnv("{{wonka}}"));
		assertEquals("wonka", dr.decodeEnv("wonka"));

		environmentVariables.set("test.value", "wonka");

		assertEquals("wonka", dr.decodeEnv("{{test.value}}"));

	}

	@Test
	void testResolveDependenciesWithAether() {

		var dr = new DependencyUtil();

		var deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		List<Artifact> artifacts = dr.resolveDependenciesViaAether(deps, Collections.emptyList(), true);

		assertEquals(5, artifacts.size());

	}

	@Test
	void testResolveDependencies() {

		var dr = new DependencyUtil();

		var deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		String classpath = dr.resolveDependencies(deps, Collections.emptyList(), true);

		assertEquals(5, classpath.split(Settings.CP_SEPARATOR).length);

	}
}
