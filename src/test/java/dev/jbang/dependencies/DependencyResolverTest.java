package dev.jbang.dependencies;

import static dev.jbang.dependencies.DependencyUtil.toMavenRepo;
import static dev.jbang.util.JavaUtil.defaultJdkManager;
import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.devkitman.Jdk;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

class DependencyResolverTest extends BaseTest {

	@Test
	void testFormatVersion() {
		assertThat(DependencyUtil.formatVersion("1.0+")).isEqualTo("[1.0,)");
	}

	@BeforeEach
	void clearCache() {
		Util.deletePath(Settings.getCacheDir(), true);
	}

	@Test
	void testdepIdToArtifact() {
		MavenCoordinate artifact = MavenCoordinate.fromString("com.offbytwo:docopt:0.6.0.20150202:redhat@doc");
		assertThat(artifact.getGroupId()).isEqualTo("com.offbytwo");
		assertThat(artifact.getArtifactId()).isEqualTo("docopt");
		assertThat(artifact.getVersion()).isEqualTo("0.6.0.20150202");
		assertThat(artifact.getClassifier()).isEqualTo("redhat");
		assertThat(artifact.getType()).isEqualTo("doc");

		artifact = MavenCoordinate.fromString("com.offbytwo:docopt:0.6.0.20150202");
		assertThat(artifact.getGroupId()).isEqualTo("com.offbytwo");
		assertThat(artifact.getArtifactId()).isEqualTo("docopt");
		assertThat(artifact.getVersion()).isEqualTo("0.6.0.20150202");
		assertThat(artifact.getClassifier()).isNull();
		assertThat(artifact.getType()).isEqualTo("jar");

		artifact = MavenCoordinate.fromString("com.offbytwo:docopt:0.6+");
		assertThat(artifact.getGroupId()).isEqualTo("com.offbytwo");
		assertThat(artifact.getArtifactId()).isEqualTo("docopt");
		assertThat(artifact.getVersion()).isEqualTo("[0.6,)");
		assertThat(artifact.getClassifier()).isNull();
		assertThat(artifact.getType()).isEqualTo("jar");

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> MavenCoordinate.fromString("bla?f"));
	}

	@Test()
	void testEqualsEmptyAttributes() {
		MavenCoordinate a1 = MavenCoordinate.fromString("a.b:c:0.6:qf@doc");
		MavenCoordinate a2 = MavenCoordinate.fromString("a.b:c:0.6:qf@doc");
		assertThat(a1).as("mavencoordinate bad comparison of empty attribtues").isEqualTo(a2);
	}

	@Test
	void testEqualsGAVBehavior() {
		MavenCoordinate a1 = MavenCoordinate.fromString("a.b:c:0.6:qf@doc");
		MavenCoordinate a2 = MavenCoordinate.fromString("a.b:c:0.6:qf@doc{build,run}");
		assertThat(a1).isEqualTo(a2); // TODO: these are equal in behavior - but not in "format"
	}

	@Test
	void testEqualsGAVFlippedBehavior() {
		MavenCoordinate a1 = MavenCoordinate.fromString("a.b:c:0.6:qf@doc{run,build}");
		MavenCoordinate a2 = MavenCoordinate.fromString("a.b:c:0.6:qf@doc{build,run}");
		assertThat(a1).isNotEqualTo(a2); // these are equal in behavior - but not in "format"
	}

	@Test
	void testVariants() {
		MavenCoordinate artifact = MavenCoordinate
			.fromString("com.offbytwo:docopt:0.6.0.20150202:redhat@doc{build,boot,run}");
		assertThat(artifact.getGroupId()).isEqualTo("com.offbytwo");
		assertThat(artifact.getArtifactId()).isEqualTo("docopt");
		assertThat(artifact.getVersion()).isEqualTo("0.6.0.20150202");
		assertThat(artifact.getClassifier()).isEqualTo("redhat");
		assertThat(artifact.getType()).isEqualTo("doc");
		assertThat(artifact.getAttributes().includeInScope("boot")).isTrue();
	}

	@Test
	void testScopes() {

		MavenCoordinate artifact = MavenCoordinate.fromString("a.b:c:1.2{build}");

		assertThat(artifact)
			.extracting("groupId", "artifactId", "version", "classifier")
			.containsExactly("a.b", "c", "1.2", null);

		assertThat(artifact.getAttributes().includeInScope("build")).isTrue();

	}

	@Test
	void testMultiScopes() {
		MavenCoordinate artifact = MavenCoordinate.fromString("a.b:c:1.2{build}");

		assertThat(artifact)
			.extracting("groupId", "artifactId", "version", "classifier")
			.containsExactly("a.b", "c", "1.2", null);

		assertThat(artifact.getAttributes().includeInScope("build")).isTrue();
		assertThat(artifact.getAttributes().includeInScope("compile")).isFalse();
		assertThat(artifact.getAttributes().includeInScope("doesnotexist")).isFalse();
	}

	@Test
	void testBadDependencyRequests() {
		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> MavenCoordinate.fromString("a.b:c:1.2{"));
	}

	@Test
	void testOtherProperties() {

		MavenCoordinate artifact = MavenCoordinate.fromString("a.b:c:1.2{build}");

		assertThat(artifact)
			.extracting("groupId", "artifactId", "version", "classifier")
			.containsExactly("a.b", "c", "1.2", null);

		MavenCoordinate artifact2 = MavenCoordinate.fromString("a.b:c:1.2");

		assertThat(artifact2)
			.extracting("groupId", "artifactId", "version", "classifier")
			.containsExactly("a.b", "c", "1.2", null);

		assertThat(artifact2.getAttributes().includeInScope("build")).isTrue();
		assertThat(artifact2.getAttributes().includeInScope("run")).isTrue();
	}

	@Test
	void testdecodeEnv() {
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> DependencyUtil.decodeEnv("{{wonka}}"));
		assertThat(DependencyUtil.decodeEnv("wonka")).isEqualTo("wonka");

		environmentVariables.set("test.value", "wonka");

		assertThat(DependencyUtil.decodeEnv("{{test.value}}")).isEqualTo("wonka");
	}

	@Test
	void testdepIdWithPlaceHoldersToArtifact() {
		Detector detector = new Detector();
		Properties p = new Properties();
		detector.detect(p, Collections.emptyList());

		String gav = PropertiesValueResolver.replaceProperties(
				"com.example:my-native-library:1.0.0:${os.detected.jfxname}", p);

		MavenCoordinate artifact = MavenCoordinate.fromString(gav);
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("my-native-library");
		assertThat(artifact.getVersion()).isEqualTo("1.0.0");
		assertThat(artifact.getClassifier()).isEqualTo(p.getProperty("os.detected.jfxname"));
		assertThat(artifact.getType()).isEqualTo("jar");
	}

	@Test
	void testResolveJavaFXWithAether() {
		Detector detector = new Detector();
		Properties p = new Properties();
		detector.detect(p, Collections.emptyList());

		List<String> deps = Collections.singletonList(
				PropertiesValueResolver.replaceProperties("org.openjfx:javafx-base:18.0.2:${os.detected.jfxname}", p));
		List<ArtifactInfo> artifacts = ArtifactResolver.Builder.create().build().resolve(deps);
		assertThat(artifacts.size()).isEqualTo(1);
	}

	@Test
	void testResolveDependenciesWithAether() {
		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");
		List<ArtifactInfo> artifacts = ArtifactResolver.Builder.create().build().resolve(deps);
		assertThat(artifacts.size()).isEqualTo(2);
	}

	@Test
	void testResolveDependenciesAltRepo(@TempDir File altrepo) {
		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");
		List<ArtifactInfo> artifacts = ArtifactResolver.Builder
			.create()
			.localFolder(altrepo.toPath())
			.build()
			.resolve(deps);
		assertThat(artifacts.size()).isEqualTo(2);
		assertThat(altrepo.listFiles()).hasSize(4);
	}

	@Test
	void testRedHatJBossRepos() {
		assertThat("https://repository.jboss.org/nexus/content/groups/public/")
			.isEqualTo(toMavenRepo("jbossorg").getUrl());
		assertThat("https://maven.repository.redhat.com/ga/").isEqualTo(toMavenRepo("redhat").getUrl());
	}

	@Test
	void testResolveDependencies() {
		List<String> deps = Arrays.asList("com.offbytwo:docopt:0.6.0.20150202", "log4j:log4j:1.2+");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				false,
				true, false);

		// if returns 5 its because optional deps are included which they shouldn't
		assertThat(classpath.getClassPaths().size()).isEqualTo(2);
	}

	@Test
	void testResolveDependenciesNoDuplicates() {
		List<String> deps = Arrays.asList("org.apache.commons:commons-configuration2:2.7",
				"org.apache.commons:commons-text:1.8");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				false,
				true, false);

		// if returns with duplicates its because some dependencies are multiple times
		// in the
		// classpath (commons-text-1.8, commons-lang3-3.9)
		List<String> cps = classpath.getClassPaths();

		HashSet<String> othercps = new HashSet<>(cps);

		assertThat(cps).containsAll(othercps);
	}

	@Test
	void testResolveNativeDependencies() {
		Detector detector = new Detector();
		detector.detect(new Properties(), Collections.emptyList());

		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Collections.singletonList("com.github.docker-java:docker-java:3.1.5");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				false,
				true, false);

		assertThat(classpath.getClassPaths().size()).isEqualTo(46);
	}

	@Test
	void testResolveJavaModules() throws IOException {
		// using shrinkwrap resolves in ${os.detected.version} not being resolved
		List<String> deps = Arrays.asList("org.openjfx:javafx-graphics:11.0.2:mac", "com.offbytwo:docopt:0.6+");

		ModularClassPath cp = new ModularClassPath(
				DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false, false, true, false)
					.getArtifacts()) {
			@Override
			protected boolean supportsModules(Jdk jdk) {
				return true;
			}
		};

		List<String> ma = cp.getAutoDectectedModuleArguments(defaultJdkManager().getOrInstallJdk(null));

		assertThat(ma).contains("--module-path");

		assertThat(ma).doesNotContain("docopt");

		assertThat(cp.getClassPath()).contains("docopt");
	}

	@Test
	void testImportPOM() {
		List<String> deps = Arrays.asList("com.microsoft.azure:azure-bom:1.0.0.M1@pom", "com.microsoft.azure:azure");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				false,
				true, false);

		assertThat(classpath.getArtifacts().size()).isEqualTo(62);
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
				false,
				true, false);

		Optional<ArtifactInfo> coord = classpath.getArtifacts()
			.stream()
			.filter(ai -> ai.getCoordinate()
				.toCanonicalForm()
				.startsWith("io.vertx:vertx-core"))
			.findFirst();

		assertThat(coord.get().getCoordinate().getVersion()).isEqualTo("4.2.3");

		coord = classpath.getArtifacts()
			.stream()
			.filter(ai -> ai.getCoordinate().toCanonicalForm().startsWith("org.slf4j:slf4j-simple:"))
			.findFirst();

		assertThat(coord.get().getCoordinate().getVersion()).isEqualTo("1.7.30");

		coord = classpath.getArtifacts()
			.stream()
			.filter(ai -> ai.getCoordinate()
				.toCanonicalForm()
				.startsWith("org.apache.camel:camel-vertx"))
			.findFirst();

		assertThat("3.9.0").isEqualTo(coord.get().getCoordinate().getVersion());

		deps = Arrays.asList(
				"org.apache.camel:camel-bom:3.9.0@pom",
				"org.apache.camel:camel-core",
				"org.apache.camel:camel-vertx",
				"org.slf4j:slf4j-simple:1.7.30");
		classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false, false, true, false);

		coord = classpath.getArtifacts()
			.stream()
			.filter(ai -> ai.getCoordinate().toCanonicalForm().startsWith("io.vertx:vertx-core"))
			.findFirst();

		assertThat(coord.get().getCoordinate().getVersion()).isEqualTo("3.9.5");
	}

	@Test
	void testResolveTestJar() {
		List<String> deps = Arrays.asList("org.infinispan:infinispan-commons:13.0.5.Final@test-jar");

		ModularClassPath classpath = DependencyUtil.resolveDependencies(deps, Collections.emptyList(), false, false,
				false,
				true, false);

		assertThat(classpath.getArtifacts()).hasSize(7);
		ArtifactInfo ai = classpath.getArtifacts().get(0);
		assertThat(ai.getCoordinate().toCanonicalForm())
			.isEqualTo("org.infinispan:infinispan-commons:tests:jar:13.0.5.Final");
		assertThat(ai.getFile().toString()).endsWith("infinispan-commons-13.0.5.Final-tests.jar");
	}

}
