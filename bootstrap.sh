## This script is an experiment to bootstrap jbang with jbang itself.

## in gradle we have plugin to generate BuildConfig with version info in it.
## For now just generate a placeholder. Ultimately could be some other script to generate the file.
mkdir -p generated
cat << EOF > generated/BuildConfig.java
package dev.jbang;

public final class BuildConfig {
    public static final String VERSION = "999-jbang";
    public static final String NAME = "jbang";

    private BuildConfig() {
    }
}
EOF

jbang export local --verbose --force -O jbang.jar --sources 'src/main/java,generated/' --files 'src/main/resources' --repos mavencentral,jitpack --deps org.jboss:jandex:2.2.3.Final,org.slf4j:slf4j-nop:1.7.30,com.offbytwo:docopt:0.6.0.20150202,org.apache.commons:commons-text:1.10.0,org.apache.commons:commons-compress:1.20,info.picocli:picocli:4.6.3,io.quarkus.qute:qute-core:1.12.2.Final,kr.motd.maven:os-maven-plugin:1.7.0,org.codehaus.plexus:plexus-java:1.0.6,com.google.code.gson:gson:2.9.0,org.jsoup:jsoup:1.13.1,org.codejive:java-properties:0.0.4,com.github.jbangdev.jbang-resolver:shrinkwrap-resolver-api:3.1.5-allowpom,com.github.jbangdev.jbang-resolver:shrinkwrap-resolver-impl-maven:3.1.5-allowpom src/main/java/dev/jbang/Main.java
