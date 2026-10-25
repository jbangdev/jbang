package dev.jbang.source;

import static dev.jbang.util.Util.MAIN_JAVA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.resolvers.AliasResourceResolver;
import dev.jbang.util.Util;
import dev.jbang.util.WarTestFixtures;

public class TestProjectBuilder extends BaseTest {

	@Test
	void testDuplicateAnonRepos() {
		ProjectBuilder pb = Project.builder();
		pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = pb.build(src);
		assertThrows(ExitException.class, () -> {
			BuildContext.forProject(prj).resolveClassPath();
		});
	}

	@Test
	void testDuplicateNamedRepos() {
		ProjectBuilder pb = Project.builder();
		pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://foo"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = pb.build(src);
		assertThrows(ExitException.class, () -> {
			BuildContext.forProject(prj).resolveClassPath();
		});
	}

	@Test
	void testReposSameIdDifferentUrl() {
		ProjectBuilder pb = Project.builder();
		pb.additionalRepositories(Arrays.asList("foo=http://foo", "foo=http://bar"));
		Path src = examplesTestFolder.resolve("quote.java");
		Project prj = pb.build(src);
		assertThrows(IllegalArgumentException.class, () -> {
			BuildContext.forProject(prj).resolveClassPath();
		});
	}

	@Test
	void testSourceTags() {
		ProjectBuilder pb = Project.builder();
		Path src = examplesTestFolder.resolve("alltags.java");
		Project prj = pb.build(src);
		assertThat(prj.getDescription().get(), equalTo("some description"));
		assertThat(prj.getRuntimeOptions(), iterableWithSize(2));
		assertThat(prj.getRuntimeOptions(), contains("--add-opens", "java.base/java.net=ALL-UNNAMED"));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(6));
		assertThat(prj.getMainSourceSet().getSources(), containsInAnyOrder(
				ResourceRef.forFile(src),
				ResourceRef.forFile(examplesTestFolder.resolve("Two.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("gh_fetch_release_assets.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("gh_release_stats.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedTwo.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedOne.java"))));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(3));
		assertThat(prj.getMainSourceSet().getResources(), containsInAnyOrder(
				RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")), null),
				RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
						Paths.get("renamed.properties")),
				RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
						Paths.get("META-INF/application.properties"))));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(5));
		assertThat(prj.getMainSourceSet().getDependencies(), contains(
				"dummy.org:dummy:1.2.3",
				"info.picocli:picocli:4.6.3",
				"org.kohsuke:github-api:1.116",
				"info.picocli:picocli:4.6.3",
				"org.kohsuke:github-api:1.116"));
		assertThat(prj.getRepositories(), iterableWithSize(1));
		assertThat(prj.getRepositories(), contains(new MavenRepo("dummy", "http://dummy")));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(0));
		assertThat(prj.getProperties(), anEmptyMap());
		assertThat(prj.getJavaVersion(), equalTo("11+"));
		assertThat(prj.getMainClass(), equalTo("mainclass"));
		assertThat(prj.getModuleName().get(), equalTo("mymodule"));
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(4));
		assertThat(prj.getMainSourceSet().getCompileOptions(),
				contains("-g", "-parameters", "--enable-preview", "--verbose"));
		assertThat(prj.isNativeImage(), is(Boolean.FALSE));
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getNativeOptions(), contains("-O1", "-d"));
		assertThat(prj.enableCDS(), is(Boolean.TRUE));
		assertThat(prj.enablePreview(), is(Boolean.TRUE));
		assertThat(prj.getManifestAttributes(), aMapWithSize(3));
		assertThat(prj.getManifestAttributes(), hasEntry("one", "1"));
		assertThat(prj.getManifestAttributes(), hasEntry("two", "2"));
		assertThat(prj.getManifestAttributes(), hasEntry("three", "3"));
		assertThat(prj.getDocs(), iterableWithSize(2));
		assertThat(prj.getDocs(), containsInAnyOrder(
				DocRef.create("javadoc", ResourceRef.forFile(examplesTestFolder.resolve("readme.md"))),
				DocRef.create(ResourceRef.forFile(examplesTestFolder.resolve("readme.adoc")))));
	}

	@Test
	void testJar() {
		ProjectBuilder pb = Project.builder();
		Path src = examplesTestFolder.resolve("hellojar.jar");
		Project prj = pb.build(src);
		assertThat(prj.getDescription().isPresent(), equalTo(Boolean.FALSE));
		assertThat(prj.getRuntimeOptions(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(0));
		assertThat(prj.getRepositories(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(0));
		assertThat(prj.getProperties(), anEmptyMap());
		assertThat(prj.getJavaVersion(), equalTo("8+"));
		assertThat(prj.getMainClass(), equalTo("helloworld"));
		assertThat(prj.getModuleName().isPresent(), equalTo(Boolean.FALSE));
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(0));
		assertThat(prj.isNativeImage(), is(Boolean.FALSE));
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(0));
		assertThat(prj.enableCDS(), is(Boolean.FALSE));
		assertThat(prj.enablePreview(), is(Boolean.FALSE));
		assertThat(prj.getManifestAttributes(), aMapWithSize(0));
	}

	@Test
	void testGAV() {
		ProjectBuilder pb = Project.builder();
		String gav = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";
		Project prj = pb.build(gav);
		assertThat(prj.getDescription().isPresent(), equalTo(Boolean.FALSE));
		assertThat(prj.getRuntimeOptions(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getDependencies(), contains(gav));
		assertThat(prj.getRepositories(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(0));
		assertThat(prj.getProperties(), anEmptyMap());
		assertThat(prj.getJavaVersion(), nullValue());
		assertThat(prj.getMainClass(), equalTo("org.eclipse.jgit.pgm.Main"));
		assertThat(prj.getModuleName().isPresent(), equalTo(Boolean.FALSE));
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(0));
		assertThat(prj.isNativeImage(), is(Boolean.FALSE));
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(0));
		assertThat(prj.enableCDS(), is(Boolean.FALSE));
		assertThat(prj.enablePreview(), is(Boolean.FALSE));
		assertThat(prj.getManifestAttributes(), aMapWithSize(0));
	}

	@Test
	void testAliasSource() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build("alltags");
		assertThat(prj.getDescription().get(), equalTo("some description"));
		assertThat(prj.getRuntimeOptions(), iterableWithSize(4));
		assertThat(prj.getRuntimeOptions(),
				contains("--add-opens", "java.base/java.net=ALL-UNNAMED", "-Dfoo=bar", "-Dbar=aap noot mies"));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(7));
		assertThat(prj.getMainSourceSet().getSources(), containsInAnyOrder(
				new AliasResourceResolver.AliasedResourceRef(prj.getResourceRef(), null, null),
				ResourceRef.forFile(examplesTestFolder.resolve("Two.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("gh_fetch_release_assets.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("gh_release_stats.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedTwo.java")),
				ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedOne.java")),
				ResourceRef.forResolvedResource("helloworld.java", examplesTestFolder.resolve("helloworld.java"))));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(4));
		assertThat(prj.getMainSourceSet().getResources(), containsInAnyOrder(
				RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")), null),
				RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
						Paths.get("renamed.properties")),
				RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
						Paths.get("META-INF/application.properties")),
				RefTarget.create(ResourceRef.forResolvedResource("res/test.properties",
						examplesTestFolder.resolve("res/test.properties")), null)));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(6));
		assertThat(prj.getMainSourceSet().getDependencies(), contains(
				"dummy.org:dummy:1.2.3",
				"info.picocli:picocli:4.6.3",
				"org.kohsuke:github-api:1.116",
				"info.picocli:picocli:4.6.3",
				"org.kohsuke:github-api:1.116",
				"twodep"));
		assertThat(prj.getRepositories(), iterableWithSize(2));
		assertThat(prj.getRepositories(),
				contains(new MavenRepo("dummy", "http://dummy"), new MavenRepo("tworepo", "tworepo")));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getClassPaths(), contains("twocp"));
		assertThat(prj.getProperties(), aMapWithSize(1));
		assertThat(prj.getProperties(), hasEntry("two", "2"));
		assertThat(prj.getJavaVersion(), equalTo("twojava"));
		assertThat(prj.getMainClass(), equalTo("mainclass")); // This is not updated from Alias here!
		assertThat(prj.getModuleName().get(), equalTo("mymodule")); // This is not updated from Alias here!
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(5));
		assertThat(prj.getMainSourceSet().getCompileOptions(),
				contains("-g", "-parameters", "--enable-preview", "--verbose", "--ctwo"));
		assertThat(prj.isNativeImage(), is(Boolean.TRUE));
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(4));
		assertThat(prj.getMainSourceSet().getNativeOptions(), contains("-O1", "-d", "-O1", "--ntwo"));
		assertThat(prj.enableCDS(), is(Boolean.TRUE));
		assertThat(prj.enablePreview(), is(Boolean.TRUE));
		assertThat(prj.getManifestAttributes(), aMapWithSize(7));
		assertThat(prj.getManifestAttributes(), hasEntry("one", "1"));
		assertThat(prj.getManifestAttributes(), hasEntry("two", "2"));
		assertThat(prj.getManifestAttributes(), hasEntry("three", "3"));
		assertThat(prj.getManifestAttributes(), hasEntry("foo", "true"));
		assertThat(prj.getManifestAttributes(), hasEntry("bar", "baz"));
		assertThat(prj.getManifestAttributes(), hasEntry("baz", "nada"));
		assertThat(prj.getManifestAttributes(), hasEntry("twom", "2"));
	}

	@Test
	void testAliasExplicitVersionExposedAsProperty() throws IOException {
		// alias:version on a plain file (no pattern) exposes jbang.app.version to the
		// script
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build("helloworld:1.2.3");
		assertThat(prj.getProperties(), hasEntry("jbang.app.version", "1.2.3"));
	}

	@Test
	void testAliasWithoutVersionHasNoVersionProperty() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build("helloworld");
		assertThat(prj.getProperties(), not(hasEntry("jbang.app.version", "1.2.3")));
		assertThat(prj.getProperties().containsKey("jbang.app.version"), is(Boolean.FALSE));
	}

	@Test
	void testExplicitVersionPropertyWinsOverAliasVersion() throws IOException {
		// An explicit -Djbang.app.version=<version> must not be clobbered by
		// alias:version
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder()
			.setProperties(java.util.Collections.singletonMap("jbang.app.version", "2.33"));
		Project prj = pb.build("helloworld:1.2");
		assertThat(prj.getProperties(), hasEntry("jbang.app.version", "2.33"));
	}

	// --- Scenarios discussed on PR #2433 ---------------------------------------
	// Alias `versiondefault` defines a default via its properties block:
	// { "script-ref": "helloworld.java", "properties": { "jbang.app.version":
	// "2.7.4" } }
	// Agreed precedence: -D (command line) > alias:version selector > alias
	// properties
	// default.

	@Test
	void testAliasPropertiesDefaultVersion() throws IOException {
		// No :version, no -D -> the alias properties-block default is used
		Util.setCwd(examplesTestFolder);
		Project prj = Project.builder().build("versiondefault");
		assertThat(prj.getProperties(), hasEntry("jbang.app.version", "2.7.4"));
	}

	@Test
	void testAliasVersionOverridesPropertiesDefault() throws IOException {
		// alias:version must override the alias properties-block default
		Util.setCwd(examplesTestFolder);
		Project prj = Project.builder().build("versiondefault:2.7.5b1");
		assertThat(prj.getProperties(), hasEntry("jbang.app.version", "2.7.5b1"));
	}

	@Test
	void testExplicitPropertyWinsOverVersionAndPropertiesDefault() throws IOException {
		// An explicit -Djbang.app.version wins over both the selector and the alias
		// default
		Util.setCwd(examplesTestFolder);
		Project prj = Project.builder()
			.setProperties(java.util.Collections.singletonMap("jbang.app.version", "1.2.3"))
			.build("versiondefault:2.7.5b1");
		assertThat(prj.getProperties(), hasEntry("jbang.app.version", "1.2.3"));
	}

	// --- Command-line -D vs alias `properties` block: should MERGE per-key, -D
	// winning
	// (today it is an all-or-nothing block override; these pin the desired merge)
	// ------

	@Test
	void testAliasPropertiesMergedWithCommandLineProperties() throws IOException {
		// alias `alltags` defines properties { "two": "2" }; an unrelated -D must merge
		// in
		Util.setCwd(examplesTestFolder);
		Map<String, String> cli = new java.util.HashMap<>();
		cli.put("foo", "bar");
		Project prj = Project.builder().setProperties(cli).build("alltags");
		assertThat(prj.getProperties(), hasEntry("two", "2")); // from alias properties
		assertThat(prj.getProperties(), hasEntry("foo", "bar")); // from -D
	}

	@Test
	void testCommandLinePropertyOverridesAliasPropertyPerKey() throws IOException {
		// same key in both -> -D wins for that key
		Util.setCwd(examplesTestFolder);
		Project prj = Project.builder()
			.setProperties(java.util.Collections.singletonMap("two", "99"))
			.build("alltags");
		assertThat(prj.getProperties(), hasEntry("two", "99"));
	}

	@Test
	void testUnrelatedCommandLinePropertyKeepsAliasDefault() throws IOException {
		// an unrelated -D must NOT wipe the alias properties-block default
		Util.setCwd(examplesTestFolder);
		Project prj = Project.builder()
			.setProperties(java.util.Collections.singletonMap("foo", "bar"))
			.build("versiondefault");
		assertThat(prj.getProperties(), hasEntry("jbang.app.version", "2.7.4"));
		assertThat(prj.getProperties(), hasEntry("foo", "bar"));
	}

	@Test
	void testAliasJar() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build("hellojar");
		assertThat(prj.getDescription().isPresent(), equalTo(Boolean.FALSE));
		assertThat(prj.getRuntimeOptions(), iterableWithSize(2));
		assertThat(prj.getRuntimeOptions(), contains("-Dfoo=bar", "-Dbar=aap noot mies"));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getSources(), containsInAnyOrder(
				ResourceRef.forResolvedResource("helloworld.java", examplesTestFolder.resolve("helloworld.java"))));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getResources(), containsInAnyOrder(
				RefTarget.create(ResourceRef.forResolvedResource("res/test.properties",
						examplesTestFolder.resolve("res/test.properties")), null)));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getDependencies(), contains("twodep"));
		assertThat(prj.getRepositories(), iterableWithSize(1));
		assertThat(prj.getRepositories(), contains(new MavenRepo("tworepo", "tworepo")));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getClassPaths(), contains("twocp"));
		assertThat(prj.getProperties(), aMapWithSize(1));
		assertThat(prj.getProperties(), hasEntry("two", "2"));
		assertThat(prj.getJavaVersion(), equalTo("twojava"));
		assertThat(prj.getMainClass(), equalTo("helloworld")); // This is not updated from Alias here!
		assertThat(prj.getModuleName().isPresent(), equalTo(Boolean.FALSE)); // This is not updated from Alias here!
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getCompileOptions(), contains("--ctwo"));
		assertThat(prj.isNativeImage(), is(Boolean.TRUE));
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getNativeOptions(), contains("-O1", "--ntwo"));
		assertThat(prj.enableCDS(), is(Boolean.FALSE)); // This is not updated from Alias here!
		assertThat(prj.enablePreview(), is(Boolean.TRUE));
		assertThat(prj.getManifestAttributes(), aMapWithSize(4));
		assertThat(prj.getManifestAttributes(), hasEntry("foo", "true"));
		assertThat(prj.getManifestAttributes(), hasEntry("bar", "baz"));
		assertThat(prj.getManifestAttributes(), hasEntry("baz", "nada"));
		assertThat(prj.getManifestAttributes(), hasEntry("twom", "2"));
	}

	@Test
	void testAliasGAV() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		String gav = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";
		Project prj = pb.build("pgmgav");
		assertThat(prj.getDescription().isPresent(), equalTo(Boolean.FALSE));
		assertThat(prj.getRuntimeOptions(), iterableWithSize(2));
		assertThat(prj.getRuntimeOptions(), contains("-Dfoo=bar", "-Dbar=aap noot mies"));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getSources(), containsInAnyOrder(
				ResourceRef.forResolvedResource("helloworld.java", examplesTestFolder.resolve("helloworld.java"))));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getResources(), containsInAnyOrder(
				RefTarget.create(ResourceRef.forResolvedResource("res/test.properties",
						examplesTestFolder.resolve("res/test.properties")), null)));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getDependencies(), contains(
				gav, "info.picocli:picocli:4.6.3"));
		assertThat(prj.getRepositories(), iterableWithSize(2));
		assertThat(prj.getRepositories(), contains(new MavenRepo("mavencentral", "https://repo1.maven.org/maven2/"),
				new MavenRepo("tworepo", "tworepo")));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getClassPaths(), contains("twocp"));
		assertThat(prj.getProperties(), aMapWithSize(1));
		assertThat(prj.getProperties(), hasEntry("two", "2"));
		assertThat(prj.getJavaVersion(), equalTo("twojava"));
		assertThat(prj.getMainClass(), equalTo("org.eclipse.jgit.pgm.Main")); // This is not updated from Alias here!
		assertThat(prj.getModuleName().isPresent(), equalTo(Boolean.FALSE)); // This is not updated from Alias here!
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getCompileOptions(), contains("--ctwo"));
		assertThat(prj.isNativeImage(), is(Boolean.TRUE));
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getNativeOptions(), contains("-O1", "--ntwo"));
		assertThat(prj.enableCDS(), is(Boolean.FALSE)); // This is not updated from Alias here!
		assertThat(prj.enablePreview(), is(Boolean.TRUE));
		assertThat(prj.getManifestAttributes(), aMapWithSize(4));
		assertThat(prj.getManifestAttributes(), hasEntry("foo", "true"));
		assertThat(prj.getManifestAttributes(), hasEntry("bar", "baz"));
		assertThat(prj.getManifestAttributes(), hasEntry("baz", "nada"));
		assertThat(prj.getManifestAttributes(), hasEntry("twom", "2"));
	}

	@Test
	void testAbsoluteFileTags() {
		ProjectBuilder pb = Project.builder();
		Path src;
		if (Util.isWindows()) {
			src = examplesTestFolder.resolve("abstagswin.java");
		} else {
			src = examplesTestFolder.resolve("abstagsnix.java");
		}
		Project prj = pb.build(src);
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(3));
		assertThat(prj.getMainSourceSet()
			.getResources()
			.stream()
			.filter(rt -> rt.getSource() instanceof ResourceRef.UnresolvableResourceRef)
			.collect(Collectors.toList()), iterableWithSize(2));
	}

	@Test
	void testMissingFilesTags() {
		ProjectBuilder pb = Project.builder();
		Path src = examplesTestFolder.resolve("badtags.java");
		Project prj = pb.build(src);
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getSources().get(1), instanceOf(ResourceRef.UnresolvableResourceRef.class));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet()
			.getResources()
			.stream()
			.filter(rt -> rt.getSource() instanceof ResourceRef.UnresolvableResourceRef)
			.collect(Collectors.toList()), iterableWithSize(2));
	}

	@Test
	void testBuildJbangAutoSourcesMainJava(@TempDir Path tmpDir) throws IOException {
		Util.setPreview(true);

		Path src = tmpDir.resolve("build.jbang");
		Util.writeString(src, "//DESCRIPTION Test\n");
		Util.writeString(tmpDir.resolve(MAIN_JAVA), "//Dummy source file");

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(src);
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getSources().get(0).getFile().toString(),
				endsWith(File.separator + MAIN_JAVA));
		assertThat(prj.getMainSourceSet().getSources(), containsInAnyOrder(
				ResourceRef.forFile(tmpDir.resolve(MAIN_JAVA))));
	}

	@Test
	void testBuildJbangAutoSourcesDirs(@TempDir Path tmpDir) throws IOException {
		Util.setPreview(true);

		Path src = tmpDir.resolve("build.jbang");
		Util.writeString(src, "//DESCRIPTION Test\n");
		Path dirs = tmpDir.resolve("src/main/java");
		dirs.toFile().mkdirs();
		Util.writeString(dirs.resolve("Foo.java"), "//Dummy source file");
		Util.writeString(dirs.resolve("Bar.java"), "//Dummy source file");
		dirs.resolve("baz").toFile().mkdirs();
		Util.writeString(dirs.resolve("baz/Baz.java"), "//Dummy source file");

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(src);
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(3));
		assertThat(prj.getMainSourceSet().getSources(), containsInAnyOrder(
				ResourceRef.forFile(dirs.resolve("Foo.java")),
				ResourceRef.forFile(dirs.resolve("Bar.java")),
				ResourceRef.forFile(dirs.resolve("baz/Baz.java"))));
	}

	@Test
	void testBuildWarFile() throws IOException {
		Path warPath = Files.createTempFile("test", ".war");
		try {
			WarTestFixtures.createExecutableWar(warPath, "TestMain");
			ProjectBuilder pb = Project.builder();
			Project project = pb.build(warPath);
			assertTrue(project.isExecutableArchive());
			assertThat(project.getMainClass(), equalTo("TestMain"));
		} finally {
			Files.deleteIfExists(warPath);
		}
	}
}
