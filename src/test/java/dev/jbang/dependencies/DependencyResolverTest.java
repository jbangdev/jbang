package dev.jbang.dependencies;

import static dev.jbang.dependencies.DependencyUtil.toMavenRepo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

class DependencyResolverTest extends BaseTest {

	@Test
	void testFormatVersion() {
		assertEquals("[1.0,)", DependencyUtil.formatVersion("1.0+"));
	}

	@BeforeEach
	void clearCache() {
		Util.deletePath(Settings.getCacheDir(), true);
	}

	@Test
	void testdepIdToArtifact() {
		MavenCoordinate artifact = DependencyUtil.depIdToArtifact("com.offbytwo:docopt:0.6.0.20150202:redhat@doc");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("0.6.0.20150202", artifact.getVersion());
		assertEquals("redhat", artifact.getClassifier());
		assertEquals("doc", artifact.getType().getId());

		artifact = DependencyUtil.depIdToArtifact("com.offbytwo:docopt:0.6.0.20150202");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("0.6.0.20150202", artifact.getVersion());
		assertEquals("", artifact.getClassifier());
		assertEquals("jar", artifact.getType().getId());

		artifact = DependencyUtil.depIdToArtifact("com.offbytwo:docopt:0.6+");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("[0.6,)", artifact.getVersion());
		assertEquals("", artifact.getClassifier());
		assertEquals("jar", artifact.getType().getId());

		assertThrows(IllegalStateException.class, () -> DependencyUtil.depIdToArtifact("bla?f"));
	}

	@Test
	void testdecodeEnv() {
		assertThrows(IllegalStateException.class, () -> DependencyUtil.decodeEnv("{{wonka}}"));
		assertEquals("wonka", DependencyUtil.decodeEnv("wonka"));

		environmentVariables.set("test.value", "wonka");

		assertEquals("wonka", DependencyUtil.decodeEnv("{{test.value}}"));
	}

	@Test
	void testdepIdWithPlaceHoldersToArtifact() {
		Detector detector = new Detector();
		Properties p = new Properties();
		detector.detect(p, Collections.emptyList());

		String gav = PropertiesValueResolver.replaceProperties(
				"com.example:my-native-library:1.0.0:${os.detected.jfxname}", p);

		MavenCoordinate artifact = DependencyUtil.depIdToArtifact(gav);
		assertEquals("com.example", artifact.getGroupId());
		assertEquals("my-native-library", artifact.getArtifactId());
		assertEquals("1.0.0", artifact.getVersion());
		assertEquals(p.getProperty("os.detected.jfxname"), artifact.getClassifier());
		assertEquals("jar", artifact.getType().getExtension());
	}

	@Test
	void testResolveJavaFXWithAether() {
		Detector detector = new Detector();
		Properties p = new Properties();
		detector.detect(p, Collections.emptyList());

		List<String> deps = Collections.singletonList(
				PropertiesValueResolver.replaceProperties("org.openjfx:javafx-base:11.0.2:${os.detected.jfxname}", p));

		List<ArtifactInfo> artifacts = DependencyUtil.resolveDependenciesViaAether(deps,
				Collections.singletonList(toMavenRepo("mavencentral")), false, false, true, true);

		assertEquals(1, artifacts.size());
	}

	@Test
	void testResolveDependenciesWithAether() {
		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		List<ArtifactInfo> artifacts = DependencyUtil.resolveDependenciesViaAether(deps,
				Collections.singletonList(toMavenRepo("mavencentral")), false, false, true, true);

		assertEquals(2, artifacts.size());
	}

	@Test
	void testRedHatJBossRepos() {
		assertEquals(toMavenRepo("jbossorg").getUrl(), "https://repository.jboss.org/nexus/content/groups/public/");
		assertEquals(toMavenRepo("redhat").getUrl(), "https://maven.repository.redhat.com/ga/");
	}

	@Test
	void testResolveDependencies() {
		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				true);

