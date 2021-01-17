package dev.jbang;

import static dev.jbang.DependencyUtil.toMavenRepo;
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
import java.util.Properties;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyResolverTest extends BaseTest {

	@Test
	void testFormatVersion() {
		DependencyUtil dr = new DependencyUtil();

		assertEquals("[1.0,)", dr.formatVersion("1.0+"));
	}

	@BeforeEach
	void clearCache() {
		Util.deletePath(Settings.getCacheDir(), true);
	}

	@Test
	void testdepIdToArtifact() {
		DependencyUtil dr = new DependencyUtil();

		MavenCoordinate artifact = dr.depIdToArtifact("com.offbytwo:docopt:0.6.0.20150202:redhat@doc");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("0.6.0.20150202", artifact.getVersion());
		assertEquals("redhat", artifact.getClassifier());
		assertEquals("doc", artifact.getType().getId());

		artifact = dr.depIdToArtifact("com.offbytwo:docopt:0.6.0.20150202");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("0.6.0.20150202", artifact.getVersion());
		assertEquals("", artifact.getClassifier());
		assertEquals("jar", artifact.getType().getId());

		artifact = dr.depIdToArtifact("com.offbytwo:docopt:0.6+");
		assertEquals("com.offbytwo", artifact.getGroupId());
		assertEquals("docopt", artifact.getArtifactId());
		assertEquals("[0.6,)", artifact.getVersion());
		assertEquals("", artifact.getClassifier());
		assertEquals("jar", artifact.getType().getId());

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
		dev.jbang.Detector detector = new dev.jbang.Detector();
		detector.detect(new Properties(), Collections.emptyList());

		String gav = PropertiesValueResolver.replaceProperties(
				"com.example:my-native-library:1.0.0:${os.detected.jfxname}");

		MavenCoordinate artifact = new DependencyUtil().depIdToArtifact(gav);
		assertEquals("com.example", artifact.getGroupId());
		assertEquals("my-native-library", artifact.getArtifactId());
		assertEquals("1.0.0", artifact.getVersion());
		assertEquals(System.getProperty("os.detected.jfxname"), artifact.getClassifier());
		assertEquals("jar", artifact.getType().getExtension());
	}

	@Test
	void testResolveJavaFXWithAether() {

		dev.jbang.Detector detector = new dev.jbang.Detector();
		detector.detect(new Properties(), Collections.emptyList());

		DependencyUtil dr = new DependencyUtil();

		List<String> deps = Arrays.asList(
				PropertiesValueResolver.replaceProperties("org.openjfx:javafx-base:11.0.2:${os.detected.jfxname}"));

		List<ArtifactInfo> artifacts = dr.resolveDependenciesViaAether(deps,
				Arrays.asList(toMavenRepo("jcenter")), false,
				true, true);

		assertEquals(1, artifacts.size());

	}

	@Test
	void testResolveDependenciesWithAether() {

		DependencyUtil dr = new DependencyUtil();

		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		List<ArtifactInfo> artifacts = dr.resolveDependenciesViaAether(deps,
				Arrays.asList(toMavenRepo("jcenter")), false,
				true, true);

		assertEquals(2, artifacts.size());

	}

	@Test
	void testRedHatJBossRepos() {
		assertEquals(toMavenRepo("jbossorg").getUrl(), "https://repository.jboss.org/nexus/content/groups/public/");
		assertEquals(toMavenRepo("redhat").getUrl(), "https://maven.repository.redhat.com/ga/");

	}

	@Test
	void testResolveDependencies() {

		DependencyUtil dr = new DependencyUtil();

		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		ModularClassPath classpath = dr.resolveDependencies(deps, Collections.emptyList(), false, true);

		// if returns 5 its because optional deps are included which they shouldn't
		assertEquals(2, classpath.getClassPath().split(Settings.CP_SEPARATOR).length);

	}

	@Test
	void testResolveDependenciesNoDuplicates() {

		DependencyUtil dr = new DependencyUtil();

		List<String> deps = Arrays.asList(
				"org.apache.commons:commons-configuration2:2.7",
				"org.apache.commons:commons-text:1.8");

		ModularClassPath classpath = dr.resolveDependencies(deps, Collections.emptyList(), false, true);

		// if returns with duplicates its because some dependencies are multiple times
		// in the
		// classpath (commons-text-1.8, commons-lang3-3.9)
		List<String> cps = Arrays.asList(classpath.getClassPath().split(Settings.CP_SEPARATOR));

		HashSet<String> othercps = new HashSet<>();
		othercps.addAll(cps);

		assertThat(cps, containsInAnyOrder(othercps.toArray()));

	}

	@Test
	void testResolveNativeDependencies() {

		dev.jbang.Detector detector = new dev.jbang.Detector();
		detector.detect(new Properties(), Collections.emptyList());

		DependencyUtil dr = new DependencyUtil();

		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Arrays.asList("com.github.docker-java:docker-java:3.1.5");

		ModularClassPath classpath = dr.resolveDependencies(deps, Collections.emptyList(), false, true);

		assertEquals(46, classpath.getClassPath().split(Settings.CP_SEPARATOR).length);

	}

	@Test
	void testResolveJavaModules() throws IOException {
		DependencyUtil dr = new DependencyUtil();

		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Arrays.asList("org.openjfx:javafx-graphics:11.0.2:mac", "com.offbytwo:docopt:0.6+");

		ModularClassPath cp = new ModularClassPath(
				dr.resolveDependencies(deps, Collections.emptyList(), false, true).getArtifacts()) {
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

		List<String> deps = Arrays.asList("com.microsoft.azure:azure-bom:1.0.0.M1@pom",
				"com.microsoft.azure:azure");

		ModularClassPath classpath = new DependencyUtil().resolveDependencies(deps,
				Collections.emptyList(), false, true);

		assertEquals(62, classpath.getArtifacts().size());

	}

}
