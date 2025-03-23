package dev.jbang.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.resolvers.AliasResourceResolver;
import dev.jbang.util.Util;

public class TestProjectBuilder extends BaseTest {

	static final String aliases = "{\n" +
			"  \"aliases\": {\n" +
			"    \"test\": {\n" +
			"      \"script-ref\": \"alltags.java\",\n" +
			"      \"description\": \"twodesc\",\n" +
			"      \"arguments\": [\"2\"],\n" +
			"      \"runtime-options\": [\"--two\"],\n" +
			"      \"sources\": [\"twosrc\"],\n" +
			"      \"files\": [\"twofiles\"],\n" +
			"      \"dependencies\": [\"twodep\"],\n" +
			"      \"repositories\": [\"tworepo\"],\n" +
			"      \"classpaths\": [\"twocp\"],\n" +
			"      \"properties\": {\"two\":\"2\"},\n" +
			"      \"java\": \"twojava\",\n" +
			"      \"main\": \"twomain\",\n" +
			"      \"module\": \"twomodule\",\n" +
			"      \"compile-options\": [\"--ctwo\"],\n" +
			"      \"native-image\": true,\n" +
			"      \"native-options\": [\"--ntwo\"],\n" +
			"      \"jfr\": \"twojfr\",\n" +
			"      \"debug\": {\"twod\":\"2\"},\n" +
			"      \"cds\": true,\n" +
			"      \"interactive\": true,\n" +
			"      \"enable-preview\": true,\n" +
			"      \"enable-assertions\": true,\n" +
			"      \"enable-system-assertions\": true,\n" +
			"      \"manifest-options\": {\"twom\":\"2\"},\n" +
			"      \"java-agents\": [{\"agent-ref\":\"twojag\",\"options\":\"twoopts\"}]\n" +
			"    }\n" +
			"  }\n" +
			"}";

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
		assertThat(prj.getDescription().get()).isEqualTo("some description");
		assertThat(prj.getRuntimeOptions(), iterableWithSize(2));
		assertThat(prj.getRuntimeOptions()).containsExactly("--add-opens", "java.base/java.net=ALL-UNNAMED");
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(6));
		assertThat(prj.getMainSourceSet().getSources()).containsExactlyInAnyOrder(ResourceRef.forFile(src), ResourceRef.forFile(examplesTestFolder.resolve("Two.java")), ResourceRef.forFile(examplesTestFolder.resolve("gh_fetch_release_assets.java")), ResourceRef.forFile(examplesTestFolder.resolve("gh_release_stats.java")), ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedTwo.java")), ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedOne.java")));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(3));
		assertThat(prj.getMainSourceSet().getResources()).containsExactlyInAnyOrder(RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")), null), RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
				Paths.get("renamed.properties")), RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
				Paths.get("META-INF/application.properties")));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(5));
		assertThat(prj.getMainSourceSet().getDependencies()).containsExactly("dummy.org:dummy:1.2.3", "info.picocli:picocli:4.6.3", "org.kohsuke:github-api:1.116", "info.picocli:picocli:4.6.3", "org.kohsuke:github-api:1.116");
		assertThat(prj.getRepositories(), iterableWithSize(1));
		assertThat(prj.getRepositories()).containsExactly(new MavenRepo("dummy", "http://dummy"));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(0));
		assertThat(prj.getProperties()).isEmpty();
		assertThat(prj.getJavaVersion()).isEqualTo("11+");
		assertThat(prj.getMainClass()).isEqualTo("mainclass");
		assertThat(prj.getModuleName().get()).isEqualTo("mymodule");
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(4));
		assertThat(prj.getMainSourceSet().getCompileOptions()).containsExactly("-g", "-parameters", "--enable-preview", "--verbose");
		assertThat(prj.isNativeImage()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getNativeOptions()).containsExactly("-O1", "-d");
		assertThat(prj.enableCDS()).isEqualTo(Boolean.TRUE);
		assertThat(prj.enablePreview()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getManifestAttributes()).hasSize(3);
		assertThat(prj.getManifestAttributes()).containsEntry("one", "1");
		assertThat(prj.getManifestAttributes()).containsEntry("two", "2");
		assertThat(prj.getManifestAttributes()).containsEntry("three", "3");
	}

	@Test
	void testJar() {
		ProjectBuilder pb = Project.builder();
		Path src = examplesTestFolder.resolve("hellojar.jar");
		Project prj = pb.build(src);
		assertThat(prj.getDescription().isPresent()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getRuntimeOptions(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(0));
		assertThat(prj.getRepositories(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(0));
		assertThat(prj.getProperties()).isEmpty();
		assertThat(prj.getJavaVersion()).isEqualTo("8+");
		assertThat(prj.getMainClass()).isEqualTo("helloworld");
		assertThat(prj.getModuleName().isPresent()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(0));
		assertThat(prj.isNativeImage()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(0));
		assertThat(prj.enableCDS()).isEqualTo(Boolean.FALSE);
		assertThat(prj.enablePreview()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getManifestAttributes()).hasSize(0);
	}

	@Test
	void testGAV() {
		ProjectBuilder pb = Project.builder();
		String gav = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";
		Project prj = pb.build(gav);
		assertThat(prj.getDescription().isPresent()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getRuntimeOptions(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getDependencies()).containsExactly(gav);
		assertThat(prj.getRepositories(), iterableWithSize(0));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(0));
		assertThat(prj.getProperties()).isEmpty();
		assertThat(prj.getJavaVersion()).isNull();
		assertThat(prj.getMainClass()).isEqualTo("org.eclipse.jgit.pgm.Main");
		assertThat(prj.getModuleName().isPresent()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(0));
		assertThat(prj.isNativeImage()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(0));
		assertThat(prj.enableCDS()).isEqualTo(Boolean.FALSE);
		assertThat(prj.enablePreview()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getManifestAttributes()).hasSize(0);
	}

	@Test
	void testAliasSource() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		Path src = examplesTestFolder.resolve("alltags.java");
		Project prj = pb.build("alltags");
		assertThat(prj.getDescription().get()).isEqualTo("some description");
		assertThat(prj.getRuntimeOptions(), iterableWithSize(4));
		assertThat(prj.getRuntimeOptions()).containsExactly("--add-opens", "java.base/java.net=ALL-UNNAMED", "-Dfoo=bar", "-Dbar=aap noot mies");
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(7));
		assertThat(prj.getMainSourceSet().getSources()).containsExactlyInAnyOrder(new AliasResourceResolver.AliasedResourceRef(src.toString(), src, null), ResourceRef.forFile(examplesTestFolder.resolve("Two.java")), ResourceRef.forFile(examplesTestFolder.resolve("gh_fetch_release_assets.java")), ResourceRef.forFile(examplesTestFolder.resolve("gh_release_stats.java")), ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedTwo.java")), ResourceRef.forFile(examplesTestFolder.resolve("nested/NestedOne.java")), ResourceRef.forNamedFile("helloworld.java", examplesTestFolder.resolve("helloworld.java")));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(4));
		assertThat(prj.getMainSourceSet().getResources()).containsExactlyInAnyOrder(RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")), null), RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
				Paths.get("renamed.properties")), RefTarget.create(ResourceRef.forFile(examplesTestFolder.resolve("res/resource.properties")),
				Paths.get("META-INF/application.properties")), RefTarget.create(ResourceRef.forNamedFile("res/test.properties",
				examplesTestFolder.resolve("res/test.properties")), null));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(6));
		assertThat(prj.getMainSourceSet().getDependencies()).containsExactly("dummy.org:dummy:1.2.3", "info.picocli:picocli:4.6.3", "org.kohsuke:github-api:1.116", "info.picocli:picocli:4.6.3", "org.kohsuke:github-api:1.116", "twodep");
		assertThat(prj.getRepositories(), iterableWithSize(2));
		assertThat(prj.getRepositories()).containsExactly(new MavenRepo("dummy", "http://dummy"), new MavenRepo("tworepo", "tworepo"));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getClassPaths()).containsExactly("twocp");
		assertThat(prj.getProperties()).hasSize(1);
		assertThat(prj.getProperties()).containsEntry("two", "2");
		assertThat(prj.getJavaVersion()).isEqualTo("twojava");
		assertThat(prj.getMainClass()).isEqualTo("mainclass"); // This is not updated from Alias here!
		assertThat(prj.getModuleName().get()).isEqualTo("mymodule"); // This is not updated from Alias here!
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(5));
		assertThat(prj.getMainSourceSet().getCompileOptions()).containsExactly("-g", "-parameters", "--enable-preview", "--verbose", "--ctwo");
		assertThat(prj.isNativeImage()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(4));
		assertThat(prj.getMainSourceSet().getNativeOptions()).containsExactly("-O1", "-d", "-O1", "--ntwo");
		assertThat(prj.enableCDS()).isEqualTo(Boolean.TRUE);
		assertThat(prj.enablePreview()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getManifestAttributes()).hasSize(7);
		assertThat(prj.getManifestAttributes()).containsEntry("one", "1");
		assertThat(prj.getManifestAttributes()).containsEntry("two", "2");
		assertThat(prj.getManifestAttributes()).containsEntry("three", "3");
		assertThat(prj.getManifestAttributes()).containsEntry("foo", "true");
		assertThat(prj.getManifestAttributes()).containsEntry("bar", "baz");
		assertThat(prj.getManifestAttributes()).containsEntry("baz", "nada");
		assertThat(prj.getManifestAttributes()).containsEntry("twom", "2");
	}

	@Test
	void testAliasJar() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build("hellojar");
		assertThat(prj.getDescription().isPresent()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getRuntimeOptions(), iterableWithSize(2));
		assertThat(prj.getRuntimeOptions()).containsExactly("-Dfoo=bar", "-Dbar=aap noot mies");
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getSources()).containsExactlyInAnyOrder(ResourceRef.forNamedFile("helloworld.java", examplesTestFolder.resolve("helloworld.java")));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getResources()).containsExactlyInAnyOrder(RefTarget.create(ResourceRef.forNamedFile("res/test.properties",
				examplesTestFolder.resolve("res/test.properties")), null));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getDependencies()).containsExactly("twodep");
		assertThat(prj.getRepositories(), iterableWithSize(1));
		assertThat(prj.getRepositories()).containsExactly(new MavenRepo("tworepo", "tworepo"));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getClassPaths()).containsExactly("twocp");
		assertThat(prj.getProperties()).hasSize(1);
		assertThat(prj.getProperties()).containsEntry("two", "2");
		assertThat(prj.getJavaVersion()).isEqualTo("twojava");
		assertThat(prj.getMainClass()).isEqualTo("helloworld"); // This is not updated from Alias here!
		assertThat(prj.getModuleName().isPresent()).isEqualTo(Boolean.FALSE); // This is not updated from Alias here!
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getCompileOptions()).containsExactly("--ctwo");
		assertThat(prj.isNativeImage()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getNativeOptions()).containsExactly("-O1", "--ntwo");
		assertThat(prj.enableCDS()).isEqualTo(Boolean.FALSE); // This is not updated from Alias here!
		assertThat(prj.enablePreview()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getManifestAttributes()).hasSize(4);
		assertThat(prj.getManifestAttributes()).containsEntry("foo", "true");
		assertThat(prj.getManifestAttributes()).containsEntry("bar", "baz");
		assertThat(prj.getManifestAttributes()).containsEntry("baz", "nada");
		assertThat(prj.getManifestAttributes()).containsEntry("twom", "2");
	}

	@Test
	void testAliasGAV() throws IOException {
		Util.setCwd(examplesTestFolder);
		ProjectBuilder pb = Project.builder();
		String gav = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";
		Project prj = pb.build("pgmgav");
		assertThat(prj.getDescription().isPresent()).isEqualTo(Boolean.FALSE);
		assertThat(prj.getRuntimeOptions(), iterableWithSize(2));
		assertThat(prj.getRuntimeOptions()).containsExactly("-Dfoo=bar", "-Dbar=aap noot mies");
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getSources()).containsExactlyInAnyOrder(ResourceRef.forNamedFile("helloworld.java", examplesTestFolder.resolve("helloworld.java")));
		assertThat(prj.getMainSourceSet().getResources(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getResources()).containsExactlyInAnyOrder(RefTarget.create(ResourceRef.forNamedFile("res/test.properties",
				examplesTestFolder.resolve("res/test.properties")), null));
		assertThat(prj.getMainSourceSet().getDependencies(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getDependencies()).containsExactly(gav, "info.picocli:picocli:4.6.3");
		assertThat(prj.getRepositories(), iterableWithSize(2));
		assertThat(prj.getRepositories()).containsExactly(new MavenRepo("mavencentral", "https://repo1.maven.org/maven2/"), new MavenRepo("tworepo", "tworepo"));
		assertThat(prj.getMainSourceSet().getClassPaths(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getClassPaths()).containsExactly("twocp");
		assertThat(prj.getProperties()).hasSize(1);
		assertThat(prj.getProperties()).containsEntry("two", "2");
		assertThat(prj.getJavaVersion()).isEqualTo("twojava");
		assertThat(prj.getMainClass()).isEqualTo("org.eclipse.jgit.pgm.Main"); // This is not updated from Alias here!
		assertThat(prj.getModuleName().isPresent()).isEqualTo(Boolean.FALSE); // This is not updated from Alias here!
		assertThat(prj.getMainSourceSet().getCompileOptions(), iterableWithSize(1));
		assertThat(prj.getMainSourceSet().getCompileOptions()).containsExactly("--ctwo");
		assertThat(prj.isNativeImage()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getMainSourceSet().getNativeOptions(), iterableWithSize(2));
		assertThat(prj.getMainSourceSet().getNativeOptions()).containsExactly("-O1", "--ntwo");
		assertThat(prj.enableCDS()).isEqualTo(Boolean.FALSE); // This is not updated from Alias here!
		assertThat(prj.enablePreview()).isEqualTo(Boolean.TRUE);
		assertThat(prj.getManifestAttributes()).hasSize(4);
		assertThat(prj.getManifestAttributes()).containsEntry("foo", "true");
		assertThat(prj.getManifestAttributes()).containsEntry("bar", "baz");
		assertThat(prj.getManifestAttributes()).containsEntry("baz", "nada");
		assertThat(prj.getManifestAttributes()).containsEntry("twom", "2");
	}
}