		// if returns 5 its because optional deps are included which they shouldn't
		assertEquals(2, classpath.getClassPaths().size());
	}

	@Test
	void testResolveDependenciesNoDuplicates() {
		List<String> deps = Arrays.asList("org.apache.commons:commons-configuration2:2.7",
				"org.apache.commons:commons-text:1.8");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				true);

		// if returns with duplicates its because some dependencies are multiple times
		// in the
		// classpath (commons-text-1.8, commons-lang3-3.9)
		List<String> cps = classpath.getClassPaths();

		HashSet<String> othercps = new HashSet<>(cps);

		assertThat(cps, containsInAnyOrder(othercps.toArray()));
	}

	@Test
	void testResolveNativeDependencies() {
		Detector detector = new Detector();
		detector.detect(new Properties(), Collections.emptyList());

		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Collections.singletonList("com.github.docker-java:docker-java:3.1.5");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				true);

		assertEquals(46, classpath.getClassPaths().size());
	}

	@Test
	void testResolveJavaModules() throws IOException {
		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Arrays.asList("org.openjfx:javafx-graphics:11.0.2:mac", "com.offbytwo:docopt:0.6+");

		ModularClassPath cp = new ModularClassPath(
				DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false, true).getArtifacts()) {
			@Override
			protected boolean supportsModules(String requestedVersion) {
				return true;
			}
		};

		List<String> ma = cp.getAutoDectectedModuleArguments(null);

		assertThat(ma, hasItem("--module-path"));

		assertThat(ma, not(hasItem("docopt")));

		assertThat(cp.getClassPath(), containsString("docopt"));
	}

	/*
	 * @Ignore("BOM import not yet figured out with shrinkwrap")
	 */
	@Test
	void testImportPOM() {
		List<String> deps = Arrays.asList("com.microsoft.azure:azure-bom:1.0.0.M1@pom", "com.microsoft.azure:azure");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				true);

		assertEquals(62, classpath.getArtifacts().size());
	}

	@Test
	void testImportMultipleBoms() {
		List<String> deps = Arrays.asList("io.vertx:vertx-stack-depchain:4.2.3@pom", // if not listed then vertx.core
																						// will be version 3.9.5
				"org.apache.camel:camel-bom:3.9.0@pom",
				// "io.vertx:vertx-core",
				"org.apache.camel:camel-core",
				"org.apache.camel:camel-vertx",
				"org.slf4j:slf4j-simple:1.7.30");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				true);

		Optional<ArtifactInfo> coord = classpath.getArtifacts()
												.stream()
												.filter(ai -> ai.getCoordinate()
																.toCanonicalForm()
																.startsWith("io.vertx:vertx-core"))
												.findFirst();

		assertEquals(coord.get().getCoordinate().getVersion(), "4.2.3");

		coord = classpath	.getArtifacts()
							.stream()
							.filter(ai -> ai.getCoordinate().toCanonicalForm().startsWith("org.slf4j:slf4j-simple:"))
							.findFirst();

		assertEquals(coord.get().getCoordinate().getVersion(), "1.7.30");

		coord = classpath	.getArtifacts()
							.stream()
							.filter(ai -> ai.getCoordinate()
											.toCanonicalForm()
											.startsWith("org.apache.camel:camel-vertx"))
							.findFirst();

		assertEquals(coord.get().getCoordinate().getVersion(), "3.9.0");

		deps = Arrays.asList(
				"org.apache.camel:camel-bom:3.9.0@pom",
				"org.apache.camel:camel-core",
				"org.apache.camel:camel-vertx",
				"org.slf4j:slf4j-simple:1.7.30");
		classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false, true);

		coord = classpath	.getArtifacts()
							.stream()
							.filter(ai -> ai.getCoordinate().toCanonicalForm().startsWith("io.vertx:vertx-core"))
							.findFirst();

		assertEquals(coord.get().getCoordinate().getVersion(), "3.9.5");
	}

}
