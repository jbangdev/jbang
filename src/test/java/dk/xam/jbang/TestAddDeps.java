package dk.xam.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestAddDeps {

	String example = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"\n" +
			"<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
			+
			"  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
			+
			"  <modelVersion>4.0.0</modelVersion>\n" +
			"\n" +
			"  <groupId>com.gerbenvis.tools</groupId>\n" +
			"  <artifactId>devops-cli</artifactId>\n" +
			"  <version>1.0-SNAPSHOT</version>\n" +
			"\n" +
			"  <name>devops-cli</name>\n" +
			"  <url>http://www.gerbenvis.com/java/devops-cli</url>\n" +
			"\n" +
			"  <properties>\n" +
			"    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
			"    <maven.compiler.source>11</maven.compiler.source>\n" +
			"    <maven.compiler.target>11</maven.compiler.target>\n" +
			"  </properties>\n" +
			"\n" +
			"  <dependencies>\n" +
			"\n" +
			"    <dependency>\n" +
			"      <groupId>org.projectlombok</groupId>\n" +
			"      <artifactId>lombok</artifactId>\n" +
			"      <version>1.18.10</version>\n" +
			"      <scope>provided</scope>\n" +
			"    </dependency>\n" +
			"\n" +
			"    <dependency>\n" +
			"      <groupId>info.picocli</groupId>\n" +
			"      <artifactId>picocli</artifactId>\n" +
			"      <version>4.1.4</version>\n" +
			"    </dependency>\n" +
			"\n" +
			"  </dependencies>\n" +
			"\n" +
			"  <build>\n" +
			"    <plugins>\n" +
			"      <plugin>\n" +
			"        <groupId>org.apache.maven.plugins</groupId>\n" +
			"        <artifactId>maven-jar-plugin</artifactId>\n" +
			"        <version>3.2.0</version>\n" +
			"        <configuration>\n" +
			"          <archive>\n" +
			"            <manifest>\n" +
			"              <addClasspath>true</addClasspath>\n" +
			"              <mainClass>com.gerbenvis.opencli.AkamaiInfoCommand</mainClass>\n" +
			"              <classpathPrefix>dependency-jars/</classpathPrefix>\n" +
			"            </manifest>\n" +
			"          </archive>\n" +
			"        </configuration>\n" +
			"      </plugin>\n" +
			"\n" +
			"      <plugin>\n" +
			"        <groupId>org.apache.maven.plugins</groupId>\n" +
			"        <artifactId>maven-dependency-plugin</artifactId>\n" +
			"        <version>2.5.1</version>\n" +
			"        <executions>\n" +
			"          <execution>\n" +
			"            <id>copy-dependencies</id>\n" +
			"            <phase>package</phase>\n" +
			"            <goals>\n" +
			"              <goal>copy-dependencies</goal>\n" +
			"            </goals>\n" +
			"            <configuration>\n" +
			"              <outputDirectory>\n" +
			"                ${project.build.directory}/dependency-jars/\n" +
			"              </outputDirectory>\n" +
			"            </configuration>\n" +
			"          </execution>\n" +
			"        </executions>\n" +
			"      </plugin>\n" +
			"    </plugins>\n" +
			"  </build>\n" +
			"</project>\n";

	@Test
	void testAddDeps(@TempDir File dir) throws IOException {

		File pom = new File(dir, "pom.xml");

		Util.writeString(pom.toPath(), example);

		List<MavenCoordinate> result = Main.findDeps(pom);

		assertThat(result, containsInAnyOrder("org.projectlombok:lombok:1.18.10", "info.picocli:picocli:4.1.4"));

	}

}
